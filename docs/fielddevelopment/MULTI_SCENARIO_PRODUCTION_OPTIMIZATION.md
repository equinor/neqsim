---
title: Multi-Scenario Production Optimization
description: VFP table generation with varying GOR and water cut scenarios for reservoir simulation coupling. Enables robust production optimization across changing field conditions.
---

# Multi-Scenario Production Optimization

## Overview

The Multi-Scenario Production Optimization framework generates Vertical Flow Performance (VFP) tables that span different Gas-Oil Ratio (GOR) and Water Cut (WC) conditions. This is essential for reservoir simulation coupling where fluid properties change over time as the field matures.

## Why This Is Useful

### The Problem

Traditional VFP tables assume fixed fluid composition. However, real fields experience:

- **Rising GOR** as pressure depletes and gas breaks out of solution
- **Increasing water cut** as aquifer breakthrough occurs
- **Changing fluid properties** affecting flow performance

Using a single VFP table leads to:
- Inaccurate production forecasts
- Suboptimal well and facility design
- Incorrect economic evaluations

### The Solution

This framework generates multi-dimensional VFP tables with:

| Dimension | Description |
|-----------|-------------|
| Rate | Liquid production rates (stock tank conditions) |
| Outlet Pressure | Separator or manifold pressures |
| Water Cut | 0% to 95% water in liquid |
| GOR | Range from initial to late-field conditions |

The result is a single VFP table file that reservoir simulators (Eclipse, tNavigator) can interpolate across all operating conditions.

## What Is Implemented

### Core Classes

The implementation consists of three main classes in `neqsim.process.util.optimizer`:

#### 1. FluidMagicInput

Reference fluid configuration from E300/FluidMagic exports or NeqSim fluids.

**Key Features:**
- Parse Eclipse E300 fluid exports (FLUIDS section)
- Extract live oil and free gas compositions
- Separate phases at standard conditions (15°C, 1.01325 bara)
- Configure GOR and water cut scenario ranges
- Support linear or logarithmic value spacing

```java
// From E300 file
FluidMagicInput input = FluidMagicInput.fromE300File(
    "path/to/fluid.inc",
    "SRK-EOS"
);

// Or from existing NeqSim fluid
FluidMagicInput input = FluidMagicInput.fromFluid(
    existingFluid,
    150.0,  // Reference GOR (Sm3/Sm3)
    0.0     // Reference water cut
);

// Configure scenarios
input.setGORRange(50.0, 500.0);            // GOR range
input.setNumberOfGORPoints(6);             // 6 GOR values
input.setWaterCutRange(0.0, 0.8);          // WC range  
input.setNumberOfWaterCutPoints(5);        // 5 WC values (0-80%)
```

#### 2. RecombinationFlashGenerator

Generates fluids at different GOR/WC by recombining separated phases.

**Key Features:**
- Recombine stock tank oil and gas to achieve target GOR
- Add water phase for desired water cut
- Thread-safe with result caching
- Validates achievable GOR range

```java
RecombinationFlashGenerator generator = 
    new RecombinationFlashGenerator(fluidInput);

// Generate fluid at GOR=200, WC=30%, 100 m³/h liquid, 80°C, 50 bara
SystemInterface fluid = generator.generateFluid(
    200.0,   // Target GOR (Sm3/Sm3)
    0.30,    // Water cut (fraction)
    100.0,   // Liquid rate (m³/h at std conditions)
    353.15,  // Temperature (K)
    50.0     // Pressure (bara)
);
```

#### 3. MultiScenarioVFPGenerator

Generates complete VFP tables with all scenario combinations.

**Key Features:**
- 4-dimensional VFP generation (rate × pressure × WC × GOR)
- Binary search for minimum inlet pressure at each condition
- Parallel execution for performance
- Eclipse VFPEXP format export
- Comprehensive statistics and feasibility tracking

