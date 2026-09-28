package me.hejl.gramps.privacy;

import static me.hejl.gramps.privacy.TestTrees.dateval;
import static me.hejl.gramps.privacy.TestTrees.event;
import static me.hejl.gramps.privacy.TestTrees.eventref;
import static me.hejl.gramps.privacy.TestTrees.family;
import static me.hejl.gramps.privacy.TestTrees.person;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.hejl.gramps.model.GrampsDatabase;
import org.junit.jupiter.api.Test;

class ProbablyAliveTest {

    private static boolean alive(GrampsDatabase db, String id) {
        return new ProbablyAlive(db, AliveRules.DEFAULTS, 2026)
                .isAlive(db.people().byId(id).orElseThrow());
    }

    @Test
    void usesOwnBirthAndDeath() {
        var db = TestTrees.parse(
                event("E1", "Birth", dateval("1990-05-01"))
                        + event("E2", "Birth", dateval("1850"))
                        + event("E3", "Death", "")
                        + event("E4", "Birth", dateval("1990"))
                        + event("E5", "Baptism", dateval("1880"))
                        + event("E6", "Birth", dateval("1920"))
                        + event("E7", "Birth", "<dateval val=\"1920\" type=\"about\"/>"),
                person("I1", eventref("E1"))
                        + person("I2", eventref("E2"))
                        + person("I3", eventref("E4") + eventref("E3"))
                        + person("I4", eventref("E5"))
                        + person("I5", eventref("E6"))
                        + person("I6", eventref("E7"))
                        + person("I7", ""),
                "",
                "");
        assertTrue(alive(db, "I1"), "born 1990");
        assertFalse(alive(db, "I2"), "born 1850");
        assertFalse(alive(db, "I3"), "death event without a date");
        assertFalse(alive(db, "I4"), "baptised 1880");
        assertTrue(alive(db, "I5"), "born 1920, could be 106");
        assertTrue(alive(db, "I6"), "born about 1920");
        assertTrue(alive(db, "I7"), "no evidence at all");
    }

    @Test
    void usesOwnOtherEvents() {
        var db = TestTrees.parse(
                event("E1", "Occupation", dateval("1880"))
                        + event("E2", "Residence", "<dateval val=\"1900\" type=\"after\"/>"),
                person("I1", eventref("E1")) + person("I2", eventref("E2")),
                "",
                "");
        assertFalse(alive(db, "I1"), "working in 1880");
        assertTrue(alive(db, "I2"), "living somewhere after 1900 could be much later");
    }

    @Test
    void usesRelatives() {
        var db = TestTrees.parse(
                event("B1", "Birth", dateval("1800"))
                        + event("B4", "Birth", dateval("1850"))
                        + event("B7", "Birth", dateval("1995"))
                        + event("M", "Marriage", dateval("1870")),
                person("I1", eventref("B1") + "<parentin hlink=\"_F1\"/>")
                        + person("I2", "<childof hlink=\"_F1\"/>")
                        + person("I3", "<childof hlink=\"_F2\"/><parentin hlink=\"_F3\"/>")
                        + person("I4", eventref("B4") + "<childof hlink=\"_F3\"/>")
                        + person("I5", "<parentin hlink=\"_F4\"/>")
                        + person("I6", "<childof hlink=\"_F4\"/>")
                        + person("I7", eventref("B7") + "<childof hlink=\"_F4\"/>")
                        + person("I8", "<parentin hlink=\"_F5\"/>"),
                family("F1", "I1", null, "I2") + family("F2", null, null, "I3") + family("F3", "I3", null, "I4")
                        + family("F4", "I5", null, "I6", "I7")
                        + "<family handle=\"_F5\" change=\"1\" id=\"F5\"><father hlink=\"_I8\"/>"
                        + "<eventref hlink=\"_M\" role=\"Family\"/></family>",
                "");
        assertFalse(alive(db, "I2"), "father born 1800");
        assertFalse(alive(db, "I3"), "child born 1850");
        assertTrue(alive(db, "I6"), "sibling born 1995");
        assertTrue(alive(db, "I5"), "child born 1995");
        assertFalse(alive(db, "I8"), "married 1870");
    }
}
