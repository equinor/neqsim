# MCP published-surface inventory

This inventory records the public MCP protocol surface on campaign issue #3153's Phase 0
baseline. The focused protocol regression in `test_mcp_server.py` obtains every entry through the
running server's standards-conforming list operations; it does not infer publication from a
manually maintained Java method list.

| Surface | Count | Protocol evidence | Authoritative implementation |
| --- | ---: | --- | --- |
| Tools | 71 | `tools/list` | `NeqSimTools` MCP annotations |
| Static resources | 7 | `resources/list` | `NeqSimResources` MCP annotations |
| Resource templates | 7 | `resources/templates/list` | `NeqSimResources` MCP annotations |
| Guided prompts | 9 | `prompts/list` | `NeqSimPrompts` MCP annotations |
| Schema entries | 71 tools / 142 documents | `resources/read` | `SchemaCatalog` |
| Example entries | 24 categories / 114 documents | `resources/read` | `ExampleCatalog` |
| Tool implementations | 71 bindings / 60 classes | `getCapabilities.implementationInventory` | `McpImplementationInventory` |
| Factory equipment | 207 types | `getCapabilities.implementationInventory` | `EquipmentFactory` |
| Engineering report paths | 2 | `getCapabilities.implementationInventory` | `ReportRunner`, `TaskWorkflowBridge` |
| MCP Java test classes | 69 | `getCapabilities.phase0EvidenceInventory` | `src/test/java/neqsim/mcp/**/*Test.java` |
| MCP protocol scenarios | 94 | `getCapabilities.phase0EvidenceInventory` | `test_mcp_server.py` |
| Focused API protocol scenarios | 3 | `getCapabilities.phase0EvidenceInventory` | `test_inspect_api_protocol.py` |
| MCP guides | 8 | `getCapabilities.phase0EvidenceInventory` | Core guides, foundation traceability, fixtures, baseline harness, and campaign matrix |
| Explicit benchmark-trust pages | 20 of 71 tools | `getBenchmarkTrust` and `getCapabilities.phase0EvidenceInventory` | `BenchmarkTrust` |
| Trust coverage records | 71 = 20 explicit benchmark + 22 bounded contract-tested software contracts + 29 confirmed gaps | `getCapabilities.phase0EvidenceInventory` | `BenchmarkTrust`, `McpImplementationInventory`, MCP contract tests |
| Contract-promotion candidates | 0 | `getCapabilities.phase0EvidenceInventory` | No candidate is queued in inventory 1.24; any future promotion must move machine-readable coverage and primary protocol accounting atomically |

The tool regression asserts the exact 71-name set grouped by its current trust tier. It also calls
`getCapabilities` and requires `toolCatalogCoverage.complete`, equal published and described tool
counts, and empty missing/undeclared descriptor lists. That reconciles transport publication with
the governance and capability registries without introducing a second MCP-only simulator.

## Resources

| Static resource | Resource template |
| --- | --- |
| `neqsim://components` | `neqsim://components/{name}` |
| `neqsim://data-tables` | `neqsim://materials/{type}` |
| `neqsim://example-catalog` | `neqsim://examples/{category}/{name}` |
| `neqsim://models` | `neqsim://api/{className}` |
| `neqsim://schema-catalog` | `neqsim://schemas/{tool}/{type}` |
| `neqsim://setup-templates` | `neqsim://setup-templates/{id}` |
| `neqsim://standards` | `neqsim://standards/{code}` |

The two columns are independent inventories; rows do not imply a one-to-one relationship.

## Schemas and examples

Every one of the 71 schema-backed tool names has an input and output document at the canonical
`neqsim://schemas/{tool}/input` and `neqsim://schemas/{tool}/output` resource URIs. The protocol
regression reads all 142 documents and requires each to decode as a JSON Schema object with a
top-level `properties` map. It also requires the schema catalog's URI fields to match the requested
resource exactly, preventing stale or redirected catalog entries from passing silently.

The example catalog contains 114 entries across 24 categories. Seventy-one are canonical
`tool/{schema_tool_name}` starters, and their names must exactly equal the schema catalog names.
The regression reads every catalog entry through `neqsim://examples/{category}/{name}` and requires
valid JSON objects. Domain examples remain representative inputs or contract-level starters; their
presence does not by itself establish scientific validation, benchmark maturity, or fitness for a
specific facility decision.

