---
title: "Alarm Configuration and Lifecycle"
description: "Executable NeqSim alarm configuration, evaluation, acknowledgement, clearance, and safety-boundary guidance."
keywords: "alarm, AlarmConfig, ProcessAlarmManager, pressure transmitter, deadband, delay, acknowledgement, process safety"
---

NeqSim alarm classes model threshold evaluation and alarm lifecycle state for simulated measurement devices. Use them to
test alarm configuration, event handling, acknowledgement, shelving, history, and control-system integration in a
process model.

They do not by themselves establish an alarm philosophy, safety-instrumented function, voting architecture, response
time, independence, integrity level, or compliance with a named standard. Those claims require a controlled design
basis, hazard analysis, lifecycle evidence, verification, and accountable approval.

## Core APIs

| Purpose | API |
| --- | --- |
| Configure LOLO, LO, HI, and HIHI thresholds | `AlarmConfig` |
| Retain pending, active, acknowledged, cleared, and shelved state | `AlarmState` |
| Evaluate devices, collect history, and dispatch optional handlers | `ProcessAlarmManager` |
| Describe activation, acknowledgement, and clearance | `AlarmEvent` |
| Read currently active alarm state | `AlarmStatusSnapshot` |
| Attach an alarm to a process measurement | `MeasurementDeviceInterface.setAlarmConfig(...)` |

Thresholds, measured values, and deadband must use the same engineering unit. Delay and evaluation time are simulation
time values; the model does not prove field-device scan time, transport latency, solver timing, or final-element stroke
time.

## Executable pressure-alarm lifecycle

This complete Java 8 example exercises HIHI activation, active-state inspection, acknowledgement, deadband-based
clearance, and immutable history. Assertions are part of the published example and are enabled by its documentation
test.

<!-- alarm-lifecycle-example:start -->
```java
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmEventType;
import neqsim.process.alarm.AlarmLevel;
import neqsim.process.alarm.AlarmStatusSnapshot;
import neqsim.process.alarm.ProcessAlarmManager;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class AlarmLifecycleExample {
  private static final Logger logger = LogManager.getLogger(AlarmLifecycleExample.class);

  private AlarmLifecycleExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("alarm feed", fluid);

    PressureTransmitter transmitter = new PressureTransmitter("PT-101", feed);
    transmitter.setAlarmConfig(AlarmConfig.builder()
        .highLimit(55.0)
        .highHighLimit(58.0)
        .deadband(1.0)
        .delay(0.0)
        .unit("bara")
        .build());

    ProcessAlarmManager alarms = new ProcessAlarmManager();
    alarms.register(transmitter);

    double highPressureBara = transmitter.getMeasuredValue("bara");
    assert Double.isFinite(highPressureBara);
    List<AlarmEvent> activation =
        alarms.evaluateMeasurement(transmitter, highPressureBara, 1.0, 1.0);
    assert activation.size() == 1;
    assert activation.get(0).getType() == AlarmEventType.ACTIVATED;
    assert activation.get(0).getLevel() == AlarmLevel.HIHI;

    List<AlarmStatusSnapshot> active = alarms.getActiveAlarms();
    assert active.size() == 1;
    assert !active.get(0).isAcknowledged();

    List<AlarmEvent> acknowledgement = alarms.acknowledgeAll(2.0);
    assert acknowledgement.size() == 1;
    assert acknowledgement.get(0).getType() == AlarmEventType.ACKNOWLEDGED;
    assert alarms.getActiveAlarms().get(0).isAcknowledged();

    feed.setPressure(50.0, "bara");
    double clearedPressureBara = transmitter.getMeasuredValue("bara");
    List<AlarmEvent> clearance =
        alarms.evaluateMeasurement(transmitter, clearedPressureBara, 1.0, 3.0);
    assert clearance.size() == 1;
    assert clearance.get(0).getType() == AlarmEventType.CLEARED;
    assert alarms.getActiveAlarms().isEmpty();
    assert alarms.getHistory().size() == 3;

    logger.info(
        "Alarm lifecycle verified for {}: activation={}, acknowledgement={}, clearance={}",
        transmitter.getName(),
        highPressureBara,
        acknowledgement.get(0).getTimestamp(),
        clearedPressureBara);
  }
}
```
<!-- alarm-lifecycle-example:end -->

The example's setpoints are synthetic software-test values, not recommended plant settings.

## Lifecycle behavior

- The most severe breached configured limit becomes the candidate alarm.
- `delay` accumulates from supplied simulation timesteps before activation.
- A high alarm clears when the measurement falls to the configured limit minus deadband; a low alarm clears at the
  limit plus deadband.
- Acknowledgement changes the acknowledgement state but does not clear the alarm.
- History records generated activation, acknowledgement, and clearance events.
- Shelving suppresses event generation while the measurement continues to be tracked. Shelving policy, authorization,
  audit, and expiry requirements remain project responsibilities.

## Automatic actions and safeguarding boundary

`ProcessAlarmManager` can dispatch an `AlarmActionHandler`. This is useful for deterministic simulation of
control responses and scenario studies. Do not treat an alarm level or a handler callback as a credited protective
function.

Before representing an automatic safeguard, independently define and verify at least:

1. initiating hazards and required risk reduction;
2. sensing, voting, diagnostics, bypasses, independence, and common-cause assumptions;
3. logic-solver and final-element behavior, including failure modes and response time;
4. proof-test, maintenance, reset, acknowledgement, and override rules;
5. dynamic process response across credible cases; and
6. approval and traceability for the applicable lifecycle and project requirements.

For a review of the repository's test-source integration demonstration and its limitations, see
[Alarm-triggered logic demonstration](alarm_triggered_logic_example.md). For broader safeguarding topics, see the
[Safety Systems documentation](index.md), [Risk and Reliability](../risk/), and
[HAZOP and LOPA to draft SRS handoff](../process/safety/hazop-lopa-srs-handoff.md).
