# MRI Plane Projection

This is my project for Exercise 1

The goal is to take a 3D MRI file and create 2D images from different planes inside it. The program reads the planes from a CSV file and joins the images into an MP4 video. Each row in the CSV becomes one frame in the video.

The project is written in Java. It supports planes defined by an isosceles triangle or by four points, as described in the assignment.

## What you need

- Java JDK 17 or newer.
- FFmpeg installed and available on PATH.
- Python 3 and FFprobe if you want to run the video tests.

I tested the project on Windows 11 with Java 22.

## How to run

Open a terminal in the project folder and run:

```powershell
javac -d out src/*.java tests/*.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

The program takes three arguments:

1. The NRRD file containing the MRI data.
2. The CSV file containing the plane coordinates.
3. The name of the output MP4 file.

In IntelliJ, you can run the `Projection` class with these program arguments:

```text
MRHead_1.nrrd planes.csv projection.mp4
```

Use the project folder as the working directory. If FFmpeg is not on PATH, set its location in PowerShell:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

## Input files

The program reads 3D NRRD files with signed or unsigned 8-, 16-, or 32-bit integer values. It supports raw, gzip, and ASCII data stored in the same file as the header. The file header gives the volume size and coordinate information.

The CSV has no header. Each nonempty row contains either:

- **9 numbers:** the x, y, z coordinates of points `a, b, c` for a triangle.
- **12 numbers:** the x, y, z coordinates of points `a1, a2, b, c` for a four-point plane.

The coordinates are world coordinates from the NRRD file, not array indices. The supplied MRI uses LPS coordinates. The program checks the side lengths and makes sure the points form a valid plane.

More information about the input files is in [datasets/README.md](datasets/README.md).

## How the algorithm works

1. Read the MRI data and its header.
2. Read and check the points for each plane.
3. Move down the two sides of the triangle or four-point plane, one row at a time.
4. For each row, move between the two sides and find the nearest MRI voxel at each sample position.
5. Put the voxel values into a 2D image. Points outside the volume get a value of zero.
6. Convert the image to grayscale and use FFmpeg to create the video.

If the planes produce different image sizes, the program centers them on a common black background. The video plays at 30 FPS.

The formulas, rounding choices, and class descriptions are in [ALGORITHM.md](ALGORITHM.md).

## Example videos

- [projection.mp4](projection.mp4): the original example with 3 frames. It lasts only 0.1 seconds because the CSV has 3 rows.
- [demo.mp4](demo.mp4): a longer example with 90 MRI sections, lasting 3 seconds.
- [mixed-demo.mp4](datasets/mixed-demo.mp4): an example using a triangle and a four-point plane with different image sizes.

To create the longer MRI video:

```powershell
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

## Tests

I used small generated volumes with known values to check the projection results. The tests also cover rotated planes, invalid inputs, points outside the volume, and video frame order.

After compiling, run:

```powershell
java -cp out RegressionTests
py tests/integration_test.py
```

Use `python3` instead of `py` if needed. The Java tests passed 1002 checks, and the video tests passed 24 checks.

## Performance experiment

The assignment requires at least 30 projection FPS and expects 100 FPS on a 500 x 500 x 500 byte volume. To run the experiment:

```powershell
java -Xms256m -Xmx1g -cp out Benchmark
```

This creates the test volume if needed, loads it, and measures repeated projections after a warmup.

| Plane | Average batch FPS |
| --- | --- |
| Rectangle | 540 |
| Triangle | 782 |
| Oblique plane | 386 |

The slowest batch was 361 FPS on the test computer. These measurements only include projection work, not reading the MRI, saving images, or creating the video. The video's 30 FPS playback setting is separate from this measurement.

The full results and computer details are in [EXPERIMENT.md](EXPERIMENT.md).

## Other documents

- [SRS.md](SRS.md): project requirements and supported features.
- [ALGORITHM.md](ALGORITHM.md): detailed algorithm and design.
- [datasets/README.md](datasets/README.md): datasets used in the project.
- [EXPERIMENT.md](EXPERIMENT.md): tests, measurements, and results.
