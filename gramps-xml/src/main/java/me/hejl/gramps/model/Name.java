package me.hejl.gramps.model;

import java.util.List;

/**
 * A person's name.
 *
 * @param alternate whether this is an alternate name rather than the primary one
 * @param sortAs    name format number used for sorting, 0 for the default
 * @param displayAs name format number used for display, 0 for the default
 */
public record Name(
        boolean alternate,
        String type,
        String first,
        String call,
        List<Surname> surnames,
        String suffix,
        String title,
        String nick,
        String familyNick,
        String group,
        int sortAs,
        int displayAs,
        GrampsDate date,
        boolean priv,
        List<String> citations,
        List<String> notes) {

    public Name {
        surnames = List.copyOf(surnames);
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }

    /** The primary surname, or the first one if none is marked primary; {@code null} if there are none. */
    public Surname primarySurname() {
        return surnames.stream()
                .filter(Surname::primary)
                .findFirst()
                .orElse(surnames.isEmpty() ? null : surnames.getFirst());
    }
}
