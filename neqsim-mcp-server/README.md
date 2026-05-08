# NeqSim MCP Server

NeqSim MCP Server provides a governed engineering calculation layer for AI-assisted workflows.

It exposes validated thermodynamic and process simulation capabilities from
[NeqSim](https://github.com/equinor/neqsim) through a structured
[Model Context Protocol](https://modelcontextprotocol.io/) (MCP) tool interface,
with built-in validation, traceability, and deployment profiles for controlled use
in engineering environments.

The MCP server does not perform autonomous decision-making; it executes explicit
tool calls under defined constraints.

Any MCP client — VS Code Copilot, Claude Desktop, Cursor, or others — can
connect and use these capabilities.

The system is designed to support controlled engineering use through:

- **3-tier tool model** — tools are classified by validation maturity and
  operational impact, with code-level enforcement that blocks disallowed
  tools with structured error JSON.
- **Auto-validation** — every calculation tool validates its output against
  engineering design rules. In governed deployment profiles, validation is
  automatically applied and cannot be disabled.
- **Benchmark trust** — each tool reports maturity level (VALIDATED / TESTED /
  EXPERIMENTAL), reference cases, accuracy bounds, and known limitations.
- **Four deployment profiles** — each profile defines enforced constraints on
  tool availability, validation behavior, and execution permissions.
- **Traceability** — every response includes provenance metadata (EOS model,
  convergence status, assumptions, limitations, warnings).

Built with [Quarkus MCP Server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
(STDIO + Streamable HTTP transport). Ships as a single uber-jar (~55 MB) — no extra services needed.

See [MCP_CONTRACT.md](MCP_CONTRACT.md) for the complete stable API contract.

---

## Deployment Profiles

Each profile defines enforced constraints on tool availability, validation behavior,
and execution permissions.

| Profile | Tier 1 | Tier 2 | Tier 3 | Approval Gate |
|---------|--------|--------|--------|---------------|
| `DESKTOP_ENGINEER` | ✅ | ✅ | ✅ | Off |
| `STUDY_TEAM` | ✅ | ✅ | ❌ | Off |
| `DIGITAL_TWIN` | ✅ (ADVISORY+CALC) | ❌ | ❌ | On |
| `ENTERPRISE` | ✅ | ❌ | ❌ | On |

Set the profile on startup or at runtime via the `manageIndustrialProfile` tool:

```json
{"action": "setActive", "mode": "STUDY_TEAM"}
```

**ENTERPRISE** constraints:

- Restricted to approved industrial toolset
- Execution tools require explicit approval (if enabled)
- Platform-level tools disabled
- Validation is enforced and cannot be bypassed

### DIGITAL_TWIN Mode — Advisory Only

In `DIGITAL_TWIN` mode, the server operates as a read-only calculation and
decision-support tool. This means:

- **No direct plant control** — no tools can write back to operational systems.
- **No autonomous execution** — state-modifying tools (sessions, variable
  writes) are blocked; EXECUTION requires separate approval architecture.
- **No write-back to operational data** — the server computes answers and
  recommendations but does not act on them.

This makes it suitable for operator decision support, what-if analysis,
and monitoring dashboards without introducing control risk.

---

## Tier 1 — Trusted Core (14 tools)

Validated against NIST/experimental data. Available in all deployment modes.
Each tool has documented accuracy bounds and clear error behavior.

> For the full authoritative contract including version tracking, see
> [MCP_CONTRACT.md](MCP_CONTRACT.md).

| Tool | Category | Description |
|------|----------|-------------|
| `runFlash` | CALCULATION | Phase equilibrium flash (TP, PH, PS, dew/bubble point, hydrate) |
| `runProcess` | CALCULATION | ProcessSystem or ProcessModel simulation from JSON definition |
| `calculateStandard` | CALCULATION | Gas/oil quality per 22 standards (ISO, AGA, GPA, EN) |
| `getPropertyTable` | CALCULATION | Property table across T or P range |
| `getPhaseEnvelope` | CALCULATION | Full PT phase envelope |
| `validateInput` | ADVISORY | Pre-flight input validation with typo correction |
| `validateResults` | ADVISORY | Validate results against engineering design rules |
| `searchComponents` | ADVISORY | Fuzzy search across 100+ components |
| `getCapabilities` | ADVISORY | Structured capabilities manifest |
| `getExample` | ADVISORY | Ready-to-use JSON templates |
| `getSchema` | ADVISORY | JSON Schema definitions |
| `getBenchmarkTrust` | ADVISORY | Per-tool validation status and accuracy bounds |
| `checkToolAccess` | ADVISORY | Pre-flight tool access check |
| `manageIndustrialProfile` | ADVISORY | Deployment profile management |

### Auto-Validation

Six calculation tools (`runFlash`, `runProcess`, `runPVT`, `runPipeline`,
`calculateStandard`, and `runFlowAssurance`) automatically validate their
output against engineering design rules. In governed deployment profiles,
validation is automatically applied and cannot be disabled.

Validation results include:

- Convergence status
- Consistency checks
- Known limitations
- Warnings for out-of-range conditions

### Benchmark Trust

Before relying on a tool for design decisions, query its validation status:

```json
{"action": "getTool", "toolName": "runFlash"}
```

Returns maturity level, reference validation cases, accuracy bounds, known
limitations, and unsupported conditions.

---

## Tier 2 — Engineering Advanced (21 tools)

Tested against literature and industry cases. Available in `DESKTOP_ENGINEER`
and `STUDY_TEAM` modes. Blocked in `DIGITAL_TWIN` and `ENTERPRISE` by
code-level `enforceAccess()` — returns structured error JSON, not a silent skip.

| Tool | Description |
|------|-------------|
| `runPVT` | PVT lab experiments (CME, CVD, DL, separator, swelling, GOR) |
| `runPipeline` | Multiphase pipeline flow (Beggs & Brill) |
| `runFlowAssurance` | Hydrate, wax, asphaltene, corrosion, erosion, cooldown, emulsion |
| `crossValidateModels` | Cross-validate process under multiple EOS models |
| `runParametricStudy` | Multi-variable parametric sweep |
| `runBatch` | Multi-point sensitivity sweep |
| `sizeEquipment` | Quick equipment sizing (separator, compressor) |
| `compareProcesses` | Compare process configurations side by side |
| `generateReport` | Structured engineering report generation |
| `generateVisualization` | Inline SVG/Mermaid/HTML visualization |
| `queryDataCatalog` | Browse component, standards, material, and EOS databases |
| `setSimulationVariable` | Set an input variable and re-run a simulation |
| `saveSimulationState` | Save process state as a JSON snapshot |
| `runRelief` | PSV sizing per API 520/521 |
| `runLOPA` | Layer of Protection Analysis per IEC 61511 / CCPS |
| `runSIL` | SIL verification per IEC 61508 / IEC 61511 |
| `runRiskMatrix` | 5x5 risk matrix scoring per ISO 31000 / NORSOK Z-013 |
| `runFlareNetwork` | Flare radiation and safe-distance contours |
| `runHAZOP` | Simulation-backed IEC 61882 HAZOP worksheets from ProcessSystem scenarios |
| `runBarrierRegister` | Evidence-linked PSF/SCE barrier register handoffs |
| `runSafetySystemPerformance` | Active/passive safety-system performance analysis with quantitative SIL/PFD bridge |

---

## Tier 3 — Experimental (14 tools)

Functional but limited validation or high-autonomy tools. `DESKTOP_ENGINEER`
only. Blocked in all other modes by code-level `enforceAccess()`.
Interfaces may change between minor versions.

| Tool | Description |
|------|-------------|
| `runReservoir` | Material balance reservoir simulation |
| `runFieldEconomics` | NPV/IRR/cash flow with fiscal regimes + decline curves |
| `runDynamic` | Transient dynamic simulation with auto-instrumented PID controllers |
| `runBioprocess` | Bioprocessing reactors (AD, fermentation, gasification, pyrolysis) |
| `solveTask` | Autonomous task solver — results require engineer review |
| `composeWorkflow` | Chain simulation steps into multi-domain workflows |
| `bridgeTaskWorkflow` | Convert MCP tool output to task_solve results.json format |
| `manageSession` | Persistent simulation sessions |
| `streamSimulation` | Async simulation with incremental polling |
| `composeMultiServerWorkflow` | Multi-server orchestration |
| `manageSecurity` | API key management, rate limiting, audit logging |
| `manageState` | Persist/restore simulation states |
| `manageValidationProfile` | Jurisdiction-specific validation profiles |
| `runPlugin` | Run or list registered MCP runner plugins |

### Enforcement Example

When a blocked tool is called, the response is:

```json
{
  "status": "blocked",
  "tool": "solveTask",
  "mode": "ENTERPRISE",
  "tier": "EXPERIMENTAL",
  "reason": "Tool 'solveTask' is not available in ENTERPRISE mode. This mode allows Tier 1 (TRUSTED_CORE) only.",
  "remediation": "Switch to DESKTOP_ENGINEER mode or request approval for this tool."
}
```

### Tool Categories

Tool categories reflect increasing levels of operational impact and therefore
increasing governance requirements.

| Category | Description |
|----------|-------------|
| **ADVISORY** | Read-only, always safe — discovery, validation, inspection |
| **CALCULATION** | Stateless engineering computation |
| **EXECUTION** | State-modifying — sessions, variable writes, task solving |
| **PLATFORM** | Infrastructure — security, persistence, multi-server |

---

## Install from GitHub Release (3 steps)

Pick **jar** or **Docker** — both are first-class paths.

<table>
<tr>
<th width="50%">Option A — Jar (requires Java 17+)</th>
<th width="50%">Option B — Docker (no Java needed)</th>
</tr>
<tr><td>

**1. Download the jar + checksum**

```bash
# Replace VERSION with the latest release (e.g. 3.9.1)
VERSION=3.9.1
curl -fLO "https://github.com/equinor/neqsim/releases/download/v${VERSION}/neqsim-mcp-server-${VERSION}-runner.jar"
curl -fLO "https://github.com/equinor/neqsim/releases/download/v${VERSION}/neqsim-mcp-server-${VERSION}-runner.jar.sha256"
```

**2. Verify integrity**

```bash
sha256sum -c neqsim-mcp-server-${VERSION}-runner.jar.sha256
```

**3. Connect to your LLM** (see config snippets below)

```
java -jar neqsim-mcp-server-${VERSION}-runner.jar
```

</td><td>

**1. Pull the image**

```bash
docker pull ghcr.io/equinor/neqsim-mcp-server:latest
# or pin a version:
docker pull ghcr.io/equinor/neqsim-mcp-server:${VERSION}
```

**2. Smoke-test**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | docker run -i --rm ghcr.io/equinor/neqsim-mcp-server:latest
```

**3. Connect to your LLM** (see config snippets below)

```
docker run -i --rm ghcr.io/equinor/neqsim-mcp-server:latest
```

</td></tr>
</table>

> **Find the latest release:** [github.com/equinor/neqsim/releases](https://github.com/equinor/neqsim/releases)
> — look for the assets named **`neqsim-mcp-server-*-runner.jar`** and **`neqsim-mcp-server-*-runner.jar.sha256`**.

<details>
<summary><strong>Install Java 17+ (if using the jar path)</strong></summary>

| OS | Command |
|----|---------|
| **macOS** | `brew install openjdk@17` |
| **Linux (Ubuntu/Debian)** | `sudo apt install openjdk-17-jdk` |
| **Windows** | `winget install EclipseAdoptium.Temurin.17.JDK` |

Verify: `java -version` should show 17 or higher.
</details>

---

## Capabilities Overview

The server exposes 56 tools organized into three tiers plus platform tools,
9 guided-workflow prompts, and 11 browsable resources.

## Complete Tool Inventory

See the tier sections above for the governance model. Additional platform
tools not listed in the three tiers:

### Process Automation (String-Addressable)

These tools enable direct variable access on running simulations.
Read-only tools are classified as **Tier 1** (available in all modes).
Write operations (`setSimulationVariable`, `saveSimulationState`) are
**Tier 2** — blocked in `DIGITAL_TWIN` and `ENTERPRISE` modes.

| Tool | Description |
|------|-------------|
| `listSimulationUnits` | List all addressable equipment in a process |
| `listUnitVariables` | List all variables (INPUT/OUTPUT) for a unit |
| `getSimulationVariable` | Read a variable by dot-notation address |
| `setSimulationVariable` | Set an INPUT variable and re-run the process |
| `saveSimulationState` | Save a complete process state as a JSON snapshot |
| `compareSimulationStates` | Diff two state snapshots to find what changed |
| `diagnoseAutomation` | Self-healing diagnostics with fuzzy name matching |
| `getAutomationLearningReport` | Operation history, error patterns, and learned corrections |
| `getProgress` | Check progress of long-running simulations |

### Guided Workflow Prompts (9)

| Prompt | Description |
|--------|-------------|
| `design_gas_processing` | Step-by-step gas processing design |
| `pvt_study` | Complete PVT study workflow |
| `flow_assurance_screening` | Pipeline flow assurance screening |
| `field_development_screening` | Field development concept screening |
| `co2_ccs_chain` | CO2 CCS chain analysis |
| `teg_dehydration_design` | TEG dehydration unit design |
| `biorefinery_analysis` | Biorefinery process analysis |
| `dynamic_simulation` | Dynamic simulation with controller setup |
| `pipeline_sizing` | Multiphase pipeline sizing |

### Browsable Resources (11)

| URI | Description |
|-----|-------------|
| `neqsim://example-catalog` | Full catalog of all examples |
| `neqsim://schema-catalog` | Full catalog of all JSON schemas |
| `neqsim://examples/{category}/{name}` | Specific example by category and name |
| `neqsim://schemas/{tool}/{type}` | Specific schema by tool and input/output |
| `neqsim://components` | Component families (hydrocarbons, acid gases, glycols, etc.) |
| `neqsim://components/{name}` | Full properties for a component (Tc, Pc, omega, MW) |
| `neqsim://standards` | Design standards catalog (ASME, API, DNV, ISO, NORSOK) |
| `neqsim://standards/{code}` | Parameters for a specific design standard |
| `neqsim://models` | Equation of state models with recommendations |
| `neqsim://materials/{type}` | Material grades and properties |
| `neqsim://data-tables` | All queryable database tables |

---

## Connect to Your LLM

### Claude Desktop

Edit `%APPDATA%\Claude\claude_desktop_config.json` (Windows) or
`~/Library/Application Support/Claude/claude_desktop_config.json` (macOS):

<table>
<tr>
<th width="50%">Jar</th>
<th width="50%">Docker</th>
</tr>
<tr><td>

```json
{
  "mcpServers": {
    "neqsim": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/neqsim-mcp-server-3.7.0-runner.jar"
      ]
    }
  }
}
```

</td><td>

```json
{
  "mcpServers": {
    "neqsim": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "ghcr.io/equinor/neqsim-mcp-server:latest"
      ]
    }
  }
}
```

</td></tr>
</table>

Restart Claude Desktop.

### VS Code Copilot

Add to `.vscode/mcp.json`:

<table>
<tr>
<th width="50%">Jar</th>
<th width="50%">Docker</th>
</tr>
<tr><td>

```json
{
  "servers": {
    "neqsim": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/path/to/neqsim-mcp-server-3.7.0-runner.jar"
      ]
    }
  }
}
```

</td><td>

```json
{
  "servers": {
    "neqsim": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "ghcr.io/equinor/neqsim-mcp-server:latest"
      ]
    }
  }
}
```

</td></tr>
</table>

Restart VS Code.

### Cursor / Other MCP Clients

Any MCP STDIO client works. Point it at one of:

```bash
java -jar /path/to/neqsim-mcp-server-3.7.0-runner.jar          # jar
docker run -i --rm ghcr.io/equinor/neqsim-mcp-server:latest    # docker
```

### Streamable HTTP Transport

The server also supports Streamable HTTP transport for web-based clients and
remote access. By default, the current MCP HTTP endpoint is available at
`http://localhost:8080/mcp` when the server starts. The legacy HTTP/SSE endpoint
remains available at `http://localhost:8080/mcp/sse` for older clients. CORS is
configured for local development frontends (`localhost:3000`, `localhost:5173`).

To use Streamable HTTP with an MCP client that supports it, configure the server
URL instead of stdin/stdout command:

```json
{
  "mcpServers": {
    "neqsim": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

---

## Quick Start: Example Conversation

**You:** "What is the dew point temperature of 85% methane, 10% ethane, 5% propane at 50 bara?"

**LLM** *(calls Tier 1 tool `runFlash` with `flashType: "dewPointT"`)*

**LLM responds:** "The dew point temperature is -42.3°C at 50 bara (SRK equation of state, converged in 12 iterations). Below this temperature, liquid will begin to condense."

Every response includes **provenance** (EOS model, convergence, limitations)
and **auto-validation** against engineering design rules.

**More examples:**

> "What is the density of natural gas (90% methane, 10% ethane) at 80 bara and 35°C?"

> "Plot the phase envelope for 85% methane, 10% ethane, 5% propane"

> "Simulate gas at 80 bara going through a separator then a compressor to 150 bara"

The LLM discovers NeqSim's tools automatically via MCP, reads the embedded
examples and schemas, then calls the tools to compute rigorous answers.

---

## Task Workflow Bridge

The `bridgeTaskWorkflow` tool converts MCP tool outputs into the `results.json`
format used by the task-solving workflow. This enables end-to-end integration
between MCP-based AI assistants and the engineering task-solving pipeline in
`task_solve/`.

| Action | Description |
|--------|-------------|
| `toResultsJson` | Convert an MCP tool response to task_solve `results.json` schema |
| `getSchema` | Get the full `results.json` schema documentation |

Example: after running a flash calculation via the MCP server, bridge the result
into the task-solving format for report generation:

```json
{
  "action": "toResultsJson",
  "toolOutput": "<flash result JSON>",
  "toolName": "runFlash",
  "taskTitle": "Dew Point Study"
}
```

The bridge extracts runner-specific key results (flash properties, process
equipment data, PVT measurements, pipeline profiles, economics, standards
compliance) and maps them into the standard `results.json` structure with
`key_results`, `validation`, `approach`, and `conclusions` sections.

---

## Session Management

The `manageSession` tool enables persistent, incremental simulation workflows:

```
"Create a session" → "Add a compressor" → "Change the pressure" → "Compare states"
```

| Action | Description |
|--------|-------------|
| `create` | Start a new session with a fluid and process definition |
| `addEquipment` | Add equipment to the current process |
| `modify` | Change a parameter and re-run |
| `run` | Re-run the current process |
| `snapshot` | Save the current state with a label |
| `restore` | Restore a previous snapshot |
| `status` | Get current session state |
| `close` | End the session |

---

## Streaming & Async Simulations

The `streamSimulation` tool runs long simulations in the background with incremental polling:

| Operation | Description |
|-----------|-------------|
| `parametricSweep` | Sweep a variable range and poll for results as they complete |
| `dynamicSimulation` | Run a transient sim and poll for time-step results |
| `monteCarlo` | Run N iterations with random inputs (uncertainty analysis) |
| `poll` | Get new results since last check |
| `cancel` | Cancel a running operation |
| `list` | List all active operations |

---

## Inline Visualization

The `generateVisualization` tool returns inline visual content:

| Type | Format | Description |
|------|--------|-------------|
| `phaseEnvelope` | SVG | PT phase envelope with bubble/dew curves, critical point |
| `flowsheet` | Mermaid | Process flow diagram with equipment-type shapes |
| `compressorMap` | SVG | Compressor performance map with surge/stonewall lines |
| `barChart` | SVG | Bar chart from key-value data |
| `table` | HTML | Styled HTML table with optional highlighting |

---

## Validation Profiles

The `manageValidationProfile` tool applies jurisdiction-specific design rules:

| Profile | Standards | Description |
|---------|-----------|-------------|
| `ncs` | NORSOK, PSA, DNV | Norwegian Continental Shelf |
| `ukcs` | API, HSE, PD 8010 | UK Continental Shelf |
| `gom` | API, BSEE, 30 CFR 250 | Gulf of Mexico |
| `brazil` | ANP, Petrobras N-series | Brazil pre-salt |
| `generic` | ISO, API, ASME | International baseline |

Each profile maps equipment types to applicable standards and
includes specific design factors (e.g., NCS separator design pressure
factor = 1.1, NCS pipeline design factor = 0.77).

---

## State Persistence

The `manageState` tool saves and restores simulation states across server restarts:

| Action | Description |
|--------|-------------|
| `save` | Save current session state to a versioned JSON file |
| `load` | Load a previously saved state and restore the session |
| `list` | List all saved simulation files |
| `compare` | Diff two saved states |
| `delete` | Remove a saved state file |
| `export` | Export state in a portable format |

States are stored in `~/.neqsim/saved_simulations/` by default.

---

## Multi-Server Composition

The `composeMultiServerWorkflow` tool orchestrates across MCP servers:

| Workflow Template | Steps |
|-------------------|-------|
| `digital-twin` | NeqSim sim → plant historian comparison → tuning |
| `feed-study` | Multi-EOS flash → process sim → economics |
| `vendor-evaluation` | Process spec → vendor matching → cost comparison |
| `safety-study` | Process sim → hazard identification → consequence analysis |

Pre-registered server types: `cost-estimation`, `plant-historian`, `cad-3d`,
`document-extraction`, `safety-analysis`.

---

## Security & Audit

The `manageSecurity` tool provides API key management and audit logging:

| Action | Description |
|--------|-------------|
| `createApiKey` | Generate a new API key with role and rate limits |
| `revokeApiKey` | Revoke an existing key |
| `authenticate` | Validate an API key |
| `getAuditLog` | Query the audit trail |
| `getRateLimits` | View current rate limit status |
| `setConfig` | Update security configuration |

---

## Developer Build (from source)

If you want to build from source (for development or to use the latest unreleased code):

### Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17+ | Quarkus requires Java 17. NeqSim core still compiles with Java 8. |
| Maven | 3.9+ | Or use the Maven wrapper (`mvnw` / `mvnw.cmd`) from the parent project |
| NeqSim core | 3.9.1 | Must be installed to local Maven repo first (see below) |

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

# Linux / macOS (use public release version)
../mvnw package -DskipTests -Dmaven.javadoc.skip=true

# Linux / macOS (use local SNAPSHOT — matches parent pom revision)
../mvnw package -DskipTests -Dmaven.javadoc.skip=true -Plocal-dev

# Windows
..\mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true"

# Windows (local SNAPSHOT)
..\mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true" -Plocal-dev
```

> **Tip:** The `-Plocal-dev` profile resolves NeqSim from your local Maven repo
> (`~/.m2/`) using the SNAPSHOT version, so you can test MCP server changes
> against unreleased NeqSim core changes without publishing a release.

This produces: `target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` (~55 MB).

**3. Verify the server works:**

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' \
  | java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar 2>/dev/null
```

**4. Run the comprehensive test suite:**

```bash
python test_mcp_server.py
```

**5. (Optional) Test with MCP Inspector:**

```bash
npx @modelcontextprotocol/inspector java -jar target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

---

## Connecting to LLM Clients (Developer Build)

> **Using a prebuilt jar or Docker?** See the [Install from GitHub Release](#install-from-github-release-3-steps)
> and [Connect to Your LLM](#connect-to-your-llm) sections above.

The workspace already includes `.vscode/mcp.json`. After building the uber-jar,
restart VS Code and Copilot will discover the NeqSim tools automatically.

For other clients, use the same config snippets from [Connect to Your LLM](#connect-to-your-llm),
replacing the jar path with your local build output:

```
target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar
```

---

## Tool & Resource Reference

For detailed parameter documentation, JSON formats, example calls, and
response schemas for all tools and browsable resources, see
**[docs/API_REFERENCE.md](docs/API_REFERENCE.md)**.

---

## How the LLM Uses the Server (Typical Flow)

1. **Discovery** — The LLM calls `tools/list` and finds the 48 available tools. It reads
   the descriptions to understand what each tool does. Or it calls `getCapabilities`
   for a structured manifest of all NeqSim capabilities. It can also browse
   `neqsim://components` and `neqsim://models` to discover available data.

2. **Learning the format** — The LLM calls `getExample` or `getSchema` to see
   the expected JSON format for the tool it wants to use.

3. **Validation (optional)** — Before running an expensive calculation, the LLM
   calls `validateInput` to catch typos and missing fields.

4. **Computation** — The LLM calls `runFlash`, `runProcess`, `runPVT`,
   `runFlowAssurance`, `runPipeline`, `runFieldEconomics`, or any domain tool
   and gets physical results.

5. **Iteration** — Using `manageSession`, the LLM can incrementally build and
   modify processes. Using `streamSimulation`, it can run parametric sweeps and
   poll for results. Using `generateVisualization`, it can produce inline diagrams.

6. **Validation & Reporting** — The LLM calls `validateResults` to check
   against design rules, `manageValidationProfile` for jurisdiction-specific
   standards, and `generateReport` for structured output.

7. **Interpretation** — The LLM reads the JSON response and presents the
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
├── pom.xml                                # Quarkus 3.33.1 + quarkus-mcp-server 1.12.0
├── test_mcp_server.py                     # Comprehensive integration test suite
└── src/main/java/neqsim/mcp/server/
    ├── NeqSimTools.java                   # 56 @Tool-annotated MCP tools
    ├── NeqSimResources.java               # 6 @Resource + 5 @ResourceTemplate (11 endpoints)
    └── NeqSimPrompts.java                 # 9 @Prompt guided workflows

Delegates to runner layer in neqsim core (src/main/java/neqsim/mcp/):
├── runners/
│   │  ── Core ──
│   ├── FlashRunner.java                   # Flash calculations (9 flash types × 6 EOS)
│   ├── BatchRunner.java                   # Multi-point batch flash (sensitivity studies)
│   ├── PropertyTableRunner.java           # Property table sweep (T or P)
│   ├── PhaseEnvelopeRunner.java           # PT phase envelope calculation
│   ├── ProcessRunner.java                 # Process simulation via JsonProcessBuilder
│   ├── AutomationRunner.java              # String-addressable variable access
│   ├── CapabilitiesRunner.java            # Capabilities discovery manifest
│   ├── Validator.java                     # Pre-flight input validation (12+ check types)
│   ├── ComponentQuery.java                # Component database search & fuzzy matching
│   │  ── Domain Runners ──
│   ├── PVTRunner.java                     # PVT lab experiments (CME, CVD, DL, etc.)
│   ├── FlowAssuranceRunner.java           # Hydrate, wax, corrosion, etc.
│   ├── StandardsRunner.java               # Gas/oil quality per 22 industry standards
│   ├── PipelineRunner.java                # Multiphase pipeline flow (Beggs & Brill)
│   ├── ReservoirRunner.java               # Material balance reservoir simulation
│   ├── FieldDevelopmentRunner.java         # NPV, IRR, cash flow, decline curves
│   ├── DynamicRunner.java                 # Transient simulation with PID controllers
│   ├── BioprocessRunner.java              # AD, fermentation, gasification, pyrolysis
│   ├── CrossValidationRunner.java         # Multi-EOS cross-validation
│   ├── ParametricStudyRunner.java         # Multi-variable parametric sweeps
│   │  ── Strategic Runners ──
│   ├── SessionRunner.java                 # Persistent simulation sessions
│   ├── TaskSolverRunner.java              # Engineering task solving
│   ├── TaskWorkflowBridge.java            # Bridge MCP output to task_solve results.json
│   ├── EngineeringValidator.java          # Design rule validation
│   ├── ReportRunner.java                  # Structured report generation
│   ├── McpRunnerPlugin.java               # Plugin interface
│   ├── PluginRegistry.java                # Plugin lifecycle management
│   ├── ProgressTracker.java               # Long-running simulation tracking
│   │  ── Platform Runners ──
│   ├── StreamingRunner.java               # Async simulation with incremental polling
│   ├── VisualizationRunner.java           # SVG/Mermaid/HTML visualization
│   ├── CompositionRunner.java             # Multi-server orchestration
│   ├── SecurityRunner.java                # API keys, rate limiting, audit
│   ├── StatePersistenceRunner.java        # Simulation state save/load/compare
│   ├── ValidationProfileRunner.java       # Jurisdiction-specific profiles
│   └── DataCatalogRunner.java             # Database browsing (components, standards, materials)
├── model/
│   ├── ApiEnvelope.java                   # Standard response wrapper (status + data + warnings)
│   ├── FlashRequest.java                  # Typed flash input (builder pattern)
│   ├── FlashResult.java                   # Typed flash output
│   ├── ProcessResult.java                 # Typed process output
│   ├── ValueWithUnit.java                 # Numeric value with unit string
│   ├── DiagnosticIssue.java               # Validation issue (severity + code + fix hint)
│   └── ResultProvenance.java              # Trust metadata (EOS, assumptions, limitations)
└── catalog/
    ├── ExampleCatalog.java                # Ready-to-use examples (flash + process)
    └── SchemaCatalog.java                 # JSON Schema definitions (tools × in/out)
```

The MCP server is a **thin Quarkus wrapper** around the framework-agnostic
runner layer in neqsim core. This design means:

- **Stability** — Runners are tested with JUnit 5 tests in the neqsim project
- **Portability** — Runners can be used with any other MCP framework, REST API, or CLI
- **Separation** — The server can be extracted to a standalone repo by copying this directory
- **Extensibility** — New domains are added as a Runner + a @Tool method, nothing else

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
and validates all 56 tools across all three tiers:

| Category | Checks | Description |
|---|---|---|
| Protocol | 9 | Tool/resource/template registration (56 tools, 6 resources, 5 templates) |
| Component search | 9 | Exact, partial, empty, no-match |
| Examples & schemas | 10 | Catalog retrieval |
| Flash calculations | 30 | SRK, PR, CPA; single/two-phase; density, Z, viscosity |
| Dew/bubble point | 6 | All 4 saturation flash types |
| Process simulation | 13 | Separator, compressor, cooler, heater, valve, multi-unit trains |
| Validation | 22 | Valid input, unknown components, bad models, missing specs |
| Error handling | 2 | Graceful failure on bad input |
| Tier 2 tools | 14 | PVT, pipeline, flow assurance, standards, reservoir, economics, dynamic, sizing, comparison |
| Tier 3 tools | 17 | Sessions, task solver, workflow, reports, plugins, streaming, visualization, state, security |
| Governance tools | 6 | Industrial profile, benchmark trust, tool access |
| Catalog round-trip | 10 | All examples run end-to-end through the server |

### NIST Benchmark Validation

`BenchmarkValidationTest.java` validates claimed accuracy bounds against reference data:

| Test | Reference | Tolerance |
|---|---|---|
| Methane density at 25°C, 100 bara | NIST 66.16 kg/m³ | ±2% |
| Methane-ethane VLE at 50 bara | Two-phase check | Phase count |
| Natural gas dew point | Physical range | \[-80, 20\] °C |
| Separator mass balance | Closure | < 0.1% |
| ISO 6976 methane GCV | 37.706 MJ/Sm³ | ±0.5% |
| Trust report completeness | 15 tools | All present |
| Trust page structure | Required fields | Non-null |

```bash
cd neqsim-mcp-server
python test_mcp_server.py
```

---

## Troubleshooting

### Build Fails — "Could not find artifact com.equinor.neqsim:neqsim:3.9.1"

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
