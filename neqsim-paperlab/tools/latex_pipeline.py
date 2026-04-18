"""
LaTeX Pipeline — Pandoc-based LaTeX compilation supporting Elsevier (elsarticle),
Springer, MDPI, ACS, and other journal LaTeX classes.

Usage::

    from tools.latex_pipeline import compile_latex, print_latex_report

    report = compile_latex("papers/my_paper/", journal="elsevier")
    print_latex_report(report)
"""

import json
import os
import shutil
import subprocess
from pathlib import Path
from typing import Dict, List, Optional


# Journal class templates and their Pandoc configurations
_JOURNAL_CONFIGS = {
    "elsevier": {
        "class": "elsarticle",
        "pandoc_args": [
            "--template", "elsarticle",
            "-V", "classoption=preprint,review,12pt",
            "-V", "documentclass=elsarticle",
        ],
        "header_includes": [
            "\\usepackage{lineno}",
            "\\linenumbers",
            "\\journal{Journal Name}",
        ],
        "bibstyle": "elsarticle-num",
    },
    "springer": {
        "class": "svjour3",
        "pandoc_args": [
            "-V", "classoption=twocolumn",
            "-V", "documentclass=svjour3",
        ],
        "header_includes": [
            "\\journalname{Journal Name}",
        ],
        "bibstyle": "spmpsci",
    },
    "mdpi": {
        "class": "mdpi",
        "pandoc_args": [
            "-V", "documentclass=article",
        ],
        "header_includes": [],
        "bibstyle": "mdpi",
    },
    "acs": {
        "class": "achemso",
        "pandoc_args": [
            "-V", "documentclass=achemso",
        ],
        "header_includes": [],
        "bibstyle": "achemso",
    },
    "generic": {
        "class": "article",
        "pandoc_args": [
            "-V", "documentclass=article",
            "-V", "classoption=12pt,a4paper",
            "-V", "geometry=margin=1in",
        ],
        "header_includes": [
            "\\usepackage{amsmath,amssymb}",
            "\\usepackage{graphicx}",
            "\\usepackage{booktabs}",
            "\\usepackage{hyperref}",
        ],
        "bibstyle": "unsrt",
    },
}


