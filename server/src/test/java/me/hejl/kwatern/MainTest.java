package me.hejl.kwatern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class MainTest {

    /** What a run of the command line printed and returned. */
    private record Run(int exitCode, String out, String err) {}

    private static Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
        int exitCode = commandLine.execute(args);
        return new Run(exitCode, out.toString(), err.toString());
    }

    @Test
    void helpStartsWithShortSynopsis() {
        Run run = run("--help");
        assertEquals(0, run.exitCode());
        assertTrue(run.out().startsWith("""
                Usage: kwatern [OPTIONS] EXPORT
                       kwatern passwd --users=FILE [OPTIONS] NAME
                       kwatern help [COMMAND]
                """), run.out());
    }

    @Test
    void helpCommandShowsHelpOfCommand() {
        assertEquals(run("--help").out(), run("help").out());
        Run passwd = run("help", "passwd");
        assertEquals(0, passwd.exitCode());
        assertTrue(passwd.out().startsWith("Usage: kwatern passwd"), passwd.out());
    }

    @Test
    void invalidInputPrintsErrorAndHintOnly() {
        Run run = run("--bogus");
        assertEquals(2, run.exitCode());
        assertEquals("""
                Unknown option: '--bogus'
                Try 'kwatern --help' for more information.
                """, run.err().replace(System.lineSeparator(), "\n"));
        assertFalse(run.err().contains("Usage:"));
    }

    @Test
    void hintNamesTheSubcommand() {
        Run run = run("passwd", "--users=users.txt");
        assertEquals(2, run.exitCode());
        assertTrue(run.err().contains("Try 'kwatern passwd --help'"), run.err());
    }

    @Test
    void passwdSetsWhatAMemberSees(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("users.txt");
        assertEquals(0, passwd("passwd", "--users", file.toString(), "--living=show", "tereza"));
        String line = Files.readString(file).strip();
        assertTrue(line.startsWith("tereza:living:pbkdf2-sha256$"), "hash not shown");

        // What is left out stays as it was.
        assertEquals(
                0,
                run("passwd", "--users", file.toString(), "--private=show", "--keep-password", "tereza")
                        .exitCode());
        assertEquals(
                line.replace(":living:", ":living,private:"),
                Files.readString(file).strip(),
                "the same password");
        assertEquals(0, passwd("passwd", "--users", file.toString(), "tereza"));
        String changed = Files.readString(file).strip();
        assertTrue(changed.startsWith("tereza:living,private:"), "a new password takes nothing away");
        assertNotEquals(line.substring(line.lastIndexOf(':')), changed.substring(changed.lastIndexOf(':')));
        assertEquals(
                0,
                run("passwd", "--users", file.toString(), "--living=hide", "--keep-password", "tereza")
                        .exitCode());
        assertTrue(Files.readString(file).startsWith("tereza:private:"));

        Run nothing = run("passwd", "--users", file.toString(), "--keep-password", "tereza");
        assertEquals(2, nothing.exitCode());
        assertTrue(nothing.err().contains("Nothing to change"), nothing.err());
        Run nobody = run("passwd", "--users", file.toString(), "--living=show", "--keep-password", "jana");
        assertEquals(2, nobody.exitCode());
        assertTrue(nobody.err().contains("jana is not in"), nobody.err());

        // A new member sees nothing more unless granted.
        assertEquals(0, passwd("passwd", "--users", file.toString(), "jana"));
        assertTrue(Files.readString(file).contains("\njana::pbkdf2-sha256$"));
    }

    /** Runs kwatern passwd with a password on standard input, returning the exit code. */
    private static int passwd(String... args) {
        InputStream in = System.in;
        try {
            System.setIn(new ByteArrayInputStream("a long password\n".getBytes(StandardCharsets.UTF_8)));
            return run(args).exitCode();
        } finally {
            System.setIn(in);
        }
    }

    @Test
    void enumValuesAreNamedAsInHelp() {
        Run run = run("--living=maybe", "tree.gramps");
        assertEquals(2, run.exitCode());
        assertTrue(run.err().contains("expected one of hide, show but was 'maybe'"), run.err());
        assertTrue(run("--access=nobody", "tree.gramps").err().contains("expected one of open, members, private"));
        assertTrue(run("--theme=Dark", "missing.gramps").err().contains("Not a file"));
    }

    @Test
    void exportThatCannotBeLoadedIsNamedWithoutQuotingIt(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("tree.gramps"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <database xmlns="http://gramps-project.org/xml/1.7.2/"><people><person><name><first>&Novak;</first>
                """);
        Run run = run("--check", file.toString());
        assertEquals(1, run.exitCode());
        assertTrue(run.err().startsWith("cannot load tree.gramps: Malformed Gramps XML at line 2, column "), run.err());
        assertFalse(run.err().contains("Novak") || run.err().contains("\tat "), run.err());
    }

    @Test
    void refusesMediaOptionsForAPackage(@TempDir Path directory) throws IOException {
        // An empty tar archive, compressed: enough to be recognised as a package.
        byte[] header = new byte[512];
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 5);
        Path file = directory.resolve("tree.gpkg");
        try (var out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(header);
        }
        Run mediaDir = run("--check", "--media-dir", directory.toString(), file.toString());
        assertEquals(2, mediaDir.exitCode());
        assertTrue(mediaDir.err().contains("--media-dir does not apply to a package"), mediaDir.err());
        Run anywhere = run("--check", "--allow-media-anywhere", file.toString());
        assertEquals(2, anywhere.exitCode());
        assertTrue(anywhere.err().contains("--allow-media-anywhere does not apply to a package"), anywhere.err());
    }

    @Test
    void tellsWhetherOthersCanReadAFile(@TempDir Path directory) throws IOException {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path file = Files.writeString(directory.resolve("secret"), "key");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        assertFalse(Main.othersCanRead(file));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        assertTrue(Main.othersCanRead(file), "the group");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw----r--"));
        assertTrue(Main.othersCanRead(file), "everyone");
        assertFalse(Main.othersCanRead(directory.resolve("missing")));
    }
}
