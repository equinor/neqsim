---
title: "PSD Valve with High-High Alarm Trip"
description: "The `PSDValve` (Process Shutdown Valve) is a safety isolation valve that automatically closes when a High-High (HIHI) pressure alarm is triggered. It provides emergency shutdown protection by monitori..."
---

# PSD Valve with High-High Alarm Trip

## Overview

The `PSDValve` (Process Shutdown Valve) is a safety isolation valve that automatically closes when a High-High (HIHI) pressure alarm is triggered. It provides emergency shutdown protection by monitoring a pressure transmitter and rapidly closing to prevent overpressure conditions from propagating through the process.

## Key Features

- **Automatic HIHI Trip**: Monitors pressure transmitter and closes automatically on HIHI alarm
- **Fast Closure**: Configurable fast closure time (default 2 seconds)
- **Trip State Tracking**: Maintains trip state for safety interlock logic
- **Manual Reset**: Requires operator reset after trip before valve can be reopened
- **Enable/Disable**: Trip function can be enabled or disabled as needed

## How It Works

1. **Normal Operation**: Valve operates fully open, allowing flow to pass through
2. **Pressure Monitoring**: Linked pressure transmitter continuously evaluates pressure against alarm limits
3. **HIHI Alarm**: When pressure exceeds highHighLimit, alarm activates after configured delay
4. **Automatic Trip**: PSD valve detects HIHI alarm and commands closure
5. **Fast Closure**: Valve closes in configured closure time (typically 2 seconds)
6. **Trip Latching**: Valve remains closed even if pressure drops - manual reset required
7. **Reset & Recovery**: Operator resets trip, then manually opens valve

## Basic Usage

### 1. Create Pressure Transmitter with Alarm Configuration

```java
// Create pressure transmitter monitoring separator inlet
PressureTransmitter PT = new PressureTransmitter("PT-101", separatorInlet);

// Configure HIHI alarm at 55 bara with 1 bara deadband and 0.5 second delay
AlarmConfig alarmConfig = AlarmConfig.builder()
    .highHighLimit(55.0)      // HIHI trip point
    .deadband(1.0)             // Alarm clears at 54 bara
    .delay(0.5)                // 0.5 second confirmation delay
    .unit("bara")
    .build();

PT.setAlarmConfig(alarmConfig);
```

### 2. Create PSD Valve

```java
// Create PSD valve on separator inlet
PSDValve psdValve = new PSDValve("PSD-101", feedStream);
psdValve.setPercentValveOpening(100.0);  // Start fully open
psdValve.setCv(150.0);                    // Sizing coefficient
psdValve.setClosureTime(2.0);            // 2 seconds fast closure

// Link to pressure transmitter
psdValve.linkToPressureTransmitter(PT);
```

### 3. Run Dynamic Simulation

```java
double time = 0.0;
double dt = 1.0; // 1 second time step

while (time < simulationTime) {
    // Update pressure measurement
    double measuredPressure = psdValve.getOutletStream().getPressure("bara");
    PT.evaluateAlarm(measuredPressure, dt, time);
    
    // Run equipment transient calculations
    psdValve.runTransient(dt, UUID.randomUUID());
    separator.runTransient(dt, UUID.randomUUID());
    
    // Check if valve has tripped
    if (psdValve.hasTripped()) {
        System.out.println("PSD VALVE TRIPPED - Emergency shutdown activated!");
        // Implement emergency response...
    }
    
    time += dt;
}
```

### 4. Reset After Trip

```java
// After alarm clears and situation is safe
if (psdValve.hasTripped()) {
    psdValve.reset();                      // Clear trip state
    psdValve.setPercentValveOpening(100.0); // Manually reopen valve
}
```

## Complete Example

