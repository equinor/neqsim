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
| `listSimulationUnits` | ADVISORY | v1.0 | List addressable equipment in a process |
| `listUnitVariables` | ADVISORY | v1.0 | List variables for a specific unit |
| `getSimulationVariable` | ADVISORY | v1.0 | Read a variable by dot-notation address |
| `getAdjustableParameters` | ADVISORY | v1.7 | Enumerate the bounded optimization decision space (adjustable INPUT variables) for a process |
| `compareSimulationStates` | ADVISORY | v1.0 | Diff two state snapshots |
| `diagnoseAutomation` | ADVISORY | v1.0 | Self-healing diagnostics for failed operations |
| `getAutomationLearningReport` | ADVISORY | v1.0 | Automation operation history and insights |
| `getProgress` | ADVISORY | v1.1 | Check progress of long-running simulations |
| `manageModel` | EXECUTION | v1.8 | Register a process model once and address it by `modelId` |

## Stable Platform

Automation and process-inspection tools. Advisory automation tools are also part
of the Stable Industrial Core because they are allowed in `ENTERPRISE`; execution
tools are governed as Advanced tools. "Stable" indicates API stability and
availability, not necessarily full industrial validation.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `setSimulationVariable` | EXECUTION | v1.0 | Set an INPUT variable and re-run |
| `saveSimulationState` | EXECUTION | v1.0 | Save process state as JSON snapshot |

## Advanced Tools

Functional and useful, but not yet formally qualified for the industrial core.
Interfaces are stable; classification may change as qualification evidence
is added. Available in `DESKTOP_ENGINEER` and `STUDY_TEAM` modes.

"Stable" in this context indicates API stability and availability, not full
industrial validation.

