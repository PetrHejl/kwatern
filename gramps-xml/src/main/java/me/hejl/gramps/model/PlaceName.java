package me.hejl.gramps.model;

/** A name of a place, optionally in a language and valid only for the given date. */
public record PlaceName(String value, String lang, GrampsDate date) {}
