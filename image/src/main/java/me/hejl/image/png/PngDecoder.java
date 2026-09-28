package me.hejl.image.png;

import java.io.InputStream;
import me.hejl.image.ImageDecoder;
import me.hejl.image.ImageReader;

/** PNG files: they start with the eight-byte PNG signature. */
public final class PngDecoder implements ImageDecoder {

    @Override
    public boolean accepts(byte[] header) {
        if (header.length < PngReader.SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PngReader.SIGNATURE.length; i++) {
            if (header[i] != PngReader.SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ImageReader open(InputStream in) {
        return new PngReader(in);
    }
}
