---
name: neqsim-agentic-process-optimization
version: "1.0.0"
description: "Agentic, closed-loop optimization of large multi-area NeqSim ProcessModel plants using the newest automation, convergence-gating, and equipment-introspection APIs. USE WHEN: an agent must optimize a live full-plant flowsheet (operating setpoints, compressor pressures, heater temperatures, routing fractions) across one or more years/scenarios, with robust convergence handling, surge/RVP/spec constraints, and per-trial feasibility gating. Covers ProcessAutomation.getAdjustableParameters, ProcessModel.runUntilConverged + getConvergenceReportJson, RunStatus failure tracking, Compressor.getOperatingPoint surge margins, Standard_ASTM_D6377 RvpResult, ProcessSystem.copy parallel sweeps, and the rebuild-to-pick-up-new-NeqSim-functionality workflow. Complements neqsim-optimization-and-doe (built-in optimizer classes) and neqsim-platform-modeling (how the plant is built)."
last_verified: "2026-06-13"
requires:
  java_packages:
    - neqsim.process.automation
    - neqsim.process.processmodel
    - neqsim.process.equipment.compressor
    - neqsim.standards.oilquality
---

# Agentic Process-Model Optimization

This skill is the recipe for an **agent that optimizes a large, already-built
multi-area plant** (e.g. an offshore separation + recompression + export train)
by turning the newest NeqSim automation and introspection APIs into a robust
optimization loop. It assumes the flowsheet is a `ProcessModel` assembled from
several named `ProcessSystem` areas (see `neqsim-platform-modeling`).

Use `neqsim-optimization-and-doe` for the *algorithm* (SQP, PSO, BatchStudy,
ProcessSimulationEvaluator → SciPy/Pyomo). Use *this* skill for the *plumbing*:
how to read the decision space, evaluate one trial robustly, gate feasibility,
and score the objective from real equipment results.

---

## 1. The four pillars (all verified in NeqSim ≥ 3.13.0)

| Need | API | Returns |
|------|-----|---------|
| Decision space (bounded knobs) | `ProcessAutomation.getAdjustableParameters()` / `getAdjustableParametersJson()` | `List<AdjustableParameter>` with `name/address/unit/lowerBound/upperBound/source` |
| Robust convergence of a coupled plant | `ProcessModel.runUntilConverged(int maxIterations, double tolerance)` | `boolean` converged; pair with `getConvergenceReportJson()` |
| Per-trial feasibility / failure gating | `ProcessModel.getRunStatus()` / `getRunStatusJson()`, `ProcessSystem.getRunStatus()` | `RunStatus` (completed/success/failedUnitName/failedUnitError) |
| Objective + constraints from equipment | `Compressor.getOperatingPoint()`, `Standard_ASTM_D6377.RvpResult` | power, surge/stonewall margins; certified RVP |

> **Why these matter for an agent:** they replace the fragile "call `.run()` twice
> and hope" pattern with explicit *did-it-converge* and *did-any-unit-fail*
> signals, and they expose objective/constraint numbers (compression power, surge
> distance, RVP spec) as structured JSON the agent can parse without walking Java
> object trees.

---

## 2. Discover the decision space

```python
from neqsim import jneqsim   # or devtools `ns` (see §8)
import json

auto = plant.getAutomation()                      # plant = ProcessModel
# NOTE: jpype returns java.lang.String, not Python str — json.loads needs str(...).
params = json.loads(str(auto.getAdjustableParametersJson()))
for p in params["parameters"]:
    print(p["name"], p["address"], p["unit"], p["lowerBound"], p["upperBound"], p["source"])
```

- `source = "INPUT_VARIABLE"` → a settable equipment input (compressor outlet
  pressure, heater outlet temperature, valve outlet pressure, …).
- `source = "ADJUSTER"` → a knob already wired to an `Adjuster`; let the model
  solve it, do **not** also optimize it (double control = divergence).
- `UNBOUNDED_THRESHOLD = 1.0e9` → bounds at/above this are "no real bound";
  the agent MUST supply a physically meaningful `[lo, hi]` before optimizing.

> `getAdjustableParameters()` surfaces **model-side** inputs with bounds. If your
> decision variables live in a Python driver (e.g. a `ProcessInput` dataclass or
> a year selector), map each one explicitly to a NeqSim address or a rebuild
> argument — the registry will not invent them for you.

---

## 3. Robust single-trial evaluation (the core helper)

Always gate on convergence **and** run status. A trial that diverges or throws in
one unit must return a large penalty, never a misleading objective.

