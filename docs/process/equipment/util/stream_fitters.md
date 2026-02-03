---
title: Stream Fitters: GOR and MPFM Data Fitting
description: Utilities for adjusting stream compositions based on measured Gas-Oil Ratio (GOR) or Multiphase Flow Meter (MPFM) data.
---

# Stream Fitters: GOR and MPFM Data Fitting

Utilities for adjusting stream compositions based on measured Gas-Oil Ratio (GOR) or Multiphase Flow Meter (MPFM) data.

## Table of Contents

- [Overview](#overview)
- [GORfitter](#gorfitter)
  - [Description](#description)
  - [How It Works](#how-it-works)
  - [Usage Examples](#usage-examples)
  - [Configuration](#configuration)
- [MPFMfitter](#mpfmfitter)
  - [Description](#mpfmfitter-description)
  - [Reference Fluid Package](#reference-fluid-package)
  - [Usage Examples](#mpfm-usage-examples)
- [API Reference](#api-reference)
- [Best Practices](#best-practices)
- [Python Examples](#python-examples)

---

## Overview

Stream fitters are utility process equipment that adjust the composition of a hydrocarbon stream to match measured field data. They are essential for:

1. **Production Allocation**: Matching model predictions to actual measurements
2. **Digital Twin Synchronization**: Keeping simulation aligned with real operations
3. **Virtual Flow Metering**: Calibrating VFM models to test separator data
4. **Well Test Analysis**: Adjusting simulated GOR to match test separator results

| Class | Purpose | Key Measurement |
|-------|---------|-----------------|
| `GORfitter` | Adjust stream to match measured GOR | Gas-Oil Ratio (Sm³/Sm³ or GVF) |
| `MPFMfitter` | Adjust stream based on MPFM readings | GOR + reference fluid package |

**Location:** `neqsim.process.equipment.util`

---

## GORfitter

### Description

The `GORfitter` class adjusts a hydrocarbon stream's gas content to achieve a specified Gas-Oil Ratio at standard or actual conditions. It modifies the gas phase composition while preserving the total mass flow rate.

```
                       ┌─────────────┐
   Inlet Stream ──────▶│  GORfitter  │──────▶ Adjusted Stream
   (Original GOR)      │             │        (Target GOR)
                       │ Target GOR: │
                       │   120 Sm³/Sm³│
                       └─────────────┘
```

### How It Works

1. **Flash at Reference Conditions**: The inlet stream is flashed at standard conditions (15°C, 1.01325 bara) or actual conditions
2. **Calculate Current GOR**: Measure gas volume / oil volume at reference conditions
3. **Calculate Deviation**: Determine factor to adjust gas content: `dev = targetGOR / currentGOR`
4. **Adjust Composition**: Scale gas-phase component moles by the deviation factor
5. **Re-flash at Original Conditions**: Return stream to original P/T with new composition

### Usage Examples

#### Basic GOR Adjustment

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.GORfitter;
import neqsim.thermo.system.SystemSrkEos;

// Create a reservoir fluid
SystemSrkEos fluid = new SystemSrkEos(340.0, 150.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("nC10", 0.30);
fluid.addComponent("nC20", 0.22);
fluid.setMixingRule("classic");

// Create inlet stream
Stream wellStream = new Stream("Well-A", fluid);
wellStream.setFlowRate(1000.0, "kg/hr");
wellStream.run();

// Adjust to measured GOR
GORfitter gorFitter = new GORfitter("GOR Adjuster", wellStream);
gorFitter.setGOR(150.0);  // Target GOR: 150 Sm³/Sm³
gorFitter.run();

// Get adjusted stream
double adjustedGOR = gorFitter.getOutletStream()
    .getFluid().getGOR("Sm3/Sm3");
System.out.println("Adjusted GOR: " + adjustedGOR + " Sm³/Sm³");
```

#### Using Gas Volume Fraction (GVF)

```java
// Fit using GVF instead of GOR
GORfitter gvfFitter = new GORfitter("GVF Adjuster", wellStream);
gvfFitter.setFitAsGVF(true);  // Enable GVF mode
gvfFitter.setGOR(0.35);       // Target GVF: 35%
gvfFitter.run();

double resultGVF = gvfFitter.getGFV();
System.out.println("Adjusted GVF: " + (resultGVF * 100) + "%");
```

#### Custom Reference Conditions

```java
// Use actual conditions instead of standard
GORfitter actualFitter = new GORfitter("Actual Conditions", wellStream);
actualFitter.setGOR(120.0);
actualFitter.setReferenceConditions("actual");  // Use actual P/T
actualFitter.run();
```

### Configuration

| Parameter | Method | Description | Default |
|-----------|--------|-------------|---------|
| Target GOR | `setGOR(double)` | Gas-Oil Ratio in Sm³/Sm³ | 120.0 |
| Reference Conditions | `setReferenceConditions(String)` | "standard" or "actual" | "standard" |
| GVF Mode | `setFitAsGVF(boolean)` | Treat GOR value as GVF fraction | false |
| Reference P | `setPressure(double, String)` | Reference pressure | 1.01325 bara |
| Reference T | `setTemperature(double, String)` | Reference temperature | 15°C |

---

## MPFMfitter

### MPFMfitter Description

The `MPFMfitter` extends GOR fitting capabilities with support for a reference fluid package, enabling more accurate matching with Multiphase Flow Meter readings.

### Reference Fluid Package

The MPFM fitter can use a separate "reference" thermodynamic system for GOR calculations while preserving the original fluid package for downstream simulation:

```java
import neqsim.process.equipment.util.MPFMfitter;

// Create main fluid
SystemSrkCPAstatoil processFluid = new SystemSrkCPAstatoil(340.0, 150.0);
// ... add components

// Create reference fluid for MPFM calculations
SystemSrkEos referenceFluid = new SystemSrkEos(288.15, 1.01325);
referenceFluid.addComponent("methane", 0.85);
referenceFluid.addComponent("ethane", 0.08);
referenceFluid.addComponent("propane", 0.04);
referenceFluid.addComponent("nC6", 0.03);
referenceFluid.setMixingRule("classic");

// Set up MPFM fitter
MPFMfitter mpfm = new MPFMfitter("MPFM-101", processStream);
mpfm.setReferenceFluidPackage(referenceFluid);
mpfm.setGOR(145.0);  // MPFM-measured GOR
mpfm.run();
```

### MPFM Usage Examples

#### Complete Well Test Analysis

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.MPFMfitter;
import neqsim.process.equipment.separator.Separator;

// Build process
ProcessSystem process = new ProcessSystem("Well Test");

// Well stream (from reservoir model or initial guess)
Stream wellStream = new Stream("Well-A", reservoirFluid);
wellStream.setFlowRate(5000.0, "kg/hr");
process.add(wellStream);

// MPFM adjustment based on field measurement
MPFMfitter mpfm = new MPFMfitter("MPFM-201", wellStream);
mpfm.setGOR(125.0);  // From MPFM reading
mpfm.setReferenceConditions("standard");
process.add(mpfm);

// Test separator
Separator testSep = new Separator("Test Separator", mpfm.getOutletStream());
testSep.setInternalDiameter(1.2);
process.add(testSep);

// Run analysis
process.run();

// Verify results match MPFM
double measuredGOR = mpfm.getGOR();
double separatorGOR = testSep.getGasOutStream().getFluid().getGOR("Sm3/Sm3");
System.out.println("MPFM GOR: " + measuredGOR);
System.out.println("Separator GOR: " + separatorGOR);
```

---

## API Reference

### GORfitter

| Method | Return Type | Description |
|--------|-------------|-------------|
| `GORfitter(String, StreamInterface)` | - | Constructor |
| `setGOR(double)` | void | Set target GOR (Sm³/Sm³ or GVF if fitAsGVF) |
| `getGOR()` | double | Get current GOR setting |
| `setFitAsGVF(boolean)` | void | Treat GOR as GVF fraction |
| `getFitAsGVF()` | boolean | Check if fitting as GVF |
| `getGFV()` | double | Get resulting GVF after fitting |
| `setReferenceConditions(String)` | void | "standard" or "actual" |
| `getReferenceConditions()` | String | Get current reference setting |
| `setPressure(double, String)` | void | Set reference pressure |
| `setTemperature(double, String)` | void | Set reference temperature |
| `run()` | void | Execute fitting calculation |

### MPFMfitter

*Inherits all GORfitter methods plus:*

| Method | Return Type | Description |
|--------|-------------|-------------|
| `setReferenceFluidPackage(SystemInterface)` | void | Set reference fluid for GOR calc |
| `getReferenceFluidPackage()` | SystemInterface | Get reference fluid |

---

## Best Practices

### 1. Reference Conditions Consistency

Ensure reference conditions match how GOR was measured:

```java
// For standard conditions (most common)
gorFitter.setReferenceConditions("standard");
gorFitter.setTemperature(15.0, "C");          // SC temperature
gorFitter.setPressure(1.01325, "bara");       // SC pressure

// For actual conditions (downhole MPFM)
gorFitter.setReferenceConditions("actual");
```

### 2. GVF vs GOR

Choose the appropriate mode based on your measurement:

```java
// GOR mode: Gas/Oil volume ratio (Sm³/Sm³)
gorFitter.setFitAsGVF(false);
gorFitter.setGOR(150.0);  // 150 Sm³ gas per Sm³ oil

// GVF mode: Gas volume fraction (0-1)  
gorFitter.setFitAsGVF(true);
gorFitter.setGOR(0.40);   // 40% gas by volume
```

### 3. Handling Edge Cases

```java
// Zero GOR (dead oil)
gorFitter.setGOR(0.0);  // Removes all gas

// Very high GOR (gas condensate)
gorFitter.setGOR(5000.0);  // High gas content

// Check for valid output
if (!Double.isNaN(gorFitter.getGFV())) {
    // Valid result
} else {
    // Handle invalid result
}
```

---

## Python Examples

### Basic GOR Fitting (Python)

```python
from neqsim.process.equipment.util import GORfitter
from neqsim.process.equipment.stream import Stream
from neqsim.thermo.system import SystemSrkEos

# Create fluid
fluid = SystemSrkEos(340.0, 150.0)
fluid.addComponent("methane", 0.35)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("nC10", 0.40)
fluid.addComponent("nC20", 0.20)
fluid.setMixingRule("classic")

# Create stream
well = Stream("Well-1", fluid)
well.setFlowRate(2000.0, "kg/hr")
well.run()

# Apply GOR fitting
gor_fitter = GORfitter("GOR-Fitter", well)
gor_fitter.setGOR(125.0)  # Target GOR from well test
gor_fitter.run()

# Check result
fitted_stream = gor_fitter.getOutletStream()
print(f"Fitted GOR: {fitted_stream.getFluid().getGOR('Sm3/Sm3'):.1f} Sm³/Sm³")
print(f"GVF: {gor_fitter.getGFV() * 100:.1f}%")
```

### GVF Mode (Python)

```python
# Use GVF mode for volume fraction
gvf_fitter = GORfitter("GVF-Fitter", well)
gvf_fitter.setFitAsGVF(True)
gvf_fitter.setGOR(0.30)  # Target 30% GVF
gvf_fitter.run()

print(f"Result GVF: {gvf_fitter.getGFV() * 100:.1f}%")
```

---

## Related Documentation

- [Well Allocation](well_allocation.md) - Production allocation methods
- [Streams](streams.md) - Stream creation and manipulation
- [Separators](separators.md) - Test separator modeling
- [Digital Twin Integration](../digital-twin-integration.md) - Real-time calibration

---

*Package Location: `neqsim.process.equipment.util`*
