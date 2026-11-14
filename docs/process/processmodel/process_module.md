---
title: ProcessModule Class
description: Compose and run ProcessSystem instances and nested ProcessModule objects in NeqSim.
---

`ProcessModule` groups complete `ProcessSystem` instances and other `ProcessModule` objects into one ordered simulation. It is an orchestration container: add equipment to a `ProcessSystem`, then add that system to a module. `ProcessModule` does not accept equipment directly.

## Ownership model

The containment hierarchy is:

1. Equipment such as `Stream` and `Heater` belongs to a `ProcessSystem`.
2. A `ProcessSystem` belongs to a `ProcessModule` through `add(ProcessSystem)`.
3. A nested module belongs to its parent through `add(ProcessModule)`.

There is no separate module interface, alternate add method, or catalog of built-in domain-specific module classes. Build reusable process sections by composing the real `ProcessSystem` and `ProcessModule` types.

## Complete example

This Java 8 program creates one process system, nests it inside two modules, runs the root module, and checks the recursive lookup and calculated outlet state. The checks are broad execution contracts, not design-acceptance limits.

<!-- process-module-example: nested-process-systems -->
```java
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Demonstrates the supported ProcessModule containment and execution contract. */
public final class ProcessModuleExample {
  private static final Logger LOGGER = LogManager.getLogger(ProcessModuleExample.class);

  /** Utility class; do not instantiate. */
  private ProcessModuleExample() {}

  /**
   * Builds and executes a nested process module.
   *
   * @param args command-line arguments; unused
   */
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Heater heater = new Heater("heater", feed);
    heater.setOutletTemperature(330.15, "K");

    ProcessSystem heatingSystem = new ProcessSystem("heating system");
    heatingSystem.add(feed);
    heatingSystem.add(heater);

    ProcessModule heatingArea = new ProcessModule("heating area");
    heatingArea.add(heatingSystem);

    ProcessModule facility = new ProcessModule("facility");
    facility.add(heatingArea);
    facility.run();

    double outletTemperatureK = heater.getOutletStream().getTemperature("K");
    require(facility.solved(), "Facility module did not finish");
    require(facility.getSubSystemCount() == 1, "Expected one direct child module");
    require(facility.getAllProcessSystems().size() == 1, "Expected one recursive process system");
    require(facility.getUnit("heater") == heater, "Recursive equipment lookup failed");
    require(Double.isFinite(outletTemperatureK), "Outlet temperature is not finite");
    require(outletTemperatureK > 329.0 && outletTemperatureK < 331.0,
        "Outlet temperature is outside the execution check");

    LOGGER.info("Nested module outlet temperature: {} K", outletTemperatureK);
  }

  /**
   * Fails closed when an execution contract is not satisfied.
   *
   * @param condition condition that must be true
   * @param message diagnostic message
   */
  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
```

## Core operations

| Operation | Meaning |
|---|---|
| `add(ProcessSystem)` | Adds a complete process system at the next execution position. |
| `add(ProcessModule)` | Adds a nested module at the next execution position. |
| `run()` / `run(UUID)` | Executes children in insertion order and resolves module-level recycles when present. |
| `runAsTask()` | Submits execution to the shared NeqSim thread pool and returns a `Future<?>`. |
| `getUnit(String)` | Searches contained systems and nested modules recursively; the return type is `Object`. |
| `getAllProcessSystems()` | Returns every contained `ProcessSystem`, including systems below nested modules. |
| `getSubSystemCount()` | Counts only the systems and modules added directly to this module. |
| `solved()` | Reports whether the module run has completed. |
| `validateStructure()` | Reports structural issues such as an empty module or disconnected sub-systems. |
| `buildModelGraph()` | Builds the graph used by calculation-order and recycle-loop inspection methods. |

Progress listeners attached with `setProgressListener(...)` propagate to child systems and nested modules.

## Capacity Constraints in ProcessModule

Capacity methods traverse all contained process systems, including those below nested modules.
Only equipment implementing the capacity contract contributes to the results.

### Available Methods

| Method | Description |
|--------|-------------|
| `getConstrainedEquipment()` | Get all capacity-constrained equipment from all systems |
| `findBottleneck()` | Find equipment with highest utilization |
| `isAnyEquipmentOverloaded()` | Check if any equipment exceeds design capacity |
| `isAnyHardLimitExceeded()` | Check if any HARD limits are exceeded |
| `getCapacityUtilizationSummary()` | Get utilization map for all equipment |
| `getEquipmentNearCapacityLimit()` | Get equipment near warning threshold |

### Usage Example

Use configured `inletSystem`, `compressionSystem`, and `exportSystem`; the example logs
with a class-level Log4j2 `logger`. Import `java.util.List`, `java.util.Map`, and
`neqsim.process.equipment.capacity.CapacityConstrainedEquipment`.

```java
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.equipment.capacity.BottleneckResult;

// Create module with multiple systems
ProcessModule module = new ProcessModule("Production Module");
module.add(inletSystem);
module.add(compressionSystem);
module.add(exportSystem);
module.run();

// Get all constrained equipment across all systems
List<CapacityConstrainedEquipment> constrained = module.getConstrainedEquipment();
logger.info("Found " + constrained.size() + " constrained equipment");

// Find bottleneck across entire module
BottleneckResult bottleneck = module.findBottleneck();
if (bottleneck.hasBottleneck()) {
    logger.info("Bottleneck: " + bottleneck.getEquipmentName());
    logger.info("Constraint: " + bottleneck.getConstraintName());
    logger.info("Utilization: " + bottleneck.getUtilizationPercent() + "%");
}

// Check for overloaded equipment
if (module.isAnyEquipmentOverloaded()) {
    logger.info("Warning: Equipment exceeds design capacity!");
}

// Get utilization summary
Map<String, Double> utilization = module.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    logger.info(String.format("%s: %.1f%%%n", entry.getKey(), entry.getValue()));
}
```

