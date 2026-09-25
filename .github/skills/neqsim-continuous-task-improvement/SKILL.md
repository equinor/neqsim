---
name: neqsim-continuous-task-improvement
description: "Living tasks and continuous task solving with NeqSim. USE WHEN: a solved task must keep improving over time (daily or on events), a task must be solved iteratively until the goal is met or improvement becomes marginal, new plant/historian data or a changed brief must reopen a task, a Word or Markdown brief must become a checkable goal, or a monitoring pipeline must be backtested against known fault dates. Covers neqsim task-living/task-cycle/task-solve/task-backtest/task-schedule/task-promote/task-ledger, cycle_plan.yaml, goal.yaml, stop rules, drift detection (EWMA/CUSUM with engineering floors), the improvement ledger, plugin adapters/stages/notifiers, and the public reference case."
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
6. **Review and promote** — read `cycles/<id>/digest.md`, decide ledger items
   (`task-ledger <task> set OPP-0002 accepted --by NAME`), then
   `neqsim task-promote <task> <cycle-id> --reviewer NAME` and regenerate the report.

## Folder layout

```
continuous/
  cycle_plan.yaml   goal.yaml   state.json   watermarks.json   drift_state.json
  kpi_history.csv   LOCK (while a cycle runs)
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

- `neqsim-model-calibration-and-data-reconciliation` — calibrate inside a stage.
- `neqsim-agentic-process-optimization` / `neqsim-optimization-and-doe` — solve stages.
- `neqsim-plant-data` — historian reads for a custom adapter.
- `neqsim-professional-reporting` — report regeneration after promotion.
- Agent: `continuous-improvement` (`.github/agents/continuous-improvement.agent.md`).
