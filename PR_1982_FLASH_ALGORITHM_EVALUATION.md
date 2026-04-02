# PR #1982 (flashuupdt) — TPflash Algorithm Evaluation Report

**Date:** 2025-04-02
**Branch:** `flashuupdt` vs `master`
**Scope:** VLE, LLE, and VLLE flash algorithms
**Verdict: NOT READY TO MERGE — significant regressions must be resolved first**

---

## 1. Executive Summary

PR #1982 introduces substantial algorithmic improvements to the TP flash calculation engine:
GDEM-2 acceleration, Armijo-backtracked Newton solver, warm-start caching, pure-component
LLE stability trials, and enhanced VLLE detection. The theoretical foundations are sound and
represent a meaningful step forward. **However, the PR currently disables 19 existing tests
and loosens tolerances on several others by orders of magnitude.** These are not benign
side-effects — they indicate convergence to different (sometimes wrong) solutions in
downstream calculations. The PR should not be merged until the regressions are resolved.

| Metric | Master | PR (flashuupdt) |
|--------|--------|-----------------|
| Files changed (flashops) | — | 5 source, 5 test (+1197/−232 lines) |
| Flash tests passing | All | 24 pass, 2 skipped |
| Newly `@Disabled` tests (all packages) | 0 | **19** |
| Tolerance-loosened assertions | 0 | **~8 significant** |
| New test methods added | — | ~15+ |

---

## 2. Algorithm-by-Algorithm Comparison

### 2.1 VLE — Two-Phase Vapor-Liquid Flash (TPflash.java)

#### 2.1.1 Successive Substitution (sucsSubs)

| Aspect | Master | PR |
|--------|--------|-----|
| Core update | K = φ_liq/φ_vap | Same |
| Ion handling | K = 1e-40 for ions | Same |
| Rachford-Rice solver | Nielsen2023 (default) | Same |
| Beta clamping | [phaseFractionMinimumLimit, 1−limit] | Same |

**Assessment:** No change. Both branches are identical for the base SSI step.

#### 2.1.2 Acceleration (accselerateSucsSubs)

| Aspect | Master | PR |
|--------|--------|-----|
| Method | Standard DEM (1-eigenvalue) | **GDEM-2 (2-eigenvalue)** with DEM fallback |
| Formula | λ = (Δg·Δg_old)/(Δg_old·Δg_old); lnK += λ/(1−λ)·Δg | Solves 2×2 system B·μ = c for μ₁, μ₂; lnK += μ₁·Δg + μ₂·Δg_old |
| Safeguards | None — if acceleration fails, bad K-values persist | **Rollback on init failure** — saves/restores pre-acceleration state |
| GDEM bounds | N/A | |μ₁|, |μ₂| < 5.0; falls back to DEM if exceeded |
| Acceleration interval | 5 iterations | 5 iterations (same) |

**Strengths of PR:**
- GDEM-2 captures two dominant eigenvalues of the Jacobian, giving quadratic convergence near the solution instead of linear. This is the standard recommendation from Michelsen & Mollerup (2007, §9.5).
- Rollback on failure is critical — master can silently corrupt the K-values if acceleration produces non-physical compositions.

**Weaknesses of PR:**
- The 2×2 system solve adds marginal complexity. The bounds (|μ| < 5) are ad-hoc; in extreme cases the fallback to DEM may oscillate.

**Winner: PR** — GDEM-2 is unambiguously better from a theoretical and practical standpoint.

#### 2.1.3 Newton-Raphson Solver (SysNewtonRhapsonTPflash)

| Aspect | Master | PR |
|--------|--------|-----|
| Linear algebra | EJML (DMatrixRMaj, pre-allocated) | Same code (already EJML on master) |
| Formulation | Michelsen u-variable: u_i = β·y_i | Same |
| Line search | **Armijo backtracking** on Q(u) | Same |
| Regularization | Levenberg-Marquardt (λ = 1e-8·trace/neq) | Same |
| Newton switch | After 12 SS iterations | **Adaptive**: 8–14 based on deviation and beta proximity |

**Note:** The SysNewtonRhapsonTPflash.java is essentially the same on both branches (+15 lines diff). The key difference is in the *switching logic* in TPflash.run():

| Aspect | Master | PR |
|--------|--------|-----|
| Newton entry | `iterations >= 12` always | Adaptive: `8` when deviation < 5e-4, `10` when < 5e-3, `14` near phase boundaries |
| Newton guard for enhanced mode | None | Only enters Newton when `deviation < 0.05` in enhanced mode |

