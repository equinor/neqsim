---
name: neqsim-flow-assurance
description: "Flow assurance patterns for NeqSim. USE WHEN: predicting hydrate, wax, asphaltene, CO2/H2S corrosion (NORSOK M-506), mineral scale and brine mixing, valve scale drift and remediation, sulfur S8 deposition, metal-loss screening, pipeline hydraulics, DNV-RP-F109/F105/F104/F110/F114 screening, water hammer, slug flow, thermal analysis or inhibitor dosing. Code patterns plus the governing standards for every threat."
last_verified: "2026-11-27"
---

# Flow Assurance Analysis with NeqSim

Consolidated guide for all flow assurance threats — hydrate, wax, asphaltene, corrosion,
hydraulics, water/liquid hammer screening, slugging, and thermal management — with
NeqSim code patterns.

Use the `runPipeline` analysis `wellToTopsideSlugControl` for the published
well-to-topside anti-slug replay. It reuses the canonical choke, two-fluid pipe,
three-phase separator and controller APIs and returns both control modes with
time series. Treat it as numerical reference evidence, not field validation.

For the public Tengesdal severe-slugging gate on the unsplit route, inspect
`TwoFluidMassBalanceReport.getRejectedAttempts()` and `getModelEvaluations()` after every accepted
observation interval. Use complete cycles from the final 300 s: require at least two pressure and
liquid-production cycles, stable cycle amplitude/period, repeatable pressure/liquid phase lock and
the unchanged experimental pressure-amplitude, inlet-pressure-period, hold-up, inventory and
conservation gates. Run `16 cells / 0.1 s`, `16 / 0.05 s` and `24 / 0.05 s` before checking
refinement. The current matrix reaches 600 s but has zero complete settled pressure and liquid cycles
on every discretization; the 24-cell run also records two rejected attempts. Treat the experimental
metrics as unresolved, not passed by a startup extremum or enforced sign. Conservative moving
slug/film geometry is accepted transactionally by the unsplit route and has closed-pipe
four/eight-cell and one/two-step conservation checks. Accepted tracked cells can also use the
reconstructed body/film friction owner while untracked cells retain ordinary friction; the 0.1 s
16/24-cell startup matrix checks rollback, passive impulse, mass closure and time-step refinement.
Run the opt-in tracking-enabled five-second 16/0.1, 16/0.05 and 24/0.05 matrix as the next hosted
numerical gate. Do not advance to tracking-enabled 180/600 s publication or the experimental cycle
gates until it passes; completion and an enforced sign alone are not physical qualification.

> A complete study report with pipeline drawings (route, profiles, hydrate margin, threat matrix,
> validation) is produced by `neqsim flow-assurance-report` — see the
> `neqsim-flow-assurance-study-report` skill. This skill stays the calculation reference.
> A whole subsea SURF network (templates, injection, risers, umbilical, concept comparison) is
> generated, solved and documented by `neqsim surf-study` — see the `neqsim-surf-design-study` skill.
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
  (for `TwoFluidPipe` no-touch times use `calculateColdestSectionHydrateCooldownTime()` after `run()`;
  `calculateHydrateCooldownTime()` starts from the inlet temperature and overstates the time for the riser base)

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
**chemical** answer — which product, minimum effective dose, cocktail compatibility, H2S/O2
scavenger sizing, demulsifier vs oil-in-water spec, and chemical root-cause of a deposit —
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

All SRK-CPA solver variants (`SystemSrkCPAstatoil`, `...FullyImplicit`, `...Reduced`, `...Broyden...`,
`...Anderson...`) and all SRK electrolyte-CPA systems use the same CPA hydrate cavity model
(`ComponentHydrateGF`), so they give the same hydrate temperature; pick the solver for speed. Before
21 Nov 2026 the variants silently used the PVTsim parameters (up to 0.44 K different). PR-CPA and UMR-CPA still
use PVTsim parameters. Check with `((PhaseHydrate) fluid.getPhases()[4]).getHydrateModel()`.

When no ions are present, prefer `SystemSrkCPAstatoil` over `SystemElectrolyteCPAstatoil`: same hydrate
temperature, 5–10× faster flash (e-CPA hydrate point 1–3 s, measured 21 Nov 2026).

`SystemPitzer` handles MEG (+ NaCl) through a fitted dataset (MEG ≤ 17.6 mol/kg). With N2 present the Pitzer
hydrate search cannot go below 278.12 K, so for MEG-inhibited low-temperature cases use e-CPA.

CPA or a consistent activity-based aqueous model is needed for water–hydrocarbon hydrate modeling.
**When dissolved salts / electrolytes are present, use `SystemElectrolyteCPAstatoil` or a
parameterized `SystemPitzer`** so the salt
thermodynamic (hydrate-suppression) effect is captured — plain
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

Call `setHydrateCheck(true)`. Since 21 Nov 2026 `hydrateFormationTemperature()` and
`hydrateFormationPressure()` add the hydrate phase themselves if it is missing, but older releases fail
(no hydrate phase) and any wrapper that swallows the exception reports NaN. The fluid must
contain `water`. `HydrateRiskMapper` (and MCP `runFlowAssurance` `hydrateRiskMap`) now sets the
flag itself and returns `RiskLevel.UNKNOWN` / `RESULT_NOT_AVAILABLE` with `failureReasons` when
no equilibrium temperature exists — never treat an unavailable hydrate temperature as safe.

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

### Host inhibitor-availability gate (check BEFORE selecting an inhibitor strategy)

A MEG (or methanol) dose that NeqSim calculates is only a strategy if the host can deliver it. Before choosing
continuous MEG for a tie-back or export line, establish from host drawings, equipment lists and the operator:

1. **MEG regeneration/reclamation plant** at the host or on the receiving route (lean MEG capacity, rich MEG handling,
   water load, salt/solids handling). Without one, MEG is single-use and continuous injection is not a strategy.
2. **Existing continuous MEG injection** that the new line can share (storage, injection pumps, a MEG line or umbilical
   service line to the template, spare capacity in m3/h and storage days).
3. **Batch (shut-in/restart) injection** capability: tank volume, pump, supply route (umbilical chemical line, riser-side
   injection point), and where used MEG goes afterwards.

If (1) and (2) are not confirmed, record a data gap and treat continuous MEG as **not available**; do not size a loop on
it. **If MEG is selected anyway (continuous, or batch at shut-in), evaluate the host topside for it:** utilisation of the
existing capacity (regeneration, storage, pumps, MEG line/umbilical service line) against the new demand, or the
installation required when nothing exists (regeneration/reclamation package sized on lean MEG rate, rich MEG and water
load, storage days, injection pumps, heater/reboiler duty and power, deck space, weight, utilities, tie-in points, rough
cost class), and show it on the tie-in PFD/P&ID and in the host debottlenecking register. Screen the alternatives instead and report which host capability each one depends on: hydrate-free margin in flowing
operation (including the minimum rate that keeps arrival temperature above the hydrate temperature, only relevant when
free water is present), depressurisation to below the hydrate pressure at seabed temperature, displacement/flush with dead
oil or diesel before a planned shut-in, insulation/heat tracing and cooldown (no-touch) time, and batch MEG/methanol/LDHI at
shut-in and restart (volume from the water in place and the settle-out pressure). State the data needed to confirm each.

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
`neqsim.process.chemistry.hydrate.ThermodynamicHydrateInhibitorPerformance` — see
`neqsim-production-chemistry`. Use the CPA curve above for the design concentration and the
chemistry model for the rate.

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
waxSim.setTemperaturesAndPressures(new double[] {333.15, 313.15, 293.15, 273.15},
    new double[] {5.0, 5.0, 5.0, 5.0}); // K, bara
