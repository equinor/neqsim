---
name: neqsim-electrolyte-systems
description: "Electrolyte and brine chemistry guidance for NeqSim. USE WHEN: modeling produced water, scale prediction, CO2/H2S in aqueous systems, MEG/DEG injection, hydrate inhibitor dosing, or any system with ions, salts, or electrolytes. Covers SystemElectrolyteCPAstatoil setup, ion components, scale risk assessment, and brine handling patterns."
last_verified: "2026-09-01"
---

# Electrolyte Systems Guide

Guide for modeling electrolyte/brine systems in NeqSim.

## When to Use Electrolyte Models

- Produced water with dissolved salts (NaCl, CaCl2, BaCl4)
- Scale prediction (CaCO3, BaSO4, CaSO4)
- CO2/H2S solubility in brines
- MEG injection rate calculations
- Hydrate inhibitor dosing
- Seawater injection and mixing
- Desalination process modeling

## EOS Selection for Electrolyte Systems

| System | NeqSim Class | Mixing Rule |
|--------|-------------|-------------|
| Water + salt + HC gas | `SystemElectrolyteCPAstatoil` | `10` |
| MEG/DEG + water + gas | `SystemSrkCPAstatoil` | `10` |
| Pure water + gas | `SystemSrkCPAstatoil` | `10` |
| Brine + multiple salts | `SystemElectrolyteCPAstatoil` | `10` |

## Basic Electrolyte Setup

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

// Create electrolyte system
SystemInterface brine = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 200.0);

// Add gas components
brine.addComponent("CO2", 0.05);
brine.addComponent("methane", 0.80);

// Add water
brine.addComponent("water", 0.10);

// Add salt components (as ions)
brine.addComponent("Na+", 0.02);
brine.addComponent("Cl-", 0.02);
brine.addComponent("Ca++", 0.005);
brine.addComponent("SO4--", 0.005);

// Set mixing rule (MUST be numeric for CPA)
brine.setMixingRule(10);
brine.setMultiPhaseCheck(true);
```

## Common Ion Components in NeqSim

| Ion | NeqSim Name | Common Source |
|-----|------------|---------------|
| Sodium | `"Na+"` | NaCl |
| Chloride | `"Cl-"` | NaCl, CaCl2 |
| Calcium | `"Ca++"` | CaCl2, CaCO3 |
| Barium | `"Ba++"` | BaSO4, BaCl2 |
| Strontium | `"Sr++"` | SrSO4 |
| Sulfate | `"SO4--"` | Na2SO4, BaSO4 |
| Bicarbonate | `"HCO3-"` | NaHCO3 |
| Carbonate | `"CO3--"` | CaCO3, Na2CO3 |
| Magnesium | `"Mg++"` | MgCl2 |
| Potassium | `"K+"` | KCl |
| Iron(II) | `"Fe++"` | FeCl2, FeS |

## Produced Water Modeling

```java
// Typical produced water composition
SystemInterface prodWater = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 50.0);

// Dissolved gas
prodWater.addComponent("CO2", 0.01);
prodWater.addComponent("H2S", 0.001);
prodWater.addComponent("methane", 0.005);

// Water (dominant)
prodWater.addComponent("water", 0.90);

// Ions (typical North Sea produced water)
prodWater.addComponent("Na+", 0.03);
prodWater.addComponent("Cl-", 0.035);
prodWater.addComponent("Ca++", 0.003);
prodWater.addComponent("Ba++", 0.0001);
prodWater.addComponent("SO4--", 0.001);
prodWater.addComponent("HCO3-", 0.005);

prodWater.setMixingRule(10);
prodWater.setMultiPhaseCheck(true);

// Flash to get gas/liquid split
ThermodynamicOperations ops = new ThermodynamicOperations(prodWater);
ops.TPflash();
prodWater.initProperties();
```

## Scale Risk Assessment

### Higher-level scale helpers (prefer for screening)

Before hand-rolling the flash, note these ready-made helpers (see the
`neqsim-flow-assurance` skill for full patterns):

- `ElectrolyteScaleCalculator` — activity-corrected SI for CaCO3, BaSO4, CaSO4,
  SrSO4 from ion mg/L (Davies + Ksp(T)).
- `ScaleKinetics` — induction time and reaction-vs-transport growth regime on top
  of an SI (SI says *if*, kinetics says *how fast*).
- `BrineMixingScaleEvaluator` — seawater/formation-water mixing sweep; finds the
  worst mixing fraction and mineral (sulphate scale often peaks mid-mix).
- `ScaleDepositionAccumulator` / `PipeSegmentIntegrity` — deposition and coupled
  corrosion+scale along a `PipeBeggsAndBrills` profile.
- Rigorous scale potential (saturation ratio SR = IAP/Ksp per salt):
  `ops.checkScalePotential(phaseNumber)` then `ops.getResultTable()`. This uses
  in-situ ion molalities, so it **needs chemical reactions + speciation** — call
  `system.chemicalReactionInit()` before `createDatabase(true)`/`setMixingRule(10)`
  and flash first (otherwise carbonate/bicarbonate/pH speciation is missing and
  the SR is wrong).

### Activity-consistent calcium-sulfate equilibrium

Use the pure-mineral operation when the engineering question is gypsum/anhydrite equilibrium rather than screening
SI:

```java
MultiSaltPrecipitationResult solids =
    new ThermodynamicOperations(brine).precipitateScales("CaSO4_A", "CaSO4_G");

