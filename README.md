# MRI plane projection

Java implementation of Exercise 1 for Introduction to Software Engineering: extract a two-dimensional image from a three-dimensional MRI volume along a plane defined by three or four points, then combine a sequence of these images into an MP4 video.

The project focuses on explicit algorithm design, correct sampling, robustness, and projection speed. Here, projection means sampling a planar section of the volume; intensities are not accumulated along a viewing ray.

## Scope and current status

The current demonstration reads `MRHead_1.nrrd`, reads plane coordinates from `planes.csv`, saves a grayscale PNG for each plane, and invokes FFmpeg to create a video. Both triangle and four-point projection methods exist. The supplied example exercises three adjacent four-point planes only.

The implementation currently depends on this MRI's dimensions and spatial metadata. General NRRD support, comprehensive geometry validation, and the assignment's performance requirement remain to be completed and verified. Requirements and acceptance criteria are defined in [SRS.md](SRS.md).

## Build and run

Prerequisites:

- A Java JDK with `javac` and `java` available. The IntelliJ project is configured for JDK 23.
- FFmpeg installed at `C:\ffmpeg\bin\ffmpeg.exe`, the path currently embedded in `Projection.createVideo`.
- The supplied MRI and CSV files in the project directory.

From PowerShell in the project directory:

```powershell
javac -d out src/Point3D.java src/MRIReader.java src/CSVReader.java src/Projection.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

In IntelliJ, run the `Projection` main class with program arguments `MRHead_1.nrrd planes.csv projection.mp4` and the project directory as the working directory.

Outputs are `frame_000.png`, `frame_001.png`, and so on in the working directory, plus the requested MP4. The program prints projection timing and benchmarks the first two input planes with 100 warmup iterations followed by 500 measured iterations each. These timing regions exclude MRI loading, PNG saving, and video encoding.

FFmpeg uses 30 frames per second, H.264, and `yuv420p`, padding dimensions to even numbers. Existing output video and matching PNG names are overwritten. Use a working directory without PNGs left over from a longer earlier run: the encoder currently consumes the numbered sequence without an explicit frame limit.

## CSV contract

Use a headerless CSV with one plane per nonempty row. Coordinates are floating-point world coordinates in the MRI's **left-posterior-superior (LPS)** coordinate system, rather than voxel indices. A coordinate is ordered `x,y,z`. The code does not convert RAS coordinates to LPS.

| Plane | Values per row | Point order | Geometry |
| --- | --- | --- | --- |
| Triangle | 9 | `a.x,a.y,a.z,b.x,b.y,b.z,c.x,c.y,c.z` | Equal lengths from apex `a` to `b` and `c` |
| Four-point plane | 12 | `a1.x,a1.y,a1.z,a2.x,a2.y,a2.z,b.x,b.y,b.z,c.x,c.y,c.z` | Equal lengths `a1` to `b` and `a2` to `c`; top edge no longer than bottom edge; coplanar points |

Length equality uses an absolute tolerance of `0.001`. Blank rows are ignored and surrounding numeric whitespace is accepted. The current CLI parses rows directly; the separate `CSVReader` class is not called by `main`.

The supplied CSV contains three four-point planes, in order, at world X values `2.145195`, `0.845200`, and `-0.454795`. They map approximately to voxel-axis Z indices 65, 66, and 67. Here voxel Z refers to the third array axis, which points along negative world X for this dataset.

## Algorithm and design decisions

### Volume coordinates

The NRRD header describes the world position of voxel index vector `v` as `p = origin + D * v`, where the columns of `D` are the three space-direction vectors. For the supplied MRI:

```text
origin = (86.644897460937486, -133.92860412597656, 116.78569793701172)
D columns = (0,1,0), (0,0,-1), (-1.2999954223632812,0,0)
```

Inverting that transform gives the current sampling conversion:

```text
voxelX = worldY + 133.92860412597656
voxelY = 116.78569793701172 - worldZ
voxelZ = (86.64489746093749 - worldX) / 1.2999954223632812
```

`MRIReader` skips the text header, decompresses an attached gzip payload, and reads little-endian two-byte values into `M[x][y][z]`, with the first axis varying fastest in the input. Dimensions and the transform are currently constants; the reader does not parse these metadata fields.

### Plane rasterization

1. Compute equal side length `L` and bottom edge length `B`. Allocate a zero-filled image with `height = ceil(L)` and `width = ceil(B)`. Java stores this as `E[row][column]`, or `E[y][x]`.
2. Normalize the two vectors along the side edges. For a triangle, both edges start at `a`; for a four-point plane, they start at `a1` and `a2`.
3. Move the two endpoints one world-distance unit down their edges for each output row. Sampling starts after the first step, as in the assignment pseudocode. The four-point method clamps the final step to the edge length. The triangle method currently advances by whole steps without this clamp.
4. For the current endpoints `left` and `right`, calculate span `s = distance(left,right)` and a unit vector across the row. If `s < 1e-7`, sample a single central output pixel.
5. For integer `k` from 0 through `round(s)`, sample the world position `left + k * unitAcross`. Center its output column using `round(B/2 - s/2 + k)`; ignore columns outside the allocated image.
6. Transform each world position into voxel coordinates and round each component with Java `Math.round` for nearest-neighbor sampling. An index outside the volume contributes zero; other values are read as `M[x][y][z] & 0xFFFF`.
7. Return the raw intensity image. For PNG export, find its maximum and map intensities to `round(value * 255 / maximum)`. A zero maximum produces a black image.

World-distance units follow the source metadata. Output spacing is a project choice of one world-distance unit along each traversal direction. The vertical dimension follows the side length, rather than perpendicular trapezoid height. The explicit rounding policy resolves an ambiguity in the assignment: its inner-loop condition uses `k <= s`, whereas the current implementation uses `k <= round(s)`. These differ for some fractional spans and need a dedicated boundary test before final acceptance.

For an output of `H` rows and `W` columns, projection time is bounded by `O(H * W)` for the intended geometry and output memory is `O(H * W)`. The loaded volume occupies `O(sx * sy * sz)` storage, with additional Java nested-array overhead. Only one raw projection is retained at a time; all PNGs remain on disk.

### Responsibilities

| Class | Current responsibility |
| --- | --- |
| `Point3D` | Coordinate and vector components |
| `MRIReader` | Load the supplied gzip-compressed MRI payload |
| `Projection` | Geometry, coordinate conversion, sampling, grayscale export, timing, CLI orchestration, and FFmpeg invocation |
| `CSVReader` | Separate CSV-to-points reader, currently unused by the CLI |

## Dataset

| Asset | Description and role |
| --- | --- |
| `MRHead_1.nrrd` | Local MRI sample: 256 x 256 x 130, signed 16-bit `short`, little endian, attached gzip payload, LPS coordinates; observed voxel range 0 to 279 |
| `planes.csv` | Three adjacent four-point sections; current demonstration input |
| `frame_000.png` through `frame_002.png` | Existing 100 x 105 grayscale projection images |
| `projection.mp4` | Existing three-frame, 100 x 106 video; one added padding row for even dimensions |

The assignment points to 3D Slicer sample data. The precise download provenance and redistribution terms for the local MRI have not been recorded here and should be established before redistributing it.

Expand the validation dataset with small synthetic volumes whose voxel values encode their indices, an all-zero volume, a second NRRD with different dimensions and spacing, and a synthetic 500 x 500 x 500 8-bit volume for the required benchmark. These additional datasets have not yet been added. General metadata and 8-bit loading support are prerequisites for the larger benchmark through the CLI.

## Testing strategy

| Area | Cases and expected evidence |
| --- | --- |
| MRI loading | Known voxel values and axis order; gzip truncation; missing header terminator; changed dimensions, byte order, type, origin, and directions |
| Coordinates | Voxel-to-world round trips; asymmetric synthetic data to detect flipped axes; nearest-neighbor tie cases; known axis-aligned and rotated sections |
| Geometry | Valid triangles and four-point sections; zero-length sides; collinear points; unequal legs; noncoplanar points; top edge longer than bottom edge; nonfinite coordinates |
| Rasterization | Integer and fractional lengths; final-row stepping; row centering; fractional span rounding; samples outside the volume; a fully external plane produces black |
| Intensities | All-zero image; known nonzero maximum; values above 32767 for unsigned input; explicit handling of signed source data |
| CLI/video | Missing arguments; invalid CSV; trailing empty fields; empty CSV; missing FFmpeg; encoder failure; one decoded frame per nonempty row in order; mixed image sizes; stale PNGs from an earlier run |
| Performance | Warmed repeated projection on the required volume with recorded machine, JVM, output dimensions, plane geometry, and timing boundaries |

For independent sampling checks, derive the affine transform from the NRRD header rather than copying the hardcoded conversion. Compare grayscale PNG pixels exactly under the declared rounding and normalization rules. H.264 is lossy, so decoded MP4 intensities should not be required to match PNG intensities exactly. Check video frame count, ordering, dimensions, and duration separately. A visual comparison in 3D Slicer remains useful with matching plane position, coordinate convention, orientation, and display window.

## Current experiment results

On 2026-10-26, all three existing PNGs were compared with an independent calculation from the decompressed source volume. The reference derived the affine transform from the MRI header and interpolated row endpoints in voxel coordinates. It used the current implementation's sampling, span rounding, and grayscale policies.

| Frame | Approximate voxel Z | Dimensions | Differing grayscale pixels | Maximum sampled raw intensity |
| --- | --- | --- | --- | --- |
| 0 | 65 | 100 x 105 | 0 / 10,500 | 154 |
| 1 | 66 | 100 x 105 | 0 / 10,500 | 151 |
| 2 | 67 | 100 x 105 | 0 / 10,500 | 156 |

The [existing MP4 output](projection.mp4) was inspected with FFprobe: 3 frames, 30 FPS, 100 x 106 pixels, and 0.1 seconds duration. The frame count matches the three input planes. Pixel equality was checked on the PNGs, rather than on decoded video frames. 3D Slicer was not found in the checked installation locations, so a Slicer comparison was not performed.

This result validates the supplied axis-aligned four-point example under the stated policies. It does not establish triangle correctness, arbitrary-plane correctness, general file support, or performance compliance. No measured benchmark results are recorded yet. Video playback at 30 FPS is independent of projection throughput.

For the performance experiment, exclude loading, warm up the JVM, measure repeated projections with an observable checksum, and report average milliseconds and `FPS = 1000 / averageMilliseconds`. Record at least the volume type and size, plane/output dimensions, CPU, RAM, OS, JVM version, warmup count, measured iteration count, and repeated-run variation. The assignment requires more than 30 projection FPS and expects at least 100 FPS on a 500-cubed byte volume. Report complete pipeline timing separately if measured.

## Remaining work

1. Resolve edge sampling and rounding decisions against the assignment and test triangles and rotated planes.
2. Parse NRRD metadata and define supported scalar types; remove dataset-specific dimensions and coordinate constants.
3. Validate finite coordinates, degenerate geometry, argument count, and CSV fields with clear errors.
4. Make FFmpeg configuration portable, isolate generated frames, and define a common video frame size for varying planes.
5. Add the planned datasets, run the testing strategy, and record the required performance experiment.
6. Record dataset provenance and commit completed project work regularly.
