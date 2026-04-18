# Association Theory: Wertheim's Thermodynamic Perturbation Theory

<!-- Chapter metadata -->
<!-- Notebooks: 01_site_fractions.ipynb, 02_association_strength.ipynb -->
<!-- Estimated pages: 20 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Explain the physical basis of Wertheim's thermodynamic perturbation theory (TPT)
2. Define association sites, bonding states, and the site fraction $X_A$
3. Derive the Helmholtz energy contribution from association
4. Compute the association strength $\Delta^{AB}$ and understand its components
5. Apply common association schemes (2B, 3B, 4C) to real molecules

## 4.1 The Physics of Hydrogen Bonding

### 4.1.1 What Is a Hydrogen Bond?

A hydrogen bond forms when a hydrogen atom covalently bonded to an electronegative atom (the **donor**) interacts with a lone pair of electrons on another electronegative atom (the **acceptor**). The interaction is primarily electrostatic but has a significant covalent component at short distances.

The key characteristics of hydrogen bonds that distinguish them from dispersion forces:

- **Strength**: Hydrogen bonds have energies of 10–40 kJ/mol, compared to 0.5–5 kJ/mol for typical dispersion interactions
- **Directionality**: Hydrogen bonds are roughly linear, with an optimal donor–H–acceptor angle near 180°
- **Saturability**: Each hydrogen atom can participate in at most one hydrogen bond (to first approximation)
- **Short range**: Hydrogen bonds operate over distances of 1.5–3.5 Å

These characteristics mean that hydrogen bonding cannot be adequately described by the mean-field, isotropic attraction captured by the $a$ parameter in cubic equations of state.

### 4.1.2 Association vs. Solvation

Two distinct types of strong specific interactions are relevant for thermodynamic modeling:

**Self-association** occurs between molecules of the same species. Water molecules form hydrogen bonds with other water molecules, creating a three-dimensional network. Alcohols self-associate through OH–OH hydrogen bonds, forming linear chains in the liquid state.

**Cross-association** (or solvation) occurs between different molecular species. For example, the oxygen atom in a ketone can accept a hydrogen bond from water, even though the ketone cannot donate hydrogen bonds. Similarly, the $\pi$-electrons in aromatic compounds can act as weak hydrogen bond acceptors.

Both phenomena are captured within Wertheim's framework by appropriate assignment of association sites.

## 4.2 Wertheim's Thermodynamic Perturbation Theory

### 4.2.1 The Fundamental Papers

Between 1984 and 1986, Michael Wertheim published a series of four papers that laid the foundation for modern association models. The theory is developed in the context of classical statistical mechanics for fluids with anisotropic interactions.

The key innovation was to decompose the intermolecular potential into two parts:

$$u(1,2) = u_{\text{ref}}(r_{12}) + u_{\text{assoc}}(1,2)$$

where $u_{\text{ref}}$ is a spherically symmetric reference potential (e.g., hard sphere or Lennard-Jones) that depends only on the center–center distance $r_{12}$, and $u_{\text{assoc}}$ is the orientation-dependent association interaction that depends on the full configuration (position and orientation) of both molecules.

### 4.2.2 The Site Model

Wertheim modeled the anisotropic attraction by placing discrete **association sites** on each molecule. Each site represents a specific location where a hydrogen bond can form. A site $A$ on molecule $i$ can bond with site $B$ on molecule $j$ if and only if the two sites are within a characteristic distance and in a favorable mutual orientation.

The site–site interaction potential has the form:

$$u_{\text{assoc}}^{AB}(1,2) = \begin{cases} -\varepsilon^{AB} & \text{if } r_{AB} < r_c^{AB} \\ 0 & \text{otherwise} \end{cases}$$

where $\varepsilon^{AB}$ is the association energy (well depth) and $r_c^{AB}$ is the critical bonding distance.

The critical assumption of the theory is that **each site can bond with at most one other site**. This steric incompatibility condition (one-bond-per-site) is what makes Wertheim's theory tractable — it eliminates the combinatorial explosion of possible bonding configurations that plagued earlier chemical theories.

### 4.2.3 Graph Theory and Cluster Expansion

Wertheim developed his theory using a graphical expansion of the partition function, analogous to the Mayer cluster expansion for simple fluids. The key step is to classify molecular clusters according to the bonding topology:

