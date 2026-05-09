# MCP Tool Contract v1

This document defines the stable API surface for the NeqSim MCP Server.
Agent builders and application developers can rely on these guarantees
when integrating with NeqSim.

Tools are organized into four tiers: **Stable Industrial Core** (the minimum
trusted surface), **Stable Platform** (discovery and automation plumbing),
**Advanced** (functional but not yet formally qualified), and **Experimental**
(interfaces may change between minor versions).

## Stability Promise

- **Required input fields** will not be removed or renamed within v1.
- **Required response fields** will not be removed within v1.
- **New optional fields** may be added to inputs or outputs at any time.
- **Advanced tools** have stable interfaces but may be reclassified.
- **Experimental tools** may change or be removed between minor versions.
- Every MCP response includes `"apiVersion": "1.0"` for contract identification.

## Stable Industrial Core

These tools form the approved industrial subset for governed deployments.
Each has documented validation basis, known accuracy bounds, and clear
error/warning behavior. Available in all deployment modes including
`ENTERPRISE`.

The industrial core toolset represents tools intended for controlled engineering use.
These tools vary in validation maturity and should be interpreted according to their
benchmark trust metadata.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `runFlash` | CALCULATION | v1.0 | Flash calculation (TP, PH, PS, dew, bubble, hydrate) |
| `runProcess` | CALCULATION | v1.0 | ProcessSystem or ProcessModel simulation from JSON definition |
| `runPVT` | CALCULATION | v1.1 | PVT lab experiments (CME, CVD, DL, saturation, separator, swelling, GOR, viscosity) |
| `runPipeline` | CALCULATION | v1.1 | Multiphase pipeline flow (Beggs & Brill) |
| `calculateStandard` | CALCULATION | v1.1 | Gas/oil quality per 22 standards (ISO, AGA, GPA, EN, ASTM) |
| `getPropertyTable` | CALCULATION | v1.0 | Property table across T or P range |
| `getPhaseEnvelope` | CALCULATION | v1.0 | Phase envelope (PT curve) |
| `validateInput` | ADVISORY | v1.0 | Pre-flight input validation |
| `validateResults` | ADVISORY | v1.1 | Validate results against engineering design rules |
| `searchComponents` | ADVISORY | v1.0 | Component database search |
| `getCapabilities` | ADVISORY | v1.0 | Capabilities discovery manifest |
| `getExample` | ADVISORY | v1.0 | Example templates for all tools |
| `getSchema` | ADVISORY | v1.0 | JSON Schema definitions |
| `getBenchmarkTrust` | ADVISORY | v1.2 | Per-tool validation status, accuracy bounds, limitations |
| `checkToolAccess` | ADVISORY | v1.2 | Pre-flight tool access check for governed deployments |
| `manageIndustrialProfile` | ADVISORY | v1.2 | Deployment profiles, tool access, validation enforcement |

## Stable Platform

Automation and process-inspection tools. "Stable" indicates API stability and
availability, not necessarily full industrial validation. Interfaces are stable
within v1.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `listSimulationUnits` | ADVISORY | v1.0 | List addressable equipment in a process |
| `listUnitVariables` | ADVISORY | v1.0 | List variables for a specific unit |
| `getSimulationVariable` | ADVISORY | v1.0 | Read a variable by dot-notation address |
| `setSimulationVariable` | EXECUTION | v1.0 | Set an INPUT variable and re-run |
| `saveSimulationState` | EXECUTION | v1.0 | Save process state as JSON snapshot |
| `compareSimulationStates` | ADVISORY | v1.0 | Diff two state snapshots |
| `diagnoseAutomation` | ADVISORY | v1.0 | Self-healing diagnostics for failed operations |
| `getAutomationLearningReport` | ADVISORY | v1.0 | Automation operation history and insights |
| `getProgress` | ADVISORY | v1.1 | Check progress of long-running simulations |
| `runPlugin` | PLATFORM | v1.1 | Run or list registered MCP runner plugins |

## Advanced Tools

Functional and useful, but not yet formally qualified for the industrial core.
Interfaces are stable; classification may change as qualification evidence
is added. Available in `DESKTOP_ENGINEER` and `STUDY_TEAM` modes.

