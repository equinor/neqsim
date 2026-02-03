---
title: Phase Envelope and Critical Points Guide
description: Guide to calculating phase envelopes in NeqSim including bubble point, dew point curves, cricondenbar, cricondentherm, and quality lines for natural gas and oil systems.
---

# Phase Envelope and Critical Points Guide

The phase envelope defines the boundary between single-phase and two-phase regions. This guide explains how to calculate phase envelopes and extract key points like cricondenbar and cricondentherm using NeqSim.

## Key Concepts

| Term | Definition | Importance |
|------|------------|------------|
| **Bubble Point** | Pressure/temperature where first vapor bubble forms | Oil systems, pipeline pressure |
| **Dew Point** | Pressure/temperature where first liquid droplet forms | Gas systems, condensation |
| **Cricondenbar** | Maximum pressure on phase envelope | Gas processing design |
| **Cricondentherm** | Maximum temperature on phase envelope | Pipeline operations |
| **Critical Point** | Where bubble and dew point curves meet | Phase behavior understanding |
| **Quality Lines** | Constant liquid fraction within two-phase region | Flash calculations |

---

## Quick Phase Envelope Calculation

### Java

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create natural gas
SystemSrkEos fluid = new SystemSrkEos(273.15, 50.0);
fluid.addComponent("nitrogen", 0.02);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("i-butane", 0.015);
fluid.addComponent("n-butane", 0.015);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Calculate phase envelope
ops.calcPTphaseEnvelope();

// Get cricondenbar and cricondentherm
double cricondenbar = ops.getCricondenbar()[1];      // Pressure (bara)
double cricondenbarT = ops.getCricondenbar()[0];     // Temperature at cricondenbar (K)
double cricondentherm = ops.getCricondentherm()[0];  // Temperature (K)
double criconderthermP = ops.getCricondentherm()[1]; // Pressure at cricondentherm (bara)
double criticalT = ops.getCriticalPoint()[0];        // Critical temperature (K)
double criticalP = ops.getCriticalPoint()[1];        // Critical pressure (bara)

System.out.println("=== Phase Envelope Results ===");
System.out.printf("Cricondenbar: %.2f bara at %.1f °C%n", 
    cricondenbar, cricondenbarT - 273.15);
System.out.printf("Cricondentherm: %.1f °C at %.2f bara%n", 
    cricondentherm - 273.15, criconderthermP);
System.out.printf("Critical point: %.1f °C, %.2f bara%n", 
    criticalT - 273.15, criticalP);

// Get envelope data for plotting
double[][] envelope = ops.getPhaseEnvelopeData();
// envelope[0] = temperatures (K)
// envelope[1] = pressures (bara)
// envelope[2] = phase type (0=bubble, 1=dew)
```

### Python

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

# Create gas
fluid = SystemSrkEos(273.15 + 20.0, 50.0)
fluid.addComponent("nitrogen", 0.02)
fluid.addComponent("CO2", 0.03)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.04)
fluid.addComponent("n-butane", 0.03)
fluid.setMixingRule("classic")

ops = ThermodynamicOperations(fluid)

# Calculate phase envelope
ops.calcPTphaseEnvelope()

# Get critical points
cricondenbar = ops.getCricondenbar()
cricondentherm = ops.getCricondentherm()
critical = ops.getCriticalPoint()

print(f"Cricondenbar: {cricondenbar[1]:.2f} bara at {cricondenbar[0] - 273.15:.1f} °C")
print(f"Cricondentherm: {cricondentherm[0] - 273.15:.1f} °C at {cricondentherm[1]:.2f} bara")
print(f"Critical Point: {critical[0] - 273.15:.1f} °C, {critical[1]:.2f} bara")
```

---

## Plotting Phase Envelopes

### With Matplotlib (Python)

