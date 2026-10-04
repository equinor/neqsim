---
title: HIPPS Modeling Boundary
description: Source-linked guidance for selecting and qualifying NeqSim HIPPS simulation APIs.
---

NeqSim has two HIPPS-oriented APIs. They model voting, trip state, and isolation-valve
response for simulation studies; neither API certifies a safety instrumented function or
establishes credit for overpressure protection.

Use this page to choose the modeling layer, then follow the
[implementation guide](hipps_implementation) and
[safety-logic lifecycle](hipps_safety_logic).

## Choose the modeling layer

| Engineering need | Current API | Evidence boundary |
| --- | --- | --- |
| Couple pressure-transmitter alarm states directly to a stream-connected valve | `HIPPSValve` | Equipment-centric transient model with nested voting enum, valve trip/reset, proof-test timer, partial-stroke state, and diagnostics |
| Evaluate explicit `Detector` values through reusable process logic | `HIPPSLogic`, `Detector`, and `VotingLogic` | Logic-centric voting, isolation action, bypass/fault handling, override/reset state, and optional escalation |
| Combine HIPPS with relief, ESD, blowdown, and transient evidence | [Safety Systems Package](../process/safety/README.md) | Maintained package overview and executable Java 8 quick start |

Do not mix the two voting enums. `HIPPSValve.VotingLogic` belongs to the equipment model.
`neqsim.process.logic.sis.VotingLogic` belongs to `HIPPSLogic`.

## What the models establish

The enabled regressions demonstrate bounded software behavior:

- `HIPPSValveTest` covers configured transmitter counts, voting decisions, transient
  closure state, reset, bypass, partial-stroke state, proof-test timing, and diagnostics.
- `ProcessSafetyOverviewDocumentationTest` protects the current `HIPPSLogic`
  constructor, isolation-valve setter, and pressure-update signature.
- current source shows which inputs are stored, which methods change model state, and
  which values are reported.

These tests do not validate sensor reliability, common-cause assumptions, probability of
failure on demand, final-element capability, process-response adequacy, or a real facility.

## What a project must establish

A project-specific safety lifecycle must separately justify:

- hazard scenarios, protected equipment, pressure source, design pressure, and relief
  philosophy;
- sensor type, range, accuracy, placement, diagnostics, independence, and bypass policy;
- voting architecture, failure data, common-cause treatment, proof-test interval,
  proof-test effectiveness, and systematic capability;
- trip set points, deadband, delays, valve travel, surge effects, and time-step
  sensitivity;
- final-element sizing, fail position, leakage, actuator utilities, response, and
  environmental qualification;
- independence from other protection layers and any relief-capacity credit;
- standards editions, company requirements, verification records, and accountable
  functional-safety approval.

A `setSILRating(int)` value or a SIL label in output is simulation configuration
metadata. Configuration does not demonstrate SIL achievement. NeqSim output is not a
design approval, certification, or substitute for an independent functional-safety
assessment.

## Interpretation boundary

HIPPS can be represented as an isolation layer that may limit a modeled pressure
transient. That result must not be rewritten as a universal claim that HIPPS prevents
overpressure, prevents relief-valve opening, or eliminates flaring. The outcome depends
on the project scenario, model boundary, response times, failure assumptions, and
acceptance criteria.

Use absolute pressure and explicit units at model boundaries. Treat set points, closure
times, escalation delays, proof-test intervals, and stroke fractions as project-specific
inputs. Values embedded in source defaults or tests are software fixtures, not design
recommendations.

## Maintained evidence

- [HIPPSValve source](../../src/main/java/neqsim/process/equipment/valve/HIPPSValve.java)
- [HIPPSLogic source](../../src/main/java/neqsim/process/logic/hipps/HIPPSLogic.java)
- [VotingLogic source](../../src/main/java/neqsim/process/logic/sis/VotingLogic.java)
- [Detector source](../../src/main/java/neqsim/process/logic/sis/Detector.java)
- [HIPPSValve enabled regression](../../src/test/java/neqsim/process/equipment/valve/HIPPSValveTest.java)
- [Safety-overview enabled regression](../../src/test/java/neqsim/process/safety/ProcessSafetyOverviewDocumentationTest.java)
- [Maintained executable safety quick start](../process/safety/README.md#executable-java-8-quick-start)

The executable quick start demonstrates current package integration for safety equipment
and ESD logic. The HIPPS regressions are the executable evidence for the HIPPS-specific
API surfaces. This documentation batch does not execute or qualify a project HIPPS
design.
