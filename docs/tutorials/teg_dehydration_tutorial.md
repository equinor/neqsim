---
title: TEG Gas Dehydration Tutorial
description: Complete tutorial for modeling triethylene glycol (TEG) gas dehydration systems in NeqSim. Covers contactor design, regeneration, water content specifications, and performance optimization.
---

# TEG Gas Dehydration Tutorial

This tutorial demonstrates how to model a complete triethylene glycol (TEG) gas dehydration system in NeqSim, including the absorption contactor, flash drum, and regeneration system.

## Overview

TEG dehydration is the most common method for removing water from natural gas to meet pipeline specifications (typically < 7 lb/MMscf or 112 mg/Sm³).

### Process Flow

```
Wet Gas → Inlet Separator → TEG Contactor → Dry Gas
                                ↓
                           Rich TEG
                                ↓
                           Flash Drum → Flash Gas
                                ↓
                           Regenerator → Water Vapor
                                ↓
                           Lean TEG (recycle)
```

---

## Quick Start Example

### Java

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Create wet gas feed
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(303.15, 70.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.02);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.01);  // Saturated with water
fluid.setMixingRule(10);  // CPA mixing rule
fluid.setMultiPhaseCheck(true);

// Create process
ProcessSystem process = new ProcessSystem();

// Wet gas feed
Stream wetGas = new Stream("Wet Gas", fluid);
wetGas.setFlowRate(10.0, "MSm3/day");
wetGas.setTemperature(30.0, "C");
wetGas.setPressure(70.0, "bara");
process.add(wetGas);

// Lean TEG stream
SystemSrkCPAstatoil tegFluid = new SystemSrkCPAstatoil(303.15, 70.0);
tegFluid.addComponent("TEG", 0.99);
tegFluid.addComponent("water", 0.01);  // 99% lean TEG
tegFluid.setMixingRule(10);

Stream leanTEG = new Stream("Lean TEG", tegFluid);
leanTEG.setFlowRate(3000.0, "kg/hr");  // TEG circulation rate
leanTEG.setTemperature(35.0, "C");
leanTEG.setPressure(70.0, "bara");
process.add(leanTEG);

// TEG Contactor (absorber)
SimpleTEGAbsorber contactor = new SimpleTEGAbsorber("TEG Contactor");
contactor.addGasInStream(wetGas);
contactor.addSolventInStream(leanTEG);
contactor.setNumberOfStages(6);
process.add(contactor);

// Run simulation
process.run();

// Get results
Stream dryGas = contactor.getGasOutStream();
System.out.println("Dry gas water content: " + 
    dryGas.getFluid().getComponent("water").getx() * 1e6 + " ppm");
```

### Python

```python
from neqsim import jneqsim

# Classes
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
SimpleTEGAbsorber = jneqsim.process.equipment.absorber.SimpleTEGAbsorber

# Create wet gas
fluid = SystemSrkCPAstatoil(273.15 + 30.0, 70.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.02)
fluid.addComponent("CO2", 0.02)
fluid.addComponent("water", 0.01)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

# Process setup
process = ProcessSystem()

wet_gas = Stream("Wet Gas", fluid)
wet_gas.setFlowRate(10.0, "MSm3/day")
wet_gas.setTemperature(30.0, "C")
wet_gas.setPressure(70.0, "bara")
process.add(wet_gas)

# Lean TEG
teg_fluid = SystemSrkCPAstatoil(273.15 + 35.0, 70.0)
teg_fluid.addComponent("TEG", 0.99)
teg_fluid.addComponent("water", 0.01)
teg_fluid.setMixingRule(10)

lean_teg = Stream("Lean TEG", teg_fluid)
lean_teg.setFlowRate(3000.0, "kg/hr")
lean_teg.setTemperature(35.0, "C")
lean_teg.setPressure(70.0, "bara")
process.add(lean_teg)

# Contactor
contactor = SimpleTEGAbsorber("TEG Contactor")
contactor.addGasInStream(wet_gas)
contactor.addSolventInStream(lean_teg)
contactor.setNumberOfStages(6)
process.add(contactor)

# Run
process.run()

