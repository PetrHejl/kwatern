package me.hejl.gramps.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.name.NameFormatter;
import me.hejl.gramps.place.Coordinates;
import me.hejl.gramps.place.PlaceFormatter;
import me.hejl.gramps.xml.GrampsXml;
import org.junit.jupiter.api.Test;

/** Checks the filter on an export given with {@code -PgrampsFile=...}; prints counts only. */
class OwnExportPrivacyTest {

    @Test
    void filtersCleanly() throws IOException {
        String file = System.getProperty("gramps.file");
        assumeTrue(file != null && !file.isBlank(), "no -PgrampsFile given");

        GrampsDatabase db = GrampsXml.read(Path.of(file)).database();
        PublicDatabase result = PrivacyFilter.apply(db, new ProbablyAlive(db, AliveRules.DEFAULTS, 2026));
        GrampsDatabase out = result.database();

        assertEquals(List.of(), out.danglingReferences());
        assertTrue(out.objects().noneMatch(PrimaryObject::priv));
        var places = new PlaceFormatter(out, "cs");
        long unnamedPlaces = out.places().all().stream()
                .filter(p -> places.title(p, null).contains("?"))
                .count();
        var names = new NameFormatter(out.nameFormats(), NameFormatter.SURNAME_GIVEN, Locale.of("cs"));
        long emptyNames = out.people().all().stream()
                .filter(p -> !result.isLiving(p.handle())
                        && names.display(p.primaryName()).isEmpty())
                .count();
        System.out.printf(
                "own export: %d place titles with \"?\", %d people without a displayable name%n",
                unnamedPlaces, emptyNames);
        assertEquals(0, unnamedPlaces);
        long unreadableCoordinates = out.places().all().stream()
                .filter(p -> p.latitude() != null && p.longitude() != null)
                .filter(p -> Coordinates.parse(p.latitude(), p.longitude()).isEmpty())
                .count();
        System.out.printf("own export: %d places with coordinates that cannot be read%n", unreadableCoordinates);
        assertEquals(0, unreadableCoordinates);
        System.out.printf(
                "own export: %d living of %d people; events %d -> %d, notes %d -> %d, media %d -> %d,"
                        + " citations %d -> %d, places %d -> %d%n",
                result.living().size(),
                db.people().size(),
                db.events().size(),
                out.events().size(),
                db.notes().size(),
                out.notes().size(),
                db.media().size(),
                out.media().size(),
                db.citations().size(),
                out.citations().size(),
                db.places().size(),
                out.places().size());
    }
}
