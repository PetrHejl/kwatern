package me.hejl.kwatern.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Region;
import me.hejl.image.Image;
import me.hejl.image.ImageDecoders;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.Thumbnails;
import me.hejl.image.jpeg.JpegWriter;

/**
 * The published media files and JPEG versions of them: thumbnails, portraits cropped to the region of a person's
 * media reference, and a larger image for the media page. They are made on first request, at most
 * {@link #PARALLEL} at a time since large scans take a while. Thumbnails and portraits are small and kept in
 * memory; the larger images only up to {@link #DISPLAY_CACHE} bytes, the most recently used. Only media objects
 * of the published database are ever read, so a request can never reach any other file.
 */
public final class MediaImages {

    /** The sizes made, as the longest side in pixels: about twice the displayed size, for high-DPI screens. */
    public enum Kind {
        THUMBNAIL(320, false),
        PORTRAIT(240, true),
        DISPLAY(1600, false);

        final int size;
        final boolean square;

        Kind(int size, boolean square) {
            this.size = size;
            this.square = square;
        }
    }

    /** A JPEG image and a tag that changes whenever it may change, for HTTP caching. */
    public record Jpeg(byte[] data, String etag) {}

    /** A media file as it is, and the type to serve it as. */
    public record Original(Path file, String contentType) {}

    private static final int PARALLEL = 2;
    private static final int QUALITY = 85;
    private static final long DISPLAY_CACHE = 24L << 20;

    private final GrampsDatabase db;
    private final Path mediaDir;
    private final String exportMediaPath;
    private final ImageDecoders decoders;
    private final Semaphore permits = new Semaphore(PARALLEL);
    /** What the header of a readable file says, and its format as recognised from the content. */
    private record Header(ImageInfo info, String mimeType) {}

    private final Map<String, Optional<Header>> headers = new ConcurrentHashMap<>();
    private final Map<String, FutureTask<Optional<byte[]>>> cache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> displayed = new LinkedHashMap<>(16, 0.75f, true);
    private long displayedBytes;

    /**
     * @param mediaDir        where relative media paths are resolved; {@code null} to serve no media
     * @param exportMediaPath the base media path the export was made with; absolute paths under it are moved to
     *     {@code mediaDir}, so that an export made on one computer works with the files copied to another
     */
    public MediaImages(GrampsDatabase db, Path mediaDir, String exportMediaPath, ImageDecoders decoders) {
        this.db = db;
        this.mediaDir = mediaDir;
        this.exportMediaPath = exportMediaPath;
        this.decoders = decoders;
    }

    /** No media at all. */
    public static MediaImages none(GrampsDatabase db) {
        return new MediaImages(db, null, null, ImageDecoders.DEFAULT);
    }

    /** The file of a media object. */
    Path file(Media media) {
        Path path = Path.of(media.path());
        if (!path.isAbsolute()) {
            return mediaDir.resolve(path);
        }
        if (exportMediaPath != null && !exportMediaPath.isBlank() && !Files.exists(path)) {
            Path exportBase = Path.of(exportMediaPath);
            if (exportBase.isAbsolute() && path.startsWith(exportBase)) {
                return mediaDir.resolve(exportBase.relativize(path));
            }
        }
        return path;
    }

    /**
     * The size and orientation of an image, if the file exists and is in a format we can decode; read once per
     * media object from the file's header.
     */
    public Optional<ImageInfo> info(Media media) {
        return header(media).map(Header::info);
    }

