---
title: Operational Evidence Package
description: "Operational evidence workflow for combining STID and P&ID document references, tagreader field data, NeqSim scenarios, and bottleneck detection into one auditable operating study."
---

## Overview

The operational evidence package connects three existing NeqSim capabilities into one repeatable
workflow:

1. Technical documentation and P&ID/STID extraction provide logical tags, equipment names, line
   references, and scenario actions.
2. Plant data access supplies measured values through public logical tags or private historian tags.
3. NeqSim runs the physics model, compares benchmark tags, executes scenarios, and reports the
   limiting capacity constraint.

The Java implementation is in `neqsim.process.operations.OperationalEvidencePackage`. It builds on
the existing `OperationalTagMap`, `OperationalScenarioRunner`, `ProcessSystem.findBottleneck()`, and
registered equipment capacity strategies. Document retrieval and OCR stay outside the Java core;
Java consumes normalized JSON so private document names and historian tags can remain in task-local
configuration.

## Package Structure Fit

| Concern | Existing NeqSim capability used | Evidence package role |
|---------|---------------------------------|-----------------------|
| Technical document references | [STID/E3D route builder](piping_route_builder.md), process extraction skills | Store evidence references and P&ID-derived logical tags in input JSON |
| Plant data | [Plant Data & Tagreader](plant-data-tagreader.md) | Map historian values to logical tags and automation addresses |
| Process model | `ProcessSystem.fromJsonAndRun(...)` | Build the local simulation copy from MCP `runProcess` JSON |
| Scenario execution | `OperationalScenarioRunner` | Execute valve, variable, field-input, steady-state, and transient actions |
| Bottleneck detection | [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md) | Report bottleneck equipment, constraint, utilization, margin, and near-limit equipment |
| Operating envelope | `OperationalEnvelopeEvaluator` | Rank capacity margins, estimate simple time-to-limit trends, and suggest advisory mitigations |

## MCP Action

Use `runOperationalStudy` with `action = "runEvidencePackage"`.

```json
{
  "action": "runEvidencePackage",
  "studyName": "operations screen",
  "processJson": { "fluid": {}, "process": [] },
  "tagBindings": [
    {
      "logicalTag": "outlet_valve_position",
      "automationAddress": "Outlet Valve.percentValveOpening",
      "unit": "%",
      "role": "INPUT"
    },
    {
      "logicalTag": "outlet_pressure",
      "automationAddress": "Outlet Valve.outletPressure",
      "unit": "bara",
      "role": "BENCHMARK"
    }
  ],
  "fieldData": {
    "outlet_valve_position": 70.0,
    "outlet_pressure": 49.0
  },
  "benchmarkToleranceFraction": 0.05,
  "scenarios": [
    {
      "scenarioName": "raise valve loading",
      "actions": [
        { "type": "SET_VALVE_OPENING", "target": "Outlet Valve", "value": 90.0 },
        { "type": "RUN_STEADY_STATE" }
      ]
    }
  ],
  "evidenceReferences": [
    { "id": "PID-001", "type": "P&ID", "description": "Source drawing reference" }
  ],
  "assumptions": ["Private historian tags are resolved outside the public model."]
}
```

The response includes:

| Field | Description |
|-------|-------------|
| `validation` | Operational tag-map validation with remediation text |
| `evidencePackage.appliedFieldData` | Values applied to writable model inputs |
| `evidencePackage.modelValues` | Current model readback for mapped tags |
| `evidencePackage.benchmarkComparison` | BENCHMARK tag deviation and tolerance status |
| `evidencePackage.baseCapacity` | Base-case bottleneck, utilization summary, near-limit equipment, and constraint details |
| `evidencePackage.scenarioStudies` | Scenario execution log and post-scenario capacity report |
| `evidencePackage.qualityGates` | Screening gates for benchmark agreement, hard limits, design capacity, and scenario success |

## Operating Envelope Screening

Use `runOperationalStudy` with `action = "evaluateOperatingEnvelope"` when the question is
which equipment margin is closest to an operating trip or constraint limit. The action builds the
process from `processJson`, optionally applies STID/datasheet limits from `designCapacities`,
optionally applies tagreader field values through `tagBindings`, and then ranks margins from the
registered equipment capacity strategies.

