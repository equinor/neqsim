---
title: HIPPS Safety-Logic Lifecycle
description: Current HIPPSLogic voting, trip, escalation, override, and reset semantics.
---

`HIPPSLogic` is a logic-centric simulation component. It evaluates explicit detector
values, changes its own state, and commands a linked `ThrottlingValve`. This page
documents current software behavior and the evidence a project must add before using
the result in a safety decision.

For API selection and the equipment-centric alternative, start with
[HIPPS Modeling Boundary](HIPPS_SUMMARY).

## Construction and identity

Construct `HIPPSLogic(String, VotingLogic)` with a stable project tag and the
`neqsim.process.logic.sis.VotingLogic` enum. That enum defines required votes and
total channels for 1oo1, 1oo2, 2oo2, 2oo3, 2oo4, and 3oo4 patterns.

Add no more than the enum's declared channel count with
`addPressureSensor(Detector)`. `Detector` stores its type, alarm level, numerical set
point, and unit text. The caller is responsible for converting each update value to the
declared unit and for retaining source and calibration context.

Connect the final element with `setIsolationValve(ThrottlingValve)`. The reference is
optional in software, but a trip without a linked valve cannot isolate a modeled stream.

## Voting and trip transition

`update(double...)` requires exactly one value per configured detector. Each detector
updates before the voting condition is evaluated.

The current evaluation order is:

1. If logic override is active, return without evaluating.
2. Count bypassed and faulty detectors.
3. Enter `FAILED` when their combined count exceeds the model's allowed count.
4. Count tripped detectors that are neither bypassed nor faulty.
5. Evaluate the configured `VotingLogic`.
6. On the first satisfied vote, set the logic to tripped and `RUNNING`, then command
   the linked valve to zero-percent opening.

This is a deterministic software transition. It does not establish detector
independence, diagnostic coverage, common-cause behavior, final-element capacity, or
process-response adequacy.

## Time and escalation

`execute(double)` advances `timeSinceTrip` in seconds only after the logic has tripped
and while override is not active.

`linkToEscalationLogic(ProcessLogic, double)` stores a secondary logic and a delay.
After the delay, `execute(double)` recounts tripped sensors and calls
`activate()` on the secondary logic if the vote remains satisfied. The model does not
verify that the secondary layer is independent or that the delay is suitable.

`setValveClosureTime(double)` stores a target on `HIPPSLogic`; current
`HIPPSLogic` trip behavior commands zero opening immediately. Do not interpret the
stored target as a simulated actuator travel profile. Model the linked valve and process
transient explicitly when closure dynamics matter.

## Override, fault, and bypass behavior

`setOverride(true)` inhibits voting and execution and changes the logic state to
`PAUSED`. Clearing override returns the state to `RUNNING` if already tripped or
`IDLE` otherwise.

A bypassed or faulty `Detector` does not contribute a trip vote. If degraded channels
exceed the internal allowance, `HIPPSLogic` reports `FAILED`. These flags are
scenario inputs; the class does not enforce a project bypass permit, compensating
measure, proof-test workflow, or operating time limit.

Treat override and bypass scenarios as explicit, reviewed cases. Never use these
software switches as plant commands or evidence of operating authorization.

## Reset behavior

`reset()` returns `false` while any detector remains tripped. After all detectors
clear, a successful reset:

- clears the logic trip, elapsed trip time, and escalation flag;
- returns the logic state to `IDLE`; and
- commands the linked valve to 100-percent opening.

The reopening is a simulation convenience. It is not a real reset permissive, operator
authorization, or safe restart sequence. A project model that evaluates restart must
represent process conditions, permissives, actuator state, and approval separately.

`deactivate()` does not reset `HIPPSLogic`, and `isComplete()` remains false because
the object represents continuously available monitoring rather than a finite sequence.

## Evidence to retain

For each scenario, retain at least:

- repository revision, model revision, calculation identity, and time basis;
- detector tag, declared unit, set point, value history, fault state, and bypass state;
- voting pattern, channel order, logic state, trip time, escalation state, and override;
- valve identity, stream connection, requested and calculated opening, and transient
  response;
- protected-system pressure, relief response, inventories, balances, event ordering,
  convergence, and time-step sensitivity;
- project-specific acceptance criteria, uncertainty, reviewer, and approval status.

Use the enabled
[ProcessSafetyOverviewDocumentationTest](../../src/test/java/neqsim/process/safety/ProcessSafetyOverviewDocumentationTest.java)
as source-linked executable evidence for the public `HIPPSLogic` surface. Use
[HIPPSValveTest](../../src/test/java/neqsim/process/equipment/valve/HIPPSValveTest.java)
only for the separate equipment-centric API.

## Qualification boundary

Voting notation and state transitions are not a SIL calculation. Configuration does not
demonstrate SIL achievement. The model does not certify IEC 61508 or IEC 61511
compliance, protection-layer independence, proof-test effectiveness, or relief credit.
Trip thresholds, response times, degradation rules, and acceptance criteria must be
project-specific and require independent functional-safety assessment and accountable
approval.

See [Implementing HIPPS Models](hipps_implementation) and the
[maintained safety quick start](../process/safety/README.md#executable-java-8-quick-start)
for the wider simulation workflow.
