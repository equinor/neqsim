---
title: "MCP Getting Started: Connect Your LLM to NeqSim"
description: "5-minute guide for connecting any MCP-compatible LLM (Copilot, Claude Desktop, Cursor) to NeqSim's thermodynamic engine. Covers setup, first calculation, tool selection, and common patterns."
---

# MCP Getting Started: Connect Your LLM to NeqSim

This tutorial gets an external LLM agent running real thermodynamic calculations
through NeqSim in under 5 minutes.

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ (or use the bundled `mvnw` wrapper) |

## 1. Build the MCP Server (2 minutes)

```bash
# Clone and build NeqSim core
git clone https://github.com/equinor/neqsim.git
cd neqsim
./mvnw install -DskipTests -Dmaven.javadoc.skip=true

# Build the MCP server
cd neqsim-mcp-server
../mvnw package -DskipTests -Dmaven.javadoc.skip=true
```

This produces a single jar:
`neqsim-mcp-server/target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar` (~55 MB).

## 2. Connect to Your LLM Client (1 minute)

### VS Code Copilot

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

### Claude Desktop

Add to `claude_desktop_config.json`:

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

### Cursor / Other MCP Clients

Use the same STDIO transport — the server reads JSON-RPC from stdin
and writes responses to stdout.

## 3. Your First Calculation (30 seconds)

Ask the LLM:

> "What is the density of natural gas (90% methane, 10% ethane) at 80 bara and 35°C?"

The LLM will call `runFlash` behind the scenes and respond with something like:

> "The gas density is approximately 62.3 kg/m³ at 80 bara and 35°C
> (SRK equation of state, single gas phase, Z-factor = 0.88)."

## Which Tool Should I Use?

The MCP server exposes several tools. Here's how to pick the right one:

```
Need a single property value?
 └─ runFlash (TP, dew point, bubble point, hydrate temp, etc.)

Need properties across a range?
 ├─ getPropertyTable (sweep T or P, get a table of properties)
 └─ runBatch (custom multi-point sweep with full flexibility)

Need the phase boundary?
 └─ getPhaseEnvelope (returns PT bubble/dew curves + cricondenbar)

Need a multi-equipment flowsheet?
 └─ runProcess (JSON process definition → full simulation)

Don't know what's available?
 └─ getCapabilities (structured manifest of everything NeqSim can do)

Need to find a component name?
 └─ searchComponents (fuzzy search: "meth" → methane, methanol, ...)

Want to validate input before running?
 └─ validateInput (catches typos, bad ranges, wrong EOS)
```

### Quick Path vs Full Simulation

| Approach | Tools | When to Use |
|---|---|---|
| **Quick path** | `runFlash`, `runBatch`, `getPropertyTable`, `getPhaseEnvelope` | Single answers, sensitivity studies, phase boundaries. No flowsheet needed. |
| **Full simulation** | `runProcess` | Multi-equipment processes (separator → compressor → cooler, etc.). Requires a JSON process definition. |

## Common Patterns

### Pattern 1: Single Property Lookup

> "What is the dew point of this gas at 100 bara?"

The LLM calls `runFlash` with `flashType: "dewPointT"`.

### Pattern 2: Sensitivity Study

> "How does gas density change from 10 to 150 bara at 25°C? Give me a table."

The LLM calls `getPropertyTable` with `sweep: "pressure"`, or `runBatch`
with cases at different pressures.

### Pattern 3: Phase Envelope

> "Plot the phase envelope for 85% methane, 10% ethane, 5% propane"

The LLM calls `getPhaseEnvelope` and gets the PT boundary points back.

### Pattern 4: Process Simulation

> "Simulate feed gas at 50 bara through a separator, then compress the gas to 120 bara"

The LLM calls `runProcess` with a JSON definition:

```json
{
  "fluid": {
    "model": "SRK",
    "temperature": 298.15,
    "pressure": 50.0,
    "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05}
  },
  "process": [
    {"type": "Stream", "name": "feed", "properties": {"flowRate": [50000, "kg/hr"]}},
    {"type": "Separator", "name": "HP Sep", "inlet": "feed"},
    {"type": "Compressor", "name": "Export Comp", "inlet": "HP Sep.gasOut",
     "properties": {"outletPressure": [120, "bara"]}}
  ]
}
```

### Pattern 5: Batch Temperature Sensitivity

> "Calculate density at 0, 10, 20, 30, 40, 50°C — all at 80 bara"

The LLM calls `runBatch`:

```json
{
  "model": "SRK",
  "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05},
  "flashType": "TP",
  "cases": [
    {"temperature": {"value": 0, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}},
    {"temperature": {"value": 10, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}},
    {"temperature": {"value": 20, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}},
    {"temperature": {"value": 30, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}},
    {"temperature": {"value": 40, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}},
    {"temperature": {"value": 50, "unit": "C"}, "pressure": {"value": 80, "unit": "bara"}}
  ]
}
```

## Understanding Provenance

Every response includes a `provenance` block:

```json
{
  "provenance": {
    "engine": "NeqSim",
    "thermodynamicModel": "SRK",
    "mixingRule": "classic",
    "calculationType": "TP flash",
    "converged": true,
    "computationTimeMs": 45,
    "assumptions": [
      "Ideal gas reference state",
      "Van der Waals one-fluid mixing rules"
    ],
    "limitations": [
      "SRK EOS may under-predict liquid densities by 5-15%",
      "Less accurate for polar/associating compounds (use CPA instead)"
    ],
    "validationsPassed": [
      "Phase stability check passed",
      "Mass balance closure < 1e-10"
    ]
  }
}
```

Use this to:
- **Present results with caveats** when limitations apply
- **Select a more appropriate EOS** (e.g., switch to CPA for water systems)
- **Report convergence issues** to the user
- **Track computation cost** for batch optimization

## EOS Selection Guide

| System | Recommended EOS | Why |
|---|---|---|
| Natural gas (C1–C10) | `SRK` or `GERG2008` | SRK is fast. GERG2008 is reference-quality. |
| Oil with heavy fractions | `PR` or `SRK` | PR slightly better for liquid density. |
| Water/methanol/MEG systems | `CPA` | Handles hydrogen bonding (association). |
| CO2-rich (CCS) | `SRK` or `PR` | Good for CO2/hydrocarbon mixtures. |
| Polymer systems | `PCSAFT` | Designed for chain molecules. |
| High-accuracy gas metering | `GERG2008` | ISO 20765 reference EOS. |

## Troubleshooting

| Problem | Solution |
|---|---|
| Server doesn't start | Check JDK 17+ is installed: `java -version` |
| "Component not found" | Use `searchComponents` to find the correct name |
| Zero density/viscosity | The flash may not have converged — check `provenance.converged` |
| Slow batch calculations | Reduce case count (max 500) or use `getPropertyTable` for uniform sweeps |
| Wrong phase results | Try `CPA` for water systems, `GERG2008` for custody-quality gas |

## Next Steps

- [MCP Server Reference](../integration/mcp_server_guide.md) — full tool documentation
- [MCP Core Layer](../integration/mcp_neqsim_core_layer.md) — architecture and extension
- [AI Agentic Programming](../integration/ai_agentic_programming_intro.md) — broader agentic patterns
- [Process JSON Format](../integration/web_api_json_process_builder.md) — detailed process definition schema
