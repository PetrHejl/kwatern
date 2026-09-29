package me.hejl.kwatern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notices when the export file changes. It is checked at an interval rather than watched with the file system's
 * notifications, which do not work on every file system (network mounts, some containers) and report each
 * write while a file is being copied. A change counts once the file's size and time have stayed the same for
 * one more check, so a file still being written is never read. Changes are reported on one background thread,
 * one at a time.
 */
final class ExportWatcher implements AutoCloseable {

    /** What to do with a changed file; it runs on the watcher's thread. */
    interface Listener {
        void changed(Path file);
    }

    private record Stamp(long modified, long size) {}

    private final Path file;
    private final ScheduledExecutorService executor;
    // Set before the first check, which the executor then sees.
    private Listener listener;
    private Stamp loaded;
    private Stamp seen;

    private ExportWatcher(Path file) {
        this.file = file;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "export-watcher");
            thread.setDaemon(true);
            return thread;
        });
        this.loaded = stamp();
    }

    /**
     * Notes the file as it is now, before it is loaded: a change while it is being loaded is then reported once
     * the watching starts, instead of being taken for the version loaded.
     */
    static ExportWatcher of(Path file) {
        return new ExportWatcher(file);
    }

    /** Starts watching a file as it is now; the listener hears of later changes. */
    static ExportWatcher start(Path file, Duration interval, Listener listener) {
        return of(file).start(interval, listener);
    }

    /** Starts watching; the listener hears of changes since the file was noted by {@link #of}. */
    ExportWatcher start(Duration interval, Listener listener) {
        this.listener = listener;
        long millis = interval.toMillis();
        executor.scheduleWithFixedDelay(this::check, millis, millis, TimeUnit.MILLISECONDS);
        return this;
    }

    private Stamp stamp() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new Stamp(attributes.lastModifiedTime().toMillis(), attributes.size());
        } catch (IOException e) {
            // Missing for a moment, e.g. while being replaced.
            return null;
        }
    }

    private void check() {
        try {
            Stamp now = stamp();
            if (now == null || now.equals(loaded)) {
                seen = null;
                return;
            }
            if (now.equals(seen)) {
                loaded = now;
                seen = null;
                listener.changed(file);
            } else {
                seen = now;
            }
        } catch (RuntimeException e) {
            // A failing listener must not stop the checks. Its type only, as the message may quote the tree.
            System.err.println("Reloading failed: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
