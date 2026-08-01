---
title: Single-Phase Gas Pipe Flow Simulation
description: NeqSim provides single-phase gas pipeline simulation capabilities through the `PipeFlowSystem` class, implementing a staggered grid finite volume method with TDMA (Tri-Diagonal Matrix Algorithm) solve...
---

# Single-Phase Gas Pipe Flow Simulation

## Overview

NeqSim provides single-phase gas pipeline simulation capabilities through the `PipeFlowSystem` class, implementing a staggered grid finite volume method with TDMA (Tri-Diagonal Matrix Algorithm) solver.

## Architecture

### Class Hierarchy

```
FlowSystem (abstract)
└── OnePhaseFlowSystem (abstract)
    └── PipeFlowSystem (concrete)
```

### Key Components

| Component | Description |
|-----------|-------------|
| `PipeFlowSystem` | Main flow system for single-phase pipe flow |
| `OnePhaseFixedStaggeredGrid` | Staggered grid solver with TDMA |
| `onePhasePipeFlowNode` | Flow node for single-phase pipe segments |
| `TimeSeries` | Time-varying inlet conditions for transient simulation |

## Governing Equations

The solver implements the following conservation equations:

### Mass Conservation

$$\frac{\partial \rho}{\partial t} + \frac{\partial (\rho v)}{\partial x} = 0$$

### Momentum Conservation

$$\frac{\partial (\rho v)}{\partial t} + \frac{\partial (\rho v^2)}{\partial x} = -\frac{\partial P}{\partial x} - \rho g \sin(\theta) - \frac{f \rho v |v|}{2D}$$

where:
- $\rho$ = density
- $v$ = velocity  
- $P$ = pressure
- $g$ = gravitational acceleration
- $\theta$ = pipe inclination angle
- $f$ = Darcy friction factor
- $D$ = pipe diameter

### Energy Conservation

$$\frac{\partial (\rho h)}{\partial t} + \frac{\partial (\rho v h)}{\partial x} = Q_{wall} + \rho v g \sin(\theta)$$

where:
- $h$ = specific enthalpy
- $Q_{wall}$ = wall heat transfer rate

### Component Conservation

For each component $i$:

$$\frac{\partial (\rho \omega_i)}{\partial t} + \frac{\partial (\rho v \omega_i)}{\partial x} = 0$$

where $\omega_i$ is the mass fraction of component $i$.

## Numerical Method

### Staggered Grid Discretization

The solver uses a staggered grid approach:
- Pressure and temperature are stored at cell centers
- Velocities are stored at cell faces

### TDMA Solver

The Tri-Diagonal Matrix Algorithm efficiently solves the linearized system:

```
a[i] * φ[i-1] + b[i] * φ[i] + c[i] * φ[i+1] = r[i]
```

### Upwind Scheme

Convective terms use upwind differencing for stability:

```java
a[i] = Math.max(Fw, 0);  // West face flux
c[i] = Math.max(-Fe, 0); // East face flux
```

## Solver Types

The solver supports different levels of physics:

| Type | Description |
|------|-------------|
| 0 | Momentum only (isothermal, incompressible) |
| 1 | Momentum + mass (compressible) |
| 10 | Momentum + mass + energy |
| 20 | Momentum + mass + energy + composition |

## Usage Example

### Steady-State Simulation

