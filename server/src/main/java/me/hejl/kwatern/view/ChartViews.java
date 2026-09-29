package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import me.hejl.gramps.model.ChildRef;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.AncestorGeneration;
import me.hejl.kwatern.view.Pages.AncestorsPage;
import me.hejl.kwatern.view.Pages.DescendantFamily;
import me.hejl.kwatern.view.Pages.DescendantNode;
import me.hejl.kwatern.view.Pages.DescendantsPage;
import me.hejl.kwatern.view.Pages.MapMarker;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.PersonMapPage;
import me.hejl.kwatern.view.Pages.PlaceStop;
import me.hejl.kwatern.view.Pages.Tab;
import me.hejl.kwatern.view.Views.Tabs;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** The other tabs of a person: the ancestor and descendant charts and the map of their life. */
final class ChartViews extends ViewPart {

    // The heading of every tab, and the person's family.
    private final PersonViews personViews;

    ChartViews(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images, PersonViews personViews) {
        super(data, index, ui, images);
        this.personViews = personViews;
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
                    personViews.hero(person, Tabs.ANCESTORS),
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
                personViews.hero(person, Tabs.ANCESTORS),
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
                personViews.hero(person, Tabs.DESCENDANTS),
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
        events.sort(Comparator.comparingInt(e -> undatedLast(sortValue(e))));
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
            for (PersonViews.Relative relative : personViews.relatives(person)) {
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
                personViews.hero(person, Tabs.MAP),
                Json.markers(markers),
                stops,
                family,
                Urls.personMap(person, !family));
    }
}
