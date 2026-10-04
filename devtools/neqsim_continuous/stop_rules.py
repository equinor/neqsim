"""Stop and reopen rules for continuous task solving.

A solve loop stops in one of five states: ``goal_met``, ``infeasible``, ``blocked``,
``budget_exhausted`` or ``converged``. Otherwise it continues with the candidate
action that has the highest net expected improvement. Only validated objective
values count, so relaxing an assumption never registers as progress.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

STOP_STATES = ("goal_met", "infeasible", "blocked", "budget_exhausted", "converged")
CONFIDENCE_RANK = {"low": 0, "medium": 1, "high": 2}


@dataclass
class StopDecision:
    """The loop state after evaluating the rules."""

    state: str
    reason: str
    details: Dict[str, Any] = field(default_factory=dict)

    @property
    def stop(self):
        return self.state in STOP_STATES

    def to_dict(self):
        return {"state": self.state, "stop": self.stop, "reason": self.reason,
                "details": self.details}


def _sign(goal):
    direction = goal.get("objective", {}).get("direction", "maximize")
    if direction not in ("maximize", "minimize"):
        raise ValueError("objective.direction must be 'maximize' or 'minimize'")
    return 1.0 if direction == "maximize" else -1.0


def _validated(history):
    return [h for h in history if h.get("validated") and h.get("value") is not None]


def _net_ei(candidate, default_cost):
    p = float(candidate.get("p_success", 1.0))
    gain = float(candidate.get("predicted_delta", 0.0))
    cost = float(candidate.get("cost", default_cost))
    return p * gain - cost


def evaluate(goal, history, candidates=None, blockers=None, usage=None, upper_bound=None):
    """Decide whether a solve loop stops.

    Parameters
    ----------
    goal : dict
        Compiled ``goal.yaml``: ``objective`` (metric, direction, target,
        confidence_required) and ``stop`` (converged: window/relative/absolute;
        budget: iterations/agent_sessions; expected_improvement_cost).
    history : list of dict
        One record per iteration with ``value``, ``validated`` and ``confidence``.
    candidates : list of dict or None
        Remaining actions with ``p_success``, ``predicted_delta`` (in the improving
        direction) and optional ``cost``, ``needs_person``. ``None`` means unknown.
    blockers : list of str or None
        Items only a person can supply.
    usage : dict or None
        ``iterations`` and ``agent_sessions`` used so far.
    upper_bound : float or None
        Best value physically reachable under the constraints, when known.
    """
    objective = goal.get("objective", {})
    stop = goal.get("stop", {})
    sign = _sign(goal)
    target = objective.get("target")
    required = CONFIDENCE_RANK.get(objective.get("confidence_required", "low"), 0)
    valid = _validated(history)
    latest = valid[-1] if valid else None

    if latest is not None and target is not None:
        confident = CONFIDENCE_RANK.get(latest.get("confidence", "low"), 0) >= required
        if sign * (float(latest["value"]) - float(target)) >= 0 and confident:
            return StopDecision("goal_met", "validated objective reaches the target",
                                {"value": latest["value"], "target": target})

    if upper_bound is not None and target is not None:
        if sign * (float(upper_bound) - float(target)) < 0:
            return StopDecision("infeasible", "the reachable bound is short of the target",
                                {"upper_bound": upper_bound, "target": target})

    budget = stop.get("budget", {})
    usage = usage or {}
    for key in ("iterations", "agent_sessions"):
        limit = budget.get(key)
        if limit is not None and usage.get(key, 0) >= limit:
            return StopDecision("budget_exhausted", "{} budget used".format(key),
                                {key: usage.get(key, 0), "limit": limit})

    cost = float(stop.get("expected_improvement_cost", 0.0))
    actionable = [c for c in (candidates or []) if not c.get("needs_person")]
    if blockers and candidates is not None and not actionable:
        return StopDecision("blocked", "the next useful action needs a person",
                            {"blockers": list(blockers)})

    converged = stop.get("converged", {})
    window = int(converged.get("window", 3))
    if len(valid) > window and candidates is not None:
        values = [float(h["value"]) for h in valid]
        gains = [sign * (values[i] - values[i - 1]) for i in range(len(values) - window, len(values))]
        tolerance = max(float(converged.get("absolute", 0.0)),
                        float(converged.get("relative", 0.02)) * abs(values[-1]))
        best = max([_net_ei(c, cost) for c in actionable], default=0.0)
        if max(gains) < tolerance and best <= 0.0:
            return StopDecision("converged", "recent gains and best expected gain are marginal",
                                {"recent_gains": gains, "tolerance": tolerance,
                                 "best_net_expected_improvement": best})

    decision = StopDecision("solving", "continue")
    if actionable:
        best = max(actionable, key=lambda c: _net_ei(c, cost))
        decision.details["next_action"] = best.get("action")
        decision.details["net_expected_improvement"] = _net_ei(best, cost)
    elif candidates is None:
        decision.reason = "continue; no candidate estimates supplied"
    return decision


def should_reopen(goal, current_value=None, promoted_value=None, events=None):
    """Return the list of reasons a monitored task should re-enter the solve loop."""
    reopen = goal.get("reopen", {})
    reasons = []  # type: List[str]
    margin = reopen.get("regress_margin")
    if margin is not None and current_value is not None and promoted_value is not None:
        if _sign(goal) * (float(promoted_value) - float(current_value)) > float(margin):
            reasons.append("goal_regressed")
    watched = set(reopen.get("on", []))
    for event in events or []:
        if event in watched and event not in reasons:
            reasons.append(event)
    return reasons


def next_best(candidates: Optional[List[Dict[str, Any]]], cost: float = 0.0):
    """Return the actionable candidate with the highest net expected improvement."""
    actionable = [c for c in (candidates or []) if not c.get("needs_person")]
    if not actionable:
        return None
    return max(actionable, key=lambda c: _net_ei(c, cost))
