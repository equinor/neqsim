# NeqSim API Changelog — Agent Notes

> **Purpose:** Track API changes that affect agent instructions, code patterns,
> and existing examples. Agents read this file to stay aware of breaking changes,
> deprecated methods, and new capabilities.
>
> Format: most recent changes at the top. Include the date, what changed,
> migration steps, and which agents/skills need updating.

---

## 2026-05-08 — MCP Server Quarkiverse Transport Refresh

### Summary

The standalone MCP server now follows the current Quarkiverse MCP Server docs:
Quarkus `3.33.1`, Quarkiverse MCP Server `1.12.0`, STDIO for local clients, and
`quarkus-mcp-server-http` for Streamable HTTP.

### Migration notes

- Replace the deprecated `quarkus-mcp-server-sse` artifact with
  `quarkus-mcp-server-http`.
- Use `http://localhost:8080/mcp` for Streamable HTTP clients.
- Older HTTP/SSE clients can still use `http://localhost:8080/mcp/sse`.
- MCP initialize examples now use protocol version `2025-11-25`.

---

## 2026-05-07 — Simulation-backed HAZOP MCP Workflow

### Summary

New `HAZOPStudyRunner` connects STID/P&ID-extracted HAZOP nodes to NeqSim
`ProcessSystem` simulations. MCP `runHAZOP` builds the baseline process, uses
`AutomaticScenarioGenerator` to create equipment-failure scenarios, runs copied
process models, maps failures to IEC 61882 guidewords/parameters, and returns
HAZOP rows, scenario evidence, quality gates, optional barrier-register handoff,
and report markdown.

### Agent Guidance

- Use `getExample("safety", "hazop-study")` for a complete input template.
- Use `getSchema("run_hazop", "input")` and `getSchema("run_hazop", "output")`
  for the contract.
- Treat generated rows as screening output. A chaired HAZOP team must verify
  nodes, causes, consequences, safeguards, barrier credit, and action ownership.
- Use `docs/safety/automated_hazop_from_stid.md` for the end-to-end STID/data/
  simulation/report workflow.

---

## 2026-05-XX — Process Safety Consequence Analysis & QRA Package

### Summary

New package `neqsim.process.safety` adds quantitative consequence analysis and
risk-quantification primitives covering API 521 / API 752 / NORSOK Z-013 /
CCPS QRA Guidelines / IEC 61025 / IEC 61882 / IEC 60812 / ASME UCS-66.

### New classes

| Subpackage | Classes |
|------------|---------|
| `depressurization` | `DepressurizationSimulator` (VU-flash transient blowdown, fire heat input, BDV sizing) |
| `mdmt` | `MDMTCalculator` (UCS-66 Curves A/B/C/D, UCS-66.1 stress reduction, API 579) |
| `dispersion` | `GaussianPlume`, `HeavyGasDispersion`, `ProbitModel`, `ToxicLibrary` |
| `fire` | `JetFireModel`, `PoolFireModel`, `VCEModel` (TNO multi-energy), `BLEVECalculator` |
| `risk.eta` | `EventTreeAnalyzer` (forward outcome frequencies, IEC 62502) |
| `risk.fta` | `FaultTreeAnalyzer`, `FaultTreeNode` (AND/OR/k-of-N + β-factor CCF, IEC 61025) |
| `hazid` | `HAZOPTemplate` (IEC 61882), `FMEAWorksheet` (IEC 60812, RPN=S·O·D) |
| `escalation` | `EscalationGraphAnalyzer` (domino/escalation screening) |
| `qra` | `ConsequenceAnalysisEngine` (IRPA roll-up, source-term JSON export) |
| `inherent` | `InherentSafetyEvaluator` (Substitute/Minimize/Moderate/Simplify) |
| `alarp` | `ALARPAuditReport` (ICAF vs VSL·GDF gross-disproportion) |
| `compliance` | `StandardsComplianceReport` (API 14C / NORSOK S-001 / IEC 61511) |

### β-factor semantics (FaultTreeAnalyzer)

`P_top_with_CCF = (1-β)·P_indep + β·max(P_basic_i)` — convex combination per
IEC 61508 Part 6. Note the directional effect differs by gate type: AND gates
see *increased* probability (CCF defeats redundancy), OR gates see *decreased*
probability (replaces independent disjunction with correlated single-event).

### New skills

- `neqsim-consequence-analysis`
- `neqsim-hazid-fmea-eta-fta`
- `neqsim-depressurization-mdmt`

### New agent

- `@analyze consequences and dispersion` — orchestrates the three skills above.

### New documentation

- `docs/safety/depressurization_per_API_521.md`
- `docs/safety/mdmt_assessment.md`
- `docs/safety/dispersion_and_consequence.md`
- `docs/safety/HAZOP.md`
- `docs/safety/FMEA.md`
- `docs/safety/event_fault_trees.md`

All classes are `Serializable` with `serialVersionUID`. 30 JUnit 5 tests under
`src/test/java/neqsim/process/safety/` pass.

---

## 2026-04-30 — Distillation Column MESH Residual Diagnostics

### Summary

`DistillationColumn` now records a scaled MESH residual vector after every run. The residual
diagnostics group material, equilibrium, summation, energy, and active specification equations.
A new `SolverType.MESH_RESIDUAL` entry uses inside-out initialization with Newton polishing and
keeps the residual diagnostics central to the solve path.

### New API

| Method | Description |
|--------|-------------|
| `getLastMeshResidualNorm()` | Full scaled MESH residual infinity norm |
| `getLastMeshMaterialResidualNorm()` | Component material residual norm |
| `getLastMeshEquilibriumResidualNorm()` | Fugacity-equilibrium residual norm |
| `getLastMeshSummationResidualNorm()` | Vapor/liquid summation residual norm |
| `getLastMeshEnergyResidualNorm()` | Tray energy residual norm |
| `getLastMeshSpecificationResidualNorm()` | Active specification residual norm |
| `getLastMeshResidualVector()` | Copy of the full residual vector |
| `setMeshResidualTolerance(double)` | Configure the optional MESH residual convergence tolerance |
| `setEnforceMeshResidualTolerance(boolean)` | Include the latest MESH residual norm in `solved()` |

### Agent Guidance

- Use `SolverType.MESH_RESIDUAL` when a task needs explicit MESH residual auditing.
- Do not describe `SolverType.NEWTON` as a full simultaneous MESH Newton solver; it is a
  tray-temperature correction accelerator.
- The MESH residual gate is disabled by default for backward compatibility. Enable it only when
  the task requires residual-vector convergence as part of the acceptance criteria.

### Affected Guidance

- `docs/process/equipment/distillation.md`
- `docs/wiki/distillation_column.md`
- `.github/skills/neqsim-distillation-design/SKILL.md`
## 2026-04-30 — CSP/PFCT Viscosity Parameter Fitting

### Summary

The PFCT/Pedersen viscosity model now exposes four tunable CSP viscosity
correction factors. `PhysicalProperties.setViscosityModel("CSP")` is an alias for
the standard PFCT/Pedersen viscosity model, and the four-parameter vector can be
read or written with `setCspViscosityParameters`, `setCspViscosityParameter`, and
`getCspViscosityParameters`. The longer `*CorrectionFactors` accessors are
equivalent.

### Agent Guidance

- Use `"PFCT"` or `"CSP"` for the standard Pedersen corresponding-states
  viscosity model; use `"PFCT-Heavy-Oil"` for the heavy-oil variant.
- The four CSP viscosity parameters default to `1.0`. Supplying values such as
  `0.6232`, `1.1507`, `1.0000`, `1.0000` preserves the external four-value order.
- For regression, add viscosity observations with `PVTRegression.addViscosityData(...)`
  and register `RegressionParameter.VISCOSITY_CSP_1` through
  `VISCOSITY_CSP_4`, or call `addCspViscosityRegressionParameters()`.
- Viscosity observations are in Pa s. Supported phase names are `gas`, `vapor`,
  `oil`, `liquid`, `aqueous`, and `water`.
- After TP flashes used for viscosity matching, call `fluid.initProperties()` so
  physical properties are initialized before viscosity is read.

### Reference

- Viscosity reference: [`docs/physical_properties/viscosity_models.md`](docs/physical_properties/viscosity_models.md)
- PVT regression guide: [`docs/pvtsimulation/fluid_characterization_mathematics.md`](docs/pvtsimulation/fluid_characterization_mathematics.md)

## 2026-04-30 — UniSim Reader: Operation Handler Registry

### Summary

The UniSim-to-NeqSim converter now centralizes operation mapping in a typed
`UniSimOperationHandler` registry. Each UniSim `TypeName` records a NeqSim target
type, strategy (`native`, `adapter`, `reference`, `control`, `column_internal`,
or `skip`), stream role, and explanatory note. Generated JSON includes
`_unisim_operation_mapping` so imported cases can audit whether operation types
were mapped to native NeqSim physics, adapters, reference objects, control
metadata, column internals, skipped utilities, or unsupported types.

### Agent Guidance

- Do not implement one UniSim-named NeqSim class for every UniSim operation.
  Keep physical equipment native to NeqSim and add UniSim compatibility through
  the converter registry and factory aliases.
- Add new UniSim type behavior by extending `UniSimOperationHandler` metadata
  first, including `strategy` and `stream_role`.
- Use `UniSimReader.is_material_stream_operation(type_name)` for topology
  reconstruction; do not add local `_NON_STREAM_OPS` lists.
- Preserve stream-carrying placeholder logic (`balanceop`, `virtualstreamop`,
  template interfaces) with `UnisimCalculator` until equations/properties are
  clear enough for a real NeqSim class and tests.
- Use `SpreadsheetBlock` for spreadsheet formula/import/export behavior when
  cells are extractable; logical/control operations should not create material
  topology edges.
- Validate changes with `python devtools/test_unisim_outputs.py`; the suite now
  checks handler strategy and `_unisim_operation_mapping` JSON summaries.

### Affected Guidance

- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim.reader.agent.md`
- `docs/process/unisim-to-neqsim-conversion.md`
- `devtools/README.md`
- `AGENTS.md`

## 2026-04-30 — UniSim Reader: Robust E300 Fluid-Package Extraction

### Summary

The UniSim-to-NeqSim conversion workflow now treats E300 full-fluid transfer as
a separate verification gate from structural process build and numerical stream
matching. `UniSimReader` can recover fluid packages when `comp.AcentricFactor`
is missing by using property-package vectors or the Edmister fallback from Tc,
Pc, and normal boiling point.

### Agent Guidance

- Request UniSim component critical temperature and normal boiling point in
  `C`, then convert to K.
- Request critical pressure in `kPa`, then convert to bara.
- Sanity-check exported E300 files with known components: methane should be
  about 190.7 K / 46.4 bara, water about 647.3 K / 221 bara.
- Report four separate gates: E300 exported, E300 loaded in the NeqSim build
  route, structural build status, and numerical stream verification status.
- Do not treat E300 fluid parity as full process parity. Virtual streams,
  spreadsheet/balance logic, template operations, compressor curves, and
  sub-flowsheet interface wiring can still dominate stream deviations.

### Affected Guidance

- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim.reader.agent.md`
- `docs/process/unisim-to-neqsim-conversion.md`
- `devtools/README.md`

## 2026-04-29 — Route-Level Piping Hydraulic Builder for STID Line Lists

### Summary

`PipingRouteBuilder` converts STID/E3D/P&ID/stress-isometric line-list rows into
a serial `ProcessSystem` with one `PipeBeggsAndBrills` unit per route segment.
It stores from/to nodes, straight length, hydraulic diameter, wall thickness,
roughness, elevation change, and K-value minor losses. Minor losses are converted
to equivalent length ratio by `K / f_D`, with default Darcy friction factor
`0.02` and a configurable `setMinorLossFrictionFactor(...)` assumption. Routes
can be run standalone with `build(feedStream)` or inserted into a larger plant
model with `addToProcessSystem(process, inletStream)`, which returns the last
pipe outlet stream for downstream equipment.

### New API

| Class | Package | Purpose |
|-------|---------|---------|
| `PipingRouteBuilder` | `neqsim.process.equipment.pipeline.routing` | Build route-level pipe hydraulic models from line-list tables |
| `PipingRouteBuilder.RouteSegment` | same | Route segment metadata, total K, total equivalent L/D, generated pipe name |
| `PipingRouteBuilder.MinorLoss` | same | K-value fitting/valve loss converted to equivalent L/D |

Important methods:

- `build(StreamInterface inletStream)` creates a standalone route `ProcessSystem`.
- `addToProcessSystem(ProcessSystem process, StreamInterface inletStream)` adds
  only the generated pipe units to an existing process and returns the final
  pipe outlet stream.
- `addToProcessSystem(ProcessSystem process, StreamInterface inletStream,
  String sourceEquipmentName, String sourcePortName)` preserves explicit source
  equipment/port metadata when the route starts from an upstream equipment outlet.

### Agent Usage

- For STID, E3D, P&ID, or stress-isometric tasks where the source has line-list
  rows with lengths, sizes, fittings, valves, elevations, and equipment nodes,
  use `PipingRouteBuilder` instead of hand-assembling individual pipes.
- In full plant simulations, pass an upstream `StreamInterface` into
  `addToProcessSystem(...)` and feed the returned outlet stream into downstream
  process equipment constructors.
- Preserve source document/page/row references in the task notes and export
  `route.toJson()` into task results for later reuse.
- Use looped-network tools for branched or ring-main hydraulics; this builder is
  for serial routes and serial branches.

### Reference

- Full guide: [`docs/process/piping_route_builder.md`](docs/process/piping_route_builder.md)
- Focused tests: `PipingRouteBuilderTest`

### Skills/Agents Updated

- `neqsim-api-patterns`
- `neqsim-process-extraction`
- `neqsim-stid-retriever`
- `neqsim-technical-document-reading`

## 2026-04-27 — Flash Warm-Start: New `ProcessSystem.setUseFlashWarmStart()` API

### Summary

Warm-start K-values are now exposed at the `ProcessSystem` level as a scoped,
opt-in flag. When enabled, the iterative TPflash inside every fluid evaluation
re-uses the previously converged K-values as the initial estimate instead of
seeding from Wilson on every call. The flag is applied via
`ThermodynamicModelSettings.setUseWarmStartKValues(true)` for the duration of
`run(UUID)` and restored afterwards (try/finally), so it never leaks to other
code on the same thread.

### New API on `ProcessSystem`

| Method | Description |
|--------|-------------|
| `setUseFlashWarmStart(boolean)` | Enable/disable warm-start for the duration of `run()` |
| `isUseFlashWarmStart()` | Returns the current setting |

### New API on `ProcessModel`

| Method | Description |
|--------|-------------|
| `setUseFlashWarmStart(boolean)` | Propagates the warm-start flag to every registered `ProcessSystem` and applies to any area added afterwards |
| `isUseFlashWarmStart()` | Returns the model-level setting |

**Default:** `false` (historical behaviour preserved). Recycle-heavy
flowsheets are sensitive to the flash trajectory and warm-start can shift the
converged fixed point — opt in deliberately.

### Usage

```java
// Single ProcessSystem
ProcessSystem process = new ProcessSystem();
// ... build flowsheet ...
process.setUseFlashWarmStart(true);
process.run();   // 10–20% wall-time reduction on recycle-heavy flowsheets

// Multi-area ProcessModel
ProcessModel plant = new ProcessModel();
plant.add("separation", separationArea);
plant.add("compression", compressionArea);
plant.setUseFlashWarmStart(true); // applies to both areas
plant.run();
```

### Inner-loop benefit (automatic, no opt-in needed)

`PHflash`, `PSFlash`, `PVflash`, `PUflash`, `TVflash`, `PVFflash`,
`PVrefluxflash`, `PHsolidFlash`, `OptimizedVUflash`, `ImprovedVUflashQfunc`,
`QfuncFlash`, `THflash`, `TSFlash`, `TUflash`, `VHflashQfunc`, `VSflash`,
`VUflashQfunc`, and `TVfractionFlash` already use a cold-first-then-warm
pattern internally (since 2026-04-21 / 2026-04-27). The first inner TPflash
runs cold (Wilson seed) to guard against stale K, all subsequent Newton
iterations re-use the previous step's converged K. This benefit is automatic
and does not require any flag.

### Skills/Agents to update

- `neqsim-troubleshooting` — mention `setUseFlashWarmStart(true)` as a
  performance lever for recycle-heavy flowsheets.
- `neqsim-platform-modeling` — recommend opt-in for large topside models
  with multiple recycles.

### Reference

- Full guide: [`docs/development/performance_tuning.md`](docs/development/performance_tuning.md)
- PRs: #2124, #2125

---

## 2026-04-20 — Gas Scrubber Mechanical Design: Internals Configuration & Conformity Checking

### Summary

