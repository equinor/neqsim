---
title: LNG Liquefaction Process Models
description: Closed-loop SMR, C3MR, DMR, and nitrogen-expander process templates with common performance and benchmark metrics.
---

# LNG Liquefaction Process Models

NeqSim provides closed-loop screening models for four common LNG liquefaction
routes in the `neqsim.process.lng` package. Each template uses the standard
NeqSim equation-of-state flashes and unit operations, an
`LNGHeatExchanger` for rigorous multi-stream temperature-enthalpy curves,
and explicit `Recycle` units for refrigerant-loop convergence.

## Supported routes

| Cycle | Template scope | Main equipment |
|---|---|---|
| `SMR` | Single mixed-refrigerant loop | Two-stage MR compression, JT expansion, one MCHE |
| `C3MR` | Single-level equivalent propane precooling plus mixed refrigerant | Propane and MR compressor trains, precooler, MCHE |
| `DMR` | Warm and cold mixed-refrigerant loops | Two MR compressor trains, precooler, MCHE |
| `NITROGEN_EXPANDER` | Closed reverse-Brayton nitrogen loop | Two-stage N2 compression, turboexpander, MCHE |

The C3MR template represents one equivalent propane evaporation level. It is
appropriate for route screening and API examples; detailed design studies
should model the actual three- or four-level propane cascade and optimize
refrigerant inventories, pressure levels, and exchanger geometry.

## Quick start

```java
LNGProcessModel model = new LNGProcessBuilder()
    .setName("C3MR screening case")
    .setCycle(LNGProcessCycle.C3MR)
    .setFeedFlowRate(100000.0)
    .setFeedPressure(60.0)
    .setNumberOfZones(16)
    .setAdaptiveRefinement(true)
    .build();

LNGProcessModel.Result result = model.run();
double specificEnergy = result.getSpecificEnergyKWhPerKgLNG();
double lngYield = result.getLNGYield();
double minimumApproach = result.getMinimumInternalTemperatureApproachC();
LNGProcessBenchmark.Assessment benchmark = result.assessBenchmark();
```

Use `setFeedFluid(SystemInterface)` to clone a custom pretreated fluid into a
standalone LNG feed. To connect a larger NeqSim simulation without copying its
state, pass the live stream:

```java
StreamInterface treatedGas = upstreamPipeline.getOutletStream();
LNGProcessModel connectedModel = new LNGProcessBuilder()
    .setName("Integrated LNG train")
    .setFeedStream(treatedGas)
    .setCycle(LNGProcessCycle.DMR)
    .build();
```

The stream reference remains live, so composition, flow, temperature, and
pressure are supplied by the surrounding process. Refrigerant circulation is
initialized from the live stream rate when `build()` is called. Rebuild or
retune the refrigerant inventory after large feed-rate changes. Run a separate
producing process before the LNG model, or use the shared-process form below.
Acid gas, water, mercury, and heavy hydrocarbon removal must be represented
upstream when those contaminants are present.

## Professional integrated flowsheet

The route templates assume pretreated gas at the battery limit, but every
piece of route equipment is explicit: compressor suction scrubbers, two-stage
compressors, intercoolers and aftercoolers, refrigerant valves or an expander,
multi-stream cryogenic exchangers, separators, and recycle tear streams.
Pretreatment and fractionation can be assembled with the normal NeqSim unit
operations and then extended in place:

```java
ProcessSystem plant = new ProcessSystem("Professional LNG plant");

Stream richFeed = new Stream("Rich natural gas", richGasFluid);
richFeed.setFlowRate(30000.0, "kg/hr");
plant.add(richFeed);

PipeBeggsAndBrills inletPipeline =
    new PipeBeggsAndBrills("Feed-gas pipeline", richFeed);
inletPipeline.setLength(20000.0);
inletPipeline.setDiameter(0.40);
plant.add(inletPipeline);

DistillationColumn scrubColumn =
    new DistillationColumn("Heavy-hydrocarbon scrub column", 8, true, true);
scrubColumn.addFeedStream(inletPipeline.getOutletStream(), 4);
scrubColumn.setCondenserTemperature(-35.0, "C");
scrubColumn.setReboilerTemperature(65.0, "C");
plant.add(scrubColumn);

LNGProcessModel model = new LNGProcessBuilder()
    .setName("C3MR train")
    .setCycle(LNGProcessCycle.C3MR)
    .setUpstreamProcess(plant, scrubColumn.getGasOutStream())
    .build();

model.registerOutputStream("NGL", scrubColumn.getLiquidOutStream());
model.run();

StreamInterface lng = model.getOutputStream(LNGProcessModel.LNG_OUTPUT);
StreamInterface flashGas =
    model.getOutputStream(LNGProcessModel.FLASH_GAS_OUTPUT);
StreamInterface ngl = model.getOutputStream("NGL");

List<DistillationColumn> columns =
    model.getEquipment(DistillationColumn.class);
List<Compressor> compressors = model.getEquipment(Compressor.class);
List<Recycle> recycles = model.getEquipment(Recycle.class);
```

This makes the result behave like a professional process-simulation
flowsheet: upstream pipeline hydraulics and columns, the complete refrigeration
train, controls or measurements, and downstream product handling all remain in
one `ProcessSystem`. `getEquipment()` returns the immutable flowsheet-order
unit manifest, while `getEquipment(Class)` selects any NeqSim equipment type.
The representative scrub column above separates an NGL bottoms stream; project
models should add the required acid-gas removal, dehydration, mercury removal,
NGL fractionation, and utility systems for the actual feed specification.

## Common comparison metrics

`LNGProcessModel.Result` reports these metrics on the same basis for every
route:

- liquid LNG rate and mass yield;
- capacity in MTPA at 8000 operating hours per year;
- total compressor, recovered expander, and net shaft power;
- net specific energy in kWh/kg LNG;
- product temperature, pressure, and density;
- minimum internal temperature approach (MITA);
- summed exchanger exergy destruction; and
- wall-clock process execution time.

## Output streams and downstream processing

Every output is a normal NeqSim `StreamInterface` and can feed other unit
operations or process modules. The built-in named outputs are:

- `LNGProcessModel.LNG_OUTPUT` — flashed liquid LNG;
- `LNGProcessModel.FLASH_GAS_OUTPUT` — product flash gas for fuel,
  boil-off-gas handling, or recompression.

```java
StreamInterface lng = model.getOutputStream(LNGProcessModel.LNG_OUTPUT);
StreamInterface flashGas =
    model.getOutputStream(LNGProcessModel.FLASH_GAS_OUTPUT);

PipeBeggsAndBrills lngPipeline =
    new PipeBeggsAndBrills("LNG transfer pipeline", lng);
model.addEquipment(lngPipeline);

Compressor flashGasCompressor =
    new Compressor("Flash gas compressor", flashGas);
model.addEquipment(flashGasCompressor);
```

`getProductStream()` remains a convenience alias for the liquid LNG stream,
and `getFlashGasStream()` exposes the vapor directly. Use
`registerOutputStream(name, stream)` to publish later route-specific or
downstream products through the same registry.

## Accuracy controls

The exchanger performs TP flashes at zone boundaries, applies per-stream
pressure drops, and calculates composite curves, MITA, and zone exergy
destruction. Adaptive refinement now scans every registered stream, so a
phase transition on a refrigerant stream can refine the grid even when the
natural-gas enthalpy curve is smooth.

For higher-fidelity studies:

1. increase `setNumberOfZones` until MITA and specific energy are
   grid-independent;
2. keep adaptive refinement enabled around refrigerant phase changes;
3. set measured pressure drops for every exchanger passage;
4. use the actual pretreated feed composition and an appropriate mixing rule;
5. calibrate compressor and expander efficiencies; and
6. optimize refrigerant composition, circulation rate, and pressure levels.

## Speed controls

