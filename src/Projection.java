import java.io.BufferedReader;
import java.io.FileReader;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
public class Projection {
    public static void saveImage(int[][] E, String filename) throws Exception {

        int height = E.length;
        int width = E[0].length;

        BufferedImage image = new BufferedImage(
                width, height, BufferedImage.TYPE_BYTE_GRAY
        );

        // Find the maximum intensity for brightness adjustment
        int max = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (E[y][x] > max) {
                    max = E[y][x];
                }
            }
        }

        // Convert voxel intensities to grayscale values (0-255)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int gray = 0;

                if (max > 0) {
                    gray = (int) Math.round(E[y][x] * 255.0 / max);
                }

                gray = Math.max(0, Math.min(255, gray));

                image.getRaster().setSample(x, y, 0, gray);
            }
        }

        ImageIO.write(image, "png", new File(filename));
    }

    public static double distance(Point3D a, Point3D b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Point3D vector(Point3D from, Point3D to) {
        return new Point3D(
                to.x - from.x,
                to.y - from.y,
                to.z - from.z
        );
    }

    public static Point3D normalize(Point3D v) {
        double length = Math.sqrt(
                v.x * v.x + v.y * v.y + v.z * v.z
        );

        if (length == 0) {
            throw new IllegalArgumentException("Zero vector");
        }

        return new Point3D(
                v.x / length,
                v.y / length,
                v.z / length
        );
    }

    public static Point3D add(Point3D a, Point3D b) {
        return new Point3D(
                a.x + b.x,
                a.y + b.y,
                a.z + b.z
        );
    }

    public static int sampleMRI(short[][][] M, Point3D p) {

        int x = (int) Math.round(p.x);
        int y = (int) Math.round(p.y);
        int z = (int) Math.round(p.z);

        if (x < 0 || x >= M.length ||
                y < 0 || y >= M[0].length ||
                z < 0 || z >= M[0][0].length) {
            return 0;
        }

        return M[x][y][z] & 0xFFFF;
    }

    public static Point3D worldToVoxel(Point3D p) {

        double x = p.y + 133.92860412597656;

        double y = 116.78569793701172 - p.z;

        double z = (86.64489746093749 - p.x) / 1.2999954223632812;

        return new Point3D(x, y, z);
    }

    public static int[][] project(
            short[][][] M,
            Point3D a1, Point3D a2,
            Point3D b, Point3D c) {

        double D1 = distance(a1, b);
        double D2 = distance(a2, c);
        double Dbc = distance(b, c);

        if (Math.abs(D1 - D2) >= 0.001) {
            throw new IllegalArgumentException(
                    "Trapezoid sides must have equal length");
        }

        if (distance(a1, a2) > Dbc) {
            throw new IllegalArgumentException(
                    "Top edge cannot exceed bottom edge");
        }

        // Check that all four points are coplanar
        Point3D u = vector(a1, a2);
        Point3D v = vector(a1, b);
        Point3D w = vector(a1, c);

        double cx = u.y * v.z - u.z * v.y;
        double cy = u.z * v.x - u.x * v.z;
        double cz = u.x * v.y - u.y * v.x;

        double crossLength = Math.sqrt(
                cx * cx + cy * cy + cz * cz);

        if (crossLength < 0.0000001) {
            throw new IllegalArgumentException(
                    "Invalid trapezoid plane");
        }

        double planeDistance =
                Math.abs(cx * w.x + cy * w.y + cz * w.z)
                        / crossLength;

        if (planeDistance >= 0.001) {
            throw new IllegalArgumentException(
                    "Points must be on the same plane");
        }

        int height = (int) Math.ceil(D1);
        int width = (int) Math.ceil(Dbc);

        if (height == 0 || width == 0) {
            throw new IllegalArgumentException(
                    "Invalid projection dimensions");
        }

        int[][] E = new int[height][width];

        Point3D step1 = normalize(vector(a1, b));
        Point3D step2 = normalize(vector(a2, c));


        for (int y = 0; y < height; y++) {

            double t = Math.min(y + 1.0, D1);

            Point3D left = new Point3D(
                    a1.x + step1.x * t,
                    a1.y + step1.y * t,
                    a1.z + step1.z * t
            );

            Point3D right = new Point3D(
                    a2.x + step2.x * t,
                    a2.y + step2.y * t,
                    a2.z + step2.z * t
            );

            double d12 = distance(left, right);


            if (d12 < 0.0000001) {
                int x = width / 2;
                E[y][x] = sampleMRI(
                        M, worldToVoxel(left));
                continue;
            }

            Point3D dp = normalize(vector(left, right));
            Point3D p = left;

            int rowLength = (int) Math.round(d12);

            for (int x1 = 0; x1 <= rowLength; x1++) {

                int x = (int) Math.round(
                        (Dbc / 2.0) - (d12 / 2.0) + x1
                );

                if (x >= 0 && x < width) {
                    E[y][x] = sampleMRI(
                            M, worldToVoxel(p));
                }

                p = add(p, dp);
            }
        }

        return E;
    }

    public static Point3D voxelToWorld(Point3D p) {

        double x = 86.64489746093749 - 1.2999954223632812 * p.z;

        double y = -133.92860412597656 + p.x;

        double z = 116.78569793701172 - p.y;

        return new Point3D(x, y, z);
    }

    public static void createVideo(String outputFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "-y",
                "-framerate", "30",
                "-start_number", "0",
                "-i", "frame_%03d.png",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2",
                outputFile
        );

        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Video creation failed");
        }

        System.out.println("Video created successfully!");
    }

    public static int[][] project(short[][][] M, Point3D a, Point3D b, Point3D c) {
        double Dbc = distance(b, c);
        double Dab = distance(a, b);
        double Dac = distance(a, c);
        if (Math.abs(Dab - Dac) >= 0.001) {
            throw new IllegalArgumentException(
                    "Triangle must be isosceles"
            );
        }
        int height = (int) Math.ceil(Dab);
        int width = (int) Math.ceil(Dbc);

        int[][] E = new int[height][width];

        Point3D v_ab = normalize(vector(a, b));
        Point3D v_ac = normalize(vector(a, c));

        Point3D a1 = a;
        Point3D a2 = a;

        for (int y = 0; y < height; y++) {
            a1 = add(a1, v_ab);
            a2 = add(a2, v_ac);
            double d12 = distance(a1, a2);
            if (d12 < 0.0000001) {
                int x = width / 2;
                Point3D voxelPoint = worldToVoxel(a1);
                int v = sampleMRI(M, voxelPoint);
                E[y][x] = v;
                continue;
            }
            Point3D dp = normalize(vector(a1, a2));
            Point3D p = a1;
            int rowLength = (int) Math.round(d12);
            for (int x1 = 0; x1 <= rowLength; x1++) {

                int x = (int) Math.round(
                        (Dbc / 2.0) - (d12 / 2.0) + x1
                );

                if (x >= 0 && x < width) {

                    Point3D voxelPoint = worldToVoxel(p);
                    int v = sampleMRI(M, voxelPoint);

                    E[y][x] = v;
                }

                p = add(p, dp);

            }


        }
        return E;
    }

    public static Point3D readPoint(String[] values, int start) {
        double x = Double.parseDouble(values[start].trim());
        double y = Double.parseDouble(values[start + 1].trim());
        double z = Double.parseDouble(values[start + 2].trim());

        return new Point3D(x, y, z);
    }


    public static void benchmarkTriangle(
            short[][][] M, Point3D a, Point3D b, Point3D c) {

        int warmup = 100;
        int runs = 500;

        // Step 1: Warm up Java
        for (int i = 0; i < warmup; i++) {
            project(M, a, b, c);
        }

        // Step 2: Start measuring
        long start = System.nanoTime();

        long checksum = 0;

        // Step 3: Run the projection 500 times
        for (int i = 0; i < runs; i++) {
            int[][] E = project(M, a, b, c);

            // Use the result so Java cannot discard the work
            checksum += E[E.length / 2][E[0].length / 2];
        }

        // Step 4: Stop measuring
        long end = System.nanoTime();

        // Step 5: Calculate the results
        double totalMs = (end - start) / 1_000_000.0;

        double averageMs = totalMs / runs;

        double fps = 1000.0 / averageMs;

        System.out.println("Total time: " + totalMs + " ms");
        System.out.println("Average time: " + averageMs + " ms");
        System.out.println("Average FPS: " + fps);
        System.out.println("Checksum: " + checksum);
    }

    public static void benchmarkTrapezoid(
            short[][][] M,
            Point3D a1, Point3D a2,
            Point3D b, Point3D c) {

        int warmup = 100;
        int runs = 500;

        // Step 1: Warm up Java
        for (int i = 0; i < warmup; i++) {
            project(M, a1, a2, b, c);
        }

        // Step 2: Start measuring time
        long start = System.nanoTime();

        long checksum = 0;

        // Step 3: Run 500 projections
        for (int i = 0; i < runs; i++) {

            int[][] E = project(M, a1, a2, b, c);

            // Use the result
            checksum += E[E.length / 2][E[0].length / 2];
        }

        // Step 4: Stop measuring
        long end = System.nanoTime();

        // Step 5: Calculate performance
        double totalMs = (end - start) / 1_000_000.0;

        double averageMs = totalMs / runs;

        double fps = 1000.0 / averageMs;

        System.out.println("Trapezoid total time: " + totalMs + " ms");
        System.out.println("Trapezoid average time: " + averageMs + " ms");
        System.out.println("Trapezoid average FPS: " + fps);
        System.out.println("Checksum: " + checksum);
    }

    public static void main(String[] args) throws Exception {

        // Get input filenames
        String mriFile = args[0];
        String csvFile = args[1];
        String videoFile = args[2];

        // Load MRI
        short[][][] M = MRIReader.loadMRI(mriFile);

        // Open CSV
        BufferedReader reader =
                new BufferedReader(new FileReader(csvFile));

        String line;
        int frame = 0;

        // Read every line in CSV
        while ((line = reader.readLine()) != null) {

            if (line.trim().isEmpty()) {
                continue;
            }

            String[] values = line.split(",");


            int[][] E;
            long start;

// Triangle
            if (values.length == 9) {

                Point3D a = readPoint(values, 0);
                Point3D b = readPoint(values, 3);
                Point3D c = readPoint(values, 6);

                // Run the benchmark separately
                if (frame < 2) {
                    System.out.println("Benchmarking plane " + frame);
                    benchmarkTriangle(M, a, b, c);
                }

                // Start timer AFTER benchmark
                start = System.nanoTime();

                E = project(M, a, b, c);

            } else if (values.length == 12) {

                // Trapezoid
                Point3D a1 = readPoint(values, 0);
                Point3D a2 = readPoint(values, 3);
                Point3D b = readPoint(values, 6);
                Point3D c = readPoint(values, 9);
                if (frame < 2) {
                    System.out.println("Benchmarking trapezoid " + frame);

                    benchmarkTrapezoid(M, a1, a2, b, c);
                }
                // Start timer before projection
                start = System.nanoTime();

                E = project(M, a1, a2, b, c);

            } else {
                reader.close();
                throw new IllegalArgumentException(
                        "CSV must contain 9 or 12 values per row"
                );
            }

// Stop timer AFTER projection
            long end = System.nanoTime();

            double milliseconds = (end - start) / 1_000_000.0;

            System.out.println(
                    "Single projection time: " + milliseconds + " ms"
            );

            if (milliseconds > 0) {
                System.out.println(
                        "Single projection FPS: " + 1000.0 / milliseconds
                );
            }
            // Save the image
            String filename =
                    String.format("frame_%03d.png", frame);

            saveImage(E, filename);

            System.out.println("Saved: " + filename);

            frame++;
        }

        reader.close();

        if (frame == 0) {
            throw new IllegalArgumentException("CSV is empty");
        }

        // Create MP4
        createVideo(videoFile);
    }
}