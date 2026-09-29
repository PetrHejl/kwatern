package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The program's own files: the stylesheet, fonts and the map's scripts, which hold nothing of the tree. */
final class StaticFiles {

    static final String PREFIX = "/static/";
    private static final String RESOURCES = "/me/hejl/kwatern/static/";
    // The files that exist, read once: they are part of the program and never change while it runs.
    private static final Map<String, StaticFile> FILES = new ConcurrentHashMap<>();

    /** A file of the program's own, and its tag for HTTP caching. */
    private record StaticFile(byte[] data, String etag) {}

    private StaticFiles() {}

    /** @param query the raw query, which versions the stylesheet */
    static void serve(HttpExchange exchange, String name, String query) throws IOException {
        if (!name.matches("((fonts|leaflet)/)?[a-z0-9-]+\\.(css|js|svg|png|woff2)")) {
            Responses.empty(exchange, 404);
            return;
        }
        StaticFile file = FILES.get(name);
        if (file == null) {
            try (InputStream in = StaticFiles.class.getResourceAsStream(RESOURCES + name)) {
                if (in == null) {
                    // Not kept: any name of the right form would take memory.
                    Responses.empty(exchange, 404);
                    return;
                }
                byte[] data = in.readAllBytes();
                file = new StaticFile(data, "\"" + Urls.hash(data) + "\"");
                FILES.putIfAbsent(name, file);
            }
        }
        // Font files never change under the same name, nor does the stylesheet under its versioned URL; other
        // requests for it are checked with the server each time, so a new build shows at once.
        boolean fixed = name.endsWith(".woff2") || (Urls.stylesheet().equals(PREFIX + name + "?" + query));
        exchange.getResponseHeaders().set("Cache-Control", fixed ? "public, max-age=31536000, immutable" : "no-cache");
        if (Responses.notModified(exchange, file.etag())) {
            return;
        }
        Responses.send(exchange, 200, contentType(name), file.data());
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
}
