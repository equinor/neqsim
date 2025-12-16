# Integrated Safety Systems Example

## Overview

The `IntegratedSafetySystemExample` demonstrates a comprehensive safety system implementation for process facilities, incorporating multiple layers of protection following the principles of **Safety Instrumented Systems (SIS)** and **Defense in Depth**.

## Safety Architecture

### Protection Layers (Onion Model)

The example implements a complete safety architecture with four distinct layers:

```
┌─────────────────────────────────────┐
│  1. High Pressure Alarm (SIL-1)     │ ← 55.0 bara
│     ├─ Operator intervention        │
│     └─ Alarms and warnings          │
├─────────────────────────────────────┤
│  2. ESD System (SIL-2)               │ ← 58.0 bara
│     ├─ Emergency shutdown            │
│     ├─ Blowdown activation           │
│     └─ Fire detection response       │
├─────────────────────────────────────┤
│  3. HIPPS (SIL-3)                    │ ← 60.0 bara
│     ├─ High integrity protection     │
│     ├─ Fast-acting valve closure     │
│     └─ Redundant pressure monitoring │
├─────────────────────────────────────┤
│  4. PSV (Mechanical)                 │ ← 65.0 bara
│     └─ Final mechanical relief       │
└─────────────────────────────────────┘
```

## System Components

### 1. High Integrity Pressure Protection System (HIPPS)

**Purpose**: Prevent overpressure by rapidly closing inlet valve before pressure reaches dangerous levels.

**Safety Integrity Level**: SIL-3 (PFD: 0.0001-0.001)

**Features**:
- Dual redundant pressure transmitters (PT-101A, PT-101B)
- 2oo2 voting logic (both sensors must confirm high pressure)
- Fast-acting valve closure (2 seconds)
- Activation setpoint: 60.0 bara

**Implementation**:
```java
HIPPSController hippsController = 
    new HIPPSController("HIPPS-Logic-001", hippsPT1, hippsPT2, hippsValve);

// 2oo2 voting for higher integrity
if (p1 >= HIPPS_ACTIVATION_PRESSURE && p2 >= HIPPS_ACTIVATION_PRESSURE) {
    hippsValve.setPercentValveOpening(0.0); // Close immediately
}
```

### 2. Emergency Shutdown (ESD) System

**Purpose**: Shut down process and activate blowdown when emergency conditions are detected.

**Safety Integrity Level**: SIL-2 (PFD: 0.001-0.01)

**Activation Conditions**:
- High-High pressure alarm (≥58.0 bara)
- Fire detection (≥150°C)
- Manual ESD push button

**Actions on Activation**:
1. Close inlet isolation valve
2. Activate blowdown valve
3. Redirect gas flow to flare system

**Implementation**:
```java
ESDController esdController = 
    new ESDController("ESD-Logic-201", separatorPT, separatorTT, 
                      esdButton, esdInletValve, bdValve);

// Multiple trigger conditions
if (pressure >= HIGH_HIGH_PRESSURE_ALARM || 
    temperature >= FIRE_DETECTION_TEMPERATURE || 
    manualESD.isPushed()) {
    esdValve.setPercentValveOpening(0.0);
    blowdownValve.activate();
}
```

### 3. Fire Detection System

**Purpose**: Detect fire conditions and trigger ESD.

**Configuration**:
- 3 temperature sensors (TT-401A, TT-401B, TT-401C)
- 2oo3 voting logic (2 out of 3 sensors must detect fire)
- Detection threshold: 150°C

**Implementation**:
```java
FireDetectionSystem fireSystem = 
    new FireDetectionSystem(
        new TemperatureTransmitter[] {fireTT1, fireTT2, fireTT3}, 
        2  // voting threshold
    );
```

### 4. Blowdown System

**Purpose**: Rapidly depressurize equipment during emergency situations.

**Features**:
- Normally closed blowdown valve (BD-301)
- Configurable opening time (5 seconds default)
- Automatic activation via ESD controller
- Routes to flare system

**Implementation**:
```java
BlowdownValve bdValve = new BlowdownValve("BD-301", blowdownStream);
bdValve.setOpeningTime(5.0);
bdValve.setCv(250.0);
```

### 5. Pressure Safety Valve (PSV)

**Purpose**: Final mechanical protection layer - relieves pressure if all other systems fail.

**Characteristics**:
- Set pressure: 65.0 bara
- Full open pressure: 67.0 bara
- Blowdown: 7% (reseats at 60.45 bara)
- Hysteresis logic prevents chattering

**Implementation**:
```java
SafetyValve psv = new SafetyValve("PSV-401", separatorGasOut);
psv.setPressureSpec(65.0);
psv.setFullOpenPressure(67.0);
psv.setBlowdown(7.0);
```

### 6. Flare System

**Purpose**: Safely combust and dispose of emergency relief gases.

**Features**:
- Accepts flows from both blowdown and PSV
- Tracks cumulative gas burned
- Monitors heat release and emissions
- 60m flame height, 1.0m tip diameter

## Safety Scenarios

The example demonstrates four operational scenarios:

### Scenario 1: Normal Operation
- All systems in standby
- Process gas flows to downstream equipment
- All safety systems monitoring

**Expected Behavior**:
- HIPPS: Normal
- ESD: Normal
- PSV: Closed
- No flare flow

### Scenario 2: HIPPS Activation (SIL-3)
- Feed pressure surge to 70 bara
- HIPPS detects overpressure on both transmitters
- Fast valve closure prevents further pressure increase

**Expected Behavior**:
- HIPPS activates when P ≥ 60 bara
- Inlet valve closes in 2 seconds
- Pressure controlled before ESD activation
- PSV remains closed

