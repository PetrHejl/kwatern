package me.hejl.gramps.model;

import java.util.List;

/**
 * A family: a couple and their children. Either parent may be missing.
 *
 * @param relationship {@code Married}, {@code Unmarried}, {@code Civil Union}, {@code Unknown} or a custom type
 */
public record Family(
        String handle,
        String id,
        long change,
        boolean priv,
        String relationship,
        String father,
        String mother,
        List<EventRef> eventRefs,
        List<LdsOrdinance> ldsOrdinances,
        List<MediaRef> media,
        List<ChildRef> children,
        List<Attribute> attributes,
        List<String> notes,
        List<String> citations,
        List<String> tags)
        implements PrimaryObject {

    public Family {
        eventRefs = List.copyOf(eventRefs);
        ldsOrdinances = List.copyOf(ldsOrdinances);
        media = List.copyOf(media);
        children = List.copyOf(children);
        attributes = List.copyOf(attributes);
        notes = List.copyOf(notes);
        citations = List.copyOf(citations);
        tags = List.copyOf(tags);
    }
}
