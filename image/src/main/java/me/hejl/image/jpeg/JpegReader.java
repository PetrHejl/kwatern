package me.hejl.image.jpeg;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import me.hejl.image.Area;
import me.hejl.image.Exif;
import me.hejl.image.ImageException;
import me.hejl.image.ImageInfo;
import me.hejl.image.ImageReader;
import me.hejl.image.RowSink;

/**
 * Decodes JPEG images (ITU-T T.81 with JFIF, EXIF and Adobe conventions), at full size or reduced to 1/2, 1/4 or
 * 1/8, optionally only an area of it.
 *
 * <p>Supports baseline and extended sequential and progressive images with Huffman coding and 8-bit samples:
 * greyscale, YCbCr, RGB, CMYK and YCCK, any chroma subsampling where the sampling factors divide each other,
 * restart markers. Not supported: arithmetic coding, 12-bit samples, lossless and hierarchical images.
 *
 * <p>Memory: baseline images whose first scan holds all components (nearly all of them) are decoded one row of
 * blocks at a time and passed on, so memory does not grow with the image; decoding stops once the requested
 * area is complete. Progressive images must be held as coefficients until the last scan: at 1/8 size only the
 * DC coefficients are kept and the other scans are skipped, otherwise all of them, which falls back to 1/8 when
 * it would exceed the memory limit.
 */
public final class JpegReader implements ImageReader {

    /** For each position in zigzag order, the position in the block in natural (row by row) order. */
    static final int[] ZIGZAG = {
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14,
        21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60,
        61, 54, 47, 55, 62, 63
    };

    /** Default for the largest image accepted, in pixels. */
    public static final long MAX_PIXELS = 1L << 30;

    /** Default for the memory used to hold coefficients of progressive images, in bytes. */
    public static final long MAX_MEMORY = 64L << 20;

    private enum Colour {
        GREY,
        YCBCR,
        RGB,
        CMYK,
        YCCK
    }

    private static final class Component {
        final int id;
        final int h;
        final int v;
        final int quantization;
        int dcTable;
        int acTable;
        int prediction;

        // Blocks with image data, used by scans of this component alone.
        int blocksPerLine;
        int blocksPerColumn;

        // Coefficients: `keep` per block (1 = DC only, or 64 in natural order), `stride` blocks per row, rows
        // starting with block row `firstRow`.
        short[] coefficients;
        int keep;
        int stride;

        // Output: each block gives sampleWidth × sampleHeight samples, from an IDCT of idctWidth × idctHeight
        // repeated to fill them.
        int sampleWidth;
        int sampleHeight;
        int idctWidth;
        int idctHeight;
        int[] plane;

        Component(int id, int h, int v, int quantization) {
            this.id = id;
            this.h = h;
            this.v = v;
            this.quantization = quantization;
        }
    }

    private enum Pass {
        SEQUENTIAL,
        DC_FIRST,
        DC_REFINE,
        AC_FIRST,
        AC_REFINE
    }

    private final Input in;
    private final BitReader reader;
    private final long maxPixels;
    private final long maxMemory;

    private final Huffman[] dcTables = new Huffman[4];
    private final Huffman[] acTables = new Huffman[4];
    private final int[][] quantization = new int[4][];
    private int restartInterval;
    private int orientation = 1;
    private int adobeTransform = -1;
    private int pendingMarker = -1;

    private JpegInfo info;
    private boolean progressive;
    private int width;
    private int height;
    private Component[] components;
    private int maxH;
    private int maxV;
    private int mcusPerLine;
    private int mcusPerColumn;
    private Colour colour;

    // The scan whose header was read last.
    private Component[] scan;
    private int spectralStart;
    private int spectralEnd;
    private int approximationHigh;
    private int approximationLow;
    private int endOfBandRun;

    // Output of decode().
    private boolean decoded;
    private final Idct idct = new Idct();
    private final float[] block = new float[64];
    private int eighths;
    private int planeWidth;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int[] row;

