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

`PipeFlowSystem.getConvergenceReport()` returns an immutable report for the latest solve. Solver
type `1` uses a safeguarded coupled Newton solve for the physical-cell pressures and staggered
face velocities. The outlet pressure is prescribed; neither boundary node is counted as an
accumulating control volume. This coupled path currently supports positive flow only and fails
with an explicit message for reversed flow. A completed transient solve requires all of the
following:

- the maximum scaled continuity/momentum equation residual is at most `1e-10`; each row uses a
  fixed dimensional scale formed from the absolute equation terms at the initial iterate;
- the maximum relative difference between the finite-volume and EOS density is at most `1e-8`;
- finite-volume and EOS inventory changes each agree with integrated inlet-minus-outlet mass to
  a relative tolerance of `1e-8`.

Use `solveSteadyState(1)` before `solveTransient(1)` when selecting this validated hydraulic/EOS
path. The coupled steady refinement is intentionally limited to type `1`; it does not overwrite
the temperature or composition results produced by staged solver types `10` and `20`.

The pressure and velocity unknowns are interleaved so each equation couples only to its two
nearest unknowns on either side. The Newton matrix is therefore stored and solved as a compact
pentadiagonal system rather than expanded to a dense matrix. Continuity pressure derivatives use
the EOS `dP/drho` response directly. The remaining local derivatives use centered finite
differences grouped into five non-overlapping graph colors, requiring ten residual evaluations
per Newton iteration independent of grid size. The compact solve uses linear storage for fixed
bandwidth and fails with a row diagnostic if it encounters an unusable pivot.

The report contains the nonlinear-metric and density-residual histories, iteration count,
initial/final finite-volume and EOS inventories in kg, integrated inlet and outlet masses in kg,
and absolute/relative closure errors. `isNonlinearMetricEquationResidual()` is true for the
coupled path; staged legacy solvers retain their relative iterate-change metric and return false.
For the coupled path, `getScaledMassEquationResidualHistory()` and
`getScaledMomentumEquationResidualHistory()` separate the maximum absolute scaled continuity and
momentum residuals. Their pointwise maximum reconstructs `getNonlinearUpdateHistory()` exactly,
making a stalled equation family explicit without changing the convergence criterion. The final
family values are also available through `getMaximumScaledMassEquationResidual()` and
`getMaximumScaledMomentumEquationResidual()`. Coupled histories contain the initial residual at
index zero followed by one entry for each completed Newton iteration; staged legacy histories
retain one entry per iteration and return empty equation-family histories. When coupled
backtracking cannot reduce the residual, the failure message also compares the banded Newton
Jacobian along the rejected Newton direction with independent central directional derivatives
at normalized perturbations of $10^{-5}$, $10^{-6}$, and $10^{-7}$. It reports separate
relative infinity-norm errors for continuity and momentum, including sensitivity to differencing
scale. A second failure-only check builds an uncolored dense finite-difference Jacobian, measures
repeated residual evaluation at identical state, compares the colored and dense entries inside the
declared band, and reports the largest dense derivative outside that band. It also reports the
largest per-node relative drift between the repeated evaluations for phase and component moles,
density, inlet and mean velocity, mass and volumetric flow, Reynolds number, and wall-friction
factor. These diagnostics are evaluated only after failure, restore the accepted state, and do not
weaken the frozen acceptance criteria.
For backward-compatible control flow, the default logs a warning and returns the failed report.
Call `pipe.setFailOnNonConvergence(true)` to make `solveTransient(...)` throw
`IllegalStateException`; the report is recorded before either behavior and distinguishes
algebraic, line-search, residual/Jacobian/linear-solve, density-consistency, and mass-balance
failures. Numerical failures restore the last accepted hydraulic/EOS state and include the
exception type and detail in the report. Node zero is a prescribed upstream boundary. The first
accumulating control volume is node one, so the inlet density is imposed at row zero and only
physical control volumes contribute to linepack.

Solver types `10` and `20` retain the staged energy/component algorithm. They expose the same
hydraulic report shape, but coupled hydraulic/EOS convergence under a changing composition is not
yet validated for those paths. Do not interpret type `20` as satisfying the conservative transport
guarantees described below.

### Opt-in Conservative Species Step

Transient solver type `1` has an opt-in first-order finite-volume species path. For positive face
mass flow, each independent component mass fraction is solved from

$$
(M_P^{n+1} + \Delta t\,\dot m_e)\,\omega_{i,P}^{n+1}
= M_P^n\,\omega_{i,P}^n + \Delta t\,\dot m_w\,\omega_{i,W}^{n+1}.
$$

Here, $M_P$ is the authoritative total mass in a physical control volume and $\dot m_w$ and
$\dot m_e$ are west/east face mass flows. The final component is set algebraically to one minus
the sum of the other mass fractions. The implementation does not clip fractions or renormalize
the result. A hydraulic/species fixed-point iteration then synchronizes the EOS composition and
requires both composition and EOS-density residuals to converge.

