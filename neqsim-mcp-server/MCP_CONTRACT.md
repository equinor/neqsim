# MCP Tool Contract v1

This document defines the stable API surface for the NeqSim MCP Server.
Agent builders and application developers can rely on these guarantees
when integrating with NeqSim.

## Stability Promise

- **Required input fields** will not be removed or renamed within v1.
- **Required response fields** will not be removed within v1.
- **New optional fields** may be added to inputs or outputs at any time.
- **Experimental tools** may change or be removed between minor versions.
- Every MCP response includes `"apiVersion": "1.0"` for contract identification.

## Core Tools (Stable)

These tools are part of the v1 contract. Their required parameters and
response keys are stable.

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `runFlash` | **Stable** | v1.0 | Flash calculation (TP, PH, PS, dew, bubble, hydrate) |
| `runBatch` | **Stable** | v1.0 | Multi-point sensitivity sweep |
| `getPropertyTable` | **Stable** | v1.0 | Property table across T or P range |
| `getPhaseEnvelope` | **Stable** | v1.0 | Phase envelope (PT curve) |
| `runProcess` | **Stable** | v1.0 | Process simulation from JSON definition |
| `validateInput` | **Stable** | v1.0 | Pre-flight input validation |
| `searchComponents` | **Stable** | v1.0 | Component database search |

## Discovery Tools (Stable)

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `getCapabilities` | **Stable** | v1.0 | Capabilities discovery manifest |
| `getExample` | **Stable** | v1.0 | Example templates for all tools |
| `getSchema` | **Stable** | v1.0 | JSON Schema definitions |

## Automation Tools (Stable)

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `listSimulationUnits` | **Stable** | v1.0 | List addressable equipment in a process |
| `listUnitVariables` | **Stable** | v1.0 | List variables for a specific unit |
| `getSimulationVariable` | **Stable** | v1.0 | Read a variable by dot-notation address |
| `setSimulationVariable` | **Stable** | v1.0 | Set an INPUT variable and re-run |
| `saveSimulationState` | **Stable** | v1.0 | Save process state as JSON snapshot |
| `compareSimulationStates` | **Stable** | v1.0 | Diff two state snapshots |
| `diagnoseAutomation` | **Stable** | v1.0 | Self-healing diagnostics for failed operations |
| `getAutomationLearningReport` | **Stable** | v1.0 | Automation operation history and insights |

## Extended Domain Tools (Stable)

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `runPVT` | **Stable** | v1.1 | PVT lab experiments (CME, CVD, DL, saturation, separator, swelling, GOR, viscosity) |
| `runFlowAssurance` | **Stable** | v1.1 | Flow assurance (hydrate, wax, asphaltene, corrosion, erosion, cooldown, emulsion) |
| `calculateStandard` | **Stable** | v1.1 | Gas/oil quality per 22 standards (ISO, AGA, GPA, EN, ASTM) |
| `runPipeline` | **Stable** | v1.1 | Multiphase pipeline flow (Beggs & Brill) |
| `runReservoir` | **Stable** | v1.1 | Material balance reservoir simulation |
| `runFieldEconomics` | **Stable** | v1.1 | NPV/IRR/cash flow with fiscal regimes + decline curves |
| `runDynamic` | **Stable** | v1.1 | Dynamic transient simulation with auto-instrumented controllers |
| `runBioprocess` | **Stable** | v1.1 | Bioprocessing reactors (AD, fermentation, gasification, pyrolysis) |
| `crossValidateModels` | **Stable** | v1.1 | Cross-validate process under multiple EOS models |
| `runParametricStudy` | **Stable** | v1.1 | Multi-variable parametric sweep |

## Session & Workflow Tools (Stable)

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `manageSession` | **Stable** | v1.1 | Persistent simulation sessions (create, modify, run, snapshot, restore) |
| `solveTask` | **Stable** | v1.1 | Solve engineering tasks from high-level descriptions |
| `composeWorkflow` | **Stable** | v1.1 | Chain simulation steps into multi-domain workflows |
| `validateResults` | **Stable** | v1.1 | Validate results against engineering design rules |
| `generateReport` | **Stable** | v1.1 | Generate structured engineering reports |
| `runPlugin` | **Stable** | v1.1 | Run or list registered MCP runner plugins |
| `getProgress` | **Stable** | v1.1 | Check progress of long-running simulations |

