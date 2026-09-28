package me.hejl.gramps.date;

import static me.hejl.gramps.model.GrampsCalendar.FRENCH_REPUBLICAN;
import static me.hejl.gramps.model.GrampsCalendar.GREGORIAN;
import static me.hejl.gramps.model.GrampsCalendar.HEBREW;
import static me.hejl.gramps.model.GrampsCalendar.ISLAMIC;
import static me.hejl.gramps.model.GrampsCalendar.JULIAN;
import static me.hejl.gramps.model.GrampsCalendar.PERSIAN;
import static me.hejl.gramps.model.GrampsCalendar.SWEDISH;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarsTest {

    private static int gregorian(int year, int month, int day) {
        return Calendars.toSdn(GREGORIAN, year, month, day);
    }

    @Test
    void gregorianAndJulian() {
        assertEquals(2451545, gregorian(2000, 1, 1));
        // Gramps uses the proleptic Gregorian calendar, so no gap in October 1582.
        assertEquals(gregorian(1582, 10, 4) + 1, gregorian(1582, 10, 5));
        // Julian 1582-10-05 is Gregorian 1582-10-15.
        assertEquals(gregorian(1582, 10, 15), Calendars.toSdn(JULIAN, 1582, 10, 5));
        assertEquals(1850, Calendars.gregorianYear(gregorian(1850, 12, 31)));
    }

    @Test
    void hebrew() {
        assertEquals(gregorian(2009, 9, 19), Calendars.toSdn(HEBREW, 5770, 1, 1));
        // 5784 is a leap year: Adar I and Adar II are separate months.
        assertEquals(gregorian(2024, 2, 10), Calendars.toSdn(HEBREW, 5784, 6, 1));
        assertEquals(gregorian(2024, 3, 11), Calendars.toSdn(HEBREW, 5784, 7, 1));
        // 5783 is not: Gramps treats Adar I and Adar II as the single Adar.
        assertEquals(gregorian(2023, 2, 22), Calendars.toSdn(HEBREW, 5783, 6, 1));
        assertEquals(gregorian(2023, 2, 22), Calendars.toSdn(HEBREW, 5783, 7, 1));
    }

    @Test
    void frenchRepublicanAndSwedish() {
        // 1 Vendémiaire an I
        assertEquals(gregorian(1792, 9, 22), Calendars.toSdn(FRENCH_REPUBLICAN, 1, 1, 1));
        // 18 Brumaire an VIII
        assertEquals(gregorian(1799, 11, 9), Calendars.toSdn(FRENCH_REPUBLICAN, 8, 2, 18));
        // Sweden's extra leap day, and the switch to Gregorian on 1753-03-01
        assertEquals(Calendars.toSdn(JULIAN, 1712, 3, 1) - 1, Calendars.toSdn(SWEDISH, 1712, 2, 30));
        assertEquals(gregorian(1753, 3, 1), Calendars.toSdn(SWEDISH, 1753, 3, 1));
        assertEquals(Calendars.toSdn(JULIAN, 1650, 6, 1), Calendars.toSdn(SWEDISH, 1650, 6, 1));
    }

    /** ICU's tabular Islamic calendar must agree with Gramps' formula. */
    @Test
    void islamicMatchesGramps() {
        List<String> differences = new ArrayList<>();
        for (int year = 1; year <= 1600; year++) {
            for (int month = 1; month <= 12; month++) {
                int icu = Calendars.toSdn(ISLAMIC, year, month, 1);
                int gramps = grampsIslamicSdn(year, month, 1);
                if (icu != gramps) {
                    differences.add(year + "-" + month + ": ICU " + icu + ", Gramps " + gramps);
                }
            }
        }
        assertEquals(List.of(), differences.stream().limit(10).toList(), differences.size() + " differences");
    }

    /**
     * ICU follows the official Iranian calendar. Gramps' 2820-year cycle formula differs from it by one day
     * in about 13% of months, e.g. it starts 1404 on 2025-03-20 instead of 03-21, so ICU is used.
     */
    @Test
    void persianNowruz() {
        assertEquals(gregorian(2024, 3, 20), Calendars.toSdn(PERSIAN, 1403, 1, 1));
        assertEquals(gregorian(2025, 3, 21), Calendars.toSdn(PERSIAN, 1404, 1, 1));
        assertEquals(gregorian(1921, 3, 21), Calendars.toSdn(PERSIAN, 1300, 1, 1));
    }

    // Gramps: gen/lib/gcalendar.py islamic_sdn
    private static int grampsIslamicSdn(int year, int month, int day) {
        double v1 = Math.ceil(29.5 * (month - 1));
        double v2 = (year - 1) * 354;
        double v3 = Math.floorDiv(3 + 11 * year, 30);
        return (int) Math.ceil(day + v1 + v2 + v3 + 1948439.5 - 1);
    }
}
