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
    private final Listener listener;
    private final ScheduledExecutorService executor;
    private Stamp loaded;
    private Stamp seen;

    private ExportWatcher(Path file, Listener listener) {
        this.file = file;
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "export-watcher");
            thread.setDaemon(true);
            return thread;
        });
        this.loaded = stamp();
    }

    /** Starts watching a file as it is now; the listener hears of later changes. */
    static ExportWatcher start(Path file, Duration interval, Listener listener) {
        ExportWatcher watcher = new ExportWatcher(file, listener);
        long millis = interval.toMillis();
        watcher.executor.scheduleWithFixedDelay(watcher::check, millis, millis, TimeUnit.MILLISECONDS);
        return watcher;
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
            // A failing listener must not stop the checks.
            System.err.println("Reloading failed: " + e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
