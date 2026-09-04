# Reporting and task-workflow handoff qualification

## Engineering question and maturity

Can the paired MCP reporting surfaces preserve structured, machine-readable simulation handoff data, generate transient
Markdown and plotting inputs, and reject invalid requests without claiming that they produced an approved engineering
deliverable?

This increment qualifies the existing `generateReport` and `bridgeTaskWorkflow` software contracts. It deliberately
leaves Phase 0 inventory at **1.24 / 20 explicit + 22 contract-tested + 29 confirmed gaps**. Promotion is a separate,
atomic increment after this evidence has merged and passed the complete exact-head gate set.

## Authoritative behavior

`NeqSimTools.generateReport` delegates to `ReportRunner.run(reportJson)` and applies the standard MCP response envelope
and response-size guard. A successful response preserves the requested title, author and report type, and returns:

- transient Markdown containing input, result and conclusion sections;
- a structured summary table of recursively discovered numeric fields, capped at 100 rows;
- optional chart-ready records for numeric arrays, capped at 20 records;
- optional advisory `EngineeringValidator` output; and
- shallow top-level numeric, object, array and total-field counts.

`NeqSimTools.bridgeTaskWorkflow` delegates to `TaskWorkflowBridge.run(bridgeJson)` and applies the same transport guard.
`getSchema` exposes the task-solve `results.json` fields. `toResultsJson` maps supported source-runner fields into
`key_results`, carries supplied or status-derived validation, preserves approach and conclusions, creates the standard
empty reporting placeholders, and records MCP source metadata. Unsupported source runners receive only a generic status
handoff. Neither route invokes a simulation or persists a file.

Malformed/non-object report JSON returns `status: error` with `REPORT_ERROR`. The bridge returns `status: error` for
malformed/non-object input, unknown actions, or a missing/non-object `toolOutput`. These errors remain structured through
the packaged STDIO MCP transport.

## Units, assumptions and determinism

The report table infers display units from field names only. Temperature-like fields are labelled `C`, pressure-like
fields `bara`, power-like fields `kW`, mass-flow-like fields `kg/hr`, density-like fields `kg/m3`, viscosity-like fields
`cP`, efficiency and `_pct` fields `%`, velocity-like fields `m/s`, and length/diameter-like fields `m`. This is a
presentation heuristic, not unit conversion or dimensional validation. Callers remain responsible for normalized values
and authoritative unit metadata.

The `runFlash` bridge converts `temperature_K` to `temperature_C` and renames selected existing fields; other supported
runner mappings copy selected values under their documented labels. The bridge does not independently recompute or
validate them.

Array order, table traversal, schema content and bridge output are deterministic for a fixed request. `generatedAt` and
the Markdown date use the host clock and are intentionally nondeterministic metadata; qualification checks their shape,
not an exact value. JSON object insertion order controls table row order.

## Acceptance evidence

- `ReportRunnerTest` covers structured report metadata, inferred-unit table rows, chart extraction, request flags,
  summary counts, report failures, bridge extraction, schema shape, provenance and bridge failures.
- `test_reporting_protocol.py` repeats those boundaries through the real packaged STDIO server and proves inventory 1.24
  remains qualification-only for both tools.
- `test_mcp_server.py` retains the broad real-protocol calls and the 71-tool registration/accounting checks.
- `mcp_protocol_qualification.yml` runs the focused Java and packaged-MCP reporting contracts before the comprehensive
  protocol regression.

Java and JSON/MCP views are applicable. A separate Python calculation, notebook, rendered document and whole-sheet
visual gate are not applicable because this increment qualifies a Java-backed transient transport/software contract.

## Explicit limitations and stop boundary

This evidence does **not** establish simulation execution, thermodynamic or equipment-model fidelity, convergence,
component or facility-wide conservation, numerical robustness, uncertainty coverage, standards currency, causal
diagnosis, safe operating limits, report completeness, or independent benchmark agreement. It does not create, save,
render or approve Word, HTML, PDF or other external artifacts. It grants no plant, control, design, certification,
regulatory or accountable engineering authority.

The underlying simulation result, canonical model, units, assumptions, provenance, convergence evidence, limitations and
independent benchmarks remain authoritative. No production runner, tool schema, inventory classification, numerical
model, deployment profile, companion repository, persisted state or live-system integration changes in this increment.
