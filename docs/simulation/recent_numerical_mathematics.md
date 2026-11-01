---
title: "Recent Numerical Mathematics: NeqSim Development Assessment"
description: "Evidence-based selection of recent numerical research, implemented equilibrium sensitivities, qualification and reproducible benchmarks."
---

# Recent numerical mathematics assessment

Assessment date: 1 November 2026. Source baseline:
[d24403cc](https://github.com/equinor/neqsim/commit/d24403ccdf41fd6d9003b84537784f2953bc256f).
The user requested evaluation of all candidates and implementation of those with the best practical
advantage. Selection weighs demonstrated defects, integration risk, reusable API value, validation
cost and measured performance. A published speedup is not a NeqSim speedup.

## Evaluation and selection

| Candidate and primary evidence | Current fit and decision |
| --- | --- |
| Implicit differentiation and shared factorized solves; [Rehner and Bauer (2021)](https://doi.org/10.3389/fceng.2021.758090) supplies established derivative background | **Implemented first.** Existing DifferentiableFlash omitted direct feed-composition terms, returned zero property composition gradients and cached results across changed inputs. This is established mathematics applied to a verified implementation gap, not a claim of a new theorem or of implementing dual-number AD. |
| Direct UVN/TVN reformulation; [Kumar and Rosen Esquivel (2025 preprint / 2026 journal)](https://doi.org/10.1016/j.fluid.2025.114528), [pipeline extension (2026)](https://doi.org/10.1016/j.compfluid.2026.107108) | **Highest-priority separate solver investigation.** OptimizedVUflash and ImprovedVUflashQfunc retain nested TP solves. A direct formulation needs volume-based phase variables, stability initialization, critical/boundary treatment, energy-reference consistency and end-to-end V/U acceptance. It cannot be replaced by a two-variable P/T Newton adjustment or by this sensitivity utility. The pipeline paper's HEM scope does not establish two-fluid transfer qualification. |
| Well-balanced, entropy-stable general-EOS scheme; [Michel-Dansac and Thomann (2025)](https://doi.org/10.1016/j.compfluid.2025.106853) | **Retain for a bounded single-phase pilot.** Existing water-hammer and two-fluid paths already contain specific well-balanced corrections. The new proof applies to one-dimensional Euler equations and compatible EOS/CFL conditions. It does not transfer unchanged to frictional, thermal, multicomponent, phase-changing two-fluid equations. Coordinate with #3298/#2907; do not replace active closure work with an unqualified flux. |
| Directional mixing and stronger network pressure-loss formulations; [Kim et al. (2025)](https://doi.org/10.1007/s11081-025-09984-x) | **Useful optimization follow-up.** LoopedPipeNetwork already has conservative component mixing and compositional/thermal hydraulics. The paper assumes stationary isothermal pipes, equal molar masses in pressure loss, and calorific-value mixing. Its cuts require rederivation or verification before use with full EOS composition/thermal coupling; the reported 35-fold benchmark gain is not portable evidence. |
| Analytical inner eCPA Jacobians and hierarchical warm starts; [Moortgat et al. (September 2026)](https://doi.org/10.1021/acs.iecr.6c02626) | **Useful dedicated electrolyte benchmark, under #3144.** Existing CO2BrinePhaseEquilibrium and ReactiveCO2BrinePhaseEquilibrium already constrain ion partition and compare phase trials. Compare complete flash work and parameter conventions before transplanting an inner solver. A lookup may seed the solution, but aqueous salinity and all component balances must be recomputed. Salt precipitation and arbitrary MEG/salt mixtures are not qualified by the paper. |
| Entropy-consistent hyperbolic elastic-pipe equations; [Daude et al. (2025)](https://doi.org/10.1016/j.apm.2025.116096) | **Relevant to #4707, but a separate physical model.** NeqSim already has hydraulic-hammer functionality. Adding compliant area and thermodynamic closure requires acoustic-speed and wave benchmarks plus conservative fluid/pipe energy accounting. An entropy-compatible PDE model is not itself a validated discretization or a complete shutdown study. |
| Composition-path phase-equilibrium ODEs; [Deiters (2025)](https://doi.org/10.1016/j.fluid.2025.114387) | **Medium priority.** PTPhaseEnvelopeMichelsen already uses continuation. The paper's fixed-T/P composition formulation should first be compared on difficult composition-dependent coexistence maps. It is not evidence that all PT tracing should be replaced. |
| Alternating Anderson acceleration; [He and Leveque (2026)](https://doi.org/10.1007/s10915-026-03302-y) | **Medium priority.** PhaseSrkCPAandersonMixing already provides an accelerated association solver. Compare alternating/restarted policies against it using EOS work and full flashes; retain admissible site fractions and residual rejection. The new convergence proof for linear Richardson iteration does not guarantee nonlinear CPA convergence. |
| DISCOMAX differentiable equilibrium; [Ben Hicham et al. (2026)](https://doi.org/10.1021/acs.jcim.6c00728) | **Exploratory.** The demonstrated binary LLE training method uses discretized forward equilibrium and a surrogate backward gradient. It is not an exact multicomponent flash derivative or a drop-in CPA/PR replacement. Complete and qualify the existing exact-equilibrium sensitivity interface before adding learned activity models. |

All decisions beyond the implemented sensitivity work are engineering assessments of literature
and current source, not benchmarked rejections of the methods. No other campaign branch or production flash/flow solver is modified by this delivery. Public Java signatures are retained, the batch method is additive, and incorrect outputs are repaired to their documented units. No EOS or public flash method is replaced; the existing scalar full-reflash method remains available.

## Implemented capability

- Correct direct feed terms in the implicit VLE Jacobian, with log K unknowns and normalized
  composition-addition directions.
- One EJML LU factorization for all T/P/feed columns, checked finite output and backward error;
  no explicit inverse.
- Additive batched local property evaluations following the differentiated equilibrium manifold.
  No TP reflash occurs inside the batch path. The scalar API retains full equilibrium finite
  differences on clones, with completed composition columns. It also supplies an independent
  reference method for the new batch API.
- Current detached snapshots instead of an indefinite mutable-fluid cache.
- Explicit unsupported topology, reaction, ion, zero-feed entropy/Gibbs and boundary failures.
- Correct molar volume, Gibbs and internal-energy values/units.
- Exact molecular-weight and vapor-fraction formulas; selective physical-property initialization.

See [the API contract](differentiable_thermodynamics.md) for mathematical equations, units,
composition paths and limitations.

## Frozen qualification and baseline reproduction

Before the production edit, DifferentiableFlashCompositionTest ran five tests on the source
baseline: three failed. Representative observations were:

- normalized feed beta derivative: reference 0.6351838365642459; baseline 0.6817718143822046;
- methane molecular-weight direction: reference -0.015429700000000008 kg/mol; baseline zero;
- reused wrapper after temperature change: current beta 0.6362843511256677; baseline cached
  beta 0.5839261011367468.

Independent references are full TP reflashes on clones; production local tangent evaluations are
not reused as their oracle. The fixed comparison gate is absolute 2e-7 plus relative 2e-4.
Hydrocarbon feed probes are 2e-5 and 1e-5; CPA binary probes are 2e-4 and 1e-4 to resolve
near-zero, theoretically feed-independent K derivatives above reflash roundoff without relaxing
the gate. Temperature probes are 0.01/0.005 K and pressure probes 0.002/0.001 bar.

Fixtures include SRK/PR methane/ethane/propane/n-heptane at 250 K and 30 bara and CPA
methane/water at 298.15 K and 50 bara. Feed scaling uses 1, 10 and 1000 mol. Tests also cover
batch/scalar parity, all requested public property families, zero-feed single-phase density,
composition changes, exact caller preservation, unsupported three-phase input and the guide example.

## Performance protocol

DifferentiableFlashBenchmark is a manual utility, not a wall-clock CI test. It creates and flashes
each fixture before timing. Five observables (density, enthalpy, entropy, Cp and Z) are evaluated
through separate scalar calls and through one batch. The scalar mode uses independent full equilibrium reflashes; the additive batch mode uses local tangent states. Both modes compute all T/P/composition columns and consume the full output arrays. Forty warmup pairs precede five measurement pairs;
execution order alternates. Repeat in separate JVMs with the same classpath and fixed heap.

Run the benchmark main class after test compilation. Its two optional arguments are repetitions
per pair and number of pairs; defaults are 100 and 5. Enable INFO logging for its class to retain
the per-round nanosecond results. The old-baseline compatibility mode, benchmark.legacy=true,
only calls the historical scalar API. Historical property gradients contained zero composition
columns, so that mode is not an equal-accuracy performance comparison.

Final benchmark measurements and validation commands follow.


### Measured results

OpenJDK 17.0.20 on Linux, 512 MiB fixed heap, JVM ActiveProcessorCount=2; two fresh JVMs, ten pairs per EOS. Times are milliseconds per complete five-property workload.

| EOS | Scalar full-reflash median | Batch local median | Median paired speedup |
| --- | ---: | ---: | ---: |
| SRK | 4.391 | 0.322 | 12.39x |
| PR | 4.945 | 0.371 | 12.99x |
| CPA | 44.496 | 0.842 | 51.49x |

These local results compare two complete gradient paths; they are not a whole-flowsheet speedup or a general hardware guarantee. Raw rounds and source fingerprints are retained in src/test/resources/neqsim/thermo/derivatives/benchmark-20261101.json.


## Final validation

All commands below exited successfully on the final implementation. The selected Maven suite ran
1,052 tests, with zero failures, errors or skips. This includes the 29 derivative tests and the
model-specification, EOS-consistency and CPA derivative suites. The complete repository CI matrix
is a separate pending check on the published PR.

| Check | Result |
| --- | --- |
| ./mvnw -B test -Dtest=DifferentiableFlashCompositionTest,DifferentiableFlashTest,ThermodynamicModelConsistencyTest,CPATemperatureConsistencyTest,PhaseSrkCPASiteDerivativeTest,ClassicTxTemperatureDerivativeTest,ComponentDerivativeCacheCloneTest,ModelSpec*Test -Djacoco.skip=true -DskipITs | Exit 0; 1,052 tests |
| Java compiler module, --release 8, changed production/test Java files | Exit 0; generated class-file major version 52; not a claim of a Java 8 runtime test |
| Javadoc module, -private -Xdoclint:all, changed package/test types | Exit 0 |
| ./mvnw -B checkstyle:check -Dcheckstyle.includes=**/thermo/util/derivatives/*.java -Dcheckstyle.includeTestSourceDirectory=true | Exit 0; zero violations under repository configuration (util and test Java paths are excluded) |
| python devtools/run_spotless.py apply, then check | Both exit 0; final fix stage produces no changes |
| python devtools/build_engineering_coverage.py | Exit 0; generated snapshot refreshed |
| python devtools/check_documentation_search.py | Exit 0 |
| pre-commit run --all-files --hook-stage pre-commit | Exit 0; both hooks pass |
| pre-commit run --all-files --hook-stage pre-push | Exit 0; both hooks pass |
| git diff --check | Exit 0 |

Documentation impact: API guide, derivative comparison guide, package/class JavaDocs, unit migration
notes, research assessment and documentation indexes updated together. No notebook is changed.
