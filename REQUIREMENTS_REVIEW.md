# Requirements review - 2026-11-04

Reviewed source commit `0bd4068` against all four pages of `I2SE_Exe1.docx.pdf`. The implementation and repository deliverables meet the written requirements checked below. Submission readiness remains incomplete until public repository access and external submissions are confirmed.

| Requirement | Evidence and result |
| --- | --- |
| Repository, problem and project scope | README.md describes the goal, inputs, outputs, dependencies, supported formats, and limitations. Git history contains incremental implementation and documentation commits. PASS. |
| SRS | SRS.md defines functional requirements, geometry, performance, failure behavior, and supported scope. PASS. |
| Detailed algorithm and design | ALGORITHM.md explains storage, world/voxel transforms, shape validation, row traversal, image dimensions, rounding, endpoint policy, grayscale, and encoding. Java is one of the explicitly permitted languages; the Python command in the assignment is represented by the equivalent three-filename Java CLI. PASS. |
| NRRD datasets | Supplied MRI, four known-value fixtures, plane lists, descriptions, and reproducible 500-cubed unsigned-byte benchmark generator are present. Integer 8/16/32-bit unsigned ranges are preserved in raw samples. PASS. |
| Testing strategy, including demo | Analytic regression tests and independent video reference comparisons cover geometry, transforms, formats, rounding, invalid input, frame ordering, reruns, and encoder failure. Fresh Java 8 run: 1002 regression checks and 47 end-to-end checks. PASS. |
| Implementation and stability | Source inspection and warning-free Java 8 compilation with `-source 1.8 -target 1.8 -Xlint:all`. File errors, empty input, and encoder failure preserve existing output. No tracked compiled classes or IDE workspace files were found. PASS within documented scope. |
| Ordered three-filename MP4 demo | Projection accepts NRRD, CSV, and MP4 filenames. Fresh real-MRI run produced 90 frames, 100 x 106, 30 FPS, three seconds, and decoded successfully. PASS. |
| Experiment and MP4 outputs | EXPERIMENT.md, benchmark method and recorded measurements, linked videos, and saved verification logs are present. All four existing demos decoded successfully. PASS. |
| 500 x 500 x 500 byte performance | Fresh warmed Java 8 benchmark: all 15 batches exceed 30 FPS mandatory and 100 FPS expected. Slowest batch: 755.018 projection FPS. PASS on this computer. |
| Valid 3/4-point sets and epsilon | Plane validates triangle side equality, four-point side equality, top/bottom length condition, coplanarity, and nondegenerate/noncrossing geometry. Epsilon is 0.001. PASS. |
| Invalid sets raise exceptions | Plane throws validation exceptions; strict CSV reader exposes a line-numbered exception. PASS. |
| Demo inserts error frame and continues | Tests cover invalid triangle/four-point geometry, malformed fields, NaN, leading/trailing errors, blank-line numbering, and all-invalid files. Error-recovery demo contains valid/error/valid/error/valid frames. PASS. |
| Nearest-voxel decisions justified | ALGORITHM.md defines Math.round ties, bounds, ceiling dimensions, fractional final rows, and nearest-voxel rationale. Added-page guidance permits such discretization choices. PASS. |
| Required documentation and supplementary links | Build/run commands, docs, benchmark material and videos are present; every relative file link in root Markdown documents resolves locally. PASS. |
| Public GitHub repository | Anonymous access to https://github.com/tamargord/MRI returned HTTP 404. Authenticated Git access works and the remote master matched the reviewed commit. Public access is NOT VERIFIED; confirm or change visibility in GitHub before submission. |
| Moodle text file | submission.txt contains the supplied ID 314874678 and repository link. File ready; Moodle upload not performed by this agent. |
| Submission form | Exact form link is in SUBMISSION.md. Form submission not performed by this agent. |

## Fresh performance measurements

Command: `java -Xms256m -Xmx1g -cp out Benchmark`, using Temurin 8u504-b01 on Windows 11, 16 logical processors, usable maximum Java heap 954728448 bytes. The documented computer is a Ryzen 9 7940HS with approximately 32 GB RAM.

The unsigned-byte volume is 500 x 500 x 500. Each plane uses 150 warmup projections, then five batches of 300. Timing excludes volume loading, grayscale conversion, PNG saving and video encoding; it includes projection allocation and sampling. FPS is projection throughput, independent of 30 FPS video playback.

| Plane | Size | Mean batch FPS | Minimum batch FPS | Maximum batch FPS |
| --- | --- | --- | --- | --- |
| Rectangle | 500 x 500 | 770.217 | 755.018 | 783.117 |
| Triangle | 500 x 472 | 1539.555 | 1531.286 | 1547.442 |
| Oblique | 500 x 500 | 787.037 | 773.636 | 798.318 |

## Verified video outputs

FFprobe counted frames and reported metadata; FFmpeg fully decoded each file without errors.

| Video | Frames | Size | Playback FPS | Duration (seconds) |
| --- | --- | --- | --- | --- |
| demo.mp4 | 90 | 100 x 106 | 30 | 3.000000 |
| projection.mp4 | 3 | 100 x 106 | 30 | 0.100000 |
| datasets/mixed-demo.mp4 | 2 | 12 x 8 | 30 | 0.066667 |
| datasets/error-recovery.mp4 | 5 | 320 x 180 | 30 | 0.166016 |
| output/audit-mri-2026-11-04.mp4 (fresh, ignored) | 90 | 100 x 106 | 30 | 3.000000 |

No direct 3D Slicer pixel comparison was performed or required by the written assignment. Detached/floating-point NRRDs and automatic LPS/RAS conversion remain outside the documented integer-volume scope. The review does not verify unprovided classroom instructions or Moodle/form submission state.
