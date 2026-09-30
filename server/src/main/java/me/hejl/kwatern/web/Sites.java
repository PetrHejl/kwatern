package me.hejl.kwatern.web;

import java.util.HashSet;
import java.util.Map;
import me.hejl.kwatern.auth.Grants;

/**
 * The versions of the site served together, each built from the export by the privacy filter with its own
 * options; which one a request sees depends on whether its visitor is signed in and what the users file grants
 * them. Keeping them apart means a page never has to check who views it: the public version does not contain
 * what it must not show, nor a member's what that member must not see.
 *
 * @param everyone what visitors who are not signed in see, or {@code null} if the site is private
 * @param members  what signed-in members see, by what they are granted: one for each of {@link Grants#ALL}, or
 *                 none if the site has no login. Views that show the same are the same site, the public one
 *                 included, which tells members granted nothing more that they see the public view.
 */
public record Sites(Site everyone, Map<Grants, Site> members) {

    public Sites {
        members = Map.copyOf(members);
        if (!members.isEmpty() && !members.keySet().equals(new HashSet<>(Grants.ALL))) {
            throw new IllegalArgumentException("a members' view for each grants is needed");
        }
        if (everyone == null && members.isEmpty()) {
            throw new IllegalArgumentException("no site");
        }
    }

    /** A site without a login. */
    public static Sites open(Site site) {
        return new Sites(site, Map.of());
    }

    public boolean hasMembers() {
        return !members.isEmpty();
    }

    /** What a member with these grants sees. */
    public Site members(Grants grants) {
        return members.get(grants);
    }

    /** The version that shows the most, which has all the others have; also for what they share: the options. */
    public Site widest() {
        return hasMembers() ? members.get(Grants.EVERYTHING) : everyone;
    }
}