## Implementation, equipment, and reporting traceability

`getCapabilities.implementationInventory` preserves a compact exact binding from every one of the
71 public MCP tools to the Java class that performs its work. The 71 bindings currently resolve to
60 implementation classes. Contract tests require the registry to have no missing or undeclared
tool and load every named class from the running NeqSim version. This supplements the existing
`runnerClass` descriptor: server-facade tools retain that compatibility field while
`implementationClass` identifies the actual catalog, runner, registry, validator, or policy class.

Process construction continues through the canonical
`neqsim.process.equipment.EquipmentFactory` and
`neqsim.process.processmodel.JsonProcessBuilder`. The inventory exposes the same 207 name-only
factory-supported equipment types as `processJsonContract.supportedEquipmentTypes`; it does not
claim that every type has the same input grammar, validation maturity, or multi-port construction
support. Equipment requiring additional constructor context remains outside this name-only list.

Two bounded reporting paths are explicit:

| Path | MCP tool | Implementation | Output boundary |
| --- | --- | --- | --- |
| Engineering report | `generateReport` | `ReportRunner` | Markdown, tables, chart data, validation, and summary in the MCP response |
| Task-workflow handoff | `bridgeTaskWorkflow` | `TaskWorkflowBridge` | `results.json`-compatible handoff for a separately reviewed rendering step |

Neither path writes to a live plant system. The direct path does not persist a file, and the bridge
does not claim that an external Word/HTML artifact has been generated or engineering-approved.

## Tests, guides, and known limitations

`getCapabilities.phase0EvidenceInventory` freezes the remaining source-evidence dimensions of the
Phase 0 inventory. The exact current source contains 69 JUnit test classes under
`src/test/java/neqsim/mcp`, 94 named scenarios in the primary real-STDIO JSON-RPC harness
`neqsim-mcp-server/test_mcp_server.py`, and three focused packaged-MCP API-inspection scenarios in
`neqsim-mcp-server/test_inspect_api_protocol.py`. The primary protocol regression independently
recounts its source tree and fails if the manifest drifts; the focused harnesses are separately
executed by the read-only `MCP protocol qualification` workflow. The validation-profile and
automation-advisory harnesses add bounded software-contract scenarios but do not alter the frozen
primary-scenario count. These counts identify evidence locations; they are not a claim that a test
ran or passed. Exact-head CI and recorded command output remain the execution evidence.

The eight MCP guides have distinct roles:

| Guide | Role |
| --- | --- |
| `neqsim-mcp-server/README.md` | Installation, profiles, tools, workflows, testing, and troubleshooting |
| `neqsim-mcp-server/MCP_CONTRACT.md` | Versioning, stability, envelopes, governance, security, and trust metadata |
| `neqsim-mcp-server/docs/API_REFERENCE.md` | Parameters, schemas, examples, resources, and selected result contracts |
| `neqsim-mcp-server/docs/SURFACE_INVENTORY.md` | Exact protocol, implementation, equipment, reporting, and evidence inventory |
| `neqsim-mcp-server/docs/FOUNDATION_TRACEABILITY.md` | Merged foundation capability evidence and remaining boundaries |
| `neqsim-mcp-server/docs/ACCEPTANCE_FIXTURES.md` | Four public synthetic scales and canonical execution routes |
| `neqsim-mcp-server/docs/ACCEPTANCE_BASELINES.md` | Bounded exact-run measurements, interpretation limits, and explicit evidence gaps |
| `neqsim-mcp-server/docs/CAMPAIGN_MATRIX.md` | All 66 campaign criteria and discipline-level trust maturity with explicit gaps |

The default response-size guard may omit large capability-catalog sections when the full manifest
exceeds 256 KiB. It retains `implementationInventory` and `phase0EvidenceInventory` because those
contracts have no equivalent selective-retrieval routes. Omitted catalog detail remains identified
in `truncation` and can be queried through `getSchema`, `getExample`, `getBenchmarkTrust`, and the
MCP catalog resources.

`BenchmarkTrust` currently contains 20 tool-specific numerical/engineering trust pages with 64
known-limit entries and 30 validation cases, of which 5 name a concrete `verifiedBy` test. The
other 51 published tools still use the compatibility-level generic `TESTED` benchmark fallback
from `getBenchmarkTrust`.

