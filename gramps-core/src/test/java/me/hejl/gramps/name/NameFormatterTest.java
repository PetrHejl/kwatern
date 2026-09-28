package me.hejl.gramps.name;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.NameMap;
import me.hejl.gramps.model.Surname;
import me.hejl.gramps.xml.GrampsXml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NameFormatterTest {

    private static GrampsDatabase db;
    private static NameFormatter formatter;
    private static Name lewis;

    @BeforeAll
    static void load() throws IOException {
        db = GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
        formatter = new NameFormatter(db.nameFormats(), NameFormatter.SURNAME_GIVEN, Locale.ENGLISH);
        // Dr. Lewis Anderson "Big Louie" Garner von Zieliński Sr
        lewis = db.people().byId("I0044").orElseThrow().primaryName();
    }

    private static Name name(String first, Surname... surnames) {
        return new Name(
                false,
                "Birth Name",
                first,
                null,
                List.of(surnames),
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                false,
                List.of(),
                List.of());
    }

    private static Surname surname(String value, String origin) {
        return new Surname(value, null, true, origin, null);
    }

    @Test
    void formatsBuiltInFormats() {
        assertEquals("Garner von Zieliński, Lewis Anderson Sr", formatter.format(lewis, NameFormatter.SURNAME_GIVEN));
        assertEquals("Garner von Zieliński, Lewis Anderson Sr", formatter.display(lewis), "default format");
        assertEquals("Lewis Anderson Garner von Zieliński Sr", formatter.format(lewis, NameFormatter.GIVEN_SURNAME));
        assertEquals("Lewis Anderson", formatter.format(lewis, NameFormatter.GIVEN));
        assertEquals(
                "Garner von Zieliński, Lewis Anderson Sr", formatter.format(lewis, NameFormatter.MAIN_SURNAMES_GIVEN));
    }

    @Test
    void formatsCustomFormatsFromTheExport() {
        // <format number="-1" fmt_str="SURNAME, given (common)"/>
        assertEquals("GARNER VON ZIELIŃSKI, Lewis Anderson (Big Louie)", formatter.format(lewis, -1));
        assertEquals("Dr. L.A. Garner", formatter.formatWith(lewis, "Title initials primary"));
        assertEquals("Beauregard / Anderson", formatter.formatWith(lewis, "familynick / call"));
    }

    @Test
    void dropsPunctuationOfEmptyParts() {
        assertEquals("Novák", formatter.format(name(null, surname("Novák", "Inherited")), NameFormatter.SURNAME_GIVEN));
        assertEquals("Jan", formatter.format(name("Jan"), NameFormatter.SURNAME_GIVEN));
        assertEquals(
                "Novák", formatter.formatWith(name(null, surname("Novák", "Inherited")), "surname, given (common)"));
        assertEquals("", formatter.display(null));
    }

    @Test
    void treatsALonePatronymicAsNoSurname() {
        Name ivan = name("Ivan", surname("Petrovich", "Patronymic"));
        assertEquals(
                "Ivan Petrovich",
                formatter
                        .format(ivan, NameFormatter.MAIN_SURNAMES_GIVEN)
                        .replace(",", "")
                        .strip());
        assertEquals("", NameOrder.primarySurname(ivan));
    }

    @Test
    void sortsByTheLocaleAlphabet() {
        List<Name> names = new ArrayList<>(List.of(
                name("A", surname("Chalupa", "Inherited")),
                name("A", surname("Hrubý", "Inherited")),
                name("A", surname("Černý", "Inherited")),
                name("A", surname("Cibulka", "Inherited"))));

        names.sort(new NameOrder(Locale.of("cs"), List.of()).names());
        assertEquals(List.of("Cibulka", "Černý", "Hrubý", "Chalupa"), surnames(names));

        names.sort(new NameOrder(Locale.ENGLISH, List.of()).names());
        assertEquals(List.of("Černý", "Chalupa", "Cibulka", "Hrubý"), surnames(names));
    }

    @Test
    void groupsSurnames() {
        var order = new NameOrder(Locale.ENGLISH, List.of(new NameMap("group_as", "Fernández", "Fernandez")));
        assertEquals("Fernandez", order.group(name("Ana", surname("Fernández", "Inherited"))));
        assertEquals("Garner", order.group(lewis));
        assertTrue(order.group(name("Jan")).isEmpty());
    }

    private static List<String> surnames(List<Name> names) {
        return names.stream().map(n -> n.primarySurname().value()).toList();
    }
}
