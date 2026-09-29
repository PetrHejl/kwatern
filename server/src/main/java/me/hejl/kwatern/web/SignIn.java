package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.auth.Sessions;

/** Signing in and out with the forms at {@code /sign-in} and {@code /sign-out}, and the session cookie. */
final class SignIn {

    private static final String COOKIE = "kwatern-session";
    // Larger sign-in forms are refused unread.
    private static final int MAX_FORM_BYTES = 4096;

    private final Login login;
    private final PageRoutes pages;

    SignIn(Login login, PageRoutes pages) {
        this.login = login;
        this.pages = pages;
    }

    /** Whether the address is one of the forms, which also take POST. */
    static boolean handles(String path) {
        return path.equals("/sign-in") || path.equals("/sign-out");
    }

    /** The session of the request's cookie, or {@code null} if the visitor is not signed in. */
    Sessions.Session session(HttpExchange exchange) {
        return login.sessions().check(cookie(exchange));
    }

    /** Sends a session in use a new cookie with a later expiry, at most every so often. */
    void renew(HttpExchange exchange, Sessions.Session session) {
        if (session != null && login.sessions().renew(session)) {
            String renewed = login.sessions().issue(session.user(), session.keep());
            if (renewed != null) {
                setCookie(exchange, renewed, Sessions.maxAge(session.keep()));
            }
        }
    }

    /** Serves an address for which {@link #handles} holds. */
    void serve(Request request) throws IOException {
        if (request.path().equals("/sign-out")) {
            signOut(request.exchange());
        } else {
            signIn(request.exchange(), request.ui(), request.members());
        }
    }

