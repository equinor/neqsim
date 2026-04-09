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

## Experimental Tools

These tools are useful but their interface may change. Do not depend on
exact field names or response structure across releases.

| Tool | Status | Notes |
|------|--------|-------|
| *(none currently)* | | |

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
