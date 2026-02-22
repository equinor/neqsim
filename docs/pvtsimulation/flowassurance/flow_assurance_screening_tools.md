---
title: "Flow Assurance Screening Tools"
description: "Pipeline cooldown calculator, CO2 corrosion (de Waard-Milliams), scale prediction (CaCO3/BaSO4/SrSO4), and wax curve monotonicity enforcement for field development studies."
---

# Flow Assurance Screening Tools

NeqSim provides a set of quantitative screening tools for the main flow assurance risks encountered in subsea and onshore pipeline systems. These complement the existing hydrate and asphaltene models.

| Tool | Class | Key Output |
|------|-------|------------|
| Pipeline cooldown | `PipelineCooldownCalculator` | Time to WAT / hydrate temperature |
| CO2 corrosion rate | `DeWaardMilliamsCorrosion` | Corrosion rate (mm/yr), severity |
| Mineral scale | `ScalePredictionCalculator` | Saturation Index for CaCO3, BaSO4, etc. |
| Wax curves | `WaxCurveCalculator` | WAT, wax fraction vs temperature |

All classes live in `neqsim.pvtsimulation.flowassurance`.

---

## Pipeline Cooldown Calculator

Calculates thermal cooldown of a pipeline after shutdown using a lumped-parameter radial heat transfer model. This determines how long operators have before the fluid temperature drops below the WAT or hydrate equilibrium temperature.

### When to Use

- Shutdown/restart planning
- Insulation specification
- Deadleg thermal analysis
- No-touch time assessment

### Java Example

```java
import neqsim.pvtsimulation.flowassurance.PipelineCooldownCalculator;

PipelineCooldownCalculator calc = new PipelineCooldownCalculator();

// Geometry: 10-inch pipeline with 50 mm PUF insulation
calc.setInternalDiameter(0.254);
calc.setWallThickness(0.0127);
calc.setInsulationThickness(0.050);
calc.setInsulationConductivity(0.17);  // W/mK for PUF

// Boundary conditions
calc.setInitialFluidTemperature(273.15 + 80.0);  // 80 C at shutdown
calc.setAmbientTemperature(273.15 + 4.0);         // 4 C seawater

// Fluid properties at shutdown conditions
calc.setFluidDensity(750.0);          // kg/m3
calc.setFluidSpecificHeat(2200.0);    // J/kgK

// Overall U-value (or use useLayerCalculation() to compute from layers)
calc.setOverallUValue(3.0);           // W/m2K

// Time parameters
calc.setTimeStepMinutes(5.0);
calc.setTotalTimeHours(48.0);

// Run
calc.calculate();

// Key results
double timeToHydrate = calc.getTimeToReachTemperature(273.15 + 20.0);
double timeToWAT = calc.getTimeToReachTemperature(273.15 + 35.0);
double tau = calc.getTimeConstantHours();
double tempAt12h = calc.getTemperatureAtTime(12.0);

System.out.println("Time to hydrate temp (20 C): " + timeToHydrate + " hours");
System.out.println("Time to WAT (35 C): " + timeToWAT + " hours");
System.out.println("Time constant: " + tau + " hours");
System.out.println("Temperature at 12 h: " + (tempAt12h - 273.15) + " C");

// Full JSON report
System.out.println(calc.toJson());
```

### Python Example

```python
from neqsim import jneqsim

CooldownCalc = jneqsim.pvtsimulation.flowassurance.PipelineCooldownCalculator

calc = CooldownCalc()
calc.setInternalDiameter(0.254)
calc.setWallThickness(0.0127)
calc.setInsulationThickness(0.050)
calc.setInsulationConductivity(0.17)
calc.setInitialFluidTemperature(273.15 + 80.0)
calc.setAmbientTemperature(273.15 + 4.0)
calc.setFluidDensity(750.0)
calc.setFluidSpecificHeat(2200.0)
calc.setOverallUValue(3.0)
calc.setTotalTimeHours(48.0)
calc.calculate()

time_to_hydrate = calc.getTimeToReachTemperature(273.15 + 20.0)
print(f"Time to hydrate temperature: {time_to_hydrate:.1f} hours")
```

### Model Details

