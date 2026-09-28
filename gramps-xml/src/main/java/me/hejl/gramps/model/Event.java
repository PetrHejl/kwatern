package me.hejl.gramps.model;

import java.util.List;

/**
 * An event such as a birth or marriage.
 *
 * @param place handle of the place, or {@code null}
 */
public record Event(
        String handle,
        String id,
        long change,
        boolean priv,
        String type,
        GrampsDate date,
        String place,
        String description,
        List<Attribute> attributes,
        List<String> notes,
        List<String> citations,
        List<MediaRef> media,
        List<String> tags)
        implements PrimaryObject {

    public Event {
        attributes = List.copyOf(attributes);
        notes = List.copyOf(notes);
        citations = List.copyOf(citations);
        media = List.copyOf(media);
        tags = List.copyOf(tags);
    }
}
