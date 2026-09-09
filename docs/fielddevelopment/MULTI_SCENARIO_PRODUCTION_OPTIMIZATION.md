---
title: Multi-Scenario Production Optimization
description: Generate and inspect pressure-performance grids across GOR and water-cut scenarios, with explicit total-fluid flow units and reservoir-export limitations.
---

## Overview

`FluidMagicInput`, `RecombinationFlashGenerator`, and `MultiScenarioVFPGenerator`
combine fluid scenarios with a process pressure search. The grid axes are flow rate,
outlet pressure, water cut, and gas-oil ratio (GOR).

Changes in producing GOR and water cut can change pressure loss and facility capacity.
Use multiple fluid scenarios to screen those effects, and validate the recombined fluids
and hydraulic model before using the grid for a field decision.

The current generator sets **total-fluid flow**, using the unit selected with
`setFlowRateUnit(...)`. Its default is `Sm3/day`; this is not stock-tank liquid flow.
The example explicitly uses `kg/hr` to avoid confusing standard gas-equivalent volume,
actual multiphase volume, and stock-tank liquid production.

The legacy `exportVFPEXP(...)` method writes a text representation. It hardcodes a
`LIQ` header and does not convert total-fluid rates to a reservoir simulator's liquid
rate basis. Its output has not been validated here as an Eclipse or tNavigator input
deck. Export the grid to CSV for review; a reservoir adapter must convert units and
validate keyword syntax, datum depth, and interpolation before loading a simulator.

## What Is Implemented

### FluidMagicInput

Create the input from a NeqSim fluid, then separate it at 15 °C and 1.01325 bara before
constructing the recombination generator. `fromFluid(...)` does not perform that separation.

```java
FluidMagicInput fluidInput = FluidMagicInput.fromFluid(referenceFluid);
fluidInput.setGORRange(80.0, 200.0);       // Sm3 gas / Sm3 oil
fluidInput.setNumberOfGORPoints(2);
fluidInput.setWaterCutRange(0.0, 0.3);    // water / (oil + water), by volume
fluidInput.setNumberOfWaterCutPoints(2);
fluidInput.separateToStandardConditions();
```

An E300 file is an alternative source, not a second declaration of the same variable:

```java
FluidMagicInput fileInput = FluidMagicInput.fromE300File("path/to/fluid.inc");
fileInput.separateToStandardConditions();
```

The file example requires a real local E300 fluid export. It is not needed to run the
complete synthetic example below.

### RecombinationFlashGenerator

`generateFluid(gor, waterCut, liquidRate, temperature, pressure)` uses a requested
stock-tank liquid rate in Sm3/hr to construct the recombined fluid. The separate VFP
generator subsequently resets its feed's **total** flow in the configured flow unit.
Do not equate these two rate arguments.

```java
RecombinationFlashGenerator generator = new RecombinationFlashGenerator(fluidInput);
SystemInterface fluid = generator.generateFluid(
    200.0,   // Target GOR, Sm3/Sm3
    0.30,    // Water cut, fraction
    100.0,   // Requested stock-tank liquid rate, Sm3/hr
    353.15,  // Temperature, K
    50.0     // Pressure, bara
);

// Verify the achieved standard-condition GOR at this water cut, within 5%.
boolean gorVerified = generator.validateGOR(200.0, 0.30, 0.05);
logger.info("Recombined GOR verified: {}", gorVerified);
```

`validateGOR` takes three arguments and checks the *achieved* GOR after flashing.
It is not a one-argument feasibility-range query. Scenario minimum and maximum GOR
settings specify requested axes; they do not establish an achievable composition range.

### MultiScenarioVFPGenerator

The constructor requires names of registered **streams** for the feed and outlet.
Register the pipe's outlet stream in `ProcessSystem` and give that stream a name;
passing the pipe equipment's name produces infeasible points.

```java
pipe.getOutletStream().setName("outlet");
process.add(pipe.getOutletStream());
MultiScenarioVFPGenerator vfpGen = new MultiScenarioVFPGenerator(
    factory, "feed", "outlet"
);
vfpGen.setFlashGenerator(generator);
vfpGen.setFlowRateUnit("kg/hr");
vfpGen.setFlowRates(new double[] {1000.0, 3000.0});
vfpGen.setOutletPressures(new double[] {20.0, 30.0});
vfpGen.setWaterCuts(fluidInput.generateWaterCutValues());
vfpGen.setGORs(fluidInput.generateGORValues());
vfpGen.setMinInletPressure(5.0);
vfpGen.setMaxInletPressure(150.0);
vfpGen.setPressureTolerance(0.2);
MultiScenarioVFPGenerator.VFPTable table = vfpGen.generateVFPTable();
```

These fragments use the imports, logger, and process factory in the complete example.
`Supplier<ProcessSystem>.get()` creates a process; it has no `createProcess(...)` method.
Each supplied process must have independent streams and equipment for parallel use.

## Complete Example

