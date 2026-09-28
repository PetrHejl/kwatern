package me.hejl.gramps.date;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.GregorianCalendar;
import com.ibm.icu.util.HebrewCalendar;
import com.ibm.icu.util.IslamicCalendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import java.time.LocalDate;
import java.util.Date;
import me.hejl.gramps.model.GrampsCalendar;

/**
 * Conversions between Gramps calendar dates and serial day numbers (SDN, the Julian Day Number), which
 * Gramps uses to sort dates. ICU4J does the arithmetic except for the French Republican and Swedish
 * calendars, which ICU does not have and which follow Gramps' own formulas.
 */
public final class Calendars {

    // Gramps: gen/lib/gcalendar.py
    private static final int FRENCH_SDN_OFFSET = 2375474;
    private static final int FRENCH_DAYS_PER_4_YEARS = 1461;
    private static final int FRENCH_DAYS_PER_MONTH = 30;

    private Calendars() {}

    /**
     * Converts a date to its serial day number. Month and day are 1-based and must not be zero; Hebrew
     * months use Gramps numbering (1 = Tishri, 6 = Adar I, 7 = Adar II, 13 = Elul).
     */
    public static int toSdn(GrampsCalendar calendar, int year, int month, int day) {
        return switch (calendar) {
            case FRENCH_REPUBLICAN ->
                Math.floorDiv(year * FRENCH_DAYS_PER_4_YEARS, 4)
                        + (month - 1) * FRENCH_DAYS_PER_MONTH
                        + day
                        + FRENCH_SDN_OFFSET;
            case SWEDISH -> swedishSdn(year, month, day);
            default -> {
                Calendar icu = icuCalendar(calendar);
                set(icu, calendar, year, month, day);
                yield icu.get(Calendar.JULIAN_DAY);
            }
        };
    }

    /** The proleptic Gregorian year containing the given serial day number. */
    public static int gregorianYear(int sdn) {
        Calendar icu = icuCalendar(GrampsCalendar.GREGORIAN);
        icu.clear();
        icu.set(Calendar.JULIAN_DAY, sdn);
        return icu.get(Calendar.EXTENDED_YEAR);
    }

    public static int toSdn(LocalDate date) {
        return toSdn(GrampsCalendar.GREGORIAN, date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    /**
     * A new ICU calendar for formatting or converting dates of the given Gramps calendar; {@code null} for
     * the French Republican calendar, which ICU lacks. Swedish dates use the Julian calendar.
     */
    static Calendar icuCalendar(GrampsCalendar calendar) {
        return switch (calendar) {
            case GREGORIAN -> gregorian(new Date(Long.MIN_VALUE)); // proleptic Gregorian, as in Gramps
            case JULIAN, SWEDISH -> gregorian(new Date(Long.MAX_VALUE)); // never switches to Gregorian
            case HEBREW -> new HebrewCalendar(TimeZone.GMT_ZONE, ULocale.ROOT);
            case ISLAMIC -> {
                var islamic = new IslamicCalendar(TimeZone.GMT_ZONE, ULocale.ROOT);
                // Gramps uses the arithmetic (tabular) calendar with the civil epoch.
                islamic.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_CIVIL);
                yield islamic;
            }
            case PERSIAN -> Calendar.getInstance(TimeZone.GMT_ZONE, new ULocale("@calendar=persian"));
            case FRENCH_REPUBLICAN -> null;
        };
    }

    /** Sets the date on an ICU calendar from Gramps year, month and day. */
    static void set(Calendar icu, GrampsCalendar calendar, int year, int month, int day) {
        icu.clear();
        icu.set(Calendar.EXTENDED_YEAR, year);
        icu.set(Calendar.MONTH, icuMonth(calendar, year, month));
        icu.set(Calendar.DAY_OF_MONTH, day);
    }

    private static int icuMonth(GrampsCalendar calendar, int year, int month) {
        // Gramps treats Adar I as plain Adar in non-leap years; ICU calls that month ADAR (index 6).
        if (calendar == GrampsCalendar.HEBREW && month == 6 && !isHebrewLeapYear(year)) {
            return HebrewCalendar.ADAR;
        }
        return month - 1;
    }

    static boolean isHebrewLeapYear(int year) {
        return Math.floorMod(7 * year + 1, 19) < 7;
    }

    private static GregorianCalendar gregorian(Date change) {
        var calendar = new GregorianCalendar(TimeZone.GMT_ZONE, ULocale.ROOT);
        calendar.setGregorianChange(change);
        return calendar;
    }

    /** Sweden was one day ahead of the Julian calendar from 1700-03-01 to 1712-02-30 (sic). */
    private static int swedishSdn(int year, int month, int day) {
        int ymd = year * 10000 + month * 100 + day;
        if (ymd >= 1700_03_01 && ymd <= 1712_02_30) {
            return toSdn(GrampsCalendar.JULIAN, year, month, day) - 1;
        }
        if (ymd >= 1753_03_01) {
            return toSdn(GrampsCalendar.GREGORIAN, year, month, day);
        }
        return toSdn(GrampsCalendar.JULIAN, year, month, day);
    }
}
