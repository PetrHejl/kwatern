package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageBuilder;
import me.hejl.image.ImageDecoders;
import me.hejl.image.jpeg.JpegReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Serves the Gramps example tree and checks the pages over HTTP. */
class WebServerTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static GrampsDatabase full;
    private static PublicDatabase published;
    private static WebServer server;
    private static String base;

    @BeforeAll
    static void start() throws IOException {
        full = GrampsXml.read(Path.of(System.getProperty("gramps.example"))).database();
        published = PrivacyFilter.apply(full, new ProbablyAlive(full, AliveRules.DEFAULTS, 2026));
        // The test data directory holds one photo of the example tree; the other media files are missing.
        Path media = Path.of(System.getProperty("gramps.example")).getParent();
        server = new WebServer(new Site(
                published,
                Site.Options.DEFAULT,
                new MediaImages(published.database(), media, null, ImageDecoders.DEFAULT)));
        base = "http://127.0.0.1:" + server.start("127.0.0.1", 0);
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private static HttpResponse<String> get(String path, String language) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path));
        if (language != null) {
            request.header("Accept-Language", language);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesPages() throws Exception {
        for (String path : new String[] {"/", "/surnames", "/surname/Garner", "/person/I0044", "/family/F0017"}) {
            var response = get(path, null);
            assertEquals(200, response.statusCode(), path);
            assertTrue(
                    response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            assertTrue(response.headers().firstValue("Content-Security-Policy").isPresent());
        }
        String lewis = get("/person/I0044", null).body();
        assertTrue(lewis.contains("Lewis Anderson <b>Garner von Zieliński</b> Sr"), "heading");
        assertTrue(lewis.contains("aged 56"), "summary");
        assertTrue(lewis.contains("class=\"box self\""), "family diagram");
        assertTrue(lewis.contains("gender-colours"), "gender colours on");
        assertEquals(200, get("/static/style.css", null).statusCode());
    }

    @Test
    void servesPlacesSourcesAndSearch() throws Exception {
        String topeka = get("/place/P1592", null).body();
        assertTrue(topeka.contains("<b>Topeka</b>"));
        assertTrue(topeka.contains("City in Shawnee, KS, USA"));
        assertTrue(topeka.matches("(?s).*John Norris</a>\\s+and <a.*"), "both spouses of a marriage");

        String lewis = get("/person/I0044", null).body();
        assertTrue(lewis.contains("href=\"/place/"), "places are links");
        assertTrue(lewis.contains("href=\"/source/"), "sources are links");
        assertTrue(get("/source/S0000", null).body().contains("Birth of Lewis Anderson Garner Sr"));

        String search = get("/search?q=zielinski", null).body();
        assertTrue(search.contains("Phoebe Emily Zieliński"), "accents are ignored");
        String places = get("/search?q=topeka", null).body();
        assertTrue(places.contains("Topeka, Shawnee, KS, USA"));
        assertFalse(places.contains("<aside>"), "results use the full width");
        assertEquals(200, get("/search", null).statusCode());
        String minnesota = get("/place/P0050", null).body();
        assertTrue(minnesota.contains("68 events in 15 places within, 1628–1999"), "area summary");
        assertTrue(minnesota.contains("Places within · 15"), "places within in the main column");
        assertTrue(minnesota.contains("Surnames here"), "surnames of the area");
        assertTrue(minnesota.indexOf(">Worthington<") < minnesota.indexOf(">Duluth<"), "busiest place first");
        assertFalse(topeka.contains("Surnames here"), "a town keeps the simple layout");
        assertTrue(get("/places", null).body().contains(">Topeka</a>"), "places tree");
        assertTrue(get("/sources", null).body().contains("All possible citations"), "sources list");
        assertTrue(topeka.contains("39.0483° N, 95.6780° W"), "coordinates");
        assertTrue(get("/place/P1592", "cs").body().contains("39,0483° s. š."), "coordinates in Czech");
        assertEquals(404, get("/place/nope", null).statusCode());
        assertEquals(
                200, get("/static/fonts/manrope-latin-wght-normal.woff2", null).statusCode());
    }

    @Test
    void logsErrorsWithoutDataOfTheTree() {
        var error = new IllegalStateException(
                "no name for Novák", new NumberFormatException("For input string: \"1850?\""));
        String log = WebServer.errorLog("/surname/Novák", error);
        assertTrue(log.startsWith("Error serving /surname/...: java.lang.IllegalStateException\n"), log);
        assertTrue(log.contains("Caused by: java.lang.NumberFormatException\n"), log);
        assertTrue(log.contains("\tat me.hejl.kwatern.web.WebServerTest.logsErrorsWithoutDataOfTheTree"), log);
        assertFalse(log.contains("Novák") || log.contains("1850"), log);
        assertTrue(WebServer.errorLog("/", error).startsWith("Error serving /: "));
    }

    @Test
    void speaksTheBrowsersLanguage() throws Exception {
        var czech = get("/person/I0044", "cs-CZ,cs;q=0.9,en;q=0.8");
        assertTrue(czech.body().contains("<html lang=\"cs\">"));
        assertTrue(czech.body().contains("Narození"));
        assertTrue(czech.body().contains("21. 6. 1855"));
        assertEquals("cs", czech.headers().firstValue("Content-Language").orElseThrow());
        assertTrue(get("/surnames", "cs").body().contains(">CH</a>"), "Czech alphabet in the index");
        assertTrue(get("/person/I0044?lang=de", "cs").body().contains("<html lang=\"de\">"));
    }

    @Test
    void makesPagesOnlyInLanguagesIcuKnows() throws Exception {
        assertTrue(get("/?lang=fr", null).body().contains("<html lang=\"fr\">"), "ICU's dates, English words");
        assertTrue(get("/?lang=qqq", "cs").body().contains("<html lang=\"cs\">"), "made-up code: the browser's");
        assertTrue(get("/", "qqq, cs;q=0.5").body().contains("<html lang=\"cs\">"), "the next known one");

        // Each language's UI is kept for good, so every code that looks valid must not make a new one.
        var site = new Site(published, Site.Options.DEFAULT);
        Ui english = site.ui(null, null);
        assertSame(english, site.ui("qqq", null));
        assertSame(english, site.ui(null, "qqq"));
        Set<Ui> made = Collections.newSetFromMap(new IdentityHashMap<>());
        for (char a = 'a'; a <= 'z'; a++) {
            for (char b = 'a'; b <= 'z'; b++) {
                made.add(site.ui("" + a + b, null));
                made.add(site.ui(null, "" + a + b));
                for (char c = 'a'; c <= 'z'; c++) {
                    made.add(site.ui("" + a + b + c, null));
                    made.add(site.ui(null, "" + a + b + c));
                }
            }
        }
        // ICU knows about 250 languages; before, each of the 18,252 codes had its own.
        assertTrue(made.size() < 300, made.size() + " UIs");
    }

    @Test
    void followsTheSystemThemeUnlessForced() throws Exception {
        assertFalse(get("/", null).body().contains("data-theme"), "auto: no attribute");
        var dark = new WebServer(new Site(published, new Site.Options("en", false, "dark", Site.MapOptions.OFF, null)));
        int port = dark.start("127.0.0.1", 0);
        try {
            String body = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(body.contains("data-theme=\"dark\""));
            assertFalse(body.contains("gender-colours"));
        } finally {
            dark.stop();
        }
    }

    @Test
    void escapesAndRejectsBadRequests() throws Exception {
        assertEquals(404, get("/person/nope", null).statusCode());
        assertEquals(404, get("/surname/%3Cscript%3E", null).statusCode());
        assertEquals(404, get("/static/..%2Fstyle.css", null).statusCode());
        var post = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, post.statusCode());
    }

    @Test
    void showsOnlyKnownMapFilters() throws Exception {
        String map = get("/map?kind=births%26period%3D1800s&period=%23x", null).body();
        assertTrue(map.contains("<b>All events</b>") && map.contains("<b>Any time</b>"), "the defaults");
        assertFalse(map.contains("#x") || map.contains("%23x") || map.contains("1800s&amp;period"));
    }

    @Test
    void namesLanguagesInThePageLanguage() throws Exception {
        // Ioannina has a Greek and an English name: the one not in the heading is listed with its language.
        assertTrue(get("/place/P0437", null).body().contains("<dd>Greek</dd>"));
        assertTrue(get("/place/P0437", "cs").body().contains("<dd>angličtina</dd>"));
    }

    @Test
    void showsLivingPeopleWithoutDetails() throws Exception {
        String handle = published.living().iterator().next();
        Person original = full.people().get(handle).orElseThrow();
        String body = get("/person/" + original.id(), null).body();
        assertTrue(body.contains("<h1 class=\"living\">Living</h1>"));
        assertFalse(body.contains("class=\"timeline\""), "no events");
        String given = original.primaryName().first();
        if (given != null && given.length() > 3) {
            assertFalse(body.contains(given), "no given name");
        }
    }

    @Test
    void servesPortraitsAndThumbnails() throws Exception {
        // Someone whose first media reference is a face in the photo that is there.
        Person person = published.database().people().all().stream()
                .filter(p -> !p.media().isEmpty() && p.media().getFirst().region() != null)
                .findFirst()
                .orElseThrow();
        assertTrue(get("/person/" + person.id(), null).body().contains("src=\"/portrait/" + person.id() + "\""));

        var portrait = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/portrait/" + person.id()))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, portrait.statusCode());
        assertEquals("image/jpeg", portrait.headers().firstValue("Content-Type").orElseThrow());
        ImageBuilder builder = new ImageBuilder();
        new JpegReader(new ByteArrayInputStream(portrait.body())).decode(builder, 8);
        assertEquals(builder.image().width(), builder.image().height(), "square");

        String etag = portrait.headers().firstValue("ETag").orElseThrow();
        var again = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/portrait/" + person.id()))
                        .header("If-None-Match", etag)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(304, again.statusCode());

        String media = published
                .database()
                .media()
                .get(person.media().getFirst().media())
                .orElseThrow()
                .id();
        var thumbnail = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/thumbnail/" + media)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, thumbnail.statusCode());
        builder = new ImageBuilder();
        new JpegReader(new ByteArrayInputStream(thumbnail.body())).decode(builder, 8);
        assertEquals(320, Math.max(builder.image().width(), builder.image().height()));

        // Media whose file is missing: initials on the page, no image.
        Person without = published.database().people().all().stream()
                .filter(p -> !p.media().isEmpty())
                .filter(p -> !p.media()
                        .getFirst()
                        .media()
                        .equals(person.media().getFirst().media()))
                .findFirst()
                .orElseThrow();
        assertFalse(get("/person/" + without.id(), null).body().contains("/portrait/"));
        assertEquals(404, get("/portrait/" + without.id(), null).statusCode());
        assertEquals(404, get("/thumbnail/O9999", null).statusCode());
        assertEquals(404, get("/thumbnail/..%2F..%2Fetc%2Fpasswd", null).statusCode());
    }

    @Test
    void servesMediaPagesAndPhotos() throws Exception {
        // The photo with two marked people: numbered regions over the image, and the people below.
        String photo = get("/media/O0010", null).body();
        assertTrue(photo.contains("In this photo · 2"), "marked people");
        assertTrue(photo.contains("<svg class=\"regions\" viewBox=\"0 0 500 347\""), "regions in image pixels");
        assertTrue(photo.contains("href=\"/person/I0044\""), "links to the marked people");
        assertTrue(photo.contains("href=\"/media/O0010/original\""), "full size");
        assertTrue(photo.contains("Photo (JPEG)"));
        assertFalse(photo.contains("style="), "no inline styles, which the Content Security Policy would block");

        var image = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/media/O0010/image")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        ImageBuilder builder = new ImageBuilder();
        new JpegReader(new ByteArrayInputStream(image.body())).decode(builder, 8);
        assertEquals(500, builder.image().width(), "smaller images keep their size");

        var original = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/media/O0010/original"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, original.statusCode());
        assertEquals("image/jpeg", original.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(original.headers()
                .firstValue("Content-Security-Policy")
                .orElseThrow()
                .contains("sandbox"));
        assertEquals(27205, original.body().length);

        // A PNG scan: shown like a photo, the original served as PNG.
        assertTrue(get("/media/O0000", null).body().contains("href=\"/media/O0000/original\""));
        assertEquals(200, get("/media/O0000/image", null).statusCode());
        assertEquals(200, get("/thumbnail/O0000", null).statusCode());
        assertEquals(
                "image/png",
                get("/media/O0000/original", null)
                        .headers()
                        .firstValue("Content-Type")
                        .orElseThrow());

        // A media object whose file is missing: the page, but no image and no file.
        String missing = get("/media/O0011", null).body();
        assertTrue(missing.contains("This file cannot be shown here."));
        assertFalse(missing.contains("/media/O0011/original"));
        assertEquals(404, get("/media/O0011/original", null).statusCode());
        assertEquals(404, get("/media/O0011/image", null).statusCode());
        assertEquals(404, get("/media/O9999", null).statusCode());

        // Photos on the person's page, and the listing by decade.
        String lewis = get("/person/I0044", null).body();
        assertTrue(lewis.contains("href=\"/media/O0010\""), "photos card");
        assertTrue(lewis.contains("marked in this photo"));
        assertTrue(lewis.contains("href=\"/photos\""), "Photos in the header");
        String photos = get("/photos", null).body();
        assertTrue(photos.contains("1890–1899 · 1"), "decade");
        assertTrue(photos.contains("Undated · 6"), "undated last");
        assertTrue(photos.indexOf("1890–1899") < photos.indexOf("Undated"));
        assertTrue(get("/photos", "cs").body().contains("7 fotografií a dokumentů"), "Czech plural");
    }

    @Test
    void versionsTheStylesheet() throws Exception {
        String page = get("/", null).body();
        var matcher = Pattern.compile("href=\"(/static/style\\.css\\?v=[0-9a-f]{12})\"")
                .matcher(page);
        assertTrue(matcher.find(), "versioned stylesheet link");
        var versioned = get(matcher.group(1), null);
        assertEquals(200, versioned.statusCode());
        assertTrue(versioned.headers().firstValue("Cache-Control").orElseThrow().contains("immutable"));
        // An old version, or none, is checked with the server every time, so a new build shows at once.
        assertEquals(
                "no-cache",
                get("/static/style.css", null)
                        .headers()
                        .firstValue("Cache-Control")
                        .orElseThrow());
        assertEquals(
                "no-cache",
                get("/static/style.css?v=000000000000", null)
                        .headers()
                        .firstValue("Cache-Control")
                        .orElseThrow());
    }

    @Test
    void servesPersonTabsAndCharts() throws Exception {
        String overview = get("/person/I0046", null).body();
        assertTrue(overview.contains("href=\"/person/I0046/ancestors\""), "tabs");
        assertTrue(overview.contains("aria-current=\"page\">Overview"), "active tab");

        String tree = get("/person/I0046/ancestors", null).body();
        assertTrue(tree.contains("Ancestors · 4 generations"));
        assertTrue(tree.contains("<svg class=\"tree\""));
        assertTrue(tree.contains(">Lewis Anderson Garner Sr</text>"), "a parent in the chart");
        assertTrue(tree.contains(">George Шестаков</text>"), "a great-grandparent");
        assertTrue(tree.contains("class=\"ancestor-list\""), "list for small screens");
        assertFalse(tree.contains("style="), "no inline styles");

        String fan = get("/person/I0046/ancestors?view=fan&generations=5", null).body();
        assertTrue(fan.contains("<svg class=\"fan\""));
        assertTrue(
                fan.contains("href=\"/person/I0046/ancestors?generations=5\""), "back to the tree, same generations");
        assertEquals(200, get("/person/I0046/ancestors?generations=99", null).statusCode(), "limited, not an error");
        assertEquals(200, get("/person/I0046/ancestors?generations=x", null).statusCode());

        String descendants = get("/person/I0044/descendants", null).body();
        assertTrue(descendants.contains("Descendants · 8 children"));
        assertTrue(descendants.contains("href=\"/person/I0046\""), "a child");
        assertTrue(get("/person/I0044/descendants", "cs").body().contains("Potomci · 8 dětí"), "Czech plural");
        assertEquals(404, get("/person/I0044/nothing", null).statusCode());
    }

    @Test
    void servesMaps() throws Exception {
        var home = get("/", null);
        assertTrue(home.body().contains("href=\"/map\""), "Map in the header");
        assertTrue(home.headers()
                .firstValue("Content-Security-Policy")
                .orElseThrow()
                .contains("https://tile.openstreetmap.org"));
        assertFalse(home.body().contains("leaflet.js"), "map scripts only where there is a map");

        String map = get("/map", null).body();
        assertTrue(map.contains("data-kind=\"dots\""));
        assertTrue(map.contains("Topeka"), "a place with coordinates");
        assertTrue(map.contains("/static/leaflet/leaflet.js"));
        assertTrue(get("/map?kind=births&period=1800s", null).body().contains("<b>Births</b>"));
        assertEquals(200, get("/static/leaflet/leaflet.js", null).statusCode());
        assertEquals(200, get("/static/map.js", null).statusCode());

        String topeka = get("/place/P1592", null).body();
        assertTrue(topeka.contains("class=\"map small\""), "place page map");

        String lewis = get("/person/I0044/map", null).body();
        assertTrue(lewis.contains("aria-current=\"page\">Map"), "Map tab");
        assertTrue(lewis.contains("Twin Falls"), "a place of his life");
        assertTrue(lewis.contains("class=\"stops\""));
        assertFalse(lewis.contains("style="), "no inline styles");
    }

    @Test
    void linksToTheSourceCode() throws Exception {
        String plain = get("/", null).body();
        assertTrue(plain.contains("<footer class=\"site\">"));
        assertTrue(plain.contains(Site.PROGRAM));
        assertFalse(plain.contains("Source code"), "no link without --source-url");

        var withSource = new WebServer(new Site(
                published, new Site.Options("en", true, null, Site.MapOptions.OFF, "https://example.org/kwatern")));
        int port = withSource.start("127.0.0.1", 0);
        try {
            String body = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I0044"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(body.contains("<a href=\"https://example.org/kwatern\">Source code (AGPL)</a>"));
        } finally {
            withSource.stop();
        }
    }

    @Test
    void leavesMapsOutWhenOff() throws Exception {
        var off = new WebServer(new Site(published, new Site.Options("en", true, null, Site.MapOptions.OFF, null)));
        int port = off.start("127.0.0.1", 0);
        try {
            var home = CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertFalse(home.body().contains("href=\"/map\""));
            assertFalse(home.headers()
                    .firstValue("Content-Security-Policy")
                    .orElseThrow()
                    .contains("openstreetmap"));
            for (String path : new String[] {"/map", "/person/I0044/map"}) {
                var response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode(), path);
            }
        } finally {
            off.stop();
        }
    }

    @Test
    void servesANewVersionAfterReplace() throws Exception {
        String handle = published.living().iterator().next();
        Person person = full.people().get(handle).orElseThrow();
        var other = new WebServer(new Site(published, Site.Options.DEFAULT));
        int port = other.start("127.0.0.1", 0);
        try {
            String url = "http://127.0.0.1:" + port + "/person/" + person.id();
            String before = CLIENT.send(
                            HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(before.contains("<h1 class=\"living\">Living</h1>"));

            // As after reloading an export in which the person is no longer treated as alive.
            var shown = PrivacyFilter.apply(
                    full, new ProbablyAlive(full, AliveRules.DEFAULTS, 2026), new PrivacyOptions(false, true));
            other.replace(new Site(shown, Site.Options.DEFAULT));
            String after = CLIENT.send(
                            HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                    .body();
            assertFalse(after.contains("<h1 class=\"living\">"));
        } finally {
            other.stop();
        }
    }
}
