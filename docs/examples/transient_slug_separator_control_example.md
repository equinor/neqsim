---
title: "Transient Slug Separator Control Example"
description: "Interpret and run the deterministic inlet-flow disturbance and separator PID-control example, including its model boundaries and CSV output."
---

This page documents the
[Java example](https://github.com/equinor/neqsim/blob/master/examples/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java)
as it exists on current NeqSim `master`. The example applies a deterministic,
slug-like mass-flow disturbance directly to a separator and records the response
of its level and pressure controllers.

The example is a control-response demonstration. It is not a mechanistic
slug-flow, terrain, or transient-pipeline model, and its output is not a design
or operability acceptance result.

## What the example models

| Item | Source configuration |
| --- | --- |
| Fluid | Seven-component hydrocarbon fluid in `SystemSrkEos`, initialized at 288.15 K and 55 bara |
| Inlet | Base mass flow of 20 kg/s applied directly to the separator inlet |
| Disturbance | 60-second cycle, 15-second event, and peak increment of 80% of base flow |
| Separator | 2.2 m internal diameter and 7.0 m length |
| Liquid control | Outlet throttling valve driven toward a 0.50 liquid-level setpoint |
| Pressure control | Gas-outlet throttling valve driven toward a 52 bar transmitter setpoint |
| Time integration | 3,600 transient steps at 1 s per step, after the initial steady-state run |

During an event, `calculateSlugFlow(...)` raises the mass flow quickly and then
decays it exponentially. Between events it applies a deterministic sinusoidal
variation of up to 2%. The process flowsheet contains the inlet stream,
separator, two outlet valves, and the level and pressure transmitters. It does
not contain an inlet choke, a discretized pipe, or an elevation profile.

The source calls `process.runTransient()` after updating the inlet mass flow at
each step. Its controller parameters are illustrative tuning choices. They are
not validated settings for a particular vessel, valve, sensor, or production
system.

## Diagnostic pipe quantities are not pipe hydraulics

The result object also carries synthetic quantities named for an upstream pipe.
The source declares a 0.25 m diameter and a 3,000 m `pipeLength`, but does not
use `pipeLength` in a hydraulic calculation. It estimates:

- a nominal seven-bar pressure drop scaled by the square of the current-to-base
  mass-flow ratio;
- pipe outlet pressure from the separator gas-outlet pressure;
- pipe inlet pressure as that outlet pressure plus the scaled drop; and
- a heuristic slug velocity from mass flow, assumed phase densities, estimated
  liquid fraction, and the 0.25 m cross-sectional area.

These series are diagnostic constructions outside the NeqSim process
flowsheet. Do not use them to infer terrain-slug frequency, liquid holdup,
surge volume, pressure drop, or mechanical design limits. A pipeline or
multiphase-flow study requires an appropriate pipe model, geometry, boundary
conditions, fluid characterization, discretization, and validation evidence.

## Results and command-line options

`runSimulation()` returns the final separator state, a formatted statistics
summary, and 3,601 samples including the initial state. The histories cover
time, separator liquid level and pressure, gas-outlet pressure, inlet mass flow,
both valve openings, both setpoints, heuristic slug quantities, separator
liquid volume, and the diagnostic pipe pressures.

`main(String[] args)` supports two flags:

- `--series` prints CSV rows for time, liquid level, separator pressure,
  gas-outlet pressure, inlet flow, and both valve openings.
- `--noplot` suppresses the seven JFreeChart windows and is appropriate for a
  headless runner. It can be combined with `--series`.

Without `--noplot`, the plots show the last 600 one-second samples (10 minutes), not the complete 60-minute run. The repository stores no governed
reference output for this example, so this page does not assert particular
level or pressure extrema.

The source file lives in the repository `examples/` tree rather than the
standard Maven `src/main/java` tree. Run its `main` method with the NeqSim
project runtime classpath, for example from an IDE configured for the checkout.
The standard Maven test lifecycle does not currently execute this example. The
hermetic documentation contract protects the page's source constants,
structure, links, and model-boundary statements; it does not claim that the
3,600-step simulation was executed.

## Interpreting the demonstration

Use the output to inspect qualitative controller response to the imposed inlet
flow pattern. Before drawing an engineering conclusion, independently verify:

- that the fluid and flow disturbance represent the intended case;
- vessel geometry, residence-time basis, valve sizing, and controller action;
- pressure and level measurement ranges and units;
- numerical time-step sensitivity and initial conditions; and
- a mechanistic pipeline/slug model when upstream hydrodynamics matter.

The 0.50 and 52 bar setpoints are source inputs, not universal operating targets.
The calculated histories remain simulation evidence requiring engineering
review.

## Related documentation

- [Examples index](index.md)
- [Dynamic simulation](../process/dynamic-simulation.md)
- [Controllers](../process/controllers.md)
- [Separators](../process/equipment/separators.md)
- [Troubleshooting](../troubleshooting/index.md)
