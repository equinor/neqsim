---
title: "Performance Tuning Guide"
description: "How to speed up NeqSim simulations — warm-start K-values, process flowsheet routing, stability analysis short-circuit, and when to enable each optimization."
keywords: "performance, speedup, warm start, K values, TPflash, stability analysis, recycle, flash, optimization"
---

# Performance Tuning Guide

NeqSim ships with several optional performance features. Some are always on
(automatic micro-optimizations); others are **opt-in** because they can produce
numerically slightly different (but physically equivalent) results and therefore
break exact-match regression baselines.

This guide explains each flag, its impact, and when to use it.

## Quick recommendation

**You probably don't need to do anything.** Since 2026-04-21 warm-start is
applied automatically inside all iterative flashes that wrap a TPflash outer
loop (`PSflash`, `PHflash`, `PVflash`, `PUflash`, `TVflash`, `PVFflash`,
`PVrefluxflash`, `PHsolidFlash`, `OptimizedVUflash`, `ImprovedVUflashQfunc`,
`QfuncFlash`).
The flag is scoped with try/finally + ThreadLocal so it never leaks out and
never interferes with concurrent flashes.

For **process flowsheets with recycle loops** warm-start is **opt-in** at the
`ProcessSystem` level (default `false`, since recycle-heavy flowsheets are
sensitive to the flash trajectory and warm-start can shift the converged fixed
point). When enabled, K propagates across recycle iterations automatically.
The flag is scoped to `ProcessSystem.run()` via try/finally so it never leaks
out of the run:

```java
ProcessSystem process = new ProcessSystem();
// ... build flowsheet ...
process.setUseFlashWarmStart(true); // opt in — default is false
process.run();
```

For **multi-area models** (`ProcessModel`), use the model-level setter to
propagate warm-start to every registered `ProcessSystem` in one call. The
setting is applied to all currently registered areas and to any area added
afterwards via `model.add(name, processSystem)`:

```java
ProcessModel plant = new ProcessModel();
plant.add("separation", separationArea);
plant.add("compression", compressionArea);
plant.setUseFlashWarmStart(true); // applies to both areas
plant.run();
```

Or set the global flag manually:

```java
// Java — enable globally for the current thread (you manage the lifetime)
ThermodynamicModelSettings.setUseWarmStartKValues(true);
```

```bash
# Or via JVM property at launch (sets default for all threads)
java -Dneqsim.warmStartK=true -jar your-app.jar
```

Expected gain: **3-5× faster** `PSflash` / `PHflash` (automatic),
**1.5× faster** process flowsheets with recycle loops (opt-in via
`process.setUseFlashWarmStart(true)`). No impact on single `TPflash` calls
from a cold start (the first flash still uses the Wilson initial guess).
CME/CVD and other chained independent `TPflash` PVT workflows are unaffected
— warm-start is NOT enabled for plain `TPflash`.

---

## Optimizations that are always on

These require no configuration and never affect accuracy.

### Stability-analysis short-circuit (since 2026-04-21)

`TPmultiflash.stabilityAnalysis()` skips the expensive pure-component trial
loop when the Wilson-K trials have conclusively proven stability. The skip is
gated conservatively:

- Both Wilson-K trials converged (neither failed nor hit max iterations)
- Either both collapsed to the trivial solution (classical stable feed) **or**
  the best non-trivial tangent-plane distance `tm > 0.25`
- Not near-critical (Wilson-K not ≈ 1 for all components)
- `doEnhancedMultiPhaseCheck()` is **not** enabled
- No polar components present (water, methanol, ethanol, MEG, DEG, TEG, MDEA,
  DEA, MEA) — these drive LLE that Wilson-K models poorly, so pure-component
  trials are still needed

Pure-component trials still run whenever any of the above gates fail, preserving
LLE detection for polar systems and near-critical fluids.

Typical gain: 1.2× faster on non-polar multi-component TPflash with stability
check enabled.