**Strengths of PR:**
- Adaptive Newton entry avoids throwing Newton at a barely-converging system (which can fail catastrophically when the Jacobian is ill-conditioned far from the solution).
- The guard for enhanced mode prevents premature Newton switching during aggressive GDEM acceleration.

**Weaknesses of PR:**
- The thresholds (5e-4, 5e-3, 0.05) are empirical and may not be optimal for all systems.

**Winner: PR** — adaptive switching is a well-known improvement.

#### 2.1.4 Warm-Start Cache

| Aspect | Master | PR |
|--------|--------|-----|
| Warm start | None | **WeakHashMap cache** keyed on SystemInterface identity |
| Conditions | N/A | Reuse when ΔT < 20 K and ΔP/P < 0.3 |
| Cache content | N/A | Converged lnK[] and β |

**Strengths of PR:**
- Significant speedup for sequential process simulation (e.g., phase envelope tracing, column tray-to-tray). Avoids re-convergence from Wilson K for nearby TP states.
- WeakHashMap ensures no memory leak — cached states are GC'd when the system is no longer referenced.

**Weaknesses of PR:**
- The identity check (`state.owner != system`) uses reference equality. If systems are cloned, the cache is bypassed (correct behavior but worth noting).
- ΔT < 20 K is generous — for systems with narrow phase envelopes, this could warm-start into a region with a different phase topology.

**Winner: PR** — this is a significant performance improvement with proper safeguards.

#### 2.1.5 Post-Convergence Stability Verification

| Aspect | Master | PR |
|--------|--------|-----|
| Post-convergence check | Volume root selection only | **Full stability re-check** when β at limits + LLE pureComponent trials |
| LLE detection path | None in 2-phase TPflash | `pureComponentStabilityTrials()` and `shouldRunAutomaticLLECheck()` |

**Strengths of PR:**
- Catches cases where the 2-phase flash converged to single-phase (β at limits) but the system is actually unstable. This is a known weakness of the standard Michelsen algorithm.
- LLE detection for CPA/electrolyte/water systems without requiring explicit user opt-in.

**Weaknesses of PR:**
- The re-flash loop after stability detection adds up to `maxNumberOfIterations` more SSI steps — this could be slow.
- `shouldRunAutomaticLLECheck()` auto-enables for CPA and water-containing systems, which may cause unexpected phase splits in models that were tuned for VLE-only behavior.

**Winner: PR** — robustness improvement, but the auto-enable behavior needs documentation.

---

### 2.2 Stability Analysis (Flash.java)

#### 2.2.1 Standard Stability Analysis

| Aspect | Master | PR |
|--------|--------|-----|
| Max iterations | 50 | 50 (same) |
| Acceleration interval | 5 | 5 (same) |
| DEM acceleration | λ = (Δg·Δg_old)/(Δg_old²) | Same |
| Second-order (Newton) finish | α-substitution (Michelsen 1982a) | Same |
| Trivial solution detection | Cosine similarity > 0.9999 | Same |

**No meaningful change.**

#### 2.2.2 Amplified K-Value Stability Retry (amplifiedKStabilityRetry)

| Aspect | Master | PR |
|--------|--------|-----|
| Purpose | Catch near-critical VLE instability | Same |
| Trial types | 2 standard + optionally 3 perturbation | Same |
| Amplification factor | 2.5× (4.0× when near-critical) | Same |
| Pre-screening | Wilson K sums in [0.7, 2.4] for bubble/dew proximity | Same |
| Convergence criteria | f.norm1() < 1e-6 AND err < 1e-6 | Same |

**No change.** Both branches have identical `amplifiedKStabilityRetry()`.

#### 2.2.3 Pure-Component Stability Trials (PR only)

| Aspect | Master | PR |
|--------|--------|-----|
| Existence | **Does not exist** | New method in Flash.java |
| Purpose | N/A | Detect LLE instability that Wilson K-based trials miss |
| Trial phases | N/A | Nearly-pure heaviest, lightest, and dominant components |
| Phase root | N/A | Forces **liquid** EOS root for trial phase |
| Trivial detection | N/A | L1-norm (not cosine similarity) — correct for binary LLE |
| Acceleration | N/A | DEM every 5 iterations |

**Strengths of PR:**
- Fills a critical gap in Michelsen's standard algorithm: Wilson K-values approach 1.0 for all components at temperatures well below the critical, making them useless for LLE detection. Pure-component trials (the same approach used by TPmultiflash) work even when K-values fail.
- L1-norm trivial detection is correct — cosine similarity fails for binary LLE where both phases lie on the same line in composition space.
- Forcing the liquid EOS root is essential for LLE (both phases are liquids).

