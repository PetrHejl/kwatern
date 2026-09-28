package me.hejl.image;

import static me.hejl.image.TestImages.crop;
import static me.hejl.image.TestImages.psnr;
import static me.hejl.image.TestImages.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ThumbnailsTest {

    private static Image make(String name, int max, Thumbnails.Crop crop, boolean square) throws IOException {
        return Thumbnails.make(ImageDecoders.DEFAULT.open(resource(name)), max, max, crop, square);
    }

    @Test
    void fitsAndTurnsImages() throws IOException {
        Image image = make("jpeg/orientation-6.jpg", 20, null, false);
        // Stored 67×51, displayed 51×67.
        assertEquals(15, image.width());
        assertEquals(20, image.height());
    }

    @Test
    void keepsSmallImages() throws IOException {
        Image image = make("jpeg/baseline-420.jpg", 1000, null, false);
        assertEquals(67, image.width());
        assertEquals(51, image.height());
    }

    @Test
    void cropsAsDisplayed() throws IOException {
        Image displayed = make("jpeg/orientation-6.jpg", 1000, null, false);
        // The top left quarter of what is shown, whatever the stored orientation.
        Image quarter = make("jpeg/orientation-6.jpg", 1000, new Thumbnails.Crop(0.5, 0.5, 0, 0), false);
        assertEquals(26, quarter.width());
        assertEquals(34, quarter.height());
        assertTrue(psnr(quarter, crop(displayed, 0, 0, 26, 34)) > 40);
    }

    @Test
    void widensPortraitsToSquares() throws IOException {
        // A narrow strip at the right edge becomes a square that stays within the image.
        Image image = make("jpeg/baseline-420.jpg", 1000, new Thumbnails.Crop(0.9, 0.2, 1, 0.6), true);
        assertEquals(image.width(), image.height());
        assertEquals(21, image.width());
    }

    @Test
    void mapsAreasForEveryOrientation() {
        // The displayed top left corner of a 40×20 stored image.
        int[][] expected = {{0, 0}, {30, 0}, {30, 15}, {0, 15}, {0, 0}, {0, 15}, {30, 15}, {30, 0}};
        for (int orientation = 1; orientation <= 8; orientation++) {
            Area area = Thumbnails.stored(new ImageInfo(40, 20, orientation), 0, 0, 0.25, 0.25);
            int[] corner = expected[orientation - 1];
            assertEquals(corner[0], area.x(), "orientation " + orientation);
            assertEquals(corner[1], area.y(), "orientation " + orientation);
        }
    }

    @Test
    void recognisesFormatsByContent() {
        ImageException e = assertThrows(
                ImageException.class,
                () -> ImageDecoders.DEFAULT.open(
                        new ByteArrayInputStream("BM6\0\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII))));
        assertTrue(e.unsupported());
    }
}
