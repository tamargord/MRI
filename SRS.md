# Software requirements specification

Exercise 1: MRI plane projection. Version 1.0, 2026-10-26.

## Goal

Read a 3D NRRD volume and a CSV list of planes, then produce an MP4 with one frame per plane in the same order. The project uses Java.

## Input and output

The command takes three paths:

```text
java -cp out Projection input.nrrd planes.csv output.mp4
```

The NRRD must contain a scalar 3D integer volume. Each dimension must be between 1 and 1023. Supported types are signed and unsigned 8-, 16-, and 32-bit integers. Data can be raw, gzip, or ASCII, stored in the same file as the header. Both byte orders and LF/CRLF headers are supported.

Each nonempty CSV row has 9 numbers for `a,b,c`, or 12 for `a1,a2,b,c`. Points use `x,y,z` world coordinates from the NRRD. The output is an MP4 at 30 playback FPS.

## Functional requirements

| ID | Requirement |
| --- | --- |
| FR-01 | Accept exactly three path arguments. Report a usage error for other argument counts. |
| FR-02 | Read dimensions, type, encoding, byte order, origin, and spatial directions from the NRRD header. Reject unsupported or malformed files with an error. |
| FR-03 | Preserve signed values and the full unsigned 8/16/32-bit ranges in raw projections. |
| FR-04 | Read planes in CSV order. Ignore blank lines. Reject missing, nonnumeric, or nonfinite coordinates and report the row number. |
| FR-05 | Accept nondegenerate isosceles triangles with side-length difference less than `0.001`. |
| FR-06 | For four points, check equal side lengths within `0.001`, coplanarity, and top length no greater than bottom length. Reject crossing sides and degenerate geometry. A repeated top point is allowed. |
| FR-07 | Convert world positions to voxel indices, sample the nearest voxel, and return zero outside the volume. |
| FR-08 | Create the 2D image using the assignment's row traversal. Document dimension, rounding, and endpoint choices. |
| FR-09 | Convert images to grayscale. An all-zero image must stay black. Keep the raw values separate from display values. |
| FR-10 | Write exactly one frame per nonempty CSV row. Use a common canvas for different image sizes and exclude frames from earlier runs. |
| FR-11 | Return a nonzero exit status on input or encoder errors. Preserve an existing output when validation or encoding fails. |
| FR-12 | Provide a benchmark that measures projection work separately from loading, image saving, and encoding. |

## Other requirements

The assignment requires at least 30 projection FPS on a `500 x 500 x 500` byte volume and expects 100 FPS. The benchmark must record its input, plane sizes, warmup, iteration counts, computer details, and results.

The project must include build/run instructions, an algorithm description, a defined dataset, a testing strategy, and an experiment report with an output video. Code and documentation are committed to the GitHub repository as work is completed.

Loading, geometry checks, CSV parsing, and projection have separate classes. The program closes streams and removes its temporary files after success or handled failure. Images and the video canvas are limited to 16 million pixels; volume size is also limited by available heap memory.

## Design choices

The output size is `ceil(firstSideLength)` rows by `ceil(bottomLength)` columns. Sampling starts after the first side step, and the final row stops at the side endpoints. Across a row, integer samples satisfy `k <= span`, with `1e-9` tolerance for floating-point error. Centering and nearest-neighbor sampling use Java `Math.round`.

Missing spatial metadata uses zero origin and identity directions, scaled by `spacings` if available. Signed values stay signed in raw output; display values clamp negatives to black and scale each frame by its maximum. The encoder uses H.264 at CRF 18. [ALGORITHM.md](ALGORITHM.md) gives the formulas and the reasons for these choices.

## Limits

Detached NRRD data, nonzero byte/line skips, nonspatial axes, floating-point values, and unsupported encodings are rejected. Singular spatial transforms are also rejected. The code does not automatically convert LPS coordinates to RAS.

The project needs JDK 17+ and FFmpeg, found through PATH or the `FFMPEG` environment variable. Tests were run on Windows 11 with Java 22; other systems have not been tested.

## Checking the requirements

The Java tests check known voxel values, coordinate transforms, plane geometry, rounding, and invalid input. The Python test runs the CLI, decodes the video, and checks frame count, order, size, image content, and failure behavior.

The recorded run passed 1002 Java assertions and 24 video/CLI checks. These counts include repeated checks across pixels and file formats. The slowest measured benchmark batch was 361 FPS. Test logs and the measurement setup are in [EXPERIMENT.md](EXPERIMENT.md). A direct comparison in 3D Slicer was not performed.

The [dataset notes](datasets/README.md) describe the input files. The [README](README.md) contains run commands and links to the example videos.
