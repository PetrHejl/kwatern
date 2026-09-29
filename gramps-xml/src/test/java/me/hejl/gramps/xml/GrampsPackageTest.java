package me.hejl.gramps.xml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Packages as Python's tarfile writes them for Gramps: ustar, PAX for long and non-ASCII names, GNU long names. */
class GrampsPackageTest {

    private static final Path EXAMPLE = Path.of(System.getProperty("gramps.example"));
    private static final String CZECH =
            "fotky/Osoby, portrét - Škaloudová Růžena a další dlouhé jméno souboru" + " přesahující sto bajtů.jpg";

    @TempDir
    Path temp;

    /** A minimal tar writer for the tests. */
    private static final class TarWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        TarWriter file(String name, byte[] data) {
            byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
            if (utf8.length > 100 || utf8.length != name.length()) {
                String record = "path=" + name + "\n";
                // The length counts its own digits, the space and the record.
                int bytes = record.getBytes(StandardCharsets.UTF_8).length;
                int length = bytes + 2;
                while (length != bytes + 1 + String.valueOf(length).length()) {
                    length = bytes + 1 + String.valueOf(length).length();
                }
                byte[] pax = (length + " " + record).getBytes(StandardCharsets.UTF_8);
                entry("PaxHeader/x", 'x', pax);
                entry("placeholder", '0', data);
            } else {
                entry(name, '0', data);
            }
            return this;
        }

        TarWriter gnuLongName(String name, byte[] data) {
            entry("././@LongLink", 'L', (name + "\0").getBytes(StandardCharsets.UTF_8));
            entry(name.substring(0, 50), '0', data);
            return this;
        }

        TarWriter entry(String name, char type, byte[] data) {
            byte[] header = new byte[512];
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(bytes, 0, header, 0, Math.min(100, bytes.length));
            octal(header, 100, 8, 0644);
            octal(header, 124, 12, data.length);
            octal(header, 136, 12, 1_700_000_000L);
            header[156] = (byte) type;
            System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 6);
            header[263] = '0';
            header[264] = '0';
            for (int i = 148; i < 156; i++) {
                header[i] = ' ';
            }
            long sum = 0;
            for (byte b : header) {
                sum += b & 0xFF;
            }
            octal(header, 148, 7, sum);
            out.writeBytes(header);
            out.writeBytes(data);
            out.writeBytes(new byte[(512 - data.length % 512) % 512]);
            return this;
        }

        private static void octal(byte[] header, int offset, int length, long value) {
            String text = String.format("%0" + (length - 1) + "o", value);
            System.arraycopy(text.getBytes(StandardCharsets.US_ASCII), 0, header, offset, length - 1);
        }

        void writeGzip(Path file) throws IOException {
            out.writeBytes(new byte[1024]);
            try (OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file))) {
                out.writeTo(gzip);
            }
        }
    }

    private Path samplePackage() throws IOException {
        Path file = temp.resolve("tree.gpkg");
        new TarWriter()
                .file("photo.jpg", new byte[] {1, 2, 3})
                .file(CZECH, new byte[700])
                .gnuLongName("scans/" + "a".repeat(120) + ".png", new byte[] {4})
                .file("../outside.jpg", new byte[] {5})
                .entry("link.jpg", '2', new byte[0])
                .file("private.jpg", new byte[] {6})
                .file(GrampsPackage.DATA, Files.readAllBytes(EXAMPLE))
                .writeGzip(file);
        return file;
    }

    @Test
    void readsTheExportInAPackage() throws IOException {
        Path file = samplePackage();
        assertTrue(GrampsPackage.isPackage(file));
        assertFalse(GrampsPackage.isPackage(EXAMPLE), "a plain export is not a package");
        ParseResult result = GrampsPackage.read(file);
        assertEquals(
                GrampsXml.read(EXAMPLE).database().people().size(),
                result.database().people().size());
    }

    @Test
    void extractsOnlyTheNamedFiles() throws IOException {
        Path file = samplePackage();
        Path media = temp.resolve("media");
        String longName = "scans/" + "a".repeat(120) + ".png";
        var extraction =
                GrampsPackage.extract(file, media, Set.of("photo.jpg", CZECH, longName, "../outside.jpg", "link.jpg"));
        assertEquals(new GrampsPackage.Extraction(3, 0), extraction);
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(media.resolve("photo.jpg")));
        assertEquals(700, Files.size(media.resolve(CZECH)), "PAX name with non-ASCII letters, over 100 bytes");
        assertArrayEquals(new byte[] {4}, Files.readAllBytes(media.resolve(longName)), "GNU long name");
        assertFalse(Files.exists(temp.resolve("outside.jpg")), "never outside the directory");
        assertFalse(Files.exists(media.resolve("link.jpg")), "links are not extracted");
        assertFalse(Files.exists(media.resolve("private.jpg")), "only the named files");
    }

    @Test
    void leavesSpaceFree() throws IOException {
        // As on a disk that the files would fill: a package from someone else can hold huge files of zeros.
        Path media = temp.resolve("media");
        var extraction = GrampsPackage.extract(samplePackage(), media, Set.of("photo.jpg", CZECH), Long.MAX_VALUE / 2);
        assertEquals(new GrampsPackage.Extraction(0, 2), extraction);
        assertFalse(Files.exists(media.resolve("photo.jpg")));
    }

    @Test
    void namesMediaAsPythonDoes() {
        assertEquals("home/user/photos/a.jpg", GrampsPackage.archiveName("/home/user/photos/a.jpg"));
        assertEquals("photos/a.jpg", GrampsPackage.archiveName("photos\\a.jpg"));
    }

    @Test
    void rejectsPackagesWithoutExport() throws IOException {
        Path file = temp.resolve("empty.gpkg");
        new TarWriter().file("photo.jpg", new byte[] {1}).writeGzip(file);
        assertThrows(IOException.class, () -> GrampsPackage.read(file));
    }

    @Test
    void readsPastMalformedPaxRecords() throws IOException {
        Path file = temp.resolve("odd.gpkg");
        // A length too short even for its own digits: the header is ignored from there on.
        new TarWriter()
                .entry("PaxHeader/x", 'x', "1 path=a\n".getBytes(StandardCharsets.US_ASCII))
                .file(GrampsPackage.DATA, Files.readAllBytes(EXAMPLE))
                .writeGzip(file);
        assertEquals(
                GrampsXml.read(EXAMPLE).database().people().size(),
                GrampsPackage.read(file).database().people().size());
    }

    @Test
    void rejectsInvalidPaxSizes() throws IOException {
        for (String size : List.of("-5", "x")) {
            Path file = temp.resolve("size" + size + ".gpkg");
            String record = "size=" + size + "\n";
            new TarWriter()
                    .entry(
                            "PaxHeader/x",
                            'x',
                            ((record.length() + 3) + " " + record).getBytes(StandardCharsets.US_ASCII))
                    .file(GrampsPackage.DATA, Files.readAllBytes(EXAMPLE))
                    .writeGzip(file);
            assertThrows(IOException.class, () -> GrampsPackage.read(file), size);
        }
    }
}
