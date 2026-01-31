# Physics-Based Risk Integration in NeqSim

This document describes how the risk framework integrates with NeqSim's physics-based process simulation capabilities.

## Overview

The risk framework now provides **two levels** of integration with NeqSim:

1. **Basic Integration** - Uses `ProcessSystem` for equipment lists and baseline production
2. **Physics-Based Integration** - Directly reads T, P, capacity utilization from equipment

## Key Classes

### ProcessEquipmentMonitor (Physics-Based)

The `ProcessEquipmentMonitor` class directly connects to NeqSim equipment to:
- Read temperature from `equipment.getTemperature()`
- Read pressure from `equipment.getPressure()`
- Read capacity utilization from `CapacityConstrainedEquipment.getMaxUtilization()`
- Identify bottleneck constraints from `getBottleneckConstraint()`

```java
// Create monitor for a separator
ProcessEquipmentMonitor monitor = new ProcessEquipmentMonitor(separator);
monitor.setDesignTemperatureRange(273.15, 373.15); // K
monitor.setDesignPressureRange(1.0, 100.0);        // bara
monitor.setBaseFailureRate(0.0001);                // per hour

// After process runs, update reads from equipment
process.run();
monitor.update();

// Health and failure rate based on physics
double health = monitor.getHealthIndex();        // 0-1
double failRate = monitor.getAdjustedFailureRate(); // increases if outside design range
double prob24h = monitor.getFailureProbability(24); // 24-hour failure probability
```

### PhysicsBasedRiskMonitor

Performs system-wide risk assessment using NeqSim's physics:

```java
PhysicsBasedRiskMonitor riskMonitor = new PhysicsBasedRiskMonitor(processSystem);

// Configure design limits
riskMonitor.setDesignTemperatureRange("HP Separator", 273.15, 373.15);
riskMonitor.setDesignPressureRange("HP Separator", 1.0, 100.0);
riskMonitor.setBaseFailureRate("Compressor1", 0.0001);

// Run assessment
PhysicsBasedRiskAssessment assessment = riskMonitor.assess();

// Results derived from physics
System.out.println("Overall Risk: " + assessment.getOverallRiskScore());
System.out.println("Bottleneck: " + assessment.getBottleneckEquipment());
System.out.println("System Margin: " + assessment.getSystemCapacityMargin());
```

## How Risk Scores Are Calculated

The physics-based risk calculation considers:

### 1. Equipment Health Index
Based on deviation from design conditions:

| Condition | Health Index |
|-----------|-------------|
| Within design range | 0.8 - 1.0 |
| Near design limits | 0.5 - 0.8 |
| Outside design range | < 0.5 |

### 2. Adjusted Failure Rate
```
adjustedFailureRate = baseFailureRate × exp((1 - healthIndex) × 3)
```

Low health → exponentially higher failure rate

### 3. Capacity-Weighted Consequence
Equipment at high utilization has higher consequence:
```
consequenceWeight = 1 + utilization × 2  (ranges 1x to 3x)
```

Bottleneck equipment has 2x additional weight.

### 4. Overall Risk Score
```
overallRisk = capacityRisk + healthRisk + maxEquipmentRisk
```

Where:
- `capacityRisk` = bottleneck utilization × 3 (0-3 scale)
- `healthRisk` = (1 - avgHealth) × 4 (0-4 scale)
- `maxEquipmentRisk` = highest equipment risk × 0.3 (0-3 scale)

## NeqSim APIs Used

| NeqSim API | Risk Usage |
|-----------|------------|
| `processSystem.findBottleneck()` | Identify system-limiting equipment |
| `processSystem.getCapacityUtilizationSummary()` | Get all equipment utilizations |
| `processSystem.getConstrainedEquipment()` | List equipment with capacity tracking |
| `equipment.getTemperature()` | Condition monitoring |
| `equipment.getPressure()` | Condition monitoring |
| `CapacityConstrainedEquipment.getMaxUtilization()` | Capacity-based risk |
| `CapacityConstrainedEquipment.getBottleneckConstraint()` | Constraint identification |

## Example: Complete Physics-Based Risk Assessment

```java
// Build process
SystemInterface gas = new SystemSrkEos(273.15 + 40, 80.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(10000, "kg/hr");

ThrottlingValve valve = new ThrottlingValve("Inlet Valve", feed);
valve.setOutletPressure(40.0, "bara");

Separator separator = new Separator("HP Separator", valve.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(separator);
process.run();

// Create physics-based risk monitor
PhysicsBasedRiskMonitor riskMonitor = new PhysicsBasedRiskMonitor(process);

// Set design envelopes
riskMonitor.setDesignTemperatureRange("HP Separator", 273.15, 373.15);
riskMonitor.setDesignPressureRange("HP Separator", 1.0, 100.0);
riskMonitor.setBaseFailureRate("HP Separator", 0.0001);

// Get assessment
PhysicsBasedRiskAssessment assessment = riskMonitor.assess();

// Output physics-based results
System.out.println("=== Physics-Based Risk Assessment ===");
System.out.println("Overall Risk Score: " + assessment.getOverallRiskScore());
System.out.println("System Capacity Margin: " + assessment.getSystemCapacityMargin());
System.out.println("Bottleneck: " + assessment.getBottleneckEquipment());
System.out.println("\nEquipment Health:");
for (Map.Entry<String, Double> e : assessment.getEquipmentHealthIndices().entrySet()) {
    System.out.println("  " + e.getKey() + ": " + String.format("%.2f", e.getValue()));
}
System.out.println("\nEquipment Risk Scores:");
for (Map.Entry<String, Double> e : assessment.getEquipmentRiskScores().entrySet()) {
    System.out.println("  " + e.getKey() + ": " + String.format("%.3f", e.getValue()));
}
```

## Comparison: Basic vs Physics-Based

| Feature | Basic (`OperationalRiskSimulator`) | Physics-Based (`PhysicsBasedRiskMonitor`) |
|---------|-------------------------------------|------------------------------------------|
| Temperature monitoring | Manual input | Auto from equipment |
| Pressure monitoring | Manual input | Auto from equipment |
| Capacity utilization | Not used | From CapacityConstrainedEquipment |
| Bottleneck detection | Not used | Uses ProcessSystem.findBottleneck() |
| Health calculation | N/A | Based on T/P deviation from design |
| Failure rate | Fixed per equipment | Dynamic based on conditions |

## Best Practices

1. **Use PhysicsBasedRiskMonitor** when process conditions vary
2. **Set realistic design limits** - these define the "normal operating envelope"
3. **Run process.run()** before assessment to ensure current physics values
4. **Check warnings** in assessment for equipment near limits
5. **Monitor capacity margin** - values < 0.1 indicate system near limits
