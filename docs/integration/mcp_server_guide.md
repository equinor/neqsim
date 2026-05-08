---
title: "NeqSim MCP Server: LLM-Powered Thermodynamics"
description: "Complete guide to the NeqSim MCP Server — a Quarkus-based Model Context Protocol server that gives any LLM (VS Code Copilot, Claude Desktop, Cursor) the ability to run rigorous thermodynamic flash calculations and process simulations through NeqSim. Covers installation, configuration, tools, resources, example conversations, testing, and troubleshooting."
---

# NeqSim MCP Server: LLM-Powered Thermodynamics

The NeqSim MCP Server is a [Model Context Protocol](https://modelcontextprotocol.io/) server
that gives any LLM the ability to run rigorous thermodynamic calculations and process
simulations through NeqSim. It ships as a single uber-jar (~55 MB) and communicates
via STDIO transport for local clients or Streamable HTTP for web-based clients.

## What Can an LLM Do With This?

| Capability | Example Prompt |
|---|---|
| **Flash calculations** | "What is the dew point temperature of 85% methane, 10% ethane, 5% propane at 50 bara?" |
| **Phase equilibrium** | "How many phases exist for this rich gas at 0 °C and 100 bara?" |
| **Physical properties** | "Get the density, viscosity, and thermal conductivity of natural gas at 25 °C, 80 bara" |
| **Process simulation** | "Simulate gas going through a separator then a compressor to 120 bara" |
| **Input validation** | "Check if my process JSON is valid before running it" |
| **Component lookup** | "What components does NeqSim have that contain 'butane'?" |

The LLM discovers the tools automatically via MCP, reads the embedded examples and schemas
to learn the JSON format, and then calls the tools to compute answers with rigorous
thermodynamic models.

---

## Architecture

```
neqsim-mcp-server/                         # Separate Maven project (Java 17+)
├── pom.xml                                 # Quarkus 3.33.1 + MCP Server 1.12.0
├── test_mcp_server.py                      # Comprehensive integration test suite
└── src/main/java/neqsim/mcp/server/
    ├── NeqSimTools.java                    # 56 @Tool MCP tools
    ├── NeqSimResources.java                # 6 @Resource + 5 @ResourceTemplate
    └── NeqSimPrompts.java                  # 9 @Prompt guided workflows

Delegates to the framework-agnostic core layer in neqsim (neqsim.mcp.*):
├── runners/   → FlashRunner, ProcessRunner, Validator, ComponentQuery
├── model/     → ApiEnvelope, FlashRequest, FlashResult, ValueWithUnit, DiagnosticIssue
└── catalog/   → ExampleCatalog and SchemaCatalog for examples and JSON schemas
```

The MCP server is a **thin Quarkus wrapper** around a framework-agnostic
runner layer in neqsim core. This means:

- **Stability** — Runners are tested with 139+ JUnit tests in the neqsim project
- **Portability** — Runners can be reused with any MCP framework, REST API, or CLI
- **Separation** — The server can be extracted to a standalone repository

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17+ | Quarkus requires Java 17. NeqSim core compiles with Java 8. |
| Maven | 3.9+ | Or use the Maven wrapper (`mvnw`/`mvnw.cmd`) from the parent project |
| NeqSim core | 3.9.1+ | Must be installed to local Maven repo first |

---

## Installation

### Step 1: Install NeqSim Core

From the **parent neqsim directory**:

```bash
# Linux / macOS
./mvnw install -DskipTests -Dmaven.javadoc.skip=true

# Windows
.\mvnw.cmd install -DskipTests "-Dmaven.javadoc.skip=true"
```

This installs `com.equinor.neqsim:neqsim:3.9.1` to your local `~/.m2/repository`.

### Step 2: Build the MCP Server

```bash
cd neqsim-mcp-server

# Linux / macOS
../mvnw package -DskipTests -Dmaven.javadoc.skip=true

# Windows
..\mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true"
```

Output: `target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` (~55 MB uber-jar)

### Step 3: Verify

**Quick STDIO test** — send an MCP initialize request:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' \
  | java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar 2>/dev/null
```

Expected: a JSON-RPC response with `serverInfo.name: "neqsim"` and tool capabilities.

---

## Connecting to LLM Clients

### VS Code Copilot

Add to `.vscode/mcp.json` in your workspace:

```json
{
  "servers": {
    "neqsim": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "${workspaceFolder}/neqsim-mcp-server/target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"
      ]
    }
  }
}
```

Restart VS Code. Copilot will discover the NeqSim tools automatically.

### Streamable HTTP Clients

For web-based MCP clients, start the server normally and connect to
`http://localhost:8080/mcp`. The Quarkiverse HTTP transport also exposes the
legacy HTTP/SSE endpoint at `http://localhost:8080/mcp/sse` for older clients.

