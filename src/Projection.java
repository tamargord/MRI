import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/** Creates a 2D section of the MRI and joins sections into a video. */
public class Projection {
    public static long[][] project(Volume volume, Point3D... points) {
        Plane plane = new Plane(points);
        return project(volume, plane);
    }

    /** Returns raw voxel values as image[row][column], before grayscale scaling. */
    public static long[][] project(Volume volume, Plane plane) {
        long[][] image = new long[plane.height][plane.width];
        Point3D leftDirection = plane.b.minus(plane.a1).times(1.0 / plane.leftSideLength);
        Point3D rightDirection = plane.c.minus(plane.a2).times(1.0 / plane.rightSideLength);

        for (int y = 0; y < plane.height; y++) {
            // Move one unit down each side. The last row stops at the endpoints.
            double leftDistance = Math.min(y + 1.0, plane.leftSideLength);
            double rightDistance = Math.min(y + 1.0, plane.rightSideLength);
            Point3D left = plane.a1.plus(leftDirection.times(leftDistance));
            Point3D right = plane.a2.plus(rightDirection.times(rightDistance));
            Point3D across = right.minus(left);
            double span = across.length();
            Point3D startVoxel = volume.worldToVoxel(left);

            if (span < 0.000000001) {
                image[y][plane.width / 2] = volume.sample(startVoxel);
                continue;
            }

            // Convert the row direction once instead of converting every pixel.
            Point3D step = volume.worldVectorToVoxel(across.times(1.0 / span));
            int lastSample = (int) Math.floor(span + 0.000000001);
            double leftMargin = (plane.bottomLength - span) / 2.0;
            for (int k = 0; k <= lastSample; k++) {
                int x = (int) Math.round(leftMargin + k);
                if (x >= 0 && x < plane.width) {
                    double voxelX = startVoxel.x + k * step.x;
                    double voxelY = startVoxel.y + k * step.y;
                    double voxelZ = startVoxel.z + k * step.z;
                    image[y][x] = volume.sample(voxelX, voxelY, voxelZ);
                }
            }
        }
        return image;
    }

    public static BufferedImage grayscale(long[][] values, int width, int height) {
        if (values.length == 0
                || values[0].length == 0
                || width < values[0].length
                || height < values.length) {
            throw new IllegalArgumentException("Canvas must contain the projection");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        long maximum = 0;
        for (int y = 0; y < values.length; y++) {
            for (int x = 0; x < values[y].length; x++) {
                maximum = Math.max(maximum, values[y][x]);
            }
        }

        int xOffset = (width - values[0].length) / 2;
        int yOffset = (height - values.length) / 2;
        for (int y = 0; y < values.length; y++) {
            for (int x = 0; x < values[y].length; x++) {
                int gray = 0;
                if (maximum > 0) {
                    long positiveValue = Math.max(0, values[y][x]);
                    gray = (int) Math.round(positiveValue * 255.0 / maximum);
                }
                image.getRaster().setSample(x + xOffset, y + yOffset, 0, gray);
            }
        }
        return image;
    }

    public static void saveImage(long[][] values, String filename) throws IOException {
        BufferedImage image = grayscale(values, values[0].length, values.length);
        ImageIO.write(image, "png", Paths.get(filename).toFile());
    }

    private static BufferedImage errorFrame(int width, int height, int lineNumber) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            graphics.drawString("ERROR - CSV line " + lineNumber, 16, 42);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            graphics.drawString("Invalid point set", 16, 70);
            graphics.drawString("See console for details", 16, 96);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void createVideo(Path directory, int frames, Path output)
            throws IOException, InterruptedException {
        String ffmpeg = System.getenv("FFMPEG");
        if (ffmpeg == null || ffmpeg.trim().isEmpty()) {
            ffmpeg = "ffmpeg";
        }
        Path log = directory.resolve("ffmpeg.log");
        String[] command = {
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-framerate",
            "30",
            "-start_number",
            "0",
            "-i",
            directory.resolve("frame_%06d.png").toString(),
            "-frames:v",
            Integer.toString(frames),
            "-c:v",
            "libx264",
            "-crf",
            "18",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            output.toString()
        };
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("Cannot start FFmpeg; install it on PATH or set FFMPEG", e);
        }
        int status;
        try {
            status = process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            process.waitFor();
            throw e;
        }
        if (status != 0) {
            String message = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            throw new IOException("FFmpeg failed (exit " + status + "): " + message);
        }
        if (Files.size(output) == 0) {
            throw new IOException("FFmpeg produced an empty video; check the FFMPEG setting");
        }
    }

