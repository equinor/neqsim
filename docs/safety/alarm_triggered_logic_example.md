---
title: Alarm-Triggered Process Logic Integration Example
description: The example implements a comprehensive 5-layer safety system:
---

# Alarm-Triggered Process Logic Integration Example

## Overview

`ProcessLogicAlarmIntegratedExample.java` demonstrates a complete, production-ready integration of the NeqSim alarm system with process control and safety logic. This example shows how alarms can trigger automatic control actions, safety responses, and emergency shutdown sequences in a layered protection architecture.

## Key Features

### 1. **Layered Safety Architecture with Alarm Integration**

The example implements a comprehensive 5-layer safety system:

```
Layer 1 (Alarms - SIL-0):
  ├─ HI/LO Alarms → Operator notification and manual intervention
  │
Layer 2 (Alarms + Control - SIL-1):
  ├─ HIHI/LOLO Alarms → Automatic control responses (valve throttling, etc.)
  │
Layer 3 (HIPPS - SIL-2):
  ├─ Independent fast-acting pressure protection
  ├─ 2oo3 voting logic on pressure transmitters
  ├─ Triggered by PT-HIPPS HIHI alarms (59 bara)
  │
Layer 4 (ESD - SIL-2):
  ├─ Emergency shutdown system
  ├─ Triggered by PT-ESD-001 HIHI alarm (60 bara) or manual button
  ├─ Full isolation and blowdown sequence
  │
Layer 5 (PSV - Mechanical):
  └─ Pressure safety valve (65 bara set pressure)
```

### 2. **Multi-Variable Alarm Monitoring**

#### Pressure Monitoring (PT-101)
```java
AlarmConfig pressureAlarmConfig = AlarmConfig.builder()
    .highLimit(53.0)          // HI: Operator notification
    .highHighLimit(56.0)      // HIHI: Auto throttle valve
    .deadband(0.5)
    .delay(1.0)
    .unit("bara")
    .build();
```

**Alarm Actions:**
- **HI (53 bara)**: Operator notification only, no automatic action
- **HIHI (56 bara)**: Automatic valve throttling to 50% opening

#### Temperature Monitoring (TT-101)
```java
AlarmConfig temperatureAlarmConfig = AlarmConfig.builder()
    .highLimit(40.0)          // HI: Operator notification
    .highHighLimit(55.0)      // HIHI: Trigger cooling
    .deadband(2.0)
    .delay(3.0)
    .unit("C")
    .build();
```

**Alarm Actions:**
- **HI (40°C)**: Operator notification
- **HIHI (55°C)**: Trigger cooling actions (demonstrated in framework)

#### Flow Monitoring (FT-201)
```java
AlarmConfig flowAlarmConfig = AlarmConfig.builder()
    .lowLimit(100.0)          // LO: Operator notification
    .lowLowLimit(50.0)        // LOLO: Trigger shutdown
    .highLimit(2000.0)        // HI: High flow warning
    .deadband(10.0)
    .delay(5.0)
    .unit("m3/hr")
    .build();
```

**Alarm Actions:**
- **LO (100 m³/hr)**: Operator notification
- **LOLO (50 m³/hr)**: Controlled shutdown sequence

#### Level Monitoring (LT-101)
```java
AlarmConfig levelAlarmConfig = AlarmConfig.builder()
    .lowLowLimit(20.0)        // LOLO: Emergency shutdown
    .lowLimit(30.0)           // LO: Operator notification
    .highLimit(70.0)          // HI: High level warning
    .highHighLimit(85.0)      // HIHI: Critical high level
    .deadband(2.0)
    .delay(2.0)
    .unit("%")
    .build();
```

**Alarm Actions:**
- **LOLO (20%)**: Emergency shutdown to prevent dry running
- **LO (30%)**: Operator notification

### 3. **Safety-Critical Alarm Configuration**

#### HIPPS Protection (PT-HIPPS-1/2/3)
```java
AlarmConfig hippsAlarmConfig = AlarmConfig.builder()
    .highHighLimit(59.0)      // Immediate HIPPS closure
    .deadband(0.2)            // Minimal deadband
    .delay(0.0)               // No delay - safety critical
    .unit("bara")
    .build();
```

**2oo3 Voting Logic:**
- Three independent pressure transmitters
- HIPPS activates when 2 out of 3 transmitters exceed HIHI limit
- No time delay for immediate protection

#### ESD Trigger (PT-ESD-001)
```java
AlarmConfig esdAlarmConfig = AlarmConfig.builder()
    .highHighLimit(60.0)      // Full ESD sequence
    .deadband(0.5)
    .delay(0.0)               // Immediate ESD trigger
    .unit("bara")
    .build();
```

