package me.hejl.kwatern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.PrivacyOptions;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsPackage;
import me.hejl.gramps.xml.GrampsParseException;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.gramps.xml.ParseResult;
import me.hejl.image.ImageDecoders;
import me.hejl.kwatern.auth.Grants;
import me.hejl.kwatern.auth.Login;
import me.hejl.kwatern.web.MediaImages;
import me.hejl.kwatern.web.Site;
import me.hejl.kwatern.web.Sites;
import me.hejl.kwatern.web.Version;

/**
 * Loads versions of the export, at start and on every reload: reads it, applies the privacy filter for each view,
 * extracts a package's published media and prepares the images, printing a summary. Thread-safe.
 */
final class Loader {

    /**
     * What to load and how, from the command line.
     *
     * @param mediaDir  the media directory given, or {@code null} for the export's own media path
     * @param extractDir where to extract a package's media, or {@code null} for the system's temporary directory
     */
    record Settings(
            Path file,
            int maxAge,
            Login.Access access,
            PrivacyOptions publicOptions,
            Path mediaDir,
            boolean allowMediaAnywhere,
            Path extractDir,
            Site.Options site) {}

    /**
     * The export could not be loaded. The message says why without quoting the export: its file names and contents
     * hold data of the tree, and so may the messages of exceptions about them. Only a parse error's message, which
     * says where and not what, is kept; otherwise the exception's type.
     */
    static final class LoadException extends IOException {
        LoadException(Path file, Exception cause) {
            super(
                    "cannot load " + file.getFileName() + ": "
                            + (cause instanceof GrampsParseException
                                    ? cause.getMessage()
                                    : cause.getClass().getSimpleName()),
                    cause);
        }

        /** @param why a reason that holds nothing of the export */
        LoadException(Path file, String why) {
            super("cannot load " + file.getFileName() + ": " + why);
        }
    }

    private final Settings settings;
    // The directories that media of packages were extracted to and that still exist, removed on exit.
    private final Set<Path> extracted = ConcurrentHashMap.newKeySet();

    Loader(Settings settings) {
        this.settings = settings;
    }

    /**
     * Loads the export as it is now. A package's media are extracted to a new directory, removed when the version
     * has been replaced and no request uses it any more, or on exit.
     */
    Version load() throws LoadException {
        try {
            return read();
        } catch (LoadException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new LoadException(settings.file(), e);
        }
    }

    /** Removes what the versions still left behind, on exit. */
    void close() {
        extracted.forEach(this::remove);
    }

    private Version read() throws IOException {
        Path file = settings.file();
        long start = System.nanoTime();
        boolean isPackage = GrampsPackage.isPackage(file);
        if (isPackage && (settings.mediaDir() != null || settings.allowMediaAnywhere())) {
            // Checked at start already; the file may have become a package since.
            throw new LoadException(
                    file,
                    "a package holds its media, so --media-dir and --allow-media-anywhere" + " do not apply to it");
        }
        ParseResult result = isPackage ? GrampsPackage.read(file) : GrampsXml.read(file);
        GrampsDatabase full = result.database();
        var rules = AliveRules.DEFAULTS.withMaxAge(settings.maxAge());
        var alive = new ProbablyAlive(full, rules, LocalDate.now().getYear());
        // What each view shows: everyone's (unless the site is private) and each grants'. Views that show the same
        // are filtered once and served as one site.
        PrivacyOptions publicOptions = settings.access() == Login.Access.PRIVATE ? null : settings.publicOptions();
        Map<Grants, PrivacyOptions> memberOptions = new LinkedHashMap<>();
        if (settings.access() != Login.Access.OPEN) {
            Grants.ALL.forEach(grants -> memberOptions.put(grants, membersOptions(grants)));
        }
        Map<PrivacyOptions, PublicDatabase> views = new LinkedHashMap<>();
        Stream.concat(Stream.ofNullable(publicOptions), memberOptions.values().stream())
                .forEach(options -> views.computeIfAbsent(options, o -> PrivacyFilter.apply(full, alive, o)));
        // It has all the others have: a member sees no less than everyone, and the most when granted everything.
        PrivacyOptions widestOptions = memberOptions.isEmpty() ? publicOptions : memberOptions.get(Grants.EVERYTHING);
        PublicDatabase widest = views.get(widestOptions);
        long loadMillis = (System.nanoTime() - start) / 1_000_000;

        result.warnings().forEach(w -> System.err.println("warning: " + w));
        System.out.print(summary(widest.database(), loadMillis));
        String contact = settings.site().contact();
        if (Site.Options.RESEARCHER.equals(contact) && Site.contactUrl(contact, full.header()) == null) {
            System.err.println("WARNING: the export has no researcher email for --contact=researcher, so pages have"
                    + " no contact link; set it in Gramps under Preferences, Researcher");
        }
        views.forEach((options, published) -> {
            List<String> who = new ArrayList<>();
            if (options.equals(publicOptions)) {
                who.add("public view");
            }
            memberOptions.forEach((grants, o) -> {
                if (o.equals(options)) {
                    who.add("members granted " + grants.describe());
                }
            });
            System.out.print(
                    privacySummary(memberOptions.isEmpty() ? null : String.join(", ", who), full, published, options));
        });
        Path directory = null;
        try {
            if (isPackage) {
                directory = extract(widest);
            }
            String exportMediaPath = expandHome(full.header().mediaPath());
            // One set of images for all views, each reaching only its own media: they are made once.
            MediaImages images = mediaImages(widest, isPackage, directory, exportMediaPath);
            Map<PrivacyOptions, Site> sites = new HashMap<>();
            views.forEach((options, published) -> sites.put(
                    options,
                    new Site(
                            published,
                            settings.site(),
                            options.equals(widestOptions) ? images : images.forView(published.database()))));
            Map<Grants, Site> members = new HashMap<>();
            memberOptions.forEach((grants, options) -> members.put(grants, sites.get(options)));
            var version = new Sites(publicOptions == null ? null : sites.get(publicOptions), members);
            long outside = version.widest().images().outsideMediaDir();
            if (outside > 0) {
                // The media path an export sets is not printed: it may hold a name, as in /home/novak/Family tree.
                String where = settings.mediaDir() != null
                        ? settings.mediaDir().toString()
                        : exportMediaPath == null || exportMediaPath.isBlank()
                                ? mediaDir(exportMediaPath).toString()
                                : "that the export sets";
                System.err.printf(
                        "WARNING: %s outside the media directory %s and not served;"
                                + " see --media-dir and --allow-media-anywhere%n",
                        outside == 1 ? "1 published media file is" : outside + " published media files are", where);
            }
            Path media = directory;
            return new Version(version, media == null ? () -> {} : () -> remove(media));
        } catch (IOException | RuntimeException e) {
            if (directory != null) {
                remove(directory);
            }
            throw e;
        }
    }

