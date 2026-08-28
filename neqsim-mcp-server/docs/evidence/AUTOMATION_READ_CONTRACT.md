# Phase 0 automation read-contract evidence

This note records bounded Phase 0 software-contract evidence for the existing read-only automation introspection tools `listSimulationUnits`, `listUnitVariables`, and `getSimulationVariable`.

## Existing implementation boundary

All three tools use the canonical NeqSim process model. `AutomationRunner` resolves the supplied process definition or model handle to a normal solved `ProcessSystem` and then delegates unit/variable discovery and reads to `ProcessAutomation`. No MCP-only process representation or calculation path is introduced.

`listSimulationUnits` returns addressable unit names and equipment types. `listUnitVariables` returns the selected unit's variable registry, including addresses, variable type, default unit, source/category metadata, writeability, invalidation behavior, and applicability. `getSimulationVariable` reads one addressed value through the existing safe accessor and preserves the standard MCP envelope, provenance, validation, and quality-gate fields.

## Focused contract evidence

- `src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java` checks canonical unit discovery, variable-addressability metadata, standard-envelope variable reads, and structured fail-closed `INPUT_ERROR` responses.
- Existing `src/test/java/neqsim/mcp/runners/McpRunnerContractTest.java` already includes the same three tools in the high-use standard-response contract against `ExampleCatalog.processSimpleSeparation()`.
- `neqsim-mcp-server/test_automation_read_protocol.py` obtains the canonical simple-separation example through real MCP and exercises all three read tools over packaged STDIO transport, including invalid-input rejection.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and packaged MCP server with Java 21 and runs the focused harness with read-only repository permissions.

## Evidence that is not established

This evidence qualifies lookup, addressability, response-envelope, and packaged-transport behavior only. It does **not** validate thermodynamic or process numerical accuracy, the engineering correctness of any returned variable value, facility topology completeness, equipment performance, control behavior, persistent model state, multi-client isolation, or external authorization.

A successful read is not an engineering approval or plant measurement. Returned values remain simulation outputs with the case-specific model, assumptions, units, convergence state, provenance, warnings, validation maturity, and limitations as the authoritative engineering context.

## Promotion boundary

The Phase 0 inventory remains version `1.17` with `20 EXPLICIT_TRUST + 11 CONTRACT_TESTED + 40 CONFIRMED_GAP` in this qualification increment. These three tools remain `CONFIRMED_GAP` until machine-readable coverage and the authoritative primary packaged-protocol accounting are promoted atomically on one later exact validated head. This PR supplies the focused source and real-protocol prerequisite for that future classification step.

## Owner-roadmap boundary

No thermodynamic, process, pipeline, dynamics, DEXPI/P&ID, production-optimization, schema, stable response-envelope, write, live-plant, or control-command behavior changes. Specialist implementation remains owned by the existing NeqSim and coordinated roadmap lanes.
