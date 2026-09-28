package me.hejl.gramps.model;

/**
 * A custom name display format.
 *
 * @param number format number referenced by {@link Name#displayAs()} and {@link Name#sortAs()}
 * @param format Gramps format string, e.g. {@code "Surname, Given"}
 */
public record NameFormat(int number, String name, String format, boolean active) {}
