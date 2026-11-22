"""Discrete Bayesian tracking of candidate causes and ranking of the next most informative test.

A root-cause question has a few competing hypotheses. Instead of an agent writing confidence numbers by
hand each cycle, the agent (or engineer) records each observation once with the probability of seeing it
under every hypothesis, ``P(observation | hypothesis)``. This module does the arithmetic:

* sequential Bayes update in date order, with a reliability for second-hand observations (an unreliable
  observation is mixed with "no information"). The arithmetic is exact; ``floor`` is a reporting threshold:
  a live hypothesis below it is shown as "< floor" and is never called ruled out. Applying a floor inside
  the update would cap the leader and stop evidence from accumulating;
* correlated evidence: observations with the same ``family`` (the same mechanism seen again) are tempered,
  the n-th member counting with weight ``family_decay**n`` (default 0.5), so ten repeats of one
  fingerprint do not look like ten independent proofs (the usual cause of over-confident Bayes);
* the weight of each observation (odds factor), so the report can say which evidence moved the answer,
  and a leave-one-out check that shows how much the answer depends on any single observation;
* for each candidate test with discrete outcomes, the expected information gain (entropy reduction in
  bits) on the cause and on the decision groups (for example "gas dry" against "gas wet"), the chance of
  each outcome and the posterior after it, ranked by information per unit of effort.

The likelihoods are engineering judgements; the module makes them explicit, consistent and auditable, it
does not make them true. File: ``continuous/hypotheses.yaml``; ``neqsim task-hypotheses <task>``.
"""

import math
import os
from collections import OrderedDict

from .plan import continuous_dir, write_json

HYPOTHESES_FILE = "hypotheses.yaml"
STATE_FILE = "hypotheses_state.json"
_MIN_LIKELIHOOD = 1e-6


def template():
    """Return a commented example file as text."""
    return """# Candidate causes, observations and candidate tests (neqsim task-hypotheses).
# Each likelihood is P(seeing this | hypothesis is true), 0..1. Ask: "if this cause were the real one,
# how surprised would I be to see this?" 0.9 = expected, 0.5 = says nothing, 0.05 = very surprising.
schema_version: "1.0"
question: "Why does X read Y, and is it safe to do Z?"
floor: 0.02            # report threshold: a live hypothesis below it is shown as "< 2 %", never "ruled out"
rank_by: auto          # auto, decision (information on the decision groups) or cause
family_decay: 0.5      # weight of the n-th observation of the same family is 0.5**n
hypotheses:
  - {id: instrument, name: "Instrument or sample system wrong (flow, line, heating)", short: "Instrument", prior: 0.5}
  - {id: process, name: "Process really changed", prior: 0.4}
  - {id: other, name: "Other / not yet identified", prior: 0.1}
decision_groups:       # optional; must cover every hypothesis exactly once
  process_ok: [instrument]
  process_not_ok: [process]
  unknown: [other]
observations:
  - id: step_without_process_change
    date: "2026-01-02"
    description: "Reading stepped 10 K with flat rate and flat downstream analyser"
    source: "historian tag ..."
    reliability: 1.0   # 1 = verified record; 0.5 = second-hand message
    family: analyser_moves_alone   # optional: repeats of the same mechanism count less
    likelihood: {instrument: 0.8, process: 0.05, other: 0.3}
tests:
  - id: independent_reading
    name: "Independent reading at the tap"
    owner: "Automation"
    cost: 1.0          # relative effort (for example days); information is ranked per unit cost
    outcomes:          # per hypothesis the outcome probabilities sum to 1
      dry: {instrument: 0.9, process: 0.1, other: 0.5}
      wet: {instrument: 0.1, process: 0.9, other: 0.5}
"""


