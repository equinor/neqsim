---
title: "Continuous Task Solving (Living Tasks)"
description: "How to keep a solved NeqSim engineering task improving over time: make a task living, set a confirmed goal, write a cycle plan and stage scripts, backtest drift detection, run monitor and solve cycles, schedule them on a laptop or server, launch headless agents on triggers, and review the living report, improvement ledger and baseline promotion."
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
9. [Run cycles by hand](#9-run-cycles-by-hand)
10. [Make it run by itself](#10-make-it-run-by-itself)
11. [Let an agent handle the triggers](#11-let-an-agent-handle-the-triggers)
12. [Day-to-day work with a living task](#12-day-to-day-work-with-a-living-task)
13. [Company data sources](#13-company-data-sources)
14. [Troubleshooting](#14-troubleshooting)
15. [Command reference](#15-command-reference)

---

## 1. How it works

The principle is **program the loop, prompt the exceptions**. The runner is
plain Python and makes no LLM calls. An agent runs only when a trigger fires.
People make every decision that changes the baseline.

```
 one cycle (daily, on new data, or on demand)

   sense -> refresh -> script:<model> -> kpis -> drift -> goal -> diff -> ledger
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
| Java (only for stages that call NeqSim) | NeqSim thermodynamics | `python devtools/java_locator.py` |
| GitHub Copilot CLI, signed in (only for automatic agent triage) | Headless agent sessions | `copilot --version` |
| A community or enterprise adapter (only for live historian data) | Pulls data from PI/IP.21, OTS, PDM | see [section 13](#13-company-data-sources) |

If `neqsim` is not on `PATH` in your shell, use the identical command through
the interpreter:

```powershell
C:\appl\neqsim-venv\Scripts\python.exe devtools\neqsim_cli.py task-status <task>
```

Nothing else is needed for the reference case: it uses a CSV file drop and the
Python standard library only.

---

## 3. Try it in five minutes

The public reference case is a synthetic compressor station with three faults
injected at known dates (efficiency decline, flow-meter bias, suction-pressure
step). Use it to learn the loop before you touch a real task.

```powershell
neqsim task-reference-case C:\tmp\living
$task = "C:\tmp\living\reference_compressor_station"

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
stages: [sense, refresh, "script:station_model", kpis, drift, goal, diff, ledger, digest, notify, agent]
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
  stages: [sense, "script:solver", goal, diff, ledger, digest]
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
stages: [sense, refresh, "script:gas_density", kpis, drift, diff, ledger, digest, notify]
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
`max_delay_days` and false alarms are close to zero. Record the result in
`results.json` `validation`. **Schedule only after a clean backtest.**

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
cycle is resumed by running it again: completed stages are skipped.

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
`python devtools\neqsim_cli.py task-cycle <task> --mode monitor` with the shared
interpreter. By default it runs only while you are signed in. To run it while
signed out, open Task Scheduler, select the task and choose
*Run whether user is logged on or not* (Windows asks for your password there;
the runner never sees it).

### 10.2 On a Linux server

`task-schedule` prints the cron line instead of installing it:

```bash
neqsim task-schedule /data/tasks/<task> --daily 05:00
crontab -e      # paste the "cron" line from the output
```

Server checklist:

1. Clone NeqSim, build once (`./mvnw compile`) and set `NEQSIM_PROJECT_ROOT`.
2. Install the Python dependencies and any adapter packages (section 13).
3. Set `NEQSIM_CONTINUOUS_HOST` so cycle ids show which machine ran them.
4. Set `data_root` in the plan to local disk if the task folder is on a share.
5. Put notification secrets in the service environment, not in the plan.

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

## 11. Let an agent handle the triggers

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
**continuous improvement of living tasks** in the agent picker and ask, for example:

- "Make `<task>` living, compile the goal from its brief and backtest it."
- "Triage the last cycle of `<task>`."
- "Solve `<task>` until converged and tell me which cycle to promote."
- "Create the reference case in `C:\tmp\living`."

The agent follows the same rules: it prepares, you decide.

---

## 12. Day-to-day work with a living task

### 12.1 Read the living report

`continuous/LIVING_REPORT.md` is the one page to open. It is rebuilt after every
cycle, solve round, backtest, promotion, reopen and ledger decision, and shows
the state and stop reason, the goal and baseline, the latest KPIs against the
baseline, solve rounds, KPI trends (`report/kpi_trends.png`), trigger events,
the ledger with pending decisions, the baseline history, backtests and the
**next actions**. Never edit it; rebuild it with `neqsim task-report <task>`.

For all living tasks in a folder at once:

```powershell
neqsim task-status C:\path\to\task-root
```

### 12.2 Triage a triggered cycle

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

### 12.3 Decide ledger items

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

### 12.4 Promote a cycle to the baseline

```powershell
neqsim task-promote <task> 2026-09-30T0500Z@myhost --reviewer "A. Engineer" --note "after wash"
```

The previous baseline is archived under `baseline/history/`. The cycle's KPIs
are merged over the old baseline, so promoting a solve round (which reports only
the objective) keeps the baseline of the monitored KPIs. With
`report.formal: on_promote` the Word/HTML report is regenerated; otherwise run
`neqsim report <task>`.

### 12.5 When the task reopens

A monitoring task reopens when the objective drops more than `regress_margin`
below the baseline, or on `new_evidence` (the references manifest changed),
`brief_changed`, `neqsim_capability` (a new NeqSim commit) or a reviewer request.
The living report shows the reason; run `neqsim task-solve <task>` to continue.

---

## 13. Company data sources

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

## 14. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Task is locked by ... until ...` | Another cycle is running, or one crashed | Wait, or delete `continuous/LOCK` if no cycle is running |
| `goal.yaml is not confirmed` | `confirmed_by` is empty | Confirm the goal (section 5) |
| `goal.yaml has no objective.metric` | The goal was not filled in | Set `objective.metric` to a KPI a stage produces |
| Source `stale` | No new rows for `stale_after_hours` | Check that the export or historian is still delivering |
| Source `not_installed` | Adapter package missing | Install it into the shared environment (section 13) |
| Cycle `degraded` | A stage failed or was skipped, or a source was not `ok` | Read `cycle.json` stage messages; rerun the cycle to resume |
| `kpis` stage `warn: no data for ...` | The source returned no rows for that column | Check `column` and `source` names in the plan |
| Too many drift alarms | Engineering floor missing or too small | Set or raise `min_sigma`; raise `confirm`; backtest again |
| Agent `not_installed` | `copilot` is not on `PATH` of the scheduled task | Install and sign in to GitHub Copilot CLI, or set `executable` to its full path |
| `neqsim` not recognized | CLI not on `PATH` | Use `python devtools\neqsim_cli.py task-...` with the shared interpreter |
| `PermissionError` on OneDrive | Sync client holds the file | The runner retries; move large data with `data_root` |

---

## 15. Command reference

| Command | Purpose |
|---------|---------|
| `neqsim task-living <task> [--brief FILE]` | Make a task living (never overwrites) |
| `neqsim task-cycle <task> [--mode monitor\|solve] [--stages a,b] [--dry-run] [--no-agent] [--now ISO]` | Run one cycle |
| `neqsim task-solve <task> [--until goal\|converged] [--max-rounds N] [--no-agent] [--allow-unconfirmed] [--reset]` | Solve loop |
| `neqsim task-backtest <task> --start ISO --end ISO [--step-hours 24] [--name N] [--repeat]` | Replay archived data |
| `neqsim task-schedule <task> [--daily HH:MM] [--install\|--remove\|--show]` | Schedule monitor cycles |
| `neqsim task-promote <task> <cycle-id> --reviewer NAME [--note TEXT]` | Promote a cycle to the baseline |
| `neqsim task-ledger <task> [list\|show ID\|set ID STATUS --by NAME [--note TEXT]\|merge FILE]` | Improvement ledger |
| `neqsim task-status <task-or-task-root>` | Status of one task or all living tasks in a folder |
| `neqsim task-report <task> [--formal]` | Rebuild the living report (and the Word/HTML report) |
| `neqsim task-reference-case <parent-folder>` | Create the public reference task |

## Related documentation

- [Task Solving Guide](TASK_SOLVING_GUIDE.md) — the one-off task workflow this builds on
- Skill: `.github/skills/neqsim-continuous-task-improvement/SKILL.md`
- Agent: `.github/agents/continuous-improvement.agent.md`
- Runner source: `devtools/neqsim_continuous/`
