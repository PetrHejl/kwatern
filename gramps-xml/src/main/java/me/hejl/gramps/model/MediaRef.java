package me.hejl.gramps.model;

import java.util.List;

/** Link to a media object, optionally to a region of it such as a face in a photo. */
public record MediaRef(
        String media,
        boolean priv,
        Region region,
        List<Attribute> attributes,
        List<String> citations,
        List<String> notes) {

    public MediaRef {
        attributes = List.copyOf(attributes);
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
