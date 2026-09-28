package me.hejl.gramps.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Region;
import me.hejl.gramps.model.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Parses the example tree shipped with Gramps 6.0.8, downloaded by the build. */
class ExampleDatabaseTest {

    private static ParseResult result;
    private static GrampsDatabase db;

    @BeforeAll
    static void load() throws IOException {
        result = GrampsXml.read(Path.of(System.getProperty("gramps.example")));
        db = result.database();
    }

    @Test
    void readsEverything() {
        assertEquals("1.7.2", db.schemaVersion());
        assertEquals(2157, db.people().size());
        assertEquals(762, db.families().size());
        assertEquals(3432, db.events().size());
        assertEquals(1296, db.places().size());
        assertEquals(4, db.sources().size());
        assertEquals(2854, db.citations().size());
        assertEquals(7, db.media().size());
        assertEquals(3, db.repositories().size());
        assertEquals(19, db.notes().size());
        assertEquals(2, db.tags().size());
        assertEquals(4, db.bookmarks().size());
        assertEquals(1, db.nameFormats().size());
        assertEquals(1, db.nameMaps().size());
    }

    @Test
    void hasNoWarnings() {
        assertEquals(List.of(), result.warnings());
    }

    @Test
    void hasNoDanglingReferences() {
        assertEquals(List.of(), db.danglingReferences());
    }

    @Test
    void indexesReferrers() {
        for (Family family : db.families().all()) {
            family.children().forEach(c -> assertTrue(db.referrers(c.child()).contains(family)));
        }
        Person home = db.homePerson().orElseThrow();
        List<PrimaryObject> homeReferrers = db.referrers(home.handle());
        home.families()
                .forEach(h ->
                        assertTrue(homeReferrers.contains(db.families().get(h).orElseThrow())));
        assertEquals(homeReferrers.size(), Set.copyOf(homeReferrers).size(), "referrers listed once");
        Tag tag = db.tags().iterator().next();
        assertFalse(db.referrers(tag.handle()).isEmpty());
    }

    @Test
    void readsHomePersonWithMultipleSurnames() {
        Person home = db.homePerson().orElseThrow();
        assertEquals("I0044", home.id());
        Name name = home.primaryName();
        assertEquals("Lewis Anderson", name.first());
        assertEquals("Anderson", name.call());
        assertEquals(2, name.surnames().size());
        assertEquals("Garner", name.primarySurname().value());
        assertEquals("von", name.surnames().get(1).prefix());
        assertFalse(name.surnames().get(1).primary());
        assertEquals("Dr.", name.title());
        assertEquals("Sr", name.suffix());
    }

    @Test
    void readsCalendarAndDualDating() {
        GrampsDate julian = db.events().byId("E0105").map(Event::date).orElseThrow();
        assertEquals(GrampsCalendar.JULIAN, julian.calendar());
        assertTrue(julian.dualDated());
        assertEquals(new DateValue(1827, 4, 24), julian.start());

        GrampsDate newYear = db.events().byId("E0107").map(Event::date).orElseThrow();
        assertEquals("Mar25", newYear.newYear());
    }

    @Test
    void readsMediaRegions() {
        Person person = db.people().byId("I1123").orElseThrow();
        assertEquals(new Region(15, 27, 25, 43), person.media().getFirst().region());
    }

    @Test
    void keepsPrivacyFlags() {
        long privateAttributes = db.people().all().stream()
                .flatMap(p -> p.attributes().stream())
                .filter(a -> a.priv())
                .count();
        assertEquals(1, privateAttributes);
    }
}
