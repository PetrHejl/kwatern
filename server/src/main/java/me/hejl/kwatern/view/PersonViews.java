package me.hejl.kwatern.view;

import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Gender;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.name.NameFormatter;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.ChildView;
import me.hejl.kwatern.view.Pages.Diagram;
import me.hejl.kwatern.view.Pages.EventRow;
import me.hejl.kwatern.view.Pages.FamilyBlock;
import me.hejl.kwatern.view.Pages.FamilyPage;
import me.hejl.kwatern.view.Pages.Heading;
import me.hejl.kwatern.view.Pages.LifespanChart;
import me.hejl.kwatern.view.Pages.MediaTile;
import me.hejl.kwatern.view.Pages.PersonHero;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.PersonPage;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.view.Pages.Tab;
import me.hejl.kwatern.view.Views.Tabs;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/** The overview of a person, with their timeline, family and lifespans, and the page of a family. */
final class PersonViews extends ViewPart {

    /** Children shown in the family diagram before "and N more". */
    private static final int DIAGRAM_CHILDREN = 8;

    PersonViews(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        super(data, index, ui, images);
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

    /** A member of a person's close family, and what they are to the person: "father", "sister", "wife". */
    record Relative(Person person, String relation, String group) {}

    /** Parents, spouses, siblings and children, each once, leaving out people who may be alive. */
    List<Relative> relatives(Person person) {
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

    PersonHero hero(Person person, Tabs active) {
        boolean living = data.isLiving(person.handle());
        Name primary = person.primaryName();
        String surname = primary == null ? "" : ui.order().group(primary);
        List<Tab> tabs = List.of(
                new Tab(ui.t("tab.overview"), Urls.person(person), active == Tabs.OVERVIEW),
                new Tab(
                        ui.t("tab.ancestors"),
                        Urls.ancestors(person, Views.ANCESTOR_GENERATIONS, false),
                        active == Tabs.ANCESTORS),
                new Tab(
                        ui.t("tab.descendants"),
                        Urls.descendants(person, Views.DESCENDANT_GENERATIONS),
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

    /** Brothers and sisters from all families the person is a child of, each listed once. */
    private List<PersonLink> siblings(Person person) {
        Set<String> seen = new LinkedHashSet<>();
        for (String handle : person.parentFamilies()) {
            db.families().get(handle).ifPresent(f -> f.children().forEach(c -> seen.add(c.child())));
        }
        seen.remove(person.handle());
        return seen.stream().map(this::link).filter(Objects::nonNull).toList();
    }
}
