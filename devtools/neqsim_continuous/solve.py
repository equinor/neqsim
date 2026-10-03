"""The solve loop: iterate cycles until a stop rule fires.

Each round runs a cycle in ``solve`` mode. A solver stage (usually ``script:solver``)
reports the validated objective, the remaining candidate actions (optionally grouped in
branches), blockers and a reachable bound through ``ctx.solve``. The loop keeps at most
``max_branches`` branches open, evaluates the stop rules, asks an independent critic
before reporting ``goal_met`` or ``converged`` (when enabled), and records the result in
``continuous/state.json``.
"""

import copy
import uuid
from datetime import datetime, timezone

from . import agent_launch
from .cycle import list_cycles, load_cycle, run_cycle
from .plan import continuous_dir, load_goal, load_plan
from .state import host_id, read_state, write_state
from .stop_rules import _net_ei, evaluate

DEFAULT_SOLVE_STAGES = ["sense", "refresh", "script:solver", "kpis", "goal", "diff", "ledger",
                        "digest", "notify"]


def _prune_branches(candidates, max_branches, cost):
    """Keep candidates of the ``max_branches`` most promising branches; return (kept, paused)."""
    best = {}
    for candidate in candidates:
        branch = candidate.get("branch", "main")
        best[branch] = max(best.get(branch, float("-inf")), _net_ei(candidate, cost))
    ranked = sorted(best, key=lambda b: best[b], reverse=True)
    active = set(ranked[:max_branches])
    kept = [c for c in candidates if c.get("branch", "main") in active]
    return kept, sorted(set(ranked) - active)


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _history_entry(manifest, number, recovered=False):
    result = manifest.get("solve") or {}
    return {"round": number, "cycle": manifest["cycle_id"],
            "value": result.get("value"), "validated": bool(result.get("validated")),
            "confidence": result.get("confidence", "low"),
            "action": result.get("action"), "predicted_delta": result.get("predicted_delta"),
            "blockers": list(result.get("blockers") or []),
            "candidates": list(result.get("candidates") or []),
            "upper_bound": result.get("upper_bound"),
            "degraded": manifest.get("degraded"), "recovered": bool(recovered)}


def _uncheckpointed_cycles(task_dir, history, cutoff):
    """Find completed solve cycles written after the active session began."""
    recorded = {entry.get("cycle") for entry in history}
    found = []
    for cycle_id in list_cycles(task_dir):
        if cycle_id in recorded:
            continue
        manifest = load_cycle(task_dir, cycle_id) or {}
        if manifest.get("mode") != "solve" or manifest.get("status") != "complete":
            continue
        if not manifest.get("solve"):
            continue
        stamp = manifest.get("started_at") or manifest.get("now") or ""
        if cutoff and stamp and stamp < cutoff:
            continue
        found.append(manifest)
    return sorted(found, key=lambda item: (
        item.get("started_at") or item.get("now") or "", item.get("cycle_id") or ""))


def _checkpoint(task_dir, previous, decision, history, paused, sessions, until, session,
                next_action):
    details = dict(decision.details if decision is not None else {})
    if next_action and "next_action" not in details:
        details["next_action"] = next_action
    current = dict(previous)
    current.update({"state": decision.state if decision and decision.stop else "solving",
                    "phase": "solving",
                    "reason": decision.reason if decision else "solve session active",
                    "details": details, "rounds": len(history), "history": history,
                    "paused_branches": paused, "agent_sessions": sessions, "until": until,
                    "solve_session": session})
    return write_state(task_dir, current)


