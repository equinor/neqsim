# Fluid Characterization in NeqSim: Mathematical Foundations

This document provides detailed mathematical documentation of the fluid characterization methods implemented in NeqSim, with emphasis on plus fraction (C7+) modeling, TBP fraction property correlations, and pseudo-component generation.

## Table of Contents

1. [Overview](#overview)
2. [Plus Fraction Distribution Models](#plus-fraction-distribution-models)
   - [Whitson Gamma Distribution Model](#whitson-gamma-distribution-model)
   - [Pedersen Exponential Model](#pedersen-exponential-model)
3. [Critical Property Correlations](#critical-property-correlations)
   - [Pedersen Correlations](#pedersen-correlations)
   - [Riazi-Daubert Correlations](#riazi-daubert-correlations)
   - [Kesler-Lee Correlations](#kesler-lee-correlations)
4. [Density Correlations](#density-correlations)
   - [Watson K-Factor Method (UOP)](#watson-k-factor-method-uop)
   - [Søreide Correlation](#søreide-correlation)
5. [Boiling Point Correlations](#boiling-point-correlations)
6. [Lumping Methods](#lumping-methods)
7. [Usage Examples](#usage-examples)

---

## Overview

Petroleum fluids contain thousands of hydrocarbon compounds. For practical thermodynamic calculations, heavy fractions (typically C7+) must be characterized using continuous distribution functions or discrete pseudo-components. NeqSim implements industry-standard characterization methods from Whitson (1983), Pedersen et al. (1984), and others.

The characterization workflow consists of three main steps:

1. **Plus Fraction Splitting**: Distribute the C7+ fraction into discrete Single Carbon Number (SCN) groups
2. **Property Estimation**: Calculate critical properties (Tc, Pc, ω) for each pseudo-component
3. **Lumping**: Combine pseudo-components into computationally efficient groups

---

## Plus Fraction Distribution Models

### Whitson Gamma Distribution Model

The Whitson gamma distribution (SPE 12233, 1983) is the most widely used continuous distribution for petroleum C7+ fractions.

#### Probability Density Function

The molar distribution of the plus fraction is described by a three-parameter gamma distribution:

$$p(M) = \frac{(M - \eta)^{\alpha - 1}}{\beta^\alpha \cdot \Gamma(\alpha)} \exp\left(-\frac{M - \eta}{\beta}\right)$$

where:
- $M$ = molecular weight (g/mol)
- $\alpha$ = shape parameter (dimensionless)
- $\eta$ = minimum molecular weight (g/mol), typically 84-90 for C7+
- $\beta$ = scale parameter (g/mol)
- $\Gamma(\alpha)$ = gamma function

#### Gamma Function Approximation

NeqSim uses a polynomial approximation for the gamma function:

$$\Gamma(\alpha) \approx \alpha \cdot \left(1 + \sum_{i=1}^{8} b_i \cdot (\alpha - 1)^i\right)$$

with coefficients:
| $i$ | $b_i$ |
|-----|-------|
| 1 | -0.577191652 |
| 2 | 0.988205891 |
| 3 | -0.897056937 |
| 4 | 0.918206857 |
| 5 | -0.756704078 |
| 6 | 0.482199394 |
| 7 | -0.193527818 |
| 8 | 0.035868343 |

#### Parameter Relationships

The mean of the gamma distribution equals the plus fraction average molecular weight:

$$\bar{M}_{C7+} = \eta + \alpha \cdot \beta$$

Therefore, the scale parameter is calculated as:

$$\beta = \frac{\bar{M}_{C7+} - \eta}{\alpha}$$

#### Cumulative Distribution Functions

For splitting the plus fraction into SCN groups, NeqSim computes:

$$P_0(M_b) = \int_\eta^{M_b} p(M) \, dM = Q(M_b) \cdot S(M_b)$$

where:

$$Q = \frac{\exp(-Y) \cdot Y^\alpha}{\Gamma(\alpha)}, \quad Y = \frac{M_b - \eta}{\beta}$$

$$S = \sum_{j=0}^{\infty} \frac{Y^j}{\prod_{k=0}^{j}(\alpha + k)} = \frac{1}{\alpha} + \frac{Y}{\alpha(\alpha+1)} + \frac{Y^2}{\alpha(\alpha+1)(\alpha+2)} + \cdots$$

The first moment $P_1$ is used for average molecular weight calculation:

$$P_1(M_b) = Q(M_b) \cdot \left(S(M_b) - \frac{1}{\alpha}\right)$$

#### Mole Fraction of SCN Group

For an SCN group bounded by molecular weights $M_L$ (lower) and $M_U$ (upper):

$$z_i = z_{C7+} \cdot \left[P_0(M_U) - P_0(M_L)\right]$$

#### Average Molecular Weight of SCN Group

$$\bar{M}_i = \eta + \alpha \cdot \beta \cdot \frac{P_1(M_U) - P_1(M_L)}{P_0(M_U) - P_0(M_L)}$$

#### Shape Parameter Guidelines

The shape parameter $\alpha$ characterizes the fluid type:

| Fluid Type | Typical $\alpha$ Range | Watson $K_w$ |
|------------|----------------------|--------------|
| Gas condensates | 0.5 - 1.0 | > 12.5 |
| Black oils | 1.0 - 2.0 | 11.5 - 12.5 |
| Heavy/naphthenic oils | 2.0 - 4.0 | < 11.5 |

#### Automatic Alpha Estimation

NeqSim can automatically estimate $\alpha$ using the Watson characterization factor:

$$K_w = 4.5579 \cdot M_{C7+}^{0.15178} \cdot \rho_{C7+}^{-1.18241}$$

where $\rho$ is specific gravity (g/cm³). The estimated alpha is:

$$\alpha = \begin{cases}
0.5 + 0.1(K_w - 12.5) & K_w \geq 12.5 \text{ (paraffinic)} \\
1.0 + 0.5(K_w - 11.5) & 11.5 \leq K_w < 12.5 \text{ (mixed)} \\
1.5 + 0.5(K_w - 10.5) & 10.5 \leq K_w < 11.5 \text{ (naphthenic)} \\
2.0 + 0.5(10.5 - K_w) & K_w < 10.5 \text{ (aromatic)}
\end{cases}$$

---

### Pedersen Exponential Model

The Pedersen model (Pedersen et al., 1984) uses an exponential distribution:

$$\ln(z_n) = A + B \cdot n$$

where:
- $z_n$ = mole fraction of carbon number $n$
- $A, B$ = regression coefficients

The coefficients are determined by matching:
1. The plus fraction mole fraction $z_{C7+}$
2. The plus fraction molecular weight $M_{C7+}$
3. The plus fraction density $\rho_{C7+}$

#### Density Correlation (Pedersen)

$$\rho_n = C_1 + C_2 \cdot \ln(n)$$

where $C_1$ and $C_2$ are fitted to match the measured plus fraction density.

---

## Critical Property Correlations

### Pedersen Correlations

For SRK equation of state, critical properties are calculated using Pedersen et al. correlations:

#### Critical Temperature (K)

$$T_c = a_0 \cdot \rho + a_1 \cdot \ln(M) + a_2 \cdot M + \frac{a_3}{M}$$

| Coefficient | Light Oil (M < 1120 g/mol) | Heavy Oil |
|-------------|---------------------------|-----------|
| $a_0$ | 163.12 | 830.63 |
| $a_1$ | 86.052 | 17.5228 |
| $a_2$ | 0.43475 | 0.0455911 |
| $a_3$ | -1877.4 | -11348.4 |

#### Critical Pressure (bar)

$$P_c = \exp\left(0.01325 + b_0 + b_1 \cdot \rho^{b_4} + \frac{b_2}{M} + \frac{b_3}{M^2}\right)$$

| Coefficient | Light Oil | Heavy Oil |
|-------------|-----------|-----------|
| $b_0$ | -0.13408 | 0.802988 |
| $b_1$ | 2.5019 | 1.78396 |
| $b_2$ | 208.46 | 156.740 |
| $b_3$ | -3987.2 | -6965.59 |
| $b_4$ | 1.0 | 0.25 |

#### Acentric Factor (SRK m-parameter)

$$m = c_0 + c_1 \cdot M + c_2 \cdot \rho + c_3 \cdot M^2$$

For Peng-Robinson, different coefficients are used.

---

### Riazi-Daubert Correlations

Alternative correlations based on molecular weight and specific gravity:

#### Critical Temperature (K)

$$T_c = \frac{5}{9} \cdot 554.4 \cdot \exp(-1.3478 \times 10^{-4} M - 0.61641 \cdot \rho) \cdot M^{0.2998} \cdot \rho^{1.0555}$$

#### Critical Pressure (bar)

$$P_c = 0.068947 \cdot 4.5203 \times 10^4 \cdot \exp(-1.8078 \times 10^{-3} M - 0.3084 \cdot \rho) \cdot M^{-0.8063} \cdot \rho^{1.6015}$$

---

### Kesler-Lee Correlations

#### Acentric Factor

For $T_{br} = T_b/T_c < 0.8$:

$$\omega = \frac{\ln(P_{br}) - 5.92714 + \frac{6.09649}{T_{br}} + 1.28862 \ln(T_{br}) - 0.169347 T_{br}^6}{15.2518 - \frac{15.6875}{T_{br}} - 13.4721 \ln(T_{br}) + 0.43577 T_{br}^6}$$

For $T_{br} \geq 0.8$:

$$\omega = -7.904 + 0.1352 K_w - 0.007465 K_w^2 + 8.359 T_{br} + \frac{1.408 - 0.01063 K_w}{T_{br}}$$

where $P_{br} = 1.01325 / P_c$ and $K_w = T_b^{1/3} / \rho$ is the Watson K-factor.

---

## Density Correlations

### Watson K-Factor Method (UOP)

The Universal Oil Products (UOP) characterization assumes constant Watson K for all SCN groups:

$$K_w = 4.5579 \cdot M_{C7+}^{0.15178} \cdot \rho_{C7+}^{-1.18241}$$

Individual SCN densities:

$$\rho_i = 6.0108 \cdot M_i^{0.17947} \cdot K_w^{-1.18241}$$

**Limitation**: Less accurate for heavy fractions (C20+).

---

### Søreide Correlation

Søreide (1989) developed a more accurate correlation for heavy fractions:

$$SG = 0.2855 + C_f \cdot (M - 66)^{0.13}$$

where $SG$ is specific gravity (same as $\rho$ in g/cm³) and $C_f$ is calculated from the plus fraction:

$$C_f = \frac{SG_{C7+} - 0.2855}{(M_{C7+} - 66)^{0.13}}$$

Individual SCN densities:

$$\rho_i = 0.2855 + C_f \cdot (M_i - 66)^{0.13}$$

Constrained to physical limits: $0.6 \leq \rho_i \leq 1.2$ g/cm³.

---

## Boiling Point Correlations

### Søreide Correlation

$$T_b = \frac{1}{1.8}\left(1928.3 - 1.695 \times 10^5 \cdot M^{-0.03522} \cdot \rho^{3.266} \cdot \exp\left(-4.922 \times 10^{-3} M - 4.7685 \rho + 3.462 \times 10^{-3} M \cdot \rho\right)\right)$$

### Riazi-Daubert Correlation

$$T_b = \left(\frac{M}{5.805 \times 10^{-5} \cdot \rho^{0.9371}}\right)^{1/2.3776}$$

### Polynomial Correlation (Light Components)

For $M < 540$ g/mol:

$$T_b = 2 \times 10^{-6} M^3 - 0.0035 M^2 + 2.4003 M + 171.74$$

---

## Lumping Methods

### Standard Weight-Based Lumping

SCN pseudo-components are grouped into $N$ lumps with approximately equal weight fractions:

$$w_{target} = \frac{\sum_i z_i \cdot M_i}{N}$$

For each lump $k$, the properties are averaged:

#### Mole Fraction
$$z_k = \sum_{i \in k} z_i$$

#### Molecular Weight
$$M_k = \frac{\sum_{i \in k} z_i \cdot M_i}{z_k}$$

#### Density (Volume-Weighted)
$$\rho_k = \frac{\sum_{i \in k} z_i \cdot M_i}{\sum_{i \in k} \frac{z_i \cdot M_i}{\rho_i}}$$

#### Critical Properties
Mixing rules (typically mole-fraction weighted):

$$T_{c,k} = \sum_{i \in k} \frac{z_i}{z_k} \cdot T_{c,i}$$

$$P_{c,k} = \sum_{i \in k} \frac{z_i}{z_k} \cdot P_{c,i}$$

$$\omega_k = \sum_{i \in k} \frac{z_i}{z_k} \cdot \omega_i$$

---

## Usage Examples

### Basic Whitson Gamma Characterization

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid with plus fraction
SystemInterface fluid = new SystemSrkEos(350.0, 150.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addPlusFraction("C7+", 0.15, 150.0 / 1000.0, 0.82);  // MW in kg/mol, density in g/cm³
fluid.setMixingRule(2);

// Configure Whitson Gamma Model
fluid.getCharacterization()
    .setPlusFractionModel("Whitson Gamma Model")
    .setGammaShapeParameter(1.5)      // alpha for black oil
    .setGammaMinMW(84.0)              // eta for C7+
    .setGammaDensityModel("Soreide"); // Use Søreide density correlation

// Run characterization
fluid.getCharacterization().characterisePlusFraction();
```

### Automatic Alpha Estimation

```java
// Let NeqSim estimate alpha based on Watson K-factor
fluid.getCharacterization()
    .setPlusFractionModel("Whitson Gamma Model")
    .setAutoEstimateGammaAlpha(true)
    .setGammaDensityModel("Soreide");

fluid.getCharacterization().characterisePlusFraction();

// Check the estimated alpha
double alpha = ((PlusFractionModel.WhitsonGammaModel) 
    fluid.getCharacterization().getPlusFractionModel()).getAlpha();
double watsonK = ((PlusFractionModel.WhitsonGammaModel) 
    fluid.getCharacterization().getPlusFractionModel()).getWatsonKFactor();

System.out.println("Estimated alpha: " + alpha);
System.out.println("Watson K-factor: " + watsonK);
```

### Pedersen Model with Lumping

```java
fluid.getCharacterization()
    .setPlusFractionModel("Pedersen")
    .setLumpingModel("Standard");

fluid.getCharacterization().getLumpingModel().setNumberOfPseudoComponents(6);
fluid.getCharacterization().characterisePlusFraction();
```

### Accessing Characterized Data

```java
PlusFractionModelInterface model = fluid.getCharacterization().getPlusFractionModel();

double[] moleFractions = model.getZ();
double[] molecularWeights = model.getM();
double[] densities = model.getDens();

for (int i = model.getFirstPlusFractionNumber(); i < model.getLastPlusFractionNumber(); i++) {
    if (moleFractions[i] > 1e-10) {
        System.out.printf("SCN %d: z=%.6f, M=%.1f g/mol, rho=%.4f g/cm³%n",
            i, moleFractions[i], molecularWeights[i] * 1000, densities[i]);
    }
}
```

---

## PVT Regression Framework

This section documents the PVT regression framework for automatic EOS model tuning based on experimental PVT report data. The framework is implemented in the `neqsim.pvtsimulation.regression` package.

### Package Overview

| Class | Description |
|-------|-------------|
| `PVTRegression` | Main regression framework class |
| `RegressionParameter` | Enum defining tunable parameters (BIPs, volume shifts, critical properties) |
| `ExperimentType` | Enum for experiment types (CCE, CVD, DLE, SEPARATOR, etc.) |
| `CCEDataPoint`, `CVDDataPoint`, `DLEDataPoint`, `SeparatorDataPoint` | Data point classes for each experiment type |
| `RegressionParameterConfig` | Configuration for each regression parameter with bounds |
| `PVTRegressionFunction` | Objective function extending LevenbergMarquardtFunction |
| `RegressionResult` | Result container with tuned fluid and uncertainty analysis |
| `UncertaintyAnalysis` | Statistical uncertainty quantification |

### Overview

PVT regression involves adjusting equation of state parameters to minimize the deviation between calculated and experimental properties. The framework handles multiple experiment types simultaneously while maintaining physical consistency.

### Multi-Objective Regression

#### Objective Function

The total objective function combines weighted contributions from different PVT experiments:

$$F_{obj} = \sum_{k} w_k \cdot F_k$$

where $w_k$ are user-defined weights and $F_k$ are individual experiment objective functions.

#### Constant Composition Expansion (CCE)

$$F_{CCE} = \sum_{i=1}^{N_{CCE}} \left[ \left(\frac{V_{rel,i}^{calc} - V_{rel,i}^{exp}}{V_{rel,i}^{exp}}\right)^2 + \lambda_Y \left(\frac{Y_i^{calc} - Y_i^{exp}}{Y_i^{exp}}\right)^2 \right]$$

where:
- $V_{rel}$ = relative volume
- $Y$ = gas compressibility factor (above saturation)
- $\lambda_Y$ = weight factor for Y-function

**Key match points:**
- Saturation pressure $P_{sat}$
- Relative volume curve
- Oil/gas compressibility

#### Constant Volume Depletion (CVD)

$$F_{CVD} = \sum_{i=1}^{N_{CVD}} \left[ \left(\frac{L_i^{calc} - L_i^{exp}}{L_i^{exp}}\right)^2 + \left(\frac{Z_i^{calc} - Z_i^{exp}}{Z_i^{exp}}\right)^2 + \sum_{j} \left(\frac{y_{j,i}^{calc} - y_{j,i}^{exp}}{y_{j,i}^{exp}}\right)^2 \right]$$

where:
- $L$ = liquid dropout (volume %)
- $Z$ = gas compressibility factor
- $y_j$ = produced gas composition

**Key match points:**
- Liquid dropout curve (retrograde condensation)
- Two-phase Z-factor
- Produced gas compositions at each stage

#### Differential Liberation Expansion (DLE)

$$F_{DLE} = \sum_{i=1}^{N_{DLE}} \left[ \left(\frac{R_{s,i}^{calc} - R_{s,i}^{exp}}{R_{s,i}^{exp}}\right)^2 + \left(\frac{B_{o,i}^{calc} - B_{o,i}^{exp}}{B_{o,i}^{exp}}\right)^2 + \left(\frac{\rho_{o,i}^{calc} - \rho_{o,i}^{exp}}{\rho_{o,i}^{exp}}\right)^2 \right]$$

where:
- $R_s$ = solution gas-oil ratio
- $B_o$ = oil formation volume factor
- $\rho_o$ = oil density

**Key match points:**
- Solution GOR curve
- Oil FVF curve
- Oil density/shrinkage
- Gas gravity

#### Separator Test

$$F_{SEP} = \left(\frac{GOR^{calc} - GOR^{exp}}{GOR^{exp}}\right)^2 + \left(\frac{B_o^{calc} - B_o^{exp}}{B_o^{exp}}\right)^2 + \left(\frac{API^{calc} - API^{exp}}{API^{exp}}\right)^2$$

#### Viscosity (Optional)

$$F_{\mu} = \sum_{i} \left(\frac{\mu_i^{calc} - \mu_i^{exp}}{\mu_i^{exp}}\right)^2$$

---

### Automatic Binary Interaction Parameter (BIP) Optimization

#### BIP Matrix Structure

For a system with $N_c$ components, the symmetric BIP matrix has $N_c(N_c-1)/2$ independent parameters. To reduce dimensionality, group-based BIPs are used:

| Group Pairs | Typical Starting $k_{ij}$ | Regression Range |
|-------------|--------------------------|------------------|
| CH₄ - C₂-C₆ | 0.00 - 0.02 | Fixed or narrow |
| CH₄ - C7+ | 0.02 - 0.05 | Primary target |
| CO₂ - HC | 0.10 - 0.15 | If CO₂ present |
| N₂ - HC | 0.04 - 0.08 | If N₂ present |
| H₂S - HC | 0.05 - 0.10 | If H₂S present |
| C7+ - C7+ | 0.00 | Usually fixed |

#### BIP Correlation (Carbon Number Based)

For C7+ pseudo-components, BIPs can be correlated:

$$k_{ij} = k_{CH_4-C7+} \cdot \left(1 - \left(\frac{2\sqrt{T_{c,i} \cdot T_{c,j}}}{T_{c,i} + T_{c,j}}\right)^n\right)$$

where $n$ is a tunable exponent (typically 0.5-2.0).

#### Optimization Strategy

1. **Level 1**: Tune $k_{CH_4-C7+}$ to match saturation pressure
2. **Level 2**: Tune $k_{C_2-C_6, C7+}$ to improve phase envelope shape
3. **Level 3**: Tune correlation exponent $n$ for density matching

---

### Volume Translation Parameter Tuning

#### Peneloux Volume Shift

The Peneloux (1982) volume translation corrects liquid density without affecting VLE:

$$V_{corrected} = V_{EOS} - \sum_i x_i \cdot c_i$$

where $c_i$ is the component volume shift parameter.

#### Regression Approach

For pseudo-components, express volume shift as:

$$c_i = c_0 + c_1 \cdot M_i + c_2 \cdot M_i^2$$

**Objective**: Minimize density deviation in single-phase liquid region:

$$F_c = \sum_{i} \left(\frac{\rho_i^{calc} - \rho_i^{exp}}{\rho_i^{exp}}\right)^2$$

#### Rackett Equation Alternative

$$Z_{RA,i} = Z_{RA}^{ref} + a \cdot (M_i - M_{ref})$$

where $Z_{RA}$ is the Rackett compressibility factor and $a$ is a tunable coefficient.

---

### Critical Property Regression for Pseudo-Components

#### Parameterization

Instead of regressing individual $T_c$, $P_c$, $\omega$ values, tune the correlation coefficients:

**Critical Temperature:**
$$T_c = (a_0 + \Delta a_0) \cdot \rho + (a_1 + \Delta a_1) \cdot \ln(M) + a_2 \cdot M + \frac{a_3}{M}$$

**Critical Pressure:**
$$\ln(P_c) = (b_0 + \Delta b_0) + b_1 \cdot \rho^{b_4} + \frac{b_2}{M} + \frac{b_3}{M^2}$$

**Acentric Factor:**
$$\omega = (\omega_{base} + \Delta\omega) \cdot f(M, \rho)$$

where $\Delta a_0$, $\Delta a_1$, $\Delta b_0$, $\Delta\omega$ are regression parameters.

#### Constraints

Physical bounds must be enforced:

$$T_c > T_b > 0$$
$$P_c > 0$$
$$0 < \omega < 2$$
$$\frac{\partial T_c}{\partial M} > 0 \text{ (monotonic increase)}$$

---

### Uncertainty Quantification

#### Parameter Sensitivity Analysis

Compute the Jacobian matrix at the optimum:

$$J_{ij} = \frac{\partial F_i}{\partial \theta_j}$$

where $F_i$ are individual residuals and $\theta_j$ are parameters.

#### Covariance Matrix

$$\text{Cov}(\theta) = s^2 \cdot (J^T J)^{-1}$$

where $s^2$ is the residual variance:

$$s^2 = \frac{F_{obj}}{N_{data} - N_{params}}$$

#### Confidence Intervals

95% confidence interval for parameter $\theta_j$:

$$\theta_j \pm t_{0.975, N-p} \cdot \sqrt{\text{Cov}(\theta)_{jj}}$$

#### Monte Carlo Uncertainty Propagation

1. Sample parameters from multivariate normal: $\theta \sim N(\hat{\theta}, \text{Cov}(\theta))$
2. Run flash calculations for each sample
3. Compute prediction intervals for properties:

$$P_{95\%} = \left[\mu - 1.96\sigma, \mu + 1.96\sigma\right]$$

---

### API Usage Example

```java
import neqsim.pvtsimulation.regression.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Create base fluid
SystemInterface fluid = new SystemSrkEos(373.15, 200.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.05);
fluid.addPlusFraction("C7+", 0.10, 0.150, 0.82);
fluid.setMixingRule(2);

// Create PVT regression framework
PVTRegression regression = new PVTRegression(fluid);

// Add experimental CCE data
double[] pressures = {300.0, 250.0, 200.0, 150.0, 100.0};
double[] relativeVolumes = {0.98, 1.00, 1.08, 1.25, 1.55};
regression.addCCEData(pressures, relativeVolumes, 373.15);

// Add DLE data
double[] dlePressures = {250.0, 200.0, 150.0, 100.0};
double[] rs = {150.0, 120.0, 85.0, 50.0};
double[] bo = {1.45, 1.38, 1.30, 1.20};
double[] oilDensity = {720.0, 740.0, 760.0, 780.0};
regression.addDLEData(dlePressures, rs, bo, oilDensity, 373.15);

// Configure regression parameters (with custom bounds or use defaults)
regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10, 0.03);
regression.addRegressionParameter(RegressionParameter.VOLUME_SHIFT_C7PLUS);  // Uses defaults

// Set weights for multi-objective optimization
regression.setExperimentWeight(ExperimentType.CCE, 1.0);
regression.setExperimentWeight(ExperimentType.DLE, 1.5);  // Prioritize DLE matching

// Configure optimization
regression.setMaxIterations(100);
regression.setVerbose(true);

// Run regression
RegressionResult result = regression.runRegression();

// Get tuned fluid
SystemInterface tunedFluid = result.getTunedFluid();

// Get optimized parameter values
double optimizedBIP = result.getOptimizedValue(RegressionParameter.BIP_METHANE_C7PLUS);
System.out.println("Optimized BIP (CH4-C7+): " + optimizedBIP);

// Uncertainty analysis
UncertaintyAnalysis uncertainty = result.getUncertainty();
double[] ci = uncertainty.getConfidenceIntervalBounds(0);
System.out.println("95% CI for BIP: [" + ci[0] + ", " + ci[1] + "]");

// Generate summary report
String summary = result.generateSummary();
System.out.println(summary);
```

### Available Regression Parameters

| Parameter | Description | Default Bounds |
|-----------|-------------|----------------|
| `BIP_METHANE_C7PLUS` | BIP between methane and C7+ fractions | [0.0, 0.10, 0.03] |
| `BIP_C2C6_C7PLUS` | BIP between C2-C6 and C7+ fractions | [0.0, 0.05, 0.01] |
| `BIP_CO2_HC` | BIP between CO₂ and hydrocarbons | [0.08, 0.18, 0.12] |
| `BIP_N2_HC` | BIP between N₂ and hydrocarbons | [0.02, 0.12, 0.05] |
| `VOLUME_SHIFT_C7PLUS` | Volume shift multiplier for C7+ | [0.8, 1.2, 1.0] |
| `TC_MULTIPLIER_C7PLUS` | Critical temperature multiplier | [0.95, 1.05, 1.0] |
| `PC_MULTIPLIER_C7PLUS` | Critical pressure multiplier | [0.95, 1.05, 1.0] |
| `OMEGA_MULTIPLIER_C7PLUS` | Acentric factor multiplier | [0.90, 1.10, 1.0] |
| `PLUS_MOLAR_MASS_MULTIPLIER` | Plus fraction MW multiplier | [0.90, 1.10, 1.0] |
| `GAMMA_ALPHA` | Gamma distribution shape parameter | [0.5, 4.0, 1.0] |
| `GAMMA_ETA` | Gamma distribution minimum MW | [75.0, 95.0, 84.0] |

### Data Input Format (CSV)

**CCE Data:**
```csv
Pressure(bara),RelativeVolume,YFactor
350.0,0.9850,
300.0,0.9912,
250.0,1.0000,  # Saturation point
200.0,1.0523,1.0234
150.0,1.1876,1.0456
```

**DLE Data:**
```csv
Pressure(bara),Rs(Sm3/Sm3),Bo(m3/Sm3),OilDensity(kg/m3),GasGravity
250.0,150.5,1.425,725.3,0.85
200.0,120.2,1.380,742.1,0.82
150.0,85.6,1.312,761.5,0.79
```

---

### Implementation Status

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | CCE/DLE simulation with objective function | ✅ Implemented |
| 2 | BIP regression for saturation pressure | ✅ Implemented |
| 3 | Volume translation optimization | ✅ Implemented |
| 4 | CVD simulation and regression | ✅ Implemented |
| 5 | Critical property correlation tuning | ✅ Implemented |
| 6 | Multi-objective optimization framework | ✅ Implemented |
| 7 | Uncertainty quantification | ✅ Implemented |
| 8 | GUI/Report generation | 🔲 Future work |

---

## References

1. Whitson, C.H. (1983). "Characterizing Hydrocarbon Plus Fractions." SPE Journal, 23(4), 683-694. SPE-12233-PA.

2. Pedersen, K.S., Thomassen, P., and Fredenslund, A. (1984). "Thermodynamics of Petroleum Mixtures Containing Heavy Hydrocarbons. 1. Phase Envelope Calculations by Use of the Soave-Redlich-Kwong Equation of State." Industrial & Engineering Chemistry Process Design and Development, 23(1), 163-170.

3. Søreide, I. (1989). "Improved Phase Behavior Predictions of Petroleum Reservoir Fluids from a Cubic Equation of State." Dr.Ing. Thesis, Norwegian Institute of Technology (NTH), Trondheim.

4. Riazi, M.R. and Daubert, T.E. (1980). "Simplify Property Predictions." Hydrocarbon Processing, 59(3), 115-116.

5. Kesler, M.G. and Lee, B.I. (1976). "Improve Prediction of Enthalpy of Fractions." Hydrocarbon Processing, 55(3), 153-158.

6. Whitson, C.H. and Brulé, M.R. (2000). "Phase Behavior." SPE Monograph Series, Vol. 20. Society of Petroleum Engineers.

---

## Appendix A: Unit Conventions in NeqSim

| Property | Internal Unit | Common Input Unit |
|----------|--------------|-------------------|
| Molecular weight | kg/mol | g/mol (÷1000) |
| Density | g/cm³ | g/cm³ or kg/m³ (÷1000) |
| Temperature | K | °C (+273.15) |
| Pressure | bar | bara |
| Critical temperature | K | K |
| Critical pressure | bar | bar |

**Note**: When using `addPlusFraction()`, molecular weight should be in kg/mol and density in g/cm³ (specific gravity).

---

## Appendix B: Mathematical Notation Summary

| Symbol | Description | Typical Range |
|--------|-------------|---------------|
| $\alpha$ | Gamma shape parameter | 0.5 - 4.0 |
| $\beta$ | Gamma scale parameter | Calculated |
| $\eta$ | Minimum molecular weight | 84 - 90 g/mol |
| $M$ | Molecular weight | g/mol |
| $\rho$ | Density (specific gravity) | 0.6 - 1.0 g/cm³ |
| $K_w$ | Watson characterization factor | 10 - 13 |
| $T_c$ | Critical temperature | K |
| $P_c$ | Critical pressure | bar |
| $\omega$ | Acentric factor | 0.2 - 1.5 |
| $T_b$ | Normal boiling point | K |
| $z$ | Mole fraction | 0 - 1 |
