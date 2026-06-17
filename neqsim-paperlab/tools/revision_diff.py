"""
Revision Diff — Visual comparison between manuscript versions.

Generates an HTML diff report showing additions, deletions, and changes
between two versions of paper.md.

Usage::

    from tools.revision_diff import generate_diff

    html_path = generate_diff("papers/my_paper/", revision=1)
    # Opens: papers/my_paper/revision_1/diff_r0_r1.html
"""

import difflib
import re
from datetime import datetime
from pathlib import Path
from typing import Optional


def _clean_for_diff(text):
    """Normalize text for cleaner diffs (strip trailing spaces, normalize newlines)."""
    lines = text.splitlines()
    lines = [line.rstrip() for line in lines]
    return lines


def _section_summary(old_lines, new_lines):
    """Produce a section-level change summary."""
    old_sections = set()
    new_sections = set()

    for line in old_lines:
        m = re.match(r'^(#{1,3})\s+(.+)$', line)
        if m:
            old_sections.add(m.group(2).strip())

    for line in new_lines:
        m = re.match(r'^(#{1,3})\s+(.+)$', line)
        if m:
            new_sections.add(m.group(2).strip())

    added = new_sections - old_sections
    removed = old_sections - new_sections
    unchanged = old_sections & new_sections

    return {
        "added_sections": sorted(added),
        "removed_sections": sorted(removed),
        "unchanged_sections": sorted(unchanged),
    }


def _count_changes(diff_lines):
    """Count additions, deletions, and context lines in a unified diff."""
    additions = 0
    deletions = 0
    for line in diff_lines:
        if line.startswith('+') and not line.startswith('+++'):
            additions += 1
        elif line.startswith('-') and not line.startswith('---'):
            deletions += 1
    return additions, deletions


def generate_diff(paper_dir, revision=None, old_file=None, new_file=None):
    """Generate an HTML diff between two manuscript versions.

    Parameters
    ----------
    paper_dir : str or Path
        Paper project directory.
    revision : int, optional
        Revision number. Compares revision_N/paper_rN-1_baseline.md with paper.md.
    old_file : str or Path, optional
        Explicit path to old version. Overrides revision auto-detection.
    new_file : str or Path, optional
        Explicit path to new version (default: paper.md).

    Returns
    -------
    dict
        Report with diff stats and path to generated HTML file.
    """
    paper_dir = Path(paper_dir)

    # Determine files to compare
    if old_file and new_file:
        old_path = Path(old_file)
        new_path = Path(new_file)
    elif revision is not None:
        rev_dir = paper_dir / f"revision_{revision}"
        baseline_num = revision - 1
        old_path = rev_dir / f"paper_r{baseline_num}_baseline.md"
        new_path = paper_dir / "paper.md"
        if not rev_dir.exists():
            return {"error": f"Revision directory not found: {rev_dir}"}
    else:
        # Auto-detect latest revision
        rev_dirs = sorted(paper_dir.glob("revision_*"))
        if not rev_dirs:
            return {"error": "No revision directories found. Run 'revise' first, "
                           "or specify --old and --new explicitly."}
        latest = rev_dirs[-1]
        revision = int(latest.name.split("_")[1])
        baseline_num = revision - 1
        old_path = latest / f"paper_r{baseline_num}_baseline.md"
        new_path = paper_dir / "paper.md"

    # Validate files exist
    if not old_path.exists():
        return {"error": f"Old version not found: {old_path}"}
    if not new_path.exists():
        return {"error": f"New version not found: {new_path}"}

    old_text = old_path.read_text(encoding="utf-8")
    new_text = new_path.read_text(encoding="utf-8")
    old_lines = _clean_for_diff(old_text)
    new_lines = _clean_for_diff(new_text)

    # Generate unified diff
    diff = list(difflib.unified_diff(
        old_lines, new_lines,
        fromfile=old_path.name,
        tofile=new_path.name,
        lineterm="",
    ))
    additions, deletions = _count_changes(diff)

    # Section-level summary
    section_changes = _section_summary(old_lines, new_lines)

    # Word count comparison
    old_words = len(old_text.split())
    new_words = len(new_text.split())

    # Generate side-by-side HTML diff
    html_diff = difflib.HtmlDiff(wrapcolumn=80)
    html_table = html_diff.make_table(
        old_lines, new_lines,
        fromdesc=old_path.name,
        todesc=new_path.name,
        context=True,
        numlines=3,
    )

    # Build full HTML page
    html = _build_html_report(
        old_path.name, new_path.name,
        additions, deletions,
        old_words, new_words,
        section_changes,
        html_table,
    )

    # Write output
    if revision is not None:
        out_dir = paper_dir / f"revision_{revision}"
    else:
        out_dir = paper_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"diff_{old_path.stem}_vs_{new_path.stem}.html"
    out_file.write_text(html, encoding="utf-8")

    return {
        "old_file": str(old_path),
        "new_file": str(new_path),
        "additions": additions,
        "deletions": deletions,
        "old_words": old_words,
        "new_words": new_words,
        "word_delta": new_words - old_words,
        "sections_added": section_changes["added_sections"],
        "sections_removed": section_changes["removed_sections"],
        "html_report": str(out_file),
    }


