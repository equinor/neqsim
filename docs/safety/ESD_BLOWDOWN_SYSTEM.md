---
title: ESD Blowdown System Implementation
description: "This implementation adds a complete Emergency Shutdown (ESD) blowdown system to NeqSim."
---

# ESD Blowdown System Implementation

## Overview

This implementation adds a complete Emergency Shutdown (ESD) blowdown system to NeqSim, including:

1. **BlowdownValve (BDValve)** - A normally-closed valve that opens during ESD events
2. **PushButton** - A manual instrument that can activate the blowdown valve
3. **Orifice** - ISO 5167 pressure-driven flow restriction for controlled depressurization
4. **Flare Integration** - Safe disposal of blowdown gas

## ESD sequence lifecycle and restart

`ESDLogic.deactivate()` pauses the sequence. Calling `activate()` from `PAUSED` resumes
the same step, elapsed time and unfinished delay; it does not replay completed actions.
Calling `reset()` or starting a new completed/failed cycle re-arms built-in one-shot
actions through `LogicAction.reset()`. Conditional and parallel actions re-arm their
children as well as their own selection/execution bookkeeping. Custom stateful actions
must implement that hook; its default is a no-op for stateless actions.

Action and sequence reset change bookkeeping only. They do not energize, reopen or
reset any target valve, clear a physical fault, or establish a restart permissive.
In particular, `ESDValve.reset()` is a separate equipment operation which can reopen
the valve. A configured safety function still needs its demand-clear and restart
checks. Action delays must be finite and nonnegative, and operations must be non-null.
`execute()` rejects negative/nonfinite intervals before advancing state; zero permits
an instantaneous demand evaluation.

Serialization now retains the ESD action graph. Serialize the process and its bound
logic in one graph when their references must remain shared after restore. Built-in
actions support serialization, but every custom action/condition and captured payload
must also support it; Java rejects an unsupported graph at write time. Older streams
discarded the transient action list and cannot recover that definition: restoration
rejects them with `InvalidObjectException` and requires reconstruction from controlled
configuration. A fresh logic factory on an isolated process copy remains the primary
scenario path. This is a software restart contract, not permission to restart a plant.

The rearm, pause/resume, nested actions, equipment-state preservation and serialization
cases are exercised by `ESDLogicLifecycleTest`.

Native actions expose their actual equipment references through
`LogicAction.getTargetEquipment()`. Conditional/parallel sequences include all possible
configured branch targets; `ESDLogic` deduplicates them by object reference, not by tag.
Opaque legacy actions expose no references and default to incomplete identity coverage.
`ESDLogic.getTargetEquipmentCoverageIssue()` reports that condition even when a partial
target list exists. A declared `getTargetName()` string is never resolved heuristically
to physical equipment. P&ID handoff must require complete target identity and exact
membership of the configured process copy before accepting the binding. This identifies
configured targets; a separate scenario must demonstrate their required response.

`ESDLogic.getTripActionConfiguration()` supplies an ordered action tree with step
delays and explicit support status for P&ID configuration fingerprints. Its bounded
closure policy accepts exact native `TripValveAction` operations on native `ESDValve`
targets and nonempty native parallel groups. Opening operations, overriding subclasses,
opaque actions, empty groups and cycles are unsupported for that policy. This keeps
a trip-to-open substitution or a changed delay from retaining closure evidence merely
because the logic and valve tags match. Unsupported actions can retain their existing
runtime APIs; they need separate reviewed semantics before strict P&ID safety binding.

## Components Created

### 1. BlowdownValve Class
**Location:** `src/main/java/neqsim/process/equipment/valve/BlowdownValve.java`

**Key Features:**
- Normally closed (fail-safe design)
- Configurable opening time (default 5 seconds)
- Activation tracking
- Transient behavior during opening
- Reset capability

**Usage Example:**
```java
// Create blowdown valve
BlowdownValve bdValve = new BlowdownValve("BD-101", blowdownStream);
bdValve.setOpeningTime(5.0); // 5 seconds to fully open

// Activate in emergency
bdValve.activate();

// Check status
if (bdValve.isActivated()) {
    System.out.println("Blowdown in progress");
}
```

### 2. PushButton Instrument
**Location:** `src/main/java/neqsim/process/measurementdevice/PushButton.java`

**Key Features:**
- Binary state: pushed (1.0) or not pushed (0.0)
- Links to BlowdownValve for automatic activation
- Auto-activation can be enabled/disabled
- Measured value integration with alarm systems
- Safety feature: button reset doesn't reset valve

**Usage Example:**
```java
// Create push button linked to BD valve
PushButton esdButton = new PushButton("ESD-PB-101", bdValve);

// Operator pushes button in emergency
esdButton.push(); // Automatically activates linked BD valve

// Check button state
if (esdButton.isPushed()) {
    System.out.println("ESD button pushed - blowdown active");
}

// Reset button (valve stays activated for safety)
esdButton.reset();
```

### 3. Orifice Class (Transient Mode)
**Location:** `src/main/java/neqsim/process/equipment/diffpressure/Orifice.java`

