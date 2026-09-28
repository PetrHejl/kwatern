package me.hejl.image;

/** Collects the rows from a decoder into an {@link Image}. */
public final class ImageBuilder implements RowSink {

    private int width;
    private int height;
    private int[] rgb;
    private int rows;

    @Override
    public void begin(int width, int height) {
        this.width = width;
        this.height = height;
        rgb = new int[width * height];
        rows = 0;
    }

    @Override
    public void row(int[] row) {
        System.arraycopy(row, 0, rgb, rows * width, width);
        rows++;
    }

    public Image image() {
        if (rgb == null || rows < height) {
            throw new IllegalStateException("Image not complete");
        }
        return new Image(width, height, rgb);
    }
}
