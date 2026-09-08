# Visualization contract qualification

This evidence note qualifies the existing `generateVisualization` software contract.
It resolves the public type-dispatch mismatch without changing NeqSim's canonical
thermodynamic or process models.

## Qualified behavior

The runner accepts these canonical types:

- `phaseEnvelope`, `flowsheet`, `compressorMap`, `propertyTable`,
  `barChart`, `pieChart`, and `lineChart`;
- `flowsheetDiagram` is a compatibility alias for `flowsheet`;
- `styledTable` and `table` are compatibility aliases for `propertyTable`.

Successful results retain canonical `visualizationType` values and stable media
fields: `svg` with `image/svg+xml`, `mermaid` with `text/x-mermaid`, or
`html` with `text/html`. The standard MCP envelope identifies
`generateVisualization`, validation succeeds, and the software quality gate
passes.

Focused Java evidence covers canonical type selection, documented aliases,
media fields, XML and HTML escaping, null and malformed JSON, missing and
unknown types, empty chart inputs, and unequal chart-array lengths. The
packaged-MCP harness repeats the public aliases, escaping, stable envelope and
media contracts, fail-closed inputs, and transport through the real server.

## Inputs, outputs, and units

`barChart` consumes same-length `labels` and numeric `values`.
`pieChart` consumes same-length `categories` and numeric `values`.
`lineChart` consumes same-length numeric `xValues` and `yValues`.
`propertyTable` consumes `headers`, `rows`, and optional `title` or
`caption`. `flowsheet` consumes `equipment` and optional `connections`.

Chart values are rendered exactly as supplied. Their physical meaning and
units are caller-defined; the visualization contract performs no unit
conversion, dimensional validation, or engineering interpretation.
`phaseEnvelope` uses component fractions, temperature in degrees Celsius,
and pressure in bar in its rendered data, but its numerical behavior is
outside this qualification.

## Evidence and provenance

Direct evidence:

- `src/test/java/neqsim/mcp/runners/VisualizationRunnerTest.java`;
- `neqsim-mcp-server/test_visualization_protocol.py`;
- `.github/workflows/mcp_protocol_qualification.yml`;
- `neqsim-mcp-server/test_mcp_server.py` as the comprehensive regression.

The implementation remains `VisualizationRunner`, exposed through
`NeqSimTools.generateVisualization`. Merged qualification #3510 supplies the
direct Java and packaged-MCP evidence. Inventory `1.29 / 20 explicit + 29
contract-tested + 22 confirmed gaps` atomically recorded
`generateVisualization` as `CONTRACT_TESTED`. Current inventory `1.33 / 20
explicit + 33 contract-tested + 18 confirmed gaps` retains that classification
while preserving zero queued promotion candidates and
`scientificValidationComplete=false`.

## Explicit limitations

This software-contract evidence does not establish:

- browser or client rendering fidelity;
- SVG, Mermaid, or HTML sandbox security;
- accessibility, responsive layout, or visual-design quality;
- complete or correct process-flow topology;
- compressor-map correctness or operating-envelope validity;
- thermodynamic, phase-envelope, or other numerical accuracy;
- convergence, conservation, uncertainty, or model applicability;
- persistent artifact storage or multi-tenant isolation;
- plant or control authority, certification, or accountable engineering
  approval.

Generated markup and charts are advisory reporting artifacts. Engineers remain
responsible for source-data quality, units, model selection, interpretation,
and all safety-critical or operational decisions.
