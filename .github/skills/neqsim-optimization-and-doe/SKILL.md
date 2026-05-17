---
name: neqsim-optimization-and-doe
version: "1.0.0"
description: "Process flowsheet optimization and Design of Experiments using NeqSim's built-in stack — SQP for constrained NLP, Particle Swarm / Nelder-Mead for global / non-smooth, ProductionOptimizer for throughput, MultiObjectiveOptimizer for Pareto, BatchStudy for parallel sweeps, MonteCarloSimulator for uncertainty, ProcessSimulationEvaluator for SciPy/NLopt/Pyomo bridging. USE WHEN: a task involves minimize/maximize over decision variables, sensitivity studies, Pareto trade-offs, max throughput, parameter screening, or DoE — distinct from `neqsim-production-optimization` which covers reservoir-level decline / gas-lift / network problems."
last_verified: "2026-04-26"
requires:
  java_packages:
    - neqsim.process.util.optimizer
    - neqsim.statistics.parameterfitting.nonlinearparameterfitting
    - neqsim.process.calibration
    - neqsim.process.design
---

# NeqSim Process Optimization & DoE Skill

NeqSim ships ~30 production-grade optimization classes. **Do not reinvent
optimizers in Python — use what exists, then wrap it.** This skill is the
discoverability layer that maps engineering problems to the right class.

## Decision Tree — Which Optimizer

```
Need to OPTIMIZE something on a flowsheet?
│
├── Maximize THROUGHPUT for given P_in / P_out, with equipment constraints?
│   → ProcessOptimizationEngine.findMaximumThroughput(...)
│
├── Single decision variable, custom objective, monotonic feasibility?
│   → ProductionOptimizer + OptimizationConfig(SearchMode.BINARY_FEASIBILITY)
│
├── Single var, NON-monotonic / unimodal score?
│   → ProductionOptimizer + SearchMode.GOLDEN_SECTION_SCORE
│
├── 2–10 vars, no gradients, smooth-ish?
│   → ProductionOptimizer + SearchMode.NELDER_MEAD_SCORE
│
├── Non-convex, multiple local optima, global search?
│   → ProductionOptimizer + SearchMode.PARTICLE_SWARM_SCORE
│
├── Constrained NLP (equality + inequality + bounds), gradients OK to FD?
│   → SQPoptimizer (BFGS + active-set QP + L1 merit)
│
├── 2–4 competing objectives → Pareto front?
│   → MultiObjectiveOptimizer.optimizeWeightedSum(...) or .optimizeEpsilonConstraint(...)
│   then front.findKneePoint()
│
├── DoE / parameter sweep / scenario screening?
│   → BatchStudy.builder(base).vary(...).vary(...).addObjective(...).parallelism(8).build().run()
│
├── Uncertainty quantification, P10/P50/P90, tornado?
│   → MonteCarloSimulator (triangular sampling + tornado)
│
├── Calibrate parameters to plant/lab data?
│   → BatchParameterEstimator (LM-based) — see `neqsim-model-calibration-...`
│
├── PVT / EOS regression to experiments?
│   → LevenbergMarquardt + appropriate *Function (CME, CVD, density, etc.)
│
├── External optimizer (SciPy, NLopt, Pyomo, GA, BoTorch)?
│   → ProcessSimulationEvaluator — black-box evaluate(double[] x) bridge
│
├── Compressor specifically?
│   → CompressorOptimizationHelper
│
├── Lift curves / VFP tables for reservoir simulation?
│   → FlowRateOptimizer + LiftCurveGenerator + EclipseVFPExporter
│
└── Auto-size equipment + apply constraints + optimize, fluent API?
    → DesignOptimizer.forProcess(p).autoSizeEquipment(1.2)
        .applyDefaultConstraints().setObjective(...).optimize()
```

## Capability Matrix

