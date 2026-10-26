---
title: "Alarm-triggered Logic Demonstration: Scope and Limitations"
description: "A safe reading guide for the repository's alarm-to-process-logic test-source demonstration."
keywords: "alarm, process logic, HIPPS, ESD, voting, safeguard, simulation limitations"
---

The repository includes an alarm-to-process-logic demonstration at
`src/test/java/neqsim/process/util/example/ProcessLogicAlarmIntegratedExample.java`. It is test-source
software for exploring API interactions and scenario sequencing. It is not a qualified design basis, a reusable
industrial safeguard, or evidence that a protective function meets a required integrity level.

For a smaller, documentation-tested alarm lifecycle example, start with
[Alarm Configuration and Lifecycle](alarm_system_guide.md).

## What the demonstration shows

The test-source program assembles a simulated process, measurement devices, alarm configurations,
`ProcessAlarmManager`, and process-logic objects. Its scenarios illustrate these software interactions:

| Demonstrated interaction | What can be inspected |
| --- | --- |
| Threshold evaluation | Alarm events created from simulated measurements |
| Handler registration | A matching alarm event can invoke an `AlarmActionHandler` |
| Process-logic activation | A handler can activate a modeled logic object |
| Acknowledgement and history | Alarm state and generated events can be collected for review |
| Scenario sequencing | Input conditions can be changed between simulation steps |

The numeric setpoints, delays, deadbands, valve actions, and scenario order are illustrative values embedded in a
test example. They are not recommendations for a plant or project.

## Important voting limitation

The source describes three pressure transmitters in its HIPPS-style scenario, but the manager registers an
independent HIHI handler for each transmitter. Each matching transmitter event can activate the same logic object.
The demonstration therefore does **not** implement or prove two-out-of-three voting at the alarm-manager layer.

Before describing a modeled function as a voting architecture, implement and test explicit vote collection,
coincidence timing, channel health, degraded-mode behavior, reset rules, bypass handling, and common-cause
assumptions. Review the resulting logic independently from the prose that describes it.

## What is not established

The demonstration does not establish:

- field-sensor independence, diagnostics, uncertainty, or failure behavior;
- a certified logic solver, final element, power supply, communications path, or operator interface;
- scan time, transport latency, valve stroke time, or end-to-end response time;
- voting correctness, fault tolerance, safe failure fraction, or common-cause treatment;
- proof-test intervals, maintenance procedures, bypass authorization, reset policy, or audit controls;
- required risk reduction, lifecycle compliance, or fitness for service; or
- correspondence between the simplified simulated process and a validated plant model.

An alarm threshold and an automatic callback are software mechanisms. They must not be counted as independent or
credited safeguards without the project-specific hazard analysis, requirements, architecture, verification, and
approval that support that claim.

## Safe use in engineering studies

Use the demonstration as a starting point for software exploration only. For an engineering study:

1. derive alarm and trip requirements from the approved hazard and operability assessment and risk analysis;
2. separate operator alarms, basic process control actions, and protective functions in the requirements and model;
3. document independence and common-cause assumptions for every claimed protection layer;
4. implement voting and state transitions explicitly, with deterministic unit and integration tests;
5. validate dynamic response using justified process cases and uncertainty assumptions; and
6. preserve traceability from each requirement to configuration, code, test evidence, and review approval.

Run the class from test sources only when you need to inspect the current demonstration:

`./mvnw -Dexec.classpathScope=test -Dexec.mainClass="neqsim.process.util.example.ProcessLogicAlarmIntegratedExample" exec:java`

Execution confirms only the behavior of that source revision in the selected environment. It does not qualify the
modeled safeguards.

## Related guidance

- [Alarm Configuration and Lifecycle](alarm_system_guide.md)
- [Safety Systems documentation](index.md)
- [Dynamic risk and reliability simulation](../risk/)
- [HAZOP and LOPA to draft SRS handoff](../process/safety/hazop-lopa-srs-handoff.md)
