package me.hejl.gramps.model;

/** Link to an enclosing place, optionally valid only for the given date. */
public record PlaceRef(String place, GrampsDate date) {}
