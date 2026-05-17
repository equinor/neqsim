"""
skill_search.py - Semantic skill retrieval for NeqSim agents.

Replaces the keyword-grep over .github/skills/skill-index.json with a small
TF-IDF + cosine similarity search over the YAML front-matter `description`
fields of every SKILL.md under .github/skills/.

Why TF-IDF and not sentence embeddings? Skill descriptions are short and
keyword-dense. TF-IDF with character n-grams gives ~80% of the recall of
a sentence-transformer model with zero heavy dependencies (only scikit-learn
which is already in dev requirements). The build is instant (< 1 s for
~40 skills) so we don't even cache an index — we rebuild on every call.

Usage:
    python devtools/skill_search.py "wax inhibitor injection in subsea tieback"
    python devtools/skill_search.py "CO2 phase behavior with H2 impurity" --top 5
    python devtools/skill_search.py --json "compressor surge anti-surge control"

Returns ranked list of skills by relevance with cosine score.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import List, Tuple


def _load_skills(skills_root: Path) -> List[Tuple[str, str, str]]:
    """Return list of (skill_name, description, path) tuples."""
    out: List[Tuple[str, str, str]] = []
    for skill_dir in sorted(skills_root.iterdir()):
        if not skill_dir.is_dir():
            continue
        skill_md = skill_dir / "SKILL.md"
        if not skill_md.is_file():
            continue
        try:
            text = skill_md.read_text(encoding="utf-8")
        except OSError:
            continue
        # Parse YAML front matter — simple, no PyYAML dependency
        if not text.startswith("---"):
            continue
        end = text.find("\n---", 3)
        if end < 0:
            continue
        front = text[3:end]
        name = ""
        desc = ""
        for line in front.splitlines():
            line = line.rstrip()
            if line.startswith("name:"):
                name = _strip_yaml_value(line[5:])
            elif line.startswith("description:"):
                desc = _strip_yaml_value(line[12:])
        if not name:
            name = skill_dir.name
        # Append the skill folder name to the haystack — improves matching
        haystack = f"{name} {skill_dir.name} {desc}"
        out.append((name, haystack, str(skill_md)))
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


def search(query: str, skills_root: Path, top: int = 5) -> List[Tuple[float, str, str]]:
    skills = _load_skills(skills_root)
    if not skills:
        return []
    try:
        return _try_sklearn_search(query, skills, top)
    except ImportError:
        return _fallback_search(query, skills, top)


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
    for score, name, path in results:
        rel = Path(path).relative_to(Path(args.skills_root).parent.parent)
        print(f"  {score:0.3f}  {name:<48}  {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
