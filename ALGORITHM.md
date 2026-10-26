# Projection algorithm

The code uses Java 8. Start with `Point3D`, which is a class with three coordinate fields and basic vector methods. Then read `Plane` for the point checks and `Projection.project` for the row-by-row algorithm.

## Reading the volume

`MRIReader` reads the NRRD header and then decodes the data. NRRD stores the first axis fastest, so `Volume` stores a voxel at:

```text
index = x + sx * (y + sy * z)
```

The volume uses a `byte[]`, `short[]`, or `int[]`, depending on the input type. Only the array for that type is allocated. Sample values are returned as `long`, which can hold the full unsigned 32-bit range, including `4294967295`. Signed input keeps its signed values.

## World coordinates and voxel indices

The CSV points use the coordinate system in the NRRD header. The relationship is:

```text
world = origin + D * voxel
voxel = inverse(D) * (world - origin)
```

The columns of `D` are the header's three space-direction vectors. `Volume` calculates the inverse once, using cross products and the determinant. This handles rotated axes, different spacing along each axis, and nonorthogonal axes. A singular or nearly singular transform is rejected.

If the header has no origin, the program uses `(0,0,0)`. If it has no directions, it uses the three unit axes, scaled by `spacings` when present. CSV points must use this same coordinate system. There is no automatic conversion between LPS and RAS.

## Checking the points

A triangle has points `a,b,c`, with equal lengths from `a` to `b` and `a` to `c`. Internally it uses the same algorithm as a four-point section, with both top points set to `a`.

A four-point section has points `a1,a2,b,c`. The program checks that:

- `abs(distance(a1,b) - distance(a2,c)) < 0.001`.
- `distance(a1,a2) <= distance(b,c)`.
- The points are coplanar within `0.001` world units.

Zero-length sides, collinear points, and crossing side edges are rejected. A repeated top point is allowed. The assignment's explicit four-point rules do not require parallel bases, so the code does not add that restriction.

## Creating the image

Let `L = distance(a1,b)` and `B = distance(b,c)`. The output has `ceil(L)` rows and `ceil(B)` columns, stored as `E[y][x]`. It starts filled with zeros.

1. Calculate unit vectors along `a1 -> b` and `a2 -> c`.
2. For row `y`, move each starting point `y+1` world units down its side. Stop at the endpoint if that distance would go past it. This means the first row comes after the first step, following the assignment pseudocode.
3. Call the two row endpoints `left` and `right`, and their distance `s`. Calculate the unit vector between them.
4. For each integer `k` from `0` through `floor(s + 1e-9)`, sample the position `left + k * unitAcross`. The tolerance handles floating-point error close to an integer span.
5. Place the sample at column `round((B-s)/2 + k)`. Ignore columns outside the image. If the span is almost zero, sample one pixel in the middle.
6. Convert sample positions to voxel coordinates and round each component with Java `Math.round`. Out-of-range indices return zero.

For example, a span of `1.6` gives samples at `k=0` and `k=1`. It does not add a sample at `k=2`. Fractional side lengths use a final clamped row in both the triangle and four-point methods.

The image height follows the side length in the assignment, not the perpendicular height of the shape. One step is one world-distance unit. Java rounding sends half-integer ties toward positive infinity.

To reduce work in the inner loop, the code transforms the row's start and direction into voxel coordinates once per row. It then samples using scalar arithmetic without creating a new point for each pixel.

For an image of height `H` and width `W`, projection time and image storage are `O(H*W)`. The volume takes `O(sx*sy*sz)` storage at its source scalar width. Images and the video canvas are limited to 16 million pixels. The volume must fit in the available Java heap.

## Grayscale and video

Raw images keep their original voxel values. For display, negative values become black. Other values use:

```text
gray = round(value * 255 / imageMaximum)
```

An all-zero or all-negative image is black. Each frame is scaled separately, which can cause brightness changes between frames.

The video canvas uses the largest image width and height, rounded up to even numbers for the encoder. Smaller images are centered on black. FFmpeg encodes H.264 at CRF 18, with `yuv420p` pixels and 30 playback FPS.

Each run creates its own temporary PNG folder and passes the exact frame count to FFmpeg. It removes the temporary images afterward. The video is first written to a temporary MP4; a successful, nonempty result replaces the requested output. The older root PNG files are kept as previous examples and are not read by the CLI.

## Classes

| Class | What it does |
| --- | --- |
| `Point3D` | Stores `x`, `y`, and `z` and provides vector operations. |
| `Volume` | Stores voxels and converts between world and voxel coordinates. |
| `MRIReader` | Reads and checks the NRRD header and data. |
| `Plane` | Checks geometry and calculates image dimensions. |
| `CSVReader` | Reads the planes in order and reports invalid rows. |
| `Projection` | Samples the planes, makes grayscale images, and calls FFmpeg. |
| `DatasetGenerator` | Creates the small test files and the large benchmark volume. |
| `Benchmark` | Measures repeated projections after warmup. |
