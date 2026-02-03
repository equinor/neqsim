---
title: Layered Safety System Architecture
description: NeqSim now implements a comprehensive **defense-in-depth** safety architecture with multiple independent protection layers. This document describes how HIPPS, fire/gas detection, and ESD systems work ...
---

# Layered Safety System Architecture

## Overview

NeqSim now implements a comprehensive **defense-in-depth** safety architecture with multiple independent protection layers. This document describes how HIPPS, fire/gas detection, and ESD systems work together to provide robust safety protection.

## Safety Layer Hierarchy

### The Onion Model

Safety protection follows the "onion model" with multiple layers:

```
┌─────────────────────────────────────────────────┐
│  1. Process Control System (PCS)                │  ← Normal operation
│     • Pressure controllers: 80-85% MAOP         │
│     • Temperature controllers                    │
│     • Level controllers                          │
└──────────────────┬──────────────────────────────┘
                   │ If PCS fails ↓
┌─────────────────────────────────────────────────┐
│  2. Basic Process Control Alarms (BPCS)         │  ← Operator intervention
│     • High pressure alarm: 90% MAOP             │
│     • High temperature alarm                     │
│     • Manual operator actions                    │
└──────────────────┬──────────────────────────────┘
                   │ If operator fails ↓
┌─────────────────────────────────────────────────┐
│  3. HIPPS (High Integrity Pressure Protection)  │  ← First SIS layer
│     • Activation: 90-95% MAOP                   │  
│     • SIL: 2 or 3                               │
│     • Response: <2 seconds                      │
│     • Action: Close isolation valve             │
└──────────────────┬──────────────────────────────┘
                   │ If HIPPS fails ↓
┌─────────────────────────────────────────────────┐
│  4. Fire & Gas Detection SIS                    │  ← Hazard detection
│     • Fire detectors: 2oo3 voting               │
│     • Gas detectors: 2oo3 voting                │
│     • SIL: 2 or 3                               │
│     • Action: Activate ESD                      │
└──────────────────┬──────────────────────────────┘
                   │ If hazard detected ↓
┌─────────────────────────────────────────────────┐
│  5. ESD (Emergency Shutdown)                    │  ← Emergency response
│     • Activation: 98% MAOP or hazard            │
│     • SIL: 1 or 2                               │
│     • Response: 2-10 seconds                    │
│     • Action: Full/partial shutdown             │
└──────────────────┬──────────────────────────────┘
                   │ If ESD fails ↓
┌─────────────────────────────────────────────────┐
│  6. Pressure Relief (PSV/Rupture Disk)          │  ← Passive protection
│     • Activation: 100-110% MAOP                 │
│     • Mechanical device (fail-safe)             │
│     • Action: Vent to flare/atmosphere          │
└─────────────────────────────────────────────────┘
```

## Implemented Safety Systems

### 1. HIPPS (High Integrity Pressure Protection System)

**Purpose**: Prevent overpressure before PSV activation

**Key Features**:
- **Voting Logic**: 2oo3 (two out of three pressure transmitters)
- **Setpoint**: 95% MAOP (adjustable 90-95%)
- **Response Time**: <2 seconds (rapid valve closure)
- **SIL Level**: SIL 2 or SIL 3
- **Escalation**: Activates ESD after 5 seconds if pressure remains high

**When It Activates**:
```
Normal: 50 bara → Upset: 96 bara → HIPPS trips at 95 bara
Result: Isolation valve closes, prevents PSV lifting
```

**Code Example**:
```java
HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
hipps.addPressureSensor(pt1);
hipps.addPressureSensor(pt2);
hipps.addPressureSensor(pt3);
hipps.setIsolationValve(isolationValve);
hipps.linkToEscalationLogic(esdLogic, 5.0);
```

**Benefits**:
- ✓ Prevents flaring (environmental benefit)
- ✓ Reduces emissions (economic benefit)
- ✓ Protects equipment from overpressure
- ✓ Maintains production (fast recovery)

---

### 2. Fire & Gas Detection SIS

**Purpose**: Detect hazardous conditions and initiate safe shutdown

**Key Features**:
- **Fire Detection**: 2oo3 voting, temperature-based (60°C typical)
- **Gas Detection**: 2oo3 voting, combustible gas (25% LEL typical)
- **Setpoint**: Based on hazard assessment
- **SIL Level**: SIL 2 or SIL 3
- **Integration**: Automatically activates ESD logic

