---
title: "Choke Collapse PSD Protection Scenario"
description: "This document describes a critical safety scenario where an inlet choke valve (throttle valve) suddenly fails open to 100%, causing rapid pressure rise in downstream equipment. A Process Shutdown (PSD..."
---

# Choke Collapse PSD Protection Scenario

## Overview

This document describes a critical safety scenario where an inlet choke valve (throttle valve) suddenly fails open to 100%, causing rapid pressure rise in downstream equipment. A Process Shutdown (PSD) valve monitors the pressure and automatically closes when a High-High (HIHI) alarm is triggered, protecting the system from overpressure.

## Scenario Description

### Normal Operation
- **High pressure feed**: 100 bara at inlet
- **Choke valve**: Operates at 30% opening to control pressure
- **Target downstream pressure**: ~50 bara
- **PSD valve**: 100% open (normally passing flow)
- **HIHI alarm setpoint**: 55 bara

### Failure Event
1. **t = 0.0 s**: Choke valve fails open to 100%
2. **t = 0-3 s**: Pressure rises rapidly from 50 to 56 bara
3. **t = 2.5 s**: Pressure reaches 55 bara, triggering HI alarm
4. **t = 3.0 s**: Pressure exceeds HIHI setpoint, PSD valve trips and closes
5. **t > 3.0 s**: PSD valve fully closed, system isolated and protected

### Protection Response
- **Response time**: 3 seconds from failure to PSD closure
- **Maximum pressure**: 56 bara (contained below safe limits)
- **PSD closure time**: 2 seconds (fast-acting)
- **Trip latching**: PSD remains closed until manual reset

## Implementation

### System Configuration

```java
// High pressure feed at 100 bara
SystemInterface feedGas = new SystemSrkEos(273.15 + 40.0, 100.0);
feedGas.addComponent("methane", 85.0);
feedGas.addComponent("ethane", 8.0);
// ... add other components
feedGas.setMixingRule("classic");

Stream feedStream = new Stream("High Pressure Feed", feedGas);
feedStream.setFlowRate(5000.0, "kg/hr");
feedStream.setPressure(100.0, "bara");

// Choke valve - normally at 30% opening
ThrottlingValve chokeValve = new ThrottlingValve("Inlet Choke Valve", feedStream);
chokeValve.setPercentValveOpening(30.0);
chokeValve.setOutletPressure(50.0);

Stream chokeOutlet = new Stream("Choke Outlet", chokeValve.getOutletStream());

// PSD valve for protection
PSDValve psdValve = new PSDValve("PSD Inlet Protection", chokeOutlet);
psdValve.setPercentValveOpening(100.0);
psdValve.setClosureTime(2.0); // Fast closure

// Pressure transmitter with HIHI alarm
PressureTransmitter pressureTransmitter = new PressureTransmitter(
    "Separator Inlet PT", chokeOutlet);

AlarmConfig alarmConfig = AlarmConfig.builder()
    .highHighLimit(55.0)
    .highLimit(52.0)
    .deadband(0.5)
    .delay(1.0)
    .unit("bara")
    .build();
pressureTransmitter.setAlarmConfig(alarmConfig);

// Link PSD valve to pressure transmitter
psdValve.linkToPressureTransmitter(pressureTransmitter);
```

### Simulating the Failure

```java
// Run initial steady state
feedStream.run();
chokeValve.run();
psdValve.run();

// FAILURE EVENT: Choke valve fails open
chokeValve.setPercentValveOpening(100.0);

// Simulate dynamic response
double timeStep = 0.5; // 0.5 second time steps
for (double time = 0.0; time <= simulationTime; time += timeStep) {
    // Run process
    feedStream.run();
    chokeValve.run();
    chokeOutlet.run();
    
    // Evaluate alarm
    pressureTransmitter.evaluateAlarm(
        chokeOutlet.getPressure("bara"), timeStep, time);
    
    // Run PSD transient behavior
    psdValve.runTransient(timeStep, UUID.randomUUID());
    
    // Check if PSD tripped
    if (psdValve.hasTripped()) {
        System.out.println("PSD valve tripped at " + time + " s");
        break;
    }
}
```

## Test Results

### Choke Collapse Test
```
===== CHOKE COLLAPSE SCENARIO =====
Initial Configuration:
  Feed pressure: 100.0 bara
  Choke opening: 30.0% (normal operation)
  PSD opening: 100.0% (normal operation)
  PSD HIHI setpoint: 55.0 bara

Time (s) | Choke Opening | Pressure (bara) | Alarm State | PSD Opening | PSD Tripped
---------|---------------|-----------------|-------------|-------------|------------
    0.0  |     100.0%    |      50.00      |   NONE      |    100.0%   |    NO
    0.5  |     100.0%    |      51.00      |   NONE      |    100.0%   |    NO
    1.0  |     100.0%    |      52.00      |   NONE      |    100.0%   |    NO
    1.5  |     100.0%    |      53.00      |   HI        |    100.0%   |    NO
    2.0  |     100.0%    |      54.00      |   HI        |    100.0%   |    NO
    2.5  |     100.0%    |      55.00      |   HI        |    100.0%   |    NO
    3.0  |     100.0%    |      56.00      |   HIHI      |      0.0%   |    YES
```

**Results:**
- ✓ Choke failed open at t = 0.0 s
- ✓ PSD valve tripped at t = 3.0 s
- ✓ Maximum pressure: 56.00 bara (safely contained)
- ✓ System protected from overpressure

