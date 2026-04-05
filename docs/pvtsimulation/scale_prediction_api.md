---
title: "Scale Prediction API Reference"
description: "Complete API reference for NeqSim mineral scale prediction classes. Covers ScalePredictionCalculator (empirical), CheckScalePotential (EOS-based), solid solution models, water compatibility, flowline profiles, and mass calculators."
---

# Scale Prediction API Reference

NeqSim provides two complementary approaches for mineral scale prediction:

1. **Rigorous EOS-based** (`CheckScalePotential`) — Uses Electrolyte CPA or Pitzer activity coefficients from a full thermodynamic flash. Best accuracy, requires complete fluid definition.
2. **Empirical standalone** (`ScalePredictionCalculator`) — Uses Davies equation with ion pairing corrections. Fast screening, needs only water analysis data.

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

$$\beta(T) = \beta_{25} + T_1 \left(\frac{1}{T} - \frac{1}{298.15}\right) + T_2 (T - 298.15)$$

21 ion pairs are included in the Pitzer parameter database (`PitzerParameters.csv`) covering Na, K, Ca, Mg, Ba, Sr, Fe with Cl, SO4, HCO3, CO3, and OH.

### Using the Pitzer Model

```java
SystemInterface pitzer = new SystemPitzer(273.15 + 80.0, 100.0);
pitzer.addComponent("water", 0.90);
pitzer.addComponent("Na+", 0.03);
pitzer.addComponent("Cl-", 0.035);
pitzer.addComponent("Ca++", 0.005);
pitzer.addComponent("SO4--", 0.002);

ThermodynamicOperations ops = new ThermodynamicOperations(pitzer);
ops.TPflash();
pitzer.initProperties();
```

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

## Comparison: Empirical vs EOS-Based

| Feature | ScalePredictionCalculator | CheckScalePotential |
|---------|--------------------------|---------------------|
| Activity coefficients | Davies equation | Electrolyte CPA or Pitzer |
| Ion pairing | 6 explicit pairs | Implicit in EOS |
| Pressure correction | $\Delta V^\circ$ method | $\Delta V^\circ$ method |
| Input | Water analysis (mg/L) | Full fluid definition (moles) |
| Speed | Milliseconds | Seconds (flash required) |
| Best for | Screening, large studies | Detailed design, complex fluids |
| T range | 0-200°C | EOS-limited |
| Ionic strength | Davies valid to ~0.5 M | CPA/Pitzer valid to ~6 M |

---

## Related Documentation

- [Mineral Scale Formation](mineral_scale_formation.md) — Theory and field examples
- [pH Stabilization & Corrosion](ph_stabilization_corrosion.md) — FeCO3 protective layers
- [Flow Assurance Overview](flow_assurance_overview.md) — Hydrates, wax, asphaltenes, scale
