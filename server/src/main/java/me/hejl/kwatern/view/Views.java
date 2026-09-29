package me.hejl.kwatern.view;

import java.time.Period;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.i18n.Languages;
import me.hejl.gramps.i18n.Numbers;
import me.hejl.gramps.model.Attribute;
import me.hejl.gramps.model.ChildRef;
import me.hejl.gramps.model.Citation;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Gender;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Note;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Region;
import me.hejl.gramps.model.RepoRef;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.model.Surname;
import me.hejl.gramps.name.NameFormatter;
import me.hejl.gramps.name.NameOrder;
import me.hejl.gramps.place.Coordinates;
import me.hejl.gramps.place.PlaceFormatter;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.image.ImageInfo;
import me.hejl.kwatern.view.Pages.AncestorGeneration;
import me.hejl.kwatern.view.Pages.AncestorsPage;
import me.hejl.kwatern.view.Pages.ChildView;
import me.hejl.kwatern.view.Pages.CitationEntry;
import me.hejl.kwatern.view.Pages.CitationView;
import me.hejl.kwatern.view.Pages.Cited;
import me.hejl.kwatern.view.Pages.DescendantFamily;
import me.hejl.kwatern.view.Pages.DescendantNode;
import me.hejl.kwatern.view.Pages.DescendantsPage;
import me.hejl.kwatern.view.Pages.Diagram;
import me.hejl.kwatern.view.Pages.EventRow;
import me.hejl.kwatern.view.Pages.FamilyBlock;
import me.hejl.kwatern.view.Pages.FamilyPage;
import me.hejl.kwatern.view.Pages.Heading;
import me.hejl.kwatern.view.Pages.HomePage;
import me.hejl.kwatern.view.Pages.Letter;
import me.hejl.kwatern.view.Pages.LifespanChart;
import me.hejl.kwatern.view.Pages.MapMarker;
import me.hejl.kwatern.view.Pages.MarkedPerson;
import me.hejl.kwatern.view.Pages.MediaPage;
import me.hejl.kwatern.view.Pages.MediaTile;
import me.hejl.kwatern.view.Pages.NoteView;
import me.hejl.kwatern.view.Pages.PersonHero;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.PersonMapPage;
import me.hejl.kwatern.view.Pages.PersonPage;
import me.hejl.kwatern.view.Pages.PhotoGroup;
import me.hejl.kwatern.view.Pages.PhotosPage;
import me.hejl.kwatern.view.Pages.PlaceArea;
import me.hejl.kwatern.view.Pages.PlaceEvent;
import me.hejl.kwatern.view.Pages.PlaceLink;
import me.hejl.kwatern.view.Pages.PlaceNode;
import me.hejl.kwatern.view.Pages.PlacePage;
import me.hejl.kwatern.view.Pages.PlaceStop;
import me.hejl.kwatern.view.Pages.PlacesPage;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.view.Pages.SearchPage;
import me.hejl.kwatern.view.Pages.SourceEntry;
import me.hejl.kwatern.view.Pages.SourceLetter;
import me.hejl.kwatern.view.Pages.SourcePage;
import me.hejl.kwatern.view.Pages.SourceUse;
import me.hejl.kwatern.view.Pages.SourcesPage;
import me.hejl.kwatern.view.Pages.SurnameEntry;
import me.hejl.kwatern.view.Pages.SurnameIndex;
import me.hejl.kwatern.view.Pages.SurnamePage;
import me.hejl.kwatern.view.Pages.Tab;
import me.hejl.kwatern.view.Pages.TreeMapPage;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** Builds page models from the published database for one page language. */
public final class Views {

    // Gramps: gen/lib/eventtype.py is_birth_fallback and is_death_fallback
    private static final List<String> BIRTH = List.of("Birth", "Baptism", "Christening");
    private static final List<String> DEATH = List.of("Death", "Burial", "Cremation");
    /** Children shown in the family diagram before "and N more". */
    private static final int DIAGRAM_CHILDREN = 8;
    /** People and places shown on a search page. */
    private static final int SEARCH_LIMIT = 50;
    /** Events listed on a place page; more are only counted. */
    private static final int PLACE_EVENTS = 100;

    private final PublicDatabase data;
    private final GrampsDatabase db;
    private final Ui ui;
    private final MediaImages images;
    private final TreeIndex index;

    public Views(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        this.data = data;
        this.db = data.database();
        this.index = index;
        this.ui = ui;
        this.images = images;
    }

    // ---------------------------------------------------------------- pages

    public HomePage home() {
        Map<String, List<Person>> surnames = index.surnames();
        return new HomePage(
                db.homePerson().map(this::link).orElse(null),
                db.people().size(),
                db.families().size(),
                db.events().size(),
                db.places().size(),
                db.sources().size(),
                db.media().size(),
                surnames.size(),
                db.header().created());
    }

    /** All surnames under the letters of the page language's alphabet; the same on every request. */
    public SurnameIndex surnames() {
        return ui.page("surnames", () -> {
            Map<String, List<Person>> surnames = index.surnames();
            List<Letter> letters = new ArrayList<>();
            Set<String> named = new HashSet<>(surnames.keySet());
            named.remove("");
            for (NameOrder.Bucket<String> bucket : ui.order().alphabeticIndex(named, s -> s)) {
                letters.add(new Letter(
                        bucket.label(),
                        bucket.items().stream()
                                .map(s -> new SurnameEntry(
                                        s, Urls.surname(s), surnames.get(s).size()))
                                .toList()));
            }
            return new SurnameIndex(
                    letters, surnames.getOrDefault("", List.of()).size());
        });
    }

    /** The people listed under a surname, or {@code null} if there are none. */
    public SurnamePage surname(String surname) {
        List<Person> people = index.surnames().getOrDefault(surname, List.of()).stream()
                .sorted(Comparator.comparing(Person::primaryName, ui.order().names()))
                .toList();
        return people.isEmpty()
                ? null
                : new SurnamePage(surname, people.stream().map(this::link).toList());
    }

    public PersonPage person(Person person) {
        return person(person, true);
    }

    /** A person's overview page, with or without the events of their family in the timeline. */
    public PersonPage person(Person person, boolean familyEvents) {
        boolean living = data.isLiving(person.handle());
        Name primary = person.primaryName();
        var sources = new SourceCollector();
        sources.add(person.citations(), ui.t("sources.person"));
        List<Relative> relatives = living ? List.of() : relatives(person);
        return new PersonPage(
                hero(person, Tabs.OVERVIEW),
                timeline(person, sources, familyEvents ? relatives : List.of()),
                familyEvents,
                familyEvents ? Urls.person(person) + "?family=hide" : Urls.person(person),
                lifespans(person, relatives),
                diagram(person),
                siblings(person),
                person.names().stream()
                        .filter(n -> n != primary)
                        .map(n -> new Row(ui.type("name", n.type()), ui.names().display(n)))
                        .toList(),
                attributes(person.attributes()),
                notes(person.notes()),
                sources.uses(),
                living ? List.of() : personMedia(person));
    }

