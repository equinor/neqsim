---
title: Single-Phase Gas Pipe Flow Simulation
description: "Architecture and usage of PipeFlowSystem for single-phase gas pipelines using a staggered-grid finite-volume method and TDMA solver."
---

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
face velocities. When conservative species transport is enabled, the steady solve enters the
coupled path directly; it does not first accept segregated pressure/velocity iterates that can
reverse an otherwise supported low positive flow. The outlet pressure is prescribed; neither
boundary node is counted as an accumulating control volume. This coupled path currently supports
positive flow only and fails with an explicit message for reversed flow. A completed transient
solve requires all of the following:

- the maximum scaled continuity/momentum equation residual is at most `1e-10`; each row uses a
  fixed dimensional scale formed from the absolute equation terms at the initial iterate;
- the maximum relative difference between the finite-volume and EOS density is at most `1e-8`;
- finite-volume and EOS inventory changes each agree with integrated inlet-minus-outlet mass to
  a relative tolerance of `1e-8`.

Use `solveSteadyState(1)` before `solveTransient(1)` when selecting this validated hydraulic/EOS
path. The direct coupled steady path is selected by `setConservativeSpeciesTransport(true)` and is
intentionally limited to type `1`; it does not overwrite the temperature or composition results
produced by staged solver types `10` and `20`. There is no universal minimum-flow cutoff: supported
low positive flow is determined by convergence of the scaled continuity and momentum equations and
a finite, positive hydraulic/EOS state. Zero or reversed flow remains outside this coupled path's
documented validity range and fails loudly. A non-converged direct conservative steady solve also
throws with its residual and iteration diagnostics before that state can initialize a transient;
the compatibility setting that permits a failed report to return applies only to legacy,
non-conservative operation.

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
weaken the frozen acceptance criteria. One-phase node initialization rescales every existing phase
component from one fixed pre-update total, so repeated trial-state evaluations do not accumulate
loop-order-dependent mole changes. A zero-velocity node retains its last finite positive
thermodynamic reference amount while its hydraulic flow variables remain zero; an EOS phase cannot
be made empty merely to represent zero flow. The conservative finite-volume cell inventories
remain authoritative, and every synchronization reinitializes the thermodynamic EOS state.
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

Transient solver type `1` has an opt-in finite-volume species path. Its compatibility default is
fully implicit first-order upwind. For positive face mass flow, each independent component mass
fraction is solved from

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
pipe.setSpeciesAdvectionScheme(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2);
pipe.setAxialDispersionModel(new ConstantAxialDispersion(1.0)); // physical D_ax [m2/s]
pipe.setConservativeSpeciesTransport(true);
pipe.setStoreSpeciesConservationHistory(true);
pipe.setFailOnNonConvergence(true);
pipe.solveTransient(1);

OnePhaseSpeciesConservationReport species = pipe.getSpeciesConservationReport();
double[] componentResidualKg = species.getInventoryResidualKg();
double[][] componentMassFractionByCell = species.getMassFractionProfile();
SpeciesTransportDiagnostics resolution = species.getTransportDiagnostics();
double maximumFullStepCfl = resolution.getMaximumCellCourantNumber();
double firstOrderReferenceM2PerSecond =
    resolution.getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond();
double physicalDispersionM2PerSecond =
    resolution.getMaximumPhysicalAxialDispersionM2PerSecond();
double[] physicalCellPeclet = resolution.getCellPecletNumbers();

