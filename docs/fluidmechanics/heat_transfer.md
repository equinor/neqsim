---
title: Heat Transfer Modeling in NeqSim
description: This document provides detailed documentation of the heat transfer models implemented in the NeqSim fluid mechanics package.
---

This document provides detailed documentation of the heat transfer models implemented in the NeqSim fluid mechanics package.

**Related Documentation:**
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer) - Coupled heat-mass transfer theory
- [mass_transfer.md](mass_transfer) - Mass transfer correlations
- [TwoPhasePipeFlowModel.md](TwoPhasePipeFlowModel) - Two-phase flow governing equations

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

The equations above describe common correlations, not a literal implementation
of `InterphaseTransportCoefficientBaseClass`: that base class returns zero for
its heat-transfer methods. The single-phase `InterphasePipeFlow` implementation
uses Nu = 3.66 below |Re| = 2000 and a friction-factor/Chilton-Colburn expression
above that threshold. Other classes, including `PipeBeggsAndBrills`, have their
own coefficient calculations. Select and inspect the actual model rather than
assuming all models use the same transition rule.

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

The low-level geometry class is `PipeData`. This complete example calculates
wall and outer-environment resistance, **excluding the inner fluid film**.
The returned coefficient is referenced to the inner pipe area. It does not run
a flow solver or determine an outlet temperature.

<!-- pipeline-doc-test: wall-geometry -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;

Logger logger = LogManager.getLogger("PipeWallExample");
PipeData pipe = new PipeData(0.30, 4.6e-5); // Inner diameter, roughness in m
pipe.setCarbonSteelWall(0.01); // 10 mm steel
pipe.addMineralWoolInsulation(0.05); // 50 mm insulation
pipe.setAirEnvironment(288.15, 2.0); // K, wind velocity m/s
double wallAndOuterCoefficient = pipe.calcOverallHeatTransferCoefficient();
logger.info("Wall plus outer coefficient: {} W/(m2 K)", wallAndOuterCoefficient);
```

For `PipeBeggsAndBrills`, an effective U-value is set with
`setHeatTransferCoefficient(double)`. To include computed inner convection,
wall conduction, insulation, and outer convection, select `DETAILED_U` with
the class-specific setters described in the
[pipeline guide](../process/equipment/pipeline_simulation#heat-transfer).

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

`FluidBoundaryInterface` exposes `setHeatTransferCalc(boolean)`, `solve()`,
and `getInterphaseHeatFlux(int phase)`. Heat flux is read one phase at a time;
there is no no-argument array getter. The film model couples interphase heat
and mass transfer internally. The worked node example below starts at phase
equilibrium and therefore checks a near-zero driving-force baseline; it is
not an evaporation or condensation-rate experiment.

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

| Object | Public method | Result / input |
|---|---|---|
| `InterphaseTransportCoefficientInterface` | `calcWallHeatTransferCoefficient(int, double, FlowNodeInterface)` | Wall coefficient for a phase and Prandtl number |
| `InterphaseTransportCoefficientInterface` | `calcInterphaseHeatTransferCoefficient(int, double, FlowNodeInterface)` | Interphase coefficient |
| `FluidBoundaryInterface` | `setHeatTransferCalc(boolean)` | Enable/disable interphase heat calculation |
| `FluidBoundaryInterface` | `getInterphaseHeatFlux(int)` | Heat flux for the specified phase, W/m² |

---

## Usage Examples

### Basic Heat Transfer in Pipe Flow

This self-contained example uses a process-level `PipeBeggsAndBrills` model
with single-phase gas. It specifies an effective U-value and ambient boundary;
SRK with the classic mixing rule supplies thermodynamic properties. As with
the pipeline guide, put imports at class scope and statements in a method.

<!-- pipeline-doc-test: gas-cooling -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

Logger logger = LogManager.getLogger("GasCoolingExample");
SystemSrkEos gas = new SystemSrkEos(373.15, 50.0); // 100 C, 50 bara
gas.addComponent("methane", 0.95);
gas.addComponent("ethane", 0.05);
gas.setMixingRule("classic");
Stream inlet = new Stream("Hot Gas", gas);
inlet.setFlowRate(10000.0, "kg/hr");
inlet.run();

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Cooling Pipeline", inlet);
pipeline.setDiameter(0.30); // m
pipeline.setLength(10000.0); // m
pipeline.setNumberOfIncrements(20);
pipeline.setConstantSurfaceTemperature(10.0, "C");
pipeline.setHeatTransferCoefficient(5.0); // W/(m2 K), SPECIFIED_U mode
pipeline.run();

double[] temperatureK = pipeline.getTemperatureProfile();
for (int i = 0; i < temperatureK.length; i++) {
    logger.info("x={} m; T={} C", pipeline.getLengthProfile().get(i),
        temperatureK[i] - 273.15);
}
logger.info("Outlet pressure: {} bara", pipeline.getOutletPressure("bara"));
```

