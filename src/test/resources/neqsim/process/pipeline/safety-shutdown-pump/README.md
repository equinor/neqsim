# Safety Shutdown forward pump coastdown — executed synthetic evidence

This is a bounded forward-curve acoustic inlet, not a complete pump trip or
check-valve slam. `AcousticPumpBoundaryTest` generates the JSON files under
`target/safety-shutdown-pump`; this directory retains the published run. Source
and commands are pinned in `provenance.json`. There is no fitted asset data,
calibration or vendor-performance claim.

## Declared physical model

- SRK/classic water, 298.15 K, 20 bara discharge; density/transport/sound speed
  come from the actual initialized EOS. The example declares 1000 m/s effective
  acoustic speed; it is not inferred as a measured water or pipe property.
- Physical line: 1000 m, 0.2 m diameter. The ordinary study retains its initialized
  Darcy friction. Analytical wave tests explicitly set the ideal zero-friction,
  uniform-reference limit after actual-fluid initialization.
- Reference pump curve: 600 rpm; actual flow 0/36/72 m3/hour, head 60/50/20 m,
  efficiency 70/75/70 percent. Existing `PumpChart` quadratic fit gives
  `H = 60*(n/600)^2 - 100000*Q^2`, Q in m3/s. Original reduced-flow domain is
  0 through 0.12 (m3/hour)/rpm. Declared positive-speed range: 6 through 600 rpm.
- Independent root: `100000*Q^2 + B*Q + Cm-Hs-60*(n/600)^2 = 0`,
  `B=1000/(9.81*pi*0.01)` s/m2. No source or boundary solver is reused by this oracle.
- Pressure head is absolute. Suction/discharge have matching velocity/elevation
  bases. The boundary has no hydraulic storage, driver inertia or fluid corrections.
  Actual speeds are prescribed piecewise-linear data on the existing pipe clock.
- `public-input.json` contains the synthetic actual 600→580 rpm/0.4 s example,
  including exact actual-liquid mass flow and consistent suction pressure.
  Feed pressure denotes the **pump discharge**, not suction.

## Frozen acceptance and results

`provenance.json` records actual test commands/exits and unique counts. Files:

| Artifact | Question and status |
| --- | --- |
| `public-input.json`, `public-output.json` | Executed real-fluid forward coastdown; aligned pressure, flow, actual speed and accepted hydraulic work. Bounded execution PASS; full Safety Shutdown NOT_EVALUATED. |
| `analytical-refinement.json` | Smooth-start samples of n=600-50*t², 0.01 s spacing; pre-reflection ideal line to0.4 s. Independent cell-average retarded characteristic; 51/101/201 faces. Same >=1.7 refinement-ratio gate PASS. |
| `time-refinement.json` | Fixed101-face ideal line; n=600-50*t; Courant .9/.45/.225. Endpoint-held hydraulic work converges to an independent dense Simpson integral with >=1.7 error reduction. PASS. |
| `nearby-cases.json` | Three initial actual flows .0075/.01/.0125m3/s × three requested steps .001/.0037/.009s. Signed accepted volume/momentum controls PASS. |
| `domain-transition-input.json`, `domain-transition-rejected.json` | Faster declared synthetic speed reduction has no admissible forward root. Request status FAIL; accepted time/history/state/work retained. No fake stopped-pump/check-valve continuation. |
| `nonsmooth-front-diagnostic.json`, `initial-smooth-gate-failure.txt` | Original abrupt linear-start forcing gives a wavefront corner. The >=1.7 smooth-wave criterion FAILS, with ratios about1.676/1.679. This failed criterion is retained, not loosened or relabelled PASS. |

Additional executed assertions cover independent roots at7speeds, metre/kJ-per-kg
unit equivalence, original-domain rejection, zero/negative speed, fitted-curve
turning, phase/mode guards, constant-speed equilibrium, exact speed and valve
knot unions, full remainder, immutable input isolation and deterministic Java
restart. A rejected internal interval preserves accepted cells, time, history,
exchange and work. Earlier accepted substeps in a longer request stay accepted;
this is not global coordinator rollback.

The unchanged discrete tolerances are: head residual1e-9m, equilibrium1e-4Pa,
volume residual1e-9m3, momentum residual1e-7kg*m/s, independent accepted-work
sum1e-6J or1e-10relative. Temporal quadrature error against continuous work is
reported separately and must converge; it is not the discrete work-sum residual.
Runtime entries are measured execution times including local load/JVM effects,
not warmed throughput or a speedup claim. The conservation checks are linear
acoustic volume/momentum, not full EOS component/mass/energy or shaft-energy closure.

## Evidence limits

Full stopped/reverse/four-quadrant pump behavior, torque/inertia, check-valve
disc/lift/contact/slam, NPSH/cavity collapse, branched routes, integrated safety
logic and global transaction/replay, vector support loads and asset/SIL/SRS/
proof-test qualification remain **NOT_EVALUATED**. The method/checkpoint review
in NRC-4707 remains a separate decision; no author acceptance is inferred.

Primary context: [Hydraulic Institute affinity rules](https://datatool.pumps.org/pump-fundamentals/pump-curves)
and [KSB affinity/power basis and similarity limits](https://www.ksb.com/en-global/centrifugal-pump-lexicon/article/pump-affinity-laws-1117584).
These are model references, not transient validation data.

See the [API and study guide](../../../../../../../docs/wiki/water_hammer_implementation.md#safety-shutdown-forward-pump-coastdown)
and campaign [#4707](https://github.com/equinor/neqsim/issues/4707).
