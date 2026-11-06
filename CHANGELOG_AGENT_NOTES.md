Warning: truncated output (original token count: 103279)
Total output lines: 7141

# NeqSim API Changelog — Agent Notes

> **Purpose:** Track API changes that affect agent instructions, code patterns,
> and existing examples. Agents read this file to stay aware of breaking changes,
> deprecated methods, and new capabilities.
>
> Format: most recent changes at the top. Include the date, what changed,
> migration steps, and which agents/skills need updating.

---

## 2026-11-06 — Conservative ideal-gas pig hydraulic coupling

- Added transaction-safe `IdealGasPiggingModel` coupling `PigMotion` to conservative upstream/downstream calorically
  perfect gas inventories. It reuses the strict ideal-gas release model for signed choked/subcritical bypass, resolves
  launcher/receiver contact, and publishes mass, work, energy, impact and adaptive-step diagnostics.
- Evidence covers conservation, direction, choking, breakaway, receipt, serialization, timestep convergence and a
  nearby bypass-diameter trend. The 62 m/52 mm Hendrix dimensions are provenance only; all other fixture inputs are
  synthetic. Distributed/multiphase/experimental pigging remains unqualified.
- Agents/skills: flow-assurance and dynamic-simulation guidance now route this reduced-order gas model separately from
  `TwoFluidPipe`, restart, severe-slugging, receiver-liquid and residual-film claims.

## 2026-11-05 — Flowing transfer area and horizontal trace-volume resolution

- `HydrocarbonPoolTransfer.forStratifiedFlow(...)` uses the local circular-stratified holdup chord along cell arclength.
  Uniform/nonuniform subdivision preserves total area and integrated species sources, including finite heat limits
  and unseeded oil appearance. Coefficients, donor bounds and conservative inventory/energy owners are retained.
- `forHydrostaticPool(...)` remains the distinct per-cell stationary-puddle model. Applying it throughout a flowing
  restart made numerical cell length a kinetic input. The restart screen now explicitly selects the flowing model;
  retained historical static-model failures are not claimed as passing convergence evidence.
- Horizontal `calculateHydrostaticCell(...)` reconstruction now scales its bisection bracket with the circular-segment
  trace depth. Independent controls retain positive volume and interface down to `1e-300` holdup, with the same
  `1e-9` relative-volume gate and no liquid seed, clipping or inventory floor.
- Agents/skills: dynamic-simulation and flow-assurance guidance distinguish static pool depth diagnostics from the
  selected flowing transfer area. See the hydrocarbon-pool-transfer receipt for complete-cycle results and limits.

---

## 2026-11-05 — Restart pressure diagnostics and inclined trace-pool geometry

- `GeometryCalculator.calculateHydrostaticCell(...)` now retains an inclined trace pool's positive leading-edge depth
  directly. This removes centre/range subtractive cancellation for representable long-cell wedges without adding a
  liquid floor, seed or inventory clipping. Truly unresolvable volumes still fail explicitly; the hydrocarbon-pool
  transfer fallback remains reported when that limit is crossed.
- `TwoFluidPipeReport.analyzePressureTrace(...)` provides unfiltered event-window extrema/times, peak-to-peak pressure,
  maximum adjacent-sample rate, total/excess variation, ringing ratio and direction reversals above a caller-supplied
  Pa gate. Preserve the sampling interval and do not treat these metrics as an experimental restart-surge band.
- Agents/skills: dynamic-simulation and flow-assurance guidance now route physical restart studies through the explicit
  trace/fallback and pressure-ringing diagnostics while retaining the separate mesh/time, public-validation and
  moving-pig conservation gates.
- Qualification fixture: retain the same physical terrain under refinement. The final 1250 m is level at -333.5 m
  on every mesh; the previous "last three cells" rule changed the pipe being compared. The earlier 22.82%/24.29%
  inventory differences cannot isolate spatial error. The 10% inventory gates remain unchanged; see the executed
  hydrocarbon-pool-transfer receipt. Maximum single-cell interface area is extensive, not a convergence target.
  The common-polyline rerun still fails at 11.21% peak and 10.86% final oil; no tolerance is relaxed or test disabled.
  This remains numerical screening, not physical restart or hydraulic-pigging qualification.
- Revalidated the exact trace-geometry source against all 222 independent 80-digit pressure-moment controls. The
  source-identity guard remains strict, including wrong-hash, incomplete-output and numerical-corruption controls.

---

## 2026-10-26 — Cross-version `.neqsim` loading: legacy mode, load report, atomic save, OmegaA guard

- **Problem found:** a `.neqsim` file written by NeqSim before 3.10 could not be opened (renamed `tagName`, removed
  separator geometry fields, classes that gained a private `readObject` such as `SystemThermo`, `Separator` and
  `EnergyStream`). Forcing it open revealed two silent faults: XStream skips constructors, so new fields kept JVM
  defaults. `ComponentEos.omegaAOverride` (initializer `NaN`) became 0.0, `calca()` returned 0 and every EOS attraction
  term vanished after `init(0)` (lean gas dew point 25.1 C instead of 34.8 C, no condensate). `energyPorts` was null
  (NPE in `run()`), and separator run caches deserialized from the file made the first `run()` return stored outlets.
- **`NeqSimXtream`:** writes a `neqsim-meta.properties` entry (format, NeqSim and Java versions) before `process.xml`
  and saves through a temporary file plus atomic move (a failed save returns false, keeps the previous file and leaves no
  partial file). Reading skips unknown fields and classes, maps renamed fields (`tagName` to `tagNumber`) and reads the
  plain field layout of classes that now have `readObject`. Files without the metadata entry load in **legacy mode**:
  every field absent from the XML is restored from a freshly constructed template of the same class (reusable values
  only; owner-bound collections become empty), `LegacyLoadRepairable.repairAfterLegacyLoad` rebuilds owner-bound state
  (`ProcessEquipmentBaseClass` re-registers energy ports), and private `readObject` hooks run with a no-op stream.
  `NeqSimXtream.getLastLoadReport()` lists what was skipped, mapped and restored, and a warning is logged when anything
  was.
- **`ComponentEos`:** only a finite positive OmegaA is an override. `setOmegaA` ignores zero, negative and infinite
  values with a warning, and `getOmegaAOverride()` returns `NaN` when no override is active.
- **`Separator` / `ThreePhaseSeparator`:** `lastEnthalpy`, `lastFlowRate`, `lastPressure` and `lastPressureDrop` are
  transient, so loaded or cloned separators always recalculate on the first run.
- **Agents:** to run an old `.neqsim` file, call `NeqSimXtream.openNeqsim`/`ProcessSystem.loadFromNeqsim` and check
  `getLastLoadReport()`; removed fields (for example separator geometry, now in `SeparatorMechanicalDesign`) must be
  re-entered. For UMR-PRU fluids use `setMixingRule("HV", "UNIFAC_UMRPRU")`: the enum-only `setMixingRule(HV)` selects
  NRTL-modified HV.
- **Tests:** `NeqSimXtreamLegacyCompatibilityTest` (fixture written by NeqSim 3.2.1).

---

## 2026-10-24 — Stratified wave roughness: depth-squared form replaces the capped Andritsos-Hanratty enhancement

- **`InterfacialFriction.stratifiedWaveEnhancement`** (new; used by the `TwoFluidPipe` steady stratified holdup root):
  `f_i/f_G = 1 + 80 (h_L/D)^2 min(U_SG/U_SG,t - 1, 4)` above the Andritsos-Hanratty transition. Transient
  wavy-stratified interfacial force retains the published `1 + 15 sqrt(h_L/D)(r - 1)` with its cap of 12 in
  `andritsosHanrattyEnhancement`: applying the steady calibration there suppressed the validated severe-slugging
  blowout/fallback cycle. At production gas densities the ratio `r` is 5-15 and the cap bound everywhere,
  so the interface was equally rough on a thin flat layer (holdup 20-30 % low vs the reference) and on a thick uphill
  layer; the uphill Taitel-Dukler balance then lost its thin root between 1.6 and 2.5 degrees and jumped 0.025 → 0.32
  where the reference rises 0.028, 0.072, 0.14. Selected on 13 reference stratified sections (30-100 bara, flat and
  0.6-4.7 degrees): eleven within 10 %, mean log holdup error 0.24 → 0.06 flat and 0.49 → 0.10 inclined. Square-root
  forms never got below 0.2 on either group whatever the slope, ratio limit or cap; linear-depth forms reached 0.13/0.15.
- Effect vs the reference: undulating lean wet gas dP +12/-2/+33 % → +4/+5/+6 % at ±20/35/50 m; gas-condensate terrain
  +12.6 → +3.8 %; low-rate flat gas condensate / wet gas dP +17/+29 % → +5/+14 % (holdup -23/-22 % → -8/-5 %). Over the
  32-case multiphase baseline: mean holdup error 16.0 → 8.6 %, dP 14.5 → 12.7 %. Single-phase, gas-oil slug and terrain
  gas-oil cases unchanged. NeqSim condenses about 30 % more liquid than the reference's interpolated PVT table in the
  near-dew-point lean gas, which inflates its holdup ratios there independently of the hydraulics.
- `TwoFluidPipeSteadyStateConvergenceTest` terrain bounds now follow the reference (+5/+30/+70 % at 20/35/50 m on that
  line); the former flat 15 % bound encoded the weak response of the capped form. Horizontal "annular" gas condensate
  (+12 % dP, -11 % holdup) is untouched: the reference labels it stratified with partial wetting, which neither closure
  represents; the dP excess is the liquid wall shear of the too-thin layer, not the friction form (separated and mixture
  friction give the same 42 Pa/m there).

## 2026-10-24 — `SlugUnitModel` calibrated to a 32-case OLGA 2025.1 sweep (horizontal slug flow)

- **High-Froude slip reduction (default on):** the unit holdup is `hs + (vB/vt)(1-hs) - vsG/vt`, so unit slip is fixed by
  the translational velocity `vt` and bubble velocity `vB`, not by the film balance. Bendiksen's `1.2 vm` overstated
  slip above Froude 3: OLGA's unit gas velocity falls from 1.25 vm at Froude 2 to about `1.12 - 0.25 vsL/vm` times vm
  above Froude 4.5. Both velocities are scaled towards no-slip (smoothstep over Froude 2.5 to 4.5, weighted by
  cos(theta)^2, so vertical units keep Nicklin). Horizontal gas-oil and gas-oil-water holdup error vs OLGA fell from
  +20..+40 % to within 10 %; the film comes out at 0.11-0.19 and the slug fraction larger, as in OLGA.
  `TwoFluidPipe.setSlugHighFroudeSlipReductionEnabled(false)` restores the plain coefficients.
- **Front-mixing weight:** the Dukler-Hubbard pickup term is now counted with an automatic weight, 0.6 falling to 0.2
  between Froude 4.5 and 7, ramped in between Froude 2.3 and 3.0 and zero beyond about 2 degrees of slope.
  `setSlugFrontMixingLossIncluded(boolean)` still forces 0/1 and `setSlugFrontMixingLossWeight(double)` sets a share.
  Horizontal 25 kg/s gas-oil pressure drop moved from 29 % to 14 % below OLGA, an undulating one from +5 % to +2 %.
- **Tried and not shipped (do not repeat without a terrain check):** a 1.5 m/s floor on the Andritsos-Hanratty wave-onset
  velocity, or a lower enhancement bound, fixes low-rate flat gas-condensate/wet-gas holdup (-22 % to -4 %, dP +17 % to
  +3 %) but raises the undulating lean wet-gas pressure drop vs OLGA at 35 and 50 m amplitude from -2 % and +33 % to
  +43 % and +62 %, and the TwoFluidPipeSteadyStateConvergenceTest terrain fixture from +3 % to +83 % at 50 m. The thin
  film that climbs gentle slopes needs the current enhancement. A film-friction scan and rough-wall stratified friction
  were also neutral or counter-productive.
- Remaining differences (not changed): horizontal annular gas condensate/wet gas is +8..+15 % on pressure drop and
  +50 % on holdup at 140 kg/s, and risers are +11..+18 % (+90 % at low rate) because
  OLGA's riser holdup is nearly no-slip (unit gas velocity 1.01-1.06 vm). `setUseInclinedFilmBridgingCriterion(true)`
  cut the mean riser pressure-drop error from 31 % to 20 % on the five riser cases.
- Tests: `SlugUnitModelTest`, `TwoFluidPipeOlgaGasOilSlugTest`.
- Saved `SlugUnitModel` instances from before the weighted closure preserve their explicit front-mixing on/off
  choice when loaded. They retain the original slip coefficients; newly constructed models keep the calibrated
  defaults. Current streams preserve explicit fractional weights and automatic weighting on a serialization round trip.

## 2026-10-23 — Two-fluid dynamic stability review

- Corrected the legacy IMEX pressure-response sign: compression produces a restoring pressure force rather than
  amplifying inward momentum. This scalar correction still transports phase mass explicitly and does not establish
  unrestricted multiphase acoustic stability.
- Partition unresolved liquid-wall resistance by conservative oil/water mass, matching the bulk velocity used by the
  wall law and keeping combined wall kinetic work nonpositive in counterflow. Gravity uses independent phase masses.
- Exactly absent phases contribute zero external mass, momentum and energy flux even when unused primitive properties
  are non-finite. Their stale reverse velocities no longer trigger outlet-backflow warnings; positive traces remain active.
- Replaced explicit radial thermal exchange with a coupled fluid/layer backward-Euler solve. Added adiabatic
  energy-equation temperature transport and the missing multi-layer elevation-work term. External inlet temperature
  events preserve finite-cell thermal inertia instead of resetting cell temperature outside the energy ledger.
- Radial links include cylindrical half-layer resistances at both boundaries and between layer nodes, restoring
  long-time heat-flux consistency for positive material/film coefficients. The existing IMEX source bound excludes
  active implicit slug friction while retaining explicit oil-water shear, using current tracked-marker membership.
  Extending it globally to Euler/Runge-Kutta
  exposed trace-film timestep collapse in coupled-riser progress tests and is deferred pending implicit mean-cell drag.
- Added analytical, conservation, counterflow, phase-limit and thermal-bound regression tests. Updated the model guide,
  wiki and evidence matrix. Severe-slugging cycles, general terrain well balancing and long-duration liquid-rich
  physical qualification remain open under #3298 and #2907.

## 2026-10-23 — `TwoFluidPipe` transient wall-energy and counterflow corrections

- **Simple transient thermal model:** solve local fluid/wall heat exchange and wall-to-ambient loss together by
  backward Euler. The fluid and wall ledgers use the same heat exchange, avoiding explicit film-exchange instability
  at low fluid inventory or high conductance. Advection and other thermal sources still require timestep refinement.
- **Film coefficient:** use separate absolute gas, oil, and water throughputs. Opposing oil/water flows no longer
  cancel into a zero-throughput film estimate. The mixture-property correlation remains an approximation.
- **State preservation:** `setPhysicalWallFilmSplit(...)` preserves an existing wall-temperature profile, including
  repeated calls with the current setting. Changing the split no longer discards stored wall energy.
- No public API additions. The physical split remains the default; `false` selects equal resistances with the same
  steady overall U-value. Updated `TWOFLUIDPIPE_MODEL.md` with applicability limits and corrected the slug-unit setter
  JavaDoc: front-mixing loss remains excluded unless `setSlugFrontMixingLossIncluded(true)` is selected.

## 2026-10-22 — `TwoFluidPipe` transient: boundary-face pressure traction, outlet-face placement, physical wall film split

- **Behaviour changes (transient only; steady results unchanged):**
  - External inlet and outlet momentum fluxes now carry the *face* pressure (second-order extrapolation from the two end
    cells, or the prescribed outlet pressure) instead of the end-cell centre pressure. The end cells previously received
    half of their pressure force against full gravity and friction, which on an undulating liquid-full line seeded an
    odd-even mass-flow pattern (23/55 kg/s around a 40 kg/s feed) and a 0.43 bar inlet offset. A converged steady state
    is now a fixed point of `runTransient` on terrain (`TwoFluidPipeTerrainTransientNullTest`).
  - With `setCellFaceElevationProfile`, a constant-pressure outlet is placed on the outlet face in the transient as in the
    steady solve (`transientOutletCellPressure()`); the last cell pressure is `outletPressure + 0.5 L grad`.
  - Simple transient thermal model: the overall U is split into a flow-dependent fluid film (`calculateInnerHTC`) and a
    wall-to-ambient path carrying the remaining resistance (`wallFilmSplit`). Outlet-temperature transients after a rate
    change now follow the wall time constant as OLGA does (20 km gas line: 15.7 against OLGA 15.5 degC at 3 h; before
    12.5). `setPhysicalWallFilmSplit(false)` restores the legacy equal split.
- New API: `TwoFluidPipe.setPhysicalWallFilmSplit/isPhysicalWallFilmSplit`, `setSlugFrontMixingLossIncluded/is...`;
  `SlugUnitModel.setFrontMixingLossIncluded` (default false), `Result.wallFrictionGradient`. The private
  `TwoFluidConservationEquations.calcInletFlux/calcOutletFlux` take the face pressure as a second argument.
## 2026-10-22 — VFP lift curve builder, deviated tubing survey and phase-type aware Gray/Beggs-Brill in `TubingPerformance`

- New `neqsim.process.fielddevelopment.reservoir`: `VfpTableBuilder` (VFPPROD/VFPINJ with axes, rate cap, sanity enforcement,
  provenance header), `VlpProvider`, `TubingVlpProvider`, `ForwardVlpProvider`, `RecombinationFluidFactory`, `VfpDeckReader`;
  JSON face `neqsim.mcp.runners.VfpTableRunner` (not a registered MCP tool yet). `ReservoirCouplingExporter.setVlpProvider(...)`
  replaces the placeholder correlation (a warning is logged when the placeholder is used).
- `neqsim.process.equipment.pipeline.TubingPerformance`: `setSurvey(md, tvd, id)` for deviated wells, `calculateOutletPressure`
  and `calculateInletPressureForOutlet` (no stream side effects). **Behaviour changes:** `generateVLPCurve` had a wrong-sign
  update and now solves correctly (returns NaN where no BHP below 1500 bara delivers the wellhead pressure; very high rates in
  small tubing are now NaN instead of a garbage number); BEGGS_BRILL and GRAY read gas and liquid by phase type
  (a trace of liquid no longer jumps the pressure drop of dry gas); `initProperties()` is called after every segment flash.
- Warnings only: `PipeBeggsAndBrills`/`TubingPerformance` Beggs-Brill with gas fraction above 0.97 and inclination above 30 degrees;
  `SimpleReservoir` below `lowPressureLimit` (`isLowPressureLimitReached()`).
- Skills: new `neqsim-vfp-lift-curves`; pointers in `neqsim-integrated-production-and-lifecycle`, `neqsim-subsea-and-wells`,
  `neqsim-production-optimization`, `neqsim-capacity-and-utilization-analysis`, `neqsim-rotating-equipment-design`,
  `neqsim-capability-map`, router agent. Agents: community `vfp-lift-curve-agent`, enterprise `enterprise-vfp-lift-curve-agent`.
- Not yet synced: `neqsim-copilot-plugin` copies (generated by `devtools/build_agent_plugin.py` at release).

---

