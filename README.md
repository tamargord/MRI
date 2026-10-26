# MRI plane projection

Exercise 1 for Introduction to Software Engineering: sample a bounded plane from a 3D scalar volume and encode a sequence of sections as an MP4. Implemented in Java without third-party Java libraries.

A projection here is a planar section of the volume, not an intensity sum along a viewing ray. The project includes the implementation, requirements, detailed design, reproducible datasets, regression tests, benchmark, and demonstration videos.

## Build and run

Requirements: JDK 17 or newer and FFmpeg with the `libx264` encoder on PATH. FFprobe and Python 3 are additionally needed for the end-to-end tests. Validation used OpenJDK 22.0.2 on Windows 11.

From PowerShell in the project directory:

```powershell
javac -Xlint:all -d out src/*.java tests/*.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

In IntelliJ, run `Projection` with arguments `MRHead_1.nrrd planes.csv projection.mp4` and the project directory as the working directory. The existing IntelliJ configuration selects JDK 23; select an installed JDK 17 or newer if necessary.

If FFmpeg is not on PATH, set its executable path explicitly:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

The three positional arguments are input NRRD, input CSV, and output MP4. The output directory must already exist. A successful run replaces the requested output video. Invalid input or encoding failure exits with a nonzero status and an error message; an encoding failure preserves an existing output video.

For a visible three-second MRI sweep:

```powershell
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

Outputs provided in the repository:

- [projection.mp4](projection.mp4): the supplied three-plane example, 3 frames, 0.1 seconds.
- [demo.mp4](demo.mp4): 90 adjacent MRI sections, 3 seconds.
- [mixed-demo.mp4](datasets/mixed-demo.mp4): synthetic triangle and four-point sections with different image sizes, demonstrating a common video canvas.

## Scope and input contract