    /** The sign-in form, and signing in with it. */
    private void signIn(HttpExchange exchange, Ui ui, boolean signedIn) throws IOException {
        String next = safeNext(Request.parameter(exchange.getRequestURI().getRawQuery(), "next"));
        if (!exchange.getRequestMethod().equals("POST")) {
            if (signedIn) {
                Responses.redirect(exchange, next);
            } else {
                signInPage(exchange, ui, 200, "", next, false, "");
            }
            return;
        }
        if (!sameOrigin(exchange)) {
            Responses.empty(exchange, 403);
            return;
        }
        Map<String, String> form = form(exchange);
        if (form == null) {
            Responses.empty(exchange, 400);
            return;
        }
        next = safeNext(form.get("next"));
        String name = form.getOrDefault("name", "").strip();
        char[] password = form.getOrDefault("password", "").toCharArray();
        boolean keep = form.containsKey("keep");
        // Shown again in the form, unless it cannot be anyone's name anyway.
        String shown = name.length() > 64 ? "" : name;
        try {
            String address = address(exchange);
            switch (login.signIn(address, name, password)) {
                case Login.Result.SignedIn signedInAs -> {
                    String cookie = login.sessions().issue(signedInAs.user(), keep);
                    if (cookie == null) {
                        // Removed from the users file while the password was checked.
                        signInPage(exchange, ui, 200, shown, next, keep, ui.t("signin.wrong"));
                        return;
                    }
                    setCookie(exchange, cookie, Sessions.maxAge(keep));
                    Responses.redirect(exchange, next);
                }
                case Login.Result.Wrong wrong -> {
                    // The address, for tools such as fail2ban; not the name, which may be a password typed there.
                    System.err.println("sign-in: wrong name or password from " + printable(address));
                    signInPage(exchange, ui, 200, shown, next, keep, ui.t("signin.wrong"));
                }
                case Login.Result.TooMany tooMany -> {
                    System.err.println("sign-in: too many attempts from " + printable(address));
                    exchange.getResponseHeaders()
                            .set(
                                    "Retry-After",
                                    String.valueOf(tooMany.retryAfter().toSeconds() + 1));
                    signInPage(exchange, ui, 429, shown, next, keep, ui.t("signin.too_many"));
                }
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void signInPage(
            HttpExchange exchange, Ui ui, int status, String name, String next, boolean keep, String error) {
        pages.render(
                exchange, ui, status, "signin.jte", Map.of("name", name, "next", next, "keep", keep, "error", error));
    }

    private void signOut(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            Responses.empty(exchange, 405);
            return;
        }
        if (!sameOrigin(exchange)) {
            Responses.empty(exchange, 403);
            return;
        }
        setCookie(exchange, "", Duration.ZERO);
        Responses.redirect(exchange, "/");
    }

    /**
     * Whether a form was sent from this site, against forms elsewhere that post to it. Browsers send
     * {@code Origin} with every form; one without it is let through only if nothing says it came from elsewhere.
     */
    private boolean sameOrigin(HttpExchange exchange) {
        var headers = exchange.getRequestHeaders();
        String origin = headers.getFirst("Origin");
        if (origin == null) {
            String fetchSite = headers.getFirst("Sec-Fetch-Site");
            return fetchSite == null || fetchSite.equals("same-origin") || fetchSite.equals("none");
        }
        String host = headers.getFirst("Host");
        if (login.behindProxy() && headers.getFirst("X-Forwarded-Host") != null) {
            host = headers.getFirst("X-Forwarded-Host").split(",")[0].strip();
        }
        try {
            String authority = URI.create(origin).getRawAuthority();
            return host != null && authority != null && authority.equalsIgnoreCase(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The visitor's address; behind a proxy, the one the proxy added last, as earlier ones can be made up. */
    private String address(HttpExchange exchange) {
        String address = exchange.getRemoteAddress().getAddress().getHostAddress();
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (login.behindProxy() && forwarded != null) {
            String[] addresses = forwarded.split(",");
            address = addresses[addresses.length - 1].strip();
        }
        return address.length() > 64 ? address.substring(0, 64) : address;
    }

    /** An address as a log may show it: a forwarded one is whatever the header held, so only address characters. */
    static String printable(String address) {
        return address.replaceAll("[^0-9A-Za-z.:%_-]", "?");
    }

    /** Whether the visitor uses HTTPS, which only a proxy in front of this server can tell. */
    private boolean secure(HttpExchange exchange) {
        String proto = exchange.getRequestHeaders().getFirst("X-Forwarded-Proto");
        return login.behindProxy() && proto != null && proto.strip().equalsIgnoreCase("https");
    }

    /** The session cookie's value, or {@code null}. */
    private static String cookie(HttpExchange exchange) {
        for (String header : exchange.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String pair : header.split(";")) {
                String trimmed = pair.strip();
                if (trimmed.startsWith(COOKIE + "=")) {
                    return trimmed.substring(COOKIE.length() + 1);
                }
            }
        }
        return null;
    }

    /**
     * Sets the session cookie: for this site only, never readable by scripts, and not sent with requests from
     * other sites except for following a link.
     *
     * @param maxAge how long to keep it, zero to remove it, {@code null} until the browser closes
     */
    private void setCookie(HttpExchange exchange, String value, Duration maxAge) {
        var cookie = new StringBuilder(COOKIE).append('=').append(value).append("; Path=/; HttpOnly; SameSite=Lax");
        if (secure(exchange)) {
            cookie.append("; Secure");
        }
        if (maxAge != null) {
            cookie.append("; Max-Age=").append(maxAge.toSeconds());
        }
        exchange.getResponseHeaders().add("Set-Cookie", cookie.toString());
    }

    /** Where to go after signing in: a path on this site, else the home page. */
    static String safeNext(String next) {
        if (next == null
                || !next.startsWith("/")
                || next.startsWith("//")
                || next.startsWith("/\\")
                || next.startsWith("/sign-")
                || next.length() > 2000
                || next.chars().anyMatch(c -> c <= ' ' || c == 0x7f)) {
            return "/";
        }
        return next;
    }

    /** The fields of a posted form, or {@code null} if it is not one or too large. */
    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.startsWith("application/x-www-form-urlencoded")) {
            return null;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_FORM_BYTES + 1);
        if (body.length > MAX_FORM_BYTES) {
            return null;
        }
        Map<String, String> fields = new HashMap<>();
        try {
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    fields.putIfAbsent(
                            URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return fields;
    }
}
