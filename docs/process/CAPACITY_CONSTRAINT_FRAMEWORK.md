# Capacity Constraint Framework

## Overview

The Capacity Constraint Framework extends NeqSim's existing bottleneck analysis capability with multi-constraint support. It provides:

- **Multiple constraints per equipment**: Track speed, power, surge margin, temperature, etc. simultaneously
- **Constraint types**: HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope)
- **Warning thresholds**: Early warning when approaching limits
- **Integration with ProductionOptimizer**: Works seamlessly with existing optimization tools

## Relationship to Existing Bottleneck Analysis

NeqSim already provides bottleneck analysis via `ProcessEquipmentInterface`:

| Existing Method | Description |
|-----------------|-------------|
| `getCapacityDuty()` | Current operating load (power, flow, etc.) |
| `getCapacityMax()` | Maximum design capacity |
| `getRestCapacity()` | Available headroom |
| `ProcessSystem.getBottleneck()` | Equipment with highest utilization |

The new `CapacityConstrainedEquipment` interface **extends** this by allowing:
- Multiple named constraints per equipment
- Constraint severity levels (HARD/SOFT/DESIGN)
- Live value suppliers for dynamic updates
- Detailed bottleneck information via `findBottleneck()`

**The systems are integrated**: `ProcessSystem.getBottleneck()` automatically uses multi-constraint data when available, falling back to single-capacity metrics for equipment that doesn't implement the new interface.

## Architecture

The framework integrates with existing bottleneck analysis in `neqsim.process.equipment.capacity`:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ProcessSystem                                │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  getBottleneck()  │  findBottleneck()  │  getRestCapacity()   │  │
│  │  (unified: checks both single and multi-constraint equipment) │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌─────────────────────────────┐│
│  │  Traditional API     │             │  Multi-Constraint API       ││
│  │  getCapacityDuty()   │             │  CapacityConstrainedEquipment│
│  │  getCapacityMax()    │             │  ├─ getCapacityConstraints()││
│  │  getRestCapacity()   │             │  ├─ getBottleneckConstraint()│
│  └──────────────────────┘             │  └─ getMaxUtilization()     ││
│                                       └─────────────────────────────┘│
│                                                   │                  │
│                                                   ▼                  │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                    CapacityConstraint                            ││
│  │  ┌──────────────────────────────────────────────────────────┐   ││
│  │  │ name │ type │ designValue │ maxValue │ valueSupplier │...│   ││
│  │  └──────────────────────────────────────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

## Core Classes

### 1. CapacityConstraint

The fundamental building block representing a single capacity limit on equipment.

```java
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;

// Create a constraint with fluent builder pattern
CapacityConstraint speedConstraint = new CapacityConstraint("speed", ConstraintType.HARD)
    .setDesignValue(10000.0)           // Design operating point (RPM)
    .setMaxValue(11000.0)              // Absolute maximum (trip point)
    .setMinValue(5000.0)               // Minimum stable operation
    .setUnit("RPM")
    .setValueSupplier(() -> compressor.getSpeed());  // Live value getter
```

#### Constraint Types

| Type | Description | Example |
|------|-------------|---------|
| `HARD` | Absolute limit - equipment trip or damage if exceeded | Compressor max speed, surge limit |
| `SOFT` | Operational limit - reduced efficiency or accelerated wear | High discharge temperature |
| `DESIGN` | Normal operating limit - design basis | Separator gas load factor |

#### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getCurrentValue()` | `double` | Current value from the valueSupplier |
| `getUtilization()` | `double` | Current value / design value (1.0 = 100%) |
| `getUtilizationPercent()` | `double` | Utilization as percentage |
| `isViolated()` | `boolean` | True if utilization > 1.0 |
| `isHardLimitExceeded()` | `boolean` | True if HARD constraint exceeds max value |
| `isNearLimit()` | `boolean` | True if above warning threshold (default 90%) |
| `getMargin()` | `double` | Remaining headroom (1.0 - utilization) |

### 2. CapacityConstrainedEquipment (Interface)

Interface that equipment classes implement to participate in capacity tracking.

```java
public interface CapacityConstrainedEquipment {
    // Get all constraints
    Map<String, CapacityConstraint> getCapacityConstraints();
    
    // Get the most limiting constraint
    CapacityConstraint getBottleneckConstraint();
    
    // Check constraint status
    boolean isCapacityExceeded();
    boolean isHardLimitExceeded();
    boolean isNearCapacityLimit();
    
    // Get utilization metrics
    double getMaxUtilization();
    double getMaxUtilizationPercent();
    double getAvailableMargin();
    
    // Modify constraints
    void addCapacityConstraint(CapacityConstraint constraint);
    boolean removeCapacityConstraint(String constraintName);
    void clearCapacityConstraints();
}
```

