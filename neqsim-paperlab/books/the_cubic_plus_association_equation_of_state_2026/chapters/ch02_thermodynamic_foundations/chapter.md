# Thermodynamic Foundations

<!-- Chapter metadata -->
<!-- Notebooks: 01_fugacity_calculation.ipynb, 02_phase_equilibrium.ipynb -->
<!-- Estimated pages: 18 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Derive the conditions for thermodynamic equilibrium from the Gibbs energy
2. Relate fugacity and fugacity coefficients to equations of state
3. Formulate the phase equilibrium problem for vapor–liquid and liquid–liquid systems
4. Compute fugacity coefficients from pressure-explicit equations of state using NeqSim

## 2.1 Fundamental Thermodynamic Relations

Before presenting the CPA equation of state, we must establish the thermodynamic framework within which it operates. This chapter reviews the essential concepts of chemical potential, fugacity, and phase equilibrium that underpin all equation of state calculations.

### 2.1.1 The Gibbs Energy and Chemical Potential

For a multicomponent system at constant temperature $T$ and pressure $P$, the Gibbs energy is the fundamental potential:

$$G = G(T, P, n_1, n_2, \ldots, n_c)$$

where $n_i$ is the number of moles of component $i$ and $c$ is the number of components. The **chemical potential** of component $i$ is defined as the partial molar Gibbs energy:

$$\mu_i = \left( \frac{\partial G}{\partial n_i} \right)_{T,P,n_{j \neq i}}$$

The total Gibbs energy can be written as:

$$G = \sum_{i=1}^{c} n_i \mu_i$$

and the Gibbs–Duhem equation constrains the chemical potentials:

$$\sum_{i=1}^{c} n_i \, d\mu_i = -S \, dT + V \, dP$$

At constant temperature and pressure, this simplifies to:

$$\sum_{i=1}^{c} n_i \, d\mu_i = 0$$

which is a crucial consistency check for any thermodynamic model.

### 2.1.2 Fugacity and the Fugacity Coefficient

While the chemical potential is the thermodynamically rigorous quantity, it has the inconvenience of approaching $-\infty$ as the concentration of a component approaches zero. Lewis (1901) introduced the **fugacity** $f_i$ as an alternative measure that behaves like a corrected partial pressure:

$$\mu_i = \mu_i^0 + RT \ln \frac{f_i}{f_i^0}$$

The fugacity has units of pressure and is defined such that it equals the partial pressure for an ideal gas:

$$\lim_{P \to 0} \frac{f_i}{y_i P} = 1$$

The **fugacity coefficient** $\varphi_i$ is defined as the ratio of fugacity to the product of mole fraction and pressure:

$$\varphi_i = \frac{f_i}{x_i P}$$

For an ideal gas, $\varphi_i = 1$. Deviations from unity reflect intermolecular interactions. The fugacity coefficient is related to the equation of state through:

$$\ln \varphi_i = \frac{1}{RT} \int_V^{\infty} \left[ \left( \frac{\partial P}{\partial n_i} \right)_{T,V,n_{j \neq i}} - \frac{RT}{V} \right] dV - \ln Z$$

where $Z = PV/(nRT)$ is the compressibility factor. This integral is the fundamental connection between an equation of state and phase equilibrium calculations — every equation of state must provide $(\partial P/\partial n_i)_{T,V}$ to be useful for phase equilibrium.

## 2.2 Phase Equilibrium Conditions

### 2.2.1 The Equilibrium Criterion

A closed system at constant $T$ and $P$ reaches equilibrium when the total Gibbs energy is minimized. For a system with $\pi$ phases, this leads to the conditions:

$$T^{(1)} = T^{(2)} = \cdots = T^{(\pi)} \quad \text{(thermal equilibrium)}$$

$$P^{(1)} = P^{(2)} = \cdots = P^{(\pi)} \quad \text{(mechanical equilibrium)}$$

$$\mu_i^{(1)} = \mu_i^{(2)} = \cdots = \mu_i^{(\pi)} \quad \text{(chemical equilibrium, } i = 1, \ldots, c\text{)}$$

In terms of fugacities, the chemical equilibrium condition becomes:

$$f_i^{(1)} = f_i^{(2)} = \cdots = f_i^{(\pi)} \quad \text{for all } i$$

or equivalently in terms of fugacity coefficients:

$$x_i^{(1)} \varphi_i^{(1)} = x_i^{(2)} \varphi_i^{(2)} = \cdots \quad \text{for all } i$$

### 2.2.2 Vapor–Liquid Equilibrium (VLE)

For a two-phase vapor–liquid system, the equilibrium condition gives:

$$y_i \varphi_i^V = x_i \varphi_i^L \quad \text{for } i = 1, \ldots, c$$

The **K-factor** or equilibrium ratio is:

$$K_i = \frac{y_i}{x_i} = \frac{\varphi_i^L}{\varphi_i^V}$$

