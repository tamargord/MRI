import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** End-to-end CLI/video checks using only Java 8, FFmpeg and FFprobe. */
public final class IntegrationTests {
    private static final Path ROOT = Paths.get("").toAbsolutePath();
    private static final String JAVA = Paths.get(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    private static int checks;

    private static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }

    private static final class Result {
        final int status;
        final byte[] stdout;
        final String stderr;

        Result(int status, byte[] stdout, String stderr) {
            this.status = status;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static Result run(Path work, String encoder, String... command) throws Exception {
        Path stdout = Files.createTempFile(work, "stdout-", ".bin");
        Path stderr = Files.createTempFile(work, "stderr-", ".txt");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile());
            if (encoder != null) builder.environment().put("FFMPEG", encoder);
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new IOException("Command timed out: " + Arrays.toString(command));
            }
            return new Result(process.exitValue(), Files.readAllBytes(stdout),
                    new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    private static Result application(Path work, String encoder, String... arguments)
            throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList(
                JAVA, "-cp", ROOT.resolve("out").toString(), "Projection"));
        command.addAll(Arrays.asList(arguments));
        return run(work, encoder, command.toArray(new String[0]));
    }

    private static final class Video {
        final Map<String, String> metadata;
        final byte[] pixels;

        Video(Map<String, String> metadata, byte[] pixels) {
            this.metadata = metadata;
            this.pixels = pixels;
        }
    }

