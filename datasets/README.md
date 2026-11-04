# Data files

## MRI sample

`MRHead_1.nrrd` in the project root is the supplied MRI. It has 256 x 256 x 130 signed 16-bit voxels, stored as little-endian gzip data. Its voxel values range from 0 to 279.

Its coordinate system is LPS, with:

```text
origin = (86.644897460937486,-133.92860412597656,116.78569793701172)
directions = (0,1,0) (0,0,-1) (-1.2999954223632812,0,0)
```

SHA-256: `e9b54e8855e9024f2238e27224702c1dcb49c64407d937499a3cc42fb1114c8b`.

This file matches the [MRI sample linked in the assignment](https://drive.google.com/file/d/1cqqOgSf8k4e14K5CBs-V_EvMmATX1K-J/view?usp=sharing). On 2026-10-27, the linked file was downloaded and its SHA-256 was compared with the local file. Both had the hash shown above.

## Small test volumes

These are small files with known values, used to check whether the program reads and samples them correctly:

| File | Size and format | Values |
| --- | --- | --- |
| `coded-u16-rotated.nrrd` | 8 x 7 x 6, unsigned 16-bit, little-endian gzip, CRLF header | `x+10*y+100*z` |
| `unsigned-u32-big.nrrd` | 2 x 2 x 2, unsigned 32-bit, big-endian raw | `0,1,2147483647,2147483648,4294967295,65536,255,42` |
| `signed-i16.nrrd` | 2 x 2 x 2, signed 16-bit, ASCII | `-32768,-1,0,1,32767,123,-20,45` |
| `zero-u8.nrrd` | 4 x 4 x 4, unsigned 8-bit, raw | All zero |

The rotated volume has origin `(10,-20,30)` and directions `(0,2,0) (-1,0,0) (0,0,1.5)`. This checks axis order, rotation, and unequal spacing. The other three files have no origin or direction fields. The program uses origin `(0,0,0)` and the normal x, y, z axes for them.

To make these small files again, run this from the project folder after compiling:

```powershell
java -cp out DatasetGenerator
```

The Java tests also make temporary files for the different data types, file formats, and byte orders. Some files are deliberately invalid to check error handling.

## Plane lists

`planes.csv` in the project root contains the original three four-point MRI sections. They are approximately at third-axis voxel indices 65, 66, and 67.

`mixed-planes.csv` contains a four-point section followed by a triangle, using the rotated test volume's world coordinates. They produce different image heights.

`mri-sweep.csv` contains 90 sections at third-axis voxel indices 20 through 109. World X is `86.644897460937486 - 1.2999954223632812*z`; the other coordinates use the same shape as the original plane list.

## Benchmark volume

The benchmark volume has 500 x 500 x 500 unsigned-byte voxels with value `(x+3*y+7*z) & 255`. It contains 125,000,000 bytes after decompression. It uses gzip, zero origin, and identity directions.

`Benchmark` creates `output/benchmark500.nrrd` if it is missing. It can also be generated directly:

```powershell
java -cp out DatasetGenerator --benchmark output/benchmark500.nrrd
```

The large file stays in `output/` and is not uploaded to GitHub. The code that creates it and the speed-test results are in the repository.

## Error recovery demo (added assignment page)

`error-recovery.csv` interleaves the three original MRI sections with an invalid triangle and a crossing four-point region. `error-recovery.mp4` contains five ordered frames at 320 x 180 and 30 FPS, with error labels at CSV lines 2 and 4. Its duration is about 0.167 seconds; pause or step through frames to inspect it.