## 2026-10-20 — `k_top` is reservoir-only: fluid characterisation agents must never touch it

- The methane-heavy BIP ramp `k_top` (reservoir saturation-pressure lever of the universal P/A EOS) is for
  reservoir BIP modelling only and must in no way be used for topside/process simulations. Agents leave `k_top = 0`
  and the base-EOS BIPs unchanged: no `apply_k_top`, no `tune_k_top`/retune, no BIP regression against psat/PVT data.
- **Behaviour change** (enterprise-skills `enterprise-precharacterized-well-fluids`): `get_precharacterized_fluid`
  ignores `apply_k_top=True|"retune"` with a warning unless `reservoir_use_only=True` is also passed; such a fluid is
  flagged `RESERVOIR-ONLY` in `warnings`. New test `test_k_top_ignored_without_reservoir_flag`.
- Updated: agent `enterprise-fluid-generation-workflow-agent` (hard rule + limitation), skills
  `enterprise-precharacterized-well-fluids`, `enterprise-fluidmagic-characterization`, community
  `neqsim-pseudocomponent-split-characterization`, `enterprise-skills.yaml` description.
- Not yet synced: `neqsim-copilot-plugin` copies.

---

## 2026-10-07 — `SealGasSupplyConditioning` (seal gas supply side) and report generator fix

- New `neqsim.process.equipment.compressor.SealGasSupplyConditioning`: dew point margin (water and hydrocarbon) of seal gas after the isenthalpic reduction over the supply valve, optional heater, `calculateRequiredInletTemperatureC`, and static `impliedLiquidLoadKgPerMSm3` / `sumpFillTimeDays` for filter sump evidence. Complements `DryGasSealAnalyzer` (leakage side). 5 tests in `SealGasSupplyConditioningTest`.
- `devtools/task_template/step3_report/generate_report.py`: `results.json` `references` may now be plain strings (two `ref.get` calls crashed with `AttributeError`).
- Skills updated: `neqsim-rotating-equipment-design` (seal gas section), community `neqsim-dry-gas-seal-screening`, enterprise `enterprise-maintenance-api` (notification text as design evidence), agent `enterprise-pepr-solve-task-agent` workflow step 5d. Source task: PEPR 80302027 (Grane).

---

## 2026-10-05 — `WellUnloadingScreening` (drowned-well start-up)

- New `neqsim.process.equipment.reservoir.WellUnloadingScreening`: static wellhead pressure for a gas cap over a
  liquid column, choke-limited liquid rate when a separator is pulled down (reservoir inflow + choke quadratic),
  unloading time and liquid share from a downhole gauge. Pure hydrostatic/valve arithmetic, no friction.
- Basis: PEPR 80302989 (C-16 B, Oseberg C). Test: `WellUnloadingScreeningTest` (5 tests).

---

## 2026-10-04 — Agent coverage and workflow review (#4191)

- Agent discovery includes gas-processing/refining and network/energy specialists.
  External weather-skill ownership is declared for core-only installations.
- Upstream task hand-offs prefer the connected GitHub capability and reuse existing
  publication authorization. CLI `--dry-run --pr` does not execute subprocesses;
  failed commits and detached branches stop publication, and selected-file commits
  exclude unrelated staged content. Issue bodies no longer add private task paths.
- Hourly scheduling preserves the old positional Python API and accepts only whole
  hour intervals dividing 24, anchored at midnight. Invalid clock times and mixed
  timing options are rejected; Windows wrapper paths are quoted and length-checked.
- Thrust and valve screening reject non-finite inputs. Thrust relative margins
  require a positive finite zero-crossing, avoiding invalid-data classifications
  as safe margins or `NULL` thrust. See the rotating-equipment skill for limitations.

## 2026-09-30 — `Compressor.getValidatedDistanceToSurge()`

- New `neqsim.process.equipment.compressor.Compressor.getValidatedDistanceToSurge()`:
  returns `getDistanceToSurge()` as normal, but `Double.NaN` whenever
  `isChartExtrapolated()` is `true` (the current flow/speed is outside the
  compressor chart's validated range). Additive, non-breaking — the existing
  `getDistanceToSurge()` is unchanged.
- Motivation: an automated blackout/driver-trip anti-surge coastdown screening
  (representative NCS LP recompression compressor, chart auto-generated by
  `generateCompressorChart()` from a single design point) produced a finite but
  physically meaningless, non-monotonic surge-margin trend once speed/flow moved
  outside the chart's validated envelope (confirmed by cross-checking
  `isChartExtrapolated()`/`getChartFlowStatus()` at each point). Callers doing
  automated sweeps had no built-in way to detect this without re-implementing
  the flow-range check themselves.
- `CompressorTest.testGetValidatedDistanceToSurgeFlagsExtrapolation` (1 test,
  sweeps 9 speed fractions and asserts the NaN-vs-passthrough contract at every
  point that is/is not extrapolated) passes; `spotless:apply` clean.
- Gotcha discovered while writing the test: even the exact post-
  `generateCompressorChart()` design point can itself read back as
  `EXTRAPOLATED_LOW_FLOW` via `getChartFlowStatus()` — do not assume the design
  point is always "in range"; check `isChartExtrapolated()` directly instead of
  hardcoding which nominal point should pass.
- Included with the agent coverage work in PR #4191.



- New `neqsim.process.equipment.valve.ValveRangeabilityScreening` (+
  `ValveTrimCharacteristic` enum: LINEAR, EQUAL_PERCENTAGE, QUICK_OPENING).
  Pure ISA-75 trim-curve arithmetic (no thermodynamics): given a trim
  characteristic, two travel fractions and a rangeability, returns the
  normalized flow fraction at each travel point and their ratio. Screening
  aid for "a recycle/anti-surge valve is clamped above its desired minimum
  opening — how much extra flow/energy does that cost, and is a low-opening
  choppy/oscillating valve a control-authority artifact?" questions, before a
  full valve signature test or process simulation.
- `ValveRangeabilityScreeningTest` (6 tests) passes; `spotless:apply` clean.
- Synthetic low-travel comparison: a 2.9% opening versus 1.2% illustrates
  trim-dependent sensitivity at fixed pressure drop. Actual recycle flow,
  compressor power and dynamic stability require a coupled process model.


- `GibbsReactor` uses total component feed inventories across all inlet phases,
  then performs homogeneous reaction equilibrium followed by the outlet flash.
  This does not add simultaneous multiphase reactive equilibrium.
- Element diagnostics now include sodium: **O, N, C, H, S, Ar, Z, Na**. Existing
  indices 0–6 keep their names; read `getElementNames()` instead of assuming seven
  entries. Argon, sodium, and signed charge map to distinct CSV columns.
  Independent charge constraints remain active for neutral feeds.
- Corrected the fallback Gibbs integration constant to satisfy the
  Gibbs–Helmholtz identity. Direct polynomial data retain their existing route.
- Regularization is applied before the Newton solve. The objective reads
  fugacity from the state being evaluated. An exhausted unconverged solve now
  returns `false`; callers of `run()` must still inspect `hasConverged()`.
- The final outlet flash receives mole fractions consistent with the component
  inventory. This corrects stale `getz()` ppm reporting in the CO2 scenarios
  without changing their numerical baselines. The CO2 wrapper refreshes the
  working fugacity state after each iteration; the generic reactor retains its
  historical iteration behavior pending independent adiabatic qualification.
- `Recycle` uses `q*x + (1-q)*g(x)` with `q=s/(s-1)`, making `q=0` direct
  substitution and recovering affine fixed points. Legacy mixed-unit flow
  tolerances are unchanged and explicitly documented, including the OR semantics
  of the optional absolute tolerance.
- Updated reactor/recycle guides and Javadocs. Reaction-engineering and process
  examples should use the named balance columns and check convergence status.

---

## 2026-09-26 — Optional formation-referenced enthalpy (#3991)

`SystemInterface.setUseIdealGasEnthalpyOfFormation(true)` enables gas-phase
formation enthalpy at 298.15 K plus the existing Cp integral and EOS departure.
The default remains the legacy sensible reference at 273.15 K. `getHID(T, true)`
also provides explicit component evaluation; availability and source getters
distinguish reviewed zero values from placeholders. `COMP.csv` stores 13 sourced
entries, including the correction of helium's -242000 J/mol placeholder to zero;
the extended loader copies reviewed value/source pairs. Updated tabulated values
also affect existing direct consumers of formation data, including reactive
equilibrium calculations, even when the new stream option is not enabled.

