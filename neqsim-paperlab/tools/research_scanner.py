"""
Research Scanner — Find scientific paper opportunities in the NeqSim codebase.

Scans the NeqSim source tree, git history, test coverage, and published
literature to identify novel, unpublished work suitable for scientific papers.

Produces ``opportunities.json`` with ranked paper topics, each classified by
paper_type (comparative / characterization / method / application), estimated
novelty, effort, readiness, and suggested target journals.

Usage::

    from tools.research_scanner import scan_opportunities

    report = scan_opportunities("/path/to/neqsim_repo")
    for opp in report["opportunities"]:
        print(f"  [{opp['paper_type']}] {opp['title']}  (score: {opp['score']})")

CLI::

    python paperflow.py scan                  # scan entire repo
    python paperflow.py scan --since 90       # only changes from last 90 days
    python paperflow.py scan --top 5          # show top 5 opportunities
    python paperflow.py scan --output opp.json
"""

import json
import os
import re
import subprocess
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False

# ── Semantic Scholar API ──────────────────────────────────────────────
_S2_SEARCH_URL = "https://api.semanticscholar.org/graph/v1/paper/search"
_S2_FIELDS = "title,authors,year,citationCount,url"

# ── NeqSim package → research domain mapping ─────────────────────────
_DOMAIN_MAP = {
    "thermo": "Thermodynamic Models",
    "process/equipment/distillation": "Distillation & Absorption",
    "process/equipment/pipeline": "Multiphase Pipeline Flow",
    "process/equipment/compressor": "Compression & Expansion",
    "process/equipment/heatexchanger": "Heat Transfer",
    "process/equipment/separator": "Separation",
    "process/equipment/valve": "Valve & Throttling",
    "process/equipment/subsea": "Subsea Systems",
    "process/mechanicaldesign": "Mechanical Design",
    "process/processmodel": "Process Simulation Framework",
    "process/measurementdevice": "Instrumentation & Control",
    "pvtsimulation": "PVT & Reservoir Fluids",
    "standards": "Industry Standards",
    "chemicalreactions": "Chemical Reaction Equilibria",
    "process/fielddevelopment": "Field Development",
    "fluidmechanics": "Fluid Mechanics",
    "statistics": "Statistical Methods",
}

# ── Journal suggestions by domain ────────────────────────────────────
_JOURNAL_MAP = {
    "Thermodynamic Models": [
        "fluid_phase_equilibria", "computers_chem_eng", "iecr",
    ],
    "Distillation & Absorption": [
        "chem_eng_sci", "computers_chem_eng",
    ],
    "Multiphase Pipeline Flow": [
        "computers_chem_eng", "chem_eng_sci",
    ],
    "Compression & Expansion": [
        "computers_chem_eng",
    ],
    "Heat Transfer": [
        "computers_chem_eng", "chem_eng_sci",
    ],
    "Separation": [
        "chem_eng_sci", "computers_chem_eng",
    ],
    "Subsea Systems": [
        "computers_chem_eng",
    ],
    "Mechanical Design": [
        "computers_chem_eng",
    ],
    "PVT & Reservoir Fluids": [
        "fluid_phase_equilibria",
    ],
    "Chemical Reaction Equilibria": [
        "computers_chem_eng", "aiche",
    ],
    "Industry Standards": [
        "computers_chem_eng",
    ],
    "Field Development": [
        "computers_chem_eng",
    ],
}

# ── Paper-type heuristic keywords ────────────────────────────────────
_METHOD_KEYWORDS = [
    "solver", "algorithm", "convergence", "jacobian", "newton",
    "numerical", "scheme", "discretization", "adaptive", "iteration",
    "minimization", "optimization", "decomposition",
]
_COMPARATIVE_KEYWORDS = [
    "benchmark", "comparison", "evaluate", "versus", "accuracy",
    "validation", "reference", "deviation",
]
_APPLICATION_KEYWORDS = [
    "design", "cost", "feasibility", "report", "pipeline", "well",
    "subsea", "field", "production", "facility",
]


def _classify_domain(file_path):
    """Map a Java source file path to a research domain."""
    rel = file_path.replace("\\", "/")
    for prefix, domain in sorted(_DOMAIN_MAP.items(), key=lambda x: -len(x[0])):
        if prefix in rel:
            return domain
    return "General"


