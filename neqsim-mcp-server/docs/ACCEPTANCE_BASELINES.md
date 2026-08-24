# MCP Phase 0 acceptance baselines

`McpAcceptanceBaselineRunner` executes the four public synthetic fixtures defined by
`McpAcceptanceFixtureCatalog` through the production `FlashRunner` and `ProcessRunner` contracts. It is an on-demand
test and evidence harness, not a public MCP tool, a second simulator, a process-performance optimizer, or a plant
qualification workflow.

Run the focused exact-head gate from the repository root:

```bash
./mvnw test -Dtest=McpAcceptanceBaselineRunnerTests,ProcessRunnerBalanceEvidenceTests
```

The test validates the compact baseline JSON returned by the runner. Every fixture is executed twice. A caller that
needs the exact numeric observations can serialize the returned `JsonObject` in its governed evidence store; the
repository test deliberately does not emit environment-specific baseline data through the disabled test logger. The
report includes:

| Evidence group | Recorded fields | Interpretation boundary |
| --- | --- | --- |
| environment | Java/runtime/OS identity, processors, maximum heap | Required context for non-portable observations |
| runtime | outer wall time and runner provenance time for both executions | Observation, not a portable regression threshold |
| heap snapshot | used-heap snapshots and deltas | JVM proxy; not allocation or peak-memory profiling |
| payload and guard | raw/guarded UTF-8 bytes, configured bound, trimming and retrieval guidance | MCP response-delivery evidence only |
| convergence | explicit provenance verdict, warnings, validation and quality-gate status | Does not substitute for physical validation |
| determinism | SHA-256 of stable response content after temporal/correlation metadata removal | Repeated outcome check for the same fixture and runtime |
| report evidence | report presence/size and canonical replay availability | Structural coverage, not subjective engineering usefulness |
| balance evidence | explicitly named mass/component/energy closure response paths | Missing numerical closure is a recorded gap, never inferred from success |

## Acceptance and failure behavior

All four fixtures must return `success`, explicitly report convergence, remain deterministic after run-identity metadata
is removed, and fit the configured shared response guard. A large raw payload may pass by being safely trimmed while
retaining the standard envelope and selective-retrieval guidance.

Balance evidence is deliberately fail-visible. A successful `runProcess` response without an explicitly named numeric
mass, component, or energy closure field is classified as `GAP_NO_NUMERIC_CLOSURE_IN_MCP_RESPONSE`. The harness does not
convert convergence, a non-empty report, or a validation label into a balance claim.

For multi-area `ProcessModel` execution, `runProcess` now exposes the existing solver-native
`convergenceReport` returned by `ProcessModel.getConvergenceReportJson()`. Its `massClosure` object carries the numeric
`relativeError`, configured `tolerance`, summary, and worst-unit evidence already used by the canonical ProcessModel
convergence machinery. `relativeError` is a fraction of detected plant feed, not an independently reconstructed MCP
balance. The same report is retained in both the legacy top-level response and the strict `data` block.

This closes one defensible response-evidence gap for the public multi-area acceptance fixture. It does **not** establish
component closure or energy closure, and it does not invent a corresponding balance for single-area `ProcessSystem`
responses. Those response paths remain explicit gaps until the canonical model exposes equally defensible numerical
evidence. A successful solver result therefore remains distinct from conservation evidence and scientific validation.

## Ownership and qualification boundaries

- Generic `ProcessSystem` and `ProcessModel` performance optimization remains owned by #2939.
- Plant-wide optimization and constraint fidelity remain owned by #3154 and the completed #2941 foundation.
- Flash/stability algorithms remain owned by #2937, dynamics by #2911, and DEXPI/P&ID ingestion by #2899.
- Runtime, heap and payload values are comparable only for equivalent environments and fixture inputs.
- The harness does not establish scientific accuracy, design certification, representative-plant fidelity, causality,
  accountable engineering approval, or authority to control a live plant.

Exact-head hosted CI is pass/fail execution evidence for the asserted contract, while persisted numeric observations
remain the responsibility of the invoking evidence workflow. The static capability inventory exposes only the bounded
measurement contract; it never performs these large calculations during capability discovery.
