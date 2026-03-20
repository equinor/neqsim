---
title: "Phase Envelope Algorithm: Michelsen's Continuation Method"
description: "Detailed mathematical and algorithmic documentation for the PT phase envelope tracer in NeqSim. Covers the Newton-Raphson continuation method, variable system, critical point detection, quality line tracing, and step size control."
---

# Phase Envelope Algorithm

NeqSim traces the PT phase envelope using Michelsen's continuation method (Michelsen & Mollerup, *Thermodynamic Models: Fundamentals & Computational Aspects*, 2nd ed., 2007). This document describes the mathematical formulation, the Newton-Raphson solver, critical point detection, quality line tracing, and the adaptive step-size control implemented in `PTPhaseEnvelopeMichelsen` and `SysNewtonRhapsonPhaseEnvelope`.

## 1. Problem Formulation

At every point on the phase boundary, the following $n_c + 2$ equations hold simultaneously:

$$
\ln K_i + \ln \hat{\varphi}_i^{\text{vap}}(T, P, \mathbf{y}) - \ln \hat{\varphi}_i^{\text{liq}}(T, P, \mathbf{x}) = 0, \quad i = 1, \dots, n_c
$$

$$
\sum_i y_i - \sum_i x_i = 0
$$

$$
u_{s} - S = 0 \qquad \text{(specification equation)}
$$

where:

- $K_i = y_i / x_i$ is the equilibrium ratio for component $i$
- $\hat{\varphi}_i$ is the fugacity coefficient from the equation of state
- $T$ and $P$ are temperature and pressure
- $u_s$ is the specification variable (one of $\ln K_i$, $\ln T$, or $\ln P$)
- $S$ is the specification value

The unknowns are collected into a vector $\mathbf{u}$:

$$
\mathbf{u} = [\ln K_1, \ln K_2, \dots, \ln K_{n_c}, \ln T, \ln P]^T
$$

Using logarithmic variables ensures positivity of $K$, $T$, and $P$ naturally.

### Phase Compositions

The liquid and vapor mole fractions are computed from the Rachford-Rice equation at a given vapor mole fraction $\beta$:

$$
x_i = \frac{z_i}{1 - \beta + \beta K_i}, \qquad y_i = \frac{K_i z_i}{1 - \beta + \beta K_i}
$$

For the **dew point curve**, $\beta \to 1$ (i.e., `phaseFraction` $\approx 1 - 10^{-10}$).
For the **bubble point curve**, $\beta \to 0$ (i.e., `phaseFraction` $\approx 10^{-10}$).
For **quality lines**, $0 < \beta < 1$.

## 2. Jacobian Matrix

The Jacobian $\mathbf{J}$ of the system has $(n_c + 2) \times (n_c + 2)$ entries:

**Rows 1 to $n_c$** (fugacity equations):

$$
J_{ij} = \delta_{ij} + \sum_k \left( \frac{\partial \ln \hat{\varphi}_i^{\text{vap}}}{\partial y_k} \frac{\partial y_k}{\partial \ln K_j} - \frac{\partial \ln \hat{\varphi}_i^{\text{liq}}}{\partial x_k} \frac{\partial x_k}{\partial \ln K_j} \right)
$$

$$
J_{i, n_c+1} = T \left( \frac{\partial \ln \hat{\varphi}_i^{\text{vap}}}{\partial T} - \frac{\partial \ln \hat{\varphi}_i^{\text{liq}}}{\partial T} \right)
$$

$$
J_{i, n_c+2} = P \left( \frac{\partial \ln \hat{\varphi}_i^{\text{vap}}}{\partial P} - \frac{\partial \ln \hat{\varphi}_i^{\text{liq}}}{\partial P} \right)
$$

**Row $n_c + 1$** (material balance):

$$
J_{n_c+1, j} = \frac{\partial y_j}{\partial \ln K_j} - \frac{\partial x_j}{\partial \ln K_j}
$$

**Row $n_c + 2$** (specification):

$$
J_{n_c+2, s} = 1, \quad J_{n_c+2, j \ne s} = 0
$$

## 3. Continuation Method

### 3.1 Sensitivity Vector