Major expansion of `GasScrubberMechanicalDesign` with ~40 new public methods for
configuring scrubber internals (inlet devices, demisting cyclones, mesh pads, vane
packs, drain pipes, level alarms) and a new conformity-checking package for
automated design verification against an operator-specific technical requirement.
Geometry fields moved from `Separator` to `SeparatorMechanicalDesign` so physical
dimensions are owned by the mechanical design layer. Bug fixes for autoSize liquid
level and drainage-head formula.

### Bug Fixes

| Bug | File(s) | Impact |
|-----|---------|--------|
| `autoSize()` used runtime `liquidLevel` (0 before sim) instead of `designLiquidLevelFraction` | `SeparatorMechanicalDesign` | Auto-sized vessel had wrong liquid height |
| Drainage-head formula had spurious ×100 factor | `GasScrubberMechanicalDesign` | Drainage head was 100x too large |
| Geometry fields on `Separator` could go stale relative to `MechanicalDesign` | `Separator`, `SeparatorMechanicalDesign` | Inconsistent diameter/length after design changes |

### Architecture Changes

| Change | Details |
|--------|---------|
| **Geometry ownership moved to MechanicalDesign** | `innerDiameter` and `tantanLength` now live on `SeparatorMechanicalDesign`; `Separator` delegates via computed getters. Eliminates dual-state inconsistency. |
| **`GasScrubber.initMechanicalDesign()` preserves geometry** | Re-initialising no longer resets previously configured internals. |
| **Derived fields replaced with computed methods** | Gas/liquid area fractions, velocities etc. are computed on the fly rather than stored. |

### New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `ConformityResult` | `mechanicaldesign.separator.conformity` | Single rule check result (PASS/WARNING/FAIL, 90% warning threshold) |
| `ConformityReport` | `mechanicaldesign.separator.conformity` | Collection of results with `isConforming()`, `toTextReport()` |
| `ConformityRuleSet` | `mechanicaldesign.separator.conformity` | Abstract base plus operator-specific rule sets for K-factor, inlet momentum, drainage head, cyclone-dp-to-drain, and mesh-K checks |

### New Methods on `GasScrubberMechanicalDesign`

| Method | Description |
|--------|-------------|
| `setInletDevice(String)` | Case-insensitive inlet device selection (e.g. `"schoepentoeter"`, `"inlet_vane"`) |
| `setInletCyclones(n, diam)` | Configure inlet cyclone count and diameter |
| `setDemistingCyclones(n, diam, deckElev)` | 3-arg: cyclone count, diameter, deck elevation |
| `setDemistingCyclones(n, diam, deckElev, length)` | 4-arg: adds cyclone length |
| `setMeshPad(area, thickness)` | Mesh pad area (m²) and thickness (mm) |
| `setVanePack(area)` | Vane pack area (m²) |
| `setDrainPipeDiameterM(diam)` | Drain/down-comer pipe diameter |
| `setLaLLElevationM()` / `setLaLElevationM()` / `setLaHElevationM()` / `setLaHHElevationM()` | Level alarm elevations |
| `setHhllElevationM()` | High-high liquid level elevation |
| `setCycloneDeckElevationM()` / `setCycloneLengthM()` / `setCycloneEulerNumber()` / `setCycloneDpToDrainPct()` | Cyclone parameters |
| `setConformityRules(String)` | Load a conformity rule set by key |
| `checkConformity()` | Run all loaded rules, returns `ConformityReport` |
| `getConformityStandard()` | Get currently loaded standard name |
| `toTextReport()` | Full text report of internals configuration and conformity |
| `getResponse()` | Structured `SeparatorMechanicalDesignResponse` with all design data |

### Usage Example

```java
GasScrubber scrubber = new GasScrubber("V-301", feedStream);
scrubber.setInternalDiameter(2.9);
scrubber.setLength(4.23);
ProcessSystem process = new ProcessSystem();
process.add(feedStream);
process.add(scrubber);
process.run();

scrubber.initMechanicalDesign();
GasScrubberMechanicalDesign design =
    (GasScrubberMechanicalDesign) scrubber.getMechanicalDesign();
design.setMaxOperationPressure(110.0);
design.setInletDevice("schoepentoeter");
design.setDemistingCyclones(256, 0.110, 3.287, 0.943);
design.setMeshPad(6.605, 150.0);
design.setDrainPipeDiameterM(0.2032);
design.setConformityRules("operator-specific-key");
design.calcDesign();

ConformityReport report = design.checkConformity();
System.out.println(report.toTextReport());
System.out.println("Conforming: " + report.isConforming());
```

### Test Classes

- Operator-specific scrubber design tests — 4 tests covering full internals configuration and conformity checking
- `SeparatorTest` — 10 existing tests (all pass, no regressions)

### Affected Skills / Agents

- `neqsim-api-patterns` — Add scrubber internals configuration pattern
- `neqsim-standards-lookup` — Add operator-specific conformity rules to standards database
## 2026-04-22 — PT Phase Envelope: NaN Branch-Break Sentinels + Structured Segments API

### Summary

Two improvements to `PTPhaseEnvelopeMichelsen` (the default PT phase envelope
tracer). Both are backward-compatible. Full docs at
`docs/pvtsimulation/phase_envelope_guide.md`.

### Bug Fix — NaN branch-break sentinels

The Michelsen tracer uses a two-pass algorithm and can cross several critical
points. Previously, points from disjoint envelope segments were all appended
to the same flat `dewT` / `bubT` arrays with no separator, causing plotters
(e.g. matplotlib) to draw spurious straight lines across the two-phase region
at every branch transition.

Fix: a `NaN` sentinel is now inserted into all ten per-point arrays
(`dewT`, `dewP`, `dewH`, `dewDens`, `dewS`, `bubT`, `bubP`, `bubH`, `bubDens`,
`bubS`) at every branch transition (pass restart, first critical point flip,
second critical point flip). Matplotlib renders `NaN` as a polyline gap.

**Migration for consumers that iterate the flat arrays:** skip `NaN` entries
or use the new structured segment API below. The cricondentherm/cricondenbar
getters and point counts (excluding `NaN`) are unchanged.

### New Feature — Structured segments API

New class:
`neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops.EnvelopeSegment`

- Immutable polyline with `PhaseType` enum (`DEW` or `BUBBLE`) and T/P/H/density/entropy arrays.
- Never contains `NaN`.

New accessor on `PTPhaseEnvelopeMichelsen`:

```java
List<EnvelopeSegment> segments = michelsen.getSegments();
```

New convenience method on `ThermodynamicOperations`:

```java
List<EnvelopeSegment> segments = ops.getEnvelopeSegments();
// Returns empty list for legacy (non-Michelsen) envelope implementations.
```

Python usage:

```python
for seg in ops.getEnvelopeSegments():
    T = list(seg.getTemperatures())
    P = list(seg.getPressures())
    plt.plot([t - 273.15 for t in T], P, label=str(seg.getPhaseType()))
```

### Agent / Skill Updates

- `neqsim-api-patterns` — prefer `getEnvelopeSegments()` over flat arrays for new code.
- `neqsim-troubleshooting` — "kinks/teleports in phase envelope plot" → use segments API or skip `NaN` in flat arrays.

---

## 2026-04-17 — Diffusion Coefficient Model Fixes and Validation

### Summary

Major bug fixes and accuracy improvements to all diffusion coefficient models
(gas and liquid). Added 6 new model names to `setDiffusionCoefficientModel()`.
All models validated against published experimental data (Marrero & Mason 1972,
Poling 2001). Full docs at `docs/physical_properties/diffusivity_models.md`.

### Bug Fixes

| Bug | File(s) | Impact |
|-----|---------|--------|
| Fuller constant 10x too large (`1.013e-2` → `1.013e-3`) | `FullerSchettlerGiddingsDiffusivity` | Gas D values were 10x too high |
| Critical volume unit conversion (`Vc * 1e3` removed) | `FullerSchettlerGiddingsDiffusivity`, `SiddiqiLucasMethod`, `WilkeChangDiffusivity`, `TynCalusDiffusivity`, `HaydukMinhasDiffusivity` | Fallback molar volumes were 1000x too large |
| HaydukMinhas volume formula inverted (`Vc * 1e6 / 0.285` → `0.285 * Vc^1.048`) | `HaydukMinhasDiffusivity` | Completely wrong liquid D values |
| Gas LJ parameters from DB unsuitable for diffusion | `Diffusivity` (gas base class) | Chapman-Enskog/Wilke-Lee gave ~60% error |

### New Features

- **Diffusion-specific LJ parameter table** — ~35 common components from Poling (2001)
  and Bird, Stewart, Lightfoot (2002). Automatically overrides DB LJ parameters for
  gas diffusion calculations in Chapman-Enskog and Wilke-Lee models.
- **`"Chapman-Enskog"` model name** — Added to `setDiffusionCoefficientModel()` for
  explicit selection of the base Chapman-Enskog gas diffusion model.

### New/Updated Model Names for `setDiffusionCoefficientModel()`

| Model String | Phase | Class | Status |
|---|---|---|---|
| `"Chapman-Enskog"` | Gas | `Diffusivity` | **NEW** |
| `"Wilke Lee"` | Gas | `WilkeLeeDiffusivity` | Existing (fixed) |
| `"Fuller-Schettler-Giddings"` | Gas | `FullerSchettlerGiddingsDiffusivity` | Existing (fixed) |
| `"Siddiqi Lucas"` | Liquid | `SiddiqiLucasMethod` | Existing (fixed) |
| `"Wilke-Chang"` | Liquid | `WilkeChangDiffusivity` | Existing (fixed) |
| `"Tyn-Calus"` | Liquid | `TynCalusDiffusivity` | Existing (fixed) |
| `"Hayduk-Minhas"` | Liquid | `HaydukMinhasDiffusivity` | Existing (fixed) |
| `"CSP"` | Gas/Liquid | `CorrespondingStatesDiffusivity` | Unchanged |
| `"High Pressure"` | Liquid | `HighPressureDiffusivity` | Unchanged |
| `"Alkanol amine"` | Aqueous | `AmineDiffusivity` | Unchanged |

### Validation Results (298 K, 1 atm)

Gas models (vs Marrero & Mason 1972, Poling 2001):
- CH₄-N₂: Chapman-Enskog 0.7%, Fuller 2.0%, Wilke-Lee 5.0%
- CO₂-N₂: Chapman-Enskog 7.4%, Fuller 2.7%, Wilke-Lee 0.3%

Liquid models (CO₂ in water vs Poling 2001):
- Wilke-Chang 10%, Hayduk-Minhas 15%, Siddiqi-Lucas 31%

### Test Classes

- `DiffusivityExperimentalValidationTest` — 13 tests validating all models against experimental data
- `AllDiffusivityModelsTest` — 17 tests (existing, all pass)
- `DiffusivityModelsTest` — 15 tests (existing, all pass)

### Affected Skills

- `neqsim-api-patterns` — Update diffusivity model examples
- `neqsim-flow-assurance` — May reference diffusion models for corrosion/mass transfer

---

## 2026-04-17 — Process Optimization Enhancements: Rate-Based Absorber, SQP Optimizer, Flow Correlations, Multi-Variable Adjuster

### Summary

Five new classes and one enum addition for improved process simulation fidelity
and optimization capability. These close key gaps identified in a reservoir-to-market
process optimization review comparing NeqSim to commercial simulators.

### New Classes

#### 1. RateBasedAbsorber (`neqsim.process.equipment.absorber`)

Rate-based (non-equilibrium) absorption column with rigorous mass transfer
calculations. Two mass transfer correlations and three enhancement factor models.

| Method | Description |
|--------|-------------|
| `setMassTransferModel(MassTransferModel)` | `ONDA_1968` or `BILLET_SCHULTES_1999` |
| `setEnhancementModel(EnhancementModel)` | `NONE`, `HATTA_PSEUDO_FIRST_ORDER`, `VAN_KREVELEN_HOFTIJZER` |
| `setColumnDiameter(double)` | Column diameter in metres |
| `setPackedHeight(double)` | Packed height in metres |
| `setPackingSpecificArea(double)` | Packing specific area (m2/m3) |
| `setPackingVoidFraction(double)` | Packing void fraction |
| `setPackingNominalSize(double)` | Packing nominal size (m) |
| `setPackingCriticalSurfaceTension(double)` | Packing critical surface tension (N/m) |
| `setReactionRateConstant(double)` | Pseudo-first-order reaction rate constant (1/s) |
| `setStoichiometricRatio(double)` | Stoichiometric ratio for VKH model |
| `setBilletSchultesConstants(double, double)` | Cl and Cv for Billet-Schultes |
| `getOverallKGa()` / `getOverallKLa()` | Overall mass transfer coefficients |
| `getWettedArea()` | Wetted area from correlation |
| `getHeightOfTransferUnit()` / `getNumberOfTransferUnits()` | HTU/NTU |
| `getStageResults()` | List of `StageResult` with per-stage detail |

**Extends:** `SimpleAbsorber`
**Test:** `RateBasedAbsorberTest` (6 tests)

#### 2. SQPoptimizer (`neqsim.process.util.optimizer`)

Full Sequential Quadratic Programming NLP solver with damped BFGS Hessian
update, active-set QP sub-problem, L1 exact penalty merit function, and
Armijo backtracking line search.

| Method | Description |
|--------|-------------|
| `setObjectiveFunction(ObjectiveFunc)` | Set objective f(x) |
| `addEqualityConstraint(ConstraintFunc)` | Add c(x) = 0 constraint |
| `addInequalityConstraint(ConstraintFunc)` | Add h(x) >= 0 constraint |
| `setVariableBounds(double[], double[])` | Lower/upper bounds on variables |
| `solve(double[])` | Solve from initial point; returns `OptimizationResult` |
| `setMaxIterations(int)` / `setTolerance(double)` | Convergence controls |
| `setFiniteDifferenceStep(double)` | Step for central-difference gradients |

**Inner interfaces:** `ObjectiveFunc`, `ConstraintFunc`
**Inner class:** `OptimizationResult` — `isConverged()`, `getOptimalPoint()`, `getOptimalValue()`, `getIterations()`, `getKktError()`
**Enum added:** `ProcessOptimizationEngine.SearchAlgorithm.SEQUENTIAL_QUADRATIC_PROGRAMMING`
**Test:** `SQPoptimizerTest` (5 tests)

#### 3. PipeHagedornBrown (`neqsim.process.equipment.pipeline`)

Hagedorn-Brown (1965) empirical holdup correlation for vertical/near-vertical
multiphase pipe flow. Best suited for oil production wells.

| Method | Description |
|--------|-------------|
| `setLength(double)` / `setDiameter(double)` / `setAngle(double)` | Geometry |
| `setNumberOfIncrements(int)` | Discretization segments |
| `setWallRoughness(double)` | Absolute roughness (m) |
| `getOutletSuperficialVelocity()` | Gas superficial velocity at outlet |
| `getLiquidHoldupProfile()` | `double[]` holdup along pipe |
| `getFlowPatternDescription()` | Descriptive string |
| `getPressureProfile()` / `getTemperatureProfile()` | `double[]` profiles |

**Extends:** `Pipeline`
**Test:** `PipeHagedornBrownTest` (3 tests)

#### 4. PipeMukherjeeAndBrill (`neqsim.process.equipment.pipeline`)

Mukherjee-Brill (1985) all-inclination holdup and friction correlation. Handles
horizontal, uphill, and downhill flows with flow pattern detection.

| Method | Description |
|--------|-------------|
| `getFlowPattern()` | Returns outlet flow pattern as String: STRATIFIED, SLUG, ANNULAR, BUBBLE, SINGLE_PHASE |
| `getFlowPatternEnum()` | Returns `FlowPattern` enum |
| `getLiquidHoldup()` | Scalar outlet liquid holdup |
| `getFlowPatternProfile()` | `List<String>` pattern at each increment |
| Same geometry methods as PipeHagedornBrown | — |

**Extends:** `Pipeline`
**Test:** `PipeMukherjeeAndBrillTest` (5 tests)

#### 5. MultiVariableAdjuster (`neqsim.process.equipment.util`)

Simultaneous multi-variable adjuster using damped successive substitution.
Solves N equations in N unknowns (target specifications) by adjusting N
process variables simultaneously.

| Method | Description |
|--------|-------------|
| `addAdjustedVariable(ProcessEquipmentInterface, String, String)` | Variable to manipulate |
| `addTargetSpecification(ProcessEquipmentInterface, String, double, String)` | Target to satisfy |
| `setVariableBounds(int, double, double)` | Bounds on adjusted variable |
| `setMaxIterations(int)` / `setTolerance(double)` | Convergence controls |
| `isConverged()` / `getIterations()` / `getMaxResidual()` | Solution status |
| `getNumberOfVariables()` | Number of adjusted variables |

**Test:** `MultiVariableAdjusterTest` (4 tests)

### Agents/Skills Affected

