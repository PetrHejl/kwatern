package me.hejl.kwatern.auth;

import java.util.ArrayList;
import java.util.List;
import me.hejl.gramps.privacy.PrivacyOptions;

/**
 * What a member sees beyond the public view: people who may be alive, records marked private in Gramps, both or
 * neither. Written in the users file as {@code living}, {@code private}, {@code living,private} or nothing.
 */
public record Grants(boolean living, boolean privateRecords) {

    public static final Grants NONE = new Grants(false, false);

    /** Living people and private records: the view with these grants has all the others have. */
    public static final Grants EVERYTHING = new Grants(true, true);

    /** Every combination; the server builds a view for each, so that a change of the users file needs none. */
    public static final List<Grants> ALL = List.of(NONE, new Grants(true, false), new Grants(false, true), EVERYTHING);

    /**
     * What members with these grants see: what the base view shows and what they are granted, so never less than
     * the base.
     */
    public PrivacyOptions over(PrivacyOptions base) {
        return new PrivacyOptions(base.hideLiving() && !living, base.hidePrivate() && !privateRecords);
    }

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

    /** What a member with these grants sees beyond the base view, in words for the command line. */
    public String describe() {
        return living && privateRecords
                ? "living people and private records"
                : living ? "living people" : privateRecords ? "private records" : "nothing";
    }

    /** The users file's form, as {@link #parse} reads it. */
    public String format() {
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
