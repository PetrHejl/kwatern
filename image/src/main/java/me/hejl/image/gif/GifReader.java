package me.hejl.image.gif;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import me.hejl.image.Area;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.ImageReader;
import me.hejl.image.RowSink;

/**
 * Decodes the first frame of GIF images (GIF87a and GIF89a): global and local colour tables, interlacing and a
 * transparent colour. The frame is placed on the image's canvas; the rest of the canvas and transparent pixels
 * are white, since the results become JPEG thumbnails.
 */
public final class GifReader implements ImageReader {

    /** Default for the largest image accepted, in pixels. */
    public static final long MAX_PIXELS = 64L << 20;

    private static final int WHITE = 0xFFFFFF;
    private static final int MAX_CODES = 4096;

    private final InputStream in;
    private final long maxPixels;

    private ImageInfo info;
    private int width;
    private int height;
    private int[] globalTable;
    private int transparent = -1;
    private boolean decoded;

    public GifReader(InputStream in) {
        this(in, MAX_PIXELS);
    }

    public GifReader(InputStream in, long maxPixels) {
        this.in = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
        this.maxPixels = maxPixels;
    }

    @Override
    public String mimeType() {
        return "image/gif";
    }

    @Override
    public ImageInfo info() throws IOException {
        if (info == null) {
            byte[] header = in.readNBytes(13);
            String signature = new String(header, 0, Math.min(6, header.length), StandardCharsets.ISO_8859_1);
            if (header.length < 13 || !signature.equals("GIF87a") && !signature.equals("GIF89a")) {
                throw ImageException.corrupt("Not a GIF image");
            }
            width = unsigned16(header, 6);
            height = unsigned16(header, 8);
            int flags = header[10] & 0xFF;
            if (width == 0 || height == 0) {
                throw ImageException.corrupt("Invalid GIF size");
            }
            if ((long) width * height > maxPixels) {
                throw ImageException.unsupported("Image too large: " + width + "×" + height);
            }
            if ((flags & 0x80) != 0) {
                globalTable = colourTable(2 << (flags & 7));
            }
            info = new ImageInfo(width, height, 1);
        }
        return info;
    }

