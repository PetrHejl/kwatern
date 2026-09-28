package me.hejl.gramps.privacy;

import java.util.Set;
import me.hejl.gramps.model.GrampsDatabase;

/**
 * A database safe to publish, as produced by {@link PrivacyFilter}.
 *
 * @param living handles of people who may be alive; they are kept only as places in the tree, without
 *               names or any other details
 */
public record PublicDatabase(GrampsDatabase database, Set<String> living) {

    public PublicDatabase {
        living = Set.copyOf(living);
    }

    public boolean isLiving(String personHandle) {
        return living.contains(personHandle);
    }
}
