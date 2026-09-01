# Adjustable-parameter discovery contract evidence

## Capability and engineering question

`getAdjustableParameters` answers which process inputs the existing NeqSim
automation model exposes for bounded study setup, using stable addresses and
explicit units. The authoritative route is
`NeqSimTools.getAdjustableParameters` →
`AutomationRunner.getAdjustableParameters` → solved canonical `ProcessSystem`
→ `ProcessAutomation.getAdjustableParametersJson()`.

The capability accepts either an explicit process JSON definition or a reusable
`ModelRegistry` handle. It returns `schemaVersion`, `count`, and parameter
records containing `name`, `address`, `unit`, `targetUnitName`,
`targetProperty`, and `source`. Optional `lowerBound` and `upperBound` values are
numeric when declared and may be omitted when the registry has no bound. The `unit`
field is an engineering-unit string; the existing registry uses an empty string for a
qualified dimensionless property such as `polytropicEfficiency`.

## Qualification evidence

`AutomationLoopRunnerTest` verifies the Java contract on a canonical compressor,
including the stable `Compressor.outletPressure` address, `bara` unit, target
metadata, provenance, declared bounds, and internally consistent count.

`test_adjustable_parameters_protocol.py` starts the packaged MCP server over
STDIO and retrieves the canonical `process/compression-with-cooling` catalog
fixture. It verifies:

- non-empty parameter records with explicit unit metadata, including the
  dimensionless empty-string convention;
- stable compressor pressure addresses;
- deterministic equivalence between an explicit definition and a reusable
  `manageModel` handle;
- fail-closed behavior for a blank request; and
- the promoted Phase 0 trust classification and its bounded evidence sources.

The focused Java and packaged-MCP checks run in
`.github/workflows/mcp_protocol_qualification.yml` before the comprehensive MCP
regression.

## Scope and limitations

This contract is discovery-only and does not mutate a process, start an
optimization, or issue a plant command. Bounds and units are those already
declared by the canonical Java automation registry. Their presence does not
establish physical feasibility, model fidelity, convergence quality, mass or
energy conservation, a globally optimal operating point, design certification,
or accountable engineering approval.

Phase 0 inventory version `1.22` atomically promotes
`getAdjustableParameters` to `CONTRACT_TESTED`, moving the accounting from
20/19/32 to 20/20/31. This is a bounded software-contract classification, not
an engineering calculation qualification. The machine-readable inventory,
focused packaged-MCP assertion, primary protocol accounting, Java regression,
and documentation move together from the merged #3365 evidence.