Adjacent exchanger zone flashes reuse one thermodynamic state and
`ThermodynamicOperations` object per stream by default. This removes
per-zone clone/allocation overhead and carries the previous converged state
into the next flash. Use
`LNGHeatExchanger.setReuseZoneFlashState(false)` for independent-zone
regression comparisons.

The builder also enables process flash warm starts and optimized execution.
For parameter studies, converge a base case before making small changes, and
verify the fast configuration against a higher-zone reference case.

## Integrated process and pipeline capacity

Use `setUpstreamProcess(processSystem, feedStream)` when inlet pipelines,
pretreatment, fractionation, and the LNG train should execute as one
`ProcessSystem`. The builder extends the supplied system in place. Standard
NeqSim measurement devices, controllers, automation, optimization, mechanical
design, cost, emissions, and reporting APIs remain available through
`model.getProcessSystem()`.

Downstream equipment can be appended directly. This example adds an LNG
product pipeline and evaluates its hydraulic constraints together with every
process equipment constraint:

```java
ProcessSystem plant = new ProcessSystem("Integrated LNG plant");
// Add upstream pipelines and pretreatment to plant.
StreamInterface treatedGas = feedPipeline.getOutletStream();

LNGProcessModel model = new LNGProcessBuilder()
    .setName("LNG train")
    .setUpstreamProcess(plant, treatedGas)
    .setCycle(LNGProcessCycle.C3MR)
    .build();

PipeBeggsAndBrills productPipeline =
    new PipeBeggsAndBrills("LNG product pipeline", model.getProductStream());
productPipeline.setLength(15000.0);
productPipeline.setDiameter(0.50);
model.addEquipment(productPipeline);

LNGProcessModel.CapacityResult capacity =
    model.autoSizeAndEvaluateCapacity(1.20);
BottleneckResult bottleneck = capacity.getBottleneck();
Map<String, Double> rankedUtilization =
    capacity.getRankedUtilizationPercent();
String detailedSnapshot = capacity.getUtilizationSnapshotJson();
```

`autoSizeAndEvaluateCapacity` runs the integrated flowsheet, calls the
standard plant-wide auto-sizing bridge, activates mechanical-design-derived
constraints, reruns, and ranks all enabled constraints. For pipelines this can
include velocity, pressure drop, volume flow, erosion/FIV, and other constraints
provided by the selected pipeline and mechanical-design models. Compressors,
exchangers, separators, valves, pumps, and other constrained equipment are
ranked on the same basis.

For an existing design, set the actual mechanical design limits and call
`model.evaluateCapacity()` after running instead of auto-sizing from the
current operating point. The capacity snapshot is a constraint-utilization
report; a maximum-throughput study should vary the live feed rate with the
NeqSim optimization framework until the first hard or design constraint binds.

## Literature comparison points

These are comparison points, not universal acceptance limits. Feed
composition, ambient conditions, LNG flash definition, driver efficiency,
and flowsheet complexity must match before interpreting deviations.

| Cycle | Reference specific energy (kWh/kg LNG) | Source |
|---|---:|---|
| SMR | 0.2561 | Pereira et al. (2022) |
| C3MR | 0.2548 | Pereira et al. (2022) |
| DMR | 0.2456 | Pereira et al. (2022) |
| Parallel nitrogen expansion | 0.6180 | He et al. (2019) |

Pereira et al. compared optimized SMR, C3MR, DMR, and AP-X cases for the
same 20,000 kg/h feed:
[Energy Conversion and Management 272 (2022) 116364](https://doi.org/10.1016/j.enconman.2022.116364).

The nitrogen reference is the optimized parallel nitrogen expansion case
reported by He et al.:
[Energy 167 (2019) 1-12](https://doi.org/10.1016/j.energy.2018.10.169).

`LNGProcessBenchmark.assess` uses deliberately broad screening envelopes
around these points and also checks product temperature, liquid yield, and
temperature crosses. A passing screening result demonstrates physical
plausibility on this basis; it does not replace project-specific validation
against plant or licensor data.
