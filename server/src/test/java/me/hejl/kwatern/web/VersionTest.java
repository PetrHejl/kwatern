package me.hejl.kwatern.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.privacy.AliveRules;
import me.hejl.gramps.privacy.PrivacyFilter;
import me.hejl.gramps.privacy.ProbablyAlive;
import me.hejl.gramps.privacy.PublicDatabase;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.ImageDecoder;
import me.hejl.image.ImageDecoders;
import me.hejl.image.ImageReader;
import me.hejl.image.jpeg.JpegDecoder;
import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void isCleanedUpOnceReplacedAndNoLongerInUse() {
        var cleanUps = new AtomicInteger();
        var version = new Version(null, cleanUps::incrementAndGet);
        assertTrue(version.enter());
        assertTrue(version.enter());
        version.replace();
        assertEquals(0, cleanUps.get(), "requests still use it");
        version.leave();
        assertEquals(0, cleanUps.get());
        version.leave();
        assertEquals(1, cleanUps.get(), "after the last request");
        assertFalse(version.enter(), "a request that comes late takes the new version");
        version.replace();
        assertEquals(1, cleanUps.get(), "once only");

        var idle = new Version(null, cleanUps::incrementAndGet);
        idle.replace();
        assertEquals(2, cleanUps.get(), "at once when nothing uses it");
    }

    @Test
    void keepsTheReplacedVersionUntilItsRequestsHaveEnded() throws Exception {
        Path example = Path.of(System.getProperty("gramps.example"));
        GrampsDatabase full = GrampsXml.read(example).database();
        PublicDatabase published = PrivacyFilter.apply(full, new ProbablyAlive(full, AliveRules.DEFAULTS, 2026));
        // Holds up making a thumbnail, as a large scan would, until the export has been reloaded.
        var decoding = new CountDownLatch(1);
        var reloaded = new CountDownLatch(1);
        ImageDecoder jpeg = new JpegDecoder();
        var decoders = new ImageDecoders(List.of(new ImageDecoder() {
            @Override
            public boolean accepts(byte[] header) {
                return jpeg.accepts(header);
            }

            @Override
            public ImageReader open(InputStream in) {
                decoding.countDown();
                try {
                    reloaded.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return jpeg.open(in);
            }
        }));
        var cleanedUp = new CountDownLatch(1);
        var site = new Site(
                published,
                Site.Options.DEFAULT,
                new MediaImages(published.database(), example.getParent(), null, decoders));
        var server = new WebServer(new Version(Sites.open(site), cleanedUp::countDown), null);
        int port = server.start("127.0.0.1", 0);
        try {
            var thumbnail = HttpClient.newHttpClient()
                    .sendAsync(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/thumbnail/O0010"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            decoding.await();
            server.replace(Version.of(Sites.open(new Site(published, Site.Options.DEFAULT))));
            // Before, the extracted media of a package were removed here, while the request still read them.
            assertEquals(1, cleanedUp.getCount(), "not while a request uses it");
            reloaded.countDown();
            assertEquals(200, thumbnail.get().statusCode());
            cleanedUp.await();
        } finally {
            server.stop();
        }
    }
}
