# Phase 0 automation advisory-contract evidence

This note records bounded Phase 0 software-contract evidence for the existing automation advisory tools `listSimulationUnits`, `listUnitVariables`, `getSimulationVariable`, `diagnoseAutomation`, and `getAutomationLearningReport`.

## Existing implementation boundary

All five tools use the canonical NeqSim process model. `AutomationRunner` resolves the supplied process definition or model handle to a normal solved `ProcessSystem` and then delegates discovery, variable reads, diagnostics, and learning-report retrieval to `ProcessAutomation` and `AutomationDiagnostics`. No MCP-only process representation or calculation path is introduced.

`listSimulationUnits` returns addressable unit names and equipment types. `listUnitVariables` returns the selected unit's variable registry, including addresses, variable type, default unit, source/category metadata, writeability, invalidation behavior, and applicability. `getSimulationVariable` reads one addressed value through the existing safe accessor and preserves the standard MCP envelope, provenance, validation, and quality-gate fields.

`diagnoseAutomation` interprets a failed automation address against the solved model and returns structured advisory evidence such as the error category, candidate names, remediation text, and the current process-local learning report. `getAutomationLearningReport` returns the process-local diagnostic history summary, including operation count, success rate, error-category counts, learned corrections, recent failures, and recommendations. Neither tool writes to a live plant or external operational system.

## Focused contract evidence

- `src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java` checks canonical unit discovery, variable-addressability metadata, standard-envelope variable reads, structured `UNIT_NOT_FOUND` diagnostic evidence, the deterministic zero-history learning baseline of a fresh process, and structured fail-closed `INPUT_ERROR` responses for all five tools.
- Existing `src/test/java/neqsim/mcp/runners/McpRunnerContractTest.java` already includes the automation surface in the high-use standard-response contract against `ExampleCatalog.processSimpleSeparation()`.
- `neqsim-mcp-server/test_automation_read_protocol.py` obtains the canonical simple-separation example through real MCP and exercises all five advisory tools over packaged STDIO transport, including invalid-input rejection and the machine-readable trust-accounting transition.
- `neqsim-mcp-server/test_mcp_server.py` is the authoritative packaged-protocol accounting gate and independently reconciles the full sixteen-tool `CONTRACT_TESTED` set against the Phase 0 inventory.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and packaged MCP server with Java 21 and runs the focused and comprehensive protocol harnesses with read-only repository permissions.

The diagnostic fixture deliberately uses an impossible unit name and verifies structured advice rather than asserting that any recommendation is an engineering diagnosis. The learning-report fixture uses a fresh process and freezes only the empty-history software contract; it does not claim persistent learning across processes, restarts, clients, or tenants.

## Evidence that is not established

This evidence qualifies discovery, lookup, addressability, diagnostic-response shape, process-local learning-report retrieval, standard-envelope behavior, and packaged transport only. It does **not** validate thermodynamic or process numerical accuracy, the engineering correctness of any returned variable value or diagnostic recommendation, causal diagnosis, facility topology completeness, equipment performance, control behavior, persistent model state, learning quality, multi-client or tenant isolation, or external authorization.

For `getSimulationVariable`, `CONTRACT_TESTED` therefore means that addressed read routing, requested-unit handling, provenance/validation/quality-gate envelope preservation, fail-closed invalid inputs, and packaged transport have direct evidence. The returned numerical value, model fidelity, convergence adequacy, and engineering applicability are **not benchmark-validated by this classification**.

A successful read or diagnostic is not an engineering approval, plant measurement, or control recommendation. Returned values and suggestions remain simulation/advisory outputs with the case-specific model, assumptions, units, convergence state, provenance, warnings, validation maturity, and limitations as the authoritative engineering context.

## Promotion boundary

Phase 0 inventory version `1.18` atomically promotes these five previously qualified automation advisory contracts from `CONFIRMED_GAP` to `CONTRACT_TESTED`. Accounting moves from `20 EXPLICIT_TRUST + 11 CONTRACT_TESTED + 40 CONFIRMED_GAP` to `20 EXPLICIT_TRUST + 16 CONTRACT_TESTED + 35 CONFIRMED_GAP` while keeping all numerical and engineering-validation limits explicit.

Merged #3302 supplied the focused prerequisite for `listSimulationUnits`, `listUnitVariables`, and `getSimulationVariable`; merged #3309 extended the same source and real-protocol evidence boundary to `diagnoseAutomation` and `getAutomationLearningReport`. Inventory `1.18`, Java regressions, the focused packaged-MCP harness, and the authoritative comprehensive packaged-MCP accounting now move together on the same exact head. No further promotion candidate is queued by this increment; remaining tools stay `CONFIRMED_GAP` unless separately qualified from direct evidence.

## Owner-roadmap boundary

No thermodynamic, process, pipeline, dynamics, DEXPI/P&ID, production-optimization, schema, stable response-envelope, write, live-plant, or control-command behavior changes. This qualification does not alter #2899 diagram semantics, #2911 dynamics, #2937 flash/stability behavior, or #2941 production-optimization implementation. Specialist implementation remains owned by the existing NeqSim and coordinated roadmap lanes.