### 3. StandardConstraintType (Enum)

Predefined constraint types for common equipment with standardized names and units.

```java
import neqsim.process.equipment.capacity.StandardConstraintType;

// Use predefined constraint types
StandardConstraintType.COMPRESSOR_SPEED          // "speed", "RPM"
StandardConstraintType.COMPRESSOR_POWER          // "power", "kW"
StandardConstraintType.COMPRESSOR_SURGE_MARGIN   // "surgeMargin", "%"
StandardConstraintType.SEPARATOR_GAS_LOAD_FACTOR // "gasLoadFactor", "m/s"
StandardConstraintType.PUMP_NPSH_MARGIN          // "npshMargin", "m"
StandardConstraintType.PIPE_VELOCITY             // "velocity", "m/s"
// ... and more
```

### 4. BottleneckResult

Result class returned by `ProcessSystem.findBottleneck()`.

```java
BottleneckResult result = process.findBottleneck();

if (!result.isEmpty()) {
    System.out.println("Bottleneck: " + result.getEquipmentName());
    System.out.println("Constraint: " + result.getConstraint().getName());
    System.out.println("Utilization: " + result.getUtilizationPercent() + "%");
}
```

## Usage Examples

### Basic Usage: Process-Wide Bottleneck Detection

```java
// Create and run process
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Find the process bottleneck
BottleneckResult bottleneck = process.findBottleneck();
System.out.println("Process bottleneck: " + bottleneck.getEquipmentName());
System.out.println("Limiting constraint: " + bottleneck.getConstraint().getName());
System.out.println("Utilization: " + bottleneck.getUtilizationPercent() + "%");

// Check if any equipment is overloaded
if (process.isAnyEquipmentOverloaded()) {
    System.out.println("WARNING: Equipment operating above design capacity!");
}

// Check if any hard limits are exceeded (critical)
if (process.isAnyHardLimitExceeded()) {
    System.out.println("CRITICAL: Hard equipment limits exceeded!");
}

// Get utilization summary for all equipment
Map<String, Double> utilization = process.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    System.out.printf("%s: %.1f%%\n", entry.getKey(), entry.getValue());
}

// Get equipment near capacity limit (early warning)
List<String> nearLimit = process.getEquipmentNearCapacityLimit();
if (!nearLimit.isEmpty()) {
    System.out.println("Equipment near capacity: " + nearLimit);
}
```

### Individual Equipment Inspection

```java
// Get a specific compressor
Compressor compressor = (Compressor) process.getUnit("27-KA-01");

// Check overall capacity status
System.out.println("Max utilization: " + compressor.getMaxUtilizationPercent() + "%");
System.out.println("Available margin: " + compressor.getAvailableMarginPercent() + "%");

// Inspect individual constraints
Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();
for (CapacityConstraint c : constraints.values()) {
    System.out.printf("  %s: %.1f / %.1f %s (%.1f%% utilized)\n",
        c.getName(), c.getCurrentValue(), c.getDesignValue(), 
        c.getUnit(), c.getUtilizationPercent());
}

// Get the bottleneck constraint for this equipment
CapacityConstraint limiting = compressor.getBottleneckConstraint();
System.out.println("Limiting factor: " + limiting.getName());
```

### Adding Custom Constraints at Runtime

```java
// Add a custom constraint to a separator
Separator separator = (Separator) process.getUnit("20-VA-01");

// Add liquid residence time constraint
CapacityConstraint residenceTime = new CapacityConstraint(
    StandardConstraintType.SEPARATOR_LIQUID_RESIDENCE_TIME, 
    CapacityConstraint.ConstraintType.DESIGN)
    .setDesignValue(180.0)  // 3 minutes minimum
    .setMinValue(60.0)      // Absolute minimum 1 minute
    .setValueSupplier(() -> separator.getLiquidResidenceTime("sec"));

separator.addCapacityConstraint(residenceTime);

// Remove a constraint
separator.removeCapacityConstraint("gasLoadFactor");

// Clear all constraints
separator.clearCapacityConstraints();
```

### Integration with Optimization

