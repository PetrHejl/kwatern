package me.hejl.kwatern.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchIndexTest {

    @Test
    void foldsCaseAndAccents() {
        assertEquals(List.of("novak", "jiri"), SearchIndex.words("Novák, Jiří"));
        assertEquals(List.of("zielinski"), SearchIndex.words("ZIELIŃSKI"));
        assertEquals(List.of("lodz", "strasse"), SearchIndex.words("Łódź Straße"));
        assertEquals(List.of("новиков"), SearchIndex.words("Новиков"));
        assertEquals(List.of(), SearchIndex.words(" , - "));
    }
}
