# Safety Shutdown conservative acoustic kernel

Campaign [#4707](https://github.com/equinor/neqsim/issues/4707), following merged #4708,
#4714 and #4720. Source base `cc35f058e6076ce634fa7321fdc12bfb55501a28`.
The exact candidate head/tree, commands and final checks are in the campaign ledger.
No asset or vendor measurements are used. All cases use EOS-derived SRK/classic water
properties, 298.15 K, 20 bara, L=1000 m, ID=0.2 m, declared effective a=1000 m/s.

## Reproduce

Evaluate the work-with-neqsim Maven bootstrap, then run:

```shell
./mvnw -q '-Dtest=WaterHammerConservationTest,AcousticValveBoundaryTest,WaterHammerKernelTest,WaterHammerPipeTest,WaterHammerPipeValveInitializationTest,TransientForceCalculatorTest,PipelineRunnerTest,ProcessAutomationTest,JsonProcessBuilderTest,TabulatedValveCharacteristicTest,CompressorFixedSpeedControlTest' test
```

The tests write `target/ss-conservative-standing-wave.json`,
`target/ss-conservative-nearby.json`, `target/ss-physical-valve-volume.json`,
`target/ss-physical-valve-matrix.json` and the executed public input/output JSON.
The copies here retain one executed candidate run. Wall times are single-fixture
diagnostics with setup excluded and outlet flashes included; they are not portable
assertions, warmed throughput comparisons or speedup evidence.

## What changed physically and numerically

N configured spatial nodes bound N-1 physical control volumes. Their mean pressure
and flow are advanced by shared acoustic characteristic fluxes; every internal
face has one trace used by both adjoining cells. This replaces the existing nodal
update in WaterHammerPipe. It does not add a parallel solver or architecture.

The [guide](../../../../../../../../docs/wiki/water_hammer_implementation.md#method-of-characteristics-moc)
derives the cell continuity update, signed implicit Darcy step, well-balanced
terrain reference and pressure/gravity/friction momentum impulses. Density,
viscosity, wave speed and friction are frozen. Cell means carry inventory;
physical boundary and interior face traces remain available through existing
profile APIs. No pressure correction, rescaled flux or clipped residual is used.
The public JSON exports both initial/final cell means and independent boundary
exchange diagnostics for a separate storage sum.

Boundary forcing retains right-endpoint hold over each internal interval. This
is first-order time quadrature, not exact integration of a continuous valve
record. Substeps localize command/delay/travel knots and advance the full remainder.
Endpoint flow after the update can differ from the held interval flux. Sparse
endpoint-flow quadrature cannot substitute for the accepted substep exchanges.

## Independent checks and frozen gates

- Unchanged physical acoustic volume tolerance: **1e-9 m3**. Compare independent
  sums of cell mean pressure changes with actual accepted inlet-minus-outlet
  exchange, including variable hidden substeps and nearby rates.
- Closed ideal reservoir/line/end closure before arrival: storage is exactly
  **Q0*t**, without computing the expected result from numerical boundary flux.
  Check at 0.3 s for 51/101/201 nodes and Courant fractions 1/0.5/0.37.
- Existing **1% Joukowsky**, arrival/reflection within two internal steps,
  **1e-4 Pa** equilibrium drift, exact-knot, receiver and replay gates retained.
- Standing wave: closed ends, zero initial flow, p'=10000 cos(pi*x/L) Pa, zero
  friction. The independent solution is p'=10000 cos(pi*x/L)cos(pi*a*t/L) and
  Q=A*10000/(rho*a)sin(pi*x/L)sin(pi*a*t/L). Compare exact **cell averages** at
  t=0.37 s, Courant 0.5. Combined pressure/characteristic-flow relative RMS
  target <=3% at 50 cells and <=1% at 200 cells, refinement ratio >=1.7.
- Closed ideal line acoustic energy must not increase beyond 1e-10 relative
  roundoff per step. Signed Darcy algebra is checked for both flow directions.
- Nine physical variable-step closure/hold/reopening cases: 51/101/201 nodes,
  18000/36000/54000 kg/hr, 12 m rise, fractions 0.37/1/0.19/0.73, through 2.3 s.
  Momentum inventory minus independent physical impulses: **1e-7 kg m/s**.
- Rejected local intervals preserve accepted cells, exchange, impulse, time and
  history. Checkpoint/replay carries these fields deterministically. Old nodal
  checkpoints reject continuation and require explicit reinitialization.

Ideal fixtures seed mathematical initial conditions only after actual EOS-fluid
initialization. They do not supply operational fallback properties. First-order
diffusion remains; smaller time alone is not automatically more accurate.

## Executed candidate results

**238 tests, zero failures/errors/skips**, including six new conservation-oracle
tests and all preceding kernel, boundary, pipe, force, pipeline runner, process
automation, JSON builder, characteristic and adjacent compressor regressions.
[provenance.json](provenance.json) records the class counts and source/runtime boundary.

| Nodes | Combined standing-wave relative RMS error | Initial / final closed-line energy (J) | Changing-travel volume residual (m3) |
| ---: | ---: | ---: | ---: |
| 51 | 0.0127937255 | 0.783493 / 0.755393 | 1.65e-17 |
| 101 | 0.0064260705 | 0.783686 / 0.769506 | 4.34e-17 |
| 201 | 0.0032203690 | 0.783734 / 0.776612 | 2.08e-17 |

Refinement ratios are 1.991 and 1.995: **PASS** against the predeclared 1.7 gate.
Energy is nonincreasing at every evaluated closed-line step: **PASS**.
The nine variable-step/nearby physical cases give maximum acoustic volume residual
**4.08e-17 m3** and momentum residual **2.66e-10 kg m/s**: **PASS** against
the unchanged 1e-9 m3 and declared 1e-7 kg m/s targets.

Rapid 0.02 s travel gives 3.195764/3.195522/3.195377/3.195289/3.195217 bar peaks
on 51/101/201/401/801 nodes at requested full Courant. These include physical
frozen-friction evolution and satisfy the unchanged 1% Joukowsky target
3.1830988618 bar. The separate ideal first-trace oracle also passes at Courant
1/0.5/0.37. Equilibrium maximum drift in the committed kernel fixture is zero
at recorded precision. Existing arrival/reflection and exact-knot assertions pass.

Raw results: [standing-wave.json](standing-wave.json), [nearby.json](nearby.json),
[volume.json](volume.json), [refinement.json](refinement.json), [kernel.json](kernel.json),
and executed [input.json](input.json)/[output.json](output.json).
The thirty closure/refinement runs took 1.641–795.689 ms each in this one run;
these are diagnostic timings, with no performance threshold or speedup claim.
Slow-closure outputs remain refinement characterization, **NOT_EVALUATED** as an
independently converged continuous actuator/asset waveform.

## Historical failure is retained

[nodal-baseline.json](nodal-baseline.json) was executed on **unmodified** base
`cc35f058e6076ce634fa7321fdc12bfb55501a28` before production edits, with
`AcousticValveBoundaryTest#characterizeAcousticVolumeDefect`. This explicit
zero-friction, nonuniform-initial-pressure mathematical diagnostic gives
50.373911/25.186270/12.592830 mL error on 51/101/201 nodes. It passes the independently
derived old discrete identity while **failing** physical acoustic conservation.
The historical target remains 1e-9 m3. The older parent-folder artifacts are also
retained under their original source provenance; they are not relabelled as new runs.

The new physical changing-travel case retains frozen physical friction. Its
trajectory is not compared as if identical to the historical ideal diagnostic;
the independent Q0*t and standing-wave fixtures provide exact physical/numerical
oracles for the new cell representation. Updating old tests from nodal quadrature
to the actual evolved cell inventory changes the storage representation, not the
acceptance tolerance. Waveform and energy checks prevent a balance-only repair
from hiding an incorrect surge or propagation speed.

## Applicability

PASS here concerns the **linear acoustic discrete volume/momentum equations** and
the stated analytical limits. Full EOS mass/energy, finite thermodynamic excursions,
phase/cavity changes and integrated Safety Shutdown qualification remain
**NOT_EVALUATED**. Pump coastdown/driver inertia, dynamic check-valve slam, acoustic
junctions, canonical coordinator/physical IDs/global rollback and vector structural
loads are separate campaign dependencies. No SIL/SRS/proof-test/plant/piping-support
approval follows from these synthetic checks. Keep #4707 open.