```java
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemSrkEos;

// Create gas system
SystemInterface gas = new SystemSrkEos(288.15, 100.0); // 15°C, 100 bar
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.10);
gas.createDatabase(true);
gas.init(0);
gas.init(3);
gas.initPhysicalProperties();
gas.setTotalFlowRate(10.0, "MSm3/day");

// Configure pipeline
PipeFlowSystem pipe = new PipeFlowSystem();
pipe.setInletThermoSystem(gas);
pipe.setNumberOfLegs(10);
pipe.setNumberOfNodesInLeg(20);

// Set geometry (10 segments)
double[] heights = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
double[] positions = {0, 10000, 20000, 30000, 40000, 50000, 
                      60000, 70000, 80000, 90000, 100000}; // meters

GeometryDefinitionInterface[] geometry = new PipeData[11];
for (int i = 0; i <= 10; i++) {
    geometry[i] = new PipeData();
    geometry[i].setDiameter(1.0);  // 1 meter diameter
    geometry[i].setInnerSurfaceRoughness(1e-5);
}

pipe.setEquipmentGeometry(geometry);
pipe.setLegHeights(heights);
pipe.setLegPositions(positions);
pipe.setLegOuterTemperatures(new double[]{278, 278, 278, 278, 278, 278, 
                                           278, 278, 278, 278, 278});
pipe.setLegWallHeatTransferCoefficients(new double[]{15, 15, 15, 15, 15, 15,
                                                      15, 15, 15, 15, 15});
pipe.setLegOuterHeatTransferCoefficients(new double[]{5, 5, 5, 5, 5, 5,
                                                       5, 5, 5, 5, 5});

// Solve
pipe.createSystem();
pipe.init();
pipe.solveSteadyState(10);  // Type 10: with energy equation

// Get results
double pressureDrop = pipe.getTotalPressureDrop();
double outletTemp = pipe.getNode(pipe.getTotalNumberOfNodes() - 1)
    .getBulkSystem().getTemperature();
```

## Dynamic/Transient Simulation

The transient solver supports time-varying inlet conditions including changes in:
- Temperature
- Pressure
- Flow rate
- Composition

### Transient Simulation Example

```java
// First solve steady state to initialize
pipe.createSystem();
pipe.init();
pipe.solveSteadyState(10);

// Setup time series with varying inlet conditions
// Note: times array has N points, systems array has N-1 entries (one per interval)
double[] times = {0, 3000, 6000};  // 3 time points = 2 intervals
pipe.getTimeSeries().setTimes(times);

// Initial cold gas
SystemInterface coldGas = new SystemSrkEos(280.0, 100.0);
coldGas.addComponent("methane", 0.90);
coldGas.addComponent("ethane", 0.10);
coldGas.createDatabase(true);
coldGas.init(0);
coldGas.init(3);
coldGas.initPhysicalProperties();
coldGas.setTotalFlowRate(10.0, "MSm3/day");

// Hot gas with different composition
SystemInterface hotGas = new SystemSrkEos(320.0, 100.0);
hotGas.addComponent("methane", 0.80);
hotGas.addComponent("ethane", 0.20);
hotGas.createDatabase(true);
hotGas.init(0);
hotGas.init(3);
hotGas.initPhysicalProperties();
hotGas.setTotalFlowRate(10.0, "MSm3/day");

// 2 intervals: [0-3000] cold, [3000-6000] hot
SystemInterface[] systems = {coldGas, hotGas};
pipe.getTimeSeries().setInletThermoSystems(systems);
pipe.getTimeSeries().setNumberOfTimeStepsInInterval(5);

// Run transient simulation with full physics (type 20 = momentum + mass + energy + composition).
// Strict mode throws when the finite-volume, EOS-density, and total-mass criteria fail.
pipe.setFailOnNonConvergence(true);
try {
    pipe.solveTransient(20);
} catch (IllegalStateException nonConvergence) {
    neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseFlowConvergenceReport
        report = pipe.getConvergenceReport();
    throw new IllegalStateException(report.toJson(), nonConvergence);
}
```

### Convergence and Total-Mass Diagnostics

`PipeFlowSystem.getConvergenceReport()` returns an immutable report for the latest solve. For
positive transient solver types that include total mass (including `1`, `10`, and `20`), a
completed solve requires all of the following:

- the nonlinear update residual is at most `1e-10`;
- the maximum relative difference between the finite-volume and EOS density is at most `1e-8`;
- finite-volume and EOS inventory changes each agree with integrated inlet-minus-outlet mass to
  a relative tolerance of `1e-8`.

The report contains both residual histories, iteration count, initial/final finite-volume and EOS
inventories in kg, integrated inlet and outlet masses in kg, and absolute/relative closure errors.
For backward-compatible control flow, the default logs a warning and returns the failed report.
Call `pipe.setFailOnNonConvergence(true)` to make `solveTransient(...)` throw
`IllegalStateException`; the report is recorded before either behavior and distinguishes
algebraic, density-consistency, and mass-balance failures. Node zero is a prescribed upstream
boundary. The first accumulating control volume is node one, so the inlet density is imposed at
row zero and only physical control volumes contribute to linepack.

