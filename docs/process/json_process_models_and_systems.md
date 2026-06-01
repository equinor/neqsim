---
title: JSON Format for ProcessSystem and ProcessModel
description: Accurate reference for NeqSim JSON process builder input, validation, advanced equipment design metadata, data connections, ProcessModel areas, and large flowsheet setup with mixers, splitters, and recycles.
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
- `connections` (optional): explicit typed topology metadata
- `designCapacities` (optional): normalized equipment capacity data keyed by unit name
- `equipmentDesign` (optional): advisory grouped sizing data from extracted/advanced models
- `dataConnections` (optional): historian/API/tag mapping metadata preserved in the build result
- `metadata` (optional): arbitrary caller metadata preserved in the build result
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
- `tagName` (optional): process or instrument tag; round-trips through exporter/importer
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

Common aliases accepted by the builder and validator:

| Equipment/output | Accepted references |
|------------------|---------------------|
| Stream or generic outlet | `Unit`, `Unit.out`, `Unit.outStream`, `Unit.outlet` |
| Separator gas | `Separator.gasOut`, `Separator.gasOutStream`, `Separator.gas` |
| Separator liquid | `Separator.liquidOut`, `Separator.liquidOutStream`, `Separator.liquid` |
| Three-phase liquid outlets | `Separator.oilOut`, `Separator.oil`, `Separator.waterOut`, `Separator.water` |
| Splitter/manifold | `Splitter.split0`, `Splitter.split1`, `Splitter.splitStream_0`, `Splitter.splitStream_1` |
| Heat exchanger | `HX.outlet`, `HX.outlet0`, `HX.outlet1`, `HX.hx0`, `HX.hx1` |

## 3.1 Supported equipment types

The JSON builder delegates equipment creation to `EquipmentFactory`, so aliases and support evolve
with that factory. The current core JSON path covers these commonly used process types:

- Streams: `Stream`, `VirtualStream`
- Separation and storage: `Separator`, `ThreePhaseSeparator`, `GasScrubber`, `Tank`
- Rotating equipment: `Compressor`, `Pump`, `Expander`, `TurboExpanderCompressor`
- Heat transfer: `Heater`, `Cooler`, `HeatExchanger`
- Flow control and splitting: `ThrottlingValve`, `Splitter`, `ComponentSplitter`, `Mixer`, `Manifold`
- Iteration and logic helpers: `Recycle`, `Adjuster`, `Calculator`, `SpreadsheetBlock`, `UnisimCalculator`
- Pipe and route equipment: `AdiabaticPipe`, `PipeBeggsAndBrills`, `WaterHammerPipe`
- Process modules/equipment with factory support such as TEG absorbers, reactors, flare equipment,
  power-generation units, electrolyzers, and subsea/electrical equipment

Unsupported or very specialized fields can still be carried as `metadata`, `dataConnections`, or
inside advisory `equipmentDesign` sections while the physical model is built from the supported
topology and property fields.

## 3.2 Compressor driver power limits and curves

Compressor units can include a nested `driver` object in `properties`. This configures the live
`CompressorDriver` model used by compressor capacity checks, including maximum power capacity and
speed-dependent maximum power curves.

Polynomial power curve example:

```json
{
  "type": "Compressor",
  "name": "Export Compressor",
  "inlet": "HP Sep.gasOut",
  "properties": {
    "outletPressure": [120.0, "bara"],
    "speed": 6000.0,
    "driver": {
      "type": "VFD_MOTOR",
      "ratedPower": 5000.0,
      "maxPower": 5500.0,
      "ratedSpeed": 5000.0,
      "minSpeed": 1000.0,
      "maxSpeed": 6000.0,
      "maxPowerCurveCoefficients": [0.0, 1.0, 0.0]
    }
  }
}
```

Tabular power curve example:

```json
"driver": {
  "type": "GAS_TURBINE",
  "ratedPower": 40500.0,
  "ratedSpeed": 7383.0,
  "ambientTemperature": 288.15,
  "maxPowerSpeedCurve": {
    "speeds": [4922.0, 6000.0, 7383.0],
    "powers": [21.8, 32.0, 44.4],
    "powerUnit": "MW"
  }
}
```

Supported `driver.type` values are `ELECTRIC_MOTOR`, `VFD_MOTOR`, `GAS_TURBINE`,
`STEAM_TURBINE`, `RECIPROCATING_ENGINE`, and `EXPANDER_DRIVE`. The exported JSON uses kW for
stored tabular power points, but the builder accepts `kW`, `MW`, or `W` in `powerUnit`.

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

