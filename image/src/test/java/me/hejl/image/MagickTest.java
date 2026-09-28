package me.hejl.image;

import static me.hejl.image.TestImages.maxDifference;
import static me.hejl.image.TestImages.readPnm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where ImageMagick is installed: every PNG colour type and bit depth, interlaced or not, with and without
 * transparency, and GIF variants, each compared with ImageMagick's own decoding over white.
 */
class MagickTest {

    private static boolean available;

    @TempDir
    static Path temp;

    @BeforeAll
    static void findMagick() {
        try {
            available = run(List.of("magick", "-version")) == 0;
        } catch (IOException e) {
            available = false;
        }
    }

    private static int run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** One variant: ImageMagick options, then the PNG colour type and bit depth it must produce (or -1 for GIF). */
    private record Variant(String name, List<String> options, int colourType, int depth) {}

    private static Variant png(String name, int colourType, int depth, String... options) {
        List<String> all = new ArrayList<>(List.of(options));
        all.addAll(List.of("-define", "png:color-type=" + colourType, "-define", "png:bit-depth=" + depth));
        return new Variant(name + ".png", all, colourType, depth);
    }

    private static List<Variant> variants() {
        List<Variant> variants = new ArrayList<>();
        for (String interlace : new String[] {"None", "PNG"}) {
            String suffix = interlace.equals("None") ? "" : "-interlaced";
            String[] i = {"-interlace", interlace};
            for (int depth : new int[] {1, 2, 4, 8, 16}) {
                variants.add(
                        png("grey" + depth + suffix, 0, depth, concat(i, "-colorspace", "Gray", "-depth", "" + depth)));
            }
            for (int depth : new int[] {8, 16}) {
                variants.add(png("rgb" + depth + suffix, 2, depth, i));
                variants.add(png(
                        "grey-alpha" + depth + suffix,
                        4,
                        depth,
                        concat(i, "-colorspace", "Gray", "-alpha", "set", "-channel", "A", "-fx", "i/w", "+channel")));
                variants.add(png(
                        "rgba" + depth + suffix,
                        6,
                        depth,
                        concat(i, "-alpha", "set", "-channel", "A", "-fx", "j/h", "+channel")));
            }
            for (int depth : new int[] {1, 2, 4, 8}) {
                variants.add(png("palette" + depth + suffix, 3, depth, concat(i, "-colors", "" + (1 << depth))));
            }
            variants.add(png("palette-trns" + suffix, 3, 8, concat(i, "-colors", "100", "-transparent", "black")));
            variants.add(png("grey-trns" + suffix, 0, 8, concat(i, "-colorspace", "Gray", "-transparent", "black")));
            variants.add(png(
                    "rgb-trns" + suffix,
                    2,
                    8,
                    concat(i, "-fill", "black", "-draw", "rectangle 5,5 20,20", "-transparent", "black")));
        }
        for (String interlace : new String[] {"None", "GIF"}) {
            String suffix = interlace.equals("None") ? "" : "-interlaced";
            for (int colours : new int[] {2, 16, 256}) {
                variants.add(new Variant(
                        "gif" + colours + suffix + ".gif",
                        List.of("-interlace", interlace, "-colors", "" + colours),
                        -1,
                        -1));
            }
            variants.add(new Variant(
                    "gif-transparent" + suffix + ".gif",
                    List.of("-interlace", interlace, "-colors", "64", "-transparent", "black"),
                    -1,
                    -1));
        }
        return variants;
    }

    private static String[] concat(String[] first, String... rest) {
        String[] all = new String[first.length + rest.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(rest, 0, all, first.length, rest.length);
        return all;
    }

    @Test
    void decodesLikeImageMagick() throws IOException {
        assumeTrue(available, "ImageMagick not installed");
        Path source = temp.resolve("source.ppm");
        try (InputStream in = TestImages.resource("jpeg/source.ppm")) {
            Files.copy(in, source);
        }
        // A larger, noisier picture too, so that GIF's code table fills up and is reset.
        Path noise = temp.resolve("noise.ppm");
        assertEquals(
                0,
                run(List.of(
                        "magick",
                        "-size",
                        "181x137",
                        "-seed",
                        "3",
                        "plasma:",
                        "+noise",
                        "Random",
                        "-depth",
                        "8",
                        noise.toString())));
        List<String> differences = new ArrayList<>();
        for (Path picture : List.of(source, noise)) {
            for (Variant variant : variants()) {
                Path file = temp.resolve(picture.getFileName() + "-" + variant.name());
                List<String> command = new ArrayList<>(List.of("magick", picture.toString(), "-strip"));
                command.addAll(variant.options());
                command.add(file.toString());
                assertEquals(0, run(command), variant.name());
                if (variant.colourType() >= 0) {
                    byte[] header = Files.readAllBytes(file);
                    assertEquals(variant.depth(), header[24], variant.name() + " bit depth");
                    assertEquals(variant.colourType(), header[25], variant.name() + " colour type");
                }
                Path expected = temp.resolve(file.getFileName() + ".ppm");
                assertEquals(
                        0,
                        run(List.of(
                                "magick",
                                file + "[0]",
                                "-background",
                                "white",
                                "-flatten",
                                "-depth",
                                "8",
                                "-type",
                                "TrueColor",
                                expected.toString())));
                Image ours;
                try (InputStream in = Files.newInputStream(file)) {
                    ours = TestImages.decode(in);
                }
                Image theirs;
                try (InputStream in = Files.newInputStream(expected)) {
                    theirs = readPnm(in);
                }
                int difference = maxDifference(theirs, ours);
                if (difference > 1) {
                    differences.add(file.getFileName() + ": " + difference);
                }
            }
        }
        assertEquals(List.of(), differences);
    }
}