- `neqsim-api-patterns` skill — add rate-based absorber, SQP optimizer, flow correlation, multi-variable adjuster patterns
- `neqsim-capability-map` skill — update mass transfer, optimization, and multiphase flow sections
- `@solve.process` agent — can now use RateBasedAbsorber and MultiVariableAdjuster
- `@mechanical.design` agent — PipeHagedornBrown/PipeMukherjeeAndBrill for well tubing design

---

## 2026-04-17 — Universal Capacity Constraints for All Equipment

### Summary

Capacity constraint methods are now available on ALL 144+ equipment types via
`ProcessEquipmentBaseClass`. Previously, only ~60 equipment classes implementing
`CapacityConstrainedEquipment` could participate in bottleneck analysis and
optimization. Now any equipment can have constraints added at runtime.

Six new capacity strategies were added (18 total built-in), covering reactors,
power generation, subsea equipment, filters/adsorbers, electrolyzers, and wells.

### New API on ProcessEquipmentBaseClass

All equipment now inherits these methods (no need to cast or check interface):

| Method | Returns | Description |
|--------|---------|-------------|
| `addCapacityConstraint(CapacityConstraint)` | `void` | Add a constraint to any equipment |
| `getCapacityConstraints()` | `Map<String, CapacityConstraint>` | Get all constraints (unmodifiable) |
| `getBottleneckConstraint()` | `CapacityConstraint` | Most limiting enabled constraint |
| `isCapacityExceeded()` | `boolean` | Any enabled constraint violated |
| `isHardLimitExceeded()` | `boolean` | Any HARD constraint exceeded |
| `getMaxUtilization()` | `double` | Highest utilization ratio (fraction) |
| `getMaxUtilizationPercent()` | `double` | Highest utilization as percentage |
| `getAvailableMargin()` | `double` | Headroom on bottleneck (fraction) |
| `getAvailableMarginPercent()` | `double` | Headroom as percentage |
| `isNearCapacityLimit()` | `boolean` | Any constraint above warning threshold |
| `getUtilizationSummary()` | `Map<String, Double>` | All constraint utilizations |
| `getConstraintEvaluationReport()` | `String` | Multi-line diagnostic report |

### Updated ProcessSystem Methods

These methods now iterate over ALL equipment (not just `CapacityConstrainedEquipment`):

- `findBottleneck()` — returns `BottleneckResult` for the most-utilized equipment
- `isAnyEquipmentOverloaded()` — checks all equipment for capacity exceedance
- `isAnyHardLimitExceeded()` — checks all equipment for HARD limit violations
- `getCapacityUtilizationSummary()` — map of all equipment utilizations
- `getEquipmentNearCapacityLimit()` — list of equipment near their limits

### New Capacity Strategy Classes (6 new, 18 total)

| Class | Equipment Types |
|-------|----------------|
| `ReactorCapacityStrategy` | GibbsReactor, PlugFlowReactor, StirredTankReactor |
| `PowerGenerationCapacityStrategy` | GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem |
| `SubseaEquipmentCapacityStrategy` | SubseaWell, SubseaTree |
| `FilterAdsorberCapacityStrategy` | Filter, SulfurFilter, CharCoalFilter, SimpleAdsorber |
| `ElectrolyzerCapacityStrategy` | Electrolyzer, CO2Electrolyzer |
| `WellFlowCapacityStrategy` | WellFlow |

### Migration Notes

- **No breaking changes** — existing code using `CapacityConstrainedEquipment` still works
- For new code, prefer using `ProcessEquipmentInterface` methods directly
- `ProcessEquipmentBaseClass.initializeDefaultConstraints()` is a protected hook
  for subclasses to set up default constraints (called lazily)
- Constraint map is `transient` (not serialized) — reconstructed on first access

### Affected Skills

- `neqsim-api-patterns` — add universal constraint patterns
- `neqsim-capability-map` — update optimization capabilities

---

## 2025-07-14 — Dynamic Process Simulation Enhancements (PR #2064)

### Summary

Comprehensive audit and fix of 29 bugs across the `fluidmechanics` package where
methods that accept a `phase` or `phaseNum` parameter internally used phase-0 defaults
for Reynolds number, velocity, or friction factor calculations. This caused all
liquid-phase (phase 1) transport coefficients to be computed with gas-phase values.

### What Changed

**Round 1 (13 bugs):** Critical fixes in core solver and flow nodes:
- `NonEquilibriumFluidBoundary`: Prandtl number missing `/getMolarMass()`, heat transfer solver step clamping
- `ReactiveKrishnaStandartFilmModel`: Per-component enhancement factor scaling
- `KrishnaStandartFilmModel`: 3 NaN guards (Schmidt, phi matrix, mass transfer inverse)
- `TwoPhaseFixedStaggeredGridSolver`: `initFinalResults` phase param, sign error, zero guards, velocity/enthalpy phase fixes
- `InterphaseStratifiedFlow`: Liquid mass transfer floor, friction uses `phase` param, heat/mass transfer use `getReynoldsNumber(phaseNum)`
- `TwoPhaseFlowNode`: Hydraulic diameter guards, convergence fix, Reynolds viscosity guard, `interphaseFrictionFactor[1]` uses phase 1

**Round 2 (6 bugs):**
- `TwoPhaseFixedStaggeredGridSolver`: Component conservation uses `getVelocity(phaseNum)`
- `TwoPhaseFixedStaggeredGridSolver`: Latent heat enthalpy zero-moles guard (2 locations)
- `InterphaseDropletFlow`: Friction factor uses `phase` parameter
- `InterphaseSlugFlow`: Friction factor uses `phase` parameter
- `InterphaseStratifiedFlow`: `calcWallMassTransferCoefficient` uses `getReynoldsNumber(phaseNum)`

**Round 3 (10 bugs):**
- `InterphaseTransportCoefficientBaseClass`: Base class `calcInterPhaseFrictionFactor` now uses `calcWallFrictionFactor(phase, node)` instead of hardcoded 0
- `MultiPhaseFlowNode`: `interphaseFrictionFactor[1]` uses phase 1 (same as TwoPhaseFlowNode fix)
- `InterphaseDropletFlow`: `calcWallMassTransferCoefficient` uses `getReynoldsNumber(phaseNum)`
- `InterphaseSlugFlow`: Both `calcInterphaseHeatTransferCoefficient` and `calcWallMassTransferCoefficient` use `getReynoldsNumber(phaseNum)`
- `InterphasePipeFlow` (one-phase): All 3 methods use `getReynoldsNumber(phase)` consistently; turbulent branches use `getVelocity(phaseNum)`
- `InterphaseStirredCellFlow`: Both `calcInterphaseHeatTransferCoefficient` and `calcWallMassTransferCoefficient` use `getReynoldsNumber(phaseNum)`

### Files Changed

| File | Change |
|------|--------|
| `NonEquilibriumFluidBoundary.java` | Prandtl fix, step clamping, df==0 guard |
| `ReactiveKrishnaStandartFilmModel.java` | Enhancement factor diagonal scaling |
| `KrishnaStandartFilmModel.java` | 3 NaN guards |
| `TwoPhaseFixedStaggeredGridSolver.java` | Phase params, sign fix, zero guards |
| `InterphaseStratifiedFlow.java` | Phase params for Re, friction, mass transfer |
| `TwoPhaseFlowNode.java` | Hydraulic diameter, convergence, friction[1] |
| `InterphaseDropletFlow.java` | Phase params for friction and Re |
| `InterphaseSlugFlow.java` | Phase params for friction, heat, mass transfer |
| `InterphaseTransportCoefficientBaseClass.java` | Base class friction uses phase param |
| `MultiPhaseFlowNode.java` | `interphaseFrictionFactor[1]` phase fix |
| `InterphasePipeFlow.java` | Consistent Re and velocity phase usage |
| `InterphaseStirredCellFlow.java` | Phase params for heat and mass transfer |

### Impact

Liquid-phase mass transfer, heat transfer, and friction factor calculations now
use the correct liquid-phase Reynolds number and velocity. This significantly
affects non-equilibrium pipeline simulations where condensation occurs — the
liquid film transport was previously computed with gas-phase properties.

### Migration

No API changes. All fixes are internal corrections. Results from two-phase
non-equilibrium simulations will differ from previous versions — this is the
**correct** behavior. Previous results had incorrect liquid-phase transport.

---

## 2026-04-17 — InterphaseDropletFlow: Corrected Mass/Heat Transfer for Dispersed Flow

### Summary

Fixed and enhanced `InterphaseDropletFlow` — the interphase transport coefficient
calculator for droplet (mist) and bubble flow regimes. The previous implementation
erroneously reused stratified flow (Yih-Chen) correlations via copy-paste. The new
implementation uses physics-appropriate correlations for dispersed particles.

### What Changed

1. **Bug fix:** Mass and heat transfer now use the **particle diameter** (droplet/bubble)
   as the characteristic length, not the pipe hydraulic diameter. This is the fundamental
   difference between dispersed and stratified flow transport.

2. **Ranz-Marshall correlation** for continuous phase: `Sh = 2 + 0.6·Re_p^0.5·Sc^0.33`
   (both mass and heat transfer).

3. **Kronig-Brink model** for dispersed phase interior: `Sh = 17.66` (steady-state limit
   for internally circulating spheres).

4. **Abramzon-Sirignano (1989) extended film model** — optional correction for
   evaporating droplets that accounts for Stefan flow (blowing) at the droplet surface.
   Enabled via `setUseAbramzonSirignano(true)` and `setSpaldingMassTransferNumber(B_M)`.

5. **Particle diameter resolution** from `DropletFlowNode.getAverageDropletDiameter()`
   and `BubbleFlowNode.getAverageBubbleDiameter()`.

### New/Changed Files

| File | Change |
|------|--------|
| `InterphaseDropletFlow.java` | **Rewritten** — Ranz-Marshall, Kronig-Brink, Abramzon-Sirignano |
| `InterphaseDropletFlowMassTransferTest.java` | **NEW** — 9 tests covering correlations and limits |
| `condensation_pipeline_equilibrium_vs_nonequilibrium.ipynb` | **NEW** — Example notebook comparing equilibrium vs non-equilibrium pipeline condensation |
| `docs/fluidmechanics/droplet_flow_correlations.md` | **NEW** — Full documentation of dispersed flow correlations |

### New API Methods on `InterphaseDropletFlow`

| Method | Description |
|--------|-------------|
| `setUseAbramzonSirignano(boolean)` | Enable/disable blowing correction |
| `isUseAbramzonSirignano()` | Query blowing correction state |
| `setSpaldingMassTransferNumber(double)` | Set B_M for Abramzon-Sirignano |
| `getSpaldingMassTransferNumber()` | Get current B_M value |
| `calcAbramzonSirignanoF(double bm)` | Calculate F(B_M) correction function |

### Migration

No breaking API changes. The corrected correlations may produce different mass
transfer coefficients than before for droplet/bubble flow nodes, but this is a
**bug fix** — the old values were physically incorrect (using pipe diameter instead
of particle diameter).

---

## 2026-04-17 — Separator MechanicalDesign Bridge Methods & Internals Classes

### Summary

MechanicalDesign is now the single gateway for ALL separator physical
configuration. Four changes:

1. **Bridge methods on SeparatorMechanicalDesign** — New methods that delegate
   to the Separator process equipment:
   - `setInletPipeDiameter(double)` / `getInletPipeDiameter()` — sets inlet
     pipe diameter on the performance calculator for DSD generation
   - `setInletDeviceType(InletDeviceModel.InletDeviceType)` — sets inlet
     device (INLET_VANE, INLET_CYCLONE, etc.)
   - `setGasLiquidSurfaceTension(double)` — sets interfacial tension for DSD
   - `addSeparatorSection(String, String)` — adds vane/meshpad/nozzle/manway
     sections
   - `getSeparatorSections()` / `getSeparatorSection(int)` /
     `getSeparatorSection(String)` — read sections
   - `setDesign()` now also pushes `inletNozzleID` back to Separator

2. **New `internals/` package** (`process.mechanicaldesign.separator.internals`):
   - `DemistingInternal` — base class for wire mesh, vane pack, cyclone
     demisting devices. Calculates Souders-Brown max gas velocity, Euler-number
     pressure drop, and exponential liquid carry-over model.
   - `DemistingInternalWithDrainage` — adds drainage section efficiency
     (reduces carry-over by drainage factor).

3. **New `primaryseparation/` package**
   (`process.mechanicaldesign.separator.primaryseparation`):
   - `PrimarySeparation` — base class for inlet devices: inlet momentum
     (rho*v^2), momentum limit checking, liquid carry-over with degradation.
   - `InletVane` — inlet vane (6000 Pa max momentum, 85% efficiency)
   - `InletVaneWithMeshpad` — inlet vane + downstream mesh pad (92% + mesh
     pad capture)
   - `InletCyclones` — inlet cyclone cluster (8000 Pa, 95% efficiency)

4. **Logging cleanup** — Replaced `System.out.println` with log4j2 `logger`
   in `SeparatorMechanicalDesign`, `GasScrubberMechanicalDesign`, and
   `GasScrubberSimple`.

### Migration

**Before (setting inlet pipe diameter directly on Separator):**
```java
separator.setInletPipeDiameter(0.254);
```

**After (set via MechanicalDesign — preferred):**
```java
SeparatorMechanicalDesign design =
    (SeparatorMechanicalDesign) separator.getMechanicalDesign();
design.setInletPipeDiameter(0.254);
```

Both paths still work — the old Separator methods remain for backward
compatibility. But all new code should use the MechanicalDesign gateway.

### Agents/Skills affected

- `neqsim-api-patterns` — updated with bridge method examples
- `neqsim-capability-map` — added internals and primaryseparation packages
- `copilot-instructions.md` / `AGENTS.md` — updated architecture table and
  example code

---

## 2026-04-17 — Dynamic Internals Bridge Methods on SeparatorMechanicalDesign

### Summary

Extended the MechanicalDesign gateway with bridge methods for separator dynamic
simulation parameters (weir, boot, mist eliminator). These delegate to the
corresponding `Separator` fields used by `runTransient()`:

- `setWeirHeightAbsolute(double)` / `getWeirHeightAbsolute()` — sets weir
  height [m] on Separator, also syncs `weirFraction` from inner diameter
- `setWeirLength(double)` / `getWeirLength()` — weir crest length [m]
- `setBootVolume(double)` / `getBootVolume()` — boot/sump volume [m3]
- `setMistEliminatorDpCoeff(double)` / `getMistEliminatorDpCoeff()` — Euler
  number for mist eliminator dP calculation (dP = Eu * 0.5 * rho * v^2)
- `setMistEliminatorThickness(double)` / `getMistEliminatorThickness()` —
  demister pad thickness [m] (converts to/from MechanicalDesign mm storage)
- `applyDemistingInternal(DemistingInternal)` — convenience method that pushes
  Eu number and thickness from a design object to the dynamic Separator

### Naming note

`setWeirHeightAbsolute` is used (not `setWeirHeight`) because the existing
`getWeirHeight()` in SeparatorMechanicalDesign returns `weirFraction * ID`
(design-phase calculated value), not the absolute dynamic height.

### Migration

**Before (setting dynamic params directly on Separator):**
```java
separator.setWeirHeight(0.30);
separator.setMistEliminatorDpCoeff(150.0);
```

**After (set via MechanicalDesign — preferred):**
```java
SeparatorMechanicalDesign design =
    (SeparatorMechanicalDesign) separator.getMechanicalDesign();
design.setWeirHeightAbsolute(0.30);
design.setMistEliminatorDpCoeff(150.0);
// Or push from a design object:
design.applyDemistingInternal(new DemistingInternal("WireMesh", "wire_mesh"));
```

### Agents/Skills affected

- `neqsim-api-patterns` — added dynamic bridge method examples
- `copilot-instructions.md` / `AGENTS.md` — updated code examples and
  architecture table with full bridge method list

---

## 2026-04-13 — MCP Server: Professional-Use Improvements (48 Tools)

### Summary

Five improvements for professional engineering use:

1. **Build coordination** — `neqsim-mcp-server/pom.xml` now has a `local-dev` Maven
   profile (`-Plocal-dev`) that resolves NeqSim from local `~/.m2/` using SNAPSHOT
   version. Keeps MCP server and core in sync during development.

2. **HTTP/SSE transport** — Added `quarkus-mcp-server-sse` dependency alongside
   existing STDIO. SSE endpoint at `http://localhost:8080/mcp` with CORS for
   `localhost:3000` and `localhost:5173`. Web-based clients can now connect
   without STDIO subprocess management.

3. **NIST benchmark validation** — New `BenchmarkValidationTest.java` (7 tests)
   validates accuracy claims against reference data: methane density vs NIST
   (±2%), ISO 6976 GCV (±0.5%), separator mass balance (<0.1%), VLE phase check,
   dew point range, and trust report completeness.

4. **Full E2E test coverage** — `test_mcp_server.py` expanded from 19 to 48 tool
   coverage. All three tiers tested: Tier 1 (21 core), Tier 2 (13 advanced),
   Tier 3 (14 experimental), plus governance tools.

