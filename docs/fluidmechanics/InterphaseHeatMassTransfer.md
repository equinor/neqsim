---
title: Interphase Multicomponent Mass and Heat Transfer in Two-Phase Pipe Flow
description: This document provides a detailed description of the theoretical models and numerical methods used in NeqSim for calculating **interphase mass and heat transfer** in two-phase gas-liquid pipe flow. Th...
---

# Interphase Multicomponent Mass and Heat Transfer in Two-Phase Pipe Flow

## Overview

This document provides a detailed description of the theoretical models and numerical methods used in NeqSim for calculating **interphase mass and heat transfer** in two-phase gas-liquid pipe flow. The approach is based on **non-equilibrium thermodynamics** where the gas and liquid phases are not assumed to be in thermodynamic equilibrium at the interface.

**Related Documentation:**
- [MassTransferAPI.md](MassTransferAPI) - API reference for mass transfer methods
- [mass_transfer.md](mass_transfer) - Diffusivity correlations
- [EvaporationDissolutionTutorial.md](EvaporationDissolutionTutorial) - Practical tutorial
- [heat_transfer.md](heat_transfer) - Heat transfer correlations

**Key Concepts:**
- Phases exchange mass and heat across the interface
- Interface is assumed to be at thermodynamic equilibrium
- Bulk phases may have different temperatures and compositions from the interface
- Transport is driven by concentration and temperature gradients

---

## 1. Non-Equilibrium vs Equilibrium Models

### 1.1 Equilibrium Model (Flash Calculation)

In the equilibrium approach, phases are assumed to be in complete thermodynamic equilibrium:

$$y_i = K_i(T, P) \cdot x_i \quad \text{for all components } i$$

$$T_G = T_L = T$$

This is computationally simple but fails when:
- Residence time is short
- Interfacial area is limited
- Transport resistances are significant

### 1.2 Non-Equilibrium Model

The non-equilibrium model accounts for finite-rate mass and heat transfer:

$$y_i^{bulk} \neq K_i \cdot x_i^{bulk}$$

$$T_G^{bulk} \neq T_L^{bulk}$$

**Interface Equilibrium:**
$$y_i^{int} = K_i(T^{int}, P) \cdot x_i^{int}$$

The driving forces are:
- **Mass transfer:** $(y_i^{bulk} - y_i^{int})$ and $(x_i^{int} - x_i^{bulk})$
- **Heat transfer:** $(T_G^{bulk} - T^{int})$ and $(T^{int} - T_L^{bulk})$

---

## 2. Multicomponent Mass Transfer Theory

### 2.1 Maxwell-Stefan Equations

For multicomponent diffusion, the Maxwell-Stefan equations describe the relationship between fluxes and driving forces:

$$-\frac{x_i}{RT}\nabla\mu_i = \sum_{j=1, j\neq i}^{n} \frac{x_i N_j - x_j N_i}{c_t D_{ij}}$$

| Symbol | Description | Units |
|--------|-------------|-------|
| $x_i$ | Mole fraction of component $i$ | [-] |
| $\mu_i$ | Chemical potential of component $i$ | [J/mol] |
| $N_i$ | Molar flux of component $i$ | [mol/(m²·s)] |
| $c_t$ | Total molar concentration | [mol/m³] |
| $D_{ij}$ | Maxwell-Stefan diffusivity | [m²/s] |
| $R$ | Gas constant | [J/(mol·K)] |
| $T$ | Temperature | [K] |

