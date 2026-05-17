---
title: Membrane Separation Equipment
description: Documentation for membrane separation equipment in NeqSim process simulation.
---

# Membrane Separation Equipment

Documentation for membrane separation equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [MembraneSeparator Class](#membraneseparator-class)
- [Permeation Models](#permeation-models)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.membrane`

**Classes:**
| Class | Description |
|-------|-------------|
| `MembraneSeparator` | Generic membrane separation unit |

Membrane separators provide selective separation based on component permeabilities through a membrane material. Applications include:
- CO₂ removal from natural gas
- Hydrogen recovery
- Nitrogen generation
- Dehydration
- Vapor/gas separation

---

## MembraneSeparator Class

### Basic Usage

```java
import neqsim.process.equipment.membrane.MembraneSeparator;

// Create membrane separator
MembraneSeparator membrane = new MembraneSeparator("CO2 Membrane", feedStream);

// Set permeate fractions for each component
membrane.setPermeateFraction("CO2", 0.95);     // 95% of CO2 permeates
membrane.setPermeateFraction("methane", 0.05); // 5% of methane permeates
membrane.setPermeateFraction("ethane", 0.03);  // 3% of ethane permeates

membrane.run();

// Get output streams
StreamInterface permeate = membrane.getPermeateStream();
StreamInterface retentate = membrane.getRetentateStream();
```

### Constructor Options

```java
// With name only
MembraneSeparator membrane = new MembraneSeparator("MEM-100");
membrane.setInletStream(feedStream);

// With name and inlet stream
MembraneSeparator membrane = new MembraneSeparator("MEM-100", feedStream);
```

---

## Permeation Models

### Permeate Fraction Method

The simplest approach specifies what fraction of each component permeates:

```java
// Set permeate fraction (0.0 to 1.0)
membrane.setPermeateFraction("CO2", 0.90);
membrane.setPermeateFraction("H2S", 0.85);
membrane.setPermeateFraction("methane", 0.02);
membrane.setPermeateFraction("ethane", 0.01);
membrane.setPermeateFraction("propane", 0.005);

// Set default for unlisted components
membrane.setDefaultPermeateFraction(0.01);
```

### Permeability Method

For more rigorous calculations using permeability coefficients:

```java
// Set membrane area
membrane.setMembraneArea(100.0);  // m²

// Set permeability for each component (mol/(m²·s·Pa))
membrane.setPermeability("CO2", 1.0e-9);
membrane.setPermeability("methane", 2.0e-11);
membrane.setPermeability("nitrogen", 5.0e-12);
```

### Selectivity

The selectivity of component A over B is:

$$\alpha_{A/B} = \frac{P_A}{P_B}$$

Where $P_A$ and $P_B$ are the permeabilities of components A and B.

Typical selectivities for polymeric membranes:
| Separation | Selectivity |
|------------|-------------|
| CO₂/CH₄ | 15-50 |
| H₂/CH₄ | 30-100 |
| O₂/N₂ | 4-8 |
| H₂O/CH₄ | >100 |

---

## Configuration

### Operating Conditions

```java
// Inlet stream conditions affect separation
feedStream.setPressure(50.0, "bara");  // High feed pressure
feedStream.setTemperature(40.0, "C");

// Permeate side typically at lower pressure
// (Pressure difference drives permeation)
```

### Stage Cut

The stage cut (θ) is the fraction of feed that permeates:

$$\theta = \frac{\dot{n}_{permeate}}{\dot{n}_{feed}}$$

```java
membrane.run();

double feedFlow = feedStream.getFlowRate("kmol/hr");
double permeateFlow = membrane.getPermeateStream().getFlowRate("kmol/hr");
double stageCut = permeateFlow / feedFlow;

System.out.println("Stage cut: " + (stageCut * 100) + " %");
```

---

## Output Streams

### Permeate Stream

The permeate contains components that pass through the membrane:

```java
StreamInterface permeate = membrane.getPermeateStream();

// Get permeate composition
double co2InPermeate = permeate.getFluid().getMoleFraction("CO2");
double permeateFlow = permeate.getFlowRate("kmol/hr");

System.out.println("Permeate CO2: " + (co2InPermeate * 100) + " mol%");
```

### Retentate Stream

The retentate contains components that do not permeate:

```java
StreamInterface retentate = membrane.getRetentateStream();

// Get retentate (product gas) composition
double co2InRetentate = retentate.getFluid().getMoleFraction("CO2");
double ch4InRetentate = retentate.getFluid().getMoleFraction("methane");

System.out.println("Retentate CO2: " + (co2InRetentate * 100) + " mol%");
System.out.println("Retentate CH4: " + (ch4InRetentate * 100) + " mol%");
```

---

## Usage Examples

### CO₂ Removal from Natural Gas

```java
ProcessSystem process = new ProcessSystem();

// Feed gas with CO2
SystemInterface feedFluid = new SystemSrkEos(310.0, 60.0);
feedFluid.addComponent("methane", 0.85);
feedFluid.addComponent("ethane", 0.05);
feedFluid.addComponent("propane", 0.02);
feedFluid.addComponent("CO2", 0.08);
feedFluid.setMixingRule("classic");

Stream feedGas = new Stream("Feed Gas", feedFluid);
feedGas.setFlowRate(100000.0, "Sm3/day");
process.add(feedGas);

// Membrane unit
MembraneSeparator membrane = new MembraneSeparator("CO2 Membrane", feedGas);
membrane.setPermeateFraction("CO2", 0.90);
membrane.setPermeateFraction("methane", 0.03);
membrane.setPermeateFraction("ethane", 0.02);
membrane.setPermeateFraction("propane", 0.01);
process.add(membrane);

// Run
process.run();

// Check CO2 spec
double productCO2 = membrane.getRetentateStream().getFluid().getMoleFraction("CO2");
System.out.println("Product gas CO2: " + (productCO2 * 100) + " mol%");

// Methane recovery
double feedCH4 = feedGas.getFlowRate("Sm3/day") * 0.85;
double productCH4 = membrane.getRetentateStream().getFlowRate("Sm3/day") * 
    membrane.getRetentateStream().getFluid().getMoleFraction("methane");
double recovery = productCH4 / feedCH4 * 100;
System.out.println("Methane recovery: " + recovery + " %");
```

### Multi-Stage Membrane System

For deep CO₂ removal, multiple stages may be required:

```java
// First stage membrane
MembraneSeparator stage1 = new MembraneSeparator("Stage 1", feedGas);
stage1.setPermeateFraction("CO2", 0.80);
stage1.setPermeateFraction("methane", 0.05);
process.add(stage1);

// Second stage on retentate
MembraneSeparator stage2 = new MembraneSeparator("Stage 2", stage1.getRetentateStream());
stage2.setPermeateFraction("CO2", 0.80);
stage2.setPermeateFraction("methane", 0.05);
process.add(stage2);

// Recycle permeate from stage 2 to stage 1 feed
Mixer mixer = new Mixer("Feed Mixer");
mixer.addStream(feedGas);
mixer.addStream(stage2.getPermeateStream());
process.add(mixer);

// Connect mixer to stage 1
stage1.setInletStream(mixer.getOutletStream());

// Add recycle
Recycle recycle = new Recycle("Membrane Recycle");
recycle.addStream(stage2.getPermeateStream());
recycle.setOutletStream(mixer);
process.add(recycle);

process.run();
```

### Hydrogen Recovery

```java
// Refinery off-gas
SystemInterface offgas = new SystemSrkEos(320.0, 30.0);
offgas.addComponent("hydrogen", 0.40);
offgas.addComponent("methane", 0.35);
offgas.addComponent("ethane", 0.15);
offgas.addComponent("propane", 0.10);
offgas.setMixingRule("classic");

Stream feed = new Stream("Off-gas", offgas);
feed.setFlowRate(5000.0, "Sm3/hr");

// H2 selective membrane
MembraneSeparator h2Membrane = new MembraneSeparator("H2 Membrane", feed);
h2Membrane.setPermeateFraction("hydrogen", 0.95);
h2Membrane.setPermeateFraction("methane", 0.08);
h2Membrane.setPermeateFraction("ethane", 0.02);
h2Membrane.setPermeateFraction("propane", 0.01);
h2Membrane.run();

// H2 purity in permeate
double h2Purity = h2Membrane.getPermeateStream().getFluid().getMoleFraction("hydrogen");
System.out.println("H2 purity: " + (h2Purity * 100) + " mol%");
```

---

## Process Integration

### With Compression

```java
// Compress permeate for recycle or further processing
Compressor permeateComp = new Compressor("Permeate Comp", membrane.getPermeateStream());
permeateComp.setOutletPressure(feedPressure, "bara");
permeateComp.setIsentropicEfficiency(0.75);
```

### With Cooling

```java
// Cool membrane feed to improve selectivity
Cooler feedCooler = new Cooler("Membrane Feed Cooler", feedGas);
feedCooler.setOutTemperature(30.0, "C");

membrane.setInletStream(feedCooler.getOutletStream());
```

---

## Design Considerations

### Operating Conditions
- Higher feed pressure → higher driving force → more permeation
- Lower temperature → higher selectivity (for most membranes)
- Higher stage cut → lower product purity

### Membrane Selection

| Type | Applications | Selectivity |
|------|--------------|-------------|
| Cellulose acetate | CO₂/CH₄ | 15-25 |
| Polyimide | CO₂/CH₄, H₂ | 20-50 |
| Polysulfone | O₂/N₂ | 5-6 |
| PDMS | VOC removal | varies |

---

## Related Documentation

- [Absorbers](absorbers) - Alternative for gas treating
- [Separators](separators) - Phase separation
- [Streams](streams) - Stream handling
