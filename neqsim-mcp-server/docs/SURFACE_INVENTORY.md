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
| Factory equipment | 205 types | `getCapabilities.implementationInventory` | `EquipmentFactory` |
| Engineering report paths | 2 | `getCapabilities.implementationInventory` | `ReportRunner`, `TaskWorkflowBridge` |
| MCP Java test classes | 67 | `getCapabilities.phase0EvidenceInventory` | `src/test/java/neqsim/mcp/**/*Test.java` |
| MCP protocol scenarios | 94 | `getCapabilities.phase0EvidenceInventory` | `test_mcp_server.py` |
| MCP guides | 8 | `getCapabilities.phase0EvidenceInventory` | Core guides, foundation traceability, fixtures, baseline harness, and campaign matrix |
| Explicit benchmark-trust pages | 20 of 71 tools | `getBenchmarkTrust` and `getCapabilities.phase0EvidenceInventory` | `BenchmarkTrust` |
| Trust coverage records | 71 = 20 explicit benchmark + 1 contract-tested discovery + 50 confirmed gaps | `getCapabilities.phase0EvidenceInventory` | `BenchmarkTrust`, `McpImplementationInventory`, MCP contract tests |

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
`neqsim.process.processmodel.JsonProcessBuilder`. The inventory exposes the same 205 name-only
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
Phase 0 inventory. The exact current source contains 67 JUnit test classes under
`src/test/java/neqsim/mcp` and 94 named scenarios in the packaged real-STDIO JSON-RPC harness
`neqsim-mcp-server/test_mcp_server.py`. The protocol regression independently recounts both source
trees and fails if the manifest drifts. These counts identify evidence locations; they are not a
claim that a test ran or passed. Exact-head CI and recorded command output remain the execution
evidence.

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
exceeds 256 KiB. It retains `phase0EvidenceInventory` because that evidence contract has no
equivalent selective-retrieval route. Omitted catalog detail remains identified in `truncation` and
can be queried through `getSchema`, `getExample`, `getBenchmarkTrust`, and the MCP catalog
resources.

`BenchmarkTrust` currently contains 20 tool-specific numerical/engineering trust pages with 64
known-limit entries and 30 validation cases, of which 5 name a concrete `verifiedBy` test. The
other 51 published tools still use the compatibility-level generic `TESTED` benchmark fallback
from `getBenchmarkTrust`.

Phase 0 does not equate that generic benchmark fallback with an unresolved scientific gap when the
tool is intrinsically non-numerical. `knownLimitations.coverageRecords` contains one deterministic
record for every published tool and now uses three bounded states:

- `EXPLICIT_TRUST` — a tool-specific `BenchmarkTrust` page exists, with its declared maturity and
  counts for validation cases, verified cases, limitations, and unsupported conditions;
- `CONTRACT_TESTED` — the tool is non-numerical and direct source, contract-test, response-guard,
  and real-protocol evidence demonstrates its MCP contract. `getCapabilities` is the first such
  record. Its engineering benchmark applicability is explicitly
  `NOT_APPLICABLE_NON_NUMERICAL_DISCOVERY`; this status does not validate any advertised
  thermodynamic or process calculation; or
- `CONFIRMED_GAP` — no tool-specific benchmark trust page or bounded non-numerical contract
  evidence closes the gap. The record names the implementation class, retains the compatibility
  maturity, and explicitly states that generic `TESTED` is not benchmark, accuracy,
  applicability, or no-limitations evidence.

Accordingly, `coverageComplete=true` means all 71 published tools have an explicit trust-coverage
classification. It does **not** mean the MCP surface is scientifically validated: 50 records remain
`CONFIRMED_GAP`, one is `CONTRACT_TESTED`, `scientificValidationComplete=false`, and the overall
Phase 0 `complete` flag remains false. The benchmark registry itself remains unchanged at 20
explicit pages and 51 generic benchmark fallbacks, so existing benchmark-report accounting and
protocol contracts are preserved.

Per-result provenance, convergence, warnings, assumptions, and limitations remain authoritative
for an executed calculation. The evidence inventory is advisory discovery metadata; it does not
certify a facility, claim causality, replace qualified engineering review, or write to a live plant
system.

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
model, or response envelope. It extends additive discovery metadata inside the existing
`getCapabilities.phase0EvidenceInventory` object. Process execution continues to use canonical
NeqSim fluids, streams, `ProcessSystem`, and `ProcessModel` objects.

## Phase 0 stop boundary

The baseline now freezes the exact transport-facing tools, resources, resource templates, prompts,
deployment-profile names, tool-capability reconciliation, schema resource graph, example resource
graph, tool implementation bindings, factory-backed equipment, report paths, test sources, guides,
merged-foundation reconciliation, four public synthetic acceptance scales, bounded acceptance
baseline harness, campaign traceability/maturity matrix, and explicit trust-coverage status for
every published tool. `getCapabilities` is now contract-tested without being misrepresented as a
scientifically benchmarked calculation.

Follow-up work should close the remaining 50 confirmed trust gaps only where current source and
public evidence support a specific claim, and continue closing explicit numeric component,
energy, and facility-wide conservation/report gaps only where canonical NeqSim APIs provide
independent evidence. Later-phase campaign criteria remain incomplete until their own merged
acceptance evidence exists.

DEXPI/P&ID ingestion remains owned by #2899, dynamics by #2911, flash/stability/performance by
#2937, and merged production-optimization foundations by #2941. This inventory audits existing
publication only and does not implement competing domain functionality.
