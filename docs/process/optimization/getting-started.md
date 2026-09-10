---
title: Getting Started with Process Optimization
description: Executable Java setup for constrained throughput, custom objectives and process automation.
---

This example builds a complete gas-compression process and limits its shaft power to
4,000 kW. It uses synthetic gas (90 mol% methane, 10 mol% ethane), SRK with the classic
mixing rule, 50 bara inlet pressure and 150 bara discharge pressure. Flow is in kg/hr.
The installed power rating is in kW; the legacy `getCapacityDuty()/getCapacityMax()`
methods both return W. Always use explicit units when reading power.

## 1) Choose optimization API

| Use case | API |
|---|---|
| Maximum throughput for fixed boundary pressures | `ProcessOptimizationEngine` |
| Custom objectives or multiple variables | `ProductionOptimizer` |
| Trade-offs among competing objectives | `MultiObjectiveOptimizer` or `ProductionOptimizer.optimizePareto()` |
| Equality/inequality constrained numerical problem | `SQPoptimizer` |
| Variables addressed by equipment/area names | `ProcessAutomation` |

## 2) Build, solve, constrain and optimize

Save this complete block as `OptimizationGuideSetup.java`. Compile it with Java 8 or
newer and the NeqSim runtime dependencies on the classpath, then run its `main` method.
The same class supplies the process fixture for the overview and plugin-architecture
snippets. The Java examples are extracted and executed by
`OptimizationEntryDocumentationTest`; they are not separate hand-maintained copies.

<!-- optimization-example: setup -->
```java
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.thermo.system.SystemSrkEos;

public class OptimizationGuideSetup {
    private static final Logger logger = LogManager.getLogger(OptimizationGuideSetup.class);

    public static ProcessSystem createProcess() {
        SystemSrkEos gas = new SystemSrkEos(303.15, 50.0);
        gas.addComponent("methane", 0.90);
        gas.addComponent("ethane", 0.10);
        gas.setMixingRule("classic");
        Stream feed = new Stream("feed", gas);
        feed.setFlowRate(50000.0, "kg/hr");
        Compressor compressor = new Compressor("comp", feed);
        compressor.setOutletPressure(150.0, "bara");
        compressor.setUsePolytropicCalc(true);
        compressor.setPolytropicEfficiency(0.78);
        compressor.getMechanicalDesign().setMaxDesignPower(4000.0);
        CapacityConstraint power = compressor.getCapacityConstraints().get("power");
        power.setMaxValue(100.0); // percent of the installed rating
        compressor.clearCapacityConstraints();
        compressor.addCapacityConstraint(power);
        Stream outlet = new Stream("outlet", compressor.getOutletStream());
        ProcessSystem process = new ProcessSystem();
        process.add(feed);
        process.add(compressor);
        process.add(outlet);
        process.run();
        return process;
    }

    public static ProcessOptimizationEngine.OptimizationResult optimize(ProcessSystem process) {
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        engine.setFeedStreamName("feed");
        engine.setOutletStreamName("outlet");
        engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
        engine.setTolerance(1.0); // absolute flow interval in kg/hr
        ProcessOptimizationEngine.OptimizationResult result =
            engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
        if (!result.isConverged()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        // Also works with 3.20.0: leave the model at the reported point after sensitivity probes.
        ((Stream) process.getUnit("feed")).setFlowRate(result.getOptimalValue(), "kg/hr");
        process.run();
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
                engine.evaluateAllConstraints().getEquipmentStatuses()) {
            if (!status.isWithinLimits()) {
                throw new IllegalStateException("Capacity exceeded: " + status.getEquipmentName());
            }
        }
        return result;
    }

    public static void main(String[] args) {
        ProcessSystem process = createProcess();
        ProcessOptimizationEngine.OptimizationResult throughput = optimize(process);
        Compressor compressor = (Compressor) process.getUnit("comp");
        logger.info("Maximum throughput: {} kg/hr; compressor power: {} kW",
            throughput.getOptimalValue(), compressor.getPower("kW"));
        // Process summaries already return PERCENT; equipment utilization methods return fractions.
        for (Map.Entry<String, Double> entry : process.getCapacityUtilizationSummary().entrySet()) {
            logger.info("{}: {}%", entry.getKey(), entry.getValue());
        }

        Stream feed = (Stream) process.getUnit("feed");
        ProductionOptimizer.OptimizationConfig config = new ProductionOptimizer.OptimizationConfig(10000.0, 200000.0)
            .rateUnit("kg/hr").defaultUtilizationLimit(1.0).utilizationMarginFraction(0.05)
            .tolerance(1.0).maxIterations(60)
            .searchMode(ProductionOptimizer.SearchMode.GOLDEN_SECTION_SCORE);
        List<ProductionOptimizer.OptimizationObjective> objectives = Arrays.asList(
            new ProductionOptimizer.OptimizationObjective("throughput",
                p -> ((Stream) p.getUnit("outlet")).getFlowRate("kg/hr"), 1.0,
                ProductionOptimizer.ObjectiveType.MAXIMIZE));
        ProductionOptimizer.OptimizationResult result =
            new ProductionOptimizer().optimize(process, feed, config, objectives, null);
        if (!result.isFeasible()) {
            throw new IllegalStateException("No feasible production-optimizer point");
        }
        feed.setFlowRate(result.getOptimalRate(), "kg/hr");
        process.run();
        logger.info("Custom objective optimum: {} kg/hr", result.getOptimalRate());

        process.getAutomation().setVariableValue("comp.outletPressure", 160.0, "bara");
        process.run();
        logger.info("Power after pressure change: {} kW",
            process.getAutomation().getVariableValue("comp.power", "kW"));
    }
}
```

