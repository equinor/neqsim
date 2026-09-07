# MCP simulation-state snapshot contract evidence

`saveSimulationState` and `compareSimulationStates` are the existing in-memory lifecycle snapshot surfaces over the canonical solved NeqSim `ProcessSystem`. Merged PR #3500 established the bounded qualification evidence; inventory 1.28 promoted both paired tools, and current inventory 1.29 retains them as `CONTRACT_TESTED` without changing production behavior or public schemas.

## Qualified software behavior

The focused Java regression and packaged STDIO harness exercise:

- snapshot creation from the canonical `simple-separation` process;
- preservation of the requested state name and version, schema version, equipment states and stream states;
- the standard MCP tool, validation and quality-gate envelope;
- equivalent bounded structure when the same canonical definition is supplied inline or through a registered model handle;
- deterministic no-change comparison for an identical emitted snapshot;
- explicit `version: 1.0 -> 1.1` reporting when only snapshot metadata changes;
- fail-closed blank process input and blank first or second comparison input;
- current inventory `1.29 / 20 explicit + 29 contract-tested + 22 confirmed gaps`, with both records retaining applicability, evidence sources and limitations.

The Java evidence is `McpRunnerContractTest`. The real packaged-MCP evidence is `neqsim-mcp-server/test_simulation_state_snapshot_protocol.py`. The read-only `MCP protocol qualification` workflow runs both before the comprehensive protocol regression. `ProcessSystemStateTest` retains the underlying canonical lifecycle-state coverage.

## Inputs, outputs and units

`saveSimulationState` accepts a canonical process definition or registered model handle plus optional snapshot name and version. It returns a versioned `ProcessSystemState` JSON view with equipment, streams, topology metadata and the standard response envelope. `compareSimulationStates` accepts two snapshot JSON strings and returns `hasChanges` plus added, removed and modified fields exposed by the current comparison implementation.

The lifecycle layer introduces no engineering unit conversion. Units, bases and numerical values inside equipment and stream state remain those of the solved canonical NeqSim model.

## Engineering and scientific boundary

This classification is deliberately limited to serialization, structural presence, route equivalence, identical and metadata-version comparison, fail-closed inputs, standard envelope evidence, and packaged transport. It does not establish:

- complete process-state capture or complete stream-value, equipment-parameter or topology-difference detection;
- reconstruction, replay, restoration or application of a snapshot to a live model;
- persistence across calls, processes, restarts, clients or deployments;
- numerical accuracy, model fidelity, convergence adequacy or component, mass or energy closure;
- distributed durability, transactionality, encryption, signing, tenant isolation or external authorization;
- optimization quality, causal troubleshooting, plant/control authority, certification or accountable engineering approval.

`manageState` remains the distinct local file-backed session-persistence contract. The normal `ProcessSystem`, `ProcessModel`, model registry and session lifecycle remain authoritative.

## Phase 0 accounting boundary

Inventory version `1.28` atomically moves the paired records from `CONFIRMED_GAP` to `CONTRACT_TESTED`, changing coverage from `20/26/25` to `20/28/23`. Current inventory `1.29 / 20+29+22` retains that paired classification. Coverage remains incomplete and `scientificValidationComplete=false`; the promotion records a bounded software contract and makes no scientific benchmark or facility-suitability claim.
