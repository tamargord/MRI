import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

/** Reader for attached, scalar 3D integer NRRDs (raw, gzip, or ASCII). */
public final class MRIReader {
    private MRIReader() {}

    public static Volume loadMRI(String filename) throws IOException {
        return loadMRI(Paths.get(filename));
    }

    public static Volume loadMRI(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            Map<String, String> fields = readHeader(input);
            validateHeader(fields);
            int[] sizes = readSizes(fields);
            int count = sizes[0] * sizes[1] * sizes[2];
            String type =
                    requireField(fields, "type").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            int bits;
            boolean signed;
            switch (type) {
                case "uchar":
                case "unsigned char":
                case "uint8":
                case "uint8_t":
                    bits = 8;
                    signed = false;
                    break;
                case "signed char":
                case "int8":
                case "int8_t":
                    bits = 8;
                    signed = true;
                    break;
                case "ushort":
                case "unsigned short":
                case "unsigned short int":
                case "uint16":
                case "uint16_t":
                    bits = 16;
                    signed = false;
                    break;
                case "short":
                case "short int":
                case "signed short":
                case "signed short int":
                case "int16":
                case "int16_t":
                    bits = 16;
                    signed = true;
                    break;
                case "uint":
                case "unsigned int":
                case "uint32":
                case "uint32_t":
                    bits = 32;
                    signed = false;
                    break;
                case "int":
                case "signed int":
                case "int32":
                case "int32_t":
                    bits = 32;
                    signed = true;
                    break;
                default:
                    throw new IOException("Unsupported NRRD scalar type: " + type);
            }
            Point3D origin =
                    fields.containsKey("space origin")
                            ? parseVector(fields.get("space origin"))
                            : new Point3D(0, 0, 0);
            Point3D[] directions = readDirections(fields);
            String encoding = requireField(fields, "encoding").toLowerCase(Locale.ROOT);
            boolean ascii =
                    encoding.equals("ascii") || encoding.equals("text") || encoding.equals("txt");
            boolean littleEndian = true;
            if (!ascii && bits > 8) {
                String endian = requireField(fields, "endian").toLowerCase(Locale.ROOT);
                if (!endian.equals("little") && !endian.equals("big")) {
                    throw new IOException("Invalid NRRD endian: " + endian);
                }
                littleEndian = endian.equals("little");
            }
            InputStream payload;
            if (encoding.equals("gzip") || encoding.equals("gz")) {
                payload = new GZIPInputStream(input);
            } else if (encoding.equals("raw") || ascii) {
                payload = input;
            } else {
                throw new IOException("Unsupported NRRD encoding: " + encoding);
            }
            Object data;
            if (bits == 8) {
                data = new byte[count];
            } else if (bits == 16) {
                data = new short[count];
            } else {
                data = new int[count];
            }
            try (InputStream dataInput = payload) {
                if (ascii) {
                    readAsciiValues(dataInput, data, bits, signed, count);
                } else {
                    readBinaryValues(dataInput, data, bits, littleEndian, count);
                }
            }
            return new Volume(sizes[0], sizes[1], sizes[2], bits, signed, data, origin, directions);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid NRRD: " + e.getMessage(), e);
        }
    }

    private static void validateHeader(Map<String, String> fields) throws IOException {
        if (!requireField(fields, "dimension").equals("3")) {
            throw new IOException("Only scalar 3D NRRD files are supported");
        }
        for (String unsupported : Arrays.asList("data file", "datafile", "block size")) {
            if (fields.containsKey(unsupported)) {
                throw new IOException("Unsupported NRRD field: " + unsupported);
            }
        }
        for (String skip : Arrays.asList("byte skip", "byteskip", "line skip", "lineskip")) {
            if (fields.containsKey(skip) && !fields.get(skip).equals("0")) {
                throw new IOException("Nonzero NRRD skips are unsupported");
            }
        }
        if (fields.containsKey("space dimension") && !fields.get("space dimension").equals("3")) {
            throw new IOException("Only three-dimensional coordinate spaces are supported");
        }
        if (fields.containsKey("kinds")) {
            String[] kinds = fields.get("kinds").split("\\s+");
            if (kinds.length != 3) {
                throw new IOException("Expected three axes");
            }
            for (String kind : kinds) {
                if (!kind.equals("domain") && !kind.equals("space")) {
                    throw new IOException("Only three spatial/domain axes are supported");
                }
            }
        }
    }

    private static int[] readSizes(Map<String, String> fields) throws IOException {
        String[] dimensions = requireField(fields, "sizes").split("\\s+");
        if (dimensions.length != 3) {
            throw new IOException("Expected three NRRD sizes");
        }
        int[] sizes = new int[3];
        for (int i = 0; i < 3; i++) {
            sizes[i] = Integer.parseInt(dimensions[i]);
        }
        for (int size : sizes) {
            if (size < 1 || size >= 1024) {
                throw new IOException("NRRD dimensions must be in 1..1023");
            }
        }
        return sizes;
    }

    private static Point3D[] readDirections(Map<String, String> fields) throws IOException {
        Point3D[] directions = {new Point3D(1, 0, 0), new Point3D(0, 1, 0), new Point3D(0, 0, 1)};
        if (fields.containsKey("space directions")) {
            String text = fields.get("space directions").trim();
            if (!text.startsWith("(") || !text.endsWith(")")) {
                throw new IOException("Expected three spatial direction vectors");
            }
            text = text.substring(1, text.length() - 1);
            String[] vectors = text.split("\\)\\s*\\(", -1);
            if (vectors.length != 3) {
                throw new IOException("Expected three spatial direction vectors");
            }
            for (int i = 0; i < 3; i++) {
                directions[i] = parseVector("(" + vectors[i] + ")");
            }
        } else if (fields.containsKey("spacings")) {
            String[] spacing = fields.get("spacings").split("\\s+");
            if (spacing.length != 3) {
                throw new IOException("Expected three spacings");
            }
            for (int i = 0; i < 3; i++) {
                directions[i] = directions[i].times(Double.parseDouble(spacing[i]));
            }
        }
        return directions;
    }

    private static void readAsciiValues(
            InputStream input, Object data, int bits, boolean signed, int count)
            throws IOException {
        Scanner tokens = new Scanner(input, "US-ASCII");
        long min = signed ? -(1L << (bits - 1)) : 0;
        long max = signed ? (1L << (bits - 1)) - 1 : (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            String token = tokens.hasNext() ? tokens.next() : null;
            if (token == null) {
                throw new EOFException("Incomplete NRRD ASCII data");
            }
            long value = Long.parseLong(token);
            if (value < min || value > max) {
                throw new IOException("NRRD value outside declared scalar range");
            }
            storeValue(data, bits, i, value);
        }
        if (tokens.hasNext()) {
            throw new IOException("Extra NRRD ASCII samples");
        }
    }

    private static void readBinaryValues(
            InputStream input, Object data, int bits, boolean littleEndian, int count)
            throws IOException {
        // Decode in chunks: no extra volume-sized byte buffer for 16/32-bit data.
        DataInputStream binary = new DataInputStream(input);
        byte[] chunk = new byte[65536];
        int bytesPerValue = bits / 8;
        for (int offset = 0; offset < count; ) {
            int samples = Math.min(chunk.length / bytesPerValue, count - offset);
            int chunkLength = samples * bytesPerValue;
            // readFully either fills this part of the buffer or reports missing data.
            binary.readFully(chunk, 0, chunkLength);
            for (int j = 0; j < samples; j++) {
                long value = 0;
                // Put the bytes together in the byte order from the header.
                for (int k = 0; k < bytesPerValue; k++) {
                    int position = k;
                    if (!littleEndian) {
                        position = bytesPerValue - 1 - k;
                    }
                    int currentByte = chunk[j * bytesPerValue + k] & 255;
                    value = value | ((long) currentByte << (8 * position));
                }
                storeValue(data, bits, offset + j, value);
            }
            offset += samples;
        }
        if (binary.read() != -1) {
            throw new IOException("Extra NRRD binary data");
        }
    }

    private static void storeValue(Object data, int bits, int index, long value) {
        if (bits == 8) {
            ((byte[]) data)[index] = (byte) value;
        } else if (bits == 16) {
            ((short[]) data)[index] = (short) value;
        } else {
            ((int[]) data)[index] = (int) value;
        }
    }

    private static String requireField(Map<String, String> fields, String name) throws IOException {
        String value = fields.get(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Missing NRRD field: " + name);
        }
        return value;
    }

    private static Map<String, String> readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        List<String> lines = new ArrayList<>();
        boolean ended = false;
        for (int n = 0; n < 1_048_576; n++) {
            int ch = input.read();
            if (ch == -1) {
                break;
            }
            if (ch == '\n') {
                String text = new String(line.toByteArray(), StandardCharsets.US_ASCII);
                if (text.endsWith("\r")) {
                    text = text.substring(0, text.length() - 1);
                }
                line.reset();
                if (text.isEmpty()) {
                    ended = true;
                    break;
                }
                lines.add(text);
            } else {
                line.write(ch);
            }
        }
        if (!ended) {
            throw new IOException("Missing NRRD header terminator or header exceeds 1 MiB");
        }
        if (lines.isEmpty() || !lines.get(0).matches("NRRD000[1-5]")) {
            throw new IOException("Invalid NRRD magic/version");
        }
        Map<String, String> fields = new HashMap<>();
        for (String text : lines.subList(1, lines.size())) {
            if (text.startsWith("#") || text.contains(":=")) {
                continue;
            }
            int colon = text.indexOf(':');
            if (colon < 1) {
                throw new IOException("Invalid NRRD header line");
            }
            String name = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (fields.containsKey(name)) {
                throw new IOException("Duplicate NRRD field: " + name);
            }
            fields.put(name, text.substring(colon + 1).trim());
        }
        return fields;
    }

    private static Point3D parseVector(String text) throws IOException {
        if (!text.startsWith("(") || !text.endsWith(")")) {
            throw new IOException("Invalid spatial vector");
        }
        String[] components = text.substring(1, text.length() - 1).split(",", -1);
        if (components.length != 3) {
            throw new IOException("Expected three vector components");
        }
        return new Point3D(
                Double.parseDouble(components[0].trim()),
                Double.parseDouble(components[1].trim()),
                Double.parseDouble(components[2].trim()));
    }
}
