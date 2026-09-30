package me.hejl.kwatern.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.hejl.kwatern.view.Charts.End;
import me.hejl.kwatern.view.Charts.Life;
import me.hejl.kwatern.view.Pages.LifespanChart;
import me.hejl.kwatern.view.Pages.LifespanRow;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.Tick;
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

    @Test
    void endsBarsByWhatIsKnown() {
        LifespanChart chart = Charts.lifespans(
                List.of(
                        life("Parents", 1900, 1970, End.DEATH, false),
                        life("Couple", 1930, 1950, End.UNKNOWN, true),
                        life("Children", 1960, 2026, End.ALIVE, false)),
                2026,
                "");
        List<LifespanRow> bars =
                chart.rows().stream().filter(r -> r.person() != null).toList();

        LifespanRow died = bars.get(0);
        assertEquals(0, died.fade());
        assertFalse(died.bar().contains("L"), "rounded at both ends");

        LifespanRow unknown = bars.get(1);
        assertTrue(unknown.fade() > unknown.x() && unknown.fade() < unknown.x() + unknown.width());
        assertEquals(bars.get(0).x() + bars.get(0).width(), unknown.x() + unknown.width(), "fades out over 20 years");

        LifespanRow alive = bars.get(2);
        assertEquals(0, alive.fade());
        assertTrue(alive.bar().contains("L"), "pointed at the right end");
        Tick last = chart.ticks().getLast();
        assertEquals("2020", last.label(), "the axis does not run past this year");
        assertTrue(alive.x() + alive.width() > last.x());
    }

    @Test
    void fadesOutNoFurtherThanThisYear() {
        LifespanChart chart = Charts.lifespans(
                List.of(
                        life("Parents", 1950, 2000, End.DEATH, false),
                        life("Couple", 1980, 2015, End.UNKNOWN, true),
                        life("Couple", 1982, 2020, End.DEATH, false)),
                2026,
                "");
        LifespanRow unknown = chart.rows().get(3);
        assertEquals("2020", chart.ticks().getLast().label());
        // 2026 is the end of the axis, 20 px from the right for half of a year label.
        assertEquals(chart.width() - 20, unknown.x() + unknown.width(), "stops at 2026, not 2035");
    }

    private static Life life(String group, int from, int to, End end, boolean self) {
        var link = new PersonLink("/person/I1", "Name", "", false, "N", "unknown", "");
        return new Life(group, link, from, to, end, "", self);
    }
}
