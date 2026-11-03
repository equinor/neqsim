---
name: neqsim-flow-assurance
description: "Flow assurance patterns for NeqSim. USE WHEN: predicting hydrate, wax, asphaltene, CO2/H2S corrosion (NORSOK M-506), mineral scale and brine mixing, valve scale drift and remediation, sulfur S8 deposition, metal-loss screening, pipeline hydraulics, DNV-RP-F109/F105/F104/F110/F114 screening, water hammer, slug flow, thermal analysis or inhibitor dosing. Code patterns plus the governing standards for every threat."
last_verified: "2026-09-07"
---

# Flow Assurance Analysis with NeqSim

Consolidated guide for all flow assurance threats â€” hydrate, wax, asphaltene, corrosion,
hydraulics, water/liquid hammer screening, slugging, and thermal management â€” with
NeqSim code patterns.

> A complete study report with pipeline drawings (route, profiles, hydrate margin, threat matrix,
> validation) is produced by `neqsim flow-assurance-report` â€” see the
> `neqsim-flow-assurance-study-report` skill. This skill stays the calculation reference.
> A whole subsea SURF network (templates, injection, risers, umbilical, concept comparison) is
> generated, solved and documented by `neqsim surf-study` â€” see the `neqsim-surf-design-study` skill.
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

This skill quantifies the **threat** (hydrate curve, WAT, SI, corrosion rate). For the
**chemical** answer â€” which product, minimum effective dose, cocktail compatibility, H2S/O2
scavenger sizing, demulsifier vs oil-in-water spec, and chemical root-cause of a deposit â€”
load `neqsim-production-chemistry` (`neqsim.process.chemistry`) and feed it the numbers
computed here.

For local dry hydrocarbon gas/oil interface investigations, the existing film boundary
has a default-off `setCoupledMassHeatTransferEnabled(true)` numerical option. Read
`docs/fluidmechanics/InterphaseHeatMassTransfer.md` for its residual gates and limited
positive-composition/finite-transport range. It is not connected to the transient
`TwoFluidPipe` source. Zero liquid Reynolds number currently gives an unsupported
stratified transfer coefficient and rejects; never interpret the old unit-matrix
fallback as stationary-pool evaporation. Keep #4674 physical boundary/full-cycle and
#4675 heat/area/diffusion qualification separate from numerical convergence.

## 1. Hydrate Analysis

### EOS Selection for Hydrate Calculations

| Aqueous phase | NeqSim Class | Mixing Rule |
|---------------|-------------|-------------|
| Pure water / fresh water | `SystemSrkCPAstatoil` | `10` |
| Water + MEG / methanol / ethanol | `SystemSrkCPAstatoil` | `10` |
| **Salt brine / formation water (NaCl, CaCl2, ...)** | `SystemElectrolyteCPAstatoil` | `10` |
| Pitzer salt-brine water activity | `SystemPitzer` | `"classic"` |

CPA or a consistent activity-based aqueous model is needed for waterâ€“hydrocarbon hydrate modeling.
**When dissolved salts / electrolytes are present, use `SystemElectrolyteCPAstatoil` or a
parameterized `SystemPitzer`** so the salt
thermodynamic (hydrate-suppression) effect is captured â€” plain
`SystemSrkCPAstatoil` ignores ion activity and underestimates subcooling margin.

For Pitzer hydrate onset, load `neqsim-electrolyte-systems` and read
`docs/thermo/pitzer_hydrate_equilibrium.md`. Accept its hand-off of salt mole basis, dataset,
explicit CO2-chloride zeta values (and K-Mg theta when both ions are present), pressure grid and operating temperature. Use the standard
temperature/pressure/curve operations; the Pitzer route checks the fugacity residual and throws
on failed points. Preserve the aqueous reference-state convention and guest Henry limits.
Do not label onset calculations as hydrate amounts, kinetics or validated complete drilling-mud predictions.
Carry forward the experimental assessment separately from convergence: the mixed-chloride high-pressure cases
include errors exceeding 1 K. Do not generalize the NaCl benchmark to all salts, concentrations or pressures.

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