def load(task_dir):
    """Return the parsed hypotheses file, or None when the task has none."""
    path = os.path.join(continuous_dir(task_dir), HYPOTHESES_FILE)
    if not os.path.isfile(path):
        return None
    import yaml
    with open(path, "r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def _ids(spec):
    return [str(h["id"]) for h in spec.get("hypotheses") or []]


def validate(spec):
    """Return a list of problems; an empty list means the file can be computed."""
    problems = []
    ids = _ids(spec)
    if len(ids) < 2:
        problems.append("at least two hypotheses are needed")
    if len(set(ids)) != len(ids):
        problems.append("hypothesis ids must be unique")
    for hyp in spec.get("hypotheses") or []:
        if not isinstance(hyp.get("prior", 0), (int, float)) or hyp.get("prior", 0) < 0:
            problems.append("hypothesis {}: prior must be a non-negative number".format(hyp.get("id")))
    floor = spec.get("floor", 0.0)
    if not isinstance(floor, (int, float)) or floor < 0 or floor >= 0.5:
        problems.append("floor must be within 0..0.5 (it is a reporting threshold)")
    decay = spec.get("family_decay", 0.5)
    if not isinstance(decay, (int, float)) or not 0 < decay <= 1:
        problems.append("family_decay must be within (0, 1]")
    if spec.get("rank_by", "auto") not in ("auto", "decision", "cause"):
        problems.append("rank_by must be auto, decision or cause")
    groups = spec.get("decision_groups") or {}
    if groups:
        listed = [h for members in groups.values() for h in members]
        if sorted(listed) != sorted(ids):
            problems.append("decision_groups must list every hypothesis exactly once")
    seen = set()
    for obs in spec.get("observations") or []:
        name = "observation {}".format(obs.get("id"))
        if obs.get("id") in seen:
            problems.append(name + ": duplicate id (count each piece of evidence once)")
        seen.add(obs.get("id"))
        problems += _check_row(obs.get("likelihood"), ids, name)
        reliability = obs.get("reliability", 1.0)
        if not isinstance(reliability, (int, float)) or not 0 <= reliability <= 1:
            problems.append(name + ": reliability must be within 0..1")
        weight = obs.get("weight")
        if weight is not None and (not isinstance(weight, (int, float)) or not 0 <= weight <= 1):
            problems.append(name + ": weight must be within 0..1")
    for test in spec.get("tests") or []:
        name = "test {}".format(test.get("id"))
        outcomes = test.get("outcomes") or {}
        if len(outcomes) < 2:
            problems.append(name + ": needs at least two outcomes")
            continue
        for outcome, row in outcomes.items():
            problems += _check_row(row, ids, "{} outcome {}".format(name, outcome))
        for hyp in ids:
            total = sum(float((row or {}).get(hyp, 0.0)) for row in outcomes.values())
            if abs(total - 1.0) > 0.02:
                problems.append("{}: outcome probabilities for {} sum to {:.2f}, not 1".format(name, hyp, total))
        if not isinstance(test.get("cost", 1.0), (int, float)) or test.get("cost", 1.0) <= 0:
            problems.append(name + ": cost must be positive")
    return problems


def _check_row(row, ids, name):
    if not isinstance(row, dict):
        return [name + ": likelihood must map every hypothesis id to a probability"]
    problems = []
    missing = [h for h in ids if h not in row]
    if missing:
        problems.append("{}: missing {}".format(name, ", ".join(missing)))
    unknown = [h for h in row if h not in ids]
    if unknown:
        problems.append("{}: unknown hypothesis {}".format(name, ", ".join(map(str, unknown))))
    for hyp, value in row.items():
        if not isinstance(value, (int, float)) or not 0 <= value <= 1:
            problems.append("{}: {} must be within 0..1".format(name, hyp))
    return problems


def _normalise(probs):
    total = sum(probs.values())
    return OrderedDict((k, v / total) for k, v in probs.items()) if total > 0 else probs


def _effective(row, reliability, ids, weight=1.0):
    """Discount an observation that may be wrong (with probability 1 - r it carries no information) and
    temper a repeated one (likelihood raised to ``weight`` in 0..1)."""
    values = [max(float(row[h]), _MIN_LIKELIHOOD) for h in ids]
    mean = sum(values) / len(values)
    return OrderedDict((h, (reliability * v + (1.0 - reliability) * mean) ** weight) for h, v in zip(ids, values))


def _weights(observations, decay):
    """Weight per observation: explicit ``weight``, else ``decay**n`` for the n-th member of its family."""
    seen, out = {}, []
    for obs in observations:
        if obs.get("weight") is not None:
            out.append(float(obs["weight"]))
            continue
        family = obs.get("family")
        index = seen.get(family, 0) if family else 0
        if family:
            seen[family] = index + 1
        out.append(decay ** index)
    return out


def _posterior(prior, observations, ids, decay):
    probs = _normalise(prior)
    for obs, weight in zip(observations, _weights(observations, decay)):
        probs = _update(probs, _effective(obs["likelihood"], float(obs.get("reliability", 1.0)), ids, weight))
    return probs


def _entropy(probs):
    return -sum(p * math.log(p, 2) for p in probs if p > 0)


def _group_probs(probs, groups):
    return OrderedDict((g, sum(probs[h] for h in members)) for g, members in groups.items())


def _update(probs, likelihood):
    return _normalise(OrderedDict((h, probs[h] * likelihood[h]) for h in probs))


def compute(spec):
    """Run the update and the test ranking; returns a JSON-serialisable result."""
    ids = _ids(spec)
    floor = float(spec.get("floor", 0.0))
    excluded = {str(h["id"]) for h in spec.get("hypotheses") or [] if h.get("excluded")}
    names = OrderedDict((str(h["id"]), h.get("name", h["id"])) for h in spec.get("hypotheses") or [])
    # Short label for tables and legends: explicit ``short`` or the name up to its first parenthesis.
    short = OrderedDict((str(h["id"]), h.get("short") or str(h.get("name", h["id"])).split(" (")[0])
                        for h in spec.get("hypotheses") or [])
    groups = OrderedDict((str(g), [str(m) for m in members]) for g, members in (spec.get("decision_groups") or {}).items())
    prior = OrderedDict((str(h["id"]), 0.0 if str(h["id"]) in excluded else float(h.get("prior", 1.0)))
                        for h in spec.get("hypotheses") or [])
    probs = _normalise(prior)
    history = [{"after": "prior", "date": None, "probabilities": dict(probs)}]
    weights = []
    decay = float(spec.get("family_decay", 0.5))
    observations = sorted(spec.get("observations") or [], key=lambda o: (str(o.get("date", "")), str(o.get("t_utc", ""))))
    for obs, weight in zip(observations, _weights(observations, decay)):
        likelihood = _effective(obs["likelihood"], float(obs.get("reliability", 1.0)), ids, weight)
        before = probs
        probs = _update(probs, likelihood)
        if groups:
            # Odds factor of the first decision group against the rest, at the state before the observation.
            first = next(iter(groups))
            inside = sum(before[h] * likelihood[h] for h in groups[first]) / max(sum(before[h] for h in groups[first]), 1e-12)
            rest = [h for h in ids if h not in groups[first]]
            outside = sum(before[h] * likelihood[h] for h in rest) / max(sum(before[h] for h in rest), 1e-12)
            label = first
        else:
            leader = max(before, key=before.get)
            rest = [h for h in ids if h != leader]
            inside = likelihood[leader]
            outside = sum(before[h] * likelihood[h] for h in rest) / max(sum(before[h] for h in rest), 1e-12)
            label = leader
        weights.append({"id": obs.get("id"), "date": obs.get("date"), "description": obs.get("description", ""),
                        "source": obs.get("source", ""), "reliability": float(obs.get("reliability", 1.0)),
                        "family": obs.get("family", ""), "weight": weight,
                        "odds_factor": inside / outside if outside > 0 else float("inf"), "odds_for": label,
                        "after": dict(probs)})
        history.append({"after": obs.get("id"), "date": obs.get("date"), "probabilities": dict(probs)})
    tests = [_score_test(test, probs, ids, groups) for test in spec.get("tests") or []]
    rank_by = spec.get("rank_by", "auto")
    group_now = _group_probs(probs, groups) if groups else {}
    if rank_by == "auto":
        # Once the decision is settled the useful question is which cause: rank on the cause then.
        settled = float(spec.get("decision_settled", 0.9))
        rank_by = "decision" if groups and max(group_now.values()) < settled else "cause"
    key = "info_decision_bits" if groups and rank_by == "decision" else "info_cause_bits"
    for test in tests:
        test["rank_value"] = test[key] / test["cost"]
    tests.sort(key=lambda t: t["rank_value"], reverse=True)
    leader = max(probs, key=probs.get)
    robustness = []
    for index, obs in enumerate(observations):
        without = _posterior(prior, observations[:index] + observations[index + 1:], ids, decay)
        robustness.append({"id": obs.get("id"), "leader_without": without[leader], "shift": without[leader] - probs[leader],
                           "groups_without": dict(_group_probs(without, groups)) if groups else {}})
    return {"schema_version": "1.0", "question": spec.get("question", ""), "names": dict(names), "short": dict(short),
            "posterior": dict(probs), "leader": leader, "groups": {g: m for g, m in groups.items()},
            "group_posterior": dict(_group_probs(probs, groups)) if groups else {},
            "entropy_bits": _entropy(probs.values()), "history": history, "weights": weights,
            "below_floor": sorted(
                h for h in ids if h not in excluded and probs[h] < floor),
            "tests": tests, "rank_by": key, "floor": floor, "robustness": robustness}


def _score_test(test, probs, ids, groups):
    outcomes = []
    info_cause = _entropy(probs.values())
    info_group = _entropy(_group_probs(probs, groups).values()) if groups else 0.0
    expected_cause, expected_group = 0.0, 0.0
    for outcome, row in (test.get("outcomes") or {}).items():
        likelihood = OrderedDict((h, max(float(row[h]), _MIN_LIKELIHOOD)) for h in ids)
        chance = sum(probs[h] * likelihood[h] for h in ids)
        posterior = _update(probs, likelihood) if chance > 0 else probs
        expected_cause += chance * _entropy(posterior.values())
        if groups:
            expected_group += chance * _entropy(_group_probs(posterior, groups).values())
        leader = max(posterior, key=posterior.get)
        outcomes.append({"outcome": outcome, "chance": chance, "posterior": dict(posterior), "leader": leader,
                         "leader_probability": posterior[leader],
                         "group_posterior": dict(_group_probs(posterior, groups)) if groups else {}})
    return {"id": test.get("id"), "name": test.get("name", test.get("id")), "owner": test.get("owner", ""),
            "cost": float(test.get("cost", 1.0)), "cost_unit": test.get("cost_unit", ""),
            "info_cause_bits": info_cause - expected_cause,
            "info_decision_bits": (info_group - expected_group) if groups else 0.0, "outcomes": outcomes}


def run(task_dir):
    """Load, validate, compute and write ``continuous/hypotheses_state.json``; returns (result, problems)."""
    spec = load(task_dir)
    if spec is None:
        return None, ["no continuous/{} in the task".format(HYPOTHESES_FILE)]
    problems = validate(spec)
    if problems:
        return None, problems
    result = compute(spec)
    write_json(os.path.join(continuous_dir(task_dir), STATE_FILE), result)
    return result, []


def format_probability(value, floor=0.0):
    """Percent text; a live value below the reporting floor is shown as "< floor" rather than a false zero."""
    if floor and value < floor:
        return "< {:.0%}".format(floor)
    return "{:.0%}".format(value) if value >= 0.1 else "{:.1%}".format(value)


def robustness_text(result):
    """Sentence naming the single observation whose removal would move the leading cause most."""
    if not result.get("robustness"):
        return ""
    biggest = max(result["robustness"], key=lambda r: abs(r["shift"]))
    leader = result["names"].get(result["leader"], result["leader"])
    return ("the leading cause ({}, {}) depends most on '{}': without it, {}.".format(
        leader, format_probability(result["posterior"][result["leader"]]), biggest["id"].replace("_", " "),
        format_probability(biggest["leader_without"])))


def summary(result, tests=3):
    """Plain-text summary for the command line."""
    floor = result.get("floor", 0.0)
    lines = ["Question: " + result["question"], "", "Candidate causes now:"]
    for hyp, value in sorted(result["posterior"].items(), key=lambda item: -item[1]):
        lines.append("  {:>7}  {}".format(format_probability(value, floor), result["names"].get(hyp, hyp)))
    if result["group_posterior"]:
        lines += ["", "Decision groups: " + ", ".join(
            "{} {}".format(g, format_probability(p, floor)) for g, p in result["group_posterior"].items())]
    if result.get("robustness"):
        lines += ["", "Robustness: " + robustness_text(result)]
    basis = "decision" if result["rank_by"] == "info_decision_bits" else "cause"
    lines += ["", "Next most informative tests, ranked by information on the {} per unit cost:".format(basis)]
    for test in result["tests"][:tests]:
        lines.append("  {} ({}): {:.2f} bits on the decision, {:.2f} bits on the cause, cost {:g} {}".format(
            test["name"], test["owner"], test["info_decision_bits"], test["info_cause_bits"], test["cost"],
            test["cost_unit"]).rstrip())
        for outcome in test["outcomes"]:
            lines.append("      if {} (chance {:.0%}): leader {} at {}".format(
                outcome["outcome"], outcome["chance"], result["names"].get(outcome["leader"], outcome["leader"]),
                format_probability(outcome["leader_probability"])))
    return "\n".join(lines)
