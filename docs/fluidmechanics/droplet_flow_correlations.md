---
title: "Droplet and Bubble Flow Mass/Heat Transfer Correlations"
description: "Detailed documentation of the InterphaseDropletFlow class implementing Ranz-Marshall, Kronig-Brink, and Abramzon-Sirignano correlations for dispersed two-phase pipe flow in NeqSim."
---

# Droplet and Bubble Flow Mass/Heat Transfer Correlations

## Overview

The `InterphaseDropletFlow` class provides interphase transport coefficient correlations for **dispersed flow regimes** — droplet (mist) flow and bubble flow — where one phase exists as discrete particles in a continuous carrier phase.

**Package:** `neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasepipeflow`

**Key difference from stratified flow:** In dispersed flow, the characteristic length scale for mass and heat transfer is the **particle diameter** (droplet or bubble), not the pipe hydraulic diameter. This gives fundamentally different transfer rates.

**Related Documentation:**
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer) — Full theory for interphase mass and heat transfer
- [MassTransferAPI.md](MassTransferAPI) — API reference for mass transfer methods
- [mass_transfer.md](mass_transfer) — Diffusivity correlations
- [TwoPhasePipeFlowModel.md](TwoPhasePipeFlowModel) — Two-phase flow governing equations

---

## Flow Regime Mapping

| Flow Regime | Continuous Phase | Dispersed Phase | NeqSim Node Class |
|-------------|-----------------|-----------------|-------------------|
| Droplet / Mist | Gas (phase 0) | Liquid (phase 1) | `DropletFlowNode` |
| Bubble | Liquid (phase 1) | Gas (phase 0) | `BubbleFlowNode` |

---

## 1. Continuous Phase: Ranz-Marshall Correlation

For mass and heat transfer from the continuous phase to the particle surface, the **Ranz-Marshall** correlation is used:

### Mass Transfer (Sherwood Number)

$$Sh = 2 + 0.6 \cdot Re_p^{0.5} \cdot Sc^{0.33}$$

### Heat Transfer (Nusselt Number)

$$Nu = 2 + 0.6 \cdot Re_p^{0.5} \cdot Pr^{0.33}$$

Where:
- $Re_p = \frac{u_{rel} \cdot d_p}{\nu_c}$ is the **particle Reynolds number**
- $u_{rel} = |u_c - u_d|$ is the relative velocity between continuous and dispersed phases
- $d_p$ is the particle diameter (droplet or bubble)
- $\nu_c$ is the kinematic viscosity of the continuous phase
- $Sc = \nu / D_{ij}$ is the Schmidt number
- $Pr = \nu / \alpha$ is the Prandtl number

### Mass Transfer Coefficient

$$k_c = \frac{Sh \cdot D_{ij}}{d_p}$$

Where $D_{ij} = \nu / Sc$ is the binary diffusivity.

### Heat Transfer Coefficient

$$h = \frac{Nu \cdot \lambda}{d_p}$$

Where $\lambda$ is the thermal conductivity.

### Physical Limits

- At $Re_p \to 0$ (stagnant particle): $Sh \to 2$, giving $k_c = 2 D_{ij} / d_p$
- At $Re_p \to \infty$: convection dominates and $Sh \propto Re_p^{0.5}$

**Reference:** Ranz, W.E. and Marshall, W.R. (1952). "Evaporation from Drops." Chemical Engineering Progress, 48(3), 141-146.

---

## 2. Dispersed Phase: Kronig-Brink Model

For transport **inside** the particle (e.g., mass transfer within a droplet or bubble with internal circulation), the **Kronig-Brink** steady-state solution is used:

$$Sh_{KB} = 17.66$$

$$Nu_{KB} = 17.66$$

This gives:

$$k_d = \frac{17.66 \cdot D_{ij}}{d_p}$$

$$h_d = \frac{17.66 \cdot \lambda_d}{d_p}$$