### Claude Desktop

Edit the config file:
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "neqsim": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/neqsim-mcp-server/target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"
      ]
    }
  }
}
```

Restart Claude Desktop to activate.

### Cursor / Other MCP Clients

Any client supporting MCP STDIO transport can connect:

```bash
java -jar /path/to/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

### MCP Inspector (Development)

For interactive testing with Node.js:

```bash
npx @modelcontextprotocol/inspector java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

---

## MCP Tools Reference

The server exposes 6 tools via the Model Context Protocol.

### 1. `runFlash` — Thermodynamic Flash Calculation

Computes phase equilibrium for a fluid mixture. Returns per-phase densities, viscosities,
thermal conductivities, heat capacities, compressibility factors, and compositions.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `components` | JSON string | Yes | Component-to-mole-fraction map |
| `temperature` | number | Yes | Temperature value |
| `temperatureUnit` | string | Yes | `C`, `K`, or `F` |
| `pressure` | number | Yes | Pressure value |
| `pressureUnit` | string | Yes | `bara`, `barg`, `Pa`, `kPa`, `MPa`, `psi`, or `atm` |
| `eos` | string | Yes | Equation of state model |
| `flashType` | string | Yes | Flash type algorithm |

**Supported EOS models:** `SRK`, `PR`, `CPA`, `GERG2008`, `PCSAFT`, `UMRPRU`

**Supported flash types:** `TP`, `PH`, `PS`, `TV`, `dewPointT`, `dewPointP`, `bubblePointT`, `bubblePointP`, `hydrateTP`

**Example MCP tool call:**

```json
{
  "method": "tools/call",
  "params": {
    "name": "runFlash",
    "arguments": {
      "components": "{\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}",
      "temperature": 25.0,
      "temperatureUnit": "C",
      "pressure": 50.0,
      "pressureUnit": "bara",
      "eos": "SRK",
      "flashType": "TP"
    }
  }
}
```

**Example response (abbreviated):**

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
      "overall": { "temperature": 298.15, "pressure": 50.0 }
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

**Dew/bubble point results:** The converged temperature appears in
`fluid.conditions.overall.temperature` (Kelvin). Subtract 273.15 for Celsius.

### 2. `runProcess` — Process Simulation

Builds and runs a process flowsheet from a JSON definition using `ProcessSystem.fromJsonAndRun()`.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `processJson` | JSON string | Yes | Complete process definition with fluid and equipment array |

**Process JSON structure:**

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
    { "type": "Stream", "name": "feed",
      "properties": { "flowRate": [50000.0, "kg/hr"] } },
    { "type": "Separator", "name": "HP Sep", "inlet": "feed" },
    { "type": "Compressor", "name": "Comp", "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": [80.0, "bara"] } }
  ]
}
```

**Supported equipment:** Stream, Separator, ThreePhaseSeparator, Compressor, Expander,
Heater, Cooler, HeatExchanger, Mixer, Splitter, Valve, Pump, DistillationColumn,
AdiabaticPipe, PipeBeggsAndBrills, Recycle, Adjuster, and more.

**Outlet port selectors:** `<name>.gasOut`, `<name>.liquidOut`, `<name>.oilOut`, `<name>.waterOut`

**Multiple fluids:** Use `"fluids"` (plural) with `"fluidRef"` on each stream.

### 3. `validateInput` — Pre-flight Validation

Validates a flash or process JSON before running. Auto-detects input type.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `inputJson` | JSON string | Yes | Flash or process JSON to validate |

**Checks performed:** component names, temperature/pressure ranges, EOS model, flash type,
required specs (enthalpy for PH, etc.), composition sum, equipment types, duplicate names.

