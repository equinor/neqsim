---
name: neqsim-process-modeling
description: "Process modeling and flowsheet construction patterns for NeqSim. USE WHEN: building executable NeqSim process simulations, ProcessSystem flowsheets, or runnable process models with streams, separators, compressors, heat exchangers, valves, pumps, distillation columns, recycles, adjusters, topology checks, result extraction, and engineering validation."
last_verified: "2026-07-18"
---

# NeqSim Process Modeling Skill

Build executable NeqSim process simulations from engineering descriptions. This skill
is the process-flowsheet layer between thermodynamic fluid setup and downstream
specialists such as mechanical design, safety, plant data, and reporting.

## Use When

- Building a `ProcessSystem` or `ProcessModel` flowsheet from a process description.
- Connecting equipment such as streams, separators, compressors, coolers, heaters,
  heat exchangers, pumps, valves, pipes, mixers, splitters, recycles, adjusters, and
  distillation columns.
- Extracting process results with units, compositions, duties, powers, phase splits,
  mass balances, energy balances, or equipment profiles.
- Preparing a steady-state process base case for mechanical design, relief sizing,
  flow assurance, dynamic simulation, plant-data comparison, or optimization.

## Core Workflow

1. **Define the fluid** using the EOS and component sequence from
   `neqsim-api-patterns`.
2. **Create feed streams** with explicit temperature, pressure, and flow units.
3. **Add equipment in topological order** to a `ProcessSystem`.
4. **Connect by outlet stream objects**, for example separator gas outlet to
   compressor inlet or valve outlet to downstream separator.
5. **Run once after assembly** unless recycle initialization requires a staged solve.
6. **Validate results** using conservation checks, phase sanity checks, equipment
   limits, and applicable standards.
7. **Report outputs with units** and include assumptions for missing design data.

## Modeling Choices

| Situation | Recommended Pattern |
|-----------|---------------------|
| Single train, linear or branched flowsheet | One `ProcessSystem` |
| Multiple areas with cross-area streams | Multiple `ProcessSystem` objects in a `ProcessModel` |
| Production / gathering / commingling / export **manifold** or inlet header | `Manifold` (`process.equipment.manifold.Manifold`) — NOT `Mixer`/`Splitter` |
| PFD/P&ID or unstructured text input | Use `neqsim-process-extraction` first |
| Distillation or fractionation | Load `neqsim-distillation-design` |
| Startup, shutdown, controllers, inventory dynamics | Load `neqsim-dynamic-simulation` |
| Turndown or control valve operability | Load `neqsim-controllability-operability` |
| Platform-scale separation/recompression | Load `neqsim-platform-modeling` |

**Manifolds:** always model a well/production/gathering/commingling/export
manifold (or an inlet/outlet header) with the `Manifold` class, not a plain
`Mixer` or `Splitter`. Add the routed inlet streams with `addStream(...)`. **A
manifold ALWAYS has split outlets — route downstream from a split stream, never
from `getMixedStream()`.** If the manifold feeds a single destination, give it
one split (`setSplitFactors([1.0])`) and route its `getSplitStream(0)`. For a
distributing manifold set `setSplitFactors([f0, f1, ...])` (fractions summing to
1) and read each branch with `getSplitStream(i)`. `getMixedStream()` returns only
the internal commingled stream (all inlets combined, before the split) — use it
for inspection, not for wiring downstream. The `Manifold` also carries
header/branch inner diameters (`setHeaderInnerDiameter`, `setBranchInnerDiameter`)
for hydraulics and mechanical design.

## Data Basis for an Optimization-Ready Model

A model built only to *run* has fixed operating points. A model built to
**optimize** additionally needs a bounded decision space, equipment constraints,
and an objective. Gather this basis up front (and record every assumed value):

- **Fluid & feed** — composition(s) + PVT/assay (C7+); feed rate, T, P, water
  cut/GOR per feed; EOS + mixing rule.
- **Geometry & hydraulics** — **line sizes** (ID, schedule/wall, length, elevation,
  roughness, insulation), **manifold/header sizes**, separator/scrubber dimensions
  (ID, T/T length, orientation, nozzle sizes), heat-exchanger area/UA.
- **Valves & chokes** — **control-valve Cv/Kv, rated travel, characteristic,
  opening**; **choke Cv-vs-opening (bean/trim)** for wells and let-down; ESD sizes.
- **Rotating equipment** — compressor maps (head/eff vs flow at several speeds) +
  design/max speed; pump curves (+ NPSHr); driver rating (GT/motor); anti-surge
  config (surge line, control-line margin, recycle-valve Cv).
