package me.hejl.kwatern.view;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.hejl.kwatern.web.Site;

/**
 * Addresses of other websites in an export. An export may come from someone else, so only http and https
 * addresses and plain email addresses become links; anything else, such as {@code javascript:} or
 * {@code file:}, stays text.
 */
final class WebLinks {

    /** Shortened addresses keep this many characters, enough for the site and the start of the path. */
    static final int SHORT = 32;

    // An address typed in text: with a scheme or starting with www., up to white space or a character that cannot
    // be part of one. Gramps' note editor also takes www. without a scheme (gui/widgets/styledtexteditor.py).
    // Email addresses in text are not linked: they often belong to living researchers.
    private static final Pattern TYPED = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"]+");
    private static final Pattern WEB = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern WWW = Pattern.compile("(?i)www\\.\\S+");
    // Characters that end a sentence rather than an address, e.g. "see www.example.org."
    private static final String TRAILING = ".,;:!?'\"*";

    private WebLinks() {}

    /** An address typed in a text, at UTF-16 offsets {@code [start, end)}, and the URL to link to. */
    record Found(int start, int end, String url) {}

    /** The URL to link to for an address entered in Gramps, or {@code null} if it is not one to link to. */
    static String url(String address) {
        if (address == null) {
            return null;
        }
        String text = address.strip();
        if (WEB.matcher(text).matches()) {
            return hasHost(text) ? text : null;
        }
        if (WWW.matcher(text).matches()) {
            return hasHost("https://" + text) ? "https://" + text : null;
        }
        if (text.regionMatches(true, 0, "mailto:", 0, 7)) {
            return Site.mailto(text.substring(7));
        }
        return Site.mailto(text);
    }

    private static boolean hasHost(String url) {
        try {
            String host = new URI(url).getHost();
            return host != null && !host.isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The addresses typed in a text that can be linked to. */
    static List<Found> find(String text) {
        List<Found> found = new ArrayList<>();
        Matcher matcher = TYPED.matcher(text);
        while (matcher.find()) {
            int end = trim(text, matcher.start(), matcher.end());
            String url = url(text.substring(matcher.start(), end));
            if (url != null) {
                found.add(new Found(matcher.start(), end, url));
            }
        }
        return found;
    }

    /** Leaves out punctuation after an address, and a closing bracket it does not open, e.g. "(see www.x.org)." */
    private static int trim(String text, int start, int end) {
        while (end > start) {
            char last = text.charAt(end - 1);
            if (TRAILING.indexOf(last) >= 0) {
                end--;
            } else if ((last == ')' || last == ']') && unbalanced(text, start, end, last == ')' ? '(' : '[', last)) {
                end--;
            } else {
                break;
            }
        }
        return end;
    }

    private static boolean unbalanced(String text, int start, int end, char open, char close) {
        int depth = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == open) {
                depth++;
            } else if (text.charAt(i) == close) {
                depth--;
            }
        }
        return depth < 0;
    }

    /**
     * An address as it is read, without the scheme, "www." and a final slash, and cut after {@link #SHORT}
     * characters: "familysearch.org/ark:/61903/1:1:M…".
     */
    static String shorten(String url) {
        String text = url.replaceFirst("(?i)^(https?://|mailto:)", "").replaceFirst("(?i)^www\\.", "");
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.codePointCount(0, text.length()) > SHORT) {
            text = text.substring(0, text.offsetByCodePoints(0, SHORT)) + "…";
        }
        return text;
    }

    /** The site of a web address without "www.", such as "familysearch.org", or the address of an email link. */
    static String site(String url) {
        if (url.regionMatches(true, 0, "mailto:", 0, 7)) {
            return url.substring(7);
        }
        try {
            String host = new URI(url).getHost().toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            return shorten(url);
        }
    }
}