Save the following as `VFPGenerationExample.java` in a NeqSim Java project. It runs
16 synthetic cases and writes a CSV containing all pressure results and feasibility flags.
The simple `AdiabaticPipe` is a screening pressure-drop model; qualify a suitable
multiphase hydraulic model and elevation profile for a real well.

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.FluidMagicInput;
import neqsim.process.util.optimizer.MultiScenarioVFPGenerator;
import neqsim.process.util.optimizer.RecombinationFlashGenerator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class VFPGenerationExample {
    private static final Logger logger = LogManager.getLogger(VFPGenerationExample.class);

    public static void main(String[] args) throws IOException {
        SystemInterface referenceFluid = new SystemSrkEos(288.15, 1.01325);
        referenceFluid.addComponent("methane", 0.5);
        referenceFluid.addComponent("n-heptane", 0.5);
        referenceFluid.setMixingRule("classic");
        referenceFluid.setMultiPhaseCheck(true);

        FluidMagicInput fluidInput = FluidMagicInput.fromFluid(referenceFluid);
        fluidInput.setGORRange(80.0, 200.0);
        fluidInput.setNumberOfGORPoints(2);
        fluidInput.setWaterCutRange(0.0, 0.3);
        fluidInput.setNumberOfWaterCutPoints(2);
        fluidInput.separateToStandardConditions();

        Supplier<ProcessSystem> factory = () -> {
            ProcessSystem process = new ProcessSystem();
            Stream feed = new Stream("feed", referenceFluid.clone());
            feed.setFlowRate(1000.0, "kg/hr");
            process.add(feed);
            AdiabaticPipe pipe = new AdiabaticPipe("pipe", feed);
            pipe.setLength(1000.0);
            pipe.setDiameter(0.15);
            process.add(pipe);
            pipe.getOutletStream().setName("outlet");
            process.add(pipe.getOutletStream());
            return process;
        };

        MultiScenarioVFPGenerator generator = new MultiScenarioVFPGenerator(
            factory, "feed", "outlet"
        );
        generator.setFlashGenerator(new RecombinationFlashGenerator(fluidInput));
        generator.setFlowRateUnit("kg/hr");
        generator.setFlowRates(new double[] {1000.0, 3000.0});
        generator.setOutletPressures(new double[] {20.0, 30.0});
        generator.setWaterCuts(fluidInput.generateWaterCutValues());
        generator.setGORs(fluidInput.generateGORValues());
        generator.setInletTemperature(353.15);
        generator.setMinInletPressure(5.0);
        generator.setMaxInletPressure(150.0);
        generator.setPressureTolerance(0.2);
        generator.setEnableParallel(false);

        MultiScenarioVFPGenerator.VFPTable table = generator.generateVFPTable();
        if (table.getFeasibleCount() != table.getTotalPoints()) {
            throw new IllegalStateException("Synthetic grid contains infeasible points");
        }
        logger.info("Feasible pressure points: {} / {}",
            table.getFeasibleCount(), table.getTotalPoints());

        StringBuilder csv = new StringBuilder(
            "total_flow_kg_hr,outlet_pressure_bara,water_cut,gor_sm3_sm3,inlet_pressure_bara,feasible\n"
        );
        for (int r = 0; r < generator.getFlowRates().length; r++) {
            for (int p = 0; p < generator.getOutletPressures().length; p++) {
                for (int w = 0; w < generator.getWaterCuts().length; w++) {
                    for (int g = 0; g < generator.getGORs().length; g++) {
                        csv.append(generator.getFlowRates()[r]).append(',')
                            .append(generator.getOutletPressures()[p]).append(',')
                            .append(generator.getWaterCuts()[w]).append(',')
                            .append(generator.getGORs()[g]).append(',')
                            .append(table.getBHP(r, p, w, g)).append(',')
                            .append(table.isFeasible(r, p, w, g)).append('\n');
                    }
                }
            }
        }
        String outputFile = args.length > 0 ? args[0] : "vfp_grid.csv";
        Files.write(Paths.get(outputFile), csv.toString().getBytes(StandardCharsets.UTF_8));
        logger.info("Saved {}", outputFile);
    }
}
```

Use only registered component names such as `n-heptane`. A petroleum fraction called
`C7` must first be characterized and added as a TBP/plus fraction with its required
properties; it is not a database component that `addComponent("C7", ...)` can resolve.

## Validation and rate consistency

The complete synthetic grid produces 16 feasible points. Recombination checks with the same
reference fluid reproduce dry GORs of 80 and 200. At 30% requested water cut the achieved
GORs are approximately 81.04 and 202.65, and water cuts are 0.29955 and 0.29891, because
adding water changes phase equilibrium. Total stock-tank liquid rates of 1000 and 2000
Sm3/hr are recovered after a standard-condition flash on both cache misses and hits.

The separated reference volumes must retain their equilibrium phase identities. Reinitializing
an extracted phase with `init(0)` resets phase information and changes its volume; the current
implementation preserves that phase state. Recombination uses the same standard-condition
normalization for fresh and cached fluids and converts hourly rates to the mol/s basis used
by the simulation. Cache keys retain the exact GOR and water-cut values.

## How It Works

For each rate, outlet-pressure, water-cut, and GOR combination, the generator creates a
fresh process, recombines a fluid, and searches for the lowest inlet pressure whose
calculated outlet pressure is at least the target. It first checks the maximum inlet
pressure, then bisects the inlet-pressure bracket until its width meets the tolerance.

This is an inequality search, not a guarantee that the outlet residual equals zero.
If the lower inlet-pressure bound already exceeds the required pressure, the result
is limited by that bound. Inspect pressure residuals and choose a bracket that covers
the physical solution. A failed simulation or unreachable target is stored as an
infeasible point with a non-finite pressure.

### VFP Table Structure

```java
double pressure = table.getBHP(0, 0, 0, 0);  // required inlet pressure, bara
boolean feasible = table.isFeasible(0, 0, 0, 0);
int total = table.getTotalPoints();
int feasibleCount = table.getFeasibleCount();
logger.info("Pressure={} bara, feasible={}, coverage={}/{}",
    pressure, feasible, feasibleCount, total);
