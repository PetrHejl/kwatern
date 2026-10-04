package me.hejl.gramps.privacy;

import static me.hejl.gramps.privacy.TestTrees.dateval;
import static me.hejl.gramps.privacy.TestTrees.event;
import static me.hejl.gramps.privacy.TestTrees.eventref;
import static me.hejl.gramps.privacy.TestTrees.family;
import static me.hejl.gramps.privacy.TestTrees.name;
import static me.hejl.gramps.privacy.TestTrees.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.References;
import me.hejl.gramps.xml.GrampsXml;
import org.junit.jupiter.api.Test;

class PrivacyFilterTest {

    private static PublicDatabase filter(GrampsDatabase db) {
        return PrivacyFilter.apply(db, new ProbablyAlive(db, AliveRules.DEFAULTS, 2026));
    }

    @Test
    void keepsLivingPeopleOnlyAsTreePositions() {
        var db = TestTrees.parse(
                event("B1", "Birth", dateval("1850"))
                        + event("B2", "Birth", dateval("1990"))
                        + event("M", "Marriage", dateval("2015")),
                person("I1", name("Jan") + eventref("B1") + "<parentin hlink=\"_F1\"/>")
                        + person(
                                "I2",
                                name("Jana") + eventref("B2")
                                        + "<childof hlink=\"_F1\"/><parentin hlink=\"_F2\"/><noteref hlink=\"_N1\"/>")
                        + person("I3", name("Petr") + "<parentin hlink=\"_F2\"/>"),
                family("F1", "I1", null, "I2")
                        + "<family handle=\"_F2\" change=\"1\" id=\"F2\"><father hlink=\"_I3\"/><mother hlink=\"_I2\"/>"
                        + "<eventref hlink=\"_M\" role=\"Family\"/></family>",
                """
                <places>
                  <placeobj handle="_P1" change="1" id="P1" type="City"><pname value="Brno"/></placeobj>
                </places>
                <notes>
                  <note handle="_N1" change="1" id="N1" type="Person Note"><text>secret</text></note>
                </notes>
                """);
        PublicDatabase result = filter(db);
        GrampsDatabase out = result.database();

        Person jana = out.people().byId("I2").orElseThrow();
        assertTrue(result.isLiving(jana.handle()));
        assertEquals(List.of(), jana.names());
        assertEquals(List.of(), jana.eventRefs());
        assertEquals(List.of("_F1"), jana.parentFamilies(), "keeps her place in the tree");
        assertFalse(out.events().contains("_B2"), "her birth is gone");
        assertFalse(out.notes().contains("_N1"), "her note is gone");
        assertFalse(out.events().contains("_M"), "her marriage is gone");
        assertTrue(out.places().contains("_P1"), "unreferenced objects stay");

        Person jan = out.people().byId("I1").orElseThrow();
        assertFalse(result.isLiving(jan.handle()));
        assertEquals("Jan", jan.primaryName().first());
        assertEquals(List.of(), out.danglingReferences());
    }

    @Test
    void removesPrivateObjectsAndDetails() {
        var db = TestTrees.parse(
                event("B1", "Birth", dateval("1850")) + event("D1", "Death", dateval("1900")),
                person(
                                "I1",
                                "<name type=\"Birth Name\" priv=\"1\"><first>Hidden</first></name>" + name("Shown")
                                        + eventref("B1") + eventref("D1")
                                        + "<attribute priv=\"1\" type=\"Nickname\" value=\"x\"/>"
                                        + "<citationref hlink=\"_C1\"/>")
                        + "<person handle=\"_I2\" change=\"1\" id=\"I2\" priv=\"1\"><gender>M</gender></person>",
                family("F1", "I2", null, "I1"),
                """
                <citations>
                  <citation handle="_C1" change="1" id="C1">
                    <confidence>2</confidence><sourceref hlink="_S1"/>
                  </citation>
                </citations>
                <sources>
                  <source handle="_S1" change="1" id="S1" priv="1"><stitle>Private source</stitle></source>
                </sources>
                """);
        GrampsDatabase out = filter(db).database();

        Person person = out.people().byId("I1").orElseThrow();
        assertEquals(1, person.names().size());
        assertEquals("Shown", person.primaryName().first());
        assertEquals(List.of(), person.attributes());
        assertFalse(out.people().contains("_I2"));
        assertNull(out.families().byId("F1").orElseThrow().father());
        assertFalse(out.sources().contains("_S1"));
        assertFalse(out.citations().contains("_C1"), "citation of a private source");
        assertEquals(List.of(), person.citations());
        assertEquals(List.of(), out.danglingReferences());
    }

