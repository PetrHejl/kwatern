package me.hejl.image;

import java.util.Arrays;

/**
 * Shrinks an image to fit a box while it is being decoded. Every target pixel is the average of the source area
 * it covers, which keeps fine detail such as text readable and needs memory only for the target image.
 * Images that already fit keep their size.
 */
public final class Scaler implements RowSink {

    private final int maxWidth;
    private final int maxHeight;

    private int sourceWidth;
    private int sourceHeight;
    private int width;
    private int height;
    private int[] target;

    // For each source column and row: the first target cell it falls into and the share of it in that cell;
    // the rest falls into the next cell. A source pixel never spans more than two cells when shrinking.
    private int[] column;
    private double[] columnShare;
    private int[] line;
    private double[] lineShare;

    private double[] horizontal;
    private double[] current;
    private double[] next;
    private int sourceRow;
    private int targetRow;
    private double area;

    public Scaler(int maxWidth, int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("Bad size " + maxWidth + "×" + maxHeight);
        }
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    /** The size that {@code width × height} gets when fitted into {@code maxWidth × maxHeight}. */
    public static int[] fit(int width, int height, int maxWidth, int maxHeight) {
        double scale = Math.min(1, Math.min((double) maxWidth / width, (double) maxHeight / height));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    @Override
    public void begin(int sourceWidth, int sourceHeight) {
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        int[] size = fit(sourceWidth, sourceHeight, maxWidth, maxHeight);
        width = size[0];
        height = size[1];
        target = new int[width * height];
        column = new int[sourceWidth];
        columnShare = new double[sourceWidth];
        cells(sourceWidth, width, column, columnShare);
        line = new int[sourceHeight];
        lineShare = new double[sourceHeight];
        cells(sourceHeight, height, line, lineShare);
        horizontal = new double[3 * (width + 1)];
        current = new double[3 * width];
        next = new double[3 * width];
        area = (double) sourceWidth / width * sourceHeight / height;
        sourceRow = 0;
        targetRow = 0;
    }

    private static void cells(int source, int target, int[] cell, double[] share) {
        double step = (double) source / target;
        for (int i = 0; i < source; i++) {
            int first = Math.min(target - 1, (int) (i / step));
            double end = (first + 1) * step;
            cell[i] = first;
            share[i] = end < i + 1 && first + 1 < target ? end - i : 1;
        }
    }

    @Override
    public void row(int[] rgb) {
        if (sourceRow >= sourceHeight) {
            throw new IllegalStateException("More rows than announced");
        }
        Arrays.fill(horizontal, 0);
        for (int x = 0; x < sourceWidth; x++) {
            int pixel = rgb[x];
            int r = pixel >>> 16 & 0xFF;
            int g = pixel >>> 8 & 0xFF;
            int b = pixel & 0xFF;
            int cell = 3 * column[x];
            double share = columnShare[x];
            horizontal[cell] += r * share;
            horizontal[cell + 1] += g * share;
            horizontal[cell + 2] += b * share;
            if (share < 1) {
                double rest = 1 - share;
                horizontal[cell + 3] += r * rest;
                horizontal[cell + 4] += g * rest;
                horizontal[cell + 5] += b * rest;
            }
        }
        int cell = line[sourceRow];
        double share = lineShare[sourceRow];
        while (targetRow < cell) {
            flush();
        }
        for (int i = 0; i < current.length; i++) {
            current[i] += horizontal[i] * share;
            if (share < 1) {
                next[i] += horizontal[i] * (1 - share);
            }
        }
        sourceRow++;
        if (sourceRow == sourceHeight) {
            while (targetRow < height) {
                flush();
            }
        }
    }

    private void flush() {
        int offset = targetRow * width;
        for (int x = 0; x < width; x++) {
            int r = (int) Math.round(current[3 * x] / area);
            int g = (int) Math.round(current[3 * x + 1] / area);
            int b = (int) Math.round(current[3 * x + 2] / area);
            target[offset + x] = clamp(r) << 16 | clamp(g) << 8 | clamp(b);
        }
        double[] done = current;
        current = next;
        next = done;
        Arrays.fill(next, 0);
        targetRow++;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /** The shrunk image, once all rows have been received. */
    public Image image() {
        if (target == null || sourceRow < sourceHeight) {
            throw new IllegalStateException("Image not complete");
        }
        return new Image(width, height, target);
    }
}