Phase 0 does not equate that generic benchmark fallback with an unresolved scientific gap when the
tool's qualified scope is a bounded software contract. `knownLimitations.coverageRecords` contains
one deterministic record for every published tool and uses three bounded states:

- `EXPLICIT_TRUST` — a tool-specific `BenchmarkTrust` page exists, with its declared maturity and
  counts for validation cases, verified cases, limitations, and unsupported conditions;
- `CONTRACT_TESTED` — bounded MCP software-contract behavior has direct source, regression, and
  real-protocol evidence listed in `contractEvidenceSources`. This applies to capability/schema/
  example discovery, trust-catalog retrieval, industrial-profile access/governance policy,
  component-name search, read-only data-catalog discovery, bounded progress retrieval,
  validation-profile governance, bounded runtime API inspection, the reusable model-registry
  lifecycle, the canonical-process session lifecycle, persisted-state lifecycle,
  adjustable-parameter discovery, and the five automation advisory discovery/read/diagnostic
  contracts. It does not validate advertised calculations, database
  contents, standards applicability, EOS accuracy, scientific claims inside trust pages, numerical
  model accuracy, calculation convergence, cancellation, durability, deployment isolation,
  external identity or authorization, facility topology completeness, a facility deployment,
  plant authority, or accountable engineering approval; or
- `CONFIRMED_GAP` — no tool-specific benchmark trust page or bounded software-contract
  classification closes the gap. The record names the implementation class, retains the
  compatibility maturity, and explicitly states that generic `TESTED` is not benchmark, accuracy,
  applicability, or no-limitations evidence.

Accordingly, `coverageComplete=true` means all 71 published tools have an explicit trust-coverage
classification. It does **not** mean the MCP surface is scientifically validated: 29 records remain
`CONFIRMED_GAP`, twenty-two are `CONTRACT_TESTED`, `scientificValidationComplete=false`, and the
overall Phase 0 `complete` flag remains false. The benchmark registry itself remains unchanged at
20 explicit pages and 51 generic benchmark fallbacks, so existing benchmark-report accounting and
protocol contracts are preserved.

### Promoted read-only catalog contracts

The two evidence-qualified candidates from inventory version 1.9 were promoted atomically to
`CONTRACT_TESTED`. The promotion changed only the non-numerical trust classification and its
frozen protocol accounting:

| Tool | Direct evidence | Boundary |
| --- | --- | --- |
| `searchComponents` | `ComponentQuery`, `ComponentQueryTest`, and real MCP searches for exact, partial, empty, and no-match queries | Component lookup behavior only; no thermodynamic calculation or component-property-model validation |
| `queryDataCatalog` | `DataCatalogRunner`, `DataCatalogRunnerTest`, and real MCP EOS/component-family catalog calls | Read-only catalog dispatch/retrieval only; no database-content, standards-applicability, EOS-accuracy, materials, or design validation |

Their full source lists and boundaries are carried directly on their `coverageRecords` entries.
The generic `BenchmarkTrust` registry is unchanged, and neither promotion adds an engineering
accuracy claim.

### Promoted progress-retrieval contract

Inventory version 1.12 promotes `getProgress` to `CONTRACT_TESTED` using the already merged bounded
evidence: `ProgressTracker`, the focused lifecycle regression in
`McpEvidenceInventoryFoundationTests`, the real-MCP `getProgress(action=listActive)` scenario, and
`docs/evidence/PROGRESS_RETRIEVAL_CONTRACT.md`. The machine-readable classification and packaged
protocol expectation move together on the same exact head, changing coverage accounting from
20/8/43 to 20/9/42.

This is a software-contract classification only. It covers active-operation discovery, point
retrieval, milestone visibility, successful completion state, missing-operation errors, and
real-protocol `listActive` retrieval. It does not validate the underlying calculation, convergence,
cancellation, durability across process restarts, deployment isolation, authorization, delivery
guarantees, or plant authority.

### Promoted runtime API-inspection contract

Inventory version 1.15 promotes `inspectApi` to `CONTRACT_TESTED` after the transport-qualified
candidate from version 1.14 completed its atomic classification gate. Current source confines
runtime reflection to fully qualified `neqsim.*` classes, a small explicit common-class map, and
canonical `EquipmentFactory` aliases. `ApiKnowledgeRunnerTest` covers representative alias/class
resolution, member filtering and source pointers, and rejects `java.lang.Runtime`; the MCP server
facade preserves normal access enforcement and the standard response envelope.

