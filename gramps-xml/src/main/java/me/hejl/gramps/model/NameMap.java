package me.hejl.gramps.model;

/** Surname grouping: people with surname {@code key} are grouped under {@code value}. */
public record NameMap(String type, String key, String value) {}
