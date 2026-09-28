package me.hejl.kwatern.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down guessing passwords. Failed attempts are counted per address and per name; after five within 15
 * minutes, the address or name must wait a minute, doubling with each further failure up to 15 minutes. Kept in
 * memory only. Thread-safe.
 */
final class Attempts {

    private static final int FREE = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration FIRST_WAIT = Duration.ofMinutes(1);
    private static final Duration MAX_WAIT = Duration.ofMinutes(15);
    // Beyond this many counters, those no longer relevant are dropped.
    private static final int MAX_ENTRIES = 10_000;

    private record Entry(int failures, Instant first, Instant blockedUntil) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    Attempts(Clock clock) {
        this.clock = clock;
    }

    /** How long the address or the name must still wait; zero if they may try now. */
    Duration wait(String address, String name) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (String key : new String[] {"a:" + address, "n:" + name}) {
            Entry entry = entries.get(key);
            if (entry != null && entry.blockedUntil().isAfter(now)) {
                Duration left = Duration.between(now, entry.blockedUntil());
                wait = left.compareTo(wait) > 0 ? left : wait;
            }
        }
        return wait;
    }

    void failed(String address, String name) {
        Instant now = clock.instant();
        if (entries.size() > MAX_ENTRIES) {
            entries.values().removeIf(e -> stale(e, now));
        }
        for (String key : new String[] {"a:" + address, "n:" + name}) {
            entries.compute(key, (k, entry) -> {
                if (entry == null || stale(entry, now)) {
                    return new Entry(1, now, Instant.MIN);
                }
                int failures = entry.failures() + 1;
                Instant blockedUntil = entry.blockedUntil();
                if (failures >= FREE) {
                    Duration wait = FIRST_WAIT.multipliedBy(1L << Math.min(failures - FREE, 4));
                    blockedUntil = now.plus(wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait);
                }
                return new Entry(failures, entry.first(), blockedUntil);
            });
        }
    }

    /** A successful sign-in clears the name's failures; the address keeps its own. */
    void succeeded(String name) {
        entries.remove("n:" + name);
    }

    private static boolean stale(Entry entry, Instant now) {
        return entry.first().plus(WINDOW).isBefore(now) && !entry.blockedUntil().isAfter(now);
    }
}
