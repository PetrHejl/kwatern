package me.hejl.gramps.model;

import java.util.List;

/**
 * A place in the place hierarchy.
 *
 * @param title      legacy full title; usually empty in current Gramps, which builds titles from the hierarchy
 * @param latitude   latitude as written in Gramps, which accepts several notations
 * @param longitude  longitude as written in Gramps, which accepts several notations
 * @param enclosedBy places that contain this one, possibly limited to date ranges
 */
public record Place(
        String handle,
        String id,
        long change,
        boolean priv,
        String type,
        String title,
        String code,
        List<PlaceName> names,
        String latitude,
        String longitude,
        List<PlaceRef> enclosedBy,
        List<Location> alternateLocations,
        List<MediaRef> media,
        List<Url> urls,
        List<String> notes,
        List<String> citations,
        List<String> tags)
        implements PrimaryObject {

    public Place {
        names = List.copyOf(names);
        enclosedBy = List.copyOf(enclosedBy);
        alternateLocations = List.copyOf(alternateLocations);
        media = List.copyOf(media);
        urls = List.copyOf(urls);
        notes = List.copyOf(notes);
        citations = List.copyOf(citations);
        tags = List.copyOf(tags);
    }

    /** The first name, which Gramps treats as the primary one; {@code null} if there are none. */
    public String name() {
        return names.isEmpty() ? null : names.getFirst().value();
    }
}