    public FamilyPage family(Family family) {
        PersonLink father = link(family.father());
        PersonLink mother = link(family.mother());
        String title = familyTitle(family);
        var sources = new SourceCollector();
        sources.add(family.citations(), ui.t("sources.family"));
        List<Dated> rows = new ArrayList<>();
        for (EventRef ref : family.eventRefs()) {
            db.events()
                    .get(ref.event())
                    .ifPresent(e -> rows.add(new Dated(sortValue(e), event(e, ref, null, sources))));
        }
        List<EventRow> timeline = inDateOrder(rows);
        return new FamilyPage(
                title,
                ui.type("family", family.relationship()),
                father,
                mother,
                timeline,
                family.children().stream().map(this::child).toList(),
                attributes(family.attributes()),
                notes(family.notes()),
                sources.uses(),
                familyMedia(family));
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
                    .map(Views::timelineYear)
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

    public SourcePage source(Source source) {
        List<Row> details = new ArrayList<>();
        addRow(details, "source.author", source.author());
        addRow(details, "source.pubinfo", source.pubInfo());
        addRow(details, "source.abbreviation", source.abbreviation());
        details.addAll(attributes(source.attributes()));
        List<Row> repositories = new ArrayList<>();
        for (RepoRef ref : source.repositories()) {
            db.repositories()
                    .get(ref.repository())
                    .ifPresent(r -> repositories.add(new Row(
                            r.name() == null ? ui.t("unknown") : r.name(),
                            ui.join(
                                    ref.callNumber() == null ? "" : ui.t("source.call_number", ref.callNumber()),
                                    ui.type("medium", ref.medium())))));
        }
        List<CitationEntry> citations = db.referrers(source.handle()).stream()
                .filter(Citation.class::isInstance)
                .map(Citation.class::cast)
                .map(c -> new CitationEntry(
                        c.page() == null ? "" : c.page(),
                        ui.date(c.date()),
                        ui.t("confidence." + Math.clamp(c.confidence(), 0, 4)),
                        cited(c),
                        tiles(c.media())))
                .toList();
        return new SourcePage(
                source.title() == null || source.title().isBlank() ? ui.t("unknown") : source.title(),
                details,
                repositories,
                notes(source.notes()),
                citations,
                tiles(source.media()));
    }

    // ---------------------------------------------------------------- the family in a person's life

    /** A member of a person's close family, and what they are to the person: "father", "sister", "wife". */
    private record Relative(Person person, String relation, String group) {}

    /** Parents, spouses, siblings and children, each once, leaving out people who may be alive. */
    private List<Relative> relatives(Person person) {
        List<Relative> relatives = new ArrayList<>();
        Set<String> seen = new HashSet<>(Set.of(person.handle()));
        Person[] parents = parents(person);
        addRelative(relatives, seen, parents[0], "father", "parents");
        addRelative(relatives, seen, parents[1], "mother", "parents");
        for (String familyHandle : person.families()) {
            db.families().get(familyHandle).ifPresent(family -> {
                String partner = partnerOf(person, family);
                db.people()
                        .get(partner == null ? "" : partner)
                        .ifPresent(p ->
                                addRelative(relatives, seen, p, byGender(p, "husband", "wife", "partner"), "couple"));
            });
        }
        for (String familyHandle : person.parentFamilies()) {
            db.families()
                    .get(familyHandle)
                    .ifPresent(family -> family.children()
                            .forEach(ref -> db.people()
                                    .get(ref.child())
                                    .ifPresent(s -> addRelative(
                                            relatives,
                                            seen,
                                            s,
                                            byGender(s, "brother", "sister", "sibling"),
                                            "siblings"))));
        }
        for (String familyHandle : person.families()) {
            db.families()
                    .get(familyHandle)
                    .ifPresent(family -> family.children()
                            .forEach(ref -> db.people()
                                    .get(ref.child())
                                    .ifPresent(c -> addRelative(
                                            relatives, seen, c, byGender(c, "son", "daughter", "child"), "children"))));
        }
        return relatives;
    }

    private void addRelative(List<Relative> relatives, Set<String> seen, Person person, String relation, String group) {
        if (person != null && !data.isLiving(person.handle()) && seen.add(person.handle())) {
            relatives.add(new Relative(person, relation, group));
        }
    }

    private static String byGender(Person person, String male, String female, String other) {
        return switch (gender(person)) {
            case "male" -> male;
            case "female" -> female;
            default -> other;
        };
    }

    /**
     * Births and deaths in the family during a person's life: from their birth to their death, or on without
     * end if it is not known. Births of parents and spouses are left out, as they are not part of the life.
     */
    private List<Dated> familyEvents(Person person, List<Relative> relatives) {
        Event birth = firstEvent(person, BIRTH);
        if (relatives.isEmpty() || birth == null || sortValue(birth) == 0) {
            return List.of();
        }
        int from = sortValue(birth);
        Event death = firstEvent(person, DEATH);
        int to = death == null || sortValue(death) == 0 ? Integer.MAX_VALUE : sortValue(death);
        List<Dated> rows = new ArrayList<>();
        for (Relative relative : relatives) {
            boolean elder =
                    relative.group().equals("parents") || relative.group().equals("couple");
            Event theirBirth = firstEvent(relative.person(), BIRTH);
            if (!elder && theirBirth != null && sortValue(theirBirth) > from && sortValue(theirBirth) <= to) {
                rows.add(familyEvent(theirBirth, "birth", relative));
            }
            Event theirDeath = firstEvent(relative.person(), DEATH);
            if (theirDeath != null && sortValue(theirDeath) >= from && sortValue(theirDeath) <= to) {
                rows.add(familyEvent(theirDeath, "death", relative));
            }
        }
        return rows;
    }

    private Dated familyEvent(Event event, String kind, Relative relative) {
        return new Dated(
                sortValue(event),
                new EventRow(
                        timelineYear(event),
                        ui.t("timeline.family." + kind + "." + relative.relation()),
                        "",
                        "",
                        "",
                        "",
                        "",
                        null,
                        List.of(),
                        link(relative.person())));
    }

    /** The lifespans of the person and their close family, or {@code null} if fewer than three are known. */
    private LifespanChart lifespans(Person person, List<Relative> relatives) {
        List<Charts.Life> lives = new ArrayList<>();
        for (String group : List.of("parents", "couple", "siblings", "children")) {
            if (group.equals("couple")) {
                life(person, ui.t("lifespans.group.couple"), true).ifPresent(lives::add);
            }
            for (Relative relative : relatives) {
                if (relative.group().equals(group)) {
                    life(relative.person(), ui.t("lifespans.group." + group), false)
                            .ifPresent(lives::add);
                }
            }
        }
        if (lives.size() < 3 || lives.stream().noneMatch(Charts.Life::self)) {
            return null;
        }
        return Charts.lifespans(lives);
    }

    private Optional<Charts.Life> life(Person person, String group, boolean self) {
        Event birth = firstEvent(person, BIRTH);
        if (data.isLiving(person.handle()) || birth == null || birth.date() == null) {
            return Optional.empty();
        }
        OptionalInt from = DateMath.gregorianYear(birth.date());
        if (from.isEmpty()) {
            return Optional.empty();
        }
        Event death = firstEvent(person, DEATH);
        OptionalInt to =
                death == null || death.date() == null ? OptionalInt.empty() : DateMath.gregorianYear(death.date());
        PersonLink link = link(person);
        return Optional.of(new Charts.Life(
                group, link, from.getAsInt(), to.isPresent() ? to.getAsInt() : null, link.lifespan(), self));
    }

    // ---------------------------------------------------------------- person tabs and charts

    /** The tabs of the pages about a person. */
    public enum Tabs {
        OVERVIEW,
        ANCESTORS,
        DESCENDANTS,
        MAP
    }

    /** Generations of the ancestor chart, including the person. */
    public static final int ANCESTOR_GENERATIONS = 4;

    /** Generations of the descendant chart, including the person. */
    public static final int DESCENDANT_GENERATIONS = 3;

    private PersonHero hero(Person person, Tabs active) {
        boolean living = data.isLiving(person.handle());
        Name primary = person.primaryName();
        String surname = primary == null ? "" : ui.order().group(primary);
        List<Tab> tabs = List.of(
                new Tab(ui.t("tab.overview"), Urls.person(person), active == Tabs.OVERVIEW),
                new Tab(
                        ui.t("tab.ancestors"),
                        Urls.ancestors(person, ANCESTOR_GENERATIONS, false),
                        active == Tabs.ANCESTORS),
                new Tab(
                        ui.t("tab.descendants"),
                        Urls.descendants(person, DESCENDANT_GENERATIONS),
                        active == Tabs.DESCENDANTS));
        if (ui.hasMap() && !living) {
            tabs = new ArrayList<>(tabs);
            tabs.add(new Tab(ui.t("tab.map"), Urls.personMap(person, false), active == Tabs.MAP));
        }
        return new PersonHero(
                living ? ui.t("living") : name(person),
                living ? new Heading(ui.t("living"), "", "") : heading(primary),
                living || images.portrait(person).isEmpty() ? "" : Urls.portrait(person),
                initials(person),
                gender(person),
                living,
                living ? "" : summary(person),
                living ? "" : details(person),
                surname.isEmpty() ? "" : Urls.surname(surname),
                surname.isEmpty() ? "" : ui.t("person.everyone_named", surname),
                tabs);
    }

    /** The father and mother of the first family a person is a child of; either may be {@code null}. */
    private Person[] parents(Person person) {
        Person[] parents = new Person[2];
        if (!person.parentFamilies().isEmpty()) {
            db.families().get(person.parentFamilies().getFirst()).ifPresent(family -> {
                parents[0] = family.father() == null
                        ? null
                        : db.people().get(family.father()).orElse(null);
                parents[1] = family.mother() == null
                        ? null
                        : db.people().get(family.mother()).orElse(null);
            });
        }
        return parents;
    }

    /**
     * The ancestor chart of a person: a tree or a fan of {@code generations} generations including the person,
     * and the same people as a list by generation for small screens.
     */
    public AncestorsPage ancestors(Person person, int generations, boolean fan) {
        Map<Integer, Person> people = new HashMap<>();
        people.put(1, person);
        for (int n = 1; n < 1 << (generations - 1); n++) {
            Person child = people.get(n);
            if (child != null) {
                Person[] parents = parents(child);
                if (parents[0] != null) {
                    people.put(2 * n, parents[0]);
                }
                if (parents[1] != null) {
                    people.put(2 * n + 1, parents[1]);
                }
            }
        }
        Map<Integer, PersonLink> links = new HashMap<>();
        people.forEach((n, p) -> links.put(n, link(p)));
        Map<Integer, String> earlier = new HashMap<>();
        int last = 1 << (generations - 1);
        people.forEach((n, p) -> {
            Person[] parents = parents(p);
            if (n >= last && (parents[0] != null || parents[1] != null)) {
                earlier.put(n, Urls.ancestors(p, generations, fan));
            }
        });
        List<Tab> choices = new ArrayList<>();
        for (int g = 3; g <= 5; g++) {
            choices.add(new Tab(String.valueOf(g), Urls.ancestors(person, g, fan), g == generations));
        }
        List<String> columns = new ArrayList<>();
        for (int g = 0; g < generations; g++) {
            columns.add(ui.t("ancestors.generation." + g));
        }
        List<AncestorGeneration> list = new ArrayList<>();
        for (int k = 1; k < generations; k++) {
            int first = 1 << k;
            int half = first / 2;
            List<PersonLink> fatherSide = new ArrayList<>();
            List<PersonLink> motherSide = new ArrayList<>();
            int fatherUnknown = 0;
            int motherUnknown = 0;
            for (int n = first; n < 2 * first; n++) {
                boolean father = n < first + half;
                if (links.containsKey(n)) {
                    (father ? fatherSide : motherSide).add(links.get(n));
                } else if (links.containsKey(n / 2)) {
                    if (father) {
                        fatherUnknown++;
                    } else {
                        motherUnknown++;
                    }
                }
            }
            if (!fatherSide.isEmpty() || !motherSide.isEmpty() || fatherUnknown + motherUnknown > 0) {
                list.add(new AncestorGeneration(
                        columns.get(k), k > 1, fatherSide, fatherUnknown, motherSide, motherUnknown));
            }
        }
        Tab otherView = new Tab(
                ui.t(fan ? "ancestors.tree" : "ancestors.fan"), Urls.ancestors(person, generations, !fan), false);
        if (fan) {
            Charts.Fan chart = Charts.fan(generations, links);
            return new AncestorsPage(
                    hero(person, Tabs.ANCESTORS),
                    true,
                    choices,
                    otherView,
                    ui.t("ancestors.fan_heading"),
                    columns,
                    chart.width(),
                    chart.height(),
                    List.of(),
                    List.of(),
                    chart.segments(),
                    list);
        }
        Charts.Tree chart = Charts.tree(generations, links, earlier);
        return new AncestorsPage(
                hero(person, Tabs.ANCESTORS),
                false,
                choices,
                otherView,
                ui.t("ancestors.heading", generations),
                columns,
                chart.width(),
                chart.height(),
                chart.boxes(),
                chart.lines(),
                List.of(),
                list);
    }

    /** The descendants of a person over {@code generations} generations including the person. */
    public DescendantsPage descendants(Person person, int generations) {
        List<Tab> choices = new ArrayList<>();
        for (int g = 2; g <= 4; g++) {
            choices.add(new Tab(String.valueOf(g), Urls.descendants(person, g), g == generations));
        }
        Set<String> children = new HashSet<>();
        Set<String> grandchildren = new HashSet<>();
        for (String family : person.families()) {
            db.families()
                    .get(family)
                    .ifPresent(f -> f.children().forEach(ref -> {
                        children.add(ref.child());
                        db.people()
                                .get(ref.child())
                                .ifPresent(child -> child.families()
                                        .forEach(g -> db.families()
                                                .get(g)
                                                .ifPresent(gf ->
                                                        gf.children().forEach(r -> grandchildren.add(r.child())))));
                    }));
        }
        DescendantNode root = descendant(person, "", 1, generations, new HashSet<>());
        return new DescendantsPage(
                hero(person, Tabs.DESCENDANTS),
                choices,
                ui.t("descendants.heading", children.size(), grandchildren.size()),
                root);
    }

    private DescendantNode descendant(Person person, String relation, int depth, int generations, Set<String> seen) {
        List<Family> families = person.families().stream()
                .map(h -> db.families().get(h).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(this::familySortValue))
                .toList();
        // Seen before: a loop in damaged data; show the person, but not again their descendants.
        if (!seen.add(person.handle())) {
            return new DescendantNode(link(person), relation, List.of(), "", "");
        }
        if (depth >= generations) {
            int count = families.stream().mapToInt(f -> f.children().size()).sum();
            return new DescendantNode(
                    link(person),
                    relation,
                    List.of(),
                    count == 0 ? "" : ui.t("descendants.children", count),
                    count == 0 ? "" : Urls.descendants(person, generations));
        }
        List<DescendantFamily> result = new ArrayList<>();
        for (Family family : families) {
            String partnerHandle = partnerOf(person, family);
            Person partner = partnerHandle == null
                    ? null
                    : db.people().get(partnerHandle).orElse(null);
            List<DescendantNode> nodes = new ArrayList<>();
            for (ChildRef ref : family.children()) {
                Person child = db.people().get(ref.child()).orElse(null);
                if (child != null) {
                    nodes.add(descendant(child, child(ref).relation(), depth + 1, generations, seen));
                }
            }
            Event marriage = marriage(family);
            result.add(new DescendantFamily(
                    year(marriage), partner == null ? null : link(partner), Urls.family(family), nodes));
        }
        return new DescendantNode(link(person), relation, result, "", "");
    }

    // ---------------------------------------------------------------- maps

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

    /** A place on the map: the place itself, or the nearest place containing it that has coordinates. */
    private record Located(Place place, Coordinates at) {}

    private final Map<String, Optional<Located>> located = new HashMap<>();

    private Optional<Located> located(Place place) {
        return located.computeIfAbsent(place.handle(), handle -> {
            for (PlaceFormatter.Level level : ui.places().hierarchy(place, null)) {
                Optional<Coordinates> at = Coordinates.parse(
                        level.place().latitude(), level.place().longitude());
                if (at.isPresent()) {
                    return Optional.of(new Located(level.place(), at.get()));
                }
            }
            return Optional.empty();
        });
    }

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

    /** The places of a person's life, numbered in the order of the timeline, and optionally of their family. */
    public PersonMapPage personMap(Person person, boolean family) {
        List<Event> events = new ArrayList<>();
        person.eventRefs().forEach(ref -> db.events().get(ref.event()).ifPresent(events::add));
        for (String familyHandle : person.families()) {
            db.families()
                    .get(familyHandle)
                    .ifPresent(f -> f.eventRefs()
                            .forEach(ref -> db.events().get(ref.event()).ifPresent(events::add)));
        }
        events.sort(Comparator.comparingInt(e -> sortValue(e) == 0 ? Integer.MAX_VALUE : sortValue(e)));
        Map<String, List<String>> byPlace = new LinkedHashMap<>();
        for (Event event : events) {
            if (event.place() != null && db.places().contains(event.place())) {
                byPlace.computeIfAbsent(event.place(), p -> new ArrayList<>())
                        .add(ui.join(ui.type("event", event.type()), year(event)));
            }
        }
        List<PlaceStop> stops = new ArrayList<>();
        // One marker per point on the map, numbered with all the places shown there.
        Map<String, List<String>> numbers = new LinkedHashMap<>();
        Map<String, Located> points = new HashMap<>();
        int number = 0;
        for (var entry : byPlace.entrySet()) {
            Place place = db.places().get(entry.getKey()).orElseThrow();
            String label = String.valueOf(++number);
            Optional<Located> at = located(place);
            stops.add(new PlaceStop(
                    label,
                    ui.places().title(place, null),
                    Urls.place(place),
                    String.join(" · ", entry.getValue()),
                    at.filter(l -> l.place() != place)
                            .map(l -> ui.places().name(l.place(), null))
                            .orElse(""),
                    at.isPresent()));
            at.ifPresent(l -> {
                numbers.computeIfAbsent(l.place().handle(), h -> new ArrayList<>())
                        .add(label);
                points.put(l.place().handle(), l);
            });
        }
        List<MapMarker> markers = new ArrayList<>();
        numbers.forEach((handle, labels) -> {
            Located l = points.get(handle);
            markers.add(new MapMarker(
                    l.at().latitude(),
                    l.at().longitude(),
                    String.join(",", labels),
                    ui.places().title(l.place(), null),
                    Urls.place(l.place()),
                    "",
                    0,
                    false));
        });
        if (family) {
            for (Relative relative : relatives(person)) {
                for (Event event :
                        new Event[] {firstEvent(relative.person(), BIRTH), firstEvent(relative.person(), DEATH)}) {
                    if (event == null || event.place() == null) {
                        continue;
                    }
                    db.places().get(event.place()).flatMap(this::located).ifPresent(l -> {
                        String kind = BIRTH.contains(event.type()) ? "birth" : "death";
                        markers.add(new MapMarker(
                                l.at().latitude(),
                                l.at().longitude(),
                                "",
                                ui.places().title(l.place(), null),
                                Urls.place(l.place()),
                                ui.join(
                                        ui.t("timeline.family." + kind + "." + relative.relation()) + " "
                                                + link(relative.person()).name(),
                                        year(event)),
                                0,
                                true));
                    });
                }
            }
        }
        return new PersonMapPage(
                hero(person, Tabs.MAP), Json.markers(markers), stops, family, Urls.personMap(person, !family));
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

    // ---------------------------------------------------------------- media

    /** The media page: the image with the people marked in it, and everything that uses it. */
    public MediaPage media(Media media) {
        Optional<ImageInfo> info = images.info(media);
        int width = info.map(ImageInfo::displayWidth).orElse(0);
        int height = info.map(ImageInfo::displayHeight).orElse(0);
        List<MarkedPerson> marked = new ArrayList<>();
        List<Cited> linkedFrom = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(media.handle())) {
            boolean isMarked = false;
            if (referrer instanceof Person person && info.isPresent()) {
                for (MediaRef ref : person.media()) {
                    Region r = ref.region();
                    if (ref.media().equals(media.handle()) && r != null) {
                        int x1 = Math.clamp(Math.min(r.x1(), r.x2()), 0, 100);
                        int x2 = Math.clamp(Math.max(r.x1(), r.x2()), 0, 100);
                        int y1 = Math.clamp(Math.min(r.y1(), r.y2()), 0, 100);
                        int y2 = Math.clamp(Math.max(r.y1(), r.y2()), 0, 100);
                        marked.add(new MarkedPerson(
                                0,
                                link(person),
                                x1 * width / 100,
                                y1 * height / 100,
                                Math.max(1, (x2 - x1) * width / 100),
                                Math.max(1, (y2 - y1) * height / 100)));
                        isMarked = true;
                    }
                }
            }
            if (!isMarked) {
                describe(referrer).ifPresent(linkedFrom::add);
            }
        }
        // Numbered from left to right, as people read a group photo.
        marked.sort(Comparator.comparingInt(MarkedPerson::x).thenComparingInt(MarkedPerson::y));
        List<MarkedPerson> numbered = new ArrayList<>();
        for (MarkedPerson m : marked) {
            numbered.add(new MarkedPerson(numbered.size() + 1, m.person(), m.x(), m.y(), m.width(), m.height()));
        }
        List<Row> details = new ArrayList<>();
        addRow(details, "media.date", ui.date(media.date()));
        addRow(details, "media.kind", mediaKind(media));
        info.ifPresent(i -> addRow(details, "media.size", ui.t("media.pixels", i.displayWidth(), i.displayHeight())));
        details.addAll(attributes(media.attributes()));
        var sources = new SourceCollector();
        sources.add(media.citations(), ui.t("sources.media"));
        var group = photoGroup(media);
        return new MediaPage(
                mediaTitle(media),
                group.label(),
                Urls.photos() + "#" + group.id(),
                ui.join(
                        mediaKind(media),
                        ui.date(media.date()),
                        numbered.isEmpty() ? "" : ui.t("media.marked_count", numbered.size())),
                info.isPresent() ? Urls.mediaImage(media) : "",
                images.original(media).isPresent() ? Urls.original(media) : "",
                width,
                height,
                Math.max(8, Math.max(width, height) / 45),
                numbered,
                details,
                linkedFrom,
                notes(media.notes()),
                sources.uses());
    }

    /** All media, by decade of their date, undated last; the same on every request. */
    public PhotosPage photos() {
        return ui.page("photos", this::makePhotos);
    }

    private PhotosPage makePhotos() {
        Map<String, List<Media>> groups = new TreeMap<>();
        for (Media media : db.media().all()) {
            var group = photoGroup(media);
            groups.computeIfAbsent(group.id(), id -> new ArrayList<>()).add(media);
        }
        Comparator<Media> order = Comparator.comparingInt(
                        (Media m) -> m.date() == null ? Integer.MAX_VALUE : DateMath.sortValue(m.date()))
                .thenComparing(this::mediaTitle, ui.order().strings());
        List<PhotoGroup> result = new ArrayList<>();
        groups.forEach((id, media) -> {
            media.sort(order);
            result.add(new PhotoGroup(
                    id,
                    photoGroup(media.getFirst()).label(),
                    media.stream().map(m -> tile(m, "")).toList()));
        });
        return new PhotosPage(result, db.media().size());
    }

    /** The decade of a media object's date, such as "1890–1899", or undated; ids sort undated last. */
    private PhotoGroup photoGroup(Media media) {
        var year = media.date() == null ? OptionalInt.empty() : DateMath.gregorianYear(media.date());
        if (year.isEmpty()) {
            return new PhotoGroup("undated", ui.t("photos.undated"), List.of());
        }
        int decade = Math.floorDiv(year.getAsInt(), 10) * 10;
        // Offset so that ids sort by decade as text, also for years before 1000.
        return new PhotoGroup("d" + (decade + 100_000), decade + "–" + (decade + 9), List.of());
    }

    private MediaTile tile(Media media, String note) {
        return new MediaTile(
                Urls.media(media),
                images.readable(media) ? Urls.thumbnail(media) : "",
                mediaTitle(media),
                ui.join(ui.date(media.date()), note));
    }

    /** Photos of the referenced media objects, each once, with a note such as the event they belong to. */
    private List<MediaTile> tiles(List<MediaRef> refs, String note, Map<String, MediaTile> into) {
        for (MediaRef ref : refs) {
            db.media().get(ref.media()).ifPresent(media -> {
                String marked = ref.region() != null ? ui.t("media.marked") : "";
                into.putIfAbsent(media.handle(), tile(media, ui.join(note, marked)));
            });
        }
        return List.copyOf(into.values());
    }

    private List<MediaTile> tiles(List<MediaRef> refs) {
        return tiles(refs, "", new LinkedHashMap<>());
    }

    /** A person's photos and those of the events they took part in. */
    private List<MediaTile> personMedia(Person person) {
        Map<String, MediaTile> tiles = new LinkedHashMap<>();
        tiles(person.media(), "", tiles);
        for (EventRef ref : person.eventRefs()) {
            db.events().get(ref.event()).ifPresent(e -> tiles(e.media(), ui.type("event", e.type()), tiles));
        }
        return List.copyOf(tiles.values());
    }

    private List<MediaTile> familyMedia(Family family) {
        Map<String, MediaTile> tiles = new LinkedHashMap<>();
        tiles(family.media(), "", tiles);
        for (EventRef ref : family.eventRefs()) {
            db.events().get(ref.event()).ifPresent(e -> tiles(e.media(), ui.type("event", e.type()), tiles));
        }
        return List.copyOf(tiles.values());
    }

    /** The description Gramps shows, or the file name without the folders when there is none. */
    private String mediaTitle(Media media) {
        if (media.description() != null && !media.description().isBlank()) {
            return media.description();
        }
        String path = media.path() == null ? "" : media.path();
        return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
    }

    /** "Photo (JPEG)", "PDF document" or "File", from the type Gramps recorded. */
    private String mediaKind(Media media) {
        String mime = media.mime() == null ? "" : media.mime().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) {
            return ui.t("media.kind.image", mime.substring("image/".length()).toUpperCase(Locale.ROOT));
        }
        return ui.t(mime.equals("application/pdf") ? "media.kind.pdf" : "media.kind.file");
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

    /** All sources, alphabetically under letter headings; the same on every request. */
    public SourcesPage sources() {
        return ui.page("sources", this::makeSources);
    }

    private SourcesPage makeSources() {
        List<SourceEntry> entries = new ArrayList<>();
        for (Source source : db.sources().all()) {
            String title = source.title() == null || source.title().isBlank() ? ui.t("unknown") : source.title();
            int citations = (int) db.referrers(source.handle()).stream()
                    .filter(Citation.class::isInstance)
                    .count();
            entries.add(new SourceEntry(
                    Urls.source(source), title, source.author() == null ? "" : source.author(), citations));
        }
        List<SourceLetter> letters = new ArrayList<>();
        for (NameOrder.Bucket<SourceEntry> bucket : ui.order().alphabeticIndex(entries, SourceEntry::title)) {
            letters.add(new SourceLetter(bucket.label(), bucket.items()));
        }
        return new SourcesPage(letters, entries.size());
    }

    public SearchPage search(String query, SearchIndex index) {
        SearchIndex.Result result = index.find(query, SEARCH_LIMIT);
        return new SearchPage(
                query,
                result.people().stream()
                        .map(h -> db.people().get(h.handle()).map(this::link).orElse(null))
                        .filter(Objects::nonNull)
                        .toList(),
                result.places().stream()
                        .map(h -> db.places().get(h.handle()).orElse(null))
                        .filter(Objects::nonNull)
                        .map(p -> new PlaceLink(
                                Urls.place(p), ui.places().title(p, null), ui.type("placetype", p.type())))
                        .toList(),
                result.more());
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

    private PlaceLink placeLink(Place place) {
        return new PlaceLink(Urls.place(place), ui.places().name(place, null), ui.type("placetype", place.type()));
    }

    /** The people an event is about: the primary participants, or both spouses of a family event. */
    private List<PersonLink> participants(Event event) {
        List<PersonLink> who = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(event.handle())) {
            switch (referrer) {
                case Person p -> {
                    boolean primary = p.eventRefs().stream()
                            .anyMatch(r -> event.handle().equals(r.event()) && "Primary".equals(r.role()));
                    if (primary) {
                        who.add(link(p));
                    }
                }
                case Family f -> {
                    Optional.ofNullable(link(f.father())).ifPresent(who::add);
                    Optional.ofNullable(link(f.mother())).ifPresent(who::add);
                }
                default -> {}
            }
        }
        return who;
    }

    /** What a citation supports: people, their events, families or places. */
    private List<Cited> cited(Citation citation) {
        List<Cited> cited = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(citation.handle())) {
            if (!(referrer instanceof Source) && !(referrer instanceof Citation)) {
                describe(referrer).ifPresent(cited::add);
            }
        }
        return cited;
    }

