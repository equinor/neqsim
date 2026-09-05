---
name: neqsim-flow-assurance
description: "Flow assurance analysis patterns for NeqSim. USE WHEN: predicting hydrate formation, wax appearance, asphaltene stability, CO2/H2S corrosion (NORSOK M-506, de Waard-Milliams, FeCO3 film), mineral scale (saturation index, scale kinetics, brine mixing / seawater incompatibility), scale/solids valve plugging & Cv/opening drift (ValveScaleDrift), scale/deposit remediation & dissolver/solvent/wash selection for cleaning fouled equipment (ScaleRemediationAdvisor), elemental sulfur (S8) deposition from oxygen ingress / H2S oxidation at pressure or temperature letdown (compressor inlets, valves, dry-gas seals, letdown stations), per-segment pipeline corrosion+scale profiles, inspected metal-loss screening, pipeline hydraulics, DNV-RP-F109 on-bottom stability screening, DNV-RP-F105 free-span screening, DNV-RP-F104 CO2-envelope screening, DNV-RP-F110 global-buckling response screening, DNV-RP-F114 pipe-soil screening, water/liquid hammer screening, slug flow, thermal analysis, or chemical inhibitor dosing. Covers all flow assurance threats with NeqSim code patterns and industry standards."
last_verified: "2026-09-05"
---

# Flow Assurance Analysis with NeqSim

Consolidated guide for all flow assurance threats — hydrate, wax, asphaltene, corrosion,
hydraulics, water/liquid hammer screening, slugging, and thermal management — with
NeqSim code patterns.

## When to Use This Skill

- Hydrate formation temperature/pressure prediction
- Hydrate inhibitor dosing (MEG, methanol, ethanol)
- Wax appearance temperature (WAT) and wax deposition risk
- Asphaltene stability screening (de Boer, CII)
- CO2 and H2S corrosion rate estimation
- Elemental sulfur (S8) deposition risk at pressure/temperature letdown (compressor inlets, control/letdown valves, dry-gas seals, filters)
- Pipeline pressure drop and temperature profile
- DNV-RP-F109 vertical and lateral on-bottom stability screening
- Water hammer/liquid hammer screening for fast valve closure, pump trip, or check-valve slam
- Multiphase flow pattern prediction (slug, annular, stratified)
- Thermal insulation sizing for subsea pipelines
- Arrival temperature and cooldown calculations

## Applicable Standards

| Domain | Standards | Key Requirements |
|--------|-----------|-----------------|
| Pipeline design | DNV-ST-F101, DNV-RP-F104 for CO2, NORSOK L-001, ASME B31.4/B31.8 | Structural design plus composition-specific CO2 phase/hydraulic and lifecycle basis |
| Corrosion | NORSOK M-001, DNV-RP-F112, ISO 21457 | Material selection, CO2/H2S corrosion rates |
| On-bottom stability | DNV-RP-F109 | Vertical stability, absolute lateral stability, displacement acceptance |
| Free spans | DNV-RP-F105 | Free-span response and fatigue assessment |
| Global buckling and pipe-soil interaction | DNV-RP-F110, DNV-RP-F114 | Caller-controlled external response and demand-resistance screening |
| Subsea systems | NORSOK U-001 | Subsea production-system requirements |
| Hydrate management | DNV-RP-F116 | Hydrate prevention and remediation |
| GRP piping | ISO 14692 | Non-metallic pipe design |
| Pipeline integrity | DNV-RP-F101, DNV-RP-F116, API 1160 | Inspected metal-loss remaining strength and integrity management |

For fast acoustic transients, also load `neqsim-water-hammer`. Use
`WaterHammerStudy` or MCP `runWaterHammer` with STID route geometry, tagreader
event windows, and valve/pump event schedules; use this flow-assurance skill for
the broader operating-envelope and mitigation context.

For on-bottom stability, load `neqsim-subsea-and-wells` and use the typed
`DnvRpF109OnBottomStabilityKernel`. It provides a transparent absolute-static
screen and checks externally calculated response displacements. It does not
contain generalized design tables, produce dynamic response, qualify environmental
or soil models, or claim DNV conformity. A pass still requires independent review.

## 1. Hydrate Analysis

### EOS Selection for Hydrate Calculations

| Aqueous phase | NeqSim Class | Mixing Rule |
|---------------|-------------|-------------|
| Pure water / fresh water | `SystemSrkCPAstatoil` | `10` |
| Water + MEG / methanol / ethanol | `SystemSrkCPAstatoil` | `10` |
| **Salt brine / formation water (NaCl, CaCl2, ...)** | `SystemElectrolyteCPAstatoil` | `10` |

CPA is required for water–hydrocarbon hydrate modeling. **When dissolved salts /
electrolytes are present, use `SystemElectrolyteCPAstatoil`** so the salt
thermodynamic (hydrate-suppression) effect is captured — plain
`SystemSrkCPAstatoil` ignores ion activity and underestimates subcooling margin.

### Hydrate Formation Temperature

```java
// CPA EOS required for accurate water-hydrocarbon modeling
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 10, 100.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.10);
fluid.setMixingRule(10);  // CPA mixing rule
fluid.setMultiPhaseCheck(true);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateFormationTemperature();
double hydrateT_C = fluid.getTemperature() - 273.15;
```

### Hydrate Equilibrium Curve (Multiple Pressures)

```java
// Calculate hydrate T at several pressures for the full curve
double[] pressures = {20, 40, 60, 80, 100, 120, 150, 200};
double[] hydrateTemps = new double[pressures.length];

for (int i = 0; i < pressures.length; i++) {
    SystemInterface testFluid = fluid.clone();
    testFluid.setPressure(pressures[i]);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testFluid);
    testOps.hydrateFormationTemperature();
    hydrateTemps[i] = testFluid.getTemperature() - 273.15;
}
```

### Hydrate Inhibitor Dosing (MEG)

```java
// Add MEG to suppress hydrate formation temperature
SystemInterface inhibitedFluid = new SystemSrkCPAstatoil(273.15 + 4, 100.0);
inhibitedFluid.addComponent("methane", 0.80);
inhibitedFluid.addComponent("water", 0.15);
inhibitedFluid.addComponent("MEG", 0.05);  // 25 wt% MEG in water phase
inhibitedFluid.setMixingRule(10);
inhibitedFluid.setMultiPhaseCheck(true);
inhibitedFluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(inhibitedFluid);
ops.hydrateFormationTemperature();
double inhibitedHydrateT = inhibitedFluid.getTemperature() - 273.15;
// Compare with uninhibited to get subcooling margin
```

### Salt-Inhibited Hydrate (Formation Water / Brine)

```java
// Dissolved salts depress the hydrate temperature — use the electrolyte CPA model
SystemInterface brineGas = new SystemElectrolyteCPAstatoil(273.15 + 4, 100.0);
brineGas.addComponent("methane", 0.80);
brineGas.addComponent("water", 0.18);
brineGas.addComponent("Na+", 0.01);   // dissociated NaCl
brineGas.addComponent("Cl-", 0.01);
brineGas.setMixingRule(10);
brineGas.setMultiPhaseCheck(true);
brineGas.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(brineGas);
ops.hydrateFormationTemperature();
double brineHydrateT = brineGas.getTemperature() - 273.15;
// SystemSrkCPAstatoil would miss the salt suppression — always use electrolyte CPA with ions
```

### MEG Concentration Sweep

```java
// Find required MEG concentration for target subcooling
double[] megWtPct = {0, 10, 20, 30, 40, 50};
for (double wt : megWtPct) {
    // Create fluid with appropriate MEG/water ratio
    double waterFrac = 0.20 * (1.0 - wt / 100.0);
    double megFrac = 0.20 * (wt / 100.0);
    SystemInterface testFluid = new SystemSrkCPAstatoil(273.15, 100.0);
    testFluid.addComponent("methane", 0.80);
    testFluid.addComponent("water", waterFrac);
    testFluid.addComponent("MEG", megFrac);
    testFluid.setMixingRule(10);
    testFluid.setHydrateCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testFluid);
    testOps.hydrateFormationTemperature();
    // Record hydrate T vs MEG concentration
}
```

## 2. Wax Analysis

### Wax Appearance Temperature (WAT)

```java
// Oil system with C7+ fractions for wax prediction
SystemInterface oil = new SystemSrkEos(273.15 + 60, 50.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.10);
oil.addTBPfraction("C7", 0.10, 92.0 / 1000, 0.727);
oil.addTBPfraction("C10", 0.15, 134.0 / 1000, 0.78);
oil.addTBPfraction("C15", 0.15, 206.0 / 1000, 0.83);
oil.addPlusFraction("C20", 0.20, 350.0 / 1000, 0.88);
oil.getCharacterization().setWaxModel(true);
oil.getCharacterization().characterisePlusFraction();
oil.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(oil);
ops.calcWAT();
double wat_C = oil.getTemperature() - 273.15;
```

### Wax Fraction vs Temperature (PVT Simulation)

```java
import neqsim.pvtsimulation.simulation.WaxFractionSim;

WaxFractionSim waxSim = new WaxFractionSim(oil);
waxSim.setTemperatures(new double[]{333.15, 313.15, 293.15, 273.15});
waxSim.run();
double[] waxFractions = waxSim.getWaxFraction();
```

## 3. Asphaltene Stability