| Capability | Class | Algorithm | Status |
|---|---|---|---|
| Throughput maximization | `ProcessOptimizationEngine` | Binary, Golden-section, Gradient, BFGS, Nelder-Mead | Mature |
| Custom-objective single-var | `ProductionOptimizer` | Binary feasibility, Golden-section score | Mature |
| Custom-objective multi-var | `ProductionOptimizer` | Nelder-Mead, Particle Swarm | Mature |
| Constrained NLP | `SQPoptimizer` | BFGS-damped + active-set QP + L1 merit | Mature |
| Pareto multi-objective | `MultiObjectiveOptimizer` | Weighted-sum, epsilon-constraint, knee-point | Mature |
| Parallel parameter sweep | `BatchStudy` | Full-factorial, ExecutorService parallel | Mature |
| Monte Carlo + tornado | `MonteCarloSimulator` | Triangular sampling, P10/P50/P90 | Mature |
| Sensitivity / shadow prices | `SensitivityAnalysis`, `ProcessConstraintEvaluator` | Finite differences, marginal value | Mature |
| External optimizer bridge | `ProcessSimulationEvaluator` | Generic black-box `evaluate(x)` + bounds + grads | Mature |
| Bottleneck / debottleneck | `BottleneckAnalysisOptimizer`, `DebottleneckAnalyzer` | Constraint utilization ranking | Mature |
| Off-design operation | `DegradedOperationOptimizer` | Constraint relaxation | Mature |
| What-if / impact | `ProductionImpactAnalyzer` | Comparative simulation | Mature |
| Lift curves / VFP | `FlowRateOptimizer`, `LiftCurveGenerator`, `EclipseVFPExporter` | Grid sweep + Eclipse export | Mature |
| Multi-scenario VFP | `MultiScenarioVFPGenerator` | Batched lift curve generation | Mature |
| Pressure boundary opt | `PressureBoundaryOptimizer` | Gradient + constraint-aware | Mature |
| Compressor-specific | `CompressorOptimizationHelper` | Polytropic / isentropic sweep | Mature |
| Auto-size + optimize | `DesignOptimizer` | Fluent builder, applies CapacityConstraints | Mature |
| Equipment constraint cache | `ProcessConstraintEvaluator` | TTL cache, 18 capacity strategies | Mature |
| YAML/JSON spec loading | `ProductionOptimizationSpecLoader` | Snake-YAML / Gson | Mature |
| Nonlinear parameter fitting | `LevenbergMarquardt` (+AbsDev, BiasDev) | LM iterations, Jacobian via FD | Mature |
| LP for chemical equilibrium | `LinearProgrammingChemicalEquilibrium` | Apache Commons `SimplexSolver` | Mature |
| MPC embedded QP | `ModelPredictiveController` | Receding-horizon QP | Mature |

## Pattern 1 — Maximize Throughput with Equipment Constraints

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.util.optimizer.ProcessOptimizationEngine.SearchAlgorithm;

ProcessOptimizationEngine engine = new ProcessOptimizationEngine(processSystem);
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);
engine.setFeedStreamName("feed");
engine.setOutletStreamName("export");

OptimizationResult r = engine.findMaximumThroughput(
    50.0,    // inlet pressure (bara)
    10.0,    // outlet pressure (bara)
    1000.0,  // min flow (kg/hr)
    100000.0 // max flow (kg/hr)
);
System.out.println("Max flow: "  + r.getOptimalValue() + " kg/hr");
System.out.println("Bottleneck: " + r.getBottleneck());
```

## Pattern 2 — Custom Objective with `ProductionOptimizer`

```java
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;
import neqsim.process.util.optimizer.ObjectiveFunction;
import neqsim.process.equipment.compressor.Compressor;

ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .tolerance(100.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .maxIterations(30);

List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("power",
        proc -> ((Compressor) proc.getUnit("comp")).getPower("kW"),
        1.0, ObjectiveType.MINIMIZE));

