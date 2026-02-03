---
title: Capacity Constraint Framework
description: The Capacity Constraint Framework extends NeqSim's existing bottleneck analysis capability with multi-constraint support. It provides:
---

# Capacity Constraint Framework

## Overview

The Capacity Constraint Framework extends NeqSim's existing bottleneck analysis capability with multi-constraint support. It provides:

- **Multiple constraints per equipment**: Track speed, power, surge margin, temperature, etc. simultaneously
- **Constraint types**: HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope)
- **Warning thresholds**: Early warning when approaching limits
- **Integration with ProductionOptimizer**: Works seamlessly with existing optimization tools

## Important: Constraints Disabled by Default

> **⚠️ Key Behavior**: All separator, valve, pipeline, pump, and manifold constraints are **disabled by default** for backward compatibility. The optimizer checks whether any constraints are enabled before using the `CapacityConstrainedEquipment` interface.

### Why Constraints Are Disabled by Default

To maintain backward compatibility with existing simulations, constraints are created but **not enabled** when equipment is initialized. This ensures that:

1. **Existing code works unchanged** - Simulations that don't use capacity analysis continue to work
2. **Explicit opt-in for capacity analysis** - You must explicitly enable constraints to use them
3. **No unexpected optimization failures** - Optimizer falls back to traditional methods if no constraints are enabled

### How to Enable Constraints

```java
// Method 1: Use pre-configured constraint sets (Separator example)
Separator separator = new Separator("HP Separator", feed);
separator.useEquinorConstraints();  // Enables K-value, droplet, momentum, retention times
// OR
separator.useAPIConstraints();      // Enables K-value and retention times per API 12J
// OR
separator.useAllConstraints();      // Enables all 5 constraint types

// Method 2: Enable individual constraints
separator.getConstraints().get(StandardConstraintType.SEPARATOR_K_VALUE).setEnabled(true);

// Method 3: Enable all constraints at once
separator.enableConstraints();      // Enables all constraints on this equipment

// Method 4: Disable constraints (return to default)
separator.disableConstraints();     // Disables all constraints
```

### How the Optimizer Uses Constraints

The `ProductionOptimizer` uses a smart fallback mechanism:

```java
// In determineCapacityRule(), the optimizer checks:
boolean hasEnabledConstraints = constrained.getCapacityConstraints().values().stream()
    .anyMatch(CapacityConstraint::isEnabled);

if (constrained.isCapacityAnalysisEnabled() && hasEnabledConstraints) {
    // Use multi-constraint capacity analysis
    return new ConstrainedCapacityRule(equipment);
} else {
    // Fall back to traditional getCapacityMax()/getCapacityDuty()
    return new TraditionalCapacityRule(equipment);
}
```

### Summary: Constraint Enablement by Equipment Type

| Equipment Type | Default State | How to Enable |
|---------------|---------------|---------------|
| **Separator** | All disabled | `useEquinorConstraints()`, `useAPIConstraints()`, `enableConstraints()` |
| **ThreePhaseSeparator** | All disabled | Same as Separator |
| **GasScrubber** | K-value only enabled | `useGasScrubberConstraints()` (automatic in constructor) |
| **Compressor** | All enabled | (constraints created by `autoSize()` are enabled by default) |
| **ThrottlingValve** | All disabled | `enableConstraints()` |
| **Pipeline** | All disabled | `enableConstraints()` |
| **Pump** | All disabled | `enableConstraints()` |
| **Manifold** | All disabled | `enableConstraints()` |

---

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
│                         ProcessModule                                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  getConstrainedEquipment() │ findBottleneck() │ ...            │ │
│  │  (recursively searches all nested modules and systems)         │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌──────────────────────┐       │
│  │    ProcessModule     │             │    ProcessSystem     │       │
│  │    (nested)          │             │                      │       │
│  └──────────────────────┘             └──────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘

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

## Integration with AutoSizing and Mechanical Design

### How AutoSizing Creates Constraints

When equipment is auto-sized using the `AutoSizeable` interface, constraints are automatically created based on the calculated design values:

```java
// Auto-sizing creates constraints automatically
Separator sep = new Separator("HP-Sep", feedStream);
sep.autoSize(1.2);  // 20% safety factor

// This creates the following constraints:
// - gasLoadFactor: based on K-factor sizing calculation
// - liquidResidenceTime: based on L/D ratio and liquid level

// For compressors, autoSize does even more:
Compressor comp = new Compressor("Export", gasStream);
comp.setOutletPressure(100.0);
comp.autoSize(1.2);

// This creates:
// - speed constraint (from mechanical design)
// - power constraint (from driver sizing)
// - surgeMargin constraint (soft limit)
// AND generates compressor curves, sets solveSpeed=true
```

### Mechanical Design as Source of Constraint Values

The `MechanicalDesign` class provides design values that become constraint limits:

```java
// Mechanical design values feed constraints
Separator sep = new Separator("V-100", feed);
sep.initMechanicalDesign();
SeparatorMechanicalDesign mechDesign = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

// Set design limits that will become constraints
mechDesign.setMaxDesignGassVolFlow(5000.0);    // m³/hr → volumeFlow constraint
mechDesign.setMaxDesignPressureDrop(2.0);      // bara → pressureDrop constraint

// For pipelines
Pipeline pipe = new PipeBeggsAndBrills("L-100", gasStream);
pipe.initMechanicalDesign();
PipelineMechanicalDesign pipeDesign = (PipelineMechanicalDesign) pipe.getMechanicalDesign();

// Design values → constraints
pipeDesign.maxDesignVelocity = 15.0;           // → velocity constraint
pipeDesign.maxDesignPressureDrop = 5.0;        // → pressureDrop constraint
pipeDesign.maxDesignVolumeFlow = 10000.0;      // → volumeFlow constraint
```

### Complete Workflow: Design → Constraints → Optimization

```java
// 1. Create process with auto-sizing
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
process.add(feed);

Separator sep = new Separator("HP-Sep", feed);
sep.autoSize(1.2);  // Creates gasLoadFactor constraint
process.add(sep);

Compressor comp = new Compressor("K-100", sep.getGasOutStream());
comp.setOutletPressure(100.0);
comp.autoSize(1.2);  // Creates speed, power, surge constraints + curves
process.add(comp);

Pipeline pipe = new PipeBeggsAndBrills("Export", comp.getOutletStream());
pipe.setLength(30000.0);
pipe.setDiameter(0.3);
pipe.autoSize(1.2);  // Creates velocity, pressureDrop, FIV constraints
process.add(pipe);

// 2. Run process
process.run();

// 3. Constraints are now active and can be queried
System.out.println("Equipment constraints after auto-sizing:");
for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    System.out.println(((ProcessEquipmentInterface) equip).getName() + ":");
    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        System.out.printf("  %s: %.2f / %.2f %s%n",
            c.getName(), c.getCurrentValue(), c.getDesignValue(), c.getUnit());
    }
}

// 4. Use in optimization - optimizer checks ALL constraints
ProductionOptimizer.OptimizationConfig config = 
    new ProductionOptimizer.OptimizationConfig(1000.0, 50000.0);
ProductionOptimizer.OptimizationResult result = 
    ProductionOptimizer.optimize(process, feed, config);

// 5. The bottleneck could be separator, compressor, or pipeline
System.out.println("Bottleneck: " + result.getBottleneck().getName());
```

### Equipment Currently Supporting CapacityConstrainedEquipment

| Equipment | Constraints | Set By |
|-----------|-------------|--------|
| **Separator** | gasLoadFactor, liquidResidenceTime | autoSize(), setDesignGasLoadFactor() |
| **Compressor** | speed, power, ratedPower, surgeMargin, stonewallMargin | autoSize(), setMaximumSpeed(), setMaximumPower() |
| **Pump** | npshMargin, power, flowRate | setMaximumPower(), mechanical design |
| **ThrottlingValve** | valveOpening, cvUtilization, AIV | autoSize(), setCv(), setMaxDesignAIV() |
| **Pipeline** | velocity, pressureDrop, volumeFlow, FIV_LOF, FIV_FRMS | autoSize(), setMaxLOF(), setMaxFRMS() |
| **PipeBeggsAndBrills** | velocity, LOF, FRMS, AIV | autoSize(), setMaxDesignVelocity(), setMaxDesignLOF(), setMaxDesignAIV() |
| **AdiabaticPipe** | velocity, LOF, FRMS, AIV, pressureDrop | autoSize(), setMaxDesignVelocity(), setMaxDesignLOF(), setMaxDesignAIV() |
| **Manifold** | headerVelocity, branchVelocity, headerLOF, headerFRMS, branchLOF, branchFRMS | autoSize(), setMaxDesignVelocity() |
| **Heater/Cooler** | duty, outletTemperature | autoSize(), setMaxDesignDuty() |

### How to Override autoSize Constraints

After `autoSize()` creates constraints, you can override them:

```java
// 1. Override BEFORE autoSize (parameter will be used in sizing)
separator.setDesignGasLoadFactor(0.15);  // Your K-factor
separator.autoSize(1.2);                  // Uses your K-factor

// 2. Override AFTER autoSize (keeps sizing, changes constraint limit)
compressor.autoSize(1.2);
compressor.setMaximumPower(6000.0);       // Override constraint limit (kW)
compressor.setMaximumSpeed(12000.0);      // Override speed limit (RPM)

// 3. Manually set constraint on existing equipment
CapacityConstraint customPower = new CapacityConstraint("powerLimit", ConstraintType.HARD)
    .setDesignValue(5000.0)
    .setUnit("kW")
    .setValueSupplier(() -> compressor.getPower("kW"));
compressor.addCapacityConstraint(customPower);

// 4. Remove auto-generated constraint and add custom one
compressor.removeCapacityConstraint("power");  // Remove default
compressor.addCapacityConstraint(customPower); // Add custom
```

### Constraint Priority After Override

When you override a constraint parameter, the priority is:
1. **User-specified value** (highest) - via setter methods
2. **autoSize calculated value** - based on flow conditions
3. **Mechanical design default** - from design standards
4. **Hard-coded default** (lowest) - in equipment class

---

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

### ProcessModule Support

The capacity constraint framework also works with `ProcessModule`, which can contain multiple `ProcessSystem` instances and nested modules. All constraint methods work recursively across the entire module hierarchy.

