package me.hejl.gramps.name;

import com.ibm.icu.text.AlphabeticIndex;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.NameMap;
import me.hejl.gramps.model.Surname;

/**
 * Sorts and groups names by the alphabet of a locale, for example with "ch" after "h" in Czech.
 * Thread-safe.
 */
public final class NameOrder {

    private final ULocale locale;
    private final Collator collator;
    private final Map<String, String> groups = new HashMap<>();

    /**
     * @param nameMaps surname groupings from the export ({@code group_as})
     */
    public NameOrder(Locale locale, List<NameMap> nameMaps) {
        this.locale = ULocale.forLocale(locale);
        this.collator = Collator.getInstance(this.locale).freeze();
        for (NameMap map : nameMaps) {
            if ("group_as".equals(map.type())) {
                groups.put(map.key(), map.value());
            }
        }
    }

    /** A heading of an alphabetical index, such as "Č" or "Ch" in Czech, with the items filed under it. */
    public record Bucket<T>(String label, List<T> items) {}

    /** Compares strings in the locale's alphabetical order. */
    public Comparator<String> strings() {
        return collator::compare;
    }

    /**
     * Files items under the headings of the locale's alphabet, as in a printed index. Items within a
     * heading are in alphabetical order; empty headings are left out.
     */
    public <T> List<Bucket<T>> alphabeticIndex(Collection<T> items, Function<T, String> key) {
        var index = new AlphabeticIndex<T>(locale);
        for (T item : items) {
            index.addRecord(key.apply(item), item);
        }
        List<Bucket<T>> buckets = new ArrayList<>();
        for (AlphabeticIndex.Bucket<T> bucket : index) {
            if (bucket.size() > 0) {
                List<T> filed = new ArrayList<>();
                bucket.forEach(record -> filed.add(record.getData()));
                buckets.add(new Bucket<>(bucket.getLabel(), filed));
            }
        }
        return buckets;
    }

    /** Orders names by primary surname, then given name, then suffix, as Gramps does. */
    public Comparator<Name> names() {
        Comparator<Name> bySurname = Comparator.comparing(NameOrder::primarySurname, collator::compare);
        return bySurname
                .thenComparing(n -> n.first() == null ? "" : n.first(), collator::compare)
                .thenComparing(n -> n.suffix() == null ? "" : n.suffix(), collator::compare);
    }

    /**
     * The surname group a name is listed under in a surname index: the name's own group if set, else the
     * export's grouping for its surname, else the surname itself. Empty if the name has no surname.
     */
    public String group(Name name) {
        if (name.group() != null && !name.group().isBlank()) {
            return name.group();
        }
        String surname = primarySurname(name);
        return groups.getOrDefault(surname, surname);
    }

    /**
     * The primary surname without prefix. Empty for a name whose only surname is a patronymic, which Gramps
     * does not sort by.
     */
    static String primarySurname(Name name) {
        Surname surname = name.primarySurname();
        if (surname == null || surname.value() == null) {
            return "";
        }
        if (name.surnames().size() == 1
                && ("Patronymic".equals(surname.origin()) || "Matronymic".equals(surname.origin()))) {
            return "";
        }
        return surname.value();
    }
}