    public JpegReader(InputStream in) {
        this(in, MAX_PIXELS, MAX_MEMORY);
    }

    /**
     * @param maxPixels larger images are rejected as unsupported
     * @param maxMemory the most memory, in bytes, used for the coefficients of progressive images; beyond it
     *     they are decoded at 1/8 size, or rejected if even that does not fit
     */
    public JpegReader(InputStream in, long maxPixels, long maxMemory) {
        this.in = new Input(in);
        this.reader = new BitReader(this.in);
        this.maxPixels = maxPixels;
        this.maxMemory = maxMemory;
        try {
            dcTables[0] = new Huffman(Huffman.DC_LUMINANCE_COUNTS, Huffman.DC_VALUES);
            dcTables[1] = new Huffman(Huffman.DC_CHROMINANCE_COUNTS, Huffman.DC_VALUES);
            acTables[0] = new Huffman(Huffman.AC_LUMINANCE_COUNTS, Huffman.AC_LUMINANCE_VALUES);
            acTables[1] = new Huffman(Huffman.AC_CHROMINANCE_COUNTS, Huffman.AC_CHROMINANCE_VALUES);
        } catch (ImageException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads the header, up to the start of the image data, and returns what it says. */
    public JpegInfo header() throws IOException {
        if (info == null) {
            readHeader();
            info = new JpegInfo(width, height, components.length, progressive, orientation);
        }
        return info;
    }

    @Override
    public String mimeType() {
        return "image/jpeg";
    }

    @Override
    public ImageInfo info() throws IOException {
        header();
        return new ImageInfo(width, height, orientation);
    }

    @Override
    public void decode(RowSink sink, int minWidth, int minHeight, Area area) throws IOException {
        header();
        Area all = area == null ? new Area(0, 0, width, height) : area;
        int areaWidth = Math.min(all.width(), width);
        int areaHeight = Math.min(all.height(), height);
        decode(sink, eighths(areaWidth, areaHeight, minWidth, minHeight), all.x(), all.y(), all.width(), all.height());
    }

    /**
     * The smallest reduction (in eighths: 1, 2, 4 or 8) at which the image still has at least the given size, so
     * it can be scaled down further without losing detail.
     */
    public static int eighths(int width, int height, int minWidth, int minHeight) {
        for (int eighths = 1; eighths < 8; eighths *= 2) {
            if (scaled(width, eighths) >= minWidth && scaled(height, eighths) >= minHeight) {
                return eighths;
            }
        }
        return 8;
    }

    private static int scaled(int size, int eighths) {
        return (size * eighths + 7) / 8;
    }

    /** Decodes the whole image at {@code eighths}/8 of its size (1, 2, 4 or 8). */
    public void decode(RowSink sink, int eighths) throws IOException {
        header();
        decode(sink, eighths, 0, 0, width, height);
    }

    /**
     * Decodes an area of the image at {@code eighths}/8 of its size (1, 2, 4 or 8). The area is in pixels of the
     * full-size image as stored, before any EXIF orientation, and is clipped to the image. Progressive images
     * may come out at 1/8 even if more was asked, see {@link JpegReader}; the sink is told the actual size.
     */
    public void decode(RowSink sink, int eighths, int x, int y, int areaWidth, int areaHeight) throws IOException {
        if (eighths != 1 && eighths != 2 && eighths != 4 && eighths != 8) {
            throw new IllegalArgumentException("Scale must be 1, 2, 4 or 8 eighths: " + eighths);
        }
        if (decoded) {
            throw new IllegalStateException("Image already decoded");
        }
        decoded = true;
        header();
        int x0 = Math.clamp(x, 0, width - 1);
        int y0 = Math.clamp(y, 0, height - 1);
        int x1 = Math.clamp((long) x + areaWidth, x0 + 1, width);
        int y1 = Math.clamp((long) y + areaHeight, y0 + 1, height);

        boolean streaming = !progressive && scan.length == components.length;
        // Only the DC coefficients are needed at 1/8, except for subsampled colour, whose blocks cover two or
        // more pixels then. Streaming holds just one row of blocks, so it keeps them all.
        int keep = eighths == 1 && !streaming ? 1 : 64;
        if (!streaming) {
            long blocks = 0;
            for (Component component : components) {
                blocks += (long) mcusPerLine * component.h * mcusPerColumn * component.v;
            }
            if (keep == 64 && blocks * 64 * Short.BYTES > maxMemory) {
                eighths = 1;
                keep = 1;
            }
            if (blocks * keep * Short.BYTES > maxMemory) {
                throw ImageException.unsupported("Image too large to decode: " + width + "×" + height);
            }
        }
        this.eighths = eighths;
        left = x0 * eighths / 8;
        top = y0 * eighths / 8;
        right = Math.max(left + 1, scaled(x1, eighths));
        bottom = Math.max(top + 1, scaled(y1, eighths));
        planeWidth = mcusPerLine * maxH * eighths;
        for (Component component : components) {
            component.keep = keep;
            component.stride = mcusPerLine * component.h;
            int rows = streaming ? component.v : mcusPerColumn * component.v;
            component.coefficients = new short[component.stride * rows * keep];
            component.sampleWidth = eighths * maxH / component.h;
            component.sampleHeight = eighths * maxV / component.v;
            component.idctWidth = keep == 1 ? 1 : largestDivisor(component.sampleWidth);
            component.idctHeight = keep == 1 ? 1 : largestDivisor(component.sampleHeight);
            component.plane = new int[planeWidth * maxV * eighths];
        }
        row = new int[right - left];
        sink.begin(right - left, bottom - top);
        if (streaming) {
            decodeStreaming(sink);
        } else {
            decodeBuffered(sink);
        }
    }

    /** The largest IDCT size (1, 2, 4 or 8) that the samples of a block are a multiple of. */
    private static int largestDivisor(int samples) {
        for (int size = 8; size > 1; size /= 2) {
            if (samples % size == 0) {
                return size;
            }
        }
        return 1;
    }

    // Header

    private void readHeader() throws IOException {
        if (in.read() != 0xFF || in.read() != 0xD8) {
            throw ImageException.corrupt("Not a JPEG image");
        }
        while (true) {
            int marker = nextMarker();
            switch (marker) {
                case 0xC0, 0xC1, 0xC2 -> readFrame(marker == 0xC2);
                case 0xC3, 0xC5, 0xC6, 0xC7 -> throw ImageException.unsupported("Lossless or hierarchical JPEG");
                case 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> throw ImageException.unsupported("Arithmetic coding");
                case 0xDA -> {
                    if (components == null) {
                        throw ImageException.corrupt("Image data before the frame header");
                    }
                    readScanHeader();
                    return;
                }
                case 0xD9 -> throw ImageException.corrupt("No image data");
                case 0xE1 -> readExif();
                case 0xEE -> readAdobe();
                default -> readTablesOrSkip(marker);
            }
        }
    }

    /** Reads table and restart interval segments, which may also appear between scans, and skips others. */
    private void readTablesOrSkip(int marker) throws IOException {
        switch (marker) {
            case 0xC4 -> readHuffmanTables();
            case 0xDB -> readQuantizationTables();
            case 0xDD -> {
                in.readShort();
                restartInterval = in.readShort();
            }
            case 0x01, 0xD0, 0xD1, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7 -> {
                // Markers without a segment.
            }
            default -> in.skip(segmentLength());
        }
    }

    /** The next marker, skipping anything that is not one, as libjpeg does with damaged data. */
    private int nextMarker() throws IOException {
        if (pendingMarker >= 0) {
            int marker = pendingMarker;
            pendingMarker = -1;
            return marker;
        }
        while (true) {
            int value = in.readByte();
            if (value != 0xFF) {
                continue;
            }
            do {
                value = in.readByte();
            } while (value == 0xFF);
            if (value != 0) {
                return value;
            }
        }
    }

    private int segmentLength() throws IOException {
        int length = in.readShort() - 2;
        if (length < 0) {
            throw ImageException.corrupt("Invalid segment length");
        }
        return length;
    }

    private void readFrame(boolean progressive) throws IOException {
        if (components != null) {
            throw ImageException.corrupt("More than one frame");
        }
        int length = segmentLength();
        int precision = in.readByte();
        height = in.readShort();
        width = in.readShort();
        int count = in.readByte();
        if (precision != 8) {
            throw ImageException.unsupported(precision + "-bit samples");
        }
        if (width == 0 || height == 0) {
            throw ImageException.unsupported("Image height given after the data (DNL marker)");
        }
        if ((long) width * height > maxPixels) {
            throw ImageException.unsupported("Image too large: " + width + "×" + height);
        }
        if (count != 1 && count != 3 && count != 4) {
            throw ImageException.unsupported(count + " colour components");
        }
        if (length < 6 + 3 * count) {
            throw ImageException.corrupt("Frame header too short");
        }
        this.progressive = progressive;
        components = new Component[count];
        for (int i = 0; i < count; i++) {
            int id = in.readByte();
            int sampling = in.readByte();
            int table = in.readByte();
            // A single component has no subsampling, whatever the header says.
            int h = count == 1 ? 1 : sampling >> 4;
            int v = count == 1 ? 1 : sampling & 15;
            if (h < 1 || h > 4 || v < 1 || v > 4 || table > 3) {
                throw ImageException.corrupt("Invalid component " + id);
            }
            components[i] = new Component(id, h, v, table);
            maxH = Math.max(maxH, h);
            maxV = Math.max(maxV, v);
        }
        in.skip(length - 6 - 3 * count);
        for (Component component : components) {
            if (maxH % component.h != 0 || maxV % component.v != 0) {
                throw ImageException.unsupported("Unusual chroma subsampling");
            }
            int samplesPerLine = (width * component.h + maxH - 1) / maxH;
            int samplesPerColumn = (height * component.v + maxV - 1) / maxV;
            component.blocksPerLine = (samplesPerLine + 7) / 8;
            component.blocksPerColumn = (samplesPerColumn + 7) / 8;
        }
        mcusPerLine = (width + 8 * maxH - 1) / (8 * maxH);
        mcusPerColumn = (height + 8 * maxV - 1) / (8 * maxV);
        colour = colour();
    }

    private Colour colour() {
        return switch (components.length) {
            case 1 -> Colour.GREY;
            case 3 -> {
                boolean rgbIds = components[0].id == 'R' && components[1].id == 'G' && components[2].id == 'B';
                yield adobeTransform == 0 || adobeTransform < 0 && rgbIds ? Colour.RGB : Colour.YCBCR;
            }
            default -> adobeTransform == 2 ? Colour.YCCK : Colour.CMYK;
        };
    }

    private void readHuffmanTables() throws IOException {
        int remaining = segmentLength();
        while (remaining > 0) {
            int type = in.readByte();
            int[] counts = new int[16];
            int total = 0;
            for (int i = 0; i < 16; i++) {
                counts[i] = in.readByte();
                total += counts[i];
            }
            if (total > 256 || (type >> 4) > 1 || (type & 15) > 3) {
                throw ImageException.corrupt("Invalid Huffman table");
            }
            int[] values = new int[total];
            for (int i = 0; i < total; i++) {
                values[i] = in.readByte();
            }
            Huffman table = new Huffman(counts, values);
            if (type >> 4 == 0) {
                dcTables[type & 15] = table;
            } else {
                acTables[type & 15] = table;
            }
            remaining -= 17 + total;
        }
    }

    private void readQuantizationTables() throws IOException {
        int remaining = segmentLength();
        while (remaining > 0) {
            int type = in.readByte();
            int precision = type >> 4;
            if (precision > 1 || (type & 15) > 3) {
                throw ImageException.corrupt("Invalid quantization table");
            }
            int[] table = new int[64];
            for (int k = 0; k < 64; k++) {
                table[ZIGZAG[k]] = precision == 0 ? in.readByte() : in.readShort();
            }
            quantization[type & 15] = table;
            remaining -= 1 + 64 * (precision + 1);
        }
    }

    private void readScanHeader() throws IOException {
        int length = segmentLength();
        int count = in.readByte();
        if (count < 1 || count > 4 || length < 4 + 2 * count) {
            throw ImageException.corrupt("Invalid scan header");
        }
        scan = new Component[count];
        for (int i = 0; i < count; i++) {
            int id = in.readByte();
            int tables = in.readByte();
            Component component = null;
            for (Component candidate : components) {
                if (candidate.id == id) {
                    component = candidate;
                }
            }
            if (component == null) {
                throw ImageException.corrupt("Scan of an unknown component " + id);
            }
            component.dcTable = tables >> 4 & 3;
            component.acTable = tables & 3;
            scan[i] = component;
        }
        spectralStart = in.readByte();
        spectralEnd = in.readByte();
        int approximation = in.readByte();
        approximationHigh = approximation >> 4;
        approximationLow = approximation & 15;
        in.skip(length - 4 - 2 * count);
        if (!progressive) {
            spectralStart = 0;
            spectralEnd = 63;
            approximationHigh = 0;
            approximationLow = 0;
        } else if (spectralEnd > 63
                || spectralStart > spectralEnd
                || spectralStart == 0 && spectralEnd != 0
                || spectralStart > 0 && count != 1
                || approximationLow > 13) {
            throw ImageException.corrupt("Invalid progressive scan");
        }
    }

    private void readExif() throws IOException {
        byte[] data = new byte[segmentLength()];
        in.readFully(data);
        if (orientation == 1
                && data.length > 14
                && new String(data, 0, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
            orientation = Exif.orientation(data, 6);
        }
    }

    private void readAdobe() throws IOException {
        byte[] data = new byte[segmentLength()];
        in.readFully(data);
        if (data.length >= 12 && new String(data, 0, 5, StandardCharsets.ISO_8859_1).equals("Adobe")) {
            adobeTransform = data[11] & 0xFF;
            if (components != null) {
                colour = colour();
            }
        }
    }

    // Image data

    private void decodeStreaming(RowSink sink) throws IOException {
        startScan();
        int restarts = restartInterval;
        for (int mcuY = 0; mcuY < mcusPerColumn; mcuY++) {
            for (Component component : components) {
                if (component.keep > 1) {
                    Arrays.fill(component.coefficients, (short) 0);
                }
            }
            for (int mcuX = 0; mcuX < mcusPerLine; mcuX++) {
                if (restartInterval > 0) {
                    if (restarts == 0) {
                        restart();
                        restarts = restartInterval;
                    }
                    restarts--;
                }
                for (Component component : scan) {
                    for (int v = 0; v < component.v; v++) {
                        for (int h = 0; h < component.h; h++) {
                            decodeBlock(Pass.SEQUENTIAL, component, mcuX * component.h + h, v);
                        }
                    }
                }
            }
            render(mcuY, false, sink);
            if ((mcuY + 1) * maxV * eighths >= bottom) {
                return;
            }
        }
    }

    private void decodeBuffered(RowSink sink) throws IOException {
        try {
            while (true) {
                decodeScan();
                int marker = nextMarker();
                while (marker != 0xDA && marker != 0xD9) {
                    readTablesOrSkip(marker);
                    marker = nextMarker();
                }
                if (marker == 0xD9) {
                    break;
                }
                readScanHeader();
            }
        } catch (EOFException e) {
            // A truncated image: show what has been decoded so far.
        }
        for (int mcuY = 0; mcuY < mcusPerColumn && mcuY * maxV * eighths < bottom; mcuY++) {
            render(mcuY, true, sink);
        }
    }

    private void startScan() throws ImageException {
        reader.reset();
        endOfBandRun = 0;
        for (Component component : scan) {
            component.prediction = 0;
            if (quantization[component.quantization] == null) {
                throw ImageException.corrupt("Missing quantization table");
            }
            if (dcTables[component.dcTable] == null || acTables[component.acTable] == null) {
                throw ImageException.corrupt("Missing Huffman table");
            }
        }
    }

    private void restart() throws IOException {
        reader.reset();
        endOfBandRun = 0;
        for (Component component : scan) {
            component.prediction = 0;
        }
        int marker;
        try {
            marker = nextMarker();
        } catch (EOFException e) {
            reader.end();
            return;
        }
        if (marker < 0xD0 || marker > 0xD7) {
            // The data ended early; leave the marker for later and decode the rest as zeros.
            pendingMarker = marker;
            reader.end();
        }
    }

    private void decodeScan() throws IOException {
        Pass pass;
        if (!progressive) {
            pass = Pass.SEQUENTIAL;
        } else if (spectralStart == 0) {
            pass = approximationHigh == 0 ? Pass.DC_FIRST : Pass.DC_REFINE;
        } else if (scan[0].keep == 1) {
            skipScan();
            return;
        } else {
            pass = approximationHigh == 0 ? Pass.AC_FIRST : Pass.AC_REFINE;
        }
        startScan();
        int restarts = restartInterval;
        if (scan.length == 1) {
            Component component = scan[0];
            for (int blockY = 0; blockY < component.blocksPerColumn; blockY++) {
                for (int blockX = 0; blockX < component.blocksPerLine; blockX++) {
                    if (restartInterval > 0) {
                        if (restarts == 0) {
                            restart();
                            restarts = restartInterval;
                        }
                        restarts--;
                    }
                    decodeBlock(pass, component, blockX, blockY);
                }
            }
        } else {
            for (int mcuY = 0; mcuY < mcusPerColumn; mcuY++) {
                for (int mcuX = 0; mcuX < mcusPerLine; mcuX++) {
                    if (restartInterval > 0) {
                        if (restarts == 0) {
                            restart();
                            restarts = restartInterval;
                        }
                        restarts--;
                    }
                    for (Component component : scan) {
                        for (int v = 0; v < component.v; v++) {
                            for (int h = 0; h < component.h; h++) {
                                decodeBlock(pass, component, mcuX * component.h + h, mcuY * component.v + v);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Skips the data of a scan that is not needed, up to the next marker other than a restart marker. */
    private void skipScan() throws IOException {
        while (true) {
            int marker = nextMarker();
            if (marker < 0xD0 || marker > 0xD7) {
                pendingMarker = marker;
                return;
            }
        }
    }

    private void decodeBlock(Pass pass, Component component, int blockX, int blockY) throws IOException {
        short[] coefficients = component.coefficients;
        int base = (blockY * component.stride + blockX) * component.keep;
        switch (pass) {
            case SEQUENTIAL -> decodeSequential(component, coefficients, base);
            case DC_FIRST -> {
                int length = reader.decode(dcTables[component.dcTable]);
                component.prediction += reader.signed(length);
                coefficients[base] = (short) (component.prediction << approximationLow);
            }
            case DC_REFINE -> {
                if (reader.bit() != 0) {
                    coefficients[base] |= (short) (1 << approximationLow);
                }
            }
            case AC_FIRST -> decodeAcFirst(component, coefficients, base);
            case AC_REFINE -> decodeAcRefine(component, coefficients, base);
        }
    }

    private void decodeSequential(Component component, short[] coefficients, int base) throws IOException {
        int length = reader.decode(dcTables[component.dcTable]);
        component.prediction += reader.signed(length);
        coefficients[base] = (short) component.prediction;
        Huffman table = acTables[component.acTable];
        boolean all = component.keep > 1;
        int[] fast = table.fastAc;
        for (int k = 1; k < 64; k++) {
            int entry = fast[reader.peek()];
            if (entry != 0) {
                reader.skip(entry & 0xFF);
                k += entry >> 8 & 15;
                if (all && k < 64) {
                    coefficients[base + ZIGZAG[k]] = (short) (entry >> 16);
                }
                continue;
            }
            int symbol = reader.decode(table);
            int run = symbol >> 4;
            int size = symbol & 15;
            if (size == 0) {
                if (run != 15) {
                    break;
                }
                k += 15;
                continue;
            }
            k += run;
            int value = reader.signed(size);
            if (all && k < 64) {
                coefficients[base + ZIGZAG[k]] = (short) value;
            }
        }
    }

    // T.81 G.1.2.2: the first scan of a band of AC coefficients.
    private void decodeAcFirst(Component component, short[] coefficients, int base) throws IOException {
        if (endOfBandRun > 0) {
            endOfBandRun--;
            return;
        }
        Huffman table = acTables[component.acTable];
        for (int k = spectralStart; k <= spectralEnd; k++) {
            int symbol = reader.decode(table);
            int run = symbol >> 4;
            int size = symbol & 15;
            if (size == 0) {
                if (run < 15) {
                    endOfBandRun = (1 << run) - 1 + reader.bits(run);
                    break;
                }
                k += 15;
                continue;
            }
            k += run;
            if (k > 63) {
                break;
            }
            coefficients[base + ZIGZAG[k]] = (short) (reader.signed(size) * (1 << approximationLow));
        }
    }

    // T.81 G.1.2.3: a refinement scan of a band of AC coefficients. Coefficients that are already non-zero
    // get one correction bit each; runs count only the coefficients that are still zero.
    private void decodeAcRefine(Component component, short[] coefficients, int base) throws IOException {
        int plus = 1 << approximationLow;
        int minus = -1 << approximationLow;
        int k = spectralStart;
        if (endOfBandRun == 0) {
            Huffman table = acTables[component.acTable];
            while (k <= spectralEnd) {
                int symbol = reader.decode(table);
                int run = symbol >> 4;
                int size = symbol & 15;
                int value = 0;
                if (size != 0) {
                    value = reader.bit() != 0 ? plus : minus;
                } else if (run != 15) {
                    endOfBandRun = (1 << run) + reader.bits(run);
                    break;
                }
                while (k <= spectralEnd) {
                    int position = base + ZIGZAG[k];
                    if (coefficients[position] != 0) {
                        refine(coefficients, position, plus, minus);
                    } else if (--run < 0) {
                        break;
                    }
                    k++;
                }
                if (value != 0 && k <= spectralEnd) {
                    coefficients[base + ZIGZAG[k]] = (short) value;
                }
                k++;
            }
        }
        if (endOfBandRun > 0) {
            for (; k <= spectralEnd; k++) {
                int position = base + ZIGZAG[k];
                if (coefficients[position] != 0) {
                    refine(coefficients, position, plus, minus);
                }
            }
            endOfBandRun--;
        }
    }

    private void refine(short[] coefficients, int position, int plus, int minus) throws IOException {
        if (reader.bit() != 0 && (coefficients[position] & plus) == 0) {
            coefficients[position] += (short) (coefficients[position] >= 0 ? plus : minus);
        }
    }

    // Output

    /** Turns one row of MCUs into pixels and passes the lines inside the requested area to the sink. */
    private void render(int mcuY, boolean buffered, RowSink sink) {
        int lines = maxV * eighths;
        int firstLine = mcuY * lines;
        if (firstLine >= bottom || firstLine + lines <= top) {
            return;
        }
        for (Component component : components) {
            int[] table = quantization[component.quantization];
            int repeatX = component.sampleWidth / component.idctWidth;
            int repeatY = component.sampleHeight / component.idctHeight;
            for (int v = 0; v < component.v; v++) {
                int blockY = buffered ? mcuY * component.v + v : v;
                for (int blockX = 0; blockX < component.stride; blockX++) {
                    int x = blockX * component.sampleWidth;
                    if (x >= right || x + component.sampleWidth <= left) {
                        continue;
                    }
                    int base = (blockY * component.stride + blockX) * component.keep;
                    if (component.idctWidth == 1 && component.idctHeight == 1) {
                        // The block's average, as the IDCT would compute it from the DC coefficient alone.
                        int sample = Math.round(component.coefficients[base] * table[0] / 8f + 128);
                        fill(component.plane, v * component.sampleHeight * planeWidth + x, repeatX, repeatY, sample);
                        continue;
                    }
                    // Blocks with more than one sample always keep all coefficients.
                    int rowsUsed = 0;
                    for (int i = 0; i < 64; i++) {
                        short coefficient = component.coefficients[base + i];
                        block[i] = coefficient * table[i];
                        if (coefficient != 0) {
                            rowsUsed |= 1 << (i >> 3);
                        }
                    }
                    idct.transform(
                            block,
                            rowsUsed,
                            component.idctWidth,
                            component.idctHeight,
                            component.plane,
                            v * component.sampleHeight * planeWidth + x,
                            planeWidth,
                            repeatX,
                            repeatY);
                }
            }
        }
        for (int line = 0; line < lines; line++) {
            int y = firstLine + line;
            if (y < top || y >= bottom) {
                continue;
            }
            convert(line * planeWidth);
            sink.row(row);
        }
    }

    private void fill(int[] plane, int offset, int width, int height, int sample) {
        int value = sample < 0 ? 0 : Math.min(sample, 255);
        for (int y = 0; y < height; y++) {
            int start = offset + y * planeWidth;
            Arrays.fill(plane, start, start + width, value);
        }
    }

    private void convert(int offset) {
        int[] first = components[0].plane;
        for (int x = left; x < right; x++) {
            int i = offset + x;
            int a = first[i];
            row[x - left] = switch (colour) {
                case GREY -> a * 0x010101;
                case RGB -> a << 16 | components[1].plane[i] << 8 | components[2].plane[i];
                case YCBCR -> ycc(a, components[1].plane[i], components[2].plane[i]);
                case CMYK -> cmyk(a, components[1].plane[i], components[2].plane[i], components[3].plane[i]);
                case YCCK -> {
                    int rgb = ycc(a, components[1].plane[i], components[2].plane[i]);
                    yield cmyk(
                            255 - (rgb >> 16 & 0xFF),
                            255 - (rgb >> 8 & 0xFF),
                            255 - (rgb & 0xFF),
                            components[3].plane[i]);
                }
            };
        }
    }

    // JFIF: YCbCr as in ITU-R BT.601 with full range, in 16-bit fixed point.
    private static int ycc(int y, int cb, int cr) {
        cb -= 128;
        cr -= 128;
        int r = y + ((91881 * cr + 32768) >> 16);
        int g = y - ((22554 * cb + 46802 * cr + 32768) >> 16);
        int b = y + ((116130 * cb + 32768) >> 16);
        return clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    // Without a colour profile, a plain conversion. Adobe applications store CMYK inverted (0 is full ink),
    // which the Adobe marker indicates.
    private int cmyk(int c, int m, int y, int k) {
        if (adobeTransform < 0) {
            c = 255 - c;
            m = 255 - m;
            y = 255 - y;
            k = 255 - k;
        }
        return (c * k + 127) / 255 << 16 | (m * k + 127) / 255 << 8 | (y * k + 127) / 255;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
