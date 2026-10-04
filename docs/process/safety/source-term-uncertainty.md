---
title: Source-term uncertainty ensembles and validation evidence
description: Weighted joint release scenarios with explicit failure probability, reproducible source frames, non-exceedance quantiles and analytical versus conservation evidence.
---

# Source-term uncertainty ensembles and validation evidence

`ReleaseFlowEnsemble` propagates complete, caller-defined uncertainty cases through a selected
[release model](release-flow-models). It retains every request and result, including failures.
It exports cases through the existing [v1 source-frame contract](source-term-contract), with
no new downstream software dependency or change to live-session behavior.

## Joint inputs and probability meaning

Each `ReleaseFlowEnsemble.Case` supplies a unique ID, a `ReleaseFlowRequest`, a finite positive
relative weight, and sampling provenance. A request includes the full upstream fluid, opening
diameter [m], discharge coefficient [1] and absolute back pressure [Pa]. Pressure, temperature,
composition and geometry may vary together. This preserves caller-supplied correlations;
the API does not assume independent distributions, generate samples, infer probability weights
from operating scenarios, or decide which uncertainty ranges are physically credible.

For equally probable Monte Carlo samples use equal weights and retain the sampling method,
seed, distribution parameters and source process calculation identity in the case metadata.
For a discrete distribution assign its declared relative probabilities. A low/base/high scenario
set is probabilistic only when its weights have a justified probability meaning.
Convergence of sample count and tail quantiles remains the caller's responsibility.

For weights $w_i$, normalized probabilities are $p_i=w_i/\sum_j w_j$.
The unconditional mean is $\overline{\dot m}=\sum_i p_i\dot m_i$ [kg/s]. The quantile
is the inverse weighted empirical CDF, without interpolation: the smallest rate with cumulative
probability at least the requested value. Endpoints return the minimum and maximum.
P90 means 90% **non-exceedance**, not petroleum-reserve exceedance notation or a confidence bound.
These are statistics over supplied inputs, not confidence intervals on physical model accuracy.

## Executable example

The code is exercised by `ReleaseFlowEnsembleTest.documentationExampleHasAnalyticallyKnownWeightedStatistics`.
`fluid` is a configured methane SRK system at 300 K and 5 bara. Coefficients and weights below
are synthetic examples, not recommended distributions.

```java
List<ReleaseFlowEnsemble.Case> cases = new ArrayList<ReleaseFlowEnsemble.Case>();
cases.add(new ReleaseFlowEnsemble.Case("low",
    new ReleaseFlowRequest(fluid, 0.01, 0.50, 1e5), 1.0,
    Collections.singletonMap("method", "discrete joint scenarios")));
cases.add(new ReleaseFlowEnsemble.Case("base",
    new ReleaseFlowRequest(fluid, 0.01, 0.75, 1e5), 2.0,
    Collections.singletonMap("method", "discrete joint scenarios")));
cases.add(new ReleaseFlowEnsemble.Case("high",
    new ReleaseFlowRequest(fluid, 0.01, 1.00, 1e5), 1.0,
    Collections.singletonMap("method", "discrete joint scenarios")));
ReleaseFlowEnsemble ensemble = ReleaseFlowEnsemble.evaluate("opening-study",
    new HomogeneousEquilibriumReleaseModel(), cases);
// Check isComplete(), getStatusCounts() and every result's diagnostics first.
double meanKgS = ensemble.getMeanMassFlowRateKgS();
double p90KgS = ensemble.getMassFlowRateQuantileKgS(0.90);
List<SourceTermFrame> frames = ensemble.toFrames("scenario-1", "feed-opening",
    UUID.fromString("00000000-0000-0000-0000-000000000386"), 0.0,
    Instant.parse("2026-09-21T00:00:00Z"));
```

Import release types from `neqsim.process.safety.release`, collections and `UUID`
from `java.util`, and `Instant` from `java.time`. Requests defensively clone their fluids.
Evaluation is sequential in supplied order; it does not run or mutate a connected process.
Obtain each uncertain upstream state from a successfully solved process first when uncertainty
changes that process's operating point. `getCases()` retains complete immutable requests
for inspection; exported failure frames intentionally omit numeric source payloads.

## Failed cases cannot disappear into the statistics

| Condition | Result |
|---|---|
| Every case is usable | Mean and quantiles are available. |
| Any case is `INVALID` or `UNSUPPORTED` | `isComplete()` is false; rate-statistic getters throw. |
| Some cases have warnings | Statistics remain available; warning counts and diagnostics remain visible. |
| Valid no-forward-flow case | Its genuine zero rate participates in statistics. |
| Model throws, returns null, or returns another model's identity/version | A per-case `INVALID` result records the reason; later cases still run. |
| Duplicate ID, missing case or unrepresentable probability | Input is rejected before any model is called. |

