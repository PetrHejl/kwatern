package me.hejl.image.jpeg;

import static me.hejl.image.TestImages.psnr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import me.hejl.image.Image;
import org.junit.jupiter.api.Test;

class JpegWriterTest {

    @Test
    void writesImagesTheReaderDecodes() throws IOException {
        Image source = JpegReaderTest.source();
        double previous = 0;
        for (int quality : new int[] {30, 75, 90, 100}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JpegWriter.write(source, quality, out);
            Image decoded = JpegReaderTest.decode(out.toByteArray(), 8);
            double psnr = psnr(source, decoded);
            assertTrue(psnr > 20 && psnr > previous, "quality " + quality + ": " + psnr + " dB");
            previous = psnr;
        }
    }

    @Test
    void writesSmallAndOddSizes() throws IOException {
        for (int[] size : new int[][] {{1, 1}, {17, 3}, {3, 33}}) {
            int[] rgb = new int[size[0] * size[1]];
            for (int i = 0; i < rgb.length; i++) {
                rgb[i] = i * 0x0F1F2F;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JpegWriter.write(new Image(size[0], size[1], rgb), 90, out);
            Image decoded = JpegReaderTest.decode(out.toByteArray(), 8);
            assertEquals(size[0], decoded.width());
            assertEquals(size[1], decoded.height());
        }
    }

    @Test
    void standardTablesHaveEveryAcSymbol() {
        for (int[] values : new int[][] {Huffman.AC_LUMINANCE_VALUES, Huffman.AC_CHROMINANCE_VALUES}) {
            boolean[] present = new boolean[256];
            for (int value : values) {
                present[value] = true;
            }
            assertTrue(present[0x00] && present[0xF0]);
            for (int run = 0; run < 16; run++) {
                for (int size = 1; size <= 10; size++) {
                    assertTrue(present[run << 4 | size], "run " + run + " size " + size);
                }
            }
            assertEquals(162, values.length);
        }
    }
}
