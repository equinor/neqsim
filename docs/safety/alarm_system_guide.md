---
title: NeqSim Alarm System Configuration Guide
description: The NeqSim alarm system provides a comprehensive framework for monitoring process variables and managing alarm states throughout the lifecycle of process operations. This guide demonstrates how to con...
---

# NeqSim Alarm System Configuration Guide

## Overview

The NeqSim alarm system provides a comprehensive framework for monitoring process variables and managing alarm states throughout the lifecycle of process operations. This guide demonstrates how to configure and handle alarms in a consistent and easy way.

## Key Components

### 1. AlarmConfig (Builder Pattern)

Configure alarms using the fluent builder pattern:

```java
AlarmConfig pressureAlarmConfig = AlarmConfig.builder()
    .lowLowLimit(10.0)        // LOLO alarm threshold
    .lowLimit(20.0)           // LO alarm threshold
    .highLimit(80.0)          // HI alarm threshold
    .highHighLimit(90.0)      // HIHI alarm threshold
    .deadband(2.0)            // Deadband to prevent chattering
    .delay(3.0)               // Time delay before activation (seconds)
    .unit("bara")             // Engineering unit
    .build();
```

### 2. Alarm Levels

Four standard alarm levels aligned with ISA-18.2:

- **LOLO**: Low-Low (most severe low alarm)
- **LO**: Low alarm
- **HI**: High alarm
- **HIHI**: High-High (most severe high alarm)

### 3. ProcessAlarmManager

Centralized coordinator for all process alarms:

```java
ProcessAlarmManager alarmManager = new ProcessAlarmManager();

// Register measurement devices
alarmManager.register(pressureTransmitter);
alarmManager.register(temperatureTransmitter);
alarmManager.register(flowTransmitter);

// Evaluate alarms during simulation
double measuredValue = transmitter.getMeasuredValue();
List<AlarmEvent> events = alarmManager.evaluateMeasurement(
    transmitter, measuredValue, dt, currentTime);

// Acknowledge all active alarms
List<AlarmEvent> ackEvents = alarmManager.acknowledgeAll(currentTime);

// Get active alarms
List<AlarmStatusSnapshot> activeAlarms = alarmManager.getActiveAlarms();

// Get complete alarm history
List<AlarmEvent> history = alarmManager.getHistory();
```

## Alarm Configuration Examples

### Pressure Alarms

```java
PressureTransmitter pt = new PressureTransmitter("PT-101", stream);

AlarmConfig pressureAlarms = AlarmConfig.builder()
    .highLimit(55.0)          // HI: 55 bara
    .highHighLimit(58.0)      // HIHI: 58 bara
    .deadband(1.0)            // 1 bara deadband
    .delay(2.0)               // 2 second delay
    .unit("bara")
    .build();

pt.setAlarmConfig(pressureAlarms);
alarmManager.register(pt);
```

### Temperature Alarms

```java
TemperatureTransmitter tt = new TemperatureTransmitter("TT-101", stream);

AlarmConfig tempAlarms = AlarmConfig.builder()
    .highLimit(45.0)          // HI: 45°C
    .highHighLimit(60.0)      // HIHI: 60°C
    .deadband(2.0)            // 2°C deadband
    .delay(5.0)               // 5 second delay (slower response)
    .unit("C")
    .build();

tt.setAlarmConfig(tempAlarms);
alarmManager.register(tt);
```

### Flow Alarms (with Low Limits)

```java
VolumeFlowTransmitter ft = new VolumeFlowTransmitter("FT-201", stream);

AlarmConfig flowAlarms = AlarmConfig.builder()
    .lowLowLimit(100.0)       // LOLO: 100 kg/hr
    .lowLimit(500.0)          // LO: 500 kg/hr
    .highLimit(20000.0)       // HI: 20000 kg/hr
    .deadband(50.0)           // 50 kg/hr deadband
    .delay(3.0)               // 3 second delay
    .unit("kg/hr")
    .build();

ft.setAlarmConfig(flowAlarms);
alarmManager.register(ft);
```

### Level Alarms (Full Range)

```java
LevelTransmitter lt = new LevelTransmitter("LT-101", separator);

AlarmConfig levelAlarms = AlarmConfig.builder()
    .lowLowLimit(15.0)        // LOLO: 15%
    .lowLimit(30.0)           // LO: 30%
    .highLimit(75.0)          // HI: 75%
    .highHighLimit(90.0)      // HIHI: 90%
    .deadband(2.0)            // 2% deadband
    .delay(4.0)               // 4 second delay
    .unit("%")
    .build();

lt.setAlarmConfig(levelAlarms);
alarmManager.register(lt);
```

## Alarm Event Handling

### Event Types

Three types of alarm events:

1. **ACTIVATED**: Alarm becomes active
2. **CLEARED**: Alarm returns to normal
3. **ACKNOWLEDGED**: Operator acknowledges the alarm

### Processing Alarm Events

```java
List<AlarmEvent> events = alarmManager.evaluateMeasurement(
    transmitter, measuredValue, dt, time);

for (AlarmEvent event : events) {
    switch (event.getType()) {
        case ACTIVATED:
            System.out.println("⚠ ALARM: " + event.getSource() + 
                              " " + event.getLevel() + 
                              " at " + event.getValue());
            // Trigger operator notification
            // Log to SCADA system
            break;
            
        case CLEARED:
            System.out.println("✓ CLEARED: " + event.getSource() + 
                              " " + event.getLevel());
            // Log clearance
            break;
            
        case ACKNOWLEDGED:
            System.out.println("✋ ACKNOWLEDGED: " + event.getSource());
            // Update alarm display
            break;
    }
}
```

