---
title: PVT Simulation Package
description: The `pvtsimulation` package provides tools for simulating standard PVT laboratory experiments used in reservoir fluid characterization.
---

# PVT Simulation Package

The `pvtsimulation` package provides tools for simulating standard PVT laboratory experiments used in reservoir fluid characterization.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [PVT Experiments](#pvt-experiments)
- [Usage Examples](#usage-examples)
- [Integration with Characterization](#integration-with-characterization)

---

## Overview

**Location:** `neqsim.pvtsimulation`

**Purpose:**
- Simulate standard PVT laboratory experiments
- Support fluid characterization workflows
- Model tuning and regression against lab data
- Generate PVT reports

---

## Package Structure

```
pvtsimulation/
├── simulation/                      # PVT experiment simulations
│   ├── BasePVTsimulation.java       # Base class
│   ├── SimulationInterface.java     # Interface
│   │
│   ├── SaturationPressure.java      # Bubble/dew point
│   ├── SaturationTemperature.java   # Saturation temperature
│   │
│   ├── ConstantMassExpansion.java   # CME experiment
│   ├── ConstantVolumeDepletion.java # CVD experiment
│   ├── DifferentialLiberation.java  # DL experiment
│   │
│   ├── SeparatorTest.java           # Single separator
│   ├── MultiStageSeparatorTest.java # Multi-stage separation
│   │
│   ├── SwellingTest.java            # Gas injection swelling
│   ├── SlimTubeSim.java             # Slim tube MMP
│   ├── MMPCalculator.java           # Minimum miscibility pressure
│   │
│   ├── ViscositySim.java            # Viscosity vs pressure
│   ├── ViscosityWaxOilSim.java      # Wax oil viscosity
│   ├── DensitySim.java              # Density vs pressure
│   ├── WaxFractionSim.java          # Wax precipitation
│   └── GOR.java                     # Gas-oil ratio
│
├── regression/                      # Parameter fitting
│   └── PVTRegression.java
│
├── modeltuning/                     # Model tuning
│   └── ModelTuning.java
│
├── reservoirproperties/             # Reservoir calculations
│   └── ReservoirProperties.java
│
├── util/                            # Utilities
│   ├── parameterfitting/            # Parameter fitting utilities
│   │   ├── AsphalteneOnsetFunction.java
│   │   └── AsphalteneOnsetFitting.java
│   └── PVTUtil.java
│
└── flowassurance/                   # Flow assurance analysis
    ├── AsphalteneStabilityAnalyzer.java
    ├── DeBoerAsphalteneScreening.java
    └── AsphalteneMethodComparison.java
```

### Sub-packages

| Package | Documentation | Description |
|---------|---------------|-------------|
| `flowassurance` | [flowassurance/](flowassurance/README) | Asphaltene stability, De Boer screening, CPA onset calculations |

---

## PVT Experiments

### Saturation Pressure

Calculate bubble point or dew point pressure.

```java
import neqsim.pvtsimulation.simulation.SaturationPressure;

SystemInterface oil = new SystemSrkEos(373.15, 200.0);
oil.addComponent("nitrogen", 0.5);
oil.addComponent("CO2", 2.0);
oil.addComponent("methane", 40.0);
oil.addComponent("ethane", 8.0);
oil.addComponent("propane", 5.0);
oil.addComponent("n-pentane", 3.0);
oil.addComponent("n-heptane", 20.0);
oil.addComponent("n-C10", 21.5);
oil.setMixingRule("classic");

SaturationPressure satPres = new SaturationPressure(oil);
satPres.setTemperature(373.15, "K");
satPres.run();

System.out.println("Saturation pressure: " + satPres.getSaturationPressure() + " bar");
```

### Constant Mass Expansion (CME)

Simulates isothermal expansion of reservoir fluid, measuring relative volume.

```java
import neqsim.pvtsimulation.simulation.ConstantMassExpansion;

ConstantMassExpansion cme = new ConstantMassExpansion(oil);
cme.setTemperature(373.15, "K");

// Set pressure steps
double[] pressures = {300, 250, 200, 180, 160, 140, 120, 100, 80, 60, 40};
cme.setPressures(pressures, "bara");

cme.run();

// Get results
double[] relativeVolumes = cme.getRelativeVolume();
double[] liquidVolumes = cme.getLiquidRelativeVolume();
double[] Yvalues = cme.getYfactor();
double[] densities = cme.getDensity();
double[] Zfactors = cme.getZfactor();

// Print results
System.out.println("P (bar)\tVrel\tVliq\tY\tDensity\tZ");
for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%.1f\t%.4f\t%.4f\t%.4f\t%.2f\t%.4f%n",
        pressures[i], relativeVolumes[i], liquidVolumes[i],
        Yvalues[i], densities[i], Zfactors[i]);
}
```

**CME Output Properties:**
- Relative volume (V/Vsat)
- Liquid relative volume
- Y-factor
- Density
- Compressibility factor (Z)

### Constant Volume Depletion (CVD)

Simulates gas condensate depletion at constant volume.

```java
import neqsim.pvtsimulation.simulation.ConstantVolumeDepletion;

SystemInterface condensate = new SystemPrEos(373.15, 400.0);
// Add components...

ConstantVolumeDepletion cvd = new ConstantVolumeDepletion(condensate);
cvd.setTemperature(373.15, "K");

double[] pressures = {400, 350, 300, 250, 200, 150, 100, 50};
cvd.setPressures(pressures, "bara");

cvd.run();

// Results
double[] liquidDropout = cvd.getLiquidVolumeFraction();
double[] cumulativeGasProduced = cvd.getCumulativeGasProduced();
double[] Zfactors = cvd.getZfactor();
double[] Bg = cvd.getBg();
```

**CVD Output Properties:**
- Liquid dropout (volume fraction)
- Cumulative gas produced
- Two-phase Z-factor
- Gas formation volume factor (Bg)
- Produced gas composition (each step)

### Differential Liberation (DL)

Black oil experiment - gas released at each pressure step.

```java
import neqsim.pvtsimulation.simulation.DifferentialLiberation;

DifferentialLiberation dl = new DifferentialLiberation(oil);
dl.setTemperature(373.15, "K");

double[] pressures = {300, 250, 200, 150, 100, 50, 1.01325};
dl.setPressures(pressures, "bara");

dl.run();

// Results
double[] Rs = dl.getRs();           // Solution GOR
double[] Bo = dl.getBo();           // Oil FVF
double[] oilDensity = dl.getOilDensity();
double[] oilViscosity = dl.getOilViscosity();
double[] gasDensity = dl.getGasDensity();
double[] gasGravity = dl.getGasGravity();
double[] Bg = dl.getBg();

System.out.println("P (bar)\tRs (Sm3/Sm3)\tBo\tρ_oil (kg/m3)");
for (int i = 0; i < pressures.length; i++) {
    System.out.printf("%.1f\t%.2f\t\t%.4f\t%.2f%n",
        pressures[i], Rs[i], Bo[i], oilDensity[i]);
}
```

**DL Output Properties:**
- Solution GOR (Rs)
- Oil formation volume factor (Bo)
- Oil density
- Oil viscosity
- Gas gravity
- Gas formation volume factor (Bg)

### Separator Test

Model surface separation conditions.

```java
import neqsim.pvtsimulation.simulation.SeparatorTest;

SeparatorTest sepTest = new SeparatorTest(oil);

// Define separator stages
double[] temperatures = {323.15, 288.15};  // K
double[] pressures = {50.0, 1.01325};       // bar

sepTest.setSeparatorConditions(temperatures, pressures);
sepTest.run();

// Results
double GOR = sepTest.getGOR();
double Bo = sepTest.getBo();
double oilDensity = sepTest.getOilDensity();
double oilMW = sepTest.getOilMolarMass();

System.out.println("GOR: " + GOR + " Sm3/Sm3");
System.out.println("Bo: " + Bo);
System.out.println("Stock tank oil density: " + oilDensity + " kg/m3");
```

### Multi-Stage Separator Test

```java
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;

MultiStageSeparatorTest mst = new MultiStageSeparatorTest(oil);

// Configure stages
mst.addSeparator(50.0, 323.15);   // HP separator
mst.addSeparator(10.0, 308.15);   // LP separator  
mst.addSeparator(1.01325, 288.15); // Stock tank

mst.run();

double[] stageGORs = mst.getStageGOR();
double totalGOR = mst.getTotalGOR();
```

### Swelling Test

Gas injection swelling experiment.

```java
import neqsim.pvtsimulation.simulation.SwellingTest;

SystemInterface injectionGas = new SystemSrkEos(373.15, 200.0);
injectionGas.addComponent("CO2", 1.0);
injectionGas.setMixingRule("classic");

SwellingTest swelling = new SwellingTest(oil);
swelling.setInjectionGas(injectionGas);
swelling.setTemperature(373.15, "K");

// Injection amounts (moles per mole original oil)
double[] injectionAmounts = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5};
swelling.setInjectionAmounts(injectionAmounts);

swelling.run();

double[] saturationPressures = swelling.getSaturationPressures();
double[] swellingFactors = swelling.getSwellingFactors();
double[] oilDensities = swelling.getOilDensities();
```

### MMP Calculation

Minimum miscibility pressure via slim tube simulation.

```java
import neqsim.pvtsimulation.simulation.MMPCalculator;

MMPCalculator mmp = new MMPCalculator(oil, injectionGas);
mmp.setTemperature(373.15, "K");
mmp.run();

double minimumMiscibilityPressure = mmp.getMMP();
System.out.println("MMP: " + minimumMiscibilityPressure + " bar");
```

---

## Viscosity Simulation

```java
import neqsim.pvtsimulation.simulation.ViscositySim;

ViscositySim viscSim = new ViscositySim(oil);
viscSim.setTemperature(373.15, "K");

double[] pressures = {400, 300, 200, 150, 100, 50};
viscSim.setPressures(pressures, "bara");

viscSim.run();

double[] oilViscosities = viscSim.getOilViscosity();
double[] gasViscosities = viscSim.getGasViscosity();
```

---

## Integration with Characterization

### Tuning to Lab Data

```java
import neqsim.pvtsimulation.regression.PVTRegression;

// Set up experiment with measured data
ConstantMassExpansion cme = new ConstantMassExpansion(oil);
cme.setExperimentalData(measuredPressures, measuredRelativeVolumes);

// Create regression
PVTRegression regression = new PVTRegression(oil);
regression.addExperiment(cme);

// Select parameters to tune
regression.tuneParameter("C7+", "Tc", 0.95, 1.05);  // ±5%
regression.tuneParameter("C7+", "Pc", 0.95, 1.05);
regression.tuneParameter("C7+", "acf", 0.90, 1.10);

// Run regression
regression.run();

// Get tuned fluid
SystemInterface tunedOil = regression.getTunedSystem();
```

---

## Complete PVT Study Example

```java
// Create reservoir oil
SystemInterface oil = new SystemSrkEos(373.15, 300.0);
oil.addComponent("nitrogen", 0.5);
oil.addComponent("CO2", 2.1);
oil.addComponent("methane", 35.2);
oil.addComponent("ethane", 7.8);
oil.addComponent("propane", 5.4);
oil.addComponent("i-butane", 1.2);
oil.addComponent("n-butane", 3.1);
oil.addComponent("i-pentane", 1.5);
oil.addComponent("n-pentane", 2.1);
oil.addComponent("n-hexane", 3.5);
oil.addTBPfraction("C7", 8.2, 96.0 / 1000.0, 0.738);
oil.addTBPfraction("C10", 12.4, 134.0 / 1000.0, 0.785);
oil.addTBPfraction("C15", 8.5, 206.0 / 1000.0, 0.835);
oil.addTBPfraction("C20+", 8.5, 450.0 / 1000.0, 0.920);
oil.setMixingRule("classic");
oil.useVolumeCorrection(true);

double reservoirTemp = 373.15;  // K

// 1. Saturation Pressure
SaturationPressure sat = new SaturationPressure(oil);
sat.setTemperature(reservoirTemp, "K");
sat.run();
double Psat = sat.getSaturationPressure();
System.out.println("Bubble point: " + Psat + " bar");

// 2. CME
ConstantMassExpansion cme = new ConstantMassExpansion(oil.clone());
cme.setTemperature(reservoirTemp, "K");
double[] cmePressures = generatePressureRange(Psat + 50, 50, 15);
cme.setPressures(cmePressures, "bara");
cme.run();

// 3. Differential Liberation
DifferentialLiberation dl = new DifferentialLiberation(oil.clone());
dl.setTemperature(reservoirTemp, "K");
double[] dlPressures = generatePressureRange(Psat, 1.01325, 10);
dl.setPressures(dlPressures, "bara");
dl.run();

// 4. Separator Test
SeparatorTest sep = new SeparatorTest(oil.clone());
sep.setSeparatorConditions(
    new double[]{323.15, 288.15},  // Temperatures
    new double[]{30.0, 1.01325}    // Pressures
);
sep.run();

// Print summary
System.out.println("\n=== PVT Summary ===");
System.out.println("Bubble point: " + Psat + " bar");
System.out.println("GOR: " + sep.getGOR() + " Sm3/Sm3");
System.out.println("Bo at Psat: " + dl.getBo()[0]);
System.out.println("Oil density at STC: " + sep.getOilDensity() + " kg/m3");
```

---

## Best Practices

1. **Clone fluids** before running multiple experiments
2. **Use appropriate EoS** for fluid type (CPA for polar, PR for heavy oils)
3. **Match temperature units** carefully (K vs °C)
4. **Validate against lab data** before using for field predictions
5. **Document characterization** for reproducibility

---

## Related Documentation

- [PVT Workflow](pvt_workflow) - End-to-end PVT workflow
- [Fluid Characterization](../thermo/pvt_fluid_characterization) - Heavy end characterization
- [Black Oil Package](../blackoil/README) - Black oil model export