    private static int unsigned16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8;
    }

    private int readByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("Truncated GIF");
        }
        return value;
    }

    private int[] colourTable(int size) throws IOException {
        byte[] data = in.readNBytes(3 * size);
        if (data.length < 3 * size) {
            throw new EOFException("Truncated GIF");
        }
        int[] table = new int[256];
        Arrays.fill(table, WHITE);
        for (int i = 0; i < size; i++) {
            table[i] = (data[3 * i] & 0xFF) << 16 | (data[3 * i + 1] & 0xFF) << 8 | data[3 * i + 2] & 0xFF;
        }
        return table;
    }

    /** Skips data sub-blocks: each a length byte and that many bytes, ended by a zero length. */
    private void skipBlocks() throws IOException {
        for (int length = readByte(); length > 0; length = readByte()) {
            in.skipNBytes(length);
        }
    }

    @Override
    public void decode(RowSink sink, int minWidth, int minHeight, Area area) throws IOException {
        if (decoded) {
            throw new IllegalStateException("Image already decoded");
        }
        decoded = true;
        info();
        // Find the first image, noting the transparent colour of its graphic control extension.
        while (true) {
            int block = readByte();
            if (block == 0x2C) {
                break;
            }
            if (block == 0x3B) {
                throw ImageException.corrupt("GIF without an image");
            }
            if (block != 0x21) {
                throw ImageException.corrupt("Invalid GIF block");
            }
            int label = readByte();
            if (label == 0xF9) {
                int length = readByte();
                byte[] control = in.readNBytes(length);
                if (length >= 4 && (control[0] & 1) != 0) {
                    transparent = control[3] & 0xFF;
                }
                skipBlocks();
            } else {
                skipBlocks();
            }
        }
        byte[] descriptor = in.readNBytes(9);
        if (descriptor.length < 9) {
            throw new EOFException("Truncated GIF");
        }
        int frameLeft = unsigned16(descriptor, 0);
        int frameTop = unsigned16(descriptor, 2);
        int frameWidth = unsigned16(descriptor, 4);
        int frameHeight = unsigned16(descriptor, 6);
        int flags = descriptor[8] & 0xFF;
        int[] table = (flags & 0x80) != 0 ? colourTable(2 << (flags & 7)) : globalTable;
        if (table == null) {
            throw ImageException.corrupt("GIF without colour table");
        }
        if ((long) frameWidth * frameHeight > maxPixels) {
            throw ImageException.unsupported("Image too large");
        }
        byte[] indices = new byte[frameWidth * frameHeight];
        lzw(indices);
        if ((flags & 0x40) != 0) {
            indices = deinterlace(indices, frameWidth, frameHeight);
        }

        Area all = area == null ? new Area(0, 0, width, height) : area;
        int left = Math.clamp(all.x(), 0, width - 1);
        int top = Math.clamp(all.y(), 0, height - 1);
        int right = Math.clamp((long) all.x() + all.width(), left + 1, width);
        int bottom = Math.clamp((long) all.y() + all.height(), top + 1, height);
        int[] row = new int[right - left];
        sink.begin(right - left, bottom - top);
        for (int y = top; y < bottom; y++) {
            int frameY = y - frameTop;
            for (int x = left; x < right; x++) {
                int frameX = x - frameLeft;
                int colour = WHITE;
                if (frameY >= 0 && frameY < frameHeight && frameX >= 0 && frameX < frameWidth) {
                    int index = indices[frameY * frameWidth + frameX] & 0xFF;
                    colour = index == transparent ? WHITE : table[index];
                }
                row[x - left] = colour;
            }
            sink.row(row);
        }
    }

    /** Rows stored in the order of the four interlace passes, put back in order. */
    private static byte[] deinterlace(byte[] stored, int width, int height) {
        byte[] result = new byte[stored.length];
        int[] start = {0, 4, 2, 1};
        int[] step = {8, 8, 4, 2};
        int row = 0;
        for (int pass = 0; pass < 4; pass++) {
            for (int y = start[pass]; y < height; y += step[pass]) {
                System.arraycopy(stored, row * width, result, y * width, width);
                row++;
            }
        }
        return result;
    }

    // LZW with variable code sizes (GIF89a appendix F). Pixels past the end of damaged data stay 0.
    private void lzw(byte[] target) throws IOException {
        int minimum = readByte();
        if (minimum < 1 || minimum > 11) {
            throw ImageException.corrupt("Invalid GIF code size");
        }
        int clear = 1 << minimum;
        int end = clear + 1;
        int[] prefix = new int[MAX_CODES];
        byte[] suffix = new byte[MAX_CODES];
        byte[] first = new byte[MAX_CODES];
        byte[] stack = new byte[MAX_CODES + 1];
        for (int i = 0; i < clear; i++) {
            suffix[i] = (byte) i;
            first[i] = (byte) i;
        }
        int size = minimum + 1;
        int next = end + 1;
        int previous = -1;
        int written = 0;

        int bits = 0;
        int count = 0;
        int blockLeft = 0;
        boolean dataEnded = false;
        while (written < target.length) {
            while (count < size && !dataEnded) {
                if (blockLeft == 0) {
                    blockLeft = in.read();
                    if (blockLeft <= 0) {
                        dataEnded = true;
                        break;
                    }
                }
                int value = in.read();
                if (value < 0) {
                    dataEnded = true;
                    break;
                }
                blockLeft--;
                bits |= value << count;
                count += 8;
            }
            if (count < size) {
                break;
            }
            int code = bits & ((1 << size) - 1);
            bits >>>= size;
            count -= size;
            if (code == clear) {
                size = minimum + 1;
                next = end + 1;
                previous = -1;
                continue;
            }
            if (code == end) {
                break;
            }
            int top = 0;
            int current = code;
            if (previous >= 0 && code >= next) {
                // The code being defined: the previous string and its own first byte.
                stack[top++] = first[previous];
                current = previous;
            } else if (code >= next) {
                throw ImageException.corrupt("Invalid GIF code");
            }
            while (current >= clear) {
                stack[top++] = suffix[current];
                current = prefix[current];
            }
            stack[top++] = (byte) current;
            while (top > 0 && written < target.length) {
                target[written++] = stack[--top];
            }
            if (previous >= 0 && next < MAX_CODES) {
                prefix[next] = previous;
                first[next] = first[previous];
                suffix[next] = (byte) current;
                next++;
                if (next == 1 << size && size < 12) {
                    size++;
                }
            }
            previous = code;
        }
        // Skip the rest of the image data.
        if (!dataEnded) {
            if (blockLeft > 0) {
                in.skipNBytes(blockLeft);
            }
        }
    }
}
