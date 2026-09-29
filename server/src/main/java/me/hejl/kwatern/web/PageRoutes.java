package me.hejl.kwatern.web;

import static java.util.Map.entry;

import com.sun.net.httpserver.HttpExchange;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.Utf8ByteOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.model.Table;
import me.hejl.kwatern.view.Views;

/**
 * The pages of the site, by the first part of their address: made by {@link Views}, rendered from the templates
 * and sent by {@link Responses}. Thread-safe.
 */
final class PageRoutes {

    /** One page request as the routes need it. */
    private record Page(Request request, Ui ui, Views views, GrampsDatabase db) {}

    /** Makes and sends a page, given the rest of its address after the first part, such as a person's ID. */
    @FunctionalInterface
    private interface Route {
        void serve(Page page, String key);
    }

    private final Responses responses;
    private final TemplateEngine templates = TemplateEngine.createPrecompiled(ContentType.Html);
    private final Map<String, Route> routes = Map.ofEntries(
            entry("", (p, key) -> send(p, "home.jte", p.views().home())),
            entry("surnames", (p, key) -> send(p, "surnames.jte", p.views().surnames())),
            entry("places", (p, key) -> send(p, "places.jte", p.views().places())),
            entry("sources", (p, key) -> send(p, "sources.jte", p.views().sources())),
            entry("photos", (p, key) -> send(p, "photos.jte", p.views().photos())),
            entry("surname", (p, key) -> {
                var surname = p.views().surname(key);
                if (surname == null) {
                    notFound(p);
                } else {
                    send(p, "surname.jte", surname);
                }
            }),
            entry("person", this::person),
            entry("family", (p, key) -> object(p, p.db().families(), key, "family.jte", p.views()::family)),
            entry("place", (p, key) -> object(p, p.db().places(), key, "place.jte", p.views()::place)),
            entry("source", (p, key) -> object(p, p.db().sources(), key, "source.jte", p.views()::source)),
            entry("media", (p, key) -> object(p, p.db().media(), key, "media.jte", p.views()::media)),
            entry("map", (p, key) -> {
                if (!p.ui().hasMap()) {
                    notFound(p);
                    return;
                }
                String kind = p.request().parameter("kind");
                String period = p.request().parameter("period");
                send(p, "map.jte", p.views().treeMap(kind == null ? "all" : kind, period == null ? "all" : period));
            }),
            entry("search", (p, key) -> {
                String query = p.request().parameter("q");
                send(
                        p,
                        "search.jte",
                        p.views()
                                .search(
                                        query == null ? "" : query.strip(),
                                        p.request().site().search()));
            }));

    PageRoutes(Responses responses) {
        this.responses = responses;
    }

    /** Serves a page, or an error page: "not found", or one that says the server failed, which is logged. */
    void serve(Request request) {
        Site site = request.site();
        Ui ui = request.ui();
        try {
            var page = new Page(
                    request,
                    ui,
                    new Views(site.data(), site.index(), ui, site.images()),
                    site.data().database());
            String[] parts = request.path().substring(1).split("/", 2);
            Route route = routes.get(parts[0]);
            if (route == null) {
                notFound(page);
            } else {
                route.serve(page, parts.length > 1 ? parts[1] : "");
            }
        } catch (RuntimeException e) {
            System.err.print(WebServer.errorLog(request.path(), e));
            render(request.exchange(), ui, 500, "error.jte", Map.of("message", ui.t("error.server")));
        }
    }

    /** Renders a template with the UI and sends it; see {@link Responses#page} for the headers. */
    void render(HttpExchange exchange, Ui ui, int status, String template, Map<String, Object> params) {
        var html = new Utf8ByteOutput();
        Map<String, Object> all = new HashMap<>(params);
        all.put("ui", ui);
        templates.render(template, all, html);
        responses.page(exchange, ui, status, template, html);
    }

    /** A person's overview, ancestor chart or descendant chart: /person/ID, /person/ID/ancestors, ... */
    private void person(Page p, String key) {
        String[] parts = key.split("/", 2);
        String tab = parts.length > 1 ? parts[1] : "";
        Optional<Person> found = Request.find(p.db().people(), parts[0]);
        if (found.isEmpty()) {
            notFound(p);
            return;
        }
        Person person = found.get();
        Request request = p.request();
        switch (tab) {
            case "" -> send(p, "person.jte", p.views().person(person, !"hide".equals(request.parameter("family"))));
            case "ancestors" -> {
                int generations = number(request.parameter("generations"), 3, 5, Views.ANCESTOR_GENERATIONS);
                boolean fan = "fan".equals(request.parameter("view"));
                send(p, "ancestors.jte", p.views().ancestors(person, generations, fan));
            }
            case "descendants" -> {
                int generations = number(request.parameter("generations"), 2, 4, Views.DESCENDANT_GENERATIONS);
                send(p, "descendants.jte", p.views().descendants(person, generations));
            }
            case "map" -> {
                if (!p.ui().hasMap() || request.site().data().isLiving(person.handle())) {
                    notFound(p);
                    return;
                }
                boolean family = "show".equals(request.parameter("family"));
                send(p, "personmap.jte", p.views().personMap(person, family));
            }
            default -> notFound(p);
        }
    }

    /** The page of an object of the address, or "not found". */
    private <T extends PrimaryObject> void object(
            Page p, Table<T> table, String key, String template, Function<T, Object> model) {
        Request.find(table, key).ifPresentOrElse(o -> send(p, template, model.apply(o)), () -> notFound(p));
    }

    /** A number from a query parameter within limits, or the default if it is missing or not a number. */
    private static int number(String value, int min, int max, int fallback) {
        try {
            return value == null ? fallback : Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void send(Page p, String template, Object model) {
        render(p.request().exchange(), p.ui(), 200, template, Map.of("page", model));
    }

    private void notFound(Page p) {
        render(p.request().exchange(), p.ui(), 404, "error.jte", Map.of("message", p.ui().t("error.not_found")));
    }
}
