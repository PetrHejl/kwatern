package me.hejl.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImageTest {

    // 1 2 3
    // 4 5 6
    private final Image image = new Image(3, 2, new int[] {1, 2, 3, 4, 5, 6});

    @Test
    void appliesExifOrientation() {
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 6}, image.oriented(1).rgb());
        assertArrayEquals(new int[] {3, 2, 1, 6, 5, 4}, image.oriented(2).rgb());
        assertArrayEquals(new int[] {6, 5, 4, 3, 2, 1}, image.oriented(3).rgb());
        assertArrayEquals(new int[] {4, 5, 6, 1, 2, 3}, image.oriented(4).rgb());
        assertArrayEquals(new int[] {1, 4, 2, 5, 3, 6}, image.oriented(5).rgb());
        assertArrayEquals(new int[] {4, 1, 5, 2, 6, 3}, image.oriented(6).rgb());
        assertArrayEquals(new int[] {6, 3, 5, 2, 4, 1}, image.oriented(7).rgb());
        assertArrayEquals(new int[] {3, 6, 2, 5, 1, 4}, image.oriented(8).rgb());
        assertEquals(2, image.oriented(6).width());
        assertEquals(3, image.oriented(6).height());
    }
}
