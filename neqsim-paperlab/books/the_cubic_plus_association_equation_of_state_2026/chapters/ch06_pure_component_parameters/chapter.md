# Pure Component Parameters

<!-- Chapter metadata -->
<!-- Notebooks: 01_parameter_regression.ipynb, 02_parameter_sensitivity.ipynb -->
<!-- Estimated pages: 16 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Explain the parameter regression methodology for CPA
2. Identify the objective function and weighting strategies used in fitting
3. Interpret the physical meaning and typical ranges of CPA parameters
4. Assess parameter sensitivity and correlation
5. Use pre-fitted CPA parameters from the NeqSim database

## 6.1 The Parameter Estimation Problem

### 6.1.1 What Must Be Fitted?

For an associating component modeled with CPA, five parameters must be determined simultaneously: $a_0$, $b$, $c_1$, $\varepsilon/R$, and $\beta$. Unlike classical cubic EoS where all three parameters are fixed by critical properties and the acentric factor, CPA parameters are obtained by regression against experimental data.

The reason critical property-based correlations are insufficient is that the association parameters ($\varepsilon$, $\beta$) introduce additional degrees of freedom that cannot be determined from $T_c$, $P_c$, and $\omega$ alone. The association energy and volume affect properties in ways that are coupled to the cubic parameters — for example, both $a_0$ and $\varepsilon$ affect the vapor pressure, and both $b$ and $\beta$ affect the liquid density.

### 6.1.2 Choice of Experimental Data

The standard approach uses two types of experimental data for pure components:

1. **Saturated vapor pressure** $P^{\text{sat}}(T)$: sensitive to $a_0$, $c_1$, and $\varepsilon$
2. **Saturated liquid density** $\rho^L(T)$: sensitive to $b$ and, to a lesser extent, $\varepsilon$ and $\beta$

The objective function for parameter regression is typically:

$$F_{\text{obj}} = \sum_{k=1}^{N_P} \left(\frac{P_k^{\text{calc}} - P_k^{\text{exp}}}{P_k^{\text{exp}}}\right)^2 + w_\rho \sum_{k=1}^{N_\rho} \left(\frac{\rho_k^{\text{calc}} - \rho_k^{\text{exp}}}{\rho_k^{\text{exp}}}\right)^2$$

where $w_\rho$ is a weighting factor that balances the relative importance of vapor pressure and density data. A typical choice is $w_\rho = 1$, giving equal weight to relative errors in both properties.

### 6.1.3 Temperature Range for Fitting

The temperature range for fitting is critical. The recommended practice is:

- **Lower bound**: $T_r \approx 0.5$, or the lowest temperature for which reliable data exist
- **Upper bound**: $T_r \approx 0.9$, avoiding the near-critical region where CPA (like all cubic EoS) is less accurate
- **Number of points**: at least 10–15 data points, uniformly spaced in reduced temperature

Fitting over too narrow a temperature range leads to parameters that extrapolate poorly. Including data too close to the critical point forces the parameters to compromise accuracy in the subcritical region.

## 6.2 Regression Methodology

### 6.2.1 Optimization Algorithm

The objective function for CPA parameter estimation is nonlinear and may have multiple local minima. A robust optimization strategy involves:

1. **Grid search**: Evaluate the objective function on a coarse grid in ($\varepsilon$, $\beta$) space (the most influential parameters), using the other three parameters optimized at each grid point
2. **Local optimization**: Use Levenberg–Marquardt or simplex (Nelder–Mead) algorithms to refine from the best grid points
3. **Multi-start**: Run local optimization from multiple initial points to increase confidence in the global optimum

The grid search in ($\varepsilon$, $\beta$) is motivated by the observation that these association parameters have the strongest effect on the objective function landscape and create the most severe non-convexity.

### 6.2.2 Initial Estimates

Good initial estimates accelerate convergence:

- $a_0$ and $b$: start with SRK values from critical properties
- $c_1$: start with $m(\omega)$ from the Soave correlation
- $\varepsilon/R$: literature values for similar molecules (1500–3000 K for OH groups)
- $\beta$: typically 0.001–0.1; start with 0.01 for alcohols, 0.04 for water

### 6.2.3 Multiple Solutions and Degeneracy

A well-known challenge in CPA parameterization is the existence of **multiple parameter sets** that give similar objective function values. This degeneracy arises because:

1. The cubic parameters ($a_0$, $b$, $c_1$) can partially compensate for changes in the association parameters
2. Different ($\varepsilon$, $\beta$) combinations can produce similar $\Delta$ values
3. The experimental data may not uniquely constrain all five parameters

The consequence is that two parameter sets with similar pure-component fits may give very different predictions for mixtures. To address this:

- Always include mixture data (binary VLE or LLE) in the assessment of parameter quality
- Prefer parameter sets where $\varepsilon$ and $\beta$ have physically reasonable values
- Check that the predicted monomer fraction ($X_A$) is consistent with spectroscopic data when available

## 6.3 Physical Interpretation of Parameters

### 6.3.1 Association Energy ($\varepsilon/R$)

