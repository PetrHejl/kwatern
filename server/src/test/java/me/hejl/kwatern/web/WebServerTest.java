package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Header;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.Researcher;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageBuilder;
import me.hejl.image.ImageDecoders;
import me.hejl.image.jpeg.JpegReader;
import me.hejl.kwatern.view.Views;
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
    void logsOnlyAddressCharactersOfAnAddress() {
        assertEquals("192.0.2.1", SignIn.printable("192.0.2.1"));
        assertEquals("fe80::1%eth0", SignIn.printable("fe80::1%eth0"));
        assertEquals("1.2.3.4?sign-in:?ok?from?x", SignIn.printable("1.2.3.4\nsign-in: ok from x"));
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
        var dark = new WebServer(new Site(published, new Site.Options("en", false, "dark", Site.MapOptions.OFF)));
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
    void showsContactAndCreditInTheFooterWhenGiven() throws Exception {
        String plain = get("/", null).body();
        assertFalse(plain.contains("mailto:") || plain.contains(">Contact<"), "no contact by default");
        String credit = "Example tree: <a href=\"https://gramps-project.org\">Gramps</a>, GPL-2.0-or-later";
        var credited = new WebServer(new Site(
                published, new Site.Options("en", true, null, Site.MapOptions.OFF, "mailto:tree@example.org", credit)));
        int port = credited.start("127.0.0.1", 0);
        try {
            String body = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I0044"))
                                    .header("Accept-Language", "cs")
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(body.contains("<a href=\"mailto:tree@example.org\">Kontakt</a>"), "translated contact link");
            assertTrue(body.contains(credit), "the credit as HTML");
        } finally {
            credited.stop();
        }
    }

    @Test
    void takesTheContactFromTheResearcherOnlyWithAPlainAddress() {
        assertEquals("https://example.org/", Site.contactUrl("https://example.org/", null));
        assertNull(Site.contactUrl(Site.Options.RESEARCHER, null));
        assertNull(Site.contactUrl(Site.Options.RESEARCHER, withEmail(null)));
        assertEquals(
                "mailto:a.b@example.org", Site.contactUrl(Site.Options.RESEARCHER, withEmail(" a.b@example.org ")));
        assertNull(Site.contactUrl(Site.Options.RESEARCHER, withEmail("a@example.org?bcc=b@example.org")));
        assertNull(Site.contactUrl(Site.Options.RESEARCHER, withEmail("javascript:alert(1)//@x.org")));
    }

    private static Header withEmail(String email) {
        return new Header(null, null, new Researcher(null, null, null, null, null, null, null, null, email), null);
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
        // Every living person rather than one: the set's order changes from run to run, so a check that fails for a
        // few people would fail at random.
        for (String handle : published.living()) {
            Person original = full.people().get(handle).orElseThrow();
            String body = get("/person/" + original.id(), null).body();
            assertTrue(body.contains("<h1 class=\"living\">Living</h1>"), original.id());
            assertFalse(body.contains("class=\"timeline\""), original.id() + ": no events");
            String given = original.primaryName().first();
            if (given != null && given.length() > 3) {
                // Links to relatives go: one may have the same given name, such as a father whose son is alive.
                String own = Pattern.compile("(?s)<a [^>]*href=\"/person/(?!" + original.id() + "[\"/]).*?</a>")
                        .matcher(body)
                        .replaceAll("");
                assertFalse(own.contains(given), original.id() + ": no given name");
            }
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
        // The browser asks again, as members' browsers do each time: the file is not sent again.
        var again = CLIENT.send(
                HttpRequest.newBuilder(URI.create(base + "/media/O0010/original"))
                        .header(
                                "If-None-Match",
                                original.headers().firstValue("ETag").orElseThrow())
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(304, again.statusCode());
        assertEquals(0, again.body().length);

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
    void letsBrowsersKeepTheScripts() throws Exception {
        // Checked with the server on every map page: without a tag, the 150 KB of Leaflet were sent each time.
        for (String path : new String[] {"/static/leaflet/leaflet.js", "/static/map.js", "/static/style.css"}) {
            var first = get(path, null);
            String etag = first.headers().firstValue("ETag").orElseThrow();
            var again = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(base + path))
                            .header("If-None-Match", etag)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(304, again.statusCode(), path);
            assertEquals("no-cache", again.headers().firstValue("Cache-Control").orElseThrow(), path);
            assertEquals(first.body(), get(path, null).body(), path);
        }
        assertEquals(404, get("/static/nothing.js", null).statusCode());
    }

    @Test
    void makesListingsOfTheWholeTreeOnce() {
        var site = new Site(published, Site.Options.DEFAULT);
        Ui ui = site.ui(null, null);
        var first = new Views(site.data(), site.index(), ui, site.images());
        var second = new Views(site.data(), site.index(), ui.with(Viewer.OPEN), site.images());
        assertSame(first.places(), second.places());
        assertSame(first.surnames(), second.surnames());
        assertSame(first.treeMap("births", "all"), second.treeMap("births", "all"));
        assertSame(first.treeMap("all", "all"), second.treeMap("nonsense", null), "unknown filters are all");
        assertFalse(
                first.places() == new Views(site.data(), site.index(), site.ui("cs", null), site.images()).places(),
                "each language its own");
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
        assertTrue(plain.contains("<a href=\"https://github.com/PetrHejl/kwatern\">Source code (AGPL)</a>"));
    }

    @Test
    void mapsEventsWithoutAType() throws Exception {
        // Gramps always writes one, but the schema does not require it, so other programs may leave it out.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <events>
                    <event handle="_e0" change="1" id="E0"><place hlink="_p0"/></event>
                    <event handle="_e1" change="1" id="E1"><type>Birth</type><place hlink="_p0"/></event>
                  </events>
                  <places>
                    <placeobj handle="_p0" change="1" id="P0" type="City">
                      <pname value="Brno"/><coord long="16.61" lat="49.19"/>
                    </placeobj>
                  </places>
                </database>
                """;
        GrampsDatabase db = GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .database();
        var untyped = new WebServer(new Site(
                PrivacyFilter.apply(db, new ProbablyAlive(db, AliveRules.DEFAULTS, 2026)), Site.Options.DEFAULT));
        int port = untyped.start("127.0.0.1", 0);
        try {
            for (String kind : new String[] {"all", "births", "marriages", "deaths"}) {
                var response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/map?kind=" + kind))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                // The kinds of events other than all failed with a server error.
                assertEquals(200, response.statusCode(), kind);
            }
            String all = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/map"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(all.contains("2 events"), "the event without a type is on the map of all events");
        } finally {
            untyped.stop();
        }
    }

    @Test
    void marksParentsAndChildrenNotByBirth() throws Exception {
        // I2 was born to I3 (F0) and grew up with foster parents I0 and I1 (F1). I5 was born to I3 and is
        // a stepchild of I4 (F2), so the relation differs between the two parents. I6 has only her foster
        // parents (F1). I7 is a child of I3 (F0) whose relation to her is unknown.
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns="http://gramps-project.org/xml/1.7.2/">
                  <events>
                    <event handle="_b0" change="1" id="E0"><type>Birth</type><dateval val="1893"/></event>
                    <event handle="_d0" change="1" id="E1"><type>Death</type><dateval val="1968"/></event>
                    <event handle="_b1" change="1" id="E2"><type>Birth</type><dateval val="1895"/></event>
                    <event handle="_d1" change="1" id="E3"><type>Death</type><dateval val="1960"/></event>
                    <event handle="_b2" change="1" id="E4"><type>Birth</type><dateval val="1915"/></event>
                    <event handle="_d2" change="1" id="E5"><type>Death</type><dateval val="2011"/></event>
                    <event handle="_b3" change="1" id="E6"><type>Birth</type><dateval val="1890"/></event>
                    <event handle="_d3" change="1" id="E7"><type>Death</type><dateval val="1924"/></event>
                    <event handle="_b4" change="1" id="E8"><type>Birth</type><dateval val="1885"/></event>
                    <event handle="_d4" change="1" id="E9"><type>Death</type><dateval val="1950"/></event>
                    <event handle="_b5" change="1" id="E10"><type>Birth</type><dateval val="1920"/></event>
                    <event handle="_d5" change="1" id="E11"><type>Death</type><dateval val="2000"/></event>
                    <event handle="_b6" change="1" id="E12"><type>Birth</type><dateval val="1925"/></event>
                    <event handle="_d6" change="1" id="E13"><type>Death</type><dateval val="2005"/></event>
                    <event handle="_b7" change="1" id="E14"><type>Birth</type><dateval val="1922"/></event>
                    <event handle="_d7" change="1" id="E15"><type>Death</type><dateval val="1990"/></event>
                  </events>
                  <people>
                    <person handle="_i0" change="1" id="I0"><gender>M</gender>
                      <eventref hlink="_b0" role="Primary"/><eventref hlink="_d0" role="Primary"/>
                      <parentin hlink="_f1"/></person>
                    <person handle="_i1" change="1" id="I1"><gender>F</gender>
                      <eventref hlink="_b1" role="Primary"/><eventref hlink="_d1" role="Primary"/>
                      <parentin hlink="_f1"/></person>
                    <person handle="_i2" change="1" id="I2"><gender>M</gender>
                      <eventref hlink="_b2" role="Primary"/><eventref hlink="_d2" role="Primary"/>
                      <childof hlink="_f0"/><childof hlink="_f1"/></person>
                    <person handle="_i3" change="1" id="I3"><gender>F</gender>
                      <eventref hlink="_b3" role="Primary"/><eventref hlink="_d3" role="Primary"/>
                      <parentin hlink="_f0"/><parentin hlink="_f2"/></person>
                    <person handle="_i4" change="1" id="I4"><gender>M</gender>
                      <eventref hlink="_b4" role="Primary"/><eventref hlink="_d4" role="Primary"/>
                      <parentin hlink="_f2"/></person>
                    <person handle="_i5" change="1" id="I5"><gender>F</gender>
                      <eventref hlink="_b5" role="Primary"/><eventref hlink="_d5" role="Primary"/>
                      <childof hlink="_f0"/><childof hlink="_f2"/></person>
                    <person handle="_i6" change="1" id="I6"><gender>F</gender>
                      <eventref hlink="_b6" role="Primary"/><eventref hlink="_d6" role="Primary"/>
                      <childof hlink="_f1"/></person>
                    <person handle="_i7" change="1" id="I7"><gender>M</gender>
                      <eventref hlink="_b7" role="Primary"/><eventref hlink="_d7" role="Primary"/>
                      <childof hlink="_f0"/></person>
                  </people>
                  <families>
                    <family handle="_f0" change="1" id="F0"><mother hlink="_i3"/>
                      <childref hlink="_i2"/><childref hlink="_i5"/><childref hlink="_i7" mrel="Unknown"/></family>
                    <family handle="_f1" change="1" id="F1"><father hlink="_i0"/><mother hlink="_i1"/>
                      <childref hlink="_i2" mrel="Foster" frel="Foster"/>
                      <childref hlink="_i6" mrel="Foster" frel="Foster"/></family>
                    <family handle="_f2" change="1" id="F2"><father hlink="_i4"/><mother hlink="_i3"/>
                      <childref hlink="_i5" frel="Stepchild"/></family>
                  </families>
                </database>
                """;
        GrampsDatabase db = GrampsXml.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .database();
        var tree = new WebServer(new Site(
                PrivacyFilter.apply(db, new ProbablyAlive(db, AliveRules.DEFAULTS, 2026)), Site.Options.DEFAULT));
        int port = tree.start("127.0.0.1", 0);
        try {
            String fosterFather = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I0"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(
                    Pattern.compile(
                                    "Birth of son <span><a href=\"/person/I2\"[^>]*>[^<]*</a>\\s* · <span class=\"relation\">"
                                            + "Foster</span>")
                            .matcher(fosterFather)
                            .find(),
                    "the foster son's birth is marked in the timeline");
            assertTrue(fosterFather.contains("<span class=\"box-sub relation\">Foster</span>"), "and in the diagram");

            String fosterSon = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I2"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            int birthParents =
                    fosterSon.indexOf("<div class=\"d-parents-label\">Birth parents · <a href=\"/family/F0\">");
            int fosterParents =
                    fosterSon.indexOf("<div class=\"d-parents-label\">Foster parents · <a href=\"/family/F1\">");
            assertTrue(
                    birthParents >= 0 && fosterParents > birthParents,
                    "both pairs of parents are headed, the main ones first");
            assertFalse(fosterSon.contains("nowrap\">Foster father<"), "so their boxes do not repeat the relation");
            assertFalse(fosterSon.contains("class=\"d-line\""), "no line joins him to one of the pairs");
            assertTrue(
                    Pattern.compile(
                                    "Death of father <span><a href=\"/person/I0\"[^>]*>[^<]*</a>\\s* · <span class=\"relation\">"
                                            + "Foster father</span>")
                            .matcher(fosterSon)
                            .find(),
                    "the foster father's death is in the timeline, named from his side");

            String stepchild = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I5"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(
                    stepchild.contains("<div class=\"d-parents-label\">Other parents · <a href=\"/family/F2\">"),
                    "parents with different relations are other parents");
            assertTrue(
                    stepchild.contains("<span class=\"nowrap\">Stepfather</span>"),
                    "the stepfather is called so, not by the child's word");
            assertFalse(stepchild.contains("Stepchild"), "which is nowhere on her page");

            // A foster child whose only parents are the foster parents: no heading, the line, and their roles.
            String onlyFoster = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I6"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertFalse(onlyFoster.contains("d-parents-label"));
            assertTrue(onlyFoster.contains("class=\"d-line\""));
            assertTrue(onlyFoster.contains("<span class=\"nowrap\">Foster father</span>"));
            assertTrue(onlyFoster.contains("<span class=\"nowrap\">Foster mother</span>"));

            // Without a word for the parent ("Unknown"), the box still says which parent, with the relation after.
            String unknown = CLIENT.send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/person/I7"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            assertTrue(unknown.contains("<span class=\"nowrap\">Mother</span>"));
            assertTrue(unknown.contains("<span class=\"box-sub relation\">Unknown relation</span>"));
        } finally {
            tree.stop();
        }
    }

    @Test
    void leavesMapsOutWhenOff() throws Exception {
        var off = new WebServer(new Site(published, new Site.Options("en", true, null, Site.MapOptions.OFF)));
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
