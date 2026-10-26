# MRI plane projection

Exercise 1 for Introduction to Software Engineering.

The program reads a 3D MRI from an NRRD file and takes 2D sections through it. A CSV file gives the points for each section. Each row becomes one frame in the output MP4.

It is written in Java and handles both plane shapes from the assignment: an isosceles triangle and a four-point section.

## Running the program

You need JDK 17 or newer and FFmpeg on PATH. From the project folder, run:

```powershell
javac -d out src/*.java tests/*.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

The arguments are the MRI file, the plane CSV, and the output video. The output folder must already exist.

In IntelliJ, run `Projection` with `MRHead_1.nrrd planes.csv projection.mp4` as the program arguments and the project folder as the working directory.

If FFmpeg is installed somewhere else, set its path before running:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

## Input

The reader supports signed and unsigned 8-, 16-, and 32-bit integers, with raw, gzip, or ASCII data in the NRRD file. It reads the size and spatial information from the header.

The CSV has no header. A row contains either 9 numbers for points `a,b,c`, or 12 numbers for `a1,a2,b,c`. Each point is written as `x,y,z`. These are world coordinates from the MRI, not array indices. The supplied MRI uses LPS coordinates.

The two side lengths must agree within `0.001`. Four-point sections must also lie on the same plane, with the top edge no wider than the bottom. Invalid rows produce an error with the line number.

## How it works

The algorithm moves down the two sides of the section one row at a time. At each row, it samples between the sides and copies the nearest voxel value into the image. Samples outside the MRI are zero.

The images are converted to grayscale and joined into a video at 30 FPS. Smaller images get a black border so every frame has the same size. [ALGORITHM.md](ALGORITHM.md) contains the formulas and rounding choices.

## Examples

[projection.mp4](projection.mp4) uses the original three rows in `planes.csv`, so it only lasts 0.1 seconds. [demo.mp4](demo.mp4) has 90 sections and lasts 3 seconds:

```powershell
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

There is also a [mixed-plane example](datasets/mixed-demo.mp4) using a triangle and a four-point section with different sizes.

## Tests and speed

The tests use small volumes with known voxel values. They check the image values, rotated planes, invalid input, and video output.

```powershell
java -cp out RegressionTests
py tests/integration_test.py
```

The video tests need Python 3 and FFprobe. Use `python3` instead of `py` if needed. The recorded run passed 1002 Java assertions and 24 video/CLI checks; these are individual checks, not 1026 separate test cases.

To measure projection speed:

```powershell
java -Xms256m -Xmx1g -cp out Benchmark
```

On the test computer, a 500 x 500 x 500 byte volume gave mean batch speeds of 540 FPS for a rectangle, 782 FPS for a triangle, and 386 FPS for an oblique section. The slowest batch was 361 FPS, above the assignment's 30 FPS minimum and 100 FPS target. Loading and video encoding are outside the timer.

## Documents

- [SRS.md](SRS.md): requirements and limits.
- [ALGORITHM.md](ALGORITHM.md): algorithm and class structure.
- [datasets/README.md](datasets/README.md): data files and how to generate them.
- [EXPERIMENT.md](EXPERIMENT.md): test results, benchmark settings, and computer details.