```java
import neqsim.process.processmodel.ProcessModule;

// Create a complex module with multiple systems
ProcessModule productionModule = new ProcessModule("Production Platform");

// Add process systems
ProcessSystem separationSystem = new ProcessSystem();
separationSystem.add(inletManifold);
separationSystem.add(hpSeparator);
separationSystem.add(lpSeparator);

ProcessSystem compressionSystem = new ProcessSystem();
compressionSystem.add(lpCompressor);
compressionSystem.add(hpCompressor);
compressionSystem.add(exportPipeline);

productionModule.add(separationSystem);
productionModule.add(compressionSystem);
productionModule.run();

// Find bottleneck across ALL systems in the module
BottleneckResult bottleneck = productionModule.findBottleneck();
if (bottleneck.hasBottleneck()) {
    System.out.println("Module bottleneck: " + bottleneck.getEquipmentName());
    System.out.println("Constraint: " + bottleneck.getConstraint().getName());
    System.out.println("Utilization: " + bottleneck.getUtilizationPercent() + "%");
}

// Check for overloaded equipment across all systems
if (productionModule.isAnyEquipmentOverloaded()) {
    System.out.println("WARNING: Equipment overloaded in module!");
}

// Get all constrained equipment from the module
List<CapacityConstrainedEquipment> allConstrained = 
    productionModule.getConstrainedEquipment();
System.out.println("Found " + allConstrained.size() + " constrained equipment items");

// Get utilization summary across entire module
Map<String, Double> utilization = productionModule.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    System.out.printf("%s: %.1f%%\n", entry.getKey(), entry.getValue());
}
```

#### Nested Module Support

ProcessModule supports nesting, and constraint methods work recursively:

```java
// Create nested modules
ProcessModule topside = new ProcessModule("Topside");
ProcessModule subsea = new ProcessModule("Subsea");

subsea.add(subseaManifold);
subsea.add(flowlines);
subsea.add(risers);

topside.add(separationSystem);
topside.add(compressionSystem);

// Create master module containing both
ProcessModule field = new ProcessModule("Field Development");
field.add(subsea);
field.add(topside);
field.run();

// findBottleneck() searches recursively through ALL nested modules
BottleneckResult fieldBottleneck = field.findBottleneck();

// All constraint methods work recursively
List<CapacityConstrainedEquipment> allEquipment = field.getConstrainedEquipment();
Map<String, Double> fieldUtilization = field.getCapacityUtilizationSummary();
boolean anyOverloaded = field.isAnyEquipmentOverloaded();
boolean hardLimitExceeded = field.isAnyHardLimitExceeded();
```

#### ProcessModule Constraint Methods

| Method | Description |
|--------|-------------|
| `getConstrainedEquipment()` | Returns all equipment implementing CapacityConstrainedEquipment from all systems and nested modules |
| `findBottleneck()` | Finds the equipment with highest utilization across entire module hierarchy |
| `isAnyEquipmentOverloaded()` | Checks if any equipment exceeds design capacity (utilization > 100%) |
| `isAnyHardLimitExceeded()` | Checks if any HARD constraint limits are exceeded |
| `getCapacityUtilizationSummary()` | Returns Map<String, Double> of equipment name to utilization percentage |
| `getEquipmentNearCapacityLimit()` | Returns list of equipment names near warning threshold |

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

---

## Activating and Deactivating Constraints

### Adding Constraints to Equipment WITH Existing Constraints

Equipment that already implements `CapacityConstrainedEquipment` (Separator, Compressor, Pipeline, Manifold, etc.) can have additional constraints added:

```java
// Equipment already has default constraints from autoSize() or initialization
Compressor compressor = new Compressor("Export Compressor", feed);
compressor.setOutletPressure(150.0);
compressor.autoSize(1.2);  // Creates speed, power, surgeMargin constraints
compressor.run();

// View existing constraints
System.out.println("Current constraints:");
for (CapacityConstraint c : compressor.getCapacityConstraints().values()) {
    System.out.println("  " + c.getName() + ": " + c.getDesignValue() + " " + c.getUnit());
}

// ADD a new custom constraint (discharge temperature)
CapacityConstraint tempLimit = new CapacityConstraint("dischargeTemp", ConstraintType.SOFT)
    .setDesignValue(150.0)  // °C design limit
    .setMaxValue(180.0)     // °C absolute max
    .setUnit("°C")
    .setWarningThreshold(0.9)
    .setValueSupplier(() -> compressor.getOutletStream().getTemperature("C"));

compressor.addCapacityConstraint(tempLimit);

// MODIFY an existing constraint's design value
CapacityConstraint speedConstraint = compressor.getCapacityConstraints().get("speed");
if (speedConstraint != null) {
    speedConstraint.setDesignValue(9500.0);  // Lower speed limit
    speedConstraint.setMaxValue(10000.0);
}
```

### Adding Constraints to Equipment WITHOUT Existing Constraints

For equipment that does not implement `CapacityConstrainedEquipment`, you need to either:

1. **Use the Strategy Registry** (recommended for temporary/external constraints):

