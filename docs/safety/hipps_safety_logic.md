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

A vote uses the current scan. Two high readings on different channels at different
times cannot trip a 2oo3 solver. Individual detector alarm histories and the
solver trip remain latched separately. Each distinct healthy detector contributes
at most one vote. Missing, nonfinite, bypassed and faulty readings reduce voting
capacity. An unavailable voting quorum gives a `FAILED` diagnostic.
`setTripOnLostVotingCapacity(true)` also commands isolation on quorum loss.
Choose this response explicitly for a project's degraded-mode policy.

## Time and escalation

Link the primary valve with `setIsolationValve(ThrottlingValve)` and additional
valves with `addIsolationValve(ThrottlingValve)`. Material streams or an explicit
hydraulic boundary define the series connection. `setValveClosureTime(double)`
configures the common stroke duration. Set an individual valve's stroke after
linking it to represent unequal actuators. A transient HIPPS valve latches a
zero target. Its normal actuator step advances actual position; steady equipment
uses the instantaneous position. A generic throttling valve receives the target
and requires separate coordination of any competing controller commands.

`linkToEscalationLogic(ProcessLogic, double)` commands a secondary sequence once
the current pressure vote stays high continuously for the configured delay.
Call `update` at the start of each pressure scan, advance the plant and actuators
over the interval, and call `execute(dt)` once for that interval. Clearing the vote
restarts the escalation timer. Trapped gas can sustain a high pressure after the
inlet seats, so record both valve positions and protected pressure when
interpreting escalation.

## Override, fault, and bypass behavior

Logic override inhibits new logic trips and escalation. Existing HIPPS valve
latches continue to demand closure. A trip interrupts an active partial-stroke
test. Model plant-specific override authorization, time limits and compensating
measures in the operating procedure.

## Reset behavior

Reset requires a full group of healthy, clear readings and cleared local valve
pressure alarms. It releases the latch and leaves the isolation demand at zero.
Reopening requires a separate command. On restart from old serialized logic,
reconnect the pressure channels omitted by the old storage format.

`deactivate()` leaves the latch intact. `isComplete()` stays false for this
continuously monitored function.

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
