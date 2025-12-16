# PVT Workflow: From Lab Data to Tuned Fluid Model

This document describes the complete workflow for generating and tuning fluid models from laboratory PVT data in NeqSim.

## Typical Laboratory PVT Report Data

A standard PVT laboratory report contains the following sections. Below are example data tables showing the format typically received from labs like Core Lab, Schlumberger, or Intertek.

### Sample Information
```
Well Name:           A-1
Sample Type:         Bottom Hole Sample (BHS)
Sampling Depth:      2850 m TVD
Sampling Date:       2024-06-15
Reservoir Pressure:  285 bara
Reservoir Temp:      98 °C (371.15 K)
Saturation Pressure: 248 bara (Bubble Point)
```

### Compositional Analysis (Mole %)

| Component | Mol % | MW (g/mol) | Density (g/cm³) |
|-----------|-------|------------|-----------------|
| N₂ | 0.34 | 28.01 | - |
| CO₂ | 3.53 | 44.01 | - |
| H₂S | 0.00 | 34.08 | - |
| C₁ | 70.78 | 16.04 | - |
| C₂ | 8.94 | 30.07 | - |
| C₃ | 5.05 | 44.10 | - |
| i-C₄ | 0.85 | 58.12 | - |
| n-C₄ | 1.68 | 58.12 | - |
| i-C₅ | 0.62 | 72.15 | - |
| n-C₅ | 0.79 | 72.15 | - |
| C₆ | 0.83 | 86.18 | 0.664 |
| C₇ | 1.06 | 92.2 | 0.7324 |
| C₈ | 1.06 | 104.6 | 0.7602 |
| C₉ | 0.79 | 119.1 | 0.7677 |
| C₁₀ | 0.57 | 133.0 | 0.790 |
| C₁₁ | 0.38 | 147.0 | 0.795 |
| C₁₂+ | 2.73 | 263.0 | 0.854 |
| **Total** | **100.00** | | |

### Constant Composition Expansion (CCE) at 98°C

| Pressure (bara) | Relative Volume | Y-Factor | Oil Density (kg/m³) | Oil Viscosity (cP) |
|-----------------|-----------------|----------|---------------------|-------------------|
| 350 | 0.9521 | - | 612.3 | 0.285 |
| 300 | 0.9712 | - | 625.1 | 0.312 |
| 280 | 0.9845 | - | 632.8 | 0.328 |
| **248** (Psat) | **1.0000** | - | **645.2** | **0.352** |
| 220 | 1.0523 | 2.145 | 658.4 | 0.385 |
| 200 | 1.1124 | 2.287 | 670.1 | 0.412 |
| 180 | 1.1892 | 2.456 | 682.5 | 0.445 |
| 150 | 1.3245 | 2.712 | 698.2 | 0.498 |
| 120 | 1.5421 | 3.024 | 715.8 | 0.562 |
| 100 | 1.7856 | 3.312 | 728.4 | 0.615 |

### Differential Liberation (DLE) at 98°C

| Pressure (bara) | Rs (Sm³/Sm³) | Bo (m³/Sm³) | Oil Density (kg/m³) | Oil Viscosity (cP) | Gas Z-factor | Gas Gravity |
|-----------------|--------------|-------------|---------------------|-------------------|--------------|-------------|
| **248** (Psat) | 152.3 | 1.4521 | 645.2 | 0.352 | - | - |
| 220 | 138.5 | 1.4012 | 658.4 | 0.385 | 0.862 | 0.745 |
| 200 | 125.2 | 1.3654 | 670.1 | 0.412 | 0.851 | 0.768 |
| 180 | 112.8 | 1.3285 | 682.5 | 0.445 | 0.838 | 0.792 |
| 150 | 94.5 | 1.2756 | 698.2 | 0.498 | 0.815 | 0.825 |
| 120 | 75.2 | 1.2198 | 715.8 | 0.562 | 0.792 | 0.861 |
| 100 | 61.8 | 1.1812 | 728.4 | 0.615 | 0.775 | 0.892 |
| 80 | 48.2 | 1.1425 | 742.1 | 0.685 | 0.758 | 0.928 |
| 50 | 28.5 | 1.0912 | 762.5 | 0.812 | 0.732 | 0.975 |
| 20 | 8.5 | 1.0385 | 785.2 | 1.025 | 0.712 | 1.045 |
| 1.01 (STO) | 0.0 | 1.0000 | 825.4 | 2.850 | - | - |