### Process-system routing (since 2026-04-21)

`ProcessSystem.runOptimized(UUID)` now routes recycle-containing flowsheets
through `runHybrid()` instead of the legacy `runSequential()`. Both paths use
the same iterative guard (`iter == 1 || unit.needRecalculation()`), so the
fixed point is identical, but `runHybrid()` exploits feed-forward parallelism
in the non-iterative portion.

Typical gain: 1.4-1.8× faster on parallel workloads, identical on pure-serial
chains.

### Removed duplicate init calls

- `Stream.run()` no longer calls `thermoSystem.initProperties()` twice per
  invocation.
- `Compressor.runCompressor()` no longer allocates two `ThermodynamicOperations`
  objects.

These are tiny per-call savings (~5-50 μs) that add up on large flowsheets.

---

## Opt-in optimizations

### Warm-start K-values

**Flag:** `ThermodynamicModelSettings.setUseWarmStartKValues(true)` or
`-Dneqsim.warmStartK=true`

**Default:** `false` (preserves bit-exact regression baselines)

**What it does:** When `Component.init(type=0)` is called between successive
flashes, the existing K-value is preserved if it is a finite, non-default
converged value. Without warm-start, every `init(0)` call resets K back to the
Wilson initial guess:

$$
K_i^{\text{Wilson}} = \frac{P_{c,i}}{P} \exp\!\left[5.373 (1 + \omega_i)\left(1 - \frac{T_{c,i}}{T}\right)\right]
$$

**Why it helps:** Iterative outer-loop flashes (`PSflash`, `PHflash`, dew/bubble
point) call `TPflash` dozens of times with only small changes in `T` or `P`
between calls. With warm-start, each inner `TPflash` converges in 1-2
successive-substitution steps instead of 5-10.

#### How PH/PS flashes use warm-start internally

Both `PHflash` and `PSFlash` are Newton-on-temperature loops wrapping an inner
`TPflash`:

```
solveQ() {                          // outer Newton on T (3-8 steps typically)
  do {
    T_new = T_old - f(T)/f'(T)
    system.setTemperature(T_new)
    tpFlash.run()                   // inner SS loop on K (5-10 steps cold,
                                    // 1-3 steps warm)
  } while (error > 1e-8)
}
```

Their `run()` method uses a **cold-first-then-warm** pattern (see
`PHflash.java:193-214`, `PSFlash.java:129-150`):

```java
boolean prevWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
try {
  ThermodynamicModelSettings.setUseWarmStartKValues(false);
  tpFlash.run();                    // first inner flash: cold (Wilson seed)
  ThermodynamicModelSettings.setUseWarmStartKValues(true);
  solveQ();                         // outer Newton: every inner flash warm
} finally {
  ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
}
```

This means **PS/PHflash already get the inner-loop benefit automatically**,
even when the global flag is off. The first inner flash is forced cold to
guard against stale K from an unrelated previous flash at very different T/P;
all subsequent Newton iterations reuse the previous step's converged K.

The global flag (or `ProcessSystem.setUseFlashWarmStart(true)`) adds the
**cross-call benefit**: when the same separator's PSflash is called repeatedly
(e.g., inside a recycle iteration), the K values stored on the `Component`
objects from the previous call seed the next call's `solveQ()` loop. The
explicit cold-first-flash inside `run()` caps this gain at ~10-20% per call.

**Per-flash gain in our benchmark** (`WarmStartFlashSpeedupTest`):

| Flash | Outer-level gain | Mechanism |
|-------|-----------------:|-----------|
| `PHflash` | ~+18% | More Newton steps → more inner flashes benefit |
| `PSFlash` | ~+5-10% | Fewer Newton steps → cold-first-flash dominates |
| `TPflash` (1000×) | +14% | Direct inner-loop reuse |



**Benchmark results (heavy 13-component gas with water, `ThermoHotspotBreakdownTest`):**

