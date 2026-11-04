import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Dependency-free regression suite. Run with java -cp out RegressionTests. */
public final class RegressionTests {
    private static int checks;
    private static final Point3D[] ID = {p(1, 0, 0), p(0, 1, 0), p(0, 0, 1)};

    private static Point3D p(double x, double y, double z) {
        return new Point3D(x, y, z);
    }

    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }

    private static void badPlane(Point3D... points) {
        try {
            new Plane(points);
        } catch (IllegalArgumentException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid plane");
    }

    private static void badPoint(double x) {
        try {
            new Point3D(x, 0, 0);
        } catch (IllegalArgumentException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted nonfinite point");
    }

    private static void badMRI(Path file) {
        try {
            MRIReader.loadMRI(file);
        } catch (IOException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid MRI");
    }

    private static void badCSV(Path file) {
        try {
            CSVReader.readPlanes(file.toString());
        } catch (IOException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid CSV");
    }

    private static void badArguments(String[] arguments) throws Exception {
        try {
            Projection.run(arguments);
        } catch (IllegalArgumentException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid arguments");
    }

    private static void badTransform() {
        try {
            new Volume(
                    1,
                    1,
                    1,
                    8,
                    false,
                    new byte[1],
                    p(0, 0, 0),
                    new Point3D[] {p(1, 0, 0), p(2, 0, 0), p(0, 0, 1)});
        } catch (IllegalArgumentException e) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted singular transform");
    }

    private static void writeText(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void deleteFolder(java.io.File directory) throws IOException {
        java.io.File[] files = directory.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) deleteFolder(file);
                else Files.deleteIfExists(file.toPath());
            }
        }
        Files.deleteIfExists(directory.toPath());
    }

    private static Volume coded() {
        int[] data = new int[12 * 12 * 12];
        for (int z = 0; z < 12; z++)
            for (int y = 0; y < 12; y++)
                for (int x = 0; x < 12; x++) data[x + 12 * (y + 12 * z)] = x + 10 * y + 100 * z;
        return new Volume(12, 12, 12, 32, false, data, p(0, 0, 0), ID);
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("mri-regression-");
        try {
            DatasetGenerator.writeSmallDatasets(temp);
            Volume rotated = MRIReader.loadMRI(temp.resolve("coded-u16-rotated.nrrd"));
            check(rotated.sx == 8 && rotated.sy == 7 && rotated.sz == 6, "header sizes");
            for (int z = 0; z < 6; z++)
                for (int y = 0; y < 7; y++)
                    for (int x = 0; x < 8; x++) {
                        Point3D world = rotated.voxelToWorld(p(x, y, z));
                        check(
                                Math.abs(world.x - (10 - y)) < 1e-9
                                        && Math.abs(world.y - (-20 + 2 * x)) < 1e-9
                                        && Math.abs(world.z - (30 + 1.5 * z)) < 1e-9,
                                "header affine");
                        check(
                                rotated.sample(rotated.worldToVoxel(world)) == x + 10 * y + 100 * z,
                                "rotated value and roundtrip");
                    }
            long[][] rotatedImage =
                    Projection.project(
                            rotated, p(10, -20, 33), p(10, -8, 33), p(4, -20, 33), p(4, -8, 33));
            for (int y = 0; y < 6; y++)
                for (int x = 0; x < 12; x++)
                    check(
                            rotatedImage[y][x] == 200 + 10 * (y + 1) + Math.round(x / 2.0),
                            "anisotropic rotated projection");
            Point3D[] shear = {p(1, 0.25, 0), p(0, 2, 0.5), p(0.5, 0, 3)};
            Volume sheared =
                    new Volume(
                            2,
                            2,
                            2,
                            8,
                            false,
                            new byte[] {0, 1, 2, 3, 4, 5, 6, 7},
                            p(-12, 50, 8),
                            shear);
            for (int z = 0; z < 2; z++)
                for (int y = 0; y < 2; y++)
                    for (int x = 0; x < 2; x++) {
                        Point3D w =
                                p(-12 + x + 0.5 * z, 50 + 0.25 * x + 2 * y, 8 + 0.5 * y + 3 * z);
                        Point3D v = sheared.worldToVoxel(w);
                        check(
                                Math.abs(v.x - x) < 1e-9
                                        && Math.abs(v.y - y) < 1e-9
                                        && Math.abs(v.z - z) < 1e-9,
                                "nonorthogonal inverse");
                        check(
                                sheared.sample(v) == x + 2 * y + 4 * z,
                                "nonorthogonal source values");
                    }
            Volume u32 = MRIReader.loadMRI(temp.resolve("unsigned-u32-big.nrrd"));
            check(
                    u32.sample(0, 0, 1) == 4294967295L && u32.sample(1, 1, 0) == 2147483648L,
                    "u32 high bit and big endian");
            Volume signed = MRIReader.loadMRI(temp.resolve("signed-i16.nrrd"));
            check(
                    signed.sample(0, 0, 0) == -32768 && signed.sample(1, 0, 0) == -1,
                    "signed ASCII preserved");
            // Integer limits across encodings and byte orders, including unsigned high bits.
            for (int bits : new int[] {8, 16, 32})
                for (boolean isSigned : new boolean[] {false, true}) {
                    long min = isSigned ? -(1L << (bits - 1)) : 0;
                    long max = isSigned ? (1L << (bits - 1)) - 1 : (1L << bits) - 1;
                    long[] values = {min, max, 0, 1};
                    for (String encoding : Arrays.asList("raw", "gzip", "ascii"))
                        for (String endian : Arrays.asList("little", "big")) {
                            Path fixture = temp.resolve("limits.nrrd");
                            DatasetGenerator.writeVolume(
                                    fixture,
                                    (isSigned ? "int" : "uint") + bits,
                                    "2 2 1",
                                    encoding,
                                    endian,
                                    "",
                                    "\n",
                                    values,
                                    bits);
                            Volume limits = MRIReader.loadMRI(fixture);
                            for (int i = 0; i < 4; i++)
                                check(
                                        limits.sample(i % 2, i / 2, 0) == values[i],
                                        "scalar boundary " + bits + " " + encoding + " " + endian);
                        }
                }
            Volume zero = MRIReader.loadMRI(temp.resolve("zero-u8.nrrd"));
            check(zero.sample(2, 2, 2) == 0, "raw byte");
            check(
                    coded().sample(-0.5, 0, 0) == 0 && coded().sample(1.5, 0, 0) == 2,
                    "nearest neighbor ties");
            check(
                    coded().sample(-1, 0, 0) == 0
                            && coded().sample(12, 0, 0) == 0
                            && coded().sample(0, 0, 99) == 0,
                    "outside volume");
            // Use a nonzero first voxel: NaN rounds to zero in Java and used to read it.
            Volume nonzero = new Volume(1, 1, 1, 8, false, new byte[] {99}, p(0, 0, 0), ID);
            for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY}) {
                check(nonzero.sample(invalid, 0, 0) == 0, "nonfinite sample x");
                check(nonzero.sample(0, invalid, 0) == 0, "nonfinite sample y");
                check(nonzero.sample(0, 0, invalid) == 0, "nonfinite sample z");
            }
            // Analytic rectangle: row y samples source y+1; column x samples source x.
            long[][] rect =
                    Projection.project(coded(), p(0, 0, 2), p(6, 0, 2), p(0, 4, 2), p(6, 4, 2));
            check(rect.length == 4 && rect[0].length == 6, "rectangle dimensions");
            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 6; x++)
                    check(rect[y][x] == x + 10 * (y + 1) + 200, "analytic rectangle pixel");
            // Right-triangle cross-section through (x,z), equal five-unit sides.
            long[][] oblique =
                    Projection.project(coded(), p(0, 0, 0), p(6, 0, 0), p(0, 3, 4), p(6, 3, 4));
            for (int y = 0; y < 5; y++)
                for (int x = 0; x < 6; x++)
                    check(
                            oblique[y][x]
                                    == x
                                            + 10 * Math.round((y + 1) * 0.6)
                                            + 100 * Math.round((y + 1) * 0.8),
                            "analytic oblique pixel");
            // 3-4-5 triangle: first row endpoints (2.4,1.8,2), (3.6,1.8,2).
            long[][] triangle = Projection.project(coded(), p(3, 1, 2), p(0, 5, 2), p(6, 5, 2));
            check(triangle.length == 5 && triangle[0].length == 6, "triangle dimensions");
            check(
                    triangle[0][2] == 222 && triangle[0][3] == 223 && triangle[0][4] == 0,
                    "triangle first row and floor span");
            check(triangle[4][0] == 250 && triangle[4][5] == 255, "triangle final row");
            long[][] collapsed =
                    Projection.project(coded(), p(3, 1, 2), p(3, 1, 2), p(0, 5, 2), p(6, 5, 2));
            check(Arrays.deepEquals(triangle, collapsed), "zero-width top equivalent to triangle");
            // Length 1.4: final row must stop at y=1.4 rather than advance to y=2.
            long[][] fractional =
                    Projection.project(coded(), p(0, 0, 0), p(2, 0, 0), p(0, 1.4, 0), p(2, 1.4, 0));
            check(fractional.length == 2 && fractional[1][0] == 10, "fractional side clamped");
            // Narrow span 1.6: k=2 is outside; round(span) previously sampled it.
            long[][] narrow = Projection.project(coded(), p(2, 0, 0), p(0, 1.5, 0), p(4, 1.5, 0));
            check(
                    narrow[0][1] == 11 && narrow[0][2] == 12 && narrow[0][3] == 0,
                    "fractional span policy");
            long[][] outside =
                    Projection.project(
                            coded(), p(30, 30, 30), p(32, 30, 30), p(30, 32, 30), p(32, 32, 30));
            long sum = 0;
            for (int y = 0; y < outside.length; y++) {
                for (int x = 0; x < outside[y].length; x++) sum += outside[y][x];
            }
            check(sum == 0, "external plane zero");
            check(
                    Projection.grayscale(new long[][] {{0, 0}}, 2, 2).getRaster().getSample(0, 0, 0)
                            == 0,
                    "black normalization");
            java.awt.image.BufferedImage gray =
                    Projection.grayscale(new long[][] {{-1, 0, 2147483648L, 4294967295L}}, 4, 2);
            check(
                    gray.getRaster().getSample(0, 0, 0) == 0
                            && gray.getRaster().getSample(2, 0, 0) == 128
                            && gray.getRaster().getSample(3, 0, 0) == 255,
                    "signed clamp and full u32 display");
            badPoint(Double.NaN);
            badPoint(Double.POSITIVE_INFINITY);
            badPlane(p(0, 0, 0), p(0, 0, 0), p(1, 0, 0));
            badPlane(p(0, 0, 0), p(-1, 0, 0), p(1, 0, 0));
            badPlane(p(0, 0, 0), p(1, 0, 0), p(0, 2, 0));
            badPlane(p(0, 0, 0), p(2, 0, 0), p(0, 2, 0), p(2, 0, 2));
            badPlane(p(0, 0, 0), p(6, 0, 0), p(1, 3, 0), p(5, 3, 0));
            badPlane(p(0, 0, 0), p(2, 0, 0), p(2, 2, 0), p(0, 2, 0));
            badTransform();
            List<Plane> mixed = CSVReader.readPlanes(temp.resolve("mixed-planes.csv").toString());
            check(
                    mixed.size() == 2 && mixed.get(0).height != mixed.get(1).height,
                    "mixed CSV order");
            Path csv = temp.resolve("bad.csv");
            String validRow = "0,0,0,2,0,0,0,2,0";
            writeText(csv, "\uFEFF" + validRow + "\n");
            check(CSVReader.readPlanes(csv.toString()).size() == 1, "UTF-8 BOM before valid row");
            writeText(csv, "\uFEFF\n\n" + validRow + "\n1,2,3\n");
            List<CSVReader.Row> bomRows = CSVReader.readRows(csv.toString());
            check(bomRows.size() == 2 && bomRows.get(0).plane != null
                    && bomRows.get(0).lineNumber == 3 && bomRows.get(1).error != null
                    && bomRows.get(1).lineNumber == 4, "BOM and blank lines preserve CSV locations");
            writeText(csv, "\uFEFF");
            badCSV(csv);
            for (String line :
                    Arrays.asList(
                            "",
                            "1,2,3",
                            "0,0,0,1,0,0,0,1,0,",
                            "NaN,0,0,1,0,0,0,1,0",
                            "a,0,0,1,0,0,0,1,0")) {
                writeText(csv, line);
                badCSV(csv);
            }
            Path bad = temp.resolve("bad.nrrd");
            String base =
                    "NRRD0005\n"
                        + "type: uint16\n"
                        + "dimension: 3\n"
                        + "sizes: 1 1 1\n"
                        + "encoding: raw\n"
                        + "endian: little\n\n";
            Files.write(bad, base.getBytes());
            badMRI(bad);
            for (String header :
                    Arrays.asList(
                            base.replace("NRRD0005", "INVALID"),
                            base.replace("1 1 1", "0 1 1"),
                            base.replace("uint16", "float"),
                            base.replace("raw", "bzip2"),
                            base.replace("endian: little\n", ""),
                            base.replace("\n\n", "\ndata file: missing.raw\n\n"),
                            base.replace("\n\n", "\nbyte skip: 2\n\n"),
                            base.replace(
                                    "\n\n", "\nspace directions: (1,0,0) (2,0,0) (0,0,1)\n\n"))) {
                Files.write(bad, concat(header.getBytes(), new byte[2]));
                badMRI(bad);
            }
            writeText(bad, "NRRD0005\ntype: uint8");
            badMRI(bad);
            DatasetGenerator.writeVolume(
                    bad, "uint8", "1 1 1", "ascii", "little", "", "\n", new long[] {256}, 8);
            badMRI(bad);
            badArguments(new String[0]);
            badArguments(new String[] {"a", "b", "out.png"});
            System.out.println(
                    "PASS: "
                            + checks
                            + " checks (loading, transforms, analytic projections, malformed"
                            + " inputs, display)");
        } finally {
            deleteFolder(temp.toFile());
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
