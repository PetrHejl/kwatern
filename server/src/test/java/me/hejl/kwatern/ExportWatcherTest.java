package me.hejl.kwatern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportWatcherTest {

    @TempDir
    Path temp;

    private static void touch(Path file, String content, long secondsFromNow) throws IOException {
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(secondsFromNow)));
    }

    @Test
    void reportsAChangeOnceTheFileIsStable() throws Exception {
        Path file = temp.resolve("tree.gramps");
        touch(file, "first", 0);
        var changes = new LinkedBlockingQueue<Path>();
        try (var watcher = ExportWatcher.start(file, Duration.ofMillis(40), changes::add)) {
            Thread.sleep(200);
            assertTrue(changes.isEmpty(), "an unchanged file is not reported");

            touch(file, "second, longer", 5);
            assertEquals(file, changes.poll(2, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertTrue(changes.isEmpty(), "reported once");
        }
    }

    @Test
    void waitsWhileTheFileIsBeingWritten() throws Exception {
        Path file = temp.resolve("tree.gramps");
        touch(file, "x", 0);
        var changes = new LinkedBlockingQueue<Path>();
        try (var watcher = ExportWatcher.start(file, Duration.ofMillis(100), changes::add)) {
            // A copy in progress: the file changes at every check.
            for (int i = 1; i <= 6; i++) {
                touch(file, "x".repeat(i * 10), i);
                Thread.sleep(60);
            }
            assertTrue(changes.size() <= 1, "not reported for every write");
            assertEquals(file, changes.poll(2, TimeUnit.SECONDS));
        }
    }
}