| Task | Cold (default) | Warm (`-Dneqsim.warmStartK=true`) | Speedup |
|------|---------------:|----------------------------------:|--------:|
| clone + `TPflash` (no stab) | 2.97 ms | 1.12 ms | **2.65×** |
| clone + `TPflash` + `PSflash` | 99 ms | 19.2 ms | **5.2×** |
| Recycle flowsheet (5 iters) | 175 ms | 114 ms | **1.53×** |

**Trade-off:** Warm-start follows a slightly different damping path during
successive substitution, so converged values may differ by 0.01-0.1% from the
cold-Wilson baseline. This is within physical tolerance (well below typical
measurement uncertainty) but enough to break baselines that assert
bit-exact numerical values:

- `ConstantMassExpansionTest.testRunCalc` — expected 2.18938, gets 2.19060
- `ConstantVolumeDepletionTest.testRunCalc` — expected 2.28907, gets 2.29117

If you need bit-exact reproducibility against these baselines, leave warm-start
off. If you're running production simulations or optimization loops, enable it.

**Safety checks already in place:**
- Ion components always reset to `K = 1e-40` (warm-start does not apply)
- `K = 1.0` (default/untouched) always resets to Wilson
- `K < 1e-20` or non-finite always resets to Wilson
- TPflash's successive-substitution naturally corrects any stale K-value; the
  flag only affects the starting point of the iteration

### Enabling programmatically per-call

For code paths where you want warm-start only in certain sections:

```java
boolean prev = ThermodynamicModelSettings.isUseWarmStartKValues();
try {
  ThermodynamicModelSettings.setUseWarmStartKValues(true);
  // ... run hot loop here ...
} finally {
  ThermodynamicModelSettings.setUseWarmStartKValues(prev);
}
```

Note: the underlying `ThermodynamicModelSettings` flag is global (static, per
thread via ThreadLocal), not per-system. All `SystemInterface` instances on
the current thread share the same setting. Prefer
`ProcessSystem.setUseFlashWarmStart(true)` — it manages this scope safely
with try/finally so the flag never leaks past `run()`.

### Phase-regime safety

Warm-start is automatically rejected per-component when:

- `K = 1.0` exactly (untouched/default → fall back to Wilson)
- `|K − 1.0| < 1e-3` (near-trivial → previous call was likely single-phase;
  fall back to Wilson to allow detection of a new phase split)
- `K < 1e-20` or non-finite
- Component is an ion (always reset to `K = 1e-40`)
- TPflash detects > 2 phases (force Wilson reseed for stability)

---

## Benchmarking your own workload

Use `ThermoHotspotBreakdownTest` as a template to measure the impact on your
own fluid:

```java
@Test
void measureMyFluid() throws Exception {
  SystemInterface f = new SystemSrkEos(298.0, 80.0);
  // ... add your components ...
  f.setMixingRule("classic");
  f.setMultiPhaseCheck(true);

  // Warm up JIT
  for (int i = 0; i < 10; i++) {
    SystemInterface c = f.clone();
    new ThermodynamicOperations(c).TPflash();
  }

  long t0 = System.nanoTime();
  int iters = 1000;
  for (int i = 0; i < iters; i++) {
    SystemInterface c = f.clone();
    new ThermodynamicOperations(c).TPflash();
  }
  double ms = (System.nanoTime() - t0) / 1e6 / iters;
  System.out.printf("TPflash: %.3f ms%n", ms);
}
```

Run twice — once with `-Dneqsim.warmStartK=false`, once with `true` — to
measure the speedup on your specific fluid and flash type.

---

## Related

- [Flash Calculations Guide](../thermo/flash_calculations_guide.md)
- [Reading Fluid Properties](../thermo/reading_fluid_properties.md)
- [`CHANGELOG_AGENT_NOTES.md`](../../CHANGELOG_AGENT_NOTES.md) — API changelog