    /** An object in words with a link, such as a person's name or "Birth of Lewis Anderson Garner". */
    private Optional<Cited> describe(PrimaryObject object) {
        return switch (object) {
            case Person p -> Optional.of(new Cited(link(p).name(), Urls.person(p)));
            case Family f -> Optional.of(new Cited(familyTitle(f), Urls.family(f)));
            case Event e -> {
                List<PersonLink> who = participants(e);
                String names =
                        String.join(" & ", who.stream().map(PersonLink::name).toList());
                yield Optional.of(new Cited(
                        names.isEmpty()
                                ? ui.type("event", e.type())
                                : ui.t("cited.event", ui.type("event", e.type()), names),
                        who.isEmpty() ? "" : who.getFirst().url()));
            }
            case Place p -> Optional.of(new Cited(ui.places().title(p, null), Urls.place(p)));
            case Source s -> Optional.of(new Cited(sourceTitle(s), Urls.source(s)));
            case Citation c ->
                db.sources()
                        .get(c.source())
                        .map(s -> new Cited(ui.join(sourceTitle(s), c.page() == null ? "" : c.page()), Urls.source(s)));
            default -> Optional.empty();
        };
    }

    private String sourceTitle(Source source) {
        return source.title() == null || source.title().isBlank() ? ui.t("unknown") : source.title();
    }

