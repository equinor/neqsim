# Introduction and Historical Context

<!-- Chapter metadata -->
<!-- Notebooks: 01_first_cpa_calculation.ipynb -->
<!-- Estimated pages: 15 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Explain why associating fluids require special thermodynamic models
2. Trace the historical development from van der Waals to CPA
3. Identify the key industrial applications that motivated CPA development
4. Set up and run a basic CPA calculation using NeqSim

## 1.1 Why Association Matters

The accurate prediction of thermodynamic properties is the foundation of chemical and petroleum engineering design. For decades, cubic equations of state (EoS) such as the Soave–Redlich–Kwong (SRK) and Peng–Robinson (PR) equations have served as the workhorses of industrial process simulation. These models excel at describing the phase behavior of hydrocarbon mixtures, where intermolecular interactions are dominated by relatively weak van der Waals (dispersion) forces.

However, a significant class of industrially important fluids exhibits much stronger intermolecular interactions — specifically, **hydrogen bonding**. Water, alcohols (methanol, ethanol), glycols (MEG, DEG, TEG), organic acids, and amines all form hydrogen bonds that profoundly affect their thermodynamic behavior. These interactions lead to phenomena that classical cubic EoS cannot capture:

- **Anomalous vapor pressures** — hydrogen-bonded species have higher boiling points than non-associating molecules of similar molecular weight
- **Liquid–liquid immiscibility** — water and hydrocarbons form two liquid phases, with mutual solubilities that vary strongly with temperature
- **Strong non-ideal mixing** — activity coefficients in associating mixtures can deviate enormously from unity
- **Composition-dependent heat capacities** — the degree of association changes with temperature and composition, affecting caloric properties

In the oil and gas industry, these effects have direct engineering consequences. The solubility of water in natural gas determines hydrate formation risks and dehydration requirements. The partitioning of methanol or MEG between hydrocarbon and aqueous phases governs inhibitor dosing rates. The phase behavior of CO$_2$–water systems controls the design of carbon capture and storage infrastructure. In every case, the accuracy of the thermodynamic model directly impacts capital expenditure, operating costs, and safety.

## 1.2 The Limits of Classical Cubic Equations

To understand why CPA was developed, it is instructive to examine where classical cubic equations fail. Consider the seemingly simple system of water and n-hexane at atmospheric pressure. Experimentally, these two components are nearly immiscible at room temperature, with the mutual solubility of water in n-hexane being on the order of $10^{-4}$ mole fraction, while the solubility of n-hexane in water is even smaller.

A standard SRK or PR equation of state, even with optimally fitted binary interaction parameters ($k_{ij}$), cannot simultaneously reproduce:

1. The vapor pressure of pure water
2. The liquid–liquid phase split between water and n-hexane
3. The temperature dependence of mutual solubilities
4. The composition of the vapor phase in equilibrium with both liquids

The fundamental reason is that cubic EoS treat all intermolecular interactions through two parameters — the attractive energy parameter $a$ and the co-volume $b$ — which are calibrated to reproduce vapor pressure and liquid density. These parameters cannot distinguish between the weak dispersion interactions in hexane and the strong, directional hydrogen bonds in water. The result is that classical models either overpredict or underpredict the mutual solubilities by orders of magnitude.

This limitation extends to many systems of industrial importance:

| System | Engineering Application | Classical EoS Limitation |
|--------|----------------------|--------------------------|
| Water–methane | Gas dehydration, hydrate prediction | Water content off by 50–200% |
| Methanol–hydrocarbons | Hydrate inhibition | Phase partitioning errors > 100% |
| MEG–water–gas | Glycol dehydration | TEG losses poorly predicted |
| CO$_2$–water | CCS pipeline design | Mutual solubility errors > 50% |
| Acetic acid–hydrocarbons | Chemical processing | Dimerization not captured |

*Table 1.1: Industrial systems where classical cubic equations of state fail due to hydrogen bonding.*

## 1.3 Historical Development

### 1.3.1 Early Association Models (1908–1980)

The concept that molecules can associate into clusters is not new. As early as 1908, Dolezalek proposed a "chemical theory" of solutions in which non-ideal behavior was attributed to the formation of new chemical species through association. In this framework, a dimerizing acid like acetic acid is treated as an equilibrium mixture of monomers and dimers:

$$2A \rightleftharpoons A_2, \quad K = \frac{x_{A_2}}{x_A^2}$$

While conceptually appealing, chemical theory models suffered from several limitations: the number of association species grows combinatorially with the number of components, the equilibrium constants are essentially additional fitting parameters, and the theory does not naturally connect to the equation of state framework used for phase equilibrium calculations.

### 1.3.2 Statistical Mechanics of Association (1984–1986)