waxSim.runCalc(); // run() only prepares the operations object
double[] waxFractions = waxSim.getWaxFraction(); // wax mass / feed mass
```

The fluid needs `addSolidComplexPhase("wax")`, `setMultiphaseWaxCheck(true)` and
`getWaxModel().addTBPWax()`; for named n-paraffin feeds see the `neqsim-wax-calculations` skill
("Named n-paraffin feeds").

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

### Calibrated onset envelope, blends and gas injection (`AsphalteneSolidModel`)

With a measured onset, use the calibrated solid model (Nghiem and Coombe). It anchors the solid
asphaltene fugacity at the measured onset and uses size-asymmetry k_ij (large for C1/N2/CO2, about 0 for
heavy fractions), so one host calibration predicts blends and gas injection:

```java
AsphalteneSolidModel model = new AsphalteneSolidModel(hostOil); // one component named *asphalten*, SRK/PR, classic
model.calibrate(363.15, 250.0);        // measured upper onset (K, bara)
model.calibrateLowerOnset(80.0);       // optional: lower onset below the bubble point
AsphalteneSolidModel.OnsetResult env = model.onsetPressures(363.15, 500.0, 20.0, 5.0);
double blendOnset = model.onsetPressures(blendFluid, 363.15, 600.0, 20.0, 5.0).getUpperOnsetPressure();
double fraction = model.precipitatedFraction(hostOil, 363.15, 200.0);
```

Gotchas:

- Do not tune k_ij against the database solid reference to hit a measured onset: it needs negative
  asphaltene-methane values and then predicts that condensate stabilises the oil (wrong sign). Calibrate
  the solid reference instead (measured 2026-11-22, see `docs/pvtsimulation/flowassurance/asphaltene_modeling.md` section 9).
- Always check `isAsphalteneLiquidSplitDetected()`; if the EoS splits the asphaltene into its own liquid use
  omega 1.2-1.5 for the solid model. (Before 2026-11-22 the Pedersen correlation gave omega = 2.5 at
  1000 g/mol through a natural-log error; it now gives 1.18 at 1.10 g/cm3. Tc also gained its missing
  density term and Pc its atm-to-bar conversion, so Pedersen onsets from before that date differ.)
- `PedersenAsphalteneCharacterization.calculateOnsetPressure` raises `IllegalStateException` when the fluid
  is already split at the start pressure. `AsphalteneOnsetFunction` (fitting) instead returns finite
  penalties: the start pressure if already unstable, the minimum pressure if no onset is found.
  `BINARY_INTERACTION` and `MOLAR_MASS` fits now act (both had no effect before 2026-11-22); `MOLAR_MASS`
  holds the asphaltene mass and is cubic-only; `ASSOCIATION_PARAMETERS`/`COMBINED` need CPA, energies in J/mol.
- `fitKijExponent` needs a blend/gas-injected fluid or another temperature; for the calibration fluid at
  the calibration temperature it throws (the objective is flat there).

### Detection, onset flashes and screening (changed 2026-11-22)

- Asphaltene components: `AsphalteneComponents.isAsphaltene(name)` (contains "asphalten" or starts with
  "asph_"); `addDistributedAsphaltene` now names slices `Asphaltene_1_PC`... from a mass-conserving gamma
  split. Asphaltene-rich phase = more than 50 wt % asphaltene (was mol %).
- `AsphalteneOnsetPressureFlash` also finds the lower onset (`getLowerOnsetPressure()`); unknown units
  (including bare "psi") throw. `AsphalteneStabilityAnalyzer.calculateOnsetPressure(T, startP)` starts at
  the given pressure; the envelope has 4 rows (T, upper, lower, bubble point at T); the assessment combines
  SARA and thermodynamic risk.
- De Boer boundaries are ordered (no-problem <= slight <= severe); `AsphalteneStabilityAnalyzer.deBoerScreening`
  delegates to `DeBoerAsphalteneScreening`. Risk index for 350/150 bara at 750 kg/m3 is 200/120 = 1.667.

### Flory-Huggins (`FloryHugginsAsphalteneModel`)

```java
FloryHugginsAsphalteneModel fh = new FloryHugginsAsphalteneModel(liveOil, 373.15);
fh.setAsphalteneWeightFraction(0.03);
fh.calibrateToOnsetPressure(373.15, 250.0); // with a measured onset; otherwise calibrateCorrelation(T) (heuristic prior)
fh.setSolubilityParameterMethod(FloryHugginsAsphalteneModel.SolubilityParameterMethod.EOS); // optional
double onset = fh.calculateOnsetPressure(373.15);
```

Onsets computed before 2026-11-22 are unreliable: the Newton solver for the solubility limit did not
converge (phi of about 1e-19, residual about -45). The heuristic prior gives 12.4 % AARD, all
under-predicted, on the 7 benchmark cases (2026-11-22); its fixed 0.35 MPa^0.5 margin sets the result.

### SARA characterization with CPA resin

`AsphalteneCharacterization.addAsphalteneComponents(fluid, nC7plus)` needs `setC7plusProperties` first,
adds `asphaltene` and a `Resin_PC` pseudo-component (2B sites in SRK-CPA), and after
`fluid.setMixingRule(10)` call `applyCrossAssociation(fluid)` to set the asphaltene-resin bond. Default
asphaltene association volume is 0.005 (database value).

### Regular-solution model regressed to onset data (`FloryHugginsAsphalteneModel`)

Needs no asphaltene component in the fluid: oil density from any EOS gives the oil solubility parameter
(`delta_L = 0.017347 rho + 2.904`), and the asphaltene solubility parameter is the fitted quantity.

```java
FloryHugginsAsphalteneModel fh = new FloryHugginsAsphalteneModel(liveOil, 373.15);
fh.setAsphalteneWeightFraction(0.03);                       // n-C7 asphaltenes, weight fraction
double deltaA = fh.calibrateToOnsetPressure(373.15, 300.0); // one measured AOP (K, bara), reproduced exactly
// or several temperatures: fits delta_a(Tref) and d(delta_a)/dT
FloryHugginsAsphalteneModel.OnsetFitResult fit = fh.fitToOnsetData(new double[] {333.15, 353.15, 373.15},
    new double[] {340.0, 318.0, 300.0});
