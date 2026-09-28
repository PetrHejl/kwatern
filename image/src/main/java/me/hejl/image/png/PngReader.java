package me.hejl.image.png;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import me.hejl.image.Area;
import me.hejl.image.Exif;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.ImageReader;
import me.hejl.image.RowSink;

/**
 * Decodes PNG images (W3C PNG, third edition): all colour types and bit depths, Adam7 interlacing, transparency
 * from alpha or {@code tRNS}, and EXIF orientation from {@code eXIf}. Transparent pixels are drawn over white,
 * since the results become JPEG thumbnails. Colour profiles and gamma are ignored. Of animated PNGs, the default
 * image is shown.
 *
 * <p>Images that are not interlaced are decoded row by row into the sink, holding two rows; decoding stops
 * below the requested area. Interlaced images are held for the requested area only, up to a memory limit.
 */
public final class PngReader implements ImageReader {

    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Default for the largest image accepted, in pixels. */
    public static final long MAX_PIXELS = 1L << 30;

    /** Default for the memory used to hold interlaced images, in bytes. */
    public static final long MAX_MEMORY = 64L << 20;

    // Adam7: for each pass, the first column and row and the steps between them.
    private static final int[] PASS_X = {0, 4, 0, 2, 0, 1, 0};
    private static final int[] PASS_Y = {0, 0, 4, 0, 2, 0, 1};
    private static final int[] STEP_X = {8, 8, 4, 4, 2, 2, 1};
    private static final int[] STEP_Y = {8, 8, 8, 4, 4, 2, 2};

    private static final int WHITE = 255;

    private final DataInputStream in;
    private final long maxPixels;
    private final long maxMemory;

    private ImageInfo info;
    private int width;
    private int height;
    private int bitDepth;
    private int colourType;
    private boolean interlaced;
    private int channels;
    private int[] palette;
    private int[] paletteAlpha;
    private int transparent = -1;
    // tRNS for colour images: the red, green and blue samples at full depth of the transparent colour.
    private int[] transparentKey;
    private int orientation = 1;
    private int idatRemaining;
    private boolean decoded;

    public PngReader(InputStream in) {
        this(in, MAX_PIXELS, MAX_MEMORY);
    }

    public PngReader(InputStream in, long maxPixels, long maxMemory) {
        this.in = new DataInputStream(in);
        this.maxPixels = maxPixels;
        this.maxMemory = maxMemory;
    }

    @Override
    public String mimeType() {
        return "image/png";
    }

    @Override
    public ImageInfo info() throws IOException {
        if (info == null) {
            readHeader();
            info = new ImageInfo(width, height, orientation);
        }
        return info;
    }

    private void readHeader() throws IOException {
        byte[] signature = new byte[8];
        in.readFully(signature);
        for (int i = 0; i < 8; i++) {
            if (signature[i] != SIGNATURE[i]) {
                throw ImageException.corrupt("Not a PNG image");
            }
        }
        boolean header = false;
        while (true) {
            int length = in.readInt();
            String type = chunkType();
            if (length < 0) {
                throw ImageException.corrupt("Invalid chunk length");
            }
            if (!header && !type.equals("IHDR")) {
                throw ImageException.corrupt("PNG without header");
            }
            switch (type) {
                case "IHDR" -> {
                    readImageHeader(length);
                    header = true;
                }
                case "PLTE" -> readPalette(length);
                case "tRNS" -> readTransparency(length);
                case "eXIf" -> {
                    byte[] data = readData(length);
                    orientation = Exif.orientation(data, 0);
                }
                case "IDAT" -> {
                    if (colourType == 3 && palette == null) {
                        throw ImageException.corrupt("Palette image without palette");
                    }
                    idatRemaining = length;
                    return;
                }
                case "IEND" -> throw ImageException.corrupt("PNG without image data");
                default -> skip(length);
            }
            in.readInt(); // CRC, not checked
        }
    }