# Results
dry_gas = contactor.getGasOutStream()
print(f"Dry gas water content: {dry_gas.getFluid().getComponent('water').getx() * 1e6:.1f} ppm")
```

---

## Complete TEG Loop with Regeneration

For a complete TEG loop including regeneration:

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.distillation.Reboiler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemSrkCPAstatoil;

public class TEGDehydrationProcess {
    public static void main(String[] args) {
        
        // === FEED STREAMS ===
        
        // Wet natural gas
        SystemSrkCPAstatoil wetGasFluid = new SystemSrkCPAstatoil(303.15, 70.0);
        wetGasFluid.addComponent("methane", 0.85);
        wetGasFluid.addComponent("ethane", 0.06);
        wetGasFluid.addComponent("propane", 0.03);
        wetGasFluid.addComponent("i-butane", 0.01);
        wetGasFluid.addComponent("n-butane", 0.01);
        wetGasFluid.addComponent("CO2", 0.02);
        wetGasFluid.addComponent("nitrogen", 0.01);
        wetGasFluid.addComponent("water", 0.01);  // Water saturated
        wetGasFluid.setMixingRule(10);
        wetGasFluid.setMultiPhaseCheck(true);
        
        ProcessSystem process = new ProcessSystem();
        
        Stream wetGas = new Stream("Wet Gas Feed", wetGasFluid);
        wetGas.setFlowRate(5.0, "MSm3/day");
        wetGas.setTemperature(35.0, "C");
        wetGas.setPressure(70.0, "bara");
        process.add(wetGas);
        
        // Lean TEG makeup (99.5% purity)
        SystemSrkCPAstatoil leanTEGFluid = new SystemSrkCPAstatoil(303.15, 70.0);
        leanTEGFluid.addComponent("TEG", 0.995);
        leanTEGFluid.addComponent("water", 0.005);
        leanTEGFluid.setMixingRule(10);
        
        Stream leanTEG = new Stream("Lean TEG", leanTEGFluid);
        leanTEG.setFlowRate(2500.0, "kg/hr");
        leanTEG.setTemperature(40.0, "C");
        leanTEG.setPressure(70.0, "bara");
        process.add(leanTEG);
        
        // === ABSORPTION SECTION ===
        
        // TEG Contactor (6 trays typical)
        SimpleTEGAbsorber contactor = new SimpleTEGAbsorber("TEG Contactor");
        contactor.addGasInStream(wetGas);
        contactor.addSolventInStream(leanTEG);
        contactor.setNumberOfStages(6);
        process.add(contactor);
        
        // Dry gas product
        Stream dryGas = contactor.getGasOutStream();
        dryGas.setName("Dry Gas Product");
        process.add(dryGas);
        
        // Rich TEG to regeneration
        Stream richTEG = contactor.getLiquidOutStream();
        richTEG.setName("Rich TEG");
        process.add(richTEG);
        
        // === REGENERATION SECTION ===
        
        // Rich TEG pressure letdown
        ThrottlingValve tegValve = new ThrottlingValve("TEG Letdown Valve", richTEG);
        tegValve.setOutletPressure(5.0);  // 5 bara flash pressure
        process.add(tegValve);
        
        // Flash drum to remove dissolved gas
        Separator flashDrum = new Separator("Flash Drum", tegValve.getOutletStream());
        process.add(flashDrum);
        
        Stream flashGas = flashDrum.getGasOutStream();
        flashGas.setName("Flash Gas");
        process.add(flashGas);
        
        Stream flashedTEG = flashDrum.getLiquidOutStream();
        flashedTEG.setName("Flashed TEG");
        process.add(flashedTEG);
        
        // Lean/Rich TEG heat exchanger
        HeatExchanger tegExchanger = new HeatExchanger("Lean/Rich Exchanger");
        tegExchanger.setFeedStream(0, flashedTEG);
        // Hot side would be lean TEG from reboiler (simplified here)
        process.add(tegExchanger);
        
        // TEG Regenerator (reboiler)
        // Temperature: 200-204°C for 99%+ purity
        // Note: Full regenerator would use DistillationColumn
        
        // === RUN SIMULATION ===
        process.run();
        
        // === RESULTS ===
        System.out.println("=== TEG Dehydration Results ===");
        System.out.println();
        
        // Wet gas water content
        double wetWaterMoleFrac = wetGas.getFluid().getComponent("water").getx();
        System.out.printf("Wet gas water content: %.0f ppm (mole)%n", 
            wetWaterMoleFrac * 1e6);
        
        // Dry gas water content
        double dryWaterMoleFrac = dryGas.getFluid().getComponent("water").getx();
        System.out.printf("Dry gas water content: %.1f ppm (mole)%n", 
            dryWaterMoleFrac * 1e6);
        
        // Water removal efficiency
        double efficiency = (1.0 - dryWaterMoleFrac / wetWaterMoleFrac) * 100;
        System.out.printf("Water removal efficiency: %.2f%%%n", efficiency);
        
        // Rich TEG water loading
        double richWaterMoleFrac = richTEG.getFluid().getComponent("water").getx();
        System.out.printf("Rich TEG water content: %.2f%% (mole)%n", 
            richWaterMoleFrac * 100);
        
        // Flash gas rate
        System.out.printf("Flash gas rate: %.2f kg/hr%n", 
            flashGas.getFlowRate("kg/hr"));
    }
}
```

---

## Design Parameters

### Typical Operating Conditions

