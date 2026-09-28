package me.hejl.kwatern.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChartsTest {

    @Test
    void shortensNamesKeepingTheSurname() {
        assertEquals("Lewis Anderson Garner Sr", Charts.shortenName("Lewis Anderson Garner Sr", 24));
        assertEquals("Lewis A. Garner Sr", Charts.shortenName("Lewis Anderson Garner Sr", 20));
        assertEquals("L. A. Garner Sr", Charts.shortenName("Lewis Anderson Garner Sr", 16));
        assertEquals("Iola E. B. Garner", Charts.shortenName("Iola Elizabeth Betty Garner", 20));
        assertEquals("K. Holanová", Charts.shortenName("Kateřina Holanová", 12));
        assertEquals("Garnerovicz…", Charts.shortenName("Garnerovicz-Zielinski", 12));
    }
}
