package me.hejl.kwatern.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.Version;
import me.hejl.kwatern.view.SearchIndex;

/** The published data and a {@link Ui} per page language. Thread-safe. */
public final class Site {

    /** The program and its version, shown in the page footer and by {@code --version}. */
    public static final String PROGRAM = "Kwatern " + Version.VERSION;

    /**
     * How the site looks.
     *
     * @param defaultLanguage page language when the browser asks for none
     * @param genderColours   whether to mark people by gender with colour
     * @param theme           {@code light} or {@code dark} to force one, {@code null} to follow the visitor's system
     * @param map             maps and their tiles
     * @param sourceUrl       where visitors get the source code of this program, as the AGPL asks; {@code null} for
     *                        no link
     */
    public record Options(
            String defaultLanguage, boolean genderColours, String theme, MapOptions map, String sourceUrl) {

        public static final Options DEFAULT = new Options("en", true, null, MapOptions.OPENSTREETMAP, null);
    }

    /**
     * Maps: whether pages show them, and where the browser loads the map images (tiles) from.
     *
     * @param tiles       a Leaflet tile URL template with {z}, {x} and {y}, and optionally {s} for subdomains
     * @param attribution the credit the tile provider requires, as HTML
     */
    public record MapOptions(boolean enabled, String tiles, String attribution) {

        public static final String OSM_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
        public static final String OSM_ATTRIBUTION =
                "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";
        public static final MapOptions OPENSTREETMAP = new MapOptions(true, OSM_TILES, OSM_ATTRIBUTION);
        public static final MapOptions OFF = new MapOptions(false, OSM_TILES, OSM_ATTRIBUTION);

        /** The origin of the tiles for the Content Security Policy, e.g. {@code https://*.tile.example.org}. */
        public String origin() {
            int scheme = tiles.indexOf("://");
            int path = tiles.indexOf('/', scheme + 3);
            return tiles.substring(0, path < 0 ? tiles.length() : path).replace("{s}", "*");
        }
    }

    private final PublicDatabase data;
    private final Options options;
    private final SearchIndex search;
    private final MediaImages images;
    private final Map<String, Ui> uis = new ConcurrentHashMap<>();

    /** A site without images. */
    public Site(PublicDatabase data, Options options) {
        this(data, options, MediaImages.none(data.database()));
    }

    public Site(PublicDatabase data, Options options, MediaImages images) {
        this.data = data;
        this.options = options;
        this.search = new SearchIndex(data);
        this.images = images;
    }

    public MediaImages images() {
        return images;
    }

    public Options options() {
        return options;
    }

    public SearchIndex search() {
        return search;
    }

    public PublicDatabase data() {
        return data;
    }

    /**
     * The UI for a request: the {@code lang} query parameter if given, else the browser's preferred
     * language from {@code Accept-Language}, else the default language.
     */
    public Ui ui(String langParameter, String acceptLanguage) {
        String language = language(langParameter);
        if (language == null && acceptLanguage != null) {
            try {
                List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
                for (Locale.LanguageRange range : ranges) {
                    language = language(range.getRange());
                    if (language != null) {
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                // a malformed header just means no preference
            }
        }
        if (language == null) {
            language = options.defaultLanguage();
        }
        return uis.computeIfAbsent(language, l -> new Ui(Locale.of(l), data.database(), options));
    }

    /** The primary language subtag if it looks valid, else {@code null}; keeps the cache small. */
    private static String language(String tag) {
        if (tag == null || tag.isBlank() || tag.equals("*")) {
            return null;
        }
        String language = Locale.forLanguageTag(tag.strip()).getLanguage();
        return language.matches("[a-z]{2,3}") ? language : null;
    }
}
