"""
agent_search.py - Semantic agent retrieval for NeqSim task solving.

Mirrors ``skill_search.py`` but ranks *agents* instead of skills. It builds a
TF-IDF + cosine similarity index over the YAML front-matter ``description``
fields of every agent definition it can find across the multi-repo workspace:

  * neqsim repo             : ``.github/agents/*.agent.md``
  * neqsim-community-agents : ``agents/<name>/AGENT.md``
  * neqsim-enterprise-agents: ``agents/<name>/AGENT.md``

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
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

# An agent record is (name, haystack, path, required_skills, repo, handle).
# ``handle`` is the id used to *invoke* the agent (e.g. "capability.scout" or
# "hydrate-margin-agent") which is NOT always the same as the front-matter
# ``name`` (neqsim agents use a prose title there, e.g. "scout neqsim
# capabilities"). Callers delegate with the handle, so it must be surfaced.
AgentRecord = Tuple[str, str, str, List[str], str, str]


def _strip_yaml_value(s: str) -> str:
    s = s.strip()
    if s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    elif s.startswith("'") and s.endswith("'"):
        s = s[1:-1]
    return s


def _parse_front_matter(text: str) -> Optional[Dict[str, object]]:
    """Return a shallow dict of the leading YAML front-matter block, or None.

    Only the keys needed for search are parsed (``name``, ``description``, and a
    simple ``required_skills`` / ``loaded_skills`` list). This avoids a PyYAML
    dependency and tolerates the small front-matter dialects used across repos.
    """
    if not text.startswith("---"):
        return None
    end = text.find("\n---", 3)
    if end < 0:
        return None
    front = text[3:end]
    out: Dict[str, object] = {}
    skills: List[str] = []
    in_skills = False
    for line in front.splitlines():
        raw = line.rstrip()
        stripped = raw.strip()
        if in_skills:
            if stripped.startswith("- "):
                skills.append(_strip_yaml_value(stripped[2:]))
                continue
            # A non-list, non-indented line ends the list block.
            if raw and not raw.startswith((" ", "\t", "-")):
                in_skills = False
            else:
                continue
        if stripped.startswith("name:"):
            out["name"] = _strip_yaml_value(stripped[5:])
        elif stripped.startswith("description:"):
            out["description"] = _strip_yaml_value(stripped[12:])
        elif re.match(r"^(required_skills|loaded_skills|skills)\s*:", stripped):
            value = stripped.split(":", 1)[1].strip()
            if value and value != "[]":
                # Inline list form: skills: [a, b] or skills: a, b
                value = value.strip("[]")
                skills.extend(
                    _strip_yaml_value(v) for v in value.split(",") if v.strip()
                )
            else:
                in_skills = True
    if skills:
        out["required_skills"] = skills
    return out


def _extract_loaded_skills_body(text: str) -> List[str]:
    """Parse a 'Loaded skills: a, b, c' line or a skills heading + bullet list.

    Handles the three conventions agents use in the body: an inline
    ``Loaded skills:`` line, a ``## Skills to Load`` heading, and a
    ``## Loaded skills`` heading, each optionally followed by a bullet list.
    """
    skills: List[str] = []
    m = re.search(r"(?im)^\s*Loaded skills:\s*(.+)$", text)
    if m:
        skills.extend(s.strip() for s in m.group(1).split(",") if s.strip())
    # Bullet list under a '## Skills to Load' or '## Loaded skills' heading
    block = re.search(
        r"(?is)##\s*(?:Skills to Load|Loaded skills)\b(.*?)(?:\n##\s|\Z)", text
    )
    if block:
        for line in block.group(1).splitlines():
            bm = re.match(r"\s*[-*]\s*`?([a-z0-9][a-z0-9_-]+)`?", line)
            if bm:
                skills.append(bm.group(1))
    # Dedupe preserving order
    seen = set()
    out = []
    for s in skills:
        key = s.lower()
        if key not in seen:
            seen.add(key)
            out.append(s)
    return out


def _handle_for_path(md: Path) -> str:
    """Return the id used to invoke the agent (its @handle / agent id).

    neqsim uses flat ``<handle>.agent.md`` files, so the handle is the stem
    minus the ``.agent`` suffix. Community/enterprise agents live in
    ``agents/<handle>/AGENT.md``, so the handle is the parent directory name.
    """
    if md.name.lower() == "agent.md":
        return md.parent.name
    return md.stem[:-6] if md.stem.lower().endswith(".agent") else md.stem


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
        # Include the handle in the haystack so a query using the @handle matches.
        haystack = f"{name} {handle} {md.parent.name} {desc}"
        out.append((name, haystack, str(md), skills, repo, handle))
    return out


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
    # 3) explicit extra roots (auto-detect layout: flat vs nested)
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


def _tokenize(s: str) -> List[str]:
    s = s.lower()
    return re.findall(r"[a-z0-9]+(?:-[a-z0-9]+)*", s)


def _try_sklearn_search(
    query: str, agents: List[AgentRecord], top: int
) -> List[Tuple[float, AgentRecord]]:
    from sklearn.feature_extraction.text import TfidfVectorizer  # type: ignore
    from sklearn.metrics.pairwise import cosine_similarity  # type: ignore

    corpus = [a[1] for a in agents]
    word_vec = TfidfVectorizer(
        analyzer="word", ngram_range=(1, 2), min_df=1, lowercase=True, sublinear_tf=True
    )
    char_vec = TfidfVectorizer(
        analyzer="char_wb", ngram_range=(3, 5), min_df=1, lowercase=True, sublinear_tf=True
    )
    Xw = word_vec.fit_transform(corpus + [query])
    Xc = char_vec.fit_transform(corpus + [query])
    sim_w = cosine_similarity(Xw[-1], Xw[:-1]).ravel()
    sim_c = cosine_similarity(Xc[-1], Xc[:-1]).ravel()
    sim = 0.6 * sim_w + 0.4 * sim_c
    order = sim.argsort()[::-1][:top]
    return [(float(sim[i]), agents[i]) for i in order]


def _fallback_search(
    query: str, agents: List[AgentRecord], top: int
) -> List[Tuple[float, AgentRecord]]:
    q_tokens = set(_tokenize(query))
    if not q_tokens:
        return []
    scored: List[Tuple[float, AgentRecord]] = []
    for rec in agents:
        h_tokens = set(_tokenize(rec[1]))
        if not h_tokens:
            continue
        intersection = len(q_tokens & h_tokens)
        if intersection == 0:
            scored.append((0.0, rec))
            continue
        union = len(q_tokens | h_tokens)
        jaccard = intersection / union
        boost = intersection / max(1, len(q_tokens))
        scored.append((0.5 * jaccard + 0.5 * boost, rec))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[:top]


def search(
    query: str, repo_root: Path, top: int = 5, extra: Optional[List[Path]] = None
) -> List[Tuple[float, AgentRecord]]:
    agents = _load_agents(repo_root, extra)
    if not agents:
        return []
    try:
        return _try_sklearn_search(query, agents, top)
    except ImportError:
        return _fallback_search(query, agents, top)


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
