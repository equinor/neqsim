---
title: Streams
description: Comprehensive documentation for process streams in NeqSim.
---

# Streams

Comprehensive documentation for process streams in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Stream Architecture](#stream-architecture)
- [Stream Class](#stream-class)
- [Stream Specifications](#stream-specifications)
- [Stream Properties](#stream-properties)
- [Phase Handling](#phase-handling)
- [Gas Quality Properties](#gas-quality-properties)
- [Virtual Streams](#virtual-streams)
- [NeqStream](#neqstream)
- [Energy Streams](#energy-streams)
- [Cloning and State Management](#cloning-and-state-management)
- [Transient Operations](#transient-operations)
- [Examples](#examples)
- [Advanced Topics](#advanced-topics)

---

## Overview

**Location:** `neqsim.process.equipment.stream`

Streams are the fundamental connections between process equipment in NeqSim, carrying material and energy through process flowsheets. They encapsulate thermodynamic fluid systems with flow conditions and provide methods for flash calculations, property retrieval, and gas quality analysis.

### Class Hierarchy

```
ProcessEquipmentBaseClass
    └── Stream (implements StreamInterface)
            └── NeqStream

ProcessEquipmentBaseClass
    └── VirtualStream

java.io.Serializable
    └── EnergyStream
```

### Available Classes

| Class | Description | Use Case |
|-------|-------------|----------|
| `Stream` | Standard process stream with full thermodynamic calculations | General material flows |
| `StreamInterface` | Interface defining stream contract | Type declarations and polymorphism |
| `NeqStream` | Stream without flash (uses existing phase split) | When phase equilibrium is known |
| `VirtualStream` | Reference stream with property overrides | Branch flows, what-if scenarios |
| `EnergyStream` | Heat/work duty carrier | Heat exchanger duties, compressor work |

---

## Stream Architecture

### Internal Structure

A Stream contains:
- **thermoSystem**: The underlying `SystemInterface` fluid object
- **stream**: Optional reference to source stream (for linked streams)
- **specification**: Flash type specification (TP, PH, dewP, etc.)
- **lastState**: Cached results for recalculation optimization

### Object Ownership

```java
// IMPORTANT: Stream uses the fluid object directly (not cloned)
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 1.0);

// The stream references the same fluid object
Stream stream = new Stream("Feed", fluid);

// To create independent streams, clone explicitly
Stream independent = new Stream("Independent", fluid.clone());
```

### Linked Streams

Streams can reference other streams:

```java
// Source stream
Stream source = new Stream("Source", fluid);
source.run();

// Linked stream (shares fluid with source)
Stream linked = new Stream("Linked", source);

// When source changes, linked sees the changes after run()
source.setTemperature(350.0, "K");
source.run();
linked.run();  // Uses updated source properties
```

---

## Stream Class

### Constructors

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;

// 1. Create with name only (fluid set later)
Stream emptyStream = new Stream("Empty");

// 2. Create from fluid system (uses fluid directly, not cloned)
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.setMixingRule("classic");
Stream feedStream = new Stream("Feed", fluid);

// 3. Create from another stream (linked reference)
Stream linkedStream = new Stream("Linked", feedStream);
```

### Setting Process Conditions

```java
// Temperature (various units)
feed.setTemperature(300.0, "K");        // Kelvin
feed.setTemperature(25.0, "C");         // Celsius
feed.setTemperature(77.0, "F");         // Fahrenheit

// Pressure (various units)
feed.setPressure(50.0, "bara");         // Bar absolute
feed.setPressure(725.0, "psia");        // PSI absolute
feed.setPressure(5.0, "MPa");           // Megapascal

// Flow rate (various units)
feed.setFlowRate(10000.0, "kg/hr");     // Mass flow
feed.setFlowRate(500.0, "kmol/hr");     // Molar flow
feed.setFlowRate(1000000.0, "Sm3/day"); // Standard volume (gas)
feed.setFlowRate(100.0, "m3/hr");       // Actual volume

// IMPORTANT: Always run() after setting conditions
feed.run();
```

### Setting Fluid/Composition

```java
// Replace entire fluid
feed.setFluid(newFluidSystem);
feed.setThermoSystem(newFluidSystem);

// Set from specific phase of another system
feed.setThermoSystemFromPhase(otherSystem, "gas");      // Gas phase only
feed.setThermoSystemFromPhase(otherSystem, "oil");      // Oil phase only
feed.setThermoSystemFromPhase(otherSystem, "aqueous");  // Water phase only
feed.setThermoSystemFromPhase(otherSystem, "liquid");   // All liquid phases

// Create empty stream from template
feed.setEmptyThermoSystem(templateSystem);
```

---

## Stream Specifications

The stream specification controls how flash calculations are performed.

### Available Specifications

| Specification | Description | When to Use |
|---------------|-------------|-------------|
| `"TP"` | Temperature-Pressure flash (default) | Standard conditions |
| `"PH"` | Pressure-Enthalpy flash | After isenthalpic processes |
| `"dewP"` | Dew point temperature at given P | Condensation studies |
| `"dewT"` | Dew point pressure at given T | Dew point analysis |
| `"bubP"` | Bubble point temperature at given P | Evaporation studies |
| `"bubT"` | Bubble point pressure at given T | Bubble point analysis |
| `"gas quality"` | Constant phase fraction flash | Fixed vapor fraction |

### Using Specifications

```java
// Default TP flash
Stream stream = new Stream("Process", fluid);
stream.run();  // Performs TP flash

// Dew point calculation
stream.setSpecification("dewP");
stream.run();  // Calculates dew point temperature at current pressure

// Bubble point calculation
stream.setSpecification("bubP");
stream.run();  // Calculates bubble point temperature at current pressure

// Gas quality specification
stream.setSpecification("gas quality");
stream.setGasQuality(0.5);  // 50% vapor fraction
stream.run();  // Calculates temperature for specified vapor fraction
```

---

## Stream Properties

### Thermodynamic Properties

```java
stream.run();  // Ensure stream is calculated

// Temperature
double tempK = stream.getTemperature();          // Kelvin (default)
double tempC = stream.getTemperature("C");       // Celsius
double tempF = stream.getTemperature("F");       // Fahrenheit

// Pressure
double pressPa = stream.getPressure();           // Pascal (default)
double pressBara = stream.getPressure("bara");   // Bar absolute
double pressPsia = stream.getPressure("psia");   // PSI absolute

// Flow rates
double massFlow = stream.getFlowRate("kg/hr");
double molarFlow = stream.getFlowRate("kmol/hr");
double molarRate = stream.getMolarRate();        // Total moles
double volFlow = stream.getFlowRate("m3/hr");
double stdVolFlow = stream.getFlowRate("Sm3/day");
```

### Flow Rate Unit Reference

| Unit | Description | Basis |
|------|-------------|-------|
| `"kg/sec"` | Kilograms per second | Mass |
| `"kg/min"` | Kilograms per minute | Mass |
| `"kg/hr"` | Kilograms per hour | Mass |
| `"kg/day"` | Kilograms per day | Mass |
| `"kmol/hr"` | Kilomoles per hour | Molar |
| `"mole/sec"` | Moles per second | Molar |
| `"mole/min"` | Moles per minute | Molar |
| `"mole/hr"` | Moles per hour | Molar |
| `"m3/sec"` | Actual m³/second | Volume |
| `"m3/min"` | Actual m³/minute | Volume |
| `"m3/hr"` | Actual m³/hour | Volume |
| `"Sm3/sec"` | Standard m³/second | Std Volume |
| `"Sm3/hr"` | Standard m³/hour | Std Volume |
| `"Sm3/day"` | Standard m³/day | Std Volume |
| `"MSm3/day"` | Million Sm³/day | Std Volume |
| `"barrel/day"` | Oil barrels/day | Volume |

### Accessing the Fluid System

```java
// Get fluid object for detailed properties
SystemInterface fluid = stream.getFluid();
// or equivalently:
SystemInterface fluid = stream.getThermoSystem();

// Molecular weight
double mw = fluid.getMolarMass("kg/kmol");

// Enthalpy
double enthalpy = fluid.getEnthalpy("kJ/kg");

// Entropy
double entropy = fluid.getEntropy("kJ/kgK");

// Density
double density = fluid.getDensity("kg/m3");

// Composition
double[] moleFractions = fluid.getMolarComposition();
double methaneFrac = fluid.getComponent("methane").getz();
```

---

## Phase Handling

### Checking Phase Presence

```java
SystemInterface fluid = stream.getFluid();

// Check for specific phases
boolean hasGas = fluid.hasPhaseType("gas");
boolean hasOil = fluid.hasPhaseType("oil");
boolean hasAqueous = fluid.hasPhaseType("aqueous");

// Number of phases
int numPhases = fluid.getNumberOfPhases();

// Phase mole fractions (beta)
double gasFraction = fluid.getPhase("gas").getBeta();  // Mole basis
```

### Accessing Phase Properties

```java
if (fluid.hasPhaseType("gas")) {
    PhaseInterface gasPhase = fluid.getPhase("gas");
    
    // Phase properties
    double gasDensity = gasPhase.getDensity("kg/m3");
    double gasViscosity = gasPhase.getViscosity("cP");
    double gasMW = gasPhase.getMolarMass("kg/kmol");
    double gasZ = gasPhase.getZ();  // Compressibility factor
    
    // Component in phase
    double methaneInGas = gasPhase.getComponent("methane").getx();
}

if (fluid.hasPhaseType("oil")) {
    PhaseInterface oilPhase = fluid.getPhase("oil");
    double oilDensity = oilPhase.getDensity("kg/m3");
    double oilViscosity = oilPhase.getViscosity("cP");
}
```

### Creating Streams from Phases

```java
// From separator outlet
Separator separator = new Separator("Sep", feed);
separator.run();

// Gas outlet
Stream gasOut = new Stream("Gas Out");
gasOut.setThermoSystemFromPhase(separator.getFluid(), "gas");
gasOut.run();

// Oil outlet
Stream oilOut = new Stream("Oil Out");
oilOut.setThermoSystemFromPhase(separator.getFluid(), "oil");
oilOut.run();

// All liquids combined
Stream liquidOut = new Stream("Liquid Out");
liquidOut.setThermoSystemFromPhase(separator.getFluid(), "liquid");
liquidOut.run();
```

---

## Gas Quality Properties

NeqSim provides comprehensive gas quality calculations per ISO 6976 and other standards.

### Calorific Values

```java
// Gross Calorific Value (Higher Heating Value)
double gcv = stream.GCV();  // kJ/Sm³ at 0°C, 15.55°C combustion

// GCV with specified reference conditions
double gcvCustom = stream.getGCV("volume", 15.0, 15.0);  // refT=15°C, combT=15°C

// Net Calorific Value (Lower Heating Value)
double lcv = stream.LCV();  // kJ/Sm³
```

### Wobbe Index

```java
// Wobbe Index (gas interchangeability measure)
double wi = stream.getWI("volume", 15.0, 15.0);  // kJ/Sm³
```

### ISO 6976 Standard Calculations

```java
// Get full ISO 6976 results
Standard_ISO6976 iso = stream.getISO6976("volume", 15.0, 15.0);
iso.calculate();

double gcv = iso.getValue("SuperiorCalorificValue");
double lcv = iso.getValue("InferiorCalorificValue");
double wobbe = iso.getValue("SuperiorWobbeIndex");
double relDensity = iso.getValue("RelativeDensity");
double compressibility = iso.getValue("CompressionFactor");
```

### Dew Points

```java
// Hydrocarbon dew point at specified pressure
double hcDewPoint = stream.getHydrocarbonDewPoint("C", 70.0, "bara");

// Hydrate equilibrium temperature
double hydrateTemp = stream.getHydrateEquilibriumTemperature();  // K

// Solid formation temperature
double freezeTemp = stream.getSolidFormationTemperature("wax");
```

### Phase Envelope Points

```java
// Cricondentherm (maximum temperature for two-phase)
double cctTemp = stream.CCT("C");      // Temperature
double cctPres = stream.CCT("bara");   // Pressure at CCT

// Cricondenbar (maximum pressure for two-phase)
double ccbTemp = stream.CCB("C");      // Temperature at CCB
double ccbPres = stream.CCB("bara");   // Pressure

// Phase envelope visualization
stream.phaseEnvelope();  // Opens plot window
```

### Vapor Pressure

```java
// True Vapor Pressure at reference temperature
double tvp = stream.TVP(37.8, "C");  // bara at 100°F
double tvpPsia = stream.getTVP(37.8, "C", "psia");

// Reid Vapor Pressure (ASTM D6377)
double rvp = stream.getRVP(37.8, "C", "psia");
double rvpMethod = stream.getRVP(37.8, "C", "psia", "VPCR4");
```

---

## Virtual Streams

VirtualStream creates a modified copy of a reference stream with overridden properties.

### Purpose

- Create branch streams with different flow rates
- Model "what-if" scenarios
- Avoid direct fluid manipulation

### Usage

```java
import neqsim.process.equipment.stream.VirtualStream;

// Reference stream
Stream mainFlow = new Stream("Main", fluid);
mainFlow.setFlowRate(10000.0, "kg/hr");
mainFlow.run();

// Virtual stream with modified flow
VirtualStream branch = new VirtualStream("Branch", mainFlow);
branch.setFlowRate(2000.0, "kg/hr");  // Override flow
branch.run();

// Virtual stream with modified conditions
VirtualStream heated = new VirtualStream("Heated", mainFlow);
heated.setTemperature(350.0, "K");    // Override temperature
heated.setFlowRate(3000.0, "kg/hr");  // Override flow
heated.run();

// Virtual stream with modified composition
VirtualStream altered = new VirtualStream("Altered", mainFlow);
double[] newComp = {0.95, 0.03, 0.02};  // New mole fractions
altered.setComposition(newComp, "mole");
altered.run();

// Get output stream from virtual
StreamInterface outputStream = altered.getOutletStream();
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setReferenceStream(stream)` | Set the source stream |
| `setFlowRate(value, unit)` | Override flow rate |
| `setTemperature(value, unit)` | Override temperature |
| `setPressure(value, unit)` | Override pressure |
| `setComposition(array, unit)` | Override composition |
| `getOutletStream()` | Get the modified stream |

---

## NeqStream

NeqStream is a specialized stream that skips flash calculations, using the existing phase distribution.

### When to Use

- Phase equilibrium is already calculated
- Performance optimization (skip redundant flashes)
- Maintaining exact phase splits from previous calculations

### Behavior Difference

```java
// Standard Stream: performs TP flash
Stream standard = new Stream("Standard", fluid);
standard.run();  // Calculates new phase equilibrium

// NeqStream: uses existing phases, just initializes properties
NeqStream neq = new NeqStream("NeqStream", fluid);
neq.run();  // Skips flash, uses existing x, y, beta
```

### Usage Example

```java
import neqsim.process.equipment.stream.NeqStream;

// After separator has calculated phases
Separator sep = new Separator("Sep", feed);
sep.run();

// Use NeqStream to preserve exact phase split
NeqStream gasStream = new NeqStream("Gas", sep.getGasOutStream());
gasStream.run();  // No reflash, preserves separator results
```

---

## Energy Streams

EnergyStream carries heat or work duty between equipment.

### Creating Energy Streams

```java
import neqsim.process.equipment.stream.EnergyStream;

// Create energy stream
EnergyStream heatDuty = new EnergyStream("Heater Duty");
heatDuty.setDuty(1000000.0);  // Watts

// Get duty
double duty = heatDuty.getDuty();  // Watts
```

### Connecting to Equipment

```java
// Heater with energy stream
Heater heater = new Heater("Heater", feed);
heater.setOutletTemperature(350.0, "K");
heater.run();

// Energy stream gets duty from heater
EnergyStream heaterPower = new EnergyStream("Heater Power");
heaterPower.setDuty(heater.getDuty());

// Connect to heat source
HeatExchanger hx = new HeatExchanger("HX");
hx.setEnergyStream(heaterPower);
```

---

## Cloning and State Management

### Cloning Streams

```java
// Clone with same name (returns copy)
Stream original = new Stream("Feed", fluid);
original.run();

Stream copy = original.clone();

// Clone with new name
Stream namedCopy = original.clone("Feed Copy");

// Clones are independent
copy.setFlowRate(500.0, "kg/hr");
copy.run();
// Original unchanged
```

### State Caching

Streams cache their last calculated state for optimization:

```java
// Check if recalculation is needed
if (stream.needRecalculation()) {
    stream.run();  // Conditions changed, recalculate
}

// Cached values used internally:
// - lastTemperature
// - lastPressure
// - lastFlowRate
// - lastComposition
```

---

## Transient Operations

Streams support dynamic simulation with controller integration.

### Running Transient

```java
// Time step in seconds
double dt = 1.0;
UUID calcId = UUID.randomUUID();

// Run transient step
stream.runTransient(dt, calcId);

// Increase simulation time
stream.increaseTime(dt);
```

### With Flow Controller

```java
// Attach controller
ControllerDeviceInterface controller = new PIDController();
controller.setControllerSetPoint(1000.0);  // kg/hr target
stream.setController(controller);

// Transient run adjusts flow via controller
for (int i = 0; i < 100; i++) {
    stream.runTransient(1.0, UUID.randomUUID());
}
```

### Minimum Flow Handling

```java
// Streams below minimum flow are deactivated
if (stream.getFlowRate("kg/hr") < stream.getMinimumFlow()) {
    // Stream runs but marks as inactive
    stream.isActive();  // Returns false
}
```

---

## Examples

### Example 1: Complete Natural Gas Feed Setup

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;

// Natural gas composition
SystemSrkEos gas = new SystemSrkEos(298.15, 70.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.03);
gas.addComponent("i-butane", 0.01);
gas.addComponent("n-butane", 0.015);
gas.addComponent("i-pentane", 0.005);
gas.setMixingRule("classic");

Stream feed = new Stream("Natural Gas Feed", gas);
feed.setFlowRate(10.0, "MSm3/day");  // 10 million Sm³/day
feed.run();

// Report properties
System.out.println("=== Feed Stream Properties ===");
System.out.println("Temperature: " + feed.getTemperature("C") + " °C");
System.out.println("Pressure: " + feed.getPressure("bara") + " bara");
System.out.println("Mass flow: " + feed.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Molar flow: " + feed.getFlowRate("kmol/hr") + " kmol/hr");
System.out.println("Density: " + feed.getFluid().getDensity("kg/m3") + " kg/m³");
System.out.println("MW: " + feed.getFluid().getMolarMass("kg/kmol") + " kg/kmol");

// Gas quality
System.out.println("\n=== Gas Quality ===");
System.out.println("GCV: " + feed.GCV() / 1000.0 + " MJ/Sm³");
System.out.println("LCV: " + feed.LCV() / 1000.0 + " MJ/Sm³");
System.out.println("Wobbe Index: " + feed.getWI("volume", 15.0, 15.0) / 1000.0 + " MJ/Sm³");
System.out.println("HC Dew Point: " + feed.getHydrocarbonDewPoint("C", 70.0, "bara") + " °C");
```

### Example 2: Two-Phase Stream Analysis

```java
// Wellhead mixture
SystemSrkEos wellfluid = new SystemSrkEos(350.0, 150.0);
wellfluid.addComponent("methane", 0.60);
wellfluid.addComponent("ethane", 0.08);
wellfluid.addComponent("propane", 0.05);
wellfluid.addComponent("n-hexane", 0.12);
wellfluid.addComponent("n-decane", 0.10);
wellfluid.addComponent("water", 0.05);
wellfluid.setMixingRule("classic");

Stream wellStream = new Stream("Well Stream", wellfluid);
wellStream.setFlowRate(50000.0, "kg/hr");
wellStream.run();

// Phase analysis
SystemInterface fluid = wellStream.getFluid();
System.out.println("Number of phases: " + fluid.getNumberOfPhases());

if (fluid.hasPhaseType("gas")) {
    double gasRate = fluid.getPhase("gas").getBeta() 
                   * wellStream.getFlowRate("kg/hr");
    System.out.println("Gas rate: " + gasRate + " kg/hr");
    System.out.println("Gas density: " + fluid.getPhase("gas").getDensity("kg/m3") + " kg/m³");
}

if (fluid.hasPhaseType("oil")) {
    double oilRate = fluid.getPhase("oil").getBeta() 
                   * wellStream.getFlowRate("kg/hr");
    System.out.println("Oil rate: " + oilRate + " kg/hr");
    System.out.println("Oil API: " + fluid.getPhase("oil").getPhysicalProperties()
                       .getValue("API_gravity"));
}

if (fluid.hasPhaseType("aqueous")) {
    double waterRate = fluid.getPhase("aqueous").getBeta() 
                     * wellStream.getFlowRate("kg/hr");
    System.out.println("Water rate: " + waterRate + " kg/hr");
}
```

### Example 3: Branch Flows with VirtualStream

```java
// Main pipeline flow
Stream pipeline = new Stream("Pipeline", gas);
pipeline.setFlowRate(100000.0, "kg/hr");
pipeline.run();

// Customer branches (each takes portion of main flow)
VirtualStream customer1 = new VirtualStream("Customer 1", pipeline);
customer1.setFlowRate(30000.0, "kg/hr");
customer1.run();

VirtualStream customer2 = new VirtualStream("Customer 2", pipeline);
customer2.setFlowRate(25000.0, "kg/hr");
customer2.setTemperature(280.0, "K");  // Heated for customer 2
customer2.run();

VirtualStream customer3 = new VirtualStream("Customer 3", pipeline);
customer3.setFlowRate(45000.0, "kg/hr");
customer3.setPressure(40.0, "bara");  // Reduced pressure
customer3.run();

// Verify mass balance
double totalOut = customer1.getOutletStream().getFlowRate("kg/hr")
                + customer2.getOutletStream().getFlowRate("kg/hr")
                + customer3.getOutletStream().getFlowRate("kg/hr");
System.out.println("Pipeline in: " + pipeline.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Total out: " + totalOut + " kg/hr");
```

### Example 4: Dew Point Specification

```java
// Gas stream
Stream gasStream = new Stream("Export Gas", gas);
gasStream.setPressure(70.0, "bara");
gasStream.setFlowRate(5000.0, "kmol/hr");

// Calculate dew point temperature
gasStream.setSpecification("dewP");
gasStream.run();
System.out.println("Dew point at 70 bara: " + gasStream.getTemperature("C") + " °C");

// Calculate bubble point
gasStream.setSpecification("bubP");
gasStream.run();
System.out.println("Bubble point at 70 bara: " + gasStream.getTemperature("C") + " °C");

// Return to normal operation
gasStream.setSpecification("TP");
gasStream.setTemperature(25.0, "C");
gasStream.run();
```

### Example 5: Stream Cloning for Parallel Paths

```java
// Feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
feed.run();

// Clone for train A (50%)
Stream trainA = feed.clone("Train A Feed");
trainA.setFlowRate(5000.0, "kg/hr");
trainA.run();

// Clone for train B (50%)
Stream trainB = feed.clone("Train B Feed");
trainB.setFlowRate(5000.0, "kg/hr");
trainB.run();

// Process independently
Heater heaterA = new Heater("Heater A", trainA);
heaterA.setOutletTemperature(400.0, "K");
heaterA.run();

Heater heaterB = new Heater("Heater B", trainB);
heaterB.setOutletTemperature(380.0, "K");  // Different setpoint
heaterB.run();

System.out.println("Train A outlet T: " + heaterA.getOutletStream().getTemperature("C") + " °C");
System.out.println("Train B outlet T: " + heaterB.getOutletStream().getTemperature("C") + " °C");
```

### Example 6: Stream Reporting

```java
// Get formatted report
ArrayList<String[]> report = stream.getReport();
for (String[] row : report) {
    System.out.println(String.join(" | ", row));
}

// JSON output
String json = stream.toJson();
System.out.println(json);

// Result table
String[][] results = stream.getResultTable();
for (String[] row : results) {
    System.out.println(String.join("\t", row));
}

// Display in NeqSim GUI
stream.displayResult();
```

---

## Advanced Topics

### Flash Type Auto-Selection

For single-component systems from other streams, the stream automatically switches to PH flash to handle phase changes correctly:

```java
// Single component from separator
if (stream != null && thermoSystem.getNumberOfComponents() == 1 
    && getSpecification().equals("TP")) {
    setSpecification("PH");  // Auto-switch for stability
}
```

### Recalculation Optimization

Streams track their last state to avoid unnecessary calculations:

```java
// Implementation checks cached values
if (temperature == lastTemperature 
    && pressure == lastPressure
    && flowRate == lastFlowRate
    && composition == lastComposition) {
    return false;  // No recalculation needed
}
```

### Serialization

Streams are fully serializable for persistence:

```java
// Save process state
ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("process.dat"));
out.writeObject(stream);
out.close();

// Restore process state
ObjectInputStream in = new ObjectInputStream(new FileInputStream("process.dat"));
Stream restored = (Stream) in.readObject();
in.close();
```

### Integration with ProcessSystem

```java
// Add streams to process system
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");

Heater heater = new Heater("Heater", feed);
heater.setOutletTemperature(350.0, "K");

Separator sep = new Separator("Separator", heater.getOutletStream());

process.add(feed);
process.add(heater);
process.add(sep);

// Run entire process
process.run();

// Access any stream
StreamInterface processedFeed = process.getMeasurementDevice("Feed");
```

---

## Related Documentation

- [Equipment Index](./\) - All process equipment
- [Mixers and Splitters](mixers_splitters) - Combining and dividing streams
- [Separators](separators) - Phase separation equipment
- [Heat Exchangers](heat_exchangers) - Thermal processing
- [Process System](../processmodel/process_system) - Flowsheet management
- [Utility Equipment](util/) - Adjusters, recycles, calculators