5. **Task workflow bridge** — New `bridgeTaskWorkflow` tool + `TaskWorkflowBridge`
   runner. Converts MCP tool output to `task_solve/` `results.json` format.
   Actions: `toResultsJson`, `getSchema`. Classified as Tier 3 EXPERIMENTAL /
   ADVISORY category. Enables end-to-end MCP → task-solving → report pipeline.

### New/Changed Files

| File | Change |
|------|--------|
| `neqsim-mcp-server/pom.xml` | Added `local-dev` profile, SSE dependency |
| `neqsim-mcp-server/src/main/resources/application.properties` | Added HTTP/SSE/CORS config |
| `src/main/java/neqsim/mcp/runners/TaskWorkflowBridge.java` | **NEW** — results.json bridge |
| `src/main/java/neqsim/mcp/runners/IndustrialProfile.java` | Added `bridgeTaskWorkflow` to EXPERIMENTAL + ADVISORY |
| `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java` | Added `bridgeTaskWorkflow` tool method |
| `src/test/java/neqsim/mcp/runners/BenchmarkValidationTest.java` | **NEW** — 7 NIST benchmark tests |
| `src/test/java/neqsim/mcp/runners/IndustrialProfileTest.java` | Updated tier size assertions (13→14 experimental) |
| `neqsim-mcp-server/test_mcp_server.py` | Expanded from 19 to 48 tool E2E coverage |

### Tool Count

- Total: **48** tools (was 47)
- Tier 1 (TRUSTED_CORE): 21
- Tier 2 (ENGINEERING_ADVANCED): 13
- Tier 3 (EXPERIMENTAL): 14 (was 13, added `bridgeTaskWorkflow`)

---

## 2026-07-13 — MCP Server: 42 Tools, 9 Prompts, 11 Resources

### MCP Server Expansion Summary

The NeqSim MCP Server has expanded from 8 basic tools to a comprehensive
engineering simulation platform:

**42 @Tool methods** in `NeqSimTools.java`:
- 9 core thermodynamic tools (flash, batch, property table, phase envelope, validation, search, capabilities, example, schema)
- 8 automation tools (list units, list variables, get/set variable, save/compare state, diagnose, learning report)
- 3 analysis tools (cross-validation, parametric study, property table)
- 8 domain-specific tools (PVT, flow assurance, standards, pipeline, reservoir, field economics, dynamic, bioprocess)
- 7 session/workflow tools (session, task solver, workflow, validation, report, plugin, progress)
- 7 platform tools (streaming, visualization, multi-server composition, security, state persistence, validation profiles, data catalog)

**9 @Prompt guided workflows** in `NeqSimPrompts.java`:
- gas processing, PVT study, flow assurance, field development, CCS, TEG dehydration, biorefinery, dynamic simulation, pipeline sizing

**11 resource endpoints** in `NeqSimResources.java` (4 static + 7 templates):
- example-catalog, schema-catalog, components, components/{name}, standards, standards/{code}, models, materials/{type}, data-tables, examples/{category}/{name}, schemas/{tool}/{type}

### New Runner Classes (in `src/main/java/neqsim/mcp/runners/`)

| Runner | Purpose |
|--------|---------|
| `PVTRunner` | PVT lab experiments (CME, CVD, DL, separator, swelling, GOR, viscosity) |
| `FlowAssuranceRunner` | Hydrate, wax, asphaltene, corrosion, erosion, cooldown |
| `StandardsRunner` | Gas/oil quality per 22 industry standards |
| `PipelineRunner` | Multiphase pipeline flow (Beggs & Brill) |
| `ReservoirRunner` | Material balance reservoir simulation |
| `FieldDevelopmentRunner` | NPV, IRR, cash flow, fiscal regimes, decline curves |
| `DynamicRunner` | Transient simulation with auto-instrumented PID controllers |
| `BioprocessRunner` | Anaerobic digestion, fermentation, gasification, pyrolysis |
| `CrossValidationRunner` | Multi-EOS cross-validation |
| `ParametricStudyRunner` | Multi-variable parametric sweeps |
| `SessionRunner` | Persistent simulation sessions (create/modify/run/snapshot/restore) |
| `TaskSolverRunner` | Engineering task solving from high-level descriptions |
| `EngineeringValidator` | Design rule validation against standards |
| `ReportRunner` | Structured engineering report generation |
| `McpRunnerPlugin` | Plugin interface for custom runners |
| `PluginRegistry` | Plugin lifecycle management |
| `ProgressTracker` | Long-running simulation progress tracking |
| `StreamingRunner` | Async simulation with incremental polling |
| `VisualizationRunner` | SVG/Mermaid/HTML visualization generation |
| `CompositionRunner` | Multi-server MCP orchestration |
| `SecurityRunner` | API key management, rate limiting, audit logging |
| `StatePersistenceRunner` | Simulation state save/load/compare across restarts |
| `ValidationProfileRunner` | Jurisdiction-specific validation (NCS, UKCS, GoM, Brazil, generic) |
| `DataCatalogRunner` | Database browsing (components, standards, materials, EOS models) |

### Key Architecture Points

- All runners follow the stateless `Runner.run(String json) → String json` pattern
- Runners live in neqsim core (`src/main/java/neqsim/mcp/runners/`)
- MCP server is a thin Quarkus wrapper (`neqsim-mcp-server/`)
- Each runner can be used independently from REST, CLI, or other MCP frameworks
- New runners are added by implementing the runner + adding a @Tool method to NeqSimTools.java

### Documentation Updated

- `neqsim-mcp-server/README.md` — Full rewrite with all 42 tools, 11 resources, 9 prompts
- `neqsim-mcp-server/MCP_CONTRACT.md` — Added Session/Workflow tools (stable), Platform tools (experimental), Resources
- `CHANGELOG_AGENT_NOTES.md` — This entry

---

## 2026-04-12 — Bioprocessing & Bioenergy: Phases 5–7

### New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `FermentationReactor` | `process.equipment.reactor` | Monod/Contois/substrate-inhibited kinetics; batch, fed-batch, continuous modes. Extends `Fermenter`. |
| `SustainabilityMetrics` | `process.util.fielddevelopment` | CO₂eq tracking (IPCC AR6 GWP), carbon intensity (kgCO₂/MWh), EROI, renewable energy fraction, fossil fuel displacement |
| `BiogasToGridModule` | `process.processmodel.biorefinery` | Pre-built: AnaerobicDigester → BiogasUpgrader → Compressor → Cooler → grid injection |
| `GasificationSynthesisModule` | `process.processmodel.biorefinery` | Pre-built: BiomassGasifier → gas cleaning → Fischer-Tropsch synthesis |
| `WasteToEnergyCHPModule` | `process.processmodel.biorefinery` | Pre-built: AnaerobicDigester → gas engine CHP with electrical + thermal output |

### Key API Patterns

```java
// FermentationReactor
FermentationReactor reactor = new FermentationReactor("FR-1", sugarFeed);
reactor.setKineticModel(FermentationReactor.KineticModel.MONOD);
reactor.setOperationMode(FermentationReactor.OperationMode.CONTINUOUS);
reactor.setMaxSpecificGrowthRate(0.30);  // NOT setMuMax()
reactor.setResidenceTime(10.0, "hr");    // requires unit string
reactor.setFeedingRate(50.0);            // NOT setFedBatchFeedRate()
reactor.setFeedSubstrateConcentration(200.0);  // NOT setFedBatchFeedConcentration()
reactor.run();
Map<String, Object> results = reactor.getResults();

// BiogasUpgrader enum methods
BiogasUpgrader.UpgradingTechnology tech = BiogasUpgrader.UpgradingTechnology.MEMBRANE;
tech.getMethaneRecovery();       // NOT getCh4Recovery()
tech.getCo2RemovalEfficiency();  // NOT getCo2Removal()

// SustainabilityMetrics
SustainabilityMetrics metrics = new SustainabilityMetrics();
metrics.setBiogasProductionNm3PerYear(3_000_000.0);
metrics.calculate();
metrics.getCarbonIntensityKgCO2PerMWh();

// BiogasToGridModule
BiogasToGridModule btg = new BiogasToGridModule("BTG");
btg.setFeedStream(wasteStream);
btg.setSubstrateType(AnaerobicDigester.SubstrateType.FOOD_WASTE);
btg.setUpgradingTechnology(BiogasUpgrader.UpgradingTechnology.MEMBRANE);
btg.setGridPressureBara(40.0);
btg.run();
Map<String, Object> results = btg.getResults();
```

### Common Mistakes (from testing)

- `getCh4Recovery()` → use `getMethaneRecovery()`
- `getCo2Removal()` → use `getCo2RemovalEfficiency()`
- `setMuMax()` → use `setMaxSpecificGrowthRate()`
- `setResidenceTime(10.0)` → use `setResidenceTime(10.0, "hr")` (unit required)
- `GasificationSynthesisModule` constructor takes `(String name)` only — set biomass via `setBiomass(BiomassCharacterization, feedRateKgPerHr)`

### Skills/Agents Updated

- `neqsim-capability-map` — added Section I-bis (Bioprocessing & Bioenergy) + quick lookup entries
- `neqsim-reaction-engineering` — added Bioprocessing Reactors section
- `copilot-instructions.md` — added bioprocessing class import paths
- `AGENTS.md` — updated reaction-engineering skill description
- `CONTEXT.md` — added bioprocessing to equipment and where-to-find tables
- `neqsim_dev_setup.py` — added all bioprocessing classes to `neqsim_classes()`

### Existing Classes (Phases 1–3, prior sessions)

| Class | Package | Tests |
|-------|---------|-------|
| `BiomassCharacterization` | `thermo.characterization` | 12 tests |
| `AnaerobicDigester` | `process.equipment.reactor` | 10 tests |
| `BiomassGasifier` | `process.equipment.reactor` | 8 tests |
| `PyrolysisReactor` | `process.equipment.reactor` | 8 tests |
| `BiogasUpgrader` | `process.equipment.splitter` | 10 tests |
| `BiorefineryCostEstimator` | `process.mechanicaldesign` | 18 tests |

---

## 2026-07-12 — LoopedPipeNetwork: 6 Advanced Production Features

### New Capabilities in `LoopedPipeNetwork`

Six production network features added to `neqsim.process.equipment.network.LoopedPipeNetwork`:

1. **Artificial Lift** — Gas lift (`setGasLift`), ESP (`setESP`), jet pump (`setJetPump`), rod pump (`setRodPump`) with `ArtificialLiftType` enum. Pressure boost applied in NR-GGA solver.
2. **Large-Scale Networks** — 120+ wells with 6 manifolds converge in 15-20 iterations (< 0.1 s). Schur complement keeps matrix size proportional to loops, not elements.
3. **Water Handling** — `setWaterCut`, `addWaterInjection(src, res, name, rate)`, `setWaterBreakthrough(elem, btWC, finalWC, currentWC)`, `calculateWaterBalance()`.
4. **Sand/Solids Tracking** — `setSandRate`, `calculateSandTransport()` per DNV RP O501, `getSandViolations()`, configurable erosion/sand rate limits.
5. **Corrosion & Integrity** — `setCorrosiveGas(elem, co2, h2s)`, `setCorrosionModel(elem, "NORSOK")`, `calculateCorrosion()` with de Waard-Milliams and NORSOK M-506 models, wall life, `getCorrosionViolations()`.
6. **GHG Emissions** — `setCO2EmissionFactor`, `setMethaneSlipFactor`, `calculateEmissions()`, `getTotalCO2Emissions()`, `getAnnualCO2EmissionsTonnes()`, `getEmissionsIntensity()`. Defaults: EF=2.75, slip=2%, GWP(CH4)=28 (IPCC AR5).

### Affected Skills/Agents

- **neqsim-capability-map**: Updated — no longer "limited to simple networks"
- **neqsim-production-optimization**: Added LoopedPipeNetwork section with advanced API
- **neqsim-flow-assurance**: Added network-level corrosion (de Waard/NORSOK) and sand erosion (DNV RP O501) patterns
- **emissions agent**: Added LoopedPipeNetwork emissions tracking section

### Documentation

- `docs/process/equipment/production_well_networks.md` — 6 new sections with API, formulas, and examples
- `examples/notebooks/production_network_advanced_features.ipynb` — 25-cell notebook demonstrating all features
- 96 unit tests in `LoopedPipeNetworkTest.java`

---

## 2026-07-08 — UniSim Reader: Default E300 Fluid Export

### E300 is Now the Default Fluid Transfer Route

When importing fluids from UniSim to NeqSim, the **E300 file route is now the
default**. `UniSimReader.read(export_e300=True)` (the default) extracts critical
properties (Tc, Pc, acentric factor, MW, BIPs, volume shifts) from each component
via COM and writes an E300 file per fluid package.

This preserves all thermodynamic characterization — including hypothetical/pseudo
components like C7+ fractions — that component name mapping alone cannot capture.

### New Java Overloads

```java
// Build and run with a pre-built fluid (e.g., from E300 file)
ProcessSystem.fromJsonAndRun(String json, SystemInterface fluid)
JsonProcessBuilder.buildAndRun(String json, SystemInterface fluid)
```

### Python Usage (Automatic)

```python
reader = UniSimReader()
model = reader.read(r'C:\path\to\model.usc')  # auto-exports E300 files
for fp in model.fluid_packages:
    print(f"  {fp.name}: {fp.e300_file_path}")

converter = UniSimToNeqSim(model)
result = converter.build_and_run()  # auto-loads E300 fluid
```

### Python Usage (Manual E300 Loading)

```python
from neqsim import jneqsim
EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite
fluid = EclipseFluidReadWrite.read(r'C:\path\to\model_FluidPkg.e300')
```

### Affected Files
- `devtools/unisim_reader.py` — `UniSimComponent` (critical properties), `UniSimFluidPackage` (`write_e300()`, `has_critical_properties`), `_extract_fluid_packages()` (COM property extraction), `_extract_bips()` (new), `read()` (`export_e300` parameter), `_build_fluid_section()` (E300 path in fluid dict), `build_and_run()` (E300 auto-loading)
- `src/main/java/neqsim/process/processmodel/JsonProcessBuilder.java` — `buildAndRun(String, SystemInterface)`, `buildFromJsonObject(JsonObject, SystemInterface)`
- `src/main/java/neqsim/process/processmodel/ProcessSystem.java` — `fromJsonAndRun(String, SystemInterface)`
- `.github/skills/neqsim-unisim-reader/SKILL.md` — E300 section added
- `AGENTS.md` — Updated descriptions

---

## 2026-07-08 — UniSim Reader: Orientation Detection (GasScrubber)

### Vertical Separator → GasScrubber Mapping

The UniSim reader (`devtools/unisim_reader.py`) now detects separator orientation.
Vertical `flashtank` operations are mapped to `GasScrubber` instead of `Separator`.

| UniSim flashtank | NeqSim Type |
|---|---|
| horizontal (default) | `Separator` |
| vertical | `GasScrubber` |
| has WaterProduct | `ThreePhaseSeparator` |

`GasScrubber` extends `Separator` — it is a vertical vessel with K-value
sizing constraints and 10% liquid level. The orientation is detected from
UniSim COM attributes (`Orientation`, `VesselOrientation`, `SeparatorOrientation`).

