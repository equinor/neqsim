---
title: "Scale Prediction API Reference"
description: "Complete API reference for NeqSim mineral scale prediction classes. Covers ScalePredictionCalculator (empirical), CheckScalePotential (EOS-based), solid solution models, water compatibility, flowline profiles, and mass calculators."
---

NeqSim provides three complementary approaches for mineral scale prediction:

1. **Rigorous EOS-based** (`CheckScalePotential`) — Uses Electrolyte CPA or Pitzer activity coefficients from a full thermodynamic flash. Best accuracy, requires complete fluid definition.
2. **High-salinity standalone** (`PitzerScaleActivityModel` + `MultiMineralScaleEquilibrium`) — Uses published binary
   Pitzer parameters and coupled shared-ion precipitation for NaCl-dominated brines.
3. **Empirical standalone** (`ScalePredictionCalculator`) — Uses Davies equation with ion pairing corrections. Fast
   dilute-brine screening, needs only water analysis data.

Both approaches share improved Ksp correlations, pressure corrections, and T-dependent parameters.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│               Scale Prediction in NeqSim            │
├──────────────────────┬──────────────────────────────┤
│   Rigorous (EOS)     │     Empirical (Standalone)   │
│                      │                              │
│  SystemElectrolyte-  │  ScalePredictionCalculator   │
│  CPAstatoil          │  ├── Davies equation         │
│  ├── TPflash()       │  ├── Ion pairing (6 pairs)   │
│  ├── ion activities  │  ├── T-dep Ksp (Monnin etc.) │
│  └── CheckScale-     │  ├── Pressure correction ΔV° │
│      Potential       │  └── Debye-Hückel A(T)       │
│                      │                              │
│  SystemPitzer        │  Helper Classes              │
│  ├── PhasePitzer     │  ├── ScaleMassCalculator     │
│  ├── Pitzer params   │  ├── BariteCelestiteSolid-   │
│  └── T-dep β₀,β₁,Cᶲ │  │   Solution                │
│                      │  ├── WaterCompatibility-      │
│                      │  │   Screener                 │
│                      │  └── FlowlineScaleProfile    │
└──────────────────────┴──────────────────────────────┘
```

---

## 1. ScalePredictionCalculator (Empirical)

**Package:** `neqsim.pvtsimulation.flowassurance`

Fast standalone calculator for 5 mineral scales using water analysis input.

### Setup and Usage

```java
ScalePredictionCalculator calc = new ScalePredictionCalculator();
calc.setCalciumConcentration(400.0);     // mg/L
calc.setBariumConcentration(10.0);       // mg/L
calc.setStrontiumConcentration(5.0);     // mg/L
calc.setIronConcentration(2.0);          // mg/L
calc.setMagnesiumConcentration(1300.0);  // mg/L (for ion pairing)
calc.setSodiumConcentration(11000.0);    // mg/L (for ion pairing)
calc.setBicarbonateConcentration(150.0); // mg/L
calc.setSulphateConcentration(10.0);     // mg/L
calc.setTotalDissolvedSolids(35000.0);   // mg/L
calc.setTemperatureCelsius(80.0);        // °C
calc.setPressureBara(100.0);             // bara
calc.setCO2PartialPressure(2.0);         // bar
calc.enableAutoPH();                     // estimate pH from CO2
calc.calculate();

double siCaCO3 = calc.getCaCO3SaturationIndex();
double siBaSO4 = calc.getBaSO4SaturationIndex();
boolean risk = calc.hasScalingRisk();
String json = calc.toJson();
```

### Python Usage

```python
from neqsim import jneqsim
ScalePredictionCalculator = jneqsim.pvtsimulation.flowassurance.ScalePredictionCalculator

calc = ScalePredictionCalculator()
calc.setCalciumConcentration(400.0)
calc.setBariumConcentration(10.0)
calc.setStrontiumConcentration(5.0)
calc.setBicarbonateConcentration(150.0)
calc.setSulphateConcentration(10.0)
calc.setTotalDissolvedSolids(35000.0)
calc.setTemperatureCelsius(80.0)
calc.setPressureBara(100.0)
calc.setCO2PartialPressure(2.0)
calc.enableAutoPH()
calc.calculate()

