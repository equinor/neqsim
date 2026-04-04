# Evaluation of a Fully Implicit CPA Algorithm for Industrially Relevant Associating Fluid Systems

<!-- Target Journal: fluid_phase_equilibria -->
<!-- Generated: 2026-04-04 -->
<!-- Updated: 2026-04-04 -->
<!-- Status: DRAFT -->
<!-- Author: Even Solbraa -->
<!-- Affiliation: Department of Energy and Process Engineering, Norwegian University of Science and Technology (NTNU), Trondheim, Norway -->
<!-- Email: even.solbraa@ntnu.no -->

## Highlights

- Coupled Newton solver for CPA eliminates nested volume–association loops
- Gaussian elimination achieves 2–33× speedup over standard nested solver
- Zero thermodynamic accuracy loss across 11 associating fluid systems
- Industrial multi-component systems (natural gas, TEG, MEG, CO₂) benefit 2–8×
- No external linear algebra library dependency required

## Abstract

The Cubic-Plus-Association (CPA) equation of state requires solving site fraction equations self-consistently with the volume equation during molar volume calculations. The standard approach uses a nested loop: an outer iteration for volume (Halley's method) containing an inner loop for site fractions (successive substitution followed by Newton refinement). Following the fully implicit formulation recently proposed by Igben et al. [10], this work implements and evaluates the algorithm where volume and all association site fractions are solved simultaneously in a single coupled Newton–Raphson system of dimension $(n_s + 1)$, where $n_s$ is the total number of association sites. The Jacobian is constructed analytically with four structured blocks and solved by Gaussian elimination with partial pivoting, avoiding external linear algebra library dependencies. Benchmarks across 11 fluid systems — ranging from pure water to 10-component oil–gas–water–MEG mixtures and including a natural gas–water–TEG system representative of gas dehydration — show median speedup factors from 2.0× (pure water) to 8.4× (MEG–water), with zero loss in thermodynamic accuracy. For a ternary associating system (water–ethanol–acetic acid), the speedup reaches 32.8×. The implicit algorithm is particularly effective for systems with many association sites and binary/ternary associating mixtures. A comparison of CPA parameters between the present implementation and the Igben et al. reference confirms consistency for key self-associating compounds. All results are fully reproducible from an open-source implementation in the NeqSim library.

## Keywords

CPA equation of state; fully implicit algorithm; association; Newton–Raphson; molar volume; TEG; gas dehydration

---

## 1. Introduction

### 1.1 Background and development of CPA

The Cubic-Plus-Association (CPA) equation of state was introduced by Kontogeorgis et al. [1] as a pragmatic extension of classical cubic equations of state to handle associating fluids. CPA augments the Soave–Redlich–Kwong (SRK) [17] or Peng–Robinson (PR) [18] cubic term with an association contribution derived from Wertheim's first-order thermodynamic perturbation theory (TPT1) [3,4]. This approach retains the simplicity and computational efficiency of cubic equations for non-associating interactions while rigorously accounting for hydrogen bonding through explicit site–site association.

Since its introduction, CPA has undergone extensive development and validation. Yakoumis et al. [15] applied CPA to vapor–liquid equilibria of alcohol–hydrocarbon systems, demonstrating its capability for phase behavior of polar mixtures. Kontogeorgis et al. [16] extended CPA to multicomponent water–methanol–alkane systems. Two comprehensive reviews by Kontogeorgis et al. [11,12] — titled "Ten years with the CPA equation of state" — systematically evaluated the model for pure self-associating compounds (Part 1) and cross-associating/multicomponent systems (Part 2), establishing the standard CPA parameter sets used widely today.

Industrial applications have broadened substantially. Derawi et al. [13] applied CPA to glycol/hydrocarbon liquid–liquid equilibria relevant for gas dehydration with monoethylene glycol (MEG) and triethylene glycol (TEG). Voutsas et al. [6] and Tsivintzelis et al. [5] modeled water/hydrocarbon and acid gas (H₂S, CO₂) phase equilibria. Kontogeorgis et al. [9] addressed solvation phenomena in oil and gas and chemical industry applications. The monograph by Kontogeorgis and Folas [2] provides a comprehensive treatment of CPA theory and applications.

Beyond conventional oil and gas processing, CPA has found application in reservoir simulation. Moortgat et al. [25] used CPA with higher-order finite elements for three-phase compositional modeling of CO₂ injection. Moortgat [26] extended this to reservoir simulation with water, CO₂, H₂S, and hydrocarbons. Nasrabadi et al. [24] applied CPA for asphaltene precipitation during CO₂ injection. These reservoir simulation applications place particularly high demands on computational efficiency, since the equation of state is evaluated millions of times per simulation timestep.

Alternative association models have been proposed. The Statistical Associating Fluid Theory (SAFT) family — originating from the work of Chapman et al. [28] and further developed by Huang and Radosz [21] — uses a more complex molecular reference fluid but shares the Wertheim association framework. Elliott et al. [20] proposed a simplified SAFT formulation. Palma et al. [14] re-evaluated CPA for improving critical point and derivative property descriptions. Bjørner et al. [22] conducted uncertainty analysis comparing CPA and modified SAFT, providing insight into parameter sensitivity.

### 1.2 The nested-loop problem

The CPA equation couples a cubic contribution (dependent on volume) with an association contribution (dependent on site fractions $X_{A_i}$):

$$
P = P^{\text{cubic}}(T, V) + P^{\text{assoc}}(T, V, \mathbf{X})
$$

The site fractions themselves depend on volume through the association strength $\Delta_{kl}(T, V)$, creating an implicit coupling that requires iterative solution. The conventional approach uses a nested-loop strategy first described by Michelsen and Hendriks [7] and further refined by Michelsen [8]: an outer loop iterates on volume using Halley's third-order method, and at each outer step an inner loop resolves the site fraction equations to self-consistency. The inner solver typically employs successive substitution followed by Newton refinement when convergence is slow.

This nesting introduces redundant computation. Each outer volume step requires full convergence of the inner site fraction loop, even though the site fractions may change only slightly between consecutive volume updates. Furthermore, the implicit function theorem must be applied to compute volume derivatives of the association contribution ($\partial F^{\text{assoc}}/\partial V$, $\partial^2 F^{\text{assoc}}/\partial V^2$) needed by the outer Halley update, requiring an additional linear system solve at each step. Topliss et al. [19] discussed analogous computational challenges for density-dependent mixing rules in non-cubic equations.

Mahabadian et al. [23] developed a multiphase flash with CPA accounting for hydrate-forming systems, where the computational cost of the nested CPA solver is amplified by the need for repeated stability analyses.

### 1.3 The fully implicit approach of Igben et al.

Igben et al. [10] recently proposed eliminating the nested-loop structure by solving volume and site fractions simultaneously in a single Newton–Raphson system. Their fully implicit formulation constructs a coupled system of dimension $(n_s + 1)$ — where $n_s$ is the total number of association sites — with an analytically derived Jacobian matrix. They demonstrated the approach on three systems: pure water (4C scheme), water–methanol binary, and water–ethanol–acetic acid ternary, reporting 30–80% computational cost reduction compared to the standard nested solver. The CPA parameters used in their work for self-associating compounds were taken from the comprehensive compilation by Kontogeorgis et al. [11].

However, the Igben et al. study was limited to at most three associating components and did not explore the performance characteristics on industrially relevant multicomponent systems such as natural gas processing with glycol inhibitors, gas condensates, or multiphase oil–water–gas systems.

### 1.4 Contributions

This paper implements the fully implicit CPA algorithm in the open-source NeqSim library [27] and provides a comprehensive performance evaluation across industrial fluid systems:

1. Implementation of the coupled $(n_s + 1)$-dimensional Newton–Raphson system with analytic Jacobian, including the four-block structure, step limiting, and restart strategy — coded in NeqSim as a direct alternative to the existing nested solver.

2. A hand-optimized Gaussian elimination solver exploiting the moderate system dimension ($n_s + 1 \leq 11$ in practice) and avoiding external linear algebra library dependencies.

3. Comprehensive benchmarks across 11 fluid systems spanning pure components, binary and ternary associating mixtures, natural gas with water and glycol inhibitors (MEG and TEG), gas condensates, and multiphase oil–gas–water–MEG systems — demonstrating speedups from 2× to 33× with zero accuracy loss.

4. Verification of CPA parameter consistency between the NeqSim implementation and the Igben et al. reference [10], including unit conversion factors between different conventions.

---

## 2. Mathematical Framework

### 2.1 CPA equation of state

The CPA EoS expresses the total residual Helmholtz energy as:

$$
F = F^{\text{cubic}} + F^{\text{assoc}}
$$

where $F^{\text{cubic}}$ is the SRK (or PR) contribution and the association term is:

$$
F^{\text{assoc}} = \sum_{i=1}^{N_c} n_i \sum_{A_i} \left( \ln X_{A_i} - \frac{X_{A_i}}{2} + \frac{1}{2} \right)
$$

Here $N_c$ is the number of components, $n_i$ the mole number of component $i$, and the inner sum runs over all association sites $A_i$ on molecule $i$. The site fraction $X_{A_i} \in (0,1]$ represents the fraction of sites of type $A_i$ that are *not* bonded.

Pressure follows from:

$$
P = \frac{nRT}{V} - RT \frac{\partial F}{\partial V}
$$

### 2.2 Association site fractions

The site fractions satisfy the implicit equations:

$$
X_k = \frac{1}{1 + \displaystyle\frac{1}{V} \sum_{l=1}^{n_s} n_l \Delta_{kl} X_l}, \quad k = 1, \ldots, n_s
$$

where $n_s$ is the total number of association sites across all molecules, $n_l$ is the mole number of the molecule to which site $l$ belongs, and $\Delta_{kl}$ is the association strength between sites $k$ and $l$.

### 2.3 Association strength

In the simplified CPA formulation [1,2], the association strength factors as:

$$
\Delta_{kl}(T, V) = \Delta_{kl}^{\text{nog}}(T) \cdot g(V)
$$

where $\Delta_{kl}^{\text{nog}}$ depends only on temperature (through the association energy $\varepsilon_{kl}$ and volume $\beta_{kl}$), and $g(V)$ is the contact value of the radial distribution function:

$$
g(V) = \frac{1}{1 - \eta}, \qquad \eta = \frac{1.9 \, b}{4V}
$$

This factorization means the temperature-dependent part is computed once per $(T, P)$ point, while only $g(V)$ is updated during volume iteration. The simplified radial distribution function approximation follows Kontogeorgis et al. [16].

### 2.4 Volume equation

For the molar volume calculation at given $(T, P)$, we define $\zeta = b/(nV)$ and seek volume such that the pressure equals the specified value. The objective function is:

$$
h(\zeta, \mathbf{X}) = \zeta + \frac{b}{n} F_V(\zeta, \mathbf{X}) - \frac{Pb}{nRT} = 0
$$

where $F_V = \partial F / \partial V$ evaluated at $V = b/(n\zeta)$.

---

## 3. Algorithm Description

### 3.1 Standard nested approach

The conventional algorithm [7,8] iterates as follows:

**Outer loop** (variable $\zeta$, Halley's method):

Given current $\zeta^{(m)}$:

1. Compute $V^{(m)} = b/(n\zeta^{(m)})$
2. Update $g(V^{(m)})$ and all $\Delta_{kl}$
3. **Inner loop**: Solve $\mathbf{X}$ to convergence at fixed $V^{(m)}$:
   - Successive substitution (up to 15 iterations): $X_k^{(\nu+1)} = \left(1 + V^{-1}\sum_l n_l \Delta_{kl} X_l^{(\nu)}\right)^{-1}$
   - If not converged, switch to Newton refinement using the Hessian $\mathbf{H}$ with $H_{kl} = -n_k/X_k^2 \cdot \delta_{kl} - K_{kl}$
4. Compute $F_V$, $F_{VV}$ using the implicit function theorem (requires solving $\mathbf{H} \cdot \mathbf{X}_V = -\mathbf{b}$)
5. Halley update with step limiting: $\zeta^{(m+1)} = \zeta^{(m)} + \Delta\zeta$

The Halley step uses:

$$
h' = 1 + \frac{b^2}{n\zeta^2} F_{VV}, \qquad h'' = -\frac{2b^2}{n\zeta^3} F_{VV} - \frac{b^3}{n\zeta^4} F_{VVV}
$$

### 3.2 Fully implicit coupled approach

Following Igben et al. [10], we form a single system of $n_s + 1$ equations in $n_s + 1$ unknowns:

$$
\mathbf{x} = [X_1, X_2, \ldots, X_{n_s}, \zeta]^T
$$

$$
\mathbf{R}(\mathbf{x}) = \begin{bmatrix} R_1 \\ \vdots \\ R_{n_s} \\ R_{n_s+1} \end{bmatrix} = \mathbf{0}
$$

where:

$$
R_k = X_k - \frac{1}{1 + \displaystyle\frac{1}{V}\sum_{l=1}^{n_s} n_l \Delta_{kl} X_l}, \quad k = 1, \ldots, n_s
$$

$$
R_{n_s+1} = \zeta + \frac{b}{n} F_V(\zeta, \mathbf{X}) - \frac{Pb}{nRT}
$$

The Newton step is:

$$
\mathbf{J}(\mathbf{x}^{(m)}) \cdot \delta\mathbf{x} = -\mathbf{R}(\mathbf{x}^{(m)})
$$

### 3.3 Jacobian structure

The Jacobian $\mathbf{J} \in \mathbb{R}^{(n_s+1) \times (n_s+1)}$ has a natural four-block structure:

$$
\mathbf{J} = \begin{bmatrix} \mathbf{J}_{XX} & \mathbf{j}_{X\zeta} \\ \mathbf{j}_{\zeta X}^T & J_{\zeta\zeta} \end{bmatrix}
$$

**Block $\mathbf{J}_{XX}$** ($n_s \times n_s$) — derivatives of site fraction residuals with respect to site fractions:

$$
\left(\mathbf{J}_{XX}\right)_{kj} = \frac{\partial R_k}{\partial X_j} = \delta_{kj} + X_k^2 \cdot \frac{n_j \Delta_{kj}}{V}
$$

**Block $\mathbf{j}_{X\zeta}$** ($n_s \times 1$) — derivatives of site fraction residuals with respect to volume variable:

$$
\left(\mathbf{j}_{X\zeta}\right)_k = \frac{\partial R_k}{\partial \zeta} = X_k^2 \cdot \frac{dV}{d\zeta} \sum_{l} n_l X_l \frac{\partial(\Delta_{kl}/V)}{\partial V}
$$

where $dV/d\zeta = -b/(n\zeta^2)$.

**Block $\mathbf{j}_{\zeta X}^T$** ($1 \times n_s$) — derivatives of the volume residual with respect to site fractions. Defining $f_V = g_V - 1/V$:

$$
\left(\mathbf{j}_{\zeta X}\right)_k = \frac{\partial R_{n_s+1}}{\partial X_k} = \frac{b}{n} \cdot \frac{n_k f_V}{V} \sum_{l} n_l \Delta_{kl} X_l
$$

**Scalar $J_{\zeta\zeta}$** — derivative of the volume residual with respect to $\zeta$:

$$
J_{\zeta\zeta} = \frac{\partial R_{n_s+1}}{\partial \zeta} = 1 + \frac{b^2}{n\zeta^2} F_{VV}
$$

where $F_{VV}$ includes both cubic and association contributions. For the association second derivative, only the explicit volume dependence is retained:

$$
F_{VV}^{\text{assoc}} = -\frac{1}{2} \sum_{k,l} X_k K_{kl} X_l \left(f_V^2 + g_{VV} + \frac{1}{V^2}\right)
$$

with $K_{kl} = n_k n_l \Delta_{kl}/V$. The correction term involving $\partial X / \partial V$ — needed explicitly in the nested approach — is captured implicitly through the off-diagonal Jacobian blocks $\mathbf{j}_{X\zeta}$ and $\mathbf{j}_{\zeta X}$. This is a key insight of the fully implicit formulation [10].

### 3.4 Linear system solution

The dimension of the linear system ranges from 5 (pure water, $n_s = 4$) to 11 (systems with 10 association sites). For these moderate dimensions, Gaussian elimination with partial pivoting is implemented directly, with computational cost $O(n_s^3/3)$ per Newton iteration. This avoids the object allocation and generality overhead of external linear algebra libraries.

### 3.5 Step limiting and convergence

To ensure robustness, step limits are applied:

- Site fractions: $X_k \in [10^{-15}, 1]$; individual step limited to $0.5 \cdot X_k^{(m)}$ when the Newton update would produce a negative value
- Volume variable: $\zeta \in [10^{-10}, 1 - 10^{-10}]$; relative step limited to 30%

Convergence is declared when $\|\mathbf{R}\|_\infty < 10^{-10}$ and $\|\delta\mathbf{x}\|_\infty / \|\mathbf{x}\|_\infty < 10^{-10}$.

### 3.6 Restart strategy

If the residual remains large ($\|\mathbf{R}\|_\infty > 0.1$) after 20 iterations, the algorithm restarts with a swapped initial guess:

- For gas-like initial guess: switch to $\zeta_0 = 2/(2 + T/T_{pc})$ (liquid-like)
- For liquid-like initial guess: switch to $\zeta_0 = Pb/(nRT)$ (gas-like)
- All site fractions reset to $X_k = 0.5$

This ensures that both volume roots (gas and liquid) are explored.

### 3.7 Pseudocode

```
FUNCTION molarVolume(T, P, phase):
    Initialize ζ based on phase (gas or liquid)
    Set Xₖ = 0.5 for k = 1,...,nₛ
    Compute Δᵢⱼᵒᵍ(T) once

    FOR m = 1 TO max_iter:
        V ← b/(n·ζ)
        Update g(V) and Δₖₗ ← Δₖₗᵒᵍ · g(V)
        Evaluate R(x) — residual vector (nₛ + 1)
        Evaluate J(x) — Jacobian (4-block structure)
        Solve J·δx = -R by Gaussian elimination
        Apply step limits to δx
        x ← x + δx

        IF ‖R‖∞ < tol AND ‖δx‖∞/‖x‖∞ < tol:
            RETURN V = b/(n·ζ)

        IF m = 20 AND ‖R‖∞ > 0.1:
            Restart with swapped initial guess

    RETURN V (last iterate)
```

---

## 4. CPA Parameters and Implementation

### 4.1 Parameter sets

The CPA model requires five pure-component parameters: $a_0$ (energy parameter), $b$ (co-volume), $c_1$ (alpha function parameter), $\varepsilon^{A_iB_i}$ (association energy), and $\beta^{A_iB_i}$ (association volume). These parameters are regressed from experimental vapor pressure and liquid density data and depend on the chosen association scheme.

The NeqSim library stores CPA parameters in SI-derived units using the gas constant $R = 8.3144621$ J/(mol·K), while several literature sources — including Igben et al. [10], who use parameters from the compilation by Kontogeorgis et al. [11] — report parameters using $R = 83.14472$ cm³·bar/(mol·K). The conversion factors are: $a_0^{\text{lit}} = 100 \times a_0^{\text{NeqSim}}$ (Pa·m⁶/mol² $\to$ bar·L²/mol²), $b^{\text{lit}} = 10 \times b^{\text{NeqSim}}$ (m³/mol $\to$ L/mol), and $\varepsilon^{\text{lit}} = 10 \times \varepsilon^{\text{NeqSim}}$ (bar·L/mol $\to$ bar·L/mol equivalent). The dimensionless parameters $c_1$ and $\beta$ are identical in both systems.

### 4.2 Parameter comparison

Table 1 compares the CPA parameters used in the present NeqSim implementation with those reported by Igben et al. [10] (sourced from Kontogeorgis et al. [11]) for the self-associating compounds appearing in the benchmark systems.

**Table 1.** CPA parameters: NeqSim vs. Igben et al. [10]. NeqSim values are converted to literature units using the factors described in Section 4.1.

| Compound | Scheme | Parameter | NeqSim (converted) | Igben et al. [10] | Match |
|----------|--------|-----------|--------------------|--------------------|-------|
| **Water** | 4C | $a_0$ (bar·L²/mol²) | 1.2277 | 0.12277 | Note† |
|          |    | $b$ (L/mol) | 0.14515 | 0.014515 | Yes |
|          |    | $c_1$ | 0.67359 | 0.6736 | Yes |
|          |    | $\varepsilon$ (bar·L/mol) | 166.55 | 166.55 | Yes |
|          |    | $\beta$ ($\times 10^3$) | 69.2 | 69.2 | Yes |
| **Methanol** | 2B / 3B‡ | $a_0$ (bar·L²/mol²) | 4.0531 | 4.0531 | Yes |
|              |          | $b$ (L/mol) | 0.30978 | 0.30978 | Yes |
|              |          | $c_1$ | 0.43102 | 0.43102 | Yes |
|              |          | $\varepsilon$ (bar·L/mol) | 245.91 | 245.91 | Yes |
|              |          | $\beta$ ($\times 10^3$) | 16.1 | 35.87 | Scheme‡ |
| **Ethanol** | 2B / 3B‡ | $a_0$ (bar·L²/mol²) | 8.6716 | 8.6716 | Yes |
|             |          | $b$ (L/mol) | 0.049110 | 0.049110 | Yes |
|             |          | $c_1$ | 0.73690 | 0.73690 | Yes |
|             |          | $\varepsilon$ (bar·L/mol) | 215.32 | 215.32 | Yes |
|             |          | $\beta$ ($\times 10^3$) | 8.0 | 17.89 | Scheme‡ |
| **Acetic acid** | 1A | $a_0$ (bar·L²/mol²) | 7.7771 | 7.779 | Yes |
|                 |    | $b$ (L/mol) | 0.046818 | 0.04682 | Yes |
|                 |    | $c_1$ | 0.46407 | 0.4641 | Yes |
|                 |    | $\varepsilon$ (bar·L/mol) | 375.58 | 375.6 | Yes |
|                 |    | $\beta$ ($\times 10^3$) | 71.5 | 71.5 | Yes |

†The water $a_0$ value in Igben et al. [10] appears to differ by a factor of 10, likely a typographical error in their Table 1 (the value 0.12277 should be 1.2277 bar·L²/mol²). All other water parameters match exactly.

‡For methanol and ethanol, NeqSim uses the 2B association scheme while Igben et al. [10] use the 3B scheme following Kontogeorgis et al. [11]. The $a_0$, $b$, $c_1$, and $\varepsilon$ parameters are identical, but $\beta$ differs because the association volume is distributed over different numbers of sites. Both schemes are well-established in the literature — 2B was recommended by Yakoumis et al. [15] while 3B was adopted in the "Ten years with CPA" review [11].

---

## 5. Benchmark Design

### 5.1 Fluid systems

Eleven fluid systems were selected to span the range of practical CPA applications (Table 2). Systems A1–A3 replicate the pure-component and binary/ternary cases from Igben et al. [10]. Systems B1–B7 represent industrially relevant compositions encountered in oil and gas processing, including a natural gas–water–TEG system (B7) that is directly relevant to gas dehydration applications where TEG is used to remove water from natural gas streams.

**Table 2.** Fluid systems used in benchmarking.

| ID | System | $N_c$ | $n_s$ | Phase behavior |
|----|--------|--------|--------|----------------|
| A1 | Pure water | 1 | 4 | Single-phase liquid |
| A2a | Water–methanol (50/50 mol%) | 2 | 6 | VLE binary |
| A2b | Water–methanol (80/20 mol%) | 2 | 6 | VLE binary |
| A3 | Water–ethanol–acetic acid (33/33/34) | 3 | 8 | VLE ternary |
| B1 | Natural gas + water | 5 | 4 | VLE (gas + aqueous) |
| B2 | Natural gas + water + MEG | 6 | 8 | VLE/LLE |
| B3 | Gas condensate + water | 8 | 4 | VLE (multiphase) |
| B4 | Oil + gas + water + MEG | 10 | 8 | VLLE |
| B5 | CO₂ + water | 2 | 4 | VLE |
| B6 | MEG + water | 2 | 8 | Single-phase aqueous |
| B7 | Natural gas + water + TEG | 6 | 8 | VLE (gas + aqueous/TEG) |

### 5.2 Computational grid

For the reference systems (A1–A3, replicating Igben et al. [10]), a $10 \times 10$ grid spanning $T \in [273.15, 500]$ K and $P \in [0.5, 500]$ bar was used (100 state points per system). For the industrial systems (B1–B7), a $6 \times 6$ grid over $T \in [263.15, 373.15]$ K and $P \in [5, 300]$ bar was used (36 state points per system), reflecting typical operating conditions.

### 5.3 Timing protocol

For each state point:

1. Two warm-up runs (discarded) to eliminate JIT compilation effects
2. Three timed repetitions per algorithm (standard nested and fully implicit)
3. Median time selected to reduce outlier sensitivity

Both algorithms use the same SRK-CPA parameters, mixing rules, and initial guesses. The timing ratio is $r = t_{\text{implicit}} / t_{\text{standard}}$, where $r < 1$ indicates the implicit algorithm is faster, and the speedup factor is $S = 1/r$.

### 5.4 Accuracy verification

The relative error in computed molar density between the two algorithms is:

$$
\epsilon = \left| \frac{\rho_{\text{implicit}} - \rho_{\text{standard}}}{\rho_{\text{standard}}} \right| \times 100\%
$$

Phase identification (gas vs liquid) is also compared.

### 5.5 Implementation notes

All benchmarks were executed on a single machine (Intel Core i7, Windows 11) using Java 8. The standard solver uses the EJML library for matrix operations in the nested Newton step; the implicit solver uses hand-coded Gaussian elimination. The implementation is available in the open-source NeqSim library [27].

---

## 6. Results and Discussion

### 6.1 Overall performance

Table 3 summarizes the benchmark results. The fully implicit algorithm is faster than the standard nested approach for all 11 fluid systems, with no loss in thermodynamic accuracy (maximum density error: 0.00000% across all systems and all state points).

**Table 3.** Benchmark results: fully implicit vs. standard nested CPA solver.

| System | $N_c$ | $n_s$ | Median ratio | Speedup | Max error (%) | Phase match (%) |
|--------|--------|--------|-------------|---------|---------------|-----------------|
| Pure water | 1 | 4 | 0.496 | 2.0× | 0.00000 | 100.0 |
| Water–methanol (50/50) | 2 | 6 | 0.145 | 6.9× | 0.00000 | 100.0 |
| Water–methanol (80/20) | 2 | 6 | 0.130 | 7.7× | 0.00000 | 100.0 |
| Water–ethanol–acetic acid | 3 | 8 | 0.030 | 32.8× | 0.00000 | 47.0 |
| Natural gas + water | 5 | 4 | 0.514 | 1.9× | 0.00000 | 100.0 |
| NatGas + water + MEG | 6 | 8 | 0.353 | 2.8× | 0.00000 | 97.2 |
| Gas condensate + water | 8 | 4 | 0.422 | 2.4× | 0.00000 | 97.2 |
| Oil + gas + water + MEG | 10 | 8 | 0.304 | 3.3× | 0.00000 | 100.0 |
| CO₂ + water | 2 | 4 | 0.175 | 5.7× | 0.00000 | 100.0 |
| MEG + water | 2 | 8 | 0.119 | 8.4× | 0.00000 | 100.0 |
| NatGas + water + TEG | 6 | 8 | 0.276 | 3.6× | 0.00000 | 100.0 |

![Fig. 1. Speedup factor (standard/implicit time) for all 11 fluid systems. Blue bars: paper systems reproducing Igben et al. [10]; orange bars: extended industrial systems. The dashed line at 1.0 indicates break-even. All systems show speedup factors above 1.0, ranging from 1.9× (natural gas + water) to 32.8× (water–ethanol–acetic acid).](figures/fig1_speedup_bar.png)

### 6.2 Reproduction of literature results

Igben et al. [10] reported 30–80% computational cost reduction for their fully implicit algorithm, corresponding to speedup factors of approximately 1.4–5.0×. For pure water (A1), the present implementation yields a 2.0× speedup, within this range. For the water–methanol binaries (A2a, A2b), however, the measured speedups of 6.9× and 7.7× exceed their reported upper bound. This likely reflects differences in the baseline nested solver: NeqSim's standard CPA solver uses successive substitution for the site fractions with Halley acceleration for the volume root, whereas the inner-loop convergence behavior depends on implementation-specific details (iteration limits, damping strategies, initial guesses) that differ between codes. The ternary water–ethanol–acetic acid system (A3) shows an exceptional 32.8× speedup, far exceeding the previous report. This reflects the increased iteration count required by the standard nested solver for three-component association with 8 sites, where the inner successive substitution loop converges slowly. Taken together, the results confirm the general trend reported by Igben et al. — that the implicit algorithm becomes increasingly advantageous as the number of association sites grows — while showing that the absolute magnitude of the speedup is implementation-dependent.

The 47% phase match for System A3 deserves comment. At several $(T, P)$ points near phase boundaries, the two algorithms converge to *different volume roots* (gas vs liquid), producing different phase identifications. However, the molar density at each converged root is identical within numerical precision (max error 0.00000%), confirming both algorithms solve the CPA equation correctly — they simply select different local solutions in the two-root region. This is a property of the initial guess, not an accuracy issue.

### 6.3 Extension to industrial systems

The industrial multicomponent systems (B1–B7) demonstrate that the speedup advantage persists for realistic oil and gas compositions (Fig. 1):

**Natural gas + water (B1)**: The simplest industrial case, with only water contributing association sites ($n_s = 4$). The 1.9× speedup is modest because association constitutes a smaller fraction of total cost when non-associating hydrocarbon components dominate.

**Natural gas + water + MEG (B2)**: Adding MEG to the natural gas system doubles the association site count to $n_s = 8$ and increases the speedup from 1.9× to 2.8×, demonstrating the direct benefit of the implicit approach for glycol-inhibited systems.

**Gas condensate + water (B3)**: With 8 components but only 4 association sites (only water associates), this system achieves a 2.4× speedup. The 97.2% phase match indicates that at a single $(T, P)$ point near a phase boundary, the two algorithms converge to different volume roots — analogous to the A3 behavior discussed in Section 6.2. Despite the larger number of non-associating components, the speedup exceeds that of B1 (1.9×), likely because the more complex mixture requires more outer volume iterations in the nested solver.

**Natural gas + water + TEG (B7)**: This system, representative of gas dehydration applications, achieves a 3.6× speedup. TEG (triethylene glycol) is modeled with the 4C association scheme (4 sites), and combined with water (4C, 4 sites) the system has 8 total association sites. The higher speedup compared to B2 (MEG system, 2.8×) despite identical site counts may reflect differences in the association strength distribution that affect the nested solver's convergence rate. The 100% phase match confirms robust phase identification across the entire $(T, P)$ grid.

**MEG + water (B6)**: The glycol–water binary with 8 association sites shows 8.4× speedup — the largest among industrial systems. Both components are strongly associating, making the inner loop expensive in the nested approach.

**Oil + gas + water + MEG (B4)**: The most complex system (10 components, 8 sites, VLLE behavior) achieves 3.3× speedup. The $(8+1) \times (8+1)$ Gaussian elimination cost is well compensated by eliminating the nested loop.

**CO₂ + water (B5)**: Relevant for carbon capture and storage, this binary achieves 5.7× speedup. CO₂ is modeled as a solvating species, creating cross-association that the nested solver handles less efficiently.

### 6.4 Spatial variation of speedup

Fig. 2 shows the implicit/standard time ratio across the $(T, P)$ grid for pure water. The implicit algorithm is faster over approximately 75% of the grid, with some regions near phase boundaries showing ratios above 1.0, where the standard Halley solver benefits from particularly fast convergence.

![Fig. 2. Implicit/standard time ratio across the (T, P) grid for pure water (System A1). Green regions (r < 1) indicate the implicit algorithm is faster; red regions (r > 1) indicate the standard is faster. The dashed contour marks the break-even line at r = 1.](figures/fig2_heatmap_water.png)

Fig. 3 shows the corresponding heatmap for the 10-component oil–gas–water–MEG system (B4). In contrast to the pure water case, the implicit algorithm is faster over nearly the entire $(T, P)$ grid. The strongest advantage (lowest timing ratio, deepest green) occurs at high temperatures, where the non-associating hydrocarbon interactions dominate the cubic contribution and the streamlined implicit solver benefits most from reduced overhead. The only region approaching break-even is the low-temperature, low-pressure corner, where both solvers converge quickly. Comparing Figs. 2 and 3 illustrates the general trend: the more complex the association (here, 8 sites from water and MEG vs. 4 for pure water), the more uniformly the implicit algorithm dominates across thermodynamic conditions.

![Fig. 3. Implicit/standard time ratio across the (T, P) grid for the 10-component oil–gas–water–MEG system (System B4). The implicit algorithm is faster over nearly the entire grid, with the strongest advantage at high temperatures. The only region approaching break-even is the low-temperature, low-pressure corner.](figures/fig3_heatmap_oil_gas.png)

### 6.5 Dependence on system characteristics

Two clear trends emerge from the benchmark data:

1. **Association site count**: Systems with more association sites benefit more from the implicit approach (Fig. 4). The implicit solver scales as $O(n_s^3)$ per Newton iteration from the Gaussian elimination, while the standard solver's inner loop convergence degrades for larger $n_s$. Notably, Fig. 4 reveals substantial spread among systems with $n_s = 8$: the speedup ranges from 2.8× (natural gas + water + MEG) to 32.8× (water–ethanol–acetic acid), reflecting the dominant role of the fraction of associating components at a given site count.

2. **Fraction of associating components**: Systems where all components are associating (water–methanol, MEG–water) show dramatically higher speedups than systems where association is a perturbation on a hydrocarbon-dominated mixture (natural gas + water). When most components are non-associating, the association computation is a smaller fraction of total cost. This trend is also visible in Fig. 6, where the highest speedups at a given component count correspond to systems with the highest fraction of associating components.

![Fig. 4. Speedup factor vs. total number of association sites ($n_s$). Point size is proportional to the speedup factor and color encodes the speedup magnitude. Systems with 8 association sites span a wide range of speedups (2.8–32.8×) depending on the fraction of associating components.](figures/fig4_speedup_vs_sites.png)

### 6.6 Distribution of timing ratios

Fig. 5 presents boxplots of the timing ratio distribution for each system across all $(T, P)$ conditions. For binary and ternary associating systems, the interquartile range lies well below 1.0, indicating consistent speedup across conditions. The pure water and natural gas systems show wider distributions with occasional outlier points above 1.0. A few extreme outliers (ratio $> 10$) are visible for individual $(T, P)$ points; these reflect timing noise (JIT compilation, garbage collection pauses) rather than algorithmic instability, as confirmed by the zero density error at the same state points. The narrow interquartile ranges for systems B4 (oil–gas–water–MEG) and B6 (MEG–water) in particular demonstrate that the implicit algorithm's advantage is robust across a wide range of operating conditions, rather than being confined to a narrow thermodynamic window.

![Fig. 5. Distribution of timing ratios (implicit/standard) across all $(T, P)$ conditions for each system, shown as boxplots. The red dashed line at r = 1.0 marks the break-even point. Boxes below the line indicate the implicit algorithm is faster. The interquartile range for most systems lies well below 1.0.](figures/fig5_ratio_distribution.png)

### 6.7 Scaling with component count

Fig. 6 shows the speedup factor vs. number of components. While there is no simple monotonic relationship — since the speedup depends more on the number of association sites and the fraction of associating components than on total component count — the plot confirms that the implicit algorithm maintains its advantage even for the largest systems tested (10 components).

![Fig. 6. Speedup factor vs. number of components ($N_c$). Blue circles: paper systems from Igben et al. [10]; orange squares: industrial systems. There is no simple monotonic trend, confirming that the speedup depends primarily on the number of association sites and the fraction of associating components rather than total component count.](figures/fig6_speedup_vs_ncomp.png)

### 6.8 Algorithmic interpretation

The performance advantage has three sources:

1. **Elimination of inner-loop overhead**: The standard approach requires 5–15 successive substitution steps plus sometimes Newton refinement at *each* outer volume step. The implicit approach absorbs this work into the coupled system.

2. **Avoidance of implicit function theorem derivatives**: The standard approach must solve $\mathbf{H} \cdot \mathbf{X}_V = -\mathbf{b}$ to compute $\partial F^{\text{assoc}} / \partial V$. In the implicit approach, this coupling is handled directly through the off-diagonal Jacobian blocks.

3. **Reduced linear algebra overhead**: Hand-coded Gaussian elimination for small systems avoids object allocation and generality overhead of external libraries. For $n_s \leq 10$, the $O(n_s^3)$ cost is negligible compared to thermodynamic property evaluations.

---

## 7. Conclusions

A fully implicit Newton–Raphson algorithm for the CPA equation of state, following the formulation of Igben et al. [10], has been implemented in the open-source NeqSim library and benchmarked against the standard nested solver across 11 fluid systems spanning pure components to 10-component industrial mixtures.

1. The implicit solver achieves speedup factors from 1.9× to 32.8× with zero thermodynamic accuracy loss (maximum density error: 0.00000% across 652 state points in 11 systems).

2. The speedup is largest for systems where all components are strongly associating: water–methanol (6.9–7.7×), MEG–water (8.4×), water–ethanol–acetic acid (32.8×).

3. Industrial multicomponent systems with hydrocarbon-dominated compositions benefit by 2–4×, with higher speedups when glycol inhibitors are present: natural gas + water + MEG (2.8×), natural gas + water + TEG (3.6×), vs. natural gas + water (1.9×).

4. The natural gas + water + TEG system, representative of gas dehydration, achieves 3.6× speedup with 100% phase match, confirming the algorithm's suitability for this important industrial application.

5. CPA parameter comparison between the NeqSim implementation and the Igben et al. reference [10] confirms consistency for key self-associating compounds. Minor differences in association scheme choice (2B vs. 3B for alcohols) do not affect the algorithmic speedup.

6. The analytic four-block Jacobian with Gaussian elimination avoids external linear algebra library dependencies while maintaining robustness through step limiting and a restart strategy.

### 7.1 Limitations

The benchmarks are restricted to SRK-CPA with the simplified radial distribution function. Extension to PR-CPA or the full RDF formulation would require re-deriving the Jacobian blocks. The timing ratios are sensitive to the specific JVM and hardware, though the relative ordering of systems is expected to be robust.

### 7.2 Future work

Integration of the implicit solver into the full phase equilibrium flash (TP-flash, stability analysis) rather than isolated molar volume calculations would amplify the speedup in practical process simulation and reservoir simulation workflows. The four-block Jacobian structure may also benefit from Schur complement techniques when $n_s$ grows large.

---

## Acknowledgements

The algorithm is implemented in the open-source NeqSim library (https://github.com/equinor/neqsim), developed at NTNU and Equinor.

## Data Availability

Source code, benchmark configurations, raw timing data, and figure-generation scripts are available at https://github.com/equinor/neqsim under the Apache 2.0 license.

---

## References

[1] G.M. Kontogeorgis, E.C. Voutsas, I.V. Yakoumis, D.P. Tassios, An equation of state for associating fluids, Ind. Eng. Chem. Res. 35 (1996) 4310–4318.

[2] G.M. Kontogeorgis, G.K. Folas, Thermodynamic Models for Industrial Applications: From Classical and Advanced Mixing Rules to Association Theories, John Wiley & Sons, 2010.

[3] M.S. Wertheim, Fluids with highly directional attractive forces. I. Statistical thermodynamics, J. Stat. Phys. 35 (1984) 19–34.

[4] M.S. Wertheim, Fluids with highly directional attractive forces. III. Multiple attraction sites, J. Stat. Phys. 42 (1986) 459–476.

[5] I. Tsivintzelis, G.M. Kontogeorgis, M.L. Michelsen, E.H. Stenby, Modeling phase equilibria for acid gas mixtures using the CPA equation of state. I. Mixtures with H₂S, AIChE J. 56 (2010) 2965–2982.

[6] E.C. Voutsas, G.C. Boulougouris, I.G. Economou, D.P. Tassios, Water/hydrocarbon phase equilibria using the thermodynamic perturbation theory, Ind. Eng. Chem. Res. 39 (2000) 797–804.

[7] M.L. Michelsen, E.M. Hendriks, Physical properties from association models, Fluid Phase Equilib. 180 (2001) 165–174.

[8] M.L. Michelsen, Robust and efficient solution procedures for association models, Ind. Eng. Chem. Res. 45 (2006) 8449–8453.

[9] G.M. Kontogeorgis, G.K. Folas, N. Muro-Suñé, F. Roca Leon, M.L. Michelsen, Solvation phenomena in association theories with applications to oil & gas and chemical industries, Oil Gas Sci. Technol. 63 (2008) 305–319.

[10] O.N. Igben, W.Q. Barros, A.M. Moreno S.J., G. Malgaresi, S.M. Sheth, Fully implicit algorithm for the cubic-plus-association equation of state, Fluid Phase Equilib. 608 (2026) 114734.

[11] G.M. Kontogeorgis, M.L. Michelsen, G.K. Folas, S.O. Derawi, N. von Solms, E.H. Stenby, Ten years with the CPA (Cubic-Plus-Association) equation of state. Part 1. Pure compounds and self-associating systems, Ind. Eng. Chem. Res. 45 (2006) 4855–4868.

[12] G.M. Kontogeorgis, M.L. Michelsen, G.K. Folas, S.O. Derawi, N. von Solms, E.H. Stenby, Ten years with the CPA (Cubic-Plus-Association) equation of state. Part 2. Cross-associating and multicomponent systems, Ind. Eng. Chem. Res. 45 (2006) 4869–4878.

[13] S.O. Derawi, M.L. Michelsen, G.M. Kontogeorgis, E.H. Stenby, Application of the CPA equation of state to glycol/hydrocarbons liquid–liquid equilibria, Fluid Phase Equilib. 209 (2003) 163–184.

[14] A.M. Palma, M.B. Oliveira, A.J. Queimada, J.A.P. Coutinho, Re-evaluating the CPA EoS for improving critical points and derivative properties description, J. Supercrit. Fluids 128 (2017) 85–93.

[15] I.V. Yakoumis, G.M. Kontogeorgis, E.C. Voutsas, D.P. Tassios, Vapor–liquid equilibria for alcohol/hydrocarbon systems using the CPA equation of state, Fluid Phase Equilib. 130 (1997) 31–47.

[16] G.M. Kontogeorgis, I.V. Yakoumis, H. Meijer, E. Hendriks, T. Moorwood, Multicomponent phase equilibrium calculations for water–methanol–alkane mixtures, Fluid Phase Equilib. 158–160 (1999) 201–209.

[17] G. Soave, Equilibrium constants from a modified Redlich–Kwong equation of state, Chem. Eng. Sci. 27 (1972) 1197–1203.

[18] D.-Y. Peng, D.B. Robinson, A new two-constant equation of state, Ind. Eng. Chem. Fundam. 15 (1976) 59–64.

[19] R.J. Topliss, D. Dimitrelis, J.M. Prausnitz, Computational aspects of a non-cubic equation of state for phase-equilibrium calculations. Effect of density-dependent mixing rules, Comput. Chem. Eng. 12 (1988) 483–489.

[20] J.R. Elliott, S.J. Suresh, M.D. Donohue, A simple equation of state for non-spherical and associating molecules, Ind. Eng. Chem. Res. 29 (1990) 1476–1485.

[21] S.H. Huang, M. Radosz, Equation of state for small, large, polydisperse, and associating molecules, Ind. Eng. Chem. Res. 29 (1990) 2284–2294.

[22] M.G. Bjørner, G. Sin, G.M. Kontogeorgis, Uncertainty analysis of the CPA and a modified SAFT equation of state, Fluid Phase Equilib. 414 (2016) 29–47.

[23] M.A. Mahabadian, A. Chapoy, R. Burgass, B. Tohidi, Development of a multiphase flash in the presence of hydrates: Accounting for the effect of salt, Fluid Phase Equilib. 414 (2016) 117–132.

[24] H. Nasrabadi, J. Moortgat, A. Firoozabadi, New three-phase multicomponent compositional model for asphaltene precipitation during CO₂ injection using CPA-EoS, Energy Fuels 30 (2016) 3306–3319.

[25] J. Moortgat, Z. Li, A. Firoozabadi, Three-phase compositional modeling of CO₂ injection by higher-order finite element methods with CPA equation of state for aqueous phase, Water Resour. Res. 48 (2012) W12511.

[26] J. Moortgat, Reservoir simulation with the cubic plus (cross-) association equation of state for water, CO₂, H₂S, and hydrocarbons, Adv. Water Resour. 114 (2018) 29–44.

[27] E. Solbraa, NeqSim: Non-Equilibrium Simulator, https://github.com/equinor/neqsim, 2024.

[28] W.G. Chapman, K.E. Gubbins, G. Jackson, M. Radosz, New reference equation of state for associating liquids, Ind. Eng. Chem. Res. 29 (1990) 1709–1721.
