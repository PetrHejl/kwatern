package me.hejl.image.jpeg;

/**
 * Inverse discrete cosine transform of an 8×8 block of coefficients into {@code width × height} samples, where
 * each size is 1, 2, 4 or 8. Smaller outputs are the full 8×8 result averaged over 2×2, 4×4 or 8×8 pixels,
 * computed directly from the coefficients: averaging the 8-point cosines over each group gives the factors
 * below. One sample per block is just the DC coefficient. libjpeg's reduced-size IDCTs approximate the same.
 */
final class Idct {

    /**
     * For each size n: {@code c(u)/2} times the mean of {@code cos((2x+1)uπ / 16)} over the {@code 8/n} pixels x
     * of output sample k, indexed {@code [k * 8 + u]}. With {@code n = 8} these are the plain IDCT factors.
     */
    private static final float[][] FACTORS = new float[9][];

    static {
        for (int n = 1; n <= 8; n *= 2) {
            int group = 8 / n;
            float[] factors = new float[n * 8];
            for (int k = 0; k < n; k++) {
                for (int u = 0; u < 8; u++) {
                    double sum = 0;
                    for (int j = 0; j < group; j++) {
                        sum += Math.cos((2 * (k * group + j) + 1) * u * Math.PI / 16);
                    }
                    double c = u == 0 ? Math.sqrt(0.5) : 1;
                    factors[k * 8 + u] = (float) (c / 2 * sum / group);
                }
            }
            FACTORS[n] = factors;
        }
    }

    /** The factors for size {@code n}, which with {@code n = 8} are also those of the forward transform. */
    static float[] factors(int n) {
        return FACTORS[n];
    }

    private final float[] rows = new float[64];

    /**
     * Transforms a dequantized block (natural order, row by row) and writes {@code width × height} level-shifted,
     * clamped samples to {@code target}, each repeated {@code repeatX × repeatY} times. Bit v of {@code rowsUsed}
     * tells whether row v of the block has any coefficients; the others are skipped.
     */
    void transform(
            float[] block,
            int rowsUsed,
            int width,
            int height,
            int[] target,
            int offset,
            int stride,
            int repeatX,
            int repeatY) {
        float[] horizontal = FACTORS[width];
        float[] vertical = FACTORS[height];
        // Rows first: for each frequency row v, the samples along x.
        for (int v = 0; v < 8; v++) {
            if ((rowsUsed >> v & 1) == 0) {
                continue;
            }
            int row = v * 8;
            for (int x = 0; x < width; x++) {
                int factors = x * 8;
                float sum = 0;
                for (int u = 0; u < 8; u++) {
                    sum += horizontal[factors + u] * block[row + u];
                }
                rows[row + x] = sum;
            }
        }
        for (int y = 0; y < height; y++) {
            int factors = y * 8;
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int v = 0; v < 8; v++) {
                    if ((rowsUsed >> v & 1) != 0) {
                        sum += vertical[factors + v] * rows[v * 8 + x];
                    }
                }
                int sample = Math.round(sum + 128);
                sample = sample < 0 ? 0 : Math.min(sample, 255);
                int start = offset + y * repeatY * stride + x * repeatX;
                for (int dy = 0; dy < repeatY; dy++) {
                    for (int dx = 0; dx < repeatX; dx++) {
                        target[start + dy * stride + dx] = sample;
                    }
                }
            }
        }
    }
}
