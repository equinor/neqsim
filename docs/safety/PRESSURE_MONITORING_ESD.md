---
title: Pressure Monitoring in ESD Blowdown System
description: The ESD blowdown system now includes comprehensive **pressure monitoring** during transient calculations to verify that the separator pressure is properly released via the blowdown valve.
---

# Pressure Monitoring in ESD Blowdown System

## Overview

The ESD blowdown system now includes comprehensive **pressure monitoring** during transient calculations to verify that the separator pressure is properly released via the blowdown valve.

## Key Features

### 1. Continuous Pressure Monitoring
The system tracks separator pressure at every time step:
- **Initial pressure** - Baseline before any events
- **Maximum pressure** - Peak pressure during simulation
- **Minimum pressure** - Lowest pressure reached
- **Final pressure** - End-state pressure

### 2. Pressure Relief Verification
Automated checks verify:
- ✓ Pressure decreases after blowdown activation
- ✓ Blowdown valve opens as expected
- ✓ Gas flows to flare system
- ✓ Pressure drop percentage calculated

## Usage Examples

### Example 1: Basic Pressure Monitoring (ESDBlowdownSystemExample.java)

```java
// Track pressure during blowdown
double initialPressure = separator.getGasOutStream().getPressure("bara");
double minPressure = initialPressure;
double maxPressure = initialPressure;

for (double time = 0.0; time <= totalTime; time += timeStep) {
    // Run equipment in transient mode
    separator.run();
    bdValve.runTransient(timeStep, uuid);
    // ... other equipment
    
    // Monitor separator pressure
    double currentPressure = separator.getGasOutStream().getPressure("bara");
    minPressure = Math.min(minPressure, currentPressure);
    maxPressure = Math.max(maxPressure, currentPressure);
    
    System.out.printf("Time: %.1fs, Pressure: %.2f bara%n", time, currentPressure);
}

// Verify pressure relief
System.out.printf("Pressure drop: %.2f bar (%.1f%% reduction)%n", 
    initialPressure - finalPressure, 
    100.0 * (initialPressure - finalPressure) / initialPressure);
```

### Example 2: Pressure Buildup and Relief Test

The new test `testPressureReliefViaBlowdown()` demonstrates:

**Scenario:**
1. **Normal Operation (0-5s)** - Separator at 60 bara, outlet valve 50% open
2. **Outlet Blockage (5-10s)** - Outlet valve closes to 5%, pressure rises
3. **ESD Activation (10s)** - Push button activates blowdown valve
4. **Depressurization (10-30s)** - Pressure drops as gas flows to flare

**Output:**
```
═══ PRESSURE PROFILE ═══
Initial pressure: 60.00 bara
Maximum pressure reached: 65.23 bara
Pressure at ESD activation: 65.23 bara
Final pressure: 52.18 bara
Pressure rise before ESD: 5.23 bar (8.7% increase)
Pressure drop after ESD: 13.05 bar (20.0% reduction)

✓ Pressure buildup detected: 60.00 bara → 65.23 bara
✓ ESD successfully activated at 65.23 bara
✓ Pressure relieved to 52.18 bara (20.0% reduction)
```

## Monitoring Points

### Separator Pressure
```java
double sepPressure = separator.getPressure("bara");
// or
double sepPressure = separator.getGasOutStream().getPressure("bara");
```

### Blowdown Flow Rate
```java
double bdFlow = blowdownStream.getFlowRate("kg/hr");
```

### Valve Opening
```java
double opening = bdValve.getPercentValveOpening(); // 0-100%
boolean isOpen = bdValve.getPercentValveOpening() > 90.0;
```

### Flare Load
```java
double heatRelease = flare.getHeatDuty("MW");
double totalGas = flare.getCumulativeGasBurned("kg");
```

## Transient Calculation Pattern