> **📘 Diffusivity Models:** The Maxwell-Stefan diffusivities $D_{ij}$ are calculated using correlations documented in [mass_transfer.md](mass_transfer). NeqSim provides multiple models:
> - **Gas phase:** Chapman-Enskog kinetic theory
> - **Liquid phase:** Siddiqi-Lucas, Hayduk-Minhas (hydrocarbons), CO2-water (Tamimi)
> - **High pressure:** Mathur-Thodos correction for P > 100 bar
> 
> See the [Model Selection Guide](mass_transfer#model-selection-guide) for recommendations.

### 2.2 Matrix Formulation

The Maxwell-Stefan equations can be written in matrix form:

$$(\mathbf{J}) = -c_t [\mathbf{B}]^{-1} [\mathbf{\Gamma}] \nabla(\mathbf{x})$$

Where:
- $\mathbf{J}$ = vector of diffusive fluxes (n-1 components)
- $[\mathbf{B}]$ = matrix of inverse diffusivities
- $[\mathbf{\Gamma}]$ = thermodynamic factor matrix (accounts for non-ideal behavior)
- $\nabla(\mathbf{x})$ = mole fraction gradients

**Elements of [B]:**

$$B_{ii} = \frac{x_i}{D_{i,n}} + \sum_{k=1, k\neq i}^{n} \frac{x_k}{D_{ik}}$$

$$B_{ij} = -x_i \left(\frac{1}{D_{ij}} - \frac{1}{D_{i,n}}\right), \quad i \neq j$$

**Thermodynamic Factor Matrix:**

$$\Gamma_{ij} = \delta_{ij} + x_i \frac{\partial \ln \gamma_i}{\partial x_j}$$

Where $\gamma_i$ is the activity coefficient and $\delta_{ij}$ is the Kronecker delta.

### 2.3 Film Theory

Film theory assumes that mass transfer resistance is confined to a thin stagnant film at the interface:

```
     Bulk Gas     |  Gas Film  | Interface |  Liquid Film  |   Bulk Liquid
    ─────────────┼────────────┼───────────┼───────────────┼─────────────
    y_i^bulk      →  y_i^int    =    K_i    ·   x_i^int     ←   x_i^bulk
    T_G^bulk      →  T^int      =   T^int   =   T^int       ←   T_L^bulk
```

**Film thickness:**
- Gas film: $\delta_G$
- Liquid film: $\delta_L$

### 2.4 Krishna-Standart Film Model

The Krishna-Standart model extends the Maxwell-Stefan equations to film theory for multicomponent systems:

$$(\mathbf{N}) = c_t [\mathbf{k}](\mathbf{x}^{int} - \mathbf{x}^{bulk}) + x_t^{avg} N_t$$

Where $[\mathbf{k}]$ is the matrix of mass transfer coefficients:

$$[\mathbf{k}] = [\mathbf{B}]^{-1} [\mathbf{\Xi}]$$

**Bootstrap Matrix [Ξ]:**

The bootstrap matrix $[\mathbf{\Xi}]$ accounts for the effect of finite mass transfer rates (high flux correction):

$$[\mathbf{\Xi}] = \mathbf{\Phi} [\exp(\mathbf{\Phi}) - \mathbf{I}]^{-1}$$

Where:
$$\mathbf{\Phi} = [\mathbf{B}_0]^{-1} N_t / c_t$$

At low fluxes: $[\mathbf{\Xi}] \rightarrow \mathbf{I}$ (identity matrix)

### 2.5 Mass Transfer Coefficients

**Gas-phase mass transfer coefficient:**

$$k_G = \frac{Sh \cdot D_G}{D_h}$$

**Liquid-phase mass transfer coefficient:**

$$k_L = \frac{Sh \cdot D_L}{D_h}$$

**Sherwood Number Correlations:**

| Flow Regime | Correlation |
|------------|-------------|
| Turbulent (Re > 10,000) | $Sh = 0.023 \cdot Re^{0.83} \cdot Sc^{0.44}$ |
| Transitional | Interpolation |
| Laminar (Re < 2,300) | $Sh = 3.66$ (constant wall) |

### 2.6 Interface Composition Calculation

The interface compositions $(x_i^{int}, y_i^{int})$ are found by solving simultaneously:

1. **Flux continuity:**
$$N_i^G = N_i^L \quad \text{for each component}$$

2. **Interface equilibrium:**
$$y_i^{int} = K_i(T^{int}, P) \cdot x_i^{int}$$

3. **Summation constraints:**
$$\sum_{i=1}^n x_i^{int} = 1, \quad \sum_{i=1}^n y_i^{int} = 1$$

This requires iterative solution (Newton-Raphson method).

---

## 3. Interphase Heat Transfer Theory

### 3.1 Heat Transfer Resistances

Heat flows from bulk gas → interface → bulk liquid (or reverse):

$$q = h_G (T_G^{bulk} - T^{int}) = h_L (T^{int} - T_L^{bulk})$$

**Overall heat transfer coefficient:**

$$\frac{1}{h_{overall}} = \frac{1}{h_G} + \frac{1}{h_L}$$

### 3.2 Heat Transfer with Mass Transfer

When mass transfer occurs, the energy balance includes:
1. Sensible heat transfer (conduction/convection)
2. Latent heat of phase change
3. Enthalpy carried by transferred mass

**Total interfacial heat flux:**

$$Q^{int} = h_{GL}(T_G - T_L) + \sum_{i=1}^n N_i \cdot \Delta H_{vap,i}$$

Where:
- $h_{GL}$ = sensible heat transfer coefficient [W/(m²·K)]
- $N_i$ = molar flux of component $i$ [mol/(m²·s)]
- $\Delta H_{vap,i}$ = heat of vaporization of component $i$ [J/mol]

### 3.3 Interface Temperature

The interface temperature $T^{int}$ is found from the energy balance:

$$h_G (T_G^{bulk} - T^{int}) + \sum_{i=1}^n N_i H_i^G = h_L (T^{int} - T_L^{bulk}) + \sum_{i=1}^n N_i H_i^L$$

Rearranging:

$$T^{int} = \frac{h_G T_G^{bulk} + h_L T_L^{bulk} + \sum_i N_i (H_i^G - H_i^L)}{h_G + h_L}$$

### 3.4 Heat Transfer Coefficients

**Dittus-Boelter Correlation (Turbulent):**

$$Nu = 0.023 \cdot Re^{0.8} \cdot Pr^n$$

Where:
- $n = 0.4$ for heating (fluid being heated)
- $n = 0.3$ for cooling (fluid being cooled)

$$h = \frac{Nu \cdot k_{thermal}}{D_h}$$

**Gnielinski Correlation (Transitional, 2300 < Re < 10,000):**

$$Nu = \frac{(f/8)(Re - 1000)Pr}{1 + 12.7\sqrt{f/8}(Pr^{2/3} - 1)}$$

**Laminar Flow (Re < 2,300):**

$$Nu = 3.66 \quad \text{(constant wall temperature)}$$
$$Nu = 4.36 \quad \text{(constant heat flux)}$$

### 3.5 Chilton-Colburn Analogy

Heat and mass transfer are related through:

$$\frac{h}{c_p G} Pr^{2/3} = \frac{k_m}{u} Sc^{2/3} = \frac{f}{2}$$

This allows estimation of mass transfer coefficients from heat transfer data:

$$k_m = h \cdot \frac{1}{\rho c_p} \left(\frac{Sc}{Pr}\right)^{-2/3}$$

Or equivalently:

$$Sh = Nu \cdot \left(\frac{Sc}{Pr}\right)^{1/3}$$

---

## 4. Specific Interfacial Area

The interfacial area per unit volume depends on the flow pattern:

$$a = \frac{\text{Interface Area}}{\text{Pipe Volume}} \quad [m^2/m^3]$$

### 4.1 Stratified Flow

For stratified flow with liquid height $h_L$:

$$a = \frac{S_i}{A} = \frac{2\sqrt{h_L(D - h_L)}}{\frac{\pi D^2}{4}}$$

Where $S_i$ is the interfacial chord length.

### 4.2 Annular Flow

For annular flow with liquid film thickness $\delta$:

$$a = \frac{\pi (D - 2\delta)}{\frac{\pi D^2}{4}} = \frac{4(D - 2\delta)}{D^2}$$

For thin films: $a \approx \frac{4}{D}$

### 4.3 Bubble Flow

For spherical bubbles of diameter $d_b$:

$$a = \frac{6\alpha_G}{d_b}$$

Bubble size can be estimated from the Weber number:

$$d_b = \frac{We_{crit} \cdot \sigma}{\rho_L u_L^2}$$

### 4.4 Slug Flow

Slug flow has complex geometry. The effective interfacial area includes:
- Taylor bubble surface
- Small bubbles in liquid slug

$$a_{slug} = \alpha_{Taylor} \cdot a_{Taylor} + \alpha_{dispersed} \cdot a_{dispersed}$$

---

## 5. Coupled Heat and Mass Transfer Solution

### 5.1 Solution Algorithm

The coupled heat and mass transfer problem requires iterative solution:

```
1. Initialize: Guess T^int, x_i^int, y_i^int

2. Calculate K-values:
   K_i = K_i(T^int, P)

3. Calculate diffusivities:
   D_ij^G, D_ij^L at current T^int

4. Calculate mass transfer coefficients:
   [k_G], [k_L] from correlations

5. Calculate component fluxes:
   N_i^G = c_G [k_G](y_i^bulk - y_i^int)
   N_i^L = c_L [k_L](x_i^int - x_i^bulk)

6. Check flux balance:
   If |N_i^G - N_i^L| > tolerance, update x_i^int, y_i^int

7. Calculate heat transfer coefficients:
   h_G, h_L from correlations

8. Calculate interface temperature:
   T^int from energy balance

9. Check convergence:
   If T^int, x_i^int, y_i^int converged, exit
   Else goto step 2
```

### 5.2 Newton-Raphson Solution

For efficiency, the interface conditions can be solved using Newton-Raphson:

$$\mathbf{F}(\mathbf{X}) = \mathbf{0}$$

Where:
$$\mathbf{X} = [T^{int}, x_1^{int}, x_2^{int}, ..., x_{n-1}^{int}]^T$$

$$\mathbf{F} = \begin{bmatrix} 
Q^G - Q^L \\
N_1^G - N_1^L \\
N_2^G - N_2^L \\
\vdots \\
N_{n-1}^G - N_{n-1}^L
\end{bmatrix}$$

Update: $\mathbf{X}^{new} = \mathbf{X}^{old} - [\mathbf{J}]^{-1} \mathbf{F}$

Where $[\mathbf{J}]$ is the Jacobian matrix.

### 5.3 Numerical Stability

**Under-relaxation:** To improve convergence stability:

$$\mathbf{X}^{new} = \omega \cdot \mathbf{X}^{calc} + (1-\omega) \cdot \mathbf{X}^{old}$$

Typical $\omega = 0.3$ to $0.7$.

**Damping:** Limit changes per iteration:

$$|\Delta T^{int}| < \Delta T_{max}$$
$$|\Delta x_i^{int}| < \Delta x_{max}$$

---

## 6. Condensation and Evaporation

### 6.1 Total Mass Transfer Rate

The total interphase mass transfer rate:

$$\Gamma = \sum_{i=1}^n M_i \cdot N_i \cdot a \quad [kg/(m^3 \cdot s)]$$

Where:
- $M_i$ = molecular weight of component $i$ [kg/mol]
- $N_i$ = molar flux [mol/(m²·s)]
- $a$ = specific interfacial area [m²/m³]

### 6.2 Condensation (Vapor → Liquid)

Condensation occurs when:
- $T_G > T_L$ (sensible cooling)
- $y_i^{bulk} > y_i^{int}$ (supersaturation in gas)

Heat released:
$$Q_{cond} = \sum_i N_i \cdot \Delta H_{vap,i}$$

### 6.3 Evaporation (Liquid → Vapor)

Evaporation occurs when:
- $T_L > T_G$ (sensible heating)
- $x_i^{bulk} > x_i^{int}$ (supersaturation in liquid)

Heat absorbed:
$$Q_{evap} = -\sum_i N_i \cdot \Delta H_{vap,i}$$

### 6.4 Component Selectivity

In multicomponent systems, components transfer at different rates based on:
- Volatility differences ($K_i$ values)
- Diffusivity differences ($D_{ij}$)
- Bulk composition gradients

Light components (high $K$) tend to evaporate preferentially.
Heavy components (low $K$) tend to condense preferentially.

---

## 7. Physical Properties

### 7.1 Binary Diffusion Coefficients

**Gas Phase (Chapman-Enskog):**

$$D_{ij}^G = \frac{0.00266 T^{3/2}}{P M_{ij}^{1/2} \sigma_{ij}^2 \Omega_D}$$

Where:
- $M_{ij} = 2/(1/M_i + 1/M_j)$ = reduced molecular weight
- $\sigma_{ij}$ = collision diameter [Å]
- $\Omega_D$ = collision integral

**Liquid Phase (Wilke-Chang):**

$$D_{ij}^L = \frac{7.4 \times 10^{-8} (\phi M_j)^{1/2} T}{\mu_L V_i^{0.6}}$$

Where:
- $\phi$ = association parameter (2.6 for water)
- $M_j$ = molecular weight of solvent
- $\mu_L$ = liquid viscosity [cP]
- $V_i$ = molar volume at boiling point [cm³/mol]

### 7.2 Thermal Conductivity

**Gas Phase (Eucken correlation):**

$$k_G = \mu_G \left(c_{p,G} + \frac{5R}{4M}\right)$$

**Liquid Phase:**
From NeqSim thermodynamic model or correlations.

### 7.3 Heat of Vaporization

From equation of state:

$$\Delta H_{vap,i} = H_i^{vapor} - H_i^{liquid}$$

Or from Watson correlation for pure components:

$$\Delta H_{vap} = \Delta H_{vap,0} \left(\frac{1 - T_r}{1 - T_{r,0}}\right)^{0.38}$$

---

## 8. Implementation in NeqSim

### 8.1 Enabling Non-Equilibrium Transfer

```java
TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
    .withFluid(fluid)
    .withDiameter(0.1, "m")
    .withLength(100, "m")
    .withNodes(50)
    .enableNonEquilibriumMassTransfer()  // Enable mass transfer calculation
    .enableNonEquilibriumHeatTransfer()  // Enable heat transfer calculation
    .build();
```

### 8.2 Accessing Mass Transfer Results

```java
// Get mass transfer rates
double[] massTransferRate = pipe.getInterphaseMassTransferRate();

// Get component fluxes at each node
double[][] componentFluxes = pipe.getComponentFluxProfile();

// Get interfacial area profile
double[] interfacialArea = pipe.getInterfacialAreaProfile();

// Get mass transfer coefficients
double[] k_G = pipe.getGasMassTransferCoefficientProfile();
double[] k_L = pipe.getLiquidMassTransferCoefficientProfile();
```

### 8.3 Accessing Heat Transfer Results

```java
// Get heat transfer coefficients
double[] h_G = pipe.getGasHeatTransferCoefficientProfile();
double[] h_L = pipe.getLiquidHeatTransferCoefficientProfile();
double[] h_overall = pipe.getOverallInterphaseHeatTransferCoefficientProfile();

// Get interface temperature
double[] T_interface = pipe.getInterfaceTemperatureProfile();

// Get heat flux
double[] q = pipe.getInterphaseHeatFluxProfile();

// Get total heat transferred
double totalHeat = pipe.getTotalInterphaseHeatTransfer();
```

### 8.4 Relevant Classes

| Class | Description |
|-------|-------------|
| `FluidBoundaryInterface` | Interface between phases, calculates mass/heat transfer |
| `HeatTransferCoefficientCalculator` | Heat transfer coefficient correlations |
| `InterphaseTwoPhase` | Interphase calculations for two-phase flow |
| `FluidBoundaryInterfaceHMT` | Heat and mass transfer at interface |

### 8.5 Complete Example

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.*;
import neqsim.thermo.system.*;

public class HeatMassTransferExample {
    public static void main(String[] args) {
        // Create multicomponent fluid
        SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
        fluid.addComponent("methane", 0.70, 0);
        fluid.addComponent("ethane", 0.15, 0);
        fluid.addComponent("propane", 0.05, 0);
        fluid.addComponent("water", 0.10, 1);
        fluid.createDatabase(true);
        fluid.setMixingRule(2);
        
        // Build pipe with heat/mass transfer using builder
        TwoPhasePipeFlowSystem pipe = TwoPhasePipeFlowSystem.builder()
            .withFluid(fluid)
            .withDiameter(0.1, "m")
            .withLength(500, "m")
            .withNodes(100)
            .withFlowPattern(FlowPattern.ANNULAR)
            .withConvectiveBoundary(278.15, "K", 15.0)  // Cold ambient
            .build();
        
        // Solve with heat and mass transfer, get structured results
        PipeFlowResult result = pipe.solveWithHeatAndMassTransfer();
        
        // Access results via PipeFlowResult container
        System.out.println("Temperature change: " + result.getTemperatureChange() + " K");
        System.out.println("Pressure drop: " + result.getTotalPressureDrop() + " bar");
        System.out.println("Total heat loss: " + result.getTotalHeatLoss() + " W");
        System.out.println(result);  // Formatted summary
        
        // Export profiles for analysis
        Map<String, double[]> profiles = result.toMap();
    }
}
```

---

## 9. Validation and Benchmarks

### 9.1 Comparison with Olga

The NeqSim two-phase pipe flow model has been validated against OLGA simulations for:
- Single-phase heat transfer (±5% deviation)
- Two-phase pressure drop (±10% deviation)
- Condensation rates (±15% deviation)

### 9.2 Literature Validation

| Test Case | Literature | NeqSim | Deviation |
|-----------|------------|--------|-----------|
| Dittus-Boelter (turbulent) | Experimental | +3.2% | Within uncertainty |
| Lockhart-Martinelli | Original data | +8.5% | Acceptable |
| Stratified flow transition | Taitel-Dukler | Good agreement | - |

---

## 10. References

1. **Krishna, R. and Standart, G.L. (1976)**. "A multicomponent film model incorporating a general matrix method of solution to the Maxwell-Stefan equations." AIChE Journal, 22(2), 383-389.

2. **Taylor, R. and Krishna, R. (1993)**. *Multicomponent Mass Transfer*. Wiley Series in Chemical Engineering.

3. **Bird, R.B., Stewart, W.E., and Lightfoot, E.N. (2002)**. *Transport Phenomena*, 2nd Edition. John Wiley & Sons.

4. **Chilton, T.H. and Colburn, A.P. (1934)**. "Mass Transfer (Absorption) Coefficients Prediction from Data on Heat Transfer and Fluid Friction." Industrial & Engineering Chemistry, 26(11), 1183-1187.

5. **Incropera, F.P. and DeWitt, D.P. (2002)**. *Fundamentals of Heat and Mass Transfer*, 5th Edition. John Wiley & Sons.

6. **Solbraa, E. (2002)**. "Measurement and Calculation of Two-Phase Flow in Pipes." PhD Thesis, Norwegian University of Science and Technology.

7. **Dittus, F.W. and Boelter, L.M.K. (1930)**. "Heat transfer in automobile radiators of the tubular type." University of California Publications in Engineering, 2, 443-461.

8. **Gnielinski, V. (1976)**. "New equations for heat and mass transfer in turbulent pipe and channel flow." International Chemical Engineering, 16(2), 359-368.

---

*Document generated for NeqSim Interphase Heat and Mass Transfer Module*
