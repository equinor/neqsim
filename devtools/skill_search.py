"""
skill_search.py - Semantic skill retrieval for NeqSim agents.

Combines a small TF-IDF + cosine similarity search over the YAML front-matter
`description` fields of every SKILL.md under .github/skills/ with a lightweight
boost from the curated .github/skills/skill-index.json keyword map.

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
import re
import sys
from pathlib import Path
from typing import List, Tuple


def _parse_skill_md(skill_md: Path) -> Tuple[str, str, str]:
    """Return (name, haystack, path) for one SKILL.md, or ("", "", "") to skip."""
    try:
        text = skill_md.read_text(encoding="utf-8")
    except OSError:
        return "", "", ""
    # Parse YAML front matter — simple, no PyYAML dependency
    if not text.startswith("---"):
        return "", "", ""
    end = text.find("\n---", 3)
    if end < 0:
        return "", "", ""
    front = text[3:end]
    name = ""
    desc = ""
    for line in front.splitlines():
        line = line.rstrip()
        if line.startswith("name:"):
            name = _strip_yaml_value(line[5:])
        elif line.startswith("description:"):
            desc = _strip_yaml_value(line[12:])
    skill_dir = skill_md.parent
    if not name:
        name = skill_dir.name
    # Append the skill folder name to the haystack — improves matching
    haystack = f"{name} {skill_dir.name} {desc}"
    return name, haystack, str(skill_md)


def _sibling_skill_roots(skills_root: Path) -> List[Path]:
    """Return sibling skill-repo roots (community + enterprise) if checked out.

    Keeps skill_search symmetric with agent_search: agents may declare skills
    that live only in the sibling *-skills repos (e.g. enterprise-pepr-actions,
    enterprise-rigga-production). Without this, an agent could recommend a skill
    the skill retriever cannot surface. The neqsim ``.github/skills`` tree stays
    the primary source and wins on name collisions.
    """
    # skills_root is typically <neqsim>/.github/skills → workspace root is 3 up.
    workspace_root = skills_root.parent.parent.parent
    out: List[Path] = []
    for sibling in ("neqsim-community-skills", "neqsim-enterprise-skills"):
        cand = workspace_root / sibling / "skills"
        if cand.is_dir():
            out.append(cand)
    return out


def _load_skills(skills_root: Path) -> List[Tuple[str, str, str]]:
    """Return list of (skill_name, description, path) tuples.

    Loads the flat ``skills_root`` (neqsim ``.github/skills``) first, then any
    sibling *-skills repos recursively (their layout is nested by category:
    ``skills/<category>/<skill>/SKILL.md``). Dedup is by skill folder name so the
    primary neqsim copy wins and sibling-only skills are still added.
    """
    out: List[Tuple[str, str, str]] = []
    seen = set()
    # Primary: flat neqsim .github/skills
    for skill_dir in sorted(skills_root.iterdir()) if skills_root.is_dir() else []:
        if not skill_dir.is_dir():
            continue
        skill_md = skill_dir / "SKILL.md"
        if not skill_md.is_file():
            continue
        name, haystack, path = _parse_skill_md(skill_md)
        if not path:
            continue
        key = name.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append((name, haystack, path))
    # Secondary: sibling *-skills repos (nested layout). Dedup by front-matter
    # name (NOT folder name) because the neqsim mirror renames folders with a
    # neqsim-/enterprise- prefix while the sibling repo keeps them unprefixed.
    for sibling_root in _sibling_skill_roots(skills_root):
        for skill_md in sorted(sibling_root.glob("**/SKILL.md")):
            name, haystack, path = _parse_skill_md(skill_md)
            if not path:
                continue
            key = name.lower()
            if key in seen:
                continue
            seen.add(key)
            out.append((name, haystack, path))
    return out


def _strip_yaml_value(s: str) -> str:
    s = s.strip()
    if s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    elif s.startswith("'") and s.endswith("'"):
        s = s[1:-1]
    return s


def _tokenize(s: str) -> List[str]:
    """Lowercase + alphanumeric split. Keeps hyphenated tokens whole."""
    s = s.lower()
    return re.findall(r"[a-z0-9]+(?:-[a-z0-9]+)*", s)


def _try_sklearn_search(
    query: str, skills: List[Tuple[str, str, str]], top: int
) -> List[Tuple[float, str, str]]:
    """High-quality TF-IDF + char n-gram + cosine. Requires scikit-learn."""
    from sklearn.feature_extraction.text import TfidfVectorizer  # type: ignore
    from sklearn.metrics.pairwise import cosine_similarity  # type: ignore

    corpus = [s[1] for s in skills]
    # Combine word and character n-grams for robustness against typos / partial matches
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
    return [(float(sim[i]), skills[i][0], skills[i][2]) for i in order]


def _fallback_search(
    query: str, skills: List[Tuple[str, str, str]], top: int
) -> List[Tuple[float, str, str]]:
    """Pure-python fallback when sklearn is unavailable. Jaccard over tokens."""
    q_tokens = set(_tokenize(query))
    if not q_tokens:
        return []
    scored = []
    for name, hay, path in skills:
        h_tokens = set(_tokenize(hay))
        if not h_tokens:
            continue
        intersection = len(q_tokens & h_tokens)
        if intersection == 0:
            scored.append((0.0, name, path))
            continue
        union = len(q_tokens | h_tokens)
        jaccard = intersection / union
        # Boost by raw overlap so longer-query matches are not overly penalised
        boost = intersection / max(1, len(q_tokens))
        scored.append((0.5 * jaccard + 0.5 * boost, name, path))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[:top]


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
    query_tokens = set(_tokenize(query))
    boosts = {}
    for key, skill_names in data.items():
        if not isinstance(key, str) or not isinstance(skill_names, list):
            continue
        key_tokens = set(_tokenize(key))
        if not key_tokens:
            continue
        phrase_match = key.lower() in query_lower
        token_match = key_tokens.issubset(query_tokens)
        if not phrase_match and not token_match:
            continue
        for position, skill_name in enumerate(skill_names):
            if not isinstance(skill_name, str):
                continue
            # Earlier entries in a curated keyword row are intentionally stronger.
            boost = 0.35 / float(position + 1)
            boosts[skill_name] = min(0.70, boosts.get(skill_name, 0.0) + boost)
    return boosts


def search(query: str, skills_root: Path, top: int = 5) -> List[Tuple[float, str, str]]:
    skills = _load_skills(skills_root)
    if not skills:
        return []
    try:
        results = _try_sklearn_search(query, skills, len(skills))
    except ImportError:
        results = _fallback_search(query, skills, len(skills))

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
        payload = {
            "query": args.query,
            "results": [
                {"score": round(s, 4), "name": n, "path": p} for s, n, p in results
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