def _classify_paper_type(class_name, changed_lines):
    """Heuristic paper-type classification from code content."""
    text = (class_name + " " + " ".join(changed_lines)).lower()
    method_score = sum(1 for kw in _METHOD_KEYWORDS if kw in text)
    comp_score = sum(1 for kw in _COMPARATIVE_KEYWORDS if kw in text)
    app_score = sum(1 for kw in _APPLICATION_KEYWORDS if kw in text)

    scores = {
        "method": method_score,
        "comparative": comp_score,
        "application": app_score,
        "characterization": 1,  # default fallback
    }
    return max(scores, key=scores.get)


def _parse_git_log(repo_root, since_days=180):
    """Parse git log for recent Java source changes.

    Returns list of dicts with author, date, files, message, insertions.
    """
    since_date = (datetime.now() - timedelta(days=since_days)).strftime("%Y-%m-%d")
    cmd = [
        "git", "log",
        "--since", since_date,
        "--pretty=format:%H|%an|%aI|%s",
        "--numstat",
        "--diff-filter=AMR",
        "--", "src/main/java/neqsim/",
    ]
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, cwd=str(repo_root),
            timeout=30,
        )
        if result.returncode != 0:
            return []
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return []

    commits = []
    current = None
    for line in result.stdout.splitlines():
        if "|" in line and line.count("|") >= 3:
            parts = line.split("|", 3)
            if len(parts) == 4:
                current = {
                    "hash": parts[0],
                    "author": parts[1],
                    "date": parts[2][:10],
                    "message": parts[3],
                    "files": [],
                    "insertions": 0,
                }
                commits.append(current)
        elif current is not None and line.strip():
            parts = line.split("\t")
            if len(parts) == 3:
                try:
                    ins = int(parts[0]) if parts[0] != "-" else 0
                except ValueError:
                    ins = 0
                filepath = parts[2]
                if filepath.endswith(".java"):
                    current["files"].append(filepath)
                    current["insertions"] += ins

    return commits


def _find_java_classes(repo_root, subdir="src/main/java/neqsim"):
    """List all Java source files under the given subdir."""
    root = Path(repo_root) / subdir
    if not root.exists():
        return []
    return sorted(str(p) for p in root.rglob("*.java"))


def _find_test_files(repo_root):
    """Return set of tested class names (from test file naming convention)."""
    test_root = Path(repo_root) / "src" / "test" / "java" / "neqsim"
    tested = set()
    if test_root.exists():
        for p in test_root.rglob("*Test.java"):
            # SeparatorTest.java → Separator
            name = p.stem
            if name.endswith("Test"):
                tested.add(name[:-4])
    return tested


def _count_class_lines(filepath):
    """Count non-blank, non-comment lines in a Java file."""
    try:
        text = Path(filepath).read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return 0
    count = 0
    in_block_comment = False
    for line in text.splitlines():
        stripped = line.strip()
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped.startswith("/*"):
            in_block_comment = True
            continue
        if stripped and not stripped.startswith("//") and not stripped.startswith("*"):
            count += 1
    return count


def _extract_class_summary(filepath):
    """Extract the public class name and public method count from a Java file."""
    try:
        text = Path(filepath).read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return None, 0

    class_match = re.search(r'public\s+(?:abstract\s+)?class\s+(\w+)', text)
    class_name = class_match.group(1) if class_match else Path(filepath).stem
    method_count = len(re.findall(r'public\s+\w+[\s<]', text))
    return class_name, method_count


def _search_literature(query, limit=5):
    """Search Semantic Scholar for papers matching a query."""
    if not _HAS_REQUESTS:
        return []
    import time as _time
    params = {
        "query": query[:200],
        "limit": limit,
        "fields": _S2_FIELDS,
    }
    try:
        resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)
        if resp.status_code == 429:
            _time.sleep(3)
            resp = requests.get(_S2_SEARCH_URL, params=params, timeout=15)
        if resp.status_code != 200:
            return []
        data = resp.json()
        return data.get("data", [])
    except (requests.RequestException, json.JSONDecodeError):
        return []


