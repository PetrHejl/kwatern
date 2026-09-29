package me.hejl.kwatern.web;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
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
import me.hejl.gramps.xml.GrampsPackage;
import me.hejl.image.Image;
import me.hejl.image.ImageDecoders;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.Thumbnails;
import me.hejl.image.jpeg.JpegWriter;

/**
 * The published media files and JPEG versions of them: thumbnails, portraits cropped to the region of a person's
 * media reference, and a larger image for the media page. They are made on first request, at most
 * {@link #PARALLEL} at a time in the whole process, since large scans take a while and much memory. Thumbnails and
 * portraits are small and kept in memory; the larger images only up to {@link #DISPLAY_CACHE} bytes, the most
 * recently used. Only media objects of the published database are ever read, so a request can never reach any
 * other file. An export can name any file, though, and may come from someone else: its media are read only from the
 * media directory unless allowed anywhere, those of a package only from where it was extracted, and a file is served
 * as it is only if its content is of the type it is served as.
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
    // Shared by all instances: each view of the site has its own, and a reload makes new ones while the old ones may
    // still be busy.
    private static final Semaphore PERMITS = new Semaphore(PARALLEL);

    private final GrampsDatabase db;
    private final Path mediaDir;
    private final String exportMediaPath;
    private final boolean packaged;
    private final boolean anywhere;
    private final ImageDecoders decoders;
    /** What the header of a readable file says, and its format as recognised from the content. */
    private record Header(ImageInfo info, String mimeType) {}

    /**
     * What is read and made of the files, by media handle and version. It depends only on the media object and its
     * file, never on the view, so the views of one version share it.
     */
    private static final class Store {
        final Map<String, Optional<Header>> headers = new ConcurrentHashMap<>();
        final Map<String, FutureTask<Optional<byte[]>>> cache = new ConcurrentHashMap<>();
        final Map<String, byte[]> displayed = new LinkedHashMap<>(16, 0.75f, true);
        long displayedBytes;
        // The larger images being made, until they are in the cache.
        final Map<String, FutureTask<Optional<byte[]>>> making = new ConcurrentHashMap<>();
    }

    private final Store store;

    /**
     * Media read only from the media directory.
     *
     * @param mediaDir        where relative media paths are resolved; {@code null} to serve no media
     * @param exportMediaPath the base media path the export was made with; absolute paths under it are moved to
     *     {@code mediaDir}, so that an export made on one computer works with the files copied to another
     */
    public MediaImages(GrampsDatabase db, Path mediaDir, String exportMediaPath, ImageDecoders decoders) {
        this(db, mediaDir, exportMediaPath, false, false, decoders, new Store());
    }

    private MediaImages(
            GrampsDatabase db,
            Path mediaDir,
            String exportMediaPath,
            boolean packaged,
            boolean anywhere,
            ImageDecoders decoders,
            Store store) {
        this.db = db;
        this.mediaDir = mediaDir;
        this.exportMediaPath = exportMediaPath;
        this.packaged = packaged;
        this.anywhere = anywhere;
        this.decoders = decoders;
        this.store = store;
    }

    /**
     * The same files for another view of the same version of the tree, such as the public one of the members'
     * view, sharing what has been made of them: images are made once for both. Only the media of that view's
     * database can be reached through it.
     */
    public MediaImages forView(GrampsDatabase view) {
        return new MediaImages(view, mediaDir, exportMediaPath, packaged, anywhere, decoders, store);
    }

    /**
     * Media read from wherever the export says, as Gramps allows; see {@code --allow-media-anywhere}. Only for an
     * export whose author may read every file of this computer that the server can.
     */
    public static MediaImages anywhere(
            GrampsDatabase db, Path mediaDir, String exportMediaPath, ImageDecoders decoders) {
        return new MediaImages(db, mediaDir, exportMediaPath, false, true, decoders, new Store());
    }

    /**
     * The media of a package, extracted to a directory. Each file is looked for there under its name in the
     * package, never elsewhere: a package may come from someone else, and the paths in its export must not reach
     * other files on this computer.
     */
    public static MediaImages ofPackage(GrampsDatabase db, Path directory, ImageDecoders decoders) {
        return new MediaImages(db, directory, null, true, false, decoders, new Store());
    }

    /** No media at all. */
    public static MediaImages none(GrampsDatabase db) {
        return new MediaImages(db, null, null, ImageDecoders.DEFAULT);
    }

    /** The file of a media object, unless there is none to read or it may not be read. */
    Optional<Path> file(Media media) {
        return located(media).filter(file -> packaged || anywhere || inMediaDir(file));
    }

    /**
     * How many media files are not read because they are outside the media directory, for a warning at start. Their
     * paths are checked only, not whether the files exist.
     */
    public long outsideMediaDir() {
        return db.media().all().stream()
                .filter(media -> located(media).isPresent() && file(media).isEmpty())
                .count();
    }

    /** Where the file of a media object is, if the path is usable; whether it may be read is not checked. */
    private Optional<Path> located(Media media) {
        if (mediaDir == null) {
            return Optional.empty();
        }
        try {
            if (packaged) {
                // Where GrampsPackage.extract put it, if it did.
                Path root = mediaDir.toAbsolutePath().normalize();
                Path file =
                        root.resolve(GrampsPackage.archiveName(media.path())).normalize();
                return file.startsWith(root) && !file.equals(root) ? Optional.of(file) : Optional.empty();
            }
            Path path = Path.of(media.path());
            if (!path.isAbsolute()) {
                return Optional.of(mediaDir.resolve(path));
            }
            if (exportMediaPath != null && !exportMediaPath.isBlank()) {
                Path exportBase = Path.of(exportMediaPath);
                // Under the export's media path: in the media directory, unless still where the export says and
                // allowed to be read there.
                if (exportBase.isAbsolute()
                        && path.startsWith(exportBase)
                        && !(Files.exists(path) && (anywhere || inMediaDir(path)))) {
                    return Optional.of(mediaDir.resolve(exportBase.relativize(path)));
                }
            }
            return Optional.of(path);
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private boolean inMediaDir(Path file) {
        Path root = mediaDir.toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(root) && !normalized.equals(root);
    }

    /**
     * The size and orientation of an image, if the file exists and is in a format we can decode; read once per
     * media object from the file's header.
     */
    public Optional<ImageInfo> info(Media media) {
        return header(media).map(Header::info);
    }

    private Optional<Header> header(Media media) {
        Optional<Header> header = store.headers.get(media.handle());
        if (header != null) {
            return header;
        }
        // Read outside the map, whose computeIfAbsent would hold up other media while the file is read. Two
        // requests at once may both read it.
        header = Optional.empty();
        boolean lasting = true;
        Optional<Path> file = file(media);
        if (file.isPresent()) {
            try (InputStream in = Files.newInputStream(file.get())) {
                var reader = decoders.open(in);
                header = Optional.of(new Header(reader.info(), reader.mimeType()));
            } catch (IOException e) {
                lasting = lasting(e);
            } catch (RuntimeException e) {
                // A malformed header the decoder does not catch: the file cannot be shown.
            }
        }
        if (lasting) {
            store.headers.putIfAbsent(media.handle(), header);
        }
        return header;
    }

    /**
     * Whether a failure to read a file will stay: it is not an image we can read, it ends early, or it is not there.
     * Other errors, such as of a disk or network mount, may pass, so their outcome is not kept: before, one of them
     * hid the image until the export was loaded again.
     */
    private static boolean lasting(IOException e) {
        return e instanceof ImageException || e instanceof EOFException || e instanceof NoSuchFileException;
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
        Path file = file(media).filter(Files::isRegularFile).orElse(null);
        if (file == null) {
            return Optional.empty();
        }
        // The format found in the file, which may differ from what Gramps recorded.
        Optional<Header> header = header(media);
        if (header.isPresent()) {
            return Optional.of(new Original(file, header.get().mimeType()));
        }
        String mime = media.mime() == null ? "" : media.mime().toLowerCase(Locale.ROOT);
        if (contentIs(file, mime)) {
            return Optional.of(new Original(file, mime));
        }
        return Optional.empty();
    }

    /**
     * Whether a file begins as files of a type do: images and PDF documents we serve without decoding them. Checked
     * so that the type an export gives cannot have other files, such as keys or settings, served as it.
     */
    static boolean contentIs(Path file, String mime) {
        byte[] start;
        long size;
        try (InputStream in = Files.newInputStream(file)) {
            start = in.readNBytes(1024);
            size = Files.size(file);
        } catch (IOException e) {
            return false;
        }
        String head = new String(start, StandardCharsets.ISO_8859_1);
        return switch (mime) {
            case "image/png" -> head.startsWith("\u0089PNG\r\n\u001a\n");
            case "image/gif" -> head.startsWith("GIF87a") || head.startsWith("GIF89a");
            case "image/webp" -> head.startsWith("RIFF") && head.startsWith("WEBP", 8);
            case "image/avif" -> head.startsWith("ftyp", 4) && avifBrand(start);
            // Two letters alone say little; the header also gives the file's size.
            case "image/bmp" -> head.startsWith("BM") && start.length >= 6 && littleEndianInt(start, 2) == size;
            case "image/tiff" -> head.startsWith("II*\0") || head.startsWith("MM\0*");
            // PDF readers accept the header anywhere in the first kilobyte.
            case "application/pdf" -> head.contains("%PDF-");
            default -> false;
        };
    }

    /** Whether an ISO file type box lists an AVIF brand, as the major or a compatible one. */
    private static boolean avifBrand(byte[] start) {
        int boxSize = (start[0] & 0xFF) << 24 | (start[1] & 0xFF) << 16 | (start[2] & 0xFF) << 8 | start[3] & 0xFF;
        int end = Math.min(start.length, boxSize);
        // The major brand at 8, the minor version at 12, then the compatible brands.
        for (int i = 8; i + 4 <= end; i += i == 8 ? 8 : 4) {
            String brand = new String(start, i, 4, StandardCharsets.ISO_8859_1);
            if (brand.equals("avif") || brand.equals("avis")) {
                return true;
            }
        }
        return false;
    }

    private static long littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | (bytes[offset + 1] & 0xFFL) << 8
                | (bytes[offset + 2] & 0xFFL) << 16
                | (bytes[offset + 3] & 0xFFL) << 24;
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

    /**
     * The image for the media page, made again when it has left the cache. Requests for it while it is being made
     * wait for that instead of making it again: a large scan takes seconds, and many requests at once would
     * otherwise each take them.
     */
    public Optional<Jpeg> display(Media media) {
        if (!readable(media)) {
            return Optional.empty();
        }
        String key = key(media, null, Kind.DISPLAY);
        byte[] data = displayed(key);
        if (data == null) {
            // Looks in the cache again, and stores what it makes there before it is no longer in making: a request
            // either finds it being made or finds it made.
            FutureTask<Optional<byte[]>> task = new FutureTask<>(() -> {
                byte[] cached = displayed(key);
                if (cached != null) {
                    return Optional.of(cached);
                }
                Optional<byte[]> made = make(media, null, Kind.DISPLAY);
                made.ifPresent(bytes -> keepDisplayed(key, bytes));
                return made;
            });
            FutureTask<Optional<byte[]>> running = store.making.putIfAbsent(key, task);
            if (running == null) {
                try {
                    task.run();
                } finally {
                    store.making.remove(key, task);
                }
            } else {
                task = running;
            }
            try {
                data = task.get().orElse(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (ExecutionException e) {
                return Optional.empty();
            }
            if (data == null) {
                return Optional.empty();
            }
        }
        return Optional.of(new Jpeg(data, "\"" + key + "\""));
    }

    private byte[] displayed(String key) {
        synchronized (store.displayed) {
            return store.displayed.get(key);
        }
    }

    private void keepDisplayed(String key, byte[] data) {
        synchronized (store.displayed) {
            if (store.displayed.put(key, data) == null) {
                store.displayedBytes += data.length;
            }
            var oldest = store.displayed.entrySet().iterator();
            while (store.displayedBytes > DISPLAY_CACHE && oldest.hasNext()) {
                store.displayedBytes -= oldest.next().getValue().length;
                oldest.remove();
            }
        }
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
        FutureTask<Optional<byte[]>> existing = store.cache.putIfAbsent(key, task);
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
            // Failed for a reason that may pass, such as a read error: tried again on the next request.
            store.cache.remove(key, task);
            return Optional.empty();
        }
    }

    /**
     * Makes an image; empty if the file cannot be made into one, which is kept.
     *
     * @throws UncheckedIOException if the file could not be read for a reason that may pass, see {@link #lasting}
     */
    private Optional<byte[]> make(Media media, Region region, Kind kind) throws InterruptedException {
        PERMITS.acquire();
        // Only called for readable media, which have a file.
        try (InputStream in = Files.newInputStream(file(media).orElseThrow())) {
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
            if (!lasting(e)) {
                throw new UncheckedIOException(e);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            // A malformed file the decoder does not catch; the page shows no image instead of failing.
            System.err.println("Cannot make an image of media " + media.id() + ": "
                    + e.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            PERMITS.release();
        }
    }
}