```java
/**
 * Find maximum throughput without exceeding equipment capacity.
 */
public double findMaxThroughput(ProcessSystem process, double initialRate) {
    double rate = initialRate;
    double maxRate = initialRate;
    
    while (!process.isAnyEquipmentOverloaded()) {
        maxRate = rate;
        rate *= 1.05;  // Increase by 5%
        
        // Update feed rate
        Stream feed = (Stream) process.getUnit("well stream");
        feed.setFlowRate(rate, "kmol/hr");
        process.run();
    }
    
    // Report bottleneck at max rate
    BottleneckResult bottleneck = process.findBottleneck();
    System.out.printf("Maximum rate: %.0f kmol/hr\n", maxRate);
    System.out.printf("Limited by: %s (%s at %.1f%%)\n",
        bottleneck.getEquipmentName(),
        bottleneck.getConstraint().getName(),
        bottleneck.getUtilizationPercent());
    
    return maxRate;
}
```

## Extending to Other Equipment

### Step-by-Step Guide

To add capacity constraint support to a new equipment type:

#### Step 1: Implement the Interface

```java
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.StandardConstraintType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MyEquipment extends ProcessEquipmentBaseClass 
    implements CapacityConstrainedEquipment {
    
    // Storage for constraints
    private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();
```

#### Step 2: Initialize Default Constraints in Constructor

```java
    public MyEquipment(String name, StreamInterface inletStream) {
        super(name, inletStream);
        initializeCapacityConstraints();
    }
    
    /**
     * Initializes default capacity constraints for this equipment.
     */
    private void initializeCapacityConstraints() {
        // Add constraints relevant to this equipment type
        CapacityConstraint flowConstraint = new CapacityConstraint(
            StandardConstraintType.PUMP_FLOW_RATE,
            CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(designFlowRate)
            .setMaxValue(maxFlowRate)
            .setValueSupplier(() -> this.getFlowRate());
        
        capacityConstraints.put(flowConstraint.getName(), flowConstraint);
    }
```

#### Step 3: Implement Required Interface Methods

```java
    /** {@inheritDoc} */
    @Override
    public Map<String, CapacityConstraint> getCapacityConstraints() {
        return Collections.unmodifiableMap(capacityConstraints);
    }

    /** {@inheritDoc} */
    @Override
    public CapacityConstraint getBottleneckConstraint() {
        CapacityConstraint bottleneck = null;
        double maxUtil = 0.0;
        for (CapacityConstraint c : capacityConstraints.values()) {
            double util = c.getUtilization();
            if (!Double.isNaN(util) && util > maxUtil) {
                maxUtil = util;
                bottleneck = c;
            }
        }
        return bottleneck;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCapacityExceeded() {
        for (CapacityConstraint c : capacityConstraints.values()) {
            if (c.isViolated()) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHardLimitExceeded() {
        for (CapacityConstraint c : capacityConstraints.values()) {
            if (c.isHardLimitExceeded()) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxUtilization() {
        double maxUtil = 0.0;
        for (CapacityConstraint c : capacityConstraints.values()) {
            double util = c.getUtilization();
            if (!Double.isNaN(util) && util > maxUtil) {
                maxUtil = util;
            }
        }
        return maxUtil;
    }

    /** {@inheritDoc} */
    @Override
    public void addCapacityConstraint(CapacityConstraint constraint) {
        if (constraint != null && constraint.getName() != null) {
            capacityConstraints.put(constraint.getName(), constraint);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeCapacityConstraint(String constraintName) {
        return capacityConstraints.remove(constraintName) != null;
    }

    /** {@inheritDoc} */
    @Override
    public void clearCapacityConstraints() {
        capacityConstraints.clear();
    }
```

#### Step 4: Update Constraints When Design Values Change

```java
    /**
     * Sets the design flow rate.
     * 
     * @param flowRate design flow rate in m³/hr
     */
    public void setDesignFlowRate(double flowRate) {
        this.designFlowRate = flowRate;
        updateFlowConstraint();
    }
    
    private void updateFlowConstraint() {
        CapacityConstraint existing = capacityConstraints.get("flowRate");
        if (existing != null) {
            existing.setDesignValue(designFlowRate);
            existing.setMaxValue(maxFlowRate);
        }
    }
```

### Example: Implementing for Pump