`test_inspect_api_protocol.py` starts the packaged STDIO server and calls `inspectApi` through
`tools/call`, requiring `ProcessModel` to resolve to the exact runtime class with a filtered public
`run` method and requiring `java.lang.Runtime` to fail closed. It also calls `getCapabilities` and
now reconciles inventory 1.24 with 20/22/29 coverage accounting while retaining
`inspectApi=CONTRACT_TESTED`. The primary `test_mcp_server.py` independently includes `inspectApi`
among its twenty-two bounded software contracts and requires 29 confirmed gaps. The read-only
`MCP protocol qualification` workflow builds the exact NeqSim/MCP artifacts and executes the
focused scenarios on pull requests and `master`.

This is discovery-contract evidence only. The inspected method is not executed, arbitrary JVM
reflection remains outside the accepted boundary, and no thermodynamic/process accuracy or
engineering applicability claim is added. The detailed evidence and safety boundary are recorded
in `docs/evidence/API_INSPECTION_CONTRACT.md`.

### Promoted validation-profile contract

Inventory version 1.17 promoted `manageValidationProfile` to `CONTRACT_TESTED` after the
promotion-ready candidate from version 1.16 completed its atomic classification gate. Merged PR
#3266 established the bounded Java and real packaged-MCP evidence: built-in profile discovery,
structural preservation of validation metadata, isolated custom-profile create/activate/read/delete
lifecycle with recovery to `generic`, equipment-standard retrieval, and fail-closed mutation
errors. The focused protocol harness verifies the promoted coverage state through
`getCapabilities`, while the primary `test_mcp_server.py` now includes
`manageValidationProfile` in its twenty-two bounded software contracts and requires 30 confirmed
gaps.

This is software/governance evidence only. The named standards and design factors are not asserted
to be current, complete, legally applicable, licensed for redistribution, or correct for a real
facility; `validateWithProfile` scientific correctness, persistence, multi-tenant isolation,
external authorization, and plant authority remain outside the evidence boundary. Its historical
promotion moved coverage from 20/10/41 to 20/11/40. Current inventory 1.24 retains that contract
alongside the later automation, model-registry, and session-lifecycle promotions below. See
`docs/evidence/VALIDATION_PROFILE_CONTRACT.md`.

### Promoted automation advisory contracts

Inventory version 1.18 atomically promotes `listSimulationUnits`, `listUnitVariables`,
`getSimulationVariable`, `diagnoseAutomation`, and `getAutomationLearningReport` from
`CONFIRMED_GAP` to `CONTRACT_TESTED`, moving overall Phase 0 accounting from 20/11/40 to 20/16/35.
Merged #3302 established direct Java and packaged-MCP evidence for the three discovery/read tools;
merged #3309 established the same bounded evidence for diagnostic and learning-report retrieval.
That promotion moved the five machine-readable coverage records, the focused
`test_automation_read_protocol.py` assertions, and the authoritative `test_mcp_server.py`
accounting together on one exact head.

All five routes resolve the supplied definition or model handle through the canonical solved
NeqSim `ProcessSystem` and use `ProcessAutomation`/`AutomationDiagnostics`; no MCP-only simulator
or second process representation is added. The contract covers unit discovery, variable registry
metadata, addressed variable-read routing and requested-unit handling, standard envelope/provenance/
validation/quality-gate preservation, structured advisory diagnostics, fresh-process learning-report
shape, fail-closed inputs, and packaged transport.

For `getSimulationVariable`, the returned numerical value, model fidelity, convergence adequacy,
and engineering applicability are **not benchmark-validated by this classification**. Diagnostic
suggestions do not establish causality and are not plant measurements, control instructions, or
accountable engineering approval. Learning persistence across processes/restarts/tenants and
learning quality remain outside the evidence boundary. See
`docs/evidence/AUTOMATION_READ_CONTRACT.md`.

### Promoted reusable model-registry contract

Inventory version 1.19 atomically promotes `manageModel` after merged #3325 established direct Java
and packaged-MCP evidence for the reusable model-registry lifecycle. The contract remains the core
`ModelRegistry` foundation from #2875: registered handles resolve back into canonical NeqSim
`ProcessSystem`/`ProcessModel` execution, with no MCP-only simulator or second flowsheet
representation. Machine-readable coverage, focused Java/protocol expectations, and the authoritative
`test_mcp_server.py` accounting move together from 20/16/35 to 20/17/34.

