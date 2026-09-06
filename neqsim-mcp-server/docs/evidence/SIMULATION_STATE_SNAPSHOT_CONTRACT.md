# MCP simulation-state snapshot qualification evidence

`saveSimulationState` and `compareSimulationStates` are the existing in-memory lifecycle snapshot surfaces over the canonical solved NeqSim `ProcessSystem`. This note records the bounded qualification evidence for issue #3153. It does not change production behavior or promote either tool in the Phase 0 inventory.

## Qualified software behavior

The focused Java regression and packaged STDIO harness exercise:

- snapshot creation from the canonical `simple-separation` process;
- preservation of the requested state name and version, schema version, equipment states and stream states;
- the standard MCP tool, validation and quality-gate envelope;
- equivalent bounded structure when the same canonical definition is supplied inline or through a registered model handle;
- deterministic no-change comparison for an identical emitted snapshot;
- explicit `version: 1.0 -> 1.1` reporting when only snapshot metadata changes;
- fail-closed blank process input and blank first or second comparison input;
- unchanged inventory `1.27 / 20 explicit + 26 contract-tested + 25 confirmed gaps`, with both tools remaining `CONFIRMED_GAP` until a later atomic promotion from merged evidence.

The Java evidence is `McpRunnerContractTest`. The real packaged-MCP evidence is `neqsim-mcp-server/test_simulation_state_snapshot_protocol.py`. The read-only `MCP protocol qualification` workflow runs both before the comprehensive protocol regression.

## Inputs, outputs and units

`saveSimulationState` accepts a canonical process definition or registered model handle plus optional snapshot name and version. It returns a versioned `ProcessSystemState` JSON view with equipment, streams, topology metadata and the standard response envelope. `compareSimulationStates` accepts two snapshot JSON strings and returns `hasChanges` plus added, removed and modified fields exposed by the current comparison implementation.

The lifecycle layer introduces no engineering unit conversion. Units, bases and numerical values inside equipment and stream state remain those of the solved canonical NeqSim model.

## Engineering and scientific boundary

This qualification is deliberately limited to serialization, structural presence, route equivalence, metadata-version comparison and fail-closed inputs. It does not establish:

- complete stream-value, equipment-parameter or topology-difference detection;
- reconstruction, replay or application of a snapshot to a live model;
- persistence across calls, processes, restarts, clients or deployments;
- numerical accuracy, model fidelity, convergence adequacy or component, mass or energy closure;
- distributed durability, transactionality, encryption, signing or tenant isolation;
- optimization quality, causal troubleshooting, plant/control authority, certification or accountable engineering approval.

`manageState` remains the distinct local file-backed session-persistence contract. The normal `ProcessSystem`, `ProcessModel`, model registry and session lifecycle remain authoritative.

## Phase 0 accounting boundary

This qualification leaves inventory version `1.27` unchanged at `20 EXPLICIT_TRUST + 26 CONTRACT_TESTED + 25 CONFIRMED_GAP`. `saveSimulationState` and `compareSimulationStates` remain confirmed gaps while this evidence is open or draft. A later atomic promotion may move both records only after this qualification merges and all coupled machine-readable, Java, packaged-protocol and documentation accounting can change together.
