package me.hejl.kwatern;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Person;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.web.Site;
import me.hejl.kwatern.web.Urls;
import me.hejl.kwatern.web.Version;
import me.hejl.kwatern.web.WebServer;

/**
 * Fingerprints every page of a tree as the server sends it, to show that a change, such as a refactoring, leaves
 * the site as it was: {@code ./gradlew :server:pages} on the old code, then with {@code -PcompareWith} on the new.
 *
 * <p>The tree is loaded as the server loads it, in two views: the public one, and one that shows living people and
 * private records. Every page of every object is requested in each language with page texts, with the person tabs
 * of the first {@value #TAB_PEOPLE} people, and every thumbnail, portrait and file once. Each answer is kept as
 * its status and hashes of its headers (without the date and length) and body, one line per address. Surnames
 * are numbered, not written, so that a snapshot of a real export holds no names; the report prints only
 * addresses, counts and IDs.
 *
 * <p>Arguments: the export, the directory to write the snapshot to, and optionally the directory of an earlier
 * snapshot to compare it with; then the exit code is 1 if any page differs.
 */
final class PageSnapshot {

    /** People whose charts and maps are requested too; enough to cover the charts' cases, and quick. */
    static final int TAB_PEOPLE = 200;

    // Requests at a time.
    private static final int PARALLEL = 16;

    // The languages with page texts, as in MessagesTest.
    private static final List<String> LANGUAGES = List.of("en", "cs", "de", "sk");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private PageSnapshot() {}

    public static void main(String[] args) throws Exception {
        Path export = Path.of(args[0]);
        Path directory = Files.createDirectories(Path.of(args[1]));
        var views = Map.of(
                "public", new PrivacyOptions(true, true),
                "everything", new PrivacyOptions(false, false));
        for (var view : new TreeMap<>(views).entrySet()) {
            Map<String, String> pages = snapshot(export, view.getValue());
            Files.write(
                    directory.resolve(view.getKey() + ".txt"),
                    pages.entrySet().stream()
                            .map(e -> e.getKey() + " " + e.getValue())
                            .toList());
            System.out.printf("pages (%s): %d addresses%n", view.getKey(), pages.size());
        }
        if (args.length > 2) {
            int differences = compare(Path.of(args[2]), directory, views.keySet());
            System.exit(differences == 0 ? 0 : 1);
        }
    }

