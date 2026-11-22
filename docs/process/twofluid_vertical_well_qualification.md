---
title: Vertical-well transient qualification
description: Reproducible synthetic flashing-well replay with phase holdup, conservative transport audits and explicit numerical validation limits.
---

# Vertical-well transient qualification

This synthetic case reproduces issue [#5066](https://github.com/equinor/neqsim/issues/5066).
It is a numerical regression and model-comparison exercise, not experimental holdup validation.
The runnable input is [qualify_vertical_well.py](../../examples/pipeline/qualify_vertical_well.py).

## Inputs and supported configuration

The well is 2000 m long and rises 2000 m, with 0.1 m internal diameter and 1e-5 m roughness.
The feed is 353.15 K, 200 bara and initially 20000 kg/h. The Peng–Robinson EOS uses the
classic mixing rule and multiphase checking. The script lists all 14 relative molar
component amounts; NeqSim normalizes them. The outlet face is fixed at 102.9531305 bara.
The inlet is a prescribed-flow stream; its pressure is not a second prescribed pressure boundary.

For flashing dynamics the example explicitly enables mass transfer, interfacial-pressure
stabilization and its implicit coupling, then calls
`prepareConservativeEnergyTransientFromSteadyState()` on the converged steady solution.
This selects coupled Euler, named-component transport and conservative EOS energy/volume recovery.
It disables slug tracking. This qualification does not cover the older aggregate
mass-transfer/thermal route, moving outlet pressure, shutdown or restart.

A steady hydraulic profile is an initial condition for this transient formulation, not
proof of a transient fixed point. The steady and transient closures differ; a warm-up
response must be retained and reported, not discarded as an already-qualified equilibrium.
Source-free transport uses different semantics: with mass transfer disabled, an oil-only
inlet must not acquire gas merely because a property flash identifies an equilibrium gas phase.

## Run against an explicit source build

Build the selected revision and its runtime classpath using the repository's supported
Java/Maven setup, then install JPype1 in a separate Python environment:

```bash
./mvnw -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
python examples/pipeline/qualify_vertical_well.py \
  --classpath "target/classes:$(cat target/runtime-classpath.txt)" \
  --revision "$(git rev-parse HEAD)" --cells 20 --dt 0.5 --duration 600 > held20.jsonl
python examples/pipeline/qualify_vertical_well.py \
  --classpath "target/classes:$(cat target/runtime-classpath.txt)" \
  --revision "$(git rev-parse HEAD)" --cells 20 --dt 0.5 \
  --duration 1200 --step-at 600 > step20.jsonl
```

The commands use a POSIX classpath separator. On Windows use `;` between classpath entries.
The revision argument is caller-supplied provenance; the script does not infer a source
revision from a jar filename. Record any uncommitted changes or combined PR overlays too.

Repeat the held case with 40 cells and with `--dt 0.25`. Preserve the full JSON Lines output.
Use the same physical reporting window across refinements. For example, a 1 s window
requires `--sample-every 2` at 0.5 s and `--sample-every 4` at 0.25 s. Each sample
reports its window start, signed phase masses discharged since time zero (kg) and the
mean phase rates over that window (kg/h). These use the accepted boundary flux in every
step, including a shortened step at a rate change. The existing phase rates describe
only the last integration interval. Compare common start and end times when an event
splits an interval or the last window is shorter.

The driver records the requested settings before the steady solve. Its
`steady_initialization` record reports convergence, the solver termination reason and
the number of refinement iterations. The default steady wall-clock budget is 20 s.
If the solver reports `WALL_CLOCK_LIMIT`, increase `--steady-time-limit` (seconds) for
the same input and mesh. This setting only changes the time available to the steady solver.
The driver requires a converged initial condition before starting the transient.
Only a final `type: summary` record verifies completion of the requested horizon. Exceptions,
missing summary records and interrupted processes are incomplete runs. The script verifies
accepted time, finite positive pressure/temperature, holdup within the recovery model’s 2e-9 endpoint tolerance and finite conservation
residuals; it reports residual magnitudes rather than silently accepting a numerical tolerance
as physical validation. Compare the phase profiles and rate histories as well as endpoint values.
The last profile entry is a cell-centre value, not the imposed outlet-face pressure.

## Retained evidence

Production baseline: master `be085325c0720f12792f54526ee81b0b2298cfff`, with interfacial-drag
fix `9beab1e266ba3f6e52b50f3a8bcba11cf288a9db`, Java 17.0.21+9. The long runs used the
configuration above and an instrumented JPype driver. The packaged driver is separately
executed against the same production classes; it exposes complete spatial profiles.

| Case | Time (s) | Inlet (kg/h) | Outlet (kg/h) | Terminal-cell liquid holdup |
|---|---:|---:|---:|---:|
| 20 cells, dt 0.5 s, held inlet | 600 | 20000 | 19945.423 | 0.833894 |
| 20 cells, dt 0.25 s, held inlet | 600 | 20000 | 19949.072 | 0.836162 |
| 20 cells, rate step at 600 s | 1200 | 15000 | 14956.840 | 0.861279 |
| 40 cells, dt 0.5 s, held inlet | 600 | 20000 | 20025.684 | 0.850488 |

The complete 600 s time-step comparison differs by 0.0183% in outlet rate and 0.002269
in terminal liquid holdup. The halved-step run has maximum relative conservative-energy
residual 3.33e-11 and component residual 1.05e-15.

With the compatible #5068/#5069/#5070/pressure-contact changes combined, source-free
default transport completes 100 s at 19999.999 kg/h outlet and liquid holdup 1.0. At that endpoint the largest
cell-pressure change from the initial profile is 0.00655 bar. Reducing the feed to 15000 kg/h at 50 s gives 14867.326 kg/h
out at 100 s; that endpoint is still transient.

A fresh 40-cell replay completed all 600 s, replacing the earlier interrupted run. Its
maximum relative conservative-energy residual is 2.54e-11, component residual 1.54e-15
and absolute total-mass residual 5.66e-12 kg. The terminal-cell holdup differs by 0.016595
from the 20-cell, 0.5 s case; sampling locations differ (1975 m versus 1950 m elevation).
Interpolating the 40-cell profile to 1950 m gives holdup 0.855153, compared with
0.836162 for the 20-cell, 0.25 s run at the same height. This does not establish grid convergence.
At 100 s, reducing the
20-cell outer step from 0.5 to 0.25 s changes outlet flow from 19203.106 to 19135.919 kg/h
and terminal holdup from 0.838628 to 0.839323. The 40-cell startup response differs materially;
the two meshes are not a demonstrated grid-convergence study.

A separate 100 s audit with the compatible production changes from #5068, #5069, #5070 and
pressure-contact change `ebf7afae` from #5072 gave maximum relative component residual
8.71e-16, conservative-energy residual 1.01e-11 and EOS energy-recovery residual 9.55e-10.
These are conservation checks, not independent validation of the interfacial closure.

The corrected Beggs–Brill energy balance in #5074 predicts terminal liquid holdup 0.648645
(20 increments) or 0.650030 (40 increments), with outlet temperatures 344.497796 and
344.516516 K. Its adiabatic static-enthalpy loss is 19613.3 J/kg for the 2000 m elevation gain.
Different slip closures and different sampling locations prevent treating agreement of
individual values as a validation criterion. The remaining holdup discrepancy requires
independent benchmark data and is not removed by the drag fix.

## Release boundary

Keep #5066 open until its acceptance criteria and exact-head CI are satisfied. Follow
#5036/#3298 for broader initialization and transient-model qualification. Passing this
fixture does not qualify all optional legacy solvers, boundary combinations or slugging regimes.

## Checkpoint restart

`TwoFluidPipe` serializes its adaptive timestep multiplier together with the accepted
pipe state. Restoring a checkpoint preserves a previously reduced multiplier rather
than resetting the integration history. Older checkpoints, which did not store
this field, initialize it to unity; their next step still obeys the current CFL
bound and rejection checks. This does not reset inventories, pressure, holdup or
simulation time.

The regression suite compares ten continued steps after a Java serialization
round-trip against an uninterrupted reference for gas, oil, water, gas/oil,
gas/water, oil/water and gas/oil/water fixtures. These short mechanical transport
checks establish restart consistency, not long-duration phase-change qualification.

### Source-free phase matrix

A separate horizontal 40 m, 0.2 m diameter, four-cell fixture uses SRK at
298.15 K and 60 bara, methane/n-decane/water as applicable, and 0.3 kg/s feed.
All seven nonempty combinations of gas, oil and water are checked after steady
convergence and throughout 10 s of IMEX stepping at 0.1 s. Acceptance requires
outlet flow within 1%, pressure drift below 600 Pa, absolute liquid-holdup drift
below 0.001, phase inventory bounds and phase-mass balance at every step.
Observed maxima across the matrix were 0.665% rate drift, 35.67 Pa pressure
drift and 0.000117 holdup drift. These checks freeze property refresh and exclude
phase transfer and thermal effects to isolate mechanical transport. They do not
qualify a flashing riser, moving outlet pressure or an arbitrary operating range.

### Common-window rate-step replay

On `0eacf4ba`, the 20,000 to 15,000 kg/h inlet step at 1 s gave the following
total outlet rates integrated over equal physical windows. Each value is the sum
of the three signed phase transfers divided by the duration of the window.

| Cells | Outer step (s) | Mean in (0, 1] s (kg/h) | Mean in (1, 2] s (kg/h) |
| ---: | ---: | ---: | ---: |
| 20 | 0.50 | 18,068.79 | 13,096.40 |
| 20 | 0.25 | 18,062.50 | 13,050.81 |
| 40 | 0.50 | 18,234.44 | 12,028.48 |

The step-refined 20-cell result changes by 0.35% in the second window. The 40-cell
result changes by 8.15% at the common outer step. The integration window removes one
source of sampling disagreement while preserving the spatial sensitivity.

## Independent vertical air–water measurements

The separate local-section screen uses near-atmospheric, upward air–water data from
Skopich et al. (2015), reproduced in [BOEM M15PC00007, Appendix IV, Figure 66(b)](https://www.boem.gov/sites/default/files/documents/oil-gas-energy/resource-evaluation/BOEM-Report-M15PC00007.pdf).
The nominal internal diameter is 50.8 mm and superficial water velocity is 0.05 m/s.
The measured values below are approximate plot readings. Measurement uncertainty and
the full facility boundary history were unavailable for this comparison.

The example flashes 79/21 mol% nitrogen/oxygen and water with SRK-CPA and adjusts
the feeds until both local superficial-volume targets at 1.01325 bara are matched to
a relative tolerance of 1e-6. It assumes 293.15 K and 10 μm roughness. The 0.2 m and
2 m vertical sections examine local developed gradients and sensitivity to gas expansion.
Their lengths are modelling assumptions. Pressure gradients use the first and last cell
centres. The code also reports the face-to-face gradient and the reconstructed range of
local phase superficial velocities. Run with the compiled Java classpath used above:

```sh
python examples/pipeline/compare_vertical_air_water_holdup.py --revision "$(git rev-parse HEAD)"
```

| Gas superficial speed (m/s) | Measured liquid holdup, approx. | NeqSim holdup | Measured gradient, approx. (Pa/m) | NeqSim gradient (Pa/m) | Native regime |
| ---: | ---: | ---: | ---: | ---: | --- |
| 10 | 0.14 | 0.184 | 1480 | 1342 | Slug |
| 20 | 0.065 | 0.115 | 800 | 7484 | Annular |
| 25 | 0.055 | 0.064 | 820 | 6298 | Annular |

These results use the 0.2 m, 20-cell case on Java revision
`0eacf4bac9646a0c2839aa95392e38d67ddb51b2`. Doubling the mesh from 10 to 20 cells
changed the three mean holdups by less than 0.000006. At 20 m/s in the 2 m section,
the mean rose to 0.131 and the centre gradient was 7641 Pa/m. In the 0.2 m section
at this speed, local gas superficial velocities were 19.71–19.99 m/s. The gravitational
contribution reconstructed from the phase inventories was 1132 Pa/m at 20 m/s and
640 Pa/m at 25 m/s. The large annular gradient overpredictions survive spatial
refinement. The laboratory data provide independent evidence for air–water closure.
Validation of the 2 km high-pressure hydrocarbon well still requires comparable
phase-resolved measurements.