The supported NRRD subset is an attached, scalar 3D volume with dimensions in `1..1023`, raw/gzip/ASCII encoding, and signed or unsigned 8-, 16-, or 32-bit integer samples. Both byte orders and LF/CRLF headers are supported. Floating-point samples, detached payloads, nonspatial axes, nonzero data skips, and other encodings are explicitly rejected. See [dataset definitions](datasets/README.md) and the [NRRD format specification](https://teem.sourceforge.net/nrrd/format.html).

CSV files are headerless. Each nonempty row contains finite world coordinates, in the coordinate convention of the input NRRD. The supplied MRI uses left-posterior-superior (LPS); there is no automatic LPS/RAS conversion.

| Region | Field count | Point order |
| --- | --- | --- |
| Isosceles triangle | 9 | `a.x,a.y,a.z,b.x,b.y,b.z,c.x,c.y,c.z` |
| Four-point section | 12 | `a1.x,a1.y,a1.z,a2.x,a2.y,a2.z,b.x,b.y,b.z,c.x,c.y,c.z` |

Blank rows are ignored. Numeric whitespace is accepted. Invalid rows report their line number. A header row, empty coordinate, trailing comma, NaN, or infinity is an error.

Equal side lengths must differ by less than `0.001`. A four-point section also requires top length no greater than bottom length and coplanarity within `0.001` world units. Degenerate planes and crossing traversing sides are rejected. A repeated top point is accepted as the corresponding triangle. As in the assignment's explicit rules, parallel bases are not an additional requirement; simple coplanar sections satisfying the stated constraints use the same traversal.

## Detailed algorithm

### Loading and coordinates

`MRIReader` parses dimensions, scalar representation, encoding, endian, origin, and direction vectors. NRRD stores the first axis fastest. `Volume` therefore uses a flat buffer indexed as `x + sx * (y + sy * z)` and retains samples in byte, short, or int storage. Unsigned values are widened at sampling time; raw projection values use Java `long` so `4294967295` is preserved. Signed samples retain their signed values.

The metadata transform is `world = origin + D * voxel`, where the columns of `D` are the space-direction vectors. Its inverse is computed once using cross products and the determinant. It supports anisotropic, rotated, and nonorthogonal spatial axes. Singular or ill-conditioned transforms are rejected. If spatial fields are absent, the explicit project convention is zero origin and identity directions; `spacings`, when supplied without directions, scales the identity axes. CSV coordinates use that resulting coordinate system.

The scalar and spatial metadata are never replaced with constants from the sample MRI.

### Sampling a bounded plane

1. Validate the three or four points. A triangle is represented internally as a four-point section with coincident top endpoints.
2. Let `L` be the distance from the first top endpoint to `b`, and `B` the bottom-edge length. Allocate `ceil(L)` rows by `ceil(B)` columns, initially zero. Storage is `E[y][x]`.
3. Normalize both traversing edge vectors independently. At row `y`, move each top endpoint by `min(y+1, itsEdgeLength)` along its edge. The first row follows the first unit step, matching the assignment's traversal order. Clamping the final row is a deliberate endpoint policy for fractional lengths.
4. Let the current row endpoints be `left` and `right`, with span `s`. For nonzero spans, compute the unit direction across the row. Convert the left endpoint and across-row direction into voxel coordinates once per row.
5. For integer `k` from zero through `floor(s + 1e-9)`, sample `left + k * unitAcross`. The small tolerance repairs floating-point error at integer endpoints. This follows the assignment's `k <= span` rule rather than rounding a fractional span upward.
6. Center the destination column as `round((B-s)/2 + k)`, skipping columns outside the allocated image. A collapsed span samples one central pixel.
7. Use Java `Math.round` independently on each continuous voxel component. Out-of-range rounded indices contribute zero. Other samples retain their full signed or unsigned numeric value.

One traversal unit means one world-distance unit from the input transform. Image height follows the side length specified by the assignment, rather than the perpendicular height of a trapezoid. Nearest-neighbor ties follow Java rounding, toward positive infinity.

The hot sampling loop uses scalar arithmetic without allocating a point per pixel. Projection time is `O(H*W)` for valid sections and output storage is `O(H*W)`. Volume storage is `O(sx*sy*sz)` at the source scalar width. A 16-million-pixel limit bounds individual images and the common video canvas; available heap still limits volume sizes.

### Display and video

Raw intensities are separate from display values. Grayscale export clamps negative signed values to black and maps nonnegative values using `round(value * 255 / imageMaximum)`. An all-zero or all-negative image is black. Each image has its own display scale, so adjacent images can change brightness.

All frames use the maximum projection width and height, rounded upward to even dimensions. Smaller images are centered on a black canvas. The encoder uses H.264 at CRF 18, `yuv420p`, and 30 playback FPS. H.264 is lossy; exact intensity comparisons belong to raw images, not compressed video.

Each run uses a unique temporary PNG directory and an explicit frame count. Temporary images are removed after the run. Encoding first writes a temporary MP4 beside the requested destination; only successful, nonempty encoded output replaces it. The historical root `frame_*.png` and `projection.png` assets predate the completed implementation and are retained as earlier sample images; the CLI does not read them.

## Design responsibilities

| Class | Responsibility |
| --- | --- |
| `Point3D` | Immutable finite coordinate and vector operations |
| `Volume` | Scalar storage, spatial transform, nearest-neighbor access |
| `MRIReader` | NRRD parsing, validation, decompression, and scalar decoding |
| `Plane` | Geometry validation and output dimensions |
| `CSVReader` | Ordered CSV parsing and row-specific errors |
| `Projection` | Rasterization, grayscale rendering, CLI orchestration, video encoding |
| `DatasetGenerator` | Reproducible synthetic fixtures and benchmark volume |
| `Benchmark` | Warmed projection-only performance experiment |

The requirements and their verification are recorded in [SRS.md](SRS.md).

## Testing strategy and execution

Use analytic synthetic voxel values to establish exact expected samples before evaluating MRI appearance. Exercise transforms separately from geometry; then verify the full CLI by decoding its output video. Test malformed files and failures as well as successful paths.

```powershell
javac -Xlint:all -d out src/*.java tests/*.java
java -cp out RegressionTests
py tests/integration_test.py
```

On systems without the Windows Python launcher, use `python3 tests/integration_test.py`.

The Java suite passes **1002 checks**, covering scalar limits for every supported signed/unsigned width across raw/gzip/ASCII and both byte orders; first-axis ordering; CRLF; spatial transforms including anisotropy and shear; exact rectangle, triangle, and oblique samples; fractional lengths; nearest-neighbor ties; zero fill; zero/negative/high-range display; invalid geometry; invalid CSV; and malformed or truncated NRRD inputs.

The integration suite passes **24 checks**. It compares decoded video frames against an independent Python world-space reference with a tolerance for H.264 compression, checks frame count/order/common canvas, excludes stale PNGs, replaces a longer video with a shorter run, handles missing or misconfigured encoders, preserves existing output on failure, validates argument count, and checks CSV error location. Every check must pass; tests fail with nonzero exit status.

## Performance and experiment

```powershell
java -Xms256m -Xmx1g -cp out Benchmark
```

This generates a gzip-compressed `500 x 500 x 500` unsigned-byte NRRD under ignored `output/` if needed, loads it before timing, warms each plane for 150 iterations, and measures five batches of 300 projections each. An existing benchmark NRRD can be passed as the only argument.

Measured mean throughput on the test machine was **540 FPS** for a 500 x 500 rectangle, **782 FPS** for a 500 x 472 triangle, and **386 FPS** for a 500 x 500 oblique section. The slowest batch was **361 FPS**. All cases exceeded both the assignment's mandatory 30 FPS and expected 100 FPS. These results describe this machine and workload, not a guarantee for all machines or plane sizes.

See [EXPERIMENT.md](EXPERIMENT.md) for hardware, methodology, timing boundaries, variation, video inspection, and limitations. Raw measurements are included in [the benchmark log](experiments/benchmark-2026-10-26.txt). Video playback FPS is independent of projection throughput.

## Repository workflow

Work is hosted at [tamargord/MRI](https://github.com/tamargord/MRI). Commit and push each coherent, checked change; keep requirements and recorded results consistent with the implementation. Generated large benchmark volumes, compiled classes, and temporary output stay out of version control.
