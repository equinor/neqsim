---
title: Differential Pressure Devices
description: Documentation for differential pressure measurement and flow restriction equipment in NeqSim.
---

# Differential Pressure Devices

Documentation for differential pressure measurement and flow restriction equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Orifice Plate](#orifice-plate)
- [Differential Pressure Flow Calculator](#differential-pressure-flow-calculator)
- [ISO 5167 Implementation](#iso-5167-implementation)
- [Usage Examples](#usage-examples)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.diffpressure`

The differential pressure package provides equipment for:
- Flow measurement using orifice plates
- Flow restriction calculations
- Pressure drop modeling
- Transient/dynamic blowdown simulations

| Class | Description |
|-------|-------------|
| `Orifice` | ISO 5167 orifice plate flow restriction |
| `DifferentialPressureFlowCalculator` | ΔP-based flow calculation |

---

## Orifice Plate

The `Orifice` class models an orifice plate flow restriction device compliant with ISO 5167.

### Class Hierarchy

```
TwoPortEquipment
└── Orifice
```

### Key Features

- ISO 5167 compliant flow calculations
- Reader-Harris/Gallagher discharge coefficient
- Support for corner, flange, D and D/2 pressure taps
- Compressible flow with expansibility factor
- Pressure-driven flow in transient simulations

### Constructor

```java
import neqsim.process.equipment.diffpressure.Orifice;

// Basic constructor
Orifice orifice = new Orifice("FO-101");

// Full constructor with ISO 5167 parameters
Orifice orifice = new Orifice(
    "FO-101",           // Name
    0.1,                // Pipe diameter (m)
    0.05,               // Orifice diameter (m)
    50.0,               // Upstream pressure (bara)
    5.0,                // Downstream pressure (bara)
    0.61                // Discharge coefficient
);
```

### Key Properties

| Property | Description | Unit |
|----------|-------------|------|
| `diameter` | Upstream pipe internal diameter | m |
| `orificeDiameter` | Orifice bore diameter | m |
| `pressureUpstream` | Upstream pressure | bara |
| `pressureDownstream` | Downstream boundary pressure | bara |
| `dischargeCoefficient` | Cd (typically 0.60-0.62) | - |
| `dp` | Pressure differential | bar |

### Beta Ratio

The beta ratio (β) is the ratio of orifice diameter to pipe diameter:

```
β = d/D
```

Where:
- d = orifice bore diameter
- D = pipe internal diameter

Typical range: 0.2 ≤ β ≤ 0.75

---

## ISO 5167 Implementation

### Discharge Coefficient

The orifice uses the Reader-Harris/Gallagher equation for the discharge coefficient:

```
C = f(β, ReD, L₁, L₂)
```

Where:
- β = diameter ratio
- ReD = pipe Reynolds number
- L₁, L₂ = tap positions

### Expansibility Factor

For compressible flow, the expansibility factor ε accounts for gas expansion:

```
ε = f(β, κ, τ)
```

Where:
- κ = isentropic exponent (Cp/Cv)
- τ = pressure ratio (P₂/P₁)

### Flow Equation

Mass flow rate through the orifice:

```
ṁ = C · ε · (π/4) · d² · √(2 · ρ₁ · ΔP)
```

---

## Differential Pressure Flow Calculator

The `DifferentialPressureFlowCalculator` provides utilities for ΔP-based flow calculations.

### Example Usage

```java
import neqsim.process.equipment.diffpressure.DifferentialPressureFlowCalculator;

DifferentialPressureFlowCalculator calc = new DifferentialPressureFlowCalculator();
calc.setPipeDiameter(0.1);
calc.setOrificeDiameter(0.05);
calc.setDifferentialPressure(0.5);  // bar

double massFlow = calc.calculateMassFlow(fluid);
System.out.println("Mass flow: " + massFlow + " kg/s");
```

---

## Usage Examples

### Flow Measurement

```java
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create gas stream
SystemInterface gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("nitrogen", 0.02);
gas.setMixingRule("classic");

Stream gasStream = new Stream("Process Gas", gas);
gasStream.setFlowRate(10000.0, "Sm3/hr");

// Create orifice meter
Orifice meter = new Orifice("FO-101");
meter.setInletStream(gasStream);
meter.setDiameter(0.15);           // 150mm pipe
meter.setOrificeDiameter(0.075);   // 75mm orifice (β = 0.5)

// Run
meter.run();

// Get differential pressure
System.out.println("ΔP: " + meter.getDp() * 1000 + " mbar");
System.out.println("Flow rate: " + meter.getOutletStream().getFlowRate("Sm3/hr") + " Sm3/hr");
```

### Transient Blowdown Simulation

The orifice can be used for transient depressurization simulations:

```java
import neqsim.process.equipment.diffpressure.Orifice;
import neqsim.process.equipment.tank.Tank;

// Create vessel
Tank vessel = new Tank("V-101", vesselFluid);
vessel.setVolume(100.0);  // m³

// Create blowdown orifice
Orifice blowdownOrifice = new Orifice(
    "RO-101",
    0.1,     // 100mm pipe
    0.025,   // 25mm orifice
    vessel.getPressure(),
    1.5,     // Flare header pressure
    0.61     // Discharge coefficient
);
blowdownOrifice.setInletStream(vessel.getOutletStream());

// Run transient simulation
for (double t = 0; t < 3600; t += 1.0) {
    blowdownOrifice.setPressureUpstream(vessel.getPressure());
    blowdownOrifice.run();
    
    double massFlow = blowdownOrifice.getOutletStream().getFlowRate("kg/s");
    vessel.removeMass(massFlow * 1.0);  // Remove mass for 1 second
    
    if (t % 60 == 0) {
        System.out.println("t=" + t + "s, P=" + vessel.getPressure() + " bara");
    }
}
```

### Integration in Process System

```java
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem process = new ProcessSystem("Flow Metering");

// Feed
process.add(feedStream);

// Flow meter
Orifice flowMeter = new Orifice("FO-101");
flowMeter.setInletStream(feedStream);
flowMeter.setDiameter(0.1);
flowMeter.setOrificeDiameter(0.05);
process.add(flowMeter);

// Downstream equipment
Separator separator = new Separator("V-101", flowMeter.getOutletStream());
process.add(separator);

// Run
process.run();
```

---

## Design Considerations

### Orifice Sizing

For accurate flow measurement:
- Beta ratio between 0.2 and 0.75
- Reynolds number > 5000
- Adequate upstream/downstream straight pipe lengths

### Pressure Recovery

Permanent pressure loss is approximately:

```
ΔP_permanent ≈ (1 - β⁴) × ΔP_measured
```

### Cavitation

Avoid cavitation by ensuring:
```
P₂ > 2 × P_vapor
```

---

## Related Documentation

- [Valves](valves.md) - Control valves and safety valves
- [Flow Meters](../../wiki/flow_meter_models.md) - Flow metering models
- [Venturi Calculation](../../wiki/venturi_calculation.md) - Venturi flow meters
- [Pipeline Pressure Drop](../../wiki/pipeline_pressure_drop.md) - Pressure drop calculations
