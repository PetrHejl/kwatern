package me.hejl.image.gif;

import static me.hejl.image.TestImages.decode;
import static me.hejl.image.TestImages.maxDifference;
import static me.hejl.image.TestImages.readPnm;
import static me.hejl.image.TestImages.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import me.hejl.image.ImageInfo;
import org.junit.jupiter.api.Test;

/** Images made by {@code src/test/fixtures/make-png-gif-fixtures.sh}; {@code MagickTest} covers more variants. */
class GifReaderTest {

    @Test
    void decodesInterlacedWithTransparency() throws IOException {
        assertEquals(0, maxDifference(readPnm(resource("gif/interlaced.ppm")), decode(resource("gif/interlaced.gif"))));
    }

    @Test
    void readsHeader() throws IOException {
        GifReader reader = new GifReader(resource("gif/interlaced.gif"));
        assertEquals(new ImageInfo(37, 29, 1), reader.info());
        assertEquals("image/gif", reader.mimeType());
    }
}
