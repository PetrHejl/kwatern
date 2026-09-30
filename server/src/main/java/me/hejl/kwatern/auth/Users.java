package me.hejl.kwatern.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The users file: one {@code name:grants:hash} line per user, as {@code kwatern passwd} writes it, where grants
 * are what the user sees beyond the public view ({@link Grants}); blank lines and lines starting with {@code #}
 * are ignored. The file is read again when it changes, so removing a line signs
 * that user out; a file that has become unreadable or malformed keeps the previous users. Thread-safe.
 */
public final class Users {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}._@-]{1,64}");
    // How often a lookup checks whether the file has changed.
    private static final long CHECK_MILLIS = 2_000;

    /** A user as the file has them: what they see, and their password hash. */
    public record Member(Grants grants, String hash) {}

    private record Snapshot(long modified, long size, Map<String, Member> members) {}

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
    public static Users of(Map<String, Member> members) {
        return new Users(null, new Snapshot(0, 0, Map.copyOf(members)));
    }

    /** Whether a name can be a user's: letters, digits and {@code . _ @ -}, at most 64 characters. */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A user, or {@code null} if there is no such user. */
    public Member member(String name) {
        refresh();
        return snapshot.members().get(name);
    }

    public int size() {
        return snapshot.members().size();
    }

    /** How many users have each grants, in the order of {@link Grants#ALL}; only those some user has. */
    public Map<Grants, Integer> countByGrants() {
        Map<Grants, Integer> counts = new LinkedHashMap<>();
        Grants.ALL.forEach(grants -> {
            int count = (int) snapshot.members().values().stream()
                    .filter(m -> m.grants().equals(grants))
                    .count();
            if (count > 0) {
                counts.put(grants, count);
            }
        });
        return counts;
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
                        file.getFileName(), snapshot.members().size());
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("users: cannot read " + file + ", keeping the previous users: " + e.getMessage());
        }
    }

    private static Snapshot read(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        Map<String, Member> members = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        return new Snapshot(attributes.lastModifiedTime().toMillis(), attributes.size(), Map.copyOf(members));
    }

    static Map<String, Member> parse(List<String> lines) {
        Map<String, Member> members = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(":", -1);
            if (parts.length != 3 || !validName(parts[0]) || !PasswordHash.wellFormed(parts[2])) {
                throw new IllegalArgumentException(
                        "line " + (i + 1) + " is not name:grants:hash as written by kwatern passwd");
            }
            Grants grants;
            try {
                grants = Grants.parse(parts[1]);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage());
            }
            if (members.put(parts[0], new Member(grants, parts[2])) != null) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + parts[0] + " is there twice");
            }
        }
        return members;
    }

    /**
     * Sets a user in the file, replacing their line or adding one, and keeping all other lines. A new
     * file is readable by its owner only, where the file system allows; an existing one keeps its permissions.
     */
    public static void put(Path file, String name, Member member) throws IOException {
        // Through a link, the file it points to is replaced, not the link.
        Path target = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath();
        List<String> lines = new ArrayList<>();
        if (Files.exists(target)) {
            lines.addAll(Files.readAllLines(target, StandardCharsets.UTF_8));
        }
        String line = name + ":" + member.grants().format() + ":" + member.hash();
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
        SecretFiles.replace(target, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
