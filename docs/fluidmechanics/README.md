---
title: Fluid Mechanics Package
description: "Guide to NeqSim fluid-mechanics models for pipeline flow, pressure drop, transient simulation, and non-equilibrium heat and mass transfer."
---

The `fluidmechanics` package provides models for pipeline flow, pressure drop calculations, and transient flow simulation with rigorous non-equilibrium thermodynamic calculations for mass and heat transfer.

## Table of Contents
- [Overview](#overview)
- [Compatibility](#compatibility)
- [Theoretical Foundation](#theoretical-foundation)
- [Package Structure](#package-structure)
- [Flow Systems](#flow-systems)
- [Flow Nodes](#flow-nodes)
- [Non-Equilibrium Modeling](#non-equilibrium-modeling)
- [Mass Transfer Models](#mass-transfer-models)
- [Heat Transfer Models](#heat-transfer-models)
- [Two-Phase Mass Transfer](#two-phase-mass-transfer)
- [Two-Phase Heat Transfer](#two-phase-heat-transfer)
- [Reactive Mass Transfer](#reactive-mass-transfer)
- [Usage Examples](#single-phase-pipe-flow)

## Related Documentation

| Document | Description |
|----------|-------------|
| [MassTransferAPI.md](MassTransferAPI) | **Complete API documentation** for mass transfer with methods, parameters, and examples |
| [EvaporationDissolutionTutorial.md](EvaporationDissolutionTutorial) | **Practical tutorial** for liquid evaporation and gas dissolution with worked examples |
| [PipelineLiquidEvaporation.md](PipelineLiquidEvaporation) | Finite-rate droplet/film evaporation distance with Maxwell-Stefan mass transfer and coupled heat transfer |
| [MASS_TRANSFER_MODEL_IMPROVEMENTS.md](MASS_TRANSFER_MODEL_IMPROVEMENTS) | **Technical review** of mass transfer model with improvement recommendations |
| [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer) | Complete theory for interphase mass and heat transfer |
| [droplet_flow_correlations.md](droplet_flow_correlations) | Ranz-Marshall, Kronig-Brink, Abramzon-Sirignano for droplet/bubble flow |
| [mass_transfer.md](mass_transfer) | Diffusivity models, correlations, and reactive mass transfer |
| [heat_transfer.md](heat_transfer) | Heat transfer correlations and wall boundary conditions |
| [TwoPhasePipeFlowModel.md](TwoPhasePipeFlowModel) | Two-phase flow governing equations and numerical methods |
| [flow_pattern_detection.md](flow_pattern_detection) | Flow regime identification algorithms |

---

## Overview

**Location:** `neqsim.fluidmechanics`

**Purpose:**
- Single-phase and two-phase pipe flow modeling
- Pressure drop calculations
- Transient flow simulation
- Flow regime identification
- Non-equilibrium heat and mass transfer
- Multicomponent diffusion modeling
- Reactive absorption (e.g., CO₂ into amine solutions)

---

## Compatibility

- **Java Version:** Java 8 and above
- **Build System:** Maven
- All code is Java 8 compatible (no use of Java 9+ features like `var`, `String.repeat()`, etc.)

---

## Theoretical Foundation

The fluid mechanics module in NeqSim is based on the work presented in:

> **Solbraa, E. (2002).** *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.*
> Dr.ing. thesis, Norwegian University of Science and Technology (NTNU).
> ISBN: 978-82-471-5541-7. [Available at NVA](https://hdl.handle.net/11250/231326)

The key contributions from this work include:

1. **Two-fluid model** for gas-liquid pipe flow with interphase mass and heat transfer
2. **Multicomponent mass transfer** based on the Maxwell-Stefan equations
3. **Film theory** with thermodynamic and finite flux corrections
4. **Reactive mass transfer** with enhancement factors for chemical absorption
5. **High-pressure effects** on mass transfer coefficients and equilibrium

### Governing Equations

The two-phase flow is modeled using separate conservation equations for each phase:

**Mass Conservation (per component i):**
$$\frac{\partial (\alpha_k \rho_k x_{i,k})}{\partial t} + \frac{\partial (\alpha_k \rho_k x_{i,k} v_k)}{\partial z} = \dot{m}_{i,k}$$

**Momentum Conservation:**
$$\frac{\partial (\alpha_k \rho_k v_k)}{\partial t} + \frac{\partial (\alpha_k \rho_k v_k^2)}{\partial z} = -\alpha_k \frac{\partial P}{\partial z} - F_{w,k} - F_{i,k} + \alpha_k \rho_k g \sin\theta$$

**Energy Conservation:**
$$\frac{\partial (\alpha_k \rho_k h_k)}{\partial t} + \frac{\partial (\alpha_k \rho_k h_k v_k)}{\partial z} = \dot{Q}_{w,k} + \dot{Q}_{i,k}$$

Where:
- $\alpha_k$ = volume fraction of phase k
- $\rho_k$ = density
- $v_k$ = velocity
- $\dot{m}_{i,k}$ = interphase mass transfer rate of component i
- $F_{w,k}$, $F_{i,k}$ = wall and interphase friction
- $\dot{Q}_{w,k}$, $\dot{Q}_{i,k}$ = wall and interphase heat transfer

---

## Package Structure

| Package under `neqsim.fluidmechanics` | Current entry points |
|---|---|
| `flowsystem.onephaseflowsystem.pipeflowsystem` | `PipeFlowSystem` |
| `flowsystem.twophaseflowsystem.twophasepipeflowsystem` | `TwoPhasePipeFlowSystem`, builder and `PipeFlowResult` |
| `flownode.onephasenode.onephasepipeflownode` | `onePhasePipeFlowNode` |
| `flownode.twophasenode.twophasepipeflownode` | `StratifiedFlowNode`, `AnnularFlow`, `DropletFlowNode` |
| `geometrydefinitions.pipe` | `PipeData` |
| `flownode.fluidboundary.heatmasstransfercalc` | `FluidBoundary`, `FluidBoundaryInterface` |
| `flowsolver.onephaseflowsolver.onephasepipeflowsolver` | `OnePhaseFixedStaggeredGrid` |
| `flowsolver.twophaseflowsolver.twophasepipeflowsolver` | `TwoPhaseFixedStaggeredGridSolver`, `MassTransferConfig` |

---

## Flow Systems

### Single-Phase Pipe Flow

The low-level class is `PipeFlowSystem`; its geometry uses `PipeData`. Flow rate
and inlet thermodynamics belong to the fluid, while length/elevation/thermal
boundary arrays belong to the flow system. Arrays below describe the two ends
of one leg. This complete steady, isothermal gas example is compiled and run
by `PipelineGuideDocumentationTest`.

<!-- pipeline-doc-test: single-phase -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemSrkEos;

Logger logger = LogManager.getLogger("SinglePhaseFlowExample");
SystemSrkEos gas = new SystemSrkEos(288.15, 70.0); // K, bara
gas.addComponent("methane", 0.95);
gas.addComponent("nitrogen", 0.05);
gas.setMixingRule("classic");
gas.setTotalFlowRate(50.0, "kg/sec");
gas.init(0);
gas.initProperties();

PipeFlowSystem flow = new PipeFlowSystem();
flow.setInletThermoSystem(gas);
flow.setNumberOfLegs(1);
flow.setNumberOfNodesInLeg(12);
GeometryDefinitionInterface[] geometry = {
    new PipeData(0.5, 1.0e-5), new PipeData(0.5, 1.0e-5)
}; // Inner diameter and roughness, m
flow.setEquipmentGeometry(geometry);
flow.setLegHeights(new double[] {0.0, 0.0});
flow.setLegPositions(new double[] {0.0, 15000.0}); // m
flow.setLegOuterTemperatures(new double[] {288.15, 288.15}); // K
flow.setLegWallHeatTransferCoefficients(new double[] {0.0, 0.0});
flow.setLegOuterHeatTransferCoefficients(new double[] {0.0, 0.0});
flow.createSystem();
flow.init();
flow.setFailOnNonConvergence(true);
flow.solveSteadyState(1); // Isothermal hydraulic solve

int outletIndex = flow.getTotalNumberOfNodes() - 1;
double outletPressure = flow.getNode(outletIndex).getBulkSystem().getPressure();
logger.info("Outlet: {} bara; pressure drop: {} bar", outletPressure,
    gas.getPressure() - outletPressure);
logger.info("Outlet velocity: {} m/s", flow.getNode(outletIndex).getVelocity());

// Solver type 1 uses face velocities and the upstream cell's EOS density.
double inletMassFlux = flow.getNode(1).getVelocityIn().doubleValue()
    * flow.getNode(0).getGeometry().getArea()
    * flow.getNode(0).getBulkSystem().getPhase(0).getDensity();
double outletMassFlux = flow.getNode(outletIndex).getVelocityIn().doubleValue()
    * flow.getNode(outletIndex - 1).getGeometry().getArea()
    * flow.getNode(outletIndex - 1).getBulkSystem().getPhase(0).getDensity();
logger.info("Finite-volume boundary flows: inlet={} kg/s; outlet={} kg/s",
    inletMassFlux, outletMassFlux);
logger.info("Reported outlet node flow: {} kg/s",
    flow.getNode(outletIndex).getBulkSystem().getFlowRate("kg/sec"));
logger.info("Convergence: {}", flow.getConvergenceReport().getMessage());
```

Expect positive outlet pressure below 70 bara and a converged hydraulic solve.
This example assumes a single gas phase throughout. Solver type `1` does not
solve a cooling-pipeline energy balance. Inventory-residual diagnostics in the
convergence report are only defined for transient calculations.

**Flow reporting limitation:** for this 12-cell case, the current outlet node
reports approximately 49.893754 kg/s, a 0.21249% difference from the specified
50 kg/s feed. The finite-volume inlet and outlet boundary fluxes are both
approximately 49.885716 kg/s and agree to about 2 × 10⁻¹² relative. Thus the
node-reported difference is not evidence of global finite-volume mass loss.
The distinction arises from the staggered face/cell layout and the solver's
EOS density (`getPhase(0).getDensity()`), versus the physical-property density
used when initializing/reporting node flow. The boundary flux also differs
from the specified feed; account for that consistency limitation when
connecting this low-level model to other equipment.

The regression independently reconstructs and compares the two boundary
fluxes using the solver's equation tolerance. It does not require the current
node-flow discrepancy to remain present.

### Two-Phase Pipe Flow

#### Simplified API (Recommended)

This complete short-pipe example uses the factory and structured result API.
The component amounts are in mol/s (0.1 mol/s methane and 0.05 mol/s water)
and supply an explicit inlet flow. The phase indices seed the initial
condition; the model determines the subsequent phase state. The short length
keeps this an API demonstration, not a long-pipeline qualification.

<!-- pipeline-doc-test: two-phase-factory -->
```java
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.TwoPhasePipeFlowSystem;
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.PipeFlowResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

Logger logger = LogManager.getLogger("TwoPhaseFlowExample");
SystemInterface fluid = new SystemSrkEos(295.3, 5.0); // K, bara
fluid.addComponent("methane", 0.1, 0); // mol/s, initial gas phase
fluid.addComponent("water", 0.05, 1); // mol/s, initial liquid phase
fluid.setMixingRule("classic");
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.025, 3.0, 10);
PipeFlowResult result = pipe.solveWithMassTransfer();
logger.info("Pressure drop: {} bar; outlet temperature: {} K",
    result.getTotalPressureDrop(), result.getOutletTemperature());
Map<String, double[]> data = result.toMap();
logger.info("Returned profile fields: {}", data.keySet());
```

#### Factory Methods

| Method | Description |
|--------|-------------|
| `horizontalPipe(fluid, diam, len, nodes)` | Horizontal pipe |
| `verticalPipe(fluid, diam, len, nodes, upward)` | Vertical pipe |
| `inclinedPipe(fluid, diam, len, nodes, angleDeg)` | Inclined pipe |
| `subseaPipe(fluid, diam, len, nodes, seawaterTempC)` | Subsea pipeline |
| `buriedPipe(fluid, diam, len, nodes, groundTempC)` | Buried pipeline |

#### Builder Pattern (Full Control)

This is an alternative configuration using the `fluid` defined above.

```java
import neqsim.fluidmechanics.flownode.FlowPattern;
// For advanced configurations, use the builder
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(fluid)
    .withDiameter(0.15, "m")
    .withLength(1000, "m")
    .withNodes(50)
    .withFlowPattern(FlowPattern.STRATIFIED)
    .withConvectiveBoundary(278.15, "K", 10.0)
    .enableNonEquilibriumMassTransfer()
    .build();

PipeFlowResult result = pipe.solve();
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
| Stratified | `StratifiedFlowNode` | Separated gas-liquid layers |
| Annular | `AnnularFlow` | Liquid film on wall, gas core |
| Droplet/Mist | `DropletFlowNode` | Liquid droplets in gas |
| Slug | `SlugFlowNode` | Intermittent gas-liquid slugs |
| Bubble | `BubbleFlowNode` | Gas bubbles in liquid |

---

## Non-Equilibrium Modeling

NeqSim distinguishes between **equilibrium** and **non-equilibrium** calculations at the gas-liquid interface. In equilibrium calculations, the phases are assumed to be at thermodynamic equilibrium at the interface. In non-equilibrium calculations, finite mass and heat transfer rates are considered.

### Architecture

```
FluidBoundary (abstract)
├── EquilibriumFluidBoundary       # Interface at equilibrium
└── NonEquilibriumFluidBoundary    # Finite transfer rates
    └── KrishnaStandartFilmModel   # Film theory implementation
        └── ReactiveKrishnaStandartFilmModel  # With chemical reactions
```

### Equilibrium vs Non-Equilibrium

| Aspect | Equilibrium | Non-Equilibrium |
|--------|-------------|-----------------|
| Interface conditions | Thermodynamic equilibrium | Finite driving forces |
| Mass transfer | Instantaneous | Rate-limited |
| Heat transfer | Instantaneous | Rate-limited |
| Computation | Simpler | More rigorous |
| Applications | Long residence times | Short contact times, absorption |

### Enabling Non-Equilibrium Calculations

```java
// Get the flow node
FlowNodeInterface node = flowSystem.getNode(i);

// Enable mass and heat transfer calculations
node.getFluidBoundary().setMassTransferCalc(true);
node.getFluidBoundary().setHeatTransferCalc(true);

// Enable thermodynamic corrections (activity coefficients)
node.getFluidBoundary().useThermodynamicCorrections(true, 0);  // Gas phase
node.getFluidBoundary().useThermodynamicCorrections(true, 1);  // Liquid phase

// Enable finite flux corrections (Stefan flow)
node.getFluidBoundary().useFiniteFluxCorrection(true, 0);
node.getFluidBoundary().useFiniteFluxCorrection(true, 1);
```

---

## Mass Transfer Models

### Film Theory

The mass transfer in NeqSim is based on the **film theory** with multicomponent extensions. The key classes are:

| Class | Description |
|-------|-------------|
| `FluidBoundary` | Abstract base for interphase calculations |
| `NonEquilibriumFluidBoundary` | Non-equilibrium mass/heat transfer |
| `KrishnaStandartFilmModel` | Krishna-Standart multicomponent model |
| `ReactiveKrishnaStandartFilmModel` | With chemical reaction enhancement |

### Single-Phase (Wall) Mass Transfer

For mass transfer from a flowing fluid to a wall (e.g., pipe wall, packing surface):

$$Sh = \frac{k_c \cdot d}{D_{AB}} = f(Re, Sc)$$

Where:
- $Sh$ = Sherwood number
- $k_c$ = mass transfer coefficient (m/s)
- $d$ = characteristic length (m)
- $D_{AB}$ = binary diffusion coefficient (m²/s)
- $Sc = \nu / D_{AB}$ = Schmidt number

**Correlations implemented:**

| Flow Regime | Correlation | Range |
|-------------|-------------|-------|
| Laminar | $Sh = 3.66$ | $Re < 2300$ |
| Turbulent | $Sh = 0.023 \cdot Re^{0.83} \cdot Sc^{0.33}$ | $Re > 10000$ |
| Transition | Interpolation | $2300 < Re < 10000$ |

---

## Heat Transfer Models

### Single-Phase Heat Transfer

Heat transfer to/from pipe walls follows analogous correlations to mass transfer:

$$Nu = \frac{h \cdot d}{k} = f(Re, Pr)$$

Where:
- $Nu$ = Nusselt number
- $h$ = heat transfer coefficient (W/m²·K)
- $k$ = thermal conductivity (W/m·K)
- $Pr = \mu \cdot c_p / k$ = Prandtl number

**Correlations implemented:**

| Flow Regime | Correlation |
|-------------|-------------|
| Laminar | $Nu = 3.66$ (constant wall temp) |
| Turbulent | Dittus-Boelter: $Nu = 0.023 \cdot Re^{0.8} \cdot Pr^{n}$ |
| Transition | Gnielinski correlation |

Where $n = 0.4$ for heating and $n = 0.3$ for cooling.

### Heat Transfer with Phase Change

When mass transfer occurs, the heat transfer is coupled:

$$\dot{Q}_i = h_{eff} \cdot A \cdot (T_{bulk} - T_i) + \sum_j \dot{n}_j \cdot \Delta H_{vap,j}$$

The effective heat transfer coefficient accounts for the latent heat of evaporation/condensation.

---

## Two-Phase Mass Transfer

### Multicomponent Maxwell-Stefan Model

For multicomponent systems, the mass transfer is described by the Maxwell-Stefan equations rather than Fick's law. The molar flux of component $i$ relative to the molar average velocity is:

$$-c_t \nabla x_i = \sum_{j=1, j \neq i}^{n} \frac{x_i N_j - x_j N_i}{c_t D_{ij}}$$

In NeqSim, this is solved using the **Krishna-Standart film model**:

### Binary Mass Transfer Coefficients

The binary mass transfer coefficients are calculated from:

$$k_{ij} = \frac{Sh \cdot D_{ij}}{d}$$

Where $D_{ij}$ is the binary diffusion coefficient calculated from:
- **Gas phase**: Chapman-Enskog theory
- **Liquid phase**: Wilke-Chang correlation

### Mass Transfer Coefficient Matrix

For multicomponent systems, the mass transfer coefficients form a matrix $[k]$:

The following is schematic matrix assembly, not a callable public method.

```text
// In KrishnaStandartFilmModel
public double calcMassTransferCoefficients(int phaseNum) {
    int n = getNumberOfComponents() - 1;

    for (int i = 0; i < n; i++) {
        double tempVar = 0;
        for (int j = 0; j < getNumberOfComponents(); j++) {
            if (i != j) {
                tempVar += x[j] / k_binary[i][j];
            }
            if (j < n) {
                K[i][j] = -x[i] * (1.0/k_binary[i][j] - 1.0/k_binary[i][n]);
            }
        }
        K[i][i] = tempVar + x[i] / k_binary[i][n];
    }
    return K.inverse();  // [k] matrix
}
```

### Interphase Mass Transfer

The total molar flux vector is:

$$\mathbf{N} = c_t [\mathbf{k}] (\mathbf{x}_{bulk} - \mathbf{x}_{interface})$$

With corrections for:

1. **Thermodynamic non-ideality**: Activity coefficient gradients
2. **Finite flux (Stefan flow)**: High mass transfer rates
3. **Film thickness variations**: Due to flow regime

### Schmidt Number

The Schmidt number characterizes the ratio of momentum to mass diffusivity:

$$Sc_{ij} = \frac{\nu}{D_{ij}}$$

```java
// Calculation in KrishnaStandartFilmModel
for (int i = 0; i < nComponents; i++) {
    for (int j = 0; j < nComponents; j++) {
        binarySchmidtNumber[phase][i][j] =
            kinematicViscosity / diffusionCoefficient[i][j];
    }
}
```

### Interphase Transport Coefficients

The interphase transport coefficients depend on the flow regime:

| Flow Regime | Gas-side $k_G$ | Liquid-side $k_L$ |
|-------------|----------------|-------------------|
| Stratified | Smooth interface correlation | Penetration theory |
| Annular | Film correlation | Film flow correlation |
| Droplet | Droplet correlations | Internal circulation |
| Bubble | External mass transfer | Higbie penetration |

---

## Two-Phase Heat Transfer

### Interphase Heat Transfer

Heat transfer between gas and liquid phases occurs at the interface:

$$\dot{Q}_{GL} = h_{GL} \cdot A_i \cdot (T_G - T_L)$$

Where $A_i$ is the interfacial area per unit volume.

### Heat Transfer Coefficient Correlations

The interphase heat transfer coefficient is related to mass transfer through the Chilton-Colburn analogy:

$$\frac{h}{k_c \cdot \rho \cdot c_p} = \left(\frac{Sc}{Pr}\right)^{2/3}$$

**Implemented correlations by flow regime:**

| Flow Regime | Correlation Type |
|-------------|------------------|
| Stratified | Flat interface model |
| Annular | Film evaporation/condensation |
| Dispersed | Droplet/bubble heat transfer |

### Coupling of Heat and Mass Transfer

In non-equilibrium calculations, heat and mass transfer are coupled through:

1. **Latent heat effects**: Evaporation/condensation carries enthalpy
2. **Sensible heat**: Temperature gradients drive conduction
3. **Dufour effect**: Mass flux induces heat flux (usually negligible)
4. **Soret effect**: Temperature gradient induces mass flux (usually negligible)

The interphase heat flux includes both contributions:

$$\dot{Q}_i = h \cdot (T_{bulk} - T_i) + \sum_j N_j \cdot \bar{H}_j$$

Where $\bar{H}_j$ is the partial molar enthalpy of component $j$.

### Wall Heat Transfer in Two-Phase Flow

For `PipeBeggsAndBrills`, use `setHeatTransferCoefficient(double)` for an
effective U-value and `setConstantSurfaceTemperature(double, String)` for the
thermal boundary. See the [heat-transfer examples](heat_transfer) for full
setup and the resistance equations with a consistent reference area.

---

## Reactive Mass Transfer

### Enhancement Factors

For absorption with chemical reaction (e.g., CO₂ into amine solutions), the mass transfer is enhanced:

$$N_{CO2} = E \cdot k_L \cdot (C_{CO2,i} - C_{CO2,bulk})$$

Where $E$ is the enhancement factor.

### Enhancement Factor Models

| Model | Description | Application |
|-------|-------------|-------------|
| Film theory | $E = \sqrt{1 + Ha^2}$ | Fast reactions |
| Penetration theory | Numerical solution | Moderate reactions |
| Danckwerts | Pseudo-first order | Industrial absorbers |

The Hatta number characterizes the reaction regime:

$$Ha = \frac{\sqrt{k_{rxn} \cdot D_A}}{k_L}$$

### Reactive Film Model

The following describes enhancement-factor application schematically.

```text
// ReactiveKrishnaStandartFilmModel extends KrishnaStandartFilmModel

// Enhancement factor calculation
EnhancementFactor enhancement = new EnhancementFactor();
double E = enhancement.calculate(hattaNumber, reactionOrder);

// Apply to mass transfer
double N_CO2 = E * k_L * (C_interface - C_bulk);
```

### CO₂-Amine Systems

NeqSim includes specific models for CO₂ absorption into:
- **MDEA** (methyldiethanolamine)
- **MEA** (monoethanolamine)
- **DEA** (diethanolamine)
- **Activated MDEA** (with piperazine)

Reaction kinetics:
$$r_{CO2} = k_2 \cdot [CO2] \cdot [Amine]$$

With temperature-dependent rate constants from experimental data.

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

For `PipeFlowSystem`, initialize and solve the steady flow first. Supply the
boundary time series through `getTimeSeries().setTimes(double[])`,
`setInletThermoSystems(SystemInterface[])`, and
`setNumberOfTimeStepsInInterval(int)` before `solveTransient(1)`. The argument
`1` selects the solver equations; it is **not** a one-second timestep.
Repeated calls without a time series do not define a transient boundary event.

See [compositional tracking](../wiki/pipeline_transient_simulation) for
complete time-series examples, conservation diagnostics, and supported boundary
conditions.

## Heat Transfer

For a process-level cooling calculation, use the self-contained
[heat-transfer example](heat_transfer#basic-heat-transfer-in-pipe-flow).
It selects `PipeBeggsAndBrills`, explicitly defines mass flow and surrounding
temperature, and uses the model's actual thermal API.

For the low-level flow system, set per-leg temperatures with
`setLegOuterTemperatures(double[])` and wall/external coefficients with
`setLegWallHeatTransferCoefficients(double[])` and
`setLegOuterHeatTransferCoefficients(double[])` before creating the system.
An energy-enabled solver is required for temperature evolution; the
isothermal example above deliberately sets both coefficients to zero.

## Geometry Definitions

### Pipe Geometry

`PipeData` describes inner diameter, roughness, and wall/environment properties.
It does not contain the whole route; use the flow system's `setLegPositions`
and `setLegHeights` for that. The single-phase example above shows the complete
route/geometry setup.

### Internal Geometry

Use `PipeData.setCarbonSteelWall(thicknessM)` and
`PipeData.addMineralWoolInsulation(thicknessM)` for material layers. The
[heat-transfer guide](heat_transfer#usage-in-neqsim) demonstrates the distinction
between wall resistance and the fluid-side coefficient. A deposit also changes
the hydraulic flow area; assigning an insulation material is not a model of
wax deposition kinetics.

## Flow Solver Options

`flow.getSolver()` returns the active `FlowSolverInterface` after solving.
Solver-specific controls and convergence reports are documented by the actual
solver class. For the single-phase example, `setFailOnNonConvergence(true)`
and `getConvergenceReport()` expose whether the hydraulic solve succeeded.
Do not assume generic `setMaxIterations`, `setConvergenceCriteria`, or
`setRelaxationFactor` methods exist on that interface.

## Integration with Process Equipment

Use `OnePhasePipeLine`, `MultiphasePipe`, or a correlation model such as
`PipeBeggsAndBrills` for a `ProcessSystem`. `Pipeline` is their base class and
should not be used as a substitute for selecting a physical model. See the
[complete pipeline examples](../process/equipment/pipeline_simulation#examples)
for inlet streams, real geometric setters, and outlet results.

## Visualization

Read node values from `flow.getNode(i).getBulkSystem()` and iterate through
`flow.getTotalNumberOfNodes()` in a low-level model. Process models such as
`PipeBeggsAndBrills` provide pressure, temperature, and length profiles for
plotting with the caller's charting tool. See the
[profile example](../process/equipment/pipeline_simulation#example-2-subsea-multiphase-flowline)
for units and array lengths.

## Example: Gas Pipeline

The [single-phase example](#single-phase-pipe-flow) is a complete 15 km gas
pipeline calculation using the low-level API. For a 100 km process-equipment
example, use the [gas export pipeline](../process/equipment/pipeline_simulation#example-1-gas-export-pipeline).
Both explicitly define the fluid, mixing rule, flow rate, inner diameter,
roughness, calculation mode, and result units.

## Best Practices

1. **Use appropriate number of nodes** - more nodes for accuracy, fewer for speed
2. **Check flow regime** in two-phase calculations
3. **Validate against correlations** for your specific application
4. **Consider elevation profile** for long pipelines
5. **Include heat transfer** for hot fluids or cold environments
6. **Enable non-equilibrium** for absorption and short-contact processes
7. **Use thermodynamic corrections** for non-ideal liquid phases

---

## Test Suite

The fluid mechanics package includes comprehensive unit tests:

| Test File | Coverage |
|-----------|----------|
| `TwoPhasePipeFlowSystemTest.java` | System setup, steady-state solving, mass/heat transfer, model comparisons |
| `NonEquilibriumPipeFlowTest.java` | Non-equilibrium mass transfer, evaporation, dissolution, bidirectional transfer |
| `FlowPatternDetectorTest.java` | Flow pattern detection models (Taitel-Dukler, Baker, Barnea, Beggs-Brill) |
| `InterfacialAreaCalculatorTest.java` | Interfacial area calculations for all flow patterns |
| `MassTransferCoefficientCalculatorTest.java` | Mass transfer coefficient correlations |
| `TwoPhasePipeFlowSystemBuilderTest.java` | Builder API tests |

### Known Test Limitations

Some advanced test scenarios are disabled pending solver optimization:
- Complete phase evaporation/dissolution tests (solver timeout)
- Transient water drying simulations (solver timeout)
- Subsea pipeline with large temperature gradients (temperature calculation issues)

See [TwoPhasePipeFlowSystem_Development_Plan.md](TwoPhasePipeFlowSystem_Development_Plan) for details.

---

## References

1. **Solbraa, E. (2002).** *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.*
   Dr.ing. thesis, NTNU. ISBN: 978-82-471-5541-7.
   [Available at NVA](https://hdl.handle.net/11250/231326)

2. **Krishna, R., Standart, G.L. (1976).** Mass and energy transfer in multicomponent systems.
   *Chemical Engineering Communications*, 3(4-5), 201-275.

3. **Taylor, R., Krishna, R. (1993).** *Multicomponent Mass Transfer*. Wiley.

4. **Bird, R.B., Stewart, W.E., Lightfoot, E.N. (2002).** *Transport Phenomena*. 2nd ed. Wiley.

5. **Danckwerts, P.V. (1970).** *Gas-Liquid Reactions*. McGraw-Hill.

---

## Related Documentation

- [Mass Transfer Modeling](mass_transfer) - Detailed multicomponent mass transfer models
- [Heat Transfer Modeling](heat_transfer) - Detailed heat transfer correlations and models
- [Process Pipeline Equipment](../process/equipment/pipelines) - Pipeline in process simulation
- [Physical Properties](../physical_properties/) - Viscosity, diffusivity, and density models
- [Thermodynamics](../thermo/) - Equations of state and phase equilibria
- [Transient Pipeline Simulation](../wiki/pipeline_transient_simulation) - Dynamic pipeline modeling
