package me.hejl.gramps.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.xml.GrampsXml;
import org.junit.jupiter.api.Test;

class CoordinatesTest {

    private static void assertParses(String lat, String lon, double expectedLat, double expectedLon) {
        Coordinates c = Coordinates.parse(lat, lon).orElseThrow(() -> new AssertionError(lat + " " + lon));
        assertEquals(expectedLat, c.latitude(), 1e-6, lat);
        assertEquals(expectedLon, c.longitude(), 1e-6, lon);
    }

    @Test
    void readsGrampsNotations() {
        assertParses("39.0483336", "-95.6780371", 39.0483336, -95.6780371);
        assertParses("50,8498", "4,3517", 50.8498, 4.3517);
        assertParses("50:30:36", "4:21:00", 50.51, 4.35);
        assertParses("50°30'36\"N", "4°21'E", 50.51, 4.35);
        assertParses("N50°30'36\"", "W4°21'", 50.51, -4.35);
        assertParses("50 30 36 S", "4 21 W", -50.51, -4.35);
        assertParses("-33.8688", "151.2093", -33.8688, 151.2093);
    }

    @Test
    void rejectsNonsense() {
        assertTrue(Coordinates.parse("", "4.35").isEmpty());
        assertTrue(Coordinates.parse("91", "4.35").isEmpty(), "latitude out of range");
        assertTrue(Coordinates.parse("50:75:00", "4.35").isEmpty(), "75 minutes");
        assertTrue(Coordinates.parse("50.5 E", "4.35").isEmpty(), "east is not a latitude");
        assertTrue(Coordinates.parse("somewhere", "4.35").isEmpty());
        assertTrue(Coordinates.parse("1 2 3 4", "4.35").isEmpty());
    }

    @Test
    void readsEveryCoordinateOfTheExampleTree() throws IOException {
        GrampsDatabase db =
                GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
        List<Place> withCoordinates = db.places().all().stream()
                .filter(p -> p.latitude() != null && p.longitude() != null)
                .toList();
        List<String> unreadable = withCoordinates.stream()
                .filter(p -> Coordinates.parse(p.latitude(), p.longitude()).isEmpty())
                .map(p -> p.id() + ": " + p.latitude() + " " + p.longitude())
                .toList();
        assertEquals(List.of(), unreadable);
        assertTrue(withCoordinates.size() > 300);
    }
}
