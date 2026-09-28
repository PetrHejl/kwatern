package me.hejl.gramps.place;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.xml.GrampsXml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlaceFormatterTest {

    private static GrampsDatabase db;

    @BeforeAll
    static void load() throws IOException {
        db = GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
    }

    private static Place place(String id) {
        return db.places().byId(id).orElseThrow();
    }

    private static GrampsDate on(int year, int month, int day) {
        return new GrampsDate(
                GrampsDate.Modifier.NONE,
                GrampsDate.Quality.REGULAR,
                GrampsCalendar.GREGORIAN,
                new DateValue(year, month, day),
                null,
                null,
                null,
                false);
    }

    private static PlaceFormatter formatter(String language) {
        return new PlaceFormatter(db, language, LocalDate.of(2026, 9, 26));
    }

    @Test
    void buildsTitlesFromTheHierarchy() {
        assertEquals("Gainesville, Llano, TX, USA", formatter(null).title(place("P0860"), null));
        assertEquals(4, formatter(null).hierarchy(place("P0860"), null).size());
        assertEquals("", formatter(null).title(null, null));
    }

    @Test
    void picksNamesValidAtTheDate() {
        Place stPetersburg = place("P0443");
        PlaceFormatter formatter = formatter(null);
        assertEquals("Petrograd", formatter.name(stPetersburg, on(1918, 6, 1)));
        assertEquals("Leningrad", formatter.name(stPetersburg, on(1950, 1, 1)));
        assertEquals("Saint Petersburg", formatter.name(stPetersburg, on(2000, 1, 1)));
        assertEquals("Saint Petersburg", formatter.name(stPetersburg, null), "current name without a date");
        assertEquals("?", formatter.name(stPetersburg, on(1900, 1, 1)), "no name known, as in Gramps");
    }

    @Test
    void prefersTheRequestedLanguage() {
        Place drama = place("P0435");
        assertEquals("Drama, Greece", formatter("en").title(drama, null));
        assertEquals("Δράμα, Greece", formatter("el").title(drama, null));
        assertEquals("Δράμα, Greece", formatter("cs").title(drama, null), "first name when no match");
        assertEquals("Drama", formatter("en-GB").name(drama, null));
    }
}
