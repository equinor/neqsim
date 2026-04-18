# Classical Cubic Equations of State

<!-- Chapter metadata -->
<!-- Notebooks: 01_cubic_eos_comparison.ipynb, 02_volume_translation.ipynb -->
<!-- Estimated pages: 18 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Derive the van der Waals equation and identify its physical basis
2. Present the SRK and PR equations and explain the role of the alpha function
3. Apply volume translation to improve liquid density predictions
4. Compute fugacity coefficients from cubic equations of state
5. Explain why cubic EoS fail for associating fluids

## 3.1 The van der Waals Equation

### 3.1.1 Physical Basis

In 1873, Johannes Diderik van der Waals proposed the first equation of state that could describe both the gas and liquid phases:

$$P = \frac{RT}{V_m - b} - \frac{a}{V_m^2}$$

where $V_m$ is the molar volume, $a$ is the attractive parameter accounting for intermolecular attraction, and $b$ is the co-volume representing the finite size of molecules.

The physical interpretation is straightforward:

- The term $RT/(V_m - b)$ is a repulsive contribution — the available volume is reduced by the finite molecular size $b$
- The term $-a/V_m^2$ is an attractive contribution — molecules attract each other, reducing the pressure below the ideal gas value

Despite its simplicity, the van der Waals equation captures the essential physics of fluid behavior: it predicts a critical point, vapor–liquid coexistence, and the transition from gas-like to liquid-like behavior.

### 3.1.2 Critical Point Conditions

At the critical point, the first and second derivatives of pressure with respect to volume vanish simultaneously:

$$\left(\frac{\partial P}{\partial V_m}\right)_{T_c} = 0 \quad \text{and} \quad \left(\frac{\partial^2 P}{\partial V_m^2}\right)_{T_c} = 0$$

Applying these conditions to the van der Waals equation yields:

$$a = \frac{27 R^2 T_c^2}{64 P_c}, \quad b = \frac{RT_c}{8P_c}, \quad Z_c = \frac{P_c V_{m,c}}{RT_c} = \frac{3}{8} = 0.375$$

The predicted critical compressibility factor of 0.375 is significantly higher than experimental values for most substances (typically 0.23–0.29), indicating that the van der Waals equation overpredicts the critical volume.

### 3.1.3 The Cubic Nature

The van der Waals equation can be rewritten as a cubic polynomial in $V_m$:

$$V_m^3 - \left(b + \frac{RT}{P}\right) V_m^2 + \frac{a}{P} V_m - \frac{ab}{P} = 0$$

This cubic nature is fundamental — it allows the equation to have up to three real roots, corresponding to the vapor, unstable, and liquid volumes. The cubic form also enables analytical solutions and efficient root-finding algorithms.

## 3.2 The Soave–Redlich–Kwong (SRK) Equation

### 3.2.1 Development

In 1949, Redlich and Kwong modified the van der Waals attraction term to include a temperature dependence:

$$P = \frac{RT}{V_m - b} - \frac{a}{T^{1/2} V_m(V_m + b)}$$

While this improved predictions at high temperatures, it still could not accurately reproduce vapor pressures. In 1972, Soave made the crucial innovation of replacing the fixed $T^{-1/2}$ dependence with a component-specific alpha function:

$$P = \frac{RT}{V_m - b} - \frac{a \cdot \alpha(T)}{V_m(V_m + b)}$$

where:

$$\alpha(T) = \left[1 + m\left(1 - \sqrt{T_r}\right)\right]^2$$

and $m$ is correlated with the acentric factor $\omega$:

$$m = 0.48508 + 1.55171\omega - 0.15613\omega^2$$

The parameters are:

$$a = 0.42748 \frac{R^2 T_c^2}{P_c}, \quad b = 0.08664 \frac{RT_c}{P_c}$$

The SRK equation accurately reproduces vapor pressures for a wide range of non-polar and slightly polar substances, making it the standard model in the oil and gas industry for decades. It is also the cubic foundation of the CPA equation of state.