print(f"CaCO3 SI = {calc.getCaCO3SaturationIndex():.3f}")
print(f"BaSO4 SI = {calc.getBaSO4SaturationIndex():.3f}")
```

### Ksp Correlations

| Scale | Source | Formula |
|-------|--------|---------|
| CaCO3 (calcite) | Plummer & Busenberg (1982) | $\log_{10} K_{sp} = -171.9065 - 0.077993T + 2839.319/T + 71.595\log_{10} T$ |
| BaSO4 (barite) | Monnin (1999) | $\log_{10} K_{sp} = 136.035 - 7680.41/T - 48.595\log_{10} T$ |
| SrSO4 (celestite) | Monnin (1999) | $\log_{10} K_{sp} = 155.889 - 7862.38/T - 56.625\log_{10} T$ |
| CaSO4 (anhydrite) | Blount & Dickson (1973) | $\log_{10} K_{sp} = 85.685 - 4279.82/T - 30.219\log_{10} T$ |
| FeCO3 (siderite) | Greenberg & Tomson (1992) | $\log_{10} K_{sp} = -59.3498 - 0.041377T + 2.1963/T + 24.5724\log_{10} T + 2.518 \times 10^{-5} T^2$ |

### Pressure Correction

All Ksp values are corrected for pressure using molar volume change:

$$\ln\frac{K_{sp}(P)}{K_{sp}(P_0)} = -\frac{\Delta V^\circ (P - P_0)}{RT}$$

Where $\Delta V^\circ$ (cm³/mol) values are: CaCO3 = -58.4, BaSO4 = -46.4, SrSO4 = -47.0, CaSO4 = -52.4, FeCO3 = -52.9.

### Ion Pairing

Six aqueous ion pairs are modelled with association constants (25°C):

| Ion Pair | $\log_{10} K_{assoc}$ | Effect |
|----------|----------------------|--------|
| CaSO4⁰ | 2.31 | Reduces free Ca²⁺ and SO4²⁻ |
| MgSO4⁰ | 2.37 | Reduces free Mg²⁺ and SO4²⁻ |
| NaSO4⁻ | 0.70 | Reduces free Na⁺ and SO4²⁻ |
| CaHCO3⁺ | 1.11 | Reduces free Ca²⁺ and HCO3⁻ |
| MgHCO3⁺ | 1.16 | Reduces free Mg²⁺ and HCO3⁻ |
| CaCO3⁰ | 3.22 | Reduces free Ca²⁺ and CO3²⁻ |

Temperature dependence uses van't Hoff correction with $\Delta H / R$ coefficients.

---

## 2. CheckScalePotential (EOS-Based)

**Package:** `neqsim.thermodynamicoperations.flashops.saturationops`

Uses full thermodynamic model (Electrolyte CPA or Pitzer) to compute activity-based ion activity products.

### Usage via ThermodynamicOperations

```java
// Create electrolyte system
SystemInterface brine = new SystemElectrolyteCPAstatoil(273.15 + 80.0, 100.0);
brine.addComponent("CO2", 0.01);
brine.addComponent("water", 0.90);
brine.addComponent("Na+", 0.03);
brine.addComponent("Cl-", 0.035);
brine.addComponent("Ca++", 0.005);
brine.addComponent("Ba++", 0.001);
brine.addComponent("SO4--", 0.002);
brine.addComponent("HCO3-", 0.005);
brine.chemicalReactionInit();
brine.createDatabase(true);
brine.setMixingRule(10);
brine.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(brine);
ops.TPflash();
brine.initProperties();

// Check scale potential
int aqPhase = brine.getPhaseNumberOfPhase("aqueous");
ops.checkScalePotential(aqPhase);
String[][] table = ops.getResultTable();

