package me.hejl.gramps.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** A complete Gramps database as loaded from an XML export. */
public final class GrampsDatabase {

    private final String schemaVersion;
    private final Header header;
    private final Table<Person> people;
    private final Table<Family> families;
    private final Table<Event> events;
    private final Table<Place> places;
    private final Table<Source> sources;
    private final Table<Citation> citations;
    private final Table<Media> media;
    private final Table<Repository> repositories;
    private final Table<Note> notes;
    private final Map<String, Tag> tags;
    private final String homePerson;
    private final List<Bookmark> bookmarks;
    private final List<NameFormat> nameFormats;
    private final List<NameMap> nameMaps;
    private final Map<String, List<PrimaryObject>> referrers;

    /**
     * @param schemaVersion Gramps XML schema version, e.g. {@code 1.7.2}
     * @param homePerson    handle of the home person, or {@code null}
     */
    public GrampsDatabase(
            String schemaVersion,
            Header header,
            List<Person> people,
            List<Family> families,
            List<Event> events,
            List<Place> places,
            List<Source> sources,
            List<Citation> citations,
            List<Media> media,
            List<Repository> repositories,
            List<Note> notes,
            List<Tag> tags,
            String homePerson,
            List<Bookmark> bookmarks,
            List<NameFormat> nameFormats,
            List<NameMap> nameMaps) {
        this.schemaVersion = schemaVersion;
        this.header = header;
        this.people = new Table<>(people);
        this.families = new Table<>(families);
        this.events = new Table<>(events);
        this.places = new Table<>(places);
        this.sources = new Table<>(sources);
        this.citations = new Table<>(citations);
        this.media = new Table<>(media);
        this.repositories = new Table<>(repositories);
        this.notes = new Table<>(notes);
        Map<String, Tag> tagMap = new LinkedHashMap<>();
        tags.forEach(t -> tagMap.put(t.handle(), t));
        this.tags = Collections.unmodifiableMap(tagMap);
        this.homePerson = homePerson;
        this.bookmarks = List.copyOf(bookmarks);
        this.nameFormats = List.copyOf(nameFormats);
        this.nameMaps = List.copyOf(nameMaps);
        this.referrers = indexReferrers();
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public Header header() {
        return header;
    }

    public Table<Person> people() {
        return people;
    }

    public Table<Family> families() {
        return families;
    }

    public Table<Event> events() {
        return events;
    }

    public Table<Place> places() {
        return places;
    }

    public Table<Source> sources() {
        return sources;
    }

    public Table<Citation> citations() {
        return citations;
    }

    public Table<Media> media() {
        return media;
    }

    public Table<Repository> repositories() {
        return repositories;
    }

    public Table<Note> notes() {
        return notes;
    }

    public Collection<Tag> tags() {
        return tags.values();
    }

    public Optional<Tag> tag(String handle) {
        return Optional.ofNullable(handle == null ? null : tags.get(handle));
    }

    public Optional<Person> homePerson() {
        return people.get(homePerson);
    }

    public List<Bookmark> bookmarks() {
        return bookmarks;
    }

    public List<NameFormat> nameFormats() {
        return nameFormats;
    }

    public List<NameMap> nameMaps() {
        return nameMaps;
    }

    /** Every primary object, table by table. */
    public Stream<PrimaryObject> objects() {
        return Stream.of(people, families, events, places, sources, citations, media, repositories, notes)
                .flatMap(table -> table.all().stream());
    }

    /** Whether an object of the given type with this handle exists. */
    public boolean contains(ObjectType type, String handle) {
        return switch (type) {
            case PERSON -> people.contains(handle);
            case FAMILY -> families.contains(handle);
            case EVENT -> events.contains(handle);
            case PLACE -> places.contains(handle);
            case SOURCE -> sources.contains(handle);
            case CITATION -> citations.contains(handle);
            case MEDIA -> media.contains(handle);
            case REPOSITORY -> repositories.contains(handle);
            case NOTE -> notes.contains(handle);
            case TAG -> handle != null && tags.containsKey(handle);
        };
    }

    /** The objects that refer to the object or tag with this handle, each listed once, in file order. */
    public List<PrimaryObject> referrers(String handle) {
        return referrers.getOrDefault(handle, List.of());
    }

    /**
     * Lists references to handles that do not exist in this database. A clean Gramps export has none;
     * a hand-edited or partially filtered one may.
     *
     * @return human-readable descriptions such as {@code "Person I0001 -> note abc123"}
     */
    public List<String> danglingReferences() {
        List<String> problems = new ArrayList<>();
        objects()
                .forEach(object -> References.visit(object, (type, handle) -> {
                    if (!contains(type, handle)) {
                        problems.add(object.getClass().getSimpleName() + " " + object.id() + " -> "
                                + type.name().toLowerCase(Locale.ROOT) + " " + handle);
                    }
                }));
        return problems;
    }

    private Map<String, List<PrimaryObject>> indexReferrers() {
        Map<String, List<PrimaryObject>> index = new HashMap<>();
        objects()
                .forEach(object -> References.visit(object, (type, handle) -> {
                    List<PrimaryObject> list = index.computeIfAbsent(handle, h -> new ArrayList<>(2));
                    // References of one object are visited together, so a repeat is always the last entry.
                    if (list.isEmpty() || list.getLast() != object) {
                        list.add(object);
                    }
                }));
        index.replaceAll((handle, list) -> List.copyOf(list));
        return index;
    }
}
