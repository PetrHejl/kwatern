package me.hejl.gramps.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One calendar point of a date. A zero year, month or day means unknown. */
public record DateValue(int year, int month, int day) {

    // Gramps writes unknown parts as question marks, e.g. "????-05-12".
    private static final Pattern FORMAT = Pattern.compile("(-?\\d+|\\?+)(?:-(\\d+|\\?+))?(?:-(\\d+|\\?+))?");

    /** Parses the Gramps XML form {@code YYYY[-MM[-DD]]}; returns {@code null} if it does not match. */
    public static DateValue parse(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = FORMAT.matcher(value.strip());
        if (!m.matches()) {
            return null;
        }
        return new DateValue(part(m.group(1)), part(m.group(2)), part(m.group(3)));
    }

    private static int part(String group) {
        return group == null || group.startsWith("?") ? 0 : Integer.parseInt(group);
    }
}
