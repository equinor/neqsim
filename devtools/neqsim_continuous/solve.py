"""The solve loop: iterate cycles until a stop rule fires.

Each round runs a cycle in ``solve`` mode. A solver stage (usually ``script:solver``)
reports the validated objective, the remaining candidate actions (optionally grouped in
branches), blockers and a reachable bound through ``ctx.solve``. The loop keeps at most
``max_branches`` branches open, evaluates the stop rules, asks an independent critic
before reporting ``goal_met`` or ``converged`` (when enabled), and records the result in
``continuous/state.json``.
"""

import copy

from . import agent_launch
from .cycle import run_cycle
from .living import read_state, write_state
from .plan import continuous_dir, load_goal, load_plan
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


def solve(task_dir, until="goal", max_rounds=None, no_agent=False, allow_unconfirmed=False,
          now=None, reset=False):
    """Run solve rounds until a stop state; return the final loop state."""
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
    history = list(previous.get("history", [])) if previous.get("phase") in ("solving", "reopen_requested") else []
    sessions = int(previous.get("agent_sessions", 0))
    decision, paused, next_action = None, [], None
    write_state(task_dir, dict(previous, state="solving", phase="solving", history=history))

    for _ in range(max_rounds):
        manifest = run_cycle(task_dir, mode="solve", now=now, stages=stages, no_agent=no_agent,
                             next_action=next_action)
        result = manifest.get("solve") or {}
        candidates, paused = _prune_branches(result.get("candidates", []) or [], max_branches, cost)
        history.append({"round": len(history) + 1, "cycle": manifest["cycle_id"],
                        "value": result.get("value"), "validated": bool(result.get("validated")),
                        "confidence": result.get("confidence", "low"),
                        "action": result.get("action"), "predicted_delta": result.get("predicted_delta"),
                        "degraded": manifest.get("degraded")})
        decision = evaluate(rule_goal, history,
                            candidates=candidates if "candidates" in result else None,
                            blockers=result.get("blockers"),
                            usage={"iterations": len(history), "agent_sessions": sessions},
                            upper_bound=result.get("upper_bound"))
        next_action = decision.details.get("next_action")
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
                                          agent=plan["solve"]["critic"].get("agent", "continuous-improvement"))
        critic = agent_launch.launch(argv, cwd=str(task_dir))
        sessions += 1
    final = write_state(task_dir, {"state": decision_state["state"], "phase": "monitoring",
                                   "reason": decision_state["reason"],
                                   "details": decision_state.get("details", {}),
                                   "rounds": len(history), "history": history,
                                   "paused_branches": paused, "agent_sessions": sessions,
                                   "critic": critic, "until": until})
    from .living_report import update
    update(task_dir, event="solve")
    return final
