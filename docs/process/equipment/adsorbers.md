---
title: Adsorbers
description: Documentation for adsorption equipment in NeqSim.
---

# Adsorbers

Documentation for adsorption equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SimpleAdsorber](#simpleadsorber)
- [Key Features](#key-features)
- [Usage Examples](#usage-examples)
- [Parameters](#parameters)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.adsorber`

The adsorber package provides equipment for modeling gas treatment processes using solid adsorbents. Adsorption is commonly used for:
- CO2 removal from natural gas
- Dehydration (water removal)
- Mercury removal
- H2S removal

---

## SimpleAdsorber

The `SimpleAdsorber` class models a simplified adsorption column for gas treatment applications.

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── SimpleAdsorber
```

### Constructor

```java
import neqsim.process.equipment.adsorber.SimpleAdsorber;
import neqsim.process.equipment.stream.Stream;

// Basic constructor
SimpleAdsorber adsorber = new SimpleAdsorber("CO2 Adsorber");

// Constructor with inlet stream
SimpleAdsorber adsorber = new SimpleAdsorber("CO2 Adsorber", feedStream);
```

### Key Properties

| Property | Description | Default |
|----------|-------------|---------|
| `numberOfStages` | Number of theoretical stages | 5 |
| `numberOfTheoreticalStages` | Theoretical stages (continuous) | 3.0 |
| `absorptionEfficiency` | Removal efficiency (0-1) | 0.5 |
| `HTU` | Height of Transfer Unit (m) | 0.85 |
| `NTU` | Number of Transfer Units | 2.0 |
| `stageEfficiency` | Per-stage efficiency | 0.25 |

---

## Key Features

### CO2 Absorption with MDEA

The SimpleAdsorber is configured for CO2 removal using MDEA (methyldiethanolamine) solvent:

```java
// Create gas feed with CO2
SystemInterface gasFluid = new SystemSrkEos(298.15, 50.0);
gasFluid.addComponent("methane", 0.85);
gasFluid.addComponent("CO2", 0.10);
gasFluid.addComponent("nitrogen", 0.05);
gasFluid.setMixingRule("classic");

Stream feedGas = new Stream("Feed Gas", gasFluid);
feedGas.setFlowRate(10000.0, "Sm3/hr");

// Create adsorber
SimpleAdsorber adsorber = new SimpleAdsorber("CO2 Removal", feedGas);

// Run
adsorber.run();

// Get treated gas
StreamInterface treatedGas = adsorber.getOutStream(0);
System.out.println("CO2 in treated gas: " + 
    treatedGas.getFluid().getComponent("CO2").getx() * 100 + " mol%");
```

### Multiple Output Streams

The adsorber provides two output streams:
- `getOutStream(0)` - Treated gas (clean gas)
- `getOutStream(1)` - Rich solvent (solvent loaded with absorbed component)

---

## Usage Examples

### Basic CO2 Removal

```java
import neqsim.process.equipment.adsorber.SimpleAdsorber;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create sour gas
SystemInterface sourGas = new SystemSrkEos(298.15, 50.0);
sourGas.addComponent("methane", 0.80);
sourGas.addComponent("ethane", 0.05);
sourGas.addComponent("CO2", 0.10);
sourGas.addComponent("H2S", 0.02);
sourGas.addComponent("nitrogen", 0.03);
sourGas.setMixingRule("classic");

Stream feed = new Stream("Sour Gas Feed", sourGas);
feed.setFlowRate(5.0, "MSm3/day");

// Create and run adsorber
SimpleAdsorber acidGasRemoval = new SimpleAdsorber("AGRU", feed);
acidGasRemoval.setAbsorptionEfficiency(0.95);
acidGasRemoval.run();

// Results
StreamInterface sweetGas = acidGasRemoval.getOutStream(0);
System.out.println("Sweet gas CO2: " + 
    sweetGas.getFluid().getComponent("CO2").getx() * 1e6 + " ppm");
```

### Integration in Process System

```java
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem process = new ProcessSystem();

// Add equipment
process.add(feedStream);
process.add(adsorber);
process.add(treatedGasExport);

// Run complete process
process.run();
```

---

## Parameters

### Setting Absorption Efficiency

```java
// Set 95% removal efficiency
adsorber.setAbsorptionEfficiency(0.95);
```

### Setting Stage Parameters

```java
adsorber.setNumberOfStages(10);
adsorber.setNumberOfTheoreticalStages(7.5);
adsorber.setStageEfficiency(0.75);
```

### Transfer Unit Parameters

```java
adsorber.setHTU(0.5);  // Height of Transfer Unit in meters
adsorber.setNTU(4.0);  // Number of Transfer Units
```

---

## Mechanical Design

The `SimpleAdsorber` supports mechanical design calculations through `AdsorberMechanicalDesign`:

```java
import neqsim.process.mechanicaldesign.adsorber.AdsorberMechanicalDesign;

AdsorberMechanicalDesign design = adsorber.getMechanicalDesign();
design.calcDesign();

System.out.println("Vessel diameter: " + design.getInnerDiameter() + " m");
System.out.println("Vessel height: " + design.getTotalHeight() + " m");
```

---

## Related Documentation

- [Absorbers](absorbers) - Liquid absorption equipment (TEG, amine)
- [Separators](separators) - Gas-liquid separation
- [Membrane](membranes) - Membrane separation for CO2
- [Chemical Reactions](../../chemicalreactions/) - Reaction chemistry

---

## See Also

- `neqsim.process.equipment.absorber` - Alternative absorption equipment
- `neqsim.thermo.characterization` - Fluid characterization for sour gas
