package me.hejl.kwatern.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import me.hejl.kwatern.auth.Login.Access;

/**
 * Who views a page, for the header: whether to offer signing in, and who is signed in.
 *
 * @param user the signed-in user, or {@code null}
 * @param path the path and query of the page, to come back to after signing in
 */
public record Viewer(Access access, String user, String path) {

    static final Viewer OPEN = new Viewer(Access.OPEN, null, "/");

    public boolean signedIn() {
        return user != null;
    }

    /** A "Sign in" link in the header: the site has a members' view and the viewer does not see it. */
    public boolean offersSignIn() {
        return access == Access.MEMBERS && user == null;
    }

    /**
     * The bar saying this is the members' view, so that nobody shares what they see thinking it is public. A
     * private site has no public view to mistake it for.
     */
    public boolean membersBar() {
        return access == Access.MEMBERS && user != null;
    }

    /** Whether the whole site is behind the sign-in: its page then shows nothing of the site. */
    public boolean privateSite() {
        return access == Access.PRIVATE;
    }

    public String signInUrl() {
        return path.equals("/") ? "/sign-in" : "/sign-in?next=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }
}