```java
// Equipment without built-in constraints
Heater heater = new Heater("Process Heater", feed);
heater.setOutTemperature(350.0);

// Use strategy registry to add constraint evaluation
EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();

// Create custom strategy for heater
EquipmentCapacityStrategy heaterStrategy = new EquipmentCapacityStrategy() {
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        return equipment instanceof Heater && equipment.getName().equals("Process Heater");
    }
    
    @Override
    public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
        Heater h = (Heater) equipment;
        Map<String, CapacityConstraint> constraints = new LinkedHashMap<>();
        
        constraints.put("duty", new CapacityConstraint("duty", ConstraintType.DESIGN)
            .setDesignValue(5000.0)  // kW
            .setMaxValue(6000.0)
            .setUnit("kW")
            .setValueSupplier(() -> Math.abs(h.getDuty()) / 1000.0));
        
        return constraints;
    }
    // ... implement other interface methods
};

registry.registerStrategy(heaterStrategy);
```

2. **Extend the equipment class** (for permanent constraints):

```java
// Create subclass with constraint support
public class ConstrainedHeater extends Heater implements CapacityConstrainedEquipment {
    private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<>();
    
    public ConstrainedHeater(String name, StreamInterface inletStream) {
        super(name, inletStream);
        initializeCapacityConstraints();
    }
    
    protected void initializeCapacityConstraints() {
        addCapacityConstraint(new CapacityConstraint("duty", ConstraintType.DESIGN)
            .setDesignValue(5000.0)
            .setUnit("kW")
            .setValueSupplier(() -> Math.abs(getDuty()) / 1000.0));
    }
    
    // Implement CapacityConstrainedEquipment interface methods...
}
```

### Deactivating (Removing) Constraints

```java
// Remove a specific constraint by name
compressor.removeCapacityConstraint("surgeMargin");

// Remove multiple constraints
compressor.removeCapacityConstraint("stonewallMargin");
compressor.removeCapacityConstraint("dischargeTemp");

// Remove ALL constraints (equipment will no longer be capacity-limited)
compressor.clearCapacityConstraints();

// Re-initialize default constraints after clearing
compressor.initializeCapacityConstraints();  // If method is public/protected
```

### Temporarily Disabling Constraints

For scenarios where you want to keep constraints defined but temporarily ignore them:

```java
// Option 1: Set design value to very high (effectively disabling)
CapacityConstraint speedConstraint = compressor.getCapacityConstraints().get("speed");
double originalDesign = speedConstraint.getDesignValue();
speedConstraint.setDesignValue(Double.MAX_VALUE);  // Disable

// ... run optimization without speed constraint ...

speedConstraint.setDesignValue(originalDesign);  // Re-enable

// Option 2: Store and remove, then re-add
CapacityConstraint removedConstraint = compressor.getCapacityConstraints().get("power");
compressor.removeCapacityConstraint("power");

// ... run optimization without power constraint ...

compressor.addCapacityConstraint(removedConstraint);  // Re-add
```

---

## Constraints in Eclipse VFP Table Generation

When generating VFP (Vertical Flow Performance) tables for Eclipse reservoir simulation, capacity constraints determine the **maximum feasible flow rates** at each operating point.

### How Constraints Affect VFP Tables

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       VFP Table Generation Process                       │
├─────────────────────────────────────────────────────────────────────────┤
│  For each (inlet_pressure, outlet_pressure, temperature, WC, GOR):      │
│                                                                          │
│  1. Set process boundary conditions                                      │
│  2. Binary search for maximum flow rate where:                           │
│     - Process converges (thermodynamically feasible)                     │
│     - ALL capacity constraints satisfied (utilization ≤ 1.0)             │
│     - No HARD limit exceeded                                             │
│                                                                          │
│  3. Record: flow_rate, BHP (or THP), bottleneck_equipment                │
└─────────────────────────────────────────────────────────────────────────┘
```

### VFP Generation with Constraint Checking

```java
import neqsim.process.util.optimizer.EclipseVFPExporter;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

// Create process and optimization engine
ProcessSystem process = createProductionProcess();
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Define VFP table parameters
double[] inletPressures = {60.0, 70.0, 80.0, 90.0, 100.0};  // Wellhead (bara)
double[] outletPressures = {40.0, 50.0, 60.0};              // Separator (bara)
double[] waterCuts = {0.0, 0.2, 0.4, 0.6};
double[] gors = {100.0, 300.0, 500.0};

// Generate VFP with constraint-limited flow rates
EclipseVFPExporter exporter = new EclipseVFPExporter(process);
exporter.setTableNumber(1);
exporter.setConstraintEnforcement(true);  // Enable constraint checking

// Each cell in the VFP table will contain the MAXIMUM flow rate
// that satisfies ALL equipment constraints
String vfpTable = exporter.generateVFPPROD(
    inletPressures,
    waterCuts,
    gors,
    new double[]{0.0},  // No artificial lift
    new double[]{5000, 10000, 20000, 50000, 100000, 150000},  // Test flow rates
    "bara",
    "kg/hr"
);

// The VFP table will show:
// - Flow rate = 0 if no feasible operation at that point
// - Flow rate = max achievable if constrained by equipment
// - Flow rate = tested max if all constraints satisfied
```

### Understanding Constraint Impact on VFP

```java
// Example: Analyze how each constraint affects maximum flow at one operating point
double inletP = 80.0;   // bara
double outletP = 50.0;  // bara

