#!/usr/bin/env python3
"""Daily research scan — run by GitHub Actions to detect paper opportunities.

This script:
1. Runs the research scanner on the NeqSim repository.
2. Generates ``scout_report.md`` and ``opportunities.json``.
3. Checks if opportunities changed since the last scan.
4. Exits with code 0 (no changes) or writes a PR body for the CI to pick up.

Usage (from repo root)::

    python neqsim-paperlab/tools/daily_scan.py [--since 180] [--top 15]

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
    """Deterministic hash of opportunity titles+scores to detect changes."""
    key_data = []
    for opp in report.get("opportunities", []):
        key_data.append(f"{opp['class_name']}:{opp['score']}:{opp['readiness']}")
    return hashlib.sha256("|".join(sorted(key_data)).encode()).hexdigest()[:16]


def main():
    parser = argparse.ArgumentParser(description="Daily NeqSim research scan")
    parser.add_argument("--since", type=int, default=180,
                        help="Look-back window in days (default: 180)")
    parser.add_argument("--top", type=int, default=15,
                        help="Max opportunities to report (default: 15)")
    parser.add_argument("--force", action="store_true",
                        help="Create PR even if nothing changed")
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

    print(f"Scanning {repo_root} (last {args.since} days, top {args.top})...")

    # Run scan
    report = scan_opportunities(
        repo_root,
        since_days=args.since,
        top_n=args.top,
        check_literature=False,  # avoid rate limits in CI
    )

    # Generate markdown report
    md_report = generate_markdown_report(report)

    # Write outputs
    json_path = output_dir / "opportunities.json"
    md_path = output_dir / "scout_report.md"

    with open(str(json_path), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, default=str)
    print(f"Wrote {json_path}")

    with open(str(md_path), "w", encoding="utf-8") as f:
        f.write(md_report)
    print(f"Wrote {md_path}")

    # Check if content changed since last scan
    new_hash = _content_hash(report)
    hash_path = output_dir / ".last_scan_hash"

    old_hash = ""
    if hash_path.exists():
        old_hash = hash_path.read_text().strip()

    changed = new_hash != old_hash or args.force

    # Write new hash
    hash_path.write_text(new_hash)

    # Write PR metadata for the GitHub Action to consume
    n_opps = report["summary"]["total_opportunities"]
    n_ready = report["summary"]["ready_count"]
    top_score = report["summary"]["top_score"]

    pr_title = (f"research-scan: {n_opps} paper opportunities found "
                f"({n_ready} ready, top score {top_score})")

    pr_body = (
        f"## Automated Research Scan\n\n"
        f"The daily research scanner found **{n_opps}** paper opportunities "
        f"in the NeqSim codebase.\n\n"
        f"- **Ready to write:** {n_ready}\n"
        f"- **Top score:** {top_score}/100\n"
        f"- **Scan window:** last {args.since} days\n\n"
        f"### What's inside\n\n"
        f"- `neqsim-paperlab/papers/_research_scan/scout_report.md` — "
        f"Full ranked report with details\n"
        f"- `neqsim-paperlab/papers/_research_scan/opportunities.json` — "
        f"Machine-readable results\n\n"
        f"### Next steps\n\n"
        f"1. Review the top opportunities in `scout_report.md`\n"
        f"2. Pick a topic and run "
        f"`python paperflow.py new \"Paper Title\" --journal <journal>`\n"
        f"3. The paper-writing workflow will improve NeqSim code along the way\n\n"
        f"---\n"
        f"*This PR was created automatically by the "
        f"[neqsim-paperlab research scanner]"
        f"(neqsim-paperlab/README.md).*"
    )

    # Write PR metadata as files the Action can read
    pr_meta_path = output_dir / ".pr_metadata.json"
    with open(str(pr_meta_path), "w", encoding="utf-8") as f:
        json.dump({
            "title": pr_title,
            "body": pr_body,
            "changed": changed,
            "hash": new_hash,
            "opportunities": n_opps,
            "ready": n_ready,
            "top_score": top_score,
        }, f, indent=2)

    if changed:
        print(f"\nOpportunities changed (hash {old_hash[:8] or 'none'}→{new_hash[:8]})")
        print(f"PR title: {pr_title}")
    else:
        print(f"\nNo changes detected (hash={new_hash[:8]}). Skipping PR.")

    # Exit codes: 0 = changed (create PR), 1 = no changes (skip PR)
    # Reversed from usual convention so the Action can use 'if: success()'
    # Actually, let's use env file approach instead
    # Write to GITHUB_OUTPUT if available
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"changed={'true' if changed else 'false'}\n")
            f.write(f"pr_title={pr_title}\n")
            f.write(f"opportunities={n_opps}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
