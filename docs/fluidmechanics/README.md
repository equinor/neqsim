# Fluid Mechanics Package

The `fluidmechanics` package provides models for pipeline flow, pressure drop calculations, and transient flow simulation.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Flow Systems](#flow-systems)
- [Flow Nodes](#flow-nodes)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.fluidmechanics`

**Purpose:**
- Single-phase and two-phase pipe flow modeling
- Pressure drop calculations
- Transient flow simulation
- Flow regime identification
- Heat transfer in pipelines

---

## Package Structure

```
fluidmechanics/
├── FluidMech.java                    # Package marker
│
├── flowsystem/                       # Flow system definitions
│   ├── FlowSystem.java               # Base flow system
│   ├── FlowSystemInterface.java      # Interface
│   │
│   ├── onephaseflowsystem/           # Single-phase systems
│   │   ├── OnePhaseFlowSystem.java
│   │   └── pipeflowsystem/
│   │       └── OnePhasePipeFlowSystem.java
│   │
│   └── twophaseflowsystem/           # Two-phase systems
│       ├── TwoPhaseFlowSystem.java
│       └── pipeflowsystem/
│           ├── TwoPhasePipeFlowSystem.java
│           └── stratifiedflowsystem/
│               └── StratifiedFlowSystem.java
│
├── flownode/                         # Flow nodes
│   ├── FlowNode.java                 # Base node
│   ├── FlowNodeInterface.java        # Interface
│   ├── FlowNodeSelector.java         # Node selection
│   │
│   ├── onephasenode/                 # Single-phase nodes
│   │   ├── OnePhaseFlowNode.java
│   │   └── onephasepipeflownode/
│   │       └── OnePhasePipeFlowNode.java
│   │
│   ├── twophasenode/                 # Two-phase nodes
│   │   ├── TwoPhaseFlowNode.java
│   │   └── twophasepipeflownode/
│   │       ├── TwoPhasePipeFlowNode.java
│   │       ├── AnnularFlow.java
│   │       ├── StratifiedFlow.java
│   │       └── DropletFlow.java
│   │
│   ├── multiphasenode/               # Multi-phase nodes
│   │   └── MultiPhaseFlowNode.java
│   │
│   └── fluidboundary/                # Boundary conditions
│       ├── FluidBoundary.java
│       └── InterphaseTransport.java
│
├── flowleg/                          # Pipe segments
│   ├── FlowLeg.java
│   └── FlowLegInterface.java
│
├── flowsolver/                       # Numerical solvers
│   ├── FlowSolver.java
│   ├── FlowSolverInterface.java
│   ├── OnePhaseFlowSolver.java
│   └── TwoPhaseFlowSolver.java
│
├── geometrydefinitions/              # Pipe geometry
│   ├── GeometryDefinition.java
│   ├── GeometryDefinitionInterface.java
│   ├── pipe/
│   │   └── PipeGeometry.java
│   └── internalgeometry/
│       └── InternalGeometry.java
│
└── util/                             # Utilities
    ├── timeseries/
    │   └── TimeSeries.java
    └── fluidmechanicsvisualization/
        └── flowsystemvisualization/
            └── FlowSystemVisualization.java
```

---

## Flow Systems

### Single-Phase Pipe Flow

```java
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.OnePhasePipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeGeometry;

// Create fluid
SystemInterface gas = new SystemSrkEos(300.0, 50.0);
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.05);
gas.setMixingRule("classic");

// Create pipe geometry
PipeGeometry pipe = new PipeGeometry("Pipeline");
pipe.setDiameter(0.5, "m");        // 0.5 m inner diameter
pipe.setLength(10000.0, "m");      // 10 km length
pipe.setRoughness(0.00005, "m");   // Pipe roughness

// Create flow system
OnePhasePipeFlowSystem flowSystem = new OnePhasePipeFlowSystem();
flowSystem.setInletFluid(gas);
flowSystem.setGeometry(pipe);
flowSystem.setInletPressure(50.0, "bara");
flowSystem.setOutletPressure(40.0, "bara");
flowSystem.setNumberOfNodes(100);

// Initialize and solve
flowSystem.init();
flowSystem.solveTransient(1);

// Get results
double pressureDrop = flowSystem.getPressureDrop();
double velocity = flowSystem.getFlowVelocity();
```

### Two-Phase Pipe Flow

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.pipeflowsystem.TwoPhasePipeFlowSystem;

// Create two-phase fluid
SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("n-pentane", 0.15);
fluid.addComponent("n-decane", 0.05);
fluid.setMixingRule("classic");

// Flash to get two phases
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Create two-phase flow system
TwoPhasePipeFlowSystem twoPhaseFlow = new TwoPhasePipeFlowSystem();
twoPhaseFlow.setInletFluid(fluid);
twoPhaseFlow.setGeometry(pipe);
twoPhaseFlow.setNumberOfNodes(100);

// Set inclination (positive = uphill)
twoPhaseFlow.setInclination(0.0);  // Horizontal

twoPhaseFlow.init();
twoPhaseFlow.solveTransient(1);

// Get flow regime
String flowRegime = twoPhaseFlow.getFlowRegime();
System.out.println("Flow regime: " + flowRegime);
```

---

## Flow Nodes

Flow nodes discretize the pipe and calculate local conditions.

### Node Properties

| Property | Description |
|----------|-------------|
| Pressure | Local pressure |
| Temperature | Local temperature |
| Velocity | Phase velocities |
| Holdup | Liquid holdup |
| Reynolds number | Flow regime indicator |
| Friction factor | Wall friction |

### Flow Regimes (Two-Phase)

| Regime | Class | Description |
|--------|-------|-------------|
| Stratified | `StratifiedFlow` | Separated gas-liquid layers |
| Annular | `AnnularFlow` | Liquid film on wall, gas core |
| Droplet/Mist | `DropletFlow` | Liquid droplets in gas |
| Slug | `SlugFlow` | Intermittent gas-liquid slugs |
| Bubble | `BubbleFlow` | Gas bubbles in liquid |

---

## Pressure Drop Correlations

### Single-Phase

```java
// Darcy-Weisbach equation
// ΔP = f * (L/D) * (ρ * v²/2)

// Friction factor correlations:
// - Moody (explicit)
// - Colebrook-White (implicit)
// - Chen (explicit approximation)
```

### Two-Phase

| Correlation | Application |
|-------------|-------------|
| Beggs-Brill | General two-phase |
| Lockhart-Martinelli | Separated flow |
| Duns-Ros | Vertical wells |
| Hagedorn-Brown | Vertical wells |
| Gray | Gas-condensate wells |

---

## Transient Flow Simulation

```java
// Set up transient simulation
flowSystem.init();

double simulationTime = 3600.0;  // 1 hour
double timeStep = 1.0;           // 1 second

for (double t = 0; t < simulationTime; t += timeStep) {
    flowSystem.solveTransient(1);
    
    // Get time series data
    TimeSeries data = flowSystem.getTimeSeries();
    
    // Log results
    for (int i = 0; i < flowSystem.getNumberOfNodes(); i++) {
        double x = flowSystem.getNode(i).getPosition();
        double P = flowSystem.getNode(i).getPressure();
        double T = flowSystem.getNode(i).getTemperature();
    }
}
```

---

## Heat Transfer

```java
// Set ambient conditions
flowSystem.setSurroundingTemperature(288.15);  // K

// Set overall heat transfer coefficient
pipe.setOverallHeatTransferCoefficient(10.0);  // W/(m²·K)

// Or specify insulation
pipe.setInsulationThickness(0.05, "m");
pipe.setInsulationConductivity(0.04);  // W/(m·K)

// Solve with heat transfer
flowSystem.setCalculateHeatTransfer(true);
flowSystem.solveTransient(1);

// Get temperature profile
for (int i = 0; i < flowSystem.getNumberOfNodes(); i++) {
    double T = flowSystem.getNode(i).getTemperature();
}
```

---

## Geometry Definitions

### Pipe Geometry

```java
PipeGeometry pipe = new PipeGeometry("Export Pipeline");

// Dimensions
pipe.setDiameter(0.4, "m");
pipe.setLength(50000.0, "m");  // 50 km
pipe.setRoughness(0.000045, "m");

// Inclination profile (optional)
double[] distances = {0, 10000, 20000, 30000, 40000, 50000};
double[] elevations = {0, 50, 100, 80, 120, 150};
pipe.setElevationProfile(distances, elevations);
```

### Internal Geometry

For complex internal structures (coatings, deposits).

```java
InternalGeometry internal = new InternalGeometry();
internal.setCoatingThickness(0.002, "m");
internal.setWaxThickness(0.001, "m");
pipe.setInternalGeometry(internal);
```

---

## Flow Solver Options

```java
FlowSolverInterface solver = flowSystem.getSolver();

// Solver settings
solver.setMaxIterations(100);
solver.setConvergenceCriteria(1e-6);
solver.setRelaxationFactor(0.8);
```

---

## Integration with Process Equipment

```java
import neqsim.process.equipment.pipeline.Pipeline;

// Use Pipeline equipment in ProcessSystem
Pipeline pipeline = new Pipeline("Export Line", inletStream);
pipeline.setLength(50.0, "km");
pipeline.setDiameter(0.5, "m");
pipeline.setOutletPressure(30.0, "bara");
pipeline.run();

Stream outlet = pipeline.getOutletStream();
double Tout = outlet.getTemperature("C");
```

---

## Visualization

```java
// Get display interface
FlowSystemVisualizationInterface display = flowSystem.getDisplay();

// Plot pressure profile
display.plotPressureProfile();

// Plot temperature profile
display.plotTemperatureProfile();

// Plot holdup (two-phase)
display.plotHoldupProfile();
```

---

## Example: Gas Pipeline

```java
// Natural gas pipeline simulation
SystemInterface gas = new SystemSrkEos(288.15, 80.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.setMixingRule("classic");

// Set flow rate
gas.setTotalFlowRate(50.0, "MSm3/day");

// Pipeline geometry
PipeGeometry pipe = new PipeGeometry("Gas Export");
pipe.setDiameter(0.9, "m");
pipe.setLength(200000.0, "m");  // 200 km
pipe.setRoughness(0.00004, "m");

// Flow system
OnePhasePipeFlowSystem gasFlow = new OnePhasePipeFlowSystem();
gasFlow.setInletFluid(gas);
gasFlow.setGeometry(pipe);
gasFlow.setInletPressure(80.0, "bara");
gasFlow.setNumberOfNodes(200);

gasFlow.init();
gasFlow.solveTransient(1);

// Results
System.out.println("Inlet pressure: " + gasFlow.getInletPressure() + " bar");
System.out.println("Outlet pressure: " + gasFlow.getOutletPressure() + " bar");
System.out.println("Pressure drop: " + gasFlow.getPressureDrop() + " bar");
System.out.println("Flow velocity: " + gasFlow.getFlowVelocity() + " m/s");
```

---

## Best Practices

1. **Use appropriate number of nodes** - more nodes for accuracy, fewer for speed
2. **Check flow regime** in two-phase calculations
3. **Validate against correlations** for your specific application
4. **Consider elevation profile** for long pipelines
5. **Include heat transfer** for hot fluids or cold environments

---

## Related Documentation

- [Process Pipeline Equipment](../process/README.md) - Pipeline in process simulation
- [Physical Properties](../physical_properties/README.md) - Viscosity and density models
