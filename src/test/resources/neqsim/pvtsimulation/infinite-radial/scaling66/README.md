# Carter–Tracy arithmetic across scales — Run66

> Large run outputs and receipts described below are archived. Follow the
> [restore instructions](../README.md#historical-outputs-and-checkout-size) before
> replaying these optional audits. The historical record remains at the
> [pinned original revision](https://github.com/equinor/neqsim/tree/bbb1429b23d8b6210a1e3bb5c7e55a92f8b0b88e/src/test/resources/neqsim/pvtsimulation/infinite-radial/scaling66).


Continues sole draft #4924 from head
`1263495ad5715f52588981dc71cd60e76b75c2fa`, master
`97c03ecf50d16a6dab7bd812364f49d5f4c5c444`; fresh remote/local parent tree
`19ef3584b2f9aacbbd26b73ffb1b81854070140c` matched before editing. Scope and
criteria were frozen in `SCOPE.txt`. No physical model, pressure protocol,
influence kernel, EOS/history calibration, public operation or dependency is added.

## Reproduced defects and equivalent evaluation

The parent silently freezes water when the absolute pressure denominator is
below `1e-30`. For a first interval with positive time t, its own rational
recurrence gives W=U*drop*t/P, strictly positive when representable. The frozen
zero contradicts that identity and the independent small-time limit. Direct
U*drop can also overflow or underflow although the final volume is finite.
Five of the initial six new regression methods fail on unchanged parent source;
all original failures and current commands are retained in the validation receipt.

The same recurrence is evaluated by its dimensionless elasticity e=t*D/P and
denominator d=1-(tPrevious/t)*e. It becomes
Wnew=((1-e)/d)*Wprevious + dt*U*drop/(P*d), followed by the existing projection
to nonnegative cumulative water. The product t*D is formed before division by P.
For the positive infinite spectrum, 0<e<=1/2, so the normalized denominator is
at least 1/2. No fitted blend or absolute freeze remains.

All three numerator and both denominator factors are exponent-scaled together.
Each finite nonzero factor normalized with Math.getExponent lies between
2^-51 and 2, including subnormals. Intermediate mantissas remain normal and
finite (roughly 2^-155 to 2^105); one final Math.scalb applies the combined exponent.
Final underflow is correctly allowed and genuinely unrepresentable positive
volume is explicitly rejected. Finite nonempty ordered inputs are required,
support is finite/nonnegative and drops are finite signed total boundary drops.
Zero support returns zero. Invalid arguments and unrepresentable/singular
transitions have explicit exceptions; inputs remain unchanged.

This preserves the existing nonnegative cumulative projection. Negative total
boundary drops can reduce cumulative influx to zero, without negative cumulative
water credit. This is not a water-mixing or recharge model.

## Independent arithmetic and analytic evidence

All cases are synthetic mathematical stress tests. Time and influence are
dimensionless; support is reservoir m³/bar, total boundary drops are bar and
cumulative water is reservoir m³. No standard/separator basis or fluid data is
invented. The 100-digit Python Decimal oracle evaluates the ORIGINAL rational
recurrence from exact binary native P,D,time,support/drop values. It does not
reuse normalized coefficients. This is independent arithmetic, not independent
pressure-model or measured-field accuracy. Original inverse-Laplace references
and separate SciPy CT ODE validation remain distinct.

| Control | Final result |
|---|---|
| Main matrix |1,872 paths spanning 13 time scales,4 support scales,3 drop scales,3 protocols and4 meshes; zero candidate failures|
| Finite-volume endpoints |37,914 comparisons; maximum error1.3892e-15 of case maximum volume|
| Unrepresentable histories |168 positive-overflow cases rejected explicitly at the correct first index|
| Parent negative control |675 main-matrix case failures, retained in full|
| Analytic early limits |24 full step/ramp histories at tEnd 1e-200/1e-70, six meshes 32…1024; strict convergence, maximum finest relative error0.00025335|
| Bounded arithmetic |12 first-step cases at radius ratios 3/10/100, including Double.MIN_VALUE; zero arithmetic failures; **no finite-radius physics admission**|
| Transition controls |Later positive overflow at index 2 rejected; finite first influx followed by unbounded negative forcing correctly projected 0|
| Original independent CT ODE |All 42 full histories retain frozen criteria; finest relative error0.00296785827|

For early P=(2/sqrt(pi))*sqrt(t), a constant unit pressure drop gives the
limiting CT coefficient 2*sqrt(pi)/3. A ramp drop=t/tEnd gives final coefficient
2*sqrt(pi)/5. These analytic references are separate from evaluating each
discrete recurrence. The first-step coefficient is sqrt(pi)/2, also tested at
the smallest positive double. None is an exact pressure-step diffusion claim;
the CT early approximation ratio to that different problem remains pi/3.

`comparison.json.gz` retains the full arithmetic/analytic comparisons and every
negative-control failure; `summary.json` records counts and its uncompressed
payload hash. Complete main, analytic, bounded and transition native histories are retained
in `before.json.gz`, `after.json.gz` and `master-control.json.gz`. The latter is
freshly fetched exact unmodified master source, compiled and executed on actual
Java 8; it is a negative control, not accuracy truth. Every kernel/input bit
matches before/after within all four families. Original 20 independent pressure/
derivative cases are unchanged, and their inverse transforms are reused, not
claimed rerun. `carter-tracy-reference.json` is a fresh execution of the unchanged
independent CT ODE qualifier on current native output.

## Failures found during qualification and retained

Independent review found that a first normalized candidate still formed dt/P
before volume scaling. For radius 100 and t=Double.MIN_VALUE that quotient
underflowed, returning 0 instead of the finite Decimal result 1.281566369331672e76.
Other subnormal quotients lost precision. All five forcing factors are now
scaled together; no acceptance tolerance changed. `partial-quotient-v1.json.gz`
and its full comparison retain six bounded arithmetic failures. The first
1,872-case matrix/ABBA driver and auditor sources and all outputs are losslessly archived in
`first-v1-evidence.tar.gz`, with payload hashes in `first-v1-manifest.json`.
The original first-candidate compiled caller SHA256 was
`d5d5834045aa17fc94a52e7107afdcbf67fe2f501a9cdca500851018a40fff2f`.
These are historical first-candidate results, not current acceptance.

Review also exposed incomplete checking of interior transition water values.
The final auditor applies the same complete Decimal/error/origin/nonnegative
checks to that history. Deliberate protocol, identity, missing-case, negative/
nonfinite/altered water, wrong overflow index, missing derivative, analytic,
bounded and original-history corruptions are retained in `test_audit.py`.
Checks use explicit exceptions and are executed under Python `-O`.

## Runtime and reproduction

Actual Zulu 8.0.512-b09, unchanged Qualification/Startup65 drivers and Run65 ABBA
runner, separate parent/current class directories, three warmups and seven
retained warm samples per JVM. Original 42 CT histories differ by at most
8.08e-16 when normalized by max(1,volume), within the frozen 1e-12 absolute +
1e-12 relative limit. Each same-source pair of full histories is bit-identical.

| Workload | Parent | Candidate |
|---|---:|---:|
|1,000 CT intervals, median|8.220–8.448 ms|7.627–8.644 ms|
|20,000 scalar P/D pairs, median|326.767–327.699 ms|295.791–326.208 ms|
|Cold first public query|4.562–5.858 ms|4.437–6.859 ms|

Conservative CT ratio 1.0516 passes the frozen <=1.20 ceiling. Cold variability and
all samples are retained; there is no cold or general speedup claim. No new
calls enter ordinary EOS/flash/PVT/Fetkovich paths. These are workload
observations requiring accountable review.

Compile `Scaling66.java` and unchanged `../Qualification.java` and
`../initialization65/Startup65.java` with affected production source on actual
Java 8, using `-source 8 -target 8`. Run Scaling66 to obtain each complete matrix.
`runtime.py` uses the unchanged Run65 runner in this directory; pass Java,
parent/current classes and dependency-classpath paths as in its help. Run
selected-Python `-O audit.py`, `-O test_audit.py` and `-O qualify_runtime.py`.
Run the unchanged `../qualify_carter_tracy.py` on the decompressed current
qualification JSON and original reference. Commands, exits, runtime/source
identities, coverage and publication gates are in `validation.json`.

Finite-radius negative-intercept/near-singular branches remain unqualified;
the new numerical controls do not repair their physical initial/late limits.
Measured laboratory-to-field, remaining PVT phase domains, broader aquifer/
Bayesian, multi-tank and full Tran2025 equations/benchmark acceptance remain open.