// table rows: [saltName, scalePotentialFactor, ""]
// scalePotentialFactor > 1.0 means supersaturated
for (int i = 1; i < table.length; i++) {
    System.out.println(table[i][0] + " : " + table[i][1]);
}
```

### Python Usage

```python
from neqsim import jneqsim
SystemElectrolyteCPAstatoil = jneqsim.thermo.system.SystemElectrolyteCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

brine = SystemElectrolyteCPAstatoil(273.15 + 80.0, 100.0)
brine.addComponent("CO2", 0.01)
brine.addComponent("water", 0.90)
brine.addComponent("Na+", 0.03)
brine.addComponent("Cl-", 0.035)
brine.addComponent("Ca++", 0.005)
brine.addComponent("SO4--", 0.002)
brine.addComponent("HCO3-", 0.005)
brine.chemicalReactionInit()
brine.createDatabase(True)
brine.setMixingRule(10)
brine.setMultiPhaseCheck(True)

ops = ThermodynamicOperations(brine)
ops.TPflash()
brine.initProperties()

aq_phase = brine.getPhaseNumberOfPhase("aqueous")
ops.checkScalePotential(aq_phase)
table = ops.getResultTable()

for i in range(1, len(table)):
    print(f"{table[i][0]:20s} SR = {table[i][1]}")
```

### Key Features

- Reads all 21 salts from the COMPSALT database
- Special Ksp overrides for NaCl, CaCO3, FeCO3, FeS
- Pressure correction via $\Delta V^\circ$ from COMPSALT `Vdelta` column
- MEG-aware (temporarily replaces MEG with water for calculation)
- Returns **saturation ratio** (SR = IAP/Ksp), where SR > 1 = supersaturated

### Activity-consistent pure-mineral precipitation

`ThermodynamicOperations.precipitateScale(String)` removes one named COMPSALT mineral
stoichiometrically until its aqueous activity saturation ratio is one. It returns an immutable
`SaltPrecipitationResult`; the thermodynamic system is the residual gas/oil/aqueous fluid, while
the result is the corresponding pure-solid material ledger. The solid is deliberately not inserted
as a NeqSim phase.

```java
SystemPitzer fluid = new SystemPitzer(298.15, 50.0);
fluid.addComponent("water", 55.508);
fluid.addComponent("Na+", 1.0);
fluid.addComponent("Ca++", 0.2);
fluid.addComponent("Mg++", 0.15);
fluid.addComponent("Cl-", 1.3);
fluid.addComponent("SO4--", 0.2);
fluid.setMixingRule("classic");
fluid.init(0); // Automatically selects the complete PHREEQC Pitzer catalog topology.
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
SaltPrecipitationResult solid = operations.precipitateScale("CaSO4_A");

double residualSaturationRatio = solid.getFinalSaturationRatio();
double solidAmountMol = solid.getPrecipitatedMoles();
double solidMassGram = solid.getPrecipitatedMassGrams();
double materialResidualMol = solid.getMaximumIonBalanceResidualMoles();
```

The default `SystemPitzer` policy automatically selects the bundled PHREEQC catalog when every
interaction required by the active aqueous topology is present; this complete Ca/Mg/Cl/SO4 example
needs no manual dataset-selection call. Missing mixed interactions still fail closed rather than
becoming zero. `useLegacyPitzerParameters()` is an explicit compatibility opt-out that must be
called before the first property evaluation.

The operation uses the selected aqueous activity model without transferring Pitzer parameters into
the mineral-reaction database. Every trial extent is evaluated on a fresh clone; the accepted
composition is reflashed and physical properties are reinitialised. Consequently the residual
fluid can continue through `Stream`, `Heater`, and `ProcessSystem` calculations. Ions remain in the
aqueous phase, while gas and oil phases retain their EOS roles.

Callers should require a complementarity residual such as
`solid.getComplementarityViolation() <= 1e-5` and independently check total and elemental balances.

### Simultaneous competing pure minerals

`ThermodynamicOperations.precipitateScales(String...)` enforces non-negative pure-solid amounts and
aqueous saturation complementarity for several named COMPSALT minerals. The active set precipitates
supersaturated minerals and redissolves an undersaturated present mineral. Names are sorted before
iteration, so caller ordering cannot select a different solid topology.

```java
MultiSaltPrecipitationResult scales =
    operations.precipitateScales("CaSO4_A", "CaSO4_G");

