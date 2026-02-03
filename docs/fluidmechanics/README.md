---
title: Fluid Mechanics Package
description: The `fluidmechanics` package provides models for pipeline flow, pressure drop calculations, and transient flow simulation with rigorous non-equilibrium thermodynamic calculations for mass and heat tra...
---

# Fluid Mechanics Package

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
- [Usage Examples](#usage-examples)

## Related Documentation

| Document | Description |
|----------|-------------|
| [MassTransferAPI.md](MassTransferAPI) | **Complete API documentation** for mass transfer with methods, parameters, and examples |
| [EvaporationDissolutionTutorial.md](EvaporationDissolutionTutorial) | **Practical tutorial** for liquid evaporation and gas dissolution with worked examples |
| [MASS_TRANSFER_MODEL_IMPROVEMENTS.md](MASS_TRANSFER_MODEL_IMPROVEMENTS) | **Technical review** of mass transfer model with improvement recommendations |
| [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer) | Complete theory for interphase mass and heat transfer |
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

#### Simplified API (Recommended)

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.*;
import neqsim.thermo.system.SystemSrkEos;

// Create two-phase fluid
SystemInterface fluid = new SystemSrkEos(300.0, 30.0);
fluid.addComponent("methane", 0.80, 0);    // Gas phase
fluid.addComponent("n-decane", 0.20, 1);   // Liquid phase
fluid.createDatabase(true);
fluid.setMixingRule(2);

// Create horizontal pipe using factory method
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.horizontalPipe(fluid, 0.15, 1000, 50);

// Solve with mass transfer and get structured results
PipeFlowResult result = pipe.solveWithMassTransfer();

// Access results
System.out.println("Pressure drop: " + result.getTotalPressureDrop() + " bar");
System.out.println("Outlet temperature: " + result.getOutletTemperature() + " K");
System.out.println(result);  // Formatted summary

// Export for plotting (e.g., in Jupyter with neqsim-python)
Map<String, double[]> data = result.toMap();
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

```java
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
| Stratified | `StratifiedFlow` | Separated gas-liquid layers |
| Annular | `AnnularFlow` | Liquid film on wall, gas core |
| Droplet/Mist | `DropletFlow` | Liquid droplets in gas |
| Slug | `SlugFlow` | Intermittent gas-liquid slugs |
| Bubble | `BubbleFlow` | Gas bubbles in liquid |

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
node.getFluidBoundary().setThermodynamicCorrections(0, true);  // Gas phase
node.getFluidBoundary().setThermodynamicCorrections(1, true);  // Liquid phase

// Enable finite flux corrections (Stefan flow)
node.getFluidBoundary().setFiniteFluxCorrection(0, true);
node.getFluidBoundary().setFiniteFluxCorrection(1, true);
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

```java
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

For heat transfer to the pipe wall in two-phase flow:

```java
// Set overall heat transfer coefficient
pipe.setOverallHeatTransferCoefficient(10.0);  // W/(m²·K)

// Or calculate from resistances
// 1/U = 1/h_inner + ln(r_o/r_i)/(2πkL) + 1/h_outer
```

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

```java
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
