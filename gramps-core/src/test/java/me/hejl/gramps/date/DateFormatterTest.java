package me.hejl.gramps.date;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.GrampsDate.Modifier;
import me.hejl.gramps.model.GrampsDate.Quality;
import org.junit.jupiter.api.Test;

class DateFormatterTest {

    private static final DateFormatter EN = new DateFormatter(Locale.ENGLISH);
    private static final DateFormatter CS = new DateFormatter(Locale.of("cs"));
    private static final DateFormatter DE = new DateFormatter(Locale.GERMAN);
    private static final DateFormatter SK = new DateFormatter(Locale.of("sk"));
    private static final DateFormatter FR = new DateFormatter(Locale.FRENCH);

    private static GrampsDate date(Modifier modifier, int year, int month, int day) {
        return new GrampsDate(
                modifier,
                Quality.REGULAR,
                GrampsCalendar.GREGORIAN,
                new DateValue(year, month, day),
                null,
                null,
                null,
                false);
    }

    @Test
    void formatsWithLocaleData() {
        GrampsDate date = date(Modifier.NONE, 1850, 3, 12);
        assertEquals("March 12, 1850", EN.format(date));
        assertEquals("12. 3. 1850", CS.format(date));
        assertEquals("12. März 1850", DE.format(date));
        assertEquals("um 12. März 1850", DE.format(date(Modifier.ABOUT, 1850, 3, 12)));
        assertEquals("12. 3. 1850", SK.format(date));
        // no French messages file: ICU still gives French month names, words fall back to English
        assertEquals("about 12 mars 1850", FR.format(date(Modifier.ABOUT, 1850, 3, 12)));
    }

    @Test
    void formatsPartialDates() {
        assertEquals("March 1850", EN.format(date(Modifier.NONE, 1850, 3, 0)));
        assertEquals("1850", EN.format(date(Modifier.NONE, 1850, 0, 0)));
        assertEquals("March 12", EN.format(date(Modifier.NONE, 0, 3, 12)));
        assertEquals("před 1850", CS.format(date(Modifier.BEFORE, 1850, 0, 0)));
    }

    @Test
    void formatsModifiersAndQuality() {
        var range = new GrampsDate(
                Modifier.RANGE,
                Quality.ESTIMATED,
                GrampsCalendar.GREGORIAN,
                new DateValue(1850, 0, 0),
                new DateValue(1855, 0, 0),
                null,
                null,
                false);
        assertEquals("estimated between 1850 and 1855", EN.format(range));
        assertEquals("odhadem mezi 1850 a 1855", CS.format(range));
        assertEquals("geschätzt zwischen 1850 und 1855", DE.format(range));
        assertEquals("odhadom medzi 1850 a 1855", SK.format(range));
        assertEquals("from 1901", EN.format(date(Modifier.FROM, 1901, 0, 0)));
        assertEquals("winter 1850", EN.format(GrampsDate.textOnly("winter 1850")));
        assertEquals("", EN.format(null));
    }

    @Test
    void formatsOtherCalendars() {
        var julian = new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.JULIAN,
                new DateValue(1827, 4, 24),
                null,
                null,
                null,
                true);
        assertEquals("April 24, 1826/7 (Julian)", EN.format(julian));

        var hebrew = new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.HEBREW,
                new DateValue(5770, 1, 1),
                null,
                null,
                null,
                false);
        assertEquals("1 Tishri 5770 (Hebrew)", EN.format(hebrew));

        var french = new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.FRENCH_REPUBLICAN,
                new DateValue(8, 2, 18),
                null,
                null,
                null,
                false);
        assertEquals("18 Brumaire an 8 (French Republican)", EN.format(french));

        var newYear = new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.GREGORIAN,
                new DateValue(1916, 2, 5),
                null,
                null,
                "Mar25",
                false);
        assertEquals("February 5, 1916 (Mar25)", EN.format(newYear));
    }

    @Test
    void dualYears() {
        assertEquals("1826/7", DateFormatter.dualYear(1827));
        assertEquals("1829/30", DateFormatter.dualYear(1830));
        assertEquals("1799/800", DateFormatter.dualYear(1800));
    }
}