OptimizationResult result = optimizer.optimize(process, feed, config, objectives, null);
```

## Pattern 3 — Constrained NLP with `SQPoptimizer`

```java
import neqsim.process.util.optimizer.SQPoptimizer;

SQPoptimizer sqp = new SQPoptimizer();
sqp.setObjectiveFunction(x -> -computeNPV(x, process));   // SQP minimizes
sqp.addEqualityConstraint(x -> massBalanceResidual(x));
sqp.addInequalityConstraint(x -> 200.0 - x[0]);           // P_out <= 200 bar
sqp.addInequalityConstraint(x -> x[1] - 0.05);            // surge margin >= 5%
sqp.setVariableBounds(
    new double[] {50.0,  0.0},        // lower
    new double[] {250.0, 1.0});       // upper
sqp.setInitialPoint(new double[] {120.0, 0.5});
sqp.setMaxIterations(80);

SQPoptimizer.OptimizationResult r = sqp.solve();
double[] xStar  = r.getOptimalPoint();
double   fStar  = r.getOptimalValue();
boolean  ok     = r.isConverged();
```

**SQP rules of thumb:**
- Always set tight, physically meaningful bounds — SQP is fragile without them.
- Scale variables to ~ unit magnitude (use `x_scaled = (x - x_low) / (x_up - x_low)` if needed).
- For ≤ 20 decision variables FD gradients are fine; provide analytical when available.
- If SQP fails to converge, escalate to Particle Swarm to find a good starting point, then re-run SQP from there.

## Pattern 4 — Pareto Multi-Objective

```java
import neqsim.process.util.optimizer.MultiObjectiveOptimizer;
import neqsim.process.util.optimizer.StandardObjective;
import neqsim.process.util.optimizer.ParetoFront;
import neqsim.process.util.optimizer.ParetoSolution;

List<ObjectiveFunction> objs = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER);

MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.optimizeWeightedSum(process, feed, objs, baseConfig, 20);
ParetoSolution knee = front.findKneePoint();   // best trade-off

front.toCsv("pareto.csv");
```

## Pattern 5 — DoE / Batch Sweep

```java
import neqsim.process.util.optimizer.BatchStudy;
import neqsim.process.util.optimizer.BatchStudy.Objective;

BatchStudy study = BatchStudy.builder(baseCase)
    .vary("heater.duty",        1.0e6, 5.0e6, 5)   // 5 levels
    .vary("compressor.pressure", 30.0, 80.0, 6)    // 6 levels
    .addObjective("power",      Objective.MINIMIZE)
    .addObjective("throughput", Objective.MAXIMIZE)
    .parallelism(8)
    .build();

BatchStudyResult result = study.run();   // 30 cases run in parallel
result.exportToCSV("batch.csv");
System.out.println("Best on power:      " + result.getBestCase("power"));
System.out.println("Best on throughput: " + result.getBestCase("throughput"));
```

## Pattern 6 — Monte Carlo Uncertainty + Tornado

```java
import neqsim.process.util.optimizer.MonteCarloSimulator;

MonteCarloSimulator mc = new MonteCarloSimulator(process, 200);
mc.addTriangularParameter("Gas Price",   0.8,  1.5,  2.5,
    (p, v) -> setGasPriceOnProcess(p, v));
mc.addTriangularParameter("CAPEX mult.", 0.85, 1.0,  1.4,
    (p, v) -> setCapexMultiplier(p, v));
mc.addTriangularParameter("Recovery",    0.45, 0.57, 0.66,
    (p, v) -> setRecoveryFactor(p, v));
mc.setOutputExtractor("NPV (MNOK)", p -> calculateNPV(p));

MonteCarloResult result = mc.run();
System.out.println("P10="  + result.getP10());
System.out.println("P50="  + result.getP50());
System.out.println("P90="  + result.getP90());
System.out.println("P(NPV<0) = " + result.getProbabilityNegative());

