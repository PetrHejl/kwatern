package me.hejl.gramps.date;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.GrampsDate.Modifier;
import me.hejl.gramps.model.GrampsDate.Quality;
import org.junit.jupiter.api.Test;

class DateMathTest {

    private static GrampsDate date(int year, int month, int day, String newYear) {
        return new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.GREGORIAN,
                new DateValue(year, month, day),
                null,
                null,
                newYear,
                false);
    }

    @Test
    void sortsLikeGramps() {
        assertEquals(2451545, DateMath.sortValue(date(2000, 1, 1, null)));
        // unknown month and day count as 1
        assertEquals(2451545, DateMath.sortValue(date(2000, 0, 0, null)));
        assertEquals(0, DateMath.sortValue(GrampsDate.textOnly("sometime")));
        assertEquals(0, DateMath.sortValue(null));
    }

    @Test
    void appliesNewYear() {
        // Before the new-year day nothing changes; from it on, the date belongs to the previous year.
        assertEquals(DateMath.sortValue(date(1916, 2, 5, null)), DateMath.sortValue(date(1916, 2, 5, "Mar25")));
        assertEquals(DateMath.sortValue(date(1915, 4, 1, null)), DateMath.sortValue(date(1916, 4, 1, "Mar25")));
        assertEquals(DateMath.sortValue(date(1915, 3, 25, null)), DateMath.sortValue(date(1916, 3, 25, "3-25")));
    }

    @Test
    void convertsYearsToGregorian() {
        var julian = new GrampsDate(
                Modifier.NONE,
                Quality.REGULAR,
                GrampsCalendar.JULIAN,
                new DateValue(1700, 12, 31),
                null,
                null,
                null,
                false);
        assertEquals(OptionalInt.of(1701), DateMath.gregorianYear(julian));
        assertEquals(OptionalInt.empty(), DateMath.gregorianYear(date(0, 5, 12, null)));
    }
}