SaltPrecipitationResult anhydrite = scales.getMineralResult("CaSO4_A");
SaltPrecipitationResult gypsumCorrelation = scales.getMineralResult("CaSO4_G");
double complementarity = scales.getMaximumComplementarityViolation();
double componentBalanceMol = scales.getMaximumComponentBalanceResidualMoles();
```

The returned ledger is absolute for that call and is not inserted into the NeqSim phase list. Carry
it with the residual fluid. After a heater, pressure change, dilution, or composition change, pass
the same ledger back so available solids can dissolve as well as precipitate:

```java
MultiSaltPrecipitationResult updatedScales =
    new ThermodynamicOperations(changedFluid).equilibrateScales(scales);
```

The continuation API conserves dissolved plus ledgered formula units and fails closed if the
bounded active set cannot reach `1e-6` log10-SR complementarity. Non-reactive component and element
ledgers use a `1e-10 mol` absolute balance tolerance. Reactive element ledgers use the same absolute
floor plus a `1e-8` relative tolerance on each element inventory, matching the numerical closure of
the chemical-equilibrium solver without weakening trace-element checks. The result reports both the
maximum absolute residual and the maximum residual normalized by its quantity-specific tolerance;
the normalized value must not exceed one. It also reports the update count, maximum complementarity
violation, per-mineral saturation ratios and non-negative amounts. It is serializable and its mineral
map is defensively immutable for Java and Python process workflows. A thermodynamic system remains
mutable and must not be shared between threads. Sequential clones are deterministic, and independently
constructed systems are covered by a parallel determinism regression. Concurrent use of clones from
one Pitzer instance is not qualified because current shared Pitzer internals can race; construct each
parallel system independently until that separate prerequisite is resolved.

The calcium-sulfate COMPSALT rows distinguish anhydrite `CaSO4_A` from gypsum
`CaSO4_G`. Gypsum explicitly carries two waters per formula unit: its saturation ratio includes
the active aqueous model's solvent activity squared, precipitation removes two moles of water into
each mole of solid, dissolution returns them, and the solid ledger reports the hydrated molar mass.
Anhydrite remains the water-free `CaSO4` reaction. The implementation follows the PHREEQC 3.9.0
reactions `CaSO4 = Ca+2 + SO4-2` and
`CaSO4:2H2O = Ca+2 + SO4-2 + 2 H2O` from the USGS public-domain
`pitzer.dat` catalog at commit `b0b3be767158ccc3322d2c816625cf470045e67e`.

A separate dilution continuation proves that an existing solid can redissolve without stale-state
carryover. These regressions establish standard-state and material-ledger semantics; they do not
independently validate the COMPSALT temperature/pressure correlations. In particular, the current
COMPSALT pressure-volume coefficients differ from PHREEQC mineral molar volumes, so quantitative
high-pressure gypsum/anhydrite phase-boundary use remains outside the qualified scope.

The process-system test carries the solid ledger beside the residual fluid through a
`Stream -> Heater -> ProcessSystem` calculation, then re-equilibrates it at the outlet. Its
charge-balanced feed contains nonzero Ca++, Mg++, Cl-, and SO4--; Ca/SO4 close against the solid
ledger while Mg/Cl remain unchanged spectators. Fluid-phase density, enthalpy and heat capacity
remain finite and ions remain aqueous. This exercises the complete four-ion PHREEQC topology but
does not by itself qualify quaternary mixed-brine observables. Solid density, enthalpy,
heat capacity and heat of precipitation are not yet represented, so rigorous process energy balances
with a material solid stream remain a separate model/property boundary.

Neither pure-mineral API solves solid solutions, nucleation, kinetics, deposition, or inhibitor
performance. A Pitzer calculation
must first select a parameter dataset complete for its active aqueous topology. Missing binary,
same-sign, ternary, or neutral interactions remain an error; they are not silently set to zero.

`SaltPrecipitationPerformanceBenchmark` records explicit-operation cost separately from the neutral
control. On OpenJDK 17 in the development container, its median fresh-system calculations were
78.9 ms for aqueous anhydrite precipitation and 1.018 s for the complete gas-oil-aqueous case.
The unchanged neutral SRK control measured 0.215 ms before and 0.055 ms after the Pitzer batches
(ratio 0.254, reflecting JIT warmup rather than a regression). This operation is invoked only by
`precipitateScale` or `precipitateScales`; neutral PR/SRK/CPA calculations execute no new branch or
allocation.

With simultaneous `CaSO4_A`/`CaSO4_G` enabled in the same benchmark, median fresh-system times were
80.5 ms for the aqueous calculation and 1.206 s for gas-oil-aqueous. The active set required one
solid update, reached `9.995e-9` maximum log10-SR complementarity violation, and closed the component
ledger to reported machine zero. The unchanged neutral SRK control measured 0.205 ms before and
0.049 ms after the electrolyte batches (ratio 0.238, JIT dominated). Timings are diagnostic and not
portable hardware guarantees.

### Reaction-level saturation diagnostics

`ChemicalReaction.getSaturationRatio(system, phaseNumber)` evaluates the same thermodynamic definition for
reaction-backed minerals:

$\mathrm{SR}=\frac{\mathrm{IAP}}{K_{sp}},\quad \mathrm{IAP}=\prod_i a_i^{-\nu_i}$

Here $\nu_i<0$ identifies each dissolved reactant and $a_i$ is its dimensionless activity. Electrolyte-CPA uses its
established mole-fraction/activity-coefficient convention; Pitzer uses solute molality and molality-scale activity
coefficients while retaining the solvent convention. `calcLogSaturationRatio(...)` returns $\ln(\mathrm{SR})$ directly
for trace systems where the linear ratio may underflow.

This activity-based definition is consistent with USGS PHREEQC saturation-index reporting and the calcite equilibrium
treatment of Plummer and Busenberg (1982), DOI `10.1016/0016-7037(82)90056-4`. The regression uses synthetic
compositions and an analytical identity; it copies or fits no external numerical data.

---

## 3. Pitzer Activity Coefficient Model

**Package:** `neqsim.thermo.phase.PhasePitzer`, `neqsim.thermo.component.ComponentGePitzer`

Enhanced Pitzer model with temperature-dependent parameters loaded from database.

### Temperature-Dependent Debye-Hückel

The Debye-Hückel osmotic coefficient $A_\phi$ is computed from:

$$A_\phi = \frac{1.4006 \times 10^6 \sqrt{\rho_w}}{(\varepsilon \cdot T)^{3/2}}$$

Where $\rho_w$ uses Kell (1975) water density and $\varepsilon$ uses Archer & Wang (1990) dielectric constant.

### Temperature-Dependent Binary Parameters

Parameters follow the form:

$$\beta(T) = \beta_{25} + T_1 \left(\frac{1}{T} - \frac{1}{298.15}\right) + T_2 \ln\left(\frac{T}{298.15}\right)$$

The Pitzer parameter database (`PitzerParameters.csv`) currently contains 30 cation-anion rows. Of these, 23 non-estimated rows have populated binary parameters and are covered by regression tests for database loading plus finite mean ionic activity and osmotic coefficients. The covered ions include Na, K, Ca, Mg, Ba, Sr, Fe and H with Cl, SO4, HCO3, CO3 and OH.

At 298.15 K and 1.01325 bara, NaCl has a separate public reference validation against the traceable recommended
values in Tables 6 and 10 of Partanen and Partanen (2020), DOI
[10.1021/acs.jced.0c00402](https://doi.org/10.1021/acs.jced.0c00402), licensed
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The validation covers 0.2, 0.5 and 1.0 mol/kg water;
2.0 and 3.0 mol/kg water are retained as a concentrated hold-out. Pointwise acceptance limits are 2% relative for the
mean molal activity coefficient and 0.75% relative for the osmotic coefficient. The tabulated values are rounded to
0.001 and were derived by the authors from traceable electrochemical, isopiestic, vapor-pressure and solubility
evidence. NeqSim parameters are not fitted or changed by this validation.

This evidence applies only to binary NaCl(aq) at 298.15 K on the molality standard state. It does not validate
temperature dependence, mixed salts, carbonate speciation, mineral parameters, precipitation complementarity or
transfer of Pitzer parameters to electrolyte EOS models.

### Using the Pitzer Model

```java
SystemInterface pitzer = new SystemPitzer(313.15, 50.0);
pitzer.addComponent("methane", 5.0);
pitzer.addComponent("CO2", 0.05);
pitzer.addComponent("n-heptane", 2.0);
pitzer.addComponent("water", 55.5);
pitzer.addComponent("Ca++", 6.0e-4);
pitzer.addComponent("Cl-", 2.0e-4);
pitzer.addComponent("HCO3-", 1.0e-3);
pitzer.chemicalReactionInit();
pitzer.createDatabase(true);
pitzer.setMixingRule("classic");
pitzer.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(pitzer);
ops.TPflash();
pitzer.initProperties();

