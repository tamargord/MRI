# SRS - project requirements

Exercise 1, Java 8. Updated 2026-11-04.

The goal is to read an MRI file and a list of planes, then create a video with one image for each plane.

## Input and output

The program takes three filenames:

```text
java -cp out Projection input.nrrd planes.csv output.mp4
```

The MRI is a 3D NRRD file. Each dimension is between 1 and 1023. The reader supports signed and unsigned 8-, 16-, and 32-bit integers. The data can be raw, gzip, or ASCII and must be in the same file as the header. Both byte orders and Windows/Unix line endings are accepted.

The CSV has no header. Each nonempty row contains three or four points, written as `x,y,z`. These are world coordinates from the MRI file. The output is an MP4 that plays at 30 FPS.

## What the program should do

1. Accept exactly three filenames and show a usage message otherwise.
2. Read the volume size, value type, and coordinate information from the NRRD header. Report an error for a bad or unsupported file.
3. Keep the original signed values and the full unsigned 8/16/32-bit ranges when creating the image array.
4. Read CSV rows in order and skip blank lines. Invalid point sets raise validation exceptions with tolerance 0.001; the demo catches row errors and inserts a black frame marked with the physical CSV line number, reports the reason, and continues. Malformed coordinates, NaN, infinity, and wrong field counts follow the same recovery policy. The strict reader API still throws on invalid rows. An all-invalid list produces an error video; an empty or unreadable CSV is fatal.
5. Check that three points form an isosceles triangle. The side lengths must differ by less than `0.001`; the points cannot be collinear.
6. For four points, check equal side lengths within `0.001`, coplanarity within `0.001`, and a top edge no longer than the bottom. Reject crossing sides and zero-length sides. A repeated top point is allowed.
7. Find the nearest voxel for each sample. Return zero if its rounded position is outside the array.
8. Build the image row by row using the assignment's algorithm. Explain how rounding and the final row are handled.
9. Convert the image to grayscale. An all-zero image must stay black. Keep the original values separate from the display values.
10. Put one frame per nonempty CSV row into the video, including error frames, in the same order. Use one frame size and do not include old images from earlier runs. When errors occur, use at least 320 x 180 pixels for readable labels; center valid sections without resampling.
11. Report file and encoder errors. Return a nonzero exit status and keep an existing output video if checking input or encoding fails.
12. Provide a speed test that times projection separately from loading and video creation.

## Speed and program structure

The assignment requires at least 30 projection FPS on a 500 x 500 x 500 byte volume and expects 100 FPS. The experiment should record the computer, input, plane sizes, warmup, number of runs, and results.

The code uses classes, arrays, loops, and methods. File reading, point checks, and projection are in separate classes. Files are closed after use, and temporary images are removed. A projection or video frame cannot exceed 16 million pixels. The MRI must fit in the available Java memory.

## Choices made for this solution

The image has `ceil(firstSideLength)` rows and `ceil(bottomLength)` columns. The first row comes after the first step down the sides. The final step stops at the endpoints. Across a row, integer samples satisfy `k <= span`, with a tolerance of `1e-9` for rounding error. Pixel positions use Java `Math.round`.

If no origin is given, it is `(0,0,0)`. If no directions are given, the program uses the normal x, y, z axes, scaled by `spacings` when present. Negative values stay in the raw image but appear black when displayed. Each frame is scaled by its own maximum. [ALGORITHM.md](ALGORITHM.md) explains these steps.

## Limits and checks

Detached NRRD data, nonzero byte/line skips, nonspatial axes, floating-point values, unsupported encodings, and a coordinate transform with no usable inverse are rejected. LPS and RAS coordinates are not automatically converted.

The current version was tested with JDK 8 on Windows 11. All application and test source code is Java 8. FFmpeg is required to make the video; the integration tests also require FFprobe. IntelliJ is set to Java 8 language and bytecode levels; the user must select a JDK 8 installation.

The original 2026-10-27 tests passed 1002 Java assertions and 24 video/CLI checks. These are individual checks, including repeated pixel and file-format checks. The slowest speed-test batch was about 686 FPS. No direct comparison in 3D Slicer was made. [EXPERIMENT.md](EXPERIMENT.md) contains the commands and logs, and [datasets/README.md](datasets/README.md) describes the data.

The assignment review on 2026-10-29 reran both suites on Java 8 with the same passing check counts. The fresh benchmark's slowest batch was 711.640 FPS. The original results above remain the 2026-10-27 measurements; the dated review in EXPERIMENT.md records the newer run separately.

The final check after the code readability cleanup on 2026-10-29 again passed both suites with the same counts. Its slowest benchmark batch was 735.346 FPS. Fresh real-MRI and mixed-plane videos, along with the three submitted example videos, passed frame-count, dimensions, playback-rate, duration, and decoding checks. The final results are recorded in [EXPERIMENT.md](EXPERIMENT.md#final-check-after-code-cleanup-2026-10-29).

On 2026-11-02, the video integration tests were converted to Java. Compilation on JDK 8 with all lint warnings enabled completed without warnings. `RegressionTests` passed 1002 checks and `IntegrationTests` passed 24 checks, including independent reference-image comparisons and output preservation after failed runs. The application and benchmark code did not change; benchmark results above remain the dated measurements.

On 2026-11-04, the added-page error-frame policy passed warning-free Java 8 compilation, 1002 regression checks, and 47 end-to-end checks. File-level and encoder failures remain fatal and preserve existing output. Projection and benchmark code are unchanged; the dated speed results above still apply. Submission instructions and a text file containing the supplied student ID are included; public visibility verification and external submissions remain user steps.

The complete four-page requirements review on 2026-11-04 again passed warning-free Java 8 compilation, 1002 regression checks and 47 end-to-end checks. A fresh benchmark had a minimum batch rate of 755.018 projection FPS. All four supplied videos and a fresh 90-frame MRI video passed metadata and full-decoding checks. Public repository access is still unverified (anonymous HTTP 404); Moodle and form submissions are not verified. See [REQUIREMENTS_REVIEW.md](REQUIREMENTS_REVIEW.md).

The subsequent 2026-11-04 bug review fixed UTF-8 BOM handling in CSV files and nonfinite sampling coordinates. An initial CSV BOM is removed before blank-line detection and parsing; physical line numbers are preserved. Direct voxel sampling returns zero for NaN or infinite coordinates instead of treating NaN as index zero. CSV coordinates still reject NaN and infinity. Compilation remains warning-free on Java 8, with 1014 regression checks and 49 end-to-end checks passing. The fresh post-fix benchmark minimum is 701.922 FPS. Results are saved in [experiments/java8-bug-fixes-2026-11-04.txt](experiments/java8-bug-fixes-2026-11-04.txt).
