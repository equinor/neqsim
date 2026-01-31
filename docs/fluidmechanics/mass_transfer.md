# Mass Transfer Modeling in NeqSim

This document provides detailed documentation of the mass transfer models implemented in the NeqSim fluid mechanics package, focusing on diffusivity correlations and reactive mass transfer.

**Related Documentation:**
- [MassTransferAPI.md](MassTransferAPI.md) - API reference for interfacial area and kL/kG methods
- [InterphaseHeatMassTransfer.md](InterphaseHeatMassTransfer.md) - Theory for interphase transfer
- [EvaporationDissolutionTutorial.md](EvaporationDissolutionTutorial.md) - Practical tutorial

## Table of Contents
- [Overview](#overview)
- [Theoretical Background](#theoretical-background)
- [Single-Phase Mass Transfer](#single-phase-mass-transfer)
- [Two-Phase Mass Transfer](#two-phase-mass-transfer)
- [Multicomponent Mass Transfer](#multicomponent-mass-transfer)
- [Reactive Mass Transfer](#reactive-mass-transfer)
- [Implementation Classes](#implementation-classes)
- [Usage Examples](#usage-examples)
- [References](#references)

---

## Overview

NeqSim implements rigorous multicomponent mass transfer models based on non-equilibrium thermodynamics. The models are suitable for:

- Gas-liquid absorption and stripping
- Condensation and evaporation in pipelines
- Distillation and packed column simulation
- High-pressure natural gas processing

The theoretical foundation is described in:

> **Solbraa, E. (2002).** *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.* 
> Dr.ing. thesis, NTNU. [Available at NVA](https://hdl.handle.net/11250/231326)

---

## Theoretical Background

### Fick's Law vs Maxwell-Stefan

For **binary** systems, Fick's law is adequate:

$$J_A = -D_{AB} \cdot c_t \cdot \nabla x_A$$

For **multicomponent** systems, NeqSim uses the **Maxwell-Stefan equations**:

$$-\frac{c_t}{RT} \nabla \mu_i = \sum_{j=1, j \neq i}^{n} \frac{x_i N_j - x_j N_i}{c_t D_{ij}}$$

Where:
- $c_t$ = total molar concentration (mol/m³)
- $\mu_i$ = chemical potential of component i
- $x_i$ = mole fraction of component i
- $N_i$ = molar flux of component i (mol/m²·s)
- $D_{ij}$ = Maxwell-Stefan diffusion coefficient (m²/s)

### Film Theory

The film theory assumes mass transfer occurs across a stagnant film of thickness $\delta$:

$$N_i = k_i \cdot c_t \cdot (x_{i,bulk} - x_{i,interface})$$

Where $k_i = D_i / \delta$ is the mass transfer coefficient.

### Penetration Theory

For unsteady-state mass transfer (short contact times):

$$k_L = 2 \sqrt{\frac{D}{\pi t_c}}$$

Where $t_c$ is the contact time.

---

## Single-Phase Mass Transfer

### Wall Mass Transfer

Mass transfer from a flowing fluid to a solid wall (pipe wall, packing surface):

#### Dimensionless Numbers

| Number | Definition | Physical Meaning |
|--------|------------|------------------|
| Sherwood (Sh) | $k_c \cdot d / D$ | Ratio of convective to diffusive mass transfer |
| Schmidt (Sc) | $\nu / D$ | Ratio of momentum to mass diffusivity |
| Reynolds (Re) | $\rho v d / \mu$ | Ratio of inertial to viscous forces |

#### Correlations

**Laminar Flow (Re < 2300):**
$$Sh = 3.66$$

**Turbulent Flow (Re > 10000):**
$$Sh = 0.023 \cdot Re^{0.83} \cdot Sc^{0.33}$$

**Transition Region (2300 < Re < 4000):**
Linear interpolation between laminar and turbulent values.

### Diffusion Coefficients

#### Gas Phase

Chapman-Enskog theory for binary diffusion:

$$D_{AB} = \frac{0.00266 \cdot T^{3/2}}{P \cdot M_{AB}^{1/2} \cdot \sigma_{AB}^2 \cdot \Omega_D}$$

Where:
- $T$ = temperature (K)
- $P$ = pressure (bar)
- $M_{AB}$ = reduced molecular weight
- $\sigma_{AB}$ = collision diameter (Å)
- $\Omega_D$ = collision integral

#### Liquid Phase

NeqSim provides multiple liquid-phase diffusivity models, each optimized for different applications:

##### 1. Wilke-Chang Correlation (Default)

$$D_{AB} = 7.4 \times 10^{-8} \cdot \frac{(\phi M_B)^{0.5} \cdot T}{\mu_B \cdot V_A^{0.6}}$$

Where:
- $\phi$ = association factor (2.6 for water, 1.0 for non-associated)
- $M_B$ = molecular weight of solvent
- $\mu_B$ = viscosity of solvent (cP)
- $V_A$ = molar volume of solute at boiling point (cm³/mol)

##### 2. Siddiqi-Lucas Method

Uses group contribution based on molecular weight and solvent viscosity:

- **Aqueous systems**: $D_{AB} = 2.98 \times 10^{-7} \cdot V_A^{-0.5473} \cdot \mu_B^{-1.026}$
- **Non-aqueous systems**: $D_{AB} = 9.89 \times 10^{-8} \cdot V_A^{-0.791} \cdot \mu_B^{-0.907}$

**Best for**: General aqueous and organic liquid systems at low to moderate pressures.

##### 3. Hayduk-Minhas Method

Optimized for hydrocarbon systems (Hayduk & Minhas, 1982):

- **Paraffin solvents**: $D_{AB} = 13.3 \times 10^{-8} \cdot \frac{T^{1.47} \cdot \mu_B^{(\epsilon_B)}}{V_A^{0.71}}$
  - Where: $\epsilon_B = \frac{10.2}{V_A} - 0.791$
  
- **Aqueous solvents**: $D_{AB} = 1.25 \times 10^{-8} \cdot (V_A^{-0.19} - 0.292) \cdot T^{1.52} \cdot \mu_B^{\epsilon}$
  - Where: $\epsilon = \frac{9.58}{V_A} - 1.12$

**Best for**: Hydrocarbon-hydrocarbon diffusion in oil/gas applications.

```java
// Example: Using Hayduk-Minhas for oil system
PhysicalProperties physProps = system.getPhase(1).getPhysicalProperties();
Diffusivity diffModel = new HaydukMinhasDiffusivity(physProps);
diffModel.calcDiffusionCoefficients(0, 0);  // binaryMethod, multicomponentMethod
double Dij = diffModel.getMaxwellStefanBinaryDiffusionCoefficient(0, 1);
```

##### 4. CO2-Water (Tamimi Correlation)

Specialized for CO2 diffusion in water, validated against experimental data:

$$D_{CO_2} = 2.35 \times 10^{-6} \cdot \exp\left(\frac{-2119}{T}\right)$$

**Best for**: Carbon capture applications, CO2 absorption/desorption studies.

##### 5. High-Pressure Correction

For reservoir and deep-water conditions (>100 bar), apply Mathur-Thodos correction:

$$D_P = D_0 \cdot f(\rho_r)$$

The correction factor accounts for increased molecular crowding at high pressures and can reduce diffusivity by 10× at 400 bar.

```java
// Example: High-pressure diffusivity
PhysicalProperties physProps = system.getPhase(1).getPhysicalProperties();
HighPressureDiffusivity hpModel = new HighPressureDiffusivity(physProps);
hpModel.calcDiffusionCoefficients(0, 0);  // applies HP correction automatically
double correctionFactor = hpModel.getPressureCorrectionFactor();
```

### Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| General aqueous | Siddiqi-Lucas (aqueous) | Well-validated for dilute solutions |
| General organic | Siddiqi-Lucas (non-aqueous) | Good for organic solvents |
| Oil/gas hydrocarbons | Hayduk-Minhas (paraffin) | 2-3× higher than Siddiqi-Lucas, physically appropriate for oils |
| CO2 in water | CO2-water (Tamimi) | Best accuracy (±11% of literature) |
| Reservoir conditions | High-pressure + Hayduk-Minhas | Critical for P > 100 bar |

### Model Comparison Results

Based on validation testing at 300 K, 1 atm for CO2 in water (literature: 1.9×10⁻⁹ m²/s):

| Model | Predicted (m²/s) | Error |
|-------|------------------|-------|
| Hayduk-Minhas (aqueous) | 1.71×10⁻⁹ | -10% |
| CO2-water (Tamimi) | 2.12×10⁻⁹ | +11% |
| Siddiqi-Lucas | 1.39×10⁻⁹ | -27% |

For hydrocarbon systems, Hayduk-Minhas produces values 2-3.5× higher than Siddiqi-Lucas, which is consistent with the different physical basis of the correlations.

### Automatic Model Selection

The `DiffusivityModelSelector` class can automatically choose the optimal model:

```java
// Automatic model selection based on composition and conditions
PhaseInterface phase = system.getPhase(1);
PhysicalProperties physProps = phase.getPhysicalProperties();
DiffusivityModelSelector.DiffusivityModelType modelType = 
    DiffusivityModelSelector.selectOptimalModel(phase);
Diffusivity model = DiffusivityModelSelector.createModel(physProps, modelType);

// Or use auto-selection directly:
Diffusivity autoModel = DiffusivityModelSelector.createAutoSelectedModel(physProps);
```

Selection criteria:
- Detects CO2-water systems → uses CO2-water model
- Detects predominantly hydrocarbon → uses Hayduk-Minhas
- Falls back to Siddiqi-Lucas for other systems
- Applies high-pressure correction when P > 100 bar

### Future Development Possibilities

1. **Concentration-dependent diffusivity (Vignes mixing rule)**:
   $$D_{AB,mix} = D_{AB}^{x_B} \cdot D_{BA}^{x_A}$$
   Currently implemented but may cause numerical issues with very different diffusivities.

2. **Binary interaction parameters**: Allow user-tuning for specific component pairs.

3. **Additional correlations**:
   - Tyn-Calus for associated liquids
   - Scheibel for high-viscosity systems
   - He-Yu for supercritical fluids

4. **Temperature extrapolation warnings**: Alert users when operating outside correlation validity ranges (typically 273-400 K).

---

## Two-Phase Mass Transfer

### Interphase Mass Transfer

At the gas-liquid interface, mass transfer occurs from both sides:

```
    Gas Bulk     |  Interface  |    Liquid Bulk
                 |             |
    x_i,G,bulk --|-- x_i,I ----|-- x_i,L,bulk
                 |             |
      k_G        |  Equilibrium|      k_L
                 |   K_i       |
```

### Two-Resistance Model

The overall mass transfer coefficient combines gas and liquid resistances:

$$\frac{1}{K_{OG}} = \frac{1}{k_G} + \frac{m}{k_L}$$

$$\frac{1}{K_{OL}} = \frac{1}{k_L} + \frac{1}{m \cdot k_G}$$

Where $m = dy/dx$ is the slope of the equilibrium line.

### Flow Regime Dependence

The mass transfer coefficients depend strongly on the flow regime:

| Flow Regime | Interfacial Area | Gas-side $k_G$ | Liquid-side $k_L$ |
|-------------|------------------|----------------|-------------------|
| **Stratified** | $A_i = W \cdot L$ (flat interface) | Smooth surface correlation | Penetration theory |
| **Annular** | $A_i = \pi d L$ (film on wall) | Core flow correlation | Film flow correlation |
| **Droplet/Mist** | $A_i = 6\epsilon/d_p$ (droplet surface) | External mass transfer | Internal circulation |
| **Bubble** | $A_i = 6\epsilon/d_b$ (bubble surface) | External mass transfer | Higbie penetration |
| **Slug** | Combined film + slug | Varies with position | Varies with position |

### Stratified Flow

For stratified gas-liquid flow in pipes:

**Gas-side (smooth interface):**
$$Sh_G = 0.023 \cdot Re_G^{0.83} \cdot Sc_G^{0.33}$$

**Liquid-side (penetration theory):**
$$k_L = 2 \sqrt{\frac{D_L \cdot v_L}{\pi \cdot L}}$$

### Annular Flow

For annular flow with liquid film:

**Gas core:**
$$Sh_G = 0.023 \cdot Re_G^{0.8} \cdot Sc_G^{0.33} \cdot \left(1 + 0.1 \cdot (d/\delta)^{0.5}\right)$$

**Liquid film:**
$$k_L = \frac{D_L}{\delta} \cdot f(Re_{film})$$

---

## Multicomponent Mass Transfer

### Krishna-Standart Model

NeqSim implements the Krishna-Standart multicomponent mass transfer model. For a system with $n$ components:

#### Binary Mass Transfer Coefficients

First, calculate binary coefficients from correlations:

```java
for (int i = 0; i < nComponents; i++) {
    for (int j = 0; j < nComponents; j++) {
        // Schmidt number
        Sc[i][j] = kinematicViscosity / D[i][j];
        
        // Binary mass transfer coefficient
        k_binary[i][j] = calcInterphaseMassTransferCoefficient(phase, Sc[i][j], node);
    }
}
```

#### Mass Transfer Coefficient Matrix

Build the $(n-1) \times (n-1)$ matrix $[\mathbf{k}]$:

$$k_{ii} = \sum_{j \neq i} \frac{x_j}{k_{ij}} + \frac{x_i}{k_{in}}$$

$$k_{ij} = -x_i \left(\frac{1}{k_{ij}} - \frac{1}{k_{in}}\right) \quad (i \neq j)$$

Where component $n$ is the reference (typically the most abundant).

#### Flux Calculation

The molar flux vector:

$$\mathbf{N} = c_t [\mathbf{k}]^{-1} (\mathbf{x}_{bulk} - \mathbf{x}_{interface})$$

### Corrections

#### Thermodynamic Correction

For non-ideal solutions, the driving force includes activity coefficient gradients:

$$[\Gamma] = [\delta_{ij} + x_i \frac{\partial \ln \gamma_i}{\partial x_j}]$$

The corrected flux:
$$\mathbf{N} = c_t [\mathbf{k}][\Gamma]^{-1} (\mathbf{x}_{bulk} - \mathbf{x}_{interface})$$

#### Finite Flux Correction (Stefan Flow)

For high mass transfer rates, the film theory correction:

$$[\Xi] = [\Phi](e^{[\Phi]} - I)^{-1}$$

Where $[\Phi]$ is the rate factor matrix.

---

## Reactive Mass Transfer

### Enhancement Factor

Chemical reactions in the liquid phase enhance mass transfer:

$$N_A = E \cdot k_L \cdot (C_{A,i} - C_{A,bulk})$$

Where $E \geq 1$ is the enhancement factor.

### Hatta Number

The Hatta number characterizes the reaction regime:

$$Ha = \frac{\sqrt{k_{rxn} \cdot D_A}}{k_L}$$

| Ha Range | Regime | Location of Reaction |
|----------|--------|---------------------|
| Ha < 0.3 | Slow | Bulk liquid |
| 0.3 < Ha < 3 | Intermediate | Film and bulk |
| Ha > 3 | Fast | Within film |
| Ha >> 3 | Instantaneous | At interface |

### Enhancement Factor Models

#### Pseudo-First Order (Fast Reaction)

$$E = \frac{Ha}{\tanh(Ha)}$$

#### Instantaneous Reaction

$$E_{\infty} = 1 + \frac{D_B \cdot C_{B,bulk}}{\nu_B \cdot D_A \cdot C_{A,i}}$$

Where $\nu_B$ is the stoichiometric coefficient.

#### General Case (Danckwerts)

$$E = \frac{\sqrt{1 + Ha^2 \cdot (E_{\infty} - 1)/E_{\infty}}}{1 + (E_{\infty} - 1)^{-1}}$$

### CO₂-Amine Systems

NeqSim includes specific models for CO₂ absorption:

#### Reaction Mechanism (MDEA)

```
CO₂ + MDEA + H₂O ⇌ MDEAH⁺ + HCO₃⁻  (slow, base-catalyzed)
CO₂ + OH⁻ ⇌ HCO₃⁻                   (parallel)
```

#### Reaction Kinetics

$$r_{CO2} = k_2 \cdot [CO_2] \cdot [MDEA]$$

With Arrhenius temperature dependence:

$$k_2 = A \cdot \exp\left(-\frac{E_a}{RT}\right)$$

| Amine | A (m³/mol·s) | Eₐ (kJ/mol) | Source |
|-------|--------------|-------------|--------|
| MDEA | 4.01×10⁸ | 42.0 | Rinker et al. (1995) |
| MEA | 4.4×10¹¹ | 50.5 | Hikita et al. (1977) |
| DEA | 1.3×10¹⁰ | 47.5 | Blauwhoff et al. (1984) |

#### High-Pressure Effects

From the thesis work, high-pressure effects on CO₂ absorption include:

1. **Reduced CO₂ capacity** at high total pressure (up to 40% reduction at 200 bar)
2. **Thermodynamic non-ideality** must be modeled consistently
3. **Reaction kinetics** relatively unaffected by pressure (with N₂ as inert)

---

## Implementation Classes

### Class Hierarchy

```
FluidBoundary (abstract)
├── EquilibriumFluidBoundary
└── NonEquilibriumFluidBoundary (abstract)
    └── KrishnaStandartFilmModel
        └── ReactiveKrishnaStandartFilmModel
            └── ReactiveFluidBoundary
```

### Key Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `FluidBoundary` | `flownode.fluidboundary.heatmasstransfercalc` | Base class for interphase calculations |
| `NonEquilibriumFluidBoundary` | `...nonequilibriumfluidboundary` | Non-equilibrium base |
| `KrishnaStandartFilmModel` | `...filmmodelboundary` | Multicomponent film model |
| `ReactiveKrishnaStandartFilmModel` | `...reactivefilmmodel` | With chemical reactions |
| `EnhancementFactor` | `...enhancementfactor` | Enhancement factor calculations |

### Key Methods

```java
// FluidBoundary
public abstract void solve();
public double[] getMolarFlux();
public double[] getHeatFlux();

// KrishnaStandartFilmModel
public double calcBinarySchmidtNumbers(int phase);
public double calcBinaryMassTransferCoefficients(int phase);
public double calcMassTransferCoefficients(int phase);
public void calcPhiMatrix(int phase);  // Finite flux correction
```

---

## Usage Examples

### Basic Two-Phase Mass Transfer

```java
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.AnnularFlow;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemSrkEos;

// Create two-phase system
SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("CO2", 0.05);
fluid.addComponent("water", 0.05);
fluid.setMixingRule("classic");

// Create pipe geometry
PipeData pipe = new PipeData(0.1);  // 0.1 m diameter

// Create flow node
AnnularFlow node = new AnnularFlow(fluid, pipe);
node.init();
node.initFlowCalc();

// Enable mass transfer
node.getFluidBoundary().setMassTransferCalc(true);
node.getFluidBoundary().solve();

// Get results
double[] molarFlux = node.getFluidBoundary().getMolarFlux();
System.out.println("CO2 flux: " + molarFlux[1] + " mol/m²·s");
```

### With Thermodynamic Corrections

```java
// Enable activity coefficient corrections
node.getFluidBoundary().setThermodynamicCorrections(0, true);  // Gas
node.getFluidBoundary().setThermodynamicCorrections(1, true);  // Liquid

// Enable Stefan flow correction
node.getFluidBoundary().setFiniteFluxCorrection(0, true);
node.getFluidBoundary().setFiniteFluxCorrection(1, true);

node.getFluidBoundary().solve();
```

### CO₂ Absorption with Reaction

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;

// Create system with amine
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(313.15, 30.0);
fluid.addComponent("nitrogen", 0.85);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("water", 0.04);
fluid.addComponent("MDEA", 0.01);
fluid.setMixingRule(10);  // CPA mixing rule

// Use reactive film model
// The enhancement factor is calculated automatically
// based on reaction kinetics and Hatta number
```

---

## References

1. **Solbraa, E. (2002).** *Equilibrium and Non-Equilibrium Thermodynamics of Natural Gas Processing.* 
   Dr.ing. thesis, NTNU. [NVA](https://hdl.handle.net/11250/231326)

2. **Krishna, R., Standart, G.L. (1976).** Mass and energy transfer in multicomponent systems. 
   *Chem. Eng. Commun.*, 3(4-5), 201-275.

3. **Taylor, R., Krishna, R. (1993).** *Multicomponent Mass Transfer*. Wiley.

4. **Danckwerts, P.V. (1970).** *Gas-Liquid Reactions*. McGraw-Hill.

5. **Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001).** *The Properties of Gases and Liquids*. 5th ed. McGraw-Hill.

6. **Rinker, E.B., Ashour, S.S., Sandall, O.C. (1995).** Kinetics and modelling of carbon dioxide 
   absorption into aqueous solutions of N-methyldiethanolamine. *Chem. Eng. Sci.*, 50(5), 755-768.

---

## Related Documentation

- [Heat Transfer Modeling](heat_transfer.md) - Companion heat transfer documentation
- [Fluid Mechanics Overview](README.md) - Main fluid mechanics documentation
- [Physical Properties](../physical_properties/README.md) - Diffusivity models
- [Thermodynamics](../thermo/README.md) - Activity coefficients and phase equilibria
