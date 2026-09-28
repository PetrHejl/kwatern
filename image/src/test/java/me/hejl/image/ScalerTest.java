package me.hejl.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ScalerTest {

    private static Image scale(Image image, int maxWidth, int maxHeight) {
        Scaler scaler = new Scaler(maxWidth, maxHeight);
        scaler.begin(image.width(), image.height());
        int[] row = new int[image.width()];
        for (int y = 0; y < image.height(); y++) {
            System.arraycopy(image.rgb(), y * image.width(), row, 0, image.width());
            scaler.row(row);
        }
        return scaler.image();
    }

    @Test
    void averagesWholeBlocks() {
        Image image = new Image(4, 2, new int[] {0, 20, 100, 100, 40, 60, 0xFF0000, 0xFF0000});
        assertArrayEquals(new int[] {30, 0x800032}, scale(image, 2, 1).rgb());
    }

    @Test
    void averagesPartialPixels() {
        // Three pixels into two: each target pixel takes one and a half.
        Image image = new Image(3, 1, new int[] {0, 90, 180});
        assertArrayEquals(new int[] {30, 150}, scale(image, 2, 1).rgb());
    }

    @Test
    void keepsAspectRatioAndNeverEnlarges() {
        assertArrayEquals(new int[] {400, 300}, Scaler.fit(4000, 3000, 400, 400));
        assertArrayEquals(new int[] {225, 300}, Scaler.fit(3000, 4000, 400, 300));
        assertArrayEquals(new int[] {100, 50}, Scaler.fit(100, 50, 400, 400));
        assertArrayEquals(new int[] {10, 1}, Scaler.fit(10000, 1, 10, 10));
        Image image = new Image(2, 1, new int[] {5, 6});
        assertEquals(image.rgb()[1], scale(image, 10, 10).rgb()[1]);
    }

    @Test
    void keepsUniformImagesUniform() {
        int[] rgb = new int[37 * 23];
        Arrays.fill(rgb, 0x123456);
        Image scaled = scale(new Image(37, 23, rgb), 10, 10);
        for (int pixel : scaled.rgb()) {
            assertEquals(0x123456, pixel);
        }
    }
}
