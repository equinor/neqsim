# The CPA Equation of State: Complete Formulation

<!-- Chapter metadata -->
<!-- Notebooks: 01_cpa_components.ipynb, 02_fugacity_calculation.ipynb -->
<!-- Estimated pages: 22 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Write the complete CPA pressure equation combining SRK and association terms
2. Derive fugacity coefficients for CPA including all cross-derivatives
3. Compute thermodynamic derivatives (enthalpy, entropy, heat capacity) from CPA
4. Distinguish between standard CPA and simplified CPA (sCPA)
5. Explain the five pure-component parameters of CPA

## 5.1 The CPA Pressure Equation

### 5.1.1 Combining Cubic and Association Terms

The CPA equation of state writes the pressure as the sum of a classical cubic term and an association term:

$$P = P^{\text{cubic}} + P^{\text{assoc}}$$

Using the SRK equation as the cubic foundation:

$$P = \frac{RT}{V_m - b} - \frac{a(T)}{V_m(V_m + b)} - \frac{1}{2} \frac{RT}{V_m} \left(1 + \rho \frac{\partial \ln g}{\partial \rho}\right) \sum_i x_i \sum_{A_i} (1 - X_{A_i})$$

The first two terms are the standard SRK equation. The third term is the association contribution, where $g(\rho)$ is the radial distribution function at contact and $X_{A_i}$ are the site fractions satisfying the site balance equations from Chapter 4.

### 5.1.2 The Simplified CPA (sCPA)

The original CPA formulation used a hard-sphere reference for the radial distribution function, leading to a complex density dependence in the association term. Kontogeorgis et al. (1999) proposed a simplification where the RDF and its derivative are approximated as:

$$g(\rho) = \frac{1}{1 - 1.9\eta}, \quad \eta = \frac{b}{4V_m}$$

$$1 + \rho \frac{\partial \ln g}{\partial \rho} = \frac{1}{1 - 1.9\eta} + \frac{0.475\eta}{(1 - 1.9\eta)^2}$$

However, in many implementations including NeqSim's `SystemSrkCPAstatoil`, a further simplification is employed where the volume-dependent prefactor in the association pressure term is simplified. The resulting **simplified CPA** (sCPA) equation is:

$$P = \frac{RT}{V_m - b} - \frac{a(T)}{V_m(V_m + b)} - \frac{RT}{2V_m} \sum_i x_i \sum_{A_i} (1 - X_{A_i})$$

This form is computationally more efficient and produces results of comparable accuracy to the original CPA. The association strength in sCPA is:

$$\Delta^{A_i B_j} = g(\rho) \left[\exp\left(\frac{\varepsilon^{A_i B_j}}{RT}\right) - 1\right] b_{ij} \beta^{A_i B_j}$$

with $g(\rho) = 1/(1 - 1.9\eta)$ and $b_{ij} = (b_i + b_j)/2$.

### 5.1.3 Helmholtz Energy Formulation

It is often more convenient to work with the Helmholtz energy rather than the pressure equation. The total residual Helmholtz energy for CPA is:

$$A^{\text{res}} = A^{\text{SRK}} + A^{\text{assoc}}$$

where:

$$A^{\text{SRK}} = -nRT \ln\left(\frac{V - nb}{V}\right) - \frac{a}{b} \ln\left(\frac{V + nb}{V}\right)$$

$$A^{\text{assoc}} = nRT \sum_i x_i \sum_{A_i} \left(\ln X_{A_i} - \frac{X_{A_i}}{2} + \frac{1}{2}\right)$$

All thermodynamic properties can be derived from this Helmholtz energy by differentiation.

## 5.2 The Five Pure-Component Parameters

For an associating component in CPA, five parameters must be determined:

### 5.2.1 Physical Parameters from the Cubic Term

Three parameters come from the SRK part:

1. **$a_0$** (or equivalently $\Omega_a T_c^2/P_c$): the attractive energy parameter at the reference temperature. This primarily controls the vapor pressure.

2. **$b$**: the co-volume parameter. This primarily controls the liquid density.

3. **$c_1$** (the first coefficient of the alpha function): controls the temperature dependence of $a(T)$ through:

$$a(T) = a_0 \left[1 + c_1\left(1 - \sqrt{T_r}\right)\right]^2$$

Note that for CPA, the Soave correlation $m = f(\omega)$ is **not** used. Instead, $c_1$ is fitted directly to experimental data along with the other parameters.

### 5.2.2 Association Parameters

Two parameters describe the hydrogen bonding:

4. **$\varepsilon^{AB}$**: the association energy (in J/mol or K). This determines the temperature at which hydrogen bonds begin to break. Typical values range from 1000 to 3000 K (in $\varepsilon/k_B$).