### Affected Files
- `devtools/unisim_reader.py` — `resolve_neqsim_type()` method, orientation extraction
- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim.reader.agent.md`
- `AGENTS.md`

---

## 2026-07-07 — Full FPSO Model: Architecture Learnings

### HP Separator Water Routing

When replicating UniSim models in NeqSim, the HP separator at high pressure (90 bar)
may not produce a separate aqueous phase in UniSim. To match this behavior, use
`ThreePhaseSeparator` and then `Mixer` to recombine oil + water:

```java
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Sep", feedStream);
Mixer hpLiqRecombine = new Mixer("HP Liquid Recombine");
hpLiqRecombine.addStream(hpSep.getOilOutStream());
hpLiqRecombine.addStream(hpSep.getWaterOutStream());
// hpLiqRecombine.getOutletStream() now matches UniSim HP oil (includes water)
```

### Import Gas Compression Architecture

Large FPSO models use staged import gas compression matching pressure levels:
- VLP gas (~2 bar) → VRU compressor → ~5 bar → mix with LP gas
- LP+VRU gas (~5 bar) → 1st import compressor → ~22 bar → mix with MP gas
- MP+1st import gas (~22 bar) → 2nd import compressor → ~90 bar → mix with HP gas

Each stage has cooler + flash drum before the compressor (removes condensate).

### Pump API

```java
Pump pump = new Pump("P-100", liquidStream);
pump.setOutletPressure(6.1);          // bara
pump.setIsentropicEfficiency(0.75);
pump.getPower("kW");                  // after run
```

### ComponentSplitter for TEG Dehydration

```java
ComponentSplitter teg = new ComponentSplitter("TEG", wetGasStream);
int nComp = wetGasStream.getFluid().getNumberOfComponents();
double[] sf = new double[nComp];
java.util.Arrays.fill(sf, 1.0);
sf[nComp - 1] = 0.0;  // water is last component
teg.setSplitFactors(sf);
// getSplitStream(0) = dry gas, getSplitStream(1) = removed water
```

### Model Scale: 50+ Equipment Units in Single ProcessSystem

The reference FPSO model demonstrates ~50 equipment units in a single `ProcessSystem`
covering wellhead → HP/MP/LP/VLP separation → VRU + import gas compression →
gas cooling + TEG → 2-stage export compression → seal gas JT → oil export.
Single `ProcessSystem` converges in ~2 seconds without recycles.

---

## 2026-07-06 — JT Expansion: Use ThrottlingValve, Not PHflash

### Critical Agent Guidance

When modeling isenthalpic (Joule-Thomson) expansion, **always use `ThrottlingValve` in a
`ProcessSystem`**, never manual `PHflash()` on a cloned fluid. Tested on FPSO seal gas
(90→48 bar):

| Method | Temperature (°C) | UniSim Reference | Error |
|--------|-----------------|------------------|-------|
| `ThrottlingValve` in ProcessSystem | 16.44 | 18.17 | -1.73°C |
| Manual `PHflash(H/n)` on clone | 33.05 | 18.17 | +14.88°C |

The manual PHflash approach fails because `getEnthalpy('J')` returns total system enthalpy
while `PHflash(double)` expects a specific enthalpy convention (per mole at the system's
reference state). The ThrottlingValve handles the enthalpy bookkeeping internally.

**Pattern:**
```java
// CORRECT: Use process-level valve
ProcessSystem proc = new ProcessSystem();
Stream sg = new Stream("SG", fluid.clone());
proc.add(sg);
ThrottlingValve jt = new ThrottlingValve("JT", sg);
jt.setOutletPressure(48.0);
proc.add(jt);
proc.run();
double T_jt = jt.getOutletStream().getTemperature("C");  // Correct JT temperature

// WRONG: Manual PHflash — gives incorrect JT temperature
// SystemInterface clone = fluid.clone();
// clone.setPressure(48.0);
// new ThermodynamicOperations(clone).PHflash(fluid.getEnthalpy("J") / fluid.getTotalNumberOfMoles());
```

### FPSO Model Extension

Extended the NeqSim FPSO replication to include:
- LP/MP gas recompression + mixing with HP gas
- Gas cooling (24HA101, 75°C→36°C) + flash drum (24VG101)
- Seal gas takeoff (5.4% split)
- 2-stage export compression (26KA101: 86→259 bar, 26KA102: 258→554 bar)
- Seal gas JT expansion curve showing 1.35% max condensation at 30 bar

Compressor discharge temperature comparison:
- 26KA101: NeqSim 126.7°C vs UniSim 117.8°C (75% η_is assumed)
- 26KA102: NeqSim 85.9°C vs UniSim 83.6°C
- Suggests UniSim uses ~83-85% isentropic efficiency

---

## 2026-07-05 — EclipseFluidReadWrite Null BIC Fix, UniSim BIP Extraction

### Bug Fix

| Class | Issue | Fix |
|-------|-------|-----|
| `EclipseFluidReadWrite` | `NullPointerException` when E300 file has no BIC section — `kij` array stays `null` | Both `read()` methods now initialize `kij` to zero matrix if BIC section is missing. E300 files without BIC load correctly (all BIPs default to 0.0). |

### Impact on Agents

- **E300 file loading**: Previously required a BIC section or the reader crashed. Now optional (defaults to zero BIPs). However, agents should always include BIC in generated E300 files for accurate results.
- **UniSim → E300 workflow**: BIPs can now be extracted from UniSim via `pp.Kij.Values` (tuple-of-tuples). See `neqsim-unisim-reader` skill Section 1.1 for the COM access pattern.

### Key Discovery

UniSim COM BIP extraction pattern:
```python
kij_obj = pp.Kij          # CDispatch (RealFlexVariable)
raw = kij_obj.Values      # tuple-of-tuples (n×n symmetric matrix)
# Diagonal sentinel = -32767.0, replace with 0.0
```
- `pp.GetInteractionParameter(i,j)` returns 0.0 for PR-LK (correlation BIPs not accessible this way)
- `kij_obj.GetValues()` fails — use `.Values` property instead

---

## 2026-04-08 — IEC 81346 Reference Designation Support

### Vertical Separator → GasScrubber Mapping

The UniSim reader (`devtools/unisim_reader.py`) now detects separator orientation.
Vertical `flashtank` operations are mapped to `GasScrubber` instead of `Separator`.

| UniSim flashtank | NeqSim Type |
|---|---|
| horizontal (default) | `Separator` |
| vertical | `GasScrubber` |
| has WaterProduct | `ThreePhaseSeparator` |

`GasScrubber` extends `Separator` — it is a vertical vessel with K-value
sizing constraints and 10% liquid level. The orientation is detected from
UniSim COM attributes (`Orientation`, `VesselOrientation`, `SeparatorOrientation`).

### Affected Files
- `devtools/unisim_reader.py` — `resolve_neqsim_type()` method, orientation extraction
- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim.reader.agent.md`
- `AGENTS.md`

---

## 2026-07-07 — Full FPSO Model: Architecture Learnings

### HP Separator Water Routing

When replicating UniSim models in NeqSim, the HP separator at high pressure (90 bar)
may not produce a separate aqueous phase in UniSim. To match this behavior, use
`ThreePhaseSeparator` and then `Mixer` to recombine oil + water:

```java
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Sep", feedStream);
Mixer hpLiqRecombine = new Mixer("HP Liquid Recombine");
hpLiqRecombine.addStream(hpSep.getOilOutStream());
hpLiqRecombine.addStream(hpSep.getWaterOutStream());
// hpLiqRecombine.getOutletStream() now matches UniSim HP oil (includes water)
```

### Import Gas Compression Architecture

Large FPSO models use staged import gas compression matching pressure levels:
- VLP gas (~2 bar) → VRU compressor → ~5 bar → mix with LP gas
- LP+VRU gas (~5 bar) → 1st import compressor → ~22 bar → mix with MP gas
- MP+1st import gas (~22 bar) → 2nd import compressor → ~90 bar → mix with HP gas

Each stage has cooler + flash drum before the compressor (removes condensate).

### Pump API

```java
Pump pump = new Pump("P-100", liquidStream);
pump.setOutletPressure(6.1);          // bara
pump.setIsentropicEfficiency(0.75);
pump.getPower("kW");                  // after run
```

### ComponentSplitter for TEG Dehydration

```java
ComponentSplitter teg = new ComponentSplitter("TEG", wetGasStream);
int nComp = wetGasStream.getFluid().getNumberOfComponents();
double[] sf = new double[nComp];
java.util.Arrays.fill(sf, 1.0);
sf[nComp - 1] = 0.0;  // water is last component
teg.setSplitFactors(sf);
// getSplitStream(0) = dry gas, getSplitStream(1) = removed water
```

### Model Scale: 50+ Equipment Units in Single ProcessSystem

The reference FPSO model demonstrates ~50 equipment units in a single `ProcessSystem`
covering wellhead → HP/MP/LP/VLP separation → VRU + import gas compression →
gas cooling + TEG → 2-stage export compression → seal gas JT → oil export.
Single `ProcessSystem` converges in ~2 seconds without recycles.

---

## 2026-07-06 — JT Expansion: Use ThrottlingValve, Not PHflash

### Critical Agent Guidance

When modeling isenthalpic (Joule-Thomson) expansion, **always use `ThrottlingValve` in a
`ProcessSystem`**, never manual `PHflash()` on a cloned fluid. Tested on FPSO seal gas
(90→48 bar):

| Method | Temperature (°C) | UniSim Reference | Error |
|--------|-----------------|------------------|-------|
| `ThrottlingValve` in ProcessSystem | 16.44 | 18.17 | -1.73°C |
| Manual `PHflash(H/n)` on clone | 33.05 | 18.17 | +14.88°C |

The manual PHflash approach fails because `getEnthalpy('J')` returns total system enthalpy
while `PHflash(double)` expects a specific enthalpy convention (per mole at the system's
reference state). The ThrottlingValve handles the enthalpy bookkeeping internally.

**Pattern:**
```java
// CORRECT: Use process-level valve
ProcessSystem proc = new ProcessSystem();
Stream sg = new Stream("SG", fluid.clone());
proc.add(sg);
ThrottlingValve jt = new ThrottlingValve("JT", sg);
jt.setOutletPressure(48.0);
proc.add(jt);
proc.run();
double T_jt = jt.getOutletStream().getTemperature("C");  // Correct JT temperature

// WRONG: Manual PHflash — gives incorrect JT temperature
// SystemInterface clone = fluid.clone();
// clone.setPressure(48.0);
// new ThermodynamicOperations(clone).PHflash(fluid.getEnthalpy("J") / fluid.getTotalNumberOfMoles());
```

### FPSO Model Extension

Extended the NeqSim FPSO replication to include:
- LP/MP gas recompression + mixing with HP gas
- Gas cooling (24HA101, 75°C→36°C) + flash drum (24VG101)
- Seal gas takeoff (5.4% split)
- 2-stage export compression (26KA101: 86→259 bar, 26KA102: 258→554 bar)
- Seal gas JT expansion curve showing 1.35% max condensation at 30 bar

Compressor discharge temperature comparison:
- 26KA101: NeqSim 126.7°C vs UniSim 117.8°C (75% η_is assumed)
- 26KA102: NeqSim 85.9°C vs UniSim 83.6°C
- Suggests UniSim uses ~83-85% isentropic efficiency

---

## 2026-07-05 — EclipseFluidReadWrite Null BIC Fix, UniSim BIP Extraction

### Bug Fix

| Class | Issue | Fix |
|-------|-------|-----|
| `EclipseFluidReadWrite` | `NullPointerException` when E300 file has no BIC section — `kij` array stays `null` | Both `read()` methods now initialize `kij` to zero matrix if BIC section is missing. E300 files without BIC load correctly (all BIPs default to 0.0). |

### Impact on Agents

- **E300 file loading**: Previously required a BIC section or the reader crashed. Now optional (defaults to zero BIPs). However, agents should always include BIC in generated E300 files for accurate results.
- **UniSim → E300 workflow**: BIPs can now be extracted from UniSim via `pp.Kij.Values` (tuple-of-tuples). See `neqsim-unisim-reader` skill Section 1.1 for the COM access pattern.

### Key Discovery

UniSim COM BIP extraction pattern:
```python
kij_obj = pp.Kij          # CDispatch (RealFlexVariable)
raw = kij_obj.Values      # tuple-of-tuples (n×n symmetric matrix)
# Diagonal sentinel = -32767.0, replace with 0.0
```
- `pp.GetInteractionParameter(i,j)` returns 0.0 for PR-LK (correlation BIPs not accessible this way)
- `kij_obj.GetValues()` fails — use `.Values` property instead

---

## 2026-04-05 — Heat Integration, Power Generation, Agentic QA Gate

### New Java Classes

| Class | Package | Description |
|-------|---------|-------------|
| `PinchAnalysis` | `process.equipment.heatexchanger.heatintegration` | Linnhoff pinch analysis: composite curves, grand composite curve, minimum hot/cold utility targeting, pinch temperature. Accepts hot/cold `HeatStream` objects with MCp and temperature range. |
| `HeatStream` | `process.equipment.heatexchanger.heatintegration` | Data model for hot/cold process streams. Auto-classifies HOT/COLD from supply vs target temperature. Celsius convenience API, Kelvin internal storage. |
| `SteamTurbine` | `process.equipment.powergeneration` | Isentropic steam expansion with configurable efficiency. PS/PH flash for outlet conditions. `getPower("kW")` API. |
| `HRSG` | `process.equipment.powergeneration` | Heat Recovery Steam Generator. Takes hot gas exhaust, calculates steam production rate at specified pressure/temperature using approach temperature and effectiveness. |
| `CombinedCycleSystem` | `process.equipment.powergeneration` | Integrates GasTurbine + HRSG + SteamTurbine. `getTotalPower("MW")`, `getOverallEfficiency()`, `toJson()`. |
| `SimulationQualityGate` | `util.agentic` | Automated QA gate for ProcessSystem validation: physical bounds (T > 0 K, P > 0), stream consistency (no NaN/Inf), composition normalization. Returns JSON report with issues, severity, and remediation hints. |

### New Skills (5)

`neqsim-eos-regression`, `neqsim-reaction-engineering`, `neqsim-dynamic-simulation`,
`neqsim-distillation-design`, `neqsim-electrolyte-systems`.

### New Agents (3)

`reaction.engineering`, `control.system`, `emissions.environmental`.

### Usage — PinchAnalysis

```java
PinchAnalysis pinch = new PinchAnalysis(10.0); // deltaT_min = 10 C
pinch.addHotStream("H1", 180, 80, 30);   // 180→80 C, MCp=30 kW/K
pinch.addColdStream("C1", 30, 140, 20);  // 30→140 C, MCp=20 kW/K
pinch.run();
double Qh = pinch.getMinimumHeatingUtility();  // kW
double Qc = pinch.getMinimumCoolingUtility();   // kW
double Tpinch = pinch.getPinchTemperatureC();   // °C
String json = pinch.toJson();
```

### Usage — SimulationQualityGate

```java
ProcessSystem process = new ProcessSystem();
// ... build and run process ...
process.run();
SimulationQualityGate gate = new SimulationQualityGate(process);
gate.validate();
if (!gate.isPassed()) {
    System.out.println(gate.toJson());
}
```

### Usage — CombinedCycleSystem

```java
CombinedCycleSystem cc = new CombinedCycleSystem("CC-1", fuelGasStream);
cc.setCombustionPressure(15.0);
cc.setSteamPressure(40.0);
cc.setSteamTemperature(400.0, "C");
cc.setSteamTurbineEfficiency(0.85);
cc.run();
double totalMW = cc.getTotalPower("MW");
double efficiency = cc.getOverallEfficiency();
```

---

## 2026-03-31 — GibbsReactor Jacobian Fix & Solver Performance Improvements

### Bug Fix — RT-Corrected Off-Diagonal Jacobian (Always On)

The off-diagonal entries of the Newton-Raphson Jacobian were missing an `RT`
factor. The corrected formula `RT * (-1/n_total + d ln(φ)/dn)` is now the only
code path — the legacy formula has been removed. This fixes convergence issues
for adiabatic and mixed-phase equilibrium. No user action needed (previously
required `setUseConsistentOffDiagonal(true)` which is now a deprecated no-op).

### Performance Improvements

Four algorithmic improvements to the Newton-Raphson solver in `GibbsReactor`:

1. **LU decomposition replaces explicit matrix inverse** — The Newton linear
   system $J \cdot \Delta x = -F$ is now solved via EJML's `solve()` (LU
   decomposition) instead of computing $J^{-1}$ then multiplying. ~3× faster
   and more numerically stable. Falls back to pseudo-inverse if LU fails.

2. **Removed SVD condition number check** — The per-iteration `conditionP2()`
   call (O(n³) SVD) has been removed from the hot path. The legacy
   `calculateJacobianInverse()` method is kept for backward compatibility but
   is only used as a fallback.

3. **NASA CEA-style adaptive step sizing** — New opt-in feature via
   `setUseAdaptiveStepSize(true)`. Computes step size each iteration to limit
   max relative mole change (factor of ~5×). Skips near-zero components so
   they can grow freely. Prevents negative moles.

4. **Configurable minimum iterations** — `setMinIterations(int n)` replaces the
   hardcoded `iteration >= 100` convergence guard. Default unchanged at 100 for
   backward compatibility. Set to 3 for simple isothermal systems.

### New Methods on `GibbsReactor`

| Method | Default | Description |
|--------|---------|-------------|
| `setMinIterations(int)` | 100 | Min iterations before convergence check |
| `getMinIterations()` | — | Get current minimum iterations |
| `setUseAdaptiveStepSize(boolean)` | false | Enable adaptive step sizing |
| `isUseAdaptiveStepSize()` | — | Check if adaptive step sizing is active |

### Deprecated Methods on `GibbsReactor`

| Method | Notes |
|--------|-------|
| `setUseConsistentOffDiagonal(boolean)` | No-op. RT correction is always active. |
| `isUseConsistentOffDiagonal()` | Always returns `true`. |

### Migration Notes

- **No breaking changes** — all defaults preserved, existing code runs identically.
- `setUseConsistentOffDiagonal(true)` calls still compile but are no-ops.
- To opt into faster convergence for isothermal systems:
  ```java
  reactor.setUseAdaptiveStepSize(true);
  reactor.setMinIterations(3);
  ```
- The internal method `solveNewtonSystem(double[])` is private — no public API change.