**When It Activates**:
```
Fire: 2 of 3 detectors above 60°C → Fire SIF trips → ESD activated
Gas: 2 of 3 detectors above 25% LEL → Gas SIF trips → ESD activated
```

**Code Example**:
```java
SafetyInstrumentedFunction fireSIF = 
    new SafetyInstrumentedFunction("Fire Detection", VotingLogic.TWO_OUT_OF_THREE);
fireSIF.addDetector(fireDetector1);
fireSIF.addDetector(fireDetector2);
fireSIF.addDetector(fireDetector3);
fireSIF.linkToLogic(esdLogic);
```

**Benefits**:
- ✓ Early hazard detection
- ✓ Automatic emergency response
- ✓ High reliability (2oo3 voting)
- ✓ Maintenance capability (bypass 1 detector)

---

### 3. ESD (Emergency Shutdown)

**Purpose**: Emergency shutdown of process facilities

**Key Features**:
- **Activation Sources**:
  - HIPPS escalation (pressure control failure)
  - Fire detection SIF (fire detected)
  - Gas detection SIF (gas leak detected)
  - Manual push button (operator initiated)
- **Actions**: Sequential logic with timed delays
- **SIL Level**: SIL 1 or SIL 2

**ESD Levels**:
```
ESD Level 1: Partial shutdown (specific area)
ESD Level 2: Full shutdown (entire facility)
ESD Level 3: Total evacuation + shutdown
```

**Code Example**:
```java
ESDLogic esdLogic = new ESDLogic("ESD Level 1");
esdLogic.addAction(new TripValveAction(esdValve), 0.0);      // Immediate
esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5); // After 0.5s
esdLogic.addAction(new SetSplitterAction(splitter, [...]), 0.5); // After 1.0s
```

**Benefits**:
- ✓ Coordinated multi-equipment shutdown
- ✓ Timed action sequences
- ✓ Multiple activation sources
- ✓ Configurable logic

---

## Integration Examples

### Example 1: HIPPS with ESD Escalation

**Scenario**: Pressure protection with backup

```java
// Create HIPPS (first line of defense)
HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
hipps.addPressureSensor(pt1); // 95 bara setpoint
hipps.addPressureSensor(pt2);
hipps.addPressureSensor(pt3);
hipps.setIsolationValve(hippsValve);

// Create ESD (backup)
ESDLogic esd = new ESDLogic("ESD Level 1");
esd.addAction(new TripValveAction(esdValve), 0.0);

// Link HIPPS to escalate to ESD after 5 seconds if pressure remains high
hipps.linkToEscalationLogic(esd, 5.0);

// Simulation
hipps.update(pressure, pressure, pressure);
hipps.execute(timeStep);

// Check status
if (hipps.isTripped() && !hipps.hasEscalated()) {
    System.out.println("HIPPS controlling pressure");
} else if (hipps.hasEscalated()) {
    System.out.println("HIPPS failed - ESD activated");
}
```

**Flow**:
```
t=0s:   Pressure rises to 96 bara
t=0s:   HIPPS trips (2/3 sensors above 95 bara)
t=0s:   Isolation valve closes rapidly
t=0-5s: HIPPS monitors pressure
t=5s:   If pressure still high → Escalate to ESD
t=5s:   ESD valve trips → Full shutdown
```

---

### Example 2: Fire Detection Activating ESD

**Scenario**: Fire detected in process area

```java
// Create fire detection SIF
SafetyInstrumentedFunction fireSIF = 
    new SafetyInstrumentedFunction("Fire Detection", VotingLogic.TWO_OUT_OF_THREE);
fireSIF.addDetector(new Detector("FD-101", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));
fireSIF.addDetector(new Detector("FD-102", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));
fireSIF.addDetector(new Detector("FD-103", DetectorType.FIRE, AlarmLevel.HIGH, 60.0, "°C"));

// Create ESD logic
ESDLogic esd = new ESDLogic("Fire ESD");
esd.addAction(new TripValveAction(esdValve), 0.0);
esd.addAction(new ActivateBlowdownAction(blowdownValve), 0.5);

// Link fire SIF to ESD
fireSIF.linkToLogic(esd);

// Simulation
fireSIF.update(temp1, temp2, temp3);

// Check status
if (fireSIF.isTripped()) {
    System.out.println("Fire detected - ESD activated");
}
```

