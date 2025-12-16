# HIPPS (High Integrity Pressure Protection System) Implementation in NeqSim

## Overview

HIPPS is a Safety Instrumented System (SIS) designed to prevent overpressure by shutting down the source of pressure rather than relieving it through pressure safety valves (PSVs) or rupture disks. This document describes the HIPPS implementation in NeqSim and provides guidance for safety simulations.

## What is HIPPS?

**High Integrity Pressure Protection System (HIPPS)** is an automated safety system that:
- **Prevents overpressure** before it occurs (vs. PSV which relieves after reaching set pressure)
- **Eliminates or reduces flaring** and emissions during upset conditions
- **Protects downstream equipment** rated below upstream pressure
- Provides **SIL-rated protection** (typically SIL 2 or SIL 3)

### HIPPS vs. PSV Comparison

| Aspect | HIPPS | PSV (Pressure Safety Valve) |
|--------|-------|------------------------------|
| **Action** | Stops flow (isolation) | Relieves pressure (venting) |
| **Trip Point** | Below MAWP (e.g., 90%) | At or above MAWP |
| **Environmental Impact** | Prevents flaring | Releases to flare |
| **Safety Rating** | SIL 2 or SIL 3 | Mechanical (non-SIL) |
| **Testing** | Partial stroke, proof tests | Periodic inspection |
| **Response Time** | 2-5 seconds typical | Instantaneous (spring-loaded) |
| **Redundancy** | Multiple transmitters, voting logic | Single device |
| **Failure Mode** | Fail-safe (close) or diagnosed | Fail-safe (open) |
| **Cost** | Higher initial cost | Lower initial cost |
| **Application** | Subsea, closed systems | General overpressure protection |

## Implementation Components

### 1. HIPPSValve Class

**Location:** `src/main/java/neqsim/process/equipment/valve/HIPPSValve.java`

**Key Features:**
- **Redundant pressure transmitters** with voting logic
- **Multiple voting schemes**: 1oo1, 1oo2, 2oo2, 2oo3, 2oo4
- **Fast closure**: Configurable closure time (typical: 2-5 seconds)
- **SIL rating**: SIL 1, 2, or 3 configuration
- **Partial stroke testing**: Required for SIL validation
- **Proof test tracking**: Monitors test intervals
- **Diagnostic monitoring**: Trip history, spurious trip tracking
- **Bypass mode**: For maintenance or testing

### 2. Voting Logic

HIPPS uses redundant pressure transmitters with voting logic to prevent spurious trips while maintaining safety:

#### 1oo1 (1 out of 1)
- Single transmitter
- Simplest configuration
- Used for SIL 1 applications
- Higher spurious trip rate
- Lower availability

#### 1oo2 (1 out of 2)
- Any one of two transmitters trips
- High availability (continues operating if one transmitter fails)
- Higher spurious trip rate
- Good for critical operations that can't tolerate downtime

#### 2oo2 (2 out of 2)
- Both transmitters must trip
- Low spurious trip rate
- Lower availability (system fails if one transmitter fails)
- Used where spurious trips are very costly

#### 2oo3 (2 out of 3) - **RECOMMENDED**
- Any two of three transmitters trip
- **Balanced** approach
- Typical for **SIL 2 and SIL 3** applications
- Tolerates one transmitter failure
- Low spurious trip rate
- **Industry standard** for HIPPS

#### 2oo4 (2 out of 4)
- Any two of four transmitters trip
- Highest availability
- More complex and expensive
- Used in very critical applications

## Usage Examples

### Example 1: Basic HIPPS Configuration (2oo3 Voting)