At each converged point, the **sensitivity vector** $d\mathbf{u}/ds$ is computed by solving:

$$
\mathbf{J} \cdot \frac{d\mathbf{u}}{ds} = \mathbf{e}_{n_c+2}
$$

where $\mathbf{e}_{n_c+2}$ is a unit vector with 1 in the last row (specification equation).

### 3.2 Specification Variable Selection

Michelsen (2007) recommends choosing the specification variable as the component of $d\mathbf{u}/ds$ with the largest absolute value:

$$
s = \arg\max_j \left| \frac{du_j}{ds} \right|
$$

This keeps the Jacobian well-conditioned near turning points (cricondenbar, cricondentherm) and the critical region where the standard choices ($\ln P$ or $\ln T$) would lead to singular or near-singular systems.

In NeqSim, the first 5 points use $\ln P$ as the specification variable (simple pressure stepping from the starting low pressure), then switch to the sensitivity-based selection.

### 3.3 Polynomial Extrapolation

The next point estimate uses **cubic polynomial extrapolation** from the last 4 converged points. Given stored solutions $\mathbf{u}_1, \mathbf{u}_2, \mathbf{u}_3, \mathbf{u}_4$ at specification values $s_1, s_2, s_3, s_4$, the cubic polynomial coefficients are found by solving:

$$
\begin{bmatrix} 1 & s_1 & s_1^2 & s_1^3 \\ 1 & s_2 & s_2^2 & s_2^3 \\ 1 & s_3 & s_3^2 & s_3^3 \\ 1 & s_4 & s_4^2 & s_4^3 \end{bmatrix}
\begin{bmatrix} c_0 \\ c_1 \\ c_2 \\ c_3 \end{bmatrix}
=
\begin{bmatrix} u_j^{(1)} \\ u_j^{(2)} \\ u_j^{(3)} \\ u_j^{(4)} \end{bmatrix}
$$

The next specification value is $s_5 = s_4 + \Delta s \cdot (d\mathbf{u}/ds)_s$, and the initial estimate is:

$$
u_j^{(5)} = c_0 + c_1 s_5 + c_2 s_5^2 + c_3 s_5^3
$$

### 3.4 Adaptive Step Size

The arc-length step $\Delta s$ is adjusted adaptively based on Newton convergence:

$$
\Delta s_{\text{new}} = \Delta s_{\text{old}} \cdot \sqrt{\frac{n_{\text{target}}}{n_{\text{actual}}}}
$$

where $n_{\text{target}} = 4$ (target Newton iterations) and $n_{\text{actual}}$ is the actual iteration count. The scale factor is clamped to $[0.25, 2.0]$ to avoid excessively large or small steps.

Additionally, absolute step limits enforce:

$$
|\Delta \ln T| \le \ln\left(1 + \frac{\Delta T_{\max}}{T}\right), \qquad |\Delta \ln P| \le \ln\left(1 + \frac{\Delta P_{\max}}{P}\right)
$$

with $\Delta T_{\max} = 10$ K and $\Delta P_{\max} = 10$ bar.

## 4. Newton-Raphson Solver

Each point on the envelope is converged using Newton-Raphson iteration with **damped steps** and **iterative backtracking**:

### 4.1 Damped Newton Step

At each iteration $k$:

$$
\mathbf{u}^{(k+1)} = \mathbf{u}^{(k)} - \lambda \cdot \mathbf{J}^{-1} \mathbf{f}
$$

where the damping factor $\lambda$ is:

$$
\lambda = \begin{cases} 1 & \text{if } \|\Delta \mathbf{u}^{(k)}\| \le 2 \|\Delta \mathbf{u}^{(k-1)}\| \\ \|\Delta \mathbf{u}^{(k-1)}\| / \|\Delta \mathbf{u}^{(k)}\| & \text{otherwise, clamped to } [0.1, 1] \end{cases}
$$

This prevents overshooting near turning points and in the critical region.

### 4.2 Convergence Criteria

Convergence requires **both** norms to be small (dual criterion):

$$
\|\mathbf{J}^{-1} \mathbf{f}\|_2 < 10^{-6} \quad \text{AND} \quad \|\mathbf{f}\|_2 < 10^{-6}
$$