```

## Configuration Options

GOR axes support linear or logarithmic spacing; logarithmic spacing is the default.
Water-cut axes use linear spacing.

```java
fluidInput.setGORRange(50.0, 500.0);
fluidInput.setNumberOfGORPoints(6);
fluidInput.setGorSpacing(FluidMagicInput.GORSpacing.LINEAR);
// Requested GORs: [50, 140, 230, 320, 410, 500]

fluidInput.setWaterCutRange(0.0, 0.8);
fluidInput.setNumberOfWaterCutPoints(5);
// Requested water cuts: [0.0, 0.2, 0.4, 0.6, 0.8]
```

Apply updated axes to the VFP generator before regenerating its table.

```java
generator.setGORs(fluidInput.generateGORValues());
generator.setWaterCuts(fluidInput.generateWaterCutValues());
generator.setEnableParallel(true);
generator.setNumberOfWorkers(2);
```

Start with a small grid. Runtime depends on phase behavior, hydraulic model, pressure
bracket, and iteration tolerance; measure it before choosing a large grid or worker count.

## Input Validation

Run a supplied process directly before launching the grid. Use `factory.get()`, replace
the registered feed fluid, set its total mass rate, and read the named outlet stream:

```java
ProcessSystem trial = factory.get();
Stream feed = (Stream) trial.getUnit("feed");
RecombinationFlashGenerator recombination = new RecombinationFlashGenerator(fluidInput);
feed.setFluid(recombination.generateFluid(200.0, 0.3, 100.0, 353.15, 100.0));
feed.setFlowRate(1000.0, "kg/hr");
trial.run();
Stream outlet = (Stream) trial.getUnit("outlet");
logger.info("Trial outlet pressure: {} bara", outlet.getPressure("bara"));
```

Check that the reference fluid has gas and oil phases at standard conditions, that the
requested GOR is reproduced within an appropriate tolerance, and that the water-cut
values lie between zero and one. Check mass conservation and physically reasonable
pressure losses at representative corners of the grid.

## Eclipse VFPEXP Format

The existing legacy writer can be inspected with the actual methods below after generation:

```java
String legacyText = generator.toVFPEXPString(1);
generator.exportVFPEXP("vfp_legacy_review.txt", 1);
```

The write operation requires handling `IOException`, as in the complete example. The
legacy text retains the supplied rate values, emits a `VFPEXP` keyword, a hardcoded
`LIQ` rate label and a zero datum depth. It is not a validated conversion of this
mass-rate grid to an Eclipse production VFP table. Do not include this text directly
in a reservoir model based only on its filename. Validate a simulator-specific adapter
against that simulator's supported VFP keyword, units, indexing, datum, and pressure
convention. Injection controls also require an appropriate injection table; the
production GOR/water-cut grid does not establish that compatibility.

## API Reference

| Class | Method | Meaning |
|-------|--------|---------|
| `FluidMagicInput` | `fromFluid(fluid)` / `fromE300File(path)` | Create reference input |
| `FluidMagicInput` | `separateToStandardConditions()` | Prepare gas, oil, and water reference phases |
| `FluidMagicInput` | `generateGORValues()` / `generateWaterCutValues()` | Generate requested scenario axes |
| `RecombinationFlashGenerator` | `generateFluid(gor, wc, liquidRate, T, P)` | Recombine and flash a scenario |
| `RecombinationFlashGenerator` | `validateGOR(gor, wc, relativeTolerance)` | Check achieved standard-condition GOR |
| `RecombinationFlashGenerator` | `clearCache()` / `getCacheStatistics()` | Control and inspect recombination cache |
| `MultiScenarioVFPGenerator` | `setFlowRateUnit(unit)` | Select total-fluid rate unit |
| `MultiScenarioVFPGenerator` | `generateVFPTable()` | Generate the pressure-performance grid |
| `VFPTable` | `getBHP(r, p, w, g)` / `isFeasible(r, p, w, g)` | Inspect a grid point |
| `VFPTable` | `getFeasibleCount()` / `getTotalPoints()` | Inspect grid coverage |

## Related Documentation

- [Field Development Module](index.md)
- [Pressure Boundary Optimization](../process/pressure_boundary_optimization.md)
- [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [Thermodynamic Systems](../thermo/index.md)
- [Pipeline Simulation](../fluidmechanics/index.md)
