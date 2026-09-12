---
title: Implementing HIPPS Models
description: Source-linked implementation workflow for NeqSim HIPPS simulation studies.
---

This guide turns a project-defined HIPPS function into a bounded NeqSim model. Begin
with the project safety requirements and select one of the two current APIs described in
[HIPPS Modeling Boundary](HIPPS_SUMMARY).

NeqSim models can support design evidence. They do not select a SIL, approve a trip
set point, demonstrate independence, or certify IEC 61508 or IEC 61511 compliance.

## Inputs to freeze before coding

Record these inputs with units, source, revision, and responsible discipline:

| Input | Required project evidence |
| --- | --- |
| Protected system and pressure source | Scenario definition, design pressure, operating envelope, and relief philosophy |
| Detector configuration | Tags, absolute-pressure units, set points, range, accuracy, fault and bypass assumptions |
| Voting architecture | Required votes, total channels, independence, common-cause basis, and degraded-state policy |
| Final element | Valve tag, stream connection, fail state, capacity basis, closure profile, leakage, and utilities |
| Timing | Sampling, detection, logic, travel, escalation, and numerical time-step assumptions |
| Lifecycle data | Proof-test interval/effectiveness, failure data, diagnostics, maintenance, and reset authorization |
| Acceptance | Maximum pressure, event ordering, final safe state, conservation, convergence, and sensitivity criteria |

Never adopt a source default or test fixture as a design value without project-specific
justification.

## Option A: equipment-centric `HIPPSValve`

Use `HIPPSValve` when the study already represents each pressure transmitter as a
`MeasurementDeviceInterface` with alarm state and the final element should be the
stream-connected equipment itself.

The current source-backed sequence is:

1. Construct `HIPPSValve(String, StreamInterface)`.
2. Configure each pressure transmitter and add it with
   `addPressureTransmitter(MeasurementDeviceInterface)`.
3. Select the nested `HIPPSValve.VotingLogic`.
4. Set project-specific transient inputs such as `setClosureTime(double)`.
5. Advance the model with `runTransient(double, UUID)` using seconds and a stable
   calculation identity.
6. Read `hasTripped()`, valve opening, active-transmitter count, and diagnostics into
   structured scenario evidence.
7. Use `reset()` only to reset the simulation case. The model does not implement a
   physical operating authorization workflow.

`setSILRating(int)`, proof-test timers, partial-stroke state, and diagnostic strings are
model configuration or bookkeeping. They do not calculate probability of failure on
demand or demonstrate SIL achievement.

The enabled
[HIPPSValveTest](../../src/test/java/neqsim/process/equipment/valve/HIPPSValveTest.java)
is the executable contract for this API.

## Option B: logic-centric `HIPPSLogic`

Use `HIPPSLogic` when pressure values should be passed explicitly through
`Detector` objects and the safety action should be separated from the
`ThrottlingValve`.

The current source-backed sequence is:

1. Construct `HIPPSLogic(String, VotingLogic)` with
   `neqsim.process.logic.sis.VotingLogic`.
2. Construct the required number of pressure `Detector` objects with explicit alarm
   level, set point, and unit.
3. Add them with `addPressureSensor(Detector)`.
4. Connect a `ThrottlingValve` with `setIsolationValve(ThrottlingValve)`.
5. Pass one value per configured detector to `update(double...)`.
6. Advance time-dependent escalation with `execute(double)`, where the time step is
   seconds.
7. Record `isTripped()`, `hasEscalated()`, `getState()`, valve opening, detector
   states, and the calculation context.
8. Clear detector states before calling `reset()`; the method fails closed while any
   detector remains tripped.

The implementation closes the linked valve by setting its opening to zero when voting
trips. It does not calculate valve hydraulics, actuator reliability, or process pressure
decay. Those behaviors need an appropriately bounded transient process model.

The
[ProcessSafetyOverviewDocumentationTest](../../src/test/java/neqsim/process/safety/ProcessSafetyOverviewDocumentationTest.java)
protects the documented constructor and method types.

## Executable evidence

Run and retain the enabled HIPPS regressions for the exact repository revision used by
the study. For a complete Java 8/Log4j2 package example, use the maintained
[ProcessSafetyOverviewQuickStart](../process/safety/README.md#executable-java-8-quick-start).
That example demonstrates safety-equipment and ESD integration; it does not claim to
validate a HIPPS design.

This guide intentionally does not duplicate an abbreviated Java fragment. The linked
example and enabled tests are the maintained executable evidence.

## Validation checklist

Before interpreting a result, verify:

- the steady-state or initial transient condition is converged and physically plausible;
- every detector value uses the declared absolute-pressure basis and correct ordering;
- the voting group has the expected channel count and degraded-state assumptions;
- logic and equipment share a justified time basis and stable calculation identity;
- the final element is connected to the intended stream and follows the intended
  transient closure model;
- the maximum protected pressure, relief response, inventories, flows, and final state
  meet project-specific acceptance criteria;
- mass and energy balances, event ordering, time-step sensitivity, and uncertainty are
  retained with the result;
- successful, spurious-trip, failure-to-close, bypass, faulty-channel, escalation, and
  reset scenarios are reviewed where applicable.

## Safety and approval boundary

Do not infer standards compliance, SIL capability, proof-test coverage, protection-layer
independence, relief-credit eligibility, or operating authorization from a passing
simulation. Configuration does not demonstrate SIL achievement. Results require
project-specific verification, independent functional-safety assessment, and
accountable approval.

See [HIPPS Safety-Logic Lifecycle](hipps_safety_logic) for exact state semantics and the
[Safety Systems Package](../process/safety/README.md) for integration with relief, ESD,
blowdown, and transient evidence.
