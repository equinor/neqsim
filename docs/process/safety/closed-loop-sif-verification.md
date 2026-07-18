---
title: Closed-loop SIF verification
description: Execute process detection, voting, logic delay, and final-element response in an isolated NeqSim transient.
---

# Closed-loop SIF verification

`ClosedLoopSafetyFunction` connects process signals to redundant channels, a voting pattern,
a logic-solver delay, and existing `ProcessLogic` final elements. Run it through
`DynamicSafetyScenarioRunner` to verify the complete detection-to-safe-state path against an
isolated copy of a `ProcessSystem`.

This workflow produces simulation evidence for engineering review. It does not assign SIL,
approve an SRS, establish IPL independence, or replace project validation and functional-safety
assessment. Those lifecycle activities remain controlled engineering decisions under IEC 61511.

## Execution model

1. The runner copies and initializes the design process.
2. The initiating event is applied only to the copy.
3. Each channel reads a live process signal and applies its trip direction, response delay, and
   explicit fault mode.
4. The configured `VotingPattern` evaluates the latched channel trips.
5. After the logic-solver delay, the supplied ESD, HIPPS, or other `ProcessLogic` executes.
6. Dynamic criteria observe the physical process and final elements until their deadlines.
7. The result records channel traces, vote time, actuation time, final state, and criteria verdicts.

## 2oo3 high-pressure isolation example

```java
DynamicSafetyScenario scenario = DynamicSafetyScenario
    .builder("SIF-HP-101", "HP inlet isolation")
    .durationSeconds(8.0)
    .timeStepSeconds(0.25)
    .initiatingEvent(process ->
        ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara"))
    .addLogic(process -> {
      ESDValve valve = (ESDValve) process.getUnit("ESDV-101");
      ESDLogic finalElements = new ESDLogic("ESD-101 final elements");
      finalElements.addAction(new TripValveAction(valve), 0.0);

      SafetyFunctionChannel.SignalExtractor pressure = model ->
          ((Stream) model.getUnit("FEED")).getPressure("bara");
      return ClosedLoopSafetyFunction
          .builder("SIF-HP-101", "2oo3 HP isolation", process,
              VotingPattern.TWO_OUT_OF_THREE, finalElements)
          .addChannel(SafetyFunctionChannel
              .highTrip("PT-101A", "bara", 60.0, pressure)
              .responseDelaySeconds(0.5).build())
          .addChannel(SafetyFunctionChannel
              .highTrip("PT-101B", "bara", 60.0, pressure)
              .responseDelaySeconds(0.5).build())
          .addChannel(SafetyFunctionChannel
              .highTrip("PT-101C", "bara", 60.0, pressure)
              .responseDelaySeconds(0.5)
              .faultMode(SafetyFunctionChannel.FaultMode.BYPASSED).build())
          .logicSolverDelaySeconds(0.25)
          .build();
    })
    .addCriterion(DynamicScenarioCriterion
        .builder("esdv-closed", "ESD valve closed", "%", process ->
            ((ESDValve) process.getUnit("ESDV-101"))
                .getPercentValveOpening())
        .acceptanceRange(null, 5.0)
        .deadlineSeconds(5.0)
        .build())
    .addEvidenceReference("SRS-SIF-HP-101")
    .build();

DynamicSafetyScenarioResult result =
    DynamicSafetyScenarioRunner.run(designProcess, scenario);
Map<String, Object> sifEvidence =
    result.getLogicEvidence().get("2oo3 HP isolation");
```

The example intentionally bypasses one channel. The 2oo3 vote still requires two demanded
channels, and the evidence exposes the reduced availability for review. A bypass is never treated
as proof that operation in the degraded state is acceptable.

## Channel fault modes

| Mode | Simulation behavior | Intended verification use |
| --- | --- | --- |
| `HEALTHY` | Uses the process signal | Normal demand test |
| `BYPASSED` | Channel unavailable and cannot vote | Authorized-bypass scenario |
| `STUCK_NORMAL` | Never demands a trip | Dangerous detected/undetected failure scenario |
| `STUCK_TRIP` | Always demands a trip | Spurious-trip and voting scenario |
| `BIASED` | Adds a configured bias to the process signal | Calibration-drift sensitivity |

## Evidence and acceptance

The `dynamic_safety_scenario_result.v2` JSON contains `logicEvidence` alongside the existing
criterion traces and final logic states. Closed-loop evidence uses
`closed_loop_sif_evidence.v1` and includes:

- SIF identifier and voting architecture;
- channel configuration, raw/indicated readings, availability, fault mode, and trip state;
- first vote and final-element actuation times;
- final-element logic state and a complete step trace;
- an explicit `engineeringApprovalRequired` flag.

Define criteria from the SRS response-time and safe-state requirements. Use sufficiently small
time steps to resolve sensor, logic, and actuator dynamics, and test normal demand, each credited
degraded mode, reset/restart behavior, and failure to reach the safe state.

## Standards context

- [IEC 61511-1](https://webstore.iec.ch/en/publication/24241) defines functional-safety lifecycle
  requirements for process-industry SIS.
- [IEC 61511-2](https://webstore.iec.ch/en/publication/25510) provides application guidance.
- [IEC 61511-3](https://webstore.iec.ch/en/publication/25480) provides guidance for required SIL
  determination.
- [ISO 10418:2019](https://www.iso.org/standard/55440.html) covers offshore production-installation
  process safety systems.

Project requirements, vendor data, cause-and-effect charts, proof-test procedures, and approved
SRS values take precedence over screening examples in NeqSim.