### de Boer Screening

```java
// Assess asphaltene precipitation risk
// Key parameters: reservoir pressure, bubble point, density difference
// Risk increases when operating pressure approaches bubble point
// High-risk zone: ΔP > 200 bar above bubble point for light oils

// Use CPA for asphaltene modeling
SystemInterface aspFluid = new SystemSrkCPAstatoil(273.15 + 90, 300.0);
// Add components including heavy asphaltenic fractions
aspFluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(aspFluid);
ops.TPflash();
aspFluid.initProperties();

// Check if asphaltene phase is stable
// Compare upper/lower asphaltene onset pressures vs operating P
```

## 4. Pipeline Hydraulics

### Simple Adiabatic Pipe

```java
AdiabaticPipe pipe = new AdiabaticPipe("Export Pipeline", feedStream);
pipe.setLength(50000.0);       // 50 km in meters
pipe.setDiameter(0.508);       // 20 inch in meters
pipe.setInletElevation(0.0);
pipe.setOutletElevation(-350.0);  // negative = downhill (subsea)
pipe.run();

double outletP = pipe.getOutletStream().getPressure();  // bara
double outletT = pipe.getOutletStream().getTemperature() - 273.15;  // C
double dP = feedStream.getPressure() - outletP;  // pressure drop
```

### DNV-RP-F105 free-span routing

When a hydraulics or environment study feeds an explicit current `DNV-RP-F105 2025-12` free-span
screen, route verified structural and environmental inputs through
`DnvRpF105FreeSpanScreeningKernel`. Keep steel and hydrodynamic diameters distinct and use velocities
normal to the span. Effective modal mass, axial force, span geometry, and response-trigger basis are
external structural inputs, not quantities inferred silently from a hydraulic pipe object.

The kernel is a simply supported first-mode/dimensionless escalation screen. Its Strouhal number,
frequency-ratio band, and reduced-velocity triggers are project-controlled and cannot be called DNV
limits. Keep soil/shoulder and multi-span response, VIV amplitudes, direct wave loading, ULS/FLS,
fatigue, monitoring, and intervention external. Never relabel
`PipeMechanicalDesignCalculator.calculateAllowableSpanLength(...)` as F105 evidence.

### DNV-RP-F101 inspected metal-loss routing

When inspection data feeds an explicit current `DNV-RP-F101 2019-09+AMD:2025-09` screen, route one
verified isolated longitudinal metal-loss defect under internal pressure through
`DnvRpF101CorrodedPipelineScreeningKernel`. Require the measured depth and axial length,
assessment wall-thickness definition, inspection/growth allowance, characteristic ultimate
tensile strength, internal/external absolute pressures, and project-controlled pressure factor.

Do not infer defect geometry from hydraulic corrosion-rate calculations or projected uniform wall
loss. The typed kernel does not handle defect interaction or complex profiles, longitudinal
compression, probabilistic assessment, crack-like damage, repair, or fitness-for-service approval.
It also does not replace DNV-ST-F101 original-design checks.

### DNV-RP-F104 CO2 pipeline routing

When an actual-composition phase-envelope and hydraulic/thermal study feeds a current
`DNV-RP-F104 2021-02+AMD:2021-09` screen, route the bounded project composition, CO2/water limits,
ordered profile, absolute MAOP, design temperatures, and a separately verified minimum single-phase
pressure boundary at each point through `DnvRpF104Co2PipelineEnvelopeScreeningKernel`.

The external thermodynamic basis must establish that pressure above each boundary represents the
intended single-phase region for the specific composition, temperature, path, EOS, and uncertainty.
Do not substitute pure-CO2 critical conditions or `CO2FlowCorrections.isDensePhase(...)`. Treat
composition, phase-boundary, MAOP, and temperature margins as screening findings. Keep transient
cases, F104 decompression/fracture and crack arrest, materials/corrosion, release consequences,
construction, operation, requalification, and all DNV-ST-F101 structural checks external.

### DNV-RP-F114 pipe-soil interaction routing

When route, hydraulic/thermal, or installation work feeds a current `DNV-RP-F114 2021-05` screen,
route named design situations with externally verified vertical, axial, and lateral action and
resistance magnitudes through `DnvRpF114PipeSoilInteractionScreeningKernel`. Treat margin and
utilization outputs as caller-controlled screening findings.

Do not convert burial depth, soil thermal resistance, or a generic friction factor into
geotechnical resistance. Keep site investigation, soil interpretation, penetration/burial and
load-displacement response, time/cyclic effects, characteristic values, uncertainty, structural
actions, and F109/F110/F105/ST-F101 acceptance external.

### DNV-RP-F110 global-buckling response routing

When hydraulic/thermal, route, or installation work feeds a current
`DNV-RP-F110 2019-09+AMD:2021-09` screen, route named external structural-analysis cases through
`DnvRpF110GlobalBucklingResponseScreeningKernel`. Supply effective force, peak longitudinal strain,
peak global displacement, and required feed-in length with caller-controlled allowable or available
values. Treat margins and utilizations as screening findings.

Require external evidence for the effective-force derivation, pipe/as-laid geometry, pipe-soil
response, imperfections/triggers/strategy, global structural model, load combinations, local
capacity and strain criteria, uncertainty/sensitivity/buckle sharing, and lifecycle actions. Never
derive critical buckling, initiation/prevention criteria, structural response, or soil springs from
NeqSim hydraulic or thermal output. Keep F109/F114/F105 and all ST-F101 acceptance external.
### Beggs and Brill Multiphase Correlation

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Subsea Flowline", feedStream);
pipe.setPipeWallRoughness(5e-5);  // meters
pipe.setLength(50000.0);          // meters
pipe.setAngle(0.0);               // horizontal
pipe.setDiameter(0.254);          // 10 inch

// For subsea with heat loss
pipe.setOuterTemperature(277.15);  // 4°C seawater
pipe.run();