    private static void deleteTemporaryFiles(Path directory) throws IOException {
        // Only this run's temporary files are removed.
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(directory);
    }

    public static void run(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: java -cp out Projection input.nrrd planes.csv output.mp4");
        }
        Path output = Paths.get(args[2]).toAbsolutePath().normalize();
        Path mri = Paths.get(args[0]).toAbsolutePath().normalize();
        Path csv = Paths.get(args[1]).toAbsolutePath().normalize();
        if (!output.toString().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            throw new IllegalArgumentException("Output must have an .mp4 extension");
        }
        if (output.equals(mri) || output.equals(csv)) {
            throw new IllegalArgumentException("Output must not overwrite an input");
        }
        if (!Files.isDirectory(output.getParent())) {
            throw new IOException("Output directory does not exist: " + output.getParent());
        }

        List<CSVReader.Row> rows = CSVReader.readRows(args[1]);
        Volume volume = MRIReader.loadMRI(args[0]);
        int width = 0;
        int height = 0;
        int errors = 0;
        for (CSVReader.Row row : rows) {
            if (row.error != null) {
                errors++;
                System.err.println("Warning: " + row.error.getMessage());
            } else {
                width = Math.max(width, row.plane.width);
                height = Math.max(height, row.plane.height);
            }
        }
        if (errors > 0) {
            // Keep the row number legible, including when every row is invalid.
            width = Math.max(width, 320);
            height = Math.max(height, 180);
        }
        // H.264 needs even image dimensions.
        width = width + width % 2;
        height = height + height % 2;
        if ((long) width * height > 16000000) {
            throw new IOException("Video canvas exceeds 16 million pixels");
        }

        Path directory = Files.createTempDirectory("mri-projection-");
        Path temporaryVideo = null;
        try {
            long projectionTime = 0;
            for (int i = 0; i < rows.size(); i++) {
                CSVReader.Row row = rows.get(i);
                BufferedImage image;
                if (row.error != null) {
                    image = errorFrame(width, height, row.lineNumber);
                } else {
                    long start = System.nanoTime();
                    long[][] values = project(volume, row.plane);
                    projectionTime += System.nanoTime() - start;
                    image = grayscale(values, width, height);
                }
                String filename = String.format(Locale.ROOT, "frame_%06d.png", i);
                Path frame = directory.resolve(filename);
                if (!ImageIO.write(image, "png", frame.toFile())) {
                    throw new IOException("PNG writer unavailable");
                }
            }
            // Keep the old output if encoding fails.
            temporaryVideo = Files.createTempFile(output.getParent(), "mri-video-", ".mp4");
            createVideo(directory, rows.size(), temporaryVideo);
            Files.move(temporaryVideo, output, StandardCopyOption.REPLACE_EXISTING);
            System.out.printf(
                    Locale.ROOT,
                    "Created %s: %d frames, %dx%d, 30 playback FPS%n",
                    output,
                    rows.size(),
                    width,
                    height);
            System.out.println("Error frames: " + errors);
            System.out.printf(
                    Locale.ROOT,
                    "Projection-only total: %.3f ms (use Benchmark for warmed timing)%n",
                    projectionTime / 1000000.0);
        } finally {
            if (temporaryVideo != null) {
                Files.deleteIfExists(temporaryVideo);
            }
            deleteTemporaryFiles(directory);
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
