import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
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
            if (!required(fields, "dimension").equals("3"))
                throw new IOException("Only scalar 3D NRRD files are supported");
            for (String unsupported : Arrays.asList("data file", "datafile", "block size"))
                if (fields.containsKey(unsupported))
                    throw new IOException("Unsupported NRRD field: " + unsupported);
            for (String skip : Arrays.asList("byte skip", "byteskip", "line skip", "lineskip"))
                if (fields.containsKey(skip) && !fields.get(skip).equals("0"))
                    throw new IOException("Nonzero NRRD skips are unsupported");
            if (fields.containsKey("space dimension") && !fields.get("space dimension").equals("3"))
                throw new IOException("Only three-dimensional coordinate spaces are supported");
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
            String[] dimensions = required(fields, "sizes").split("\\s+");
            if (dimensions.length != 3) throw new IOException("Expected three NRRD sizes");
            int[] sizes = new int[3];
            for (int i = 0; i < 3; i++) {
                sizes[i] = Integer.parseInt(dimensions[i]);
            }
            for (int size : sizes)
                if (size < 1 || size >= 1024)
                    throw new IOException("NRRD dimensions must be in 1..1023");
            int count = sizes[0] * sizes[1] * sizes[2];
            String type = required(fields, "type").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
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
            Point3D[] directions = {
                new Point3D(1, 0, 0), new Point3D(0, 1, 0), new Point3D(0, 0, 1)
            };
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
                if (spacing.length != 3) throw new IOException("Expected three spacings");
                for (int i = 0; i < 3; i++)
                    directions[i] = directions[i].times(Double.parseDouble(spacing[i]));
            }
            String encoding = required(fields, "encoding").toLowerCase(Locale.ROOT);
            boolean ascii =
                    encoding.equals("ascii") || encoding.equals("text") || encoding.equals("txt");
            boolean little = true;
            if (!ascii && bits > 8) {
                String endian = required(fields, "endian").toLowerCase(Locale.ROOT);
                if (!endian.equals("little") && !endian.equals("big"))
                    throw new IOException("Invalid NRRD endian: " + endian);
                little = endian.equals("little");
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
            if (bits == 8) data = new byte[count];
            else if (bits == 16) data = new short[count];
            else data = new int[count];
            try (InputStream dataInput = payload) {
                if (ascii) {
                    Scanner tokens = new Scanner(dataInput, "US-ASCII");
                    long min = signed ? -(1L << (bits - 1)) : 0;
                    long max = signed ? (1L << (bits - 1)) - 1 : (1L << bits) - 1;
                    for (int i = 0; i < count; i++) {
                        String token = tokens.hasNext() ? tokens.next() : null;
                        if (token == null) throw new EOFException("Incomplete NRRD ASCII data");
                        long value = Long.parseLong(token);
                        if (value < min || value > max)
                            throw new IOException("NRRD value outside declared scalar range");
                        store(data, bits, i, value);
                    }
                    if (tokens.hasNext()) throw new IOException("Extra NRRD ASCII samples");
                } else {
                    // Decode in chunks: no extra volume-sized byte buffer for 16/32-bit data.
                    DataInputStream binary = new DataInputStream(dataInput);
                    byte[] chunk = new byte[65536];
                    int bytes = bits / 8;
                    for (int offset = 0; offset < count; ) {
                        int samples = Math.min(chunk.length / bytes, count - offset),
                                length = samples * bytes;
                        // readFully either fills this part of the buffer or reports missing data.
                        binary.readFully(chunk, 0, length);
                        for (int j = 0; j < samples; j++) {
                            long value = 0;
                            // Put the bytes together in the byte order from the header.
                            for (int k = 0; k < bytes; k++) {
                                int position = k;
                                if (!little) {
                                    position = bytes - 1 - k;
                                }
                                int currentByte = chunk[j * bytes + k] & 255;
                                value = value | ((long) currentByte << (8 * position));
                            }
                            store(data, bits, offset + j, value);
                        }
                        offset += samples;
                    }
                    if (binary.read() != -1) throw new IOException("Extra NRRD binary data");
                }
            }
            return new Volume(sizes[0], sizes[1], sizes[2], bits, signed, data, origin, directions);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid NRRD: " + e.getMessage(), e);
        }
    }

    private static void store(Object data, int bits, int i, long value) {
        if (bits == 8) {
            ((byte[]) data)[i] = (byte) value;
        } else if (bits == 16) {
            ((short[]) data)[i] = (short) value;
        } else {
            ((int[]) data)[i] = (int) value;
        }
    }

    private static String required(Map<String, String> fields, String name) throws IOException {
        String value = fields.get(name);
        if (value == null || value.trim().isEmpty())
            throw new IOException("Missing NRRD field: " + name);
        return value;
    }

    private static Map<String, String> readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        List<String> lines = new ArrayList<>();
        boolean ended = false;
        for (int n = 0; n < 1_048_576; n++) {
            int ch = input.read();
            if (ch == -1) break;
            if (ch == '\n') {
                String s = new String(line.toByteArray(), StandardCharsets.US_ASCII);
                if (s.endsWith("\r")) {
                    s = s.substring(0, s.length() - 1);
                }
                line.reset();
                if (s.isEmpty()) {
                    ended = true;
                    break;
                }
                lines.add(s);
            } else line.write(ch);
        }
        if (!ended) throw new IOException("Missing NRRD header terminator or header exceeds 1 MiB");
        if (lines.isEmpty() || !lines.get(0).matches("NRRD000[1-5]"))
            throw new IOException("Invalid NRRD magic/version");
        Map<String, String> fields = new HashMap<>();
        for (String s : lines.subList(1, lines.size())) {
            if (s.startsWith("#") || s.contains(":=")) continue;
            int colon = s.indexOf(':');
            if (colon < 1) throw new IOException("Invalid NRRD header line");
            String name = s.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (fields.containsKey(name)) {
                throw new IOException("Duplicate NRRD field: " + name);
            }
            fields.put(name, s.substring(colon + 1).trim());
        }
        return fields;
    }

    private static Point3D parseVector(String s) throws IOException {
        if (!s.startsWith("(") || !s.endsWith(")")) throw new IOException("Invalid spatial vector");
        String[] v = s.substring(1, s.length() - 1).split(",", -1);
        if (v.length != 3) throw new IOException("Expected three vector components");
        return new Point3D(
                Double.parseDouble(v[0].trim()),
                Double.parseDouble(v[1].trim()),
                Double.parseDouble(v[2].trim()));
    }
}