def _compute_novelty_score(class_name, domain, line_count, has_test,
                           recent_commits, literature_hits):
    """Score an opportunity 0–100 based on multiple signals."""
    score = 0.0

    # Size / complexity (larger implementations = more paper material)
    if line_count > 500:
        score += 20
    elif line_count > 200:
        score += 12
    elif line_count > 100:
        score += 6

    # Test coverage (tested = validated = more publishable)
    if has_test:
        score += 15

    # Recent activity (recently changed = actively developed)
    if recent_commits >= 5:
        score += 20
    elif recent_commits >= 2:
        score += 12
    elif recent_commits >= 1:
        score += 6

    # Literature gap (fewer hits = more novel)
    if literature_hits == 0:
        score += 25
    elif literature_hits <= 2:
        score += 15
    elif literature_hits <= 5:
        score += 8

    # Domain bonus (some domains are inherently more publishable)
    high_impact_domains = {
        "Chemical Reaction Equilibria", "Multiphase Pipeline Flow",
        "Thermodynamic Models", "PVT & Reservoir Fluids",
    }
    if domain in high_impact_domains:
        score += 10

    return min(100, score)


def _estimate_effort(line_count, has_test, recent_commits):
    """Estimate effort level: low / medium / high / very_high."""
    if has_test and line_count < 300 and recent_commits >= 2:
        return "low"
    if has_test and line_count < 800:
        return "medium"
    if line_count < 1500:
        return "high"
    return "very_high"


def _effort_weeks(effort):
    """Map effort label to estimated weeks."""
    return {"low": 4, "medium": 8, "high": 12, "very_high": 16}.get(effort, 8)


def _infer_code_improvement(cls_info, paper_type):
    """Infer what NeqSim code improvement this paper would drive.

    Every paper in PaperLab must improve the NeqSim codebase. This function
    describes what kind of improvement the paper process would produce.
    """
    improvements = []
    if not cls_info["has_test"]:
        improvements.append("Add test coverage for " + cls_info["class_name"])
    if cls_info["recent_commits"] > 0:
        improvements.append("Validate and harden recent changes")
    if paper_type == "method":
        improvements.append("Improve solver/algorithm implementation")
    elif paper_type == "comparative":
        improvements.append("Benchmark against reference implementations")
    elif paper_type == "characterization":
        improvements.append("Systematic validation across operating conditions")
    elif paper_type == "application":
        improvements.append("Demonstrate and document engineering workflow")

    if cls_info["line_count"] > 500 and not cls_info["has_test"]:
        improvements.append("Large untested implementation needs quality assurance")

    domain = cls_info["domain"]
    if domain == "Thermodynamic Models":
        improvements.append("Calibrate property predictions against experimental data")
    elif domain in ("Multiphase Pipeline Flow", "Distillation & Absorption"):
        improvements.append("Validate process model against plant/pilot data")
    elif domain == "Mechanical Design":
        improvements.append("Verify design calculations against standards")

    return improvements


# ── Main scanner ─────────────────────────────────────────────────────


