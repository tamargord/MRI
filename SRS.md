# Software Requirements Specification

Version 1.0, 2026-10-26. Introduction to Software Engineering, Exercise 1.

## Purpose and scope

Provide a Java command-line program that loads a scalar 3D NRRD volume, samples bounded planes from a CSV file, and writes an MP4 with one frame per plane in input order. The intended users are students and reviewers evaluating algorithm design, correctness, stability, and runtime efficiency.

This is an educational planar sampling tool. A projection is a sampled section, rather than an accumulation along viewing rays. Scope is the integer NRRD subset below; support for every optional NRRD feature is not claimed.

## Definitions and input conventions

- Dimensions: `1 <= sx,sy,sz < 1024`, subject to available memory.
- Scalars: unsigned 8-, 16-, and 32-bit values required by the assignment; signed variants additionally supported for the supplied signed-short MRI.
- Coordinates: finite world coordinates expressed in the input NRRD coordinate system. The MRI example uses LPS. No implicit RAS/LPS conversion occurs.
- Plane: three points `a,b,c`, or four points `a1,a2,b,c`, in that order.
- EPS: absolute side-equality and point-to-plane tolerance `0.001` world units.
- Projection FPS: raw section computations per second, excluding loading, display conversion, and video encoding.

## Functional requirements and verification

| ID | Requirement | Verification and status |
| --- | --- | --- |
| FR-01 | Accept exactly three positional paths: NRRD, CSV, MP4. Reject invalid argument counts with usage information and nonzero status. | Implemented; CLI integration tests pass. |
| FR-02 | Read attached scalar 3D NRRDs, using declared sizes, integer type, endian, encoding, origin, and spatial directions. Support raw/gzip/ASCII and LF/CRLF. Clearly reject unsupported variants. | Implemented; synthetic loader and transform checks pass; supplied MRI demo runs. |
| FR-03 | Preserve full unsigned 8/16/32 ranges and supported signed values in raw projections. | Implemented using compact scalar storage and long output; boundary fixtures pass for all widths and encodings. |
| FR-04 | Read one plane per nonempty CSV row, preserve order, and reject incorrect counts, empty fields, nonnumeric or nonfinite coordinates with row-specific errors. | Implemented; parser and CLI checks pass. |
| FR-05 | Validate isosceles triangles: positive sides, side-length difference less than EPS, and nonzero area. | Implemented; analytic and invalid-geometry checks pass. |
| FR-06 | Validate four-point sections: equal traversing sides within EPS, top no wider than bottom, coplanarity, and a nondegenerate simple region. Accept coincident top points as a triangle. | Implemented; valid, crossing, wide-top, degenerate, and noncoplanar fixtures pass. |
| FR-07 | Transform world positions using the inverse metadata affine transform; sample nearest neighbors and fill out-of-volume positions with zero. | Implemented; axis-aligned, rotated, anisotropic, nonorthogonal, boundary, and external-section checks pass. |
| FR-08 | Rasterize rows according to the assignment's side traversal and centering algorithm, with explicit rounding and endpoint policies. | Implemented; analytic rectangle/triangle/oblique and fractional-edge tests pass; policies defined below. |
| FR-09 | Export grayscale display values without losing the raw scalar values. All-zero images must remain black. | Implemented; zero, negative, and unsigned-32 display checks pass. |
| FR-10 | Encode exactly one frame per nonempty input row, in order, with a consistent canvas when dimensions differ. Exclude files from previous runs. | Implemented; decoded mixed-frame and shorter-rerun integration checks pass. |
| FR-11 | Write the requested MP4 and report access/encoder errors with nonzero status. Preserve an existing output if input validation or encoding fails. | Implemented; missing/misconfigured encoder and malformed-input integration checks pass. |
| FR-12 | Provide projection-only performance measurements separately from demo loading, rendering, and encoding. | Implemented in Benchmark; completed experiment and raw log are included. |

