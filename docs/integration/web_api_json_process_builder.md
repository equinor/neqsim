---
title: "Web API Integration: JSON Process Builder and Session Management"
description: "Guide to building and running NeqSim process simulations from JSON definitions, structured error responses, equipment wiring API, and multi-user session management. Covers JsonProcessBuilder, SimulationResult, ProcessSimulationSession, and Python integration via JPype."
---

# Web API Integration: JSON Process Builder and Session Management

NeqSim provides a complete set of classes for building process simulations declaratively from JSON,
receiving structured error/success responses, and managing isolated multi-user sessions. These
features are designed for web services and Python-based applications that call NeqSim remotely.

## Architecture Overview

| Class | Purpose |
|-------|---------|
| `JsonProcessBuilder` | Parses JSON into a `ProcessSystem` with fluids, equipment, and stream wiring |
| `SimulationResult` | Structured success/error envelope with error codes, remediation hints, and report JSON |
| `ProcessSimulationSession` | Multi-user session manager with template-based copy-on-create and automatic expiry |
| `ProcessSystem.fromJson()` | Convenience entry points on `ProcessSystem` for JSON build and run |
| `ProcessSystem.resolveStreamReference()` | Resolves dot-notation stream references (e.g., `"HP Sep.gasOut"`) |
| `ProcessSystem.wireStream()` | Connects equipment by name using the wiring API |

All classes are in the `neqsim.process.processmodel` package.

---

## 1. JSON Process Builder

### JSON Format

A JSON process definition has three top-level sections:

```json
{
  "fluid": { ... },
  "fluids": { ... },
  "process": [ ... ],
  "autoRun": false
}
```

**Sections:**

- **`fluid`** (optional) — A single default fluid definition used by all streams unless overridden.
- **`fluids`** (optional) — A named map of fluid definitions for multi-fluid processes.
- **`process`** (required) — An ordered array of equipment definitions. Units are created and wired in order.
- **`autoRun`** (optional, default `false`) — If `true`, the process is run immediately after building.

