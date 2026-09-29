package me.hejl.kwatern.web;

import com.sun.net.httpserver.HttpExchange;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Table;

/**
 * One request as the handlers need it.
 *
 * @param site    the view of the site the visitor sees
 * @param members whether the visitor is signed in, and so sees the members' view
 */
record Request(HttpExchange exchange, Site site, Viewer viewer, boolean members) {

    String path() {
        return exchange.getRequestURI().getPath();
    }

    /** A query parameter, decoded, or {@code null}. */
    String parameter(String name) {
        return parameter(exchange.getRequestURI().getRawQuery(), name);
    }

    /**
     * The UI for the page language: the {@code lang} parameter if given, else the browser's preferred language,
     * else the default one.
     */
    Ui ui() {
        return site.ui(parameter("lang"), exchange.getRequestHeaders().getFirst("Accept-Language"))
                .with(viewer);
    }

    static String parameter(String rawQuery, String name) {
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

    /** Looks an object of the address up by Gramps ID, falling back to its handle. */
    static <T extends PrimaryObject> Optional<T> find(Table<T> table, String key) {
        if (key.isEmpty() || key.contains("/")) {
            return Optional.empty();
        }
        return table.byId(key).or(() -> table.get(key));
    }
}
