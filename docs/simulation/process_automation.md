---
title: Process Automation API
description: "String-addressable API for reading and writing simulation variables in NeqSim process models. Covers getUnitList, getVariableList, getVariableValue, setVariableValue with dot-notation addresses."
---

# Process Automation API

A stable, string-addressable API for interacting with running NeqSim process simulations.
Variables are accessed through dot-notation paths like `"separator.gasOutStream.temperature"`,
removing the need to navigate Java objects directly.

**Package:** `neqsim.process.automation`

## Why This Matters for Agentic Workflows

LLM agents and optimizers do not have a Java type checker, an IDE, or the ability to hold a
live object graph in memory across turns. `ProcessAutomation` is the bridge that makes a NeqSim
flowsheet *operable by an agent* rather than only by a programmer. It removes the four things
that otherwise make autonomous process work brittle:

| Problem for an agent without automation | What `ProcessAutomation` provides |
|------------------------------------------|-----------------------------------|
| **Discovery** — the agent cannot "see" the Java class hierarchy or guess method names | `getUnitList()`, `getVariableList()`, `describe()`, `getTopology()`, `getAdjustableParameters()` return the entire operable surface as data the agent can read |
| **Addressing** — equipment references are object pointers that cannot survive a tool call | Every handle is a stable **string address** (`"Compression::Compressor.outletPressure"`) the agent can store, log, and re-use across turns |
| **Brittleness** — a single typo or wrong unit throws a `RuntimeException` that derails the run | Safe accessors (`getVariableValueSafe`, `setVariableValueSafe`) and `validateAddress` return **JSON diagnostics with suggestions** instead of throwing, so the agent can self-correct |
| **Run feedback** — `run()` returns `void`; convergence and failed-unit info are scattered across objects | `runJson()`, `runUntilConvergedJson()` and `evaluate()` return **one schema-versioned JSON** with the converged flag, the failing unit, and read-back values in a single parse |