- **Design limits → constraints** — separator design gas-load K + residence time;
  compressor rated power, surge/stonewall margins, max discharge T; pump power +
  NPSHa; line erosional-velocity limit; design P/T; valve max Cv; MAWP; PSV set P.
- **Decision space & control** — manipulable setpoints with **physical bounds**
  (stage pressures, temperatures, compressor discharge P or speed, split/routing);
  **compressor control mode** (solve-speed vs predictive — see
  `neqsim-agentic-process-optimization`); pre-wired adjusters (do not also optimize).
- **Objective & economics** — objective (max throughput / min power / max value /
  min emissions); product specs as constraints (RVP, dew point, cricondenbar,
  Wobbe); prices / power & fuel cost / CO2 price for value objectives.

Source geometry and Cv from the line list, valve/choke datasheets, and instrument
index; maps from vendor curve sheets; limits from datasheets + piping class. For
the governed enterprise checklist and readiness gates use
`enterprise-process-model-build-verify` (`target_fidelity="optimization_ready"`).

## Required Checks

- Temperatures and pressures use explicit units in setters.
- Fluids have a mixing rule before simulation.
- Branching streams use cloned fluids or well-defined equipment outlet streams.
- Every equipment item has a unique name inside the process.
- Recycles and adjusters are added after their connected equipment.
- **Pick the separator class by orientation, or set it explicitly.** Gas-capacity
  results depend on orientation because a horizontal vessel derates the gas area by
  the design liquid level (default 80% → gas area `(1−0.8)=0.2×`, a **5× over-read**
  of gas velocity / `getGasLoadFactor()` if used for a vertical vessel):
  - `Separator` and `ThreePhaseSeparator` default to **horizontal** — use for the
    horizontal 1st/2nd/3rd-stage separators (VA-tag).
  - `GasScrubber`, `GasScrubberSimple`, `NeqGasScrubber` (2-phase) and
    `ThreePhaseGasScrubber` (3-phase) default to **vertical** — prefer these for
    vertical scrubbers (VG-tag); their constructor calls `setOrientation("vertical")`.
  - Either way you can override with `separator.setOrientation("vertical"|"horizontal")`.
  Verified: with the correct orientation, `getGasLoadFactor()` matches a hand
  Souders-Brown `v·sqrt(ρg/(ρl−ρg))`. `setInternalDiameter()` itself propagates
  correctly through `run()` — the trap is orientation, not diameter.
- Every suction/export scrubber in a recompression/export-compression train has its
  liquid knock-out (`scrubber.getLiquidOutStream()`) closed back to the separator
  operating at the matching pressure — never leave it unconnected (it is silently
  dropped, under-counting oil/condensate recovery). See `neqsim-platform-modeling`
  Section 4 for the seed + TP-setter + `Recycle` pattern.
- **Overall mass balance MUST be verified before accepting any solution.** Sum the mass
  flow (`kg/hr`) of all feed streams and all product/export streams; the closure error
  must be `< 0.1 %` (`abs(sum_in - sum_out) / sum_in`). A larger imbalance means a stream
  was dropped (e.g. an unconnected scrubber liquid), a recycle did not converge, or a
  splitter fraction is wrong — fix the flowsheet and re-run; do NOT report results from an
  unbalanced model. For multi-area `ProcessModel`s, also confirm `plant.run()` converged.
- Results include the verified mass balance, expected pressure ordering, and physically
  reasonable phase splits.
- For industrial engineering use, assess every exact `method@version` with
  `EngineeringMethodQualificationRegistry`: require an independent benchmark, approved structured applicability
  envelope, intended use, controlled service inputs, uncertainty basis and explicit extrapolation policy. A converged
  calculation outside the envelope remains investigation evidence, not a qualified engineering result.
- Use `EngineeringNumericalHealthAnalyzer` to capture convergence, mass/energy closure, residual, and sensitivity
  evidence for every process state that governs an engineering decision. Required but absent evidence must remain
  `INCOMPLETE`; never replace unavailable closure data with zero.
- Use `Dexpi20XmlWriter` for native Plant/P&ID exchange and `Dexpi20ProcessModelWriter` for native Process/PFD/BFD
  exchange. A Proteus document with a changed header is not native DEXPI 2.0. Preserve the conformance report and still
  require a named-CAE round-trip before project qualification.
- Compressor, pump, heat exchanger, separator, and pipeline cases identify applicable
  standards through `neqsim-standards-lookup`.

## Related Skills

- `neqsim-api-patterns` — fluid setup, equipment APIs, and result extraction.
- `neqsim-input-validation` — pre-simulation physical bounds and component checks.
- `neqsim-troubleshooting` — flash and process convergence recovery.
- `neqsim-process-extraction` — JSON builder and route extraction from documents.
- `neqsim-notebook-patterns` — executable notebook structure and devtools setup.
