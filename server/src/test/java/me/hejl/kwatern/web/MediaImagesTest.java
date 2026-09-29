package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageDecoders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaImagesTest {

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

    @Test
    void readsThePackagesMediaOnlyWhereItWasExtracted() throws IOException {
        Path extracted = Files.createDirectories(temp.resolve("extracted"));
        Path scan = Files.createDirectories(extracted.resolve("scans")).resolve("letter.pdf");
        Files.writeString(scan, "scan");
        Path outside = temp.resolve("secret.pdf");
        Files.writeString(outside, "not in the package");
        GrampsDatabase db = tree("scans/letter.pdf", "/scans/letter.pdf", outside.toString(), "../secret.pdf");

        var images = MediaImages.ofPackage(db, extracted, ImageDecoders.DEFAULT);
        assertEquals(Optional.of(scan), original(images, db, "O0"));
        assertEquals(Optional.of(scan), original(images, db, "O1"), "a leading slash, as extraction ignores it");
        assertEquals(Optional.empty(), original(images, db, "O2"), "an absolute path outside the package");
        assertEquals(Optional.empty(), original(images, db, "O3"), "a relative path leading out");

        // A plain export is the operator's own, and Gramps allows media anywhere.
        var own = new MediaImages(db, extracted, null, ImageDecoders.DEFAULT);
        assertTrue(original(own, db, "O2").isPresent());
    }
}