    private String familyTitle(Family family) {
        PersonLink father = link(family.father());
        PersonLink mother = link(family.mother());
        return ui.t(
                "family.title",
                father != null ? father.name() : ui.t("unknown"),
                mother != null ? mother.name() : ui.t("unknown"));
    }

    private void addRow(List<Row> rows, String key, String value) {
        if (value != null && !value.isBlank()) {
            rows.add(new Row(ui.t(key), value));
        }
    }

    /** A language code as a name in the page language, e.g. "el" as "Greek". */
    private String language(String code) {
        return Languages.name(code, ui.locale());
    }

    // ---------------------------------------------------------------- person parts

    /** A link to a person, or {@code null} if the handle is {@code null} or unknown. */
    public PersonLink link(String handle) {
        return handle == null ? null : db.people().get(handle).map(this::link).orElse(null);
    }

    public PersonLink link(Person person) {
        if (data.isLiving(person.handle())) {
            return new PersonLink(Urls.person(person), ui.t("living"), "", true, "", "unknown", "");
        }
        String gender = gender(person);
        return new PersonLink(
                Urls.person(person),
                name(person),
                lifespan(person),
                false,
                initials(person),
                gender,
                ui.t("gender." + gender));
    }

    /**
     * The name in reading order with the main surname, e.g. "Lewis Anderson Garner Sr", for links, boxes and
     * titles. Lists are still sorted by surname.
     */
    private String name(Person person) {
        String name = ui.names().formatWith(person.primaryName(), "%f %m %s");
        return name.isEmpty() ? ui.t("unknown") : name;
    }

