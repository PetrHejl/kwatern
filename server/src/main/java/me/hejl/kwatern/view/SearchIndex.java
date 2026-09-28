package me.hejl.kwatern.view;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PlaceName;
import me.hejl.gramps.model.Surname;
import me.hejl.gramps.privacy.PublicDatabase;

/**
 * Finds people and places by name. Matching ignores case and accents ("novak" finds "Novák") and each
 * word of the query must start a word of the name ("gar lew" finds "Lewis Garner"). People who may be
 * alive are never found. Built once when the site starts; thread-safe.
 */
public final class SearchIndex {

    /** A match: the object's handle and how well it matched. */
    public record Hit(String handle, int score) {}

    public record Result(List<Hit> people, List<Hit> places, boolean more) {}

    private record Entry(String handle, Set<String> words) {}

    private final List<Entry> people = new ArrayList<>();
    private final List<Entry> places = new ArrayList<>();

    public SearchIndex(PublicDatabase data) {
        for (Person person : data.database().people().all()) {
            if (data.isLiving(person.handle())) {
                continue;
            }
            Set<String> words = new LinkedHashSet<>();
            for (Name name : person.names()) {
                addWords(words, name.first(), name.call(), name.nick(), name.title(), name.suffix());
                for (Surname surname : name.surnames()) {
                    addWords(words, surname.prefix(), surname.value());
                }
            }
            addWords(words, person.id());
            people.add(new Entry(person.handle(), words));
        }
        for (Place place : data.database().places().all()) {
            Set<String> words = new LinkedHashSet<>();
            for (PlaceName name : place.names()) {
                addWords(words, name.value());
            }
            addWords(words, place.id());
            places.add(new Entry(place.handle(), words));
        }
    }

    /** The best matches, at most {@code limit} of each kind, best first. */
    public Result find(String query, int limit) {
        List<String> terms = words(query);
        if (terms.isEmpty()) {
            return new Result(List.of(), List.of(), false);
        }
        List<Hit> peopleHits = match(people, terms);
        List<Hit> placeHits = match(places, terms);
        boolean more = peopleHits.size() > limit || placeHits.size() > limit;
        return new Result(
                peopleHits.subList(0, Math.min(limit, peopleHits.size())),
                placeHits.subList(0, Math.min(limit, placeHits.size())),
                more);
    }

    private static List<Hit> match(List<Entry> entries, List<String> terms) {
        List<Hit> hits = new ArrayList<>();
        for (Entry entry : entries) {
            int score = 0;
            for (String term : terms) {
                int best = 0;
                for (String word : entry.words()) {
                    if (word.equals(term)) {
                        best = 2;
                        break;
                    }
                    if (word.startsWith(term)) {
                        best = 1;
                    }
                }
                if (best == 0) {
                    score = -1;
                    break;
                }
                score += best;
            }
            if (score > 0) {
                hits.add(new Hit(entry.handle(), score));
            }
        }
        // Stable, so equally good matches keep the export's order.
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        return hits;
    }

    private static void addWords(Set<String> words, String... texts) {
        for (String text : texts) {
            if (text != null) {
                words.addAll(words(text));
            }
        }
    }

    /** Lower-case words without accents. */
    static List<String> words(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                // Letters that are not a base letter plus an accent, so NFD leaves them alone.
                .replace("ł", "l")
                .replace("đ", "d")
                .replace("ø", "o")
                .replace("æ", "ae")
                .replace("œ", "oe")
                .replace("ß", "ss")
                .replace("ı", "i");
        List<String> words = new ArrayList<>();
        for (String word : folded.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }
}
