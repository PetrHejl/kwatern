package me.hejl.kwatern.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebLinksTest {

    @Test
    void linksOnlyWebAndEmailAddresses() {
        assertEquals("https://example.org/a", WebLinks.url(" https://example.org/a "));
        assertEquals("HTTP://example.org", WebLinks.url("HTTP://example.org"));
        assertEquals("https://www.example.org", WebLinks.url("www.example.org"));
        assertEquals("mailto:archive@example.org", WebLinks.url("archive@example.org"));
        assertEquals("mailto:archive@example.org", WebLinks.url("mailto:archive@example.org"));
        assertNull(WebLinks.url("mailto:archive@example.org?subject=x"));
        assertNull(WebLinks.url("javascript:alert(1)"));
        assertNull(WebLinks.url("file:///home/scan.jpg"));
        assertNull(WebLinks.url("relative://archive.zip"));
        assertNull(WebLinks.url("ftp://example.org/a"));
        assertNull(WebLinks.url("https://"));
        assertNull(WebLinks.url("https://exa mple.org"));
        assertNull(WebLinks.url("example.org"));
        assertNull(WebLinks.url("https://user@example.org"));
        assertNull(WebLinks.url("https://example.org/\u0000"));
        assertNull(WebLinks.url(null));
    }

    @Test
    void linksWhatBrowsersOpen() {
        assertEquals("https://příklad.cz/", WebLinks.url("https://příklad.cz/"));
        assertEquals(
                "https://a.example.org/q?x=[1]|2&y={3}^%", WebLinks.url("https://a.example.org/q?x=[1]|2&y={3}^%"));
        assertEquals("http://[::1]:8080/a", WebLinks.url("http://[::1]:8080/a"));
        assertEquals("příklad.cz", WebLinks.site("https://www.Příklad.cz/a"));
    }

    @Test
    void showsHostsThatMixScriptsInPunycode() {
        // "аpple.com" with a Cyrillic а
        assertEquals("https://\u0430pple.com/", WebLinks.url("https://\u0430pple.com/"));
        assertEquals("xn--pple-43d.com", WebLinks.site("https://\u0430pple.com/"));
        assertEquals("xn--pple-43d.com/a", WebLinks.shorten("https://\u0430pple.com/a"));
        assertEquals("www.xn--pple-43d.com/a", WebLinks.readable("www.\u0430pple.com/a"));
        assertEquals("příklad.cz", WebLinks.site("https://příklad.cz"));
        assertEquals("例え.jp", WebLinks.site("https://例え.jp"));
        assertEquals("сайт.рф", WebLinks.site("https://сайт.рф"));
    }

    @Test
    void linksOnlyWebAddressesInText() {
        assertEquals("https://example.org", WebLinks.web("https://example.org"));
        assertNull(WebLinks.web("archive@example.org"));
        assertNull(WebLinks.web("mailto:archive@example.org"));
    }

    @Test
    void findsTypedAddressesWithoutTheSentenceAroundThem() {
        assertEquals(
                found("see www.example.org.", "www.example.org", "https://www.example.org"),
                WebLinks.find("see www.example.org."));
        assertEquals(
                found("(see http://example.org/a).", "http://example.org/a", "http://example.org/a"),
                WebLinks.find("(see http://example.org/a)."));
        String wiki = "https://en.wikipedia.org/wiki/Great_Falls_(Montana)";
        assertEquals(found("at " + wiki + ", ", wiki, wiki), WebLinks.find("at " + wiki + ", "));
        assertEquals(
                found("„https://archiv.cz/fond/12“ a", "https://archiv.cz/fond/12", "https://archiv.cz/fond/12"),
                WebLinks.find("„https://archiv.cz/fond/12“ a"));
        assertEquals(
                found("„www.archiv.cz“.", "www.archiv.cz", "https://www.archiv.cz"), WebLinks.find("„www.archiv.cz“."));
        assertEquals(found("«www.a.cz»", "www.a.cz", "https://www.a.cz"), WebLinks.find("«www.a.cz»"));
        assertEquals(found("‘www.a.cz’", "www.a.cz", "https://www.a.cz"), WebLinks.find("‘www.a.cz’"));
        assertEquals(found("（www.a.cz）", "www.a.cz", "https://www.a.cz"), WebLinks.find("（www.a.cz）"));
        assertEquals(
                found("見https://example.org/", "https://example.org/", "https://example.org/"),
                WebLinks.find("見https://example.org/"));
        assertEquals(
                found("https://example.org/a\u200Bnext", "https://example.org/a", "https://example.org/a"),
                WebLinks.find("https://example.org/a\u200Bnext"));
        assertEquals(
                found("https://example.org/a\u00A0(1890)", "https://example.org/a", "https://example.org/a"),
                WebLinks.find("https://example.org/a\u00A0(1890)"));
        assertEquals(List.of(), WebLinks.find("no address, archive@example.org, xwww.example.org"));
        assertEquals(
                2, WebLinks.find("https://a.example.org https://b.example.org").size());
    }

    private static List<WebLinks.Found> found(String text, String address, String url) {
        int start = text.indexOf(address);
        return List.of(new WebLinks.Found(start, start + address.length(), url));
    }

    @Test
    void shortensAddressesForReading() {
        assertEquals("example.org", WebLinks.shorten("https://www.example.org/"));
        assertEquals("archive@example.org", WebLinks.shorten("mailto:archive@example.org"));
        assertEquals(
                "familysearch.org/ark:/61903/1:1:…",
                WebLinks.shorten("https://www.familysearch.org/ark:/61903/1:1:M8"));
        assertEquals("familysearch.org", WebLinks.site("https://WWW.FamilySearch.org/tree"));
        assertEquals("archive@example.org", WebLinks.site("mailto:archive@example.org"));
    }
}
