# NeqSim MCP — Model Context Protocol Integration Layer

This package provides a **framework-agnostic** runner layer that enables
any MCP server, REST API, or CLI to perform NeqSim thermodynamic calculations
and process simulations through simple JSON-in / JSON-out interfaces.

## Package Structure

```
neqsim.mcp
├── runners/       Stateless calculation engines (JSON string → JSON string)
│   ├── FlashRunner        Flash / phase equilibrium calculations
│   ├── ProcessRunner      Process simulation (flowsheets)
│   ├── Validator          Pre-flight input validation
│   ├── ComponentQuery     Component database search & fuzzy matching
│   └── CapabilitiesRunner Capability descriptors and setup templates
│
├── model/         Typed Java POJOs for request/response objects
│   ├── ApiEnvelope<T>     Standard response wrapper and MCP contract fields
│   ├── FlashRequest       Typed flash input (model, T, P, components, flashType)
│   ├── FlashResult        Typed flash output (phases, properties, compositions)
│   ├── ProcessResult      Typed process output (report, process system ref)
│   ├── ResultProvenance   Model, convergence, limitations, and trust metadata
│   ├── ValueWithUnit      Numeric value with unit string (e.g. 25.0 "C")
│   └── DiagnosticIssue    Validation issue with severity, code, and fix hint
│
└── catalog/       Example inputs and JSON schemas for MCP resources
  ├── ExampleCatalog     Canonical examples for base and schema-backed tools
  └── SchemaCatalog      JSON Schema definitions for schema-backed tool contracts
```

## Design Principles

### 1. Framework-Agnostic

Every runner is a pure static class with a `run(String json)` method. There are
**no dependencies on Quarkus, Spring, or any MCP framework**. This means the
same runner code works in:

- The Quarkus MCP server (`neqsim-mcp-server/`)
- A future Spring Boot REST API
- A standalone CLI tool
- Direct Java calls in unit tests

### 2. Dual Interface — String and Typed

Each runner supports two interfaces:

```java
// String-based (for MCP tools, REST handlers)
String json = FlashRunner.run(inputJson);

// Typed (for direct Java consumers)
ApiEnvelope<FlashResult> result = FlashRunner.runTyped(request);
FlashResult flash = result.getData();
int nPhases = flash.getNumberOfPhases();
```

### 3. Standard Response Envelope

All runners return responses in a consistent envelope format:

```json
{
  "apiVersion": "1.0",
  "status": "success",
  "tool": "run_flash",
  "data": { ... },
  "flash": { ... },
  "fluid": { ... },
  "provenance": { ... },
  "validation": { "valid": true, "phase": "runner", "message": "..." },
  "qualityGate": { "verdict": "passed", "engineeringReviewRequired": true },
  "warnings": []
}
```

Or on error:

```json
{
  "apiVersion": "1.0",
  "status": "error",
  "tool": "run_flash",
  "errors": [{
    "severity": "error",
    "code": "UNKNOWN_COMPONENT",
    "message": "'metane' is not a known component. Did you mean 'methane'?",
    "remediation": "Check spelling. Use searchComponents to find valid names."
  }],
  "validation": { "valid": false, "phase": "runner", "message": "..." },
  "qualityGate": { "verdict": "failed", "engineeringReviewRequired": true },
  "warnings": []
}
```

Legacy top-level fields such as `flash`, `fluid`, or `process` remain available for
backward compatibility. New clients should prefer the canonical `data` payload and use
`provenance`, `validation`, and `qualityGate` when deciding how much trust to place in a result.

### 4. Comprehensive Validation

The `Validator` class catches configuration errors **before** they trigger
cryptic simulation failures. It checks:

| Check | Code | Severity |
|---|---|---|
| Component name exists | `UNKNOWN_COMPONENT` | error |
| EOS model recognized | `UNKNOWN_MODEL` | error |
| Flash type valid | `UNKNOWN_FLASH_TYPE` | error |
| Required spec present (PH/PS/TV) | `MISSING_SPEC` | error |
| Negative mole fraction | `NEGATIVE_FRACTION` | error |
| Equipment type recognized | `UNKNOWN_EQUIPMENT_TYPE` | error |
| Equipment missing `type` field | `MISSING_TYPE` | error |
| Duplicate equipment names | `DUPLICATE_NAME` | error |
| Composition sum ≠ 1.0 | `COMPOSITION_SUM` | warning |
| Temperature out of range | `TEMPERATURE_RANGE` | warning |
| Pressure out of range | `PRESSURE_RANGE` | warning |

## Runners

### FlashRunner

Computes phase equilibrium for a fluid mixture given thermodynamic conditions.

**Supported models:** SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU

**Supported flash types:** TP, PH, PS, TV, dewPointT, dewPointP, bubblePointT,
bubblePointP, hydrateTP

**Input JSON:**

```json
{
  "model": "SRK",
  "temperature": { "value": 25.0, "unit": "C" },
  "pressure": { "value": 50.0, "unit": "bara" },
  "flashType": "TP",
  "components": { "methane": 0.85, "ethane": 0.10, "propane": 0.05 },
  "mixingRule": "classic"
}
```

**Output includes (per phase):** density, viscosity, thermal conductivity,
Cp, Cv, compressibility factor, molar mass, speed of sound, JT coefficient,
and component mole fractions.

### ProcessRunner

Builds and runs a flowsheet from a JSON definition using `ProcessSystem.fromJsonAndRun()`.

**Supported equipment types:** Stream, Separator, Compressor, Cooler, Heater,
Valve, Mixer, Splitter, HeatExchanger, DistillationColumn, Pipe

