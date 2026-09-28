package me.hejl.image;

import java.io.IOException;

/** An image that is damaged, or uses a feature the decoder does not support. */
public class ImageException extends IOException {

    private final boolean unsupported;

    public ImageException(String message, boolean unsupported) {
        super(message);
        this.unsupported = unsupported;
    }

    public static ImageException corrupt(String message) {
        return new ImageException(message, false);
    }

    public static ImageException unsupported(String message) {
        return new ImageException(message, true);
    }

    /** Whether the image is valid but uses a feature this decoder does not implement. */
    public boolean unsupported() {
        return unsupported;
    }
}
