# Pressure Safety Valve (PSV) Dynamic Sizing Example

## Overview

This example demonstrates how to perform a dynamic safety calculation for sizing a pressure safety valve (PSV) using NeqSim's transient simulation capabilities. The scenario simulates a blocked outlet condition where a pressure control valve suddenly closes, causing pressure to rise in a separator until the PSV opens to prevent overpressure.

## Process Description

### Equipment Configuration

1. **Separator**: High-pressure separator receiving gas feed at 50 bara
2. **Splitter**: Splits the gas outlet into two streams
   - Stream 1 (99.9%): Goes to the pressure control valve (PCV) for normal operation
   - Stream 2 (0.1%): Goes to the pressure safety valve (PSV) for overpressure protection
3. **Pressure Control Valve (PCV-001)**: Controls normal outlet pressure (5 bara)
4. **Pressure Safety Valve (PSV-001)**: Protects against overpressure
   - Set pressure: 55 bara
   - Full open pressure: 60.5 bara (110% of set pressure)

### Safety Scenario

The simulation models a sudden blocked outlet scenario:

- **t = 0-50s**: Normal operation with PCV at 50% opening
- **t = 50s**: PCV suddenly closes to 1% (simulating blocked outlet)
- **t > 50s**: 
  - Pressure in separator begins to rise
  - PSV opens when pressure exceeds 55 bara
  - PSV modulates to maintain pressure below catastrophic levels

## Implementation

### Key Code Sections

#### 1. System Setup

```java
// Create gas system
SystemInterface feedFluid = new SystemSrkEos(273.15 + 40.0, 50.0);
feedFluid.addComponent("nitrogen", 1.0);
feedFluid.addComponent("methane", 85.0);
// ... additional components

// Create separator
Separator separator = new Separator("HP Separator", feedStream);
separator.setCalculateSteadyState(false); // Enable dynamic mode
```

#### 2. Splitter Configuration

```java
// Split gas to PCV and PSV
Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 2);
gasSplitter.setSplitFactors(new double[] {0.999, 0.001});
gasSplitter.setCalculateSteadyState(false);
```

#### 3. PSV Automatic Opening

The `SafetyValve` class now automatically controls its opening based on inlet pressure during dynamic simulations. When `runTransient()` is called, the valve:

- **Closes** when pressure is below set pressure
- **Opens proportionally** between set pressure and full open pressure  
- **Fully opens** at or above full open pressure

```java
// PSV configured with set and full open pressures
SafetyValve pressureSafetyValve = new SafetyValve("PSV-001", stream);
pressureSafetyValve.setPressureSpec(55.0);  // Set pressure
pressureSafetyValve.setFullOpenPressure(60.5);  // Full open pressure
pressureSafetyValve.setCalculateSteadyState(false);  // Enable dynamic mode

// PSV automatically calculates opening in runTransient()
// No manual opening calculation needed!
```

#### 4. Transient Simulation Loop

```java
double dt = 0.5; // Time step in seconds
for (int i = 0; i < numSteps; i++) {
    currentTime = i * dt;
    
    // Simulate blocked outlet at t=50s
    if (currentTime >= 50.0 && currentTime < 51.0) {
        pressureControlValve.setPercentValveOpening(1.0);
    }
    
    // Run transient calculations
    // PSV automatically adjusts its opening based on inlet pressure
    separator.runTransient(dt, id);
    gasSplitter.runTransient(dt, id);
    pressureControlValve.runTransient(dt, id);
    pressureSafetyValve.runTransient(dt, id);  // Automatic PSV control
}
```

## Results

### Typical Simulation Results

- **Feed flow rate**: 5000 kg/hr
- **Normal operating pressure**: ~50 bara
- **PSV set pressure**: 55 bara
- **PSV full open pressure**: 60.5 bara
- **Maximum separator pressure**: 58.69 bara (controlled by PSV)
- **Maximum PSV relief flow**: 4305 kg/hr (86% of feed rate)
- **Required PSV Cv**: 150

### Key Observations

1. **Pressure Response**: When the PCV closes at t=50s, the separator pressure begins to rise steadily
2. **PSV Activation**: PSV starts opening at ~140s when pressure reaches 55 bara
3. **Pressure Control**: PSV successfully limits maximum pressure to 58.69 bara (6.7% above set pressure)
4. **Flow Distribution**: PSV relieves approximately 86% of the feed flow rate at maximum opening

## PSV Sizing Validation

The test validates several critical aspects:

1. ✓ PSV remains closed during normal operation
2. ✓ PSV opens at the set pressure
3. ✓ Maximum pressure stays within acceptable limits (< 130% of full open pressure)
4. ✓ PSV relief capacity exceeds minimum requirements (> 80% of feed rate)
5. ✓ Pressure rise occurs after PCV blockage
6. ✓ Overall pressure limited to within 35% of set pressure

## Usage

### Running the Example

The example is implemented as a JUnit test:

```bash
mvnw test -Dtest=SafetyValveDynamicSizingTest
```

### Customization

You can modify the following parameters to study different scenarios:

- **Feed composition and flow rate**: Adjust in `SystemInterface` setup
- **Separator dimensions**: `setInternalDiameter()`, `setSeparatorLength()`
- **PSV set pressure**: `setPressureSpec()`
- **PSV full open pressure**: `setFullOpenPressure()`
- **PSV Cv**: `setCv()` - size the valve appropriately
- **Time step**: `dt` variable - smaller for better accuracy
- **Blockage timing**: Modify the condition checking `currentTime`

## Best Practices for PSV Sizing

1. **Set Pressure**: Typically 10% above maximum allowable working pressure (MAWP)
2. **Accumulation**: Full opening at 110% of set pressure (10% accumulation)
3. **Relief Capacity**: PSV must handle the maximum credible flow rate
4. **Dynamic Simulation**: Validates PSV response and pressure dynamics
5. **Time Step**: Use 0.5-1.0 second time steps for valve dynamics
6. **Validation**: Compare with API 520/521 or equivalent standards

## Related Examples

- [Separator Test](../test/java/neqsim/process/equipment/separator/SeparatorTest.java)
- [Throttling Valve Test](../test/java/neqsim/process/equipment/valve/ThrottlingValveTest.java)
- [Process System Transient Test](../test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java)

## References

- API Standard 520: Sizing, Selection, and Installation of Pressure-relieving Devices
- API Standard 521: Pressure-relieving and Depressuring Systems
- ASME Boiler and Pressure Vessel Code, Section VIII
