# Test and performance results

Recorded on 2026-10-26 (Asia/Jerusalem). The raw output is in [experiments](experiments/README.md).

## Test computer

- AMD Ryzen 9 7940HS, 8 cores and 16 logical processors.
- 31,982,637,056 bytes of RAM, about 29.79 GiB.
- Windows 11 Pro, version 10.0.26200, x64.
- Temurin OpenJDK 22.0.2+9, 64-bit Server VM.
- Benchmark heap: `-Xms256m -Xmx1g`.

Projection runs on one CPU thread.

## Commands

Run these from the project folder with FFmpeg and FFprobe on PATH:

```powershell
javac -Xlint:all -d out src/*.java tests/*.java
java -cp out RegressionTests
py tests/integration_test.py
java -Xms256m -Xmx1g -cp out Benchmark
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
java -cp out Projection datasets/coded-u16-rotated.nrrd datasets/mixed-planes.csv datasets/mixed-demo.mp4
```

The Python test uses only the standard library. Use `python3` instead of `py` if needed.

## Correctness tests

Compilation with `-Xlint:all` finished without warnings. The Java suite passed 1002 assertions. Many are pixel checks or the same check repeated for different file formats, so this is not a count of separate test cases.

The tests compare sampled values with known values in generated volumes. They cover rectangles, triangles, oblique planes, rotated and nonorthogonal coordinates, fractional lengths, nearest-neighbor rounding, points outside the volume, signed/unsigned limits, and invalid input.

The Python suite passed 24 video/CLI checks. It calculates expected images from the synthetic volume's known value formula and an independent coordinate conversion, then compares them with decoded video frames in order. It also checks different frame sizes, a shorter second run, stale PNG files, missing arguments, bad CSV rows, and encoder failures. Failed runs preserved the existing output video.

H.264 changes pixel values slightly. For these video comparisons, the test allows a mean absolute error of 8 and a maximum error of 30 on the 0..255 grayscale range:

| Frame | Mean absolute error | Maximum error |
| --- | --- | --- |
| Four-point section | 2.094 | 9 |
| Triangle | 4.521 | 21 |

Raw image values are checked exactly in the Java tests. No direct visual comparison in 3D Slicer was performed.

## Output videos

| Video | Frames | Size | Duration at 30 FPS |
| --- | --- | --- | --- |
| [projection.mp4](projection.mp4) | 3 | 100 x 106 | 0.1 seconds |
| [demo.mp4](demo.mp4) | 90 | 100 x 106 | 3 seconds |
| [mixed-demo.mp4](datasets/mixed-demo.mp4) | 2 | 12 x 8 | About 0.067 seconds |

FFprobe confirmed the frame counts and sizes. FFmpeg decoded all three without errors. The original example is short because `planes.csv` has only three rows. The longer demo uses 90 adjacent MRI sections.

The root PNG files are older examples. The current program creates its own temporary frames for each run.

## Benchmark setup

The benchmark uses a generated `500 x 500 x 500` unsigned-byte NRRD. Its voxel values are `(x+3*y+7*z) & 255`, and the loaded volume contains 125,000,000 bytes. Dataset generation and loading happen before timing.

For each plane, the program runs 150 warmup projections, then five batches of 300 measured projections. The timer includes geometry setup inside the projection method, output allocation, sampling, and reading one changing output pixel for a checksum. It excludes loading, grayscale conversion, PNG saving, and MP4 encoding.

The planes use identity world/voxel coordinates:

- Rectangle: `(0,-1,250),(500,-1,250),(0,499,250),(500,499,250)`.
- Triangle: `(250,0,250),(0,400,250),(500,400,250)`.
- Oblique section: `(0,-1,0),(500,-1,0),(0,399,300),(500,399,300)`.

Some boundary points are outside the volume, where sampling returns zero. Each projection allocates a new output image, so allocation and any garbage collection during the batch are included in the timing.

## Benchmark results

| Plane | Output size | Mean batch FPS | Minimum FPS | Maximum FPS | Checksum per batch |
| --- | --- | --- | --- | --- | --- |
| Rectangle | 500 x 500 | 540.199 | 385.962 | 640.536 | 37948 |
| Triangle | 500 x 472 | 782.159 | 768.052 | 793.200 | 11909 |
| Oblique | 500 x 500 | 386.455 | 361.359 | 394.760 | 38240 |

For each batch, `FPS = 300 * 1000 / totalMilliseconds`. The mean in the table is the arithmetic mean of the five batch FPS values. Batch times are in [the original log](experiments/benchmark-2026-10-26.txt).

Every measured batch passed the assignment's 30 FPS minimum and 100 FPS target on this computer. The slowest batch was 361.359 FPS. The triangle samples a smaller region than the rectangle. The reason for the variation between batches was not measured.

These numbers apply to the listed computer, volume, and planes. They do not measure video encoding speed. The tests cover the cases described above, and the supported file formats and memory limits are listed in [SRS.md](SRS.md).