The association energy represents the depth of the hydrogen-bond potential well. Typical values:

| Molecule Class | $\varepsilon/R$ (K) | Physical Interpretation |
|---------------|---------------------|----------------------|
| Water | 1800–2500 | Strong O–H···O bonds |
| Primary alcohols | 2000–3000 | O–H···O bonds similar to water |
| Glycols (MEG, TEG) | 2000–2800 | Multiple OH groups |
| Amines | 1000–2000 | N–H···N weaker than O–H···O |
| Carboxylic acids | 3000–5000 | Very strong O–H···O=C bonds (dimerization) |

*Table 6.1: Typical association energy values for different molecule classes.*

The association energy can be compared with experimental hydrogen-bond enthalpies from spectroscopy. For water, the O–H···O bond energy is approximately 20 kJ/mol ($\approx 2400$ K in $\varepsilon/k_B$), consistent with CPA parameter values.

### 6.3.2 Association Volume ($\beta$)

The association volume is a dimensionless parameter related to the geometric probability of forming a hydrogen bond when two molecules are at contact distance. It reflects:

- The angular constraint for hydrogen bond formation
- The effective bonding distance relative to the molecular diameter
- The entropy penalty for the specific orientation required for bonding

Smaller $\beta$ values mean that bonding is geometrically less probable (stricter orientational requirements). Water has a relatively large $\beta$ because its tetrahedral structure allows hydrogen bonds over a wide angular range.

### 6.3.3 The Cubic Parameters in CPA

The cubic parameters ($a_0$, $b$, $c_1$) in CPA differ from their SRK counterparts because they must work in concert with the association term:

- **$a_0$ in CPA is typically smaller** than in SRK for the same component, because part of the attractive interaction is now captured by the association term
- **$b$ in CPA is similar** to SRK values, as the molecular size is largely independent of association
- **$c_1$ in CPA differs** from the Soave $m(\omega)$ value because the temperature dependence of the cubic term must complement the temperature dependence of the association term

## 6.4 Parameter Tables for Common Components

### 6.4.1 Water

Water is the most important associating component in process engineering. The recommended CPA parameters for water (4C scheme) in the NeqSim/Equinor set are:

| Parameter | Value | Unit |
|-----------|-------|------|
| $a_0$ | 0.12277 | Pa·m$^6$/mol$^2$ |
| $b$ | $1.4515 \times 10^{-5}$ | m$^3$/mol |
| $c_1$ | 0.6736 | — |
| $\varepsilon/R$ | 2003.25 | K |
| $\beta$ | 0.0692 | — |

*Table 6.2: CPA parameters for water (4C scheme, Equinor/NeqSim set).*

These parameters reproduce:
- Vapor pressure: average absolute deviation (AAD) < 1% over 280–620 K
- Liquid density: AAD < 1.5% over 280–580 K

### 6.4.2 Alcohols

| Component | Scheme | $a_0$ | $b \times 10^5$ | $c_1$ | $\varepsilon/R$ (K) | $\beta$ |
|-----------|--------|-------|-----------------|-------|---------------------|---------|
| Methanol | 2B | 0.4053 | 3.098 | 0.4310 | 2957.78 | 0.0163 |
| Ethanol | 2B | 0.6878 | 4.908 | 0.7369 | 2589.85 | 0.0080 |
| 1-Propanol | 2B | 1.0780 | 6.453 | 0.9171 | 2525.86 | 0.0084 |
| 1-Butanol | 2B | 1.5221 | 7.979 | 1.0770 | 2525.86 | 0.0047 |

*Table 6.3: CPA parameters for primary alcohols (2B scheme).*

A clear trend is visible: the cubic parameters ($a_0$, $b$) increase with molecular size while the association energy remains roughly constant along the homologous series, reflecting that the OH group is the same in all cases.

### 6.4.3 Glycols

| Component | Scheme | $\varepsilon/R$ (K) | $\beta$ | Vapor Pressure AAD (%) | Density AAD (%) |
|-----------|--------|---------------------|---------|----------------------|-----------------|
| MEG | 4C | 2375.00 | 0.0141 | 1.2 | 0.8 |
| DEG | 4C | 2568.00 | 0.0045 | 1.5 | 1.0 |
| TEG | 4C | 2637.00 | 0.0018 | 1.8 | 1.2 |

*Table 6.4: CPA association parameters and fitting quality for glycols.*

The decreasing $\beta$ along the glycol series reflects the increasing molecular size — the OH groups become a smaller fraction of the total molecular volume, reducing the geometric probability of hydrogen bonding.

## 6.5 Parameter Sensitivity Analysis

### 6.5.1 Sensitivity of Vapor Pressure

The sensitivity of calculated vapor pressure to each parameter can be quantified by:

$$S_j^{P^{\text{sat}}} = \frac{\theta_j}{P^{\text{sat}}} \frac{\partial P^{\text{sat}}}{\partial \theta_j}$$

where $\theta_j$ is the $j$-th parameter. For water at 373 K:

- $a_0$: $S \approx -2.5$ (strong, negative — increasing $a_0$ lowers vapor pressure)
- $b$: $S \approx 0.5$ (moderate, positive)
- $c_1$: $S \approx -1.8$ (strong, negative — controls temperature dependence)
- $\varepsilon/R$: $S \approx -1.2$ (moderate, negative — stronger association lowers vapor pressure)
- $\beta$: $S \approx -0.3$ (weak — association volume has mild effect on vapor pressure)

The vapor pressure is most sensitive to $a_0$ and $c_1$, consistent with the SRK origin of these parameters.

### 6.5.2 Sensitivity of Liquid Density

For liquid density at 373 K:

- $b$: $S \approx -1.0$ (strong — co-volume directly affects liquid volume)
- $\varepsilon/R$: $S \approx -0.4$ (moderate — association compresses the liquid)
- $\beta$: $S \approx -0.2$ (weak)
- $a_0$: $S \approx -0.3$ (weak)
- $c_1$: $S \approx 0.1$ (very weak)

The liquid density is primarily controlled by $b$, with secondary contributions from the association parameters.

### 6.5.3 Parameter Correlation

A correlation analysis reveals strong correlations between parameter pairs:

- ($a_0$, $c_1$): correlation coefficient $\approx 0.9$ — these jointly determine vapor pressure
- ($\varepsilon$, $\beta$): correlation coefficient $\approx -0.8$ — these compensate each other in $\Delta$
- ($a_0$, $\varepsilon$): correlation coefficient $\approx 0.6$ — both affect the attractive interactions

These correlations explain the multiple-solution problem: along the correlated directions, different parameter combinations give similar fits.

```python
from neqsim import jneqsim

# Demonstrate that CPA correctly reproduces water properties
# by checking vapor pressure at the normal boiling point
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(373.15, 1.01325)
fluid.addComponent("water", 1.0)
fluid.setMixingRule(10)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.bubblePointPressureFlash(False)

print(f"Predicted boiling pressure: {fluid.getPressure('bara'):.4f} bara")
print(f"Experimental (1 atm): 1.0132 bara")
```

## 6.6 Transferability and Generalization

### 6.6.1 Group Contribution Approaches

For components without experimental data for parameter fitting, group contribution methods can provide estimates:

- **GC-CPA**: Predicts CPA parameters from molecular group contributions
- **Analogy-based**: Uses parameters from similar molecules (e.g., using ethanol parameters for 1-propanol with adjusted $a_0$ and $b$)

### 6.6.2 Pseudo-Component Treatment

For petroleum fractions (C7+ characterization), associating and non-associating contributions must be separated. The recommended approach is:

1. Treat petroleum fractions as non-associating (SRK parameters from $T_c$, $P_c$, $\omega$)
2. Model water, methanol, MEG, etc. as fully associating with database parameters
3. Use binary interaction parameters between petroleum fractions and associating components

This is the approach implemented in NeqSim for reservoir fluid characterization.

## Summary

Key points from this chapter:

- CPA has five pure-component parameters: $a_0$, $b$, $c_1$ (cubic) and $\varepsilon/R$, $\beta$ (association)
- Parameters are fitted to vapor pressure and liquid density data using nonlinear regression
- Multiple parameter sets may give similar pure-component fits but different mixture predictions
- Association energy values are physically meaningful and comparable to spectroscopic data
- The NeqSim database contains pre-fitted parameters for water, alcohols, glycols, and other key components
- For non-associating components, CPA reduces to SRK with standard critical property-based parameters

## Exercises

1. **Exercise 6.1:** Using the CPA parameters for water from Table 6.2, compute the vapor pressure from 280 K to 620 K and compare with NIST data. Calculate the AAD.

2. **Exercise 6.2:** Perform a parameter sensitivity analysis for methanol: vary each of the five parameters by $\pm 5$% and compute the effect on vapor pressure at 337.7 K (normal boiling point). Rank the parameters by sensitivity.

3. **Exercise 6.3:** Compare the predicted liquid density of MEG from CPA and SRK at temperatures from 20°C to 200°C. Explain the improvement in terms of the association contribution to liquid structure.

## References

<!-- Chapter-level references are merged into master refs.bib -->


## Figures

![Figure 6.1: 01 Parameter Comparison](figures/fig_ch06_01_parameter_comparison.png)

*Figure 6.1: 01 Parameter Comparison*

![Figure 6.2: 02 Parameter Landscape](figures/fig_ch06_02_parameter_landscape.png)

*Figure 6.2: 02 Parameter Landscape*

![Figure 6.3: 03 Cpa Water Pvap Accuracy](figures/fig_ch06_03_cpa_water_pvap_accuracy.png)

*Figure 6.3: 03 Cpa Water Pvap Accuracy*

![Figure 6.4: Ex01 Regression Landscape](figures/fig_ch06_ex01_regression_landscape.png)

*Figure 6.4: Ex01 Regression Landscape*

![Figure 6.5: Ex03 Compensating Params](figures/fig_ch06_ex03_compensating_params.png)

*Figure 6.5: Ex03 Compensating Params*
