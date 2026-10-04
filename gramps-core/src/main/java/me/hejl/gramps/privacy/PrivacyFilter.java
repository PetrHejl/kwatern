package me.hejl.gramps.privacy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import me.hejl.gramps.model.Address;
import me.hejl.gramps.model.Attribute;
import me.hejl.gramps.model.Bookmark;
import me.hejl.gramps.model.ChildRef;
import me.hejl.gramps.model.Citation;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.Gender;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.LdsOrdinance;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.MediaRef;
import me.hejl.gramps.model.Name;
import me.hejl.gramps.model.Note;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PersonRef;
import me.hejl.gramps.model.Place;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.References;
import me.hejl.gramps.model.RepoRef;
import me.hejl.gramps.model.Repository;
import me.hejl.gramps.model.Source;
import me.hejl.gramps.model.Tag;
import me.hejl.gramps.model.Url;

/**
 * Removes everything that must not be published, depending on {@link PrivacyOptions}:
 *
 * <ul>
 *   <li>objects and details marked private in Gramps;
 *   <li>everything about people who may be alive except their place in the tree: they keep their family
 *       links but lose names, gender, events, notes, media and all other details, and families with a
 *       living parent lose their events and details too;
 *   <li>objects that were only reachable through removed ones, such as the note or birth event of a living
 *       person, or a place only used by that event;
 *   <li>media that no published object links to.
 * </ul>
 *
 * <p>Every reference in the result points to an object that exists.
 */
public final class PrivacyFilter {

    private final GrampsDatabase db;
    private final Set<String> living;
    private final boolean hidePrivate;
    private final Set<String> kept = new HashSet<>();

    private PrivacyFilter(GrampsDatabase db, Set<String> living, boolean hidePrivate) {
        this.db = db;
        this.living = living;
        this.hidePrivate = hidePrivate;
    }

    /** Applies the default options, hiding living people and private records. */
    public static PublicDatabase apply(GrampsDatabase db, ProbablyAlive alive) {
        return apply(db, alive, PrivacyOptions.DEFAULT);
    }

    public static PublicDatabase apply(GrampsDatabase db, ProbablyAlive alive, PrivacyOptions options) {
        Set<String> mayBeAlive = new HashSet<>();
        for (Person person : db.people().all()) {
            if (!(options.hidePrivate() && person.priv()) && alive.isAlive(person)) {
                mayBeAlive.add(person.handle());
            }
        }
        // A HashSet, since families without a father or mother look up null.
        Set<String> living = options.hideLiving() ? mayBeAlive : new HashSet<>();
        return new PublicDatabase(new PrivacyFilter(db, living, options.hidePrivate()).filter(), living, mayBeAlive);
    }