```java
// Create high-pressure feed stream
SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
fluid.addComponent("methane", 85.0);
fluid.addComponent("ethane", 10.0);
fluid.addComponent("propane", 5.0);
fluid.setMixingRule("classic");
fluid.createDatabase(true);

Stream feedStream = new Stream("HP Feed", fluid);
feedStream.setFlowRate(20000.0, "kg/hr");
feedStream.setPressure(100.0, "bara");
feedStream.setTemperature(40.0, "C");
feedStream.run();

// Create three redundant pressure transmitters
PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

// Configure HIHI alarm at 90 bara (below 100 bara MAWP)
AlarmConfig hippsAlarm = AlarmConfig.builder()
    .highHighLimit(90.0)  // HIPPS trips at 90% of MAWP
    .deadband(2.0)
    .delay(0.5)          // 500ms confirmation delay
    .unit("bara")
    .build();

PT1.setAlarmConfig(hippsAlarm);
PT2.setAlarmConfig(hippsAlarm);
PT3.setAlarmConfig(hippsAlarm);

// Create HIPPS valve with 2oo3 voting (SIL 3)
HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);
hippsValve.addPressureTransmitter(PT1);
hippsValve.addPressureTransmitter(PT2);
hippsValve.addPressureTransmitter(PT3);
hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);
hippsValve.setClosureTime(3.0); // 3 second SIL-rated actuator
hippsValve.setSILRating(3);
hippsValve.setProofTestInterval(8760.0); // Annual proof test

// Add to process system
ProcessSystem process = new ProcessSystem();
process.add(hippsValve);
```

### Example 2: Dynamic Simulation with HIPPS

```java
// Transient simulation with pressure ramp
double timeStep = 0.5; // seconds
double totalTime = 30.0;

for (double time = 0; time <= totalTime; time += timeStep) {
    // Update process conditions (e.g., blocked outlet scenario)
    if (time > 5.0) {
        // Simulate pressure buildup
        double pressure = 80.0 + (time - 5.0) * 2.0; // 2 bara/sec ramp
        feedStream.setPressure(pressure, "bara");
        feedStream.run();
    }
    
    // Evaluate alarms on all transmitters
    double currentPressure = feedStream.getPressure("bara");
    PT1.evaluateAlarm(currentPressure, timeStep, time);
    PT2.evaluateAlarm(currentPressure, timeStep, time);
    PT3.evaluateAlarm(currentPressure, timeStep, time);
    
    // Run HIPPS transient calculation
    hippsValve.runTransient(timeStep, UUID.randomUUID());
    
    // Check HIPPS status
    if (hippsValve.hasTripped()) {
        System.out.println("HIPPS activated at t=" + time + "s, P=" + currentPressure + " bara");
        System.out.println("Active transmitters: " + hippsValve.getActiveTransmitterCount());
        break;
    }
    
    // Continue processing downstream equipment...
}
```

### Example 3: HIPPS with PSV Backup

```java
// HIPPS provides primary protection, PSV is backup

// Create HIPPS (trips at 90 bara)
HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);
// ... configure as in Example 1 ...

// Create PSV as backup (set at 100 bara MAWP)
SafetyValve psv = new SafetyValve("PSV-001", feedStream);
psv.setPressureSpec(100.0); // PSV set pressure at MAWP
psv.setFullOpenPressure(110.0); // Full open at 10% overpressure
psv.setBlowdown(7.0); // 7% blowdown

// In normal operation:
// 1. Pressure rises due to upset condition
// 2. HIPPS trips at 90 bara (prevents further pressure rise)
// 3. PSV never lifts because HIPPS stopped the overpressure
// 4. No flaring or emissions

// In HIPPS failure scenario:
// 1. Pressure continues to rise
// 2. PSV lifts at 100 bara (backup protection)
// 3. System is protected, but gas is flared
```

### Example 4: Transmitter Failure Scenario

```java
// Simulate a transmitter failure during operation

HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);
hippsValve.setVotingLogic(HIPPSValve.VotingLogic.TWO_OUT_OF_THREE);

PressureTransmitter PT1 = new PressureTransmitter("PT-101A", feedStream);
PressureTransmitter PT2 = new PressureTransmitter("PT-101B", feedStream);
PressureTransmitter PT3 = new PressureTransmitter("PT-101C", feedStream);

hippsValve.addPressureTransmitter(PT1);
hippsValve.addPressureTransmitter(PT2);
hippsValve.addPressureTransmitter(PT3);

// During operation, PT2 fails (diagnosed and bypassed)
hippsValve.removePressureTransmitter(PT2);

// Change voting to 1oo2 for continued operation
hippsValve.setVotingLogic(HIPPSValve.VotingLogic.ONE_OUT_OF_TWO);

// System continues operating with degraded redundancy
// Schedule maintenance to repair PT2 and restore 2oo3 voting
```