The net effect: an agent can **explore → adjust → run → read back** entirely through strings and
JSON, with every failure mode reported as structured data it can act on. This is what turns NeqSim
from a simulation library into a closed-loop optimization target. See
[Closed-Loop Agentic Optimization](#closed-loop-agentic-optimization) for the headline `evaluate()`
primitive, and the
[`neqsim-agentic-process-optimization`](https://github.com/equinor/neqsim) skill for full-plant
optimization patterns.

> **One cached facade.** `process.getAutomation()` / `plant.getAutomation()` always return the
> **same** instance, so the dirty flag, learned typo corrections, and diagnostics history persist
> across agent turns. Always read/write through `getAutomation()` rather than constructing a new
> `ProcessAutomation` each turn.

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


## Additional Discovery Helpers

Beyond `getUnitList()` and `getVariableList(unit)`, `ProcessAutomation` also provides
helpers that are useful when building generic tooling:

```java
ProcessAutomation auto = process.getAutomation();

boolean multiArea = auto.isMultiArea();
String equipmentType = auto.getEquipmentType("HP Sep");

List<SimulationVariable> inputVars =
    auto.getVariableList("Compressor", SimulationVariable.VariableType.INPUT);
List<SimulationVariable> outputVars =
    auto.getVariableList("Compressor", SimulationVariable.VariableType.OUTPUT);
```

- `isMultiArea()` tells whether the facade is bound to a `ProcessModel` (multi-area) or a
  single `ProcessSystem`.
- `getEquipmentType(unitName)` returns a normalized equipment class label (for example
  `"Separator"`, `"Compressor"`, `"HeatExchanger"`) that can drive UI widgets and validation.
- `getVariableList(unitName, type)` lets you filter to only writable inputs or read-only outputs.

For multi-area models, pass area-qualified names where needed (for example
`"Compression::Compressor"`).

## Adjustable Parameters

For optimization and agentic workflows you often need the full list of
**degrees of freedom** — the inputs that may be changed to influence the process —
without having to enumerate every unit and filter its variables yourself.
`getAdjustableParameters()` returns this registry directly.

```java
ProcessAutomation auto = process.getAutomation();

List<AdjustableParameter> dof = auto.getAdjustableParameters();
for (AdjustableParameter p : dof) {
  System.out.println(p.getName()
      + " @ " + p.getAddress()
      + " [" + p.getUnit() + "]"
      + " bounds=[" + p.getLowerBound() + ", " + p.getUpperBound() + "]"
      + " -> " + p.getTargetUnitName() + "." + p.getTargetProperty()
      + " (" + p.getSource() + ")");
}
```

The registry combines two sources:

| Source | Origin | Bounds / unit |
|--------|--------|---------------|
| `INPUT_VARIABLE` | Every writable `SimulationVariable` of type `INPUT` | Variable's own min/max and default unit |
| `ADJUSTER` | Each `Adjuster` unit operation | Adjuster's min/max adjusted value and unit |

### AdjustableParameter Descriptor

Each entry is an `AdjustableParameter` describing one degree of freedom:

| Method | Description |
|--------|-------------|
| `getName()` | Short, human-readable parameter name |
| `getAddress()` | Stable dot-notation address for `setVariableValue(...)` |
| `getUnit()` | Unit of measure (e.g. `bara`, `C`, `kg/hr`) |
| `getLowerBound()` | Lower bound as a `Double`, or `null` if unbounded |
| `getUpperBound()` | Upper bound as a `Double`, or `null` if unbounded |
| `getTargetUnitName()` | The unit operation the parameter actually affects |
| `getTargetProperty()` | The property the parameter actually drives |
| `getSource()` | `Source.INPUT_VARIABLE` or `Source.ADJUSTER` |

For an `Adjuster`-sourced parameter, `getTargetUnitName()` / `getTargetProperty()` make
explicit what the handle actually controls. This removes the ambiguity that arises when an
adjuster's name does not match the variable it drives — so an optimizer can map the
parameter straight onto the variable it should perturb.

> **Unbounded sentinel:** Adjusters default to ±1e10. Any bound with magnitude at or
> beyond 1e9 (or non-finite) is reported as `null` (no bound).

### JSON Form

`getAdjustableParametersJson()` returns a schema-versioned payload suitable for handing to
an external optimizer or agent:

```java
String json = auto.getAdjustableParametersJson();
```

```json
{
  "schemaVersion": "1.0",
  "count": 2,
  "parameters": [
    {
      "name": "outletPressure",
      "address": "Compressor.outletPressure",
      "unit": "bara",
      "lowerBound": null,
      "upperBound": null,
      "targetUnitName": "Compressor",
      "targetProperty": "outletPressure",
      "source": "INPUT_VARIABLE"
    },
    {
      "name": "feedRateAdjuster",
      "address": "HP Sep.gasOutStream.flowRate",
      "unit": "kg/hr",
      "lowerBound": 1000.0,
      "upperBound": 50000.0,
      "targetUnitName": "HP Sep",
      "targetProperty": "gasOutStream.flowRate",
      "source": "ADJUSTER"
    }
  ]
}
```

Use the `address` field directly with `setVariableValue(address, value, unit)` to apply a
candidate solution back to the process.

## Closed-Loop Agentic Optimization

The methods above let an agent *discover* and *address* variables. The methods in this section
close the loop: they apply a batch of setpoints, run the model, gate the result on convergence,
and read back objectives — returning **one JSON object** so the agent never has to catch a Java
exception or correlate several objects to decide whether a trial is usable.

### `evaluate(...)` — the atomic optimizer step

`evaluate()` is the recommended primitive for every iteration of an optimizer or agent loop. It
performs the full **apply → run-until-converged → gate → read-back** sequence and never throws.

```java
ProcessAutomation auto = plant.getAutomation();

Map<String, Double> setpoints = new LinkedHashMap<String, Double>();
setpoints.put("Compression::Export Compressor.outletPressure", 150.0);
setpoints.put("Separation::Oil Heater.outletTemperature", 78.0);

List<String> readbacks = Arrays.asList(
    "Compression::Export Compressor.power",
    "Separation::Crude.flowRate");

// setpoints in bara/C, read-backs in kW, up to 30 iterations, tol 5e-3
String json = auto.evaluate(setpoints, "bara", readbacks, "kW", 30, 5.0e-3);
```

The returned JSON gives the agent everything it needs to score the trial:

```json
{
  "schemaVersion": "1.0",
  "setpointsApplied": { "Compression::Export Compressor.outletPressure": 150.0,
                        "Separation::Oil Heater.outletTemperature": 78.0 },
  "setpointsRejected": {},
  "runSucceeded": true,
  "converged": true,
  "iterations": 7,
  "maxError": 0.0021,
  "failedUnitName": null,
  "failedUnitError": null,
  "feasible": true,
  "readbacks": { "Compression::Export Compressor.power": 8421.5,
                 "Separation::Crude.flowRate": 412000.0 },
  "readbackErrors": {}
}
```

The **`feasible`** flag is the single field an optimizer should gate on. It is `true` only when:

- the run did not throw (`runSucceeded`),
- the model converged (`converged`),
- no unit reported a failure (`failedUnitName` is null), **and**
- every setpoint was accepted (`setpointsRejected` is empty).

A bad address or out-of-bounds value lands in `setpointsRejected` (the good setpoints are still
applied), and a read-back of a non-existent variable lands in `readbackErrors` — both without
throwing. This means a single malformed candidate degrades one trial instead of crashing the loop.

> **Why one JSON object?** Through jpype, a Java `String` is returned to Python as
> `java.lang.String`; wrapping the result as `json.loads(str(result))` is all the agent needs.
> Returning structured run feedback as JSON avoids the common pitfall of trying to read a `void`
> `run()` and then juggling `RunStatus`, `solved()`, and the convergence report separately.

A convenience overload uses one unit for both setpoints and read-backs with robust defaults
(30 iterations, relative tolerance `5e-3` — chosen because plants with near-zero-flow anti-surge
recycles rarely reach a strict `1e-4`):

```java
String json = auto.evaluate(setpoints, "bara",
    Arrays.asList("Compression::Export Compressor.power"));
```

### `runUntilConvergedJson(...)` and `runJson()`

When you have already applied setpoints (for example via `setValues(...)`) and just need a gated
run, call these directly:

```java
// Run the model to convergence and return the full report as JSON (never throws).
String conv = auto.runUntilConvergedJson(30, 5.0e-3);

// Single run() with structured outcome (clears the dirty flag even on failure).
String run = auto.runJson();
```

For a multi-area `ProcessModel`, `runUntilConvergedJson()` delegates to
`ProcessModel.runUntilConverged(...)` and embeds the nested `convergence` report plus a per-area
`areas` array. For a single `ProcessSystem`, `run()` already iterates internal recycles and the
`converged` flag reflects whether the run completed without a failed unit (the `RunStatus` success
flag).

| Method | Throws? | Returns | Use when |
|--------|---------|---------|----------|
| `evaluate(...)` | Never | setpoints + run gate + read-backs | Each optimizer / agent iteration |
| `runUntilConvergedJson(maxIter, tol)` | Never (args validated up front) | convergence report + run status | You set values yourself and need a gated run |
| `runJson()` | Never | single-run status | A one-shot run with structured feedback |
| `getRunStatusJson()` | Never | last run status only | Inspect the previous run without re-running |

> **Dirty-flag aware.** All three methods clear the dirty flag (even on failure). Combine with
> `isDirty()` / `runIfDirty()` to avoid redundant runs when nothing changed since the last solve.

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


## Self-Healing Safe Accessors

For agentic workflows where variable addresses may contain typos or formatting drift,
use the safe accessors. They return JSON payloads with diagnostics instead of throwing
immediately.

```java
ProcessAutomation auto = process.getAutomation();

String getJson = auto.getVariableValueSafe("hp separator.temperature", "C");
String setJson = auto.setVariableValueSafe("compressor outlet pressure", 120.0, "bara");
System.out.println(getJson);
System.out.println(setJson);
```

Typical successful payload:

```json
{
  "status": "auto_corrected",
  "originalAddress": "hp separator.temperature",
  "correctedAddress": "HP Sep.temperature",
  "value": 25.0,
  "unit": "C"
}
```

If recovery fails, payload contains diagnostics (`errorCategory`, `message`, and suggestions)
that can be fed back into the next call.

### Built-in Recovery Behaviors

- Case-insensitive and whitespace-tolerant matching.
- Typo correction (small edit distance).
- Partial-name recovery for unit and property names.
- Physical bounds checking before write operations.

### Diagnostics and Learning Report

Use `AutomationDiagnostics` to inspect error patterns and learned corrections:

```java
AutomationDiagnostics diagnostics = auto.getDiagnostics();
String report = diagnostics.getLearningReport();
System.out.println(report);
```

This is useful when building autonomous optimizers or digital-twin agents that repeatedly
read/write process variables.

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
