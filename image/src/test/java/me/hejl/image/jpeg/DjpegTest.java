package me.hejl.image.jpeg;

import static me.hejl.image.TestImages.boxAverage;
import static me.hejl.image.TestImages.psnr;
import static me.hejl.image.TestImages.readPnm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import me.hejl.gramps.model.GrampsDatabase;
import me.hejl.gramps.model.Media;
import me.hejl.gramps.xml.GrampsXml;
import me.hejl.image.Image;
import me.hejl.image.ImageBuilder;
import me.hejl.image.ImageException;
import me.hejl.image.TestImages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Compares the decoder with libjpeg's {@code djpeg} (float IDCT, no smoothing, which does the same arithmetic up
 * to rounding) where it is installed: the test images, and with {@code -PgrampsFile} all JPEG files of a real
 * export. Only counts are printed for the export, never file names.
 *
 * <p>Reduced sizes are compared with libjpeg's full-size result averaged over blocks, which is what this decoder
 * computes. libjpeg's own reduced sizes differ for chroma subsampled in one direction only (4:2:2, 4:4:0, 4:1:1),
 * which it enlarges by repeating pixels.
 */
class DjpegTest {

    private static boolean available;

    @BeforeAll
    static void findDjpeg() {
        try {
            Process process = new ProcessBuilder("djpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            available = process.waitFor() == 0;
        } catch (IOException e) {
            available = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** djpeg's output for a file at {@code eighths}/8 of its size. */
    private static Image djpeg(Path file, int eighths) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                        "djpeg", "-dct", "float", "-nosmooth", "-scale", eighths + "/8", "-pnm", file.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        Image image;
        try (InputStream out = process.getInputStream()) {
            image = readPnm(out);
            out.readAllBytes();
        }
        assertEquals(0, process.waitFor());
        return image;
    }

    private static Image ours(Path file, int eighths) throws IOException {
        ImageBuilder builder = new ImageBuilder();
        try (InputStream in = Files.newInputStream(file)) {
            new JpegReader(in).decode(builder, eighths);
        }
        return builder.image();
    }

    @Test
    void matchesLibjpegOnTestImages() throws Exception {
        assumeTrue(available, "djpeg not installed");
        Path directory = Files.createTempDirectory("jpeg");
        List<String> names = new ArrayList<>(List.of(JpegReaderTest.COLOUR));
        names.remove("cmyk.jpg"); // djpeg cannot write CMYK as PPM
        names.add("grey.jpg");
        names.add("grey-progressive.jpg");
        names.add("orientation-6.jpg");
        List<String> differences = new ArrayList<>();
        try {
            for (String name : names) {
                Path file = directory.resolve(name);
                Files.write(file, TestImages.bytes("jpeg/" + name));
                Image full = djpeg(file, 8);
                for (int eighths : new int[] {8, 4, 2, 1}) {
                    double psnr = psnr(boxAverage(full, eighths), ours(file, eighths));
                    double limit = eighths == 8 ? 40 : JpegReaderTest.dcOnly(name, eighths) ? 20 : 35;
                    if (psnr <= limit) {
                        differences.add(String.format(Locale.ROOT, "%s at %d/8: %.1f dB", name, eighths, psnr));
                    }
                }
            }
        } finally {
            try (var files = Files.list(directory)) {
                files.forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            Files.delete(directory);
        }
        assertEquals(List.of(), differences);
    }

    @Test
    void matchesLibjpegOnRealExport() throws Exception {
        String export = System.getProperty("gramps.file");
        assumeTrue(export != null && !export.isBlank(), "no -PgrampsFile given");
        assumeTrue(available, "djpeg not installed");
        GrampsDatabase db = GrampsXml.read(Path.of(export)).database();
        Path base = Path.of(db.header().mediaPath() == null ? "" : db.header().mediaPath());
        List<Path> files = db.media().all().stream()
                .map(Media::path)
                .filter(path -> path.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g"))
                .map(base::resolve)
                .filter(Files::isRegularFile)
                .distinct()
                .toList();
        int compared = 0;
        int progressive = 0;
        Map<String, Integer> failures = new TreeMap<>();
        // Worst PSNR at full size, at 1/8 against libjpeg's full size averaged, and at 1/8 for progressive images,
        // which then use only DC coefficients.
        double[] worst = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        long ourTime = 0;
        long started = System.nanoTime();
        for (Path file : files) {
            try {
                JpegInfo info;
                try (InputStream in = Files.newInputStream(file)) {
                    info = new JpegReader(in).header();
                }
                if (info.progressive()) {
                    progressive++;
                }
                long start = System.nanoTime();
                Image small = ours(file, 1);
                ourTime += System.nanoTime() - start;
                int kind = info.progressive() ? 2 : 1;
                // Full size only for images of reasonable size; the largest would need gigabytes as int arrays.
                if ((long) info.width() * info.height() < 30_000_000) {
                    Image full = djpeg(file, 8);
                    start = System.nanoTime();
                    Image ours = ours(file, 8);
                    ourTime += System.nanoTime() - start;
                    worst[0] = Math.min(worst[0], psnr(full, ours));
                    worst[kind] = Math.min(worst[kind], psnr(boxAverage(full, 1), small));
                } else {
                    worst[kind] = Math.min(worst[kind], psnr(djpeg(file, 1), small));
                }
                compared++;
            } catch (ImageException e) {
                failures.merge((e.unsupported() ? "unsupported: " : "corrupt: ") + e.getMessage(), 1, Integer::sum);
            }
        }
        System.out.printf(
                Locale.ROOT,
                "JPEG files: %d, compared with djpeg: %d (%d progressive), worst PSNR: full size %.1f dB, 1/8 %.1f dB,"
                        + " progressive at 1/8 %.1f dB; our decoding %d ms, total %d ms%n",
                files.size(),
                compared,
                progressive,
                worst[0],
                worst[1],
                worst[2],
                ourTime / 1_000_000,
                (System.nanoTime() - started) / 1_000_000);
        failures.forEach((reason, count) -> System.out.println("  " + count + " × " + reason));
        assertTrue(worst[0] > 40 && worst[1] > 30 && worst[2] > 20, Arrays.toString(worst));
    }
}