double upper = fh.calculateOnsetPressure(373.15, 500.0, 1.0);       // (T, start P, min P)
double lower = fh.calculateLowerOnsetPressure(373.15, 500.0, 1.0);  // NaN if it does not redissolve
```

- Without onset data (`configureFromAPIGravity` + `configureFromSARA` + `calibrateCorrelation`) the result is a
  screening estimate: `calibrateCorrelation` assumes a stability margin at reservoir conditions, it is not a fit.
  Do not combine it with `calibrateToOnsetPressure`/`fitToOnsetData`.
- The fitted `delta_a` is tied to the EOS density; recalibrate after retuning the fluid.
- Choosing between calibrated models: `AsphalteneSolidModel` when the fluid has an asphaltene component and
  blends/gas injection matter (k_ij carries the composition effect); `FloryHugginsAsphalteneModel` when only a
  characterized oil without asphaltene component and AOP data at one or more temperatures exist;
  `AsphalteneOnsetFitting` for CPA association parameters. No head-to-head accuracy comparison on measured
  data has been made (2026-11-23); the Flory-Huggins regression is verified on synthetic onsets only.

## 3b. Naphthenates and Organic Acid Soaps

`NaphthenatePrecipitationModel` (`neqsim.pvtsimulation.flowassurance`): equilibrium of monoprotic naphthenic
acids (TAN) and ARN tetraacids with calcium in produced water. pH is given (`setWaterPH`) or calculated from
CO2 partial pressure and bicarbonate (`setCO2PartialPressure`), which captures the pH rise on depressurisation.

```java
NaphthenatePrecipitationModel m = new NaphthenatePrecipitationModel();
m.setWaterCut(0.5);
m.setOilDensity(850.0);                         // kg/m3
m.setTotalAcidNumber(1.0);                      // mg KOH/g
m.setAcidMolarMass(300.0);                      // g/mol
m.setArnConcentration(10.0);                    // mg/kg oil
m.setWaterComposition(1000.0, 10000.0, 500.0);  // Ca, Na, HCO3- mg/L
m.setTemperature(333.15);
m.setCO2PartialPressure(0.2);                   // bara, e.g. yCO2 * P of the separator gas
m.run();
m.getWaterPH(); m.getRisk();                    // NO_RISK, LOW_RISK, SOAP_RISK, ARN_DEPOSIT_RISK
m.getPrecipitatedArnNaphthenate();              // kg/m3 oil
m.getPrecipitatedCalciumSoap();                 // kg/m3 oil
m.calculateArnOnsetPH(); m.calculateCalciumSoapOnsetPH();
```

- Defaults for log P, pKsp (soap) and pK (ARN) are engineering defaults anchored to typical onset pH, not
  published constants. Uncalibrated results are for ranking and sensitivity; `isCalibrated()` is false and
  the output carries a warning. State this in any report.
- Calibrate with `NaphthenateModelFitting`: `addObservation(scenario, ObservationType, measured, stdDev)`,
  `addFitParameter(Parameter)`, `solve()`, `applyTo(model)`. Observation types: `AQUEOUS_ORGANIC_ACID` (mg/L),
  `PRECIPITATED_CALCIUM_SOAP` (kg/m3 oil), `PRECIPITATED_ARN_FRACTION`, `ARN_ONSET_PH`,
  `CALCIUM_SOAP_ONSET_PH`, `WATER_PH`. pKa and log P are not separately identifiable from partition data:
  fit one. `getStandardError` returns NaN for a parameter the data do not determine.
- Equilibrium potential only (no kinetics or deposition rate); only calcium forms solids.
- Guide: `docs/pvtsimulation/flowassurance/naphthenate_precipitation.md`.

## 4. Pipeline Hydraulics

### Retrograde condensation in long gas lines (shutdown, depressurisation, restart)

For a rich gas line taken below its cricondenbar (settle-out, blowdown, hold, packing, restart) use
`CompositionalLinePackPipe` with `RetrogradeDewPointScanner` and `DewPointGuardedOutletController`
([docs](../../../docs/process/compositional-linepack-pipe.md)). It conserves component masses and energy and
recovers the phase split from NeqSim flashes, so condensate forms and revaporises with the EOS.

Liquid movement: use `setLiquidTransportModel(LiquidTransportModel.STRATIFIED_TWO_FLUID)` for a predicted film
velocity (Taitel-Dukler combined momentum equation with the `TwoFluidPipe` closures, level gradient for pooling,
entrainment above the Kelvin-Helmholtz limit). Also run the two bounds (`BOUNDED_SLIP` with
`setLiquidSlipFactor` 0 and 1) and report all three. Measured 2026-11-27 against converged `TwoFluidPipe`
steady states on 18 cases: holdup ratio 0.89–1.10 in stratified flow (7 cases), 0.50–0.92 where
`TwoFluidPipe` classifies slug (8), 1.43–1.81 in annular (3). Do not quote slug or annular holdup from it.

```java
CompositionalLinePackPipe pipe = new CompositionalLinePackPipe("export", feed); // feed: composition, kg/s, P, T
pipe.setLength(707e3); pipe.setDiameter(1.013); pipe.setNumberOfSections(100);
pipe.setElevationProfileFromPoints(chainageM, elevationM);   // after setLength/setNumberOfSections
pipe.setHeatTransferCoefficient(5.0); pipe.setConstantSurfaceTemperature(278.15);
pipe.initializeUniform(130.0, 283.15);                      // or pipe.run() for the steady flowing state
pipe.setOutletMassFlowRate(400.0); pipe.runTransient(3600.0, UUID.randomUUID());
double liquidKg = pipe.getLiquidInventory("kg");            // "kg", "tonnes", "m3"; other units throw
```

- **Restart without liquid at the receiving end.** Feeding the guard only the feed cricondenbar is not
  enough: revaporised condensate leaves C3+-rich gas whose dew point is far higher. Measured 2026-11-26 on a
  707 km rich-gas case: about 130 bara local against 103 bara feed. A fixed cricondenbar + 2 bar trigger let
  170–180 t of liquid out under the no-slip bound; the local-dew-point guard let none out. Run both
  `setLiquidSlipFactor(0)` and `(1)` and report liquid delivered from the no-slip run.
- **Enable `TwoFluidPipe.setIncludeMassTransfer(true)` before using it to find in-line condensation.**
  With the default frozen phase composition, steady initialisation of a cold 251 km blowdown section returned
  single-phase gas where the EOS gives retrograde liquid. With mass transfer on, the same case gave 1,169 t
  (max holdup 2.7 %, annular/stratified) against 1,586 t of accumulated, immobile liquid in
  `CompositionalLinePackPipe` (2026-11-26). Use `TwoFluidPipe` (mass transfer on) for slip, holdup and
  regime at a flowing state; use `CompositionalLinePackPipe` for the hours-to-days sequence. For pressure
  drop the two agreed within 1.4–2 % on the same 707 km route.
- **Blowdown from the receiving end delivers liquid.** On the 707 km case the two-fluid transport sent
  1,177 t of condensate through the outlet during blowdown to 80 bara average (bounds: 0 immobile, 2,072 t
  no-slip; 2026-11-27). Report it; the guard only protects the restart.
- **`TwoFluidPipe` transient cannot run this sequence yet.** Probed 2026-11-27 with a closed inlet and a
  130→30 bara outlet ramp: it stopped at 10 s (time step collapse, 5 km) and at 180 s (outlet backflow without
  an outlet composition, 50 km). Use it for steady cross-checks only.
- Equilibrium is instantaneous, so "line liquid-free" times are optimistic. Slug flow, annular films and
  terrain slugging are not modelled, and nothing here is validated against measured pipeline data.


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

> **PATTERN — tubing outflow (VLP) and gas-lift need on a heavy-oil tie-back.** Put the **bottom-hole**
> pressure on the inlet stream (`P_bh = P_res - q_liq / PI`), set `setLength(MD)`, `setElevation(+TVD)`,
> `setAdiabatic(true)` and read the outlet pressure as the wellhead pressure; add lift gas as extra gas in the
> stream composition (bottom injection slightly over-states the lift). A 24 API oil (10 cP at 85 C) with 2000 m TVD
> has a hydrostatic head of about 170 bar, so a 205 bar reservoir cannot reach a 40 bar arrival without lift: sweep
> water cut x rate x lift GLR once, tabulate the WHP margin against `arrival + line dP + choke`, and read the
> deliverable rate per well and the GLR needed from the table (it falls from about 2.5 kSm3/d per well at 5 % water cut
> to zero near 85 %). Every `None`/exception in the table is the deliverability limit above, not a failure.
> `getMixtureViscosityProfile()` / `getSegmentMixtureViscosity()` return **cP**, not Pa.s (do not multiply by 1000).

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

> **`TwoFluidPipe` steady-state usage.** Gate every result on the complete
> immutable `SteadyStateConvergenceReport`, not a stationary pressure trace or
> iteration count. Require `isConverged()`, inspect the explicit termination
> reason, and retain pressure-momentum, pressure-update, total-liquid-holdup,
> water/oil-split, thermodynamic-property, and total-pressure-drop residuals
> against the report's unchanged tolerance. Pressure-floor and wall-clock
> termination are non-converged. Repeat mesh sensitivity for the actual
> geometry; no fixed cell length is universally qualified.

> **`TwoFluidPipe` run time (measured 2026-11-22, revision a2399c6 before and after
> the closure-reuse change; 5.4 km 8-inch three-phase flowline and riser at 60 and
> 120 cells, 20 km 16-inch gas-condensate line at 100 cells; OpenJDK 21, one
> thread).** Steady `run()` takes 1.23 s at 60 cells and 4.14 s at 120 cells
> (was 1.60 and 5.60 s); 60 s of transient on the 60-cell line takes 13.6 s
> (was 24.2 s), with identical profiles. Equilibrium flashes are now the largest
> remaining cost (`TPflash` is 48% of that transient case and 69% of the gas-condensate
> steady solve), so run time scales with cells x flash cadence x fluid complexity. Budget
> studies on that basis: screen sensitivities with `PipeBeggsAndBrills`
> (0.04 s per 100 increments on the same feed) and keep `TwoFluidPipe` for the
> cases that need section-resolved holdup, inventory or transients. The cadence
> setters are `setSteadyStateFlashInterval(int)` (default 3) and
> `setThermodynamicUpdateInterval(int)` (default 10); on that transient an interval
> of 20 saved 27% of the transient time and moved the inlet pressure by 0.14 bar
> after 60 s, so a longer interval is a model change to check per case. Details and
> the two benchmark entry points:
> [TwoFluidPipe model, run time](../../../docs/process/TWOFLUIDPIPE_MODEL.md#run-time-and-where-it-goes).

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
> inclination and scales with `sin(theta)`. The compact public 3 km,
> 10-degree uphill gas/oil/water fixture is annular with dispersed oil and water
> and converges on 30 and 60 cells without oil-water slip, with 0.922%
> arrival-pressure and 0.138% mean-liquid-holdup sensitivity (rerun 2026-11-15)
> and every final report residual below `1e-4`. This is numerical verification, not
> experimental qualification. The historical 73.8 km / 15 m3/hr free-water
> input is unavailable, so the earlier 4,078-iteration / 1,200 s report cannot
> be reproduced or claimed fixed. Never generalize the compact result to that
> unavailable case.

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
>
> **Annular friction can use the flowing (no-slip) density.** In annular flow the film
> moves several times slower than the mixture, so the holdup-weighted density over-charged the
> wall by `rho_m / rho_f` (1.05 on a lean high-pressure line, 1.3 to 2.5 on a rich low-pressure
> line). Select `setAnnularFlowingDensityFriction(true)` for this comparison. Holdup weighting is the default.
> Measured 2026-11-25 against OLGA 2025.1.0, same fluid, geometry and boundaries, inlet pressure
> for a fixed outlet (OLGA / holdup-weighted / flowing density, bara): rich gas with 38 % liquid
> by mass on 15.5 km x 285.7 mm with a 121 m riser and 13 bara at the outlet, 18 kg/s
> 26.49 / 30.95 / 26.05, 37 kg/s 49.71 / 59.27 / 47.22, 55 kg/s 71.88 / 83.97 / 67.86; lean
> gas-condensate on 30 km x 355 mm with 80 bara at the outlet 113.85 / 114.04 / 112.44. On ΔP
> that is 3 to 7 % BELOW OLGA (non-conservative: add a margin) where the earlier form was 21 to
> 33 % above on the rich line and 0.6 % above on the lean line. Annular holdup is NOT improved
> (−38 %, +12 %, +48 % and −1 % against OLGA on those four cases); do not quote it on a rich
> low-pressure line.
>
> **With a prescribed outlet pressure the feed split is flashed at the solved inlet pressure.**
> The inlet stream pressure is only a starting value and no longer changes the answer; before
> 2026-11-25 it set the gas/liquid split carried down the line (lean line above: mean holdup
> 0.021 to 0.009 for stream pressures of 90 to 140 bara). There is no need to iterate the stream
> pressure to the solved inlet pressure for a steady solve. A transient continued from that
> solution uses the current inlet-cell pressure and feed temperature and composition for the split.
> For separately metered phases, configure the connected stream with the intended phase proportions
> and select `setUseStreamPhaseSplit(true)` before the steady run.
> For the 39-cell rich and lean gas transient hand-offs, IMEX pressure correction with interfacial pressure
> and coupled pressure–momentum passed 1 s versus 0.1 s reporting-interval checks. Compare the first-cell
> pressure history and phase balances. The 60 s runs still drift from the steady pressure.

> **The historical 73.8 km comparison is not a public qualification basis.**
> Its complete input and measurement package is unavailable in the repository,
> so earlier reported pressure-drop and holdup deviations are retained only as
> repair history. Do not route or certify a study from those values. Use the
> reproducible public evidence and explicit failed/unsupported rows in the
> TwoFluidPipe evidence matrix.

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
> **Screening a gas-oil-water riser.** `evaluateSevereSluggingSystem(riserBase)` returns
> `NOT_VALIDATED_THREE_PHASE` when a section holds both oil and water. Call
> `evaluateSevereSluggingSystem(riserBase, 0.89, 0.0, true)` to evaluate the Taitel condition with the
> in-situ oil-water mixture density of the riser column, and report `isLiquidMixtureBasis()` with the
> result: it has no three-phase validation and does not cover water collecting at the riser base. The
> stratified-feeder check still applies, so run it at the turndown rates. Measured 2026-11-22 on a
> 15 km, 12-inch flowline with a 430 m riser at 73 bara: slug-flow feeder at 300 and 150 t/h (not
> applicable); stratified feeder and unstable at 75 and 30 t/h (critical top pressure 494 and
> 800 bara).
>
> **The synthetic three-phase riser handoff is a narrower qualified lane.** With phase transfer
> disabled, its steady gas/oil/water mass-flow profiles are flat, dispersed oil-water drag uses
> Hinze droplet area, and the unchanged-boundary 60 s replay keeps accepted phase outlet transfer
> nonnegative on 60/120 cells and 0.25/0.125 s reporting steps. Check the phase profiles and
> inventory ledger before applying a choke move. A pressure-coupled 65% to 58% choke step now keeps
> riser water outflow positive for 150 s; the earlier zero-water intervals came from an oil-water
> regime flip, not from physical redistribution (`neqsim-dynamic-simulation`, equinor/neqsim#5046). Do not generalize this numerical initialization
> result to slug statistics, separator control or field validation.
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
> The 100 W/m finite-heat 780 s rich-gas cycle is numerical mesh/time evidence. It
> retains the requested dense EOS root across the cubic model's diagnostic
> $V/B=1.75$ label transition while positive-compressibility, Jacobian and
> energy-admissibility guards remain active. Do not clip composition or change
> the configured root to manufacture a restart result.
> The opt-in hydrostatic-pool mode uses volume-preserving inclined-cell area,
> retains representable trace-wedge leading depth without a liquid floor, reports equilibrium
> appearance and any still-sub-resolution stratified trace fallback,
> and can retain an explicitly initialized wall profile across event intervals.
> Use `HydrocarbonPoolTransfer.forStratifiedFlow(...)` for moving circular-stratified states.
> Its local holdup chord preserves total area and component sources under subdivision of a
> uniform physical state. Independent stationary puddles in every numerical cell are a
> different area model and introduce a cell-length kinetic dependence during flow. Preserve
> the historical hydrostatic-cycle failure; the flowing alternative does not validate it.
> Mesh refinement must retain physical terrain and outlet breakpoints. The wall-coupled
> 12/24-cell cycle fixes its final 1250 m at -333.5 m on both meshes; the earlier
> "last three cells" rule compared different pipes. Retain the 10% peak/final inventory
> gates and inspect the executed hydrocarbon-pool-transfer receipt. Maximum single-cell
> interface area is extensive, not a convergence target. Independent mesh/time, rate/thermal
> and moving-boundary conservation evidence is still required for coupled pigging.
> A conservative `CONSTANT_PRESSURE` inlet now reflashes its explicit or connected external
> reservoir and resolves signed phase flow with acoustic impedance, carrying the same donor through
> mass, named-component and enthalpy ledgers. Start a restart ramp from
> `getInletFacePressure("Pa")`, not the first cell-centre pressure.
> An explicitly isolated reservoir reuses one flash across internal stages at each pressure;
> `getPressureInletFlashCount()` verifies that safeguard. A live connected stream remains uncached.
> The declared full cycle closes on 12/24 cells and 0.5/0.25 s intervals, but one small unfiltered
> opening reversal and the absence
> of independent public surge data keep physical restart and distributed hydraulic pigging open.
> Record refill/valve-opening pressure windows with
> `TwoFluidPipeReport.analyzePressureTrace(...)`; its unfiltered ringing metrics are numerical
> diagnostics, not an independently validated surge limit.
>
> **Ideal-gas pig hydraulics is a separate reduced-order route.**
> `IdealGasPiggingModel` couples `PigMotion` to conservative upstream/downstream
> calorically perfect gas inventories and reuses `IdealGasReleaseModel` for
> bidirectional choked/subcritical bypass. Use it only when two well-mixed gas
> control volumes are an explicit approximation. Inspect total-mass and
> combined-energy residuals, timestep refinement, numerical safeguards and the
> launcher/receiver ledger. The public 62 m/52 mm Hendrix geometry is
> provenance, not experimental trajectory validation; other example inputs are
> synthetic. `PigMotion.advanceToPosition` supplies reversal-safe first-crossing
> timing for known launcher, receiver, sensor or geometry coordinates; it does not
> define contact or geometry physics. Do not route it as distributed `TwoFluidPipe` coupling,
> pressure-wave, swept-liquid, receiver-capacity, residual-film,
> physical-restart, severe-slugging, pig-train or field/commercial evidence.
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
> A negative prescribed inlet rate uses the first pipe cell as the outflow donor;
> it must not inherit the external feed phase split. Review total phase momentum
> and pressure traction separately through
> `getLastBoundaryPhaseMomentumFluxes()` and
> `getLastBoundaryPhasePressureForces()`. Those arrays qualify the external
> boundary contract only, not a complete momentum residual or physical restart.
>
> **Two severe-slugging screens exist; pick by question.**
> `pipe.evaluateSevereSluggingSystem(riserBaseSection)` is the quasi-steady Taitel
> criterion on a solved `TwoFluidPipe` profile (stable/unstable, pressure margin in
> Pa). `SevereSlugAnalyser` (`neqsim.process.measurementdevice.simpleflowregime`) is
> a lumped dynamic model that also returns the riser-base pressure swing
> (`getPressureAmplitude()` in bar, `getSlugValue()` dimensionless). Gotchas for the
> analyser: flowline `angle` is the **downward** inclination in degrees and must be
> positive; `usg` is defined at `getGasReferencePressure()` (1.0e5 Pa by default, the
> stream pressure for stream-based constructors); pressures are absolute Pa;
> statistics use the second half of `simulationTime`, which must cover at least one
> cycle (order `(H + alpha*L)/usl`). The slug value is normalised by absolute
> pressure, so at high pressure judge severity from the amplitude against the riser
> head `rho_L*g*H`. Both tools lump or reject oil–water: the default Taitel call keeps
> `NOT_VALIDATED_THREE_PHASE` and adds an all-oil/all-water bracket
> (`getThreePhaseBound()`, `getCriticalTopPressureLowerBoundPa()` /
> `...UpperBoundPa()`); report an `INCONCLUSIVE` bracket as inconclusive, never as
> stable. See `docs/process/severe-slug-analyser.md`.
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
> off to no slip once the liquid disperses above a mixing Froude number of about
> 3; the previous form cut off at Fr = 2 and under-predicted water holdup badly.
> The Froude velocity is the bulk liquid velocity in stratified and slug flow
> and the total volumetric flux in annular, churn, bubbly and mist flow, the
> same mixing velocity the oil-water regime detector uses. Gas-sheared liquids
> therefore carry no stratified slip into a transient that treats them as
> dispersed (equinor/neqsim#5046, 2026-11-15; the horizontal in-situ water
> benchmark, 0.1337, still passes).
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
> **Check three-phase PVT keyword semantics.** Use `RS = gas mass/(gas + oil
> mass)`, excluding the separate aqueous phase. At standard conditions, use
> `GLR = gas volume/(oil + water volume)` and `WC = water volume/(oil + water
> volume)`; never copy GOR into GLR for a wet fluid. Omit optional multicomponent
> bubble headers rather than exporting failed or inappropriate wet-fluid roots.
> Regenerate older tables after the issue #4478 corrections; see
> `docs/integration/olga_pvt_table_generation.md` for the no-liquid conventions
> and required pure-component saturation validation.
>
> **Configure the table for the study, then hand over the report.** For a
> wellstream table set produced water on a water-free feed
> (`setProducedWater(30.0, "watercut")`), salinity (`setWaterSalinity(35000.0,
> "mg/l")`), IAPWS aqueous properties (`setUseReferenceWaterProperties(true)`,
> applied only where the aqueous phase is >= 95 wt% water, so MEG-rich water stays
> on the EOS), `setEnvelopeRefinement(true, 5.0)` with >= 50 points per axis and
> `setInsertStandardConditions(true)`. After `run()`, require
> `getQualityReport().isLoadable()` and attach `writeTableReport("x_report.md")`
> and the `.tab` to the study. Retrograde liquid density falling with pressure at
> existing nodes is physical and is reported, not an error.
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
>
> **`TwoFluidPipe` closed-end pack-up limits (measured 2026-11-21, 15.6 km 12-inch
> line, 300 t/h wet gas/oil/water, 80 cells, coupled pressure-momentum).** The
> flat line packs with no rejected substep up to the saturation pressure of the
> feed (255 bar at 2,400 s). Above that pressure cell pressures differ by tens of
> bar again, because the gas should dissolve and the default route keeps the phase
> masses frozen: use component transport with mass transfer there. With a steep
> riser and a closed top the cells at the riser show velocity differences of about
> 10 m/s within 5 s and pressure spikes after about 30 min: do not report
> closed-riser-top pressures from the model.

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

### Legacy CO2 corrosion screening from a brine (electrolyte pH + FeCO3 film)

`NorsokM506CorrosionRate` (`neqsim.process.corrosion`) uses de Waard-Milliams screening equations (fugacity, in-situ pH, FeCO3 scaling temperature, wall shear, glycol,
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
boolean suspect = bridge.isElectrolytePhaseSuspect(); // true -> correlation pH was used
```

