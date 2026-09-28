package me.hejl.gramps.model;

import java.util.List;

/** Enumerates the outgoing references of an object, including those held by its secondary objects. */
public final class References {

    /** Receives one reference; an object referring to the same target twice reports it twice. */
    @FunctionalInterface
    public interface Visitor {
        void visit(ObjectType type, String handle);
    }

    private References() {}

    public static void visit(PrimaryObject object, Visitor v) {
        switch (object) {
            case Person p -> {
                p.names().forEach(n -> notesAndCitations(v, n.notes(), n.citations()));
                p.eventRefs().forEach(r -> eventRef(v, r));
                p.ldsOrdinances().forEach(o -> lds(v, o));
                p.media().forEach(r -> mediaRef(v, r));
                p.addresses().forEach(a -> notesAndCitations(v, a.notes(), a.citations()));
                attributes(v, p.attributes());
                all(v, ObjectType.FAMILY, p.parentFamilies());
                all(v, ObjectType.FAMILY, p.families());
                for (PersonRef r : p.associations()) {
                    v.visit(ObjectType.PERSON, r.person());
                    notesAndCitations(v, r.notes(), r.citations());
                }
                notesAndCitations(v, p.notes(), p.citations());
                all(v, ObjectType.TAG, p.tags());
            }
            case Family f -> {
                optional(v, ObjectType.PERSON, f.father());
                optional(v, ObjectType.PERSON, f.mother());
                f.eventRefs().forEach(r -> eventRef(v, r));
                f.ldsOrdinances().forEach(o -> lds(v, o));
                f.media().forEach(r -> mediaRef(v, r));
                for (ChildRef c : f.children()) {
                    v.visit(ObjectType.PERSON, c.child());
                    notesAndCitations(v, c.notes(), c.citations());
                }
                attributes(v, f.attributes());
                notesAndCitations(v, f.notes(), f.citations());
                all(v, ObjectType.TAG, f.tags());
            }
            case Event e -> {
                optional(v, ObjectType.PLACE, e.place());
                attributes(v, e.attributes());
                e.media().forEach(r -> mediaRef(v, r));
                notesAndCitations(v, e.notes(), e.citations());
                all(v, ObjectType.TAG, e.tags());
            }
            case Place p -> {
                p.enclosedBy().forEach(r -> v.visit(ObjectType.PLACE, r.place()));
                p.media().forEach(r -> mediaRef(v, r));
                notesAndCitations(v, p.notes(), p.citations());
                all(v, ObjectType.TAG, p.tags());
            }
            case Source s -> {
                s.media().forEach(r -> mediaRef(v, r));
                for (RepoRef r : s.repositories()) {
                    v.visit(ObjectType.REPOSITORY, r.repository());
                    all(v, ObjectType.NOTE, r.notes());
                }
                all(v, ObjectType.NOTE, s.notes());
                all(v, ObjectType.TAG, s.tags());
            }
            case Citation c -> {
                optional(v, ObjectType.SOURCE, c.source());
                c.media().forEach(r -> mediaRef(v, r));
                all(v, ObjectType.NOTE, c.notes());
                all(v, ObjectType.TAG, c.tags());
            }
            case Media m -> {
                attributes(v, m.attributes());
                notesAndCitations(v, m.notes(), m.citations());
                all(v, ObjectType.TAG, m.tags());
            }
            case Repository r -> {
                r.addresses().forEach(a -> notesAndCitations(v, a.notes(), a.citations()));
                all(v, ObjectType.NOTE, r.notes());
                all(v, ObjectType.TAG, r.tags());
            }
            case Note n -> all(v, ObjectType.TAG, n.tags());
        }
    }

    private static void eventRef(Visitor v, EventRef r) {
        v.visit(ObjectType.EVENT, r.event());
        attributes(v, r.attributes());
        notesAndCitations(v, r.notes(), r.citations());
    }

    private static void mediaRef(Visitor v, MediaRef r) {
        v.visit(ObjectType.MEDIA, r.media());
        attributes(v, r.attributes());
        notesAndCitations(v, r.notes(), r.citations());
    }

    private static void lds(Visitor v, LdsOrdinance o) {
        optional(v, ObjectType.PLACE, o.place());
        optional(v, ObjectType.FAMILY, o.sealedTo());
        notesAndCitations(v, o.notes(), o.citations());
    }

    private static void attributes(Visitor v, List<Attribute> attributes) {
        attributes.forEach(a -> notesAndCitations(v, a.notes(), a.citations()));
    }

    private static void notesAndCitations(Visitor v, List<String> notes, List<String> citations) {
        all(v, ObjectType.NOTE, notes);
        all(v, ObjectType.CITATION, citations);
    }

    private static void all(Visitor v, ObjectType type, List<String> handles) {
        handles.forEach(h -> v.visit(type, h));
    }

    private static void optional(Visitor v, ObjectType type, String handle) {
        if (handle != null) {
            v.visit(type, handle);
        }
    }
}
