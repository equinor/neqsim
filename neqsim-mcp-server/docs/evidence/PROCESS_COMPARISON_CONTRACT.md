# Bounded canonical process-comparison contract

## Engineering question

Can `compareProcesses` safely execute a bounded set of caller-defined process
cases through NeqSim's canonical process runner while making incomplete output
explicit?

## Qualified contract

- Request text is limited to 1 MiB UTF-8.
- The `cases` collection contains two to 32 objects.
- Every case supplies a `fluid` object and `process` array in canonical
  `runProcess` shape.
- Optional names are trimmed, unique, and limited to 256 characters; omitted
  names deterministically become `Case N`.
- Cases execute sequentially in request order through `ProcessRunner`.
- Results preserve canonical per-case responses and expose `caseCount`,
  `successfulCaseCount`, `failedCaseCount`, and `complete`.
- A failed case remains present with `converged=false`, its canonical error
  result, and a comparison-level error summary. Successful siblings remain.
- Discovery, normal access enforcement, standard response evidence, and
  packaged STDIO transport are covered.

## Evidence

`ProcessComparisonRunnerTest` verifies complete execution, partial failure,
malformed and oversized requests, collection limits, and name limits.
`test_process_comparison_protocol.py` exercises seven packaged-server
scenarios. `test_mcp_server.py` retains broad real-protocol coverage and
verifies the atomic inventory record. `SchemaCatalog`, `ExampleCatalog`, and
`NeqSimTools` expose synchronized discovery contracts.

Inventory `1.39 / 20 explicit + 39 contract-tested + 12 confirmed gaps`
records `compareProcesses=CONTRACT_TESTED`. No promotion candidate remains.

## Exclusions

This bounded software contract does not establish compatible case units, bases,
assumptions, fluids, equipment semantics, or boundary conditions. It does not
establish numerical or thermodynamic accuracy, convergence for arbitrary
inputs, mass or energy conservation, uncertainty, optimization quality,
facility fidelity, persistence, parallel execution, standards compliance,
plant or control authority, certification, or accountable engineering
approval. Per-case provenance, convergence, validation, warnings, quality gates,
and limitations remain authoritative.
