---
title: Absorbers and Strippers
description: Documentation for mass transfer columns in NeqSim.
---

# Absorbers and Strippers

Documentation for mass transfer columns in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Absorber](#absorber)
- [Stripper](#stripper)
- [Simple Absorber](#simple-absorber)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.absorber`

**Classes:**
| Class | Description |
|-------|-------------|
| `Absorber` | General absorption column |
| `SimpleAbsorber` | Simplified absorber model |
| `WaterStripperColumn` | Water stripping column |

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

## Related Documentation

- [Equipment Index](./ - All equipment
- [Distillation](distillation) - Distillation columns
- [Separators](separators) - Phase separation
