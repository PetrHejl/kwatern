package me.hejl.image.gif;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import me.hejl.image.ImageDecoder;
import me.hejl.image.ImageReader;

/** GIF files: they start with "GIF87a" or "GIF89a". */
public final class GifDecoder implements ImageDecoder {

    @Override
    public boolean accepts(byte[] header) {
        if (header.length < 6) {
            return false;
        }
        String signature = new String(header, 0, 6, StandardCharsets.ISO_8859_1);
        return signature.equals("GIF87a") || signature.equals("GIF89a");
    }

    @Override
    public ImageReader open(InputStream in) {
        return new GifReader(in);
    }
}
