package me.hejl.gramps.xml;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Gramps packages ({@code .gpkg}): a gzip-compressed tar archive with the media files, each under its path as
 * stored in the tree, and the export as {@code data.gramps}, written last. Gramps writes the media paths of
 * the export without a leading {@code /} so that they match the names in the archive.
 */
// Gramps: plugins/export/exportpkg.py PackageWriter, plugins/importer/importgpkg.py
public final class GrampsPackage {

    /** The name of the export in the archive. */
    public static final String DATA = "data.gramps";

    private GrampsPackage() {}

    /** Whether a file is a Gramps package: a gzip-compressed tar archive. */
    public static boolean isPackage(Path file) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            raw.mark(2);
            if (raw.read() != 0x1f || raw.read() != 0x8b) {
                return false;
            }
            raw.reset();
            byte[] start = new GZIPInputStream(raw).readNBytes(263);
            return start.length == 263 && new String(start, 257, 5, StandardCharsets.US_ASCII).equals("ustar");
        } catch (IOException e) {
            return false;
        }
    }

    /** Reads the export in a package, without extracting anything. */
    public static ParseResult read(Path file) throws IOException {
        try (InputStream in = open(file)) {
            Tar tar = new Tar(in);
            for (Tar.Entry entry = tar.next(); entry != null; entry = tar.next()) {
                if (entry.regular() && normalize(entry.name()).equals(DATA)) {
                    return GrampsXml.read(tar.content());
                }
            }
        }
        throw new GrampsParseException("No " + DATA + " in the package " + file.getFileName());
    }

    /**
     * What extracting a package did.
     *
     * @param extracted files written
     * @param noSpace   files left out because they would have left less than {@link #KEEP_FREE} free
     */
    public record Extraction(int extracted, int noSpace) {}

    /**
     * Space left free on the file system that media are extracted to. A package may come from someone else, and a
     * small one can hold huge files of zeros, which would fill the disk, or the memory where {@code /tmp} is kept
     * in memory.
     */
    public static final long KEEP_FREE = 256L << 20;

    /**
     * Extracts the media files with the given names (media paths as {@link #archiveName} gives them) into a
     * directory. Other entries, and any whose name would lead outside the directory, are skipped, and so are files
     * that would leave less than {@link #KEEP_FREE} bytes free.
     */
    public static Extraction extract(Path file, Path directory, Set<String> names) throws IOException {
        return extract(file, directory, names, KEEP_FREE);
    }

    static Extraction extract(Path file, Path directory, Set<String> names, long keepFree) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        FileStore store = Files.getFileStore(Files.createDirectories(root));
        int count = 0;
        int noSpace = 0;
        try (InputStream in = open(file)) {
            Tar tar = new Tar(in);
            for (Tar.Entry entry = tar.next(); entry != null; entry = tar.next()) {
                String name = normalize(entry.name());
                if (!entry.regular() || !names.contains(name)) {
                    continue;
                }
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root) || target.equals(root)) {
                    continue;
                }
                if (entry.size() > store.getUsableSpace() - keepFree) {
                    noSpace++;
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(tar.content(), target, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return new Extraction(count, noSpace);
    }

    /** The name a media path has in a package: with forward slashes and no leading slash, as Python writes it. */
    public static String archiveName(String mediaPath) {
        return normalize(mediaPath.replace('\\', '/'));
    }

    private static String normalize(String name) {
        String result = name;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        return result;
    }

    private static InputStream open(Path file) throws IOException {
        return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file), 64 * 1024), 64 * 1024);
    }
}
