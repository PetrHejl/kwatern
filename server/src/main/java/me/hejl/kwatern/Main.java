package me.hejl.kwatern;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import me.hejl.gramps.date.DateFormatter;
import me.hejl.gramps.i18n.Languages;
import me.hejl.gramps.i18n.Numbers;
import me.hejl.gramps.model.DateValue;
import me.hejl.gramps.model.GrampsCalendar;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.name.NameOrder;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsPackage;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.gramps.xml.ParseResult;
import me.hejl.image.ImageDecoders;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.auth.PasswordHash;
import me.hejl.kwatern.auth.Sessions;
import me.hejl.kwatern.auth.Users;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Site;
import me.hejl.kwatern.web.Sites;
import me.hejl.kwatern.web.WebServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

@Command(
        name = "kwatern",
        mixinStandardHelpOptions = true,
        version = Site.PROGRAM,
        sortOptions = false,
        usageHelpAutoWidth = true,
        // The generated synopsis lists every option, which the option list below shows anyway; later lines are
        // indented under the first, after "Usage: ".
        customSynopsis = {
            "kwatern [OPTIONS] EXPORT",
            "       kwatern passwd --users=FILE NAME",
            "       kwatern help [COMMAND]",
        },
        subcommands = {Main.Passwd.class, HelpCommand.class},
        description = "Serves a Gramps XML export as a read-only web site.")
public final class Main implements Callable<Integer> {

    enum Visibility {
        HIDE,
        SHOW
    }

    enum Toggle {
        ON,
        OFF
    }

    enum Theme {
        AUTO,
        LIGHT,
        DARK
    }

    @Spec
    private CommandSpec spec;

    // Optional for the parser only, which would otherwise ask for it before the passwd subcommand; required below.
    @Parameters(
            paramLabel = "EXPORT",
            arity = "0..1",
            description = "Gramps XML export (.gramps), compressed or not, or Gramps package (.gpkg) with the media.")
    private Path file;

    @Option(
            names = "--host",
            paramLabel = "ADDRESS",
            defaultValue = "127.0.0.1",
            description = "Address to listen on. Default: ${DEFAULT-VALUE}")
    private String host;

    @Option(
            names = "--port",
            paramLabel = "PORT",
            defaultValue = "8080",
            description = "Port to listen on. Default: ${DEFAULT-VALUE}")
    private int port;

    @Option(
            names = "--language",
            paramLabel = "LANG",
            defaultValue = "en",
            description = "Page language when the browser asks for none, e.g. cs. Default: ${DEFAULT-VALUE}")
    private String language;

    @Option(
            names = "--gender-colours",
            paramLabel = "on|off",
            defaultValue = "on",
            description = "Mark men and women by colour in family diagrams and person lists. Default: ${DEFAULT-VALUE}")
    private Toggle genderColours;

    @Option(
            names = "--theme",
            paramLabel = "auto|light|dark",
            defaultValue = "auto",
            description = "Colour theme; auto follows each visitor's system setting. Default: ${DEFAULT-VALUE}")
    private Theme theme;

    @Option(
            names = "--map",
            paramLabel = "on|off",
            defaultValue = "on",
            description = "Maps of places and lives. The map images come from a tile server, which visitors' browsers"
                    + " contact directly. Default: ${DEFAULT-VALUE}")
    private Toggle map;

    @Option(
            names = "--map-tiles",
            paramLabel = "URL",
            defaultValue = Site.MapOptions.OSM_TILES,
            description = "Tile server, as a URL template with {z}, {x} and {y}. Default: OpenStreetMap, whose tile"
                    + " usage policy allows light use.")
    private String mapTiles;

    @Option(
            names = "--map-attribution",
            paramLabel = "HTML",
            description = "The credit the tile server requires, shown on every map. Default: the OpenStreetMap credit.")
    private String mapAttribution;

    @Option(
            names = "--living",
            paramLabel = "hide|show",
            defaultValue = "hide",
            description = "People who may be alive, in the public view: hide shows them only as \"Living\" without"
                    + " details. Default: ${DEFAULT-VALUE}")
    private Visibility living;

    @Option(
            names = "--private",
            paramLabel = "hide|show",
            defaultValue = "hide",
            description = "Records and details marked private in Gramps, in the public view. Default: ${DEFAULT-VALUE}")
    private Visibility privateRecords;