### Nested Module Support

Capacity constraint methods work recursively across nested modules:

```java
// Inner modules
ProcessModule gatheringModule = new ProcessModule("Gathering");
gatheringModule.add(manifoldSystem);

ProcessModule compressionModule = new ProcessModule("Compression");
compressionModule.add(compressorSystem);

// Outer module containing both
ProcessModule facilityModule = new ProcessModule("Facility");
facilityModule.add(gatheringModule);
facilityModule.add(compressionModule);
facilityModule.run();

// This will find constrained equipment from BOTH inner modules
List<CapacityConstrainedEquipment> allConstrained = facilityModule.getConstrainedEquipment();

// Bottleneck detection spans all nested modules
BottleneckResult bottleneck = facilityModule.findBottleneck();
```

For detailed constraint management, see [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md).

---

## Optimization with ProcessModule

`ProcessOptimizationEngine` accepts `ProcessModule`. `DesignOptimizer` supports module
validation and sizing; its bounded search currently requires a single `ProcessSystem`.
The following snippets require a configured, connected `facilityModule`, with unique
stream names `Well Feed` and `Export Gas`. Use the result type belonging to each optimizer.

### ProcessOptimizationEngine

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.processmodel.ProcessSystem;

org.apache.logging.log4j.Logger logger =
    org.apache.logging.log4j.LogManager.getLogger("ModuleOptimization");

// Create engine with ProcessModule
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(facilityModule);
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
engine.setTolerance(1.0); // kg/hr resolution for this throughput search

// Set feed stream (searches across ALL systems in module)
engine.setFeedStreamName("Well Feed");

// Set outlet stream to monitor (searches across ALL systems in module)
engine.setOutletStreamName("Export Gas");

// Find maximum throughput
ProcessOptimizationEngine.OptimizationResult result = engine.findMaximumThroughput(
    85.0,      // inlet pressure (bara)
    40.0,      // outlet pressure (bara)
    5000.0,    // min flow (kg/hr)
    200000.0   // max flow (kg/hr)
);

if (!result.isConverged() || !result.getConstraintViolations().isEmpty()) {
    throw new IllegalStateException("Search failed or returned constraint violations");
}
logger.info("Selected feed rate: {} kg/hr", result.getOptimalValue());

// Replay the selected flow before reading the current outlet conditions.
// Use the actual connected feed from the module when applying this setpoint.
for (ProcessSystem system : facilityModule.getAllProcessSystems()) {
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : system.getUnitOperations()) {
        if (unit instanceof neqsim.process.equipment.stream.StreamInterface
                && unit.getName().equals("Well Feed")) {
            ((neqsim.process.equipment.stream.StreamInterface) unit)
                .setFlowRate(result.getOptimalValue(), "kg/hr");
        }
    }
}
facilityModule.run();

// Get outlet conditions from the configured outlet stream
double outletTemp = engine.getOutletTemperature("C");
double outletFlow = engine.getOutletFlowRate("MSm3/day");
logger.info("Export temperature: " + outletTemp + " °C");
logger.info("Export flow: " + outletFlow + " MSm3/day");
```

### Stream Configuration Methods

| Method | Description |
|--------|-------------|
| `setFeedStreamName(String)` | Set which stream to vary during optimization |
| `getFeedStreamName()` | Get the feed stream name |
| `setOutletStreamName(String)` | Set which stream to monitor for outlet conditions |
| `getOutletStreamName()` | Get the outlet stream name |
| `getOutletTemperature(String)` | Get outlet temperature in specified unit |
| `getOutletFlowRate(String)` | Get outlet flow rate in specified unit |

### DesignOptimizer

```java
import neqsim.process.design.DesignOptimizer;
import neqsim.process.design.DesignResult;

// Create optimizer from ProcessModule
DesignOptimizer optimizer = DesignOptimizer.forProcess(facilityModule);

// Check mode
if (optimizer.isModuleMode()) {
    logger.info("Optimizing module: " + optimizer.getModule().getName());
}

// Configure validation and auto-sizing across the module
optimizer
    .autoSizeEquipment(1.2)
    .applyDefaultConstraints();

DesignResult result = optimizer.optimize();
```

The methods above only assess configured constraints and the selected search; independently
check complete-model convergence, mass/energy balance, and installed capacity coverage.

Constraints are evaluated across **all nested ProcessSystems** in the module hierarchy. The result
status is `AUTO_SIZED`, not `OPTIMIZED`, because no search was performed. Configured bounded search
through `DesignOptimizer` currently requires a single `ProcessSystem`; use
`ProductionOptimizer` with `ProcessModelOptimizationView` for whole-module optimization.


## Related documentation

- [ProcessSystem](process_system.md) explains equipment ownership and execution within one system.
- [Graph Simulation](graph_simulation.md) covers graph-based inspection and execution.
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) describes capacity contracts used by module-level summaries.
- [Equipment Overview](../equipment/) lists process equipment that can be added to a `ProcessSystem`.
