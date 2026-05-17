#!/usr/bin/env python3
"""Daily research scan — run by GitHub Actions to detect paper opportunities.

This script has two modes:

**Trending mode (default)** — queries academic search APIs for recent hot
topics in NeqSim's capability domains, picks *one suggestion per day*, and
writes a ``daily_suggestion.json`` + ``daily_suggestion.md``.  A history file
prevents repeats within a configurable window (default: 90 days).

**Legacy codebase mode (--legacy)** — scans the NeqSim Java source tree for
paper opportunities based on git history and code analysis.  Produces the
same results on every run (retained for CI backwards compatibility).

Usage (from repo root)::

    python neqsim-paperlab/tools/daily_scan.py               # trending daily pick
    python neqsim-paperlab/tools/daily_scan.py --legacy       # old codebase scan
    python neqsim-paperlab/tools/daily_scan.py --full-scan    # full trending list

Environment variables (optional)::

    NEQSIM_REPO_ROOT   Path to NeqSim repo root (default: auto-detect)
    SCAN_OUTPUT_DIR     Where to write scan results (default: neqsim-paperlab/papers/_research_scan)
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

# Ensure tools/ is importable
_SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(_SCRIPT_DIR))

from research_scanner import generate_markdown_report, scan_opportunities


def _content_hash(report):
    """Deterministic hash of opportunity titles+scores to detect changes.

    Works with both legacy (class_name/score/readiness) and trending
    (domain/trend_score/inspiring_paper) opportunity formats.
    """
    key_data = []
    for opp in report.get("opportunities", []):
        # Legacy format
        if "class_name" in opp:
            key_data.append(f"{opp['class_name']}:{opp['score']}:{opp['readiness']}")
        # Trending format
        else:
            ip_title = opp.get("inspiring_paper", {}).get("title", "")
            key_data.append(f"{opp['domain']}:{opp.get('trend_score', 0)}:{ip_title[:40]}")
    return hashlib.sha256("|".join(sorted(key_data)).encode()).hexdigest()[:16]


def _run_trending_daily(args, output_dir):
    """Run the trending topics scanner and pick one suggestion for today."""
    from trending_topics import (
        daily_suggestion,
        format_daily_suggestion,
        generate_markdown_suggestion,
        scan_trending,
    )

    if args.full_scan:
        # Full trending scan — all opportunities
        print(f"Running full trending scan (top {args.top})...")
        report = scan_trending(top_n=args.top, rate_limit_delay=1.2)
        json_path = output_dir / "trending_opportunities.json"
        md_path = output_dir / "trending_report.md"

        with open(str(json_path), "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, default=str)
        print(f"Wrote {json_path}")

        # Simple markdown for full scan
        lines = ["# Trending Paper Opportunities\n"]
        lines.append(f"**Date:** {report['metadata']['scan_date']}  ")
        lines.append(f"**Domains searched:** {report['metadata']['domains_searched']}  \n")
        for i, opp in enumerate(report["opportunities"], 1):
            ip = opp.get("inspiring_paper", {})
            lines.append(f"### {i}. {opp['title']}")
            lines.append(f"- **Domain:** {opp['domain']}")
            lines.append(f"- **Trend score:** {opp['trend_score']}/100")
            lines.append(f"- **Inspired by:** {ip.get('title', 'N/A')}")
            lines.append(f"- **Research angle:** {opp.get('research_angle', 'N/A')}")
            lines.append("")

        with open(str(md_path), "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        print(f"Wrote {md_path}")

        n_opps = report["summary"]["total_opportunities"]
        return report, n_opps, True

    # Default: one daily suggestion
    print("Finding today's paper opportunity from trending research...")
    pick = daily_suggestion(
        history_dir=str(output_dir),
        scan_kwargs={"top_n": 30, "rate_limit_delay": 1.2},
    )

    if not pick:
        print("No trending suggestions found (API may be unavailable).")
        print("Falling back to legacy codebase scan.")
        return None, 0, False

    # Print to terminal
    print(format_daily_suggestion(pick))

    # Write JSON
    json_path = output_dir / "daily_suggestion.json"
    with open(str(json_path), "w", encoding="utf-8") as f:
        json.dump(pick, f, indent=2, default=str)
    print(f"\nWrote {json_path}")

    # Write markdown
    md_path = output_dir / "daily_suggestion.md"
    with open(str(md_path), "w", encoding="utf-8") as f:
        f.write(generate_markdown_suggestion(pick))
    print(f"Wrote {md_path}")

    # Wrap in report format for the hash/change-detection logic
    report = {
        "opportunities": [pick],
        "summary": {"total_opportunities": 1, "top_score": pick["trend_score"]},
        "metadata": pick.get("scan_metadata", {}),
    }
    return report, 1, True


def _run_legacy_scan(args, repo_root, output_dir):
    """Run the legacy codebase scanner (old behavior)."""
    print(f"Scanning {repo_root} (last {args.since} days, top {args.top})...")

    report = scan_opportunities(
        repo_root,
        since_days=args.since,
        top_n=args.top,
        check_literature=False,
    )

    md_report = generate_markdown_report(report)

    json_path = output_dir / "opportunities.json"
    md_path = output_dir / "scout_report.md"

    with open(str(json_path), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, default=str)
    print(f"Wrote {json_path}")

    with open(str(md_path), "w", encoding="utf-8") as f:
        f.write(md_report)
    print(f"Wrote {md_path}")

    n_opps = report["summary"]["total_opportunities"]
    n_ready = report["summary"]["ready_count"]
    top_score = report["summary"]["top_score"]
    return report, n_opps, n_ready, top_score


def main():
    parser = argparse.ArgumentParser(description="Daily NeqSim research scan")
    parser.add_argument("--since", type=int, default=180,
                        help="Look-back window in days for legacy scan (default: 180)")
    parser.add_argument("--top", type=int, default=15,
                        help="Max opportunities to report (default: 15)")
    parser.add_argument("--force", action="store_true",
                        help="Create PR even if nothing changed")
    parser.add_argument("--legacy", action="store_true",
                        help="Use old codebase scanner instead of trending topics")
    parser.add_argument("--full-scan", action="store_true",
                        help="Show all trending opportunities (not just daily pick)")
    args = parser.parse_args()

    # Resolve paths
    repo_root = os.environ.get("NEQSIM_REPO_ROOT")
    if not repo_root:
        repo_root = str(_SCRIPT_DIR.parent.parent)  # neqsim-paperlab/../
    repo_root = Path(repo_root)

    output_dir = os.environ.get("SCAN_OUTPUT_DIR")
    if not output_dir:
        output_dir = repo_root / "neqsim-paperlab" / "papers" / "_research_scan"
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Choose mode
    if args.legacy:
        report, n_opps, n_ready, top_score = _run_legacy_scan(args, repo_root, output_dir)
    else:
        result = _run_trending_daily(args, output_dir)
        report, n_opps, trending_ok = result
        if not trending_ok:
            # Fallback to legacy if trending API failed
            report, n_opps, n_ready, top_score = _run_legacy_scan(args, repo_root, output_dir)
        else:
            n_ready = n_opps  # trending results are always "ready"
            top_score = report["summary"].get("top_score", 0) if report else 0

    if not report:
        print("No results from either scanner.")
        return 1

    # Check if content changed since last scan
    new_hash = _content_hash(report)
    hash_path = output_dir / ".last_scan_hash"

    old_hash = ""
    if hash_path.exists():
        old_hash = hash_path.read_text().strip()

    changed = new_hash != old_hash or args.force

    # Write new hash
    hash_path.write_text(new_hash)

    # Write metadata for the GitHub Action to consume
    pr_title = (f"research-scan: {n_opps} paper opportunities found "
                f"(top score {top_score})")

    pr_meta_path = output_dir / ".pr_metadata.json"
    with open(str(pr_meta_path), "w", encoding="utf-8") as f:
        json.dump({
            "title": pr_title,
            "changed": changed,
            "hash": new_hash,
            "opportunities": n_opps,
            "ready": n_ready,
            "top_score": top_score,
        }, f, indent=2)

    if changed:
        print(f"\nOpportunities changed (hash {old_hash[:8] or 'none'}→{new_hash[:8]})")
        print(f"Issue title: {pr_title}")
    else:
        print(f"\nNo changes detected (hash={new_hash[:8]}). Skipping issue.")

    # Write to GITHUB_OUTPUT if available (for the Actions workflow)
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"changed={'true' if changed else 'false'}\n")
            f.write(f"title={pr_title}\n")
            f.write(f"opportunities={n_opps}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
