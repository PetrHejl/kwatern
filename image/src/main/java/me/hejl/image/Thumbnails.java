package me.hejl.image;

import java.io.IOException;

/** Makes small versions of images, whole or a part such as a face, as they are displayed. */
public final class Thumbnails {

    private Thumbnails() {}

    /**
     * A part of an image as displayed (after EXIF orientation), as fractions 0 to 1 of its width and height, like
     * the percentages of Gramps' media reference regions. The corners may be given in any order.
     */
    public record Crop(double x1, double y1, double x2, double y2) {}

    /**
     * Decodes an image reduced to fit {@code maxWidth × maxHeight}, turned as it should be displayed. Images that
     * are smaller keep their size.
     *
     * @param crop   the part to show, or {@code null} for all of it
     * @param square whether to widen the shorter side of the part, around its centre, to make it square, as far
     *     as the image allows; for round portraits
     */
    public static Image make(ImageReader reader, int maxWidth, int maxHeight, Crop crop, boolean square)
            throws IOException {
        ImageInfo info = reader.info();
        int orientation = info.orientation();
        double width = info.displayWidth();
        double height = info.displayHeight();
        double left = 0;
        double top = 0;
        double right = width;
        double bottom = height;
        if (crop != null) {
            left = clamp(Math.min(crop.x1(), crop.x2())) * width;
            right = clamp(Math.max(crop.x1(), crop.x2())) * width;
            top = clamp(Math.min(crop.y1(), crop.y2())) * height;
            bottom = clamp(Math.max(crop.y1(), crop.y2())) * height;
        }
        if (square) {
            double side = Math.min(Math.max(right - left, bottom - top), Math.min(width, height));
            double[] horizontal = centre(left, right, side, width);
            double[] vertical = centre(top, bottom, side, height);
            left = horizontal[0];
            right = horizontal[1];
            top = vertical[0];
            bottom = vertical[1];
        }
        Area area = stored(info, left / width, top / height, right / width, bottom / height);
        if (square && area.width() != area.height()) {
            // Rounding to whole pixels may leave one side a pixel longer.
            int side = Math.min(area.width(), area.height());
            area = new Area(area.x(), area.y(), side, side);
        }
        boolean turned = orientation >= 5;
        int boxWidth = turned ? maxHeight : maxWidth;
        int boxHeight = turned ? maxWidth : maxHeight;
        int[] size = Scaler.fit(area.width(), area.height(), boxWidth, boxHeight);
        Scaler scaler = new Scaler(size[0], size[1]);
        reader.decode(scaler, size[0], size[1], area);
        return scaler.image().oriented(orientation);
    }

    private static double clamp(double fraction) {
        return Math.clamp(fraction, 0, 1);
    }

    /** A range of the given length around the centre of {@code start..end}, moved to lie within {@code 0..limit}. */
    private static double[] centre(double start, double end, double length, double limit) {
        double from = (start + end - length) / 2;
        from = Math.clamp(from, 0, limit - length);
        return new double[] {from, from + length};
    }

    /** The part of the stored image that is shown at the given fractions of the displayed one. */
    static Area stored(ImageInfo info, double left, double top, double right, double bottom) {
        double[] a = storedFractions(info.orientation(), left, top);
        double[] b = storedFractions(info.orientation(), right, bottom);
        int x0 = (int) Math.floor(Math.min(a[0], b[0]) * info.width());
        int x1 = (int) Math.ceil(Math.max(a[0], b[0]) * info.width());
        int y0 = (int) Math.floor(Math.min(a[1], b[1]) * info.height());
        int y1 = (int) Math.ceil(Math.max(a[1], b[1]) * info.height());
        x0 = Math.min(x0, info.width() - 1);
        y0 = Math.min(y0, info.height() - 1);
        return new Area(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    // The inverse of Image.oriented, on fractions of the width and height.
    private static double[] storedFractions(int orientation, double x, double y) {
        return switch (orientation) {
            case 2 -> new double[] {1 - x, y};
            case 3 -> new double[] {1 - x, 1 - y};
            case 4 -> new double[] {x, 1 - y};
            case 5 -> new double[] {y, x};
            case 6 -> new double[] {y, 1 - x};
            case 7 -> new double[] {1 - y, 1 - x};
            case 8 -> new double[] {1 - y, x};
            default -> new double[] {x, y};
        };
    }
}