### Constant Volume Depletion (CVD) at 98°C (for Gas Condensates)

| Pressure (bara) | Liquid Dropout (%) | Z-factor | Cumulative Gas Produced (%) | Liquid Density (kg/m³) |
|-----------------|-------------------|----------|----------------------------|----------------------|
| **285** (Pdew) | 0.0 | 0.892 | 0.0 | - |
| 260 | 4.2 | 0.875 | 8.5 | 612.5 |
| 230 | 8.5 | 0.856 | 18.2 | 628.4 |
| 200 | 12.8 | 0.838 | 28.5 | 645.2 |
| 170 | 15.2 | 0.821 | 39.8 | 658.7 |
| 140 | 14.5 | 0.805 | 51.2 | 672.1 |
| 110 | 12.1 | 0.792 | 62.8 | 685.4 |
| 80 | 8.8 | 0.781 | 74.5 | 698.2 |
| 50 | 5.2 | 0.772 | 86.2 | 712.5 |

### Multi-Stage Separator Test

**Test Conditions:**

| Stage | Pressure (bara) | Temperature (°C) |
|-------|-----------------|------------------|
| 1 (HP Sep) | 45.0 | 45.0 |
| 2 (LP Sep) | 8.0 | 35.0 |
| 3 (Stock Tank) | 1.01 | 15.0 |

**Results:**

| Property | Stage 1 | Stage 2 | Stage 3 | Total |
|----------|---------|---------|---------|-------|
| GOR (Sm³/Sm³) | 95.2 | 18.5 | 8.2 | 121.9 |
| Gas Gravity | 0.752 | 0.985 | 1.245 | 0.812 |
| Oil Density (kg/m³) | 712.5 | 758.2 | 825.4 | - |
| Oil Viscosity (cP) | 0.52 | 0.85 | 2.45 | - |

**Stock Tank Oil Properties:**
- API Gravity: 39.8 °API
- Density: 825.4 kg/m³
- Formation Volume Factor (Bo): 1.385 rm³/Sm³
- Solution GOR (Rs): 121.9 Sm³/Sm³

### Viscosity Data (Separate Measurements)

| Pressure (bara) | Temperature (°C) | Oil Viscosity (cP) | Gas Viscosity (cP) |
|-----------------|------------------|-------------------|-------------------|
| 285 | 98 | 0.285 | 0.0185 |
| 248 | 98 | 0.352 | 0.0178 |
| 200 | 98 | 0.412 | 0.0165 |
| 150 | 98 | 0.498 | 0.0148 |
| 100 | 98 | 0.615 | 0.0132 |
| 248 | 80 | 0.425 | 0.0168 |
| 248 | 60 | 0.585 | 0.0155 |

### Swelling Test (CO₂ Injection)

| Cumulative CO₂ Injected (mol%) | Saturation Pressure (bara) | Relative Swelling Factor | Oil Density (kg/m³) |
|-------------------------------|---------------------------|-------------------------|---------------------|
| 0.0 | 248.0 | 1.000 | 645.2 |
| 5.0 | 268.5 | 1.025 | 638.4 |
| 10.0 | 292.1 | 1.052 | 630.1 |
| 15.0 | 318.4 | 1.082 | 620.5 |
| 20.0 | 348.2 | 1.115 | 608.2 |
| 25.0 | 382.5 | 1.152 | 594.8 |
| 30.0 | 421.8 | 1.195 | 578.5 |

---

## Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Lab PVT Data  │───▶│  Initial Fluid  │───▶│  EOS Regression │───▶│  Export Model   │
│   (CCE,DLE,CVD) │    │  Characterization│    │  (Parameter Fit)│    │  (E300, CSV)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Step 1: Create Initial Fluid from Composition

Start with laboratory-reported composition and characterize heavy fractions:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

// Create fluid at reservoir conditions
SystemInterface fluid = new SystemSrkEos(373.15, 250.0);  // T(K), P(bar)