def _check_pandoc():
    """Check if Pandoc is available on PATH.

    Returns:
        Tuple of (available: bool, version: str).
    """
    try:
        result = subprocess.run(
            ["pandoc", "--version"],
            capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            version = result.stdout.split("\n")[0].strip()
            return True, version
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    return False, ""


def _check_latex():
    """Check if a LaTeX distribution is available.

    Returns:
        Tuple of (available: bool, engine: str).
    """
    for engine in ["pdflatex", "xelatex", "lualatex"]:
        try:
            result = subprocess.run(
                [engine, "--version"],
                capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                return True, engine
        except (FileNotFoundError, subprocess.TimeoutExpired):
            continue
    return False, ""


def _build_yaml_header(paper_dir, journal_config):
    """Build YAML metadata header for Pandoc from plan.json.

    Args:
        paper_dir: Path to the paper directory.
        journal_config: Journal configuration dict.

    Returns:
        YAML string to prepend to the manuscript.
    """
    plan_file = paper_dir / "plan.json"
    meta = {}
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        meta["title"] = plan.get("title", "Untitled")
        meta["abstract"] = plan.get("abstract", "")
        authors = plan.get("authors", [])
        if authors:
            meta["author"] = authors

    lines = ["---"]
    for key, val in meta.items():
        if isinstance(val, str):
            lines.append(f'{key}: "{val}"')
        elif isinstance(val, list):
            lines.append(f"{key}:")
            for item in val:
                lines.append(f"  - {item}")
    lines.append(f"bibliography: refs.bib")
    if journal_config.get("bibstyle"):
        lines.append(f"biblio-style: {journal_config['bibstyle']}")

    header_includes = journal_config.get("header_includes", [])
    if header_includes:
        lines.append("header-includes:")
        for inc in header_includes:
            lines.append(f"  - '{inc}'")

    lines.append("---")
    return "\n".join(lines)


def compile_latex(paper_dir, journal="elsevier", output_format="pdf",
                  engine=None, extra_pandoc_args=None):
    """Compile paper.md to LaTeX/PDF via Pandoc.

    Args:
        paper_dir: Path to the paper directory.
        journal: Journal template name (elsevier, springer, mdpi, acs, generic).
        output_format: Either 'pdf', 'tex', or 'both'.
        engine: LaTeX engine override (pdflatex, xelatex, lualatex).
        extra_pandoc_args: Additional Pandoc command-line arguments.

    Returns:
        Report dict with compilation status and output paths.
    """
    paper_dir = Path(paper_dir)
    manuscript = paper_dir / "paper.md"
    if not manuscript.exists():
        return {"error": f"Manuscript not found: {manuscript}"}

    # Check toolchain
    pandoc_ok, pandoc_ver = _check_pandoc()
    if not pandoc_ok:
        return {
            "error": "Pandoc not found. Install from https://pandoc.org/installing.html",
            "hint": "pip install pandoc  OR  choco install pandoc  OR  brew install pandoc",
        }

    latex_ok, latex_engine = _check_latex()
    if engine:
        latex_engine = engine

    journal_config = _JOURNAL_CONFIGS.get(journal, _JOURNAL_CONFIGS["generic"])

    # Build output directory
    output_dir = paper_dir / "output"
    output_dir.mkdir(exist_ok=True)

    report = {
        "pandoc_version": pandoc_ver,
        "latex_engine": latex_engine if latex_ok else "none",
        "journal_class": journal_config["class"],
        "outputs": [],
        "warnings": [],
    }

    # Generate .tex first
    tex_output = output_dir / "paper.tex"
    pandoc_cmd = [
        "pandoc", str(manuscript),
        "-o", str(tex_output),
        "--standalone",
        "--natbib",
    ]
    pandoc_cmd.extend(journal_config.get("pandoc_args", []))
    if extra_pandoc_args:
        pandoc_cmd.extend(extra_pandoc_args)

    # Add bibliography if refs.bib exists
    bib_file = paper_dir / "refs.bib"
    if bib_file.exists():
        pandoc_cmd.extend(["--bibliography", str(bib_file)])

    try:
        result = subprocess.run(
            pandoc_cmd, capture_output=True, text=True,
            cwd=str(paper_dir), timeout=120)

        if result.returncode == 0:
            report["outputs"].append(str(tex_output))
            report["tex_status"] = "success"
        else:
            report["tex_status"] = "failed"
            report["tex_error"] = result.stderr[:2000]
            if result.stderr:
                report["warnings"].append(f"Pandoc stderr: {result.stderr[:500]}")
    except subprocess.TimeoutExpired:
        report["tex_status"] = "timeout"
        report["tex_error"] = "Pandoc conversion timed out (120s)"
    except Exception as e:
        report["tex_status"] = "error"
        report["tex_error"] = str(e)

    # Compile to PDF if requested and LaTeX is available
    if output_format in ("pdf", "both") and latex_ok and report.get("tex_status") == "success":
        pdf_output = output_dir / "paper.pdf"
        pandoc_pdf_cmd = [
            "pandoc", str(manuscript),
            "-o", str(pdf_output),
            "--pdf-engine", latex_engine,
            "--natbib",
        ]
        pandoc_pdf_cmd.extend(journal_config.get("pandoc_args", []))
        if bib_file.exists():
            pandoc_pdf_cmd.extend(["--bibliography", str(bib_file)])
        if extra_pandoc_args:
            pandoc_pdf_cmd.extend(extra_pandoc_args)

        try:
            result = subprocess.run(
                pandoc_pdf_cmd, capture_output=True, text=True,
                cwd=str(paper_dir), timeout=180)

            if result.returncode == 0:
                report["outputs"].append(str(pdf_output))
                report["pdf_status"] = "success"
            else:
                report["pdf_status"] = "failed"
                report["pdf_error"] = result.stderr[:2000]
        except subprocess.TimeoutExpired:
            report["pdf_status"] = "timeout"
        except Exception as e:
            report["pdf_status"] = "error"
            report["pdf_error"] = str(e)
    elif output_format in ("pdf", "both") and not latex_ok:
        report["pdf_status"] = "skipped"
        report["warnings"].append(
            "No LaTeX distribution found. Install TeX Live or MiKTeX for PDF output.")

    report["status"] = "success" if report.get("outputs") else "failed"
    return report


def list_journal_templates():
    """List available journal LaTeX templates.

    Returns:
        Dict mapping template name to description.
    """
    return {
        name: f"Class: {cfg['class']}, BibStyle: {cfg.get('bibstyle', 'default')}"
        for name, cfg in _JOURNAL_CONFIGS.items()
    }


def print_latex_report(report):
    """Print a formatted LaTeX compilation report.

    Args:
        report: Report dict from compile_latex().
    """
    if "error" in report:
        print(f"  Error: {report['error']}")
        if "hint" in report:
            print(f"  Hint:  {report['hint']}")
        return

    print("=" * 60)
    print("LaTeX COMPILATION REPORT")
    print("=" * 60)
    print(f"  Pandoc:  {report.get('pandoc_version', 'unknown')}")
    print(f"  Engine:  {report.get('latex_engine', 'none')}")
    print(f"  Class:   {report.get('journal_class', 'unknown')}")
    print()

    tex_status = report.get("tex_status", "—")
    pdf_status = report.get("pdf_status", "—")
    print(f"  .tex generation: {tex_status}")
    print(f"  .pdf generation: {pdf_status}")
    print()

    outputs = report.get("outputs", [])
    if outputs:
        print("  Outputs:")
        for o in outputs:
            print(f"    → {o}")
    else:
        print("  No outputs generated.")

    warnings = report.get("warnings", [])
    if warnings:
        print()
        print("  Warnings:")
        for w in warnings:
            print(f"    ⚠ {w}")

    errors = []
    for key in ("tex_error", "pdf_error"):
        if key in report:
            errors.append(report[key])
    if errors:
        print()
        print("  Errors:")
        for e in errors:
            print(f"    ✖ {e[:200]}")
    print()
