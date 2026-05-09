---
title: JSON Format for ProcessSystem and ProcessModel
description: Accurate reference for NeqSim JSON process builder input, validation, and large flowsheet setup with mixers, splitters, and recycles.
---

# JSON Format for ProcessSystem and ProcessModel

This page documents the **actual JSON format** consumed by `ProcessSystem.fromJson(...)` and
`ProcessSystem.fromJsonAndRun(...)`.

For exporting a live `ProcessSystem` or `ProcessModel` to portable JSON, including
E300-characterized fluids, stream-specific fluids, recycle settings, and MCP checks, see
[Process JSON Export and E300 Fluid Characterization](process_json_export_and_e300_fluids.md).

## Quick answer: is there a JSON verifier?

Yes:

- `ProcessSystem.validateJson(String json)`
- `ProcessJsonValidator.validate(String json)`

Use validation as a pre-flight check before calling `fromJson()` or `fromJsonAndRun()`.

## 1. Root JSON structure (builder input)

The builder expects a root object with these common keys:

- `fluid` (optional): single default fluid definition
- `fluids` (optional): named fluids map (for `fluidRef` on streams)
- `process` (**required**): array of unit definitions
- `autoRun` (optional): `true` to run immediately

Minimal valid structure:

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.85,
      "ethane": 0.10,
      "propane": 0.05
    }
  },
  "process": [
    {
      "type": "Stream",
      "name": "feed",
      "properties": {
        "flowRate": [50000.0, "kg/hr"]
      }
    }
  ]
}
```

> Important: current builder input uses `process` + `properties` + `inlet`/`inlets` patterns.

## 2. Unit definition format

Each object in `process` should include:

- `type` (required)
- `name` (strongly recommended and should be unique)
- wiring fields depending on unit type:
  - `inlet`: single reference string
  - `inlets`: array of reference strings
- `properties` object for operating conditions and setpoints

Example:

```json
{
  "type": "Compressor",
  "name": "Comp",
  "inlet": "HP Sep.gasOut",
  "properties": {
    "outletPressure": [80.0, "bara"]
  }
}
```

## 3. Stream addressing and ports

References are by unit name, optionally with port suffix:

- `"feed"` (stream unit)
- `"HP Sep.gasOut"`
- `"HP Sep.liquidOut"`
- `"Comp.out"` / `"Comp.outStream"`

The resolver trims surrounding whitespace.

## 4. Large process JSON example (splitter + mixer + recycle)

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 70.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.88,
      "ethane": 0.08,
      "propane": 0.04
    }
  },
  "autoRun": true,
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [250000.0, "kg/hr"]}},

    {"type": "Splitter", "name": "Feed Splitter", "inlet": "feed",
      "properties": {"splitFactors": [0.60, 0.40]}},

    {"type": "Cooler", "name": "Train A Cooler", "inlet": "Feed Splitter.splitStream_0",
      "properties": {"outTemperature": [20.0, "C"]}},

    {"type": "ThrottlingValve", "name": "Train B Valve", "inlet": "Feed Splitter.splitStream_1",
      "properties": {"outletPressure": [65.0, "bara"]}},

    {"type": "Mixer", "name": "Combined Mixer",
      "inlets": ["Train A Cooler.out", "Train B Valve.out"]},

    {"type": "Separator", "name": "HP Sep", "inlet": "Combined Mixer.out"},

    {"type": "Compressor", "name": "Recycle Comp", "inlet": "HP Sep.gasOut",
      "properties": {"outletPressure": [72.0, "bara"]}},

    {"type": "Recycle", "name": "Gas Recycle", "inlet": "Recycle Comp.out"},

    {"type": "Mixer", "name": "Feed + Recycle",
      "inlets": ["feed", "Gas Recycle.out"]}
  ]
}
```

### 4.1 Optional explicit `connections` metadata

You can include a root-level `connections` array to persist explicit topology metadata.
These are recorded on `ProcessSystem.getConnections()` and are useful for interchange,
PFD export, and topology analysis.

```json
"connections": [
  {"from": "feed", "to": "Sep", "sourcePort": "outlet", "targetPort": "inlet", "type": "MATERIAL"}
]
```

Supported types map to `ProcessConnection.ConnectionType` values (`MATERIAL`, `ENERGY`, `SIGNAL`).
Unknown types default to `MATERIAL` with a warning.

## 5. Validation workflow (recommended)

Java:

```java
String json = ...;
ProcessJsonValidator.ValidationReport report = ProcessSystem.validateJson(json);
if (!report.isValid()) {
  throw new IllegalArgumentException("Invalid process JSON: " + report.getErrors());
}
SimulationResult result = ProcessSystem.fromJsonAndRun(json);
```

Python (via JPype wrapper):

```python
report = ns.ProcessSystem.validateJson(json_text)
if not report.isValid():
    raise ValueError(str(report.getErrors()))
result = ns.ProcessSystem.fromJsonAndRun(json_text)
```


