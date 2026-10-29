# MRI plane projection

This is my Java 8 project for Exercise 1. It takes a 3D MRI and creates 2D images from it. The points for each image come from a CSV file. The images are then joined into an MP4 video.

## Start here for the presentation

Read [PRESENTATION.md](PRESENTATION.md) for a five-minute walkthrough, a small worked example, and questions to practice.

1. Open `Projection.java` in IntelliJ and select the `Projection` run configuration.
2. Use Java 8, the project folder as the working directory, and these program arguments:

   ```text
   MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
   ```

3. Run the program. It should report `90 frames, 100x106, 30 playback FPS`.
4. Open [demo.mp4](demo.mp4). It lasts three seconds; replay it while explaining the changing MRI sections.
5. Explain `Projection.project`, then show the tests and speed results below.

The shorter [projection.mp4](projection.mp4) has only three frames and lasts 0.1 seconds, so it can look like a still image.

## Where to find things

| Location | Purpose |
| --- | --- |
| [src](src) | Java application, data generator and speed test |
| [tests](tests) | Correctness and video tests |
| [datasets](datasets/README.md) | Small test volumes and plane lists |
| [SRS.md](SRS.md) | What the program must do and its limits |
| [ALGORITHM.md](ALGORITHM.md) | Detailed explanation of the calculations |
| [EXPERIMENT.md](EXPERIMENT.md) | Test method, measurements and assignment checklist |
| [experiments](experiments/README.md) | Saved test and benchmark logs |

The root `frame_*.png` files and `projection.png` are older examples. The current program creates its own temporary frames for each video. `out/` holds compiled classes; `output/` holds local generated files and tools.

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

The tests use small arrays where the correct values are known. They also check bad input and the video output. The latest checks passed 1002 Java checks and 24 video/command-line checks.

The speed test uses a 500 x 500 x 500 byte volume. On 2026-10-29, its slowest measured batch was 711.640 FPS on the test computer, above the assignment's 100 FPS target. This measures projection only; the saved video plays at 30 FPS.

Commands and results are in [EXPERIMENT.md](EXPERIMENT.md).

To demonstrate both supported plane shapes, use `datasets/coded-u16-rotated.nrrd datasets/mixed-planes.csv datasets/mixed-demo.mp4` as the IntelliJ program arguments. That small test produces two frames; use it to explain the input and tests rather than as the main video to watch.

The [assignment review from 2026-10-29](EXPERIMENT.md#assignment-review-2026-10-29) maps the eight deliverables to the project and records the latest test and speed results.

## Q&A: How is the plane p defined?

For three points `a,b,c`, the shape is an isosceles triangle: the lengths from `a` to `b` and from `a` to `c` must differ by less than `0.001`. The points cannot all be on one straight line.

For four points `a1,a2,b,c`:

- The lengths from `a1` to `b` and from `a2` to `c` must differ by less than `0.001`.
- The top edge `a1` to `a2` cannot be longer than the bottom edge `b` to `c`.
- All four points must be on the same plane.

## More details

[SRS.md](SRS.md) lists the requirements. [datasets/README.md](datasets/README.md) describes the input files.
