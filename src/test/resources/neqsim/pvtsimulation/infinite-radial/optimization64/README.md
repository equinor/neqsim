# Controlled radial evaluation and positive-tail qualification — Run64

This continues draft #4924 from exact parent
`04e78817a608b59aa5bd0bbe846b406ff18729c9`, based on master
`97c03ecf50d16a6dab7bd812364f49d5f4c5c444`. It changes the numerical evaluation
of the same infinite-acting radial diffusion model. Units, public APIs,
Carter–Tracy recurrence, finite-radius policy, fluid/sample/separator records,
EOS versions and reservoir matching parameters are unchanged. The original
Run63 outputs and independent 25/35 digit inverse-Laplace references remain
historical evidence, linked to their exact parent revision.

## Problem and numerical correction

The parent evaluates hundreds of exponentials for every scalar response. A
first optimization used controlled Taylor/exponential bounds but retained its
analytic high-frequency tail. A denser adjacent-double screen exposed small
pressure decreases in both parent and first candidate. No tolerance was
relaxed: the failing trial, its patch, raw outputs and report remain here.

The current implementation retains the positive middle-frequency quadrature,
uses analytic small-argument remainder bounds, and resolves the high-frequency
tail with positive fixed log-frequency quadrature. Its reciprocal Bessel
modulus uses the same five independently reviewed asymptotic coefficients.
This avoids the rounding/cancellation in the previous analytic tail expression.
There is no empirical transition fitted to these diagnostic rows, interpolation
table, time rounding, new physical model or data calibration.

For argument a<=0.001, the spectral pressure uses the quartic of
1-exp(-a); its derivative uses that quartic's cubic derivative. Alternating
series give relative remainders below 8.34e-15 and 4.18e-14. The low-frequency
Ein integral uses its quartic/cubic at x<=0.001, with relative bounds below
1.67e-15 and 8.34e-15. Thresholds were frozen from these bounds before the first
candidate comparison, not fitted to reference values.

The tail uses 6,656 positive Gauss nodes from u=64 to exp(420). Its weight is
(2/pi)*ordinary_log_weight*R(u)/u, and derivative contribution is
weight*a*exp(-a)/time. Frequencies are stored unsquared and a is evaluated as
(sqrt(time)*u)^2, avoiding overflow of u^2 before damping. The exp(420) endpoint
omits less than 2.6e-183 pressure in exact arithmetic, or 1.2e-21 after scaling
by sqrt(Double.MIN_VALUE). Exponential derivative omission beyond that
frequency is negligible for every positive representable double time.

At a>=80, pressure loss rounds to one and derivative contributions are clipped
within declared error bounds. For small time, a retained tail node near
u=1/sqrt(time) supplies D>0.001/sqrt(time), while the clipped tail contributes
less than 4.3e-32/sqrt(time). Its relative bound is therefore 4.3e-29. For
time>=1/4096, the absolute clipped bound is below 3.79e-28; for
x=time*exp(-80)>=1 the low-frequency derivative supplies the late relative
bound, including the declared quadrature/roundoff margin. These errors are far
below the unchanged original pressure/derivative acceptance bounds.

Gauss weights are not monotone. An exact suffix maximum of the actual weights
certifies early termination only when every remaining positive addition would
round away individually and all remaining derivatives are already clipped.
This preserves the prescribed sequential floating sum. It is not a bound on
the total exact-arithmetic mass of all individually rounded-away additions;
floating accumulation and quadrature error are qualified separately.

`tail-grid.txt` verifies the proof premises: combined positive weight
40.115931515658495<64; minimum ordinary log weight 0.0252974685>0.025;
maximum log-node gap 0.0916823<0.1; R(u)>=0.99991027>0.999, R(u)<=1;
and every suffix maximum matches the remaining actual weights.

## Complete numerical and admission evidence

All quantities below are dimensionless mathematical responses, not measured
PVT/field accuracy. The primary source and data-use provenance remain in the
parent README: van Everdingen–Hurst1949, equations VI-4 and VI-24. No paper or
third-party experiment table is redistributed.

| Check | Executed result |
|---|---|
| First 6,278-point trial | Parent 12 pressure decreases; polynomial-only candidate the same 12; failing patch, outputs and report retained |
| Positive-tail 6,278-point trial | Candidate zero decreases; parent failures remain explicit |
| Initial expanded vector | 41,415 points, candidate zero decreases, parent 1,312 decreases; complete first-expanded outputs retained |
| Final certified vector | 51,216 points; candidate zero decreases, parent 2,514 decreases retained as negative controls |
| Before/after algorithm agreement | Frozen 1e-12 absolute + 1e-12 relative bounds pass; maximum relative P/D differences 1.066e-14 / 5.019e-14 |
| Scaled limits | Double.MIN pressure/slope and Double.MAX late slope pass unchanged 1e-12 scaled bounds |
| Independent inverse transforms | All original 20 times / 40 separately inverted values pass unchanged bounds; sampled relative P/D errors <=1.333e-14 / 1.414e-13 |
| Independent Carter–Tracy ODE | All seven targets and six meshes converge; maximum finest relative error 0.00296785827; original bounds unchanged |

Large absolute derivative differences at subnormal time are retained in the
JSON; their relative and scaled errors are the relevant numerical controls.
The parent is an explicit failed monotonicity control. Its decrease count is
not relabeled as a passing baseline. Candidate nondecrease still allows zero
negative steps, with no epsilon. Original laboratory and reservoir holdout
claims remain separate and unresolved.

