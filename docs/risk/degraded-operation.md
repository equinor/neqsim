# Degraded Operation Optimization

When equipment fails, plants often continue operating at reduced capacity. The `DegradedOperationOptimizer` finds the best operating strategy during equipment outages.

---

## Overview

Instead of a complete shutdown, degraded operation may allow:

- **Partial production** at reduced rate
- **Alternative routing** through parallel equipment
- **Operating mode changes** to maximize output
- **Graceful degradation** until repairs complete

---

## Optimization Objectives

The optimizer can target different objectives:

| Objective | Description |
|-----------|-------------|
| **MAXIMIZE_PRODUCTION** | Get maximum output (default) |
| **MAXIMIZE_REVENUE** | Consider product prices |
| **MINIMIZE_ENERGY** | Reduce energy consumption |
| **MINIMIZE_FLARING** | Reduce environmental impact |
| **MAINTAIN_QUALITY** | Keep product on-spec |

---

## Using DegradedOperationOptimizer

### Basic Usage

```java
// Create optimizer
DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(processSystem);

// Define failure scenario
EquipmentFailureMode failure = EquipmentFailureMode.trip("Compressor A");

// Find optimal degraded operation
DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(failure);

// Apply recommendations
System.out.println("Optimal production: " + result.getOptimalProduction() + " kg/hr");
System.out.println("Operating adjustments:");
for (OperatingAdjustment adj : result.getAdjustments()) {
    System.out.println("  " + adj.getEquipment() + ": " + adj.getAction());
}
```

### With Objective Selection

```java
// Optimize for revenue (considers product prices)
optimizer.setObjective(OptimizationObjective.MAXIMIZE_REVENUE);
optimizer.setProductPrices(Map.of(
    "gas", 500.0,
    "oil", 600.0,
    "condensate", 400.0
));

DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(failure);
```

---

## DegradedOperationResult

The result contains the optimized operating strategy:

```java
public class DegradedOperationResult {
    // Production metrics
    double getNormalProduction();     // Before failure
    double getOptimalProduction();    // Optimized degraded
    double getProductionRecovery();   // % of normal achieved
    
    // Operating adjustments
    List<OperatingAdjustment> getAdjustments();
    
    // Recovery plan
    RecoveryPlan getRecoveryPlan();
    
    // Constraints
    List<OperatingConstraint> getActiveConstraints();
    List<OperatingConstraint> getViolatedConstraints();
}
```

### OperatingAdjustment

```java
public class OperatingAdjustment {
    String getEquipment();           // Equipment to adjust
    String getParameter();           // What to change
    double getCurrentValue();        // Current setting
    double getRecommendedValue();    // New setting
    String getAction();              // Human-readable action
    double getProductionGain();      // Expected improvement
}
```

---

## Optimization Strategies

### 1. Parallel Equipment Redistribution

When one train fails, load is shifted to parallel equipment:

```
Before failure:
  Compressor A: 50% load → Compressor B: 50% load = 100% total

After Compressor A trips:
  Compressor A: 0% load → Compressor B: 100% load = ~95% total*
  
* Limited by maximum capacity
```

```java
// Optimizer automatically handles parallel redistribution
DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(
    EquipmentFailureMode.trip("Compressor A")
);

for (OperatingAdjustment adj : result.getAdjustments()) {
    if (adj.getEquipment().equals("Compressor B")) {
        System.out.println("Increase Compressor B to: " + adj.getRecommendedValue());
    }
}
```

### 2. Feed Rate Reduction

Reduce feed to match available processing capacity:

```java
OperatingAdjustment feedReduction = result.getAdjustments().stream()
    .filter(a -> a.getParameter().equals("feed_rate"))
    .findFirst()
    .orElse(null);

if (feedReduction != null) {
    System.out.println("Reduce feed rate to: " + 
        feedReduction.getRecommendedValue() + " kg/hr");
}
```

### 3. Operating Point Shift

Adjust operating conditions (pressure, temperature) to maximize throughput:

```java
// Find pressure adjustments
for (OperatingAdjustment adj : result.getAdjustments()) {
    if (adj.getParameter().contains("pressure")) {
        System.out.printf("%s: Adjust %s from %.1f to %.1f bar%n",
            adj.getEquipment(),
            adj.getParameter(),
            adj.getCurrentValue(),
            adj.getRecommendedValue());
    }
}
```

### 4. Product Slate Optimization

When multiple products are possible, optimize the product mix:

```java
optimizer.setObjective(OptimizationObjective.MAXIMIZE_REVENUE);

// Different products have different values
optimizer.setProductPrices(Map.of(
    "export_gas", 500.0,    // USD/tonne
    "lpg", 450.0,
    "condensate", 400.0,
    "fuel_gas", 100.0       // Low value
));

// Optimizer may recommend maximizing high-value products
DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(failure);
```

---

## Operating Modes

The optimizer evaluates different operating modes:

```java
// Get available operating modes during outage
List<OperatingMode> modes = optimizer.evaluateOperatingModes(failure);

for (OperatingMode mode : modes) {
    System.out.printf("Mode: %s%n", mode.getName());
    System.out.printf("  Production: %.1f%% of normal%n", mode.getProductionPercent());
    System.out.printf("  Feasible: %s%n", mode.isFeasible());
    if (!mode.isFeasible()) {
        System.out.printf("  Constraint: %s%n", mode.getViolatedConstraint());
    }
}
```

Output:
```
Mode: Full parallel operation
  Production: 95.0% of normal
  Feasible: true

Mode: Reduced throughput
  Production: 70.0% of normal
  Feasible: true

Mode: Bypass mode
  Production: 60.0% of normal
  Feasible: false
  Constraint: Minimum separator pressure not met
```

---

## Recovery Planning

### Recovery Plan Generation

