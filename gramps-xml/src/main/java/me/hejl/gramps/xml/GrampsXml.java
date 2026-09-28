package me.hejl.gramps.xml;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Reads Gramps XML exports ({@code .gramps}), gzip-compressed or plain. */
public final class GrampsXml {

    private GrampsXml() {}

    public static ParseResult read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        }
    }

    /** Reads an export from a stream, which may be gzip-compressed. The stream is not closed. */
    public static ParseResult read(InputStream in) throws IOException {
        var buffered = new BufferedInputStream(in, 64 * 1024);
        buffered.mark(2);
        int b1 = buffered.read();
        int b2 = buffered.read();
        buffered.reset();
        boolean gzip = b1 == 0x1f && b2 == 0x8b;
        InputStream xml = gzip ? new GZIPInputStream(buffered, 64 * 1024) : buffered;
        return GrampsXmlParser.parse(xml);
    }
}