    @Option(
            names = "--access",
            paramLabel = "open|members|private",
            defaultValue = "open",
            description = "Who sees the tree. open: everyone sees the public view. members: everyone sees the public"
                    + " view, members who sign in see the members' view. private: only members who sign in see"
                    + " anything. Default: ${DEFAULT-VALUE}")
    private Login.Access access;

    @Option(
            names = "--users",
            paramLabel = "FILE",
            description = "The members who can sign in, as written by kwatern passwd; read again when it changes.")
    private Path users;

    @Option(
            names = "--members-living",
            paramLabel = "hide|show",
            defaultValue = "show",
            description = "People who may be alive, in the members' view. Default: ${DEFAULT-VALUE}")
    private Visibility membersLiving;

    @Option(
            names = "--members-private",
            paramLabel = "hide|show",
            defaultValue = "hide",
            description =
                    "Records and details marked private in Gramps, in the members' view. Default: ${DEFAULT-VALUE}")
    private Visibility membersPrivate;

    @Option(
            names = "--secret-file",
            paramLabel = "FILE",
            description = "Key that signs the session cookies, created if missing, so that members stay signed in when"
                    + " the server restarts. Default: a new key on each start.")
    private Path secretFile;

    @Option(
            names = "--behind-proxy",
            description = "Requests come through a reverse proxy such as Caddy or nginx, whose X-Forwarded-For, -Host"
                    + " and -Proto headers are then trusted. Only the proxy must be able to reach this server.")
    private boolean behindProxy;

    @Option(
            names = "--allow-insecure-login",
            description = "Allow signing in over plain HTTP on an address other than this computer's, where passwords"
                    + " and sessions can be read on the way.")
    private boolean allowInsecureLogin;

    @Option(
            names = "--max-age",
            paramLabel = "YEARS",
            defaultValue = "110",
            description = "Oldest age anyone reaches, used to decide who may be alive. Default: ${DEFAULT-VALUE}")
    private int maxAge;

    @Option(
            names = "--media-dir",
            paramLabel = "DIR",
            description = "Directory with the media files. Default: the media path set in the export, else the"
                    + " export's directory. Files the export names by absolute path under its media path are looked"
                    + " for here too.")
    private Path mediaDir;

    @Option(
            names = "--extract-dir",
            paramLabel = "DIR",
            description = "For a package: where to extract the published media files, in a new directory for each"
                    + " load, removed when replaced and on exit. Default: the system's temporary directory. Where /tmp"
                    + " is kept in memory, prefer a directory on disk.")
    private Path extractDir;

    @Option(
            names = "--reload",
            paramLabel = "on|off",
            defaultValue = "on",
            description =
                    "Load the export again when its file changes, and serve the new version. Default: ${DEFAULT-VALUE}")
    private Toggle reload;

    @Option(
            names = "--reload-interval",
            paramLabel = "SECONDS",
            defaultValue = "10",
            description = "How often to check the file for changes. Default: ${DEFAULT-VALUE}")
    private int reloadInterval;

    @Option(
            names = "--source-url",
            paramLabel = "URL",
            description = "Where visitors get the source code of this program, linked in the page footer. The AGPL"
                    + " asks anyone who serves a changed version to offer its source this way. Default: no link.")
    private String sourceUrl;

    @Option(names = "--check", description = "Load the export, print a summary and exit.")
    private boolean check;

    @Option(
            names = "--licenses",
            description = "Print the license of Kwatern and of the software it includes, and exit.")
    private boolean licenses;