### 3.2.2 Fugacity Coefficient

The fugacity coefficient for component $i$ in a mixture described by the SRK equation is:

$$\ln \varphi_i = \frac{b_i}{b_m}(Z - 1) - \ln(Z - B) - \frac{A}{B}\left(\frac{2\sum_j x_j a_{ij}}{a_m} - \frac{b_i}{b_m}\right) \ln\left(1 + \frac{B}{Z}\right)$$

where:

$$A = \frac{a_m P}{R^2 T^2}, \quad B = \frac{b_m P}{RT}, \quad Z = \frac{PV_m}{RT}$$

and the mixture parameters use van der Waals one-fluid mixing rules:

$$a_m = \sum_i \sum_j x_i x_j a_{ij}, \quad b_m = \sum_i x_i b_i$$

$$a_{ij} = \sqrt{a_i a_j}(1 - k_{ij})$$

The binary interaction parameter $k_{ij}$ is the single adjustable parameter available for tuning VLE predictions.

## 3.3 The Peng–Robinson (PR) Equation

### 3.3.1 Formulation

In 1976, Peng and Robinson proposed a modification that improved liquid density predictions:

$$P = \frac{RT}{V_m - b} - \frac{a \cdot \alpha(T)}{V_m(V_m + b) + b(V_m - b)}$$

$$P = \frac{RT}{V_m - b} - \frac{a \cdot \alpha(T)}{V_m^2 + 2bV_m - b^2}$$

The parameters are:

$$a = 0.45724 \frac{R^2 T_c^2}{P_c}, \quad b = 0.07780 \frac{RT_c}{P_c}$$

$$m = 0.37464 + 1.54226\omega - 0.26992\omega^2$$

The predicted critical compressibility factor is $Z_c = 0.3074$, closer to experimental values than both van der Waals (0.375) and SRK (0.333).

### 3.3.2 SRK vs. PR: When Does It Matter?

The choice between SRK and PR is sometimes debated, but in practice the differences are relatively small for vapor–liquid equilibrium. The main differences are:

| Property | SRK | PR |
|----------|-----|-----|
| $Z_c$ | 0.333 | 0.307 |
| Liquid density | Overpredicts ~5–15% | Overpredicts ~2–8% |
| Vapor pressure | Excellent | Excellent |
| VLE K-factors | Excellent | Excellent |
| Heavy hydrocarbon density | Poor without correction | Better, still needs correction |

*Table 3.1: Comparison of SRK and PR equations.*

For CPA, the SRK form is used as the cubic foundation because CPA was originally developed with SRK. A PR-based variant (PR-CPA) exists but is less commonly used in the oil and gas industry.

## 3.4 Alpha Functions and Temperature Dependence

### 3.4.1 The Soave Alpha Function

The original Soave alpha function:

$$\alpha(T_r) = \left[1 + m(1 - \sqrt{T_r})\right]^2$$

where $T_r = T/T_c$ is the reduced temperature, works well for $T_r < 1$ but can exhibit unphysical behavior at high supercritical temperatures (negative values of $\alpha$ for components with high $\omega$).

### 3.4.2 The Mathias–Copeman Alpha Function

For improved accuracy, particularly for polar and associating components, the Mathias–Copeman (1983) alpha function provides additional flexibility:

$$\alpha(T_r) = \left[1 + c_1(1 - \sqrt{T_r}) + c_2(1 - \sqrt{T_r})^2 + c_3(1 - \sqrt{T_r})^3\right]^2 \quad \text{for } T_r \leq 1$$

$$\alpha(T_r) = \left[1 + c_1(1 - \sqrt{T_r})\right]^2 \quad \text{for } T_r > 1$$

The three parameters $c_1$, $c_2$, $c_3$ are fitted to experimental vapor pressure data. For non-polar molecules, setting $c_2 = c_3 = 0$ recovers the standard Soave form.