The qualified boundary covers registration and idempotency, get/list/inspect, canonical process and
automation routing, revisioning, fail-closed malformed/unknown requests, deletion/invalidation,
source-level principal/tenant isolation, and packaged transport. It does **not** establish restart
persistence, distributed coherence, external identity correctness, numerical accuracy, convergence,
component or energy closure, facility fidelity, plant authority, control-system permission, design
certification, or accountable engineering approval. See
`docs/evidence/MODEL_REGISTRY_CONTRACT.md`.

### Promoted stateful session-lifecycle contract

Inventory version 1.20 atomically promotes `manageSession` after merged #3335 established direct
Java and packaged-MCP evidence for the existing canonical-process lifecycle. `SessionRunner`
continues to hold the normal NeqSim `ProcessSystem`; no MCP-only simulator or second process
representation is introduced. Machine-readable coverage, focused Java assertions,
`test_session_protocol.py`, all synchronized focused protocol expectations, and the authoritative
`test_mcp_server.py` accounting move together from 20/17/34 to 20/18/33.

The qualified boundary covers canonical process-backed creation, caller-visible list/state
identity, authenticated owner isolation, fail-closed unknown actions, close/invalidation, existing
evaluate/read/write routes, the current 50-session and 30-minute inactivity bounds, and packaged
transport. It does **not** establish restart durability, distributed coherence, external identity
correctness, numerical accuracy, convergence, component or energy closure, facility fidelity,
causal troubleshooting, plant authority, control-system permission, design certification, or
accountable engineering approval. See `docs/evidence/SESSION_LIFECYCLE_CONTRACT.md`.

### Promoted persisted-state lifecycle contract

Inventory version 1.21 atomically promotes `manageState` after merged #3348 established direct
Java and real packaged-MCP evidence for the existing local persisted-state lifecycle. The saved
payload retains the canonical process definition owned by `SessionRunner`; loading reconstructs a
normal canonical NeqSim session rather than introducing an MCP-only simulator. Machine-readable
coverage, focused Java assertions, `test_state_persistence_protocol.py`, synchronized protocol
expectations, and the authoritative `test_mcp_server.py` accounting move together from 20/18/33
to 20/19/32.

The qualified boundary covers save without silent overwrite, list/info, definition comparison,
load, JSON/YAML export, delete, caller-scoped isolated storage, traversal/external-path rejection,
and packaged transport. It does **not** establish numerical replay equality, thermodynamic
accuracy, convergence, component or energy closure, distributed durability or coherence,
transactions, encryption, signing, facility fidelity, causal troubleshooting, plant authority,
control-system permission, design certification, or accountable engineering approval. See
`docs/evidence/STATE_PERSISTENCE_CONTRACT.md`.

### Promoted adjustable-parameter discovery contract

Inventory version 1.22 atomically promotes `getAdjustableParameters` after merged #3365
established direct Java and real packaged-MCP evidence for canonical adjustable-parameter metadata
discovery. `AutomationRunner` resolves an explicit definition or reusable model handle to the same
solved NeqSim `ProcessSystem` and delegates discovery to `ProcessAutomation`; no MCP-only simulator
or second parameter registry is introduced. Machine-readable coverage, focused Java assertions,
`test_adjustable_parameters_protocol.py`, synchronized protocol expectations, and authoritative
`test_mcp_server.py` accounting move together from 20/19/32 to 20/20/31.

The qualified boundary covers deterministic parameter names and addresses, engineering units,
optional bounds, target metadata, direct-definition/model-handle equivalence, fail-closed input,
and packaged transport. It does **not** establish physical feasibility of declared bounds,
thermodynamic or process-model fidelity, convergence, mass or energy conservation, optimization
quality or global optimality, facility fidelity, plant authority, control-system permission,
design certification, or accountable engineering approval. See
`docs/evidence/ADJUSTABLE_PARAMETERS_CONTRACT.md`.

### Promoted pre-flight input-validation contract

Inventory version 1.23 atomically promotes `validateInput` after merged #3391 established direct
Java and real packaged-MCP evidence for the existing pre-flight validator. `NeqSimTools` resolves
reusable handles through `ModelRegistry` and delegates to `Validator`; no simulation is executed
and no MCP-only model is introduced. Machine-readable coverage, focused Java assertions,
`test_validate_input_protocol.py`, synchronized protocol expectations, and authoritative
`test_mcp_server.py` accounting move together from 20/20/31 to 20/21/30.

