package me.hejl.gramps.privacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.xml.GrampsXml;

/** Builds small Gramps XML trees for tests. */
final class TestTrees {

    private TestTrees() {}

    static GrampsDatabase parse(String events, String people, String families, String extra) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <events>%s</events>
                  <people>%s</people>
                  <families>%s</families>
                  %s
                </database>
                """.formatted(events, people, families, extra);
        try {
            return GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .database();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An event with handle {@code _<id>}; {@code date} is a Gramps date element or empty. */
    static String event(String id, String type, String date) {
        return "<event handle=\"_%s\" change=\"1\" id=\"%s\"><type>%s</type>%s</event>".formatted(id, id, type, date);
    }

    static String dateval(String value) {
        return "<dateval val=\"" + value + "\"/>";
    }

    /** A person with handle {@code _<id>}; {@code body} holds names, event refs and family links. */
    static String person(String id, String body) {
        return "<person handle=\"_%s\" change=\"1\" id=\"%s\"><gender>U</gender>%s</person>".formatted(id, id, body);
    }

    static String name(String first) {
        return "<name type=\"Birth Name\"><first>" + first + "</first></name>";
    }

    static String eventref(String eventId) {
        return "<eventref hlink=\"_" + eventId + "\" role=\"Primary\"/>";
    }

    static String family(String id, String father, String mother, String... children) {
        var body = new StringBuilder();
        if (father != null) body.append("<father hlink=\"_").append(father).append("\"/>");
        if (mother != null) body.append("<mother hlink=\"_").append(mother).append("\"/>");
        for (String child : children)
            body.append("<childref hlink=\"_").append(child).append("\"/>");
        return "<family handle=\"_%s\" change=\"1\" id=\"%s\">%s</family>".formatted(id, id, body);
    }
}