// Add defined components (from lab composition)
fluid.addComponent("nitrogen", 0.34);
fluid.addComponent("CO2", 3.53);
fluid.addComponent("methane", 70.78);
fluid.addComponent("ethane", 8.94);
fluid.addComponent("propane", 5.05);
fluid.addComponent("i-butane", 0.85);
fluid.addComponent("n-butane", 1.68);
fluid.addComponent("i-pentane", 0.62);
fluid.addComponent("n-pentane", 0.79);
fluid.addComponent("n-hexane", 0.83);

// Add C7+ fractions (TBP cuts from lab)
fluid.addTBPfraction("C7", 1.06, 92.2/1000.0, 0.7324);   // name, mole%, MW(kg/mol), SG
fluid.addTBPfraction("C8", 1.06, 104.6/1000.0, 0.7602);
fluid.addTBPfraction("C9", 0.79, 119.1/1000.0, 0.7677);
fluid.addTBPfraction("C10", 0.57, 133.0/1000.0, 0.79);

// Add plus fraction
fluid.addPlusFraction("C20+", 2.11, 381.0/1000.0, 0.88);

// Characterize plus fraction into pseudo-components
fluid.getCharacterization().characterisePlusFraction();

// Initialize database and set mixing rule
fluid.createDatabase(true);
fluid.setMixingRule(2);  // Classic mixing rule
```

## Step 2: Add Laboratory PVT Data

Import experimental data from CCE, DLE, CVD, and separator tests:

```java
import neqsim.pvtsimulation.regression.PVTRegression;
import neqsim.pvtsimulation.regression.RegressionParameter;

PVTRegression regression = new PVTRegression(fluid);

// CCE (Constant Composition Expansion) data
double[] ccePressures = {400, 350, 300, 280, 260, 240, 220, 200, 180};  // bara
double[] cceRelVol = {0.95, 0.97, 0.99, 1.00, 1.02, 1.05, 1.10, 1.18, 1.30};
double cceTemperature = 373.15;  // K
regression.addCCEData(ccePressures, cceRelVol, cceTemperature);

// DLE (Differential Liberation) data
double[] dlePressures = {280, 240, 200, 160, 120, 80, 40, 1.01325};  // bara
double[] dleRs = {150, 130, 110, 85, 60, 35, 15, 0};  // Sm³/Sm³
double[] dleBo = {1.45, 1.40, 1.35, 1.28, 1.20, 1.12, 1.06, 1.02};  // m³/Sm³
double[] dleOilDensity = {650, 680, 710, 740, 770, 800, 830, 850};  // kg/m³
double dleTemperature = 373.15;  // K
regression.addDLEData(dlePressures, dleRs, dleBo, dleOilDensity, dleTemperature);

// CVD (Constant Volume Depletion) data - for gas condensates
double[] cvdPressures = {350, 300, 250, 200, 150, 100};  // bara
double[] cvdLiquidDropout = {0, 5, 12, 18, 15, 10};  // volume %
double[] cvdZFactor = {0.85, 0.82, 0.80, 0.78, 0.77, 0.76};
double cvdTemperature = 373.15;  // K
regression.addCVDData(cvdPressures, cvdLiquidDropout, cvdZFactor, cvdTemperature);

// Separator test data
regression.addSeparatorData(
    125.0,    // GOR (Sm³/Sm³)
    1.35,     // Bo
    35.0,     // API gravity
    50.0,     // separator pressure (bar)
    313.15,   // separator temperature (K)
    373.15    // reservoir temperature (K)
);
```

## Step 3: Configure Regression Parameters

Select which EOS parameters to tune:

```java
// Binary Interaction Parameters (BIPs)
regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS);
regression.addRegressionParameter(RegressionParameter.BIP_C2C6_C7PLUS);
regression.addRegressionParameter(RegressionParameter.BIP_CO2_HC);

// Critical property multipliers for C7+ pseudo-components
regression.addRegressionParameter(RegressionParameter.TC_MULTIPLIER_C7PLUS);
regression.addRegressionParameter(RegressionParameter.PC_MULTIPLIER_C7PLUS);
regression.addRegressionParameter(RegressionParameter.OMEGA_MULTIPLIER_C7PLUS);

// Volume shift for density matching
regression.addRegressionParameter(RegressionParameter.VOLUME_SHIFT_C7PLUS);

// Viscosity parameters (if tuning viscosity)
regression.addRegressionParameter(RegressionParameter.VISCOSITY_LBC_MULTIPLIER);
regression.addRegressionParameter(RegressionParameter.VISCOSITY_PEDERSEN_ALPHA);

