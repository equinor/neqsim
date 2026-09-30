# Safety-system performance software contract

## Qualified boundary

`runSafetySystemPerformance` parses the existing safety-system performance request, delegates to
the canonical `SafetySystemPerformanceRunner` and `SafetySystemPerformanceAnalyzer`, and returns
the report, summary, standards templates, and STID-extraction templates through the standard MCP
response envelope.

Inventory 1.48 promotes this tool from `CONFIRMED_GAP` to `CONTRACT_TESTED` from direct evidence
in the runner and analyzer sources, their Java tests, the MCP facade, the comprehensive packaged-MCP
regression, and the focused packaged-MCP qualification. The focused qualification covers discovery,
the catalog example, deterministic replay, fail-closed empty input, and atomic inventory accounting.

## Evidence claim

The qualified software contract covers:

- deterministic catalog-example execution;
- report and assessment-summary presence;
- NORSOK S-001, ISO 13702, TR1055-style, and STID-extraction template presence;
- stable invalid-input failure behavior;
- normal access enforcement and the standard MCP response envelope; and
- inventory 1.48 accounting at 20 explicit, 48 contract-tested, and 3 confirmed-gap tools.

## Exclusions

This evidence does not establish source-document or tag extraction fidelity, hazard or demand
completeness, barrier or safeguard adequacy, SIL/PFD validity, independence, common-cause,
proof-test or lifecycle evidence, process-model or facility fidelity, standards applicability or
conformance, safe operating limits, plant or control authority, certification, or accountable
functional-safety and process-safety approval.

Independent maintainer and qualified process/functional-safety review remain mandatory.