```json
{
  "action": "evaluateOperatingEnvelope",
  "processJson": { "fluid": {}, "process": [] },
  "tagBindings": [
    {
      "logicalTag": "outlet_valve_position",
      "automationAddress": "Outlet Valve.percentValveOpening",
      "unit": "%",
      "role": "INPUT"
    }
  ],
  "fieldData": {
    "outlet_valve_position": 72.0
  },
  "designCapacities": {
    "Outlet Valve": { "maximumValveOpening": 100.0 }
  },
  "marginHistory": [
    { "key": "Outlet Valve.valveOpening", "timestampSeconds": 0.0, "marginPercent": 35.0 },
    { "key": "Outlet Valve.valveOpening", "timestampSeconds": 60.0, "marginPercent": 25.0 }
  ],
  "predictionHorizonSeconds": 3600.0,
  "evidenceReferences": [
    { "id": "PID-001", "type": "P&ID", "description": "Source drawing reference" },
    { "id": "PI-001", "type": "tagreader", "description": "Saved plant-data snapshot" }
  ]
}
```

The response includes `operatingEnvelope.overallStatus`, `rankedMargins`,
`tripPredictions`, and `mitigationSuggestions`. These outputs are advisory screening results: use
them to focus operator or engineering review, then validate any action against the P&ID, STID
source evidence, historian data quality, and relevant equipment procedures.

## Bottleneck Detection

`baseCapacity.bottleneck` first uses `ProcessSystem.findBottleneck()`. If the process has no
equipment-attached constraints, it falls back to the `EquipmentCapacityStrategyRegistry`, which
provides strategy constraints for common equipment such as valves, compressors, separators, pipes,
heat exchangers, pumps, reactors, and subsea equipment.

The bottleneck block reports:

| Field | Meaning |
|-------|---------|
| `equipmentName` | Limiting equipment name |
| `constraintName` | Limiting constraint, such as `valveOpening`, `pressureDropRatio`, or compressor power |
| `utilizationPercent` | Current utilization relative to design capacity |
| `marginPercent` | Remaining headroom before design capacity |
| `exceeded` | True when the design limit is exceeded |
| `nearLimit` | True when the warning threshold is exceeded |
| `constraint` | Current, design, maximum, severity, type, and unit details |

## Recommended Workflow

1. Extract a normalized operational model from documents: equipment names, logical tags, source
   references, and candidate actions.
2. Bind logical tags to NeqSim automation addresses and, in private configuration, historian tags.
3. Run `validateTagMap` first when building a new model.
4. Run `runEvidencePackage` for each operating snapshot or scenario set.
5. Run `evaluateOperatingEnvelope` when margin ranking, trip prediction, or advisory mitigation is
  needed for the same snapshot.
6. Use `qualityGates.acceptableForOperationScreening` only as a screening flag; engineering review
   is still required before changing plant operation.

## Hydraulic Surge Handoff

Operational evidence packages can identify the initiating action for a water-hammer
or liquid-hammer study, but the high-speed acoustic transient is calculated with
`WaterHammerStudy` or MCP `runWaterHammer`.

Use this handoff when the scenario closes a liquid-line valve quickly, trips a pump,
or creates check-valve slam risk:

1. Use P&ID/STID evidence to define the affected line, isolation valve, pump, and
  route source references.
2. Use tagreader field data for the event window: pressure, temperature, flow,
  valve position, pump speed/status, and observed closure time.
3. Run `runOperationalStudy` if the process model needs a steady-state or
  tag-map quality check before the event.
4. Pass the same `fieldData`, `evidenceReferences`, route geometry, and
  `eventSchedule` to `runWaterHammer`.
5. Include the water-hammer pressure envelope and design-pressure margin in the
  evidence package assumptions, risk register, or follow-up action list.

The two tools are complementary: `runOperationalStudy` validates the operating
snapshot and scenario context, while `runWaterHammer` screens the surge envelope
on the affected route.

## Related Documentation

- [Plant Data & Tagreader](plant-data-tagreader.md)
- [STID/E3D Route Builder](piping_route_builder.md)
- [Water Hammer Simulation](../wiki/water_hammer_implementation.md)
- [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [Bottleneck Analysis](../wiki/bottleneck_analysis.md)