## 6. Importing E300 fluids (supported)

The JSON builder supports loading fluid thermodynamics from Eclipse E300 data using
`e300FilePath` in the `fluid` object.

Example:

```json
{
  "fluid": {
    "model": "PR_LK",
    "temperature": 310.15,
    "pressure": 50.0,
    "e300FilePath": "/absolute/path/to/fluid.e300"
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [10000.0, "kg/hr"]}}
  ]
}
```

Notes:

- When `e300FilePath` is provided, component properties and BIPs are sourced from the E300 file.
- You can still validate the process structure with `ProcessSystem.validateJson(...)` before build/run.
- For pre-built fluids from E300 workflows, use `ProcessSystem.fromJsonAndRun(json, fluid)`.

## 7. What validator checks today

Errors:
- invalid JSON
- missing `process` array
- missing `type`
- missing `name`
- duplicate names

Warnings:
- inlet/inlets/streams input references that do not resolve to known unit/port names

## 8. ProcessModel lifecycle JSON

For lifecycle snapshots/versioning of full `ProcessModel` and `ProcessSystem` states,
see:

- `docs/process/lifecycle/process_model_lifecycle.md`
- `docs/simulation/process_serialization.md`

Builder input JSON (`fromJson`) and lifecycle snapshot JSON (`ProcessModelState`) have different purposes.


## 9. Export and full JSON round-trip workflows

### 9.1 Builder JSON round-trip (ProcessSystem -> JSON -> ProcessSystem)

Use this for interoperability and API payloads.

```java
// Build or run a process
ProcessSystem process = ...;
process.run();

// Export to builder JSON
String json = process.toJson();

// Rebuild from exported JSON
SimulationResult rebuilt = ProcessSystem.fromJson(json);
if (!rebuilt.isSuccess()) {
  throw new RuntimeException(rebuilt.toString());
}

// Optional: run rebuilt process
rebuilt.getProcessSystem().run();
```

Python pattern:

```python
json_text = process.toJson()
rebuild = ns.ProcessSystem.fromJson(json_text)
if not rebuild.isSuccess():
    raise RuntimeError(str(rebuild))
rebuilt_process = rebuild.getProcessSystem()
rebuilt_process.run()
```

### 9.2 Lifecycle JSON round-trip (state snapshots)

Use `ProcessSystemState` and `ProcessModelState` for Git-friendly model snapshots,
versioning, comparison, and lifecycle workflows.

```java
// Single ProcessSystem snapshot
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.saveToFile("model_state.json");
ProcessSystemState loaded = ProcessSystemState.loadFromFile("model_state.json");
ProcessSystem restored = loaded.toProcessSystem();

// Multi-area ProcessModel snapshot
ProcessModelState modelState = ProcessModelState.fromProcessModel(model);
modelState.saveToFile("plant_state.json");
ProcessModelState loadedModelState = ProcessModelState.loadFromFile("plant_state.json");
ProcessModel restoredModel = loadedModelState.toProcessModel();
```

Choose the round-trip type by purpose:

- `ProcessSystem.toJson()/fromJson(...)`: portable builder/execution JSON
- `ProcessSystemState` / `ProcessModelState`: lifecycle state management JSON



## 10. Coverage note

Not every possible NeqSim feature is currently expressible in concise JSON form.
The JSON builder supports the most commonly used process/equipment workflows, and
coverage continues to evolve. For advanced behavior, you can:

- build core topology from JSON, then refine in Java/Python API calls
- export back using `process.toJson()` for traceability
- use lifecycle snapshots (`ProcessSystemState`/`ProcessModelState`) for full-state persistence



## 11. JSON + optimization loop example (process + sizing variables)

You can tune both process variables and equipment sizing variables directly from JSON payloads.
A common pattern is:

1. Optimizer proposes a payload (e.g., separator pressure + mechanical design knobs).
2. Validate JSON with `ProcessSystem.validateJson(...)`.
3. Build/run with `ProcessSystem.fromJsonAndRun(...)`.
4. Evaluate objective (energy, CAPEX proxy, emissions, etc.).

Example payload fragment:

```json
{
  "process": [
    {"type": "Separator", "name": "HP Sep", "inlet": "feed",
      "properties": {
        "pressure": [70.0, "bara"],
        "mechanicalDesign": {
          "gasLoadFactor": 0.107,
          "retentionTime": 120.0,
          "calcDesign": true
        }
      }
    }
  ]
}
```

Python pseudo-loop:

```python
for candidate in candidates:
    payload = make_payload(candidate)
    report = ns.ProcessSystem.validateJson(payload)
    if not report.isValid():
        continue
    result = ns.ProcessSystem.fromJsonAndRun(payload)
    if not result.isSuccess():
        continue
    process = result.getProcessSystem()
    # Example objective: combine process KPI + design KPI
    score = objective(process)
```