Gotcha (2026-11-22): the electrolyte CPA pH of CO2-saturated water is about 0.9 units high
(4.84 vs about 3.9 at 25 C, 1 bar CO2), which gives non-conservative rates. The bridge falls back to
the correlation pH when the aqueous phase is suspect (water < 0.7 mol frac) or pH is outside 2.5-10;
check `isElectrolytePhaseSuspect()` and compare with the correlation pH. `ChlorideSCCAssessment`
reports carbon and low-alloy steels as "Not susceptible" (they used to fall through to 316L).

**FeCO3 film feedback (closes the corrosion↔scaling loop):** when the aqueous phase
is supersaturated in siderite (SR>1), a protective film suppresses corrosion even
below the scaling temperature. Supply the ratio directly on the model, or let the
bridge compute it from aqueous Fe++/CO3-- molalities (Sun & Nesic 2009 Ksp):

```java
model.setFeCO3SaturationRatio(sr);   // >1 = protective; -1 = disabled (default)
double film = model.calculateFeCO3FilmFactor();  // 1.0 = no credit, <1 = protective
```

For an auditable legacy screening calculation, pass the resulting in-situ pH and optional saturation ratio
to `NorsokM506CorrosionDesignKernel.Input`. For the M-506 Rev. 2 unmitigated rate, use `NorsokM506Rev2.rateMmPerYear` with
local shear and in-situ pH. The legacy kernel checks its registered 2017 edition identifier and model envelope, preserves raw inputs
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
velocity profile and reports, per segment, the legacy screening corrosion rate AND the
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

