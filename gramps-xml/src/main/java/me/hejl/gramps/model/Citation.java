package me.hejl.gramps.model;

import java.util.List;

/**
 * A citation of a specific location within a source.
 *
 * @param confidence 0 (very low) to 4 (very high); 2 is normal
 * @param source     handle of the cited source
 */
public record Citation(
        String handle,
        String id,
        long change,
        boolean priv,
        GrampsDate date,
        String page,
        int confidence,
        List<String> notes,
        List<MediaRef> media,
        List<Attribute> attributes,
        String source,
        List<String> tags)
        implements PrimaryObject {

    public Citation {
        notes = List.copyOf(notes);
        media = List.copyOf(media);
        attributes = List.copyOf(attributes);
        tags = List.copyOf(tags);
    }
}
