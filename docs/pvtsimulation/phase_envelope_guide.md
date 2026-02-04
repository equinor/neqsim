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

## Algorithm Overview

NeqSim uses a **continuation method** (Newton-Raphson based) to trace the phase envelope. Understanding the algorithm helps interpret results and troubleshoot issues.

### How the Algorithm Works

1. **Starting Point Selection**: Based on the `bubblePointFirst` parameter:
   - `bubblePointFirst=true`: Starts at low pressure, finds bubble point using the **most volatile** component (lowest critical temperature)
   - `bubblePointFirst=false` (default): Starts at low pressure, finds dew point using the **least volatile** component (highest critical temperature)

2. **Initial Temperature Estimation**: Uses Wilson K-value correlation to estimate starting temperature at the specified low pressure

3. **Envelope Tracing**: Newton-Raphson solver steps along the envelope, adjusting T and P to maintain phase equilibrium

4. **Critical Point Detection**: When K-values for both light and heavy components approach 1.0 (K ≈ 1), the algorithm:
   - Records the critical point position
   - Inverts phase types (switches from bubble to dew branch or vice versa)
   - Continues tracing on the other side

5. **Automatic Restart**: If the algorithm fails before completing, it automatically restarts from the opposite end to capture the full envelope

### Data Structure

The envelope data is stored in a `points2` array with multiple branches:

| Index | Description |
|-------|-------------|
| `points2[0]`, `points2[1]` | First branch T/P (start to critical point) |
| `points2[2]`, `points2[3]` | Second branch T/P (critical point to end) |
| `points2[4]`, `points2[5]` | Third branch T/P (if restarted from other side) |
| `points2[6]`, `points2[7]` | Fourth branch T/P (if restarted from other side) |

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

// Calculate phase envelope (default: starts from dew point)
ops.calcPTphaseEnvelope();

// Get cricondenbar [T(K), P(bara)]
double[] cricondenbar = ops.get("cricondenbar");
double cricondenbarP = cricondenbar[1];    // Pressure (bara)
double cricondenbarT = cricondenbar[0];    // Temperature at cricondenbar (K)

// Get cricondentherm [T(K), P(bara)]
double[] cricondentherm = ops.get("cricondentherm");
double criconderthermT = cricondentherm[0];  // Temperature (K)
double criconderthermP = cricondentherm[1];  // Pressure at cricondentherm (bara)

// Get critical point [T(K), P(bara)]
double[] criticalPoint = ops.get("criticalPoint1");
double criticalT = criticalPoint[0];
double criticalP = criticalPoint[1];

System.out.println("=== Phase Envelope Results ===");
System.out.printf("Cricondenbar: %.2f bara at %.1f °C%n", 
    cricondenbarP, cricondenbarT - 273.15);
System.out.printf("Cricondentherm: %.1f °C at %.2f bara%n", 
    criconderthermT - 273.15, criconderthermP);
System.out.printf("Critical point: %.1f °C, %.2f bara%n", 
    criticalT - 273.15, criticalP);

// Get all envelope data for plotting (2D array)
double[][] envelope = ops.getData();
// envelope contains multiple branches - see "Retrieving Envelope Data" section
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

# Get critical points using the get() method
cricondenbar = list(ops.get("cricondenbar"))      # [T(K), P(bara)]
cricondentherm = list(ops.get("cricondentherm"))  # [T(K), P(bara)]
critical = list(ops.get("criticalPoint1"))        # [T(K), P(bara)]

print(f"Cricondenbar: {cricondenbar[1]:.2f} bara at {cricondenbar[0] - 273.15:.1f} °C")
print(f"Cricondentherm: {cricondentherm[0] - 273.15:.1f} °C at {cricondentherm[1]:.2f} bara")
print(f"Critical Point: {critical[0] - 273.15:.1f} °C, {critical[1]:.2f} bara")
```

---

## Controlling the Starting Point

You can control whether the algorithm starts from the bubble point or dew point side:

```java
// Start from BUBBLE point (trace bubble curve first, then dew after critical)
ops.calcPTphaseEnvelope(true);  // bubblePointFirst = true

// Start from DEW point (default behavior)
ops.calcPTphaseEnvelope(false); // bubblePointFirst = false

// With custom low pressure starting point
ops.calcPTphaseEnvelope(true, 0.5);  // bubbleFirst=true, lowPres=0.5 bar

