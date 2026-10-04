---
name: neqsim-continuous-task-improvement
description: "Living tasks and continuous task solving with NeqSim (neqsim task-living/task-cycle/task-solve/task-backtest/task-schedule/task-promote/task-ledger). USE WHEN: a solved task must keep improving daily or on events, be solved until the goal is met or gains are marginal, reopen on new plant data or a changed brief, turn a Word/Markdown brief into a checkable goal, or backtest monitoring against known fault dates."
last_verified: "2026-09-25"
---

# Continuous Task Improvement (Living Tasks)

A normal task ends with a report. A **living task** keeps a `continuous/` folder
next to the normal three steps and is improved by **cycles**: small, resumable,
auditable runs that pull new evidence, recompute KPIs, detect drift, update an
improvement ledger and — only on a trigger — ask an agent to look. The engineer
promotes a reviewed cycle to the new baseline. Nothing in the old task changes.

Principle: **program the loop, prompt the exceptions.** The runner makes no LLM
calls; agents run only when a trigger fires; people decide.

## When to use

| Need | Command |
|------|---------|
| Keep a finished task up to date (daily, on a server or on demand) | `task-living` then `task-schedule --install` |
| Solve until the goal is met, or until improvement is marginal | `task-solve --until goal` / `--until converged` |
| Start from a Word/Markdown brief | `neqsim new-task "title" --prompt-file brief.docx` then `task-living` |
| Prove a monitor finds the faults it should (and no others) | `task-backtest --start ... --end ...` |
| Try everything without company data | `task-reference-case <folder>` |

All commands run through the shared interpreter:
`<python-executable> devtools/neqsim_cli.py task-<command> ...` (or `neqsim task-<command>`).
`<task>` may be a path or a folder name inside the task root (`neqsim --show-task-root`);
`task-status` and `task-reference-case` default to the task root when no folder is given.

## Workflow

1. **Make it living** (never overwrites):
   ```bash
   neqsim task-living <task> --brief brief.docx
   ```
   Creates `continuous/cycle_plan.yaml`, a draft `goal.yaml` compiled from the
   brief, `baseline/` from `results.json` key results, a ledger seeded from
   `recommendations`, and a `continuous:` block in `study_config.yaml`.
2. **Edit the plan** — sources (adapter + options), scripts (task-local stage
   functions), KPIs, drift signals, triggers, stages, solve settings, backtest
   expectations. See the template written by `task-living`.
3. **Confirm the goal** — fill `objective.metric/direction/target` and set
   `confirmed_by`. `task-solve` refuses an unconfirmed goal
   (`--allow-unconfirmed` for experiments only).
4. **Backtest before scheduling** — replay archived data with a simulated clock:
   ```bash
   neqsim task-backtest <task> --start 2025-10-02 --end 2026-09-30 --repeat
   ```
   Report: detected / missed expected events, delay, false alarms per month,
   reproducibility. Written to `continuous/backtests/<run>/`; live state is untouched.
5. **Run cycles** — `neqsim task-cycle <task>` (monitor) or schedule it:
   `neqsim task-schedule <task> --daily 05:00 --install` (Windows Task Scheduler;
   the `cron` line is printed for Linux servers).
6. **Review and promote** — read `continuous/LIVING_REPORT.md` (the always-current
   view) and `cycles/<id>/digest.md`, decide ledger items
   (`task-ledger <task> set OPP-0002 accepted --by NAME`), then
   `neqsim task-promote <task> <cycle-id> --reviewer NAME`.

## Living report (always up to date)

`continuous/LIVING_REPORT.md` (+ `continuous/report/kpi_trends.png`) is rewritten
automatically after `task-living`, every live cycle and solve round, `task-solve`,
`task-backtest`, `task-promote`, a reopen and every `task-ledger set/merge`. It shows:

- the state, the stop reason, the goal and the baseline;
- the latest value of every KPI against the baseline;
- the solve rounds;
- the KPI trends;
- the trigger events;
- the ledger with its pending decisions;
- the baseline history and the backtests;
- the next actions.

It is a view built from the folder — never edit it; `neqsim task-report <task>` rebuilds it.
A report failure is logged and never fails the cycle.

The formal Word/HTML report (`neqsim report`) follows the plan's `report.formal`:
`never` (default), `on_promote` (recommended: the formal report always matches the
promoted baseline) or `every_cycle`. `task-report <task> --formal` forces it once.