OnePhaseSpeciesConservationHistory history = pipe.getSpeciesConservationHistory();
double[] acceptedStepTimesSeconds = history.getElapsedTimeSeconds();
String pythonReadyHistoryJson = history.toJson();
```

`SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT` remains the default. The opt-in
`TVD_VAN_LEER_SSP_RK2` method uses MUSCL face reconstruction with a Van Leer limiter and a
two-stage strong-stability-preserving Runge-Kutta update. It automatically divides an accepted
hydraulic step into conservative transport substeps until every local mass Courant number is at
most 0.45. When explicit physical dispersion is enabled, the substep criterion includes both
advective CFL and the physical face-conductance dispersion number. The limited positive-flow
update is bounded without clipping and remains second order in smooth constant-coefficient
regions. `SpeciesAdvectionScheme` is separate from the legacy
`AdvectionScheme`; setting the latter does not silently change the validated component-inventory
path.

The process-equipment wrapper exposes the same validated path without requiring callers to reach
into `PipeFlowSystem`:

```java
OnePhasePipeLine pipeline = new OnePhasePipeLine("export gas", inletStream);
pipeline.setConservativeCompositionalTracking(true);
pipeline.setSpeciesAdvectionScheme(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2);
pipeline.setStoreSpeciesConservationHistory(true);
pipeline.setFailOnNonConvergence(true);
pipeline.run();
pipeline.runConservativeTransient(new double[] {0.0, 1800.0, 5400.0},
    new SystemInterface[] {pulseGas, baselineGas}, 60, UUID.randomUUID());

double[] nitrogenMassFractionProfile = pipeline.getConservativeMassFractionProfile("nitrogen");
double[] totalLinepackByCellKg = pipeline.getConservativeCellInventoryKg();
double[] nitrogenLinepackByCellKg =
    pipeline.getConservativeComponentInventoryProfileKg("nitrogen");
String pythonReadyHistoryJson = pipeline.getSpeciesConservationHistory().toJson();
```

`runConservativeTransient(...)` takes elapsed interval boundaries in seconds, one initialized
single-gas-phase inlet system per interval, and a positive number of equal solver steps per
interval. Each interval system supplies both its composition and a strictly positive mass flow.
For the conservative path, that scheduled mass rate is imposed at the authoritative finite-volume
inlet face as $v_{in}=\dot m/(A\rho_{EOS})$, using the same inlet EOS density as the continuity
matrix. A simultaneous composition/rate event therefore preserves the requested integrated inlet
mass in each accepted-step report. Solver type `1` preserves the initialized temperature profile;
this path does not yet solve a dynamic energy equation. The authoritative component profiles and outlet accessor are mass fractions; the existing
`getOutletMoleFraction(...)` remains explicitly molar. The local linepack accessors return kg for
physical finite-volume cells in inlet-to-outlet order and exclude boundary nodes. Total cell
inventory is retained directly from the accepted hydraulic finite-volume state; component-cell
inventory is that total multiplied by the conservative component mass fraction. Both arrays are
also serialized as `finalCellInventoryKg` and `finalComponentCellInventoryKg` in stable report
JSON. Python/JPype callers can pass `JArray(JDouble)` and `JArray(SystemInterface)` and read the
same report/history objects directly. Full accepted-step history remains opt-in because retaining
a cell-by-component matrix for every step increases memory in proportion to components, cells,
and accepted steps.
`runTransient(dt, id)` also selects type `1` when conservative mode is enabled. When
`setStoreSpeciesConservationHistory(true)` is enabled, it retains every internal accepted step from
that call. Legacy `setCompositionalTracking(true)` still selects type `20` for compatibility and
must not be interpreted as the validated conservative path.

### ProcessSystem event integration

The conservative wrapper can be advanced by `ProcessSystem` so scheduled composition and mass-flow
events share the process clock and calculation identifier:

```java
ProcessSystem process = new ProcessSystem("export gas transient");
process.add(inletStream);
process.add(pipeline);

EventScheduler scheduler = new EventScheduler();
process.setEventScheduler(scheduler);
scheduler.scheduleEvent(60.0, "start pulse",
    () -> inletStream.setThermoSystem(pulseGas.clone()));
scheduler.scheduleEvent(120.0, "restore baseline",
    () -> inletStream.setThermoSystem(baselineGas.clone()));

