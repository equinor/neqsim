# NeqSim MCP Server

A [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) server that gives any
LLM — VS Code Copilot, Claude Desktop, Cursor, or any MCP-compatible client — the
ability to run rigorous thermodynamic calculations and process simulations through
[NeqSim](https://github.com/equinor/neqsim), an open-source Java library for
oil & gas thermodynamics.

Built with [Quarkus MCP Server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
(STDIO transport). Ships as a single uber-jar (~55 MB) — no extra services needed.

## What Can an LLM Do With This?

| Capability | Example Prompt | Tool |
|---|---|---|
| **Flash calculations** | "What is the dew point temperature of 85% methane, 10% ethane, 5% propane at 50 bara?" | `runFlash` |
| **Batch sensitivity** | "How does density change from 0 to 50 °C at 80 bara? Give me 10 data points." | `runBatch` |
| **Property table** | "Get density, viscosity, Cp, and Z-factor from 10 to 100 bara at 25 °C" | `getPropertyTable` |
| **Phase envelope** | "Plot the phase envelope for this natural gas composition" | `getPhaseEnvelope` |
| **Process simulation** | "Simulate a gas going through a separator then a compressor to 120 bara" | `runProcess` |
| **Input validation** | "Check if my process JSON is valid before running it" | `validateInput` |
| **Component lookup** | "What components does NeqSim have that contain 'butane'?" | `searchComponents` |
| **Capabilities** | "What can NeqSim calculate? Which EOS models are available?" | `getCapabilities` |

### Quick Path vs Full Simulation

| Need | Tool | Flowsheet Required? |
|---|---|---|
| Single property lookup | `runFlash` | No |
| Multi-point sweep | `runBatch` or `getPropertyTable` | No |
| Phase boundary | `getPhaseEnvelope` | No |
| Multi-equipment process | `runProcess` | Yes (JSON definition) |

The LLM discovers the tools automatically via MCP, reads the embedded examples
and schemas to learn the JSON format, then calls the tools to compute answers.
Every response includes **provenance metadata** (EOS model, assumptions,
limitations, convergence status) for trust assessment.

---

## Quick Start (5 minutes)

The fastest way to get NeqSim running with your LLM. No cloning, no Maven, no build step.

### 1. Install Java 17+

<details>
<summary><strong>macOS</strong></summary>

```bash
brew install openjdk@17
```
</details>

<details>
<summary><strong>Linux (Ubuntu/Debian)</strong></summary>

```bash
sudo apt install openjdk-17-jdk
```
</details>

<details>
<summary><strong>Windows</strong></summary>

Download from [Adoptium](https://adoptium.net/temurin/releases/?version=17) and run the installer.
Or with winget:
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```
</details>

Verify: `java -version` should show 17 or higher.

### 2. Download the server

Download `neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` from the
[latest release](https://github.com/equinor/neqsim/releases).

**Or use Docker** (no Java install needed):
```bash
docker pull ghcr.io/equinor/neqsim-mcp-server:latest
```

### 3. Connect to your LLM

<details>
<summary><strong>Claude Desktop</strong></summary>

Edit `%APPDATA%\Claude\claude_desktop_config.json` (Windows) or
`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

```json
{
  "mcpServers": {
    "neqsim": {
      "command": "java",
      "args": ["-jar", "/path/to/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"]
    }
  }
}
```

With Docker:
```json
{
  "mcpServers": {
    "neqsim": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "ghcr.io/equinor/neqsim-mcp-server:latest"]
    }
  }
}
```

Restart Claude Desktop.
</details>

<details>
<summary><strong>VS Code Copilot</strong></summary>

Add to `.vscode/mcp.json`:

```json
{
  "servers": {
    "neqsim": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar"]
    }
  }
}
```

Restart VS Code.
</details>

<details>
<summary><strong>Cursor / Other MCP Clients</strong></summary>

Any MCP STDIO client works. Point it at:
```
java -jar /path/to/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```
</details>

### 4. Ask a question

Try any of these:

> "What is the density of natural gas (90% methane, 10% ethane) at 80 bara and 35°C?"

> "Plot the phase envelope for 85% methane, 10% ethane, 5% propane"

> "Simulate gas at 80 bara going through a separator then a compressor to 150 bara"

The LLM discovers NeqSim's tools automatically and calls them to compute rigorous answers — no coding needed.

### Example conversation

**You:** "What is the dew point temperature of 85% methane, 10% ethane, 5% propane at 50 bara?"

**LLM** *(internally calls `runFlash` with `flashType: "dewPointT"`)*

**LLM responds:** "The dew point temperature is -42.3°C at 50 bara (SRK equation of state, converged in 12 iterations). Below this temperature, liquid will begin to condense."

Every response includes **provenance**: which EOS model was used, whether the calculation converged, and what limitations apply.

---

## Developer Build (from source)

If you want to build from source (for development or to use the latest unreleased code):

### Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17+ | Quarkus requires Java 17. NeqSim core still compiles with Java 8. |
| Maven | 3.9+ | Or use the Maven wrapper (`mvnw` / `mvnw.cmd`) from the parent project |
| NeqSim core | 3.6.1 | Must be installed to local Maven repo first (see below) |

### Build steps

**1. Install NeqSim to local Maven repo** (from the parent neqsim directory):

```bash
# Linux / macOS
./mvnw install -DskipTests -Dmaven.javadoc.skip=true

# Windows
.\mvnw.cmd install -DskipTests "-Dmaven.javadoc.skip=true"
```

**2. Build the MCP server:**

```bash
cd neqsim-mcp-server

# Linux / macOS
../mvnw package -DskipTests -Dmaven.javadoc.skip=true

# Windows
..\mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true"
```

This produces: `target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` (~55 MB).

**3. Verify the server works:**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' \
  | java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar 2>/dev/null
```

**4. Run the comprehensive test suite** (111 checks):

```bash
python test_mcp_server.py
```

**5. (Optional) Test with MCP Inspector:**

```bash
npx @modelcontextprotocol/inspector java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

---

## Connecting to LLM Clients (Developer Build)

> **Using a prebuilt jar or Docker?** See the [Quick Start](#quick-start-5-minutes)
> section above for connection instructions.

The instructions below are for connecting with a **locally built** uber-jar.

### VS Code Copilot

The workspace already includes `.vscode/mcp.json`. After building the uber-jar,
restart VS Code and Copilot will discover the NeqSim tools automatically.

To configure manually, add to `.vscode/mcp.json`:

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

### Claude Desktop (developer path)

Edit `%APPDATA%\Claude\claude_desktop_config.json` (Windows) or
`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

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

Any client that supports MCP STDIO transport can connect. Point it at:

```
java -jar /path/to/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

---

## Available MCP Tools

### `runFlash` — Thermodynamic Flash Calculation

Computes phase equilibrium for a fluid mixture. Returns per-phase densities,
viscosities, thermal conductivities, heat capacities, compressibility factors,
and compositions.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `components` | JSON string | Component-to-mole-fraction map, e.g. `{"methane": 0.85, "ethane": 0.15}` |
| `temperature` | number | Temperature value |
| `temperatureUnit` | string | `C`, `K`, or `F` |
| `pressure` | number | Pressure value |
| `pressureUnit` | string | `bara`, `barg`, `Pa`, `kPa`, `MPa`, `psi`, or `atm` |
| `eos` | string | Equation of state (see table below) |
| `flashType` | string | Flash algorithm (see table below) |

**Supported Equations of State:**

| EOS | Full Name | Best For |
|---|---|---|
| `SRK` | Soave-Redlich-Kwong | General hydrocarbon systems (default) |
| `PR` | Peng-Robinson | General purpose, slightly different liquid densities |
| `CPA` | CPA-SRK | Systems with water, methanol, MEG, or other associating fluids |
| `GERG2008` | GERG-2008 | High-accuracy natural gas (reference-quality) |
| `PCSAFT` | PC-SAFT | Polymers, associating fluids |
| `UMRPRU` | UMR-PRU with Mathias-Copeman | Advanced mixing rules |

**Supported Flash Types:**

| Flash Type | Description |
|---|---|
| `TP` | Temperature-Pressure flash (most common) |
| `PH` | Pressure-Enthalpy flash (requires `enthalpy` in input) |
| `PS` | Pressure-Entropy flash (requires `entropy` in input) |
| `TV` | Temperature-Volume flash (requires `volume` in input) |
| `dewPointT` | Dew point temperature at given pressure |
| `dewPointP` | Dew point pressure at given temperature |
| `bubblePointT` | Bubble point temperature at given pressure |
| `bubblePointP` | Bubble point pressure at given temperature |
| `hydrateTP` | Hydrate equilibrium temperature at given pressure |

**Example call (via MCP JSON-RPC):**

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

### `runProcess` — Process Simulation

Builds and runs a flowsheet from a JSON definition. Supports streams, separators,
compressors, coolers, heaters, valves, mixers, splitters, heat exchangers, distillation
columns, and pipelines.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `processJson` | JSON string | Complete process definition (see format below) |

**Process JSON format:**

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

**Equipment types:** `Stream`, `Separator`, `Compressor`, `Cooler`, `Heater`,
`Valve`, `Mixer`, `Splitter`, `HeatExchanger`, `DistillationColumn`, `Pipe`

**Outlet port selectors** (for connecting equipment):

| Port | Description |
|---|---|
| `<name>.gasOut` | Gas outlet from separator |
| `<name>.liquidOut` | Liquid outlet from separator |
| `<name>.oilOut` | Oil outlet from three-phase separator |
| `<name>.waterOut` | Water outlet from three-phase separator |

**Multiple fluids** — use `"fluids"` (plural) with named references:

```json
{
  "fluids": {
    "gas": { "model": "SRK", "temperature": 298.15, "pressure": 50.0, "components": {"methane": 0.9} },
    "oil": { "model": "PR", "temperature": 350.0, "pressure": 100.0, "components": {"nC10": 1.0} }
  },
  "process": [
    { "type": "Stream", "name": "gasFeed", "fluidRef": "gas", "properties": {"flowRate": [10000.0, "kg/hr"]} },
    { "type": "Stream", "name": "oilFeed", "fluidRef": "oil", "properties": {"flowRate": [50000.0, "kg/hr"]} }
  ]
}
```

### `validateInput` — Pre-flight Validation

Validates a flash or process JSON **before running it**. Catches common mistakes
and returns actionable fix suggestions.

**Checks performed:**
- Component names exist in the database (suggests corrections for typos)
- Temperature and pressure are in physically reasonable ranges
- EOS model is recognized
- Flash type is valid, and required specs are present (e.g. enthalpy for PH flash)
- Composition sums are reasonable
- Process equipment types are recognized
- Duplicate equipment names detected

**Example response (with errors):**

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "error",
      "code": "UNKNOWN_COMPONENT",
      "message": "'metane' is not a known component. Did you mean 'methane'?"
    },
    {
      "severity": "error",
      "code": "UNKNOWN_MODEL",
      "message": "'FAKEOS' is not a supported model. Valid: SRK, PR, CPA, GERG2008, PCSAFT, UMRPRU"
    }
  ]
}
```

### `searchComponents` — Component Database Search

Searches the NeqSim component database by name (partial matching, case-insensitive).

**Examples:**
- `query: "methane"` → `["methane"]`
- `query: "meth"` → `["methane", "methanol", "dimethylether", ...]`
- `query: ""` → all 100+ components

### `getExample` — Example Templates

Returns ready-to-use JSON examples. The LLM reads these to learn the format,
then modifies them based on the user's requirements.

**Available examples:**

| Category | Name | Description |
|---|---|---|
| `flash` | `tp-simple-gas` | TP flash of a simple natural gas |
| `flash` | `tp-two-phase` | TP flash producing gas + liquid phases |
| `flash` | `dew-point-t` | Dew point temperature calculation |
| `flash` | `bubble-point-p` | Bubble point pressure calculation |
| `flash` | `cpa-with-water` | CPA EOS flash with water (associating fluid) |
| `process` | `simple-separation` | Stream → Separator |
| `process` | `compression-with-cooling` | Stream → Compressor → Cooler |
| `validation` | `error-flash` | A deliberately invalid flash input |

### `getSchema` — JSON Schemas

Returns JSON Schema (Draft 2020-12) definitions for tool inputs and outputs.

**Available schemas:**

| Tool Name | Types | Description |
|---|---|---|
| `run_flash` | `input`, `output` | Flash calculation JSON format |
| `run_process` | `input`, `output` | Process simulation JSON format |
| `validate_input` | `input`, `output` | Validator JSON format |
| `search_components` | `input`, `output` | Component search JSON format |

---

## Available MCP Resources

| URI | Description |
|---|---|
| `neqsim://example-catalog` | Full catalog of all examples with descriptions |
| `neqsim://schema-catalog` | Full catalog of all JSON schemas |
| `neqsim://examples/{category}/{name}` | Specific example by category and name |
| `neqsim://schemas/{tool}/{type}` | Specific schema by tool name and type |

---

## How the LLM Uses the Server (Typical Flow)

1. **Discovery** — The LLM calls `tools/list` and finds the available tools. It reads
   the descriptions to understand what each tool does. Or it calls `getCapabilities`
   for a structured manifest of all NeqSim capabilities.

2. **Learning the format** — The LLM calls `getExample` or `getSchema` to see
   the expected JSON format for the tool it wants to use.

3. **Validation (optional)** — Before running an expensive calculation, the LLM
   calls `validateInput` to catch typos and missing fields.

4. **Computation** — The LLM calls `runFlash` or `runProcess` with the
   constructed JSON and gets physical results (densities, temperatures,
   compositions, etc.).

5. **Interpretation** — The LLM reads the JSON response and presents the
   results to the user in natural language, with units and context.

### Example Conversation

**User:** "What is the density of natural gas (90% methane, 10% ethane) at 80 bara and 35°C?"

**LLM internally calls:**
```json
runFlash({
  "components": "{\"methane\": 0.90, \"ethane\": 0.10}",
  "temperature": 35.0, "temperatureUnit": "C",
  "pressure": 80.0, "pressureUnit": "bara",
  "eos": "SRK", "flashType": "TP"
})
```

**LLM responds:** "The gas density is approximately 62.3 kg/m³ at 80 bara and 35°C
(SRK equation of state, single gas phase, Z-factor = 0.88)."

---

## Architecture

```
neqsim-mcp-server/                        # Separate Maven project (Java 17+)
├── pom.xml                                # Quarkus 3.33.1 + quarkus-mcp-server 1.11.0
├── test_mcp_server.py                     # 111-check comprehensive test suite
└── src/main/java/neqsim/mcp/server/
    ├── NeqSimTools.java                   # @Tool-annotated MCP tools (flash, batch, process, etc.)
    └── NeqSimResources.java               # 2 @Resource + 2 @ResourceTemplate

Delegates to runner layer in neqsim core (src/main/java/neqsim/mcp/):
├── runners/
│   ├── FlashRunner.java                   # Flash calculations (9 flash types × 6 EOS)
│   ├── BatchRunner.java                   # Multi-point batch flash (sensitivity studies)
│   ├── PropertyTableRunner.java           # Property table sweep (T or P)
│   ├── PhaseEnvelopeRunner.java           # PT phase envelope calculation
│   ├── ProcessRunner.java                 # Process simulation via JsonProcessBuilder
│   ├── AutomationRunner.java              # String-addressable variable access
│   ├── CapabilitiesRunner.java            # Capabilities discovery manifest
│   ├── Validator.java                     # Pre-flight input validation (12+ check types)
│   └── ComponentQuery.java                # Component database search & fuzzy matching
├── model/
│   ├── ApiEnvelope.java                   # Standard response wrapper (status + data + warnings)
│   ├── FlashRequest.java                  # Typed flash input (builder pattern)
│   ├── FlashResult.java                   # Typed flash output
│   ├── ProcessResult.java                 # Typed process output
│   ├── ValueWithUnit.java                 # Numeric value with unit string
│   ├── DiagnosticIssue.java               # Validation issue (severity + code + fix hint)
│   └── ResultProvenance.java              # Trust metadata (EOS, assumptions, limitations)
└── catalog/
    ├── ExampleCatalog.java                # 8 ready-to-use examples (flash + process)
    └── SchemaCatalog.java                 # JSON Schema definitions (4 tools × in/out)
```

The MCP server is a **thin Quarkus wrapper** (~200 lines) around the
framework-agnostic runner layer in neqsim core. This design means:

- **Stability** — Runners are tested with 139+ JUnit tests in the neqsim project
- **Portability** — Runners can be used with any other MCP framework, REST API, or CLI
- **Separation** — The server can be extracted to a standalone repo by copying this directory

---

## Testing

### Unit Tests (Runner Layer)

The runner layer in neqsim core has 139+ JUnit 5 tests across 12 test classes:

```bash
# Run all MCP runner tests
./mvnw test -pl . -Dtest="neqsim.mcp.**"
```

### Integration Tests (MCP Server)

The `test_mcp_server.py` script launches the server, communicates over STDIO,
and validates 111 checks:

| Category | Checks | Description |
|---|---|---|
| Protocol | 9 | Tool/resource/template registration |
| Component search | 9 | Exact, partial, empty, no-match |
| Examples & schemas | 10 | Catalog retrieval |
| Flash calculations | 30 | SRK, PR, CPA; single/two-phase; density, Z, viscosity |
| Dew/bubble point | 6 | All 4 saturation flash types |
| Process simulation | 13 | Separator, compressor, cooler, heater, valve, multi-unit trains |
| Validation | 22 | Valid input, unknown components, bad models, missing specs |
| Error handling | 2 | Graceful failure on bad input |
| Catalog round-trip | 10 | All examples run end-to-end through the server |

```bash
cd neqsim-mcp-server
python test_mcp_server.py
```

---

## Troubleshooting

### Build Fails — "Could not find artifact com.equinor.neqsim:neqsim:3.6.1"

The neqsim core library must be installed first:

```bash
cd ..  # go to parent neqsim directory
./mvnw install -DskipTests -Dmaven.javadoc.skip=true
```

### Build Fails — "No matching toolchain found for JDK 17+"

This project requires JDK 17+. Check with `java -version`.
The parent neqsim project compiles with Java 8. Both can coexist — just ensure
`JAVA_HOME` points to JDK 17+ when building this project.

### Server Hangs or Returns Garbled Output

The STDIO transport uses stdin/stdout for JSON-RPC. All application logs go to
stderr. Check that:
- No library is writing directly to `System.out`
- Logging is set to WARN or higher (default in `application.properties`)
- You're sending one JSON-RPC message per line

### "Unknown component" Errors

Use `searchComponents` to find the exact component name NeqSim expects:
- `n-heptane` (not `nC7`)
- `i-butane` (not `isobutane`)
- `CO2` (not `carbon dioxide`)
- `H2S` (not `hydrogen sulfide`)
