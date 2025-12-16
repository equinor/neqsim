# Heat Transfer Modeling in NeqSim

This document provides detailed documentation of the heat transfer models implemented in the NeqSim fluid mechanics package.

## Table of Contents
- [Overview](#overview)
- [Theoretical Background](#theoretical-background)
- [Single-Phase Heat Transfer](#single-phase-heat-transfer)
- [Two-Phase Heat Transfer](#two-phase-heat-transfer)
- [Wall Heat Transfer](#wall-heat-transfer)
- [Interphase Heat Transfer](#interphase-heat-transfer)
- [Heat-Mass Transfer Coupling](#heat-mass-transfer-coupling)
- [Implementation Classes](#implementation-classes)
- [Usage Examples](#usage-examples)
- [References](#references)

---

## Overview

NeqSim implements comprehensive heat transfer models for:

- Convective heat transfer in pipes
- Interphase heat transfer in two-phase flow
- Heat transfer coupled with mass transfer
- Phase change (evaporation/condensation)
- Heat loss to surroundings

The models are based on established correlations and are coupled with the rigorous thermodynamic calculations in NeqSim.

---

## Theoretical Background

### Energy Balance

The energy conservation equation for pipe flow:

$$\frac{\partial (\rho h)}{\partial t} + \frac{\partial (\rho v h)}{\partial z} = \dot{Q}_{wall} + \dot{Q}_{interphase}$$

Where:
- $h$ = specific enthalpy (J/kg)
- $\dot{Q}_{wall}$ = heat transfer rate to/from wall (W/m³)
- $\dot{Q}_{interphase}$ = heat transfer rate between phases (W/m³)

### Heat Transfer Mechanisms

| Mechanism | Equation | Application |
|-----------|----------|-------------|
| Conduction | $q = -k \nabla T$ | Through solid walls, stagnant fluids |
| Convection | $q = h (T_w - T_f)$ | Flowing fluids to walls |
| Radiation | $q = \epsilon \sigma (T_1^4 - T_2^4)$ | High temperature systems |
| Latent heat | $\dot{Q} = \dot{m} \Delta H_{vap}$ | Phase change |

---

## Single-Phase Heat Transfer

### Dimensionless Numbers

| Number | Definition | Physical Meaning |
|--------|------------|------------------|
| Nusselt (Nu) | $h \cdot d / k$ | Ratio of convective to conductive heat transfer |
| Prandtl (Pr) | $\mu c_p / k$ | Ratio of momentum to thermal diffusivity |
| Reynolds (Re) | $\rho v d / \mu$ | Ratio of inertial to viscous forces |
| Péclet (Pe) | $Re \cdot Pr$ | Ratio of advective to diffusive heat transport |

### Prandtl Number

The Prandtl number characterizes the relative thickness of thermal and velocity boundary layers:

```java
// Calculated in FlowNode
double Pr = viscosity * heatCapacity / thermalConductivity;
```

Typical values:

| Fluid | Pr |
|-------|-----|
| Gases | 0.7 - 1.0 |
| Water | 1.7 - 13 |
| Light oils | 10 - 1000 |
| Heavy oils | 100 - 100,000 |

### Correlations

#### Laminar Flow (Re < 2300)

**Constant wall temperature:**
$$Nu = 3.66$$

**Constant heat flux:**
$$Nu = 4.36$$

**Developing flow (Sieder-Tate):**
$$Nu = 1.86 \left(\frac{Re \cdot Pr \cdot d}{L}\right)^{1/3} \left(\frac{\mu}{\mu_w}\right)^{0.14}$$

#### Turbulent Flow (Re > 10,000)

**Dittus-Boelter equation:**
$$Nu = 0.023 \cdot Re^{0.8} \cdot Pr^{n}$$

Where:
- $n = 0.4$ for heating (fluid being heated)
- $n = 0.3$ for cooling (fluid being cooled)

**Gnielinski correlation (more accurate):**
$$Nu = \frac{(f/8)(Re - 1000)Pr}{1 + 12.7(f/8)^{0.5}(Pr^{2/3} - 1)}$$

Valid for: $3000 < Re < 5 \times 10^6$, $0.5 < Pr < 2000$

#### Transition Region (2300 < Re < 10,000)

**Gnielinski correlation** or linear interpolation between laminar and turbulent.

### Implementation

```java
// In InterphaseTransportCoefficientBaseClass
public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, 
                                               FlowNodeInterface node) {
    double Re = node.getReynoldsNumber(phase);
    double Nu;
    
    if (Re < 2300) {
        Nu = 3.66;  // Laminar
    } else if (Re < 10000) {
        // Transition - Gnielinski
        double f = calcWallFrictionFactor(phase, node);
        Nu = (f/8) * (Re - 1000) * prandtlNumber 
             / (1 + 12.7 * Math.sqrt(f/8) * (Math.pow(prandtlNumber, 2.0/3.0) - 1));
    } else {
        // Turbulent - Dittus-Boelter
        Nu = 0.023 * Math.pow(Re, 0.8) * Math.pow(prandtlNumber, 0.4);
    }
    
    return Nu * thermalConductivity / hydraulicDiameter;
}
```

---

## Two-Phase Heat Transfer

### Flow Pattern Effects

Heat transfer in two-phase flow depends strongly on the flow pattern:

| Flow Pattern | Dominant Mechanism | Heat Transfer Characteristics |
|--------------|-------------------|-------------------------------|
| **Stratified** | Convection in each phase | Independent gas/liquid correlations |
| **Annular** | Film evaporation/condensation | High liquid-side coefficients |
| **Slug** | Alternating mechanisms | Time-averaged values |
| **Bubble** | Enhanced liquid mixing | Increased liquid-side coefficient |
| **Mist** | Droplet evaporation | Reduced wall wetting |

### Two-Phase Multiplier Approach

Some correlations use a two-phase multiplier:

$$h_{TP} = F \cdot h_{LO}$$

Where:
- $h_{LO}$ = heat transfer coefficient for liquid flowing alone
- $F$ = two-phase multiplier (function of quality, flow pattern)

### Flow Pattern-Specific Correlations

#### Stratified Flow

Gas and liquid are treated separately:

$$h_{gas} = \text{Single-phase correlation with } d_h = 4A_G/P_G$$
$$h_{liquid} = \text{Single-phase correlation with } d_h = 4A_L/P_L$$

#### Annular Flow

**Liquid film:**
$$h_L = 0.023 \cdot Re_f^{0.8} \cdot Pr_L^{0.4} \cdot \frac{k_L}{\delta}$$

Where $\delta$ is the film thickness and $Re_f = 4\Gamma/\mu_L$ is the film Reynolds number.

**Gas core:**
$$h_G = 0.023 \cdot Re_G^{0.8} \cdot Pr_G^{0.4} \cdot \frac{k_G}{d - 2\delta}$$

---

## Wall Heat Transfer

### Overall Heat Transfer Coefficient

For heat transfer from fluid to surroundings through the pipe wall:

$$\frac{1}{U} = \frac{1}{h_i} + \frac{r_i \ln(r_o/r_i)}{k_{wall}} + \frac{r_i}{r_o \cdot h_o}$$

Where:
- $h_i$ = inner (fluid-side) heat transfer coefficient
- $h_o$ = outer (ambient-side) heat transfer coefficient
- $k_{wall}$ = wall thermal conductivity
- $r_i$, $r_o$ = inner and outer radii

### Insulation

For insulated pipes, add insulation resistance:

$$\frac{1}{U} = \frac{1}{h_i} + \frac{r_i \ln(r_o/r_i)}{k_{wall}} + \frac{r_i \ln(r_{ins}/r_o)}{k_{ins}} + \frac{r_i}{r_{ins} \cdot h_o}$$

### Buried Pipelines

For buried pipelines, the outer resistance includes soil conduction:

$$R_{soil} = \frac{\ln(2z/r_o)}{2\pi k_{soil}}$$

Where $z$ is the burial depth.

### Usage in NeqSim

```java
// Set overall heat transfer coefficient directly
pipe.setOverallHeatTransferCoefficient(10.0);  // W/(m²·K)

// Or specify components
pipe.setInnerHeatTransferCoefficient(1000.0);   // W/(m²·K)
pipe.setWallThickness(0.01);                     // m
pipe.setWallConductivity(50.0);                  // W/(m·K)
pipe.setInsulationThickness(0.05);               // m
pipe.setInsulationConductivity(0.04);            // W/(m·K)
pipe.setOuterHeatTransferCoefficient(10.0);     // W/(m²·K)

// Set ambient conditions
flowSystem.setSurroundingTemperature(288.15);    // K
```

---

## Interphase Heat Transfer

### Heat Transfer Between Phases

At the gas-liquid interface:

$$\dot{Q}_{GL} = h_{GL} \cdot a_i \cdot (T_G - T_L)$$

Where:
- $h_{GL}$ = interphase heat transfer coefficient (W/m²·K)
- $a_i$ = interfacial area per unit volume (m²/m³)
- $T_G$, $T_L$ = gas and liquid temperatures (K)

### Interfacial Area

The interfacial area depends on the flow pattern:

| Flow Pattern | Interfacial Area $a_i$ |
|--------------|----------------------|
| Stratified | $W/A_{pipe}$ (width / cross-section) |
| Annular | $\pi(d - 2\delta)/A_{pipe}$ |
| Bubble | $6\epsilon_G/d_b$ |
| Droplet | $6\epsilon_L/d_d$ |

### Chilton-Colburn Analogy

The heat and mass transfer coefficients are related:

$$\frac{h}{k_c \cdot \rho \cdot c_p} = \left(\frac{Sc}{Pr}\right)^{2/3}$$

Or in terms of j-factors:

$$j_H = j_D$$

Where:
$$j_H = \frac{Nu}{Re \cdot Pr^{1/3}} = St \cdot Pr^{2/3}$$
$$j_D = \frac{Sh}{Re \cdot Sc^{1/3}}$$

---

## Heat-Mass Transfer Coupling

### Latent Heat Effects

When mass transfer occurs, the associated enthalpy must be considered:

$$\dot{Q}_{total} = \dot{Q}_{sensible} + \dot{Q}_{latent}$$

$$\dot{Q}_{latent} = \sum_j N_j \cdot \Delta H_{vap,j}$$

### Interface Energy Balance

At the gas-liquid interface, the energy balance:

$$h_G (T_G - T_i) + \sum_j N_j H_j^G = h_L (T_i - T_L) + \sum_j N_j H_j^L$$

Rearranging:

$$h_G (T_G - T_i) - h_L (T_i - T_L) = \sum_j N_j (H_j^L - H_j^G) = -\sum_j N_j \Delta H_{vap,j}$$

### Ackermann Correction

For high mass transfer rates, the sensible heat transfer is modified:

$$\dot{Q}_{sensible} = h \cdot \Phi \cdot (T_{bulk} - T_i)$$

Where the Ackermann correction factor:

$$\Phi = \frac{\phi}{e^\phi - 1}$$

And:
$$\phi = \frac{\sum_j N_j c_{p,j}}{h}$$

### Implementation

```java
// In FluidBoundary
public double[] calcInterphaseHeatFlux() {
    // Sensible heat
    double Q_sensible = heatTransferCoefficient[0] * heatTransferCorrection[0] 
                        * (T_bulk - T_interface);
    
    // Latent heat
    double Q_latent = 0;
    for (int j = 0; j < nComponents; j++) {
        Q_latent += nFlux.get(j, 0) * deltaHvap[j];
    }
    
    interphaseHeatFlux[0] = Q_sensible + Q_latent;
    return interphaseHeatFlux;
}
```

---

## Implementation Classes

### Class Hierarchy

```
FluidBoundary
├── heatTransferCoefficient[2]    // Gas, Liquid
├── heatTransferCorrection[2]     // Ackermann factors
├── prandtlNumber[2]
└── interphaseHeatFlux[2]

InterphaseTransportCoefficientBaseClass
├── calcWallHeatTransferCoefficient()
├── calcInterphaseHeatTransferCoefficient()
└── calcWallFrictionFactor()
```

### Key Methods

```java
// InterphaseTransportCoefficientInterface
double calcWallHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);
double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber, FlowNodeInterface node);

// FluidBoundary
void setHeatTransferCalc(boolean calc);
double[] getInterphaseHeatFlux();
double getHeatTransferCoefficient(int phase);
```

---

## Usage Examples

### Basic Heat Transfer in Pipe Flow

```java
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.OnePhasePipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeGeometry;
import neqsim.thermo.system.SystemSrkEos;

// Create hot gas
SystemSrkEos gas = new SystemSrkEos(373.15, 50.0);  // 100°C, 50 bar
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.05);
gas.setMixingRule("classic");

// Pipe geometry
PipeGeometry pipe = new PipeGeometry("Pipeline");
pipe.setDiameter(0.3, "m");
pipe.setLength(10000.0, "m");

// Heat transfer setup
pipe.setOverallHeatTransferCoefficient(5.0);  // W/(m²·K)

// Flow system
OnePhasePipeFlowSystem flow = new OnePhasePipeFlowSystem();
flow.setInletFluid(gas);
flow.setGeometry(pipe);
flow.setSurroundingTemperature(283.15);  // 10°C ambient
flow.setCalculateHeatTransfer(true);
flow.setNumberOfNodes(100);

flow.init();
flow.solveTransient(1);

// Temperature profile
for (int i = 0; i < flow.getNumberOfNodes(); i++) {
    double x = flow.getNode(i).getPosition();
    double T = flow.getNode(i).getTemperature() - 273.15;  // °C
    System.out.println("x = " + x + " m, T = " + T + " °C");
}
```

### Two-Phase with Interphase Heat Transfer

```java
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;

// Create two-phase system
SystemSrkEos fluid = new SystemSrkEos(280.0, 30.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("n-pentane", 0.10);
fluid.addComponent("n-decane", 0.05);
fluid.setMixingRule("classic");

// Initialize with phase split
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Create flow node
PipeData pipe = new PipeData(0.2);  // 0.2 m diameter
StratifiedFlowNode node = new StratifiedFlowNode(fluid, pipe);
node.init();

// Enable heat transfer calculations
node.getFluidBoundary().setHeatTransferCalc(true);
node.getFluidBoundary().setMassTransferCalc(true);

// Solve
node.getFluidBoundary().solve();

// Get interphase heat flux
double[] Q = node.getFluidBoundary().getInterphaseHeatFlux();
System.out.println("Gas-side heat flux: " + Q[0] + " W/m²");
System.out.println("Liquid-side heat flux: " + Q[1] + " W/m²");
```

### Condensation in Pipeline

```java
// Hot gas entering cold pipeline
SystemSrkEos gas = new SystemSrkEos(320.0, 80.0);  // Hot, high pressure
gas.addComponent("methane", 0.80);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.addComponent("n-butane", 0.03);
gas.addComponent("n-pentane", 0.02);
gas.setMixingRule("classic");

// Cold seabed pipeline
TwoPhasePipeFlowSystem flow = new TwoPhasePipeFlowSystem();
flow.setInletFluid(gas);
flow.setGeometry(seabedPipe);
flow.setSurroundingTemperature(277.15);  // 4°C seabed
flow.setCalculateHeatTransfer(true);

flow.init();
flow.solveTransient(1);

// Check for liquid formation
for (int i = 0; i < flow.getNumberOfNodes(); i++) {
    double liquidHoldup = flow.getNode(i).getPhaseFraction(1);
    if (liquidHoldup > 0.01) {
        System.out.println("Condensation at x = " + flow.getNode(i).getPosition() + " m");
        break;
    }
}
```

---

## References

1. **Incropera, F.P., DeWitt, D.P., et al. (2007).** *Fundamentals of Heat and Mass Transfer*. 6th ed. Wiley.

2. **Gnielinski, V. (1976).** New equations for heat and mass transfer in turbulent pipe and channel flow. 
   *Int. Chem. Eng.*, 16(2), 359-368.

3. **Dittus, F.W., Boelter, L.M.K. (1930).** Heat transfer in automobile radiators of the tubular type. 
   *Univ. Calif. Publ. Eng.*, 2(13), 443-461.

4. **Chilton, T.H., Colburn, A.P. (1934).** Mass transfer (absorption) coefficients: Prediction from data 
   on heat transfer and fluid friction. *Ind. Eng. Chem.*, 26(11), 1183-1187.

5. **Solbraa, E. (2002).** *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.* 
   Dr.ing. thesis, NTNU. [NVA](https://hdl.handle.net/11250/231326)

6. **Bird, R.B., Stewart, W.E., Lightfoot, E.N. (2002).** *Transport Phenomena*. 2nd ed. Wiley.

---

## Related Documentation

- [Mass Transfer Modeling](mass_transfer.md) - Companion mass transfer documentation
- [Fluid Mechanics Overview](README.md) - Main fluid mechanics documentation
- [Physical Properties](../physical_properties/README.md) - Thermal conductivity models
- [Pipeline Simulation](../wiki/pipeline_transient_simulation.md) - Transient pipeline modeling
