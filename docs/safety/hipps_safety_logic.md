---
title: HIPPS Safety Logic Implementation
description: High Integrity Pressure Protection System (HIPPS) is a Safety Instrumented System (SIS) designed to prevent overpressure in process equipment by rapidly closing isolation valves when pressure exceeds ...
---

# HIPPS Safety Logic Implementation

## Overview

High Integrity Pressure Protection System (HIPPS) is a Safety Instrumented System (SIS) designed to prevent overpressure in process equipment by rapidly closing isolation valves when pressure exceeds safe limits. HIPPS acts as the first line of defense before pressure relief devices (PSVs) or Emergency Shutdown (ESD) systems are activated.

## Key Concepts

### What is HIPPS?

HIPPS is an automated safety system that:
- **Prevents overpressure** by rapidly closing isolation valves (typically <2 seconds)
- **Operates at 90-95% of MAOP** (Maximum Allowable Operating Pressure)
- **Prevents PSV activation**, reducing flaring and environmental impact
- **Provides SIL 2 or SIL 3 protection** per IEC 61508/61511
- **Uses redundant sensors** with voting logic for high reliability

### HIPPS vs ESD

| Feature | HIPPS | ESD |
|---------|-------|-----|
| **Activation Pressure** | 90-95% MAOP | 98-99% MAOP |
| **Primary Function** | Prevent overpressure | Emergency shutdown |
| **Response Time** | <2 seconds | 2-10 seconds |
| **Scope** | Local pressure protection | Full facility shutdown |
| **SIL Level** | SIL 2 or SIL 3 | SIL 1 or SIL 2 |
| **Typical Voting** | 2oo3 | 1oo2 or 2oo3 |

### Defense in Depth

A typical pressure safety system has multiple layers:

1. **Process Control System (PCS)** - Normal control at 80-85% MAOP
2. **HIPPS** - First safety layer at 90-95% MAOP
3. **ESD** - Backup safety layer at 98% MAOP
4. **Pressure Relief Valves (PSVs)** - Last resort at 100%+ MAOP

## Architecture

### Class Structure

```
HIPPSLogic (implements ProcessLogic)
├── VotingLogic (enum: 1oo1, 1oo2, 2oo2, 2oo3, 2oo4, 3oo4)
├── List<Detector> (pressure transmitters)
├── ThrottlingValve (isolation valve)
└── ProcessLogic (escalation logic - typically ESD)
```

### Key Components

#### 1. Pressure Sensors (Detectors)
- **Type**: Pressure transmitters with HIGH_HIGH alarm level
- **Redundancy**: Typically 3 sensors for 2oo3 voting
- **Setpoint**: 90-95% of MAOP
- **Bypass**: Maximum 1 sensor can be bypassed for maintenance

#### 2. Logic Solver
- **Voting Logic**: Evaluates sensor trips (typically 2oo3)
- **Response**: Immediate closure of isolation valve
- **Escalation**: Activates ESD if pressure remains high

#### 3. Final Element
- **Valve Type**: Full-bore ball valve or gate valve
- **Closure Time**: <2 seconds (critical for HIPPS)
- **Fail Position**: Fail-closed (de-energize to close)

## Implementation

### Basic HIPPS Setup

```java
// Create HIPPS with 2oo3 voting (SIL 3)
HIPPSLogic hipps = new HIPPSLogic("HIPPS-101", VotingLogic.TWO_OUT_OF_THREE);

// Add pressure transmitters
double hippsSetpoint = 95.0; // 95 bara (95% of 100 bara MAOP)
Detector pt1 = new Detector("PT-101A", DetectorType.PRESSURE, 
                            AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");
Detector pt2 = new Detector("PT-101B", DetectorType.PRESSURE, 
                            AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");
Detector pt3 = new Detector("PT-101C", DetectorType.PRESSURE, 
                            AlarmLevel.HIGH_HIGH, hippsSetpoint, "bara");

hipps.addPressureSensor(pt1);
hipps.addPressureSensor(pt2);
hipps.addPressureSensor(pt3);

// Set isolation valve
ThrottlingValve isolationValve = new ThrottlingValve("HIPPS-Isolation-Valve", stream);
hipps.setIsolationValve(isolationValve);
```

### HIPPS with ESD Escalation

```java
// Create ESD logic as backup
ESDLogic esdLogic = new ESDLogic("ESD Level 1");
esdLogic.addAction(new TripValveAction(esdValve), 0.0);

// Link HIPPS to escalate to ESD after 5 seconds if pressure remains high
hipps.linkToEscalationLogic(esdLogic, 5.0);
```

### Simulation Loop

