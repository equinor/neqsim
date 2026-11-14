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

Progress listeners can be attached with `setProgressListener(...)`; they propagate to child systems and nested modules. Capacity helpers such as `findBottleneck()`, `isAnyEquipmentOverloaded()`, and `getCapacityUtilizationSummary()` traverse the hierarchy but depend on equipment that implements NeqSim's capacity contracts.

## Related documentation

- [ProcessSystem](process_system.md) explains equipment ownership and execution within one system.
- [Graph Simulation](graph_simulation.md) covers graph-based inspection and execution.
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) describes capacity contracts used by module-level summaries.
- [Equipment Overview](../equipment/) lists process equipment that can be added to a `ProcessSystem`.
