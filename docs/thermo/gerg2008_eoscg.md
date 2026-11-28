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

### Subcooled LNG TP flash

GERG-2008 checks that a density root reproduces the specified pressure on a
positive-slope branch. If the iterative vapor trial cannot provide such a root,
NeqSim tries the bracketed dense branch at the same pressure and still runs
the flash stability calculation. A single-phase subcritical result is labelled
liquid when the gas and liquid requests identify the same dense root.
See the [cryogenic EOS regression cases](cryogenic_eos_validation.md) for the
tested LNG composition, phase transition and measured density comparisons.

### Liquid density and phase envelopes

GERG-2008 liquid density calculations bracket the high-density root, then use
a local Chebyshev approximation to seed a safeguarded Newton solve. The
accepted density closes the pressure equation of the original GERG model on a
positive-slope branch. If the approximation is unsuitable, NeqSim refines the
bracket by bisection. During the scan, a negative-to-positive pressure slope
can reveal a pressure minimum even when both endpoint pressures exceed the
target. NeqSim checks that interval for a higher positive-slope root before
continuing to lower densities. The approximation follows the proxy-and-polish approach
of [Bell and Alpert (2018)](https://doi.org/10.1016/j.fluid.2018.04.026).
The scope is the requested liquid branch. Certification of all density roots
and global phase stability require additional calculations.

Phase-envelope tracing uses bisection for its nested finite differences and
reuses density results at identical states. The cache is isolated by model,
thread and phase branch. Fugacity perturbations evaluate the residual Helmholtz
energy directly at the trial composition, leaving the original phase intact.

### Thread safety

The GERG-2008, GERG-2008-H2, GERG-2008-NH3 and EOS-CG kernels keep mutable
temperature, composition and work state. `NeqSimGERG2008` and `NeqSimEOSCG`
therefore hold one initialised kernel per thread (and per GERG model type), and
each calculation uses the kernel of the thread that runs it. Independently owned
fluids can be evaluated in parallel, for example with
`ProcessSystem.runParallel()`, and give the same results as a serial run. The
regression test `ReferenceEosThreadConfinementTest` races two workers at
different states and requires bit-identical residual Helmholtz energies; the
cases come from a report by Kirill Shevtsov, where a shared kernel shifted the
result by 84 % (GERG-2008) and 64 % (EOS-CG).

What remains the caller's responsibility:

- One fluid, phase or wrapper must not be used by several threads at once.
  `clone()` the fluid for each worker.
- Every thread that uses a model holds its own kernel for its lifetime, so
  memory grows with the number of threads in long-lived pools.
- `clearCache()` makes the next calculation on each thread build a fresh
  kernel. Existing wrappers stay usable. An EOS-CG kernel passed explicitly to a
  wrapper is owned by the caller and is not affected.

### Usage in NeqSim

To use GERG-2008 in NeqSim, use the `SystemGERG2008Eos` class.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.gerg.NeqSimGERG2008;
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
    heavyEndLumping();
  }

  /** Demonstrates a molar-mass-preserving heavy-end lump in a lean gas. */
  private static void heavyEndLumping() {
    SystemInterface fluid = new SystemSrkEos(288.15, 5.0); // K, bara
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("nitrogen", 0.008);
    double heavy = 0.002;
    double xDecane = (0.130 - 0.12825) / (0.14228 - 0.12825); // 130 g/mol
    fluid.addComponent("n-nonane", heavy * (1.0 - xDecane));
    fluid.addComponent("nC10", heavy * xDecane);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();

    double gergDensity = fluid.getPhase("gas").getDensity_GERG2008();
    double gergMolarMass = new NeqSimGERG2008(fluid.getPhase("gas")).getMolarMass();
    double phaseMolarMass = fluid.getPhase("gas").getMolarMass();
    assert Double.isFinite(gergDensity) && gergDensity > 0.0;
    assert Math.abs(gergMolarMass - phaseMolarMass) <= 1.0e-4 * phaseMolarMass;
    logger.info("GERG density: {} kg/m3; mapped molar mass: {} kg/mol", gergDensity, gergMolarMass);
  }
}
```

### Heavy hydrocarbons in the gas

GERG-2008 has no components heavier than n-decane (142.28 g/mol) and was
fitted to pipeline gas, so C6+ is the weakest part of the model. Plus
fractions, pseudo-components and TBP fractions are therefore not described by
their own properties. This section states what NeqSim does with them and how to
keep the error under control.

#### What NeqSim does with components that are not in the GERG list

`NeqSimGERG2008.mapComposition` places each phase component in a GERG slot
before the mole fractions are renormalised. The 21 named components (including
`n-hexane` ... `n-nonane` and `nC10`) go to their own slot. Every other
component is assigned by its **molar mass**:

The limits below are rounded for readability; the implementation uses strict inequalities at the exact cutoffs.

| Molar mass of the component [g/mol] | GERG slot used |
|---|---|
| 44.10 to 58.12 | n-butane |
| 58.12 to 72.15 | i-pentane |
| 72.15 to 86.2 | n-hexane |
| 86.2 to 100.20 | n-heptane |
| 100.20 to 107 | n-octane |
| 107 to 121 | n-nonane |
| above 121 | n-decane |

Consequences to be aware of:

- Mapped mole fractions are accumulated in their assigned slots and then
  renormalised. The heavy fraction behaves thermodynamically as n-decane (or
  the slot above), not as its true molecular size. GERG density uses `molar
  density from GERG x molar mass of the real phase`. This preserves the
  real-phase mass conversion, but does not establish the accuracy of the
  molar volume, density or heat capacity. The sign and magnitude of the
  property error depend on composition and state.
- A component with a molar mass exactly on a boundary, or below 44.10 g/mol and
  not named in the list, is dropped and the rest is renormalised. Use exact
  names for light components.
- GERG-2008 gives no warning. The mapped molar mass,
  `new NeqSimGERG2008(phase).getMolarMass()`, can be compared with
  `phase.getMolarMass()` to see how much the mapping changed.

#### Applicability

Treat GERG-2008 as valid only for gas where C6+ is a few tenths of a mole
percent at most (the ISO 20765-2 normal range for each of n-hexane to n-decane
is of the order 0.05 to 0.1 mol% or less; check the standard table for the
exact limits). Outside this range, or when the gas is close to its
hydrocarbon dew point, the equation is an extrapolation. Rich gas, condensate and
anything that needs a C7+ characterisation should use SRK/PR (or UMR-PRU, CPA
when water or glycol is present).

#### Recommended workflow

1. **Do the phase split with a model that characterises heavy ends.** Flash with
   SRK, PR or UMR-PRU using `addTBPfraction`/`addPlusFraction`. Hydrocarbon dew
   point, liquid dropout and the composition of the gas leaving a separator come
   from this flash, not from GERG-2008.
2. **Apply GERG-2008 to the gas phase only.** After separation the heavy ends in
   the gas are small. Use `getPhase("gas").getDensity_GERG2008()` or
   `getProperties_GERG2008()` on that phase for density, Z, speed of sound,
   enthalpy and Joule-Thomson coefficient.
3. **Lump the remaining heavy ends explicitly instead of relying on the mass
   mapping.** Replace a fraction of molar mass $M$ between two GERG
   components $M_a < M < M_b$ by a mixture of both with the same moles and
   molar mass:

   $$
   x_b = \frac{M - M_a}{M_b - M_a}, \qquad x_a = 1 - x_b
   $$

   For a 130 g/mol fraction this gives 87.6 % n-nonane and 12.4 % n-decane. The
   molar mass of the GERG mixture then equals that of the real gas. A
   fraction heavier than n-decane cannot be matched with this two-component
   balance. Keep it in the SRK/PR flash and use that characterised model for
   properties unless a separately validated approximation is available. Do not
   silently omit the fraction from a GERG calculation.
4. **Check and report.** Compare the GERG molar mass with the real phase molar
   mass and compare the GERG density with the SRK/PR density of the same phase.
   A large difference flags a property-model discrepancy, not necessarily a
   heavy-end effect. A small difference is not independent validation either.
   Record the C6+ mole fraction, mapping, conditions and applicable range.

The `heavyEndLumping()` method in the complete `GergExample` above demonstrates
this procedure for a lean gas and checks the gas-phase density and molar-mass
balance. `GergEoscgDocumentationTest` extracts, compiles and executes the example
with assertions enabled.

The regression test `NeqSimGERG2008HeavyEndsTest` covers the mapping error of an
unlumped 130 g/mol fraction and the molar-mass match after lumping.

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

The [CO2/SO2 mixture example and numerical validation boundary](eoscg_co2_so2)
document the initialization-order repair for issue #3702. With that repair, the
95 mol% CO2 / 5 mol% SO2 flash at 298.15 K and 50 bara converges to gas and liquid;
the earlier density-root limitation applies to versions without this repair.
The linked example checks each phase's pressure and the component inventories.

For a requested liquid density, EOS-CG uses the same Chebyshev refinement as
GERG-2008, with pressure and slope evaluated by the EOS-CG kernel. A positive-
slope crossing is a candidate density for the requested branch. The TP flash
still determines the phase split through the fugacity and stability equations.
The phase-envelope finite differences retain their original root refinement.

For pure-component EOS-CG volume–energy flashes, the saturation-volume stability
check requires distinct, physically ordered roots: vapor molar volume must exceed
liquid molar volume. Inverted or coalesced numerical roots cannot establish a
stable single phase. The solver attempts a saturation-line solution and throws
`IllegalStateException` if neither route meets the volume and energy specifications.
EOS-CG refreshes temperature-dependent Helmholtz terms for every temperature
change, including the small steps used near saturation. Reusing cached terms
across successive sub-`1e-7` K changes can otherwise make properties depend on
the iteration history. The reference-EOS liquid-density root is refined to a
pressure residual of `max(1e-9 kPa, 1e-11 * abs(pressure in kPa))`, so density
roundoff does not dominate saturation-line volume closure. The VU solver retains
its `1e-8` relative volume and energy acceptance tolerances.

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
