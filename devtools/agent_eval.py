#!/usr/bin/env python
"""Deterministic agent evaluation harness (CI gate for routing and result contracts).

Two layers, both offline and LLM-free so they run on every PR:

* **routing** - a user prompt is fed to the same retrievers agents use
  (``agent_search.search`` and ``skill_search.search``); the expected agent
  handle(s) and skill name(s) must appear within top-k. This catches the
  silent failure mode of description edits, renames and new agents that
  push the right specialist out of reach.
* **contracts** - a ``results.json`` shape is fed to
  ``validate_task_results.validate``; a ``pass`` case must produce no errors, a
  ``fail`` case must produce an error whose prefix is ``fail_on``. This pins the
  quality gate's behaviour so a refactor cannot loosen it unnoticed.

Cases live in ``devtools/agent_eval_cases.json``. Every routing case is scored
individually (hit / miss with the actual top-k) so a regression names the case.

Usage:
    python devtools/agent_eval.py            # run all, exit 1 on any miss
    python devtools/agent_eval.py --json     # machine-readable report
    python devtools/agent_eval.py --only routing
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_search  # noqa: E402
import skill_search  # noqa: E402
import validate_task_results as vtr  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
SKILLS_ROOT = REPO_ROOT / ".github" / "skills"
CASES = Path(__file__).resolve().parent / "agent_eval_cases.json"


def load_cases(path: Path = CASES) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def eval_routing(case: dict, agent_top: int = 3, skill_top: int = 5) -> dict:
    prompt = case["prompt"]
    all_agents = agent_search._load_agents(REPO_ROOT)
    known_handles = {rec[5] for rec in all_agents}
    agents = agent_search.search(prompt, REPO_ROOT, top=agent_top)
    agent_handles = [rec[5] for _score, rec in agents]
    skills = skill_search.search(prompt, SKILLS_ROOT, top=skill_top)
    skill_names = [name for _score, name, _desc in skills]

    # Sibling agent/skill repos are optional (absent in core-only CI): only
    # expectations that exist in the indexed catalogue are enforced.
    want_agents = [a for a in case.get("expect_agent_top3", []) if a in known_handles]
    known_skills = {name for _s, name, _p in skill_search._load_skills(SKILLS_ROOT)}
    want_skills = [s for s in case.get("expect_skills_top5", []) if s in known_skills]
    # Any one of the acceptable agents is enough; every listed skill must surface.
    agent_hit = not want_agents or any(a in agent_handles for a in want_agents)
    missing_skills = [s for s in want_skills if s not in skill_names]
    return {
        "id": case["id"],
        "ok": agent_hit and not missing_skills,
        "agent_hit": agent_hit,
        "agents_top": agent_handles,
        "missing_skills": missing_skills,
        "skills_top": skill_names,
    }


def eval_contract(case: dict) -> dict:
    errors, _warnings = vtr.validate(case["results"])
    if case["expect"] == "pass":
        ok = not errors
        detail = errors
    else:
        # Validator errors are "<key>: msg" or "<key>.<sub>: msg".
        key = case["fail_on"]
        ok = any(e.startswith(key + ":") or e.startswith(key + ".") for e in errors)
        detail = errors
    return {"id": case["id"], "ok": ok, "expect": case["expect"], "errors": detail}


def run(only: str = None, cases: dict = None) -> Dict[str, List[dict]]:
    cases = cases or load_cases()
    report: Dict[str, List[dict]] = {}
    if only in (None, "routing"):
        report["routing"] = [eval_routing(c) for c in cases.get("routing", [])]
    if only in (None, "contracts"):
        report["contracts"] = [eval_contract(c) for c in cases.get("contracts", [])]
    return report


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", choices=["routing", "contracts"])
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--cases", type=Path, default=CASES)
    args = parser.parse_args(argv)

    report = run(args.only, load_cases(args.cases))
    failed = 0
    if args.json:
        print(json.dumps(report, indent=2))
    for layer, results in report.items():
        for r in results:
            if r["ok"]:
                continue
            failed += 1
            if not args.json:
                print("FAIL [{}] {}".format(layer, r["id"]))
                if layer == "routing":
                    if not r["agent_hit"]:
                        print("      agents top-k: {}".format(r["agents_top"]))
                    if r["missing_skills"]:
                        print("      skills missing {} from top-k {}".format(
                            r["missing_skills"], r["skills_top"]))
                else:
                    print("      expected {}; errors: {}".format(r["expect"], r["errors"]))
    total = sum(len(v) for v in report.values())
    if not args.json:
        print("agent eval: {}/{} cases passed".format(total - failed, total))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