Promotion merges the cycle's KPIs over the previous baseline, so promoting a solve
round (which reports only the objective) keeps the baseline of the monitored KPIs.

## Folder layout

```
continuous/
  cycle_plan.yaml   goal.yaml   state.json   watermarks.json   drift_state.json
  kpi_history.csv   LOCK (while a cycle runs)
  LIVING_REPORT.md  report/kpi_trends.png   always-current view (rebuilt, never edited)
  baseline/         baseline.json, kpis.json, results_snapshot.json, history/<id>/
  ledger/events.jsonl                    append-only; merge between hosts by event_id
  stages/*.py                            task-local stage scripts
  data/<source>/YYYY/MM/part_*.csv       pulled evidence
  cycles/<YYYY-MM-DDTHHMMZ@host>[-rN]/   cycle.json, kpis.json, triggers.json, digest.md, ...
  backtests/<run>/                       isolated replay state + backtest_report.{json,md}
```

## Stop rules (solve loop)

States: `goal_met` (validated, required confidence), `infeasible` (reachable
upper bound below target), `blocked` (only person-dependent actions left),
`budget_exhausted`, `converged` (window gains below
`max(absolute, relative·|J|)` **and** the best candidate's net expected
improvement `p·Δ − cost` ≤ 0). After any stop the task enters **monitoring** and
reopens on regression (`regress_margin`), `new_evidence`, `brief_changed`,
`neqsim_changed` or a reviewer request.

## Writing a stage script

```python
def run(ctx):
    rows = ctx.new_rows.get("station", [])        # rows pulled this cycle
    kpi = ...                                     # compute with NeqSim if needed
    return {"kpis": {"polytropic_efficiency": kpi},
            "proposals": [{"title": "...", "category": "maintenance"}],
            "solve": {"value": v, "validated": True, "confidence": "high",
                      "upper_bound": ub, "candidates": [{"action": "...",
                      "p_success": 0.7, "predicted_delta": 0.3}]}}
```

Register in the plan: `scripts: {station_model: {file: continuous/stages/x.py, function: run}}`
and add `script:station_model` to `stages`. Use NeqSim through
`ProcessAutomation.evaluate()` / `getUtilizationSnapshot()` inside the script;
keep the heavy model in the script, not in the plan.

## Drift detection

EWMA + two-sided CUSUM on a frozen warm-up baseline, `confirm` consecutive
values to alarm. **Always set an engineering floor** (`min_sigma`) per signal:
a 30-sample baseline underestimates σ and white noise alone gives false alarms
within months. On the reference case, floors of 0.002 (efficiency), 0.5 %
(meter mismatch), 0.2 bar (pressure) give 3/3 detections and 0 false alarms.

```yaml
drift:
  signals:
    polytropic_efficiency: {min_sigma: 0.002}
  settings: {lambda: 0.2, L: 3.5, k: 0.5, h: 6.0, warmup: 30, confirm: 2}
```

Step and criterion triggers (`triggers.kpi_step`, `triggers.criteria: {kpi: "< 0.785"}`)
fire on **crossing**, so a persistent state raises one trigger, not one per day.

## Production optimisation loop (advisory)

`neqsim task-living <task> --template production` writes a plan, a goal with constraints and an empty
`continuous/user_input.yaml`. Stages, in order: `sense`, `inputs`, `script:model_update` (live data into the
model, run, residual KPIs), `script:optimize` (search, returns `proposals`), `kpis`, `gates`, `constraints`,
`guard`, `drift`, `goal`, `diff`, `outcome`, `ledger`, `digest`, `notify`. The loop is advisory: it never
writes to a control system and every proposal carries `requires_approval`.

- **Levers**: by default every operator-adjustable parameter is a lever; the plan lists them under
  `production.levers`. The task-local `optimize.py` of the Snorre A reference task supports three kinds:
  `separator_pressure`, `well_rate` (choke opening: scales a manifold feed at constant GOR/water cut; assumes the
  reservoir and tubing deliver it, so proposals carry `needs_well_check`) and `equipment_setpoint` (any
  unit-operation setter: `{tag, setter, getter, unit, step, lo, hi}`; not yet run on a real model, so check the
  getter and setter signatures first). Bounds are the operating-experience
  envelope. Each lever is stepped both ways, positive moves are then simulated together (moves interact, so the
  gains are not summed). Call `lever_limits(ctx, name, lo, hi)` so user restrictions apply.
