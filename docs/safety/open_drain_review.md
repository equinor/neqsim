---
title: Open Drain Review with NeqSim Evidence
description: Guide to NORSOK S-001 Clause 9 open-drain review using NeqSim process and thermodynamic calculations for liquid leak rate, firewater load, liquid density, pressure, and drain hydraulic capacity.
---

## Overview

Open-drain review in NeqSim combines two evidence streams:

| Evidence stream | Source | Use in review |
|-----------------|--------|---------------|
| Calculated process evidence | NeqSim streams, equipment, and process systems | Liquid leak rate, liquid density, pressure, temperature, firewater load, and drain hydraulic capacity |
| Normalized document and operational evidence | STID/P&ID extracts, line lists, design-basis records, and optional tagreader summaries | Drain segregation, backflow prevention, seals, vents, fire-area separation, helideck and temporary-storage evidence |

The central class for the calculated path is `neqsim.process.safety.opendrain.OpenDrainProcessEvidenceCalculator`. It builds `OpenDrainReviewInput` or `OpenDrainReviewItem` objects that are consumed by `OpenDrainReviewEngine`, so the same NORSOK S-001 Clause 9 checks can run on either calculated NeqSim evidence or externally normalized JSON.

The MCP runner `run_open_drain_review` accepts normalized JSON. It does not open a direct STID or tagreader connection; retrieval and historian reads should normalize evidence before calling the Java runner.

## Calculated Values

`OpenDrainProcessEvidenceCalculator` writes these review keys into each `OpenDrainReviewItem` when the underlying stream contains enough thermodynamic and design-basis data:

| Key | Unit | Meaning |
|-----|------|---------|
| `neqsimLiquidMassFlowKgPerS` | kg/s | Total liquid-like phase mass flow from oil, aqueous, liquid, and liquid-asphaltene phases |
| `liquidLeakRateKgPerS` | kg/s | Credible liquid leak rate, calculated from liquid flow, leak fraction, and maximum credible leak cap |
| `liquidDensityKgPerM3` | kg/m3 | Mass-flow-weighted liquid density, or total fluid density if no liquid phase is present |
| `pressureBara` | bara | Stream operating pressure |
| `temperatureC` | deg C | Stream operating temperature |
| `fireWaterCapacityKgPerS` | kg/s | Firewater mass load from application area, application rate, and water density |
| `drainageCapacityKgPerS` | kg/s | Gravity-drain hydraulic capacity from density, drain diameter, head, discharge coefficient, and backpressure |

The capacity check in `OpenDrainReviewEngine` compares:

$$
\dot{m}_{required} = \dot{m}_{\mathrm{firewater}} + \dot{m}_{\mathrm{credible\ leak}}
$$

against `drainageCapacityKgPerS`. The gravity-drain helper subtracts downstream backpressure as an opposing liquid head before calculating capacity.

## Java Example

This example creates a liquid stream, derives open-drain evidence from NeqSim, and runs the review engine. The same API calls are covered by `OpenDrainReviewEngineTest.testNeqSimStreamEvidenceDrivesOpenDrainReview`.

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.safety.opendrain.OpenDrainProcessEvidenceCalculator;
import neqsim.process.safety.opendrain.OpenDrainReviewEngine;
import neqsim.process.safety.opendrain.OpenDrainReviewInput;
import neqsim.process.safety.opendrain.OpenDrainReviewItem;
import neqsim.process.safety.opendrain.OpenDrainReviewReport;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface fluid = new SystemSrkEos(293.15, 8.0);
fluid.addComponent("n-heptane", 1.0);
fluid.setMixingRule("classic");

Stream liquidStream = new Stream("OD process liquid", fluid);
liquidStream.setFlowRate(8.0, "kg/sec");
liquidStream.run();

