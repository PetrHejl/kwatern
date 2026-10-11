package me.hejl.kwatern.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.model.Attribute;
import me.hejl.gramps.model.ChildRef;
import me.hejl.gramps.model.Citation;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Note;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.model.StyledText;
import me.hejl.gramps.model.Surname;
import me.hejl.gramps.model.Url;
import me.hejl.gramps.place.Coordinates;
import me.hejl.gramps.place.PlaceFormatter;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.view.Pages.ChildView;
import me.hejl.kwatern.view.Pages.CitationView;
import me.hejl.kwatern.view.Pages.Cited;
import me.hejl.kwatern.view.Pages.MediaTile;
import me.hejl.kwatern.view.Pages.NoteView;
import me.hejl.kwatern.view.Pages.PersonLink;
import me.hejl.kwatern.view.Pages.Piece;
import me.hejl.kwatern.view.Pages.Row;
import me.hejl.kwatern.view.Pages.Run;
import me.hejl.kwatern.view.Pages.SourceUse;
import me.hejl.kwatern.view.Pages.Text;
import me.hejl.kwatern.view.Pages.WebLink;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Ui;
import me.hejl.kwatern.web.Urls;

/**
 * What the parts of {@link Views} share: the published data, its index, the page language and the images, and the
 * helpers every page uses, such as links to people, dates, sources and photos. Made for one request.
 */
abstract sealed class ViewPart permits ListingViews, PersonViews, ChartViews, PlaceViews, MediaViews {

    // Gramps: gen/lib/eventtype.py is_birth_fallback and is_death_fallback
    static final List<String> BIRTH = List.of("Birth", "Baptism", "Christening");
    static final List<String> DEATH = List.of("Death", "Burial", "Cremation");

    final PublicDatabase data;
    final GrampsDatabase db;
    final TreeIndex index;
    final Ui ui;
    final MediaImages images;

    ViewPart(PublicDatabase data, TreeIndex index, Ui ui, MediaImages images) {
        this.data = data;
        this.db = data.database();
        this.index = index;
        this.ui = ui;
        this.images = images;
    }