    /** The media of a view: from the package's extraction directory, else from the media directory or anywhere. */
    private MediaImages mediaImages(
            PublicDatabase published, boolean isPackage, Path extracted, String exportMediaPath) {
        if (isPackage) {
            return MediaImages.ofPackage(published.database(), extracted, ImageDecoders.DEFAULT);
        }
        Path dir = mediaDir(exportMediaPath);
        return settings.allowMediaAnywhere()
                ? MediaImages.anywhere(published.database(), dir, exportMediaPath, ImageDecoders.DEFAULT)
                : new MediaImages(published.database(), dir, exportMediaPath, ImageDecoders.DEFAULT);
    }

    /**
     * What members with these grants see: what everyone sees and what they are granted. On a private site, where
     * nobody else sees anything, what they are granted beyond the safe defaults.
     */
    private PrivacyOptions membersOptions(Grants grants) {
        return grants.over(
                settings.access() == Login.Access.PRIVATE ? PrivacyOptions.DEFAULT : settings.publicOptions());
    }

    /**
     * Extracts the media files of a package that are published in the widest view, never the others, and returns
     * the directory.
     */
    private Path extract(PublicDatabase widest) throws IOException {
        // A new directory for every load, so that a reload never changes the files being served.
        Path directory = settings.extractDir() != null
                ? Files.createTempDirectory(Files.createDirectories(settings.extractDir()), "kwatern-media")
                : Files.createTempDirectory("kwatern-media");
        extracted.add(directory);
        Set<String> names = widest.database().media().all().stream()
                .map(m -> GrampsPackage.archiveName(m.path()))
                .collect(Collectors.toSet());
        long start = System.nanoTime();
        GrampsPackage.Extraction extraction = GrampsPackage.extract(settings.file(), directory, names);
        System.out.printf(
                "package: extracted %d of %d published media files to %s in %d ms%n",
                extraction.extracted(), names.size(), directory, (System.nanoTime() - start) / 1_000_000);
        if (extraction.noSpace() > 0) {
            System.err.printf(
                    "WARNING: %s not extracted, as extracting would leave less than %d MB free in %s;"
                            + " see --extract-dir%n",
                    extraction.noSpace() == 1 ? "1 media file is" : extraction.noSpace() + " media files are",
                    GrampsPackage.KEEP_FREE >> 20,
                    directory);
        }
        return directory;
    }

    private void remove(Path directory) {
        extracted.remove(directory);
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

    private static String expandHome(String path) {
        if (path != null && (path.equals("~") || path.startsWith("~/"))) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    private Path mediaDir(String exportMediaPath) {
        if (settings.mediaDir() != null) {
            return settings.mediaDir();
        }
        Path exportDir = settings.file().toAbsolutePath().getParent();
        if (exportMediaPath == null || exportMediaPath.isBlank()) {
            return exportDir;
        }
        return exportDir.resolve(exportMediaPath);
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
}