    private static String gender(Person person) {
        return person.gender().name().toLowerCase();
    }

    /** The name in reading order with the surname separate, e.g. "Lewis Anderson", "Garner", "Sr". */
    private Heading heading(Name name) {
        if (name == null) {
            return new Heading(ui.t("unknown"), "", "");
        }
        String surname = ui.names().formatWith(name, "%l");
        String given = name.first() == null ? "" : name.first();
        String suffix = name.suffix() == null ? "" : name.suffix();
        if (surname.isEmpty()) {
            return new Heading(ui.names().formatWith(name, "%f %s"), "", "");
        }
        return new Heading(given, surname, suffix);
    }

    private String initials(Person person) {
        if (data.isLiving(person.handle()) || person.primaryName() == null) {
            return "";
        }
        Name name = person.primaryName();
        Surname surname = name.primarySurname();
        String given = name.first() == null ? "" : name.first().strip();
        String family = surname == null || surname.value() == null
                ? ""
                : surname.value().strip();
        return (firstLetter(given) + firstLetter(family)).toUpperCase(ui.locale());
    }

    private static String firstLetter(String text) {
        return text.isEmpty() ? "" : text.substring(0, text.offsetByCodePoints(0, 1));
    }

    /** "Born 21 June 1855 in Great Falls · died 28 June 1911 in Twin Falls, aged 56". */
    private String summary(Person person) {
        Gender gender = person.gender();
        Event birth = firstEvent(person, BIRTH);
        Event death = firstEvent(person, DEATH);
        List<String> parts = new ArrayList<>(2);
        if (birth != null && birth.date() != null) {
            String place = place(birth);
            parts.add(
                    place.isEmpty()
                            ? ui.t("summary.born", gender, ui.date(birth.date()))
                            : ui.t("summary.born_in", gender, ui.date(birth.date()), place));
        }
        if (death != null && death.date() != null) {
            String place = place(death);
            String prefix = parts.isEmpty() ? "summary.first." : "summary.";
            String text = place.isEmpty()
                    ? ui.t(prefix + "died", gender, ui.date(death.date()))
                    : ui.t(prefix + "died_in", gender, ui.date(death.date()), place);
            Optional<Integer> age = age(birth, death);
            parts.add(age.map(a -> ui.t("summary.aged", text, a)).orElse(text));
        }
        return String.join(" · ", parts);
    }

