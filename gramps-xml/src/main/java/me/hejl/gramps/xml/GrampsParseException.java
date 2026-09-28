package me.hejl.gramps.xml;

import java.io.IOException;

/** The input is not a Gramps XML export this parser can read. */
public class GrampsParseException extends IOException {

    public GrampsParseException(String message) {
        super(message);
    }

    public GrampsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
