package me.hejl.kwatern.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import me.hejl.gramps.date.DateFormatter;
import me.hejl.gramps.i18n.Messages;
import me.hejl.gramps.model.Gender;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.GrampsDate;
import me.hejl.gramps.name.NameFormatter;
import me.hejl.gramps.name.NameOrder;
import me.hejl.gramps.place.PlaceFormatter;

/**
 * Everything language-dependent for one page language: translated texts and the formatters for dates,
 * names and places. Passed to every template as {@code ui}. Thread-safe.
 */
public final class Ui {

    // Characters of a Gramps type that message keys have as "_"; used for every type on every page.
    private static final Pattern NOT_IN_KEYS = Pattern.compile("[^a-z0-9]+");

    private final Locale locale;
    private final Messages messages;
    private final DateFormatter dates;
    private final NameFormatter names;
    private final NameOrder order;
    private final PlaceFormatter places;
    private final Site.Options options;
    private final boolean hasMedia;
    private final boolean hasMap;
    private final Map<String, Object> pages;
    private final Viewer viewer;

    /**
     * @param hasMedia whether the tree has any media
     * @param hasMap   whether maps are shown: turned on, and the tree has places with coordinates
     */
    Ui(Locale locale, GrampsDatabase db, Site.Options options, boolean hasMedia, boolean hasMap) {
        this.locale = locale;
        this.options = options;
        this.hasMedia = hasMedia;
        this.hasMap = hasMap;
        this.messages = Messages.load("me/hejl/kwatern/messages", locale);
        this.dates = new DateFormatter(locale);
        this.names = new NameFormatter(db.nameFormats(), NameFormatter.SURNAME_GIVEN, locale);
        this.order = new NameOrder(locale, db.nameMaps());
        this.places = new PlaceFormatter(db, locale.getLanguage());
        this.pages = new ConcurrentHashMap<>();
        this.viewer = Viewer.OPEN;
    }

    private Ui(Ui ui, Viewer viewer) {
        this.locale = ui.locale;
        this.options = ui.options;
        this.hasMedia = ui.hasMedia;
        this.hasMap = ui.hasMap;
        this.messages = ui.messages;
        this.dates = ui.dates;
        this.names = ui.names;
        this.order = ui.order;
        this.places = ui.places;
        this.pages = ui.pages;
        this.viewer = viewer;
    }

    /**
     * A page that is the same on every request in this language and version of the tree, such as a listing of the
     * whole tree: made on first request and kept. Only for pages with a fixed, small set of keys, as they are
     * kept for good; the page model must not depend on who views it.
     */
    @SuppressWarnings("unchecked")
    public <T> T page(String key, Supplier<T> make) {
        Object page = pages.get(key);
        if (page == null) {
            // Two requests at once may both make it; both get the same.
            Object made = make.get();
            page = pages.putIfAbsent(key, made);
            if (page == null) {
                page = made;
            }
        }
        return (T) page;
    }

    /** The same UI for one request's viewer; cheap, it shares everything else. */
    public Ui with(Viewer viewer) {
        return new Ui(this, viewer);
    }

    public Viewer viewer() {
        return viewer;
    }

    /** The language tag for the page's {@code lang} attribute. */
    public String language() {
        return locale.toLanguageTag();
    }

    public Locale locale() {
        return locale;
    }

    /** A translated text. */
    public String t(String key) {
        return messages.get(key);
    }

    /** A translated text with arguments; numbers are grouped as the language does, so pass years as strings. */
    public String t(String key, Object... args) {
        return messages.format(key, args);
    }

    /**
     * A translated text that may depend on the grammatical gender of the person it is about, as verbs do in
     * Czech ("narozen", "narozena"): uses {@code key.female} or {@code key.male} where a language defines
     * it, else {@code key}.
     */
    public String t(String key, Gender gender, Object... args) {
        String specific = key + "." + gender.name().toLowerCase(Locale.ROOT);
        return messages.find(specific) != null ? messages.format(specific, args) : messages.format(key, args);
    }

    /** The parts that are not empty, for templates that lay them out one by one. */
    public List<String> parts(String... parts) {
        return Arrays.stream(parts).filter(p -> p != null && !p.isEmpty()).toList();
    }

    /** Joins the non-empty parts with " · ", for compact lines such as "Wife · 1852–1921". */
    public String join(String... parts) {
        return String.join(
                " · ",
                Arrays.stream(parts).filter(p -> p != null && !p.isEmpty()).toList());
    }

    /** Whether the tree has any media, for the Photos link in the header. */
    public boolean hasMedia() {
        return hasMedia;
    }

    /** Whether maps are shown: turned on, and the tree has places with coordinates. */
    public boolean hasMap() {
        return hasMap;
    }

    /** Where the source code is, for the footer link; {@code null} for none. See {@code --source-url}. */
    public String sourceUrl() {
        return options.sourceUrl();
    }

    public Site.MapOptions map() {
        return options.map();
    }

    /** Whether people are marked by gender with colour; see {@code --gender-colours}. */
    public boolean genderColours() {
        return options.genderColours();
    }

    /** {@code light} or {@code dark} when the site forces a theme, else {@code null}; see {@code --theme}. */
    public String theme() {
        return options.theme();
    }

    public String date(GrampsDate date) {
        return dates.format(date);
    }

    public NameFormatter names() {
        return names;
    }

    public NameOrder order() {
        return order;
    }

    public PlaceFormatter places() {
        return places;
    }

    /**
     * Translates a Gramps type such as an event type ("Birth") or a role ("Primary"). Gramps writes its
     * standard types in English; custom types are shown as entered.
     *
     * @param kind message key prefix, e.g. {@code event}
     */
    public String type(String kind, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String key = kind + "."
                + NOT_IN_KEYS.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll("_");
        String translated = messages.find(key);
        return translated != null ? translated : value;
    }
}