    private static Optional<Integer> age(Event birth, Event death) {
        if (birth == null || death == null) {
            return Optional.empty();
        }
        var born = DateMath.exactGregorianDate(birth.date());
        var died = DateMath.exactGregorianDate(death.date());
        if (born.isEmpty() || died.isEmpty() || died.get().isBefore(born.get())) {
            return Optional.empty();
        }
        return Optional.of(Period.between(born.get(), died.get()).getYears());
    }

    /** Title, nickname and other names, e.g. "Dr. · “Big Louie” · also Louis Garner". */
    private String details(Person person) {
        Name name = person.primaryName();
        List<String> parts = new ArrayList<>();
        if (name != null && name.title() != null && !name.title().isBlank()) {
            parts.add(name.title());
        }
        if (name != null && name.nick() != null && !name.nick().isBlank()) {
            parts.add(ui.t("person.nickname", name.nick()));
        }
        List<String> others = person.names().stream()
                .filter(n -> n != name)
                .map(n -> ui.names().format(n, NameFormatter.GIVEN_SURNAME))
                .filter(s -> !s.isBlank())
                .toList();
        if (!others.isEmpty()) {
            parts.add(ui.t("person.also", String.join(", ", others)));
        }
        return String.join(" · ", parts);
    }

    private String lifespan(Person person) {
        String born = year(firstEvent(person, BIRTH));
        String died = year(firstEvent(person, DEATH));
        if (!born.isEmpty() && !died.isEmpty()) {
            return ui.t("lifespan.both", born, died);
        }
        if (!born.isEmpty()) {
            return ui.t("lifespan.born", born);
        }
        return died.isEmpty() ? "" : ui.t("lifespan.died", died);
    }