### Minimum flow of a tie-back line, on the real route

A line is not "flat plus a riser" once it is longer than about 2 km, and one rate is not a design.

1. **Use the real profile.** Take depths along the route (survey, EMODnet `depth_sample`, or the
   KP route of an existing line) and build
   `RouteProfile.fromDepths(kp, depth).withRiser(depthAtHost, airGap).resample(60)`, then
   `route.applyTo(twoFluidPipe)`; `route.getLowPointKp()` lists the liquid traps.
   `route.applyTo(PipeBeggsAndBrills)` collapses the route to one averaged leg and logs a warning above
   5 degrees; it keeps the net rise and loses the undulation. Compare the real profile with a flat
   bed of the same length and riser on the SAME model before saying the seabed does not matter.
2. **Fix the outlet, solve the inlet.** `TwoFluidPipe.setOutletPressure(p, "bara")` gives the inlet
   pressure the line needs, which is the number a VFP or nodal curve wants. Fixing the inlet and
   reading the outlet hides how fast the line pressure runs away at low rate. The pressure of the
   inlet stream is only a starting value: the feed split is flashed at the solved inlet pressure.
3. **Sweep the rate downwards** (about 10 to 200 % of plateau, with and without lift gas, at the lowest and
   highest water cut) and report each limit by name; the minimum flow is the largest of them:
   - hydraulic: the rate where the required inlet pressure stops falling (U-shape: friction falls,
     hold-up and hydrostatic head rise);
   - hold-up and liquid inventory (`getLiquidInventory("m3")`, `getLiquidHoldupProfile()`) and the
     time to sweep it after a restart;
   - thermal: arrival temperature minus the hydrate or wax temperature at the line pressure;
   - sand or solids: `u_L / v_c` (section above);
   - riser or terrain slugging and the pig rate, which the steady-state model does not give.
