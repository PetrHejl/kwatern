package me.hejl.gramps.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import org.junit.jupiter.api.Test;

/**
 * Checks a real export given with {@code -PgrampsFile=/path/to/tree.gramps}; skipped otherwise.
 * Only counts are printed, never personal data.
 */
class OwnExportTest {

    @Test
    void loadsCleanly() throws IOException {
        String file = System.getProperty("gramps.file");
        assumeTrue(file != null && !file.isBlank(), "no -PgrampsFile given");

        ParseResult result = GrampsXml.read(Path.of(file));
        GrampsDatabase db = result.database();
        System.out.printf(
                "people=%d families=%d events=%d places=%d sources=%d citations=%d media=%d notes=%d%n",
                db.people().size(),
                db.families().size(),
                db.events().size(),
                db.places().size(),
                db.sources().size(),
                db.citations().size(),
                db.media().size(),
                db.notes().size());
        result.warnings().forEach(w -> System.out.println("warning: " + w));

        assertEquals(List.of(), db.danglingReferences());

        Path base = Path.of(db.header().mediaPath() == null ? "" : db.header().mediaPath());
        List<String> missing = db.media().all().stream()
                .map(Media::path)
                .filter(p -> !Files.isRegularFile(base.resolve(p)))
                .toList();
        assertTrue(missing.isEmpty(), () -> missing.size() + " media files missing, e.g. " + missing.getFirst());
    }
}
