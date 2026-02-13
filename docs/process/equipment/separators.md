---
title: Separator Equipment
description: "Documentation for separator equipment in NeqSim process simulation including two-phase separators, three-phase separators, gas scrubbers, entrainment specification, and design constraints."
---

# Separator Equipment

Documentation for separator equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Separator Types](#separator-types)
- [Three-Phase Separator in Detail](#three-phase-separator-in-detail)
- [Entrainment](#entrainment)
- [Horizontal Separator Design Parameters](#horizontal-separator-design-parameters)
- [Three-Phase Separator Design Parameters](#three-phase-separator-design-parameters)
- [Gas Scrubber Design Parameters](#gas-scrubber-design-parameters)
- [Performance Constraints](#performance-constraints)
- [Constraint Selection Methods](#constraint-selection-methods)
- [Usage Examples](#usage-examples)
- [Design Calculations](#design-calculations)

---

## Overview

**Location:** `neqsim.process.equipment.separator`

**Classes:**
- `Separator` - Two-phase gas-liquid separator (horizontal or vertical)
- `ThreePhaseSeparator` - Three-phase gas-oil-water separator with interface levels
- `GasScrubber` - Vertical gas scrubbing separator (K-value constrained)
- `GasScrubberSimple` - Simplified gas scrubber model

---

## Separator Types

### Two-Phase Separator

```java
import neqsim.process.equipment.separator.Separator;

Separator separator = new Separator("V-100", inletStream);
separator.run();

// Get outlet streams
Stream gasOut = separator.getGasOutStream();
Stream liquidOut = separator.getLiquidOutStream();

// Properties
double gasRate = gasOut.getFlowRate("kg/hr");
double liquidRate = liquidOut.getFlowRate("kg/hr");
double liquidLevel = separator.getLiquidLevel();
```

### Three-Phase Separator

```java
import neqsim.process.equipment.separator.ThreePhaseSeparator;

ThreePhaseSeparator separator = new ThreePhaseSeparator("V-200", inletStream);
separator.run();

// Get outlet streams
Stream gasOut = separator.getGasOutStream();
Stream oilOut = separator.getOilOutStream();
Stream waterOut = separator.getWaterOutStream();

// Water cut
double waterCut = separator.getWaterCut();
```

### Gas Scrubber

```java
import neqsim.process.equipment.separator.GasScrubber;

GasScrubber scrubber = new GasScrubber("Inlet Scrubber", gasStream);
scrubber.run();

// Dry gas output
Stream dryGas = scrubber.getGasOutStream();

// Condensate removal
Stream condensate = scrubber.getLiquidOutStream();
```

> **Note:** Gas scrubbers automatically use K-value only constraints (`useGasScrubberConstraints()`). 
> This is appropriate since scrubbers focus on gas-phase separation efficiency rather than liquid retention time.

---

## Three-Phase Separator in Detail

The `ThreePhaseSeparator` extends `Separator` and separates a multiphase feed into three outlet streams: gas, oil, and water (aqueous). It requires the fluid to have `setMultiPhaseCheck(true)` so the thermodynamic flash produces distinct oil and aqueous phases.

### Creating a Three-Phase Separator

```java
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Create a fluid with gas, oil, and water components
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(273.15 + 42.0, 10.0);
fluid.addComponent("methane", 72.0);
fluid.addComponent("n-heptane", 14.0);
fluid.addComponent("water", 40.0);
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);  // REQUIRED for three-phase separation

Stream feed = new Stream("Feed", fluid);
feed.setTemperature(72.0, "C");
feed.setPressure(10.7, "bara");
feed.setFlowRate(720.0, "kg/hr");
feed.run();

// Create three-phase separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("1st Stage Sep", feed);
separator.run();
```

### Outlet Streams

```java
// Gas outlet
StreamInterface gasOut = separator.getGasOutStream();
double gasRate = gasOut.getFlowRate("kg/hr");

// Oil outlet
StreamInterface oilOut = separator.getOilOutStream();
double oilRate = oilOut.getFlowRate("kg/hr");

// Water outlet
StreamInterface waterOut = separator.getWaterOutStream();
double waterRate = waterOut.getFlowRate("kg/hr");
```

### Connecting to Downstream Equipment

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);

ThreePhaseSeparator separator = new ThreePhaseSeparator("HP Sep", feed);
process.add(separator);

// Gas to compressor
Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
process.add(compressor);

// Oil to heater and next stage
Heater oilHeater = new Heater("Oil Heater", separator.getOilOutStream());
oilHeater.setOutTemperature(85.0, "C");
process.add(oilHeater);

// Water to treatment
ThrottlingValve waterValve = new ThrottlingValve("Water Valve", separator.getWaterOutStream());
waterValve.setOutletPressure(1.01325);
process.add(waterValve);

process.run();
```

### Adding Multiple Inlet Streams

```java
ThreePhaseSeparator separator = new ThreePhaseSeparator("HP Sep", primaryFeed);
separator.addStream(recycleFeed);  // Additional inlet stream
separator.run();
```

### Vessel Dimensions and Dynamic Simulation

```java
// Set physical dimensions
separator.setInternalDiameter(2.5, "m");
separator.setSeparatorLength(10.0);

// Water and oil levels (for dynamic mode)
separator.setWaterLevel(0.3);  // meters from bottom
separator.setOilLevel(0.6);    // meters from bottom (includes water below)
double oilThickness = separator.getOilThickness();  // oil layer thickness

// Outlet valve fractions for dynamic control (0.0 = closed, 1.0 = open)
separator.setGasOutletFlowFraction(1.0);
separator.setOilOutletFlowFraction(0.8);
separator.setWaterOutletFlowFraction(0.9);
```

---

## Entrainment

Entrainment models imperfect separation by transferring a fraction of one phase into another outlet stream. Both `Separator` and `ThreePhaseSeparator` support entrainment specification through the `setEntrainment()` method.

### Method Signature

```java
void setEntrainment(double val, String specType, String specifiedStream,
                    String phaseFrom, String phaseTo)
```

| Parameter | Description | Values |
|-----------|-------------|--------|
| `val` | Fraction of the source phase to entrain | 0.0 to 1.0 (e.g., 0.05 = 5%) |
| `specType` | Basis for the fraction | `"mole"`, `"mass"`, or `"volume"` |
| `specifiedStream` | Reference stream for the fraction | `"feed"` or `"product"` |
| `phaseFrom` | Phase being entrained (source) | `"gas"`, `"oil"`, `"aqueous"` |
| `phaseTo` | Phase receiving the entrainment (destination) | `"gas"`, `"oil"`, `"aqueous"`, `"liquid"` |

### Specification Type (`specType`)

- **`"mole"`** - Fraction is on a molar basis (moles of source phase transferred / total moles of reference)
- **`"mass"`** - Fraction is on a mass basis (kg transferred / total kg of reference)
- **`"volume"`** - Fraction is on a volumetric basis (m3 transferred / total m3 of reference)

### Specified Stream (`specifiedStream`)

- **`"feed"`** - The fraction is relative to the feed stream composition
- **`"product"`** - The fraction is relative to the outlet product stream composition

### Two-Phase Separator Entrainment

The `Separator` class supports three entrainment paths:

| From Phase | To Phase | Description |
|------------|----------|-------------|
| `"oil"` | `"gas"` | Oil droplets carried over into the gas outlet |
| `"aqueous"` | `"gas"` | Water droplets carried over into the gas outlet |
| `"gas"` | `"liquid"` | Gas bubbles carried under into the liquid outlet |

```java
Separator separator = new Separator("HP Sep", feed);

// 10% of feed oil moles entrained into gas
separator.setEntrainment(0.10, "mole", "feed", "oil", "gas");

// 5% of feed water moles entrained into gas
separator.setEntrainment(0.05, "mole", "feed", "aqueous", "gas");

// 8% of feed gas moles entrained into liquid
separator.setEntrainment(0.08, "mole", "feed", "gas", "liquid");

separator.run();
```

> **Note:** When using `"gas"` to `"liquid"` entrainment in a system with both oil and aqueous phases (but no combined "liquid" phase), the entrained gas is split proportionally between the oil and aqueous phases based on their molar content.

### Three-Phase Separator Entrainment

The `ThreePhaseSeparator` supports six entrainment paths covering all phase-to-phase combinations:

| From Phase | To Phase | Description |
|------------|----------|-------------|
| `"oil"` | `"gas"` | Oil droplets in gas outlet |
| `"aqueous"` | `"gas"` | Water droplets in gas outlet |
| `"gas"` | `"oil"` | Gas bubbles in oil outlet |
| `"gas"` | `"aqueous"` | Gas bubbles in water outlet |
| `"oil"` | `"aqueous"` | Oil droplets in water outlet |
| `"aqueous"` | `"oil"` | Water droplets in oil outlet (water-in-oil) |

```java
ThreePhaseSeparator separator = new ThreePhaseSeparator("1st Stage Sep", feed);

// Water-in-oil: 0.5% by mass in the product oil stream
separator.setEntrainment(0.005, "mass", "product", "aqueous", "oil");

// Oil-in-water: 500 ppm by mole in the product water stream
separator.setEntrainment(500e-6, "mole", "product", "oil", "aqueous");

separator.run();

// Verify water content in oil
double waterInOilVolPct = separator.getOilOutStream().getFluid()
    .getPhase("aqueous").getFlowRate("m3/hr")
    / (separator.getOilOutStream().getFluid().getPhase("oil").getFlowRate("m3/hr")
       + separator.getOilOutStream().getFluid().getPhase("aqueous").getFlowRate("m3/hr"))
    * 100.0;
```

### Example: Full Entrainment Specification

```java
ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Sep", feed);

// Liquid carryover into gas
separator.setEntrainment(0.001, "mole", "feed", "oil", "gas");
separator.setEntrainment(0.001, "mole", "feed", "aqueous", "gas");

// Gas carry-under into liquids
separator.setEntrainment(0.002, "mole", "feed", "gas", "oil");
separator.setEntrainment(0.001, "mole", "feed", "gas", "aqueous");

// Oil-water cross-contamination
separator.setEntrainment(0.05, "volume", "product", "aqueous", "oil");  // 5 vol% water-in-oil
separator.setEntrainment(0.01, "volume", "product", "oil", "aqueous");  // 1 vol% oil-in-water

separator.run();
```

### Example: Offshore Three-Stage Separation with Entrainment

```java
// Create fluid
SystemInterface fluid = new neqsim.thermo.Fluid().create("black oil with water");

Stream wellStream = new Stream("Well Stream", fluid);
wellStream.setFlowRate(14.23, "MSm3/day");
wellStream.setTemperature(41.0, "C");
wellStream.setPressure(120.0, "bara");

// Inlet choke
ThrottlingValve chokeValve = new ThrottlingValve("Inlet Choke", wellStream);
chokeValve.setOutletPressure(35.0);

// 1st stage separator with entrainment
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", chokeValve.getOutletStream());
hpSep.setEntrainment(0.005, "mass", "product", "aqueous", "oil");   // 0.5% water in oil
hpSep.setEntrainment(500e-6, "mole", "product", "oil", "aqueous");  // 500 ppm oil in water

// 2nd stage: heat oil and reduce pressure
Heater oilHeater = new Heater("Oil Heater", hpSep.getOilOutStream());
oilHeater.setOutTemperature(85.0, "C");

ThrottlingValve mpValve = new ThrottlingValve("MP Valve", oilHeater.getOutletStream());
mpValve.setOutletPressure(7.0);

// 2nd stage separator
ThreePhaseSeparator mpSep = new ThreePhaseSeparator("MP Separator", mpValve.getOutletStream());
mpSep.setEntrainment(0.003, "mass", "product", "aqueous", "oil");

// Water treatment
ThrottlingValve waterValve = new ThrottlingValve("Water DP Valve", hpSep.getWaterOutStream());
waterValve.setOutletPressure(1.01325);
Separator waterDegasser = new Separator("Water Degasser", waterValve.getOutletStream());

// Build and run process
ProcessSystem process = new ProcessSystem();
process.add(wellStream);
process.add(chokeValve);
process.add(hpSep);
process.add(oilHeater);
process.add(mpValve);
process.add(mpSep);
process.add(waterValve);
process.add(waterDegasser);
process.run();
```

### Zero Entrainment (Perfect Separation)

Setting entrainment to 0.0 or not calling `setEntrainment()` at all both result in ideal (perfect) separation where each phase goes entirely to its designated outlet:

```java
// These are equivalent:
Separator idealSep = new Separator("Ideal Sep", feed);
idealSep.run();

Separator explicitZero = new Separator("Zero Entrainment Sep", feed);
explicitZero.setEntrainment(0.0, "mole", "feed", "oil", "gas");
explicitZero.setEntrainment(0.0, "mole", "feed", "aqueous", "gas");
explicitZero.setEntrainment(0.0, "mole", "feed", "gas", "liquid");
explicitZero.run();
// Both produce identical outlet compositions
```

### Entrainment with Python (neqsim-python)

```python
from neqsim import jneqsim

SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
Stream = jneqsim.process.equipment.stream.Stream
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

# Create fluid
fluid = SystemSrkCPAstatoil(273.15 + 42.0, 10.0)
fluid.addComponent("methane", 72.0)
fluid.addComponent("n-heptane", 14.0)
fluid.addComponent("water", 40.0)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)

feed = Stream("Feed", fluid)
feed.setTemperature(72.0, "C")
feed.setPressure(10.7, "bara")
feed.setFlowRate(720.0, "kg/hr")

# Create separator with entrainment
separator = ThreePhaseSeparator("HP Sep", feed)
separator.setEntrainment(0.005, "mass", "product", "aqueous", "oil")
separator.setEntrainment(500e-6, "mole", "product", "oil", "aqueous")

process = ProcessSystem()
process.add(feed)
process.add(separator)
process.run()

# Read results
oil_rate = separator.getOilOutStream().getFlowRate("kg/hr")
water_rate = separator.getWaterOutStream().getFlowRate("kg/hr")
gas_rate = separator.getGasOutStream().getFlowRate("kg/hr")
print(f"Oil: {oil_rate:.1f} kg/hr, Water: {water_rate:.1f} kg/hr, Gas: {gas_rate:.1f} kg/hr")
```

---

## Horizontal Separator Design Parameters

Horizontal separators have specific geometry parameters for sizing and level calculations.

### Vessel Geometry

| Parameter | Method | Description | Unit |
|-----------|--------|-------------|------|
| Internal Diameter | `setInternalDiameter(value, unit)` | Vessel ID | m |
| Length | `setLength(value, unit)` | Tan-to-tan length | m |
| L/D Ratio | `getLengthDiameterRatio()` | Length to diameter ratio (design target: 3-5) | - |

### Liquid Levels (Horizontal Separators)

Liquid levels are defined as percentages of internal diameter (ID). The mechanical design calculates absolute heights.

| Level | Method | Description | Default % of ID |
|-------|--------|-------------|-----------------|
| HHLL | `getHHLL()` | High-High Liquid Level (alarm/shutdown) | 75% |
| HLL | `getHLL()` | High Liquid Level (K-value reference) | 70% |
| NLL | `getNLL()` | Normal Liquid Level (design point) | 50% |
| LLL | `getLLL()` | Low Liquid Level (control warning) | 30% |
| LLLL | `getLLLL()` | Low-Low Liquid Level (alarm/shutdown) | 25% |

```java
// Configure liquid levels (percentage of internal diameter)
SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) separator.getMechanicalDesign();
design.setHHLLFraction(0.75);  // 75% of ID
design.setHLLFraction(0.70);   // 70% of ID  
design.setNLLFraction(0.50);   // 50% of ID
design.setLLLFraction(0.30);   // 30% of ID
design.setLLLLFraction(0.25);  // 25% of ID

// Calculate and retrieve absolute levels
design.calcDesign();
double hhlAbsolute = design.getHHLL();  // in meters
double hllAbsolute = design.getHLL();
```

### Effective Lengths

For horizontal separators, effective lengths define zones for gas-liquid separation.

| Parameter | Method | Description |
|-----------|--------|-------------|
| Gas Effective Length | `getGasEffectiveLength()` | Length for gas separation (inlet to outlet nozzle) |
| Liquid Effective Length | `getLiquidEffectiveLength()` | Length for liquid settling |

```java
// Get effective lengths for capacity calculations
double Leff_gas = separator.getMechanicalDesign().getGasEffectiveLength();
double Leff_liquid = separator.getMechanicalDesign().getLiquidEffectiveLength();
```

### Pre-Designed Separator Setup

For existing/pre-designed separators, use the convenience methods:

```java
// Option 1: Set from existing design
separator.setFromExistingDesign(
    2.5,    // internal diameter [m]
    10.0,   // length (tan-to-tan) [m]
    0.70,   // HLL fraction (% of ID)
    0.50,   // NLL fraction (% of ID)
    8.0,    // liquid effective length [m]
    9.0     // gas effective length [m]
);

// Option 2: Alternative design specification
separator.setFromDesignSpec(
    2.5,    // internal diameter [m]
    10.0,   // length [m]
    0.70,   // HLL fraction
    0.50    // NLL fraction
);
// Effective lengths default to 80% and 90% of total length
```

---

## Three-Phase Separator Design Parameters

Three-phase separators have additional interface level parameters for oil-water separation.

### Interface Levels

| Level | Method | Description | Default % of ID |
|-------|--------|-------------|-----------------|
| HIL | `getHIL()` | High Interface Level | 45% |
| NIL | `getNIL()` | Normal Interface Level (design point) | 40% |
| LIL | `getLIL()` | Low Interface Level | 35% |

```java
// Configure interface levels
SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) separator.getMechanicalDesign();
design.setHILFraction(0.45);  // 45% of ID
design.setNILFraction(0.40);  // 40% of ID
design.setLILFraction(0.35);  // 35% of ID

// Calculate designs
design.calcDesign();

// Get absolute interface levels
double nilAbsolute = design.getNIL();  // in meters
```

### Weir Configuration

Three-phase separators typically use a weir to maintain the oil-water interface.

```java
// Set weir height (typically at or slightly above NIL)
design.setWeirHeight(design.getNIL() * 1.05);  // 5% above NIL
```

### Three-Phase Specific Calculations

```java
ThreePhaseSeparator separator = new ThreePhaseSeparator("V-200", inletStream);
separator.setInternalDiameter(2.5, "m");
separator.setLength(12.0, "m");
separator.run();

// Oil retention time (from NLL to NIL)
double oilRetention = separator.calcOilRetentionTime();  // minutes

// Water retention time (from NIL to vessel bottom)
double waterRetention = separator.calcWaterRetentionTime();  // minutes

// Interface settling time
double settlingTime = separator.calcInterfaceSettlingTime();  // minutes
```

---

## Gas Scrubber Design Parameters

Gas scrubbers (vertical separators) focus on gas phase quality with minimal liquid holdup.

### Key Parameters

| Parameter | Method | Description |
|-----------|--------|-------------|
| Internal Diameter | `setInternalDiameter(value, unit)` | Vessel ID |
| Height | `setLength(value, unit)` | Tan-to-tan height |
| K-value | `calcKValueAtHLL()` | Souders-Brown coefficient |

### Automatic Constraint Configuration

Gas scrubbers automatically configure for K-value only constraints:

```java
// GasScrubber constructor automatically calls useGasScrubberConstraints()
GasScrubber scrubber = new GasScrubber("Inlet Scrubber", gasStream);

// Only K-value constraint is active
// Droplet cut size, inlet momentum, and retention times are disabled
```

To verify or manually configure:

```java
// Check active constraints
scrubber.getConstraints().forEach((type, constraint) -> {
    System.out.println(type + ": enabled=" + constraint.isEnabled());
});

// Manual configuration if needed
scrubber.useGasScrubberConstraints();  // Only K-value
```

---

## Performance Constraints

NeqSim separators include a constraint system for performance monitoring and capacity analysis. Constraints are based on industry standards including **Equinor TR3500** and **API 12J**.

> **⚠️ Important:** All separator constraints are **disabled by default** for backward compatibility with the optimizer. Use the constraint selection methods (`useEquinorConstraints()`, `useAPIConstraints()`, `useAllConstraints()`, or `enableConstraints()`) to enable constraints for capacity analysis. The optimizer automatically falls back to traditional capacity methods when no enabled constraints exist.
>
> For detailed information on how the optimizer handles constraints, see [Capacity Constraint Framework - Constraints Disabled by Default](../CAPACITY_CONSTRAINT_FRAMEWORK#important-constraints-disabled-by-default).

### Available Constraints

| Constraint Type | Parameter | Limit | Standard Reference |
|-----------------|-----------|-------|-------------------|
| K-value (Souders-Brown) | Gas load factor at HLL | < 0.15 m/s | Equinor TR3500, API 12J |
| Droplet Cut Size | Minimum removed droplet | < 150 µm | Industry practice |
| Inlet Momentum Flux | ρv² at inlet nozzle | < 16,000 Pa | Equinor revamp criteria |
| Oil Retention Time | Oil phase residence | ≥ 3 min | API 12J |
| Water Retention Time | Water phase residence | ≥ 3 min | API 12J |

### Performance Calculation Methods

```java
// After running separator
separator.run();

// Calculate performance parameters
double kValue = separator.calcKValueAtHLL();           // m/s
double dropletSize = separator.calcDropletCutSizeAtHLL(); // µm  
double momentum = separator.calcInletMomentumFlux();   // Pa
double oilRetention = separator.calcOilRetentionTime();    // min
double waterRetention = separator.calcWaterRetentionTime(); // min

// Check against limits
boolean kOk = separator.isKValueWithinLimit();
boolean dropletOk = separator.isDropletCutSizeWithinLimit();
boolean momentumOk = separator.isInletMomentumWithinLimit();
boolean oilTimeOk = separator.isOilRetentionTimeAboveMinimum();
boolean waterTimeOk = separator.isWaterRetentionTimeAboveMinimum();

// Check all active constraints
boolean allOk = separator.isWithinAllLimits();
```

### Performance Summary

Get a comprehensive performance summary with all metrics:

```java
Map<String, Object> summary = separator.getPerformanceSummary();

// Summary includes:
// - kValue, kValueLimit, kValueWithinLimit
// - dropletCutSize, dropletCutSizeLimit, dropletCutSizeWithinLimit
// - inletMomentum, inletMomentumLimit, inletMomentumWithinLimit
// - oilRetentionTime, minOilRetentionTime, oilRetentionTimeOk
// - waterRetentionTime, minWaterRetentionTime, waterRetentionTimeOk
// - allConstraintsMet
```

### Constraint Customization

Adjust constraint limits for specific project requirements:

```java
// Set custom K-value limit (e.g., for high-pressure service)
separator.setKValueLimit(0.12);  // more conservative than default 0.15

// Set custom droplet cut size (e.g., for mist eliminator specification)
separator.setDropletCutSizeLimit(100.0);  // µm, stricter than 150 µm

// Set custom inlet momentum (e.g., for retrofit assessment)
separator.setInletMomentumLimit(12000.0);  // Pa, more conservative

// Set custom retention times (e.g., for emulsion handling)
separator.setMinOilRetentionTime(5.0);  // 5 minutes
separator.setMinWaterRetentionTime(5.0);  // 5 minutes
```

---

## Constraint Selection Methods

Different separator types and applications require different constraint sets. NeqSim provides methods to select appropriate constraints.

### Pre-Configured Constraint Sets

| Method | Constraints Enabled | Use Case |
|--------|---------------------|----------|
| `useAllConstraints()` | All 5 constraints | Full process separator analysis |
| `useEquinorConstraints()` | K-value, Droplet, Momentum, Oil RT, Water RT | Equinor TR3500 compliance |
| `useAPIConstraints()` | K-value, Oil RT, Water RT | API 12J compliance |
| `useGasScrubberConstraints()` | K-value only | Gas scrubbers, inlet separators |
| `useGasCapacityConstraints()` | K-value, Droplet, Momentum | Gas-focused analysis |
| `useLiquidCapacityConstraints()` | Oil RT, Water RT | Liquid-focused analysis |

### Usage Examples

```java
// Scenario 1: Full process separator per Equinor standards
Separator hpSeparator = new Separator("HP Separator", feed);
hpSeparator.useEquinorConstraints();  // All 5 constraints per TR3500
hpSeparator.run();
boolean compliant = hpSeparator.isWithinAllLimits();

// Scenario 2: API 12J compliance check
ThreePhaseSeparator prodSep = new ThreePhaseSeparator("Production Sep", feed);
prodSep.useAPIConstraints();  // K-value + retention times per API 12J
prodSep.run();

// Scenario 3: Gas scrubber (automatic)
GasScrubber scrubber = new GasScrubber("Inlet Scrubber", gasStream);
// K-value only is already configured by constructor
scrubber.run();
double kValue = scrubber.calcKValueAtHLL();

// Scenario 4: Custom constraint selection
Separator testSep = new Separator("Test Sep", feed);
testSep.useConstraints(
    StandardConstraintType.SEPARATOR_K_VALUE,
    StandardConstraintType.SEPARATOR_INLET_MOMENTUM
);
// Only K-value and inlet momentum are checked
```

### Manual Constraint Control

For fine-grained control over individual constraints:

```java
// Disable specific constraints
separator.useAllConstraints();  // Start with all
CapacityConstraint momentumConstraint = 
    separator.getConstraints().get(StandardConstraintType.SEPARATOR_INLET_MOMENTUM);
momentumConstraint.setEnabled(false);  // Disable momentum check

// Enable/disable via Map iteration
separator.getConstraints().forEach((type, constraint) -> {
    if (type.toString().contains("RETENTION")) {
        constraint.setEnabled(false);  // Disable all retention time constraints
    }
});
```

---

## Separator Sizing

### Vertical Separator

```java
// Set dimensions
separator.setInternalDiameter(2.0, "m");
separator.setLiquidVolume(10.0, "m3");

// Or specify residence time
separator.setLiquidResidenceTime(120.0, "sec");
```

### Horizontal Separator

```java
separator.setSeparatorType("horizontal");
separator.setLength(10.0, "m");
separator.setInternalDiameter(2.5, "m");
```

---

## Dynamic Simulation

```java
// Enable dynamic mode
separator.setCalculateSteadyState(false);

// Set initial conditions
separator.setLiquidLevel(0.5);  // 50% level

// Run transient
for (int i = 0; i < 100; i++) {
    separator.runTransient();
    
    double level = separator.getLiquidLevel();
    double pressure = separator.getPressure();
}
```

---

## Separation Efficiency

For basic efficiency controls:

```java
// Set droplet removal efficiency
separator.setGasCarryUnderFraction(0.001);  // 0.1% liquid in gas
separator.setLiquidCarryOverFraction(0.0001); // 0.01% gas in liquid
```

For detailed phase-to-phase entrainment specification, see the [Entrainment](#entrainment) section above.

---

## Example: HP/LP Separation Train

```java
// HP Separator at 50 bar
Separator hpSep = new Separator("HP Sep", feedStream);
process.add(hpSep);

// Letdown valve
ThrottlingValve lpValve = new ThrottlingValve("LP Valve", hpSep.getLiquidOutStream());
lpValve.setOutletPressure(5.0, "bara");
process.add(lpValve);

// LP Separator at 5 bar
Separator lpSep = new Separator("LP Sep", lpValve.getOutletStream());
process.add(lpSep);

// Run process
process.run();

// Total gas production
double hpGas = hpSep.getGasOutStream().getFlowRate("MSm3/day");
double lpGas = lpSep.getGasOutStream().getFlowRate("MSm3/day");
double totalGas = hpGas + lpGas;
```

---

## Gas Load Factor (K-Factor)

The gas load factor (Souders-Brown coefficient) is used for separator sizing and capacity analysis:

```java
// Get current gas load factor
double kFactor = separator.getGasLoadFactor();

// Set design K-factor for sizing
separator.setDesignGasLoadFactor(0.10);  // Typical for horizontal separator
separator.setDesignGasLoadFactor(0.07);  // Typical for vertical scrubber
```

### Dry Gas Handling

For dry gas or single-phase systems (e.g., gas scrubbers with no liquid), the gas load factor calculation uses a default liquid density of **1000 kg/m³**. This allows capacity calculations to work correctly even when no liquid phase is present:

```java
// Dry gas scrubber - liquid density defaults to 1000 kg/m3
GasScrubber scrubber = new GasScrubber("Inlet Scrubber", dryGasStream);
scrubber.run();
double kFactor = scrubber.getGasLoadFactor();  // Uses 1000 kg/m3 for liquid reference
```

---

## Auto-Sizing

Separators implement the `AutoSizeable` interface for automatic sizing based on flow conditions:

```java
// Auto-size with 20% safety factor (default)
separator.autoSize();

// Auto-size with custom safety factor
separator.autoSize(1.3);  // 30% margin

// Auto-size per company standards
separator.autoSize("Equinor", "TR2000");

// Get sizing report
System.out.println(separator.getSizingReport());
System.out.println(separator.getSizingReportJson());
```

---

## Constraint Utilization and Bottleneck Analysis

Constraints integrate with NeqSim's bottleneck analysis framework for capacity assessment.

### Utilization Calculation

Each constraint calculates utilization as a percentage of its limit:

```java
separator.run();

// Get constraint utilizations
Map<StandardConstraintType, CapacityConstraint> constraints = separator.getConstraints();

for (Map.Entry<StandardConstraintType, CapacityConstraint> entry : constraints.entrySet()) {
    CapacityConstraint c = entry.getValue();
    if (c.isEnabled()) {
        System.out.printf("%s: %.1f%% utilization%n", 
            entry.getKey(), c.getUtilizationPercentage());
    }
}

// Example output:
// SEPARATOR_K_VALUE: 78.5% utilization
// SEPARATOR_DROPLET_CUTSIZE: 92.3% utilization
// SEPARATOR_INLET_MOMENTUM: 45.2% utilization
// SEPARATOR_OIL_RETENTION_TIME: 110.5% utilization (over limit!)
// SEPARATOR_WATER_RETENTION_TIME: 85.0% utilization
```

### Identifying Bottlenecks

```java
// Find limiting constraint
StandardConstraintType bottleneck = null;
double maxUtilization = 0;

for (Map.Entry<StandardConstraintType, CapacityConstraint> entry : 
        separator.getConstraints().entrySet()) {
    CapacityConstraint c = entry.getValue();
    if (c.isEnabled() && c.getUtilizationPercentage() > maxUtilization) {
        maxUtilization = c.getUtilizationPercentage();
        bottleneck = entry.getKey();
    }
}

System.out.println("Bottleneck: " + bottleneck + " at " + maxUtilization + "%");
```

---

## JSON Output with Performance Metrics

The `SeparatorResponse` class provides comprehensive JSON output including performance metrics:

```java
// Get JSON report
String json = separator.toJson();
```

Example JSON output:

```json
{
  "name": "HP Separator",
  "type": "Separator",
  "internalDiameter_m": 2.5,
  "length_m": 10.0,
  "pressure_bara": 50.0,
  "temperature_C": 45.0,
  "gasFlowRate_Sm3_hr": 150000.0,
  "liquidFlowRate_m3_hr": 25.0,
  "performanceMetrics": {
    "kValue_m_s": 0.098,
    "kValueLimit_m_s": 0.15,
    "kValueWithinLimit": true,
    "dropletCutSize_um": 125.3,
    "dropletCutSizeLimit_um": 150.0,
    "dropletCutSizeWithinLimit": true,
    "inletMomentum_Pa": 8500.0,
    "inletMomentumLimit_Pa": 16000.0,
    "inletMomentumWithinLimit": true,
    "oilRetentionTime_min": 4.2,
    "minOilRetentionTime_min": 3.0,
    "oilRetentionTimeOk": true,
    "waterRetentionTime_min": 3.8,
    "minWaterRetentionTime_min": 3.0,
    "waterRetentionTimeOk": true,
    "allConstraintsMet": true
  }
}
```

---

## Design Standards Reference

### Equinor TR3500 Requirements

| Parameter | Requirement | Description |
|-----------|-------------|-------------|
| K-value | ≤ 0.15 m/s | Souders-Brown at HLL |
| Droplet cut size | ≤ 150 µm | At HLL conditions |
| Inlet momentum | ≤ 16,000 Pa | For revamp assessment |
| Retention time | ≥ 3 min | For oil and water phases |

### API 12J Guidelines

| Parameter | Requirement | Description |
|-----------|-------------|-------------|
| K-value | ≤ 0.107 m/s | More conservative for oilfield use |
| Liquid retention | ≥ 3 min | Oil and water phases |
| L/D ratio | 3:1 to 5:1 | Horizontal separator design |

### Application by Equipment Type

| Equipment | Primary Constraints | Secondary Constraints |
|-----------|--------------------|-----------------------|
| Two-Phase Separator | K-value, Oil RT | Droplet, Momentum |
| Three-Phase Separator | K-value, Oil RT, Water RT | Droplet, Momentum |
| Gas Scrubber | K-value only | - |
| Inlet Separator | K-value, Momentum | - |
| Test Separator | Oil RT, Water RT | K-value |

---

## Complete Example: Separator Design and Constraint Check

```java
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create feed
SystemSrkEos fluid = new SystemSrkEos(273.15 + 45, 50.0);
fluid.addComponent("methane", 100.0);
fluid.addComponent("n-heptane", 30.0);
fluid.addComponent("water", 10.0);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(100000, "kg/hr");
feed.run();

// Create separator with pre-designed dimensions
ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Sep", feed);
separator.setFromExistingDesign(
    2.5,    // ID [m]
    10.0,   // Length [m]
    0.70,   // HLL fraction
    0.50,   // NLL fraction
    8.0,    // Liquid Leff [m]
    9.0     // Gas Leff [m]
);

// Apply Equinor TR3500 constraints
separator.useEquinorConstraints();

// Customize retention time for heavy crude
separator.setMinOilRetentionTime(5.0);  // 5 min for emulsion
separator.setMinWaterRetentionTime(5.0);

// Run simulation
separator.run();

// Check performance
System.out.println("=== Separator Performance ===");
System.out.printf("K-value: %.3f m/s (limit: %.3f) - %s%n",
    separator.calcKValueAtHLL(),
    separator.getKValueLimit(),
    separator.isKValueWithinLimit() ? "OK" : "EXCEEDED");

System.out.printf("Droplet cut size: %.1f µm (limit: %.1f) - %s%n",
    separator.calcDropletCutSizeAtHLL(),
    separator.getDropletCutSizeLimit(),
    separator.isDropletCutSizeWithinLimit() ? "OK" : "EXCEEDED");

System.out.printf("Inlet momentum: %.0f Pa (limit: %.0f) - %s%n",
    separator.calcInletMomentumFlux(),
    separator.getInletMomentumLimit(),
    separator.isInletMomentumWithinLimit() ? "OK" : "EXCEEDED");

System.out.printf("Oil retention: %.1f min (min: %.1f) - %s%n",
    separator.calcOilRetentionTime(),
    separator.getMinOilRetentionTime(),
    separator.isOilRetentionTimeAboveMinimum() ? "OK" : "INSUFFICIENT");

System.out.printf("Water retention: %.1f min (min: %.1f) - %s%n",
    separator.calcWaterRetentionTime(),
    separator.getMinWaterRetentionTime(),
    separator.isWaterRetentionTimeAboveMinimum() ? "OK" : "INSUFFICIENT");

System.out.println("\nAll constraints met: " + separator.isWithinAllLimits());

// Get full JSON report
System.out.println("\n" + separator.toJson());
```

---

## Related Documentation

- [Process Package](../) - Package overview
- [Streams](streams) - Stream handling
- [Design Framework](../DESIGN_FRAMEWORK) - Auto-sizing and design specifications
- [Bottleneck Analysis](../../wiki/bottleneck_analysis) - Capacity utilization
