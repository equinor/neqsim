---
name: neqsim-continuous-task-improvement
description: "Living tasks and continuous task solving with NeqSim (neqsim task-living/cycle/solve/resume/update/backtest/schedule/loop/promote/report/ledger). USE WHEN: a solved task must keep improving daily or on events, resume from persisted state, rerun only affected calculations on new evidence, backtest and tune drift settings, track model parameters with an EnKF/EnIF calibrate stage, rank actions by value of information, or issue a reviewed Final Report."
last_verified: "2026-10-18"
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
| Run a live loop at sub-hour cadence with a warm model (10-minute advice, emulator tests) | `task-loop <task> --every-minutes 10 --for-hours 3 [--hook file.py:func]` |
| Solve until the goal is met, or until improvement is marginal | `task-solve --until goal` / `--until converged` |
| Resume after closing VS Code, rebooting or changing machine | `task-status <task>` then `task-resume <task>` |
| Reassess after a datasheet, drawing, standard, measurement or model changes | `task-status <task>` then `task-update <task>` |
| Issue a reviewed standalone Word/HTML delivery | Promote the validated cycle, then `task-report <task> --final --reviewer NAME` |
| Start from a Word/Markdown brief | `neqsim new-task "title" --prompt-file brief.docx` then `task-living` |
| Prove a monitor finds the faults it should (and no others) | `task-backtest --start ... --end ...` |
| Choose drift settings from that backtest without rerunning NeqSim | `task-backtest --tune --name RUN` |
| Track fouling, efficiency or meter bias every cycle | `calibrate` stage (`calibration:` plan block) |
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
5. **Resume before starting fresh work** — `neqsim task-status <task>` is the
   five-second persisted view. If it reports resumable work, run
   `neqsim task-resume <task>`. The task folder, not prior chat, is authoritative.
6. **Process evidence changes** — map evidence globs to affected stages and conclusions under
   `evidence.rules` (`match`, `stages`, affected `kpis`, and `conclusions`); use
   `neqsim task-update <task>`. Mapped changes rerun only the affected stages and declared
   dependencies while invalidating affected KPIs and retaining unaffected ones. An unmapped path fails
   closed to the full plan.
7. **Run cycles** — `neqsim task-cycle <task>` (monitor) or schedule it:
   `neqsim task-schedule <task> --daily 05:00 --install` (Windows Task Scheduler;
   the `cron` line is printed for Linux servers).
8. **Review and promote** — read `continuous/LIVING_REPORT.md` (the always-current
   view) and `cycles/<id>/digest.md`, decide ledger items
   (`task-ledger <task> set OPP-0002 accepted --by NAME`), then
   `neqsim task-promote <task> <cycle-id> --reviewer NAME`.
9. **Finalize explicitly** — only after the accepted result is promoted and evidence is current,
   run `neqsim task-report <task> --final --reviewer NAME`. This uses the canonical Task Solver
   generator and writes an immutable `FR-###` Word/HTML delivery plus hashes and audit references.

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
Declare `report.prepare: step2_analysis/40_write_results.py` (optionally `:function`) and the
runner runs that script first, so the task-local writer turns the latest complete cycle into
`results.json` + figures and `neqsim task-report <task> --formal` is the one-command,
on-request report of the current state (also used by `on_promote` / `every_cycle`).

Do not confuse the four views: `task-status` is the five-second operational summary;
`continuous/LIVING_REPORT.md` is regenerated history/change detail; `neqsim report` is the
ordinary current-best engineering report; and `task-report --final --reviewer NAME` creates an
immutable standalone delivery. A Final Report contains no transient cycle narrative, only a
short record pointing to the Living Report, ledger, promoted cycle and `WORK_RECORD.md`.

Promotion merges the cycle's KPIs over the previous baseline, so promoting a solve
round (which reports only the objective) keeps the baseline of the monitored KPIs.

## Folder layout