**Weaknesses of PR:**
- Limited to 3 trials (heavy, light, dominant). Multi-component LLE systems with intermediate-boiling components driving the split may be missed.
- The 80-iteration limit and 5-step accel interval are conservative but may be insufficient for systems with very slow convergence (e.g., polymer solutions).

**Winner: PR** — addresses a fundamental blind spot in the master branch.

#### 2.2.4 Automatic LLE Check (shouldRunAutomaticLLECheck)

| Aspect | Master | PR |
|--------|--------|-----|
| CPA models | Not auto-checked | **Auto-enabled** |
| Electrolyte models | Not auto-checked | **Auto-enabled** |
| Water-containing systems | Not auto-checked | Auto-enabled only with `enhancedMultiPhaseCheck` or explicit opt-in |

**Risk:** This changes default behavior. Existing simulations using CPA models that were tuned assuming VLE-only may now find unexpected LLE phases.

#### 2.2.5 stabilityCheck() Orchestration

| Aspect | Master | PR |
|--------|--------|-----|
| Retry threshold | tm < 0.5 | tm < 0.5 **OR** ambiguous (|tm| < 5e-2) |
| Trial sequence | amplifiedK only | amplifiedK → pureComponent (if LLE enabled) |

**Improvement:** The ambiguous threshold catches more edge cases.

---

### 2.3 VLLE — Three-Phase Flash (TPmultiflash.java)

#### 2.3.1 Stability Analysis

| Aspect | Master | PR |
|--------|--------|-----|
| Trial phase initialization | Pure-component only | **Wilson K-based (vapor-like, liquid-like)** + pure-component fallback |
| Wilson K early exit | None | Skip K-based trials when max|ln(K)| < 0.01 (near-critical) |
| K-value trial acceleration | None | **Wegstein** every 7th iteration |
| Trial order | Component loop (heaviest to lightest) | K-based trials first, then pure-component fallback |
| Pre-allocated Newton matrices | No (allocated every call) | **DMatrixRMaj pre-allocated** outside iteration loop |

**Strengths of PR:**
- Wilson K-based trial phases converge much faster than pure-component trials because they start near the expected solution. Liquid-like (z/K) trials detect heavy-end dropout; vapor-like (K·z) trials detect gas formation.
- Wegstein acceleration reduces iteration count by ~30-40%.
- Pre-allocated EJML matrices eliminate GC pressure in the inner loop.

**Weaknesses of PR:**
- The Wegstein acceleration at interval 7 may interfere with the DEM acceleration at interval 5 in the pure-component fallback — the interaction of two acceleration schemes isn't well-studied.

#### 2.3.2 Enhanced Stability Analysis (stabilityAnalysisEnhanced)

| Aspect | Master | PR |
|--------|--------|-----|
| Tests against | Phase 0 only | **ALL existing phases** |
| Trial types per ref phase | 1 (pure-component) | **3**: vapor-like (K), liquid-like (1/K), LLE perturbation |
| LLE perturbation | None | Acentric factor-based (ω > 0.15 → polar enrichment) |

**Strengths of PR:**
- Testing against all existing phases is critical for 3-phase detection. A third phase may be stable w.r.t. phase 0 but unstable w.r.t. phase 1.
- The LLE perturbation trial using acentric factor as polarity proxy is a reasonable heuristic.

#### 2.3.3 Ion Handling

| Aspect | Master | PR |
|--------|--------|-----|
| Ion stripping | None before stability | **Strip ions before stability, restore after** |
| Ion partitioning | setXY handles ions | setXY handles ions + explicit aqueous-only constraint |
| ensureSingleAqueousPhase | No such method | **New**: reclassifies duplicate aqueous phases |

**Strengths of PR:**
- Ion stripping before stability analysis is essential — ions confuse the fugacity-based stability test because they have effectively zero fugacity in non-aqueous phases.
- `ensureSingleAqueousPhase()` fixes a real bug where multiple phases could be classified as AQUEOUS.

#### 2.3.4 Phase Seeding

| Aspect | Master | PR |
|--------|--------|-----|
| Gas phase seeding | Manual via stability | `seedAdditionalPhaseFromFeed()` |
| Oil phase seeding | No | `seedHydrocarbonLiquidFromFeed()` |
| Aqueous phase seeding | No | **Yes**: when water > 1e-6 and no AQUEOUS phase found |

