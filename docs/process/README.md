# Process Simulation Package

The `process` package provides process equipment, unit operations, controllers, and process system management for building complete flowsheets.

## Table of Contents
- [Overview](#overview)
- [Documentation Structure](#documentation-structure)
- [Package Structure](#package-structure)
- [ProcessSystem](#processsystem)
- [Equipment Categories](#equipment-categories)
- [Controllers and Logic](#controllers-and-logic)
- [Safety Systems](#safety-systems)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process`

**Purpose:**
- Model process equipment (separators, heat exchangers, compressors, etc.)
- Build process flowsheets with `ProcessSystem`
- Implement control logic and adjusters
- Simulate dynamic and steady-state processes
- Safety system modeling (PSV, ESD, blowdown)

---

## Documentation Structure

This documentation is organized into the following sections:

| Section | Description |
|---------|-------------|
| [equipment/](equipment/) | Equipment documentation (separators, compressors, etc.) |
| [processmodel/](processmodel/) | ProcessSystem and flowsheet management |
| [safety/](safety/) | Safety systems (PSV, ESD, blowdown) |
| [controllers.md](controllers.md) | Process controllers and logic |

### Process Design Guide

| Document | Description |
|----------|-------------|
| [process_design_guide.md](process_design_guide.md) | **Complete guide to process design workflow using NeqSim** |

### Design Framework (NEW) ✨

| Document | Description |
|----------|-------------|
| [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK.md) | **Automated equipment sizing and optimization framework** |
| [OPTIMIZATION_IMPROVEMENT_PROPOSAL.md](OPTIMIZATION_IMPROVEMENT_PROPOSAL.md) | Implementation status and roadmap |

**Key Features:**
- `AutoSizeable` interface - Equipment auto-sizing from flow requirements
- `DesignSpecification` - Builder pattern for equipment configuration
- `ProcessTemplate` - Reusable process configurations
- `DesignOptimizer` - Design-to-optimization workflow
- Integration with MechanicalDesign and company TR documents

### Capacity Constraint Framework

| Document | Description |
|----------|-------------|
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](CAPACITY_CONSTRAINT_FRAMEWORK.md) | **Framework for equipment capacity limits, bottleneck detection, and utilization tracking** |

### Mechanical Design Documentation

| Document | Description |
|----------|-------------|
| [EQUIPMENT_DESIGN_PARAMETERS.md](EQUIPMENT_DESIGN_PARAMETERS.md) | **Equipment design parameters, autoSize vs MechanicalDesign guide** |
| [mechanical_design_standards.md](mechanical_design_standards.md) | Design standards (NORSOK, ASME, API, DNV, etc.) |
| [mechanical_design_database.md](mechanical_design_database.md) | Data sources, database schemas, and CSV configuration |
| [pipeline_mechanical_design.md](pipeline_mechanical_design.md) | Pipeline mechanical design (wall thickness, stress, buckling) |
| [topside_piping_design.md](topside_piping_design.md) | **Topside piping design (velocity, support, vibration per ASME B31.3)** |
| [riser_mechanical_design.md](riser_mechanical_design.md) | Riser design (catenary, VIV, fatigue per DNV-OS-F201) |
| [torg_integration.md](torg_integration.md) | Technical Requirements Documents (TORG) integration |
| [field_development_orchestration.md](field_development_orchestration.md) | Complete design workflow orchestration |

### Equipment Categories

| Category | Documentation | Classes |
|----------|--------------|---------|
| Streams | [streams.md](equipment/streams.md) | Stream, EnergyStream, VirtualStream |
| Separators | [separators.md](equipment/separators.md) | Separator, ThreePhaseSeparator, GasScrubber |
| Heat Exchangers | [heat_exchangers.md](equipment/heat_exchangers.md) | Heater, Cooler, HeatExchanger |
| Compressors | [compressors.md](equipment/compressors.md) | Compressor, CompressorChart |
| Pumps | [pumps.md](equipment/pumps.md) | Pump, PumpChart |
| Expanders | [expanders.md](equipment/expanders.md) | Expander, TurboExpanderCompressor |
| Valves | [valves.md](equipment/valves.md) | ThrottlingValve, SafetyValve, BlowdownValve |
| Distillation | [distillation.md](equipment/distillation.md) | DistillationColumn, SimpleTray |
| Absorbers | [absorbers.md](equipment/absorbers.md) | SimpleAbsorber, SimpleTEGAbsorber |
| Ejectors | [ejectors.md](equipment/ejectors.md) | Ejector |
| Membranes | [membranes.md](equipment/membranes.md) | MembraneSeparator |
| Flares | [flares.md](equipment/flares.md) | Flare, FlareStack |
| Electrolyzers | [electrolyzers.md](equipment/electrolyzers.md) | Electrolyzer, CO2Electrolyzer |
| Filters | [filters.md](equipment/filters.md) | Filter, CharCoalFilter |
| Reactors | [reactors.md](equipment/reactors.md) | GibbsReactor |
| Pipelines | [pipelines.md](equipment/pipelines.md) | Pipeline, AdiabaticPipe, TopsidePiping, Riser |
| Tanks | [tanks.md](equipment/tanks.md) | Tank, VesselDepressurization |
| Wells | [wells.md](equipment/wells.md) | Well equipment |
| Mixers/Splitters | [mixers_splitters.md](equipment/mixers_splitters.md) | Mixer, Splitter |
| Utility | [util/](equipment/util/) | Adjuster, Recycle, Calculator |

---

## Package Structure

```
process/
├── SimulationBaseClass.java         # Base class for simulations
├── SimulationInterface.java         # Simulation interface
│
├── equipment/                        # Process equipment
│   ├── ProcessEquipmentBaseClass.java
│   ├── ProcessEquipmentInterface.java
│   ├── TwoPortEquipment.java         # Equipment with inlet/outlet
│   ├── EquipmentFactory.java         # Factory for creating equipment
│   │
│   ├── stream/                       # Streams
│   │   ├── Stream.java
│   │   ├── StreamInterface.java
│   │   ├── EnergyStream.java
│   │   └── VirtualStream.java
│   │
│   ├── separator/                    # Separators
│   │   ├── Separator.java
│   │   ├── ThreePhaseSeparator.java
│   │   ├── GasScrubber.java
│   │   └── SeparatorInterface.java
│   │
│   ├── heatexchanger/               # Heat transfer
│   │   ├── Heater.java
│   │   ├── Cooler.java
│   │   ├── HeatExchanger.java
│   │   ├── NeqHeater.java
│   │   └── Condenser.java
│   │
│   ├── compressor/                  # Compression
│   │   ├── Compressor.java
│   │   ├── CompressorInterface.java
│   │   └── CompressorChartInterface.java
│   │
│   ├── pump/                        # Pumps
│   │   ├── Pump.java
│   │   └── PumpInterface.java
│   │
│   ├── expander/                    # Expanders
│   │   ├── Expander.java
│   │   └── ExpanderInterface.java
│   │
│   ├── valve/                       # Valves
│   │   ├── ThrottlingValve.java
│   │   ├── ValveInterface.java
│   │   └── SafetyValve.java
│   │
│   ├── mixer/                       # Mixers
│   │   ├── Mixer.java
│   │   ├── StaticMixer.java
│   │   └── MixerInterface.java
│   │
│   ├── splitter/                    # Splitters
│   │   ├── Splitter.java
│   │   └── SplitterInterface.java
│   │
│   ├── distillation/                # Distillation
│   │   ├── DistillationColumn.java
│   │   ├── SimpleTray.java
│   │   ├── Condenser.java
│   │   └── Reboiler.java
│   │
│   ├── reactor/                     # Reactors
│   │   ├── Reactor.java
│   │   └── PFReactor.java
│   │
│   ├── absorber/                    # Absorption
│   │   ├── Absorber.java
│   │   └── SimpleTEGAbsorber.java
│   │
│   ├── pipeline/                    # Pipelines
│   │   ├── Pipeline.java
│   │   └── PipelineInterface.java
│   │
│   ├── well/                        # Wells
│   │   ├── SimpleWell.java
│   │   └── WellFlow.java
│   │
│   ├── tank/                        # Tanks and vessels
│   │   ├── Tank.java
│   │   └── ProcessVessel.java
│   │
│   ├── filter/                      # Filters
│   │   └── Filter.java
│   │
│   ├── membrane/                    # Membranes
│   │   └── Membrane.java
│   │
│   ├── ejector/                     # Ejectors
│   │   └── Ejector.java
│   │
│   ├── electrolyzer/                # Electrolyzers
│   │   └── PEM_Electrolyzer.java
│   │
│   └── util/                        # Utility equipment
│       ├── Adjuster.java
│       ├── Recycle.java
│       ├── Calculator.java
│       ├── Setter.java
│       └── MoleFractionSetter.java
│
├── processmodel/                    # Process system
│   ├── ProcessSystem.java
│   ├── ProcessModule.java
│   └── graph/                       # Graph-based execution
│       ├── ProcessGraph.java
│       └── ProcessGraphBuilder.java
│
├── controllerdevice/                # Controllers
│   ├── ControllerDevice.java
│   └── PIDController.java
│
├── measurementdevice/               # Measurements
│   ├── MeasurementDevice.java
│   ├── TemperatureMeasurement.java
│   ├── PressureMeasurement.java
│   └── FlowMeasurement.java
│
├── logic/                           # Process logic
│   ├── ProcessLogicController.java
│   └── ConditionalLogic.java
│
├── alarm/                           # Alarm system
│   └── ProcessAlarmManager.java
│
├── safety/                          # Safety systems
│   ├── PSV/
│   ├── ESD/
│   └── Blowdown/
│
├── calibration/                     # Equipment calibration
├── conditionmonitor/                # Condition monitoring
├── costestimation/                  # Cost estimation
├── mechanicaldesign/                # Mechanical design calculations
│   ├── separator/                   # Separator vessel design
│   ├── compressor/                  # Compressor design (API 617)
│   ├── valve/                       # Valve body, sizing, actuator
│   └── designstandards/             # ASME, API, IEC standards
├── mpc/                             # Model predictive control
├── ml/                              # Machine learning
└── streaming/                       # Data streaming
```

### Mechanical Design Documentation

| Equipment | Documentation | Standards |
|-----------|--------------|-----------|
| Separators | See SeparatorMechanicalDesign | ASME, BS 5500 |
| Compressors | [CompressorMechanicalDesign.md](CompressorMechanicalDesign.md) | API 617, API 672 |
| Valves | [ValveMechanicalDesign.md](ValveMechanicalDesign.md) | IEC 60534, ANSI/ISA-75, ASME B16.34 |

---

## ProcessSystem

The `ProcessSystem` class is the container for building and running process flowsheets.

### Basic Usage

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;

// Create process system
ProcessSystem process = new ProcessSystem("Gas Processing Plant");

// Create feed stream
SystemInterface feed = new SystemSrkEos(300.0, 80.0);
feed.addComponent("methane", 0.85);
feed.addComponent("ethane", 0.08);
feed.addComponent("propane", 0.05);
feed.addComponent("n-butane", 0.02);
feed.setMixingRule("classic");

Stream feedStream = new Stream("Feed", feed);
feedStream.setFlowRate(1000.0, "kg/hr");

// Add equipment to process
process.add(feedStream);

// Letdown valve
ThrottlingValve valve = new ThrottlingValve("Inlet Valve", feedStream);
valve.setOutletPressure(40.0, "bara");
process.add(valve);

// Separator
Separator separator = new Separator("HP Separator", valve.getOutletStream());
process.add(separator);

// Run process (recommended - auto-optimized)
process.runOptimized();

// Get results
System.out.println("Separator gas rate: " + 
    separator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Separator liquid rate: " + 
    separator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
```

### Execution Strategies

NeqSim provides multiple execution strategies for optimal performance:

| Method | Best For | Speedup |
|--------|----------|---------|
| `run()` | General use | baseline |
| `runOptimized()` | **Recommended** | 28-40% |
| `runParallel()` | Feed-forward (no recycles) | 40-57% |
| `runHybrid()` | Complex recycle processes | 38% |

```java
// Recommended - auto-selects best strategy
process.runOptimized();

// Or use specific strategies:
process.run();           // Sequential (default)
process.runParallel();   // Parallel (feed-forward only)
process.runHybrid();     // Hybrid (parallel + iterative)
```

### Analyze Process Topology

```java
// Check for recycles
boolean hasRecycles = process.hasRecycleLoops();

// Get detailed execution analysis
System.out.println(process.getExecutionPartitionInfo());
```

### Key ProcessSystem Methods

| Method | Description |
|--------|-------------|
| `add(equipment)` | Add equipment to process |
| `run()` | Run sequential simulation |
| `runOptimized()` | Run with auto-optimized strategy |
| `runParallel()` | Run with parallel execution |
| `runHybrid()` | Run with hybrid execution |
| `runTransient(time, dt)` | Run transient simulation |
| `getUnit(name)` | Get equipment by name |
| `hasRecycleLoops()` | Check for recycle loops |
| `getExecutionPartitionInfo()` | Get execution analysis |
| `copy()` | Clone the process system |
| `getReport()` | Get process report |
| `display()` | Display process summary |

---

## Equipment Categories

### Streams

```java
// Material stream
Stream gas = new Stream("Natural Gas", fluid);
gas.setFlowRate(5000.0, "Sm3/hr");
gas.setTemperature(25.0, "C");
gas.setPressure(100.0, "bara");
gas.run();

// Energy stream
EnergyStream heat = new EnergyStream("Heating Duty");
heat.setEnergyFlow(1000.0, "kW");
```

### Separators

```java
// Two-phase separator
Separator sep2p = new Separator("V-100", inletStream);
sep2p.run();
Stream gas = sep2p.getGasOutStream();
Stream liquid = sep2p.getLiquidOutStream();

// Three-phase separator
ThreePhaseSeparator sep3p = new ThreePhaseSeparator("V-200", inletStream);
sep3p.run();
Stream gas = sep3p.getGasOutStream();
Stream oil = sep3p.getOilOutStream();
Stream water = sep3p.getWaterOutStream();
```

### Heat Exchangers

```java
// Heater (duty specified)
Heater heater = new Heater("E-100", inletStream);
heater.setOutTemperature(80.0, "C");
heater.run();
System.out.println("Duty: " + heater.getDuty() + " W");

// Cooler
Cooler cooler = new Cooler("E-200", inletStream);
cooler.setOutTemperature(30.0, "C");
cooler.run();

// Shell-tube heat exchanger
HeatExchanger hx = new HeatExchanger("E-300", hotStream, coldStream);
hx.setUAvalue(5000.0);  // W/K
hx.run();
```

### Compressors

```java
// Compressor with polytropic efficiency
Compressor comp = new Compressor("K-100", inletStream);
comp.setOutletPressure(80.0, "bara");
comp.setPolytropicEfficiency(0.75);
comp.setUsePolytropicCalc(true);
comp.run();

System.out.println("Power: " + comp.getPower("kW") + " kW");
System.out.println("Outlet T: " + comp.getOutletStream().getTemperature("C") + " °C");
```

### Valves

```java
// Throttling valve (Joule-Thomson)
ThrottlingValve valve = new ThrottlingValve("FV-100", inletStream);
valve.setOutletPressure(50.0, "bara");
valve.run();

// Valve with Cv
valve.setCv(100.0, "US");
valve.setPercentValveOpening(50.0);
```

### Distillation

```java
// Simple distillation column
DistillationColumn column = new DistillationColumn("T-100", 10, true, true);
column.addFeedStream(feedStream, 5);
column.setCondenserTemperature(40.0, "C");
column.setReboilerTemperature(120.0, "C");
column.run();

Stream overhead = column.getGasOutStream();
Stream bottoms = column.getLiquidOutStream();
```

---

## Controllers and Logic

### Adjusters

Adjust a parameter to meet a specification.

```java
// Adjust heater duty to achieve target temperature
Adjuster tempAdjuster = new Adjuster("TC-100");
tempAdjuster.setAdjustedVariable(heater, "duty");
tempAdjuster.setTargetVariable(heater.getOutletStream(), "temperature", 80.0, "C");
process.add(tempAdjuster);
```

### Recycles

Handle recycle loops in the process.

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(recycleStream);
recycle.setOutletStream(recycleInletStream);
recycle.setTolerance(1e-6);
process.add(recycle);
```

### Calculators

Perform custom calculations.

```java
Calculator calc = new Calculator("MW Calculator");
calc.addInputVariable(stream);
calc.setOutputVariable(heater, "duty");
calc.setExpression("molarMass * 1000");
process.add(calc);
```

---

## Safety Systems

### Pressure Safety Valves

```java
SafetyValve psv = new SafetyValve("PSV-100", vessel);
psv.setSetPressure(120.0, "bara");
psv.setBlowdownPressure(0.1);  // 10% blowdown
process.add(psv);
```

### Blowdown Systems

See [Safety Simulation Roadmap](../safety/SAFETY_SIMULATION_ROADMAP.md) for detailed safety system documentation.

---

## Dynamic Simulation

```java
// Run transient simulation
double simulationTime = 3600.0;  // 1 hour
double timeStep = 1.0;           // 1 second

process.setTimeStep(timeStep);
for (double t = 0; t < simulationTime; t += timeStep) {
    process.runTransient();
    
    // Log data
    System.out.println(t + ", " + 
        separator.getPressure() + ", " +
        separator.getGasOutStream().getFlowRate("kg/hr"));
}
```

---

## Process Reports

```java
// Get JSON report
String jsonReport = process.getReport_json();

// Get tabular report
String[][] table = process.getUnitOperationsAsTable();

// Display to console
process.display();
```

---

## Best Practices

1. **Use unique names** for all equipment
2. **Set flow rate and conditions** before running
3. **Add equipment in flow order** for clarity
4. **Use Recycle** for recycle loops
5. **Check mass balance** after simulation
6. **Clone streams** before branching to avoid shared state

---

## Future Infrastructure

NeqSim includes foundational infrastructure to support the future of process simulation:

| Capability | Documentation | Description |
|------------|---------------|-------------|
| **Lifecycle Management** | [lifecycle/](lifecycle/) | Model versioning, state export/import, lifecycle tracking |
| **Emissions Tracking** | [sustainability/](sustainability/) | CO2e accounting, regulatory reporting |
| **Advisory Systems** | [advisory/](advisory/) | Look-ahead predictions with uncertainty |
| **ML Integration** | [ml/](ml/) | Surrogate models, physics constraint validation |
| **Safety Scenarios** | [safety/scenario-generation.md](safety/scenario-generation.md) | Automatic failure scenario generation |
| **Batch Studies** | [optimization/batch-studies.md](optimization/batch-studies.md) | Parallel parameter studies |

See [Future Infrastructure Overview](future-infrastructure.md) for complete documentation.

---

## Related Documentation

- [Equipment Documentation](equipment/) - Detailed equipment guides
- [Process Logic Framework](../simulation/process_logic_framework.md) - Logic controllers
- [Safety Systems](../safety/SAFETY_SIMULATION_ROADMAP.md) - Safety simulation
- [Alarm System](../safety/alarm_system_guide.md) - Process alarms
- [Future Infrastructure](future-infrastructure.md) - Digital twin, AI integration, sustainability
