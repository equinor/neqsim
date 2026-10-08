"""Make a task living, promote a cycle to the baseline, and read the loop state."""

import json
import os
import shutil
from datetime import datetime, timezone

from .ledger import CATEGORIES, Ledger
from .plan import (GOAL_FILE, PLAN_FILE, continuous_dir, file_sha256, load_baseline, read_json,
                   write_json)
from .state import read_state, write_state
from .stages import neqsim_commit

PLAN_TEMPLATE = """# Living-task cycle plan (neqsim_continuous schema 1.0). Every section is optional.
schema_version: "1.0"
# data_root: "C:/neqsim-data/<task>"   # keep large partitions off synced folders
sources: {}
#  historian:
#    adapter: file                      # file (built in), tagreader (community), ots (enterprise), ...
#    options: {folder: data_drop, time_column: timestamp, pattern: "*.csv"}
#    initial_lookback_days: 1
#    stale_after_hours: 36
kpis: {}
#  suction_pressure_bara: {source: historian, column: suction_pressure_bara, agg: mean}
#  headroom_MSm3d: {from: results, key: headroom_to_98pct_power_MSm3d}
scripts: {}
#  model: {file: continuous/stages/model.py, function: run}   # use as stage "script:model"
drift:
  signals: []
  settings: {lambda: 0.2, L: 3.5, k: 0.5, h: 6.0, warmup: 30}
triggers:
  kpi_step: {}
  criteria: {}
stages: [sense, refresh, kpis, drift, goal, diff, ledger, digest, notify, agent]
notify:
  channels: [file]
#   - {type: email, host: smtp.example.com, to: [you@example.com], user: you, password_env: NEQSIM_SMTP_PASSWORD, when: [needs_decision]}
#   - {type: teams, url_env: NEQSIM_TEAMS_WEBHOOK, when: [needs_decision, stop_state]}
agent: {enabled: false, agent: continuous-improvement, executable: copilot}
solve: {}
#  stages: [sense, refresh, "script:solver", kpis, goal, diff, ledger, digest]
#  max_rounds: 12
#  max_branches: 3
#  critic: {enabled: false}
backtest: {}
#  expected: [{trigger: "drift:polytropic_efficiency", onset: "2026-02-01", max_delay_days: 30}]
# continuous/LIVING_REPORT.md is always rewritten; formal also regenerates the Word/HTML report
report: {formal: never}                 # never | on_promote | every_cycle
"""

PRODUCTION_PLAN_TEMPLATE = """# Production-optimisation cycle plan (neqsim_continuous schema 1.0).
# Advisory loop: the cycle updates the model from live data, checks that it matches the plant,
# checks the constraints, lets the optimiser propose setpoints, and withholds every proposal that
# is not safe to show. An engineer approves; nothing is written to the control system.
schema_version: "1.0"
sources: {}
#  historian:
#    adapter: tagreader                 # community; ots / pdm adapters are enterprise
#    options: {site: SNA, tags: {...}, time_column: timestamp}
#    initial_lookback_days: 1
#    stale_after_hours: 6
kpis: {}
#  export_oil_Sm3d: {source: model, column: export_oil_Sm3d}      # normally set by script:model_update
scripts:
  model_update: {file: continuous/stages/model_update.py, function: run}   # live data -> model state, run, residual KPIs
  optimize: {file: continuous/stages/optimize.py, function: run}           # bounded search -> proposals
gates: []
#  - {name: sep_T_residual, kpi: max_sep_T_residual_K, abs_max: 3.0}      # no advice if the model is off
#  - {name: mass_balance, kpi: mass_balance_pct, abs_max: 0.05}
production: {min_gain: 0.0, outcome_tolerance: 0.5}
drift:
  signals: []
  settings: {lambda: 0.2, L: 3.5, k: 0.5, h: 6.0, warmup: 30}
triggers:
  kpi_step: {}
  criteria: {}
stages: [sense, inputs, refresh, "script:model_update", "script:optimize", kpis, gates, constraints, guard,
         drift, goal, diff, outcome, ledger, digest, notify, agent]
notify:
  channels: [file]
agent: {enabled: false, agent: continuous-improvement, executable: copilot}
solve: {}
backtest: {}
report: {formal: never}
"""