// Get flow regime, liquid holdup, pressure profile
double outP = pipe.getOutletStream().getPressure();
double outT = pipe.getOutletStream().getTemperature() - 273.15;
```

### Liquid Holdup, Flow Regime & Liquid-Loading (gravity-dominated screening)

Verified reader methods on `PipeBeggsAndBrills` (after `run()`):

```java
String regime = pipe.getFlowRegime();          // SEGREGATED / TRANSITION / INTERMITTENT / DISTRIBUTED
double dP     = pipe.getPressureDrop();         // bar (inlet - outlet)
double vmix   = pipe.getMixtureVelocity();      // m/s
double[] holdupProfile = pipe.getLiquidHoldupProfile();   // fraction per segment (0-1)
// per-segment access (0 .. numberOfIncrements-1):
Double hSeg   = pipe.getSegmentLiquidHoldup(i);
Double vsgSeg = pipe.getSegmentGasSuperficialVelocity(i);
Double vslSeg = pipe.getSegmentLiquidSuperficialVelocity(i);
Double elevSeg = pipe.getSegmentElevation(i);
// average holdup = mean(holdupProfile); "liquid content" for the line
// liquid inventory (m3) = sum_i holdup_i * (pi/4*D^2) * segmentLength
```

**Liquid-loading / gravity-dominated screening** (PEPR-style "is the line filling
with liquid?"): sweep **gas rate** (and water cut) and read average holdup +
regime. As gas rate falls, holdup rises and the regime moves
`INTERMITTENT -> TRANSITION -> SEGREGATED (stratified)` = gravity-dominated /
liquid loading. Higher water cut lifts holdup at every rate and pushes toward
INTERMITTENT (slugging). Define liquid-loading onset as the gas rate where
average holdup crosses a threshold (e.g. 25%).

```java
for (double qgMSm3d : gasRates) {
  SystemInterface feed = fluidTemplate.clone();      // gas-condensate + water (CPA, rule 10)
  Stream s = new Stream("feed", feed);
  s.setFlowRate(qgMSm3d, "MSm3/day");                // wellstream gas standard volume
  s.setTemperature(50.0, "C"); s.setPressure(150.0, "bara");
  s.run();
  PipeBeggsAndBrills p = new PipeBeggsAndBrills("line", s);
  p.setLength(21000.0); p.setDiameter(0.254); p.setAngle(0.0);
  p.setPipeWallRoughness(5e-5); p.setNumberOfIncrements(40);
  try {
    p.run();
    double[] h = p.getLiquidHoldupProfile();
    // record mean(h), p.getFlowRegime(), p.getMixtureVelocity()
  } catch (RuntimeException e) {
    // "Outlet pressure is negative" = DELIVERABILITY LIMIT, not a bug (see gotcha)
  }
}
```

> **GOTCHA — deliverability limit vs bug.** `PipeBeggsAndBrills` uses a **fixed
> inlet pressure**. If frictional ΔP over a long/small line exceeds the inlet
> pressure, `run()` throws `InvalidOutputException: ... Outlet pressure is
> negative`. That is a **genuine deliverability limit** (the line cannot pass
> that rate at that inlet P), not a solver failure — catch it and report the
> max deliverable rate. To model to a fixed **arrival** (outlet) pressure
> instead, raise the inlet pressure until the delivered rate matches, or iterate
> inlet P per rate.

> **GOTCHA — `getFlowRegime()` naming.** Beggs & Brill regimes are returned as
> `SEGREGATED` (stratified/annular — gravity-dominated), `TRANSITION`,
> `INTERMITTENT` (plug/slug), `DISTRIBUTED` (bubble/mist). "Gravity-dominated /
> liquid loading" = SEGREGATED (+ low-velocity TRANSITION).

> **GOTCHA — profile index vs `getFlowRegime()`.** `run()` evaluates the
> correlation once per increment **and then once more at the outlet state**, so
> every correlation profile (`getLiquidHoldupProfile()`,
> `getFlowRegimeProfileList()`, …) has `numberOfIncrements + 1` entries and
> index *i* belongs to `getPressureProfile()[i]`. `getFlowRegime()` returns the
> **outlet** state. Reading `getFlowRegime()` next to
> `getSegmentLiquidHoldup(0)` compares two different states and can look like a
> discontinuity. Always pair `getSegmentFlowRegime(i)` with
> `getSegmentLiquidHoldup(i)`.

> **VALIDITY — low liquid loading.** On a long wet-gas export line (ID 0.355 m,
> no-slip liquid fraction λ_L ≈ 0.006), Beggs & Brill over-predicts ΔP well above
> a single-phase Darcy-Weisbach integration of the same line, and the gap is
> entirely the two-phase friction multiplier `exp(S)`: removing it brings ΔP
> within 4% of that analytic integration. `S` is monotonically *increasing*
> in `y = λ_L / H_L²`, so *less* liquid gives a *larger* multiplier — the
> opposite of the physical trend — up to a bounded maximum of `exp(S) = 3.19` at
> `y = 52.1`. B&B is calibrated for λ_L down to roughly 0.01–0.02; below that,
> use `TwoFluidPipe` or a mechanistic simulator and treat B&B as a conservative
> upper bound. The published map also has a genuine step where the segregated and
> distributed correlations meet at `L1` for λ_L < 0.01 (no transition band
> exists there): hold-up ×0.70 and ΔP ×1.12 across a 1 bara change. That step is
> in the correlation — do not smooth it.

> **Fixed defects worth knowing (all affected the *inclination* correction).**
> Older NeqSim builds silently returned the horizontal hold-up on an inclined leg
> when (a) the pipe angle was converted degrees→radians twice, (b) the
> Baker-Swerdloff surface tension went negative above 274 bara or for a very
> light liquid, making the liquid velocity number NaN, or (c) the flow regime was
> `TRANSITION`, which had no inclination branch at all. All three are fixed and
> locked by `PipeBeggsAndBrillsCorrelationTest`. If you are on an older build,
> sanity-check that uphill hold-up clearly exceeds horizontal hold-up before
> trusting an inclined result.

> **Phase-count code paths.** `PipeBeggsAndBrills` assumes phase 0 is the gas
> whenever the stream has more than one phase. A **gas-free** stream that splits
> into oil and water (dead oil with free water at high pressure) used to be
> modelled as gas–liquid, with the oil phase acting as the gas: fictitious flow
> regime, hold-up 0.21 instead of 1, and ΔP 44% above the homogeneous liquid
> value. It is now carried as a homogeneous liquid with a volume-weighted density
> and viscosity. The three-phase liquid density is also now combined on **volume**
> fractions (total mass over total volume) rather than mass fractions. Locked by
> `PipeBeggsAndBrillsPhasePathsTest`.

> **Where the error sits.** On a **single-phase** gas line Beggs & Brill matches a
> Darcy-Weisbach integration to a few tenths of a per cent on pressure drop and to
> ~0.15 K on arrival temperature — the friction and energy paths are sound. The
> over-prediction seen on the *two-phase* version of the same line is therefore
> entirely the two-phase friction multiplier, not the solver.

> **Comparisons against commercial transient multiphase simulators are not
> published in this repository.** Their licence terms generally prohibit
> publishing benchmark results and prohibit using the software to develop
> competing software, so no NeqSim closure is tuned to such a tool and no measured
> deviation against one is recorded here. Validate against experimental,
> laboratory or field data, or against an analytic/first-principles check.

> **`TwoFluidPipe` steady-state usage.** The refinement loop needs a few hundred
> sweeps to settle the pressure profile against the updated section densities
> (74 km at 160 sections ≈ 25 s, at 320 sections ≈ 60 s). Always
> `assert pipe.isSteadyStateConverged()` **and** check
> `pipe.getSteadyStateIterationsUsed() > 1` — a result reported after one sweep
> still carries the densities the sections were initialised with and understates
> the pressure drop of a gas line by roughly ten per cent. Around 160 sections
> (≈450 m) is the sweet spot on a long transmission line: it converges reliably
> and is grid-converged to 0.4% against 320 sections. Raise
> `setSteadyStateMaxWallClockTime(...)` (default 300 s) before blaming the model
> if `isSteadyStateWallClockLimited()` is true.

> **MCP `runPipeline` solver and response handoff.** Omit `solver` (or use
> `beggsBrill`) for the established correlation path. Use `solver: "twoFluid"`
> when the task needs finite-volume pressure, temperature, holdup, phase-velocity,
> flow-regime, inventory, erosion-margin, hydrate/wax, or slug profiles. Pass
> `sectionLengths_m`, `elevationProfile_m`, `heatTransferProfile_W_m2K`, and
> `surfaceTemperatureProfile_C` or `_K` as equal-length per-section arrays; the
> lengths must sum to `length_m`. Bound expensive solves with
> `steadyStateMaxWallClockTime_s`. Request `detailLevel: FULL` for spatial
> profiles, `SUMMARY` for engineering KPIs without profiles, or `MINIMUM` for
> the compact core result. The authoritative object is `TwoFluidPipeResponse`
> (a `BaseResponse`): preserve it through MCP/report handoffs instead of
> independently reconstructing fields from the equipment. Always propagate its
> convergence/pressure-floor/wall-clock validation findings and apply the
> limitations below before quoting a result.

> **Three defects found by auditing the solve against its own governing equations
> — all fixed, but the symptoms are generic and worth recognising elsewhere.**
> (1) *A time integrator inside a fixed-point sweep.* The steady solve integrated
> `LiquidAccumulationTracker` once per iteration with a nominal `dt`; that tracker
> only ever adds liquid, ratchets its volume up to what the sections already hold,
> then adds it back on top of that holdup, so it has no fixed point. Valley
> sections climbed to the 0.85/0.95 cap and the profile never settled.
> (2) *A correlation overriding a solved momentum balance.* The minimum-slip
> constraint applied the Beggs and Brill horizontal holdup correlation as a lower
> bound in every regime. It is fitted to 1–1.5 inch air-water loops at
> near-atmospheric pressure with λ_L ≥ 0.01; the line runs λ_L ≈ 0.008
> in a 14-inch pipe at 200 bara, and the floor was binding in EVERY section — so
> the reported holdup was the correlation, not the solved momentum balance, worth
> ≈+20% on ΔP through the mixture density. Only the scale-free
> `lambdaL * minimumSlipFactor` bound remains.
> (3) *Convergence declared without re-evaluating thermodynamics.* The flash runs
> every `ssFlashInterval` sweeps but the "flash moved nothing" flag started false,
> so a non-flash sweep read as settled. The solve could exit after ONE sweep on
> densities it had never revisited.
> If you see a steady profile with a section sitting exactly on a cap, or
> `getSteadyStateIterationsUsed()` returning 1 on a long line, suspect these.

> **Behaviour after those fixes**, on that line at default settings: the rate
> exponent in ΔP ~ rate^n rises from about 2.1 to about 3.1 across a 3x rate
> range, i.e. the density feedback along the line is reproduced, not just the
> level at one rate; 10 MW of DEH raises arrival T by ~17 K and ΔP by ~15%, so the
> energy equation feeds the momentum balance. Grid-converged (160 vs 320 sections
> within 0.4%). Terrain response is solved, not tuned: the annular film balance
> carries `tau_i = tau_wL + rhoL*g*sin(theta)*delta`, so holdup responds to
> inclination and scales with `sin(theta)`. Still open: **the three-phase
> free-water case does not converge** — 15 m3/hr of free water on the same line is
> wall-clock limited after 4078 iterations at a 1200 s budget. ΔP is identical
> between a 300 s and a 1200 s budget, so the profile is stationary and the
> criterion is stalling on the three-phase liquid split — but
> `isSteadyStateConverged()` is false, so the number must not be quoted. ALWAYS
> check it on a water-bearing line.

> **`TwoFluidPipe` also fails silently when a line has no deliverability.** The
> marching solver clamps section pressure at a 1 bara floor; that clamp is a
> fixed point of itself, so the per-section change falls below tolerance and the
> sweep would report success on a case with no physical solution. Check
> `pipe.isSteadyStatePressureFloorLimited()` — when it is true,
> `isSteadyStateConverged()` is withheld and the profile must be discarded, not
> reported. `PipeBeggsAndBrills` throws `Outlet pressure is negative` on the same
> condition.

> **`TwoFluidPipe` holdup at low rate is dominated by terrain trap sections.**
> The MAXIMUM holdup at 4 and 7 MSm3/d sits well above the line mean (a single
> valley section), so do not
> quote local `TwoFluidPipe` holdup or valley inventory as a design number. The
> three-phase bookkeeping is sound — gas/oil/water fractions sum to one and stay in
> range at every node.

> **The minimum-slip bound applies only level and uphill.** `alphaL >= lambdaL *
> minimumSlipFactor` (default 2.0) states that the gas outruns the liquid, which is a
> property of gas-driven transport. On a downhill section gravity moves the liquid and
> the slip ratio legitimately falls, so the bound is not applied there. It used to be
> applied everywhere and was binding on 39 of 42 downhill sections of an undulating
> fixture, replacing the momentum balance with a constant.

> **The horizontal annular criterion is the Taitel-Dukler equilibrium level.**
> `pipe.setUseEquilibriumLevelAnnularTransition(false)` restores the earlier path, the
> vertical droplet-entrainment threshold `U_SG > 3.1*(sigma*g*drho/rhoG^2)^0.25`, which is
> about 0.75 m/s on a 14-inch export line and so classified essentially any horizontal gas
> pipeline as annular, solving a shallow stratified layer with a thin-film closure. The two
> agree wherever the Kelvin-Helmholtz margin exceeds one — identical at 10 MSm3/d on the
> export line — and differ at 4 MSm3/d, where the equilibrium branch reclassifies 272 of 320
> sections as stratified-wavy.

> **Transient closure sources follow continuous regime weights.** During dynamic
> evaluation, the existing dimensionless normalized weights from
> `FlowRegimeDetector.classify(...)` blend wall friction, interfacial friction and area,
> and entrainment. A non-zero stratified weight keeps the matching segment geometry active.
> The flow map, transition bands, hold-up closure, and regime-specific formulas are unchanged,
> and pure-regime endpoints recover their original closures. This is experimental numerical
> continuation, not severe-slugging or liquid-rich qualification; require the public Tengesdal,
> 1,800 s inventory, conservation, nearby-point, refinement, and solver-diagnostic gates.
>
> **Friction is per-phase wall shear in stratified flow.**
> `setSeparatedFrictionModel(false)` restores the mixture correlation, which charges the whole
> perimeter with a holdup-weighted density; on a stratified line at 41% holdup that
> over-predicts ΔP by ~2.3x, and because it scales as `G^2/rho_mix` it makes extra liquid
> REDUCE the gradient, inverting the terrain response. The separated form is scoped to
> stratified flow because its perimeters come from a circular-segment layer; annular flow,
> whose film wets the whole perimeter, is not that geometry.

> **Measured accuracy on the 73.8 km reference line**, across a threefold rate range:
> ΔP +1.4 / +1.6 / +0.1 / −2.7 % and maximum holdup −2.4 / −7.1 / −6.6 / +3.5 % at
> 4 / 7 / 10 / 12 MSm3/d, all converged and grid-converged. The earlier defaults gave
> ΔP +5.7 / +5.6 / +1.4 / −0.0 % and holdup −25.5 / −18.6 / −6.2 / +3.3 %.

> **Direct electrical heating (DEH)** is available on both models with the same
> convention — the power set is what reaches the fluid, so cable and coating
> losses must already be deducted:
> `pipe.setDirectElectricalHeatingPower(watts)` or
> `setDirectElectricalHeatingPowerPerMeter(wattsPerMetre)`. In `TwoFluidPipe`
> steady state each segment decays toward the balance temperature
> `T_surface + q/(U·π·D)` (exact for a uniform source, cannot overshoot); it also
> works in transient and with wall heat transfer switched off. A tool with no
> distributed-heating input can represent the same source through the identity
> `−UπD(T−T_surf) + q ≡ −UπD(T − [T_surf + q/(UπD)])`, i.e. by raising the ambient
> temperature by `q/(UπD)`.

> **`TwoFluidPipe` liquid-rich and severe-slugging transients remain
> unqualified.** The legacy route can still develop phase backflow and clamp the
> outlet flux at zero while its finite-volume balance closes exactly. Gas-dominated
> null cases remain usable, but do not promote a liquid-rich or severe-slugging
> trajectory from either a small residual or a completed time loop alone. Use the
> analytical `SevereSluggingBenchmarkHarnessTest` screen and the public Tengesdal
> evidence until the dynamic qualification gates below pass.
>
> **The coupled route is an opt-in four-part configuration.** For a pressure
> outlet that physically permits phase fallback, use
> `setEnableInterfacialPressure(true)`,
> `setImplicitInterfacialPressureCoupling(true)`,
> `setEnableCoupledPressureMomentum(true)`, and
> `setAllowOutletPhaseBackflow(true)` together. The nonlinear controls are public:
> `setCoupledPressureMomentumMaximumIterations(int)` and
> `setCoupledPressureMomentumRelativeVolumeTolerance(double)`, with defaults 24
> and `1e-7`.
>
> **WS3 restores progress, not physical parity.** The 16-section Tengesdal Test 3
> probe now completes 50/50 calls of 0.1 s; a 24-section refinement completes
> 100/100 calls of 0.05 s. Neither rejects a nonlinear substep, and gas, oil,
> water, liquid, and total mass residuals are below `1e-9`. The former
> 12-iteration cap stopped around `6e-7`, above the `1e-7` gate. The sticky pressure
> limiter still fires, however, and the liquid outlet spans -18.55 to 6.88 kg/s
> versus the stored 0.375 to 4.03 kg/s comparison. This is a disclosed boundary/
> pressure-coupling gap, not a reason to tune a public closure to a commercial
> trace. Subsequent validation must use the public Tengesdal experiment, nearby
> operating points, conservation, and mesh/time-step refinement.
>
> **What did help: fixing the regime.** The same case classified SLUG rather than
> ANNULAR (PR #3086, Barnea bridging limit) cuts the 30-minute inventory runaway
> from **+56.3% to +20.1%** at default settings. Still unusable, still gated by
> the flag, but the regime branch is a bigger lever on the runaway than the
> interfacial-pressure term is.
>
> **The transient tells you when it has failed — always check every diagnostic.**
> Read `isTransientOutletBackflowClamped()`,
> `isTransientCoupledPressureMomentumFailureDetected()`,
> `isTransientCoupledPressureMomentumCorrectionLimited()`, and
> `getTransientCoupledPressureMomentumRejectedSubsteps()` after the full window.
> `isCoupledPressureMomentumPressureCorrectionLimited()` is the non-sticky view
> of only the latest correction.
> The flags/counter are sticky and reset on the next steady `run()`. A coupled
> call that cannot complete its requested interval now throws with accepted time,
> requested time, residual/tolerance, iterations/cap, and limiter state; never
> catch it and advance the engineering timeline. The mass-balance report alone
> cannot qualify the result because a clamped or limited route can still conserve
> its own discrete fluxes exactly.
>
> **Three-phase (gas/oil/water) steady state is fixed.** The oil/water slip
> ratio uses `S = 1 + 1.75·max(0, 1 − (Fr/3)²)`, a stratified plateau that rolls
> off to no slip once the liquid disperses above a liquid Froude number of about
> 3; the previous form cut off at Fr = 2 and under-predicted water holdup badly.
> One gap remains open: the pressure drop is over-predicted in this liquid-rich
> regime, far more than on a gas-dominated line.
>
> **Exporting NeqSim to OLGA — the fluid basis is two files, not one.** The
> `.tab` PVT table fixes the phase behaviour; the **hydrate boundary is separate**
> and OLGA does not compute it. Without a `HYDRATECURVE` in the case, OLGA falls
> back to the Hammerschmidt correlation, so a study whose NeqSim half uses CPA
> hydrate equilibrium and whose OLGA half uses Hammerschmidt disagrees about where
> hydrates form, invisibly. Export both from the same fluid:
> `OLGAhydrateCurveGenerator` writes the `HYDRATECURVE LABEL=..., PRESSURE=(...)
> bara, TEMPERATURE=(...) C` block and returns the matching
> `HYDRATECHECK HYDRATECURVE="..."` line for the flowpath. OLGA interpolates that
> curve **linearly**, so span the pressures the case actually visits and use ≥20
> points when the range reaches below ~50 bara (4 points over 10–200 bara costs
> 4.1 K of hydrate temperature; 20 points costs 0.48 K). The OLGA output variable
> is `DTHYD` in °C, and it is `T_hydrate − T_fluid` — **positive means inside the
> hydrate region**, negative is the safe margin, which is the opposite of the
> intuitive reading. Full OLGA-side workflow in the community
> `neqsim-olga-multiphase-simulator` skill.
>
> **Never build a volumetric phase fraction from `phase.getVolume()`.** With a
> Peneloux volume shift active it disagrees with `getDensity()` by the shift —
> +16.6% for oil and +31.7% for water on a typical SRK three-phase system, while
> gas matches to 0.25%. Use `getFlowRate("kg/sec")/getDensity("kg/m3")`.

> advection-relaxation transport lag, not a conservation-law solver, and the
> Beggs & Brill correlation is not even used on that path (friction reverts to
> single-phase Darcy-Weisbach, viscosity is frozen at the inlet). It **does not
> store mass**: on a rate step the outlet mass flow equals the inlet at every
> timestep, so `∫(ṁ_in − ṁ_out)dt = 0` while the inventory implied by its own
> profile moves 171 t — about 192 t of gas appears from nowhere on a 74 km line.
> This is not repairable in the class as written: it takes only an inlet boundary
> condition, so there is nothing to pin the arrival pressure and drive line pack.
> It also does not exactly preserve its own steady state — with the boundary
> conditions held **constant** it drifts −6.3 bar on that line (was +30 bar before
> the cell-density fix), because the transient friction closure differs from the
> steady one. Note also that `calculateSteadyState` defaults to **true**, so
> without `setCalculateSteadyState(false)` `runTransient` is only a steady-state
> solve with the clock advanced; and the time step must be shorter than the
> *segment* transit time `L/numberOfIncrements/v`, otherwise the relaxation factor
> saturates at 1 and the whole line responds in a single step. Use it for transport
> delay in a flowsheet; use `TwoFluidPipe` for line pack, shut-in, ramps and
> blowdown (0.00 bar drift on the same null test, mass balance closing to the
> digit), and `WaterHammerPipe` for surge.

### Gray (1974) Correlation — Gas / Gas-Condensate Vertical Wells

`PipeGray` implements the Gray (1974) correlation, the industry standard for
**gas-dominated vertical wells** producing condensate and/or water (API 14B
program). Prefer it over Beggs & Brill for vertical/near-vertical gas-condensate
tubing where the superficial gas velocity is high (> ~4.6 m/s), the tubing is
small (< ~3.5 in), and condensate loading is low (< ~50 bbl/MMscf). It predicts
in-situ liquid holdup and a condensate-film effective roughness.

```java
PipeGray well = new PipeGray("Gray well", inletStream);   // gas-condensate wellstream
well.setDiameter(0.0889);        // 3.5 inch tubing
well.setLength(3000.0);
well.setElevation(3000.0);       // vertical well (upward flow)
well.setNumberOfIncrements(10);
// Optional: swap the holdup closure to Woldesemayat-Ghajar (2007)
well.setHoldupMethod(PipeGray.HoldupMethod.WOLDESEMAYAT_GHAJAR);
well.run();

