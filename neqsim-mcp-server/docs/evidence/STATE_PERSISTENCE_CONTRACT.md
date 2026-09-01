# MCP persisted-state lifecycle contract evidence

`manageState` is the existing local persistence façade for canonical NeqSim sessions. This note records the bounded Phase 0 software-contract evidence and the atomic inventory 1.21 promotion for issue #3153. It does not change production behavior.

## Qualified software behavior

The qualification exercises the existing Java implementation and one canonical model:

- `getExample(process, simple-separation)` supplies the process definition used to create a `SessionRunner` session and its authoritative `ProcessSystem`;
- two saves with the same name and version produce distinct files rather than overwriting evidence;
- the saved `neqsim-saved-state` JSON preserves format version, NeqSim version, description, canonical process definition and session metadata;
- list and information actions expose the configured local storage and saved-file counts;
- compare reports process-definition equality and explicit state/metadata comparison fields;
- load recreates a new canonical session from the saved process definition, and that session retains equipment and solved-state metadata;
- export returns a versioned `neqsim-exported-session` envelope;
- delete removes each persisted file and the final list is empty;
- unknown actions, traversal filenames and file paths outside the configured sandbox fail closed.

The focused Java regression is `StatePersistenceRunnerTest`. The real packaged STDIO contract is `neqsim-mcp-server/test_state_persistence_protocol.py`. The latter drives `manageSession` and `manageState` through their public MCP envelopes and starts the server with an isolated Java user home so no user state is read or modified.

## Inputs, outputs and compatibility

Inputs are JSON actions, an active session identifier, optional name/version/description/process definition, saved filenames, comparison filenames and an optional storage directory. Outputs are status, versioned filename/path/size, saved metadata, list metadata, restored session identity, equality diagnostics, export envelope and deletion result.

The lifecycle layer introduces no engineering units. Units and bases inside the preserved process definition or calculation result remain authoritative. The default storage root remains `~/.neqsim/saved_simulations/`; a path outside `~/.neqsim` requires explicit `NEQSIM_MCP_ALLOW_EXTERNAL_STATE_DIR=true` or `neqsim.mcp.allowExternalStateDir=true`. Existing tool names, schemas, defaults and file-format versions are unchanged.

## Engineering and scientific boundary

This is software lifecycle, transport, provenance and path-safety evidence. It does **not** establish:

- numerical accuracy, model fidelity or convergence adequacy;
- equality of results after rerunning a restored process;
- component, total-mass or energy closure;
- distributed or multi-process consistency, transactions, encryption, signing or durable service guarantees;
- facility completeness, causal troubleshooting conclusions, live-plant authority, control authority, design certification or accountable engineering approval.

The saved process definition remains the reproducible input. Any executed result remains governed by its own units, assumptions, provenance, convergence, warnings, limitations and validation maturity.

## Phase 0 accounting boundary

Inventory `1.21` classifies `manageState` as `CONTRACT_TESTED` and moves Phase 0 accounting atomically from `20 EXPLICIT_TRUST + 18 CONTRACT_TESTED + 33 CONFIRMED_GAP` to `20 + 19 + 32`. The machine-readable record, focused Java assertions, focused packaged-STDIO expectation, synchronized protocol harnesses, and authoritative `test_mcp_server.py` accounting move together. This classification establishes only the bounded local lifecycle, path-safety, provenance, and transport contract described above; it does not imply scientific validation of persisted or restored calculations.