`common-times.txt` is a strict superset of the retained 41,415-point vector.
Simply taking a rounded estimated crossing plus/minus one ulp did not guarantee
that sqrt/multiply arithmetic reached both branches. The native coverage
producer now probes the actual arithmetic and adds up to four adjacent steps,
retaining all initial inputs. `brackets.csv.gz` contains all 14,736 grid/shortcut
records: 13,186 certified positive-time branch brackets, 1,538 underflowed
centers, and 12 transitions below the smallest positive double time. No
unsupported positive-time value is invented.

The independent Python auditor repeats every native branch expression, checks
both branch sides and their presence in the common vector, and pins the full
ordered grid identity vector to SHA256
`11f4d08e68ac460ddbea38b741f5f0b059b913e92103662a320b6d3acd48f5d8`.
The exact common time vector is also hash-pinned. Counts and self-consistency
alone are insufficient: independent review
reproduced an accepted substituted frequency, which is now rejected by the
identity pin and a retained negative control.

Admission controls include a passing candidate, the failing parent, ten matrix
corruptions and five branch-receipt corruptions. Independent read-only review
replays the final matrix and CT audit under Python -O; numerical and metadata
results match the retained reports. This supports accountable human review;
it does not replace subsystem/domain approval.

## Measured runtime and compatibility

`runtime-java8.json` retains ABBA order, UTC starts, all seven samples after
three warmups in each separate JVM, and checksums. Both versions use the same
inputs/driver and actual Zulu8.0.512-b09 runtime. The affected production
classes and optional drivers compile and execute on that actual Java8 runtime.
Ordinary Maven analysis uses the current pinned Temurin17.0.21+9.

| Workload | Parent04e78817 | Current candidate |
|---|---:|---:|
| 1,000 Carter–Tracy intervals, median | 26.38–26.75 ms | 7.62–8.28 ms |
| Separate scalar P/D pair, median | 54.08–54.17 microseconds | 14.16–15.67 microseconds |
| Cold first query at tD=1 | 2.207–2.226 ms | 21.342–23.316 ms |

The conservative warm median speedups are 3.184x for CT and 3.452x for scalar
pairs, exceeding the predeclared 2x targets. **Cold initialization becomes
about 9.6–10.6x slower** because the positive tail is initialized once. Both
tradeoffs are retained, not hidden by the warm averages. Scalar getters still
compute paired responses independently; CT computes the pair once per interval.

A fresh second ABBA control against unmodified master is retained in
`runtime-master-control.json`, with a complete native master receipt in
`master-control-native.json.gz`. Master CT medians are 0.129–0.149 ms versus
7.60–8.24 ms for the candidate, a residual cost of roughly 51–64x. The master
formulas fail 18 of the 20 combined pressure/derivative reference comparisons;
they are a cost/negative control, not an accuracy reference. The earlier
historical baseline was 0.165–0.167 ms. This is a bounded radial
workload observation, not a general process speedup or performance acceptance.
No new calls are added to EOS/flash, ordinary PVT or lumped Fetkovich paths.
Accountable review of the residual/cold costs remains pending.

## Reproduction and file identities

Use the Maven bootstrap before Maven/Spotless. Compile the same optional drivers
against the exact parent and current affected Java sources in separate class
directories; use the existing Maven dependency classpath and actual Java8.
Generate the native tail coverage vector once from the candidate, then feed
that identical vector to both binaries:

```sh
$JDK8/bin/java -cp "$AFTER_CLASSES:$DEPENDENCIES" neqsim.pvtsimulation.reservoirproperties.materialbalance.TailGrid64 before.csv common-times.txt tail-grid.txt brackets.csv.gz
$JDK8/bin/java -cp "$BEFORE_CLASSES:$DEPENDENCIES" neqsim.pvtsimulation.reservoirproperties.materialbalance.Matrix64 common-before.csv.gz common-before.txt 04e78817 common-times.txt
$JDK8/bin/java -cp "$AFTER_CLASSES:$DEPENDENCIES" neqsim.pvtsimulation.reservoirproperties.materialbalance.Matrix64 common-after.csv.gz common-after.txt candidate common-times.txt
$PYTHON audit.py --times common-times.txt --brackets brackets.csv.gz --before common-before.csv.gz --after common-after.csv.gz --output comparison.json
$PYTHON test_audit.py
$PYTHON ../qualify_carter_tracy.py --native native-java8.json --reference ../reference.json --output carter-tracy-reference.json
```

Run the commands in this directory, with selected Python3.12.14; CT also uses
NumPy2.3.5/SciPy1.17.0. Large historical records and common before/after matrices are losslessly
compressed; no rows, failures or output bytes are lost. `historical-containers.json`
records both payload and container hashes. Extract historical containers before
replaying their frozen scripts; the initial `before.csv` remains plain text
as input to the current native coverage producer. Historical `first-*`/`*-first` files preserve
the initial trial and initial expanded vector. `first-candidate.patch.gz`, after extraction, applies
to the exact parent kernel. Current committed tests read the full certified
vector. The final validation receipt records actual commands, exits, source
hashes, generated coverage and publication gates; it does not claim unrun CI.

## Remaining boundary

Finite-radius maximum-policy physics is unchanged and unqualified, including
its incorrect initial right limit for some radii and approximate late
intercept. CT convergence is to its limiting ODE, not exact pressure-step
radial diffusion: its early intrinsic ratio remains pi/3. No water mixing,
Fetkovich/radial transient equivalence, continuous posterior or recoverable
reserve claim is made. Full #4740 acceptance still requires measured
laboratory-to-field qualification, remaining phase domains, broader aquifer/
Bayesian evidence, multi-tank and the full Tran2025 method/benchmark.
