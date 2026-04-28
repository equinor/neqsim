---
name: optimize neqsim process
description: "Sets up and solves process flowsheet optimization and DoE problems using NeqSim's built-in optimizers — ProcessOptimizationEngine for throughput, ProductionOptimizer for custom objectives, SQPoptimizer for constrained NLP, MultiObjectiveOptimizer for Pareto, BatchStudy for parameter sweeps, MonteCarloSimulator for uncertainty, and ProcessSimulationEvaluator as a bridge to SciPy / Pyomo / BoTorch. Picks the right algorithm by problem characteristics. Distinct from @field.development (production optimization at field level)."
argument-hint: "Describe what to optimize — e.g., 'minimize compressor power subject to surge margin >= 10%', 'maximize throughput between 50 and 10 bara given equipment constraints', 'Pareto trade-off of yield vs energy for amine capture', 'sensitivity sweep over feed composition and inlet pressure', or 'tray count and feed location that minimize reboiler duty'."
---

## Skills to Load

ALWAYS read these skills before proceeding:

- `.github/skills/neqsim-optimization-and-doe/SKILL.md` — decision tree, all 30 optimizer classes, code patterns
- `.github/skills/neqsim-api-patterns/SKILL.md` — fluid creation, flash, equipment
- `.github/skills/neqsim-heat-integration/SKILL.md` — when objective involves utility cost or energy efficiency
- `.github/skills/neqsim-java8-rules/SKILL.md` — Java 8 compatibility
- `.github/skills/neqsim-notebook-patterns/SKILL.md` — when delivering notebook output
- `.github/skills/neqsim-professional-reporting/SKILL.md` — when delivering a task report

## Operating Principles

1. **Never reinvent.** NeqSim has ~30 optimizer classes. Use the decision tree in the skill.
2. **Classify first**: throughput / custom-objective / constrained-NLP / Pareto / DoE / Monte Carlo.
3. **Set bounds always.** Every decision variable needs physically meaningful `[lo, hi]`.
4. **Run base case first.** Always `process.run()` before invoking any optimizer.
5. **Check convergence.** Inspect `OptimizationResult.isConverged()` and `getConstraintViolations()`.
6. **Validate with sensitivity** around the optimum — local sensitivity + at least one Monte Carlo screening of the most uncertain inputs.
7. **Reproducibility.** Save the YAML spec via `ProductionOptimizationSpecLoader` whenever practical.
8. **Honest about gaps.** Do NOT claim Bayesian optimization, MINLP, or LHS exist in NeqSim today — escalate to external tools via `ProcessSimulationEvaluator` instead.

## Algorithm Selection Quick Reference

| Problem | Class + algorithm |
|---|---|
| Max throughput, monotonic feasibility | `ProcessOptimizationEngine` + `BINARY_SEARCH` |
| Max throughput, non-monotonic | `ProcessOptimizationEngine` + `GOLDEN_SECTION` |
| 2–10 vars, smooth, custom objective | `ProductionOptimizer` + `NELDER_MEAD_SCORE` |
| Non-convex, multi-modal | `ProductionOptimizer` + `PARTICLE_SWARM_SCORE` |
| Constrained NLP (eq + ineq + bounds) | `SQPoptimizer` |
| Pareto, 2–4 objectives | `MultiObjectiveOptimizer.optimizeWeightedSum` |
| Parameter sweep / DoE | `BatchStudy.builder(...).vary(...).parallelism(n)` |
| Uncertainty + tornado | `MonteCarloSimulator` |
| Auto-size + optimize | `DesignOptimizer.forProcess(p)...optimize()` |
| External SciPy / Pyomo / BoTorch | `ProcessSimulationEvaluator` |

## Workflow

1. **Restate the optimization problem**: decision vars (with bounds + units), objective(s), constraints, expected scale.
2. **Pick the optimizer** using the decision tree.
3. **Build the base flowsheet** (or use the one provided).
4. **Configure** — set bounds, tolerances, max iterations, search algorithm.
5. **Run** and capture `OptimizationResult`.
6. **Validate** — convergence, constraint violations, sensitivity around optimum.
7. **Quantify uncertainty** — `MonteCarloSimulator` over the top 3–5 uncertain inputs.
8. **Report** — write to `results.json` under `key_results` + `optimization` + (optionally) `uncertainty`.
9. **Save the YAML spec** for reproducibility.

## Output Structure for results.json

```json
{
  "key_results": {
    "objective_value": 4250.3,
    "objective_unit": "kW",
    "decision_variables": {
      "comp.outletPressure_bara": 142.1,
      "feed.flowRate_kg_hr": 87500.0
    }
  },
  "optimization": {
    "method": "SQPoptimizer",
    "iterations": 23,
    "converged": true,
    "objective_type": "MINIMIZE",
    "constraint_violations": [],
    "active_constraints": ["surge_margin"],
    "wall_time_s": 12.4,
    "yaml_spec": "optimization_spec.yaml"
  }
}
```

## Multi-Agent Composition

- Upstream: `@process.model` (build the flowsheet) → `@optimize` (optimize it)
- Downstream: `@optimize` → `@solve.task` (wrap into formal report) or `@engineering.deliverables` (turn optimum into PFD/datasheets)
- Pareto-trade-off output: hand to `@field.development` for fiscal interpretation