```
continuous/
  cycle_plan.yaml   goal.yaml   state.json   watermarks.json   drift_state.json
  kpi_history.csv   LOCK (while a cycle runs)
  LIVING_REPORT.md  report/kpi_trends.png   always-current view (rebuilt, never edited)
  final_report.json                         current immutable Final Report pointer
  final_reports/FR-###.json                 retained finalization revisions
  baseline/         baseline.json, kpis.json, results_snapshot.json, history/<id>/
  evidence/         inventory.json: accepted task-relative SHA-256 provenance
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

### Tune the drift settings from a backtest

Drift alarms are a pure function of the recorded KPI series, so once a backtest has run
and `backtest.expected` lists the events it should have found (maintenance, washes, trips,
known step changes), the settings can be chosen without rerunning NeqSim:

```bash
neqsim task-backtest <task> --tune --name pilot-365d --holdout-from 2026-06-01 [--false-alarm-weight 0.5] [--delay-weight 0.25]
```

It replays every `(lambda, L, h, confirm, min_sigma_scale)` combination of a grid over
`cycles/*/kpis.json`, ranks on the events before `--holdout-from`, scores every candidate on
the events after it, and writes `continuous/backtests/<run>/drift_tuning.json` with the
ranking, `validated` (best misses nothing on the holdout and adds no false alarms there) and
a `recommended_drift_settings` block. Copy that block into `drift.settings` yourself; the plan
is never edited. Without `--holdout-from` the result is in-sample and says so; on a quiet
period the tuner favours the most aggressive settings, which is exactly what the holdout
catches. Without `drift:*` expected events it refuses — a backtest with only unscored
triggers (the Troll A pilot) has nothing to learn from, so label the events first.

The monitor itself recovers: after `recover` in-control cycles (default `confirm`) an alarmed
signal resets and can alarm again, while a persistent shift stays one alarm. The CUSUM is
bounded at `2h` so a long excursion cannot silence a signal for months.

## Learning inside the loop (Bayesian helpers)

The runner stays programmed; the statistical parts are helpers in
`neqsim_continuous.inference` and the `calibrate` stage, all numpy-only and seeded. The
pattern is the one proposed for simulation-based inference in industrial agentic AI: an
agent proposes alternatives, NeqSim evaluates them, Bayesian inference keeps what remains
plausible, and value of information decides whether to evaluate more or to propose.

- **`calibrate` stage** — ensemble parameter tracking every cycle (fouling, efficiency,
  meter bias) with `method: enkf` or `method: enif` (information form with a sparse
  precision matrix: declare `neighbours` per parameter and `depends_on` per observation so
  a 30–60 member ensemble cannot update a parameter through a spurious sample correlation).
  Production settings: `walk_std` per moving parameter and `inflation: 1.02` against
  ensemble collapse, `gate_sigma: 4` innovation gating against frozen tags and wrong units,
  `mda_steps: 3-4` (ES-MDA) for strongly nonlinear responses, `std: auto` + `min_std` to
  estimate measurement noise from `kpi_history.csv`, `min_members` so a `None`/NaN member
  from a failed solve is dropped and replenished instead of failing the cycle, and
  `reinit_after` so a confidently wrong prior widens itself (trigger
  `calibration:reinitialised`) instead of rejecting data forever. `depends_on` localises the
  EnKF gain as well as sparsifying EnIF. Plan block,
  `predict(ctx, members)` contract, published KPIs (`<p>_mean`, `<p>_std`, `<obs>_resid`,
  `<obs>_z`, `calibration_rank`, `calibration_rejected`, `calibration_failed_members`),
  `ctx.calibrated` for later scripts and the triggers `calibration:unidentifiable` (once),
  `calibration:all_rejected` and `calibration:reinitialised` are documented in
  `devtools/neqsim_continuous/calibrate.py`.
  Place it after `kpis` and before the model script; monitor the `_resid` KPIs with `drift`
  rather than raw measurements so load changes do not alarm. Never raise `gate_sigma` to make
  a measurement pass — a rejected observation is a tag or model finding. A member that fails
  at one end of a parameter range is information: set `lo`/`hi` there instead of relying on
  drop-and-replenish, which narrows the ensemble.
- **`identifiability(J, names, parameter_scale)`** — SVD rank of the sensitivity Jacobian;
  fix a parameter or add a measurement before calibrating when `identifiable` is false.
- **`candidate_from_samples(action, delta_samples, cost)`** — turns posterior predictive
  samples of a candidate's gain (one NeqSim run per ensemble member, or a surrogate) into the
  `p_success` / `predicted_delta` that `stop_rules.evaluate` ranks, instead of hand-typed
  numbers. `p_success = P(gain > cost)`, `predicted_delta = E[gain | success]`.
- **`value_of_information(samples_by_action, cost, simulation_cost)`** — expected value of
  perfect information over the candidate set (doing nothing allowed). `evaluate_more` is
  true when another evaluation is worth more than it costs; otherwise propose
  `recommended_action`. Use it in a solve stage to decide between "run more simulations"
  and "raise a proposal", and record the dict as `optimizer_evidence`.
- Bayesian optimisation over levers (`propose_next` in the community
  `neqsim-continuous-improvement-toolkit`) pays off above ~5 levers or when each NeqSim run
  is slow; below that the ±1-step sweep of the production template is adequate.
- **Expensive simulators change the answer.** The direct-sampling pattern assumes an
  evaluation of seconds. With OPM Flow (`neqsim-near-well-and-injectivity`) run ES-MDA with a
  modest ensemble on weekly/event cycles, cache every run by parameter hash under
  `continuous/data/simulations/`, and fit a surrogate on the ensemble runs for VOI and
  optimisation between cycles; with CFD/FEM (`neqsim-cfd-coupling`, `neqsim-fem-coupling`)
  build the surrogate from an offline DoE and let `predict` call it, rerunning the full model
  only outside the DoE hull. Amortised inference (SBI, learned EnIF maps) is for the same
  inference repeated many times (field model every cycle, one interpretation across many
  wells); `graphite-maps`/`sbi`/`torch` are not in the shared environment — report the
  blocker, do not approximate. Decision rule and table: user guide §18.3.

What is deliberately **not** here: reinforcement learning on the live plant (the loop is
advisory and the guard fails closed), and any learned model without outcome labels — drive
ledger items to `implemented` and fill `backtest.expected` first, then the helpers above have
data to learn from.

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
- **Proposals** must carry finite numeric `setpoints`, `expected_gain`, `predicted` (a value for every
  hard-constrained KPI), and `optimizer_evidence`. Evidence must name the current `ctx.cycle_id` and set
  `simulation_converged`, `candidate_feasible`, `candidate_finite`, `constraint_evidence_complete`, `state_restore_complete`,
  `accepted_point_replayed`, and `actions_complete` to exactly `true`. The `guard` fails closed on missing,
  stale, non-finite, unconverged, partially restored/replayed, incomplete-constraint, or partially applied
  candidates. It keeps only proposals that also satisfy every hard constraint with margin; every rejection is
  listed in `guard.json`. Also record a positive integer `evaluation_count` and finite non-negative
  `runtime_seconds`; the ledger retains the evidence block for persistent incumbent and performance provenance.
  Field studies can set `production.require_envelope_evidence: true` and attach the Java
  `getAcceptedPointEnvelopeEvidence(...)` map as `accepted_point_envelope_evidence`. The guard requires exact
  cycle/optimizer identity, action-id/setpoint binding, complete uniquely addressed slices, safe recovery of every
  sampled point, and reconciled point/evaluation/runtime totals before the existing ledger persists it. A supplied
  block is always validated even when the setting is false; never copy envelope evidence into a later cycle.
  Map production/injection route line-ups, controlled reservoir-lifecycle boundaries, mapped rotating-equipment
  power, common-shaft speed/power, driver limits, shared host-power limits, and other operating-limit files to
  `guard` with dependencies on the model update, optimizer, gates, and constraints. Keep route selectors as process
  actions, reservoir pressure as a model input owned by `WellFlow.ReservoirLayer`, and mapped equipment power as
  solved evidence read from the canonical `ProcessModel.getPower(...)` path. On a detected relevant revision
  the append-only ledger marks old validation evidence non-current
  and supersedes unfinished advice during the evidence stage, before model/optimizer/guard work can fail. A failed
  guard still blocks publication of new advice, but cannot leave the old item marked current. Retry and resume
  preserve idempotent invalidation and the current cycle's already-persisted replacement; dry runs do not mutate the
  ledger. Custom production plans must keep `evidence` before model update, optimization, and `guard` to retain this
  failure-path guarantee. Only a newly guarded point becomes a separate
  `proposed` item with `revalidation_of`; if no safe replacement exists, expose none and never retain stale advice.
  When a later revision is safe, resume lineage from the most recent superseded item rather than silently starting
  a disconnected incumbent.
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

- `task-schedule` starts a fresh interpreter per cycle (hourly at the fastest). For a live loop use
  `task-loop`: one process, cycles every N minutes, `continuous/STOP` to end early, `loop.log` /
  `loop_state.json` for progress. Stage scripts are re-executed every cycle, so cache a simulator on a
  module imported from `step2_analysis` (e.g. `FieldModel.shared()`), not on the stage module's globals,
  or every cycle rebuilds the JVM model. The `--hook` module (plant emulator, test harness) is called
  before each cycle and re-loaded each cycle too.
- The `file` adapter pulls rows with `since <= t < until` where `until` is the cycle clock: a row stamped
  exactly at the cycle time is missed. Stamp live rows at the end of their averaging window (e.g. now − 1 min).
- The ledger de-duplicates proposals by exact title: keep titles stable and put the changing numbers
  (gain, P10-P90) in a `summary` field, or every cycle creates a new item. Ledger categories are fixed
  (`operational, maintenance, modification, model, data, tooling`) — an appraisal/VOI proposal is `data`.
- Measured actuator positions (choke multipliers, valve openings) must be model inputs, or the calibration
  absorbs an operator move into a parameter (a PI jump) and the outcome stage never sees the realised gain.
- The utilisation register's surge rows are turndown limits without anti-surge recycle: report them, do not
  make them hard constraints; the Vigdis GT-train speed row is a constant in the ProcessPilot model.
- A ProcessPilot run with a foreign tie-in stream mixed in takes 30-250 s (vs 6-10 s for a host-only
  rescale): one spot check per 30-minute cycle, surrogate for the rest (user guide §18.3).
- Cycle ids are UTC; a rerun in the same minute gets `-r2`. Use
  `task-resume` for crashed/interrupted work: it keeps the original cycle id across
  later days/hosts, skips completed stages, restores completed side-effect metadata,
  and resumes solve checkpoints. A stale `LOCK` expires after 6 h.
- `continuous/state.json` is schema-versioned. Legacy 1.0 state migrates to 1.1;
  newer incompatible state fails closed instead of being silently rewritten.
- `task-update` compares task-relative content hashes, not timestamps. A successful mapped
  update advances the accepted evidence inventory; a degraded update leaves it pending. Every
  changed path must match an impact rule or the runner conservatively executes the full plan.
- Finalization requires a named reviewer, a complete non-degraded promoted cycle, current evidence
  and a `results.json` matching the promoted snapshot. Report-build environment stamps do not make
  a final revision stale; engineering-result, study, evidence, figure or report-content changes do.
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