### Fluid Definition

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "mixingRule": "classic",
    "multiPhaseCheck": true,
    "components": {
      "methane": 0.85,
      "ethane": 0.10,
      "propane": 0.05
    }
  }
}
```

**Parameters:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `model` | string | `"SRK"` | Thermodynamic model: `SRK`, `PR`, `CPA`, `GERG2008`, `PCSAFT`, `UMRPRU` |
| `temperature` | number | `288.15` | Temperature in Kelvin |
| `pressure` | number | `1.01325` | Pressure in bara |
| `mixingRule` | string | `"classic"` | Mixing rule name |
| `multiPhaseCheck` | boolean | `false` | Enable multi-phase detection |
| `components` | object | — | Map of component name to mole fraction |

### Multiple Named Fluids

Use the `fluids` map when different streams need different compositions:

```json
{
  "fluids": {
    "rich_gas": {
      "model": "SRK",
      "temperature": 298.15,
      "pressure": 50.0,
      "components": { "methane": 0.85, "ethane": 0.10, "propane": 0.05 }
    },
    "lean_gas": {
      "model": "SRK",
      "temperature": 298.15,
      "pressure": 50.0,
      "components": { "methane": 0.95, "ethane": 0.05 }
    }
  },
  "process": [
    { "type": "Stream", "name": "rich feed", "fluidRef": "rich_gas" },
    { "type": "Stream", "name": "lean feed", "fluidRef": "lean_gas" }
  ]
}
```

### Equipment Definitions

Each entry in the `process` array defines one equipment unit:

```json
{
  "type": "Separator",
  "name": "HP Sep",
  "inlet": "feed",
  "properties": {
    "internalDiameter": 2.0
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Equipment type name (matches `EquipmentFactory` types) |
| `name` | string | No | Unique name (auto-generated if omitted) |
| `inlet` | string | No | Stream reference for inlet (see Stream Wiring below) |
| `fluidRef` | string | No | Named fluid reference for Stream-type units |
| `properties` | object | No | Property setters applied via reflection |

### Supported Equipment Types

Any type registered in `EquipmentFactory` is supported, including:

`Stream`, `Separator`, `ThreePhaseSeparator`, `Compressor`, `Expander`, `Pump`, `Heater`, `Cooler`, `HeatExchanger`, `Mixer`, `Splitter`, `ThrottlingValve` (`Valve`), `Recycle`, `Adjuster`, `Tank`, `Flare`, `Ejector`, and more.

### Property Setting

Properties are applied via reflection using setter methods. Two formats are supported:

**Simple value** — calls `setPropertyName(double)`, `setPropertyName(String)`, or `setPropertyName(boolean)`:

```json
"properties": {
  "outletPressure": 80.0,
  "isentropicEfficiency": 0.75,
  "usePolytropicCalc": true
}
```

**Value with unit** — calls `setPropertyName(double, String)`:

```json
"properties": {
  "flowRate": [50000.0, "kg/hr"],
  "outletPressure": [80.0, "bara"]
}
```

---

## 2. Stream Wiring API

### Dot-Notation References

The `inlet` field on each equipment definition uses dot-notation to specify which outlet port of an upstream unit to connect:

| Reference | Resolves To |
|-----------|-------------|
| `"feed"` | The stream named `"feed"` (direct stream reference), or the default outlet of unit `"feed"` |
| `"HP Sep.gasOut"` | `HPSep.getGasOutStream()` |
| `"HP Sep.liquidOut"` | `HPSep.getLiquidOutStream()` |
| `"HP Sep.oilOut"` | `HPSep.getOilOutStream()` |
| `"HP Sep.waterOut"` | `HPSep.getWaterOutStream()` |
| `"Comp.outlet"` | `Comp.getOutletStream()` |

Port aliases are case-insensitive: `gasOut`, `gas`, `liquidOut`, `liquid`, `oilOut`, `oil`, `waterOut`, `water`, `outlet`.

### Programmatic Wiring on ProcessSystem

After building a `ProcessSystem`, you can also wire streams programmatically:

```java
// Resolve a stream reference
StreamInterface gasStream = process.resolveStreamReference("HP Sep.gasOut");

// Wire a stream to a target unit
boolean success = process.wireStream("Compressor-1", "HP Sep.gasOut");
```

From Python via JPype:

```python
from neqsim import jneqsim

process = jneqsim.process.processmodel.ProcessSystem.fromJson(json_string).getProcessSystem()
gas = process.resolveStreamReference("HP Sep.gasOut")
process.wireStream("Compressor-1", "HP Sep.gasOut")
```

---

## 3. Structured Error Responses

All build and run operations return a `SimulationResult` with a well-defined JSON structure.

### Success Response

```json
{
  "status": "success",
  "processSystemName": "json-process",
  "report": { ... },
  "warnings": [
    "Property outletPressure not found on Stream (tried setOutletPressure)"
  ]
}
```

### Error Response

```json
{
  "status": "error",
  "errors": [
    {
      "code": "STREAM_NOT_FOUND",
      "message": "Inlet reference 'HP Sep' not found for unit 'Compressor-1'",
      "unit": "Compressor-1",
      "remediation": "Ensure the referenced unit exists and was defined before this unit"
    }
  ],
  "warnings": []
}
```

### Error Codes

| Code | Meaning |
|------|---------|
| `JSON_PARSE_ERROR` | Input is null, empty, or malformed JSON |
| `MISSING_PROCESS` | JSON has no `process` array |
| `MISSING_TYPE` | A unit definition has no `type` field |
| `UNKNOWN_MODEL` | Unrecognized thermodynamic model name |
| `FLUID_ERROR` | Error creating a fluid (bad component, invalid parameters) |
| `NO_FLUID` | Stream has no fluid defined and no default fluid exists |
| `FLUID_NOT_FOUND` | `fluidRef` refers to a fluid name that does not exist |
| `STREAM_NOT_FOUND` | `inlet` refers to an unknown unit or port |
| `UNIT_ERROR` | Equipment creation or wiring failed |
| `SIMULATION_ERROR` | Process ran but threw an exception |
| `VALIDATION_*` | Validation issues from `validateSetup()` |
| `SESSION_NOT_FOUND` | Session ID not found or expired |
| `MAX_SESSIONS` | Maximum concurrent session limit reached |

### Java API

```java
// Build only
SimulationResult result = ProcessSystem.fromJson(jsonString);
if (result.isSuccess()) {
    ProcessSystem process = result.getProcessSystem();
}

// Build and run
SimulationResult result = ProcessSystem.fromJsonAndRun(jsonString);
String reportJson = result.getReportJson();

// Validate and report
ProcessSystem process = ...;
SimulationResult validation = process.validateAndReport();

// Run and report
SimulationResult runResult = process.runAndReport();
String fullJson = runResult.toJson();  // Ready for HTTP response
```

---

## 4. Session Management

`ProcessSimulationSession` provides multi-user isolation for web services where each request or user gets their own `ProcessSystem` instance.

### Core Concepts

- **Templates** — Pre-built `ProcessSystem` objects registered by name. Creating a session from a template deep-copies it so each session is independent.
- **Sessions** — Isolated `ProcessSystem` instances identified by UUID. Each session has its own state and can be modified and run independently.
- **Expiry** — Sessions are automatically cleaned up after a configurable timeout (default 30 minutes).
- **Concurrency** — Thread-safe with `ConcurrentHashMap` backing and configurable maximum session count (default 100).

### Java Usage

```java
// Create session manager
ProcessSimulationSession manager = new ProcessSimulationSession();

// Register a template
ProcessSystem template = buildGasProcessingProcess();
template.run();
manager.registerTemplate("gas_processing", template);

// Create a user session from the template
String sessionId = manager.createSession("gas_processing");

// Get the session and modify it
ProcessSystem userProcess = manager.getSession(sessionId);
// ... modify parameters ...

// Run and get results
SimulationResult result = manager.runSession(sessionId);

// Clean up
manager.destroySession(sessionId);

// Shut down (destroys all sessions, stops cleanup thread)
manager.shutdown();
```

### JSON-Based Session Creation

```java
// Create a session directly from JSON
SimulationResult createResult = manager.createSessionFromJson(jsonDefinition);
if (createResult.isSuccess()) {
    // Session ID is in the first warning entry
    String sessionInfo = createResult.getWarnings().get(0);  // "sessionId:uuid-value"
    String sessionId = sessionInfo.substring("sessionId:".length());

    // Run the session
    SimulationResult runResult = manager.runSession(sessionId);
}
```

### Custom Configuration

```java
// Custom timeout (60 min) and max sessions (200)
ProcessSimulationSession manager = new ProcessSimulationSession(60, 200);

// Modify at runtime
manager.setMaxSessions(500);

// Manual cleanup
int removed = manager.cleanupExpiredSessions();

// Session info
int active = manager.getActiveSessionCount();
Map<String, String> info = manager.getSessionInfo();
```

---

## 5. Python Integration Examples

### Basic: Build and Run from JSON

```python
from neqsim import jneqsim

ProcessSystem = jneqsim.process.processmodel.ProcessSystem

json_def = '''{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
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
      "properties": { "flowRate": [50000.0, "kg/hr"] }
    },
    {
      "type": "Separator",
      "name": "HP Sep",
      "inlet": "feed"
    },
    {
      "type": "Compressor",
      "name": "Gas Compressor",
      "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": 80.0 }
    }
  ],
  "autoRun": true
}'''

result = ProcessSystem.fromJsonAndRun(json_def)

if result.isSuccess():
    print("Simulation succeeded")
    print(result.getReportJson())
else:
    for err in result.getErrors():
        print(f"[{err.getCode()}] {err.getMessage()}")
        print(f"  Fix: {err.getRemediation()}")
```

### Session Management from Python

```python
from neqsim import jneqsim

SessionManager = jneqsim.process.processmodel.ProcessSimulationSession

manager = SessionManager()

# Create session from JSON
create_result = manager.createSessionFromJson(json_def)
if create_result.isSuccess():
    session_info = str(create_result.getWarnings().get(0))
    session_id = session_info.split(":", 1)[1]

    # Modify parameters
    process = manager.getSession(session_id)
    feed = process.getUnit("feed")
    feed.setFlowRate(60000.0, "kg/hr")

    # Re-run
    run_result = manager.runSession(session_id)
    print(run_result.toJson())

    # Clean up
    manager.destroySession(session_id)

manager.shutdown()
```

### Validate Before Running

```python
result = ProcessSystem.fromJson(json_def)
if result.isSuccess():
    process = result.getProcessSystem()
    validation = process.validateAndReport()
    if validation.isSuccess():
        run_result = process.runAndReport()
        print(run_result.toJson())
    else:
        print("Validation failed:")
        print(validation.toJson())
```

---

## 6. Complete Example: Gas Processing Plant

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 323.15,
    "pressure": 65.0,
    "mixingRule": "classic",
    "components": {
      "nitrogen": 0.01,
      "CO2": 0.02,
      "methane": 0.80,
      "ethane": 0.08,
      "propane": 0.05,
      "n-butane": 0.02,
      "n-pentane": 0.01,
      "n-hexane": 0.005,
      "n-heptane": 0.005
    }
  },
  "process": [
    {
      "type": "Stream",
      "name": "well stream",
      "properties": {
        "flowRate": [75000.0, "kg/hr"]
      }
    },
    {
      "type": "Cooler",
      "name": "inlet cooler",
      "inlet": "well stream",
      "properties": {
        "outTemperature": [303.15]
      }
    },
    {
      "type": "ThreePhaseSeparator",
      "name": "inlet separator",
      "inlet": "inlet cooler.outlet"
    },
    {
      "type": "Compressor",
      "name": "1st stage compressor",
      "inlet": "inlet separator.gasOut",
      "properties": {
        "outletPressure": 100.0,
        "isentropicEfficiency": 0.78
      }
    },
    {
      "type": "Cooler",
      "name": "aftercooler",
      "inlet": "1st stage compressor.outlet",
      "properties": {
        "outTemperature": [308.15]
      }
    },
    {
      "type": "Separator",
      "name": "scrubber",
      "inlet": "aftercooler.outlet"
    }
  ],
  "autoRun": true
}
```

---

## Related Documentation

- [AI Validation Framework](ai_validation_framework) — Structured validation with remediation hints
- [External Optimizer Integration](EXTERNAL_OPTIMIZER_INTEGRATION) — Python/SciPy optimization with NeqSim
- [Python Extension Patterns](../development/python_extension_patterns) — JPype integration patterns
- [Process Simulation](../simulation/) — Advanced simulation techniques
