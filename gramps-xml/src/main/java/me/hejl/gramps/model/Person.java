package me.hejl.gramps.model;

import java.util.List;

/**
 * A person.
 *
 * @param parentFamilies handles of families in which this person is a child
 * @param families       handles of families in which this person is a parent
 */
public record Person(
        String handle,
        String id,
        long change,
        boolean priv,
        Gender gender,
        List<Name> names,
        List<EventRef> eventRefs,
        List<LdsOrdinance> ldsOrdinances,
        List<MediaRef> media,
        List<Address> addresses,
        List<Attribute> attributes,
        List<Url> urls,
        List<String> parentFamilies,
        List<String> families,
        List<PersonRef> associations,
        List<String> notes,
        List<String> citations,
        List<String> tags)
        implements PrimaryObject {

    public Person {
        names = List.copyOf(names);
        eventRefs = List.copyOf(eventRefs);
        ldsOrdinances = List.copyOf(ldsOrdinances);
        media = List.copyOf(media);
        addresses = List.copyOf(addresses);
        attributes = List.copyOf(attributes);
        urls = List.copyOf(urls);
        parentFamilies = List.copyOf(parentFamilies);
        families = List.copyOf(families);
        associations = List.copyOf(associations);
        notes = List.copyOf(notes);
        citations = List.copyOf(citations);
        tags = List.copyOf(tags);
    }

    /** The first non-alternate name, or the first name if all are alternate; {@code null} if there are none. */
    public Name primaryName() {
        return names.stream().filter(n -> !n.alternate()).findFirst().orElse(names.isEmpty() ? null : names.getFirst());
    }
}
