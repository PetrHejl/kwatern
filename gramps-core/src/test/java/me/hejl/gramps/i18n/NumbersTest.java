package me.hejl.gramps.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void formatsForLanguage() {
        assertEquals("39.0483", Numbers.decimal(39.0483, 4, Locale.ENGLISH));
        assertEquals("39,0483", Numbers.decimal(39.0483, 4, Locale.of("cs")));
        assertEquals("1,234.50", Numbers.decimal(1234.5, 2, Locale.ENGLISH));
    }
}
