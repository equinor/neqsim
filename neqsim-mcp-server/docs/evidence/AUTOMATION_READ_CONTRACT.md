# Phase 0 automation advisory-contract evidence

This note records bounded Phase 0 software-contract evidence for the existing automation advisory tools `listSimulationUnits`, `listUnitVariables`, `getSimulationVariable`, `diagnoseAutomation`, and `getAutomationLearningReport`.

## Existing implementation boundary

All five tools use the canonical NeqSim process model. `AutomationRunner` resolves the supplied process definition or model handle to a normal solved `ProcessSystem` and then delegates discovery, variable reads, diagnostics, and learning-report retrieval to `ProcessAutomation` and `AutomationDiagnostics`. No MCP-only process representation or calculation path is introduced.

`listSimulationUnits` returns addressable unit names and equipment types. `listUnitVariables` returns the selected unit's variable registry, including addresses, variable type, default unit, source/category metadata, writeability, invalidation behavior, and applicability. `getSimulationVariable` reads one addressed value through the existing safe accessor and preserves the standard MCP envelope, provenance, validation, and quality-gate fields.

`diagnoseAutomation` interprets a failed automation address against the solved model and returns structured advisory evidence such as the error category, candidate names, remediation text, and the current process-local learning report. `getAutomationLearningReport` returns the process-local diagnostic history summary, including operation count, success rate, error-category counts, learned corrections, recent failures, and recommendations. Neither tool writes to a live plant or external operational system.

## Focused contract evidence

- `src/test/java/neqsim/mcp/runners/AutomationReadContractTest.java` checks canonical unit discovery, variable-addressability metadata, standard-envelope variable reads, structured `UNIT_NOT_FOUND` diagnostic evidence, the deterministic zero-history learning baseline of a fresh process, and structured fail-closed `INPUT_ERROR` responses for all five tools.
- Existing `src/test/java/neqsim/mcp/runners/McpRunnerContractTest.java` already includes the automation surface in the high-use standard-response contract against `ExampleCatalog.processSimpleSeparation()`.
- `neqsim-mcp-server/test_automation_read_protocol.py` obtains the canonical simple-separation example through real MCP and exercises all five advisory tools over packaged STDIO transport, including invalid-input rejection.
- `.github/workflows/mcp_protocol_qualification.yml` builds the exact NeqSim core and packaged MCP server with Java 21 and runs this focused harness with read-only repository permissions.

The diagnostic fixture deliberately uses an impossible unit name and verifies structured advice rather than asserting that any recommendation is an engineering diagnosis. The learning-report fixture uses a fresh process and freezes only the empty-history software contract; it does not claim persistent learning across processes, restarts, clients, or tenants.

## Evidence that is not established

This evidence qualifies discovery, lookup, addressability, diagnostic-response shape, process-local learning-report retrieval, standard-envelope behavior, and packaged transport only. It does **not** validate thermodynamic or process numerical accuracy, the engineering correctness of any returned variable value or diagnostic recommendation, causal diagnosis, facility topology completeness, equipment performance, control behavior, persistent model state, learning quality, multi-client or tenant isolation, or external authorization.

A successful read or diagnostic is not an engineering approval, plant measurement, or control recommendation. Returned values and suggestions remain simulation/advisory outputs with the case-specific model, assumptions, units, convergence state, provenance, warnings, validation maturity, and limitations as the authoritative engineering context.

## Promotion boundary

The Phase 0 inventory remains version `1.17` with `20 EXPLICIT_TRUST + 11 CONTRACT_TESTED + 40 CONFIRMED_GAP` in this qualification increment. All five tools covered by this note remain `CONFIRMED_GAP` until machine-readable coverage and the authoritative primary packaged-protocol accounting can be changed together on one exact validated head.

Merged #3302 supplied the focused prerequisite for the three discovery/read contracts. This increment extends the same source and real-protocol evidence boundary to `diagnoseAutomation` and `getAutomationLearningReport`. After merge and current-master re-audit, the coherent atomic promotion target is therefore the five-tool automation advisory set, moving Phase 0 accounting from 20/11/40 to 20/16/35 only if the machine-readable coverage records and primary protocol expectations move together and exact-head validation passes.

## Owner-roadmap boundary

No thermodynamic, process, pipeline, dynamics, DEXPI/P&ID, production-optimization, schema, stable response-envelope, write, live-plant, or control-command behavior changes. This qualification does not alter #2899 diagram semantics, #2911 dynamics, #2937 flash/stability behavior, or #2941 production-optimization implementation. Specialist implementation remains owned by the existing NeqSim and coordinated roadmap lanes.
