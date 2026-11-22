"""
skill_search.py - Semantic skill retrieval for NeqSim agents.

Combines a small TF-IDF + cosine similarity search over the YAML front-matter
`description` fields of every SKILL.md under .github/skills/, the sibling *-skills
repos, installed skills and installed Agent Plugins (see discovery_registry.py),
with a lightweight boost from the curated .github/skills/skill-index.json keyword map.

Why TF-IDF and not sentence embeddings? Skill descriptions are short and
keyword-dense. TF-IDF with character n-grams gives ~80% of the recall of
a sentence-transformer model with zero heavy dependencies (only scikit-learn
which is already in dev requirements). The build is instant (< 1 s for
~40 skills) so we don't even cache an index — we rebuild on every call.

Usage:
    python devtools/skill_search.py "wax inhibitor injection in subsea tieback"
    python devtools/skill_search.py "CO2 phase behavior with H2 impurity" --top 5
    python devtools/skill_search.py --json "compressor surge anti-surge control"

Returns ranked list of skills by relevance with combined relevance score.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import bm25  # noqa: E402
import discovery_registry as registry  # noqa: E402


def _parse_skill_md(skill_md: Path) -> Tuple[str, str, str]:
    """Return (name, haystack, path) for one SKILL.md, or ("", "", "") to skip."""
    parsed = registry.parse_skill_md(skill_md)
    if not parsed:
        return "", "", ""
    return parsed["name"], parsed["haystack"], str(skill_md)


def _sibling_skill_roots(skills_root: Path) -> List[Path]:
    """Sibling skill-repo roots (community + enterprise) if checked out."""
    return [root for root, _, _, _ in registry.sibling_skill_roots(skills_root)]


def _load_skill_entries(skills_root: Path) -> List[Tuple[registry.Entry, str]]:
    """Skills from the neqsim tree, sibling repos, installed copies and plugins.

    One registry (``discovery_registry``) supplies the locations, the same one
    ``agent_search`` uses, so a skill an agent declares is findable wherever
    that agent was installed from. The first copy of a name wins.
    """
    return registry.skill_entries(skills_root)


def _load_skills(skills_root: Path) -> List[Tuple[str, str, str]]:
    """Return list of (skill_name, haystack, path) tuples."""
    return [(e.id, hay, e.location) for e, hay in _load_skill_entries(skills_root)]



def _strip_yaml_value(s: str) -> str:
    s = s.strip()
    if s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    elif s.startswith("'") and s.endswith("'"):
        s = s[1:-1]
    return s


def _try_sklearn_search(
    query: str, skills: List[Tuple[str, str, str]], top: int
) -> List[Tuple[float, str, str]]:
    """Rank skills with dependency-free BM25 so dev and CI score identically.

    Name kept for callers/tests; the scikit-learn path was optional and absent in
    both the shared interpreter and CI, so the fallback was what actually ran.
    """
    scores = bm25.BM25([s[1] for s in skills]).scores(query)
    order = sorted(range(len(skills)), key=lambda i: scores[i], reverse=True)
    return [(scores[i], skills[i][0], skills[i][2]) for i in order[:top]]



def _keyword_index_boosts(query: str, skills_root: Path) -> dict:
    """Return score boosts from skill-index.json keyword entries."""
    index_path = skills_root / "skill-index.json"
    if not index_path.is_file():
        return {}
    try:
        data = json.loads(index_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}

    query_lower = query.lower()
    query_tokens = set(bm25.tokenize(query))
    boosts = {}
    for key, skill_names in data.items():
        if not isinstance(key, str) or not isinstance(skill_names, list):
            continue
        key_tokens = set(bm25.tokenize(key))
        if not key_tokens:
            continue
        # Stemmed overlap so "predicting hydrate formation" fires on "hydrate
        # formation temperature"; a lone shared token is not enough.
        overlap = len(key_tokens & query_tokens)
        if key.lower() in query_lower:
            strength = 1.0
        elif overlap >= 2 and overlap / len(key_tokens) >= 0.5:
            strength = overlap / len(key_tokens)
        elif len(key_tokens) == 1 and overlap == 1:
            strength = 1.0
        else:
            continue
        for position, skill_name in enumerate(skill_names):
            if not isinstance(skill_name, str):
                continue
            # Earlier entries in a curated keyword row are intentionally stronger.
            boost = strength * 0.35 / float(position + 1)
            boosts[skill_name] = min(0.70, boosts.get(skill_name, 0.0) + boost)
    return boosts


def search(query: str, skills_root: Path, top: int = 5) -> List[Tuple[float, str, str]]:
    skills = _load_skills(skills_root)
    if not skills:
        return []
    results = _try_sklearn_search(query, skills, len(skills))

    boosts = _keyword_index_boosts(query, skills_root)
    if boosts:
        results = [
            (score + boosts.get(name, 0.0), name, path)
            for score, name, path in results
        ]
        results.sort(key=lambda item: item[0], reverse=True)
    return results[:top]


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    skills_root = repo_root / ".github" / "skills"

    parser = argparse.ArgumentParser(
        description="Semantic skill search over .github/skills/"
    )
    parser.add_argument("query", help="Natural-language description of the task")
    parser.add_argument(
        "--top", type=int, default=5, help="Number of skills to return (default 5)"
    )
    parser.add_argument(
        "--json", action="store_true", help="Emit machine-readable JSON output"
    )
    parser.add_argument(
        "--skills-root",
        default=str(skills_root),
        help="Path to .github/skills/ (default auto-detect)",
    )
    args = parser.parse_args()

    results = search(args.query, Path(args.skills_root), args.top)

    if args.json:
        by_path = {e.location: e for e, _ in _load_skill_entries(Path(args.skills_root))}
        payload = {
            "query": args.query,
            "results": [
                {"score": round(s, 4), "name": n, "path": p,
                 "source": by_path[p].source if p in by_path else "",
                 "version": by_path[p].version if p in by_path else ""}
                for s, n, p in results
            ],
        }
        print(json.dumps(payload, indent=2))
        return 0

    if not results:
        print("No skills found.")
        return 0

    print(f"Top {len(results)} skills for: {args.query!r}\n")
    # neqsim skills render relative to the neqsim root (unchanged); sibling-repo
    # skills fall back to workspace-relative so the display never crashes.
    neqsim_root = Path(args.skills_root).parent.parent
    workspace_root = neqsim_root.parent
    for score, name, path in results:
        try:
            rel = Path(path).relative_to(neqsim_root)
        except ValueError:
            try:
                rel = Path(path).relative_to(workspace_root)
            except ValueError:
                rel = Path(path)
        print(f"  {score:0.3f}  {name:<48}  {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