CalciumSulfatePhaseBoundaryQualification evidence =
    new ThermodynamicOperations(brine).qualifyCalciumSulfatePhaseBoundary();
```

`CaSO4_G` is hydrated gypsum: the authoritative operation includes $a_{\mathrm{w}}^2$, removes or returns two water
moles per formula unit, and reports hydrated mass. `CaSO4_A` remains water-free anhydrite. The qualification object
separates this mineral-standard-state evidence from the Pitzer or electrolyte-CPA aqueous activity model. It is
currently fail-closed against the CC BY 4.0 Voigt–Freyer pure-water and NaCl crossing envelopes and does not qualify
high-pressure use; do not fit or reinterpret Pitzer interactions to hide a mineral-correlation mismatch. See
`docs/pvtsimulation/scale_prediction_api.md` for the source matrix, numerical residuals, and limits.

### CaCO3 (Calcite) Scaling

Scale forms when the product of ion activities exceeds the solubility product:

$$
SI = \log_{10}\left(\frac{a_{Ca^{2+}} \cdot a_{CO_3^{2-}}}{K_{sp}}\right)
$$

Where $SI > 0$ indicates supersaturation (scaling risk).

```java
// Rigorous route: mix formation water + seawater, solve speciation, check scale
SystemInterface mixed = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 50.0);
// Add components from both waters (water, Na+, Cl-, Ca++, Ba++, SO4--, HCO3-, CO2 ...)
mixed.chemicalReactionInit();   // MANDATORY for pH + carbonate speciation + SR
mixed.createDatabase(true);
mixed.setMixingRule(10);
mixed.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(mixed);
ops.TPflash();                  // solves aqueous speciation
mixed.initProperties();

// (1) Scale potential per salt: saturation ratio SR = IAP/Ksp (>1 => scaling)
int aq = mixed.getPhaseNumberOfPhase("aqueous");
ops.checkScalePotential(aq);
String[][] sr = ops.getResultTable();     // rows: {saltName, SR, ""}
double pH = mixed.getpH();                // speciated in-situ pH from same flash

// (2) Precipitation amount: a supersaturated brine drops a solid phase
if (mixed.hasPhaseType("solid")) {
    double solidMoles = mixed.getPhase("solid").getNumberOfMolesInPhase();
    double solidMassKg = solidMoles * mixed.getPhase("solid").getMolarMass();
}
```

### BaSO4 (Barite) Scaling

Most problematic scale in oil production — forms when Ba++ meets SO4--.

```java
// Formation water (high Ba++)
SystemInterface formWater = new SystemElectrolyteCPAstatoil(273.15 + 90.0, 200.0);
formWater.addComponent("water", 0.95);
formWater.addComponent("Na+", 0.025);
formWater.addComponent("Cl-", 0.020);
formWater.addComponent("Ba++", 0.001);  // High barium
formWater.addComponent("Sr++", 0.0005);
formWater.setMixingRule(10);

// Injection water (high SO4--)
SystemInterface injWater = new SystemElectrolyteCPAstatoil(273.15 + 20.0, 200.0);
injWater.addComponent("water", 0.96);
injWater.addComponent("Na+", 0.015);
injWater.addComponent("Cl-", 0.015);
injWater.addComponent("SO4--", 0.01);   // High sulfate
injWater.setMixingRule(10);
```

## Cathodic (high-pH) scaling in seawater electrochemical devices

Electrochlorination cells, seawater electrolysers and impressed-current cathodic protection all
evolve hydrogen at the cathode, which lifts the boundary-layer pH far above the bulk seawater
value of ≈ 8.1. The deposit there is **brucite Mg(OH)₂ together with CaCO₃**, not the sulphate
minerals produced-water work usually screens for. Set the **boundary-layer** pH, not the bulk pH:

```java
ElectrolyteScaleCalculator calc = new ElectrolyteScaleCalculator()
    .setTemperatureCelsius(8.0).setPressureBara(1.013)
    .setPH(10.5)                       // cathode boundary layer, NOT the bulk 8.1
    .setCations(412.0, 0.02, 7.9, 1290.0, 10780.0, 399.0, 0.002)  // S = 35 seawater, mg/L
    .setAnions(19350.0, 2710.0, 142.0, 0.0);