double calciteScalePotential = ops.getRelativeScalePotential("CaCO3");
```

The example couples SRK gas and oil phases to Pitzer aqueous chemistry. Remove `n-heptane` for a gas-aqueous case.
Its ionic feed is electroneutral: `2 m(Ca++) = m(Cl-) + m(HCO3-)`. The primary-salt coverage topology is therefore
the qualified binary Ca/Cl pair; bicarbonate remains part of the reactive carbonate subsystem.

Do not extend this fixture to a mixed primary salt by adding Na, Ba, Sr, Mg, sulfate, or another ion without defining
the complete binary, same-sign `theta`, and ternary `psi` family from one convention-mapped dataset.
`PhasePitzer.getPitzerParameterCoverage()` reports the exact missing tuples, and standard initialization fails closed
when a mixed primary-salt topology is incomplete. An explicit zero is a scientific parameter definition, not a
placeholder.

The returned value is the calcite saturation ratio, where values above one indicate thermodynamic supersaturation.
It does not calculate precipitated mass or deposition kinetics. The fixed-role hybrid flash currently rejects explicit
solid- and wax-phase checks.

### Other electrolyte GE models

The reactive hybrid solver is selected through the `HybridEosGeFlashModel` contract; it does not contain a Pitzer
type check. `SystemDesmukhMather` and `SystemKentEisenberg` provide the same SRK-gas / SRK-oil / GE-aqueous roles.
For example, a Desmukh-Mather amine system can retain an oil phase, solve aqueous speciation and evaluate the same
activity-based scale-potential API:

```java
SystemDesmukhMather fluid = new SystemDesmukhMather(313.15, 5.0);
fluid.addComponent("methane", 5.0);
fluid.addComponent("CO2", 0.2);
fluid.addComponent("n-heptane", 2.0);
fluid.addComponent("MDEA", 1.0);
fluid.addComponent("water", 9.0);
fluid.addComponent("Ca++", 1.0e-4);
fluid.addComponent("Na+", 1.0e-3);
fluid.addComponent("Cl-", 2.0e-4);
fluid.addComponent("HCO3-", 1.0e-3);
fluid.chemicalReactionInit();
fluid.createDatabase(true);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
operations.TPflash();
double calciteSaturationRatio = operations.getRelativeScalePotential("CaCO3");
```

Every `SystemEosGE` subclass can explicitly configure the same topology through `enableHybridEosGeFlash()`. This
includes Wilson, NRTL, UNIFAC and specialised activity models, but it only supplies the phase-equilibrium topology: a
model must still support water, the requested ions, aqueous reactions and appropriate interaction parameters before its
scale result is meaningful. Pitzer remains the broadly parameterised choice for concentrated mineral-scale brines;
Desmukh-Mather and Kent-Eisenberg are primarily amine/electrolyte screening models. `SystemDuanSun` is not included
because its current system API accepts CO2 only and therefore cannot represent a gas-oil-aqueous electrolyte feed.

---

## 4. WaterCompatibilityScreener

**Package:** `neqsim.pvtsimulation.flowassurance`

Screens formation water and injection water for compatibility by evaluating scale risk at every mixing ratio.

### Usage

```java
WaterCompatibilityScreener screener = new WaterCompatibilityScreener();