def solve(task_dir, until="goal", max_rounds=None, no_agent=False, allow_unconfirmed=False,
          now=None, reset=False):
    """Run or resume solve rounds until a stop state; return the final loop state."""
    if until not in ("goal", "converged"):
        raise ValueError("until must be 'goal' or 'converged'")
    goal = load_goal(task_dir)
    if not goal.get("objective", {}).get("metric"):
        raise ValueError("goal.yaml has no objective.metric; compile the brief first")
    if not goal.get("confirmed_by") and not allow_unconfirmed:
        raise ValueError("goal.yaml is not confirmed (set confirmed_by) - refusing to solve")
    plan = load_plan(task_dir)
    config = plan.get("solve", {})
    stages = config.get("stages", DEFAULT_SOLVE_STAGES)
    max_rounds = int(max_rounds or config.get("max_rounds")
                     or goal.get("stop", {}).get("budget", {}).get("iterations") or 10)
    max_branches = int(config.get("max_branches", 3))
    cost = float(goal.get("stop", {}).get("expected_improvement_cost", 0.0))
    rule_goal = copy.deepcopy(goal)
    if until == "converged":
        rule_goal.setdefault("objective", {})["target"] = None

    previous = {} if reset else read_state(task_dir)
    resuming = not reset and previous.get("phase") in ("solving", "reopen_requested")
    history = list(previous.get("history", [])) if resuming else []
    sessions = int(previous.get("agent_sessions", 0)) if resuming else 0
    old_session = dict(previous.get("solve_session") or {}) if resuming else {}
    session = {
        "id": old_session.get("id") or "S-" + uuid.uuid4().hex[:12],
        "status": "running",
        "started_at": old_session.get("started_at") or previous.get("updated") or _now(),
        "resumed_at": _now() if resuming else None,
        "resume_count": int(old_session.get("resume_count", 0) or 0) + (1 if resuming else 0),
        "last_host": host_id(),
        "until": old_session.get("until") or until,
        "last_cycle": old_session.get("last_cycle"),
    }
    until = session["until"]
    if until == "converged":
        rule_goal.setdefault("objective", {})["target"] = None

    decision, paused = None, list(previous.get("paused_branches") or []) if resuming else []
    next_action = (previous.get("details") or {}).get("next_action") if resuming else None
    previous = write_state(task_dir, dict(previous, state="solving", phase="solving",
                                          history=history, rounds=len(history),
                                          solve_session=session, until=until))

    # A process can die after a cycle is complete but before state.json is checkpointed. Re-adopt
    # those immutable cycle results before launching any new engineering work.
    for manifest in _uncheckpointed_cycles(task_dir, history, session["started_at"]):
        result = manifest.get("solve") or {}
        candidates, paused = _prune_branches(result.get("candidates", []) or [],
                                             max_branches, cost)
        history.append(_history_entry(manifest, len(history) + 1, recovered=True))
        decision = evaluate(rule_goal, history,
                            candidates=candidates if "candidates" in result else None,
                            blockers=result.get("blockers"),
                            usage={"iterations": len(history), "agent_sessions": sessions},
                            upper_bound=result.get("upper_bound"))
        next_action = decision.details.get("next_action")
        session["last_cycle"] = manifest["cycle_id"]
        session["checkpointed_at"] = _now()
        previous = _checkpoint(task_dir, previous, decision, history, paused, sessions,
                               until, session, next_action)
        if decision.stop:
            break

    remaining = max(0, max_rounds - len(history))
    if decision is None or not decision.stop:
        for _ in range(remaining):
            manifest = run_cycle(task_dir, mode="solve", now=now, stages=stages,
                                 no_agent=no_agent, next_action=next_action)
            result = manifest.get("solve") or {}
            candidates, paused = _prune_branches(result.get("candidates", []) or [],
                                                 max_branches, cost)
            history.append(_history_entry(manifest, len(history) + 1))
            decision = evaluate(rule_goal, history,
                                candidates=candidates if "candidates" in result else None,
                                blockers=result.get("blockers"),
                                usage={"iterations": len(history), "agent_sessions": sessions},
                                upper_bound=result.get("upper_bound"))
            next_action = decision.details.get("next_action")
            session["last_cycle"] = manifest["cycle_id"]
            session["checkpointed_at"] = _now()
            previous = _checkpoint(task_dir, previous, decision, history, paused, sessions,
                                   until, session, next_action)
            if decision.stop:
                break

    if decision is None or not decision.stop:
        decision_state = {"state": "budget_exhausted", "reason": "max_rounds reached",
                          "details": {"rounds": len(history)}}
    else:
        decision_state = decision.to_dict()
    critic = None
    if decision_state["state"] in ("goal_met", "converged") and plan.get("solve", {}).get(
            "critic", {}).get("enabled") and not no_agent:
        argv = agent_launch.build_command(task_dir, continuous_dir(task_dir),
                                          agent_launch.critic_prompt(decision_state["state"],
                                                                     decision_state["reason"]),
                                          agent=plan["solve"]["critic"].get(
                                              "agent", "continuous-improvement"))
        critic = agent_launch.launch(argv, cwd=str(task_dir))
        sessions += 1
    session.update({"status": "complete", "finished_at": _now(),
                    "last_host": host_id(), "last_cycle":
                    history[-1].get("cycle") if history else session.get("last_cycle")})
    final = write_state(task_dir, {"state": decision_state["state"], "phase": "monitoring",
                                   "reason": decision_state["reason"],
                                   "details": decision_state.get("details", {}),
                                   "rounds": len(history), "history": history,
                                   "paused_branches": paused, "agent_sessions": sessions,
                                   "critic": critic, "until": until,
                                   "solve_session": session})
    from .living_report import update
    update(task_dir, event="solve")
    return final
