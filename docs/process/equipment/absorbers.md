---
title: Absorbers and Strippers
description: "Documentation for mass transfer columns in NeqSim: TEG dehydration with Fs-factor sizing, amine gas sweetening (MDEA/DEA/MEA), rate-based absorber with Onda and Billet-Schultes mass transfer correlations, simple absorber models, and water stripping."
---

# Absorbers and Strippers

Documentation for mass transfer columns in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Absorber](#absorber)
- [Simple Absorber](#simple-absorber)
- [Simple TEG Absorber](#simple-teg-absorber)
- [Simple Amine Absorber](#simple-amine-absorber)
- [Stripper](#stripper)
- [Rate-Based Absorber](#rate-based-absorber)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.absorber`

**Classes:**
| Class                   | Description                                           |
| ----------------------- | ----------------------------------------------------- |
| `Absorber`              | General absorption column                             |
| `SimpleAbsorber`        | Simplified absorber model (base class)                |
| `SimpleTEGAbsorber`     | TEG dehydration with Fs-factor sizing                 |
| `SimpleAmineAbsorber`   | Amine gas sweetening (MDEA, DEA, MEA)                 |
| `WaterStripperColumn`   | Water stripping column                                |

Absorbers transfer components from gas to liquid phase, while strippers transfer from liquid to gas.

---

## Absorber

### Basic Usage

```java
import neqsim.process.equipment.absorber.Absorber;

Absorber absorber = new Absorber("Amine Absorber");
absorber.addGasInStream(gasStream);
absorber.addSolventInStream(amineSolution);
absorber.setNumberOfTheoreticalStages(10);
absorber.run();

Stream sweetGas = absorber.getGasOutStream();
Stream richAmine = absorber.getLiquidOutStream();
```

### Absorption Efficiency

```java
// Component removal efficiency
absorber.setRemovalEfficiency("CO2", 0.95);  // 95% CO2 removal
absorber.setRemovalEfficiency("H2S", 0.99);  // 99% H2S removal
```

### Stage Configuration

```java
absorber.setNumberOfTheoreticalStages(20);
absorber.setStageEfficiency(0.7);  // Murphree efficiency
```

---

## Stripper

### Basic Usage

```java
import neqsim.process.equipment.absorber.WaterStripperColumn;

WaterStripperColumn stripper = new WaterStripperColumn("Regenerator");
stripper.setLiquidInStream(richAmine);
stripper.setNumberOfStages(15);
stripper.setReboilerTemperature(120.0, "C");
stripper.run();

Stream leanAmine = stripper.getLiquidOutStream();
Stream acidGas = stripper.getGasOutStream();
```

---

## Simple Absorber

Simplified mass transfer model.

### Usage

```java
import neqsim.process.equipment.absorber.SimpleAbsorber;

SimpleAbsorber absorber = new SimpleAbsorber("CO2 Absorber");
absorber.addGasInStream(feedGas);
absorber.addSolventInStream(solvent);
absorber.setAbsorptionEfficiency(0.90);
absorber.run();
```

### Fs-Factor and Wetting Rate

The base `SimpleAbsorber` class provides Fs-factor and wetting rate calculations used by all absorber subclasses:

$$F_s = v_s \cdot \sqrt{\rho_g}$$

where $v_s$ is the superficial gas velocity (m/s) and $\rho_g$ is gas density (kg/m3).

The wetting rate is the liquid volume flowing per unit packing area:

$$WR = \frac{Q_L}{\pi / 4 \cdot D^2}$$

---

## Simple TEG Absorber

`SimpleTEGAbsorber` extends the base absorber with TEG-specific sizing via the Kremser equation and Fs-factor capacity checking.

### Fs-Factor Sizing

The Fs-factor determines whether the contactor diameter is adequate for the gas load. Industry practice limits Fs to approximately 2.5-3.5 (Pa)^0.5 for structured packing:

```java
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;

SimpleTEGAbsorber tegAbsorber = new SimpleTEGAbsorber("TEG Contactor");
tegAbsorber.addGasInStream(wetGasStream);
tegAbsorber.addSolventInStream(leanTEGStream);
tegAbsorber.run();

// Fs-factor capacity check
double fs = tegAbsorber.getFsFactor();
double maxFs = tegAbsorber.getMaxAllowableFsFactor();  // default 3.0
boolean withinLimit = tegAbsorber.isFsFactorWithinDesignLimit();
double utilization = tegAbsorber.getFsFactorUtilization();  // fs / maxFs

// Minimum diameter needed
double minDiameter = tegAbsorber.getMinimumDiameterForFsLimit();

// Full validation report
String report = tegAbsorber.validateContactorDesign();
System.out.println(report);
```

### TEG Quality and Water Dew Point

```java
// Check equilibrium water dew point from TEG purity
double dewPointC = tegAbsorber.getLeanTEGEquilibriumWaterDewPoint(99.5);
System.out.println("Water dew point at 99.5 wt% TEG: " + dewPointC + " °C");

// Check if TEG quality margin is adequate
boolean adequate = tegAbsorber.hasAdequateTEGQualityMargin(99.5, -18.0);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `getFsFactor()` | Current Fs-factor from gas outlet stream |
| `getMaxAllowableFsFactor()` | Design limit (default 3.0) |
| `setMaxAllowableFsFactor(double)` | Override design limit |
| `isFsFactorWithinDesignLimit()` | Check if Fs is below limit |
| `getFsFactorUtilization()` | Fraction of capacity used (0-1) |
| `getMinimumDiameterForFsLimit()` | Minimum diameter at current gas load |
| `getLeanTEGEquilibriumWaterDewPoint(double)` | Water dew point for a TEG purity |
| `hasAdequateTEGQualityMargin(double, double)` | TEG purity vs target dew point |
| `validateContactorDesign()` | Full text validation report |

---

## Simple Amine Absorber

`SimpleAmineAbsorber` models amine-based gas sweetening for CO2 and H2S removal. It calculates acid gas removal by applying user-specified removal efficiencies, and includes design calculations for column sizing, loading, and validation.

### Basic Usage

```java
import neqsim.process.equipment.absorber.SimpleAmineAbsorber;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create sour gas
SystemSrkEos sourGas = new SystemSrkEos(273.15 + 40.0, 70.0);
sourGas.addComponent("methane", 0.85);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("H2S", 0.005);
sourGas.addComponent("ethane", 0.045);
sourGas.setMixingRule("classic");

Stream sourGasStream = new Stream("Sour Gas", sourGas);
sourGasStream.setFlowRate(50000.0, "kg/hr");

// Create amine absorber
SimpleAmineAbsorber absorber = new SimpleAmineAbsorber("MDEA Absorber", sourGasStream);
absorber.setAmineType("MDEA");
absorber.setAmineConcentrationWtPct(50.0);
absorber.setCO2RemovalEfficiency(0.95);
absorber.setH2SRemovalEfficiency(0.99);

// Wire into process
ProcessSystem process = new ProcessSystem();
process.add(sourGasStream);
process.add(absorber);
process.run();

// Get treated gas
Stream sweetGas = (Stream) absorber.getSweetGasOutStream();
```

### With Lean Amine Stream

When a lean amine stream is provided, the absorber also calculates the rich amine outlet (acid gas picked up by the solvent):

```java
// Lean amine
SystemSrkEos amineFluid = new SystemSrkEos(273.15 + 42.0, 70.0);
amineFluid.addComponent("MDEA", 0.50);
amineFluid.addComponent("water", 0.50);
amineFluid.setMixingRule("classic");

Stream leanAmine = new Stream("Lean Amine", amineFluid);
leanAmine.setFlowRate(30000.0, "kg/hr");

absorber.setLeanAmineInStream(leanAmine);
process.add(leanAmine);
process.run();

// Rich amine with absorbed acid gas
Stream richAmine = (Stream) absorber.getRichAmineOutStream();
```

### Design Calculations

```java
// Acid gas loading
absorber.setLeanAmineLoading(0.01);
absorber.setApproachToEquilibrium(0.70);
double richLoading = absorber.calcRichAmineLoading(0.50);
// richLoading = 0.01 + (0.50 - 0.01) * 0.70 = 0.353 mol/mol

// Amine circulation rate
double rate = absorber.calcRequiredCirculationRate(
    10.0,     // mol/s acid gas to remove
    1050.0,   // kg/m3 amine density
    0.119     // kg/mol MDEA molar mass
);

// Packing height with redistribution sections
absorber.setMaxPackingHeightPerSection(5.5);
absorber.calcPackingHeight(1.0, 12.0);  // HTU=1.0m, NTU=12
int sections = absorber.getNumberOfPackingSections();  // 3

// Demister K-factor
double kFactor = absorber.calcDemisterKFactor(2.0, 50.0, 1050.0);
boolean withinLimit = absorber.isDemisterWithinLimit();  // K <= 0.08

// Temperature margin check
boolean tempOk = absorber.checkAmineTemperatureMargin(30.0, 37.0);  // need 6°C margin

// Foaming derating
double effectiveFlow = absorber.getEffectiveGasCapacityWithFoamingMargin(100.0);  // 120.0
```

### Design Validation

```java
// Run all design checks at once
Map<String, SimpleAmineAbsorber.DesignCheck> checks = absorber.validateDesign();
for (Map.Entry<String, SimpleAmineAbsorber.DesignCheck> entry : checks.entrySet()) {
    System.out.println(entry.getKey() + ": " +
        (entry.getValue().isPassed() ? "PASS" : "FAIL") +
        " - " + entry.getValue().getDetail());
}

// Get full design summary
System.out.println(absorber.getDesignSummary());
```

### Design Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| Amine type | MDEA | Amine solvent (MDEA, DEA, MEA) |
| Concentration | 50 wt% | Amine weight percent in lean solvent |
| CO2 removal | 90% | Overall CO2 removal efficiency |
| H2S removal | 99% | Overall H2S removal efficiency |
| Foaming margin | 20% | Capacity derating for foaming |
| Max packing height | 5.5 m | Maximum per section before redistribution |
| Amine temperature margin | 6 °C | Lean amine T above gas feed T |
| Gas carry-under | 0.03 | Am3 gas per Am3 amine |
| Demister K-factor limit | 0.08 m/s | Wire mesh at gas outlet |
| Approach to equilibrium | 70% | Fraction of thermodynamic equilibrium loading |

---

## Examples

### Example 1: Amine Gas Treating

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.absorber.Absorber;

// Sour gas
SystemSrkCPAstatoil sourGas = new SystemSrkCPAstatoil(313.15, 70.0);
sourGas.addComponent("methane", 0.85);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("H2S", 0.01);
sourGas.addComponent("water", 0.04);
sourGas.setMixingRule("classic");

Stream gasIn = new Stream("Sour Gas", sourGas);
gasIn.setFlowRate(100000.0, "Sm3/hr");
gasIn.run();

// Lean amine (MDEA solution)
SystemSrkCPAstatoil amine = new SystemSrkCPAstatoil(313.15, 70.0);
amine.addComponent("water", 0.50);
amine.addComponent("MDEA", 0.50);
amine.setMixingRule("classic");

Stream leanAmine = new Stream("Lean Amine", amine);
leanAmine.setFlowRate(50000.0, "kg/hr");
leanAmine.run();

// Absorber
Absorber absorber = new Absorber("Amine Contactor");
absorber.addGasInStream(gasIn);
absorber.addSolventInStream(leanAmine);
absorber.setNumberOfTheoreticalStages(15);
absorber.run();

// Results
Stream sweetGas = absorber.getGasOutStream();
double co2Out = sweetGas.getFluid().getMoleFraction("CO2") * 1e6;  // ppm
System.out.println("Sweet gas CO2: " + co2Out + " ppm");
```

### Example 2: TEG Dehydration

```java
// Wet natural gas
SystemSrkEos wetGas = new SystemSrkEos(303.15, 70.0);
wetGas.addComponent("methane", 0.90);
wetGas.addComponent("ethane", 0.05);
wetGas.addComponent("propane", 0.03);
wetGas.addComponent("water", 0.02);
wetGas.setMixingRule("classic");

Stream gasIn = new Stream("Wet Gas", wetGas);
gasIn.setFlowRate(5000000.0, "Sm3/day");
gasIn.run();

// Lean TEG
SystemSrkEos teg = new SystemSrkEos(313.15, 70.0);
teg.addComponent("TEG", 0.99);
teg.addComponent("water", 0.01);
teg.setMixingRule("classic");

Stream leanTEG = new Stream("Lean TEG", teg);
leanTEG.setFlowRate(1000.0, "kg/hr");
leanTEG.run();

// Contactor
Absorber contactor = new Absorber("TEG Contactor");
contactor.addGasInStream(gasIn);
contactor.addSolventInStream(leanTEG);
contactor.setNumberOfTheoreticalStages(3);
contactor.run();

Stream dryGas = contactor.getGasOutStream();
double waterContent = dryGas.getFluid().getMoleFraction("water") * 1e6;
System.out.println("Dry gas water content: " + waterContent + " ppm");
```

### Example 3: Water Wash Column

```java
// Gas with methanol
SystemSrkEos gas = new SystemSrkEos(280.0, 50.0);
gas.addComponent("methane", 0.95);
gas.addComponent("methanol", 0.03);
gas.addComponent("water", 0.02);
gas.setMixingRule("classic");

Stream gasIn = new Stream("Gas", gas);
gasIn.setFlowRate(10000.0, "kg/hr");
gasIn.run();

// Wash water
SystemSrkEos water = new SystemSrkEos(290.0, 50.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");

Stream washWater = new Stream("Wash Water", water);
washWater.setFlowRate(500.0, "kg/hr");
washWater.run();

// Absorber
SimpleAbsorber waterWash = new SimpleAbsorber("Water Wash");
waterWash.addGasInStream(gasIn);
waterWash.addSolventInStream(washWater);
waterWash.setAbsorptionEfficiency(0.85);
waterWash.run();

Stream cleanGas = waterWash.getGasOutStream();
double meohRemaining = cleanGas.getFluid().getMoleFraction("methanol") * 100;
System.out.println("Methanol in clean gas: " + meohRemaining + " mol%");
```

---

## Rate-Based Absorber

`RateBasedAbsorber` extends `SimpleAbsorber` with rigorous mass transfer calculations
using published correlations. Unlike the equilibrium-stage models above, the rate-based
approach computes actual mass transfer rates through gas and liquid film resistances,
giving more physically meaningful predictions of column performance.

### When to Use Rate-Based vs Equilibrium

| Factor | Equilibrium (SimpleAbsorber) | Rate-Based (RateBasedAbsorber) |
|--------|------------------------------|-------------------------------|
| Speed | Fast | Moderate |
| Input data | Minimal (efficiency) | Packing type, column diameter, packed height |
| Physical basis | Assumed HETP/efficiency | Film theory with correlations |
| Reactive systems | Manual adjustment | Enhancement factor models |
| Use case | Screening, quick estimates | Detailed design, packing selection, scale-up |

### Mass Transfer Models

Two correlations are available:

**Onda et al. (1968)** — Classic correlation for random and structured packings:

$$k_G a_w = C \cdot \left(\frac{Re_G}{a_p}\right)^{0.7} Sc_G^{1/3} (a_p D_p)^{-2.0}$$

$$k_L \left(\frac{\rho_L}{\mu_L g}\right)^{1/3} = 0.0051 \left(\frac{Re_L}{a_w}\right)^{2/3} Sc_L^{-1/2} (a_p D_p)^{0.4}$$

**Billet & Schultes (1999)** — Modern correlation with packing-specific constants ($C_l$, $C_v$):

$$k_L = C_l \left(\frac{D_L}{d_h}\right) \sqrt{\frac{u_{Ls}}{a_p \varepsilon_h}}$$

$$k_G = C_v \frac{1}{\varepsilon - h_L} \sqrt{\frac{a_p D_G}{u_{Gs}}}$$

### Enhancement Factor Models

For reactive absorption (e.g., CO2 into amine):

| Model | Use Case |
|-------|----------|
| `NONE` | Physical absorption only |
| `HATTA_PSEUDO_FIRST_ORDER` | Fast pseudo-first-order reaction (e.g., CO2 + MEA) |
| `VAN_KREVELEN_HOFTIJZER` | Second-order reaction with finite amine concentration |

The Hatta number characterises the ratio of reaction rate to diffusion rate:

$$Ha = \frac{\sqrt{k_1 D_L}}{k_L^0}$$

where $k_1$ is the pseudo-first-order rate constant and $D_L$ is liquid diffusivity.

### Basic Usage

```java
import neqsim.process.equipment.absorber.RateBasedAbsorber;
import neqsim.process.equipment.absorber.RateBasedAbsorber.MassTransferModel;
import neqsim.process.equipment.absorber.RateBasedAbsorber.EnhancementModel;

// Create streams (gas and solvent)
RateBasedAbsorber absorber = new RateBasedAbsorber("CO2 Absorber");
absorber.addGasInStream(sourGasStream);
absorber.addSolventInStream(amineSolventStream);

// Column geometry
absorber.setColumnDiameter(2.0);       // 2 m diameter
absorber.setPackedHeight(10.0);        // 10 m packed height
absorber.setNumberOfTheoreticalStages(10);

// Packing properties (e.g., Mellapak 250Y)
absorber.setPackingSpecificArea(250.0);  // m2/m3
absorber.setPackingVoidFraction(0.95);
absorber.setPackingNominalSize(0.025);   // 25 mm
absorber.setPackingCriticalSurfaceTension(0.075);  // N/m

// Mass transfer model
absorber.setMassTransferModel(MassTransferModel.ONDA_1968);

// Enhancement factor for reactive absorption
absorber.setEnhancementModel(EnhancementModel.HATTA_PSEUDO_FIRST_ORDER);
absorber.setReactionRateConstant(5000.0);  // 1/s for CO2 + amine

absorber.run();

// Results
double kGa = absorber.getOverallKGa();
double kLa = absorber.getOverallKLa();
double wettedArea = absorber.getWettedArea();
double htu = absorber.getHeightOfTransferUnit();
double ntu = absorber.getNumberOfTransferUnits();
```

### Stage Results

Per-stage detail is available for profiling the column:

```java
java.util.List<RateBasedAbsorber.StageResult> stages = absorber.getStageResults();
for (RateBasedAbsorber.StageResult stage : stages) {
    System.out.printf("Stage %d: T=%.1f K, kGa=%.4f, kLa=%.4f, E=%.2f%n",
        stage.getStageNumber(),
        stage.getTemperature(),
        stage.getKGa(),
        stage.getKLa(),
        stage.getEnhancementFactor());
}
```

### Billet-Schultes Model

When using Billet-Schultes, supply packing-specific constants from the literature:

```java
absorber.setMassTransferModel(MassTransferModel.BILLET_SCHULTES_1999);
absorber.setBilletSchultesConstants(1.2, 0.4);  // Cl, Cv for Mellapak 250Y
```

### Key Methods Reference

| Method | Description |
|--------|-------------|
| `setMassTransferModel(MassTransferModel)` | `ONDA_1968` or `BILLET_SCHULTES_1999` |
| `setEnhancementModel(EnhancementModel)` | `NONE`, `HATTA_PSEUDO_FIRST_ORDER`, `VAN_KREVELEN_HOFTIJZER` |
| `setColumnDiameter(double)` | Column ID in metres |
| `setPackedHeight(double)` | Height of packing in metres |
| `setPackingSpecificArea(double)` | Packing area per unit volume (m2/m3) |
| `setPackingVoidFraction(double)` | Void fraction (0-1) |
| `setPackingNominalSize(double)` | Nominal packing size (m) |
| `setPackingCriticalSurfaceTension(double)` | Surface tension of packing (N/m) |
| `setReactionRateConstant(double)` | Pseudo-1st-order rate constant (1/s) |
| `setStoichiometricRatio(double)` | Moles of amine per mole of CO2 |
| `setBilletSchultesConstants(double, double)` | Packing constants Cl, Cv |
| `getOverallKGa()` / `getOverallKLa()` | Overall volumetric coefficients (1/s) |
| `getWettedArea()` | Effective wetted area (m2/m3) |
| `getHeightOfTransferUnit()` | HTU based on gas-side (m) |
| `getNumberOfTransferUnits()` | NTU for the given packed height |
| `getStageResults()` | List of per-stage mass transfer results |

---

## Related Documentation

- [Equipment Index](index.md) - All equipment
- [Water Treatment](water_treatment) - Hydrocyclones and gas flotation units
- [Distillation](distillation) - Distillation columns
- [Separators](separators) - Phase separation
- [H2S Scavenger Guide](../H2S_scavenger_guide) - Chemical scavenging of H2S
- [Multiphase Flow Correlations](multiphase_flow_correlations) - Pipeline flow models
