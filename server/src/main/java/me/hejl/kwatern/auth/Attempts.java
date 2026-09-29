package me.hejl.kwatern.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Slows down guessing passwords. Failed attempts are counted per address and per name; after five within 15
 * minutes, the address or name must wait a minute, doubling with each further failure up to 15 minutes. Kept in
 * memory only. Thread-safe.
 *
 * <p>Attempts still being checked count too: an address or name may have only as many in flight as it has free
 * attempts left, so that many sent at once cannot all be checked before the first failure is counted. Attempts in
 * flight are also limited in all, as many addresses and names could otherwise queue without end.
 */
final class Attempts {

    private static final int FREE = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration FIRST_WAIT = Duration.ofMinutes(1);
    private static final Duration MAX_WAIT = Duration.ofMinutes(15);
    // When all of an address's or name's attempts are in flight; a check takes under a second.
    static final Duration BUSY_WAIT = Duration.ofSeconds(1);
    // Beyond this many counters, those no longer relevant are dropped.
    private static final int MAX_ENTRIES = 10_000;
    // Attempts in flight in all. With two password checks of about 0.6 s at a time, the last waits some 10 s;
    // further ones are refused at once rather than queued, which would take memory and keep members waiting.
    static final int MAX_IN_FLIGHT = 32;

    /** @param pending attempts started and not yet ended */
    private record Entry(int failures, Instant first, Instant blockedUntil, int pending) {
        static final Entry NONE = new Entry(0, Instant.MIN, Instant.MIN, 0);
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;
    private int inFlight;

    Attempts(Clock clock) {
        this.clock = clock;
    }

    /**
     * Starts an attempt if the address and the name may try now, and returns zero; else returns how long they must
     * still wait. Each started attempt must be ended with {@link #failed} or {@link #succeeded}.
     */
    synchronized Duration start(String address, String name) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (String key : keys(address, name)) {
            Entry entry = current(key, now);
            Duration left = entry.blockedUntil().isAfter(now)
                    ? Duration.between(now, entry.blockedUntil())
                    : entry.pending() >= Math.max(1, FREE - entry.failures()) ? BUSY_WAIT : Duration.ZERO;
            wait = left.compareTo(wait) > 0 ? left : wait;
        }
        if (wait.isZero() && inFlight >= MAX_IN_FLIGHT) {
            wait = BUSY_WAIT;
        }
        if (wait.isZero()) {
            inFlight++;
            for (String key : keys(address, name)) {
                Entry entry = current(key, now);
                store(key, new Entry(entry.failures(), entry.first(), entry.blockedUntil(), entry.pending() + 1));
            }
        }
        return wait;
    }

    synchronized void failed(String address, String name) {
        Instant now = clock.instant();
        inFlight--;
        if (entries.size() > MAX_ENTRIES) {
            entries.keySet().removeIf(key -> current(key, now).equals(Entry.NONE));
        }
        for (String key : keys(address, name)) {
            Entry entry = current(key, now);
            int failures = entry.failures() + 1;
            Instant blockedUntil = entry.blockedUntil();
            if (failures >= FREE) {
                Duration wait = FIRST_WAIT.multipliedBy(1L << Math.min(failures - FREE, 4));
                blockedUntil = now.plus(wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait);
            }
            Instant first = entry.failures() == 0 ? now : entry.first();
            store(key, new Entry(failures, first, blockedUntil, entry.pending() - 1));
        }
    }

    /** A successful sign-in clears the name's failures; the address keeps its own. */
    synchronized void succeeded(String address, String name) {
        Instant now = clock.instant();
        inFlight--;
        Entry byAddress = current("a:" + address, now);
        store(
                "a:" + address,
                new Entry(byAddress.failures(), byAddress.first(), byAddress.blockedUntil(), byAddress.pending() - 1));
        Entry byName = current("n:" + name, now);
        store("n:" + name, new Entry(0, Instant.MIN, Instant.MIN, byName.pending() - 1));
    }

    private static String[] keys(String address, String name) {
        return new String[] {"a:" + address, "n:" + name};
    }

    /** The counter of a key, its failures forgotten once they are old and no longer block. */
    private Entry current(String key, Instant now) {
        Entry entry = entries.getOrDefault(key, Entry.NONE);
        if (entry.failures() > 0
                && entry.first().plus(WINDOW).isBefore(now)
                && !entry.blockedUntil().isAfter(now)) {
            return new Entry(0, Instant.MIN, Instant.MIN, entry.pending());
        }
        return entry;
    }

    private void store(String key, Entry entry) {
        if (entry.equals(Entry.NONE)) {
            entries.remove(key);
        } else {
            entries.put(key, entry);
        }
    }
}