```java
// Define process factory (creates fresh process for each calculation)
ProcessFactory factory = (fluid, rate) -> {
    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(rate, "m3/hr");
    process.add(feed);
    
    AdiabaticPipe pipe = new AdiabaticPipe("well", feed);
    pipe.setLength(3000.0);
    pipe.setDiameter(0.1);
    process.add(pipe);
    
    return process;
};

// Create VFP generator
MultiScenarioVFPGenerator vfpGen = new MultiScenarioVFPGenerator(
    factory,
    "feed",      // Feed stream name
    "well"       // Outlet equipment name (get outlet stream)
);

// Configure flash generator from fluid input
RecombinationFlashGenerator flashGen = new RecombinationFlashGenerator(fluidInput);
vfpGen.setFlashGenerator(flashGen);

// Configure dimensions
vfpGen.setFlowRates(new double[]{50, 100, 200, 400, 800});  // m³/h
vfpGen.setOutletPressures(new double[]{10, 20, 30, 40});    // bara
vfpGen.setWaterCuts(fluidInput.getWaterCutValues());        // from fluid input
vfpGen.setGORs(fluidInput.getGORValues());                  // from fluid input
vfpGen.setMinInletPressure(50.0);
vfpGen.setMaxInletPressure(500.0);

// Generate table
VFPTable table = vfpGen.generateVFPTable();

// Export to Eclipse format
vfpGen.exportVFPEXP("output/WELL_VFP.inc", 1);
```

### VFP Table Structure

The generated `VFPTable` object provides:

```java
// Get BHP at specific conditions
double bhp = table.getBHP(rateIdx, pressIdx, wcIdx, gorIdx);

// Print a 2D slice (rate vs outlet pressure) at fixed WC/GOR
table.printSlice(wcIdx, gorIdx);

// Statistics
int feasible = table.getFeasibleCount();
int total = table.getTotalCount();
double coverage = (double) feasible / total * 100;
```

### Eclipse VFPEXP Format

The export generates Eclipse-compatible VFP tables:

```
-- Multi-Scenario VFP Table generated by NeqSim
-- Generated: 2024-01-15T10:30:00
-- GOR Range: 50.0 - 500.0 Sm3/Sm3
-- Water Cut Range: 0.0 - 80.0 %

VFPEXP
  1 /                        -- Table number
  3000.0 /                   -- Reference depth (m)
  'LIQ' /                    -- Rate type (liquid)
  'THP' /                    -- Pressure type
  'GOR' /                    -- First interpolation variable
  'WCT' /                    -- Second interpolation variable
  
-- Rates (m3/day)
  50.0  100.0  200.0  400.0  800.0 /
  
-- Outlet pressures (bara)
  10.0  20.0  30.0  40.0 /
  
-- GOR values (Sm3/Sm3)
  50.0  100.0  200.0  300.0  400.0  500.0 /
  
-- Water cut values (fraction)
  0.0  0.2  0.4  0.6  0.8 /

-- BHP data follows...
```

## How It Works

### Phase Recombination Algorithm

1. **Separate reference fluid** at standard conditions (15°C, 1.01325 bara)
2. **Calculate reference GOR** from separated gas and oil volumes
3. **For target GOR:**
   - If target > reference: Add more gas phase
   - If target < reference: Add more oil phase
   - Blend to achieve exact target ratio
4. **Add water** to achieve target water cut
5. **Flash to operating conditions** (T, P)

### VFP Generation Algorithm

For each combination of (rate, outlet_pressure, water_cut, GOR):

1. Generate fluid at operating conditions using recombination
2. Binary search for inlet pressure:
   - Start with pressure bracket [min_inlet, max_inlet]
   - Run process simulation
   - Check if outlet pressure matches target (±tolerance)
   - Narrow bracket until converged
3. Record inlet pressure (BHP) or mark as infeasible
4. Continue to next combination

### Parallel Execution

The generator uses a process factory pattern to enable thread-safe parallel execution:

```java
// Each thread gets its own process instance
@FunctionalInterface
public interface ProcessFactory {
    ProcessSystem createProcess(SystemInterface fluid, double rate);
}
```

## Complete Example