| Tool | Category | Since | Description |
|------|----------|-------|-------------|
| `runPVT` | CALCULATION | v1.1 | PVT lab experiments (CME, CVD, DL, saturation, separator, swelling, GOR, viscosity) |
| `runPipeline` | CALCULATION | v1.1 | Multiphase pipeline flow (Beggs & Brill) |
| `runFlowAssurance` | CALCULATION | v1.1 | Flow assurance (hydrate, wax, asphaltene, corrosion, erosion, cooldown, emulsion) |
| `runChemistry` | CALCULATION | v1.6 | Open chemistry and integrity calculations for scale, corrosion, inhibitors, and scavengers |
| `runWaterHammer` | CALCULATION | v1.5 | Water/liquid hammer screening for fast valve closures, pump trips, STID routes, tagreader event windows, and pressure envelopes |
| `runMaterialsReview` | CALCULATION | v1.5 | Process-wide material selection, degradation, CUI, remaining-life, and STID-backed integrity review |
| `runOpenDrainReview` | CALCULATION | v1.6 | NORSOK S-001 open-drain review from normalized STID/P&ID and tag evidence |
| `runNorsokS001Clause10Review` | CALCULATION | v1.6 | NORSOK S-001 process safety system review from C&E, SRS, PSV, and instrument evidence |
| `crossValidateModels` | CALCULATION | v1.1 | Cross-validate process under multiple EOS models |
| `runParametricStudy` | CALCULATION | v1.1 | Multi-variable parametric sweep |
| `runAgenticEngineering` | CALCULATION | v1.6 | Plan engineering workflows, score result evidence, and rank candidate studies without executing them |
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
| `runOperationalStudy` | EXECUTION | v1.5 | P&ID/tag-driven valve scenarios, field-data binding, controller response metrics, evidence-package bottleneck reports, and operating-envelope margin/trip screening on local simulation copies |
| `runRootCauseAnalysis` | CALCULATION | v1.6 | Bayesian root cause analysis integrating OREDA, historian, STID, and simulation for ranked failure hypotheses |
| `runProcessLoop` | CALCULATION | v1.7 | Build a process once, then sweep many setpoint trials through the cached `ProcessAutomation.evaluate()` primitive (per-trial convergence gating, feasibility flag, objective read-backs) for closed-loop optimization |

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
| `manageSession` | EXECUTION | v1.1 | Persistent simulation sessions (create, modify, run, snapshot, restore). Since v1.7 also supports closed-loop automation on the cached live process without rebuilding: `evaluate` (apply setpoints, run to convergence, read back objectives), `getValues`, `setValues`, `adjustables` |
| `runReservoir` | CALCULATION | v1.1 | Material balance reservoir simulation |
| `runFieldEconomics` | CALCULATION | v1.1 | NPV/IRR/cash flow with fiscal regimes + decline curves |
| `runDynamic` | CALCULATION | v1.1 | Dynamic transient simulation with auto-instrumented controllers |
| `runBioprocess` | CALCULATION | v1.1 | Bioprocessing reactors (AD, fermentation, gasification, pyrolysis) |
| `streamSimulation` | PLATFORM | v1.2 | Async simulation with incremental polling |
| `composeMultiServerWorkflow` | PLATFORM | v1.2 | Multi-server orchestration across MCP servers |
| `manageSecurity` | PLATFORM | v1.2 | API key management, rate limiting, audit logging |
| `manageState` | PLATFORM | v1.2 | Persist/restore simulation states across server restarts |
| `manageValidationProfile` | PLATFORM | v1.2 | Jurisdiction-specific validation profiles (NCS, UKCS, GoM, Brazil) |
| `runPlugin` | PLATFORM | v1.1 | Run or list registered MCP runner plugins |
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
| `water_hammer_screening` | Fast valve-closure and pump-trip hydraulic surge screening |
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
  "apiVersion": "1.0",
  "status": "success | error | blocked | approval_required",
  "tool": "runFlash",
  "data": { "canonicalPayload": "..." },
  "provenance": {
    "model": "SRK",
    "flashType": "TP",
    "convergence": { "converged": true, "iterations": 8 },
    "assumptions": ["..."],
    "limitations": ["..."]
  },
  "validation": {
    "valid": true,
    "phase": "runner",
    "message": "Runner input checks completed"
  },
  "qualityGate": {
    "verdict": "passed",
    "summary": "Calculation completed",
    "engineeringReviewRequired": true
  },
  "warnings": []
}
```

String-based runners may also preserve legacy top-level fields such as `flash`, `fluid`,
`process`, `units`, or `diff` for backward compatibility. New clients should read the canonical
payload from `data` and use `tool` to identify the MCP operation that produced the response.

### Stable response fields

| Field | Type | Guaranteed |
|-------|------|------------|
| `apiVersion` | string | Always present |
| `status` | `"success"`, `"error"`, `"blocked"`, or `"approval_required"` | Always present |
| `tool` | string | Present for tool runner responses |
| `data` | object | Present for successful responses and standardized automation/lifecycle responses |
| `provenance` | object | Present on standardized runner responses |
| `validation.valid` | boolean | Present on standardized runner responses |
| `qualityGate.verdict` | string | Present on standardized runner responses |
| `warnings` | array | Always present on standardized runner responses |

Schema resource paths use snake_case tool names such as `run_flash`, but response `tool` values use
the MCP method names such as `runFlash`. Schema lookups accept only `input` and `output` as schema
types; any other type is treated as schema-not-found.

Responses larger than 256 KiB are reduced by the shared transport guard unless
`neqsim.mcp.maxResponseBytes` or `NEQSIM_MCP_MAX_RESPONSE_BYTES` configures another limit. The
`truncation` block identifies omitted root fields and focused retrieval routes, and the legacy
top-level and canonical `data` views are reduced together. For `getCapabilities`,
`implementationInventory` and `phase0EvidenceInventory` are retained because neither has an
equivalent selective-retrieval route; larger catalog sections may be queried through `getSchema`,
`getExample`, `getBenchmarkTrust`, and the MCP catalog resources.

### Warning taxonomy

Warnings in the root `warnings` array, and any tool-specific warning details, use these standard
codes where a machine-readable code is available:

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
- Accepted input forms may be **widened** (as in v1.8, where every tool taking a
  process definition also began accepting a `modelId`). Previously valid input
  stays valid.
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
| 1.5 | 1.5.0+ | Operational evidence packages, materials review, and water-hammer screening |
| 1.6 | 1.6.0+ | Admin-gated profile changes, one-shot approvals, state sandboxing, SQL hardening |
| 1.7 | 1.7.0+ | Bounded optimization decision space (`getAdjustableParameters`, `runProcessLoop`) |
| 1.8 | 1.8.0+ | Model handles (`manageModel`), transport-resolved caller identity, principal-scoped state, bounded execution limits |

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
| `ENTERPRISE` | Restricted to approved industrial core | Industrial core only (24 tools) | Enforced, approval gates on EXECUTION |

**ENTERPRISE** constraints:

- Restricted to approved industrial toolset
- Execution tools require explicit approval (if enabled)
- Platform-level tools disabled
- Validation is enforced and cannot be bypassed

Default mode: `DESKTOP_ENGINEER`.

Startup mode can be set with `NEQSIM_MCP_PROFILE` or `neqsim.mcp.profile`.
Runtime `setActive` profile changes require `NEQSIM_MCP_ADMIN_TOKEN` or
`neqsim.mcp.adminToken` and an `adminToken` field in the tool call. The same
admin token is required for `approveTool`, which grants one execution of an
approval-gated tool.

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

Approval-gated tools return `status: "approval_required"` until an administrator
calls `manageIndustrialProfile` with `action: "approveTool"`, the target
`toolName`, and a valid `adminToken`. Approvals are consumed on the next matching
tool invocation.

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
| `CALCULATION` | Stateless engineering calculations | `runFlash`, `runProcess`, `runPVT`, `runPipeline`, `runWaterHammer`, `runMaterialsReview`, `calculateStandard` |
| `EXECUTION` | State-modifying operations; may require approval | `setSimulationVariable`, `runOperationalStudy`, `manageSession`, `solveTask` |
| `PLATFORM` | Security, persistence, multi-server; restricted in production | `manageSecurity`, `manageState`, `composeMultiServerWorkflow` |

### State Storage Sandbox

`manageState` stores files under `~/.neqsim/saved_simulations/` by default.
File names are validated, path traversal is rejected, and legacy `filePath`
loads are allowed only when the target remains inside the configured storage
directory. External storage directories require explicit opt-in with
`NEQSIM_MCP_ALLOW_EXTERNAL_STATE_DIR=true` or
`neqsim.mcp.allowExternalStateDir=true`.
### Model Handles (v1.8)

`manageModel` registers a process definition once and returns a stable
`modelId`. Any tool that accepts a process definition also accepts that handle,
so a conversation can anchor on one model instead of re-transmitting and
re-parsing the flowsheet on every call.

| Action | Input | Result |
|--------|-------|--------|
| `register` | `processJson` (JSON string **or** nested JSON object), optional `name`, `version` | `modelId`, `revision` |
| `revise` | `modelId`, updated `processJson` | same `modelId`, incremented `revision` |
| `get` | `modelId` | stored definition |
| `inspect` | `modelId` | equipment and area inventory, without running the model |
| `list` | — | models visible to the caller |
| `delete` | `modelId` | handle removed |

Contract rules:

- Handles are **content-addressed** — registering identical content returns the
  existing handle, so registration is idempotent.
- `revise` keeps the handle stable and increments `revision`, giving results a
  revision to cite.
- A value is treated as a handle only when it starts with `model_`. Inline JSON
  and file-path inputs are unaffected, so this is a widening, not a breaking
  change.
- Handles resolve only within the calling principal's tenant. An unknown or
  out-of-tenant handle returns an actionable error rather than silently falling
  through to a parse failure.
- Storage is in-process. Handles do not survive a server restart.

Tools accepting a handle: `runProcess`, `validateInput`, `listSimulationUnits`,
`listUnitVariables`, `getSimulationVariable`, `setSimulationVariable`,
`saveSimulationState`, `diagnoseAutomation`, `getAutomationLearningReport`,
`getAdjustableParameters`, `runProcessLoop`.

### Caller Identity and State Scoping (v1.8)

Credentials are never accepted as tool arguments. The transport resolves the
caller once per request and binds a principal (subject, tenant, roles, issuer)
that the governance layer evaluates.

| Transport | Identity source |
|-----------|-----------------|
| HTTP (`enterprise` profile) | OIDC bearer token claims |
| STDIO | `NEQSIM_MCP_API_KEY` environment variable |
| Local desktop | Anonymous — enforcement disabled by default |

Server state is scoped to that principal:

- **Sessions** are owned by the authenticated subject. A client-supplied
  `ownerId` is ignored when authenticated, and listing shows only the caller's
  own sessions.
- **Streaming operations** can be polled, cancelled and listed only by their
  owner.
- **Registered models** are visible only within their tenant.
- **One-shot approvals** are bound to the principal they were granted for.
- **Audit entries** record subject and tenant.

When enforcement is enabled, `manageSecurity` remains reachable so an operator
can inspect or disable enforcement, but its privileged actions
(`createApiKey`, `revokeApiKey`, `setConfig`, `getAuditLog`, `getRateLimits`)
require the configured admin token.

### Execution Limits (v1.8)

Asynchronous work runs on a bounded pool with a wall-clock timeout and a
per-principal concurrency cap, so one caller cannot starve the server and a
non-converging run cannot hold a worker indefinitely.

| Setting | Environment variable | Default |
|---------|----------------------|---------|
| `neqsim.mcp.workers` | `NEQSIM_MCP_WORKERS` | CPU count, clamped 2..16 |
| `neqsim.mcp.operationTimeoutSeconds` | `NEQSIM_MCP_OPERATION_TIMEOUT_SECONDS` | 900 |
| `neqsim.mcp.maxOperationsPerPrincipal` | `NEQSIM_MCP_MAX_OPERATIONS_PER_PRINCIPAL` | 5 |

An operation exceeding its timeout is cancelled and reported with status
`timed_out`. Exceeding the per-principal cap returns a `CONCURRENCY_LIMIT`
error rather than queueing. Active limits are reported by `getCapabilities`
under `modelLifecycle.executionPolicy` and by `streamSimulation(action='list')`.

`runCapability(action='invoke')` has a separate five-second in-process worker budget for bounded
static calculations. It rejects MCP runners and dispatchers, raw generic containers, requests over
64 KiB, argument arrays over 4096 elements, and results over 256 KiB. Conversion and serialization
are included in that budget. Cancellation uses Java interruption and is cooperative, not a hard
process kill; calculations that may run for a long time or ignore interruption must use a curated
runner, `runProcess`, or an isolated external execution environment.

### Transport Security & Observability (opt-in)

The HTTP transport supports two enterprise-grade, transport-layer capabilities
that complement the application-level governance described above. Both are
**disabled by default** so the server starts with no external dependencies, and
both activate together under the Quarkus `enterprise` profile
(`-Dquarkus.profile=enterprise` or `QUARKUS_PROFILE=enterprise`).

| Capability | Default | Enable | Purpose |
|------------|---------|--------|---------|
| OIDC bearer-token auth | off (`quarkus.oidc.tenant-enabled=false`) | `enterprise` profile + `quarkus.oidc.auth-server-url` and `quarkus.oidc.client-id` | Authenticate `/mcp` requests against an enterprise identity provider |
| OpenTelemetry tracing | off (`quarkus.otel.sdk.disabled=true`) | `enterprise` profile + OTLP endpoint (`quarkus.otel.exporter.otlp.traces.endpoint`) | Export distributed traces for governance/observability |

These are transport-level concerns and do **not** change the tool contract,
response envelope, or governance enforcement. They apply only to the HTTP
transport; the STDIO transport is unaffected.

Hosting the server for a remote MCP client (for example Microsoft Copilot
Studio) additionally requires, per the MCP Streamable HTTP specification, a
public HTTPS endpoint, authentication on every connection, and strict `Origin`
validation. The `enterprise` profile provides these: it binds `0.0.0.0`,
requires an authenticated principal on `/mcp`, redirects insecure requests, and
restricts CORS to the explicit allowlist in `NEQSIM_MCP_ALLOWED_ORIGINS`. That
allowlist is the `Origin` gate against DNS-rebinding and must never be widened
to `*`. Note that the identity established here is what
[Caller Identity and State Scoping](#caller-identity-and-state-scoping-v18)
consumes, so transport configuration does affect which state a caller can reach.

### Industrial Core Toolset

These 24 tools form the approved industrial subset for governed deployments.
The industrial core toolset represents tools intended for controlled engineering use.
These tools vary in validation maturity and should be interpreted according to their
benchmark trust metadata.

Each has documented validation basis, known accuracy bounds, and clear
error/warning behavior:

```
runFlash, runProcess, calculateStandard,
getPropertyTable, getPhaseEnvelope, validateInput, validateResults,
searchComponents, getCapabilities, getExample, getSchema,
getBenchmarkTrust, checkToolAccess, manageIndustrialProfile,
listSimulationUnits, listUnitVariables, getSimulationVariable,
getAdjustableParameters, compareSimulationStates, diagnoseAutomation,
getAutomationLearningReport, getProgress, manageModel, inspectApi
```

Tools such as `runFlowAssurance`, `runWaterHammer`, `runMaterialsReview`, `crossValidateModels`, `runParametricStudy`,
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

When auto-validation is enabled (default in all modes), selected
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
`runWaterHammer`, `runMaterialsReview`, `calculateStandard`, `runPipeline`.

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
