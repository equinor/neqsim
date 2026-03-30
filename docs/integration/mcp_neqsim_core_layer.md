---
title: "MCP Core Layer: Runners, Models, and Catalogs"
description: "Architecture and API reference for the MCP integration layer in neqsim core — FlashRunner, ProcessRunner, Validator, ComponentQuery, typed models (ApiEnvelope, FlashRequest, FlashResult), and example/schema catalogs. Framework-agnostic, testable with JUnit, portable to any MCP server or REST API."
---

# MCP Core Layer: Runners, Models, and Catalogs

NeqSim includes a framework-agnostic MCP (Model Context Protocol) integration layer in the
`neqsim.mcp` package. This layer provides stateless runners, typed models, and self-describing
catalogs that any MCP server, REST API, or CLI tool can use to expose NeqSim's thermodynamic
engine to LLMs and other automated clients.

## Architecture Overview

| Package | Purpose |
|---------|---------|
| `neqsim.mcp.runners` | Stateless runners that accept JSON, call NeqSim, and return JSON |
| `neqsim.mcp.model` | Typed Java models for requests, responses, and diagnostics |
| `neqsim.mcp.catalog` | Self-describing examples and JSON Schema catalogs |

All classes are **pure Java 8** and have **no framework dependencies** — they can be used
from Quarkus, Spring Boot, plain servlets, or a standalone `main()` method.

```
neqsim/mcp/
├── runners/
│   ├── FlashRunner.java        # Flash calculations (9 types × 6 EOS)
│   ├── ProcessRunner.java      # Process simulation via JsonProcessBuilder
│   ├── Validator.java          # Pre-flight input validation (12+ checks)
│   └── ComponentQuery.java     # Component database search & fuzzy matching
├── model/
│   ├── ApiEnvelope.java        # Standard response wrapper
│   ├── FlashRequest.java       # Typed flash input (builder pattern)
│   ├── FlashResult.java        # Typed flash output
│   ├── ProcessResult.java      # Typed process simulation output
│   ├── ValueWithUnit.java      # Numeric value + unit string
│   └── DiagnosticIssue.java    # Validation issue with fix hint
└── catalog/
    ├── ExampleCatalog.java     # 8 ready-to-use JSON examples
    └── SchemaCatalog.java      # JSON Schema for 4 tools (input + output)
```

---

## 1. Runners

### FlashRunner

Performs thermodynamic flash calculations on a fluid mixture. Accepts JSON, creates the
appropriate `SystemInterface`, runs the flash, and returns a JSON response with per-phase
properties and compositions.

**Entry point:**

```java
String resultJson = FlashRunner.run(inputJson);
```

**Typed entry point:**

```java
ApiEnvelope<FlashResult> envelope = FlashRunner.runTyped(inputJson);
if (envelope.isSuccess()) {
    FlashResult result = envelope.getData();
}
```

#### Input JSON Format

```json
{
  "model": "SRK",
  "temperature": { "value": 25.0, "unit": "C" },
  "pressure": { "value": 50.0, "unit": "bara" },
  "flashType": "TP",
  "components": {
    "methane": 0.85,
    "ethane": 0.10,
    "propane": 0.05
  },
  "mixingRule": "classic"
}
```

#### Input Parameters

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `model` | string | `"SRK"` | Equation of state (see table below) |
| `temperature` | object | — | `{"value": number, "unit": "C"\|"K"\|"F"}` |
| `pressure` | object | — | `{"value": number, "unit": "bara"\|"barg"\|"Pa"\|"kPa"\|"MPa"\|"psi"\|"atm"}` |
| `flashType` | string | `"TP"` | Flash algorithm |
| `components` | object | — | Map of component name to mole fraction |
| `mixingRule` | string | `"classic"` | Mixing rule name |
| `enthalpy` | object | — | Required for PH flash: `{"value": number, "unit": "J/mol"}` |
| `entropy` | object | — | Required for PS flash: `{"value": number, "unit": "J/molK"}` |
| `volume` | object | — | Required for TV flash: `{"value": number, "unit": "m3/mol"}` |