**Response format:**

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "error",
      "code": "UNKNOWN_COMPONENT",
      "message": "'metane' is not a known component",
      "fix": "Did you mean 'methane'?"
    }
  ]
}
```

### 4. `searchComponents` — Component Database Search

Searches the NeqSim component database by substring match (case-insensitive).

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | string | Yes | Component name or partial name. Empty string returns all. |

**Example:** `query: "meth"` returns `["methane", "methanol", "dimethylether", ...]`

### 5. `getExample` — Example Templates

Returns ready-to-use JSON templates for tools.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `category` | string | Yes | `flash`, `process`, or `validation` |
| `name` | string | Yes | Example name (see table below) |

**Available examples:**

| Category | Name | Description |
|----------|------|-------------|
| `flash` | `tp-simple-gas` | TP flash of a simple natural gas |
| `flash` | `tp-two-phase` | TP flash producing gas + liquid phases |
| `flash` | `dew-point-t` | Dew point temperature calculation |
| `flash` | `bubble-point-p` | Bubble point pressure calculation |
| `flash` | `cpa-with-water` | CPA EOS flash with water |
| `process` | `simple-separation` | Stream → Separator |
| `process` | `compression-with-cooling` | Stream → Compressor → Cooler |
| `validation` | `error-flash` | Deliberately invalid input |

### 6. `getSchema` — JSON Schemas

Returns JSON Schema (Draft 2020-12) definitions for tool inputs and outputs.

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `toolName` | string | Yes | `run_flash`, `run_process`, `validate_input`, or `search_components` |
| `schemaType` | string | Yes | `input` or `output` |

---

## MCP Resources

The server also exposes static resources and resource templates:

| Type | URI | Description |
|------|-----|-------------|
| Resource | `neqsim://example-catalog` | Full catalog of all examples with descriptions |
| Resource | `neqsim://schema-catalog` | Full catalog of all JSON schemas |
| Template | `neqsim://examples/{category}/{name}` | Specific example by category and name |
| Template | `neqsim://schemas/{tool}/{type}` | Specific schema by tool and type |

---

## How the LLM Uses the Server

A typical interaction follows this flow:

```
1. DISCOVERY     → LLM calls tools/list, finds 6 tools, reads descriptions
2. LEARNING      → LLM calls getExample or getSchema to learn JSON format
3. VALIDATION    → LLM calls validateInput to catch errors early (optional)
4. COMPUTATION   → LLM calls runFlash or runProcess with constructed JSON
5. INTERPRETATION → LLM reads JSON response, presents results in natural language
```

### Chat Example 1: Gas Density Lookup

> **User:** What is the density of natural gas (90% methane, 10% ethane) at 80 bara and 35 °C?

The LLM calls `runFlash` behind the scenes:

```json
{
  "components": "{\"methane\": 0.90, \"ethane\": 0.10}",
  "temperature": 35.0, "temperatureUnit": "C",
  "pressure": 80.0, "pressureUnit": "bara",
  "eos": "SRK", "flashType": "TP"
}
```

> **Assistant:** The gas density is **62.3 kg/m3** at 80 bara and 35 °C.
>
> | Property | Value | Unit |
> |---|---|---|
> | Density | 62.3 | kg/m3 |
> | Z-factor | 0.88 | — |
> | Viscosity | 1.3 × 10⁻⁵ | Pa·s |
> | Thermal conductivity | 0.042 | W/(m·K) |
> | Cp | 2580 | J/(kg·K) |
>
> The fluid is single-phase gas at these conditions (SRK equation of state).

---

### Chat Example 2: Dew Point Temperature

> **User:** What is the dew point of this gas at 50 bara?
> Composition: 85% methane, 10% ethane, 5% propane.

The LLM calls `runFlash` with `flashType: "dewPointT"`:

```json
{
  "components": "{\"methane\": 0.85, \"ethane\": 0.10, \"propane\": 0.05}",
  "temperature": 25.0, "temperatureUnit": "C",
  "pressure": 50.0, "pressureUnit": "bara",
  "eos": "SRK", "flashType": "dewPointT"
}
```

The converged dew point temperature is in `fluid.conditions.overall.temperature` (Kelvin).

> **Assistant:** The **dew point temperature is 8.2 °C** at 50 bara (SRK EOS).
>
> Below this temperature, liquid will start condensing from the gas. This is
> important for pipeline design — if the gas cools below 8 °C at 50 bara,
> you will get two-phase flow.
>
> Want me to check the bubble point or calculate properties at a different pressure?

---

