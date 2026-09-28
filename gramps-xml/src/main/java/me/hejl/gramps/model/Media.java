package me.hejl.gramps.model;

import java.util.List;

/**
 * A media object.
 *
 * @param path file path, relative to {@link Header#mediaPath()} unless absolute
 */
public record Media(
        String handle,
        String id,
        long change,
        boolean priv,
        String path,
        String mime,
        String checksum,
        String description,
        List<Attribute> attributes,
        GrampsDate date,
        List<String> notes,
        List<String> citations,
        List<String> tags)
        implements PrimaryObject {

    public Media {
        attributes = List.copyOf(attributes);
        notes = List.copyOf(notes);
        citations = List.copyOf(citations);
        tags = List.copyOf(tags);
    }
}