| Parameter | Typical Range | Notes |
|-----------|---------------|-------|
| Contactor pressure | 40-100 bara | Higher pressure improves absorption |
| Contactor temperature | 25-40°C | Lower temp improves absorption |
| Number of trays | 4-8 | 6 trays typical |
| TEG circulation rate | 15-40 L TEG/kg H₂O | Higher rate = drier gas |
| Lean TEG purity | 99.0-99.9% | 99.5% typical, >99.9% with stripping gas |
| Regenerator temperature | 200-204°C | Max 204°C to prevent degradation |
| Regenerator pressure | 0.1-0.5 bara | Atmospheric typical |

### Water Content Specifications

| Specification | Limit | Unit |
|---------------|-------|------|
| Pipeline spec (typical) | 7 | lb/MMscf |
| Pipeline spec (SI) | 112 | mg/Sm³ |
| Cryogenic processing | 1 | ppm |
| LNG feed | 0.1 | ppm |

### TEG Circulation Rate Calculation

The TEG circulation rate affects dew point depression:

$$
\text{TEG Rate} = \frac{W_{in} \times Q_{gas}}{W_{cap} \times \rho_{TEG}}
$$

Where:
- $W_{in}$ = inlet water content (kg/Sm³)
- $Q_{gas}$ = gas flow rate (Sm³/hr)
- $W_{cap}$ = TEG water pickup capacity (kg H₂O/kg TEG)
- $\rho_{TEG}$ = TEG density (kg/m³)

---

## Water Dew Point Calculation

To calculate the water dew point of the dry gas:

```java
// After running process
Stream dryGas = contactor.getGasOutStream();
SystemInterface dryGasFluid = dryGas.getFluid();

// Calculate water dew point
ThermodynamicOperations ops = new ThermodynamicOperations(dryGasFluid);
try {
    ops.waterDewPointTemperatureFlash();
    double waterDewPoint = dryGasFluid.getTemperature("C");
    System.out.printf("Water dew point: %.1f °C%n", waterDewPoint);
} catch (Exception e) {
    System.out.println("No water dew point (too dry)");
}
```

---

## Stripping Gas for Enhanced Regeneration

To achieve >99.5% lean TEG purity, add stripping gas to the regenerator:

```java
// Stripping gas (typically fuel gas or dry product gas)
SystemSrkCPAstatoil stripGasFluid = new SystemSrkCPAstatoil(473.15, 1.0);
stripGasFluid.addComponent("methane", 1.0);
stripGasFluid.setMixingRule(10);

Stream strippingGas = new Stream("Stripping Gas", stripGasFluid);
strippingGas.setFlowRate(50.0, "kg/hr");  // 2-5% of TEG rate
strippingGas.setTemperature(200.0, "C");
strippingGas.setPressure(1.0, "bara");
```

---

## TEG Quality Monitoring

Key parameters to monitor for TEG health:

| Parameter | Normal Range | Problem Indication |
|-----------|--------------|-------------------|
| pH | 6.0-8.0 | <6.0 indicates acid contamination |
| Color | Clear to light yellow | Dark = thermal degradation |
| Foaming tendency | None | Indicates contamination |
| Flash point | >177°C | Lower = hydrocarbon contamination |
| Specific gravity | 1.120-1.125 | Variation indicates water or contamination |

---

## Common Issues and Solutions

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| High dry gas water content | Low TEG purity | Increase regenerator temperature |
| | Low circulation rate | Increase TEG rate |
| | Too few trays | Add trays or packing |
| TEG losses | High reboiler temperature | Reduce to ≤204°C |
| | Carry-over in contactor | Add mist eliminator |
| Foaming | Hydrocarbon contamination | Carbon filter, skim tank |
| Corrosion | Acid gases, degradation products | Maintain pH, replace TEG |

---

## Using the GlycolDehydrationModule

NeqSim provides a pre-built module for TEG dehydration:

```java
import neqsim.process.equipment.util.GlycolDehydrationModule;

// Create module
GlycolDehydrationModule tegModule = new GlycolDehydrationModule("TEG Unit");
tegModule.setGasInStream(wetGas);
tegModule.setNumberOfTheoreticalStages(6);
tegModule.setLeanGlycolPurity(0.995);  // 99.5% TEG
tegModule.setGlycolCirculationRate(3000.0);  // kg/hr

// Run
tegModule.run();

// Get dry gas
Stream dryGas = tegModule.getGasOutStream();
```

---

## See Also

- [SimpleTEGAbsorber](../process/absorbers) - Absorber equipment documentation
- [Water Dew Point Guide](../pvtsimulation/water_dew_point_guide) - Water content calculations
- [Process Recipes](../cookbook/process-recipes) - Quick recipes for common operations
- [Component List](component_list) - TEG and glycol properties