4. **Always check `isSteadyStateConverged()`** and the iteration count; a case that exits after one
   sweep is not a solution.
5. State what was not run: severe-slugging limit cycles need a transient run and are not qualified in
   `TwoFluidPipe` (see the evidence matrix); a steady-state hold-up profile is not a slug size.

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

Start with the
[TwoFluidPipe evidence matrix](../../../docs/process/twofluidpipe-evidence-matrix.md).
It separates implemented, numerically verified, experimentally qualified, failed,
and unsupported configurations. Do not route from a finite profile or a private
commercial-simulator comparison.

- Use `PipeBeggsAndBrills` for a correlation-based steady or quasi-steady
  pressure-drop screen. It has no conservative distributed line-pack inventory,
  so it is not evidence for transient liquid accumulation or severe slugging.
- Use `TwoFluidPipe` when section-resolved phase momentum, holdup, inventory,
  thermal, or transient diagnostics are required **and** the requested
  configuration fits a verified matrix row.
- The public Mohmmed air/water slug comparison currently fails qualification:
  the baseline passes 3/9 fixed gates with MARE 1.0402, and the 40/80-cell plus
  0.05/0.025 s sweep is non-monotone. Do not replace that evidence with a looser
  physical bound or a proprietary trace.
- Gate every steady result on the complete convergence report and every transient
  on requested elapsed time, conservation, positivity, refinement, and all
  applicable sticky solver/boundary diagnostics. Exclude a failed or unsupported
  row instead of quoting it.
- The experimental unsplit directional-complementarity path now solves nonnegative
  mass magnitude, advective momentum and donor multipliers for both one-sided donor streams
  and verifies its solved ledger independently. Physical AUSM transport and pressure
  correction retain separate donors. Conflict-free exact-presence anchors and exact
  zero-flow rows permit phase-free multi-cell regions without a mass clip or inventory
  tolerance. Treat disappearance, simultaneous opposing streams and short-step
  conservation tests as numerical evidence only: all no-pressure-interpolation
  five-second lanes complete, but pressure-interpolated baseline and augmented lanes
  still stop at 1.075--1.8427734375 s, so the complete riser matrix remains failed.
  Require that entire matrix before progressing to 180/600 s,
  physical restart, swept-liquid, receiver-surge or hydraulic-pigging claims.
- The current no-pressure-interpolation unsplit preparer advances passive wall
  and interphase friction once with the conservative frozen-resistance source.
  Audit the accepted per-cell phase impulse from `PreparedStep`/`PreparedInterval`.
  The three midpoint five-second lanes now pass; the pressure-interpolated lanes
  still stop at 1.4000/1.8125/0.9750 s and retain their prior friction owner
  because the combined operator regresses the trace-phase/donor event. This is
  numerical source evidence only, not restart, severe-slugging or pigging
  qualification.