    private String chunkType() throws IOException {
        byte[] type = new byte[4];
        in.readFully(type);
        return new String(type, StandardCharsets.ISO_8859_1);
    }

    private void readImageHeader(int length) throws IOException {
        if (length < 13) {
            throw ImageException.corrupt("Invalid PNG header");
        }
        width = in.readInt();
        height = in.readInt();
        bitDepth = in.readUnsignedByte();
        colourType = in.readUnsignedByte();
        int compression = in.readUnsignedByte();
        int filter = in.readUnsignedByte();
        interlaced = in.readUnsignedByte() == 1;
        skip(length - 13);
        if (width <= 0 || height <= 0) {
            throw ImageException.corrupt("Invalid PNG size");
        }
        if ((long) width * height > maxPixels) {
            throw ImageException.unsupported("Image too large: " + width + "×" + height);
        }
        channels = switch (colourType) {
            case 0, 3 -> 1;
            case 2 -> 3;
            case 4 -> 2;
            case 6 -> 4;
            default -> throw ImageException.corrupt("Invalid PNG colour type " + colourType);
        };
        boolean validDepth = switch (colourType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> bitDepth == 8 || bitDepth == 16;
        };
        if (!validDepth || compression != 0 || filter != 0) {
            throw ImageException.corrupt("Invalid PNG header");
        }
    }

    private void readPalette(int length) throws IOException {
        byte[] data = readData(length);
        palette = new int[256];
        for (int i = 0; i < Math.min(256, length / 3); i++) {
            palette[i] = (data[3 * i] & 0xFF) << 16 | (data[3 * i + 1] & 0xFF) << 8 | data[3 * i + 2] & 0xFF;
        }
    }

    private void readTransparency(int length) throws IOException {
        byte[] data = readData(length);
        switch (colourType) {
            case 0 -> transparent = length >= 2 ? (data[0] & 0xFF) << 8 | data[1] & 0xFF : -1;
            case 2 -> {
                if (length >= 6) {
                    int r = (data[0] & 0xFF) << 8 | data[1] & 0xFF;
                    int g = (data[2] & 0xFF) << 8 | data[3] & 0xFF;
                    int b = (data[4] & 0xFF) << 8 | data[5] & 0xFF;
                    transparentKey = new int[] {r, g, b};
                }
            }
            case 3 -> {
                paletteAlpha = new int[256];
                Arrays.fill(paletteAlpha, 255);
                for (int i = 0; i < Math.min(256, length); i++) {
                    paletteAlpha[i] = data[i] & 0xFF;
                }
            }
            default -> {
                // Images with an alpha channel have no tRNS.
            }
        }
    }

