---
title: Electrolyzer Equipment
description: Documentation for electrolyzer equipment in NeqSim process simulation.
---

# Electrolyzer Equipment

Documentation for electrolyzer equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Electrolyzer Classes](#electrolyzer-classes)
- [PEM Electrolyzer](#pem-electrolyzer)
- [CO₂ Electrolyzer](#co2-electrolyzer)
- [Energy Calculations](#energy-calculations)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.electrolyzer`

**Classes:**
| Class | Description |
|-------|-------------|
| `Electrolyzer` | Base electrolyzer class |
| `CO2Electrolyzer` | CO₂ electrolysis unit |

Electrolyzers convert electrical energy into chemical energy through electrochemical reactions. Key applications:
- Green hydrogen production (water electrolysis)
- CO₂ reduction to fuels/chemicals
- Power-to-X systems
- Energy storage

---

## Electrolyzer Class

### Basic Usage

```java
import neqsim.process.equipment.electrolyzer.Electrolyzer;

// Create electrolyzer with water feed
Electrolyzer electrolyzer = new Electrolyzer("PEM Electrolyzer", waterStream);
electrolyzer.setPower(1e6);  // 1 MW
electrolyzer.setEfficiency(0.70);  // 70% efficiency
electrolyzer.run();

// Get hydrogen production
StreamInterface h2Stream = electrolyzer.getHydrogenStream();
double h2Rate = h2Stream.getFlowRate("kg/hr");
System.out.println("H2 production: " + h2Rate + " kg/hr");
```

---

## CO₂ Electrolyzer

The CO2Electrolyzer converts CO₂ to valuable products through electrolysis.

### Basic Usage

```java
import neqsim.process.equipment.electrolyzer.CO2Electrolyzer;

// Create CO2 electrolyzer
CO2Electrolyzer co2Elec = new CO2Electrolyzer("CO2 Electrolyzer", co2Stream);
co2Elec.setPower(500e3);  // 500 kW
co2Elec.run();

// Get products
StreamInterface products = co2Elec.getOutletStream();
```

### Products

CO₂ electrolysis can produce various products depending on the catalyst:
- Carbon monoxide (CO)
- Formic acid (HCOOH)
- Methanol (CH₃OH)
- Ethylene (C₂H₄)
- Syngas (CO + H₂)

---

## Energy Calculations

### Power Consumption

The electrical power required for electrolysis:

$$P = \frac{\Delta G}{\eta_{elec}}$$

Where:
- $\Delta G$ = Gibbs free energy of reaction
- $\eta_{elec}$ = electrolyzer efficiency

### Efficiency

```java
// Set efficiency (energy efficiency)
electrolyzer.setEfficiency(0.75);  // 75%

// Get actual power consumption
double power = electrolyzer.getPower();  // W
```

### Faradaic Efficiency

The fraction of electrical current that drives the desired reaction:

$$\eta_F = \frac{n \times F \times \dot{n}_{product}}{I}$$

Where:
- $n$ = electrons per molecule
- $F$ = Faraday constant (96485 C/mol)
- $\dot{n}_{product}$ = molar production rate
- $I$ = current

---

## Water Electrolysis

### Reaction

$$2H_2O \rightarrow 2H_2 + O_2$$

### Energy Requirement

Theoretical minimum: 39.4 kWh/kg H₂ (based on HHV)

Typical actual consumption:
| Technology | Energy (kWh/kg H₂) |
|------------|-------------------|
| Alkaline | 50-55 |
| PEM | 50-60 |
| SOEC | 35-45 |

### Example

```java
// Water feed
SystemInterface waterFluid = new SystemSrkEos(298.15, 1.0);
waterFluid.addComponent("water", 1.0);
waterFluid.setMixingRule("classic");

Stream waterFeed = new Stream("Water Feed", waterFluid);
waterFeed.setFlowRate(1000.0, "kg/hr");
waterFeed.run();

// PEM Electrolyzer
Electrolyzer pemElec = new Electrolyzer("PEM Stack", waterFeed);
pemElec.setPower(10e6);  // 10 MW
pemElec.setEfficiency(0.70);  // 70%
pemElec.run();

// Hydrogen output
double h2Production = pemElec.getHydrogenStream().getFlowRate("kg/hr");
double specificEnergy = pemElec.getPower() / 1000 / h2Production;  // kWh/kg

System.out.println("H2 production: " + h2Production + " kg/hr");
System.out.println("Specific energy: " + specificEnergy + " kWh/kg H2");
```

---

## Usage Examples

### Green Hydrogen Plant

```java
ProcessSystem process = new ProcessSystem();

// Deionized water feed
SystemInterface diWater = new SystemSrkEos(298.15, 1.0);
diWater.addComponent("water", 1.0);

Stream waterFeed = new Stream("DI Water", diWater);
waterFeed.setFlowRate(2000.0, "kg/hr");
process.add(waterFeed);

// Electrolyzer stack
Electrolyzer electrolyzer = new Electrolyzer("H2 Electrolyzer", waterFeed);
electrolyzer.setPower(20e6);  // 20 MW from renewable source
electrolyzer.setEfficiency(0.72);
process.add(electrolyzer);

// Hydrogen purification (PSA or membrane)
MembraneSeparator h2Purifier = new MembraneSeparator("H2 Purifier", 
    electrolyzer.getHydrogenStream());
h2Purifier.setPermeateFraction("hydrogen", 0.999);
h2Purifier.setPermeateFraction("water", 0.01);
process.add(h2Purifier);

// Compression for storage
Compressor h2Comp = new Compressor("H2 Compressor", h2Purifier.getPermeateStream());
h2Comp.setOutletPressure(350.0, "bara");
h2Comp.setPolytropicEfficiency(0.80);
process.add(h2Comp);

// Run process
process.run();

// Results
double h2Output = h2Comp.getOutletStream().getFlowRate("kg/hr");
double h2Purity = h2Comp.getOutletStream().getFluid().getMoleFraction("hydrogen") * 100;
System.out.println("H2 output: " + h2Output + " kg/hr");
System.out.println("H2 purity: " + h2Purity + " %");
```

### Power-to-Methanol

```java
// CO2 capture stream
Stream co2Stream = new Stream("Captured CO2", co2Fluid);
co2Stream.setFlowRate(1000.0, "kg/hr");

// CO2 electrolyzer producing syngas
CO2Electrolyzer co2Elec = new CO2Electrolyzer("CO2 Electrolyzer", co2Stream);
co2Elec.setPower(5e6);  // 5 MW
co2Elec.run();

// Additional hydrogen from water electrolysis
Electrolyzer h2Elec = new Electrolyzer("H2 Electrolyzer", waterStream);
h2Elec.setPower(15e6);  // 15 MW
h2Elec.run();

// Mix syngas and H2 for methanol synthesis
Mixer syngasMixer = new Mixer("Syngas Mixer");
syngasMixer.addStream(co2Elec.getOutletStream());
syngasMixer.addStream(h2Elec.getHydrogenStream());

// Methanol reactor (downstream)
// ...
```

### Energy Storage Application

```java
// Variable renewable power input
double[] powerProfile = loadRenewablePowerProfile();  // hourly MW

// Electrolyzer with variable power
Electrolyzer electrolyzer = new Electrolyzer("Variable Electrolyzer", waterFeed);
electrolyzer.setEfficiency(0.70);

double totalH2 = 0.0;
for (int hour = 0; hour < 24; hour++) {
    double power = powerProfile[hour] * 1e6;  // Convert MW to W
    
    if (power > 0) {
        electrolyzer.setPower(power);
        electrolyzer.run();
        
        double h2Rate = electrolyzer.getHydrogenStream().getFlowRate("kg/hr");
        totalH2 += h2Rate;
        
        System.out.println("Hour " + hour + ": Power=" + power/1e6 + 
            " MW, H2=" + h2Rate + " kg/hr");
    }
}

System.out.println("Total H2 production: " + totalH2 + " kg");
```

---

## Operating Parameters

### Temperature and Pressure

```java
// Set operating conditions
electrolyzer.setTemperature(80.0, "C");  // PEM typical
electrolyzer.setPressure(30.0, "bara");  // Pressurized operation

// High-temperature electrolysis (SOEC)
electrolyzer.setTemperature(800.0, "C");
electrolyzer.setPressure(1.0, "bara");
```

### Stack Configuration

```java
// Set number of cells
electrolyzer.setNumberOfCells(100);

// Set cell voltage
electrolyzer.setCellVoltage(1.8);  // V

// Calculate current
double current = electrolyzer.getCurrent();  // A
```

---

## Related Documentation

- [Reactors](reactors) - Chemical reactors
- [Compressors](compressors) - H2 compression
- [Membrane Separators](membranes) - Gas purification
- [Process Package](../) - Package overview