double dP       = well.getTotalPressureDrop();          // bar
double holdup   = well.getLiquidHoldup();               // fraction (0-1)
double vsg      = well.getSuperficialGasVelocity();     // m/s
double ke       = well.getEffectiveRoughness();         // m (Gray condensate-film roughness)
```

Single-phase gas and single-phase liquid segments fall back to a Haaland
friction-factor Darcy-Weisbach drop, so the same model spans wet-gas wells that
drop out condensate along the tubing. The void-fraction closures are exposed
directly via `VoidFractionCorrelations.woldesemayatGhajar(...)`.

### Pipeline with Formation Temperature Gradient (Wells / Risers)

```java
PipeBeggsAndBrills wellbore = new PipeBeggsAndBrills("Production Well", feedStream);
wellbore.setLength(3000.0);
wellbore.setElevation(-3000.0);  // vertical well
wellbore.setDiameter(0.1571);    // 6-5/8 inch
wellbore.setPipeWallRoughness(5e-5);

// Formation temperature: 90°C at bottom, gradient of -0.03°C/m going up
wellbore.setFormationTemperatureGradient(4.0, -0.03, "C");
wellbore.run();
```

### Pipeline Sizing (Iterative)

```java
// Iterate over diameters to find optimal size
double[] diameters = {0.1524, 0.2032, 0.254, 0.3048, 0.3556, 0.4064, 0.508};
// 6", 8", 10", 12", 14", 16", 20"