// Find maximum flow WITH all constraints
OptimizationResult withConstraints = engine.findMaximumThroughput(
    inletP, outletP, 1000.0, 200000.0);
System.out.println("Max flow (all constraints): " + withConstraints.getOptimalFlowRate());
System.out.println("Bottleneck: " + withConstraints.getBottleneckEquipment());

// Temporarily remove compressor speed constraint
Compressor comp = (Compressor) process.getUnit("Export Compressor");
CapacityConstraint speedLimit = comp.getCapacityConstraints().get("speed");
comp.removeCapacityConstraint("speed");

OptimizationResult noSpeedLimit = engine.findMaximumThroughput(
    inletP, outletP, 1000.0, 200000.0);
System.out.println("Max flow (no speed limit): " + noSpeedLimit.getOptimalFlowRate());

// Restore constraint
comp.addCapacityConstraint(speedLimit);

// Calculate flow increase if speed limit removed
double flowIncrease = noSpeedLimit.getOptimalFlowRate() - withConstraints.getOptimalFlowRate();
System.out.println("Flow increase if speed debottlenecked: " + flowIncrease + " kg/hr");
```

### Modifying Constraints for What-If VFP Studies

```java
// Study: How does upgrading separator affect production capacity?

// Baseline VFP
String baselineVFP = exporter.generateVFPPROD(...);
Files.writeString(Path.of("VFP_BASELINE.INC"), baselineVFP);

// Upgraded separator (higher gas load factor)
Separator sep = (Separator) process.getUnit("HP Separator");
CapacityConstraint gasLoad = sep.getCapacityConstraints().get("gasLoadFactor");
double originalDesign = gasLoad.getDesignValue();
gasLoad.setDesignValue(originalDesign * 1.5);  // 50% larger separator

String upgradedVFP = exporter.generateVFPPROD(...);
Files.writeString(Path.of("VFP_UPGRADED_SEPARATOR.INC"), upgradedVFP);

// Restore original
gasLoad.setDesignValue(originalDesign);
```

### Constraint-Aware Lift Curve Generation

```java
// Generate lift curves showing which constraint limits each point
ProcessOptimizationEngine.LiftCurveData liftCurve = engine.generateLiftCurve(
    inletPressures, outletPressures, temperatures, waterCuts, gors);

// Access detailed results
for (LiftCurvePoint point : liftCurve.getPoints()) {
    System.out.printf("Pin=%.0f, Pout=%.0f, WC=%.1f, GOR=%.0f: " +
                      "MaxFlow=%.0f kg/hr, Limited by: %s%n",
        point.getInletPressure(),
        point.getOutletPressure(),
        point.getWaterCut(),
        point.getGOR(),
        point.getMaxFlowRate(),
        point.getBottleneckConstraint()  // e.g., "Compressor:speed"
    );
}
```

### Summary: Constraint Management for VFP Tables

| Action | Method | Effect on VFP |
|--------|--------|---------------|
| **Add constraint** | `equipment.addCapacityConstraint(c)` | Lower max flow rates |
| **Remove constraint** | `equipment.removeCapacityConstraint(name)` | Higher max flow rates |
| **Tighten constraint** | `constraint.setDesignValue(lower)` | Lower max flow rates |
| **Relax constraint** | `constraint.setDesignValue(higher)` | Higher max flow rates |
| **Disable all** | `equipment.clearCapacityConstraints()` | Unconstrained (thermodynamic only) |
| **What-if study** | Modify → generate VFP → restore | Compare scenarios |

---

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
| **Compressor** | `COMPRESSOR_SPEED` | speed | RPM | Maximum rotational speed |
| | `COMPRESSOR_MIN_SPEED` | minSpeed | RPM | Minimum stable speed (from curve) |
| | `COMPRESSOR_POWER` | power | % | Power utilization vs speed-dependent driver limit |
| | (custom) | ratedPower | % | Power utilization vs driver rated power |
| | `COMPRESSOR_SURGE_MARGIN` | surgeMargin | % | Distance to surge |
| | `COMPRESSOR_STONEWALL_MARGIN` | stonewallMargin | % | Distance to stonewall |
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
| | `VALVE_AIV` | AIV | kW | Acoustic-induced vibration power |
| **Pipe** | `PIPE_VELOCITY` | velocity | m/s | Fluid velocity |
| | `PIPE_EROSIONAL_VELOCITY` | erosionalVelocityRatio | - | v/v_erosional ratio |
| | `PIPE_PRESSURE_DROP` | pressureDrop | bar/km | Pressure gradient |
| | `PIPE_AIV` | AIV | kW | Acoustic-induced vibration power |

**Notes:**
- **COMPRESSOR_MIN_SPEED**: This is a "minimum constraint" - utilization is calculated as `minSpeed / currentSpeed`. Values < 1.0 mean operating safely above minimum; values > 1.0 mean operating below minimum (violation).
- **COMPRESSOR_POWER**: Utilization vs speed-dependent max power from driver curve. Shows actual operating margin at current speed. 100% means the driver is at its maximum power output at the current speed.
- **ratedPower**: Utilization vs driver's rated power (for capacity planning). Shows what fraction of the motor's full rating is being used, regardless of current speed.
- **COMPRESSOR_SURGE_MARGIN** and **COMPRESSOR_STONEWALL_MARGIN**: Utilization is calculated as `1 / (1 + marginRatio)` where margin = 0 gives 100% utilization.

## Flow-Induced Vibration (FIV) Analysis

Pipeline equipment (`Pipeline`, `PipeBeggsAndBrills`, `AdiabaticPipe`, `Manifold`) includes built-in FIV analysis capabilities with constraints based on industry standards.

### FIV Metrics

| Metric | Description | Risk Threshold |
|--------|-------------|----------------|
| **LOF** (Likelihood of Failure) | Dimensionless indicator based on density, velocity, GVF, and support stiffness | > 0.6 = High risk |
| **FRMS** | RMS force per meter (N/m) - dynamic loading indicator | > 500 N/m = High risk |
| **Erosional Velocity** | Maximum velocity per API RP 14E: Ve = C/√ρ | > 100% = Erosion risk |

### Support Arrangement Coefficients

The LOF calculation uses support arrangement coefficients per industry practice:

| Support Type | Coefficient | Description |
|--------------|-------------|-------------|
| Stiff | 1.0 | Rigid supports, short spans |
| Medium stiff | 1.5 | Standard pipe racks |
| Medium | 2.0 | Longer spans, typical offshore |
| Flexible | 3.0 | Flexible supports, risers |

### Using FIV Analysis

```java
// Pipeline with FIV constraints
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Export Line", feed);
pipe.setLength(5000.0);
pipe.setDiameter(0.2032);  // 8 inch
pipe.setThickness(0.008);   // 8mm wall
pipe.setSupportArrangement("Medium stiff");
pipe.run();