- For the bounded frozen-phase `runTransient` route, configure
  `setOutletBoundaryFluid(...)` whenever a constant-pressure outlet can donate a
  reversed phase. The signed transfer stays in the phase ledger; only positive
  exports enter the downstream stream. The historical-grid 16/24-cell,
  0.1/0.05 s five-second matrix and the 16-cell 180/600 s duration gates pass
  with backward Euler, pressure interpolation, transition blending and signed
  donor transport. The interval controller tightens local solves against a
  duration-weighted share of the unchanged cumulative conservation budget.
  Treat this as atomic numerical continuation evidence, not experimental severe
  slugging, physical restart, receiver surge or pigging qualification.
- Do not over-constrain `setOutletPressure` with a measured pressure drop the
  modelled geometry cannot produce. Investigate valves, fittings, elevations, and
  boundary consistency separately.

### Mineral Scale (thermodynamics + kinetics + brine mixing)

For commingled formation waters with dissolved silica, follow the
[produced-water route](../neqsim-produced-water-compatibility/SKILL.md)
with the NeqSim and PHREEQC Pitzer example. Compare the CO₂ boundary conditions
and amorphous silica, crystalline silica and metal silicate results at each station.

For common carbonate and sulfate minerals, pick by how much you know about
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

### Burial, trenching and rock dump change the thermal basis

A buried or rock-covered flowline cools down much more slowly than an exposed one, so one burial depth
for the whole line is wrong wherever the route changes between trenched, rock-covered and exposed
stretches. Scope the protection first with the `neqsim-seabed-protection-screening` skill (map layers,
trenchability, rock volume and tonnes; community `seabed-protection-agent`), then use its
`to_neqsim_burial_segments` hand-off: one pipe section per burial state with `setBuried`,
`setBurialDepth` (depth to the pipe centre) and `setSoilConductivity`, exposed sections on the
seawater film coefficient. Report cooldown and hydrate margin for the protected and the unprotected
state so the margin that burial buys is explicit. Burial depth and soil conductivity are thermal
inputs only - never derive pipe-soil resistance or upheaval-buckling cover from them (see the DNV-RP-F114
and F110 routing above).

### Arrival Temperature Check

```java
// Critical checks:
// 1. Arrival T > hydrate formation T + margin (typically 5°C subcooling)
// 2. Arrival T > WAT (if waxy crude) + margin
// 3. Arrival T > pour point (for restart)
// If not met: increase insulation, add DEH, reduce flow, or inject inhibitor
```

### Low-rate satellite tie-back: heat loss, not friction, sets the design

A short tie-back at 0.5-2 kSm3/d liquid (typical of a small cold, shallow oil such as the Barents
satellites) has a pressure drop of 1-2 bar in 6-8 in, but a residence time of about a day, so the
arrival temperature falls towards the seabed. Rank the options on arrival temperature and cost, not
on hydraulics:

- Run `PipeBeggsAndBrills` with `setUseOverallHeatTransferCoefficient(true)` and U = 12 (bare),
  4 (wet insulation), 2.5 (thick wet), 1.2 W/m2K (pipe-in-pipe) at 500-4000 Sm3/d, at the real
  wellhead temperature (area BHT trend gives the reservoir temperature when no BHT is reported).
- No-touch time must include the steel (and half the insulation) heat capacity per metre; the fluid
  alone overstates the cooling rate by about 50 %.
- The WAT is usually the unknown: sweep it (for example 18 / 26 / 34 C) instead of picking one; a
  passive 12 h no-touch time to a 26 C WAT from 35 C is not reachable even with pipe-in-pipe, so
  adopt displacement/flush and chemicals for restart, keep DEH as the hedge for a high WAT, and say
  that WAT, pour point and viscosity are the first data need.
- Rank options by 15-year life-cycle cost (capex premium + chemical opex) and list the measured-WAT
  decision rule (low WAT: wet insulation; mid: wet insulation plus displacement; high: DEH or PiP or
  avoid a new line, for example by extended-reach wells from an existing template).