The dual check prevents false convergence where the correction is small but the residual is not.

### 4.3 Backtracking

If Newton iteration does not converge within 50 iterations, the solver backtracks to the last converged point and halves the step size:

$$
\Delta s_{\text{bt}} = \Delta s \cdot 0.5^k, \quad k = 1, 2, \dots, 15
$$

After 15 failed backtrack attempts, a `RuntimeException` signals failure to `PTPhaseEnvelopeMichelsen`, which then either terminates the current tracing pass or starts a second pass from the opposite end.

## 5. Two-Pass Envelope Tracing

The envelope is traced in up to two passes:

**Pass 1**: Start from the specified end (dew or bubble) at low pressure but trace towards and through the critical point to the other branch.

**Pass 2** (if needed): If Pass 1 fails before completing (solver divergence near the critical region), the algorithm:
1. Saves all cricondenbar/cricondentherm data from Pass 1
2. Flips `bubblePointFirst` and `phaseFraction`
3. Restarts from the opposite end

This two-pass approach ensures that both dew and bubble branches are captured even when the continuation method fails near the critical point.

## 6. Critical Point Detection

### 6.1 Criterion

The critical point is detected using the Michelsen criterion based on the sum of squared log K-values:

$$
\Sigma = \sum_{i=1}^{n_c} (\ln K_i)^2
$$

When $\Sigma < 0.01$, all equilibrium ratios are approaching unity ($K_i \to 1$), indicating the critical point.

### 6.2 Newton Refinement

After detection, the critical point estimate from polynomial extrapolation is refined by Newton-Raphson iteration. The specification equation (last row) is replaced with the critical constraint:

$$
f_{n_c+2} = \sum_{i=1}^{n_c} (\ln K_i)^2 = 0
$$

The corresponding Jacobian row becomes:

$$
J_{n_c+2, j} = \begin{cases} 2 \ln K_j & j \le n_c \\ 0 & j = n_c+1 \text{ or } n_c+2 \end{cases}
$$

This gives a well-conditioned system at the critical point where the standard specification equation becomes degenerate (the sensitivity of any single variable vanishes as all K-values converge).

Up to 10 Newton iterations are performed with convergence tolerance $\|\mathbf{f}\|_2 < 10^{-8}$.

## 7. Envelope Closure Check

After tracing completes, the envelope is considered **closed** if both branches contain at least 3 points:

$$
\text{closed} = (n_{\text{dew}} \ge 3) \quad \text{AND} \quad (n_{\text{bub}} \ge 3)
$$

This check is available via `isEnvelopeClosed()`. A closed envelope means the full dew-bubble-critical loop has been successfully traced, either in a single pass through the critical point or via two passes from each end.

## 8. Quality Lines

Quality lines are curves of **constant molar vapor fraction** $\beta$ inside the two-phase region. They connect the bubble curve ($\beta = 0$) to the dew curve ($\beta = 1$).

### 8.1 Tracing Algorithm

Each quality line is traced using the same continuation method as the phase boundary, but with fixed $\beta$:

1. **Initialization**: Wilson K-value correlation estimates the starting temperature at low pressure for the specified $\beta$. A flash calculation (bubble-point if $\beta < 0.5$, dew-point if $\beta \ge 0.5$) refines the K-values.

2. **Continuation**: The Newton-Raphson solver traces the curve in $P$-$T$ space using the same Jacobian, but the Rachford-Rice equation uses the specified $\beta$ instead of $\beta \to 0$ or $\beta \to 1$.

3. **Exit criteria**: Tracing stops when any of:
   - Pressure exceeds the maximum envelope pressure
   - Pressure drops below minimum after passing a peak (the curve turns around)
   - The solver fails to converge
   - 5000 points are traced

### 8.2 Volume and Mass Fractions

At each point on a quality line, the volume fraction $\beta_V$ and mass fraction $\beta_W$ are computed from the molar vapor fraction $\beta$:

$$
\beta_V = \frac{\beta \cdot V_m^{\text{vap}}}{\beta \cdot V_m^{\text{vap}} + (1-\beta) \cdot V_m^{\text{liq}}}
$$