- **Monomers**: molecules with no bonds
- **Dimers**: pairs of molecules connected by one bond
- **Trimers**: three molecules in a chain
- **Branched structures**: molecules with bonds to multiple partners (at different sites)

The one-bond-per-site condition ensures that the cluster expansion can be resummed exactly to first order in the perturbation (first-order thermodynamic perturbation theory, or TPT1). This remarkable result means that the free energy depends only on the **monomer fraction** — the fraction of molecules not bonded at each site — rather than on the detailed distribution of cluster sizes.

## 4.3 The Site Balance Equation

### 4.3.1 Derivation

The central result of Wertheim's TPT1 is an implicit equation for $X_{A_i}$, the fraction of molecules of type $i$ that are **not bonded** at site $A$:

$$X_{A_i} = \frac{1}{1 + \rho \sum_{j} x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}}$$

Let us examine each term:

- $\rho$ is the total molar density of the mixture
- $x_j$ is the mole fraction of component $j$
- The outer sum runs over all components $j$
- The inner sum runs over all association sites $B_j$ on component $j$
- $X_{B_j}$ is the fraction of molecules $j$ not bonded at site $B$
- $\Delta^{A_i B_j}$ is the **association strength** between site $A$ on molecule $i$ and site $B$ on molecule $j$

This equation has a simple physical interpretation: the probability that site $A$ on molecule $i$ is free (not bonded) equals 1 divided by 1 plus the total concentration of available bonding partners weighted by the association strength. If there are many potential bonding partners with strong interactions ($\rho x_j X_{B_j} \Delta^{AB}$ is large), then $X_{A_i}$ is small — most sites are bonded.

### 4.3.2 The Association Strength

The association strength $\Delta^{A_i B_j}$ quantifies the tendency for site $A$ on molecule $i$ to bond with site $B$ on molecule $j$. In the CPA framework, it is given by:

$$\Delta^{A_i B_j} = g(\rho) \left[\exp\left(\frac{\varepsilon^{A_i B_j}}{RT}\right) - 1\right] b_{ij} \beta^{A_i B_j}$$

where:

- $g(\rho)$ is the **radial distribution function** (RDF) at contact distance, evaluated for the reference fluid
- $\varepsilon^{A_i B_j}$ is the **association energy** — the depth of the potential well for the site–site interaction
- $\beta^{A_i B_j}$ is the **association volume** — a dimensionless parameter related to the spatial extent of the bonding interaction
- $b_{ij}$ is the co-volume, linking the association to the molecular size

The exponential factor $[\exp(\varepsilon^{AB}/RT) - 1]$ captures the Boltzmann weighting of the association energy. At low temperatures, this factor is large, meaning strong association. At high temperatures, it approaches $\varepsilon^{AB}/RT$ (weak association limit). The $-1$ ensures that $\Delta \to 0$ as $\varepsilon^{AB} \to 0$ (no association for non-interacting sites).

### 4.3.3 The Radial Distribution Function

The radial distribution function $g(\rho)$ describes the probability of finding the center of molecule $j$ at contact distance from molecule $i$, relative to a uniform distribution. For the simplified CPA, a simple expression is used:

$$g(\rho) = \frac{1}{1 - 1.9 \eta}$$

where $\eta = b\rho/4$ is the packing fraction. This expression, derived from the Carnahan–Starling equation, provides a good approximation for hard-sphere fluids and captures the key physics: as density increases, the probability of molecular contact increases (because molecules are forced closer together), which enhances the rate of association.

## 4.4 Association Schemes

### 4.4.1 Notation

Association schemes specify the number and type of sites on each molecule. The standard notation, introduced by Huang and Radosz (1990), uses numbers and letters:

- **1A**: One association site (e.g., HCl)
- **2A**: Two identical sites (e.g., dimerizing acid)
- **2B**: Two non-identical sites — one electron donor, one electron acceptor (e.g., simple alcohol model)
- **3B**: Three sites — two identical donors and one acceptor, or vice versa (e.g., alcohol with two lone pairs and one OH)
- **4C**: Four sites — two donors and two acceptors (e.g., water)

### 4.4.2 Common Molecules and Their Schemes

