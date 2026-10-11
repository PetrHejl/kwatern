package me.hejl.kwatern.view;

import java.lang.Character.UnicodeScript;
import java.net.IDN;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    // Without UNICODE_CHARACTER_CLASS, \s and \S are ASCII-only, and a no-break space would not end an address.
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    // An address typed in text: with a scheme or starting with www., up to white space or a character that cannot
    // be part of one. Gramps' note editor also takes www. without a scheme (gui/widgets/styledtexteditor.py).
    // Email addresses in text are not linked: they often belong to living researchers. Not \b, which with Unicode
    // classes finds no boundary after Chinese or Japanese text, often written without a space before an address.
    // Format characters such as a zero-width space, common in text pasted from web pages, end an address too.
    private static final Pattern TYPED = Pattern.compile("(?<![a-z0-9_])(?:https?://|www\\.)[^\\s<>\"\\p{C}]+", FLAGS);
    // Checked by hand rather than with java.net.URI, which rejects what browsers open: hosts in other scripts
    // (příklad.cz), and |, [ ], { }, ^ or a stray % in the path, all common on archive viewers. No user name
    // before the host, so that the site shown is the one the link opens.
    private static final Pattern WEB = Pattern.compile(
            "https?://(?<host>[\\p{L}\\p{N}\\p{M}-]+(?:\\.[\\p{L}\\p{N}\\p{M}-]+)*|\\[[0-9a-f:.]+])(?::[0-9]{1,5})?"
                    + "(?:[/?#][^\\s\\p{C}]*)?",
            FLAGS);
    private static final Pattern WWW = Pattern.compile("www\\.\\S+", FLAGS);
    // Characters that end a sentence rather than an address, e.g. "see www.example.org."; quotes are found by
    // their Unicode type, so that „www.archiv.cz“ and «www.archiv.cz» lose them too.
    private static final String TRAILING = ".,;:!?'\"*…。、，；：！？";
    // Closing brackets kept at the end of an address when it opens them, as in Great_Falls_(Montana).
    private static final String BRACKETS = "()[]{}（）［］｛｝";
    // Scripts that may share a part of a host, as in ICU's highly restrictive level (UTS #39 5.2): Japanese,
    // Chinese and Korean are written with several, and with Latin.
    private static final List<Set<UnicodeScript>> SCRIPT_MIXES = List.of(
            EnumSet.of(UnicodeScript.LATIN, UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA),
            EnumSet.of(UnicodeScript.LATIN, UnicodeScript.HAN, UnicodeScript.BOPOMOFO),
            EnumSet.of(UnicodeScript.LATIN, UnicodeScript.HAN, UnicodeScript.HANGUL));

    private WebLinks() {}

    /** An address typed in a text, at UTF-16 offsets {@code [start, end)}, and the URL to link to. */
    record Found(int start, int end, String url) {}

    /**
     * The URL to link to for an address entered on Gramps' Internet tab, a web or plain email address, or
     * {@code null} if it is not one to link to.
     */
    static String url(String address) {
        String web = web(address);
        if (web != null || address == null) {
            return web;
        }
        String text = address.strip();
        if (text.regionMatches(true, 0, "mailto:", 0, 7)) {
            return Site.mailto(text.substring(7));
        }
        return Site.mailto(text);
    }

    /** The URL to link to for a web address, with www. opening as https, or {@code null} if it is not one. */
    static String web(String address) {
        if (address == null) {
            return null;
        }
        String text = address.strip();
        if (WWW.matcher(text).matches()) {
            text = "https://" + text;
        }
        Matcher matcher = WEB.matcher(text);
        return matcher.matches() && shownHost(matcher.group("host")) != null ? text : null;
    }

    /**
     * A host as the page shows it: in punycode when a part of it mixes scripts, as "аpple.com" with a Cyrillic а
     * does to pass for another site, or {@code null} when that fails. An export may come from someone else.
     */
    private static String shownHost(String host) {
        for (String label : host.split("\\.")) {
            Set<UnicodeScript> scripts = EnumSet.noneOf(UnicodeScript.class);
            label.codePoints().mapToObj(UnicodeScript::of).forEach(scripts::add);
            scripts.removeAll(EnumSet.of(UnicodeScript.COMMON, UnicodeScript.INHERITED));
            if (scripts.size() > 1 && SCRIPT_MIXES.stream().noneMatch(mix -> mix.containsAll(scripts))) {
                try {
                    return IDN.toASCII(host);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return host;
    }

    /** The addresses typed in a text that can be linked to. */
    static List<Found> find(String text) {
        List<Found> found = new ArrayList<>();
        Matcher matcher = TYPED.matcher(text);
        while (matcher.find()) {
            int end = trim(text, matcher.start(), matcher.end());
            String url = web(text.substring(matcher.start(), end));
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
            int type = Character.getType(last);
            int bracket = BRACKETS.indexOf(last);
            boolean trailing = TRAILING.indexOf(last) >= 0
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || type == Character.FINAL_QUOTE_PUNCTUATION
                    // Opening brackets, and low quotes such as „, which Unicode counts as opening ones.
                    || type == Character.START_PUNCTUATION
                    || (type == Character.END_PUNCTUATION
                            && (bracket < 0 || unbalanced(text, start, end, BRACKETS.charAt(bracket - 1), last)));
            if (!trailing) {
                break;
            }
            end--;
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

    /** A web address as typed, with or without a scheme, and its host as the page shows it. */
    static String readable(String address) {
        String scheme = WWW.matcher(address).matches() ? "https://" : "";
        Matcher matcher = WEB.matcher(scheme + address);
        if (!matcher.matches()) {
            return address;
        }
        String host = matcher.group("host");
        String shown = shownHost(host);
        int start = matcher.start("host") - scheme.length();
        return shown == null || shown.equals(host)
                ? address
                : address.substring(0, start) + shown + address.substring(start + host.length());
    }

    /**
     * An address as it is read, without the scheme, "www." and a final slash, and cut after {@link #SHORT}
     * characters: "familysearch.org/ark:/61903/1:1:…".
     */
    static String shorten(String url) {
        String text = readable(url).replaceFirst("(?i)^(https?://|mailto:)", "").replaceFirst("(?i)^www\\.", "");
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
        Matcher matcher = WEB.matcher(url);
        if (!matcher.matches()) {
            return shorten(url);
        }
        String host = shownHost(matcher.group("host")).toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
