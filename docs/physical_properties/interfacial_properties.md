# Interfacial Properties

This guide documents the interfacial property calculations available in NeqSim, including surface tension and related phenomena.

## Table of Contents
- [Overview](#overview)
- [Surface Tension Models](#surface-tension-models)
  - [Parachor (Macleod-Sugden)](#parachor-macleod-sugden)
  - [Gradient Theory (GT)](#gradient-theory-gt)
  - [Linear Gradient Theory (LGT)](#linear-gradient-theory-lgt)
  - [Firozabadi-Ramley](#firozabadi-ramley)
- [Model Selection by Interface Type](#model-selection-by-interface-type)
- [Usage Examples](#usage-examples)
- [Adsorption Calculations](#adsorption-calculations)
- [Mathematical Background](#mathematical-background)

---

## Overview

Interfacial tension (IFT) describes the energy required to create a unit area of interface between two phases. It is critical for:
- Capillary pressure calculations
- Droplet/bubble formation
- Mass transfer at interfaces
- Enhanced oil recovery studies
- Separator design

**Units:**
- Default output: **N/m** (Newtons per meter)
- Alternative: **mN/m** (milliNewtons per meter = dyne/cm)

**Basic usage:**
```java
fluid.initPhysicalProperties();
double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1);  // N/m
```

---

## Surface Tension Models

### Parachor (Macleod-Sugden)

The Parachor method is an empirical correlation relating surface tension to density difference and component parachors.

**Class:** `ParachorSurfaceTension`

**Equation:**
$$\sigma^{1/4} = \sum_i P_i \left( \frac{\rho_L x_i}{M_{mix,L}} - \frac{\rho_V y_i}{M_{mix,V}} \right)$$

where:
- $P_i$ is the parachor of component $i$ (from COMP database)
- $\rho$ is molar density (mol/m³)
- $x_i, y_i$ are liquid and vapor mole fractions
- $M_{mix}$ is mixture molar mass

**Parachor values:**
- Stored in COMP database as `PARACHOR` column
- For CPA models, use `PARACHOR_CPA`

**Applicable interfaces:** Gas-liquid, Gas-aqueous

**Best for:**
- Hydrocarbon systems
- Quick estimates
- When parachor values are available

**Usage:**
```java
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Parachor");
```

---

### Gradient Theory (GT)

The Gradient Theory is a rigorous thermodynamic approach based on density functional theory.

**Classes:**
- `GTSurfaceTension` - Full gradient theory (most rigorous)
- `GTSurfaceTensionSimple` - Simplified version
- `GTSurfaceTensionODE` - ODE-based solver

**Physical basis:**

Near an interface, the Helmholtz energy depends on density gradients:

$$A = \int_{-\infty}^{\infty} \left[ a_0(\boldsymbol{n}) + \frac{1}{2}\sum_i\sum_j c_{ij} \frac{dn_i}{dz}\frac{dn_j}{dz} \right] dz$$

where:
- $a_0$ is the homogeneous fluid Helmholtz energy
- $c_{ij}$ are the influence parameters
- $n_i$ is the number density of component $i$
- $z$ is the spatial coordinate

**Surface tension calculation:**
$$\sigma = \int_{-\infty}^{\infty} \sum_i\sum_j c_{ij} \frac{dn_i}{dz}\frac{dn_j}{dz} dz$$

**Influence parameter correlation:**
$$c_i = (A_i t_i + B_i) a_i b_i^{2/3}$$

where:
- $t_i = 1 - T/T_{c,i}$
- $a_i, b_i$ are EoS parameters
- $A_i, B_i$ are component-specific constants

**Applicable interfaces:** All phase pairs

**Best for:**
- High accuracy requirements
- Near-critical conditions
- When EoS is well-calibrated

**Usage:**
```java
// Full gradient theory
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Full Gradient Theory");

// Simplified version (faster)
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Simple Gradient Theory");
```

---

### Linear Gradient Theory (LGT)

A linearized approximation of gradient theory that is computationally efficient.

**Class:** `LGTSurfaceTension`

**Approximation:**
Assumes linear density profile between bulk phases:

$$n_i(z) = n_i^L + \frac{n_i^V - n_i^L}{L} z$$

This allows analytical integration:

$$\sigma = \sum_i\sum_j c_{ij} \frac{(n_i^V - n_i^L)(n_j^V - n_j^L)}{L}$$

where $L$ is optimized to minimize Helmholtz energy.

**Applicable interfaces:** Gas-liquid

**Best for:**
- Balance of accuracy and speed
- Parametric studies
- When full GT is too slow

**Usage:**
```java
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Linear Gradient Theory");
```

---

### Firozabadi-Ramley

A correlation specifically designed for liquid-liquid interfaces (oil-water).

**Class:** `FirozabadiRamleyInterfaceTension`

**Equation:**
$$\sigma_{ow} = \sigma_o + \sigma_w - 2\sqrt{\sigma_o \sigma_w} \phi$$

where:
- $\sigma_o, \sigma_w$ are oil and water surface tensions
- $\phi$ is an interaction parameter

**Applicable interfaces:** Oil-water (liquid-liquid)

**Best for:**
- Oil-water systems
- When aqueous phase is present
- Three-phase systems

**Usage:**
```java
fluid.getInterphaseProperties().setInterfacialTensionModel("oil", "aqueous", "Firozabadi Ramley");
```

---

## Model Selection by Interface Type

NeqSim automatically selects models based on phase types, but you can override:

### Using Model Numbers

```java
// Set interfacial tension model set by number
fluid.getInterphaseProperties().setInterfacialTensionModel(0);  // Default set
```

| Number | Gas-Oil | Gas-Aqueous | Oil-Aqueous |
|--------|---------|-------------|-------------|
| 0 | Parachor | Parachor | Firozabadi-Ramley |
| 1 | Full GT | Simple GT | Simple GT |
| 2 | LGT | LGT | LGT |
| 3 | Parachor | Parachor | Firozabadi-Ramley |
| 4 | Simple GT | Parachor | LGT |
| 5 | Parachor | Parachor | Firozabadi-Ramley |

### Using Named Models

```java
// Set specific models per interface
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", "Full Gradient Theory");
fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "aqueous", "Parachor");
fluid.getInterphaseProperties().setInterfacialTensionModel("oil", "aqueous", "Firozabadi Ramley");
```

Available model names:
- `"Parachor"` or `"Weinaug-Katz"`
- `"Full Gradient Theory"`
- `"Simple Gradient Theory"`
- `"Linear Gradient Theory"`
- `"Firozabadi Ramley"`

---

## Usage Examples

### Basic Surface Tension

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("n-decane", 0.2);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();

// Get surface tension between phase 0 and 1
double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1);
System.out.println("Surface tension: " + sigma * 1000 + " mN/m");
```

### Comparing Surface Tension Models

```java
String[] models = {"Parachor", "Full Gradient Theory", "Linear Gradient Theory"};

SystemInterface baseFluid = createTwoPhaseFluid();
ThermodynamicOperations ops = new ThermodynamicOperations(baseFluid);
ops.TPflash();
baseFluid.initPhysicalProperties();

for (String model : models) {
    SystemInterface fluid = baseFluid.clone();
    fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", model);
    fluid.initPhysicalProperties();
    
    double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1000;
    System.out.println(model + ": " + sigma + " mN/m");
}
```

### Surface Tension vs Pressure (Approaching Critical)

```java
SystemInterface fluid = new SystemSrkEos(350.0, 10.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("n-pentane", 0.5);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

double[] pressures = {10, 30, 50, 70, 90, 100, 110};

for (double P : pressures) {
    fluid.setPressure(P, "bar");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    
    if (fluid.getNumberOfPhases() >= 2) {
        fluid.initPhysicalProperties();
        double sigma = fluid.getInterphaseProperties().getSurfaceTension(0, 1) * 1000;
        System.out.println("P=" + P + " bar: σ=" + sigma + " mN/m");
    } else {
        System.out.println("P=" + P + " bar: Single phase");
    }
}
// Surface tension approaches zero at critical point
```

### Three-Phase System

```java
SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 30.0);
fluid.addComponent("methane", 0.6);
fluid.addComponent("n-heptane", 0.3);
fluid.addComponent("water", 0.1);
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Get all interfacial tensions
int nPhases = fluid.getNumberOfPhases();
for (int i = 0; i < nPhases; i++) {
    for (int j = i + 1; j < nPhases; j++) {
        double sigma = fluid.getInterphaseProperties().getSurfaceTension(i, j);
        System.out.println("Phase " + i + " - Phase " + j + ": " + 
            sigma * 1000 + " mN/m");
    }
}
```

---

## Adsorption Calculations

NeqSim also supports adsorption calculations at solid surfaces.

### Setup

```java
// Initialize adsorption
fluid.getInterphaseProperties().initAdsorption();

// Set adsorbent material
fluid.getInterphaseProperties().setSolidAdsorbentMaterial("ite");

// Calculate adsorption
fluid.getInterphaseProperties().calcAdsorption();
```

### Access Results

```java
AdsorptionInterface ads = fluid.getInterphaseProperties().getAdsorptionCalc("gas");
// Access adsorption quantities per component
```

---

## Mathematical Background

### Thermodynamic Definition

Surface tension is defined as:

$$\sigma = \left( \frac{\partial G}{\partial A} \right)_{T,P,n}$$

where $G$ is Gibbs energy and $A$ is interfacial area.

### Young-Laplace Equation

The pressure difference across a curved interface:

$$\Delta P = \sigma \left( \frac{1}{R_1} + \frac{1}{R_2} \right)$$

where $R_1, R_2$ are the principal radii of curvature.

### Temperature Dependence

Surface tension typically decreases with temperature:

$$\sigma = \sigma_0 \left( 1 - T/T_c \right)^n$$

where $n \approx 1.26$ (Guggenheim exponent).

At the critical point: $\sigma \rightarrow 0$.

### Pressure Dependence

Surface tension generally decreases with increasing pressure because:
1. Density difference decreases
2. Phases become more similar as pressure increases

Near the critical point, $\sigma \propto (\rho_L - \rho_V)^{3.9}$.

---

## Typical Values

| Interface | Temperature | Typical IFT |
|-----------|-------------|-------------|
| Methane-Water | 25°C, 100 bar | 50-70 mN/m |
| Crude Oil-Gas | Reservoir | 5-30 mN/m |
| Crude Oil-Water | 25°C | 20-30 mN/m |
| n-Hexane-Air | 25°C | 18 mN/m |
| Water-Air | 25°C | 72 mN/m |
| Near critical | - | 0-1 mN/m |

---

## References

1. Macleod, D.B. (1923). On a Relation between Surface Tension and Density. Trans. Faraday Soc.
2. Sugden, S. (1924). The Variation of Surface Tension with Temperature and Some Related Functions. J. Chem. Soc.
3. Cahn, J.W., Hilliard, J.E. (1958). Free Energy of a Nonuniform System. J. Chem. Phys.
4. Miqueu, C., et al. (2004). Modelling of the Surface Tension of Pure Components with the Gradient Theory. Fluid Phase Equilib.
5. Firozabadi, A., Ramey, H.J. (1988). Surface Tension of Water-Hydrocarbon Systems at Reservoir Conditions. JCPT.