```python
def converge(plant, max_iter=30, tol=5e-3, settle_passes=2, soft_maxerr=0.05):
    """Run the coupled ProcessModel until boundary streams stop moving.

    Recycle-heavy plants have near-zero-flow anti-surge recycles that inflate
    the *relative* boundary-error metric (a tiny stream gives a large % error
    even when physically settled), so a strict `tol=1e-4` rarely passes. Accept
    a trial when NO unit threw AND the model either strictly converged or
    reached a relaxed boundary band; keep genuine unit failures infeasible.
    """
    converged = bool(plant.runUntilConverged(int(max_iter), float(tol)))
    for _ in range(settle_passes):                # settle slow recompression loops
        try:
            plant.run()
        except Exception:
            break
    report = json.loads(str(plant.getConvergenceReportJson()))   # str() — jpype String
    status = json.loads(str(plant.getRunStatusJson()))           # str() — jpype String
    failed = status.get("failedUnitName") not in (None, "", "null")
    max_err = report.get("maxError", float("inf"))
    soft_ok = (max_err == max_err) and (max_err < soft_maxerr)   # not NaN and small
    ok = (not failed) and (converged or soft_ok)
    return ok, report, status

def evaluate(plant, setpoints, *, max_iter=30, tol=5e-3):
    """Apply setpoints, converge, score. Returns (objective, record)."""
    try:
        apply_setpoints(plant, setpoints)          # see §4
        ok, report, status = converge(plant, max_iter, tol)
        if not ok:
            failed = status.get("failedUnitName") or report.get("maxError")
            return 1e9, {"feasible": False, "reason": f"non-converged/{failed}", **setpoints}
        obj, parts = objective_and_constraints(plant)   # see §5
        return obj, {"feasible": True, **parts, **setpoints}
    except Exception as exc:                        # JVM exception from a unit
        return 1e9, {"feasible": False, "reason": f"exception:{exc}", **setpoints}
```

Key rules:
- **Penalty, not crash.** Optimizers must keep exploring after a bad trial.
- **`maxError` is a *relative* boundary error.** Near-zero anti-surge recycles
  inflate it to ~0.8 even when settled — don't demand `tol=1e-4`. Gate on
  *unit failure* (`failedUnitName`) plus a relaxed `maxError` band, and add a
  couple of settling `run()` passes.
- **Twice-run is obsolete as a *gate*** — `runUntilConverged` drives convergence;
  the extra passes only damp slow recompression loops. Raise `max_iter`, not hacks.
- **Record everything.** Keep a list of `record` dicts → tornado/trace plots and
  `results.json` later. Report the **best feasible trial** (min objective among
  `feasible=True`), never the last `evaluate()` result — the final call can land
  on a non-converged retry and write `null` power/RVP/surge into `results.json`.
- **One lever may be infeasible.** If a single setpoint (e.g. oil-heater T) can't
  satisfy two coupled constraints (RVP *and* compressor surge), say so and add
  the relieving lever (recompression suction pressure) to `DECISION_VARS`; switch
  the optimizer from `minimize_scalar` to `scipy.minimize(Nelder-Mead, bounds=…)`.

---

## 4. Apply setpoints (string-addressable, dirty-tracked)

Prefer `ProcessAutomation` string addresses over walking the object graph:

```python
def apply_setpoints(plant, sp):
    auto = plant.getAutomation()
    updates = {}                                   # address -> value (one unit family)
    if "export_P_bara" in sp:
        updates["Compression::export compressor.outletPressure"] = sp["export_P_bara"]
    auto.setValues(updates, "bara", False)         # batch, no run yet
    # temperatures use a different unit -> separate batch
    if "oil_heater_T_C" in sp:
        auto.setVariableValue("Sep train A::oil heater second stage.outletTemperature",
                              sp["oil_heater_T_C"], "C")
        auto.setVariableValue("Sep train B::oil heater second stage.outletTemperature",
                              sp["oil_heater_T_C"], "C")
    # convergence happens in converge() via runUntilConverged
```

If addresses are uncertain, self-heal: `auto.setVariableValueSafe(addr, val, unit)`
returns JSON with an auto-corrected address instead of throwing. Use
`auto.validateAddress(addr)` (returns `None` when valid) as a pre-flight check.

When the plant is built imperatively (handles in Python scope), it is equally
valid to set levers directly on the unit and let `converge()` settle them:
`train_A.getUnit("oil heater second stage").setOutTemperature(T, "C")`.

---

## 5. Objective and constraints from real equipment

### Compression power + surge/stonewall margin

```python
def compressor_metrics(plant):
    total_power_MW = 0.0
    min_surge_margin = float("inf")
    within_chart_all = True
    for area in plant.getAllProcesses():           # each ProcessSystem
        for u in area.getUnitOperations():
            if u.getClass().getSimpleName() != "Compressor":
                continue
            op = json.loads(str(u.getOperatingPointJson()))   # str() — jpype String
            p = op.get("power_MW")
            if p == p:                              # not NaN
                total_power_MW += p
            d = op.get("distanceToSurge")           # fraction; NaN if no chart
            if d == d:
                min_surge_margin = min(min_surge_margin, d)
            if op.get("withinChart") is False:
                within_chart_all = False
    return total_power_MW, min_surge_margin, within_chart_all
```