// Custom bounds (optional)
regression.addRegressionParameter(
    RegressionParameter.BIP_METHANE_C7PLUS, 
    0.0,    // lower bound
    0.15,   // upper bound
    0.05    // initial guess
);

// Set experiment weights (optional)
regression.setExperimentWeight(ExperimentType.CCE, 1.0);
regression.setExperimentWeight(ExperimentType.DLE, 1.5);  // Higher weight for DLE
regression.setExperimentWeight(ExperimentType.SEPARATOR, 1.0);

// Optimization settings
regression.setMaxIterations(200);
regression.setTolerance(1e-8);
regression.setVerbose(true);
```

## Step 4: Run Regression

Execute the optimization:

```java
import neqsim.pvtsimulation.regression.RegressionResult;

RegressionResult result = regression.runRegression();

// Get the tuned fluid
SystemInterface tunedFluid = result.getTunedFluid();

// Check results
System.out.println("Final chi-square: " + result.getChiSquare());
System.out.println("Optimized parameters:");
double[] params = result.getOptimizedParameters();
for (int i = 0; i < params.length; i++) {
    System.out.println("  Parameter " + i + ": " + params[i]);
}
```

## Step 5: Validate and Compare with Lab Data

Generate comparison reports:

```java
import neqsim.pvtsimulation.util.PVTReportGenerator;
import neqsim.pvtsimulation.simulation.*;

// Run PVT simulations with tuned fluid
ConstantMassExpansion cce = new ConstantMassExpansion(tunedFluid);
cce.setTemperature(373.15);
cce.setPressures(ccePressures);
cce.runCalc();

DifferentialLiberation dle = new DifferentialLiberation(tunedFluid);
dle.setTemperature(373.15);
dle.setPressures(dlePressures);
dle.runCalc();

// Create report generator
PVTReportGenerator report = new PVTReportGenerator(tunedFluid);
report.setProjectInfo("Field X Development", "Well A-1 Sample");
report.setReservoirConditions(250.0, 100.0);  // P(bar), T(°C)

// Add simulation results
report.addCCE(cce);
report.addDLE(dle);

// Add lab data for comparison
for (int i = 0; i < ccePressures.length; i++) {
    report.addLabCCEData(ccePressures[i], "RelVol", cceRelVol[i], "");
}
for (int i = 0; i < dlePressures.length; i++) {
    report.addLabDLEData(dlePressures[i], "Bo", dleBo[i], "m³/Sm³");
    report.addLabDLEData(dlePressures[i], "Rs", dleRs[i], "Sm³/Sm³");
}

// Generate comparison with statistics
String comparison = report.generateLabComparison();
System.out.println(comparison);
// Output includes AAD (Average Absolute Deviation) and ARE (Average Relative Error)

// Generate full Markdown report
String fullReport = report.generateMarkdownReport();
```

## Step 6: Export to Reservoir Simulator

Export the tuned fluid model to Eclipse E300 format:

```java
import neqsim.blackoil.io.EclipseEOSExporter;
import java.nio.file.Path;

// Simple export with default settings
EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT_TUNED.INC"));

// Export with custom configuration
EclipseEOSExporter.ExportConfig config = new EclipseEOSExporter.ExportConfig()
    .setUnits(EclipseEOSExporter.Units.FIELD)      // METRIC or FIELD
    .setReferenceTemperature(373.15)               // Reservoir temp (K)
    .setIncludePVTO(true)                          // Live oil table
    .setIncludePVTG(true)                          // Wet gas table
    .setIncludePVTW(true)                          // Water properties
    .setIncludeDensity(true)                       // Stock tank densities
    .setComment("Tuned to Well A-1 PVT data");

EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT_FIELD.INC"), config);

// Or get as string for inspection
String eclipseContent = EclipseEOSExporter.toString(tunedFluid, config);
System.out.println(eclipseContent);
```

### Generated Eclipse Keywords

The exporter produces standard Eclipse keywords:

- **PVTO** - Live oil PVT table (Rs, P, Bo, viscosity)
- **PVTG** - Wet gas PVT table (Rv, P, Bg, viscosity)  
- **PVTW** - Water PVT properties
- **DENSITY** - Stock tank densities (oil, water, gas)

### Unit Systems

| Unit System | Pressure | Density | GOR/CGR | Viscosity |
|-------------|----------|---------|---------|-----------|
| METRIC | bar | kg/m³ | Sm³/Sm³ | mPa·s |
| FIELD | psia | lb/ft³ | scf/stb | cp |

## Step 7: Export CSV for Other Applications

Generate CSV files for spreadsheet analysis or other simulators:

```java
// CCE data
String cceCSV = report.generateCCECSV();

