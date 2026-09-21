# Canonical utility-design screening contract

Inventory 1.44 records `designUtilities=CONTRACT_TESTED` from direct native
component tests and real packaged-MCP evidence. This is a software-routing and
transport contract for early-stage screening, not a detailed design, accuracy,
standards-conformance, safety, or plant-operating claim.

## Qualified behavior

- The caller selects `boiler`, `deaerator`, `refrigeration`, `nitrogen`,
  or `steamNetwork`.
- `UtilityDesignRunner` deterministically dispatches to the existing NeqSim
  `Boiler`, `Deaerator`, `RefrigerationCycle`, `NitrogenSystem`, or
  `SteamNetwork`; no MCP-only calculation model is introduced.
- Direct utility-component tests exercise the canonical model implementations.
- The focused packaged STDIO harness executes all five routes and verifies
  fail-closed blank, malformed, and unsupported-type inputs.
- Normal MCP access enforcement, standard result evidence, and JSON-RPC/STDIO
  transport remain in force.

Evidence sources are `UtilityDesignRunner`, `UtilityComponentsTest`, the
`NeqSimTools` facade, `test_utility_design_protocol.py`, the comprehensive
protocol suite, and this document.

## Explicit exclusions

This classification does not establish:

- design-basis completeness or input suitability;
- property-data, correlation, duty, flow, power, emissions, or cost accuracy;
- equipment sizing adequacy, availability, reliability, or network optimality;
- detailed mechanical design or safe operating limits;
- standards, regulatory, or project conformance;
- certification, plant/control authority, or permission to act; or
- qualified engineering judgment and accountable approval.

Per-result provenance, assumptions, warnings, units, limitations, validation,
and quality gates remain authoritative for an executed case.