    /** Status and hashes of every address of a view, by language and address, in order. */
    private static Map<String, String> snapshot(Path export, PrivacyOptions options) throws Exception {
        var loader = new Loader(
                new Loader.Settings(export, 110, Login.Access.OPEN, options, null, false, null, Site.Options.DEFAULT));
        Version version = loader.load();
        var server = new WebServer(version, null);
        String base = "http://127.0.0.1:" + server.start("127.0.0.1", 0);
        Map<String, String> pages = new TreeMap<>();
        try {
            Site site = version.sites().everyone();
            // The surnames the site links to, in the order of their addresses.
            String index = CLIENT.send(
                            HttpRequest.newBuilder(URI.create(base + "/surnames"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .body();
            List<String> surnames = Pattern.compile("href=\"(/surname/[^\"]+)\"")
                    .matcher(index)
                    .results()
                    .map(m -> m.group(1))
                    .distinct()
                    .sorted()
                    .toList();
            Map<String, Integer> numbers = new HashMap<>();
            for (int i = 0; i < surnames.size(); i++) {
                numbers.put(surnames.get(i), i);
            }
            Map<String, Future<String>> answers = new TreeMap<>();
            // A few at a time, as a browser would ask; the answers do not depend on the order.
            var permits = new Semaphore(PARALLEL);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (String language : LANGUAGES) {
                    for (String path : pages(site.data().database(), surnames)) {
                        answers.put(
                                language + " " + anonymous(path, numbers),
                                executor.submit(() -> fingerprint(base + path, language, permits)));
                    }
                }
                // The same in every language.
                for (String path : files(site.data().database())) {
                    answers.put("- " + path, executor.submit(() -> fingerprint(base + path, "en", permits)));
                }
                for (var answer : answers.entrySet()) {
                    pages.put(answer.getKey(), answer.getValue().get());
                }
            }
        } finally {
            server.stop();
            loader.close();
        }
        return pages;
    }

    /** The addresses of the pages, which depend on the language. */
    private static List<String> pages(GrampsDatabase db, List<String> surnames) {
        List<String> paths = new ArrayList<>(List.of(
                "/",
                "/surnames",
                "/places",
                "/sources",
                "/photos",
                "/search?q=a",
                "/search?q=to",
                "/search?q=",
                "/person/nobody",
                "/nothing"));
        for (String kind : List.of("all", "births", "marriages", "deaths")) {
            for (String period : List.of("all", "before1800", "1800s", "1900s")) {
                paths.add(Urls.treeMap(kind, period));
            }
        }
        paths.addAll(surnames);
        int tabs = 0;
        for (Person person : db.people().all()) {
            paths.add(Urls.person(person));
            if (tabs++ < TAB_PEOPLE) {
                paths.add(Urls.person(person) + "?family=hide");
                for (int generations = 3; generations <= 5; generations++) {
                    paths.add(Urls.ancestors(person, generations, false));
                    paths.add(Urls.ancestors(person, generations, true));
                }
                for (int generations = 2; generations <= 4; generations++) {
                    paths.add(Urls.descendants(person, generations));
                }
                paths.add(Urls.personMap(person, false));
                paths.add(Urls.personMap(person, true));
            }
        }
        db.families().all().forEach(f -> paths.add(Urls.family(f)));
        db.places().all().forEach(p -> paths.add(Urls.place(p)));
        db.sources().all().forEach(s -> paths.add(Urls.source(s)));
        db.media().all().forEach(m -> paths.add(Urls.media(m)));
        return paths;
    }

    /** The addresses of images, files and the program's own files, the same in every language. */
    private static List<String> files(GrampsDatabase db) {
        List<String> paths = new ArrayList<>(List.of(
                Urls.stylesheet(),
                "/static/style.css",
                "/static/icon.svg",
                "/static/map.js",
                "/static/leaflet/leaflet.js",
                "/static/leaflet/leaflet.css",
                "/health"));
        for (PrimaryObject media : db.media().all()) {
            paths.add(Urls.thumbnail(media));
            paths.add(Urls.mediaImage(media));
            paths.add(Urls.original(media));
        }
        db.people().all().forEach(p -> paths.add(Urls.portrait(p)));
        return paths;
    }

    /** The address with a surname in it replaced by its number, so that no name is written. */
    private static String anonymous(String path, Map<String, Integer> surnames) {
        Integer number = surnames.get(path);
        return number == null ? path : "/surname/#" + number;
    }

    /**
     * "STATUS HEADERS BODY": the status, and hashes of the headers and of the body. The date is left out of the
     * headers, and so is the length, which the body shows.
     */
    private static String fingerprint(String url, String language, Semaphore permits)
            throws IOException, InterruptedException {
        HttpResponse<byte[]> response;
        permits.acquire();
        try {
            response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Accept-Language", language)
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } finally {
            permits.release();
        }
        String headers = new TreeMap<>(response.headers().map())
                .entrySet().stream()
                        .filter(e -> !e.getKey().equalsIgnoreCase("date")
                                && !e.getKey().equalsIgnoreCase("content-length"))
                        .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                        .collect(Collectors.joining("\n"));
        return response.statusCode() + " " + hash(headers.getBytes(StandardCharsets.UTF_8)) + " "
                + hash(response.body());
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Prints how two snapshots differ, and returns the number of addresses that do. */
    private static int compare(Path before, Path after, Iterable<String> views) throws IOException {
        int differences = 0;
        for (String view : views) {
            Map<String, String> old = read(before.resolve(view + ".txt"));
            Map<String, String> now = read(after.resolve(view + ".txt"));
            List<String> changed = new ArrayList<>();
            for (String address : union(old, now)) {
                String a = old.get(address);
                String b = now.get(address);
                if (a == null || !a.equals(b)) {
                    changed.add(address + (a == null ? " (new)" : b == null ? " (gone)" : differs(a, b)));
                }
            }
            System.out.printf("compared (%s): %d addresses, %d differ%n", view, now.size(), changed.size());
            changed.stream().limit(20).forEach(address -> System.out.println("  " + address));
            differences += changed.size();
        }
        return differences;
    }

    /** Which part of an answer differs: " (status)", " (headers)" or " (body)". */
    private static String differs(String before, String after) {
        String[] a = before.split(" ");
        String[] b = after.split(" ");
        List<String> parts = new ArrayList<>();
        String[] names = {"status", "headers", "body"};
        for (int i = 0; i < names.length; i++) {
            if (!a[i].equals(b[i])) {
                parts.add(names[i]);
            }
        }
        return " (" + String.join(", ", parts) + ")";
    }

    private static Set<String> union(Map<String, String> a, Map<String, String> b) {
        Set<String> all = new TreeSet<>(a.keySet());
        all.addAll(b.keySet());
        return all;
    }

    /** A snapshot file: the address, the language before it, and the fingerprint after it. */
    private static Map<String, String> read(Path file) throws IOException {
        Map<String, String> pages = new TreeMap<>();
        for (String line : Files.readAllLines(file)) {
            // "LANGUAGE PATH STATUS HEADERS BODY"; a path never has a space, as URLs encode it.
            String[] parts = line.split(" ");
            pages.put(parts[0] + " " + parts[1], parts[2] + " " + parts[3] + " " + parts[4]);
        }
        return pages;
    }
}