| Molecule | Recommended Scheme | Sites | Description |
|----------|-------------------|-------|-------------|
| Water | 4C | 2 $e^-$ donors, 2 $e^-$ acceptors | Two lone pairs + two OH |
| Methanol | 2B or 3B | 1 donor, 1 (or 2) acceptor | OH group |
| Ethanol | 2B or 3B | 1 donor, 1 (or 2) acceptor | OH group |
| MEG | 4C | 2 donors, 2 acceptors | Two OH groups |
| DEG | 4C | 2 donors, 2 acceptors | Two OH groups |
| TEG | 4C | 2 donors, 2 acceptors | Two OH groups |
| Acetic acid | 1A | 1 site (dimerization) | Carboxylic acid |
| Formic acid | 1A | 1 site (dimerization) | Carboxylic acid |
| Amines (primary) | 3B | 2 donors, 1 acceptor | NH$_2$ group |
| CO$_2$ | Solvation only | 0 self-association sites | Lewis acid (electron acceptor) |

*Table 4.1: Recommended association schemes for common molecules in CPA.*

The choice of association scheme significantly affects the number of pure-component parameters. For a 2B molecule, there are two association parameters ($\varepsilon$ and $\beta$); for a 4C molecule with symmetric sites, the same two parameters apply but the equations are different due to the different number of sites.

### 4.4.3 Water: The 4C Scheme

Water is the most important associating molecule in process engineering. In the 4C scheme, each water molecule has four association sites:

- Sites $e_1$ and $e_2$ represent the two lone electron pairs (electron donors / hydrogen bond acceptors)
- Sites $H_1$ and $H_2$ represent the two hydrogen atoms (proton donors)

An $H$ site on one water molecule bonds with an $e$ site on another. The allowed bond pairs are $\{H_1 \leftrightarrow e_1, H_1 \leftrightarrow e_2, H_2 \leftrightarrow e_1, H_2 \leftrightarrow e_2\}$, while $H \leftrightarrow H$ and $e \leftrightarrow e$ bonds are forbidden.

With symmetric sites ($X_{H_1} = X_{H_2} \equiv X_H$ and $X_{e_1} = X_{e_2} \equiv X_e$), the site balance equations reduce to:

$$X_H = \frac{1}{1 + 2\rho X_e \Delta^{He}}$$

$$X_e = \frac{1}{1 + 2\rho X_H \Delta^{He}}$$

where $\Delta^{He}$ uses the single pair of association parameters $\varepsilon^{He}$ and $\beta^{He}$. These two coupled equations can be solved analytically by substituting one into the other.

### 4.4.4 Analytical Solutions for Simple Schemes

For the 2B scheme (one site of each type), the site balance equations give:

$$X_A = X_B = \frac{-1 + \sqrt{1 + 4\rho\Delta}}{2\rho\Delta}$$

For the 4C scheme with symmetric sites, the analytical solution is:

$$X_H = X_e = \frac{-1 + \sqrt{1 + 8\rho\Delta}}{4\rho\Delta}$$

These closed-form expressions are valuable for both understanding and computation — they eliminate the need for iterative solution of the site balance equations in pure-component calculations.

## 4.5 The Helmholtz Energy of Association

### 4.5.1 The General Expression

Wertheim's TPT1 gives the Helmholtz energy contribution from association as:

$$\frac{A^{\text{assoc}}}{nRT} = \sum_{i=1}^{c} x_i \sum_{A_i} \left(\ln X_{A_i} - \frac{X_{A_i}}{2} + \frac{1}{2}\right)$$

where the outer sum is over all components and the inner sum is over all association sites on component $i$. The terms have the following interpretation:

- $\ln X_{A_i}$: entropic contribution — there are fewer configurations when molecules are bonded
- $-X_{A_i}/2 + 1/2$: ensures proper normalization and removes double-counting of bonded pairs

### 4.5.2 Properties of the Association Energy

Several important properties follow from this expression:

1. **$A^{\text{assoc}} \leq 0$ always**: Association always lowers the free energy (it is a stabilizing interaction). Since $0 \leq X_A \leq 1$, we have $\ln X_A \leq 0$ and $-X_A/2 + 1/2 \geq 0$, with the logarithmic term dominating.

2. **$A^{\text{assoc}} = 0$ when $X_A = 1$ for all sites**: If no association occurs (all sites free), the association contribution vanishes.

3. **$A^{\text{assoc}}$ becomes more negative with increasing association**: As $X_A \to 0$ (complete association), $A^{\text{assoc}} \to -\infty$ in the logarithmic limit.

4. **Temperature dependence**: As temperature increases, $\Delta$ decreases, $X_A$ increases, and $A^{\text{assoc}}$ becomes less negative. This is consistent with hydrogen bonds being broken by thermal energy.