### 4.2 Design capacity and advanced equipment design metadata

Use `designCapacities` for deterministic equipment capacity inputs that should be applied to live
equipment during build:

```json
"designCapacities": {
  "HP Sep": {
    "internalDiameter": 2.0,
    "separatorLength": 6.0,
    "designGasLoadFactor": 0.08
  },
  "Export Compressor": {
    "ratedPower": 5000.0,
    "maxSpeed": 12000.0
  },
  "Export Cooler": {
    "maxDesignDutyMW": 12.0
  }
}
```

Supported normalized capacity properties are:

| Equipment | Capacity keys |
|-----------|---------------|
| Separator / scrubber | `internalDiameter`, `separatorLength`, `designGasLoadFactor` |
| Compressor | `ratedPower` in kW, `maxSpeed` |
| Heater / cooler | `maxDesignDuty` in W, `maxDesignDutyKW`, `maxDesignDutyMW` |
| Pump | `maxDesignPower`, `maxDesignVolumeFlow` |

Large extracted process models can also include a richer `equipmentDesign` object. It is preserved in
`SimulationResult.getMetadata()` and the builder applies the deterministic parts it understands:

- `valveSizingGroups`: applies `installedCv`, `normalOpeningPercent`,
  `minimumValveOpeningPercent`, and `maximumValveOpeningPercent` to listed valves
- `separatorSizing`: applies `innerDiameterM`, `tanTanLengthM`, and `designPressureBara`
- `compressorSizing`: applies `designDischargePressureBara`, `normalIsentropicEfficiency`, and
  converts `ratedShaftPowerMW` to normalized `ratedPower` capacity in kW
- `coolerSizing`: applies `normalOutletTemperatureDegC` and converts `maxDesignDutyMWEach` to
  normalized cooler capacity data

The result metadata contains the original advisory sections plus a `designDataApplied` report. This
lets API callers learn which parts were applied, which unit names were missing, and which sections
were retained only as metadata.

### 4.3 Data connections

Use `dataConnections` for non-physical integration metadata such as historian tags, API endpoint
bindings, tag quality rules, or model-variable address mappings. The builder does not create network
clients from this section. It preserves the JSON in `SimulationResult.getMetadata()` so downstream
automation code can bind the running process to plant data without losing the source mapping.

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
- missing per-area `process` array when validating top-level `areas`
- missing `type`
- missing `name`
- duplicate names

Warnings:
- inlet/inlets/streams input references that do not resolve to known unit/port names

## 8. ProcessModel lifecycle JSON

`ProcessModel.toJson()` exports builder JSON with a top-level `areas` object. Each area value is a
regular `ProcessSystem` builder JSON object, and model-level iteration settings are included at the
root. Shared live streams between areas are exported as `interAreaLinks` where needed.

Minimal shape:

```json
{
  "areas": {
    "separation": {
      "fluid": {"model": "SRK", "components": {"methane": 1.0}},
      "process": []
    },
    "compression": {
      "fluid": {"model": "SRK", "components": {"methane": 1.0}},
      "process": []
    }
  },
  "maxIterations": 100,
  "flowTolerance": 1.0e-6
}
```

Use `ProcessModel.fromJson(json)` or `ProcessModel.fromJsonAndRun(json)` for the builder JSON path.
The same `ProcessJsonValidator.validate(...)` preflight accepts both single `process` roots and
model-level `areas` roots.

After building a multi-area model, use `model.toDOT()` or `model.exportToGraphviz("plant.dot")` to
create one common Graphviz DOT graph with each area shown as a cluster. Use
`model.exportAreaDOT(Paths.get("plant-diagrams"))` when you want one DOT file per contained
`ProcessSystem` area. Cross-area DOT edges are drawn for shared live streams, including streams
recreated from `interAreaLinks` during JSON import.

## 8.1 Lifecycle JSON

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

Round-trip tests should use the builder JSON path, not report JSON:

- Use `process.toJson()` followed by `ProcessSystem.fromJson(...)` or
  `ProcessSystem.fromJsonAndRun(...)`.
- Use `model.toJson()` followed by `ProcessModel.fromJson(...)` or
  `ProcessModel.fromJsonAndRun(...)`.
- Use `getReport_json()` only for reporting/output payload tests; it is not builder input JSON.



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