def scan_opportunities(repo_root, since_days=180, top_n=15,
                       check_literature=False):
    """Scan the NeqSim repo for scientific paper opportunities.

    Parameters
    ----------
    repo_root : str or Path
        Root of the NeqSim repository.
    since_days : int
        Look back window for git history (default: 180 days).
    top_n : int
        Maximum number of opportunities to return.
    check_literature : bool
        If True, query Semantic Scholar to assess novelty (requires
        network access; rate-limited to ~4 queries).

    Returns
    -------
    dict
        Report with 'opportunities' list, 'summary', and 'metadata'.
    """
    repo_root = Path(repo_root)

    # 1. Parse git log for recent changes
    commits = _parse_git_log(repo_root, since_days=since_days)

    # Aggregate: file → commit count, total insertions, commit messages
    file_stats = defaultdict(lambda: {
        "commits": 0, "insertions": 0, "messages": [], "authors": set(),
        "dates": [],
    })
    for commit in commits:
        for f in commit["files"]:
            file_stats[f]["commits"] += 1
            file_stats[f]["insertions"] += commit["insertions"]
            file_stats[f]["messages"].append(commit["message"])
            file_stats[f]["authors"].add(commit["author"])
            file_stats[f]["dates"].append(commit["date"])

    # 2. Find all Java source classes
    all_classes = _find_java_classes(repo_root)
    tested_classes = _find_test_files(repo_root)

    # 3. Group files by domain and identify clusters
    domain_clusters = defaultdict(list)  # domain → [class_info, ...]

    for filepath in all_classes:
        rel_path = os.path.relpath(filepath, repo_root).replace("\\", "/")
        domain = _classify_domain(rel_path)
        class_name, method_count = _extract_class_summary(filepath)
        if class_name is None:
            continue

        line_count = _count_class_lines(filepath)
        stats = file_stats.get(rel_path, {
            "commits": 0, "insertions": 0, "messages": [], "authors": set(),
            "dates": [],
        })

        has_test = class_name in tested_classes

        info = {
            "class_name": class_name,
            "path": rel_path,
            "domain": domain,
            "line_count": line_count,
            "method_count": method_count,
            "has_test": has_test,
            "recent_commits": stats["commits"],
            "recent_insertions": stats["insertions"],
            "commit_messages": stats["messages"][:5],
            "authors": list(stats["authors"]) if isinstance(stats["authors"], set) else [],
            "last_changed": max(stats["dates"]) if stats["dates"] else None,
        }
        domain_clusters[domain].append(info)

    # 4. Score and rank — focus on classes with recent activity OR large implementations
    candidates = []
    for domain, classes in domain_clusters.items():
        # Sort within domain by recent activity then size
        ranked = sorted(
            classes,
            key=lambda c: (c["recent_commits"], c["line_count"]),
            reverse=True,
        )
        # Take top classes per domain (avoid over-representing one area)
        for cls in ranked[:5]:
            if cls["line_count"] < 30:
                continue  # Skip trivial files
            candidates.append(cls)

    # 5. Optional literature check
    literature_cache = {}
    if check_literature and _HAS_REQUESTS:
        import time as _time
        # Pick top candidates by commit activity for literature check
        top_for_lit = sorted(
            candidates,
            key=lambda c: c["recent_commits"],
            reverse=True,
        )[:4]
        for cls in top_for_lit:
            query = f"NeqSim {cls['domain']} {cls['class_name']}"
            hits = _search_literature(query, limit=5)
            literature_cache[cls["class_name"]] = len(hits)
            _time.sleep(1.1)

    # 6. Compute final scores
    opportunities = []
    for cls in candidates:
        lit_hits = literature_cache.get(cls["class_name"], -1)
        if lit_hits < 0:
            lit_hits = 3  # Assume moderate when not checked

        score = _compute_novelty_score(
            cls["class_name"], cls["domain"], cls["line_count"],
            cls["has_test"], cls["recent_commits"], lit_hits,
        )

        paper_type = _classify_paper_type(
            cls["class_name"], cls["commit_messages"],
        )

        effort = _estimate_effort(
            cls["line_count"], cls["has_test"], cls["recent_commits"],
        )

        journals = _JOURNAL_MAP.get(cls["domain"], ["computers_chem_eng"])

        title = _generate_title(cls["class_name"], cls["domain"], paper_type)

        code_improvements = _infer_code_improvement(cls, paper_type)

        opp = {
            "title": title,
            "class_name": cls["class_name"],
            "domain": cls["domain"],
            "paper_type": paper_type,
            "score": round(score),
            "readiness": "ready" if cls["has_test"] and cls["recent_commits"] >= 2 else (
                "needs_validation" if not cls["has_test"] else "needs_development"
            ),
            "effort": effort,
            "effort_weeks": _effort_weeks(effort),
            "suggested_journals": journals,
            "neqsim_improvement": code_improvements,
            "evidence": {
                "line_count": cls["line_count"],
                "method_count": cls["method_count"],
                "has_test": cls["has_test"],
                "recent_commits": cls["recent_commits"],
                "recent_insertions": cls["recent_insertions"],
                "last_changed": cls["last_changed"],
                "authors": cls["authors"],
            },
            "source_path": cls["path"],
            "commit_highlights": cls["commit_messages"][:3],
        }
        if lit_hits >= 0 and check_literature:
            opp["literature_hits"] = lit_hits

        opportunities.append(opp)

    # Sort by score descending
    opportunities.sort(key=lambda o: o["score"], reverse=True)
    opportunities = opportunities[:top_n]

    # 7. Summary statistics
    domain_counts = Counter(o["domain"] for o in opportunities)
    type_counts = Counter(o["paper_type"] for o in opportunities)

    report = {
        "metadata": {
            "scan_date": datetime.now().isoformat()[:10],
            "repo_root": str(repo_root),
            "since_days": since_days,
            "total_classes_scanned": len(all_classes),
            "classes_with_recent_changes": sum(
                1 for c in candidates if c["recent_commits"] > 0
            ),
            "tested_classes": len(tested_classes),
            "literature_checked": check_literature,
        },
        "summary": {
            "total_opportunities": len(opportunities),
            "by_domain": dict(domain_counts.most_common()),
            "by_paper_type": dict(type_counts.most_common()),
            "top_score": opportunities[0]["score"] if opportunities else 0,
            "ready_count": sum(
                1 for o in opportunities if o["readiness"] == "ready"
            ),
        },
        "opportunities": opportunities,
    }

    return report