### 4.5.3 Pressure Contribution from Association

The association contribution to pressure is obtained by differentiation:

$$P^{\text{assoc}} = -\left(\frac{\partial A^{\text{assoc}}}{\partial V}\right)_{T,\mathbf{n}} = -\frac{nRT}{2} \sum_i x_i \sum_{A_i} \frac{1}{X_{A_i}} \frac{\partial X_{A_i}}{\partial V}$$

The derivative $\partial X_{A_i}/\partial V$ requires implicit differentiation of the site balance equations and is non-trivial — this is one of the computational challenges of CPA relative to pure cubic EoS.

## 4.6 Fugacity Coefficient from Association

### 4.6.1 The Chemical Potential Contribution

The association contribution to the chemical potential of component $i$ is:

$$\frac{\mu_i^{\text{assoc}}}{RT} = \sum_{A_i} \ln X_{A_i} + \rho \sum_k x_k \sum_{A_k} \frac{1}{X_{A_k}} \frac{\partial X_{A_k}}{\partial n_i}$$

The first term is the direct contribution from the sites on molecule $i$. The second term accounts for the fact that adding molecule $i$ to the mixture changes the association equilibrium of all other species.

### 4.6.2 Simplification Using the Site Balance

A remarkable simplification occurs when the site balance equation is used. Differentiating the Helmholtz energy while using the stationarity condition (that the site balance equations are satisfied), one obtains:

$$\frac{\mu_i^{\text{assoc}}}{RT} = \sum_{A_i} \ln X_{A_i} - \frac{1}{2} \rho \sum_k x_k \sum_{A_k} \left(\frac{1}{X_{A_k}} - 1\right) \frac{\partial \Delta^{A_k}}{\partial n_i}$$

In NeqSim, this is implemented using analytical derivatives of the association strength with respect to composition.

## 4.7 Solution of the Site Balance Equations

### 4.7.1 Successive Substitution

The simplest approach to solving the coupled site balance equations is successive substitution:

1. Initialize $X_{A_i}^{(0)} = 1$ for all sites (no association)
2. Update: $X_{A_i}^{(k+1)} = \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j}^{(k)} \Delta^{A_i B_j}}$
3. Repeat until convergence: $\max |X_{A_i}^{(k+1)} - X_{A_i}^{(k)}| < \varepsilon_{\text{tol}}$

This method is simple and usually converges in 3–10 iterations for typical conditions. However, it can be slow near critical points or at conditions of very strong association.

### 4.7.2 Newton's Method

For faster convergence, Newton's method can be applied to the residual form of the site balance equations:

$$R_{A_i} = X_{A_i} - \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}} = 0$$

The Jacobian matrix is:

$$J_{A_i, B_j} = \frac{\partial R_{A_i}}{\partial X_{B_j}} = \delta_{A_i B_j} + \frac{\rho x_j \Delta^{A_i B_j}}{\left(1 + \rho \sum_k x_k \sum_{C_k} X_{C_k} \Delta^{A_i C_k}\right)^2}$$

Newton's method typically converges in 2–4 iterations but requires forming and solving the linear system at each step.

### 4.7.3 Fully Implicit Approach

In the fully implicit approach used in NeqSim's advanced solvers, the site balance equations are solved simultaneously with the flash equations, rather than as an inner loop. This eliminates the nested iteration structure and can significantly improve overall convergence. Chapter 8 discusses this in detail.

## 4.8 Temperature and Density Dependence of Association

### 4.8.1 Effect of Temperature

As temperature increases:

1. The Boltzmann factor $\exp(\varepsilon/RT)$ decreases, reducing $\Delta$
2. The site fractions $X_A$ increase (fewer bonds)
3. The association free energy $A^{\text{assoc}}$ becomes less negative

This temperature dependence captures the well-known behavior that hydrogen bonds weaken and break at high temperatures. The predicted degree of association ($1 - X_A$) for water at 1 bar decreases from about 0.85 at 25°C to about 0.6 at 100°C and approaches zero near the critical temperature.

### 4.8.2 Effect of Density

As density increases:

1. The contact probability $g(\rho)$ increases, enhancing $\Delta$
2. The product $\rho \Delta$ increases, leading to more association
3. The competing effect of steric crowding limits the maximum bonding