---

## 2026-03-30 — Serialization Cleanup & ProcessLogic Extends Serializable

### Breaking Change — `ProcessLogic` now extends `Serializable`

- **`ProcessLogic`** (`process.logic.ProcessLogic`) now extends `java.io.Serializable`.
  This was required to eliminate the last SpotBugs SE_BAD_FIELD warning caused by
  a compiler-generated synthetic field in `AlarmActionHandler`'s anonymous inner class
  that captured a `ProcessLogic` reference.
- Any class implementing `ProcessLogic` is now implicitly `Serializable`.
- Non-serializable fields in `ProcessLogic` implementations (`ESDLogic`, `HIPPSLogic`,
  `ShutdownLogic`, `StartupLogic`, `SafetyInstrumentedFunction`) have been marked
  `transient`.

### Serialization Audit — 56 SE_BAD_FIELD Warnings Fixed

All SpotBugs SE_BAD_FIELD warnings have been resolved by adding `transient` to
non-serializable fields across 40+ classes. Categories fixed:

- **Thermo phases**: `doubleW[]`, `doubleW[][]`, GERG EOS objects in phase classes
- **Database classes**: JDBC `Connection` and `Statement` fields (6 database classes)
- **Process equipment**: Inner class types (`NetworkNode`, `GibbsComponent`,
  `ReservoirLayer`, `ValveSkid`, `UmbilicalElement`, `TransientWallHeatTransfer`, etc.)
- **Functional interfaces**: `Function`, `BiConsumer`, `Consumer` fields in
  `Adjuster`, `SetPoint`, `Calculator`, `SpreadsheetBlock`, `EquipmentStateAdapter`,
  `BatchStudy`, `SensitivityAnalysis`, `ProcessSafetyScenario`
- **Util/optimizer**: `ProductionOptimizer`, `ProcessLinearizer`, `ProgressCallback`
- **Mechanical design**: `SubseaCostEstimator`, `ShellAndTubeDesignCalculator`,
  `TorgManager`, `MechanicalDesignDataSource`
- **Standards**: Apache Commons Math interpolators in `Standard_ISO6578`
- **Core**: `Thread` in `ThermodynamicOperations`, `BicubicInterpolator` in
  `OLGApropertyTableGeneratorWater`

**Pattern for new code:** When adding fields to any class that extends
`ProcessEquipmentBaseClass`, `MeasurementDeviceBaseClass`, `MechanicalDesign`,
or any other `Serializable` class, mark non-serializable fields `transient`:

```java
// Correct modifier order:
private transient MyNonSerializableType field;
private final transient List<NonSerializableInner> items = new ArrayList<>();
transient SomeType packagePrivateField;  // package-private
```

**Agents/skills updated:** `neqsim-java8-rules/SKILL.md`, `copilot-instructions.md`.

---

## 2026-03-27 — UniSimToNeqSim Python Code Generation

### New Method — `to_python()` on `UniSimToNeqSim`

- **`UniSimToNeqSim.to_python(include_subflowsheets=True)`** generates a self-contained,
  **human-readable Python script** that recreates the entire UniSim process using
  explicit `jneqsim` API calls — instead of the opaque JSON intermediate format.
- The generated script includes: all imports, fluid/EOS definition with components,
  feed streams with T/P/flow, every equipment item in topological order wired through
  outlet stream references (`getGasOutStream()`, `getLiquidOutStream()`,
  `getSplitStream(int)`, `getOutletStream()`), and `process.run()`.
- Handles all supported equipment types: Separator, ThreePhaseSeparator, Mixer,
  Splitter, Compressor, ThrottlingValve, Cooler, Heater, HeatExchanger, Pump,
  Expander, AdiabaticPipe, Recycle, DistillationColumn, StreamSaturatorUtil.
- Sanitizes variable names (spaces, hyphens, special chars → underscores; numeric
  prefixes get `_` prefix; uniqueness guaranteed).
- Located in `devtools/unisim_reader.py`.

**Usage:**
```python
from devtools.unisim_reader import UniSimReader, UniSimToNeqSim

reader = UniSimReader(visible=False)
model = reader.read(r"path\to\file.usc")
reader.close()

converter = UniSimToNeqSim(model)
python_code = converter.to_python()

with open("my_process.py", "w") as f:
    f.write(python_code)
```

**Agents/skills updated:** `unisim.reader.agent.md`, `neqsim-unisim-reader/SKILL.md`,
`PR_DESCRIPTION_PROCESS_EXTRACTION.md`, `devtools/README.md`.

---

## 2026-03-27 — Distillation Column Internals, Air Cooler, PVF Flash, Amine Framework

### New Classes — Distillation Internals

- **`PackedColumn`** (`process.equipment.distillation`) — Extends `DistillationColumn`
  for packed absorption/distillation columns (absorbers, strippers, contactors).
  Wraps rigorous VLE column solver and adds packing-specific functionality:
  - HETP calculation from packed bed height
  - Packing hydraulics via `PackingHydraulicsCalculator`
  - Built-in presets (Pall Ring, Mellapak, IMTP, etc.)
  - API: `setPackedHeight()`, `setPackingType()`, `setStructuredPacking()`,
    `addSolventStream()`, `getHETP()`, `getPercentFlood()`, `toJson()`

- **`ShortcutDistillationColumn`** (`process.equipment.distillation`) — Rapid conceptual
  design using Fenske-Underwood-Gilliland (FUG) method:
  - Fenske: minimum stages from relative volatility
  - Underwood: minimum reflux ratio
  - Gilliland: actual stages (Molokanov correlation)
  - Kirkbride: optimal feed tray location
  - API: `setLightKey()`, `setHeavyKey()`, `setLightKeyRecoveryDistillate()`,
    `setRefluxRatioMultiplier()`, `getMinimumNumberOfStages()`,
    `getActualRefluxRatio()`, `getResultsJson()`

- **`ColumnInternalsDesigner`** (`process.equipment.distillation.internals`) — High-level
  internals sizing facade. Evaluates hydraulic performance on every tray of a converged
  `DistillationColumn`, identifies controlling tray, sizes column diameter.
  Supports tray (sieve, valve, bubble-cap) and packed modes.
  API: `calculate()`, `getRequiredDiameter()`, `isDesignOk()`, `toJson()`

- **`TrayHydraulicsCalculator`** (`process.equipment.distillation.internals`) — Per-tray
  hydraulic evaluation for sieve, valve, and bubble-cap trays. Correlations: Fair
  (flooding, entrainment), Sinnott (weeping), Francis weir (downcomer backup),
  O'Connell (tray efficiency). References: Kister (1992), Ludwig (2001), Sinnott (2005).

- **`PackingHydraulicsCalculator`** (`process.equipment.distillation.internals`) — Packing
  hydraulics engine with Eckert GPDC (flooding), Leva (pressure drop), Onda 1968
  (mass transfer coefficients), HTU/HETP. Built-in presets for 10 random packings
  and 7 structured packings (Mellapak 125Y–500Y, Flexipac 1Y–3Y).

### New Class — AirCooler Rewrite

- **`AirCooler`** (`process.equipment.heatexchanger`) — Complete rewrite from simple
  air flow calculator to full API 661 thermal design model (~960 lines):
  - Briggs-Young fin-tube correlation for air-side HTC
  - Schmidt annular fin efficiency
  - Robinson-Briggs air-side pressure drop
  - LMTD with F-correction for cross-flow
  - Fan model with cubic polynomial fan curve (dP vs Q)
  - Ambient temperature correction (ITD ratio method)
  - Bundle sizing (tubes per row, total tubes, face area, fin area)
  - Comprehensive `toJson()` report
  - API: `setDesignAmbientTemperature(T, "C")`, `setNumberOfTubeRows()`,
    `setTubeLength()`, `getFanPower("kW")`, `getOverallU()`, `toJson()`

### New Class — PVF Flash

- **`PVFflash`** (`thermodynamicoperations.flashops`) — Pressure-Vapor Fraction flash.
  Given P + target vapor fraction β → find temperature. Uses Illinois method
  (accelerated regula falsi). Integrated into `ThermodynamicOperations` via
  `ops.PVFflash(beta)`. β=0.0 → bubble point, β=1.0 → dew point.

### New Classes — Amine Framework

- **`AmineSystem`** (`thermo.util.amines`) — Convenience wrapper for creating
  electrolyte-CPA amine systems. Supports MEA, DEA, MDEA, aMDEA. Auto-configures
  species (neutral + ionic + carbamate), mixing rules, reactions, physical properties.
  - Enum: `AmineType` (`MEA`, `DEA`, `MDEA`, `AMDEA`)
  - API: `new AmineSystem(AmineType, T_K, P_bara, amineMolFraction, co2Loading)`,
    `getSystem()`, `getAmineType()`

- **`AmineViscosity`** (`physicalproperties.methods.liquidphysicalproperties.viscosity`) —
  Correlations for CO₂-loaded amine solution viscosity:
  - Weiland et al. (1998) for MEA, DEA, aMDEA
  - Teng et al. (1994) for MDEA
  - Auto-detects amine type from fluid composition

### Updated Classes

- **`DistillationColumn`** — Column specification framework with `ColumnSpecification`,
  secant-method outer adjustment loop (+531 lines)

- **`ProcessSystem`** — Three new UniSim/HYSYS-style stream summary methods:
  - `getStreamSummaryTable()` — formatted text table with T, P, flow, composition
  - `getStreamSummaryJson()` — JSON output for programmatic access
  - `getAllStreams()` — collects all unique `StreamInterface` objects

- **`ThermodynamicOperations`** — Added `PVFflash(double vaporFraction)` entry point

- **`ThermalDesignCalculator`** — Added `toJson()` method for JSON reporting

### New Database Entries

- **COMP.csv**: MEA+ (ID 1259, charge=+1) and MEACOO- (ID 1260, charge=-1)
- **REACTIONDATA.csv**: MEA/DEA equilibrium reactions (Austgen 1989)
- **STOCCOEFDATA.csv**: Updated stoichiometric coefficients for amine reactions

### Usage Examples

```java
// Packed column absorber
PackedColumn absorber = new PackedColumn("CO2 Absorber", 10, feed);
absorber.setPackedHeight(15.0);
absorber.setPackingType("Mellapak 250Y");
absorber.setStructuredPacking(true);
absorber.addSolventStream(leanAmine, 1);
absorber.run();

// Shortcut design
ShortcutDistillationColumn shortcut = new ShortcutDistillationColumn("Deprop", feed);
shortcut.setLightKey("propane");
shortcut.setHeavyKey("n-butane");
shortcut.setLightKeyRecoveryDistillate(0.98);
shortcut.setHeavyKeyRecoveryDistillate(0.02);
shortcut.run();

// Air cooler
AirCooler cooler = new AirCooler("Gas Cooler", hotStream);
cooler.setOutTemperature(40.0, "C");
cooler.setDesignAmbientTemperature(15.0, "C");
cooler.run();
double fanPower = cooler.getFanPower("kW");

// PVF flash
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.PVFflash(0.5);  // Find T where β = 0.5

// Amine system
AmineSystem amine = new AmineSystem(AmineSystem.AmineType.MEA,
    273.15 + 40.0, 1.0, 0.30, 0.40);
SystemInterface fluid = amine.getSystem();

// Stream summary
process.run();
System.out.println(process.getStreamSummaryTable());
String json = process.getStreamSummaryJson();
```

### New Tests

| Test | Methods |
|------|---------|
| `PackedColumnTest` | 4 tests: basic absorber, setters/getters, condenser/reboiler, JSON |
| `ShortcutDistillationColumnTest` | 3 tests: deethanizer, depropanizer, JSON |
| `ColumnInternalsDesignerTest` | 4 tests: sieve tray, convenience, packed, structured |
| `PackingHydraulicsCalculatorTest` | 6 tests: Pall Ring, structured, diameter, presets, mass transfer, dP |
| `TrayHydraulicsCalculatorTest` | 6 tests: sieve, diameter, valve, liquid rate, weeping, O'Connell |
| `ProcessSystemStreamSummaryTest` | 3 tests: text table, JSON, getAllStreams |
| `PVFflashTest` | 4 tests: mid-fraction, bubble point, dew point, consistency |
| `AirCoolerTest` | 14 new tests: LMTD, U, fin efficiency, fan, bundle, ITD, JSON |
| `ColumnSpecificationTest` | Column spec purity/recovery/flow rate tests |

### New Documentation

- `docs/development/NEQSIM_VS_UNISIM_COMPARISON.md` — NeqSim vs UniSim feature comparison
- `docs/process/process-simulation-enhancements.md` — User guide for all new capabilities
- `examples/notebooks/air_cooler_and_packed_column.ipynb` — Jupyter notebook example

### Agents/Skills to Update

- `neqsim-capability-map` — Add PackedColumn, ShortcutDistillationColumn, ColumnInternalsDesigner,
  TrayHydraulicsCalculator, PackingHydraulicsCalculator, PVFflash, AmineSystem, AirCooler
- `neqsim-api-patterns` — Add packed column, shortcut distillation, air cooler, PVF flash,
  amine system, stream summary patterns
- `CONTEXT.md` — Add distillation internals, amine framework to repo map
- `docs/development/CODE_PATTERNS.md` — Add packed column, shortcut, air cooler, amine patterns

---

## 2026-03-27 — Column Specification Flexibility

### New Classes

- **`ColumnSpecification`** (`process.equipment.distillation`) — Represents one
  degree-of-freedom specification for a distillation column. Five specification
  types via `SpecificationType` enum:
  - `PRODUCT_PURITY` — mole-fraction purity target for a product stream
  - `REFLUX_RATIO` — condenser reflux ratio (L/D)
  - `COMPONENT_RECOVERY` — fractional recovery of a named component (0–1)
  - `PRODUCT_FLOW_RATE` — molar flow rate target (kmol/h)
  - `DUTY` — condenser or reboiler duty (W)
  - `ProductLocation` enum: `TOP`, `BOTTOM`
  - Configurable tolerance (default 1e-4) and max iterations (default 20)
  - Full input validation, serializable

### Updated Classes

- **`DistillationColumn`** — Integrated `ColumnSpecification` support:
  - New convenience methods: `setTopProductPurity(component, target)`,
    `setBottomProductPurity(component, target)`, `setCondenserRefluxRatio(ratio)`,
    `setReboilerBoilupRatio(ratio)`, `setTopComponentRecovery(component, fraction)`,
    `setBottomComponentRecovery(component, fraction)`, `setTopProductFlowRate(rate)`,
    `setBottomProductFlowRate(rate)`, `getTopSpecification()`, `getBottomSpecification()`
  - Outer secant-method adjustment loop (`solveWithSpecifications()`) iterates
    condenser/reboiler temperatures to satisfy purity, recovery, or flow-rate specs.
    Safeguards: max step 50 K, temperature bounds 100–1000 K.
  - Direct-set specs (reflux ratio, duty) applied before inner solve without outer loop.
  - Builder pattern extended: `topSpecification()`, `bottomSpecification()`,
    `topProductPurity()`, `bottomProductPurity()` methods.

### Usage

```java
// Product purity specification
DistillationColumn column = new DistillationColumn("T-100", 25, true, true);
column.addFeedStream(feed, 12);
column.setTopPressure(25.0, "bara");
column.setTopProductPurity("ethane", 0.95);      // 95 mol% ethane overhead
column.setBottomProductPurity("propane", 0.98);   // 98 mol% propane bottoms
column.run();

// Component recovery specification
column.setTopComponentRecovery("ethane", 0.99);   // 99% ethane recovery overhead
column.run();

// Reflux ratio specification (applied directly, no outer loop)
column.setCondenserRefluxRatio(3.5);
column.run();

// Builder pattern with specs
DistillationColumn col = DistillationColumn.builder()
    .name("Deethanizer")
    .numberOfTrays(25)
    .hasCondenser(true)
    .hasReboiler(true)
    .topPressure(25.0)
    .topProductPurity("ethane", 0.95)
    .bottomProductPurity("propane", 0.98)
    .build();
```

### Agents/Skills to Update

- `neqsim-api-patterns` — Add column specification pattern
- `docs/process/equipment/distillation.md` — Add Column Specifications section
- `docs/development/CODE_PATTERNS.md` — Add distillation specification pattern

---

## 2026-03-26 — Heat Exchanger Thermal-Hydraulic Design Toolkit

### New Classes

- **`ThermalDesignCalculator`** (`process.mechanicaldesign.heatexchanger`) — Central
  calculator for tube-side and shell-side heat transfer coefficients, overall U,
  pressure drops, and zone-by-zone analysis. Supports Gnielinski (tube-side) and
  Kern or Bell-Delaware (shell-side) methods.
  - Inner enum: `ShellSideMethod` (`KERN`, `BELL_DELAWARE`)

