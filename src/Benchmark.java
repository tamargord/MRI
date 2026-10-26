import java.nio.file.*;
import java.util.*;

/** Projection-only benchmark: loading and dataset generation are outside the timer. */
public final class Benchmark {
    // Keep a checksum so the benchmark uses the calculated image values.
    private static long sink;

    private Benchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 1)
            throw new IllegalArgumentException("Usage: Benchmark [500-cubed-u8.nrrd]");
        Path path = Paths.get(args.length == 0 ? "output/benchmark500.nrrd" : args[0]);
        if (args.length == 0 && !Files.exists(path)) DatasetGenerator.writeBenchmark(path);
        Volume volume = MRIReader.loadMRI(path);
        if (volume.sx != 500
                || volume.sy != 500
                || volume.sz != 500
                || volume.bits != 8
                || volume.signed)
            throw new IllegalArgumentException(
                    "Benchmark requires a 500 x 500 x 500 unsigned-byte volume");
        Plane[] planes = {
            new Plane(
                    new Point3D(0, -1, 250),
                    new Point3D(500, -1, 250),
                    new Point3D(0, 499, 250),
                    new Point3D(500, 499, 250)),
            new Plane(
                    new Point3D(250, 0, 250), new Point3D(0, 400, 250), new Point3D(500, 400, 250)),
            new Plane(
                    new Point3D(0, -1, 0),
                    new Point3D(500, -1, 0),
                    new Point3D(0, 399, 300),
                    new Point3D(500, 399, 300))
        };
        String[] names = {"rectangle", "triangle", "oblique"};
        int warmup = 150, iterations = 300, batches = 5;
        System.out.println(
                "java="
                        + System.getProperty("java.runtime.version")
                        + "; vm="
                        + System.getProperty("java.vm.name"));
        System.out.println(
                "os="
                        + System.getProperty("os.name")
                        + " "
                        + System.getProperty("os.version")
                        + " "
                        + System.getProperty("os.arch"));
        System.out.println(
                "logical_processors="
                        + Runtime.getRuntime().availableProcessors()
                        + "; max_heap_bytes="
                        + Runtime.getRuntime().maxMemory());
        System.out.println(
                "volume=500x500x500 uint8; value=(x+3*y+7*z)&255; warmup="
                        + warmup
                        + "; iterations_per_batch="
                        + iterations
                        + "; batches="
                        + batches);
        System.out.println(
                "Timed work includes geometry setup, output allocation, sampling, and one observed"
                    + " pixel per projection; excludes loading, rendering and encoding.");
        System.out.println("plane,width,height,batch,total_ms,average_ms,fps,checksum");
        for (int p = 0; p < planes.length; p++) {
            Plane plane = planes[p];
            for (int i = 0; i < warmup; i++) observe(Projection.project(volume, plane), i);
            double[] fps = new double[batches];
            for (int batch = 0; batch < batches; batch++) {
                long checksum = 0, start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    long[][] image = Projection.project(volume, plane);
                    // Vary the observed row/column to keep returned samples observable.
                    checksum += image[i % image.length][(i * 17) % image[0].length];
                }
                long duration = System.nanoTime() - start;
                sink = checksum;
                double ms = duration / 1e6;
                fps[batch] = iterations * 1000.0 / ms;
                System.out.printf(
                        Locale.ROOT,
                        "%s,%d,%d,%d,%.6f,%.6f,%.3f,%d%n",
                        names[p],
                        plane.width,
                        plane.height,
                        batch + 1,
                        ms,
                        ms / iterations,
                        fps[batch],
                        checksum);
            }
            double min = fps[0];
            double max = fps[0];
            double total = 0;
            for (int i = 0; i < fps.length; i++) {
                min = Math.min(min, fps[i]);
                max = Math.max(max, fps[i]);
                total += fps[i];
            }
            double mean = total / fps.length;
            System.out.printf(
                    Locale.ROOT,
                    "SUMMARY %s mean_fps=%.3f min_fps=%.3f max_fps=%.3f mandatory_30=%s"
                        + " expected_100=%s%n",
                    names[p],
                    mean,
                    min,
                    max,
                    min >= 30 ? "PASS" : "FAIL",
                    min >= 100 ? "PASS" : "FAIL");
        }
        System.out.println("observable_sink=" + sink);
    }

    private static void observe(long[][] image, int i) {
        sink += image[i % image.length][(i * 17) % image[0].length];
    }
}
