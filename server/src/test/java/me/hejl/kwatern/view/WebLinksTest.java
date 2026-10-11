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
        assertNull(WebLinks.url(null));
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
