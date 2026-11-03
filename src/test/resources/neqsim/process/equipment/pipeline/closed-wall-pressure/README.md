# Closed-wall mechanical pressure compatibility

This is a bounded #4674 / #3298 prerequisite. It does **not** qualify terrain pools,
multicomponent phase transfer, or a predictive evaporation time. No EOS or drag
closure is changed. The existing conservative operator and transaction owner are reused.

## Model and acceptance defined before evaluation

The independent analytical limit is a stationary pure phase at constant density:
`p(x) = 15 MPa - rho * 9.81 m/s² * (z(x) + 300 m)`. The pipe is 5 km long,
ID 1.0148 m. Gas and oil controls use 10 and 800 kg/m³; JUnit also tests water at
1000 kg/m³. No thermal or interphase source is enabled. Both external faces are
impermeable. These are mechanical operator controls, not an EOS fluid study.

For an affine elevation, every cell must have acceleration magnitude <= 1e-10 m/s²
and exactly zero mass/energy rates. JUnit checks 1/12/24/48 cells and both signs of
the 0.011 slope. A horizontal acoustic cosine must retain the cell-centre wall
pressure (zero normal pressure derivative). The implementation must ignore an
external inlet donor when closed, balance the **total** mixed-cell body force,
reject inadmissible half-cell pressure without a floor, and preserve the existing
transactional diagnostics on rejection. Seven JUnit methods test these contracts.
They do not assert stationary individual phases of different density.

## Result and source provenance

Baseline commit: `f57ea4fbe0a49b81d3a4fd28096d83561e7b3fb2`.
Baseline production Git blob: `9fcd175997b02fd55555cb569293e7f39e7f9794`.
Executed candidate production Git blob: `2cb8ac3fad322f94061780864a5a22534d9690ab`.
The commit containing this evidence identifies the complete source tree.
Runtime: OpenJDK 17.0.20, compiled with Java 8 `--release 8`. Java 8/21 runtime
validation belongs to exact-head CI and is not claimed by this local run.

| Limit | Unmodified operator | Mechanical wall |
| --- | ---: | ---: |
| Maximum affine boundary acceleration, 12–192 cells (m/s²) | 0.053955 | 7.16e-15 |
| Maximum affine interior acceleration (m/s²) | 1.43e-14 | 1.43e-14 |
| Flat acceleration (m/s²) | 0 | 0 |
| Maximum discrete oracle disagreement (m/s²) | 1.79e-16 | 1.79e-16 |
| Integrated mass and energy rate for stationary controls (kg/s, W) | 0, 0 | 0, 0 |

Three of the initial four regression methods fail against the unmodified class:
affine balance, horizontal acoustic inlet response (factor two), and inadmissible
wall pressure rejection. The final candidate passes all seven methods. The
baseline failure log is a four-method baseline run, not a claimed seven-method run.

The mechanical wall pressure is the adjacent cell pressure plus/minus the local
half-cell gravitational head, using conservative total mass per length divided by
area. Merely extrapolating interior pressure to the closed outlet was rejected:
it removes the affine outlet error but changes the horizontal acoustic reflection.
The current closure applies the mechanical wall condition at **both** closed ends.
There is no imposed numerical pressure floor or new donor through a closed face.

## Refinement and remaining limitation

`gravity_baseline.csv` and `gravity_candidate.csv` contain all 30 cases each:
flat, affine, and original terrain, 12/24/48/96/192 cells, gas/oil. Acceleration and
oracle errors are in m/s²; integrated mass and energy rates are kg/s and W.
Phase column 0 is gas and 1 is oil. The discrete oracle is independently evaluated
from the pressure samples, gravity, and documented old/new wall rule.

Original face terrain is
`z_j = -300 - 55 sin(pi*j/N)^2 - 12 sin(3*pi*j/N)^2 m`.
The curved-profile **interior** residual is unchanged: 0.0865071 m/s² at 12 cells,
0.000580045 m/s² at 192 cells. Candidate boundary residual is 0.0375609 and
0.00818738 m/s² respectively. A general well-balanced interior/free-interface
scheme and actual gravity-retention qualification remain open. Zero stationary
mass/energy rates alone do not establish momentum equilibrium or pool retention.

## Reproduction

The Java sources `ClosedWallPressureCompatibilityTest`, `ClosedWallGravityAudit`,
and `ClosedWallPressureBenchmark` are in the matching `twophasepipe` test package.
After a normal `./mvnw test-compile dependency:build-classpath
-Dmdep.outputFile=target/closed-wall-classpath.txt`, run the audit with the normal
test/classes/dependency classpath and an output CSV argument. The optional second
argument `legacy` selects the independent old-wall oracle, **only** when executing
against the baseline production class. It does not switch the production model.
Run `./mvnw test -Dtest=ClosedWallPressureCompatibilityTest` for the focused suite.
For baseline comparison, use an isolated checkout of the stated baseline and copy
only these audit/benchmark test sources into it; do not merge or sync an active PR.

The benchmark takes an output CSV path and `true`/`false` for closed boundaries:
16 horizontal pure-gas cells, 3000 warm-ups, seven repeats of 5000 RHS evaluations.
The four timing CSVs retain every repeat. Median baseline/candidate timings were
8.598/6.583 us for open ends and 10.230/5.873 us for closed ends. They were measured
in independent sequential warmed JVMs while the broader regression JVM was active;
repeat ranges overlap widely (about 5.6–35.3 us). These are measured cost evidence,
**not** a speed-up, isolated throughput, or statistical non-inferiority claim.
The additional mechanical arithmetic is restricted to explicitly closed faces.