## Platform Tools (Experimental)

These tools are functional but their interfaces may evolve between minor versions.

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `streamSimulation` | **Experimental** | v1.2 | Async simulation with incremental polling (parametric sweep, Monte Carlo, dynamic) |
| `generateVisualization` | **Experimental** | v1.2 | Inline SVG/Mermaid/HTML visualization (phase envelopes, flowsheets, charts) |
| `composeMultiServerWorkflow` | **Experimental** | v1.2 | Multi-server orchestration across MCP servers |
| `manageSecurity` | **Experimental** | v1.2 | API key management, rate limiting, audit logging |
| `manageState` | **Experimental** | v1.2 | Persist/restore simulation states across server restarts |
| `manageValidationProfile` | **Experimental** | v1.2 | Jurisdiction-specific validation profiles (NCS, UKCS, GoM, Brazil) |
| `queryDataCatalog` | **Experimental** | v1.2 | Browse thermodynamic databases (components, standards, materials, EOS models) |

## Browsable Resources (Stable)

| URI | Status | Since | Description |
|-----|--------|-------|-------------|
| `neqsim://example-catalog` | **Stable** | v1.0 | Full catalog of examples |
| `neqsim://schema-catalog` | **Stable** | v1.0 | Full catalog of JSON schemas |
| `neqsim://examples/{category}/{name}` | **Stable** | v1.0 | Specific example |
| `neqsim://schemas/{tool}/{type}` | **Stable** | v1.0 | Specific schema |
| `neqsim://components` | **Stable** | v1.2 | Component families |
| `neqsim://components/{name}` | **Stable** | v1.2 | Component properties (Tc, Pc, omega, MW) |
| `neqsim://standards` | **Stable** | v1.2 | Design standards catalog |
| `neqsim://standards/{code}` | **Stable** | v1.2 | Specific standard parameters |
| `neqsim://models` | **Stable** | v1.2 | EOS model catalog |
| `neqsim://materials/{type}` | **Stable** | v1.2 | Material grades and properties |
| `neqsim://data-tables` | **Stable** | v1.2 | All queryable database tables |

## Guided Workflow Prompts

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

## Response Envelope (Stable)

Every tool response follows this envelope structure:

```json
{
  "status": "success | error",
  "apiVersion": "1.0",
  "provenance": {
    "model": "SRK",
    "flashType": "TP",
    "convergence": { "converged": true, "iterations": 8 },
    "assumptions": ["..."],
    "limitations": ["..."],
    "warnings": []
  },
  "data": { ... }
}
```

### Stable response fields

| Field | Type | Guaranteed |
|-------|------|------------|
| `status` | `"success"` or `"error"` | Always present |
| `apiVersion` | string | Always present |
| `provenance.model` | string | Present on success |
| `provenance.convergence.converged` | boolean | Present on success |

### Warning taxonomy

Warnings in `provenance.warnings` use these standard codes:

| Code | Severity | Description |
|------|----------|-------------|
| `MODEL_LIMITATION` | INFO | Known limitation of the selected EOS model |
| `EXTRAPOLATION` | WARNING | Operating outside validated T/P/composition range |
| `MISSING_REFERENCE_DATA` | WARNING | No experimental data available for this binary pair |
| `TWO_PHASE_UNCERTAINTY` | CAUTION | Near phase boundary; small input changes may shift phase count |
| `NEAR_CRITICAL` | CAUTION | Operating within 10% of critical point |
| `CONVERGENCE_WARNING` | WARNING | Converged but residual above typical threshold |
| `COMPOSITION_NORMALIZED` | INFO | Input composition did not sum to 1.0; was normalized |
| `HYDRATE_APPROXIMATE` | INFO | Hydrate model is correlative, not rigorous |

## What May Change

- New tools may be added at any time.
- New optional fields may be added to existing tool inputs and outputs.
- Warning messages (human-readable text) may be reworded.
- Experimental tools may be promoted to stable or removed.
- Default EOS model may change between major versions (currently SRK).

## What Will Not Change (Within v1)

- Required input field names for core tools.
- Required response field names listed above.
- Tool names for core and discovery tools.
- Warning code identifiers (machine-parseable codes).
- Response envelope structure.

