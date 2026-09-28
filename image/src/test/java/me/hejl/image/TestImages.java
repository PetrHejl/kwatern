package me.hejl.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Helpers for image tests: reading PPM and PGM files, comparing images. */
public final class TestImages {

    private TestImages() {}

    public static InputStream resource(String name) {
        InputStream in = TestImages.class.getResourceAsStream("/me/hejl/image/" + name);
        if (in == null) {
            throw new IllegalArgumentException("No test resource " + name);
        }
        return in;
    }

    public static byte[] bytes(String name) {
        try (InputStream in = resource(name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a binary PPM (P6) or PGM (P5) image with 8-bit samples. */
    public static Image readPnm(InputStream in) throws IOException {
        String magic = token(in);
        int width = Integer.parseInt(token(in));
        int height = Integer.parseInt(token(in));
        int max = Integer.parseInt(token(in));
        if (max != 255 || !magic.equals("P6") && !magic.equals("P5")) {
            throw new IOException("Unsupported PNM " + magic + " " + max);
        }
        boolean colour = magic.equals("P6");
        byte[] data = in.readNBytes(width * height * (colour ? 3 : 1));
        int[] rgb = new int[width * height];
        for (int i = 0; i < rgb.length; i++) {
            if (colour) {
                rgb[i] = (data[3 * i] & 0xFF) << 16 | (data[3 * i + 1] & 0xFF) << 8 | data[3 * i + 2] & 0xFF;
            } else {
                rgb[i] = (data[i] & 0xFF) * 0x010101;
            }
        }
        return new Image(width, height, rgb);
    }

    // Header tokens are separated by whitespace, the last one by a single whitespace byte.
    private static String token(InputStream in) throws IOException {
        ByteArrayOutputStream token = new ByteArrayOutputStream();
        while (true) {
            int value = in.read();
            if (value < 0) {
                throw new IOException("Truncated PNM header");
            }
            if (value == '#') {
                while (value != '\n' && value >= 0) {
                    value = in.read();
                }
                continue;
            }
            if (Character.isWhitespace(value)) {
                if (token.size() > 0) {
                    return token.toString();
                }
                continue;
            }
            token.write(value);
        }
    }

    /** Peak signal-to-noise ratio in dB over all three channels; infinite for identical images. */
    public static double psnr(Image a, Image b) {
        if (a.width() != b.width() || a.height() != b.height()) {
            throw new AssertionError(
                    "Sizes differ: " + a.width() + "×" + a.height() + " and " + b.width() + "×" + b.height());
        }
        double sum = 0;
        int count = a.width() * a.height();
        for (int i = 0; i < count; i++) {
            int p = a.rgb()[i];
            int q = b.rgb()[i];
            for (int shift = 0; shift <= 16; shift += 8) {
                int difference = (p >> shift & 0xFF) - (q >> shift & 0xFF);
                sum += difference * difference;
            }
        }
        double mse = sum / (3.0 * count);
        return mse == 0 ? Double.POSITIVE_INFINITY : 10 * Math.log10(255 * 255 / mse);
    }

    /** The largest difference of any channel of any pixel. */
    public static int maxDifference(Image a, Image b) {
        if (a.width() != b.width() || a.height() != b.height()) {
            throw new AssertionError(
                    "Sizes differ: " + a.width() + "×" + a.height() + " and " + b.width() + "×" + b.height());
        }
        int max = 0;
        for (int i = 0; i < a.rgb().length; i++) {
            for (int shift = 0; shift <= 16; shift += 8) {
                max = Math.max(max, Math.abs((a.rgb()[i] >> shift & 0xFF) - (b.rgb()[i] >> shift & 0xFF)));
            }
        }
        return max;
    }

    /** Decodes a whole image at full size with the decoders of this module. */
    public static Image decode(InputStream in) throws IOException {
        ImageBuilder builder = new ImageBuilder();
        ImageDecoders.DEFAULT.open(in).decode(builder, Integer.MAX_VALUE, Integer.MAX_VALUE, null);
        return builder.image();
    }

    /** The image reduced to {@code eighths}/8 by averaging blocks, the reference for scaled decoding. */
    public static Image boxAverage(Image image, int eighths) {
        int step = 8 / eighths;
        int width = (image.width() + step - 1) / step;
        int height = (image.height() + step - 1) / step;
        int[] rgb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] sum = new int[3];
                int count = 0;
                for (int sy = y * step; sy < Math.min(image.height(), (y + 1) * step); sy++) {
                    for (int sx = x * step; sx < Math.min(image.width(), (x + 1) * step); sx++) {
                        int pixel = image.pixel(sx, sy);
                        sum[0] += pixel >> 16 & 0xFF;
                        sum[1] += pixel >> 8 & 0xFF;
                        sum[2] += pixel & 0xFF;
                        count++;
                    }
                }
                rgb[y * width + x] = Math.round((float) sum[0] / count) << 16
                        | Math.round((float) sum[1] / count) << 8
                        | Math.round((float) sum[2] / count);
            }
        }
        return new Image(width, height, rgb);
    }

    /** The part of an image in the given rectangle. */
    public static Image crop(Image image, int x, int y, int width, int height) {
        int[] rgb = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(image.rgb(), (y + row) * image.width() + x, rgb, row * width, width);
        }
        return new Image(width, height, rgb);
    }

    public static Image grey(Image image) {
        int[] rgb = new int[image.rgb().length];
        for (int i = 0; i < rgb.length; i++) {
            int p = image.rgb()[i];
            int value = Math.round(0.299f * (p >> 16 & 0xFF) + 0.587f * (p >> 8 & 0xFF) + 0.114f * (p & 0xFF));
            rgb[i] = value * 0x010101;
        }
        return new Image(image.width(), image.height(), rgb);
    }
}