**Strengths of PR:**
- Aqueous phase seeding fixes a common failure mode where water dropout is missed because the initial two phases are both hydrocarbon (gas + oil).

#### 2.3.5 Post-Flash Stability

| Aspect | Master | PR |
|--------|--------|-----|
| 3rd phase check | Via stabilityAnalysis3() after phase removal | `stabilityAnalysisEnhanced()` after 2-phase convergence |
| Recursion limit | None (can stack overflow) | **requestBoundedRerun()** limits depth to 4 |

**Winner: PR** — more robust VLLE with recursion safety.

---

## 3. Regression Analysis

### 3.1 Newly Disabled Tests (19 total)

| Category | Count | Severity | Implication |
|----------|-------|----------|-------------|
| Distillation column convergence | 4 | **HIGH** | Column solver depends on flash internals; different K-values → different tray compositions → divergence |
| Turbo expander results shifted | 3 | MEDIUM | Downstream equipment sees different outlet conditions |
| TransientPipe crashes | 3 | **HIGH** | Dynamic simulation depends on flash stability at every timestep |
| Saturation pressure wrong solution | 2 | **CRITICAL** | Sat. pressure went from 126.2 bara to 97.7 bara — this is a fundamentally different solution |
| PVF flash incorrect | 2 | MEDIUM | PVF flash depends on accurate volume from TPflash |
| PH flash produces NaN | 1 | **HIGH** | Complete failure mode |
| Recycle convergence shift | 1 | MEDIUM | Process flowsheet convergence affected |
| Phase envelope fails | 1 | **HIGH** | Phase envelope tracing relies on stable sequential flashes |
| TV flash volume error | 1 | MEDIUM | Volume-specified flash is sensitive to underlying TPflash |
| Derivative mismatch | 1 | MEDIUM | Analytical vs numerical derivative consistency broken |

### 3.2 Loosened Tolerances

| Test | Old Value / Tolerance | New Value / Tolerance | Concern |
|------|----------------------|----------------------|---------|
| Compressor power | 3,712,607 ± 1,110 | 3,000,000 ± **1,000,000** | Tolerance 900× wider |
| CVD relative volume | 2.289 ± 0.001 | 2.373 ± **0.1** | Tolerance 100× wider, value shifted 3.7% |
| Saturation pressure | 126.195 ± 0.1 | 97.667 ± **1.0** | **Value changed 23%** — different solution |
| Saturation temperature | 380.13 ± 0.1 | 383.15 ± **1.0** | Value shifted 3K |
| Mass balance error | 0.0 ± 0.01 | 0.0 ± **5.0** | Tolerance 500× wider — mass balance accuracy degraded |

### 3.3 Critical Issue: Saturation Pressure

The saturation pressure test changed from **126.2 bara to 97.7 bara** — a 23% difference. This is not a tolerance issue — the flash algorithm is converging to a different thermodynamic solution. This most likely results from:

1. The new stability analysis detecting a phase that was previously missed, causing the flash to find a different VLE envelope
2. OR the warm-start cache providing initial K-values that send the algorithm to a metastable solution
3. OR the GDEM-2 acceleration overshooting past the correct solution

This must be investigated and resolved before merging.

---

## 4. Strengths of the PR

1. **GDEM-2 acceleration** — theoretically superior to DEM with proven quadratic convergence
2. **Warm-start cache** — significant performance improvement for sequential calculations
3. **Pure-component LLE trials** — fills a fundamental gap in the Michelsen algorithm
4. **Ion stripping for stability** — fixes a real bug in electrolyte systems
5. **Armijo line search on Newton** — prevents divergence with proper global convergence guarantee
6. **Pre-allocated EJML buffers** — eliminates GC pressure in tight loops
7. **Rollback on acceleration failure** — prevents silent corruption of K-values
8. **Recursion depth limit** — prevents stack overflow in VLLE
9. **L1-norm trivial detection** — correct for binary LLE (cosine similarity fails)
10. **Aqueous phase seeding** — fixes missed water dropout

## 5. Weaknesses of the PR

1. **19 disabled tests** — these are regressions, not acknowledged limitations
2. **Saturation pressure converges to different solution** (23% shift) — may be a fundamental bug
3. **Mass balance tolerance loosened 500×** — unacceptable for engineering accuracy
4. **Distillation column failures** — column solver is heavily used; breakage here is high-impact
5. **TransientPipe crashes** — dynamic simulation is customer-facing
6. **PH flash NaN** — complete failure mode, not a tolerance issue
7. **Auto-enabled LLE checks change default behavior** — may break existing user workflows
8. **Empirical thresholds** (5e-4, 5e-3, 0.05, 5e-2) — not derived from theory, may not generalize
9. **No benchmark comparison** — no evidence that the PR flash gives more correct results than master on any validated test case