// With custom phase fraction (0 = bubble, 1 = dew)
ops.calcPTphaseEnvelope(1.0, 1e-10);  // lowPres=1 bar, phaseFraction≈0 (bubble)
```

**When to use each:**
- **Bubble first**: Better for oil-rich systems where bubble point is more important
- **Dew first** (default): Better for gas systems where dew point/HCDP is critical

---

## Retrieving Envelope Data

### Available Data Keys

Use `ops.get(key)` to retrieve specific envelope data:

| Key | Returns | Description |
|-----|---------|-------------|
| `"dewT"` | `double[]` | Dew point temperatures (K) - first branch |
| `"dewP"` | `double[]` | Dew point pressures (bara) - first branch |
| `"bubT"` | `double[]` | Bubble point temperatures (K) - second branch |
| `"bubP"` | `double[]` | Bubble point pressures (bara) - second branch |
| `"dewT2"` | `double[]` | Dew temperatures if restarted from other side |
| `"dewP2"` | `double[]` | Dew pressures if restarted from other side |
| `"bubT2"` | `double[]` | Bubble temperatures if restarted from other side |
| `"bubP2"` | `double[]` | Bubble pressures if restarted from other side |
| `"cricondenbar"` | `double[]` | [T(K), P(bara)] at maximum pressure |
| `"cricondentherm"` | `double[]` | [T(K), P(bara)] at maximum temperature |
| `"criticalPoint1"` | `double[]` | [Tc(K), Pc(bara)] critical point |
| `"dewH"` | `double[]` | Dew point enthalpies |
| `"bubH"` | `double[]` | Bubble point enthalpies |
| `"dewDens"` | `double[]` | Dew point densities |
| `"bubDens"` | `double[]` | Bubble point densities |
| `"dewS"` | `double[]` | Dew point entropies |
| `"bubS"` | `double[]` | Bubble point entropies |

### Getting All Data

```java
// Get all envelope points as 2D array
double[][] allData = ops.getData();

// allData structure depends on whether envelope completed or restarted:
// allData[0], allData[1] = First branch (T, P)
// allData[2], allData[3] = Second branch (T, P) after critical point
// allData[4], allData[5] = Third branch if restarted
// allData[6], allData[7] = Fourth branch if restarted
```

### Complete Example: Extracting Both Curves

```java
// Calculate envelope
ops.calcPTphaseEnvelope();

// Get temperatures and pressures for plotting
double[] dewT = ops.get("dewT");   // First branch temperatures
double[] dewP = ops.get("dewP");   // First branch pressures
double[] bubT = ops.get("bubT");   // Second branch temperatures (after critical)
double[] bubP = ops.get("bubP");   // Second branch pressures

// Note: "dew" and "bub" naming depends on bubblePointFirst setting
// With default (bubblePointFirst=false):
//   dewT/dewP = dew point curve (before critical)
//   bubT/bubP = bubble point curve (after critical)

// Critical point
double[] crit = ops.get("criticalPoint1");
System.out.printf("Critical: %.1f K, %.2f bar%n", crit[0], crit[1]);

// Combine for full envelope
System.out.println("Dew curve points: " + dewT.length);
System.out.println("Bubble curve points: " + bubT.length);
```

---

## Plotting Phase Envelopes

### With Matplotlib (Python)

```python
import matplotlib.pyplot as plt
import numpy as np
from neqsim import jneqsim

# Create fluid
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

fluid = SystemSrkEos(273.15 + 20.0, 50.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.05)
fluid.setMixingRule("classic")

ops = ThermodynamicOperations(fluid)
ops.calcPTphaseEnvelope()

# Get envelope data
dewT = np.array(list(ops.get("dewT"))) - 273.15  # Convert to °C
dewP = np.array(list(ops.get("dewP")))
bubT = np.array(list(ops.get("bubT"))) - 273.15
bubP = np.array(list(ops.get("bubP")))

# Get critical points
cricondenbar = list(ops.get("cricondenbar"))
cricondentherm = list(ops.get("cricondentherm"))
critical = list(ops.get("criticalPoint1"))

# Plot
plt.figure(figsize=(10, 7))
plt.plot(dewT, dewP, 'b-', label='Dew Point', linewidth=2)
plt.plot(bubT, bubP, 'r-', label='Bubble Point', linewidth=2)

# Mark critical points
plt.plot(cricondenbar[0] - 273.15, cricondenbar[1], 'ko', markersize=10, label='Cricondenbar')
plt.plot(cricondentherm[0] - 273.15, cricondentherm[1], 'g^', markersize=10, label='Cricondentherm')
plt.plot(critical[0] - 273.15, critical[1], 'rs', markersize=10, label='Critical Point')

plt.xlabel('Temperature (°C)', fontsize=12)
plt.ylabel('Pressure (bara)', fontsize=12)
plt.title('Phase Envelope', fontsize=14)
plt.legend()
plt.grid(True, alpha=0.3)
plt.show()
```

### With JFreeChart (Java - built-in)

```java
// NeqSim has built-in JFreeChart display
ops.calcPTphaseEnvelope();
ops.displayResult();  // Opens a window with the phase envelope plot
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