for (double d : diameters) {
    Stream testFeed = new Stream("feed", feedFluid.clone());
    testFeed.setFlowRate(flowRate, "kg/hr");
    testFeed.run();

    PipeBeggsAndBrills testPipe = new PipeBeggsAndBrills("test", testFeed);
    testPipe.setLength(pipeLength);
    testPipe.setDiameter(d);
    testPipe.setPipeWallRoughness(5e-5);
    testPipe.run();

    double dP = testFeed.getPressure() - testPipe.getOutletStream().getPressure();
    double velocity = testPipe.getSuperficialVelocity();
    // Check: erosional velocity < API RP 14E limit, dP within allowable
}
```

## 5. CO2 / H2S Corrosion Assessment

### CO2 Partial Pressure Based Screening

```java
// After flash calculation
fluid.initProperties();
double pCO2 = fluid.getPhase("gas").getComponent("CO2").getx()
              * fluid.getPressure();  // CO2 partial pressure in bara

// NORSOK M-001 / DNV-RP-F112 screening:
// pCO2 < 0.02 bar  → low risk (carbon steel OK)
// 0.02 < pCO2 < 0.2 → moderate (corrosion allowance or inhibitor)
// pCO2 > 0.2        → high risk (CRA or heavy inhibition)
```

### Temperature and pH Effects

```java
// Corrosion rate increases with temperature up to ~80°C
// then decreases due to protective FeCO3 film
// Lower pH (more acidic) → higher corrosion rate
// Water cut affects wetting: >30% water cut → higher risk
```

### Network-Level Corrosion (LoopedPipeNetwork)

For production gathering networks, `LoopedPipeNetwork` has inline corrosion
models that compute rates per element during network solution:

```java
// de Waard-Milliams (default) or NORSOK M-506
net.setCorrosiveGas("trunk", 0.035, 0.002);  // CO2 mol%, H2S mol%
net.setCorrosionModel("trunk", "NORSOK");     // "DEWAARD" or "NORSOK"
net.setMinAllowableWallLife(20.0);            // years

net.run();
Map<String, double[]> corr = net.calculateCorrosion();
// Per element: [0] = rate (mm/yr), [1] = pCO2 (bar), [2] = wall life (yr)
List<String> violations = net.getCorrosionViolations();
```

Models: de Waard-Milliams (log10(Vcorr) = 5.8 - 1710/T + 0.67*log10(pCO2))
and NORSOK M-506 (Vcorr = Kt * fCO2^0.62 * (S/19)^0.146).

### Rigorous NORSOK M-506 from a brine (electrolyte pH + FeCO3 film)

`NorsokM506CorrosionRate` (`neqsim.process.corrosion`) is the standalone NORSOK
M-506 model (fugacity, in-situ pH, FeCO3 scaling temperature, wall shear, glycol,
inhibitor). By default it estimates pH from a CO2-water correlation. To drive it
from **rigorous electrolyte thermodynamics** instead, use `NorsokM506ElectrolyteBridge`
— the M-506 analogue of `CO2CorrosionAnalyzer`:

```java
// fluid = SystemElectrolyteCPAstatoil with CO2, water, ions (+ optional Fe++)
NorsokM506ElectrolyteBridge bridge = new NorsokM506ElectrolyteBridge(fluid);
bridge.setFlowVelocityMs(3.0);
bridge.setPipeDiameterM(0.254);
bridge.run();                       // clones + flashes; does not mutate `fluid`
double rate = bridge.getModel().getCorrectedCorrosionRate();  // mm/yr
double pH   = bridge.getInSituPH();          // rigorous aqueous pH (getpH())
double sr   = bridge.getFeCO3SaturationRatio();  // IAP/Ksp, or -1 if no Fe++/CO3--
```

**FeCO3 film feedback (closes the corrosion↔scaling loop):** when the aqueous phase
is supersaturated in siderite (SR>1), a protective film suppresses corrosion even
below the scaling temperature. Supply the ratio directly on the model, or let the
bridge compute it from aqueous Fe++/CO3-- molalities (Sun & Nesic 2009 Ksp):

```java
model.setFeCO3SaturationRatio(sr);   // >1 = protective; -1 = disabled (default)
double film = model.calculateFeCO3FilmFactor();  // 1.0 = no credit, <1 = protective
```

For an auditable standards calculation, pass the resulting in-situ pH and optional saturation ratio
to `NorsokM506CorrosionDesignKernel.Input`. The kernel is the preferred public path when the task
names NORSOK M-506: it enforces the unamended 2017 edition and model envelope, preserves raw inputs
without setter clamping, and returns `CALCULATED_REVIEW_REQUIRED`. Continue to use the bridge to
derive chemistry and the legacy model for sweeps, but do not present either route as a conformity
assessment. The optional saturation-ratio film factor and projected wall loss are NeqSim screening
extensions, not code acceptance criteria.

**Gotchas (verified):** `SystemInterface.clone()` drops the chemical-reaction setup
— re-run `chemicalReactionInit()` on the clone before flashing or the CO2-brine pH
comes out unphysically basic (~10). `setActualPH()` is read back via `getEffectivePH()`,
NOT `getCalculatedPH()`. Proven converging brine: CO2 0.10 / water 0.88 / Na+ 0.01 /
Cl- 0.01, `setMixingRule(10)`.

### Robust in-situ pH for investigations

`getpH()` on the aqueous phase (or `SystemInterface.getpH()`) returns the in-situ
pH. It now has a **built-in acid-gas dissociation fallback**: when the aqueous
phase has water + dissolved CO2/H2S but no explicit `H3O+` species (i.e.
`chemicalReactionInit()` was not run) or the electrolyte solver is unstable and
returns NaN at low pressure, `getpH()` estimates pH from the carbonic/sulfide
acid first-dissociation equilibria — `[H+]=sqrt(K1_CO2·C_CO2 + K1_H2S·C_H2S + Kw)`
— instead of the old silent, unphysical `7.0`. CO2-saturated water → pH ≈ 3.9.
Force it explicitly with `getpH("acidgas")`. This is a **screening** estimate
(ignores bicarbonate buffering and salt-ion alkalinity); for a rigorous speciated
pH in a buffered brine still run `chemicalReactionInit()`.

For corrosion/scale investigations that must always return a finite, bounded,
**source-tagged** value with an explicit pCO2 basis, use
`RobustAqueousPH.estimate(fluid, pCO2Bar)`: it takes the rigorous `getpH()` when
valid, else a CO2-water correlation, and records which source was used
(`getSource()`, `isFellBack()`).

Regression test for the fallback: `neqsim.chemicalreactions.AcidGasPHTest`
(CO2-saturated water pH ≈ 3.9, neutral water ≈ 7, CO2+H2S acidic — all without
`chemicalReactionInit()`).

### Per-segment corrosion + scale along a line (PipeSegmentIntegrity)

`PipeSegmentIntegrity` (`neqsim.process.corrosion`) walks a temperature/pressure/
velocity profile and reports, per segment, the NORSOK M-506 corrosion rate AND the
CaCO3 scale saturation, then ranks the worst corrosion and worst scale segments —
so mitigation can be located along the line. Feed it a run `PipeBeggsAndBrills`:

```java
PipeSegmentIntegrity integrity = new PipeSegmentIntegrity();
integrity.fromPipe(pipe)                       // extracts T, P, mixture velocity, diameter
    .setPipeAndGas(0.2, 0.05)                  // diameter [m], CO2 mole fraction
    .setBrineChemistry(1500, 400, 0, 0, 12000, 35000); // Ca,HCO3,SO4,Ba,Na,Cl [mg/L]