```

### DEH operations: how much power, how much temperature, how much energy

Use this when a task asks what a direct-electrical-heating (DEH) system or an inlet heater is worth,
how much electricity it burns or how its on/off operation relates to arrival temperature. The
detailed pipe model is `setDirectElectricalHeatingPower` (above); for yearly screening a lumped
model is enough and is cheap enough for Monte Carlo.

- **Lumped arrival temperature** with distributed DEH power $P$: balance temperature
  $T_{bal}=T_{sea}+P/UA$, arrival $T=T_{bal}+(T_{in}-T_{bal})e^{-UA/\dot m c_p}$. Fraction of DEH heat
  that reaches the arrival (heat-exchanger effectiveness) is $\eta=(1-e^{-NTU})/NTU$ with
  $NTU=UA/\dot m c_p$, so it falls from about 0.55 at 9 MSm3/d to about 0.2 at 2.5 MSm3/d in a
  30 km line: electric DEH loses most of its heat to the sea at low rate, a heater at the receiving
  end does not. Take $\dot m c_p$ from NeqSim (recombined wellstream, TP flash, per MSm3/d), not
  from gas alone: liquids and water add 5-15 %.
- **Calibrate on two independent anchors** (flow-assurance rate-vs-arrival-temperature curve and a
  measured DEH step test), fit $(T_{in},UA)$ on one and predict the other; cap $T_{in}$ at a
  physical wellhead temperature or the pair is degenerate (T_in runs to the optimiser bound).
- **Never read DEH power from the transformer tap tag.** The tap/step tag is a setting that stays
  at its last value (for example 12.6 of 13) with the breaker open. Power is breaker state times
  primary current: $P\approx P_{max}\,I/I_{max}$ (check median current when on against the rated
  power to find $I_{ref}$). One field showed the breaker closed 25 % of hours at about 7.7 MW,
  so the time-average power was 2.0 MW (17 GWh/y), not the 7.7 MW the tap tag suggests, and a
  quoted "halve the DEH, save 20 kt CO2" only applies once DEH runs continuously.
- **Bang-bang DEH** (fixed power steps, on/off) holds the mean temperature but not the minimum.
  Report on-fraction per month and the temperature percentiles, not only the mean; a regulated
  heater saves little energy at equal mean but narrows the band, so value it through the
  threshold-driven consequence (for example oil-in-water exceedance probability vs temperature
  bin), not through kWh.
- **Cross-check hydrate margin in the heated line**, not only at the receiving separator: the
  arrival temperature without DEH crosses (hydrate formation temperature + margin) years before the
  separator temperature does. In Python, `hydrateFormationTemperature()` on a CPA fluid with a long
  component list threw `getPhase ... Can not return` here; `runFlowAssurance` `hydrateRiskMap` (MCP)
  returned the CPA temperature directly and is the quick route.
- Calibrate unknown mixing inputs (temperature of the other streams, DEH delivery scale) by
  approximate Bayesian rejection against the measured energy rather than fixing them: draw from
  wide priors, keep draws that reproduce the measured average DEH energy, and carry the survivors
  through the economics.

## 7. Flow Assurance Decision Matrix

| Threat | Detection | NeqSim Method | Mitigation |
|--------|-----------|---------------|------------|
| Hydrate | `hydrateFormationTemperature()` | CPA EOS + hydrate check | MEG, methanol, insulation, DEH |
| Wax | `calcWAT()`, `WaxFractionSim` | Wax characterization | Pigging, inhibitor, insulation |
| Asphaltene | de Boer screening | `AsphalteneSolidModel` calibrated to a measured onset; CPA flash at multiple P | Inhibitor, avoid P drop |
| Naphthenate / soap | `NaphthenatePrecipitationModel` with default parameters (onset pH, risk class) | Same model with parameters regressed by `NaphthenateModelFitting` to bottle tests | Keep pH low (acid, CO2 back-pressure), naphthenate inhibitor |
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
| Tie-back deliverability collapses (recovery 7-13 %) when a NeqSim tubing/flowline table is used as the outflow curve | `PipeBeggsAndBrills` tubing outflow is non-monotonic at low rate (liquid-loading minimum): intersect the inflow with the stable right-hand branch only (rate above the pressure minimum), never the whole table |
| `PipeBeggsAndBrills` throws a negative-pressure exception while building a Pin(Q, P_arrival) table | Solve the required inlet pressure by bisection inside `try/except`, treat a failure as infeasible (NaN), and replace NaN by a large pressure before interpolating |
| Hydrate temperature is NaN or MEG suppression is zero | `fluid.setHydrateCheck(True)` before the flash, and a water component present; in a TBP-characterised fluid the pseudo-components are named `<name>_PC` |
| Arrival temperature equals the inlet in a Beggs-and-Brill cooling study | Call `setConstantSurfaceTemperature(...)` before `setHeatTransferCoefficient(...)`; screening U is about 20 W/m2K for a bare line and 4 for an insulated one. The U-value is referenced to the INNER area (pi x ID x L): convert a layered build-up with `U_inner = 1/(2 pi r_i R_total)`; an outer-area U overstates the heat loss |
| 5.5-inch tubing shows no feasible rate for a 1-3 MSm3/d gas-condensate well | Use 7-inch (ID 0.159 m) as the screening tubing and check the liquid-loading minimum before blaming the flowline |
| A new tie-back looks feasible on its own line but is routed through a shared trunk | Compute the back-pressure the added rate puts on the trunk inlet and compare it with the choke dP of the existing wells (Trestakk: +23/+51/+93 bar for +1.5/+3/+5 kSm3/d on a 12-in trunk against 14-95 bar of headroom); beyond the headroom the existing wells are throttled out or a dedicated line is needed |
| Trunk ID and host arrival pressure are unknown | Calibrate the arrival pressure to measured wellhead/choke data for each plausible ID (10 and 12 in), show both, and bracket the arrival pressure with the host PSD window instead of reporting one number |

### TwoFluidPipe static terrain initialization

The conservative interior operator reconstructs a common face pressure from adjacent mixture inertia and signed
half-cell heads. The public 90-case analytical matrix covers flat, affine and curved terrain, three density patterns,
12--192 cells and pure gas/oil below `1e-10 m/s2`, with zero local mass and energy rates; a separate 45-case
pure-water extension passes the same gates. Signed face work uses
`Q * deltaP` with the mass donor, horizontal pressure flux is unchanged and rollback restores the accepted diagnostic.
Use this as numerical hydrostatic-initialization evidence only. A stationary gas/liquid pool with different phase
densities, moving terrain drainage, restart, slugging and pigging remain unsupported until their own conservation,
refinement and public experimental gates pass.

For an explicit static phase-traction diagnostic, use
`GeometryCalculator.calculateStratifiedPressureMoments(depth, diameter)`: signed liquid/gas moments
about the interface in m3 and their area derivatives in m2. Multiply each moment by its phase density,
gravity and cosine of inclination for the hydrostatic traction contribution; interface pressure times
phase area is separate. To use the same geometry in the transient operator, explicitly call
`setStratifiedHydrostaticPressureMomentsEnabled(true)`. It adds phase traction at every face and
matching `Q * F / A` work with the signed mass donor; inspect
`getLastStratifiedHydrostaticPressureForces()` in N. The supported face contains gas and at most one
liquid in a constant-diameter circular pipe; oil+water and diameter steps reject. Its independent
integral, signed-work, conservation and rollback tests do not establish elapsed dynamic pool
retention, terrain drainage, shutdown/cooldown, restart, slugging, pigging or hydrocarbon evaporation.

Preserve both face fractions when adapting holdup to pressure moments: use
`calculateStratifiedPressureMomentsFromHoldups(gas, liquid, diameter)`. Its relative minority-area
inversion and algebraic reflection retain positive traces; the legacy general holdup-to-depth
approximation has an absolute area tolerance and must not supply this pressure-work geometry.
See `StratifiedPressureMomentsTest`, `StratifiedPressureMomentCouplingTest` and the public numerical record.
The enabled historical 12/24-cell ramp currently stops at 0.25 s on the unchanged face-volume
closure guard. Keep the executable coupling rejection record attached; instantaneous operator
identities do not qualify full elapsed transient operation.

## Residual MEG/water liquid after pigging

For the bounded experimental Java/Python route, use `TwoFluidPipe` with
`NonEquilibriumFilmTransfer`, `setInitialAqueousFilm`, and named-component transport.
Inspect `docs/process/TwoFluidPipeFilmDrying.md` and `TwoFluidPipeFilmDryingTest` before setup.
The inlet gas and initial liquid compositions are independent; water can absorb while MEG
evaporates. Select isothermal Euler, CFL <= 0.5, and disable tracked slugs/heating. Coefficients
are user inputs, not calibrated defaults. This is a gas/aqueous two-fluid route; the axial
`PipelineEvaporationStudy` wall-film option does not calculate elapsed post-pigging drying time.
Use `getFilmDryingStatus` with a positive residual threshold and horizon, preserve boundary
and component ledgers, and distinguish seeded pool motion from validated terrain trapping.
Short-time conservation, refinement and CPA equilibrium tests do not qualify 10 km drying duration.


### Cross-sectional gravitational energy budget

Use the existing `GeometryCalculator.StratifiedPressureMoments` derived centre-referenced phase
first moments [m3] and `getGravityPotentialEnergyPerLength(gasDensity, liquidDensity, normalGravity)`
[J/m] for an explicit static normal-gravity budget. No solver state or serialized field is added.
See [the executed analytical/high-precision energy record](../../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/gravity-energy/README.md).
This does not resolve the documented dry/wet pressure impulse or qualify dynamic pools, inlet refill
or hydrocarbon evaporation; those still require conservative phase-front transport/work and heat/mass transfer.


### Optional Rusanov phase-flux contract

The default remains AUSM+. `AUSMPlusFluxCalculator.calcRusanovFlux` is an explicit local query,
and `TwoFluidPipe.setRusanovInternalFluxEnabled(true)` selects the qualified two-donor operator on
independent gas/oil/water internal faces after `setEnableWaterOilSlip(true)`.
Its dissipated energy is `alpha * (rho * H - p)` with total specific enthalpy;
exact absence ignores unused properties and positive traces retain the same formula.
SRK gas/oil controls distinguish EOS-volume internal energy from physical-density mechanical
energy using explicit pressure-volume work; do not mix these API conventions in a reference.
See the [executed conservative-flux evidence](../../../src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/README.md).
Forward and reverse donor streams remain separate through Runge-Kutta staging, pressure work and
transactional component transport; their difference must reproduce the hydrodynamic net. The
combined-liquid split, pressure interpolation, reconstructed slug faces and one-direction unsplit
complementarity route reject this option. Apply a wave-speed Courant bound and inspect component,
phase, total-energy and rollback reports.
Do not use it as proof of dry/wet fronts, terrain pools, refill or physical evaporation.