    @Test
    void hidesParentsLinkedByAPrivateChildReference() {
        // I3 is a child of F1 openly and of F2 by a private reference, as for foster parents kept private.
        var db = TestTrees.parse(
                event("D1", "Death", dateval("1900")) + event("D2", "Death", dateval("1910")),
                person("I1", name("Jan") + eventref("D1") + "<parentin hlink=\"_F2\"/>")
                        + person("I2", name("Jana") + eventref("D1") + "<parentin hlink=\"_F1\"/>")
                        + person(
                                "I3",
                                name("Petr") + eventref("D2") + "<childof hlink=\"_F1\"/><childof hlink=\"_F2\"/>"),
                family("F1", null, "I2", "I3")
                        + "<family handle=\"_F2\" change=\"1\" id=\"F2\"><father hlink=\"_I1\"/>"
                        + "<childref hlink=\"_I3\" priv=\"1\" frel=\"Foster\"/></family>",
                "");
        var alive = new ProbablyAlive(db, AliveRules.DEFAULTS, 2026);

        GrampsDatabase out = filter(db).database();
        assertEquals(List.of("_F1"), out.people().byId("I3").orElseThrow().parentFamilies());
        assertEquals(List.of(), out.families().byId("F2").orElseThrow().children());
        assertEquals(List.of(), out.danglingReferences());

        GrampsDatabase shown =
                PrivacyFilter.apply(db, alive, new PrivacyOptions(true, false)).database();
        assertEquals(
                List.of("_F1", "_F2"),
                shown.people().byId("I3").orElseThrow().parentFamilies(),
                "with private records shown");
    }

    @Test
    void publishesOnlyLinkedMedia() {
        String objects = new StringBuilder("<objects>")
                .append("<object handle=\"_O1\" change=\"1\" id=\"O1\"><file src=\"a.jpg\" mime=\"image/jpeg\"")
                .append(" description=\"\"/></object>")
                .append("<object handle=\"_O2\" change=\"1\" id=\"O2\"><file src=\"b.jpg\" mime=\"image/jpeg\"")
                .append(" description=\"\"/></object>")
                .append("<object handle=\"_O3\" change=\"1\" id=\"O3\"><file src=\"c.jpg\" mime=\"image/jpeg\"")
                .append(" description=\"\"/></object>")
                .append("</objects>")
                .toString();
        var db = TestTrees.parse(
                event("D1", "Death", dateval("1900")),
                person("I1", name("Jan") + eventref("D1") + "<objref hlink=\"_O1\"/>")
                        + person("I2", name("Jana") + "<objref hlink=\"_O2\"/>"),
                "",
                objects);
        GrampsDatabase out = filter(db).database();

        assertTrue(out.media().contains("_O1"), "linked from a published person");
        assertFalse(out.media().contains("_O2"), "linked only from a living person");
        assertFalse(out.media().contains("_O3"), "linked from nothing");
        assertEquals(List.of(), out.danglingReferences());
    }

    @Test
    void canShowLivingPeopleAndPrivateRecords() throws IOException {
        GrampsDatabase db =
                GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
        var alive = new ProbablyAlive(db, AliveRules.DEFAULTS, 2026);

        PublicDatabase everything = PrivacyFilter.apply(db, alive, new PrivacyOptions(false, false));
        assertEquals(Set.of(), everything.living());
        assertEquals(db.objects().count(), everything.database().objects().count());
        assertEquals(
                1,
                everything.database().people().all().stream()
                        .flatMap(p -> p.attributes().stream())
                        .filter(a -> a.priv())
                        .count(),
                "keeps private flags");

        PublicDatabase livingShown = PrivacyFilter.apply(db, alive, new PrivacyOptions(false, true));
        assertEquals(Set.of(), livingShown.living());
        assertEquals(filter(db).living(), livingShown.alive(), "still knows who may be alive");
        assertTrue(livingShown.database().objects().noneMatch(PrimaryObject::priv));

        PublicDatabase privateShown = PrivacyFilter.apply(db, alive, new PrivacyOptions(true, false));
        assertFalse(privateShown.living().isEmpty());
        assertEquals(List.of(), privateShown.database().danglingReferences());
    }

    @Test
    void exampleTreeIsConsistentAfterFiltering() throws IOException {
        GrampsDatabase db =
                GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
        PublicDatabase result = filter(db);
        GrampsDatabase out = result.database();

        assertEquals(List.of(), out.danglingReferences());
        assertEquals(db.people().size(), out.people().size());
        assertTrue(out.objects().noneMatch(PrimaryObject::priv));
        assertTrue(out.people().all().stream()
                .flatMap(p -> p.attributes().stream())
                .noneMatch(a -> a.priv()));
        for (String handle : result.living()) {
            Person person = out.people().get(handle).orElseThrow();
            assertEquals(List.of(), person.names());
            List<String> references = new ArrayList<>();
            References.visit(person, (type, h) -> references.add(type + ""));
            assertTrue(references.stream().allMatch("FAMILY"::equals), references::toString);
        }
        System.out.printf(
                "example: %d living of %d people; events %d -> %d, notes %d -> %d%n",
                result.living().size(),
                db.people().size(),
                db.events().size(),
                out.events().size(),
                db.notes().size(),
                out.notes().size());
    }
}
