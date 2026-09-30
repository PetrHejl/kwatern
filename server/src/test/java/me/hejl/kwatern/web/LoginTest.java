package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageDecoders;
import me.hejl.kwatern.auth.Grants;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.auth.PasswordHash;
import me.hejl.kwatern.auth.Sessions;
import me.hejl.kwatern.auth.Users;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Signing in to a private site and to one with a members' view, over HTTP. */
class LoginTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String PASSWORD = "correct horse";

    @TempDir
    static Path directory;

    private static GrampsDatabase full;
    private static Person living;
    private static WebServer privateServer;
    private static WebServer membersServer;
    private static String privateBase;
    private static String membersBase;

    @BeforeAll
    static void start() throws IOException {
        Path example = Path.of(System.getProperty("gramps.example"));
        full = GrampsXml.read(example).database();
        var alive = new ProbablyAlive(full, AliveRules.DEFAULTS, 2026);
        PublicDatabase everyone = PrivacyFilter.apply(full, alive);
        living = full.people().get(everyone.living().iterator().next()).orElseThrow();
        Map<Grants, Site> members = new HashMap<>();
        for (Grants grants : Grants.ALL) {
            PublicDatabase view =
                    PrivacyFilter.apply(full, alive, new PrivacyOptions(!grants.living(), !grants.privateRecords()));
            members.put(
                    grants,
                    new Site(
                            view,
                            Site.Options.DEFAULT,
                            new MediaImages(view.database(), example.getParent(), null, ImageDecoders.DEFAULT)));
        }

        Path file = directory.resolve("users.txt");
        String hash = PasswordHash.hash(PASSWORD.toCharArray());
        Users.put(file, "jana", new Users.Member(new Grants(true, false), hash));
        Users.put(file, "eva", new Users.Member(Grants.NONE, hash));
        Users users = Users.load(file);

        privateServer = new WebServer(
                Version.of(new Sites(null, members)),
                new Login(Login.Access.PRIVATE, users, new Sessions(Sessions.randomKey(), users), false));
        privateBase = "http://127.0.0.1:" + privateServer.start("127.0.0.1", 0);
        membersServer = new WebServer(
                Version.of(new Sites(members.get(Grants.NONE), members)),
                new Login(Login.Access.MEMBERS, users, new Sessions(Sessions.randomKey(), users), false));
        membersBase = "http://127.0.0.1:" + membersServer.start("127.0.0.1", 0);
    }

    @AfterAll
    static void stop() {
        privateServer.stop();
        membersServer.stop();
    }

    private static HttpResponse<String> get(String url, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String base, String path, String origin, String cookie, String form)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", origin)
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String signIn(String base, String password, String next) throws Exception {
        return signIn(base, "jana", password, next);
    }

    private static String signIn(String base, String name, String password, String next) throws Exception {
        return post(
                        base,
                        "/sign-in",
                        base,
                        null,
                        "name=" + name + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                                + "&keep=1&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8))
                .headers()
                .firstValue("Set-Cookie")
                .map(c -> c.split(";")[0])
                .orElse(null);
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    @Test
    void privateSiteShowsNothingBeforeSigningIn() throws Exception {
        for (String path :
                new String[] {"/person/I0044", "/no/such/page", "/thumbnail/O0010", "/media/O0010/original"}) {
            var response = get(privateBase + path, null);
            assertEquals(303, response.statusCode(), path);
            assertEquals(
                    "/sign-in?next=" + URLEncoder.encode(path, StandardCharsets.UTF_8),
                    header(response, "Location"),
                    path);
        }
        assertEquals("/sign-in", header(get(privateBase + "/", null), "Location"));
        assertEquals(200, get(privateBase + "/static/style.css", null).statusCode(), "the page needs its styles");
        assertEquals(200, get(privateBase + "/health", null).statusCode());

        var page = get(privateBase + "/sign-in?next=%2Fperson%2FI0044", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("This family tree is private."));
        assertTrue(page.body().contains("value=\"/person/I0044\""), "comes back after signing in");
        assertFalse(page.body().contains("href=\"/surnames\""), "no navigation");
        assertFalse(page.body().contains("name=\"q\""), "no search");
        assertTrue(page.body().contains("<footer class=\"site\">"), "the footer even when private");
        assertTrue(header(page, "X-Robots-Tag").contains("noindex"));
        assertEquals("private, no-store", header(page, "Cache-Control"));
    }

    @Test
    void signsInAndOut() throws Exception {
        var wrong = post(privateBase, "/sign-in", privateBase, null, "name=jana&password=nope&next=%2F");
        assertEquals(200, wrong.statusCode());
        assertTrue(wrong.body().contains("Name or password is wrong."));
        assertTrue(wrong.body().contains("value=\"jana\""), "the name stays");
        assertTrue(header(wrong, "Set-Cookie").isEmpty());

        var right = post(
                privateBase,
                "/sign-in",
                privateBase,
                null,
                "name=jana&password=correct+horse&keep=1&next=%2Fperson%2FI0044");
        assertEquals(303, right.statusCode());
        assertEquals("/person/I0044", header(right, "Location"));
        String setCookie = header(right, "Set-Cookie");
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Lax"), setCookie);
        assertTrue(setCookie.contains("Max-Age=2592000"), "kept for 30 days: " + setCookie);
        assertFalse(setCookie.contains("Secure"), "plain HTTP, no proxy");
        String cookie = setCookie.split(";")[0];

        var page = get(privateBase + "/person/" + living.id(), cookie);
        assertEquals(200, page.statusCode());
        assertFalse(page.body().contains("<h1 class=\"living\">"), "members see living people");
        assertTrue(page.body().contains("action=\"/sign-out\""));
        assertTrue(page.body().contains(">jana<"));
        assertFalse(page.body().contains("members-bar"), "a private site has no public view to mistake it for");
        assertEquals("private, no-store", header(page, "Cache-Control"));
        assertEquals("/", header(get(privateBase + "/sign-in", cookie), "Location"), "already signed in");

        var out = post(privateBase, "/sign-out", privateBase, cookie, "");
        assertEquals(303, out.statusCode());
        assertTrue(header(out, "Set-Cookie").contains("Max-Age=0"));
        assertEquals(405, get(privateBase + "/sign-out", cookie).statusCode(), "signing out needs a form");
    }

    @Test
    void refusesFormsFromElsewhereAndRedirectsOnlyHere() throws Exception {
        var elsewhere = post(
                privateBase, "/sign-in", "https://evil.example", null, "name=jana&password=correct+horse&next=%2F");
        assertEquals(403, elsewhere.statusCode());
        assertTrue(header(elsewhere, "Set-Cookie").isEmpty());

        for (String next : new String[] {"//evil.example/", "https://evil.example/", "/\\evil.example", "/sign-out"}) {
            var response = post(
                    privateBase,
                    "/sign-in",
                    privateBase,
                    null,
                    "name=jana&password=correct+horse&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
            assertEquals("/", header(response, "Location"), next);
        }
        var notAForm = CLIENT.send(
                HttpRequest.newBuilder(URI.create(privateBase + "/sign-in"))
                        .header("Origin", privateBase)
                        .POST(HttpRequest.BodyPublishers.ofString("x".repeat(10_000)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, notAForm.statusCode());
        var forged = get(privateBase + "/person/I0044", "kwatern-session=amFuYQ.9999999999.1.AAAA");
        assertEquals(303, forged.statusCode(), "a forged cookie is no session");
    }

    @Test
    void membersSeeMoreThanEveryone() throws Exception {
        String path = "/person/" + living.id();
        var publicPage = get(membersBase + path, null);
        assertTrue(publicPage.body().contains("<h1 class=\"living\">Living</h1>"));
        assertTrue(
                publicPage.body().contains("href=\"/sign-in?next=%2Fperson%2F" + living.id() + "\""),
                "Sign in, coming back here");
        assertTrue(header(publicPage, "Vary").contains("Cookie"));
        assertEquals("no-cache", header(publicPage, "Cache-Control"));
        assertTrue(header(publicPage, "X-Robots-Tag").isEmpty(), "the public view may be found");

        var signInPage = get(membersBase + "/sign-in", null);
        assertTrue(signInPage.body().contains("href=\"/surnames\""), "the public site's header");
        assertFalse(signInPage.body().contains("This family tree is private."));

        String cookie = signIn(membersBase, PASSWORD, path);
        var membersPage = get(membersBase + path, cookie);
        assertFalse(membersPage.body().contains("<h1 class=\"living\">"));
        assertTrue(membersPage.body().contains("class=\"members-bar\""));
        assertTrue(header(membersPage, "X-Robots-Tag").contains("noindex"));

        var thumbnail = get(membersBase + "/thumbnail/O0010", cookie);
        assertEquals(200, thumbnail.statusCode());
        assertEquals("private, no-cache", header(thumbnail, "Cache-Control"));
        assertEquals("public, max-age=3600", header(get(membersBase + "/thumbnail/O0010", null), "Cache-Control"));
    }

    @Test
    void eachMemberSeesWhatTheyAreGranted() throws Exception {
        Path file = directory.resolve("users.txt");
        String path = "/person/" + living.id();
        String cookie = signIn(membersBase, "eva", PASSWORD, path);
        var page = get(membersBase + path, cookie);
        assertTrue(page.body().contains("<h1 class=\"living\">Living</h1>"), "granted nothing more");
        assertFalse(page.body().contains("members-bar"), "the public view, which the bar must not call otherwise");
        assertTrue(get(privateBase + path, signIn(privateBase, "eva", PASSWORD, path))
                .body()
                .contains("<h1 class=\"living\">Living</h1>"));

        String before = Files.readString(file);
        try {
            Files.writeString(file, before.replace("eva::", "eva:living:"));
            // The file is checked for changes every two seconds; the grants apply to the cookie she has.
            Thread.sleep(2_100);
            var granted = get(membersBase + path, cookie);
            assertFalse(granted.body().contains("<h1 class=\"living\">"));
            assertTrue(granted.body().contains("class=\"members-bar\""));
        } finally {
            Files.writeString(file, before);
            Thread.sleep(2_100);
        }
    }

    @Test
    void removingAMemberEndsTheirSession() throws Exception {
        Path file = directory.resolve("users.txt");
        String cookie = signIn(membersBase, PASSWORD, "/");
        assertTrue(get(membersBase + "/", cookie).body().contains("action=\"/sign-out\""));
        String before = Files.readString(file);
        try {
            Files.writeString(file, "# nobody\n");
            // The file is checked for changes every two seconds.
            Thread.sleep(2_100);
            assertTrue(get(membersBase + "/", cookie).body().contains("href=\"/sign-in\""));
        } finally {
            Files.writeString(file, before);
            Thread.sleep(2_100);
        }
    }
}
