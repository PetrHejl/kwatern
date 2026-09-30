package me.hejl.kwatern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.kwatern.auth.Grants;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.web.Site;
import me.hejl.kwatern.web.Sites;
import org.junit.jupiter.api.Test;

/** Which view the loader gives to whom: the privacy-critical part of signing in. */
class LoaderTest {

    private static final Grants LIVING = new Grants(true, false);
    private static final Grants PRIVATE = new Grants(false, true);

    private static Sites load(Login.Access access, PrivacyOptions publicOptions) throws Exception {
        Path example = Path.of(System.getProperty("gramps.example"));
        var loader = new Loader(
                new Loader.Settings(example, 110, access, publicOptions, null, false, null, Site.Options.DEFAULT));
        return loader.load().sites();
    }

    private static boolean showsLiving(Site site) {
        return site.data().living().isEmpty();
    }

    private static int distinct(Map<Grants, Site> members) {
        // The same site where views show the same, so counted by identity.
        Set<Site> sites = Collections.newSetFromMap(new IdentityHashMap<>());
        sites.addAll(members.values());
        return sites.size();
    }

    @Test
    void openSiteHasNoMembers() throws Exception {
        Sites sites = load(Login.Access.OPEN, PrivacyOptions.DEFAULT);
        assertFalse(sites.hasMembers());
        assertSame(sites.everyone(), sites.widest());
        assertFalse(showsLiving(sites.everyone()));
    }

    @Test
    void membersSeeThePublicViewAndWhatTheyAreGranted() throws Exception {
        Sites sites = load(Login.Access.MEMBERS, PrivacyOptions.DEFAULT);
        assertFalse(showsLiving(sites.everyone()));
        assertSame(sites.everyone(), sites.members(Grants.NONE), "granted nothing, the public view itself");
        assertTrue(showsLiving(sites.members(LIVING)));
        assertFalse(showsLiving(sites.members(PRIVATE)));
        assertSame(sites.members(Grants.EVERYTHING), sites.widest());
        assertEquals(4, distinct(sites.members()));
    }

    @Test
    void membersNeverSeeLessThanThePublic() throws Exception {
        // The public view shows living people already: granting them changes nothing.
        Sites sites = load(Login.Access.MEMBERS, new PrivacyOptions(false, true));
        assertTrue(showsLiving(sites.everyone()));
        for (Grants grants : Grants.ALL) {
            assertTrue(showsLiving(sites.members(grants)), grants.describe());
        }
        assertSame(sites.everyone(), sites.members(Grants.NONE));
        assertSame(sites.everyone(), sites.members(LIVING));
        assertSame(sites.members(PRIVATE), sites.members(Grants.EVERYTHING));
        assertEquals(2, distinct(sites.members()));
    }

    @Test
    void privateSiteStartsFromTheSafeDefaults() throws Exception {
        // What the public view would show does not matter on a private site, which has none.
        Sites sites = load(Login.Access.PRIVATE, new PrivacyOptions(false, false));
        assertNull(sites.everyone());
        assertFalse(showsLiving(sites.members(Grants.NONE)), "granted nothing, living people stay hidden");
        assertFalse(showsLiving(sites.members(PRIVATE)));
        assertTrue(showsLiving(sites.members(LIVING)));
        assertEquals(4, distinct(sites.members()), "private records hidden unless granted");
    }
}