#### Supported Equations of State

| Model | Full Name | Best For |
|-------|-----------|----------|
| `SRK` | Soave-Redlich-Kwong | General hydrocarbon systems (default) |
| `PR` | Peng-Robinson | General purpose, slightly different liquid densities |
| `CPA` | CPA-SRK | Systems with water, methanol, MEG, or other associating fluids |
| `GERG2008` | GERG-2008 | High-accuracy natural gas (reference-quality) |
| `PCSAFT` | PC-SAFT | Polymers, associating fluids |
| `UMRPRU` | UMR-PRU with Mathias-Copeman | Advanced mixing rules |

#### Supported Flash Types

| Flash Type | Description | Required Extra Fields |
|------------|-------------|----------------------|
| `TP` | Temperature-Pressure flash (most common) | — |
| `PH` | Pressure-Enthalpy flash | `enthalpy` |
| `PS` | Pressure-Entropy flash | `entropy` |
| `TV` | Temperature-Volume flash | `volume` |
| `dewPointT` | Dew point temperature at given pressure | — |
| `dewPointP` | Dew point pressure at given temperature | — |
| `bubblePointT` | Bubble point temperature at given pressure | — |
| `bubblePointP` | Bubble point pressure at given temperature | — |
| `hydrateTP` | Hydrate equilibrium temperature at given pressure | — |

#### Temperature Unit Conversion

FlashRunner converts all temperature units to Kelvin internally:

| Input Unit | Conversion |
|------------|------------|
| `C` | K = C + 273.15 |
| `K` | No conversion |
| `F` | K = (F - 32) × 5/9 + 273.15 |

#### Pressure Unit Conversion

| Input Unit | Conversion to bara |
|------------|-------------------|
| `bara` | No conversion |
| `barg` | bara = barg + 1.01325 |
| `Pa` | bara = Pa / 100000 |
| `kPa` | bara = kPa / 100 |
| `MPa` | bara = MPa × 10 |
| `psi` | bara = psi × 0.0689476 |
| `atm` | bara = atm × 1.01325 |

#### Output JSON Structure

The response follows the `FluidResponse` format used by NeqSim's monitoring layer:

```json
{
  "status": "success",
  "flash": {
    "model": "SRK",
    "flashType": "TP",
    "numberOfPhases": 1,
    "phases": ["gas"]
  },
  "fluid": {
    "conditions": {
      "overall": { "temperature": 298.15, "pressure": 50.0 },
      "gas": { "temperature": 298.15, "pressure": 50.0, "molarMass": 0.0178 }
    },
    "properties": {
      "gas": {
        "density": { "value": 38.9, "unit": "kg/m3" },
        "compressibilityFactor": { "value": 0.907, "unit": "" },
        "viscosity": { "value": 1.17e-5, "unit": "Pa·s" },
        "thermalConductivity": { "value": 0.038, "unit": "W/(m·K)" },
        "Cp": { "value": 2350, "unit": "J/(kg·K)" }
      }
    },
    "composition": {
      "gas": {
        "methane": { "value": 0.85 },
        "ethane": { "value": 0.10 },
        "propane": { "value": 0.05 }
      }
    }
  }
}
```

**Important for dew/bubble point calculations:** The converged temperature or pressure
is found in `fluid.conditions.overall.temperature` (Kelvin) or `fluid.conditions.overall.pressure`
(bara), not in a separate field.

---

### ProcessRunner

Runs a process simulation from a JSON definition. Delegates to
`ProcessSystem.fromJsonAndRun()` and returns structured results.

**Entry points:**

```java
// JSON string in, JSON string out
String resultJson = ProcessRunner.run(inputJson);

// Pre-validate then run (merges validation warnings into result)
String resultJson = ProcessRunner.validateAndRun(inputJson);

// Typed API
ApiEnvelope<ProcessResult> envelope = ProcessRunner.runTyped(inputJson);
```