`setHydrateCheck(true)` is mandatory: without it `hydrateFormationTemperature()` fails
(no hydrate phase) and any wrapper that swallows the exception reports NaN. The fluid must
contain `water`. `HydrateRiskMapper` (and MCP `runFlowAssurance` `hydrateRiskMap`) now sets the
flag itself and returns `RiskLevel.UNKNOWN` / `RESULT_NOT_AVAILABLE` with `failureReasons` when
no equilibrium temperature exists â€” never treat an unavailable hydrate temperature as safe.

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
// 25 wt% MEG in the water + MEG phase: n_MEG = n_water * 18.015 * 0.25 / 0.75 / 62.07 = 0.0145 mol
// (0.05 mol MEG per 0.15 mol water would be 53 wt%: amounts are MOLES, never weight percentages)
inhibitedFluid.addComponent("MEG", 0.0145);
inhibitedFluid.setMixingRule(10);
inhibitedFluid.setMultiPhaseCheck(true);
inhibitedFluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(inhibitedFluid);
ops.hydrateFormationTemperature();
double inhibitedHydrateT = inhibitedFluid.getTemperature() - 273.15;
// Compare with uninhibited to get subcooling margin
```

For the **injection rate** that delivers a required wt% in the water phase (including purity
and lean/rich MEG bookkeeping), use
`neqsim.process.chemistry.hydrate.ThermodynamicHydrateInhibitorPerformance` â€” see
`neqsim-production-chemistry`. Use the CPA curve above for the design concentration and the
chemistry model for the rate.

### Salt-Inhibited Hydrate (Formation Water / Brine)

```java
// Dissolved salts depress the hydrate temperature â€” use the electrolyte CPA model
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
// SystemSrkCPAstatoil would miss the salt suppression â€” always use electrolyte CPA with ions
```

### MEG Concentration Sweep

```java
// Find required MEG concentration for target subcooling
double[] megWtPct = {0, 10, 20, 30, 40, 50};
for (double wt : megWtPct) {
    // wt % MEG is a MASS fraction of the water + MEG mixture: convert to moles (M_water 18.015, M_MEG 62.07 g/mol)
    double waterMol = 0.20 * (1.0 - wt / 100.0) / 18.015;
    double megMol = 0.20 * (wt / 100.0) / 62.07;
    double waterFrac = waterMol / (waterMol + megMol) * 0.20;
    double megFrac = megMol / (waterMol + megMol) * 0.20;
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

**Sweep gotchas (found building an HPHT tie-back, Fogelberg VPbo task):** `hydrateFormationTemperature()`
with a rich gas condensate (C7+ pseudo-components) plus water and MEG at 40 wt % MEG did not return
(CPA hydrate solver loop, no exception, no timeout). Run every MEG/pressure point of a sweep in a
watchdog thread or subprocess with a hard time limit and keep the sweep to at most 30 wt % MEG. If a
40-50 wt % row is still needed, a linear extrapolation (about -4.5 K per 10 wt % at 30-400 bara) is an
UNVALIDATED screening number: label it as extrapolated, never use it for a pass/fail margin, and do not
let the whole script hang for it. Write the intermediate JSON before the hydrate loop so a kill does not lose the
fluid and CVD results.
For flowline hydraulics with `PipeBeggsAndBrills` a trial inlet pressure that is too low throws
`HydraulicDomainException: Non-positive pipeline pressure` instead of returning a low outlet pressure:
catch it inside the chain function and return outlet 0 (infeasible). Letting it propagate out of a
bisection silently turns a whole row of the inlet-pressure table into NaN.
`ThermodynamicOperations.hydrateFormationTemperature()` is `void`: the answer is left in the fluid
temperature, so read `fluid.getTemperature() - 273.15` after the call (`float(ops.hydrateFormationTemperature())`
fails with `NoneType`; found in the Mistral Nord VPbo task). A wellstream built with `SystemSrkEos` plus
`setHydrateCheck(true)` also under-predicts the hydrate temperature (methane benchmark about -8 K): rebuild
the composition in `SystemSrkCPAstatoil` (mixing rule 10) and benchmark methane at 43/76/140 bara against
5/10/15 C before using the numbers (CPA gives -0.03/+0.33/+0.58 K).
To benchmark `PipeBeggsAndBrills` against Darcy-Weisbach use a dry single-phase gas (pure methane, 60 C,
`setHeatTransferCoefficient` large): the deviation is then 0.2-0.4 %. A lean-gas condensate line cooled
to seabed temperature differs by 25-50 % because the liquid holdup is real, so that difference is not a defect.
Gas-condensate saturation pressure (Tyrihans cellar task): a bubble-point search on a lean gas-condensate fluid returns
a wrong value because the fluid has a dew point; take the saturation pressure of GOR above about 1500 Sm3/Sm3 fluids
from the CVD first step (dew point) and record it as a limitation. `hydrateFormationTemperature()` on a GOR 250 oil fluid
with 3 mol % water at 200 bara did not converge and left the temperature unchanged: check that the returned temperature
differs from the start temperature before reporting it and report `None` as a gap, not as a hydrate-free result.
HPHT wellstream hydrates (Erlend / Kristin study): the CPA hydrate solver also stalls (no exception) with the
C7+ TBP/plus pseudo-components at 50 bara and 1 mol % water, even without MEG. Rebuild the CPA mixture from the
light components only (C1-C6, N2, CO2, water, renormalised), as C7+ barely moves the hydrate temperature; a
Kristin-type gas (CGR 300 Sm3/MSm3) with 1 mol % water gave 17.2 / 21.2 / 26.6 / 31.9 C at 50 / 100 / 300 / 600 bara.
Do not iterate `PipeBeggsAndBrills` inlet pressure by a fixed point `p_in += p_out_target - p_out`: it stalls at high
pressure drop (unconverged tables looked like data). Use a secant solve, treat a `HydraulicDomainException` as outlet
pressure 0, store an `ok` flag and exclude `ok == False` rows when interpolating; beyond the last converged rate
extend the table with dp proportional to q^2 instead of the last slope.

### MEG/Water Film Conditioned by Dry Gas (pipeline drying after glycol swabbing)

Question pattern: "what MEG:H2O ratio does a residual film reach under dry export gas, and why is
the gas above the dew-point spec?" Worked task: `2026-09-29_steady_state_megh2o_ratio_during_dry_gas_drying_of_a_meg`.

- **Direction:** a 99 wt% MEG film is *drier* than the plateau (about 90 wt% at 6 C, 140 bara), so the
  gas is the wetter side and the film **absorbs** water. It "dries" only if it starts wetter than the plateau.
- **Equilibrium curve:** two-phase `SystemSrkCPAstatoil` TPflashes over an MEG sweep give gas water *and MEG*
  content versus film composition (gas composition depends on the liquid composition, T and P only).
  Report film composition on a **gas-free** basis, MEG/(MEG+water); dissolved CO2/CH4 is ~0.8 wt%.
  MEG is not strictly non-volatile: about 5 ppm wt in the gas at 140 bara.
- **Dew-point spec fixes only the water mole fraction** y_in, not the pipe condition. Hand chain: water
  fugacity continuity, `a_w = y_in * phiV_w(T,P) * P / f0_w(T,P)`, with `f0_w` = IAPWS vapour pressure x
  Poynting; `a_w` from the CPA liquid fugacity coefficient divided by the pure-water coefficient. Use binary
  (gas-free) mole fractions when fitting `ln gamma_w` or the fit mis-predicts at high MEG.
- **Plateau offset by mass balance:** with a stationary film the gas leaves carrying water and MEG in the
  film's own ratio, so `(y_out - y_in)/y_in = r * y_MEG / y_in` (about 1.7 %), independent of `K_G` at large NTU.
  The film inlet zone is stripped of MEG first (a film-free zone grows at ~200 m/day in a 0.4 m, 60 kg/s example).
- **Flash per time step is backward Euler:** unconditionally stable, plateau independent of step size; an
  explicit coding is stable only below `2 / (d y*_H2O / d r)` kg gas per kg film MEG per step.
- **Dew point is logarithmic** (~6 % in water content per K at these conditions) and, for CPA, nearly
  pressure independent at 70-140 bara: reading the same gas as a dew point at pipe pressure adds ~0.9 K.
- **Basis traps:** CPA liquid-water dew point is a supercooled-liquid reference below freezing;
  ISO 18453 / GERG-water uses an ice reference below 273.16 K. These are different phase boundaries.
  For GERG-water, use `SystemGERGwaterEos`, `setMixingRule(8)`, and a
  `ThermodynamicOperations` instance's `waterDewPointTemperatureFlash()`; follow the ISO 18453 execution rule
  in `neqsim-standards-lookup` and the [dew-point guide](../../../docs/standards/dew_point_standards.md).
  Recalculate outputs from the former non-convergent solver before comparing models. Neither an
  ideal-gas estimate nor a CPA/GERG difference alone identifies an ISO implementation error.
- **Numerics:** for N >= 100 film cells use `solve_ivp(method="BDF")`; LSODA stalls on the film-exhaustion
  switch. Regularise `r = W/(M + delta)` so a film-free cell cannot return a spurious composition.

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
// High-risk zone: Î”P > 200 bar above bubble point for light oils

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
pipe.setOuterTemperature(277.15);  // 4Â°C seawater
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

> **GOTCHA â€” deliverability limit vs bug.** `PipeBeggsAndBrills` uses a **fixed
> inlet pressure**. If frictional Î”P over a long/small line exceeds the inlet
> pressure, `run()` throws `InvalidOutputException: ... Outlet pressure is
> negative`. That is a **genuine deliverability limit** (the line cannot pass
> that rate at that inlet P), not a solver failure â€” catch it and report the
> max deliverable rate. To model to a fixed **arrival** (outlet) pressure
> instead, raise the inlet pressure until the delivered rate matches, or iterate
> inlet P per rate.

> **PATTERN â€” tubing outflow (VLP) and gas-lift need on a heavy-oil tie-back.** Put the **bottom-hole**
> pressure on the inlet stream (`P_bh = P_res - q_liq / PI`), set `setLength(MD)`, `setElevation(+TVD)`,
> `setAdiabatic(true)` and read the outlet pressure as the wellhead pressure; add lift gas as extra gas in the
> stream composition (bottom injection slightly over-states the lift). A 24 API oil (10 cP at 85 C) with 2000 m TVD
> has a hydrostatic head of about 170 bar, so a 205 bar reservoir cannot reach a 40 bar arrival without lift: sweep
> water cut x rate x lift GLR once, tabulate the WHP margin against `arrival + line dP + choke`, and read the
> deliverable rate per well and the GLR needed from the table (it falls from about 2.5 kSm3/d per well at 5 % water cut
> to zero near 85 %). Every `None`/exception in the table is the deliverability limit above, not a failure.
> `getMixtureViscosityProfile()` / `getSegmentMixtureViscosity()` return **cP**, not Pa.s (do not multiply by 1000).

> **GOTCHA â€” `getFlowRegime()` naming.** Beggs & Brill regimes are returned as
> `SEGREGATED` (stratified/annular â€” gravity-dominated), `TRANSITION`,
> `INTERMITTENT` (plug/slug), `DISTRIBUTED` (bubble/mist). "Gravity-dominated /
> liquid loading" = SEGREGATED (+ low-velocity TRANSITION).

> **GOTCHA â€” profile index vs `getFlowRegime()`.** `run()` evaluates the
> correlation once per increment **and then once more at the outlet state**, so
> every correlation profile (`getLiquidHoldupProfile()`,
> `getFlowRegimeProfileList()`, â€¦) has `numberOfIncrements + 1` entries and
> index *i* belongs to `getPressureProfile()[i]`. `getFlowRegime()` returns the
> **outlet** state. Reading `getFlowRegime()` next to
> `getSegmentLiquidHoldup(0)` compares two different states and can look like a
> discontinuity. Always pair `getSegmentFlowRegime(i)` with
> `getSegmentLiquidHoldup(i)`.

> **VALIDITY â€” low liquid loading.** On a long wet-gas export line (ID 0.355 m,
> no-slip liquid fraction Î»_L â‰ˆ 0.006), Beggs & Brill over-predicts Î”P well above
> a single-phase Darcy-Weisbach integration of the same line, and the gap is
> entirely the two-phase friction multiplier `exp(S)`: removing it brings Î”P
> within 4% of that analytic integration. `S` is monotonically *increasing*
> in `y = Î»_L / H_LÂ²`, so *less* liquid gives a *larger* multiplier â€” the
> opposite of the physical trend â€” up to a bounded maximum of `exp(S) = 3.19` at
> `y = 52.1`. B&B is calibrated for Î»_L down to roughly 0.01â€“0.02; below that,
> use `TwoFluidPipe` or a mechanistic simulator and treat B&B as a conservative
> upper bound. The published map also has a genuine step where the segregated and
> distributed correlations meet at `L1` for Î»_L < 0.01 (no transition band
> exists there): hold-up Ã—0.70 and Î”P Ã—1.12 across a 1 bara change. That step is
> in the correlation â€” do not smooth it.

> **Fixed defects worth knowing (all affected the *inclination* correction).**
> Older NeqSim builds silently returned the horizontal hold-up on an inclined leg
> when (a) the pipe angle was converted degreesâ†’radians twice, (b) the
> Baker-Swerdloff surface tension went negative above 274 bara or for a very
> light liquid, making the liquid velocity number NaN, or (c) the flow regime was
> `TRANSITION`, which had no inclination branch at all. All three are fixed and
> locked by `PipeBeggsAndBrillsCorrelationTest`. If you are on an older build,
> sanity-check that uphill hold-up clearly exceeds horizontal hold-up before
> trusting an inclined result.

> **Phase-count code paths.** `PipeBeggsAndBrills` assumes phase 0 is the gas
> whenever the stream has more than one phase. A **gas-free** stream that splits
> into oil and water (dead oil with free water at high pressure) used to be
> modelled as gasâ€“liquid, with the oil phase acting as the gas: fictitious flow
> regime, hold-up 0.21 instead of 1, and Î”P 44% above the homogeneous liquid
> value. It is now carried as a homogeneous liquid with a volume-weighted density
> and viscosity. The three-phase liquid density is also now combined on **volume**
> fractions (total mass over total volume) rather than mass fractions. Locked by
> `PipeBeggsAndBrillsPhasePathsTest`.

> **Where the error sits.** On a **single-phase** gas line Beggs & Brill matches a
> Darcy-Weisbach integration to a few tenths of a per cent on pressure drop and to
> ~0.15 K on arrival temperature â€” the friction and energy paths are sound. The
> over-prediction seen on the *two-phase* version of the same line is therefore
> entirely the two-phase friction multiplier, not the solver.

> **Comparisons against commercial transient multiphase simulators are not
> published in this repository.** Their licence terms generally prohibit
> publishing benchmark results and prohibit using the software to develop
> competing software, so no NeqSim closure is tuned to such a tool and no measured
> deviation against one is recorded here. Validate against experimental,
> laboratory or field data, or against an analytic/first-principles check.

> **`TwoFluidPipe` steady-state usage.** Gate every result on the complete
> immutable `SteadyStateConvergenceReport`, not a stationary pressure trace or
> iteration count. Require `isConverged()`, inspect the explicit termination
> reason, and retain pressure-momentum, pressure-update, total-liquid-holdup,
> water/oil-split, thermodynamic-property, and total-pressure-drop residuals
> against the report's unchanged tolerance. Pressure-floor and wall-clock
> termination are non-converged. Repeat mesh sensitivity for the actual
> geometry; no fixed cell length is universally qualified.

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
> â€” all fixed, but the symptoms are generic and worth recognising elsewhere.**
> (1) *A time integrator inside a fixed-point sweep.* The steady solve integrated
> `LiquidAccumulationTracker` once per iteration with a nominal `dt`; that tracker
> only ever adds liquid, ratchets its volume up to what the sections already hold,
> then adds it back on top of that holdup, so it has no fixed point. Valley
> sections climbed to the 0.85/0.95 cap and the profile never settled.
> (2) *A correlation overriding a solved momentum balance.* The minimum-slip
> constraint applied the Beggs and Brill horizontal holdup correlation as a lower
> bound in every regime. It is fitted to 1â€“1.5 inch air-water loops at
> near-atmospheric pressure with Î»_L â‰¥ 0.01; the line runs Î»_L â‰ˆ 0.008
> in a 14-inch pipe at 200 bara, and the floor was binding in EVERY section â€” so
> the reported holdup was the correlation, not the solved momentum balance, worth
> â‰ˆ+20% on Î”P through the mixture density. Only the scale-free
> `lambdaL * minimumSlipFactor` bound remains.
> (3) *Convergence declared without re-evaluating thermodynamics.* The flash runs
> every `ssFlashInterval` sweeps but the "flash moved nothing" flag started false,
> so a non-flash sweep read as settled. The solve could exit after ONE sweep on
> densities it had never revisited.
> If you see a steady profile with a section sitting exactly on a cap, or
> `getSteadyStateIterationsUsed()` returning 1 on a long line, suspect these.

> **Behaviour after those fixes**, on that line at default settings: the rate
> exponent in Î”P ~ rate^n rises from about 2.1 to about 3.1 across a 3x rate
> range, i.e. the density feedback along the line is reproduced, not just the
> level at one rate; 10 MW of DEH raises arrival T by ~17 K and Î”P by ~15%, so the
> energy equation feeds the momentum balance. Grid-converged (160 vs 320 sections
> within 0.4%). Terrain response is solved, not tuned: the annular film balance
> carries `tau_i = tau_wL + rhoL*g*sin(theta)*delta`, so holdup responds to
> inclination and scales with `sin(theta)`. The compact public 3 km,
> 10-degree uphill gas/oil/water fixture now converges on 30 and 60 cells, with
> 0.538% arrival-pressure and 0.983% mean-liquid-holdup sensitivity and every
> final report residual below `1e-4`. This is numerical verification, not
> experimental qualification. The historical 73.8 km / 15 m3/hr free-water
> input is unavailable, so the earlier 4,078-iteration / 1,200 s report cannot
> be reproduced or claimed fixed. Never generalize the compact result to that
> unavailable case.

> **`TwoFluidPipe` also fails silently when a line has no deliverability.** The
> marching solver clamps section pressure at a 1 bara floor; that clamp is a
> fixed point of itself, so the per-section change falls below tolerance and the
> sweep would report success on a case with no physical solution. Check
> `pipe.isSteadyStatePressureFloorLimited()` â€” when it is true,
> `isSteadyStateConverged()` is withheld and the profile must be discarded, not
> reported. `PipeBeggsAndBrills` throws `Outlet pressure is negative` on the same
> condition.

> **`TwoFluidPipe` holdup at low rate is dominated by terrain trap sections.**
> The MAXIMUM holdup at 4 and 7 MSm3/d sits well above the line mean (a single
> valley section), so do not
> quote local `TwoFluidPipe` holdup or valley inventory as a design number. The
> three-phase bookkeeping is sound â€” gas/oil/water fractions sum to one and stay in
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
> agree wherever the Kelvin-Helmholtz margin exceeds one â€” identical at 10 MSm3/d on the
> export line â€” and differ at 4 MSm3/d, where the equilibrium branch reclassifies 272 of 320
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
> over-predicts Î”P by ~2.3x, and because it scales as `G^2/rho_mix` it makes extra liquid
> REDUCE the gradient, inverting the terrain response. The separated form is scoped to
> stratified flow because its perimeters come from a circular-segment layer; annular flow,
> whose film wets the whole perimeter, is not that geometry.

> **The historical 73.8 km comparison is not a public qualification basis.**
> Its complete input and measurement package is unavailable in the repository,
> so earlier reported pressure-drop and holdup deviations are retained only as
> repair history. Do not route or certify a study from those values. Use the
> reproducible public evidence and explicit failed/unsupported rows in the
> TwoFluidPipe evidence matrix.

> **Direct electrical heating (DEH)** is available on both models with the same
> convention â€” the power set is what reaches the fluid, so cable and coating
> losses must already be deducted:
> `pipe.setDirectElectricalHeatingPower(watts)` or
> `setDirectElectricalHeatingPowerPerMeter(wattsPerMetre)`. In `TwoFluidPipe`
> steady state each segment decays toward the balance temperature
> `T_surface + q/(UÂ·Ï€Â·D)` (exact for a uniform source, cannot overshoot); it also
> works in transient and with wall heat transfer switched off. A tool with no
> distributed-heating input can represent the same source through the identity
> `âˆ’UÏ€D(Tâˆ’T_surf) + q â‰¡ âˆ’UÏ€D(T âˆ’ [T_surf + q/(UÏ€D)])`, i.e. by raising the ambient
> temperature by `q/(UÏ€D)`.

> **`TwoFluidPipe` liquid-rich and severe-slugging transients remain
> unqualified.** The legacy route can still develop phase backflow and clamp the
> outlet flux at zero while its finite-volume balance closes exactly. Gas-dominated
> null cases remain usable, but do not promote a liquid-rich or severe-slugging
> trajectory from either a small residual or a completed time loop alone. Use the
> analytical `SevereSluggingBenchmarkHarnessTest` screen and the public Tengesdal
> evidence until the dynamic qualification gates below pass.
>
> **Finite-rate hydrocarbon pool transfer is parameterized screening.** On the
> conservative-energy Euler route, `HydrocarbonPoolTransfer` accepts explicit
> component transfer coefficients, evaporation enthalpies, interface width and
> available W/m. It preserves component/momentum/energy ownership and exposes
> donor/heat-limit diagnostics. Do not use an arbitrary relaxation time as a
> fitted evaporation rate, and do not promote analytical conservation tests to
> shutdown/restart, swept-slug, receiver-capacity or hydraulic-pigging evidence.
> Calibration and validation need separate independent public multicomponent
> rate data plus mesh/time and complete-cycle evidence.
> The unlimited-heat 780 s rich-gas cycle is numerical mesh evidence. A finite
> heat-supply cycle and closed-outlet settling currently fail loud when a
> far-from-equilibrium forced phase changes its EOS branch; do not clip or
> relabel that state to manufacture a restart result.
>
> **Coupled component/phase/thermal slug transport has a narrower envelope.**
> For the conservative Lagrangian slug/film path, named-component phase sources
> and their partial-enthalpy latent source must be frozen from the same
> hydrodynamic RHS-stage equilibrium state. The Stage 4 closed wet-gas regression
> verifies phase, total, component, interphase, and thermal ledgers around a
> seeded in-domain marker. It is single-stage Euler evidence only. Multi-stage
> phase appearance needs stage-local component inventories and fails before
> mutation. Named-component transport with signed outlet backflow requires
> `setOutletBoundaryFluid(externalFluid)` before
> `setAllowOutletPhaseBackflow(true)` so reversed phase composition, density,
> and enthalpy have an explicit physical owner. Never infer that state from the
> last interior cell. The current rich-gas pressure-ramp check is conservative
> boundary evidence, not complete blowdown, pooling, restart, or evaporation-rate
> qualification. For a gas takeoff above retained liquid, use
> `setGasOnlyOutletTakeoff(true)` only with a constant-pressure outlet. Oil and
> water remain externally closed while retaining traction and internal drainage;
> do not replace this contract with inventory clipping or a manufactured boundary
> mixture.
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
> **The transient tells you when it has failed â€” always check every diagnostic.**
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
> ratio uses `S = 1 + 1.75Â·max(0, 1 âˆ’ (Fr/3)Â²)`, a stratified plateau that rolls
> off to no slip once the liquid disperses above a liquid Froude number of about
> 3; the previous form cut off at Fr = 2 and under-predicted water holdup badly.
> One gap remains open: the pressure drop is over-predicted in this liquid-rich
> regime, far more than on a gas-dominated line.
>
> **Exporting NeqSim to OLGA â€” the fluid basis is two files, not one.** The
> `.tab` PVT table fixes the phase behaviour; the **hydrate boundary is separate**
> and OLGA does not compute it. Without a `HYDRATECURVE` in the case, OLGA falls
> back to the Hammerschmidt correlation, so a study whose NeqSim half uses CPA
> hydrate equilibrium and whose OLGA half uses Hammerschmidt disagrees about where
> hydrates form, invisibly. Export both from the same fluid:
> `OLGAhydrateCurveGenerator` writes the `HYDRATECURVE LABEL=..., PRESSURE=(...)
> bara, TEMPERATURE=(...) C` block and returns the matching
> `HYDRATECHECK HYDRATECURVE="..."` line for the flowpath. OLGA interpolates that
> curve **linearly**, so span the pressures the case actually visits and use â‰¥20
> points when the range reaches below ~50 bara (4 points over 10â€“200 bara costs
> 4.1 K of hydrate temperature; 20 points costs 0.48 K). The OLGA output variable
> is `DTHYD` in Â°C, and it is `T_hydrate âˆ’ T_fluid` â€” **positive means inside the
> hydrate region**, negative is the safe margin, which is the opposite of the
> intuitive reading. Full OLGA-side workflow in the community
> `neqsim-olga-multiphase-simulator` skill.
>
> **Check three-phase PVT keyword semantics.** Use `RS = gas mass/(gas + oil
> mass)`, excluding the separate aqueous phase. At standard conditions, use
> `GLR = gas volume/(oil + water volume)` and `WC = water volume/(oil + water
> volume)`; never copy GOR into GLR for a wet fluid. Omit optional multicomponent
> bubble headers rather than exporting failed or inappropriate wet-fluid roots.
> Regenerate older tables after the issue #4478 corrections; see
> `docs/integration/olga_pvt_table_generation.md` for the no-liquid conventions
> and required pure-component saturation validation.
>
> **Never build a volumetric phase fraction from `phase.getVolume()`.** With a
> Peneloux volume shift active it disagrees with `getDensity()` by the shift â€”
> +16.6% for oil and +31.7% for water on a typical SRK three-phase system, while
> gas matches to 0.25%. Use `getFlowRate("kg/sec")/getDensity("kg/m3")`.

> advection-relaxation transport lag, not a conservation-law solver, and the
> Beggs & Brill correlation is not even used on that path (friction reverts to
> single-phase Darcy-Weisbach, viscosity is frozen at the inlet). It **does not
> store mass**: on a rate step the outlet mass flow equals the inlet at every
> timestep, so `âˆ«(á¹_in âˆ’ á¹_out)dt = 0` while the inventory implied by its own
> profile moves 171 t â€” about 192 t of gas appears from nowhere on a 74 km line.
> This is not repairable in the class as written: it takes only an inlet boundary
> condition, so there is nothing to pin the arrival pressure and drive line pack.
> It also does not exactly preserve its own steady state â€” with the boundary
> conditions held **constant** it drifts âˆ’6.3 bar on that line (was +30 bar before
> the cell-density fix), because the transient friction closure differs from the
> steady one. Note also that `calculateSteadyState` defaults to **true**, so
> without `setCalculateSteadyState(false)` `runTransient` is only a steady-state
> solve with the clock advanced; and the time step must be shorter than the
> *segment* transit time `L/numberOfIncrements/v`, otherwise the relaxation factor
> saturates at 1 and the whole line responds in a single step. Use it for transport
> delay in a flowsheet; use `TwoFluidPipe` for line pack, shut-in, ramps and
> blowdown (0.00 bar drift on the same null test, mass balance closing to the
> digit), and `WaterHammerPipe` for surge.

### Gray (1974) Correlation â€” Gas / Gas-Condensate Vertical Wells

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

// Formation temperature: 90Â°C at bottom, gradient of -0.03Â°C/m going up
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
// pCO2 < 0.02 bar  â†’ low risk (carbon steel OK)
// 0.02 < pCO2 < 0.2 â†’ moderate (corrosion allowance or inhibitor)
// pCO2 > 0.2        â†’ high risk (CRA or heavy inhibition)
```

### Temperature and pH Effects

```java
// Corrosion rate increases with temperature up to ~80Â°C
// then decreases due to protective FeCO3 film
// Lower pH (more acidic) â†’ higher corrosion rate
// Water cut affects wetting: >30% water cut â†’ higher risk
```

### Sour service (H2S cracking) screening for compressors and vessels

H2S cracking (SSC) is a materials question, not a corrosion rate: ISO 15156-2 (NORSOK M-001 cl. 6.1.3) calls the service sour when the wet gas has
pH2S >= 0.3 kPa at total pressure >= 0.4 MPa, and dehydration or chemical treatment give no relief. Screen it as follows (task 2026-10-21 Vigdis/Snorre compressors):

1. Express the limit as H2S in the gas for each machine pressure: `y_limit = 0.3 kPa / P` (24 ppm at 125 bara, 10 ppm at 300 bara, 100 ppm at 30 bara). Check
   both suction and discharge; the high-pressure machines go sour first because the ppm is constant while P rises.
2. Get y_H2S per machine from a process model with H2S as a trace component (the system is linear: run once and scale), or from the stream composition; use
   `y x P` (Dalton) for classification and the fugacity coefficient (`getFugacityCoefficient()`, 0.2-0.4 in dense injection gas) as sensitivity only.
3. When the H2S source is not in the model (e.g. degassing-tank gas recovered from a flare), carry it with a dilution share f (`y_mix = f x y_source`) and report the
   break-even f = `0.3 kPa / (y_source x P)` plus a Monte Carlo, instead of a single number.
4. Report in-situ pH of the condensed water (CO2 and H2S, salt-free) and the water dew point at the discharge; ISO 15156-2 Figure 1 region and the material check are for the materials group.
5. Adding a trace component to a model fluid: check the unit operations that rebuild streams by component index (a `ComponentSplitter` with split factors `[1]*(n-1)+[0]`
   removes the LAST component); insert the tracer before the last component and verify the downstream ppm is not zero.

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
â€” the M-506 analogue of `CO2CorrosionAnalyzer`:

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

**FeCO3 film feedback (closes the corrosionâ†”scaling loop):** when the aqueous phase
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
â€” re-run `chemicalReactionInit()` on the clone before flashing or the CO2-brine pH
comes out unphysically basic (~10). `setActualPH()` is read back via `getEffectivePH()`,
NOT `getCalculatedPH()`. Proven converging brine: CO2 0.10 / water 0.88 / Na+ 0.01 /
Cl- 0.01, `setMixingRule(10)`.

### Robust in-situ pH for investigations

`getpH()` on the aqueous phase (or `SystemInterface.getpH()`) returns the in-situ
pH. It now has a **built-in acid-gas dissociation fallback**: when the aqueous
phase has water + dissolved CO2/H2S but no explicit `H3O+` species (i.e.
`chemicalReactionInit()` was not run) or the electrolyte solver is unstable and
returns NaN at low pressure, `getpH()` estimates pH from the carbonic/sulfide
acid first-dissociation equilibria â€” `[H+]=sqrt(K1_CO2Â·C_CO2 + K1_H2SÂ·C_H2S + Kw)`
â€” instead of the old silent, unphysical `7.0`. CO2-saturated water â†’ pH â‰ˆ 3.9.
Force it explicitly with `getpH("acidgas")`. This is a **screening** estimate
(ignores bicarbonate buffering and salt-ion alkalinity); for a rigorous speciated
pH in a buffered brine still run `chemicalReactionInit()`.

For corrosion/scale investigations that must always return a finite, bounded,
**source-tagged** value with an explicit pCO2 basis, use
`RuëmyÚÚ$z{-®éÜj×6RÂ6÷W&6W5¶6VÆÅÒæÆVæwFƒ²†6R²²’°¢6÷•¶6VÆÅÕ·†6UÒÒ6÷W&6W5¶6VÆÅÕ·†6UÒæ6ÆöæR‚“°¢Ð¢Ð¢&WGW&â6÷“°¢Ð ¢ò¢¢&WGW&â6öæf–wW&VB6ö×öæVçB&FR6öç7FçG2'’6VÆÂ–â÷2¢ð¢V&Æ–2F÷V&ÆUµÕµÒvWE&FT6öç7FçG5W%6V6öæB‚’°¢F÷V&ÆUµÕµÒ6÷’ÒæWrF÷V&ÆU·&FT6öç7FçG2æÆVæwF…ÕµÓ°¢f÷"†–çB6VÆÂÒ²6VÆÂÂ&FT6öç7FçG2æÆVæwFƒ²6VÆÂ²²’°¢6÷•¶6VÆÅÒÒ&FT6öç7FçG5¶6VÆÅÒæ6ÆöæR‚“°¢Ð¢&WGW&â6÷“°¢Ð ¢ò¢¢&WGW&âÆFVçB†VB&Vf÷&RF†R†VB×7WÇ’Æ–Ö—B'’6VÆÂ–âröÓ²Wf÷&F–öâ—2æVvF—fR¢ð¢V&Æ–2F÷V&ÆUµÒvWEVæ6öç7G&–æVDÆFVçD†VEuW$ÖWG&R‚’°¢&WGW&âVæ6öç7G&–æVDÆFVçD†VEuW$ÖWG&Ræ6ÆöæR‚“°¢Ð ¢ò¢¢&WGW&â×VÇF—Æ–6F—fR†VBÖÆ–Ö—B66ÆR'’6VÆÂ–â³ÂÒ¢ð¢V&Æ–2F÷V&ÆUµÒvWD†VE66ÆR‚’°¢&WGW&â†VE66ÆRæ6ÆöæR‚“°¢Ð ¢ò¢¢&WGW&âv†WF†W"6ö×öæVçBFöæ÷"Æ–Ö—Bv27F—fR–âV6‚6VÆÂ¢ð¢V&Æ–2&ööÆVåµÒvWDFöæ÷$Æ–Ö—FVB‚’°¢&WGW&âFöæ÷$Æ–Ö—FVBæ6ÆöæR‚“°¢Ð¢Ð ¢ò¢ ¢¢6öç7G'V7Bâ–Ö×WF&ÆRG'’Ö‡–G&ö6&&öâööÂ×G&ç6fW"6Æ÷7W&Rà¢ ¢¢&Ò6ö×öæVçG2Væ—VRæW6–Ò6ö×öæVçBæÖW0¢¢&Ò6öVff–6–VçG2÷fW&ÆÂG&ç6fW"6öVff–6–VçG2–âÒ÷2ÂöæRW"6ö×öæVç@¢¢&ÒWf÷&F–öäVçF†Ç–W4¥W$¶r÷6—F—fRWf÷&F–öâ†VB&WV—&VÖVçG2–â¢ö¶rÂöæRW"6ö×öæVç@¢¢&Ò–çFW&f6Uv–GF„g&7F–öâ&W67&–&VB–çFW&f6Rv–GF‚F—f–FVB'’—RF–ÖWFW"Â–â³ÂÐ¢¢&ÒÖ†–×VÔ†VE7WÇ•uW$ÖWG&RÖ†–×VÒW‡FW&æÂ†VBf–Æ&ÆRFòæWBWf÷&F–öâ–âröÓ²÷6—F—fR–æf–æ—G¢¢F—6&ÆW2F†—2Æ–Ö—@¢¢ð¢V&Æ–2‡–G&ö6&&öåööÅG&ç6fW"…7G&–æuµÒ6ö×öæVçG2ÂF÷V&ÆUµÒ6öVff–6–VçG2ÂF÷V&ÆUµÒWf÷&F–öäVçF†Ç–W4¥W$¶rÀ¢F÷V&ÆR–çFW&f6Uv–GF„g&7F–öâÂF÷V&ÆRÖ†–×VÔ†VE7WÇ•uW$ÖWG&R’°¢–b†6ö×öæVçG2ÓÒçVÆÂÇÂ6öVff–6–VçG2ÓÒçVÆÂÇÂWf÷&F–öäVçF†Ç–W4¥W$¶rÓÒçVÆÂÇÂ6ö×öæVçG2æÆVæwF‚ÓÒ ¢ÇÂ6ö×öæVçG2æÆVæwF‚Ò6öVff–6–VçG2æÆVæwF‚ÇÂ6ö×öæVçG2æÆVæwF‚ÒWf÷&F–öäVçF†Ç–W4¥W$¶ræÆVæwF€¢ÇÂF÷V&ÆRæ—4f–æ—FR†–çFW&f6Uv–GF„g&7F–öâ’ÇÂ–çFW&f6Uv–GF„g&7F–öâÂãÇÂ–çFW&f6Uv–GF„g&7F–öââã ¢ÇÂF÷V&ÆRæ—4æâ†Ö†–×VÔ†VE7WÇ•uW$ÖWG&R’ÇÂÖ†–×VÔ†VE7WÇ•uW$ÖWG&RÂã’°¢F‡&÷ræWr–ÆÆVvÄ&wVÖVçDW†6WF–öâ€¢%ööÂ×G&ç6fW"7V6–W2Â6öVff–6–VçG2Â–çFW&f6RæB†VB7WÇ’×W7B&RfÆ–B"“°¢Ð¢f÷"†–çB’Ò²’Â6ö×öæVçG2æÆVæwFƒ²’²²’°¢–b†6ö×öæVçG5¶•ÒÓÒçVÆÂÇÂ6ö×öæVçG5¶•ÒçG&–Ò‚’æ—4V×G’‚’ÇÂF÷V&ÆRæ—4f–æ—FR†6öVff–6–VçG5¶•Ò¢ÇÂ6öVff–6–VçG5¶•ÒÂãÇÂF÷V&ÆRæ—4f–æ—FR†Wf÷&F–öäVçF†Ç–W4¥W$¶u¶•Ò¢ÇÂWf÷&F–öäVçF†Ç–W4¥W$¶u¶•ÒÂã’°¢F‡&÷ræWr–ÆÆVvÄ&wVÖVçDW†6WF–öâ€¢%ööÂ×G&ç6fW"6öVff–6–VçG2æBWf÷&F–öâVçF†Ç–W2×W7B&Rf–æ—FRæBæöææVvF—fR"“°¢Ð¢f÷"†–çB&Wf–÷W2Ò²&Wf–÷W2Â“²&Wf–÷W2²²’°¢–b†6ö×öæVçG5¶•ÒæWVÇ2†6ö×öæVçG5·&Wf–÷W5Ò’’°¢F‡&÷ræWr–ÆÆVvÄ&wVÖVçDW†6WF–öâ‚$GWÆ–6FRööÂ×G&ç6fW"6ö×öæVçB"²6ö×öæVçG5¶•Ò“°¢Ð¢Ð¢Ð¢F†—2æ6ö×öæVçG2Ò6ö×öæVçG2æ6ÆöæR‚“°¢F†—2æ6öVff–6–VçG2Ò6öVff–6–VçG2æ6ÆöæR‚“°¢F†—2æWf÷&F–öäVçF†Ç–W4¥W$¶rÒWf÷&F–öäVçF†Ç–W4¥W$¶ræ6ÆöæR‚“°¢F†—2æ–çFW&f6Uv–GF„g&7F–öâÒ–çFW&f6Uv–GF„g&7F–öã°¢F†—2æÖ†–×VÔ†VE7WÇ•uW$ÖWG&RÒÖ†–×VÔ†VE7WÇ•uW$ÖWG&S°¢Ð ¢ò¢ ¢¢WfÇVFR—&VB6ö×öæVçB6÷W&6W2f÷"öæRWVÆW"7FvRà¢ ¢¢&ÒG&ç7÷'B66WFVB6ö×öæVçB–çfVçF÷&–W0¢¢&Ò6V7F–öç2‡–G&öG–æÖ–26VÆÇ0¢¢&ÒWV–Æ–'&—VÕ7FFW2Æö6Â6÷W&6R×7FvRWV–Æ–'&—VÒfÆ6†W0¢¢&ÒF–ÖU7FW6V6öæG266WFVBWVÆW"7V'7FW–â6V6öæG0¢¢&WGW&â6÷W&6W2æB&FRÖÆ–Ö—BF–væ÷7F–70¢¢ð¢V&Æ–2&W7VÇB6Æ7VÆFR…GvôfÇV–D6ö×öæVçEG&ç7÷'BG&ç7÷'BÂGvôfÇV–E6V7F–öåµÒ6V7F–öç2À¢7—7FVÔ–çFW&f6UµÒWV–Æ–'&—VÕ7FFW2ÂF÷V&ÆRF–ÖU7FW6V6öæG2’°¢–b‡G&ç7÷'BÓÒçVÆÂÇÂ6V7F–öç2ÓÒçVÆÂÇÂWV–Æ–'&—VÕ7FFW2ÓÒçVÆÂÇÂF÷V&ÆRæ—4f–æ—FR‡F–ÖU7FW6V6öæG2¢ÇÂF–ÖU7FW6V6öæG2ÃÒã’°¢F‡&÷ræWr–ÆÆVvÄ&wVÖVçDW†6WF–öâ‚%ööÂG&ç6fW"&WV—&W2–çfVçF÷&–W2Â6VÆÇ2ÂfÆ6†W2æBf–æ—FR÷6—F—fRF–ÖR"“°¢Ð¢7G&–æuµÒæÖW2ÒG&ç7÷'BævWD6ö×öæVçDæÖW2‚“°¢–çEµÒ–æF–6W2ÒæWr–çE¶6ö×öæVçG2æÆVæwF…Ó°¢f÷"†–çB6öæf–wW&VBÒ²6öæf–wW&VBÂ6ö×öæVçG2æÆVæwFƒ²6öæf–wW&VB²²’°¢–æF–6W5¶6öæf–wW&VEÒÒ'&—2æ4Æ—7B†æÖW2’æ–æFW„öb†6ö×öæVçG5¶6öæf–wW&VEÒ“°¢–b†–æF–6W5¶6öæf–wW&VEÒÂ’°¢F‡&÷ræWr–ÆÆVvÄ&wVÖVçDW†6WF–öâ‚$Ö—76–ærööÂ×G&ç6fW"6ö×öæVçB"²6ö×öæVçG5¶6öæf–wW&VEÒ“°¢Ð¢Ð¢F÷V&ÆUµÕµÕµÒF&vWD6†ævW2ÒG&ç7÷'Bæ7&VFT‡–G&ö6&&öäWV–Æ–'&—VÔ–çfVçF÷'”6†ævW2†WV–Æ–'&—VÕ7FFW2Â6V7F–öç2“°¢–b‡F&vWD6†ævW2ÓÒçVÆÂ’°¢F‡&÷ræWr–ÆÆVvÅ7FFTW†6WF–öâ‚$‡–G&ö6&&öâööÂG&ç6fW"&WV—&W2G'’v2öö–Â–çfVçF÷&–W2æBWV–Æ–'&—VÒ"“°¢Ð¢F÷V&ÆUµÕµÕµÒ–çfVçF÷'’ÒG&ç7÷'BævWD–çfVçF÷'”¶r‚“°¢F÷V&ÆUµÕµÕµÒ6÷W&6W2ÒæWrF÷V&ÆU·6V7F–öç2æÆVæwF…Õ³5Õ¶æÖW2æÆVæwF…Ó°¢F÷V&ÆUµÕµÒ&FT6öç7FçG2ÒæWrF÷V&ÆU·6V7F–öç2æÆVæwF…Õ¶6ö×öæVçG2æÆVæwF…Ó°¢&ööÆVåµÒFöæ÷$Æ–Ö—FVBÒæWr&ööÆVå·6V7F–öç2æÆVæwF…Ó°¢f÷"†–çB6VÆÂÒ²6VÆÂÂ6V7F–öç2æÆVæwFƒ²6VÆÂ²²’°¢GvôfÇV–E6V7F–öâ6V7F–öâÒ6V7F–öç5¶6VÆÅÓ°¢F÷V&ÆRF–ÖWFW"Ò6V7F–öâævWDF–ÖWFW"‚“°¢F÷V&ÆRföÇVÖUW$ÖWG&RÒÖF‚å’¢F–ÖWFW"¢F–ÖWFW"òBã°¢F÷V&ÆR–çFW&f6Uv–GF‚Ò–çFW&f6Uv–GF„g&7F–öâ¢F–ÖWFW#°¢–b‚†F–ÖWFW"âã’ÇÂF÷V&ÆRæ—4f–æ—FR†F–ÖWFW"’ÇÂ‡6V7F–öâævWDÆVæwF‚‚’âã¢ÇÂF÷V&ÆRæ—4f–æ—FR‡6V7F–öâævWDÆVæwF‚‚’’’°¢F‡&÷ræWr–ÆÆVvÅ7FFTW†6WF–öâ‚%ööÂ×G&ç6fW"6VÆÇ2&WV—&Rf–æ—FR÷6—F—fRF–ÖWFW"æBÆVæwF‚"“°¢Ð¢f÷"†–çB6öæf–wW&VBÒ²6öæf–wW&VBÂ6ö×öæVçG2æÆVæwFƒ²6öæf–wW&VB²²’°¢–çB6ö×öæVçBÒ–æF–6W5¶6öæf–wW&VEÓ°¢F÷V&ÆR&FT6öç7FçBÒ6öVff–6–VçG5¶6öæf–wW&VEÒ¢–çFW&f6Uv–GF‚òföÇVÖUW$ÖWG&S°¢&FT6öç7FçG5¶6VÆÅÕ¶6öæf–wW&VEÒÒ&FT6öç7FçC°¢F÷V&ÆRö–Å&FRÒ&FT6öç7FçB¢F&vWD6†ævW5¶6VÆÅÕ³Õ¶6ö×öæVçEÓ°¢–çBFöæ÷"Òö–Å&FRâãò¢°¢F÷V&ÆRFöæ÷$Æ–Ö—BÒÔ…õ4õU$4Uôe$5D”ôâ¢–çfVçF÷'•¶6VÆÅÕ¶Föæ÷%Õ¶6ö×öæVçEÒò6V7F–öâævWDÆVæwF‚‚¢òF–ÖU7FW6V6öæG3°¢F÷V&ÆRÆ–Ö—FVE&FRÒÖF‚æ6÷•6–vâ„ÖF‚æÖ–â„ÖF‚æ'2†ö–Å&FR’ÂFöæ÷$Æ–Ö—B’Âö–Å&FR“°¢Föæ÷$Æ–Ö—FVE¶6VÆÅÒÃÒÖF‚æ'2†Æ–Ö—FVE&FR’ÂÖF‚æ'2†ö–Å&FR“°¢6÷W&6W5¶6VÆÅÕ³Õ¶6ö×öæVçEÒÒÆ–Ö—FVE&FS°¢6÷W&6W5¶6VÆÅÕ³Õ¶6ö×öæVçEÒÒÖÆ–Ö—FVE&FS°¢Ð¢Ð ¢F÷V&ÆUµÒVæ6öç7G&–æVDÆFVçD†VBÒæWrF÷V&ÆU·6V7F–öç2æÆVæwF…Ó°¢f÷"†–çB6VÆÂÒ²6VÆÂÂ6V7F–öç2æÆVæwFƒ²6VÆÂ²²’°¢f÷"†–çB6öæf–wW&VBÒ²6öæf–wW&VBÂ6ö×öæVçG2æÆVæwFƒ²6öæf–wW&VB²²’°¢Væ6öç7G&–æVDÆFVçD†VE¶6VÆÅÒ³Ò6÷W&6W5¶6VÆÅÕ³Õ¶–æF–6W5¶6öæf–wW&VEÕÐ¢¢Wf÷&F–öäVçF†Ç–W4¥W$¶u¶6öæf–wW&VEÓ°¢Ð¢Ð¢F÷V&ÆUµÒ†VE66ÆRÒæWrF÷V&ÆU·6V7F–öç2æÆVæwF…Ó°¢'&—2æf–ÆÂ††VE66ÆRÂã“°¢–b„F÷V&ÆRæ—4f–æ—FR†Ö†–×VÔ†VE7WÇ•uW$ÖWG&R’’°¢f÷"†–çB6VÆÂÒ²6VÆÂÂ6V7F–öç2æÆVæwFƒ²6VÆÂ²²’°¢F÷V&ÆRWf÷&F–öäFVÖæBÒÖF‚æÖ‚ƒãÂ×Væ6öç7G&–æVDÆFVçD†VE¶6VÆÅÒ“°¢–b†Wf÷&F–öäFVÖæBâÖ†–×VÔ†VE7WÇ•uW$ÖWG&R’°¢†VE66ÆU¶6VÆÅÒÒÖ†–×VÔ†VE7WÇ•uW$ÖWG&RòWf÷&F–öäFVÖæC°¢f÷"†–çB†6RÒ²†6RÂ#²†6R²²’°¢f÷"†–çB6ö×öæVçBÒ²6ö×öæVçBÂæÖW2æÆVæwFƒ²6ö×öæVçB²²’°¢6÷W&6W5¶6VÆÅÕ·†6UÕ¶6ö×öæVçEÒ£Ò†VE66ÆU¶6VÆÅÓ°¢Ð¢Ð¢Ð¢Ð¢Ð¢&WGW&âæWr&W7VÇB‡6÷W&6W2Â&FT6öç7FçG2ÂVæ6öç7G&–æVDÆFVçD†VBÂ†VE66ÆRÂFöæ÷$Æ–Ö—FVB“°¢Ð§Ð