$$
\beta_W = \frac{\beta \cdot M_w^{\text{vap}}}{\beta \cdot M_w^{\text{vap}} + (1-\beta) \cdot M_w^{\text{liq}}}
$$

where $V_m = M_w / \rho$ is the molar volume and $M_w$ is the phase molar mass.

These three fractions differ because the vapor and liquid phases have different densities and molar masses. For example, at $\beta = 0.5$ (50% of moles are vapor):
- $\beta_V > 0.5$ because vapor occupies more volume per mole
- $\beta_W \ne 0.5$ when vapor and liquid have different average molecular weights

### 8.3 Data Access

Quality line data is accessed via the `get()` method with string keys:

| Key Pattern | Returns | Example |
|-------------|---------|---------|
| `"qualityT_X"` | Temperatures (K) along quality line $\beta = X$ | `"qualityT_0.5"` |
| `"qualityP_X"` | Pressures (bara) along quality line $\beta = X$ | `"qualityP_0.5"` |
| `"qualityVolFrac_X"` | Volume fractions along the line | `"qualityVolFrac_0.5"` |
| `"qualityMassFrac_X"` | Mass fractions along the line | `"qualityMassFrac_0.5"` |

## 9. Starting Temperature from Wilson K-Values

The initial temperature estimate at a given pressure $P$ and vapor fraction $\beta$ is found by solving:

$$
g(\beta, T) = \sum_i \frac{z_i (K_i^W - 1)}{1 + \beta(K_i^W - 1)} = 0
$$

where the Wilson K-values are:

$$
K_i^W = \frac{P_{c,i}}{P} \exp\left[5.373(1 + \omega_i)\left(1 - \frac{T_{c,i}}{T}\right)\right]
$$

Newton's method is used: starting from $T_0 \approx T_{c,\text{ref}} \cdot 5.373(1+\omega_{\text{ref}}) / [5.373(1+\omega_{\text{ref}}) - \ln(P/P_{c,\text{ref}})]$ where the reference component is the lightest (for bubble) or heaviest (for dew).

## 10. Cricondenbar and Cricondentherm Tracking

These extrema are tracked incrementally during tracing:

- **Cricondenbar**: The point with the highest pressure encountered. Updated whenever $P_{\text{current}} > P_{\text{cricondenbar}}$.
- **Cricondentherm**: The point with the highest temperature encountered. Updated whenever $T_{\text{current}} > T_{\text{cricondentherm}}$.

Both include the phase compositions $\mathbf{x}$ and $\mathbf{y}$ at the extremum, accessible via `"cricondenbarX"`, `"cricondenbarY"`, `"cricondenthermX"`, `"cricondenthermY"`.

## 11. Implementation Classes

| Class | Responsibility |
|-------|---------------|
| `PTPhaseEnvelopeMichelsen` | Orchestration: two-pass tracing, point storage, critical/cricondenbar/cricondentherm tracking, quality line management, data access via `get()` |
| `SysNewtonRhapsonPhaseEnvelope` | Newton-Raphson solver: Jacobian assembly, sensitivity vector, specification variable selection, polynomial extrapolation, step-size control, critical point refinement |
| `ThermodynamicOperations` | Public API: `calcPTphaseEnvelope()` variants and `calcPTphaseEnvelopeWithQualityLines()` |

## References

1. Michelsen, M.L. & Mollerup, J.M., *Thermodynamic Models: Fundamentals & Computational Aspects*, 2nd ed., Tie-Line Publications, 2007.
2. Michelsen, M.L., "Calculation of phase envelopes and critical points for multicomponent mixtures", *Fluid Phase Equilibria*, 4(1-2), 1-10, 1980.
3. Wilson, G.M., "A modified Redlich-Kwong equation of state, application to general physical data calculations", Paper No. 15C, AIChE 65th National Meeting, Cleveland, Ohio, 1968.

## See Also

- [Phase Envelope and Critical Points Guide](../pvtsimulation/phase_envelope_guide) - Usage guide with examples
- [TP Flash Algorithm](TPflash_algorithm) - The TP flash used for initialization
- [Thermodynamic Operations Package](README) - Overview of all operations