    private Optional<Header> header(Media media) {
        if (mediaDir == null) {
            return Optional.empty();
        }
        return headers.computeIfAbsent(media.handle(), handle -> {
            try (InputStream in = Files.newInputStream(file(media))) {
                var reader = decoders.open(in);
                return Optional.of(new Header(reader.info(), reader.mimeType()));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    /** Whether images can probably be made of a media file. */
    public boolean readable(Media media) {
        return info(media).isPresent();
    }

    /**
     * The file itself, for viewing in full: images we can read, and other images and PDF documents by the type
     * Gramps recorded. Other kinds of files are not served, as browsers would only download them.
     */
    public Optional<Original> original(Media media) {
        if (mediaDir == null) {
            return Optional.empty();
        }
        Path file = file(media);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        // The format found in the file, which may differ from what Gramps recorded.
        Optional<Header> header = header(media);
        if (header.isPresent()) {
            return Optional.of(new Original(file, header.get().mimeType()));
        }
        String mime = media.mime() == null ? "" : media.mime().toLowerCase(Locale.ROOT);
        if (mime.matches("image/(png|gif|webp|avif|bmp|tiff)|application/pdf")) {
            return Optional.of(new Original(file, mime));
        }
        return Optional.empty();
    }

    /** The media reference whose image stands for a person: the first one, as in Gramps, if it can be shown. */
    public Optional<MediaRef> portrait(Person person) {
        if (person.media().isEmpty()) {
            return Optional.empty();
        }
        MediaRef first = person.media().getFirst();
        return db.media().get(first.media()).filter(this::readable).map(media -> first);
    }

    public Optional<Jpeg> thumbnail(Media media) {
        return image(media, null, Kind.THUMBNAIL);
    }

    public Optional<Jpeg> portraitImage(Person person) {
        return portrait(person)
                .flatMap(ref -> db.media().get(ref.media()).flatMap(m -> image(m, ref.region(), Kind.PORTRAIT)));
    }

    /** The image for the media page, made again when it has left the cache. */
    public Optional<Jpeg> display(Media media) {
        if (!readable(media)) {
            return Optional.empty();
        }
        String key = key(media, null, Kind.DISPLAY);
        byte[] data;
        synchronized (displayed) {
            data = displayed.get(key);
        }
        if (data == null) {
            try {
                data = make(media, null, Kind.DISPLAY).orElse(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            if (data == null) {
                return Optional.empty();
            }
            synchronized (displayed) {
                if (displayed.put(key, data) == null) {
                    displayedBytes += data.length;
                }
                var oldest = displayed.entrySet().iterator();
                while (displayedBytes > DISPLAY_CACHE && oldest.hasNext()) {
                    displayedBytes -= oldest.next().getValue().length;
                    oldest.remove();
                }
            }
        }
        return Optional.of(new Jpeg(data, "\"" + key + "\""));
    }

    private static String key(Media media, Region region, Kind kind) {
        return media.handle() + "-" + media.change() + "-" + kind.name().toLowerCase(Locale.ROOT)
                + (region == null ? "" : "-" + region.x1() + "-" + region.y1() + "-" + region.x2() + "-" + region.y2());
    }

    private Optional<Jpeg> image(Media media, Region region, Kind kind) {
        if (!readable(media)) {
            return Optional.empty();
        }
        String key = key(media, region, kind);
        FutureTask<Optional<byte[]>> task = new FutureTask<>(() -> make(media, region, kind));
        FutureTask<Optional<byte[]>> existing = cache.putIfAbsent(key, task);
        if (existing == null) {
            task.run();
        } else {
            task = existing;
        }
        try {
            return task.get().map(data -> new Jpeg(data, "\"" + key + "\""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }

    private Optional<byte[]> make(Media media, Region region, Kind kind) throws InterruptedException {
        permits.acquire();
        try (InputStream in = Files.newInputStream(file(media))) {
            Thumbnails.Crop crop = region == null
                    ? null
                    : new Thumbnails.Crop(
                            region.x1() / 100.0, region.y1() / 100.0, region.x2() / 100.0, region.y2() / 100.0);
            Image image = Thumbnails.make(decoders.open(in), kind.size, kind.size, crop, kind.square);
            var out = new ByteArrayOutputStream();
            JpegWriter.write(image, QUALITY, out);
            return Optional.of(out.toByteArray());
        } catch (ImageException e) {
            // The media ID only: file names often contain people's names.
            System.err.println("Cannot make an image of media " + media.id() + ": " + e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            System.err.println(
                    "Cannot read media " + media.id() + ": " + e.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            permits.release();
        }
    }
}