```java
public class Pump extends ProcessEquipmentBaseClass 
    implements CapacityConstrainedEquipment {
    
    private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();
    private double designFlowRate = 100.0;  // m³/hr
    private double designHead = 50.0;       // m
    private double designNPSH = 3.0;        // m (required)
    
    private void initializeCapacityConstraints() {
        // Flow rate constraint
        CapacityConstraint flow = new CapacityConstraint(
            StandardConstraintType.PUMP_FLOW_RATE,
            CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(designFlowRate)
            .setMaxValue(designFlowRate * 1.2)
            .setValueSupplier(() -> getInletStream().getFlowRate("m3/hr"));
        capacityConstraints.put(flow.getName(), flow);
        
        // Power constraint
        CapacityConstraint power = new CapacityConstraint(
            StandardConstraintType.PUMP_POWER,
            CapacityConstraint.ConstraintType.HARD)
            .setDesignValue(motorRatedPower)
            .setMaxValue(motorRatedPower * 1.1)  // 110% service factor
            .setValueSupplier(() -> getPower());
        capacityConstraints.put(power.getName(), power);
        
        // NPSH margin constraint (inverted - higher available is better)
        CapacityConstraint npsh = new CapacityConstraint(
            StandardConstraintType.PUMP_NPSH_MARGIN,
            CapacityConstraint.ConstraintType.HARD)
            .setDesignValue(designNPSH * 1.3)  // 30% margin required
            .setMinValue(designNPSH)            // Absolute minimum
            .setValueSupplier(() -> getNPSHavailable());
        capacityConstraints.put(npsh.getName(), npsh);
    }
    
    // ... implement all interface methods as shown above
}
```

### Example: Implementing for Heat Exchanger

```java
public class HeatExchanger extends ProcessEquipmentBaseClass 
    implements CapacityConstrainedEquipment {
    
    private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();
    private double designDuty = 1000.0;        // kW
    private double designApproachTemp = 5.0;   // °C
    
    private void initializeCapacityConstraints() {
        // Duty constraint
        CapacityConstraint duty = new CapacityConstraint(
            StandardConstraintType.HEAT_EXCHANGER_DUTY,
            CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(designDuty)
            .setMaxValue(designDuty * 1.1)  // 10% overdesign typical
            .setValueSupplier(() -> Math.abs(getDuty()) / 1000.0);  // Convert W to kW
        capacityConstraints.put(duty.getName(), duty);
        
        // Approach temperature constraint (inverted - want to stay above minimum)
        CapacityConstraint approach = new CapacityConstraint(
            StandardConstraintType.HEAT_EXCHANGER_APPROACH_TEMP,
            CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(designApproachTemp)
            .setMinValue(2.0)  // Minimum practical approach
            .setValueSupplier(() -> getApproachTemperature());
        capacityConstraints.put(approach.getName(), approach);
        
        // Pressure drop constraint
        CapacityConstraint pressureDrop = new CapacityConstraint(
            StandardConstraintType.HEAT_EXCHANGER_PRESSURE_DROP,
            CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(maxPressureDrop)
            .setMaxValue(maxPressureDrop * 1.5)
            .setValueSupplier(() -> getPressureDrop("bar"));
        capacityConstraints.put(pressureDrop.getName(), pressureDrop);
    }
}
```

### Example: Implementing for Pipe/Pipeline

```java
public class Pipe extends ProcessEquipmentBaseClass 
    implements CapacityConstrainedEquipment {
    
    private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();
    private double erosionalVelocityRatio = 0.8;  // Design at 80% of erosional
    
    private void initializeCapacityConstraints() {
        // Velocity constraint
        CapacityConstraint velocity = new CapacityConstraint(
            StandardConstraintType.PIPE_VELOCITY,
            CapacityConstraint.ConstraintType.DESIGN)
            .setDesignValue(calculateErosionalVelocity() * erosionalVelocityRatio)
            .setMaxValue(calculateErosionalVelocity())
            .setValueSupplier(() -> getFluidVelocity());
        capacityConstraints.put(velocity.getName(), velocity);
        
        // Erosional velocity ratio constraint
        CapacityConstraint erosional = new CapacityConstraint(
            StandardConstraintType.PIPE_EROSIONAL_VELOCITY,
            CapacityConstraint.ConstraintType.HARD)
            .setDesignValue(erosionalVelocityRatio)
            .setMaxValue(1.0)  // Never exceed erosional velocity
            .setValueSupplier(() -> getFluidVelocity() / calculateErosionalVelocity());
        capacityConstraints.put(erosional.getName(), erosional);
        
        // Pressure drop constraint
        CapacityConstraint dp = new CapacityConstraint(
            StandardConstraintType.PIPE_PRESSURE_DROP,
            CapacityConstraint.ConstraintType.SOFT)
            .setDesignValue(allowablePressureDrop)
            .setMaxValue(allowablePressureDrop * 1.2)
            .setValueSupplier(() -> getPressureDrop("bar"));
        capacityConstraints.put(dp.getName(), dp);
    }
}
```

## StandardConstraintType Reference

