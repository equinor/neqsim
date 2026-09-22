"""
agent_search.py - Semantic agent retrieval for NeqSim task solving.

Mirrors ``skill_search.py`` but ranks *agents* instead of skills. It builds a
TF-IDF + cosine similarity index over the YAML front-matter ``description``
fields of every agent definition it can find across the multi-repo workspace:

  * neqsim repo             : ``.github/agents/*.agent.md``
  * neqsim-community-agents : ``agents/<name>/AGENT.md`` (if cloned as a sibling)
  * neqsim-enterprise-agents: ``agents/<name>/AGENT.md`` (if cloned as a sibling)
  * ~/.neqsim/agents        : ``<name>/AGENT.md`` (agents installed via
    ``neqsim agent install``/``--all`` — this is the normal way most users obtain
    community/private agents, and does not require the *-agents repos above to be
    cloned locally)

Why TF-IDF and not sentence embeddings? Agent descriptions are short and
keyword-dense, so character + word n-gram TF-IDF gives most of the recall of a
sentence-transformer with zero heavy dependencies (only scikit-learn, already in
dev requirements). If scikit-learn is unavailable it falls back to a pure-python
Jaccard search so the tool never hard-fails during a task.

The point of this tool is to make the solver agent *discover the best agents*
(and the skills each agent chains to) at the start of a task, instead of relying
only on the hand-maintained routing table in ``router.agent.md``. Persist the
JSON output into ``capability_assessment.md`` and ``results.json`` so the choice
of agents/workflow is auditable and feeds the final report.

Usage::

    python devtools/agent_search.py "hydrate margin for subsea gas tieback"
    python devtools/agent_search.py "CO2 pipeline wall thickness" --top 8
    python devtools/agent_search.py --json "compressor surge control" --out plan.json

Returns a ranked list of agents by relevance, each with the skills it loads so
the caller can plan a multi-agent / multi-skill workflow.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import agent_frontmatter as af  # noqa: E402
import bm25  # noqa: E402

# An agent record is (name, haystack, path, required_skills, repo, handle).
# ``handle`` is the id used to *invoke* the agent (e.g. "capability.scout" or
# "hydrate-margin-agent") which is NOT always the same as the front-matter
# ``name`` (neqsim agents use a prose title there, e.g. "scout neqsim
# capabilities"). Callers delegate with the handle, so it must be surfaced.
AgentRecord = Tuple[str, str, str, List[str], str, str]


def _strip_yaml_value(s: str) -> str:
    return af.strip_yaml_scalar(s)


def _parse_front_matter(text: str) -> Optional[Dict[str, object]]:
    """Return ``name``/``description``/``required_skills`` from the front-matter, or None.

    Thin wrapper over :mod:`agent_frontmatter` kept for backwards-compatible
    imports; frontmatter ``required_skills`` (also ``loaded_skills``/``skills``)
    is returned under ``required_skills``.
    """
    front, _ = af.split_frontmatter(text)
    if front is None:
        return None
    fm = af.parse_frontmatter(text)
    out: Dict[str, object] = {}
    if isinstance(fm.get("name"), str):
        out["name"] = fm["name"]
    if isinstance(fm.get("description"), str):
        out["description"] = fm["description"]
    skills: List[str] = []
    for key in af.SKILL_LIST_KEYS:
        value = fm.get(key)
        if isinstance(value, list):
            skills.extend(value)
        elif isinstance(value, str) and value:
            skills.extend(v.strip() for v in value.strip("[]").split(",") if v.strip())
    if skills:
        out["required_skills"] = skills
    return out


def _extract_loaded_skills_body(text: str) -> List[str]:
    """Parse the legacy body declarations (``Loaded skills:`` line or bullet block)."""
    _, body = af.split_frontmatter(text)
    return af.extract_body_skills(body)


def _handle_for_path(md: Path) -> str:
    """Return the kebab-case id used to invoke the agent (its @handle)."""
    return af.agent_id_for_path(md)


def _load_from_dir(agents_dir: Path, repo: str, pattern: str) -> List[AgentRecord]:
    """Load agent records from a directory using a glob pattern of *.md files."""
    out: List[AgentRecord] = []
    if not agents_dir.is_dir():
        return out
    for md in sorted(agents_dir.glob(pattern)):
        if not md.is_file():
            continue
        try:
            text = md.read_text(encoding="utf-8")
        except OSError:
            continue
        fm = _parse_front_matter(text)
        if fm is None:
            continue
        handle = _handle_for_path(md)
        name = str(fm.get("name") or handle)
        desc = str(fm.get("description") or "")
        skills = list(fm.get("required_skills") or [])  # type: ignore[arg-type]
        if not skills:
            skills = _extract_loaded_skills_body(text)
        # Handle so an @handle query matches; required skills because a skill id such
        # as neqsim-water-hammer is the sharpest routing signal an agent declares. The
        # folder name is skipped when it merely repeats the handle (agents/<id>/AGENT.md),
        # otherwise community ids were counted three times and outranked core agents.
        folder = md.parent.name if md.parent.name != handle else ""
        haystack = f"{name} {handle} {folder} {desc} {' '.join(skills)}"
        out.append((name, haystack, str(md), skills, repo, handle))
    return out


def _installed_agents_root() -> Path:
    """Return the directory ``neqsim agent install`` places agents into.

    Honors ``NEQSIM_AGENTS_HOME`` so tests (and any caller that needs isolation
    from the real machine's installed catalog) can redirect this without
    depending on ``Path.home()``. Mirrors ``install_agent.py``'s
    ``INSTALL_DIR = Path.home() / ".neqsim" / "agents"``.

    @return absolute path to the installed-agents directory (may not exist).
    """
    override = os.environ.get("NEQSIM_AGENTS_HOME")
    if override:
        return Path(override)
    return Path.home() / ".neqsim" / "agents"


def _discover_roots(repo_root: Path, extra: Optional[List[Path]]) -> List[Tuple[Path, str, str]]:
    """Return (dir, repo_label, glob) tuples for every agent source to index."""
    roots: List[Tuple[Path, str, str]] = []
    # 1) neqsim repo — flat *.agent.md files
    roots.append((repo_root / ".github" / "agents", "neqsim", "*.agent.md"))
    # 2) sibling *-agents repos — agents/<name>/AGENT.md
    workspace_root = repo_root.parent
    for sibling in ("neqsim-community-agents", "neqsim-enterprise-agents"):
        cand = workspace_root / sibling / "agents"
        roots.append((cand, sibling, "*/AGENT.md"))
    # 3) the user's locally *installed* agent catalog — ~/.neqsim/agents/<name>/AGENT.md.
    # This is where `neqsim agent install <name>` / `--all` actually places agents
    # (see install_agent.py INSTALL_DIR), independent of whether the community/
    # enterprise *-agents repos above happen to be cloned as siblings. Without this
    # root, any agent installed only via the CLI catalog (the normal, documented way
    # to obtain community/private agents) is invisible to this search even though it
    # is fully installed and already invocable — see CHANGELOG_AGENT_NOTES.md.
    roots.append((_installed_agents_root(), "installed", "*/AGENT.md"))
    # 4) explicit extra roots (auto-detect layout: flat vs nested)
    for path in extra or []:
        if (path / "agents").is_dir():
            roots.append((path / "agents", path.name, "*/AGENT.md"))
        else:
            roots.append((path, path.name, "*.agent.md"))
    return roots


def _load_agents(repo_root: Path, extra: Optional[List[Path]] = None) -> List[AgentRecord]:
    # Dedup by (repo, name) so cross-repo variants that intentionally share a
    # name (e.g. a community screening agent and its enterprise policy-gated
    # counterpart) are BOTH indexed — dropping either hides functionality.
    seen_keys = set()
    out: List[AgentRecord] = []
    for agents_dir, repo, pattern in _discover_roots(repo_root, extra):
        for rec in _load_from_dir(agents_dir, repo, pattern):
            key = (rec[4], rec[0].lower())
            if key in seen_keys:
                continue
            seen_keys.add(key)
            out.append(rec)
    return out


def _bm25_search(
    query: str, agents: List[AgentRecord], top: int
) -> List[Tuple[float, AgentRecord]]:
    """Rank agents with dependency-free BM25 so dev and CI score identically."""
    scores = bm25.BM25([a[1] for a in agents]).scores(query)
    order = sorted(range(len(agents)), key=lambda i: scores[i], reverse=True)
    return [(scores[i], agents[i]) for i in order[:top]]


def search(
    query: str, repo_root: Path, top: int = 5, extra: Optional[List[Path]] = None
) -> List[Tuple[float, AgentRecord]]:
    agents = _load_agents(repo_root, extra)
    if not agents:
        return []
    return _bm25_search(query, agents, top)


def _results_to_payload(query: str, results: List[Tuple[float, AgentRecord]]) -> dict:
    return {
        "query": query,
        "results": [
            {
                "score": round(score, 4),
                "name": rec[0],
                "handle": rec[5],
                "repo": rec[4],
                "loads_skills": rec[3],
                "path": rec[2],
            }
            for score, rec in results
        ],
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent

    parser = argparse.ArgumentParser(
        description="Semantic agent search across the NeqSim agent repos."
    )
    parser.add_argument("query", help="Natural-language description of the task")
    parser.add_argument(
        "--top", type=int, default=5, help="Number of agents to return (default 5)"
    )
    parser.add_argument(
        "--json", action="store_true", help="Emit machine-readable JSON output"
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Write JSON result to this path (implies --json format for the file)",
    )
    parser.add_argument(
        "--agents-root",
        action="append",
        default=None,
        help="Extra agent repo/dir to index (repeatable). Auto-detects flat vs agents/<name>/ layout.",
    )
    args = parser.parse_args()

    extra = [Path(p).resolve() for p in (args.agents_root or [])]
    results = search(args.query, repo_root, args.top, extra)
    payload = _results_to_payload(args.query, results)

    if args.out:
        Path(args.out).write_text(json.dumps(payload, indent=2), encoding="utf-8")
        print("Wrote {} agents to {}".format(len(results), args.out))

    if args.json:
        print(json.dumps(payload, indent=2))
        return 0

    if not results:
        print("No agents found. (Are the sibling *-agents repos checked out?)")
        return 0

    print("Top {} agents for: {!r}\n".format(len(results), args.query))
    for score, rec in results:
        name, _, _, skills, repo, handle = rec
        skill_hint = ("  loads: " + ", ".join(skills[:4])) if skills else ""
        print("  {:0.3f}  {:<38} (@{}) [{}]{}".format(
            score, name, handle, repo, skill_hint))
    return 0


if __name__ == "__main__":
    sys.exit(main())