// Formation water (high Ba, low SO4)
screener.setFormationWater(
    400,  // Ca mg/L
    200,  // Ba mg/L
    50,   // Sr mg/L
    2,    // Fe mg/L
    150,  // HCO3 mg/L
    10,   // SO4 mg/L
    50000,// TDS mg/L
    90,   // T °C
    200,  // P bara
    3.0,  // CO2 pp bar
    6.2   // pH
);

// Injection water (seawater: low Ba, high SO4)
screener.setInjectionWater(
    400, 0, 5, 0, 140, 2700, 35000, 15, 200, 0.3, 8.1
);

screener.setMixingRatios(new double[]{0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100});
screener.calculate();

System.out.println("Worst case: " + screener.getWorstCaseScale()
    + " at " + screener.getWorstCaseRatio() + "% IW, SI = "
    + screener.getWorstCaseSI());
```

---

## 5. FlowlineScaleProfile

**Package:** `neqsim.pvtsimulation.flowassurance`

Computes scale saturation indices along a pipeline with linearly varying T and P.

### Usage

```java
FlowlineScaleProfile profile = new FlowlineScaleProfile();
profile.setWaterChemistry(400, 10, 5, 0, 150, 10, 40000, 2.0, 6.5);
profile.setInletConditions(90.0, 250.0);   // wellhead: 90°C, 250 bara
profile.setOutletConditions(15.0, 50.0);   // host: 15°C, 50 bara
profile.setNumberOfSegments(50);
profile.calculate();