### Example 5: Partial Stroke Testing

```java
// Perform partial stroke test (required for SIL validation)

HIPPSValve hippsValve = new HIPPSValve("HIPPS-XV-001", feedStream);

// During normal operation, perform 15% stroke test
hippsValve.performPartialStrokeTest(0.15); // 15% stroke

// Simulation of partial stroke test
double testDuration = 5.0; // seconds
double timeStep = 0.1;

for (double time = 0; time < testDuration; time += timeStep) {
    hippsValve.runTransient(timeStep, UUID.randomUUID());
    
    if (hippsValve.isPartialStrokeTestActive()) {
        System.out.println("Test in progress: Opening = " + 
            hippsValve.getPercentValveOpening() + "%");
    }
}

// Valve returns to 100% open after test
// Test validates valve can move (demonstrates functional operation)
```

## Safety Simulation Best Practices

### 1. Response Time Modeling

HIPPS response time includes:
- **Transmitter response**: Typically 100-500 ms
- **Logic solver**: 10-100 ms
- **Valve closure time**: 2-5 seconds (dominant factor)

```java
// Model realistic closure time
hippsValve.setClosureTime(3.0); // 3 seconds typical for SIL-rated ball valve

// Account for alarm confirmation delay
AlarmConfig alarm = AlarmConfig.builder()
    .highHighLimit(90.0)
    .delay(0.5) // 500 ms confirmation delay
    .build();
```

### 2. Set Point Selection

HIPPS trip point should be:
- **Below MAWP**: Typically 90-95% of MAWP
- **Above normal operating pressure**: Avoid spurious trips
- **Account for pressure surge**: Consider water hammer effects

```java
// Example: MAWP = 100 bara
// Normal operation = 70-80 bara
// HIPPS trip = 90 bara (10% margin below MAWP)
// PSV set = 100 bara (at MAWP)

double mawp = 100.0;
double hippsTrip = mawp * 0.90; // 90% of MAWP
```

### 3. Transmitter Placement

- Install transmitters **upstream** of HIPPS valve
- Ensure transmitters measure **same pressure point**
- Consider **impulse line** dynamics in fast transients
- **Protect** from process effects (vibration, temperature)

### 4. Failure Mode Analysis

Model both success and failure scenarios:

```java
// Scenario 1: HIPPS successful operation
// - Transmitters detect overpressure
// - Voting logic triggers
// - Valve closes in 3 seconds
// - Pressure stabilizes below MAWP

// Scenario 2: HIPPS spurious trip
hippsValve.recordSpuriousTrip();
// - Production lost
// - Economic impact
// - Need to restart system

// Scenario 3: HIPPS failure to close
hippsValve.setTripEnabled(false); // Simulate failure
// - PSV must provide protection
// - Flaring occurs
// - Verify PSV capacity adequate
```

### 5. Integration with Process Control

```java
// HIPPS should be independent of process control system
// But can provide signals for:
// - Alarm annunciation
// - Automatic process shutdown
// - Data logging

if (hippsValve.hasTripped()) {
    // Trigger alarms
    // Shut down feed pumps/compressors
    // Log event for investigation
    System.out.println(hippsValve.getDiagnostics());
}
```

### 6. Proof Test Interval

```java
// Track proof test intervals for SIL validation
hippsValve.setProofTestInterval(8760.0); // Annual proof test

// During operation
if (hippsValve.isProofTestDue()) {
    // Schedule maintenance
    // Perform full functional test
    // Document results
    hippsValve.performProofTest(); // Reset timer
}
```

## Typical Applications

### 1. Subsea Pipeline Protection

```
[Platform] --100 bara--> [Subsea Pipeline] ---> [HIPPS] --50 bara--> [Receiving Platform]
```

- Platform can generate 100 bara
- Receiving equipment rated for 50 bara
- HIPPS protects receiving platform
- Prevents costly subsea PSV installation

### 2. Blocked Outlet Scenario

