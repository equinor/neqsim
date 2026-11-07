# Prescribed-pressure finite-aquifer precision qualification

Synthetic analytical benchmark; no measured-data calibration or holdout is represented.
The independent standard-library Python Decimal reference solves the storage ODE using
an affine particular solution plus an exponential. It locates interior exchange extrema
by 100 derivative-sign bisections, independently of the native crossing-time formula.
Inputs use reservoir m3, bara, seconds, compressibility in 1/bar and productivity
in reservoir m3/(s bar). Initial aquifer pressure is 300 bara and initial exchange is zero.
These original synthetic inputs and scripts are distributed under the repository license.

## Frozen design and tolerance

`grid.csv` has 776 cases: 756 finite cases and 20 explicit zero-productivity,
zero-storage and infinite-storage limits. `boundaries.csv` has 668 cases: 648 finite
cases bracketing both arithmetic boundaries plus the same 20 limits.
Each case compares endpoint pressure, endpoint water exchange and minimum/maximum
exchange against one whole interval and 16 equal subintervals: 11,552 comparisons
and 24,548 native advance calls. The initial checkpoint must remain unchanged.
The pressure tolerance is 1e-9 bara. Each water tolerance is
1e-8 m3 + 2e-12 * max(1, absolute reference water volume in m3).
Inputs, tolerances and arithmetic scope were frozen before comparing implementations.

The 60-digit and 75-digit generators reproduce both 36-significant-digit CSV files
byte-for-byte. Zero is canonically serialized as `0`; no zero exponent is meaningful.
Run `reference.py --precision 75 --output /tmp/aquifer-reference-75` and compare
both files to this directory. The generator uses decimal representations of the
explicit CSV inputs, including the fixed binary64-generated time schedule.

## Before and after

Base: `de81c1f81c226988a2326c27ed1873094ed86e11`.
The existing 11 aquifer tests pass on unmodified master. The two new tests fail
on that source with 16 failed water comparisons across eight matrix rows (four
physical cases repeated at the shared boundary). Maximum normalized error is
1.080983076 of the unchanged tolerance. `baseline/` retains every native result.

The candidate changes only the existing fourth-order series selection threshold
from 1e-4 to 1e-3, in the endpoint and interior-extremum calculations. No coefficient,
physical parameter, public API or EOS solver changes. At x < 1e-3 the first omitted
decay coefficient is bounded by x^5/720 < 1.4e-18, and the ramp coefficient by
x^5/5040 < 2.0e-19. This avoids cancellation in 1 - (1 - exp(-x))/x.
All 11,552 comparisons pass; maximum normalized error is 0.472502534.
`candidate/` and `numerical-results.json` retain all outputs and binary64 comparison maxima.
`compare.py` audits the same outputs with 60-digit Decimal comparisons; complete
failures and unrounded normalized maxima are retained in `decimal-results.json`.
An absolute maximum alone is not a pass criterion because water magnitudes vary.

Run the Java regression with Maven's test selector `FetkovichAquifer*Test`.
It writes complete new outputs under `target/aquifer-precision` before asserting
any accumulated numerical failures. Compare them with `candidate/`.

## Runtime control

`AquiferTiming.java` is an isolated reproducible kernel diagnostic. Compile it with
FetkovichAquifer using Java's compiler and `--release 8`; run each build in a fresh
JVM with output path and iteration count `1000000`. It warms 200,000 calls before
timing mixed pressure ramps at six exposures, with an observable checksum.
`timings.json` preserves three ABBA blocks (12 samples) on OpenJDK 17.0.20.
Baseline median: 169,672,988 ns; candidate median: 156,886,512 ns; ratio 0.92464.
Baseline range: 151,772,093–175,687,761 ns; candidate range:
138,354,579–176,092,556 ns. This is a noisy isolated timing control, not evidence
of end-to-end speedup.

## Executed repository checks

`validation.json` records command exit codes, versions, source hashes, coverage
and remaining acceptance. `qualification.log` retains the baseline, failing
regression, candidate, PVT, compatibility and gate outputs. Java 8 compatibility
checks use the Java 8 POM and release-8 compilation on JRE17; actual Java 8 JVM
and the full repository matrix remain hosted-CI checks. The Maven Javadoc goal
cannot locate a standalone launcher here; strict private doclint runs successfully
through the installed JDK javadoc module. Initial incomplete-materialization and
inventory-staging failures are recorded explicitly in the receipt.

## Scope and remaining acceptance

This qualifies a prescribed linear reservoir-pressure path for a lumped,
constant-property, reversible reservoir-volume storage law. It does not establish
radial-transient equivalence, Carter-Tracy/VEH accuracy away from common limits,
water mixing, continuous Bayesian inference, coupled-EOS timestep convergence or
measured laboratory-to-field accuracy. Those acceptance items remain in #4740.
The existing coupled-history refinement tests remain required.
