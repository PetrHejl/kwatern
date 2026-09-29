package me.hejl.kwatern.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed session cookies. The cookie holds the user's name, when it expires and whether to keep it, signed with
 * HMAC-SHA256; the server stores nothing. The signature also covers the user's password hash, so changing the
 * password or removing the user from the users file ends their sessions. Thread-safe.
 */
public final class Sessions {

    /** A session that is signed in. */
    public record Session(String user, boolean keep, Instant expires) {}

    // "Keep me signed in": a month, renewed while in use; otherwise until the browser closes, at most 12 hours.
    static final Duration KEEP = Duration.ofDays(30);
    static final Duration SHORT = Duration.ofHours(12);
    // A cookie is renewed at most this often, so that most responses do not set it.
    private static final Duration RENEW_AFTER = Duration.ofHours(1);
    private static final int KEY_BYTES = 32;
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final byte[] key;
    private final Users users;
    private final Clock clock;

    public Sessions(byte[] key, Users users) {
        this(key, users, Clock.systemUTC());
    }

    Sessions(byte[] key, Users users, Clock clock) {
        this.key = key.clone();
        this.users = users;
        this.clock = clock;
    }

    /** A new random key: sessions signed with it end when the server stops. */
    public static byte[] randomKey() {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /**
     * The key in a file, so that sessions outlive a restart; a missing file is created with a new key, readable
     * by its owner only where the file system allows.
     */
    public static byte[] keyFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            byte[] key = randomKey();
            // Created whole: an empty file, left by a crash between creating and writing it, stopped the next start.
            SecretFiles.create(
                    file, (Base64.getEncoder().encodeToString(key) + "\n").getBytes(StandardCharsets.US_ASCII));
            return key;
        }
        try {
            byte[] key = Base64.getDecoder()
                    .decode(Files.readString(file, StandardCharsets.US_ASCII).strip());
            if (key.length < KEY_BYTES) {
                throw new IOException(file + " holds a key shorter than " + KEY_BYTES + " bytes");
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new IOException(file + " does not hold a Base64 key", e);
        }
    }

    /**
     * The cookie value for a user who has just signed in, or {@code null} if they have been removed from the users
     * file since, which can happen between checking a password or session and this.
     */
    public String issue(String user, boolean keep) {
        String hash = users.hash(user);
        if (hash == null) {
            return null;
        }
        Instant expires = clock.instant().plus(keep ? KEEP : SHORT);
        String payload = BASE64.encodeToString(user.getBytes(StandardCharsets.UTF_8)) + "." + expires.getEpochSecond()
                + "." + (keep ? "1" : "0");
        return payload + "." + BASE64.encodeToString(sign(payload, hash));
    }

    /** The session of a cookie value, or {@code null} if it is missing, forged, expired or its user is gone. */
    public Session check(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            String user = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[1]));
            boolean keep = parts[2].equals("1");
            byte[] signature = Base64.getUrlDecoder().decode(parts[3]);
            String hash = users.hash(user);
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (hash == null || !MessageDigest.isEqual(signature, sign(payload, hash))) {
                return null;
            }
            return expires.isAfter(clock.instant()) ? new Session(user, keep, expires) : null;
        } catch (IllegalArgumentException | DateTimeException e) {
            return null;
        }
    }

    /** Whether to send the session a new cookie with a later expiry, as it is being used. */
    public boolean renew(Session session) {
        Duration lifetime = session.keep() ? KEEP : SHORT;
        return session.expires().isBefore(clock.instant().plus(lifetime).minus(RENEW_AFTER));
    }

    /** How long a browser should keep the cookie, or {@code null} for until it closes. */
    public static Duration maxAge(boolean keep) {
        return keep ? KEEP : null;
    }

    private byte[] sign(String payload, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(payload.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(passwordHash.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