// Find worst location for each scale
System.out.println("Max CaCO3 SI: " + profile.getMaxSI("CaCO3"));
System.out.println("Max BaSO4 SI: " + profile.getMaxSI("BaSO4"));

String json = profile.toJson();
```

---

## 6. ScaleMassCalculator

**Package:** `neqsim.pvtsimulation.flowassurance`

Estimates the mass of scale precipitated per litre of produced water when SI > 0.

### Usage

```java
// First compute SIs
ScalePredictionCalculator calc = new ScalePredictionCalculator();
// ... set concentrations, T, P ...
calc.calculate();

// Then compute mass
ScaleMassCalculator massCal = new ScaleMassCalculator(calc);
massCal.setWaterVolume(1000.0);  // litres

// Individual mass calculations (mol/L inputs)
double caMolL = 400.0 / 40078.0;    // 400 mg/L Ca / MW
double co3MolL = 150.0 / 61017.0;   // approximate
double mass = massCal.calcCaCO3Mass(caMolL, co3MolL, calc.getCaCO3SaturationIndex());
```

---

## 7. BariteCelestiteSolidSolution

**Package:** `neqsim.pvtsimulation.flowassurance`

Regular solution model for (Ba,Sr)SO4 co-precipitation using Margules 1-parameter model.

At equilibrium:

$$x_{Ba} \gamma_{Ba}^{(s)} K_{sp,BaSO_4} = a_{Ba^{2+}} \cdot a_{SO_4^{2-}}$$

$$x_{Sr} \gamma_{Sr}^{(s)} K_{sp,SrSO_4} = a_{Sr^{2+}} \cdot a_{SO_4^{2-}}$$

Where solid activity coefficients: $\ln \gamma_i^{(s)} = W (1 - x_i)^2$

### Usage

```java
BariteCelestiteSolidSolution ss = new BariteCelestiteSolidSolution();
ss.setAqueousActivities(0.001, 0.005, 0.01);  // aBa, aSr, aSO4
ss.setEndMemberKsp(1.08e-10, 3.44e-7);        // Ksp_BaSO4, Ksp_SrSO4
ss.setMargules(2.3);                           // W/(RT) parameter
ss.calculate();

