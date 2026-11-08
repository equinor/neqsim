# Infinite radial influence qualification (issue #4740, Run63)

Current numerical source includes **Run65 deferred initialization** with output-bit
identity to Run64. Current evidence is in [initialization65/README.md](initialization65/README.md);
[optimization64/README.md](optimization64/README.md) retains the positive-tail correction.
The remainder of this file and `validation.json` retain the historical Run63
receipt at exact head `04e78817a608b59aa5bd0bbe846b406ff18729c9`; its runtime and
analytic high-frequency-tail description are superseded by Run64. Original
outputs and failures are preserved for comparison.

Base: `97c03ecf50d16a6dab7bd812364f49d5f4c5c444`.
This batch repairs the infinite-acting constant-terminal-rate influence function
and its derivative. It is independent mathematical qualification, not measured
PVT/field accuracy, a finite-annulus solution, an EOS fit or recoverable reserves.
The finite-radius maximum policy remains explicitly unqualified. Its shared
infinite branch and active-branch derivative change in this batch.

## Model, primary source and provenance

Dimensionless radial diffusion has initially zero pressure perturbation,
inner radius one, unit withdrawal rate (`dp/dr=-1` at the inner boundary),
and pressure tending to zero at infinite radius. Time and pressure are
unitless. The primary source is A. F. van Everdingen and W. Hurst (1949),
*The Application of the Laplace Transformation to Flow Problems in Reservoirs*,
Petroleum Transactions AIME, pp.305–324, DOI10.2118/949305-G.
[University-hosted primary scan](https://blasingame.engr.tamu.edu/z_zCourse_Archive/P620_12C/P620_12C_Final_Exam/P620_12C_Prob_01_ref_AIME_Trans_TP_2732_%28VEH%29_%28pdf%29.pdf)
was retrieved 8 November 2026: 1,339,103 bytes,
SHA256 `322a7846582e037878c5b5891ce5e1982d9f2a879d130e41f7b74313101a6020`.
EquationsVI-4/VI-7/VI-24 and printed page312 were checked, including visual
inspection of the Bessel integral. No scan, paper text or tabulated third-party
experimental data is redistributed. All retained numerical outputs here are
new calculations of the stated mathematical model. Public accessibility does
not establish permission to redistribute the paper.

The independent generator inverts the Laplace transform
`K0(sqrt(s))/(s*sqrt(s)*K1(sqrt(s)))` for pressure, and separately inverts
`s*Pbar` for its time derivative. The production kernel instead integrates the
positive spectrum (VI-24), `4/pi^2*(1-exp(-t*u^2))/(u^3*(J1^2+Y1^2))`.
The derivative integrates the same spectral measure analytically.
No fluid, separator, sample, standard-volume basis or adjustable field
parameter enters either calculation.

The kernel uses 712 positive logarithmic Gauss nodes on `[exp(-40),64]`, 128 positive
nodes for the low-frequency entire exponential integral, and five analytic
large-frequency asymptotic terms. For the low-frequency continuation above
argument 50 the omitted exponential integral is about 4e-24; the logarithm is
anchored to the same quadrature value. This preserves adjacent-double
monotonicity. The positive Gauss integral's analytic error bound for Ein on
`[0,50]` is 4.130e-15 (half for the pressure remainder), before floating-point
roundoff. At the upper tail's exponential argument above 676 its negligible
exponential remainder is omitted to avoid overflow times zero. There is no
pressure-history/EOS fitting or empirical switch at the old times .01/200.

## Frozen acceptance and complete outputs

The scope/tolerances were frozen before candidate comparison:

- Pressure: absolute 1e-7 plus relative 1e-6 against the independent inversion.
- Derivative: absolute 1e-6 plus relative 1e-5 against its separate inversion.
- Independent 25/35 digit repeat: absolute 1e-9 plus relative 1e-9; finite/positive.
- Strict adjacent-double nondecrease at old and numerical branch boundaries;
  no post-failure tolerance change. Positive monotone logarithmic time grid,
  nonvacuous scaled subnormal/maximum-time limits, analytic-derivative consistency.
- CT's six meshes must each reduce error against its limiting ODE; finest
  relative error<=.02. ODE-start refinement <=1e-8+1e-7*abs(reference).
  Native time identity uses 1e-13 relative, including every interior node.

`reference.json` retains all 20 times/40 outputs/80 inverse transforms at 25/35 digits
(mpmath 1.3.0, 109.073 s). It includes old branch boundaries and off-grid times
from 1e-12 to1e12. `native-java8.json` retains every comparison and all 42 CT
histories. Largest relative pressure/derivative errors are
1.3545e-14/1.4134e-13; largest absolute errors1.5988e-14/9.0950e-13.
These sampled errors are evidence, not a uniform machine-precision guarantee.

`carter-tracy-reference.json` retains a separate SciPy-Bessel spectral
calculation (12 point Gauss, log width<=.25,lower exp(-42), upper 80), checked against
all 40 inverse-transform values before DOP853 integration of
`dW/dt=(1-W*Pprime)/(P-t*Pprime)` in log time. Starts 1e-14 and1e-16 are both
retained. Unit U=1 reservoir m3/bar and boundary pressure step 1 bar set the influx
scale. Seven final times 1e-8…1e4 and meshes 32/64/128/256/512/1024 all converge;
maximum finest-mesh relative error 0.00296785827. This is a CT recurrence/ODE
comparison. CT is itself approximate: its early pressure-step influx ratio to
exact radial diffusion tends to pi/3, about 1.04719755. Timestep refinement
cannot remove that intrinsic approximation error. The earlier Run62 exact
pressure-step benchmark and its failed strict-positive PDE reference remain
separate evidence; no prior failures are removed by this batch.

The audit rejects missing/duplicate cases, nonfinite/negative outputs, altered
mesh identity and corrupted pressure/derivative evidence. Its positive control
and 12 deliberate corruption controls pass. Source review found and repaired a
NaN-interior-time admission hole before publication; the negative control is
retained. Reported derived errors/acceptance flags are not trusted in place of
recomputing from raw outputs.

## Runtime and compatibility

`runtime-java8.json` retains execution indices/UTC times and all samples from
ABBA execution: unmodified master, candidate, candidate, unmodified master.
Each separate JVM has three warmups and seven retained samples. Actual runtime
is Zulu OpenJDK 1.8.0_512-b09. The affected production classes and optional driver
were separately compiled with `javac --release 8`, then the final sources
were compiled and replayed using actual JDK8 `javac -source 8 -target 8`; the repository's ordinary
Maven build uses its current Java 17 release setting. No dependency/POM change.

Median 1000 interval CT runtime: baseline 0.1654–0.1670ms, candidate 31.125–31.515ms.
Separate pressure+derivative query pairs: baseline 107.8–108.3ns,
candidate 59.1–71.8microseconds. Cold first query: baseline 1.23–1.43ms,
candidate 2.23–2.27ms. Timings include their loop overhead and are environment
observations, not performance assertions or a general process benchmark.
The positive quadrature is substantially more expensive than the inaccurate
rational/log approximations. CT computes the pair once per interval. Each scalar pressure or derivative
getter currently computes the pair, so requesting both separately duplicates
that work; the separate-query timing includes this remaining avoidable cost. The
kernel is invoked only by the radial APIs; ordinary EOS/flash, PVT and
Fetkovich paths do not call it. No general simulation speed claim is made.

## Reproduction

Use the work-with-neqsim Maven bootstrap first. From the repository root,
with a selected Python containing mpmath 1.3.0 and NumPy 2.3.5/SciPy 1.17.0:

```sh
$PYTHON src/test/resources/neqsim/pvtsimulation/infinite-radial/generate_reference.py --output /tmp/reference.json
./mvnw -B -ntp dependency:build-classpath -Dmdep.outputFile=/tmp/radial-classpath
$JDK17/bin/javac --release 8 -cp "target/classes:$(cat /tmp/radial-classpath)" -d /tmp/radial-classes src/main/java/neqsim/pvtsimulation/reservoirproperties/materialbalance/RadialInfiniteInfluence.java src/main/java/neqsim/pvtsimulation/reservoirproperties/materialbalance/VanEverdingenHurstAquifer.java src/test/resources/neqsim/pvtsimulation/infinite-radial/Qualification.java
$JDK8/bin/java -cp "/tmp/radial-classes:$(cat /tmp/radial-classpath)" Qualification /tmp/reference.json /tmp/native.json
$PYTHON src/test/resources/neqsim/pvtsimulation/infinite-radial/qualify_carter_tracy.py --native /tmp/native.json --reference /tmp/reference.json --output /tmp/ct-reference.json
$PYTHON src/test/resources/neqsim/pvtsimulation/infinite-radial/test_qualification.py
./mvnw -B -ntp -Dtest=RadialInfiniteInfluenceTest,VanEverdingenHurstAquiferTest test
```

The optional reference generator accepts `--dependency-dir` for a private
mpmath installation. These drivers do not add project/runtime dependencies.
`test_qualification.py` deliberately audits the committed outputs, while
`qualify_carter_tracy.py` accepts newly generated paths. The JUnit fixture is
output-complete and does not run arbitrary-precision inversion during CI.

## Boundaries and next dependency

Zero time returns zero as bookkeeping; the physical right derivative diverges.
Negative/nonfinite time and NaN radius now throw. Existing nonpositive/infinite
radius sentinels remain supported. The finite-radius maximum and clipping in
CT remain legacy policies. In particular, R=10 retains its incorrect
1.552585 zero-time right limit; this batch does not qualify a finite annulus,
a water-mixing model or Fetkovich/radial equivalence. A finite-annulus response
has a distinct spectral/boundary/validation matrix and requires its own batch.
No new public/MCP operation, fluid version or reservoir-matching parameter is
introduced. Javadocs, indexed material-balance/aquifer guides and field agent/
skill handoffs are updated; the capability inventory is regenerated.

Full #4740 acceptance still requires complete independently admitted measured
laboratory-to-field data, remaining phase domains, heldout GOR/composition,
physical finite-aquifer and broader Bayesian evidence, dependency-gated
multi-tank matching and full Tran 2025 RCVD equations/data. Refreshed publisher
retrieval for DOI10.1016/j.jgsce.2025.205551 returned 403; exact missing material
is the full RCVD/density equations/conventions, fluid/EOS/relative-permeability,
BHP/boundary inputs and independent numerical benchmark. No abstract-based
implementation, whole-issue closure or recoverable-reserve inference is made.