### Chat Example 3: Component Lookup

> **User:** Does NeqSim have data for butane? What's the exact name I should use?

The LLM calls `searchComponents` with `query: "butane"`:

> **Assistant:** NeqSim has these butane-related components:
>
> | Component | Description |
> |---|---|
> | `n-butane` | Normal butane (C4H10) |
> | `i-butane` | Isobutane (C4H10) |
>
> Use `n-butane` for normal butane and `i-butane` for isobutane in your
> compositions. Note: `nC4` or `isobutane` won't work — the exact names above
> are what NeqSim expects.

---

### Chat Example 4: Process Simulation

> **User:** Simulate a gas separation process: feed gas at 50 bara and 25 °C
> goes into a separator, then compress the gas to 120 bara. Use 80% methane,
> 10% ethane, 5% propane, 5% n-butane. Flow rate is 50000 kg/hr.

The LLM first calls `getExample("process", "compression-with-cooling")` to learn the
format, then constructs and calls `runProcess`:

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "mixingRule": "classic",
    "components": {
      "methane": 0.80,
      "ethane": 0.10,
      "propane": 0.05,
      "n-butane": 0.05
    }
  },
  "process": [
    { "type": "Stream", "name": "feed",
      "properties": { "flowRate": [50000.0, "kg/hr"] } },
    { "type": "Separator", "name": "HP Sep", "inlet": "feed" },
    { "type": "Compressor", "name": "Gas Compressor",
      "inlet": "HP Sep.gasOut",
      "properties": { "outletPressure": [120.0, "bara"] } }
  ]
}
```

> **Assistant:** Here are the simulation results:
>
> **Separator (HP Sep) at 50 bara, 25 °C:**
>
> | Stream | Flow Rate (kg/hr) | Temperature (°C) | Pressure (bara) |
> |---|---|---|---|
> | Feed | 50,000 | 25.0 | 50.0 |
> | Gas out | 47,800 | 25.0 | 50.0 |
> | Liquid out | 2,200 | 25.0 | 50.0 |
>
> About 4.4% of the feed condenses as liquid (mostly heavier components).
>
> **Compressor (Gas Compressor):**
>
> | Property | Value | Unit |
> |---|---|---|
> | Outlet pressure | 120.0 | bara |
> | Outlet temperature | ~105 | °C |
> | Power consumption | ~1,850 | kW |
>
> The discharge temperature is quite high — you may want to add an aftercooler.
> Want me to add a cooler after the compressor?

---

### Chat Example 5: Input Validation

> **User:** Check this flash input for errors:
> ```json
> {"model": "SRK", "components": {"metane": 0.85, "ethane": 0.15},
>  "temperature": {"value": 25, "unit": "C"},
>  "pressure": {"value": 50, "unit": "bara"}, "flashType": "TP"}
> ```

The LLM calls `validateInput`:

> **Assistant:** I found one error in your input:
>
> | Severity | Issue | Fix |
> |---|---|---|
> | ❌ Error | `metane` is not a known component | Did you mean **methane**? |
>
> Fix the component name from `"metane"` to `"methane"` and it will work.

---

### Chat Example 6: Multi-Step Engineering Workflow

> **User:** I have a rich gas coming from a well at 150 bara and 80 °C. Composition:
> 70% methane, 12% ethane, 8% propane, 5% n-butane, 3% n-pentane, 2% CO2.
> I need to know: (1) how many phases at wellhead conditions, (2) the dew point
> at 80 bara (export pipeline pressure), and (3) what happens after cooling to 20 °C
> in a separator at 80 bara.

> **Assistant:** I'll answer all three questions using NeqSim. Let me run the calculations...
>
> **1. Phase check at wellhead (150 bara, 80 °C):**
>
> Single gas phase. The fluid is above its dew point at these conditions.
>
> **2. Dew point at 80 bara (export pipeline):**
>
> The **dew point temperature is 42.5 °C** at 80 bara. If the pipeline operates
> below 42.5 °C, liquid will condense. Since export pipelines on the seabed can
> reach 4-10 °C, you will definitely have two-phase flow.
>
> **3. Separation at 80 bara, 20 °C:**
>
> | Stream | Flow Rate | Methane (mol%) | C3+ (mol%) |
> |---|---|---|---|
> | Gas out | 87% of feed | 78.5% | 8.2% |
> | Liquid out | 13% of feed | 25.1% | 52.3% |
>
> About 13% of the feed condenses as NGL-rich liquid. The liquid contains
> most of the propane, butane, and pentane — this could be valuable as a
> separate NGL product.
>
> Would you like me to add a second-stage separator or check hydrate
> formation temperatures?

---

### Chat Example 7: CPA Equation of State for Water Systems

> **User:** I need to check how much water dissolves in natural gas at 100 bara and 40 °C.
> Gas is 95% methane, 5% CO2. Use CPA since there's water.

The LLM uses `eos: "CPA"` which is the correct choice for associating fluids:

> **Assistant:** Using the CPA (Cubic Plus Association) equation of state, which properly
> models water's hydrogen bonding behavior:
>
> **At 100 bara, 40 °C (95% CH4, 5% CO2, saturated with water):**
>
> | Property | Value | Unit |
> |---|---|---|
> | Water content in gas | ~850 | ppm (molar) |
> | Water content in gas | ~0.55 | g/Sm3 |
> | Number of phases | 2 | gas + aqueous |
>
> The gas is saturated with about 850 ppm water at these conditions. For a typical
> pipeline spec of 50 mg/Sm3, you would need a dehydration unit (TEG or molecular
> sieves) to remove most of this water.
>
> CPA is the right EOS choice here — SRK or PR would significantly underpredict
> the water content in the gas phase.

---

## Testing

### Integration Test Suite

The `test_mcp_server.py` script launches the server as a subprocess, communicates
over STDIO, and validates protocol behavior, catalogs, core calculations,
advanced tools, governance, and error handling:

| Category | Checks | Description |
|---|---|---|
| Protocol | 9 | Tool/resource/template registration |
| Component search | 9 | Exact, partial, empty, no-match queries |
| Examples and schemas | 10 | Catalog retrieval and format validation |
| Flash calculations | 30 | SRK, PR, CPA; single/two-phase; density, Z, viscosity |
| Dew/bubble point | 6 | All 4 saturation flash types |
| Process simulation | 13 | Separator, compressor, cooler, heater, valve, multi-unit |
| Validation | 22 | Valid input, unknown components, bad models, missing specs |
| Error handling | 2 | Graceful failure on malformed input |
| Catalog round-trip | 10 | All examples run end-to-end through the server |

**Run:**

```bash
cd neqsim-mcp-server
python test_mcp_server.py
```

### Unit Tests (Core Layer)

The runner layer in neqsim core has 139+ JUnit 5 tests:

```bash
# From neqsim root
./mvnw test -Dtest="neqsim.mcp.**"
```

---

## Troubleshooting

### "Could not find artifact com.equinor.neqsim:neqsim:3.9.1"

Install NeqSim core first:

```bash
cd ..  # parent neqsim directory
./mvnw install -DskipTests -Dmaven.javadoc.skip=true
```

### "No matching toolchain found for JDK 17+"

The MCP server requires JDK 17+. Check with `java -version`. The parent neqsim
project compiles with Java 8 — both can coexist.

### Server Hangs or Returns Garbled Output

STDIO transport uses stdin/stdout for JSON-RPC. All logs go to stderr.
- Check no library writes to `System.out`
- Logging is set to WARN (default in `application.properties`)
- Send one JSON-RPC message per line

### "Unknown component" Errors

NeqSim uses specific component names. Use `searchComponents` to find them:
- `n-heptane` (not `nC7`)
- `i-butane` (not `isobutane`)
- `CO2` (not `carbon dioxide`)
- `H2S` (not `hydrogen sulfide`)

### LLM Doesn't See the Tools

1. Ensure the JAR is built: check `target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` exists
2. Verify `.vscode/mcp.json` (or equivalent config) points to the correct jar path
3. Restart VS Code / Claude Desktop after configuration changes
4. Open a **new** chat session — existing sessions don't see newly added MCP servers

---

## Related Documentation

- [MCP Core Layer](mcp_neqsim_core_layer) — Framework-agnostic runners, models, and catalogs in neqsim core
- [Web API / JSON Process Builder](web_api_json_process_builder) — JSON process definition format and session management
- [AI Agents Reference](ai_agents_reference) — Full catalog of NeqSim AI agents and skills
- [Model Context Protocol specification](https://spec.modelcontextprotocol.io/)
- [Quarkus MCP Server extension](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