    public static void main(String[] args) {
        int exitCode = commandLine().execute(args);
        // On success the HTTP server's threads keep the process running.
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static CommandLine commandLine() {
        return new CommandLine(new Main())
                .registerConverter(Visibility.class, lowerCase(Visibility.class))
                .registerConverter(Toggle.class, lowerCase(Toggle.class))
                .registerConverter(Theme.class, lowerCase(Theme.class))
                .registerConverter(Login.Access.class, lowerCase(Login.Access.class))
                .setParameterExceptionHandler(Main::invalidInput);
    }

    /** Reads enum values in any case and names them in lower case, as the help does, when one is wrong. */
    private static <E extends Enum<E>> ITypeConverter<E> lowerCase(Class<E> type) {
        return value -> Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new TypeConversionException(Arrays.stream(type.getEnumConstants())
                        .map(e -> e.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", ", "expected one of ", " but was '" + value + "'"))));
    }

    // The error and where to find help, like git and coreutils; the whole help would push the error off the screen.
    private static int invalidInput(ParameterException e, String[] args) {
        CommandLine command = e.getCommandLine();
        PrintWriter err = command.getErr();
        err.println(command.getColorScheme().errorText(e.getMessage()));
        err.printf(
                "Try '%s --help' for more information.%n",
                command.getCommandSpec().qualifiedName());
        return command.getCommandSpec().exitCodeOnInvalidInput();
    }

    @Override
    public Integer call() throws IOException {
        if (licenses) {
            System.out.print(Licenses.text());
            return 0;
        }
        validate();
        Login login = access == Login.Access.OPEN ? null : login();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (extracted != null) {
                deleteTree(extracted);
            }
        }));
        if (access != Login.Access.PRIVATE) {
            warnAboutVisibility(publicOptions());
        }
        Loaded loaded = load();
        extracted = loaded.extracted();
        if (check) {
            Site site = loaded.sites().members() != null
                    ? loaded.sites().members()
                    : loaded.sites().everyone();
            System.out.print(icuSelfTest());
            System.out.print(imageSelfTest(site.data().database(), site.images()));
            if (login != null) {
                System.out.print(loginSelfTest());
            }
            System.out.printf(
                    "licenses: %d files, %d KB (--licenses)%n",
                    Licenses.files().size(), Licenses.text().length() / 1024);
            return 0;
        }

        WebServer server = new WebServer(loaded.sites(), login);
        server.start(host, port);
        if (map == Toggle.ON) {
            System.out.println("maps: tiles from " + mapOptions().origin()
                    + ", loaded by visitors' browsers (--map=off to turn maps off)");
        }
        if (reload == Toggle.ON) {
            ExportWatcher.start(file, Duration.ofSeconds(reloadInterval), changed -> reload(server));
            System.out.printf("reload: when %s changes (checked every %d s)%n", file.getFileName(), reloadInterval);
        }
        System.out.printf("Listening on http://%s:%d/%n", host, port);
        return 0;
    }

    /** A loaded export ready to serve, and the directory its package was extracted to, if it is one. */
    private record Loaded(Sites sites, Path extracted) {}

    // The directory the media of the package being served were extracted to, removed when replaced or on exit.
    private volatile Path extracted;

    private PrivacyOptions publicOptions() {
        return new PrivacyOptions(living == Visibility.HIDE, privateRecords == Visibility.HIDE);
    }

    private PrivacyOptions membersOptions() {
        return new PrivacyOptions(membersLiving == Visibility.HIDE, membersPrivate == Visibility.HIDE);
    }

    /** The members and how they sign in, printing a summary. */
    private Login login() throws IOException {
        Users members;
        try {
            members = Users.load(users);
        } catch (IOException | IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), "Cannot read " + users + ": " + e.getMessage());
        }
        byte[] key = secretFile != null ? Sessions.keyFile(secretFile) : Sessions.randomKey();
        System.out.printf(
                "login: %s site, members: %d in %s; %s%n",
                access.name().toLowerCase(Locale.ROOT),
                members.size(),
                users,
                secretFile != null
                        ? "sessions signed with the key in " + secretFile
                        : "sessions end when the server stops (see --secret-file)");
        if (members.size() == 0) {
            System.err.println(
                    "WARNING: nobody can sign in yet; add members with kwatern passwd --users " + users + " NAME");
        }
        if (access == Login.Access.MEMBERS && publicOptions().equals(membersOptions())) {
            System.err.println("WARNING: members see the same as everyone; see --members-living and --members-private");
        }
        return new Login(access, members, new Sessions(key, members), behindProxy);
    }

    /** Reads the export, applies the privacy filter for each view and prepares their media, printing a summary. */
    private Loaded load() throws IOException {
        long start = System.nanoTime();
        boolean isPackage = GrampsPackage.isPackage(file);
        ParseResult result = isPackage ? GrampsPackage.read(file) : GrampsXml.read(file);
        GrampsDatabase full = result.database();
        var rules = AliveRules.DEFAULTS.withMaxAge(maxAge);
        var alive = new ProbablyAlive(full, rules, LocalDate.now().getYear());
        PublicDatabase everyone =
                access == Login.Access.PRIVATE ? null : PrivacyFilter.apply(full, alive, publicOptions());
        PublicDatabase members =
                access == Login.Access.OPEN ? null : PrivacyFilter.apply(full, alive, membersOptions());
        long loadMillis = (System.nanoTime() - start) / 1_000_000;

        result.warnings().forEach(w -> System.err.println("warning: " + w));
        System.out.print(summary(members != null ? members.database() : everyone.database(), loadMillis));
        if (everyone != null) {
            System.out.print(privacySummary(members != null ? "public view" : null, full, everyone, publicOptions()));
        }
        if (members != null) {
            System.out.print(privacySummary("members' view", full, members, membersOptions()));
        }
        Path directory = null;
        try {
            if (isPackage) {
                directory = extract(everyone, members);
            }
            Path media = directory;
            String exportMediaPath = expandHome(full.header().mediaPath());
            Function<PublicDatabase, Site> site = published -> published == null
                    ? null
                    : new Site(
                            published,
                            siteOptions(),
                            isPackage
                                    ? new MediaImages(published.database(), media, null, ImageDecoders.DEFAULT)
                                    : new MediaImages(
                                            published.database(),
                                            mediaDir(exportMediaPath),
                                            exportMediaPath,
                                            ImageDecoders.DEFAULT));
            return new Loaded(new Sites(site.apply(everyone), site.apply(members)), directory);
        } catch (IOException | RuntimeException e) {
            if (directory != null) {
                deleteTree(directory);
            }
            throw e;
        }
    }

    private Site.Options siteOptions() {
        return new Site.Options(
                language,
                genderColours == Toggle.ON,
                theme == Theme.AUTO ? null : theme.name().toLowerCase(Locale.ROOT),
                mapOptions(),
                sourceUrl);
    }

    /**
     * Loads the changed export and serves it from the next request on. If it cannot be loaded, for example
     * while only part of it has been copied, the previous version stays.
     */
    private void reload(WebServer server) {
        System.out.println("reload: " + file.getFileName() + " changed, loading it");
        try {
            Loaded loaded = load();
            server.replace(loaded.sites());
            Path previous = extracted;
            extracted = loaded.extracted();
            if (previous != null) {
                deleteTree(previous);
            }
            System.out.println("reload: serving the new version");
        } catch (IOException | RuntimeException e) {
            System.err.println("reload: cannot load " + file.getFileName() + ", keeping the previous version: " + e);
        }
    }

    private void validate() {
        if (!mapTiles.matches("https?://[^/]+/.*")
                || !mapTiles.contains("{z}")
                || !mapTiles.contains("{x}")
                || !mapTiles.contains("{y}")) {
            throw new ParameterException(
                    spec.commandLine(), "--map-tiles must be an http(s) URL with {z}, {x} and {y}: " + mapTiles);
        }
        if (sourceUrl != null && !sourceUrl.matches("https?://[^/\\s]+(/\\S*)?")) {
            throw new ParameterException(spec.commandLine(), "--source-url must be an http(s) URL: " + sourceUrl);
        }
        if (file == null) {
            throw new ParameterException(spec.commandLine(), "Missing the export: kwatern EXPORT");
        }
        if (!Files.isRegularFile(file)) {
            throw new ParameterException(spec.commandLine(), "Not a file: " + file);
        }
        validateLogin();
        if (port < 0 || port > 65535) {
            throw new ParameterException(spec.commandLine(), "--port must be between 0 and 65535");
        }
        if (mediaDir != null && !Files.isDirectory(mediaDir)) {
            throw new ParameterException(spec.commandLine(), "Not a directory: " + mediaDir);
        }
        if (reloadInterval < 1) {
            throw new ParameterException(spec.commandLine(), "--reload-interval must be at least 1 second");
        }
        if (maxAge < 1) {
            throw new ParameterException(spec.commandLine(), "--max-age must be positive");
        }
    }

    private void validateLogin() {
        if (access == Login.Access.OPEN) {
            if (users != null || secretFile != null) {
                throw new ParameterException(
                        spec.commandLine(), "--users and --secret-file need --access=members or --access=private");
            }
            return;
        }
        if (users == null) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--access=" + access.name().toLowerCase(Locale.ROOT)
                            + " needs --users FILE; add members to it with kwatern passwd --users FILE NAME");
        }
        if (!Files.isRegularFile(users)) {
            throw new ParameterException(
                    spec.commandLine(),
                    "Not a file: " + users + "; add members with kwatern passwd --users " + users + " NAME");
        }
        if (access == Login.Access.MEMBERS
                && ((living == Visibility.SHOW && membersLiving == Visibility.HIDE)
                        || (privateRecords == Visibility.SHOW && membersPrivate == Visibility.HIDE))) {
            throw new ParameterException(
                    spec.commandLine(),
                    "Members must see at least what everyone sees: check --members-living and" + " --members-private");
        }
        if (!behindProxy && !allowInsecureLogin && !loopback(host)) {
            throw new ParameterException(
                    spec.commandLine(),
                    "Signing in over plain HTTP sends passwords that can be read on the way. Put a reverse proxy"
                            + " with HTTPS in front of this server and use --behind-proxy, or accept the risk with"
                            + " --allow-insecure-login.");
        }
    }

    private static boolean loopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * Extracts the media files of a package that are published in either view, never the others, and returns
     * the directory.
     */
    private Path extract(PublicDatabase... views) throws IOException {
        if (mediaDir != null) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--media-dir does not apply to a package, which holds its media; see --extract-dir");
        }
        // A new directory for every load, so that a reload never changes the files being served.
        Path directory = extractDir != null
                ? Files.createTempDirectory(Files.createDirectories(extractDir), "kwatern-media")
                : Files.createTempDirectory("kwatern-media");
        Set<String> names = Arrays.stream(views)
                .filter(Objects::nonNull)
                .flatMap(view -> view.database().media().all().stream())
                .map(m -> GrampsPackage.archiveName(m.path()))
                .collect(Collectors.toSet());
        long start = System.nanoTime();
        int count = GrampsPackage.extract(file, directory, names);
        System.out.printf(
                "package: extracted %d of %d published media files to %s in %d ms%n",
                count, names.size(), directory, (System.nanoTime() - start) / 1_000_000);
        return directory;
    }

    private static void deleteTree(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Left for the system to clean up.
                }
            });
        } catch (IOException e) {
            // Left for the system to clean up.
        }
    }

    private Site.MapOptions mapOptions() {
        if (map == Toggle.OFF) {
            return Site.MapOptions.OFF;
        }
        String attribution = mapAttribution != null
                ? mapAttribution
                : mapTiles.equals(Site.MapOptions.OSM_TILES) ? Site.MapOptions.OSM_ATTRIBUTION : "";
        return new Site.MapOptions(true, mapTiles, attribution);
    }

    private static String expandHome(String path) {
        if (path != null && (path.equals("~") || path.startsWith("~/"))) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private Path mediaDir(String exportMediaPath) {
        if (mediaDir != null) {
            return mediaDir;
        }
        Path exportDir = file.toAbsolutePath().getParent();
        if (exportMediaPath == null || exportMediaPath.isBlank()) {
            return exportDir;
        }
        return exportDir.resolve(exportMediaPath);
    }

    /** Checks a password and a session, to show that the JDK's cryptography works in this build. */
    private static String loginSelfTest() {
        long start = System.nanoTime();
        char[] password = "a password".toCharArray();
        String hash = PasswordHash.hash(password);
        boolean checks = PasswordHash.verify(password, hash) && !PasswordHash.verify("another".toCharArray(), hash);
        long millis = (System.nanoTime() - start) / 2_000_000;
        var sessions = new Sessions(Sessions.randomKey(), Users.of(Map.of("someone", hash)));
        boolean signs = sessions.check(sessions.issue("someone", true)) != null;
        return "login: password check %s, %d ms each; sessions %s%n"
                .formatted(checks ? "works" : "FAILS", millis, signs ? "work" : "FAIL");
    }

    @Command(
            name = "passwd",
            mixinStandardHelpOptions = true,
            description = "Sets a member's password in the users file, adding the member if new. Reads the password"
                    + " twice from the terminal, or once from standard input.")
    static final class Passwd implements Callable<Integer> {

        private static final int MIN_LENGTH = 8;

        @Spec
        private CommandSpec spec;

        @Option(
                names = "--users",
                paramLabel = "FILE",
                required = true,
                description = "The users file; created if missing, readable by its owner only.")
        private Path file;

        @Parameters(
                paramLabel = "NAME",
                description = "The member's name for signing in: letters, digits and . _ @ -, at most 64.")
        private String name;

        @Override
        public Integer call() throws IOException {
            if (!Users.validName(name)) {
                throw new ParameterException(
                        spec.commandLine(), "A name has only letters, digits and . _ @ -, at most 64: " + name);
            }
            char[] password = readPassword();
            try {
                if (password.length < MIN_LENGTH) {
                    throw new ParameterException(
                            spec.commandLine(), "A password needs at least " + MIN_LENGTH + " characters");
                }
                Users.put(file, name, PasswordHash.hash(password));
            } finally {
                Arrays.fill(password, '\0');
            }
            System.out.printf("passwd: set the password of %s in %s%n", name, file);
            return 0;
        }

        private char[] readPassword() throws IOException {
            Console console = System.console();
            if (console != null && console.isTerminal()) {
                char[] first = console.readPassword("Password for %s: ", name);
                char[] second = console.readPassword("Again: ");
                boolean same = first != null && Arrays.equals(first, second);
                if (second != null) {
                    Arrays.fill(second, '\0');
                }
                if (!same) {
                    throw new ParameterException(spec.commandLine(), "The two passwords differ");
                }
                return first;
            }
            String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            return line == null ? new char[0] : line.toCharArray();
        }
    }

    /** Counts the media files that can be read and makes one thumbnail, to show that images work in this build. */
    private static String imageSelfTest(GrampsDatabase db, MediaImages images) {
        long readable = db.media().all().stream().filter(images::readable).count();
        long start = System.nanoTime();
        int size = db.media().all().stream()
                .filter(images::readable)
                .findFirst()
                .flatMap(images::thumbnail)
                .map(jpeg -> jpeg.data().length)
                .orElse(0);
        return "media: %d of %d files readable; first thumbnail %d bytes in %d ms%n"
                .formatted(readable, db.media().size(), size, (System.nanoTime() - start) / 1_000_000);
    }

    private static void warnAboutVisibility(PrivacyOptions options) {
        if (!options.hideLiving() || !options.hidePrivate()) {
            String what = !options.hideLiving() && !options.hidePrivate()
                    ? "living people and private records"
                    : !options.hideLiving() ? "living people" : "private records";
            System.err.println("WARNING: " + what + " are visible to everyone who can open this site.");
        }
    }

    /** @param view which view it is about, or {@code null} if there is only one */
    private static String privacySummary(
            String view, GrampsDatabase full, PublicDatabase published, PrivacyOptions options) {
        long all = full.objects().count();
        long withheld = all - published.database().objects().count();
        return "privacy%s: living people %s (%d), private records %s; %d of %d objects withheld%n"
                .formatted(
                        view == null ? "" : " (" + view + ")",
                        options.hideLiving() ? "hidden" : "shown",
                        published.living().size(),
                        options.hidePrivate() ? "hidden" : "shown",
                        withheld,
                        all);
    }

    private static String summary(GrampsDatabase db, long loadMillis) {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return """
                Gramps XML %s, exported %s by Gramps %s
                people %d, families %d, events %d, places %d
                sources %d, citations %d, repositories %d, media %d, notes %d
                loaded in %d ms, heap in use %d MB
                """.formatted(
                        db.schemaVersion(),
                        db.header().created(),
                        db.header().grampsVersion(),
                        db.people().size(),
                        db.families().size(),
                        db.events().size(),
                        db.places().size(),
                        db.sources().size(),
                        db.citations().size(),
                        db.repositories().size(),
                        db.media().size(),
                        db.notes().size(),
                        loadMillis,
                        usedMb);
    }

    /** Formats fixed dates and sorts names in a few locales, to show that ICU works in this build. */
    private static String icuSelfTest() {
        var sample = new GrampsDate(
                GrampsDate.Modifier.ABOUT,
                GrampsDate.Quality.REGULAR,
                GrampsCalendar.GREGORIAN,
                new DateValue(1850, 3, 12),
                null,
                null,
                null,
                false);
        var hebrew = new GrampsDate(
                GrampsDate.Modifier.NONE,
                GrampsDate.Quality.REGULAR,
                GrampsCalendar.HEBREW,
                new DateValue(5770, 1, 1),
                null,
                null,
                null,
                false);
        var sb = new StringBuilder("date formatting:");
        for (String tag : new String[] {"en", "cs", "de", "sk", "fr"}) {
            sb.append(" ")
                    .append(tag)
                    .append(": ")
                    .append(new DateFormatter(Locale.forLanguageTag(tag)).format(sample))
                    .append(" |");
        }
        sb.append(" ").append(new DateFormatter(Locale.ENGLISH).format(hebrew)).append("\n");
        var surnames = new ArrayList<>(List.of("Chalupa", "Hrubý", "Cibulka", "Černý"));
        surnames.sort(new NameOrder(Locale.of("cs"), List.of()).strings());
        sb.append("sorting (cs): ").append(String.join(", ", surnames)).append("\n");
        sb.append("numbers: en ")
                .append(Numbers.decimal(39.0483, 4, Locale.ENGLISH))
                .append(" | cs ")
                .append(Numbers.decimal(39.0483, 4, Locale.of("cs")))
                .append("\n");
        // Pages are made only in these; with none, every visitor would get --language.
        sb.append("page languages: ").append(Languages.count()).append(" with ICU locale data\n");
        return sb.toString();
    }
}
