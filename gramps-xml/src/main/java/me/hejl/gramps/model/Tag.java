package me.hejl.gramps.model;

/**
 * A tag.
 *
 * @param color colour as {@code #RRGGBB} or Gramps' 12-digit {@code #RRRRGGGGBBBB} form
 */
public record Tag(String handle, long change, String name, String color, int priority) {}
