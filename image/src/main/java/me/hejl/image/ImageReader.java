package me.hejl.image;

import java.io.IOException;

/** Reads one image from a stream: first its header, then its pixels, once. */
public interface ImageReader {

    /** Reads the header, if not done yet. */
    ImageInfo info() throws IOException;

    /** The media type of the format, such as {@code image/jpeg}. */
    String mimeType();

    /**
     * Decodes an area of the image into the sink, row by row. Decoders may deliver it reduced, as long as it is
     * at least {@code minWidth × minHeight} pixels (or the full size, if that is smaller), which saves time and
     * memory for thumbnails; the sink is told the actual size.
     *
     * @param area the part to decode in pixels of the stored image, clipped to it; {@code null} for all of it
     */
    void decode(RowSink sink, int minWidth, int minHeight, Area area) throws IOException;
}