    private static String year(Event event) {
        if (event == null || event.date() == null) {
            return "";
        }
        var year = DateMath.gregorianYear(event.date());
        if (year.isEmpty()) {
            return "";
        }
        boolean exact = event.date().modifier() == GrampsDate.Modifier.NONE
                && event.date().quality() == GrampsDate.Quality.REGULAR;
        return (exact ? "" : "~") + year.getAsInt();
    }

    /** The first dated primary event of the given types, in order of preference; else the first undated. */
    private Event firstEvent(Person person, List<String> types) {
        Event undated = null;
        for (String type : types) {
            for (EventRef ref : person.eventRefs()) {
                if (!"Primary".equals(ref.role())) {
                    continue;
                }
                Event event = db.events().get(ref.event()).orElse(null);
                if (event != null && type.equals(event.type())) {
                    if (event.date() != null && DateMath.sortValue(event.date()) != 0) {
                        return event;
                    }
                    if (undated == null) {
                        undated = event;
                    }
                }
            }
        }
        return undated;
    }

    private String place(Event event) {
        return db.places()
                .get(event.place())
                .map(p -> ui.places().title(p, event.date()))
                .orElse("");
    }

    // ---------------------------------------------------------------- timeline

    /** The person's own events and those of their families, in date order; undated ones last. */
    private List<EventRow> timeline(Person person, SourceCollector sources, List<Relative> relatives) {
        List<Dated> rows = new ArrayList<>(familyEvents(person, relatives));
        for (EventRef ref : person.eventRefs()) {
            db.events()
                    .get(ref.event())
                    .ifPresent(e -> rows.add(new Dated(sortValue(e), event(e, ref, null, sources))));
        }
        for (String familyHandle : person.families()) {
            Family family = db.families().get(familyHandle).orElse(null);
            if (family == null) {
                continue;
            }
            PersonLink partner = link(partnerOf(person, family));
            for (EventRef ref : family.eventRefs()) {
                db.events()
                        .get(ref.event())
                        .ifPresent(e -> rows.add(new Dated(sortValue(e), event(e, ref, partner, sources))));
            }
        }
        return inDateOrder(rows);
    }

    private record Dated(int sortValue, EventRow row) {}

    /** Rows by date; undated ones keep their order at the end. */
    private static List<EventRow> inDateOrder(List<Dated> rows) {
        return rows.stream()
                .sorted(Comparator.comparingInt((Dated d) -> d.sortValue() == 0 ? Integer.MAX_VALUE : d.sortValue()))
                .map(Dated::row)
                .toList();
    }

    private static int sortValue(Event event) {
        return DateMath.sortValue(event.date());
    }

    /** A sort value that puts undated ones, whose value is 0, last. */
    private static int undatedLast(int sortValue) {
        return sortValue == 0 ? Integer.MAX_VALUE : sortValue;
    }

    private EventRow event(Event event, EventRef ref, PersonLink partner, SourceCollector sources) {
        String type = ui.type("event", event.type());
        sources.add(event.citations(), type);
        return new EventRow(
                timelineYear(event),
                type,
                ui.date(event.date()),
                place(event),
                db.places().get(event.place()).map(Urls::place).orElse(""),
                description(event),
                "Primary".equals(ref.role()) || "Family".equals(ref.role()) ? "" : ui.type("role", ref.role()),
                partner,
                citations(event.citations()),
                null);
    }