## Demonstration Scenarios

The example runs six comprehensive scenarios demonstrating alarm-triggered logic:

### Scenario 1: Normal Operation
- System startup with alarm monitoring
- All parameters within normal range
- No alarms active

### Scenario 2: HI Alarm - Operator Notification
- Feed pressure increased to 54 bara (above HI limit of 53 bara)
- PT-101 HI alarm activated
- **Response**: Operator notification only, no automatic action
- Demonstrates Layer 1 protection

### Scenario 3: HIHI Alarm - Automatic Control
- Feed pressure increased to 57 bara (above HIHI limit of 56 bara)
- PT-101 HIHI alarm activated
- **Response**: Automatic valve throttling to 50% opening
- System pressure reduced through control action
- Alarm acknowledged after action
- Demonstrates Layer 2 protection

### Scenario 4: HIPPS Activation
- Feed pressure increased to 60 bara (HIPPS activation level)
- PT-HIPPS HIHI alarms triggered (2oo3 voting)
- **Response**: Immediate HIPPS valve closure (3 second stroke time)
- Demonstrates Layer 3 protection (SIL-2)

### Scenario 5: ESD Triggered by Alarm
- Feed pressure increased to 61 bara (ESD activation level)
- PT-ESD-001 HIHI alarm activated
- **Response**: Full ESD sequence:
  1. Close inlet isolation valves
  2. Route gas to blowdown system
  3. Depressurize to flare
  4. Switch separator to transient mode
- Demonstrates Layer 4 protection (SIL-2)

### Scenario 6: Low Level Emergency Shutdown
- Separator level drops to 20% (LOLO limit)
- LT-101 LOLO alarm activated
- **Response**: Emergency shutdown to prevent dry running
- Demonstrates process-specific alarm triggers

## Code Structure

### Main Components

```java
// 1. Build process system
ProcessSystem processSystem = buildProcessSystem();

// 2. Create alarm manager
ProcessAlarmManager alarmManager = new ProcessAlarmManager();

// 3. Setup instrumentation with alarms
InstrumentationSetup instruments = 
    setupInstrumentationWithAlarms(processSystem, alarmManager);

// 4. Setup process logic
ProcessLogicSetup logicSetup = setupProcessLogic(processSystem, instruments);

// 5. Run scenarios
runAlarmTriggeredScenarios(runner, alarmManager, instruments, 
                          logicSetup, processSystem);
```

### Alarm Evaluation Loop

```java
private static List<AlarmEvent> evaluateAndDisplayAlarms(
    ProcessAlarmManager alarmManager,
    InstrumentationSetup instruments, 
    ProcessSystem system, 
    double dt) {
    
    List<AlarmEvent> allEvents = new ArrayList<>();
    
    // Run process to get current values
    system.run();
    
    // Evaluate each measurement device
    double sepPressure = instruments.separatorPT.getMeasuredValue();
    allEvents.addAll(alarmManager.evaluateMeasurement(
        instruments.separatorPT, sepPressure, dt, simulationTime));
    
    // ... evaluate other transmitters ...
    
    return allEvents;
}
```

### Alarm-Triggered Control Actions

```java
private static void handlePressureHIHIAlarm(List<AlarmEvent> events, 
                                           ProcessSystem system,
                                           ProcessAlarmManager alarmManager) {
    
    for (AlarmEvent event : events) {
        if (event.getType() == AlarmEventType.ACTIVATED && 
            event.getLevel() == AlarmLevel.HIHI &&
            event.getSource().equals("PT-101")) {
            
            // Automatic control response
            ControlValve inletValve = 
                (ControlValve) system.getUnit("Inlet Control Valve");
            inletValve.setPercentValveOpening(50.0);
            
            // Run system with new valve position
            system.run();
            
            // Acknowledge alarm after action
            alarmManager.acknowledgeAll(simulationTime);
        }
    }
}
```

### Alarm-Triggered Safety Logic

```java
private static void handleHIPPSAlarm(List<AlarmEvent> events, 
                                    ESDLogic hippsLogic,
                                    ProcessAlarmManager alarmManager) {
    
    for (AlarmEvent event : events) {
        if (event.getType() == AlarmEventType.ACTIVATED && 
            event.getLevel() == AlarmLevel.HIHI &&
            event.getSource().startsWith("PT-HIPPS")) {
            
            // Activate HIPPS logic
            hippsLogic.activate();
            
            alarmManager.acknowledgeAll(simulationTime);
            break; // Only need one HIPPS transmitter to trigger
        }
    }
}
```

## Output Reports

The example generates comprehensive reports:

### 1. Alarm Status Display
Shows currently active alarms with acknowledgement status:
```
┌─────────────────────────────────────────────────────────┐
│ ALARM STATUS: After HIHI Alarm + Auto Control          │
├─────────────────────────────────────────────────────────┤
│ Active Alarms: 1                                        │
├─────────────────────────────────────────────────────────┤
│ [ACK] HIHI - PT-101        : 57.00                     │
└─────────────────────────────────────────────────────────┘
```

### 2. Alarm History Report
Shows all alarm events with timestamps:
```
╔════════════════════════════════════════════════════════════════╗
║                    ALARM HISTORY REPORT                        ║
╠════════════════════════════════════════════════════════════════╣
║  Total Events: 12                                              ║
╠════════════════════════════════════════════════════════════════╣
║  Recent Events (last 10):                                      ║
║  ⚠ 30.0s ACTIVATED  PT-101          HI   53.50                ║
║  ⚠ 35.0s ACTIVATED  PT-101          HIHI 57.00                ║
║  ✋ 35.5s ACKNOWLEDGED PT-101       HIHI 57.00                ║
║  ...                                                           ║
╚════════════════════════════════════════════════════════════════╝
```

### 3. Alarm Statistics
Aggregated statistics by type and level:
```
╔════════════════════════════════════════════════════════════════╗
║                    ALARM STATISTICS                            ║
╠════════════════════════════════════════════════════════════════╣
║  Total Activations:     8                                      ║
║  Total Clearances:      3                                      ║
║  Total Acknowledgements: 5                                     ║
║                                                                ║
║  By Level:                                                     ║
║    HIHI (Critical High): 4                                     ║
║    HI (High):            2                                     ║
║    LO (Low):             1                                     ║
║    LOLO (Critical Low):  1                                     ║
╚════════════════════════════════════════════════════════════════╝
```

## Integration Patterns

### Pattern 1: Alarm-Triggered Control Adjustment
```java
// Monitor for HIHI alarm
if (alarm.getLevel() == AlarmLevel.HIHI) {
    // Implement automatic control response
    valve.setPercentValveOpening(safeValue);
    system.run();
    alarmManager.acknowledgeAll(time);
}
```

### Pattern 2: Alarm-Triggered Safety Logic
```java
// Monitor for safety-critical alarm
if (alarm.getLevel() == AlarmLevel.HIHI && 
    alarm.getSource().equals("PT-ESD-001")) {
    // Activate safety logic
    esdLogic.activate();
}
```

### Pattern 3: Alarm Acknowledgement Workflow
```java
// Evaluate alarms
List<AlarmEvent> events = evaluateAlarms();

// Process events
for (AlarmEvent event : events) {
    if (event.getType() == AlarmEventType.ACTIVATED) {
        // Log alarm activation
        logger.logAlarm(event);
        
        // Notify operator
        operatorPanel.displayAlarm(event);
    }
}

// Acknowledge after operator review or automatic action
alarmManager.acknowledgeAll(currentTime);
```

## Best Practices Demonstrated

1. **Layered Protection**: Multiple independent protection layers from alarms to mechanical safety devices

2. **Appropriate Delays**: 
   - Safety-critical alarms: 0 seconds (immediate)
   - Process alarms: 1-5 seconds (avoid nuisance trips)

3. **Deadband Configuration**:
   - Safety alarms: Minimal (0.2-0.5)
   - Process alarms: Moderate (1-2% of range)

4. **Alarm Actions**:
   - HI/LO: Operator notification
   - HIHI/LOLO: Automatic control or shutdown

5. **Acknowledgement**:
   - Acknowledge after automatic actions
   - Track acknowledgement status

6. **Comprehensive Logging**:
   - All alarm events recorded
   - Statistics tracked by type and level
   - History available for analysis

## Running the Example

```bash
# Compile
mvn compile

# Run
mvn exec:java -Dexec.mainClass="neqsim.process.util.example.ProcessLogicAlarmIntegratedExample"
```

## Key Takeaways

✅ **Consistent Framework**: All alarms configured using the same AlarmConfig builder pattern

✅ **Flexible Triggering**: Alarms can trigger operator notifications, control actions, or safety logic

✅ **Centralized Management**: ProcessAlarmManager coordinates all process alarms

✅ **Safety Integration**: Seamless connection between alarms and SIL-rated safety systems

✅ **Production-Ready**: Complete with logging, statistics, and acknowledgement workflows

✅ **ISA-18.2 Aligned**: Four standard alarm levels (LOLO, LO, HI, HIHI)

This example provides a complete template for implementing alarm-triggered process control and safety logic in industrial applications using NeqSim.
