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

For neutral fluids containing an active TBP or plus fraction with invalid stored
critical properties and multiphase checking enabled, the final TPflash state is
checked after the bounded refinement paths. Invalid properties are non-finite or
non-positive critical values, or a critical temperature at or below the normal
boiling point. Active phase fractions and compositions must be finite,
nonnegative, and normalized; recombined component mole fractions must agree
with the feed within an absolute tolerance of `1e-8`. An invalid result throws
`IllegalStateException` before streams or separators can use its phase
inventories. Independently normalizing the compositions of a failed phase split
does not restore component conservation. Reactive, ionic, solid, wax,
specialized EOS-GE, and valid characterized fluids retain their existing
acceptance paths; this guard does not itself certify equilibrium or global
stability.

The wet PR/Twu sour-fluid regression for
[issue #3624](https://github.com/equinor/neqsim/issues/3624) combines a repaired
[Twu characterization correction](../wiki/tbp_fraction_models#34-twu-model)
with this failure guard. At 343.15 K and 33 bara, a 100000 kg/h feed gives
14181.286376 kg/h gas, 84974.581766 kg/h oil, and 844.131858 kg/h water.
The regression checks phase and separator component balances, normalized phases,
fugacity equality, repeated/cloned runs, and nearby pressure/temperature cases.

---

## Water-and-ion electrolyte-CPA endpoint

Reactive water-and-ion feeds use the same constrained phase search and aqueous-reaction coupling
as other ionic fluids. The former model- and water-name-specific endpoint shortcut has been removed.
Ions retain the aqueous host, while neutral water participates in the ordinary molecular stability
and fugacity equations. Phase, reaction, charge and original-element acceptance checks still apply.
`TPflashWaterIonBrineTest` exercises public calculations at 1, 20 and 100 bar, repeated salt removal
and cloned fluids without a separate composition dispatch. Numerical closure does not validate
salt solubility or hydrate-model accuracy.

The ordinary two-root comparison evaluates `sum(x_i * ln(phi_i))` at fixed
composition, temperature and pressure on the same phase object. Its common
ideal-gas reference and mixing terms cancel between the GAS and LIQUID roots.
This avoids subtracting extensive `H - T*S` values when a phase is near its
amount bound after a different initialization level. Zero-fraction terms are
omitted analytically; invalid compositions or non-finite occupied fugacity
contributions are rejected. Direct and hybrid EOS-GE strategies retain their
model-owned phase roles and do not enter this comparison. The final material,
fugacity and phase-stability checks remain required.

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
   - [5.5 Detached Stability Workspaces](#55-detached-stability-workspaces)
   - [5.6 Matched Stability-Workspace Qualification](#56-matched-stability-workspace-qualification-2026-10-14)
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
| Ionic fluid closure | full species inventory with a unique aqueous host | Shared RR/SSI/Newton and stability constraints; `1e-10` species/phase amount closure, `1e-12` beta normalization, aqueous ion confinement and `1e-8` molecular log-fugacity equality. The constrained Newton iteration validates the endpoint both before and after the final association/property initialization, using the same physical tolerances on the actual returned state. It does not repeatedly reject a qualified endpoint merely because its numerical residual floor exceeds a tighter surrogate target. Failure identifies the offending species and residual. Failure is explicit. |
| Ionic host assignment | More than one material liquid has the model's aqueous classification | Move the complete ionic inventory to each alternate liquid on an isolated clone, conserving every species and charge; solve the same fixed-topology beta/Newton equations. Adopt only a result passing the unchanged physical checks, retaining its requested roots and material phases, and lowering Gibbs energy by more than `max(1e-8 J, 1e-10 abs(G))`. A gas/brine endpoint with one eligible liquid creates no trial. This compares single-host active sets; it does not model ion partitioning between multiple ionic aqueous phases. |
| Trace duplicate phase cleanup | `min(beta_i, beta_j) < 10 beta_min`, same `PhaseType`, and `max abs(x_i - x_j) < 1e-6` | Merge and remove an already-disappeared numerical duplicate for any EOS while conserving phase fraction. Material-fraction duplicate cleanup remains limited to CPA models to protect near-critical cubic-EOS splits. |
| Initial SSI iterations | 3 | Preliminary iterations before stability check |
| `accelerateInterval` | 5 | Apply DEM every 5th iteration in the ordinary TPflash loop |
| `newtonLimit` | 12 | Switch to Newton-Raphson after 12 SSI iterations when derivatives are available |
| Enhanced multiphase adaptation | 3-4 / 8-10 | Lower acceleration and Newton thresholds when enhanced stability checks are active and the SSI deviation is already small |
| `maxNumberOfIterations` | 50 | Maximum iterations per convergence loop |
| Convergence tolerance | 1e-10 | Deviation threshold for K-value convergence |
| Gibbs increase tolerance | 1e-8 | Relative increase that triggers K-reset |
| Supplementary stability TPD limit | -1e-6 | Accept a converged amplified-K or composition-perturbation trial only when its reduced TPD exceeds that SSI solve's residual/step resolution and the trial composition is non-trivial |
| Ordinary water-rich refinement feed threshold | 0.01 mole fraction water | Avoid phase-search overhead for valid trace-water flashes. An already-active neutral aqueous split bypasses only this feed threshold when material-balance or log-fugacity residuals are non-finite or exceed their `1e-8` acceptance limits, allowing the bounded beta correction below without another stability calculation. An incipient trace-water GAS+OIL endpoint with `min(beta) <= 1e-4` also bypasses the threshold when its confirmed log-fugacity residual is non-finite or at least `1e-8`; the guarded candidate must then pass the strict feasibility and lower-Gibbs gates. |
| Water-rich cross-algorithm fallback | water feed `>= 0.01` and current `max abs(Delta ln(f_i)) >= 1e-8`, material-balance failure, or multiphase collapse to one hydrocarbon phase | Evaluate the existing cold candidate through the alternate path first. If an invalid ordinary two-phase split remains after that candidate is rejected, preserve its compositions as the seed for one fully initialized `TPmultiflash` trial. A multiphase endpoint collapsed to one phase may try the ordinary path, including the same seeded refinement when the cold ordinary result is invalid, but accepts only a result that restores an AQUEOUS phase; ordinary gas appearance remains outside this aqueous-recovery fallback. Genuine OIL+AQUEOUS liquid-liquid endpoints remain on the multiphase path. Prevent reciprocal recursion and replace the endpoint only when the candidate has exactly two distinct bounded phases, normalized beta and compositions, material balance and fugacity residuals below `1e-8`, and a Gibbs reduction larger than `max(1e-6 J, 1e-8 abs(G))`. Already-feasible two-phase endpoints incur only the residual acceptance scan. |
| CPA high-water hydrocarbon-liquid stability screen | ordinary one-phase AQUEOUS CPA endpoint, water feed `>= 0.01`, `f_water / p_water_sat >= 0.8`, and at least 0.01 hydrocarbon feed with `T_c > T + 80 K` | Use the water saturation, composition, and critical-temperature checks only as a performance gate for one cloned multiphase stability calculation. Accept only an exactly-two-phase, distinct, bounded, normalized result with material-balance and log-fugacity residuals below `1e-8` and a Gibbs reduction larger than `max(1e-8 J, 1e-12 abs(G))`. Chemical, ionic, solid, wax, non-CPA, explicit-multiphase, gas, and non-aqueous endpoints retain their existing paths. |
| Trace-water aqueous-stability screen | water feed `< 0.01`, GAS+OIL, `min(beta) <= 0.01`, and `x_water,oil >= 10 z_water` | Use the structural conditions only as a performance gate for an aqueous TPD trial. The TPD result, reduced-active-set convergence below `1e-10`, phase normalization, `1e-8` material/fugacity checks, distinct compositions, and lower Gibbs energy determine acceptance. Full recursive TPmultiflash is not run. |
| CPA one-phase aqueous-stability screen | water feed `< 0.01`, one ordinary phase, CPA model, and `f_water / p_water_sat >= 0.8` | Use the fugacity ratio only as a conservative performance gate for the existing aqueous TPD trial. When the trial adds one phase, rebuild the two-phase active set and require beta-solver residual `< 1e-10`, phase normalization, `1e-8` material/fugacity checks, distinct compositions, and a Gibbs reduction larger than `max(1e-8 J, 1e-12 abs(G))`. The tighter Gibbs tolerance retains independently converged incipient aqueous fractions without treating the saturation screen itself as a stability criterion. |
| Water-rich material-balance tolerance | 1e-8 in `max abs(Delta z_i)` | Reject a non-conservative reference before comparing feasible Gibbs minima |
| Dry cubic-root screen and acceptance | Screen normally ordered GAS+OIL endpoints when `max abs(Delta ln(f_i)) >= 1e-8`; accept below `1e-8` | Evaluate both roots for one phase at a time and retain a lower-Gibbs root seed only when the resulting unchanged composition split restores fugacity equality. Inverted mean-molar-mass order retains the paired-root comparison. |
| Aqueous cubic-root equilibrium tolerance | 1e-8 in `max abs(Delta ln(f_i))` | Ordinary flashes accept an alternate root only when it lowers Gibbs energy and already satisfies component fugacity equality. A multiphase GAS+AQUEOUS endpoint may replay a lower-Gibbs ordinary candidate containing exactly one AQUEOUS and one cubic GAS/OIL/LIQUID phase, so a valid gas-to-oil root transition is not rejected by its new label. The candidate must additionally retain normalized positive phases, distinct compositions, and component balance below `1e-8`. |
| Stable-single-phase aqueous-seed gate | `1e-8` in phase-composition normalization | Reject a structurally invalid aqueous trial whose composition is non-finite, out of `[0, 1]`, or unnormalized; also reject a normalized trial when its component balance fails and its Gibbs energy exceeds the stable single-phase reference. Other normalized endpoints retain model-specific refinement paths |
| Post-removal aqueous recovery | `1e-8` in `max abs(Delta z_i)` and `max abs(Delta ln(f_i))` | Restore a balanced neutral two-phase aqueous reference only when a rejected third-phase trial leaves the same two-phase topology infeasible; a valid GAS↔OIL root transition, genuine three-phase result, or already feasible endpoint is retained |
| Final aqueous active-set refinement | water feed `>= 0.01`, or an active aqueous split with invalid material-balance or log-fugacity residual; at most 3 beta refinements; `1e-8` in phase normalization, `max abs(Delta z_i)`, and `max abs(Delta ln(f_i))` | Preserve the selected neutral two-phase active set while correcting stale beta/compositions after phase cleanup or root selection. Trace-water bypass does not run phase search. Roll back unless the result is feasible; also require no Gibbs increase when the reference material balance was valid. |
| Missing aqueous phase after oil-endpoint cleanup | Explicit multiphase, neutral OIL or GAS+OIL endpoint, water feed `> 20 * phaseFractionMinimumLimit` (currently `2e-11`), phase shifting allowed, no solid/wax check | On both the normal and stable-single-phase return paths, solve a balanced OIL+AQUEOUS trial on a clone. The former 5 mol% cutoff no longer excludes dilute aqueous equilibria. Skip numerically unresolvable feeds before allocation: the half-water seed must exceed the ten-times-minimum phase-fraction acceptance floor. Use the shared phase lifecycle to add a distinct aqueous trial without aliasing the oil object. Adopt only an aqueous equilibrium with positive normalized phases, component balance and log-fugacity residuals within `1e-8`, and a Gibbs decrease exceeding `max(1e-6 J, 1e-8 abs(G))` relative to any normalized, material-balanced incumbent, even if its fugacities have not fully converged; otherwise retain the original state. This is bounded active-set recovery, not a global stability certificate. |
| Final neutral gas/oil equilibrium refinement | `1e-8 <= max abs(Delta ln(f_i)) <= 1e-5`; at most 8 SSI updates | Repair only balanced, near-converged vapor-liquid endpoints after post-convergence root handling. Preserve both phase types and roll back unless phase fractions, compositions, material balance, fugacity equality, and Gibbs energy pass the strict acceptance checks. |
| Stalled three-phase active-set fallback | `1e-8` in phase normalization, material balance, and `max abs(Delta ln(f_i))` | For neutral non-reactive fluid phases only, evaluate each two-phase active set after a non-converged three-phase endpoint, including an invalid three-phase split left by final TP-flash cleanup and accept the lowest-Gibbs feasible equilibrium only when it also lowers Gibbs energy relative to the stalled state. A pending solid check is preserved and runs after fluid recovery; active solid phases and locked phase roles are excluded. The condensate entropy-continuity regression and the oil-stabilization pump integration test cover final-cleanup recovery. |
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

$$J_{ij} = \frac{\partial f_i}{\partial u_j} = \frac{1}{\beta}\left(\frac{\delta_{ij}}{y_i} - 1 + \frac{\partial \ln \phi_i^V}{\partial y_j}\right) + \frac{1}{1-\beta}\left(\frac{\delta_{ij}}{x_i} - 1 + \frac{\partial \ln \phi_i^L}{\partial x_j}\right)$$

The two-phase implementation stores the initially smaller allowed-phase amount for
each partitioning species. This preserves a positive trace that would disappear in
the subtraction of nearly equal feed and phase amounts. The orientation stays fixed
during the local solve. With diagonal signs $D_{ii}=+1$ for first-phase amounts and
$D_{ii}=-1$ for second-phase amounts, the transformed gradient and Hessian are
$D\mathbf{f}$ and $D\mathbf{J}D$. The conserved objective and phase constraints are
unchanged. Fixed ions retain their complete inventory in their admissible phase.

**NeqSim Implementation:**

See [SysNewtonRhapsonTPflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/SysNewtonRhapsonTPflash.java) for the full implementation.

The amount-coordinate Hessian uses relative diagonal regularization,
$J_{ii} \leftarrow J_{ii} + 10^{-8}|J_{ii}|$. Each coordinate keeps its own
curvature scale, so the inverse-amount curvature of a trace species does not damp
all abundant-species transfers. Finite-curvature, descent and feasibility checks
remain mandatory.

The line search evaluates the reduced Gibbs energy with the **trial phase component
amounts**, together with fugacities evaluated at that same trial. A candidate must be
finite, feasible and satisfy Armijo descent before the retained Newton inventory is
updated. When the predicted decrease is below 32 ulp of the current objective, an
otherwise valid trial may instead qualify if its apparent objective increase is also
within that roundoff bound and its maximum log-fugacity residual falls by more than
half. This only resolves the objective's numerical precision limit; the final
equilibrium tolerances remain unchanged. The eight-trial limit is a work bound, not permission to accept an untested
smaller step. If no candidate qualifies, the solver restores the preceding split and
reports rejection to the existing successive-substitution fallback. Non-finite,
singular and non-descent Newton directions are rejected before changing the split.

The convergence measure is the maximum of the relative accepted step and the
remaining component log-fugacity mismatch. A tiny line-search step alone does not
establish equilibrium. Within ten times the existing minimum phase fraction of
either two-phase boundary, TPflash uses successive substitution with Rachford-Rice
instead of the interior Newton step, so a disappearing phase can reach its bound
and be removed. This numerical active-set safeguard uses no component-family
screen and adds no EOS evaluations to the Newton convergence check.

For electrolyte CPA, every positive initialization level refreshes association
contact values, site fractions and component properties at the final returned
volume. Property initialization (`init(1)`) and derivative initialization
(`init(3)`) must therefore give the same fugacities at an unchanged state; stale
association caches must not invalidate a converged endpoint.

An already equilibrated state with log-fugacity mismatch at floating-point roundoff
(32 ulp of one) returns a zero correction because its Gibbs decrease cannot be
resolved. This does not relax the public flash tolerances or the separately guarded
sour-endpoint polishing criterion. Accepted trials synchronize their K values, and
rebinding a cached solver refreshes its component inventory from the live state after
any caller-side fallback. These safeguards apply to the local two-phase step; global
phase stability still requires the surrounding stability search and endpoint checks.



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

`TPmultiflash` retains the full input species inventory during phase search. Ionic participation is
captured as an aqueous-phase constraint; neutral molecules remain free to partition through the
existing EOS fugacity equations. No CO2, MEG, methanol or mixed-gas whitelist selects a different solver.

1. Capture permitted phase participation and the full feed before stability analysis.
2. Evaluate normalized molecular trial phases against the ionic aqueous reference; ions remain in
   that reference and are excluded from non-aqueous trials.
3. Add a phase only through the existing stability/seed machinery, then solve the full phase amounts.
4. Reconcile the aqueous host at an active-set change if the original host becomes non-aqueous and
   the model identifies one actual aqueous phase. Re-equilibrate the complete inventory after transfer.
5. Solve beta and composition together. The ionic beta Hessian uses dimensionless ratios before
   products to avoid overflow/underflow at extreme fugacity ratios. Composition SSI uses available
   derivatives to bound its logarithmic step; stopping includes molecular residuals, not only beta changes.
6. Require finite, normalized phase and stored component amounts, original species conservation,
   aqueous ion confinement and molecular fugacity equality before returning a non-reactive ionic state.

The neutral stability algorithm below retains its established trial selection. Solids, wax and
forced phase topologies keep their physical model boundaries.

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

The active set is searched, seeded, equilibrated and pruned with the existing multiphase machinery.
For ionic fluids, the feed is never normalized to an ion-free surrogate and restored afterward.
`FlashPhaseConstraints` identifies allowed species/phase pairs, while `FluidEquilibriumChecks`
qualifies the final material state. A converged beta update alone cannot certify the composition solve.
See [the complete flow](#30-complete-tpmultiflash-algorithm-flow) for the current sequence.

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
`TPmultiflash`. The snapshot retains both logical phase roles and their physical phase-array slots. A thread-local guard
and a per-operation one-shot flag prevent reciprocal recursion.

The trial replaces the retained endpoint only if it preserves every original phase role, adds GAS, stays within the
configured phase-count limit, and lowers extensive Gibbs energy. An exception, missing phase, non-improving Gibbs
energy, or rejected trial leaves the snapshot in place; beta, phase compositions, K-values, and initialized properties
are restored on their original physical phase-array slots. If phase shifting is disabled, the restart is skipped because
the trial cannot reliably adopt or restore phase roles. Thus the Wilson screen triggers an equilibrium calculation but
does not itself decide phase stability.

```java
if (oilAndAqueousWithoutGas && neutral && wilsonSubmixtureBracketsTwoPhases) {
    if (!system.allowPhaseShift()) {
        return;
    }
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

Reactive ionic fluids with freely selected fluid phases use `AqueousReactionEquilibrium`, a bounded
coupling of the ordinary TP flash and the existing chemical solver. Each phase solve freezes the
species inventory. Chemistry runs on an isolated aqueous phase; only its reaction-induced species
changes are transferred back to the full fluid. Element totals include spectator ions and charge.

A result needs molecular log-fugacity equality within `1e-8`, reaction log residual within `2e-6`,
charge closure and the original elemental inventory. At most 30 coupling iterations are allowed.
Slowly converging reaction/phase coupling uses a safeguarded scalar secant estimate for the complete
reaction-induced inventory increment. The extrapolation preserves the increment's elemental and
charge balances, is bounded by species positivity, and is accepted only through the same physical
checks. A worsening reaction residual or a failed accelerated phase solve restores the conservative
unaccelerated chemical update and resets the secant history. The convergence tolerances and the
30-iteration bound are unchanged. A change in the material or requested phase roles clears the
acceleration history.

This uses vector Aitken acceleration (Irons and Tuck, *International Journal for Numerical Methods
in Engineering* **1**, 275–277, 1969,
[doi:10.1002/nme.1620010306](https://doi.org/10.1002/nme.1620010306)). For the conservative fixed-point
increment $r_k=F(n_k)-n_k$ and actual previous step $s_{k-1}=n_k-n_{k-1}$, the scalar inverse-secant
estimate is $\alpha_k=-s_{k-1}^{T}(r_k-r_{k-1})/\lVert r_k-r_{k-1}\rVert^2$. This implementation
bounds the factor to $[1,10]$ and limits an extrapolated decrement to 99% of each positive species
inventory; the original chemical update remains the fallback. This accelerates an existing local
coupling and makes no new claim of global reactive phase stability.
After six slow coupling iterations, or a failed isolated chemical solve, a
bounded Newton correction in the configured reaction-extent space accounts for
the finite gas inventory. Its centered-difference Jacobian differentiates the
actual reaction affinities after fixed-topology phase equilibration, so dissolved
gas replenishment participates in the chemical response. An accepted trial must
decrease the actual reaction-residual merit after a full phase flash, retain
positive species amounts, and preserve all invariants of the registered
stoichiometric network. The original 30-iteration coupling bound and final
physical tolerances remain unchanged. The optional isolated-chemistry projection
is adopted only when it further improves that merit. This is a local coupled
correction, not a proof of global reactive stability.

System clones and extracted reactive phases retain the caller's configured
reaction list, equilibrium coefficients, kinetic parameters, and disabled-chemistry
state. Rebinding numerical helpers must not reload the database, rediscover a
removed reaction, or replace a user-supplied equilibrium constant. Rejected
trial systems remain detached, including their activity reference phases.

Failures and cancellation leave the caller uncommitted. This supports general molecular mixtures;
the old `ReactiveCO2BrinePhaseEquilibrium` class is a deprecated compatibility adapter.
A fixed-species phase flash conserves each species; reactions instead conserve elements and may
change both species amounts and total moles.

Last-aqueous disappearance has a conservative boundary recovery. Registered
reaction extents must first recombine every occupied ion into nonnegative
molecular inventory. Spectator ions cannot disappear. A complete frozen-species
flash must qualify the resulting neutral phases, after which reaction-aware
aqueous trials minimize the grand potential in the registered reaction and
molecular-transfer span. Positive conserved atom normalization keeps the
stationarity equations valid when reactions change total species moles.
Every start must converge with a pressure-valid root, a small reduced gradient
and positive reduced curvature; its distance is compared on the existing
per-mole stability scale. A negative or unqualified trial rejects disappearance.

This local multistart recovery preserves the configured reaction network and
elemental and charge invariants. It is qualified for mole-fraction reaction
activities with compatible fugacity references; forced phase types, solid/wax
routes, GE models and unsupported reaction conventions retain rejection. Heating
and cooling tests include repeated phase disappearance/reappearance, different
feed scales, neighboring pressures and custom reaction constants. This addresses
the tested transition in [issue #4341](https://github.com/equinor/neqsim/issues/4341)
without discarding ionic inventory. Exact-zero ionic products also require
reaction-aware aqueous absence checks when a registered reactive species is
present. Feasible reaction extents create an interior trial without changing the
caller inventory or imposing a permanent species floor. A negative or unqualified
trial rejects the neutral endpoint transactionally. This guard prevents false
stable-gas acceptance; it does not construct a new reaction-stabilized phase split
or provide a global reactive stability certificate.

The adaptive aqueous chemical solver retains ideal concentration curvature when adding EOS
composition derivatives. `ComponentInterface.getdfugdn(j)` is
$\partial\ln\phi_i/\partial n_j$ alone; it does not include
$\delta_{ij}/n_i-1/n_t$. Subtracting that ideal term a second time removes the
large positive curvature of dilute species and can produce a negative diagonal.
For mole-fraction activities with fixed pure-solvent or binary infinite-dilution
references at the specified temperature and pressure, the nonideal derivative is
added only for species whose reaction convention uses an activity coefficient.
Independent centered differences check the coefficient API and both supported
M-matrix conventions against the actual logarithmic activities.

### 4.2 Ion Handling in Stability Analysis

The aqueous reference retains its full ionic composition and electrolyte chemical potentials.
Non-aqueous molecular trials exclude ions by phase participation, without removing ions from the feed.
Trial compositions are normalized before invoking component setters. Both fluid roots are considered;
a homogeneous ionic result is rejected if a normalized molecular trial is unstable.

The supplementary homogeneous-reference trials run at final homogeneous ionic acceptance. Fluids
configured with `setHydrateCheck(true)` also evaluate the initial homogeneous aqueous reference:
hydrate diagnostics use its sign to distinguish an undersaturated feed from a saturated boundary,
even when the accepted fluid has multiple phases. Ordinary ionic multiphase flashes omit this
additional diagnostic. Ordinary neutral stability analysis and all final homogeneous rejection
thresholds remain unchanged. Recorded tangent-plane distances describe trials actually performed;
the initial hydrate-reference diagnostic does not change the accepted fluid equilibrium.

Rachford-Rice does not infer ionic identity from a small K-value. Zero or tiny K values for positive-feed
neutral species still contribute their inventory. Algebraically equivalent ratios avoid squaring
extreme K values; both existing algorithms and their selector remain available.

The existing Newton solver uses positive-feed, unconstrained neutral amounts as its free variables.
For more than two fixed fluid phases it solves amounts in every phase except a reference phase,
whose amounts follow from complete species conservation. Its reduced Hessian combines the independent
phase curvature with reference-phase curvature; fixed ions remain in all model properties and Gibbs
energy. Phase creation/removal stays in the outer stability algorithm. The original two-phase path is
retained. Ions and exact-zero feed components remain constrained. Representable positive trace amounts
are allowed without an arbitrary `1e-15` floor; a complementary amount lost to floating-point subtraction
is still rejected. Gibbs descent, feasibility, line-search limits and rollback remain mandatory.

EOS-GE models retain their existing phase-role and reaction strategy. Their EOS vapour and GE liquid
use model-specific reference states, so comparing an alternate cubic liquid root of the vapour slot
is not a valid same-model stability diagnostic. That diagnostic is unavailable (NaN) for this
capability; the shared physical acceptance checks remain in place. Generic constrained Newton and
reaction dispatch do not replace the model-owned hybrid equations.

### 4.3 Aqueous Phase Management

The initial constrained split allocates ions to one aqueous material host. Aqueous ions are
allocated by conserved amount, not by copying the overall mole fraction into a phase and normalizing.
A gas/oil/brine phase-set change may require transferring the host to the model-classified aqueous
phase and re-equilibrating; changing a display label alone does not solve equilibrium.

When a nonreactive EOS endpoint contains multiple aqueous liquids, a subsequent
fixed-topology calculation permits charge-neutral ionic transfers between them.
Each host must have both an initialized AQUEOUS classification and a configured
liquid-family root. One reference ion is eliminated using each phase's charge
balance; the remaining independent amount directions preserve every species
inventory. The existing Newton objective and line search use the projected
gradient `B^T mu` and Hessian `B^T H B`. Individual ionic chemical potentials
need not be equal across phases. Acceptance instead requires all independent
charge-neutral affinities and molecular fugacity residuals below `1e-8`,
pressure closure, phase charge and species balance, unchanged volume roots,
and nonincreasing Gibbs energy within rounding error. This local refinement
does not add or remove phases, prove global stability, or qualify reactive
multiple-liquid states.

The constrained route requires a strictly positive ionic inventory, rather than only registered
ionic component names. `phaseToSystem("gas")` retains the original component basis, including
zero-amount ions; reflashing that molecular stream must not invent an aqueous host or new ions.
The public `hasIons()` still reports registered ionic species. There is no concentration cutoff:
every positive ionic amount retains the aqueous constraint. Reactive streams retain their chemical
strategy: the coupling first qualifies frozen-species phase equilibrium, then accepts a state with
zero ionic inventory and no aqueous phase without evaluating undefined aqueous reaction residuals.
If an aqueous phase appears, the normal reaction coupling continues. `TPflashZeroIonicInventoryTest` covers separated streams, repeated
and changed-temperature flashes, neutral-reference agreement and positive trace-ion conservation.

Final non-reactive ionic acceptance checks original species balance and phase/amount normalization
within `1e-10`, beta sum within `1e-12`, phase charge, ion confinement and molecular log-fugacity
equality within `1e-8`. These checks establish feasibility and present-phase equilibrium, not a
proof of the global Gibbs minimum or experimental model accuracy. Incorrect dielectric data cannot
be corrected by tighter numerical convergence. See the [electrolyte derivative qualification](../thermo/ElectrolyteBornDerivatives).

Fluid EOS roots are also checked against the phase's actual pressure equation. A small
volume correction can occur at a spinodal without solving that equation. Acceptance
requires finite positive total volume, finite nonpositive `dP/dV`, and pressure error
no larger than `max(1e-8 * max(1, abs(P)), 1e-10 * abs(V_total * dP/dV))`.
The second term preserves the volume solver's relative accuracy for stiff liquids;
the direct pressure bound controls at a spinodal. The cubic analytical fallback is
only a proposal: it must satisfy the virtual pressure equation, including any
subclass contributions. Its single-real-root Cardano evaluation uses the larger
radicand and recovers the partner cube root from `u*v = -p/3`, avoiding loss of
precision when two nearly equal terms would otherwise be subtracted. Dense
distillation-mixture regressions check actual pressure at three inventory scales. Legacy GERG2004 and Span–Wagner adapters are excluded from
this particular check because their inherited cubic pressure method does not
represent their external density model. Newton trials and returned material
endpoints use the same gate. Benchmark snapshots record pressure closure both
before and after derivative initialization, so reinitialization cannot hide an
invalid returned state. Initialization also respects the configured maximum phase
count: `init(0)` does not activate an uninitialized second phase when that maximum
is one. Repeated initialization is qualified across feed-amount scales for SRK
and PR.

GERG-2008, EOS-CG, Leachman hydrogen and Vega helium adapters obtain volume from
native molar density and evaluate the native pressure equation at that stored
volume. Their pressure slopes use bar per total internal volume, including the
phase amount. Independent native-EOS checks cover dense and gas roots, every
hydrogen variant, EOS-CG-only species, material scaling and deliberately corrupted volumes. The
IAPWS water adapter retains its SI volume convention and converts its numerical
molar-volume slope to a total-volume derivative. The GERG and EOS-CG native
working caches are thread-owned, as described in the [reference-EOS guide](../thermo/gerg2008_eoscg.md).

For a nonreactive ionic split, alternate roots of the nonionic phases are screened
at unchanged composition using `sum(x_i * ln(phi_i))`. A promising root is
reequilibrated on a detached system with the same species inventory and fluid
active set. Only a physically qualified lower-Gibbs result is adopted. This
bounded root comparison does not certify global phase stability.

## 5. Performance Optimizations

The TP flash implementation includes several performance optimizations to reduce computational cost, particularly for multi-component systems.

The historical benchmark snapshots below retain their original source identities.
They predate the expanded actual-EOS pressure checks and the subsequent derivative,
reactive-boundary and model-input repairs. Their results do not qualify the final
reviewed implementation; new comparisons must recheck both builds with the same
complete physical gates before making a speed claim.

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

Before the Wilson trial stage, the stability analysis checks whether
$\max|\ln K_i| < 0.01$. Near-unity Wilson values produce nearly trivial starting
compositions, so those particular trials are skipped. The subsequent stability
search remains available; this heuristic is not a proof of mixture stability.
Every EOS evaluation uses normalized composition, including neutral enhanced
trials. Unnormalized Wilson or exponential weights belong only to the stability
objective.

### 5.4 Two-Stage Trial Strategy

The Wilson K-based trials (Stage 1) run with only 50 iterations and catch the majority of VLE instabilities. The more expensive pure-component trials (Stage 2, up to 200 iterations with potential Newton fallback) only execute when the K-based trials fail to find instability.

### 5.5 Detached Stability Workspaces

The lowest-Gibbs reference calculation in `Flash.findLowestGibbsEnergyPhase()` uses a
detached, fixed-component stability workspace. For exact `SystemSrkEos` and `SystemPrEos`
instances with two or three ordinary cubic storage slots, the classic mixing rule and analytic
composition derivatives, `copyForStabilityTrial()` copies the six mutable component
derivative arrays at the active component count. Each copy owns its arrays; the attractive
term is rebound to the copied component. The calculation retains its existing `init(0)`
and `init(1)` sequence and root comparison.

This compact representation is restricted to the lifetime of the stability calculation.
Its component list must remain fixed, and its phases must never be installed in the live
user fluid. General `clone()` continues to preserve the source arrays' capacity and
independent ownership, including the full-capacity buffers of ordinary user fluids.
There is no shared workspace cache between flash calls. Mixing parameters retain the
same read-only sharing contract as normal phase cloning.

The compact path requires exact ordinary SRK/PR phase and component classes in every
storage slots, consistent component indices, and one or two active phases mapped to distinct
valid slots. Unknown subclasses, specialized models such as CPA, other mixing rules,
numeric derivatives, more than three stored or more than two active phases, locked or forced phase roles, chemical or
ionic systems, and solid, hydrate or wax options use their normal virtual clone behavior.
The flash operation's own solid-check option also bypasses compact copying even when the
system's solid-check flag is false.
`TPmultiflash.stabilityAnalysis()` also uses this qualified workspace for detached composition
seeds; it copies only seed values into live phases and never adopts compact phase objects.
This storage optimization does not broaden root-selection
policy, change convergence tolerances or replace accepted-candidate transfer with compact
phase objects. See [Root selectors and Newton inventory mapping](#root-selectors-and-newton-inventory-mapping)
for the separate rollback and adoption contracts.

`StabilityTrialCopyTest` checks compact buffer ownership and active values, ordinary clone
capacity, attractive-term rebinding, initialization equivalence, invalid capacities and
fallback for specialized or unqualified inputs, including independent buffers in a dormant third
slot and active mappings that use that slot. `TPflashConcurrentCloneIsolationTest`
exercises concurrent flashes on independently cloned default SRK, PR and Statoil CPA fluids,
including Cp, Cv and the second temperature derivative of the attraction term. Classic
mixing-rule calculations retain local accumulators for the qualified derivative, general
covolume and composition-dependent attraction evaluations. Legacy last-result fields remain
for compatibility; consumers use each initialized phase's own properties. Configuration must
be complete and unchanged during concurrent evaluation. This does not qualify arbitrary
mixing-rule implementations or concurrent parameter tuning. Performance qualification
must compare complete flashes at the same JVM settings and reject any change that fails
phase, material-balance or fugacity checks; reduced workspace capacity alone is not a
whole-flash speedup result.

### 5.6 Matched Stability-Workspace Qualification (2026-10-14)

This is the historical allocation qualification for commit `6d05316`, before the general electrolyte correction. It preserves the full public clone contract but does not establish allocation parity for every workload. Its then-unresolved electrolyte failures and measurements must not be read as validation of later solver changes.

This matrix supersedes the previous **36,136 → 152,488 B/flash** headline. Those figures are not the matched fixture/profile measured here and must not be used as the current comparison. The matched three-component dry-gas result is **18,104 → 76,280 → 19,544 B per complete flash** for master, original PR and redesign. The earlier 75,680 B figure excluded the 600 B operation constructor; this harness includes it. Any comparison with `dcc5105` remains historical and separate from this matrix.

| Warm changed workload | Master `d28b180` | Original PR `0239f31` | #4320 + #4329 baseline | Redesign | Redesign + #4320 + #4329 |
|---|---:|---:|---:|---:|---:|
| Dry gas, SRK | 18,104 | 76,280 | 18,112 | 19,544 | 19,552 |
| Two-phase methane/decane, PR | 4,864 | 4,864 | 4,872 | 4,864 | 4,872 |
| Condensate, SRK | 2,197,501 | 240,892 | 2,197,166 | 111,196 | 111,204 |
| Methane/water, CPA | 1,873,344 | 1,989,696 | 1,873,352 | 2,024,112 | 2,024,120 |
| Trace aqueous, SRK | 83,040 | 345,048 | 83,048 | 345,048 | 345,056 |
| Trace aqueous, CPA | 493,978,368 | 494,327,696 | 493,978,376 | 494,394,616 | 494,394,624 |
| Separator/compressor/cooler process | 553,592 | 2,415,224 | 553,632 | 1,110,296 | 1,110,336 |

Values are median current-thread allocated **bytes per complete operation**, across two JVM forks. The process value covers a full `ProcessSystem.run()` with changed live-feed temperature/pressure. It includes inlet separation, compression, cooling and outlet separation. Unchanged process samples retain normal equipment caching and are reported separately in the evidence.

Relative to the original PR, redesigned allocation falls **74.4% for dry gas, 53.8% for condensate and 54.0% for the changed process**. Dry gas remains 1,440 B above master. The process remains about 2.01 times master because general copies retain independently owned, full-capacity arrays. In that historical checkpoint the three-storage-slot aqueous SRK case fell outside the compact two-slot path. The current implementation qualifies a dormant third ordinary cubic slot and uses compact composition trials in `TPmultiflash`; updated comparison evidence is recorded in the PR. General public-copy ownership costs remain. Fixing CPA association-cache ownership adds 34,416 B to the methane/water case and 66,920 B to the trace-CPA case; those are correctness costs, not hidden speed claims.

The dry-gas accounting is exact: one stability system copy contains six component copies. Six independently owned 200-double arrays per component cost 58,176 B; the compact three-component workspace uses 1,440 B instead. Public clone capacity remains unchanged.

| Warm changed workload | Master CPU µs/op | Original PR CPU µs/op | Redesign CPU µs/op | Combined CPU µs/op |
|---|---:|---:|---:|---:|
| Dry gas | 21.3 | 29.4 | 20.6 | 18.1 |
| Condensate | 6,676.9 | 391.1 | 379.0 | 394.1 |
| Process | 795.6 | 1,143.3 | 901.9 | 831.6 |

These descriptive CPU medians use the second fork; optimized second forks ran after known numerical test workloads stopped. The first optimized fork overlapped integration tests and is retained without presenting its timing as an attributable regression. In particular, trace-CPA CPU medians were noisy: the original PR's second fork was 291.7 ms/op, redesign 313.6 ms/op and combined 271.1 ms/op. No universal speedup, statistical significance or timing threshold is asserted. Raw wall samples, p95 values and both CPU forks are retained.

All **1,980 first/last numerical snapshots** pass their conservation, phase-normalization, fugacity and finite-property gates, with no benchmark exceptions. Each of the five revisions has 396 snapshots covering cold changed, warm unchanged and warm changed sequences. The dry process has zero recorded mass-flow and energy-balance residuals; inlet and outlet remain gas. Power, duty, compositions, phase fractions, density, enthalpy and Gibbs energy are retained.

Against the original PR, the redesign preserves every phase count/type and every recorded total Gibbs energy. Only trace-CPA fractions differ at rounding scale: maximum beta difference **1.11e-16**, maximum phase-composition difference **4.34e-19**. The combined trial is **identical in all 396 matched endpoint snapshots** to the redesign. Against master, topology also matches; the largest differences are the already improved condensate solution: beta **1.65e-10**, composition **2.60e-11**, relative density **1.66e-12**, and relative Gibbs **2.76e-16**. The largest signed Gibbs increase is **3.64e-12 J**, indistinguishable from rounding here. There is no unexplained worse Gibbs endpoint in this matrix.

The method uses OpenJDK **17.0.20**, the standard `pom.xml` with `release 17`, identical 55 dependency artifacts, and `-Xms512m -Xmx512m -XX:+UseSerialGC`. The original head is `0239f31c2933751df29946bd78533d7d9d9acf78`; master is `d28b180969e248837e68b5e982374db69e11a376`; related heads are #4320 `0915f16e49e992d6c103618ad706d67fce20012e` and #4329 `515e21acbb776099836b06fb7318208726e28918`. Frozen redesign and combined production-source SHA256 deltas identify the measured candidates precisely. Java 8 source/API compatibility-profile tests are separate; these timings do not represent a Java 8 runtime.

Per fork and sequence, operations per batch / batches / warmup are: dry gas **128/5/4096**; two-phase **64/5/2048**; condensate **32/5/128**; CPA water, SRK trace and process **16/5/64**; expensive CPA trace **4/3/8**. Cold means a newly constructed fluid/process, not a cold JVM. Construction, boundary changes and snapshots are outside measurement. Flash construction and `run()` are inside it. Changed sequences alternate base conditions with T + 0.25 K and P × 0.995.

Snapshots run on public clones outside measurement. Historical implementations may refresh shared derivative caches at the same thermodynamic state, so their timing deserves caution; final clone isolation is covered by separate regressions. These are synthetic numerical checks, not experimental validation of an EOS. Named-wax correctness belongs to the separate combined integration suite, not this performance matrix.

Reproducibility evidence: [compressed qualification bundle](benchmarks/tpflash-allocation-qualification.json.gz) (SHA256 `685193c3424be39c28e4f3de4cec73bc97db9cc1b17b9714c0cdd2cd38c31f74`) retains all 70 raw JSON reports, complete endpoint snapshots, raw timing samples, fixture definitions, dependency coordinates/checksums, source hashes and a machine-readable summary. Its embedded manifest identifies all five builds. Absolute workspace paths, process identifiers and runtime access diagnostics are excluded.

---

### 5.7 General electrolyte checkpoint allocation qualification

A separate [reproducibility bundle](benchmarks/general-equilibrium-matched-java8.json.gz) records
matched Java 8 POM/dependency measurements against `6d05316`. It identifies the exact source and
class hashes of the 14-case checkpoint, including multiphase Newton, component cache ownership
and removal of the water-only shortcut (source aggregate `c14819bfb0df5f1bf77b10d09b6e93b58f20e769baf3a5cd7c548a8fd3629da0`).
The later EOS-GE capability guards are separately qualified and outside this benchmark snapshot.
Each measured operation includes physical checks.

| Warm complete flash | Base `6d05316`, bytes | General checkpoint, bytes | Qualification |
| --- | ---: | ---: | --- |
| Dry gas | 19,544 | 19,544 | Both endpoints valid and identical |
| Methane/brine | 5,991,776 | 26,710,048 | Both valid; allocation increases 4.46 times |
| Mixed gas/brine | 62,452,544 | 39,948,192 | Baseline fails physical closure; no qualified speedup claim |

The methane/brine topology is unchanged; maximum beta/composition differences are `3.8e-13` and
`3.1e-12`, with relative Gibbs difference `2.23e-11`. All 36 candidate snapshots pass; only 24 of
36 baseline snapshots pass. Shared-host timing is descriptive. The additional cost of constrained
ionic correctness is visible and needs separate performance work before claiming broad parity.

### 5.8 Integrated master qualification (2026-10-15)

The [integration evidence bundle](benchmarks/pr4306-integration-qualification.json.gz)
(SHA256 `65ba5857c815f4e017d8dfb222a83952c4e5c0a374073961f1a4d4f56439f7af`)
compares the integrated PR source with master `c93c8153fc66aa3a49b4d8fd4b51613c978341ae`.
It includes 67 raw reports, exact source/class/dependency hashes, fixture definitions and
commands. Compilation uses the Java 8 POM on Java 17; this is not an actual Java 8 runtime
measurement. The subsequent master `bc579afa` changes documentation only.

All 504 primary candidate snapshots and 120 additional settled candidate snapshots pass
their numerical checks. The baseline passes 468 of 504 primary snapshots: its 36 mixed-brine
snapshots fail molecular fugacity closure (maximum log residual about 208, versus about
`1.6e-14` for the candidate). Invalid baseline endpoints do not establish a speedup. Other
valid baseline endpoints retain their topology and Gibbs energy within numerical rounding.
These checks establish numerical closure, not experimental salting-out accuracy; the four
physical salting-direction failures tracked in issue #4334 remain merge blockers.

| Warm changed-state workload | Master allocated bytes | Candidate allocated bytes |
| --- | ---: | ---: |
| Dry gas | 18,112 | 19,552 |
| Ordinary two-phase | 4,872 | 4,872 |
| Condensate | 2,230,998 | 108,192 |
| Methane/brine | 11,554,616 | 12,690,536 |
| Trace aqueous SRK | 83,048 | 175,144 |
| Gas process | 553,630 | 1,110,337 |

The initial aqueous diagnostic is omitted from ordinary flashes because phase selection does not
use it; the acceptance-time ionic diagnostic remains. A subsequent diagnostic gate retains the
initial trials for positive ionic inventories configured with `setHydrateCheck(true)`, preserving
the homogeneous-reference evidence required by hydrate consumers. That gate is tested separately
from the frozen benchmark bundle; its fixtures use `hydrateCheck=false`, so their measured path is
unchanged. Matched isolation trials reduce methane/brine allocation by 55.7%
and water-rich CPA allocation by 81.6% against the unoptimized candidate, with identical
endpoints. Qualifying the dormant third cubic slot and the multiphase composition-trial call
reduces trace-SRK candidate allocation from 345,064 to 175,144 bytes. Public clones retain
independently owned full-capacity arrays, including their remaining process allocation cost.

Timing is descriptive on a shared host. The primary condensate median thread CPU falls from
7.784 to 0.529 ms; dry gas rises from 22.8 to 28.4 microseconds. Longer warmup checks resolve
water-rich CPA at 1.420 versus 1.419 ms (9.0% more bytes), and the gas process at 1.149 versus
1.237 ms (7.7% more CPU, with substantial between-fork variability). This is a demonstrated
improvement in selected correctness and allocation contracts, not universal performance parity.

## 6. State-of-the-Art Comparison and Recommendations

### 6.1 Current Position

NeqSim combines Michelsen tangent-plane-distance stability analysis, Rachford-Rice
phase-fraction solution, successive substitution, acceleration and Newton
refinement. The primary-literature comparison below identifies both established
foundations and newer research methods. It does not establish equivalence to
undisclosed commercial implementations or a global convergence guarantee.

| Area | Current NeqSim implementation | Research and implementation comparators |
|------|-------------------------------|------------------------------|
| Two-phase VLE | Wilson K, Rachford-Rice, SSI, DEM, Newton refinement | Same core sequence, typically with extensive fallback telemetry |
| Rachford-Rice | Nielsen 2023 reformulation with Michelsen 2001 option | Robust transformed formulations with bracketing safeguards |
| Stability analysis | Michelsen TPD, Wilson K trials, amplified K retry, pure-component trials | Systematic multi-start TPD minimization with traceable trial outcomes |
| Multiphase flash | Phase addition plus Q-function minimization and cleanup | Broader phase-set search and final stability retesting; local multistart searches alone cannot certify a global minimum |
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
  non-finite material residual or one above `1e-8` may use the bounded existing-phase beta repair below that threshold;
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
| Missing aqueous phase near a bubble point (#3955) | Public-API 23-component SRK/classic pump inlet; all 81 temperature/pressure cells from 11–31 °C and 1.8–5.0 bara; warm starts, repeats, aliased inactive phase slots, and phase-role lock | Retain AQUEOUS with normalized phases, `1e-8` component/fugacity closure, lower Gibbs energy on recovery, and positive pump work with less than 1 K temperature rise; preserve locked states. Additional regressions cover 0.7 mol% water, an undersaturated `1e-8` water feed, and zero candidate clones at or below the numerical seed floor |
| Documentation drift | Algorithm doc parameter table versus source constants | Stale thresholds are detected during review |

The aqueous consistency regression additionally checks fugacity and material closure for every
ordinary, explicit-multiphase, poor-guess and repeated endpoint. At 230 K and 120 bara, its
normalized water fraction is `0.01 / 1.001`, just below the `0.01` refinement threshold.
Previously a material-balanced multiphase result bypassed final refinement despite a
`7.93e-5` log-fugacity residual, leaving enthalpy at `-7316.423487690394` J instead of the
ordinary result `-7316.421704183054` J. The cheap cached-residual gate now requires both
material balance and fugacity equality before skipping refinement. The existing bounded
beta update reduces the multiphase residual to `8.09e-10` and enthalpy to
`-7316.421704183606` J, with the same active phases and roots. Valid low-water endpoints
perform no additional initialization, stability search or beta solve; numerical tolerances
and the three-update bound are unchanged.

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

The phase-reuse follow-up additionally covers ordinary and enhanced PR/classic flashes near
the two-liquid boundary. Cloned components must own their fugacity-derivative and EOS work
arrays: shared arrays corrupted the analytical Hessian when a second liquid was initialized.
`ComponentCloneDerivativeIsolationTest` checks both independent storage and all nine entries
of a three-component Hessian against central differences at fixed configured roots. Enhanced
stability trials copy their configured root into the recycled phase slot only when enhanced
checking is explicitly requested. Rollback always preserves the configured selector independently
from the resulting GAS/OIL classification. Reading the stored selector also removes two cloned
root-inference evaluations per copied phase.

Implicit supplementary stability trials retain the established local-root policy for every model.
A trial selector is an initial search choice, not a guarantee that the subsequent coupled
phase-fraction iteration converges on that branch. Propagating the selector unconditionally
produced a non-equilibrated, higher-Gibbs split in the PR methane/heptane case at 194 K and
78.5 bara, which final cleanup then collapsed to one oil phase. It similarly lost the SRK
CO2/methane/ethane/water oil/aqueous state at 220 K and 200 bara. Retaining the implicit policy
restores the independently qualified PR gas fraction `0.1581255471` and the SRK oil fraction
`0.907774808238`, without changing their numerical tolerances. The ordinary, multiphase,
repeat-state, nearby-state, sour-gas and explicit-enhanced scrubber regressions cover this
compatibility boundary.

This is a bounded correction of the new trial-root propagation, not a general root-selection
solution. UMR-PRU-MC also exposes an incipient phase with reduced TPD `-5.38e-7`, below the
ordinary supplementary solve's `1e-6` resolution floor, and a Gibbs change of only `4.2e-15` J.
Unifying root selection and convergence policy across implicit and explicit enhanced modes
remains part of [#4310](https://github.com/equinor/neqsim/issues/4310). The UMR-PRU incipient-phase
expectations and nearby-state tests remain unchanged. No default TPD threshold is altered.

Accepted dry sour two-phase states with at least 0.05 CO2, 0.20 H2S, 0.30 combined acid gas,
and some hydrocarbon receive at most three local Newton corrections when their log-fugacity
residual remains above `1e-12`. The existing balance and equilibrium screen must pass first.
Nonfinite, infeasible, root-changing, nonconvergent or Gibbs-increasing corrections restore
the original mappings, configured roots, phase fractions, compositions and K values. This
does not add a new stability search. The general Newton line-search safeguards are described
in Section 1.5; the sour-endpoint polishing remains a separate, bounded correction.

Independent PR cubic-root and log-fugacity equations give the following methane-rich phase
fractions for the same normalized feed and classic interaction parameters (methane/CO2 0.0973,
methane/H2S 0.08, CO2/H2S 0.12). Both retained roots are the liquid roots. The corrected
references retain the original `1e-10` beta tolerance; the tests also require log-fugacity
closure within `1e-12`.

| Temperature (K) | Pressure (bara) | Previous underconverged beta | Independent converged beta |
| --- | ---: | ---: | ---: |
| 245 | 100.80 | 0.330402477242444 | 0.3304026330996995 |
| 225 | 95.81 | 0.572813112145268 | 0.5728114916364095 |

The independent equation residuals are below `4e-15`; component closure is below `6e-17`.
This is EOS consistency evidence, not experimental validation. Original sulfur-filter,
scrubber saturation and exchanger regression expectations remain unchanged.

The following historical diagnostics use different fixtures from the matched master/original-PR/redesign matrix in [Section 5.6](#56-matched-stability-workspace-qualification-2026-10-14). They are retained for provenance and are superseded for current allocation comparisons.

The correctness fix adds allocations. A diagnostic on Java 17 with a fixed 512 MiB heap,
seven batches of 128 full flashes and 256 warmups (4096 for dry and seed-floor controls)
compared the previous `518867a` flash/component classes with the repaired classes. All
70 cold/warm numerical snapshots match exactly. Median warm CPU and allocation changed as
follows; shared-host timing is diagnostic and does not establish performance parity.

| Workload | CPU before/after (microseconds/flash) | Allocation before/after (bytes/flash) |
| --- | ---: | ---: |
| Dry gas | 71 / 95 | 36,136 / 152,488 |
| Seed-floor water | 200 / 266 | 68,000 / 300,704 |
| Undersaturated water | 298 / 325 | 90,680 / 400,952 |
| Dilute aqueous | 258 / 460 | 101,680 / 334,384 |
| Three phase | 255 / 446 | 102,176 / 334,880 |

A separate three-component clone diagnostic measured 11.9/18.0 microseconds and
14,544/72,720 bytes per clone. Complete cold sour flashes, including the clone, measured
9.07/8.57 ms at 245 K and 14.17/9.67 ms at 225 K (seven batches of 64, 256 warmups).
The 240 K control increased from 148 to 183 microseconds. Root-copy simplification removes
redundant work, but deep ownership of public mutable derivative buffers has a measurable
cost. No universal speedup or representative-process throughput parity is claimed.

A subsequent diagnostic compared PR head `dcc5105` with the repaired Newton
line-search and explicit-enhanced-root candidate. Both variants used Java 17, a
fixed 512 MiB heap, five batches of 64 complete flashes, and 128 warmups per case
(4096 for dry gas, hot gas and ordinary two-phase controls). Construction, state
changes and numerical snapshots remained outside the measurement interval.
Each variant passed all 70 numerical snapshots. The 40 dry-gas, hot-gas,
ordinary two-phase and CPA-water snapshots were identical. The 30 condensate
snapshots retained phase topology and passed the same material-balance,
normalization and fugacity gates; maximum differences were `1.64e-10` in beta,
`2.56e-11` in component mole fraction and `3.64e-12 J` in Gibbs energy. This is
numerical consistency evidence, not experimental validation.

| Condensate workload | Median CPU before/after (ms/flash) | Allocation before/after (bytes/flash) |
| --- | ---: | ---: |
| Ordinary, unchanged state | 7.426 / 1.230 | 1,826,735 / 244,396 |
| Ordinary, changed state | 15.192 / 0.884 | 3,590,504 / 240,068 |
| Cold, unchanged state | 8.767 / 0.803 | 1,823,688 / 244,440 |
| Cold, changed state | 10.440 / 0.848 | 3,580,979 / 240,116 |
| Multiphase, unchanged state | 12.734 / 2.960 | 2,930,784 / 684,256 |
| Multiphase, changed state | 13.090 / 2.985 | 2,827,956 / 679,388 |

The corrected trial objective reduced allocation in these condensate workloads
by 76–93%, while preserving independent derivative-array ownership. Dry-gas,
hot-gas and ordinary two-phase control allocations were identical; CPA-water
allocation differed by at most 28 bytes on approximately 1.99 MB per flash.
Timing varied substantially between identical baseline JVM runs and remains
diagnostic. These measurements do not establish universal speed improvement or
performance parity against master, and do not replace the preceding evidence
of clone-allocation overhead. Avoiding rejected recovery work for undersaturated
trace water remains tracked separately in
[issue #4311](https://github.com/equinor/neqsim/issues/4311).

`TPflashEndpointRecoveryBenchmark` now records optional per-thread allocation
alongside elapsed time, thread CPU time and numerical snapshots. Its
`threadAllocationCountingEnabled` field identifies whether allocation accounting
is available; `bytesPerFlash` is `-1` when the JVM lacks or denies that feature.
Allocation-counter reads remain outside the elapsed and CPU timing intervals.

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
restart to dry flashes. A locked-phase regression invokes the bounded restart guard directly and requires the phase-role
lock, logical-to-physical phase mapping, equilibrium state, and frozen numerical gates to remain unchanged.

The focused class executes 35 complete flashes plus one direct locked-phase guard invocation. That bounded count is the
performance evidence for this test-only qualification; no wall-clock threshold or production speedup is claimed.
Production solver code, public APIs, model
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

### 6.4.12 UMR-PRU trace oil-dropout lifecycle qualification

The synthetic qualification uses `SystemUMRPRUMCEos` with the `HV` /
`UNIFAC_UMRPRU` mixing rule. Its normalized 24-component lean-gas feed contains
nitrogen, carbon dioxide, methane through n-pentane, both methylpentane isomers,
normal and cyclic C6-C8 components, benzene, toluene, m-xylene, and normal C9-C12
heavy ends. The qualified range is 281.15-293.15 K (8-20 °C) at 77-79 bara.
This is deterministic numerical evidence after component and UNIFAC-table repairs,
not experimental PVT validation of UMR-PRU parameters or the predicted dew point.

The established 78 bara oil-beta anchors are `9.144142e-4` at 8 °C,
`3.502455e-4` at 18 °C, and `2.582079e-4` at 20 °C, each with an absolute
tolerance of `5e-6`. Oil dropout must increase monotonically as temperature
decreases from 20 to 8 °C. Ordinary and multiphase public TP flashes must recover
the same GAS+OIL equilibrium at the three anchor temperatures.

Every qualified state requires finite bounded phase fractions and compositions,
beta and phase normalization within `2e-12`, component material balance below
`1e-10`, and maximum comparable interphase log-fugacity residual below
`1e-8`. Each phase must have positive finite compressibility, while total Gibbs
energy and enthalpy must remain finite. The regression also qualifies recovery
from beta values within `1e-12` of a bound, nearby pressures, a changed
temperature/pressure state, return to the reference state, and an immediate
deterministic repeat. Gas and liquid roles are matched explicitly so phase-array
ordering cannot hide a lifecycle mismatch.

The focused class performs 31 complete public TP flashes. This fixed workload is
performance evidence only; no wall-clock threshold or production speedup is
claimed. Production solver code, public APIs, model parameters or defaults,
experimental parameter validation, saturation search, electrolyte/reaction
models, solids/wax, Column Solver, Process Performance, proprietary data, and
Huldra are outside this tranche.

### 6.4.13 Incipient phase appearance and disappearance lifecycle qualification

The phase-boundary qualification covers two synthetic numerical cases. A classic-mixing
`SystemSrkEos` feed of methane, ethane, propane, and n-butane at
253.46685189059752 K and 77.53775411226596 bara must retain an incipient vapour
fraction of `3.508682096277057e-5` through the supplementary stability trial. A
classic-mixing `SystemUMRPRUMCEos` feed of methane, ethane, n-pentane, and nC16
at 293.15 K must retain a stable single phase at 90.03461693 and 90.5 bara.
The ordinary and multiphase paths must agree while the sub-residual TPD guard
retains the reference single phase. The 89.5 bara trial is outside this qualified
envelope because its Windows endpoint does not satisfy the closure gates. Both
feeds are synthetic and provide numerical
regression evidence rather than experimental validation of phase boundaries or
model parameters.

The SRK reference was independently recomputed with 70-digit decimal arithmetic,
using the configured pure-component attraction/covolume parameters and classic
binary interactions. A separate solver evaluates the SRK cubic and log-fugacity
equations, brackets the physical volume roots, and solves equilibrium with a
numerical Jacobian. It obtains beta `3.508682096277057e-5`, maximum log-fugacity
residual below `8e-67`, and material/normalization residuals below `1e-69`.
The previous reference `3.50882832337307e-5` was underconverged by `1.46e-9`
in beta. The corrected Newton result differs from the independent value by
`1.57e-11`, within the unchanged `1e-10` beta tolerance. The exact-state regression
also requires log-fugacity closure within `1e-10` for both public flash paths.
The extra arithmetic digits describe equation-solving precision; the exported
EOS parameters retain double precision and this is not experimental validation.

The reference uses `R = 8.3144621 J/(mol K)` and the following parameters at
the stated temperature. The SI values are the exported `getaT()` and `getb()`
values multiplied by `1e-5`; pressure is converted from bara to Pa before forming
`A = a P/(R T)^2` and `B = b P/(R T)`.

| Component | Normalized feed fraction | a(T), Pa m6/mol2 | b, m3/mol |
| --- | ---: | ---: | ---: |
| Methane | 0.5833884211682983 | 0.19906334237169536 | 2.984851232640916e-5 |
| Ethane | 0.1647535915704123 | 0.63104267304609966 | 4.514423831995930e-5 |
| Propane | 0.19866217294783828 | 1.2003495539713910 | 6.271508067756126e-5 |
| n-Butane | 0.05319581431345125 | 1.9575721924885706 | 8.067513283649740e-5 |

Symmetric classic binary interactions, with zero diagonal, are methane/ethane
`0.00295295`, methane/propane `0.00747722`, methane/n-butane `0.01289789`,
ethane/propane `0.00185286`, ethane/n-butane `0.00464288`, and
propane/n-butane `0.00132268`. The mixing rules are
$a=\sum_i\sum_j x_i x_j\sqrt{a_i a_j}(1-k_{ij})$ and
$b=\sum_i x_i b_i$. The independent cubic is
$Z^3-Z^2+(A-B-B^2)Z-AB=0$; its physical roots are
`0.28907489265639535` for liquid and `0.59614803621601418` for vapour.


Every active phase and beta must be finite, bounded, and normalized within
`3e-12`; maximum component material-balance residual must be below `1e-10`;
and every phase must have positive finite compressibility. Two-phase states require
maximum comparable interphase log-fugacity residual below `1e-8`. Single-phase
states require beta one and `x=z` within `1e-10`. Total Gibbs energy and
enthalpy must remain finite.

Ordinary and multiphase public TP flashes must agree for both boundary roles. The
SRK stability diagnostic must continue to report the supplementary unstable trial.
Both cases must recover from beta values within `1e-12` of a bound. Reused
systems are moved to a nearby pressure, compared with fresh calculations, returned
to the reference pressure, and flashed again; the changed and returned states must
remain equivalent within `1e-8`, while the incipient-vapour immediate repeat uses
the measured cross-platform beta bound of `2e-9`.

The focused class performs 21 complete TP flashes. That fixed workload is
performance evidence only; no wall-clock threshold or production speedup is
claimed. Production stability algorithms, public APIs, model parameters and
defaults, saturation operations, electrolyte-performance ownership, solids/wax,
Column Solver, Process Performance, proprietary data, and Huldra are outside this
tranche.

### 6.4.14 UMR-CPA methane-water dehydration lifecycle qualification

The synthetic dehydration qualification uses `SystemUMRCPAEoS` with the `HV` /
`UNIFAC_UMRPRU` mixing rule and a normalized feed of 0.98 methane and 0.02 water.
Its local envelope is 283.15-313.15 K and 30-120 bara. The 298.15 K / 70 bara
reference retains the existing paper-facing gas-water mole-fraction envelope of
`1e-5` to `5e-3`. This is deterministic numerical qualification of the public
flash path, not independent experimental validation or a re-fit of UMR-CPA,
association, alpha-function, or UNIFAC parameters.

Every state must contain GAS and AQUEOUS phases with finite bounded phase fractions
and compositions. Beta and phase compositions must normalize within `3e-12`,
maximum component material-balance residual must remain below `1e-10`, and the
maximum methane/water interphase log-fugacity residual must remain below `1e-8`.
Both phases require positive finite compressibility, and total Gibbs energy and
enthalpy must remain finite.

The temperature matrix requires gas-phase water content to increase from 283.15 to
313.15 K at 70 bara. The pressure matrix requires it to decrease from 30 to 120
bara at 298.15 K. Ordinary and explicit-multiphase public TP flashes must agree
throughout both matrices. The regression also qualifies recovery from beta values
within `1e-12` of a bound, a changed temperature/pressure state, return to the
reference state, and an immediate deterministic repeat.

The focused class performs 20 complete public TP flashes. This fixed workload is
performance evidence only; no wall-clock threshold or speedup is claimed. TEG
derivative behavior, model or data parameter changes, saturation operations,
electrolyte/reaction models, public API and serialization changes, Column Solver,
Process Performance, proprietary data, and Huldra are outside this tranche.

### 6.4.15 PC-SAFT methane/n-hexane phase-split lifecycle qualification

The synthetic PC-SAFT qualification uses `SystemPCSAFT`, mixing rule 1, and an
equimolar methane/n-hexane feed. Its local phase-split envelope is 248-252 K and
9-11 bara. At 250 K and 10 bara, the corrected total heat-capacity reference is
`219.08347126863538 J/K` with an absolute tolerance of `0.1 J/K`. This is
deterministic numerical qualification of the current Java calculation, not
independent experimental validation or a re-fit of PC-SAFT parameters.

Every qualified state must contain GAS and OIL phases with finite bounded phase
fractions and compositions. Beta and phase compositions must normalize within
`3e-12`, maximum component material-balance residual must remain below
`1e-10`, and the maximum methane/n-hexane interphase log-fugacity residual must
remain below `1e-8`. Both phases require positive finite compressibility, and
total Gibbs energy and enthalpy must remain finite.

Ordinary and explicit-multiphase public TP flashes must agree throughout the nearby
temperature and pressure matrices. The regression also qualifies recovery from
beta values within `1e-12` of a bound, a changed temperature/pressure state,
return to the reference state, and an immediate deterministic repeat.

The focused class performs 20 complete public TP flashes. This fixed workload is
performance evidence only; no wall-clock threshold or speedup is claimed. PC-SAFT
parameter or association changes, petroleum characterization, PH/PS operations,
process equipment, saturation operations, public APIs, MCP payloads, Column
Solver, Process Performance, proprietary data, and Huldra are outside this
tranche.

### 6.4.16 SAFT-VR Mie fresh-feed lifecycle qualification

The synthetic SAFT-VR Mie qualification uses `SystemSAFTVRMie`, the classic
mixing rule, and a feed of 0.60 methane and 0.40 n-butane at 200 K and 250 K
and 30 bara. It compares direct `TPflashSAFT` execution with public
`ThermodynamicOperations.TPflash()` dispatch after merged PR #3831 corrected
fresh-feed initialization. This is deterministic numerical qualification, not
independent validation or a re-fit of SAFT-VR Mie parameters.

Every qualified state must contain GAS and OIL phases with finite bounded phase
fractions and compositions. Beta and phase compositions must normalize within
`5e-12`, maximum component material-balance residual must remain below
`1e-10`, and the maximum methane/n-butane interphase log-fugacity residual
must remain below `1e-6`. Both phases require positive finite compressibility,
and total Gibbs energy and enthalpy must remain finite.

Fresh and explicitly initialized feeds, and direct and public dispatch, must
agree within `1e-8`. The regression also qualifies recovery from beta values
within `1e-12` of a bound, a reused 200 K to 250 K state change, return to the
200 K state, and an immediate deterministic repeat.

The focused class performs 14 complete TP flashes. This fixed workload is
regression evidence only; no wall-clock threshold or speedup is claimed.
SAFT-VR Mie derivatives or parameters, saturation and phase-envelope
operations, electrolytes, public APIs, process equipment, proprietary data,
and Huldra are outside this tranche.

The fugacity gate matches the production `TPflashSAFT` stopping criterion of
`1e-6` relative K-value change. Windows Java 21 recorded residuals up to
`3.66e-7`; the earlier `1e-8` test threshold required precision beyond that
contract. This qualification does not change solver tolerances or claim
`1e-8` equilibrium closure. The final Rachford-Rice solve now uses the
accepted K-values rather than the preceding K iterate, removing the former
`5.51e-8` stale-beta inventory residual without another fugacity evaluation.

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


### Direct GERG-2008 gas flash lifecycle

The direct GERG-2008 regression uses `SystemGERG2008Eos` with the standard
GERG-2008 model and the following synthetic molar feed:

- methane: 0.90
- ethane: 0.05
- propane: 0.03
- carbon dioxide: 0.02

The qualification covers 300-350 K and 50-100 bara. It activates the two
historically disabled direct-flash checks and exercises an initial state, changed
temperature, changed pressure, an independently constructed changed state,
return to the initial state, deterministic repeat, and complete thermodynamic
property access.

Every state must remain a single GAS phase with beta and composition
normalization within `5e-12`, component material balance and `x=z` below
`1e-10`, positive finite component fugacity coefficients, compressibility,
density, heat capacities, and sound speed, and finite energy and
Joule-Thomson outputs. The regression also requires density to decrease when
temperature rises at fixed pressure and increase when pressure rises at fixed
temperature. Fresh and reused calculations must agree within a `1e-8`
relative budget; the unchanged-state repeat uses `1e-10`.

This is synthetic numerical and state-cache qualification. Existing
direct-versus-SRK GERG property checks provide supplementary implementation
comparison, but this matrix does not independently validate GERG parameters,
multiphase GERG behavior, transport properties, phase envelopes, or process
equipment and makes no wall-clock performance claim.

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

### Recent algorithm comparisons and scope

The October 2026 review compares the implementation with the following primary
literature. These references identify numerical requirements and useful methods;
they do not establish that every method below is implemented or that the
thermodynamic parameters are experimentally qualified.

| Topic | Primary source | Consequence for implementation and qualification |
| --- | --- | --- |
| Newton globalization | *A Line-Search-Based Algorithm for Multiphase Flash Calculations with CO2–Hydrocarbon System* (2024), [doi:10.1021/acsomega.4c01394](https://doi.org/10.1021/acsomega.4c01394) | Modified Cholesky and line search provide a useful comparator. NeqSim's relative diagonal perturbation is not modified Cholesky and does not guarantee a positive-definite Hessian. A finite descent direction, feasible step and recomputed accepted-state residual are separate requirements. |
| Charge-constrained phase splitting | Nikolaidis, Novak, Kontogeorgis and Economou (2022), *Rigorous Phase Equilibrium Calculation Methods for Strong Electrolyte Solutions: The Isothermal Flash*, [doi:10.1016/j.fluid.2022.113441](https://doi.org/10.1016/j.fluid.2022.113441) | Ion partitioning must respect each phase's electroneutrality. Electrochemical potentials, or an independent basis of charge-neutral transfers, define stationarity; equality of individual ionic chemical potentials is generally incorrect. |
| Electrolyte stability and trace amounts | Perdomo, Jackson, Mitsos, Galindo and Adjiman (2025), *Phase stability criteria and fluid-phase equilibria in strong-electrolyte systems*, [doi:10.1016/j.compchemeng.2024.108977](https://doi.org/10.1016/j.compchemeng.2024.108977) | The stability problem must use the same electroneutral feasible set as phase equilibrium. This work concerns fully dissociated electrolytes; it does not qualify reactive phase disappearance. |
| Stability information and initialization | Heringer, Wapperom, Secuianu, Voskov and Nichita (2026), *New initialization procedures from phase stability testing in three-phase flash calculations for CO2-hydrocarbon mixtures*, [doi:10.1016/j.fluid.2025.114653](https://doi.org/10.1016/j.fluid.2025.114653) | Stationary trial compositions, including positive and trivial TPD minima, can supply additional split seeds. A seed is not proof of a stable phase. Their reported speed improvements apply to their tested mixtures and algorithms, not automatically to NeqSim. |
| Reactive multiphase equilibrium | Tsanas, de Hemptinne and Mougin (2022), *Calculation of phase and chemical equilibrium for multiple ion-containing phases including stability analysis*, [doi:10.1016/j.ces.2021.117174](https://doi.org/10.1016/j.ces.2021.117174) | Reaction invariants, phase charge and phase stability must be satisfied together. Accelerating alternating chemistry and phase flashes alone does not establish simultaneous reactive stability. |

Numerical qualification checks the actual equation-of-state pressure at the
returned volume, conservation, phase charge, molecular or electrochemical
equilibrium as applicable, and stability evidence. Small volume steps or small
changes in the objective alone are insufficient. Finite-difference derivative
checks use independently perturbed states and the same thermodynamic constraints
as the derivative being tested. Model accuracy additionally requires independent
measurements with matching temperature, pressure, concentration basis and
standard state. Calibration and held-out validation data must be identified
separately.

Performance comparisons require these correctness conditions for both builds,
identical fixtures and dependencies, and repeated fresh JVMs. Invalid or failed
baseline calculations remain failures and cannot count as faster successful
solutions. Improvements in one hydrate or trace-composition case do not imply a
universal speedup across thermodynamic models.

---

## Review qualification (2026-10-15)

The [follow-up qualification bundle](benchmarks/pr4306-followup-qualification.json.gz)
(SHA256 `607aaba23d61b88f878b7fdbe4342178e3c71f7fb3c8446cba49cd547149d5ab`) records **496 tests across 84 distinct classes** on one frozen
source/class/resource/dependency state: 494 passed, zero failed or errored, and two
pre-existing tests remained disabled. The primary execution contains 494 tests in
83 classes. Its requested selector `MultiphaseInitializationTest` was a typo;
the unchanged command and unmatched selector are preserved. The correctly named
`SystemThermoMultiphaseInitializationTest` then passed its two tests separately
against the same frozen bytecode. These are disjoint executions, not a claim that
the original request executed all 84 classes.

The two disabled tests are the existing GUI display test and
`CO2WaterPHTest.testCO2WaterAcidicPH`; neither is counted as passing. The bundle
retains raw XML and logs, the original command, the explicit selector correction,
34 evidence-integrity checks, primary-literature/data audits, and earlier failed
runs separately. Earlier or isolated-agent totals are not added to final coverage.
The pretest manifest SHA256 is `9a2d7caaf89791ed20711a195b81573b84fcaf26dcb3a3b194b5750a008eb905`.

All 45 follow-up production files and 52 follow-up test files compile with
`--release 8`. Execution used Java 17 with the Java 8 POM. Full Javadoc, 18
Python documentation tests, documentation search, direct formatter apply/check,
engineering coverage and whitespace checks pass. Hosted Java 8/21 execution and
final matched performance results are separate gates; these local results do
not substitute for them or prove global phase stability or universal model accuracy.

---

## Implementation Files

| File | Description |
|------|-------------|
| [TPflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/TPflash.java) | Two-phase flash with SSI and Newton |
| [TPmultiflash.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/TPmultiflash.java) | Multi-phase flash with stability analysis |
| [FlashPhaseState.java](https://github.com/equinor/neqsim/blob/master/src/main/java/neqsim/thermodynamicoperations/flashops/FlashPhaseState.java) | Package-private retained-split restoration and accepted-phase transfer |
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


### UMR-PRU maintained-group flash lifecycle

The maintained `UNIFACcompUMRPRU.csv` table supplies the group decompositions used by
`SystemUMRPRUMCEos` with the `HV` / `UNIFAC_UMRPRU` mixing rule. Components without a
valid decomposition can otherwise propagate non-finite activity coefficients into a flash. The
component-table repair therefore has a separate TP-flash lifecycle qualification for newly
resolvable component families.

The synthetic matrix covers the following nominal states. Mole amounts are normalized by the
thermodynamic system; pressure is absolute.

| Case | Components and mole amounts | Temperature (K) | Pressure (bara) |
| --- | --- | ---: | ---: |
| Aromatic heavy end | methane 0.90, propylbenzene 0.10 | 298.15 | 10.0 |
| Cyclic light hydrocarbons | methane 0.80, c-propane 0.10, c-C4 0.10 | 285.15 | 20.0 |
| Hydrogen and inert gas | hydrogen 0.10, argon 0.02, methane 0.83, n-hexane 0.05 | 280.15 | 50.0 |
| Substituted naphthenes | methane 0.80, n-Bcychexane 0.10, Pent-CC6 0.10 | 310.15 | 15.0 |

The bounded 32-flash regression compares ordinary and explicit-multiphase calculations, starts
from beta values within `1e-12` of a bound, changes temperature by 1 K and pressure by 2%, returns
to the nominal state, and repeats the settled calculation. Acceptance requires phase and beta
normalization within `5e-12`, component material balance below `1e-10`, comparable interphase
log-fugacity residual below `1e-8`, bounded finite compositions and fractions, positive
compressibility, and finite Gibbs energy and enthalpy. Single-phase states additionally require
`beta = 1` and `x = z`; the matrix must exercise at least two multiphase states.

These public synthetic fluids qualify numerical closure and lifecycle behavior after the group-table
repair. They do not independently validate UMR-PRU interaction parameters, phase-envelope accuracy,
or experimental PVT predictions. No solver algorithm, public API, table entry, model default, or
wall-clock performance claim is introduced by this qualification.


## Add-fluid order-invariance qualification

A bounded synthetic SRK regression qualifies TP-flash behavior when two independently constructed
fluids are combined in opposite orders and a petroleum pseudo-component exists only in the
hydrocarbon-liquid input. The public case combines methane (0.50 mol) and ethane (0.05 mol) with
n-heptane (0.40 mol) and a synthetic `C10_PC` TBP fraction (0.05 mol, molar mass 0.142 kg/mol,
relative density 0.82) at 280 K and 28–32 bara using the classic mixing rule.

The regression executes both ordinary and multiphase-enabled `TPflash()` paths. It compares feed
inventories and phase compositions by component name rather than array position, resolves phases by
`PhaseType`, starts from beta values within `1e-12` of a bound, changes and restores pressure on
reused systems, and repeats the settled calculation. Acceptance requires GAS+OIL topology, beta and
composition normalization within `1e-12`, component material balance below `1e-10`, maximum
comparable interphase log-fugacity residual below `1e-8`, and order-to-order beta and composition
agreement within `1e-10`. Compressibility must be finite and positive, and Gibbs energy and
enthalpy must be finite.

This is numerical qualification of a public synthetic case related to issue #1362. The original
Matlab/private-fluid composition is unavailable, so the regression neither reproduces that case nor
establishes general order invariance for arbitrary pseudo-component characterizations. It changes no
`addFluid` API, pseudo-component characterization, mixing rule, solver tolerance, or model default.
The bounded flash count is workload evidence only; no wall-clock performance claim is made.


### Expanded UMR-PRU component flash lifecycle

The standard component and `UNIFACcompUMRPRU.csv` tables were expanded together so
`SystemUMRPRUMCEos` can use additional chromatographic hydrocarbon families through the
`HV` / `UNIFAC_UMRPRU` mixing rule. Database-integrity and finite-density checks establish
that the rows load, but they do not qualify complete TP-flash equilibrium or state reuse. The
expanded-table qualification therefore applies the common lifecycle gates to representative new
aromatic, cyclic, branched-paraffin, and olefin entries.

The synthetic matrix uses the following nominal states. Mole amounts are normalized by the
thermodynamic system, and pressure is absolute.

| Case | Components and mole amounts | Temperature (K) | Pressure (bara) |
| --- | --- | ---: | ---: |
| Aromatic | methane 0.90, 1,2,4-trimethylbenzene 0.10 | 298.15 | 10.0 |
| Cyclic | methane 0.80, 1,1,2-trimethylcyclopentane 0.10, trans-1,3-dimethylcyclohexane 0.10 | 285.15 | 20.0 |
| Branched paraffin | methane 0.85, 2,4-dimethylheptane 0.15 | 280.15 | 30.0 |
| Olefin | methane 0.88, 1-hexene 0.12 | 285.15 | 20.0 |

The bounded 32-flash regression compares ordinary and explicit-multiphase calculations, starts
from beta values within `1e-12` of a bound, changes temperature by 1 K and pressure by 2%, returns
to the nominal state, and repeats the settled calculation. Acceptance requires phase and beta
normalization within `5e-12`, component material balance below `1e-10`, comparable interphase
log-fugacity residual below `1e-8`, bounded finite compositions and phase fractions, positive
compressibility, and finite Gibbs energy and enthalpy. Single-phase states additionally require
`beta = 1` and `x = z`; the matrix must exercise at least two multiphase endpoints.

These public synthetic fluids qualify numerical closure and lifecycle behavior for the expanded
tables. They do not independently validate component-property corrections, UMR-PRU group or
interaction parameters, phase-envelope accuracy, experimental PVT predictions, or the
missing-interaction policy tracked by issue #3727. No solver algorithm, public API, table entry,
model default, or wall-clock performance claim is introduced.


### Translated Peng-Robinson flash lifecycle

The translated Peng-Robinson system (`SystemPrEosvolcor`) solves the ordinary PR cubic before
applying its volume shift. This preserves mechanically stable liquid roots whose translated molar
volume is below the untranslated co-volume. Level-zero phase initialization also refreshes the
translation properties used by a subsequent flash. These shared phase changes support phase-envelope
work, but TP-flash equilibrium and state reuse are qualified independently.

The bounded synthetic qualification uses the classic mixing rule and the following nominal states.
Mole amounts are normalized by the thermodynamic system, and pressure is absolute.

| Case | Components and mole amounts | Temperature (K) | Pressure (bara) |
| --- | --- | ---: | ---: |
| Large volatility | methane 0.90, n-heptane 0.10 | 260.0 | 200.0 |
| Rich gas | nitrogen 0.01, carbon dioxide 0.02, methane 0.72, ethane 0.08, propane 0.06, n-butane 0.05, n-hexane 0.06 | 273.15 | 100.0 |
| Aromatic | methane 0.90, cumene 0.10 | 298.15 | 10.0 |
| Light control | methane 0.70, ethane 0.20, n-butane 0.10 | 298.15 | 30.0 |

The 32-flash lifecycle matrix compares ordinary and explicit-multiphase calculations, starts from
beta values within `1e-12` of a bound, changes temperature by 1 K and pressure by 2%, returns to
the nominal state, and repeats the settled calculation. Acceptance requires phase and composition
normalization within `5e-12`, component material balance below `1e-10`, comparable interphase
log-fugacity residual below `1e-8`, bounded finite compositions and phase fractions, positive
compressibility, and finite Gibbs energy and enthalpy. Single-phase states additionally require
`beta = 1` and `x = z`; at least two nominal states must be multiphase.

These public synthetic fluids qualify translated-PR TP-flash closure and lifecycle behavior. They
do not validate the volume-translation correlation or parameters, phase-envelope accuracy,
experimental PVT predictions, PS/PH flashes, or acoustic-speed calculations. No production solver,
public API, model parameter, unit, default, or wall-clock performance claim is introduced.


### Endpoint recovery performance and phase-storage lifecycle

Empty-clone scaling covers every stored physical slot, avoiding the inventory corruption
caused by scaling reordered logical slots. The aqueous recovery removes the former 5 mol%
exclusion and uses the shared phase lifecycle to allocate a distinct trial slot. It remains a
bounded active-set recovery, not proof that the general stability search finds every global
minimum. The lifecycle correction for [#4310](https://github.com/equinor/neqsim/issues/4310) returns the
released physical slot to the inactive tail, retains root roles by physical slot, and normalizes
stability candidates through their logical phase mapping. `addPhase()` also avoids stale inactive
mappings that point at a surviving object. Remove/add, reordered-slot, root-role and beta tests
cover both removal APIs. These corrections do not constitute a global stability certificate.

Intermediate three-phase recovery runs only when the beta solve stalls. Final cleanup still
checks neutral water-bearing three-phase endpoints, but a feasible split is checked without
allocating another solver. Locked, reactive, ionic, wax and ineligible solid-recovery paths
return before that allocation. Numerically unresolvable water remains excluded before cloning.

`DiluteWaterRecoveryBenchmark` measures complete cold and warm TP flashes for dry gas,
numerical trace water, resolvable undersaturated water, dilute aqueous appearance and three-phase
fluids. It retains seven batches of elapsed time, thread CPU time, allocated bytes and numerical
snapshots. Run identical JVM settings on both comparison heads; compare repeated runs and
reject any speedup accompanied by failed normalization, material balance or fugacity checks.
Timing thresholds are deliberately not asserted in CI. For unmodified SRK/PR phases with the
classic mixing rule and hydrocarbon/water feeds, a detached phase runs two water-rich
successive-substitution trials before full-system recovery allocation. Both mixed trials must
converge to the same water-rich stationary composition with `sum(W_i) < 1 - 1e-6`;
this corresponds to positive stationary tangent-plane distance. Composition convergence is
`1e-10`, agreement between trials is `1e-8`, and the bound is 30 iterations per trial.
A pure-water fugacity comparison alone never bypasses recovery. Invalid references, failed or
trivial trials, near-saturation states, other model/mixing-rule families and non-hydrocarbon
co-solvents retain the full recovery path. This is a model-qualified screen for the aqueous
minimum seeded by recovery, not proof that every possible mixed liquid minimum is absent.
Performance follow-up is tracked in [#4311](https://github.com/equinor/neqsim/issues/4311).

#### Independently qualified methane/heptane phase-map change

Correcting storage aliasing exposes gas/oil splits at 194 K that the previous regression
incorrectly required to be single oil. Independent PR cubic-root and log-fugacity equations
confirm the split, using `z = (0.7, 0.3)`, `kij = 0.05`, `Tc = (190.56, 540.2) K`,
`Pc = (45.99, 27.4) bara`, `omega = (0.0115, 0.3495)`, `OmegaA = 0.45724333333`
and `OmegaB = 0.077803333`. This is an EOS consistency benchmark, not experimental validation.

| Pressure (bara) | Gas fraction | Methane in oil | Methane in gas | Split minus homogeneous-oil Gibbs / (nRT) | Vapor-trial TPD against homogeneous oil |
| --- | --- | --- | --- | --- | --- |
| 78.5 | 0.1581255471 | 0.6454055390 | 0.9906657579 | -0.0022325446 | -0.0246866032 |
| 81.0 | 0.1485707931 | 0.6494133213 | 0.9899020380 | -0.0018972961 | -0.0225099606 |

`TPFlashTest` now checks these independent values over cold and repeated warm starts, component
balance, log-fugacity equality, Gibbs decrease and negative TPD for the former single-oil state.
The remaining phase-map closure, duplicate-removal and density-hole tests are retained.



### Root selectors and Newton inventory mapping

The early PR review found ionic states that passed direction-only property
assertions while failing normalization or allowing ions into gas. The revised
constrained path therefore qualifies original species balance, normalized phase
amounts, ion admissibility, molecular and electrochemical stationarity, and the
actual pressure roots independently of the requested physical property.
Salting tests compare aqueous molality at matched gas fugacity. Numerical closure
still does not establish experimental salting or hydrate accuracy: those model
checks remain separate merge requirements. See the derivative and model-data
qualification in [ElectrolyteBornDerivatives](../thermo/ElectrolyteBornDerivatives)
and [ElectrolyteCPAModel](../thermo/ElectrolyteCPAModel).

A phase's reported GAS/OIL/AQUEOUS classification is not necessarily its configured cubic
root selector. Bounded flash rollback preserves both the physical-slot mapping and
`getPhaseType(logicalPhase)` selector, rather than reconstructing the selector from the
resulting phase label after a rejected trial.

`TPflash` and `TPmultiflash` share the package-private `FlashPhaseState` contract for retained
splits and accepted detached candidates. A compact snapshot owns its captured values:
the active phase count, logical-to-physical mapping, configured root selectors, resulting
classifications, phase fractions, compositions and a separate K-value vector for each phase.
The two-phase refinement snapshot also retains the reference Gibbs energy. Restoring every
phase from the first phase's K-values would discard part of the pre-trial iteration state.

| Transfer | State retained | Initialization boundary |
| --- | --- | --- |
| Compact rollback | Existing physical slots, configured selectors, beta, composition and per-phase K-values | Restore mapping before phase access, normalize beta, then call `init(1)` once. Restore the active count where the caller permits phase removal. |
| Accepted initialized phases | Complete, independently cloned phase objects in canonical destination slots | Preserve accepted caches without normalization or initialization, including the selected near-critical root. |
| Accepted phases requiring reevaluation | The same cloned phase transfer | Normalize beta and call `init(1)` once at the existing adoption boundary. |

Compact rollback assumes unchanged temperature, pressure, feed, component order and retained
physical slots. It rebuilds fugacity caches; it does not restore previously calculated
composition derivatives. If phase shifts are disabled, the configured selectors must still
be present in their retained physical slots. Cached adoption requires matching feed and
thermodynamic model, and matching temperature and pressure in **each destination physical
slot**: `SystemThermo.setPhase()` preserves the destination slot's temperature and pressure.
Consumers of higher derivatives must still perform their required initialization after
adoption or rollback.

Normal default CPA clones also detach the association arrays and matrices owned by
`PhaseSrkCPA`/`PhaseSrkCPAs`, including each component's site-composition derivative rows.
The copied Hessian supports rebuilding a private LU factorization when a derivative is
requested before another association solve; the clone does not reuse its parent's mutable
factorization. `PhaseSrkCPACloneIsolationTest` checks retained values and derivative access
without an intervening `init(0)` that could conceal shared buffers. This ownership repair
supports the qualified ordinary default Statoil CPA path. Optional fully implicit, Anderson
and Broyden CPA variants retain separate qualification scope; it is not a blanket thread-safety
claim for specialized CPA models.

`PhaseElectrolyteCPA` now follows the same ownership contract. Its initialized
association workspaces previously remained shared when multiphase templates
were cloned after hydrate setup. Evaluating one phase could then change another
phase's pressure without modifying its temperature, volume or composition.
`ElectrolyteCPACloneIsolationTest` checks interleaved phase evaluation and both
configuration orders without reinitializing the source to hide that mutation.

These helpers transfer state after a caller's acceptance decision; they do not decide
stability or select a root policy. Implicit supplementary and explicitly enhanced stability
retain their established policies. The guarded ordinary water-rich fallback still replays
the accepted calculation on the live system, and existing Newton and CPA acceptance checks
remain in place. Consolidating the transfer code does not justify removing those calculations.

The two-phase Newton solver uses indexed beta access consistently with its logical phase
compositions, including after removal/reuse produces mappings such as `[2, 0]`. These changes
add no phase search or equilibrium iterations. The tiny aqueous-seed rejection also checks
material infeasibility together with higher Gibbs energy, since normalizing an unsolved seed
is insufficient to qualify it against the preceding stable single phase.

`FlashPhaseStateTest` covers repeated rollback, two- and three-phase mappings, configured
roots distinct from phase classifications, per-phase K-values, locked phase roles, independent
derivative buffers, initialization boundaries, repeated or changed-state flashes, and the
operation-local solid-check workspace exclusion.
`TPflashPhaseLifecycleTest` covers the TPflash rollback caller and reordered Newton inventories;
`TPflashTinyAqueousSeedRejectionTest` retains the stable vapor and nearby genuine aqueous split.

### Existing trace-water aqueous split repair (PR #4320)

An aqueous label alone does not establish a valid equilibrium. For neutral, unconstrained
fluid flashes with a positive water feed fraction below 0.01, an already-present two-phase
aqueous split enters the existing endpoint recovery only when its component material-balance
residual is non-finite or exceeds `1e-8`. Balanced endpoints require no candidate clone or
additional EOS initialization. Chemical, ionic, solid, wax, forced-phase and phase-locked
states are excluded from this new existing-aqueous repair path.

The detached candidate retains its active phases and starts from an interior 50/50 beta seed.
That seed is purely numerical. Up to 20 complete `TPmultiflash.solveBeta()` calls update beta,
compositions and fugacities; each call also has its own bounded inner iteration loop. No
redundant `init(1)` is performed after these calls. Acceptance requires finite bounded beta
and compositions, beta and composition normalization within `1e-12`, component material
balance within `1e-10`, and interphase log-fugacity residual below `1e-8`, together with the
existing distinct-phase and candidate-selection checks. These stricter tolerances apply to
this repair only; ordinary solver tolerances are unchanged.

Two consecutive solves that fail to reduce the larger material/fugacity residual by at
least one part per million stop the repair early. Non-finite residuals also stop it. The
same strict acceptance and final failure checks apply. A qualified trace-water gas/aqueous
candidate is copied directly, just like an oil/aqueous candidate; it is not discarded by
replaying the ordinary flash used for water-rich cross-algorithm recovery.

The public reservoir regression exposed an upstream inventory problem in
`SystemThermo.setTotalFlowRate`: subtracting a reservoir-sized component amount to obtain
a stream-sized flow loses significant digits. Repeated beta solves cannot close a feed
whose overall fractions do not sum to one. Flow scaling now rebuilds positive component
amounts from normalized ratios, with no extra EOS initialization or system clone. The
250 bara, 363.15 K gas-producer regression exercises extraction, both rate changes and
repeated public TP flashes. Separate inventory tests cover initial amounts from `1e-12`
to `1e18` mol and retain a `1e-10` mole-fraction nitrogen component. They check component
ratios, total flow and normalization without loosening the flash contract.

If repair was attempted, the public `TPflash.run()` return also checks the final endpoint after
subsequent phase selection and cleanup. This final check permits one, two or three phases;
it does not force the repaired two-phase topology. An unqualified endpoint raises
`IllegalStateException` with temperature, pressure and material residual instead of returning
silently. Iterative PH/PS callers can therefore observe an unrecovered numerical failure.
The check certifies local conservation, normalization and equilibrium closure, not global
thermodynamic stability or experimental accuracy.

`TPflashTraceWaterRepairTest` qualifies public temperature sweeps, pressure changes and return,
operation reuse, cold-reference agreement, and actual pump entropy/mass/energy closure with
SRK, PR and CPA. Separate injected-state tests exercise strict acceptance, failure reporting
and the no-clone/no-initialization guard. The original deliberately corrupted endpoint test
remains. The synthetic public cases are lifecycle qualification; they do not reproduce the
unavailable original process snapshot.

`TPflashEndpointRecoveryBenchmark` includes dry gas, ordinary two-phase, CPA, condensate,
trace-water oil/aqueous, undersaturated water and three-phase workloads. Arguments are output
path, flashes per batch, batch count, warmup count, and optional family-name prefix. It records
elapsed and thread CPU time, p95 latency, allocated bytes where supported, and numerical
snapshots for both unchanged and changed states. Compare identical harnesses and JVM settings
against an exact baseline in alternating fresh JVMs. Timing is diagnostic, never a CI assertion;
failed numerical states must remain visible and cannot count as successful faster flashes.