## Nonfunctional requirements

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| NFR-01 | Correctness and stability | 1002 Java regression checks and 24 end-to-end checks pass on the recorded environment. Exact analytic comparisons cover raw sampling; lossy video comparisons use declared tolerance. |
| NFR-02 | Performance | On a 500-cubed byte volume, at least 30 projection FPS is mandatory and 100 FPS is expected. All five batches for each of three tested planes exceed 100 FPS; lowest observed batch is 361 FPS. |
| NFR-03 | Reproducibility | README documents dependencies and commands; source fixtures and their generator are committed; experiment records inputs, hardware, timing boundaries, and raw results. |
| NFR-04 | Maintainability | Volume loading, geometry validation, input parsing, sampling, and experiment generation have separate class responsibilities. Immutable finite points prevent invalid coordinate state. |
| NFR-05 | Portability | JDK 17+ and FFmpeg are required. FFmpeg is found on PATH or configured with FFMPEG. No MRI dimensions or spatial metadata are machine-specific constants. Runtime verification was on Windows; other platforms are not claimed as tested. |
| NFR-06 | Resource management | Streams and temporary files are closed/removed after normal success or handled failure. Process interruption destroys the encoder. Individual images and video canvas are limited to 16 million pixels; volume loading remains bounded by available heap. |

## Detailed design policies

These choices resolve ambiguities in the assignment rather than silently changing them:

1. Dimensions are `height=ceil(firstSideLength)` and `width=ceil(bottomLength)`, stored as `E[y][x]`.
2. Sampling starts after the first one-world-unit side step. Both side endpoints clamp at their final point, so the last fractional row stays within the supplied region.
3. Across a row, integer `k` satisfies `k <= span`, allowing `1e-9` numerical tolerance at an integer endpoint. A fractional span is not rounded upward.
4. Output centering and nearest-neighbor coordinates use Java Math.round. Out-of-range rounded indices return zero.
5. A collapsed row samples one centered pixel. A repeated top point uses the triangle behavior. Collinear points and crossing traversing sides are invalid.
6. The assignment's explicit four-point constraints do not require parallel bases; no such extra restriction is imposed on otherwise valid simple sections.
7. Missing origin defaults to zero. Missing directions use identity axes, scaled by spacings when provided. The CSV contract follows that explicitly chosen coordinate frame.
8. Signed scalar values remain signed in raw output. Display clamps negatives to black, then independently normalizes each frame by its nonnegative maximum.
9. The common video canvas uses maximum section dimensions, rounded up to even numbers, with smaller images centered on black. H.264 CRF 18 and 30 playback FPS are display/export choices.
10. Supported integer encodings are attached raw, gzip/gz, and ASCII/text/txt. Detached data, nonzero byte/line skips, nonspatial axes, unsupported scalar types, and singular spatial transforms fail explicitly. No universal NRRD compliance is claimed.

The full algorithm and class responsibilities are documented in [README.md](README.md#detailed-algorithm).

## Dataset and verification strategy

The [dataset catalog](datasets/README.md) defines the provided MRI, synthetic scalar/coordinate fixtures, plane sequences, and the reproducible 500-cubed benchmark dataset. Tests establish scalar decoding and transforms independently, check analytic raw section values, then decode end-to-end video output to assess count, dimensions, order, content, and failure behavior.

Execution commands and results are in [EXPERIMENT.md](EXPERIMENT.md); raw logs are in [experiments](experiments/README.md). A direct 3D Slicer visual comparison was not performed. Analytic and independently computed references supply the correctness evidence instead.

## Assignment deliverables

| Assignment item | Repository evidence |
| --- | --- |
| Repository, problem definition, and scope | GitHub repository and README |
| SRS | This specification |
| Detailed main algorithm | README algorithm and explicit policies |
| Defined NRRD dataset | Dataset catalog, fixtures, and generator |
| Testing strategy including demo | README, Java regression suite, Python integration suite |
| Implementation with frequent commits | Java sources and repository history |
| Three-path command-line demo | Projection main; MRI and mixed-plane MP4 outputs |
| Experiment description with output video | EXPERIMENT.md, benchmark log, projection.mp4, demo.mp4 |
