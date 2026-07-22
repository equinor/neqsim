---
title: NeqSim Field Development - Mathematical Reference
description: This document provides the complete mathematical foundations for the field development framework, linking thermodynamic calculations to economic evaluation and decision support.
---

# NeqSim Field Development - Mathematical Reference

This document provides the complete mathematical foundations for the field development framework, linking thermodynamic calculations to economic evaluation and decision support.

---

## Table of Contents

1. [Thermodynamic Foundations](#1-thermodynamic-foundations)
2. [Production Modeling](#2-production-modeling)
3. [Flow Assurance & Hydraulics](#3-flow-assurance--hydraulics)
4. [Economic Calculations](#4-economic-calculations)
5. [Decision Analysis](#5-decision-analysis)
6. [Uncertainty Quantification](#6-uncertainty-quantification)
7. [Emissions & Sustainability](#7-emissions--sustainability)

---

## 1. Thermodynamic Foundations

### 1.1 Equations of State

NeqSim supports multiple equations of state. The general cubic EoS form:

$$P = \frac{RT}{V-b} - \frac{a(T)}{(V+\delta_1 b)(V+\delta_2 b)}$$

| EoS | $\delta_1$ | $\delta_2$ | Use Case |
|-----|------------|------------|----------|
| SRK | 1 | 0 | General hydrocarbon systems |
| Peng-Robinson | $1+\sqrt{2}$ | $1-\sqrt{2}$ | Liquid density improvement |
| CPA | SRK base | + association | Water, glycols, alcohols |

#### Critical Properties

$$a_c = \Omega_a \frac{R^2 T_c^2}{P_c}, \quad b = \Omega_b \frac{R T_c}{P_c}$$

#### Temperature Dependence (Soave-type)

$$\alpha(T) = \left[1 + m\left(1 - \sqrt{T_r}\right)\right]^2$$

$$m = 0.48 + 1.574\omega - 0.176\omega^2$$

### 1.2 Mixing Rules

**Classical (van der Waals)**:
$$a_{mix} = \sum_i \sum_j x_i x_j \sqrt{a_i a_j}(1-k_{ij})$$
$$b_{mix} = \sum_i x_i b_i$$

**CPA Association Term**:
$$Z^{assoc} = -\frac{1}{2}\sum_i x_i \sum_{A_i} \left(1 - X_{A_i}\right)$$

Where $X_{A_i}$ is the fraction of sites A on molecule i NOT bonded.

### 1.3 Flash Calculations

**PT Flash Objective** (Rachford-Rice):
$$f(\beta) = \sum_i \frac{z_i(K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

Where:
- $\beta$ = vapor fraction
- $z_i$ = overall composition
- $K_i = y_i/x_i$ = equilibrium ratio

**Fugacity Coefficient**:
$$\ln \phi_i = \frac{1}{RT}\int_V^\infty \left(\frac{\partial P}{\partial n_i}\bigg|_{T,V,n_{j\neq i}} - \frac{RT}{V}\right)dV - \ln Z$$

---

## 2. Production Modeling

### 2.1 Inflow Performance Relationship (IPR)

**Vogel's Equation** (oil wells below bubble point):
$$\frac{q_o}{q_{o,max}} = 1 - 0.2\left(\frac{P_{wf}}{P_r}\right) - 0.8\left(\frac{P_{wf}}{P_r}\right)^2$$

**Darcy's Law** (single-phase liquid):
$$q = \frac{2\pi k h}{\mu B \ln(r_e/r_w)} (P_r - P_{wf})$$

**Productivity Index**:
$$J = \frac{q}{P_r - P_{wf}} \quad \text{[Sm³/d/bar]}$$

### 2.2 Vertical Lift Performance (VLP)

**Pressure Traverse**:
$$\frac{dP}{dL} = \frac{\rho_m g \sin\theta}{1 - \frac{\rho_m v_m v_{sg}}{P}} + \frac{f \rho_m v_m^2}{2D} + \frac{\rho_m v_m dv_m}{dL}$$

Components:
- Gravity: $(\rho_m g \sin\theta)$
- Friction: $(f \rho_m v_m^2 / 2D)$
- Acceleration: $(\rho_m v_m dv_m/dL)$

### 2.3 Decline Curve Analysis

**Arps Hyperbolic**:
$$q(t) = \frac{q_i}{(1 + b D_i t)^{1/b}}$$

Where:
- $q_i$ = initial rate
- $D_i$ = initial decline rate
- $b$ = hyperbolic exponent (0 ≤ b ≤ 1)

**Cumulative Production**:
$$N_p = \frac{q_i}{D_i(1-b)}\left[1 - \left(\frac{q}{q_i}\right)^{1-b}\right]$$

### 2.4 TransientWellModel Equations

**Drawdown (radial flow)**:
$$P_{wf} = P_i - \frac{q \mu B}{4\pi kh}\left[\ln\left(\frac{4kt}{\phi \mu c_t r_w^2 \gamma}\right) + 2S\right]$$

**Dimensionless Pressure**:
$$P_D = \frac{2\pi kh(P_i - P_{wf})}{q \mu B}$$

**Dimensionless Time**:
$$t_D = \frac{kt}{\phi \mu c_t r_w^2}$$

---

## 3. Flow Assurance & Hydraulics

### 3.1 Beggs & Brill Correlation

**No-Slip Liquid Holdup**:
$$\lambda_L = \frac{v_{SL}}{v_m}$$

Where superficial velocities:
$$v_{SL} = \frac{Q_L}{A}, \quad v_{SG} = \frac{Q_G}{A}, \quad v_m = v_{SL} + v_{SG}$$

**Flow Pattern Boundaries**:

| Transition | Equation |
|------------|----------|
| $L_1$ | $316 \lambda_L^{0.302}$ |
| $L_2$ | $0.0009252 \lambda_L^{-2.4684}$ |
| $L_3$ | $0.10 \lambda_L^{-1.4516}$ |
| $L_4$ | $0.5 \lambda_L^{-6.738}$ |

**Liquid Holdup (Segregated)**:
$$H_L(0) = \frac{0.980 \lambda_L^{0.4846}}{Fr^{0.0868}}$$

**Liquid Holdup (Intermittent)**:
$$H_L(0) = \frac{0.845 \lambda_L^{0.5351}}{Fr^{0.0173}}$$

**Liquid Holdup (Distributed)**:
$$H_L(0) = \frac{1.065 \lambda_L^{0.5824}}{Fr^{0.0609}}$$

**Inclination Correction**:
$$H_L(\theta) = H_L(0) \cdot \psi(\theta)$$

$$\psi = 1 + C\left[\sin(1.8\theta) - \frac{1}{3}\sin^3(1.8\theta)\right]$$

### 3.2 Pressure Drop Calculation

**Two-Phase Friction Factor**:
$$\Delta P_f = \frac{f_{tp} \rho_n v_m^2 L}{2D}$$

**Normalized Friction Factor**:
$$f_{tp} = f_n \cdot e^S$$

Where $S$ depends on:
$$y = \frac{\lambda_L}{H_L^2}$$

**Elevation Component**:
$$\Delta P_g = \rho_m g L \sin\theta$$

**Mixture Density**:
$$\rho_m = \rho_L H_L + \rho_G (1 - H_L)$$

### 3.3 Hydrate Formation

**Hammerschmidt Equation** (inhibitor depression):
$$\Delta T = \frac{K_H \cdot w}{M(100-w)}$$

Where:
- $\Delta T$ = temperature depression (°C)
- $K_H$ = inhibitor constant (1297 for MEG)
- $w$ = weight percent inhibitor
- $M$ = molecular weight of inhibitor

**CSMGem-type correlation** (implemented in NeqSim):
$$\ln\left(\frac{f_w^H}{f_w^L}\right) = \frac{\Delta\mu_w^0}{RT} + \sum_i \ln(1-\theta_i)$$

### 3.4 Wax Appearance Temperature

**Coutinho Model**:
$$\ln(\gamma_i^s x_i^s) = \frac{\Delta H_{fus,i}}{R}\left(\frac{1}{T_m} - \frac{1}{T}\right) + \frac{\Delta C_p}{R}\left(\frac{T_m-T}{T} + \ln\frac{T}{T_m}\right)$$

---

## 4. Economic Calculations

### 4.1 Net Present Value (NPV)

$$NPV = \sum_{t=0}^{n} \frac{CF_t}{(1+r)^t}$$

**Cash Flow**:
$$CF_t = Revenue_t - OPEX_t - CAPEX_t - Tax_t$$

### 4.2 Norwegian Petroleum Tax

**Corporate Tax (22%)**:
$$Tax_C = 0.22 \times (Revenue - OPEX - DD\&A - Interest)$$

**Special Petroleum Tax (56%)**:
$$Tax_S = 0.56 \times (Revenue - OPEX - Uplift - Special\ DD\&A)$$

**Uplift Calculation**:
$$Uplift = 0.208 \times CAPEX_{eligible}$$

**Depreciation** (6-year linear):
$$DD\&A_t = \frac{CAPEX_{t-6} + CAPEX_{t-5} + ... + CAPEX_{t-1}}{6}$$

**After-Tax Cash Flow**:
$$CF_{at} = Revenue - OPEX - CAPEX - Tax_C - Tax_S$$

### 4.3 Internal Rate of Return (IRR)

Find $r$ such that:
$$\sum_{t=0}^{n} \frac{CF_t}{(1+r)^t} = 0$$

### 4.4 Payback Period

$$Payback = t^* \text{ where } \sum_{t=0}^{t^*} CF_t \geq 0$$

### 4.5 Capital Efficiency Metrics

**Profitability Index**:
$$PI = \frac{NPV + CAPEX_{PV}}{CAPEX_{PV}}$$

**Return on Investment**:
$$ROI = \frac{\sum CF_{positive}}{CAPEX_{total}}$$

### 4.6 Breakeven Price

Find $P_{oil}$ such that $NPV = 0$:
$$\sum_{t=0}^{n} \frac{(P_{oil} \cdot Q_t - OPEX_t - CAPEX_t - Tax_t(P_{oil}))}{(1+r)^t} = 0$$

---

## 5. Decision Analysis

### 5.1 Multi-Criteria Decision Analysis (MCDA)

**Weighted Sum Model**:
$$S_i = \sum_{j=1}^{m} w_j \cdot \tilde{s}_{ij}$$

**Min-Max Normalization**:

For "higher is better":
$$\tilde{s}_{ij} = \frac{s_{ij} - s_j^{min}}{s_j^{max} - s_j^{min}}$$

For "lower is better":
$$\tilde{s}_{ij} = \frac{s_j^{max} - s_{ij}}{s_j^{max} - s_j^{min}}$$

**Weight Normalization**:
$$w_j^{norm} = \frac{w_j}{\sum_{k=1}^{m} w_k}$$

### 5.2 Portfolio Optimization

**Objective Function**:
$$\max Z = \sum_{i=1}^{n} x_i \cdot NPV_i$$

**Budget Constraint (by year)**:
$$\sum_{i=1}^{n} x_i \cdot CAPEX_{i,t} \leq Budget_t \quad \forall t$$

**Binary Selection**:
$$x_i \in \{0, 1\}$$

**Expected Monetary Value**:
$$EMV_i = P_i \cdot NPV_i - (1-P_i) \cdot C_{dry}$$

Where:
- $P_i$ = probability of success
- $C_{dry}$ = cost of failure (sunk costs)

### 5.3 Value of Information (VoI)

$$VoI = EMV_{with\ info} - EMV_{without\ info}$$

**Perfect Information**:
$$EVPI = \sum_s P(s) \cdot \max_a \{V(a,s)\} - \max_a \{\sum_s P(s) \cdot V(a,s)\}$$

### 5.4 Sensitivity Analysis

**Tornado Analysis** (one-at-a-time):
$$\Delta NPV_i = NPV(x_i^{high}) - NPV(x_i^{low})$$

**Elasticity**:
$$E_i = \frac{\partial NPV / NPV}{\partial x_i / x_i} = \frac{\partial \ln(NPV)}{\partial \ln(x_i)}$$

---

## 6. Uncertainty Quantification

### 6.1 Monte Carlo Simulation

**Expected Value**:
$$E[Y] \approx \frac{1}{N} \sum_{i=1}^{N} f(X_i)$$

**Variance**:
$$Var[Y] \approx \frac{1}{N-1} \sum_{i=1}^{N} (Y_i - \bar{Y})^2$$

**Confidence Interval**:
$$CI_{95\%} = \bar{Y} \pm 1.96 \frac{s}{\sqrt{N}}$$

### 6.2 Distribution Functions

**Triangular**:
$$f(x) = \begin{cases}
\frac{2(x-a)}{(b-a)(c-a)} & a \leq x \leq c \\
\frac{2(b-x)}{(b-a)(b-c)} & c < x \leq b
\end{cases}$$

**Lognormal**:
$$f(x) = \frac{1}{x\sigma\sqrt{2\pi}} \exp\left(-\frac{(\ln x - \mu)^2}{2\sigma^2}\right)$$

**Beta-PERT**:
$$E[X] = \frac{a + 4m + b}{6}, \quad \sigma^2 \approx \frac{(b-a)^2}{36}$$

### 6.3 Percentile Calculation

**P10, P50, P90**:
$$P_{p} = X_{(k)} + d(X_{(k+1)} - X_{(k)})$$

Where $k = \lfloor p(N+1) \rfloor$ and $d = p(N+1) - k$

### 6.4 Correlation Handling

**Rank Correlation (Spearman)**:
$$\rho_s = 1 - \frac{6\sum d_i^2}{N(N^2-1)}$$

**Iman-Conover Method** for inducing correlation in Monte Carlo samples.

---

## 7. Emissions & Sustainability

### 7.1 CO₂ Intensity

$$I_{CO2} = \frac{\sum_{sources} E_{source}}{Q_{oil,equiv}}$$

Units: kg CO₂/boe

### 7.2 Emission Sources

**Fuel Gas Combustion**:
$$E_{fuel} = Q_{fuel} \cdot \rho_{gas} \cdot \frac{M_{CO2}}{M_{CH4}} \cdot (1 + \epsilon)$$

**Flaring**:
$$E_{flare} = Q_{flare} \cdot \rho_{gas} \cdot \frac{44}{16} \cdot \eta_{combustion}$$

**Fugitive Emissions** (Tier 2):
$$E_{fugitive} = \sum_j N_j \cdot EF_j$$

Where $EF_j$ = emission factor for equipment type $j$

### 7.3 Power Generation Emissions

**Gas Turbine**:
$$E_{GT} = \frac{P_{shaft}}{\eta_{th}} \cdot EF_{NG}$$

Where:
- $\eta_{th}$ = thermal efficiency (typically 0.30-0.40)
- $EF_{NG}$ = 56.1 kg CO₂/GJ (natural gas)

**Combined Cycle**:
$$\eta_{CC} = \eta_{GT} + \eta_{ST}(1 - \eta_{GT})$$

### 7.4 Carbon Tax Scenarios

**Norwegian CO₂ Tax (2025)**:
$$Tax_{CO2} = E_{total} \times 2000 \text{ NOK/tonne}$$

**EU ETS Cost**:
$$Cost_{ETS} = E_{total} \times P_{EUA}$$

---

## Implementation Notes

### Numerical Methods

1. **Flash Calculations**: Newton-Raphson with line search
2. **VLE Equilibrium**: Successive substitution with acceleration
3. **Process Simulation**: Sequential modular with tear streams
4. **Optimization**: Greedy heuristics for portfolio, gradient-free for complex objectives

### Convergence Criteria

| Calculation | Tolerance |
|-------------|-----------|
| Flash (mole balance) | $10^{-10}$ |
| Fugacity coefficients | $10^{-8}$ |
| Process simulation | $10^{-6}$ (relative) |
| Economic NPV | $10^{-4}$ MUSD |

### Units Convention

| Quantity | SI Unit | Field Unit |
|----------|---------|------------|
| Pressure | Pa | bara |
| Temperature | K | °C |
| Volume | m³ | Sm³ @ 15°C, 1.01325 bara |
| Mass | kg | tonnes |
| Energy | J | kJ, MW |
| Money | - | MUSD, MNOK |

---

## References

1. Soave, G. (1972). "Equilibrium constants from a modified Redlich-Kwong equation of state." Chem. Eng. Sci., 27(6), 1197-1203.

2. Peng, D.Y. & Robinson, D.B. (1976). "A New Two-Constant Equation of State." Ind. Eng. Chem. Fundam., 15(1), 59-64.

3. Beggs, H.D. & Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes." J. Pet. Technol., 25(5), 607-617.

4. Vogel, J.V. (1968). "Inflow Performance Relationships for Solution-Gas Drive Wells." J. Pet. Technol., 20(1), 83-92.

5. Norwegian Petroleum Directorate (2024). "Petroleum Taxation in Norway."

6. SPE (2023). "Petroleum Resources Management System (PRMS)."
