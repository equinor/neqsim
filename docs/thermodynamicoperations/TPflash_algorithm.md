---
title: "TPflash Algorithm Documentation"
description: "Temperature-pressure flash algorithm reference for NeqSim, covering VLE, VLLE, LLE, Rachford-Rice, tangent-plane stability analysis, Newton refinement, multiphase flash workflow, performance, and robustness recommendations."
---

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
5. [Performance Optimizations](#5-performance-optimizations)
   - [5.1 Fugacity Coefficient Cache](#51-fugacity-coefficient-cache)
   - [5.2 EJML Matrix Operations](#52-ejml-matrix-operations)
   - [5.3 Wilson K Early Exit](#53-wilson-k-early-exit)
   - [5.4 Two-Stage Trial Strategy](#54-two-stage-trial-strategy)
6. [State-of-the-Art Comparison and Recommendations](#6-state-of-the-art-comparison-and-recommendations)
    - [6.1 Current Position](#61-current-position)
    - [6.2 Strengths](#62-strengths)
    - [6.3 Recommended Improvements](#63-recommended-improvements)
    - [6.4 Regression and Benchmark Coverage](#64-regression-and-benchmark-coverage)
7. [References](#7-references)

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
│     • accelerateInterval = 5 (use DEM every 5 iterations)                       │
│     • newtonLimit = 12 (switch to Newton after 12 SSI iterations)               │
│     • Enhanced multiphase systems may lower these thresholds adaptively          │
│     • maxNumberOfIterations = 50 (default)                                      │
│     • convergence tolerance = 1e-10                                             │
│                                                                                 │
│  DO (outer loop for chemical systems):                                          │
│  │   iterations = 0                                                             │
│  │   DO (inner loop):                                                           │
│  │   │   iterations++                                                           │
│  │   │                                                                          │
│  │   │   IF iterations < 12 (or chemical system, or no fugacity derivatives):   │
│  │   │   │   IF timeFromLastGibbsFail > 6 AND iterations % 5 == 0:              │
│  │   │   │       → accselerateSucsSubs()  [DEM acceleration]                    │
│  │   │   │   ELSE:                                                              │
│  │   │   │       → sucsSubs()  [standard SSI]                                   │
│  │   │   │                                                                      │
│  │   │   ELSE IF iterations >= 12:                                              │
│  │   │   │   IF solver is new or component count changed:                       │
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
│     → Preserve an already balanced neutral two-phase aqueous reference          │
│     → Delegate to TPmultiflash for stability analysis and phase split           │
│     → If a rejected third-phase trial leaves the same topology invalid,         │
│       restore the balanced reference; retain GAS↔OIL root transitions          │
│     → If cleanup collapses a strong water/non-water K split to one phase, retry │
│       the ordinary flash and retain it only when balanced and lower in Gibbs    │
│     → If a neutral three-phase beta solve stalls, test the three two-phase      │
│       active sets and retain only a balanced, equilibrated lower-Gibbs endpoint │
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
│  IF aqueous multiphase checking follows an accepted stable single-phase test:    │
│     → Reject an endpoint only when a phase composition contains non-finite or     │
│       out-of-range values, or fails |Σxᵢ - 1| ≤ 1e-8                            │
│     → Keep normalized endpoints for model-specific convergence and refinement    │
│       paths                                                                       │
│                                                                                 │
│  IF ordinary flash and water feed ≥ 0.01:                                       │
│     → Refine a missing aqueous phase, or an existing two-phase aqueous           │
│       endpoint when max |Δ ln(fᵢ)| ≥ 1e-8 or max |Δzᵢ| ≥ 1e-8                   │
│     → Evaluate a cloned TPmultiflash candidate                                  │
│     → Accept a lower-Gibbs physical candidate, or replace a non-conservative     │
│       reference only with a balanced, equilibrated physical candidate            │
│                                                                                 │
│  IF ordinary CPA flash ends in one AQUEOUS phase with water feed ≥ 0.01:        │
│     → Require f_water / p_water_sat ≥ 0.8 and at least 0.01 condensable          │
│       hydrocarbon feed with T_c > T + 80 K; these checks gate cost only          │
│     → Evaluate one cloned multiphase stability candidate                         │
│     → Accept only an exactly-two-phase, normalized, balanced, fugacity-equal,    │
│       distinct, lower-Gibbs OIL+AQUEOUS endpoint                                │
│                                                                                 │
│  IF trace water, GAS+OIL, min(β) ≤ 0.01, and x_water,oil ≥ 10 z_water:           │
│     → Run the existing aqueous tangent-plane stability trial on a clone          │
│     → Solve the multiphase beta problem, remove exactly one disappearing phase   │
│       or merge exactly one composition-identical duplicate, then rebuild and     │
│       reconverge the resulting two-phase active set                              │
│     → Replace only with a normalized, balanced, fugacity-equal, distinct,        │
│       lower-Gibbs GAS+AQUEOUS endpoint; otherwise retain the original state       │
│                                                                                 │
│  IF ordinary CPA flash ends in one phase with water feed < 0.01:                │
│     → Screen water fugacity against pure-water vapor pressure                    │
│     → If f_water / p_water_sat >= 0.8, run the same aqueous TPD trial on a clone │
│     → Rebuild and solve the two-phase active set when the trial adds a phase     │
│     → Accept only a normalized, balanced, fugacity-equal, distinct GAS+AQUEOUS   │
│       state that lowers Gibbs energy beyond max(1e-8 J, 1e-12 abs(G))            │
│     → The saturation ratio gates cost only; TPD and strict acceptance decide     │
│       stability                                                                   │
│                                                                                 │
│  IF ordinary, neutral, exactly-two-phase result contains an aqueous phase:       │
│     → Evaluate gas-like and liquid-like roots of the non-aqueous cubic phase     │
│     → Replace only with a lower-Gibbs root that already satisfies                │
│       max |Δ ln(fᵢ)| < 1e-8; phase fractions and compositions stay unchanged     │
│                                                                                 │
│  IF multiphase, neutral GAS+AQUEOUS has a lower alternate cubic root:            │
│     → Evaluate one ordinary candidate from the unchanged feed                    │
│     → Accept exactly one AQUEOUS plus one GAS/OIL/LIQUID phase; the cubic root   │
│       may change label only when balance, fugacity, and lower-Gibbs gates pass    │
│                                                                                 │
│  IF a final neutral two-phase aqueous endpoint with water feed ≥ 0.01 fails:     │
│     → Audit max |Δzᵢ| and max |Δ ln(fᵢ)| against 1e-8                            │
│     → Retain the selected two-phase active set and run at most three              │
│       TPmultiflash Q-function/beta refinements                                   │
│     → Keep only a normalized, material-balanced, fugacity-equal endpoint;         │
│       otherwise restore the pre-refinement state                                 │
│     → Reject a Gibbs increase unless the reference was non-conservative,          │
│       because Gibbs energies of an unbalanced reference are not comparable       │
│                                                                                 │
│  IF a final neutral gas/oil endpoint is balanced but near-converged:             │
│     → Trigger only for 1e-8 ≤ max |Δ ln(fᵢ)| ≤ 1e-5                              │
│     → Run at most eight SSI updates without changing the selected phase types     │
│     → Keep only a normalized, balanced, fugacity-equal result with no Gibbs       │
│       increase; otherwise restore beta, compositions, K-values, and phase types   │
│                                                                                 │
│  IF ordinary, neutral, dry two-phase hydrocarbon roots are inconsistent:         │
│     → Trigger for inverted mean-molar-mass order or max |Δ ln(fᵢ)| ≥ 1e-8        │
│     → For inverted order, evaluate vapor/light and liquid/heavy roots together   │
│     → Otherwise evaluate both roots on each phase while leaving the other fixed  │
│     → Retain the selected root seed and public phase identity only for lower     │
│       Gibbs energy and max |Δ ln(fᵢ)| < 1e-8                                     │
│     → In both cases, beta, x, and material balance stay unchanged                │
│                                                                                 │
│  IF chemical system:                                                            │
│     → Final chemical equilibrium solve in aqueous/liquid phases                 │
│                                                                                 │
│  FINALIZE active phase fractions:                                               │
│     → If multiphase checking returns the unchanged stable one-phase state,       │
│       normalize its stale beta to one                                            │
│     → That unchanged one-phase endpoint therefore returns beta = 1              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

#### Key Algorithm Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `phaseFractionMinimumLimit` | ~1e-12 | Minimum allowed phase fraction |
| Unchanged one-phase closure | `abs(beta - 1) < 1e-12`; `abs(delta Z) <= 1e-11`; `max abs(delta x_i) <= 1e-11` | When multiphase checking finds no new stable phase and returns the same phase type, compressibility factor, and composition as the accepted one-phase reference, normalize a stale beta and reinitialize. Different roots, chemical-equilibrium states, and internal multiphase trial states retain their existing finalization paths. |
| Ionic GAS+AQUEOUS closure | exactly two phases, ions present, and `isChemicalSystem() == false` | Set ionic gas-to-aqueous K-values to zero, retain molecular fugacity-coefficient K-values, and solve the constrained Rachford-Rice equation by safeguarded bisection. Successive substitution continues until beta and compositions change by less than `1e-12`. Accept only normalized phases with `1e-10` component balance, `1e-8` molecular log-fugacity equality, gas-phase ion exclusion, and no Gibbs increase; otherwise restore the original endpoint and emit a diagnostic. One-phase, OIL+AQUEOUS, reactive, and genuine three-phase paths are unchanged. |
| Trace duplicate phase cleanup | `min(beta_i, beta_j) < 10 beta_min`, same `PhaseType`, and `max abs(x_i - x_j) < 1e-6` | Merge and remove an already-disappeared numerical duplicate for any EOS while conserving phase fraction. Material-fraction duplicate cleanup remains limited to CPA models to protect near-critical cubic-EOS splits. |
| Initial SSI iterations | 3 | Preliminary iterations before stability check |
| `accelerateInterval` | 5 | Apply DEM every 5th iteration in the ordinary TPflash loop |
| `newtonLimit` | 12 | Switch to Newton-Raphson after 12 SSI iterations when derivatives are available |
| Enhanced multiphase adaptation | 3-4 / 8-10 | Lower acceleration and Newton thresholds when enhanced stability checks are active and the SSI deviation is already small |
| `maxNumberOfIterations` | 50 | Maximum iterations per convergence loop |
| Convergence tolerance | 1e-10 | Deviation threshold for K-value convergence |
| Gibbs increase tolerance | 1e-8 | Relative increase that triggers K-reset |
| Supplementary stability TPD limit | -1e-6 | Accept a converged amplified-K or composition-perturbation trial only when its reduced TPD exceeds that SSI solve's residual/step resolution and the trial composition is non-trivial |
| Ordinary water-rich refinement feed threshold | 0.01 mole fraction water | Avoid phase-search overhead for valid trace-water flashes. An already-active neutral aqueous split bypasses only this feed threshold when `max abs(Delta z_i)` is non-finite or above `1e-8`, allowing the bounded beta correction below without another stability calculation. An incipient trace-water GAS+OIL endpoint with `min(beta) <= 1e-4` also bypasses the threshold when its confirmed log-fugacity residual is non-finite or at least `1e-8`; the guarded candidate must then pass the strict feasibility and lower-Gibbs gates. |
| Water-rich cross-algorithm fallback | water feed `>= 0.01` and current `max abs(Delta ln(f_i)) >= 1e-8`, material-balance failure, or multiphase collapse to one hydrocarbon phase | Evaluate the existing cold candidate through the alternate path first. If an invalid ordinary two-phase split remains after that candidate is rejected, preserve its compositions as the seed for one fully initialized `TPmultiflash` trial. A multiphase endpoint collapsed to one phase may try the ordinary path, including the same seeded refinement when the cold ordinary result is invalid, but accepts only a result that restores an AQUEOUS phase; ordinary gas appearance remains outside this aqueous-recovery fallback. Genuine OIL+AQUEOUS liquid-liquid endpoints remain on the multiphase path. Prevent reciprocal recursion and replace the endpoint only when the candidate has exactly two distinct bounded phases, normalized beta and compositions, material balance and fugacity residuals below `1e-8`, and a Gibbs reduction larger than `max(1e-6 J, 1e-8 abs(G))`. Already-feasible two-phase endpoints incur only the residual acceptance scan. |
| CPA high-water hydrocarbon-liquid stability screen | ordinary one-phase AQUEOUS CPA endpoint, water feed `>= 0.01`, `f_water / p_water_sat >= 0.8`, and at least 0.01 hydrocarbon feed with `T_c > T + 80 K` | Use the water saturation, composition, and critical-temperature checks only as a performance gate for one cloned multiphase stability calculation. Accept only an exactly-two-phase, distinct, bounded, normalized result with material-balance and log-fugacity residuals below `1e-8` and a Gibbs reduction larger than `max(1e-8 J, 1e-12 abs(G))`. Chemical, ionic, solid, wax, non-CPA, explicit-multiphase, gas, and non-aqueous endpoints retain their existing paths. |
| Trace-water aqueous-stability screen | water feed `< 0.01`, GAS+OIL, `min(beta) <= 0.01`, and `x_water,oil >= 10 z_water` | Use the structural conditions only as a performance gate for an aqueous TPD trial. The TPD result, reduced-active-set convergence below `1e-10`, phase normalization, `1e-8` material/fugacity checks, distinct compositions, and lower Gibbs energy determine acceptance. Full recursive TPmultiflash is not run. |
| CPA one-phase aqueous-stability screen | water feed `< 0.01`, one ordinary phase, CPA model, and `f_water / p_water_sat >= 0.8` | Use the fugacity ratio only as a conservative performance gate for the existing aqueous TPD trial. When the trial adds one phase, rebuild the two-phase active set and require beta-solver residual `< 1e-10`, phase normalization, `1e-8` material/fugacity checks, distinct compositions, and a Gibbs reduction larger than `max(1e-8 J, 1e-12 abs(G))`. The tighter Gibbs tolerance retains independently converged incipient aqueous fractions without treating the saturation screen itself as a stability criterion. |
| Water-rich material-balance tolerance | 1e-8 in `max abs(Delta z_i)` | Reject a non-conservative reference before comparing feasible Gibbs minima |
| Dry cubic-root screen and acceptance | Screen normally ordered GAS+OIL endpoints when `max abs(Delta ln(f_i)) >= 1e-8`; accept below `1e-8` | Evaluate both roots for one phase at a time and retain a lower-Gibbs root seed only when the resulting unchanged composition split restores fugacity equality. Inverted mean-molar-mass order retains the paired-root comparison. |
| Aqueous cubic-root equilibrium tolerance | 1e-8 in `max abs(Delta ln(f_i))` | Ordinary flashes accept an alternate root only when it lowers Gibbs energy and already satisfies component fugacity equality. A multiphase GAS+AQUEOUS endpoint may replay a lower-Gibbs ordinary candidate containing exactly one AQUEOUS and one cubic GAS/OIL/LIQUID phase, so a valid gas-to-oil root transition is not rejected by its new label. The candidate must additionally retain normalized positive phases, distinct compositions, and component balance below `1e-8`. |
| Stable-single-phase aqueous-seed gate | `1e-8` in phase-composition normalization | Reject only a structurally invalid aqueous trial whose composition is non-finite, out of `[0, 1]`, or unnormalized; leave normalized endpoints to model-specific convergence and refinement paths |
| Post-removal aqueous recovery | `1e-8` in `max abs(Delta z_i)` and `max abs(Delta ln(f_i))` | Restore a balanced neutral two-phase aqueous reference only when a rejected third-phase trial leaves the same two-phase topology infeasible; a valid GAS↔OIL root transition, genuine three-phase result, or already feasible endpoint is retained |
| Final aqueous active-set refinement | water feed `>= 0.01`, or an active aqueous split with `max abs(Delta z_i) > 1e-8`; at most 3 beta refinements; `1e-8` in phase normalization, `max abs(Delta z_i)`, and `max abs(Delta ln(f_i))` | Preserve the selected neutral two-phase active set while correcting stale beta/compositions after phase cleanup or root selection. Trace-water bypass does not run phase search. Roll back unless the result is feasible; also require no Gibbs increase when the reference material balance was valid. |
| Final neutral gas/oil equilibrium refinement | `1e-8 <= max abs(Delta ln(f_i)) <= 1e-5`; at most 8 SSI updates | Repair only balanced, near-converged vapor-liquid endpoints after post-convergence root handling. Preserve both phase types and roll back unless phase fractions, compositions, material balance, fugacity equality, and Gibbs energy pass the strict acceptance checks. |
| Stalled three-phase active-set fallback | `1e-8` in phase normalization, material balance, and `max abs(Delta ln(f_i))` | For neutral non-reactive systems only, evaluate each two-phase active set after a non-converged three-phase endpoint and accept the lowest-Gibbs feasible equilibrium only when it also lowers Gibbs energy relative to the stalled state |
| Water-bearing single-phase-collapse screen | water feed `>= 0.01`, stored water `K < 1e-2`, and a non-water `K > 10` | Run one bounded ordinary-flash retry only after multiphase cleanup returns one phase with strong retained phase-preference evidence; accept only a balanced, distinct two-phase state that lowers extensive Gibbs energy beyond `max(1e-6 J, 1e-8 abs(G))` |

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

See [RachfordRice.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/RachfordRice.java) for implementation details.

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

See [SysNewtonRhapsonTPflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/SysNewtonRhapsonTPflash.java) for the full implementation.

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
│  IF system.hasIons() (any ionic components, not just chemical systems):         │
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
│     → Add bounded vapor-like gas seed, xᵢ ∝ zᵢKᵢ(Wilson)                       │
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
│  Merge trace composition duplicates for any EOS:                                │
│     IF same PhaseType, min(βi, βj) < 10 βmin, and max |xi - xj| < 1e-6:         │
│        → Merge βi + βj, remove the duplicate, and re-check stability             │
│                                                                                 │
│  Detect trivial solutions (phases with same density):                           │
│     FOR each pair of phases (i, j):                                             │
│        IF |ρi - ρj| < 1.1e-5:                                                  │
│           → Remove phase j                                                      │
│           → hasRemovedPhase = true                                              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STEP 10: POST-FLASH STABILITY CHECK AND RECURSIVE RE-RUN                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│  After flash converges with 2 phases, check for a third phase:                 │
│  IF multiPhaseCheck AND 2 ≤ numPhases < 3 AND NOT postFlashStabilityChecked:   │
│     → postFlashStabilityChecked = true                                          │
│     → stabilityAnalysisEnhanced()  [Wilson K-based additional check]            │
│     → IF new phase found: re-run()  [recursive call]                           │
│                                                                                 │
│  After phase removal (β < βmin or trivial solutions):                          │
│  IF hasRemovedPhase AND NOT secondTime:                                         │
│     → secondTime = true                                                         │
│     → stabilityAnalysis3()  [re-check stability]                               │
│     → run()  [RECURSIVE CALL - restart algorithm]                              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.0.1 Stability Analysis Detailed Flow

The `stabilityAnalysis()` method tests multiple trial phases to find instabilities. It proceeds in two stages: first **Wilson K-based trial phases** (fast, catch most VLE instabilities), then **pure-component trial phases** (slower, catch LLE and unusual splits).

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
│  • Initialize logWi[j] = 0.0 for components with z > 1e-100                    │
│  • Compute Wilson K-values for all valid (non-ionic, non-negligible) components │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STAGE 0: WILSON K EARLY EXIT (O2)                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Calculate Wilson K for each valid component:                                   │
│     K[i] = (Pc[i] / P) × exp[5.373 × (1 + ω[i]) × (1 - Tc[i]/T)]              │
│                                                                                 │
│  Track maxAbsLogK = max |ln(K[i])| across all valid components                 │
│                                                                                 │
│  IF maxAbsLogK < 0.01:                                                          │
│     → All K ≈ 1.0 → system is near or above critical point                     │
│     → Trivially stable, RETURN immediately                                      │
│                                                                                 │
│  This avoids expensive stability analysis for supercritical conditions.         │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STAGE 1: WILSON K-BASED TRIAL PHASES (O3)                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Test two trial phases using Wilson K-values as initialization:                 │
│                                                                                 │
│  Trial 0 — LIQUID-LIKE:    W[i] = z[i] / K[i]                                  │
│     Heavy components (K << 1) are strongly enriched.                            │
│     Detects liquid dropout from gas, water condensation.                        │
│                                                                                 │
│  Trial 1 — VAPOR-LIKE:     W[i] = K[i] × z[i]                                  │
│     Light/volatile components are strongly enriched.                            │
│     Detects vapor formation from liquid.                                        │
│                                                                                 │
│  FOR EACH TRIAL (0 and 1):                                                      │
│  │   Set initial logWi[i] = ln(W[i])                                           │
│  │   SSI loop (up to 50 iterations):                                            │
│  │   │   clonedSystem.init(1,1) — compute fugacity coefficients                │
│  │   │   logWi[i] = d[i] - ln(φ[i])                                            │
│  │   │   Every 7th iteration: Wegstein/DEM acceleration                         │
│  │   │      λ = Σ(ΔlnW^n × ΔlnW^{n-1}) / Σ(ΔlnW^{n-1})²                       │
│  │   │      logWi += λ/(1-λ) × ΔlnW  (only if 0 < λ < 1)                       │
│  │   │   Disable acceleration if error increases                               │
│  │   Calculate tm = 1 - Σ exp(logWi)                                           │
│  │   Trivial solution check: Σ|w - x_existing| < 1e-4                          │
│  │   IF tm < -1e-8 AND NOT trivial AND converged:                               │
│  │      → Add new phase, set composition from trial, RETURN                     │
│  │                                                                              │
│  IF both trials stable → proceed to pure-component trials                      │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        ↓
┌─────────────────────────────────────────────────────────────────────────────────┐
│ STAGE 2: PURE-COMPONENT TRIALS — COMPONENT SELECTION                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Find heaviest and lightest hydrocarbon components (by molar mass)             │
│                                                                                 │
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
│  │   │   IF iter % 5 == 0 AND iter > 5 AND useaccsubst (DEM acceleration):     │
│  │   │   │   Calculate acceleration factor λ:                                  │
│  │   │   │      λ = Σ(ΔlnW^n × ΔlnW^{n-1}) / Σ(ΔlnW^{n-1})²                   │
│  │   │   │   Apply acceleration (only if 0 < λ < 1):                           │
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
│  │   │   Build objective function F and Jacobian J using raw EJML               │
│  │   │   (DMatrixRMaj, pre-allocated outside loop):                            │
│  │   │      F[i] = √W[i] × (lnW[i] + ln(φ[i]) - d[i])                         │
│  │   │      J[i,k] = δ[i,k] + √(W[i]×W[k]) × ∂ln(φ[i])/∂n[k]                  │
│  │   │                                                                          │
│  │   │   Solve Newton step via CommonOps_DDRM.solve():                         │
│  │   │      Δα = -(I + J)⁻¹ × F                                               │
│  │   │      (with regularization: add 0.1 to diagonal if singular)             │
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
| Wilson K trial max iter | 50 | Max iterations for K-based trial phases |
| Wilson K early exit | 0.01 | max\|ln(K)\| threshold for supercritical skip |
| DEM interval (K trials) | 7 | Apply Wegstein acceleration every 7th iter |
| DEM interval (pure-comp) | 5 | Apply DEM acceleration every 5th iter |
| Convergence tolerance | 1e-9 | Error threshold for lnW convergence |
| Instability threshold | -1e-8 | tm value indicating phase split |
| Trivial solution threshold | 1e-4 | Composition difference to detect trivial |

### 3.0.2 Multiphase Stability Analysis Additional Details

The multiphase stability analysis in NeqSim is more sophisticated than the basic two-phase version. It systematically tests multiple trial phase compositions to ensure no additional phases can form.

#### 3.0.2.1 Trial Phase Selection Strategy

The stability analysis proceeds in two stages. First, **Wilson K-based trial phases** are tested (liquid-like $z/K$ and vapor-like $K \cdot z$) — these are fast and catch most VLE instabilities. If both K-based trials are stable, the algorithm falls through to **pure-component trial phases** which test for polarity-driven LLE and other unusual splits.

**Stage 1: Wilson K-based trials** (see Section 3.0.1 flowchart for details):

1. Compute Wilson K-values for all valid components
2. If all $|\ln K_i| < 0.01$ (near-critical), return immediately — system is trivially stable
3. Test liquid-like trial ($W_i = z_i / K_i$): enriches heavy components
4. Test vapor-like trial ($W_i = K_i \cdot z_i$): enriches volatile components
5. If either trial shows instability ($\text{tm} < -10^{-8}$), add phase and return

These Wilson trials are part of ordinary `setMultiPhaseCheck(true)` as well as explicit enhanced
multiphase checking. A negative tangent-plane distance identifies a missing phase composition, but
does not determine that phase's equilibrium amount. The admitted trial therefore starts at the
ordinary beta solver's regularization scale (normally `1e-3`, and never larger than the dominant
component's overall fraction). This keeps the trial incipient without pinning it below the solver's
useful correction scale. The existing phase-fraction and fugacity-equilibrium solve then grows or
removes the phase. This avoids turning a
stability composition guess into an order-one material split while preserving Wilson coverage for
hydrate, electrolyte, and ordinary multiphase callers.

**Stage 2: Pure-component trials** (fallback for cases K-based trials miss):

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

Every 5 iterations in pure-component trials (every 7 in Wilson K trials), the Dominant Eigenvalue Method accelerates convergence:

$$\lambda = \frac{\sum_i (\Delta \ln W_i^{(n)}) \cdot (\Delta \ln W_i^{(n-1)})}{\sum_i (\Delta \ln W_i^{(n-1)})^2}$$

$$\ln W_i^{\text{acc}} = \ln W_i^{(n)} + \frac{\lambda}{1 - \lambda} \Delta \ln W_i^{(n)}$$

Acceleration is only applied when $0 < \lambda < 1$ (convergent regime) and $\sum (\Delta \ln W^{(n-1)})^2 > 10^{-20}$. It is disabled entirely if the error increases (indicating divergence):
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

**Newton update:**
$$\boldsymbol{\alpha}^{(n+1)} = \boldsymbol{\alpha}^{(n)} - \mathbf{J}^{-1} \mathbf{F}$$

The implementation uses **raw EJML** (`DMatrixRMaj`) matrices pre-allocated outside the iteration loop to avoid GC pressure. The system is solved via `CommonOps_DDRM.solve()`:

```java
// Pre-allocate Newton matrices outside the iteration loop
DMatrixRMaj newtonF = new DMatrixRMaj(nc, 1);
DMatrixRMaj newtonJ = new DMatrixRMaj(nc, nc);
DMatrixRMaj newtonDx = new DMatrixRMaj(nc, 1);

// Solve J·dx = F
boolean solved = CommonOps_DDRM.solve(newtonJ, newtonF, newtonDx);
if (!solved) {
    // Regularize: add 0.1 to diagonal and retry
    for (int i = 0; i < nc; i++) {
        newtonJ.add(i, i, 0.1);
    }
    solved = CommonOps_DDRM.solve(newtonJ, newtonF, newtonDx);
}
```

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
| `stabilityAnalysis()` | Wilson K trials + pure-component trials, single clone | Primary method — called first |
| `stabilityAnalysisEnhanced()` | Wilson K + LLE polarity trials, tests all phases | After standard fails; also post-flash 3-phase check |
| `stabilityAnalysis3()` | Re-run after phase removal | Post-processing verification |

The main `run()` method orchestrates these:
```java
if (doStabilityAnalysis) {
    stabilityAnalysis();  // Primary: K trials + pure-component trials
    // If enhanced enabled and standard didn't find additional phases
    if (system.doEnhancedMultiPhaseCheck() && !multiPhaseTest
        && system.getNumberOfPhases() < 3) {
        stabilityAnalysisEnhanced();  // Extended: K + LLE polarity trials
    }
}

// ... phase equilibrium calculation ...

// Post-flash: check for third phase (e.g., VLLE)
if (system.doMultiPhaseCheck() && system.getNumberOfPhases() >= 2
    && system.getNumberOfPhases() < 3 && !postFlashStabilityChecked) {
    postFlashStabilityChecked = true;
    stabilityAnalysisEnhanced();
    if (newPhaseFound) run();  // Recursive call
}

// After phase removal: verify stability
if (hasRemovedPhase && !secondTime) {
    secondTime = true;
    stabilityAnalysis3();
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
| Stage 1 | Wilson K trials (liquid-like z/K, vapor-like K·z) | Wilson K-values (same) |
| Stage 2 initial guess | Pure component | Vapor-like, Liquid-like, LLE trial types |
| Reference phase | Phase 0 only | All existing phases |
| LLE detection | Component-based (pure comp) | Polarity perturbation (acentric factor) |
| K-trial acceleration | Wegstein every 7 iterations | Wegstein every 5 iterations |
| Pure-comp DEM | Every 5 iterations | N/A (no pure-comp fallback) |
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

The `solveBeta()` method uses pre-allocated **EJML** matrices (`DMatrixRMaj`) for efficient Newton steps on the Q-function. The Hessian includes a small diagonal regularization ($10^{-3}$) for numerical stability:

```java
// From TPmultiflash.java - solveBeta()
public double solveBeta() {
    int numPhases = system.getNumberOfPhases();

    // Pre-allocate EJML matrices (reused across iterations)
    DMatrixRMaj gradVec = new DMatrixRMaj(numPhases, 1);
    DMatrixRMaj hessianMat = new DMatrixRMaj(numPhases, numPhases);
    DMatrixRMaj stepVec = new DMatrixRMaj(numPhases, 1);

    double err = 1.0;
    int iter = 1;
    do {
        iter++;
        calcQ();  // Fills dQdbeta and Qmatrix

        // Fill EJML matrices from calcQ results
        for (int k = 0; k < numPhases; k++) {
            gradVec.set(k, 0, dQdbeta[k][0]);
            for (int j = 0; j < numPhases; j++) {
                hessianMat.set(k, j, Qmatrix[k][j]);
            }
        }

        // Solve H·step = grad via raw EJML
        CommonOps_DDRM.solve(hessianMat, gradVec, stepVec);

        double damping = iter / (iter + 3.0);
        for (int k = 0; k < numPhases; k++) {
            double currBeta = betaArr[k] - damping * stepVec.get(k, 0);
            // ... clamp to [βmin, 1-βmin] ...
        }
        system.normalizeBeta();
        calcE();
        setXY();
        system.init(1);
        err = NormOps_DDRM.normF(stepVec);
    } while ((err > 1e-12 && iter < 50) || iter < 3);
    return err;
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
│     - For systems with ions (hasIons()): remove ions        │
│     - Store ionic compositions for later restoration        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  2. STABILITY ANALYSIS                                      │
│     a. stabilityAnalysis() — K trials + pure-comp trials    │
│     b. If enhanced enabled and no phase found:              │
│        stabilityAnalysisEnhanced() — K + LLE trials         │
│     - If tm < -1e-8: add phase, multiPhaseTest=true         │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  3. ADDITIONAL PHASE SEEDING (if stability didn't add)      │
│     - seedAdditionalPhaseFromFeed(): gas phase seeding      │
│     - seedHydrocarbonLiquidFromFeed(): oil phase seeding    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  4. ION RESTORATION (systems with ions)                     │
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
│     - Q-function minimization with damped Newton (EJML)     │
│     - fugCoeffCache avoids repeated virtual dispatch        │
│     - Nested iteration with chemical equilibrium            │
│     - Continue until convergence                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  6b. POST-FLASH STABILITY CHECK (three-phase detection)     │
│     - IF multiPhaseCheck AND 2 ≤ phases < 3:                │
│       run stabilityAnalysisEnhanced() for third phase       │
│     - If new phase found: recursive run()                   │
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
│     - ensureSingleAqueousPhase() for systems with ions      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│  9. POST-REMOVAL STABILITY CHECK                            │
│     - If phase removed: run stabilityAnalysis3()            │
│     - Recursive call to run() if new phase found            │
└─────────────────────────────────────────────────────────────┘
```

### 3.5 Phase Seeding and Restart Strategies

Beyond stability analysis, NeqSim uses heuristic phase seeding and guarded restarts to improve convergence:

#### 3.5.1 Water-rich Hydrocarbon Vapour Appearance

A neutral OIL+AQUEOUS endpoint can still be unstable to a hydrocarbon vapour phase when water dominates the overall
composition. The ordinary stability trials use that overall composition, so dilution can hide the vapour stationary
point even though the water-free hydrocarbon sub-mixture remains between its bubble and dew points.

The fallback first applies a cheap Rachford-Rice bracketing screen to the normalized, ion-free volatile
sub-composition. With Wilson K-values, both

$$\sum_i \hat z_i K_i > 1 + 10^{-3}$$

and

$$\sum_i \frac{\hat z_i}{K_i} > 1 + 10^{-3}$$

must hold. The screen is considered only when multiphase checking is enabled, the converged endpoint has exactly OIL
and AQUEOUS phases but no GAS phase, the system is non-reactive, and at least `1e-6` of the overall feed is represented
by volatile screening components.

Directly appending a gas phase is not used because `SystemInterface.addPhase()` can expose a stale phase slot whose
requested type need not survive initialization. Instead, the algorithm snapshots the converged endpoint, clones it,
resets the clone to a fresh two-phase GAS/liquid estimate, calls `init(0)` and `init(1)`, and runs one nested
`TPmultiflash`. A thread-local guard and a per-operation one-shot flag prevent reciprocal recursion.

The trial replaces the retained endpoint only if it preserves every original phase role, adds GAS, stays within the
configured phase-count limit, and lowers extensive Gibbs energy. An exception, missing phase, non-improving Gibbs
energy, or rejected trial leaves the snapshot in place; beta, phase compositions, K-values, and initialized properties
are restored. Thus the Wilson screen triggers an equilibrium calculation but does not itself decide phase stability.

```java
if (oilAndAqueousWithoutGas && neutral && wilsonSubmixtureBracketsTwoPhases) {
    PhaseSplitSnapshot retained = snapshot(system);
    SystemInterface trial = freshGasLiquidEstimate(system);
    new TPmultiflash(trial, solidCheck).run();

    if (retainsOriginalPhaseRoles(trial)
            && trial.hasPhaseType(PhaseType.GAS)
            && trial.getGibbsEnergy() < retainedGibbs) {
        restorePhaseSplit(snapshot(trial));
    } else {
        restorePhaseSplit(retained);
    }
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

1. **Temporarily removing ions** before stability analysis. Note that `hasIons()` is used instead of `isChemicalSystem()` to catch ions even when no chemical reactions are defined:
   ```java
   boolean hasIons = system.hasIons();
   if (hasIons) {
       ionicZ = new double[system.getPhase(0).getNumberOfComponents()];
       for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
           if (system.getPhase(0).getComponent(i).getIonicCharge() != 0
               || system.getPhase(0).getComponent(i).isIsIon()) {
               ionicZ[i] = system.getPhase(0).getComponent(i).getz();
               // Temporarily set to near-zero in all phases
               for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
                   system.getPhase(phase).getComponent(i).setz(1e-100);
               }
           }
       }
       system.init(1);
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

4. **Refining a non-reactive two-phase GAS+AQUEOUS endpoint** after final phase selection. Ionic K-values are fixed to
   zero because ions are excluded from the gas phase. Molecular K-values remain the ratio of aqueous and gas fugacity
   coefficients. A safeguarded Rachford-Rice solve and successive-substitution update restore both component balance and
   molecular fugacity equality. The candidate is rejected and the original endpoint restored unless beta and phase
   compositions are bounded and normalized, component balance is below `1e-10`, molecular log-fugacity residuals are
   below `1e-8`, and Gibbs energy does not increase. Reactive, one-phase, OIL+AQUEOUS, and genuine three-phase endpoints
   retain their existing paths.

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

## 5. Performance Optimizations

The TP flash implementation includes several performance optimizations to reduce computational cost, particularly for multi-component systems.

### 5.1 Fugacity Coefficient Cache

The `calcE()` method populates a `fugCoeffCache` 2D array with fugacity coefficients for all phases and components. This avoids repeated virtual method dispatch on `getComponent(i).getFugacityCoefficient()` during the inner loops of `calcQ()` and `setXY()`:

```java
// Cache fugacity coefficients to avoid repeated virtual method dispatch
fugCoeffCache = new double[numPhases][numComp];
for (int k = 0; k < numPhases; k++) {
    for (int i = 0; i < numComp; i++) {
        fugCoeffCache[k][i] = system.getPhase(k).getComponent(i).getFugacityCoefficient();
    }
}
```

The cache is used in `setXY()` for phase composition updates and in `calcQ()` for gradient/Hessian computation.

### 5.2 EJML Matrix Operations

All matrix operations use the **EJML** (Efficient Java Matrix Library) with raw `DMatrixRMaj` types instead of the higher-level `SimpleMatrix` wrapper. Key uses:

| Operation | Method | EJML Call |
|-----------|--------|-----------|
| Q-function Newton step | `solveBeta()` | `CommonOps_DDRM.solve()` |
| Step norm computation | `solveBeta()` | `NormOps_DDRM.normF()` |
| Stability Newton step | `stabilityAnalysis()` | `CommonOps_DDRM.solve()` |

Matrices are pre-allocated outside iteration loops and reused to avoid GC pressure.

### 5.3 Wilson K Early Exit

Before running any trial phase iterations, the stability analysis computes Wilson K-values and checks if $\max|\ln K_i| < 0.01$. If all K-values are near unity, the system is near or above the critical point and the mixture is trivially stable — the expensive iterative analysis is skipped entirely.

### 5.4 Two-Stage Trial Strategy

The Wilson K-based trials (Stage 1) run with only 50 iterations and catch the majority of VLE instabilities. The more expensive pure-component trials (Stage 2, up to 200 iterations with potential Newton fallback) only execute when the K-based trials fail to find instability.

---

## 6. State-of-the-Art Comparison and Recommendations

### 6.1 Current Position

NeqSim uses the same core algorithm family as modern equation-of-state simulators: Michelsen tangent-plane-distance stability analysis, Rachford-Rice phase-fraction solution, successive substitution, accelerated substitution, and Newton refinement. The current implementation is therefore not an ad hoc flash calculation; it is a classical industrial thermodynamics workflow with several modern robustness upgrades.

Commercial process simulators do not publish all implementation details, but published state-of-the-art practice generally adds stronger global phase discovery, more formal fallback handling, continuation methods for phase-boundary tracing, and richer convergence diagnostics around the same theoretical foundation.

| Area | Current NeqSim implementation | State-of-the-art expectation |
|------|-------------------------------|------------------------------|
| Two-phase VLE | Wilson K, Rachford-Rice, SSI, DEM, Newton refinement | Same core sequence, typically with extensive fallback telemetry |
| Rachford-Rice | Nielsen 2023 reformulation with Michelsen 2001 option | Robust transformed formulations with bracketing safeguards |
| Stability analysis | Michelsen TPD, Wilson K trials, amplified K retry, pure-component trials | Systematic multi-start TPD minimization with traceable trial outcomes |
| Multiphase flash | Phase addition plus Q-function minimization and cleanup | Global phase-set search, phase deletion, and final global stability certification |
| Critical-region behavior | Amplified K trials and heuristic gates | Continuation, negative flash, and critical-region classifiers |
| Diagnostics | Mainly logging and final phase result | Structured flash report with iteration counts, seeds, TPD minima, and fallback reasons |

### 6.2 Strengths

- The ordinary VLE flash path follows the accepted Michelsen/Mollerup method and is suitable for normal process-simulation use.
- The `Nielsen2023` Rachford-Rice option improves numerical robustness near difficult beta limits.
- The Newton solver uses fugacity derivatives, matrix preallocation, diagonal regularization, and line-search damping, which are all aligned with mature simulator practice.
- Pure-component fallback trials improve LLE/VLLE detection where Wilson K-based trials can report positive TPD even though a liquid-liquid split exists.
- Explicit multiphase hydrocarbon flashes now retry a local lower-temperature seed before accepting an ambiguous single-phase endpoint, and keep the retry only if it gives a lower-Gibbs multiphase result.
- A cheap post-flash K-envelope gate skips that rescue for clearly single-phase hydrocarbon endpoints, preserving ordinary `setMultiPhaseCheck(true)` speed.
- Water-rich ordinary endpoints are not accepted from an aqueous phase label alone. An existing two-phase aqueous
  endpoint is sent to multiphase refinement when `max abs(Delta ln(f_i)) >= 1e-8` or its phase-recombined component
  balance has `max abs(Delta z_i) >= 1e-8`. A feasible candidate normally must lower Gibbs energy; when the reference
  is non-conservative and its Gibbs energy is therefore not comparable, the candidate must instead independently pass
  phase-fraction, composition-normalization, material-balance, fugacity, and distinct-composition checks. The 0.01
  water-feed gate still protects phase-search cost for valid endpoints. A trace-water GAS+OIL endpoint with a minor
  phase no larger than `0.01` and at least tenfold water enrichment in its hydrocarbon liquid uses the existing aqueous
  TPD trial on a clone. The resulting three-phase beta problem must lose exactly one numerical-floor phase or one
  composition-identical duplicate before the reduced two-phase active set is rebuilt and reconverged. It replaces the
  original only when phase fractions, normalization, material balance, fugacity equality, distinct compositions, and
  lower Gibbs energy all pass. Thus the enrichment and beta values gate cost; they do not decide stability. An
  already-active neutral aqueous split with a
  non-finite material residual or one above `1e-8` may use the existing three-step beta correction below that threshold;
  this does not search for or create a phase. A trace-water GAS+OIL endpoint with a confirmed non-finite or at-least
  `1e-8` log-fugacity residual may run the existing guarded multiphase candidate and is replaced only by a strictly
  feasible lower-Gibbs result. That invalid-endpoint retry is restricted to an incipient secondary phase with
  `min(beta) <= 1e-4`, so established valid gas/oil splits avoid even the component residual scan unless they satisfy
  the separate aqueous-stability screen.
- An ordinary, neutral CPA endpoint containing substantial water can select the aqueous minimum before testing a
  competing hydrocarbon-liquid minimum. A one-phase AQUEOUS result therefore receives one cloned multiphase stability
  calculation only when water feed is at least `0.01`, `f_water / p_water_sat >= 0.8`, and at least `0.01` of the
  feed is hydrocarbon with `T_c > T + 80 K`. These are performance screens, not stability evidence. The candidate
  replaces the endpoint only when it contains exactly two distinct phases, has finite bounded and normalized phase
  fractions and compositions, closes component balance and interphase log-fugacity residuals below `1e-8`, and lowers
  Gibbs energy by more than `max(1e-8 J, 1e-12 abs(G))`. Stable polar aqueous endpoints without condensable
  hydrocarbons stay on the ordinary fast path. Chemical, ionic, solid, wax, and explicit-multiphase calculations are
  excluded.
- The same strict gate is reciprocal for a water-rich multiphase GAS+AQUEOUS endpoint. If its gas phase has a
  lower-Gibbs alternate cubic root, one ordinary candidate is evaluated from the unchanged feed. It replaces the
  multiphase endpoint only when it contains exactly one AQUEOUS and one cubic GAS/OIL/LIQUID phase, is independently
  feasible, and lowers extensive Gibbs energy beyond `max(1e-6 J, 1e-8 abs(G))`. Allowing the cubic phase identity to
  change prevents a stable liquid root from being rejected merely because the reference endpoint called the same
  phase GAS. The accepted ordinary calculation is repeated on the live system to rebuild the cubic/aqueous storage
  mapping; a recursion guard prevents fallback ping-pong. Genuine three-phase endpoints, chemical/electrolyte systems,
  solid/wax calculations, and dry systems do not run the additional flash.
- For a neutral non-CPA water-rich asymmetric feed, ordinary and explicit-multiphase calculations retain the cold
  pre-iteration state instead of relying on a clone of a final endpoint. A post-flash clone can preserve collapsed
  phase-storage and cubic-root history even after beta and compositions are reset, causing it to revisit a rejected
  stationary point while the unchanged cold feed reaches a feasible OIL+AQUEOUS equilibrium. If the explicit
  multiphase calculation collapses to one phase, water-bearing recovery consumes the cold state for one ordinary
  TP flash; the retained K-value clone fallback remains available for other eligible collapses. The cold seed is
  allocated only when water feed is at least `0.01` and the existing critical-temperature/composition screen identifies
  an asymmetric neutral mixture. Dry, CPA, chemical, ionic, solid, wax, and non-asymmetric water-bearing flashes remain
  on their existing initialization paths. Seed provenance does not relax acceptance: exactly two distinct phases,
  bounded and normalized beta/compositions, material balance and log-fugacity residuals below `1e-8`, and lower Gibbs
  energy are still required.
- Multiphase stability cleanup merges two same-type liquid phases when their maximum component-composition difference
  is below `1e-6` and they are either a supported CPA duplicate or the two hydrocarbon roots beside an aqueous phase in
  a neutral cubic-EOS three-phase trial. Their phase fractions are added before the redundant phase is removed, so the
  resulting two-phase endpoint retains material balance. Compositionally distinct gas/oil/aqueous equilibria remain
  three phase.
- A water-rich ordinary two-phase endpoint that fails the strict equilibrium gate can retain useful phase-composition
  information even when its cold multiphase candidate collapses to one phase. After that cold candidate is rejected,
  the existing split seeds one fully initialized `TPmultiflash` calculation. A water-rich multiphase endpoint that
  collapsed to one hydrocarbon phase likewise tries the cold ordinary path, which can supply the same seed. That
  collapsed-multiphase route accepts only a candidate that restores an AQUEOUS phase, so it cannot turn a normal
  water-bearing liquid stream into a gas/oil split; ordinary gas appearance remains the responsibility of the existing
  stability path. Either route is accepted only as an exactly-two-phase, bounded, normalized, material-balanced,
  fugacity-balanced, distinct, lower-Gibbs result. Trying the cold candidate first preserves its gas/oil cubic-root
  classification when it already reaches the feasible equilibrium. Three-phase, unbalanced, higher-Gibbs, chemical,
  ionic, solid, and wax candidates remain unchanged. This is a correctness fallback for rejected endpoints; it adds
  solver work on the affected path and is not a performance optimization.
- A one-phase ordinary CPA endpoint containing trace water uses a cheap water-fugacity screen before the same aqueous
  TPD trial. The screen compares water fugacity in the current phase with pure-water vapor pressure and triggers at a
  ratio of `0.8`, conservatively below nominal saturation. This avoids a TPD calculation for clearly undersaturated
  states. The ratio is not evidence that a second phase is stable: only the converged TPD candidate may add a phase,
  and it must independently pass phase-fraction, normalization, material-balance, fugacity, distinct-composition, and
  lower-Gibbs checks. For an incipient CPA aqueous split, the Gibbs comparison uses
  `max(1e-8 J, 1e-12 abs(G))` so a valid small phase is not hidden by the broader endpoint-rescue tolerance.
- When the tangent-plane stability path has already accepted a homogeneous state, an unnormalized aqueous trial seed
  cannot replace it. The guard is deliberately structural: each active phase composition must be finite, bounded in
  `[0, 1]`, and normalized within `1e-8`. It does not impose a universal material-balance or fugacity threshold on
  normalized endpoints because specialized fluid and solid-phase models retain their existing convergence and
  refinement paths.
- A neutral, balanced two-phase aqueous state is preserved before multiphase phase-appearance trials. If a trial third
  phase is subsequently removed but its phase fractions leave the same surviving two-phase topology outside `1e-8`
  component material-balance or fugacity tolerances, the pre-trial state is restored. A GAS↔OIL root transition is
  retained for the later endpoint refinement instead of being overwritten by the saved topology. The recovery does not
  run another flash and is not considered for chemical, ionic, solid, wax, genuine three-phase, or already feasible
  endpoints.
- Ordinary neutral aqueous splits now compare both cubic roots of the non-aqueous phase at the converged composition. An alternate root is retained only when it lowers Gibbs energy and already satisfies `max abs(Delta ln(f_i)) < 1e-8`; no extra TPflash or multiphase stability calculation is run.
- Balanced neutral gas/oil endpoints whose terminal fugacity residual is between `1e-8` and `1e-5` receive at most
  eight additional SSI updates. This targets stale, near-converged K-values after root selection without adding work to
  already converged endpoints or attempting to rescue grossly invalid states. The original endpoint is restored unless
  phase identity, normalization, material balance, fugacity equality, and Gibbs energy all pass.
- Near a hydrocarbon critical boundary, the ordinary flash can converge the correct light and heavy compositions but
  retain an inconsistent cubic volume root. Inverted mean-molar-mass order still triggers the paired vapor/light and
  liquid/heavy root comparison. A normally ordered endpoint triggers only when its current
  `max abs(Delta ln(f_i)) >= 1e-8`; both cubic root seeds are then reinitialized on one cloned phase at a time while the
  other phase remains fixed. The lowest-Gibbs candidate is retained only when it restores
  `max abs(Delta ln(f_i)) < 1e-8`, and its root seed is stored separately from the unchanged public GAS/OIL phase label
  so later property initialization does not recreate the stale root. Phase fractions, compositions, and material
  balance remain unchanged. This is a post-convergence root selection, not an additional stability or TPflash solve.
- Converged supplementary near-critical stability trials accept reduced TPD below `-1e-6`, matching the residual and
  step convergence resolution of that SSI solve. This replaces the former `-1e-4` cutoff while rejecting negative
  values below the solver's numerical resolution. The standard stability decision retains its `-1e-8` limit, and the
  supplementary path still requires a non-trivial trial composition.
- Enhanced stability checks are gated to polar, associating, electrolyte, sour, or explicitly requested multiphase systems, limiting unnecessary hydrocarbon phase-map artifacts.

### 6.3 Recommended Improvements

| Priority | Recommendation | Status | Rationale |
|----------|----------------|--------|-----------|
| 1 | Do not treat a failed stability analysis as stable without a conservative fallback | Implemented for failed stability gates; partial TPD failures now keep the supplementary fallback path when usable TPD values exist | A failed TPD solve can hide a real phase split and create missing or isolated phase-map regions |
| 2 | Add structured flash diagnostics | Partially implemented with stability outcome, failure message, and recorded TPD values; a fuller object should add per-trial telemetry | Expose beta, SSI iterations, Newton iterations, trial seeds, TPD minima, fallback path, phase additions, and phase removals for tests and debugging |
| 3 | Add a rigorous phase-map mode | Not implemented | Use continuation or negative-flash style tracing for property maps where topological smoothness matters more than single-call speed |
| 4 | Add final global stability certification after `TPmultiflash` cleanup | Partially implemented for ambiguous hydrocarbon single-phase endpoints through a K-envelope gate, local lower-temperature seed, and lower-Gibbs acceptance check; full global certification is still not implemented | Re-run TPD against the final phase set to verify no additional stable phase remains |
| 5 | Keep pure-component LLE trials in the multiphase path | Implemented in the current multiphase stability path | Wilson-positive TPD trials do not rule out hydrocarbon LLE or polarity-driven liquid splits |
| 6 | Separate fast process flash from authoritative phase-map flash | Not implemented | Process simulation and phase-boundary generation have different robustness and performance needs |
| 7 | Update documentation whenever solver thresholds change | Implemented for the current TPflash thresholds | Algorithm descriptions should match the active values in `TPflash.java` and `TPmultiflash.java` |

### 6.4 Regression and Benchmark Coverage

Recommended regression coverage should include both numerical convergence and phase-map topology:

| Test category | Recommended cases | Acceptance signal |
|---------------|-------------------|-------------------|
| Ordinary TPflash speed | Natural-gas SRK/PR mixtures with and without `setMultiPhaseCheck(true)` | Median runtime does not regress outside a defined tolerance |
| Phase-map topology | Methane/n-heptane PR grid with hydrocarbon LLE region | No failed flash points and no isolated one-cell phase regions |
| Known phase-map spot cells | Methane/n-heptane PR cells at 78.5/194, 81/194, 186/424, and 191/418 bara/K | Each cold-start flash converges to gas-oil instead of an isolated one-phase island |
| Critical-region robustness | Rich gas near cricondenbar and cricondentherm | No false single-phase result when TPD finds instability |
| Polar/VLLE systems | Water, CO2, H2S, methanol, glycols, and CPA/electrolyte examples | Correct aqueous/oil/gas phase count and stable final split |
| CPA aqueous appearance | Ordinary and explicit-multiphase flashes above and below the water-fugacity screen, including aqueous fractions near `1e-6` | Same stable phase set, beta, phase compositions, properties, material balance, fugacity equality, and deterministic repeatability |
| Multiphase cleanup | Cases with small beta phases and duplicate aqueous candidates | Removed phases preserve total composition and final mass balance |
| Documentation drift | Algorithm doc parameter table versus source constants | Stale thresholds are detected during review |

#### 6.4.1 Hydrogen-Rich Cubic-EOS Qualification

The hydrogen-rich binary regression uses five experimental vapor-liquid tie lines reported by Sagara, Arai, and
Saito [10]. Pressure is absolute and is converted from atmospheres to bar with `1 atm = 1.01325 bar`. No binary
interaction parameters were fitted: both SRK and PR use the classic mixing rule and the database parameters available
to a normal NeqSim calculation.

| System | Temperature (K) | Pressure (atm) | Experimental x(H2) | Experimental y(H2) | SRK x/y(H2) | PR x/y(H2) |
|--------|----------------:|---------------:|-------------------:|-------------------:|-------------:|------------:|
| H2 + methane | 123.15 | 20.0 | 0.01920 | 0.818 | 0.019225 / 0.849073 | 0.022707 / 0.842951 |
| H2 + methane | 143.05 | 40.2 | 0.04770 | 0.721 | 0.049051 / 0.725721 | 0.055217 / 0.717073 |
| H2 + methane | 173.65 | 59.9 | 0.07090 | 0.362 | 0.090427 / 0.349538 | 0.096174 / 0.339973 |
| H2 + ethane | 148.15 | 20.0 | 0.00618 | 0.986 | 0.006841 / 0.994840 | 0.008724 / 0.994161 |
| H2 + ethane | 173.15 | 40.0 | 0.01680 | 0.977 | 0.019465 / 0.981041 | 0.023502 / 0.979088 |

The experimental authors report temperature and pressure accuracies of `0.1 K` and `0.1 atm`, respectively, and
composition uncertainty normally within `0.01` mole fraction. The regression's `0.04` absolute composition envelope
is deliberately a separate model-qualification tolerance. The observed maximum absolute deviation is `0.031073` for
SRK and `0.025274` for PR; these values do not imply parameter fitting or universal hydrogen-mixture accuracy.

For each experimental tie line, the regression verifies the following:

- the overall composition halfway between the liquid and vapor endpoints returns GAS+OIL equilibrium;
- a liquid-side composition at half the experimental liquid endpoint and a vapor-side composition `0.05` above the
  experimental vapor endpoint (capped at `0.999`) return the adjacent one-phase states;
- ordinary and explicit-multiphase calculations agree within `1e-10` for beta and phase composition, `1e-8` for
  compressibility factor, and a relative `1e-8` extensive-Gibbs tolerance;
- phase fractions and compositions are finite, bounded, and normalized within `1e-12`, component material balance
  closes below `1e-10`, and two-phase log-fugacity residuals remain below `1e-8`; and
- poor phase-fraction initialization, an exact repeated flash, and a changed temperature-pressure-composition state
  recover the same equilibrium as a fresh calculation.

This is an evidence-level-1 literature qualification of phase topology and numerical consistency. It does not claim
that classic cubic EOS models reproduce every hydrogen-rich VLE dataset within experimental uncertainty.

The same reference matrix also anchors a low-pressure phase-boundary safeguard. At 143.05 K and 40.2 atm, an overall
hydrogen fraction `1e-4` inside the calculated vapor boundary previously collapsed to one phase through ordinary
`TPflash`, while the explicit-multiphase path retained the equilibrium liquid with beta `1.4778e-4` for SRK and
`1.5109e-4` for PR. The ordinary stability path had rejected every amplified-K supplementary trial below 50 bar before
applying its Wilson-sum or tangent-plane screens.

For feeds containing more than `0.01` overall hydrogen, the pressure prefilter now permits that existing supplementary
trial below 50 bar. All later Wilson-sum, K-value-spread, convergence, non-trivial-composition, and non-physical-state
rollback gates remain unchanged. The regression recomputes each EOS boundary for the four literature points below
50 bar, then evaluates overall compositions `1e-4` to either side of both calculated endpoints. Inside states retain
GAS+OIL with a small phase of order `1e-4`; outside states remain single-phase. Ordinary and explicit-multiphase paths
must satisfy the same normalization, material-balance, fugacity, property, repeat, poor-initialization, phase
disappearance, and reappearance gates listed above. Non-hydrogen feeds, hydrogen at or below one mole percent, and the
established pressure-at-or-above-50-bar path are unchanged.

The remaining `59.9 atm` hydrogen+methane point also qualifies the established high-pressure path at both calculated
phase boundaries. Before the high-pressure boundary refinement, the ordinary SRK result `1e-4` inside the calculated
liquid boundary retained the heavy-phase vapor-like cubic root (`Z = 0.605203`) instead of the lower liquid root
(`Z = 0.256018`). The returned phase fractions and compositions were normalized and material-balanced, but the maximum
log-fugacity residual was `1.25942` and total Gibbs energy was `3906.28697 J`, compared with `3734.24830 J` for the
lower-root equilibrium.

A bounded reciprocal ordinary/multiphase refinement now covers only neutral two-phase feeds at or above `50 bar`
containing
more than `0.01` overall hydrogen, at least one hydrocarbon, no other active non-inert component, and an incipient phase
below `0.01`. It first retains the existing beta refinement and complete rollback behavior. An invalid endpoint may be
replaced only by the reciprocal path when phase topology is preserved, all strict normalization, material-balance, and
fugacity checks pass, and Gibbs energy is not increased. The corrected SRK residual is `1.63e-12`; PR remains within
`1.00e-12`. The five-point SRK/PR matrix now checks compositions `1e-4` inside and outside both model boundaries, and
the `40.2 atm` and `59.9 atm` points both cover poor initialization, deterministic repeat, phase disappearance, and
reappearance.

This is a correctness fallback, not a performance optimization. A constrained fresh-system probe (20 warmups and five
blocks of 30 flashes) measured the corrected SRK boundary at about `8.5 ms/flash`, versus `4.3-5.4 ms/flash` for the
invalid higher-root baseline. The PR boundary and adjacent retained one-phase controls were within run-to-run noise.
Common flashes return before the hydrogen/high-pressure/incipient-phase screen.

#### 6.4.2 Neutral SRK-CPA MEG Three-Phase Qualification

The neutral associating regression uses `SystemSrkCPAstatoil` with mixing rule 10 at
298.15 K and 60.0 bara. The synthetic overall inventory is 1 mol nitrogen, 85 mol
methane, 5 mol ethane, 3 mol propane, 1 mol n-hexane, 1 mol n-decane, 2 mol MEG, and
5 mol water. It is a numerical qualification of the existing CPA flash path, not an
independent experimental validation of phase fractions or CPA parameters.

The expected topology is GAS+OIL+AQUEOUS. The focused test now requires every phase
fraction and composition to be finite, bounded, and normalized within `1e-12`,
component material balance below `1e-10`, and maximum cross-phase log-fugacity
residual below `1e-8`. It compares the complete equilibrium by phase type, including
beta, every component composition, compressibility factor, density, Gibbs energy, and
enthalpy.

The same matrix covers an exact repeated flash, a deliberately poor initial
phase-fraction split, a changed state at 300.15 K and 59.5 bara, and a return to the
original state. Warm and changed-state results must reproduce a fresh cold flash,
which guards against stale gas-to-oil K-values hiding the water/MEG partitioning into
the third phase. The thread-local warm-start policy must also be restored after each
call.

This tranche changes tests and algorithm documentation only; production flash code,
public APIs, model parameters, tolerances, and runtime paths are unchanged. Therefore
no production performance change is possible and no speedup is claimed. The added
focused test performs seven complete neutral CPA flashes so hosted CI records the
qualification cost without placing a timing threshold on shared runners. Electrolyte
phase-boundary calculation, ion confinement, and Pitzer parameterization remain
outside this neutral-fluid matrix.

#### 6.4.3 PR Sour-Gas Three-Phase Qualification

The sour-gas regression uses the classic PR mixing rule with a synthetic overall
composition of 49.88 mol methane, 9.87 mol CO2, and 40.22 mol H2S, normalized from a
99.97 mol total. It is a solver qualification of the existing enhanced multiphase
path, not an experimental validation of the predicted phase boundary or phase
fractions. Temperature is in kelvin and pressure is absolute bar.

Fresh flashes at 202 K/47 bara, 205 K/50 bara, and 208 K/53 bara must produce one
GAS phase and two compositionally distinct OIL phases. Each endpoint requires finite,
bounded phase fractions and compositions, phase and beta normalization within
`1e-10`, component material balance within `1e-10`, and maximum cross-phase
log-fugacity residual below `1e-8`. The enhanced three-phase endpoint must also have
lower Gibbs energy than the ordinary two-phase endpoint at the same state. This last
comparison documents why `setEnhancedMultiPhaseCheck(true)` is required for this
qualification instead of silently treating the ordinary converged split as globally
stable.

At the interior 205 K/50 bara point, the complete three-phase endpoint must be
recovered from an initial beta of `1e-12`. Moving the same system to 49 bara must
remove the second liquid while preserving closure; returning to 50 bara must restore
the cold-flash beta, compressibility factors, compositions, and Gibbs energy. An
immediate repeated flash must reproduce that result within `1e-10`, guarding both
phase appearance/disappearance and stale-state behavior.

This deterministic matrix replaces a 25,551-state slow scan that swallowed every
flash exception and ended with the tautology `threePhaseCount >= 0`. The focused
six-test sour-gas class completed in 0.694-0.888 s across local runs, including the new
qualification, while adding enforceable thermodynamic acceptance gates. Production code, public
APIs, model parameters, and runtime behavior are unchanged, so no production speedup
is claimed. The scope is limited to PR; SRK and experimental sour-gas tie-line
validation remain separate model-qualification work.

#### 6.4.4 PR Methane/Heptane Boundary Qualification

The low-temperature binary regression uses 70 mol methane and 30 mol n-heptane with
the classic Peng-Robinson mixing rule and a methane/heptane binary interaction
parameter of 0.05. The frozen state is 155.1 K and 84.4 bara, with nearby pressure
checks at 84.3 and 84.5 bara. This is a deterministic solver qualification of the
existing model, not experimental validation of the predicted phase boundary or
phase fractions.

Ordinary, multiphase, and enhanced-multiphase flashes must return the same closed
methane-rich/n-heptane-rich two-phase equilibrium at the frozen state. The phases
are matched by n-heptane mole fraction rather than a phase-role enum, because that
metadata is not part of the thermodynamic equilibrium contract. Every endpoint
requires compositionally distinct phases, finite and bounded phase fractions and
compositions, phase and beta normalization within `1e-12`, component material
balance below `1e-10`, maximum interphase log-fugacity residual below `1e-8`,
positive compressibility factors, and finite Gibbs energy. The nearby states apply
the same closure and cross-algorithm gates.

The enhanced path must also recover the reference endpoint from beta `1e-12`.
Changing a reused state to 84.5 bara, returning it to 84.4 bara, and immediately
repeating the flash must match fresh-state phase fractions, compositions,
compressibility factors, topology, and Gibbs energy. These checks guard poor
initialization, stale state, and nearby-state continuity.

This qualification replaces a JVM `assert` that was disabled in normal JUnit
execution and removes an unannotated 1,401-state logging scan. Production code,
public APIs, thermodynamic parameters, defaults, and runtime behavior are unchanged.
The bounded regression adds no production performance claim.

#### 6.4.5 Water-bearing PR multiphase endpoint qualification

The legacy water-bearing Peng-Robinson regression contains 1 mol nitrogen,
90 mol methane, 2 mol ethane, 1 mol each of propane, iso-/normal butane,
iso-/normal pentane, n-hexane and nC10, plus 10 mol water. It uses the classic
mixing rule without fitted binary interaction parameters. The bounded
qualification covers 298.15 K near 10 bara and 288.15 K near 500 bara. These
states are synthetic solver regressions; they are not experimental validation
of water solubility, phase fractions, or the classic-PR parameterization.

At 10 bara, the multiphase endpoint must retain a closed split, remain within
`5e-5` relative of the frozen enthalpy reference, and have Gibbs energy no higher
than the ordinary endpoint. The relative reference tolerance covers the observed
cross-platform property difference while remaining one hundred times tighter than
the legacy 0.5 percent enthalpy check. At 500 bara, ordinary and multiphase flashes
must agree. Nearby checks use 9.9/10.0/10.1 bara and 499/500/501 bara. Both regimes
require recovery from beta `1e-12`, changed-pressure and return-to-state agreement,
and deterministic repeat at the low-pressure reference. Equivalent endpoints also
reproduce enthalpy using the same tight state-comparison tolerance applied to Gibbs
energy.

Every accepted state requires finite, bounded and normalized beta and
compositions, beta and phase normalization within `1e-12`, component material
balance below `1e-10`, maximum interphase log-fugacity residual below `1e-8`
for components present in both phases, positive compressibility factors, and
finite Gibbs energy. Phases are paired by water mole fraction, so phase-role
enum metadata is outside this thermodynamic contract.

This qualification activates two previously unexecuted JUnit methods and adds a
small fixed flash matrix. Production code, APIs, parameters, defaults and
runtime behavior are unchanged. No production performance improvement is
claimed.

#### 6.4.6 High-temperature rich-fluid cubic-EOS qualification

The high-temperature rich-fluid regression uses a synthetic 24-component normal-alkane
distribution from nitrogen through nC19. The normalized composition contains 30 mol%
methane, 12 mol% n-heptane, 15 mol% n-octane, successively smaller heavy-normal-alkane
fractions, 0.163 mol% nitrogen, 0.323 mol% CO2, and zero active H2S. Both SRK and PR use
the classic mixing rule without fitted binary interaction parameters. Temperature is in
degrees Celsius and pressure is absolute bar.

At 268 °C and 88 bara, SRK returns a closed two-phase equilibrium while PR returns a
closed single-phase equilibrium. Ordinary and explicit-multiphase flashes must agree on
that EOS-specific topology and state. Multiphase comparisons match phases by nC19 mole
fraction rather than phase-role metadata and require phase fraction, composition, and
compressibility agreement within `1e-8`. Every accepted endpoint requires finite and
bounded phase fractions and compositions, phase and beta normalization within `1e-12`,
component material balance below `1e-10`, maximum interphase log-fugacity residual
below `1e-8`, positive compressibility factors, and finite Gibbs energy and enthalpy.

The same contracts apply at 267 °C and 269 °C. The 268 °C equilibrium must also be
recovered from beta `1e-12`, after changing a reused state to 269 °C and returning it
to 268 °C, and on an immediate repeated flash. These checks cover path agreement, poor
initialization, nearby-state continuity, stale-state recovery, and deterministic repeat
for both cubic equations of state.

This bounded JUnit matrix replaces a nine-stage logger-only `main` diagnostic that
performed no assertions and was not run by the test suite. The two tests execute sixteen
complete flashes, giving hosted CI a fixed-work performance record without imposing a
timing threshold on shared runners. Production solver code, public APIs, parameters,
defaults, and runtime behavior are unchanged, so no production speedup is claimed. The
synthetic matrix is a numerical solver qualification, not experimental validation of the
predicted phase fractions or phase boundary.

### 6.4.7 Rich-gas cricondenbar boundary qualification

The established synthetic rich-gas regression contains nitrogen, carbon dioxide, methane,
ethane, propane, i-butane, n-butane, i-pentane, n-pentane, and n-hexane in the respective
feed amounts 3.43, 0.34, 62.51, 15.65, 13.22, 1.61, 2.48, 0.35, 0.29, and 0.12. The
qualification uses SRK and PR with classic mixing rule 2. Temperatures are specified in
degrees Celsius and pressures as absolute bar. The primary 100 bara matrix covers -8, 0,
10, and 30 degrees Celsius; a nearby 50 bara point at 0 degrees Celsius exercises the
pressure direction. This is synthetic regression provenance rather than experimental VLE
data.

The legacy SRK regression executed the -8 degrees Celsius endpoint without an assertion.
The replacement requires ordinary and explicit-multiphase paths to agree at every matrix
point, while independently enforcing phase and beta normalization within `1e-12`,
component material-balance closure below `1e-10`, and maximum interphase log-fugacity
residual below `1e-8`. All beta values and compositions must remain finite and bounded,
compressibility factors must be positive, and Gibbs energy and enthalpy must be finite.
The established SRK topology anchors remain two phases at 0 and 10 degrees Celsius and
one phase at 30 degrees Celsius; PR is qualified by equilibrium closure and path agreement
without inheriting SRK-specific topology.

Additional regressions start the -8 and 0 degrees Celsius states from beta values within
`1e-12` of a bound, cross the disappearance boundary at 30 degrees Celsius, return through
-8 degrees Celsius, reappear at 0 degrees Celsius, and repeat the settled state. Phase
matching is based on n-hexane content so phase-order changes cannot hide a mismatch. This
covers poor initialization, phase appearance and disappearance, changed-state reuse,
return-state continuity, stale-state recovery, and deterministic repeats across both cubic
equations of state.

The two JUnit tests execute 41 complete flashes: 22 SRK and 19 PR. That fixed workload gives
hosted CI a reproducible performance record without a wall-clock threshold on shared
runners. Production solver code, public APIs, model parameters, units, defaults, and runtime
behavior are unchanged, so no speedup is claimed. The matrix qualifies numerical solver
contracts around the synthetic phase boundary; it does not validate the predicted boundary
against experimental measurements.


### 6.4.8 Water-bearing SRK-CPA well-fluid qualification

The well-fluid regression uses `SystemSrkCPAstatoil` with mixing rule 10 and the
existing public 28-component inventories from `TPFlashTestWellFluid`. The
characterization contains methane through n-pentane, water, eight nonzero Frigg
heavy fractions, and eight zero-inventory West-Central heavy-fraction identities.
The base endpoint is 303.15 K and 65 bara. The water-rich endpoint replaces the
component amounts with the established normalized molar composition and runs at
339.04 K and 1.5 bara. Temperature is in kelvin, pressure is absolute bar, density
is in kg/m3, and component inputs are amounts or normalized mole fractions.

The discoverable qualification requires finite bounded phase fractions and
compositions, phase and beta normalization within `1e-12`, component material
balance below `1e-10`, maximum comparable interphase log-fugacity residual below
`1e-8`, positive compressibility and density, and finite Gibbs energy and
enthalpy. The water-rich endpoint retains its established phase-zero density
reference of `1.432253736300898 kg/m3` within `1e-5 kg/m3`. Phase comparisons
are ordered by water mole fraction and density so phase-array ordering cannot hide
a lifecycle mismatch.

Both endpoints must recover from beta values within `1e-12` of a bound, remain
closed at tightly nearby pressure or temperature, match a fresh calculation after
a changed state, return to the reference state, and repeat deterministically.
The two JUnit tests execute a fixed matrix of complete SRK-CPA flashes. This
replaces a class outside Surefire's default discovery pattern and a no-assertion
method with enforceable thermodynamic contracts. The fixed workload is performance
evidence only; production solver code, public APIs, parameters, defaults, and
runtime behavior are unchanged, so no speedup is claimed. The public repository
composition is numerical regression provenance, not independent experimental
validation of the well-fluid characterization or CPA parameters.

### 6.4.9 High-temperature rich-fluid gas-fraction anchor

The established 24-component rich-fluid regression uses classic-mixing SRK and PR at
88 bara and 267--269 degrees Celsius. Temperature is specified in degrees Celsius and
pressure as absolute bar. The public synthetic composition spans nitrogen, carbon dioxide,
hydrogen sulfide, methane through n-nonane, and nC10 through nC19.

The discoverable qualification already requires ordinary and explicit-multiphase path
agreement, phase and beta normalization within `1e-12`, component material balance below
`1e-10`, maximum comparable interphase log-fugacity residual below `1e-8`, bounded
finite compositions and phase fractions, positive compressibility, finite Gibbs energy and
enthalpy, recovery from beta values within `1e-12` of a bound, nearby-state continuity,
changed/returned-state agreement, and deterministic repeat.

A historical SRK regression additionally anchors the 268 degrees Celsius, 88 bara gas mole
fraction at `0.00698 +/- 0.001`. That assertion previously remained in
`TPFlashTestHighTemp`, whose filename is outside Surefire's default discovery pattern.
The anchor now executes inside `TPFlashHighTempTest`, and the duplicate undiscovered class
is removed. The tolerance preserves the established numerical contract; it is not an
experimental uncertainty or validation of the predicted high-temperature phase split.

The bounded test matrix is performance evidence only. Production solver code, public APIs,
model parameters, defaults, and runtime behavior are unchanged, so no speedup is claimed.

### 6.4.10 Water-rich cubic-EOS vapour-appearance qualification

The synthetic qualification uses classic-mixing `SystemSrkEos` and `SystemPrEos` at 30.8 °C and 45.62 bara. The
water-free hydrocarbon inventory is 0.55 methane, 0.08 ethane, 0.05 propane, 0.03 n-butane, 0.02 n-pentane, 0.02
n-hexane, 0.10 n-heptane, and 0.15 n-octane on a mole-fraction basis. Water then dilutes that normalized inventory.
This is deterministic solver evidence, not an experimental validation of the predicted phase fractions or boundary.

The historical SRK water-cut range from 0.50 through 0.95 must retain GAS+OIL+AQUEOUS equilibrium. SRK and PR are both
qualified at a water mole fraction of 0.83 and 44.62, 45.62, and 46.62 bara. Every active phase and beta must be finite,
bounded, and normalized within `1e-12`; maximum component material-balance residual must be below `1e-10`; maximum
comparable interphase log-fugacity residual must be below `1e-8`; compressibility must be positive; and Gibbs energy
and enthalpy must be finite.

At the reference state, both equations of state must recover the fresh public `TPflash` endpoint from beta values
within `1e-12` of a bound and from an ordinary two-phase endpoint passed directly to `TPmultiflash`. Reused systems
must agree with fresh calculations after a nearby pressure change, after return to the reference pressure, and on an
immediate deterministic repeat. A water-free GAS+OIL control verifies that the aqueous-only guard does not broaden the
restart to dry flashes.

The focused class executes 34 complete flashes. That bounded count is the performance evidence for this test-only
qualification; no wall-clock threshold or production speedup is claimed. Production solver code, public APIs, model
parameters/defaults, electrolyte and reactive paths, solids/wax, saturation search, Column Solver, Process Performance,
and Huldra are outside this tranche.

### 6.4.11 Water-rich SRK-CPA three-phase lifecycle qualification

The associating-fluid qualification uses `SystemSrkCPAstatoil` with mixing rule 10 at 313.15 K and 20.0 bara. The dry
inventory contains 25 mol methane, 5 mol ethane, 5 mol propane, 4 mol n-butane, 4 mol n-pentane, 6 mol n-hexane, 8 mol
n-heptane, 8 mol n-octane, 15 mol of a C10 TBP fraction with molar mass 0.142 kg/mol and density 0.78 kg/L, and 20 mol
of a C20 TBP fraction with molar mass 0.282 kg/mol and density 0.88 kg/L. Water is added from the requested mass
fraction $w$ as $n_{water}=(11.29556/0.01801528)w/(1-w)$ mol. The constants use kilograms and kilograms per mole, so
the resulting amount is in moles.

Water mass fractions from 0.40 through 0.80 must retain GAS+OIL+AQUEOUS equilibrium. The established gas-beta anchors
decrease continuously from `0.0441902453511` at 0.40 water mass fraction to `0.00844359351489` at 0.80. Nearby states
at 312.65 K/19.5 bara, 313.15 K/20.0 bara, and 313.65 K/20.5 bara must recover the same topology from an initial beta
of `1e-10`.

Every qualified state requires finite bounded phase fractions and compositions, beta and phase normalization within
`1e-12`, component material balance below `1e-10`, and maximum comparable interphase log-fugacity residual below
`1e-8`. Each phase must also have positive finite compressibility and density, while total Gibbs energy and enthalpy
must remain finite. Phases are matched by `PhaseType`, so phase-array reordering cannot hide a lifecycle mismatch.

A reused reference system is moved from 20.0 to 20.5 bara, compared with a fresh calculation, returned to 20.0 bara,
and flashed again. The changed state, returned state, and immediate repeat must reproduce the corresponding fresh or
retained equilibria within the frozen per-property tolerances. The existing `ThreePhaseSeparator` sweep remains a
process-composability check without changing process-equipment production code.

The focused class performs 22 explicit complete TP flashes plus four bounded separator cases. This fixed workload is
performance evidence only; no wall-clock threshold or production speedup is claimed. The composition is synthetic
numerical evidence, not experimental validation of CPA parameters or the predicted phase fractions. Production solver
code, public APIs, association parameters, mixing-rule defaults, electrolyte/reaction models, saturation search,
Column Solver, generic Process Performance, proprietary data, and Huldra are outside this tranche.

### 6.5 Hybrid EOS-GE ionic-capacity safeguard

In a fixed-role EOS-gas/GE-aqueous calculation, ions are excluded from every non-aqueous role.
Consequently the aqueous phase fraction must remain strictly greater than the sum of the overall
ionic mole fractions: otherwise the required aqueous ionic mole fractions sum to one or more and no
normalized neutral solvent composition exists.

The unconstrained beta Newton correction can cross that physical boundary during an intermediate
iteration even when the final gas-aqueous equilibrium is feasible. The hybrid solver now projects
only such infeasible iterates back above
`sum(zIon) + 100 * phaseFractionMinimumLimit`. The projection also limits a single correction to
between one half and twice the synchronized pre-Newton aqueous phase fraction. Limiting beta
reduction bounds the corresponding ionic-concentration increase, while limiting beta growth
prevents a gas-forming neutral component from displacing nearly all aqueous solvent in one Newton
step. Both bounds are evaluated before the trial composition is used. Ionic hybrid flashes also
cap the common beta Newton scale at 0.1, damping every phase correction along the same simplex
direction; non-ionic hybrid and ordinary multiphase flashes retain the general iteration-dependent
scale. A valid aqueous neutral-composition proposal remains unchanged. If its reinitialized
material-component fugacity coefficient is non-finite or non-positive, the ionic hybrid solver
backtracks the neutral composition halfway toward the last finite normalized composition until all
material neutral coefficients are finite. This retains the exact ionic inventory and normalization
without perturbing non-ionic or already-valid paths. Together these guards prevent non-finite
coefficients and clipping-driven oscillation while allowing subsequent corrections to approach
equilibrium.
The required fraction is removed proportionally from the adjustable part of the other active roles,
their minimum fractions are preserved, and the phase-split denominator is rebuilt before
compositions are evaluated. Feasible iterates, public tolerances, dataset parameters, and final
acceptance checks are unchanged. If the ionic inventory cannot fit while retaining the active roles
at their numerical minima, the solver still fails with explicit capacity diagnostics. Finalization
also treats non-finite mole fractions or fugacity coefficients in a material phase as a failed
equilibrium gate and records the offending component rather than silently excluding it.

The regression uses the public-domain PHREEQC CO2-Na2SO4 subset documented in
`docs/thermo/pitzer_parameter_provenance.md`. It forces a formerly invalid intermediate beta below
the ionic inventory, then checks projection, ion confinement, normalization, and exact ionic
material balance. A complete 373.15 K gas-aqueous flash at 150 bar is repeated and changed to
140 bar; both states must retain gas and aqueous roles, bounded normalized compositions, component
material balance below `1e-7`, and molecular log-fugacity residual below `1e-5`.

Pitzer neutral activities and the CO2 Henry correlation both use the molality standard state. The
aqueous fugacity coefficient consequently converts `m_i gamma_i H_i` to the common
`x_i phi_i P` representation through `m_i/x_i`; omitting that factor mixes standard states and can
drive the flash toward an unphysical, CO2-rich aqueous iterate.

This is a feasibility safeguard, not a performance optimization. The common feasible hybrid path
adds one allocation-free component scan to each composition update and performs no extra property
initialization. Only a projected iterate rebuilds the already allocated phase-split denominator.
Neutral EOS flashes do not dispatch to this solver.

---

## 7. References

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

### Hydrogen-Rich Vapor-Liquid Equilibrium

10. **Sagara, H., Arai, Y., & Saito, S.** (1972). "Vapor-Liquid Equilibria of Binary and Ternary Systems Containing
    Hydrogen and Light Hydrocarbons." *Journal of Chemical Engineering of Japan*, 5(4), 339-348.
    [doi:10.1252/jcej.5.339](https://doi.org/10.1252/jcej.5.339)

---

## Implementation Files

| File | Description |
|------|-------------|
| [TPflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/TPflash.java) | Two-phase flash with SSI and Newton |
| [TPmultiflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/TPmultiflash.java) | Multi-phase flash with stability analysis |
| [RachfordRice.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/RachfordRice.java) | Rachford-Rice equation solvers |
| [SysNewtonRhapsonTPflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/SysNewtonRhapsonTPflash.java) | Second-order Newton solver |
| [ChemicalReactionOperations.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/chemicalreactions/ChemicalReactionOperations.java) | Chemical equilibrium solver |

---

## Usage Example

The constructor below uses temperature in K and absolute pressure in bara. Component additions are
amounts in mol; these values happen to sum to 1.0 mol. Enabling multiphase checking asks NeqSim to
test for additional fluid phases. It does not select or validate the thermodynamic model and does
not guarantee that a gas phase exists at every state.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class TpFlashExample {
  private static final Logger logger = LogManager.getLogger(TpFlashExample.class);

  private TpFlashExample() {}

  public static void main(String[] args) {
    SystemSrkEos system = new SystemSrkEos(298.15, 10.0);
    system.addComponent("methane", 0.7);
    system.addComponent("ethane", 0.2);
    system.addComponent("propane", 0.1);
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);

    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    operations.TPflash();

    double vaporFraction = 0.0;
    if (system.hasPhaseType(PhaseType.GAS)) {
      int gasPhaseNumber = system.getPhaseNumberOfPhase(PhaseType.GAS);
      vaporFraction = system.getBeta(gasPhaseNumber);
    }

    logger.info("Number of phases: {}", system.getNumberOfPhases());
    logger.info("Vapor fraction: {}", vaporFraction);
  }
}
```

Resolve the gas phase by `PhaseType.GAS`; active phase zero is not a universal vapor-phase
contract after phase addition, removal, and density ordering. The example is a calculation
workflow, not evidence that the classic SRK parameterization is accurate for a particular fluid or
operating envelope. Check convergence, material balance, phase stability, and an independent
benchmark before engineering use.

Ordinary `TPflash()` equilibrates the component identities already present in the thermodynamic
system. It does **not** discover reaction products or by itself establish electrolyte reaction
equilibrium. For aqueous speciation or simultaneous chemical and phase equilibrium, follow the
[reactive-flash workflow](../thermo/reactive_flash.md), including its explicit reaction-model,
standard-state, charge-balance, and validation boundaries.

### Large-volatility hydrocarbon endpoint refinement

For neutral high-pressure hydrocarbon mixtures with a large volatility contrast, an ordinary
two-phase flash can retain a higher-Gibbs stationary point even though the reciprocal explicit
multiphase path finds a closed equilibrium. A private finalization screen now admits a reciprocal
candidate only when all active components are hydrocarbons or inerts, pressure is at least 50 bar,
at least two components are active, the component critical-temperature span is at least 300 K, and
the least volatile component contributes at least 0.01 of the feed. Public API and tolerances are
unchanged.

Four deterministic methane/n-heptane regressions were reproduced on SRK and PR:

| EOS | Temperature (K) | Pressure (bar) | `z(n-heptane)` | Ordinary maximum log-fugacity residual before refinement |
| --- | ---: | ---: | ---: | ---: |
| SRK | 180 | 50 | 0.05 | 4.58128e-2 |
| SRK | 180 | 100 | 0.10 | 1.10606e-2 |
| SRK | 220 | 200 | 0.10 | 5.80033e-5 |
| PR | 260 | 200 | 0.10 | 1.02025e-2 |

The reciprocal candidate is reset to the feed before reflashing. If it retains two liquid-like
cubic roots and fails material closure, the lighter phase is initialized on the gas root and the
heavier phase on the oil root before bounded multiphase beta refinement. Acceptance still requires
neutral fluid phases, normalized and bounded compositions, maximum component material-balance
residual below `1e-10`, maximum log-fugacity residual below `1e-8`, and a lower Gibbs energy within
the existing numerical allowance. Recursion is guarded and a rejected or failed candidate cannot
modify the original state.

An exact-master qualification matrix covered 1,200 SRK/PR states: methane/n-heptane and the
nearby methane/ethane control, 180--380 K, 5--200 bar, and heavy-component feed fractions from
`1e-8` to `0.8`. All states met ordinary/multiphase agreement of `1e-10` for beta and compositions
and `1e-8` for compressibility factor. Focused regressions also cover poor initialization, exact
settled repeats, cold-to-warm and changed-state continuity, nearby compositions, material balance,
fugacity equality, and screen-excluded controls.

This is a correctness fallback rather than a speed optimization. In a constrained fresh-system
probe (20 warmups and five blocks of 60 flashes), the corrected SRK 180 K/50 bar case changed from
a median 14.25 ms/flash for the invalid endpoint to 18.20 ms/flash for the accepted equilibrium.
The screen-excluded SRK 300 K/100 bar trace-heavy control was 10.98 versus 10.88 ms/flash, within
run-to-run noise. No speedup is claimed, and common flashes return before the new refinement.

### Near-cricondenbar rich-gas endpoint refinement

An ordinary SRK or PR flash near a rich natural gas's cricondenbar can retain a local cubic root or
collapse a valid gas/oil split even when the explicit multiphase path finds a conservative,
fugacity-equal, lower-Gibbs equilibrium. The private performance screen is limited to 50--200 bar,
neutral water-free feeds containing at least four active hydrocarbons, at least 0.90 total
hydrocarbon, at least 0.30 methane, at least 0.05 condensable hydrocarbon, no more than 0.05 carbon
dioxide, and a hydrocarbon critical-temperature span of at least 250 K. Other active components
must be inert. These conditions select only whether to run the established reciprocal stability
flash; thermodynamic acceptance remains authoritative.

The qualification fluid contains nitrogen, carbon dioxide, methane through n-hexane and uses the
classic mixing rule. Deterministic failures included:

| EOS | Temperature (K) | Pressure (bara) | Ordinary result before refinement | Maximum log-fugacity residual before refinement | Lower-Gibbs result (J) |
| --- | ---: | ---: | --- | ---: | ---: |
| SRK | 273.15 | 100 | GAS+OIL | 1.30055e-2 | 626518.842 |
| SRK | 283.15 | 100 | GAS+OIL | 1.51337e-2 | 670464.297 |
| PR | 268.15 | 95 | GAS+OIL | 8.42662e-2 | 586168.078 |
| PR | 273.15 | 100 | GAS only; material residual 9.88469e-4 | not applicable | 614120.253 |
| PR | 278.15 | 100 | GAS+OIL | 5.2598e-3 | 636403.262 |
| PR | 283.15 | 100 | GAS+OIL | 3.1720e-3 | 657662.282 |

The reciprocal candidate must contain one or two neutral fluid phases, remain bounded and
normalized, close component material balance below `1e-10`, close maximum log-fugacity residual
below `1e-8`, and lower Gibbs energy beyond the existing allowance. Complete initialized phase
objects preserve the accepted cubic roots during transfer. A final maximum of five bounded beta
updates targets a tighter `1e-10` log-fugacity residual without changing the selected active set;
failure, Gibbs increase, or lost closure restores the complete accepted candidate. Final one-phase
states are normalized to beta one and `x = z`.

Focused SRK/PR regressions cover the six states above, poor initial beta values, deterministic
repeats, changed-temperature and return-to-state execution, gas/oil appearance, single-phase
disappearance, material balance, fugacity equality, phase normalization, bounded compositions,
compressibility factor, density, and ordinary/multiphase agreement. The near-critical comparison
uses `1e-7` for beta and composition because phase fraction is ill-conditioned at the boundary;
the independent conservation and fugacity gates remain `1e-10` and `1e-8` respectively.

This is a correctness fallback, not a speed optimization. Valid nonqualifying flashes perform no
additional thermodynamic solve. A qualifying invalid endpoint performs one reciprocal flash and at
most five beta updates; qualifying endpoints that already meet the equilibrium gate return after
an allocation-free composition screen and residual audit. No public API, model parameter, unit, or
default changes.
