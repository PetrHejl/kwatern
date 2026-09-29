package me.hejl.kwatern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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
                       kwatern passwd --users=FILE NAME
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
    void enumValuesAreNamedAsInHelp() {
        Run run = run("--living=maybe", "tree.gramps");
        assertEquals(2, run.exitCode());
        assertTrue(run.err().contains("expected one of hide, show but was 'maybe'"), run.err());
        assertTrue(run("--access=nobody", "tree.gramps").err().contains("expected one of open, members, private"));
        assertTrue(run("--theme=Dark", "missing.gramps").err().contains("Not a file"));
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