    private GrampsDatabase filter() {
        db.objects().filter(o -> visible(o.priv())).forEach(o -> kept.add(o.handle()));
        db.tags().forEach(t -> kept.add(t.handle()));

        // Removing an object can leave others unreachable (event -> place -> enclosing place), so repeat
        // until nothing changes.
        List<PrimaryObject> result;
        while (true) {
            result = db.objects()
                    .filter(o -> kept.contains(o.handle()))
                    .map(this::prune)
                    .toList();
            Set<String> referenced = new HashSet<>();
            for (PrimaryObject object : result) {
                References.visit(object, (type, handle) -> referenced.add(handle));
            }
            boolean changed = false;
            for (PrimaryObject object : result) {
                if (orphaned(object, referenced)) {
                    kept.remove(object.handle());
                    changed = true;
                }
            }
            for (Tag tag : db.tags()) {
                if (kept.contains(tag.handle()) && !referenced.contains(tag.handle())) {
                    kept.remove(tag.handle());
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }

        var people = new ArrayList<Person>();
        var families = new ArrayList<Family>();
        var events = new ArrayList<Event>();
        var places = new ArrayList<Place>();
        var sources = new ArrayList<Source>();
        var citations = new ArrayList<Citation>();
        var media = new ArrayList<Media>();
        var repositories = new ArrayList<Repository>();
        var notes = new ArrayList<Note>();
        for (PrimaryObject object : result) {
            switch (object) {
                case Person p -> people.add(p);
                case Family f -> families.add(f);
                case Event e -> events.add(e);
                case Place p -> places.add(p);
                case Source s -> sources.add(s);
                case Citation c -> citations.add(c);
                case Media m -> media.add(m);
                case Repository r -> repositories.add(r);
                case Note n -> notes.add(n);
            }
        }
        List<Tag> tags =
                db.tags().stream().filter(t -> kept.contains(t.handle())).toList();
        String home = db.homePerson().map(Person::handle).filter(kept::contains).orElse(null);
        List<Bookmark> bookmarks =
                db.bookmarks().stream().filter(b -> kept.contains(b.handle())).toList();
        return new GrampsDatabase(
                db.schemaVersion(),
                db.header(),
                people,
                families,
                events,
                places,
                sources,
                citations,
                media,
                repositories,
                notes,
                tags,
                home,
                bookmarks,
                db.nameFormats(),
                db.nameMaps());
    }

    /**
     * Whether an object only existed to serve objects that are now gone. People and families always stay,
     * as does anything that was not referenced to begin with, such as a source without citations, except media.
     */
    private boolean orphaned(PrimaryObject object, Set<String> referenced) {
        if (object instanceof Person || object instanceof Family) {
            return false;
        }
        if (object instanceof Citation c && !kept.contains(c.source())) {
            return true;
        }
        // A photo nothing links to yet may well show someone alive, and nothing here says who.
        if (object instanceof Media) {
            return !referenced.contains(object.handle());
        }
        return !referenced.contains(object.handle())
                && !db.referrers(object.handle()).isEmpty();
    }

    // ---------------------------------------------------------------- pruning

    private PrimaryObject prune(PrimaryObject object) {
        return switch (object) {
            case Person p -> living.contains(p.handle()) ? livingPerson(p) : person(p);
            case Family f -> family(f);
            case Event e ->
                new Event(
                        e.handle(),
                        e.id(),
                        e.change(),
                        e.priv(),
                        e.type(),
                        e.date(),
                        ref(e.place()),
                        e.description(),
                        attributes(e.attributes()),
                        refs(e.notes()),
                        refs(e.citations()),
                        mediaRefs(e.media()),
                        refs(e.tags()));
            case Place p ->
                new Place(
                        p.handle(),
                        p.id(),
                        p.change(),
                        p.priv(),
                        p.type(),
                        p.title(),
                        p.code(),
                        p.names(),
                        p.latitude(),
                        p.longitude(),
                        keep(p.enclosedBy(), r -> kept(r.place()), r -> r),
                        p.alternateLocations(),
                        mediaRefs(p.media()),
                        urls(p.urls()),
                        refs(p.notes()),
                        refs(p.citations()),
                        refs(p.tags()));
            case Source s ->
                new Source(
                        s.handle(),
                        s.id(),
                        s.change(),
                        s.priv(),
                        s.title(),
                        s.author(),
                        s.pubInfo(),
                        s.abbreviation(),
                        refs(s.notes()),
                        mediaRefs(s.media()),
                        attributes(s.attributes()),
                        keep(
                                s.repositories(),
                                r -> visible(r.priv()) && kept(r.repository()),
                                r -> new RepoRef(
                                        r.repository(), r.callNumber(), r.medium(), r.priv(), refs(r.notes()))),
                        refs(s.tags()));
            case Citation c ->
                new Citation(
                        c.handle(),
                        c.id(),
                        c.change(),
                        c.priv(),
                        c.date(),
                        c.page(),
                        c.confidence(),
                        refs(c.notes()),
                        mediaRefs(c.media()),
                        attributes(c.attributes()),
                        c.source(),
                        refs(c.tags()));
            case Media m ->
                new Media(
                        m.handle(),
                        m.id(),
                        m.change(),
                        m.priv(),
                        m.path(),
                        m.mime(),
                        m.checksum(),
                        m.description(),
                        attributes(m.attributes()),
                        m.date(),
                        refs(m.notes()),
                        refs(m.citations()),
                        refs(m.tags()));
            case Repository r ->
                new Repository(
                        r.handle(),
                        r.id(),
                        r.change(),
                        r.priv(),
                        r.name(),
                        r.type(),
                        addresses(r.addresses()),
                        urls(r.urls()),
                        refs(r.notes()),
                        refs(r.tags()));
            case Note n ->
                new Note(
                        n.handle(), n.id(), n.change(), n.priv(), n.type(), n.preformatted(), n.text(), refs(n.tags()));
        };
    }

    private Person person(Person p) {
        return new Person(
                p.handle(),
                p.id(),
                p.change(),
                p.priv(),
                p.gender(),
                keep(p.names(), n -> visible(n.priv()), this::name),
                eventRefs(p.eventRefs()),
                keep(p.ldsOrdinances(), o -> visible(o.priv()), this::lds),
                mediaRefs(p.media()),
                addresses(p.addresses()),
                attributes(p.attributes()),
                urls(p.urls()),
                parentFamilies(p),
                refs(p.families()),
                keep(
                        p.associations(),
                        r -> visible(r.priv()) && kept(r.person()),
                        r -> new PersonRef(r.person(), r.relation(), r.priv(), refs(r.citations()), refs(r.notes()))),
                refs(p.notes()),
                refs(p.citations()),
                refs(p.tags()));
    }

    /** A living person: only the links that give them a place in the tree. */
    private Person livingPerson(Person p) {
        return new Person(
                p.handle(),
                p.id(),
                p.change(),
                p.priv(),
                Gender.UNKNOWN,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                parentFamilies(p),
                refs(p.families()),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * The families a person is a child of, as far as they are published: a private child reference hides the
     * link to that family, as it is gone from the family's children too.
     */
    // Gramps: gen/proxy/private.py sanitize_person
    private List<String> parentFamilies(Person p) {
        return keep(
                p.parentFamilies(),
                handle -> kept(handle)
                        && db.families()
                                .get(handle)
                                .map(f -> f.children().stream()
                                        .anyMatch(c -> c.child().equals(p.handle()) && visible(c.priv())))
                                .orElse(false),
                handle -> handle);
    }

    private Family family(Family f) {
        String father = ref(f.father());
        String mother = ref(f.mother());
        List<ChildRef> children = keep(
                f.children(),
                c -> visible(c.priv()) && kept(c.child()),
                c -> new ChildRef(
                        c.child(),
                        c.fatherRelation(),
                        c.motherRelation(),
                        c.priv(),
                        refs(c.citations()),
                        refs(c.notes())));
        if (living.contains(father) || living.contains(mother)) {
            return new Family(
                    f.handle(),
                    f.id(),
                    f.change(),
                    f.priv(),
                    f.relationship(),
                    father,
                    mother,
                    List.of(),
                    List.of(),
                    List.of(),
                    children,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        return new Family(
                f.handle(),
                f.id(),
                f.change(),
                f.priv(),
                f.relationship(),
                father,
                mother,
                eventRefs(f.eventRefs()),
                keep(f.ldsOrdinances(), o -> visible(o.priv()), this::lds),
                mediaRefs(f.media()),
                children,
                attributes(f.attributes()),
                refs(f.notes()),
                refs(f.citations()),
                refs(f.tags()));
    }

    private Name name(Name n) {
        return new Name(
                n.alternate(),
                n.type(),
                n.first(),
                n.call(),
                n.surnames(),
                n.suffix(),
                n.title(),
                n.nick(),
                n.familyNick(),
                n.group(),
                n.sortAs(),
                n.displayAs(),
                n.date(),
                n.priv(),
                refs(n.citations()),
                refs(n.notes()));
    }

    private LdsOrdinance lds(LdsOrdinance o) {
        return new LdsOrdinance(
                o.type(),
                o.date(),
                o.temple(),
                ref(o.place()),
                o.status(),
                ref(o.sealedTo()),
                o.priv(),
                refs(o.citations()),
                refs(o.notes()));
    }

    private List<EventRef> eventRefs(List<EventRef> refs) {
        return keep(
                refs,
                r -> visible(r.priv()) && kept(r.event()),
                r -> new EventRef(
                        r.event(),
                        r.role(),
                        r.priv(),
                        attributes(r.attributes()),
                        refs(r.citations()),
                        refs(r.notes())));
    }

    private List<MediaRef> mediaRefs(List<MediaRef> refs) {
        return keep(
                refs,
                r -> visible(r.priv()) && kept(r.media()),
                r -> new MediaRef(
                        r.media(),
                        r.priv(),
                        r.region(),
                        attributes(r.attributes()),
                        refs(r.citations()),
                        refs(r.notes())));
    }

    private List<Attribute> attributes(List<Attribute> attributes) {
        return keep(
                attributes,
                a -> visible(a.priv()),
                a -> new Attribute(a.type(), a.value(), a.priv(), refs(a.citations()), refs(a.notes())));
    }

    private List<Address> addresses(List<Address> addresses) {
        return keep(
                addresses,
                a -> visible(a.priv()),
                a -> new Address(
                        a.date(),
                        a.street(),
                        a.locality(),
                        a.city(),
                        a.county(),
                        a.state(),
                        a.country(),
                        a.postal(),
                        a.phone(),
                        a.priv(),
                        refs(a.citations()),
                        refs(a.notes())));
    }

    private List<Url> urls(List<Url> urls) {
        return keep(urls, u -> visible(u.priv()), u -> u);
    }

    private List<String> refs(List<String> handles) {
        return keep(handles, this::kept, h -> h);
    }

    private String ref(String handle) {
        return kept(handle) ? handle : null;
    }

    private boolean visible(boolean priv) {
        return !hidePrivate || !priv;
    }

    private boolean kept(String handle) {
        return handle != null && kept.contains(handle);
    }

    private static <T> List<T> keep(List<T> items, Predicate<T> keep, Function<T, T> copy) {
        List<T> result = new ArrayList<>(items.size());
        for (T item : items) {
            if (keep.test(item)) {
                result.add(copy.apply(item));
            }
        }
        return result;
    }
}
