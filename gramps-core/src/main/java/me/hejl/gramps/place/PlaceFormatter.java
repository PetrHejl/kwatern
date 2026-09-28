package me.hejl.gramps.place;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.hejl.gramps.date.Calendars;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.GrampsDate.Modifier;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PlaceName;
import me.hejl.gramps.model.PlaceRef;

/**
 * Builds place titles such as "Gainesville, Llano, TX, USA" from the place hierarchy, as Gramps does with
 * automatic place titles (gen/utils/location.py).
 *
 * <p>Names and enclosing places can be limited to date ranges, so the title depends on the date of the event
 * it is shown for: Saint Petersburg is "Leningrad" in 1950. Without a date the current names are used. Where
 * a place has names in several languages the preferred language wins, otherwise the first valid name.
 */
public final class PlaceFormatter {

    private final GrampsDatabase db;
    private final String language;
    private final int todaySortValue;

    /**
     * @param language preferred language of place names (ISO 639 code such as {@code cs}), or {@code null}
     */
    public PlaceFormatter(GrampsDatabase db, String language) {
        this(db, language, LocalDate.now());
    }

    PlaceFormatter(GrampsDatabase db, String language, LocalDate today) {
        this.db = db;
        this.language = language == null ? "" : primaryLanguage(language);
        this.todaySortValue = Calendars.toSdn(today);
    }

    /** One level of a place hierarchy: the place and its name at the given date. */
    public record Level(Place place, String name) {}

    /** The full title, from the place itself up to the outermost place. */
    public String title(Place place, GrampsDate date) {
        return String.join(
                ", ", hierarchy(place, date).stream().map(Level::name).toList());
    }

    /** The place's own name at the date. */
    public String name(Place place, GrampsDate date) {
        return name(place, referenceDate(place, date));
    }

    /** The place and the places enclosing it at the date, innermost first. */
    public List<Level> hierarchy(Place place, GrampsDate date) {
        if (place == null) {
            return List.of();
        }
        int when = referenceDate(place, date);
        List<Level> levels = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Place current = place;
        while (current != null && visited.add(current.handle())) {
            levels.add(new Level(current, name(current, when)));
            current = enclosing(current, when);
        }
        return levels;
    }

    private Place enclosing(Place place, int when) {
        for (PlaceRef ref : place.enclosedBy()) {
            if (matches(when, ref.date())) {
                return db.places().get(ref.place()).orElse(null);
            }
        }
        return null;
    }

    private String name(Place place, int when) {
        String first = null;
        for (PlaceName name : place.names()) {
            if (!matches(when, name.date())) {
                continue;
            }
            String lang = name.lang() == null ? "" : primaryLanguage(name.lang());
            if (lang.equals(language)) {
                return name.value();
            }
            if (first == null) {
                first = name.value();
            }
        }
        return first != null ? first : "?";
    }

    /** The sort value to judge names and enclosing places by. */
    private int referenceDate(Place place, GrampsDate date) {
        if (date != null && DateMath.sortValue(date) != 0) {
            return DateMath.sortValue(date);
        }
        return latestDate(place);
    }

    // Gramps: location.__get_latest_date - today if any name is current, else the end of the latest name.
    private int latestDate(Place place) {
        Integer latest = null;
        for (PlaceName name : place.names()) {
            GrampsDate date = name.date();
            if (isEmpty(date) || date.modifier() == Modifier.FROM || date.modifier() == Modifier.AFTER) {
                return todaySortValue;
            }
            int value;
            if (date.isCompound()) {
                value = DateMath.stopSortValue(date);
            } else if (date.modifier() == Modifier.TO || date.modifier() == Modifier.BEFORE) {
                DateValue start = date.start();
                value = DateMath.sortValue(new GrampsDate(
                        Modifier.NONE,
                        date.quality(),
                        date.calendar(),
                        new DateValue(start.year() - 1, start.month(), start.day()),
                        null,
                        null,
                        date.newYear(),
                        date.dualDated()));
            } else {
                value = DateMath.sortValue(date);
            }
            if (latest == null || value > latest) {
                latest = value;
            }
        }
        return latest != null ? latest : todaySortValue;
    }

    // Gramps: Date.match_exact, used for place dates. "About" dates never match.
    private static boolean matches(int when, GrampsDate range) {
        if (isEmpty(range)) {
            return true;
        }
        int value = DateMath.sortValue(range);
        return switch (range.modifier()) {
            case NONE -> value == when;
            case BEFORE, TO -> value > when;
            case AFTER, FROM -> value < when;
            case RANGE, SPAN -> value <= when && when <= DateMath.stopSortValue(range);
            case ABOUT, TEXT_ONLY -> false;
        };
    }

    private static boolean isEmpty(GrampsDate date) {
        return date == null
                || (date.modifier() == Modifier.TEXT_ONLY
                        && (date.text() == null || date.text().isBlank()));
    }

    private static String primaryLanguage(String tag) {
        return Locale.forLanguageTag(tag.replace('_', '-')).getLanguage();
    }
}
