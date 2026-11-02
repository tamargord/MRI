# MRI Plane Projection — Exercise 1

This is my Java 8 solution for Exercise 1. The goal is to take a 3D MRI volume and create 2D images of sections through it. The program reads the MRI from a NRRD file and the points defining each section from a CSV file. It then creates an MP4 video with one frame for each CSV row, in the same order.

I split the program into separate classes for reading files, checking the planes, sampling the MRI, and creating the output. This made it easier to test each part with small examples before using the real MRI.

## How it works

An MRI volume is a three-dimensional grid of values. Each cell is called a voxel, similar to a pixel in a 2D image. The program samples these values along a plane to build an image.

1. Read the volume dimensions, voxel values, origin, and directions from the NRRD file.
2. Read each CSV row and check that its points define a valid shape.
3. Move down the two sides of the shape, building the image one row at a time.
4. Convert each sample from world coordinates to voxel coordinates and read the nearest voxel. A rounded position outside the volume gives zero.
5. Convert the image to grayscale and use FFmpeg to join the frames into a video at 30 FPS.

Here, “projection” means the section produced by the assignment's sampling algorithm. Each sample reads one nearest voxel; values are not added along a viewing ray. The formulas, image dimensions, and rounding choices are explained in [ALGORITHM.md](ALGORITHM.md).

## Running it

The program needs **JDK 8** and **FFmpeg**. FFmpeg must be available on `PATH`, or its executable path can be set in the `FFMPEG` environment variable.

From the project folder, compile and run:

```powershell
javac -d out src/*.java
java -cp out Projection MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

The three arguments are the MRI filename, the CSV filename, and the output MP4 filename. A successful run with this example creates 90 frames at 100 × 106 pixels. The video plays for three seconds at 30 FPS.

To set the FFmpeg location in PowerShell, for example:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

In IntelliJ, select an installed Java 8 SDK and run `Projection` with the project folder as the working directory. Use these program arguments:

```text
MRHead_1.nrrd datasets/mri-sweep.csv demo.mp4
```

If IntelliJ reports a missing JDK, select your Java 8 installation under **File > Project Structure > Project**. The project SDK is named `temurin-1.8`, but another installed Java 8 SDK can be selected. Keep the language level at 8. FFmpeg must also be available to the IntelliJ process through `PATH` or `FFMPEG`.

## Input format

The CSV has no header. Each nonempty row contains either nine numbers for three points or twelve numbers for four points. Each point is written as `x,y,z`, using the MRI's world coordinate system.

- **Three points, `a,b,c`:** an isosceles triangle. The two sides from `a` must differ in length by less than `0.001`, and the points must not be on one straight line.
- **Four points, `a1,a2,b,c`:** a section with sides `a1` to `b` and `a2` to `c` equal within the same tolerance. The points must be coplanar, the top edge cannot be longer than the bottom edge, and crossing or zero-length sides are rejected. The two top points may be the same, which also lets the algorithm handle a triangle.

Invalid rows are reported with their line number. The supplied [datasets](datasets/README.md) include both plane shapes and small volumes with known values.

## Example output

| Video | Input plane list | Frames | Duration at 30 FPS |
| --- | --- | --- | --- |
| [demo.mp4](demo.mp4) | `datasets/mri-sweep.csv` | 90 | 3 seconds |
| [projection.mp4](projection.mp4) | `planes.csv` | 3 | 0.1 seconds |
| [mixed-demo.mp4](datasets/mixed-demo.mp4) | `datasets/mixed-planes.csv` | 2 | About 0.067 seconds |

`demo.mp4` shows a sweep through adjacent sections of the real MRI. The other two videos are very short, so they can look like still images during playback. The mixed example uses `datasets/coded-u16-rotated.nrrd` and contains a four-point section followed by a triangle.

## Testing and results

I used small volumes with known values to check the raw projection results. The tests cover coordinate conversion, rotated axes, both plane shapes, rounding, out-of-bounds samples, and invalid input. A separate Java integration test checks the running Java program and the encoded video, including frame count, order, image content, and failed runs. All application and test code is Java 8, with no third-party Java libraries.

The recorded Java 8 run on **2 November 2026** passed **1002 regression checks** and **24 video/command-line checks** using the Java test suites. These are individual checks, including repeated pixel and file-format checks, rather than 1002 separate test cases.

To run the checks with JDK 8, FFmpeg, and FFprobe available:

```powershell
javac -d out src/*.java tests/*.java
java -cp out RegressionTests
java -cp out IntegrationTests
```

The speed test uses a **500 × 500 × 500 unsigned-byte volume**. After warmup, it measures repeated projections of a rectangle, a triangle, and an oblique section. On the test computer (Windows 11, Ryzen 9 7940HS, about 32 GB RAM), the slowest measured batch in the final check on 29 October 2026 was **735.346 projection FPS**, above the assignment's 30 FPS minimum and 100 FPS target.

```powershell
java -Xms256m -Xmx1g -cp out Benchmark
```

This measures projection only. File loading, grayscale conversion, PNG saving, and MP4 encoding are excluded. The saved video still plays at 30 FPS. The method, full results, and saved logs are in [EXPERIMENT.md](EXPERIMENT.md).

## Scope and limitations

The reader supports scalar 3D NRRD volumes with signed or unsigned 8-, 16-, or 32-bit integer values, using raw, gzip, or ASCII data stored in the same file as the header. Floating-point volumes and detached data files are not supported. The volume must fit in Java memory, and each projection or video frame is limited to 16 million pixels.

CSV coordinates must use the MRI's coordinate system; the program does not automatically convert between LPS and RAS. Grayscale is scaled separately for each frame, so brightness can change between frames. Negative values remain in the raw image array but appear black in the video.

Correctness was checked using known-value test volumes and video tests. No direct pixel comparison with 3D Slicer was made.

## Project files

| Location | Contents |
| --- | --- |
| [src](src) | Java source code, dataset generator, and benchmark |
| [tests](tests) | Java regression and video integration tests |
| [datasets](datasets/README.md) | Test volumes and plane lists, with descriptions |
| [SRS.md](SRS.md) | Requirements and supported scope |
| [ALGORITHM.md](ALGORITHM.md) | Detailed algorithm and implementation choices |
| [EXPERIMENT.md](EXPERIMENT.md) | Test method, measurements, and assignment review |
| [experiments](experiments/README.md) | Saved verification and benchmark logs |
| [PRESENTATION.md](PRESENTATION.md) | Presentation notes and a worked example |

The program creates temporary frame images for each run and removes them afterward. The root `frame_*.png` files and `projection.png` are older examples. Compiled classes go in `out/`, and local generated files go in `output/`.
