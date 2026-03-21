---
name: neqsim-api-patterns
description: "NeqSim API patterns and code recipes. USE WHEN: writing Java or Python code that uses NeqSim for thermodynamic calculations, process simulation, or property retrieval. Covers EOS selection, fluid creation, flash calculations, property access, equipment patterns, and unit conventions."
---

# NeqSim API Patterns

Copy-paste reference for common NeqSim operations. All Java code must be Java 8 compatible.

## EOS Selection Guide

| Fluid Type | Java Class | Mixing Rule |
|-----------|-----------|-------------|
| Dry/lean gas, simple HC | `SystemSrkEos` | `"classic"` |
| General hydrocarbons, oil | `SystemPrEos` | `"classic"` |
| Water, MEG, methanol, polar | `SystemSrkCPAstatoil` | `10` (numeric) |
| Custody transfer, fiscal metering | `SystemGERG2008Eos` | (none needed) |
| Electrolyte systems | `SystemElectrolyteCPAstatoil` | `10` |
| Volume-corrected SRK | `SystemSrkEosvolcor` | `"classic"` |

## Fluid Creation (Required Sequence)

```java
// 1. Create: temperature in KELVIN, pressure in bara
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);

// 2. Add components (name, mole fraction)
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);

// 3. MANDATORY: set mixing rule — NEVER skip
fluid.setMixingRule("classic");

// 4. Optional: multi-phase check for water/heavy systems
fluid.setMultiPhaseCheck(true);
```

## Oil Characterization (C7+ Fractions)

```java
fluid.addTBPfraction("C7", 0.05, 92.0 / 1000, 0.727);   // name, moleFrac, MW_kg/mol, density
fluid.addTBPfraction("C8", 0.04, 104.0 / 1000, 0.749);
fluid.addPlusFraction("C20+", 0.02, 350.0 / 1000, 0.88);
fluid.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(6);
fluid.getCharacterization().characterisePlusFraction();
```

## Flash Calculations and Property Retrieval

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// CRITICAL: call initProperties() AFTER flash, BEFORE reading properties
// init(3) alone does NOT initialize transport properties — they return ZERO
fluid.initProperties();

// Bulk properties
double density = fluid.getDensity("kg/m3");
double molarMass = fluid.getMolarMass("kg/mol");
double Z = fluid.getZ();

// Phase properties
double gasDensity = fluid.getPhase("gas").getDensity("kg/m3");
double gasViscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double gasThermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
double gasCp = fluid.getPhase("gas").getCp("J/kgK");

// Phase checks
int numPhases = fluid.getNumberOfPhases();
boolean hasGas = fluid.hasPhaseType("gas");
```

### Other Flash Types

```java
ops.PHflash(enthalpy);                  // Pressure-Enthalpy
ops.PSflash(entropy);                   // Pressure-Entropy
ops.dewPointTemperatureFlash();          // Dew point temperature
ops.bubblePointPressureFlash();          // Bubble point pressure
ops.hydrateFormationTemperature();       // Hydrate T at given P
ops.calcPTphaseEnvelope();              // Phase envelope
```

## Unit Conventions

| Quantity | Constructor default | Setter pattern |
|----------|-------------------|----------------|
| Temperature | **Kelvin** | `setTemperature(25.0, "C")` |
| Pressure | **bara** | `setPressure(50.0, "bara")` |
| Flow rate | — | `setFlowRate(50000.0, "kg/hr")` |
| Getting temp | Returns **Kelvin** | `getTemperature() - 273.15` for °C |

## Process Equipment Patterns

### Stream

```java
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
feed.setPressure(50.0, "bara");
feed.setTemperature(30.0, "C");
```

### Separator

```java
Separator sep = new Separator("HP Sep", feedStream);
Stream gasOut = sep.getGasOutStream();
Stream liqOut = sep.getLiquidOutStream();
```

### Compressor

```java
Compressor comp = new Compressor("Comp", gasStream);
comp.setOutletPressure(120.0);
// comp.setIsentropicEfficiency(0.75);
Stream out = comp.getOutletStream();
// After run: comp.getPower("kW")
```

### Cooler / Heater

```java
Cooler cooler = new Cooler("Cooler", hotStream);
cooler.setOutTemperature(273.15 + 30.0);
Stream out = cooler.getOutletStream();
// After run: cooler.getDuty() — Watts
```

### Valve

```java
ThrottlingValve valve = new ThrottlingValve("JT Valve", stream);
valve.setOutletPressure(20.0);
Stream out = valve.getOutletStream();
```

### Mixer

```java
Mixer mixer = new Mixer("Mix");
mixer.addStream(stream1);
mixer.addStream(stream2);
Stream out = mixer.getOutletStream();
```

### Pipeline

```java
AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", stream);
pipe.setLength(50000.0);   // meters
pipe.setDiameter(0.508);   // meters (20 inch)
Stream out = pipe.getOutletStream();
```

### Recycle and Adjuster

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(outletStream);
// Add to process after the equipment loop

Adjuster adjuster = new Adjuster("Adj");
adjuster.setAdjustedVariable(equipment, "methodName");
adjuster.setTargetVariable(stream, "methodName", targetValue);
```

## ProcessSystem Assembly

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.add(cooler);
process.run();  // Run ONCE after adding all equipment
```

## Key Rules

- **Clone fluids** before branching: `fluid.clone()` to avoid shared-state bugs
- Equipment constructors take `(String name, StreamInterface inlet)`
- Connect equipment via outlet streams — don't create separate streams
- Add equipment to `ProcessSystem` in topological order
- Call `process.run()` only ONCE after building the entire flowsheet

## Documentation Code Verification

When writing code examples for documentation (markdown guides, cookbook recipes, tutorials):

1. **Read the source class first** — verify every method signature, constructor, inner class
2. **Write a JUnit test** that calls every documented API method (append to `DocExamplesCompilationTest.java`)
3. **Run the test** and confirm it passes before publishing the doc
4. **Common pitfalls**:
   - Plus fraction names: use `"C20"` not `"C20+"` (the `+` character breaks parsing)
   - Set mixing rule BEFORE calling `characterisePlusFraction()`
   - `getUnit("name")` not `getUnitOperation("name")`
   - `setDepreciationYears` takes `double`, not `int`
   - Risk thresholds: always read source for actual comparison logic (subcooling direction, enum ordering)