- **`BellDelawareMethod`** (`process.mechanicaldesign.heatexchanger`) — Static utility
  for industry-standard Bell-Delaware shell-side HTC and pressure drop with J-factor
  correction factors (Jc, Jl, Jb, Js, Jr) and Zhukauskas correlation for tube banks.

- **`VibrationAnalysis`** (`process.mechanicaldesign.heatexchanger`) — Flow-induced
  vibration screening per TEMA RCB-4.6. Evaluates vortex shedding (Von Karman),
  fluid-elastic instability (Connors), and acoustic resonance.
  - Inner class: `VibrationResult` with pass/fail, natural frequency, critical velocity

- **`LMTDcorrectionFactor`** (`process.mechanicaldesign.heatexchanger`) — LMTD correction
  factor F_t for multi-pass configurations using Bowman-Mueller-Nagle (1940) method.
  Supports 1-N shell passes, calculates R and P parameters, recommends minimum shell
  passes needed.

- **`InterfacialFriction`** (`process.equipment.pipeline.twophasepipe.closure`) —
  Interfacial friction correlations for two-fluid pipe model. Flow regime-dependent:
  Taitel-Dukler (stratified smooth), Andritsos-Hanratty (stratified wavy), Wallis
  (annular), Oliemans (slug).
  - Inner class: `InterfacialFrictionResult` with shear, friction factor, slip velocity

### Updated Classes

- **`ShellAndTubeDesignCalculator`** — Major expansion: now includes ASME VIII Div.1
  pressure design (UHX-13 tubesheet, UG-27 MAWP, UG-37 nozzle reinforcement, UG-99
  hydro test), NACE MR0175/ISO 15156 sour service assessment, thermal-hydraulic
  integration (auto-runs `ThermalDesignCalculator` + `VibrationAnalysis` when fluid
  properties are provided), weight/cost estimation with Bill of Materials.

- **`HeatExchangerMechanicalDesign`** — New high-level orchestrator auto-selecting
  exchanger type (shell-and-tube, plate, air-cooled) based on configurable criteria
  (`MIN_AREA`, `MIN_WEIGHT`, `MIN_PRESSURE_DROP`). Handles TEMA class (R/C/B),
  shell types (E/F/G/H/J/K/X), fouling resistances, velocity limits, materials,
  and NACE sour service.

- **`HeatExchanger`** — Added `getRatingCalculator()` returning `ThermalDesignCalculator`
  for rating mode. Added `getThermalEffectiveness()` and `calcThermalEffectivenes(NTU, Cr)`.

- **`TwoFluidPipe`** — Enhanced with boundary condition API (STREAM_CONNECTED,
  CONSTANT_FLOW, CONSTANT_PRESSURE, CLOSED), elevation profile support, temperature
  profile output (K and °C), liquid inventory calculation, cooldown time estimation.

- **`TwoFluidConservationEquations`** — Extended to 7 conservation equations for
  three-phase (gas/oil/water) with separate oil and water momentum. Uses AUSM+ flux
  scheme and MUSCL reconstruction.

- **`Pump`** — Added pump curve support with affinity law scaling, cavitation detection
  (NPSH available vs required), operating status monitoring, outlet temperature mode.

### Usage

```java
// Standalone thermal design
ThermalDesignCalculator calc = new ThermalDesignCalculator();
calc.setTubeODm(0.01905);
calc.setTubeIDm(0.01483);
calc.setTubeLengthm(6.0);
calc.setTubeCount(200);
calc.setTubePasses(2);
calc.setTubePitchm(0.0254);
calc.setTriangularPitch(true);
calc.setShellIDm(0.489);
calc.setBaffleSpacingm(0.15);
calc.setBaffleCount(30);
calc.setBaffleCut(0.25);
calc.setTubeSideFluid(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
calc.setShellSideFluid(820.0, 0.003, 2200.0, 0.13, 8.0);
calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
calc.calculate();
String json = calc.toJson();

// Vibration screening
VibrationAnalysis.VibrationResult result = VibrationAnalysis.performScreening(
    tubeOD, tubeID, unsupportedSpan, tubeMaterialE, tubeDensity,
    fluidDensityTube, fluidDensityShell, endCondition,
    crossflowVelocity, tubePitch, triangularPitch, shellID, sonicVelocity
);
boolean safe = result.passed;

// LMTD correction factor
double ft = LMTDcorrectionFactor.calcFt(tHotIn, tHotOut, tColdIn, tColdOut, shellPasses);
int minShells = LMTDcorrectionFactor.requiredShellPasses(tHotIn, tHotOut, tColdIn, tColdOut);

// Full mechanical design with thermal-hydraulic
ShellAndTubeDesignCalculator stCalc = new ShellAndTubeDesignCalculator();
stCalc.setTubeSideFluidProperties(density, viscosity, cp, k, massFlow, isHeating);
stCalc.setShellSideFluidProperties(density, viscosity, cp, k, massFlow);
stCalc.calculate();  // runs mech + thermal + vibration
String report = stCalc.toJson();
```

### Agents/Skills to Update

- `neqsim-capability-map` — Add ThermalDesignCalculator, BellDelawareMethod, VibrationAnalysis, LMTDcorrectionFactor, InterfacialFriction
- `neqsim-api-patterns` — Add HX thermal design pattern
- `CONTEXT.md` — Add HX thermal design to repo map
- `docs/REFERENCE_MANUAL_INDEX.md` — Add thermal_hydraulic_design.md entry

---

## 2026-03-26 — InstrumentScheduleGenerator and Updated Engineering Deliverables

### New Classes

- **`InstrumentScheduleGenerator`** (`process.mechanicaldesign`) — ISA-5.1 tagged
  instrument schedule generator that bridges engineering deliverables and dynamic
  simulation. Walks a `ProcessSystem`, creates `MeasurementDeviceInterface` objects
  (PT, TT, LT, FT) with `AlarmConfig` (HH/H/L/LL thresholds) and SIL ratings.
  With `setRegisterOnProcess(true)`, live devices are registered on the ProcessSystem.

### Updated Classes

- **`StudyClass`** — Added `INSTRUMENT_SCHEDULE` to `DeliverableType` enum.
  CLASS_A now produces 7 deliverables (was 6), CLASS_B produces 4 (was 3).
- **`EngineeringDeliverablesPackage`** — Added `generateInstrumentSchedule()` and
  `getInstrumentSchedule()`. The `INSTRUMENT_SCHEDULE` case is handled in `generate()`.

### StudyClass Deliverable Counts (IMPORTANT for tests)

| Study Class | Count | Deliverables |
|-------------|-------|-------------|
| CLASS_A | 7 | PFD, Thermal, Alarm/Trip, Spares, Fire, Noise, Instrument Schedule |
| CLASS_B | 4 | PFD, Thermal, Fire, Instrument Schedule |
| CLASS_C | 1 | PFD |

### Usage

```java
InstrumentScheduleGenerator gen = new InstrumentScheduleGenerator(process);
gen.setRegisterOnProcess(true);  // creates live MeasurementDevice objects
gen.generate();
List<InstrumentScheduleGenerator.InstrumentEntry> entries = gen.getEntries();
String json = gen.toJson();

// Through package
EngineeringDeliverablesPackage pkg =
    new EngineeringDeliverablesPackage(process, StudyClass.CLASS_A);
pkg.generate();  // includes instrument schedule
InstrumentScheduleGenerator instrSchedule = pkg.getInstrumentSchedule();
```

### Agents/Skills Updated

- `neqsim-capability-map` SKILL — Expanded Measurement Devices table, added Engineering Deliverables subsection
- `neqsim-api-patterns` SKILL — Added Engineering Deliverables section with instrument schedule pattern
- `engineering.deliverables.agent.md` — Added instrument schedule deliverable section and code examples
- `field.development.agent.md` — Added item 17 (instrument schedule), updated StudyClass table and class map
- `AGENTS.md` — Updated key paths table
- `CONTEXT.md` — Updated repo map and key locations table

---

## 2026-03-25 — TwoFluidPipe Boundary Condition API

### New API

Added public setters for configuring inlet and outlet boundary conditions during
transient `TwoFluidPipe` simulations. Includes CLOSED BC for shut-in/surge scenarios.

### New Methods

```java
// Set boundary condition types
pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);  // default
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
pipe.setInletBoundaryCondition(BoundaryCondition.CLOSED);            // NEW: blocked
pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE); // default
pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);            // NEW: blocked

// Query boundary condition types
BoundaryCondition inletBC = pipe.getInletBoundaryCondition();
BoundaryCondition outletBC = pipe.getOutletBoundaryCondition();

// Set explicit values for CONSTANT_FLOW / CONSTANT_PRESSURE BCs
pipe.setInletMassFlow(50.0);             // kg/s
pipe.setInletMassFlow(180000, "kg/hr");  // with unit
pipe.setInletPressure(60.0, "bara");     // with unit

// Convenience methods for shut-in scenarios
pipe.closeOutlet();                       // Set outlet BC to CLOSED
pipe.openOutlet();                        // Restore to CONSTANT_PRESSURE
pipe.openOutlet(30.0, "bara");            // Open with specified pressure
pipe.closeInlet();                        // Set inlet BC to CLOSED
pipe.openInlet();                         // Restore to STREAM_CONNECTED
boolean closed = pipe.isOutletClosed();   // Check if outlet is blocked
boolean closed = pipe.isInletClosed();    // Check if inlet is blocked
```

### Boundary Condition Types

| Type | Description |
|------|-------------|
| `STREAM_CONNECTED` | Flow rate, T, composition from connected stream (default inlet) |
| `CONSTANT_FLOW` | Fixed mass flow via `setInletMassFlow()` |
| `CONSTANT_PRESSURE` | Fixed pressure (default outlet, optional inlet) |
| `CLOSED` | Zero velocity (blocked/shut-in) — pressure floats |

### Common Configurations

| Config | Inlet BC | Outlet BC | Inlet P | Flow |
|--------|----------|-----------|---------|------|
| Default | STREAM_CONNECTED | CONSTANT_PRESSURE | Computed | From stream |
| Explicit flow | CONSTANT_FLOW | CONSTANT_PRESSURE | Computed | Fixed |
| Both P fixed | CONSTANT_PRESSURE | CONSTANT_PRESSURE | Fixed | Computed |
| Shut-in | STREAM_CONNECTED | CLOSED | Computed | From stream |
| Blowdown | CLOSED | CONSTANT_PRESSURE | Floats | Zero |
| Blocked pipe | CLOSED | CLOSED | Floats | Zero |

### Python Usage

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe
BoundaryCondition = TwoFluidPipe.BoundaryCondition

pipe = TwoFluidPipe("Pipeline", feed)
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW)
pipe.setInletMassFlow(50.0)
pipe.setOutletPressure(30.0, "bara")

# Shut-in scenario
pipe.closeOutlet()
for t in range(60):
    pipe.runTransient(1.0)
pipe.openOutlet(30.0, "bara")  # Reopen
```

### Documentation

- Updated [Pipeline Recipes](docs/cookbook/pipeline-recipes.md) with Boundary Conditions section

### Migration

No breaking changes. Existing code using default BCs continues to work unchanged.

---

## 2026-06-18 — TwoFluidPipe Transient & Pressure Gradient Improvements

### Bug Fixes

- **Transient inlet pressure override (FIXED):** `applyBoundaryConditions()` was
  overwriting the inlet pressure from the stream during transient runs, preventing
  the pressure profile from evolving. Added `isTransientMode` boolean flag; when
  `true` (set automatically by `runTransient()`), inlet pressure comes from
  `reconstructPressureProfile()` (backward march from fixed outlet BC) instead of
  from the inlet stream.

- **Outlet pressure captured before convergence (FIXED):** `outletPressure` was
  being captured before `runSteadyState()` converged, recording the initial guess
  (~54 bar) rather than the converged value (~59 bar). Now captured after
  steady-state convergence.

### Updated Files

- **`TwoFluidPipe.java`** (`process.equipment.pipeline`):
  - New field: `private boolean isTransientMode = false;`
  - New method: `reconstructPressureProfile()` — backward marches from fixed outlet
    boundary condition to compute inlet pressure from the local pressure gradient.
  - New method: `calcDarcyFrictionFactor(rho, velocity, D, mu)` — extracted Haaland
    equation (turbulent), 64/Re (laminar), linear interpolation (transitional
    Re 2300–4000). Used in `estimatePressureGradient()`.
  - Updated `estimatePressureGradient()`: replaced holdup-weighted viscosity
    (`αG*μG + αL*μL`) with McAdams quality-based harmonic averaging
    (`1/(x/μG + (1-x)/μL)`) where x is vapor mass fraction. Density remains
    holdup-weighted (`αG*ρG + αL*ρL`).
  - Updated `applyBoundaryConditions()`: inlet pressure only set from stream when
    `!isTransientMode`.
  - Updated `runTransient()`: sets `isTransientMode = true` at entry, calls
    `reconstructPressureProfile()` for inlet pressure.

### New Test File

- **`TwoFluidPipeBenchmarkTest.java`** (`test/.../pipeline/`) — 19 benchmark tests
  in 8 categories:
  1. **SinglePhaseTests** (2): Gas and liquid horizontal flow
  2. **TwoPhaseHorizontalTests** (3): Gas-dominated, liquid-dominated, intermediate GOR
  3. **InclinedFlowTests** (3): Uphill 5°, downhill 5°, vertical riser
  4. **ThreePhaseTests** (2): Moderate and high water cut
  5. **ConsistencyTests** (3): dP monotonicity, smooth pressure profile, holdup sum = 1
  6. **TransientTests** (1): 200 m pipe, 100% flow rate step-change, holdup evolution
  7. **CrossValidationTests** (1): GLR sweep 0.50–0.95 vs PipeBeggsAndBrills
  8. **LiteratureValidationTests** (4): Moody chart, holdup vs gas velocity, gravity in
     vertical riser, diameter D⁻⁵ scaling

### Benchmark Results Summary

| Test | TwoFluidPipe / BeggsAndBrill | Notes |
|------|-----|-------|
| Single-phase gas | 0.98 | Excellent agreement |
| Two-phase GLR 0.50–0.95 | 0.81–1.33 | Within engineering accuracy |
| Vertical riser | 1.04 bar gravity dP | Matches ρgH calculation |
| Diameter scaling (6"/12") | 33.7× | Close to theoretical ~32× (D⁻⁵) |
| Transient holdup evolution | 0.19 → 0.09 | Holdup decreases after flow increase |

### Migration

No breaking API changes. Existing code calling `run()` and `runTransient()` will
behave identically (steady-state) or more correctly (transient now evolves).

---

## 2026-03-24 — Field Development Agent and Skills

### New Agent

- **`@field.development`** (`.github/agents/field.development.agent.md`) — Expert agent for oil & gas field development workflows: concept selection, subsea tieback, production forecasting, and project economics (NPV/IRR). Orchestrates concept screening through final investment decision.

### New Skills (4)

- **`neqsim-field-development`** — Field development lifecycle (DG1→Operations), concept selection, reservoir/well/facility API patterns
- **`neqsim-field-economics`** — NPV, IRR, cash flow engines, Norwegian NCS and UK UKCS tax models, cost estimation
- **`neqsim-subsea-and-wells`** — Subsea equipment APIs, well casing design (API 5C3/NORSOK D-010), SURF cost estimation, tieback analysis
- **`neqsim-production-optimization`** — Decline curves, bottleneck analysis, gas lift optimization, IOR/EOR screening, emissions tracking

### Updated Files

- `AGENTS.md` — Added field development paths and skills references
- `CONTEXT.md` — Updated agent/skill counts (16 agents, 14 skills)
- `.github/agents/router.agent.md` — Added field development routing
- `.github/agents/README.md` — Added field development section
- `.github/agents/solve.task.agent.md` — Added `@field.development` to delegation table
- `docs/integration/ai_agents_reference.md` — Added agent entry, 4 skill entries, updated cross-reference tables
- `docs/integration/ai_agentic_programming_intro.md` — Updated count and added agent to catalog
- `docs/integration/ai_workflow_examples.md` — Added Example 8: Field Development Concept Selection
- `docs/fielddevelopment/README.md` — Added AI Agent & Skills section
- `docs/REFERENCE_MANUAL_INDEX.md` — Updated description

### Migration

No code changes needed. Use `@field.development` for field development tasks that were previously handled by `@solve.task`.

---

## 2026-03-23 — CO2 Injection Well Analysis Module (NIP-1 to NIP-6)

### New Classes

- **`CO2InjectionWellAnalyzer`** (`process.equipment.pipeline`) — High-level safety orchestrator for CO2 injection wells:
  - Steady-state wellbore flow via PipeBeggsAndBrills
  - Phase boundary scanning (P-T space flash grid)
  - Impurity enrichment mapping in two-phase region
  - Shutdown safety assessment at various trapped WHPs
  - Returns `isSafeToOperate()` boolean and comprehensive `getResults()` map
  - API: `setFluid()`, `setWellGeometry()`, `setOperatingConditions()`, `setFormationTemperature()`, `addTrackedComponent()`, `runFullAnalysis()`

- **`ImpurityMonitor`** (`process.measurementdevice`) — Phase-partitioned composition tracking device:
  - Extends `StreamMeasurementDeviceBaseClass`
  - Tracks gas/liquid/bulk mole fractions and enrichment factors (K-values = y/z)
  - Configurable alarm thresholds per component
  - API: `addTrackedComponent(name, alarmThreshold)`, `getGasPhaseMoleFraction()`, `getEnrichmentFactor()`, `isAlarmExceeded()`, `getFullReport()`

- **`TransientWellbore`** (`process.equipment.pipeline`) — Shutdown cooling transient model:
  - Extends `Pipeline`
  - Exponential temperature decay toward formation temperature (geothermal gradient)
  - Vertical segmentation with TP flash at each depth and time step
  - Tracks phase evolution and impurity enrichment over time
  - Inner class `TransientSnapshot` stores per-timestep depth profiles
  - API: `setWellDepth()`, `setFormationTemperature(topK, bottomK)`, `setShutdownCoolingRate(tau_hr)`, `runShutdownSimulation(hours, dt)`

- **`CO2FlowCorrections`** (`process.equipment.pipeline`) — Static utility for CO2-specific flow corrections:
  - `isCO2DominatedFluid()` — checks >50 mol% CO2
  - `getLiquidHoldupCorrectionFactor()` — returns 0.70-0.85 based on reduced temperature
  - `getFrictionCorrectionFactor()` — returns 0.85-0.95
  - `estimateCO2SurfaceTension()` — Sugden correlation
  - `isDensePhase()`, `getReducedTemperature()`, `getReducedPressure()`

### Modified Classes

- **`PipeBeggsAndBrills`** — Added formation temperature gradient support (NIP-1):
  - New method: `setFormationTemperatureGradient(double inletTemp, double gradient, String unit)`
  - Enables depth-dependent heat transfer with geothermal gradient
  - Sign convention: negative gradient = temperature increases with depth

### Test Coverage

- 19 tests in `CO2InjectionNIPsTest.java` covering all NIP classes

### Documentation

- New doc: `docs/process/co2_injection_well_analysis.md`
- Updated: `REFERENCE_MANUAL_INDEX.md`, `docs/process/README.md`

---

## 2026-03-22 — Motor Mechanical Design and Combined Equipment Design Report

### New Classes

- **`MotorMechanicalDesign`** (`process.mechanicaldesign.motor`) — Physical/mechanical design of electric motors:
  - Foundation loads (static + dynamic) and mass per IEEE 841 (3:1 ratio)
  - Cooling classification per IEC 60034-6 (IC411/IC611/IC81W)
  - Bearing selection and L10 life per ISO 281 (ball vs roller, lubrication)
  - Vibration limits per IEC 60034-14 Grade A and ISO 10816-3 zone classification
  - Noise assessment per IEC 60034-9 and NORSOK S-002 (83 dB(A) at 1m)
  - Enclosure/IP rating per IEC 60034-5, Ex marking per IEC 60079 (Zone 0/1/2)
  - Environmental derating per IEC 60034-1 (altitude: 1%/100m above 1000m; temperature: 2.5%/°C above 40°C)
  - Motor weight and dimensional estimation
  - Constructors: `MotorMechanicalDesign(double shaftPowerKW)`, `MotorMechanicalDesign(ElectricalDesign)`

- **`EquipmentDesignReport`** (`process.mechanicaldesign`) — Combined design report for any process equipment:
  - Orchestrates mechanical design + electrical design + motor mechanical design
  - Produces FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE verdict
  - Checks: motor undersizing, excessive derating, noise exceedance, low bearing life
  - `toJson()` — comprehensive JSON with all three design disciplines
  - `toLoadListEntry()` — summary for electrical load list integration
  - Works with any `ProcessEquipmentInterface` (compressor, pump, separator, etc.)

### Key API Methods

```java
// Motor mechanical design — standalone
MotorMechanicalDesign motorDesign = new MotorMechanicalDesign(250.0);
motorDesign.setPoles(4);
motorDesign.setAmbientTemperatureC(45.0);
motorDesign.setAltitudeM(500.0);
motorDesign.setHazardousZone(1);
motorDesign.calcDesign();
motorDesign.toJson();