### Recovery Test
```
===== CHOKE REPAIR AND PSD RESET TEST =====
Step 1: Simulating choke collapse...
  PSD tripped at 56.0 bara

Step 2: Repairing choke valve (returning to 30% opening)...
  Choke repaired and pressure returned to 50 bara

Step 3: Attempting to open PSD valve while still tripped...
  ✓ PSD correctly prevents opening while tripped

Step 4: Resetting PSD valve...
  PSD valve reset complete

Step 5: Opening PSD valve to resume operation...
  ✓ PSD successfully opened to 100.0%

===== RESET TEST SUMMARY =====
✓ Choke collapse triggered PSD trip
✓ Choke repaired (returned to 30% opening)
✓ PSD prevented opening while tripped
✓ PSD reset successful
✓ System ready to resume normal operation
```

## Key Features Demonstrated

### 1. Rapid Failure Detection
- Pressure rise detected within 1.5 seconds
- HI alarm activated at 53 bara
- HIHI alarm activated at 56 bara

### 2. Automatic Protection
- PSD valve automatically trips on HIHI alarm
- Fast closure (2 seconds) minimizes pressure overshoot
- No manual intervention required for trip

### 3. Trip Latching
- PSD valve remains closed after trip
- Cannot be opened while in tripped state
- Requires manual reset before operation can resume

### 4. Recovery Procedure
1. Repair/replace failed choke valve
2. Verify pressure has returned to safe levels
3. Reset PSD valve to clear trip state
4. Manually open PSD valve to resume operation

## Safety Analysis

### Layers of Protection

| Layer | Device | Setpoint | Action | Response Time |
|-------|--------|----------|--------|---------------|
| 1 | HI Alarm | 52 bara | Operator notification | Immediate |
| 2 | HIHI Alarm | 55 bara | Triggers PSD closure | 1 second delay |
| 3 | PSD Valve | On HIHI | Closes to 0% | 2 seconds |

### Effectiveness
- **Pressure containment**: Maximum 56 bara vs. 100 bara feed pressure
- **Response time**: 3 seconds from failure to isolation
- **Protection margin**: 11% overshoot above HIHI setpoint
- **Recovery capability**: System can be safely restarted after repair

## Best Practices

### 1. Alarm Configuration
```java
AlarmConfig alarmConfig = AlarmConfig.builder()
    .highHighLimit(55.0)      // Trip setpoint
    .highLimit(52.0)          // Early warning
    .deadband(0.5)            // Prevent chattering
    .delay(1.0)               // Confirmation time
    .unit("bara")
    .build();
```

### 2. PSD Valve Settings
- **Closure time**: Fast enough to prevent overpressure (2-3 seconds typical)
- **Trip enabled**: Always enabled during normal operation
- **Reset requirement**: Manual reset prevents inadvertent restart

### 3. Testing
- Test PSD trip function regularly
- Simulate failure scenarios in training mode
- Verify alarm setpoints match design basis

## Related Safety Devices

This scenario complements other safety devices in NeqSim:

### Comparison Matrix

| Feature | PSD Valve | Safety Valve (PSV) | Rupture Disk |
|---------|-----------|-------------------|--------------|
| **Activation** | HIHI alarm | Set pressure | Burst pressure |
| **Response** | Fast closure | Opens to relieve | Bursts open |
| **Reset** | Manual | Self-resetting | Requires replacement |
| **Use Case** | Process shutdown | Overpressure relief | Last-resort protection |
| **Typical Setpoint** | 55 bara (HIHI) | 55 bara (set) | 65 bara (burst) |

### Integrated Protection Strategy

For complete system protection, use all three in series:

1. **PSD Valve** (Primary): Isolates on abnormal process conditions
2. **PSV** (Secondary): Relieves pressure if PSD fails
3. **Rupture Disk** (Ultimate): Prevents catastrophic failure

## Example Application

### Production Separator Protection

```java
// High pressure feed from wellhead
Stream wellheadStream = new Stream("Wellhead", highPressureGas);
wellheadStream.setPressure(100.0, "bara");

// Choke valve for flow control
ThrottlingValve chokeValve = new ThrottlingValve("Production Choke", wellheadStream);
chokeValve.setPercentValveOpening(30.0);

// PSD valve for emergency isolation
PSDValve psdValve = new PSDValve("ESD Inlet", chokeValve.getOutletStream());
psdValve.linkToPressureTransmitter(pressureTransmitter);

// Production separator
Separator separator = new Separator("Production Sep", psdValve.getOutletStream());
separator.setInternalDiameter(1.5);
separator.setSeparatorLength(4.0);

// PSV for overpressure relief
SafetyValve psv = new SafetyValve("PSV-101", separator.getGasOutStream());
psv.setSetPressure(55.0, "bara");

// Rupture disk as last resort
RuptureDisk ruptureDisk = new RuptureDisk("RD-101", separator.getGasOutStream());
ruptureDisk.setBurstPressure(65.0, "bara");
```

## References

- [PSD Valve HIHI Trip Documentation](psd_valve_hihi_trip)
- [PSV Dynamic Sizing](psv_dynamic_sizing_example)
- [Process Alarm System](process_control)

## Test Class

Complete test implementation: `neqsim.process.equipment.valve.ChokeCollapsePSDProtectionTest`

Run tests:
```bash
mvn test -Dtest=ChokeCollapsePSDProtectionTest
```
