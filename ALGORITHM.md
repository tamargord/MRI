# Projection algorithm

This document explains the algorithm and the main design choices for Exercise 1.

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