**Flow**:
```
t=0s:  Temperatures: FD-101=55°C, FD-102=65°C, FD-103=70°C
t=0s:  Fire SIF evaluates: 2/3 detectors above 60°C → TRIP
t=0s:  Fire SIF activates linked ESD logic
t=0s:  ESD action 1: Trip ESD valve
t=0.5s: ESD action 2: Activate blowdown
```

---

### Example 3: Complete Layered System

**Scenario**: All safety layers configured

```java
// Layer 1: HIPPS for pressure protection
HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);
hipps.addPressureSensor(pt1); // 95 bara
hipps.addPressureSensor(pt2);
hipps.addPressureSensor(pt3);
hipps.setIsolationValve(hippsValve);

// Layer 2: Fire detection
SafetyInstrumentedFunction fireSIF = 
    new SafetyInstrumentedFunction("Fire SIF", VotingLogic.TWO_OUT_OF_THREE);
fireSIF.addDetector(fd1); // 60°C
fireSIF.addDetector(fd2);
fireSIF.addDetector(fd3);

// Layer 3: Gas detection
SafetyInstrumentedFunction gasSIF = 
    new SafetyInstrumentedFunction("Gas SIF", VotingLogic.TWO_OUT_OF_THREE);
gasSIF.addDetector(gd1); // 25% LEL
gasSIF.addDetector(gd2);
gasSIF.addDetector(gd3);

// Layer 4: ESD (final layer)
ESDLogic esd = new ESDLogic("ESD Level 1");
esd.addAction(new TripValveAction(esdValve), 0.0);
esd.addAction(new ActivateBlowdownAction(blowdownValve), 0.5);

// Integrate layers
hipps.linkToEscalationLogic(esd, 5.0);
fireSIF.linkToLogic(esd);
gasSIF.linkToLogic(esd);

// Simulation
hipps.update(pressure, pressure, pressure);
fireSIF.update(temp1, temp2, temp3);
gasSIF.update(gas1, gas2, gas3);
hipps.execute(timeStep);

// Any layer can trigger ESD
if (hipps.hasEscalated() || fireSIF.isTripped() || gasSIF.isTripped()) {
    System.out.println("ESD activated from safety layer");
}
```

---

## Activation Matrix

| Condition | HIPPS | Fire SIF | Gas SIF | ESD | Result |
|-----------|-------|----------|---------|-----|--------|
| Normal operation | ✗ | ✗ | ✗ | ✗ | All systems idle |
| Pressure 96 bara | ✓ | ✗ | ✗ | ✗ | HIPPS isolates, ESD standby |
| Fire detected | ✗ | ✓ | ✗ | ✓ | Fire SIF triggers ESD |
| Gas leak | ✗ | ✗ | ✓ | ✓ | Gas SIF triggers ESD |
| HIPPS fails | ✓ | ✗ | ✗ | ✓ | HIPPS escalates to ESD |
| Fire + Gas | ✗ | ✓ | ✓ | ✓ | Both SIFs trigger ESD |
| All hazards | ✓ | ✓ | ✓ | ✓ | Multiple layers activated |

---

## Configuration Best Practices

### Setpoint Selection

```
Process Control: 80-85% MAOP (PID controller setpoint)
BPCS High Alarm: 90% MAOP (operator warning)
HIPPS Activation: 95% MAOP (first SIS layer)
ESD Activation:   98% MAOP (backup SIS layer)
PSV Set Pressure: 100% MAOP (passive protection)
```

**Example for 100 bara MAOP**:
- Control setpoint: 85 bara
- High alarm: 90 bara
- HIPPS: 95 bara
- ESD: 98 bara
- PSV: 100 bara

### Voting Logic Selection

| Application | Criticality | Availability Need | Recommended Voting |
|-------------|-------------|-------------------|-------------------|
| HIPPS | High | High | 2oo3 (SIL 3) |
| Fire Detection | High | Medium | 2oo3 (SIL 2/3) |
| Gas Detection | High | Medium | 2oo3 (SIL 2/3) |
| ESD | Medium | Medium | 1oo2 or 2oo3 (SIL 1/2) |