// DLE data
String dleCSV = report.generateDLECSV();

// CVD data  
String cvdCSV = report.generateCVDCSV();

// Viscosity data
String viscCSV = report.generateViscosityCSV();

// Density data
String densCSV = report.generateDensityCSV();

// Swelling test
String swellCSV = report.generateSwellingCSV();

// GOR data
String gorCSV = report.generateGORCSV();

// MMP data
String mmpCSV = report.generateMMPCSV();
```

## Available Regression Parameters

| Parameter | Description | Typical Bounds |
|-----------|-------------|----------------|
| `BIP_METHANE_C7PLUS` | BIP between CH₄ and C7+ | 0.0 - 0.10 |
| `BIP_C2C6_C7PLUS` | BIP between C2-C6 and C7+ | 0.0 - 0.05 |
| `BIP_CO2_HC` | BIP between CO₂ and hydrocarbons | 0.08 - 0.18 |
| `BIP_N2_HC` | BIP between N₂ and hydrocarbons | 0.02 - 0.12 |
| `VOLUME_SHIFT_C7PLUS` | Volume shift multiplier for C7+ | 0.8 - 1.2 |
| `TC_MULTIPLIER_C7PLUS` | Critical temperature multiplier | 0.95 - 1.05 |
| `PC_MULTIPLIER_C7PLUS` | Critical pressure multiplier | 0.95 - 1.05 |
| `OMEGA_MULTIPLIER_C7PLUS` | Acentric factor multiplier | 0.90 - 1.10 |
| `PLUS_MOLAR_MASS_MULTIPLIER` | Plus fraction MW adjustment | 0.90 - 1.10 |
| `GAMMA_ALPHA` | Whitson gamma distribution shape | 0.5 - 4.0 |
| `GAMMA_ETA` | Whitson gamma min MW (η) | 75.0 - 95.0 |
| `VISCOSITY_LBC_MULTIPLIER` | LBC viscosity correlation factor | 0.8 - 1.5 |
| `VISCOSITY_PEDERSEN_ALPHA` | Pedersen viscosity parameter | 0.5 - 2.0 |

## Separator Optimization

Find optimal separator conditions:

```java
import neqsim.pvtsimulation.simulation.MultiStageSeparatorTest;

MultiStageSeparatorTest sepTest = new MultiStageSeparatorTest(tunedFluid);
sepTest.setReservoirConditions(250.0, 373.15);  // P(bar), T(K)

// Add separator stages
sepTest.addSeparatorStage(50.0, 40.0, "HP Separator");   // P(bar), T(°C)
sepTest.addSeparatorStage(10.0, 30.0, "LP Separator");
sepTest.addSeparatorStage(1.01325, 15.0, "Stock Tank");

// Run simulation
sepTest.run();

// Optimize first stage pressure/temperature
MultiStageSeparatorTest.OptimizationResult optResult = 
    sepTest.optimizeFirstStageSeparator(
        5.0, 80.0, 16,    // pressure: min, max, steps
        20.0, 60.0, 9     // temperature: min, max, steps
    );

System.out.println("Optimal P: " + optResult.getOptimalPressure() + " bara");
System.out.println("Optimal T: " + optResult.getOptimalTemperature() + " °C");
System.out.println("Max Recovery: " + optResult.getMaximumOilRecovery());
System.out.println("GOR at optimum: " + optResult.getGorAtOptimum() + " Sm³/Sm³");
```

## Complete Example

See [PVTRegressionTest.java](../src/test/java/neqsim/pvtsimulation/regression/PVTRegressionTest.java) for working examples.

## Related Documentation

- [Black Oil PVT Export](blackoil_pvt_export.md)
- [Fluid Characterization Mathematics](fluid_characterization_mathematics.md)
- [Whitson PVT Reader](whitson_pvt_reader.md)