integrity.evaluate();
int worstCorr = integrity.getWorstCorrosionIndex();
int worstScale = integrity.getWorstScaleIndex();
// or supply an explicit profile: integrity.setProfile(tC[], pBara[], vMs[])
```

### Network-Level Sand Erosion (DNV RP O501)

```java
net.setSandRate("W1", 3.0);             // kg/hr
net.setMaxAllowableSandRate(10.0);
net.setMaxAllowableErosionRate(5.0);    // mm/yr

net.run();
Map<String, double[]> sand = net.calculateSandTransport();
// Per element: [0] = rate, [1] = concentration, [2] = erosion, [3] = deposition
List<String> violations = net.getSandViolations();
```

Erosion per DNV RP O501: E = K * Csand * v^2.6 * dp^0.2.
Deposition flagged when v < 1 m/s.

### Sand TRANSPORT is a different question from sand EROSION

Erosion asks "is the fluid too fast?"; transport asks "is the fluid fast enough
to keep the sand moving?". They pull in **opposite directions**, so a line can
be comfortably inside its erosional limit and still be laying down a sand bed.
NeqSim currently models erosion only (`ErosionPredictionCalculator`,
`LoopedPipeNetwork.calculateSandTransport()`), and the network deposition rule
above is a hard-coded `v < 1 m/s`, not a transport criterion. Compute the
transport check explicitly.

**Evaluate transport on the in-situ LIQUID velocity, never on the mixture
velocity.** Sand is carried by the liquid, and because of slip

```
u_L = v_sl / H_L          (in-situ liquid velocity)
u_G = v_sg / (1 - H_L)    (in-situ gas velocity)
```

`H_L > lambda_L` whenever there is slip, so `u_L < v_m`. On a measured mature-well
case (6" line, 52% water cut, ~45 bara) `v_m` was 0.888 m/s but `u_L` only
0.691 m/s — using `v_m` would have overstated the transport margin by 1.29x.

Two criteria, both public and cheap:

- **Oroskar & Turian (1980)** critical (deposition) velocity —
  `v_c = 1.85 C^0.1536 (1-C)^0.3564 (D/d_p)^0.378 Re_p^0.09 x^0.30 sqrt(g d_p (s-1))`
  with `Re_p = D sqrt(g d_p (s-1)) rho_L / mu_L` and `x ~ 0.95`. Report `u_L / v_c`.
- **Shields (1936)** incipient motion of a settled bed — compare the liquid wall
  shear `tau_w = (f/8) rho_L u_L^2` to `tau_c = theta_c (rho_s - rho_L) g d_p`
  with `theta_c ~ 0.045`.

**Sand deposition is a TURNDOWN failure mode.** As rate falls, hold-up *rises* and
the slip ratio grows, so `u_L` collapses faster than `v_m`. Always run a rate
sweep and report the rate at which `u_L` crosses `v_c` — that threshold, not the
present-day margin, is the number operations needs. Do not use erosion headroom
as a reason to choke a well back without checking it.

### Get the operating basis right before computing any velocity

A wrong pressure invalidates every velocity, hold-up and transport number
downstream of it, and no amount of model sophistication recovers it.

**A well flowline runs DOWNSTREAM of the choke.** Its pressure is the receiving
manifold/separator pressure, *not* the wellhead pressure that PDM and the
historian report as "wellhead pressure" / "brønnhodetrykk". On a real case the
wellhead read 45.5 bara while the line ran at 17.0 bara: gas density 63% lower,
mixture velocity 2.1x higher, no-slip hold-up 59% lower, and the sand-transport
margin moved from 1.38 to 1.8-2.3. Take the flowline condition from the
**after-choke transmitter** (Norwegian tag descriptions: "Etter Strupeventil").

**Confirm which route is actually in use, not just which routes exist.** Match
the measured after-choke pressure against the separator pressures; the receiving
vessel is the one a small dP away, and the others are typically tens of bar off.
Check the trend as well as the snapshot — a well swaps onto the test separator
during a well test, which is usually a *higher* pressure and therefore the
**binding case for sand deposition**, while normal production is comfortable.

**Derive the line temperature by an isenthalpic flash across the choke** when
there is no after-choke temperature transmitter (`ops.PHflash(h_wellhead)`). With
a high water cut the JT cooling is small (~1 K at 83 mol% water) because water
dominates the heat capacity — do not assume a large drop.

### Choosing the multiphase model for a transport check

Verified on a 6" water-continuous line (17 bara, 83 mol% water) against
OLGA 2025.1.0 on an identical fluid, geometry and mass flow:

| model | H_L | u_L [m/s] | slip |
|---|---|---|---|
| `PipeBeggsAndBrills` | 0.325 | 1.141 | 2.28 |
| `TwoFluidPipe` | 0.407 | 0.913 | 3.24 |
| OLGA 2025.1.0 | 0.407 | 0.916 | 3.32 |

- `TwoFluidPipe` reproduced OLGA to **0.2% on both hold-up and liquid velocity**.
- **Beggs & Brill is non-conservative for sand transport**: it under-predicts
  hold-up ~20%, so it over-predicts the sand-carrying `u_L` ~25% and inflates the
  margin. Prefer `TwoFluidPipe` (or OLGA) for water-continuous transport checks;
  use B&B for a quick pressure-drop screen only.
- **Gate on convergence.** `TwoFluidPipe` converged in 6 iterations at the above
  duty but failed (399 iterations) at a slower, more liquid-loaded slug condition.
  Always assert `isSteadyStateConverged()` and exclude the result if it is False —
  never quote an unconverged two-fluid number.
- Do not over-constrain `setOutletPressure` with a measured dP the modelled
  geometry cannot produce; that alone can break convergence. If the measured drop
  is much larger than the predicted pipe friction, the difference is manifold
  valves and fittings, not the line.

### Mineral Scale (thermodynamics + kinetics + brine mixing)

NeqSim offers **two routes** to mineral scale. Pick by how much you know about
the brine and whether you need a rigorous precipitated amount.

**(A) Screening SI from an ion analysis (fast, no flash).** Activity-corrected
saturation index (Davies + Ksp(T)) for the common scales directly from a
produced-water ion table in mg/L:

```java
ElectrolyteScaleCalculator scale = new ElectrolyteScaleCalculator();
scale.setTemperatureCelsius(60).setPressureBara(100).setPH(6.0);
scale.setCations(caMgL, baMgL, srMgL, mgMgL, naMgL, kMgL, feMgL);
scale.setAnions(clMgL, so4MgL, hco3MgL, co3MgL);
scale.calculate();
double siCaCO3 = scale.getCaCO3SaturationIndex();  // also BaSO4, CaSO4, SrSO4
// SI = log10(IAP/Ksp): >0 supersaturated (scale risk), 0 at equilibrium, <0 undersaturated
```

**(B) Rigorous scale potential from a speciated electrolyte fluid — REQUIRES
chemical reactions + speciation.** The rigorous saturation ratio needs the *in
situ* ion molalities (Ca²⁺, HCO₃⁻, CO₃²⁻, Ba²⁺, SO₄²⁻, …), which only exist
after the aqueous speciation equilibria have been solved. So you MUST call
`chemicalReactionInit()` before `createDatabase(true)` / `setMixingRule(10)`, and
you should get the in-situ pH from the same speciated flash (see the in-situ pH
section above). Then `checkScalePotential(phaseNumber)` returns, per salt, the
**saturation ratio SR = IAP/Ksp** ("relative solubility"): SR > 1 means
supersaturated and at scale risk.

```java
SystemInterface brine = new SystemElectrolyteCPAstatoil(273.15 + 60.0, 100.0);
brine.addComponent("CO2", 0.02);
brine.addComponent("water", 55.5);
brine.addComponent("Na+", 1.0);
brine.addComponent("Cl-", 1.0);
brine.addComponent("Ca++", 0.02);
brine.addComponent("HCO3-", 0.04);
brine.chemicalReactionInit();     // MANDATORY: builds carbonate/bicarbonate speciation
brine.createDatabase(true);
brine.setMixingRule(10);          // numeric CPA rule (required)
brine.setMultiPhaseCheck(true);   // allow gas + aqueous (+ solid) phases

