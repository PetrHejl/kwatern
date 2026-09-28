package me.hejl.kwatern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The licenses of Kwatern and of the software it includes, which must go with every copy. They are built into
 * the program, so that a copy of the single executable carries them.
 */
final class Licenses {

    private static final String DIRECTORY = "/me/hejl/kwatern/";

    private static final List<String> FILES = List.of(
            "licenses/NOTICE.txt",
            "licenses/LICENSE.txt",
            "licenses/ICU.txt",
            "licenses/Apache-2.0.txt",
            "static/leaflet/LICENSE.txt",
            "static/fonts/OFL-Manrope.txt",
            "static/fonts/OFL-SourceSans3.txt");

    // Copied from the GraalVM that builds the native executable (server/build.gradle), whose runtime and class
    // library it contains. A JVM build contains neither.
    private static final List<String> NATIVE_FILES = List.of(
            "licenses/graalvm/SOURCE.txt",
            "licenses/graalvm/LICENSE_NATIVEIMAGE.txt",
            "licenses/graalvm/THIRD_PARTY_LICENSE.txt");

    private Licenses() {}

    static List<String> files() {
        List<String> files = new ArrayList<>(FILES);
        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            files.addAll(NATIVE_FILES);
        }
        return files;
    }

    /** All the texts, each under a heading with its file name. */
    static String text() throws IOException {
        var text = new StringBuilder();
        for (String file : files()) {
            try (InputStream in = Licenses.class.getResourceAsStream(DIRECTORY + file)) {
                if (in == null) {
                    throw new IOException("license file missing from this build: " + file);
                }
                text.append("==== ")
                        .append(file)
                        .append(" ====\n\n")
                        .append(new String(in.readAllBytes(), StandardCharsets.UTF_8).strip())
                        .append("\n\n");
            }
        }
        return text.toString();
    }
}
