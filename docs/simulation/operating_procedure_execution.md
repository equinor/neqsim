---
title: Executable operating procedures
description: Run revisioned startup, shutdown, recovery and restart steps against native NeqSim equipment with measured completion and deterministic evidence.
---

# Executable operating procedures

`OperatingProcedure` turns an approved sequence into executable simulation logic without replacing
native process equipment. Each step separates the requested command from measured completion, so a
slow or stuck actuator cannot be credited merely because its command was issued.

## Contract

Every procedure declares a stable ID, revision and name. Procedure-level and step-level
prerequisites use existing `LogicCondition` implementations. A step also declares:

- automatic or operator command ownership;
- an existing `LogicAction`;
- a measured completion condition;
- a finite timeout; and
- an optional recovery action.

`SetValveTargetAction` changes `ThrottlingValve.getTargetPercentValveOpening()` and leaves measured
position to the valve travel model. Rate limit, first-order lag, deadband, stiction and hysteresis
therefore remain active. `OperatingProcedureRunner` advances the caller-supplied `ProcessSystem`
with deterministic physical-step identities and returns timestamped JSON evidence.

```java
valve.setTravelModel(ValveTravelModel.LINEAR_RATE_LIMIT);
valve.setTravelTime(4.0);

OperatingProcedure startup = new OperatingProcedure(
    "OPS-START-001", "A", "Open inlet isolation");
startup.addPrerequisite(new PressureCondition(feed, 10.0, ">"));
startup.addOperatorStep(
    "OPEN-SDV",
    null,
    new SetValveTargetAction(valve, 100.0, 0.5),
    new ValvePositionCondition(valve, ">=", 99.5, 0.5),
    8.0,
    new SetValveTargetAction(valve, 0.0, 0.5));

startup.activate();
startup.execute(0.0);
startup.authorizeOperatorStep("OPEN-SDV");
```

For automatic procedures, `OperatingProcedureRunner.run(...)` performs the activation and scan
loop. Operator-owned procedures expose `authorizeOperatorStep(...)` so the caller can insert an
operator action at a controlled scan. Authorization is accepted only for the current active
operator-owned step; pre-authorization and a mismatched step ID fail explicitly. The execution
record retains activation, readiness, step entry, operator wait/authorization, command, measured
completion, recovery, first-out and terminal events with seconds, actual value and expected value.

## Quantitative regression

The public regression uses a 20 bara methane/ethane feed, 10 bara downstream boundary and a native
control valve with a four-second linear full stroke. A 0.5 s procedure scan reaches 100% measured
position at 4.0 s; repeating at a 0.25 s scan preserves that completion time and final position.
Repeating the 0.5 s procedure against an independently rebuilt starting state produces
byte-identical JSON evidence. A 150 percentage-point synthetic stiction band holds the stem at 0%;
the step times out at 2.0 s, records `OPEN-SDV` as first-out and issues its configured close
recovery. An operator-owned restart without authorization records the wait, issues no open command
and times out at 2.0 s. With authorization at the initial scan, the same operator-owned step issues
its command once and confirms the 100% measured position at 4.0 s.

These are deterministic synthetic commissioning tests. They do not establish an approved
procedure, operator competence, alarm rationalization, SIS independence, SIL, SRS, FAT/SAT,
construction approval or operating approval. Project procedures must supply reviewed prerequisites,
timeouts, valve data, recovery actions, command ownership and acceptance limits. A caller seeking
restart must restore or reconstruct the required process snapshot and separately satisfy reset and
restart permissives; the runner does not invent a safe state.

