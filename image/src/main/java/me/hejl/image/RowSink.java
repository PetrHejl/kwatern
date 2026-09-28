package me.hejl.image;

/**
 * Receives an image row by row from a decoder, so that large images never have to be held in memory whole.
 */
public interface RowSink {

    /** Called once before the first row. */
    void begin(int width, int height);

    /**
     * One row of pixels packed as {@code 0xRRGGBB}, from top to bottom. The array is reused for the next
     * row, so it must be copied if kept.
     */
    void row(int[] rgb);
}