#### Input JSON Format

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
      "properties": { "flowRate": [50000.0, "kg/hr"] }
    },
    {
      "type": "Separator",
      "name": "HP Sep",
      "inlet": "feed"
    },
    {
      "type": "Compressor",
      "name": "Comp",
      "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": [80.0, "bara"] }
    }
  ]
}
```

#### Supported Equipment Types

The Validator recognizes all equipment types supported by `JsonProcessBuilder`:

| Equipment Type | Description |
|---------------|-------------|
| `Stream` | Feed or intermediate stream |
| `Separator` | Two-phase gas/liquid separator |
| `ThreePhaseSeparator` | Three-phase gas/oil/water separator |
| `Compressor` | Gas compressor |
| `Expander` | Gas expander/turbine |
| `Heater` | Heat addition |
| `Cooler` | Heat removal |
| `HeatExchanger` | Two-stream heat exchanger |
| `Mixer` | Stream mixer |
| `Splitter` | Stream splitter |
| `Valve` / `ThrottlingValve` | Throttling valve (isenthalpic) |
| `Pump` | Liquid pump |
| `DistillationColumn` / `Column` | Distillation column |
| `AdiabaticPipe` / `Pipe` / `Pipeline` / `PipeBeggsAndBrills` | Pipe flow |
| `Recycle` | Recycle loop |
| `Adjuster` / `SetPoint` / `GORfitter` | Process adjusters |
| `StreamSaturatorUtil` / `Saturator` | Stream saturation utility |
| `CO2Electrolyzer` | CO2 electrolysis reactor |
| `WindTurbine` / `SolarPanel` / `WindFarm` | Renewable energy equipment |
| `BatteryStorage` | Energy storage |
| `OffshoreEnergySystem` | Offshore energy system |
| `AmmoniaSynthesisReactor` | Ammonia synthesis |
| `SubseaPowerCable` | Subsea power cable |

#### Outlet Port Selectors

When connecting equipment, use dot-notation to select specific outlet streams:

| Port | Description |
|------|-------------|
| `<name>.gasOut` | Gas outlet from separator |
| `<name>.liquidOut` | Liquid outlet from separator |
| `<name>.oilOut` | Oil outlet from three-phase separator |
| `<name>.waterOut` | Water outlet from three-phase separator |

#### Multiple Fluids

Use `"fluids"` (plural) for processes with different fluid compositions:

```json
{
  "fluids": {
    "gas": {
      "model": "SRK",
      "temperature": 298.15,
      "pressure": 50.0,
      "components": { "methane": 0.9, "ethane": 0.1 }
    },
    "oil": {
      "model": "PR",
      "temperature": 350.0,
      "pressure": 100.0,
      "components": { "nC10": 1.0 }
    }
  },
  "process": [
    { "type": "Stream", "name": "gasFeed", "fluidRef": "gas",
      "properties": { "flowRate": [10000.0, "kg/hr"] } },
    { "type": "Stream", "name": "oilFeed", "fluidRef": "oil",
      "properties": { "flowRate": [50000.0, "kg/hr"] } }
  ]
}
```

---

### Validator

Validates a flash or process JSON input **without running any simulation**. Auto-detects
whether the input is a flash definition or a process definition based on the presence
of a `"process"` array.

**Entry point:**

```java
String validationJson = Validator.validate(inputJson);
```

#### Checks Performed

| Check Code | Severity | Description |
|------------|----------|-------------|
| `INPUT_ERROR` | error | JSON is null or empty |
| `JSON_PARSE_ERROR` | error | Malformed JSON |
| `UNKNOWN_MODEL` | error | Unrecognized EOS model |
| `UNKNOWN_FLASH_TYPE` | error | Unrecognized flash type |
| `MISSING_SPEC` | error | PH flash without enthalpy, PS without entropy, TV without volume |
| `NEGATIVE_TEMPERATURE` | error | Temperature below absolute zero (-273.15 °C) |
| `EXTREME_TEMPERATURE` | warning | Temperature outside -200 to 1000 °C |
| `NEGATIVE_PRESSURE` | error | Pressure ≤ 0 |
| `EXTREME_PRESSURE` | warning | Pressure above 1000 bara |
| `UNKNOWN_COMPONENT` | error | Component not in NeqSim database (suggests correction) |
| `COMP_SUM_WARNING` | warning | Mole fractions don't sum to ~1.0 (tolerance: ±0.01) |
| `UNKNOWN_EQUIPMENT_TYPE` | error | Process equipment type not recognized |
| `DUPLICATE_EQUIPMENT_NAME` | warning | Two equipment items with the same name |

#### Output Format

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "error",
      "code": "UNKNOWN_COMPONENT",
      "message": "'metane' is not a known component",
      "fix": "Did you mean 'methane'?"
    },
    {
      "severity": "warning",
      "code": "COMP_SUM_WARNING",
      "message": "Component mole fractions sum to 0.80 (expected ~1.0)",
      "fix": "Normalize fractions to sum to 1.0"
    }
  ]
}
```

