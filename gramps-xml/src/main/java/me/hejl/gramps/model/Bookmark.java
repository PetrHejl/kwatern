package me.hejl.gramps.model;

/**
 * A bookmark.
 *
 * @param target object type: {@code person}, {@code family}, {@code event}, ...
 */
public record Bookmark(String target, String handle) {}
