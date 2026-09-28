package me.hejl.gramps.model;

/**
 * One surname of a name; a name may have several.
 *
 * @param origin how the surname was derived ({@code Patrilineal}, {@code Taken}, ...)
 */
public record Surname(String value, String prefix, boolean primary, String origin, String connector) {}
