---
title: "Continuous Task Solving (Living Tasks)"
description: "How to keep a solved NeqSim engineering task improving over time: persist and resume state, detect evidence changes, analyze impact, selectively rerun calculations, backtest monitoring and tune drift settings from it, track model parameters with an ensemble calibrate stage, rank actions by value of information, and maintain living, current-best and final reports."
---

# Continuous Task Solving (Living Tasks)

A normal task ([Task Solving Guide](TASK_SOLVING_GUIDE.md)) ends with a report.
A **living task** keeps working after that report: every day, or whenever new
data arrives, it pulls fresh evidence, recomputes its KPIs, checks for drift and
asks an agent to look **only when something changed**. You review what it found
and decide what becomes the new baseline.

Read the Task Solving Guide first. This guide assumes you already have a task
folder with a `results.json`.

**Contents**

1. [How it works](#1-how-it-works)
2. [Before you start](#2-before-you-start)
3. [Try it in five minutes](#3-try-it-in-five-minutes)
4. [Make your own task living](#4-make-your-own-task-living)
5. [Set the goal](#5-set-the-goal)
6. [Write the cycle plan](#6-write-the-cycle-plan)
7. [Write a stage script](#7-write-a-stage-script)
8. [Backtest before you trust it](#8-backtest-before-you-trust-it)
   - [Stop today, resume tomorrow](#81-stop-today-resume-tomorrow)
   - [Update when evidence changes](#82-update-when-evidence-changes)
   - [Tune the drift settings from a backtest](#83-tune-the-drift-settings-from-a-backtest)
9. [Run cycles by hand](#9-run-cycles-by-hand)
10. [Make it run by itself](#10-make-it-run-by-itself)
11. [Standard-first gate for scheduled tasks](#11-standard-first-gate-for-scheduled-tasks)
12. [Let an agent handle the triggers](#12-let-an-agent-handle-the-triggers)
13. [Day-to-day work with a living task](#13-day-to-day-work-with-a-living-task)
14. [Company data sources](#14-company-data-sources)
15. [Troubleshooting](#15-troubleshooting)
16. [Command reference](#16-command-reference)
17. [Production optimisation: advisory loop with engineer input](#17-production-optimisation-advisory-loop-with-engineer-input)
18. [Learning inside the loop: calibration and value of information](#18-learning-inside-the-loop-calibration-and-value-of-information)

---

## 1. How it works

The principle is **program the loop, prompt the exceptions**. The runner is
plain Python and makes no LLM calls. An agent runs only when a trigger fires.
People make every decision that changes the baseline.

```
 one cycle (daily, on new data, or on demand)

   sense -> evidence -> refresh -> script:<model> -> kpis -> drift -> goal -> diff -> ledger
         -> digest -> notify -> agent (only when a trigger fired)

   writes: cycles/<id>/digest.md  and  LIVING_REPORT.md (always current)

 you:  read the report -> decide ledger items -> promote a cycle to the baseline
```

Two ways to run cycles:

| Mode | Command | Use it to |
|------|---------|-----------|
| **Monitor** | `neqsim task-cycle` | Keep a finished study current: new data, new KPIs, drift alarms |
| **Solve** | `neqsim task-solve` | Iterate until the goal is met, or until further rounds gain too little |

After a solve loop stops, the task goes back to monitoring. It reopens by itself
when the objective regresses, new evidence arrives, the brief changes, NeqSim
changes, or a reviewer asks.

Everything lives in one `continuous/` folder beside the normal three steps.
Nothing in `step1_*`, `step2_*` or `step3_*` is changed by a cycle.

```
continuous/
  cycle_plan.yaml     what each cycle does            (you edit)
  goal.yaml           what "solved" means             (you confirm)
  stages/*.py         task-local stage scripts        (you write)
  LIVING_REPORT.md    always-current summary          (generated, never edit)
  report/             KPI trend figure                (generated)
  baseline/           promoted baseline + history
  evidence/           accepted file hashes and provenance baseline
  ledger/events.jsonl improvement ledger (append-only)
  cycles/<id>/        one folder per cycle: cycle.json, kpis.json, triggers.json, digest.md
  backtests/<run>/    isolated replay runs
  data/<source>/      pulled evidence, partitioned by year/month
  state.json  watermarks.json  drift_state.json  kpi_history.csv
```

---

## 2. Before you start

| You need | Why | Check |
|----------|-----|-------|
| The shared Python environment (with `pyyaml`) | Runs the runner and stage scripts | `C:\appl\neqsim-venv\Scripts\python.exe -c "import yaml"` |
| The `neqsim` CLI | All `task-*` commands | `neqsim task-status --help` |
| A NeqSim source checkout | Runner code lives in `devtools/neqsim_continuous/` | `neqsim --show-task-root` |
| Java (only for stages that call NeqSim) | NeqSim thermodynamics | `uv run --project devtools --locked devtools/java_locator.py` |
| GitHub Copilot CLI, signed in (only for automatic agent triage) | Headless agent sessions | `copilot --version` |
| A community or enterprise adapter (only for live historian data) | Pulls data from PI/IP.21, OTS, PDM | see [section 14](#14-company-data-sources) |

If `neqsim` is not on `PATH` in your shell, use the identical command through
the interpreter:

```powershell
C:\appl\neqsim-venv\Scripts\python.exe devtools\neqsim_cli.py task-status <task>
```

Nothing else is needed for the reference case: it uses a CSV file drop and the
Python standard library only.

### Where living tasks are created and found

Living tasks use the **same task root as every other task** — the folder shown by
`neqsim --show-task-root` (set it once with `neqsim --set-task-root "PATH"`, or
for one shell with `NEQSIM_TASK_ROOT`). So you never have to type full paths:

| You type | The runner uses |
|----------|-----------------|
| `neqsim task-cycle C:\work\my_task` | That folder (an existing path always wins) |
| `neqsim task-cycle 2026-09-24_my_task` | `<task root>\2026-09-24_my_task` |
| `neqsim task-status` | Every living task in the task root |
| `neqsim task-reference-case` | Creates `<task root>\reference_compressor_station` |

This works from any folder. If the name is found in neither place, the error
names both locations it looked in.

---

## 3. Try it in five minutes

The public reference case is a synthetic compressor station with three faults
injected at known dates (efficiency decline, flow-meter bias, suction-pressure
step). Use it to learn the loop before you touch a real task.

```powershell
neqsim task-reference-case                     # created in your task root
$task = "reference_compressor_station"         # a name inside the task root is enough

# 1. Replay a year of data and check the monitor finds the three faults
neqsim task-backtest $task --start 2025-10-02 --end 2026-09-30

# 2. Run one monitor cycle as if it were the last day of the data
neqsim task-cycle $task --now 2026-09-30T05:00:00Z

# 3. Solve until improvement is marginal
neqsim task-solve $task --until converged

# 4. Look at what it found
neqsim task-status $task
neqsim task-ledger $task list
```

Expected results:

- **Backtest:** 3 of 3 faults detected (delays 13, 2 and 2 days), 0 missed,
  0 false alarms per month.
- **Cycle:** triggers `kpi_step:suction_pressure_bara` and
  `criterion:polytropic_efficiency`, a new ledger item "Wash or inspect the
  compressor", and `Needs a decision: yes`.
- **Solve:** stops in state `converged` after 8 rounds, because the last gains
  and the best expected gain are below the tolerance.

Open `continuous/LIVING_REPORT.md` to see all of this in one page.

To keep the experiment out of your task root, give a folder:
`neqsim task-reference-case C:\tmp\living`, then use the full path
`C:\tmp\living\reference_compressor_station` in the commands above.

---

## 4. Make your own task living

Start from a finished (or nearly finished) task folder:

```powershell
neqsim task-living <task> --brief path\to\brief.docx
```

This never overwrites an existing file. It creates:

| File | Content |
|------|---------|
| `continuous/cycle_plan.yaml` | A commented template of every section |
| `continuous/goal.yaml` | A draft goal compiled from the brief headings (unconfirmed) |
| `continuous/baseline/` | The first baseline, taken from the numeric `key_results` in `results.json` |
| `continuous/ledger/events.jsonl` | One `proposed` item per `results.json` `recommendations` entry |
| `study_config.yaml` | A `continuous:` block that marks the task as living |

`--brief` is optional. Without it, the brief recorded in `study_config.yaml`
(`inputs.prompt_file`, set by `neqsim new-task --prompt-file`) is used. The brief
is copied into `step1_scope_and_research/references/manual/` and its hash is
stored, so an edited brief later raises a `brief_changed` trigger.

Then do sections 5 to 8, in that order.

---

## 5. Set the goal

`goal.yaml` says what "solved" means. A solve loop refuses to run until
somebody confirms it.

```yaml
confirmed_by: "A. Engineer"            # required before task-solve
objective:
  metric: power_reduction_pct          # a KPI produced by a stage
  direction: maximize                  # or minimize
  target: 2.0                          # goal_met when reached (validated)
  confidence_required: medium
constraints: ["suction temperature >= 22 C"]
stop:
  converged: {window: 3, relative: 0.02, absolute: 0.01}
  budget: {iterations: 12, agent_sessions: 6}
  expected_improvement_cost: 0.01      # a candidate must be worth at least this
reopen:
  regress_margin: 0.5                  # reopen if the metric falls this far below the baseline
  on: [new_evidence, brief_changed, neqsim_capability, reviewer_request]
```

The solve loop stops in one of five states:

| State | Meaning |
|-------|---------|
| `goal_met` | The target was reached with a validated result at the required confidence |
| `converged` | Recent gains are below `max(absolute, relative * abs(J))` **and** no candidate is worth its cost |
| `infeasible` | The reachable upper bound reported by the solver is below the target |
| `blocked` | Only actions that need a person are left |
| `budget_exhausted` | The round or agent-session budget ran out |

Use `neqsim task-solve <task> --allow-unconfirmed` only for experiments.

---

## 6. Write the cycle plan

`cycle_plan.yaml` is the only file the runner reads to know what to do. Every
section is optional; start small and add sections as you need them.

### 6.1 Sources — where new data comes from

```yaml
sources:
  station:
    adapter: file                  # built in: CSV files dropped in a folder
    options: {folder: data_drop, time_column: timestamp, pattern: "*.csv"}
    initial_lookback_days: 1       # first pull goes this far back
    stale_after_hours: 48          # no rows for this long -> source "stale"
```

Each source keeps a **watermark**. A pull only moves the watermark forward when
it succeeds, so a failed pull is retried with the same window next cycle.
Relative folders are resolved against the task folder. Put large data outside
synced folders with `data_root: "D:/neqsim-data/<task>"`.

### 6.2 KPIs — what each cycle measures

```yaml
kpis:
  suction_pressure_bara: {source: station, column: suction_pressure_bara, agg: mean}
  headroom_MSm3d:        {from: results, key: headroom_to_98pct_power_MSm3d}
```

`agg` is `mean`, `min`, `max`, `last`, `sum` or `count`. KPIs computed by a stage
script (section 7) need no entry here.

Add `describe` and `unit` to a KPI so the reports can say what the number is:

```yaml
kpis:
  polytropic_efficiency: {source: station, column: eff, agg: mean,
                          describe: "Compressor efficiency, higher is better", unit: "-"}
```

Both the Living Report and the Final Report show them in a *Meaning* column; without them the
column is left out of the Final Report summary.

### 6.3 Drift — when a KPI has really moved

```yaml
drift:
  signals: [polytropic_efficiency, suction_pressure_bara]
  settings:
    lambda: 0.2
    L: 3.5
    k: 0.5
    h: 6.0
    warmup: 30        # cycles used to freeze the baseline mean and sigma
    confirm: 2        # consecutive out-of-limit cycles before an alarm
    signals:
      polytropic_efficiency: {min_sigma: 0.002}   # engineering floor
      suction_pressure_bara: {min_sigma: 0.2}
```

Drift uses EWMA plus a two-sided CUSUM. **Always set `min_sigma`**: a 30-cycle
warm-up underestimates the noise, and without a floor white noise alone raises
false alarms within months. Choose the smallest shift that is worth a review.
An alarmed signal **recovers** after `recover` consecutive in-control cycles
(default: `confirm`) and can alarm again; a shift that persists stays one alarm.
The CUSUM is bounded at `2h`, so a long excursion does not silence the signal
for months after the plant returns to normal.
Once a backtest with labelled events exists, `task-backtest --tune` chooses these
settings for you (section 8.3). Prefer monitoring model residuals (`<obs>_resid`
from the `calibrate` stage, section 18) over raw measurements, so a load change
does not look like degradation.

### 6.4 Triggers — simple rules

```yaml
triggers:
  kpi_step: {suction_pressure_bara: 2.0}          # |value - baseline| > 2.0
  criteria: {polytropic_efficiency: "< 0.785"}    # >, <, >=, <=
```

Step and criterion triggers fire when the rule is **crossed**, so a persistent
state raises one trigger, not one per day.

### 6.5 Stages — the order of work

```yaml
stages: [sense, evidence, refresh, "script:station_model", kpis, drift, goal, diff, ledger, digest, notify, agent]
```

| Stage | Does |
|-------|------|
| `sense` | Detects a changed brief, new reference documents, a new NeqSim commit |
| `refresh` | Pulls new rows from every source |
| `script:<name>` | Runs one of your stage scripts |
| `kpis` | Computes the plan KPIs |
| `drift` | Updates the drift monitor and raises `drift:<signal>` alarms |
| `goal` | Checks whether the task must reopen |
| `diff` | Compares KPIs with the baseline and the previous cycle; step/criterion triggers |
| `ledger` | Adds new proposals and tracks KPI values of ledger items |
| `digest` | Writes `digest.md` for the cycle |
| `notify` | Sends the digest to the configured channels |
| `agent` | Launches a headless agent when a trigger fired (section 11) |
| `inputs` | Reads the engineers' comments and restrictions (`user_input.yaml`, section 17) |
| `calibrate` | Updates the ensemble of uncertain model parameters from this cycle's measurements (section 18) |
| `gates`, `constraints`, `guard`, `outcome` | Production loop: model-validity gates, constraint slack, proposal guard, realised-vs-predicted gain (section 17) |

### 6.6 Notifications

```yaml
notify:
  channels:
    - file                                                         # always: digest.md
    - {type: email, host: smtp.example.com, to: [you@example.com], user: you,
       password_env: NEQSIM_SMTP_PASSWORD, when: [needs_decision]}
    - {type: teams, url_env: NEQSIM_TEAMS_WEBHOOK, when: [needs_decision, stop_state]}
```

`when` is any of `every`, `needs_decision`, `trigger`, `stop_state`. Secrets are
never written in the plan: give the **name** of an environment variable. Email
and webhooks are suppressed in dry runs and backtests.

### 6.7 Solve, backtest and report settings

```yaml
solve:
  stages: [sense, evidence, "script:solver", goal, diff, ledger, digest]
  max_rounds: 12
  max_branches: 3            # keep at most 3 candidate branches open
  critic: {enabled: false}   # independent agent review before goal_met/converged

backtest:
  expected:
    - {trigger: "drift:polytropic_efficiency", onset: "2026-01-29", max_delay_days: 45}

report: {formal: on_promote}   # never | on_promote | every_cycle
```

`report.formal` controls the Word/HTML report. `on_promote` is recommended: the
formal report then always matches the promoted baseline. `LIVING_REPORT.md` is
rewritten after every cycle regardless.

`report.prepare` names a task-local script (`file.py` or `file.py:function`) that
the runner executes before every formal report. Give it the job of turning the
latest complete cycle into `results.json` and the figures the report needs; then
`neqsim task-report <task> --formal` is a one-command report on request, and the
`on_promote` / `every_cycle` reports are built from current cycle data rather than
from a stale `results.json`. A failing prepare script is logged and the report is
built from the existing `results.json`.

---

## 7. Write a stage script

Put task-specific work — a NeqSim model, a calibration, an optimiser — in a
small Python file under `continuous/stages/`. A stage is one function that takes
the cycle context and returns a dictionary.

```python
"""Stage script: suction gas density from the rows pulled this cycle, computed with NeqSim."""
from neqsim_dev_setup import neqsim_init


def run(ctx):
    rows = ctx.new_rows.get("station", [])
    if not rows:
        return {"status": "warn", "message": "no new rows"}
    pressure = sum(r["suction_pressure_bara"] for r in rows) / len(rows)
    temperature = sum(r["suction_temperature_C"] for r in rows) / len(rows)

    ns = neqsim_init(recompile=False, verbose=False)  # reuses the JVM on later cycles
    fluid = ns.JClass("neqsim.thermo.system.SystemSrkEos")(273.15 + temperature, pressure)
    fluid.addComponent("methane", 0.90)
    fluid.addComponent("ethane", 0.07)
    fluid.addComponent("propane", 0.03)
    fluid.setMixingRule("classic")
    ns.JClass("neqsim.thermodynamicoperations.ThermodynamicOperations")(fluid).TPflash()
    fluid.initProperties()
    return {"kpis": {"suction_density_kg_m3": float(fluid.getDensity("kg/m3"))}}
```

Register it and add it to the stages:

```yaml
scripts:
  gas_density: {file: continuous/stages/gas_density.py, function: run}
stages: [sense, evidence, refresh, "script:gas_density", kpis, drift, diff, ledger, digest, notify]
```

What the context gives you:

| Attribute | Content |
|-----------|---------|
| `ctx.new_rows[source]` | Rows pulled this cycle, numbers already converted |
| `ctx.kpis` | KPIs computed so far this cycle |
| `ctx.baseline`, `ctx.goal`, `ctx.plan` | The promoted baseline, the goal and the plan |
| `ctx.task_dir`, `ctx.cycle_dir`, `ctx.state_dir` | Task, cycle and `continuous/` folders |
| `ctx.now`, `ctx.mode`, `ctx.dry_run` | Cycle clock, `monitor`/`solve`/`backtest`, dry-run flag |

What you may return (all keys optional):

| Key | Effect |
|-----|--------|
| `kpis` | KPI values added to this cycle |
| `proposals` | New ledger items: `{"title", "category", "kpi", ...}` |
| `triggers` | Extra trigger names |
| `status`, `message` | `ok`, `warn` or `fail`, with a reason |
| `solve` | Solve-loop result (below) |

A **solver stage** for `task-solve` also returns the validated objective, the
reachable bound and the candidate next actions:

```python
return {"kpis": {"power_reduction_pct": value},
        "solve": {"value": value, "validated": True, "confidence": "high",
                  "action": "lower suction temperature to 297 K",
                  "upper_bound": bound,
                  "candidates": [{"action": "lower suction temperature further",
                                  "branch": "cooling", "p_success": 0.9,
                                  "predicted_delta": 0.3}]}}
```

For a full flowsheet, load the model once in the script and use
`ProcessAutomation.evaluate()` for each trial and `getUtilizationSnapshot()` for
capacity — both return JSON and never throw, so one bad candidate degrades one
round, not the loop. Keep the heavy model in the script; keep the plan declarative.

Do not type `p_success` and `predicted_delta` by hand when you can sample them:
run each candidate once per member of the calibrated ensemble (or through a
surrogate) and pass the gains to `candidate_from_samples` (section 18).

---

## 8. Backtest before you trust it

A backtest replays archived data with a simulated clock and scores the monitor
against the events you expected. Live state is never touched.

```powershell
neqsim task-backtest <task> --start 2025-10-02 --end 2026-09-30 --repeat
```

| Option | Meaning |
|--------|---------|
| `--step-hours 24` | Simulated time between cycles |
| `--name backtest` | Run folder name under `continuous/backtests/` (use several names to keep several runs) |
| `--repeat` | Run twice and report whether the result is reproducible |

The report (`continuous/backtests/<run>/backtest_report.md`) gives detected and
missed events, detection delay, false alarms per month and reproducibility. Tune
`min_sigma` and `confirm` until every expected event is found within its
`max_delay_days` and false alarms are close to zero — by hand, or with
`--tune` (section 8.3). Record the result in
`results.json` `validation`. **Schedule only after a clean backtest.**

A run whose `backtest.expected` list is empty cannot score anything: its
triggers are reported as `other_triggers` and nothing can be learned from it.
Label the events first — maintenance and wash dates, trips, known step changes
— from the maintenance system, the alarm log or the operators.

---

## 8.1 Stop today, resume tomorrow

The **task folder is the hand-off**, not the chat session. Completed cycle stages, the
improvement ledger, baselines, checkpoints and `continuous/state.json` are persisted as
work proceeds. On another day — or after copying/synchronising the task folder to another
supported machine — start with:

```powershell
neqsim task-status <task>     # five-second view: conclusion, evidence, changes and next action
neqsim task-resume <task>     # continue an interrupted solve/cycle, if there is one
```

`task-status` reports the goal, current conclusion, baseline/best validated result,
attempts and rejected hypotheses, blockers, validation/evidence state, what changed since
the last run, the recommended next action, and last/next-run information. It is derived
from persisted task evidence and does not require conversation history.

Persisted task state uses an explicit schema. State written by the earlier 1.0 runner is
migrated to schema 1.1 without dropping unknown fields. A task written by a newer
incompatible schema fails closed with an upgrade message instead of being silently
rewritten. The persisted identity is task-relative and records writer provenance rather
than checkout-absolute paths, so moving a task between machines does not invalidate it.

If a process stops after a cycle begins, `task-resume` reuses the same cycle id and skips
already completed calculation stages even when the wall clock or host changes. Production
gates, constraints and proposal validation run again against the restored values before
advice can be published; a saved successful stage status does not restore its filtering.
The input stage reapplies its frozen restrictions without repeating input side effects.
An unavailable input checkpoint blocks advice. Completed digest,
notification and agent-review metadata are restored when such stages are skipped, so a
resumed cycle cannot erase already recorded side effects. If a solve cycle completed but
the process died before `state.json` was checkpointed, the solve loop adopts that completed
cycle before starting another attempt. An active `continuous/LOCK` still protects against
two concurrent writers.

The **Status** view is the five-second operational summary. The **Living Report** keeps the
understandable history and change detail. The ordinary Task Solver report remains the clean
**current-best engineering report**. The **Final Report** is an explicitly reviewed, immutable
delivery revision generated from that same canonical report pipeline; it excludes transient
cycle history and points to the Living Report, ledger, promoted-cycle manifest and work record
for auditability.

## 8.2 Update when evidence changes

Use the task folder as the source of truth when a datasheet, drawing, standard, measurement,
requirement, assumption, model or calculation input changes:

```powershell
neqsim task-status <task>     # shows pending files, affected stages/conclusions and next action
neqsim task-update <task>     # records provenance and reruns the affected calculation stages
```

`task-living` creates `continuous/evidence/inventory.json`, a task-relative SHA-256 inventory.
The default inventory covers the reference collection, `SOURCES.md`, scope/analysis files,
`user_input.md`, `study_config.yaml`, `continuous/goal.yaml` and `continuous/user_input.yaml`.
Override `evidence.include` or
`evidence.exclude` in `cycle_plan.yaml` for task-specific technical inputs. Absolute paths are
never persisted, so the inventory remains portable with the task folder.
The inventory has an explicit schema; an inventory written by a newer incompatible runner
fails closed with an upgrade instruction instead of being silently rewritten.

Map each evidence family to the stages and conclusions it can affect:

```yaml
evidence:
  include:
    - "step1_scope_and_research/references/**/*"
    - "step2_analysis/models/**/*"
  rules:
    - name: compressor-datasheet
      match: "**/compressor-datasheets/**"
      stages: ["script:compressor_model"]
      kpis: [compressor_power_MW, driver_margin_pct]
      conclusions: ["compressor power", "driver margin"]
  dependencies:
    "script:compressor_model": [refresh]
```

When every changed path matches a rule, `task-update` reruns only the affected stages, their
declared dependencies, and the common KPI/goal/diff/ledger/digest pipeline. List recalculated
metrics under `kpis`; those values are invalidated before the selected stages run. Other KPIs from
the latest completed cycle are carried forward as unaffected results, with the source cycle recorded in
`cycle.json`. Added, modified and removed files retain before/after hashes in the immutable cycle
folder. If an affected KPI is not recomputed, the cycle is degraded and the evidence baseline does
not advance. If any changed path is unmapped, the runner fails closed to the full planned cycle; it
never silently assumes that a conclusion is unaffected. A degraded update does not advance the
accepted inventory, so the same change remains pending for a safe retry.

The Living Report shows the file changes, provenance, affected conclusions and rerun scope. The
ordinary Task Solver report remains the current-best engineering report and is regenerated only
through the existing canonical report settings (`report.formal`) or `task-report --formal`.

---

## 8.3 Tune the drift settings from a backtest

Drift alarms depend only on the KPI values a cycle recorded, so once a backtest
has run you can try other settings without rerunning NeqSim:

```powershell
neqsim task-backtest <task> --tune --name pilot-365d --holdout-from 2026-06-01
```

It replays `continuous/backtests/<run>/cycles/*/kpis.json` through the drift
monitor for every combination of a grid over `lambda`, `L`, `h`, `confirm` and a
scale on each signal's `min_sigma`, scores each against the `drift:*` events in
`backtest.expected`, and writes `drift_tuning.json` with the ranking:

| Field | Meaning |
|-------|---------|
| `current` | Detections, misses and false alarms with the settings in the plan |
| `best` | The best-scoring combination and its detections, misses, false alarms, mean delay |
| `holdout` (per candidate) | The same score on the events from `--holdout-from` on, which were not used to rank |
| `validated` | True when the best candidate misses nothing on the held-out events and adds no false alarms there |
| `recommended_drift_settings` | A ready `drift.settings` block (per-signal `min_sigma` already scaled) |
| `ranking` | The top ten combinations |

The score is detections minus `--false-alarm-weight` per false alarm (default
0.5) minus `--delay-weight` per unit of normalised delay (default 0.25). Raise
the false-alarm weight for a monitor that notifies people; lower it for one that
only writes a digest. On a period with no noise events the tuner will favour the
most aggressive settings, because nothing penalises them — that is why the
holdout exists. **Always give `--holdout-from`** when there are at least two
labelled events; a result without one is marked in-sample and should be
confirmed on a later period before it is scheduled.

The plan is **not** edited: copy the recommended block into `cycle_plan.yaml`,
rerun the backtest to confirm, and record the result in `results.json`
`validation`. The command refuses to run when `backtest.expected` has no `drift:*`
events — a monitor that was never told which faults it should have found cannot
be tuned, only inspected.

---

## 9. Run cycles by hand

```powershell
neqsim task-cycle <task>                     # one monitor cycle, now
neqsim task-cycle <task> --dry-run           # no watermarks, ledger or drift state written
neqsim task-cycle <task> --stages sense,refresh,kpis,digest
neqsim task-cycle <task> --no-agent          # never launch an agent this cycle
neqsim task-solve <task> --until goal        # stop at goal_met (or another stop state)
neqsim task-solve <task> --until converged   # ignore the target, stop when gains are marginal
neqsim task-solve <task> --reset             # forget earlier solve rounds
```

Each cycle prints its digest. The cycle id is the UTC time and host
(`2026-09-30T0500Z@myhost`); a rerun in the same minute gets `-r2`. A crashed
cycle is resumed by running it again: completed calculations are reused and production validation is rerun.

---

## 10. Make it run by itself

### 10.1 On a Windows laptop or workstation

```powershell
neqsim task-schedule <task> --daily 05:00            # preview the command
neqsim task-schedule <task> --daily 05:00 --install  # create the scheduled task
neqsim task-schedule <task> --show                   # check it
neqsim task-schedule <task> --remove                 # delete it
```

`--install` creates a Windows Task Scheduler entry named
`NeqSim living task <folder>` that runs
`python devtools\neqsim_cli.py task-cycle <task> --mode monitor --standard-first`
with the shared interpreter. By default it runs only while you are signed in. To
run it while signed out, open Task Scheduler, select the task and choose
*Run whether user is logged on or not* (Windows asks for your password there;
the runner never sees it).

Scheduled cycles are intended to maintain a solved engineering task, not replace
the first solve. The generated command therefore includes `--standard-first`:
before every scheduled cycle, NeqSim checks that the initial Standard task basis
exists and tries to create missing formal report/work-record artifacts with the
normal report tools.

### 10.2 On a Linux server

Use `--every-hours 2` instead of `--daily` for a recurring interval. Supported
intervals are 1, 2, 3, 4, 6, 8, 12 and 24 whole hours, anchored at local midnight
on both Windows and cron. Other intervals are rejected because cron hour steps
reset each day and would not preserve the requested spacing. Use only one timing
option; daily times must be between 00:00 and 23:59. These are local wall-clock
schedules, so daylight-saving transitions may change elapsed spacing.

Windows runs a quoted `continuous/run_cycle.cmd` wrapper to support paths with
spaces. If the wrapper path itself exceeds the Task Scheduler command limit,
installation reports a failure and asks for a shorter task path.

`task-schedule` prints the cron line instead of installing it:

```bash
neqsim task-schedule /data/tasks/<task> --daily 05:00
crontab -e      # paste the "cron" line from the output
```

Server checklist:

1. Clone NeqSim, build once (`./mvnw compile`) and set `NEQSIM_PROJECT_ROOT`.
2. Install the Python dependencies and any adapter packages (section 14).
3. Set `NEQSIM_CONTINUOUS_HOST` so cycle ids show which machine ran them.
4. Set `data_root` in the plan to local disk if the task folder is on a share.
5. Put notification secrets in the service environment, not in the plan.

### 10.2b Live loops at sub-hour cadence

The scheduler starts a new interpreter per cycle, which is right for daily work
and wrong for a ten-minute advisory loop: every cycle would pay the JVM and the
model build again. `task-loop` keeps one process alive and runs cycles at a fixed
cadence:

```powershell
neqsim task-loop <task> --every-minutes 10 --for-hours 8 --no-agent
neqsim task-loop <task> --every-minutes 10 --for-hours 3 --hook continuous/stages/plant_emulator.py:before_cycle
```

- Progress in `continuous/loop.log` and `loop_state.json` (per cycle: id, status,
  degraded, triggers, seconds; overruns are flagged). Create `continuous/STOP` to
  end the loop early; `--max-cycles` bounds a trial run.
- `--hook module.py:function` is called before every cycle with
  `(task_dir, index, now)` — the place for a plant emulator or a test harness that
  injects evidence changes. Hook and stage errors are logged and the loop continues.
- Stage scripts are re-executed every cycle, so cache a simulator on a module
  imported from `step2_analysis` (a `shared()` class method), not on the stage
  module's globals, or each cycle rebuilds it.
- Run it detached (`Start-Process` with quoted paths, or `nohup` on Linux) so the
  loop survives the terminal; one loop per task folder (the `LOCK` enforces it).

### 10.3 Running on more than one machine

Only one cycle runs at a time per task: a `continuous/LOCK` file blocks a second
run and expires after 6 hours if a run crashed. If a laptop and a server keep
separate copies of a task, merge their ledgers:

```powershell
neqsim task-ledger <task> merge \\server\tasks\<task>\continuous\ledger\events.jsonl
```

The ledger is append-only, so a merge is the union of events and never loses a
decision.

---

## 11. Standard-first gate for scheduled tasks

The Standard-first gate is also available for a manual cycle:

```powershell
neqsim task-cycle <task> --standard-first
```

Use it before switching an ad-hoc living task to a server or laptop schedule.
The gate checks that the task has:

- Step 1 scope/research files, including capability assessment;
- Step 2 runnable model, notebook or script, or an explicit data-gap blocker;
- `results.json` with assumptions/data gaps;
- consistency-check status where available;
- first formal Word/HTML report; and
- `step3_report/WORK_RECORD.md`.

When the report or work record is missing, the gate calls the same configured
tools as the normal workflow (`neqsim report`, `neqsim work-record`, and the
consistency checker when present). It does not fabricate missing inputs. If
documents, live data, engineering limits or validation evidence are unavailable,
the correct outcome is a first report that states the data gap and explains what
was assumed instead.

Before judging the basis, the gate runs the automatic document retriever
(`devtools/doc_retriever.py`, also `neqsim fetch-docs <task>`) when a retrieval
backend is configured. It infers the installation from the task text, pulls the
highest-ranked P&IDs and data sheets into `references/stid/`, re-indexes
`SOURCES.md`, and records a `document_retrieval` action. A successful retrieval
is reused for 24 h; a failed one is retried after 6 h. The `controlled_documents`
check shows whether any controlled PDFs are present. Its status file,
`references/stid/retrieval_status.json`, names the concrete blocker
(`no_backend`, `no_installation`, `no_matches`, `auth_error`), so "no documents
found" is never reported after searching only the standards library.

The audit is written to `continuous/standard_first_status.json`:

| Field | Meaning |
|-------|---------|
| `ready` | True only when the initial Standard task basis is complete |
| `readiness` | `ready`, `blocked` or `incomplete` |
| `checks` | Per-artifact pass/fail map |
| `hard_missing` | Items that still prevent operational scheduled monitoring |
| `actions` | Document retrieval, report, work-record and consistency commands attempted by the gate |

If the gate cannot finish the Standard basis, the cycle may still run to collect
evidence, but it is marked degraded and raises a `standard_first:*` trigger.
Treat that trigger as a work item: finish the report, fill the work-record
narrative, or record the blocker/data gap explicitly.

The gate is deliberately about task readiness, not process correctness. A task
can pass Standard-first and still block operational recommendations if its model
representativeness, tag map, equipment limits or safety basis are insufficient;
those are domain-specific KPIs handled by the cycle plan and stage scripts.

---

## 12. Let an agent handle the triggers

When a cycle raises a trigger and the plan enables it, the `agent` stage starts
a bounded, headless GitHub Copilot CLI session with the
`continuous-improvement` agent and a triage prompt:

```yaml
agent:
  enabled: true
  agent: continuous-improvement
  executable: copilot
  timeout_s: 1800
  # deny: ["shell(git push)", "shell(git commit)", "shell(gh pr create)"]   # the default
```

The session may read the task and write only inside it. It writes
`cycles/<id>/agent_review.md` and may add ledger items with status `proposed`.
It cannot push, commit, open pull requests, promote a baseline or accept a
ledger item. The exact command is stored in `cycle.json` so a reviewer can see
what ran. If `copilot` is not installed the cycle records `not_installed` and
continues.

You can also work with the agent interactively in VS Code Copilot Chat: pick
**continuous-improvement** in the agent picker and ask, for example:

- "Make `<task>` living, compile the goal from its brief and backtest it."
- "Triage the last cycle of `<task>`."
- "Solve `<task>` until converged and tell me which cycle to promote."
- "Create the reference case in `C:\tmp\living`."

The agent follows the same rules: it prepares, you decide.

---

## 13. Day-to-day work with a living task

### 13.1 Read the living report

`continuous/LIVING_REPORT.md` is the one page to open. It is rebuilt after every
cycle, solve round, backtest, promotion, reopen and ledger decision. It starts with
**In short**: what the task is trying to achieve, the result so far against the first
baseline, the status in plain words, the triggers of the latest cycle explained in
sentences, and **what needs your attention**. A short glossary (KPI, baseline, cycle,
trigger, drift, solve round, ledger, backtest) follows, and every section opens with one
sentence saying what it shows.

The report is figure-led. Pictures are written to `continuous/report/` and linked from the
Markdown; a figure with no data is skipped:

| Figure | Shows |
|--------|-------|
| `workflow_of_the_living_task.png` | The loop (data, cycle, triggers, solve, review/promote, final report) with real counts and the current position |
| `how_the_inputs_changed.png` | Rows read per cycle for every data source (database, historian, live feed, files) and when evidence, brief or NeqSim-version changes arrived |
| `objective_progress_by_solve_round.png` | The objective after each solve round against the first baseline and the target |
| `baseline_progression.png` | The objective at each reviewed baseline |
| `change_since_first_baseline.png` | Change of every KPI between the first and the current baseline |
| `kpi_trends_with_triggers.png` | Each KPI per cycle against the baseline, triggering cycles marked |
| `monitoring_events_timeline.png` | Which triggers fired, and when |
| `decision_ledger_status.png` | Improvement proposals by status |

The tables cover the state and stop reason, the inputs and how they changed, the latest KPIs
against the baseline (with a plain-words reading), solve rounds, events with an explanation
of each trigger, the ledger with pending decisions, the baseline history and backtests.
Never edit it; rebuild it with `neqsim task-report <task>`.

For all living tasks in a folder at once:

```powershell
neqsim task-status C:\path\to\task-root
```

### 13.2 Triage a triggered cycle

Open `continuous/cycles/<id>/`:

| File | Tells you |
|------|-----------|
| `digest.md` | Short summary and whether a decision is needed |
| `triggers.json` | Which rules fired |
| `validation.json` | Drift statistics per signal |
| `diff_vs_baseline.json` | Every KPI against the baseline and the previous cycle |
| `refresh_report.json` | What each source returned |
| `agent_review.md` | The agent's analysis, if it ran |

Classify each trigger: data fault, model mismatch, real plant change, or new
opportunity. A data fault usually means fixing the source, not the plant.

### 13.3 Decide ledger items

```powershell
neqsim task-ledger <task> list
neqsim task-ledger <task> show OPP-0002
neqsim task-ledger <task> set OPP-0002 accepted --by "A. Engineer" --note "wash planned week 42"
```

Allowed transitions:

| From | To |
|------|----|
| `proposed` | `under_review`, `accepted`, `rejected`, `superseded` |
| `under_review` | `accepted`, `rejected`, `superseded` |
| `accepted` | `implemented`, `rejected`, `superseded` |
| `implemented` | `verified`, `not_verified` |
| `not_verified` | `under_review`, `superseded` |

Categories are `operational`, `maintenance`, `modification`, `model`, `data` and
`tooling`. An item that names a `kpi` gets that KPI's value recorded every
cycle, so after `implemented` you can see whether it worked before you set
`verified`.

### 13.4 Promote a cycle to the baseline

```powershell
neqsim task-promote <task> 2026-09-30T0500Z@myhost --reviewer "A. Engineer" --note "after wash"
```

The previous baseline is archived under `baseline/history/`. The cycle's KPIs
are merged over the old baseline, so promoting a solve round (which reports only
the objective) keeps the baseline of the monitored KPIs. With
`report.formal: on_promote` the Word/HTML report is regenerated; otherwise run
`neqsim report <task>`.

### 13.5 Generate the Final Report

After the latest complete, non-degraded cycle has been reviewed and promoted, and no evidence
change is pending:

```powershell
neqsim task-report <task> --final --reviewer "A. Engineer" --note "Issued for delivery"
```

This invokes the canonical Task Solver Word/HTML generator; it does not fork or replace the
ordinary report architecture. The Final Report is a **separate document**: the task's original
report in `step3_report/` and the task's own `figures/` folder are not touched. Final delivery
revisions are written under `step3_report/final/FR-001/`,
`FR-002/`, and so on. Each revision includes Word and self-contained HTML plus
`FINAL_REPORT_MANIFEST.json` with task-relative provenance and SHA-256 hashes. Add `--pdf` when
the configured host has a supported Word/LibreOffice PDF renderer.

A Final Report contains everything the original report contains (it is built from the same
promoted `results.json`) plus what only a live study can show:

- a section **How the Live Study Developed**: the aim, how the work went (cycles, triggers,
  solve rounds, baselines, proposals), the first baseline against the final one for every KPI,
  the **inputs** the study read and how they changed (data sources, evidence files, brief,
  NeqSim version), the flags raised and how far the monitoring can be trusted (last backtest);
- up to eight figures from the table in section 13.1, saved in
  `step3_report/final/FR-xxx/figures/` and numbered with the other figures in the Results
  section, each with a caption.

The command fails closed if a cycle is interrupted, the promoted cycle is degraded, evidence is
pending, `results.json` differs from the promoted snapshot, or the final-report schema is newer
than the installed runner. Repeating the same finalization is idempotent; a later promoted result
creates the next revision and preserves earlier deliveries. `task-status` and the Living Report
show the current final revision and whether changed report inputs have made it stale.

The generated report contains a short **Final Report Record** appendix. It identifies the
reviewer, promoted baseline and source cycle, and references—but does not copy—the transient
history in:

- `continuous/LIVING_REPORT.md` (history and change view);
- `continuous/ledger/events.jsonl` (decision trail);
- `continuous/cycles/<id>/cycle.json` (promoted-cycle evidence);
- `step3_report/WORK_RECORD.md` (method, sources and reproducibility record).

#### Multi-day acceptance example

1. **Day 1:** run `task-solve`; close VS Code while a later cycle is incomplete.
2. **Day 2:** copy or reopen the task folder on another supported machine. Run `task-status`,
   then `task-resume`. Persisted task state—not chat history—restores the cycle and completed
   stages. Review and promote the validated result.
3. **Day 3:** add a revised public datasheet under the task references. `task-status` identifies
   the changed hash and affected conclusions; `task-update` performs the mapped selective rerun
   and retains unaffected KPIs. Review and promote the updated result.
4. Run `task-report --final --reviewer NAME`. The ordinary current-best report remains in place,
   the Living Report retains the three-day history, and `FR-001` is the standalone delivery.

The synthetic compressor reference case and deterministic devtools tests exercise this sequence,
including Word/HTML generation, audit references, idempotent re-issue, stale-report detection and
fail-closed newer-schema handling.

#### Software acceptance coverage

The acceptance scope of [#4187](https://github.com/equinor/neqsim/issues/4187) is
the persistent Task Solver workflow. The checks below exercise the existing
implementation; they do not qualify a production model, equipment limits or an
external agent's engineering judgment.

| Acceptance criterion | Executable evidence |
| --- | --- |
| Five-second status, conclusions, attempts, rejected hypotheses, blockers and next action | `test_five_second_status_and_resume_cli` checks the complete status contract |
| Stop/resume without chat history | `test_cycle_resumes_same_id_on_another_day` and `test_solve_adopts_completed_uncheckpointed_cycle` retain cycle and solve identity |
| Agent/machine portability with version handling | `test_relocated_task_resumes_updates_and_finalizes_in_fresh_processes` moves and renames the whole task, removes each old location and uses four fresh interpreters with different host labels; `test_state_schema_migrates_and_future_schema_fails_closed` verifies migration and rejection |
| Interrupted-run recovery | The relocation test preserves the completed solver value and exactly three distinct attempts; the side-effect recovery test preserves notification and agent metadata |
| Evidence-change detection | `test_evidence_inventory_detects_added_modified_and_removed_files` checks relative paths and hashes; the relocation test detects a revised brief after handoff |
| Impact analysis and retention of unaffected results | `test_task_update_selectively_reruns_mapped_impact_and_retains_kpis` checks the selected stages and retained KPIs; unmapped and degraded updates have separate rejection tests |
| Result provenance | The selective-update tests check evidence hashes, affected conclusions and the accepted cycle; final-report tests check the promoted baseline and source record |
| Living Report | `test_living_report_follows_every_change` checks updates after solve, cycle, promotion and ledger changes |
| Current-best report | The relocation test generates ordinary Word/HTML reports through the canonical CLI, then verifies their bytes and output metadata survive finalization |
| Final report | `test_multi_day_final_report_uses_canonical_pipeline_and_is_immutable` checks Word/HTML content, figures, audit links, idempotence and stale results; the relocation test checks output hashes and re-issue after a second move |
| CLI ergonomics | Status/resume, update and final-report dispatch tests exercise the public CLI; commands and examples are documented in this guide |
| Focused validation | State, evidence, interrupted work, report-schema rejection, selective updates and production-evidence guards run in the devtools suites |

These tests are in
[`devtools/test_neqsim_living_tasks.py`](../../devtools/test_neqsim_living_tasks.py),
with related foundation, production and canonical-report suites alongside it.
The relocation test starts each interpreter outside the task and source checkout.
It disables external agents and uses the public synthetic compressor calculation;
it does not require an LLM, historian connection or JVM. Host labels model handoff
provenance, not distinct operating systems. Compatible Python dependencies, task
scripts and separately configured external adapters are still required on a new
machine. An active task lock continues to prevent concurrent writers.

### 13.6 When the task reopens

A monitoring task reopens when the objective drops more than `regress_margin`
below the baseline, or on `new_evidence` (the references manifest changed),
`brief_changed`, `neqsim_capability` (a new NeqSim commit) or a reviewer request.
The living report shows the reason; run `neqsim task-solve <task>` to continue.

---

## 14. Company data sources

The runner is public-first. The only built-in adapter is `file`, which reads CSV
exports dropped into a folder, so every task works without company access.
Additional adapters install as Python packages and are found automatically:

| Adapter name | Package | Reads |
|--------------|---------|-------|
| `file` | built in | CSV files in a folder |
| `tagreader` | `neqsim-continuous-improvement-toolkit` (community skills) | OSIsoft PI and Aspen IP.21 via tagreader |
| `ots` | `enterprise-continuous-improvement-adapters` (enterprise skills) | Omnia Timeseries hourly aggregates |
| `pdm` | `enterprise-continuous-improvement-adapters` (enterprise skills) | PDM daily allocated production |

Use the name in `sources.<name>.adapter`. A plugin can also be named by its
import path, `package.module:Class`, which works even without package metadata.
A missing adapter is not an error: the source is recorded as `not_installed`,
the cycle is marked **degraded**, and it continues.

To write your own adapter, implement a class whose constructor takes the
`options` and whose `pull(since, until, out_dir)` returns a `SourceResult`
(see `devtools/neqsim_continuous/adapters.py` for the file adapter), then
register it under the entry-point group `neqsim_continuous.adapters`. Stages and
notifiers use the groups `neqsim_continuous.stages` and
`neqsim_continuous.notifiers` the same way.

Never commit `continuous/data/` or plant data to a public repository.

---

## 15. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Task is locked by ... until ...` | Another cycle is running, or one crashed | Wait, or delete `continuous/LOCK` if no cycle is running |
| `goal.yaml is not confirmed` | `confirmed_by` is empty | Confirm the goal (section 5) |
| `goal.yaml has no objective.metric` | The goal was not filled in | Set `objective.metric` to a KPI a stage produces |
| Source `stale` | No new rows for `stale_after_hours` | Check that the export or historian is still delivering |
| Source `not_installed` | Adapter package missing | Install it into the shared environment (section 14) |
| Cycle `degraded` | A stage failed or was skipped, or a source was not `ok` | Read `cycle.json` stage messages; rerun the cycle to resume |
| Final report says evidence changed | Files differ from the accepted evidence inventory | Run `task-update`, validate the selected rerun, then promote it |
| Final report says results differ from baseline | `results.json` was edited or regenerated after promotion | Validate the result and promote its complete cycle before finalizing |
| `kpis` stage `warn: no data for ...` | The source returned no rows for that column | Check `column` and `source` names in the plan |
| Too many drift alarms | Engineering floor missing or too small | Set or raise `min_sigma`; raise `confirm`; backtest again, or run `task-backtest --tune` (section 8.3) |
| `--tune` says `backtest.expected has no drift:* events` | The backtest has no labelled events to score against | Add `{trigger: "drift:<signal>", onset: ...}` entries and rerun the backtest |
| `calibrate` warns `unidentifiable` | The measurements do not determine every parameter | Read `calibration.json` `unidentifiable_directions`; fix one parameter or add a measurement (section 18) |
| `calibrate` rejects a measurement every cycle | Gross error in the tag, wrong unit, or a model defect | Check `<obs>_z` and the tag; if the model is wrong, widen the prior or fix the model — do not raise `gate_sigma` to make it pass. After `reinit_after` cycles the stage widens the prior itself and raises `calibration:reinitialised` |
| `calibrate` warns `std_pending` | `std: auto` has fewer than eight history values | Wait, or give an explicit `std` |
| `calibrate` fails `model solved for only N of M members` | NeqSim did not converge for most of the ensemble | Tighten `lo`/`hi` to the feasible region, check the prior width, or lower `min_members` for a cycle |
| `<parameter>_std` shrinks to nothing after months | Ensemble collapse | Set `walk_std` on moving parameters and `inflation: 1.02` (section 18.1) |
| Same drift alarm never fires twice | Expected: a persistent shift is one alarm | If the plant recovered in between, check `recover` and the bounded CUSUM in `validation.json` |
| Agent `not_installed` | `copilot` is not on `PATH` of the scheduled task | Install and sign in to GitHub Copilot CLI, or set `executable` to its full path |
| `neqsim` not recognized | CLI not on `PATH` | Use `python devtools\neqsim_cli.py task-...` with the shared interpreter |
| `PermissionError` on OneDrive | Sync client holds the file | The runner retries; move large data with `data_root` |

---

## 16. Command reference

| Command | Purpose |
|---------|---------|
| `neqsim task-living <task> [--brief FILE] [--template production]` | Make a task living (never overwrites); `production` adds gates, constraints, guard and `user_input.yaml` |
| `neqsim task-note <task> [TEXT] [--lever N --lo X --hi Y \| --freeze N \| --constraint N --kpi K --limit X] [--setting K=V] [--by NAME] [--expires DATE] \| --list \| --resolve ID` | Add, list or resolve engineer comments and restrictions |
| `neqsim task-cycle <task> [--mode monitor\|solve] [--stages a,b] [--dry-run] [--no-agent] [--now ISO]` | Run one cycle |
| `neqsim task-solve <task> [--until goal\|converged] [--max-rounds N] [--no-agent] [--allow-unconfirmed] [--reset]` | Solve loop |
| `neqsim task-resume <task> [--max-rounds N] [--no-agent]` | Resume interrupted cycle/solve from persisted task state |
| `neqsim task-update <task> [--no-agent] [--dry-run]` | Detect file-level evidence changes, analyze impact and rerun only mapped stages (full cycle for unmapped changes) |
| `neqsim task-backtest <task> --start ISO --end ISO [--step-hours 24] [--name N] [--repeat]` | Replay archived data |
| `neqsim task-backtest <task> --tune [--name N] [--holdout-from ISO] [--false-alarm-weight 0.5] [--delay-weight 0.25]` | Choose drift settings from a finished backtest, validated on held-out events (no NeqSim rerun; plan not edited) |
| `neqsim task-schedule <task> [--daily HH:MM] [--install\|--remove\|--show]` | Schedule monitor cycles |
| `neqsim task-loop <task> --every-minutes N --for-hours H [--mode monitor\|solve] [--hook file.py:func] [--no-agent] [--max-cycles N]` | Run cycles at a fixed sub-hour cadence in one process (warm model); `continuous/STOP` ends it early |
| `neqsim task-promote <task> <cycle-id> --reviewer NAME [--note TEXT]` | Promote a cycle to the baseline |
| `neqsim task-ledger <task> [list\|show ID\|set ID STATUS --by NAME [--note TEXT]\|merge FILE]` | Improvement ledger |
| `neqsim task-status [task-or-folder]` | Status of one task, or of all living tasks in a folder (default: the task root) |
| `neqsim task-report <task> [--formal]` | Rebuild the Living Report (`--formal` also runs `report.prepare` and rebuilds the ordinary current-best Word/HTML report) |
| `neqsim task-report <task> --final --reviewer NAME [--note TEXT] [--pdf]` | Generate or reuse an immutable reviewed Final Report revision through the canonical report pipeline |
| `neqsim task-reference-case [parent-folder]` | Create the public reference task (default: in the task root) |

`<task>` is a folder path, or the name of a folder inside the task root.

## 17. Production optimisation: advisory loop with engineer input

For a plant model that should be kept current and used to recommend operating changes, create the task with
`neqsim task-living <task> --template production`. The cycle then runs:

```text
sense -> evidence -> inputs -> script:model_update -> script:optimize -> kpis -> gates -> constraints -> guard
      -> drift -> goal -> diff -> outcome -> ledger -> digest -> notify
```

The loop is **advisory**: nothing is written to the control system, and every accepted proposal is marked
`requires_approval`. An engineer decides in the ledger (`task-ledger ... set ID accepted --by NAME`).

### 17.1 What the stages decide

| Stage | Rule |
|-------|------|
| `model_update` (your script) | Reads live data, updates and runs the model, returns KPIs and the residuals against the measurements |
| `gates` | `gates:` in the plan (`{name, kpi, abs_max\|max\|min}`). A failed or missing KPI blocks all advice (`model_gate:<name>`) |
| `constraints` | `constraints:` in `goal.yaml` (`{name, kpi, op, limit, margin, warn, hard, source}`). `margin` tightens the limit. A hard constraint with no `limit` is *unconfirmed* and blocks all advice |
| `optimize` (your script) | Searches the levers and returns `proposals` with `setpoints`, `expected_gain`, `predicted` KPIs, `objective_kpi`, `baseline_value`, and the fail-closed `optimizer_evidence` below; field studies may also return `accepted_point_envelope_evidence` and `lifecycle_envelope_evidence` |
| `guard` | Keeps a proposal only if its numeric data are finite, its evidence belongs to the current cycle and proves a fully validated/replayed candidate, it has a gain above `production.min_gain`, and every hard constraint is predicted inside its margin. `production.require_envelope_evidence` requires a complete current accepted-point envelope bound exactly to the proposal. `production.require_lifecycle_envelope_evidence` additionally requires complete ordered lifecycle periods whose current-cycle period is that same accepted-point envelope. `guard.json` lists the reasons for every withheld proposal |
| `outcome` | For ledger items in status `implemented`: realised gain against predicted gain (`outcome_confirmed:` / `outcome_miss:`) |

`demonstrated_limit(values, quantile=0.99, design=...)` gives an equipment limit from operating experience: the
larger of the design value and a high quantile of historian samples. Use it where equipment has been run above
its design value before; record who accepted that.

Every optimizer proposal must include this evidence from the same cycle. The guard requires each boolean to be
exactly `true`; missing, false, or stale evidence withholds the proposal rather than inferring success:

```yaml
optimizer_evidence:
  cycle_id: <ctx.cycle_id>
  simulation_converged: true
  candidate_feasible: true
  candidate_finite: true
  constraint_evidence_complete: true
  state_restore_complete: true
  accepted_point_replayed: true
  actions_complete: true
  evaluation_count: 42
  runtime_seconds: 12.8
```

`state_restore_complete` covers restoration after every rejected/intermediate evaluation;
`accepted_point_replayed` confirms the selected point was then applied and re-simulated from a known state.
`actions_complete` must be false when any requested setpoint or routing action was rejected. The accepted ledger
item retains this block, including the positive integer evaluation count and finite non-negative runtime, so
resume and change-driven cycles preserve the incumbent's validation and performance provenance.

For a Java lifecycle study, do not hand-assemble the optimizer, accepted-envelope, and lifecycle blocks in Python.
Export the exact model result with
`ProcessModelOperatingEnvelopeLifecycleStudy.toEvidenceJson(ctx.cycle_id)`, then pass that string to
`neqsim_continuous.production.proposal_from_lifecycle_json(...)` in the task-local `scripts.optimize` stage. The
adapter selects exactly one current-cycle period, derives its ordered setpoints and accepted envelope, rejects
non-finite JSON, and replays the existing optimizer/envelope/lifecycle validators without changing the evidence.
The caller must still supply the engineering proposal title, objective KPI, finite baseline and expected gain, and
finite predicted KPI values. `report.prepare` remains a downstream `results.json` refresh hook; it does not publish
or bypass guarded proposals.

For field-to-facility studies, set `production.require_envelope_evidence: true` and attach the map from
`ProcessModelOperatingEnvelopeStudy.getAcceptedPointEnvelopeEvidence(...)` as
`accepted_point_envelope_evidence`. The guard revalidates the persisted JSON instead of trusting its Java origin:
the cycle and optimizer block must match; ordered `accepted_action_ids` and
`accepted_candidate_values` must bind exactly to `setpoints`; slice study/evaluator identities and anchors must
match; ordered axis sets must be unique; every slice must be complete; every point must have restored and
reconverged the baseline; and point, evaluation and runtime totals must reconcile. Exploratory points may be
physically infeasible or fail candidate convergence—the envelope records those boundaries—but they cannot leave
the live model partially restored. For accepted-envelope schema v2, the guard independently rebuilds the
cross-slice rejecting/near-active constraint summary from sampled points and final refined endpoints. It rejects
changed grouping identities, applicable limits or units, occurrence counts, ranking, threshold provenance, or
representative rows; physical margins with unlike units are never compared. The ordinary production template leaves this requirement disabled for backward
compatibility; a supplied envelope is always validated strictly. Accepted proposals retain the complete block in
the existing append-only ledger, so resume and evidence-change cycles do not depend on chat history or a second
state store.

For ordered lifecycle studies, also set `production.require_lifecycle_envelope_evidence: true` and attach the map
from `ProcessModelOperatingEnvelopeLifecycleStudy.toEvidenceMap(...)` as `lifecycle_envelope_evidence`. Keep
`accepted_point_envelope_evidence` on the proposal: exactly one lifecycle period must use the current task-cycle ID,
and that period's complete envelope map must equal the proposal's current accepted-point envelope. The guard then
revalidates every period independently, including strictly increasing finite elapsed time, common time unit and
ordered action identity, study/evaluator identity, complete slice and restoration evidence, aggregate counts and
runtime, accepted bottleneck identity, and the exact adjacent-period bottleneck-transition list. A stale lifecycle,
missing current period, changed forecast envelope, non-finite value, partial restoration, or aggregate/transition
mismatch withholds the proposal. Accepted lifecycle evidence is stored in the same append-only ledger and follows
the existing change-driven `evidence_current`, supersession and `revalidation_of` path; it does not create a second
task-state or optimization architecture.

Current lifecycle exports use schema version 8 and include exact sampled `period_change_diagnostics`. For each
adjacent period pair these record accepted-action deltas, comparable/added/removed slice axes, common sampled
points, feasible-to-infeasible changes, infeasible-to-feasible changes, and sampled leading-constraint changes.
They also compare the exact `operating_region_transitions` rows emitted by each matching slice, retaining counts
and the ordered events added or removed between periods. The guard reconstructs the complete diagnostic from the
persisted period envelopes and rejects any mismatch. It never interpolates changed grids, matches a discrete
line-up event to an intermediate state, or treats sampled counts as a continuous boundary.

When a reviewed cycle is promoted, `task-promote` selects schema-v8 evidence only from that cycle's current,
guard-approved ledger proposal and writes the exact block at the top level of the promoted `results.json` and
baseline snapshot. Promotion replays the production validation and fails closed if the evidence is stale,
superseded, rejected, edited, not guard-published, or ambiguous between distinct accepted proposals. This handoff
uses the existing append-only ledger and promotion boundary; it does not introduce a report-only state store. The
canonical current-best and final engineering-report pipeline adds a **Lifecycle Operating Envelope** section. The renderer
checks the promoted source-cycle identity, complete ordered periods, finite aggregate values, complete envelope
slices, exact representative constraint identity, and independently reconstructed retained/removed/added
constraint identities before showing any claim. Malformed, partial, stale, or edited evidence is replaced by a
fail-closed “evidence withheld” notice; report rendering does not replace the production guard that qualified the
proposal. Accepted rows retain each period's representative value and signed physical headroom in its own unit.
Ranks apply only within a period: the report does not compare unlike units or imply causality, shadow price,
equipment qualification, operating approval, or permission to relax a physical constraint.

Schema v8 additionally validates and compares `operating_region_boundary_refinements`. Each receipt
contains the exact feasible- and infeasible-endpoint constraint rows, including signed physical margin
and unit. The guard reconstructs those endpoint rows from the sampled points and midpoint sequence, so
changed or partially copied headroom evidence is withheld. Schema v8 also requires exact schema-v2
cross-slice summaries for every lifecycle period and derives retained, removed, and added ranked constraint identities
between adjacent periods. Identity retains classification, source, qualified name, applicable limit, physical unit,
limit role, and provenance. A classification or limit-basis change is therefore explicit, while rank, counts, and
physical headroom remain period evidence rather than cross-unit comparison keys. Schemas v1-v7 remain readable for stored
historical evidence. The guard
replays the midpoint arithmetic from the persisted trace, requires finite candidates plus complete
baseline restoration, accepts bracket updates only from converged feasible or explicit hard/validity
rejections, and checks terminal tolerance/status closure. Missing or edited refinement configuration,
midpoints, outcomes, recovery flags, brackets, summary identities, or lifecycle migration rows fail closed.

Schema versions 4 through 8 may additionally bind independently accepted physical-state metrics to every period. Each period must
carry the same ordered metric IDs and units, finite values, and explicit provenance. The guard reconstructs exact
adjacent-period state deltas and rejects missing, reordered, non-finite, unit-changed, or modified state evidence.
Use this boundary for accepted shared-reservoir pressure, production/injection rates, cumulative throughput and
shared-power snapshots; do not populate it from an unconverged, rejected, stale, partially restored, or interpolated
state. Schema versions 1 through 5 remain accepted for historical ledger evidence.

Map every file that defines routing, constraints, limits, or model inputs to the production `guard`, including
mapped network pump/compressor power, common-shaft speed and power, driver limits, and shared host-power limits.
Declare the production-stage dependencies that must rebuild its evidence:

```yaml
evidence:
  rules:
    - name: field-operating-definition
      matches: [study_config.yaml, continuous/goal.yaml]
      stages: [guard]
      conclusions: [validated field operating point and envelope]
  dependencies:
    guard: ["script:optimize", gates, constraints]
    "script:optimize": ["script:model_update"]
    gates: ["script:model_update"]
    constraints: ["script:model_update"]
```

`neqsim task-update <task>` then reruns the model, optimizer, constraints, and guard while retaining unrelated
cycle results. Map production and injection line-ups plus controlled reservoir-lifecycle boundaries and host-power
definitions through the same rule. The route selectors remain discrete process actions, while reservoir pressure
remains a model input synchronized through its owning `WellFlow.ReservoirLayer`. Read mapped network and standalone
rotating-equipment demand from the canonical `ProcessModel.getPower(...)` result and preserve the common-shaft,
driver, and shared-resource evidence; the task layer does not duplicate well, network, or rotating-equipment physics.
Non-finite mapped demand or a driver/shared-power breach produces no replacement proposal. Before any
model update, optimization, or guard runs, the evidence stage marks prior optimizer/envelope evidence in the ledger
`evidence_current: false`. Unfinished advice is superseded and cannot remain the current incumbent. A replacement
is a new `proposed` ledger item with `revalidation_of`, the changed evidence paths, the new cycle identity, and a
fresh envelope; this deliberately requires a new engineering review. If the revised limit or route leaves no safe
candidate, no replacement is created. Historical `implemented` records are retained for outcome traceability but
their validation evidence is still marked non-current. If a later operating definition becomes safe again, its new
item links to the most recent superseded item even when an intermediate cycle produced no proposal. Changes mapped
only to reporting stages do not invalidate
optimizer evidence. The Living Report exposes the evidence state for every ledger item. Unmapped changes and malformed
`evidence_sensitive_stages` configuration retain fail-closed invalidation behavior.

A malformed optimizer result or failed guard still leaves the old advice visibly stale, even when the guarded
ledger-publication stage is skipped. Retrying or resuming the same invalidation does not duplicate its events;
resuming after a replacement was already persisted preserves that cycle's fresh evidence. Resume restores the
original optimizer proposals and reruns the inexpensive gates, constraints and guard; withheld proposals remain
withheld, and accepted proposals retain their approval requirement. It does not rerun the completed model or
optimizer. The ledger also requires optimizer/envelope proposals to match the exact content accepted by the
current cycle's guard. This rejects custom plans that omit the guard, proposals inserted after the guard,
and changes to already guarded proposals. A dry run reports the
opportunities it would invalidate without changing the persisted ledger. The guard remains required before any
new proposal can be written.
Custom production plans must keep `evidence` before model update, optimization, and `guard`, as the production
template does. The ledger fallback alone cannot provide early invalidation when a failed guard skips publication.

### 17.2 Levers: every operator-adjustable parameter

Levers are listed under `production.levers` in the plan. A lever is any setting the operators can change:
separator pressures, heater and cooler outlet temperatures, valve pressures, and **well choke opening**.
The Snorre A reference task supports three kinds (see `continuous/stages/optimize.py` in that task):

| Kind | Fields | Note |
|------|--------|------|
| `separator_pressure` | `separator, step_bar, lo_barg, hi_barg` | Also sets the let-down valves into that separator |
| `well_rate` | `manifold, step_pct, lo_pct, hi_pct` | Choke opening, modelled as a rate scale on the manifold feed at constant GOR and water cut and unchanged arrival pressure. The proposal carries `needs_well_check` because the well's inflow and tubing limit are not in the model |
| `equipment_setpoint` | `tag, setter, getter, unit, step, lo, hi` | Any unit-operation setter, e.g. an outlet temperature. Implemented but not yet run on a real model: check the getter and setter signatures (units) first |

Bounds are the operating-experience envelope; widen them only with the operator's agreement (use a note, 17.3).
Each lever is stepped both ways, then the positive moves are run **together** as one candidate, because
separators and compressors share load and the gains do not add.

### 17.3 Comments and restrictions from the engineers

Write between cycles; the next cycle acts on it. Entries are in `continuous/user_input.yaml`:

```yaml
entries:
  - id: U-001
    date: 2026-10-03
    by: NAME
    status: active            # active | resolved
    expires: 2026-11-01       # optional
    text: "Vigdis HP wells: sand limit, do not open more than 5 %."
    effects:
      - {kind: lever_bound, lever: "Vigdis HP wells", hi: 5}
      - {kind: lever_freeze, lever: "20D-VA60 3rd stage"}
      - {kind: constraint, name: rvp_spec, kpi: export_rvp_bara, op: "<=", limit: 0.70, margin: 0.05}
      - {kind: setting, key: production.rvp_bias_bara, value: 0.05}
      - {kind: gate, name: separator_T_residual, abs_max: 4.0}
```

| Effect | Does |
|--------|------|
| `lever_bound` | Replaces the plan bounds of a lever (narrows or widens). Scripts call `lever_limits(ctx, name, lo, hi)` |
| `lever_freeze` | Keeps the lever at its current value |
| `constraint` | Sets or adds a goal constraint; setting the `limit` confirms an unset one |
| `setting` | Sets a dotted plan key |
| `gate` | Changes a model-validity gate |

An entry without `effects` is free text: it is printed in the digest and the living report and goes to the agent
with the digest. A new or changed entry raises `user_input:<id>` once; an expired one raises
`user_input_expired:<id>`. Set `status: resolved` (or `neqsim task-note <task> --resolve U-001 --by NAME`) to stop
its effects. Keep resolved entries: the file is the record of who restricted what and when.

```text
neqsim task-note <task> "Sand limit" --lever "Vigdis HP wells" --hi 5 --by NAME
neqsim task-note <task> "Spec from lab" --constraint rvp_spec --kpi export_rvp_bara --limit 0.70 --margin 0.05
neqsim task-note <task> --list
```

### 17.4 Process boundary conditions

Boundary conditions that the operators do not control, such as the gas export or gas injection pressure, are read
each cycle as KPIs from `production.gas_tags` (`{kpi_name: historian tag}`, 24 h means). They appear as
`<name>_meas`; where the model has the equipment, `<name>_model` and `<name>_resid` show the difference, so a
model that has drifted from its boundary shows up. Add a `constraint` on a boundary KPI to enforce a limit.

### 17.5 What the loop does not know

State these in the task README so the reviewer sees them: well-rate moves assume the wells can deliver; equipment
limits are checked only where a constraint exists (`needs_equipment_check` otherwise); a calculated product spec
such as RVP needs the lab bias; and a slow model limits how often the loop can run.

---

## 18. Learning inside the loop: calibration and value of information

The runner stays programmed and makes no LLM calls. Where the loop has to
*learn* — how far the model has drifted from the plant, how likely a candidate
action is to pay off, whether one more simulation is worth running — it uses
Bayesian inference rather than hand-typed numbers. The pattern is the one used
for simulation-based inference in industrial agentic AI: propose alternatives,
simulate them with NeqSim, keep the parameter values that remain plausible given
the measurements, and let the remaining uncertainty decide the next evaluation.

Everything below is numpy-only, seeded, and writes its evidence into the cycle
folder. None of it changes a setpoint: proposals still pass the guard and the
reviewer.

### 18.1 The `calibrate` stage: track model parameters every cycle

Fouling factors, stage efficiencies and meter biases move slowly. Instead of
letting the model drift until a gate fails, declare them as uncertain parameters
and let each cycle update them from the measurements it already has:

```yaml
calibration:
  method: enif            # enkf | enif
  members: 40
  seed: 7
  mda_steps: 1            # >1 = ES-MDA for a nonlinear model (one extra predict per step)
  gate_sigma: 4.0         # innovation gate: reject a measurement more than 4 sigma from the prior prediction
  inflation: 1.02         # posterior anomaly inflation against ensemble collapse
  min_members: 20         # fewest members the model must solve; failed members are dropped and replenished
  reinit_after: 3         # cycles with every measurement rejected before the prior is re-drawn wider
  parameters:             # prior mean/std, bounds; walk_std = per-cycle random walk; neighbours (enif)
    fouling_20A: {initial: 0.0,  std: 0.2,  lo: 0.0, hi: 1.0, walk_std: 0.01, neighbours: [eta_A]}
    eta_A:       {initial: 0.78, std: 0.02, lo: 0.6, hi: 0.9}
  observations:           # KPI holding the measurement; depends_on = parameters it responds to
    T_20A_meas: {std: 0.5, depends_on: [fouling_20A]}
    power_A_MW: {std: auto, min_std: 0.1, depends_on: [eta_A], measured: power_A_meas_MW}
  predict: {file: continuous/stages/predict.py, function: predict}

stages: [sense, refresh, kpis, calibrate, "script:station_model", drift, goal, diff, ledger, digest, notify, agent]
```

`predict(ctx, members)` receives a list of `{parameter: value}` dictionaries and
returns one `{observation: value}` dictionary per member. Build the NeqSim model
once and loop over the members inside the function; 40 members of a single-train
model is seconds. Return `None` (or non-finite values) for a member the solver
could not converge; the stage drops it for the cycle and refills the ensemble
from the survivors, as long as `min_members` solved.

```python
def predict(ctx, members):
    model = _load_model(ctx)                 # built once, cached on the module
    out = []
    for m in members:
        model.setFouling("20A", m["fouling_20A"])
        model.setEfficiency("A", m["eta_A"])
        model.run()
        out.append({"T_20A_meas": model.outletT("20A"), "power_A_MW": model.power("A")})
    return out
```

Each cycle the stage:

- reads the measured values from `ctx.kpis` (the KPI named like the observation,
  or `measured:`), applies the parameter random walk, runs `predict`, **gates** each
  measurement on its normalised innovation (a frozen tag, a spike or a wrong unit
  must not move the parameters) and updates the ensemble with the rest;
- publishes `<parameter>_mean`, `<parameter>_std`, `<observation>_resid` (measured
  minus prior prediction), `<observation>_z`, `calibration_rank` and
  `calibration_rejected` as KPIs — put the `_resid` KPIs under `drift.signals` and
  consider a criterion trigger on `calibration_rejected`;
- exposes `ctx.calibrated` (posterior means) so the model script that follows
  runs on the calibrated parameters;
- writes `cycles/<id>/calibration.json` (method, seed, MDA steps, gate, per-observation
  innovation and whether it was used, prior/posterior moments, identifiability) and
  persists the ensemble in `continuous/calibration_state.json` (not in dry runs);
- raises `calibration:unidentifiable` **once** when the SVD rank of the
  sensitivity Jacobian drops below the number of parameters, and
  `calibration:all_rejected` when every measurement failed the gate; both leave the
  stage at `warn`.

**Keeping the filter alive.** A plain ensemble update shrinks the spread every
cycle; after a few months a 40-member ensemble has collapsed and stops
responding to new data (filter divergence). Two settings prevent that:
`walk_std` on a parameter adds a random walk before each update — the honest
statement that fouling or efficiency *moves* — and `inflation` stretches the
posterior anomalies slightly (1.01–1.05). Use `walk_std` for parameters you expect
to drift and leave it at zero for constants such as a meter bias.

**Nonlinear models.** One Gaussian update linearises the model around the
ensemble. When a NeqSim response is strongly nonlinear in a parameter (a
fouling factor acting on a log-mean temperature difference, a choke opening),
set `mda_steps: 3` or `4`: the same measurements are assimilated in several
smaller steps with inflated noise (ES-MDA), re-linearising each time. It costs
one `predict` call per step.

**Measurement noise you do not know.** `std: auto` estimates it from the KPI's
own history in `kpi_history.csv` (median absolute deviation of first
differences, which ignores trends, steps and spikes), floored by `min_std`.
Until eight values exist the observation is skipped and reported as
`std_pending`; the stage keeps running on the others. Prefer an explicit `std`
from the instrument datasheet when you have one.

**When the prior is simply wrong.** A gate protects the parameters from bad
data, but a confidently wrong prior makes *every* measurement look like bad
data. After `reinit_after` consecutive cycles with all measurements rejected,
the stage re-draws the ensemble around its current mean, widened in proportion
to the largest rejected innovation, raises `calibration:reinitialised`, and
records it in the state. The engineer sees it in the digest; nothing is hidden.

**`depends_on` does two jobs.** For `enif` it sparsifies the observation
operator; for `enkf` it localises the Kalman gain, zeroing the entries between
an observation and the parameters it is declared not to touch. Declare it
whenever you can — it is the cheapest protection a small ensemble has.

**A failed member is information.** If the solver fails for members at one end
of a parameter range, that region is infeasible: set `lo`/`hi` there rather
than relying on the drop-and-replenish, which otherwise narrows the ensemble
and makes the gate stricter than it should be.

**`enkf` or `enif`?** The ensemble Kalman filter updates every parameter through
its sample covariance with the predictions. With 30–60 members, two parameters
that happen to be correlated in the sample will update together even when only
one is observed. The information-form update (`enif`, after the Ensemble
Information Filter) estimates a *sparse precision matrix* from the structure you
declare (`neighbours`, `depends_on`) by node-wise regression, so an unobserved,
independent parameter stays where it was. Use `enif` whenever you can state which
parameters affect which measurements; use `enkf` for a handful of parameters that
are all observed.

Before trusting a calibration, read `identifiability` in `calibration.json`: a
parameter combination with a singular value at noise level is set by noise, not
data. Fix one of the parameters or add a measurement.

### 18.2 Candidates from samples, not from guesses

The stop rules rank candidate actions by `p_success · predicted_delta − cost`.
With a calibrated ensemble you can sample those numbers: run the candidate once
per member and pass the gains in.

```python
from neqsim_continuous import candidate_from_samples, value_of_information

gains = {name: [simulate(action, m) - base for m in members] for name, action in actions.items()}
candidates = [candidate_from_samples(name, g, cost=0.1) for name, g in gains.items()]
voi = value_of_information(gains, cost=0.1, simulation_cost=0.02)
```

`candidate_from_samples` sets `p_success = P(gain > cost)` and
`predicted_delta = E[gain | success]`, plus `p10`, `p90` and `delta_std` for the
digest. `value_of_information` computes the expected value of perfect information
over the candidate set (doing nothing is always allowed):

| Field | Meaning |
|-------|---------|
| `recommended_action` | Best expected net improvement, or `None` when nothing beats doing nothing |
| `p_best` | Probability each action is the best one |
| `evpi` | What knowing the true outcome before choosing would be worth |
| `evaluate_more` | `evpi > simulation_cost`: run another evaluation before proposing |

In a solve stage, return `candidates` and record `voi` as `optimizer_evidence`;
when `evaluate_more` is false, raise the proposal. Bayesian optimisation over the
levers themselves (`propose_next` in the community continuous-improvement
toolkit) is worth adding above roughly five levers or when a NeqSim run takes
minutes; below that the ±1-step sweep of the production template is enough.

### 18.3 Expensive simulators: OPM Flow, CFD and when a surrogate pays

Everything above assumes `predict` and each candidate evaluation is a NeqSim run
of seconds, so sampling the posterior directly (40 members, a few MDA steps) is
cheaper than training anything. The decision changes with the cost of one
evaluation, not with the method. Rule of thumb, with `N` the evaluations a cycle
needs (members × MDA steps + candidate runs) and `c` the wall time of one:

| Simulator | `c` | Per cycle at `N` ≈ 100–200 | Pattern |
|-----------|-----|----------------------------|---------|
| NeqSim flowsheet | 1–10 s | minutes | Direct sampling: `calibrate` + `candidate_from_samples` as written |
| OPM Flow sector / field model | 10 min – hours | days | ES-MDA with a modest ensemble on a cluster or Docker farm, cycles **weekly or on new well data**, not daily; a **surrogate** fitted on the ensemble runs answers the VOI and optimisation questions between cycles |
| CFD / FEM (OpenFOAM, FEniCSx) | hours – days | infeasible | Offline DoE over the few design/operating parameters → surrogate (GP, polynomial chaos or reduced-order model); the living task's `predict` calls the surrogate, and the full model is rerun only when a cycle leaves the DoE hull |

The runner does not care: `predict(ctx, members)` may launch OPM Flow decks
(see the `neqsim-near-well-and-injectivity` skill for deck generation and the
Docker/WSL2 requirement), read back the summary vectors, and cache every run
under `continuous/data/simulations/` keyed by the parameter hash so a resumed or
repeated cycle never pays twice. Set `members` and `mda_steps` to what the
budget allows, prefer `enif` with a declared graph when members are few, and
let `value_of_information(..., simulation_cost=c)` decide whether another full
run is worth it — with `c` in the same unit as the gain.

A **surrogate** is justified when the DoE needed to fit it (typically 30–100
runs for ≤ 6 parameters with a Gaussian process) is smaller than the
evaluations it will replace over the life of the task, and its validation error
on held-out runs is below the measurement noise it is asked to explain. Keep
the surrogate's training set, validation error and domain hull in the cycle
folder; a prediction outside the hull is an `extrapolation` finding, not a result.
The community toolkit's `propose_next` already carries a GP on the unit cube
that can serve as a first surrogate.

**Amortised inference** (training a network once to map observations to a
posterior, as in simulation-based inference or the Ensemble Information
Filter's learned sparse maps) pays when the *same* inference is repeated many
times with the same structure — every cycle of a field model, or the same
well-test interpretation across dozens of wells. It is the right tool for an
FMU-scale reservoir workflow where every history-match update is a cluster
job, which is exactly the subsurface case where a 50 % compute reduction has
been projected; for a single NeqSim living task it is not. The packages
(`graphite-maps`, `sbi`, `torch`) are not in the shared environment today;
report that as a blocker when a task needs them rather than approximating.

### 18.4 What is deliberately not here

- **Reinforcement learning on the live plant.** The loop is advisory and the
  guard fails closed; a learned policy that explores on a running facility
  contradicts both. Sequential decisions under uncertainty (when to wash a
  compressor, when to schedule maintenance) belong in an offline study with
  NeqSim as the environment, whose output becomes ledger proposals.
- **Learned models without labels.** `--tune` needs labelled events, the
  `outcome` stage needs ledger items driven to `implemented`, and a surrogate
  needs a candidate history. Collect those first; the helpers above then have
  something to learn from.

## Related documentation

- [Task Solving Guide](TASK_SOLVING_GUIDE.md) — the one-off task workflow this builds on
- Skill: `.github/skills/neqsim-continuous-task-improvement/SKILL.md`
- Agent: `.github/agents/continuous-improvement.agent.md`
- Runner source: `devtools/neqsim_continuous/`