    private static Video decode(Path work, Path video) throws Exception {
        Result probe = run(work, null, "ffprobe", "-v", "error", "-select_streams", "v:0",
                "-count_frames", "-show_entries", "stream=width,height,nb_read_frames,r_frame_rate",
                "-of", "default=noprint_wrappers=1", video.toString());
        check(probe.status == 0, "ffprobe success: " + probe.stderr);
        Map<String, String> metadata = new HashMap<>();
        for (String line : new String(probe.stdout, StandardCharsets.UTF_8).split("\\R")) {
            String[] pair = line.split("=", 2);
            if (pair.length == 2) metadata.put(pair[0], pair[1]);
        }
        Result decoded = run(work, null, "ffmpeg", "-v", "error", "-i", video.toString(),
                "-f", "rawvideo", "-pix_fmt", "gray", "pipe:1");
        check(decoded.status == 0, "video decodes: " + decoded.stderr);
        return new Video(metadata, decoded.stdout);
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < 3; i++) sum += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(sum);
    }

    private static double[] interpolate(double[] a, double[] b, double fraction) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) result[i] = a[i] + (b[i] - a[i]) * fraction;
        return result;
    }

    /** Independent world-space reference; the fixture's inverse transform is analytic.
     * Does not call the application's geometry, projection or grayscale methods. */
    private static int[][] projectReference(String row) {
        String[] fields = row.split(",");
        double[][] points = new double[4][3];
        for (int i = 0; i < 4; i++) {
            int source = fields.length == 9 ? Math.max(0, i - 1) : i;
            for (int j = 0; j < 3; j++) points[i][j] = Double.parseDouble(fields[source * 3 + j]);
        }
        double leftLength = distance(points[0], points[2]);
        double rightLength = distance(points[1], points[3]);
        double base = distance(points[2], points[3]);
        int[][] image = new int[(int) Math.ceil(leftLength)][(int) Math.ceil(base)];
        int maximum = 0;
        for (int y = 0; y < image.length; y++) {
            double[] left = interpolate(points[0], points[2], Math.min(y + 1, leftLength) / leftLength);
            double[] right = interpolate(points[1], points[3], Math.min(y + 1, rightLength) / rightLength);
            double span = distance(left, right);
            for (int k = 0; k <= Math.floor(span + 1e-9); k++) {
                double[] world = interpolate(left, right, span == 0 ? 0 : k / span);
                int x = (int) Math.floor((world[1] + 20) / 2 + 0.5);
                int yy = (int) Math.floor(10 - world[0] + 0.5);
                int z = (int) Math.floor((world[2] - 30) / 1.5 + 0.5);
                int col = (int) Math.floor((base - span) / 2 + k + 0.5);
                if (col >= 0 && col < image[y].length && x >= 0 && x < 8
                        && yy >= 0 && yy < 7 && z >= 0 && z < 6) {
                    image[y][col] = x + 10 * yy + 100 * z;
                    maximum = Math.max(maximum, image[y][col]);
                }
            }
        }
        for (int[] line : image) {
            for (int x = 0; x < line.length; x++) {
                line[x] = maximum == 0 ? 0 : (int) Math.floor(line[x] * 255.0 / maximum + 0.5);
            }
        }
        return image;
    }

    private static void checkFrames(Video video, List<String> rows, int width, int height) {
        for (int index = 0; index < rows.size(); index++) {
            int[][] image = projectReference(rows.get(index));
            int[] expected = new int[width * height];
            int dx = (width - image[0].length) / 2;
            int dy = (height - image.length) / 2;
            for (int y = 0; y < image.length; y++) {
                System.arraycopy(image[y], 0, expected, (y + dy) * width + dx, image[y].length);
            }
            int sum = 0;
            int maximum = 0;
            for (int i = 0; i < expected.length; i++) {
                int difference = Math.abs((video.pixels[index * expected.length + i] & 255) - expected[i]);
                sum += difference;
                maximum = Math.max(maximum, difference);
            }
            double mae = (double) sum / expected.length;
            check(mae <= 8 && maximum <= 30, "frame " + index + " content/order: MAE="
                    + mae + ", max=" + maximum);
            System.out.printf(Locale.ROOT,
                    "Frame %d: independent grayscale reference, decoded MAE=%.3f, max error=%d%n",
                    index, mae, maximum);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) deleteTree(entry);
                else Files.delete(entry);
            }
        }
        Files.delete(directory);
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("mri-integration-");
        try {
            String volume = ROOT.resolve("datasets/coded-u16-rotated.nrrd").toString();
            Path csv = ROOT.resolve("datasets/mixed-planes.csv");
            Path output = work.resolve("result with spaces.mp4");
            Path stale = work.resolve("frame_000.png");
            byte[] staleBytes = "This stale file is deliberately not a valid PNG".getBytes(StandardCharsets.UTF_8);
            Files.write(stale, staleBytes);
            Result completed = application(work, null, volume, csv.toString(), output.toString());
            check(completed.status == 0, completed.stderr);
            Video video = decode(work, output);
            check("2".equals(video.metadata.get("nb_read_frames")), "exact mixed frame count");
            check("30/1".equals(video.metadata.get("r_frame_rate")), "30 playback FPS");
            int width = Integer.parseInt(video.metadata.get("width"));
            int height = Integer.parseInt(video.metadata.get("height"));
            check(width == 12 && height == 8, "common canvas with varying plane sizes");
            check(video.pixels.length == width * height * 2, "decoded frame byte count");
            List<String> rows = new ArrayList<>();
            for (String row : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                if (!row.trim().isEmpty()) rows.add(row);
            }
            checkFrames(video, rows, width, height);
            check(Arrays.equals(Files.readAllBytes(stale), staleBytes), "stale PNG untouched and excluded");
            Path shorterCsv = work.resolve("one.csv");
            Files.write(shorterCsv, Arrays.asList(rows.get(0)), StandardCharsets.UTF_8);
            Result rerun = application(work, null, volume, shorterCsv.toString(), output.toString());
            check(rerun.status == 0, "overwrite previous video with shorter run: " + rerun.stderr);
            Video shorter = decode(work, output);
            check("1".equals(shorter.metadata.get("nb_read_frames")), "no leftover frames on rerun");
            byte[] original = Files.readAllBytes(output);
            // Java is a real executable that rejects FFmpeg's arguments, simulating encoder failure.
            for (String encoder : Arrays.asList(work.resolve("missing-ffmpeg").toString(), JAVA)) {
                Result failure = application(work, encoder, volume, csv.toString(), output.toString());
                check(failure.status != 0 && failure.stderr.contains("Error:"),
                        "missing/failed encoder is a nonzero CLI error: " + failure.stderr);
                check(Arrays.equals(Files.readAllBytes(output), original), "failed encoder preserves previous output");
            }
            for (String[] arguments : new String[][] { {}, {volume, csv.toString()},
                    {volume, csv.toString(), output.toString(), "extra"} }) {
                Result failure = application(work, null, arguments);
                check(failure.status != 0 && failure.stderr.contains("Usage:"), "argument validation");
            }
            Path badCsv = work.resolve("bad.csv");
            Files.write(badCsv, Arrays.asList("NaN,0,0,1,0,0,0,1,0"), StandardCharsets.UTF_8);
            Result failure = application(work, null, volume, badCsv.toString(), output.toString());
            check(failure.status != 0 && failure.stderr.contains("CSV line 1"), "CSV row error identifies location");
            check(Arrays.equals(Files.readAllBytes(output), original), "invalid CSV preserves output");
            try (DirectoryStream<Path> files = Files.newDirectoryStream(work, "mri-video-*")) {
                check(!files.iterator().hasNext(), "temporary video files removed");
            }
            System.out.println("PASS: " + checks + " end-to-end checks");
        } finally {
            deleteTree(work);
        }
    }
}
