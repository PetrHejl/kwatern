package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.Utf8ByteOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Table;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.auth.Sessions;
import me.hejl.kwatern.view.Views;

/** Serves the site: routes requests, renders templates and sets response headers. */
public final class WebServer {

    private static final String STATIC = "/me/hejl/kwatern/static/";
    // Map tiles are the only content from elsewhere, and only when maps are on.
    private static final String SECURITY_POLICY = "default-src 'self'; img-src 'self' data:%s; style-src 'self';"
            + " script-src 'self'; frame-ancestors 'none'";

    private static final Set<String> MAP_PAGES = Set.of("map.jte", "personmap.jte", "place.jte");

    private static final String COOKIE = "kwatern-session";
    // Larger sign-in forms are refused unread.
    private static final int MAX_FORM_BYTES = 4096;
    // A request must arrive within this many seconds, so that clients sending it slowly cannot hold connections
    // open for good. Answers are not limited: a large file over a slow link takes long.
    static final String MAX_REQUEST_SECONDS = "30";
    private static final String MAX_REQUEST_PROPERTY = "sun.net.httpserver.maxReqTime";

    // Replaced when the export is reloaded; each request reads it once, so it never mixes two versions.
    private volatile Sites current;
    private final Login login;
    private final String securityPolicy;
    private final TemplateEngine templates = TemplateEngine.createPrecompiled(ContentType.Html);
    private HttpServer server;

    /** A site without a login. */
    public WebServer(Site site) {
        this(Sites.open(site), null);
    }

    /**
     * @param login how members sign in, or {@code null} for a site without a login, which then serves
     *              {@link Sites#everyone()} only
     */
    public WebServer(Sites sites, Login login) {
        if (login == null ? sites.everyone() == null : sites.members() == null) {
            throw new IllegalArgumentException("the sites do not match the login");
        }
        this.current = sites;
        this.login = login;
        Site.MapOptions map = sites.any().options().map();
        this.securityPolicy = SECURITY_POLICY.formatted(map.enabled() ? " " + map.origin() : "");
    }

    /** Serves another version of the site from the next request on; its options must be the same. */
    public void replace(Site site) {
        replace(Sites.open(site));
    }

    /** Serves other versions of the sites from the next request on; they must be for the same login. */
    public void replace(Sites sites) {
        this.current = sites;
    }