def _build_html_report(old_name, new_name, additions, deletions,
                       old_words, new_words, section_changes, diff_table):
    """Build a styled HTML diff report."""
    delta = new_words - old_words
    delta_str = f"+{delta}" if delta >= 0 else str(delta)

    sections_html = ""
    if section_changes["added_sections"]:
        items = "".join(f"<li style='color:#2e7d32'>+ {s}</li>"
                       for s in section_changes["added_sections"])
        sections_html += f"<h3>Added Sections</h3><ul>{items}</ul>"
    if section_changes["removed_sections"]:
        items = "".join(f"<li style='color:#c62828'>- {s}</li>"
                       for s in section_changes["removed_sections"])
        sections_html += f"<h3>Removed Sections</h3><ul>{items}</ul>"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Diff: {old_name} vs {new_name}</title>
<style>
  body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
         max-width: 1200px; margin: 20px auto; padding: 0 20px; color: #333; }}
  h1 {{ color: #1565C0; font-size: 1.5em; }}
  .stats {{ display: flex; gap: 20px; margin: 1em 0; flex-wrap: wrap; }}
  .stat-box {{ background: #f5f5f5; padding: 12px 20px; border-radius: 8px;
               border-left: 4px solid #1565C0; }}
  .stat-box.add {{ border-left-color: #2e7d32; }}
  .stat-box.del {{ border-left-color: #c62828; }}
  .stat-box .label {{ font-size: 0.85em; color: #666; }}
  .stat-box .value {{ font-size: 1.4em; font-weight: 600; }}
  table.diff {{ font-family: 'Consolas', 'Monaco', monospace; font-size: 0.85em;
                border-collapse: collapse; width: 100%; }}
  table.diff td {{ padding: 2px 8px; vertical-align: top; white-space: pre-wrap;
                   word-wrap: break-word; }}
  table.diff .diff_header {{ background: #e0e0e0; font-weight: 600; }}
  table.diff .diff_next {{ background: #E3F2FD; }}
  table.diff .diff_add {{ background: #e8f5e9; }}
  table.diff .diff_chg {{ background: #fff9c4; }}
  table.diff .diff_sub {{ background: #ffebee; }}
  .timestamp {{ color: #999; font-size: 0.85em; }}
</style>
</head>
<body>
<h1>Manuscript Diff Report</h1>
<p class="timestamp">Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}</p>
<p><strong>{old_name}</strong> → <strong>{new_name}</strong></p>

<div class="stats">
  <div class="stat-box add">
    <div class="label">Lines Added</div>
    <div class="value" style="color:#2e7d32">+{additions}</div>
  </div>
  <div class="stat-box del">
    <div class="label">Lines Removed</div>
    <div class="value" style="color:#c62828">-{deletions}</div>
  </div>
  <div class="stat-box">
    <div class="label">Old Word Count</div>
    <div class="value">{old_words:,}</div>
  </div>
  <div class="stat-box">
    <div class="label">New Word Count</div>
    <div class="value">{new_words:,} ({delta_str})</div>
  </div>
</div>

{sections_html}

<h2>Line-by-Line Diff</h2>
{diff_table}

</body>
</html>"""


def print_diff_summary(report):
    """Print a quick diff summary to console."""
    if "error" in report:
        print(f"Error: {report['error']}")
        return

    print("=" * 60)
    print("REVISION DIFF SUMMARY")
    print("=" * 60)
    print(f"  Old: {Path(report['old_file']).name}")
    print(f"  New: {Path(report['new_file']).name}")
    print(f"  Lines added:   +{report['additions']}")
    print(f"  Lines removed: -{report['deletions']}")
    delta = report['word_delta']
    delta_str = f"+{delta}" if delta >= 0 else str(delta)
    print(f"  Words: {report['old_words']:,} → {report['new_words']:,} ({delta_str})")

    if report.get("sections_added"):
        print(f"\n  New sections:")
        for s in report["sections_added"]:
            print(f"    + {s}")
    if report.get("sections_removed"):
        print(f"\n  Removed sections:")
        for s in report["sections_removed"]:
            print(f"    - {s}")

    print(f"\n  HTML report: {report['html_report']}")
    print()