// Get FIV metrics
double lof = pipe.calculateLOF();
double frms = pipe.calculateFRMS();
double erosionalVel = pipe.getErosionalVelocity();
double actualVel = pipe.getMixtureVelocity();

System.out.printf("LOF: %.3f (Risk: %s)%n", lof, lof > 0.6 ? "HIGH" : "Low");
System.out.printf("FRMS: %.1f N/m%n", frms);
System.out.printf("Velocity: %.2f / %.2f m/s (%.1f%% of erosional)%n",
    actualVel, erosionalVel, 100 * actualVel / erosionalVel);

// Get full FIV analysis as Map
Map<String, Object> fivAnalysis = pipe.getFIVAnalysis();

// Get FIV analysis as JSON
String fivJson = pipe.getFIVAnalysisJson();
```

### FIV Analysis Output (JSON)

```json
{
  "LOF": 0.234,
  "LOF_risk": "Low",
  "FRMS_N_per_m": 125.6,
  "FRMS_risk": "Low",
  "mixtureDensity_kg_m3": 85.2,
  "mixtureVelocity_m_s": 12.4,
  "erosionalVelocity_m_s": 18.5,
  "velocityRatio": 0.67,
  "gasVolumeFraction": 0.92,
  "supportArrangement": "Medium stiff",
  "supportCoefficient": 1.5,
  "innerDiameter_m": 0.1872
}
```

### Manifold FIV Analysis

Manifolds provide separate FIV analysis for header and branch lines:

```java
Manifold manifold = new Manifold("Production Manifold", inlet1, inlet2);
manifold.setSplitNumber(3);
manifold.setMaxDesignVelocity(15.0);
manifold.setInnerHeaderDiameter(0.3);
manifold.setInnerBranchDiameter(0.15);
manifold.run();

// Header FIV
double headerLOF = manifold.calculateHeaderLOF();
double headerFRMS = manifold.calculateHeaderFRMS();

// Branch FIV (uses average branch flow)
double branchLOF = manifold.calculateBranchLOF();

// All constraints
Map<String, CapacityConstraint> constraints = manifold.getCapacityConstraints();
// Contains: headerVelocity, branchVelocity, headerLOF, headerFRMS, branchLOF
```

### FIV Design Limits

Set design limits for FIV constraints:

```java
// Set maximum allowable values
pipe.setMaxDesignVelocity(15.0);  // m/s
pipe.setMaxDesignLOF(0.5);        // dimensionless
pipe.setMaxDesignFRMS(400.0);     // N/m