5. **$\beta^{AB}$**: the association volume (dimensionless). This determines the probability that two molecules at contact distance will be in the correct orientation for bonding. Typical values range from 0.001 to 0.1.

### 5.2.3 Non-Associating Components

For non-associating components (alkanes, N$_2$, CO$_2$, etc.), the association parameters are zero and CPA reduces to SRK. The three cubic parameters are determined from:

$$a_0 = 0.42748 \frac{R^2 T_c^2}{P_c}, \quad b = 0.08664 \frac{RT_c}{P_c}, \quad c_1 = 0.48508 + 1.55171\omega - 0.15613\omega^2$$

Thus, non-associating components retain their SRK parameters unchanged — this backward compatibility was a key design goal of CPA.

## 5.3 Fugacity Coefficients

### 5.3.1 The Complete Expression

The fugacity coefficient of component $i$ in a CPA mixture has contributions from both the cubic and association terms:

$$\ln \varphi_i = \ln \varphi_i^{\text{SRK}} + \ln \varphi_i^{\text{assoc}}$$

The SRK contribution is the standard expression from Chapter 3:

$$\ln \varphi_i^{\text{SRK}} = \frac{b_i}{b_m}(Z - 1) - \ln(Z - B) - \frac{A}{B}\left(\frac{2\sum_j x_j a_{ij}}{a_m} - \frac{b_i}{b_m}\right) \ln\left(1 + \frac{B}{Z}\right)$$

The association contribution is:

$$\ln \varphi_i^{\text{assoc}} = \sum_{A_i} \left(\ln X_{A_i}\right) + \sum_k x_k \sum_{A_k} \frac{1}{X_{A_k}} \frac{\partial X_{A_k}}{\partial n_i} \bigg|_{T,V}$$

The second term arises because adding molecule $i$ to the mixture changes the site fractions of all species.

### 5.3.2 The Cross-Derivative Challenge

Computing $\partial X_{A_k}/\partial n_i$ requires implicit differentiation of the site balance equations. Differentiating:

$$X_{A_i} = \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}}$$

with respect to $n_i$ at constant $T$ and $V$ yields a system of linear equations:

$$\sum_k \sum_{B_k} M_{A_i, B_k} \frac{\partial X_{B_k}}{\partial n_j} = R_{A_i, j}$$

where the matrix $M$ and right-hand side $R$ involve the current values of $X$, $\Delta$, and their derivatives with respect to composition and volume.

In NeqSim, this linear system is solved analytically for simple association schemes and numerically for complex mixtures. The efficient computation of these derivatives is crucial for the performance of the flash algorithm and is one of the main contributions of the NeqSim implementation.

### 5.3.3 Verification by Numerical Differentiation

When implementing CPA, it is essential to verify the analytical fugacity coefficients against numerical differentiation:

$$\ln \varphi_i^{\text{numerical}} = \frac{1}{RT} \frac{\partial A^{\text{res}}}{\partial n_i} \bigg|_{T,V}^{\text{numerical}} \approx \frac{A^{\text{res}}(n_i + \delta) - A^{\text{res}}(n_i - \delta)}{2\delta \cdot RT}$$

This is a critical development practice used in NeqSim's test suite.

## 5.4 Thermodynamic Derivatives

### 5.4.1 Temperature Derivatives

The temperature derivative of the Helmholtz energy is needed for entropy and enthalpy:

$$\left(\frac{\partial A^{\text{assoc}}}{\partial T}\right)_{V,\mathbf{n}} = nR \sum_i x_i \sum_{A_i} \left(\ln X_{A_i} - \frac{X_{A_i}}{2} + \frac{1}{2}\right) + nRT \sum_i x_i \sum_{A_i} \frac{1}{X_{A_i}} \frac{\partial X_{A_i}}{\partial T}$$

The derivative $\partial X_{A_i}/\partial T$ is computed by differentiating the site balance equation with respect to temperature, which introduces the temperature derivative of $\Delta^{AB}$:

$$\frac{\partial \Delta^{AB}}{\partial T} = \frac{\partial g}{\partial T} \left[\exp\left(\frac{\varepsilon}{RT}\right) - 1\right] b\beta + g \cdot \left(-\frac{\varepsilon}{RT^2}\right) \exp\left(\frac{\varepsilon}{RT}\right) \cdot b\beta$$

### 5.4.2 Volume Derivatives

The volume derivative gives the association contribution to pressure:

$$P^{\text{assoc}} = -\left(\frac{\partial A^{\text{assoc}}}{\partial V}\right)_{T,\mathbf{n}} = -\frac{nRT}{V_m} \sum_i x_i \sum_{A_i} \frac{1}{X_{A_i}} \frac{\partial X_{A_i}}{\partial V} \cdot V_m$$