    /** The father and mother of the first family a person is a child of; either may be {@code null}. */
    Person[] parents(Person person) {
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

    /** A place on the map: the place itself, or the nearest place containing it that has coordinates. */
    record Located(Place place, Coordinates at) {}

    private final Map<String, Optional<Located>> located = new HashMap<>();

    Optional<Located> located(Place place) {
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

    MediaTile tile(Media media, String note) {
        return new MediaTile(
                Urls.media(media),
                images.readable(media) ? Urls.thumbnail(media) : "",
                mediaTitle(media),
                ui.join(ui.date(media.date()), note));
    }

    /** Photos of the referenced media objects, each once, with a note such as the event they belong to. */
    List<MediaTile> tiles(List<MediaRef> refs, String note, Map<String, MediaTile> into) {
        for (MediaRef ref : refs) {
            db.media().get(ref.media()).ifPresent(media -> {
                String marked = ref.region() != null ? ui.t("media.marked") : "";
                into.putIfAbsent(media.handle(), tile(media, ui.join(note, marked)));
            });
        }
        return List.copyOf(into.values());
    }

    List<MediaTile> tiles(List<MediaRef> refs) {
        return tiles(refs, "", new LinkedHashMap<>());
    }

    /** The description Gramps shows, or the file name without the folders when there is none. */
    String mediaTitle(Media media) {
        if (media.description() != null && !media.description().isBlank()) {
            return media.description();
        }
        String path = media.path() == null ? "" : media.path();
        return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
    }

    /** The people an event is about: the primary participants, or both spouses of a family event. */
    List<PersonLink> participants(Event event) {
        List<PersonLink> who = new ArrayList<>();
        for (PrimaryObject referrer : db.referrers(event.handle())) {
            switch (referrer) {
                case Person p -> {
                    if (primaryIn(p, event)) {
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

    /** Whether the event is about the person, not one they only took part in, such as a witness. */
    static boolean primaryIn(Person person, Event event) {
        return person.eventRefs().stream()
                .anyMatch(r -> event.handle().equals(r.event()) && "Primary".equals(r.role()));
    }

    /** An object in words with a link, such as a person's name or "Birth of Lewis Anderson Garner". */
    Optional<Cited> describe(PrimaryObject object) {
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

    String sourceTitle(Source source) {
        return source.title() == null || source.title().isBlank() ? ui.t("unknown") : source.title();
    }

    String familyTitle(Family family) {
        PersonLink father = link(family.father());
        PersonLink mother = link(family.mother());
        return ui.t(
                "family.title",
                father != null ? father.name() : ui.t("unknown"),
                mother != null ? mother.name() : ui.t("unknown"));
    }

    void addRow(List<Row> rows, String key, String value) {
        if (value != null && !value.isBlank()) {
            rows.add(new Row(ui.t(key), value));
        }
    }

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
    String name(Person person) {
        String name = ui.names().formatWith(person.primaryName(), "%f %m %s");
        return name.isEmpty() ? ui.t("unknown") : name;
    }

    static String gender(Person person) {
        return person.gender().name().toLowerCase(Locale.ROOT);
    }

    String initials(Person person) {
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

    static String firstLetter(String text) {
        return text.isEmpty() ? "" : text.substring(0, text.offsetByCodePoints(0, 1));
    }

    String lifespan(Person person) {
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

    String year(Event event) {
        if (event == null || event.date() == null) {
            return "";
        }
        var year = DateMath.gregorianYear(event.date());
        if (year.isEmpty()) {
            return "";
        }
        String text = String.valueOf(year.getAsInt());
        GrampsDate date = event.date();
        // Before and after say which way the year may be off, so a death "after 1920" does not read as about 1920.
        return switch (date.modifier()) {
            case BEFORE -> ui.t("year.before", text);
            case AFTER -> ui.t("year.after", text);
            case NONE -> date.quality() == GrampsDate.Quality.REGULAR ? text : ui.t("year.about", text);
            default -> ui.t("year.about", text); // about, ranges, spans and text-only dates
        };
    }

    /** The first dated primary event of the given types, in order of preference; else the first undated. */
    Event firstEvent(Person person, List<String> types) {
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

    String place(Event event) {
        return db.places()
                .get(event.place())
                .map(p -> ui.places().title(p, event.date()))
                .orElse("");
    }

    static int sortValue(Event event) {
        return DateMath.sortValue(event.date());
    }

    /** A sort value that puts undated ones, whose value is 0, last. */
    static int undatedLast(int sortValue) {
        return sortValue == 0 ? Integer.MAX_VALUE : sortValue;
    }

    /** The Gregorian year for a timeline's year column, without any "about" marker; empty if unknown. */
    static String timelineYear(Event event) {
        var year = event.date() == null ? null : DateMath.gregorianYear(event.date());
        return year == null || year.isEmpty() ? "" : String.valueOf(year.getAsInt());
    }

    /** Families in the order they were formed: by marriage date, undated ones last. */
    int familySortValue(Family family) {
        Event marriage = marriage(family);
        int value = marriage == null ? 0 : sortValue(marriage);
        return undatedLast(value);
    }

    Event marriage(Family family) {
        for (EventRef ref : family.eventRefs()) {
            Event event = db.events().get(ref.event()).orElse(null);
            if (event != null && "Marriage".equals(event.type())) {
                return event;
            }
        }
        return null;
    }

    static String partnerOf(Person person, Family family) {
        return person.handle().equals(family.father()) ? family.mother() : family.father();
    }

    /** A child in the family of one of its parents, with the relation to that parent if it is not by birth. */
    ChildView child(ChildRef ref, Family family, Person parent) {
        return new ChildView(link(ref.child()), relationText(relationTo(ref, family, parent.handle())));
    }

    /**
     * The child's relation to one parent of the family, as Gramps writes it ("Birth", "Foster"), or
     * {@code null} if the person is not a parent of the family.
     */
    static String relationTo(ChildRef ref, Family family, String parent) {
        if (parent.equals(family.father())) {
            return ref.fatherRelation();
        }
        return parent.equals(family.mother()) ? ref.motherRelation() : null;
    }

    /** A child's relation to a parent for the page, or empty when it is by birth. */
    String relationText(String relation) {
        return relation == null || relation.equals("Birth") ? "" : ui.type("child", relation);
    }

    /**
     * What a parent is to a child not by birth, as "Foster father" or "Stepmother", from the parent's side;
     * empty when by birth. A custom relation is shown as entered.
     */
    String parentText(String relation, boolean father) {
        if (relation == null || relation.equals("Birth")) {
            return "";
        }
        String text = ui.findType("parent", relation, father ? "father" : "mother");
        return text != null ? text : ui.type("parent", relation);
    }

    ChildView child(ChildRef ref) {
        PersonLink person = link(ref.child());
        String relation = Objects.equals(ref.fatherRelation(), "Birth") && Objects.equals(ref.motherRelation(), "Birth")
                ? ""
                : ui.type("child", !"Birth".equals(ref.fatherRelation()) ? ref.fatherRelation() : ref.motherRelation());
        return new ChildView(person, relation);
    }

    List<Row> attributes(List<Attribute> attributes) {
        return attributes.stream()
                .map(a -> new Row(ui.type("attribute", a.type()), text(a.value())))
                .toList();
    }

    List<NoteView> notes(List<String> handles) {
        List<NoteView> notes = new ArrayList<>();
        for (String handle : handles) {
            db.notes().get(handle).map(this::note).ifPresent(notes::add);
        }
        return notes;
    }

    NoteView note(Note note) {
        return new NoteView(ui.type("note", note.type()), text(note.text(), note.preformatted()), note.preformatted());
    }

    /** Plain text from the export, with the web addresses typed in it as links. */
    Text text(String plain) {
        return plain == null ? Text.plain("") : text(new StyledText(plain, List.of()), false);
    }

    // Styles shown as in Gramps. Font, size, colour and highlight are left out: the site keeps its own type and
    // colours, which also have to work in the dark theme.
    private static final Map<String, String> STYLES = Map.of(
            "bold", "st-bold",
            "italic", "st-italic",
            "underline", "st-underline",
            "strikethrough", "st-strikethrough",
            "superscript", "st-superscript",
            "subscript", "st-subscript");

    /** A part of a text that is a link, at UTF-16 offsets; {@code label} replaces its text when not null. */
    private record LinkSpan(int start, int end, String url, boolean external, String label) {}

    private record Mark(int start, int end, String style) {}

    /**
     * Styled text as runs of links and plain text. Links are those of the link style that lead to a web address
     * or a published object, and addresses typed outside them. A preformatted text keeps typed addresses in full,
     * so that columns stay aligned; other text shows them shortened.
     */
    Text text(StyledText styled, boolean preformatted) {
        String text = styled.text();
        List<LinkSpan> links = new ArrayList<>();
        List<Mark> marks = new ArrayList<>();
        List<Mark> linkStyled = new ArrayList<>();
        for (StyledText.Style style : styled.styles()) {
            for (StyledText.Range range : style.ranges()) {
                int start = offset(text, range.start());
                int end = offset(text, range.end());
                if (start >= end) {
                    continue;
                }
                if ("link".equals(style.name())) {
                    linkStyled.add(new Mark(start, end, ""));
                    String internal = target(style.value());
                    String external = internal == null ? WebLinks.url(style.value()) : null;
                    if (internal != null && !internal.isEmpty()) {
                        links.add(new LinkSpan(start, end, internal, false, null));
                    } else if (external != null) {
                        links.add(new LinkSpan(start, end, external, true, null));
                    }
                } else if (STYLES.containsKey(style.name())) {
                    marks.add(new Mark(start, end, STYLES.get(style.name())));
                }
            }
        }
        for (WebLinks.Found found : WebLinks.find(text)) {
            if (linkStyled.stream().noneMatch(m -> m.start() < found.end() && found.start() < m.end())) {
                String typed = text.substring(found.start(), found.end());
                links.add(new LinkSpan(
                        found.start(), found.end(), found.url(), true, preformatted ? typed : WebLinks.shorten(typed)));
            }
        }
        links.sort(Comparator.comparingInt(LinkSpan::start));
        // Links in Gramps do not overlap; if an export has some that do, the first wins.
        List<LinkSpan> kept = new ArrayList<>();
        for (LinkSpan link : links) {
            if (kept.isEmpty() || kept.getLast().end() <= link.start()) {
                kept.add(link);
            }
        }

        TreeSet<Integer> cuts = new TreeSet<>(List.of(0, text.length()));
        kept.forEach(l -> {
            cuts.add(l.start());
            cuts.add(l.end());
        });
        marks.forEach(m -> {
            cuts.add(m.start());
            cuts.add(m.end());
        });
        List<Run> runs = new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        LinkSpan current = null;
        Integer start = cuts.first();
        for (Integer end = cuts.higher(start); end != null; start = end, end = cuts.higher(end)) {
            int from = start;
            LinkSpan link = kept.stream()
                    .filter(l -> l.start() <= from && from < l.end())
                    .findFirst()
                    .orElse(null);
            if (link != current) {
                if (!pieces.isEmpty()) {
                    runs.add(run(current, pieces));
                }
                pieces = new ArrayList<>();
                current = link;
            }
            String style = String.join(
                    " ",
                    marks.stream()
                            .filter(m -> m.start() <= from && from < m.end())
                            .map(Mark::style)
                            .distinct()
                            .sorted()
                            .toList());
            if (link != null && link.label() != null) {
                // A shortened address is one piece in the style of its start.
                if (pieces.isEmpty()) {
                    pieces.add(new Piece(link.label(), style));
                }
            } else {
                pieces.add(new Piece(text.substring(from, end), style));
            }
        }
        if (!pieces.isEmpty()) {
            runs.add(run(current, pieces));
        }
        return new Text(List.copyOf(runs));
    }

    private static Run run(LinkSpan link, List<Piece> pieces) {
        return new Run(link == null ? "" : link.url(), link != null && link.external(), List.copyOf(pieces));
    }

    /**
     * The UTF-16 offset of a Gramps offset, which counts code points as Python strings do, clamped to the text: an
     * export may come from someone else.
     */
    private static int offset(String text, int codePoints) {
        int count = text.codePointCount(0, text.length());
        return text.offsetByCodePoints(0, Math.clamp(codePoints, 0, count));
    }

    /**
     * The page of an object a note links to, as Gramps writes the link ({@code gramps://Person/handle/...}): empty
     * when the object is not published or has no page, and {@code null} when the link is not to an object.
     * Living people are not linked, as their names are not published.
     */
    // Gramps: gui/editors/editlink.py; the XML export writes handles with a leading "_" (plugins/export/exportxml.py)
    String target(String link) {
        if (link == null || !link.startsWith("gramps://")) {
            return null;
        }
        String[] parts = link.substring("gramps://".length()).split("/");
        if (parts.length != 3 || !parts[1].equals("handle")) {
            return "";
        }
        String handle = "_" + parts[2];
        return switch (parts[0]) {
            case "Person" ->
                db.people()
                        .get(handle)
                        .filter(p -> !data.isLiving(p.handle()))
                        .map(Urls::person)
                        .orElse("");
            case "Family" -> db.families().get(handle).map(Urls::family).orElse("");
            case "Place" -> db.places().get(handle).map(Urls::place).orElse("");
            case "Source" -> db.sources().get(handle).map(Urls::source).orElse("");
            case "Citation" -> db.citations().get(handle).map(this::sourceUrl).orElse("");
            case "Media" -> db.media().get(handle).map(Urls::media).orElse("");
            default -> "";
        };
    }

    /** Links to other websites from the Internet tab of Gramps, leaving out addresses that are not web or email. */
    List<WebLink> webLinks(List<Url> urls) {
        List<WebLink> links = new ArrayList<>();
        for (Url url : urls) {
            String href = WebLinks.url(url.href());
            if (href == null) {
                continue;
            }
            boolean described = url.description() != null && !url.description().isBlank();
            links.add(
                    described
                            ? new WebLink(href, url.description().strip(), WebLinks.site(href))
                            : new WebLink(href, WebLinks.shorten(href), ""));
        }
        return links;
    }

    List<CitationView> citations(List<String> handles) {
        List<CitationView> result = new ArrayList<>();
        for (String handle : handles) {
            db.citations()
                    .get(handle)
                    .ifPresent(c -> result.add(new CitationView(
                            sourceTitle(c),
                            sourceUrl(c),
                            text(c.page()),
                            ui.date(c.date()),
                            ui.t("confidence." + Math.clamp(c.confidence(), 0, 4)))));
        }
        return result;
    }

    String sourceUrl(Citation citation) {
        return db.sources().get(citation.source()).map(Urls::source).orElse("");
    }

    String sourceTitle(Citation citation) {
        return db.sources().get(citation.source()).map(this::sourceTitle).orElse(ui.t("unknown"));
    }

    /** Collects the citations shown on a page, each once, with what they support. */
    final class SourceCollector {
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
                    .ifPresent(c -> uses.add(
                            new SourceUse(sourceTitle(c), sourceUrl(c), text(c.page()), String.join(", ", what)))));
            return uses;
        }
    }
}
