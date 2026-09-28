package me.hejl.kwatern.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashes: PBKDF2 with HMAC-SHA256, which the JDK provides, written as
 * {@code pbkdf2-sha256$ITERATIONS$SALT$HASH} with the salt and hash in Base64. Argon2 or scrypt would resist
 * guessing on graphics cards better, but need a library.
 */
public final class PasswordHash {

    // OWASP's recommendation for PBKDF2-HMAC-SHA256 (2023); about a third of a second per check.
    static final int ITERATIONS = 600_000;
    private static final String SCHEME = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    // Bounds for hashes read from a file, so that a mistyped one cannot make each check take minutes.
    private static final int MIN_ITERATIONS = 1_000;
    private static final int MAX_ITERATIONS = 10_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHash() {}

    /** A new hash of the password, with a random salt. */
    public static String hash(char[] password) {
        return hash(password, ITERATIONS);
    }

    static String hash(char[] password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
        return String.join(
                "$",
                SCHEME,
                String.valueOf(iterations),
                base64.encodeToString(salt),
                base64.encodeToString(derive(password, salt, iterations)));
    }

    /** Whether a stored hash has the expected form; it says nothing about the password. */
    public static boolean wellFormed(String stored) {
        return parse(stored) != null;
    }

    /** Whether the password matches the stored hash; {@code false} for a malformed hash. */
    public static boolean verify(char[] password, String stored) {
        Parsed parsed = parse(stored);
        if (parsed == null) {
            return false;
        }
        return MessageDigest.isEqual(parsed.hash(), derive(password, parsed.salt(), parsed.iterations()));
    }

    /**
     * Takes as long as checking a password, for names that have no user, so that the time of an answer does
     * not tell whether a name exists.
     */
    static void verifyNothing(char[] password) {
        verify(password, Dummy.HASH);
    }

    private static final class Dummy {
        static final String HASH = hash(new char[0]);
    }

    private record Parsed(int iterations, byte[] salt, byte[] hash) {}

    private static Parsed parse(String stored) {
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals(SCHEME)) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] hash = Base64.getDecoder().decode(parts[3]);
            if (iterations < MIN_ITERATIONS
                    || iterations > MAX_ITERATIONS
                    || salt.length < 8
                    || hash.length != HASH_BITS / 8) {
                return null;
            }
            return new Parsed(iterations, salt, hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
