# TPflash Algorithm Documentation

## Overview

The Temperature-Pressure (TP) flash calculation is a fundamental operation in chemical engineering thermodynamics. Given a mixture composition, temperature, and pressure, the TP flash determines:
- The number and type of equilibrium phases
- The phase fractions (vapor/liquid split)
- The composition of each phase

NeqSim implements the classical Michelsen flash algorithm with stability analysis, as described in the landmark work *Thermodynamic Models: Fundamentals and Computational Aspects* (Michelsen & Mollerup, 2007). The implementation supports:
- Two-phase vapor-liquid equilibrium (VLE)
- Multi-phase equilibrium (VLLE, LLE)
- Systems with electrolytes and chemical reactions

---

## Table of Contents

1. [Two-Phase Flash Algorithm](#1-two-phase-flash-algorithm)
   - [1.0 Complete TPflash Algorithm Flow](#10-complete-tpflash-algorithm-flow)
   - [1.1 Problem Formulation](#11-problem-formulation)
   - [1.2 Rachford-Rice Equation](#12-rachford-rice-equation)
   - [1.3 Successive Substitution](#13-successive-substitution)
   - [1.4 Accelerated Successive Substitution](#14-accelerated-successive-substitution)
   - [1.5 Second-Order Newton-Raphson Method](#15-second-order-newton-raphson-method)
2. [Stability Analysis](#2-stability-analysis)
   - [2.1 Theoretical Foundation](#21-theoretical-foundation)
   - [2.2 Tangent Plane Distance Function](#22-tangent-plane-distance-function)
   - [2.3 Stationary Points and Stability Criterion](#23-stationary-points-and-stability-criterion)
   - [2.4 Solution Algorithm](#24-solution-algorithm)
3. [Multi-Phase Flash Algorithm](#3-multi-phase-flash-algorithm)
   - [3.0 Complete TPmultiflash Algorithm Flow](#30-complete-tpmultiflash-algorithm-flow)
   - [3.0.1 Stability Analysis Detailed Flow](#301-stability-analysis-detailed-flow)
   - [3.0.2 Multiphase Stability Analysis Additional Details](#302-multiphase-stability-analysis-additional-details)
   - [3.0.3 Enhanced Stability Analysis](#303-enhanced-stability-analysis)
   - [3.1 Multiphase Equilibrium Formulation](#31-multiphase-equilibrium-formulation)
   - [3.2 Q-Function Minimization](#32-q-function-minimization)
   - [3.3 Phase Addition and Removal](#33-phase-addition-and-removal)
   - [3.4 Complete Multiphase Flash Workflow](#34-complete-multiphase-flash-workflow)
   - [3.5 Phase Seeding Strategies](#35-phase-seeding-strategies)
4. [Electrolytes and Chemical Reactions](#4-electrolytes-and-chemical-reactions)
   - [4.1 Chemical Equilibrium Coupling](#41-chemical-equilibrium-coupling)
   - [4.2 Ion Handling in Stability Analysis](#42-ion-handling-in-stability-analysis)
   - [4.3 Aqueous Phase Management](#43-aqueous-phase-management)
5. [References](#5-references)

---

## 1. Two-Phase Flash Algorithm

### 1.0 Complete TPflash Algorithm Flow

The following flowchart shows the complete two-phase flash algorithm as implemented in `TPflash.run()`:

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                         TPflash.run() ALGORITHM FLOW                          ║
╚═══════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: INITIALIZATION                                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│  • system.init(0) - Initialize molar composition                                │
│  • system.init(1) - Calculate thermodynamic properties                          │
│  • Determine minimum Gibbs energy phase (gas or liquid)                         │
│  • Store reference: minGibsPhaseLogZ[i], minGibsLogFugCoef[i]                   │
│  • Handle single-component or single-phase systems → return early               │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: INITIAL K-VALUES (Wilson Equation)                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│  K-values are pre-initialized using Wilson's correlation:                       │
│                                                                                 │
│    Ki = (Pc,i / P) × exp[5.373(1 + ωi)(1 - Tc,i/T)]                            │
│                                                                                 │
│  • Solve Rachford-Rice equation to get initial β                                │
│  • Calculate initial x, y from material balance                                 │
│  • system.init(1) - Update fugacity coefficients                                │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: INITIAL SSI (3 iterations)                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF β is at bounds (all liquid or all vapor):                                   │
│     • Reset β = 0.5                                                             │
│     • Run 1 sucsSubs() iteration                                                │
│                                                                                 │
│  FOR k = 0 to 2:  (exactly 3 preliminary SSI iterations)                        │
│     • IF β is in valid range (not at bounds):                                   │
│         - Run sucsSubs() iteration                                              │
│         - IF Gibbs energy decreased significantly → break early                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: QUICK STABILITY CHECK (TPD-based)                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Calculate tangent plane distances for both phases:                             │
│                                                                                 │
│    tpdy = Σ yi × [ln(φi^V) + ln(yi) - ln(zi) - ln(φi^ref)]                     │
│    tpdx = Σ xi × [ln(φi^L) + ln(xi) - ln(zi) - ln(φi^ref)]                     │
│    dgonRT = β × tpdy + (1-β) × tpdx                                            │
│                                                                                 │
│  IF dgonRT > 0 AND tpdx > 0 AND tpdy > 0:                                      │
│     → Single phase is stable                                                    │
│     → Run full stability analysis if checkStability() enabled                   │
│     → If multiPhaseCheck: delegate to TPmultiflash                              │
│     → return                                                                    │
│                                                                                 │
│  ELSE IF tpdx < 0 or tpdy < 0:                                                 │
│     → Re-estimate K-values from fugacity ratios                                 │
│     → Continue to main iteration loop                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 5: PHASE TYPE DETERMINATION                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Compare Gibbs energy of phase 0 as GAS vs LIQUID:                              │
│     • Calculate G(gas), G(liquid)                                               │
│     • Set phase type to lower Gibbs energy option                               │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 6: MAIN ITERATION LOOP                                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Parameters:                                                                    │
│     • accelerateInterval = 7 (use DEM every 7 iterations)                       │
│     • newtonLimit = 20 (switch to Newton after 20 SSI iterations)               │
│     • maxNumberOfIterations = 50 (default)                                      │
│     • convergence tolerance = 1e-10                                             │
│                                                                                 │
│  DO (outer loop for chemical systems):                                          │
│  │   iterations = 0                                                             │
│  │   DO (inner loop):                                                           │
│  │   │   iterations++                                                           │
│  │   │                                                                          │
│  │   │   IF iterations < 20 (or chemical system, or no fugacity derivatives):   │
│  │   │   │   IF timeFromLastGibbsFail > 6 AND iterations % 7 == 0:              │
│  │   │   │       → accselerateSucsSubs()  [DEM acceleration]                    │
│  │   │   │   ELSE:                                                              │
│  │   │   │       → sucsSubs()  [standard SSI]                                   │
│  │   │   │                                                                      │
│  │   │   ELSE IF iterations >= 20:                                              │
│  │   │   │   IF iterations == 20:                                               │
│  │   │   │       → Create SysNewtonRhapsonTPflash solver                        │
│  │   │   │   → secondOrderSolver.solve()  [Newton-Raphson]                      │
│  │   │   │                                                                      │
│  │   │   Check Gibbs energy:                                                    │
│  │   │   IF G increased OR β at bounds:                                         │
│  │   │       → resetK() [restore previous K-values]                             │
│  │   │       → timeFromLastGibbsFail = 0                                        │
│  │   │   ELSE:                                                                  │
│  │   │       → setNewK() [store current K-values]                               │
│  │   │       → timeFromLastGibbsFail++                                          │
│  │   │                                                                          │
│  │   WHILE (deviation > 1e-10 AND iterations < 50)                              │
│  │                                                                              │
│  │   IF chemical system:                                                        │
│  │       → Solve chemical equilibrium in liquid phase                           │
│  │       → Calculate chemical equilibrium deviation                             │
│  │                                                                              │
│  WHILE (chemdev > 1e-6 AND totiter < 300) OR (chemical system AND totiter < 2)  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 7: POST-PROCESSING                                                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF multiPhaseCheck enabled:                                                    │
│     → Delegate to TPmultiflash for stability analysis and phase split           │
│  ELSE:                                                                          │
│     → Final phase type check (gas vs liquid Gibbs energy)                       │
│                                                                                 │
│  IF solidCheck enabled:                                                         │
│     → Run solid phase flash                                                     │
│                                                                                 │
│  Remove phases with β < βmin                                                    │
│  Order phases by density                                                        │
│  Final system.init(1)                                                           │
│                                                                                 │
│  IF chemical system:                                                            │
│     → Final chemical equilibrium solve in aqueous/liquid phases                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Key Algorithm Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `phaseFractionMinimumLimit` | ~1e-12 | Minimum allowed phase fraction |
| Initial SSI iterations | 3 | Preliminary iterations before stability check |
| `accelerateInterval` | 7 | Apply DEM every 7th iteration |
| `newtonLimit` | 20 | Switch to Newton-Raphson after 20 SSI iterations |
| `maxNumberOfIterations` | 50 | Maximum iterations per convergence loop |
| Convergence tolerance | 1e-10 | Deviation threshold for K-value convergence |
| Gibbs increase tolerance | 1e-8 | Relative increase that triggers K-reset |

### 1.1 Problem Formulation

Consider a mixture of $N_c$ components with overall mole fractions $z_i$ at temperature $T$ and pressure $P$. The two-phase flash problem seeks the vapor fraction $\beta$ (also called $V$ for vapor) and the mole fractions in each phase ($x_i$ for liquid, $y_i$ for vapor) such that thermodynamic equilibrium is satisfied.

**Equilibrium Conditions:**

At equilibrium, the fugacity of each component must be equal in all phases:

$$f_i^V = f_i^L \quad \text{for } i = 1, 2, \ldots, N_c$$

This can be rewritten using fugacity coefficients $\phi_i$:

$$y_i \phi_i^V P = x_i \phi_i^L P$$

Defining the equilibrium ratio (K-factor):

$$K_i = \frac{y_i}{x_i} = \frac{\phi_i^L}{\phi_i^V}$$

**Material Balance:**

The overall material balance constrains the phase compositions:

$$z_i = \beta y_i + (1 - \beta) x_i$$

Combining with the K-factor definition:

$$y_i = \frac{K_i z_i}{1 + \beta(K_i - 1)}$$

$$x_i = \frac{z_i}{1 + \beta(K_i - 1)}$$

### 1.2 Rachford-Rice Equation

The vapor fraction $\beta$ is found by solving the **Rachford-Rice equation**, derived from the constraint $\sum_i y_i = \sum_i x_i = 1$:

$$g(\beta) = \sum_{i=1}^{N_c} \frac{z_i (K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

**Properties of $g(\beta)$:**
- $g(\beta)$ is monotonically decreasing in $\beta$
- For a valid two-phase solution: $\beta \in (\beta_{\min}, \beta_{\max})$

Where the bounds ensure positive mole fractions:

$$\beta_{\min} = \max_i \left( \frac{K_i z_i - 1}{K_i - 1} \right) \quad \text{for } K_i > 1$$

$$\beta_{\max} = \min_i \left( \frac{1 - z_i}{1 - K_i} \right) \quad \text{for } K_i < 1$$

**Derivative for Newton's Method:**

$$\frac{dg}{d\beta} = -\sum_{i=1}^{N_c} \frac{z_i (K_i - 1)^2}{[1 + \beta(K_i - 1)]^2}$$

#### NeqSim Implementation

NeqSim implements two Rachford-Rice solvers:

1. **Michelsen (2001)**: Newton-Raphson with bisection fallback
2. **Nielsen (2023)**: Robust reformulation avoiding round-off errors

The method can be selected via:
```java
RachfordRice.setMethod("Nielsen2023");  // or "Michelsen2001"
```

See [RachfordRice.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/RachfordRice.java) for implementation details.

### 1.3 Successive Substitution

The standard approach to solve the two-phase flash is **Successive Substitution Iteration (SSI)**, which iteratively updates K-factors until convergence.

**Algorithm:**

1. **Initialize K-factors** using Wilson's correlation:
   $$K_i^{(0)} = \frac{P_{c,i}}{P} \exp\left[ 5.373(1 + \omega_i)\left(1 - \frac{T_{c,i}}{T}\right) \right]$$

2. **Solve Rachford-Rice** to obtain $\beta$

3. **Calculate phase compositions** using material balance:
   $$x_i = \frac{z_i}{1 + \beta(K_i - 1)}, \quad y_i = K_i x_i$$

4. **Update K-factors** from fugacity coefficients:
   $$K_i^{(n+1)} = \frac{\phi_i^L(x, T, P)}{\phi_i^V(y, T, P)}$$

5. **Check convergence:**
   $$\sum_i \left| \ln K_i^{(n+1)} - \ln K_i^{(n)} \right| < \epsilon$$

6. If not converged, return to step 2.

**NeqSim Implementation:**

```java
// From TPflash.java - sucsSubs() method
public void sucsSubs() {
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        Kold = system.getPhase(0).getComponent(i).getK();
        system.getPhase(0).getComponent(i).setK(
            system.getPhase(1).getComponent(i).getFugacityCoefficient()
            / system.getPhase(0).getComponent(i).getFugacityCoefficient() * presdiff);
        deviation += Math.abs(Math.log(system.getPhase(0).getComponent(i).getK()) 
                            - Math.log(Kold));
    }
    
    RachfordRice rachfordRice = new RachfordRice();
    system.setBeta(rachfordRice.calcBeta(system.getKvector(), system.getzvector()));
    system.calc_x_y();
    system.init(1);
}
```

### 1.4 Accelerated Successive Substitution

Near the critical point or for systems with similar K-factors, standard SSI converges slowly. NeqSim implements the **Dominant Eigenvalue Method (DEM)** for acceleration.

**Theory (Michelsen, 1982):**

The convergence of SSI is limited by the dominant eigenvalue of the iteration matrix. The acceleration factor $\lambda$ is estimated from the last three iterates:

$$\lambda = \frac{\sum_i (\Delta \ln K_i^{(n)}) \cdot (\Delta \ln K_i^{(n-1)})}{\sum_i (\Delta \ln K_i^{(n-1)})^2}$$

Where $\Delta \ln K_i^{(n)} = \ln K_i^{(n)} - \ln K_i^{(n-1)}$.

**Accelerated Update:**

$$\ln K_i^{(n+1)} = \ln K_i^{(n)} + \frac{\lambda}{1 - \lambda} \Delta \ln K_i^{(n)}$$

**NeqSim Implementation:**

```java
// From TPflash.java - accselerateSucsSubs() method
public void accselerateSucsSubs() {
    double prod1 = 0.0, prod2 = 0.0;
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        prod1 += oldDeltalnK[i] * oldoldDeltalnK[i];
        prod2 += oldoldDeltalnK[i] * oldoldDeltalnK[i];
    }
    double lambda = prod1 / prod2;
    
    for (i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
        lnK[i] += lambda / (1.0 - lambda) * deltalnK[i];
        system.getPhase(0).getComponent(i).setK(Math.exp(lnK[i]));
    }
    // ... Rachford-Rice and update
}
```

### 1.5 Second-Order Newton-Raphson Method

For difficult systems or near-critical conditions, NeqSim employs a **second-order Newton-Raphson method** using fugacity derivatives.

**Formulation:**

Define the objective function vector $\mathbf{f}$ with components:

$$f_i = \ln \left( \frac{y_i \phi_i^V}{x_i \phi_i^L} \right) = 0$$

The solution is found by iterating:

$$\mathbf{u}^{(n+1)} = \mathbf{u}^{(n)} - \mathbf{J}^{-1} \mathbf{f}(\mathbf{u}^{(n)})$$

Where $\mathbf{u} = (\beta y_1, \beta y_2, \ldots, \beta y_{N_c})^T$ and the Jacobian $\mathbf{J}$ includes composition derivatives of fugacity coefficients:

$$J_{ij} = \frac{\partial f_i}{\partial u_j} = \frac{1}{\beta}\left(\frac{\delta_{ij}}{x_i} - 1 + \frac{\partial \ln \phi_i^V}{\partial x_j}\right) + \frac{1}{1-\beta}\left(\frac{\delta_{ij}}{y_i} - 1 + \frac{\partial \ln \phi_i^L}{\partial y_j}\right)$$

**NeqSim Implementation:**

See [SysNewtonRhapsonTPflash.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/SysNewtonRhapsonTPflash.java) for the full implementation.

---

## 2. Stability Analysis

### 2.1 Theoretical Foundation

A phase is **thermodynamically stable** if it has the lowest Gibbs energy among all possible phase configurations. The stability analysis determines whether a given phase will spontaneously split into multiple phases.

**Gibbs Energy Criterion:**

For a single-phase mixture with mole numbers $\mathbf{n}$, the mixture is stable if and only if the Gibbs energy $G(\mathbf{n})$ is at its global minimum. This is equivalent to requiring that no other phase can exist with lower chemical potential.

### 2.2 Tangent Plane Distance Function

Michelsen (1982) introduced the **Tangent Plane Distance (TPD)** function for stability analysis. Consider a reference phase with composition $\mathbf{z}$ and a trial phase with composition $\mathbf{w}$.

**TPD Definition:**

$$\text{TPD}(\mathbf{w}) = \sum_{i=1}^{N_c} w_i \left[ \mu_i(\mathbf{w}) - \mu_i(\mathbf{z}) \right]$$

In terms of fugacity coefficients:

$$\text{TPD}(\mathbf{w}) = \sum_{i=1}^{N_c} w_i \left[ \ln w_i + \ln \phi_i(\mathbf{w}) - d_i \right]$$

Where:
$$d_i = \ln z_i + \ln \phi_i(\mathbf{z})$$

### 2.3 Stationary Points and Stability Criterion

**Stationary Point Condition:**

At a stationary point of TPD, the gradient is zero:

$$\frac{\partial \text{TPD}}{\partial w_i} = \ln w_i + \ln \phi_i(\mathbf{w}) - d_i + 1 = 0$$

Using the substitution $W_i = \exp(\ln w_i)$, define:

$$\ln W_i = d_i - \ln \phi_i(\mathbf{w})$$

**Stability Test:**

The reduced TPD at a stationary point is:

$$\text{tm} = 1 - \sum_{i=1}^{N_c} W_i$$

**Criterion:**
- If $\text{tm} \geq 0$ for all stationary points → **Stable** (single phase)
- If $\text{tm} < 0$ for any stationary point → **Unstable** (phase split occurs)

### 2.4 Solution Algorithm

NeqSim implements a hybrid algorithm combining successive substitution with Newton's method:

**Phase 1: Successive Substitution**

1. **Initialize trial phase** with pure component or Wilson K-factor estimate:
   $$W_i^{(0)} = z_i \cdot K_i \quad \text{(vapor-like)} \quad \text{or} \quad W_i^{(0)} = z_i / K_i \quad \text{(liquid-like)}$$

2. **Iterate:**
   $$\ln W_i^{(n+1)} = d_i - \ln \phi_i(\mathbf{w}^{(n)})$$
   
   Where $w_i = W_i / \sum_j W_j$ (normalized composition)

3. **Accelerate** using DEM (every 7 iterations):
   $$\lambda = \frac{\sum_i \Delta(\ln W_i)^{(n)} \cdot \Delta(\ln W_i)^{(n-1)}}{\sum_i [\Delta(\ln W_i)^{(n-1)}]^2}$$
   $$\ln W_i^{(n+1)} = \ln W_i^{(n)} + \frac{\lambda}{1-\lambda} \Delta(\ln W_i)^{(n)}$$

4. **Continue** until $\sum_i |\ln W_i^{(n+1)} - \ln W_i^{(n)}| < \epsilon$

**Phase 2: Second-Order Newton (if needed)**

For difficult cases (iteration > 150), switch to Newton's method using the variable $\alpha_i = 2\sqrt{W_i}$:

**Objective function:**
$$F_i = \sqrt{W_i} \left[ \ln W_i + \ln \phi_i(\mathbf{w}) - d_i \right]$$

**Jacobian:**
$$\frac{\partial F_i}{\partial \alpha_j} = \delta_{ij} + \sqrt{W_i W_j} \frac{\partial \ln \phi_i}{\partial n_j}$$

**Newton step:**
$$\boldsymbol{\alpha}^{(n+1)} = \boldsymbol{\alpha}^{(n)} - (\mathbf{I} + \mathbf{H})^{-1} \mathbf{F}$$

**NeqSim Implementation:**

```java
// From TPmultiflash.java - stabilityAnalysis() method
// Successive substitution phase
for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
    logWi[i] = d[i] - clonedSystem.getPhase(1).getComponent(i).getLogFugacityCoefficient();
    Wi[j][i] = safeExp(logWi[i]);
}

// Check convergence and compute tm
tm[j] = 1.0;
for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
    tm[j] -= safeExp(logWi[i]);
}

// Phase is unstable if tm < -1e-8
if (tm[j] < -1e-8) {
    system.addPhase();  // Add new phase
    // Set composition from stationary point
}
```

**Trivial Solution Check:**

To avoid converging to trivial solutions (identical to existing phases):

$$\sum_i |w_i - x_i^{\text{existing}}| < \epsilon_{\text{trivial}}$$

If the trial composition is too close to an existing phase, it is rejected.

---

## 3. Multi-Phase Flash Algorithm

When `system.setMultiPhaseCheck(true)` is called, NeqSim uses the `TPmultiflash` class which extends the basic two-phase flash with comprehensive stability analysis and support for three or more equilibrium phases.

### 3.0 Complete TPmultiflash Algorithm Flow

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                       TPmultiflash.run() ALGORITHM FLOW                        ║
╚═══════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: ELECTROLYTE PREPROCESSING                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF system is chemical/electrolyte:                                             │
│     • Store ionic component compositions: ionicZ[i] = z[i] for ions             │
│     • Temporarily set ion z = 1e-100 (remove from stability analysis)           │
│     • hasIons = true                                                            │
│     • system.init(1) - Recalculate properties without ions                      │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: PRIMARY STABILITY ANALYSIS                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF doStabilityAnalysis == true:                                                │
│     → stabilityAnalysis()  [see detailed flow below]                            │
│     → Sets multiPhaseTest = true if unstable phase found                        │
│     → Adds new phase with composition from stationary point                     │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: HEURISTIC PHASE SEEDING                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF NOT multiPhaseTest AND seedAdditionalPhaseFromFeed():                       │
│     → Add gas phase seeded from feed composition                                │
│     → multiPhaseTest = true                                                     │
│                                                                                 │
│  IF seedHydrocarbonLiquidFromFeed():                                           │
│     → Add hydrocarbon liquid phase if conditions met                            │
│     → multiPhaseTest = true                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: ION RESTORATION (Electrolyte Systems)                                   │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF hasIons:                                                                    │
│     FOR each ionic component:                                                   │
│        • Restore z[i] = ionicZ[i] in all phases                                │
│        • IF phase is AQUEOUS: set x[i] = ionicZ[i]                             │
│        • ELSE: set x[i] = 1e-50 (ions only in aqueous)                         │
│     • Normalize all phases                                                      │
│     • system.init(1)                                                            │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 5: INITIAL CHEMICAL EQUILIBRIUM                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF chemical system AND has aqueous phase:                                      │
│     → solveChemEq(aqueousPhaseNumber, 0)  [stoichiometric]                      │
│     → solveChemEq(aqueousPhaseNumber, 1)  [full Newton]                         │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 6: MULTIPHASE SPLIT CALCULATION                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF multiPhaseTest == true:                                                     │
│     maxerr = 1e-12                                                              │
│                                                                                 │
│     DO (outer loop - chemical equilibrium):                                     │
│     │   iterOut++                                                               │
│     │                                                                           │
│     │   IF chemical system with aqueous phase:                                  │
│     │      → Solve chemical equilibrium                                         │
│     │      → Calculate chemical deviation                                       │
│     │                                                                           │
│     │   setDoubleArrays()  [allocate Q-function arrays]                        │
│     │   iterations = 0                                                          │
│     │                                                                           │
│     │   DO (inner loop - Q-function minimization):                              │
│     │   │   iterations++                                                        │
│     │   │   oldDiff = diff                                                      │
│     │   │   diff = solveBeta()  [Newton step on Q-function]                    │
│     │   │                                                                       │
│     │   │   IF iterations % 50 == 0:                                           │
│     │   │       maxerr *= 100  [relax tolerance]                               │
│     │   │                                                                       │
│     │   WHILE (diff > maxerr AND NOT removePhase                               │
│     │          AND (diff < oldDiff OR iterations < 50)                         │
│     │          AND iterations < 200)                                            │
│     │                                                                           │
│     WHILE (|chemdev| > 1e-10 AND iterOut < 100)                                │
│            OR (iterOut < 3 AND chemical AND aqueous)                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 7: AQUEOUS PHASE SEEDING (if water present but no aqueous)                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF has water component AND NOT aqueousPhaseSeedAttempted                       │
│     AND multiPhaseCheck AND NOT hasAqueousPhase:                                │
│                                                                                 │
│     IF waterZ > 1e-6 AND numberOfPhases < 3:                                   │
│        → Add new phase                                                          │
│        → Set phase type = AQUEOUS                                               │
│        → Initialize with water-rich composition                                 │
│        → Set β = max(1e-5, 10 × βmin)                                          │
│        → multiPhaseTest = true                                                  │
│        → aqueousPhaseSeedAttempted = true                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 8: SINGLE AQUEOUS PHASE ENFORCEMENT (Electrolytes)                         │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF chemical system:                                                            │
│     → ensureSingleAqueousPhase()                                               │
│     → Reclassify extra "aqueous" phases as OIL                                  │
│     → Move ions to the true aqueous phase                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 9: PHASE CLEANUP                                                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Remove negligible phases:                                                      │
│     FOR each phase:                                                             │
│        IF β < 1.1 × βmin:                                                      │
│           → removePhaseKeepTotalComposition()                                   │
│           → hasRemovedPhase = true                                              │
│                                                                                 │
│  Detect trivial solutions (phases with same density):                           │
│     FOR each pair of phases (i, j):                                             │
│        IF |ρi - ρj| < 1.1e-5:                                                  │
│           → Remove phase j                                                      │
│           → hasRemovedPhase = true                                              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 10: RECURSIVE STABILITY CHECK                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  IF hasRemovedPhase AND NOT secondTime:                                         │
│     → secondTime = true                                                         │
│     → stabilityAnalysis3()  [re-check stability]                               │
│     → run()  [RECURSIVE CALL - restart algorithm]                              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.0.1 Stability Analysis Detailed Flow

The `stabilityAnalysis()` method tests multiple trial phases to find instabilities:

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                    stabilityAnalysis() DETAILED FLOW                           ║
╚═══════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────────┐
│ INITIALIZATION                                                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│  • Clone system for trial phase calculations                                    │
│  • Calculate reference chemical potentials:                                     │
│       d[k] = ln(x[k]) + ln(φ[k])   for each component k                        │
│  • Initialize logWi[j] = 1.0 for components with z > 1e-100                    │
│  • Find heaviest and lightest hydrocarbon components                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ COMPONENT SELECTION                                                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Components to test (loop j from Nc-1 down to 0):                              │
│     SKIP if:                                                                    │
│        • x[j] < 1e-100  (negligible)                                           │
│        • Component is ionic                                                     │
│        • Hydrocarbon but NOT heaviest AND NOT lightest                         │
│                                                                                 │
│  This typically tests: water, CO2, H2S, heaviest HC, lightest HC, etc.         │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
   ╔═════════════════════════════════════════════════════════════════════════════╗
   ║  FOR EACH SELECTED COMPONENT j:                                             ║
   ╚═════════════════════════════════════════════════════════════════════════════╝
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ TRIAL PHASE INITIALIZATION                                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Initialize trial phase composition (nearly pure component j):                  │
│     w[i] = 1.0      if i == j                                                  │
│     w[i] = 1e-12    if i ≠ j  (trace amounts)                                  │
│     w[i] = 0        if z[i] < 1e-100                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ SSI LOOP (up to 150 iterations)                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Parameters:                                                                    │
│     • maxsucssubiter = 150  (max SSI iterations)                               │
│     • maxiter = 200  (absolute max with Newton)                                 │
│     • convergence = 1e-9                                                        │
│                                                                                 │
│  iter = 0                                                                       │
│  DO:                                                                            │
│  │   iter++                                                                     │
│  │   errOld = err                                                               │
│  │   err = 0                                                                    │
│  │                                                                              │
│  │   IF iter <= 150 (SSI phase):                                               │
│  │   │                                                                          │
│  │   │   IF iter % 7 == 0 AND useaccsubst (DEM acceleration):                  │
│  │   │   │   Calculate acceleration factor λ:                                  │
│  │   │   │      λ = Σ(ΔlnW^n × ΔlnW^n-1 × (ΔlnW^n-1)²)                        │
│  │   │   │          / Σ(ΔlnW^n-1)⁴                                            │
│  │   │   │   Apply acceleration:                                               │
│  │   │   │      lnW[i] += λ/(1-λ) × ΔlnW[i]                                   │
│  │   │   │                                                                      │
│  │   │   ELSE (standard SSI):                                                  │
│  │   │   │   Store old values for acceleration                                 │
│  │   │   │   Calculate fugacity coefficients: clonedSystem.init(1,1)           │
│  │   │   │   Update:                                                           │
│  │   │   │      lnW[i] = d[i] - ln(φ[i])                                       │
│  │   │   │      W[j][i] = exp(lnW[i])                                          │
│  │   │   │   err += |lnW[i] - lnW_old[i]|                                      │
│  │   │   │                                                                      │
│  │   │   IF err > errOld after 2 iters:                                        │
│  │   │      useaccsubst = false  (disable acceleration)                        │
│  │   │                                                                          │
│  │   ELSE (iter > 150 - Newton phase):                                         │
│  │   │   clonedSystem.init(3,1)  [compute fugacity derivatives]                │
│  │   │   α[i] = 2√(W[j][i])                                                    │
│  │   │                                                                          │
│  │   │   Build objective function F and Jacobian J:                            │
│  │   │      F[i] = √W[i] × (lnW[i] + ln(φ[i]) - d[i])                         │
│  │   │      J[i,k] = δ[i,k] + √(W[i]×W[k]) × ∂ln(φ[i])/∂n[k]                  │
│  │   │                                                                          │
│  │   │   Solve Newton step:                                                    │
│  │   │      Δα = -(I + J)⁻¹ × F                                               │
│  │   │      (with regularization fallback if singular)                         │
│  │   │                                                                          │
│  │   │   Update:                                                               │
│  │   │      α_new = α + Δα                                                     │
│  │   │      W[j][i] = (α_new/2)²                                              │
│  │   │      lnW[i] = ln(W[j][i])                                               │
│  │   │                                                                          │
│  │   Normalize and update trial phase composition:                             │
│  │      sumw = Σ exp(lnW[i])                                                   │
│  │      x[i] = exp(lnW[i]) / sumw                                              │
│  │                                                                              │
│  WHILE (|err| > 1e-9 OR err > errOld) AND iter < 200                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ CONVERGENCE CHECK AND tm CALCULATION                                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Calculate tangent plane distance:                                              │
│     tm[j] = 1 - Σ exp(lnW[i])                                                  │
│                                                                                 │
│  Check for trivial solution:                                                    │
│     trivialCheck0 = Σ |w[i] - x_phase0[i]|                                     │
│     trivialCheck1 = Σ |w[i] - x_phase1[i]|                                     │
│     IF trivialCheck0 < 1e-4 OR trivialCheck1 < 1e-4:                           │
│        tm[j] = 10.0  (mark as stable - trivial solution)                       │
│                                                                                 │
│  IF tm[j] < -1e-8:                                                             │
│     → UNSTABLE! Break loop, proceed to phase addition                          │
└─────────────────────────────────────────────────────────────────────────────────┘
   ║                                                                              ║
   ║  END FOR EACH COMPONENT                                                      ║
   ╚══════════════════════════════════════════════════════════════════════════════╝
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ PHASE ADDITION (if instability found)                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│  FOR k = Nc-1 down to 0:                                                       │
│     IF tm[k] < -1e-8 AND NOT NaN:                                              │
│        • system.addPhase()                                                      │
│        • Set new phase composition = x[k][i] (from stationary point)           │
│        • Normalize new phase                                                    │
│        • multiPhaseTest = true                                                  │
│        • Set initial β = z[destabilizing_component]                            │
│        • system.init(1)                                                         │
│        • system.normalizeBeta()                                                 │
│        → RETURN (exit stability analysis)                                       │
│                                                                                 │
│  IF no instability found:                                                       │
│     → system.normalizeBeta()                                                    │
│     → RETURN (system is stable)                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Stability Analysis Key Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `maxsucssubiter` | 150 | Maximum SSI iterations before Newton |
| `maxiter` | 200 | Absolute maximum iterations |
| DEM interval | 7 | Apply acceleration every 7th iteration |
| Convergence tolerance | 1e-9 | Error threshold for lnW convergence |
| Instability threshold | -1e-8 | tm value indicating phase split |
| Trivial solution threshold | 1e-4 | Composition difference to detect trivial |

### 3.0.2 Multiphase Stability Analysis Additional Details

The multiphase stability analysis in NeqSim is more sophisticated than the basic two-phase version. It systematically tests multiple trial phase compositions to ensure no additional phases can form.

#### 3.0.2.1 Trial Phase Selection Strategy

Instead of using only Wilson K-factor estimates, the multiphase stability analysis tests **component-seeded trial phases**:

1. **Pure component initialization**: For each component $j$, create a trial phase with:
   $$w_i^{(0)} = \begin{cases} 1.0 & \text{if } i = j \\ 10^{-12} & \text{if } i \neq j \end{cases}$$

2. **Hydrocarbon optimization**: To reduce computational cost, only two hydrocarbon components are tested:
   - The **heaviest hydrocarbon** (highest molar mass)
   - The **lightest hydrocarbon** (lowest molar mass)
   
   This captures both potential liquid-liquid separation (heavy components) and vapor formation (light components).

3. **Non-hydrocarbon components**: All non-hydrocarbon components (water, CO₂, H₂S, etc.) are tested individually.

4. **Ion exclusion**: Components with ionic charge are excluded from stability testing since they cannot exist in separate non-aqueous phases.

```java
// From TPmultiflash.java - component selection logic
for (int j = system.getPhase(0).getNumberOfComponents() - 1; j >= 0; j--) {
    // Skip negligible components
    if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getx() < 1e-100)
        continue;
    // Skip ions
    if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).getIonicCharge() != 0)
        continue;
    // For hydrocarbons, only test heaviest and lightest
    if (minimumGibbsEnergySystem.getPhase(0).getComponent(j).isHydrocarbon()
        && j != hydrocarbonTestCompNumb && j != lightTestCompNumb)
        continue;
    
    // Perform stability test for this component...
}
```

#### 3.0.2.2 Reference Phase Chemical Potential

The reference chemical potential $d_i$ is computed from the current phase (typically the phase with lowest Gibbs energy):

$$d_i = \ln x_i^{\text{ref}} + \ln \phi_i^{\text{ref}}$$

Where superscript "ref" denotes the reference phase. This is computed once before the iteration loop:

```java
for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
    if (system.getPhase(0).getComponent(k).getx() > 1e-100) {
        d[k] = Math.log(system.getPhase(0).getComponent(k).getx())
             + system.getPhase(0).getComponent(k).getLogFugacityCoefficient();
    }
}
```

#### 3.0.2.3 Successive Substitution with Normalization

The multiphase stability analysis maintains both unnormalized ($W_i$) and normalized ($w_i$) compositions:

**Iteration update:**
$$\ln W_i^{(n+1)} = d_i - \ln \phi_i(\mathbf{w}^{(n)})$$

**Normalization for fugacity calculation:**
$$w_i = \frac{W_i}{\sum_j W_j}$$

This is important because fugacity coefficients must be evaluated at normalized compositions, but the TPD criterion uses the unnormalized $W_i$ values.

```java
// Compute sum for normalization
sumw[j] = 0;
for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
    sumw[j] += safeExp(logWi[i]);
}

// Set normalized composition for fugacity calculation
for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
    clonedSystem.get(0).getPhase(1).getComponent(i).setx(safeExp(logWi[i]) / sumw[j]);
}
```

#### 3.0.2.4 Convergence Acceleration (DEM)

Every 7 iterations, the Dominant Eigenvalue Method accelerates convergence:

$$\lambda = \frac{\sum_i (\Delta \ln W_i^{(n)}) \cdot (\Delta \ln W_i^{(n-1)}) \cdot (\Delta \ln W_i^{(n-1)})^2}{\sum_i (\Delta \ln W_i^{(n-1)})^4}$$

$$\ln W_i^{\text{acc}} = \ln W_i^{(n)} + \frac{\lambda}{1 - \lambda} \Delta \ln W_i^{(n)}$$

Acceleration is disabled if the error increases (indicating divergence):
```java
if (iter > 2 && err > errOld) {
    useaccsubst = false;
}
```

#### 3.0.2.5 Second-Order Newton Method (Optional)

After 150 successive substitution iterations, NeqSim switches to a second-order Newton method using the substitution $\alpha_i = 2\sqrt{W_i}$:

**Objective function:**
$$F_i = \sqrt{W_i} \left[ \ln W_i + \ln \phi_i(\mathbf{w}) - d_i \right]$$

**Jacobian with fugacity derivatives:**
$$\frac{\partial F_i}{\partial \alpha_k} = \delta_{ik} + \sqrt{W_i W_k} \cdot \frac{\partial \ln \phi_i}{\partial n_k}$$

**Newton update with regularization:**
$$\boldsymbol{\alpha}^{(n+1)} = \boldsymbol{\alpha}^{(n)} - (\mathbf{I} + \mathbf{J})^{-1} \mathbf{F}$$

The implementation includes robust fallbacks for singular matrices:
1. Try with small regularization ($10^{-6} \cdot \mathbf{I}$)
2. Try with larger regularization ($0.5 \cdot \mathbf{I}$)
3. Use pseudo-inverse as last resort

#### 3.0.2.6 Trivial Solution Detection

After convergence, the algorithm checks if the solution is trivial (identical to an existing phase):

$$\text{trivialCheck}_k = \sum_i |w_i - x_i^{(k)}|$$

If $\text{trivialCheck}_k < 10^{-4}$ for any existing phase $k$, the stationary point is rejected:

```java
double xTrivialCheck0 = 0.0;
double xTrivialCheck1 = 0.0;

for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
    xTrivialCheck0 += Math.abs(x[j][i] - system.getPhase(0).getComponent(i).getx());
    xTrivialCheck1 += Math.abs(x[j][i] - system.getPhase(1).getComponent(i).getx());
}

if (Math.abs(xTrivialCheck0) < 1e-4 || Math.abs(xTrivialCheck1) < 1e-4) {
    tm[j] = 10.0;  // Mark as stable (trivial solution)
}
```

#### 3.0.2.7 Phase Addition from Unstable Stationary Point

When an unstable stationary point is found ($\text{tm} < -10^{-8}$):

1. **Add new phase** to the system
2. **Set composition** from the converged stationary point
3. **Set initial phase fraction** proportional to the destabilizing component's feed fraction
4. **Normalize** phase fractions

```java
if (tm[k] < -1e-8 && !(Double.isNaN(tm[k]))) {
    system.addPhase();
    unstabcomp = k;
    
    // Set composition from stationary point
    for (int i = 0; i < system.getPhase(1).getNumberOfComponents(); i++) {
        system.getPhase(system.getNumberOfPhases() - 1).getComponent(i).setx(x[k][i]);
    }
    system.getPhases()[system.getNumberOfPhases() - 1].normalize();
    
    // Set initial phase fraction
    multiPhaseTest = true;
    system.setBeta(system.getNumberOfPhases() - 1,
                   system.getPhase(0).getComponent(unstabcomp).getz());
    system.init(1);
    system.normalizeBeta();
    return;  // Exit stability analysis, proceed to phase split
}
```

#### 3.0.2.8 Multiple Stability Analysis Variants

NeqSim implements three stability analysis methods in `TPmultiflash`:

| Method | Description | Use Case |
|--------|-------------|----------|
| `stabilityAnalysis()` | Single cloned system, optimized for performance | Primary method |
| `stabilityAnalysis2()` | Multiple cloned systems (one per component) | Alternative for difficult cases |
| `stabilityAnalysis3()` | Re-run after phase removal | Post-processing verification |

The main `run()` method orchestrates these:
```java
if (doStabilityAnalysis) {
    stabilityAnalysis();  // Primary stability check
}

// ... phase equilibrium calculation ...

if (hasRemovedPhase && !secondTime) {
    secondTime = true;
    stabilityAnalysis3();  // Re-check after phase removal
    run();  // Recursive call
}
```

### 3.0.3 Enhanced Stability Analysis

When `system.setEnhancedMultiPhaseCheck(true)` is enabled, an additional stability analysis is performed using Wilson K-value based initialization. This is particularly useful for detecting liquid-liquid equilibria in complex mixtures such as sour gas systems (methane/CO₂/H₂S).

#### Motivation

The standard stability analysis may fail to detect additional phases in certain systems because:

1. **Pure component initialization** may not provide good starting points for LLE
2. **Wilson K-values** are vapor-pressure based and work well for VLE but not LLE
3. Some systems require testing both **vapor-like** and **liquid-like** trial phases
4. **Polarity-driven LLE** requires different initialization strategies

#### Enhanced Algorithm

The enhanced stability analysis (`stabilityAnalysisEnhanced()`) addresses these limitations:

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║                stabilityAnalysisEnhanced() ALGORITHM FLOW                      ║
╚═══════════════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: WILSON K-VALUE CALCULATION                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  FOR each valid component i (z > 1e-100, not ionic):                           │
│     K[i] = (Pc[i] / P) × exp[5.373 × (1 + ω[i]) × (1 - Tc[i]/T)]              │
│     log(K[i]) = ln(K[i])                                                        │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: PRE-CALCULATE REFERENCE FUGACITIES                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│  FOR each existing phase p = 0 to numPhases-1:                                 │
│     FOR each component k:                                                       │
│        d_ref[p][k] = ln(x[p][k]) + ln(φ[p][k])                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
   ╔═════════════════════════════════════════════════════════════════════════════╗
   ║  FOR EACH EXISTING PHASE AS REFERENCE (p = 0 to numPhases-1):               ║
   ╠═════════════════════════════════════════════════════════════════════════════╣
   ║  FOR EACH TRIAL TYPE (vapor-like, liquid-like, LLE):                        ║
   ╚═════════════════════════════════════════════════════════════════════════════╝
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 3: TRIAL PHASE INITIALIZATION                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  trialType = 1:  VAPOR-LIKE (VLE gas detection)                                │
│     W[i] = exp(ln(K[i]))  → volatile components enriched                       │
│                                                                                 │
│  trialType = -1: LIQUID-LIKE (VLE liquid detection)                            │
│     W[i] = exp(-ln(K[i])) = 1/K[i]  → heavy components enriched               │
│                                                                                 │
│  trialType = 0:  LLE TRIAL (polarity-based perturbation)                       │
│     perturbFactor = 2.0 if ω[i] > 0.15 (polar), else 0.5 (non-polar)          │
│     W[i] = z[i] × perturbFactor                                                │
│                                                                                 │
│  Note: LLE uses acentric factor as polarity proxy since Wilson K-values       │
│  are derived from vapor pressure and don't capture activity coefficient-       │
│  driven liquid-liquid splits.                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: SSI LOOP WITH WEGSTEIN ACCELERATION                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│  FOR iter = 1 to maxIter (300):                                                │
│     Calculate fugacity coefficients at normalized w[i]                         │
│     Update: ln(W[i]) = d_ref[p] - ln(φ[i])                                     │
│                                                                                 │
│     IF iter % 5 == 0 AND iter > 5 (Wegstein acceleration):                     │
│        λ = Σ(Δln(W)^n × Δln(W)^n-1) / Σ(Δln(W)^n-1)²                          │
│        λ = clamp(λ, -0.5, 0.9)                                                 │
│        ln(W[i]) += λ/(1-λ) × Δln(W[i])                                         │
│                                                                                 │
│     Check convergence: err = Σ|ln(W[i])^n - ln(W[i])^n-1|                      │
│     IF err < 1e-10: BREAK                                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 5: STABILITY CHECK AND PHASE ADDITION                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│  tm = 1 - Σ W[i]                                                               │
│                                                                                 │
│  Check for trivial solution (composition too close to existing phase)          │
│                                                                                 │
│  IF tm < -1e-8 AND NOT trivial:                                                │
│     → Add new phase with composition w[i] = W[i]/ΣW[j]                         │
│     → multiPhaseTest = true                                                     │
│     → RETURN                                                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Key Differences from Standard Stability Analysis

| Feature | Standard Analysis | Enhanced Analysis |
|---------|-------------------|-------------------|
| Initial guess | Pure component | Wilson K-values |
| Trial types | Single | Vapor-like, Liquid-like, LLE |
| Reference phase | Phase 0 only | All existing phases |
| LLE detection | Component-based | Polarity perturbation |
| Acceleration | DEM every 7 iterations | Wegstein every 5 iterations |
| Hydrocarbon filtering | Yes (only heaviest/lightest) | No (all components tested) |

#### When to Enable Enhanced Stability Analysis

Enable `setEnhancedMultiPhaseCheck(true)` for:

- **Sour gas systems**: Methane/CO₂/H₂S mixtures at low temperatures
- **CO₂ systems**: CO₂ injection, sequestration, EOR applications
- **Near-critical systems**: Where standard analysis may miss phase splits
- **LLE detection**: Systems with polar/non-polar liquid-liquid equilibria

**Example usage:**
```java
SystemInterface fluid = new SystemPrEos(210.0, 55.0);  // Low T, moderate P
fluid.addComponent("methane", 49.88);
fluid.addComponent("CO2", 9.87);
fluid.addComponent("H2S", 40.22);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);
fluid.setEnhancedMultiPhaseCheck(true);  // Enable enhanced detection

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
// May find vapor + CO2-rich liquid + H2S-rich liquid
```

**Note:** Enhanced stability analysis adds computational overhead. For simple VLE systems, the standard analysis is sufficient and more efficient.

### 3.1 Multiphase Equilibrium Formulation

For systems with $N_p$ phases, the equilibrium conditions become:

$$f_i^{(1)} = f_i^{(2)} = \cdots = f_i^{(N_p)} \quad \text{for } i = 1, \ldots, N_c$$

**Material Balance:**

$$z_i = \sum_{k=1}^{N_p} \beta_k x_i^{(k)}$$

With the constraint:

$$\sum_{k=1}^{N_p} \beta_k = 1$$

### 3.2 Q-Function Minimization

Michelsen (1982) introduced the **Q-function** for multiphase flash:

$$Q = \sum_{k=1}^{N_p} \beta_k - \sum_{i=1}^{N_c} z_i \ln E_i$$

Where:
$$E_i = \sum_{k=1}^{N_p} \frac{\beta_k}{\phi_i^{(k)}}$$

**Gradient:**
$$\frac{\partial Q}{\partial \beta_k} = 1 - \sum_{i=1}^{N_c} \frac{z_i}{E_i \phi_i^{(k)}}$$

**Hessian:**
$$\frac{\partial^2 Q}{\partial \beta_k \partial \beta_l} = \sum_{i=1}^{N_c} \frac{z_i}{E_i^2 \phi_i^{(k)} \phi_i^{(l)}}$$

**Newton Update:**

$$\boldsymbol{\beta}^{(n+1)} = \boldsymbol{\beta}^{(n)} - \mathbf{H}^{-1} \nabla Q$$

**Phase Compositions:**

$$x_i^{(k)} = \frac{z_i}{E_i \phi_i^{(k)}}$$

**NeqSim Implementation:**

```java
// From TPmultiflash.java - calcQ() and solveBeta()
public double calcQ() {
    this.calcE();  // Calculate E_i
    
    // Compute gradient dQ/dβ
    for (int k = 0; k < system.getNumberOfPhases(); k++) {
        dQdbeta[k][0] = 1.0;
        for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
            dQdbeta[k][0] -= multTerm[i] / system.getPhase(k).getComponent(i).getFugacityCoefficient();
        }
    }
    
    // Compute Hessian Q_matrix
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
        for (int j = 0; j < system.getNumberOfPhases(); j++) {
            Qmatrix[i][j] = 0.0;
            for (int k = 0; k < system.getPhase(0).getNumberOfComponents(); k++) {
                Qmatrix[i][j] += multTerm2[k] / (phi_j[k] * phi_i[k]);
            }
        }
    }
    return Q;
}
```

### 3.3 Phase Addition and Removal

**Phase Addition:**

When stability analysis indicates an unstable phase (tm < 0):
1. A new phase is added to the system
2. Initial composition is taken from the unstable stationary point
3. Initial phase fraction is set to a small value (~0.001)

**Phase Removal:**

Phases with negligible fractions ($\beta_k < \beta_{\min}$) are removed:

```java
// From TPmultiflash.java - run()
for (int i = 0; i < system.getNumberOfPhases(); i++) {
    if (system.getBeta(i) < 1.1 * phaseFractionMinimumLimit) {
        system.removePhaseKeepTotalComposition(i);
    }
}
```

**Trivial Solution Detection:**

Phases with nearly identical densities are merged:

```java
if (Math.abs(system.getPhase(i).getDensity() - system.getPhase(j).getDensity()) < 1.1e-5) {
    system.removePhaseKeepTotalComposition(j);
}
```

### 3.4 Complete Multiphase Flash Workflow

The complete workflow in `TPmultiflash.run()` is:

```
┌─────────────────────────────────────────────────────────────┐
│  1. PREPROCESSING                                           │
│     - For electrolyte systems: temporarily remove ions      │
│     - Store ionic compositions for later restoration        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  2. STABILITY ANALYSIS                                      │
│     - Test component-seeded trial phases                    │
│     - Use SSI + DEM acceleration + Newton fallback          │
│     - If tm < -1e-8: add new phase, set multiPhaseTest=true │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  3. ADDITIONAL PHASE SEEDING (if stability didn't add)      │
│     - seedAdditionalPhaseFromFeed(): gas phase seeding      │
│     - seedHydrocarbonLiquidFromFeed(): oil phase seeding    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  4. ION RESTORATION (electrolyte systems)                   │
│     - Restore ions to aqueous phase(s) only                 │
│     - Set ion x = 1e-50 in non-aqueous phases               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  5. CHEMICAL EQUILIBRIUM (if applicable)                    │
│     - Solve chemical equilibrium in aqueous phase           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  6. PHASE SPLIT CALCULATION (if multiPhaseTest = true)      │
│     - Q-function minimization with Newton's method          │
│     - Nested iteration with chemical equilibrium            │
│     - Continue until convergence                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  7. AQUEOUS PHASE SEEDING (if water present but no aq)      │
│     - Add aqueous phase seeded with water                   │
│     - Re-run phase split if phase added                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  8. PHASE CLEANUP                                           │
│     - Remove phases with β < βmin                           │
│     - Detect and merge trivial solutions (same density)     │
│     - ensureSingleAqueousPhase() for electrolytes           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  9. POST-REMOVAL STABILITY CHECK                            │
│     - If phase removed: run stabilityAnalysis3()            │
│     - Recursive call to run() if new phase found            │
└─────────────────────────────────────────────────────────────┘
```

### 3.5 Phase Seeding Strategies

Beyond stability analysis, NeqSim uses heuristic phase seeding to improve convergence:

#### 3.5.1 Gas Phase Seeding from Feed

When an aqueous phase exists without a gas phase, seed a gas phase:

```java
private boolean seedAdditionalPhaseFromFeed() {
    // Only if multiphase check enabled and < 3 phases
    if (!system.doMultiPhaseCheck() || system.getNumberOfPhases() >= 3)
        return false;
    
    // Need aqueous phase but no gas phase
    boolean hasAqueous = false, hasGas = false;
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        if (type == PhaseType.GAS) hasGas = true;
        if (type == PhaseType.AQUEOUS) hasAqueous = true;
    }
    if (!hasAqueous || hasGas) return false;
    
    // Seed gas phase with feed composition
    system.addPhase();
    system.setPhaseType(phaseIndex, PhaseType.GAS);
    for (int comp = 0; comp < ncomp; comp++) {
        system.getPhase(phaseIndex).getComponent(comp).setx(z[comp]);
    }
    system.setBeta(phaseIndex, 1e-3);
    return true;
}
```

#### 3.5.2 Aqueous Phase Seeding

When water is present but no aqueous phase exists:

```java
if (waterZ > 1.0e-6 && !system.hasPhaseType(PhaseType.AQUEOUS)) {
    system.addPhase();
    system.setPhaseType(aquPhaseIndex, PhaseType.AQUEOUS);
    
    // Initialize with water-concentrated composition
    for (int comp = 0; comp < ncomp; comp++) {
        double x = 1.0e-16;
        if (comp == waterComponentIndex) {
            x = Math.max(waterZ, 1.0e-12);  // Concentrate water
        } else if (!isHydrocarbon(comp) && !isInert(comp)) {
            x = Math.min(z[comp] * 1.0e-2, 1.0e-8);  // Trace aqueous components
        }
        system.getPhase(aquPhaseIndex).getComponent(comp).setx(x);
    }
    system.setBeta(aquPhaseIndex, 1e-5);
}
```

---

## 4. Electrolytes and Chemical Reactions

### 4.1 Chemical Equilibrium Coupling

For systems with chemical reactions (electrolytes, acid-base equilibria), the flash calculation must be coupled with chemical equilibrium. NeqSim solves this as a nested iteration:

**Outer Loop:** Phase equilibrium (flash)
**Inner Loop:** Chemical equilibrium within each phase

**Chemical Equilibrium Condition:**

For a reaction $\sum_i \nu_i A_i = 0$:

$$\sum_i \nu_i \mu_i = 0$$

Or equivalently:

$$\prod_i a_i^{\nu_i} = K_{eq}(T)$$

Where $a_i$ is the activity and $K_{eq}$ is the equilibrium constant.

**NeqSim Implementation:**

```java
// From TPflash.java - chemical equilibrium integration
if (system.isChemicalSystem()) {
    for (int phaseNum = 0; phaseNum < system.getNumberOfPhases(); phaseNum++) {
        if ("aqueous".equalsIgnoreCase(phaseType)) {
            system.getChemicalReactionOperations().solveChemEq(phaseNum, 0);
            system.getChemicalReactionOperations().solveChemEq(phaseNum, 1);
        }
    }
}
```

The chemical equilibrium solver uses:
- **Level 0:** Stoichiometric balance with linear programming initialization
- **Level 1:** Full Newton-Raphson with activity coefficient derivatives

### 4.2 Ion Handling in Stability Analysis

Ionic species present special challenges for stability analysis because they cannot exist in non-aqueous phases. NeqSim handles this by:

1. **Temporarily removing ions** before stability analysis:
   ```java
   if (system.isChemicalSystem()) {
       ionicZ = new double[system.getPhase(0).getNumberOfComponents()];
       for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
           if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
               ionicZ[i] = system.getPhase(0).getComponent(i).getz();
               // Temporarily set to near-zero
               system.getPhase(phase).getComponent(i).setz(1e-100);
           }
       }
   }
   ```

2. **Running stability analysis** on the neutral system

3. **Restoring ions** to aqueous phases after phase configuration is determined:
   ```java
   if (hasIons && ionicZ != null) {
       for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
           if (system.getPhase(0).getComponent(i).getIonicCharge() != 0) {
               // Restore z values, put ions only in aqueous phase
               if (system.getPhase(phase).getType() == PhaseType.AQUEOUS) {
                   system.getPhase(phase).getComponent(i).setx(ionicZ[i]);
               } else {
                   system.getPhase(phase).getComponent(i).setx(1e-50);
               }
           }
       }
   }
   ```

### 4.3 Aqueous Phase Management

For electrolyte systems, NeqSim ensures proper aqueous phase handling:

**Single Aqueous Phase Constraint:**

The system ensures only one aqueous phase exists, containing all ionic species:

```java
private void ensureSingleAqueousPhase() {
    if (!system.isChemicalSystem() || system.getNumberOfPhases() < 2) {
        return;
    }
    
    // Find phase with highest aqueous component content
    int bestAqueousPhase = -1;
    double maxAqueousContent = 0.0;
    
    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
        double aqueousContent = 0.0;
        for (int comp = 0; comp < ncomp; comp++) {
            // Count water, glycols, alcohols, and ions
            if (isAqueousComponent(component)) {
                aqueousContent += component.getx();
            }
        }
        if (aqueousContent > maxAqueousContent) {
            maxAqueousContent = aqueousContent;
            bestAqueousPhase = phase;
        }
    }
    
    // Reclassify other phases as OIL
}
```

**Aqueous Phase Seeding:**

When water is present but no aqueous phase exists, a seed aqueous phase can be created:

```java
if (waterZ > 1.0e-6 && !system.hasPhaseType(PhaseType.AQUEOUS)) {
    system.addPhase();
    system.setPhaseType(aquPhaseIndex, PhaseType.AQUEOUS);
    // Initialize with water-rich composition
}
```

---

## 5. References

### Primary References

1. **Michelsen, M. L.** (1982). "The isothermal flash problem. Part I. Stability." *Fluid Phase Equilibria*, 9(1), 1-19. 
   - Introduces the tangent plane distance criterion for stability analysis.

2. **Michelsen, M. L.** (1982). "The isothermal flash problem. Part II. Phase-split calculation." *Fluid Phase Equilibria*, 9(1), 21-40.
   - Presents the Q-function minimization for multiphase flash.

3. **Michelsen, M. L. & Mollerup, J. M.** (2007). *Thermodynamic Models: Fundamentals and Computational Aspects*, 2nd Ed. Tie-Line Publications.
   - Comprehensive textbook covering all aspects of phase equilibrium calculations.

### Rachford-Rice Equation

4. **Rachford, H. H. & Rice, J. D.** (1952). "Procedure for use of electronic digital computers in calculating flash vaporization hydrocarbon equilibrium." *Journal of Petroleum Technology*, 4(10), 19-3.

5. **Nielsen, R. F. & Lia, A.** (2023). "Avoiding round-off error in the Rachford–Rice equation." *Fluid Phase Equilibria*, 571, 113801.
   - Robust reformulation used in the Nielsen2023 solver.

### Successive Substitution and Acceleration

6. **Mehra, R. K., Heidemann, R. A., & Aziz, K.** (1983). "An accelerated successive substitution algorithm." *The Canadian Journal of Chemical Engineering*, 61(4), 590-596.
   - Dominant eigenvalue method for acceleration.

### Chemical Equilibrium

7. **Smith, W. R. & Missen, R. W.** (1982). *Chemical Reaction Equilibrium Analysis: Theory and Algorithms*. Wiley-Interscience.

8. **Michelsen, M. L.** (1989). "Calculation of multiphase equilibrium in ideal solutions." *Fluid Phase Equilibria*, 53, 73-80.

### Electrolyte Systems

9. **Thomsen, K.** (2005). "Modeling electrolyte solutions with the extended UNIQUAC model." *Pure and Applied Chemistry*, 77(3), 531-542.

---

## Implementation Files

| File | Description |
|------|-------------|
| [TPflash.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/TPflash.java) | Two-phase flash with SSI and Newton |
| [TPmultiflash.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/TPmultiflash.java) | Multi-phase flash with stability analysis |
| [RachfordRice.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/RachfordRice.java) | Rachford-Rice equation solvers |
| [SysNewtonRhapsonTPflash.java](../../src/main/java/neqsim/thermodynamicoperations/flashops/SysNewtonRhapsonTPflash.java) | Second-order Newton solver |
| [ChemicalReactionOperations.java](../../src/main/java/neqsim/chemicalreactions/ChemicalReactionOperations.java) | Chemical equilibrium solver |

---

## Usage Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create system
SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
system.addComponent("methane", 0.7);
system.addComponent("ethane", 0.2);
system.addComponent("propane", 0.1);
system.setMixingRule("classic");

// Enable multi-phase check for stability analysis
system.setMultiPhaseCheck(true);

// Perform TP flash
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Results
System.out.println("Number of phases: " + system.getNumberOfPhases());
System.out.println("Vapor fraction: " + system.getBeta(0));
system.display();
```

For electrolyte systems:

```java
import neqsim.thermo.system.SystemElectrolyteCPA;

SystemElectrolyteCPA system = new SystemElectrolyteCPA(298.15, 1.0);
system.addComponent("CO2", 1.0);
system.addComponent("water", 100.0);
system.setMixingRule(10);  // CPA mixing rule with electrolyte support
system.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();  // Automatically solves chemical equilibrium in aqueous phase
```
