# Software Requirements Specification

Version: 1.0 draft, 2026-10-26. Project: MRI plane projection, Introduction to Software Engineering Exercise 1.

## Purpose and scope

Provide a command-line program that loads a 3D raster volume from an NRRD file, samples a sequence of planar sections described in a CSV file, and produces an MP4 containing one frame per plane in input order. Java is the chosen implementation language. Intended users are students and reviewers reproducing the assignment demonstration and assessing correctness, design, and efficiency.

This specification distinguishes required behavior from the current implementation. The existing code is a dataset-specific prototype. It does not yet satisfy every requirement below. The [README](README.md) documents its algorithm, supported example, results, and limitations.

## Terms and conventions

- **Volume:** a scalar 3D array `M[x][y][z]`; each dimension is at least 1 and less than 1024.
- **World coordinates:** spatial coordinates defined by NRRD origin and space-direction vectors. The supplied MRI uses LPS.
- **Voxel coordinates:** continuous array indices before nearest-neighbor rounding.
- **Plane:** the triangular or four-point bounded sampling region described by one CSV row.
- **Projection:** a sampled planar section, not an intensity integration through the volume.
- **EPS:** absolute tolerance `0.001` for side-length equality and the current point-to-plane distance check.
- **Projection FPS:** completed raw 2D projections per second, excluding loading, PNG saving, and encoding.

The assignment allows unsigned 8-, 16-, and 32-bit volume values. The supplied NRRD declares signed 16-bit values; all observed values are nonnegative. Support for this sample does not demonstrate support for other scalar types. A general implementation must define signed-input treatment explicitly and preserve the full unsigned range of each supported type.

## Functional requirements

| ID | Requirement | Acceptance evidence | Current status |
| --- | --- | --- | --- |
| FR-01 | Accept three positional arguments: input NRRD path, input CSV path, output MP4 path. | A documented Java invocation completes; missing or extra arguments produce a clear usage error. | Three paths used; argument-count validation missing |
| FR-02 | Load scalar 3D NRRD data with dimensions in the assignment range. Read dimensions, scalar type, byte order, encoding, origin, and space directions from metadata. Clearly reject unsupported variants. | Known-valued input volumes with differing dimensions and transforms load correctly; unsupported and malformed files fail explicitly. | Attached gzip, little-endian 16-bit sample only; metadata skipped |
| FR-03 | Preserve unsigned scalar values for supported 8-, 16-, and 32-bit inputs. Define and document treatment of signed NRRD types. | Boundary-value fixtures retain their intended numeric values without overflow or reinterpretation. | Unsigned 16-bit interpretation only; signed values would be reinterpreted |
| FR-04 | Read one plane per nonempty CSV row, with 9 values for `a,b,c` or 12 for `a1,a2,b,c`. Preserve row order and reject missing, nonnumeric, or nonfinite coordinates. | Valid files parse in order; invalid rows identify their location and cause. | Basic numeric parsing and field count exist; comprehensive validation missing |
| FR-05 | Accept a nondegenerate isosceles triangle when `abs(distance(a,b)-distance(a,c)) < EPS`. Reject a zero-area triangle. | Valid triangle accepted; unequal-leg, coincident, and collinear examples rejected. | Side equality checked; degeneracy validation incomplete |
| FR-06 | Accept four-point regions satisfying equal side lengths within EPS, top-edge length no greater than bottom-edge length, and coplanarity. Reject undefined plane geometry. | Valid regions accepted; unequal legs, invalid edge ordering, noncoplanar, and degenerate fixtures rejected. | Several checks implemented; broader geometry cases unverified |
| FR-07 | Convert world positions using the inverse of the input NRRD spatial transform. Sample by nearest-neighbor rounding and return zero for indices outside the volume. | Known samples and affine round trips agree with a header-derived independent reference, including rotated axes and boundaries. | Supplied transform verified; general transform missing |
| FR-08 | Produce a centered triangle or four-point raw intensity image using the assignment's side-traversal algorithm, with explicitly documented dimension, rounding, and endpoint policies. | Synthetic fixtures cover integer/fractional lengths, narrow spans, rotated planes, and out-of-volume sections. | Methods implemented; supplied four-point example verified only |
| FR-09 | Convert raw projections to 8-bit grayscale for display; leave an all-zero image black. Document the chosen display scale. | Known values normalize correctly, including zero and maximum cases. | Per-frame maximum scaling implemented |
| FR-10 | Encode exactly one video frame per nonempty input row in the same order. Define a consistent video canvas when plane dimensions differ. | Decode video and verify frame count, sequence, common dimensions, and expected content for mixed inputs. | Fixed numbered PNG sequence; mixed dimensions and stale-frame handling unresolved |
| FR-11 | Produce the requested MP4 and surface file-access or encoder failures clearly. | Valid output can be decoded; unreadable inputs, unwritable outputs, and missing or failed FFmpeg produce actionable errors. | Encoder exit status checked; portability and error reporting incomplete |
| FR-12 | Report projection timing separately from image export and video encoding. | Timing boundaries are documented and benchmark results record their configuration. | Basic timers and warmup benchmarks implemented; required experiment pending |

