package me.hejl.image.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import me.hejl.image.Image;

/**
 * Writes baseline JPEG images (JFIF, YCbCr with chroma at half resolution both ways, the standard Huffman
 * tables of ITU-T T.81 annex K). Meant for thumbnails, which are small, so it favours simplicity over speed.
 */
public final class JpegWriter {

    // ITU-T T.81 annex K.1, in natural order.
    private static final int[] LUMINANCE = {
        16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29,
        51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121,
        120, 101, 72, 92, 95, 98, 112, 100, 103, 99
    };

    private static final int[] CHROMINANCE = {
        17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99,
        99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99, 99, 99
    };

    /** A Huffman table for encoding: the code and its length for each symbol. */
    private record Codes(int[] counts, int[] values, int[] code, int[] length) {
        static Codes of(int[] counts, int[] values) {
            int[] code = new int[256];
            int[] length = new int[256];
            int next = 0;
            int index = 0;
            for (int bits = 1; bits <= 16; bits++) {
                for (int i = 0; i < counts[bits - 1]; i++) {
                    code[values[index]] = next++;
                    length[values[index]] = bits;
                    index++;
                }
                next <<= 1;
            }
            return new Codes(counts, values, code, length);
        }
    }

    private static final Codes DC_LUMINANCE = Codes.of(Huffman.DC_LUMINANCE_COUNTS, Huffman.DC_VALUES);
    private static final Codes DC_CHROMINANCE = Codes.of(Huffman.DC_CHROMINANCE_COUNTS, Huffman.DC_VALUES);
    private static final Codes AC_LUMINANCE = Codes.of(Huffman.AC_LUMINANCE_COUNTS, Huffman.AC_LUMINANCE_VALUES);
    private static final Codes AC_CHROMINANCE = Codes.of(Huffman.AC_CHROMINANCE_COUNTS, Huffman.AC_CHROMINANCE_VALUES);

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final float[] samples = new float[64];
    private final float[] rows = new float[64];
    private final int[] quantized = new int[64];
    private int bits;
    private int count;

    private JpegWriter() {}

