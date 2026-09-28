package me.hejl.image;

/**
 * The size of an image as stored, and how to turn it for display.
 *
 * @param orientation the EXIF orientation, 1 (as stored) to 8, see {@link Image#oriented(int)}
 */
public record ImageInfo(int width, int height, int orientation) {

    /** The width as displayed, after applying the orientation. */
    public int displayWidth() {
        return orientation >= 5 ? height : width;
    }

    /** The height as displayed, after applying the orientation. */
    public int displayHeight() {
        return orientation >= 5 ? width : height;
    }
}
