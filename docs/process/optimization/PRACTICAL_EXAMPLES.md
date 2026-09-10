---
title: Process Optimization Practical Examples
description: This document provides practical examples for using the optimizer plugin architecture with process simulations, including both Java and Python code samples.
---

# Process Optimization Practical Examples

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

This document provides practical examples for using the optimizer plugin architecture with process simulations, including both Java and Python code samples.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | When to use which optimizer |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) | ProcessOptimizationEngine API |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | ProductionOptimizer examples |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | Python/SciPy integration |

## Table of Contents

- [Java Examples](#java-examples)
  - [Simple Throughput Optimization](#simple-throughput-optimization)
  - [Multi-Equipment Process](#multi-equipment-process)
  - [Constraint Monitoring Dashboard](#constraint-monitoring-dashboard)
  - [Eclipse VFP Table Generation](#eclipse-vfp-table-generation)
  - [Power Generation Capacity Optimization](#power-generation-capacity-optimization)
- [Python Examples (via JPype)](#python-examples-via-jpype)
  - [Basic Process Optimization](#basic-process-optimization)
  - [Lift Curve Generation](#lift-curve-generation)
  - [Equipment Constraint Analysis](#equipment-constraint-analysis)

---

## Java Examples

### Simple Throughput Optimization

Find the maximum throughput for a simple gas compression system with an explicit
4,000 kW shaft-power limit. All compositions are mole fractions, pressures are
absolute, and flows are mass rates unless stated otherwise. The compressor uses
the SRK equation of state and a fixed polytropic efficiency; these are synthetic
screening examples without compressor maps or a validated installed plant envelope.

Save each Java block in a file matching its public class name and compile with
NeqSim on the classpath. The dashboard also needs `SimpleThroughputOptimization`.
The code is compatible with Java 8 and later.

`OptimizationResult.getOptimalValue()` contains throughput in kg/hr for this
objective. `isConverged()` reports completion of the search; check equipment hard
limits and the outlet pressure separately before accepting a result. Power comes
from the equipment, for example `compressor.getPower("kW")`. The reported
bottleneck is the equipment with the highest utilization, which can be below its
limit when the supplied upper flow bound controls the result.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

public class SimpleThroughputOptimization {
    private static final Logger logger = LogManager.getLogger(SimpleThroughputOptimization.class);

    public static ProcessSystem createProcess() {
        SystemInterface gas = new SystemSrkEos(288.15, 50.0);
        gas.addComponent("methane", 0.85);
        gas.addComponent("ethane", 0.10);
        gas.addComponent("propane", 0.05);
        gas.setMixingRule("classic");

        Stream feed = new Stream("feed", gas);
        feed.setFlowRate(50000.0, "kg/hr");
        Compressor compressor = new Compressor("Export Compressor", feed);
        compressor.setOutletPressure(150.0);
        compressor.setUsePolytropicCalc(true);
        compressor.setPolytropicEfficiency(0.78);
        // Fixed illustrative shaft-power limit; maxDesignPower is in kW.
        compressor.getMechanicalDesign().maxDesignPower = 4000.0;
        neqsim.process.equipment.capacity.CapacityConstraint compressorPower =
            compressor.getCapacityConstraints().get("power");
        compressorPower.setMaxValue(100.0);
        // This fixed-pressure example constrains only shaft power; no map is supplied.
        compressor.clearCapacityConstraints();
        compressor.addCapacityConstraint(compressorPower);

        Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
        aftercooler.setOutTemperature(313.15);
        Stream gasExport = new Stream("Gas Export", aftercooler.getOutletStream());

        ProcessSystem process = new ProcessSystem();
        process.add(feed);
        process.add(compressor);
        process.add(aftercooler);
        process.add(gasExport);
        process.run();
        return process;
    }

    public static ProcessOptimizationEngine.OptimizationResult optimize(ProcessSystem process) {
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        engine.setFeedStreamName("feed");
        engine.setOutletStreamName("Gas Export");
        engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
        engine.setTolerance(1.0); // absolute flow tolerance, kg/hr
        ProcessOptimizationEngine.OptimizationResult result =
            engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
        if (!result.isConverged()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        // Compatibility with 3.20.0: explicitly restore the reported operating point.
        ((Stream) process.getUnit(engine.getFeedStreamName())).setFlowRate(result.getOptimalValue(), "kg/hr");
        process.run();
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
                engine.evaluateAllConstraints().getEquipmentStatuses()) {
            if (!status.isWithinLimits()) {
                throw new IllegalStateException("Hard capacity limit exceeded: " + status.getEquipmentName());
            }
        }
        return result;
    }

    public static void main(String[] args) {
        ProcessSystem process = createProcess();
        ProcessOptimizationEngine.OptimizationResult result = optimize(process);
        Compressor compressor = (Compressor) process.getUnit("Export Compressor");
        logger.info("Maximum throughput: {} kg/hr", result.getOptimalValue());
        logger.info("Highest utilization equipment: {}", result.getBottleneck());
        logger.info("Compressor power: {} kW", compressor.getPower("kW"));
        logger.info("Constraint violations (including soft limits): {}", result.getConstraintViolations());
    }
}
```

### Multi-Equipment Process

Optimize a branched oil and gas process. Scrubbers remove condensed liquids before
compression, an oil letdown valve establishes the 6 bara LP separation pressure,
the liquid is subcooled to 20 °C for pump NPSH headroom,
and the export pump raises it to 20 bara. The optimizer explicitly monitors
the 180 bara gas export, since the last unit in the flowsheet is the oil pump.
Separated water, scrubber liquids, LP flash gas, export oil, and export gas are all
material outlets; retain them when checking the overall mass balance.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.*;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.equipment.valve.ThrottlingValve;

public class MultiEquipmentOptimization {
    private static final Logger logger = LogManager.getLogger(MultiEquipmentOptimization.class);
    public static ProcessSystem createProcess() {
        // Create wellstream fluid
        SystemInterface wellFluid = new SystemSrkEos(330.0, 80.0);
        wellFluid.addComponent("nitrogen", 0.005);
        wellFluid.addComponent("CO2", 0.02);
        wellFluid.addComponent("methane", 0.60);
        wellFluid.addComponent("ethane", 0.08);
        wellFluid.addComponent("propane", 0.05);
        wellFluid.addComponent("n-butane", 0.03);
        wellFluid.addComponent("n-pentane", 0.02);
        wellFluid.addComponent("nC10", 0.12);
        wellFluid.addComponent("water", 0.075);
        wellFluid.setMixingRule("classic");
        wellFluid.setMultiPhaseCheck(true);

        // Create production train
        Stream wellStream = new Stream("Well Stream", wellFluid);
        wellStream.setFlowRate(100000, "kg/hr");
        wellStream.setPressure(80.0, "bara");
        wellStream.setTemperature(330.0, "K");

        // HP Separator
        ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP Separator", wellStream);
        hpSeparator.setInternalDiameter(3.0);

        // Gas treatment train
        Heater gasHeater = new Heater("Gas Heater", hpSeparator.getGasOutStream());
        gasHeater.setOutTemperature(320.0);

        Separator inletScrubber = new Separator("Inlet Scrubber", gasHeater.getOutletStream());

        Compressor stage1 = new Compressor("1st Stage Compressor", inletScrubber.getGasOutStream());
        stage1.setOutletPressure(120.0);
        stage1.setUsePolytropicCalc(true);
        stage1.getMechanicalDesign().maxDesignPower = 1500.0;
        neqsim.process.equipment.capacity.CapacityConstraint stage1Power =
            stage1.getCapacityConstraints().get("power");
        stage1Power.setMaxValue(100.0);
        // This fixed-pressure example constrains only shaft power; no map is supplied.
        stage1.clearCapacityConstraints();
        stage1.addCapacityConstraint(stage1Power);
        stage1.setPolytropicEfficiency(0.78);

        Cooler intercooler = new Cooler("Intercooler", stage1.getOutletStream());
        intercooler.setOutTemperature(313.15);

        Separator interstageScrubber = new Separator("Interstage Scrubber", intercooler.getOutletStream());

        Compressor stage2 = new Compressor("2nd Stage Compressor", interstageScrubber.getGasOutStream());
        stage2.setOutletPressure(180.0);
        stage2.setUsePolytropicCalc(true);
        stage2.getMechanicalDesign().maxDesignPower = 2000.0;
        neqsim.process.equipment.capacity.CapacityConstraint stage2Power =
            stage2.getCapacityConstraints().get("power");
        stage2Power.setMaxValue(100.0);
        // This fixed-pressure example constrains only shaft power; no map is supplied.
        stage2.clearCapacityConstraints();
        stage2.addCapacityConstraint(stage2Power);
        stage2.setPolytropicEfficiency(0.76);

        Cooler aftercooler = new Cooler("Aftercooler", stage2.getOutletStream());
        aftercooler.setOutTemperature(313.15);

        Separator exportScrubber = new Separator("Export Scrubber", aftercooler.getOutletStream());
        Stream gasExport = new Stream("Gas Export", exportScrubber.getGasOutStream());

        // Oil treatment train
        Heater oilHeater = new Heater("Oil Heater", hpSeparator.getOilOutStream());
        oilHeater.setOutTemperature(340.0);

        ThrottlingValve letdown = new ThrottlingValve("Oil Letdown", oilHeater.getOutletStream());
        letdown.setOutletPressure(6.0);
        Separator lpSeparator = new Separator("LP Separator", letdown.getOutletStream());
        lpSeparator.setInternalDiameter(2.0);

        // Subcool the saturated separator liquid to provide pump suction headroom.
        Cooler oilCooler = new Cooler("Oil Cooler", lpSeparator.getLiquidOutStream());
        oilCooler.setOutTemperature(293.15);
        Pump exportPump = new Pump("Export Pump", oilCooler.getOutletStream());
        exportPump.setOutletPressure(20.0);

        // Build process
        ProcessSystem process = new ProcessSystem();
        process.add(wellStream);
        process.add(hpSeparator);
        process.add(gasHeater);
        process.add(inletScrubber);
        process.add(stage1);
        process.add(intercooler);
        process.add(interstageScrubber);
        process.add(stage2);
        process.add(aftercooler);
        process.add(exportScrubber);
        process.add(gasExport);
        process.add(oilHeater);
        process.add(letdown);
        process.add(lpSeparator);
        process.add(oilCooler);
        process.add(exportPump);
        process.run();

        return process;
    }

    public static void main(String[] args) {
        ProcessSystem process = createProcess();
        Compressor stage1 = (Compressor) process.getUnit("1st Stage Compressor");
        Compressor stage2 = (Compressor) process.getUnit("2nd Stage Compressor");

        // Create optimization engine
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        engine.setFeedStreamName("Well Stream");
        engine.setOutletStreamName("Gas Export");
        engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
        engine.setTolerance(1.0);

        // Evaluate current constraints
        ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

        logger.info("=== Equipment Utilization Summary ===\n");
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
                report.getEquipmentStatuses()) {
            String warningFlag = status.isWithinLimits() ? "✓" : "⚠";
            logger.info(String.format("%s %s: %.1f%% utilization\n",
                warningFlag,
                status.getEquipmentName(),
                status.getUtilization() * 100));

            // Show bottleneck constraint for each equipment
            if (status.getBottleneckConstraint() != null) {
                logger.info(String.format("   Bottleneck: %s\n", status.getBottleneckConstraint()));
            }
        }

        // Find bottleneck
        logger.info("\n=== Process Bottleneck ===");
        String bottleneck = engine.findBottleneckEquipment();
        logger.info("Bottleneck equipment: " + bottleneck);

        // Find maximum throughput
        ProcessOptimizationEngine.OptimizationResult result =
            engine.findMaximumThroughput(80.0, 180.0, 50000.0, 300000.0);

        if (!result.isConverged()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        // Compatibility with 3.20.0: explicitly restore the reported operating point.
        ((Stream) process.getUnit(engine.getFeedStreamName())).setFlowRate(result.getOptimalValue(), "kg/hr");
        process.run();
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
                engine.evaluateAllConstraints().getEquipmentStatuses()) {
            if (!status.isWithinLimits()) {
                throw new IllegalStateException("Hard capacity limit exceeded: " + status.getEquipmentName());
            }
        }
        logger.info("\n=== Maximum Throughput ===");
        logger.info(String.format("Maximum rate: %.0f kg/hr (%.0f%% of current)\n",
            result.getOptimalValue(),
            result.getOptimalValue() / 100000.0 * 100));
        logger.info("Highest utilization (including soft limits): " + result.getBottleneck());
        logger.info(String.format("Total compression power: %.1f MW\n", (stage1.getPower("MW") + stage2.getPower("MW"))));
    }
}
```

### Constraint Monitoring Dashboard

Create a real-time monitoring dashboard for equipment constraints:

```java
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.processmodel.ProcessSystem;

public class ConstraintMonitoringDashboard {
    private static final Logger logger = LogManager.getLogger(ConstraintMonitoringDashboard.class);
    private final ProcessSystem process;
    private final EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();

    public ConstraintMonitoringDashboard(ProcessSystem process) {
        this.process = process;
    }

    public void printConstraintReport() {
        for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
            EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
            if (strategy == null) {
                continue;
            }
            logger.info("{}: {}% utilization, hard limits satisfied: {}", equipment.getName(),
                strategy.evaluateCapacity(equipment) * 100.0, strategy.isWithinHardLimits(equipment));
            for (CapacityConstraint constraint : strategy.getConstraints(equipment).values()) {
                if (!constraint.isEnabled() || (constraint.getDesignValue() == Double.MAX_VALUE
                        && constraint.getMaxValue() == Double.MAX_VALUE && constraint.getMinValue() <= 0.0)) {
                    continue;
                }
                double limit = constraint.isMinimumConstraint() ? constraint.getMinValue() : constraint.getMaxValue();
                logger.info("  {} [{}]: {} {}, {} limit {}, violated: {}", constraint.getName(),
                    constraint.getType(), constraint.getCurrentValue(), constraint.getUnit(),
                    constraint.isMinimumConstraint() ? "minimum" : "maximum", limit,
                    constraint.isMinimumConstraint() ? constraint.getCurrentValue() < limit : constraint.getCurrentValue() > limit);
            }
        }
    }

    public List<String> getDebottleneckingCandidates() {
        List<String> candidates = new ArrayList<String>();
        for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
            EquipmentCapacityStrategy strategy = registry.findStrategy(equipment);
            if (strategy != null && strategy.evaluateCapacity(equipment) > 0.85) {
                candidates.add(equipment.getName());
            }
        }
        return candidates;
    }

    public static void main(String[] args) {
        ProcessSystem process = SimpleThroughputOptimization.createProcess();
        SimpleThroughputOptimization.optimize(process);
        ConstraintMonitoringDashboard dashboard = new ConstraintMonitoringDashboard(process);
        dashboard.printConstraintReport();
        logger.info("Debottlenecking candidates: {}", dashboard.getDebottleneckingCandidates());
    }
}
```

### Eclipse VFP Table Generation

Export an explicitly supplied BHP grid using the current `EclipseVFPExporter`
API. The exporter formats data; it does not accept a `ProcessSystem` constructor
or calculate a well model. This example uses synthetic dry-gas BHP data and a
single zero water-ratio, oil-ratio, and artificial-lift slice. The current VFPPROD
writer emits only this slice; it must not be used to claim a multi-composition
table. Replace the synthetic grid with validated well calculations for a reservoir
study. Use this include in a METRIC deck; gas flow is standard volume per day,
not kg/hr. Array order is `[flow][THP][water ratio][gas/oil ratio][ALQ]`.

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.optimizer.EclipseVFPExporter;

public class VFPTableGeneration {
    private static final Logger logger = LogManager.getLogger(VFPTableGeneration.class);

    public static void main(String[] args) throws Exception {
        // Synthetic dry-gas BHP data: demonstrates export, not a calibrated well model.
        double[] flowRates = {50000.0, 100000.0, 200000.0}; // Sm3/day, METRIC gas rate
        double[] thp = {20.0, 40.0, 60.0}; // bara
        double[][] bhpByThp = {
            {30.0, 36.0, 48.0},
            {52.0, 58.0, 70.0},
            {74.0, 80.0, 92.0}
        };
        // Exporter indexing: [flow][THP][water ratio][gas/oil ratio][ALQ].
        double[][][][][] bhp = new double[flowRates.length][thp.length][1][1][1];
        for (int i = 0; i < flowRates.length; i++) {
            for (int j = 0; j < thp.length; j++) {
                bhp[i][j][0][0][0] = bhpByThp[j][i];
            }
        }

        EclipseVFPExporter exporter = new EclipseVFPExporter(1);
        exporter.setTableTitle("Synthetic dry-gas export example");
        exporter.setDatumDepth(1000.0); // m
        exporter.setUnitSystem("METRIC");
        exporter.setFlowRateType("GAS");
        exporter.setFlowRates(flowRates);
        exporter.setTHPs(thp);
        exporter.setWaterCuts(new double[] {0.0});
        exporter.setGORs(new double[] {0.0});
        exporter.setALQs(new double[] {0.0});
        exporter.setBHPTable(bhp);

        Path outputPath = Paths.get(args.length > 0 ? args[0] : "VFPPROD_PLATFORM.INC");
        Files.write(outputPath, exporter.getVFPPRODString().getBytes(StandardCharsets.UTF_8));
        logger.info("Wrote {} BHP values to {}", flowRates.length * thp.length, outputPath.toAbsolutePath());
    }
}
```

### Power Generation Capacity Optimization

Find the fuel-rate limit for a CHP (combined heat and power) system using explicit
gas-turbine and HRSG capacities. Fuel enters at 25 bara, while the stack-gas
pressure requirement is 1 bara. This is a steady-state capacity calculation; it
does not simulate protective trips or allocate fuel among multiple turbines.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.powergeneration.GasTurbine;
import neqsim.process.equipment.powergeneration.HRSG;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

public class PowerGenerationOptimization {
    private static final Logger logger = LogManager.getLogger(PowerGenerationOptimization.class);
    public static void main(String[] args) {
        // Create fuel gas
        SystemInterface fuelGas = new SystemSrkEos(288.15, 25.0);
        fuelGas.addComponent("methane", 0.90);
        fuelGas.addComponent("ethane", 0.05);
        fuelGas.addComponent("propane", 0.03);
        fuelGas.addComponent("nitrogen", 0.02);
        fuelGas.setMixingRule("classic");

        Stream fuel = new Stream("Fuel Gas", fuelGas);
        fuel.setFlowRate(1500.0, "kg/hr");
        fuel.setPressure(25.0, "bara");
        fuel.setTemperature(288.15, "K");

        // Gas turbine with rated capacity
        GasTurbine gt = new GasTurbine("GT-101", fuel);
        gt.setRatedPower(30.0, "MW");

        // HRSG recovering exhaust heat
        HRSG hrsg = new HRSG("HRSG-101", gt.getOutletStream());
        hrsg.setSteamPressure(40.0);
        hrsg.setSteamTemperature(400.0, "C");
        hrsg.setDesignHeatDuty(50.0, "MW");

        Stream stackGas = new Stream("Stack Gas", hrsg.getOutletStream());

        // Build and run
        ProcessSystem chp = new ProcessSystem();
        chp.add(fuel);
        chp.add(gt);
        chp.add(hrsg);
        chp.add(stackGas);
        chp.run();

        // --- Capacity analysis ---

        // 1. Check utilization of each unit
        logger.info(String.format("GT utilization:   %.1f%%%n",
            gt.getMaxUtilization() * 100));
        logger.info(String.format("HRSG utilization: %.1f%%%n",
            hrsg.getMaxUtilization() * 100));

        // 2. Identify the bottleneck
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(chp);
        engine.setFeedStreamName("Fuel Gas");
        engine.setOutletStreamName("Stack Gas");
        engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
        engine.setTolerance(0.1);
        ProcessOptimizationEngine.ConstraintReport report =
            engine.evaluateAllConstraints();

        for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
                report.getEquipmentStatuses()) {
            logger.info(String.format("%s %-15s %.1f%% [%s]%n",
                status.isWithinLimits() ? "OK" : "!!",
                status.getEquipmentName(),
                status.getUtilization() * 100,
                status.getBottleneckConstraint()));
        }

        // 3. Find maximum fuel rate within the configured steady-state hard limits
        ProcessOptimizationEngine.OptimizationResult result =
            engine.findMaximumThroughput(25.0, 1.0, 100.0, 20000.0);
        fuel.setFlowRate(result.getOptimalValue(), "kg/hr");
        chp.run(); // Restore the reported flow when using the 3.20.0 release.
        if (!result.isConverged() || gt.isHardLimitExceeded() || hrsg.isHardLimitExceeded()) {
            throw new IllegalStateException("No feasible CHP solution: " + result.getErrorMessage());
        }
        logger.info("Max fuel rate: " + result.getOptimalValue() + " kg/hr");
        logger.info("Bottleneck: " + result.getBottleneck());

        // 4. Utilization summary across all equipment
        java.util.Map<String, Double> summary = chp.getCapacityUtilizationSummary();
        for (java.util.Map.Entry<String, Double> e : summary.entrySet()) {
            logger.info(String.format("  %-20s %.1f%%%n", e.getKey(), e.getValue() * 100));
        }
    }
}
```

**Key points:**

- `setRatedPower()` / `setDesignHeatDuty()` create the capacity constraints. In this API their hard limits include 10% overload, so the search can reach 110% design utilization. Without a specified rating, the fallback capacity estimate is not a fixed installed limit.
- `autoSize(1.2)` is a shortcut that sets rated capacity = current duty × safety factor.
- The `ProcessOptimizationEngine` reads `CapacityConstrainedEquipment` constraints from every unit in the `ProcessSystem`, so power generation equipment participates in plant-wide bottleneck detection alongside compressors, separators, and other equipment.
- For more on capacity constraints, see the [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK).
- For power generation equipment details, see [Power Generation Equipment](../equipment/power_generation).

---

## Python Examples (via JPype)

### Basic Process Optimization

Install the Python package and plotting dependencies in your Python environment:

```bash
python -m pip install "neqsim==3.20.0" numpy pandas matplotlib
```

Use a compatible 64-bit Java runtime (Java 17 is suitable). `from neqsim import
jneqsim` starts JPype with the packaged NeqSim JAR; no placeholder JAR path or
Java-package imports that conflict with the Python `neqsim` package are needed.
Save this first block as `basic_process_optimization.py`. Run it with
`python basic_process_optimization.py`, or run the block in a notebook cell.
The following two scripts import its process builder, so save them alongside it.

For the fixed-pressure compressors, the examples retain only the explicit power
constraint. This also avoids a 3.20.0 capacity-strategy bug that counts disabled
surge constraints for compressors without charts. The corrected strategy ignores
disabled constraints. A mapped compressor should retain its active surge, speed,
and other applicable limits.

The explicit re-run after optimization keeps these scripts compatible with
NeqSim 3.20.0, whose sensitivity analysis leaves the last trial flow in the live
process. The corrected engine restores the reported optimum automatically.

The model has an explicit 4,000 kW compressor limit, with 100% as the maximum
allowed power utilization. `total_power` below is that compressor's shaft power
in kW; it is not an attribute of `OptimizationResult`.

```python
import sys

from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
ProcessOptimizationEngine = jneqsim.process.util.optimizer.ProcessOptimizationEngine


def create_compression_process():
    """Create a simple gas compression process."""

    # Create gas composition
    gas = SystemSrkEos(288.15, 50.0)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.10)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")

    # Create feed stream
    feed = Stream("feed", gas)
    feed.setFlowRate(50000, "kg/hr")
    feed.setPressure(50.0, "bara")
    feed.setTemperature(288.15, "K")

    # Create compressor
    compressor = Compressor("Export Compressor", feed)
    compressor.setOutletPressure(150.0)
    compressor.setUsePolytropicCalc(True)
    compressor.setPolytropicEfficiency(0.78)
    compressor.getMechanicalDesign().maxDesignPower = 4000.0  # kW
    power_limit = compressor.getCapacityConstraints().get("power")
    power_limit.setMaxValue(100.0)
    # A fixed-pressure, power-only example with no compressor performance map.
    compressor.clearCapacityConstraints()
    compressor.addCapacityConstraint(power_limit)

    # Create aftercooler
    aftercooler = Cooler("Aftercooler", compressor.getOutletStream())
    aftercooler.setOutTemperature(313.15)

    gas_export = Stream("Gas Export", aftercooler.getOutletStream())

    # Build process
    process = ProcessSystem()
    process.add(feed)
    process.add(compressor)
    process.add(aftercooler)
    process.add(gas_export)
    process.run()

    return process


def optimize_throughput(process):
    """Find maximum throughput for the process."""

    # Create optimization engine
    engine = ProcessOptimizationEngine(process)

    engine.setFeedStreamName("feed")
    engine.setOutletStreamName("Gas Export")
    engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH)
    engine.setTolerance(1.0)  # absolute flow tolerance, kg/hr

    # Find maximum throughput
    result = engine.findMaximumThroughput(
        50.0,      # inlet pressure (bara)
        150.0,     # outlet pressure (bara)
        10000.0,   # min flow rate (kg/hr)
        200000.0   # max flow rate (kg/hr)
    )

    if not result.isConverged():
        raise RuntimeError(str(result.getErrorMessage()))
    # NeqSim 3.20.0 leaves the process at the last sensitivity probe.
    # Re-run the returned optimum before reading equipment outputs.
    process.getUnit("feed").setFlowRate(result.getOptimalValue(), "kg/hr")
    process.run()
    statuses = engine.evaluateAllConstraints().getEquipmentStatuses()
    feasible = all(status.isWithinLimits() for status in statuses)
    feasible = feasible and process.getUnit("Gas Export").getPressure("bara") >= 148.5
    if not feasible:
        raise RuntimeError("Returned operating point does not meet the hard limits")

    # Extract results
    return {
        'optimal_flow_rate': result.getOptimalValue(),
        'feasible': feasible,
        'bottleneck': str(result.getBottleneck()),
        'total_power': process.getUnit("Export Compressor").getPower("kW"),
        'constraint_violations': [str(item) for item in result.getConstraintViolations()]
    }


def evaluate_constraints(process):
    """Evaluate all equipment constraints."""

    engine = ProcessOptimizationEngine(process)
    report = engine.evaluateAllConstraints()

    results = []
    for status in report.getEquipmentStatuses():
        equipment_data = {
            'name': str(status.getEquipmentName()),
            'type': str(status.getEquipmentType()),
            'utilization': status.getUtilization(),
            'within_limits': status.isWithinLimits(),
            'bottleneck_constraint': str(status.getBottleneckConstraint())
        }

        # Get individual constraints
        constraints = []
        for constraint in status.getConstraints():
            if not constraint.isEnabled():
                continue
            if (constraint.getDesignValue() == sys.float_info.max
                    and constraint.getMaxValue() == sys.float_info.max
                    and constraint.getMinValue() <= 0.0):
                continue
            constraints.append({
                'name': str(constraint.getName()),
                'current_value': constraint.getCurrentValue(),
                'design_value': constraint.getDesignValue(),
                'unit': str(constraint.getUnit()),
                'utilization_percent': constraint.getUtilizationPercent()
            })
        equipment_data['constraints'] = constraints
        if constraints:
            results.append(equipment_data)

    return results


# Main execution
if __name__ == "__main__":
    # Create process
    process = create_compression_process()

    # Optimize throughput
    print("=== Throughput Optimization ===")
    opt_result = optimize_throughput(process)
    print(f"Maximum throughput: {opt_result['optimal_flow_rate']:.0f} kg/hr")
    print(f"Bottleneck: {opt_result['bottleneck']}")
    print(f"Total power: {opt_result['total_power']:.1f} kW")

    # Evaluate constraints
    print("\n=== Equipment Constraints ===")
    constraint_report = evaluate_constraints(process)
    for eq in constraint_report:
        status = "✓" if eq['within_limits'] else "⚠"
        print(f"{status} {eq['name']}: {eq['utilization']*100:.1f}% utilization")
        for c in eq['constraints']:
            print(f"   - {c['name']}: {c['current_value']:.2f}/{c['design_value']:.2f} "
                  f"{c['unit']} ({c['utilization_percent']:.1f}%)")
```

### Lift Curve Generation

Save as `lift_curve_generation.py` next to `basic_process_optimization.py` and run
`python lift_curve_generation.py`. This is a compressor operating-point sweep at
fixed composition, not a reservoir well-lift correlation. It produces 50 solved
points, `lift_curve_data.csv`, and `operating_envelope.png`. API and calculation
errors stop execution; only solved points are classified by their hard limits.

```python
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

from basic_process_optimization import create_compression_process, ProcessOptimizationEngine


def generate_lift_curve_data(process,
                              inlet_pressures,
                              outlet_pressures,
                              flow_rates):
    """Generate lift curve data for a range of conditions."""

    engine = ProcessOptimizationEngine(process)

    results = []
    for p_in in inlet_pressures:
        for p_out in outlet_pressures:
            for q in flow_rates:
                feed = process.getUnit("feed")
                feed.setPressure(float(p_in), "bara")
                feed.setFlowRate(float(q), "kg/hr")
                compressor = process.getUnit("Export Compressor")
                compressor.setOutletPressure(float(p_out))
                # Let simulation/API errors propagate; infeasible means a solved
                # operating point that violates a configured hard constraint.
                process.run()
                report = engine.evaluateAllConstraints()
                statuses = list(report.getEquipmentStatuses())
                feasible = all(status.isWithinLimits() for status in statuses)
                bottleneck = report.getBottleneck()
                power_kw = compressor.getPower("kW")
                utilization = max((s.getUtilization() for s in statuses), default=0.0)
                if not np.isfinite(power_kw) or not np.isfinite(utilization):
                    raise RuntimeError("Non-finite process result")
                results.append({
                    'inlet_pressure': float(p_in),
                    'outlet_pressure': float(p_out),
                    'flow_rate': float(q),
                    'power': power_kw,
                    'efficiency': compressor.getPolytropicEfficiency(),
                    'feasible': feasible,
                    'bottleneck': str(bottleneck.getEquipmentName()) if bottleneck else None,
                    'overall_utilization': utilization,
                })

    return pd.DataFrame(results)


def plot_operating_envelope(df):
    """Plot the equipment operating envelope from lift curve data."""

    fig, axes = plt.subplots(2, 2, figsize=(12, 10))

    # Plot 1: Flow rate vs Power (colored by feasibility)
    ax1 = axes[0, 0]
    colors = ['green' if f else 'red' for f in df['feasible']]
    ax1.scatter(df['flow_rate'], df['power'], c=colors, alpha=0.6)
    ax1.set_xlabel('Flow Rate (kg/hr)')
    ax1.set_ylabel('Power (kW)')
    ax1.set_title('Power vs Flow Rate')
    ax1.axhline(4000.0, color='black', linestyle='--', label='4,000 kW limit')
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    # Plot 2: Inlet Pressure vs Max Flow (envelope)
    ax2 = axes[0, 1]
    feasible_df = df[df['feasible']]
    max_flow_by_pin = feasible_df.groupby('inlet_pressure')['flow_rate'].max()
    ax2.plot(max_flow_by_pin.index, max_flow_by_pin.values, 'b-o', linewidth=2)
    ax2.fill_between(max_flow_by_pin.index, 0, max_flow_by_pin.values, alpha=0.3)
    ax2.set_xlabel('Inlet Pressure (bara)')
    ax2.set_ylabel('Largest Sampled Feasible Flow (kg/hr)')
    ax2.set_title('Sampled Feasible Envelope')
    ax2.grid(True, alpha=0.3)

    # Plot 3: Flow rate vs Utilization
    ax3 = axes[1, 0]
    ax3.scatter(df['flow_rate'], df['overall_utilization'] * 100, alpha=0.6)
    ax3.axhline(y=100, color='r', linestyle='--', label='100% Utilization')
    ax3.axhline(y=90, color='orange', linestyle='--', label='90% Warning')
    ax3.set_xlabel('Flow Rate (kg/hr)')
    ax3.set_ylabel('Overall Utilization (%)')
    ax3.set_title('Utilization vs Flow Rate')
    ax3.legend()
    ax3.grid(True, alpha=0.3)

    # Plot 4: Bottleneck distribution
    ax4 = axes[1, 1]
    bottleneck_counts = df[~df['feasible']]['bottleneck'].value_counts()
    if len(bottleneck_counts) > 0:
        ax4.pie(bottleneck_counts.values, labels=bottleneck_counts.index, autopct='%1.1f%%')
        ax4.set_title('Bottleneck Distribution (Infeasible Cases)')
    else:
        ax4.text(0.5, 0.5, 'All cases feasible', ha='center', va='center')
        ax4.set_title('Bottleneck Distribution')

    plt.tight_layout()
    plt.savefig('operating_envelope.png', dpi=150)
    plt.show()

    return fig


# Main execution
if __name__ == "__main__":
    # Create process
    process = create_compression_process()

    # Define parameter ranges
    inlet_pressures = np.linspace(40, 80, 5)
    outlet_pressures = [150.0]  # Fixed outlet
    flow_rates = np.linspace(20000, 150000, 10)

    # Generate lift curve data
    print("Generating lift curve data...")
    lift_curve_df = generate_lift_curve_data(
        process, inlet_pressures, outlet_pressures, flow_rates
    )

    # Save to CSV
    lift_curve_df.to_csv('lift_curve_data.csv', index=False)
    print(f"Saved {len(lift_curve_df)} data points to lift_curve_data.csv")

    # Print summary
    feasible_count = lift_curve_df['feasible'].sum()
    print(f"\nFeasible operating points: {feasible_count}/{len(lift_curve_df)}")

    # Plot
    plot_operating_envelope(lift_curve_df)
```

### Equipment Constraint Analysis

Save as `equipment_constraint_analysis.py` alongside the basic script and run it.
It writes `constraint_analysis.csv` and `constraint_dashboard.png`. Disabled constraints and observations without configured bounds are excluded.
Omitted equipment has no assessed capacity limit. Utilization is relative to the design value (or an
inverse ratio for a minimum constraint); violation flags use the actual maximum
or minimum, which may differ from the design reference. An empty constraint set
is reported without trying to plot zero subplots.

```python
import sys

import pandas as pd
import matplotlib.pyplot as plt

from neqsim import jneqsim
from basic_process_optimization import create_compression_process

EquipmentCapacityStrategyRegistry = (
    jneqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry
)


def analyze_equipment_constraints(process):
    """Detailed analysis of equipment constraints."""

    registry = EquipmentCapacityStrategyRegistry.getInstance()

    all_constraints = []

    for i in range(process.getUnitOperations().size()):
        equipment = process.getUnitOperations().get(i)
        strategy = registry.findStrategy(equipment)

        if strategy is None:
            continue

        constraints = strategy.getConstraints(equipment)

        for name, constraint in constraints.items():
            if not constraint.isEnabled():
                continue
            if (constraint.getDesignValue() == sys.float_info.max
                    and constraint.getMaxValue() == sys.float_info.max
                    and constraint.getMinValue() <= 0.0):
                continue
            all_constraints.append({
                'equipment': str(equipment.getName()),
                'constraint': str(name),
                'type': str(constraint.getType()),
                'current': constraint.getCurrentValue(),
                'design': constraint.getDesignValue(),
                'max': constraint.getMaxValue(),
                'minimum_constraint': constraint.isMinimumConstraint(),
                'min': constraint.getMinValue(),
                'unit': str(constraint.getUnit()),
                'utilization': constraint.getUtilization(),
                'violated': (
                    constraint.getCurrentValue() < constraint.getMinValue()
                    if constraint.isMinimumConstraint()
                    else constraint.getCurrentValue() > constraint.getMaxValue()
                )
            })

    columns = [
        'equipment', 'constraint', 'type', 'current', 'design', 'max',
        'minimum_constraint', 'min', 'unit', 'utilization', 'violated',
    ]
    return pd.DataFrame(all_constraints, columns=columns)


def plot_constraint_dashboard(df):
    """Create a visual dashboard of constraint status."""

    if df.empty:
        print("No enabled equipment constraints are configured.")
        return None

    # Group by equipment
    equipment_list = df['equipment'].unique()
    n_equipment = len(equipment_list)

    fig, axes = plt.subplots(n_equipment, 1, figsize=(12, 3 * n_equipment))
    if n_equipment == 1:
        axes = [axes]

    for i, equipment in enumerate(equipment_list):
        ax = axes[i]
        eq_df = df[df['equipment'] == equipment]

        # Create horizontal bar chart
        constraints = eq_df['constraint'].values
        utilizations = eq_df['utilization'].values * 100
        violations = eq_df['violated'].values

        colors = ['red' if v else ('orange' if u > 90 else 'green')
                  for u, v in zip(utilizations, violations)]

        y_pos = range(len(constraints))
        bars = ax.barh(y_pos, utilizations, color=colors, alpha=0.7)

        # Add reference lines
        ax.axvline(x=100, color='red', linestyle='--', linewidth=2, label='Design reference')
        ax.axvline(x=90, color='orange', linestyle='--', linewidth=1, label='Warning')

        # Labels
        ax.set_yticks(y_pos)
        ax.set_yticklabels(constraints)
        ax.set_xlabel('Utilization (%)')
        ax.set_title(f'{equipment}')
        ax.set_xlim(0, max(120, max(utilizations) * 1.1))

        # Add value labels
        for bar, util in zip(bars, utilizations):
            ax.text(bar.get_width() + 2, bar.get_y() + bar.get_height()/2,
                   f'{util:.1f}%', va='center')

        ax.grid(True, alpha=0.3, axis='x')
        ax.legend(loc='upper right')

    plt.tight_layout()
    plt.savefig('constraint_dashboard.png', dpi=150)
    plt.show()

    return fig


# Main execution
if __name__ == "__main__":
    # Create and run process
    process = create_compression_process()

    # Analyze constraints
    print("Analyzing equipment constraints...")
    constraint_df = analyze_equipment_constraints(process)

    # Print summary table
    print("\n=== Constraint Summary ===")
    print(constraint_df[['equipment', 'constraint', 'type', 'utilization', 'violated']]
          .to_string(index=False))

    # Save to CSV
    constraint_df.to_csv('constraint_analysis.csv', index=False)

    # Identify critical constraints
    critical = constraint_df[constraint_df['violated']]
    if len(critical) > 0:
        print("\n⚠ CRITICAL CONSTRAINTS:")
        for _, row in critical.iterrows():
            comparison = '<' if row['minimum_constraint'] else '>'
            limit = row['min'] if row['minimum_constraint'] else row['max']
            print(f"  - {row['equipment']}/{row['constraint']}: "
                  f"{row['current']:.2f} {comparison} {limit:.2f} {row['unit']}")

    # Identify near-limit constraints
    near_limit = constraint_df[(constraint_df['utilization'] > 0.9) & (~constraint_df['violated'])]
    if len(near_limit) > 0:
        print("\n⚡ NEAR LIMIT (>90%):")
        for _, row in near_limit.iterrows():
            print(f"  - {row['equipment']}/{row['constraint']}: "
                  f"{row['utilization']*100:.1f}%")

    # Plot dashboard
    plot_constraint_dashboard(constraint_df)
```

---

## Validation

The examples are exercised directly from this Markdown file. The Java regression
test compiles all five Java blocks for Java 8 and runs each entry point. The Python
test executes all three scripts, checks the 50-point sweep, tests empty constraint
sets and error propagation, and verifies the power limit and mass balance.

```bash
./mvnw -Dtest=ProcessOptimizationEngineOperatingPointTest,PracticalOptimizationDocumentationTest test
python -m unittest devtools.test_practical_optimization_examples -v
```

The Python examples were tested with Python 3.12, Java 17, and NeqSim 3.20.0,
and against the corrected Java source. Representative released-package output is
76,743 kg/hr at 4,000 kW for the basic optimization, and 31 feasible points out of
50 in the sweep. The plotted envelope is the largest feasible **sampled** flow at
each inlet pressure, so it is limited by grid spacing and the upper sampled rate.
These are consistency checks for the synthetic examples, not plant qualification.

## See Also

- [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) - Full API documentation
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) - Core constraint system
- [NeqSim Python Guide](https://github.com/equinor/neqsim-python) - Python integration guide