`getOperatingPoint()` / `getOperatingPointJson()` fields:
`power_MW`, `polytropicEfficiency`, `head_kJkg`, `flow_m3hr`, `speed_rpm`,
`distanceToSurge`, `distanceToStoneWall`, `surgeFlowRateMargin_m3hr`,
`withinChart`, `limitingConstraint` (`none`/`surge`/`stonewall`/`no_chart`).
Uncomputable margins are `NaN` — always test `x == x` before using them.

### Export-oil RVP (certified spec) — use RvpResult

```python
D6377 = jneqsim.standards.oilquality.Standard_ASTM_D6377
def export_oil_rvp_bara(stream, ref_T_C=37.8):
    std = D6377(stream.getFluid())
    std.setReferenceTemperature(ref_T_C, "C")      # if available on the build
    res = std.getRvpResult(D6377.RvpMethod.RVP_ASTM_D6377)
    r = json.loads(str(res.toJson()))              # str() — jpype String; {value, unit, method, referenceTemperatureC, valid}
    return r["value"], r["valid"]
```

The convenience `stream.getRVP(37.8, "C", "bara", "RVP_ASTM_D6377")` still works
and is fine inside a hot loop; switch to `RvpResult` when you need the method
label, reference temperature, and `valid` flag for the report.

### Assemble objective with penalties

```python
def objective_and_constraints(plant, rvp_target=0.79, surge_floor=0.10):
    power_MW, surge_margin, within = compressor_metrics(plant)
    rvp, rvp_valid = export_oil_rvp_bara(export_oil_stream())
    penalty = 0.0
    if not rvp_valid or rvp > rvp_target:
        penalty += 50.0 * max(0.0, rvp - rvp_target) + (0.0 if rvp_valid else 25.0)
    if surge_margin < surge_floor or not within:
        penalty += 100.0 * max(0.0, surge_floor - surge_margin) + (0.0 if within else 50.0)
    objective = power_MW + penalty                 # minimise compression power, feasibly
    return objective, {"power_MW": power_MW, "surge_margin": surge_margin,
                       "rvp_bara": rvp, "penalty": penalty}
```

Pick the objective from the task: minimise compression power, maximise gas/oil
export, minimise fuel gas, or a weighted blend. Express every constraint as a
**soft penalty** so gradient-free optimizers degrade gracefully.

---

## 6. Choosing the optimizer (defer to neqsim-optimization-and-doe)

Each full-plant evaluation is **expensive** (seconds–minutes) and the response is
**noisy** (recycle drift) and **non-smooth** (regime switches, flares). Therefore:

| Decision space | Recommended | Notes |
|----------------|-------------|-------|
| 1–2 knobs | 1-D/2-D sweep + interpolation | cheapest, most transparent (see notebook RVP-vs-heater sweep) |
| 3–6 continuous knobs | `scipy.optimize.minimize(method="Powell")` or Nelder-Mead, or NeqSim `SQPoptimizer` | bound via penalties; small `maxiter` |
| Global / many local minima | NeqSim Particle Swarm, or coordinate descent restarts | use when sweeps show multimodality |
| Pareto (power vs export) | `MultiObjectiveOptimizer` | trade-off front |
| Screening / DoE | `BatchStudy` + `ProcessSystem.copy()` | parallel, see §7 |

Bridge to SciPy/Pyomo/BoTorch via `ProcessSimulationEvaluator` when you need
algorithms NeqSim lacks (Bayesian, MINLP). **Do not claim NeqSim has Bayesian
optimization or LHS — it does not.**

---

## 7. Multi-year / multi-scenario sweeps with deep copies

`ProcessSystem.copy()` (and `ProcessModel` rebuild) produce **independent** deep
copies — verified independent so parallel trials cannot cross-contaminate.

```python
years = [2033, 2034, 2035, 2036]
best = {}
for year in years:
    plant = build_plant(year)          # rebuild the full ProcessModel for the year
    x0 = initial_setpoints(plant)
    res = minimize(lambda x: evaluate(plant, vec_to_sp(x))[0], x0,
                   method="Powell", options={"maxiter": 30, "xtol": 0.5})
    best[year] = collect(plant, res)
```

- Rebuild per year because feed composition/rate (and thus the whole heat/mass
  balance) changes with `Year`. Optimizing operating levers on a stale build
  gives the wrong answer.