ThermodynamicOperations ops = new ThermodynamicOperations(brine);
ops.TPflash();                    // solves speciation in the aqueous phase
brine.initProperties();

int aq = brine.getPhaseNumberOfPhase("aqueous");
ops.checkScalePotential(aq);      // uses speciated ion molalities in phase `aq`
String[][] sr = ops.getResultTable();   // rows: {saltName, SR (=IAP/Ksp), ""}
// Read SR per salt; SR > 1.0 => supersaturated => scale precipitation likely.
double pH = brine.getpH();        // rigorous speciated in-situ pH from the same flash
```

Convenience equipment wrapper: `ScalePotentialCheckStream` runs the same SR
check on a process `Stream`.

**Precipitation amount (how much solid forms).** Two options:

- *Rigorous (solid phase flash):* with `setMultiPhaseCheck(true)`, a
  supersaturated brine drops a solid phase during `TPflash()`. Read the
  precipitated mineral directly:
  ```java
  if (brine.hasPhaseType("solid")) {
    double solidMoles = brine.getPhase("solid").getNumberOfMolesInPhase();
    double solidMassKg = solidMoles * brine.getPhase("solid").getMolarMass();
  }
  ```
- *Screening (from SI):* `ScaleMassCalculator` estimates the mg/L precipitated to
  reach equilibrium from ion concentrations and SI (per mineral):
  ```java
  ScaleMassCalculator mass = new ScaleMassCalculator(predictionCalc);
  mass.setWaterVolume(1.0); // litres
  double caco3MgL = mass.calcCaCO3Mass(cCaMolL, cCO3MolL, siCaCO3);  // mg/L
  // also calcBaSO4Mass, calcCaSO4Mass, calcSrSO4Mass, calcFeCO3Mass
  ```
  ⚠️ `ScaleMassCalculator` is **decoupled** — each mineral is treated
  independently, so it *overpredicts* competing minerals (e.g. it reports
  celestite scaling even when barite has already consumed the shared sulphate).

- *Coupled screening (recommended for shared-ion brines):*
  `MultiMineralScaleEquilibrium` drops all supersaturated minerals
  *simultaneously* and re-equilibrates the shared ion pools (sulphate: BaSO4 /
  SrSO4 / CaSO4; carbonate: CaCO3 / FeCO3; calcium shared by anhydrite and
  calcite), giving OLI/ScaleChem-style precipitated **amounts** plus the residual
  brine. It reuses the same Ksp/ion-pairing/activity chemistry as
  `ScalePredictionCalculator`, and can upgrade the activity model from Davies
  (I ≤ 0.5 m) to an extended B-dot (Helgeson) model for high-salinity brines:
  ```java
  MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(predictionCalc)
      .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.BDOT) // high-I option
      .setWaterVolume(1.0);
  eq.solve();
  double baso4MgL = eq.getPrecipitatedMassMgPerL("BaSO4");
  double totalMgL = eq.getTotalScaleMassMgPerL();
  double srResid  = eq.getResidualFreeIonMolPerL("SO4--");
  // each precipitated mineral ends at SI≈0; suppressed minerals stay SI≤0
  ```
  For high-pressure brines, enable the second-order (compressibility) Ksp term on
  the predictor first: `predictionCalc.setSecondOrderPressureCorrection(true)`.

- *In a process flowsheet (scaling mass RATE):* `StreamScaleAnalyzer` extracts the
  aqueous ion chemistry + produced-water throughput from a run `Stream` and turns
  the coupled equilibrium into a **kg/day** scaling rate — the number that matters
  for operations and deposition:
  ```java
  StreamScaleAnalyzer a = StreamScaleAnalyzer.fromStream(producedWaterStream);
  a.analyze();
  double bariteKgPerDay = a.getScaleRateKgPerDay("BaSO4");
  double totalKgPerDay  = a.getTotalScaleRateKgPerDay();
  String dominant       = a.getDominantScale();
  ```
  (Needs an electrolyte-CPA fluid with an aqueous ion phase; a non-electrolyte
  stream carries no ions and reports no scale.)

- *Root cause analysis:* `RootCauseAnalyser.setWaterChemistryFromStream(stream)`
  (or the ion setters) makes a scale-deposit symptom self-quantifying — the
  `MINERAL_SCALE` candidate then carries the coupled dominant mineral and its
  predicted mg/L, and is *down-weighted* when the brine is thermodynamically
  undersaturated (deposit is more likely wax/asphaltene/corrosion product).

- *Agentic (MCP):* the `runChemistry` tool exposes `analysis:"multiMineralScale"`
  (ions in mg/L + T/P + optional `waterFlow_LPerDay`, `activityModel:"BDOT"`,
  `secondOrderPressure`) returning per-mineral amounts and a kg/day rate.

> **Model validation:** the Ksp correlations are pinned to published log10(Ksp)
> at 25 °C in `ScaleKspLiteratureBenchmarkTest` — calcite −8.48, barite −9.97,
> celestite −6.63, anhydrite −4.36, siderite −10.89 (Greenberg & Tomson 1992).
> Keep the siderite form `-59.3498 - 0.041377*T - 2.1963/T + 24.5724*log10(T)`
> consistent across `ScalePredictionCalculator`, `CheckScalePotential` and
> `CalcSaltSatauration` (no `T^2` term).


**Kinetics** — SI says *if*, not *how fast*. `ScaleKinetics` adds induction time
and growth regime on top of the SI:

```java
ScaleKinetics k = new ScaleKinetics().setSaturationIndex(si).evaluate();
double tInd = k.getInductionTimeHours();      // classical nucleation
String regime = k.getLimitingRegime();        // REACTION / TRANSPORT / NONE
double growth = k.getEffectiveGrowthRateMmYr();
```

**Brine mixing (seawater + formation water incompatibility)** — sulphate scale
often peaks at an *intermediate* mixing ratio, not either end member:

```java
BrineMixingScaleEvaluator mix = new BrineMixingScaleEvaluator(formationWater, seawater);
mix.setConditions(60, 100).setPH(6.0).setSteps(21).evaluate();
double worstFrac = mix.getWorstFractionA();    // fraction of formation water
String worst = mix.getWorstMineral();          // e.g. BaSO4
```

Deposition along a line: `ScaleDepositionAccumulator` (walks a `PipeBeggsAndBrills`),
or the coupled corrosion+scale `PipeSegmentIntegrity` above.

### Valve scale drift (plugging → Cv loss → opening drift → RCA)

Scale/solids on a control-valve trim do not fail the valve suddenly: the deposit
shrinks the open flow area, the effective `Kv/Cv` drops, and the level/pressure
controller opens the valve further to hold setpoint — an observable upward
**drift** of the opening until it pins at 100% and control is lost. This is the
classic level-valve-plugging signature. `ThrottlingValve` now carries a fouling
term and `ValveScaleDrift` turns a deposit growth rate into that drift:

```java
valve.setFoulingFraction(0.5);        // effectiveKv = Kv*(1-f); 0=clean
double effCv = valve.getEffectiveCv();

ValveScaleDrift drift = new ValveScaleDrift(valve)
    .setPortDiameter(0.05)            // trim port diameter [m]
    .setKinetics(scaleKinetics);      // or setGrowthRateMmPerYear(20.0)
