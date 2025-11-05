# ESD Blowdown System Implementation

## Overview

This implementation adds a complete Emergency Shutdown (ESD) blowdown system to NeqSim, including:

1. **BlowdownValve (BDValve)** - A normally-closed valve that opens during ESD events
2. **PushButton** - A manual instrument that can activate the blowdown valve
3. **Orifice Control** - Flow rate control through downstream orifice
4. **Flare Integration** - Safe disposal of blowdown gas

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

### 3. Complete ESD System Test
**Location:** `src/test/java/neqsim/process/equipment/valve/BlowdownValveESDSystemTest.java`

**Test Coverage:**
- Complete ESD blowdown scenario with dynamic simulation
- Push button operation and integration
- Manual mode (auto-activation disabled)
- Blowdown valve behavior in isolation
- Multiple blowdown sources to common flare

**Key Scenario:**
1. Normal operation with gas to process
2. ESD activation via push button at t=10s
3. Splitter redirects gas from process to blowdown
4. BD valve opens over 5 seconds
5. Gas flows through orifice to flare (controlled depressurization)
6. Flare tracks heat release and emissions

### 4. Demonstration Example
**Location:** `src/main/java/neqsim/process/util/example/ESDBlowdownSystemExample.java`

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
         BD Orifice (Cv=150) - Controls depressurization rate
              |
              v
         Flare Header (1.5 bara)
              |
              v
         Flare - Combusts blowdown gas
```

## Key Design Features

### Safety
- Blowdown valve is normally closed (fail-safe)
- Push button reset does NOT reset valve (requires operator action)
- Orifice controls depressurization rate
- All safety actions are logged and tracked

### Flexibility
- Auto-activation can be enabled/disabled
- Multiple blowdown sources can feed common flare
- Configurable opening times and flow capacities
- Integration with existing alarm systems

### Monitoring
- Real-time tracking of valve opening percentage
- Flow rate monitoring
- Flare heat release and emissions
- Cumulative gas blown down
- System state tracking

## Running the Example

### Via Main Method:
```bash
mvn exec:java -Dexec.mainClass="neqsim.process.util.example.ESDBlowdownSystemExample"
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

1. **testESDBlowdownSystem()** - Complete dynamic simulation
2. **testPushButtonOperation()** - Button activation and reset
3. **testPushButtonManualMode()** - Manual control mode
4. **testBlowdownValveOperation()** - Valve behavior
5. **testMultipleBlowdownSources()** - Multiple BD sources to common flare

Run all tests:
```bash
mvn test -Dtest=BlowdownValveESDSystemTest
```

## Future Enhancements

Potential additions:
- Integration with control systems
- Automatic splitter control on activation
- Pressure decay tracking
- Depressurization curve validation
- Integration with process alarms
- Time-to-depressurize calculations
- Multi-stage blowdown systems

## Author

Implementation follows NeqSim architecture patterns and coding standards.
