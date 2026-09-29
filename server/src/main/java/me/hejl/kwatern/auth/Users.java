package me.hejl.kwatern.auth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The users file: one {@code name:hash} line per user, as {@code kwatern passwd} writes it; blank lines and
 * lines starting with {@code #} are ignored. The file is read again when it changes, so removing a line signs
 * that user out; a file that has become unreadable or malformed keeps the previous users. Thread-safe.
 */
public final class Users {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}._@-]{1,64}");
    // How often a lookup checks whether the file has changed.
    private static final long CHECK_MILLIS = 2_000;

    private record Snapshot(long modified, long size, Map<String, String> hashes) {}

    private final Path file;
    private volatile Snapshot snapshot;
    private volatile long checked;

    private Users(Path file, Snapshot snapshot) {
        this.file = file;
        this.snapshot = snapshot;
        this.checked = System.currentTimeMillis();
    }

    /** Reads the file; fails if it cannot be read or a line is malformed. */
    public static Users load(Path file) throws IOException {
        return new Users(file, read(file));
    }

    /** Users that are not in a file, for tests and self-checks. */
    public static Users of(Map<String, String> hashes) {
        return new Users(null, new Snapshot(0, 0, Map.copyOf(hashes)));
    }

    /** Whether a name can be a user's: letters, digits and {@code . _ @ -}, at most 64 characters. */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** The stored password hash of a user, or {@code null} if there is no such user. */
    public String hash(String name) {
        refresh();
        return snapshot.hashes().get(name);
    }

    public int size() {
        return snapshot.hashes().size();
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (file == null || now - checked < CHECK_MILLIS) {
            return;
        }
        checked = now;
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            Snapshot current = snapshot;
            if (attributes.lastModifiedTime().toMillis() != current.modified() || attributes.size() != current.size()) {
                snapshot = read(file);
                System.out.printf(
                        "users: read %s again, %d users%n",
                        file.getFileName(), snapshot.hashes().size());
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("users: cannot read " + file + ", keeping the previous users: " + e.getMessage());
        }
    }

    private static Snapshot read(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        Map<String, String> hashes = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        return new Snapshot(attributes.lastModifiedTime().toMillis(), attributes.size(), Map.copyOf(hashes));
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> hashes = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            String name = colon < 0 ? line : line.substring(0, colon);
            String hash = colon < 0 ? "" : line.substring(colon + 1);
            if (!validName(name) || !PasswordHash.wellFormed(hash)) {
                throw new IllegalArgumentException(
                        "line " + (i + 1) + " is not name:hash as written by kwatern passwd");
            }
            if (hashes.put(name, hash) != null) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + name + " is there twice");
            }
        }
        return hashes;
    }

    /**
     * Sets a user's hash in the file, replacing their line or adding one, and keeping all other lines. A new
     * file is readable by its owner only, where the file system allows; an existing one keeps its permissions.
     */
    public static void put(Path file, String name, String hash) throws IOException {
        // Through a link, the file it points to is replaced, not the link.
        Path target = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath();
        List<String> lines = new ArrayList<>();
        if (Files.exists(target)) {
            lines.addAll(Files.readAllLines(target, StandardCharsets.UTF_8));
        }
        String line = name + ":" + hash;
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().startsWith(name + ":")) {
                lines.set(i, line);
                replaced = true;
            }
        }
        if (!replaced) {
            lines.add(line);
        }
        replace(target, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a new version beside the file and moves it over the file, so that the file is never half written:
     * written in place, a full disk or a crash lost every member.
     */
    private static void replace(Path file, byte[] content) throws IOException {
        // Readable by its owner only where the file system allows, as temporary files are.
        Path written = Files.createTempFile(file.getParent(), "." + file.getFileName(), ".tmp");
        try {
            if (Files.exists(file)) {
                keepOwnerAndPermissions(file, written);
            }
            try (FileChannel channel = FileChannel.open(written, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(written, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(written);
        }
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
            // Only root may give a file away; the file then belongs to whoever set the password.
        }
    }
}
