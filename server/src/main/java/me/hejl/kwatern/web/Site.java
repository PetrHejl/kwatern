package me.hejl.kwatern.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import me.hejl.gramps.i18n.Languages;
import me.hejl.gramps.model.Header;
import me.hejl.gramps.place.Coordinates;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.kwatern.Version;
import me.hejl.kwatern.view.SearchIndex;
import me.hejl.kwatern.view.TreeIndex;

/** The published data and a {@link Ui} per page language. Thread-safe. */
public final class Site {

    /** The program and its version, shown in the page footer and by {@code --version}. */
    public static final String PROGRAM = "Kwatern " + Version.VERSION;

    /**
     * Where visitors get the source code, linked in the page footer as the AGPL asks (section 13). Anyone serving a
     * changed version changes it to where their source is.
     */
    public static final String SOURCE_URL = "https://github.com/PetrHejl/kwatern";

    /**
     * How the site looks.
     *
     * @param defaultLanguage page language when the browser asks for none
     * @param genderColours   whether to mark people by gender with colour
     * @param theme           {@code light} or {@code dark} to force one, {@code null} to follow the visitor's system
     * @param map             maps and their tiles
     * @param contact         where visitors reach whoever publishes the tree, linked in the page footer: an http(s) or
     *                        mailto URL, {@link #RESEARCHER} for the researcher's email in the export, or {@code null}
     * @param credit          a line for the page footer, such as where the tree comes from and its license, as HTML;
     *                        {@code null} for none
     */
    public record Options(
            String defaultLanguage,
            boolean genderColours,
            String theme,
            MapOptions map,
            String contact,
            String credit) {

        /** The {@code contact} that links to the email of the researcher named in the export. */
        public static final String RESEARCHER = "researcher";

        public static final Options DEFAULT = new Options("en", true, null, MapOptions.OPENSTREETMAP, null, null);

        public Options(String defaultLanguage, boolean genderColours, String theme, MapOptions map) {
            this(defaultLanguage, genderColours, theme, map, null, null);
        }
    }

    // Plain addresses only: an export may come from someone else, and characters such as "?" would add to the mail.
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+");

    /** A mailto URL for a plain email address, else {@code null}. */
    public static String mailto(String email) {
        return email != null && EMAIL.matcher(email.strip()).matches() ? "mailto:" + email.strip() : null;
    }

    /** The URL of the footer's contact link for an export with this header, or {@code null} for none. */
    public static String contactUrl(String contact, Header header) {
        if (!Options.RESEARCHER.equals(contact)) {
            return contact;
        }
        return header == null || header.researcher() == null
                ? null
                : mailto(header.researcher().email());
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
    private final TreeIndex index;
    private final boolean hasMedia;
    private final boolean hasMap;
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
        this.index = new TreeIndex(data);
        this.hasMedia = data.database().media().size() > 0;
        this.hasMap = options.map().enabled()
                && data.database().places().all().stream()
                        .anyMatch(p ->
                                Coordinates.parse(p.latitude(), p.longitude()).isPresent());
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

    /** Lookups for pages, built once for this version of the tree. */
    public TreeIndex index() {
        return index;
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
        return uis.computeIfAbsent(language, l -> new Ui(Locale.of(l), data.database(), options, hasMedia, hasMap));
    }

    /**
     * The primary language subtag if ICU knows the language, else {@code null}. Only those get a {@link Ui}, since
     * each takes memory for good: any code that merely looks valid would let requests make thousands.
     */
    private static String language(String tag) {
        if (tag == null || tag.isBlank() || tag.equals("*")) {
            return null;
        }
        String language = Locale.forLanguageTag(tag.strip()).getLanguage();
        return Languages.known(language) ? language : null;
    }
}