The volume derivative of $X_A$ involves:

$$\frac{\partial \Delta^{AB}}{\partial V} = \frac{\partial g}{\partial V} \left[\exp\left(\frac{\varepsilon}{RT}\right) - 1\right] b\beta$$

where $\partial g/\partial V$ depends on the choice of radial distribution function.

### 5.4.3 Second Derivatives

Second derivatives are needed for heat capacities, speed of sound, and other caloric properties:

$$\left(\frac{\partial^2 A^{\text{assoc}}}{\partial T^2}\right)_{V,\mathbf{n}}, \quad \left(\frac{\partial^2 A^{\text{assoc}}}{\partial V^2}\right)_{T,\mathbf{n}}, \quad \left(\frac{\partial^2 A^{\text{assoc}}}{\partial T \partial V}\right)_{\mathbf{n}}$$

These expressions are lengthy but follow directly from differentiating the site balance equations twice. The key challenge is maintaining analytical consistency — numerical errors in second derivatives propagate into property calculations and can cause convergence problems in flash algorithms.

## 5.5 Enthalpy and Heat Capacity Contributions

### 5.5.1 Residual Enthalpy from Association

The association contribution to the residual enthalpy is:

$$H^{\text{assoc}} = A^{\text{assoc}} + TS^{\text{assoc}} + PV - nRT$$

This simplifies to:

$$H^{\text{assoc}} = -T^2 \frac{\partial}{\partial T}\left(\frac{A^{\text{assoc}}}{T}\right)_{V,\mathbf{n}} + V P^{\text{assoc}} - nRT$$

The association enthalpy is negative (exothermic) because forming hydrogen bonds releases energy. Its magnitude depends on the degree of association: at low temperatures where most sites are bonded, $H^{\text{assoc}}$ is most negative.

### 5.5.2 Heat Capacity

The heat capacity contribution from association is significant for water and alcohols. The association contribution to $C_V$ is:

$$C_V^{\text{assoc}} = -T \left(\frac{\partial^2 A^{\text{assoc}}}{\partial T^2}\right)_{V,\mathbf{n}}$$

This captures the physical effect that breaking hydrogen bonds as temperature increases absorbs energy, contributing to the anomalously high heat capacity of water. CPA predictions of $C_P$ for water are significantly better than SRK because the association term accounts for the energy stored in the hydrogen-bond network.

## 5.6 The PR-CPA Variant

While the standard CPA uses SRK as the cubic term, a Peng–Robinson variant has also been developed:

$$P = \frac{RT}{V_m - b} - \frac{a(T)}{V_m^2 + 2bV_m - b^2} + P^{\text{assoc}}$$

PR-CPA generally provides better liquid density predictions due to the improved critical compressibility factor of the PR equation. However, SRK-CPA has a larger parameter database and is more widely used in the oil and gas industry.

NeqSim implements both variants:

```python
from neqsim import jneqsim

# SRK-CPA (standard, recommended)
fluid_srk = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.0)
fluid_srk.addComponent("water", 1.0)
fluid_srk.setMixingRule(10)

# PR-CPA variant
fluid_pr = jneqsim.thermo.system.SystemPrCPA(298.15, 1.0)
fluid_pr.addComponent("water", 1.0)
fluid_pr.setMixingRule(10)

# Compare
for fluid, name in [(fluid_srk, "SRK-CPA"), (fluid_pr, "PR-CPA")]:
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()
    rho = fluid.getDensity("kg/m3")
    print(f"{name}: density = {rho:.1f} kg/m3")
```

## 5.7 Implementation in NeqSim

### 5.7.1 Class Hierarchy

The CPA implementation in NeqSim follows a layered architecture with System, Phase, and Component levels:

**System level** (`neqsim.thermo.system`):
- `SystemSrkCPA` — base CPA with standard solver
- `SystemSrkCPAs` — simplified CPA
- `SystemSrkCPAstatoil` — recommended: sCPA with Equinor parameters
- `SystemSrkCPAstatoilFullyImplicit` — fully implicit Newton solver
- `SystemSrkCPAstatoilBroydenImplicit` — Broyden quasi-Newton
- `SystemSrkCPAstatoilAndersonMixing` — Anderson acceleration

**Phase level** (`neqsim.thermo.phase`):
- `PhaseSrkCPA` — SRK-CPA phase calculations
- `PhaseSrkCPAs` — simplified CPA phase

**Component level** (`neqsim.thermo.component`):
- `ComponentSrkCPA` — fugacity coefficient, association parameters

### 5.7.2 Parameter Database