// Tornado — sweep each param low→high holding others at base
result.getTornadoData().forEach((name, swing) ->
    System.out.println(name + ": swing=" + swing));
```

This is the **mandatory pattern for the uncertainty + risk notebook** in the
3-step task workflow (see `AGENTS.md` Step 2 requirements).

## Pattern 7 — Bridge to SciPy / Pyomo / NLopt / BoTorch (Python)

```python
from neqsim import jneqsim
from scipy.optimize import minimize, differential_evolution
import numpy as np

ev = jneqsim.process.util.optimizer.ProcessSimulationEvaluator(process)
ev.addParameter("feed",      "flowRate", 1000.0, 100000.0, "kg/hr")
ev.addParameter("comp",      "outletPressure", 50.0, 200.0, "bara")
ev.addObjective("power",     lambda p: p.getUnit("comp").getPower("kW"))
ev.addConstraint("surge",    lambda p: p.getUnit("comp").getSurgeMargin(), 0.10, 1.0)

bounds = list(ev.getBoundsAsList())

def objective(x):
    r = ev.evaluate(x)
    return float(r.getObjectives()[0])

def constraint(x):
    return list(ev.evaluate(x).getConstraintMargins())

# SciPy SLSQP
res = minimize(objective, x0=[50000, 100], bounds=bounds,
               constraints={"type": "ineq", "fun": constraint},
               method="SLSQP")

# Or differential evolution for global
res_global = differential_evolution(objective, bounds, seed=42, maxiter=50)
```

The same evaluator works with **NLopt**, **Pyomo + IPOPT**, **DEAP** (GA),
**scikit-optimize / BoTorch** (Bayesian), or any optimizer that consumes a
black-box `f: R^n → R`.

## Pattern 8 — Auto-size + Optimize Fluent API

```java
import neqsim.process.design.DesignOptimizer;
import neqsim.process.design.DesignOptimizer.ObjectiveType;
import neqsim.process.design.DesignResult;

DesignResult result = DesignOptimizer.forProcess(process)
    .autoSizeEquipment(1.2)            // 20% over-sizing margin
    .applyDefaultConstraints()         // pulls 18 capacity strategies
    .setObjective(ObjectiveType.MAXIMIZE_PRODUCTION)
    .optimize();

ProcessSystem optimized = result.getProcess();
result.getOptimizedFlowRates().forEach((s, q) ->
    System.out.println(s + " → " + q + " kg/hr"));
```

## Pattern 9 — YAML Spec for Reproducibility

```yaml
# optimization_spec.yaml
search:
  algorithm: GOLDEN_SECTION_SCORE
  bounds:    [50000.0, 200000.0]
  tolerance: 100.0
  maxIterations: 30
objectives:
  - name: power
    type: MINIMIZE
    weight: 1.0
constraints:
  - name: surge
    min:  0.10
    max:  1.0
    severity: HARD
```

```java
import neqsim.process.util.optimizer.ProductionOptimizationSpecLoader;
ProductionOptimizationSpecLoader loader = new ProductionOptimizationSpecLoader();
OptimizationConfig cfg = loader.fromYaml("optimization_spec.yaml");
```

This is the canonical way to make optimization runs reproducible across
notebooks, tasks, and reports.

## Pattern 10 — Sensitivity Analysis Standalone

```java
import neqsim.process.util.optimizer.SensitivityAnalysis;
import neqsim.process.util.optimizer.ProcessConstraintEvaluator;

ProcessConstraintEvaluator ev = new ProcessConstraintEvaluator(process);
ev.setCacheTTLMillis(30_000);

