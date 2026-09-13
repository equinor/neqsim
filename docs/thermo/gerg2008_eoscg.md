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
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class EosCgExample {
  private static final Logger logger = LogManager.getLogger(EosCgExample.class);

  private EosCgExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0); // K, bara
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("SO2", 0.05);
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
