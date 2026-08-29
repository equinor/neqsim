# Reusable model-registry contract evidence

This note records the bounded Phase 0 evidence and classification for the existing
`manageModel` software contract used by the NeqSim MCP server. The contract is
implemented by the core `ModelRegistry`; registered handles resolve back to the
same canonical NeqSim process definition and are consumed by normal
`ProcessSystem` / `ProcessModel` runners. There is no MCP-only process simulator
or second flowsheet representation.

## Foundation and engineering value

Merged PR #2875 introduced reusable model handles after a real converted plant
model showed that repeatedly resending, parsing, and solving the same flowsheet
made interactive troubleshooting impractical. Current `ModelRegistry` keeps
definitions in a bounded in-memory working set, issues stable content-derived
`model_*` handles, scopes records to the authenticated principal and tenant,
tracks revisions, reuses solved `ProcessSystem` instances for read-only routes,
and invalidates cached solves on revision or deletion.

The existing `ModelRegistryTest` is the source-level contract. It covers:

- handle registration and resolution;
- idempotent content-addressed registration and distinct-content separation;
- stable-handle revisioning;
- structure inspection and documented response fields;
- owner/tenant isolation and unknown-handle failure;
- nested-object and escaped-string definitions;
- cached-solve reuse plus revision/delete invalidation;
- inline-JSON compatibility;
- handle use through process and automation runner paths; and
- usage accounting and bounded registry behavior.

This evidence reuses #2875 rather than adding a competing model/session layer.

## Packaged MCP qualification

Merged PR #3325 added `test_model_registry_protocol.py`, which starts the exact
packaged server over STDIO and obtains the canonical
`process/simple-separation` fixture through MCP. It qualifies seven bounded
protocol scenarios:

1. registration plus idempotent repeated registration;
2. `get`, caller-visible `list`, and structure-only `inspect`;
3. use of the issued handle through canonical `runProcess` and
   `listSimulationUnits`;
4. stable model identity with monotonic revision and retained updated
   definition;
5. fail-closed invalid definitions, unknown actions, and unknown handles;
6. deletion plus rejection of the deleted handle through both registry and
   process-execution paths; and
7. atomic Phase 0 classification: inventory 1.19 reports
   **20 `EXPLICIT_TRUST` + 17 `CONTRACT_TESTED` + 34 `CONFIRMED_GAP`**, with
   `manageModel` classified `CONTRACT_TESTED` from its direct source and
   packaged-protocol evidence.

The `MCP protocol qualification` workflow runs the focused `ModelRegistryTest`
under Java 21, this packaged transport harness, and the authoritative
comprehensive MCP protocol regression. The primary protocol accounting also
includes `manageModel`, so machine-readable inventory and protocol expectations
move together.

## Qualification boundary

Inventory 1.19 promotes only the bounded software-contract classification of
`manageModel`. It does not add a numerical benchmark or scientific-validation
claim. `CONTRACT_TESTED` means the published lifecycle/routing contract has
direct evidence; it does not mean results produced by an arbitrary registered
model are validated for an engineering purpose.

The bounded evidence supports registry/lifecycle/transport behavior only. It
does **not** establish:

- thermodynamic or process-model numerical accuracy;
- convergence adequacy, mass/energy closure, or facility fidelity;
- validity of engineering conclusions obtained from a registered model;
- persistence or durability across MCP server restarts;
- correctness of an external identity provider or authorization policy;
- multi-process or distributed cache coherence;
- protection against every deployment-specific memory-pressure condition;
- plant authority, control-system write permission, design certification, or
  accountable engineering approval.

The source-level tests exercise principal/tenant isolation, but the focused
STDIO harness intentionally runs as one local caller. Production deployment
identity and tenant enforcement remain governed by the security foundation from
#2874 and deployment configuration.

`ModelRegistry` stores a working set, not an engineering document archive.
Revision labels and model handles are traceability aids; they do not replace
project document control or provenance for source facility data.

## Compatibility and documentation impact

No public MCP tool name, argument schema, response envelope, process JSON
grammar, model solver, numerical tolerance, security default, or agent/skill
handoff changes. Existing inline `processJson` callers remain supported. No
companion agent/skill repository update is required.

Owner boundaries remain unchanged: DEXPI/P&ID semantics belong to #2899,
dynamics to #2911, TP-flash/stability/performance to #2937, and production
optimization #2941 is complete. This classification does not import or
duplicate those implementations.

## Next dependency

After inventory 1.19 is merged and current `master` is re-audited, Phase 0
continues from the remaining **34 `CONFIRMED_GAP`** tool contracts. The next
promotion must again be evidence-led and atomic across machine-readable
coverage and authoritative protocol accounting. Component-wise, energy, and
facility-wide conservation evidence remain separate engineering-validation
dependencies and are not implied by this registry classification.
