---
title: "GERG-2008 and EOS-CG Equations of State"
description: "Guide to NeqSim's GERG-2008 and EOS-CG Helmholtz-energy implementations, model selection, property access, and validation boundaries."
---

NeqSim exposes implementations of the **GERG-2008** and **EOS-CG** equations of state, which are model families explicit in Helmholtz free energy. The published formulations target natural-gas and CCS (Carbon Capture and Storage) property calculations.

The current `SystemGERG2008Eos` and `SystemEOSCGEos` implementations do not advertise analytical composition, pressure, or temperature fugacity derivatives. Positive finite results from the examples below prove API execution and model selection only; they do not establish custody-transfer accuracy, published-range reproduction, or fitness for a particular engineering decision. Validate the selected mixture and operating envelope against controlled reference data before use.

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

Published reference-EOS formulations can represent density, speed of sound, and heat capacity more directly than cubic equations of state such as SRK or PR. Accuracy from the NeqSim implementation remains mixture-, property-, and state-dependent and requires application-specific validation.

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class GergExample {
  private static final Logger logger = LogManager.getLogger(GergExample.class);

  private GergExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemGERG2008Eos(298.15, 10.0); // K, bara
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    double density = fluid.getPhase(0).getDensity_GERG2008();
    double[] properties = fluid.getPhase(0).getProperties_GERG2008();
    assert Double.isFinite(density) && density > 0.0 : "GERG density must be finite and positive";
    assert properties != null && properties.length > 0 : "GERG property vector must be populated";

    logger.info("GERG-2008 density: {} kg/m3", density);
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class Gerg2008H2Example {
  private static final Logger logger = LogManager.getLogger(Gerg2008H2Example.class);

  private Gerg2008H2Example() {}

  public static void main(String[] args) {
    SystemGERG2008Eos fluid = new SystemGERG2008Eos(300.0, 50.0); // K, bara
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("hydrogen", 0.3);
    fluid.useHydrogenEnhancedModel();

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    double density = fluid.getPhase(0).getDensity("kg/m3");
    assert Double.isFinite(density) && density > 0.0 : "H2-mixture density must be finite and positive";
    assert fluid.getGergModelType() == GERG2008Type.HYDROGEN_ENHANCED;
    assert fluid.isUsingHydrogenEnhancedModel();
    assert "GERG2008-H2-EOS".equals(fluid.getModelName());

    logger.info("GERG-2008-H2 density: {} kg/m3; model: {}", density, fluid.getModelName());
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

## 4. GERG-2008-NH3 (Ammonia Extended)

**Full Name:** Extension of the GERG-2008 equation of state with ammonia as a 22nd component.
**Authors:** T. Neumann, M. Thol, E.W. Lemmon, R. Span (Ruhr-Universität Bochum, NIST); ammonia pure-fluid equation by K. Gao, J. Wu, E.W. Lemmon (NIST, Xi'an Jiaotong Univ.).

### Application
GERG-2008-NH3 extends GERG-2008 to include **ammonia (NH₃)** as a fully integrated 22nd component. This is essential for:
- Ammonia-based hydrogen energy carriers (green ammonia)
- Ammonia co-firing in gas turbines
- Ammonia refrigeration loops in LNG plants
- CO₂-NH₃ mixtures in industrial processes

### Key Features

| Feature | Detail |
|---------|--------|
| Pure-fluid EOS | Full 20-term Gao et al. (2020) equation: 8 power + 10 Gaussian + 2 GaoB terms |
| Critical properties | $T_c = 405.56$ K, $\rho_c = 13.696$ mol/L (Gao et al.) |
| Ideal gas | Planck-Einstein terms with $v = [2.224, 3.148, 0.9579]$ and $\theta = [1646, 3965, 7231]$ K |
| Binary interactions | Reducing parameters and GBS departure functions for NH₃ with CH₄, N₂, CO₂, H₂O, H₂, and other GERG components (Neumann et al. 2020) |
| Published formulation | Gao and Neumann reference data cover ammonia-containing states; NeqSim results require application-specific validation |

### Usage in NeqSim

The GERG-2008-NH3 model is available through `SystemGERG2008Eos` by enabling the ammonia-extended mode:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class Gerg2008NH3Example {
  private static final Logger logger = LogManager.getLogger(Gerg2008NH3Example.class);

  private Gerg2008NH3Example() {}

  public static void main(String[] args) {
    SystemGERG2008Eos fluid = new SystemGERG2008Eos(400.0, 50.0); // K, bara
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ammonia", 0.18);
    fluid.useAmmoniaExtendedModel();

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    double density = fluid.getPhase(0).getDensity("kg/m3");
    assert Double.isFinite(density) && density > 0.0 : "NH3-mixture density must be finite and positive";
    assert fluid.getGergModelType() == GERG2008Type.AMMONIA_EXTENDED;
    assert fluid.isUsingAmmoniaExtendedModel();
    assert "GERG2008-NH3-EOS".equals(fluid.getModelName());

    logger.info("GERG-2008-NH3 density: {} kg/m3; model: {}", density, fluid.getModelName());
  }
}
```

### API Methods

| Method | Description |
|--------|-------------|
| `useAmmoniaExtendedModel()` | Enable GERG-2008-NH3 model |
| `setGergModelType(GERG2008Type.AMMONIA_EXTENDED)` | Enable GERG-2008-NH3 model (enum form) |
| `isUsingAmmoniaExtendedModel()` | Check if NH3 model is active |
| `getModelName()` | Returns `"GERG2008-NH3-EOS"` when NH3 model is active |

---

## 5. EOS-CG

**Full Name:** EOS-CG-2021: a Helmholtz energy equation of state for CCS mixtures.
**Authors:** Tobias Neumann, Stefan Herrig, Ian Bell, Robin Beckmüller, Eric W. Lemmon, Monika Thol, and Roland Span.

### Application
EOS-CG is an extension of the GERG framework designed for **Carbon Capture and Storage (CCS)** and **combustion gas** applications. It includes additional components found in CO2-rich transport streams and impurities relevant to CCS.

### EOS-CG-2021 component coverage
The EOS-CG-2021 publication defines a 16-component CCS model: CO₂, water, N₂, O₂, Ar, CO, H₂, CH₄, H₂S, SO₂, monoethanolamine (MEA), diethanolamine (DEA), HCl, Cl₂, NH₃, and methyl diethanolamine (MDEA). NeqSim keeps the GERG-style hydrocarbon slots for compatibility while refreshing the EOS-CG reducing-parameter table and adding MDEA as an EOS-CG component.

Recent updates refreshed the EOS-CG component tables with the EOS-CG-2021 gas constant, MDEA pure-fluid parameters, and binary reducing parameters, improving consistency with the current CCS-mixture model.

### Usage in NeqSim

To use EOS-CG in NeqSim, use the `SystemEOSCGEos` class. This introductory example
uses pure CO2 gas at 298.15 K and 10 bara, also covered by the repository's CO2
density regression. Mixture flashes require separate convergence and accuracy
validation for the intended composition and operating range.

The previously shown 95 mol% CO2 / 5 mol% SO2 flash at 298.15 K and 50 bara
fails density-root convergence in the current implementation. The reproducible
limitation is tracked in [issue #3702](https://github.com/equinor/neqsim/issues/3702).

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class EosCgExample {
  private static final Logger logger = LogManager.getLogger(EosCgExample.class);

  private EosCgExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 10.0); // K, bara
    fluid.addComponent("CO2", 1.0);
    fluid.createDatabase(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();

    double density = fluid.getPhase(0).getDensity_EOSCG();
    assert Double.isFinite(density) && density > 0.0 : "EOS-CG density must be finite and positive";

    logger.info("EOS-CG density: {} kg/m3", density);
  }
}
```

---

## 6. Literature References

1.  **GERG-2008:** Kunz, O., & Wagner, W. (2012). *The GERG-2008 Wide-Range Equation of State for Natural Gases and Other Mixtures: An Expansion of GERG-2004*. Journal of Chemical & Engineering Data, 57(11), 3032–3091.
2.  **GERG-2008-H2:** Beckmüller, R., Thol, M., Sampson, I., Lemmon, E.W., & Span, R. (2022). *Extension of the equation of state for natural gases GERG-2008 with improved hydrogen parameters*. Fluid Phase Equilibria, 557, 113411.
3.  **EOS-CG:** Gernert, J., & Span, R. (2016). *EOS-CG: A Helmholtz energy equation of state for combustion gases and CCS mixtures*. The Journal of Chemical Thermodynamics, 93, 274–293.
4.  **EOS-CG-2021:** Neumann, T., Herrig, S., Bell, I.H., Beckmüller, R., Lemmon, E.W., Thol, M., & Span, R. (2023). *EOS-CG-2021: A Mixture Model for the Calculation of Thermodynamic Properties of CCS Mixtures*. International Journal of Thermophysics, 44, 178.
5.  **MDEA Pure-Fluid EOS:** Neumann, T., Baumhögger, E., Span, R., Vrabec, J., & Thol, M. (2022). *Thermodynamic Properties of Methyl Diethanolamine*. International Journal of Thermophysics, 43, 10.
6.  **GERG-2008-NH3:** Neumann, T., Thol, M., Lemmon, E.W., & Span, R. (2020). *Extension of the equation of state for natural gases (GERG-2008) with ammonia*. Molecular Physics, 118(21–22), e1769856.
7.  **NH3 Pure-Fluid EOS:** Gao, K., Wu, J., & Lemmon, E.W. (2020). *A Helmholtz Energy Equation of State for Ammonia*. International Journal of Thermophysics, 41, 68.
