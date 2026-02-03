---
title: "GERG-2008 and EOS-CG Equations of State"
description: "NeqSim supports the **GERG-2008** and **EOS-CG** equations of state, which are reference-quality models explicit in the Helmholtz free energy. These models are widely used for high-accuracy property c..."
---

# GERG-2008 and EOS-CG Equations of State

NeqSim supports the **GERG-2008** and **EOS-CG** equations of state, which are reference-quality models explicit in the Helmholtz free energy. These models are widely used for high-accuracy property calculations in natural gas and CCS (Carbon Capture and Storage) applications.

## 1. Mathematical Framework

Both GERG-2008 and EOS-CG share the same fundamental mathematical structure. They are fundamental equations of state explicit in the **dimensionless Helmholtz free energy** $\alpha$.

The dimensionless Helmholtz energy $\alpha$ is separated into an ideal gas part $\alpha^0$ and a residual part $\alpha^r$:

$$
\alpha(\delta, \tau, \bar{x}) = \frac{a(\rho, T, \bar{x})}{RT} = \alpha^0(\delta, \tau, \bar{x}) + \alpha^r(\delta, \tau, \bar{x})
$$

Where:
*   $\delta = \rho / \rho_r$ is the reduced density.
*   $\tau = T_r / T$ is the inverse reduced temperature.
*   $\bar{x}$ is the vector of mole fractions.

### Ideal Gas Contribution ($\alpha^0$)
The ideal gas part is determined from the ideal gas heat capacity of the mixture components:

$$
\alpha^0(\delta, \tau, \bar{x}) = \sum_{i=1}^{N} x_i \left[ \alpha_{0i}^0(\delta, \tau) + \ln x_i \right]
$$

### Residual Contribution ($\alpha^r$)
The residual part accounts for intermolecular forces and real fluid behavior. It is typically expressed as a sum of polynomial and exponential terms fitted to high-accuracy experimental data:

$$
\alpha^r(\delta, \tau, \bar{x}) = \sum_{i=1}^{N} x_i \alpha_{0i}^r(\delta, \tau) + \sum_{i=1}^{N-1} \sum_{j=i+1}^{N} x_i x_j F_{ij} \alpha_{ij}^r(\delta, \tau)
$$

This structure allows for extremely high accuracy in density, speed of sound, and heat capacity calculations, often superior to cubic equations of state (like SRK or PR), especially in the supercritical region.

---

## 2. GERG-2008

**Full Name:** GERG-2008 Wide-Range Equation of State for Natural Gases and Other Mixtures.  
**Authors:** O. Kunz and W. Wagner (Ruhr-Universität Bochum).  
**Standard:** ISO 20765-2.

### Application
GERG-2008 is the standard reference equation for **natural gas** transport, processing, and custody transfer. It covers 21 components typical of natural gas.

### Supported Components (21)
Methane, Nitrogen, Carbon Dioxide, Ethane, Propane, Butanes, Pentanes, Hexane, Heptane, Octane, Nonane, Decane, Hydrogen, Oxygen, Carbon Monoxide, Water, Helium, Argon.

### Usage in NeqSim

To use GERG-2008 in NeqSim, use the `SystemGERG2008Eos` class.

```java
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;

public class GergExample {
    public static void main(String[] args) {
        // Create system
        SystemInterface fluid = new SystemGERG2008Eos(298.15, 10.0); // T in K, P in bara
        
        // Add components
        fluid.addComponent("methane", 0.9);
        fluid.addComponent("ethane", 0.1);
        
        // Initialize
        fluid.createDatabase(true);
        fluid.setMixingRule("classic"); // Not strictly used by GERG but good practice for init
        
        // Flash calculation
        neqsim.thermodynamicoperations.ThermodynamicOperations ops = 
            new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Retrieve properties
        // Note: GERG-2008 properties are often accessed via specific methods
        double density = fluid.getPhase(0).getDensity_GERG2008();
        double[] props = fluid.getPhase(0).getProperties_GERG2008();
        
        System.out.println("Density (GERG): " + density + " kg/m3");
    }
}
```

---

## 3. GERG-2008-H2 (Hydrogen Enhanced)

**Full Name:** Extension of the equation of state for natural gases GERG-2008 with improved hydrogen parameters.  
**Authors:** R. Beckmüller, M. Thol, I. Sampson, E.W. Lemmon, R. Span (Ruhr-Universität Bochum, NIST).

### Application
GERG-2008-H2 is an extension of GERG-2008 with **improved hydrogen binary interaction parameters**. This extension is particularly important for:
- Hydrogen-rich natural gas blends (power-to-gas applications)
- Hydrogen transport in existing natural gas pipelines
- CO₂-H₂ mixtures in CCS with hydrogen

### Key Improvements
The GERG-2008-H2 model includes:
- Updated binary reducing parameters for hydrogen with methane, nitrogen, CO₂, and other hydrocarbons
- **New departure function** for N₂-H₂ (Model 8)
- **New departure function** for CO₂-H₂ (Model 9)
- Extended validation range for hydrogen-containing mixtures

