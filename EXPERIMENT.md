# Experiment: correctness, demo, and projection throughput

Date: 2026-10-26 (Asia/Jerusalem). The experiment evaluates Exercise 1's planar sampling and video demo, and the required 500-cubed byte-volume throughput. Raw measurements are stored in [experiments](experiments/README.md).

## Environment

- CPU: AMD Ryzen 9 7940HS with Radeon 780M Graphics; 8 physical cores, 16 logical processors.
- Installed physical memory reported by Windows: 31,982,637,056 bytes (about 29.79 GiB).
- OS: Microsoft Windows 11 Pro, version 10.0.26200, x64.
- JVM: Temurin OpenJDK 22.0.2+9, OpenJDK 64-Bit Server VM.
- Benchmark heap flags: `-Xms256m -Xmx1g`; reported maximum heap 1,073,741,824 bytes.
- Implementation: single-threaded Java projection; no GPU computation.

## Reproduction

Run from the repository root with FFmpeg and FFprobe on PATH:

```powershell
javac -Xlint:all -d out src/*.java tests/*.java
java -cp out RegressionTests
py tests/integration_test.py
java -Xms256m -Xmx1g -cp out Benchmark
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
java -cp out Projection datasets/coded-u16-rotated.nrrd datasets/mixed-planes.csv datasets/mixed-demo.mp4
```

Use `python3` instead of `py` where appropriate. Java tests and the application have no third-party Java dependencies. The Python test uses only its standard library plus the installed FFmpeg/FFprobe executables.

## Correctness and robustness results

The warning-enabled Java compilation succeeds. The dependency-free Java regression suite passes **1002 checks**. These include exact analytic sections for rectangles, triangles, and oblique planes; an anisotropic rotated source; nonorthogonal coordinate inversion; fractional final-row clamping; fractional row spans; nearest-neighbor ties; out-of-volume zero fill; all scalar-width limits; and invalid input cases.

The end-to-end suite passes **24 checks**. An independent Python calculation interpolates the synthetic plane endpoints in world coordinates, applies an analytic inverse for the rotated fixture, samples its known voxel-value function, normalizes the image, and constructs the expected common canvas. The test decodes the MP4 to grayscale and compares each frame in input order.

| Mixed-demo frame | Decoded mean absolute grayscale error | Maximum absolute error |
| --- | --- | --- |
| Four-point region | 2.094 | 9 |
| Triangle | 4.521 | 21 |

The predeclared integration acceptance limits are mean absolute error at most 8 and maximum error at most 30 on the 0..255 display range. Nonzero errors are expected because H.264 is lossy. Exact raw-pixel expectations are checked separately in Java. The synthetic video also passes frame-count, frame-order, dimension, stale-file exclusion, shorter-rerun, invalid-argument, and failure-preserves-output checks.

During testing, an encoder configuration that exited successfully without producing content exposed an empty-output acceptance issue. The implementation now checks that encoded output is nonempty before replacing the destination. Missing or failing encoder configurations return a nonzero CLI error and preserve an existing output.

Before the implementation changes, the supplied three PNGs also matched a header-derived independent calculation exactly under the earlier span-rounding policy. That historical observation is not used as evidence for the revised fractional-span policy; current acceptance uses the tests above. A direct visual comparison in 3D Slicer was not performed.

## Video demonstration

| Output | Inputs | Decoded/probed frames | Canvas | Playback FPS | Duration |
| --- | --- | --- | --- | --- | --- |
| [projection.mp4](projection.mp4) | Supplied MRI and planes.csv | 3 | 100 x 106 | 30 | 0.1 seconds |
| [demo.mp4](demo.mp4) | Supplied MRI and mri-sweep.csv | 90 | 100 x 106 | 30 | 3 seconds |
| [mixed-demo.mp4](datasets/mixed-demo.mp4) | Coded rotated fixture and mixed-planes.csv | 2 | 12 x 8 | 30 | About 0.067 seconds |

Each nonempty CSV row produces exactly one frame. The longer sweep is included to make motion visible; the assignment's original three-row input inherently yields a short video. All output videos were regenerated with the completed implementation. The original root PNG files are historical images and are not used as video input.

## Performance methodology

The benchmark loads a 500 x 500 x 500 unsigned-byte NRRD containing `(x+3*y+7*z) & 255`. This is a real 125,000,000-byte scalar volume in memory. Gzip generation and NRRD loading occur before timing.

Each plane receives 150 warmup projections and five measured batches of 300 projections, for 1500 measured projections per plane. Measured work includes per-projection geometry setup, output allocation, sampling, and observing a changing output location for a checksum. It excludes dataset generation, volume loading, grayscale conversion, PNG writing, and MP4 encoding. Results include the allocation/garbage-collection effects of repeated output creation. No forced garbage collection or parallel projection is used.

The fixed planes, expressed in identity world/voxel coordinates, are:

- Rectangle: `(0,-1,250),(500,-1,250),(0,499,250),(500,499,250)`, producing 500 x 500 output.
- Triangle: `(250,0,250),(0,400,250),(500,400,250)`, producing 500 x 472 output.
- Oblique section: `(0,-1,0),(500,-1,0),(0,399,300),(500,399,300)`, producing 500 x 500 output.

Some plane boundary coordinates lie outside the volume, exercising zero-fill behavior. The sampled interior covers the volume. Per batch, `averageMilliseconds = totalMilliseconds / 300` and `FPS = 1000 / averageMilliseconds`.

## Performance results

| Plane | Mean batch FPS | Minimum batch FPS | Maximum batch FPS | Observed average ms/projection range | Checksum per batch |
| --- | --- | --- | --- | --- | --- |
| Rectangle | 540.199 | 385.962 | 640.536 | 1.561191..2.590931 | 37948 |
| Triangle | 782.159 | 768.052 | 793.200 | 1.260716..1.301994 | 11909 |
| Oblique | 386.455 | 361.359 | 394.760 | 2.533182..2.767331 | 38240 |

The mean FPS is the arithmetic mean of the five per-batch throughput values. It is not the reciprocal of the mean duration. Every measured batch exceeds the mandatory 30 FPS and expected 100 FPS. The slowest batch is 361.359 FPS. Raw batch durations and JVM details are retained in [benchmark-2026-10-26.txt](experiments/benchmark-2026-10-26.txt).

## Interpretation and limits

The implementation meets the performance thresholds on this machine for the three declared workloads. The triangle samples fewer nonzero pixels than a full rectangle, so its higher throughput is expected. The oblique section accesses different volume locations and is slower. Batch variation can reflect scheduling, JVM optimization, allocation, and garbage collection; this experiment does not isolate their individual contributions.

These results establish the requirement for the measured environment and plane sizes. They do not guarantee the same speed on all hardware, all output dimensions, or all supported scalar types. Video playback at 30 FPS is an encoding setting and is not evidence of projection throughput. The correctness suite covers declared cases but cannot exhaust every possible geometry or malformed file. Supported NRRD encodings and resource limits remain explicit in the README and SRS.
