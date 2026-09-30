package me.hejl.kwatern.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * What a member sees beyond the public view: people who may be alive, records marked private in Gramps, both or
 * neither. Written in the users file as {@code living}, {@code private}, {@code living,private} or nothing.
 */
public record Grants(boolean living, boolean privateRecords) {

    public static final Grants NONE = new Grants(false, false);

    /** Every combination; the server builds a view for each, so that a change of the users file needs none. */
    public static final List<Grants> ALL =
            List.of(NONE, new Grants(true, false), new Grants(false, true), new Grants(true, true));

    /** Reads the users file's form; fails on anything else. */
    public static Grants parse(String text) {
        if (text.isEmpty()) {
            return NONE;
        }
        boolean living = false;
        boolean privateRecords = false;
        for (String part : text.split(",", -1)) {
            switch (part) {
                case "living" -> {
                    if (living) {
                        throw new IllegalArgumentException("living is there twice");
                    }
                    living = true;
                }
                case "private" -> {
                    if (privateRecords) {
                        throw new IllegalArgumentException("private is there twice");
                    }
                    privateRecords = true;
                }
                default -> throw new IllegalArgumentException("expected living, private or both");
            }
        }
        return new Grants(living, privateRecords);
    }

    /** The users file's form, as {@link #parse} reads it. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        if (living) {
            parts.add("living");
        }
        if (privateRecords) {
            parts.add("private");
        }
        return String.join(",", parts);
    }
}