```python
import matplotlib.pyplot as plt
import numpy as np

# After calculating phase envelope
temps = []
pressures = []

# Get bubble point curve
ops.calcPTphaseEnvelopeBubb()
bubb_data = ops.getPointsOnEnvelope()
for point in bubb_data:
    temps.append(point[0] - 273.15)  # Convert to °C
    pressures.append(point[1])

# Get dew point curve
ops.calcPTphaseEnvelopeDew()
dew_data = ops.getPointsOnEnvelope()
dew_temps = [p[0] - 273.15 for p in dew_data]
dew_pressures = [p[1] for p in dew_data]

# Plot
plt.figure(figsize=(10, 7))
plt.plot(temps, pressures, 'b-', label='Bubble Point', linewidth=2)
plt.plot(dew_temps, dew_pressures, 'r-', label='Dew Point', linewidth=2)

# Mark critical points
plt.plot(cricondenbar[0] - 273.15, cricondenbar[1], 'ko', markersize=10, label='Cricondenbar')
plt.plot(cricondentherm[0] - 273.15, cricondentherm[1], 'g^', markersize=10, label='Cricondentherm')
plt.plot(critical[0] - 273.15, critical[1], 'rs', markersize=10, label='Critical Point')

plt.xlabel('Temperature (°C)', fontsize=12)
plt.ylabel('Pressure (bara)', fontsize=12)
plt.title('Phase Envelope', fontsize=14)
plt.legend()
plt.grid(True, alpha=0.3)
plt.xlim([-150, 100])
plt.ylim([0, 100])
plt.show()
```

---

## Hydrocarbon Dew Point (HCDP)

For gas pipeline specifications, the hydrocarbon dew point is critical:

```java
// Calculate hydrocarbon dew point at specific pressure
double pipelinePressure = 70.0;  // bara
fluid.setPressure(pipelinePressure);

ops.dewPointTemperatureFlash();
double hcdp = fluid.getTemperature("C");

System.out.printf("HCDP at %.0f bara: %.1f °C%n", pipelinePressure, hcdp);

// Check against spec (e.g., -2°C at delivery pressure)
double maxHCDP = -2.0;
if (hcdp > maxHCDP) {
    System.out.printf("⚠️ HCDP %.1f °C exceeds spec %.1f °C%n", hcdp, maxHCDP);
    System.out.println("   Need NGL extraction or C3+ removal");
} else {
    System.out.printf("✅ HCDP %.1f °C meets spec%n", hcdp);
}
```

---

## Water Dew Point

For pipeline water specifications:

```java
// Add water to gas
fluid.addComponent("water", 0.001);  // Small amount

// Calculate water dew point at pipeline pressure
fluid.setPressure(70.0);
ops.waterDewPointTemperatureFlash();
double waterDP = fluid.getTemperature("C");

System.out.printf("Water dew point at 70 bara: %.1f °C%n", waterDP);

// Pipeline spec typically requires water DP below minimum ground temp
double minGroundTemp = 5.0;
if (waterDP > minGroundTemp) {
    System.out.println("⚠️ Need dehydration (TEG)");
}
```

---

## Quality Lines (Iso-Vapor Fraction)

Calculate lines of constant liquid fraction within the two-phase region:

```java
// Calculate 10%, 50%, 90% liquid quality lines
double[] qualities = {0.1, 0.5, 0.9};

for (double q : qualities) {
    ops.calcPTphaseEnvelopeQuality(q);
    double[][] qualityLine = ops.getQualityLineData();
    
    System.out.printf("Quality %.0f%% liquid:%n", q * 100);
    for (int i = 0; i < qualityLine[0].length; i++) {
        System.out.printf("  T=%.1f°C, P=%.2f bara%n", 
            qualityLine[0][i] - 273.15, qualityLine[1][i]);
    }
}
```

---

## Phase Envelope for Different Fluid Types

### Lean Gas (Dry Gas)

```java
SystemSrkEos leanGas = new SystemSrkEos(273.15, 50.0);
leanGas.addComponent("methane", 0.95);
leanGas.addComponent("ethane", 0.03);
leanGas.addComponent("propane", 0.01);
leanGas.addComponent("nitrogen", 0.01);
leanGas.setMixingRule("classic");

// Lean gas has small phase envelope, low cricondentherm
// Typically single-phase at pipeline conditions
```

### Rich Gas (Wet Gas / Condensate)

```java
SystemSrkEos richGas = new SystemSrkEos(273.15, 50.0);
richGas.addComponent("methane", 0.70);
richGas.addComponent("ethane", 0.10);
richGas.addComponent("propane", 0.08);
richGas.addComponent("i-butane", 0.03);
richGas.addComponent("n-butane", 0.04);
richGas.addComponent("n-pentane", 0.02);
richGas.addTBPfraction("C6+", 0.03, 90.0, 0.70);
richGas.setMixingRule("classic");

// Rich gas has larger phase envelope
// May condense liquids in pipeline - flow assurance concern
```

