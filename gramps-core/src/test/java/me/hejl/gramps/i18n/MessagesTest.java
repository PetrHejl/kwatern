package me.hejl.gramps.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static final String DATES = "me/hejl/gramps/date/messages";

    @Test
    void formatsNumbersForTheLanguage() {
        // With ICU's locale data: the JDK's formats in a native image know only the default locale.
        assertEquals("about 12,345", Messages.load(DATES, Locale.ENGLISH).format("date.about", 12345));
        assertEquals("asi 12 345", Messages.load(DATES, Locale.of("cs")).format("date.about", 12345));
        assertEquals("um 12.345", Messages.load(DATES, Locale.GERMAN).format("date.about", 12345));
        assertEquals("about 1850", Messages.load(DATES, Locale.ENGLISH).format("date.about", "1850"));
    }
}
