package me.hejl.gramps.model;

import java.util.List;

/** Child in a family, with the relation to each parent (default {@code Birth}). */
public record ChildRef(
        String child,
        String fatherRelation,
        String motherRelation,
        boolean priv,
        List<String> citations,
        List<String> notes) {

    public ChildRef {
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