// These become constraint design values
CapacityConstraint lofConstraint = pipe.getCapacityConstraints().get("LOF");
// lofConstraint.getDesignValue() returns 0.5
```

**Note:** The setter methods (`setMaxDesignVelocity`, `setMaxDesignLOF`, `setMaxDesignFRMS`, `setMaxDesignAIV`) automatically invalidate cached constraints, so the new values take effect immediately when `getCapacityConstraints()` is called. If you need to explicitly reinitialize constraints after other changes, call `pipe.reinitializeCapacityConstraints()`.

## Acoustic-Induced Vibration (AIV) Analysis

AIV is caused by high acoustic energy generated by pressure-reducing devices (valves, orifices) and is particularly relevant for high-pressure gas systems. Unlike FIV (which relates to liquid slugging), AIV is critical for dry gas systems.

### AIV Formula (Energy Institute Guidelines)

The acoustic power is calculated using the Energy Institute Guidelines formula:

$$W_{acoustic} = 3.2 \times 10^{-9} \cdot \dot{m} \cdot P_1 \cdot \left(\frac{\Delta P}{P_1}\right)^{3.6} \cdot \left(\frac{T}{273.15}\right)^{0.8}$$

Where:
- $W_{acoustic}$ = Acoustic power (kW)
- $\dot{m}$ = Mass flow rate (kg/s)
- $P_1$ = Upstream pressure (Pa)
- $\Delta P$ = Pressure drop (Pa)
- $T$ = Temperature (K)

### AIV Risk Levels

| Acoustic Power (kW) | Risk Level | Action Required |
|---------------------|------------|-----------------|
| < 1 | LOW | No action required |
| 1 - 10 | MEDIUM | Review piping layout |
| 10 - 25 | HIGH | Detailed analysis required |
| > 25 | VERY HIGH | Mitigation required |

### Using AIV Analysis in Pipes

```java
// Pipeline with AIV constraint
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("HP Gas Line", feed);
pipe.setLength(100.0);
pipe.setDiameter(0.2032);  // 8 inch
pipe.setThickness(0.008);   // 8mm wall
pipe.run();

// Get AIV metrics
double aivPower = pipe.calculateAIV();  // kW
double aivLOF = pipe.calculateAIVLikelihoodOfFailure();

System.out.printf("AIV Power: %.2f kW%n", aivPower);
System.out.printf("AIV LOF: %.2f%n", aivLOF);

// Set AIV design limit (default is 25 kW)
pipe.setMaxDesignAIV(10.0);  // kW

// Get FIV analysis (now includes AIV)
Map<String, Object> analysis = pipe.getFIVAnalysis();
// Contains: AIV_power_kW, AIV_risk, AIV_LOF
```

### Using AIV Analysis in Valves

Throttling valves are primary sources of AIV due to large pressure drops:

```java
// Control valve with significant pressure drop
ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
valve.setOutletPressure(30.0, "bara");  // Large ΔP
valve.run();

// Get AIV metrics
double aivPower = valve.calculateAIV();  // kW

// Calculate AIV LOF (requires downstream pipe geometry)
double downstreamDiameter = 0.2032;  // 8 inch
double downstreamThickness = 0.008;  // 8mm
double aivLOF = valve.calculateAIVLikelihoodOfFailure(
    downstreamDiameter, downstreamThickness);

System.out.printf("Valve AIV Power: %.2f kW%n", aivPower);
System.out.printf("Valve AIV LOF: %.3f%n", aivLOF);

// Set AIV design limit (default is 10 kW for valves)
valve.setMaxDesignAIV(5.0);  // kW - stricter limit

// Access AIV constraint
CapacityConstraint aivConstraint = valve.getCapacityConstraints().get("AIV");
double utilization = aivConstraint.getUtilization();
```

### AIV Analysis Output (JSON)

The `getFIVAnalysis()` method now includes AIV data:

```json
{
  "LOF": 0.05,
  "LOF_risk": "Low",
  "FRMS_N_per_m": 12.3,
  "FRMS_risk": "Low",
  "AIV_power_kW": 8.45,
  "AIV_risk": "MEDIUM",
  "AIV_LOF": 0.35,
  "mixtureDensity_kg_m3": 45.2,
  "mixtureVelocity_m_s": 18.4,
  "erosionalVelocity_m_s": 25.5,
  "velocityRatio": 0.72,
  "gasVolumeFraction": 0.98,
  "supportArrangement": "Medium stiff",
  "supportCoefficient": 1.5,
  "innerDiameter_m": 0.1872
}
```

### FIV vs AIV: When to Use Each

| Metric | Applicable Systems | Primary Concern |
|--------|-------------------|-----------------|
| **LOF/FRMS** (FIV) | Two-phase flow with liquid slugging | Liquid impacts causing pipe vibration |
| **AIV** | High-pressure gas with pressure drops | Acoustic energy from turbulent flow |

For dry gas systems, AIV is typically more relevant than FIV (LOF/FRMS will be near zero)

---

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

## Equipment Capacity Strategy Registry

The Strategy Registry provides a plugin-based architecture for evaluating equipment capacity constraints without modifying equipment classes. This is useful when:

- Working with equipment that doesn't implement `CapacityConstrainedEquipment`
- Adding custom constraint evaluation logic
- Performing system-wide optimization

### Strategy Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                 EquipmentCapacityStrategyRegistry (Singleton)       │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  findStrategy(equipment)  │  getAllStrategies()                 ││
│  │  registerStrategy(...)    │  getConstraints(equipment)          ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌─────────────────────────────┐│
│  │  Built-in Strategies │             │  Custom Strategies          ││
│  │  CompressorStrategy  │             │  MyEquipmentStrategy        ││
│  │  SeparatorStrategy   │             │  VendorSpecificStrategy     ││
│  │  PumpStrategy        │             │  ...                        ││
│  │  ValveStrategy       │             │                             ││
│  │  PipeStrategy        │             │                             ││
│  │  HeatExchangerStrategy│            │                             ││
│  └──────────────────────┘             └─────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

### Using the Strategy Registry

```java
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;