### Black Oil

```java
SystemSrkEos oil = new SystemSrkEos(373.15, 200.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.05);
oil.addComponent("propane", 0.04);
oil.addComponent("n-butane", 0.03);
oil.addComponent("n-pentane", 0.03);
oil.addTBPfraction("C7", 0.10, 100.0, 0.74);
oil.addTBPfraction("C15", 0.20, 210.0, 0.82);
oil.addTBPfraction("C30+", 0.25, 420.0, 0.90);
oil.setMixingRule("classic");

// Oil systems: bubble point line is most important
// Critical point at high temperature (often > 400°C)
```

---

## Bubble Point Pressure

For oils, the bubble point at reservoir temperature is key:

```java
// Set reservoir temperature
double reservoirT = 100.0;  // °C
fluid.setTemperature(reservoirT + 273.15);

// Calculate bubble point pressure
ops.bubblePointPressureFlash();
double bubblePointP = fluid.getPressure("bara");

System.out.printf("Bubble point at %.0f°C: %.1f bara%n", reservoirT, bubblePointP);

// Compare to reservoir pressure for undersaturation check
double reservoirP = 300.0;
double undersaturation = reservoirP - bubblePointP;
System.out.printf("Undersaturation: %.1f bar%n", undersaturation);
```

---

## Effect of Composition on Phase Envelope

### Adding Heavy Components

```java
// Base case
SystemSrkEos gas1 = createBaseGas();
ops1.calcPTphaseEnvelope();
double cricondentherm1 = ops1.getCricondentherm()[0] - 273.15;

// With added C6+
SystemSrkEos gas2 = createBaseGas();
gas2.addComponent("n-hexane", 0.02);
ops2.calcPTphaseEnvelope();
double cricondentherm2 = ops2.getCricondentherm()[0] - 273.15;

System.out.printf("Cricondentherm without C6+: %.1f °C%n", cricondentherm1);
System.out.printf("Cricondentherm with C6+: %.1f °C%n", cricondentherm2);
System.out.println("Adding heavy components increases cricondentherm!");
```

### Adding CO2

```java
// CO2 typically reduces cricondentherm but increases cricondenbar
// Important for CCS applications
```

---

## Pipeline Operating Point Analysis

Check if pipeline conditions are in single-phase region:

```java
// Pipeline operating conditions
double[] pipelineT = {10, 5, 0, -5};  // °C along pipeline
double pipelineP = 70.0;  // bara (constant pressure assumption)

System.out.println("Pipeline Operating Point Analysis:");
System.out.printf("Cricondentherm: %.1f °C%n", cricondentherm - 273.15);
System.out.printf("Operating pressure: %.0f bara%n%n", pipelineP);

for (double t : pipelineT) {
    fluid.setTemperature(t + 273.15);
    fluid.setPressure(pipelineP);
    ops.TPflash();
    
    int nPhases = fluid.getNumberOfPhases();
    String status = (nPhases == 1) ? "Single phase ✅" : "Two phase ⚠️";
    double liquidFrac = (nPhases > 1) ? fluid.getBeta(1) : 0.0;
    
    System.out.printf("T=%3.0f°C: %s (liquid frac: %.3f)%n", t, status, liquidFrac);
}
```

---

## Troubleshooting Phase Envelope Calculations

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Envelope doesn't close | Missing critical point calculation | Check convergence parameters |
| Cricondentherm too high | Heavy components or characterization | Review plus fraction properties |
| No dew point found | Fluid too light (dry gas) | Normal for methane-rich gas |
| Calculation fails | Near-critical region | Reduce step size, improve initial guess |

### Improving Convergence

```java
// Use tighter tolerances for difficult fluids
ops.setMaxIterations(500);
ops.setPTphaseEnvelopeStepsize(0.5);  // Smaller step for better resolution
```

---

## See Also

- [Flash Calculations Guide](../thermo/flash_calculations_guide.md) - TP/PH/PS flash
- [Thermodynamic Operations](../thermo/thermodynamic_operations.md) - All thermo operations
- [Flow Assurance Overview](flow_assurance_overview.md) - HCDP in context
- [Gas Quality Standards](../standards/gas_quality.md) - Pipeline specifications
