package me.hejl.gramps.i18n;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanguagesTest {

    @Test
    void knowsTheLanguagesOfIcu() {
        assertTrue(Languages.known("en"));
        assertTrue(Languages.known("cs"));
        assertTrue(Languages.known("fr"), "without page texts, but with ICU's locale data");
        assertTrue(Languages.known("haw"), "three letters");
        assertFalse(Languages.known("qqq"));
        assertFalse(Languages.known(""));
        assertTrue(Languages.count() > 200 && Languages.count() < 400, Languages.count() + " languages");
    }
}
