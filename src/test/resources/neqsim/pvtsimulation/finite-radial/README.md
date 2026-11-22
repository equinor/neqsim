# Sealed-annulus qualification

The native response uses dimensionless radius, time, pressure and inner wall flux.
The initial perturbation is zero, the inner flux is unity and the outer wall is
sealed. All comparisons use analytical diffusion or synthetic histories. For
physical input units and API usage see the
[reservoir material balance guide](../../../../../../docs/pvtsimulation/reservoir_material_balance.md#sealed-annulus-response).

- [Frozen scope](https://github.com/equinor/neqsim/issues/4740#issuecomment-6874305444)
  records the radius and time matrix, tolerances, numerical limits and timing controls
  before comparison.
- [Derivation](DERIVATION.md) describes the independent Laplace transform, radial
  storage conservation, mode normalization, tail bound and early reflection bound.
- [Laplace fixture](laplace_reference.json) retains all 36 declared pairs, both
  precision outputs, binary64 time encodings and verification results. It was
  generated using [the independent script](laplace_reference.py) with
  `python3 -O laplace_reference.py --workers 4` and mpmath 1.3.0.
- [Finite volumes](fv_reference.csv) contains the 12 rate-driven wall pressures in
  the finest case of the [Run70 positive conservative finite-volume benchmark](https://github.com/equinor/neqsim/issues/4740#issuecomment-6722563773).
  It uses 1024 spatial cells and a maximum relative time step of 0.005.
  The pressure-driven Run70 outputs belong to a separate boundary-value problem.
- `FiniteRadialAquiferInfluenceTest` tests both references, the handoff,
  neighboring radii, pressure and slope trends, stored-volume limits, the discrete
  Carter–Tracy recurrence, invalid inputs and the guide example. The prepared
  response applies to homogeneous radial diffusion with the stated boundaries.
  Application to a different geometry or to measured histories requires separate
  qualification of the model and of the history grid.

## Independent review and timing

`review_java8.txt` retains 11,228 bit/text comparisons against the parent and
8,193 adjacent-double junction checks over the full supported radius interval.
The largest absolute pressure and slope jumps were 3.55e-13 and 1.08e-11.
Both directions of rounding occurred. For example, at the handoff pressure
decreased in 4,618 comparisons and slope increased in 4,647. All were within the
frozen budgets. Strict floating-point monotonicity at an adjacent-double handoff
is outside the numerical contract.

`pressure_step_comparison.json` retains the measured discretization effect in
the approximate recurrence and the difference from independent Run70
unit-pressure influx. At radius ratio 10 and time 100, the differences were
−5.486%, −0.180% and +1.395% at 4, 16 and 64 steps per decade. The fine-grid
result at 1e-6 is 21.460% low because the first interval starts at the settled
zero-influx origin. The independently derived short-time continuum Carter–Tracy
startup is 4.673% high there. At 1e4 all three grids reach the theoretical storage
limit. Use a separately qualified superposition or pressure-history solution
when the application requires this transient agreement.

The timing harness compares one immutable prepared response with the same
radius on the original facade, using two class loaders in one warmed VM.
Each workload has 5 warmups and 9 alternating pairs of 800 pressure queries.
The preparation workload averages 16 radii per repetition and includes one
late pressure call per prepared object. Values are elapsed ns per query or
prepared radius. Zulu 8.0.512 and Temurin 17.0.21+9 both pass the frozen
time budgets. Legacy/early/late ratios are 0.963/0.998/0.0081 on Java 8 and
0.871/0.998/0.0054 on Java 17. Warm preparation medians are 0.279 ms and
0.375 ms. This workload supports the prepared-query usage pattern. A standalone
timing should not be extrapolated to a flowsheet or full history-matching run.

To reproduce the runtime and independent diagnostics from the repository root
using a Java 8 JDK, set `JDK8` to that installation's path:

```sh
reference_dir=src/test/resources/neqsim/pvtsimulation/finite-radial
package_dir=src/main/java/neqsim/pvtsimulation/reservoirproperties/materialbalance
base=baf00059f4453eb82a84ead56fa00aacf9783996
output_dir=$(mktemp -d)
mkdir -p "$output_dir"/{original,baseline,current,drivers}
for source in RadialInfiniteInfluence VanEverdingenHurstAquifer; do
  git show "$base:$package_dir/$source.java" > "$output_dir/original/$source.java"
done
"$JDK8/bin/javac" -source 8 -target 8 -d "$output_dir/baseline" "$output_dir"/original/*.java
"$JDK8/bin/javac" -source 8 -target 8 -d "$output_dir/current" \
  "$package_dir"/{RadialBessel,RadialInfiniteInfluence,VanEverdingenHurstAquifer,FiniteRadialAquiferInfluence}.java
"$JDK8/bin/javac" -source 8 -target 8 -cp "$output_dir/current" -d "$output_dir/drivers" \
  "$reference_dir"/{IndependentAnnulusReview,AnnulusTiming}.java
"$JDK8/bin/java" -Xms256m -Xmx256m -cp "$output_dir/drivers" IndependentAnnulusReview \
  "$output_dir/baseline" "$output_dir/current" > "$output_dir/review.txt"
"$JDK8/bin/java" -Xms256m -Xmx256m -cp "$output_dir/current:$output_dir/drivers" AnnulusTiming \
  "$output_dir/baseline" > "$output_dir/timing.csv"
python3 -O "$reference_dir/compare_pressure_step.py" "$output_dir/review.txt" \
  "$reference_dir/run70_reference.json" "$output_dir/pressure_step_comparison.json"
```
