package me.hejl.kwatern.web;

/**
 * One loaded version of the export: the views of it that are served, and what to clean up once it is no longer
 * needed, such as the directory a package's media were extracted to. That happens when a newer version has replaced
 * it and the last request still using it has ended, never while a request may read its files. Thread-safe.
 */
public final class Version {

    private final Sites sites;
    private final Runnable cleanUp;
    private int requests;
    private boolean replaced;
    private boolean cleanedUp;

    /** @param cleanUp run once when the version is replaced and no request uses it any more */
    public Version(Sites sites, Runnable cleanUp) {
        this.sites = sites;
        this.cleanUp = cleanUp;
    }

    /** A version with nothing to clean up. */
    public static Version of(Sites sites) {
        return new Version(sites, () -> {});
    }

    public Sites sites() {
        return sites;
    }

    /**
     * Starts a request with this version; {@code false} if it has been cleaned up already, when the request must
     * take the version that replaced it.
     */
    synchronized boolean enter() {
        if (cleanedUp) {
            return false;
        }
        requests++;
        return true;
    }

    /** Ends a request started with {@link #enter}. */
    void leave() {
        boolean now;
        synchronized (this) {
            requests--;
            now = cleanUpNow();
        }
        if (now) {
            cleanUp.run();
        }
    }

    /** No new request will get this version from the server; it is cleaned up once the ones using it have ended. */
    void replace() {
        boolean now;
        synchronized (this) {
            replaced = true;
            now = cleanUpNow();
        }
        if (now) {
            cleanUp.run();
        }
    }

    /** Whether to clean up now, which is then due only once; the clean-up itself runs outside the lock. */
    private boolean cleanUpNow() {
        if (replaced && requests == 0 && !cleanedUp) {
            cleanedUp = true;
            return true;
        }
        return false;
    }
}
