---
title: "AI-HAZOP Input Data Format"
description: "Required input-data format for connecting a browser P&ID Safety Analyser / AI-HAZOP tool to NeqSim for quantified consequence simulation of HAZOP deviations, DEXPI design-conditions export, blocked-outlet overpressure screening, and per-deviation MCP scenario quantification."
---

# AI-HAZOP Input Data Format

This page documents the data a P&ID Safety Analyser / AI-HAZOP front-end (for
example a browser tool, v3.2+) must provide so NeqSim can **quantify** each HAZOP
deviation rather than only enumerate it. It covers the four NeqSim capabilities
that close the loop between a qualitative HAZOP grid and a simulation-backed
verdict:

| Capability | NeqSim entry point |
|------------|--------------------|
| Per-deviation scenario quantification (MCP) | `runHazopScenario` → `neqsim.mcp.runners.HazopScenarioRunner` |
| DEXPI design-conditions export | `neqsim.process.mechanicaldesign.DesignConditions` + `DexpiXmlWriter` |
| Blocked-outlet overpressure screening | `neqsim.process.safety.depressurization.BlockedOutletOverpressureAnalyzer` |
| Limit-basis provenance in findings | `neqsim.process.safety.hazid.HazopConsequenceFinding#getLimitBasis()` |

## 1. Process model (mandatory)

Every quantified scenario starts from a **run** NeqSim `ProcessSystem`. The
front-end supplies it as the JSON consumed by `ProcessSystem.fromJsonAndRun`.
Minimum content:

- **Fluid definition** — components and mole fractions (a PVT/composition table),
  the equation of state (SRK is the default screening EOS), and the mixing rule.
- **Streams** — feed streams with flow rate, temperature and pressure (with
  units), wired to equipment by name.
- **Equipment** — separators, compressors, valves, heat exchangers, etc., named
  so HAZOP nodes can reference them.

Unit conventions: temperature in `C` or `K`, pressure in `bara`, flow with an
explicit unit string (`kg/hr`, `MSm3/day`). See
[Extract Process to NeqSim JSON](../examples/index.md) for the builder schema.

## 2. HAZOP deviation request (`runHazopScenario`)

`HazopScenarioRunner.run(json)` accepts one JSON object that embeds the process
model and the deviation to quantify:

| Field | Required | Meaning |
|-------|----------|---------|
| `process` | yes | The `ProcessSystem` builder JSON (section 1) |
| `guideWord` | optional | IEC 61882 guide-word filter (`MORE`, `LESS`, `NO`, `REVERSE`, `AS_WELL_AS`, `PART_OF`, `OTHER_THAN`) |
| `parameter` | optional | HAZOP parameter filter (`FLOW`, `PRESSURE`, `TEMPERATURE`, `LEVEL`, `COMPOSITION`, `REACTION`) |
| `nodeTag` | optional | Unit-operation name to scope the node to one equipment item |
| `limits` | optional | Design-limit policy (section 4) |

When `guideWord`/`parameter`/`nodeTag` are omitted the runner quantifies every
mappable deviation in the flowsheet. The response is a stable schema:

```json
{
  "schemaVersion": "1.0",
  "status": "ok",
  "matchCount": 1,
  "findings": [
    {
      "nodeId": "Node-02: 2nd Stage (Compressor)",
      "unitName": "2nd Stage",
      "guideWord": "MORE",
      "parameter": "TEMPERATURE",
      "computedValue": 168.4,
      "designLimit": 170.0,
      "valueUnit": "C",
      "verdict": "PASS",
      "calculator": "Discharge temperature (polytropic compression + flash)",
      "standardReference": "API 617 / API 521",
      "limitBasis": "Max discharge temperature 170.0 C (per-unit override for '2nd Stage'; basis: equipment data sheet / API 617)",
      "message": "Discharge temperature 168.4 C within maximum allowable 170.0 C."
    }
  ]
}
```

`status` is `"error"` for empty/invalid input or a process that fails to build.

## 3. Design conditions for DEXPI export

To carry equipment design limits into a DEXPI P&ID, attach a
`DesignConditions` block to each equipment item before exporting with
`DexpiXmlWriter`. The front-end supplies these from equipment data sheets:

| Field | Unit | DEXPI attribute |
|-------|------|-----------------|
| Design pressure | bara | `DesignPressure` |
| Maximum design temperature | °C | `DesignTemperature` |
| Minimum design temperature (MDMT) | °C | `MinimumDesignTemperature` |
| Relief set pressure | bara | `ReliefSetPressure` |
| Corrosion allowance | mm | `CorrosionAllowance` |
| Construction material | text | `ConstructionMaterial` |
| Failure action | enum | `FailureAction` (`FAIL_CLOSED`, `FAIL_OPEN`, `FAIL_LAST`, `FAIL_INDETERMINATE`, `NOT_SPECIFIED`) |

```java
DesignConditions dc = separator.getDesignConditions();
dc.setDesignPressure(120.0)
  .setMaxDesignTemperature(180.0)
  .setMinDesignTemperature(-46.0)
  .setReliefSetPressure(132.0)
  .setConstructionMaterial("Duplex 22Cr")
  .setFailureAction(DesignConditions.FailureAction.FAIL_CLOSED);
```

These are exported as a `GenericAttributes Set="DesignConditions"` group so the
P&ID round-trips the design basis used by the HAZOP verdicts.

## 4. Design-limit policy (`limits`) and provenance

So a green/red verdict is auditable, every finding carries a `limitBasis`
string explaining where the limit came from. The `limits` object supplies the
policy:

| Field | Default | Used by |
|-------|---------|---------|
| `maxDischargeTemperatureC` | 150.0 °C (API 617 screening) | compressor/expander MORE TEMPERATURE |
| `minDesignMetalTemperatureC` | -46.0 °C (ASME UCS-66) | valve LESS TEMPERATURE (auto-refrigeration) |
| `maxDischargeTemperatureByUnit` | — | per-unit override map `{ "unitName": value }` |
| `minDesignMetalTemperatureByUnit` | — | per-unit override map `{ "unitName": value }` |

A per-unit override takes precedence over the screening default and is recorded
in the `limitBasis` so a reviewer sees whether the limit is a data-sheet value
or a conservative default.

## 5. Blocked-outlet overpressure screening

For a MORE PRESSURE / blocked-outlet deviation, `BlockedOutletOverpressureAnalyzer`
wraps the vessel filling physics and reports time-to-relief-set per API 521 §4.4:

```java
BlockedOutletOverpressureAnalyzer analyzer =
    new BlockedOutletOverpressureAnalyzer(fluid, 5.0); // vessel volume m3
analyzer.setInletConditions(298.15, 90.0, 2.0)         // T(K), supply P(bara), flow(kg/s)
        .setReliefSetPressure(50.0)                    // bara
        .setTimeStep(1.0)
        .setMaxTime(1800.0);
BlockedOutletOverpressureAnalyzer.BlockedOutletResult r = analyzer.run();
// r.reliefDemand, r.timeToReliefSetSeconds, r.maxPressureBara, r.toJson()
```

The front-end supplies the trapped vessel volume, the upstream supply pressure
and inlet mass flow, and the protected equipment's relief set pressure
(section 3).

## See also

- [HAZOP Worksheet (IEC 61882)](HAZOP.md)
- [Automated HAZOP from STID and Simulation](automated_hazop_from_stid.md)
- [Relief Valve Sizing (API 520/521)](relief_valve_sizing_api.md)
- [MDMT Assessment (ASME UCS-66)](mdmt_assessment.md)