In the context of CPA, the alpha function is particularly important for the energy parameter of the cubic term. For associating components, the effective temperature dependence of $a(T)$ captures both the changing dispersion interactions and, to some extent, compensates for simplified treatment of the reference term.

### 3.4.3 The Twu Alpha Function

Twu et al. (1991) proposed an alpha function that is guaranteed to be positive and monotonically decreasing:

$$\alpha(T_r) = T_r^{N(M-1)} \exp\left[L(1 - T_r^{NM})\right]$$

This form has better thermodynamic consistency at high temperatures and is used in some CPA implementations.

## 3.5 Volume Translation

### 3.5.1 The Péneloux Correction

Both SRK and PR systematically overpredict liquid molar volumes. Péneloux et al. (1982) showed that a simple volume shift can correct this without affecting vapor–liquid equilibrium:

$$V_m^{\text{corrected}} = V_m^{\text{EoS}} - c$$

where $c$ is the volume translation parameter. For component $i$:

$$c_i = 0.40768 \frac{RT_{c,i}}{P_{c,i}} \left(0.29441 - Z_{\text{RA},i}\right)$$

where $Z_{\text{RA}}$ is the Rackett compressibility factor.

The remarkable property of the Péneloux correction is that it does not change the fugacity coefficients — the VLE predictions remain identical, while liquid densities improve significantly.

### 3.5.2 Temperature-Dependent Volume Translation

For improved accuracy over wide temperature ranges, temperature-dependent volume translation can be used:

$$c(T) = c_0 + c_1(T - T_{\text{ref}})$$

However, care must be taken: temperature-dependent volume translation can introduce thermodynamic inconsistencies (crossing of isotherms in the $P$-$V$ diagram), so constant volume translation is generally preferred.

### 3.5.3 Volume Translation in NeqSim

NeqSim supports volume translation for both SRK and CPA:

```python
from neqsim import jneqsim

# Without volume translation
fluid_no_vt = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.01325)
fluid_no_vt.addComponent("water", 1.0)
fluid_no_vt.setMixingRule(10)
ops1 = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid_no_vt)
ops1.TPflash()
fluid_no_vt.initProperties()

# With Peneloux volume translation
fluid_vt = jneqsim.thermo.system.SystemSrkCPAstatoil(298.15, 1.01325)
fluid_vt.addComponent("water", 1.0)
fluid_vt.setMixingRule(10)
fluid_vt.useVolumeCorrection(True)
ops2 = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid_vt)
ops2.TPflash()
fluid_vt.initProperties()

print(f"Without volume translation: {fluid_no_vt.getDensity('kg/m3'):.1f} kg/m3")
print(f"With volume translation: {fluid_vt.getDensity('kg/m3'):.1f} kg/m3")
print(f"Experimental (water at 25C): 997.0 kg/m3")
```

## 3.6 Mixing Rules

### 3.6.1 Classical van der Waals Mixing Rules

The standard (one-fluid) mixing rules for cubic EoS are:

$$a_m = \sum_i \sum_j x_i x_j \sqrt{a_i a_j} (1 - k_{ij})$$

$$b_m = \sum_i x_i b_i$$

The binary interaction parameter $k_{ij}$ adjusts the geometric mean combining rule for the attractive parameter. For hydrocarbon–hydrocarbon pairs, $k_{ij}$ is typically small (0–0.05). For CO$_2$–hydrocarbon pairs, larger values (0.10–0.15) are needed. For water–hydrocarbon pairs, very large values would be needed, and even then the predictions are poor — this is precisely where CPA adds value.

### 3.6.2 Limitations for Asymmetric Mixtures

The van der Waals mixing rules assume that the interaction between unlike molecules can be described by a simple geometric mean. This works well for mixtures of similar molecules but fails for highly asymmetric mixtures such as:

- Hydrogen-bonding systems (water + hydrocarbon)
- Size-asymmetric systems (methane + heavy hydrocarbons)
- Mixtures with strong specific interactions (CO$_2$ + water)