The qualified boundary covers deterministic syntax, structure, explicit unit and range checks,
component/equipment recognition, model-handle equivalence, issue severity/remediation, fail-closed
inputs, and packaged transport. It does **not** establish completeness, physical fidelity,
convergence, conservation, performance, facility suitability, plant authority, certification, or
engineering approval. See `docs/evidence/VALIDATE_INPUT_CONTRACT.md`.

### Promoted result-validation contract

Inventory version 1.24 atomically promotes `validateResults` after merged #3406 established direct Java and real
packaged-MCP evidence for the existing advisory `EngineeringValidator`, and merged #3416/#3421 preserved complete
inventory transport under the response-size guard. Machine-readable coverage, Java assertions,
`test_validate_results_protocol.py`, synchronized focused protocol expectations, and authoritative
`test_mcp_server.py` accounting move together from 20/21/30 to 20/22/29.

This software-contract classification covers deterministic engineering-rule findings, stable severity/remediation,
fail-closed malformed inputs, and packaged transport. It does not execute or independently verify a model, recompute
component, energy, or facility-wide conservation, establish numerical fidelity or convergence, grant plant authority,
or replace accountable engineering approval. The detailed evidence is in
`docs/evidence/VALIDATE_RESULTS_CONTRACT.md`.

No promotion candidate remains queued in inventory 1.24. A future transition must again move the
machine-readable coverage record and primary packaged-protocol accounting atomically from direct
evidence.

Per-result provenance, convergence, warnings, assumptions, units, and limitations remain
authoritative for an executed calculation. The evidence inventory is advisory discovery metadata;
it does not certify a facility, claim causality, replace qualified engineering review, or write to
a live plant system.

## Guided prompts

- `biorefinery_analysis`
- `co2_ccs_chain`
- `design_gas_processing`
- `dynamic_simulation`
- `field_development_screening`
- `flow_assurance_screening`
- `pipeline_sizing`
- `pvt_study`
- `teg_dehydration_design`

## Governance and compatibility

The published tools are classified by `IndustrialProfile` as trusted core, engineering advanced,
or experimental, and as advisory, calculation, execution, or platform operations. The supported
deployment profiles are `DESKTOP_ENGINEER`, `STUDY_TEAM`, `DIGITAL_TWIN`, and `ENTERPRISE`.
Publication does not mean that every tool is enabled in every profile or has the same engineering
validation maturity. Call `getCapabilities`, `getBenchmarkTrust`, and `checkToolAccess` before
execution and preserve each calculation's provenance, convergence, validation, assumptions, and
limitations.

This increment changes no tool name, tool input, deployment default, thermodynamic model, process
model, or response envelope. It updates additive discovery metadata inside the existing
`getCapabilities.phase0EvidenceInventory` object. Process execution continues to use canonical
NeqSim fluids, streams, `ProcessSystem`, and `ProcessModel` objects.

## Phase 0 stop boundary

The baseline now freezes the exact transport-facing tools, resources, resource templates, prompts,
deployment-profile names, tool-capability reconciliation, schema resource graph, example resource
graph, tool implementation bindings, factory-backed equipment, report paths, test sources, guides,
merged-foundation reconciliation, four public synthetic acceptance scales, bounded acceptance
baseline harness, campaign traceability/maturity matrix, and explicit trust-coverage status for
every published tool. Twenty-one bounded discovery, catalog, lookup, progress, trust-retrieval,
governance, validation-profile, API-inspection, model-registry, session-lifecycle, persisted-state,
adjustable-parameter discovery, pre-flight validation, and automation advisory contracts are
contract-tested, leaving 30 confirmed trust gaps and no queued promotion candidate.

Follow-up work should continue auditing remaining confirmed gaps and promote only when concrete
source/test/public-benchmark evidence or a clearly bounded software contract supports a precise
classification. Explicit numeric component, energy, and facility-wide conservation/report gaps
remain separate work and must not be reconstructed as a second MCP-side physics model. Later-phase
campaign criteria remain incomplete until their own merged acceptance evidence exists.

DEXPI/P&ID ingestion remains owned by #2899, dynamics by #2911, flash/stability/performance by
#2937, and merged production-optimization foundations by #2941. This inventory audits existing
publication only and does not implement competing domain functionality.
