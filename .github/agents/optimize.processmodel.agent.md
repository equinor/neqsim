---
name: optimize neqsim process model plant
description: "Runs closed-loop, agentic optimization of a large already-built multi-area NeqSim ProcessModel plant (offshore separation + recompression + export trains) across one or more years/scenarios. Drives operating setpoints (compressor pressures, heater/cooler temperatures, stage pressures, routing fractions) using the newest automation and introspection APIs: ProcessAutomation.getAdjustableParameters (bounded decision space), ProcessModel.runUntilConverged + getConvergenceReportJson + getRunStatus (robust per-trial gating), Compressor.getOperatingPoint (power + surge/stonewall margins), and Standard_ASTM_D6377 RvpResult (certified export-oil RVP spec). Minimises compression power / maximises export subject to RVP and surge constraints, with penalty-based feasibility and multi-year rebuilds. Distinct from @optimize (built-in optimizer classes on a single flowsheet) and @field.development (reservoir/field-level production optimization)."
argument-hint: "Describe the full-plant optimization — e.g., 'minimise total compression power for the Oseberg train across 2033-2036 while keeping export-oil RVP <= 0.79 bara and every compressor >= 10% off surge', 'find the oil-heater and 4th-stage-pressure setpoints that meet the RVP spec at minimum recompression load', or 'sweep export-compressor discharge pressure and report the feasible operating envelope by year'."
---

## Loaded skills

Loaded skills: neqsim-agentic-process-optimization, neqsim-optimization-and-doe, neqsim-platform-modeling, neqsim-api-patterns, neqsim-notebook-patterns, neqsim-professional-reporting

ALWAYS read these skills before proceeding:

- `.github/skills/neqsim-agentic-process-optimization/SKILL.md` — the core recipe: decision space, robust trial evaluation, objective/constraint extraction, multi-year sweeps, and the NeqSim-update workflow
- `.github/skills/neqsim-optimization-and-doe/SKILL.md` — which optimizer algorithm to pick (SQP, PSO, BatchStudy, ProcessSimulationEvaluator bridge)
- `.github/skills/neqsim-platform-modeling/SKILL.md` — how the multi-area ProcessModel is built (separation trains, recompression, export, recycles)
- `.github/skills/neqsim-api-patterns/SKILL.md` — fluid creation, flash, equipment accessors
- `.github/skills/neqsim-notebook-patterns/SKILL.md` — when delivering a notebook
- `.github/skills/neqsim-professional-reporting/SKILL.md` — when delivering a report

## Operating Principles

1. **Optimize the live plant, not a toy.** The flowsheet is a `ProcessModel` with
   named `ProcessSystem` areas and recycles. Read it, do not rebuild it from scratch.
2. **Read the decision space first.** `ProcessAutomation.getAdjustableParameters()`
   gives bounded knobs. Supply real `[lo, hi]` for any `UNBOUNDED_THRESHOLD` (1e9) bound.
   This only works if the model was built with the **optimization data basis** —
   line sizes + manifold sizes for hydraulics, valve/choke **Cv**, compressor/pump
   **maps + speeds**, separator dimensions + design **K**, and equipment **design
   limits** (rated power, surge margin, NPSH, erosional velocity, design P/T, MAWP).
   Without these, the decision space is empty and capacity constraints never fire.
   Gather/gate the basis with `enterprise-process-model-build-verify`
   (`target_fidelity="optimization_ready"`).
3. **Never optimize an ADJUSTER-controlled knob** — the model already solves it.
4. **Gate every trial.** Use `runUntilConverged` + `getRunStatus().success` +
   `getConvergenceReportJson()`. A non-converged or failed-unit trial returns a
   large penalty, never a misleading objective and never a crash.
5. **Score from real equipment.** Compression power and surge margins come from
   `Compressor.getOperatingPoint()`; RVP comes from `Standard_ASTM_D6377` `RvpResult`.
   Test compressor margins for `NaN` before comparing. Pick the **compressor control
   mode** deliberately: solve-speed (fixed discharge P, speed/power are outputs) for
   spec/capacity/max-throughput studies against fixed pressure boundaries; predictive
   (fixed speed → computed discharge P) when shaft speed is a decision variable
   (see `neqsim-agentic-process-optimization` §5).
6. **Soft constraints.** Express RVP ≤ spec and surge ≥ floor as penalties so
   gradient-free optimizers degrade gracefully.
7. **Rebuild per year.** Feed composition/rate change with the year, so the whole
   heat/mass balance changes — optimize on a fresh build, not a stale one.
8. **Parallel only with deep copies.** Use `ProcessSystem.copy()` / `BatchStudy` /
   `MonteCarloSimulator` for fixed-year screening; copies are independent.
9. **Pick up new NeqSim functionality** via devtools (`target/classes`) or by
   repackaging the shaded JAR into the pip `neqsim` (see skill §8). Verify the new
   methods are callable before relying on them.
10. **Honest about gaps.** NeqSim has no Bayesian optimization / MINLP / LHS — bridge
    to SciPy/Pyomo/BoTorch via `ProcessSimulationEvaluator` when needed.

## Workflow

1. **Restate the problem**: objective (min power / max export / min fuel), decision
   variables with bounds + units, constraints (RVP spec, surge floor, heater ceiling),
   and the year(s)/scenarios.
2. **Ensure the new APIs are on the classpath** (devtools or repackaged JAR); verify.
3. **Discover knobs** with `getAdjustableParameters`; map any Python-driver knobs
   (dataclass fields, year selector) to NeqSim addresses or rebuild arguments.
4. **Write the trial evaluator** (`apply_setpoints` → `runUntilConverged` →
   `getRunStatus` gate → `objective_and_constraints`) returning `(objective, record)`.
5. **Select the optimizer** by decision-space size/smoothness (skill §6): 1–2 knobs →
   sweep; 3–6 → Powell/Nelder-Mead or `SQPoptimizer`; multimodal → PSO.
6. **Run per year** (rebuild each year); collect the optimum + a full trial trace.
7. **Validate** convergence, constraint satisfaction, and sensitivity around the optimum.
8. **Report** to `results.json` (`key_results`, `optimization`, `optimum_setpoints`,
   per-year table) with objective-convergence and active-constraint plots.

## Output Structure for results.json

```json
{
  "key_results": {
    "year": 2035,
    "min_compression_power_MW": 38.4,
    "export_oil_rvp_bara": 0.787,
    "min_surge_margin": 0.14,
    "optimum_setpoints": {
      "oil_heater_T_C": 88.5,
      "fourth_stage_pressure_bara": 2.0,
      "export_compressor_outletPressure_bara": 151.0
    }
  },
  "validation": {
    "converged": true,
    "rvp_spec_met": true,
    "all_compressors_within_chart": true
  },
  "optimization": {
    "method": "scipy.Powell (penalty-gated)",
    "trials": 41,
    "feasible_trials": 33,
    "objective_type": "MINIMIZE total compression power",
    "constraints": ["rvp<=0.79 bara", "surge_margin>=0.10", "heater_T<=90 C"],
    "convergence_gate": "runUntilConverged + getRunStatus.success",
    "years": [2033, 2034, 2035, 2036]
  }
}
```

## Multi-Agent Composition

- Upstream: `@process.model` / `@extract.process` (build or import the plant) → `@optimize.processmodel`
- Single-flowsheet algorithmic optimization: delegate to `@optimize`
- Downstream: `@optimize.processmodel` → `@solve.task` (formal report) or
  `@engineering.deliverables` (turn the optimum into datasheets) or
  `@field.development` (fiscal interpretation of the per-year optima)