def _generate_title(class_name, domain, paper_type):
    """Generate a working paper title from class metadata."""
    # Convert CamelCase to words
    words = re.sub(r'(?<=[a-z])(?=[A-Z])', ' ', class_name)

    templates = {
        "method": f"A Novel {words} Approach for {domain}",
        "comparative": f"Comparative Evaluation of {words} Methods in {domain}",
        "characterization": f"Systematic Characterization of {words} for {domain}",
        "application": f"Application of {words} to {domain} Problems",
    }
    return templates.get(paper_type, f"{words}: A Study in {domain}")


def print_scan_report(report, verbose=False):
    """Pretty-print a scan report to stdout."""
    meta = report["metadata"]
    summary = report["summary"]
    opps = report["opportunities"]

    print("=" * 72)
    print("  NeqSim Research Opportunity Scanner")
    print("=" * 72)
    print(f"  Scan date:    {meta['scan_date']}")
    print(f"  Classes scanned:  {meta['total_classes_scanned']}")
    print(f"  Recent changes:   {meta['classes_with_recent_changes']}")
    print(f"  Tested classes:   {meta['tested_classes']}")
    print(f"  Literature check: {'Yes' if meta['literature_checked'] else 'No'}")
    print()
    print(f"  Found {summary['total_opportunities']} opportunities "
          f"({summary['ready_count']} ready to write)")
    print()

    if not opps:
        print("  No opportunities found.")
        return

    # Ranked list
    print("  Rank | Score | Type            | Readiness        | Title")
    print("  " + "-" * 68)
    for i, opp in enumerate(opps, 1):
        t = opp["paper_type"][:14].ljust(14)
        r = opp["readiness"][:16].ljust(16)
        title = opp["title"]
        if len(title) > 42:
            title = title[:39] + "..."
        print(f"  {i:4d} | {opp['score']:5d} | {t} | {r} | {title}")

    print()

    if verbose:
        for i, opp in enumerate(opps, 1):
            print(f"\n  --- #{i}: {opp['title']} ---")
            print(f"  Class:    {opp['class_name']}")
            print(f"  Path:     {opp['source_path']}")
            print(f"  Domain:   {opp['domain']}")
            print(f"  Type:     {opp['paper_type']}")
            print(f"  Score:    {opp['score']}/100")
            print(f"  Effort:   {opp['effort']} (~{opp['effort_weeks']} weeks)")
            print(f"  Readiness:{opp['readiness']}")
            print(f"  Journals: {', '.join(opp['suggested_journals'])}")
            ev = opp["evidence"]
            print(f"  Lines:    {ev['line_count']}, Methods: {ev['method_count']}")
            print(f"  Tested:   {'Yes' if ev['has_test'] else 'No'}")
            print(f"  Commits:  {ev['recent_commits']} (+{ev['recent_insertions']} lines)")
            if opp.get("neqsim_improvement"):
                print(f"  NeqSim:   {'; '.join(opp['neqsim_improvement'][:3])}")
            if opp.get("commit_highlights"):
                print(f"  Recent:   {opp['commit_highlights'][0]}")

    # Domain breakdown
    print("\n  Domain Breakdown:")
    for domain, count in summary["by_domain"].items():
        print(f"    {domain}: {count}")

    print("\n  Paper Type Breakdown:")
    for ptype, count in summary["by_paper_type"].items():
        print(f"    {ptype}: {count}")

    print()