| Category | Type | Name | Unit | Description |
|----------|------|------|------|-------------|
| **Separator** | `SEPARATOR_GAS_LOAD_FACTOR` | gasLoadFactor | m/s | Souders-Brown K-factor |
| | `SEPARATOR_LIQUID_RESIDENCE_TIME` | liquidResidenceTime | s | Liquid hold-up time |
| | `SEPARATOR_LIQUID_LEVEL` | liquidLevel | % | Level as % of capacity |
| **Compressor** | `COMPRESSOR_SPEED` | speed | RPM | Rotational speed |
| | `COMPRESSOR_POWER` | power | kW | Shaft power |
| | `COMPRESSOR_SURGE_MARGIN` | surgeMargin | % | Distance to surge |
| | `COMPRESSOR_DISCHARGE_TEMP` | dischargeTemperature | °C | Discharge temperature |
| | `COMPRESSOR_PRESSURE_RATIO` | pressureRatio | - | Compression ratio |
| **Pump** | `PUMP_FLOW_RATE` | flowRate | m³/hr | Volumetric flow |
| | `PUMP_HEAD` | head | m | Developed head |
| | `PUMP_POWER` | power | kW | Shaft power |
| | `PUMP_NPSH_MARGIN` | npshMargin | m | NPSH available margin |
| **Heat Exchanger** | `HEAT_EXCHANGER_DUTY` | duty | kW | Heat transfer rate |
| | `HEAT_EXCHANGER_APPROACH_TEMP` | approachTemperature | °C | Minimum ΔT |
| | `HEAT_EXCHANGER_PRESSURE_DROP` | pressureDrop | bar | Pressure loss |
| **Valve** | `VALVE_CV_UTILIZATION` | cvUtilization | % | Cv used / Cv available |
| | `VALVE_PRESSURE_DROP` | pressureDrop | bar | Pressure loss |
| **Pipe** | `PIPE_VELOCITY` | velocity | m/s | Fluid velocity |
| | `PIPE_EROSIONAL_VELOCITY` | erosionalVelocityRatio | - | v/v_erosional ratio |
| | `PIPE_PRESSURE_DROP` | pressureDrop | bar/km | Pressure gradient |

## Best Practices

### 1. Choose Appropriate Constraint Types

- Use `HARD` for absolute limits that cause trips or damage
- Use `SOFT` for operational limits affecting efficiency
- Use `DESIGN` for normal operating envelope limits

### 2. Set Meaningful Design Values

Design values should represent the intended operating point, not the maximum:
```java
// Good: Design at normal operation, max at limit
constraint.setDesignValue(10000.0);  // Normal speed
constraint.setMaxValue(11000.0);     // Trip speed

// Bad: Design equals maximum
constraint.setDesignValue(11000.0);  // No warning margin
```

### 3. Use Warning Thresholds

The default warning threshold is 90%. Adjust if needed:
```java
constraint.setWarningThreshold(0.85);  // Warn at 85% utilization
```

### 4. Handle Missing Data Gracefully

The valueSupplier should return `Double.NaN` for unavailable data:
```java
.setValueSupplier(() -> {
    if (compressorMap == null) return Double.NaN;
    return compressor.getSpeed();
});
```

### 5. Update Constraints When Design Changes

Ensure constraints stay synchronized with design parameters:
```java
public void setDesignSpeed(double speed) {
    this.designSpeed = speed;
    CapacityConstraint c = capacityConstraints.get("speed");
    if (c != null) {
        c.setDesignValue(speed);
    }
}
```

## Integration with OilGasProcessSimulationOptimization

The example simulation class demonstrates integration:

```java
// After running the process
ProcessOutputResults results = simulation.getOutput();

// Check separator capacity
if (results.isAnySeparatorOverloaded()) {
    System.out.println("Separator capacity exceeded!");
    for (Map.Entry<String, Double> e : results.getSeparatorCapacityUtilization().entrySet()) {
        if (e.getValue() > 100.0) {
            System.out.printf("  %s at %.1f%%\n", e.getKey(), e.getValue());
        }
    }
}

// Check compressor speed limits
if (results.isAnyCompressorOverspeed()) {
    System.out.println("Compressor speed limit exceeded!");
}

// Use ProcessSystem methods for deeper analysis
ProcessSystem process = simulation.getProcess();
BottleneckResult bottleneck = process.findBottleneck();
```

## See Also

- [Process Equipment Documentation](../process/README.md)
- [Mechanical Design Framework](../process/MECHANICAL_DESIGN_FRAMEWORK.md)
- [Optimization Examples](../examples/index.md)