The custom-objective example reserves 5% capacity headroom explicitly, so its optimum
is lower than the engine example at the full 4,000 kW limit.

The model intentionally constrains shaft power alone. No compressor chart, vendor speed
limits, surge qualification, or installed separator geometry is supplied. Replacing the
power-only fixture with an actual facility requires its installed limits and equipment
maps. Autosizing is a separate design study: repeatedly resizing equipment during an
operating-point search changes the optimization problem.

For this fixed-composition, fixed-pressure-ratio case, compressor power is proportional
to mass flow. Check the optimum against the base-case flow multiplied by
`4000 / basePowerKW`, and confirm inlet and outlet mass flow agree. The binary search
requires monotonic feasibility; it is not a general global optimizer.

## 3) Multi-area process models

Use `ProcessModel` when areas must be solved together. This complete method-body example
uses the setup class above and the named `Compression` area. Place imports above the
containing class and run the remaining lines inside a method.

<!-- optimization-example: model -->
```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem compression = OptimizationGuideSetup.createProcess();
ProcessModel plant = new ProcessModel();
plant.add("Compression", compression);
plant.run();
plant.getAutomation().setVariableValue("Compression::comp.outletPressure", 160.0, "bara");
plant.run();
double powerKW = plant.getAutomation().getVariableValue("Compression::comp.power", "kW");
if (!Double.isFinite(powerKW) || powerKW <= 0.0) {
    throw new IllegalStateException("Invalid compression power");
}
```

## 4) Recommended workflow

1. Build and solve the base case, then check mass balance and units.
2. Set installed capacity limits or carry out a separately identified sizing study.
3. Register named decision variables, objectives, and required constraints.
4. Optimize, check feasibility and convergence, and re-run the selected point.
5. Inspect all active constraints and the governing bottleneck before using the result.

## 5) Next reading

- [Optimization Overview](OPTIMIZATION_OVERVIEW.md)
- [Practical Examples](PRACTICAL_EXAMPLES.md)
- [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md)
- [Constraint Framework](constraint-framework.md)
- [Multi-Objective Optimization](multi-objective-optimization.md)
- [SQP Optimizer](sqp_optimizer.md)
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md)
