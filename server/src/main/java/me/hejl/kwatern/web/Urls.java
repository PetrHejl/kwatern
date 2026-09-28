package me.hejl.kwatern.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import me.hejl.gramps.model.PrimaryObject;
import me.hejl.kwatern.view.Views;

/** Builds site URLs. Objects are addressed by Gramps ID, or by handle if they have none. */
public final class Urls {

    /**
     * The stylesheet, with a version taken from its content: browsers may keep it for good, and fetch it again
     * as soon as a new build changes it.
     */
    private static final String STYLESHEET = "/static/style.css?v=" + version("/me/hejl/kwatern/static/style.css");

    private Urls() {}

    public static String stylesheet() {
        return STYLESHEET;
    }

    /** The first 12 hex digits of the SHA-256 of a resource. */
    static String version(String resource) {
        try (InputStream in = Urls.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "0";
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (IOException | NoSuchAlgorithmException e) {
            return "0";
        }
    }

    public static String person(PrimaryObject person) {
        return "/person/" + segment(key(person));
    }

    public static String family(PrimaryObject family) {
        return "/family/" + segment(key(family));
    }

    public static String place(PrimaryObject place) {
        return "/place/" + segment(key(place));
    }

    public static String source(PrimaryObject source) {
        return "/source/" + segment(key(source));
    }

    /** The picture of a person, see {@link MediaImages#portrait}. */
    public static String portrait(PrimaryObject person) {
        return "/portrait/" + segment(key(person));
    }

    public static String thumbnail(PrimaryObject media) {
        return "/thumbnail/" + segment(key(media));
    }

    /** The ancestor chart of a person; the defaults leave the URL short. */
    public static String ancestors(PrimaryObject person, int generations, boolean fan) {
        String url = person(person) + "/ancestors";
        List<String> query = new ArrayList<>();
        if (generations != Views.ANCESTOR_GENERATIONS) {
            query.add("generations=" + generations);
        }
        if (fan) {
            query.add("view=fan");
        }
        return query.isEmpty() ? url : url + "?" + String.join("&", query);
    }

    public static String descendants(PrimaryObject person, int generations) {
        String url = person(person) + "/descendants";
        return generations == Views.DESCENDANT_GENERATIONS ? url : url + "?generations=" + generations;
    }

    public static String media(PrimaryObject media) {
        return "/media/" + segment(key(media));
    }

    /** The larger image shown on the media page. */
    public static String mediaImage(PrimaryObject media) {
        return media(media) + "/image";
    }

    /** The file itself. */
    public static String original(PrimaryObject media) {
        return media(media) + "/original";
    }

    public static String personMap(PrimaryObject person, boolean family) {
        return person(person) + "/map" + (family ? "?family=show" : "");
    }

    /** The map of the whole tree; the defaults leave the URL short. */
    public static String treeMap(String kind, String period) {
        List<String> query = new ArrayList<>();
        if (!kind.equals("all")) {
            query.add("kind=" + kind);
        }
        if (!period.equals("all")) {
            query.add("period=" + period);
        }
        return query.isEmpty() ? "/map" : "/map?" + String.join("&", query);
    }

    public static String photos() {
        return "/photos";
    }

    public static String search(String query) {
        return "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    public static String surname(String surname) {
        return "/surname/" + segment(surname);
    }

    private static String key(PrimaryObject object) {
        return object.id() != null ? object.id() : object.handle();
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
