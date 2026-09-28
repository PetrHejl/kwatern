package me.hejl.gramps.model;

import java.util.List;

/** Link from a person or family to an event, with the role played in it (default {@code Primary}). */
public record EventRef(
        String event,
        String role,
        boolean priv,
        List<Attribute> attributes,
        List<String> citations,
        List<String> notes) {

    public EventRef {
        attributes = List.copyOf(attributes);
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