for (int day = 0; day < 60; day++) {
  drift.advance(1.0);                 // grow deposit, update valve foulingFraction
  process.run();                      // controller compensates -> opening drifts up
}
double daysToPin = drift.predictTimeToPinDays(cleanOpeningPercent); // loss of control
double tPlug     = drift.getTimeToPlugDays();                       // full closure
```

Uniform radial deposit model: `foulingFraction = 1 - ((d0 - 2t)/d0)^2`; the
opening required to hold flow rises as `cleanOpening / (1 - fouling)`. Use it in
an RCA to reproduce the "both LVs → 100% open, level still rising, no inflow
surge" trend and to bound time-to-plug.

### Scale / precipitation remediation (dissolvers & washing for RCA solutions)

Once a deposit is identified, `ScaleRemediationAdvisor` recommends the dissolver /
solvent / wash to clean the **already-fouled** equipment (valves, trim, bridles,
lines, exchangers) — the proposed-solution counterpart to inhibitor prevention.
It is backed by a knowledge-base CSV (`/data/scale_remediation.csv`) and maps
common mineral names to canonical formulae:

```java
ScaleRemediationAdvisor advisor = new ScaleRemediationAdvisor();
List<ScaleRemediationAdvisor.RemediationOption> opts = advisor.recommendFor("calcite");
ScaleRemediationAdvisor.RemediationOption best = opts.get(0); // ordered by effectiveness
best.getDissolver();   // e.g. "Hydrochloric acid (HCl) with corrosion inhibitor ..."
best.getMethod();      // "Acid soak or circulation, then neutralise and flush"
best.getCautions();    // safety/operational cautions
String json = advisor.toJson("BaSO4");
```

Coverage & the key gotcha it encodes: **acid dissolves carbonate/sulfide scale
(CaCO3, FeCO3, FeS) but NOT sulfate scale** — barite/celestite (BaSO4, SrSO4)
need a high-pH chelant (DTPA/EDTA); dithiazine (H2S-scavenger solids) needs a
proprietary dissolver **plus restoring water pH control** to prevent recurrence;
asphaltene/wax/naphthenate use aromatic solvent / hot oil. Unknown deposits
return a "sample-and-identify first (XRD/SEM-EDX)" guard.

`RootCauseAnalyser` uses this automatically: every deposit candidate
(`MINERAL_SCALE`, `WAX_DEPOSITION`, `ASPHALTENE`, `FES_DEPOSITION`) now appends a
concrete "to clean fouled equipment: <dissolver> …" hint to its recommendation,
targeting the coupled dominant mineral when ion chemistry is available.

### Deposits on compressors (fouling → performance loss → washing)

When deposit-forming species (elemental sulfur S8, salt from entrained produced
water, mineral scale, wax) reach a compressor, they foul the impeller and reduce
head/efficiency. The `neqsim.process.equipment.compressor` deposit model bridges
these flow-assurance calculations to compressor performance and washing:

```java
CompressorDeposit dep = CompressorDeposit.fromCompressor(comp);
dep.accumulate(new SolidFlashDepositSource(feed, "S8", DepositMechanism.SULFUR_S8, 0.3), 500.0);
dep.accumulate(new EntrainedSaltDepositSource(10.0, 0.05), 500.0); // entrained brine -> salt
comp.setDepositModel(dep);            // run() degrades efficiency/power
CompressorChart chart500 = comp.buildDegradedChart();               // map after 500 h
WashFluid wash = CompressorDepositWash.recommend(dep);              // salt->WATER, S8->XYLENE
comp.washOnline(wash, 300.0, 3.0);    // online wash removes matching deposits
```

Per-impeller deposit location: `CompressorDepositProfile.compute(...)` /
`computeFromPropertyProfile(...)`. Full API in the `neqsim-api-patterns` skill and the
`compressor_deposit_degradation` doc. Use this to plan sulfur/salt washing of fouled
compressors and to recommend a wash fluid.

## 6. Thermal Analysis

### Cooldown Calculation

```java
// Simple cooldown time estimate for insulated pipeline
// Use Newton's cooling law: T(t) = Tsea + (Tin - Tsea) * exp(-t/tau)
// where tau = m * Cp / (U * A)
// U = overall heat transfer coefficient (W/m2K)
// A = pipe surface area per unit length (m2/m)
// m = fluid mass per unit length (kg/m)
// Cp = fluid heat capacity (J/kgK)
```

### Arrival Temperature Check

```java
// Critical checks:
// 1. Arrival T > hydrate formation T + margin (typically 5°C subcooling)
// 2. Arrival T > WAT (if waxy crude) + margin
// 3. Arrival T > pour point (for restart)
// If not met: increase insulation, add DEH, reduce flow, or inject inhibitor
```

## 7. Flow Assurance Decision Matrix

| Threat | Detection | NeqSim Method | Mitigation |
|--------|-----------|---------------|------------|
| Hydrate | `hydrateFormationTemperature()` | CPA EOS + hydrate check | MEG, methanol, insulation, DEH |
| Wax | `calcWAT()`, `WaxFractionSim` | Wax characterization | Pigging, inhibitor, insulation |
| Asphaltene | de Boer screening | CPA flash at multiple P | Inhibitor, avoid P drop |
| Corrosion (CO2) | CO2 partial pressure | Standard flash | CRA, inhibitor, pH stabilization |
| Slugging | Beggs & Brill flow regime | `PipeBeggsAndBrills` | Slug catcher, topside choking |
| Scale | Ion activity product | Electrolyte-CPA | Scale inhibitor, pH control |
| Elemental sulfur (S8) | S8 solid drop-out at P/T letdown | `setSolidPhaseCheck("S8")` + `TPSolidflash()` | Remove O2 source, heat gas before letdown, reduce dP, `SulfurFilter` |

## 8. CO2 Injection Well Analysis

For CCS/injection well safety analysis, use the dedicated analyzer:

```java
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjWell-1");
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(measuredDepth, innerDiameter, roughness);
analyzer.setOperatingConditions(inletP_bara, inletT_C, massFlowRate_kg_h);
analyzer.setFormationTemperature(surfaceT_C, bottomholeT_C);
analyzer.addTrackedComponent("hydrogen", maxMolFracLimit);
analyzer.runFullAnalysis();
boolean safe = analyzer.isSafeToOperate();
```

### Impurity Monitoring

```java
ImpurityMonitor monitor = new ImpurityMonitor("H2-Mon", stream);
monitor.addTrackedComponent("hydrogen", 0.10);  // 10 mol% limit
monitor.addTrackedComponent("H2S", 0.001);      // 0.1% limit
double enrichment = monitor.getEnrichmentFactor("hydrogen");
boolean exceeds = monitor.exceedsLimit("hydrogen");
```

## 9. Elemental Sulfur (S8) Deposition

Elemental sulfur (cyclo-octasulfur, `S8`) drops out of natural gas as a yellow/grey
solid at points of pressure and/or temperature letdown — compressor inlets, control
and letdown valves, dry-gas seals, pressure regulators, and filters. The gas dissolves
a small amount of S8 (solubility rises with pressure and with H2S/CO2 content, and is
high in condensate, MEG, methanol and TEG); when pressure or temperature drops, the
gas becomes supersaturated and S8 deposits. The **root cause is almost always an
oxygen source** oxidising H2S: `8 H2S + 4 O2 -> S8 + 8 H2O` (O2 typically enters via
preservation fluids, platform-nitrogen purge/seal gas, or injected chemicals).

**Screening workflow:** assume the gas is S8-saturated at a baseline (e.g. scrubber /
separator conditions such as 100 bara, 45 C), then check whether S8 drops to a solid
as the stream follows the compressor / valve pressure-temperature path. `S8` is a
database component (`fluid.addComponent("S8", ...)`), and deposition is detected with a
solid flash.

### S8 solid-drop-out (solubility) check

```java
// Gas saturated with a trace of S8 at baseline, then evaluate a P/T letdown point
SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 100.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("H2S", 0.001);
fluid.addComponent("S8", 1.0e-6);   // trace S8 (mole basis)
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);
fluid.setSolidPhaseCheck("S8");      // enable S8 solid-phase modelling

// Evaluate at the letdown condition (e.g. valve / compressor-inlet P and T)
fluid.setPressure(60.0, "bara");
fluid.setTemperature(30.0, "C");
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPSolidflash();                 // NOT TPflash — needed to form the solid S8 phase
fluid.initProperties();

boolean s8Deposits = fluid.hasPhaseType(PhaseType.SOLID);
if (s8Deposits) {
  double solidMass = fluid.getPhaseOfType("solid").getMass("kg");  // S8 solid inventory
}
```

> Rule of thumb: deposition risk exists wherever a saturated (or nearly saturated)
> stream sees a pressure or temperature drop. Heating the gas *before* letdown or
> reducing the dP moves the point back into the single-phase (dissolved) region.

### SulfurFilter equipment (solid S8 removal + change interval)

`SulfurFilter` runs a `TPSolidflash` internally, removes the solid S8, and tracks the
kg/hr loading for element sizing and change-out interval:

```java
SulfurFilter filter = new SulfurFilter("S8 Filter", valveOutletStream);
filter.setRemovalEfficiency(0.99);       // 99% solid removal
filter.setFilterElementCapacity(50.0);   // kg S8 per element set
filter.setDeltaP(0.5);                    // clean pressure drop [bar]
filter.run();

boolean s8Present = filter.isSolidS8Detected();
double s8Rate = filter.getSolidSulfurRemovalRate();  // kg/hr captured
double interval = filter.getChangeIntervalHours();   // hours until element change
```

### Particle nucleation (optional, for particle-size / filter rating)

```java
ClassicalNucleationTheory cnt = ClassicalNucleationTheory.sulfurS8();
// pair with PopulationBalanceModel for particle-size distribution across the letdown
```

**Mitigations (in order of effectiveness):** eliminate the O2 source (nitrogen/
preservation-fluid quality and routing); heat the gas upstream of pressure reduction;
reduce the dP / minimum landing pressure; install a `SulfurFilter` (watch the dP);
keep dry-gas-seal gas warm and well above its S8 saturation point.

**Related:** `SourServiceAssessment` (`setElementalSulfurPresent(true)`) for materials
impact; `neqsim-electrolyte-systems` for the aqueous-phase corrosivity of wet sulfur.

## 10. Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Hydrate T too low (no water in fluid) | Add water component to fluid |
| Using SRK instead of CPA for water systems | Use `SystemSrkCPAstatoil` with mixing rule `10` |
| Pipeline output T = input T (adiabatic) | Set `outerTemperature` for heat loss |
| Zero viscosity from pipeline calculation | Call `fluid.initProperties()` after flash |
| Wax prediction fails (no heavy fractions) | Add C7+ TBP fractions with wax model enabled |
| MEG not reducing hydrate T | Check MEG is partitioning to aqueous phase |
| No solid S8 phase forms | Use `TPSolidflash()` (not `TPflash()`) and call `setSolidPhaseCheck("S8")` first |
| S8 deposition risk missed | Saturate the gas at a realistic baseline (scrubber/separator P,T) before checking the letdown point |
