# How the projection works

The main method to read is `Projection.project`. It creates one image for one plane. `Point3D` stores the coordinates, `Plane` checks the shape, and `Volume` holds the MRI values.

## Reading the MRI

`MRIReader` reads the NRRD header first. The header gives the array size, value type, byte order, compression, and coordinate information.

The values are stored in a one-dimensional array. NRRD stores the x values next to each other, so the position of voxel `(x,y,z)` is:

```text
index = x + sx * (y + sy * z)
```

For example, moving one step in x increases the index by 1. Moving one step in y increases it by `sx`.

The program uses a `byte[]`, `short[]`, or `int[]`, depending on the file type. Only one is allocated. A sampled value is returned as `long` so even the largest unsigned 32-bit value, `4294967295`, fits.

## Converting coordinates

The CSV points are positions in the MRI's coordinate system. They are not array indices. The origin and direction vectors in the header tell the program how to convert them:

```text
world = origin + D * voxel
voxel = inverse(D) * (world - origin)
```

`D` is a 3 x 3 matrix whose columns are the direction vectors. `Volume` calculates its inverse once using cross products and the determinant. This also works when the MRI axes are rotated, have different spacing, or are not at right angles. A matrix with no usable inverse causes an error.

If the origin is missing, the code uses `(0,0,0)`. Missing directions mean the normal x, y, z axes, scaled by `spacings` if it is given. There is no automatic conversion between LPS and RAS.

## Checking the shape

For a triangle `a,b,c`, the distances `a` to `b` and `a` to `c` must agree within `0.001`. The code then uses `a` for both top points, so the same loop can handle both shapes.

For four points `a1,a2,b,c`, the checks are:

```text
abs(distance(a1,b) - distance(a2,c)) < 0.001
distance(a1,a2) <= distance(b,c)
```

The points must also lie on the same plane within `0.001` world units. Zero-length sides, points on one line, and crossing side edges are rejected. Coincident top points are allowed. Parallel bases are not an extra check because they are not required by the assignment's listed four-point conditions.

## Filling the image

Let `L` be the length from `a1` to `b`, and `B` the bottom length from `b` to `c`. The output starts as a zero-filled array with `ceil(L)` rows and `ceil(B)` columns. It is stored as `image[y][x]`.

For each row:

1. Move one world unit farther down each side. Stop at an endpoint if the step would go past it. The first row is after the first step, as in the assignment.
2. Find the current left and right endpoints. Call their distance `span`.
3. Calculate the unit vector from left to right. Sample at whole-unit distances `k`, from 0 through `floor(span + 1e-9)`.
4. Center the row by placing each sample at `round((B-span)/2 + k)`. Skip columns outside the image. If the span is almost zero, sample one pixel in the middle.
5. Convert the sample to voxel coordinates. Round each coordinate with `Math.round`, then read that value from the MRI. A rounded index outside the volume gives zero.

A span of `1.6` gives samples at 0 and 1. The `1e-9` tolerance is there for floating-point error near a whole number. Half-integer ties use Java rounding, toward positive infinity. Both plane shapes stop at their endpoints on a fractional final row.

The image height follows the side length from the assignment. It is not the perpendicular height of the shape.

For speed, the row's starting point and step are converted to voxel coordinates before the inner loop. The loop then uses numbers directly instead of creating a new point object for every pixel.

For height `H` and width `W`, projection time and output memory are `O(H*W)`. Volume memory is `O(sx*sy*sz)` at the file's value width. The volume must fit in Java memory. Images and video frames are limited to 16 million pixels.

## Making the video

The original voxel values stay in the image array. For display, negative values become black. The other values are converted to 0..255 using:

```text
gray = round(value * 255 / imageMaximum)
```

An all-zero or all-negative image is black. Every frame uses its own maximum, so brightness can change between frames.

All frames use the largest image width and height, rounded up to even numbers. Smaller images are centered on black. The code saves temporary PNG files and asks FFmpeg to join them into an MP4 at 30 FPS. FFmpeg uses H.264, CRF 18, and `yuv420p`.

Each run uses a new temporary folder and gives FFmpeg the exact number of frames. Afterward, the temporary files are deleted. The old output video is replaced only after encoding succeeds and produces a nonempty file. The root PNG files are old examples and are not used by the program.

## Files

- `Point3D.java`: coordinates and vector calculations.
- `Volume.java`: MRI values and coordinate conversion.
- `MRIReader.java`: reading the NRRD header and data.
- `Plane.java`: checking points and calculating image size.
- `CSVReader.java`: reading the plane list.
- `Projection.java`: creating images and calling FFmpeg.
- `DatasetGenerator.java`: creating test data.
- `Benchmark.java`: measuring projection speed.