The density dependence through $\rho g(\rho)$ is crucial for predicting the correct pressure dependence of association. Under high pressures, the liquid becomes denser, molecules are forced closer together, and the degree of association increases.

```python
from neqsim import jneqsim
import json

# Examine how association changes with temperature
for T_C in [25, 50, 100, 150, 200, 250, 300, 350]:
    fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + T_C, 10.0)
    fluid.addComponent("water", 1.0)
    fluid.setMixingRule(10)
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    # The density reflects the degree of association
    density = fluid.getDensity("kg/m3")
    print(f"T = {T_C:4d} C, rho = {density:.1f} kg/m3")
```

## Summary

Key points from this chapter:

- Wertheim's TPT provides a rigorous statistical mechanical framework for associating fluids
- Molecules are modeled with discrete association sites, each forming at most one bond
- The site balance equation $X_A = 1/(1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{AB})$ is the central equation
- The association strength $\Delta^{AB}$ depends on energy ($\varepsilon$), volume ($\beta$), density ($g(\rho)$), and temperature
- Association schemes (2B, 3B, 4C, etc.) specify the number and type of sites per molecule
- The Helmholtz energy from association is $A^{\text{assoc}}/nRT = \sum_i x_i \sum_{A_i} (\ln X_{A_i} - X_{A_i}/2 + 1/2)$
- Simple schemes have analytical solutions; complex mixtures require iterative solution

## Exercises

1. **Exercise 4.1:** For a pure 2B fluid with $\varepsilon/R = 2500$ K and $\beta = 0.02$, compute $X_A$ at $T = 300$ K for densities from 0 to 30 mol/L. Plot $X_A$ vs. density and interpret the result.

2. **Exercise 4.2:** Derive the analytical solution for $X_A$ in the 4C scheme. Show that for a pure component with symmetric sites, the four coupled equations reduce to a single quadratic.

3. **Exercise 4.3:** A mixture contains 70 mol% water (4C) and 30 mol% methanol (2B) at 25°C and 1 bar. Write out the complete set of site balance equations, identifying all allowed cross-association pairs.

4. **Exercise 4.4:** Calculate and plot the association contribution to the Helmholtz energy for pure water (4C scheme) as a function of temperature from 0°C to 374°C at a fixed density of 55.5 mol/L. Relate the result to the heat of vaporization.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 4.1: 01 Site Fraction Vs Density](figures/fig_ch04_01_site_fraction_vs_density.png)

*Figure 4.1: 01 Site Fraction Vs Density*

![Figure 4.2: 02 Delta Vs Temperature](figures/fig_ch04_02_delta_vs_temperature.png)

*Figure 4.2: 02 Delta Vs Temperature*

![Figure 4.3: Ex01 Xa Vs Rho](figures/fig_ch04_ex01_XA_vs_rho.png)

*Figure 4.3: Ex01 Xa Vs Rho*

![Figure 4.4: Ex02 2B Vs 4C](figures/fig_ch04_ex02_2B_vs_4C.png)

*Figure 4.4: Ex02 2B Vs 4C*

![Figure 4.5: Ex04 Helmholtz And Bonding](figures/fig_ch04_ex04_helmholtz_and_bonding.png)

*Figure 4.5: Ex04 Helmholtz And Bonding*


## Figures

![Figure 4.1: 04 Rdf Vs Packing](figures/fig_ch04_04_rdf_vs_packing.png)

*Figure 4.1: 04 Rdf Vs Packing*

![Figure 4.2: 05 Helmholtz Assoc Vs T](figures/fig_ch04_05_helmholtz_assoc_vs_T.png)

*Figure 4.2: 05 Helmholtz Assoc Vs T*

![Figure 4.3: 06 Association Schemes](figures/fig_ch04_06_association_schemes.png)

*Figure 4.3: 06 Association Schemes*

![Figure 4.4: 07 Solver Speedup Paperlab](figures/fig_ch04_07_solver_speedup_paperlab.png)

*Figure 4.4: 07 Solver Speedup Paperlab*

![Figure 4.5: 08 Site Reduction Jacobian](figures/fig_ch04_08_site_reduction_jacobian.png)

*Figure 4.5: 08 Site Reduction Jacobian*


## Figures

![Figure 4.1: 03 Cpa Vs Srk Water](figures/fig_ch04_03_cpa_vs_srk_water.png)

*Figure 4.1: 03 Cpa Vs Srk Water*
