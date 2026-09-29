package me.hejl.kwatern.auth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

/**
 * Writes the files with secrets, the users file and the key that signs sessions, so that they are never half
 * written: a new version is written beside the file and moved over it. Written in place, a full disk or a crash
 * left an empty or cut file, which lost every member or kept the server from starting. New files are readable by
 * their owner only, where the file system allows, as temporary files are.
 */
final class SecretFiles {

    private SecretFiles() {}

    /** Creates a file with the content; fails if it exists, so that a key made by someone else is kept. */
    static void create(Path file, byte[] content) throws IOException {
        Path written = write(file, content);
        try {
            // Without replacing, a move checks that nothing is there; on one file system it is still a rename.
            Files.move(written, file);
        } finally {
            Files.deleteIfExists(written);
        }
    }

    /** Replaces a file with the content, keeping its permissions and, where allowed, its owner. */
    static void replace(Path file, byte[] content) throws IOException {
        Path written = write(file, content);
        try {
            if (Files.exists(file)) {
                keepOwnerAndPermissions(file, written);
            }
            Files.move(written, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(written);
        }
    }

    /** Writes the content to a new file beside the given one, all the way to the disk. */
    private static Path write(Path file, byte[] content) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path written = Files.createTempFile(absolute.getParent(), "." + absolute.getFileName(), ".tmp");
        try (FileChannel channel = FileChannel.open(written, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(written);
            throw e;
        }
        return written;
    }

    private static void keepOwnerAndPermissions(Path file, Path written) throws IOException {
        PosixFileAttributes old;
        try {
            old = Files.readAttributes(file, PosixFileAttributes.class);
        } catch (UnsupportedOperationException e) {
            return;
        }
        Files.setPosixFilePermissions(written, old.permissions());
        try {
            // Where the server runs as another user, e.g. after sudo, it must still be able to read the file.
            PosixFileAttributeView view = Files.getFileAttributeView(written, PosixFileAttributeView.class);
            view.setGroup(old.group());
            view.setOwner(old.owner());
        } catch (IOException e) {
            // Only root may give a file away; the file then belongs to whoever wrote it.
        }
    }
}