// Get the singleton registry
EquipmentCapacityStrategyRegistry registry = 
    EquipmentCapacityStrategyRegistry.getInstance();

// Find strategy for a specific equipment
Compressor compressor = (Compressor) process.getUnit("ExportCompressor");
EquipmentCapacityStrategy strategy = registry.findStrategy(compressor);

if (strategy != null) {
    // Evaluate capacity
    double utilization = strategy.evaluateCapacity(compressor);
    System.out.printf("Compressor utilization: %.1f%%\n", utilization * 100);
    
    // Get all constraints
    Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);
    for (CapacityConstraint c : constraints.values()) {
        System.out.printf("  %s: %.2f %s (%.1f%% of design)\n",
            c.getName(), c.getCurrentValue(), c.getUnit(), 
            c.getUtilizationPercent());
    }
    
    // Check for violations
    List<CapacityConstraint> violations = strategy.getViolations(compressor);
    if (!violations.isEmpty()) {
        System.out.println("Constraint violations:");
        for (CapacityConstraint v : violations) {
            System.out.printf("  - %s: %.2f exceeds %.2f\n",
                v.getName(), v.getCurrentValue(), v.getDesignValue());
        }
    }
    
    // Get bottleneck constraint
    CapacityConstraint bottleneck = strategy.getBottleneckConstraint(compressor);
    System.out.println("Bottleneck: " + bottleneck.getName());
}
```

### Creating Custom Strategies

Implement `EquipmentCapacityStrategy` for equipment-specific logic:

```java
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;

public class MyCustomStrategy implements EquipmentCapacityStrategy {
    
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        // Return true if this strategy handles this equipment type
        return equipment instanceof MyCustomEquipment;
    }
    
    @Override
    public int getPriority() {
        // Higher priority = more specific strategy
        return 100;  // Built-in strategies use priority 10
    }
    
    @Override
    public double evaluateCapacity(ProcessEquipmentInterface equipment) {
        MyCustomEquipment eq = (MyCustomEquipment) equipment;
        // Return utilization as 0.0 to 1.0+
        return eq.getCurrentLoad() / eq.getMaxLoad();
    }
    
    @Override
    public Map<String, CapacityConstraint> getConstraints(
            ProcessEquipmentInterface equipment) {
        Map<String, CapacityConstraint> constraints = new LinkedHashMap<>();
        MyCustomEquipment eq = (MyCustomEquipment) equipment;
        
        constraints.put("customLoad", 
            new CapacityConstraint("customLoad", ConstraintType.DESIGN)
                .setDesignValue(eq.getDesignLoad())
                .setMaxValue(eq.getMaxLoad())
                .setUnit("kW")
                .setValueSupplier(() -> eq.getCurrentLoad()));
        
        return constraints;
    }
    
    @Override
    public List<CapacityConstraint> getViolations(
            ProcessEquipmentInterface equipment) {
        List<CapacityConstraint> violations = new ArrayList<>();
        for (CapacityConstraint c : getConstraints(equipment).values()) {
            if (c.isViolated()) {
                violations.add(c);
            }
        }
        return violations;
    }
    
    @Override
    public CapacityConstraint getBottleneckConstraint(
            ProcessEquipmentInterface equipment) {
        CapacityConstraint bottleneck = null;
        double maxUtilization = 0.0;
        for (CapacityConstraint c : getConstraints(equipment).values()) {
            if (c.getUtilization() > maxUtilization) {
                maxUtilization = c.getUtilization();
                bottleneck = c;
            }
        }
        return bottleneck;
    }
    
    @Override
    public boolean isWithinHardLimits(ProcessEquipmentInterface equipment) {
        for (CapacityConstraint c : getConstraints(equipment).values()) {
            if (c.getType() == ConstraintType.HARD && c.isHardLimitExceeded()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean isWithinSoftLimits(ProcessEquipmentInterface equipment) {
        for (CapacityConstraint c : getConstraints(equipment).values()) {
            if (c.getType() == ConstraintType.SOFT && c.isViolated()) {
                return false;
            }
        }
        return true;
    }
}

// Register the custom strategy
registry.registerStrategy(new MyCustomStrategy());
```

### Built-in Strategies

| Strategy | Equipment Type | Constraints Evaluated |
|----------|---------------|----------------------|
| `CompressorCapacityStrategy` | Compressor | speed, power, surgeMargin, stonewallMargin, dischargeTemperature |
| `SeparatorCapacityStrategy` | Separator | liquidLevel, gasLoadFactor |
| `PumpCapacityStrategy` | Pump | power, npshMargin, flowRate |
| `ValveCapacityStrategy` | Valve | valveOpening, pressureDropRatio |
| `PipeCapacityStrategy` | Pipeline | velocity, pressureDrop |
| `HeatExchangerCapacityStrategy` | HeatExchanger | duty, outletTemperature |

For detailed usage and integration with the ProcessOptimizationEngine, see [Optimizer Plugin Architecture](optimization/OPTIMIZER_PLUGIN_ARCHITECTURE).

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

- [Process Equipment Documentation](README)
- [Mechanical Design](mechanical_design)
- [Optimizer Plugin Architecture](optimization/OPTIMIZER_PLUGIN_ARCHITECTURE)
- [Optimization Examples](../examples/index)