def generate_markdown_report(report):
    """Generate a markdown-formatted scout report from scan results.

    Returns a string suitable for a PR body or standalone ``scout_report.md``.
    """
    meta = report["metadata"]
    summary = report["summary"]
    opps = report["opportunities"]

    lines = []
    lines.append("# NeqSim Research Opportunity Report")
    lines.append("")
    lines.append(f"**Scan date:** {meta['scan_date']}  ")
    lines.append(f"**Classes scanned:** {meta['total_classes_scanned']}  ")
    lines.append(f"**With recent changes:** {meta['classes_with_recent_changes']}  ")
    lines.append(f"**Test coverage:** {meta['tested_classes']} tested classes  ")
    lines.append(f"**Literature check:** {'Yes' if meta['literature_checked'] else 'No'}")
    lines.append("")
    lines.append(f"> Found **{summary['total_opportunities']}** paper opportunities "
                 f"({summary['ready_count']} ready to write).")
    lines.append("")

    if not opps:
        lines.append("No opportunities found in this scan window.")
        return "\n".join(lines)

    # Summary table
    lines.append("## Top Opportunities")
    lines.append("")
    lines.append("| # | Score | Type | Readiness | Title |")
    lines.append("|---|-------|------|-----------|-------|")
    for i, opp in enumerate(opps, 1):
        title = opp["title"]
        if len(title) > 60:
            title = title[:57] + "..."
        lines.append(
            f"| {i} | {opp['score']} | {opp['paper_type']} "
            f"| {opp['readiness']} | {title} |"
        )
    lines.append("")

    # Detailed cards
    lines.append("## Detailed Descriptions")
    lines.append("")
    for i, opp in enumerate(opps, 1):
        lines.append(f"### {i}. {opp['title']}")
        lines.append("")
        lines.append(f"- **Class:** `{opp['class_name']}` ({opp['source_path']})")
        lines.append(f"- **Domain:** {opp['domain']}")
        lines.append(f"- **Paper type:** {opp['paper_type']}")
        lines.append(f"- **Novelty score:** {opp['score']}/100")
        lines.append(f"- **Readiness:** {opp['readiness']}")
        lines.append(f"- **Effort:** {opp['effort']} (~{opp['effort_weeks']} weeks)")
        lines.append(f"- **Suggested journals:** {', '.join(opp['suggested_journals'])}")
        ev = opp["evidence"]
        lines.append(f"- **Size:** {ev['line_count']} lines, "
                     f"{ev['method_count']} public methods")
        lines.append(f"- **Tested:** {'Yes' if ev['has_test'] else 'No'}")
        lines.append(f"- **Recent commits:** {ev['recent_commits']} "
                     f"(+{ev['recent_insertions']} lines)")

        if opp.get("neqsim_improvement"):
            lines.append("")
            lines.append("**NeqSim code improvements this paper would drive:**")
            for imp in opp["neqsim_improvement"]:
                lines.append(f"- {imp}")

        if opp.get("commit_highlights"):
            lines.append("")
            lines.append("**Recent commit highlights:**")
            for msg in opp["commit_highlights"]:
                lines.append(f"- {msg}")

        lines.append("")

    # Domain breakdown
    lines.append("## Domain Breakdown")
    lines.append("")
    lines.append("| Domain | Opportunities |")
    lines.append("|--------|--------------|")
    for domain, count in summary["by_domain"].items():
        lines.append(f"| {domain} | {count} |")
    lines.append("")

    # Paper type breakdown
    lines.append("## Paper Type Breakdown")
    lines.append("")
    lines.append("| Type | Count |")
    lines.append("|------|-------|")
    for ptype, count in summary["by_paper_type"].items():
        lines.append(f"| {ptype} | {count} |")
    lines.append("")

    lines.append("---")
    lines.append("*Generated by [neqsim-paperlab](../README.md) research scanner.*")
    return "\n".join(lines)
