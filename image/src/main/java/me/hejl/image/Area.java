package me.hejl.image;

/** A rectangle of an image in pixels, as stored (before any EXIF orientation). */
public record Area(int x, int y, int width, int height) {

    public Area {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Empty area " + width + "×" + height);
        }
    }
}
