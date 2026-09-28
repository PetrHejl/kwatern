package me.hejl.gramps.model;

import java.util.List;

public record Source(
        String handle,
        String id,
        long change,
        boolean priv,
        String title,
        String author,
        String pubInfo,
        String abbreviation,
        List<String> notes,
        List<MediaRef> media,
        List<Attribute> attributes,
        List<RepoRef> repositories,
        List<String> tags)
        implements PrimaryObject {

    public Source {
        notes = List.copyOf(notes);
        media = List.copyOf(media);
        attributes = List.copyOf(attributes);
        repositories = List.copyOf(repositories);
        tags = List.copyOf(tags);
    }
}
