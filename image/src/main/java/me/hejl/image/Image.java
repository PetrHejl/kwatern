package me.hejl.image;

/**
 * A decoded image: {@code width × height} pixels, row by row, each packed as {@code 0xRRGGBB}.
 */
public record Image(int width, int height, int[] rgb) {

    public Image {
        if (width <= 0 || height <= 0 || rgb.length < width * height) {
            throw new IllegalArgumentException("Bad image size " + width + "×" + height);
        }
    }

    public int pixel(int x, int y) {
        return rgb[y * width + x];
    }

    /**
     * The image as it should be displayed, given an EXIF orientation (1 to 8). Values outside the range are
     * treated as 1, the stored orientation.
     */
    public Image oriented(int orientation) {
        if (orientation < 2 || orientation > 8) {
            return this;
        }
        boolean swap = orientation >= 5;
        int targetWidth = swap ? height : width;
        int[] target = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int tx;
                int ty;
                switch (orientation) {
                    case 2 -> {
                        tx = width - 1 - x;
                        ty = y;
                    }
                    case 3 -> {
                        tx = width - 1 - x;
                        ty = height - 1 - y;
                    }
                    case 4 -> {
                        tx = x;
                        ty = height - 1 - y;
                    }
                    case 5 -> {
                        tx = y;
                        ty = x;
                    }
                    case 6 -> {
                        tx = height - 1 - y;
                        ty = x;
                    }
                    case 7 -> {
                        tx = height - 1 - y;
                        ty = width - 1 - x;
                    }
                    default -> {
                        tx = y;
                        ty = width - 1 - x;
                    }
                }
                target[ty * targetWidth + tx] = rgb[y * width + x];
            }
        }
        return new Image(targetWidth, swap ? width : height, target);
    }
}