`getUsableProbability()` reports the sum of the **original** probabilities for usable cases.
The evaluator never renormalizes probabilities after a failure and never substitutes zero.
An ensemble with 75% unsupported probability has 25% usable probability and **no unconditional
rate statistics**, even if remaining cases appear well behaved. This avoids silently excluding
regimes that may dominate the high-rate tail. Resolve failures before reporting an unconditional distribution.

## Reproducible exchange

`toFrames` preserves order, status, stations, model identity and the `UNQUALIFIED` evidence
label. Metadata records ensemble ID, case ID, original weight, normalized probability, and
caller provenance under `sampling.*`. Fixed identities/timestamp and deterministic calculations
give deterministic JSON and fingerprints. The schema is unchanged.

All cases share the supplied simulation time. `sequenceMeaning=ENSEMBLE_CASE_INDEX` states
that the sequence indexes alternative cases, **not a transient trajectory**. Do not interleave
them into a live sequence or integrate them as successive time samples.
Retain complete input cases separately for replay, particularly failed cases whose frames
contain no numeric source payload. Provenance should identify the EOS, composition, sampling
method and correlated process inputs sufficiently for reproducibility.

## Validation matrix and evidence limits

The focused CI workflow runs this matrix and retains test reports, schema-validated frames
and CSV output. Existing flashing and mixture tests remain in the same mandatory command.

| Regime or behavior | Executable evidence | What it establishes |
|---|---|---|
| Dilute methane, 300 K, 0.1 bara, back-pressure ratios 0.05/0.30/0.70/0.90/0.98 | `ReleaseFlowBenchmarkTest.diluteGasBackpressureSweepMatchesIndependentPerfectGasEquation` | Choked/subcritical decision and rate within 1.5% of the constant-gamma equation. |
| Methane, 300 K, 50 bara | `ReleaseFlowModelTest.documentationExampleAndEnergyClosure` | Entropy, stagnation-energy and phase-mass closure. |
| Flashing propane, 300 K, 20 to 3 bara | `ReleaseFlowModelTest.liquidFlashingUsesMassFractionsAndPreservesInventory` | Two-phase expansion, mass fractions and conserved composition. |
| Methane/ethane gas | `ReleaseFlowModelTest.mixtureGasAndUnresolvedFlashingBoundaryAreDistinguished` | Multicomponent gas support; explicit rejection of an unresolved flashing-mixture root. |
| Dense CO2, 310 K, 120 to 110 bara | `ReleaseFlowBenchmarkTest.denseFluidHighBackpressureAndCo2SolidRiskAreExplicit` | A resolved dense-fluid case and conservation; **not** independent dense-fluid rate accuracy. |
| CO2 at 210 K | Same dense-fluid test | Solid-risk rejection without a fabricated rate. |
| Weighted cases and failures | `ReleaseFlowEnsembleTest` | Exact scaling statistics, retained failure probability, deterministic frames, serialization and defensive inputs. |
| Real separator transients in both containers | `SourceTermSessionTest` | Actual vessel dynamics reach source frames; no release-inventory feedback is implied. |

For the gas benchmark, with $R_s=R/M$, $r=\max(p_b/p_0,r_c)$ and
$r_c=(2/(\gamma+1))^{\gamma/(\gamma-1)}$, the reference is

$$\dot m=C_d A p_0\sqrt{\frac{2\gamma}{R_s T_0(\gamma-1)}\left(r^{2/\gamma}-r^{(\gamma+1)/\gamma}\right)}.$$

Here $R_s$ is the specific gas constant [J/(kg K)], $\gamma$ the upstream heat-capacity ratio [1],
$p_0$ absolute stagnation pressure [Pa], $T_0$ stagnation temperature [K], and $A$ opening area [m2].
The reference follows constant-gamma ideal-gas isentropic relations and the steady energy balance.
It shares upstream NeqSim heat-capacity ratio and molar mass: this is an independent discharge
equation, not independent property data. The tolerance allows EOS and heat-capacity differences.

Run the six release test classes in `.github/workflows/safety-source-term-contract.yml`,
then `devtools/validate_source_term_contract.py`. CSVs under `target/source-term-benchmarks/`
are generated from calculations, not pre-filled. Conservation and software checks do not
establish experimental accuracy or facility qualification. See the
[completion status](source-term-platform-status) for remaining work in #3860.
