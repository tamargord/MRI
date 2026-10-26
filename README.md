# MRI plane projection

This is my project for Exercise 1.

The program is written in Java. It reads a 3D MRI file, creates 2D images of the planes given in a CSV file, and puts them into a video. Each CSV row becomes one video frame.

## How to run

You need Java 17 or newer and FFmpeg installed. Run these commands from the project folder:

```powershell
javac -d out src/*.java
java -cp out Projection MRHead_1.nrrd planes.csv projection.mp4
```

The three arguments are:

- `MRHead_1.nrrd`: the MRI data.
- `planes.csv`: the points that define each plane.
- `projection.mp4`: the output video.

In IntelliJ, run the `Projection` class with those three filenames as the program arguments.

FFmpeg must be on PATH. If it is installed at `C:\ffmpeg\bin\ffmpeg.exe`, you can set it in PowerShell:

```powershell
$env:FFMPEG = 'C:\ffmpeg\bin\ffmpeg.exe'
```

## How it works

The CSV has no header. Each row contains three or four points, with each point written as `x,y,z`. The points use the MRI's world coordinates.

The program goes through each plane row by row. It takes the value of the nearest MRI voxel and uses it as a pixel in the image. Points outside the MRI get a value of zero. FFmpeg joins the grayscale images into a video.

The full algorithm is in [ALGORITHM.md](ALGORITHM.md).

## Results

[projection.mp4](projection.mp4) has three frames because `planes.csv` has three rows. At 30 FPS, it lasts 0.1 seconds. [demo.mp4](demo.mp4) is a longer example with 90 frames and lasts 3 seconds.

The tests check known image values, different plane directions, invalid input, and video output. In the speed test on a 500 x 500 x 500 byte volume, the slowest measured batch was 361 FPS. Loading the MRI and making the video were not included in that timing.

Test commands and full results are in [EXPERIMENT.md](EXPERIMENT.md).

## Q&A

### How is the plane p defined?

A plane is given by three or four 3D points.

With three points `a,b,c`, the shape is an isosceles triangle. The distances from `a` to `b` and from `a` to `c` must differ by less than `0.001`. The points must not all be on one straight line.

With four points `a1,a2,b,c`:

- The lengths `a1` to `b` and `a2` to `c` must differ by less than `0.001`.
- The top edge `a1` to `a2` must be no longer than the bottom edge `b` to `c`.
- All four points must be on the same plane.

These points define the part of the plane used to create the image.

## Other files

- [SRS.md](SRS.md): project requirements.
- [ALGORITHM.md](ALGORITHM.md): detailed explanation of the algorithm.
- [datasets/README.md](datasets/README.md): information about the data files.
- [EXPERIMENT.md](EXPERIMENT.md): tests and performance results.
