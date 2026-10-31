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

## Event-exact timing policy

The default runner policy remains `LEGACY_FIXED_SCAN` for compatibility. For quantitative response-time
verification, opt in with `.eventExactTiming(true)`. The event-exact policy:

- splits accepted process steps at a non-grid initiating-event time and at the requested scenario duration;
- applies the initiating event at that exact boundary and records a zero-second criterion sample;
- advances the physical process first, then samples sensor/voting/logic and criteria at the accepted
  end-of-step state;
- uses deterministic physical-step identities and exports every accepted step duration;
- reports `triggerTimeSeconds`, requested and completed duration, and `timingPolicy` in the result.

The policy does not interpolate inside a sensor, logic-solver, or actuator scan. Choose a scan interval that
resolves the shortest declared response time and repeat at a smaller interval. In the public 0.25 s synthetic
2oo3 case, two healthy 1.0 s channels vote at 1.0 s, the 0.5 s logic delay actuates at 1.5 s, and the 2.0 s
ESD-valve stroke reaches the closed criterion at 3.5 s. Replacing one healthy channel by
`STUCK_NORMAL` while another is bypassed leaves the scenario FAIL with no vote or actuation timestamp.
These are executable software-timing checks, not vendor response data or safety credit.

## Final-element availability and traceability

`ESDValve` is available by default. Set `setActuatorAvailable(false)` only when a declared
unavailable-actuator or mechanically stuck case must hold the valve at its current
position after a shutdown demand. The solenoid and trip logic still change state, so the result
distinguishes a missing demand from a demanded final element that cannot travel. Use
`setStrokeTime(...)` for a slow but moving case; do not represent slow travel by clipping the
position or by treating an unavailable valve as closed.

Attach controlled identities to each executable case with `addRequirementId(...)` and
`addEquipmentTag(...)`. The scenario and `dynamic_safety_scenario_result.v2` retain these values in
insertion order, providing a stable requirement-to-tag-to-scenario-to-result join for study reports.
The runner does not infer approval or safety credit from these identifiers. When a scenario belongs to an
`EngineeringProject`, `EngineeringProject.validate()` checks that every requirement identifier exists and that
every exercised tag resolves to process equipment, a stream, or a measurement device. Give the case configuration,
initiating event, each logic factory, and each criterion extractor stable definition-and-revision identifiers through
the overloads that accept a definition ID. Change that ID whenever the callback behavior or its controlled source
revision changes. Legacy overloads remain available but are exported as `UNSPECIFIED` and
produce the `ENG-DYNAMIC-006` review finding. Unregistered evidence references remain review findings rather than
simulated proof of an approved SRS, P&ID, instrument index, or cause-and-effect chart.

The public regression matrix uses the same 1.0 s sensor delay and 0.5 s logic delay for every case:

| Final-element case | Declared behavior | First safe position | 3.5 s deadline |
| --- | --- | ---: | --- |
| Healthy | 2.0 s full stroke | 3.5 s | PASS |
| Slow | 4.0 s full stroke | 5.5 s | FAIL |
| Unavailable | demanded, no travel | not reached | FAIL |

The 0.25 s and 0.125 s scans both resolve the healthy safe-position time at 3.5 s. Rejected
transient trials and serialized restart preserve the availability flag. These values qualify the
synthetic software case only; use reviewed vendor stroke data, utility architecture, site test data,
and project response-time requirements for engineering decisions.

## Revision-controlled scenario evidence

After `EngineeringSimulationRunner` executes a project, the project retains the latest coordinated result as
transient run evidence. `EngineeringGraphBuilder` then materializes two stable identities for each case:

- a `SCENARIO` node containing the executable public definition, evidence references, requirement links, and
  exercised equipment/instrument links;
- a `SCENARIO_RESULT` node containing the quantitative traces, verdict, timing policy, accepted physical steps,
  logic evidence, requirement identifiers, and equipment tags.

The result node is `GENERATED_FROM` the scenario node. Consequently, a changed public scenario definition
propagates revision impact to its result, while a changed quantitative result can invalidate only the result.
`EngineeringApprovalLedger` reports existing approvals as `REVALIDATION_REQUIRED` for the affected node(s);
it never silently carries approval across a changed executable definition or result. The latest result is transient:
rerun the coordinated simulation before compiling an evidence graph after restart or deserialization.

Controlled definition IDs are project-governed identities, not hashes of callback bytecode. Configuration control must
change an ID when implementation behavior changes and rerun the scenario before review.

This graph contract records simulation evidence and review state. It does not approve the scenario, assign SIL or
IPL credit, or establish detector coverage, voting independence, vendor response, or site acceptance.

## 2oo3 high-pressure isolation example

```java
DynamicSafetyScenario scenario = DynamicSafetyScenario
    .builder("SIF-HP-101", "HP inlet isolation")
    .durationSeconds(8.0)
    .timeStepSeconds(0.25)
    .initiatingEvent("feed-high-pressure-v1", process ->
        ((Stream) process.getUnit("FEED")).setPressure(70.0, "bara"))
    .addLogic("sif-hp-101-2oo3-v1", process -> {
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
        .builder("esdv-closed", "ESD valve closed", "%",
            "esdv-101-opening-percent-v1", process ->
            ((ESDValve) process.getUnit("ESDV-101"))
                .getPercentValveOpening())
        .acceptanceRange(null, 5.0)
        .deadlineSeconds(5.0)
        .build())
    .addRequirementId("REQ-SIF-HP-101")
    .addEquipmentTag("PT-101A")
    .addEquipmentTag("PT-101B")
    .addEquipmentTag("PT-101C")
    .addEquipmentTag("ESDV-101")
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
- ordered requirement identifiers and equipment/instrument tags;
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