Expect cooling from 100 °C toward the 10 °C surroundings and a positive
pressure drop. The chosen U-value is an example input, not a prediction of a
particular insulation system.

### Two-Phase with Interphase Heat Transfer

This complete flow-node example starts with an equilibrium methane/decane
mixture. Both phases initially have the same temperature, so the calculated
interphase heat flux should be close to zero. A finite transfer-rate study
requires non-equilibrium phase compositions or temperatures and a validated
spatial/time integration.

<!-- pipeline-doc-test: interphase-equilibrium -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

Logger logger = LogManager.getLogger("InterphaseHeatExample");
SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("n-decane", 0.2);
fluid.setMixingRule("classic");
fluid.setTotalFlowRate(1.0, "kg/sec");
new ThermodynamicOperations(fluid).TPflash();
fluid.initProperties();

PipeData geometry = new PipeData(0.1);
StratifiedFlowNode node = new StratifiedFlowNode(fluid, geometry);
node.initFlowCalc();
node.init();
node.getFluidBoundary().setHeatTransferCalc(true);
node.getFluidBoundary().setMassTransferCalc(true);
node.getFluidBoundary().useThermodynamicCorrections(true, 0);
node.getFluidBoundary().useThermodynamicCorrections(true, 1);
node.getFluidBoundary().useFiniteFluxCorrection(true, 0);
node.getFluidBoundary().useFiniteFluxCorrection(true, 1);
node.getFluidBoundary().solve();
double gasHeatFlux = node.getFluidBoundary().getInterphaseHeatFlux(0);
double liquidHeatFlux = node.getFluidBoundary().getInterphaseHeatFlux(1);
logger.info("Gas-side: {} W/m2; liquid-side: {} W/m2", gasHeatFlux, liquidHeatFlux);
```

### Condensation in Pipeline

The following equilibrium example checks whether cooling produces a liquid
phase. Thermodynamic phase fraction and hydrodynamic holdup are different
quantities: use outlet fluid phase volumes for equilibrium partitioning, and
the pipeline's holdup profile for in-situ liquid inventory. This model does
not estimate finite-rate nucleation or condensation kinetics.

<!-- pipeline-doc-test: condensation -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

Logger logger = LogManager.getLogger("CondensationExample");
SystemSrkEos gas = new SystemSrkEos(320.0, 80.0);
gas.addComponent("methane", 0.80);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.addComponent("n-butane", 0.03);
gas.addComponent("n-pentane", 0.02);
gas.setMixingRule("classic");
Stream inlet = new Stream("Warm Gas", gas);
inlet.setFlowRate(5000.0, "kg/hr");
inlet.run();

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Cold Flowline", inlet);
pipeline.setLength(5000.0);
pipeline.setDiameter(0.20);
pipeline.setNumberOfIncrements(20);
pipeline.setConstantSurfaceTemperature(4.0, "C");
pipeline.setHeatTransferCoefficient(25.0);
pipeline.run();

logger.info("Inlet phases: {}; outlet phases: {}", inlet.getFluid().getNumberOfPhases(),
    pipeline.getOutletStream().getFluid().getNumberOfPhases());
double[] holdup = pipeline.getLiquidHoldupProfile();
logger.info("Outlet: {} C; liquid holdup: {}", pipeline.getOutletTemperature("C"),
    holdup[holdup.length - 1]);
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

- [Mass Transfer Modeling](mass_transfer) - Companion mass transfer documentation
- [Fluid Mechanics Overview](./) - Main fluid mechanics documentation
- [Physical Properties](../physical_properties/) - Thermal conductivity models
- [Pipeline Simulation](../wiki/pipeline_transient_simulation) - Transient pipeline modeling
