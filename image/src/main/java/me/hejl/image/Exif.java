package me.hejl.image;

/** Reads the orientation from EXIF data, which JPEG and PNG files carry in the same TIFF structure. */
public final class Exif {

    private Exif() {}

    /** Tag 0x0112 of the first IFD of the TIFF structure at {@code base}, or 1 if absent or invalid. */
    public static int orientation(byte[] data, int base) {
        if (base < 0 || data.length < base + 8) {
            return 1;
        }
        boolean little;
        if (data[base] == 'I' && data[base + 1] == 'I') {
            little = true;
        } else if (data[base] == 'M' && data[base + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifd = base + unsigned(data, base + 4, 4, little);
        if (ifd + 2 > data.length) {
            return 1;
        }
        int entries = (int) unsigned(data, (int) ifd, 2, little);
        for (int i = 0; i < entries; i++) {
            int entry = (int) ifd + 2 + 12 * i;
            if (entry + 12 > data.length) {
                break;
            }
            if (unsigned(data, entry, 2, little) == 0x0112 && unsigned(data, entry + 2, 2, little) == 3) {
                int value = (int) unsigned(data, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static long unsigned(byte[] data, int offset, int length, boolean little) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            int b = data[little ? offset + length - 1 - i : offset + i] & 0xFF;
            value = value << 8 | b;
        }
        return value;
    }
}