## Alarm Status Monitoring

### Get Active Alarms

```java
List<AlarmStatusSnapshot> activeAlarms = alarmManager.getActiveAlarms();

System.out.println("Active Alarms: " + activeAlarms.size());
for (AlarmStatusSnapshot alarm : activeAlarms) {
    String status = alarm.isAcknowledged() ? "[ACK]" : "[NEW]";
    System.out.println(status + " " + 
                      alarm.getLevel() + " - " + 
                      alarm.getSource() + ": " + 
                      alarm.getValue());
}
```

### Alarm History

```java
List<AlarmEvent> history = alarmManager.getHistory();

// Count events by type
long activations = history.stream()
    .filter(e -> e.getType() == AlarmEventType.ACTIVATED)
    .count();
    
long clearances = history.stream()
    .filter(e -> e.getType() == AlarmEventType.CLEARED)
    .count();
    
long acknowledged = history.stream()
    .filter(e -> e.getType() == AlarmEventType.ACKNOWLEDGED)
    .count();
```

## Integration with Safety Logic

### Alarm-Triggered ESD

Alarms can be integrated with ESD logic to trigger automatic actions:

```java
// Monitor pressure alarms
List<AlarmEvent> events = alarmManager.evaluateMeasurement(
    pressureTransmitter, pressure, dt, time);

// Check for HIHI alarm activation
for (AlarmEvent event : events) {
    if (event.getType() == AlarmEventType.ACTIVATED && 
        event.getLevel() == AlarmLevel.HIHI) {
        // Trigger ESD logic
        esdLogic.activate();
        System.out.println("ESD triggered by HIHI pressure alarm");
    }
}
```

## Best Practices

### 1. Alarm Configuration

- **Use appropriate deadbands**: Prevent alarm chattering (typically 1-5% of range)
- **Set realistic delays**: Balance responsiveness vs. nuisance alarms
  - Fast processes: 1-3 seconds
  - Slow processes: 5-10 seconds
  - Safety critical: 0 seconds (immediate)

### 2. Alarm Priorities

- **HIHI/LOLO**: Immediate action required, potential safety impact
- **HI/LO**: Operator intervention needed, performance degradation
- Use alarm levels consistently across the plant

### 3. Alarm Management

- **Monitor alarm rates**: High alarm rates indicate poor tuning
- **Acknowledge alarms promptly**: Maintain operator awareness
- **Review alarm history**: Identify recurring issues
- **Test alarm systems**: Verify correct operation during commissioning

### 4. Safety Integration

- **Safety-critical alarms**: No delay, minimal deadband
- **Voting systems (2oo3)**: Configure identical alarms on redundant sensors
- **Automatic actions**: Link HIHI/LOLO alarms to safety logic
- **Operator alarms (HI/LO)**: Allow operator intervention before automatic action

## Example Application

See `ProcessLogicWithAlarmsExample.java` for a complete demonstration showing:

- Multi-level alarm configuration for pressure, temperature, flow, and level
- ProcessAlarmManager integration
- Alarm event handling and display
- Operator acknowledgement workflow
- Alarm history reporting
- Integration with ESD and startup logic

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                 ProcessAlarmManager                         │
│  - Centralized alarm coordination                           │
│  - Alarm history tracking                                   │
│  - Active alarm monitoring                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
        ┌────────────┼────────────┬────────────┐
        │            │            │            │
┌───────▼──────┐ ┌──▼──────┐ ┌──▼──────┐ ┌──▼──────┐
│ Pressure TX  │ │ Temp TX │ │ Flow TX │ │Level TX │
├──────────────┤ ├─────────┤ ├─────────┤ ├─────────┤
│ AlarmConfig  │ │AlarmCfg │ │AlarmCfg │ │AlarmCfg │
│ - HI: 55     │ │- HI: 45 │ │- LO:500 │ │- HI: 75 │
│ - HIHI: 58   │ │-HIHI:60 │ │-LOLO:100│ │-HIHI:90 │
│ - Deadband:1 │ │-Dband:2 │ │-HI:20000│ │- LO: 30 │
│ - Delay: 2s  │ │-Delay:5s│ │-Dband:50│ │-LOLO:15 │
└──────────────┘ └─────────┘ └─────────┘ └─────────┘
        │            │            │            │
        └────────────┴────────────┴────────────┘
                     │
        ┌────────────▼────────────────┐
        │     AlarmEvent Stream       │
        │  - ACTIVATED                │
        │  - CLEARED                  │
        │  - ACKNOWLEDGED             │
        └────────────┬────────────────┘
                     │
        ┌────────────▼────────────────┐
        │  Integration Points         │
        │  - ESD Logic Triggers       │
        │  - SCADA Display            │
        │  - Historian Logging        │
        │  - Operator Notifications   │
        └─────────────────────────────┘
```

## Summary

The NeqSim alarm system provides:

✓ **Consistent Configuration**: Builder pattern for all alarm types  
✓ **Flexible Limits**: Support for LOLO, LO, HI, HIHI levels  
✓ **Smart Behavior**: Deadband and delay to prevent nuisance alarms  
✓ **Centralized Management**: ProcessAlarmManager for system-wide coordination  
✓ **Event Tracking**: Complete lifecycle from activation to acknowledgement  
✓ **Safety Integration**: Seamless connection to ESD and control logic  

This framework enables reliable process monitoring with minimal code complexity while maintaining industrial alarm management best practices.
