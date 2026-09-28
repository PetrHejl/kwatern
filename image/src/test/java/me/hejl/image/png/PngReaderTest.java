package me.hejl.image.png;

import static me.hejl.image.TestImages.crop;
import static me.hejl.image.TestImages.decode;
import static me.hejl.image.TestImages.maxDifference;
import static me.hejl.image.TestImages.readPnm;
import static me.hejl.image.TestImages.resource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import me.hejl.image.Area;
import me.hejl.image.Image;
import me.hejl.image.ImageBuilder;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.TestImages;
import org.junit.jupiter.api.Test;

/** Images made by {@code src/test/fixtures/make-png-gif-fixtures.sh}; {@code MagickTest} covers more variants. */
class PngReaderTest {

    @Test
    void decodesColourExactly() throws IOException {
        Image source = readPnm(resource("jpeg/source.ppm"));
        assertArrayEquals(source.rgb(), decode(resource("png/rgb8.png")).rgb());
    }

    @Test
    void decodesInterlacedPaletteWithTransparency() throws IOException {
        Image expected = readPnm(resource("png/palette-interlaced.ppm"));
        assertEquals(0, maxDifference(expected, decode(resource("png/palette-interlaced.png"))));
    }

    @Test
    void decodesSixteenBitGreyWithAlpha() throws IOException {
        Image expected = readPnm(resource("png/grey-alpha16.ppm"));
        assertTrue(maxDifference(expected, decode(resource("png/grey-alpha16.png"))) <= 1);
    }

    @Test
    void readsHeader() throws IOException {
        assertEquals(new ImageInfo(67, 51, 1), new PngReader(resource("png/rgb8.png")).info());
        assertEquals("image/png", new PngReader(resource("png/rgb8.png")).mimeType());
    }

    @Test
    void decodesArea() throws IOException {
        for (String name : new String[] {"png/rgb8.png", "png/palette-interlaced.png"}) {
            Image whole = decode(resource(name));
            ImageBuilder builder = new ImageBuilder();
            new PngReader(resource(name)).decode(builder, 1, 1, new Area(5, 3, 20, 11));
            assertArrayEquals(crop(whole, 5, 3, 20, 11).rgb(), builder.image().rgb(), name);
        }
    }

    @Test
    void decodesTruncatedImages() throws IOException {
        byte[] data = TestImages.bytes("png/rgb8.png");
        Image image = decode(new ByteArrayInputStream(Arrays.copyOf(data, data.length * 2 / 3)));
        assertEquals(67, image.width());
        assertEquals(51, image.height());
    }

    @Test
    void rejectsOtherFormats() {
        assertThrows(ImageException.class, () -> new PngReader(resource("jpeg/baseline-420.jpg")).info());
    }
}