Quality lines show constant vapor/liquid fraction within the two-phase region. NeqSim calculates these by running phase envelope calculations at different phase fractions:

```java
// Calculate quality lines by varying phaseFraction parameter
// phaseFraction = 0 -> bubble point (0% vapor)
// phaseFraction = 1 -> dew point (100% vapor)

double[] qualities = {0.1, 0.25, 0.5, 0.75, 0.9};  // vapor fractions

for (double q : qualities) {
    ThermodynamicOperations opsQ = new ThermodynamicOperations(fluid.clone());
    // lowPres=1.0, phaseFraction=q
    opsQ.calcPTphaseEnvelope(1.0, q);
    
    double[][] qualityLine = opsQ.getData();
    System.out.printf("Quality %.0f%% vapor - %d points%n", q * 100, qualityLine[0].length);
}
```

**Note:** The `calcPTphaseEnvelope(double lowPres, double phaseFraction)` method allows specifying a phase fraction between 0 (bubble) and 1 (dew) to trace quality lines.

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
ThermodynamicOperations ops1 = new ThermodynamicOperations(gas1);
ops1.calcPTphaseEnvelope();
double[] cricoT1 = ops1.get("cricondentherm");
double cricondentherm1 = cricoT1[0] - 273.15;

// With added C6+
SystemSrkEos gas2 = createBaseGas();
gas2.addComponent("n-hexane", 0.02);
ThermodynamicOperations ops2 = new ThermodynamicOperations(gas2);
ops2.calcPTphaseEnvelope();
double[] cricoT2 = ops2.get("cricondentherm");
double cricondentherm2 = cricoT2[0] - 273.15;

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
// Get cricondentherm from envelope calculation
double[] cricoTherm = ops.get("cricondentherm");
double cricondenthermC = cricoTherm[0] - 273.15;

// Pipeline operating conditions
double[] pipelineT = {10, 5, 0, -5};  // °C along pipeline
double pipelineP = 70.0;  // bara (constant pressure assumption)

System.out.println("Pipeline Operating Point Analysis:");
System.out.printf("Cricondentherm: %.1f °C%n", cricondenthermC);
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
| Envelope doesn't close | Algorithm failed before reaching critical point | Try starting from the other side with `calcPTphaseEnvelope(true)` |
| Missing branch | Algorithm restarted from other side | Check `dewT2`/`bubT2` arrays for additional data |
| Cricondentherm too high | Heavy components or characterization | Review plus fraction properties |
| No dew point found | Fluid too light (dry gas) | Normal for methane-rich gas |
| Calculation fails | Near-critical region or bad initial guess | Try different starting pressure with `calcPTphaseEnvelope(true, 0.5)` |
| NaN values in results | Non-convergence | Check fluid composition, try simpler EoS |

### Understanding Algorithm Behavior

The algorithm may produce data in multiple branches:

```java
ops.calcPTphaseEnvelope();

// Check what data is available
double[] dewT = ops.get("dewT");
double[] bubT = ops.get("bubT");
double[] dewT2 = ops.get("dewT2");  // May be empty if no restart
double[] bubT2 = ops.get("bubT2");

System.out.println("First branch (dew): " + (dewT != null ? dewT.length : 0) + " points");
System.out.println("Second branch (bub): " + (bubT != null ? bubT.length : 0) + " points");
System.out.println("Third branch (dew2): " + (dewT2 != null ? dewT2.length : 0) + " points");
System.out.println("Fourth branch (bub2): " + (bubT2 != null ? bubT2.length : 0) + " points");
```

### Alternative: calcPTphaseEnvelope2

For difficult fluids, try the alternative algorithm:

```java
// Uses PTphaseEnvelopeNew2 implementation
ops.calcPTphaseEnvelope2();
```

---

## API Reference Summary

### calcPTphaseEnvelope Overloads

| Method | Description |
|--------|-------------|
| `calcPTphaseEnvelope()` | Default: dew first, lowPres=1.0 bar |
| `calcPTphaseEnvelope(boolean bubbleFirst)` | Control start side |
| `calcPTphaseEnvelope(double lowPres)` | Custom starting pressure |
| `calcPTphaseEnvelope(boolean bubbleFirst, double lowPres)` | Both options |
| `calcPTphaseEnvelope(double lowPres, double phaseFraction)` | Quality lines (0=bubble, 1=dew) |
| `calcPTphaseEnvelope2()` | Alternative algorithm (PTphaseEnvelopeNew2) |

---

## See Also

- [Flash Calculations Guide](../thermo/flash_calculations_guide) - TP/PH/PS flash
- [Thermodynamic Operations](../thermo/thermodynamic_operations) - All thermo operations
- [Flow Assurance Overview](flow_assurance_overview) - HCDP in context
- [Gas Quality Standards](../standards/gas_quality) - Pipeline specifications
