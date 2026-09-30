package me.hejl.gramps.privacy;

import java.util.Set;
import me.hejl.gramps.model.GrampsDatabase;

/**
 * A database safe to publish, as produced by {@link PrivacyFilter}.
 *
 * @param living handles of people who may be alive; they are kept only as places in the tree, without
 *               names or any other details
 * @param alive  handles of people who may be alive, whether hidden as {@code living} or shown because the options
 *               allow it
 */
public record PublicDatabase(GrampsDatabase database, Set<String> living, Set<String> alive) {

    public PublicDatabase {
        living = Set.copyOf(living);
        alive = Set.copyOf(alive);
    }

    public boolean isLiving(String personHandle) {
        return living.contains(personHandle);
    }

    /** Whether the person may be alive, even if the options show them. */
    public boolean mayBeAlive(String personHandle) {
        return alive.contains(personHandle);
    }
}