The lumped model treats fluid, steel wall, and insulation as a combined thermal mass:

$$
\frac{dT}{dt} = -\frac{U \cdot A_{outer} \cdot (T_{fluid} - T_{ambient})}{m_{fluid} C_{p,fluid} + m_{steel} C_{p,steel} + m_{ins} C_{p,ins}}
$$

The analytical time constant is:

$$
\tau = \frac{\sum m_i C_{p,i}}{U \cdot A_{outer}}
$$

The U-value can be set directly or computed from individual layer thermal resistances (steel wall, insulation, external convection).

---

## CO2 Corrosion - de Waard-Milliams Model

Predicts internal CO2 corrosion rate for carbon steel pipelines using the de Waard-Milliams (1991) empirical correlation. This is the standard model referenced in NORSOK M-506.

### When to Use

- Material selection (carbon steel vs CRA)
- Corrosion allowance sizing
- Inhibitor requirement assessment
- Pipeline design life calculations

### Java Example

```java
import neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion;

DeWaardMilliamsCorrosion model = new DeWaardMilliamsCorrosion();

// Operating conditions
model.setTemperatureCelsius(60.0);
model.setCO2PartialPressure(2.0);  // bar
model.setPH(4.5);
model.setTotalPressure(100.0);

// Optional corrections
model.setInhibitorEfficiency(0.80);  // 80% inhibitor
model.setGlycolFraction(0.0);
model.setH2SPartialPressure(0.001);

// Calculate
double rate = model.calculateCorrosionRate();
String severity = model.getCorrosionSeverity();
double allowance25yr = model.estimateCorrosionAllowance(25.0);
boolean sour = model.isSourService();

System.out.println("Corrosion rate: " + rate + " mm/yr");
System.out.println("Severity: " + severity);
System.out.println("25-year allowance: " + allowance25yr + " mm");
System.out.println("Sour service: " + sour);

// Temperature profile
java.util.List<java.util.Map<String, Object>> profile =
    model.calculateOverTemperatureRange(20.0, 120.0, 20);

// Full JSON report
System.out.println(model.toJson());
```

### Baseline Equation

$$
\log_{10}(V_{cor}) = 5.8 - \frac{1710}{T + 273.15} + 0.67 \cdot \log_{10}(p_{CO_2})
$$

where $V_{cor}$ is in mm/yr, $T$ in Celsius, $p_{CO_2}$ in bar.

### Correction Factors

The corrected rate applies multiplicative factors:

$$
V_{corrected} = V_{baseline} \times f_{pH} \times f_{scale} \times f_{glycol} \times (1 - IE)
$$

| Factor | Description | Source |
|--------|-------------|--------|
| $f_{pH}$ | pH correction for FeCO3 film | de Waard-Lotz (1993) |
| $f_{scale}$ | Protective scale at high T/pH | Empirical |
| $f_{glycol}$ | Glycol water activity reduction | Empirical |
| $IE$ | Chemical inhibitor efficiency | Field data |

### Severity Classification (NORSOK M-001)

| Category | Rate (mm/yr) |
|----------|-------------|
| Low | less than 0.1 |
| Medium | 0.1 - 0.3 |
| High | 0.3 - 1.0 |
| Very High | greater than 1.0 |

---

## Scale Prediction Calculator

Calculates the Saturation Index (SI) for common oilfield mineral scales based on water chemistry and thermodynamic conditions.

### When to Use

- Produced water management
- Seawater injection compatibility studies
- Scale inhibitor dosing assessment
- Commingling studies

### Supported Scale Types

| Scale | Mineral | Formula |
|-------|---------|---------|
| Calcite | CaCO3 | Calcium carbonate |
| Barite | BaSO4 | Barium sulphate |
| Celestite | SrSO4 | Strontium sulphate |
| Anhydrite | CaSO4 | Calcium sulphate |
| Siderite | FeCO3 | Iron carbonate |

### Java Example