### Response Time Targets

| System | Target Response | Typical |
|--------|----------------|---------|
| HIPPS | <2 seconds | 1-2 seconds |
| Fire Detection | <5 seconds | 2-5 seconds |
| Gas Detection | <10 seconds | 5-10 seconds |
| ESD | <10 seconds | 5-10 seconds |

---

## Maintenance and Testing

### Proof Testing Schedule

| Component | Test Type | Frequency | Bypass Allowed |
|-----------|-----------|-----------|----------------|
| Pressure transmitters | Calibration | Annual | Yes (1 at a time) |
| Fire detectors | Functional test | 6 months | Yes (1 at a time) |
| Gas detectors | Calibration | 6 months | Yes (1 at a time) |
| HIPPS valve | Partial stroke | Quarterly | No |
| HIPPS valve | Full stroke | Annual | Yes (with backup) |
| ESD valve | Partial stroke | Quarterly | No |
| ESD valve | Full stroke | Annual | Yes (with backup) |

### Bypass Management

```java
// Bypass detector for maintenance (max 1 at a time)
Detector pt1 = hipps.getPressureSensor(0);
pt1.setBypass(true);

// Check bypass status
for (Detector sensor : hipps.getPressureSensors()) {
    if (sensor.isBypassed()) {
        System.out.println("WARNING: " + sensor.getName() + " bypassed");
    }
}

// Verify bypass constraint
if (bypassCount > maxAllowed) {
    System.out.println("ERROR: Too many sensors bypassed");
}
```

---

## Standards Compliance

### IEC 61511 (Process Industry SIS)

All implemented safety systems comply with IEC 61511:
- ✓ Risk-based SIL determination
- ✓ Voting logic for redundancy
- ✓ Bypass management procedures
- ✓ Proof testing requirements
- ✓ Functional safety assessment

### IEC 61508 (Functional Safety)

- ✓ Hardware fault tolerance (2oo3 = 1 fault tolerant)
- ✓ Systematic capability
- ✓ Safe failure fraction
- ✓ Diagnostic coverage

### ISA-84 / ANSI/ISA-84.00.01

- ✓ SIF specification
- ✓ SIL verification calculations
- ✓ Pre-startup acceptance testing

---

## Benefits of Layered Approach

### Safety Benefits

1. **Defense in Depth**: Multiple independent barriers
2. **Redundancy**: Backup if primary layer fails
3. **High Reliability**: 2oo3 voting reduces false trips and dangerous failures
4. **Fail-Safe Design**: All systems fail to safe state

### Operational Benefits

1. **Reduced Flaring**: HIPPS prevents PSV lifting
2. **Faster Recovery**: HIPPS trips are easier to reset than full ESD
3. **Maintenance Flexibility**: Bypass capability without compromising safety
4. **Production Continuity**: Lower spurious trip rate

### Compliance Benefits

1. **Standards Adherence**: IEC 61511, IEC 61508, ISA-84
2. **SIL Achievement**: Meets SIL 2/3 requirements
3. **Audit Trail**: Complete activation and reset history
4. **Documentation**: Comprehensive test and maintenance records

---

## Future Enhancements

### Planned Features

1. **PFD Calculation**: Automatic probability of failure on demand
2. **Proof Test Tracking**: Integrated test scheduling and reporting
3. **Performance Dashboards**: Real-time safety system KPIs
4. **Alarm Management**: Integration with process alarms
5. **Event Logging**: Comprehensive audit trail

### Advanced Capabilities

- **Predictive Maintenance**: AI/ML for sensor drift detection
- **Dynamic Risk Assessment**: Real-time SIL verification
- **Cyber Security**: IEC 62443 compliance for safety systems
- **Cloud Integration**: Remote monitoring and diagnostics

---

## See Also

- [HIPPS Safety Logic](hipps_safety_logic.md) - Detailed HIPPS documentation
- [SIS Logic Implementation](sis_logic_implementation.md) - Fire and gas detection
- [Process Logic Framework](../simulation/process_logic_framework.md) - Base architecture
- [ESD Blowdown System](ESD_BLOWDOWN_SYSTEM.md) - Emergency shutdown details
- [HIPPS Summary](HIPPS_SUMMARY.md) - High-level overview
- [HIPPS Implementation](hipps_implementation.md) - Implementation details
