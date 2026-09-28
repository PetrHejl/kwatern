package me.hejl.gramps.model;

import java.util.List;

public record Address(
        GrampsDate date,
        String street,
        String locality,
        String city,
        String county,
        String state,
        String country,
        String postal,
        String phone,
        boolean priv,
        List<String> citations,
        List<String> notes) {

    public Address {
        citations = List.copyOf(citations);
        notes = List.copyOf(notes);
    }
}
