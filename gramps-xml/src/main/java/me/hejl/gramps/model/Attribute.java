package me.hejl.gramps.model;

import java.util.List;

/** A typed key/value attribute; also used for source and citation attributes. */
public record Attribute(String type, String value, boolean priv, List<String> citations, List<String> notes) {

    public Attribute {
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
