# Pressure-relief sizing screening contract evidence

## Scope

`runRelief` is a bounded engineering-screening interface over NeqSim's existing
`ReliefValveSizing` calculations. This qualification increment does not change
the canonical equations or the MCP inventory classification. The tool remains a
`CONFIRMED_GAP` until a separate atomic promotion updates the inventory.

Maximum request size is 16,384 UTF-8 bytes. Unknown cases, malformed JSON,
missing required inputs, non-finite values, non-positive physical quantities,
overpressure fractions outside `(0, 1]`, gas mass fractions outside `[0, 1]`,
and backpressure at or above calculated relieving pressure fail closed.

## Qualified routes and SI inputs

| Case | Required inputs | Qualified calculation |
| --- | --- | --- |
| `gas` | `massFlowRate_kg_s`, `setPressure_bara`, `temperature_K`, `molecularWeight_kg_mol` | API 520/API 521-oriented vapour sizing |
| `liquid` | `volumeFlowRate_m3_s`, `liquidDensity_kg_m3`, `setPressure_bara` | liquid relief sizing |
| `twoPhase` | mass flow, set pressure, temperature, gas mass fraction, gas/liquid densities, latent heat, liquid heat capacity | Leung omega-method screening |
| `fireHeatInput` | `wettedArea_m2` | API 521-oriented wetted-area heat-input screening |

Optional correction inputs retain their existing defaults. Successful responses
expose the case-specific result plus `screeningOnly=true`,
`standardConformanceClaimed=false`, and an explicit advisory boundary.

## Evidence

Focused Java contract tests cover all four valid routes, finite positive results,
selected-versus-required gas area, boundary metadata, malformed and oversized
requests, negative flow, invalid gas fraction, invalid backpressure, and unknown
cases. The packaged comprehensive MCP protocol harness invokes the real
`runRelief` tool, verifies the standard response envelope and boundary, checks a
finite conservative gas result, and confirms invalid input fails closed.

## Advisory boundary

The calculation does not establish relief-scenario completeness, applicable
standard edition, relieving-rate or property validity, allowable accumulation,
coefficient applicability, inlet/outlet piping acceptability, disposal-system
capacity, reaction loads, or installation suitability. It is not certification
or plant authorization. A qualified pressure-relief/process-safety review is
required before design or operational use.
