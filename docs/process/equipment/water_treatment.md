---
title: Water Treatment Equipment
description: Documentation for produced water treatment equipment in NeqSim.
---

# Water Treatment Equipment

Documentation for produced water treatment equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Hydrocyclone](#hydrocyclone)
- [Produced Water Treatment Train](#produced-water-treatment-train)
- [Design Considerations](#design-considerations)
- [Regulatory Compliance](#regulatory-compliance)

---

## Overview

**Package:** `neqsim.process.equipment.watertreatment`

Produced water treatment is critical for offshore oil and gas operations. NeqSim provides equipment models for simulating oil-in-water separation processes, helping engineers design systems that meet discharge regulations.

### Key Classes

| Class | Description |
|-------|-------------|
| `Hydrocyclone` | Centrifugal oil-water separator |
| `ProducedWaterTreatmentTrain` | Multi-stage treatment system |

---

## Hydrocyclone

### Overview

Hydrocyclones use centrifugal force to separate oil droplets from water. The swirling flow creates centrifugal acceleration many times greater than gravity, causing lighter oil droplets to migrate to the center and exit through the reject stream.

### Performance Characteristics

| Parameter | Typical Value | Range |
|-----------|--------------|-------|
| d50 cut size | 10-15 μm | 8-20 μm |
| d100 removal | 20-30 μm | 15-40 μm |
| Reject ratio | 1-3% | 0.5-5% |
| Pressure drop | 1-3 bar | 0.5-5 bar |
| Oil removal efficiency | 90-98% | 85-99% |

### Separation Efficiency Model

The grade efficiency is modeled using:

$$\eta(d) = 1 - \exp\left(-A \cdot \left(\frac{d}{d_{50}}\right)^n\right)$$

where:
- $d$ = droplet diameter (μm)
- $d_{50}$ = cut size (50% removal efficiency)
- $n$ = typically 2-4 (sharpness of separation)

### Basic Usage

```java
import neqsim.process.equipment.watertreatment.Hydrocyclone;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create produced water stream
SystemSrkEos water = new SystemSrkEos(323.15, 10.0);
water.addComponent("water", 0.995);
water.addComponent("n-heptane", 0.005);  // Oil phase
water.setMixingRule("classic");

Stream producedWater = new Stream("Produced Water", water);
producedWater.setFlowRate(500.0, "m3/hr");
producedWater.run();

// Create hydrocyclone
Hydrocyclone cyclone = new Hydrocyclone("HP Hydrocyclone", producedWater);
cyclone.setD50Microns(12.0);
cyclone.setRejectRatio(0.02);
cyclone.setPressureDrop(2.0);
cyclone.setOilRemovalEfficiency(0.95);
cyclone.run();

// Get results
System.out.println("Outlet OIW: " + cyclone.getOutletOilConcentrationMgL() + " mg/L");
System.out.println("Recovered oil: " + cyclone.getRecoveredOilM3h() + " m³/h");
```

### Configuration Methods

```java
// Set d50 cut size in microns
cyclone.setD50Microns(12.0);

// Set reject ratio (oil-rich stream / feed)
cyclone.setRejectRatio(0.02);

// Set pressure drop across cyclone
cyclone.setPressureDrop(2.0);

// Set target oil removal efficiency
cyclone.setOilRemovalEfficiency(0.95);

// Set inlet oil concentration
cyclone.setInletOilConcentration(1000.0);  // mg/L
```

### Output Streams

```java
// Treated water (underflow) - main outlet
Stream treatedWater = (Stream) cyclone.getOutletStream();

// Rejected oil-rich stream (overflow)
Stream oilReject = (Stream) cyclone.getOilOutStream();
```

---

## Produced Water Treatment Train

### Overview

The `ProducedWaterTreatmentTrain` models a complete multi-stage treatment system typically used on offshore platforms. It combines multiple treatment technologies to achieve discharge compliance.

### Typical Treatment Stages

| Stage | Equipment | Target Droplets | Efficiency |
|-------|-----------|-----------------|------------|
| Primary | Hydrocyclone | >20 μm | 90-98% |
| Secondary | IGF/DGF | >5 μm | 80-95% |
| Polishing | Skim Tank | >50 μm | 60-80% |

### Basic Usage

```java
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain;
import neqsim.process.equipment.stream.Stream;

// Create treatment train
ProducedWaterTreatmentTrain train = new ProducedWaterTreatmentTrain(
    "PW Treatment", 
    producedWater
);

// Configure inlet conditions
train.setInletOilConcentration(1000.0);  // mg/L from separator
train.setWaterFlowRate(200.0);  // m³/h

// Run simulation
train.run();

// Check compliance
System.out.println("Outlet OIW: " + train.getOutletOilConcentration() + " mg/L");
System.out.println("Compliant: " + train.isCompliant());
System.out.println("Overall efficiency: " + (train.getOverallEfficiency() * 100) + "%");
```

### Stage Types

```java
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain.StageType;

// Available stage types
StageType.HYDROCYCLONE    // Centrifugal separation
StageType.FLOTATION       // IGF/DGF units
StageType.SKIM_TANK       // Gravity separation
StageType.FILTER          // Filtration
StageType.MEMBRANE        // Membrane separation
```

### Custom Stage Configuration

```java
// Clear default stages
train.clearStages();

// Add custom stages
train.addStage("Primary Cyclone", StageType.HYDROCYCLONE, 0.95);
train.addStage("Compact Floatation", StageType.FLOTATION, 0.92);
train.addStage("Final Polish", StageType.SKIM_TANK, 0.75);

// Run with custom configuration
train.run();
```

### Detailed Results

```java
// Get stage-by-stage results
for (WaterTreatmentStage stage : train.getStages()) {
    System.out.println(stage.getName() + ":");
    System.out.println("  Inlet OIW: " + stage.getInletOilMgL() + " mg/L");
    System.out.println("  Outlet OIW: " + stage.getOutletOilMgL() + " mg/L");
    System.out.println("  Efficiency: " + (stage.getEfficiency() * 100) + "%");
}

// Get treated water and oil streams
Stream treatedWater = train.getTreatedWaterStream();
Stream recoveredOil = train.getRecoveredOilStream();
```

---

## Design Considerations

### Droplet Size Distribution

The performance of water treatment equipment depends heavily on the oil droplet size distribution in the feed:

| Source | Typical d50 | Comments |
|--------|-------------|----------|
| HP Separator | 100-300 μm | Large droplets, easy separation |
| LP Separator | 30-100 μm | Moderate separation |
| Degasser | 10-30 μm | Fine droplets, challenging |
| Direct discharge | <10 μm | Very fine, requires flotation |

### Sizing Guidelines

```java
// Hydrocyclone sizing (typical)
double feedFlowM3h = 200.0;
int numberOfLiners = (int) Math.ceil(feedFlowM3h / 35.0);  // ~35 m³/h per liner
double cycloneDP = 1.5 + 0.02 * feedFlowM3h / numberOfLiners;

// Flotation unit sizing
double retentionTime = 3.0;  // minutes
double flotationVolume = feedFlowM3h * retentionTime / 60.0;
```

### Temperature Effects

Oil-water separation efficiency varies with temperature:
- Higher temperature → lower viscosity → faster separation
- Typical operating range: 40-70°C
- Minimum temperature for effective separation: ~30°C

---

## Regulatory Compliance

### Norwegian Continental Shelf (NCS)

| Requirement | Limit | Monitoring |
|-------------|-------|------------|
| Monthly average OIW | 30 mg/L | Weighted average |
| Dispersed oil | Monitored | Daily sampling |
| Zero discharge target | Best available technology | Continuous improvement |

### OSPAR Convention

| Region | OIW Limit | Notes |
|--------|-----------|-------|
| North Sea | 30 mg/L | Monthly average |
| Atlantic | 30 mg/L | Monthly average |

### Compliance Checking

```java
// Check against NCS requirements
boolean ncsCompliant = train.getOutletOilConcentration() 
    <= ProducedWaterTreatmentTrain.NCS_OIW_LIMIT_MGL;

// Check against OSPAR
boolean osparCompliant = train.getOutletOilConcentration() 
    <= ProducedWaterTreatmentTrain.OSPAR_OIW_LIMIT_MGL;

// Get compliance report
String report = train.getComplianceReport();
System.out.println(report);
```

---

## Integration with Process Systems

### Complete Process Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain;

// Create process system
ProcessSystem process = new ProcessSystem();

// Add production separator
ThreePhaseSeparator prodSep = new ThreePhaseSeparator("Production Separator", wellStream);
process.add(prodSep);

// Add water treatment train
ProducedWaterTreatmentTrain pwTrain = new ProducedWaterTreatmentTrain(
    "PW Treatment",
    prodSep.getWaterOutStream()
);
pwTrain.setInletOilConcentration(800.0);
process.add(pwTrain);

// Run process
process.run();

// Check results
System.out.println("Water cut: " + (prodSep.getWaterCut() * 100) + "%");
System.out.println("OIW to discharge: " + pwTrain.getOutletOilConcentration() + " mg/L");
System.out.println("Compliant: " + pwTrain.isCompliant());
```

---

## See Also

- [Separators](separators) - Three-phase separators
- [Filters](filters) - Filtration equipment
- [Membrane Separators](membrane) - Membrane-based separation
