package me.hejl.image.jpeg;

import java.io.IOException;
import me.hejl.image.ImageException;

/**
 * Reads the entropy-coded data of a scan bit by bit. A 0xFF byte followed by 0x00 stands for 0xFF; any other
 * byte after 0xFF is a marker, which ends the data: the reader leaves it in the input and returns zero bits
 * from then on, as libjpeg does, so that truncated images decode as far as they go.
 */
final class BitReader {

    private final Input in;
    private long bits;
    private int count;
    private boolean ended;

    BitReader(Input in) {
        this.in = in;
    }

    /** Forgets buffered bits, at the start of a scan or after a restart marker. */
    void reset() {
        bits = 0;
        count = 0;
        ended = false;
    }

    /** Treats the data as ended: all further bits are zero. */
    void end() {
        ended = true;
    }

    private void fill() throws IOException {
        byte[] buffer = in.buffer;
        while (count <= 56) {
            // Plain bytes straight from the buffer; 0xFF, the end of the buffer and the end of data the slow way.
            int position = in.position;
            int value;
            if (!ended && position < in.limit && (value = buffer[position] & 0xFF) != 0xFF) {
                in.position = position + 1;
            } else {
                value = nextByte();
            }
            bits = bits << 8 | value;
            count += 8;
        }
    }

    /** The next {@link Huffman#FAST_BITS} bits, without consuming them. */
    int peek() throws IOException {
        if (count < 16) {
            fill();
        }
        return (int) (bits >>> (count - Huffman.FAST_BITS)) & ((1 << Huffman.FAST_BITS) - 1);
    }

    /** Consumes bits seen with {@link #peek()}. */
    void skip(int length) {
        count -= length;
    }

    private int nextByte() throws IOException {
        if (ended) {
            return 0;
        }
        int value = in.read();
        if (value < 0) {
            ended = true;
            return 0;
        }
        if (value == 0xFF) {
            int following = in.read();
            if (following == 0) {
                return 0xFF;
            }
            // A marker: leave it, including the 0xFF, for the marker reader.
            if (following >= 0) {
                in.unread();
            }
            in.unread();
            ended = true;
            return 0;
        }
        return value;
    }

    int bit() throws IOException {
        if (count < 1) {
            fill();
        }
        count--;
        return (int) (bits >>> count) & 1;
    }

    /** The next {@code length} bits (0 to 16) as an unsigned number. */
    int bits(int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (count < length) {
            fill();
        }
        count -= length;
        return (int) (bits >>> count) & ((1 << length) - 1);
    }

    /** The next {@code length} bits as a signed coefficient value (T.81 F.2.2.1, EXTEND). */
    int signed(int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        int value = bits(length);
        return value < 1 << (length - 1) ? value - (1 << length) + 1 : value;
    }

    int decode(Huffman table) throws IOException {
        if (count < 16) {
            fill();
        }
        int prefix = (int) (bits >>> (count - Huffman.FAST_BITS)) & ((1 << Huffman.FAST_BITS) - 1);
        int entry = table.fast[prefix];
        if (entry != 0) {
            count -= entry >>> 8;
            return entry & 0xFF;
        }
        int code = (int) (bits >>> (count - 16)) & 0xFFFF;
        for (int length = Huffman.FAST_BITS + 1; length <= 16; length++) {
            int candidate = code >>> (16 - length);
            if (candidate <= table.maxCode[length]) {
                count -= length;
                return table.values[table.offset[length] + candidate];
            }
        }
        if (ended) {
            // Zero bits after the end of the data: treat as zero, like libjpeg.
            count -= 16;
            return 0;
        }
        throw ImageException.corrupt("Invalid Huffman code");
    }
}
