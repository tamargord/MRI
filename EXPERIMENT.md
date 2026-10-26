# Tests and results

These are the results recorded on 2026-10-27 for the Java 8 version.

## Computer used

The tests ran on Windows 11 Pro with a Ryzen 9 7940HS processor and about 32 GB of RAM. The JDK was Temurin 8u504-b01. Projection uses one CPU thread.

## Running the tests

With JDK 8, FFmpeg, and FFprobe available, run:

```powershell
javac -d out src/*.java tests/*.java
java -cp out RegressionTests
py tests/integration_test.py
```

The Python script is an extra test for the video output. It needs Python 3; use `python3` instead of `py` if necessary. The application itself is Java 8.

The Java tests use small arrays with known values. They check different data types, point directions, triangles, four-point sections, rounding, samples outside the array, and invalid input. The recorded run passed 1002 assertions. This includes repeated checks for individual pixels and file formats.

The video script runs the program and checks the output's frame count, order, size, and image values. It also checks bad arguments, CSV errors, missing FFmpeg, old PNGs, and a shorter second run. It passed 24 checks. Failed runs kept the existing output video.

A video can have slightly different pixel values because H.264 compresses the images. The test allows a mean error of 8 and a maximum error of 30 out of 255. The measured errors were:

| Frame | Mean error | Largest error |
| --- | --- | --- |
| Four-point section | 2.094 | 9 |
| Triangle | 4.521 | 21 |

The Java tests compare raw image values exactly. There was no direct comparison with 3D Slicer. The [test log](experiments/java8-verification-2026-10-27.txt) has the full commands and output, including compilation on an actual Java 8 JDK.

## Example videos

| File | Frames | Size | Length at 30 FPS |
| --- | --- | --- | --- |
| [projection.mp4](projection.mp4) | 3 | 100 x 106 | 0.1 seconds |
| [demo.mp4](demo.mp4) | 90 | 100 x 106 | 3 seconds |
| [mixed-demo.mp4](datasets/mixed-demo.mp4) | 2 | 12 x 8 | About 0.067 seconds |

All three were made using Java 8. FFprobe checked their frame counts and sizes, and FFmpeg decoded them without errors.

The first video is short because its CSV has only three rows. The longer video uses 90 adjacent MRI sections. The PNG files in the project root are older examples; the program makes new temporary images each time it runs.

## Speed test

Run:

```powershell
java -Xms256m -Xmx1g -cp out Benchmark
```

The memory options give Java an initial heap of 256 MB and a maximum setting of 1 GB. On this Java 8 run, the reported usable maximum was 954,728,448 bytes.

`Benchmark` creates a 500 x 500 x 500 unsigned-byte volume if it is missing. This is 125,000,000 bytes of voxel data, with values `(x+3*y+7*z) & 255`. It loads the volume before starting the timer.

For each plane, the test runs 150 projections to warm up Java. It then times five groups of 300 projections. The timer includes setting up rows, creating the output array, sampling, and reading one pixel into a checksum so the result is used. It excludes loading, grayscale conversion, saving PNGs, and making the video. Any garbage collection during a measured group is included.

The plane coordinates are in [Benchmark.java](src/Benchmark.java). They use identity world/voxel coordinates. Some boundary points fall outside the array and return zero.

| Plane | Image size | Average group FPS | Slowest group FPS | Fastest group FPS |
| --- | --- | --- | --- | --- |
| Rectangle | 500 x 500 | 743.967 | 685.956 | 762.845 |
| Triangle | 500 x 472 | 1506.170 | 1498.288 | 1513.033 |
| Oblique section | 500 x 500 | 781.179 | 774.319 | 785.833 |

For each group, `FPS = 300 * 1000 / totalMilliseconds`. The average is the mean of the five group FPS values. The [benchmark log](experiments/java8-benchmark-2026-10-27.txt) includes every timing and checksum.

The slowest group was 685.956 FPS. All groups passed the assignment's 30 FPS minimum and 100 FPS target on this computer. These numbers measure projection speed for these inputs, not video encoding speed.

The older Java 17 and Java 22 logs are kept in [experiments](experiments/README.md). They are from earlier versions, so they are not a controlled comparison with this version.