    /** The Gregorian year for a timeline's year column, without any "about" marker; empty if unknown. */
    private static String timelineYear(Event event) {
        var year = event.date() == null ? null : DateMath.gregorianYear(event.date());
        return year == null || year.isEmpty() ? "" : String.valueOf(year.getAsInt());
    }

    /**
     * The event's description, unless it is the one Gramps generates automatically ("Birth of Garner, Lewis
     * Anderson"), which only repeats what the page already shows.
     */
    private static String description(Event event) {
        String description = event.description();
        if (description == null || description.isBlank()) {
            return "";
        }
        if (event.type() != null && description.startsWith(event.type() + " of ")) {
            return "";
        }
        return description;
    }

    // ---------------------------------------------------------------- family

    private Diagram diagram(Person person) {
        PersonLink father = null;
        PersonLink mother = null;
        if (!person.parentFamilies().isEmpty()) {
            Family parents =
                    db.families().get(person.parentFamilies().getFirst()).orElse(null);
            if (parents != null) {
                father = link(parents.father());
                mother = link(parents.mother());
            }
        }
        List<Family> families = person.families().stream()
                .map(h -> db.families().get(h).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(this::familySortValue))
                .toList();
        List<FamilyBlock> blocks = new ArrayList<>();
        for (Family family : families) {
            String partnerHandle = partnerOf(person, family);
            Person partner = partnerHandle == null
                    ? null
                    : db.people().get(partnerHandle).orElse(null);
            List<ChildView> children =
                    family.children().stream().map(this::child).toList();
            blocks.add(new FamilyBlock(
                    Urls.family(family),
                    familyLabel(family),
                    partner == null ? null : link(partner),
                    partnerRole(family, partner),
                    children.subList(0, Math.min(children.size(), DIAGRAM_CHILDREN)),
                    children.size(),
                    Math.max(0, children.size() - DIAGRAM_CHILDREN)));
        }
        return new Diagram(father, mother, link(person), blocks);
    }

    /** Families in the order they were formed: by marriage date, undated ones last. */
    private int familySortValue(Family family) {
        Event marriage = marriage(family);
        int value = marriage == null ? 0 : sortValue(marriage);
        return value == 0 ? Integer.MAX_VALUE : value;
    }

    private Event marriage(Family family) {
        for (EventRef ref : family.eventRefs()) {
            Event event = db.events().get(ref.event()).orElse(null);
            if (event != null && "Marriage".equals(event.type())) {
                return event;
            }
        }
        return null;
    }

    private String familyLabel(Family family) {
        Event marriage = marriage(family);
        String year = year(marriage);
        if (!year.isEmpty()) {
            return ui.t("family.married_in", year);
        }
        String relationship = ui.type("family", family.relationship());
        return relationship.isEmpty() ? ui.t("family") : relationship;
    }

    private String partnerRole(Family family, Person partner) {
        if (partner == null || data.isLiving(partner.handle())) {
            return ui.t("partner");
        }
        if ("Married".equals(family.relationship())) {
            return switch (partner.gender()) {
                case MALE -> ui.t("partner.husband");
                case FEMALE -> ui.t("partner.wife");
                default -> ui.t("partner");
            };
        }
        return ui.t("partner");
    }

    private static String partnerOf(Person person, Family family) {
        return person.handle().equals(family.father()) ? family.mother() : family.father();
    }

    /** Brothers and sisters from all families the person is a child of, each listed once. */
    private List<PersonLink> siblings(Person person) {
        Set<String> seen = new LinkedHashSet<>();
        for (String handle : person.parentFamilies()) {
            db.families().get(handle).ifPresent(f -> f.children().forEach(c -> seen.add(c.child())));
        }
        seen.remove(person.handle());
        return seen.stream().map(this::link).filter(Objects::nonNull).toList();
    }

    private ChildView child(ChildRef ref) {
        PersonLink person = link(ref.child());
        String relation = Objects.equals(ref.fatherRelation(), "Birth") && Objects.equals(ref.motherRelation(), "Birth")
                ? ""
                : ui.type("child", !"Birth".equals(ref.fatherRelation()) ? ref.fatherRelation() : ref.motherRelation());
        return new ChildView(person, relation);
    }

    // ---------------------------------------------------------------- details

    private List<Row> attributes(List<Attribute> attributes) {
        return attributes.stream()
                .map(a -> new Row(ui.type("attribute", a.type()), a.value() == null ? "" : a.value()))
                .toList();
    }

    private List<NoteView> notes(List<String> handles) {
        List<NoteView> notes = new ArrayList<>();
        for (String handle : handles) {
            db.notes().get(handle).map(this::note).ifPresent(notes::add);
        }
        return notes;
    }

    private NoteView note(Note note) {
        return new NoteView(ui.type("note", note.type()), note.text().text(), note.preformatted());
    }

    private List<CitationView> citations(List<String> handles) {
        List<CitationView> result = new ArrayList<>();
        for (String handle : handles) {
            db.citations()
                    .get(handle)
                    .ifPresent(c -> result.add(new CitationView(
                            sourceTitle(c),
                            sourceUrl(c),
                            c.page() == null ? "" : c.page(),
                            ui.date(c.date()),
                            ui.t("confidence." + Math.clamp(c.confidence(), 0, 4)))));
        }
        return result;
    }

    private String sourceUrl(Citation citation) {
        return db.sources().get(citation.source()).map(Urls::source).orElse("");
    }

    private String sourceTitle(Citation citation) {
        return db.sources()
                .get(citation.source())
                .map(s -> s.title() == null || s.title().isBlank() ? ui.t("unknown") : s.title())
                .orElse(ui.t("unknown"));
    }

    /** Collects the citations shown on a page, each once, with what they support. */
    private final class SourceCollector {
        private final Map<String, List<String>> supports = new LinkedHashMap<>();

        void add(List<String> citations, String what) {
            for (String handle : citations) {
                List<String> list = supports.computeIfAbsent(handle, h -> new ArrayList<>());
                if (!list.contains(what)) {
                    list.add(what);
                }
            }
        }

        List<SourceUse> uses() {
            List<SourceUse> uses = new ArrayList<>();
            supports.forEach((handle, what) -> db.citations()
                    .get(handle)
                    .ifPresent(c -> uses.add(new SourceUse(
                            sourceTitle(c), sourceUrl(c), c.page() == null ? "" : c.page(), String.join(", ", what)))));
            return uses;
        }
    }
}