**Key Features:**
- ISO 5167 compliant flow calculations
- Pressure-driven flow in transient simulations
- Reader-Harris/Gallagher discharge coefficient
- Compressible flow with expansibility factor
- Automatic flow calculation based on ΔP

**Transient Behavior:**
In dynamic/transient mode, the orifice acts as a pressure-driven flow restriction device. Unlike steady-state mode where flow may be specified upstream, in transient mode the orifice **calculates and determines** the actual flow based on the pressure differential:

**Flow Equation (ISO 5167):**
```
m = A × C × ε × √(2ρΔP / (1 - β⁴))
```

Where:
- A = orifice area (m²)
- C = discharge coefficient
- ε = expansibility factor (accounts for compressibility)
- ρ = fluid density at inlet (kg/m³)
- ΔP = pressure drop across orifice (Pa)
- β = diameter ratio (d/D)

**Usage Example:**
```java
// Create orifice with ISO 5167 parameters
// Orifice(name, pipeDiameter, orificeDiameter, P_upstream_ref, P_downstream, dischargeCoeff)
Orifice bdOrifice = new Orifice("BD-Orifice", 
    0.45,  // Pipe diameter (m)
    0.18,  // Orifice diameter (m)
    60.0,  // Upstream reference pressure (bara)
    1.5,   // Downstream pressure - flare header (bara)
    0.61); // Discharge coefficient

// In transient simulation, the orifice automatically calculates flow
bdOrifice.runTransient(timeStep, uuid);

// As separator pressure drops, orifice flow decreases
// Example: ΔP 22.5→4.2 bar causes flow to drop 29,110→7,756 kg/hr (73% reduction)
```

**Important Notes:**
- The `pressureDownstream` parameter sets the boundary pressure (e.g., flare header at 1.5 bara)
- Flow automatically decreases as upstream pressure drops during blowdown
- Properly models the √(ΔP) relationship - flow reduces as driving force decreases
- Essential for realistic depressurization rate calculations

### 4. Complete ESD System Test
**Location:** `src/test/java/neqsim/process/equipment/valve/BlowdownValveESDSystemTest.java`

**Test Coverage:**
- Complete ESD blowdown scenario with dynamic simulation
- Pressure-driven orifice flow validation (flow decreases with ΔP)
- Push button operation and integration
- Manual mode (auto-activation disabled)
- Blowdown valve behavior in isolation
- Multiple blowdown sources to common flare
- Pressure relief and monitoring scenarios

**Key Scenario:**
1. Normal operation with gas to process
2. ESD activation via push button at t=10s
3. Splitter redirects gas from process to blowdown
4. BD valve opens over 5 seconds
5. Gas flows through orifice to flare - **flow rate automatically decreases as separator depressurizes**
6. Orifice demonstrates ISO 5167 behavior: flow ∝ √(ΔP)
7. Flare tracks heat release and emissions

### 5. Demonstration Example
**Location:** `src/test/java/neqsim/process/util/example/ESDBlowdownSystemExample.java`

A standalone runnable example showing:
- System configuration
- Normal operation
- ESD activation via push button
- Dynamic blowdown simulation
- Performance summary

## System Architecture

```
Separator (50 bara)
    |
    v
Gas Splitter
    |
    +----> Process Stream (normal operation)
    |
    +----> Blowdown Stream
              |
              v
         BD Valve (BD-101) <--- ESD Push Button (ESD-PB-101)
              |
              v
         BD Orifice - ISO 5167 pressure-driven flow
              |      Flow = f(√ΔP) - decreases as P₁ drops
              |      Controls depressurization rate
              v
         Flare Header (1.5 bara)
              |
              v
         Flare - Combusts blowdown gas
```

## Physics and Behavior

### Orifice Flow Dynamics

In transient simulations, the orifice flow is **pressure-driven** following ISO 5167:

```
Flow Rate ∝ √(ΔP)
```

**Example from ESD Simulation:**
- **t=10s**: Separator at 24.02 bara, ΔP=22.52 bar → Flow=29,110 kg/hr
- **t=30s**: Separator at 5.68 bara, ΔP=4.18 bar → Flow=7,756 kg/hr
- **Result**: 88.6% pressure reduction → 73.4% flow reduction

This demonstrates realistic depressurization behavior where:
1. Initial flow is high due to large pressure differential
2. As separator depressurizes, driving force (ΔP) decreases
3. Flow rate automatically reduces following √(ΔP) relationship
4. Prevents excessive depressurization rates at low pressures
5. Provides controlled, safe blowdown progression

### Why This Matters for Safety Studies

**Realistic Depressurization Profiles:**
- Flow isn't constant during blowdown
- Initial high flow rates decrease over time
- Matches real-world orifice behavior
- Critical for flare load calculations
- Important for time-to-depressurize estimates

**PSV/Rupture Disc Scenarios:**
- Accurate flow vs. pressure relationships
- Proper flare sizing
- Realistic heat release profiles
- Better emissions estimates

