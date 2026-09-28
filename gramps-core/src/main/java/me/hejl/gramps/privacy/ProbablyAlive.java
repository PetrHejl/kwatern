package me.hejl.gramps.privacy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import me.hejl.gramps.date.DateMath;
import me.hejl.gramps.model.Event;
import me.hejl.gramps.model.EventRef;
import me.hejl.gramps.model.Family;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.model.Person;

/**
 * Decides whether a person may still be alive, in the spirit of Gramps' {@code probably_alive}.
 *
 * <p>A person is dead if they have a death, burial, cremation, cause-of-death, probate or stillbirth event,
 * dated or not. Otherwise the latest year they can have been born is worked out from their own events and
 * those of parents, siblings, spouses and descendants; they are alive if that year plus the maximum age has
 * not passed. With no evidence at all a person counts as alive, so doubt errs towards hiding.
 *
 * <p>Not thread-safe; create one per filtering run.
 */
public final class ProbablyAlive {

    // Standard Gramps event types as written in Gramps XML (gen/lib/eventtype.py)
    private static final Set<String> BIRTH = Set.of("Birth");
    private static final Set<String> BIRTH_FALLBACK = Set.of("Baptism", "Christening", "Stillbirth");
    private static final Set<String> DEATH =
            Set.of("Death", "Burial", "Cremation", "Cause Of Death", "Probate", "Stillbirth");
    private static final int MAX_DESCENDANT_GENERATIONS = 5;

    private final GrampsDatabase db;
    private final AliveRules rules;
    private final int currentYear;
    private final Map<String, Evidence> evidence = new HashMap<>();
    private final Map<String, OptionalInt> descendantBounds = new HashMap<>();

    /**
     * @param currentYear Gregorian year to judge against, normally this year
     */
    public ProbablyAlive(GrampsDatabase db, AliveRules rules, int currentYear) {
        this.db = db;
        this.rules = rules;
        this.currentYear = currentYear;
    }

    public boolean isAlive(Person person) {
        Evidence own = evidence(person);
        if (own.dead) {
            return false;
        }
        OptionalInt bornBy = latestBirthYear(person);
        return bornBy.isEmpty() || bornBy.getAsInt() + rules.maxAge() > currentYear;
    }

    /** The latest year the person can have been born, if anything limits it. */
    OptionalInt latestBirthYear(Person person) {
        Evidence own = evidence(person);
        if (own.birthBy != null) {
            return OptionalInt.of(own.birthBy);
        }
        Bound bound = new Bound();
        bound.add(own.eventsBy);

        Family parents = person.parentFamilies().isEmpty()
                ? null
                : db.families().get(person.parentFamilies().getFirst()).orElse(null);
        if (parents != null) {
            Evidence mother = evidence(parents.mother());
            Evidence father = evidence(parents.father());
            bound.add(mother.deathBy);
            bound.add(father.deathBy == null ? null : father.deathBy + 1);
            bound.add(mother.birthBy == null ? null : mother.birthBy + rules.maxParentAge());
            bound.add(father.birthBy == null ? null : father.birthBy + rules.maxParentAge());
            for (var sibling : parents.children()) {
                if (!sibling.child().equals(person.handle())) {
                    Integer siblingBirth = evidence(sibling.child()).birthBy;
                    bound.add(siblingBirth == null ? null : siblingBirth + rules.maxSiblingAgeDifference());
                }
            }
        }
        for (String familyHandle : person.families()) {
            Family family = db.families().get(familyHandle).orElse(null);
            if (family == null) {
                continue;
            }
            String spouse = person.handle().equals(family.father()) ? family.mother() : family.father();
            Integer spouseBirth = evidence(spouse).birthBy;
            bound.add(spouseBirth == null ? null : spouseBirth + rules.averageGenerationGap());
            for (var child : family.children()) {
                OptionalInt childBirth = descendantBound(child.child(), 1);
                if (childBirth.isPresent()) {
                    bound.add(childBirth.getAsInt() - rules.minGenerationYears());
                }
            }
        }
        return bound.value();
    }

