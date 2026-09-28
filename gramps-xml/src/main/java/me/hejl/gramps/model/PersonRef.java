package me.hejl.gramps.model;

import java.util.List;

/** Association with another person, such as godfather or witness. */
public record PersonRef(String person, String relation, boolean priv, List<String> citations, List<String> notes) {

    public PersonRef {
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
