# Dataset catalog

All small synthetic fixtures are committed and can be regenerated with:

```powershell
java -cp out DatasetGenerator
```

| File | Data and purpose |
| --- | --- |
| `../MRHead_1.nrrd` | Supplied local MRI, 256 x 256 x 130 signed 16-bit samples, gzip, little endian; observed values 0..279. Origin `(86.644897460937486,-133.92860412597656,116.78569793701172)`; directions `(0,1,0) (0,0,-1) (-1.2999954223632812,0,0)`; LPS coordinates. |
| `coded-u16-rotated.nrrd` | Generated 8 x 7 x 6 unsigned 16-bit gzip fixture, little endian, CRLF header. Value `x+10*y+100*z`. Origin `(10,-20,30)`; directions `(0,2,0) (-1,0,0) (0,0,1.5)`. Tests axis order, anisotropy, rotation, and coordinate inversion. |
| `unsigned-u32-big.nrrd` | Generated 2 x 2 x 2 unsigned 32-bit raw big-endian fixture. Ordered values: `0,1,2147483647,2147483648,4294967295,65536,255,42`. Tests the full unsigned range. |
| `signed-i16.nrrd` | Generated 2 x 2 x 2 signed 16-bit ASCII fixture. Ordered values: `-32768,-1,0,1,32767,123,-20,45`. Tests signed interpretation and ASCII decoding. |
| `zero-u8.nrrd` | Generated 4 x 4 x 4 all-zero unsigned-byte raw fixture. Tests zero sampling and black display behavior. |
| `../planes.csv` | Supplied three adjacent four-point MRI sections, approximately voxel-axis Z indices 65, 66, 67. |
| `mixed-planes.csv` | Generated four-point section followed by a triangle in the rotated fixture's world coordinates. Different output heights test common-canvas video generation and input order. |
| `mri-sweep.csv` | 90 MRI sections at voxel-axis Z indices 20 through 109. Each row uses world X `86.644897460937486 - 1.2999954223632812*z`; the other coordinates retain the supplied plane shape. |

For absent spatial fields, the project uses zero origin and identity directions. This applies to the scalar-limit and zero fixtures.

The supplied MRI SHA-256 is `e9b54e8855e9024f2238e27224702c1dcb49c64407d937499a3cc42fb1114c8b`. It was present in the original project; its exact download provenance has not been independently established. The assignment directs students toward 3D Slicer data. Synthetic fixtures are generated entirely by this project and do not require a download.

## Performance dataset

`Benchmark` generates `output/benchmark500.nrrd` when run without an argument and that file does not yet exist. It is an attached gzip NRRD with unsigned 8-bit samples, 500 x 500 x 500 dimensions, and values `(x+3*y+7*z) & 255`. The decompressed volume contains exactly 125,000,000 samples/bytes. Origin and directions are omitted, giving the documented identity coordinate convention.

Generation and loading happen before timing. The generated large volume is ignored by Git; its generator and raw benchmark results are committed. To generate it explicitly:

```powershell
java -cp out DatasetGenerator --benchmark output/benchmark500.nrrd
```

The regression suite generates additional temporary fixtures for every signed/unsigned width, each supported encoding, and both byte orders. It also creates malformed/truncated files and removes these temporary fixtures on completion.
