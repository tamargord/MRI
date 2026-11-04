# Explaining the MRI project

Use this as a practice guide. Be able to connect each explanation to the code you show.

## 1. Explain the goal (30 seconds)

The input MRI is a three-dimensional grid of values. One grid cell is called a voxel, like a pixel with depth. A plane describes a flat section through that grid. The program samples values along that section to produce a two-dimensional image.

The program takes three filenames: the NRRD volume, a CSV list of planes, and the output MP4. Each nonempty CSV row produces one video frame, in the same order.

Here, "projection" means the section sampled by the assignment's algorithm. Each sample reads one nearest voxel; it does not add values along a viewing ray.

## 2. Show the demo (one minute)

In IntelliJ, select `Projection`, Java 8, and the project folder as working directory. Set the program arguments to:

```text
MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

Run it and open `demo.mp4`. The CSV has 90 sections, so the output contains 90 frames. At 30 frames per second, it lasts `90 / 30 = 3` seconds. Java creates the frame images; FFmpeg encodes them as an MP4.

Open `datasets/mri-sweep.csv` and point out that every row has 12 numbers: four points, each with x, y and z coordinates. A three-point row has nine numbers. The files have no header.

The real MRI demo uses four-point sections. The separate `datasets/mixed-planes.csv` example contains a four-point section and a triangle. Its two-frame video is mainly useful for testing.

## 3. Walk through the code (two minutes)

Start at `Projection.main`, which calls `run` and prints an error if the run fails. In `run`, show input validation, reading the CSV and MRI, the loop that makes one image per plane, and the video call. Then open `Projection.project` for the main algorithm.

| Class | What to explain |
| --- | --- |
| `Point3D` | Stores x, y and z; provides vector operations such as subtraction, length and cross product. |
| `Plane` | Checks the point geometry and calculates the image width and height. |
| `CSVReader` | Reads the rows, makes points and planes, and identifies invalid rows by line number. |
| `MRIReader` | Reads the NRRD header and voxel values. The header describes size, type and coordinates. |
| `Volume` | Stores voxel values, converts coordinates, and returns the nearest voxel value. |
| `Projection` | Samples a plane, makes a grayscale image, and coordinates video creation. |
| `DatasetGenerator` | Makes small volumes whose values are known, plus the large benchmark volume. |
| `Benchmark` | Times repeated projections after warmup, leaving file loading and encoding outside the timer. |

In `MRIReader`, start with `loadMRI`, then follow `validateHeader`, `readSizes`, and `readDirections`. The data-reading loops are in `readAsciiValues` and `readBinaryValues`. In `Plane`, `leftSideLength`, `rightSideLength`, and `bottomLength` match the measurements used by the projection loop.

The steps in `Projection.project` are:

1. Allocate an image of zeros. Its height comes from the side length and its width from the bottom edge length, both rounded up.
2. Calculate a unit vector down each side. A unit vector has length one, so it describes one step in that direction.
3. For each row, find the left and right endpoints after the next step down the sides. The first row is after one step. Clamp the last step to the endpoints.
4. Find the direction across that row and sample at whole-unit distances. Center the samples in the image using `leftMargin`.
5. Convert sample positions to voxel coordinates, round to the nearest voxel and read the values. An out-of-bounds rounded voxel index gives zero.

For a triangle, both top points are the same point. That allows the same loop to handle three-point and four-point input.

### A small example you can calculate

The Java regression test uses a volume with value `x + 10*y + 100*z` and identity coordinates. Consider these four points:

```text
a1=(0,0,2), a2=(6,0,2), b=(0,4,2), c=(6,4,2)
```

Each side has length 4 and the bottom has length 6. The output is four rows by six columns. The first step reaches source y=1, so the first output row is:

```text
210 211 212 213 214 215
```

The last output row samples source y=4:

```text
240 241 242 243 244 245
```

These are raw voxel values, before display scaling. The fixed width has columns 0 through 5; the additional right endpoint at column 6 is skipped. This handles the assignment's inclusive sampling loop without writing outside the array.

### Explain the coordinate conversion

CSV positions use the MRI's world coordinates. Array positions count voxels. The header's origin tells where voxel zero is located, and its direction vectors tell how a step along each voxel axis moves in world coordinates, including spacing and rotation.

The relationship is `world = origin + D * voxel`. To reverse it, subtract the origin and apply the inverse matrix: `voxel = inverse(D) * (world - origin)`. `Volume` calculates that inverse once. For a direction vector, there is no origin to subtract.

In the inner projection loop, the code uses a converted starting point plus `k` times a converted step. That avoids repeating a complete coordinate conversion for every sample. The derivation and formulas are in [ALGORITHM.md](ALGORITHM.md).

## 4. Show the evidence (one minute)

Open [EXPERIMENT.md](EXPERIMENT.md), then point to the corresponding test code or saved log.

- `RegressionTests.java`: 1014 checks, including known voxel values, rotated coordinates, triangles, four-point sections, rounding and invalid inputs. These are individual checks, including repeated pixel checks.
- `IntegrationTests.java`: 49 checks of the running Java program and encoded video, including frame order, count, content, errors and reruns. It calculates expected images independently of the application code. Both test suites passed on Java 8 on 2026-11-04; all application and test code is Java.
- `Benchmark.java`: a 500 x 500 x 500 unsigned-byte volume, 150 warmup projections per plane, then five batches of 300. After the bug fixes on 2026-11-04, the slowest batch was 701.922 projection FPS. The assignment requires 30 FPS and expects 100 FPS.

The video plays at 30 FPS. Benchmark FPS measures how quickly the algorithm computes images; loading, PNG creation and MP4 encoding are excluded. Results depend on the computer and the tested planes.

## Questions to practice

**Why are there separate classes?** Each class has a specific job. Reading files, checking geometry and sampling images can be understood and tested separately.

**What makes the triangle valid?** The two sides from its top point must differ in length by less than 0.001, and the points must not be collinear. Four-point sections also need coplanarity and a top edge no longer than the bottom. The code rejects degenerate and crossing shapes.

**What is a cross product used for?** The cross product of two edge vectors gives a normal perpendicular to their plane. A zero normal indicates collinearity. The dot product with this normal helps check how far a fourth point lies from the plane. `Volume` also uses cross products and a determinant to calculate the inverse transform.

**Why use a one-dimensional array for a three-dimensional MRI?** It stores the same grid compactly. The index is `x + sx * (y + sy * z)`. Adjacent x values are next to one another, matching the NRRD data order.

**Why return `long` values?** Java's `int` cannot hold the full unsigned 32-bit range. A `long` can hold 4294967295. Storage still uses the file's byte, short or int width.

**Why does the output use grayscale?** It maps positive values into 0..255 for display. Negative values appear black. Each frame uses its own maximum, which can cause brightness changes; the raw values remain separate.

**Why are the image corners black?** Areas outside the sampled shape remain zero. Samples outside the volume also return zero. Smaller images are centered on a common black video canvas.

**How much work does projection take?** At most approximately the number of pixels in the image: `O(height * width)` time and output memory. The loaded volume uses separate memory proportional to its voxel count and value width.

**What happens if something is wrong?** Invalid coordinates and shapes raise validation exceptions. The demo catches row errors and inserts marked black frames, then continues with valid rows. Show `datasets/error-recovery.mp4` and its CSV. File-level errors, empty CSV, invalid arguments, and encoder failures return a nonzero status and preserve an existing output video.

**What are the limits?** The supported NRRDs contain a scalar 3D integer volume with data in the same file, using raw, gzip or ASCII encoding. Floating-point and detached data are unsupported. The volume must fit in memory, and a frame is limited to 16 million pixels. LPS/RAS coordinate conversion is not automatic. There has been no direct pixel comparison against 3D Slicer.

## Before presenting

- Practice the worked example and point to its check in `tests/RegressionTests.java`.
- Keep `Projection.java`, `Plane.java`, `Volume.java`, the demo CSV and `EXPERIMENT.md` ready to open.
- Check that FFmpeg is available to IntelliJ. See [README.md](README.md#running-it) for setup instructions.
- Have `demo.mp4` ready to play and replay; three seconds is short.
- Explain the choices you understand, and use the detailed algorithm document when discussing the matrix calculations.