### Expected Differences from GERG-2008

| Binary System | Typical Density Difference |
|---------------|---------------------------|
| CH₄-H₂        | ~0.1-0.25%               |
| N₂-H₂         | ~0.05-0.5%               |
| CO₂-H₂        | ~1-1.5% (largest)        |
| C₂H₆-H₂       | ~0.5-0.8%                |

Differences increase with:
- Higher hydrogen content
- Higher pressure
- Lower temperature

### Usage in NeqSim

The GERG-2008-H2 model is available through `SystemGERG2008Eos` by enabling the hydrogen-enhanced mode:

```java
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class Gerg2008H2Example {
    public static void main(String[] args) {
        // Create system with GERG-2008
        SystemGERG2008Eos fluid = new SystemGERG2008Eos(300.0, 50.0); // T in K, P in bara
        
        // Add hydrogen-rich mixture
        fluid.addComponent("methane", 0.7);
        fluid.addComponent("hydrogen", 0.3);
        
        // Enable GERG-2008-H2 model with improved hydrogen parameters
        fluid.useHydrogenEnhancedModel();
        // or equivalently:
        // fluid.setGergModelType(GERG2008Type.HYDROGEN_ENHANCED);
        
        // Flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Retrieve properties
        double density = fluid.getPhase(0).getDensity();
        System.out.println("Density (GERG-2008-H2): " + density + " kg/m3");
        System.out.println("Model: " + fluid.getModelName()); // "GERG2008-H2-EOS"
        
        // Check which model is active
        if (fluid.isUsingHydrogenEnhancedModel()) {
            System.out.println("Using hydrogen-enhanced GERG-2008-H2 model");
        }
    }
}
```

### API Methods

| Method | Description |
|--------|-------------|
| `useHydrogenEnhancedModel()` | Enable GERG-2008-H2 model |
| `setGergModelType(GERG2008Type.STANDARD)` | Use standard GERG-2008 |
| `setGergModelType(GERG2008Type.HYDROGEN_ENHANCED)` | Use GERG-2008-H2 |
| `getGergModelType()` | Get current model type |
| `isUsingHydrogenEnhancedModel()` | Check if H2 model is active |

---

## 4. EOS-CG

**Full Name:** EOS-CG: A Helmholtz energy equation of state for combustion gases and CCS mixtures.  
**Authors:** J. Gernert and R. Span (Ruhr-Universität Bochum).

### Application
EOS-CG is an extension of the GERG framework designed for **Carbon Capture and Storage (CCS)** and **combustion gas** applications. It includes additional components found in flue gases and impurities relevant to CO2 transport.

### Supported Components (27)
Includes all 21 components from GERG-2008, plus:
*   Sulfur Dioxide (SO₂)
*   Nitrogen Monoxide (NO)
*   Nitrogen Dioxide (NO₂)
*   Hydrogen Chloride (HCl)
*   Chlorine (Cl₂)
*   Carbonyl Sulfide (COS)

Recent PRs refreshed the EOS-CG component tables with updated critical properties and binary
interaction data, improving phase behavior for acid-gas heavy blends. The refresh aligns the
library with the latest GERG-compatible datasets so CCS mixtures match reference densities and
sound speed benchmarks more closely.

### Usage in NeqSim

To use EOS-CG in NeqSim, use the `SystemEOSCGEos` class.

```java
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;

public class EosCgExample {
    public static void main(String[] args) {
        // Create system
        SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0);
        
        // Add components (including CCS impurities)
        fluid.addComponent("CO2", 0.95);
        fluid.addComponent("SO2", 0.05);
        
        // Initialize and Flash
        fluid.createDatabase(true);
        neqsim.thermodynamicoperations.ThermodynamicOperations ops = 
            new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
        ops.TPflash();
        
        // Retrieve properties
        double density = fluid.getPhase(0).getDensity_EOSCG();
        
        System.out.println("Density (EOS-CG): " + density + " kg/m3");
    }
}
```

---

## 5. Literature References

1.  **GERG-2008:** Kunz, O., & Wagner, W. (2012). *The GERG-2008 Wide-Range Equation of State for Natural Gases and Other Mixtures: An Expansion of GERG-2004*. Journal of Chemical & Engineering Data, 57(11), 3032–3091.
2.  **GERG-2008-H2:** Beckmüller, R., Thol, M., Sampson, I., Lemmon, E.W., & Span, R. (2022). *Extension of the equation of state for natural gases GERG-2008 with improved hydrogen parameters*. Fluid Phase Equilibria, 557, 113411.
3.  **EOS-CG:** Gernert, J., & Span, R. (2016). *EOS-CG: A Helmholtz energy equation of state for combustion gases and CCS mixtures*. The Journal of Chemical Thermodynamics, 93, 274–293.
