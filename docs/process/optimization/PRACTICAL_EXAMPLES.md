---
title: Process Optimization Practical Examples
description: Practical Java and Python examples for the current NeqSim process optimization APIs.
---

# Process Optimization Practical Examples

These examples use the current `ProcessOptimizationEngine` API. In particular, an
`OptimizationResult` exposes `getOptimalValue()`, `isConverged()`, `getBottleneck()`, and
`getConstraintViolations()`. A `ConstraintReport` exposes `getEquipmentStatuses()` and
`getBottleneck()`.

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW).

## Related Documentation

| Document | Description |
|---|---|
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | When to use each optimizer |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) | `ProcessOptimizationEngine` API |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | `ProductionOptimizer` examples |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | Python/SciPy integration |

## Java examples

### Simple throughput optimization

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class SimpleThroughputOptimization {
  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(288.15, 50.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setPressure(50.0, "bara");
    feed.setTemperature(288.15, "K");

    Compressor compressor = new Compressor("Export Compressor", feed);
    compressor.setOutletPressure(150.0);
    compressor.setPolytropicEfficiency(0.78);

    Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
    aftercooler.setOutTemperature(313.15);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(aftercooler);
    process.run();

    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    ProcessOptimizationEngine.OptimizationResult result =
        engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);

    System.out.println("Maximum throughput: " + result.getOptimalValue() + " kg/hr");
    System.out.println("Converged: " + result.isConverged());
    System.out.println("Bottleneck: " + result.getBottleneck());

    if (!result.getConstraintViolations().isEmpty()) {
      System.out.println("Constraint violations:");
      for (String violation : result.getConstraintViolations()) {
        System.out.println("  - " + violation);
      }
    }

    // Power is an equipment result, not a field on OptimizationResult.
    System.out.println("Compressor power: " + compressor.getPower("kW") + " kW");
  }
}
```

### Multi-equipment process and constraint report

The same engine can be used for a larger process. Always run the base process before evaluating
constraints or starting an optimization.

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

public final class ConstraintReportExample {
  private ConstraintReportExample() {}

  public static void printConstraintReport(ProcessSystem process) {
    process.run();

    ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
    ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

    for (ProcessOptimizationEngine.EquipmentConstraintStatus status
        : report.getEquipmentStatuses()) {
      System.out.printf(
          "%s: %.1f%% utilization, within limits=%s%n",
          status.getEquipmentName(),
          status.getUtilization() * 100.0,
          status.isWithinLimits());

      if (status.getBottleneckConstraint() != null) {
        System.out.println("  limiting constraint: " + status.getBottleneckConstraint());
      }
    }

    ProcessOptimizationEngine.EquipmentConstraintStatus bottleneck = report.getBottleneck();
    if (bottleneck != null) {
      System.out.printf(
          "Process bottleneck: %s (%.1f%%)%n",
          bottleneck.getEquipmentName(), bottleneck.getUtilization() * 100.0);
    }
  }
}
```

### Maximum throughput for a larger process

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(80.0, 180.0, 50000.0, 300000.0);

System.out.printf("Maximum rate: %.0f kg/hr%n", result.getOptimalValue());
System.out.println("Converged: " + result.isConverged());
System.out.println("Limited by: " + result.getBottleneck());
```

### Equipment-capacity dashboard

Capacity-strategy utilization is returned as a fraction, where `1.0` means 100% of the design
limit.

```java
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

public final class CapacityDashboard {
  private CapacityDashboard() {}

  public static void print(ProcessSystem process) {
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
      if (strategy == null) {
        continue;
      }

      Map<String, CapacityConstraint> constraints = strategy.getConstraints(equipment);
      if (constraints.isEmpty()) {
        continue;
      }

      double maxUtilization = strategy.evaluateCapacity(equipment);
      System.out.printf("%s: %.1f%%%n", equipment.getName(), maxUtilization * 100.0);

      for (CapacityConstraint constraint : constraints.values()) {
        System.out.printf(
            "  %-24s %.1f%%%n",
            constraint.getName(), constraint.getUtilization() * 100.0);
      }
    }
  }
}
```

### Power-generation capacity optimization

`EquipmentConstraintStatus` uses `getUtilization()`; there is no
`EquipmentConstraintStatus.getMaxUtilization()` method.

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(chp);
ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

for (ProcessOptimizationEngine.EquipmentConstraintStatus status
    : report.getEquipmentStatuses()) {
  System.out.printf(
      "%s %-15s %.1f%% [%s]%n",
      status.isWithinLimits() ? "OK" : "!!",
      status.getEquipmentName(),
      status.getUtilization() * 100.0,
      status.getBottleneckConstraint());
}

ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(25.0, 25.0, 100.0, 5000.0);
System.out.println("Max fuel rate: " + result.getOptimalValue() + " kg/hr");
System.out.println("Bottleneck: " + result.getBottleneck());
```

For complete gas-turbine/HRSG construction examples, see
[Power Generation Equipment](../equipment/power_generation).

### VFP and lift-curve generation

