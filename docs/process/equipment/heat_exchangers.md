---
title: Heat Exchanger Equipment
description: Documentation for heat transfer equipment in NeqSim process simulation.
---

# Heat Exchanger Equipment

Documentation for heat transfer equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Equipment Types](#equipment-types)
- [Usage Examples](#usage-examples)
- [Heat Exchanger Networks](#heat-exchanger-networks)

---

## Overview

**Location:** `neqsim.process.equipment.heatexchanger`

**Classes:**
- `Heater` - Simple heater (duty or outlet T specified)
- `Cooler` - Simple cooler (duty or outlet T specified)
- `HeatExchanger` - Shell-tube heat exchanger
- `NeqHeater` - Non-equilibrium heater
- `Condenser` - Overhead condenser
- `WaterCooler` - Seawater/cooling water cooler with IAPWS
- `ReBoiler` - Column reboiler with duty specification

> **📖 Additional Heat Exchanger Documentation:**
> - [Multi-Stream Heat Exchanger](multistream_heat_exchanger) - Comprehensive MSHE guide with composite curves, pinch analysis
> - [Water Cooler and Reboiler](water_cooler_reboiler) - WaterCooler and ReBoiler documentation
> - [Air Cooler](../../wiki/air_cooler) - Air-cooled heat exchangers
> - [Steam Heater](../../wiki/steam_heater) - Steam heating with IAPWS

---

## Equipment Types

### Heater

```java
import neqsim.process.equipment.heatexchanger.Heater;

// Specify outlet temperature
Heater heater = new Heater("E-100", inletStream);
heater.setOutTemperature(80.0, "C");
heater.run();

double duty = heater.getDuty();  // W
System.out.println("Heating duty: " + duty/1000.0 + " kW");

// Or specify duty
Heater heater2 = new Heater("E-101", inletStream);
heater2.setEnergyInput(500000.0);  // 500 kW
heater2.run();
```

### Cooler

```java
import neqsim.process.equipment.heatexchanger.Cooler;

Cooler cooler = new Cooler("E-200", hotStream);
cooler.setOutTemperature(30.0, "C");
cooler.run();

double duty = cooler.getDuty();  // Negative for cooling
System.out.println("Cooling duty: " + (-duty/1000.0) + " kW");
```

### Heat Exchanger

Two-stream heat exchanger with hot and cold sides.

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;

HeatExchanger hx = new HeatExchanger("E-300", hotStream, coldStream);

// Specify UA value
hx.setUAvalue(10000.0);  // W/K

// Or specify approach temperature
hx.setApproachTemperature(10.0, "C");

hx.run();

// Results
double hotOut = hx.getOutStream(0).getTemperature("C");
double coldOut = hx.getOutStream(1).getTemperature("C");
double duty = hx.getDuty();
double LMTD = hx.getLMTD();
```

### Multi-Stream Heat Exchanger

For detailed documentation including mathematical foundations, composite curves, and advanced usage, see:

> **📖 [Multi-Stream Heat Exchanger Guide](multistream_heat_exchanger.md)**

Quick example:

```java
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2;

MultiStreamHeatExchanger2 mshx = new MultiStreamHeatExchanger2("LNG-100");

// Add streams: (stream, "hot"/"cold", outletTemp or null for unknown)
mshx.addInStreamMSHE(stream1, "hot", 40.0);    // Known outlet
mshx.addInStreamMSHE(stream2, "hot", null);    // Solve for this
mshx.addInStreamMSHE(stream3, "cold", null);   // Solve for this
mshx.addInStreamMSHE(stream4, "cold", 80.0);   // Known outlet

// Set approach temperature (required for 2+ unknowns)
mshx.setTemperatureApproach(5.0);

mshx.run();

// Get results
double solvedTemp = mshx.getOutStream(1).getTemperature("C");
double ua = mshx.getUA();
```

---

## Operating Modes

### Temperature Specification

```java
// Outlet temperature
heater.setOutTemperature(100.0, "C");

// Temperature change
heater.setdT(50.0, "C");  // ΔT = 50°C
```

### Duty Specification

```java
// Fixed duty
heater.setEnergyInput(1000000.0);  // 1 MW

// Duty from energy stream
EnergyStream energyIn = new EnergyStream("Heat Source");
energyIn.setEnergyFlow(500.0, "kW");
heater.setEnergyStream(energyIn);
```

### UA Specification

```java
// For heat exchangers
hx.setUAvalue(5000.0);  // W/K

// Calculate UA from geometry
double U = 500.0;  // Overall HTC, W/(m²·K)
double A = 100.0;  // Area, m²
hx.setUAvalue(U * A);
```

---

## Condenser

```java
import neqsim.process.equipment.heatexchanger.Condenser;

Condenser condenser = new Condenser("Overhead Condenser", vaporStream);

// Total condensation
condenser.setOutTemperature(40.0, "C");
condenser.run();

// Partial condensation
condenser.setDewPointTemperature(true);  // Operate at dew point
condenser.run();

// Sub-cooling
condenser.setSubCooling(5.0, "C");  // 5°C subcooling
```

---

## Dynamic Simulation

```java
heater.setCalculateSteadyState(false);

// Set thermal mass
heater.setThermalMass(10000.0);  // J/K

// Transient response
for (double t = 0; t < 3600; t += 1.0) {
    heater.runTransient();
    double Tout = heater.getOutletStream().getTemperature("C");
}
```

---

## Example: Gas Cooling Train

```java
ProcessSystem process = new ProcessSystem();

// Hot gas inlet
Stream hotGas = new Stream("Hot Gas", gasFluid);
hotGas.setFlowRate(50000.0, "kg/hr");
hotGas.setTemperature(150.0, "C");
hotGas.setPressure(80.0, "bara");
process.add(hotGas);

// Air cooler
Cooler airCooler = new Cooler("Air Cooler", hotGas);
airCooler.setOutTemperature(60.0, "C");
process.add(airCooler);

// Trim cooler (seawater)
Cooler trimCooler = new Cooler("Trim Cooler", airCooler.getOutletStream());
trimCooler.setOutTemperature(25.0, "C");
process.add(trimCooler);

// Separator for condensate
Separator separator = new Separator("Inlet Sep", trimCooler.getOutletStream());
process.add(separator);

process.run();

// Total cooling duty
double airDuty = -airCooler.getDuty() / 1e6;  // MW
double trimDuty = -trimCooler.getDuty() / 1e6;  // MW
System.out.println("Air cooler duty: " + airDuty + " MW");
System.out.println("Trim cooler duty: " + trimDuty + " MW");
```

---

## Heat Exchanger Design

### LMTD Method

```java
HeatExchanger hx = new HeatExchanger("E-400", hotIn, coldIn);
hx.setUAvalue(ua);
hx.run();

double LMTD = hx.getLMTD();
double duty = hx.getDuty();
double UA = duty / LMTD;
double area = UA / overallHTC;
```

### Effectiveness-NTU

```java
double NTU = hx.getNTU();
double effectiveness = hx.getEffectiveness();
```

---

## Auto-Sizing

Heat exchanger equipment implements the `AutoSizeable` interface for automatic sizing based on duty requirements.

### Heater/Cooler Auto-Sizing

```java
import neqsim.process.equipment.heatexchanger.Heater;

Heater heater = new Heater("E-100", inletStream);
heater.setOutTemperature(80.0, "C");
heater.run();

// Auto-size with 20% safety factor
heater.autoSize(1.2);

// Get sizing report
System.out.println(heater.getSizingReport());
// Output includes:
// - Inlet/Outlet temperatures
// - Duty
// - Max design duty
// - Duty utilization %

// Get JSON report for programmatic access
String jsonReport = heater.getSizingReportJson();
```

### Heat Exchanger Auto-Sizing

Two-stream heat exchangers provide enhanced sizing reports:

```java
import neqsim.process.equipment.heatexchanger.HeatExchanger;

HeatExchanger hx = new HeatExchanger("E-300", hotStream, coldStream);
hx.setUAvalue(10000.0);
hx.run();

// Auto-size with 25% safety factor
hx.autoSize(1.25);

// Get detailed sizing report
System.out.println(hx.getSizingReport());
// Output includes:
// - Hot side: inlet/outlet temperatures, flow rate
// - Cold side: inlet/outlet temperatures, flow rate
// - Duty, UA value, thermal effectiveness
// - LMTD (Log Mean Temperature Difference)
// - Mechanical design parameters
```

### Using Company Standards

```java
// Auto-size using company-specific standards
heater.autoSize("Equinor", "TR3100");

// Standards are loaded from TechnicalRequirements_Process.csv
// and design standards tables (api_standards.csv, etc.)
```

---

## Mechanical Design

Access mechanical design calculations:

```java
HeatExchangerMechanicalDesign mechDesign = hx.getMechanicalDesign();
mechDesign.calcDesign();

// Get exchanger type recommendations
List<HeatExchangerSizingResult> results = mechDesign.getSizingResults();
for (HeatExchangerSizingResult result : results) {
    System.out.println(result.getType() + ": " + result.getArea() + " m²");
}
```

---

## Related Documentation

- [Process Package](../) - Package overview
- [Compressors](compressors) - Compression equipment
- [Design Framework](../DESIGN_FRAMEWORK) - AutoSizeable interface
