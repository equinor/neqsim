---
title: Control Valves
description: Comprehensive documentation for control valves in NeqSim including CheckValve, LevelControlValve, PressureControlValve, and safety valves (ESD, PSD).
---

# Control Valves

Comprehensive documentation for control valves in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Check Valve](#check-valve)
- [Level Control Valve](#level-control-valve)
- [Pressure Control Valve](#pressure-control-valve)
- [ESD Valve](#esd-valve)
- [PSD Valve](#psd-valve)
- [Dynamic Simulation](#dynamic-simulation)
- [API Reference](#api-reference)

---

## Overview

**Location:** `neqsim.process.equipment.valve`

NeqSim provides specialized control valves for process automation and safety systems:

| Class | Description | Typical Application |
|-------|-------------|---------------------|
| `CheckValve` | Non-return valve | Pump/compressor protection |
| `LevelControlValve` | Level regulation | Separator liquid discharge |
| `PressureControlValve` | Pressure regulation | Gas pressure letdown |
| `ESDValve` | Emergency shutdown | Process isolation |
| `PSDValve` | Process shutdown | Overpressure protection |

All control valves extend `ThrottlingValve` and inherit Cv sizing, JT effects, and dynamic capabilities.

---

## Check Valve

A **check valve** (non-return valve) prevents reverse flow automatically based on differential pressure.

### Features

- Automatic operation based on differential pressure
- Configurable cracking pressure (minimum ΔP to open)
- Zero leakage when closed (ideal model)
- Minimal pressure drop when fully open

### Applications

- Pump discharge protection
- Compressor discharge protection
- Parallel equipment isolation
- Gravity drainage systems
- Siphon prevention

### Java Example

```java
import neqsim.process.equipment.valve.CheckValve;

// Create check valve on pump discharge
CheckValve checkValve = new CheckValve("CV-101", pumpDischargeStream);
checkValve.setCrackingPressure(0.2);  // Opens at 0.2 bar differential
checkValve.setCv(250.0);              // Flow coefficient when fully open
checkValve.run();

// Check valve state
if (checkValve.isOpen()) {
    System.out.println("Forward flow - valve open");
    double flowRate = checkValve.getOutletStream().getFlowRate("kg/hr");
} else {
    System.out.println("Reverse flow blocked - valve closed");
}
```

### Python Example

```python
from neqsim import jneqsim

CheckValve = jneqsim.process.equipment.valve.CheckValve

# Create check valve
check_valve = CheckValve("CV-101", pump_discharge)
check_valve.setCrackingPressure(0.2)  # 0.2 bar
check_valve.setCv(250.0)
check_valve.run()

print(f"Valve open: {check_valve.isOpen()}")
print(f"Cracking pressure: {check_valve.getCrackingPressure()} bar")
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setCrackingPressure(double)` | Set minimum ΔP to open valve (bar) |
| `getCrackingPressure()` | Get cracking pressure |
| `isOpen()` | Check if valve is currently open |

---

## Level Control Valve

A **level control valve** (LCV) modulates opening to maintain vessel level at setpoint.

### Features

- Automatic level control with proportional action
- Configurable level setpoint (0-100%)
- Direct or reverse control action
- Adjustable controller gain
- Fail-safe position configuration

### Control Actions

| Action | Description | Use Case |
|--------|-------------|----------|
| **DIRECT** | Increase opening → increase outflow → decrease level | Liquid discharge (most common) |
| **REVERSE** | Increase opening → decrease outflow → increase level | Rare configurations |

### Java Example

```java
import neqsim.process.equipment.valve.LevelControlValve;
import neqsim.process.equipment.valve.LevelControlValve.ControlAction;

// Create separator with liquid outlet
Separator separator = new Separator("V-101", feedStream);
Stream liquidOut = separator.getLiquidOutStream();

// Create level control valve
LevelControlValve lcv = new LevelControlValve("LCV-101", liquidOut);
lcv.setLevelSetpoint(50.0);          // Control to 50% level
lcv.setControllerGain(3.0);          // Proportional gain
lcv.setControlAction(ControlAction.DIRECT);
lcv.setMeasuredLevel(45.0);          // Current level from transmitter
lcv.setCv(150.0);
lcv.run();

System.out.println("Valve opening: " + lcv.getPercentValveOpening() + "%");
System.out.println("Level error: " + lcv.getControlError() + "%");
```

### Python Example

```python
from neqsim import jneqsim

LevelControlValve = jneqsim.process.equipment.valve.LevelControlValve

# Create level control valve on separator liquid outlet
lcv = LevelControlValve("LCV-101", separator.getLiquidOutStream())
lcv.setLevelSetpoint(50.0)       # 50% level setpoint
lcv.setControllerGain(3.0)       # Proportional gain
lcv.setMeasuredLevel(45.0)       # Current measured level
lcv.setCv(150.0)
lcv.run()

print(f"Valve opening: {lcv.getPercentValveOpening()}%")
print(f"Control error: {lcv.getControlError()}%")
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setLevelSetpoint(double)` | Set target level (0-100%) |
| `setMeasuredLevel(double)` | Update measured level from transmitter |
| `setControllerGain(double)` | Set proportional gain (1-10 typical) |
| `setControlAction(ControlAction)` | DIRECT or REVERSE |
| `getControlError()` | Get setpoint - measured level |
| `setFailSafePosition(double)` | Position on loss of signal (%) |

---

## Pressure Control Valve

A **pressure control valve** (PCV) modulates opening to maintain pressure at setpoint.

### Features

- Automatic pressure control
- Multiple control modes (downstream, upstream, differential)
- Proportional control action
- Min/max opening limits

### Control Modes

| Mode | Description | Application |
|------|-------------|-------------|
| **DOWNSTREAM** | Control outlet pressure | Gas letdown stations |
| **UPSTREAM** | Control inlet pressure | Back-pressure control |
| **DIFFERENTIAL** | Control ΔP across valve | Flow control |

### Java Example

```java
import neqsim.process.equipment.valve.PressureControlValve;
import neqsim.process.equipment.valve.PressureControlValve.ControlMode;

// Create pressure control valve
PressureControlValve pcv = new PressureControlValve("PCV-101", hpGasStream);
pcv.setPressureSetpoint(25.0);       // Control to 25 bara downstream
pcv.setControlMode(ControlMode.DOWNSTREAM);
pcv.setControllerGain(5.0);          // Proportional gain
pcv.setCv(300.0);
pcv.run();

System.out.println("Valve opening: " + pcv.getPercentValveOpening() + "%");
System.out.println("Outlet pressure: " + pcv.getOutletStream().getPressure("bara") + " bara");
System.out.println("Control error: " + pcv.getControlError() + " bar");
```

### Python Example

```python
from neqsim import jneqsim

PressureControlValve = jneqsim.process.equipment.valve.PressureControlValve

# Create pressure control valve for gas letdown
pcv = PressureControlValve("PCV-101", hp_gas_stream)
pcv.setPressureSetpoint(25.0)  # 25 bara target
pcv.setControllerGain(5.0)
pcv.setCv(300.0)
pcv.run()

print(f"Outlet pressure: {pcv.getOutletStream().getPressure('bara'):.1f} bara")
```

### Key Methods

| Method | Description |
|--------|-------------|
| `setPressureSetpoint(double)` | Set target pressure (bara) |
| `setControlMode(ControlMode)` | DOWNSTREAM, UPSTREAM, DIFFERENTIAL |
| `setControllerGain(double)` | Proportional gain (1-10 typical) |
| `getProcessVariable()` | Get measured pressure |
| `getControlError()` | Get setpoint - PV |
| `setAutoMode(boolean)` | Enable/disable automatic control |

---

## ESD Valve

An **ESD valve** (Emergency Shutdown Valve / Isolation Valve XV) is a fail-closed valve for emergency isolation.

### Features

- Fail-safe design: fails to closed position
- Energize-to-open operation
- Configurable stroke time (closure time)
- Partial stroke testing capability
- SIL rating support
- Status feedback for monitoring

### Design Philosophy

| State | Description |
|-------|-------------|
| **Energized** | Valve is open (normal operation) |
| **De-energized** | Valve closes (emergency or loss of power) |

### Java Example

```java
import neqsim.process.equipment.valve.ESDValve;
import java.util.UUID;

// Create ESD inlet valve (normally open)
ESDValve esdValve = new ESDValve("ESD-XV-101", feedStream);
esdValve.setStrokeTime(10.0);  // 10 seconds to close
esdValve.setCv(500.0);         // Large Cv for minimal ΔP when open

// Normal operation - valve open
esdValve.energize();
esdValve.run();
System.out.println("Normal: Valve " + (esdValve.isEnergized() ? "OPEN" : "CLOSED"));

// Emergency shutdown
esdValve.deEnergize();  // Triggers closure

// Dynamic simulation of closure
double dt = 0.5;  // 0.5 second time steps
for (double t = 0; t <= 12.0; t += dt) {
    esdValve.runTransient(dt, UUID.randomUUID());
    System.out.printf("t=%.1f s: Opening = %.1f%%%n", 
        t, esdValve.getPercentValveOpening());
}
```

### Integration with ESD Controller

```java
// ESD controller monitors process conditions
if (pressure > highPressureSetpoint || fireDetected || manualESDActivated) {
    esdValve.deEnergize();  // Initiate emergency closure
}
```

### Python Example

```python
from neqsim import jneqsim
import uuid

ESDValve = jneqsim.process.equipment.valve.ESDValve

# Create ESD valve
esd_valve = ESDValve("ESD-XV-101", feed_stream)
esd_valve.setStrokeTime(10.0)  # 10 seconds closure
esd_valve.setCv(500.0)

# Normal operation
esd_valve.energize()
esd_valve.run()

# Emergency shutdown
esd_valve.deEnergize()

# Simulate closure
dt = 0.5
for i in range(25):
    esd_valve.runTransient(dt, uuid.uuid4())
    print(f"Opening: {esd_valve.getPercentValveOpening():.1f}%")
```

### Key Methods

| Method | Description |
|--------|-------------|
| `energize()` | Power actuator - allows valve to open |
| `deEnergize()` | Remove power - initiates closure |
| `isEnergized()` | Check energization state |
| `setStrokeTime(double)` | Set closure time (seconds) |
| `getStrokeTime()` | Get closure time |
| `setFailSafePosition(double)` | Position on de-energize (0=closed) |
| `startPartialStrokeTest()` | Initiate PST |
| `hasTripCompleted()` | Check if closure is complete |

---

## PSD Valve

A **PSD valve** (Process Shutdown Valve) automatically closes on high-high (HIHI) pressure alarm.

### Features

- Links to pressure transmitter
- Automatic closure on HIHI alarm
- Fast closure time (default 2 seconds)
- Manual reset required after trip
- Trip state tracking

### Java Example

```java
import neqsim.process.equipment.valve.PSDValve;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.process.alarm.AlarmConfig;

// Create pressure transmitter with alarm configuration
PressureTransmitter PT = new PressureTransmitter("PT-101", separatorInlet);
PT.setAlarmConfig(AlarmConfig.builder()
    .highHighLimit(55.0)
    .deadband(1.0)
    .delay(0.5)
    .unit("bara")
    .build());

// Create PSD valve linked to transmitter
PSDValve psdValve = new PSDValve("PSD-101", feedStream);
psdValve.linkToPressureTransmitter(PT);
psdValve.setClosureTime(2.0);  // 2 seconds fast closure

// In dynamic simulation loop
for (int i = 0; i < 100; i++) {
    process.runTransient(0.1, UUID.randomUUID());
    
    if (psdValve.hasTripped()) {
        System.out.println("PSD valve tripped on HIHI alarm!");
        break;
    }
}

// Reset after alarm clears (operator action)
psdValve.reset();
```

### Python Example

```python
from neqsim import jneqsim

PSDValve = jneqsim.process.equipment.valve.PSDValve

# Create PSD valve
psd_valve = PSDValve("PSD-101", feed_stream)
psd_valve.linkToPressureTransmitter(pressure_transmitter)
psd_valve.setClosureTime(2.0)  # Fast closure

# Check trip status
if psd_valve.hasTripped():
    print("Valve tripped on HIHI!")
    # After condition clears
    psd_valve.reset()
```

### Key Methods

| Method | Description |
|--------|-------------|
| `linkToPressureTransmitter(MeasurementDeviceInterface)` | Connect to PT |
| `setClosureTime(double)` | Set closure time (seconds) |
| `hasTripped()` | Check if valve has tripped |
| `reset()` | Reset trip state (requires operator action) |
| `setTripEnabled(boolean)` | Enable/disable automatic trip |
| `getPressureTransmitter()` | Get linked transmitter |

---

## Dynamic Simulation

All control valves support dynamic simulation with `runTransient()`.

### Valve Travel Dynamics

```java
// Configure valve travel times
valve.setOpeningTravelTime(5.0);   // 5 seconds to fully open
valve.setClosingTravelTime(3.0);   // 3 seconds to fully close

// Simulate opening
valve.setPercentValveOpening(100.0);  // Command full open
for (double t = 0; t <= 6.0; t += 0.1) {
    valve.runTransient(0.1, UUID.randomUUID());
    System.out.printf("t=%.1f: Actual opening = %.1f%%%n", 
        t, valve.getActualValveOpening());
}
```

### Level Control Dynamic Response

```java
// Simulate level disturbance
double level = 50.0;
double dt = 1.0;

for (int i = 0; i < 100; i++) {
    // Disturbance at t=20s
    if (i == 20) level = 70.0;
    
    lcv.setMeasuredLevel(level);
    lcv.runTransient(dt, UUID.randomUUID());
    
    // Level responds to valve opening
    double flowOut = lcv.getOutletStream().getFlowRate("kg/hr");
    level = level - (flowOut - inflow) * dt / vesselVolume;
    
    System.out.printf("t=%d: Level=%.1f%%, Opening=%.1f%%%n", 
        i, level, lcv.getPercentValveOpening());
}
```

---

## API Reference

### Common Base Methods (from ThrottlingValve)

| Method | Description |
|--------|-------------|
| `setCv(double, String)` | Set flow coefficient ("US" or "SI") |
| `getCv(String)` | Get flow coefficient |
| `setOutletPressure(double, String)` | Set outlet pressure |
| `setPercentValveOpening(double)` | Set valve position (0-100%) |
| `getPercentValveOpening()` | Get commanded position |
| `run()` | Execute steady-state calculation |
| `runTransient(double dt, UUID id)` | Execute dynamic step |

### Valve Sizing

```java
// Size valve for service
valve.setCv(calculateRequiredCv(flow, dP, fluid));
valve.setValveCharacteristic("equal_percentage");

// Check valve selection
double maxCv = valve.getCv("US");
double actualCv = valve.getActualCv();  // At current opening
double rangeability = maxCv / actualCv;
```

---

## Related Documentation

- [Valve Equipment](valves) - Throttling valve and safety valve documentation
- [HIPPS Implementation](../../safety/hipps_implementation) - High Integrity Pressure Protection
- [Dynamic Simulation](../../simulation/dynamic_simulation_guide) - Transient simulation guide
- [Process Controllers](../controllers) - PID controller documentation
