package me.hejl.kwatern.view;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.i18n.Languages;
import me.hejl.gramps.i18n.Numbers;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.place.Coordinates;
import me.hejl.gramps.place.PlaceFormatter;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.MapMarker;
import me.hejl.kwatern.view.Pages.PlaceArea;
import me.hejl.kwatern.view.Pages.PlaceEvent;
import me.hejl.kwatern.view.Pages.PlaceLink;
import me.hejl.kwatern.view.Pages.PlaceNode;
import me.hejl.kwatern.view.Pages.PlacePage;
import me.hejl.kwatern.view.Pages.PlacesPage;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.view.Pages.SurnameEntry;
import me.hejl.kwatern.view.Pages.Tab;
import me.hejl.kwatern.view.Pages.TreeMapPage;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** The pages of places, the tree of all places, and the map of the whole tree. */
final class PlaceViews extends ViewPart {

    /** Events listed on a place page; more are only counted. */
    private static final int PLACE_EVENTS = 100;

    PlaceViews(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        super(data, index, ui, images);
    }

    public PlacePage place(Place place) {
        List<PlaceFormatter.Level> levels = ui.places().hierarchy(place, null);
        List<PlaceLink> partOf = levels.stream()
                .skip(1)
                .map(l -> new PlaceLink(
                        Urls.place(l.place()),
                        l.name(),
                        ui.type("placetype", l.place().type())))
                .toList();

        // A place that contains others is shown as an overview of everything within it.
        List<Place> children = index.placesWithin(place);
        Set<String> area = within(place);
        // By date, undated last, and in file order on the same date; each date is converted once.
        List<Event> events = area.stream()
                .flatMap(h -> index.eventsAt(h).stream())
                .map(e -> new SortedEvent(e, undatedLast(sortValue(e)), index.eventOrder(e)))
                .sorted(Comparator.comparingInt(SortedEvent::date).thenComparingInt(SortedEvent::order))
                .map(SortedEvent::event)
                .toList();
        boolean overview = !children.isEmpty();
        List<PlaceEvent> happened = events.stream()
                .limit(PLACE_EVENTS)
                .map(e -> placeEvent(e, overview && !place.handle().equals(e.place())))
                .toList();

        List<PlaceArea> within = new ArrayList<>();
        if (overview) {
            Map<String, Integer> counts = new HashMap<>();
            events.forEach(e -> counts.merge(e.place(), 1, Integer::sum));
            List<PlaceArea> areas = new ArrayList<>();
            for (Place child : children) {
                int total = within(child).stream()
                        .mapToInt(h -> counts.getOrDefault(h, 0))
                        .sum();
                areas.add(new PlaceArea(
                        Urls.place(child),
                        ui.places().name(child, null),
                        ui.type("placetype", child.type()),
                        total,
                        0));
            }
            int most = areas.stream().mapToInt(PlaceArea::events).max().orElse(0);
            areas.stream()
                    .sorted(Comparator.comparingInt(PlaceArea::events)
                            .reversed()
                            .thenComparing(PlaceArea::name, ui.order().strings()))
                    .map(a -> new PlaceArea(
                            a.url(),
                            a.name(),
                            a.type(),
                            a.events(),
                            most == 0 ? 0 : Math.max(2, 100 * a.events() / most)))
                    .forEach(within::add);
        }

        String displayed = ui.places().name(place, null);
        List<Row> otherNames = place.names().stream()
                .filter(n -> !n.value().equals(displayed) || n.date() != null)
                .map(n -> new Row(n.value(), ui.join(language(n.lang()), ui.date(n.date()))))
                .toList();
        var sources = new SourceCollector();
        sources.add(place.citations(), ui.t("sources.place"));
        return new PlacePage(
                displayed,
                partOf.reversed(),
                placeSummary(place, levels, events, overview ? area.size() - 1 : 0),
                coordinates(place),
                otherNames.size() > 1
                                || otherNames.stream().anyMatch(r -> !r.label().isEmpty())
                        ? otherNames
                        : List.of(),
                partOf,
                within,
                happened,
                events.size() - happened.size(),
                overview ? surnamesOf(events) : List.of(),
                notes(place.notes()),
                sources.uses(),
                tiles(place.media()),
                placeMarkers(place));
    }

    /** "State in USA · 68 events in 15 places within, 1628–1999". */
    private String placeSummary(Place place, List<PlaceFormatter.Level> levels, List<Event> events, int places) {
        String type = ui.type("placetype", place.type());
        String parent = levels.size() > 1 ? ui.places().title(levels.get(1).place(), null) : "";
        List<String> summary = new ArrayList<>(2);
        if (!type.isEmpty()) {
            summary.add(parent.isEmpty() ? type : ui.t("place.type_in", type, parent));
        }
        if (!events.isEmpty()) {
            List<String> years = events.stream()
                    .map(ViewPart::timelineYear)
                    .filter(y -> !y.isEmpty())
                    .toList();
            String key = places > 0 ? "place.events_within" : "place.events";
            if (years.isEmpty()) {
                summary.add(ui.t(key, events.size(), places));
            } else if (years.getFirst().equals(years.getLast())) {
                summary.add(ui.t(key + "_in", events.size(), places, years.getFirst()));
            } else {
                summary.add(ui.t(key + "_between", events.size(), places, years.getFirst(), years.getLast()));
            }
        }
        return String.join(" · ", summary);
    }