    /** Starts serving; returns the port, which is chosen by the system if {@code port} is 0. */
    public int start(String host, int port) throws IOException {
        // The JDK's server reads it when first used, and waits forever without it; an operator's own value stays.
        if (System.getProperty(MAX_REQUEST_PROPERTY) == null) {
            System.setProperty(MAX_REQUEST_PROPERTY, MAX_REQUEST_SECONDS);
        }
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Sites sites = current;
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            boolean form = login != null && (path.equals("/sign-in") || path.equals("/sign-out"));
            if (!method.equals("GET") && !method.equals("HEAD") && !(form && method.equals("POST"))) {
                exchange.getResponseHeaders().set("Allow", form ? "GET, HEAD, POST" : "GET, HEAD");
                send(exchange, 405, "text/plain; charset=utf-8", new byte[0]);
                return;
            }
            if (path.equals("/health")) {
                send(exchange, 200, "text/plain; charset=utf-8", "ok\n".getBytes(StandardCharsets.UTF_8));
                return;
            }
            // The sign-in page needs the stylesheet and fonts, which hold nothing of the tree.
            if (path.startsWith("/static/")) {
                serveStatic(
                        exchange,
                        path.substring("/static/".length()),
                        exchange.getRequestURI().getRawQuery());
                return;
            }
            Sessions.Session session = login == null ? null : login.sessions().check(cookie(exchange));
            String here = exchange.getRequestURI().getRawPath()
                    + (exchange.getRequestURI().getRawQuery() == null
                            ? ""
                            : "?" + exchange.getRequestURI().getRawQuery());
            Viewer viewer = login == null
                    ? Viewer.OPEN
                    : new Viewer(login.access(), session == null ? null : session.user(), here);
            Site site = session != null ? sites.members() : sites.everyone();
            if (login != null) {
                // The same address shows a different page to a member.
                exchange.getResponseHeaders().set("Vary", "Accept-Language, Cookie");
                if (login.access() == Login.Access.PRIVATE) {
                    exchange.getResponseHeaders().set("X-Robots-Tag", "noindex, nofollow");
                }
                String renewed = session != null && login.sessions().renew(session)
                        ? login.sessions().issue(session.user(), session.keep())
                        : null;
                if (renewed != null) {
                    setCookie(exchange, renewed, Sessions.maxAge(session.keep()));
                }
            }
            if (form) {
                Ui ui = ui(site != null ? site : sites.members(), exchange).with(viewer);
                if (path.equals("/sign-out")) {
                    signOut(exchange);
                } else {
                    signIn(exchange, ui, session != null);
                }
                return;
            }
            if (site == null) {
                // Every address alike, whether it exists or not, so that none can be told apart.
                redirect(exchange, viewer.signInUrl());
                return;
            }
            if (path.startsWith("/portrait/") || path.startsWith("/thumbnail/")) {
                serveImage(site, exchange, path, session != null);
                return;
            }
            if (path.matches("/media/[^/]+/(image|original)")) {
                serveMediaFile(site, exchange, path, session != null);
                return;
            }
            Ui ui = ui(site, exchange).with(viewer);
            try {
                route(site, exchange, path, ui);
            } catch (RuntimeException e) {
                System.err.print(errorLog(path, e));
                page(exchange, ui, 500, "error.jte", Map.of("message", ui.t("error.server")));
            }
        }
    }

    /**
     * What to log about an error while serving a page: the kind of page, and where the error happened. The rest of
     * the path and the exceptions' messages are left out, as they may hold data of the tree, such as a surname or
     * a date, which logs must not.
     */
    static String errorLog(String path, Throwable error) {
        String[] parts = path.split("/", 3);
        var log = new StringBuilder("Error serving /")
                .append(parts.length > 1 ? parts[1] : "")
                .append(parts.length > 2 ? "/..." : "")
                .append(": ");
        Throwable t = error;
        // A few causes are enough, and a cycle of them must not loop.
        for (int depth = 0; t != null && depth < 5; depth++, t = t.getCause()) {
            log.append(depth == 0 ? "" : "Caused by: ")
                    .append(t.getClass().getName())
                    .append('\n');
            for (StackTraceElement frame : t.getStackTrace()) {
                log.append("\tat ").append(frame).append('\n');
            }
        }
        return log.toString();
    }

    private static Ui ui(Site site, HttpExchange exchange) {
        return site.ui(
                queryParameter(exchange.getRequestURI().getRawQuery(), "lang"),
                exchange.getRequestHeaders().getFirst("Accept-Language"));
    }

    /** The sign-in form, and signing in with it. */
    private void signIn(HttpExchange exchange, Ui ui, boolean signedIn) throws IOException {
        String next = safeNext(queryParameter(exchange.getRequestURI().getRawQuery(), "next"));
        if (!exchange.getRequestMethod().equals("POST")) {
            if (signedIn) {
                redirect(exchange, next);
            } else {
                signInPage(exchange, ui, 200, "", next, false, "");
            }
            return;
        }
        if (!sameOrigin(exchange)) {
            send(exchange, 403, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        Map<String, String> form = form(exchange);
        if (form == null) {
            send(exchange, 400, "text/plain; charset=utf-8", new byte[0]);
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
                    redirect(exchange, next);
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
        page(exchange, ui, status, "signin.jte", Map.of("name", name, "next", next, "keep", keep, "error", error));
    }

    private void signOut(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            send(exchange, 405, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        if (!sameOrigin(exchange)) {
            send(exchange, 403, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        setCookie(exchange, "", Duration.ZERO);
        redirect(exchange, "/");
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

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, 303, "text/plain; charset=utf-8", new byte[0]);
    }

    private void route(Site site, HttpExchange exchange, String path, Ui ui) throws IOException {
        var views = new Views(site.data(), ui, site.images());
        GrampsDatabase db = site.data().database();
        String[] parts = path.substring(1).split("/", 2);
        String key = parts.length > 1 ? parts[1] : "";
        switch (parts[0]) {
            case "" -> page(exchange, ui, 200, "home.jte", Map.of("page", views.home()));
            case "surnames" -> page(exchange, ui, 200, "surnames.jte", Map.of("page", views.surnames()));
            case "places" -> page(exchange, ui, 200, "places.jte", Map.of("page", views.places()));
            case "sources" -> page(exchange, ui, 200, "sources.jte", Map.of("page", views.sources()));
            case "surname" -> {
                var surname = views.surname(key);
                if (surname == null) {
                    notFound(exchange, ui);
                } else {
                    page(exchange, ui, 200, "surname.jte", Map.of("page", surname));
                }
            }
            case "person" -> personPage(site, exchange, ui, views, key);
            case "family" ->
                find(db.families(), key)
                        .ifPresentOrElse(
                                f -> page(exchange, ui, 200, "family.jte", Map.of("page", views.family(f))),
                                () -> notFound(exchange, ui));
            case "place" ->
                find(db.places(), key)
                        .ifPresentOrElse(
                                p -> page(exchange, ui, 200, "place.jte", Map.of("page", views.place(p))),
                                () -> notFound(exchange, ui));
            case "source" ->
                find(db.sources(), key)
                        .ifPresentOrElse(
                                s -> page(exchange, ui, 200, "source.jte", Map.of("page", views.source(s))),
                                () -> notFound(exchange, ui));
            case "media" ->
                find(db.media(), key)
                        .ifPresentOrElse(
                                m -> page(exchange, ui, 200, "media.jte", Map.of("page", views.media(m))),
                                () -> notFound(exchange, ui));
            case "photos" -> page(exchange, ui, 200, "photos.jte", Map.of("page", views.photos()));
            case "map" -> {
                if (!ui.hasMap()) {
                    notFound(exchange, ui);
                    return;
                }
                String query = exchange.getRequestURI().getRawQuery();
                String kind = queryParameter(query, "kind");
                String period = queryParameter(query, "period");
                page(
                        exchange,
                        ui,
                        200,
                        "map.jte",
                        Map.of("page", views.treeMap(kind == null ? "all" : kind, period == null ? "all" : period)));
            }
            case "search" -> {
                String query = queryParameter(exchange.getRequestURI().getRawQuery(), "q");
                var results = views.search(query == null ? "" : query.strip(), site.search());
                page(exchange, ui, 200, "search.jte", Map.of("page", results));
            }
            default -> notFound(exchange, ui);
        }
    }

    /** A person's overview, ancestor chart or descendant chart: /person/ID, /person/ID/ancestors, ... */
    private void personPage(Site site, HttpExchange exchange, Ui ui, Views views, String key) {
        String[] parts = key.split("/", 2);
        String tab = parts.length > 1 ? parts[1] : "";
        Optional<Person> person = find(site.data().database().people(), parts[0]);
        if (person.isEmpty()) {
            notFound(exchange, ui);
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        switch (tab) {
            case "" -> {
                boolean family = !"hide".equals(queryParameter(query, "family"));
                page(exchange, ui, 200, "person.jte", Map.of("page", views.person(person.get(), family)));
            }
            case "ancestors" -> {
                int generations = number(queryParameter(query, "generations"), 3, 5, Views.ANCESTOR_GENERATIONS);
                boolean fan = "fan".equals(queryParameter(query, "view"));
                page(
                        exchange,
                        ui,
                        200,
                        "ancestors.jte",
                        Map.of("page", views.ancestors(person.get(), generations, fan)));
            }
            case "descendants" -> {
                int generations = number(queryParameter(query, "generations"), 2, 4, Views.DESCENDANT_GENERATIONS);
                page(
                        exchange,
                        ui,
                        200,
                        "descendants.jte",
                        Map.of("page", views.descendants(person.get(), generations)));
            }
            case "map" -> {
                if (!ui.hasMap() || site.data().isLiving(person.get().handle())) {
                    notFound(exchange, ui);
                    return;
                }
                boolean family = "show".equals(queryParameter(query, "family"));
                page(exchange, ui, 200, "personmap.jte", Map.of("page", views.personMap(person.get(), family)));
            }
            default -> notFound(exchange, ui);
        }
    }

    /** A number from a query parameter within limits, or the default if it is missing or not a number. */
    private static int number(String value, int min, int max, int fallback) {
        try {
            return value == null ? fallback : Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Looks an object up by Gramps ID, falling back to its handle. */
    private static <T extends PrimaryObject> Optional<T> find(Table<T> table, String key) {
        if (key.isEmpty() || key.contains("/")) {
            return Optional.empty();
        }
        return table.byId(key).or(() -> table.get(key));
    }

    private void notFound(HttpExchange exchange, Ui ui) {
        page(exchange, ui, 404, "error.jte", Map.of("message", ui.t("error.not_found")));
    }

    private void page(HttpExchange exchange, Ui ui, int status, String template, Map<String, Object> params) {
        var output = new Utf8ByteOutput();
        Map<String, Object> all = new HashMap<>(params);
        all.put("ui", ui);
        templates.render(template, all, output);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Language", ui.language());
        headers.set("Vary", login == null ? "Accept-Language" : "Accept-Language, Cookie");
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
            exchange.sendResponseHeaders(status, head ? -1 : output.getContentLength());
            if (!head) {
                try (OutputStream out = exchange.getResponseBody()) {
                    output.writeTo(out);
                }
            }
        } catch (IOException e) {
            // the client went away
        }
    }

    /** Pictures of people and thumbnails of media, made from published media only. */
    private void serveImage(Site site, HttpExchange exchange, String path, boolean members) throws IOException {
        GrampsDatabase db = site.data().database();
        MediaImages images = site.images();
        String[] parts = path.substring(1).split("/", 2);
        Optional<MediaImages.Jpeg> jpeg = parts[0].equals("portrait")
                ? find(db.people(), parts[1])
                        .filter(p -> !site.data().isLiving(p.handle()))
                        .flatMap(images::portraitImage)
                : find(db.media(), parts[1]).flatMap(images::thumbnail);
        sendJpeg(exchange, jpeg, members);
    }

    /**
     * Images of the public view may be cached anywhere for an hour; those of the members' view only by the
     * browser, which checks with the server each time, so that it stops showing them once signed out.
     */
    private static String imageCaching(boolean members) {
        return members ? "private, no-cache" : "public, max-age=3600";
    }

    private static void sendJpeg(HttpExchange exchange, Optional<MediaImages.Jpeg> jpeg, boolean members)
            throws IOException {
        if (jpeg.isEmpty()) {
            send(exchange, 404, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        String etag = jpeg.get().etag();
        var headers = exchange.getResponseHeaders();
        headers.set("ETag", etag);
        headers.set("Cache-Control", imageCaching(members));
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }
        send(exchange, 200, "image/jpeg", jpeg.get().data());
    }

    /** The larger image of a media page, or the file itself. */
    private void serveMediaFile(Site site, HttpExchange exchange, String path, boolean members) throws IOException {
        String[] parts = path.substring(1).split("/");
        Optional<Media> media = find(site.data().database().media(), parts[1]);
        if (parts[2].equals("image")) {
            sendJpeg(exchange, media.flatMap(site.images()::display), members);
            return;
        }
        Optional<MediaImages.Original> original = media.flatMap(site.images()::original);
        if (original.isEmpty()) {
            send(exchange, 404, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", original.get().contentType());
        headers.set("X-Content-Type-Options", "nosniff");
        // Shown by the browser, never run: a file is not part of the site, even if it contains scripts.
        headers.set("Content-Security-Policy", "default-src 'none'; img-src 'self'; sandbox");
        headers.set("Content-Disposition", "inline");
        headers.set("Cache-Control", imageCaching(members));
        boolean head = exchange.getRequestMethod().equals("HEAD");
        long size = Files.size(original.get().file());
        exchange.sendResponseHeaders(200, head ? -1 : size == 0 ? -1 : size);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(original.get().file(), out);
            }
        }
    }

    private void serveStatic(HttpExchange exchange, String name, String query) throws IOException {
        if (!name.matches("((fonts|leaflet)/)?[a-z0-9-]+\\.(css|js|svg|png|woff2)")) {
            send(exchange, 404, "text/plain; charset=utf-8", new byte[0]);
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream(STATIC + name)) {
            if (in == null) {
                send(exchange, 404, "text/plain; charset=utf-8", new byte[0]);
                return;
            }
            // Font files never change under the same name, nor does the stylesheet under its versioned URL; other
            // requests for it are checked with the server each time, so a new build shows at once.
            boolean fixed = name.endsWith(".woff2") || (Urls.stylesheet().equals("/static/" + name + "?" + query));
            exchange.getResponseHeaders()
                    .set("Cache-Control", fixed ? "public, max-age=31536000, immutable" : "no-cache");
            send(exchange, 200, contentType(name), in.readAllBytes());
        }
    }

    private static String contentType(String name) {
        return switch (name.substring(name.lastIndexOf('.') + 1)) {
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "woff2" -> "font/woff2";
            default -> "image/png";
        };
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
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

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