A breakthrough came in 1984–1986 when Michael Wertheim published a series of four landmark papers that provided a rigorous statistical mechanical framework for describing associating fluids. Wertheim's thermodynamic perturbation theory (TPT) treats association as a perturbation to a reference fluid (typically a hard-sphere or Lennard-Jones fluid) and derives exact expressions for the Helmholtz free energy contribution due to association.

The key insight of Wertheim's theory is that each molecule has a fixed number of **association sites** — specific locations on the molecule where hydrogen bonds can form. Each site can bond with at most one site on another molecule. The fraction of molecules *not* bonded at site $A$, denoted $X_A$, satisfies:

$$X_A = \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}}$$

where $\rho$ is the molar density, $x_j$ is the mole fraction of component $j$, and $\Delta^{A_i B_j}$ is the **association strength** between site $A$ on molecule $i$ and site $B$ on molecule $j$.

The Helmholtz free energy contribution from association is then:

$$\frac{A^{\text{assoc}}}{RT} = \sum_i x_i \left[ \sum_{A_i} \left( \ln X_{A_i} - \frac{X_{A_i}}{2} + \frac{1}{2} \right) \right]$$

This framework is remarkable for its generality — it can describe self-association (e.g., water–water hydrogen bonds), cross-association (e.g., water–methanol), and solvation (e.g., water–aromatic interactions) within a unified theory.

### 1.3.3 From SAFT to CPA (1988–1996)

Wertheim's theory was first applied to equation of state development by Chapman, Gubbins, Jackson, and Radosz, who in 1988–1990 developed the **Statistical Associating Fluid Theory** (SAFT). SAFT builds the total Helmholtz free energy from four contributions:

$$A = A^{\text{ideal}} + A^{\text{segment}} + A^{\text{chain}} + A^{\text{assoc}}$$

where the segment term describes reference fluid interactions (hard sphere + dispersion), the chain term accounts for molecular connectivity, and the association term uses Wertheim's TPT.

While SAFT and its variants (PC-SAFT, soft-SAFT, SAFT-VR) have achieved remarkable success, they differ fundamentally from the cubic EoS that the oil and gas industry had used for decades. This created a practical barrier to adoption: replacing SRK or PR in existing simulation software required changes to flash algorithms, property routines, and process simulators.

Recognizing this barrier, Georgios Kontogeorgis and colleagues at the Technical University of Denmark (DTU) proposed the **Cubic Plus Association** (CPA) equation of state in 1996. The central idea was elegantly simple: combine the familiar SRK cubic equation with Wertheim's association term:

$$P = \underbrace{\frac{RT}{V_m - b} - \frac{a(T)}{V_m(V_m + b)}}_{\text{SRK cubic term}} + \underbrace{P^{\text{assoc}}}_{\text{Wertheim association}}$$

This hybrid approach preserved the strengths of both components — the proven accuracy of cubic EoS for hydrocarbons and the rigorous treatment of hydrogen bonding from Wertheim's theory. For non-associating components, CPA reduces exactly to SRK, ensuring backward compatibility with the existing parameter databases.

### 1.3.4 Industrial Adoption (1996–Present)

The publication of CPA marked the beginning of rapid development and industrial adoption:

- **1996–2000**: Initial parameter fitting and validation for water, alcohols, glycols
- **2000–2006**: Extension to multicomponent systems; Equinor (then Statoil) began internal adoption
- **2006–2010**: Simplified CPA (sCPA) developed for computational efficiency; electrolyte CPA formulated
- **2010–2015**: PR-CPA variant; UMR-CPA (Universal Mixing Rules + CPA); broad industrial deployment
- **2015–2020**: Asphaltene modeling with CPA; improved cross-association schemes
- **2020–present**: Fully implicit solvers; Anderson acceleration; Broyden methods for faster convergence

Today, CPA is implemented in most major process simulators and is the recommended model for systems involving water, alcohols, glycols, and CO$_2$ in the petroleum industry.

## 1.4 The NeqSim Implementation

NeqSim (Non-Equilibrium Simulator) is an open-source Java library for thermodynamic and process simulation that has been developed since 2000. It provides one of the most comprehensive CPA implementations available, with:

- **15 system classes** covering SRK-CPA, PR-CPA, UMR-CPA, and electrolyte CPA variants
- **Multiple solver strategies**: standard successive substitution, fully implicit Newton, Broyden quasi-Newton, and Anderson acceleration
- **Reduced-variable formulations** that improve numerical stability
- **Extensive parameter database** with pre-fitted parameters for water, methanol, ethanol, MEG, DEG, TEG, and many other associating components
- **Cross-association** between different molecular species
- **Integration with process simulation** — CPA fluids can be used seamlessly in separators, heat exchangers, compressors, and complete process flowsheets

The NeqSim CPA class hierarchy follows a layered architecture:

```
SystemSrkEos (standard SRK)
  └── SystemSrkCPA (base SRK-CPA)
        └── SystemSrkCPAs (simplified sCPA)
              └── SystemSrkCPAstatoil (recommended for industrial use)
                    ├── SystemSrkCPAstatoilFullyImplicit
                    ├── SystemSrkCPAstatoilBroydenImplicit
                    └── SystemSrkCPAstatoilAndersonMixing
```

