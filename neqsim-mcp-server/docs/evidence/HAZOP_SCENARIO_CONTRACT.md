# Simulation-backed HAZOP scenario software contract

## Qualified boundary

`runHazopScenario` answers one focused HAZOP deviation question against a caller-supplied
canonical NeqSim process definition. The existing runner builds and executes the `ProcessSystem`,
uses `HazopConsequenceAutoPopulator`, filters findings by node, guide word, and parameter, and
returns the computed value, caller-owned design limit, verdict, standard reference, and auditable
limit basis.

Inventory 1.47 promotes this tool from `CONFIRMED_GAP` to `CONTRACT_TESTED` from direct
evidence in:

- `HazopScenarioRunner` and `HazopConsequenceAutoPopulator`;
- `HazopScenarioRunnerTest`;
- the `NeqSimTools` MCP facade;
- `test_hazop_scenario_protocol.py` over the packaged STDIO server;
- the comprehensive `test_mcp_server.py` regression; and
- this contract record.

The focused protocol qualification checks tool discovery, the catalog-equivalent compressor
scenario, deterministic replay, explicit zero-match evidence, fail-closed empty input, and atomic
inventory accounting. The primary protocol suite independently exercises a quantified finding and
the standard response envelope.

## Engineering and safety boundary

This is software-contract qualification, not scientific or standards validation. It does not
establish hazard-identification or scenario completeness, P&ID/STID extraction fidelity,
process-model or thermodynamic accuracy, convergence for arbitrary facilities, uncertainty,
suitability of caller-provided limits, governing-standard applicability or conformance, safe
operating limits, safeguard adequacy, plant or control authority, design certification, or
accountable HAZOP/process-safety approval.

The tool remains advisory. Qualified HAZOP, process-safety, process, operations, and accountable
engineering review remain mandatory before any facility decision or action.
