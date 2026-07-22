---
name: design neqsim utility systems
description: "Coordinates screening-level utility-system DESIGN with NeqSim — sizes fired steam boilers, deaerators, vapour-compression refrigeration, on-site nitrogen generation, and multi-pressure steam networks, aggregates plant utility demands from a run flowsheet, and runs the agentic closed loop that optimizes a two-stage compression utility. Backed by the neqsim.process.util.utilitydesign classes and the MCP designUtilities tool. Pairs with @optimize (general flowsheet optimization) and is distinct from utility-LEVEL selection (neqsim-utilities-specification)."
argument-hint: "Describe the utility task — e.g., 'size a fired boiler for an 8 MW reboiler duty', 'design a propane chiller for 3 MW of gas chilling at -35 C', 'membrane N2 generator for 500 Nm3/h at 99.5%', 'HP/LP steam header balance', or 'optimize the interstage pressure of a two-stage instrument-air compressor'."
---

Loaded skills: neqsim-utility-design, neqsim-utilities-specification, neqsim-heat-integration, neqsim-api-patterns, neqsim-java8-rules, neqsim-professional-reporting

## Skills to Load

ALWAYS read these skills before proceeding:

- `.github/skills/neqsim-utility-design/SKILL.md` — the five utility classes, the MCP `designUtilities` tool, and the agentic `UtilityCompressionOptimizer`
- `.github/skills/neqsim-utilities-specification/SKILL.md` — choosing utility LEVELS (HP/MP/LP steam, CW vs chilled water) and standards
- `.github/skills/neqsim-heat-integration/SKILL.md` — the grand composite curve that fixes the utility duties
- `.github/skills/neqsim-api-patterns/SKILL.md` — fluid creation, flash, equipment
- `.github/skills/neqsim-java8-rules/SKILL.md` — Java 8 compatibility
- `.github/skills/neqsim-professional-reporting/SKILL.md` — when delivering a task report

## Operating Principles

1. **Levels first, then sizing.** Decide the utility level with `neqsim-utilities-specification`; size the duty/flow/power with `neqsim-utility-design`.
2. **Duties drive everything.** Get heating/cooling duties from the flowsheet (or the grand composite curve), not from guesses.
3. **Screening, not detail.** These models are deterministic early-design sizers — state that explicitly; do not present them as detailed mechanical design.
4. **Use the right class.** Map the request to exactly one of: `Boiler`, `Deaerator`, `RefrigerationCycle`, `NitrogenSystem`, `SteamNetwork`, or the `UtilitySystemDesigner` aggregator.
5. **kW everywhere.** All duties are kW; negative duties are rejected.
6. **Report CO₂ and OPEX.** Every utility class emits emissions and operating cost — include them for ESG/screening.
7. **Java 8 only.** No `var`, `List.of`, text blocks, etc.

## Utility → Class Quick Reference

| Request | Class | Key inputs |
|---|---|---|
| Fired steam / steam generation | `Boiler` | steam duties (kW), efficiency, fuel LHV |
| Feedwater deaeration | `Deaerator` | feedwater flow, inlet T, pressure |
| Chilling / refrigeration | `RefrigerationCycle` | duties (kW), evaporator/condenser T |
| On-site nitrogen | `NitrogenSystem` | demand (Nm³/h), purity, method (MEMBRANE/PSA/CRYOGENIC) |
| Steam header balance | `SteamNetwork` | levels, demands, local generation |
| Plant-wide utility roll-up | `UtilitySystemDesigner` | a run `ProcessSystem` / `ProcessModel` |
| Optimize compression utility | `UtilityCompressionOptimizer` | inlet/delivery pressure, flow |

## Workflow

1. **Classify** the utility (steam / deaerator / refrigeration / nitrogen / steam-network / aggregate / compression-optimize).
2. **Resolve duties** from the flowsheet or grand composite curve; convert to kW.
3. **Pick the level** (steam pressure, CW vs chilled water) using `neqsim-utilities-specification`.
4. **Build and `calculate()`** the chosen utility class (or run `UtilitySystemDesigner.fromProcessSystem(p).design()`).
5. **For compression utilities**, run `UtilityCompressionOptimizer.optimize()` to minimize total shaft power.
6. **Read results** — flows, power, CO₂, OPEX via getters / `toResultsMap()` / `toJson()`.
7. **Report** — write to `results.json` under `key_results`; state the screening-level assumption and any gaps.

## Calling via MCP

When the task is exposed through the MCP server, call the `designUtilities` tool with a `utilityType` of `boiler`, `deaerator`, `refrigeration`, `nitrogen`, or `steamNetwork` and the JSON contract documented in the `neqsim-utility-design` skill. The runner returns the class `toJson()` wrapped in the standard response envelope.

## Output Structure for results.json

```json
{
  "key_results": {
    "utility": "refrigeration",
    "duty_kW": 3000.0,
    "compressor_power_kW": 1180.0,
    "cop": 2.54,
    "co2_tonne_per_year": 5400.0,
    "opex_per_year": 1.1e6
  }
}
```

## Honest Gaps

- Screening-level only: no detailed combustion, control, transient, or mechanical design.
- Refrigeration COP is a Carnot fraction, not a cycle simulation.
- Nitrogen specific energy is a method-based estimate; override it when a vendor figure exists.
- Do NOT claim TEMA/ASME mechanical sizing here — escalate to `@mechanical.design`.