process.run(UUID.randomUUID());
process.runTransient(60.0, UUID.randomUUID());
process.runTransient(60.0, UUID.randomUUID());
```

Before the first process-level transient step, `ProcessSystem` captures a deep snapshot for its
copy/reset lifecycle. NeqSim's built-in one-phase pipe flow nodes, geometry surroundings, wall
layers, interphase-transport strategies, and node selector are serializable so this snapshot
cannot be bypassed by calling the pipeline directly. Custom geometry or transport implementations
stored in the pipeline graph must also keep every non-transient field serializable; otherwise the
snapshot fails before advancing time and reports the offending class.

The regression uses SRK/classic methane/nitrogen gas in a 3 km, 0.5 m pipe with 12 nodes at
70 bara absolute and 288.15 K. A 60 kg/s pulse followed by the 50 kg/s baseline in two 60 s process
steps must contribute 3600 kg and 3000 kg at the authoritative finite-volume inlet boundary,
respectively. Each step must retain the existing component-inventory, EOS synchronization,
boundedness, process/pipe clock, event-count, and calculation-identifier gates. This establishes
process-level event propagation for positive-flow, one-phase, solver-type-1 operation; it does not
yet establish event replay after `ProcessSystem.reset()`, dynamic energy transport, phase
appearance, or zero/reversed-flow support.

`OnePhaseSpeciesConservationReport` exposes component names, physical-cell mass-fraction profiles,
final total and component inventories by physical cell, initial/final global component inventories,
integrated inlet/outlet component masses, absolute and
relative inventory residuals, boundedness and sum-to-one diagnostics, thermodynamic
synchronization error, hydraulic/species residual histories, and a per-step
`SpeciesTransportDiagnostics` object. The diagnostic records the selected method, full-step local
and effective CFL values, the number of bounded substeps, and the first-order implicit numerical-
dispersion reference. It separately records the selected physical model, physical coefficient
profile/range, cell Peclet numbers, full-step physical dispersion numbers, and boundary
conditions. Its array getters return defensive copies and `toJson()` is suitable for
Python-side result capture.

For constant velocity and cell length, modified-equation analysis of the compatibility method
gives

$$
D_{num,implicit}=\frac{1}{2}u\,\Delta x\,(1+CFL).
$$

The diagnostic reports this quantity in m²/s when cell lengths are available, including when the
TVD method is selected so the avoided first-order spreading remains visible. A flux limiter does
not have one constant equivalent diffusion coefficient, so the value is explicitly a first-order
reference rather than a TVD calibration.

Physical axial dispersion is a separate opt-in flux model. `NoAxialDispersion` is the default and
reproduces pure-advection behavior exactly. `ConstantAxialDispersion(D_ax)` supplies a finite
non-negative physical coefficient in m²/s for analytical tests, calibration, and uncertainty
studies. For every internal face the solver constructs one conservative conductance by adding the
two adjacent half-cell resistances from cell line density, length, and coefficient. This remains
consistent on a nonuniform grid. The same component flux leaves the west cell and enters the east
cell. The independent $n-1$ component fluxes are advanced and the final component flux is their
negative sum, preserving sum-to-one without clipping or normalization.

The physical boundary conditions are explicit:

- `DIRICHLET_INLET`: the inlet thermodynamic-system mass fraction is the external composition for
  both advection and physical dispersion;
- `ZERO_GRADIENT_OUTLET`: physical dispersive flux is zero, while advective component mass leaves
  with the outlet composition.

First-order advection and physical dispersion use one coupled implicit tridiagonal solve. The TVD
path applies the physical face flux in both SSP-RK2 stages and automatically substeps the combined
explicit operator. Diagnostics report

$$
Pe_P=\frac{u_P\,\Delta x_P}{D_{ax,P}}
$$

only when $D_{ax,P}>0$. A constant coefficient is a user hypothesis, not a turbulent-pipe
correlation. Molecular diffusion, Taylor/shear dispersion, network mixing, and grid-dependent
numerical spreading must be evaluated separately. Calibrate $D_{ax}$ only after a grid/time study
with the physical model held fixed; never tune it to cancel numerical diffusion.

Published validation should follow the operational comparison in Chaczykowski et al. (2018):
align measured inlet composition, flow/pressure history, geometry, and outlet gas-chromatograph
timestamps; report transport-time error separately from profile-shape error; and repeat the study
under grid/time refinement with one fixed physical coefficient. If the Norwegian and Polish
machine-readable operational series cannot be redistributed, use permissioned data in a private
validation job and publish only aggregate error metrics. The repository's Gaussian-variance and
finite-pulse tests remain the non-proprietary analytical regression and must not be presented as
operational calibration.

After `setStoreSpeciesConservationHistory(true)`,
`PipeFlowSystem.getSpeciesConservationHistory()` retains the immutable report from every accepted
step in the latest multi-step transient solve, aligned with elapsed step-end times in seconds.
Storage is off by default so long simulations do not retain every component-by-cell profile. The
history is reset when a new transient solve starts and contains only successfully accepted steps;
if a later step fails loudly, earlier accepted diagnostics remain available. Its defensive array
getters and `toJson()` expose distance profiles, outlet breakthrough, component linepack, boundary
integrals, and cumulative pulse-balance inputs without requiring Python to call one step at a time.
Accepted reports accumulate in amortized linear time and are copied into one immutable snapshot at
the end of a successful solve. An explicit getter after a failed solve creates the same immutable
snapshot from the steps accepted before failure.
The history records existing solver evidence and does not change transport equations, tolerances,
or finite-volume state ownership.

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

The validated stand-alone, constant-mass transport kernel matches analytical repeated-step
profiles at two timesteps, recovers the inventory-over-flow residence time, conserves a synthetic
1800 s pulse over six residence times, and reduces pulse error when its grid and timestep are
jointly refined from 12 nodes/60 s to 24 nodes/30 s. Separately, the end-to-end SRK/classic
baseline regression repeats the same 1800 s event through a 3000 m isothermal pipe, requires
bit-identical outlet histories, final profiles, and component inventories, verifies breakthrough
and recovery, and telescopes every immutable step report into a cumulative nitrogen balance.

The active coupled refinement regression advances the same physical pulse and recovery at
6 nodes/120 s, 12 nodes/60 s, and 24 nodes/30 s. At common 120 s sample times, it requires the
mean absolute outlet-composition difference between the two finer solutions to be smaller than
the difference between the two coarser solutions while every resolution retains the same EOS,
boundedness, and conservation gates. Exact-head Java 21 CI executes all three resolutions and
the independent 30-minute pulse repeatability test together. The comparison is a Cauchy-
convergence check and does not define an exact analytical solution for the coupled compressible
case.

Zero or reversed face flow still fails explicitly because an external upwind composition is not
yet defined. `OnePhasePipeLine` also performs an independent multiphase TP flash on cloned schedule
inputs and rejects actual phase appearance before advancing the finite-volume state. Once enabled,
every failed hydraulic/species criterion throws so that a failed conservative state cannot advance
to another timestep. Full hydraulic/EOS grid-and-timestep convergence, thermal coupling,
phase-changing transport, and physical dispersion remain validation gates. The default first-order
spreading is numerical; selecting the TVD scheme reduces it but does not model molecular diffusion,
turbulent axial dispersion, fittings, or network mixing.

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
3. **Physical-dispersion scope is deliberately narrow**: `NoAxialDispersion` and a user-specified
   constant coefficient are available. No compressible turbulent-pipe correlation is asserted;
   project use requires tracer/gas-quality calibration and a documented validity range.
4. **Positive-flow diagnostic boundary**: Current transient mass diagnostics reject reverse
   boundary flow because an external upwind thermodynamic state is not yet defined.
5. **TimeSeries API**: Inlet systems array must have N-1 elements for N time points (one system per interval)

## Recommendations

### For Improved Mass Conservation

Planned dependency order is:
- Coupled hydraulic/EOS and total-mass convergence for solver type `1`
- Conservative repeated-step and pulse validation after the one-step component balance
- Dimensionally valid higher-order convection after the established first-order analytical gate
- Published and permissioned operational validation of constant physical dispersion, followed by
  review of any turbulent/shear correlation before it becomes a selectable model

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
6. Taylor, G. I. (1954). “The dispersion of matter in turbulent flow through a pipe.”
   *Proceedings of the Royal Society A*, 223, 446–468. <https://doi.org/10.1098/rspa.1954.0130>.
