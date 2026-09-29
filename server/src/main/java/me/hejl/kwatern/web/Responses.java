package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import gg.jte.output.Utf8ByteOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import me.hejl.kwatern.auth.Login;

/**
 * Every response the site sends, and so every header that keeps it safe: the Content Security Policy, the referrer
 * policy, caching and {@code nosniff}. Thread-safe.
 */
final class Responses {

    static final String TEXT = "text/plain; charset=utf-8";

    // Map tiles are the only content from elsewhere, and only when maps are on.
    private static final String SECURITY_POLICY = "default-src 'self'; img-src 'self' data:%s; style-src 'self';"
            + " script-src 'self'; frame-ancestors 'none'";
    // Shown by the browser, never run: a file is not part of the site, even if it contains scripts.
    private static final String FILE_POLICY = "default-src 'none'; img-src 'self'; sandbox";
    private static final Set<String> MAP_PAGES = Set.of("map.jte", "personmap.jte", "place.jte");

    private final Login login;
    private final String securityPolicy;

    /** @param login how members sign in, or {@code null} for a site without a login */
    Responses(Login login, Site.MapOptions map) {
        this.login = login;
        this.securityPolicy = SECURITY_POLICY.formatted(map.enabled() ? " " + map.origin() : "");
    }

    /**
     * Headers that every response of a site with a login has: the same address shows a different page to a
     * member, and a private site is kept out of search engines as a whole.
     */
    void withLogin(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Vary", "Accept-Language, Cookie");
        if (login.access() == Login.Access.PRIVATE) {
            exchange.getResponseHeaders().set("X-Robots-Tag", "noindex, nofollow");
        }
    }

    /** A page rendered from a template; see {@link #withLogin} for what a site with a login adds. */
    void page(HttpExchange exchange, Ui ui, int status, String template, Utf8ByteOutput html) {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Language", ui.language());
        if (login == null) {
            headers.set("Vary", "Accept-Language");
        }
        headers.set("Content-Security-Policy", securityPolicy);
        // Links and resources elsewhere learn nothing about the page, except tile servers on pages with a map:
        // OpenStreetMap refuses tiles without a Referer. They get the site's address, never the page's.
        headers.set("Referrer-Policy", ui.hasMap() && MAP_PAGES.contains(template) ? "strict-origin" : "same-origin");
        // The members' view is kept by no cache, not even the browser's, where it would outlast signing out.
        boolean members = ui.viewer().signedIn();
        headers.set("Cache-Control", members || template.equals("signin.jte") ? "private, no-store" : "no-cache");
        if (members) {
            headers.set("X-Robots-Tag", "noindex, nofollow");
        }
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("X-Content-Type-Options", "nosniff");
        try {
            boolean head = exchange.getRequestMethod().equals("HEAD");
            exchange.sendResponseHeaders(status, head ? -1 : html.getContentLength());
            if (!head) {
                try (OutputStream out = exchange.getResponseBody()) {
                    html.writeTo(out);
                }
            }
        } catch (IOException e) {
            // the client went away
        }
    }

    /**
     * Images of the public view may be cached anywhere for an hour; those of the members' view only by the
     * browser, which checks with the server each time, so that it stops showing them once signed out.
     */
    static void imageCaching(HttpExchange exchange, boolean members) {
        exchange.getResponseHeaders().set("Cache-Control", members ? "private, no-cache" : "public, max-age=3600");
    }

    /** A media file as it is, to be shown by the browser as a document of its own, never run. */
    static void file(HttpExchange exchange, Path file, long size, String contentType, String etag, boolean members)
            throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", FILE_POLICY);
        headers.set("Content-Disposition", "inline");
        imageCaching(exchange, members);
        if (notModified(exchange, etag)) {
            return;
        }
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(200, head ? -1 : size == 0 ? -1 : size);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(file, out);
            }
        }
    }

    /**
     * Sets the tag of a response and answers 304 if the browser already has it; else the response is still to be
     * sent.
     */
    static boolean notModified(HttpExchange exchange, String etag) throws IOException {
        exchange.getResponseHeaders().set("ETag", etag);
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            return true;
        }
        return false;
    }

    static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        empty(exchange, 303);
    }

    /** A response without a body, such as a 404 for an image or a 405. */
    static void empty(HttpExchange exchange, int status) throws IOException {
        send(exchange, status, TEXT, new byte[0]);
    }

    static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length);
        if (!head && body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
