---
title: Vessel Depressurization and Blowdown
description: Comprehensive modeling of pressure vessel filling, depressurization, and blowdown scenarios.
---

# Vessel Depressurization and Blowdown

Comprehensive modeling of pressure vessel filling, depressurization, and blowdown scenarios.

## Table of Contents

- [Overview](#overview)
- [Calculation Types](#calculation-types)
- [VesselDepressurization Class](#vesseldepressurization-class)
  - [Basic Usage](#basic-usage)
  - [Configuration Parameters](#configuration-parameters)
  - [Transient Simulation](#transient-simulation)
- [Heat Transfer Models](#heat-transfer-models)
- [Fire Scenarios](#fire-scenarios)
- [Results and Reporting](#results-and-reporting)
- [API Reference](#api-reference)
- [Examples](#examples)
- [Python Examples](#python-examples)

---

## Overview

The `VesselDepressurization` class models dynamic filling and depressurization of pressure vessels with support for:

1. **Multiple Thermodynamic Processes**: Isothermal, isenthalpic, isentropic, isenergetic, and energy balance
2. **Heat Transfer**: Vessel wall thermal modeling, fire exposure
3. **Multi-component Mixtures**: Full thermodynamic calculations using NeqSim's equation of state
4. **Orifice Flow**: Critical and subcritical discharge through orifices

**Location:** `neqsim.process.equipment.tank`

**Reference:** Andreasen, A. (2021). "HydDown: A Python package for calculation of hydrogen (or other gas) pressure vessel filling and discharge." Journal of Open Source Software, 6(66), 3695.

### Physical Model

```
                    ┌─────────────────────────────────┐
                    │                                 │
                    │   ╔═══════════════════════╗     │
                    │   ║                       ║     │
                    │   ║    VESSEL CONTENTS    ║     │
     Fire ──────────┤   ║    P, T, m, U         ║     ├────── Fire
     (optional)     │   ║                       ║     │     (optional)
                    │   ╠═══════════════════════╣     │
                    │   ║  Vessel Wall (thermal)║     │
                    │   ╚═══════════════════════╝     │
                    │               │                 │
                    └───────────────┼─────────────────┘
                                    │
                              Orifice │ (d_orifice)
                                    ▼
                               Discharge
                            (critical/subcritical)
```

---

## Calculation Types

### Available Thermodynamic Models

| Type | Description | Conservation | Use Case |
|------|-------------|--------------|----------|
| `ISOTHERMAL` | Constant temperature | T = const | Fast estimates, isothermal expansion |
| `ISENTHALPIC` | Constant enthalpy | H = const | Adiabatic, no PV work (J-T effect) |
| `ISENTROPIC` | Constant entropy | S = const | Adiabatic with PV work |
| `ISENERGETIC` | Constant internal energy | U = const | Closed adiabatic vessel |
| `ENERGY_BALANCE` | Full heat transfer | Energy balance | Most accurate, fire scenarios |

### Selection Guide

```
                        Need accuracy?
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
           No/Quick                    Yes/Safety
              │                             │
              ▼                             │
        ISOTHERMAL                          │
        (simplest)                          │
                                            │
                              Heat transfer involved?
                                      │
                         ┌────────────┴────────────┐
                         │                         │
                         ▼                         ▼
                        No                        Yes
                         │                         │
                         ▼                         ▼
                   ISENTROPIC              ENERGY_BALANCE
                   or ISENERGETIC          (with fire/walls)
```

---

## VesselDepressurization Class

### Basic Usage

```java
import neqsim.process.equipment.tank.VesselDepressurization;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create high-pressure gas
SystemSrkEos gas = new SystemSrkEos(298.0, 200.0);  // 200 bara
gas.addComponent("hydrogen", 1.0);
gas.setMixingRule("classic");

// Create inlet stream (closed vessel = 0 flow)
Stream feed = new Stream("feed", gas);
feed.setFlowRate(0.0, "kg/hr");
feed.run();

// Create vessel
VesselDepressurization vessel = new VesselDepressurization("HP Vessel", feed);
vessel.setVolume(0.1);  // 100 liters = 0.1 m³
vessel.setCalculationType(VesselDepressurization.CalculationType.ENERGY_BALANCE);

// Configure discharge
vessel.setOrificeDiameter(0.005);  // 5 mm orifice
vessel.setDischargeCoefficient(0.84);
vessel.setBackPressure(1.0);  // 1 bara ambient

// Initial state
vessel.run();
System.out.println("Initial pressure: " + vessel.getPressure() + " bara");
System.out.println("Initial temperature: " + vessel.getTemperature() + " K");
```

### Configuration Parameters

#### Vessel Geometry

```java
vessel.setVolume(0.5);           // Internal volume (m³)
vessel.setVesselDiameter(0.5);   // Internal diameter (m)
vessel.setVesselLength(2.5);     // Cylinder length (m)
```

#### Discharge Configuration

```java
vessel.setOrificeDiameter(0.010);   // Orifice diameter (m)
vessel.setDischargeCoefficient(0.84); // Cd (typical 0.8-0.9)
vessel.setBackPressure(1.0);        // Downstream pressure (bara)
```

#### Wall Thermal Properties

```java
// Set wall thermal properties for heat transfer
vessel.setVesselProperties(
    0.015,    // Wall thickness (m)
    7800.0,   // Wall density (kg/m³) - steel
    500.0,    // Wall specific heat (J/kg·K)
    45.0      // Wall thermal conductivity (W/m·K)
);
```

#### Heat Transfer Type

```java
// Available heat transfer modes
vessel.setHeatTransferType(HeatTransferType.ADIABATIC);    // No heat transfer
vessel.setHeatTransferType(HeatTransferType.NATURAL_CONVECTION); // Natural convection
vessel.setHeatTransferType(HeatTransferType.FORCED_CONVECTION);  // Forced convection
vessel.setHeatTransferType(HeatTransferType.FIRE);          // Fire exposure
```

### Transient Simulation

```java
import java.util.UUID;

// Run transient depressurization
UUID runId = UUID.randomUUID();
double dt = 0.5;  // Time step (seconds)
double totalTime = 300.0;  // Total simulation time (seconds)

// Storage for results
List<Double> times = new ArrayList<>();
List<Double> pressures = new ArrayList<>();
List<Double> temperatures = new ArrayList<>();

for (double t = 0; t <= totalTime; t += dt) {
    vessel.runTransient(dt, runId);
    
    times.add(t);
    pressures.add(vessel.getPressure());
    temperatures.add(vessel.getTemperature());
    
    // Stop if pressure reaches back pressure
    if (vessel.getPressure() <= vessel.getBackPressure() * 1.01) {
        System.out.println("Depressurization complete at t = " + t + " s");
        break;
    }
}
```

---

## Heat Transfer Models

### Natural Convection

Internal and external natural convection correlations:

```java
vessel.setHeatTransferType(HeatTransferType.NATURAL_CONVECTION);
vessel.setAmbientTemperature(288.15);  // 15°C ambient
```

### Forced Convection

For wind-exposed vessels:

```java
vessel.setHeatTransferType(HeatTransferType.FORCED_CONVECTION);
vessel.setExternalHeatTransferCoefficient(25.0);  // W/m²·K
vessel.setAmbientTemperature(288.15);
```

### Fire Scenarios

The `TransientWallHeatTransfer` class provides detailed fire modeling:

```java
import neqsim.process.util.fire.TransientWallHeatTransfer;

// Configure fire exposure
vessel.setHeatTransferType(HeatTransferType.FIRE);
vessel.setFireHeatFlux(50000.0);  // 50 kW/m² (pool fire)
vessel.setFireCoverage(0.5);     // 50% of vessel exposed
```

### Wall Temperature Tracking

```java
// Get wall temperatures during simulation
double innerWallTemp = vessel.getInnerWallTemperature();
double outerWallTemp = vessel.getOuterWallTemperature();

System.out.println("Inner wall: " + (innerWallTemp - 273.15) + " °C");
System.out.println("Outer wall: " + (outerWallTemp - 273.15) + " °C");
```

---

## Fire Scenarios

### Pool Fire (50 kW/m²)

```java
vessel.setHeatTransferType(HeatTransferType.FIRE);
vessel.setFireHeatFlux(50000.0);  // Pool fire: 50-150 kW/m²
vessel.setFireCoverage(0.4);
```

### Jet Fire (150-300 kW/m²)

```java
vessel.setHeatTransferType(HeatTransferType.FIRE);
vessel.setFireHeatFlux(200000.0);  // Jet fire: 150-300 kW/m²
vessel.setFireCoverage(0.3);       // Localized exposure
```

### API 521 Fire Case

Following API 521 guidance for fire case relief:

```java
// API 521 recommends 45-75 kW/m² for pressure relief sizing
vessel.setHeatTransferType(HeatTransferType.FIRE);
vessel.setFireHeatFlux(75000.0);   // Conservative API 521 value
vessel.setFireCoverage(1.0);       // Full exposure (conservative)

// Run until relief device opens
double reliefPressure = 1.1 * designPressure;
while (vessel.getPressure() < reliefPressure) {
    vessel.runTransient(0.1, runId);
}
double timeToRelief = currentTime;
System.out.println("Time to relief opening: " + timeToRelief + " s");
```

---

## Results and Reporting

### Transient Results

```java
// Get current state
double pressure = vessel.getPressure();           // bara
double temperature = vessel.getTemperature();     // K
double mass = vessel.getMass();                   // kg
double internalEnergy = vessel.getInternalEnergy(); // J
double enthalpy = vessel.getEnthalpy();           // J
double entropy = vessel.getEntropy();             // J/K

// Get discharge properties
double massFlowRate = vessel.getDischargeFlowRate();  // kg/s
double velocity = vessel.getDischargeVelocity();      // m/s
boolean isCritical = vessel.isCriticalFlow();         // choked flow?
```

### Export to JSON/CSV

```java
// Export results
vessel.exportResultsToJSON("blowdown_results.json");
vessel.exportResultsToCSV("blowdown_timeseries.csv");
```

### Result Structure

```json
{
  "vesselName": "HP Vessel",
  "initialConditions": {
    "pressure_bara": 200.0,
    "temperature_K": 298.0,
    "mass_kg": 12.5,
    "volume_m3": 0.1
  },
  "finalConditions": {
    "pressure_bara": 1.0,
    "temperature_K": 178.5,
    "mass_kg": 0.08,
    "blowdownTime_s": 245.0
  },
  "peakValues": {
    "massFlowRate_kg_s": 2.3,
    "cooldownRate_K_s": 1.5,
    "minTemperature_K": 165.2
  }
}
```

---

## API Reference

### Constructor

| Constructor | Description |
|-------------|-------------|
| `VesselDepressurization(String name, StreamInterface feed)` | Create vessel with feed stream |

### Configuration Methods

| Method | Description |
|--------|-------------|
| `setVolume(double)` | Set vessel volume (m³) |
| `setCalculationType(CalculationType)` | Set thermodynamic model |
| `setHeatTransferType(HeatTransferType)` | Set heat transfer mode |
| `setOrificeDiameter(double)` | Set discharge orifice (m) |
| `setDischargeCoefficient(double)` | Set orifice Cd (0.8-0.9 typical) |
| `setBackPressure(double)` | Set downstream pressure (bara) |
| `setVesselProperties(thickness, density, cp, k)` | Set wall thermal properties |
| `setFireHeatFlux(double)` | Set fire heat input (W/m²) |
| `setAmbientTemperature(double)` | Set ambient temperature (K) |

### Execution Methods

| Method | Description |
|--------|-------------|
| `run()` | Initialize steady state |
| `runTransient(double dt, UUID id)` | Advance by time step dt |

### Result Methods

| Method | Description |
|--------|-------------|
| `getPressure()` | Current pressure (bara) |
| `getTemperature()` | Current temperature (K) |
| `getMass()` | Current mass (kg) |
| `getDischargeFlowRate()` | Mass flow rate (kg/s) |
| `isCriticalFlow()` | Check if flow is choked |
| `getInnerWallTemperature()` | Inner wall temp (K) |
| `getOuterWallTemperature()` | Outer wall temp (K) |

### Enumerations

```java
public enum CalculationType {
    ISOTHERMAL,
    ISENTHALPIC,
    ISENTROPIC,
    ISENERGETIC,
    ENERGY_BALANCE
}

public enum HeatTransferType {
    ADIABATIC,
    NATURAL_CONVECTION,
    FORCED_CONVECTION,
    FIRE
}
```

---

## Examples

### Complete Blowdown Simulation

```java
import neqsim.process.equipment.tank.VesselDepressurization;
import neqsim.process.equipment.tank.VesselDepressurization.CalculationType;
import neqsim.process.equipment.tank.VesselDepressurization.HeatTransferType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

public class BlowdownExample {
    public static void main(String[] args) {
        // Natural gas at high pressure
        SystemSrkEos gas = new SystemSrkEos(300.0, 100.0);
        gas.addComponent("methane", 0.90);
        gas.addComponent("ethane", 0.05);
        gas.addComponent("propane", 0.03);
        gas.addComponent("CO2", 0.02);
        gas.setMixingRule("classic");
        
        Stream feed = new Stream("feed", gas);
        feed.setFlowRate(0.0, "kg/hr");
        feed.run();
        
        // Configure vessel
        VesselDepressurization vessel = new VesselDepressurization("Scrubber", feed);
        vessel.setVolume(5.0);  // 5 m³
        vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
        vessel.setHeatTransferType(HeatTransferType.NATURAL_CONVECTION);
        
        // Vessel wall properties (carbon steel)
        vessel.setVesselProperties(0.025, 7850.0, 490.0, 43.0);
        vessel.setAmbientTemperature(288.15);
        
        // Discharge through 2" orifice
        vessel.setOrificeDiameter(0.0508);
        vessel.setDischargeCoefficient(0.85);
        vessel.setBackPressure(1.0);
        
        // Initialize
        vessel.run();
        System.out.println("Initial: P=" + vessel.getPressure() + " bara, " +
                          "T=" + (vessel.getTemperature()-273.15) + " °C");
        
        // Run transient
        UUID id = UUID.randomUUID();
        double dt = 1.0;
        double t = 0;
        double minTemp = Double.MAX_VALUE;
        
        while (vessel.getPressure() > 2.0) {  // Stop at 2 bara
            vessel.runTransient(dt, id);
            t += dt;
            
            minTemp = Math.min(minTemp, vessel.getTemperature());
            
            if (t % 30 < dt) {  // Print every 30 seconds
                System.out.printf("t=%5.0fs: P=%6.2f bara, T=%6.1f °C, mdot=%.3f kg/s%n",
                    t, vessel.getPressure(), vessel.getTemperature()-273.15,
                    vessel.getDischargeFlowRate());
            }
        }
        
        System.out.println("\nBlowdown complete:");
        System.out.println("  Total time: " + t + " s");
        System.out.println("  Minimum temperature: " + (minTemp-273.15) + " °C");
    }
}
```

### Fire Case Analysis

```java
// Same setup as above, then:
vessel.setCalculationType(CalculationType.ENERGY_BALANCE);
vessel.setHeatTransferType(HeatTransferType.FIRE);
vessel.setFireHeatFlux(75000.0);  // 75 kW/m² fire
vessel.setFireCoverage(0.5);      // Half vessel exposed

// Track maximum pressure and temperature
double maxPressure = vessel.getPressure();
double maxWallTemp = vessel.getOuterWallTemperature();

while (t < 1800) {  // 30 minutes
    vessel.runTransient(dt, id);
    t += dt;
    
    maxPressure = Math.max(maxPressure, vessel.getPressure());
    maxWallTemp = Math.max(maxWallTemp, vessel.getOuterWallTemperature());
    
    // Check for relief device opening
    if (vessel.getPressure() >= reliefSetPressure) {
        System.out.println("Relief device opens at t = " + t + " s");
        break;
    }
    
    // Check wall temperature limit (API 521)
    if (maxWallTemp > 593 + 273.15) {  // 593°C steel limit
        System.out.println("WARNING: Wall temperature exceeds material limit!");
    }
}
```

---

## Python Examples

### Basic Depressurization (Python)

```python
from neqsim.process.equipment.tank import VesselDepressurization
from neqsim.process.equipment.stream import Stream
from neqsim.thermo.system import SystemSrkEos
from java.util import UUID

# Create gas
gas = SystemSrkEos(300.0, 150.0)  # 150 bara
gas.addComponent("nitrogen", 0.95)
gas.addComponent("oxygen", 0.05)
gas.setMixingRule("classic")

feed = Stream("feed", gas)
feed.setFlowRate(0.0, "kg/hr")
feed.run()

# Configure vessel
vessel = VesselDepressurization("N2 Buffer", feed)
vessel.setVolume(1.0)
vessel.setCalculationType(VesselDepressurization.CalculationType.ISENTROPIC)
vessel.setOrificeDiameter(0.010)
vessel.setBackPressure(1.0)

vessel.run()

# Run simulation
run_id = UUID.randomUUID()
results = {'time': [], 'pressure': [], 'temperature': []}

t = 0
dt = 0.5
while vessel.getPressure() > 2.0:
    vessel.runTransient(dt, run_id)
    t += dt
    
    results['time'].append(t)
    results['pressure'].append(vessel.getPressure())
    results['temperature'].append(vessel.getTemperature() - 273.15)

# Plot results
import matplotlib.pyplot as plt

fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8), sharex=True)

ax1.plot(results['time'], results['pressure'], 'b-')
ax1.set_ylabel('Pressure (bara)')
ax1.grid(True)

ax2.plot(results['time'], results['temperature'], 'r-')
ax2.set_xlabel('Time (s)')
ax2.set_ylabel('Temperature (°C)')
ax2.grid(True)

plt.tight_layout()
plt.savefig('blowdown_results.png', dpi=150)
```

---

## Related Documentation

- [QRA Integration Guide](../../integration/QRA_INTEGRATION_GUIDE) - Safety analysis integration
- [Tank Equipment](tanks) - General tank modeling
- [Safety Simulation](../../safety/README) - Safety system modeling

---

*Package Location: `neqsim.process.equipment.tank`*
