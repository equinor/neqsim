---
title: Process Automation API
description: "String-addressable API for reading and writing simulation variables in NeqSim process models. Covers getUnitList, getVariableList, getVariableValue, setVariableValue with dot-notation addresses."
---

# Process Automation API

A stable, string-addressable API for interacting with running NeqSim process simulations.
Variables are accessed through dot-notation paths like `"separator.gasOutStream.temperature"`,
removing the need to navigate Java objects directly.

**Package:** `neqsim.process.automation`

## Key Classes

| Class | Description |
|-------|-------------|
| `ProcessAutomation` | Main facade for reading/writing simulation variables |
| `SimulationVariable` | Descriptor for a single variable (address, type, unit) |

## Quick Start

### Single ProcessSystem

```java
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;

ProcessSystem process = new ProcessSystem();
// ... add equipment and run ...
process.run();

ProcessAutomation auto = new ProcessAutomation(process);

// List all units
List<String> units = auto.getUnitList();

// List variables for a unit
List<SimulationVariable> vars = auto.getVariableList("HP Sep");
for (SimulationVariable v : vars) {
    System.out.println(v.getAddress() + " [" + v.getType() + "] " + v.getDefaultUnit());
}

// Read a value
double temp = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");

// Set an input
auto.setVariableValue("Compressor.outletPressure", 120.0, "bara");

// Re-run to propagate changes
process.run();
```

### Multi-Area ProcessModel

When backed by a `ProcessModel`, addresses use area-qualified syntax:

```java
ProcessModel model = new ProcessModel();
model.add("Separation", separationProcess);
model.add("Compression", compressionProcess);
model.run();

ProcessAutomation auto = new ProcessAutomation(model);

// List all areas
List<String> areas = auto.getAreaList();
// ["Separation", "Compression"]

// List units in a specific area
List<String> sepUnits = auto.getUnitList("Separation");

// Area-qualified addresses
double temp = auto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
auto.setVariableValue("Compression::Compressor.outletPressure", 120.0, "bara");
```

## Convenience Delegates on ProcessSystem

`ProcessSystem` exposes convenience methods that delegate to `ProcessAutomation`:

```java
// These are equivalent:
process.getAutomation().getUnitList();
process.getUnitNames();

process.getAutomation().getVariableList("HP Sep");
process.getVariableList("HP Sep");

process.getAutomation().getVariableValue("HP Sep.gasOutStream.temperature", "C");
process.getVariableValue("HP Sep.gasOutStream.temperature", "C");

process.getAutomation().setVariableValue("Compressor.outletPressure", 120.0, "bara");
process.setVariableValue("Compressor.outletPressure", 120.0, "bara");
```

## Convenience Delegates on ProcessModel

`ProcessModel` has matching delegates with area-qualified addresses:

```java
model.getUnitNames();                                    // all units, area-qualified
model.getAreaNames();                                    // area names
model.getUnitNames("Separation");                        // units in one area
model.getVariableList("Separation::HP Sep");             // variables for a unit
model.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
model.setVariableValue("Compression::Compressor.outletPressure", 120.0, "bara");
```

## Address Format

Variables use dot-notation addresses:

| Pattern | Example | Description |
|---------|---------|-------------|
| `unit.property` | `Compressor.power` | Direct equipment property |
| `unit.port.property` | `HP Sep.gasOutStream.temperature` | Stream port property |
| `Area::unit.property` | `Compression::Compressor.power` | Area-qualified (ProcessModel) |
| `Area::unit.port.property` | `Separation::HP Sep.gasOutStream.temperature` | Full path |

### Stream Port Names

| Equipment | Ports |
|-----------|-------|
| `Stream` | (direct properties only) |
| `Separator` | `gasOutStream`, `liquidOutStream` |
| `ThreePhaseSeparator` | `gasOutStream`, `liquidOutStream`, `waterOutStream` |
| `Compressor` / `Expander` / `Pump` | `outletStream` |
| `Heater` / `Cooler` | `outletStream` |
| `ThrottlingValve` | `outletStream` |
| `HeatExchanger` | `outStream0` (hot out), `outStream1` (cold out) |
| `Mixer` | `outletStream` |
| `Splitter` | `splitStream_0`, `splitStream_1`, ... |
| `DistillationColumn` | `condenserStream`, `reboilerStream` |

### Stream Properties

Each stream port exposes:

| Property | Default Unit | Description |
|----------|-------------|-------------|
| `temperature` | K | Temperature |
| `pressure` | bara | Pressure |
| `flowRate` | kg/hr | Mass flow rate |
| `molarFlowRate` | mole/sec | Molar flow rate |

## SimulationVariable

Each variable exposed by `getVariableList()` returns a `SimulationVariable` descriptor:

```java
SimulationVariable var = vars.get(0);
var.getAddress();      // "HP Sep.gasOutStream.temperature"
var.getName();         // "temperature"
var.getType();         // VariableType.OUTPUT or VariableType.INPUT
var.getDefaultUnit();  // "K"
var.getDescription();  // "Gas outlet temperature"
```

### Variable Types

| Type | Access | Description |
|------|--------|-------------|
| `OUTPUT` | Read-only | Calculated result (temperature, pressure, flow, power) |
| `INPUT` | Read-write | Settable parameter (outlet pressure, efficiency, Cv) |

## Supported Equipment

The automation API covers 20+ equipment types:

| Equipment | Key Properties |
|-----------|---------------|
| `Stream` | temperature, pressure, flowRate, molarFlowRate |
| `Separator` | temperature, pressure, liquidLevel + stream ports |
| `ThreePhaseSeparator` | Same as Separator + waterLevel + waterOutStream |
| `Tank` | temperature, pressure, liquidLevel + stream ports |
| `Compressor` | outletPressure (INPUT), power, polytropicEfficiency, isentropicEfficiency, speed |
| `CompressorTrain` | Same as Compressor (applied to first stage) |
| `Expander` | outletPressure (INPUT), power, isentropicEfficiency |
| `Pump` | outletPressure (INPUT), power |
| `Heater` / `Cooler` | outletTemperature (INPUT), duty |
| `HeatExchanger` | uAvalue (INPUT), duty + two outlet streams |
| `ThrottlingValve` | outletPressure, Cv (INPUT), percentValveOpening (INPUT) |
| `Pipeline` | pressure/temperature at inlet/outlet |
| `Ejector` | outletPressure (INPUT) + stream ports |
| `GibbsReactor` | temperature, pressure, outletTemperature (INPUT) |
| `DistillationColumn` | numberOfTrays, condenserTemperature, reboilerTemperature |
| `Recycle` | errorFlow, errorTemperature, errorPressure, flowTolerance (INPUT) |
| `ComponentSplitter` | splitFactor_0, splitFactor_1, ... (INPUT) |
| `Mixer` | temperature, pressure + outletStream |
| `Splitter` | splitFactor_0, splitFactor_1, ... (INPUT) + split streams |
| Generic `TwoPortEquipment` | Fallback with outletStream port |

## Unit Conversion

The API handles unit conversion for common properties:

```java
// Temperature in different units
double tempC = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");
double tempK = auto.getVariableValue("HP Sep.gasOutStream.temperature", "K");
double tempF = auto.getVariableValue("HP Sep.gasOutStream.temperature", "F");

// Pressure
double pBara = auto.getVariableValue("Compressor.outletStream.pressure", "bara");

// Flow rates
double massFlow = auto.getVariableValue("feed.flowRate", "kg/hr");
double molarFlow = auto.getVariableValue("feed.molarFlowRate", "mole/sec");
```

## Error Handling

The API throws `IllegalArgumentException` for invalid addresses or read-only writes:

```java
try {
    auto.getVariableValue("nonexistent.temperature", "C");
} catch (IllegalArgumentException e) {
    System.out.println("Unit not found: " + e.getMessage());
}

try {
    // Cannot set a calculated output
    auto.setVariableValue("Compressor.power", 1000.0, "kW");
} catch (IllegalArgumentException e) {
    System.out.println("Read-only variable: " + e.getMessage());
}
```

## Python Usage

```python
from neqsim import jneqsim

# Get automation from ProcessSystem
process = jneqsim.process.processmodel.ProcessSystem()
# ... build and run ...

auto = process.getAutomation()
units = list(auto.getUnitList())
vars = list(auto.getVariableList("HP Sep"))
temp = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C")
auto.setVariableValue("Compressor.outletPressure", 120.0, "bara")

# Or use convenience methods directly
process.getVariableValue("HP Sep.gasOutStream.temperature", "C")
process.setVariableValue("Compressor.outletPressure", 120.0, "bara")
```

## See Also

- [Process Serialization](process_serialization) - Save/load process state
- [Process Model Lifecycle](../process/lifecycle/process_model_lifecycle) - Versioning and lifecycle management
- [Process Simulation Guide](../process/) - Building process models