```java
// Generate step-by-step recovery plan
RecoveryPlan plan = optimizer.createRecoveryPlan(failure);

System.out.println("Recovery Plan:");
for (RecoveryStep step : plan.getSteps()) {
    System.out.printf("%d. [%s] %s%n", 
        step.getSequence(),
        step.getTiming(),
        step.getAction());
}
```

Output:
```
Recovery Plan:
1. [Immediate] Reduce feed rate to 15,000 kg/hr
2. [Immediate] Increase Compressor B speed to 95%
3. [Immediate] Open bypass valve VLV-102 to 30%
4. [+15 min] Stabilize separator level at 55%
5. [+30 min] Optimize export pressure to 95 bar
6. [On repair] Restart Compressor A following procedure
7. [+1 hour after restart] Gradually redistribute load to 50/50
```

### RecoveryStep Details

```java
public class RecoveryStep {
    int getSequence();            // Step number
    String getTiming();           // When to execute
    String getAction();           // What to do
    String getEquipment();        // Which equipment
    String getParameter();        // What parameter
    double getTargetValue();      // Target setting
    String getSafetyNote();       // Safety considerations
    boolean requiresOperator();   // Manual action needed?
}
```

---

## Constraints Handling

### Defining Constraints

```java
// Add operating constraints
optimizer.addConstraint(new OperatingConstraint(
    "separator_pressure",
    ConstraintType.MINIMUM,
    30.0,  // bara
    "Separator pressure must stay above 30 bara for liquid recovery"
));

optimizer.addConstraint(new OperatingConstraint(
    "compressor_speed",
    ConstraintType.MAXIMUM,
    105.0,  // % of design
    "Compressor speed limited to 105% for mechanical integrity"
));
```

### Constraint Violations

```java
DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(failure);

if (result.hasViolatedConstraints()) {
    System.out.println("Warning: Some constraints cannot be satisfied:");
    for (OperatingConstraint constraint : result.getViolatedConstraints()) {
        System.out.printf("  %s: %s%n", 
            constraint.getParameter(), 
            constraint.getDescription());
    }
}
```

---

## Multi-Failure Scenarios

### Multiple Equipment Down

```java
// Two compressors down simultaneously
List<EquipmentFailureMode> failures = Arrays.asList(
    EquipmentFailureMode.trip("Compressor A"),
    EquipmentFailureMode.degraded("Compressor C", 0.5)
);

DegradedOperationResult result = optimizer.optimizeWithMultipleFailures(failures);

if (result.getOptimalProduction() == 0) {
    System.out.println("No feasible operating point - recommend shutdown");
} else {
    System.out.println("Partial operation possible at " + 
        result.getProductionRecovery() + "% capacity");
}
```

### Cascading Failures

```java
// Primary failure triggers secondary issues
EquipmentFailureMode primary = EquipmentFailureMode.trip("HP Separator");

// Check for cascade effects
DependencyAnalyzer deps = new DependencyAnalyzer(process, topology);
DependencyResult cascade = deps.analyzeFailure("HP Separator");

// Include cascade in optimization
List<String> affectedEquipment = new ArrayList<>();
affectedEquipment.add("HP Separator");
affectedEquipment.addAll(cascade.getDirectlyAffected());

DegradedOperationResult result = optimizer.optimizeWithEquipmentUnavailable(affectedEquipment);
```

---

## Example: Compressor Outage Optimization

```java
// Complete example
ProcessSystem process = createGasProcessingPlant();

// Create optimizer
DegradedOperationOptimizer optimizer = new DegradedOperationOptimizer(process);
optimizer.setObjective(OptimizationObjective.MAXIMIZE_PRODUCTION);

// Add constraints
optimizer.addConstraint(new OperatingConstraint("export_pressure", MINIMUM, 80.0, "bara"));
optimizer.addConstraint(new OperatingConstraint("compressor_surge_margin", MINIMUM, 10.0, "%"));

// Simulate compressor trip
EquipmentFailureMode trip = EquipmentFailureMode.trip("Export Compressor A");

// Optimize
DegradedOperationResult result = optimizer.optimizeWithEquipmentDown(trip);

// Report
System.out.println("=== DEGRADED OPERATION OPTIMIZATION ===");
System.out.printf("Normal production: %.0f kg/hr%n", result.getNormalProduction());
System.out.printf("Optimal degraded:  %.0f kg/hr%n", result.getOptimalProduction());
System.out.printf("Recovery rate:     %.1f%%%n", result.getProductionRecovery());
System.out.println();
System.out.println("Recommended adjustments:");
for (OperatingAdjustment adj : result.getAdjustments()) {
    System.out.printf("  • %s: %s → %s%n",
        adj.getEquipment(),
        adj.getAction(),
        adj.getRecommendedValue() + " " + adj.getUnit());
}
```

Output:
```
=== DEGRADED OPERATION OPTIMIZATION ===
Normal production: 50000 kg/hr
Optimal degraded:  42500 kg/hr
Recovery rate:     85.0%

Recommended adjustments:
  • Export Compressor B: Increase speed → 98%
  • Well Feed: Reduce flow rate → 42500 kg/hr
  • LP Separator: Increase pressure → 25 bara
  • Recycle Valve: Open → 15%
```

---

## Best Practices

1. **Define all constraints** - Safety limits, equipment ratings
2. **Consider equipment limits** - Don't exceed design margins
3. **Test recovery plans** - Simulate before real events
4. **Include operator actions** - Not everything is automated
5. **Document procedures** - Create operating procedures from results
6. **Regular updates** - Re-optimize as equipment degrades

---

## See Also

- [Production Impact Analysis](production-impact.md)
- [Dependency Analysis](dependency-analysis.md)
- [API Reference](api-reference.md#degradedoperationoptimizer)
