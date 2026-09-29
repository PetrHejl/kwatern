package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageDecoder;
import me.hejl.image.ImageDecoders;
import me.hejl.image.ImageReader;
import me.hejl.image.jpeg.JpegDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaImagesTest {

    private static final String PDF = "%PDF-1.7\n";

    @TempDir
    Path temp;

    /** A tree whose media objects have the given paths, as PDF documents, with IDs O0, O1, ... */
    private static GrampsDatabase tree(String... paths) throws IOException {
        var objects = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            objects.append("<object handle=\"_o%d\" change=\"1\" id=\"O%d\">".formatted(i, i))
                    .append("<file src=\"%s\" mime=\"application/pdf\" description=\"\"/>".formatted(paths[i]))
                    .append("</object>");
        }
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <objects>%s</objects>
                </database>
                """.formatted(objects);
        return GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .database();
    }

    private static Optional<Path> original(MediaImages images, GrampsDatabase db, String id) {
        Media media = db.media().byId(id).orElseThrow();
        return images.original(media).map(MediaImages.Original::file);
    }

    private Path file(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content, StandardCharsets.ISO_8859_1);
    }

    @Test
    void readsThePackagesMediaOnlyWhereItWasExtracted() throws IOException {
        Path extracted = temp.resolve("extracted");
        Path scan = file(extracted.resolve("scans/letter.pdf"), PDF);
        Path outside = file(temp.resolve("secret.pdf"), PDF);
        GrampsDatabase db = tree("scans/letter.pdf", "/scans/letter.pdf", outside.toString(), "../secret.pdf");

        var images = MediaImages.ofPackage(db, extracted, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(scan), original(images, db, "O0"));
        assertEquals(Optional.of(scan), original(images, db, "O1"), "a leading slash, as extraction ignores it");
        assertEquals(Optional.empty(), original(images, db, "O2"), "an absolute path outside the package");
        assertEquals(Optional.empty(), original(images, db, "O3"), "a relative path leading out");
    }

    @Test
    void readsAnExportsMediaOnlyFromTheMediaDirectoryUnlessAllowedAnywhere() throws IOException {
        Path media = temp.resolve("media");
        Path scan = file(media.resolve("scans/letter.pdf"), PDF);
        Path outside = file(temp.resolve("secret.pdf"), PDF);
        GrampsDatabase db = tree("scans/letter.pdf", scan.toString(), outside.toString(), "../secret.pdf");

        var images = new MediaImages(db, media, null, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(scan), original(images, db, "O0"));
        assertEquals(Optional.of(scan), original(images, db, "O1"), "an absolute path in the media directory");
        assertEquals(Optional.empty(), original(images, db, "O2"), "an absolute path outside");
        assertEquals(Optional.empty(), original(images, db, "O3"), "a relative path leading out");
        assertEquals(2, images.outsideMediaDir());

        // Your own tree with media all over the disk, as Gramps allows.
        var anywhere = MediaImages.anywhere(db, media, null, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(outside), original(anywhere, db, "O2"));
        assertEquals(
                Optional.of(outside.toAbsolutePath()),
                original(anywhere, db, "O3").map(Path::normalize));
        assertEquals(0, anywhere.outsideMediaDir());
    }

    @Test
    void looksForMediaUnderTheExportsMediaPathInTheMediaDirectory() throws IOException {
        // The export was made with its media in "old"; they have been copied to "new", and "old" still exists.
        Path old = file(temp.resolve("old/scan.pdf"), PDF);
        Path copied = file(temp.resolve("new/scan.pdf"), PDF);
        GrampsDatabase db = tree(old.toString());
        String exportMediaPath = temp.resolve("old").toString();

        var images = new MediaImages(db, temp.resolve("new"), exportMediaPath, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(copied), original(images, db, "O0"), "only the media directory is read");
        var anywhere = MediaImages.anywhere(db, temp.resolve("new"), exportMediaPath, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(old), original(anywhere, db, "O0"), "where the export says, while it exists");
    }

    @Test
    void servesAFileOnlyIfItsContentIsOfItsType() throws IOException {
        Path media = temp.resolve("media");
        file(media.resolve("key.pdf"), "-----BEGIN OPENSSH PRIVATE KEY-----\n");
        GrampsDatabase db = tree("key.pdf");
        assertEquals(
                Optional.empty(), original(MediaImages.anywhere(db, media, null, ImageDecoders.DEFAULT), db, "O0"));

        String[][] good = {
            {"application/pdf", "%PDF-1.4\n"},
            {"application/pdf", "junk before the header %PDF-1.4\n"},
            {"image/png", "\u0089PNG\r\n\u001a\n...."},
            {"image/gif", "GIF89a...."},
            {"image/webp", "RIFF\0\0\0\0WEBPVP8 "},
            {"image/avif", "\0\0\0\u0018ftypavif\0\0\0\0mif1"},
            {"image/avif", "\0\0\0\u001cftypmif1\0\0\0\0miafavif"},
            {"image/tiff", "II*\0...."},
            {"image/tiff", "MM\0*...."},
            {"image/bmp", "BM\n\0\0\0...."},
        };
        for (String[] test : good) {
            assertTrue(MediaImages.contentIs(file(temp.resolve("good"), test[1]), test[0]), test[0] + " " + test[1]);
        }
        String[][] bad = {
            {"application/pdf", "password=secret\n"},
            {"image/png", "PNG but not really"},
            {"image/webp", "RIFF\0\0\0\0WAVEfmt "},
            {"image/avif", "\0\0\0\u0014ftypmif1\0\0\0\0mif1avif"},
            {"image/bmp", "BM but the size is wrong"},
            {"text/html", "<html>"},
        };
        for (String[] test : bad) {
            assertFalse(MediaImages.contentIs(file(temp.resolve("bad"), test[1]), test[0]), test[0] + " " + test[1]);
        }
    }

    @Test
    void makesTheMediaPageImageOnceForRequestsAtOnce() throws Exception {
        Path media = Path.of(System.getProperty("gramps.example")).getParent();
        GrampsDatabase db = tree("1897_expeditionsmannschaft_rio_a.jpg");
        Media photo = db.media().byId("O0").orElseThrow();
        // Counts the files opened, and holds up making images until all requests are there.
        var opened = new AtomicInteger();
        var go = new CountDownLatch(1);
        ImageDecoder jpeg = new JpegDecoder();
        var decoders = new ImageDecoders(List.of(new ImageDecoder() {
            @Override
            public boolean accepts(byte[] header) {
                return jpeg.accepts(header);
            }

            @Override
            public ImageReader open(InputStream in) {
                // The first opening only reads the header.
                if (opened.incrementAndGet() > 1) {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return jpeg.open(in);
            }
        }));
        var images = new MediaImages(db, media, null, decoders);
        assertTrue(images.readable(photo));

        List<Future<Optional<MediaImages.Jpeg>>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                results.add(executor.submit(() -> images.display(photo)));
            }
            Thread.sleep(200);
            go.countDown();
        }
        for (Future<Optional<MediaImages.Jpeg>> result : results) {
            assertTrue(result.get().isPresent());
        }
        assertEquals(2, opened.get(), "the header once, the image once");
        assertTrue(images.display(photo).isPresent());
        assertEquals(2, opened.get(), "then from the cache");
    }

    @Test
    void makesAtMostTwoImagesAtOnceInAllViews() throws Exception {
        Path media = Path.of(System.getProperty("gramps.example")).getParent();
        GrampsDatabase db = tree("1897_expeditionsmannschaft_rio_a.jpg");
        Media photo = db.media().byId("O0").orElseThrow();
        // Counts the images being made, and holds them up until all requests are there.
        var counting = new AtomicBoolean();
        var making = new AtomicInteger();
        var most = new AtomicInteger();
        var go = new CountDownLatch(1);
        ImageDecoder jpeg = new JpegDecoder();
        var decoders = new ImageDecoders(List.of(new ImageDecoder() {
            @Override
            public boolean accepts(byte[] header) {
                return jpeg.accepts(header);
            }

            @Override
            public ImageReader open(InputStream in) {
                if (counting.get()) {
                    most.accumulateAndGet(making.incrementAndGet(), Math::max);
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    making.decrementAndGet();
                }
                return jpeg.open(in);
            }
        }));
        // The public and the members' view, and a version being loaded: each has its own images.
        List<MediaImages> views = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var images = new MediaImages(db, media, null, decoders);
            assertTrue(images.readable(photo));
            views.add(images);
        }
        counting.set(true);
        List<Future<Optional<MediaImages.Jpeg>>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (MediaImages images : views) {
                results.add(executor.submit(() -> images.display(photo)));
            }
            Thread.sleep(200);
            go.countDown();
        }
        for (Future<Optional<MediaImages.Jpeg>> result : results) {
            assertTrue(result.get().isPresent());
        }
        assertEquals(2, most.get());
    }
}