// Combined report — from any equipment
EquipmentDesignReport report = new EquipmentDesignReport(compressor);
report.setUseVFD(true);
report.setRatedVoltageV(6600);
report.setHazardousZone(1);
report.generateReport();
report.getVerdict();   // "FEASIBLE" / "FEASIBLE_WITH_WARNINGS" / "NOT_FEASIBLE"
report.toJson();
```

### Bug Fix
- Fixed IP rating override in Zone 0 hazardous areas — IEEE 841 IP55 minimum no longer overrides Zone 0 IP66 requirement

### Test Coverage
- 22 new tests in `MotorMechanicalDesignTest`: standalone design, small/large motors, altitude/temperature derating, hazardous area enclosure, vibration zones, NORSOK noise compliance, bearing L10 life, VFD notes, applied standards, compressor integration, JSON/Map output, combined reports

### Documentation
- New doc: `docs/process/motor-mechanical-design.md`
- Updated: `REFERENCE_MANUAL_INDEX.md`, capability map, `mechanical_design.md`, `electrical-design.md`

---

## 2026-03-22 — Heat Exchanger Mechanical Design Standards Expansion

### New Data Files
- **`HeatExchangerTubeMaterials.csv`** — 22 material grades for tubes and shells with
  SMYS, SMTS, allowable stress, thermal conductivity, NACE compliance, and temperature limits.
  Covers SA-179, SA-213 (T11/T22/TP304/304L/316/316L/321), duplex/super-duplex, Cu-Ni,
  titanium, Inconel, Hastelloy, Incoloy, and shell plate materials.

### Standards Database Additions
| Standard | Equipment Types | New Entries |
|----------|----------------|-------------|
| API-660 9th Ed | HeatExchanger | 21 entries (design margins, velocity limits, hydro test, joint efficiency, vibration) |
| API-661 7th Ed | HeatExchanger/Cooler | 9 entries (air cooler fins, face velocity, fan efficiency) |
| API-662 1st Ed | HeatExchanger | 10 entries (plate HX gasketed/welded pressure/temp limits) |
| NORSOK-P-002 Rev 5 | HeatExchanger/Cooler/Heater | 14 entries (duty/area/pressure margins, velocity limits) |
| NORSOK-M-001 Rev 6 | HeatExchanger/Cooler/Heater | 7 entries (min/max design temp, hardness, H2S limits) |
| ASME VIII Div.1 | HeatExchanger | 19 entries (UG-27, UHX-13, UG-37, UG-99, allowable stresses, joint efficiencies, flange ratings) |
| ISO-16812 | HeatExchanger | 12 entries (velocity, fouling resistance, baffle cut range) |
| ISO-15547 | HeatExchanger | 3 entries (plate-fin aluminium HX) |
| EN-13445 | HeatExchanger | 3 entries (pressure, joint efficiency, corrosion allowance) |
| PD-5500 | HeatExchanger | 3 entries (pressure, joint efficiency, corrosion allowance) |

### `ShellAndTubeDesignCalculator` Expanded
| New Capability | Standard | Method |
|----------------|----------|--------|
| Tubesheet thickness per UHX-13 | ASME VIII | `calculateTubesheetThicknessUHX()` |
| Nozzle reinforcement per UG-37 | ASME VIII | `calculateNozzleReinforcement()` |
| MAWP back-calculation per UG-27 | ASME VIII | `calculateMAWP()` |
| Hydrostatic test pressure per UG-99 | ASME VIII | `calculateHydroTestPressure()` |
| Material property lookup from DB | HeatExchangerTubeMaterials | `loadMaterialProperties()` |
| NACE MR0175 sour service assessment | NACE MR0175 / NORSOK M-001 | `performNACEAssessment()` |
| Shell/tube material grade tracking | — | `setShellMaterialGrade()`, `setTubeMaterialGrade()` |

### `HeatExchangerMechanicalDesign` Integration
- New fields: `shellMaterialGrade`, `tubeMaterialGrade`, `h2sPartialPressure`,
  `sourServiceAssessment`, `shellJointEfficiency`
- `calcDesign()` now runs `ShellAndTubeDesignCalculator` with ASME VIII and NACE
- `getShellAndTubeCalculator()` provides access to detailed calculator results
- `HeatExchangerMechanicalDesignResponse` updated with MAWP, hydro test, NACE fields

### Migration Notes
- Existing `calcDesign()` calls work unchanged — new calculator runs automatically
- To access ASME/NACE results: `design.getShellAndTubeCalculator().getMawpShellSide()`
- For sour service: set `design.setSourServiceAssessment(true)` and `setH2sPartialPressure(pp)`
- Material grades default to SA-516-70 (shell) and SA-179 (tubes) if not set

---

## 2026-03-22 — Compressor Casing Mechanical Design (API 617 / ASME VIII)

### New Class
- **`CompressorCasingDesignCalculator`** — Standalone calculator for compressor casing
  pressure containment design per API 617 and ASME Section VIII Div. 1.

### Capabilities Added
| Feature | Standard |
|---------|----------|
| Casing wall thickness (UG-27 formula) | ASME VIII Div. 1 |
| Material selection with SMYS/SMTS (9 grades) | ASME II Part D |
| Temperature derating of allowable stress | ASME II Part D Table 1A |
| Nozzle load analysis (force/moment scaling) | API 617 Table 3 |
| Flange rating verification with temp derating | ASME B16.5 / B16.47 |
| Hydrostatic test pressure | ASME VIII UG-99 |
| Corrosion allowance integration | API 617 |
| NACE MR0175 / ISO 15156 sour service check | NACE MR0175 |
| Thermal growth & differential expansion | API 617 |
| Split-line bolt sizing (horizontally-split) | API 617 |
| Barrel casing outer/inner/end-cover sizing | ASME VIII UG-34 |
| MAWP back-calculation | ASME VIII |
| Automatic material recommendation | — |

### Integration
- `CompressorMechanicalDesign.calcDesign()` now automatically runs the casing
  calculator after process sizing and populates
  `getCasingDesignCalculator()` with results.
- New configuration methods on `CompressorMechanicalDesign`:
  `setCasingMaterialGrade(String)`, `setCasingCorrosionAllowanceMm(double)`,
  `setH2sPartialPressureKPa(double)`.
- `CompressorMechanicalDesignResponse` includes full casing design data in
  the `casingDesign` section of JSON output.

### New Data Files
| File | Content |
|------|---------|
| `designdata/CompressorCasingMaterials.csv` | 20 material grades with mechanical properties |
| `designdata/standards/api_standards.csv` | +22 API-617 compressor entries |
| `designdata/standards/asme_standards.csv` | +18 ASME VIII / B16.5 compressor entries |

### Agent Migration
- When writing compressor casing design code, use `CompressorCasingDesignCalculator`
  directly or via `comp.getMechanicalDesign().getCasingDesignCalculator()`.
- For sour service: set `design.setNaceCompliance(true)` and
  `design.setH2sPartialPressureKPa(value)` before calling `calcDesign()`.
- For automatic material selection: call `casingCalc.recommendMaterial()`.

---

## 2026-03-21 — Capability Scout Agent and Capability Map Skill

### New Agent
- **`@capability.scout`** — Analyzes engineering tasks, identifies required capabilities,
  checks NeqSim coverage, identifies gaps, writes NIPs, recommends skills and agent pipelines.
  Use before starting complex multi-discipline tasks.

### New Skill
| Skill | Purpose |
|-------|---------|
| `neqsim-capability-map` | Structured inventory of all NeqSim capabilities by discipline (EOS, equipment, PVT, standards, mechanical design, flow assurance, safety, economics) |

### Updated Files
- `solve.task.agent.md` — Phase 1.5 Section 7b.3 now recommends invoking `@capability.scout` for comprehensive tasks
- `router.agent.md` — Added capability scout to routing table and Pattern 6 (Capability Assessment + Implementation)
- `README.md` — Added capability scout to Routing & Help section and capability-map to Skills table
- `AGENTS.md` — Added capability scout to Key Paths and capability-map to Skills Reference
- `CONTEXT.md` — Updated agent count to 14, skill count to 9
- `copilot-instructions.md` — Added Capability Assessment bullet point

---

## 2026-03-21 — Agent Ecosystem v2: Router, Skills, Validation

### New Agents
- **`@neqsim.help` (router agent)** — Routes requests to specialist agents. Use when unsure which agent to pick.

### New Skills (6 added)
| Skill | Purpose |
|-------|---------|
| `neqsim-troubleshooting` | Recovery strategies for convergence failures, zero values, phase issues |
| `neqsim-input-validation` | Pre-simulation input checks (T, P, composition, component names, order of operations) |
| `neqsim-regression-baselines` | Baseline management for preventing silent accuracy drift |
| `neqsim-agent-handoff` | Structured schemas for agent-to-agent result passing |
| `neqsim-physics-explanations` | Plain-language explanations of thermodynamic and process phenomena |
| `neqsim-performance-guide` | Simulation time estimates and optimization strategies (in notebook-patterns skill) |

### Updated Files
- `solve.task.agent.md` — Added auto-search past solutions (Phase 0, Step 1.5) and cross-discipline consistency gate
- `README.md` — Updated with new router agent, skills table, and cross-references
- `neqsim-notebook-patterns/SKILL.md` — Added performance estimation table and optimization tips

---

## 2026-03-14 — Fix IEC 60534 Gas Valve Sizing

### Changed
- `ControlValveSizing_IEC_60534.java` — Gas valve Cv now uses standard volumetric flow instead of actual
- `ControlValveSizing_IEC_60534_full.java` — Same fix applied to full version

### Migration
- If you have code using `sizeControlValveGas()`, results will now be correct (previously ~98% too low at 50 bara)
- No API change — same methods, corrected internal calculations

---

## 2026-03-10 — Process Architecture Improvements

### New APIs
| API | Class | Description |
|-----|-------|-------------|
| `getInletStreams()` | `ProcessEquipmentInterface` | Returns list of inlet streams for any equipment |
| `getOutletStreams()` | `ProcessEquipmentInterface` | Returns list of outlet streams for any equipment |
| `addController(tag, ctrl)` | `ProcessEquipmentBaseClass` | Attach named controller to equipment |
| `getController(tag)` | `ProcessEquipmentBaseClass` | Retrieve controller by tag name |
| `getControllers()` | `ProcessEquipmentBaseClass` | Get all named controllers as map |
| `connect(src, dst, type, label)` | `ProcessSystem` | Record typed connection metadata |
| `getConnections()` | `ProcessSystem` | Query all recorded connections |
| `getAllElements()` | `ProcessSystem` | Get all equipment, controllers, and measurements |

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `ProcessElementInterface` | `process` | Unified supertype for equipment, controllers, measurements |
| `MultiPortEquipment` | `process.equipment` | Abstract base for multi-inlet/outlet equipment |
| `ProcessConnection` | `process.processmodel` | Typed connection metadata (MATERIAL/ENERGY/SIGNAL) |

### Migration
- **Backward compatible** — all existing code continues to work
- Legacy `setController()`/`getController()` still work alongside named controllers
- `getInletStreams()`/`getOutletStreams()` default to empty lists for classes that don't override

---

## 2026-03-09 — CO2 Corrosion Analyzer

### New Classes
| Class | Package | Purpose |
|-------|---------|---------|
| `CO2CorrosionAnalyzer` | `pvtsimulation.flowassurance` | Couples electrolyte CPA EOS with de Waard-Milliams corrosion model |

### Important Note
Must call `chemicalReactionInit()` before `createDatabase(true)` and `setMixingRule(10)` to enable aqueous chemical equilibrium. Without this, pH returns 7.0 (neutral) because H3O+ is not generated.

---

## Pre-2026 — Stable API Reference

### Key Methods (Unchanged)
These core methods have been stable for years and are safe to use:
- `SystemInterface.addComponent(name, moleFraction)`
- `SystemInterface.setMixingRule(rule)`
- `SystemInterface.initProperties()`
- `ThermodynamicOperations.TPflash()` / `PHflash()` / `PSflash()`
- `Stream.setFlowRate(value, unit)` / `setTemperature(value, unit)` / `setPressure(value, unit)`
- `ProcessSystem.add(equipment)` / `run()`
- `Separator.getGasOutStream()` / `getLiquidOutStream()`
- `Compressor.setOutletPressure(value)` / `getPower(unit)`

### Known Method Name Corrections
| Wrong Name (Don't Use) | Correct Name |
|-------------------------|-------------|
| `getUnitOperation("name")` | `getUnit("name")` |
| `characterise()` | `characterisePlusFraction()` |
| `characterize()` | `characterisePlusFraction()` |
| `Optional.isEmpty()` | `!optional.isPresent()` (Java 8) |