The Kronig-Brink value (17.66) represents the asymptotic Sherwood/Nusselt number for a sphere with complete internal circulation (Hill's vortex inside the particle). It is significantly higher than the pure diffusion limit ($Sh = 6.58$) because internal recirculation enhances transport.

**Reference:** Kronig, R. and Brink, J.C. (1951). "On the Theory of Extraction from Falling Droplets." Applied Scientific Research, A2, 142-154.

---

## 3. Abramzon-Sirignano Extended Film Model (Optional)

For **evaporating droplets** with significant mass transfer rates, the standard Ranz-Marshall correlation underestimates the film thickness because it does not account for **Stefan flow** (outward radial convection caused by evaporation, also called "blowing").

The Abramzon-Sirignano (1989) model corrects the Sherwood number:

$$Sh^* = 2 + \frac{Sh_0 - 2}{F(B_M)}$$

Where $Sh_0$ is the standard Ranz-Marshall Sherwood number and $F(B_M)$ is the film correction function:

$$F(B_M) = (1 + B_M)^{0.7} \cdot \frac{\ln(1 + B_M)}{B_M}$$

### Spalding Mass Transfer Number

$$B_M = \frac{Y_s - Y_\infty}{1 - Y_s}$$

Where:
- $Y_s$ = vapor mass fraction at the droplet surface (saturation)
- $Y_\infty$ = vapor mass fraction in the far-field (bulk gas)

### Behavior

| $B_M$ | $F(B_M)$ | Effect |
|-------|----------|--------|
| $\to 0$ | $\to 1$ | No blowing — recovers standard Ranz-Marshall |
| $= 1$ | $\approx 1.125$ | Moderate evaporation — $Sh^*$ reduced by ~10% |
| $\gg 1$ | $\gg 1$ | Vigorous evaporation — significant $Sh^*$ reduction |

**Physical interpretation:** Evaporation creates an outward flow of vapor at the droplet surface. This "blowing" effectively thickens the boundary layer, reducing the rate at which fresh gas reaches the surface. The correction factor $F(B_M) > 1$ increases the effective film thickness, which lowers $Sh^*$ relative to $Sh_0$.

**Reference:** Abramzon, B. and Sirignano, W.A. (1989). "Droplet Vaporization Model for Spray Combustion Calculations." International Journal of Heat and Mass Transfer, 32(9), 1605-1618.

---

## 4. Particle Diameter

The characteristic particle diameter is obtained from the flow node:

| Node Type | Method | Typical Range |
|-----------|--------|---------------|
| `DropletFlowNode` | `getAverageDropletDiameter()` | 10-500 $\mu$m |
| `BubbleFlowNode` | `getAverageBubbleDiameter()` | 1-10 mm |

If the node type is not recognized, a default of 100 $\mu$m is used.

---

## 5. API Usage

### Basic Usage

```java
// Create a droplet flow node
DropletFlowNode node = new DropletFlowNode(fluid, pipeData);
node.setAverageDropletDiameter(100.0e-6); // 100 micron droplets
node.initFlowCalc();

// Create the interphase transport calculator
InterphaseDropletFlow interphase = new InterphaseDropletFlow(node);

// Gas-side (continuous phase) mass transfer coefficient
double schmidtNumber = 0.7; // typical for gas
double kc_gas = interphase.calcInterphaseMassTransferCoefficient(0, schmidtNumber, node);

// Liquid-side (dispersed phase) mass transfer coefficient
double kc_liq = interphase.calcInterphaseMassTransferCoefficient(1, 1000.0, node);
```

### With Abramzon-Sirignano Correction

```java
interphase.setUseAbramzonSirignano(true);
interphase.setSpaldingMassTransferNumber(2.0); // vigorous evaporation

double kc_corrected = interphase.calcInterphaseMassTransferCoefficient(0, 0.7, node);
// kc_corrected < kc_standard due to blowing correction
```

### Bubble Flow

```java
BubbleFlowNode bubbleNode = new BubbleFlowNode(fluid, pipeData);
bubbleNode.setAverageBubbleDiameter(5.0e-3); // 5 mm bubbles
bubbleNode.initFlowCalc();

InterphaseDropletFlow interphase = new InterphaseDropletFlow(bubbleNode);

// Liquid-side is now continuous (phase 1)
double kc_liq_cont = interphase.calcInterphaseMassTransferCoefficient(1, 500.0, bubbleNode);
// Gas-side is now dispersed (phase 0)
double kc_gas_disp = interphase.calcInterphaseMassTransferCoefficient(0, 0.7, bubbleNode);
```

---

## 6. Comparison with Stratified Flow

The key difference between `InterphaseDropletFlow` and `InterphaseStratifiedFlow`:

| Aspect | Stratified Flow | Droplet/Bubble Flow |
|--------|----------------|-------------------|
| Characteristic length | Pipe hydraulic diameter $D_h$ | Particle diameter $d_p$ |
| Sh correlation | Yih-Chen or Dittus-Boelter | Ranz-Marshall |
| Reynolds number | Based on pipe geometry | Based on particle size and relative velocity |
| Dispersed phase | N/A | Kronig-Brink ($Sh = 17.66$) |
| Blowing correction | N/A | Abramzon-Sirignano (optional) |
| Typical $k_c$ range | $10^{-4}$ to $10^{-2}$ m/s | $10^{-3}$ to $1$ m/s |

Since $d_p \ll D_h$, dispersed flow generally has **much higher** mass transfer coefficients per unit area, but the total interfacial area depends on the number and size of particles.

---

## 7. References

1. Ranz, W.E. and Marshall, W.R. (1952). "Evaporation from Drops, Part I and II." Chemical Engineering Progress, 48(3), 141-146 and 48(4), 173-180.

2. Kronig, R. and Brink, J.C. (1951). "On the Theory of Extraction from Falling Droplets." Applied Scientific Research, A2, 142-154.

3. Abramzon, B. and Sirignano, W.A. (1989). "Droplet Vaporization Model for Spray Combustion Calculations." International Journal of Heat and Mass Transfer, 32(9), 1605-1618.

4. Clift, R., Grace, J.R., and Weber, M.E. (1978). *Bubbles, Drops, and Particles*. Academic Press.

5. Solbraa, E. (2002). *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.* PhD Thesis, NTNU.
