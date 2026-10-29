# Tests and results

The original results below were recorded on 2026-10-27 for the Java 8 version. The dated assignment review at the end records the follow-up checks on 2026-10-29.

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

After the code readability cleanup on 2026-10-29, compilation was repeated with the same Java 8 `-source 1.8 -target 1.8 -Xlint:all` command and completed without warnings. The regression suite again passed 1002 checks, and the integration suite passed 24 checks. Decoded frame errors remained MAE 2.094 / maximum 9 and MAE 4.521 / maximum 21. This check covers the renamed fields and methods, extracted MRI-reading helpers, and simplified dataset byte-writing loop. The benchmark tables below describe the earlier recorded runs.

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

The older Java 22 logs are kept in [experiments](experiments/README.md). They are from an earlier version, so they are not a controlled comparison with this version.

## Assignment review 2026-10-29

The three-page `I2SE_Exe1.docx.pdf` was read against the current implementation. No missing required deliverable was found in this review. This is a check against the written assignment, not a guarantee about any additional classroom instructions or grading criteria.

| Assignment item | Evidence in this project | Review result |
| --- | --- | --- |
| 1. Repository, problem and scope in README | Git repository, README input/output description and links to design and limits | Present |
| 2. SRS | SRS.md covers inputs, outputs, geometry, robustness, performance and limits | Present |
| 3. Detailed algorithm | ALGORITHM.md describes transforms, row traversal, sampling, sizing, rounding and video creation | Present; implementation follows the stated policy |
| 4. NRRD data set | Supplied MRI plus four small fixtures and a reproducible 500-cubed volume generator; datasets/README.md describes them | Present |
| 5. Testing strategy including demo | Analytic Java checks plus independent Python video-content/order checks, malformed input and failed-encoder cases | Both suites passed again |
| 6. Implementation and frequent commits | Separate reader, volume, point, plane, CSV, projection and benchmark classes; existing Git history | Present; Java 8 compilation passed |
| 7. Three-filename demo with ordered frames | Projection.main takes NRRD, CSV and MP4 filenames; integration checks frame count, content, order and reruns | Passed; fresh real-MRI demo also generated |
| 8. Experiment, results and MP4 output | This document, dated benchmark logs, projection.mp4, demo.mp4 and datasets/mixed-demo.mp4 | Present; existing MRI videos decoded without errors |

The assignment allows Java, Python, C or C++; its Python invocation is an example of the three-filename interface. This project's corresponding invocation is `java -cp out Projection input.nrrd planes.csv output.mp4`.

Fresh checks used the existing Temurin 8u504-b01 JDK in `output/java8/jdk8u504-b01`, on the same Windows computer as the original experiment:

```powershell
& ./output/java8/jdk8u504-b01/bin/javac.exe -source 1.8 -target 1.8 -Xlint:all -d out src/*.java tests/*.java
& ./output/java8/jdk8u504-b01/bin/java.exe -cp out RegressionTests
$env:PATH = "$PWD/output/java8/jdk8u504-b01/bin;$env:PATH"
py tests/integration_test.py
& ./output/java8/jdk8u504-b01/bin/java.exe -Xms256m -Xmx1g -cp out Benchmark
```

Compilation completed without warnings. Java tests passed 1002 checks; integration tests passed 24 checks. Decoded frame errors were again MAE 2.094 / maximum 9 and MAE 4.521 / maximum 21, within the test tolerances.

| Plane | Mean projection FPS | Slowest batch FPS | 30 FPS minimum / 100 FPS expected |
| --- | --- | --- | --- |
| Rectangle | 744.850 | 711.640 | PASS / PASS |
| Triangle | 1512.450 | 1490.523 | PASS / PASS |
| Oblique section | 789.887 | 787.226 | PASS / PASS |

The [full fresh benchmark log](experiments/java8-benchmark-2026-10-29.txt) contains all 15 batches. These are projection timings, not MP4 encoding throughput or video playback speed.

A fresh demo was generated with `Projection MRHead_1.nrrd datasets/mri-sweep.csv output/ide-check/teacher-demo.mp4`. FFprobe confirmed 90 frames, 100 x 106 pixels, 30 FPS and 3 seconds. The first, middle and last frames were inspected visually; each contained an MRI section. The existing demo.mp4 has the same frame count, size and playback duration. FFmpeg decoded both existing root videos without errors. The local MRI SHA-256 still matches the value documented in datasets/README.md.

## Final check after code cleanup 2026-10-29

The final review checked source commit `67bfa9b` against all three pages of the assignment PDF. All eight numbered deliverables are present. No required implementation gap was found. The implementation was also checked against the plane conditions in the Q&A and the row-by-row algorithm on page 2, including the documented choices for rounding and image dimensions.

Java 8 compilation with `-source 1.8 -target 1.8 -Xlint:all` completed without warnings. The Java suite passed 1002 checks, and the integration suite passed 24 checks, with the same decoded pixel errors as above.

The benchmark was rerun after the readability changes, using the same volume, planes, warmup, and batch sizes:

| Plane | Mean projection FPS | Slowest batch FPS | 30 FPS minimum / 100 FPS expected |
| --- | --- | --- | --- |
| Rectangle | 759.683 | 735.346 | PASS / PASS |
| Triangle | 1514.610 | 1464.105 | PASS / PASS |
| Oblique section | 785.921 | 779.778 | PASS / PASS |

Fresh videos were generated from the 90-row MRI sweep, the original three-row MRI list, and the two-row mixed-plane list. Both fresh and submitted videos passed checks for frame count, dimensions, 30 FPS playback, duration, and full decoding. Frames 0, 44, and 89 of the fresh MRI sweep were inspected visually and each showed an MRI section. The MRI hash still matches the dataset documentation. Required files are tracked in Git, documentation file links exist, and compiled classes and local output tools are not tracked.

The [final verification log](experiments/java8-final-audit-2026-10-29.txt) records commands and output. The earlier measurements remain above for comparison. This review does not add a direct pixel comparison with 3D Slicer.

### Points to explain during the demonstration

- Show demo.mp4 for the real MRI sweep. The default projection.mp4 is only three frames (0.1 seconds), so it can look like a still image.
- Show the mixed-plane input and test to explain support for both three-point triangles and four-point sections. The supplied three-row MRI list contains only four-point sections.
- The PDF intentionally leaves discretization incomplete. This implementation chooses ceiling dimensions, nearest-voxel rounding, a first row after one step, and endpoint clamping on a fractional final row. It stores the image as `image[y][x]`, whereas the PDF writes `E[x][y]`. Samples whose rounded output column is outside the fixed width are skipped, including the extra right endpoint when the base length is an integer. These choices are documented in ALGORITHM.md.
- World coordinates are converted using the NRRD origin and direction matrix. CSV coordinates must use that same coordinate system; LPS/RAS conversion is not automatic.
- Grayscale is scaled separately per frame, so brightness can vary. Full signed/unsigned values remain available in the raw projection array.
- There has been no direct pixel comparison against 3D Slicer. Correctness evidence comes from analytic fixtures and the independent video reference. Detached, floating-point and nonspatial NRRDs are outside the documented scope; these are not required by the assignment's integer-volume definition.
- IntelliJ must use the registered Java 8 SDK, the project folder as working directory and all three program arguments. FFmpeg must be available to the IntelliJ process through PATH or FFMPEG.