- For embarrassingly parallel screening at a *fixed* year, `copy()` each area
  and evaluate trials in separate threads/processes (`BatchStudy`,
  `MonteCarloSimulator`, or the `neqsim_runner` subprocess bridge).
- Cache values that don't change between trials (base CAPEX, fixed-duty units);
  classify knobs as "technical" (need a rebuild/converge) vs "operating" (only
  re-converge) to cut runtime.

---

## 8. Picking up brand-new NeqSim functionality (updates workflow)

New Java methods (like `getOperatingPoint`, `runUntilConverged`,
`getAdjustableParameters`, `RvpResult`) are only callable once the classes are on
the Python classpath. Two supported paths:

**A. Devtools (workspace classes, no repackaging)** — best for repo task
notebooks; picks up `target/classes` ahead of the shaded JAR:

```python
import os, sys
from pathlib import Path
PROJECT_ROOT = Path(r"C:\Users\ESOL\Documents\GitHub\neqsim")
os.environ["NEQSIM_PROJECT_ROOT"] = str(PROJECT_ROOT)
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))
from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)
ns = neqsim_classes(ns)
```

Rebuild first if you changed Java: `mvnw.cmd compile` (or `package -DskipTests`).
This MUST be the **first** NeqSim-touching cell — JPype allows one JVM per
process, so any earlier `from neqsim import jneqsim` locks out the override
(restart the kernel if so).

**B. Repackage the JAR into the pip `neqsim`** — best when an existing notebook
already uses `from neqsim import jneqsim` everywhere and you don't want to touch
100+ cells:

```powershell
.\mvnw.cmd package -DskipTests
Copy-Item target\neqsim-<version>.jar `
  "$env:APPDATA\Python\Python312\site-packages\neqsim\lib\java11\neqsim-<version>.jar" -Force
```

Verify in a fresh process before relying on it:

```python
from neqsim import jneqsim
c = jneqsim.process.equipment.compressor.Compressor("c")
assert hasattr(c, "getOperatingPointJson")
assert "runUntilConverged" in dir(jneqsim.process.processmodel.ProcessModel)
```

---

## 9. Reporting (defer to neqsim-professional-reporting)

Persist a trace of every trial and the optimum to `results.json`:

```python
results = {
  "key_results": {"year": 2035, "min_compression_power_MW": power_MW,
                  "export_oil_rvp_bara": rvp, "surge_margin": surge_margin},
  "validation": {"converged": True, "rvp_spec_met": rvp <= 0.79,
                 "all_compressors_within_chart": within},
  "optimum_setpoints": best_setpoints,
  "tables": [{"title": "Per-year optimum", "headers": ["Year", "Power MW", "RVP bara", "Surge margin"],
              "rows": rows}],
}
```

Always plot: objective convergence trace, the active-constraint (RVP or surge)
vs the binding knob, and a per-year optimum summary. Add a discussion block per
figure (observation → mechanism → implication → recommendation).

---

## 10. Pitfalls checklist

- [ ] **`json.loads(str(...))` ALWAYS.** jpype returns `java.lang.String`, not Python
      `str`. `json.loads(comp.getOperatingPointJson())` raises *"the JSON object must
      be str, bytes or bytearray, not java.lang.String"*. If that error is swallowed by
      a bare `except`, every trial silently reports `power_MW=0.0` and `surge_margin=NaN`
      — the objective collapses to the RVP/spec penalty and the optimizer "succeeds" on
      a meaningless metric. Wrap **every** `getXxxJson()` in `str(...)`.
- [ ] Did you `runUntilConverged` (not a single `.run()`) before reading the objective?
- [ ] Did you gate on *unit failure* (`failedUnitName`) plus a **relaxed** `maxError`
      band — not a strict `tol=1e-4`? Near-zero anti-surge recycles inflate the
      relative boundary error; demand convergence too tightly and every trial is
      "non-converged" even though the plant is physically settled.
- [ ] Does `results.json` report the **best feasible trial** from the trace, not the
      last `evaluate()` call? The final call can hit a non-converged retry → `null`
      power/RVP/surge in `key_results`.
- [ ] If one lever can't satisfy two coupled constraints (RVP *and* surge), did you
      add the relieving lever and switch to a multivariate optimizer?
- [ ] Did you test compressor margins for `NaN` before comparing?
- [ ] Are ADJUSTER-sourced knobs left to the model (not also optimized)?
- [ ] Did you supply real bounds for any `UNBOUNDED_THRESHOLD` parameter?
- [ ] Did you rebuild per year (composition changes), not reuse a stale build?
- [ ] Did failed trials return a penalty (never crash the optimizer)?
- [ ] Did you verify the new APIs are on the classpath (devtools or repackaged JAR)?
- [ ] Java helpers (if any) compile on Java 8 — no `var`, `List.of`, `String.repeat`.