```java
// In transient simulation
for (double time = 0; time < totalTime; time += timeStep) {
    // Run process equipment
    stream.run();
    isolationValve.run();
    
    // Get pressure from process
    double pressure = stream.getPressure();
    
    // Update HIPPS (all three sensors)
    hipps.update(pressure, pressure, pressure);
    
    // Execute HIPPS logic (checks for escalation)
    hipps.execute(timeStep);
    
    // Check status
    if (hipps.isTripped()) {
        System.out.println("HIPPS ACTIVATED: Isolation valve closed");
    }
    
    if (hipps.hasEscalated()) {
        System.out.println("ESCALATED TO ESD: HIPPS failed to control pressure");
    }
}
```

## Voting Logic Patterns

### Standard Patterns

| Pattern | Description | Spurious Trip Rate | Safety Integrity | Typical Use |
|---------|-------------|-------------------|------------------|-------------|
| **1oo1** | 1 out of 1 must trip | High | Low | Low criticality |
| **1oo2** | 1 out of 2 must trip | Medium | Medium | Standard applications |
| **2oo2** | 2 out of 2 must trip | Very Low | Low | High availability needed |
| **2oo3** | 2 out of 3 must trip | Low | High | **HIPPS standard (SIL 3)** |
| **2oo4** | 2 out of 4 must trip | Very Low | High | Critical applications |
| **3oo4** | 3 out of 4 must trip | Low | Very High | Ultra-high reliability |

### Why 2oo3 for HIPPS?

The **2oo3** voting pattern is the industry standard for HIPPS because:

1. **High Safety Integrity**: Two sensors must agree before trip (reduces false trips)
2. **Fault Tolerance**: System remains operational if one sensor fails
3. **Maintenance Capability**: One sensor can be bypassed without compromising safety
4. **Balanced Availability**: Low spurious trip rate means fewer production interruptions
5. **SIL 3 Capable**: Meets requirements for high-criticality applications

## Configuration Options

### Bypass Management

```java
// Bypass one sensor for maintenance (max 1 allowed)
Detector pt1 = hipps.getPressureSensor(0);
pt1.setBypass(true);

// Set maximum bypassed sensors (default is 1)
hipps.setMaxBypassedSensors(1);
```

### Valve Closure Time

```java
// Set target closure time (default 2 seconds)
hipps.setValveClosureTime(1.5); // Very fast closure
```

### Manual Override

```java
// Override HIPPS (inhibit trip function)
// WARNING: Requires management approval and risk assessment
hipps.setOverride(true);

// Check override status
if (hipps.isOverridden()) {
    System.out.println("WARNING: HIPPS is overridden");
}
```

### Reset After Trip

```java
// Reset HIPPS after pressure returns to normal
if (hipps.reset()) {
    System.out.println("HIPPS reset successful");
} else {
    System.out.println("Cannot reset - pressure still high");
}
```

## Safety Considerations

### SIL (Safety Integrity Level)

HIPPS typically requires **SIL 2 or SIL 3** per IEC 61511:

- **SIL 2**: 10⁻³ to 10⁻² probability of failure on demand (PFD)
- **SIL 3**: 10⁻⁴ to 10⁻³ probability of failure on demand (PFD)

### Design Requirements

1. **Independent Sensors**: Three independent pressure transmitters
2. **Diverse Measurement**: Consider different sensor technologies
3. **Rapid Response**: Valve closure <2 seconds
4. **Fail-Safe Design**: De-energize to close (fail-closed)
5. **Regular Testing**: Partial stroke testing, full stroke testing
6. **Bypass Constraints**: Maximum 1 sensor bypassed at a time
7. **Escalation**: Backup ESD system if HIPPS fails

### Common Failure Modes

| Failure Mode | Detection | Mitigation |
|--------------|-----------|------------|
| Sensor failure | Self-diagnostics, voting | 2oo3 voting, regular testing |
| Valve failure to close | Position feedback, escalation | ESD backup, proof testing |
| Logic solver failure | Watchdog, self-test | Redundant processors |
| Common cause failure | Design diversity | Different sensor technologies |

## Standards Compliance

### IEC 61511 (Process Industry SIS)

- **Risk Assessment**: SIL determination per layer of protection analysis (LOPA)
- **Design**: Redundancy, voting logic, bypass management
- **Implementation**: Independent validation, functional testing
- **Operation**: Bypass procedures, proof testing schedule
- **Maintenance**: Partial stroke testing, full stroke testing

### IEC 61508 (Functional Safety)

- **Hardware Fault Tolerance**: 2oo3 provides 1-fault tolerance
- **Safe Failure Fraction**: High SFF with diagnostics
- **Systematic Capability**: SC2 or SC3 depending on development process

### ISA-84 / ANSI/ISA-84.00.01

- **SIF Design**: Safety function specification
- **Verification**: Proof testing intervals
- **Validation**: Pre-startup acceptance testing

## Performance Metrics

### Key Performance Indicators

```java
// Trip statistics
double tripTime = hipps.getTimeSinceTrip();
boolean hasEscalated = hipps.hasEscalated();

// Sensor status
int trippedCount = 0;
int bypassedCount = 0;
for (Detector sensor : hipps.getPressureSensors()) {
    if (sensor.isTripped()) trippedCount++;
    if (sensor.isBypassed()) bypassedCount++;
}
```