```java
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class VFPGenerationExample {

    public static void main(String[] args) {
        // 1. Create reference fluid (typical North Sea oil)
        SystemInterface referenceFluid = new SystemSrkEos(288.15, 1.01325);
        referenceFluid.addComponent("nitrogen", 0.005);
        referenceFluid.addComponent("CO2", 0.01);
        referenceFluid.addComponent("methane", 0.45);
        referenceFluid.addComponent("ethane", 0.05);
        referenceFluid.addComponent("propane", 0.03);
        referenceFluid.addComponent("n-butane", 0.015);
        referenceFluid.addComponent("n-pentane", 0.01);
        referenceFluid.addComponent("n-hexane", 0.02);
        referenceFluid.addComponent("C7", 0.41);
        referenceFluid.setMixingRule("classic");
        referenceFluid.setMultiPhaseCheck(true);
        
        // 2. Create fluid input with GOR/WC scenarios
        FluidMagicInput fluidInput = FluidMagicInput.fromFluid(
            referenceFluid,
            150.0,  // Initial GOR (Sm3/Sm3)
            0.0     // Initial water cut
        );
        fluidInput.setGORRange(80.0, 400.0);
        fluidInput.setNumberOfGORPoints(5);       // 5 GOR values
        fluidInput.setWaterCutRange(0.0, 0.6);
        fluidInput.setNumberOfWaterCutPoints(4);  // 4 WC values
        
        // 3. Define process factory (well model)
        ProcessFactory wellFactory = (fluid, rate) -> {
            ProcessSystem process = new ProcessSystem();
            
            Stream wellhead = new Stream("wellhead", fluid);
            wellhead.setFlowRate(rate, "m3/hr");
            process.add(wellhead);
            
            AdiabaticPipe tubing = new AdiabaticPipe("tubing", wellhead);
            tubing.setLength(2500.0);           // 2500m TVD
            tubing.setDiameter(0.10);           // 4" tubing
            tubing.setInletElevation(0.0);
            tubing.setOutletElevation(2500.0);  // Vertical well
            process.add(tubing);
            
            return process;
        };
        
        // 4. Create VFP generator
        RecombinationFlashGenerator flashGen = new RecombinationFlashGenerator(fluidInput);
        MultiScenarioVFPGenerator vfpGen = new MultiScenarioVFPGenerator(
            wellFactory, "wellhead", "tubing"
        );
        vfpGen.setFlashGenerator(flashGen);
        
        // 5. Configure rate and pressure dimensions
        vfpGen.setFlowRates(new double[]{50, 100, 200, 400, 600, 800});
        vfpGen.setOutletPressures(new double[]{15, 20, 30, 40, 50});
        vfpGen.setWaterCuts(fluidInput.getWaterCutValues());
        vfpGen.setGORs(fluidInput.getGORValues());
        vfpGen.setMinInletPressure(80.0);
        vfpGen.setMaxInletPressure(450.0);
        vfpGen.setPressureTolerance(0.5);  // 0.5 bar tolerance
        
        // 6. Generate VFP table
        System.out.println("Generating multi-scenario VFP table...");
        MultiScenarioVFPGenerator.VFPTable table = vfpGen.generateVFPTable();
        
        // 7. Report results
        System.out.println("\n=== VFP Generation Complete ===");
        System.out.println("Feasible points: " + table.getFeasibleCount() 
            + " / " + table.getTotalCount());
        
        // 8. Print sample slice
        System.out.println("\nSample: WC=0%, GOR=" + fluidInput.getGORValues()[2]);
        table.printSlice(0, 2);
        
        // 9. Export to Eclipse
        vfpGen.exportVFPEXP("WELL_A_VFP.inc", 1);
        System.out.println("\nExported to WELL_A_VFP.inc");
    }
}
```

## Use Cases

### 1. Reservoir Simulation Coupling

Generate VFP tables for Eclipse/tNavigator that automatically interpolate:

```java
// Configure for full field life
fluidInput.setGORRange(100.0, 800.0);
fluidInput.setNumberOfGORPoints(8);   // Initial to blowdown
fluidInput.setWaterCutRange(0.0, 0.95);
fluidInput.setNumberOfWaterCutPoints(10); // Dry to wet
```

### 2. Facility Debottlenecking

Evaluate facility capacity across fluid scenarios:

```java
// Fixed facility, varying fluids
for (double gor : gorValues) {
    for (double wc : wcValues) {
        SystemInterface fluid = generator.generateFluid(gor, wc, rate, T, P);
        // Evaluate separator, compressor capacity
    }
}
```

### 3. Well Design Optimization

Compare tubing sizes across production scenarios:

```java
for (double diameter : new double[]{0.076, 0.10, 0.127}) {
    ProcessFactory factory = createWellFactory(diameter);
    VFPTable table = generateVFP(factory, fluidInput);
    // Compare deliverability
}
```

## Configuration Options

### GOR Range Configuration

```java
// Linear spacing
fluidInput.setGORRange(50.0, 500.0);
fluidInput.setNumberOfGORPoints(6);
fluidInput.setGorSpacing(FluidMagicInput.GORSpacing.LINEAR);
// Results: [50, 140, 230, 320, 410, 500]

// Logarithmic spacing (better for wide ranges) - this is the default
fluidInput.setGORRange(10.0, 1000.0);
fluidInput.setNumberOfGORPoints(5);
fluidInput.setGorSpacing(FluidMagicInput.GORSpacing.LOGARITHMIC);
// Results: [10, 31.6, 100, 316, 1000]
```

### Water Cut Range Configuration

```java
// Linear spacing
fluidInput.setWaterCutRange(0.0, 0.8);
fluidInput.setNumberOfWaterCutPoints(5);
// Results: [0.0, 0.2, 0.4, 0.6, 0.8]
```

### Pressure Search Configuration

```java
vfpGen.setMinInletPressure(50.0);    // Min BHP to search
vfpGen.setMaxInletPressure(500.0);   // Max BHP to search
vfpGen.setPressureTolerance(0.5);    // Pressure tolerance (bar)
```

## API Reference

### FluidMagicInput

| Method | Description |
|--------|-------------|
| `fromE300File(path, eosType)` | Parse E300 fluid export file |
| `fromFluid(fluid, gor, wc)` | Create from NeqSim fluid |
| `builder()` | Get builder for custom configuration |
| `setGORRange(min, max)` | Set GOR range (Sm³/Sm³) |
| `setNumberOfGORPoints(count)` | Set number of GOR values |
| `setGorSpacing(spacing)` | Set LINEAR or LOGARITHMIC spacing |
| `setWaterCutRange(min, max)` | Set water cut range (0-1) |
| `setNumberOfWaterCutPoints(count)` | Set number of WC values |
| `separateToStandardConditions()` | Separate into gas/oil/water |
| `getGORValues()` | Get configured GOR array |
| `getWaterCutValues()` | Get configured water cut array |

### RecombinationFlashGenerator

| Method | Description |
|--------|-------------|
| `generateFluid(gor, wc, rate, T, P)` | Generate fluid at conditions |
| `validateGOR(gor)` | Check if GOR is achievable |
| `clearCache()` | Clear cached results |
| `getCacheStatistics()` | Get cache hit/miss stats |

### MultiScenarioVFPGenerator

| Method | Description |
|--------|-------------|
| `setFlashGenerator(gen)` | Set recombination flash generator |
| `setFlowRates(rates)` | Set rate dimension (m³/h) |
| `setWaterCuts(wcs)` | Set water cut values (0-1) |
| `setGORs(gors)` | Set GOR values (Sm³/Sm³) |
| `setOutletPressures(pressures)` | Set outlet pressure dimension (bara) |
| `generateVFPTable()` | Generate complete VFP table |
| `exportVFPEXP(path, tableNum)` | Export to Eclipse format |
| `toVFPEXPString(tableNum)` | Get Eclipse format as string |

### VFPTable

| Method | Description |
|--------|-------------|
| `getBHP(r, p, w, g)` | Get BHP at indices |
| `printSlice(wcIdx, gorIdx)` | Print 2D slice |
| `getFeasibleCount()` | Count feasible points |
| `getTotalCount()` | Total point count |

## Performance Guidelines

| VFP Points | Estimated Time | Recommendation |
|------------|----------------|----------------|
| < 100 | < 1 min | Quick testing |
| 100-500 | 1-5 min | Standard runs |
| 500-2000 | 5-30 min | Enable parallel execution |
| > 2000 | 30+ min | Use parallel + batch processing |

