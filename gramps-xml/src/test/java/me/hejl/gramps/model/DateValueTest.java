package me.hejl.gramps.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DateValueTest {

    @Test
    void parsesFullAndPartialDates() {
        assertEquals(new DateValue(1850, 3, 12), DateValue.parse("1850-03-12"));
        assertEquals(new DateValue(1850, 3, 0), DateValue.parse("1850-03"));
        assertEquals(new DateValue(1850, 0, 0), DateValue.parse("1850"));
    }

    @Test
    void treatsQuestionMarksAsUnknown() {
        assertEquals(new DateValue(0, 5, 12), DateValue.parse("????-05-12"));
        assertEquals(new DateValue(1850, 0, 12), DateValue.parse("1850-??-12"));
    }

    @Test
    void rejectsText() {
        assertNull(DateValue.parse("about 1850"));
        assertNull(DateValue.parse(""));
        assertNull(DateValue.parse(null));
    }
}