The four-point constraints above reproduce the assignment's explicit criteria. Those criteria do not by themselves fully specify every conventional trapezoid property, such as parallel bases and absence of crossing edges. The implementation must document the accepted geometry and reject cases for which row traversal is undefined; additional restrictions require an explicit design decision.

## Nonfunctional requirements

| ID | Requirement | Acceptance criterion |
| --- | --- | --- |
| NFR-01 | Correctness | Independent reference comparisons pass for axis-aligned and oblique triangle and four-point sections under the declared sampling policies. |
| NFR-02 | Required performance | On a 500 x 500 x 500 byte volume, warmed projection throughput exceeds 30 FPS; at least 100 FPS is the assignment's expected target. Record hardware, JVM, plane/output dimensions, iteration counts, and timing boundaries. |
| NFR-03 | Robustness | Malformed inputs fail clearly without being reported as successful output. Resources are released on success and failure. |
| NFR-04 | Reproducibility | Input dataset, coordinate convention, build/run commands, dependencies, testing method, and experiment results are documented. |
| NFR-05 | Maintainability | Responsibilities for volume loading, geometry/sampling, input parsing, rendering, and encoding are explicit and can be tested independently. |
| NFR-06 | Portability | Dependencies and supported environments are documented; dataset metadata and machine-specific executable paths are configurable rather than embedded assumptions. |

The performance requirement concerns projection throughput. A 30 FPS encoder setting alone cannot satisfy NFR-02. Output size materially affects throughput and must accompany every performance claim.

## Design policies and unresolved decisions

The current design uses `ceil(sideLength)` output rows, `ceil(bottomEdgeLength)` columns, a one-world-unit traversal step, Java `Math.round` nearest-neighbor sampling and centering, zero fill outside the volume, and independent maximum normalization for each frame. These are documented implementation choices, rather than additional assignment requirements. Independent normalization can change brightness between adjacent frames; a fixed display window is an optional future design choice.

Resolve and test the following before claiming full algorithm acceptance:

1. The code iterates across rows through `round(span)`, while the assignment pseudocode compares the integer loop counter directly to the floating-point span.
2. The triangle method may step beyond the final side endpoint when its length is fractional; the four-point method clamps the last step.
3. A zero-width four-point top edge currently fails the plane-normal check. Decide whether this representation should be accepted as a triangle or rejected explicitly.
4. Define accepted NRRD encodings and scalar types, signed-value treatment, and a safe representation for unsigned 32-bit samples.
5. Define padding/alignment for different plane sizes and isolate each run's numbered images.

## Verification and acceptance plan

Use small synthetic volumes to establish exact expected values and axis orientation, then use the supplied MRI to demonstrate realistic images. Add an independent NRRD with different dimensions and spatial metadata. Exercise triangle and four-point planes, oblique sections, boundary cases, malformed inputs, and encoder failures. The [README testing strategy](README.md#testing-strategy) lists concrete cases.

The current evidence is limited to three 100 x 105 PNG images from adjacent four-point sections of `MRHead_1.nrrd`. A header-derived reference calculation matched all 10,500 grayscale pixels in each image. FFprobe reported the existing MP4 as three frames at 30 FPS, 100 x 106, lasting 0.1 seconds. General geometry tests, MP4 decoded-content checks, and the required 500-cubed performance experiment remain pending.

Submission acceptance requires the documented scope and algorithm, requirements specification, defined dataset, executed test strategy, runnable three-argument demo, and a short experiment report with MP4 output and recorded performance evidence. Existing sample success alone does not establish completion of these criteria.