### Typical Metrics to Track

- **Spurious Trip Rate**: Trips per year (target: <0.1)
- **Response Time**: Time from pressure excursion to valve closure
- **Escalation Rate**: HIPPS failures requiring ESD activation
- **Sensor Availability**: Percentage time all sensors operational
- **Proof Test Interval**: 1-2 years typical

## Example Scenarios

### Scenario 1: Normal HIPPS Trip

```
Pressure: 50 bara → 96 bara (exceeds 95 bara setpoint)
Result: 
  - 3/3 sensors trip
  - 2oo3 voting satisfied
  - Isolation valve closes in <2 seconds
  - Pressure controlled
  - ESD not activated
```

### Scenario 2: Single Sensor Failure

```
Pressure: 50 bara → 96 bara
Sensor Status:
  - PT-101A: TRIPPED
  - PT-101B: TRIPPED
  - PT-101C: FAULTY (not counted)
Result:
  - 2/2 active sensors trip
  - 2oo3 voting satisfied (excludes faulty sensor)
  - HIPPS activates successfully
```

### Scenario 3: HIPPS Failure - ESD Escalation

```
Pressure: 50 bara → 96 bara
HIPPS: Trips and closes valve
Pressure: Remains at 96 bara (valve failure)
Time: 5 seconds elapsed
Result:
  - HIPPS escalation timer expires
  - ESD activated automatically
  - Full shutdown initiated
```

### Scenario 4: Maintenance Bypass

```
Sensor Status:
  - PT-101A: BYPASSED (maintenance)
  - PT-101B: NORMAL
  - PT-101C: NORMAL
Pressure: 50 bara → 96 bara
Result:
  - PT-101B: TRIPPED
  - PT-101C: TRIPPED
  - 2/2 active sensors trip
  - 2oo3 voting satisfied
  - HIPPS activates successfully with 1 sensor bypassed
```

## Best Practices

### Design Phase

1. **SIL Determination**: Perform LOPA to determine required SIL
2. **Voting Selection**: Use 2oo3 for SIL 3, 1oo2 for SIL 2
3. **Setpoint Calculation**: 90-95% MAOP (below ESD, above control)
4. **Valve Sizing**: Full-bore valve for rapid closure
5. **Response Time Analysis**: Model full loop response including valve stroking

### Implementation Phase

1. **Sensor Installation**: Independent tapping points, avoid process deadlegs
2. **Calibration**: Factory calibration with certificates
3. **Logic Configuration**: Test voting logic thoroughly
4. **Valve Testing**: Partial stroke test before commissioning
5. **Integration Testing**: Test escalation to ESD

### Operational Phase

1. **Bypass Procedures**: Management of change (MOC) for bypasses
2. **Proof Testing**: Annual full stroke test, quarterly partial stroke test
3. **Performance Monitoring**: Track spurious trips, response times
4. **Incident Investigation**: Analyze all HIPPS activations
5. **Training**: Regular operator training on HIPPS operation

### Maintenance Phase

1. **Sensor Calibration**: Annual verification
2. **Valve Maintenance**: Lubrication, seal replacement per schedule
3. **Logic Solver Testing**: Self-diagnostics, watchdog verification
4. **Spare Parts**: Critical spares available (sensors, valves, solenoids)

## Future Enhancements

### Planned Features

1. **Demand Rate Tracking**: Calculate PFD based on activation history
2. **Proof Test Integration**: Schedule and track proof test activities
3. **Partial Stroke Testing**: Automated PST for valves
4. **Diagnostic Coverage**: Calculate SFF (Safe Failure Fraction)
5. **LOPA Integration**: Link to risk analysis tools
6. **Performance Dashboards**: Real-time KPI visualization

### Advanced Capabilities

- **Time-based voting**: Require sensors tripped for N seconds
- **Rate-of-change detection**: Trip on rapid pressure rise
- **Pressure prediction**: AI/ML for early warning
- **Multi-stage HIPPS**: Cascading pressure protection
- **Dynamic setpoint adjustment**: Based on operating mode

## References

- **IEC 61511**: Functional safety - Safety instrumented systems for the process industry sector
- **IEC 61508**: Functional safety of electrical/electronic/programmable electronic safety-related systems
- **ISA-84.00.01**: Application of Safety Instrumented Systems for the Process Industries
- **API RP 14C**: Recommended Practice for Analysis, Design, Installation, and Testing of Safety Systems for Offshore Production Facilities
- **NORSOK S-001**: Technical Safety (Norwegian offshore standard)

## See Also

- [Process Logic Framework](../simulation/process_logic_framework) - Base architecture
- [SIS Logic Implementation](sis_logic_implementation) - Fire and gas detection
- [ESD Logic](ESD_BLOWDOWN_SYSTEM) - Emergency shutdown systems
- [HIPPS Summary](HIPPS_SUMMARY) - High-level overview
