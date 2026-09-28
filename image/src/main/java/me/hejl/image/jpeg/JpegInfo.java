package me.hejl.image.jpeg;

/**
 * What the header of a JPEG image says.
 *
 * @param components 1 for greyscale, 3 for colour, 4 for CMYK
 * @param orientation the EXIF orientation, 1 (as stored) to 8
 */
public record JpegInfo(int width, int height, int components, boolean progressive, int orientation) {}