```
[Compressor] --> [HIPPS] --> [Valve] --> [Process]
```

- Downstream valve accidentally closes
- Pressure builds rapidly
- HIPPS isolates compressor before overpressure
- Prevents PSV lifting and flaring

### 3. Thermal Expansion

```
[Storage] --liquid--> [HIPPS] ---> [Isolated Section] ---> [Valve]
```

- Liquid trapped between valves
- Sun/ambient heating causes thermal expansion
- HIPPS prevents overpressure rupture
- Common in pipeline systems

## Diagnostic and Monitoring

### Getting HIPPS Status

```java
// Basic status
System.out.println(hippsValve.toString());

// Comprehensive diagnostics
System.out.println(hippsValve.getDiagnostics());

// Key metrics
int activeTx = hippsValve.getActiveTransmitterCount();
boolean tripped = hippsValve.hasTripped();
int spurious = hippsValve.getSpuriousTripCount();
double lastTrip = hippsValve.getLastTripTime();
boolean testDue = hippsValve.isProofTestDue();
```

### Output Example

```
=== HIPPS DIAGNOSTICS ===
System: HIPPS-XV-001
SIL Rating: SIL 3
Configuration: 2oo3 voting
Closure Time: 3.0 s

Transmitter Status:
  PT-1: ALARM (92.50 bara)
  PT-2: ALARM (92.45 bara)
  PT-3: OK (89.80 bara)

Operational History:
  Total Trips: 1
  Spurious Trips: 0
  Last Trip: 15.5 s
  Runtime: 120.0 s

Maintenance:
  Proof Test Interval: 8760 hrs
  Time Since Proof Test: 450.5 hrs
  Status: OK
```

## Testing

Comprehensive test suite located at:
`src/test/java/neqsim/process/equipment/valve/HIPPSValveTest.java`

Tests cover:
- ✅ Basic configuration
- ✅ All voting logic schemes (1oo1, 1oo2, 2oo2, 2oo3, 2oo4)
- ✅ Transient response and closure timing
- ✅ Transmitter failure scenarios
- ✅ Partial stroke testing
- ✅ Proof test tracking
- ✅ Integration with PSV
- ✅ Spurious trip detection
- ✅ Reset and reopen procedures
- ✅ Bypass mode operation

Run tests:
```bash
mvnw test -Dtest=HIPPSValveTest
```

## Standards and References

### Industry Standards
- **IEC 61508**: Functional Safety of Electrical/Electronic/Programmable Electronic Safety-related Systems
- **IEC 61511**: Functional Safety - Safety Instrumented Systems for the Process Industry Sector
- **API RP 14C**: Recommended Practice for Analysis, Design, Installation, and Testing of Safety Systems for Offshore Production Facilities
- **API RP 521**: Pressure-relieving and Depressuring Systems

### SIL Requirements

| SIL Level | PFD (Probability of Failure on Demand) | Typical Application |
|-----------|----------------------------------------|---------------------|
| SIL 1 | 10⁻¹ to 10⁻² | Low risk, 1oo1 voting | 
| SIL 2 | 10⁻² to 10⁻³ | Medium risk, 1oo2 or 2oo3 voting |
| SIL 3 | 10⁻³ to 10⁻⁴ | High risk, 2oo3 voting |

## Summary

HIPPS in NeqSim provides:
- ✅ **Comprehensive SIS modeling** for safety simulations
- ✅ **Redundant architecture** with multiple voting schemes
- ✅ **Realistic transient behavior** including closure time
- ✅ **SIL-rated configuration** (SIL 1, 2, 3)
- ✅ **Partial stroke and proof testing** support
- ✅ **Diagnostic monitoring** for safety validation
- ✅ **Integration with PSVs** for layered protection
- ✅ **Transmitter failure scenarios** for reliability analysis

**Key Advantage**: HIPPS prevents overpressure **before** it occurs, eliminating flaring and protecting equipment, while PSVs relieve pressure **after** it exceeds safe limits.

For safety-critical applications, **HIPPS + PSV** provides defense-in-depth protection strategy.

## Author

Implementation follows NeqSim architecture patterns and coding standards for process safety simulation.
