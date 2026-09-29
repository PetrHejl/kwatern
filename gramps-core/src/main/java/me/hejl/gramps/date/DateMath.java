package me.hejl.gramps.date;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsDate;

/** Sorting and year arithmetic on Gramps dates. */
public final class DateMath {

    private static final int UNIX_EPOCH_SDN = 2440588; // 1970-01-01

    private DateMath() {}

    /**
     * The value Gramps sorts dates by: the serial day number of the (first) date, with an unknown month or
     * day counted as 1. Text-only and empty dates sort as 0, before everything else.
     */
    public static int sortValue(GrampsDate date) {
        if (date == null || date.start() == null) {
            return 0;
        }
        return sdn(date, date.start());
    }

    /** The serial day number of the second value of a range or span, or of the only value otherwise. */
    public static int stopSortValue(GrampsDate date) {
        if (date == null || date.start() == null) {
            return 0;
        }
        return sdn(date, date.stop() != null ? date.stop() : date.start());
    }

    /**
     * The date as a Gregorian calendar date, if it is a single regular date with year, month and day, such
     * as a birth date an age can be computed from.
     */
    public static Optional<LocalDate> exactGregorianDate(GrampsDate date) {
        if (date == null
                || date.modifier() != GrampsDate.Modifier.NONE
                || date.quality() != GrampsDate.Quality.REGULAR
                || date.start() == null) {
            return Optional.empty();
        }
        DateValue value = date.start();
        if (value.year() == 0 || value.month() == 0 || value.day() == 0) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.ofEpochDay(sortValue(date) - UNIX_EPOCH_SDN));
    }

    /** The Gregorian year of the first value, if the date has a known year. */
    public static OptionalInt gregorianYear(GrampsDate date) {
        return year(date, date == null ? null : date.start());
    }

    /** The Gregorian year of the second value of a range or span, or of the only value otherwise. */
    public static OptionalInt gregorianStopYear(GrampsDate date) {
        return year(date, date == null ? null : date.stop() != null ? date.stop() : date.start());
    }

    private static OptionalInt year(GrampsDate date, DateValue value) {
        if (value == null || value.year() == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Calendars.gregorianYear(sdn(date, value)));
    }

    // Gramps: Date._calc_sort_value and Date._adjust_newyear
    private static int sdn(GrampsDate date, DateValue value) {
        if (value.year() == 0 && value.month() == 0 && value.day() == 0) {
            return 0;
        }
        int year = value.year() == 0 ? 1 : value.year();
        int[] split = newYearSplit(date.newYear());
        if (split != null && compare(value.month(), value.day(), split[0], split[1]) >= 0) {
            year--;
        }
        return Calendars.toSdn(date.calendar(), year, Math.max(value.month(), 1), Math.max(value.day(), 1));
    }

    /** Month and day on which the year starts, or {@code null} for January 1. */
    static int[] newYearSplit(String newYear) {
        if (newYear == null) {
            return null;
        }
        return switch (newYear.strip().toLowerCase(Locale.ROOT)) {
            case "", "jan1" -> null;
            case "mar1" -> new int[] {3, 1};
            case "mar25" -> new int[] {3, 25};
            case "sep1" -> new int[] {9, 1};
            default -> {
                String[] parts = newYear.strip().split("-");
                try {
                    yield parts.length == 2 ? new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])} : null;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
        };
    }

    private static int compare(int month1, int day1, int month2, int day2) {
        return month1 != month2 ? Integer.compare(month1, month2) : Integer.compare(day1, day2);
    }
}