For large VFP tables:

```java
// Enable parallel execution
vfpGen.setEnableParallel(true);
vfpGen.setNumberOfWorkers(Runtime.getRuntime().availableProcessors());
```

## Input Validation

Before running large VFP generations, validate your setup:

```java
// 1. Verify fluid input is valid
fluidInput.separateToStandardConditions();
System.out.println("Reference GOR: " + fluidInput.getBaseCaseGOR() + " Sm3/Sm3");
System.out.println("Achievable GOR range: " + fluidInput.getMinGOR() 
    + " - " + fluidInput.getMaxGOR());

// 2. Create flash generator and test a single point
RecombinationFlashGenerator flashGen = new RecombinationFlashGenerator(fluidInput);
double testGOR = (fluidInput.getMinGOR() + fluidInput.getMaxGOR()) / 2;
SystemInterface testFluid = flashGen.generateFluid(testGOR, 0.1, 100.0, 353.15, 100.0);
if (testFluid == null) {
    throw new RuntimeException("Fluid generation failed - check GOR range");
}
System.out.println("Test fluid phases: " + testFluid.getNumberOfPhases());

// 3. Verify process runs
ProcessSystem testProcess = factory.createProcess(testFluid, 100.0);
testProcess.run();
System.out.println("Test outlet pressure: " 
    + testProcess.getUnit("tubing").getOutletStream().getPressure("bara") + " bara");
```

## Troubleshooting

### "GOR out of achievable range" Error

The target GOR must be within the achievable range based on the reference fluid composition. The achievable range depends on the gas-to-oil ratio in the separated phases.

```java
// Check achievable range
RecombinationFlashGenerator gen = new RecombinationFlashGenerator(fluidInput);
boolean valid = gen.validateGOR(targetGOR);
if (!valid) {
    System.out.println("GOR " + targetGOR + " is outside achievable range");
}
```

### Low VFP Feasibility (< 50%)

If many VFP points are infeasible:

1. **Increase `maxInletPressure`** - BHP may be hitting the ceiling for high rates
2. **Decrease `minInletPressure`** - Low rates may need lower BHP to converge
3. **Check rate/pressure combinations** - Some combinations are physically unrealistic
4. **Review well model** - Ensure tubing dimensions and elevations are correct

### Slow Generation

1. Enable parallel execution: `vfpGen.setEnableParallel(true)`
2. Reduce initial testing points: Start with 3-4 values per dimension
3. Increase pressure tolerance: `vfpGen.setPressureTolerance(1.0)` for faster convergence

### Fluid Generation Returns Null

1. Check that `separateToStandardConditions()` was called on the fluid input
2. Verify GOR is within achievable range
3. Ensure water cut is between 0 and 1

## Using VFP in Eclipse Reservoir Simulator

Include the generated VFP file in your Eclipse DATA file:

```
-- Include multi-scenario VFP table
INCLUDE
  'WELL_A_VFP.inc' /

-- Reference VFP table in well controls
WCONPROD
  'WELL_A' OPEN LRAT 500.0 4* 1 /   -- VFP table 1
/

-- For injection wells
WCONINJE
  'INJ_1' WATER OPEN RATE 1000.0 1* 200.0 1 /
/
```

The reservoir simulator will automatically interpolate across:
- **GOR dimension** - Based on producing GOR from the reservoir
- **Water cut dimension** - Based on water cut from the reservoir
- **Rate and THP dimensions** - Based on operating conditions

## Related Documentation

- [Field Development Module](index.md) - Overview of field development tools
- [VFP Tables Guide](../process/vfp-tables.md) - Standard VFP generation
- [Thermodynamic Systems](../thermo/index.md) - Fluid modeling
- [Pipeline Simulation](../fluidmechanics/index.md) - Flow modeling

## Source Code

- [FluidMagicInput.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/process/util/optimizer/FluidMagicInput.java)
- [RecombinationFlashGenerator.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/process/util/optimizer/RecombinationFlashGenerator.java)
- [MultiScenarioVFPGenerator.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/process/util/optimizer/MultiScenarioVFPGenerator.java)
