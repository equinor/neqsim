# PSV Dynamic Sizing Example

This example demonstrates how to perform a dynamic safety calculation for a pressure safety valve (PSV) sizing using NeqSim's transient simulation capabilities.

## Scenario

A high-pressure separator operates at ~50 bara with gas output flowing through a splitter:
- **Stream 1**: Goes to a pressure control valve (PCV) for normal operation (99.9% of flow)
- **Stream 2**: Goes to a pressure safety valve (PSV) for overpressure protection (0.1% of flow)

## Dynamic Event Sequence

1. **Normal operation** (t=0-50s): Separator operates normally, PCV at 50% opening, PSV closed
2. **Blocked outlet** (t=50s): PCV suddenly closes to 1% (simulating blocked outlet)
3. **Pressure rise** (t=50-130s): Separator pressure increases from ~32 bara to 55 bara
4. **PSV opens** (t=130s): PSV starts opening when pressure exceeds set pressure (55 bara)
5. **Relief phase** (t=130-200s): PSV relieves gas to control pressure at ~58.7 bara
6. **Recovery** (t=200s): PCV reopens to 50%, allowing normal flow path
7. **Pressure drops** (t=200-260s): System pressure decreases as both valves relieve
8. **PSV closes** (t=260s): PSV reseats when pressure drops to blowdown pressure (~51.15 bara)

## PSV Hysteresis Behavior

The PSV implements realistic hysteresis (blowdown) behavior to prevent valve chattering:

- **Set Pressure**: 55.0 bara - PSV starts opening at this pressure
- **Full Open Pressure**: 60.5 bara - PSV is 100% open (10% overpressure allowed)
- **Blowdown Pressure**: 51.15 bara - PSV reseats at 93% of set pressure (7% blowdown)

**Key Point**: Once the PSV opens, it does NOT close immediately when pressure drops below the set pressure. It stays open until pressure drops to the blowdown/reseat pressure. This prevents rapid cycling (chattering) that could damage the valve.

### Typical Blowdown Values
- **Gas service**: 7-10% (default: 7%)
- **Liquid service**: 10-20%
- **Steam service**: 2-5%

## Code Structure

```java
// Setup equipment
Separator separator = new Separator("HP Separator", feedStream);
Splitter gasSplitter = new Splitter("Gas Splitter", separator.getGasOutStream(), 2);
ThrottlingValve pressureControlValve = new ThrottlingValve("PCV-001", gasSplitter.getSplitStream(0));
SafetyValve pressureSafetyValve = new SafetyValve("PSV-001", gasSplitter.getSplitStream(1));

// Configure PSV with automatic opening
pressureSafetyValve.setPressureSpec(55.0);  // Set pressure (bara)
pressureSafetyValve.setFullOpenPressure(60.5);  // Full open at 110% of set
// Blowdown is automatically set to 7% (reseat at 51.15 bara)

// Alternative: Explicitly set blowdown percentage
pressureSafetyValve.setBlowdown(10.0);  // 10% blowdown for liquid service

// Dynamic simulation loop
for (int i = 0; i < numSteps; i++) {
    currentTime = i * dt;
    
    // Simulate events (blockage, recovery, etc.)
    if (currentTime >= 50.0 && currentTime < 51.0) {
        pressureControlValve.setPercentValveOpening(1.0);  // Block outlet
    }
    if (currentTime >= 200.0 && currentTime < 201.0) {
        pressureControlValve.setPercentValveOpening(50.0);  // Recover
    }
    
    // Run transient calculations
    // PSV opening is calculated automatically based on inlet pressure
    separator.runTransient(dt, id);
    gasSplitter.runTransient(dt, id);
    pressureControlValve.runTransient(dt, id);
    pressureSafetyValve.runTransient(dt, id);  // Automatic PSV control with hysteresis
}
```

## Automatic PSV Control

The `SafetyValve.runTransient()` method automatically:
1. Monitors inlet pressure
2. Calculates valve opening percentage based on:
   - Set pressure (starts opening)
   - Full open pressure (100% open)
   - Current valve state (open/closed)
3. Implements hysteresis:
   - **When closed**: Opens when P ≥ P_set
   - **When open**: Closes when P ≤ P_blowdown (reseat pressure)
4. Prevents chattering through state tracking

## Results

From the test simulation with 5000 kg/hr feed:

| Parameter | Value |
|-----------|-------|
| Feed flow rate | 5000 kg/hr |
| PSV set pressure | 55.0 bara |
| PSV full open pressure | 60.5 bara |
| PSV blowdown pressure | 51.15 bara (7% blowdown) |
| Maximum separator pressure | 58.69 bara |
| Maximum PSV relief flow | 6086 kg/hr |
| PSV opening at max pressure | 67.1% |

## Key Observations

1. **PSV prevents catastrophic overpressure**: Maximum pressure (58.69 bara) is well below the full open pressure (60.5 bara), demonstrating effective pressure control.

2. **Adequate relief capacity**: PSV relieves 6086 kg/hr, which exceeds the feed rate (5000 kg/hr), ensuring the valve can handle the relief scenario.

3. **Hysteresis prevents chattering**: 
   - PSV opens at 55.0 bara
   - PSV stays open even when pressure drops to 52-54 bara
   - PSV only closes when pressure reaches 50.95 bara (below the 51.15 bara blowdown)
   
4. **Smooth pressure control**: The automatic PSV control provides smooth pressure regulation during both pressure buildup and recovery phases.

## Best Practices

1. **Always use dynamic mode**: Set `setCalculateSteadyState(false)` for all equipment in transient simulations

2. **Size PSV conservatively**: Ensure PSV can handle at least 100% of the feed flow rate

3. **Set appropriate blowdown**: Use 7-10% for gas, 10-20% for liquid service to prevent chattering

4. **Use unique UUID**: Create one UUID per simulation run to track transient state correctly

5. **Choose appropriate time step**: 0.5 seconds provides good resolution for PSV dynamics

6. **Monitor key parameters**: Track separator pressure, valve openings, and flow rates throughout the simulation

## See Also

- Test implementation: `SafetyValveDynamicSizingTest.java`
- SafetyValve class: `src/main/java/neqsim/process/equipment/valve/SafetyValve.java`
- API 520 - Sizing, Selection, and Installation of Pressure-relieving Devices
