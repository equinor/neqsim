"""Find current NeqSim source APIs and their agent/skill entry points.

This checkout-only discovery tool complements runtime MCP discovery and the
operation coverage registry. A source match or a routing assignment is never
evidence that an operation is executable, tested or engineering-qualified.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re

import agent_frontmatter
import bm25
from build_engineering_coverage import LEXICAL, java_types, read_source

ROOT = Path(__file__).resolve().parents[1]
ROUTES = Path("devtools/agent_capability_routes.json")
NOTICE = (
    "Source discovery and workflow routing only; inspect constructors, inherited "
    "methods, units, setup and limitations before use. Test references are not "
    "execution evidence. Use runtime MCP discovery for the deployed artifact."
)


def words(value: str) -> str:
    """Split Java identifiers while retaining exact names for API queries."""
    split = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", value)
    split = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", split)
    return value + " " + split


def route_for(identifier: str, routes: list[dict]) -> dict | None:
    """Select the most specific package route, respecting package boundaries."""
    candidates = [row for row in routes if identifier.startswith(row["package"] + ".")]
    return max(candidates, key=lambda row: len(row["package"])) if candidates else None


def load_routes(root: Path) -> list[dict]:
    """Reject broken local routes instead of presenting unusable instructions."""
    rows = json.loads((root / ROUTES).read_text(encoding="utf-8"))["routes"]
    seen = set()
    for row in rows:
        for key in ("package", "title", "keywords", "agent", "skill", "docs"):
            if not row.get(key):
                raise ValueError("Incomplete agent capability route: " + str(row))
        if row["package"] in seen:
            raise ValueError("Duplicate package route: " + row["package"])
        seen.add(row["package"])
        agent = root / ".github/agents" / (row["agent"] + ".agent.md")
        skill = root / ".github/skills" / row["skill"] / "SKILL.md"
        if not agent.is_file() or not skill.is_file():
            raise ValueError("Missing agent or skill: " + row["package"])
        required = agent_frontmatter.parse_frontmatter(agent.read_text(encoding="utf-8"))
        if row["skill"] not in required.get("required_skills", []):
            raise ValueError("Agent does not load route skill: " + row["package"])
        for doc in row["docs"]:
            if not (root / doc).is_file():
                raise ValueError("Missing route documentation: " + doc)
    return rows


def audit(root: Path) -> dict:
    """Report routing completeness separately from engineering qualification."""
    routes = load_routes(root)
    apis = java_types(root)
    missing = sorted(name for name in apis if route_for(name, routes) is None)
    empty = sorted(row["package"] for row in routes if not any(
        name.startswith(row["package"] + ".") for name in apis))
    return {
        "notice": NOTICE,
        "publicTypes": len(apis),
        "domains": dict(sorted(Counter(row["domain"] for row in apis.values()).items())),
        "routedTypes": len(apis) - len(missing),
        "unroutedTypes": missing,
        "emptyRoutes": empty,
        "routes": len(routes),
        "engineeringQualification": "not_assessed",
    }


def _test_references(root: Path, identifier: str, limit: int = 5) -> list[str]:
    """Return bounded test-source references, preferring the mirrored package.

    Match the exact FQN or a simple name in the same package/an exact import.
    Ignore comments and string literals, and do not equate a reference to an
    assertion, an executed test or an independently validated calculation.
    """
    return _test_reference_index(root, [identifier], limit)[identifier]


def _test_reference_index(root: Path, identifiers: list[str], limit: int = 5) -> dict:
    """Read each candidate test once for the bounded set of selected API types."""
    tests = root / "src/test/java/neqsim"
    matches = {identifier: [] for identifier in identifiers}
    if not identifiers:
        return matches
    patterns = {}
    mirrored_dirs = {}
    nearby = []
    for identifier in identifiers:
        package, name = identifier.rsplit(".", 1)
        mirrored = root / "src/test/java" / package.replace(".", "/")
        mirrored_dirs[identifier] = mirrored
        nearby.extend(sorted(mirrored.glob("*.java")))
        patterns[identifier] = (
            re.compile(r"\b" + re.escape(name) + r"\b"),
            re.compile(r"\b" + re.escape(identifier) + r"\b"),
            re.compile(r"\bimport\s+" + re.escape(package) + r"\.\*\s*;"),
            re.compile(r"\bpackage\s+" + re.escape(package) + r"\s*;"),
        )
    seen = set()
    for path in nearby + sorted(tests.rglob("*.java")):
        if path in seen:
            continue
        seen.add(path)
        source = LEXICAL.sub(" ", read_source(path))
        for identifier, (token, fqn, imported, same_package) in patterns.items():
            if len(matches[identifier]) >= limit and path.parent != mirrored_dirs[identifier]:
                continue
            if fqn.search(source) or ((imported.search(source) or same_package.search(source))
                                      and token.search(source)):
                matches[identifier].append(path.relative_to(root).as_posix())
                matches[identifier].sort(key=lambda ref: (
                    (root / ref).parent != mirrored_dirs[identifier], ref))
                del matches[identifier][limit:]
    return matches


def search(query: str, root: Path = ROOT, top: int = 5) -> dict:
    """Rank current types and workflow families with dependency-free BM25."""
    if not query.strip() or not 1 <= top <= 20:
        raise ValueError("Use a non-empty query and top between 1 and 20")
    routes = load_routes(root)
    apis = java_types(root)
    if not apis:
        raise ValueError("No NeqSim Java source checkout; use runtime MCP discovery")
    route_scores = bm25.BM25([" ".join((row["package"], row["title"], row["keywords"]))
                             for row in routes]).scores(words(query))
    ranked_routes = sorted(zip(route_scores, routes), key=lambda item: -item[0])
    api_list = list(apis.values())
    # Search declared public member names too; signatures still require source
    # inspection, especially inheritance, interfaces and overloads.
    documents = []
    for api in api_list:
        source = LEXICAL.sub(" ", read_source(root / api["source"]))
        members = re.findall(r"\bpublic\s+[^;{}=]*?\b(\w+)\s*\(", source)
        documents.append(words(api["id"] + " " + " ".join(members)))
    scores = bm25.BM25(documents).scores(words(query))
    query_id = query.strip().casefold()
    ranked = []
    for score, api in zip(scores, api_list):
        if query_id in (api["id"].casefold(), api["id"].rsplit(".", 1)[-1].casefold()):
            score += 2.0
        if score > 0:
            ranked.append((score, api))
    ranked.sort(key=lambda item: (-item[0], item[1]["id"]))
    references = _test_reference_index(root, [api["id"] for _score, api in ranked[:top]])
    registrations = json.loads((root / "devtools/engineering_capabilities.json").read_text(
        encoding="utf-8"))["capabilities"]
    results = []
    for score, api in ranked[:top]:
        route = route_for(api["id"], routes)
        results.append({
            "id": api["id"], "source": api["source"], "kind": api["kind"],
            "score": round(score, 4), "route": route,
            "testSourceReferences": references[api["id"]],
            "declaredRegistrations": [{"id": row["id"], "tool": row["tool"],
                                       "limitations": row["limitations"]}
                                      for row in registrations if api["id"] in row["apis"]],
            "execution": "not_recorded", "engineeringQualification": "not_assessed",
        })
    return {"query": query, "notice": NOTICE, "apis": results,
            "workflowRoutes": [dict(row, score=round(score, 4))
                               for score, row in ranked_routes[:top] if score > 0]}


def main(argv: list[str] | None = None) -> int:
    """Print bounded discovery results or fail on incomplete routing coverage."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("query", nargs="?")
    parser.add_argument("--top", type=int, default=5, choices=range(1, 21))
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--audit", action="store_true")
    args = parser.parse_args(argv)
    if not args.audit and not args.query:
        parser.error("provide a query or --audit")
    try:
        data = audit(ROOT) if args.audit else search(args.query, ROOT, args.top)
    except (OSError, ValueError, KeyError) as exc:
        parser.error(str(exc))
    if args.json:
        print(json.dumps(data, indent=2))
    elif args.audit:
        print("Routing: {routedTypes}/{publicTypes} public source types; {routes} routes".format(**data))
        print(NOTICE)
        for missing in data["unroutedTypes"] + data["emptyRoutes"]:
            print("REVIEW " + missing)
    else:
        print(NOTICE)
        for api in data["apis"]:
            print("\n" + api["id"] + " -> " + api["source"])
            route = api["route"]
            if route:
                print("  @" + route["agent"] + " / " + route["skill"])
                print("  Guides: " + ", ".join(route["docs"]))
            else:
                print("  No route assigned; ask capability-scout to review")
            print("  Test references: " + (", ".join(api["testSourceReferences"]) or "none found"))
        if data["workflowRoutes"]:
            print("\nWorkflow matches (may differ from API-name matches):")
            for row in data["workflowRoutes"]:
                print("  " + row["title"] + " -> @" + row["agent"] + " / " + row["skill"])
    return int(args.audit and bool(data["unroutedTypes"] or data["emptyRoutes"]
                                  or not data["publicTypes"]))


if __name__ == "__main__":
    raise SystemExit(main())