calc.calculate();
double siBrucite = calc.getBruciteSaturationIndex();   // Mg(OH)2 - two decades per pH unit
double siCalcite = calc.getCaCO3SaturationIndex();
double aOH = calc.getHydroxideActivity();
```

Bulk S = 35 seawater at 8 °C is already supersaturated in calcite (SI ≈ +0.58, Ω ≈ 3.8) but
strongly undersaturated in brucite (SI ≈ −4.8); brucite crosses SI = 0 near pH 10.5 at 8 °C and
near pH 10.1 at 14 °C (retrograde solubility, so it is *worse* in warm water).

`getpH()` on a loaded `SystemElectrolyteCPAstatoil` can return a flat 7.0 without an explicit
reaction/ion setup — for boundary-layer work set the pH explicitly on the scale calculator instead
of reading it back from a flash.

**Cell voltage is an electrolyte-transport question.** For a current-controlled device the applied
voltage floats with seawater resistivity, so a fixed voltage limit (vendor maximum, Ex/ATEX
certification) has a *temperature-dependent* margin. Use
`neqsim.process.chemistry.electrochlorination.SeawaterElectrolyteConductivity`
(UNESCO/PSS-78) for conductivity, resistivity and the ohmic voltage:

```java
SeawaterElectrolyteConductivity sw = new SeawaterElectrolyteConductivity()
    .setSalinityPsu(35.0).setTemperatureCelsius(8.0);
sw.calculate();
double kappa = sw.getConductivitySPerM();                       // ≈ 3.62 S/m
double ratio = sw.ohmicVoltageRatioVersusTemperature(15.0);     // ≈ 1.19 vs a 15 °C design basis
```

Chain: this skill → `neqsim-flow-assurance` (scale kinetics, remediation) for deposition rate and
dissolver selection; → `neqsim-standards-lookup` for NORSOK S-001 / ISO 13702 when the consequence
is firewater or seawater-system availability.

## MEG Injection Calculations

```java
// Wet gas with MEG injection for hydrate prevention
SystemInterface wetGas = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
wetGas.addComponent("methane", 0.85);
wetGas.addComponent("ethane", 0.08);
wetGas.addComponent("propane", 0.03);
wetGas.addComponent("water", 0.03);
wetGas.addComponent("MEG", 0.01);  // Monoethylene glycol
wetGas.setMixingRule(10);
wetGas.setMultiPhaseCheck(true);

// Check hydrate temperature with MEG
ThermodynamicOperations ops = new ThermodynamicOperations(wetGas);
ops.hydrateFormationTemperature();
double hydrateT = wetGas.getTemperature() - 273.15;  // °C

// Compare with and without MEG to get suppression
```

## CO2 Solubility in Brine

Important for CCS and EOR projects:

```java
// CO2 solubility decreases with salinity (salting-out effect)
SystemInterface co2Brine = new SystemElectrolyteCPAstatoil(273.15 + 50.0, 100.0);
co2Brine.addComponent("CO2", 0.10);
co2Brine.addComponent("water", 0.80);
co2Brine.addComponent("Na+", 0.05);
co2Brine.addComponent("Cl-", 0.05);
co2Brine.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(co2Brine);
ops.TPflash();
co2Brine.initProperties();

// CO2 in aqueous phase
double co2InWater = co2Brine.getPhase("aqueous").getComponent("CO2").getx();

// In-situ pH — getpH() has a built-in acid-gas fallback, so a CO2/H2S brine
// returns an acidic pH even WITHOUT chemicalReactionInit() (no more silent 7.0).
double pH = co2Brine.getpH();               // ~3.9 for CO2-saturated water
// double pH = co2Brine.getPhase("aqueous").getpH("acidgas"); // force the estimate
```

> For a rigorous speciated pH in a buffered brine (explicit `H3O+`/`HCO3-`/`OH-`),
> run `system.chemicalReactionInit()` before `createDatabase(true)` /
> `setMixingRule(10)`. The `getpH("acidgas")` fallback is a screening estimate that
> ignores bicarbonate buffering and salt-ion alkalinity — see the electrolyte-CPA
> model docs and the flow-assurance skill for corrosion use.


## Key Units and Conversions

| Quantity | Unit | Conversion |
|----------|------|------------|
| Ion concentration | mol fraction | ppm = x × MW_solution / MW_ion × 1e6 |
| TDS | mg/L | Sum of all dissolved ion concentrations |
| Salinity | wt% NaCl equiv | Based on Na+/Cl- content |
| pH | dimensionless | From H+ activity |

## Common Pitfalls

1. **Charge balance**: Total positive charges must equal total negative charges
2. **Mixing rule must be numeric `10`**: Not `"classic"` — CPA requires numeric mixing rule
3. **Ion names are case-sensitive**: `"Na+"` not `"na+"` or `"NA+"`
4. **Multi-phase check**: Always enable for electrolyte systems (`setMultiPhaseCheck(true)`)
5. **Temperature limits**: Electrolyte models may have narrower valid T range than HC models
6. **Convergence**: Electrolyte flashes can be slow — be patient or reduce component count
7. **Missing counter-ions**: Always add both cation and anion (e.g., Na+ with Cl-)