### Scenario 3: ESD and Blowdown (SIL-2)
- Manual ESD activation by operator
- Inlet valve closes
- Blowdown valve opens progressively
- Gas routed to flare

**Expected Behavior**:
- ESD inlet valve closes in 5 seconds
- Blowdown valve opens in 5 seconds
- Pressure gradually reduced via controlled blowdown
- Flare receives and combusts gas

### Scenario 4: PSV Relief (Final Protection)
- Extreme overpressure scenario
- HIPPS and ESD assumed failed
- PSV provides final mechanical protection

**Expected Behavior**:
- PSV opens when P ≥ 65 bara
- Relief flow to flare
- Continuous relief until pressure drops
- PSV reseats when P ≤ 60.45 bara

## SIL Requirements and Implementation

### Safety Integrity Levels (IEC 61508/61511)

| SIL Level | PFD Range | RRF Range | Implementation |
|-----------|-----------|-----------|----------------|
| SIL-3 | 10⁻⁴ to 10⁻³ | 10,000 to 1,000 | HIPPS with 2oo2 voting |
| SIL-2 | 10⁻³ to 10⁻² | 1,000 to 100 | ESD with redundant sensors |
| SIL-1 | 10⁻² to 10⁻¹ | 100 to 10 | Alarms with operator action |

**PFD**: Probability of Failure on Demand  
**RRF**: Risk Reduction Factor

### Voting Architectures

**2oo2 (HIPPS - SIL-3)**:
- Both sensors must detect high pressure
- Higher reliability (lower spurious trips)
- Suitable for high-consequence scenarios

**2oo3 (Fire Detection)**:
- 2 out of 3 sensors must detect fire
- Balances reliability and availability
- Reduces false alarms

## Key Design Principles

### 1. Defense in Depth
Multiple independent protection layers ensure safety even if individual layers fail.

### 2. Fail-Safe Design
- Blowdown valve: Normally closed (fail-closed)
- Safety valve: Spring-loaded mechanical (fail-open)
- HIPPS valve: Fast-acting closure

### 3. Separation of Functions
- HIPPS: Prevention (stop overpressure before it occurs)
- ESD: Emergency response (safe shutdown)
- PSV: Protection (final mechanical relief)

### 4. Diversity
- Different technologies (electronic, mechanical)
- Different activation mechanisms (automatic, manual)
- Different physical principles (sensors, springs)

## Usage Example

```java
// Run the integrated safety system example
java neqsim.process.util.example.IntegratedSafetySystemExample

// Expected output:
// - System configuration summary
// - Four safety scenarios with detailed monitoring
// - Verification of each protection layer
```

## Output Interpretation

### Normal Operation
```
HIPPS status: NORMAL
ESD status: NORMAL
Fire detection: NORMAL
PSV status: CLOSED
```

### During HIPPS Activation
```
>>> HIPPS ACTIVATED (SIL-3) - Both pressure sensors confirm <<<
HIPPS Valve: Closing from 100% to 0%
Separator pressure: Controlled below 60 bara
```

### During ESD and Blowdown
```
>>> ESD ACTIVATED (SIL-2) - Manual Push Button <<<
ESD inlet valve: Closing to 0%
BD valve: Opening to 100%
Blowdown flow: Increasing to flare
Separator pressure: Decreasing
```

### PSV Relief
```
Sep P > 65.0 bara
PSV status: RELIEVING
PSV Flow: High flow to flare
```

## Performance Metrics

The example tracks and reports:

1. **Pressure profiles** during each scenario
2. **Valve opening percentages** over time
3. **Flow rates** to flare system
4. **Cumulative emissions** (gas burned, CO₂, heat)
5. **Response times** of safety systems

## Related Documentation

- [ESD Blowdown System](./ESD_BLOWDOWN_SYSTEM.md)
- [HIPPS Implementation](./HIPPS_SUMMARY.md)
- [PSV Dynamic Sizing](./psv_dynamic_sizing_example.md)
- [Pressure Monitoring](./PRESSURE_MONITORING_ESD.md)

## API Reference

### Key Classes Used

- `ThrottlingValve` - Control valves and isolation valves
- `BlowdownValve` - Emergency depressurization valve
- `SafetyValve` - Pressure relief valve with hysteresis
- `PressureTransmitter` - Pressure measurement devices
- `TemperatureTransmitter` - Temperature measurement devices
- `PushButton` - Manual activation device
- `Separator` - Process vessel with transient capability
- `Flare` - Flare system with emission tracking
- `ControllerDeviceBaseClass` - Base for custom controllers

## Best Practices

1. **Always implement multiple protection layers** - Never rely on a single safety device
2. **Use appropriate SIL ratings** - Match safety system integrity to risk level
3. **Test safety systems regularly** - Proof test intervals per IEC 61511
4. **Document all safety logic** - Clear, auditable control algorithms
5. **Monitor performance** - Track activation rates and failure modes
6. **Train operators** - Ensure understanding of safety system behavior

## Further Development

This example can be extended to include:

- Emergency shutdown valves (ESVs) on multiple process units
- High-High level trips on separators
- Low flow trips on critical services
- Integration with distributed control system (DCS)
- Cause and effect diagrams
- Safety instrumented function (SIF) verification
- Reliability calculations (PFD, SIL verification)

## References

- IEC 61508: Functional Safety of Electrical/Electronic/Programmable Electronic Safety-related Systems
- IEC 61511: Functional Safety - Safety Instrumented Systems for the Process Industry Sector
- API RP 521: Pressure-relieving and Depressuring Systems
- API STD 2000: Venting Atmospheric and Low-pressure Storage Tanks
