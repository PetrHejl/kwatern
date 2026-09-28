package me.hejl.image;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import me.hejl.image.gif.GifDecoder;
import me.hejl.image.jpeg.JpegDecoder;
import me.hejl.image.png.PngDecoder;

/**
 * Picks the decoder for a file by its first bytes, never by its name or declared type, which are sometimes
 * wrong. The decoders are a plain list, so another implementation can be used by passing a different one.
 */
public final class ImageDecoders {

    /** The decoders of this module. */
    public static final ImageDecoders DEFAULT =
            new ImageDecoders(List.of(new JpegDecoder(), new PngDecoder(), new GifDecoder()));

    private final List<ImageDecoder> decoders;

    public ImageDecoders(List<ImageDecoder> decoders) {
        this.decoders = List.copyOf(decoders);
    }

    /**
     * A reader for the image in the stream.
     *
     * @throws ImageException marked unsupported if no decoder recognises the format
     */
    public ImageReader open(InputStream in) throws IOException {
        InputStream buffered = in.markSupported() ? in : new BufferedInputStream(in);
        buffered.mark(ImageDecoder.HEADER_LENGTH);
        byte[] header = buffered.readNBytes(ImageDecoder.HEADER_LENGTH);
        buffered.reset();
        for (ImageDecoder decoder : decoders) {
            if (decoder.accepts(header)) {
                return decoder.open(buffered);
            }
        }
        throw ImageException.unsupported("Unknown image format");
    }
}
