package me.hejl.kwatern.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * Signing in: who may see the members' view, and how that is checked. Thread-safe.
 *
 * @see Access
 */
public final class Login {

    /** Who sees what; see {@code --access}. */
    public enum Access {
        /** Everyone sees the public view; there is no signing in. */
        OPEN,
        /** Everyone sees the public view; members who sign in see the members' view. */
        MEMBERS,
        /** Only members who sign in see anything, the members' view. */
        PRIVATE
    }

    /** The outcome of signing in. */
    public sealed interface Result {
        record SignedIn(String user) implements Result {}

        record Wrong() implements Result {}

        record TooMany(Duration retryAfter) implements Result {}
    }

    // Password checks are slow on purpose; this many at a time keeps a flood of them from taking every core.
    private static final int CONCURRENT_CHECKS = 2;

    private final Access access;
    private final Users users;
    private final Sessions sessions;
    private final boolean behindProxy;
    private final Attempts attempts;
    private final Semaphore checks = new Semaphore(CONCURRENT_CHECKS, true);

    /**
     * @param behindProxy whether requests come through a reverse proxy, whose {@code X-Forwarded-*} headers are
     *                    then trusted for the visitor's address and whether they use HTTPS
     */
    public Login(Access access, Users users, Sessions sessions, boolean behindProxy) {
        this(access, users, sessions, behindProxy, Clock.systemUTC());
    }

    Login(Access access, Users users, Sessions sessions, boolean behindProxy, Clock clock) {
        if (access == Access.OPEN) {
            throw new IllegalArgumentException("an open site has no login");
        }
        this.access = access;
        this.users = users;
        this.sessions = sessions;
        this.behindProxy = behindProxy;
        this.attempts = new Attempts(clock);
    }

    public Access access() {
        return access;
    }

    public Sessions sessions() {
        return sessions;
    }

    public boolean behindProxy() {
        return behindProxy;
    }

    /**
     * Checks a name and password from the sign-in form. The answer is the same for an unknown name and a wrong
     * password, and takes as long.
     *
     * @param address the visitor's address, for slowing down guessing
     */
    public Result signIn(String address, String given, char[] password) {
        // Counted under one key, so that made-up long names cannot fill the memory.
        String name = Users.validName(given) ? given : "";
        Duration wait = attempts.wait(address, name);
        if (!wait.isZero()) {
            return new Result.TooMany(wait);
        }
        boolean right;
        checks.acquireUninterruptibly();
        try {
            String hash = name.isEmpty() ? null : users.hash(name);
            if (hash == null) {
                PasswordHash.verifyNothing(password);
                right = false;
            } else {
                right = PasswordHash.verify(password, hash);
            }
        } finally {
            checks.release();
        }
        if (!right) {
            attempts.failed(address, name);
            return new Result.Wrong();
        }
        attempts.succeeded(name);
        return new Result.SignedIn(name);
    }
}
