package me.hejl.image;

import java.io.InputStream;

/** An image format: recognises its files and reads them. */
public interface ImageDecoder {

    /** How many bytes from the start of a file {@link #accepts} needs at most. */
    int HEADER_LENGTH = 16;

    /** Whether a file starting with these bytes is in this format; may get fewer bytes for short files. */
    boolean accepts(byte[] header);

    ImageReader open(InputStream in);
}