## Versioning

The MCP server version follows `{neqsim-version}-mcp-{mcp-version}`.
The `apiVersion` field in responses tracks the contract version independently
of the server version.

| Contract Version | Server Versions | Notes |
|-----------------|-----------------|-------|
| 1.0 | 1.0.0+ | Initial stable release |
| 1.1 | 1.1.0+ | Extended domain, session, workflow tools |
| 1.2 | 1.2.0+ | Platform tools, industrial governance, benchmark trust |

---

## Industrial Governance (v1.2)

### Deployment Profiles

The `manageIndustrialProfile` tool controls which tools are exposed and
what validation level is enforced.

| Profile | Description | Tool Access | Auto-Validation |
|---------|-------------|-------------|-----------------|
| `DESKTOP_ENGINEER` | Full access for individual engineering work | All tools | On by default |
| `STUDY_TEAM` | Collaborative team environment | All tools | Enforced |
| `DIGITAL_TWIN` | Read-heavy advisory for live operations | ADVISORY + CALCULATION only | Enforced |
| `ENTERPRISE` | Restricted to industrial core tools | Industrial core only | Enforced, approval gates on EXECUTION |

Default mode: `DESKTOP_ENGINEER`.

### Tool Categories

Every tool is classified into exactly one category:

| Category | Description | Examples |
|----------|-------------|---------|
| `ADVISORY` | Read-only discovery and validation; always allowed | `getCapabilities`, `getExample`, `getSchema`, `validateInput`, `searchComponents` |
| `CALCULATION` | Stateless engineering calculations | `runFlash`, `runProcess`, `runPVT`, `runPipeline`, `calculateStandard` |
| `EXECUTION` | State-modifying operations; may require approval | `setSimulationVariable`, `manageSession`, `solveTask` |
| `PLATFORM` | Security, persistence, multi-server; restricted in production | `manageSecurity`, `manageState`, `composeMultiServerWorkflow` |

### Industrial Core Toolset

These 20 tools are the production-proven subset available in all deployment
modes including `ENTERPRISE`:

```
runFlash, runProcess, runPVT, runFlowAssurance, calculateStandard,
crossValidateModels, runParametricStudy, validateResults, generateReport,
validateInput, searchComponents, getCapabilities, getExample, getSchema,
getPropertyTable, getPhaseEnvelope, runBatch, runPipeline, sizeEquipment,
compareProcesses
```

### Governance Tools (Stable)

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `manageIndustrialProfile` | **Stable** | v1.2 | Deployment profiles, tool access, validation enforcement |
| `getBenchmarkTrust` | **Stable** | v1.2 | Per-tool validation status, accuracy bounds, limitations |
| `checkToolAccess` | **Stable** | v1.2 | Pre-flight tool access check for governed deployments |

### Auto-Validation Pipeline

When auto-validation is enabled (default in all modes), every CALCULATION
tool automatically appends a `"autoValidation"` block to the response containing:

```json
{
  "autoValidation": {
    "overall": "PASS | WARNING | FAIL",
    "checks": [
      { "rule": "...", "status": "PASS", "message": "..." }
    ],
    "timestamp": "2025-01-15T10:30:00Z"
  }
}
```

Auto-validated tools: `runFlash`, `runProcess`, `runPVT`, `runFlowAssurance`,
`calculateStandard`, `runPipeline`.

### Benchmark Trust Metadata

The `getBenchmarkTrust` tool returns per-tool validation metadata:

| Field | Description |
|-------|-------------|
| `maturityLevel` | `VALIDATED`, `TESTED`, or `EXPERIMENTAL` |
| `validationCases` | Reference cases with expected results |
| `accuracyBounds` | Typical accuracy ranges (e.g., density ±0.5%) |
| `knownLimitations` | Conditions where results are unreliable |
| `unsupported` | Explicitly unsupported scenarios |

**Maturity levels:**

| Level | Meaning |
|-------|---------|
| `VALIDATED` | Verified against NIST/experimental data; suitable for design decisions |
| `TESTED` | Tested against literature/industry cases; suitable for screening studies |
| `EXPERIMENTAL` | Functional but limited validation; use for exploration only |