## Compositional Tracking

### Steady-State Composition

In steady-state single-phase flow, composition is uniform throughout the pipeline:

- Solver type 20 includes component conservation equations
- Mole fractions are solved using the same TDMA scheme
- Normalization ensures mole fractions sum to unity

### Dynamic Composition Tracking

The component equations provide an experimental basis for dynamic compositional transitions:

1. `oldComposition[component][node]` stores previous time step values
2. `setComponentConservationMatrix()` builds the discretized equations
3. `initComposition()` updates node compositions after each time step

**Example - Compositional Change at Inlet**:

```java
// Initial gas with ethane
SystemInterface initialGas = new SystemSrkEos(298.0, 30.0);
initialGas.addComponent("methane", 0.9);
initialGas.addComponent("ethane", 0.1);
initialGas.initPhysicalProperties();
initialGas.setTotalFlowRate(10.0, "MSm3/day");

// New gas (pure methane)
SystemInterface newGas = initialGas.clone();
newGas.addComponent("methane", 0.1);  // Shift to 100% methane
newGas.initPhysicalProperties();

// TimeSeries with 2 intervals
SystemInterface[] systems = {initialGas, newGas};
pipe.getTimeSeries().setInletThermoSystems(systems);
pipe.getTimeSeries().setNumberOfTimeStepsInInterval(10);

// Run with compositional tracking (type 20)
pipe.solveTransient(20);
```

## Physical Effects Captured

### Pressure Drop

- Friction losses (Darcy-Weisbach)
- Gravitational head (for inclined pipes)
- Acceleration losses (compressible flow)

### Temperature Effects

- Wall heat transfer to surroundings
- Joule-Thomson cooling on expansion
- Gravitational work term

### Compressibility

- Real gas equation of state (SRK-EOS or other)
- Density variation with pressure and temperature
- Velocity increase as gas expands

## Validation Results

The steady-state solver has been validated for:

| Test | Result |
|------|--------|
| Pressure monotonically decreases | ✓ Pass |
| Temperature approaches surroundings | ✓ Pass |
| Mass conservation (inlet ≈ outlet) | ✓ Pass (within 15%) |
| Reynolds number physically correct | ✓ Pass |
| Friction factor in reasonable range | ✓ Pass |
| Composition preserved | ✓ Pass |
| Numerical stability (high flow) | ✓ Pass |
| Inclined pipeline handling | ✓ Pass |

## Known Limitations

1. **Single-phase only**: No phase transition handling
2. **Component-transport validation is incomplete**: Conservative per-component inventories,
   boundedness without normalization, analytical front speed, pulse breakthrough, and grid/time
   convergence are not yet established. Do not use the current component result as a validated
   compositional-tracking prediction.
3. **No physical axial dispersion model**: Existing advection-scheme spreading is numerical and
   must not be interpreted as molecular or turbulent dispersion.
4. **Positive-flow diagnostic boundary**: Current transient mass diagnostics reject reverse
   boundary flow because an external upwind thermodynamic state is not yet defined.
5. **TimeSeries API**: Inlet systems array must have N-1 elements for N time points (one system per interval)

## Recommendations

### For Improved Mass Conservation

Planned dependency order is:
- Pressure-velocity coupling (SIMPLE algorithm)
- Conservative per-component transport after total-mass/EOS convergence is demonstrated
- Dimensionally valid higher-order convection only after first-order analytical validation
- Explicit physical dispersion as a separate, documented model

### TimeSeries Best Practices

When setting up transient simulations:
```java
// CORRECT: 3 time points → 2 systems (one per interval)
double[] times = {0, 3000, 6000};
pipe.getTimeSeries().setOutletMolarFlowRates(times, "kg/sec");

SystemInterface[] systems = {gasForInterval1, gasForInterval2};
pipe.getTimeSeries().setInletThermoSystems(systems);
```

## References

1. Patankar, S.V. (1980). *Numerical Heat Transfer and Fluid Flow*. Hemisphere Publishing.
2. Solbraa, E. (2002). *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing*. PhD Thesis, NTNU.
