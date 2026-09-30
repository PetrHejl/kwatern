package me.hejl.kwatern.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthTest {

    // Few iterations keep the tests fast; the format and the check are the same.
    private static final String HASH = PasswordHash.hash("right password".toCharArray(), 1_000);
    private static final Users.Member MEMBER = new Users.Member(Grants.NONE, HASH);

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-09-28T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void hashesAndChecksPasswords() {
        assertTrue(HASH.matches("pbkdf2-sha256\\$1000\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}"), HASH);
        assertTrue(PasswordHash.verify("right password".toCharArray(), HASH));
        assertFalse(PasswordHash.verify("wrong password".toCharArray(), HASH));
        assertFalse(PasswordHash.verify(new char[0], HASH));
        assertFalse(HASH.equals(PasswordHash.hash("right password".toCharArray(), 1_000)), "a new salt for each hash");
        for (String bad :
                new String[] {"", "x", "md5$1$a$b", "pbkdf2-sha256$99999999$AAAAAAAAAAA$AAAA", "pbkdf2-sha256$x$a$b"}) {
            assertFalse(PasswordHash.wellFormed(bad), bad);
            assertFalse(PasswordHash.verify("right password".toCharArray(), bad), bad);
        }
    }

    @Test
    void readsTheUsersFile(@TempDir Path directory) throws Exception {
        Map<String, Users.Member> users = Users.parse(List.of(
                "# members", "", "jana::" + HASH, "  petr@hejl.me:private,living:" + HASH, "eva:private:" + HASH));
        assertEquals(
                Map.of(
                        "jana", MEMBER,
                        "petr@hejl.me", new Users.Member(new Grants(true, true), HASH),
                        "eva", new Users.Member(new Grants(false, true), HASH)),
                users);
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana")));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana::secret")));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana:" + HASH)), "no grants");
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana:all:" + HASH)));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana:living,:" + HASH)));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana:living,living:" + HASH)));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("ja na::" + HASH)));
        assertThrows(IllegalArgumentException.class, () -> Users.parse(List.of("jana::" + HASH, "jana::" + HASH)));
        for (Grants grants : Grants.ALL) {
            assertEquals(grants, Grants.parse(grants.toString()));
        }
        assertTrue(Users.validName("Jiří.Novák"));
        assertFalse(Users.validName("a:b"));
        assertFalse(Users.validName("x".repeat(65)));

        Path file = directory.resolve("users.txt");
        Users.put(file, "jana", MEMBER);
        Files.writeString(file, "# comment\n" + Files.readString(file));
        Users.put(file, "petr", MEMBER);
        String other = PasswordHash.hash("another one".toCharArray(), 1_000);
        Users.put(file, "jana", new Users.Member(new Grants(true, false), other));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(
                List.of("# comment", "jana:living:" + other, "petr::" + HASH),
                lines,
                "replaced in place, comment kept");

        Users loaded = Users.load(file);
        assertEquals(new Users.Member(new Grants(true, false), other), loaded.member("jana"));
        assertNull(loaded.member("nobody"));
    }

    @Test
    void replacesTheUsersFileWhole(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("users.txt");
        Users.put(file, "jana", MEMBER);
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        Object before = Files.readAttributes(file, BasicFileAttributes.class).fileKey();

        Users.put(file, "petr", MEMBER);
        // A new file moved over the old one: written in place, a crash while writing lost every member.
        assertNotEquals(
                before, Files.readAttributes(file, BasicFileAttributes.class).fileKey());
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(file), "kept");
        assertEquals(List.of("jana::" + HASH, "petr::" + HASH), Files.readAllLines(file));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(file), files.toList(), "nothing left beside it");
        }

        // A link to the file stays a link, and the file it points to changes.
        Path link = Files.createSymbolicLink(directory.resolve("link.txt"), file);
        Users.put(link, "eva", MEMBER);
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(3, Files.readAllLines(file).size());
    }

    @Test
    void signsSessions() {
        var clock = new TestClock();
        Users users = Users.of(Map.of("jana", MEMBER));
        byte[] key = Sessions.randomKey();
        var sessions = new Sessions(key, users, clock);

        assertNull(sessions.issue("nobody", true), "not a user, or no longer one");
        String cookie = sessions.issue("jana", true);
        Sessions.Session session = sessions.check(cookie);
        assertNotNull(session);
        assertEquals("jana", session.user());
        assertTrue(session.keep());
        assertFalse(sessions.renew(session), "just issued");
        clock.advance(Duration.ofHours(2));
        assertTrue(sessions.renew(sessions.check(cookie)), "renewed while in use");

        // Tampered with, or signed with another key, or for another user: no session.
        String[] parts = cookie.split("\\.");
        String longer = parts[0] + "." + (Long.parseLong(parts[1]) + 86_400) + "." + parts[2] + "." + parts[3];
        assertNull(sessions.check(longer));
        assertNull(new Sessions(Sessions.randomKey(), users, clock).check(cookie));
        assertNull(sessions.check("garbage"));
        assertNull(sessions.check(null));
        assertNull(sessions.check("a.b.c.d"));

        // A new password, or a removed user, ends the session.
        var changed = new Sessions(key, Users.of(Map.of("jana", new Users.Member(Grants.NONE, HASH + "x"))), clock);
        assertNull(changed.check(cookie));
        assertNull(new Sessions(key, Users.of(Map.of()), clock).check(cookie));

        // What the user sees comes from the users file, so a change applies to the cookie they have.
        assertEquals(Grants.NONE, session.grants());
        var granted = new Grants(true, false);
        assertEquals(
                granted,
                new Sessions(key, Users.of(Map.of("jana", new Users.Member(granted, HASH))), clock)
                        .check(cookie)
                        .grants());

        // Expired.
        String browserOnly = sessions.issue("jana", false);
        clock.advance(Sessions.SHORT.plusSeconds(1));
        assertNull(sessions.check(browserOnly));
        assertNotNull(sessions.check(cookie));
        clock.advance(Sessions.KEEP);
        assertNull(sessions.check(cookie));
    }

    @Test
    void keepsTheKeyInAFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("secret");
        byte[] first = Sessions.keyFile(file);
        assertEquals(32, first.length);
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(file), files.toList(), "written beside it and moved, nothing left");
        }
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SecretFiles.create(file, new byte[] {1}),
                "a key someone else made meanwhile is kept");
        byte[] again = Sessions.keyFile(file);
        assertTrue(Arrays.equals(first, again), "the same key after a restart");
        Files.writeString(file, "not base64!");
        assertThrows(IOException.class, () -> Sessions.keyFile(file));
        Files.writeString(file, "c2hvcnQ=");
        assertThrows(IOException.class, () -> Sessions.keyFile(file));
    }

    @Test
    void slowsDownGuessing() {
        var clock = new TestClock();
        var login = new Login(
                Login.Access.MEMBERS,
                Users.of(Map.of("jana", MEMBER)),
                new Sessions(Sessions.randomKey(), Users.of(Map.of("jana", MEMBER)), clock),
                false,
                clock);
        char[] wrong = "wrong password".toCharArray();
        for (int i = 0; i < 5; i++) {
            assertInstanceOf(Login.Result.Wrong.class, login.signIn("10.0.0.1", "jana", wrong));
        }
        var blocked = login.signIn("10.0.0.1", "jana", "right password".toCharArray());
        assertInstanceOf(Login.Result.TooMany.class, blocked, "even the right password waits");
        assertEquals(Duration.ofMinutes(1), ((Login.Result.TooMany) blocked).retryAfter());
        // The name is blocked from other addresses too, but other names are not.
        assertInstanceOf(Login.Result.TooMany.class, login.signIn("10.0.0.2", "jana", wrong));
        assertInstanceOf(Login.Result.Wrong.class, login.signIn("10.0.0.2", "petr", wrong));

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertInstanceOf(Login.Result.Wrong.class, login.signIn("10.0.0.1", "jana", wrong));
        var longer = login.signIn("10.0.0.1", "jana", wrong);
        assertEquals(Duration.ofMinutes(2), ((Login.Result.TooMany) longer).retryAfter(), "doubled");

        clock.advance(Duration.ofMinutes(30));
        assertEquals(
                new Login.Result.SignedIn("jana"), login.signIn("10.0.0.3", "jana", "right password".toCharArray()));
        assertInstanceOf(Login.Result.Wrong.class, login.signIn("10.0.0.3", "nobody", wrong), "unknown name");
        assertInstanceOf(Login.Result.Wrong.class, login.signIn("10.0.0.3", "not a name!", wrong));
    }

    @Test
    void countsAttemptsInFlight() {
        var clock = new TestClock();
        var attempts = new Attempts(clock);
        for (int i = 0; i < 5; i++) {
            assertEquals(Duration.ZERO, attempts.start("10.0.0." + i, "jana"));
        }
        assertEquals(Attempts.BUSY_WAIT, attempts.start("10.0.0.9", "jana"), "the name's free attempts are in flight");
        assertEquals(Duration.ZERO, attempts.start("10.0.0.0", "petr"), "the address has free attempts left");

        attempts.succeeded("10.0.0.0", "jana");
        assertEquals(Duration.ZERO, attempts.start("10.0.0.9", "jana"), "an ended attempt frees its place");
        for (int i = 1; i < 5; i++) {
            attempts.failed("10.0.0." + i, "jana");
        }
        assertEquals(Attempts.BUSY_WAIT, attempts.start("10.0.0.8", "jana"), "one free attempt left, in flight");
        attempts.failed("10.0.0.9", "jana");
        assertEquals(Duration.ofMinutes(1), attempts.start("10.0.0.8", "jana"), "five failures block the name");

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertEquals(Duration.ZERO, attempts.start("10.0.0.8", "jana"));
        assertEquals(Attempts.BUSY_WAIT, attempts.start("10.0.0.7", "jana"), "after a block, one at a time");
    }

    @Test
    void limitsAttemptsInFlightInAll() {
        var attempts = new Attempts(new TestClock());
        // Each from its own address for its own name, so that only the limit in all applies.
        for (int i = 0; i < Attempts.MAX_IN_FLIGHT; i++) {
            assertEquals(Duration.ZERO, attempts.start("10.0.1." + i, "name" + i));
        }
        assertEquals(Attempts.BUSY_WAIT, attempts.start("10.0.2.1", "other"));
        attempts.failed("10.0.1.0", "name0");
        assertEquals(Duration.ZERO, attempts.start("10.0.2.1", "other"), "an ended attempt frees its place");
        assertEquals(Attempts.BUSY_WAIT, attempts.start("10.0.2.2", "another"));
        attempts.succeeded("10.0.1.1", "name1");
        assertEquals(Duration.ZERO, attempts.start("10.0.2.2", "another"), "a successful one too");
    }

    @Test
    void checksNoMoreGuessesSentAtOnceThanAreFree() throws Exception {
        var login = new Login(
                Login.Access.MEMBERS,
                Users.of(Map.of("jana", MEMBER)),
                new Sessions(Sessions.randomKey(), Users.of(Map.of("jana", MEMBER))),
                false);
        var go = new CountDownLatch(1);
        List<Future<Login.Result>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                char[] guess = ("guess " + i).toCharArray();
                results.add(executor.submit(() -> {
                    go.await();
                    return login.signIn("10.0.0.1", "jana", guess);
                }));
            }
            go.countDown();
        }
        int checked = 0;
        for (Future<Login.Result> result : results) {
            if (result.get() instanceof Login.Result.Wrong) {
                checked++;
            }
        }
        // Failures and attempts in flight together never exceed the five free ones.
        assertEquals(5, checked);
        assertInstanceOf(Login.Result.TooMany.class, login.signIn("10.0.0.2", "jana", "right password".toCharArray()));
    }
}
