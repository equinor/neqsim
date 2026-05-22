---
title: ESD Dynamic Testing Workflow
description: Emergency shutdown dynamic testing workflow using NeqSim process logic, ESD valves, OperationalTagMap bindings, tagreader field data, and JSON evidence reports for NORSOK S-001 Clause 11 verification.
---

## Purpose

The ESD dynamic testing workflow turns an emergency shutdown philosophy, cause-and-effect row,
technical documentation, and optional tagreader event data into a repeatable NeqSim simulation test.
It is intended for virtual commissioning, proof-test planning, tagreader replay, and evidence packs
for NORSOK S-001 Clause 11 reviews.

The workflow does not replace certified SIS/FAT/SAT or proof testing on the real safety system. It
checks whether the documented ESD sequence, when represented in NeqSim, gives the expected process
response.

## Package Structure

Core classes are in `neqsim.process.safety.esd`:

| Class | Role |
|-------|------|
| `EmergencyShutdownTestPlan` | Test setup: duration, trigger time, logic names, tag map, field data, references, and criteria |
| `EmergencyShutdownTestCriterion` | Acceptance checks for final values, min/max values, response changes, logic completion, simulation errors, and model-to-field deviation |
| `EmergencyShutdownTestRunner` | Executes existing `ProcessLogic` sequences with `ProcessSystem.runTransient(...)` and captures monitored values |
| `EmergencyShutdownTestResult` | JSON-ready evidence report with verdict, time series, signal statistics, field comparisons, warnings, errors, and criteria results |

The package reuses existing NeqSim functionality rather than duplicating it:

| Existing API | Used For |
|--------------|----------|
| `ESDValve` | Fail-safe isolation valve dynamics and stroke time |
| `ESDLogic` and `LogicAction` | Cause-and-effect shutdown sequence execution |
| `ProcessSystem.runTransient(...)` | Dynamic process simulation |
| `OperationalTagMap` | Mapping logical tags to historian tags, P&ID references, and NeqSim automation addresses |
| `ProcessAutomation` | Reading monitored variables such as valve opening, pressure, temperature, and flow |

## Workflow

1. Extract ESD functions from technical documentation such as C&E, SRS, P&IDs, valve datasheets,
   F&G action charts, and operating procedures.
2. Create NeqSim equipment and ESD logic using existing process and safety classes.
3. Create an `OperationalTagMap` that maps public logical tags to private historian tags and NeqSim
   automation addresses.
4. Load tagreader event data into a `Map<String, Double>` keyed by logical tag or historian tag.
5. Build an `EmergencyShutdownTestPlan` with monitor tags, criteria, evidence references, and
   standards references.
6. Run `EmergencyShutdownTestRunner.run(...)` and store `EmergencyShutdownTestResult.toJson()` in
   the task evidence package or report.

## Example

```java
ESDLogic esdLogic = new ESDLogic("ESD Level 1");
esdLogic.addAction(new TripValveAction(isolationValve), 0.0);

OperationalTagMap tagMap = new OperationalTagMap()
    .addBinding(OperationalTagBinding.builder("xv_opening")
        .historianTag("PRIVATE-XV-001-ZSO")
        .pidReference("P&ID-ESD-001/XV-001")
        .automationAddress("ESD Inlet Isolation.percentValveOpening")
        .unit("%")
        .role(InstrumentTagRole.BENCHMARK)
        .build());

EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("ESD1 isolation closure")
    .duration(8.0)
    .timeStep(1.0)
    .tagMap(tagMap)
    .enableLogic("ESD Level 1")
    .triggerLogic("ESD Level 1")
    .fieldData("xv_opening", 0.0)
    .criterion(EmergencyShutdownTestCriterion.finalAtMost(
        "ESD-XV-CLOSED", "xv_opening", 5.0, "%")
        .withClause("NORSOK S-001 Clause 11"))
    .criterion(EmergencyShutdownTestCriterion.logicCompleted(
        "ESD-LOGIC-COMPLETE", "ESD Level 1"))
    .criterion(EmergencyShutdownTestCriterion.fieldAbsoluteDeviationAtMost(
        "ESD-FIELD-MATCH", "xv_opening", 5.0, "%"))
    .standardReference("NORSOK S-001 Clause 11 emergency shutdown testing")
    .evidenceReference("Cause and effect matrix row ESD-001")
    .build();

EmergencyShutdownTestResult report = EmergencyShutdownTestRunner.run(process, plan, esdLogic);
String json = report.toJson();
```

The example pattern is covered by the focused `EmergencyShutdownTestRunnerTest` unit test.

## Evidence Output

The JSON report includes:

| Field | Description |
|-------|-------------|
| `verdict` | `PASS`, `PASS_WITH_WARNINGS`, or `FAIL` |
| `signalStats` | Initial, final, minimum, and maximum values for every monitored logical tag |
| `timeSeries` | Time-stamped monitored values for replay and plotting |
| `logicStates` | Final state of each supplied `ProcessLogic` sequence |
| `fieldComparisons` | Model-to-field deviation for tagreader-backed evidence |
| `criterionResults` | Requirement-level pass/fail result with clause, severity, and recommendation |
| `evidenceReferences` | Source documents such as C&E, SRS, P&ID, valve datasheets, and procedure IDs |

## Related Documentation

- [ESD Blowdown System](ESD_BLOWDOWN_SYSTEM.md)
- [Pressure Monitoring ESD](PRESSURE_MONITORING_ESD.md)
- [Barrier Management](barrier_management.md)
- [Process Safety Package](../process/safety/README.md)