These K-factors are the central quantities in flash calculations, which determine the amounts and compositions of coexisting phases at given conditions.

### 2.2.3 Liquid–Liquid Equilibrium (LLE)

For liquid–liquid systems — of particular importance for CPA applications involving water and hydrocarbons — the equilibrium condition is:

$$x_i^{L1} \varphi_i^{L1} = x_i^{L2} \varphi_i^{L2} \quad \text{for } i = 1, \ldots, c$$

LLE calculations are often more numerically challenging than VLE because the two liquid phases can have very different compositions (e.g., water-rich vs. hydrocarbon-rich) and the objective function has multiple local minima.

### 2.2.4 Three-Phase Equilibrium (VLLE)

Many systems relevant to CPA applications exhibit three-phase vapor–liquid–liquid equilibrium (VLLE). For example, a natural gas–water system at moderate pressures can have a vapor phase, a hydrocarbon-rich liquid phase, and a water-rich liquid phase coexisting simultaneously.

The equilibrium conditions are:

$$f_i^V = f_i^{L1} = f_i^{L2} \quad \text{for all } i$$

NeqSim handles multi-phase equilibrium through the Michelsen stability analysis and successive flash calculations:

```python
from neqsim import jneqsim

# Three-phase system: methane + n-hexane + water
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 50.0)
fluid.addComponent("methane", 0.6)
fluid.addComponent("n-hexane", 0.3)
fluid.addComponent("water", 0.1)
fluid.setMixingRule(10)

# Enable multi-phase check
fluid.setMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Number of phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    print(f"Phase {i}: {phase.getType()}, density = {phase.getDensity('kg/m3'):.1f} kg/m3")
```

## 2.3 The Flash Problem

### 2.3.1 Isothermal Flash (TP Flash)

The most common phase equilibrium calculation is the isothermal (TP) flash: given the overall composition $z_i$, temperature $T$, and pressure $P$, determine the number of phases, their amounts, and their compositions.

For a two-phase system, the flash problem combines the equilibrium conditions with material balances:

$$z_i = \beta y_i + (1 - \beta) x_i$$

where $\beta$ is the vapor fraction. The **Rachford–Rice equation** eliminates the individual phase compositions:

$$\sum_{i=1}^{c} \frac{z_i (K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

This single equation in $\beta$ is solved iteratively, with the K-factors updated at each step using the equation of state. The Michelsen (1982) algorithm provides a robust and efficient solution procedure.

### 2.3.2 Stability Analysis

Before performing a flash calculation, it is essential to determine whether the system is stable as a single phase or whether it will split into multiple phases. Michelsen's (1982) **tangent plane distance** (TPD) criterion provides a rigorous test:

$$\text{TPD}(\mathbf{w}) = \sum_{i=1}^{c} w_i \left[ \ln w_i + \ln \varphi_i(\mathbf{w}) - \ln z_i - \ln \varphi_i(\mathbf{z}) \right]$$

If $\text{TPD}(\mathbf{w}) < 0$ for any trial composition $\mathbf{w}$, the single-phase solution is unstable and the system will split.

For CPA systems, the stability analysis must account for the dependence of the association term on composition — the site fractions $X_A$ change with composition and affect the fugacity coefficients. This coupling makes CPA stability analysis more computationally expensive than for classical cubic EoS.

### 2.3.3 Other Flash Specifications

While TP flash is the most common, other specifications are used in process simulation:

| Flash Type | Given | Find |
|-----------|-------|------|
| TP flash | $T$, $P$, $\mathbf{z}$ | $\beta$, $\mathbf{x}$, $\mathbf{y}$ |
| PH flash | $P$, $H$, $\mathbf{z}$ | $T$, $\beta$, $\mathbf{x}$, $\mathbf{y}$ |
| PS flash | $P$, $S$, $\mathbf{z}$ | $T$, $\beta$, $\mathbf{x}$, $\mathbf{y}$ |
| TV flash | $T$, $V$, $\mathbf{z}$ | $P$, $\beta$, $\mathbf{x}$, $\mathbf{y}$ |
| Bubble point | $T$ (or $P$), $\mathbf{x}$ | $P$ (or $T$), $\mathbf{y}$ |
| Dew point | $T$ (or $P$), $\mathbf{y}$ | $P$ (or $T$), $\mathbf{x}$ |

*Table 2.1: Common flash calculation specifications.*

NeqSim supports all of these specifications with CPA:

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("water", 0.1)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)

# Bubble point calculation
ops.bubblePointPressureFlash(False)
print(f"Bubble point pressure: {fluid.getPressure('bara'):.2f} bara")

# Dew point calculation
ops.dewPointTemperatureFlash()
print(f"Dew point temperature: {fluid.getTemperature('C'):.2f} C")
```

## 2.4 Thermodynamic Derivatives and Caloric Properties

### 2.4.1 Residual Properties

The residual Helmholtz energy $A^{\text{res}}$ contains all the information needed to compute thermodynamic properties. For a pressure-explicit EoS $P(T, V, \mathbf{n})$, the residual Helmholtz energy is:

$$A^{\text{res}}(T, V, \mathbf{n}) = -\int_{\infty}^{V} \left[ P - \frac{nRT}{V'} \right] dV'$$

All thermodynamic properties can be derived from $A^{\text{res}}$ and its derivatives:

$$P = -\left(\frac{\partial A}{\partial V}\right)_{T,\mathbf{n}}$$

$$S^{\text{res}} = -\left(\frac{\partial A^{\text{res}}}{\partial T}\right)_{V,\mathbf{n}}$$

$$\mu_i^{\text{res}} = \left(\frac{\partial A^{\text{res}}}{\partial n_i}\right)_{T,V,n_{j \neq i}}$$

### 2.4.2 Enthalpy, Entropy, and Heat Capacity

The residual enthalpy and entropy are essential for process simulation (heat exchanger design, compressor work, etc.):

$$H^{\text{res}} = A^{\text{res}} + TS^{\text{res}} + PV - nRT$$

$$C_P^{\text{res}} = C_V^{\text{res}} + T \frac{(\partial P / \partial T)_V^2}{(\partial P / \partial V)_T} + nR$$

For CPA, these derivatives include contributions from both the cubic and association terms, which must be computed consistently. The association contribution to enthalpy is particularly important because the degree of hydrogen bonding changes with temperature — breaking hydrogen bonds absorbs energy, contributing to the anomalously high heat capacity of water.

### 2.4.3 Speed of Sound and Compressibility

The speed of sound is important for flow measurement and pipeline design:

$$w = \sqrt{\frac{C_P}{C_V} \cdot \frac{V^2}{M} \cdot \left(-\frac{\partial P}{\partial V}\right)_T}$$

where $M$ is the molar mass. CPA provides improved predictions of the speed of sound in associating fluids because it correctly captures the density and compressibility effects of hydrogen bonding.

## 2.5 Property Initialization in NeqSim

A critical practical point when using NeqSim for property calculations: after any flash calculation, you must call `fluid.initProperties()` before reading physical and transport properties. This initializes both thermodynamic properties (from the EoS) and physical properties (viscosity, thermal conductivity, surface tension):

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(323.15, 50.0)
fluid.addComponent("water", 0.3)
fluid.addComponent("methane", 0.7)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()

# CRITICAL: Must call initProperties() after flash
fluid.initProperties()

# Now we can read all properties
print(f"Density: {fluid.getDensity('kg/m3'):.2f} kg/m3")
print(f"Enthalpy: {fluid.getEnthalpy('J/mol'):.1f} J/mol")
print(f"Entropy: {fluid.getEntropy('J/molK'):.3f} J/(mol·K)")
print(f"Cp: {fluid.getCp('J/molK'):.2f} J/(mol·K)")
print(f"Speed of sound: {fluid.getSoundSpeed():.1f} m/s")
```

## Summary

Key points from this chapter:

- Phase equilibrium is governed by equality of fugacities (or chemical potentials) across all phases
- Fugacity coefficients are computed from the equation of state via an integral over volume
- The flash problem determines the number, amounts, and compositions of coexisting phases
- Stability analysis (Michelsen's TPD criterion) must precede flash calculations
- For CPA, the association term couples into all thermodynamic derivatives
- Always call `fluid.initProperties()` after a flash calculation in NeqSim before reading properties

## Exercises

1. **Exercise 2.1:** Derive the expression for the fugacity coefficient of component $i$ in a mixture described by the van der Waals equation of state.

2. **Exercise 2.2:** Using NeqSim, compute the compressibility factor $Z$ for pure water vapor at 200°C and pressures from 1 to 100 bar using CPA. Compare with ideal gas ($Z = 1$) and SRK predictions.

3. **Exercise 2.3:** Set up a methane–water system and compute K-factors at 50°C for pressures from 10 to 200 bar. Plot $K_{\text{water}}$ vs. pressure and explain the trend physically.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 2.1: 01 Gibbs Mixing Phase Diagram](figures/fig_ch02_01_gibbs_mixing_phase_diagram.png)

*Figure 2.1: 01 Gibbs Mixing Phase Diagram*

![Figure 2.2: 02 Fugacity Coefficient](figures/fig_ch02_02_fugacity_coefficient.png)

*Figure 2.2: 02 Fugacity Coefficient*

![Figure 2.3: 03 Helmholtz Decomposition](figures/fig_ch02_03_helmholtz_decomposition.png)

*Figure 2.3: 03 Helmholtz Decomposition*

![Figure 2.4: Ex01 Fugacity](figures/fig_ch02_ex01_fugacity.png)

*Figure 2.4: Ex01 Fugacity*


## Figures

![Figure 2.1: Ex03 Gibbs Mixing](figures/fig_ch02_ex03_gibbs_mixing.png)

*Figure 2.1: Ex03 Gibbs Mixing*