**Input JSON:**

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "mixingRule": "classic",
    "components": { "methane": 0.85, "ethane": 0.10, "propane": 0.05 }
  },
  "process": [
    { "type": "Stream", "name": "feed", "properties": { "flowRate": [50000.0, "kg/hr"] } },
    { "type": "Separator", "name": "HP Sep", "inlet": "feed" },
    { "type": "Compressor", "name": "Comp", "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": [80.0, "bara"] } }
  ]
}
```

Equipment connections use dot-notation port selectors: `HP Sep.gasOut`,
`HP Sep.liquidOut`, `HP Sep.oilOut`, `HP Sep.waterOut`.

### ComponentQuery

Searches the NeqSim component database for valid component names. Supports:
- Exact match: `exists("methane")` → `true`
- Partial match: `search("meth")` → `["methane", "methanol", ...]`
- Fuzzy suggestions: `suggest("metane")` → `["methane"]`

### Validator

Pre-flight validation for flash and process JSON inputs. Auto-detects input
type (flash vs. process) based on the presence of `"fluid"` / `"process"` keys.

## Model Classes

| Class | Purpose |
|---|---|
| `ApiEnvelope<T>` | Generic wrapper with `apiVersion`, `tool`, `data`, `provenance`, `validation`, `qualityGate`, `warnings`, and `errors` |
| `FlashRequest` | Builder-pattern flash input with defaults (SRK, TP, 15°C, 1 atm) |
| `FlashResult` | Flash output: model, flashType, numberOfPhases, phases, FluidResponse |
| `ProcessResult` | Process output: name, ProcessSystem reference, report JSON |
| `ResultProvenance` | Calculation provenance, convergence, assumptions, limitations, and trust metadata |
| `ValueWithUnit` | A double value with a unit string (e.g. `50.0 "bara"`) |
| `DiagnosticIssue` | Validation issue with `severity`, `code`, `message`, `remediation` |

## Catalog Classes

| Class | Purpose |
|---|---|
| `ExampleCatalog` | Canonical and contract-level examples across flash, process, validation, safety, and all MCP tool categories |
| `SchemaCatalog` | JSON Schema (Draft 2020-12) for all 56 MCP tool input and output contracts; high-use tools have detailed schemas and remaining tools have generic contract schemas |
| `CapabilitiesRunner` | Machine-readable capability map, setup templates, process JSON contract, unit system, benchmark trust, lifecycle metadata, safety gates, validation coverage, and response-contract coverage |

These are served as MCP Resources so LLMs can read them to learn the format:

| URI Pattern | Example |
|---|---|
| `neqsim://example-catalog` | Full example listing |
| `neqsim://examples/{category}/{name}` | `neqsim://examples/flash/tp-simple-gas` |
| `neqsim://setup-templates` | Full setup-template listing |
| `neqsim://setup-templates/{id}` | `neqsim://setup-templates/thermodynamic-flash` |
| `neqsim://schemas/{tool}/input` | `neqsim://schemas/run_flash/input` |
| `neqsim://schemas/{tool}/output` | `neqsim://schemas/run_flash/output` |
| `neqsim://schema-catalog` | Full schema listing |

## Test Coverage

Focused JUnit 5 tests cover the MCP runners, models, catalogs, schemas, examples,
capability descriptors, response contracts, and validation behavior:

| Test Class | Tests | What It Covers |
|---|---|---|
| `FlashRunnerTest` | ~25 | TP/PH/PS/dew/bubble, all EOS, error cases |
| `FlashRunnerTypedTest` | ~15 | Typed `runTyped()` API, FlashRequest builder |
| `ProcessRunnerTest` | ~15 | Separator, compressor, cooler, valve, multi-unit |
| `ProcessRunnerTypedTest` | ~10 | Typed process API, ProcessResult |
| `ValidatorTest` | ~25 | All validation codes, edge cases, error combinations |
| `ComponentQueryTest` | ~15 | Search, exists, suggest, fuzzy matching |
| `ExampleCatalogTest` | ~10 | All examples parse and run successfully |
| `SchemaCatalogTest` | ~10 | All advertised tools have input and output schemas |
| `CapabilitiesRunnerTest` | ~4 | Capability descriptors resolve schemas, examples, setup templates, validation metadata, and response contracts |
| `McpRunnerContractTest` | ~1 | Standard response-contract fixture |
| `ApiEnvelopeTest` | ~5 | Envelope serialization, success/error factories |
| `FlashRequestTest` | ~5 | Builder, defaults, JSON round-trip |
| `DiagnosticIssueTest` | ~3 | Issue creation and serialization |
| `ValueWithUnitTest` | ~3 | Value creation, unit handling |

Run all MCP tests:

```bash
./mvnw test -Dtest="neqsim.mcp.**"
```

## Usage Examples

### From Java (Direct)

```java
// Flash calculation
String input = "{\"model\":\"SRK\",\"temperature\":{\"value\":25.0,\"unit\":\"C\"},"
    + "\"pressure\":{\"value\":50.0,\"unit\":\"bara\"},"
    + "\"flashType\":\"TP\",\"components\":{\"methane\":0.85,\"ethane\":0.15}}";
String result = FlashRunner.run(input);

// Typed API
FlashRequest request = new FlashRequest()
    .model("SRK").flashType("TP")
    .temperature(25.0, "C").pressure(50.0, "bara")
    .component("methane", 0.85).component("ethane", 0.15);
ApiEnvelope<FlashResult> envelope = FlashRunner.runTyped(request);
```

### From the MCP Server

The `neqsim-mcp-server/` project wraps these runners in Quarkus `@Tool`
annotations. See `neqsim-mcp-server/README.md` for configuration and usage.

### From Python (via jpype)

```python
import jpype
FlashRunner = jpype.JClass("neqsim.mcp.runners.FlashRunner")
result = FlashRunner.run('{"model":"SRK","temperature":{"value":25,"unit":"C"},...}')
```
