"""
Related Work Table Generator — Builds structured comparison tables from
literature entries to position a paper's contribution against prior work.

Usage::

    from tools.related_work_table import generate_related_work_table, print_related_work_report

    report = generate_related_work_table("papers/my_paper/")
    print_related_work_report(report)
"""

import json
import re
from pathlib import Path
from typing import Dict, List, Optional


def _load_literature(paper_dir):
    """Load literature entries from various sources in the paper directory.

    Checks: literature_map.md, plan.json (literature section), refs.bib.

    Args:
        paper_dir: Path to the paper directory.

    Returns:
        List of literature entry dicts.
    """
    paper_dir = Path(paper_dir)
    entries = []

    # 1. Load from literature_map.md (structured markdown)
    lit_map = paper_dir / "literature_map.md"
    if lit_map.exists():
        text = lit_map.read_text(encoding="utf-8", errors="replace")
        # Parse entries like "### Author (Year) — Title\n- Method: ...\n- Key finding: ..."
        entry_pattern = re.compile(
            r'###\s+(.+?)(?:\n(?:- .+\n?)+)', re.MULTILINE)
        for match in entry_pattern.finditer(text):
            header = match.group(1).strip()
            body = match.group(0)

            entry = {"header": header, "source": "literature_map.md"}
            # Extract structured fields
            for field in ["Method", "Key finding", "Limitation", "Dataset",
                         "EOS", "Algorithm", "Systems", "Scope"]:
                field_match = re.search(
                    rf'-\s*{field}\s*:\s*(.+)', body, re.IGNORECASE)
                if field_match:
                    entry[field.lower().replace(" ", "_")] = field_match.group(1).strip()

            entries.append(entry)

    # 2. Load from plan.json literature section
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        for item in plan.get("literature", []):
            if isinstance(item, dict):
                item["source"] = "plan.json"
                entries.append(item)
            elif isinstance(item, str):
                entries.append({"header": item, "source": "plan.json"})

    return entries


def _load_comparison_config(paper_dir):
    """Load comparison dimensions from plan.json or use defaults.

    Args:
        paper_dir: Path to paper directory.

    Returns:
        List of dimension names for comparison columns.
    """
    paper_dir = Path(paper_dir)
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        dims = plan.get("comparison_dimensions", [])
        if dims:
            return dims

    # Defaults for thermodynamics/process papers
    return ["Method/Algorithm", "EOS", "Systems Tested", "Key Result", "Limitation"]


def generate_related_work_table(paper_dir, dimensions=None, include_this_work=True):
    """Generate a structured comparison table of related work.

    Args:
        paper_dir: Path to the paper directory.
        dimensions: List of comparison column names (auto-detected if None).
        include_this_work: Whether to add a "This work" row from plan.json.

    Returns:
        Report dict with markdown and LaTeX table representations.
    """
    paper_dir = Path(paper_dir)
    entries = _load_literature(paper_dir)

    if dimensions is None:
        dimensions = _load_comparison_config(paper_dir)

    if not entries:
        return {
            "status": "no_literature",
            "message": "No literature entries found. Add literature_map.md or entries to plan.json.",
            "dimensions": dimensions,
        }

    # Map entries to comparison dimensions
    dim_keys = [d.lower().replace(" ", "_").replace("/", "_") for d in dimensions]
    rows = []

    for entry in entries:
        row = {"reference": entry.get("header", "Unknown")}
        for dim, dim_key in zip(dimensions, dim_keys):
            # Try multiple key variations
            val = (entry.get(dim_key) or
                   entry.get(dim.lower()) or
                   entry.get(dim) or
                   "—")
            row[dim] = val
        rows.append(row)

    # Add "This work" row
    if include_this_work:
        plan_file = paper_dir / "plan.json"
        this_work = {"reference": "**This work**"}
        if plan_file.exists():
            with open(plan_file) as f:
                plan = json.load(f)
            tw_data = plan.get("this_work_comparison", {})
            for dim in dimensions:
                this_work[dim] = tw_data.get(dim, tw_data.get(
                    dim.lower().replace(" ", "_"), "—"))
        else:
            for dim in dimensions:
                this_work[dim] = "—"
        rows.append(this_work)

    # Build Markdown table
    md_lines = []
    header = "| Reference | " + " | ".join(dimensions) + " |"
    separator = "|-----------|" + "|".join(["---" for _ in dimensions]) + "|"
    md_lines.append(header)
    md_lines.append(separator)
    for row in rows:
        cells = [row["reference"]] + [str(row.get(d, "—")) for d in dimensions]
        md_lines.append("| " + " | ".join(cells) + " |")

    # Build LaTeX table
    col_spec = "l" + "p{3cm}" * len(dimensions)
    tex_lines = [
        "\\begin{table}[htbp]",
        "\\caption{Comparison of related work.}",
        "\\label{tab:related_work}",
        "\\centering",
        "\\footnotesize",
        f"\\begin{{tabular}}{{{col_spec}}}",
        "\\toprule",
        "Reference & " + " & ".join(dimensions) + " \\\\",
        "\\midrule",
    ]
    for row in rows:
        cells = [row["reference"].replace("**", "\\textbf{").replace("**", "}")]
        for d in dimensions:
            cells.append(str(row.get(d, "---")))
        # Fix bold for LaTeX
        ref_cell = row["reference"]
        if ref_cell.startswith("**") and ref_cell.endswith("**"):
            ref_cell = "\\textbf{" + ref_cell.strip("*") + "}"
        cells_tex = [ref_cell] + [str(row.get(d, "---")) for d in dimensions]
        tex_lines.append(" & ".join(cells_tex) + " \\\\")
    tex_lines += ["\\bottomrule", "\\end{tabular}", "\\end{table}"]

    report = {
        "status": "generated",
        "entry_count": len(entries),
        "dimensions": dimensions,
        "rows": rows,
        "markdown_table": "\n".join(md_lines),
        "latex_table": "\n".join(tex_lines),
        "includes_this_work": include_this_work,
    }

    # Save tables to files
    (paper_dir / "related_work_table.md").write_text(
        "\n".join(md_lines), encoding="utf-8")
    (paper_dir / "related_work_table.tex").write_text(
        "\n".join(tex_lines), encoding="utf-8")

    return report


def print_related_work_report(report):
    """Print a formatted related work table report.

    Args:
        report: Report dict from generate_related_work_table().
    """
    if report.get("status") == "no_literature":
        print(f"  {report['message']}")
        print(f"  Suggested dimensions: {', '.join(report['dimensions'])}")
        return

    print("=" * 60)
    print("RELATED WORK COMPARISON TABLE")
    print("=" * 60)
    print(f"  Entries: {report['entry_count']}    Dimensions: {len(report['dimensions'])}")
    print(f"  Includes 'This work': {report['includes_this_work']}")
    print()
    print(report["markdown_table"])
    print()
    print("  Saved: related_work_table.md, related_work_table.tex")
    print()