NeqSim stores CPA parameters in its component database. For each associating component, the database contains:

- Cubic parameters: $T_c$, $P_c$, $\omega$, and the CPA-specific $a_0$, $b$, $c_1$
- Association parameters: $\varepsilon/R$ (in K), $\beta$ (dimensionless)
- Association scheme: specified as site counts

When a CPA system is created, NeqSim automatically loads the appropriate parameters:

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.0)
fluid.addComponent("water", 1.0)
fluid.addComponent("methanol", 0.5)
fluid.addComponent("methane", 2.0)
fluid.setMixingRule(10)  # CPA mixing rule with cross-association

# water and methanol get CPA parameters automatically
# methane gets standard SRK parameters
```

### 5.7.3 Mixing Rule 10

The mixing rule index 10 in NeqSim activates the CPA mixing rules with automatic handling of cross-association. This rule:

1. Uses van der Waals one-fluid rules for the cubic parameters ($a_m$, $b_m$)
2. Applies the CR-1 combining rule for cross-association parameters
3. Handles solvation between associating and non-self-associating species
4. Manages the site bookkeeping for complex multicomponent mixtures

## 5.8 Comparison: CPA vs. SRK for Pure Water

To demonstrate the improvement CPA provides, let us compare predictions for pure water:

```python
from neqsim import jneqsim

T_K = 373.15  # 100 C, boiling point of water at 1 atm

for ModelClass, name in [
    (jneqsim.thermo.system.SystemSrkEos, "SRK"),
    (jneqsim.thermo.system.SystemSrkCPAstatoil, "CPA"),
]:
    fluid = ModelClass(T_K, 1.01325)
    fluid.addComponent("water", 1.0)
    if "CPA" in name:
        fluid.setMixingRule(10)
    else:
        fluid.setMixingRule("classic")

    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    rho_liq = fluid.getPhase("aqueous").getDensity("kg/m3")
    print(f"{name}: liquid density at 100 C = {rho_liq:.1f} kg/m3")
    # Experimental: 958.4 kg/m3
```

## Summary

Key points from this chapter:

- CPA combines the SRK cubic EoS with Wertheim's association term: $P = P^{\text{SRK}} + P^{\text{assoc}}$
- Five pure-component parameters: $a_0$, $b$, $c_1$ (cubic) and $\varepsilon$, $\beta$ (association)
- Non-associating components retain their SRK parameters, ensuring backward compatibility
- Fugacity coefficients include cross-derivatives from the site balance equations
- Thermodynamic derivatives capture the energy content of the hydrogen-bond network
- NeqSim provides multiple solver variants (standard, fully implicit, Broyden, Anderson)
- `SystemSrkCPAstatoil` with mixing rule 10 is the recommended choice for industrial use

## Exercises

1. **Exercise 5.1:** Starting from the CPA Helmholtz energy, derive the expression for the association contribution to pressure. Verify that it reduces to zero when all $X_A = 1$.

2. **Exercise 5.2:** For pure water (4C scheme), compute and plot the five CPA parameters' sensitivity: vary each parameter by $\pm 10$% from its database value and calculate the vapor pressure at 100°C. Which parameter has the strongest influence?

3. **Exercise 5.3:** Using NeqSim, compare the predicted heat capacity ($C_P$) of pure water from 0°C to 200°C using SRK and CPA. Compare with NIST reference data. Explain the improvement in terms of the association contribution.

4. **Exercise 5.4:** Compute the fugacity coefficient of water in a methane–water mixture at 100°C and 100 bar using CPA. Decompose it into the SRK and association contributions.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 5.1: 01 Cpa Pressure Decomposition](figures/fig_ch05_01_cpa_pressure_decomposition.png)

*Figure 5.1: 01 Cpa Pressure Decomposition*

![Figure 5.2: 02 Compressibility Cpa Vs Srk](figures/fig_ch05_02_compressibility_cpa_vs_srk.png)

*Figure 5.2: 02 Compressibility Cpa Vs Srk*

![Figure 5.3: 03 Original Vs Scpa](figures/fig_ch05_03_original_vs_scpa.png)

*Figure 5.3: 03 Original Vs Scpa*

![Figure 5.4: Ex01 Pressure Decomp](figures/fig_ch05_ex01_pressure_decomp.png)

*Figure 5.4: Ex01 Pressure Decomp*

![Figure 5.5: Ex02 Water Density](figures/fig_ch05_ex02_water_density.png)

*Figure 5.5: Ex02 Water Density*

![Figure 5.6: Ex03 Eps Sensitivity](figures/fig_ch05_ex03_eps_sensitivity.png)

*Figure 5.6: Ex03 Eps Sensitivity*