# Constraints are checked by the 'constraints' stage; a hard constraint without a limit withholds advice.
PRODUCTION_CONSTRAINTS = [
    {"name": "product_spec", "kpi": None, "op": "<=", "limit": None, "margin": 0.0, "warn": 0.0, "hard": True,
     "source": "spec", "confirmed_by": None, "note": "set kpi, limit and margin (bias scatter) for the certified spec"},
    {"name": "equipment_limit", "kpi": None, "op": "<=", "limit": None, "margin": 0.0, "hard": True,
     "source": "demonstrated", "confirmed_by": None,
     "note": "larger of design and demonstrated limit (neqsim_continuous.production.demonstrated_limit)"},
]

CATEGORY_HINTS = (
    ("model", ("re-fit", "refit", "calibrat", "model")),
    ("maintenance", ("wash", "monitor", "inspect", "maintenance", "clean")),
    ("modification", ("uprat", "modif", "retrofit", "screening", "vendor", "install")),
    ("data", ("data", "measure", "sample")),
)


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _guess_category(text):
    lower = text.lower()
    for category, words in CATEGORY_HINTS:
        if any(w in lower for w in words):
            return category
    return "operational"


def _brief_sections(text):
    return [line.lstrip("#").strip() for line in text.splitlines() if line.startswith("#")]


