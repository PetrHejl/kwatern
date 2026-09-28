package me.hejl.gramps.model;

/**
 * A Gramps date: a single value with an optional modifier, a range or span with two values, or
 * free text that Gramps could not parse.
 *
 * @param stop     second value of a {@link Modifier#RANGE} or {@link Modifier#SPAN}, else {@code null}
 * @param text     original text of a {@link Modifier#TEXT_ONLY} date, else {@code null}
 * @param newYear  new-year convention: {@code Mar1}, {@code Mar25}, {@code Sep1} or {@code M-D};
 *                 {@code null} for January 1
 */
public record GrampsDate(
        Modifier modifier,
        Quality quality,
        GrampsCalendar calendar,
        DateValue start,
        DateValue stop,
        String text,
        String newYear,
        boolean dualDated) {

    public enum Modifier {
        NONE,
        BEFORE,
        AFTER,
        ABOUT,
        FROM,
        TO,
        RANGE,
        SPAN,
        TEXT_ONLY
    }

    public enum Quality {
        REGULAR,
        ESTIMATED,
        CALCULATED
    }

    public static GrampsDate textOnly(String text) {
        return new GrampsDate(
                Modifier.TEXT_ONLY, Quality.REGULAR, GrampsCalendar.GREGORIAN, null, null, text, null, false);
    }

    /** Whether the date has two values ({@code between ... and ...} or {@code from ... to ...}). */
    public boolean isCompound() {
        return modifier == Modifier.RANGE || modifier == Modifier.SPAN;
    }
}