```java
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

ScalePredictionCalculator calc = new ScalePredictionCalculator();

// Conditions
calc.setTemperatureCelsius(80.0);
calc.setPressureBara(100.0);
calc.setCO2PartialPressure(2.0);
calc.setPH(6.5);

// Water chemistry (mg/L)
calc.setCalciumConcentration(1000.0);
calc.setBicarbonateConcentration(500.0);
calc.setBariumConcentration(50.0);
calc.setSulphateConcentration(200.0);
calc.setStrontiumConcentration(10.0);
calc.setIronConcentration(5.0);
calc.setTotalDissolvedSolids(50000.0);

// Calculate
calc.calculate();

// Results
double siCaCO3 = calc.getCaCO3SaturationIndex();
double siBaSO4 = calc.getBaSO4SaturationIndex();
boolean risk = calc.hasScalingRisk();

System.out.println("CaCO3 SI: " + siCaCO3);
System.out.println("BaSO4 SI: " + siBaSO4);
System.out.println("Scaling risk: " + risk);

// All risks
for (String r : calc.getScaleRisks()) {
    System.out.println("  Risk: " + r);
}

// Full JSON report
System.out.println(calc.toJson());
```

### Saturation Index

$$
SI = \log_{10}\left(\frac{IAP}{K_{sp}}\right)
$$

| SI Value | Interpretation |
|----------|---------------|
| SI less than 0 | Undersaturated - no scaling |
| SI = 0 | Equilibrium |
| 0 less than SI less than 0.5 | Moderate tendency |
| SI greater than 0.5 | High scaling tendency |

### Solubility Products

Temperature-dependent $K_{sp}$ correlations are used:

- **CaCO3**: Plummer and Busenberg (1982) calcite correlation
- **BaSO4**: Temperature-adjusted barite correlation
- **SrSO4**: Celestite solubility model
- **CaSO4**: Anhydrite solubility model
- **FeCO3**: Siderite solubility model

Activity coefficients are calculated using the Davies equation with ionic strength estimated from TDS.

---

## Wax Curve Calculator

Calculates wax weight fraction as a function of temperature with post-processing to enforce physical monotonicity. Numerical artifacts from flash calculations can produce non-physical decreases in wax fraction — this calculator corrects them automatically.

### When to Use

- Wax fraction vs temperature curves for flow assurance studies
- WAT determination
- Pipeline restart assessment
- Pigging frequency estimation

### Java Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.pvtsimulation.flowassurance.WaxCurveCalculator;

// Create fluid with wax-forming components
SystemSrkEos fluid = new SystemSrkEos(273.15 + 60, 50.0);
fluid.addComponent("methane", 0.50);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addTBPfraction("C7", 0.10, 95.0, 0.72);
fluid.addTBPfraction("C20", 0.15, 280.0, 0.85);
fluid.addTBPfraction("C30+", 0.10, 450.0, 0.91);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Calculate wax curve
WaxCurveCalculator waxCalc = new WaxCurveCalculator(fluid, 50.0);
waxCalc.setTemperatureRange(273.15 - 10, 273.15 + 60, 1.0);
waxCalc.calculate();

// Results
double wat = waxCalc.getWAT();
int violations = waxCalc.getMonotonicityViolationCount();
System.out.println("WAT: " + (wat - 273.15) + " C");
System.out.println("Monotonicity violations corrected: " + violations);

// Full JSON report
System.out.println(waxCalc.toJson());
```

### Monotonicity Enforcement

The algorithm applies a running-maximum correction from high to low temperature:

1. Flash at each temperature point (high to low)
2. Extract wax phase fraction
3. Apply running maximum: if $w(T_i) < w(T_{i+1})$ where $T_i < T_{i+1}$, set $w(T_i) = w(T_{i+1})$
4. Report number of violations corrected

---

## Related Documentation

- [Flow Assurance Overview](../flow_assurance_overview.md) - Hydrate, wax, asphaltene, scale screening workflows
- [Asphaltene Modeling](asphaltene_modeling.md) - CPA-based asphaltene prediction
- [Mineral Scale Formation](../mineral_scale_formation.md) - Carbonate/sulfate scale and seawater mixing
- [pH Stabilization and Corrosion](../ph_stabilization_corrosion.md) - Corrosion control with Electrolyte CPA EoS
- [Wax Characterization](../../thermo/characterization/wax_characterization.md) - Wax modeling and WAT calculation