OpenDrainProcessEvidenceCalculator.DesignBasis basis =
    new OpenDrainProcessEvidenceCalculator.DesignBasis()
        .setAreaId("OD-NEQSIM-01")
        .setSourceReference("NeqSim process model: OD process liquid")
        .setFireWaterAreaM2(20.0)
        .setFireWaterApplicationRateLPerMinM2(18.0)
        .setDrainPipeDiameterM(0.20)
        .setAvailableDrainHeadM(1.0)
        .setCredibleLeakFractionOfLiquidFlow(1.0)
        .setMaximumCredibleLiquidLeakRateKgPerS(5.0);

OpenDrainReviewInput input = OpenDrainProcessEvidenceCalculator.createInputFromStream(
    "NeqSim calculated open drain review", liquidStream, basis);

OpenDrainReviewItem item = input.getItems().get(0);
double leakRateKgPerS = item.getDouble(Double.NaN, "liquidLeakRateKgPerS");
double drainCapacityKgPerS = item.getDouble(Double.NaN, "drainageCapacityKgPerS");

OpenDrainReviewReport report = new OpenDrainReviewEngine().evaluate(input);
String verdict = report.getOverallVerdict();
```

## Process-System Workflow

Use `createInputFromProcessSystem(process, basis)` when a complete flowsheet is available. The helper walks process units, selects representative inlet or outlet streams, and creates one review item per stream-bearing unit.

For multi-area plants, create area-specific design bases where firewater area, drain diameter, available head, and safeguard evidence differ. The generic process-system helper is a screening shortcut, not a substitute for area-by-area STID and line-list evidence.

## Normalized JSON Evidence

The review engine also accepts normalized items, `openDrainAreas`, `drainAreas`, or `stidData`. Use this when STID/P&ID extraction or tagreader summaries have already provided evidence values.

```json
{
  "projectName": "Open drain review",
  "openDrainAreas": [
    {
      "areaId": "OD-A01",
      "areaType": "process area",
      "drainSystemType": "hazardous open drain",
      "standards": "NORSOK S-001; NORSOK P-002; ISO 13702",
      "sourceHasFlammableOrHazardousLiquid": true,
      "liquidLeakRateKgPerS": 5.0,
      "fireWaterCapacityKgPerS": 6.0,
      "drainageCapacityKgPerS": 12.0,
      "hasOpenDrainMeasures": true,
      "backflowPrevented": true,
      "closedOpenDrainInteractionPrevented": true,
      "hazardousNonHazardousPhysicallySeparated": true,
      "sealDesignedForMaxBackpressure": true,
      "ventTerminatedSafe": true,
      "openDrainDependsOnUtility": false
    }
  ]
}
```

## Safeguards That Still Need Evidence

NeqSim can calculate process and hydraulic evidence, but it cannot infer all layout and design-basis requirements from a stream. Keep these values traceable to STID/P&ID, design basis, line list, area safety chart, or tagreader records:

| Evidence key | Typical source |
|--------------|----------------|
| `hasOpenDrainMeasures` | STID/P&ID drain boxes, gutters, coamings, deck drainage |
| `backflowPrevented` | Drain hydraulics, non-return devices, elevation breaks, sump/caisson checks |
| `closedOpenDrainInteractionPrevented` | Drain philosophy, P&ID separation, seal design |
| `hazardousNonHazardousPhysicallySeparated` | Drain routing drawings and common-sump safeguards |
| `sealDesignedForMaxBackpressure` | Seal height and hydraulic grade-line calculation |
| `ventTerminatedSafe` | Vent routing drawings and hazardous-area review |
| `openDrainDependsOnUtility` | Utility-loss assessment and pump/sump design |

## Verification

The documented NeqSim stream workflow is exercised in `OpenDrainReviewEngineTest`. Focused verification command:

```powershell
.\mvnw.cmd test "-Dtest=OpenDrainReviewEngineTest"
```

Use targeted Checkstyle when changing the package:

```powershell
.\mvnw.cmd checkstyle:check "-Dcheckstyle.includes=**/opendrain/*.java,**/OpenDrainReviewEngineTest.java"
```