def _find_brief(task_dir, brief):
    if brief:
        return brief
    config = os.path.join(task_dir, "study_config.yaml")
    if os.path.exists(config):
        import yaml
        with open(config, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
        relative = (data.get("inputs") or {}).get("prompt_file")
        if relative:
            return os.path.join(task_dir, relative)
    return None


def make_living(task_dir, brief=None, template=None):
    """Scaffold ``continuous/`` for an existing task. Never overwrites existing files.

    ``template='production'`` writes the production-optimisation plan and goal constraints.
    """
    if template not in (None, "production"):
        raise ValueError("Unknown template '{}'. Valid: production".format(template))
    task_dir = os.path.abspath(str(task_dir))
    cont = continuous_dir(task_dir)
    report = {"created": [], "kept": []}
    for sub in ("baseline", "cycles", "ledger", "stages"):
        os.makedirs(os.path.join(cont, sub), exist_ok=True)

    def _write(path, writer):
        if os.path.exists(path):
            report["kept"].append(os.path.relpath(path, task_dir))
        else:
            writer(path)
            report["created"].append(os.path.relpath(path, task_dir))

    def _plan(path):
        with open(path, "w", encoding="utf-8") as f:
            f.write(PRODUCTION_PLAN_TEMPLATE if template == "production" else PLAN_TEMPLATE)

    _write(os.path.join(cont, PLAN_FILE), _plan)

    brief_path = _find_brief(task_dir, brief)
    if brief_path and not os.path.isabs(brief_path):
        brief_path = os.path.abspath(brief_path)
    if brief_path and os.path.isfile(brief_path):
        manual = os.path.join(task_dir, "step1_scope_and_research", "references", "manual")
        if os.path.dirname(os.path.abspath(brief_path)) != os.path.abspath(manual):
            os.makedirs(manual, exist_ok=True)
            target = os.path.join(manual, os.path.basename(brief_path))
            if not os.path.exists(target):
                shutil.copy2(brief_path, target)
            brief_path = target

    def _goal(path):
        import yaml
        try:
            from new_task import read_prompt_file
        except ImportError:  # used outside devtools: plain text briefs only
            def read_prompt_file(path):
                with open(path, "r", encoding="utf-8-sig") as f:
                    return f.read()
        sections, relative, digest = [], None, None
        if brief_path and os.path.isfile(brief_path):
            sections = _brief_sections(read_prompt_file(brief_path))
            relative = os.path.relpath(brief_path, task_dir).replace("\\", "/")
            digest = file_sha256(brief_path)
        goal = {"schema_version": "1.0", "brief": relative, "brief_sha256": digest,
                "brief_sections": sections, "confirmed_by": None,
                "objective": {"metric": None, "direction": "maximize", "target": None,
                              "confidence_required": "medium", "source_section": None},
                "secondary_metrics": [], "constraints": [dict(c) for c in PRODUCTION_CONSTRAINTS] if template == "production" else [],
                "stop": {"goal_met": {"validated": True},
                         "converged": {"window": 3, "relative": 0.02, "absolute": 0.0},
                         "budget": {"iterations": 12, "agent_sessions": 6},
                         "expected_improvement_cost": 0.0},
                "reopen": {"regress_margin": None,
                           "on": ["new_evidence", "brief_changed", "neqsim_capability",
                                  "reviewer_request"]}}
        with open(path, "w", encoding="utf-8") as f:
            f.write("# Compiled from the task brief. The agent fills objective/constraints; the\n"
                    "# engineer sets confirmed_by before a solve loop may use it.\n")
            yaml.safe_dump(goal, f, sort_keys=False)

    _write(os.path.join(cont, GOAL_FILE), _goal)

    def _user_input(path):
        from .user_input import HEADER
        with open(path, "w", encoding="utf-8") as f:
            f.write(HEADER + "entries: []\n")

    if template == "production":
        _write(os.path.join(cont, "user_input.yaml"), _user_input)

    results = read_json(os.path.join(task_dir, "results.json"), {}) or {}
    base = os.path.join(cont, "baseline")

    def _baseline(path):
        kpis = {k: float(v) for k, v in (results.get("key_results") or {}).items()
                if isinstance(v, (int, float)) and not isinstance(v, bool)}
        write_json(os.path.join(base, "kpis.json"), kpis)
        write_json(os.path.join(base, "results_snapshot.json"), results)
        write_json(path, {"schema_version": "1.0", "id": "B-{}".format(_now()[:10]),
                          "promoted_at": _now(), "promoted_by": "task-living (initial baseline)",
                          "source": "results.json", "versions": {"neqsim_commit": neqsim_commit()},
                          "references_sha256": file_sha256(os.path.join(
                              task_dir, "step1_scope_and_research", "references",
                              "collection_manifest.json"))})

    _write(os.path.join(base, "baseline.json"), _baseline)

    ledger_path = os.path.join(cont, "ledger", "events.jsonl")

    def _ledger(path):
        book = Ledger(path)
        for text in results.get("recommendations") or []:
            if isinstance(text, str) and text.strip():
                book.create(text.strip()[:200], _guess_category(text), description=text.strip(),
                            evidence=["results.json#recommendations"])
        if not os.path.exists(path):
            open(path, "w").close()

    _write(ledger_path, _ledger)
    _write(os.path.join(cont, "state.json"),
           lambda p: write_state(task_dir, {"state": "draft", "phase": "draft"}))

    config = os.path.join(task_dir, "study_config.yaml")
    if os.path.exists(config):
        with open(config, "r", encoding="utf-8") as f:
            text = f.read()
        if "\ncontinuous:" not in text and not text.startswith("continuous:"):
            with open(config, "a", encoding="utf-8") as f:
                f.write("\ncontinuous:\n  enabled: true\n  plan: continuous/cycle_plan.yaml\n")
            report["created"].append("study_config.yaml: continuous block")
    from .living_report import update
    if update(task_dir, event="living"):
        report["living_report"] = "continuous/LIVING_REPORT.md"
    return report


def promote(task_dir, cycle_id, reviewer, note=""):
    """Promote a complete cycle to the baseline; the previous baseline is archived."""
    task_dir = os.path.abspath(str(task_dir))
    if not reviewer:
        raise ValueError("A named reviewer is required to promote a baseline")
    cont = continuous_dir(task_dir)
    cycle_dir = os.path.join(cont, "cycles", cycle_id)
    manifest = read_json(os.path.join(cycle_dir, "cycle.json"))
    if not manifest or manifest.get("status") != "complete":
        raise ValueError("Cycle {} is not complete".format(cycle_id))
    base = os.path.join(cont, "baseline")
    old = load_baseline(task_dir)["meta"]
    old_kpis = load_baseline(task_dir)["kpis"] or {}
    if old.get("id"):
        archive = os.path.join(base, "history", old["id"])
        os.makedirs(archive, exist_ok=True)
        for name in os.listdir(base):
            if os.path.isfile(os.path.join(base, name)):
                shutil.copy2(os.path.join(base, name), os.path.join(archive, name))
    # A solve round reports only the objective; keep the baseline of every KPI it did not report.
    write_json(os.path.join(base, "kpis.json"),
               dict(old_kpis, **(read_json(os.path.join(cycle_dir, "kpis.json"), {}) or {})))
    candidate = os.path.join(cycle_dir, "results.json")
    if os.path.exists(candidate):
        shutil.copy2(candidate, os.path.join(task_dir, "results.json"))
        shutil.copy2(candidate, os.path.join(base, "results_snapshot.json"))
    sense = read_json(os.path.join(cycle_dir, "sense.json"), {}) or {}
    meta = {"schema_version": "1.0", "id": "B-" + cycle_id, "promoted_at": _now(),
            "promoted_by": reviewer, "note": note, "source_cycle": cycle_id,
            "previous": old.get("id"), "versions": manifest.get("versions", {}),
            "references_sha256": sense.get("references_sha256")}
    write_json(os.path.join(base, "baseline.json"), meta)
    from .living_report import update
    update(task_dir, event="promote")
    return meta



def note_reopen(task_dir, manifest):
    """Mark a stopped task for reopening when a monitor cycle raised a reopen trigger."""
    reasons = [t.split(":", 1)[1] for t in manifest.get("triggers", []) if t.startswith("reopen:")]
    state = read_state(task_dir)
    if reasons and state.get("phase") == "monitoring":
        state.update({"phase": "reopen_requested", "reopen_reasons": reasons,
                      "reopen_cycle": manifest.get("cycle_id")})
        write_state(task_dir, state)
        from .living_report import update
        update(task_dir, event="reopen")
    return reasons


def status(task_dir):
    """Return the durable five-second status view for one living task."""
    from .task_status import build
    return build(task_dir)


def resume(task_dir, no_agent=False, max_rounds=None):
    """Resume interrupted work using the persisted task folder, never chat history."""
    from .cycle import find_incomplete_cycle, run_cycle

    task_dir = os.path.abspath(str(task_dir))
    state = read_state(task_dir)
    incomplete = find_incomplete_cycle(task_dir)
    if (incomplete and incomplete.get("mode") == "solve") or state.get("phase") in (
            "solving", "reopen_requested"):
        from .solve import solve
        until = (state.get("solve_session") or {}).get("until") or state.get("until") or "goal"
        final = solve(task_dir, until=until, max_rounds=max_rounds, no_agent=no_agent)
        return {"action": "solve", "resumed": True, "state": final}
    if incomplete:
        manifest = run_cycle(task_dir, mode=incomplete.get("mode", "monitor"), no_agent=no_agent)
        note_reopen(task_dir, manifest)
        return {"action": "cycle", "resumed": True, "cycle": manifest.get("cycle_id"),
                "status": manifest.get("status"), "degraded": manifest.get("degraded")}
    return {"action": "none", "resumed": False, "status": status(task_dir)}


def dumps(data):
    return json.dumps(data, indent=2, sort_keys=True, default=str)


__all__ = ["make_living", "promote", "read_state", "write_state", "note_reopen", "status",
           "resume", "CATEGORIES"]