"Stable" in this context indicates API stability and availability, not full
industrial validation.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `runFlowAssurance` | CALCULATION | v1.1 | Flow assurance (hydrate, wax, asphaltene, corrosion, erosion, cooldown, emulsion) |
| `runMaterialsReview` | CALCULATION | v1.5 | Process-wide material selection, degradation, CUI, remaining-life, and STID-backed integrity review |
| `crossValidateModels` | CALCULATION | v1.1 | Cross-validate process under multiple EOS models |
| `runParametricStudy` | CALCULATION | v1.1 | Multi-variable parametric sweep |
| `runBatch` | CALCULATION | v1.0 | Multi-point sensitivity sweep |
| `sizeEquipment` | CALCULATION | v1.2 | Quick equipment sizing (separator, compressor) |
| `compareProcesses` | CALCULATION | v1.2 | Compare process configurations side by side |
| `generateReport` | ADVISORY | v1.1 | Generate structured engineering reports |
| `queryDataCatalog` | ADVISORY | v1.2 | Browse thermodynamic databases (components, standards, materials, EOS models) |
| `generateVisualization` | CALCULATION | v1.2 | Inline SVG/Mermaid/HTML visualization |
| `runRelief` | CALCULATION | v1.3 | PSV sizing per API 520 (gas/liquid/two-phase) and API 521 fire heat input |
| `runLOPA` | CALCULATION | v1.3 | Layer of Protection Analysis per IEC 61511 / CCPS, with required-SIL gap analysis |
| `runSIL` | CALCULATION | v1.3 | SIL verification per IEC 61508 / 61511 (1oo1, 1oo2, 2oo3 architectures) |
| `runRiskMatrix` | CALCULATION | v1.3 | 5×5 risk matrix scoring per ISO 31000 / NORSOK Z-013 |
| `runFlareNetwork` | CALCULATION | v1.3 | Flare radiation profile and API 521 safe-distance contour |
| `runHAZOP` | CALCULATION | v1.4 | Simulation-backed IEC 61882 HAZOP worksheets from ProcessSystem scenarios and document evidence |
| `runBarrierRegister` | CALCULATION | v1.4 | Evidence-linked PSF/SCE barrier register validation with LOPA/SIL/bow-tie/QRA handoffs |
| `runSafetySystemPerformance` | CALCULATION | v1.4 | Active/passive safety-system performance analysis with quantitative SIL/PFD bridge |

## Experimental Tools

Functional but interfaces may evolve between minor versions. Includes
high-autonomy execution tools that require external validation and
domain-specific runners with limited qualification evidence.

`DESKTOP_ENGINEER` only. Blocked in all other modes by code-level
`enforceAccess()` guards.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `solveTask` | EXECUTION | v1.1 | Autonomous task solver — results require independent engineer review |
| `composeWorkflow` | EXECUTION | v1.1 | Chain simulation steps into multi-domain workflows |
| `manageSession` | EXECUTION | v1.1 | Persistent simulation sessions (create, modify, run, snapshot, restore) |
| `runReservoir` | CALCULATION | v1.1 | Material balance reservoir simulation |
| `runFieldEconomics` | CALCULATION | v1.1 | NPV/IRR/cash flow with fiscal regimes + decline curves |
| `runDynamic` | CALCULATION | v1.1 | Dynamic transient simulation with auto-instrumented controllers |
| `runBioprocess` | CALCULATION | v1.1 | Bioprocessing reactors (AD, fermentation, gasification, pyrolysis) |
| `streamSimulation` | PLATFORM | v1.2 | Async simulation with incremental polling |
| `composeMultiServerWorkflow` | PLATFORM | v1.2 | Multi-server orchestration across MCP servers |
| `manageSecurity` | PLATFORM | v1.2 | API key management, rate limiting, audit logging |
| `manageState` | PLATFORM | v1.2 | Persist/restore simulation states across server restarts |
| `manageValidationProfile` | PLATFORM | v1.2 | Jurisdiction-specific validation profiles (NCS, UKCS, GoM, Brazil) |
| `bridgeTaskWorkflow` | ADVISORY | v1.2 | Convert MCP tool output to task_solve results.json format |

Execution tools (`solveTask`, `composeWorkflow`, `manageSession`) perform
multi-step or stateful operations. They are **not part of any governed tier**
and must not be used for engineering decisions without independent validation.

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
what validation level is enforced. Each profile defines enforced constraints
on tool availability, validation behavior, and execution permissions.

| Profile | Description | Tool Access | Auto-Validation |
|---------|-------------|-------------|-----------------|
| `DESKTOP_ENGINEER` | Full access for individual engineering work | Core + Advanced + Experimental (all tiers, labeled) | On by default |
| `STUDY_TEAM` | Collaborative team environment | Core + Advanced (no PLATFORM) | Enforced |
| `DIGITAL_TWIN` | Advisory-only for live operations | ADVISORY + CALCULATION only; no plant control, no write-back, no autonomous execution | Enforced |
| `ENTERPRISE` | Restricted to approved industrial core | Industrial core only (21 tools) | Enforced, approval gates on EXECUTION |

