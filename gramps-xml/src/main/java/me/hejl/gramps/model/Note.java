package me.hejl.gramps.model;

import java.util.List;

/**
 * A note.
 *
 * @param preformatted whether whitespace and line breaks are significant
 */
public record Note(
        String handle,
        String id,
        long change,
        boolean priv,
        String type,
        boolean preformatted,
        StyledText text,
        List<String> tags)
        implements PrimaryObject {

    public Note {
        tags = List.copyOf(tags);
    }
}
