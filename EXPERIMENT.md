# Test and performance results

These results are for the Java 8 version, tested on 2026-10-27 (Asia/Jerusalem). Logs are in [experiments](experiments/README.md).

## Test computer

- AMD Ryzen 9 7940HS, 8 cores and 16 logical processors.
- About 29.79 GiB of RAM.
- Windows 11 Pro, version 10.0.26200, x64.
- Temurin JDK 8u504-b01, 64-bit Server VM.
- Benchmark heap settings: `-Xms256m -Xmx1g`.

The program uses one CPU thread for projection. Java 8 reported a maximum heap of 954,728,448 bytes with those settings.

## Commands

Use a JDK 8 terminal, with FFmpeg and FFprobe on PATH:

```powershell
javac -source 1.8 -target 1.8 -Xlint:all -d out src/*.java tests/*.java
java -cp out RegressionTests
py tests/integration_test.py
java -Xms256m -Xmx1g -cp out Benchmark
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
java -cp out Projection datasets/coded-u16-rotated.nrrd datasets/mixed-planes.csv datasets/mixed-demo.mp4
```

The Python script is an extra video test. It needs Python 3 and only uses the standard library. Use `python3` instead of `py` if needed. The application, data generator, benchmark, and Java tests all compile with Java 8.

## Test results

The actual Java 8 compiler finished without warnings. The application and Java test classes have bytecode version 52, which is the Java 8 version.

The Java tests passed 1002 assertions. Many assertions check individual pixels or repeat the same check for different data types. They cover known image values, triangles, four-point sections, oblique planes, rotated coordinates, fractional lengths, rounding, points outside the MRI, and invalid files.

The Python test passed 24 video/CLI checks while using Java 8 for its Java processes. It checks frame count, order, dimensions, and content. It also checks invalid arguments, CSV errors, missing or misconfigured FFmpeg, old PNG files, and a shorter second run. A failed run preserved the existing output video.

The video test calculates expected images from known voxel values and compares them with decoded frames. H.264 changes pixel values slightly, so the allowed mean error is 8 and maximum error is 30 on the 0..255 grayscale scale:

| Frame | Mean absolute error | Maximum error |
| --- | --- | --- |
| Four-point section | 2.094 | 9 |
| Triangle | 4.521 | 21 |

Raw image values are checked exactly in the Java tests. A direct comparison in 3D Slicer was not performed. The [Java 8 verification log](experiments/java8-verification-2026-10-27.txt) records the commands, JDK version, and results.

## Output videos

All three videos were regenerated using Java 8. FFprobe confirmed their frame counts and dimensions, and FFmpeg decoded them without errors.

| Video | Frames | Size | Duration at 30 FPS |
| --- | --- | --- | --- |
| [projection.mp4](projection.mp4) | 3 | 100 x 106 | 0.1 seconds |
| [demo.mp4](demo.mp4) | 90 | 100 x 106 | 3 seconds |
| [mixed-demo.mp4](datasets/mixed-demo.mp4) | 2 | 12 x 8 | About 0.067 seconds |

The original example is short because `planes.csv` has three rows. The longer demo uses 90 adjacent sections. The root PNG files are older examples; the current program creates its own temporary frames.

## Speed test

The benchmark loads a generated `500 x 500 x 500` unsigned-byte NRRD, containing 125,000,000 bytes. Voxel values are `(x+3*y+7*z) & 255`.

For each plane, it runs 150 warmup projections and then five batches of 300 measured projections. The timer includes geometry setup inside the projection method, output allocation, sampling, and reading one output pixel for a checksum. Loading, grayscale conversion, saving PNGs, and making the video are outside the timer.

The planes use identity world/voxel coordinates:

- Rectangle: `(0,-1,250),(500,-1,250),(0,499,250),(500,499,250)`.
- Triangle: `(250,0,250),(0,400,250),(500,400,250)`.
- Oblique section: `(0,-1,0),(500,-1,0),(0,399,300),(500,399,300)`.

Some boundary points are outside the volume and return zero. Each projection creates a new image, so allocation and any garbage collection during the batch are included.

| Plane | Output size | Mean batch FPS | Minimum FPS | Maximum FPS | Checksum per batch |
| --- | --- | --- | --- | --- | --- |
| Rectangle | 500 x 500 | 743.967 | 685.956 | 762.845 | 37948 |
| Triangle | 500 x 472 | 1506.170 | 1498.288 | 1513.033 | 11909 |
| Oblique | 500 x 500 | 781.179 | 774.319 | 785.833 | 38240 |

For each batch, `FPS = 300 * 1000 / totalMilliseconds`. The mean is the arithmetic mean of the five batch FPS values. The complete output is in [the Java 8 benchmark log](experiments/java8-benchmark-2026-10-27.txt).

Every batch passed the assignment's 30 FPS minimum and 100 FPS target on this computer. The slowest batch was 685.956 FPS. These are projection speeds for the listed inputs and machine, not video encoding speeds.

## Earlier results

The logs from the earlier Java 22 and Java 17 versions are kept in [experiments](experiments/README.md). They describe earlier versions. The results above are from the current Java 8 code. The old and new measurements were not a controlled JVM comparison, so they do not establish why the speed changed.
