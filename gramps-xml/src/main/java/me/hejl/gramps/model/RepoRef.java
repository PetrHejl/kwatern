package me.hejl.gramps.model;

import java.util.List;

public record RepoRef(String repository, String callNumber, String medium, boolean priv, List<String> notes) {

    public RepoRef {
        notes = List.copyOf(notes);
    }
}
