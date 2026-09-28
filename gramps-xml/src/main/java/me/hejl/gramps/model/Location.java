package me.hejl.gramps.model;

/** Alternate location of a place, written as structured address parts. */
public record Location(
        String street,
        String locality,
        String city,
        String parish,
        String county,
        String state,
        String country,
        String postal,
        String phone) {}
