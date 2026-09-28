package me.hejl.gramps.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the parser in sync with Gramps' XML schema (grampsxml.rng of the Gramps release in the build).
 * A sample document containing every element and attribute of the schema must parse without the parser
 * skipping an element or leaving an attribute unread. When Gramps changes the schema, this fails until the
 * parser reads the new vocabulary or explicitly ignores it.
 */
class SchemaCoverageTest {

    @Test
    void parserReadsEverythingTheSchemaDefines() throws IOException {
        SchemaSample.Sample sample = SchemaSample.generate(Path.of(System.getProperty("gramps.schema")));
        assertTrue(
                sample.vocabulary().size() > 200,
                "sample covers " + sample.vocabulary().size() + " names");

        ParseResult result =
                GrampsXml.read(new ByteArrayInputStream(sample.xml().getBytes(StandardCharsets.UTF_8)));

        List<String> uncovered = result.warnings().stream()
                .filter(w -> w.startsWith("Skipped unknown element") || w.startsWith("Ignored unknown attribute"))
                .toList();
        assertEquals(List.of(), uncovered);
    }
}