**ENTERPRISE** constraints:

- Restricted to approved industrial toolset
- Execution tools require explicit approval (if enabled)
- Platform-level tools disabled
- Validation is enforced and cannot be bypassed

Default mode: `DESKTOP_ENGINEER`.

### Code-Level Enforcement

Governance is not just documented — it is enforced in code. Every Advanced and
Experimental tool calls `IndustrialProfile.enforceAccess(toolName)` as its first
operation. When a tool is blocked, the response is:

```json
{
  "status": "blocked",
  "tool": "runReservoir",
  "mode": "ENTERPRISE",
  "tier": "EXPERIMENTAL",
  "reason": "Tool 'runReservoir' is not available in ENTERPRISE mode.",
  "remediation": "Switch to DESKTOP_ENGINEER mode or request approval."
}
```

The enforcement method returns null (allowed) or a structured error JSON (blocked).
This ensures no Advanced/Experimental tool can execute in a restricted mode
regardless of how it is called.

**DIGITAL_TWIN advisory:** This mode supports operator decision support and
what-if analysis. It does not provide plant control, write-back to operational
systems, or autonomous action execution. A separate approval architecture
is required for any actions that affect plant operations.

### Tool Categories

Every tool is classified into exactly one category. Tool categories reflect
increasing levels of operational impact and therefore increasing governance
requirements.

| Category | Description | Examples |
|----------|-------------|---------|
| `ADVISORY` | Read-only discovery and validation; always allowed | `getCapabilities`, `getExample`, `getSchema`, `validateInput`, `searchComponents` |
| `CALCULATION` | Stateless engineering calculations | `runFlash`, `runProcess`, `runPVT`, `runPipeline`, `runMaterialsReview`, `calculateStandard` |
| `EXECUTION` | State-modifying operations; may require approval | `setSimulationVariable`, `manageSession`, `solveTask` |
| `PLATFORM` | Security, persistence, multi-server; restricted in production | `manageSecurity`, `manageState`, `composeMultiServerWorkflow` |

### Industrial Core Toolset

These 21 tools form the approved industrial subset for governed deployments.
The industrial core toolset represents tools intended for controlled engineering use.
These tools vary in validation maturity and should be interpreted according to their
benchmark trust metadata.

Each has documented validation basis, known accuracy bounds, and clear
error/warning behavior:

```
runFlash, runProcess, runPVT, runPipeline, calculateStandard,
getPropertyTable, getPhaseEnvelope, validateInput, validateResults,
searchComponents, getCapabilities, getExample, getSchema,
getBenchmarkTrust, checkToolAccess, manageIndustrialProfile,
listSimulationUnits, listUnitVariables, getSimulationVariable,
compareSimulationStates, diagnoseAutomation, getAutomationLearningReport,
getProgress
```

Tools such as `runFlowAssurance`, `runMaterialsReview`, `crossValidateModels`, `runParametricStudy`,
`runBatch`, `sizeEquipment`, `compareProcesses`, and `generateReport` are
available as **Advanced** tools and may be promoted to the core as formal
qualification evidence is added.

### Governance Tools (Stable)

Governance tools provide visibility into access control, validation maturity,
and deployment configuration.

| Tool | Status | Since | Description |
|------|--------|-------|-------------|
| `manageIndustrialProfile` | **Stable** | v1.2 | Deployment profiles, tool access, validation enforcement |
| `getBenchmarkTrust` | **Stable** | v1.2 | Per-tool validation status, accuracy bounds, limitations |
| `checkToolAccess` | **Stable** | v1.2 | Pre-flight tool access check for governed deployments |

### Auto-Validation Pipeline

When auto-validation is enabled (default in all modes), the following six
CALCULATION tools automatically append an `"autoValidation"` block to the
response:

In governed deployment profiles, validation is automatically applied and cannot
be disabled.

Validation results include:

- Convergence status
- Consistency checks
- Known limitations
- Warnings for out-of-range conditions

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
`runMaterialsReview`, `calculateStandard`, `runPipeline`.

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

**Tool maturity classification:**

- **Qualified:** Validated against reference data and suitable for governed use
- **Engineering:** Generally applicable but with limited validation coverage
- **Experimental:** Research-grade, not intended for production use