    /** Latest birth year of a person judged only by their own events and their descendants. */
    private OptionalInt descendantBound(String handle, int generation) {
        OptionalInt cached = descendantBounds.get(handle);
        if (cached != null) {
            return cached;
        }
        Person person = db.people().get(handle).orElse(null);
        if (person == null) {
            return OptionalInt.empty();
        }
        Evidence own = evidence(person);
        Bound bound = new Bound();
        bound.add(own.birthBy);
        bound.add(own.eventsBy);
        if (own.birthBy == null && generation < MAX_DESCENDANT_GENERATIONS) {
            for (String familyHandle : person.families()) {
                for (var child :
                        db.families().get(familyHandle).map(Family::children).orElse(List.of())) {
                    OptionalInt childBirth = descendantBound(child.child(), generation + 1);
                    if (childBirth.isPresent()) {
                        bound.add(childBirth.getAsInt() - rules.minGenerationYears());
                    }
                }
            }
        }
        OptionalInt result = bound.value();
        descendantBounds.put(handle, result);
        return result;
    }

    private Evidence evidence(String handle) {
        return handle == null
                ? Evidence.NONE
                : db.people().get(handle).map(this::evidence).orElse(Evidence.NONE);
    }

    private Evidence evidence(Person person) {
        return evidence.computeIfAbsent(person.handle(), h -> collect(person));
    }

    private Evidence collect(Person person) {
        boolean dead = false;
        Integer birthBy = null, fallbackBirthBy = null, deathBy = null, eventsBy = null;
        for (EventRef ref : person.eventRefs()) {
            if (!"Primary".equals(ref.role())) {
                continue;
            }
            Event event = db.events().get(ref.event()).orElse(null);
            if (event == null) {
                continue;
            }
            Integer latest = latestYear(event.date());
            String type = event.type() == null ? "" : event.type();
            if (DEATH.contains(type)) {
                dead = true;
                deathBy = min(deathBy, latest);
            }
            if (BIRTH.contains(type)) {
                birthBy = min(birthBy, latest);
            } else if (BIRTH_FALLBACK.contains(type)) {
                fallbackBirthBy = min(fallbackBirthBy, latest);
            } else {
                // Taking part in any event means having been born by then.
                eventsBy = min(eventsBy, latest);
            }
        }
        for (String familyHandle : person.families()) {
            Family family = db.families().get(familyHandle).orElse(null);
            if (family == null) {
                continue;
            }
            for (EventRef ref : family.eventRefs()) {
                // A marriage or other family event also means having been born by then.
                eventsBy = min(
                        eventsBy,
                        db.events()
                                .get(ref.event())
                                .map(e -> latestYear(e.date()))
                                .orElse(null));
            }
        }
        return new Evidence(dead, birthBy != null ? birthBy : fallbackBirthBy, deathBy, eventsBy);
    }

    /** The latest Gregorian year a date can stand for, or {@code null} if it has no usable year. */
    Integer latestYear(GrampsDate date) {
        if (date == null || date.modifier() == GrampsDate.Modifier.TEXT_ONLY) {
            return null;
        }
        OptionalInt year = date.isCompound() ? DateMath.gregorianStopYear(date) : DateMath.gregorianYear(date);
        if (year.isEmpty()) {
            return null;
        }
        int latest = year.getAsInt();
        if (date.modifier() == GrampsDate.Modifier.AFTER) {
            latest += rules.afterRange();
        } else if (date.modifier() == GrampsDate.Modifier.ABOUT || date.quality() != GrampsDate.Quality.REGULAR) {
            latest += rules.aboutRange();
        }
        return latest;
    }

    private static Integer min(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.min(a, b);
    }

    /**
     * What a person's own events say.
     *
     * @param birthBy  latest birth year from a birth, baptism or christening
     * @param deathBy  latest death year from a death-like event
     * @param eventsBy latest year of their earliest other event
     */
    private record Evidence(boolean dead, Integer birthBy, Integer deathBy, Integer eventsBy) {
        static final Evidence NONE = new Evidence(false, null, null, null);
    }

    /** The tightest of several upper bounds. */
    private static final class Bound {
        private Integer value;

        void add(Integer bound) {
            value = min(value, bound);
        }

        OptionalInt value() {
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }
}
