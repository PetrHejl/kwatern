package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PlaceRef;
import me.hejl.gramps.name.NameOrder;
import me.hejl.gramps.privacy.PublicDatabase;

/**
 * Lookups that pages would otherwise make by going through the whole tree on every request: the events at a place,
 * the places within one, and the people listed under a surname. Built once for each published version, in any
 * language; thread-safe.
 */
public final class TreeIndex {

    private final Map<String, List<Event>> eventsAt = new HashMap<>();
    private final Map<String, Integer> eventOrder = new HashMap<>();
    private final Map<String, List<Place>> placesWithin = new HashMap<>();
    private final Map<String, List<Person>> surnames = new HashMap<>();

    public TreeIndex(PublicDatabase data) {
        GrampsDatabase db = data.database();
        for (Event event : db.events().all()) {
            eventOrder.put(event.handle(), eventOrder.size());
            if (event.place() != null) {
                eventsAt.computeIfAbsent(event.place(), h -> new ArrayList<>()).add(event);
            }
        }
        for (Place place : db.places().all()) {
            Set<String> parents = new LinkedHashSet<>();
            for (PlaceRef ref : place.enclosedBy()) {
                parents.add(ref.place());
            }
            for (String parent : parents) {
                placesWithin.computeIfAbsent(parent, h -> new ArrayList<>()).add(place);
            }
        }
        // The group a name is listed under does not depend on the language, only its order does.
        var order = new NameOrder(Locale.ROOT, db.nameMaps());
        for (Person person : db.people().all()) {
            if (!data.isLiving(person.handle()) && person.primaryName() != null) {
                surnames.computeIfAbsent(order.group(person.primaryName()), s -> new ArrayList<>())
                        .add(person);
            }
        }
        eventsAt.replaceAll((h, list) -> List.copyOf(list));
        placesWithin.replaceAll((h, list) -> List.copyOf(list));
        surnames.replaceAll((s, list) -> List.copyOf(list));
    }

    /** The events at a place itself, not at the places within it, in file order. */
    List<Event> eventsAt(String place) {
        return eventsAt.getOrDefault(place, List.of());
    }

    /** The position of an event in the file, to keep that order among events of the same date. */
    int eventOrder(Event event) {
        return eventOrder.getOrDefault(event.handle(), Integer.MAX_VALUE);
    }

    /** The places directly within a place, in file order. */
    List<Place> placesWithin(Place place) {
        return placesWithin.getOrDefault(place.handle(), List.of());
    }

    /**
     * The people who may be shown by name, by the surname group they are listed under; the empty group holds
     * those without a surname. In file order.
     */
    Map<String, List<Person>> surnames() {
        return Collections.unmodifiableMap(surnames);
    }
}