More sophisticated mixing rules (Wong–Sandler, MHV2, LCVM) have been developed to address these limitations, but they add complexity and additional parameters. CPA takes a different approach: keep the simple van der Waals mixing rules for the cubic part, but add the association term to explicitly account for hydrogen bonding.

## 3.7 Limitations of Cubic EoS for Associating Systems

To motivate the need for CPA, let us quantify the failure of classical cubic EoS for associating systems.

### 3.7.1 Water Vapor Pressure

Pure water is a severe test for any EoS. The vapor pressure curve spans from 0.006 bar at 0°C to 220.6 bar at the critical point (374°C). While SRK can reproduce the vapor pressure reasonably well with the standard alpha function, the liquid density is overpredicted by 15–20%. This is because the SRK parameters are forced to simultaneously capture the strong hydrogen-bonding interactions (through $a$) and the molecular size (through $b$), but two parameters are insufficient to describe both dispersion and association.

### 3.7.2 Water Content of Natural Gas

The water content of natural gas is a critical parameter for pipeline design and hydrate prevention. Experimental data show that the water content of methane at 50°C and 100 bar is approximately 0.0015 mole fraction. SRK with an optimized $k_{ij}$ predicts 0.003–0.005, overestimating by a factor of 2–3.

The reason is clear: SRK does not know that water molecules in the liquid phase are hydrogen-bonded, which dramatically reduces their chemical potential (and hence fugacity) relative to what a non-associating model would predict. The water molecules are "held" in the liquid phase by hydrogen bonds, reducing their tendency to escape into the gas phase.

### 3.7.3 Mutual Solubilities

The water–n-alkane mutual solubilities exhibit characteristic behavior:

- The solubility of water in alkanes decreases with alkane chain length
- The solubility of alkanes in water decreases much more steeply with chain length
- Both solubilities have a minimum as a function of temperature

Classical cubic EoS with a single $k_{ij}$ cannot reproduce these trends because $k_{ij}$ is essentially a constant correction that cannot capture the temperature-dependent effects of association. CPA resolves this by explicitly accounting for the hydrogen-bond network in the aqueous phase.

## Summary

Key points from this chapter:

- Cubic EoS (van der Waals, SRK, PR) describe PVT behavior using two parameters derived from critical properties
- The Soave alpha function enables accurate vapor pressure reproduction
- Volume translation corrects liquid density without affecting VLE
- Classical mixing rules with $k_{ij}$ work well for non-polar systems
- Cubic EoS fundamentally fail for associating systems because they cannot distinguish dispersion from hydrogen-bonding interactions
- CPA addresses this by adding Wertheim's association term to SRK

## Exercises

1. **Exercise 3.1:** Using NeqSim, compute the PVT surface for pure methane using SRK at temperatures from $-160°C$ to $100°C$ and pressures from 1 to 200 bar. Plot isotherms on a $P$-$V_m$ diagram and identify the two-phase region.

2. **Exercise 3.2:** Compare the predicted liquid density of n-heptane from SRK, PR, and SRK with Péneloux volume translation at 25°C and pressures from 1 to 500 bar. Plot against NIST data.

3. **Exercise 3.3:** For the system CO$_2$–water at 25°C, compute VLE using SRK with $k_{ij} = 0$, $0.1$, and $0.2$. Compare the predicted CO$_2$ solubility in water with experimental data. Can any single $k_{ij}$ value give satisfactory results?

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 3.1: 01 Vdw Isotherms](figures/fig_ch03_01_vdw_isotherms.png)

*Figure 3.1: 01 Vdw Isotherms*

![Figure 3.2: 02 Alpha Functions](figures/fig_ch03_02_alpha_functions.png)

*Figure 3.2: 02 Alpha Functions*

![Figure 3.3: 03 Srk Pr Methane Density](figures/fig_ch03_03_srk_pr_methane_density.png)

*Figure 3.3: 03 Srk Pr Methane Density*

![Figure 3.4: 04 Volume Translation](figures/fig_ch03_04_volume_translation.png)

*Figure 3.4: 04 Volume Translation*