## Key Design Features

### Safety
- Blowdown valve is normally closed (fail-safe)
- Push button reset does NOT reset valve (requires operator action)
- **Orifice provides pressure-driven flow control (ISO 5167)**
- **Automatic flow reduction as pressure decreases - prevents over-depressurization**
- All safety actions are logged and tracked

### Flexibility
- Auto-activation can be enabled/disabled
- Multiple blowdown sources can feed common flare
- Configurable opening times and flow capacities
- Integration with existing alarm systems

### Monitoring
- Real-time tracking of valve opening percentage
- **Pressure-dependent flow rate through orifice**
- **Dynamic ΔP and flow relationship validation**
- Flare heat release and emissions
- Cumulative gas blown down
- System state tracking

## Running the Example

### Via Main Method:
```bash
./mvnw -Dexec.classpathScope=test -Dexec.mainClass="neqsim.process.util.example.ESDBlowdownSystemExample" exec:java
```

### Via Test:
```bash
mvn test -Dtest=BlowdownValveESDSystemTest
```

## Expected Output

The system will show:
1. **Configuration** - System setup and parameters
2. **Normal Operation** - Initial steady state
3. **ESD Activation** - Push button activation
4. **Dynamic Simulation** - Blowdown progression over time
5. **Summary** - Total gas blown down, heat released, emissions

### Sample Output:
```
╔════════════════════════════════════════════════════════════════╗
║        EMERGENCY SHUTDOWN (ESD) BLOWDOWN SYSTEM TEST          ║
╚════════════════════════════════════════════════════════════════╝

═══ SYSTEM CONFIGURATION ═══
Separator operating pressure: 50.0 bara
Gas flow rate: 10000.0 kg/hr
Blowdown valve: BD-101 (normally closed)
ESD Push Button: ESD-PB-101 (linked to BD-101)
BD Orifice Cv: 150.0
Flare header pressure: 1.5 bara
BD valve opening time: 5.0 seconds

>>> OPERATOR PUSHES ESD BUTTON - BLOWDOWN INITIATED <<<

Time (s) | Sep Press | Process Flow | BD Flow    | BD Opening | Flare Flow | Heat Release
         | (bara)    | (kg/hr)      | (kg/hr)    | (%)        | (kg/hr)    | (MW)
---------|-----------|--------------|------------|------------|------------|-------------
    10.0 |     50.00 |         0.0  |    10000.0 |        0.0 |        0.0 |         0.00
    12.0 |     50.00 |         0.0  |    10000.0 |       40.0 |     4000.0 |        29.45
    14.0 |     50.00 |         0.0  |    10000.0 |       80.0 |     8000.0 |        58.91
    16.0 |     50.00 |         0.0  |    10000.0 |      100.0 |    10000.0 |        73.63

═══ BLOWDOWN SUMMARY ═══
Maximum blowdown flow: 10000.0 kg/hr
Total gas blown down: 138.9 kg
Total heat released: 3.21 GJ
Total CO2 emissions: 382.5 kg

✓ ESD push button successfully triggered blowdown
✓ BD valve automatically activated by push button
✓ Controlled depressurization through BD orifice
```

## Integration with Existing NeqSim Components

### Compatible Equipment:
- **Separator** - Source of gas for blowdown
- **Splitter** - Directs flow between process and blowdown
- **ThrottlingValve** - Can be used as BD orifice
- **Mixer** - Flare header for multiple sources
- **Flare** - Combusts and tracks emissions

### Measurement Integration:
- PushButton extends `MeasurementDeviceBaseClass`
- Compatible with alarm systems (`AlarmConfig`)
- Can be used with controllers
- Supports noise and delay simulation

## Testing

All tests are in `BlowdownValveESDSystemTest.java`:

1. **testESDBlowdownSystem()** - Complete dynamic simulation with orifice flow validation
2. **testPushButtonOperation()** - Button activation and reset
3. **testPushButtonManualMode()** - Manual control mode
4. **testBlowdownValveOperation()** - Valve behavior
5. **testMultipleBlowdownSources()** - Multiple BD sources to common flare
6. **testPressureReliefViaBlowdown()** - Pressure buildup and relief scenario

**Orifice Flow Validation:**
The tests verify that orifice flow correctly responds to pressure changes:
- Flow increases when upstream pressure increases
- Flow decreases when upstream pressure decreases
- Flow follows √(ΔP) relationship per ISO 5167
- Demonstrates realistic depressurization profiles

Run all tests:
```bash
mvn test -Dtest=BlowdownValveESDSystemTest
```

## Future Enhancements

Potential additions:
- Integration with control systems
- Automatic splitter control on activation
- Pressure decay curve validation against API/ISO standards
- Multi-component orifice calculations
- Two-phase flow through orifices
- Sonic flow limiting (choked flow)
- Integration with process alarms
- Time-to-depressurize calculations per API 521
- Multi-stage blowdown systems
- Temperature effects on orifice sizing

## Author

Implementation follows NeqSim architecture patterns and coding standards.
