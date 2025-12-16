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

```java
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger;

MultiStreamHeatExchanger mshx = new MultiStreamHeatExchanger("LNG-100");
mshx.addStream(stream1, "hot");
mshx.addStream(stream2, "hot");
mshx.addStream(stream3, "cold");

mshx.setUAvalue(50000.0);
mshx.run();
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

## Related Documentation

- [Process Package](../README.md) - Package overview
- [Compressors](compressors.md) - Compression equipment
