# MRI plane projection

This is my Java 8 project for Exercise 1. It takes a 3D MRI and creates 2D images from it. The points for each image come from a CSV file. The images are then joined into an MP4 video.

## Running it

You need JDK 8 and FFmpeg. From the project folder, run:

```powershell
javac -d out src/*.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

`MRHead_1.nrrd` is the MRI file, `planes.csv` contains the plane points, and `projection.mp4` is the video to create.

In IntelliJ, choose JDK 8 and run `Projection` with those three filenames as the program arguments.

If IntelliJ reports a missing JDK, open **File > Project Structure > Project** and select an installed Java 8 SDK. Use **Add JDK from disk** if it is not listed, and select the JDK folder containing `bin/javac.exe`. The project SDK name is `temurin-1.8`; choose your own installed Java 8 SDK if its name differs. Keep the language level at 8.

In **Run > Edit Configurations**, select `Projection`, set the working directory to the project folder, and set the program arguments to `MRHead_1.nrrd planes.csv projection.mp4`. The numbered temporary configurations may have no arguments.

FFmpeg is used to turn the images into a video. Java creates the images, but it does not have a built-in MP4 encoder. FFmpeg must be on PATH, or you can give its location in PowerShell:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

## How it works

Each CSV row contains three or four points. A point is written as `x,y,z`, using the MRI's world coordinates. There is no header row.

The program moves across each plane one row at a time. For every sample, it finds the nearest voxel in the MRI. A voxel is like a pixel in a 3D image. Its value becomes a pixel in the output image. Samples outside the array are zero, which appears black.

The full steps and formulas are in [ALGORITHM.md](ALGORITHM.md).

## Output and tests

[projection.mp4](projection.mp4) has three frames, one for each row in `planes.csv`. It lasts 0.1 seconds at 30 FPS. [demo.mp4](demo.mp4) is a longer example with 90 frames.

The tests use small arrays where the correct values are known. They also check bad input and the video output. The speed test uses a 500 x 500 x 500 byte volume. Its slowest measured batch was about 686 FPS on the test computer, above the assignment's 100 FPS target. This measures projection only.

Commands and results are in [EXPERIMENT.md](EXPERIMENT.md).

For a live demonstration, use `MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4` as the IntelliJ program arguments. This produces 90 frames (3 seconds), which is easier to show than the three-frame example. To demonstrate both supported plane shapes, use `datasets/coded-u16-rotated.nrrd datasets/mixed-planes.csv datasets/mixed-demo.mp4`.

The [assignment review from 2026-10-29](EXPERIMENT.md#assignment-review-2026-10-29) maps the eight deliverables to the project and records the latest test and speed results.

## Q&A: How is the plane p defined?

For three points `a,b,c`, the shape is an isosceles triangle: the lengths from `a` to `b` and from `a` to `c` must differ by less than `0.001`. The points cannot all be on one straight line.

For four points `a1,a2,b,c`:

- The lengths from `a1` to `b` and from `a2` to `c` must differ by less than `0.001`.
- The top edge `a1` to `a2` cannot be longer than the bottom edge `b` to `c`.
- All four points must be on the same plane.

## More details

[SRS.md](SRS.md) lists the requirements. [datasets/README.md](datasets/README.md) describes the input files.
