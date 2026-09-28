package me.hejl.image.jpeg;

import java.io.InputStream;
import me.hejl.image.ImageDecoder;
import me.hejl.image.ImageReader;

/** JPEG files: they start with the SOI marker and another marker. */
public final class JpegDecoder implements ImageDecoder {

    @Override
    public boolean accepts(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    @Override
    public ImageReader open(InputStream in) {
        return new JpegReader(in);
    }
}