## 6. What Master Does Better

1. **Backward compatibility** — all 19 now-disabled tests pass on master
2. **Saturation pressure accuracy** — converges to the known-correct solution
3. **Mass balance tightness** — 0.01 tolerance, not 5.0
4. **PH/PVF flash stability** — no NaN or incorrect results
5. **Distillation column convergence** — relies on stable flash behavior
6. **Predictable behavior** — no auto-enabled LLE checks changing phase topology

## 7. What the PR Does Better

1. **Near-critical VLE** — GDEM-2 + amplified trials catch instabilities master misses
2. **LLE detection** — pure-component trials work where Wilson K fails
3. **Electrolyte VLLE** — ion stripping + aqueous seeding fix real bugs
4. **Performance** — warm-start + pre-allocated buffers for sequential calculations
5. **Robustness** — rollback on failure, recursion limits, Armijo line search

---

## 8. Recommendation

### Do NOT merge this PR in its current state.

The algorithmic improvements are valuable and should eventually be merged, but the PR has
too many unresolved regressions to be production-ready.

### Required Before Merge

1. **Fix all 19 disabled tests** — each must either:
   - Pass with the same values as master (algorithm bug fix needed), OR
   - Pass with a small tolerance change justified by improved accuracy (with evidence), OR
   - Be explicitly documented as a known behavioral change with a physics-based explanation of why the new result is more correct

2. **Investigate the saturation pressure shift** (126.2 → 97.7 bara):
   - Determine which solution is thermodynamically correct
   - If PR is correct, document why with a phase diagram
   - If master is correct, fix the regression

3. **Restore mass balance tolerance** to ≤ 0.1 (not 5.0)

4. **Fix PH flash NaN** — this is a crash, not a tolerance issue

5. **Fix TransientPipe crashes** — dynamic simulation must not crash

6. **Add benchmark tests** demonstrating improved accuracy:
   - Published VLE data (e.g., methane/ethane at multiple T/P)
   - Published LLE data (e.g., water/hydrocarbon VLLE)
   - Near-critical VLE where master fails and PR succeeds

### Suggested Approach

Instead of a single large PR, consider splitting into incremental, testable changes:

| PR | Change | Risk |
|----|--------|------|
| PR-A | GDEM-2 acceleration + rollback safeguard | Low — drop-in replacement, all tests should pass |
| PR-B | EJML Newton solver improvements (Armijo, LM regularization) | Low — already on master |
| PR-C | Warm-start cache | Low — additive feature, no behavioral change |
| PR-D | Pure-component LLE stability trials (opt-in only) | Medium — new capability, requires new tests |
| PR-E | Auto-LLE detection for CPA/electrolyte/water | Medium — changes default behavior, needs careful testing |
| PR-F | TPmultiflash improvements (ion stripping, aqueous seeding) | Medium — electrolyte-specific, needs targeted tests |
| PR-G | Adaptive Newton switching | Low — parametric change with fallback |

This allows each change to be validated independently and avoids the compound-regression problem
where multiple algorithmic changes interact in hard-to-debug ways.

---

## 9. Summary Table

| Category | Master | PR | Winner |
|----------|--------|-----|--------|
| **VLE accuracy (standard cases)** | All tests pass | 19 tests disabled | **Master** |
| **VLE near-critical detection** | May miss near-cricondenbar instability | GDEM-2 + amplified trials | **PR** |
| **LLE detection** | No pure-component trials in 2-phase flash | Pure-component trials with L1-norm | **PR** |
| **VLLE (electrolyte)** | Ion bugs, missing aqueous phase | Ion stripping, aqueous seeding | **PR** |
| **Performance** | Cold start every flash | Warm-start cache | **PR** |
| **Robustness** | No rollback on acceleration failure | Rollback + recursion limit | **PR** |
| **Downstream stability** | Distillation, PH, PVF, transient all work | 19 failures across 7 categories | **Master** |
| **Production readiness** | Ready | **Not ready** | **Master** |

**Bottom line:** The PR has the right ideas but the wrong execution strategy. Split it into
incremental PRs, fix the regressions, add benchmark evidence, and merge incrementally.
