# Canonical chemistry dispatch and transport contract

Inventory 1.45 records `runChemistry=CONTRACT_TESTED` from direct runner tests
and real packaged-MCP evidence. This is a software-routing, failure-envelope,
and transport contract. It is not a model-accuracy, applicability, design,
safety, standards-conformance, or plant-operating claim.

## Qualified behavior

- `ChemistryRunner` deterministically dispatches the eight advertised runner
  analyses: `electrolyteScale`, `multiMineralScale`,
  `mechanisticCorrosion`, `langmuirInhibitor`,
  `packedBedScavenger`, `electrolyteScaleEquilibrium`,
  `electrolyteMultiScaleEquilibrium`, and `pitzerQualification`.
- The MCP facade delegates to the existing runner; no MCP-only chemistry,
  equilibrium, corrosion, adsorption, reaction, or transport model is added.
- Direct Java runner tests exercise success, determinism, qualification
  boundaries, canonical equilibrium operations, and rejected inputs.
- The focused packaged STDIO harness executes all eight routes and verifies
  fail-closed blank, malformed, and unknown-analysis inputs.
- Normal MCP access enforcement, standard result evidence, and JSON-RPC/STDIO
  transport remain in force.

Evidence sources are `ChemistryRunner`, `ChemistryRunnerTest`,
`ChemistryRunnerScaleTest`, the `NeqSimTools` facade,
`test_chemistry_protocol.py`, the comprehensive protocol suite, and this
document.

## Explicit exclusions

This classification does not establish:

- composition, state, model, parameter, dataset, or design-basis suitability;
- thermodynamic, electrolyte, precipitation, scale, corrosion, adsorption,
  reaction, transport, or kinetic accuracy;
- convergence, numerical stability, or uncertainty bounds for arbitrary inputs;
- equipment design, chemical dose, safe operating limits, or fitness for use;
- standards, regulatory, project, or evidence-envelope compliance;
- certification, plant/control authority, or permission to act; or
- qualified engineering judgment and accountable approval.

Per-result provenance, assumptions, warnings, units, limitations, validation,
qualification decisions, and quality gates remain authoritative for an
executed case.