The recommended class for most industrial applications is `SystemSrkCPAstatoil`, which uses the simplified CPA formulation with the Equinor parameter set and mixing rule 10 for automatic handling of cross-association.

### 1.4.1 Your First CPA Calculation

```python
from neqsim import jneqsim

# Create a CPA fluid system at 25°C, 1 bar
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.01325)

# Add components — water is associating, methane is not
fluid.addComponent("water", 1.0)
fluid.addComponent("methane", 1.0)

# Set CPA mixing rule (rule 10 handles cross-association automatically)
fluid.setMixingRule(10)

# Run a flash calculation
ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# Initialize all properties (CRITICAL — must call after flash)
fluid.initProperties()

# Read results
print(f"Number of phases: {fluid.getNumberOfPhases()}")
print(f"Gas phase water content: {fluid.getPhase('gas').getComponent('water').getx():.6f}")
print(f"Liquid phase methane solubility: {fluid.getPhase('aqueous').getComponent('methane').getx():.6f}")
```

This simple example already demonstrates the power of CPA: it correctly predicts the very low mutual solubility of water and methane, which a classical SRK model would overpredict by an order of magnitude.

## 1.5 Scope and Organization of This Book

This book provides a comprehensive treatment of the CPA equation of state, from its theoretical foundations to its numerical implementation and industrial applications. It is organized in three parts:

**Part I: Foundations** (Chapters 1–4) establishes the theoretical background. Chapter 2 reviews the thermodynamic framework — fugacity, chemical potential, and phase equilibrium conditions. Chapter 3 covers classical cubic equations of state that form the "cubic" part of CPA. Chapter 4 presents Wertheim's thermodynamic perturbation theory that provides the "association" part.

**Part II: The CPA Model** (Chapters 5–8) covers the model itself. Chapter 5 presents the complete CPA formulation, including the pressure equation, fugacity coefficients, and thermodynamic derivatives. Chapter 6 discusses pure component parameter estimation through regression against experimental data. Chapter 7 addresses mixing rules and cross-association schemes. Chapter 8 — arguably the most important chapter for practitioners — covers the numerical implementation, including solver algorithms, convergence strategies, and the specific techniques implemented in NeqSim.

**Part III: Applications and Validation** (Chapters 9–12) demonstrates CPA in practice. Chapter 9 covers water–hydrocarbon systems and gas dehydration. Chapter 10 addresses glycol dehydration and gas processing. Chapter 11 focuses on CO$_2$ systems relevant to carbon capture and storage. Chapter 12 discusses advanced topics including electrolyte CPA, asphaltene modeling, and future research directions.

Throughout the book, every equation and algorithm is accompanied by working code examples using NeqSim, and every prediction is validated against experimental data. The reader is encouraged to run the accompanying Jupyter notebooks to develop hands-on experience with CPA calculations.

## Summary

Key points from this chapter:

- Hydrogen bonding in fluids like water, alcohols, and glycols creates thermodynamic behavior that classical cubic EoS cannot capture
- The CPA equation of state combines the SRK cubic equation with Wertheim's association theory to handle both non-associating and associating interactions
- CPA was developed at DTU in 1996 by Kontogeorgis and colleagues, motivated by the need for accurate predictions in oil and gas applications
- NeqSim provides a comprehensive open-source implementation of CPA with multiple solver variants
- This book covers CPA from fundamentals through numerical implementation to industrial applications

## Exercises

1. **Exercise 1.1:** Using NeqSim, compare the predicted vapor pressure of pure water using SRK (`SystemSrkEos`) and CPA (`SystemSrkCPAstatoil`) at temperatures from 25°C to 200°C. Plot the results against NIST reference data.

2. **Exercise 1.2:** Set up a water–n-hexane system at 25°C and 1 bar using both SRK and CPA. Compare the predicted mutual solubilities with experimental values from the IUPAC Solubility Data Series.

3. **Exercise 1.3:** Create a natural gas mixture (methane 85%, ethane 7%, propane 5%, CO$_2$ 2%, water 1%) and compute the water dew point using CPA. Compare with an SRK prediction.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 1.1: 01 Eos Timeline](figures/fig_ch01_01_eos_timeline.png)

*Figure 1.1: 01 Eos Timeline*

![Figure 1.2: 02 Water Density Comparison](figures/fig_ch01_02_water_density_comparison.png)

*Figure 1.2: 02 Water Density Comparison*

![Figure 1.3: 03 Industrial Systems](figures/fig_ch01_03_industrial_systems.png)

*Figure 1.3: 03 Industrial Systems*

![Figure 1.4: Ex01 Z Methane](figures/fig_ch01_ex01_Z_methane.png)

*Figure 1.4: Ex01 Z Methane*
