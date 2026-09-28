package me.hejl.gramps.xml;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads tar archives as Python's {@code tarfile} writes them for Gramps packages: ustar headers, PAX extended
 * headers (long and non-ASCII names, large sizes) and GNU long names. Entries are read in order, each through
 * a stream that ends with the entry.
 */
final class Tar {

    private static final int BLOCK = 512;

    /** An entry of the archive; {@code regular} is false for directories, links and other special files. */
    record Entry(String name, long size, boolean regular) {}

    private final InputStream in;
    private final byte[] header = new byte[BLOCK];
    private long remaining;
    private long padding;

    Tar(InputStream in) {
        this.in = in;
    }

    /** The next entry, skipping what is left of the current one, or {@code null} at the end. */
    Entry next() throws IOException {
        skip(remaining + padding);
        remaining = 0;
        padding = 0;
        Map<String, String> pax = new HashMap<>();
        String longName = null;
        while (true) {
            if (in.readNBytes(header, 0, BLOCK) < BLOCK || empty(header)) {
                return null;
            }
            long size = number(header, 124, 12);
            char type = (char) (header[156] & 0xFF);
            if (size < 0) {
                throw new IOException("Invalid tar header");
            }
            switch (type) {
                case 'x' -> pax.putAll(pax(readText(size)));
                case 'g' -> skipData(size); // global PAX header: nothing we use
                case 'L' -> longName = trimNul(readText(size));
                case 'K' -> skipData(size); // GNU long link name: links are not extracted
                default -> {
                    String name =
                            pax.containsKey("path") ? pax.get("path") : longName != null ? longName : name(header);
                    if (pax.containsKey("size")) {
                        size = Long.parseLong(pax.get("size"));
                    }
                    remaining = size;
                    padding = pad(size);
                    return new Entry(name, size, type == '0' || type == 0 || type == '7');
                }
            }
        }
    }

    /** The content of the current entry; it ends with the entry and does not close the archive. */
    InputStream content() {
        return new FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int b = in.read();
                if (b >= 0) {
                    remaining--;
                }
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                int n = in.read(b, off, (int) Math.min(len, remaining));
                if (n > 0) {
                    remaining -= n;
                }
                return n;
            }

            @Override
            public long skip(long n) throws IOException {
                long skipped = in.skip(Math.min(n, remaining));
                remaining -= skipped;
                return skipped;
            }

            @Override
            public int available() throws IOException {
                return (int) Math.min(in.available(), remaining);
            }

            @Override
            public void close() {
                // The archive stays open for the next entry.
            }
        };
    }

    private String readText(long size) throws IOException {
        if (size > 1 << 20) {
            throw new IOException("Tar header too large");
        }
        byte[] data = in.readNBytes((int) size);
        if (data.length < size) {
            throw new EOFException("Truncated tar archive");
        }
        skip(pad(size));
        return new String(data, StandardCharsets.UTF_8);
    }

    private void skipData(long size) throws IOException {
        skip(size + pad(size));
    }

    private void skip(long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("Truncated tar archive");
                }
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static long pad(long size) {
        return (BLOCK - size % BLOCK) % BLOCK;
    }

    private static boolean empty(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** The ustar name: the prefix field, if any, then the name field. */
    private static String name(byte[] header) {
        String name = field(header, 0, 100);
        boolean ustar = new String(header, 257, 5, StandardCharsets.US_ASCII).equals("ustar");
        String prefix = ustar ? field(header, 345, 155) : "";
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    /** An octal number, or GNU's base-256 form for large values (first byte with the high bit set). */
    private static long number(byte[] header, int offset, int length) {
        if ((header[offset] & 0x80) != 0) {
            long value = header[offset] & 0x3F;
            for (int i = 1; i < length; i++) {
                value = value << 8 | (header[offset + i] & 0xFF);
            }
            return value;
        }
        int i = offset;
        int end = offset + length;
        while (i < end && (header[i] == ' ' || header[i] == 0)) {
            i++;
        }
        long value = 0;
        for (; i < end && header[i] >= '0' && header[i] <= '7'; i++) {
            value = value * 8 + (header[i] - '0');
        }
        return value;
    }

    /** PAX records: "LENGTH KEY=VALUE\n", where LENGTH counts the whole record in bytes. */
    private static Map<String, String> pax(String text) {
        Map<String, String> records = new HashMap<>();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int position = 0;
        while (position < bytes.length) {
            int space = position;
            while (space < bytes.length && bytes[space] != ' ') {
                space++;
            }
            if (space >= bytes.length) {
                break;
            }
            int length;
            try {
                length = Integer.parseInt(new String(bytes, position, space - position, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                break;
            }
            if (length <= 0 || position + length > bytes.length) {
                break;
            }
            String record = new String(bytes, space + 1, position + length - space - 2, StandardCharsets.UTF_8);
            int equals = record.indexOf('=');
            if (equals > 0) {
                records.put(record.substring(0, equals), record.substring(equals + 1));
            }
            position += length;
        }
        return records;
    }

    private static String trimNul(String text) {
        int nul = text.indexOf('\0');
        return nul < 0 ? text : text.substring(0, nul);
    }
}
