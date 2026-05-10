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
5. Use `qualityGates.acceptableForOperationScreening` only as a screening flag; engineering review
   is still required before changing plant operation.

## Related Documentation

- [Plant Data & Tagreader](plant-data-tagreader.md)
- [STID/E3D Route Builder](piping_route_builder.md)
- [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [Bottleneck Analysis](../wiki/bottleneck_analysis.md)
