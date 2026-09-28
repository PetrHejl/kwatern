package me.hejl.gramps.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Person;
import org.junit.jupiter.api.Test;

class GrampsXmlParserTest {

    private static final String PROLOG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE database PUBLIC "-//Gramps//DTD Gramps XML 1.7.2//EN"
            "http://gramps-project.org/xml/1.7.2/grampsxml.dtd">
            """;

    private static ParseResult parse(String body) throws IOException {
        return parse(body, "http://gramps-project.org/xml/1.7.2/");
    }

    private static ParseResult parse(String body, String namespace) throws IOException {
        String xml = PROLOG + "<database xmlns=\"" + namespace + "\">" + body + "</database>";
        return GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void skipsUnknownElementsAndReportsThemOnce() throws IOException {
        ParseResult result = parse("""
                <people>
                  <person handle="_p1" change="1" id="I1">
                    <gender>F</gender>
                    <hologram><nested/></hologram>
                    <name type="Birth Name"><first>Anna</first><surname>Nováková</surname></name>
                  </person>
                  <person handle="_p2" change="1" id="I2"><gender>M</gender><hologram/></person>
                </people>
                <futurestuff><x/></futurestuff>
                """);

        Person anna = result.database().people().byId("I1").orElseThrow();
        assertEquals("Anna", anna.primaryName().first());
        assertEquals("Nováková", anna.primaryName().primarySurname().value());
        assertEquals(2, result.database().people().size());
        assertTrue(
                result.warnings().contains("Skipped unknown element <hologram> in <person> (2x)"),
                result.warnings()::toString);
        assertTrue(
                result.warnings().contains("Skipped unknown element <futurestuff> in <database>"),
                result.warnings()::toString);
    }

    @Test
    void reportsUnknownAttributes() throws IOException {
        ParseResult result = parse("""
                <people>
                  <person handle="_p1" change="1" id="I1" mood="happy"><gender>F</gender></person>
                </people>
                """);
        assertEquals(List.of("Ignored unknown attribute mood on <person>"), result.warnings());
    }

    @Test
    void readsNewerDateModifiers() throws IOException {
        ParseResult result = parse("""
                <events>
                  <event handle="_e1" change="1" id="E1">
                    <type>Residence</type><dateval val="1901-02-03" type="from"/>
                  </event>
                  <event handle="_e2" change="1" id="E2"><type>Birth</type><dateval val="1901" type="someday"/></event>
                  <event handle="_e3" change="1" id="E3"><type>Death</type><dateval val="winter 1901"/></event>
                </events>
                """);

        var events = result.database().events();
        GrampsDate from = events.byId("E1").orElseThrow().date();
        assertEquals(GrampsDate.Modifier.FROM, from.modifier());
        assertEquals(new DateValue(1901, 2, 3), from.start());
        assertEquals(
                GrampsDate.Modifier.NONE, events.byId("E2").orElseThrow().date().modifier());
        assertEquals(
                GrampsDate.textOnly("winter 1901"),
                events.byId("E3").orElseThrow().date());
        assertEquals(2, result.warnings().size(), result.warnings()::toString);
    }

    @Test
    void readsGzippedInput() throws IOException {
        String xml = PROLOG + """
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <header><created date="2026-09-26" version="6.0.8"/><mediapath>/media</mediapath></header>
                </database>
                """;
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(xml.getBytes(StandardCharsets.UTF_8));
        }

        ParseResult result = GrampsXml.read(new ByteArrayInputStream(bytes.toByteArray()));

        assertEquals("1.7.2", result.database().schemaVersion());
        assertEquals("6.0.8", result.database().header().grampsVersion());
        assertEquals("/media", result.database().header().mediaPath());
        assertFalse(result.database().homePerson().isPresent());
    }

    @Test
    void rejectsOtherXml() {
        assertThrows(
                GrampsParseException.class,
                () -> GrampsXml.read(
                        new ByteArrayInputStream("<html><body/></html>".getBytes(StandardCharsets.UTF_8))));
        assertThrows(GrampsParseException.class, () -> parse("", "http://example.com/other/"));
        assertThrows(GrampsParseException.class, () -> parse("", "http://gramps-project.org/xml/2.0.0/"));
        assertThrows(
                GrampsParseException.class,
                () -> GrampsXml.read(new ByteArrayInputStream("not xml at all".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void doesNotResolveExternalEntities() throws IOException {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE database [<!ENTITY secret SYSTEM "file:///etc/hostname">]>
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <header><mediapath>&secret;</mediapath></header>
                </database>
                """;
        ParseResult result;
        try {
            result = GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (GrampsParseException e) {
            return; // refusing the document is fine too
        }
        String mediaPath = result.database().header().mediaPath();
        assertTrue(mediaPath == null || mediaPath.isEmpty() || mediaPath.equals("&secret;"), mediaPath);
    }

    @Test
    void explainsTruncatedFiles() {
        byte[] xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<database xmlns=\"http://gramps-project.org/xml/1.7.2/\"><people><person"
                        .getBytes(StandardCharsets.UTF_8);
        GrampsParseException e =
                assertThrows(GrampsParseException.class, () -> GrampsXml.read(new ByteArrayInputStream(xml)));
        // The XML parser's own message, which a native image only has if its resource bundle is included.
        assertFalse(e.getMessage().contains("resource bundle"), e.getMessage());
        assertTrue(e.getMessage().startsWith("Malformed Gramps XML"), e.getMessage());
    }
}