    private byte[] readData(int length) throws IOException {
        if (length > 16 << 20) {
            throw ImageException.corrupt("Chunk too large");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    private void skip(long count) throws IOException {
        long left = count;
        while (left > 0) {
            int skipped = in.skipBytes((int) Math.min(left, Integer.MAX_VALUE));
            if (skipped <= 0) {
                throw new EOFException("Truncated PNG");
            }
            left -= skipped;
        }
    }

    // Image data

    /** The compressed image data: the contents of consecutive IDAT chunks, as one stream. */
    private int readIdat(byte[] buffer) throws IOException {
        while (idatRemaining == 0) {
            in.readInt(); // CRC of the previous chunk
            int length = in.readInt();
            String type = chunkType();
            if (!type.equals("IDAT")) {
                return -1;
            }
            idatRemaining = length;
        }
        int count = in.read(buffer, 0, Math.min(buffer.length, idatRemaining));
        if (count < 0) {
            return -1;
        }
        idatRemaining -= count;
        return count;
    }

    private final Inflater inflater = new Inflater();
    private final byte[] compressed = new byte[16 * 1024];
    private boolean inputEnded;

    /** Fills {@code target} with decompressed bytes; missing data at the end becomes zeros, as in libpng. */
    private void inflate(byte[] target, int length) throws IOException {
        int done = 0;
        try {
            while (done < length) {
                int count = inflater.inflate(target, done, length - done);
                done += count;
                if (count == 0) {
                    if (inflater.finished() || inflater.needsDictionary() || inputEnded) {
                        break;
                    }
                    if (inflater.needsInput()) {
                        int read = readIdat(compressed);
                        if (read < 0) {
                            inputEnded = true;
                        } else {
                            inflater.setInput(compressed, 0, read);
                        }
                    }
                }
            }
        } catch (DataFormatException e) {
            throw ImageException.corrupt("Invalid compressed PNG data");
        } catch (EOFException e) {
            // Truncated file: decode what there is.
        }
        Arrays.fill(target, done, length, (byte) 0);
    }

    @Override
    public void decode(RowSink sink, int minWidth, int minHeight, Area area) throws IOException {
        if (decoded) {
            throw new IllegalStateException("Image already decoded");
        }
        decoded = true;
        info();
        Area all = area == null ? new Area(0, 0, width, height) : area;
        int left = Math.clamp(all.x(), 0, width - 1);
        int top = Math.clamp(all.y(), 0, height - 1);
        int right = Math.clamp((long) all.x() + all.width(), left + 1, width);
        int bottom = Math.clamp((long) all.y() + all.height(), top + 1, height);
        try {
            if (interlaced) {
                decodeInterlaced(sink, left, top, right, bottom);
            } else {
                decodeRows(sink, left, top, right, bottom);
            }
        } finally {
            inflater.end();
        }
    }

    private int bytesPerPixel() {
        return Math.max(1, channels * bitDepth / 8);
    }

    private int rowBytes(int pixels) {
        return (int) (((long) pixels * channels * bitDepth + 7) / 8);
    }

    private void decodeRows(RowSink sink, int left, int top, int right, int bottom) throws IOException {
        int length = rowBytes(width);
        byte[] previous = new byte[length];
        byte[] current = new byte[length];
        byte[] filter = new byte[1];
        int[] row = new int[right - left];
        sink.begin(right - left, bottom - top);
        for (int y = 0; y < bottom; y++) {
            inflate(filter, 1);
            inflate(current, length);
            unfilter(filter[0] & 0xFF, current, previous, length);
            if (y >= top) {
                for (int x = left; x < right; x++) {
                    row[x - left] = pixel(current, x);
                }
                sink.row(row);
            }
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
    }

    private void decodeInterlaced(RowSink sink, int left, int top, int right, int bottom) throws IOException {
        int areaWidth = right - left;
        int areaHeight = bottom - top;
        if ((long) areaWidth * areaHeight * Integer.BYTES > maxMemory) {
            throw ImageException.unsupported("Interlaced image too large to decode: " + width + "×" + height);
        }
        int[] pixels = new int[areaWidth * areaHeight];
        for (int pass = 0; pass < 7; pass++) {
            int passWidth = (width - PASS_X[pass] + STEP_X[pass] - 1) / STEP_X[pass];
            int passHeight = (height - PASS_Y[pass] + STEP_Y[pass] - 1) / STEP_Y[pass];
            if (passWidth <= 0 || passHeight <= 0) {
                continue;
            }
            int length = rowBytes(passWidth);
            byte[] previous = new byte[length];
            byte[] current = new byte[length];
            byte[] filter = new byte[1];
            for (int py = 0; py < passHeight; py++) {
                inflate(filter, 1);
                inflate(current, length);
                unfilter(filter[0] & 0xFF, current, previous, length);
                int y = PASS_Y[pass] + py * STEP_Y[pass];
                if (y >= top && y < bottom) {
                    for (int px = 0; px < passWidth; px++) {
                        int x = PASS_X[pass] + px * STEP_X[pass];
                        if (x >= left && x < right) {
                            pixels[(y - top) * areaWidth + x - left] = pixel(current, px);
                        }
                    }
                }
                byte[] swap = previous;
                previous = current;
                current = swap;
            }
        }
        sink.begin(areaWidth, areaHeight);
        int[] row = new int[areaWidth];
        for (int y = 0; y < areaHeight; y++) {
            System.arraycopy(pixels, y * areaWidth, row, 0, areaWidth);
            sink.row(row);
        }
    }

    /** Reverses the filter of a row (PNG section 7.3), given the unfiltered row above it. */
    private void unfilter(int type, byte[] row, byte[] above, int length) throws ImageException {
        int bpp = bytesPerPixel();
        switch (type) {
            case 0 -> {}
            case 1 -> {
                for (int i = bpp; i < length; i++) {
                    row[i] += row[i - bpp];
                }
            }
            case 2 -> {
                for (int i = 0; i < length; i++) {
                    row[i] += above[i];
                }
            }
            case 3 -> {
                for (int i = 0; i < length; i++) {
                    int a = i >= bpp ? row[i - bpp] & 0xFF : 0;
                    row[i] += (byte) ((a + (above[i] & 0xFF)) >>> 1);
                }
            }
            case 4 -> {
                for (int i = 0; i < length; i++) {
                    int a = i >= bpp ? row[i - bpp] & 0xFF : 0;
                    int b = above[i] & 0xFF;
                    int c = i >= bpp ? above[i - bpp] & 0xFF : 0;
                    row[i] += (byte) paeth(a, b, c);
                }
            }
            default -> throw ImageException.corrupt("Invalid PNG filter " + type);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    /** Sample {@code index} of a row at the image's bit depth, at full depth (up to 16 bits). */
    private int sample(byte[] row, int index) {
        return switch (bitDepth) {
            case 16 -> (row[2 * index] & 0xFF) << 8 | row[2 * index + 1] & 0xFF;
            case 8 -> row[index] & 0xFF;
            default -> {
                int perByte = 8 / bitDepth;
                int shift = 8 - bitDepth * (index % perByte + 1);
                yield (row[index / perByte] & 0xFF) >> shift & ((1 << bitDepth) - 1);
            }
        };
    }

    /** A sample scaled to 0–255. */
    private int scaled(int sample) {
        return switch (bitDepth) {
            case 16 -> sample >> 8;
            case 8 -> sample;
            default -> sample * 255 / ((1 << bitDepth) - 1);
        };
    }

    private int pixel(byte[] row, int x) {
        return switch (colourType) {
            case 0 -> {
                int sample = sample(row, x);
                int grey = scaled(sample);
                yield sample == transparent ? WHITE * 0x010101 : grey * 0x010101;
            }
            case 2 -> {
                int r = sample(row, 3 * x);
                int g = sample(row, 3 * x + 1);
                int b = sample(row, 3 * x + 2);
                if (transparentKey != null
                        && r == transparentKey[0]
                        && g == transparentKey[1]
                        && b == transparentKey[2]) {
                    yield WHITE * 0x010101;
                }
                yield scaled(r) << 16 | scaled(g) << 8 | scaled(b);
            }
            case 3 -> {
                int index = sample(row, x);
                int colour = palette[index];
                yield paletteAlpha == null ? colour : over(colour, paletteAlpha[index]);
            }
            case 4 -> {
                int grey = scaled(sample(row, 2 * x));
                int alpha = scaled(sample(row, 2 * x + 1));
                yield over(grey * 0x010101, alpha);
            }
            default -> {
                int r = scaled(sample(row, 4 * x));
                int g = scaled(sample(row, 4 * x + 1));
                int b = scaled(sample(row, 4 * x + 2));
                int alpha = scaled(sample(row, 4 * x + 3));
                yield over(r << 16 | g << 8 | b, alpha);
            }
        };
    }

    /** A colour with the given opacity over white. */
    private static int over(int rgb, int alpha) {
        if (alpha == 255) {
            return rgb;
        }
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int rest = (255 - alpha) * WHITE;
        return (r * alpha + rest + 127) / 255 << 16
                | (g * alpha + rest + 127) / 255 << 8
                | (b * alpha + rest + 127) / 255;
    }
}