```java
pipe.setConservativeSpeciesTransport(true);
pipe.setFailOnNonConvergence(true);
pipe.solveTransient(1);

OnePhaseSpeciesConservationReport species = pipe.getSpeciesConservationReport();
double[] componentResidualKg = species.getInventoryResidualKg();
double[][] componentMassFractionByCell = species.getMassFractionProfile();
```

`OnePhaseSpeciesConservationReport` exposes component names, physical-cell mass-fraction profiles,
initial/final component inventories, integrated inlet/outlet component masses, absolute and
relative inventory residuals, boundedness and sum-to-one diagnostics, thermodynamic
synchronization error, and hydraulic/species residual histories. Its array getters return
defensive copies and `toJson()` is suitable for Python-side result capture.

This path has been regression-tested for a single coupled isothermal composition-change step and
an 1800 s methane/nitrogen pulse through the coupled hydraulic/EOS/species path at positive flow.
The isolated conservative kernel is also checked over repeated uniform-cell steps. For constant
cell mass $M$, face mass flow $\dot m$, and timestep $\Delta t$, define the cell
Courant number $\lambda=\dot m\Delta t/M$, $p=\lambda/(1+\lambda)$, and
$q=1/(1+\lambda)$. The closed-form response in cell $j$ after $n$ repeated steps from an initially
tracer-free pipe is

$$\omega_j^n=\sum_{k=0}^{n-1}\binom{k+j}{j}p^{j+1}q^k.$$

This negative-binomial response is used as an independent regression target. Its outlet impulse
first moment also recovers the inventory-over-flow residence time

$$\tau=\frac{\sum_P M_P}{\dot m}.$$

The validated first-order kernel matches analytical repeated-step profiles at two timesteps,
recovers the inventory-over-flow residence time, conserves a synthetic 1800 s pulse over six
residence times, and reduces pulse error when the grid and timestep are jointly refined from
12 cells/60 s to 24 cells/30 s. The end-to-end SRK/classic regression repeats the same 1800 s
event independently through a 3000 m isothermal pipe, requires bit-identical outlet histories,
final profiles, and component inventories, verifies breakthrough and recovery, and telescopes
every immutable step report into a cumulative nitrogen balance. A coupled refinement regression
then advances the same physical pulse and recovery at 6 nodes/120 s, 12 nodes/60 s, and
24 nodes/30 s. At common 120 s sample times, it requires the mean absolute outlet-composition
difference between the two finer solutions to be smaller than the difference between the two
coarser solutions while every resolution retains the same EOS, boundedness, and conservation
gates. This is a Cauchy-convergence check; it does not define an exact analytical solution for the
coupled compressible case.

Zero or reversed face flow still fails explicitly because an external upwind composition is not
yet defined. Once enabled, every failed hydraulic/species criterion throws so that a failed
conservative state cannot advance to another timestep. Full hydraulic/EOS grid-and-timestep
convergence, thermal coupling, phase appearance, higher-order convection, and physical dispersion
remain validation gates. The spreading of the present first-order upwind scheme is numerical;
there is no physical axial-dispersion model.

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
2. **Component-transport validation remains bounded**: Repeated-step analytical advection,
   inventory-over-flow residence time, an 1800 s pulse, cumulative component closure, and
   first-order kernel grid/time refinement are established for positive isothermal flow. Full
   hydraulic/EOS grid/time convergence, thermal coupling, phase appearance, and network junctions
   are not yet established.
3. **No physical axial dispersion model**: Existing advection-scheme spreading is numerical and
   must not be interpreted as molecular or turbulent dispersion.
4. **Positive-flow diagnostic boundary**: Current transient mass diagnostics reject reverse
   boundary flow because an external upwind thermodynamic state is not yet defined.
5. **TimeSeries API**: Inlet systems array must have N-1 elements for N time points (one system per interval)

## Recommendations

### For Improved Mass Conservation

Planned dependency order is:
- Coupled hydraulic/EOS and total-mass convergence for solver type `1`
- Conservative repeated-step and pulse validation after the one-step component balance
- Dimensionally valid higher-order convection after the established first-order analytical gate
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
3. Chaczykowski, M., Sund, F., Zarodkiewicz, P. and Hope, S.M. (2018). “Gas composition
   tracking in transient pipeline flow.” *Journal of Natural Gas Science and Engineering*, 55,
   321–330. <https://doi.org/10.1016/j.jngse.2018.03.014>.
4. Urh, B. et al. (2024). “Gas composition tracking feasibility using transient finite difference
   theta-scheme model for binary gas mixtures.” *International Journal of Hydrogen Energy*, 49,
   1319–1331. <https://doi.org/10.1016/j.ijhydene.2023.11.031>.
5. Chen, Q. et al. (2024). “A transient gas pipeline network simulation model for decoupling the
   hydraulic-thermal process and the component tracking process.” *Energy*, 301, 131613.
   <https://doi.org/10.1016/j.energy.2024.131613>.
