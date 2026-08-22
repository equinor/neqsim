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

This increment changes no tool name, input or output schema, deployment default, thermodynamic
model, process model, or response envelope. Process execution continues to use canonical NeqSim
fluids, streams, `ProcessSystem`, and `ProcessModel` objects.

## Phase 0 stop boundary

The baseline now freezes the exact transport-facing tools, resources, resource templates, prompts,
deployment-profile names, tool-capability reconciliation, schema resource graph, and example
resource graph. It does not complete the campaign's full Phase 0 inventory. Follow-up work must
still inventory runners, equipment factories, report paths, tests, guides, and known limitations;
reconcile merged foundations #2874, #2875, and #3152 criterion by criterion; add the four
acceptance scales; build the full traceability and discipline-maturity matrices; and record runtime,
memory, payload, convergence, balance-closure, and report-usefulness baselines.

DEXPI/P&ID ingestion remains owned by #2899, dynamics by #2911, flash/stability/performance by
#2937, and merged production-optimization foundations by #2941. This inventory audits existing
publication only and does not implement competing domain functionality.