    /**
     * Writes an image.
     *
     * @param quality 1 to 100, as in libjpeg: 50 uses the tables of the standard, higher values finer ones
     */
    public static void write(Image image, int quality, OutputStream target) throws IOException {
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("Quality must be 1 to 100: " + quality);
        }
        if (image.width() > 0xFFFF || image.height() > 0xFFFF) {
            throw new IllegalArgumentException("Image too large for JPEG");
        }
        JpegWriter writer = new JpegWriter();
        writer.encode(image, quality);
        writer.out.writeTo(target);
    }

    private static int[] scale(int[] table, int quality) {
        int factor = quality < 50 ? 5000 / quality : 200 - 2 * quality;
        int[] scaled = new int[64];
        for (int i = 0; i < 64; i++) {
            scaled[i] = Math.clamp((table[i] * factor + 50) / 100, 1, 255);
        }
        return scaled;
    }

    private void encode(Image image, int quality) {
        int[] luminance = scale(LUMINANCE, quality);
        int[] chrominance = scale(CHROMINANCE, quality);
        marker(0xD8);
        // JFIF 1.01, no units, 1:1 pixel aspect ratio, no thumbnail.
        segment(0xE0, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0);
        int[] tables = new int[2 * 65];
        for (int k = 0; k < 64; k++) {
            tables[1 + k] = luminance[JpegReader.ZIGZAG[k]];
            tables[66 + k] = chrominance[JpegReader.ZIGZAG[k]];
        }
        tables[65] = 1;
        segment(0xDB, tables);
        int width = image.width();
        int height = image.height();
        segment(0xC0, 8, height >> 8, height & 0xFF, width >> 8, width & 0xFF, 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1);
        huffmanTable(0x00, DC_LUMINANCE);
        huffmanTable(0x10, AC_LUMINANCE);
        huffmanTable(0x01, DC_CHROMINANCE);
        huffmanTable(0x11, AC_CHROMINANCE);
        segment(0xDA, 3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0);

        float[] y = new float[256];
        float[] cb = new float[64];
        float[] cr = new float[64];
        int[] predictions = new int[3];
        for (int mcuY = 0; mcuY < height; mcuY += 16) {
            for (int mcuX = 0; mcuX < width; mcuX += 16) {
                Arrays.fill(cb, 0);
                Arrays.fill(cr, 0);
                for (int dy = 0; dy < 16; dy++) {
                    int row = Math.min(mcuY + dy, height - 1) * width;
                    for (int dx = 0; dx < 16; dx++) {
                        int pixel = image.rgb()[row + Math.min(mcuX + dx, width - 1)];
                        int r = pixel >> 16 & 0xFF;
                        int g = pixel >> 8 & 0xFF;
                        int b = pixel & 0xFF;
                        y[dy * 16 + dx] = 0.299f * r + 0.587f * g + 0.114f * b - 128;
                        int chroma = (dy >> 1) * 8 + (dx >> 1);
                        cb[chroma] += (-0.168736f * r - 0.331264f * g + 0.5f * b) / 4;
                        cr[chroma] += (0.5f * r - 0.418688f * g - 0.081312f * b) / 4;
                    }
                }
                for (int i = 0; i < 4; i++) {
                    int offset = (i >> 1) * 128 + (i & 1) * 8;
                    for (int j = 0; j < 64; j++) {
                        samples[j] = y[offset + (j >> 3) * 16 + (j & 7)];
                    }
                    predictions[0] = block(luminance, DC_LUMINANCE, AC_LUMINANCE, predictions[0]);
                }
                System.arraycopy(cb, 0, samples, 0, 64);
                predictions[1] = block(chrominance, DC_CHROMINANCE, AC_CHROMINANCE, predictions[1]);
                System.arraycopy(cr, 0, samples, 0, 64);
                predictions[2] = block(chrominance, DC_CHROMINANCE, AC_CHROMINANCE, predictions[2]);
            }
        }
        // Pad the last byte with one bits (T.81 F.1.2.3).
        if (count > 0) {
            put(0x7F, 7);
        }
        marker(0xD9);
    }

    /** Transforms, quantizes and writes the block in {@code samples}; returns its DC value for the next one. */
    private int block(int[] table, Codes dc, Codes ac, int prediction) {
        float[] factors = Idct.factors(8);
        for (int y = 0; y < 8; y++) {
            for (int u = 0; u < 8; u++) {
                float sum = 0;
                for (int x = 0; x < 8; x++) {
                    sum += factors[x * 8 + u] * samples[y * 8 + x];
                }
                rows[y * 8 + u] = sum;
            }
        }
        for (int v = 0; v < 8; v++) {
            for (int u = 0; u < 8; u++) {
                float sum = 0;
                for (int y = 0; y < 8; y++) {
                    sum += factors[y * 8 + v] * rows[y * 8 + u];
                }
                quantized[v * 8 + u] = Math.round(sum / table[v * 8 + u]);
            }
        }
        int value = quantized[0];
        int difference = value - prediction;
        int size = size(difference);
        put(dc.code[size], dc.length[size]);
        put(difference < 0 ? difference - 1 : difference, size);
        int run = 0;
        for (int k = 1; k < 64; k++) {
            // Baseline allows AC values of at most 10 bits, which only quality 100 could exceed.
            int coefficient = Math.clamp(quantized[JpegReader.ZIGZAG[k]], -1023, 1023);
            if (coefficient == 0) {
                run++;
                continue;
            }
            while (run > 15) {
                put(ac.code[0xF0], ac.length[0xF0]);
                run -= 16;
            }
            size = size(coefficient);
            int symbol = run << 4 | size;
            put(ac.code[symbol], ac.length[symbol]);
            put(coefficient < 0 ? coefficient - 1 : coefficient, size);
            run = 0;
        }
        if (run > 0) {
            put(ac.code[0], ac.length[0]);
        }
        return value;
    }

    /** The number of bits of the magnitude (T.81 table F.1); baseline allows at most 11. */
    private static int size(int value) {
        return Math.min(11, 32 - Integer.numberOfLeadingZeros(Math.abs(value)));
    }

    private void put(int value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            bits = bits << 1 | (value >> i & 1);
            if (++count == 8) {
                out.write(bits);
                if (bits == 0xFF) {
                    out.write(0);
                }
                bits = 0;
                count = 0;
            }
        }
    }

    private void marker(int marker) {
        out.write(0xFF);
        out.write(marker);
    }

    private void segment(int marker, int... data) {
        marker(marker);
        int length = data.length + 2;
        out.write(length >> 8);
        out.write(length & 0xFF);
        for (int value : data) {
            out.write(value);
        }
    }

    private void huffmanTable(int type, Codes codes) {
        int[] data = new int[17 + codes.values().length];
        data[0] = type;
        System.arraycopy(codes.counts(), 0, data, 1, 16);
        System.arraycopy(codes.values(), 0, data, 17, codes.values().length);
        segment(0xC4, data);
    }
}
