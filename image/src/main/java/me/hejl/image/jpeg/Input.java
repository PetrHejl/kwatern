package me.hejl.image.jpeg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Buffered bytes from a stream, where the last two bytes read can always be pushed back. */
final class Input {

    private final InputStream in;
    final byte[] buffer = new byte[64 * 1024];
    int position;
    int limit;

    Input(InputStream in) {
        this.in = in;
    }

    /** The next byte, or -1 at the end of the stream. */
    int read() throws IOException {
        if (position == limit && !fill()) {
            return -1;
        }
        return buffer[position++] & 0xFF;
    }

    int readByte() throws IOException {
        int value = read();
        if (value < 0) {
            throw new EOFException("Unexpected end of image");
        }
        return value;
    }

    int readShort() throws IOException {
        return readByte() << 8 | readByte();
    }

    void readFully(byte[] target) throws IOException {
        for (int i = 0; i < target.length; i++) {
            target[i] = (byte) readByte();
        }
    }

    void skip(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            readByte();
        }
    }

    /** Pushes back the byte just read; may be called twice in a row. */
    void unread() {
        position--;
    }

    private boolean fill() throws IOException {
        // Keep the last two bytes so that unread() still works after refilling.
        int kept = Math.min(2, limit);
        System.arraycopy(buffer, limit - kept, buffer, 0, kept);
        int count = in.readNBytes(buffer, kept, buffer.length - kept);
        position = kept;
        limit = kept + count;
        return count > 0;
    }
}