- **Gates** (`gates:` in the plan: `{name, kpi, abs_max|max|min}`): no advice while the model residuals,
  mass balance, input age or live-data flag are outside limits. A missing KPI fails the gate.
- **Constraints** (`constraints:` in `goal.yaml`: `{name, kpi, op, limit, margin, warn, hard, source}`):
  `margin` tightens the limit (use the lab-vs-model scatter for a product spec; for equipment use
  `demonstrated_limit(history, quantile, design)` = larger of design and experience). A hard constraint
  with no `limit` is **unconfirmed** and withholds all advice. Slack becomes the KPI `slack_<name>`.
- **Proposals** must carry `setpoints`, `expected_gain` and `predicted` (a value for every hard-constrained
  KPI). The `guard` stage keeps only proposals that satisfy every hard constraint with margin; the rest are
  listed in `guard.json` with the reasons.
- **Outcome**: ledger items set to `implemented` with `objective_kpi` and `baseline_value` are compared with
  the realised gain each cycle (`outcome_confirmed` / `outcome_miss`); the engineer decides on `verified`.
- **Boundary conditions**: read the measured process boundary (here gas export and injection pressure and
  temperature, via `production.gas_tags`) every cycle as KPIs `<name>_meas`, with `<name>_model`/`_resid`
  when the model has the counterpart, so a model that drifts from the boundary is visible.

### Comments and restrictions from the engineers

The engineer can steer the loop between cycles without touching code. Entries live in
`continuous/user_input.yaml`; the `inputs` stage reads them first in every cycle, applies the effects,
prints them in the digest and the living report, and raises `user_input:<id>` once when an entry is new or
changed (`user_input_expired:<id>` when it expires). Add, list and resolve with the CLI:

```text
neqsim task-note <task> "Vigdis HP choke limited by sand, max +5 %" --lever "Vigdis HP wells" --hi 5 --by NAME
neqsim task-note <task> "Spec is 0.70 bara RVP per lab" --constraint rvp_spec --kpi export_rvp_bara --limit 0.70 --margin 0.05
neqsim task-note <task> "Keep 3rd stage fixed during the trial" --freeze "20D-VA60 3rd stage" --expires 2026-11-01
neqsim task-note <task> "Lab bias measured" --setting production.rvp_bias_bara=0.05
neqsim task-note <task> --list           # or --resolve U-001 --by NAME
```

Effects: `lever_bound` (replaces the plan bounds, so it can also widen), `lever_freeze`, `constraint`
(sets or adds a goal constraint, which also confirms an unset limit), `setting` (dotted plan key) and `gate`.
A note with no effect is free text that the digest and the agent see. Resolve an entry to stop its effects;
the file is the audit trail, so do not delete entries.
## Plugins (public-first)

Entry-point groups `neqsim_continuous.adapters`, `.stages`, `.notifiers`.
Built in: adapter `file` (CSV drop folder); notifiers `file`, `smtp`/`email`,
`webhook`/`teams` (secrets only via `*_env` variable names). Community packages
add generic adapters (tagreader) and optimizers; enterprise packages add site
adapters (OTS, PDM, …). A missing plugin gives source status `not_installed`
and a **degraded** cycle — never a crash.

## Gotchas

- Cycle ids are UTC; a rerun in the same minute gets `-r2`. A crashed cycle is
  resumed by rerunning (completed stages are skipped); a stale `LOCK` expires after 6 h.
- Watermarks only move forward and only on `ok`/`partial` pulls — a failed pull
  is retried next cycle with the same window.
- `--dry-run` writes the cycle folder but no watermarks, ledger or drift state.
- Backtests clear their own run folder; name runs (`--name`) to keep several.
- The validator (`validate_task_results.py`) checks `continuous/` only when it
  exists and ignores `results.json` files inside it.
- Never commit `continuous/data/` or plant data to public repos.

## Related

- User guide: `docs/development/CONTINUOUS_TASK_SOLVING.md` (setup, scheduling, day-to-day work);
  introduced in `docs/development/TASK_SOLVING_GUIDE.md` § "Keeping a Task Alive".
- `neqsim-model-calibration-and-data-reconciliation` — calibrate inside a stage.
- `neqsim-agentic-process-optimization` / `neqsim-optimization-and-doe` — solve stages.
- `neqsim-plant-data` — historian reads for a custom adapter.
- `neqsim-professional-reporting` — report regeneration after promotion.
- Agent: `continuous-improvement` (`.github/agents/continuous-improvement.agent.md`).