```java
// Initialize tracking variables
double initialPressure = separator.getPressure("bara");
double maxPressure = initialPressure;
double finalPressure = initialPressure;

// Time loop
double timeStep = 0.5; // seconds
for (double time = 0.0; time <= simulationTime; time += timeStep) {
    
    // 1. Update process conditions (if needed)
    if (time >= eventTime) {
        esdButton.push(); // Trigger ESD
        splitter.setSplitFactors(new double[] {0.0, 1.0});
    }
    
    // 2. Run all equipment
    feedStream.run();
    separator.run();
    // ... other steady-state equipment
    
    // 3. Run transient equipment
    bdValve.runTransient(timeStep, UUID.randomUUID());
    
    // ... more equipment
    
    flare.run();
    flare.updateCumulative(timeStep); // Track cumulative values
    
    // 4. Monitor and record
    double currentPressure = separator.getPressure("bara");
    maxPressure = Math.max(maxPressure, currentPressure);
    finalPressure = currentPressure;
    
    // 5. Output (optional)
    System.out.printf("t=%.1fs: P=%.2f bara, BD=%.1f%%, Flow=%.1f kg/hr%n",
        time, currentPressure, bdValve.getPercentValveOpening(), 
        blowdownStream.getFlowRate("kg/hr"));
}

// 6. Verify results
if (finalPressure < initialPressure) {
    System.out.println("✓ Pressure successfully relieved");
}
```

## Verification Checks

### Automated Assertions
```java
// Pressure should decrease after blowdown
assertTrue(finalPressure < pressureAtEsdActivation, 
    "Pressure should decrease after ESD activation");

// Valve should be activated
assertTrue(bdValve.isActivated(), "BD valve should be activated");

// Gas should flow to flare
assertTrue(flare.getCumulativeGasBurned("kg") > 0, 
    "Gas should flow to flare");

// Valve should be fully open
assertTrue(bdValve.getPercentValveOpening() > 90.0, 
    "BD valve should be nearly fully open");
```

### Console Output Verification
The system provides detailed output showing:
- Time-series pressure data
- Valve opening progression
- Flow rates to flare
- Status messages at key events

## Running the Examples

### Run the basic example with pressure monitoring:
```bash
mvn exec:java -Dexec.mainClass="neqsim.process.util.example.ESDBlowdownSystemExample"
```

### Run the comprehensive test suite:
```bash
# Run all blowdown tests
mvn test -Dtest=BlowdownValveESDSystemTest

# Run specific pressure relief test
mvn test -Dtest=BlowdownValveESDSystemTest#testPressureReliefViaBlowdown
```

## Expected Results

When the blowdown system is working correctly, you should observe:

1. **Pressure Decrease** - Separator pressure drops after ESD activation
2. **Valve Opening** - BD valve gradually opens to 100% over configured time
3. **Gas Flow** - Significant flow rate through blowdown valve to flare
4. **Heat Release** - Flare shows increasing heat duty as gas combusts
5. **Cumulative Tracking** - Total gas burned and emissions tracked

### Typical Values
- **Pressure drop**: 10-30% reduction depending on flow rates and orifice sizing
- **Opening time**: 3-5 seconds for BD valve
- **Blowdown rate**: Controlled by orifice Cv (typically 100-200)
- **Flare heat release**: Proportional to gas flow rate and composition

## Troubleshooting

If pressure is not relieved:
1. Check BD valve is activated: `bdValve.isActivated()`
2. Verify valve opening: `bdValve.getPercentValveOpening()`
3. Check flow to blowdown: `blowdownStream.getFlowRate("kg/hr")`
4. Verify splitter redirects flow: `splitter.setSplitFactors(...)`
5. Check orifice pressure drop: `bdOrifice.getOutletPressure()`

## Integration with Process Systems

The pressure monitoring integrates with:
- **Alarm Systems** - Can trigger on high/low pressure
- **Control Systems** - Feedback for automatic control
- **Safety Systems** - ESD interlock logic
- **Data Logging** - Historical pressure trends
- **Flare Management** - Load tracking and emissions

## Summary

The enhanced ESD blowdown system provides:
- ✅ Real-time pressure monitoring during transient calculations
- ✅ Automated verification of pressure relief
- ✅ Detailed output showing system behavior
- ✅ Pressure buildup and relief scenarios
- ✅ Complete test coverage with assertions
- ✅ Integration with flare systems for emissions tracking

This ensures that the blowdown valve correctly relieves pressure when activated, protecting equipment from overpressure conditions.
