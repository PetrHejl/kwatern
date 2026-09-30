package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.auth.Sessions;

/**
 * Serves the site: takes each request to the version it is served from, finds out who asks, and hands it to the
 * sign-in forms ({@link SignIn}), the program's own files ({@link StaticFiles}), the images and files of the tree
 * ({@link MediaFiles}) or its pages ({@link PageRoutes}). Every response goes through {@link Responses}.
 */
public final class WebServer {

    // A request must arrive within this many seconds, so that clients sending it slowly cannot hold connections
    // open for good. Answers are not limited: a large file over a slow link takes long.
    static final String MAX_REQUEST_SECONDS = "30";
    private static final String MAX_REQUEST_PROPERTY = "sun.net.httpserver.maxReqTime";

    // Replaced when the export is reloaded; each request reads it once, so it never mixes two versions.
    private volatile Version current;
    private final Login login;
    private final Responses responses;
    private final PageRoutes pages;
    // Null for a site without a login.
    private final SignIn signIn;
    private HttpServer server;

    /** A site without a login. */
    public WebServer(Site site) {
        this(Version.of(Sites.open(site)), null);
    }

    /**
     * @param login how members sign in, or {@code null} for a site without a login, which then serves
     *              {@link Sites#everyone()} only
     */
    public WebServer(Version version, Login login) {
        Sites sites = version.sites();
        if (login == null ? sites.everyone() == null : !sites.hasMembers()) {
            throw new IllegalArgumentException("the sites do not match the login");
        }
        this.current = version;
        this.login = login;
        this.responses = new Responses(login, sites.widest().options().map());
        this.pages = new PageRoutes(responses);
        this.signIn = login == null ? null : new SignIn(login, pages);
    }

    /** Serves another version of the site from the next request on; its options must be the same. */
    public void replace(Site site) {
        replace(Version.of(Sites.open(site)));
    }

    /**
     * Serves another version from the next request on; it must be for the same login and options. The previous
     * one is cleaned up once the requests still using it have ended.
     */
    public void replace(Version version) {
        Version previous = current;
        current = version;
        previous.replace();
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
        // A version cleaned up between reading it and entering it has just been replaced: take the new one.
        Version version = current;
        while (!version.enter()) {
            version = current;
        }
        try (exchange) {
            try {
                serve(exchange, version.sites());
            } catch (RuntimeException e) {
                // Pages answer with an error page of their own; this is for images, files and signing in, whose
                // errors the JDK's server would only answer by closing the connection, without a word in the log.
                System.err.print(errorLog(exchange.getRequestURI().getPath(), e));
                if (exchange.getResponseCode() == -1) {
                    Responses.empty(exchange, 500);
                }
            }
        } finally {
            version.leave();
        }
    }

    private void serve(HttpExchange exchange, Sites sites) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        boolean form = signIn != null && SignIn.handles(path);
        if (!method.equals("GET") && !method.equals("HEAD") && !(form && method.equals("POST"))) {
            exchange.getResponseHeaders().set("Allow", form ? "GET, HEAD, POST" : "GET, HEAD");
            Responses.empty(exchange, 405);
            return;
        }
        if (path.equals("/health")) {
            Responses.send(exchange, 200, Responses.TEXT, "ok\n".getBytes(StandardCharsets.UTF_8));
            return;
        }
        // The sign-in page needs the stylesheet and fonts, which hold nothing of the tree.
        if (path.startsWith(StaticFiles.PREFIX)) {
            StaticFiles.serve(
                    exchange,
                    path.substring(StaticFiles.PREFIX.length()),
                    exchange.getRequestURI().getRawQuery());
            return;
        }
        Sessions.Session session = signIn == null ? null : signIn.session(exchange);
        String here = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null
                        ? ""
                        : "?" + exchange.getRequestURI().getRawQuery());
        Site site = session != null ? sites.members(session.grants()) : sites.everyone();
        Viewer viewer = login == null
                ? Viewer.OPEN
                : new Viewer(login.access(), session == null ? null : session.user(), site != sites.everyone(), here);
        if (signIn != null) {
            responses.withLogin(exchange);
            signIn.renew(exchange, session);
        }
        if (form) {
            // The sign-in page of a private site shows nothing of the tree, whichever view it is made from.
            signIn.serve(new Request(exchange, site != null ? site : sites.widest(), viewer, session != null));
            return;
        }
        if (site == null) {
            // Every address alike, whether it exists or not, so that none can be told apart.
            Responses.redirect(exchange, viewer.signInUrl());
            return;
        }
        var request = new Request(exchange, site, viewer, session != null);
        if (MediaFiles.handles(path)) {
            MediaFiles.serve(request);
        } else {
            pages.serve(request);
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
}
