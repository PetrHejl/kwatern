package me.hejl.gramps.model;

import java.util.List;

public record Repository(
        String handle,
        String id,
        long change,
        boolean priv,
        String name,
        String type,
        List<Address> addresses,
        List<Url> urls,
        List<String> notes,
        List<String> tags)
        implements PrimaryObject {

    public Repository {
        addresses = List.copyOf(addresses);
        urls = List.copyOf(urls);
        notes = List.copyOf(notes);
        tags = List.copyOf(tags);
    }
}