```java
import neqsim.process.equipment.valve.PSDValve;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.measurementdevice.PressureTransmitter;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

public class PSDValveExample {
    public static void main(String[] args) {
        // Create natural gas mixture at 50 bara, 40°C
        SystemInterface fluid = new SystemSrkEos(273.15 + 40, 50.0);
        fluid.addComponent("nitrogen", 0.5);
        fluid.addComponent("CO2", 1.0);
        fluid.addComponent("methane", 85.0);
        fluid.addComponent("ethane", 8.0);
        fluid.addComponent("propane", 4.0);
        fluid.addComponent("i-butane", 0.75);
        fluid.addComponent("n-butane", 0.75);
        fluid.setMixingRule(2);

        // Create feed stream
        Stream feedStream = new Stream("Feed", fluid);
        feedStream.setFlowRate(5000.0, "kg/hr");
        feedStream.setTemperature(40.0, "C");
        feedStream.setPressure(50.0, "bara");
        feedStream.run();

        // Create PSD valve on separator inlet
        PSDValve psdValve = new PSDValve("PSD-101", feedStream);
        psdValve.setPercentValveOpening(100.0);
        psdValve.setCv(150.0);
        psdValve.setClosureTime(2.0); // 2 second fast closure
        psdValve.run();

        // Create pressure transmitter monitoring PSD outlet (separator inlet)
        PressureTransmitter PT = new PressureTransmitter("PT-101", 
                                                         psdValve.getOutletStream());

        // Configure HIHI alarm
        AlarmConfig alarmConfig = AlarmConfig.builder()
            .highHighLimit(55.0)  // PAHH at 55 bara
            .deadband(1.0)
            .delay(0.5)
            .unit("bara")
            .build();
        PT.setAlarmConfig(alarmConfig);

        // Link PSD valve to pressure transmitter
        psdValve.linkToPressureTransmitter(PT);

        // Create separator
        Separator separator = new Separator("Separator", psdValve.getOutletStream());
        separator.setInternalDiameter(1.5);
        separator.setSeparatorLength(4.0);
        separator.run();

        // Dynamic simulation
        double time = 0.0;
        double dt = 1.0;

        System.out.println("=== PSD VALVE PROTECTION SYSTEM ===");
        System.out.println("HIHI setpoint: 55.0 bara");
        System.out.println("PSD closure time: 2.0 seconds\n");

        for (int i = 0; i < 100; i++) {
            // Run transient calculations
            psdValve.runTransient(dt, UUID.randomUUID());
            separator.runTransient(dt, UUID.randomUUID());

            time += dt;

            // Evaluate alarm
            double pressure = psdValve.getOutletStream().getPressure("bara");
            PT.evaluateAlarm(pressure, dt, time);

            // Status reporting
            if (i % 10 == 0 || psdValve.hasTripped()) {
                String alarmState = "NONE";
                if (PT.getAlarmState().isActive()) {
                    alarmState = PT.getAlarmState().getActiveLevel().toString();
                }
                
                System.out.printf("Time: %5.0f s | Pressure: %5.2f bara | " +
                                  "Alarm: %4s | PSD: %5.1f %% | Tripped: %3s%n",
                    time, pressure, alarmState,
                    psdValve.getPercentValveOpening(),
                    psdValve.hasTripped() ? "YES" : "NO");
            }

            if (psdValve.hasTripped()) {
                System.out.println("\n*** EMERGENCY SHUTDOWN ACTIVATED ***");
                break;
            }
        }
    }
}
```

## Alarm Configuration

The PSD valve relies on NeqSim's alarm system. Key parameters:

| Parameter | Description | Typical Value |
|-----------|-------------|---------------|
| `highHighLimit` | HIHI trip pressure | 110% of MAWP |
| `highLimit` | High alarm (warning only) | 105% of MAWP |
| `deadband` | Hysteresis to prevent chattering | 1-2% of setpoint |
| `delay` | Confirmation time before alarm | 0.1-2.0 seconds |
| `unit` | Engineering unit | "bara", "barg", "psia" |

## API Reference

### Constructor

```java
PSDValve(String name)
PSDValve(String name, StreamInterface inletStream)
```

### Configuration Methods

```java
void linkToPressureTransmitter(MeasurementDeviceInterface transmitter)
void setClosureTime(double closureTime)  // seconds
void setTripEnabled(boolean enabled)
void setCv(double Cv)  // Valve sizing coefficient
```

### Status Methods

```java
boolean hasTripped()
boolean isTripEnabled()
double getClosureTime()
MeasurementDeviceInterface getPressureTransmitter()
```

### Control Methods

```java
void reset()  // Clear trip state
void setPercentValveOpening(double opening)  // 0-100%
@Override
void runTransient(double dt, UUID id)
```

## Best Practices

1. **Alarm Setpoint**: Set HIHI at safe margin below equipment MAWP (typically 95-98% of MAWP)
2. **Closure Time**: Balance between fast response and avoiding water hammer (1-5 seconds typical)
3. **Deadband**: Use 1-2% deadband to prevent alarm chattering at trip point
4. **Alarm Delay**: Short delay (0.1-0.5s) to confirm trip, avoid nuisance trips
5. **Testing**: Regularly test PSD functionality in simulation before deployment
6. **Documentation**: Document trip setpoints in process safety documentation
7. **Redundancy**: Consider redundant pressure transmitters for critical applications

## Comparison: PSD Valve vs Safety Valve

| Feature | PSD Valve | Safety Valve |
|---------|-----------|--------------|
| Activation | HIHI alarm signal | Pressure overcomes spring |
| Response Time | 1-5 seconds (fast) | Milliseconds (immediate) |
| Reopening | Manual reset required | Automatic reseating |
| Primary Use | Isolation/shutdown | Pressure relief |
| Flow Direction | Stops inlet flow | Vents to atmosphere/flare |
| Typical Location | Inlet to equipment | Top of vessel/equipment |
| Failure Mode | Should fail closed | Must fail open |

## Integration with Process Safety

The PSD valve integrates with NeqSim's process safety features:

- **ProcessAlarmManager**: Coordinates all alarms across equipment
- **AlarmEvent History**: Tracks activation, acknowledgment, clearance
- **Safety Interlock Logic**: PSD valves can trigger cascade shutdowns
- **Emergency Depressurization**: Combine with relief devices for complete protection

## See Also

- [Safety Valve Dynamic Sizing](../safety/psv_dynamic_sizing_example.md)
- [Rupture Disk Behavior](../safety/rupture_disk_dynamic_behavior.md)
- [Process Control Overview](process_control.md)
- [Alarm System Documentation](../safety/alarm_system_guide.md)