Reactive PH avoids double-counting when formation heat is already in stream
enthalpy. Recompute numerical PH targets after switching reference, and select
the same convention for every connected stream. Native caloric models and
aqueous ionic/solid references are outside this option. See the
[reference guide](docs/thermo/reading_fluid_properties.md#formation-enthalpy-reference).
## 2026-09-26 — Living tasks use the general task root; user guide added

- Every `neqsim task-*` command accepts a `<task>` path **or** a folder name inside
  the task root (`neqsim --show-task-root`), from any directory. An existing path wins.
- `neqsim task-status` without a folder lists every living task in the task root;
  `neqsim task-reference-case` without a folder creates the reference case there.
- New user guide `docs/development/CONTINUOUS_TASK_SOLVING.md` (setup, goal and plan
  reference, stage scripts, backtest, scheduling, headless agents, ledger, promotion),
  introduced in `TASK_SOLVING_GUIDE.md` § "Keeping a Task Alive".
- Updated: skill `neqsim-continuous-task-improvement`, agent `continuous-improvement`,
  community `continuous-task-improvement-agent`, enterprise
  `enterprise-continuous-improvement-agent` workflow.

## 2026-09-25 — `RotorUnbalanceAssessment`: coupling/rotor unbalance and shaft-vibration criteria

New `neqsim.process.mechanicaldesign.compressor.RotorUnbalanceAssessment` (static helpers + `evaluate(...)`
returning a `Result` with `toJson()`), added while diagnosing a coupling drive-bolt fracture on an LP
recompression train:

* `unbalanceFromMass(massG, radiusMm)` — U = m r (g mm) for a lost/added mass (bolt fragment, balance weight, deposit).
* `apiAllowableUnbalance(planeMassKg, mcsRpm, applyApi671Floor)` — API 617/671 `6350 W/N` g mm, optional 7.2 g mm floor.
* `isoPermissibleUnbalance(G, rotorMassKg, rpm)` — ISO 21940-11.
* `centrifugalForce(unbalanceGmm, rpm)`, `apiShaftVibrationLimit(mcsRpm)` (API 617, capped 25.4 um),
  `isoZoneBoundaries(rpm)` (ISO 7919-3 shaft relative A/B, B/C, C/D), `significantChangeThreshold(rpm)`
  (ISO 20816-1, 25 % of B/C), `vectorChange(...)` (1X vector difference), `influenceCoefficient(...)`.
* Tests: `RotorUnbalanceAssessmentTest` (hand-calculated values). No existing API changed.

---

## 2026-09-18 — MCP tool contracts: schema coverage gate, `validateInput` for every tool, no more silent "success"

An end-to-end probe of the packaged MCP server (`tools/list` + four task chains) found that
breadth was fine (71 tools) but a model working blind lost turns to three contract defects.
All three are fixed and gated:

* **Every calculation tool has a real input schema.** `SchemaCatalog` now ships hand-written
  input schemas for `run_relief`, `run_flare_network`, `run_lopa`, `run_sil`, `run_risk_matrix`,
  `run_chemistry`, `design_utilities`, `run_hazop_scenario` and `run_parametric_study`; field
  names and units mirror the runners exactly (`massFlowRate_kg_s`, `setPressure_bara`, …).
  `SchemaCatalog.hasDetailedInputSchema(tool)` + `SchemaCatalogTest.testEveryCalculationToolHasDetailedInputSchema`
  fail CI when a new `run_*` / `size_equipment` / `design_utilities` / `calculate_standard`
  tool is added without one (orchestration tools are listed explicitly). `getSchema` accepts
  `runRelief`, `run-relief` and `run_relief` (`SchemaCatalog.normalizeToolName`).
* **`validateInput` validates any tool, not just flash/process.** Wrap the input as
  `{"tool": "runRelief", "input": {...}}`; `Validator` checks it against the catalog schema with
  the new dependency-free `neqsim.mcp.catalog.SchemaChecker` (type/required/enum/const/
  minItems/allOf/anyOf/oneOf/if-then) and returns `SCHEMA_VIOLATION` issues naming the missing
  or wrong field. Flash/process inputs nested under `input`/`processJson`/… are unwrapped.
* **Process JSON pre-flight is now strict about the two mistakes that used to run silently.**
  `UNRESOLVED_INLET` (error): an `inlet`/`inlets` entry that names no unit in the `process`
  array (forward refs and `unit.port` aliases resolve; `interAreaLinks` targets are exempt).
  `MISPLACED_UNIT_PARAMETERS` (error): keys beside `properties` on a unit (e.g.
  `outletPressure_bara`) that the builder ignores. `UNRECOGNIZED_INPUT_SHAPE` (error): an
  object with `fluid`/`units`/`equipment`/… but no `process`/`areas`/`components`, instead of
  the misleading `MISSING_COMPONENTS`. `ProcessRunner` additionally refuses to report
  `status: success` when the builder emitted an `Unresolved inlet` warning (`UNRESOLVED_INLET`).
* **Hydrate risk never reports NaN as LOW.** `HydrateRiskMapper.RiskLevel.UNKNOWN` replaces the
  old "assume safe if calculation failed"; the mapper now sets `setHydrateCheck(true)` on the
  clone (the reason every MCP call returned NaN), refuses fluids without `water`, and exposes
  `getFailureReasons()` / `getUnknownPointCount()` (also in `toJson()` as `failureReasons`,
  `unknownPointCount`; `minimumSubcooling_C` is NaN, not `Double.MAX_VALUE`, when nothing
  converged). `FlowAssuranceRunner` returns `status: error` / `RESULT_NOT_AVAILABLE` with the
  partial data when a hydrateRiskMap (or waxAppearance) headline result is unavailable.
  Callers that switched on `RiskLevel` must handle `UNKNOWN`.
* **CPA fluids built from JSON default to the CPA mixing rule.** Every MCP runner and
  `JsonProcessBuilder` fell back to `"classic"` (SRK kij database) for `model: "CPA"`, which gave a
  wet-gas hydrate temperature of −0.04 °C at 100 bara where the CPA rule gives 15.7 °C (SRK
  classic: 16.3 °C) — a silent wrong answer, not an error. New
  `EosMixingRuleType.defaultForModel(model)` (→ `CLASSIC_TX_CPA` for any model containing "CPA",
  `CLASSIC` otherwise) and `neqsim.mcp.runners.FluidDefaults.resolveMixingRule(input, model)` are
  used by `FlashRunner`, `PVTRunner`, `FlowAssuranceRunner`, `PhaseEnvelopeRunner`,
  `PropertyTableRunner`, `PipelineRunner`, `ReservoirRunner`, `StandardsRunner`,
  `EquipmentSizingRunner` and `JsonProcessBuilder`. An explicit `mixingRule` is always honoured;
  results of CPA inputs that omitted it will change (to the correct value).

Agents: prefer `getSchema(<tool>, input)` → `validateInput({tool, input})` → run. Skills
`neqsim-flow-assurance` and `neqsim-process-modeling` should mention the `{tool, input}`
validation form and that a `hydrateRiskMap` fluid must contain water.

---

Preparation for packaging NeqSim skills, agents and the MCP server as
[VS Code Agent Plugins 1.0](https://code.visualstudio.com/docs/agent-customization/agent-plugins).
Plugin loaders silently skip a skill whose folder name differs from its frontmatter `name`
or whose name is not kebab-case, and marketplaces reject dotted agent ids, so the source
repos were normalised:

* **Core skills renamed** to kebab-case (`analyze_convergence` → `analyze-convergence`,
  `paperlab_*` → `paperlab-*`, `neqsim_in_writing` → `neqsim-in-writing`, …), in both
  `.github/skills/` and `neqsim-paperlab/skills/` (`devtools/rename_underscore_skills.py`).
  `figure_discussion` (paperlab-internal duplicate of `figure-discussion`, and a results.json key)
  is left as-is.
* **Core agent files renamed** `<a.b>.agent.md` → `<a-b>.agent.md`; the `@handle` is now
  the kebab id (`@solve-task`, `@capability-scout`, `@process-model`, …). Display `name:`
  is unchanged (`devtools/rename_dotted_agents.py`).
* **`required_skills:` frontmatter** is now the canonical skill declaration on every core agent
  (the prose `Loaded skills:` line stays for run-time loading). `devtools/agent_frontmatter.py`
  is the single reader used by `install_agent.py`, `agent_search.py`, `paperlab_install.py`,
  `generate_agent_skill_map.py` and `verify_skills_agents.py`; keep the two in sync with
  `devtools/sync_agent_required_skills.py --apply` (CI runs `--check`).
* **Canonical MCP definition** at `.github/mcp/mcp.json` (Agent Plugins `mcpServers` format);
  `.vscode/mcp.json` must mirror it (linted: same `command`, canonical args as prefix with
  `${PLUGIN_ROOT}` → `${workspaceFolder}/.github/mcp`). **The server now starts from the release
  jar, not Docker:** `command: java`, `args: [${PLUGIN_ROOT}/servers/NeqsimMcpLauncher.java]`.
  The launcher (`.github/mcp/servers/NeqsimMcpLauncher.java`, Java source-launch, JDK 21+)
  downloads `neqsim-mcp-server-<version>-runner.jar` + `.sha256` from the GitHub release named
  in `servers/neqsim-mcp-server.properties` (written by the builder; default `version=latest`,
  pin with `--mcp-version X.Y.Z` or `pom`) into `${PLUGIN_DATA}`, verifies it, and runs it with
  stdio inherited, UTF-8 forced and HTTP transport disabled. **`latest` tracking:** the launcher
  resolves the tag behind `releases/latest` (HEAD, at most once per 24 h, stamp
  `latest-release.txt`), fetches a newer jar when needed, prunes superseded jars and falls back
  to the cached version offline. `--prefetch` resolves + downloads without starting the server;
  the plugin's `SessionStart` hook runs it detached so the first-session download overlaps the
  user's first prompt and new releases are fetched a day ahead (a `.lock` beside the jar makes a
  concurrent server start wait instead of downloading twice). Env: `NEQSIM_MCP_JAR`,
  `NEQSIM_MCP_VERSION` (`X.Y.Z` or `latest`), `NEQSIM_MCP_LATEST_TTL_HOURS`,
  `NEQSIM_MCP_JAVA_OPTS`. Verified: first start downloads ~89 MB, later starts `initialize` in
  ~2 s, 71 tools listed. The marketplace repo ships `scripts/install.{ps1,sh}` that registers the
  marketplace, enables `extensions.autoUpdate` and installs all three plugins via the bundled
  Copilot CLI in one step. **Offline bundle:** a `neqsim-mcp-server-*-runner.jar` (+ `.sha256`)
  placed beside the launcher in `servers/` seeds the jar cache (`seedFromBundle`), and the hook
  installs from `${PLUGIN_ROOT}/wheels` with `pip --no-index` before falling back to PyPI; the
  marketplace repo's `scripts/make_offline_bundle.py` + `offline_bundle.yml` build and publish
  such a zip on every plugin change / NeqSim release (`release_with_jars.yml` dispatches
  `neqsim-release` when `NEQSIM_PLUGIN_DISPATCH_TOKEN` is set).
* **`devtools/build_agent_plugin.py`** emits `neqsim`, `neqsim-community` and
  `neqsim-enterprise` plugins plus a `marketplace.json`, with a content-hash version gate
  (`--bump patch|minor|major`) and a `SessionStart` hook that `pip install -e` the skills repo.
  The hook is portable: `sh` / PowerShell launchers resolve the interpreter at run time
  (`NEQSIM_PYTHON`, then PATH); nothing from the build machine is baked in unless `--python`
  (or `NEQSIM_PLUGIN_PYTHON`) pins a site-specific fallback. `install_agent.render_vscode_agent()` is the shared renderer.
* Companion changes: community skill folders renamed to their `neqsim-*` manifest names,
  enterprise agents carry the `enterprise-` prefix in `agent.yaml`, and both skills repos
  install all skill packages with one `pip install -e .` (root `setup.py`, which discovers
  packages at both `skills/<category>/<skill>/src` and the plugin's flat `skills/<skill>/src`).
* **Published marketplace:** `equinor/neqsim-copilot-plugin` (internal) is the generated output —
  never hand-edit it. Release procedure from a checkout with the four sibling repos beside `neqsim`:
  `python devtools/build_agent_plugin.py --out ../neqsim-copilot-plugin --bump patch`, then
  `python devtools/validate_agent_plugin.py ../neqsim-copilot-plugin`, then commit + push there.
  Users add `"chat.plugins.marketplaces": ["equinor/neqsim-copilot-plugin"]`.

**Agents/skills to update:** anything that hard-codes an old `@a.b` handle, a
`skills/<category>/<unprefixed>` community path, or an underscore paperlab skill name.

### 2026-09-18 (later) — `@solve-task` split, agent eval harness, BM25 retrieval, description cap

* **`solve-task.agent.md` is now a 16 KB orchestrator** (was 141 KB). Sections 1–10 — the
  phase-by-phase workflow, quality gates, benchmark/uncertainty procedure, task-type guidance,
  NIPs, interpretation rules, critical rules, lessons learned — moved verbatim into the new core
  skill **`neqsim-task-workflow`** (section numbers preserved). The agent keeps the mandatory
  first action, purpose, and section 0 (proportionality, validation, assumption, intent,
  continuous-improvement rules, deliverable matrix, quick fast path). Quick tasks no longer pay
  the 141 KB context cost; Standard/Comprehensive load the skill.
* **`devtools/agent_eval.py` + `agent_eval_cases.json`** — deterministic agent evaluation:
  12 routing golden prompts (expected agent in top-3, skills in top-5) and 5 `results.json`
  contract cases; `test_agent_eval.py` runs in `skills_agents_lint.yml`. Sibling-repo
  expectations are enforced only when those repos are checked out.
* **`devtools/bm25.py`** replaces the optional scikit-learn / Jaccard scorers in `agent_search`
  and `skill_search` (dependency-free BM25 with stemming, stopwords, coverage term), so dev and
  CI rank identically. Agent haystacks include `required_skills`.
* **SKILL.md `description` capped at 1024 chars** (`maxLength` in the shared manifest schema +
  `verify_skills_agents.py`); 12 over-long descriptions rewritten. Clients truncate beyond
  1024 and silently drop the routing vocabulary.
* **Plugin-mode fitness for the task workflow.** `devtools/` is now a complete pip package
  (`neqsim-dev-setup` 0.2.0: `neqsim` CLI, `neqsim_dev_setup`, `new_task`, `neqsim_runner`,
  validators, consistency checker, work record, report generator + `task_template/`) that
  depends on the `neqsim` wheel. The core agent plugin's SessionStart hook installs it
  (`pip install "neqsim-dev-setup @ git+https://github.com/equinor/neqsim.git@<ref>#subdirectory=devtools"`,
  ref from `build_agent_plugin.py --toolkit-ref`). `neqsim_dev_setup.neqsim_init()` falls back
  to the packaged JAR when no `pom.xml` is reachable (`ns.JAR_MODE`, `ns.MISSING_CLASSES` for
  classes newer than the JAR; `NEQSIM_JAR` overrides). `@solve-task` Step 0 now detects
  Workspace / Toolkit / Chat-only and the `neqsim-task-workflow` skill §0.6 says what each step
  needs. Verified end to end in a fresh venv with no checkout: `neqsim new-task`, JAR-mode
  flash, validator, `neqsim report` (docx + html + work record).

---

## 2026-09-14 — `ChemicalInteractionRule` environment rules no longer fire on unsupplied conditions

**Bug fix** in `neqsim.process.chemistry.ChemicalInteractionRule.environmentMatches`.

A `material_carbon_steel` environment rule whose threshold was not temperature-keyed fell through
to an unconditional `return true`. The bundled rule set carries such rules keyed on variables the
assessor does not carry — `velocity>5mps` (NORSOK M-506), `O2<10ppb` (NACE TM0169) — so they fired
for **every** carbon-steel study regardless of the actual operating condition. The effect was not
cosmetic: the spurious `velocity>5mps` rule is `HIGH`, so it set `getHighestSeverity()`, pushed
`getVerdict()` to `INCOMPATIBLE`, and through that raised a `CHEMICAL_INCOMPATIBILITY` candidate in
`RootCauseAnalyser`. A static chemical-injection cabinet at 25 °C was reported as having a
flow-induced corrosion problem.

Material rules now route through a private `materialThresholdMatches(String, double)` helper:

* `*` or an empty spec still matches (e.g. `ACID,HF,material_carbon_steel,*`);
* a `T…` spec still gates on temperature (`T>60C`, `T>120C`, `T>150C`, `T>175C`);
* any other keyed spec no longer matches, because the condition variable was never supplied.

**Migration:** none for callers. A study that previously reported a velocity- or oxygen-keyed
carbon-steel issue without supplying a velocity or an oxygen level will now report one fewer issue
and may drop from `INCOMPATIBLE` to `CAUTION`. That is the corrected result.

**Known remaining gap:** `environmentMatches` still has no branch for `material_316L`,
`water_high_h2s`, `water_high_chloride`, `water_high_barium`, `water_low_water_cut`,
`gas_lift_high_co2` or `operating_low_temperature`, so those bundled rules are unreachable.
Closing that needs a conditions-object signature change and its own regression baseline.

**Tests:** `src/test/java/neqsim/process/chemistry/ChemicalInteractionRuleEnvironmentTest.java`
(5 cases). Full chemistry suite re-run: 44 tests, 0 failures.

**Agents/skills affected:** `neqsim-production-chemistry` (compatibility screening verdicts),
`production.chemistry` agent, and any study that calls `ChemicalCompatibilityAssessor` with a
carbon-steel material.

---

## 2026-09-12 — New `ChemicalInjectionNozzlePerformance` for gas-phase chemical injection

**New class** `neqsim.process.chemistry.injection.ChemicalInjectionNozzlePerformance`.

NeqSim could size the chemistry of a gas-phase chemical treatment
(`chemistry.scavenger.H2SScavengerPerformance`) and could apply a mixing efficiency to a scavenger
unit (`equipment.absorber.H2SScavenger.setMixingEfficiency`), but nothing computed what mixing
efficiency an injection arrangement actually delivers. `setMixingEfficiency` was a free input. That
made the common field question — a bare injection quill was replaced by an atomizing nozzle, is the
chemical now in contact with the gas? — unanswerable with the library.

The new class closes that gap for any liquid chemical injected into a flowing gas line: H2S
scavenger, corrosion inhibitor, methanol or MEG sprayed into a gas stream.

```java
ChemicalInjectionNozzlePerformance nozzle = new ChemicalInjectionNozzlePerformance();
nozzle.setInjectionDevice(ChemicalInjectionNozzlePerformance.InjectionDevice.FULL_CONE_NOZZLE);
nozzle.setPipeInnerDiameter(0.4889);
nozzle.setGasVolumeFlow(2.21);          // m3/s at line conditions
nozzle.setGasDensity(11.76);            // from a flashed NeqSim stream
nozzle.setGasViscosity(1.229e-5);
nozzle.setChemicalVolumeFlow(235.0);    // l/h
nozzle.setChemicalDensity(1080.0);
nozzle.setChemicalViscosity(8.0e-3);
nozzle.setSurfaceTension(0.040);
nozzle.setNozzleDifferentialPressure(6.5);   // bar
nozzle.setSprayConeAngle(90.0);
nozzle.setInsertionDepth(0.135);        // from the pipe wall; piping specs limit this
nozzle.evaluate();

double smd = nozzle.getSauterMeanDiameterMicron();
double area = nozzle.getInterfacialArea();            // m2/m3
double reach = nozzle.getWallImpingementLength();     // m before the drops deposit
double index = nozzle.getDispersionIndex();           // 0-1, feeds setMixingEfficiency
```

Correlations are all from the open literature: Lefebvre's pressure-swirl Sauter mean diameter for a
nozzle, the critical-Weber aerodynamic breakup limit (We = 12) for a bare quill, Stokes settling
with a Schiller-Naumann drag correction, and `a = 6 Q_L / (SMD Q_G)` for the interfacial area. The
off-centre term is the part specific to real installations: chemical-injection piping
specifications limit insertion length, so on a large line the nozzle cannot reach the centreline and
the drop flight path before wall contact is correspondingly shorter.

Named warnings (`gas_velocity_below_quill_limit`, `droplets_too_coarse`, `mist_carryover_risk`,
`early_wall_impingement`, `partial_cross_section_coverage`, `off_centre_injection`) make a screening
verdict explainable instead of a single number.

**Agents and skills to update:** `production.chemistry` agent and the
`neqsim-production-chemistry` skill should reference the class for injection-point placement and
nozzle-versus-quill questions.

**Tests:** `ChemicalInjectionNozzlePerformanceTest`, 6 tests.
## 2026-09-12 — Automatic recycle insertion: `makeRecycles()` and `setAutoRecycles(...)`

A feedback loop wired straight back into an upstream mixer, with no `Recycle` unit in it, is an
implicit tear: it converges only because the surrounding sweep re-evaluates it, so it has no
tolerance, no acceleration and no convergence report of its own. Across `ProcessModel` areas it is
worse - a stream produced by an area that runs after its consumer can only be closed by the outer
Gauss-Seidel pass, which has no relaxation setting, so the plant residual sits on a floor that no
tolerance setting reaches.

**New `neqsim.process.processmodel.AutoRecycleBuilder`** finds those loops and closes them, exposed as:

- `ProcessSystem.makeRecycles()` / `makeRecycles(double tolerance)` — strongly connected components
  of one flowsheet.
- `ProcessModel.makeRecycles()` / `makeRecycles(double tolerance)` — cross-area feedback streams
  first, then every area.
- `setAutoRecycles(boolean)` on both — do it automatically inside `run()` and therefore inside every
  `runUntilConverged(...)` overload.

The tear point is the inlet with the smallest recycle ratio (tear flow / total inlet flow of the
consuming unit), because that ratio sets the contraction rate of a direct-substitution tear. One
edge is torn per round and the loop structure is recomputed afterwards, so nested cycles get the
minimum number of tears. Each generated recycle starts on direct substitution with
`setAdaptiveAcceleration(true)` and gets an absolute flow tolerance at 1e-6 of the largest flow in
its area.

- **No migration needed.** `setAutoRecycles` defaults to **false**, so nothing changes for an
  existing model; inserting a tear changes how a flowsheet iterates and must be opted into.
- `makeRecycles()` seeds itself - it runs the flowsheet once when streams have no fluid yet - and is
  idempotent, so the old "run, then insert, then run again" ordering is no longer the caller's
  problem.
- Only `Mixer` and `Manifold` inlets can be torn (they expose `replaceStream(int, StreamInterface)`).
  A loop closing on any other equipment is logged and left untouched rather than mis-wired.
- Replaces hand-written tear helpers in notebooks (a `Stream` clone + `Recycle` per cross-area
  stream). Documented in
  [docs/process/controllers.md#automatic-recycle-insertion](docs/process/controllers.md#automatic-recycle-insertion).

---

## 2026-08-24 — Component name resolution, parameterised component lookup, COMP_EXT superset fix

Three related changes around how a component name reaches the component database.

**1. New `neqsim.thermo.component.ComponentNameResolver`.**
`addComponent(...)` previously mapped names through an 18-entry hardcoded map of reservoir
shorthand (`C1`, `nC4`, `H2O`), matched case-sensitively. Nothing else was recognised, so a
systematic or trivial name from a laboratory report failed with "not found in database" even when
the component was present under the in-house shorthand.

The resolver now recognises, in this order: any database name regardless of letter case; the
reservoir shorthand as before; a 148-entry synonym table covering systematic names
(`2,2,4-trimethylpentane` → `224-TM-C5`) and trivial names (`isopentane` → `i-pentane`,
`cyclohexane` → `c-hexane`); and inverted CAS index names as printed by chromatography software
(`Cyclohexane, 1,2,4-trimethyl-` → `1.2.4-TMcyC6`). A `.` between two digits is accepted wherever
the database writes a locant separator, so `1,2,3-TM-Benzene` and `1.2.3-TM-Benzene` both work.

Stereochemistry is never guessed. Where the database holds both partners of a cis/trans pair
(the 1,2- and 1,3-dimethylcyclopentanes, the 1,2- and 1,4-dimethylcyclohexanes, 2-butene), the
unqualified parent name is deliberately absent from the synonym table and is passed through
unchanged rather than silently resolved to one of the two.

- **No migration needed.** `ComponentInterface.getComponentNameFromAlias(String)` keeps its
  signature and delegates to the resolver, so all ~28 existing call sites gain the new behaviour.
  Every previously accepted name still resolves to the same component.
- `ComponentInterface.getComponentNameMap()` is **deprecated**; use
  `ComponentNameResolver.getSynonyms()`.
- Unknown names are still returned unchanged, so components that exist only in the extended
  database are unaffected.

**2. `NeqSimDataBase.hasComponent` / `hasTempComponent` now use a prepared statement.**
The query was assembled as `"... WHERE NAME='" + name + "'"`. A name containing an apostrophe
produced invalid SQL — 2,061 names in the extended database contain one, for example
`4'-hydroxyacetophenone`, so those components could never be looked up. Both methods now bind the
name as a parameter and return `false` for `null` instead of reaching the database.

**3. `COMP_EXT.csv` is now genuinely a superset of `COMP.csv`.**
The documentation stated that the extended database contains every standard component; eleven were
missing (`NO2-`, `H2O2`, `NaNO2`, `NH2SO3H`, `NH2SO3-`, `NaHSO4`, `N2O4`, `N2O4-`, `asphaltene`,
`MEA+`, `MEACOO-`). Because `useExtendedComponentDatabase(true)` replaces the `COMP` table, calling
it silently removed those components from any fluid built afterwards. The rows are copied verbatim
from `COMP.csv`, and the relationship is now enforced by a test.

**Documentation:** `docs/thermo/component_list.md` gains a "Component Name Resolution" section,
corrects the component counts (257 standard, 76,704 extended — it claimed "~250" and "50,000+"),
and records that the provenance of the extended database is undocumented and its parameters are
unvalidated against a primary source.

**Tests:** `ComponentNameResolverTest` (14 tests, including a check that every database name
resolves to itself and that every synonym target exists) and
`NeqSimDataBaseComponentLookupTest` (4 tests).

---

## 2026-08-15 — Beggs and Brill correlation corrected in `PipeBeggsAndBrills`
## 2026-08-20 — Gas-turbine water-wash planning: `GasTurbineWashPlanner` + partial on-line wash

`GasTurbineDegradation` could only model a wash as `offlineWash()`, a full reset of the recoverable
penalty, and nothing turned a *measured* corrected-efficiency trend into a wash decision. Both gaps
are now closed in `neqsim.process.equipment.powergeneration.gasturbine`.

**New: `GasTurbineWashPlanner`.** Screening-level planner for compressor water-wash programmes.

- Steady-state sawtooth with **partial** recovery: a wash removing a fraction `e` of the accumulated
  loss leaves a residual `L0 = (1-e)*r*T/e` at the start of every cycle, so an imperfect on-line wash
  never returns the machine to clean.
- The extra-fuel fraction `1/(1-L) - 1` is **integrated over the cycle**, not evaluated at the mean
  loss.
- `evaluate(intervalHours)` returns a `WashPlan` with washes/year, mean efficiency loss, extra fuel
  (Sm3/yr), extra CO2 (t/yr), and the fuel / CO2 / wash / outage cost split.
- `optimize(min, max, step)` scans for the lowest total annual cost;
  `paybackYears(capex, reference, withPermanentSystem)` gives the payback of a permanent
  installation (`POSITIVE_INFINITY` when it does not save money).
- `lossRateFromCorrectedEfficiencyTrend(ppPer1000FiredHours, cleanEfficiencyPercent)` converts the
  "corrected turbine efficiency" KPI that energy-management systems trend into the fractional loss
  rate the planner needs — the bridge from plant data to the model.

**Extended: `GasTurbineDegradation.onlineWash(double effectiveness)`** — partial recovery of the
recoverable penalty (clamped to 0–1; 1.0 is equivalent to `offlineWash()`).

Tests: `GasTurbineWashPlannerTest` (9 tests). Skill updated: `neqsim-power-generation` gained a
"Water-wash interval and permanent-wash business case" section with the usage pattern and the
gotchas (deferment cost dominates an off-line case; an off-line optimum at the scan bound means
annual crank washing is already right and on-line washing is the lever).



Four defects in the Beggs and Brill (1973) implementation, found by auditing the correlation term
by term against a clean-room reimplementation of the published equations and cross-checking a 74 km
gas-export line against a single-phase Darcy-Weisbach integration. All four now have regression
tests in `PipeBeggsAndBrillsCorrelationTest`.

**1. The pipe angle was converted from degrees to radians twice.**
`convertSystemUnitToImperial()` already converts `angle` to radians, but the inclination
correction then evaluated `Math.sin(1.8 * angle * 0.01745329)`. The correction was suppressed by
roughly a factor of 57, so liquid holdup barely responded to inclination — the correction factor
came out as 1.011 where the published correlation gives 2.00 at +4 degrees.

- **Impact:** every inclined two-phase result. Holdup, mixture density and the hydrostatic term
  were all wrong on any non-horizontal segment, and elevation profiles were effectively ignored.
  Horizontal lines were unaffected. Re-run any inclined or undulating pipeline case.

**2. The distributed-regime boundary tested `L4` instead of `L1`.**
For a no-slip liquid fraction below 0.4 the published map gives distributed flow when
`Fr >= L1`. Testing `L4` — which is astronomically large at low liquid fraction — made the branch
unreachable, and such points fell through to a `Fr > 110` catch-all and were reported as
intermittent. The branch order is now segregated, transition, intermittent, distributed, because
`L1` and `L3` cross near a liquid fraction of 0.01; the two ad-hoc catch-alls that were masking the
gap have been removed.

- **Impact:** flow regime, holdup and the two-phase friction multiplier for high-Froude flow at
  liquid fractions below 0.4. `BeggsAndBrillsPipeTest.testPipeLineBeggsAndBrills2` now reports
  `DISTRIBUTED` at the outlet instead of `INTERMITTENT`.

**3. The liquid velocity number counted gravity twice.**
`N_LV = 1.938 * vsl * (rho_L / sigma)^0.25`; the 1.938 prefactor already absorbs the gravitational
acceleration and the field-unit conversion. The code divided by a further 32.2.

**4. A volume-corrected density was mixed with an uncorrected one in the same formula.**
The specific gravity feeding the surface-tension correlation used
`getPhase(1).getDensity("lb/ft3")`, but the liquid velocity number used the no-argument
`getPhase(1).getDensity()`. **These are not the same number when volume correction is on** — 558.0
against 675.4 kg/m3 for a lean methane/n-decane liquid, a 21 % difference. Both now use the
explicit-unit accessor.

- **General rule for agents:** treat `phase.getDensity()` and `phase.getDensity("kg/m3")` as
  different quantities and never mix them inside one calculation.

Minor: the API gravity constant was `141.5/SG - 131.0`, corrected to the standard `- 131.5`, and
the two-phase friction `S` coefficients `3.18` / `0.872` are now the published `3.182` / `0.8725`.

**Still not modelled:** the Payne et al. (1979) holdup correction is not applied. Note, however, that
it would *not* have explained the residual difference against a transient two-fluid code on a large-bore
gas line: the Beggs and Brill `S` factor is monotonically increasing in `y = lambda_L / H_L^2` over that
range, so lowering the holdup *raises* the friction multiplier. On a 74 km 14-inch line at a mean
no-slip liquid fraction of 0.009 the multiplier was 1.42 and accounted for the entire gap; removing it
brought the correlation from 124 bar to 87 bar against 78 bar from OLGA and 84 bar from a single-phase
Darcy check. Beggs and Brill is calibrated for no-slip liquid fractions down to roughly 0.01-0.02, so
below that the two-phase friction multiplier is an extrapolation. State this as a correlation
applicability limit when reporting.

---

## 2026-08-14 — Component clone keeps its attractive term, and E300 export keeps the volume shift

Two defects that together made EOS regression results disagree with the Eclipse file they were
exported to. Both surfaced while characterising a gas condensate and both now have regression tests.

**1. `ComponentEos.clone()` left the cloned attractive term bound to the original component.**
The alpha function reads `Tc`, `Pc` and the acentric factor live from `getComponent()`, so the
usual regression pattern — clone a base fluid, then adjust critical properties on the clone — only
changed the `a` parameter while `alpha(T)` kept evaluating against the untuned critical temperature.
The tuning was silently half-applied.

- `AttractiveTermInterface.setComponent(ComponentEosInterface)` is now public, and
  `ComponentEos.clone()` re-points the cloned term at the cloned component.
- **Impact:** any tuning applied to a *clone* via `setTC` / `setPC` / `setAcentricFactor` was
  partly ignored. Tuning applied to a freshly built fluid was always correct. Re-run regressions
  that used the clone-then-tune pattern.
- Regression test: `ComponentEosCloneAttractiveTermTest`.

**2. `EclipseFluidReadWrite.write` wrote `SSHIFT`/`SSHIFTS` as zero for characterised fractions.**
It emitted `getVolumeCorrectionConst()`, which is only populated when a shift is set explicitly.
TBP and plus fractions derive their Péneloux translation from the Rackett compressibility instead,
so the translation was dropped on export — condensate liquid density came back ~14 % too high, both
on NeqSim read-back and in Eclipse.

- Now writes the effective dimensionless shift `getVolumeCorrection() / getb()`, matching the
  Eclipse convention `v = v_EOS − s·b`. The reader reproduces the original molar volume exactly.
- **Impact:** E300 files written before this change carry `SSHIFT = 0` for pseudo-components and
  will give untranslated PR liquid densities. Re-export them.
- Regression test: `EclipseFluidReadWriteVolumeShiftTest`.

**Agent-facing notes** (also added to the `neqsim-eos-regression` skill):

- Apply per-component tuning by iterating `fluid.getPhases()` (skipping nulls), **not**
  `getPhase(i)` — the latter resolves through the phase-index map and can return the same phase
  object several times while leaving other phase objects untuned.
- `dewPointPressureFlash()` / `dewPointPressureFlashHC()` can return the initial guess on
  near-critical fluids. Verify against a pressure scan or `calcPTphaseEnvelope` before using them
  as a regression target.

---

## 2026-08-13 — FIV fluid-viscosity factor corrected, and dead-leg pulsation screening added

**Breaking behaviour change.** `FlowInducedVibrationAnalyser` evaluated the Energy Institute
fluid-viscosity factor for void fraction above 0.99 as `sqrt(mu / sqrt(0.001))` while
`PipeBeggsAndBrills.getSegmentMixtureViscosity(int)` already returns **centipoise**. The extra
square root combined with a Pa·s / cP unit mismatch inflated the dry-gas `F_VF` by a factor 5.6
and produced an *upward* jump across the GVF = 0.99 boundary, so removing liquid from a wet-gas
line appeared to *raise* the vibration driver. The governing form is `FVF = sqrt(mu_gas / 1e-3)`
with the viscosity in Pa·s, i.e. `sqrt(mu_cP / 1 cP)`.

- Corrected to `Math.sqrt(viscosity_cP / REFERENCE_VISCOSITY_CP)`, with
  `FlowInducedVibrationAnalyser.REFERENCE_VISCOSITY_CP = 1.0` exposed as a public constant.
- **Impact:** any single-phase-gas LOF computed before this change is a factor 5.6 too high.
  Two-phase results (GVF ≤ 0.99) and liquid results are unaffected. Re-run gas-dominated cases.
- Sanity rule for agents: `F_VF` must *fall* as the void fraction goes to 1. The wet-gas branch
  reaches 0.268 at GVF = 0.99, so a single-phase gas must come out below that (~0.11 for a
  hydrocarbon gas). At equal standard rate and pressure the wet-over-dry driver ratio is ~3–4.
- Regression tests: `FlowInducedVibrationAnalyserTest#testDryGasLofBelowWetGasLof` and
  `#testDryGasFluidViscosityFactorReferencedToOneCentipoise`.

**New capability.** `neqsim.process.safety.vibration.FlowInducedPulsationScreening` and
`FlowInducedPulsationResult` screen closed side branches (dead legs) for acoustic lock-in — the
tonal mechanism that governs when a wet-gas line is converted to dry-gas service and that
main-line FIV screening does not cover.

- Acoustic length runs to the *first acoustic boundary*; **no end correction** is applied.
- Modes: `f_n = (2n+1)c/(4L)` for `AcousticTermination.CLOSED`, `(n+1)c/(2L)` for `OPEN`, n from 0.
- Shedding: `f_s = Sr·U0/W_eff` with `W_eff = pi·d_s/4 + r_eff` (the effective mouth width, **not**
  the branch diameter); `DEFAULT_STROUHAL_MODE_A = 0.37`, `DEFAULT_STROUHAL_MODE_C = 0.20`.
- Resonance when `0.8 f_n <= f_s <= 1.2 f_n` (`LOCK_IN_ENVELOPE_FRACTION = 0.20`).
- Helpers `effectiveWidth(...)` and `eigenFrequency(...)` allow length or velocity windows to be
  built without a full screening.
- Verified against a published worked example: 3 m closed branch at c = 400 m/s gives
  33.3 / 100 / 166.7 Hz.
- Docs: `docs/safety/mah_bowtie_fiv_screening.md`; examples executed by
  `MahBowTieFivScreeningDocExamplesTest`.

## 2026-08-13 — Independent stagnant inner HTC for TwoFluidPipe cooldown

- `TwoFluidPipe.setStagnantInnerHeatTransferCoefficient(...)` and its getter now own the zero-local-throughput
  fluid-to-wall coefficient used by the multi-layer transient model. The documented default is 50 W/(m²·K).
- `setHeatTransferCoefficient(...)` remains the simple-model or configuration-level overall U-value and no longer
  becomes the closed-flow inner film coefficient. Multi-layer shutdown results are therefore independent of whether
  that overall coefficient is set before or after radial-layer configuration.
- Migration: replace post-configuration `setHeatTransferCoefficient(value)` workarounds that intended to set the
  stagnant fluid film with `setStagnantInnerHeatTransferCoefficient(value)`.

## 2026-08-12 — Reversible ProcessModel operating actions

- `ProcessModelOperatingAction` adds immutable, serializable action identity, area-qualified
  automation address, unit, provenance, and strict continuous or enumerated-discrete semantics.
- `inspectCapability`, `capture`, `apply`, and `restore` provide explicit diagnostics,
  write/read-back verification and identity-bound restoration without running the process model.
- `registerWith(ProcessModelSimulationEvaluator)` exposes bounded continuous actions and exact
  discrete-value discovery to Java and JPype/Python optimizers. Intermediate discrete values fail
  closed and evaluator callbacks must be re-registered after deserialization.
- The API does not mutate topology, solve mixed-integer decisions, infer feasibility, rank actions,
  or approve an operating change. Candidate process runs and all engineering constraints remain
  explicit.

## 2026-08-12 — Explicit constraint scaling and candidate-active diagnostics

- `ConstraintActivityAnalyzer` consumes an immutable `SensitivityQualityResult` and performs no
  process evaluations. It reports dimensionless base margins, normalized constraint-margin
  derivatives, violations and conservative candidate-active/inactive classifications.
- Every `ConstraintScale` is positive, uses the constraint's declared unit, records provenance and
  is bound to exact immutable identity: index, name, type, bounds/tolerance, hard/soft semantics,
  penalty and capacity origin. Missing, duplicate, unitless or stale scales fail closed.
- `ActivityPolicy` retains the dimensionless activity tolerance, local sensitivity qualification
  policy and soft-constraint choice. Assessments and policies are immutable and serializable for
  Java and JPype/Python workflows.
- A scaled derivative remains linked to its complete raw evidence and must pass `isUsable()` before
  consumption. `CANDIDATE_ACTIVE` is only a feasible near-boundary diagnostic—not cross-unit
  ranking, optimizer active-set proof, a KKT multiplier, economic shadow price or engineering
  approval.

## 2026-08-11 — Evidence-qualified local ProcessModel constraint sensitivities

- `SensitivityQualityResult.assessConstraintSensitivities(policy)` now binds each constraint row
  and parameter column to pair-specific numerical evidence, immutable engineering identity, raw
  and minimizer objective derivatives, the constraint-margin derivative, declared derivative
  units, and actionable diagnostics without rerunning the process model.
- `SensitivityQualificationPolicy` explicitly controls relative-disagreement tolerance, base and
  perturbation feasibility requirements, and acceptance of one-sided stencils. Convergence
  failures, evaluation errors, non-finite derivatives, unstable refinement, and fixed parameters
  always reject a pair.
- `getEvidenceFlags()` retains cautions even when a policy permits them;
  `getRejectionReasons()` explains why a pair is refused. The accepted-only convenience getter
  must not replace archiving the complete assessment when auditability matters.
- The API deliberately performs no cross-unit ranking, scaling, active-set inference, KKT
  multiplier calculation, shadow-price claim, or engineering approval.

## 2026-08-11 — Self-describing ProcessModel sensitivity snapshots

- `SensitivityQualityResult` now includes immutable parameter, selected-objective, and constraint
  snapshots that bind derivative columns and rows to their engineering identity.
- Parameter snapshots retain index, name, automation address, unit, bounds, and bounded base
  value. The objective snapshot retains direction, unit, weight, raw and minimizer base values,
  and its gradient. Constraint snapshots retain type, unit, hard/soft semantics, penalty, bounds,
  equality tolerance, capacity area/equipment origin, base value, base margin, and the matching
  Jacobian row.
- The snapshots are serializable, defensively copy derivative arrays, and remain unchanged after
  evaluator definitions mutate or another process point runs. Existing matrix getters and
  sensitivity evaluation cost are unchanged.
- Agents must preserve these records when archiving or explaining sensitivities and must not rank
  unlike raw margins or derivatives without explicit engineering scaling.

---

## 2026-08-10 — ProcessModel sensitivity-quality evidence

- `ProcessModelSimulationEvaluator.estimateSensitivitiesWithQuality(...)` now evaluates one coarse
  and one halved finite-difference step, reusing each process run for the selected objective and
  every constraint-margin derivative.
- The immutable result returns the fine-step gradient/Jacobian and records each parameter's actual
  bounded stencil, requested/coarse/fine steps, scale-independent coarse/fine disagreement, and
  every perturbation's parameter value, convergence, hard-constraint feasibility, and error.
- `isNumericallyStable(tolerance)` is deliberately a numerical consistency check. Agents must
  inspect feasibility and active equipment/control regimes separately and must not label the
  derivative a shadow price without optimizer-specific KKT evidence.
- Existing gradient/Jacobian methods and their lower evaluation cost remain unchanged.

---

## 2026-08-09 — Bound-aware ProcessModel finite-difference sensitivities

- `ProcessModelSimulationEvaluator` objective gradients and constraint Jacobians now divide by the
  actual perturbation available inside each parameter's bounds. Narrow ranges and active bounds no
  longer silently understate derivatives after parameter clamping.
- `FiniteDifferenceMethod.CENTRAL` adds a symmetric second-order interior stencil with a one-sided
  boundary fallback. `FORWARD` remains the default and retains one perturbed simulation per
  non-fixed parameter.
- Non-positive or non-finite finite-difference steps and a null method now fail before simulation.
  Fixed parameters report zero derivative because no feasible perturbation direction exists.
- Treat the outputs as local sensitivities. Check step-size stability and active-set consistency
  before using them as debottlenecking or shadow-value evidence.

---

## 2026-08-09 — ProcessModel bottleneck snapshots retain area identity

`ProcessModel.getUtilizationSnapshotJson()` now adds `area` and `qualifiedName` to a
non-null plant-wide `bottleneck`. The existing `name`, utilization, constraint, ranking,
tie behavior, and schema version remain unchanged. Python and AI consumers should use
`qualifiedName` (`area::unit`) when joining or archiving bottlenecks because different
process areas may legitimately reuse the same unit name.

---
## 2026-08-09 — Expose Naphtali-Sandholm tray K-value convergence work

- Naphtali-Sandholm diagnostics now report forced-root K-value sweep count, the number of tray
  evaluations still above the `1e-8` log-K update criterion after two sweeps, and the maximum final
  update.
- The two-sweep numerical path, MESH equations, acceptance gates, and fallback behavior are
  unchanged. The telemetry establishes the baseline needed for a later bounded convergence method.

## 2026-08-09 — Correct Naphtali-Sandholm Jacobian work classification

- Naphtali-Sandholm diagnostics now count each numerically perturbed Jacobian column only as
  finite-difference work; the analytic-column counter reports zero for the current implementation.
- Both public telemetry getters remain available. Solver equations, Jacobian values, convergence,
  and fallback behavior are unchanged.

## 2026-08-07 — Flow-accelerated corrosion + in-situ pH at temperature (new classes) + DEA protonation enabled

**New in `neqsim.process.corrosion`** — for closed heating- and cooling-medium loops,
boiler feedwater and WHRU / economiser tubes, where the damage mechanism is magnetite
dissolution rather than acid-gas corrosion.

- `AmineBufferedPH` — converts a laboratory pH measured on a **cooled sample** into the
  **in-situ pH at operating temperature**, and reports the **alkaline margin** above
  neutrality. Critical point for agents: neutral water is pH 7.00 at 25 °C but about
  **pH 5.85 at 150 °C**, so a hot-system pH cannot be judged against pH 7. For a buffered
  fluid the pH shift equals the pKa shift exactly. Supports `BufferAmine.DEA` and
  `BufferAmine.MDEA`; verdicts `ROBUST` / `ADEQUATE` / `MARGINAL` / `INSUFFICIENT`.
- `FlowAcceleratedCorrosion` + `FacGeometry` — FAC screening index built from a
  Berger-Hau mass-transfer coefficient and factors for temperature (bell peaking at
  150 °C), in-situ pH, local geometry (bend / weld / weld-at-bend / orifice) and chromium
  content. `getDominantFactor()` names the controlling lever; `ratioTo(other)` quantifies
  a proposed change.

**Agent guidance — pick the right corrosion model:**

- `NorsokM506CorrosionRate` is **CO2 corrosion**. It does not apply to a CO2-free closed
  loop; using it there is a misapplication.
- FAC is **not** erosion-corrosion. FAC is electrochemical dissolution under mass-transfer
  control; erosion-corrosion needs mechanical particle impingement or cavitation. They
  occur at the same locations but need different mitigation. `RootCauseAnalyser` now
  raises `FLOW_ACCELERATED_CORROSION` separately from `EROSION_CORROSION`.
- Always feed `FlowAcceleratedCorrosion.setInSituPH(...)` a value from `AmineBufferedPH`,
  never a raw laboratory pH.
- The FAC index is **comparison-only**. Ratios between cases are meaningful; the absolute
  value is not a wall-loss rate.
- Wall shear scales as roughly `v^1.75`, so a 3 % velocity exceedance is a ~13 % shear
  exceedance. Report shear, not just velocity.

**Data fix — DEA protonation was disabled.** The `DEAprot` reaction
(`DEA+ + H2O <-> DEA + H3O+`, index 52, Austgen 1989) had complete stoichiometry in
`STOCCOEFDATA.csv` and complete constants in `REACTIONDATA.csv`, but `USEREACTION = 0`,
so any DEA-buffered electrolyte system silently returned no acid-base equilibrium. Now
enabled; a half-neutralised DEA buffer in `SystemFurstElectrolyteEos` returns pH 8.98 at
25 °C against a literature pKa of 8.88-8.92. The CPA electrolyte path
(`SystemElectrolyteCPAstatoil`) remains unreliable for amine buffers — pre-existing, and
affects MDEA equally.

**New skill:** `neqsim-flow-accelerated-corrosion`. Loaded by `@flow-assurance` and
`@root-cause`.

---

## 2026-08-07 — Self-heating / spontaneous-ignition criticality (new package) + glycol formation-property fix

**New package `neqsim.process.safety.selfheating`** — screening for low-temperature
self-heating leading to spontaneous ignition ("lagging fires", where a combustible
liquid soaks into porous thermal insulation and ignites with no external ignition
source).

- `PorousMediaSelfHeatingAnalyzer` — Frank-Kamenetskii steady-state criticality.
  Returns the dimensionless parameter `delta` against its shape-dependent critical
  value, plus the two engineering answers: **critical surface temperature** for a
  given layer thickness and **critical thickness** at a given temperature.
  `forPipeInsulation(...)` configures the conservative bounding case for lagging on
  a hot line and records the assumption in `getWarnings()`.
- `SemenovSelfHeatingAnalyzer` — Semenov `1/e` criterion for the surface-cooling
  limit (drained pools, thin films, small samples).
- `SelfHeatingInductionSolver` — transient 1-D conduction with an Arrhenius source,
  giving the **induction time** to ignition (hours to days, not seconds).
- `BasketTestRegression` — fits activation energy and volumetric heat-release
  pre-factor from hot-storage (basket) test data per EN 15188 / ASTM E2021, and
  `createAnalyzer(...)` carries the fit straight to a plant-scale screening.
- `SelfHeatingGeometry` carries the published critical values (slab 0.878,
  infinite cylinder 2.00, sphere 3.32, cube 2.52, equicylinder 2.76).

**Agent guidance — do not substitute the wrong model:**

- `neqsim.process.safety.reaction.RunawayReactionAnalyzer` is **lumped adiabatic**
  (MTSR / dT_ad / TMR_ad). It has no spatial conduction, therefore no concept of a
  critical thickness or critical ambient temperature, and **cannot** assess
  spontaneous ignition in insulation. Use `selfheating` instead.
- `GibbsReactor` will report complete oxidation of any hydrocarbon at ambient
  temperature. Equilibrium gives the fuel, not the hazard — ignition is always a
  kinetic question.
- Activation energy and pre-factor are **measured**, never derived from
  thermodynamics. Flag them as assumptions in `results.json` when not from testing.

**Data fix — TEG and DEG formation properties were placeholders.** Both carried
water's enthalpy of formation (`-242000 J/mol`) and CO2's Gibbs energy of formation
(`-394370 J/mol`) in `COMP.csv` and `COMP_EXT.csv`. Corrected to literature
ideal-gas values: TEG `-726500 / -474700 J/mol`, DEG `-571200 / -402900 J/mol`.
Any previous Gibbs-minimisation, reactive-flash or heat-of-reaction result
involving a glycol was wrong (TEG heat of combustion was ~25.4 MJ/kg, now
~22.2 MJ/kg). Normal enthalpy calculations were unaffected, because the formation
term is multiplied by zero in that path.

**New skill:** `neqsim-self-heating-ignition`. Loaded by `@safety-depressuring` and
`@reaction-engineering`; routed from `@router` on "self-ignition, spontaneous
combustion, lagging fire, fire with no ignition source".

---

## 2026-08-07 — Coupled transient gas-network hydraulics and source schedules

- Added `TransientGasNetwork` for bounded, positive-flow, one-phase isothermal
  gathering trees. It simultaneously solves source and junction pressures,
  Darcy edge flow, compressible linepack, and conservative named-component
  transport for scheduled source rates/compositions and one fixed-pressure
  sink.
- `TransientGasNetworkHistory` provides time-aligned node pressure/composition,
  edge inlet/average/outlet flow, edge linepack, and immutable hydraulic,
  junction, and component-conservation reports. The history and reports have a
  stable JSON path for Python/JPype.
- Source pressure is an emergent feasibility result, not a prescribed boundary
  or a deliverability controller. Pressure bounds and edge velocity limits fail
  explicitly; reverse flow, phase appearance, thermal transport, recirculation,
  and branching splits remain outside the initial API.
- Added a 700 km synthetic Åsgard/Kristin-to-Kårstø rate-event regression with
  the sink fixed at 110 bara, comparison to the 200/207 bara quasi-steady
  anchors, deterministic repeat, linepack response, component/junction/total
  balance gates, unsupported-state diagnostics, and joint grid/timestep
  refinement.
## 2026-08-06 — Per-case ranked capacity snapshots

- `ProcessModelSimulationEvaluator.EvaluationResult.getRankedCapacityConstraints()` retains the
  immutable full-model capacity ranking produced after that exact simulation point.
- The legacy active bottleneck is selected from the same ranking, while undefined-utilization
  constraints remain visible at the end for diagnosis.
- `ThroughputCaseRow.getRankedCapacityConstraints()` preserves emerging and switching constraints
  for every scalar-throughput case. JSON exports include the complete ranked list and its evidence
  applicability; CSV remains the flat leading-limit summary.
- Use these case-owned snapshots for debottleneck histories and AI/tool output. Do not rescan the
  live model after a later evaluation and attribute that state to an earlier case.

---
## 2026-08-06 — Artificial-lift screening rejects non-physical calculated results

- `ArtificialLiftScreener.screen()` now rejects a calculated method result before ranking when its
  production rate is non-finite or non-positive, or when its power consumption is non-finite or
  negative.
- Rejected results have zero reported rate and power, negative-infinite NPV, rank zero, and an
  explicit infeasibility reason. A method reported as feasible therefore always has a finite,
  positive rate and finite, non-negative power.
- The field-development API example now uses the current `ArtificialLiftScreener` method names,
  units, and `ScreeningResult` return type.

## 2026-08-06 — Evidence-aware full-model capacity ranking

- `ProcessModelSimulationEvaluator.rankCapacityConstraints(model)` returns every enabled
  `CapacityConstraint` as an immutable list ordered by descending utilization. Stable ties retain
  process-model registration order, and each dynamic value supplier is sampled once per call.
- `BottleneckStatus.getEvidenceApplicability()` reports `WITHIN_VALIDITY_RANGE`,
  `OUTSIDE_VALIDITY_RANGE`, or `NOT_ASSESSED` for the snapshotted operating point.
- Evidence confidence and applicability remain diagnostics only. They do not change utilization,
  feasibility, or ranking and must not be interpreted as probabilities of safe operation.
- `findActiveBottleneck(model)` remains available when only the leading constraint is required.

---

## 2026-08-06 — Side-draw flow targets reject invalid inner column states

- A column with one independent side-draw flow specification now evaluates trial split fractions
  on cold copied column states and accepts only rigorous or reconciled inner solves.
- Failed and fallback-product trials no longer update the side-draw controller or replace the last
  accepted public column state. The safeguarded search uses only accepted flow observations for
  interpolation and bounded exploration.
- When a cold trial is rejected after an accepted state exists, the same fraction is retried once
  from the nearest accepted solved profile. This continuation path removes Java-runtime-dependent
  cold-start failures without reusing a failed or fallback-product state.
- `DistillationColumn` now reports rejected candidates, state rollbacks, accumulated inner-solver
  work, and the candidate history through its column-tear diagnostics. These values are transient
  and reset on copied or deserialized columns.

---

## 2026-08-05 — Lossless external-to-internal constraint conversion

- `ProcessSimulationEvaluator.ConstraintDefinition.toOptimizationConstraints()` now converts
  lower/upper bounds to one immutable-list element and range/equality definitions to explicit
  `_lower` and `_upper` internal constraints.
- Both generated sides retain the source evaluator, severity, and penalty weight, preventing
  operating envelopes and tolerance bands from silently losing their lower bound.
- The singular `toOptimizationConstraint()` API is unchanged for compatibility and remains lossy
  for range and equality definitions. New integrations must use the plural method.
- Interpret sensitivities and shadow values for the generated lower and upper constraints
  separately.

---

## 2026-08-05 — Column pumparound returns remain internal recycles

- `DistillationColumn` no longer captures a named pumparound return stream as a legacy direct
  external tray feed during iterative solves.
- `getInletStreams()`, feed fingerprints, and feed/product balance diagnostics therefore retain
  only caller-supplied feeds; configured pumparound returns remain internal recycles.

---

## 2026-08-05 — Conservative transient gas-network species transport

- Added `TransientCompositionalPipeNetwork` for prescribed positive-flow,
  one-phase, isothermal gathering networks. Source composition and mass-flow
  schedules propagate through finite-volume edge inventories and conservative
  component-name junction mixing without an instantaneous manual handoff.
- `TransientCompositionalPipeNetworkHistory` exposes defensive, time-aligned
  node mass fractions plus immutable edge, junction, and cumulative network
  conservation reports. The history and reports provide JSON capture for
  Python/JPype.
- The first validated scope is a directed acyclic network with at most one
  outgoing edge per node. Reverse flow, branching splits, recirculation,
  hydraulic/thermal coupling, dispersion, and phase appearance fail explicitly
  or remain outside this API.
- Added a two-source finite-CO2-pulse regression with deterministic repeat,
  component-order independence, balance/boundedness gates, linepack delay and
  broadening, unsupported-state diagnostics, and joint grid/timestep
  refinement.

---

## 2026-08-05 — Conservative TwoFluidPipe component transport

### Added
- Opt-in per-cell, per-phase named-component inventories in `TwoFluidPipe`, advected with the accepted gas/oil/water face fluxes.
- Equal-and-opposite component mapping for gas/oil/water flash transfer, fail-loud thermodynamic synchronization, bounded profiles, immutable reports, JSON diagnostics, and report history.
- Composition-dependent interphase latent heat in the transient temperature equation and thermal-energy ledger.
- Java and JPype/Python access to gas, oil, and water component profiles and outlet mass fractions.

### Compatibility and scope
- Backward compatible: component transport is disabled by default and must be enabled before `run()`.
- The initial validated scope requires a fixed named component slate, known inlet composition, gas/oil/aqueous phase identities, and no reverse inflow through the outlet boundary.
- Internal signed phase-flow reversals are handled with phase-consistent upwinding; unsupported phase transitions and non-closing component/phase ledgers throw.

---

## 2026-08-05 — External process evaluators sample result callbacks once

- `ProcessSimulationEvaluator.evaluate(...)` and `ProcessModelSimulationEvaluator.evaluate(...)`
  now invoke each registered objective and constraint callback exactly once per completed
  simulation point.
- Raw and minimizer-sign objectives reuse one scalar. Constraint value, margin, feasibility, and
  penalty likewise reuse one scalar, avoiding repeated report or serialization work and preventing
  internally inconsistent results from mutable diagnostics.
- Direct calls to objective and constraint definition methods retain their existing behavior and
  public signatures.

---

## 2026-08-05 — TwoFluidPipe transient thermal-energy closure

- The multilayer cooldown path now removes fluid energy with the same instantaneous fluid-to-first-layer flux that
  advances the radial wall state. It no longer mixes that transient flux with a separate steady overall-U heat-loss
  estimate.
- `MultilayerThermalCalculator` exposes the fluid-side and ambient-side heat rates used by its most recent transient
  update through `getLastFluidHeatTransferPerLength()` and `getLastAmbientHeatTransferPerLength()`.
- `TwoFluidPipe.getLastThermalEnergyBalanceReport()` now reports time-integrated fluid and wall energy changes,
  conservative-face sensible advection, Joule-Thomson energy, ambient heat loss, and absolute/relative residuals for
  the most recent thermal transient call.
- Added simple/multilayer, Euler/IMEX, mesh/time-step refinement, disabled-heat-transfer, serialized-copy, and closed
  SRK-CPA water-dew-point appearance/disappearance regressions. The phase-transition case checks gas, oil, water, and
  total mass closure without a seeded liquid phase.

---

## 2026-08-05 — Column exact reuse honors active convergence gates

### Corrected

Naphtali-Sandholm exact unchanged-input reuse now requires the active convergence-gate
configuration to match the snapshot recorded after the accepted public solve. Changing an enforced
tolerance no longer returns the previous state with zero iterations under a different convergence
contract. Disabled energy and MESH tolerances, plus outer tear tolerances when no tear variable is
configured, are excluded from the cache key so irrelevant setting changes retain zero-iteration
reuse.

## 2026-08-05 — ProcessModel unit-level mass closure is reported again

### Added

`ProcessModel` now reports a second mass-closure figure covering the units the recycle-tear gate
does not own. `getLastUnitMassClosureError()` and `getUnitMassClosureOffenders()` expose the mass
created or destroyed by non-recycle unit operations as a fraction of plant feed, the
`getMassClosureSummary()` text states it alongside the recycle-tear result, and the `massClosure`
JSON block gained `unitRelativeError`, `unitWorstUnits`, and `unitGateEnabled`. Bypassed and
low-flow units are excluded (`ProcessSystem.getFailedMassBalance`), and recycles are skipped so
they are not counted twice.

The figure is **report-only by default**: a non-recycle unit that does not conserve mass is an
equipment defect rather than something the outer solver can close, so gating on it would iterate
to the cap and bury the real diagnosis. Opt in with `setUnitMassClosureGate(true)` to make it
block a converged verdict as well.

The closure is now also evaluated once after a run that never reached the acceptance test, so a
model that stops on the iteration cap still reports what it is failing to conserve instead of
leaving `relativeError` null behind a max-iterations warning.

### Compatibility and validation

No default, gate outcome, or existing JSON field changed; the recycle-tear gate and its
`relativeError`/`worstUnits` fields behave exactly as before. Callers that only read the recycle
figures are unaffected.

---
## 2026-08-05 — Optional Chabab 2019 Søreide-Whitson CO2-brine parameterization

### Added

`SystemSoreideWhitson` now exposes `setAqueousCO2Parameterization(...)` with enum and string
overloads. `LEGACY` remains the default and preserves existing results. Select `CHABAB_2019`
(aliases `M_SW` or `m-sw`) to use the Chabab et al. (2019) aqueous CO2-water binary-interaction
correlation for NaCl brine. The salt basis used by the correlation is equivalent NaCl molality in
mol/kg water; the published measured range is approximately 1-3 mol/kg, 323-373 K, and up to 230 bar.

The selector is directly accessible through Java and Python/JPype, survives system cloning, and
does not change other gas-water or non-aqueous interaction parameters.

## 2026-08-04 — Fixed-reflux fallback product inventory

### Corrected

When a fixed-liquid-reflux column rejects its tray state and installs guarded full-feed fallback
products, the condenser's separate liquid product is now cleared. The fallback gas and bottom
streams already contain the complete feed inventory; retaining the rejected liquid product beside
them previously exposed more material than entered the column. Fixed-reflux availability,
delivery, and residual diagnostics are invalidated because they no longer describe the exposed
fallback products.

The solve status remains `FALLBACK_PRODUCTS`, `solved()` remains false, and no tray equation,
tolerance, flash, or iteration rule changed. Callers must continue to inspect the solve status
before treating column products as a rigorous fixed-reflux solution.

## 2026-08-04 — TwoFluidPipe closed thermal boundary consistency

- Transient temperature advection now consumes the conservative solver's retained phase-resolved face mass fluxes,
  combined with the configured integrator's stage weights and one pre-update temperature snapshot; it no longer
  recomputes AUSM+ fluxes after acceptance or reads already-updated upstream cells.
- CLOSED external faces contribute exactly zero advective transport while internal phase convection remains active.
- Simple and multilayer radial heat transfer now visit section zero and use local conservative fluid inventory for
  thermal inertia. Stateful multilayer wall temperatures are retained independently for every cell and advanced once
  per accepted thermal time step.
- The post-step temperature model is the single owner of ambient heat exchange, preventing the duplicate equation-level
  wall source from applying the same loss twice.
- Added deterministic regressions for disconnected inlet-rate invariance, uniform closed adiabatic behavior, all-cell
  cooldown without ambient undershoot, independent multilayer cell state, and zero closed external face flux.
## 2026-08-04 — ProcessModel recycle mass-closure reporting

### Corrected

The automatic model-level mass-closure convergence gate now evaluates active `Recycle` tear
imbalances only. Unit-level mass-balance diagnostics remain available through `ProcessSystem` but
no longer masquerade as open recycle tears or block an otherwise converged multi-area model.

The `massClosure` JSON block reports `enabled: true` only when automatic convergence tuning and
the closure gate are both active. An unevaluated `relativeError` is emitted as JSON `null` rather
than the non-standard numeric literal `NaN`.

### Compatibility and validation

No public method or default is removed. Existing callers should treat `relativeError: null` as
"not evaluated" and inspect unit-level mass-balance reports separately from the recycle-tear
convergence gate.
## 2026-08-04 — Capacity evidence in bottleneck and throughput results

### Added

`ProcessModelSimulationEvaluator.BottleneckStatus` and `ThroughputCaseRow` now snapshot the
active constraint's confidence presence/value, scalar validity-range presence/bounds, and whether
the evaluated current value lies inside the inclusive range. The metadata is available through
Java getters and is retained by throughput JSON and CSV exports.

### Compatibility and reporting

Existing constructors remain available and represent evidence metadata as unset. Manually constructed
snapshots normalize inconsistent enabled metadata (non-finite/out-of-range confidence or
non-finite/reversed bounds) to the same unset state, and derive applicability from the current value
and retained bounds. Bottleneck scans read dynamic constraint suppliers once per candidate and use
that scalar for both utilization and applicability. JSON includes presence flags and uses `null` for
unset confidence, bounds, and applicability. CSV includes the same flags and uses blank cells for
unset values. Utilization, constraint direction, margins, feasibility, thermodynamics, hydraulics,
and throughput search are unchanged.

---

## 2026-08-04 — ConeFlowMeter rejects non-physical geometry (Copilot review round 11)

### Fixed

- `ConeFlowMeter.setGeometry(double, double, String)` now validates `coneDiameter &lt; pipeDiameter` (both positive)
  instead of silently clamping the beta formula's `sqrt` argument to 0 with `Math.max(0.0, ...)`. Invalid geometry
  (cone diameter &gt;= pipe diameter, or either non-positive) now logs a warning and stores a `NaN` throat diameter,
  consistent with `WedgeFlowMeter`'s invalid-geometry handling, instead of silently producing beta = 0.
- `ConeFlowMeter.getConeDiameter(String)` now returns `NaN` for a non-physical beta (NaN, &lt;= 0, or &gt; 1) instead
  of clamping to a misleading 0 diameter.

---

## 2026-08-04 — Reject non-physical discharge coefficients in the Reynolds iteration (Copilot review round 10)

### Fixed

- `DifferentialPressureFlowMeter.getMassFlowRatePerSecond()`'s Reynolds-number iteration only checked
  `Double.isFinite(updatedFlow)`, so a Reynolds-independent device whose `calcDischargeCoefficient(...)` returns a
  non-physical value (`C &lt;= 0`) could silently converge to a negative (but finite) mass flow and Reynolds number on
  the very first pass. The initial guess and every iteration now also reject `flow &lt;= 0.0`, returning `NaN` (with a
  warning identifying the offending Re,D) instead of a silently wrong negative flow.

---

## 2026-08-04 — Test doc/privacy wording fixes (Copilot review round 9)

### Fixed

- `OrificeFlowMeterTest.buildWetGasMeter(double)` JavaDoc claimed `p1 = 60 bara`, but the meter uses the shared
  `stream` fixture from `setUp()`, which is 20 bara. Reworded to describe the actual upstream pressure source.
- Redacted equipment tag identifiers (`27A-KA01A` / `27A-KA60`) from a `VenturiFlowMeterTest` JavaDoc comment, per the
  repository's privacy rule against including equipment tag numbers in public/reusable content.
- `DocExamplesCompilationTest.buildDocExampleWetGasStream()` no longer hard-asserts an exact phase count of 2 (brittle
  if NeqSim ever adds another phase type); it now asserts the intended `gas` and `oil` phases are both present via
  `hasPhaseType(...)`.

---

## 2026-08-04 — Volume-unit conversion gaps fixed (Copilot review round 8)

### Fixed

- `DifferentialPressureFlowMeter.volumeFlowConversionToM3PerSecond(String)` now supports every unit string that
  `isActualVolumeUnit(String)`/`isStandardVolumeUnit(String)` classify as valid: `Sm^3/sec`, `kSm3/sec`, `MSm3/sec`,
  `m^3/min`, `Sm^3/min`, `kSm3/min`, `MSm3/min`, and `m^3/day` were previously missing, so `getVolumeFlowRate(unit)` /
  `getStandardVolumeFlowRate(unit)` threw `RuntimeException` for those (previously "valid-looking") unit strings.

### Verified as a false positive (no change made)

- A review also claimed `OrificeFlowMeter`/`VenturiFlowMeter`'s `buildWetGasSignature()` cache never hits because
  `Arrays.equals(double[], double[])` treats `NaN != NaN`. This is incorrect: per the method's own Javadoc contract
  (and confirmed with a standalone JVM check), `Arrays.equals(double[], double[])` compares `Double.doubleToLongBits`
  values and explicitly treats two `NaN`s as equal. Added a one-line note to `buildWetGasSignature()` in both classes
  documenting this so future reviews don't re-flag it.

---

## 2026-08-04 — Reynolds-cache staleness and nozzle math-domain fixes (Copilot review round 7)

### Fixed

- `DifferentialPressureFlowMeter.getMassFlowRatePerSecond()` now resets `lastReynoldsNumberPipe` to `NaN` on every
  invalid-input/invalid-expansibility early return, not just when `dp &lt;= 0`, so `getReynoldsNumberPipe()` never
  reports a stale value from a previous successful solve after a failed one.
- The Reynolds-number iteration now checks `Double.isFinite(updatedFlow)` each pass and fails fast (NaN + a logged
  warning identifying the offending Re,D) instead of running all `MAX_ITERATIONS` passes and logging a misleading
  "did not converge" warning when a device-specific discharge-coefficient correlation produces NaN/Infinity.
- `NozzleFlowMeter.calcThroatTappedDischargeCoefficient(double)` now explicitly returns `NaN` for
  `reynoldsThroat &lt; 400000`, instead of relying on `Math.pow(negative, 0.8)` (a non-integer power of a negative
  base) to produce `NaN` indirectly once `1 - 400000 / Re,d` goes negative.

---

## 2026-08-04 — Doc/test wording fixes and CONE beta validation (Copilot review round 6)

### Fixed

- `ExpansibilityModel.CONE.calculate(...)` now validates `0 < beta < 1` like `ORIFICE` and `ISENTROPIC`, instead of
  silently returning a finite value for non-physical geometry.
- Corrected the low-dP limit comment on `ExpansibilityModel.ISENTROPIC`: the indeterminate
  `(1 - tau^((kappa-1)/kappa)) / (1 - tau)` term itself tends to `(kappa-1)/kappa`, not `1`; it is the overall
  expansibility factor that tends to `1.0`.
- `WedgeFlowMeter.setWedgeRatio(double)` JavaDoc no longer claims the pipe diameter is "required" to already be set
  (it isn't enforced); it now documents the actual behavior, including the base class's 0.2 m default.
- Renamed the misleading "dry-gas example" JavaDoc on `testVenturiFlowMeterDoc()`/`testOrificeFlowMeterDoc()` in
  `DocExamplesCompilationTest`, which actually exercise the two-phase `buildDocExampleWetGasStream()` helper with
  `WetGasCorrelation.NONE` (the liquid load is simply ignored in that mode, not absent from the stream).
- Removed the remaining `System.out.println` calls from `docs/process/equipment/measurement_devices.md` code
  snippets (CO2 emissions, NMVOC, HC/water dew point, cricondenbar, FIV LOF/F-RMS, molar mass, water content, pH).

---

## 2026-08-04 — Wet-gas getter caching for OrificeFlowMeter and VenturiFlowMeter

### Changed

- `OrificeFlowMeter` and `VenturiFlowMeter` no longer re-run the full iterative wet-gas solve on every getter call
  (`getLockhartMartinelliParameter()`, `getGasDensiometricFroudeNumber()`, `getOverReadingFactor()`, etc., plus
  `getMassFlowRatePerSecond()`). Each now caches the last `WetGasResult` behind a cheap input fingerprint
  (`buildWetGasSignature()`: differential pressure, upstream pressure, beta, gas density, viscosity/discharge
  coefficient, liquid load configuration, and wet-gas correlation settings). Reading multiple derived quantities within
  the same timestep now reuses one solve instead of re-solving per getter, and all getters are guaranteed to reflect
  the same solved state. The cache is not manually invalidated by setters; it is recomputed automatically whenever the
  fingerprint changes (e.g. after `process.run()` advances the stream, or after any wet-gas setter call).
- Documentation code snippets no longer use `System.out.println` (project convention: avoid it in examples that may be
  copied into production code).
- `DocExamplesCompilationTest.buildDocExampleWetGasStream()` now asserts the built stream is two-phase, instead of
  assuming it silently.

---

## 2026-08-04 — DP flow-meter Copilot review fixes (Reynolds cache, volume-unit dispatch, near-zero-dP expansibility)

### Fixed

- `DifferentialPressureFlowMeter.getMassFlowRatePerSecond()` now resets the cached
  `lastReynoldsNumberPipe` to `NaN` when the differential pressure is not positive, instead of
  leaving it at the previous solve's converged value. `getReynoldsNumberPipe()`,
  `getReynoldsNumberThroat()`, and `getValidityViolations()` no longer report stale Reynolds-number
  information after `dp` drops to zero (or negative).
- `DifferentialPressureFlowMeter.getVolumeFlowRate(String unit)` now delegates to
  `getStandardVolumeFlowRate(unit)` when `unit` is a standard-volume unit (`Sm3/...`, `kSm3/...`,
  `MSm3/...`). Previously it always divided by the flowing (actual) gas density, so
  `getVolumeFlowRate("Sm3/hr")` silently returned a dimensionally-wrong value instead of the correct
  standard-condition flow.
- `ExpansibilityModel.ISENTROPIC.calculate(...)` now returns `1.0` instead of `NaN` when `tau` is
  within `1e-12` of `1.0` (the low-differential-pressure limit). The `(1 - tau^((kappa-1)/kappa)) /
  (1 - tau)` term is a removable 0/0 indeterminate form whose limit is `(kappa-1)/kappa`, which makes
  the overall expansibility factor tend to `1.0` — i.e. no expansion for a negligible pressure drop,
  matching physical expectation instead of propagating `NaN` into the flow calculation.
- Documentation code snippets in `docs/process/equipment/measurement_devices.md` that reference
  `List<String> issues = meter.getValidityViolations();` now include `import java.util.List;` so
  they compile standalone if copied into a small program.

---

## 2026-08-04 — ISO/TR 11583 Clause 7 wet-gas correction added to OrificeFlowMeter

### Added

`OrificeFlowMeter.setWetGasCorrelation(WetGasCorrelation.ISO_TR_11583)` switches the meter to the
ISO/TR 11583 Clause 7 wet-gas orifice method. The liquid load is supplied via
`setLiquidFromStream(true)` (reads the connected stream's own phase split), `setLiquidToGasMassRatio(x)`,
`setLiquidMassFlowRate(v, unit)`, or (when 0.5 <= beta <= 0.68 and no explicit liquid rate/ratio is
given) the 7.5.5 permanent pressure-loss route via `setPressureLoss(v, unit)`. New getters:
`getLockhartMartinelliParameter()`, `getGasDensiometricFroudeNumber()`, `getOverReadingFactor()`,
`getChisholmCoefficient()`, `getChisholmExponent()`. `getValidityViolations()` now reports the Clause 7
limits of use (0.24 <= beta <= 0.73, 0 < X <= 0.3, Fr,gas >= 0.2, rho,gas/rho,liquid > 0.014, D >= 50 mm,
plus the additional 7.5.5 bounds when the pressure-loss route is used) instead of the dry-gas ISO 5167-2
limits when the correlation is active.

**Key difference from `VenturiFlowMeter`'s Clause 6 wet-gas method**: the orifice discharge coefficient
is **never replaced** (Clause 7.5.2) — it stays the plain Reader-Harris/Gallagher equation evaluated at
the gas-only Reynolds number, so there is no `useWetGasDischargeCoefficient`-style guard. The Chisholm
exponent also has no diameter-ratio term (unlike Venturi's beta-reduced exponent):
`n = 0.214` for `Fr,gas < 1.5`, `n = (1/sqrt(2) - 0.3/sqrt(Fr,gas))^2` for `Fr,gas > 1.5`.

`DifferentialPressureFlowMeter` gained a protected `setReynoldsNumberPipe(double)` so a wet-gas subclass
can record its own converged Reynolds number on the base class; without it, `getReynoldsNumberPipe()`
stayed pinned at the dry-gas seed value from the initial solve.

### Compatibility

All 8 pre-existing `OrificeFlowMeterTest` cases pass unchanged (default correlation is `NONE`). 9 new
wet-gas tests added (17 total). No other DP flow meter class is affected.

## 2026-08-04 — ISO 5167 differential-pressure flow meters: shared base class + orifice/nozzle/cone/wedge

### Added

`DifferentialPressureFlowMeter` (abstract, `neqsim.process.measurementdevice`) is the new shared base
for ISO 5167-1 general-principles physics: geometry (`setGeometry`/`setPipeDiameter`/`setThroatDiameter`,
diameter ratio always `beta = d/D` recomputed on demand), differential pressure (explicit or via
`DifferentialPressureTransmitter`), gas density/isentropic exponent/dynamic viscosity readers (each
overridable), a Reynolds-number fixed-point iteration for devices whose discharge coefficient depends on
`Re,D`, and the mass/actual-volume/standard-volume/`getMeasuredValue` accessors. `ExpansibilityModel`
(enum: `ORIFICE`, `ISENTROPIC`, `CONE`) holds the three expansibility-factor families shared across ISO
5167-2/-3/-4/-5/-6.

Four new concrete devices, each implementing only its own discharge coefficient and expansibility model:

- `OrificeFlowMeter` (ISO 5167-2) — Reader-Harris/Gallagher (1998) discharge coefficient,
  `TappingArrangement` (`CORNER`, `D_AND_D_HALF`, `FLANGE`).
- `NozzleFlowMeter` (ISO 5167-3) — `NozzleType` (`ISA_1932`, `LONG_RADIUS`, `THROAT_TAPPED`,
  `VENTURI_NOZZLE`); the first three depend on the Reynolds number, the Venturi nozzle does not.
- `ConeFlowMeter` (ISO 5167-5) — constant C = 0.82; no physical throat, `beta = sqrt(1 - dc^2/D^2)`
  derived from the cone diameter via `setGeometry(D, dc, unit)`.
- `WedgeFlowMeter` (ISO 5167-6) — C = 0.77 - 0.09 beta; no physical throat, beta derived from the wedge
  gap height (`setGeometry(D, h, unit)`) or wedge ratio (`setWedgeRatio(h/D)`) via ISO 5167-6 Formula (3).

### Compatibility and migration

`VenturiFlowMeter` (ISO 5167-4) is re-parented onto `DifferentialPressureFlowMeter` with **no public API
change** — same constructors, same method signatures, same wet-gas (ISO/TR 11583, de Leeuw) behavior. All
20 pre-existing `VenturiFlowMeterTest` cases pass unchanged. Wet-gas over-reading correction (liquid load,
Lockhart-Martinelli, Froude number, Chisholm form) remains Venturi-specific; it has not been generalized to
the other four devices in this change.

### Not in scope (raise separately if needed)

`neqsim.standards.gasquality.Standard_AGA3`'s own Reader-Harris/Gallagher implementation has known
transcription bugs (missing terms, wrong Reynolds-number basis) found while verifying `OrificeFlowMeter`
against the same ISO 5167-2:2022 Formula (4); it was deliberately left untouched pending maintainer review.

## 2026-08-03 — Capacity constraint confidence and validity metadata

### Added

`CapacityConstraint` now records an optional evidence-quality confidence score in `[0, 1]` and an
optional inclusive scalar validity range in the constraint's own unit. Fluent setters validate all
values, explicit `hasConfidence()` and `hasValidityRange()` methods preserve unset semantics, and
`isCurrentValueWithinValidityRange()` checks the live constraint value against the stated range.

### Compatibility and behavior

Existing constructors and serialized constraints remain compatible. Unset and legacy metadata is
reported as absent and numeric getters return `NaN`. The metadata does not alter utilization,
constraint direction, margins, violation status, feasibility, or optimizer search. Confidence is
an evidence-quality score, not a probability of safety or constraint satisfaction. This release
propagates confidence and validity into throughput case rows as of 2026-08-04, but does not
implement a multidimensional operating envelope.
## 2026-08-03 — TwoFluidPipe phase-resolved flash transfer

### Corrected

`ThermodynamicCoupling` now identifies phases by `PhaseType`, aggregates all hydrocarbon and
aqueous liquid contributions, and returns immutable `PhaseMassTransfer` gas/oil/water sources.
Condensation follows equilibrium liquid mass contributions. Evaporation follows and is limited by
the actual conservative oil and water inventories. Transfer momentum uses donor velocity and is
conservative across all three phases.

`FlashTable` now stores the aggregate liquid fraction, oil/aqueous mass split, and gas/liquid molar
masses. Existing serialized tables without these arrays return a rebuild diagnostic instead of
silently reconstructing ambiguous liquid identity.

### Compatibility and validation

`calcMassTransferRatePerLength(...)` and the internal two-element gas/liquid adapter remain
available. Code needing phase identity should call `calcPhaseMassTransferRatePerLength(...)` and
read the SI-unit gas, oil, and water sources. Validate each phase with `TwoFluidMassBalanceReport`;
total-mass closure alone does not prove correct liquid identity.

## 2026-08-03 — Capacity provenance in process-model throughput results

### Added

`ProcessModelSimulationEvaluator.BottleneckStatus` and `ThroughputCaseRow` now preserve the
underlying `CapacityConstraint.dataSource`. Java getters, JSON case rows, and CSV throughput traces
therefore retain whether a limiting value came from sources such as mechanical design, an installed
data sheet, or an operating envelope. Untagged and legacy constructor paths use `not_set`.

### Compatibility and reporting

Existing constructors remain available and retain their previous behavior. JSON adds `dataSource`;
the CSV column is inserted after `minimumConstraint`. No thermodynamic, hydraulic, utilization,
feasibility, equipment-design, or throughput-search calculation changed.

## 2026-08-02 — Directed capacity margins in process-model throughput results

### Corrected

`ProcessModelSimulationEvaluator.BottleneckStatus` and `ThroughputCaseRow` now preserve whether an
equipment bottleneck is minimum-directed. Minimum-only constraints report their finite
`getDisplayDesignValue()` limit instead of the internal unset `Double.MAX_VALUE` design sentinel.
Engineering-unit `capacityMargin` is `current - minimum` for lower limits and remains
`limit - current` for upper limits, so non-negative consistently means feasible.

### Compatibility and reporting

Existing constructors remain available and default to maximum-directed behavior. JSON case rows
and CSV throughput traces add `minimumConstraint`; the CSV column is inserted after `designValue`.
No thermodynamic, hydraulic, utilization, feasibility, or optimizer search calculation changed.

## 2026-08-02 — DNV-RP-F101 isolated metal-loss pressure screening added

### Added

`DnvRpF101CorrodedPipelineScreeningKernel` implements a fail-closed calculation for the current
`DNV-RP-F101 2019-09+AMD:2025-09` basis. Its narrow scope is one isolated longitudinal metal-loss
defect under internal pressure. It reports assessment depth, remaining wall, length correction,
uncorroded and defect failure pressures, a caller-controlled pressure limit, utilization, margin,
and within-limit status.

### Required evidence and migration

Measured defect geometry, depth allowance, assessment wall thickness, characteristic ultimate
tensile strength, internal/external pressures, caller-controlled pressure factor, applicability,
and verification attestations are explicit inputs. Interacting or complex defects, combined
compression, probabilistic assessment, inspection-uncertainty derivation, corrosion growth,
crack-like damage, repair, and fitness-for-service approval remain external.

Agents must not convert a NORSOK M-506 corrosion rate or projected uniform loss into inspected
RP-F101 defect geometry. The RP-F101 kernel is also separate from and does not replace DNV-ST-F101
pressure containment, collapse, propagation/local buckling, load interaction, fatigue,
incidental/test pressure, de-rating, safety class, ovality, fabrication route, or installation
strain.

## 2026-08-02 — DNV-RP-F105 added as a first-mode free-span screening kernel

### Added

`DnvRpF105FreeSpanScreeningKernel` implements an edition-aware, fail-closed screen for
`DNV-RP-F105 2025-12`. It calculates a simply supported Euler-Bernoulli first-mode frequency with
externally derived effective mass and axial force, then reports current/wave frequency ratios,
reduced velocities, and Keulegan-Carpenter number. Steel and hydrodynamic diameters are distinct.

### Required evidence and migration

Geometry, structural-model, environmental, and project-trigger verification are mandatory caller
attestations. Strouhal number, frequency-ratio band, and reduced-velocity triggers are
project-controlled evidence; they are not embedded DNV criteria or acceptance decisions. Soil and
span-shoulder stiffness, interacting spans, response amplitudes, direct wave loading, ULS/FLS,
fatigue, monitoring, intervention, and conformity remain external.

`PipeMechanicalDesignCalculator.calculateAllowableSpanLength(...)` remains compatible but is a
legacy fixed-assumption estimate with fallback/cap behavior. Agents must not relabel it as F105 and
must route an explicit current-edition basis through the typed kernel.

The standards resource index now records the current F105 edition/applicability. Unverified legacy
CSV values labelled as F105 safety factors, fatigue factors, allowable stress, and maximum span were
removed rather than relabelled as current. The generic transient-pipe surge allowance is now
identified as project basis, not F105.

---

## 2026-08-02 — DNV-RP-C203 added as a controlled-curve fatigue kernel

### Added

`DnvRpC203FatigueDesignKernel` implements the S-N and Palmgren-Miner arithmetic for the current
`2024-10+AMD:2025-10` basis. It accepts immutable spectrum bins, stress-range factors, a design
fatigue factor, damage limit, and a caller-supplied single-slope or continuous bi-linear curve.
`DnvRpC203FatigueAssessment` reports per-bin cycles to failure and damage, cumulative raw and design
damage, utilization, governing bin, and linear-extrapolated life.

### Required evidence and migration

NeqSim deliberately does not embed or select licensed DNV S-N tables. Curve and spectrum
verification flags are attestations and both are required before calculation. Curve/detail
selection, structural stress derivation, environment/thickness factors, SCFs, rainflow counting,
load combination, inspection planning, and conformity remain external.

Existing pipeline and riser fatigue methods remain compatible but use inconsistent embedded
parameters. Agents must call them legacy estimates and must route an explicit current-edition C203
basis through the typed kernel with a controlled project curve.

---

## 2026-08-02 — ISO 5167-1/-2 added to the edition-aware standards pipeline

### Added

`ISO-5167-1 2022` and `ISO-5167-2 2022` are catalogued separately with ISO publisher lifecycle
sources. Part 1 records the companion general-principles basis; Part 2 registers the new
`Iso5167OrificeMeteringKernel` for `Orifice` equipment.

The kernel reuses the existing `Orifice` Reader-Harris/Gallagher and pressure-loss equations through
an immutable, unit-explicit contract. Liquid service uses an explicit expansibility factor of one,
while gas/vapour service requires kappa and applies the existing compressible correction. The typed
result records beta ratio, differential and pressure ratios, discharge and expansibility factors,
mass and actual-volume flow, pipe Reynolds number, permanent pressure loss, and iteration count.

### Applicability and boundary

The adapter fails closed for unsupported editions or amendments, non-`Orifice` equipment,
multiphase/part-full/pulsating/non-subsonic flow, pipe diameter outside 50 mm to 1,000 mm, beta ratio
outside the implemented 0.10 to 0.75 screen, Reynolds number below 5,000, invalid absolute
pressures/properties, and missing external geometry/installation verification. That verification is
a caller attestation; NeqSim does not inspect the installed meter.

The method remains `SCREENING`. It does not replace purchased ISO 5167-1/-2 documents, uncertainty
analysis, calibration, plate and tapping inspection, straight-length verification, pulsation or
two-phase analysis, custody-transfer acceptance, or accountable engineering approval. Existing
`Orifice`, `Standard_AGA3`, and `GpsaOrificeCalculator` entry points remain available under their
respective process-simulation and AGA/API/GPSA bases.

### Documentation and example

Added `docs/process/measurement/iso_5167_orifice_metering.md` and an executed
`examples/notebooks/iso_5167_orifice_metering_kernel.ipynb`. The common regression suite, support
matrix, migration/program/design-framework guides, standards lookup skill, standards reviewer, and
gas-quality agent now cover the exact ISO path and its exclusions.

## 2026-08-02 — NORSOK M-506 added to the edition-aware standards kernel registry

### Summary

The existing mutable `NorsokM506CorrosionRate` calculation now has a strict common-kernel adapter.
`NORSOK-M-506 2017` is catalogued with publisher lifecycle evidence, exact-edition support, equipment
applicability, readiness blockers, immutable inputs and outputs, and regression coverage.

### New API

| API | What it does |
|---|---|
| `NorsokM506CorrosionDesignKernel` | Runs the existing simplified calculation only after edition, applicability, range, and input-quality checks pass |
| `NorsokM506CorrosionDesignKernel.Input.builder(...)` | Retains unit-explicit raw inputs without the legacy setters' silent clamping |
| `NorsokM506CorrosionAssessment` | Reports rate, pH, fugacity, correction factors, wall shear, and projected uniform wall loss as an immutable review-gated snapshot |

### Migration

Use the kernel for new auditable studies. Keep `NorsokM506CorrosionRate` for legacy mutable workflows
and sweeps, and use `NorsokM506ElectrolyteBridge` when an electrolyte-model pH or FeCO3 saturation
ratio is required. The projected wall loss is not a code corrosion allowance or acceptance decision.

### Agent and skill behavior

Standards and flow-assurance guidance now route explicit M-506 compliance work through the common
kernel and require the screening boundary, purchased-standard review, and NeqSim FeCO3 extension to
remain visible.

---

## 2026-08-02 — Pump NPSH capacity constraint direction corrected

### Summary

`PumpCapacityStrategy` now represents N…43279 tokens truncated…ses a two-pass algorithm and can cross several critical
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

| Bug                                                                            | File(s)                                                                                                                               | Impact                                      |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| Fuller constant 10x too large (`1.013e-2` → `1.013e-3`)                        | `FullerSchettlerGiddingsDiffusivity`                                                                                                  | Gas D values were 10x too high              |
| Critical volume unit conversion (`Vc * 1e3` removed)                           | `FullerSchettlerGiddingsDiffusivity`, `SiddiqiLucasMethod`, `WilkeChangDiffusivity`, `TynCalusDiffusivity`, `HaydukMinhasDiffusivity` | Fallback molar volumes were 1000x too large |
| HaydukMinhas volume formula inverted (`Vc * 1e6 / 0.285` → `0.285 * Vc^1.048`) | `HaydukMinhasDiffusivity`                                                                                                             | Completely wrong liquid D values            |
| Gas LJ parameters from DB unsuitable for diffusion                             | `Diffusivity` (gas base class)                                                                                                        | Chapman-Enskog/Wilke-Lee gave ~60% error    |

### New Features

- **Diffusion-specific LJ parameter table** — ~35 common components from Poling (2001)
  and Bird, Stewart, Lightfoot (2002). Automatically overrides DB LJ parameters for
  gas diffusion calculations in Chapman-Enskog and Wilke-Lee models.
- **`"Chapman-Enskog"` model name** — Added to `setDiffusionCoefficientModel()` for
  explicit selection of the base Chapman-Enskog gas diffusion model.

### New/Updated Model Names for `setDiffusionCoefficientModel()`

| Model String                  | Phase      | Class                                | Status           |
| ----------------------------- | ---------- | ------------------------------------ | ---------------- |
| `"Chapman-Enskog"`            | Gas        | `Diffusivity`                        | **NEW**          |
| `"Wilke Lee"`                 | Gas        | `WilkeLeeDiffusivity`                | Existing (fixed) |
| `"Fuller-Schettler-Giddings"` | Gas        | `FullerSchettlerGiddingsDiffusivity` | Existing (fixed) |
| `"Siddiqi Lucas"`             | Liquid     | `SiddiqiLucasMethod`                 | Existing (fixed) |
| `"Wilke-Chang"`               | Liquid     | `WilkeChangDiffusivity`              | Existing (fixed) |
| `"Tyn-Calus"`                 | Liquid     | `TynCalusDiffusivity`                | Existing (fixed) |
| `"Hayduk-Minhas"`             | Liquid     | `HaydukMinhasDiffusivity`            | Existing (fixed) |
| `"CSP"`                       | Gas/Liquid | `CorrespondingStatesDiffusivity`     | Unchanged        |
| `"High Pressure"`             | Liquid     | `HighPressureDiffusivity`            | Unchanged        |
| `"Alkanol amine"`             | Aqueous    | `AmineDiffusivity`                   | Unchanged        |

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

| Method                                                     | Description                                                  |
| ---------------------------------------------------------- | ------------------------------------------------------------ |
| `setMassTransferModel(MassTransferModel)`                  | `ONDA_1968` or `BILLET_SCHULTES_1999`                        |
| `setEnhancementModel(EnhancementModel)`                    | `NONE`, `HATTA_PSEUDO_FIRST_ORDER`, `VAN_KREVELEN_HOFTIJZER` |
| `setColumnDiameter(double)`                                | Column diameter in metres                                    |
| `setPackedHeight(double)`                                  | Packed height in metres                                      |
| `setPackingSpecificArea(double)`                           | Packing specific area (m2/m3)                                |
| `setPackingVoidFraction(double)`                           | Packing void fraction                                        |
| `setPackingNominalSize(double)`                            | Packing nominal size (m)                                     |
| `setPackingCriticalSurfaceTension(double)`                 | Packing critical surface tension (N/m)                       |
| `setReactionRateConstant(double)`                          | Pseudo-first-order reaction rate constant (1/s)              |
| `setStoichiometricRatio(double)`                           | Stoichiometric ratio for VKH model                           |
| `setBilletSchultesConstants(double, double)`               | Cl and Cv for Billet-Schultes                                |
| `getOverallKGa()` / `getOverallKLa()`                      | Overall mass transfer coefficients                           |
| `getWettedArea()`                                          | Wetted area from correlation                                 |
| `getHeightOfTransferUnit()` / `getNumberOfTransferUnits()` | HTU/NTU                                                      |
| `getStageResults()`                                        | List of `StageResult` with per-stage detail                  |

**Extends:** `SimpleAbsorber`
**Test:** `RateBasedAbsorberTest` (6 tests)

#### 2. SQPoptimizer (`neqsim.process.util.optimizer`)

Full Sequential Quadratic Programming NLP solver with damped BFGS Hessian
update, active-set QP sub-problem, L1 exact penalty merit function, and
Armijo backtracking line search.

| Method                                           | Description                                            |
| ------------------------------------------------ | ------------------------------------------------------ |
| `setObjectiveFunction(ObjectiveFunc)`            | Set objective f(x)                                     |
| `addEqualityConstraint(ConstraintFunc)`          | Add c(x) = 0 constraint                                |
| `addInequalityConstraint(ConstraintFunc)`        | Add h(x) >= 0 constraint                               |
| `setVariableBounds(double[], double[])`          | Lower/upper bounds on variables                        |
| `solve(double[])`                                | Solve from initial point; returns `OptimizationResult` |
| `setMaxIterations(int)` / `setTolerance(double)` | Convergence controls                                   |
| `setFiniteDifferenceStep(double)`                | Step for central-difference gradients                  |

**Inner interfaces:** `ObjectiveFunc`, `ConstraintFunc`
**Inner class:** `OptimizationResult` — `isConverged()`, `getOptimalPoint()`, `getOptimalValue()`, `getIterations()`, `getKktError()`
**Enum added:** `ProcessOptimizationEngine.SearchAlgorithm.SEQUENTIAL_QUADRATIC_PROGRAMMING`
**Test:** `SQPoptimizerTest` (5 tests)

#### 3. PipeHagedornBrown (`neqsim.process.equipment.pipeline`)

Hagedorn-Brown (1965) empirical holdup correlation for vertical/near-vertical
multiphase pipe flow. Best suited for oil production wells.

| Method                                                           | Description                        |
| ---------------------------------------------------------------- | ---------------------------------- |
| `setLength(double)` / `setDiameter(double)` / `setAngle(double)` | Geometry                           |
| `setNumberOfIncrements(int)`                                     | Discretization segments            |
| `setWallRoughness(double)`                                       | Absolute roughness (m)             |
| `getOutletSuperficialVelocity()`                                 | Gas superficial velocity at outlet |
| `getLiquidHoldupProfile()`                                       | `double[]` holdup along pipe       |
| `getFlowPatternDescription()`                                    | Descriptive string                 |
| `getPressureProfile()` / `getTemperatureProfile()`               | `double[]` profiles                |

**Extends:** `Pipeline`
**Test:** `PipeHagedornBrownTest` (3 tests)

#### 4. PipeMukherjeeAndBrill (`neqsim.process.equipment.pipeline`)

Mukherjee-Brill (1985) all-inclination holdup and friction correlation. Handles
horizontal, uphill, and downhill flows with flow pattern detection.

| Method                                     | Description                                                                            |
| ------------------------------------------ | -------------------------------------------------------------------------------------- |
| `getFlowPattern()`                         | Returns outlet flow pattern as String: STRATIFIED, SLUG, ANNULAR, BUBBLE, SINGLE_PHASE |
| `getFlowPatternEnum()`                     | Returns `FlowPattern` enum                                                             |
| `getLiquidHoldup()`                        | Scalar outlet liquid holdup                                                            |
| `getFlowPatternProfile()`                  | `List<String>` pattern at each increment                                               |
| Same geometry methods as PipeHagedornBrown | —                                                                                      |

**Extends:** `Pipeline`
**Test:** `PipeMukherjeeAndBrillTest` (5 tests)

#### 5. MultiVariableAdjuster (`neqsim.process.equipment.util`)

Simultaneous multi-variable adjuster using damped successive substitution.
Solves N equations in N unknowns (target specifications) by adjusting N
process variables simultaneously.

| Method                                                                      | Description                  |
| --------------------------------------------------------------------------- | ---------------------------- |
| `addAdjustedVariable(ProcessEquipmentInterface, String, String)`            | Variable to manipulate       |
| `addTargetSpecification(ProcessEquipmentInterface, String, double, String)` | Target to satisfy            |
| `setVariableBounds(int, double, double)`                                    | Bounds on adjusted variable  |
| `setMaxIterations(int)` / `setTolerance(double)`                            | Convergence controls         |
| `isConverged()` / `getIterations()` / `getMaxResidual()`                    | Solution status              |
| `getNumberOfVariables()`                                                    | Number of adjusted variables |

**Test:** `MultiVariableAdjusterTest` (4 tests)

### Agents/Skills Affected

- `neqsim-api-patterns` skill — add rate-based absorber, SQP optimizer, flow correlation, multi-variable adjuster patterns
- `neqsim-capability-map` skill — update mass transfer, optimization, and multiphase flow sections
- `@solve-process` agent — can now use RateBasedAbsorber and MultiVariableAdjuster
- `@mechanical-design` agent — PipeHagedornBrown/PipeMukherjeeAndBrill for well tubing design

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

| Method                                      | Returns                           | Description                            |
| ------------------------------------------- | --------------------------------- | -------------------------------------- |
| `addCapacityConstraint(CapacityConstraint)` | `void`                            | Add a constraint to any equipment      |
| `getCapacityConstraints()`                  | `Map<String, CapacityConstraint>` | Get all constraints (unmodifiable)     |
| `getBottleneckConstraint()`                 | `CapacityConstraint`              | Most limiting enabled constraint       |
| `isCapacityExceeded()`                      | `boolean`                         | Any enabled constraint violated        |
| `isHardLimitExceeded()`                     | `boolean`                         | Any HARD constraint exceeded           |
| `getMaxUtilization()`                       | `double`                          | Highest utilization ratio (fraction)   |
| `getMaxUtilizationPercent()`                | `double`                          | Highest utilization as percentage      |
| `getAvailableMargin()`                      | `double`                          | Headroom on bottleneck (fraction)      |
| `getAvailableMarginPercent()`               | `double`                          | Headroom as percentage                 |
| `isNearCapacityLimit()`                     | `boolean`                         | Any constraint above warning threshold |
| `getUtilizationSummary()`                   | `Map<String, Double>`             | All constraint utilizations            |
| `getConstraintEvaluationReport()`           | `String`                          | Multi-line diagnostic report           |

### Updated ProcessSystem Methods

These methods now iterate over ALL equipment (not just `CapacityConstrainedEquipment`):

- `findBottleneck()` — returns `BottleneckResult` for the most-utilized equipment
- `isAnyEquipmentOverloaded()` — checks all equipment for capacity exceedance
- `isAnyHardLimitExceeded()` — checks all equipment for HARD limit violations
- `getCapacityUtilizationSummary()` — map of all equipment utilizations
- `getEquipmentNearCapacityLimit()` — list of equipment near their limits

### New Capacity Strategy Classes (6 new, 18 total)

| Class                             | Equipment Types                                      |
| --------------------------------- | ---------------------------------------------------- |
| `ReactorCapacityStrategy`         | GibbsReactor, PlugFlowReactor, StirredTankReactor    |
| `PowerGenerationCapacityStrategy` | GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem  |
| `SubseaEquipmentCapacityStrategy` | SubseaWell, SubseaTree                               |
| `FilterAdsorberCapacityStrategy`  | Filter, SulfurFilter, CharCoalFilter, SimpleAdsorber |
| `ElectrolyzerCapacityStrategy`    | Electrolyzer, CO2Electrolyzer                        |
| `WellFlowCapacityStrategy`        | WellFlow                                             |

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

| File                                           | Change                                         |
| ---------------------------------------------- | ---------------------------------------------- |
| `NonEquilibriumFluidBoundary.java`             | Prandtl fix, step clamping, df==0 guard        |
| `ReactiveKrishnaStandartFilmModel.java`        | Enhancement factor diagonal scaling            |
| `KrishnaStandartFilmModel.java`                | 3 NaN guards                                   |
| `TwoPhaseFixedStaggeredGridSolver.java`        | Phase params, sign fix, zero guards            |
| `InterphaseStratifiedFlow.java`                | Phase params for Re, friction, mass transfer   |
| `TwoPhaseFlowNode.java`                        | Hydraulic diameter, convergence, friction[1]   |
| `InterphaseDropletFlow.java`                   | Phase params for friction and Re               |
| `InterphaseSlugFlow.java`                      | Phase params for friction, heat, mass transfer |
| `InterphaseTransportCoefficientBaseClass.java` | Base class friction uses phase param           |
| `MultiPhaseFlowNode.java`                      | `interphaseFrictionFactor[1]` phase fix        |
| `InterphasePipeFlow.java`                      | Consistent Re and velocity phase usage         |
| `InterphaseStirredCellFlow.java`               | Phase params for heat and mass transfer        |

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

| File                                                        | Change                                                                                    |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `InterphaseDropletFlow.java`                                | **Rewritten** — Ranz-Marshall, Kronig-Brink, Abramzon-Sirignano                           |
| `InterphaseDropletFlowMassTransferTest.java`                | **NEW** — 9 tests covering correlations and limits                                        |
| `condensation_pipeline_equilibrium_vs_nonequilibrium.ipynb` | **NEW** — Example notebook comparing equilibrium vs non-equilibrium pipeline condensation |
| `docs/fluidmechanics/droplet_flow_correlations.md`          | **NEW** — Full documentation of dispersed flow correlations                               |

### New API Methods on `InterphaseDropletFlow`

| Method                                  | Description                          |
| --------------------------------------- | ------------------------------------ |
| `setUseAbramzonSirignano(boolean)`      | Enable/disable blowing correction    |
| `isUseAbramzonSirignano()`              | Query blowing correction state       |
| `setSpaldingMassTransferNumber(double)` | Set B_M for Abramzon-Sirignano       |
| `getSpaldingMassTransferNumber()`       | Get current B_M value                |
| `calcAbramzonSirignanoF(double bm)`     | Calculate F(B_M) correction function |

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

| File                                                                 | Change                                                |
| -------------------------------------------------------------------- | ----------------------------------------------------- |
| `neqsim-mcp-server/pom.xml`                                          | Added `local-dev` profile, SSE dependency             |
| `neqsim-mcp-server/src/main/resources/application.properties`        | Added HTTP/SSE/CORS config                            |
| `src/main/java/neqsim/mcp/runners/TaskWorkflowBridge.java`           | **NEW** — results.json bridge                         |
| `src/main/java/neqsim/mcp/runners/IndustrialProfile.java`            | Added `bridgeTaskWorkflow` to EXPERIMENTAL + ADVISORY |
| `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java` | Added `bridgeTaskWorkflow` tool method                |
| `src/test/java/neqsim/mcp/runners/BenchmarkValidationTest.java`      | **NEW** — 7 NIST benchmark tests                      |
| `src/test/java/neqsim/mcp/runners/IndustrialProfileTest.java`        | Updated tier size assertions (13→14 experimental)     |
| `neqsim-mcp-server/test_mcp_server.py`                               | Expanded from 19 to 48 tool E2E coverage              |

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

| Runner                    | Purpose                                                                 |
| ------------------------- | ----------------------------------------------------------------------- |
| `PVTRunner`               | PVT lab experiments (CME, CVD, DL, separator, swelling, GOR, viscosity) |
| `FlowAssuranceRunner`     | Hydrate, wax, asphaltene, corrosion, erosion, cooldown                  |
| `StandardsRunner`         | Gas/oil quality per 22 industry standards                               |
| `PipelineRunner`          | Multiphase pipeline flow (Beggs & Brill)                                |
| `ReservoirRunner`         | Material balance reservoir simulation                                   |
| `FieldDevelopmentRunner`  | NPV, IRR, cash flow, fiscal regimes, decline curves                     |
| `DynamicRunner`           | Transient simulation with auto-instrumented PID controllers             |
| `BioprocessRunner`        | Anaerobic digestion, fermentation, gasification, pyrolysis              |
| `CrossValidationRunner`   | Multi-EOS cross-validation                                              |
| `ParametricStudyRunner`   | Multi-variable parametric sweeps                                        |
| `SessionRunner`           | Persistent simulation sessions (create/modify/run/snapshot/restore)     |
| `TaskSolverRunner`        | Engineering task solving from high-level descriptions                   |
| `EngineeringValidator`    | Design rule validation against standards                                |
| `ReportRunner`            | Structured engineering report generation                                |
| `McpRunnerPlugin`         | Plugin interface for custom runners                                     |
| `PluginRegistry`          | Plugin lifecycle management                                             |
| `ProgressTracker`         | Long-running simulation progress tracking                               |
| `StreamingRunner`         | Async simulation with incremental polling                               |
| `VisualizationRunner`     | SVG/Mermaid/HTML visualization generation                               |
| `CompositionRunner`       | Multi-server MCP orchestration                                          |
| `SecurityRunner`          | API key management, rate limiting, audit logging                        |
| `StatePersistenceRunner`  | Simulation state save/load/compare across restarts                      |
| `ValidationProfileRunner` | Jurisdiction-specific validation (NCS, UKCS, GoM, Brazil, generic)      |
| `DataCatalogRunner`       | Database browsing (components, standards, materials, EOS models)        |

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

| Class                         | Package                            | Purpose                                                                                                                |
| ----------------------------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `FermentationReactor`         | `process.equipment.reactor`        | Monod/Contois/substrate-inhibited kinetics; batch, fed-batch, continuous modes. Extends `Fermenter`.                   |
| `SustainabilityMetrics`       | `process.util.fielddevelopment`    | CO₂eq tracking (IPCC AR6 GWP), carbon intensity (kgCO₂/MWh), EROI, renewable energy fraction, fossil fuel displacement |
| `BiogasToGridModule`          | `process.processmodel.biorefinery` | Pre-built: AnaerobicDigester → BiogasUpgrader → Compressor → Cooler → grid injection                                   |
| `GasificationSynthesisModule` | `process.processmodel.biorefinery` | Pre-built: BiomassGasifier → gas cleaning → Fischer-Tropsch synthesis                                                  |
| `WasteToEnergyCHPModule`      | `process.processmodel.biorefinery` | Pre-built: AnaerobicDigester → gas engine CHP with electrical + thermal output                                         |

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

| Class                      | Package                      | Tests    |
| -------------------------- | ---------------------------- | -------- |
| `BiomassCharacterization`  | `thermo.characterization`    | 12 tests |
| `AnaerobicDigester`        | `process.equipment.reactor`  | 10 tests |
| `BiomassGasifier`          | `process.equipment.reactor`  | 8 tests  |
| `PyrolysisReactor`         | `process.equipment.reactor`  | 8 tests  |
| `BiogasUpgrader`           | `process.equipment.splitter` | 10 tests |
| `BiorefineryCostEstimator` | `process.mechanicaldesign`   | 18 tests |

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

| UniSim flashtank     | NeqSim Type           |
| -------------------- | --------------------- |
| horizontal (default) | `Separator`           |
| vertical             | `GasScrubber`         |
| has WaterProduct     | `ThreePhaseSeparator` |

`GasScrubber` extends `Separator` — it is a vertical vessel with K-value
sizing constraints and 10% liquid level. The orientation is detected from
UniSim COM attributes (`Orientation`, `VesselOrientation`, `SeparatorOrientation`).

### Affected Files
- `devtools/unisim_reader.py` — `resolve_neqsim_type()` method, orientation extraction
- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim-reader.agent.md`
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

| Method                             | Temperature (°C) | UniSim Reference | Error    |
| ---------------------------------- | ---------------- | ---------------- | -------- |
| `ThrottlingValve` in ProcessSystem | 16.44            | 18.17            | -1.73°C  |
| Manual `PHflash(H/n)` on clone     | 33.05            | 18.17            | +14.88°C |

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

| Class                   | Issue                                                                               | Fix                                                                                                                                                   |
| ----------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
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

| UniSim flashtank     | NeqSim Type           |
| -------------------- | --------------------- |
| horizontal (default) | `Separator`           |
| vertical             | `GasScrubber`         |
| has WaterProduct     | `ThreePhaseSeparator` |

`GasScrubber` extends `Separator` — it is a vertical vessel with K-value
sizing constraints and 10% liquid level. The orientation is detected from
UniSim COM attributes (`Orientation`, `VesselOrientation`, `SeparatorOrientation`).

### Affected Files
- `devtools/unisim_reader.py` — `resolve_neqsim_type()` method, orientation extraction
- `.github/skills/neqsim-unisim-reader/SKILL.md`
- `.github/agents/unisim-reader.agent.md`
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

| Method                             | Temperature (°C) | UniSim Reference | Error    |
| ---------------------------------- | ---------------- | ---------------- | -------- |
| `ThrottlingValve` in ProcessSystem | 16.44            | 18.17            | -1.73°C  |
| Manual `PHflash(H/n)` on clone     | 33.05            | 18.17            | +14.88°C |

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

| Class                   | Issue                                                                               | Fix                                                                                                                                                   |
| ----------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
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

| Class                   | Package                                           | Description                                                                                                                                                                                                     |
| ----------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PinchAnalysis`         | `process.equipment.heatexchanger.heatintegration` | Linnhoff pinch analysis: composite curves, grand composite curve, minimum hot/cold utility targeting, pinch temperature. Accepts hot/cold `HeatStream` objects with MCp and temperature range.                  |
| `HeatStream`            | `process.equipment.heatexchanger.heatintegration` | Data model for hot/cold process streams. Auto-classifies HOT/COLD from supply vs target temperature. Celsius convenience API, Kelvin internal storage.                                                          |
| `SteamTurbine`          | `process.equipment.powergeneration`               | Isentropic steam expansion with configurable efficiency. PS/PH flash for outlet conditions. `getPower("kW")` API.                                                                                               |
| `HRSG`                  | `process.equipment.powergeneration`               | Heat Recovery Steam Generator. Takes hot gas exhaust, calculates steam production rate at specified pressure/temperature using approach temperature and effectiveness.                                          |
| `CombinedCycleSystem`   | `process.equipment.powergeneration`               | Integrates GasTurbine + HRSG + SteamTurbine. `getTotalPower("MW")`, `getOverallEfficiency()`, `toJson()`.                                                                                                       |
| `SimulationQualityGate` | `util.agentic`                                    | Automated QA gate for ProcessSystem validation: physical bounds (T > 0 K, P > 0), stream consistency (no NaN/Inf), composition normalization. Returns JSON report with issues, severity, and remediation hints. |

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

| Method                            | Default | Description                             |
| --------------------------------- | ------- | --------------------------------------- |
| `setMinIterations(int)`           | 100     | Min iterations before convergence check |
| `getMinIterations()`              | —       | Get current minimum iterations          |
| `setUseAdaptiveStepSize(boolean)` | false   | Enable adaptive step sizing             |
| `isUseAdaptiveStepSize()`         | —       | Check if adaptive step sizing is active |

### Deprecated Methods on `GibbsReactor`

| Method                                 | Notes                                  |
| -------------------------------------- | -------------------------------------- |
| `setUseConsistentOffDiagonal(boolean)` | No-op. RT correction is always active. |
| `isUseConsistentOffDiagonal()`         | Always returns `true`.                 |

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

**Agents/skills updated:** `unisim-reader.agent.md`, `neqsim-unisim-reader/SKILL.md`,
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

| Test                              | Methods                                                              |
| --------------------------------- | -------------------------------------------------------------------- |
| `PackedColumnTest`                | 4 tests: basic absorber, setters/getters, condenser/reboiler, JSON   |
| `ShortcutDistillationColumnTest`  | 3 tests: deethanizer, depropanizer, JSON                             |
| `ColumnInternalsDesignerTest`     | 4 tests: sieve tray, convenience, packed, structured                 |
| `PackingHydraulicsCalculatorTest` | 6 tests: Pall Ring, structured, diameter, presets, mass transfer, dP |
| `TrayHydraulicsCalculatorTest`    | 6 tests: sieve, diameter, valve, liquid rate, weeping, O'Connell     |
| `ProcessSystemStreamSummaryTest`  | 3 tests: text table, JSON, getAllStreams                             |
| `PVFflashTest`                    | 4 tests: mid-fraction, bubble point, dew point, consistency          |
| `AirCoolerTest`                   | 14 new tests: LMTD, U, fin efficiency, fan, bundle, ITD, JSON        |
| `ColumnSpecificationTest`         | Column spec purity/recovery/flow rate tests                          |

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

| Study Class | Count | Deliverables                                                       |
| ----------- | ----- | ------------------------------------------------------------------ |
| CLASS_A     | 7     | PFD, Thermal, Alarm/Trip, Spares, Fire, Noise, Instrument Schedule |
| CLASS_B     | 4     | PFD, Thermal, Fire, Instrument Schedule                            |
| CLASS_C     | 1     | PFD                                                                |

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
- `engineering-deliverables.agent.md` — Added instrument schedule deliverable section and code examples
- `field-development.agent.md` — Added item 17 (instrument schedule), updated StudyClass table and class map
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

| Type                | Description                                                     |
| ------------------- | --------------------------------------------------------------- |
| `STREAM_CONNECTED`  | Flow rate, T, composition from connected stream (default inlet) |
| `CONSTANT_FLOW`     | Fixed mass flow via `setInletMassFlow()`                        |
| `CONSTANT_PRESSURE` | Fixed pressure (default outlet, optional inlet)                 |
| `CLOSED`            | Zero velocity (blocked/shut-in) — pressure floats               |

### Common Configurations

| Config        | Inlet BC          | Outlet BC         | Inlet P  | Flow        |
| ------------- | ----------------- | ----------------- | -------- | ----------- |
| Default       | STREAM_CONNECTED  | CONSTANT_PRESSURE | Computed | From stream |
| Explicit flow | CONSTANT_FLOW     | CONSTANT_PRESSURE | Computed | Fixed       |
| Both P fixed  | CONSTANT_PRESSURE | CONSTANT_PRESSURE | Fixed    | Computed    |
| Shut-in       | STREAM_CONNECTED  | CLOSED            | Computed | From stream |
| Blowdown      | CLOSED            | CONSTANT_PRESSURE | Floats   | Zero        |
| Blocked pipe  | CLOSED            | CLOSED            | Floats   | Zero        |

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

| Test                       | TwoFluidPipe / BeggsAndBrill | Notes                                |
| -------------------------- | ---------------------------- | ------------------------------------ |
| Single-phase gas           | 0.98                         | Excellent agreement                  |
| Two-phase GLR 0.50–0.95    | 0.81–1.33                    | Within engineering accuracy          |
| Vertical riser             | 1.04 bar gravity dP          | Matches ρgH calculation              |
| Diameter scaling (6"/12")  | 33.7×                        | Close to theoretical ~32× (D⁻⁵)      |
| Transient holdup evolution | 0.19 → 0.09                  | Holdup decreases after flow increase |

### Migration

No breaking API changes. Existing code calling `run()` and `runTransient()` will
behave identically (steady-state) or more correctly (transient now evolves).

---

## 2026-03-24 — Field Development Agent and Skills

### New Agent

- **`@field-development`** (`.github/agents/field-development.agent.md`) — Expert agent for oil & gas field development workflows: concept selection, subsea tieback, production forecasting, and project economics (NPV/IRR). Orchestrates concept screening through final investment decision.

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
- `.github/agents/solve-task.agent.md` — Added `@field-development` to delegation table
- `docs/integration/ai_agents_reference.md` — Added agent entry, 4 skill entries, updated cross-reference tables
- `docs/integration/ai_agentic_programming_intro.md` — Updated count and added agent to catalog
- `docs/integration/ai_workflow_examples.md` — Added Example 8: Field Development Concept Selection
- `docs/fielddevelopment/README.md` — Added AI Agent & Skills section
- `docs/REFERENCE_MANUAL_INDEX.md` — Updated description

### Migration

No code changes needed. Use `@field-development` for field development tasks that were previously handled by `@solve-task`.

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
| Standard           | Equipment Types             | New Entries                                                                                      |
| ------------------ | --------------------------- | ------------------------------------------------------------------------------------------------ |
| API-660 9th Ed     | HeatExchanger               | 21 entries (design margins, velocity limits, hydro test, joint efficiency, vibration)            |
| API-661 7th Ed     | HeatExchanger/Cooler        | 9 entries (air cooler fins, face velocity, fan efficiency)                                       |
| API-662 1st Ed     | HeatExchanger               | 10 entries (plate HX gasketed/welded pressure/temp limits)                                       |
| NORSOK-P-002 Rev 5 | HeatExchanger/Cooler/Heater | 14 entries (duty/area/pressure margins, velocity limits)                                         |
| NORSOK-M-001 Rev 6 | HeatExchanger/Cooler/Heater | 7 entries (min/max design temp, hardness, H2S limits)                                            |
| ASME VIII Div.1    | HeatExchanger               | 19 entries (UG-27, UHX-13, UG-37, UG-99, allowable stresses, joint efficiencies, flange ratings) |
| ISO-16812          | HeatExchanger               | 12 entries (velocity, fouling resistance, baffle cut range)                                      |
| ISO-15547          | HeatExchanger               | 3 entries (plate-fin aluminium HX)                                                               |
| EN-13445           | HeatExchanger               | 3 entries (pressure, joint efficiency, corrosion allowance)                                      |
| PD-5500            | HeatExchanger               | 3 entries (pressure, joint efficiency, corrosion allowance)                                      |

### `ShellAndTubeDesignCalculator` Expanded
| New Capability                      | Standard                   | Method                                              |
| ----------------------------------- | -------------------------- | --------------------------------------------------- |
| Tubesheet thickness per UHX-13      | ASME VIII                  | `calculateTubesheetThicknessUHX()`                  |
| Nozzle reinforcement per UG-37      | ASME VIII                  | `calculateNozzleReinforcement()`                    |
| MAWP back-calculation per UG-27     | ASME VIII                  | `calculateMAWP()`                                   |
| Hydrostatic test pressure per UG-99 | ASME VIII                  | `calculateHydroTestPressure()`                      |
| Material property lookup from DB    | HeatExchangerTubeMaterials | `loadMaterialProperties()`                          |
| NACE MR0175 sour service assessment | NACE MR0175 / NORSOK M-001 | `performNACEAssessment()`                           |
| Shell/tube material grade tracking  | —                          | `setShellMaterialGrade()`, `setTubeMaterialGrade()` |

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
| Feature                                       | Standard                |
| --------------------------------------------- | ----------------------- |
| Casing wall thickness (UG-27 formula)         | ASME VIII Div. 1        |
| Material selection with SMYS/SMTS (9 grades)  | ASME II Part D          |
| Temperature derating of allowable stress      | ASME II Part D Table 1A |
| Nozzle load analysis (force/moment scaling)   | API 617 Table 3         |
| Flange rating verification with temp derating | ASME B16.5 / B16.47     |
| Hydrostatic test pressure                     | ASME VIII UG-99         |
| Corrosion allowance integration               | API 617                 |
| NACE MR0175 / ISO 15156 sour service check    | NACE MR0175             |
| Thermal growth & differential expansion       | API 617                 |
| Split-line bolt sizing (horizontally-split)   | API 617                 |
| Barrel casing outer/inner/end-cover sizing    | ASME VIII UG-34         |
| MAWP back-calculation                         | ASME VIII               |
| Automatic material recommendation             | —                       |

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
| File                                       | Content                                       |
| ------------------------------------------ | --------------------------------------------- |
| `designdata/CompressorCasingMaterials.csv` | 20 material grades with mechanical properties |
| `designdata/standards/api_standards.csv`   | +22 API-617 compressor entries                |
| `designdata/standards/asme_standards.csv`  | +18 ASME VIII / B16.5 compressor entries      |

### Agent Migration
- When writing compressor casing design code, use `CompressorCasingDesignCalculator`
  directly or via `comp.getMechanicalDesign().getCasingDesignCalculator()`.
- For sour service: set `design.setNaceCompliance(true)` and
  `design.setH2sPartialPressureKPa(value)` before calling `calcDesign()`.
- For automatic material selection: call `casingCalc.recommendMaterial()`.

---

## 2026-03-21 — Capability Scout Agent and Capability Map Skill

### New Agent
- **`@capability-scout`** — Analyzes engineering tasks, identifies required capabilities,
  checks NeqSim coverage, identifies gaps, writes NIPs, recommends skills and agent pipelines.
  Use before starting complex multi-discipline tasks.

### New Skill
| Skill                   | Purpose                                                                                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `neqsim-capability-map` | Structured inventory of all NeqSim capabilities by discipline (EOS, equipment, PVT, standards, mechanical design, flow assurance, safety, economics) |

### Updated Files
- `solve-task.agent.md` — Phase 1.5 Section 7b.3 now recommends invoking `@capability-scout` for comprehensive tasks
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
| Skill                         | Purpose                                                                               |
| ----------------------------- | ------------------------------------------------------------------------------------- |
| `neqsim-troubleshooting`      | Recovery strategies for convergence failures, zero values, phase issues               |
| `neqsim-input-validation`     | Pre-simulation input checks (T, P, composition, component names, order of operations) |
| `neqsim-regression-baselines` | Baseline management for preventing silent accuracy drift                              |
| `neqsim-agent-handoff`        | Structured schemas for agent-to-agent result passing                                  |
| `neqsim-physics-explanations` | Plain-language explanations of thermodynamic and process phenomena                    |
| `neqsim-performance-guide`    | Simulation time estimates and optimization strategies (in notebook-patterns skill)    |

### Updated Files
- `solve-task.agent.md` — Added auto-search past solutions (Phase 0, Step 1.5) and cross-discipline consistency gate
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
| API                              | Class                       | Description                                      |
| -------------------------------- | --------------------------- | ------------------------------------------------ |
| `getInletStreams()`              | `ProcessEquipmentInterface` | Returns list of inlet streams for any equipment  |
| `getOutletStreams()`             | `ProcessEquipmentInterface` | Returns list of outlet streams for any equipment |
| `addController(tag, ctrl)`       | `ProcessEquipmentBaseClass` | Attach named controller to equipment             |
| `getController(tag)`             | `ProcessEquipmentBaseClass` | Retrieve controller by tag name                  |
| `getControllers()`               | `ProcessEquipmentBaseClass` | Get all named controllers as map                 |
| `connect(src, dst, type, label)` | `ProcessSystem`             | Record typed connection metadata                 |
| `getConnections()`               | `ProcessSystem`             | Query all recorded connections                   |
| `getAllElements()`               | `ProcessSystem`             | Get all equipment, controllers, and measurements |

### New Classes
| Class                     | Package                | Purpose                                                    |
| ------------------------- | ---------------------- | ---------------------------------------------------------- |
| `ProcessElementInterface` | `process`              | Unified supertype for equipment, controllers, measurements |
| `MultiPortEquipment`      | `process.equipment`    | Abstract base for multi-inlet/outlet equipment             |
| `ProcessConnection`       | `process.processmodel` | Typed connection metadata (MATERIAL/ENERGY/SIGNAL)         |

### Migration
- **Backward compatible** — all existing code continues to work
- Legacy `setController()`/`getController()` still work alongside named controllers
- `getInletStreams()`/`getOutletStreams()` default to empty lists for classes that don't override

---

## 2026-03-09 — CO2 Corrosion Analyzer

### New Classes
| Class                  | Package                       | Purpose                                                            |
| ---------------------- | ----------------------------- | ------------------------------------------------------------------ |
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
| Wrong Name (Don't Use)     | Correct Name                     |
| -------------------------- | -------------------------------- |
| `getUnitOperation("name")` | `getUnit("name")`                |
| `characterise()`           | `characterisePlusFraction()`     |
| `characterize()`           | `characterisePlusFraction()`     |
| `Optional.isEmpty()`       | `!optional.isPresent()` (Java 8) |