---

### ComponentQuery

Searches the NeqSim component database. Uses lazy-loaded caching for fast lookups.

**Entry points:**

```java
// Search by substring (case-insensitive)
String resultsJson = ComponentQuery.search("meth");

// Check if exact name exists
boolean valid = ComponentQuery.isValid("methane");

// Fuzzy match for misspelled names (Levenshtein distance)
String suggestion = ComponentQuery.closestMatch("metane");  // → "methane"

// Get component properties from database
String infoJson = ComponentQuery.getInfo("methane");

// List all component names
List<String> allNames = ComponentQuery.allNames();
```

#### Search Response

```json
{
  "status": "success",
  "query": "meth",
  "matchCount": 4,
  "components": ["methane", "methanol", "dimethylether", "methylamine"]
}
```

#### Component Info Response

```json
{
  "status": "success",
  "component": {
    "name": "methane",
    "formula": "CH4",
    "molarMass_g_mol": 16.043,
    "criticalTemperature_C": -82.55,
    "criticalPressure_bara": 46.0,
    "acentricFactor": 0.0115,
    "normalBoilingPoint_C": -161.5
  }
}
```

---

## 2. Typed Models

### ApiEnvelope

Standard response wrapper used by all runners:

```java
public class ApiEnvelope<T> {
    private String status;          // "success" or "error"
    private T data;                 // The typed result
    private String errorCode;       // Error code (if error)
    private String errorMessage;    // Error description (if error)
    private String fix;             // Remediation hint (if error)
    private List<DiagnosticIssue> warnings;  // Non-fatal warnings
}
```

### FlashRequest

Typed flash input with builder pattern:

```java
FlashRequest request = FlashRequest.builder()
    .model("SRK")
    .temperature(25.0, "C")
    .pressure(50.0, "bara")
    .flashType("TP")
    .component("methane", 0.85)
    .component("ethane", 0.10)
    .component("propane", 0.05)
    .build();
```

### FlashResult

Typed flash output containing phase information and the `FluidResponse`:

```java
FlashResult result = envelope.getData();
String model = result.getModel();
int numPhases = result.getNumberOfPhases();
List<String> phases = result.getPhases();
```

### ProcessResult

Typed process simulation output with equipment-level results.

### ValueWithUnit

Simple holder for a numeric value with its unit:

```java
ValueWithUnit density = new ValueWithUnit(38.9, "kg/m3");
double value = density.getValue();
String unit = density.getUnit();
```

### DiagnosticIssue

Validation issue with severity, error code, message, and fix suggestion:

```java
DiagnosticIssue issue = DiagnosticIssue.error(
    "UNKNOWN_COMPONENT",
    "'metane' is not a known component",
    "Did you mean 'methane'?"
);
```

---

## 3. Catalogs

### ExampleCatalog

Provides 8 ready-to-use JSON examples that LLMs use to learn the input format:

| Category | Name | Description |
|----------|------|-------------|
| `flash` | `tp-simple-gas` | TP flash of a simple natural gas |
| `flash` | `tp-two-phase` | TP flash producing gas + liquid phases |
| `flash` | `dew-point-t` | Dew point temperature calculation |
| `flash` | `bubble-point-p` | Bubble point pressure calculation |
| `flash` | `cpa-with-water` | CPA EOS flash with water |
| `process` | `simple-separation` | Stream → Separator |
| `process` | `compression-with-cooling` | Stream → Compressor → Cooler |
| `validation` | `error-flash` | Deliberately invalid flash input |

**Usage:**

```java
String example = ExampleCatalog.getExample("flash", "tp-simple-gas");
String catalogJson = ExampleCatalog.getCatalogJson();
```

### SchemaCatalog

Provides JSON Schema (Draft 2020-12) definitions for each tool's input and output:

| Tool | Schema Types |
|------|-------------|
| `run_flash` | `input`, `output` |
| `run_process` | `input`, `output` |
| `validate_input` | `input`, `output` |
| `search_components` | `input`, `output` |

**Usage:**

```java
String schema = SchemaCatalog.getSchema("run_flash", "input");
String catalogJson = SchemaCatalog.getCatalogJson();
```

---

## 4. Test Coverage

The MCP core layer has 139+ JUnit 5 tests across 12 test classes:

| Test Class | Description |
|------------|-------------|
| `FlashRunnerTest` | TP flash, multi-phase, all EOS models |
| `FlashRunnerDewBubbleTest` | Dew point and bubble point calculations |
| `FlashRunnerSpecialFlashTest` | PH, PS, TV flash types |
| `FlashRunnerEdgeCasesTest` | Error handling, invalid inputs |
| `FlashRunnerUnitConversionTest` | Temperature and pressure unit conversion |
| `ProcessRunnerTest` | Simple and multi-unit process simulations |
| `ProcessRunnerValidateAndRunTest` | Combined validation + run workflow |
| `ValidatorTest` | All 12+ validation check codes |
| `ComponentQueryTest` | Search, isValid, closestMatch, getInfo |
| `ExampleCatalogTest` | All examples parse and contain required fields |
| `SchemaCatalogTest` | All schemas are valid JSON Schema |
| `ModelClassTest` | ApiEnvelope, FlashRequest builder, ValueWithUnit |

**Run all MCP tests from the NeqSim root:**

```bash
# Linux/macOS
./mvnw test -Dtest="neqsim.mcp.**"

# Windows
.\mvnw.cmd test -Dtest="neqsim.mcp.**"
```

---

## 5. Design Principles

1. **Stateless** — Every call is independent. No session state, no shared mutable fields.
   All runners use static methods.

2. **JSON-in, JSON-out** — Input and output are always JSON strings. This makes the layer
   transport-agnostic. The same runner works with MCP STDIO, HTTP REST, WebSocket, or CLI.

3. **Framework-agnostic** — Zero dependencies on Quarkus, Spring, or any web framework.
   The layer depends only on NeqSim core and Gson.

4. **Self-describing** — The `ExampleCatalog` and `SchemaCatalog` mean any LLM can discover
   the correct JSON format without external documentation.

5. **Defensive validation** — The `Validator` catches common mistakes (misspelled components,
   wrong units, missing specs) before expensive simulation runs, with actionable fix hints.

6. **Standard envelope** — All responses use the same `ApiEnvelope` structure with `status`,
   `data`, `warnings`, and error details, making response parsing predictable.

---

## Related Documentation

- [MCP Server Guide](mcp_server_guide) — Quarkus-based MCP server that wraps this layer
- [Web API / JSON Process Builder](web_api_json_process_builder) — JSON process definition format
- [AI Validation Framework](ai_validation_framework) — NeqSim's broader validation system
- [AI Agents Reference](ai_agents_reference) — Full catalog of NeqSim AI agents
