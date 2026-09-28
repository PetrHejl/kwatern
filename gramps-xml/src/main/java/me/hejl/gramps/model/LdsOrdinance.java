package me.hejl.gramps.model;

import java.util.List;

/**
 * Latter-day Saints ordinance.
 *
 * @param place    handle of the place
 * @param sealedTo handle of the family, for sealing to parents
 */
public record LdsOrdinance(
        String type,
        GrampsDate date,
        String temple,
        String place,
        String status,
        String sealedTo,
        boolean priv,
        List<String> citations,
        List<String> notes) {

    public LdsOrdinance {
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