    private PlaceEvent placeEvent(Event event, boolean showPlace) {
        Place at = showPlace ? db.places().get(event.place()).orElse(null) : null;
        return new PlaceEvent(
                timelineYear(event),
                ui.type("event", event.type()),
                ui.date(event.date()),
                participants(event),
                at == null ? "" : ui.places().name(at, event.date()),
                at == null ? "" : Urls.place(at));
    }

    /** The place and every place within it, at any depth; a hierarchy that loops back is followed once. */
    private Set<String> within(Place place) {
        Set<String> handles = new LinkedHashSet<>();
        Deque<Place> todo = new ArrayDeque<>(List.of(place));
        while (!todo.isEmpty()) {
            Place next = todo.pop();
            if (handles.add(next.handle())) {
                todo.addAll(index.placesWithin(next));
            }
        }
        return handles;
    }

    /** An event with its date's sort value (undated last) and its position in the file, for sorting. */
    private record SortedEvent(Event event, int date, int order) {}

    /** Surnames of the people the events are about, most frequent first, counting each person once. */
    private List<SurnameEntry> surnamesOf(List<Event> events) {
        Map<String, Set<String>> people = new HashMap<>();
        for (Event event : events) {
            for (PrimaryObject referrer : db.referrers(event.handle())) {
                List<String> handles = switch (referrer) {
                    case Person p
                    when p.eventRefs().stream()
                            .anyMatch(r -> event.handle().equals(r.event()) && "Primary".equals(r.role())) ->
                        List.of(p.handle());
                    case Family f ->
                        Stream.of(f.father(), f.mother())
                                .filter(Objects::nonNull)
                                .toList();
                    default -> List.of();
                };
                for (String handle : handles) {
                    db.people()
                            .get(handle)
                            .filter(p -> !data.isLiving(p.handle()) && p.primaryName() != null)
                            .ifPresent(p -> {
                                String surname = ui.order().group(p.primaryName());
                                if (!surname.isEmpty()) {
                                    people.computeIfAbsent(surname, s -> new HashSet<>())
                                            .add(handle);
                                }
                            });
                }
            }
        }
        return people.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> e) ->
                                e.getValue().size())
                        .reversed()
                        .thenComparing(Map.Entry::getKey, ui.order().strings()))
                .map(e -> new SurnameEntry(
                        e.getKey(), Urls.surname(e.getKey()), e.getValue().size()))
                .toList();
    }

    private static final List<String> MARRIAGE = List.of(
            "Marriage",
            "Alternate Marriage",
            "Marriage Banns",
            "Marriage Contract",
            "Marriage License",
            "Marriage Settlement",
            "Engagement");

    /** The filters of the tree's map, "all" first. */
    private static final List<String> MAP_KINDS = List.of("all", "births", "marriages", "deaths");

    private static final List<String> MAP_PERIODS = List.of("all", "before1800", "1800s", "1900s");

    /** The marker of a place page, as JSON, or empty when maps are off or it cannot be placed. */
    private String placeMarkers(Place place) {
        if (!ui.hasMap()) {
            return "";
        }
        return located(place)
                .map(l -> Json.markers(List.of(new MapMarker(
                        l.at().latitude(),
                        l.at().longitude(),
                        "",
                        ui.places().title(l.place(), null),
                        Urls.place(l.place()),
                        l.place() == place
                                ? ""
                                : ui.t("map.shown_at", ui.places().name(l.place(), null)),
                        0,
                        false))))
                .orElse("");
    }

    /**
     * All places with events on one map, as circles by the number of events; the same on every request.
     *
     * @param requestedKind   {@code births}, {@code marriages}, {@code deaths}, or anything else for all events
     * @param requestedPeriod {@code before1800}, {@code 1800s}, {@code 1900s}, or anything else for any time
     */
    public TreeMapPage treeMap(String requestedKind, String requestedPeriod) {
        // Anything else would end up in the filter links, and none of them would be marked as chosen. It also keeps
        // the pages kept to the few filters there are.
        String kind = requestedKind != null && MAP_KINDS.contains(requestedKind) ? requestedKind : "all";
        String period = requestedPeriod != null && MAP_PERIODS.contains(requestedPeriod) ? requestedPeriod : "all";
        return ui.page("map/" + kind + "/" + period, () -> makeTreeMap(kind, period));
    }

    private TreeMapPage makeTreeMap(String kind, String period) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Located> points = new HashMap<>();
        Set<String> unmapped = new HashSet<>();
        for (Event event : db.events().all()) {
            // The lists' contains throws on null, and the schema lets an event go without a type.
            String type = event.type() == null ? "" : event.type();
            boolean kindMatches = switch (kind) {
                case "births" -> BIRTH.contains(type);
                case "marriages" -> MARRIAGE.contains(type);
                case "deaths" -> DEATH.contains(type);
                default -> true;
            };
            if (!kindMatches || event.place() == null) {
                continue;
            }
            if (!period.equals("all")) {
                OptionalInt year = event.date() == null ? OptionalInt.empty() : DateMath.gregorianYear(event.date());
                boolean inPeriod = year.isPresent()
                        && switch (period) {
                            case "before1800" -> year.getAsInt() < 1800;
                            case "1800s" -> year.getAsInt() >= 1800 && year.getAsInt() < 1900;
                            case "1900s" -> year.getAsInt() >= 1900;
                            default -> true;
                        };
                if (!inPeriod) {
                    continue;
                }
            }
            Place place = db.places().get(event.place()).orElse(null);
            if (place == null) {
                continue;
            }
            Optional<Located> at = located(place);
            if (at.isEmpty()) {
                unmapped.add(place.handle());
                continue;
            }
            counts.merge(at.get().place().handle(), 1, Integer::sum);
            points.put(at.get().place().handle(), at.get());
        }
        // Larger circles first, so that smaller ones stay on top and can be clicked.
        List<MapMarker> markers = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Located l = points.get(e.getKey());
                    return new MapMarker(
                            l.at().latitude(),
                            l.at().longitude(),
                            "",
                            ui.places().title(l.place(), null),
                            Urls.place(l.place()),
                            ui.t("map.events", e.getValue()),
                            (int) Math.min(28, 5 + 2.2 * Math.sqrt(e.getValue())),
                            false);
                })
                .toList();
        List<Tab> kinds = new ArrayList<>();
        for (String k : MAP_KINDS) {
            kinds.add(new Tab(ui.t("map.kind." + k), Urls.treeMap(k, period), k.equals(kind)));
        }
        List<Tab> periods = new ArrayList<>();
        for (String p : MAP_PERIODS) {
            periods.add(new Tab(ui.t("map.period." + p), Urls.treeMap(kind, p), p.equals(period)));
        }
        return new TreeMapPage(Json.markers(markers), counts.size(), unmapped.size(), kinds, periods);
    }

    /** All places as a tree under the places that are not within another; the same on every request. */
    public PlacesPage places() {
        return ui.page("places", this::makePlaces);
    }

    private PlacesPage makePlaces() {
        Map<String, String> names = new HashMap<>();
        Map<String, List<Place>> children = new HashMap<>();
        List<Place> roots = new ArrayList<>();
        for (Place place : db.places().all()) {
            names.put(place.handle(), ui.places().name(place, null));
            Set<String> parents = new LinkedHashSet<>();
            place.enclosedBy().forEach(r -> parents.add(r.place()));
            parents.removeIf(h -> !db.places().contains(h));
            if (parents.isEmpty()) {
                roots.add(place);
            }
            parents.forEach(
                    h -> children.computeIfAbsent(h, k -> new ArrayList<>()).add(place));
        }
        Map<String, Integer> events = new HashMap<>();
        for (Event event : db.events().all()) {
            if (event.place() != null) {
                events.merge(event.place(), 1, Integer::sum);
            }
        }
        Comparator<Place> byName =
                Comparator.comparing(p -> names.get(p.handle()), ui.order().strings());
        return new PlacesPage(
                placeNodes(roots, children, names, events, byName, new HashSet<>()),
                db.places().size());
    }

    private List<PlaceNode> placeNodes(
            List<Place> places,
            Map<String, List<Place>> children,
            Map<String, String> names,
            Map<String, Integer> events,
            Comparator<Place> byName,
            Set<String> path) {
        List<PlaceNode> nodes = new ArrayList<>();
        for (Place place : places.stream().sorted(byName).toList()) {
            // The path guards against a place hierarchy that loops back on itself.
            if (!path.add(place.handle())) {
                continue;
            }
            nodes.add(new PlaceNode(
                    Urls.place(place),
                    names.get(place.handle()),
                    ui.type("placetype", place.type()),
                    events.getOrDefault(place.handle(), 0),
                    placeNodes(
                            children.getOrDefault(place.handle(), List.of()), children, names, events, byName, path)));
            path.remove(place.handle());
        }
        return nodes;
    }

    /** "39.0483° N, 95.6780° W" in the page language, or the text as written if it cannot be read. */
    private String coordinates(Place place) {
        if (place.latitude() == null || place.longitude() == null) {
            return "";
        }
        return Coordinates.parse(place.latitude(), place.longitude())
                .map(c -> {
                    String lat = Numbers.decimal(Math.abs(c.latitude()), 4, ui.locale());
                    String lon = Numbers.decimal(Math.abs(c.longitude()), 4, ui.locale());
                    return ui.t(
                            "place.coordinates_value",
                            ui.t(c.latitude() < 0 ? "coord.south" : "coord.north", lat),
                            ui.t(c.longitude() < 0 ? "coord.west" : "coord.east", lon));
                })
                .orElse(place.latitude() + ", " + place.longitude());
    }

    /** A language code as a name in the page language, e.g. "el" as "Greek". */
    private String language(String code) {
        return Languages.name(code, ui.locale());
    }
}
