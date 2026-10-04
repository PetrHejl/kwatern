package me.hejl.kwatern.view;

import java.time.Period;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
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
import me.hejl.kwatern.view.Pages.ParentsBlock;
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

    /**
     * A member of a person's close family, and what they are to the person: "father", "sister", "wife".
     *
     * @param qualifier the relation between parent and child if it is not by birth, e.g. "Foster", else empty
     * @param heading   the group's heading in the lifespans if not the usual one, e.g. "Foster parents"
     */
    record Relative(Person person, String relation, String group, String qualifier, String heading) {}

    /** Parents, spouses, siblings and children, each once, leaving out people who may be alive. */
    List<Relative> relatives(Person person) {
        List<Relative> relatives = new ArrayList<>();
        Set<String> seen = new HashSet<>(Set.of(person.handle()));
        List<Family> parentFamilies = parentFamilies(person);
        for (Family family : parentFamilies) {
            String heading =
                    parentFamilies.size() > 1 ? parentsLabel(person, family, family == parentFamilies.getFirst()) : "";
            for (boolean father : new boolean[] {true, false}) {
                db.people()
                        .get(Objects.requireNonNullElse(father ? family.father() : family.mother(), ""))
                        .ifPresent(p -> addRelative(
                                relatives,
                                seen,
                                p,
                                father ? "father" : "mother",
                                "parents",
                                parentText(relationOf(person, family, father), father),
                                heading));
            }
        }
        for (String familyHandle : person.families()) {
            db.families().get(familyHandle).ifPresent(family -> {
                String partner = partnerOf(person, family);
                db.people()
                        .get(partner == null ? "" : partner)
                        .ifPresent(p -> addRelative(
                                relatives, seen, p, byGender(p, "husband", "wife", "partner"), "couple", "", ""));
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
                                            "siblings",
                                            "",
                                            ""))));
        }
        for (String familyHandle : person.families()) {
            db.families()
                    .get(familyHandle)
                    .ifPresent(family -> family.children()
                            .forEach(ref -> db.people()
                                    .get(ref.child())
                                    .ifPresent(c -> addRelative(
                                            relatives,
                                            seen,
                                            c,
                                            byGender(c, "son", "daughter", "child"),
                                            "children",
                                            relationText(relationTo(ref, family, person.handle())),
                                            ""))));
        }
        return relatives;
    }

    private void addRelative(
            List<Relative> relatives,
            Set<String> seen,
            Person person,
            String relation,
            String group,
            String qualifier,
            String heading) {
        if (!data.isLiving(person.handle()) && seen.add(person.handle())) {
            relatives.add(new Relative(person, relation, group, qualifier, heading));
        }
    }

    /**
     * The families a person is a child of that have a known parent, the main one first as in Gramps. A family
     * whose parents are all unknown or not published is left out, so the next one counts as the main one.
     */
    private List<Family> parentFamilies(Person person) {
        return person.parentFamilies().stream()
                .map(h -> db.families().get(h).orElse(null))
                .filter(f -> f != null && (known(f.father()) || known(f.mother())))
                .toList();
    }

    /** A person's relation to the father or the mother of a family they are a child of, as Gramps writes it. */
    private static String relationOf(Person person, Family family, boolean father) {
        return family.children().stream()
                .filter(ref -> ref.child().equals(person.handle()))
                .findFirst()
                .map(ref -> father ? ref.fatherRelation() : ref.motherRelation())
                .orElse("Birth");
    }

    /**
     * The relation to both known parents of a family, or {@code null} if it differs between them, as for a
     * stepfather married to the mother.
     */
    private static String commonRelation(Person person, Family family) {
        String father = relationOf(person, family, true);
        String mother = relationOf(person, family, false);
        if (family.father() == null) {
            return mother;
        }
        return family.mother() == null || father.equals(mother) ? father : null;
    }

    private boolean known(String person) {
        return person != null && db.people().get(person).isPresent();
    }

    /**
     * The heading of a family's parents: "Birth parents", "Foster parents" where the page texts name the
     * relation, else "Parents" for the main family and "Other parents" for the others.
     */
    private String parentsLabel(Person person, Family family, boolean main) {
        String label = ui.findType("parents", commonRelation(person, family));
        return label != null ? label : ui.t(main ? "parents" : "parents.other");
    }

    /**
     * The parents of a family the person is a child of. With several families, each pair is headed by its
     * relation, or by "Parents" or "Other parents" if the texts name none. A parent not by birth whose relation
     * is not in a heading is called by it ("Foster father") instead of "Father" where the texts have such a
     * word; otherwise, as for an unknown or custom relation, they stay "Father" with the relation after it.
     */
    private ParentsBlock parentsBlock(Person person, Family family, boolean main, boolean headed) {
        boolean named = headed && ui.findType("parents", commonRelation(person, family)) != null;
        String fatherRelation = named ? "Birth" : relationOf(person, family, true);
        String motherRelation = named ? "Birth" : relationOf(person, family, false);
        String fatherRole = parentRole(fatherRelation, true);
        String motherRole = parentRole(motherRelation, false);
        return new ParentsBlock(
                headed ? parentsLabel(person, family, main) : "",
                headed ? Urls.family(family) : "",
                link(family.father()),
                fatherRole != null ? fatherRole : ui.t("father"),
                fatherRole != null ? "" : parentText(fatherRelation, true),
                link(family.mother()),
                motherRole != null ? motherRole : ui.t("mother"),
                motherRole != null ? "" : parentText(motherRelation, false));
    }

    /** What the texts call a parent not by birth, as "Foster father", or {@code null} if they have no word. */
    private String parentRole(String relation, boolean father) {
        return relation.equals("Birth") ? null : ui.findType("parent", relation, father ? "father" : "mother");
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
        int to = death == null ? Integer.MAX_VALUE : undatedLast(sortValue(death));
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
                        relative.qualifier(),
                        "",
                        null,
                        List.of(),
                        link(relative.person())));
    }

    /** The lifespans of the person and their close family, or {@code null} if fewer than three are known. */
    private LifespanChart lifespans(Person person, List<Relative> relatives) {
        int currentYear = Year.now().getValue();
        List<Charts.Life> lives = new ArrayList<>();
        for (String group : List.of("parents", "couple", "siblings", "children")) {
            if (group.equals("couple")) {
                life(person, ui.t("lifespans.group.couple"), true, currentYear).ifPresent(lives::add);
            }
            for (Relative relative : relatives) {
                if (relative.group().equals(group)) {
                    String heading =
                            relative.heading().isEmpty() ? ui.t("lifespans.group." + group) : relative.heading();
                    life(relative.person(), heading, false, currentYear).ifPresent(lives::add);
                }
            }
        }
        if (lives.size() < 3 || lives.stream().noneMatch(Charts.Life::self)) {
            return null;
        }
        List<String> note = new ArrayList<>(List.of(ui.t("lifespans.note.band")));
        for (Charts.End end : List.of(Charts.End.UNKNOWN, Charts.End.ALIVE)) {
            if (lives.stream().anyMatch(l -> l.end() == end)) {
                note.add(ui.t("lifespans.note." + end.name().toLowerCase(Locale.ROOT)));
            }
        }
        if (!data.living().isEmpty()) {
            note.add(ui.t("lifespans.note.hidden"));
        }
        return Charts.lifespans(lives, currentYear, String.join(" ", note));
    }

    private Optional<Charts.Life> life(Person person, String group, boolean self, int currentYear) {
        Event birth = firstEvent(person, BIRTH);
        if (data.isLiving(person.handle()) || birth == null || birth.date() == null) {
            return Optional.empty();
        }
        OptionalInt from = DateMath.gregorianYear(birth.date());
        if (from.isEmpty()) {
            return Optional.empty();
        }
        Event death = firstEvent(person, DEATH);
        OptionalInt died =
                death == null || death.date() == null ? OptionalInt.empty() : DateMath.gregorianYear(death.date());
        Charts.End end;
        int to;
        if (died.isPresent()) {
            end = Charts.End.DEATH;
            to = died.getAsInt();
        } else if (death == null && data.mayBeAlive(person.handle())) {
            end = Charts.End.ALIVE;
            to = Math.max(from.getAsInt(), currentYear);
        } else {
            end = Charts.End.UNKNOWN;
            to = Math.max(from.getAsInt(), lastKnownYear(person));
        }
        PersonLink link = link(person);
        return Optional.of(new Charts.Life(group, link, from.getAsInt(), to, end, link.lifespan(), self));
    }

    /** The latest year of the person's own and their families' dated events, or 0 if none is dated. */
    private int lastKnownYear(Person person) {
        List<EventRef> refs = new ArrayList<>(person.eventRefs());
        for (String familyHandle : person.families()) {
            db.families().get(familyHandle).ifPresent(family -> refs.addAll(family.eventRefs()));
        }
        int last = 0;
        for (EventRef ref : refs) {
            Event event = db.events().get(ref.event()).orElse(null);
            if (event != null && event.date() != null) {
                OptionalInt year = DateMath.gregorianYear(event.date());
                if (year.isPresent()) {
                    last = Math.max(last, year.getAsInt());
                }
            }
        }
        return last;
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
                .sorted(Comparator.comparingInt((Dated d) -> undatedLast(d.sortValue())))
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
        List<Family> parentFamilies = parentFamilies(person);
        List<ParentsBlock> parents = parentFamilies.stream()
                .map(family ->
                        parentsBlock(person, family, family == parentFamilies.getFirst(), parentFamilies.size() > 1))
                .toList();
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
            List<ChildView> children = family.children().stream()
                    .map(ref -> child(ref, family, person))
                    .toList();
            blocks.add(new FamilyBlock(
                    Urls.family(family),
                    familyLabel(family),
                    partner == null ? null : link(partner),
                    partnerRole(family, partner),
                    children.subList(0, Math.min(children.size(), DIAGRAM_CHILDREN)),
                    children.size(),
                    Math.max(0, children.size() - DIAGRAM_CHILDREN)));
        }
        return new Diagram(parents, link(person), blocks);
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