VFP generation requires a real, configured process. Do not use an empty placeholder
`ProcessSystem`: the process must contain the streams/equipment whose pressure-flow response is
being evaluated. See [Flow Rate Optimization](flow-rate-optimization) and use its complete process
setup before exporting VFP/lift-curve data.

## Python examples (JPype)

### Basic throughput optimization

Assume `process` is a configured and already-run `ProcessSystem`.

```python
from neqsim.process.util.optimizer import ProcessOptimizationEngine

engine = ProcessOptimizationEngine(process)
result = engine.findMaximumThroughput(
    50.0,       # inlet pressure [bara]
    150.0,      # outlet pressure [bara]
    10000.0,    # minimum flow [kg/hr]
    200000.0,   # maximum flow [kg/hr]
)

print(f"Maximum throughput: {float(result.getOptimalValue()):.0f} kg/hr")
print(f"Converged: {bool(result.isConverged())}")
print(f"Bottleneck: {result.getBottleneck()}")

violations = list(result.getConstraintViolations())
for violation in violations:
    print(f"  - {violation}")
```

### Constraint analysis

`ConstraintReport` does not provide `hasViolations()`, `getOverallUtilization()`, or
`getBottleneckEquipment()`. Derive those values from the equipment statuses instead:

```python
from neqsim.process.util.optimizer import ProcessOptimizationEngine

engine = ProcessOptimizationEngine(process)
report = engine.evaluateAllConstraints()
statuses = list(report.getEquipmentStatuses())

feasible = all(bool(status.isWithinLimits()) for status in statuses)
overall_utilization = max(
    (float(status.getUtilization()) for status in statuses),
    default=0.0,
)

bottleneck_status = report.getBottleneck()
bottleneck = (
    str(bottleneck_status.getEquipmentName())
    if bottleneck_status is not None
    else None
)

print(f"Feasible: {feasible}")
print(f"Overall utilization: {overall_utilization * 100.0:.1f}%")
print(f"Bottleneck: {bottleneck}")
```

### Parameter sweep / operating envelope

```python
import pandas as pd
from neqsim.process.util.optimizer import ProcessOptimizationEngine


def evaluate_point(process, inlet_pressure, outlet_pressure, flow_rate):
    feed = process.getUnit("feed")
    compressor = process.getUnit("Export Compressor")

    feed.setPressure(inlet_pressure, "bara")
    feed.setFlowRate(flow_rate, "kg/hr")
    compressor.setOutletPressure(outlet_pressure)
    process.run()

    report = ProcessOptimizationEngine(process).evaluateAllConstraints()
    statuses = list(report.getEquipmentStatuses())
    bottleneck_status = report.getBottleneck()

    return {
        "inlet_pressure": inlet_pressure,
        "outlet_pressure": outlet_pressure,
        "flow_rate": flow_rate,
        "compressor_power_kw": float(compressor.getPower("kW")),
        "feasible": all(bool(s.isWithinLimits()) for s in statuses),
        "overall_utilization": max(
            (float(s.getUtilization()) for s in statuses), default=0.0
        ),
        "bottleneck": (
            str(bottleneck_status.getEquipmentName())
            if bottleneck_status is not None
            else None
        ),
    }


rows = []
for p_in in [40.0, 50.0, 60.0]:
    for flow in [20000.0, 50000.0, 100000.0]:
        rows.append(evaluate_point(process, p_in, 150.0, flow))

df = pd.DataFrame(rows)
print(df)
```

### Detailed equipment constraints

```python
from neqsim.process.equipment.capacity import EquipmentCapacityStrategyRegistry

registry = EquipmentCapacityStrategyRegistry.getInstance()

for equipment in process.getUnitOperations():
    strategy = registry.findStrategy(equipment)
    if strategy is None:
        continue

    constraints = strategy.getConstraints(equipment)
    for entry in constraints.entrySet():
        name = str(entry.getKey())
        constraint = entry.getValue()
        print(
            f"{equipment.getName()} / {name}: "
            f"{float(constraint.getUtilization()) * 100.0:.1f}%"
        )
```

## Current `ProcessOptimizationEngine` result/report API

Use this table when updating older examples:

| Older/stale example call | Current API / pattern |
|---|---|
| `result.getOptimalFlowRate()` | `result.getOptimalValue()` |
| `result.isFeasible()` | `result.isConverged()` plus inspect constraint violations/report |
| `result.getBottleneckEquipment()` | `result.getBottleneck()` |
| `result.getTotalPower()` | Read power from the relevant equipment, e.g. `compressor.getPower("kW")` |
| `status.getMaxUtilization()` | `status.getUtilization()` |
| `report.hasViolations()` | `any(!status.isWithinLimits())` / Python equivalent |
| `report.getOverallUtilization()` | Maximum `status.getUtilization()` |
| `report.getBottleneckEquipment()` | `report.getBottleneck().getEquipmentName()` with a null check |

## See also

- [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE)
- [Optimization and Constraints](OPTIMIZATION_AND_CONSTRAINTS)
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK)
- [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE)