System.out.println("BaSO4 in solid: " + (ss.getBaSO4MoleFraction() * 100) + "%");
System.out.println("Total SI: " + ss.getTotalSaturationIndex());
```

---

## Coupled mineral-equilibrium diagnostics

`MultiMineralScaleEquilibrium` exposes the numerical evidence needed to decide whether a coupled precipitation result
is acceptable:

- `getIterationCount()` reports coordinate-descent updates;
- `hasReachedIterationLimit()` distinguishes normal step convergence from an exhausted solver budget;
- `getMaximumComplementarityViolation()` reports `abs(SI)` for minerals with solid present and
  `max(SI, 0)` for absent solids;
- `getMaximumIonBalanceResidualMolPerL()` closes each tracked free-ion inventory against the sum of 1:1 mineral
  precipitation extents.

The same values appear in the JSON `diagnostics` object. Acceptance remains case-specific: an iteration-limit flag is
not itself a thermodynamic residual, and a small step does not prove that the mineral inequalities are satisfied. The
reference regression requires maximum complementarity violation <= 1e-3 SI and maximum tracked-ion balance residual <=
1e-12 mol/L. A deliberately one-iteration solve remains material-balanced but fails complementarity, preventing silent
classification as an equilibrated mineral state.

This contract follows the equilibrium-phase convention in Parkhurst (1995), *U.S. Geological Survey
Water-Resources Investigations Report 95-4227*, [doi:10.3133/wri954227](https://doi.org/10.3133/wri954227):
minerals in the stable phase assemblage satisfy the target SI equality, while absent minerals remain inequality
constraints at or below the target. The current PHREEQC 3
[`EQUILIBRIUM_PHASES` documentation](https://water.usgs.gov/water-resources/software/PHREEQC/documentation/phreeqc3-html/phreeqc3-13.htm)
states the same convention. USGS-authored information is
[public domain](https://www.usgs.gov/faqs/are-usgs-reportspublications-copyrighted). No numerical data, parameter,
correlation or PHREEQC code is copied or fitted by this diagnostic regression.

The diagnostic is limited to the standalone coupled solver's tracked free ions and fixed 1:1 mineral stoichiometries.
It does not prove elemental/charge closure for the predictor's aqueous speciation, update activity coefficients during
precipitation, select a globally stable phase topology, or validate Ksp and activity parameters against independent
precipitation data. Full electrolyte EOS/GE calculations must continue to apply their model-specific activity,
speciation and balance gates.

---

## Comparison of thermodynamic routes

| Feature | Davies standalone | Pitzer binary + coupled solids | Electrolyte EOS / full Pitzer |
|---------|--------------------|--------------------------------|-------------------------------|
| Activity coefficients | Charge-based Davies | Ion-specific binary Pitzer | Full fluid speciation/model |
| Ion pairing | 6 explicit pairs | 6 pairs plus trace-ion activity mapping | Thermodynamic phase model |
| Input | Water analysis | Water analysis + salinity/molality | Full fluid definition |
| Best for | Dilute screening | NaCl-dominated oilfield-brine screening | Detailed design, complex brines |
| Ionic strength | Approximately <=0.5 mol/kg | Validated against NaCl data to 6 mol/kg | Model/database dependent |

See [High-Salinity Mineral Scale and Production-Chemical Validation](mineral_scale_chemical_treatment_validation.md)
for published-data errors, treatment scenarios and limitations.

---

## Related Documentation

- [Mineral Scale Formation](mineral_scale_formation.md) — Theory and field examples
- [pH Stabilization & Corrosion](ph_stabilization_corrosion.md) — FeCO3 protective layers
- [Flow Assurance Overview](flow_assurance_overview.md) — Hydrates, wax, asphaltenes, scale