Map<String, Double> sens = ev.calculateFlowSensitivities(5000.0, "kg/hr");
double maxFeasibleFlow = ev.estimateMaxFlow(5000.0, "kg/hr");
String bottleneck      = ev.evaluate().getBottleneckEquipment();
```

## Common Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| No bounds on decision variables | SQP/NLP solver wanders to nonsense | Always set physically meaningful `setVariableBounds(lo, hi)` |
| Decision vars on wildly different scales | Slow convergence, bad gradients | Scale to ~ unit magnitude |
| Single starting point for non-convex problem | Stuck in local optimum | Use Particle Swarm first → SQP refinement |
| Re-running base-case simulation in tight loop | Painfully slow | Use `ProcessConstraintEvaluator` cache (TTL) or `BatchStudy.parallelism(n)` |
| Hand-rolling Monte Carlo in Python | Reinvents `MonteCarloSimulator` | Use the Java class; integrates with results.json |
| Hand-rolling SciPy on top of `process.run()` | Reinvents `ProcessSimulationEvaluator` | Use the bridge — handles bounds, constraints, FD gradients |
| Mixing absolute throughput and dimensionless score in same optimizer call | Tolerance unit mismatch | Pick `BINARY_FEASIBILITY` (absolute) **or** `*_SCORE` (normalized), not both |
| Using `BINARY_FEASIBILITY` on non-monotonic feasibility | Wrong answer, no warning | Use `GOLDEN_SECTION_SCORE` if feasibility is unimodal |
| Forgetting `process.run()` before optimizer | NPE or stale state | Always run base case first |
| Pareto with > 5 objectives | Front becomes meaningless | Group / aggregate to ≤ 4 objectives |

## Validation Checklist

Before declaring an optimization study complete:

- [ ] Bounds on every decision variable, with units in comments
- [ ] At least two starting points tried (or PSO + SQP refinement) for non-convex problems
- [ ] Constraint violations reported in `OptimizationResult.getConstraintViolations()`
- [ ] Sensitivity analysis around the optimum (`SensitivityAnalysis.calculate()`)
- [ ] Monte Carlo for the most uncertain parameters with P10/P50/P90 reported
- [ ] YAML spec saved alongside results for reproducibility
- [ ] Results written to `results.json` under `key_results` and `optimization` keys
- [ ] If SciPy/Pyomo used externally, the script and the bridge config saved to the task folder

## Genuine Gaps (do **not** claim these exist)

These are NOT in NeqSim today. If a task needs them, escalate (don't fabricate):

- **Bayesian optimization** (Gaussian Process / Kriging surrogate, acquisition functions). Workaround: use BoTorch or scikit-optimize externally via `ProcessSimulationEvaluator`.
- **MINLP / superstructure synthesis** (true integer decision variables with branch-and-bound). Workaround: enumerate integer combinations and run continuous optimization within each.
- **Interior-point NLP for very large problems** (> 100 vars). `SQPoptimizer` is fine up to ~ 100; beyond that bridge to Pyomo + IPOPT via `ProcessSimulationEvaluator`.
- **Latin Hypercube / Sobol / Morris sampling** in `BatchStudy`. Today only full-factorial; for LHS use scipy's `qmc` module externally and feed via `ProcessSimulationEvaluator`.
- **Robust / scenario optimization** (two-stage stochastic, CVaR). Currently approximated via Monte Carlo + post-hoc filtering.
- **Algorithmic differentiation** of the EOS Jacobian for cubic equations. `FugacityJacobian` exists but is not piped into optimizers as analytical gradients.

## See Also

- `neqsim-production-optimization` — reservoir-level production decline, gas-lift allocation, network optimization (different scope)
- `neqsim-model-calibration-and-data-reconciliation` — `BatchParameterEstimator` for plant-data tuning
- `neqsim-eos-regression` — `LevenbergMarquardt` for PVT/EOS parameter fitting
- `neqsim-notebook-patterns` — how to embed optimization runs in task notebooks
- `neqsim-professional-reporting` — `optimization` schema in `results.json`
- Documentation: `docs/process/optimization/OPTIMIZATION_OVERVIEW.md` and 12 sibling files
