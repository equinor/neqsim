#!/usr/bin/env python3
"""
paperflow — CLI orchestrator for NeqSim scientific paper production.

Commands:
    paperflow new <title> --journal <name> --topic <slug>
    paperflow benchmark <paper_dir>
    paperflow figures <paper_dir>
    paperflow draft <paper_dir>
    paperflow format <paper_dir> --journal <name>
    paperflow audit <paper_dir>
    paperflow status <paper_dir>

Example:
    python paperflow.py new "TPflash Algorithm Improvements" \\
        --journal fluid_phase_equilibria --topic tpflash_algorithms
"""

import argparse
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path


PAPERLAB_ROOT = Path(__file__).parent
PAPERS_DIR = PAPERLAB_ROOT / "papers"
BOOKS_DIR = PAPERLAB_ROOT / "books"
JOURNALS_DIR = PAPERLAB_ROOT / "journals"
TOOLS_DIR = PAPERLAB_ROOT / "tools"

# Canonical status values — used by cmd_list and for validation
VALID_STATUSES = [
    "planning", "benchmarking", "drafting", "draft_complete",
    "formatted", "submitted", "revision", "accepted", "published",
]

# Map ad-hoc status strings to canonical values
_STATUS_ALIASES = {
    "draft": "drafting",
    "draft_v2": "drafting",
    "draft_in_progress": "drafting",
    "first draft": "drafting",
    "DRAFT": "drafting",
    "FIRST DRAFT": "drafting",
}


def normalize_status(raw):
    """Return canonical status for *raw*, or raw.lower() if unrecognized."""
    if raw in VALID_STATUSES:
        return raw
    return _STATUS_ALIASES.get(raw, _STATUS_ALIASES.get(raw.lower() if raw else "", raw))


def cmd_list(args):
    """List all papers with status, journal, type, and word count."""
    if not PAPERS_DIR.exists():
        print("No papers directory found.")
        return

    rows = []
    for d in sorted(PAPERS_DIR.iterdir()):
        if not d.is_dir():
            continue
        plan_path = d / "plan.json"
        if not plan_path.exists():
            continue
        try:
            with open(plan_path, encoding="utf-8") as f:
                plan = json.load(f)
        except (json.JSONDecodeError, OSError):
            rows.append((d.name, "BROKEN", "", "", ""))
            continue
        journal = plan.get("target_journal", "")
        status = plan.get("status", "")
        ptype = plan.get("paper_type", "")
        # word count from paper.md
        paper_md = d / "paper.md"
        wc = ""
        if paper_md.exists():
            try:
                text = paper_md.read_text(encoding="utf-8")
                wc = str(len(text.split()))
            except OSError:
                pass
        rows.append((d.name, status, journal, ptype, wc))

    if not rows:
        print("No papers found.")
        return

    # Column widths
    headers = ("Paper", "Status", "Journal", "Type", "Words")
    widths = [max(len(h), max((len(str(r[i])) for r in rows), default=0))
              for i, h in enumerate(headers)]
    fmt = "  ".join(f"{{:<{w}}}" for w in widths)

    print(fmt.format(*headers))
    print(fmt.format(*("-" * w for w in widths)))
    for row in rows:
        print(fmt.format(*row))
    print(f"\n{len(rows)} papers total")


def cmd_new(args):
    """Create a new paper project."""
    title = args.title
    journal = args.journal
    paper_type = getattr(args, "paper_type", None)
    topic = args.topic or title.lower().replace(" ", "_")[:40]
    year = datetime.now().strftime("%Y")

    slug = f"{topic}_{year}"
    paper_dir = PAPERS_DIR / slug

    if paper_dir.exists():
        print(f"Error: paper directory already exists: {paper_dir}")
        sys.exit(1)

    # Create directory structure
    dirs = [
        paper_dir,
        paper_dir / "algorithm",
        paper_dir / "figures",
        paper_dir / "figures" / "validation",
        paper_dir / "tables",
        paper_dir / "results",
        paper_dir / "results" / "raw",
        paper_dir / "submission",
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)

    # Create plan.json skeleton
    plan = {
        "title": title,
        "target_journal": journal,
        "paper_type": paper_type,
        "created": datetime.now().isoformat(),
        "status": "planning",
        "novelty_statement": "TODO: What is new about this work?",
        "research_questions": [
            {
                "id": "RQ1",
                "question": "TODO: Primary research question",
                "hypothesis": "TODO: Expected answer",
                "method": "TODO: How to answer",
                "acceptance_criterion": "TODO: When is the answer good enough?",
            }
        ],
        "benchmark_design": {
            "algorithms": ["baseline", "candidate_1"],
            "eos_models": ["SRK"],
            "fluid_families": [
                "lean_gas", "rich_gas", "gas_condensate",
                "co2_rich", "wide_boiling", "near_critical",
            ],
            "pressure_range_bara": [1, 500],
            "temperature_range_K": [200, 500],
            "n_cases_per_family": 200,
            "metrics": [
                "convergence_rate", "iterations", "cpu_time_ms",
                "residual_norm", "phase_id_correct"
            ],
        },
        "manuscript_outline": {
            "sections": [
                "Abstract", "Keywords", "Highlights",
                "Introduction", "Mathematical Framework",
                "Algorithm Description", "Benchmark Design",
                "Results and Discussion", "Conclusions",
                "Acknowledgements", "References"
            ],
            "key_figures": [],
            "key_tables": [],
        },
        "workflow_log": [
            {
                "stage": "plan",
                "status": "created",
                "date": datetime.now().isoformat(),
            }
        ],
    }

    with open(paper_dir / "plan.json", "w") as f:
        json.dump(plan, f, indent=2)

    # Create benchmark config
    benchmark_config = {
        "benchmark_id": f"{topic}_{year}",
        "created": datetime.now().isoformat(),
        "algorithms": ["baseline"],
        "eos_models": ["SRK"],
        "random_seed": 42,
        "timing_repeats": 3,
        "families": [
            {
                "name": "lean_gas",
                "base_composition": {
                    "methane": 0.90, "ethane": 0.05,
                    "propane": 0.03, "nitrogen": 0.01, "CO2": 0.01,
                },
                "n_composition_variants": 5,
                "dirichlet_concentration": 50,
                "T_range_K": [200, 400],
                "P_range_bara": [1, 200],
                "n_T": 10,
                "n_P": 10,
            },
            {
                "name": "rich_gas",
                "base_composition": {
                    "methane": 0.70, "ethane": 0.10, "propane": 0.08,
                    "n-butane": 0.05, "n-pentane": 0.03,
                    "nitrogen": 0.02, "CO2": 0.02,
                },
                "n_composition_variants": 5,
                "dirichlet_concentration": 40,
                "T_range_K": [220, 450],
                "P_range_bara": [5, 300],
                "n_T": 10,
                "n_P": 10,
            },
            {
                "name": "gas_condensate",
                "base_composition": {
                    "methane": 0.65, "ethane": 0.08, "propane": 0.06,
                    "n-butane": 0.04, "n-pentane": 0.03,
                    "n-hexane": 0.02, "n-heptane": 0.02,
                    "n-decane": 0.05, "nitrogen": 0.02, "CO2": 0.03,
                },
                "n_composition_variants": 5,
                "dirichlet_concentration": 30,
                "T_range_K": [250, 500],
                "P_range_bara": [10, 500],
                "n_T": 10,
                "n_P": 10,
            },
        ],
        "stress_cases": {
            "near_critical": 20,
            "near_bubble": 10,
            "near_dew": 10,
            "trace_component": 10,
        },
    }

    with open(paper_dir / "benchmark_config.json", "w") as f:
        json.dump(benchmark_config, f, indent=2)

    # Create paper skeleton — select template by paper_type
    _TEMPLATE_MAP = {
        "comparative": "paper_skeleton_comparative.md",
        "data": "paper_skeleton_data.md",
    }
    # SPE journal always uses the SPE template
    if journal and "spe" in journal.lower():
        skeleton_name = "paper_skeleton_spe.md"
    else:
        skeleton_name = _TEMPLATE_MAP.get(paper_type, "paper_skeleton.md")
    paper_template = PAPERLAB_ROOT / "templates" / skeleton_name
    if paper_template.exists():
        template_text = paper_template.read_text(encoding="utf-8")
        paper_text = template_text.replace("{{TITLE}}", title)
        paper_text = paper_text.replace("{{JOURNAL}}", journal)
        paper_text = paper_text.replace("{{DATE}}", datetime.now().strftime("%Y-%m-%d"))
    else:
        paper_text = f"# {title}\n\n## Abstract\n\nTODO\n"

    (paper_dir / "paper.md").write_text(paper_text, encoding="utf-8")

    # Create empty refs.bib
    refs_bib = """% References for: {title}
% Generated by paperflow on {date}

@software{{neqsim2024,
  title = {{{{NeqSim}}: {{Non-Equilibrium Simulator}}}},
  author = {{Solbraa, Even}},
  year = {{2024}},
  url = {{https://github.com/equinor/neqsim}},
  note = {{Open-source thermodynamics and process simulation library}}
}}

@article{{Michelsen1982a,
  author = {{Michelsen, Michael L.}},
  title = {{The isothermal flash problem. Part I. Stability}},
  journal = {{Fluid Phase Equilibria}},
  volume = {{9}},
  pages = {{1--19}},
  year = {{1982}}
}}

@article{{Michelsen1982b,
  author = {{Michelsen, Michael L.}},
  title = {{The isothermal flash problem. Part II. Phase-split calculation}},
  journal = {{Fluid Phase Equilibria}},
  volume = {{9}},
  pages = {{21--40}},
  year = {{1982}}
}}

@article{{RachfordRice1952,
  author = {{Rachford, H. H. and Rice, J. D.}},
  title = {{Procedure for use of electronic digital computers in calculating flash vaporization hydrocarbon equilibrium}},
  journal = {{Journal of Petroleum Technology}},
  volume = {{4}},
  number = {{10}},
  pages = {{19}},
  year = {{1952}}
}}
""".format(title=title, date=datetime.now().strftime("%Y-%m-%d"))

    (paper_dir / "refs.bib").write_text(refs_bib, encoding="utf-8")

    # Create empty claims files
    (paper_dir / "approved_claims.json").write_text(
        json.dumps({"claims": [], "threats_to_validity": []}, indent=2),
        encoding="utf-8",
    )
    (paper_dir / "claims_manifest.json").write_text(
        json.dumps({"claims": [], "unlinked_claims": []}, indent=2),
        encoding="utf-8",
    )

    print(f"Created paper project: {paper_dir}")
    print()
    print("Next steps:")
    print(f"  1. Edit {paper_dir / 'plan.json'} — fill in research questions")
    print(f"  2. Edit {paper_dir / 'benchmark_config.json'} — tune test matrix")
    print(f"  3. Run: python paperflow.py benchmark {paper_dir}")
    print(f"  4. Run: python paperflow.py draft {paper_dir}")
    print(f"  5. Run: python paperflow.py format {paper_dir} --journal {journal}")


def cmd_benchmark(args):
    """Run the benchmark suite for a paper."""
    paper_dir = Path(args.paper_dir)
    config_file = paper_dir / "benchmark_config.json"

    if not config_file.exists():
        print(f"Error: benchmark_config.json not found in {paper_dir}")
        sys.exit(1)

    with open(config_file) as f:
        config = json.load(f)

    # Add tools to path
    sys.path.insert(0, str(TOOLS_DIR))
    from flash_benchmark import run_benchmark

    results_dir = str(paper_dir / "results")

    for algo in config["algorithms"]:
        print(f"\n{'='*60}")
        print(f"Running benchmark: {algo}")
        print(f"{'='*60}")
        summary = run_benchmark(config, algo, results_dir)
        print(f"\nSummary: {json.dumps(summary, indent=2)}")

    # Update plan.json
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        plan.setdefault("workflow_log", []).append({
            "stage": "benchmark",
            "status": "completed",
            "date": datetime.now().isoformat(),
            "algorithms": config["algorithms"],
        })
        with open(plan_file, "w") as f:
            json.dump(plan, f, indent=2)


def cmd_figures(args):
    """Generate figures from benchmark results."""
    paper_dir = Path(args.paper_dir)
    results_dir = paper_dir / "results"
    figures_dir = paper_dir / "figures"
    figures_dir.mkdir(exist_ok=True)

    # Try running a generate_figures script if one exists in the paper
    fig_script = paper_dir / "step2_analysis" / "02_generate_figures.py"
    if not fig_script.exists():
        fig_script = paper_dir / "generate_figures.py"

    if fig_script.exists():
        print(f"Running figure generation script: {fig_script}")
        import subprocess
        result = subprocess.run(
            [sys.executable, str(fig_script)],
            cwd=str(paper_dir),
            capture_output=True, text=True,
        )
        print(result.stdout)
        if result.returncode != 0:
            print(f"ERROR: {result.stderr}")
            sys.exit(1)
        print(f"Figures generated in {figures_dir}")
    else:
        print(f"No generate_figures.py found. Available result files:")
        for f in sorted(results_dir.glob("*.json")):
            print(f"  {f.name}")
        if (results_dir / "raw").exists():
            for f in sorted((results_dir / "raw").glob("*.jsonl")):
                print(f"  raw/{f.name}")
        print()
        print("To auto-generate figures, create one of:")
        print(f"  {paper_dir / 'generate_figures.py'}")
        print(f"  {paper_dir / 'step2_analysis' / '02_generate_figures.py'}")


def cmd_validate_figures(args):
    """Validate figure formats against journal requirements."""
    paper_dir = Path(args.paper_dir)
    journal = args.journal

    sys.path.insert(0, str(TOOLS_DIR))
    from paper_renderer import load_journal_profile
    from figure_validator import validate_figures, print_validation_report

    profile = load_journal_profile(journal, str(JOURNALS_DIR))
    figures_dir = paper_dir / "figures"

    print("Validating figures...")
    issues = validate_figures(str(figures_dir), profile)
    print_validation_report(issues)

    if any(i["severity"] == "FAIL" for i in issues):
        sys.exit(1)


def cmd_format(args):
    """Format manuscript for target journal."""
    paper_dir = Path(args.paper_dir)
    journal = args.journal

    sys.path.insert(0, str(TOOLS_DIR))
    from paper_renderer import load_journal_profile, render_latex, check_compliance, print_compliance

    profile = load_journal_profile(journal, str(JOURNALS_DIR))

    # Check compliance first
    print("Checking compliance...")
    checks = check_compliance(str(paper_dir), profile)
    print_compliance(checks)

    # Render LaTeX
    print("\nRendering LaTeX...")
    tex_file = render_latex(str(paper_dir), profile)
    print(f"Output: {tex_file}")

    # Render Word document
    print("\nRendering Word document...")
    from word_renderer import render_word_document
    docx_file = render_word_document(str(paper_dir), journal_profile=profile)
    print(f"Word:   {docx_file}")

    # Render PDF (via typst)
    print("\nRendering PDF...")
    try:
        from render_pdf import render_pdf
        pdf_file = render_pdf(str(paper_dir))
        if pdf_file:
            print(f"PDF:    {pdf_file}")
        else:
            print("PDF:    generation failed (check pandoc/typst)")
    except ImportError:
        print("PDF:    skipped (install typst: pip install typst)")
    except Exception as exc:
        print(f"PDF:    failed — {exc}")


def cmd_audit(args):
    """Audit manuscript for unsupported claims."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from claim_tracer import audit_manuscript, print_report

    report = audit_manuscript(str(paper_dir))
    print_report(report)

    if report["verdict"] != "PASS":
        sys.exit(1)


def cmd_draft(args):
    """Generate a manuscript draft from plan.json, results.json, and template.

    This produces a structured draft that fills in the paper skeleton with
    actual data from benchmark results, plan metadata, and approved claims.
    The draft is a starting point for iterative refinement with the user.
    """
    paper_dir = Path(args.paper_dir)

    plan_file = paper_dir / "plan.json"
    results_file = paper_dir / "results.json"
    paper_file = paper_dir / "paper.md"

    if not plan_file.exists():
        print(f"Error: plan.json not found in {paper_dir}")
        sys.exit(1)

    with open(plan_file) as f:
        plan = json.load(f)

    # Load results if available
    results = {}
    if results_file.exists():
        with open(results_file) as f:
            results = json.load(f)

    # Load journal profile
    journal_name = plan.get("target_journal", "")
    profile = None
    if journal_name:
        profile_path = JOURNALS_DIR / f"{journal_name}.yaml"
        if profile_path.exists():
            try:
                import yaml
                with open(profile_path) as f:
                    profile = yaml.safe_load(f)
            except ImportError:
                print("Warning: PyYAML not installed - journal profile not loaded")

    paper_type = plan.get("paper_type", "characterization")
    title = plan.get("title", "Untitled")

    # Check if paper.md already exists and is beyond skeleton
    if paper_file.exists():
        existing = paper_file.read_text(encoding="utf-8")
        if "TODO" not in existing and len(existing) > 2000:
            if not args.force:
                print(f"paper.md already contains a draft ({len(existing)} chars).")
                print("Use --force to overwrite, or use 'iterate' to refine.")
                return

    # Build draft sections
    sections = []

    # Title
    sections.append(f"# {title}\n")

    # Metadata comments
    sections.append(f"<!-- Target Journal: {journal_name} -->")
    sections.append(f"<!-- Generated: {datetime.now().strftime('%Y-%m-%d')} -->")
    sections.append(f"<!-- Paper Type: {paper_type} -->")
    sections.append(f"<!-- Status: DRAFT -->\n")

    # Highlights
    sections.append("## Highlights\n")
    key_results = results.get("key_results", {})
    if key_results:
        highlight_count = 0
        for key, val in key_results.items():
            if highlight_count >= 5:
                break
            label = key.replace("_", " ").title()
            sections.append(f"- {label}: {val}")
            highlight_count += 1
    else:
        sections.append("- TODO: First highlight (max 85 chars)")
        sections.append("- TODO: Second highlight")
        sections.append("- TODO: Third highlight")
    sections.append("")

    # Abstract
    abstract_limit = 250
    if profile:
        abstract_limit = profile.get("abstract_words_max", 250)
    sections.append("## Abstract\n")
    approach = results.get("approach", "")
    conclusions = results.get("conclusions", "")
    if approach and conclusions:
        sections.append(f"{approach}\n")
        sections.append(f"{conclusions}\n")
        sections.append(f"<!-- Abstract word limit: {abstract_limit} words -->\n")
    else:
        sections.append(f"TODO: Write abstract (max {abstract_limit} words).\n")

    # Keywords
    sections.append("## Keywords\n")
    sections.append("TODO: 3-7 keywords separated by semicolons\n")

    sections.append("---\n")

    # Introduction
    sections.append("## 1. Introduction\n")
    sections.append("### 1.1 Background\n")
    novelty = plan.get("novelty_statement", "")
    if novelty:
        sections.append(f"<!-- Novelty: {novelty} -->\n")
    sections.append("TODO: Frame the scientific/algorithmic problem.\n")
    sections.append("### 1.2 Prior Work\n")
    sections.append("TODO: Survey existing approaches. Cite algorithms, not software.\n")
    sections.append("### 1.3 Contributions\n")
    rqs = plan.get("research_questions", [])
    if rqs:
        sections.append("This paper addresses the following research questions:\n")
        for rq in rqs:
            sections.append(f"- **{rq.get('id', '?')}**: {rq.get('question', 'TODO')}")
        sections.append("")
    else:
        sections.append("TODO: List specific contributions.\n")

    # Mathematical Framework
    sections.append("---\n")
    sections.append("## 2. Mathematical Framework\n")
    sections.append("TODO: Pure mathematics - equations, derivations, proofs.\n")

    # Algorithm / Methods
    sections.append("---\n")
    if paper_type in ("comparative", "method"):
        sections.append("## 3. Algorithm Description\n")
        sections.append("### 3.1 Baseline Algorithm\n")
        sections.append("TODO: Describe the baseline.\n")
        sections.append("### 3.2 Proposed Improvement\n")
        sections.append("TODO: Describe the modification.\n")
    elif paper_type == "characterization":
        sections.append("## 3. Algorithm Description\n")
        sections.append("TODO: Describe the algorithm under study.\n")
    else:
        sections.append("## 3. Methods\n")
        sections.append("TODO: Describe the computational methods.\n")

    # Benchmark Design
    sections.append("---\n")
    sections.append("## 4. Benchmark Design\n")
    bench = plan.get("benchmark_design", {})
    if bench:
        families = bench.get("fluid_families", [])
        if families:
            sections.append(f"The benchmark covers {len(families)} fluid families: "
                          f"{', '.join(families)}.\n")
        metrics = bench.get("metrics", [])
        if metrics:
            sections.append(f"Metrics collected: {', '.join(metrics)}.\n")
    else:
        sections.append("TODO: Describe benchmark design.\n")

    # Results and Discussion
    sections.append("---\n")
    sections.append("## 5. Results and Discussion\n")
    if key_results:
        sections.append("### 5.1 Overall Performance\n")
        # Build a results table
        sections.append("| Metric | Value |")
        sections.append("|--------|-------|")
        for key, val in key_results.items():
            label = key.replace("_", " ").replace("pct", "%").title()
            sections.append(f"| {label} | {val} |")
        sections.append("")

    # Include custom tables from results.json
    for tbl in results.get("tables", []):
        sections.append(f"\n**{tbl.get('title', 'Table')}**\n")
        headers = tbl.get("headers", [])
        if headers:
            sections.append("| " + " | ".join(str(h) for h in headers) + " |")
            sections.append("| " + " | ".join("---" for _ in headers) + " |")
            for row in tbl.get("rows", []):
                sections.append("| " + " | ".join(str(c) for c in row) + " |")
            sections.append("")

    # Figures
    fig_captions = results.get("figure_captions", {})
    if fig_captions:
        sections.append("### 5.2 Figures\n")
        for i, (fname, caption) in enumerate(fig_captions.items(), 1):
            sections.append(f"![Figure {i}: {caption}](figures/{fname})\n")
            sections.append(f"*Figure {i}: {caption}*\n")

    if not key_results and not fig_captions:
        sections.append("TODO: Present and discuss results.\n")

    # Conclusions
    sections.append("---\n")
    sections.append("## 6. Conclusions\n")
    if conclusions:
        sections.append(f"{conclusions}\n")
    else:
        sections.append("TODO: Summarize findings and future work.\n")

    # Acknowledgements
    sections.append("---\n")
    sections.append("## Acknowledgements\n")
    sections.append("The algorithm is implemented in the open-source NeqSim library "
                  "(https://github.com/equinor/neqsim).\n")

    # Data Availability
    sections.append("## Data Availability\n")
    sections.append("Source code, benchmark configurations, raw results, and "
                  "figure-generation scripts are available at "
                  "https://github.com/equinor/neqsim under the MIT license.\n")

    # References
    sections.append("## References\n")
    refs_file = paper_dir / "refs.bib"
    if refs_file.exists():
        sections.append("<!-- References managed in refs.bib -->\n")
    else:
        sections.append("TODO: Add references.\n")

    # Write draft
    draft_text = "\n".join(sections)
    paper_file.write_text(draft_text, encoding="utf-8")

    # Update plan.json
    if plan_file.exists():
        plan.setdefault("workflow_log", []).append({
            "stage": "draft",
            "status": "generated",
            "date": datetime.now().isoformat(),
            "paper_type": paper_type,
            "results_available": bool(key_results),
        })
        with open(plan_file, "w") as f:
            json.dump(plan, f, indent=2)

    word_count = len(draft_text.split())
    print(f"Draft generated: {paper_file} ({word_count} words)")
    print()
    print("Next steps:")
    print("  1. Review and refine the draft manually or with an agent")
    print(f"  2. Run: python paperflow.py iterate {paper_dir} --check all")
    print(f"  3. Run: python paperflow.py audit {paper_dir}")
    print(f"  4. Run: python paperflow.py format {paper_dir} --journal {journal_name}")


def cmd_iterate(args):
    """Interactive iteration on a specific section or the full manuscript.

    Generates a structured feedback report showing what's missing, what needs
    improvement, and suggests specific actions. Designed for iterative
    refinement cycles between user and agent.
    """
    paper_dir = Path(args.paper_dir)
    check_type = getattr(args, 'check', 'all')

    paper_file = paper_dir / "paper.md"
    if not paper_file.exists():
        print(f"Error: paper.md not found. Run 'draft' first.")
        sys.exit(1)

    paper_text = paper_file.read_text(encoding="utf-8")

    # Load plan and results
    plan = {}
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)

    results = {}
    results_file = paper_dir / "results.json"
    if results_file.exists():
        with open(results_file) as f:
            results = json.load(f)

    # Load journal profile
    journal_name = plan.get("target_journal", "")
    profile = None
    if journal_name:
        profile_path = JOURNALS_DIR / f"{journal_name}.yaml"
        if profile_path.exists():
            try:
                import yaml
                with open(profile_path) as f:
                    profile = yaml.safe_load(f)
            except ImportError:
                pass

    paper_type = plan.get("paper_type", "characterization")
    feedback = []
    score = 0
    max_score = 0

    def add_check(category, test, msg_pass, msg_fail, weight=1):
        nonlocal score, max_score
        max_score += weight
        if test:
            score += weight
            feedback.append({"category": category, "status": "OK",
                           "message": msg_pass})
        else:
            feedback.append({"category": category, "status": "NEEDS_WORK",
                           "message": msg_fail})

    # ── Structure checks ──
    if check_type in ("all", "structure"):
        add_check("structure", "## Abstract" in paper_text or "## abstract" in paper_text.lower(),
              "Abstract section present", "Missing Abstract section")
        add_check("structure", "## Keywords" in paper_text or "## keywords" in paper_text.lower(),
              "Keywords section present", "Missing Keywords section")
        add_check("structure", "Introduction" in paper_text,
              "Introduction present", "Missing Introduction section")
        add_check("structure", "Conclusion" in paper_text,
              "Conclusions present", "Missing Conclusions section")
        add_check("structure", "References" in paper_text,
              "References present", "Missing References section")
        add_check("structure", "Acknowledgement" in paper_text,
              "Acknowledgements present", "Missing Acknowledgements section")

    # ── Completeness checks ──
    if check_type in ("all", "completeness"):
        todo_count = paper_text.count("TODO")
        add_check("completeness", todo_count == 0,
              "No TODO placeholders remain",
              f"{todo_count} TODO placeholders still in manuscript", weight=3)

        # Check highlights
        if profile and profile.get("highlights_required", False):
            highlight_section = re.search(
                r'## Highlights?\s*\n((?:- .+\n?)+)', paper_text, re.IGNORECASE)
            if highlight_section:
                hl_lines = [l.strip() for l in highlight_section.group(1).strip().split("\n")
                          if l.strip().startswith("-")]
                hl_min = profile.get("highlights_min", 3)
                hl_max = profile.get("highlights_max", 5)
                add_check("completeness", hl_min <= len(hl_lines) <= hl_max,
                      f"{len(hl_lines)} highlights (within {hl_min}-{hl_max})",
                      f"{len(hl_lines)} highlights (need {hl_min}-{hl_max})")

                max_chars = profile.get("highlights_max_chars_each", 85)
                over_limit = [l for l in hl_lines if len(l) - 2 > max_chars]
                add_check("completeness", len(over_limit) == 0,
                      f"All highlights within {max_chars} char limit",
                      f"{len(over_limit)} highlights exceed {max_chars} chars")

    # ── Evidence checks ──
    if check_type in ("all", "evidence"):
        key_results = results.get("key_results", {})

        if paper_type == "comparative":
            claim_refs = re.findall(r'\[Claim\s+C\d+', paper_text)
            add_check("evidence", len(claim_refs) > 0,
                  f"{len(claim_refs)} claim references found",
                  "No [Claim Cx] references - comparative papers need claim tracing",
                  weight=3)
        else:
            results_used = 0
            for key, val in key_results.items():
                val_str = str(val)
                if val_str in paper_text:
                    results_used += 1
            if key_results:
                pct = results_used / len(key_results) * 100
                add_check("evidence", pct >= 50,
                      f"{results_used}/{len(key_results)} key results referenced in text",
                      f"Only {results_used}/{len(key_results)} key results appear in text",
                      weight=2)

        fig_captions = results.get("figure_captions", {})
        if fig_captions:
            fig_refs = re.findall(r'[Ff]ig(?:ure)?\.?\s*\d+', paper_text)
            add_check("evidence", len(fig_refs) >= len(fig_captions),
                  f"{len(fig_refs)} figure references for {len(fig_captions)} figures",
                  f"Only {len(fig_refs)} figure refs for {len(fig_captions)} figures")

    # ── Writing quality checks ──
    if check_type in ("all", "writing"):
        abstract_match = re.search(
            r'## Abstract\s*\n(.*?)(?=\n## |\n---)', paper_text,
            re.DOTALL | re.IGNORECASE)
        if abstract_match:
            abstract_words = len(abstract_match.group(1).split())
            limit = 250
            if profile:
                limit = profile.get("abstract_words_max", 250)
            add_check("writing", abstract_words <= limit,
                  f"Abstract: {abstract_words} words (limit {limit})",
                  f"Abstract: {abstract_words} words exceeds {limit} limit")
            add_check("writing", abstract_words >= 100,
                  f"Abstract has sufficient length ({abstract_words} words)",
                  f"Abstract too short ({abstract_words} words, aim for 150+)")

        body_text = re.sub(
            r'## (?:Acknowledgements?|Data Availability).*$', '',
            paper_text, flags=re.DOTALL | re.IGNORECASE)
        body_neqsim = len(re.findall(r'\bNeqSim\b', body_text))
        add_check("writing", body_neqsim <= 3,
              f"NeqSim mentioned {body_neqsim} times in body (good: algorithm-first)",
              f"NeqSim mentioned {body_neqsim} times in body - use algorithm-first language",
              weight=2)

    # ── Print feedback report ──
    print("=" * 60)
    print("ITERATION FEEDBACK REPORT")
    print("=" * 60)
    print(f"Paper: {paper_dir.name}")
    print(f"Type:  {paper_type}")
    if max_score > 0:
        print(f"Score: {score}/{max_score} ({score / max_score * 100:.0f}%)")
    print()

    categories = {}
    for item in feedback:
        categories.setdefault(item["category"], []).append(item)

    for cat, items in categories.items():
        print(f"  [{cat.upper()}]")
        for item in items:
            icon = "  [OK]" if item["status"] == "OK" else "  [!!]"
            print(f"    {icon} {item['message']}")
        print()

    needs_work = [f for f in feedback if f["status"] == "NEEDS_WORK"]
    if needs_work:
        print("SUGGESTED NEXT ACTIONS:")
        for i, item in enumerate(needs_work, 1):
            print(f"  {i}. {item['message']}")
        print()
        print("Refine the manuscript and run 'iterate' again to check progress.")
    else:
        print("All checks pass! Ready for:")
        print(f"  python paperflow.py audit {paper_dir}")
        print(f"  python paperflow.py format {paper_dir} --journal {journal_name}")

    # Save feedback to file for agent consumption
    feedback_file = paper_dir / "iteration_feedback.json"
    feedback_data = {
        "timestamp": datetime.now().isoformat(),
        "score": score,
        "max_score": max_score,
        "score_pct": round(score / max_score * 100, 1) if max_score > 0 else 0,
        "paper_type": paper_type,
        "feedback": feedback,
        "needs_work": needs_work,
    }
    with open(feedback_file, "w") as f:
        json.dump(feedback_data, f, indent=2)

    plan.setdefault("workflow_log", []).append({
        "stage": "iterate",
        "status": "checked",
        "date": datetime.now().isoformat(),
        "score_pct": feedback_data["score_pct"],
        "issues": len(needs_work),
    })
    if plan_file.exists():
        with open(plan_file, "w") as f:
            json.dump(plan, f, indent=2)


def cmd_revise(args):
    """Process reviewer comments and prepare revision workspace."""
    paper_dir = Path(args.paper_dir)
    comments_file = Path(args.comments) if hasattr(args, 'comments') and args.comments else None

    # Determine revision number
    existing_revisions = sorted(paper_dir.glob("revision_*"))
    rev_num = len(existing_revisions) + 1
    rev_dir = paper_dir / f"revision_{rev_num}"
    rev_dir.mkdir(exist_ok=True)

    # Copy or create reviewer comments file
    if comments_file and comments_file.exists():
        import shutil
        shutil.copy2(str(comments_file), str(rev_dir / "reviewer_comments.md"))
        print(f"Copied reviewer comments to {rev_dir / 'reviewer_comments.md'}")
    else:
        template = (
            f"# Reviewer Comments - Revision {rev_num}\n\n"
            "## Reviewer 1\n\n### Comment 1.1\n> Paste reviewer comment here\n\n"
            "### Comment 1.2\n> Paste reviewer comment here\n\n"
            "## Reviewer 2\n\n### Comment 2.1\n> Paste reviewer comment here\n"
        )
        (rev_dir / "reviewer_comments.md").write_text(template, encoding="utf-8")
        print(f"Created template: {rev_dir / 'reviewer_comments.md'}")
        print("Paste the actual reviewer comments into this file.")

    # Create response template
    response_template_path = PAPERLAB_ROOT / "templates" / "response_to_reviewers.md"
    if response_template_path.exists():
        response_text = response_template_path.read_text(encoding="utf-8")
    else:
        response_text = "# Response to Reviewers\n\n## Reviewer 1\n\nTODO\n"
    (rev_dir / "response_to_reviewers.md").write_text(response_text, encoding="utf-8")

    # Create revision plan
    rev_plan = {
        "revision_number": rev_num,
        "created": datetime.now().isoformat(),
        "status": "in_progress",
        "comments_parsed": False,
        "experiments_needed": False,
        "new_experiments": [],
        "sections_modified": [],
    }
    with open(rev_dir / "revision_plan.json", "w") as f:
        json.dump(rev_plan, f, indent=2)

    # Copy current paper.md as baseline
    paper_file = paper_dir / "paper.md"
    if paper_file.exists():
        import shutil
        shutil.copy2(str(paper_file), str(rev_dir / f"paper_r{rev_num - 1}_baseline.md"))

    # Update plan.json
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        plan.setdefault("workflow_log", []).append({
            "stage": f"revision_{rev_num}",
            "status": "started",
            "date": datetime.now().isoformat(),
        })
        with open(plan_file, "w") as f:
            json.dump(plan, f, indent=2)

    print(f"\nRevision {rev_num} workspace created: {rev_dir}")
    print()
    print("Next steps:")
    print(f"  1. Edit {rev_dir / 'reviewer_comments.md'} with actual comments")
    print(f"  2. Use @reviewer-response agent to parse and classify comments")
    print(f"  3. Fill in {rev_dir / 'response_to_reviewers.md'}")
    print(f"  4. Edit {paper_file} with revisions")
    print(f"  5. Run: python paperflow.py iterate {paper_dir}")


def cmd_status(args):
    """Show current status of a paper project."""
    paper_dir = Path(args.paper_dir)

    print(f"Paper: {paper_dir.name}")
    print(f"{'='*60}")

    # Plan
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)
        print(f"  Title:   {plan.get('title', 'Unknown')}")
        print(f"  Journal: {plan.get('target_journal', 'Unknown')}")
        print(f"  Status:  {plan.get('status', 'Unknown')}")
        print(f"  RQs:     {len(plan.get('research_questions', []))}")
        print()

        # Workflow log
        log = plan.get("workflow_log", [])
        if log:
            print("  Workflow log:")
            for entry in log:
                print(f"    [{entry.get('date', '?')[:10]}] "
                      f"{entry.get('stage', '?')} — {entry.get('status', '?')}")
    else:
        print("  No plan.json found")

    print()

    # Check which files exist
    files_to_check = [
        ("plan.json", "Research plan"),
        ("benchmark_config.json", "Benchmark config"),
        ("paper.md", "Manuscript"),
        ("refs.bib", "References"),
        ("approved_claims.json", "Approved claims"),
        ("claims_manifest.json", "Claims manifest"),
        ("literature_map.md", "Literature review"),
        ("gap_statement.md", "Gap statement"),
        ("validation_report.md", "Validation report"),
    ]

    print("  Files:")
    for filename, desc in files_to_check:
        exists = (paper_dir / filename).exists()
        icon = "[x]" if exists else "[ ]"
        print(f"    {icon} {desc:25s} ({filename})")

    # Check results
    results_dir = paper_dir / "results"
    if results_dir.exists():
        summaries = list(results_dir.glob("summary_*.json"))
        raw_files = list((results_dir / "raw").glob("*.jsonl")) if (results_dir / "raw").exists() else []
        print(f"\n  Results: {len(summaries)} summaries, {len(raw_files)} raw files")
        for s in summaries:
            with open(s) as f:
                data = json.load(f)
            print(f"    {s.name}: {data.get('converged', '?')}/{data.get('total_cases', '?')} "
                  f"({data.get('convergence_rate_pct', '?')}%)")

    # Check figures
    fig_dir = paper_dir / "figures"
    if fig_dir.exists():
        figs = list(fig_dir.glob("*.png")) + list(fig_dir.glob("*.pdf"))
        print(f"\n  Figures: {len(figs)}")

    print()


def cmd_validate_bib(args):
    """Validate bibliography against entry requirements and manuscript citations."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from bib_validator import validate_bibliography, print_validation_report

    bib_path = paper_dir / "refs.bib"
    manuscript_path = paper_dir / "paper.md"

    issues = validate_bibliography(
        str(bib_path),
        manuscript_path=str(manuscript_path) if manuscript_path.exists() else None,
    )
    print_validation_report(issues)

    if any(i["severity"] == "FAIL" for i in issues):
        sys.exit(1)


def cmd_render(args):
    """Render manuscript to all submission formats (HTML, LaTeX, Word)."""
    paper_dir = Path(args.paper_dir)

    if not (paper_dir / "paper.md").exists():
        print(f"Error: paper.md not found in {paper_dir}")
        sys.exit(1)

    sys.path.insert(0, str(TOOLS_DIR))

    # Load journal from plan.json
    plan = {}
    plan_file = paper_dir / "plan.json"
    if plan_file.exists():
        with open(plan_file) as f:
            plan = json.load(f)

    journal_name = getattr(args, 'journal', None) or plan.get("target_journal", "")

    # Render HTML via render_all.py
    from render_all import render_html as render_html_func
    print("Rendering HTML...")
    html_file = render_html_func(str(paper_dir))
    print(f"  Output: {html_file}")

    # Render Word + LaTeX if journal profile available
    if journal_name:
        from paper_renderer import load_journal_profile, render_latex
        try:
            profile = load_journal_profile(journal_name, str(JOURNALS_DIR))

            print("Rendering LaTeX...")
            tex_file = render_latex(str(paper_dir), profile)
            print(f"  Output: {tex_file}")

            print("Rendering Word...")
            from word_renderer import render_word_document
            docx_file = render_word_document(str(paper_dir), journal_profile=profile)
            print(f"  Output: {docx_file}")
        except Exception as e:
            print(f"  Warning: LaTeX/Word rendering failed: {e}")
    else:
        print("  Skipping LaTeX/Word (no journal profile)")

    # Render PDF (via typst) — always attempted, does not need journal profile
    print("Rendering PDF...")
    try:
        from render_pdf import render_pdf
        pdf_file = render_pdf(str(paper_dir))
        if pdf_file:
            print(f"  Output: {pdf_file}")
        else:
            print("  PDF generation failed (check pandoc/typst)")
    except ImportError:
        print("  Skipped PDF (install typst: pip install typst)")
    except Exception as exc:
        print(f"  PDF failed: {exc}")

    print("\nDone. Files in:", paper_dir / "submission")


def cmd_check_prose(args):
    """Analyze manuscript prose quality (readability, passive voice, hedging)."""
    paper_dir = Path(args.paper_dir)
    paper_path = paper_dir / "paper.md"

    if not paper_path.exists():
        print(f"Error: paper.md not found in {paper_dir}")
        sys.exit(1)

    sys.path.insert(0, str(TOOLS_DIR))
    from prose_quality import analyze_prose, print_prose_report

    report = analyze_prose(str(paper_path))
    print_prose_report(report)

    # Fail if overall score is very low
    overall = report.get("summary", {}).get("overall", 100)
    if overall < 40:
        print(f"\nOverall prose score {overall}/100 is below minimum threshold (40).")
        sys.exit(1)


def cmd_suggest_refs(args):
    """Suggest missing references via Semantic Scholar."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from citation_discovery import suggest_citations, print_suggestions

    report = suggest_citations(str(paper_dir), max_suggestions=args.max)
    print_suggestions(report)


def cmd_diff(args):
    """Generate a visual diff between manuscript revisions."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from revision_diff import generate_diff, print_diff_summary

    report = generate_diff(
        str(paper_dir),
        revision=args.revision,
        old_file=args.old,
        new_file=args.new,
    )
    print_diff_summary(report)


def cmd_scan(args):
    """Scan for scientific paper opportunities (trending or codebase)."""
    sys.path.insert(0, str(TOOLS_DIR))

    scan_dir = PAPERS_DIR / "_research_scan"
    scan_dir.mkdir(parents=True, exist_ok=True)

    if getattr(args, "trending", False) or not getattr(args, "legacy", False):
        # ── Trending mode (default) ──
        from trending_topics import (
            daily_suggestion,
            format_daily_suggestion,
            generate_markdown_suggestion,
            scan_trending,
        )

        if getattr(args, "full", False):
            # Full trending scan
            report = scan_trending(top_n=args.top, rate_limit_delay=1.2)
            for i, opp in enumerate(report["opportunities"], 1):
                ip = opp.get("inspiring_paper", {})
                print(f"  {i:3d}. [{opp['trend_score']:3d}] [{opp['domain']}] "
                      f"{opp['title']}")
                print(f"       Inspired by: {ip.get('title', 'N/A')[:60]}")
            output_path = args.output or str(scan_dir / "trending_opportunities.json")
            with open(output_path, "w", encoding="utf-8") as f:
                json.dump(report, f, indent=2, default=str)
            print(f"\n  Saved to: {output_path}")
        else:
            # Daily pick (default)
            pick = daily_suggestion(
                history_dir=str(scan_dir),
                scan_kwargs={"top_n": 30, "rate_limit_delay": 1.2},
            )
            if not pick:
                print("  No trending suggestions (API may be unavailable).")
                print("  Try: paperflow scan --legacy")
                return
            print(format_daily_suggestion(pick))

            output_path = args.output or str(scan_dir / "daily_suggestion.json")
            with open(output_path, "w", encoding="utf-8") as f:
                json.dump(pick, f, indent=2, default=str)
            md_path = scan_dir / "daily_suggestion.md"
            with open(str(md_path), "w", encoding="utf-8") as f:
                f.write(generate_markdown_suggestion(pick))
            print(f"\n  Saved to: {output_path}")
    else:
        # ── Legacy codebase mode ──
        from research_scanner import scan_opportunities, print_scan_report

        repo_root = args.repo or str(PAPERLAB_ROOT.parent)
        report = scan_opportunities(
            repo_root,
            since_days=args.since,
            top_n=args.top,
            check_literature=args.literature,
        )
        print_scan_report(report, verbose=args.verbose)

        output_path = args.output or str(scan_dir / "opportunities.json")
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2, default=str)
        print(f"\n  Saved to: {output_path}")


def cmd_stats(args):
    """Run statistical tests on benchmark results."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from statistical_tests import analyze_benchmarks, print_stats_report

    report = analyze_benchmarks(str(paper_dir))
    print_stats_report(report)


def cmd_check_plagiarism(args):
    """Check manuscript for self-plagiarism against other papers."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from self_plagiarism_checker import check_self_plagiarism, print_plagiarism_report

    report = check_self_plagiarism(
        str(paper_dir),
        doc_threshold=args.doc_threshold,
        para_threshold=args.para_threshold,
    )
    print_plagiarism_report(report)

    max_sim = report.get("max_document_similarity", 0)
    if max_sim > args.doc_threshold:
        print(f"\nDocument similarity {max_sim:.2f} exceeds threshold ({args.doc_threshold}).")
        sys.exit(1)


def cmd_manifest(args):
    """Generate a reproducibility manifest for the paper."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from reproducibility_manifest import generate_manifest, print_manifest_report

    report = generate_manifest(str(paper_dir))
    print_manifest_report(report)


def cmd_verify_manifest(args):
    """Verify an existing reproducibility manifest."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from reproducibility_manifest import verify_manifest, print_manifest_report

    report = verify_manifest(str(paper_dir))
    print_manifest_report(report)

    if not report.get("all_match", True):
        print("\nManifest verification FAILED — artifacts have changed.")
        sys.exit(1)


def cmd_graphical_abstract(args):
    """Generate a graphical abstract from paper artifacts."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from graphical_abstract import generate_graphical_abstract, print_abstract_report

    report = generate_graphical_abstract(str(paper_dir))
    print_abstract_report(report)


def cmd_credit(args):
    """Generate CRediT author contribution statement."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from credit_generator import generate_credit, print_credit_report

    contributors = None
    if args.contributors:
        contributors = json.loads(args.contributors)

    report = generate_credit(str(paper_dir), contributors=contributors)
    print_credit_report(report)


def cmd_nomenclature(args):
    """Extract nomenclature table from manuscript."""
    paper_dir = Path(args.paper_dir)
    paper_path = paper_dir / "paper.md"

    if not paper_path.exists():
        print(f"Error: paper.md not found in {paper_dir}")
        sys.exit(1)

    sys.path.insert(0, str(TOOLS_DIR))
    from nomenclature_extractor import extract_nomenclature, print_nomenclature_report

    report = extract_nomenclature(str(paper_path))
    print_nomenclature_report(report)


def cmd_related_work(args):
    """Generate related work comparison table."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from related_work_table import generate_related_work_table, print_related_work_report

    report = generate_related_work_table(str(paper_dir))
    print_related_work_report(report)


def cmd_latex(args):
    """Compile manuscript to LaTeX/PDF via Pandoc."""
    paper_dir = Path(args.paper_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from latex_pipeline import compile_latex, print_latex_report

    report = compile_latex(
        str(paper_dir),
        journal=args.journal or "generic",
        output_format=args.output_format or "both",
        engine=args.engine,
    )
    print_latex_report(report)


def cmd_verify_dois(args):
    """Verify DOIs in bibliography resolve correctly."""
    paper_dir = Path(args.paper_dir)
    bib_path = paper_dir / "refs.bib"

    if not bib_path.exists():
        print(f"Error: refs.bib not found in {paper_dir}")
        sys.exit(1)

    sys.path.insert(0, str(TOOLS_DIR))
    from bib_validator import verify_dois, print_doi_report

    results = verify_dois(str(bib_path))
    print_doi_report(results)

    broken = sum(1 for r in results if r["status"] == "broken")
    if broken > 0:
        print(f"\n{broken} broken DOI(s) found.")
        sys.exit(1)


# ═══════════════════════════════════════════════════════════════════════════
# Evaluate commands — automated figure & result evaluation
# ═══════════════════════════════════════════════════════════════════════════

def cmd_evaluate_figures(args):
    """Evaluate all figures in a paper or book chapter."""
    target_dir = Path(args.target_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from figure_evaluator import (evaluate_all_figures,
                                  print_evaluation_report,
                                  save_evaluation_report)

    provider = getattr(args, "provider", "openai")
    model = getattr(args, "model", "gpt-4o")
    skip_llm = getattr(args, "skip_llm", False)
    skip_ocr = getattr(args, "skip_ocr", False)

    print(f"Evaluating figures in {target_dir}...")
    if skip_llm:
        print("  (LLM critique disabled — technical checks only)")

    reports = evaluate_all_figures(
        str(target_dir),
        provider=provider, model=model,
        skip_llm=skip_llm, skip_ocr=skip_ocr,
    )

    if not reports:
        print("No figures found to evaluate.")
        return

    print_evaluation_report(reports)

    # Save JSON report
    output_file = target_dir / "figure_evaluation.json"
    save_evaluation_report(reports, output_file)
    print(f"Report saved to {output_file}")


def cmd_evaluate_results(args):
    """Check result consistency and optionally run LLM quality assessment."""
    target_dir = Path(args.target_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from result_evaluator import (check_result_consistency,
                                  print_consistency_report,
                                  save_consistency_report,
                                  evaluate_results_quality,
                                  print_quality_report)

    # Always run consistency check (free, fast)
    print(f"Checking result consistency in {target_dir}...")
    report = check_result_consistency(str(target_dir))
    print_consistency_report(report)

    # Save consistency report
    output_file = target_dir / "result_consistency.json"
    save_consistency_report(report, output_file)
    print(f"Consistency report saved to {output_file}")

    # LLM quality assessment (optional)
    skip_llm = getattr(args, "skip_llm", False)
    if not skip_llm:
        provider = getattr(args, "provider", "openai")
        model = getattr(args, "model", "gpt-4o")
        print(f"\nRunning LLM quality assessment ({provider}/{model})...")
        quality = evaluate_results_quality(
            str(target_dir), provider=provider, model=model
        )
        print_quality_report(quality)

    if report.verdict == "FAIL":
        sys.exit(1)


def cmd_generate_context(args):
    """Auto-generate discussion context for figures."""
    target_dir = Path(args.target_dir)

    sys.path.insert(0, str(TOOLS_DIR))
    from result_evaluator import generate_figure_context

    provider = getattr(args, "provider", "openai")
    model = getattr(args, "model", "gpt-4o")
    use_vision = not getattr(args, "no_vision", False)

    print(f"Generating figure context for {target_dir}...")
    contexts = generate_figure_context(
        str(target_dir), provider=provider, model=model,
        use_vision=use_vision,
    )

    if not contexts:
        print("No figures found.")
        return

    # Print and save
    for ctx in contexts:
        print(f"\n  [{ctx.figure_name}]")
        if ctx.generated_caption:
            print(f"    Caption: {ctx.generated_caption}")
        if ctx.observation:
            print(f"    Observation: {ctx.observation[:120]}...")
        if ctx.mechanism:
            print(f"    Mechanism: {ctx.mechanism[:120]}...")
        if ctx.implication:
            print(f"    Implication: {ctx.implication[:120]}...")
        if ctx.recommendation:
            print(f"    Recommendation: {ctx.recommendation[:120]}...")

    # Save to JSON
    import dataclasses
    output_file = target_dir / "figure_contexts.json"
    output_data = [dataclasses.asdict(c) for c in contexts]
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output_data, f, indent=2, default=str)
    print(f"\nContexts saved to {output_file}")


def cmd_book_source_inventory(args):
    """Inventory course/source files for a book."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_source_inventory

    manifest = build_source_inventory(
        args.book_dir,
        args.source_root,
        hash_limit_mb=getattr(args, "hash_limit_mb", 20.0),
    )
    print(f"Source inventory written for {manifest['total_files']} files.")
    print(f"  JSON: {Path(args.book_dir) / 'source_manifest.json'}")
    print(f"  Markdown: {Path(args.book_dir) / 'source_manifest.md'}")


def cmd_book_figure_dossier(args):
    """Build a book-level figure dossier."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_figure_dossier

    dossier = build_figure_dossier(
        args.book_dir,
        source_root=getattr(args, "source_root", None),
        use_vision=getattr(args, "vision", False),
        provider=getattr(args, "provider", "openai"),
        model=getattr(args, "model", "gpt-4o"),
    )
    summary = dossier["summary"]
    print(f"Figure dossier written for {dossier['figure_count']} figures.")
    print(f"  Missing files: {summary['missing_files']}")
    print(f"  Without discussion: {summary['without_discussion']}")
    print(f"  Without number: {summary.get('without_number', 0)}")
    print(f"  Without reference: {summary.get('without_reference', 0)}")
    print(f"  Weak captions: {summary['weak_captions']}")
    print(f"  JSON: {Path(args.book_dir) / 'figure_dossier.json'}")
    print(f"  HTML: {Path(args.book_dir) / 'figure_dossier.html'}")


def cmd_book_apply_figure_context(args):
    """Apply reviewed figure context from figure_dossier.json."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import apply_figure_context

    result = apply_figure_context(
        args.book_dir,
        reviewed_only=getattr(args, "reviewed_only", True),
        dry_run=getattr(args, "dry_run", False),
    )
    action = "Would insert" if result["dry_run"] else "Inserted"
    print(f"{action} {result['inserted']} discussion block(s).")
    for changed in result["changed"]:
        print(f"  {changed['chapter']}: {changed['inserted']}")


def cmd_book_normalize_figures(args):
    """Normalize book figures to numbered captions and discussion references."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import normalize_figure_references

    result = normalize_figure_references(
        args.book_dir,
        dry_run=getattr(args, "dry_run", False),
    )
    action = "Would update" if result["dry_run"] else "Updated"
    print(f"{action} {result['chapters_changed']} chapter(s).")
    print(f"  Figures checked: {result['figures_checked']}")
    print(f"  Caption updates: {result['caption_updates']}")
    print(f"  Discussion heading updates: {result['discussion_updates']}")
    print(f"  Prose reference updates: {result['reference_updates']}")
    print(f"  Duplicate italic captions removed: {result['duplicate_captions_removed']}")
    for changed in result["changed"]:
        print(
            f"  {changed['chapter']}: captions={changed['caption_updates']}, "
            f"discussions={changed['discussion_updates']}, "
            f"references={changed['reference_updates']}, "
            f"duplicates={changed['duplicate_captions_removed']}"
        )


def cmd_book_coverage_audit(args):
    """Audit source coverage against the book coverage matrix."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_coverage_audit

    audit = build_coverage_audit(args.book_dir, args.source_root)
    print(f"Coverage audit written: {Path(args.book_dir) / 'coverage_audit.md'}")
    print(f"  Lecture folders: {len(audit['lecture_folders'])}")
    print(f"  Unmapped lecture folders: {len(audit['unmapped_lecture_folders'])}")
    print(f"  Exercise PDFs: {audit['exercise_pdf_count']}")
    print(f"  Exam PDFs: {audit['exam_pdf_count']}")


def cmd_book_lecture_topic_coverage(args):
    """Build lecture-deck topic coverage and optionally patch chapter checkpoints."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_lecture_topic_coverage

    report = build_lecture_topic_coverage(
        args.book_dir,
        apply_checkpoints=getattr(args, "apply", False),
        max_topics_per_deck=getattr(args, "max_topics_per_deck", 120),
    )
    summary = report["summary"]
    print(f"Lecture topic coverage written: {Path(args.book_dir) / 'lecture_topic_coverage.md'}")
    if getattr(args, "apply", False):
        print(f"  Chapter checkpoints updated and appendix written: {Path(args.book_dir) / 'backmatter' / 'lecture_coverage.md'}")
    print(f"  Lecture decks: {summary['lecture_decks']}")
    print(f"  Slide topics: {summary['topics']}")
    print(f"  Covered slide topics: {summary['covered_topics']}")
    print(f"  Topics needing review: {summary['topics_needing_review']}")
    print(f"  Coverage: {summary['coverage_pct']}%")


def cmd_book_lecture_figure_plan(args):
    """Build a review plan for lecture slides that should become chapter figures."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_lecture_figure_plan

    report = build_lecture_figure_plan(
        args.book_dir,
        max_slides_per_deck=getattr(args, "max_slides_per_deck", 12),
    )
    summary = report["summary"]
    print(f"Lecture figure plan written: {Path(args.book_dir) / 'lecture_figure_plan.md'}")
    print(f"  Lecture decks: {summary['lecture_decks']}")
    print(f"  Candidate slides: {summary['candidate_slides']}")
    print(f"  Rendered candidate slides: {summary['rendered_candidate_slides']}")


def cmd_book_evidence_check(args):
    """Run the book evidence and figure-context release gate."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_evidence_report

    report = build_evidence_report(args.book_dir)
    summary = report["summary"]
    print(f"Evidence report written: {Path(args.book_dir) / 'evidence_report.md'}")
    print(f"  Issues: {report['issue_count']}")
    print(f"  Errors: {summary.get('error', 0)}")
    print(f"  Warnings: {summary.get('warning', 0)}")
    print(f"  Info: {summary.get('info', 0)}")
    if getattr(args, "strict", False) and summary.get("error", 0) + summary.get("warning", 0) > 0:
        sys.exit(1)


def cmd_book_conciseness_audit(args):
    """Detect repeated text/figures and propose chapter consolidation."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_conciseness_audit

    audit = build_conciseness_audit(
        args.book_dir,
        min_words=getattr(args, "min_words", 18),
        paragraph_similarity_threshold=getattr(args, "paragraph_similarity", 0.82),
        chapter_similarity_threshold=getattr(args, "chapter_similarity", 0.44),
        max_pairs=getattr(args, "max_pairs", 80),
    )
    summary = audit["summary"]
    print(f"Conciseness audit written: {Path(args.book_dir) / 'conciseness_audit.md'}")
    print(f"  Words: {summary['word_count']:,}")
    print(f"  Exact duplicate paragraph groups: {summary['exact_duplicate_paragraph_groups']}")
    print(f"  Near-duplicate paragraph pairs: {summary['near_duplicate_paragraph_pairs']}")
    print(f"  Duplicate figure groups: {summary['duplicate_figure_groups']}")
    print(f"  Repeated heading groups: {summary['repeated_heading_groups']}")
    print(f"  Merge candidates: {summary['chapter_merge_candidates']}")
    print(f"  Suggested target chapters: {summary['suggested_target_chapters']}")


def cmd_book_apply_conciseness(args):
    """Apply safe conciseness edits to generated book appendices."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import apply_conciseness_edits

    result = apply_conciseness_edits(
        args.book_dir,
        min_block_words=getattr(args, "min_block_words", 250),
        max_topics=getattr(args, "max_topics", 10),
        dry_run=getattr(args, "dry_run", False),
    )
    action = "Would compress" if result["dry_run"] else "Compressed"
    print(f"{action} {result['chapters_changed']} generated lecture-topic block(s).")
    print(f"  Words before: {result['words_before']:,}")
    print(f"  Words after: {result['words_after']:,}")
    print(f"  Removed words: {result['removed_words']:,}")
    for changed in result["changed"]:
        print(f"  {changed['chapter']}: -{changed['removed_words']} words")


def cmd_book_skill_stack_plan(args):
    """Build a systematic skill-stack adoption plan for a book."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_skill_stack_plan

    plan = build_skill_stack_plan(args.book_dir)
    summary = plan["summary"]
    print(f"Skill-stack plan written: {Path(args.book_dir) / 'skill_stack_plan.md'}")
    print(f"  Ready dimensions: {summary['dimensions_ready']} / {summary['dimensions_total']}")
    print(f"  Figures: {summary['figures']}")
    print(f"  Notebooks: {summary['notebooks']}")


def cmd_book_standards_map(args):
    """Build a standards-to-chapter map for a book."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_standards_map

    report = build_standards_map(args.book_dir)
    summary = report["summary"]
    print(f"Standards map written: {Path(args.book_dir) / 'standards_map.md'}")
    print(f"  Chapters with standards: {summary['chapters_with_standards']}")
    print(f"  Unique standards: {summary['unique_standards']}")


def cmd_book_exam_alignment(args):
    """Build an exam and exercise alignment report for a book."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_exam_alignment

    report = build_exam_alignment(args.book_dir, source_root=getattr(args, "source_root", None))
    summary = report["summary"]
    print(f"Exam alignment written: {Path(args.book_dir) / 'exam_alignment.md'}")
    print(f"  Exam source files: {summary['exam_source_files']}")
    print(f"  Exercise source files: {summary['exercise_source_files']}")
    print(f"  Topics needing review: {summary['topics_needing_review']}")


def cmd_book_source_pdf_html_plan(args):
    """Build a source PDF-to-HTML conversion plan."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_source_pdf_html_plan

    plan = build_source_pdf_html_plan(args.book_dir, source_root=getattr(args, "source_root", None))
    print(f"Source PDF-to-HTML plan written: {Path(args.book_dir) / 'source_pdf_html_plan.md'}")
    print(f"  PDF files: {plan['summary']['pdf_files']}")
    for area, count in sorted(plan["summary"]["areas"].items()):
        print(f"  {area}: {count}")


def cmd_book_knowledge_graph(args):
    """Build a book knowledge graph from available book artifacts."""
    sys.path.insert(0, str(TOOLS_DIR))
    from book_improvement_tools import build_book_knowledge_graph

    graph = build_book_knowledge_graph(args.book_dir)
    summary = graph["summary"]
    print(f"Book knowledge graph written: {Path(args.book_dir) / 'book_knowledge_graph.html'}")
    print(f"  Nodes: {summary['nodes']}")
    print(f"  Edges: {summary['edges']}")


# ═══════════════════════════════════════════════════════════════════════════
# Book commands
# ═══════════════════════════════════════════════════════════════════════════

def cmd_book_new(args):
    """Create a new book project."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_builder import create_book_project

    try:
        book_dir = create_book_project(
            title=args.title,
            publisher=args.publisher,
            n_chapters=args.chapters,
            books_dir=BOOKS_DIR,
        )
        print(f"Book project created: {book_dir}")
        print(f"  Publisher: {args.publisher}")
        print(f"  Chapters:  {args.chapters}")
        print(f"\nNext steps:")
        print(f"  1. Edit book.yaml to set authors, titles, parts")
        print(f"  2. Write chapters in chapters/chNN/chapter.md")
        print(f"  3. Run: python paperflow.py book-status {book_dir}")
    except FileExistsError as exc:
        print(f"Error: {exc}")
        sys.exit(1)


def cmd_book_add_chapter(args):
    """Add a chapter to an existing book."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_builder import add_chapter

    try:
        ch_dir = add_chapter(
            book_dir=args.book_dir,
            title=args.title,
            part_index=args.part - 1,  # CLI is 1-based, internal is 0-based
        )
        print(f"Chapter added: {ch_dir}")
    except (FileNotFoundError, IndexError, ValueError) as exc:
        print(f"Error: {exc}")
        sys.exit(1)


def cmd_book_render(args):
    """Render book to PDF, Word, or HTML."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))

    fmt = args.out_format
    chapter = getattr(args, "chapter", None)

    if fmt == "pdf":
        from book_render_pdf import render_book_pdf
        result = render_book_pdf(args.book_dir, chapter_filter=chapter)
    elif fmt == "docx":
        from book_render_word import render_book_word
        result = render_book_word(args.book_dir, chapter_filter=chapter)
    elif fmt == "html":
        from book_render_html import render_book_html
        result = render_book_html(args.book_dir, chapter_filter=chapter)
    elif fmt == "odf":
        from book_render_odf import render_book_odf
        result = render_book_odf(args.book_dir, chapter_filter=chapter)
    elif fmt == "epub":
        from book_render_epub import render_book_epub
        result = render_book_epub(args.book_dir, chapter_filter=chapter)
    else:
        print(f"Unknown format: {fmt}")
        sys.exit(1)

    if result is None:
        print("Render failed.")
        sys.exit(1)


def cmd_book_preview(args):
    """Start a live HTML preview server."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_preview import serve
    serve(args.book_dir, port=args.port, open_browser=not args.no_open)


def cmd_book_enrich_bib(args):
    """Validate / enrich refs.bib via the Crossref API."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from bib_enrich import enrich_bibfile
    from pathlib import Path as _P

    bib_path = _P(args.book_dir) / "refs.bib"
    if not bib_path.exists():
        print(f"refs.bib not found in {args.book_dir}")
        sys.exit(1)
    enrich_bibfile(bib_path, in_place=args.in_place,
                   min_score=args.min_score, limit=args.limit)


def cmd_book_index(args):
    """Generate the back-of-book index from @index markers."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_index import write_index
    write_index(args.book_dir, force=not args.no_force)


def cmd_book_citation(args):
    """Generate the 'How to cite NeqSim' backmatter from CITATION.cff."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_citation_block import write_citation_appendix
    from pathlib import Path as _P
    cff = _P(args.cff) if args.cff else (PAPERLAB_ROOT.parent / "CITATION.cff")
    write_citation_appendix(args.book_dir, cff_path=cff)


def cmd_book_revision_diff(args):
    """Generate a track-changes report between two git revisions."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_revision_diff import write_diff_report
    write_diff_report(args.book_dir, args.ref_old, args.ref_new,
                      out_path=args.out)


def cmd_book_render_xml(args):
    """Render JATS or DocBook XML."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_render_xml import render_book_jats, render_book_docbook
    if args.fmt == "jats":
        render_book_jats(args.book_dir)
    else:
        render_book_docbook(args.book_dir)


def cmd_book_prose_review(args):
    """Run offline prose-review check on every chapter."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_prose_review import check_prose_review, format_prose_report
    rep = check_prose_review(args.book_dir)
    text = format_prose_report(rep)
    try:
        print(text)
    except UnicodeEncodeError:
        # Fallback for Windows cp1252 consoles: strip non-ASCII for stdout
        print(text.encode("ascii", errors="replace").decode("ascii"))
    if args.write:
        from pathlib import Path as _P
        out = _P(args.book_dir) / "submission" / "prose_review.md"
        out.parent.mkdir(exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"\nReport written to {out}")


def cmd_paper_render_journal(args):
    """Render a paper for a specific journal class (IEEE / ACS / Elsevier ...)."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from paper_journal_render import render_paper_journal
    render_paper_journal(args.paper_dir, journal=args.journal, out=args.out)



def cmd_book_check(args):
    """Run quality checks on a book."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_checker import run_checks, format_issues

    checks = [args.check] if args.check != "all" else None
    issues = run_checks(args.book_dir, checks=checks)
    print(format_issues(issues))

    errors = sum(1 for i in issues if i["severity"] == "error")
    if errors > 0:
        sys.exit(1)


def cmd_book_status(args):
    """Show book project status overview."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_builder import get_book_status

    try:
        status = get_book_status(args.book_dir)
    except (FileNotFoundError, ValueError) as exc:
        print(f"Error: {exc}")
        sys.exit(1)

    print(f"\nBook: {status['title']}")
    print(f"Publisher: {status['publisher']}")
    print(f"Chapters:  {status['total_chapters']}")
    print(f"Words:     {status['total_words']:,}")
    print(f"Est pages: ~{status['estimated_pages']}")
    print(f"TODOs:     {status['total_todos']}")
    print(f"Figures:   {status['total_figures']}")
    print(f"Notebooks: {status['total_notebooks']}")

    print(f"\n{'Ch':>3}  {'Words':>7}  {'TODOs':>5}  {'Figs':>4}  {'NBs':>3}  {'Pages':>5}  Title")
    print(f"{'─' * 3}  {'─' * 7}  {'─' * 5}  {'─' * 4}  {'─' * 3}  {'─' * 5}  {'─' * 30}")
    for ch in status["chapters"]:
        mark = "✓" if ch["exists"] and ch["todo_count"] == 0 else "·"
        print(f"{ch['number']:3d}  {ch['word_count']:7,}  {ch['todo_count']:5d}  "
              f"{ch['figure_count']:4d}  {ch['notebook_count']:3d}  "
              f"~{ch['estimated_pages']:4d}  {mark} {ch['title']}")


def cmd_book_toc(args):
    """Preview table of contents."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_builder import load_book_config, generate_toc, format_toc

    try:
        cfg = load_book_config(args.book_dir)
    except (FileNotFoundError, ValueError) as exc:
        print(f"Error: {exc}")
        sys.exit(1)

    toc = generate_toc(args.book_dir)
    print(f"\nTable of Contents — {cfg.get('title', 'Untitled')}\n")
    print(format_toc(toc))


def cmd_book_inject_citations(args):
    """Convert author-year prose references to \\cite{key} using refs.bib."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from citation_utils import parse_bibtex, inject_citations
    from book_builder import load_book_config, iter_chapters, resolve_chapter_dir

    book_dir = Path(args.book_dir)
    dry_run = getattr(args, "dry_run", False)
    chapter_filter = getattr(args, "chapter", None)

    bib_path = book_dir / "refs.bib"
    bib_entries = parse_bibtex(bib_path)
    if not bib_entries:
        print(f"No entries found in {bib_path}")
        sys.exit(1)

    cfg = load_book_config(book_dir)
    total = 0

    for ch_num, ch, part_title in iter_chapters(cfg):
        if chapter_filter and ch["dir"] != chapter_filter:
            continue
        ch_dir = resolve_chapter_dir(book_dir, ch)
        ch_md = ch_dir / "chapter.md"
        if not ch_md.exists():
            continue

        text = ch_md.read_text(encoding="utf-8")
        new_text, count = inject_citations(text, bib_entries)
        if count > 0:
            print(f"  Ch {ch_num:2d} ({ch['dir']}): {count} citation(s) injected")
            if not dry_run:
                ch_md.write_text(new_text, encoding="utf-8")
            total += count
        else:
            print(f"  Ch {ch_num:2d} ({ch['dir']}): no matches")

    action = "would inject" if dry_run else "injected"
    print(f"\nTotal: {action} {total} citation(s) across all chapters")
    if dry_run and total > 0:
        print("Run without --dry-run to apply changes.")


def cmd_book_draft(args):
    """Generate draft chapter content from chapter_outlines.yaml.

    Reads the structured outlines and produces full chapter.md files with
    sections, equations, code examples, tables, and figures.
    """
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_builder import draft_all_chapters, draft_chapter, load_book_config

    book_dir = Path(args.book_dir)
    force = getattr(args, "force", False)
    chapter = getattr(args, "chapter", None)

    outlines_path = book_dir / "chapter_outlines.yaml"
    if not outlines_path.exists():
        print(f"Error: chapter_outlines.yaml not found in {book_dir}")
        print("Create this file with structured outlines for each chapter.")
        sys.exit(1)

    if chapter:
        # Draft a single chapter
        from book_builder import load_chapter_outline
        outlines = load_chapter_outline(book_dir)
        if chapter not in outlines:
            print(f"Error: No outline found for chapter '{chapter}'")
            sys.exit(1)
        path = draft_chapter(book_dir, chapter, outlines[chapter], force=force)
        words = len(path.read_text(encoding="utf-8").split()) if path.exists() else 0
        print(f"Drafted: {chapter} ({words:,} words)")
    else:
        # Draft all chapters
        drafted = draft_all_chapters(book_dir, force=force)
        if not drafted:
            print("No chapters drafted. Check chapter_outlines.yaml has entries "
                  "matching chapter dir names in book.yaml.")
            sys.exit(1)

        total_words = 0
        for ch_name, path in drafted:
            words = len(path.read_text(encoding="utf-8").split()) if path.exists() else 0
            total_words += words
            print(f"  Drafted: {ch_name} ({words:,} words)")

        print(f"\n{len(drafted)} chapters drafted ({total_words:,} total words, "
              f"~{total_words // 250} pages)")
        print(f"\nNext steps:")
        print(f"  1. Review chapters in {book_dir / 'chapters'}")
        print(f"  2. Run: python paperflow.py book-status {book_dir}")
        print(f"  3. Run: python paperflow.py book-check {book_dir}")


def cmd_book_expand_outline(args):
    """Expand book.yaml chapters into a fine-grained chapter_outlines.yaml."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_writer import expand_outlines

    chapters = args.chapter or None
    expand_outlines(
        Path(args.book_dir),
        provider=args.provider,
        model=args.model,
        chapters=chapters,
        force=args.force,
        target_pages_default=args.target_pages,
    )


def cmd_book_expand_local(args):
    """Augment chapter.md files with non-LLM, deterministic content blocks."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_local_expander import expand_book

    expand_book(
        Path(args.book_dir),
        chapters=args.chapter or None,
        strip_only=args.strip,
    )


def cmd_book_plan_notebooks(args):
    """LLM-generate one ``.ipynb`` per (section, notebook_filename) group.

    Reads ``chapter_outlines.yaml`` and, for every section that lists
    figures with a ``notebook`` field, produces a runnable Jupyter notebook
    under ``chapters/<ch>/notebooks/`` that saves each PNG to
    ``../figures/<file>``. Existing notebooks are skipped unless
    ``--force``.
    """
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_notebook_planner import plan_book_notebooks

    plan_book_notebooks(
        Path(args.book_dir),
        provider=args.provider,
        model=args.model,
        chapter_filter=args.chapter or None,
        force=args.force,
        use_llm=not args.no_llm,
        max_tokens=args.max_tokens,
        dry_run=args.dry_run,
    )


def cmd_book_write(args):
    """Long-running orchestrator: draft every section of the book.

    One LLM call per section; checkpoints to .book_write_progress.json so
    interrupted runs can be resumed with --resume (default).
    """
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_writer import (
        write_book, expand_outlines, _load_chapter_specs, estimate_book_cost,
    )

    book_dir = Path(args.book_dir)
    outlines_path = book_dir / "chapter_outlines.yaml"

    # Auto-expand outlines if missing (unless user opted out)
    if not outlines_path.exists() and not args.no_auto_expand:
        print("chapter_outlines.yaml not found — expanding outlines first...")
        expand_outlines(
            book_dir,
            provider=args.provider, model=args.model,
            chapters=args.chapter or None,
            force=False,
            target_pages_default=args.target_pages,
        )

    specs = _load_chapter_specs(book_dir)
    if args.chapter:
        specs = [c for c in specs if c.dir in args.chapter]
    est = estimate_book_cost(specs)
    print("\nDrafting plan:")
    print(f"  chapters         : {est['n_chapters']}")
    print(f"  sections         : {est['n_sections']}")
    print(f"  target words     : {est['target_words']:,}")
    print(f"  approx pages     : {est['approx_pages']}")
    print(f"  est. tokens in   : {est['tokens_in']:,}")
    print(f"  est. tokens out  : {est['tokens_out']:,}")
    print(f"  est. USD (~mid)  : ${est['usd_estimate']}")
    print()

    if args.dry_run:
        print("Dry run — no LLM calls made.")
        return

    if args.confirm and est["n_sections"] > 50:
        try:
            ans = input(f"Proceed with {est['n_sections']} LLM calls? [y/N] ")
        except EOFError:
            ans = "n"
        if ans.strip().lower() not in ("y", "yes"):
            print("Aborted.")
            return

    summary = write_book(
        book_dir,
        provider=args.provider,
        model=args.model,
        chapters=args.chapter or None,
        sections=args.section or None,
        resume=args.resume,
        stop_on_error=args.stop_on_error,
        skip_stitch=args.skip_stitch,
        max_tokens_per_section=args.max_tokens,
        sleep_between=args.sleep,
    )
    print("\nSummary:")
    print(json.dumps(summary, indent=2))
    print(f"\nNext: python paperflow.py book-build {book_dir} --skip-notebooks --no-compile --format html")


def cmd_book_run_notebooks(args):
    """Run book notebooks via devtools (no JAR packaging needed)."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_notebook_runner import run_book_notebooks, compile_neqsim

    chapter = getattr(args, "chapter", None)
    compile_first = getattr(args, "compile", True)
    timeout = getattr(args, "timeout", 600)
    stop_on_error = getattr(args, "stop_on_error", False)
    update_chapters = getattr(args, "update_chapters", False)

    results = run_book_notebooks(
        args.book_dir,
        chapter_filter=chapter,
        compile_first=compile_first,
        timeout=timeout,
        stop_on_error=stop_on_error,
    )

    if update_chapters:
        from book_notebook_runner import update_all_chapters_with_figures
        print("\nUpdating chapters with generated figures...")
        injected = update_all_chapters_with_figures(args.book_dir, chapter)
        if not injected:
            print("  No new figures to inject.")

    failed = sum(1 for r in results if r.get("errors") or r.get("error"))
    if failed > 0:
        sys.exit(1)


def cmd_book_build(args):
    """Full book build: compile -> run notebooks -> update chapters -> check -> render."""
    sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
    from book_notebook_runner import build_book

    summary = build_book(
        args.book_dir,
        output_format=getattr(args, "out_format", "html"),
        compile_first=getattr(args, "compile", True),
        run_notebooks=not getattr(args, "skip_notebooks", False),
        update_chapters=True,
        check_quality=True,
        timeout=getattr(args, "timeout", 600),
        stop_on_error=getattr(args, "stop_on_error", False),
    )

    nb_info = summary.get("steps", {}).get("notebooks", {})
    if isinstance(nb_info, dict) and nb_info.get("failed", 0) > 0:
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="paperflow — NeqSim scientific paper production",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python paperflow.py new "TPflash Improvements" --journal fluid_phase_equilibria --topic tpflash
  python paperflow.py benchmark papers/tpflash_2026/
  python paperflow.py format papers/tpflash_2026/ --journal fluid_phase_equilibria
  python paperflow.py audit papers/tpflash_2026/
  python paperflow.py status papers/tpflash_2026/
        """,
    )
    subparsers = parser.add_subparsers(dest="command")

    # new
    p_new = subparsers.add_parser("new", help="Create new paper project")
    p_new.add_argument("title", help="Paper title")
    p_new.add_argument("--journal", required=True, help="Target journal profile name")
    p_new.add_argument("--topic", help="Topic slug (default: derived from title)")
    p_new.add_argument("--paper-type", dest="paper_type",
                       choices=["comparative", "characterization", "method",
                                "application", "data", "review"],
                       help="Paper type (selects manuscript template)")

    # list
    subparsers.add_parser("list", help="List all papers with status and metadata")

    # benchmark
    p_bench = subparsers.add_parser("benchmark", help="Run benchmark suite")
    p_bench.add_argument("paper_dir", help="Paper directory")

    # figures
    p_fig = subparsers.add_parser("figures", help="Generate figures from results")
    p_fig.add_argument("paper_dir", help="Paper directory")

    # validate-figures
    p_valfig = subparsers.add_parser("validate-figures",
                                      help="Validate figure/table formats against journal requirements")
    p_valfig.add_argument("paper_dir", help="Paper directory")
    p_valfig.add_argument("--journal", required=True, help="Journal profile name")

    # draft — generates manuscript from plan + results
    p_draft = subparsers.add_parser("draft", help="Generate manuscript draft from artifacts")
    p_draft.add_argument("paper_dir", help="Paper directory")
    p_draft.add_argument("--force", action="store_true",
                        help="Overwrite existing paper.md even if it has content")

    # iterate — interactive quality checks for iterative refinement
    p_iter = subparsers.add_parser("iterate", help="Check manuscript quality and suggest improvements")
    p_iter.add_argument("paper_dir", help="Paper directory")
    p_iter.add_argument("--check", default="all",
                       choices=["all", "structure", "completeness", "evidence", "writing"],
                       help="Which checks to run (default: all)")

    # revise — prepare revision workspace for reviewer comments
    p_rev = subparsers.add_parser("revise", help="Prepare revision workspace for reviewer comments")
    p_rev.add_argument("paper_dir", help="Paper directory")
    p_rev.add_argument("--comments", help="Path to reviewer comments file (optional)")

    # format
    p_fmt = subparsers.add_parser("format", help="Format for journal submission")
    p_fmt.add_argument("paper_dir", help="Paper directory")
    p_fmt.add_argument("--journal", required=True, help="Journal profile name")

    # audit
    p_audit = subparsers.add_parser("audit", help="Audit claims and reproducibility")
    p_audit.add_argument("paper_dir", help="Paper directory")

    # status
    p_status = subparsers.add_parser("status", help="Show paper project status")
    p_status.add_argument("paper_dir", help="Paper directory")

    # validate-bib
    p_valbib = subparsers.add_parser("validate-bib",
                                      help="Validate bibliography (refs.bib)")
    p_valbib.add_argument("paper_dir", help="Paper directory")

    # render
    p_render = subparsers.add_parser("render",
                                      help="Render manuscript to HTML/LaTeX/Word")
    p_render.add_argument("paper_dir", help="Paper directory")
    p_render.add_argument("--journal", help="Journal profile (default: from plan.json)")

    # check-prose
    p_prose = subparsers.add_parser("check-prose",
                                     help="Analyze prose quality (readability, passive voice, hedging)")
    p_prose.add_argument("paper_dir", help="Paper directory")

    # suggest-refs
    p_refs = subparsers.add_parser("suggest-refs",
                                    help="Suggest missing references via Semantic Scholar")
    p_refs.add_argument("paper_dir", help="Paper directory")
    p_refs.add_argument("--max", type=int, default=10,
                       help="Max suggestions to show (default: 10)")

    # diff
    p_diff = subparsers.add_parser("diff",
                                    help="Generate visual diff between manuscript revisions")
    p_diff.add_argument("paper_dir", help="Paper directory")
    p_diff.add_argument("--revision", type=int, help="Revision number (default: latest)")
    p_diff.add_argument("--old", help="Explicit path to old version")
    p_diff.add_argument("--new", help="Explicit path to new version")

    # scan — discover paper opportunities (trending by default)
    p_scan = subparsers.add_parser("scan",
                                    help="Find paper opportunities from trending research")
    p_scan.add_argument("--trending", action="store_true", default=True,
                        help="Search trending academic topics (default)")
    p_scan.add_argument("--full", action="store_true",
                        help="Show all trending opportunities (not just daily pick)")
    p_scan.add_argument("--legacy", action="store_true",
                        help="Use old codebase scanner instead of trending")
    p_scan.add_argument("--since", type=int, default=180,
                        help="Look-back window in days for legacy scan (default: 180)")
    p_scan.add_argument("--top", type=int, default=15,
                        help="Max opportunities to show (default: 15)")
    p_scan.add_argument("--literature", action="store_true",
                        help="Cross-check Semantic Scholar (legacy mode)")
    p_scan.add_argument("--verbose", "-v", action="store_true",
                        help="Show detailed info for each opportunity")
    p_scan.add_argument("--output", help="Custom output path for results JSON")
    p_scan.add_argument("--repo", help="Path to NeqSim repo root (default: auto-detect)")

    # stats — statistical tests on benchmark results
    p_stats = subparsers.add_parser("stats",
                                     help="Run statistical tests on benchmark results")
    p_stats.add_argument("paper_dir", help="Paper directory")

    # check-plagiarism — self-plagiarism detection
    p_plag = subparsers.add_parser("check-plagiarism",
                                    help="Check manuscript for self-plagiarism against other papers")
    p_plag.add_argument("paper_dir", help="Paper directory")
    p_plag.add_argument("--doc-threshold", type=float, default=0.35,
                        help="Document similarity threshold (default: 0.35)")
    p_plag.add_argument("--para-threshold", type=float, default=0.50,
                        help="Paragraph similarity threshold (default: 0.50)")

    # manifest — generate reproducibility manifest
    p_manifest = subparsers.add_parser("manifest",
                                        help="Generate reproducibility manifest")
    p_manifest.add_argument("paper_dir", help="Paper directory")

    # verify-manifest — verify existing manifest
    p_vmanifest = subparsers.add_parser("verify-manifest",
                                         help="Verify reproducibility manifest")
    p_vmanifest.add_argument("paper_dir", help="Paper directory")

    # graphical-abstract — generate graphical abstract
    p_gabstract = subparsers.add_parser("graphical-abstract",
                                         help="Generate graphical abstract from paper artifacts")
    p_gabstract.add_argument("paper_dir", help="Paper directory")

    # credit — CRediT author contribution statement
    p_credit = subparsers.add_parser("credit",
                                      help="Generate CRediT author contribution statement")
    p_credit.add_argument("paper_dir", help="Paper directory")
    p_credit.add_argument("--contributors", help="JSON dict of author→roles (overrides plan.json)")

    # nomenclature — extract nomenclature table
    p_nomen = subparsers.add_parser("nomenclature",
                                     help="Extract nomenclature table from manuscript symbols")
    p_nomen.add_argument("paper_dir", help="Paper directory")

    # related-work — comparison table from literature
    p_relwork = subparsers.add_parser("related-work",
                                       help="Generate related work comparison table")
    p_relwork.add_argument("paper_dir", help="Paper directory")

    # latex — compile to LaTeX/PDF via Pandoc
    p_latex = subparsers.add_parser("latex",
                                     help="Compile manuscript to LaTeX/PDF via Pandoc")
    p_latex.add_argument("paper_dir", help="Paper directory")
    p_latex.add_argument("--journal", help="Journal template (elsevier, springer, mdpi, acs, generic)")
    p_latex.add_argument("--output-format", choices=["pdf", "tex", "both"], default="both",
                         help="Output format (default: both)")
    p_latex.add_argument("--engine", help="LaTeX engine (pdflatex, xelatex, lualatex)")

    # verify-dois — check DOIs resolve
    p_dois = subparsers.add_parser("verify-dois",
                                    help="Verify DOIs in refs.bib resolve correctly")
    p_dois.add_argument("paper_dir", help="Paper directory")

    # ── Evaluate commands ──────────────────────────────────────────────

    # evaluate-figures — multi-layer figure evaluation
    p_evalfig = subparsers.add_parser("evaluate-figures",
                                       help="Evaluate figures (technical + structural + LLM)")
    p_evalfig.add_argument("target_dir", help="Paper or book chapter directory")
    p_evalfig.add_argument("--provider", default="openai",
                           choices=["openai", "anthropic", "litellm"],
                           help="LLM provider (default: openai)")
    p_evalfig.add_argument("--model", default="gpt-4o",
                           help="LLM model (default: gpt-4o)")
    p_evalfig.add_argument("--skip-llm", action="store_true",
                           help="Skip LLM critique (fast, technical-only)")
    p_evalfig.add_argument("--skip-ocr", action="store_true",
                           help="Skip OCR structural analysis")

    # evaluate-results — consistency check + LLM quality assessment
    p_evalres = subparsers.add_parser("evaluate-results",
                                       help="Check result consistency and quality")
    p_evalres.add_argument("target_dir", help="Paper or book chapter directory")
    p_evalres.add_argument("--provider", default="openai",
                           choices=["openai", "anthropic", "litellm"],
                           help="LLM provider (default: openai)")
    p_evalres.add_argument("--model", default="gpt-4o",
                           help="LLM model (default: gpt-4o)")
    p_evalres.add_argument("--skip-llm", action="store_true",
                           help="Skip LLM quality assessment (consistency only)")

    # generate-context — auto-generate figure discussions
    p_genctx = subparsers.add_parser("generate-context",
                                      help="Auto-generate figure discussion and captions")
    p_genctx.add_argument("target_dir", help="Paper or book chapter directory")
    p_genctx.add_argument("--provider", default="openai",
                           choices=["openai", "anthropic", "litellm"],
                           help="LLM provider (default: openai)")
    p_genctx.add_argument("--model", default="gpt-4o",
                           help="LLM model (default: gpt-4o)")
    p_genctx.add_argument("--no-vision", action="store_true",
                           help="Disable vision (text-only context generation)")

    # ── Book commands ──────────────────────────────────────────────────

    # book-new — create a new book project
    p_bnew = subparsers.add_parser("book-new",
                                    help="Create a new book project")
    p_bnew.add_argument("title", help="Book title")
    p_bnew.add_argument("--publisher", default="self",
                        choices=["springer", "wiley", "crc", "self", "ntnu"],
                        help="Publisher profile (default: self)")
    p_bnew.add_argument("--chapters", type=int, default=8,
                        help="Number of initial chapters (default: 8)")

    # book-add-chapter — add a chapter to an existing book
    p_bach = subparsers.add_parser("book-add-chapter",
                                    help="Add a chapter to an existing book")
    p_bach.add_argument("book_dir", help="Book directory")
    p_bach.add_argument("--title", required=True, help="Chapter title")
    p_bach.add_argument("--part", type=int, default=1,
                        help="Part number (1-based, default: 1)")

    # book-render — render book to PDF, Word, or HTML
    p_brender = subparsers.add_parser("book-render",
                                       help="Render book to PDF, Word, or HTML")
    p_brender.add_argument("book_dir", help="Book directory")
    p_brender.add_argument("--format", dest="out_format", default="html",
                           choices=["pdf", "docx", "html", "odf", "epub"],
                           help="Output format (default: html)")
    p_brender.add_argument("--chapter", help="Render single chapter (dir name)")

    # book-preview — live HTML preview server
    p_bprev = subparsers.add_parser("book-preview",
                                     help="Live HTML preview with auto-reload")
    p_bprev.add_argument("book_dir", help="Book directory")
    p_bprev.add_argument("--port", type=int, default=8765,
                          help="HTTP port (default: 8765)")
    p_bprev.add_argument("--no-open", action="store_true",
                          help="Do not open browser automatically")

    # book-enrich-bib — Crossref DOI validation/enrichment for refs.bib
    p_bbib = subparsers.add_parser("book-enrich-bib",
                                    help="Validate and enrich refs.bib via Crossref")
    p_bbib.add_argument("book_dir", help="Book directory")
    p_bbib.add_argument("--in-place", action="store_true",
                         help="Overwrite refs.bib (default: writes refs.enriched.bib)")
    p_bbib.add_argument("--min-score", type=float, default=70.0,
                         help="Minimum title match score 0-100 (default: 70)")
    p_bbib.add_argument("--limit", type=int, default=None,
                         help="Process at most N entries (default: all)")

    # book-index — back-of-book index from @index markers
    p_bidx = subparsers.add_parser("book-index",
                                    help="Generate back-of-book index from @index markers")
    p_bidx.add_argument("book_dir", help="Book directory")
    p_bidx.add_argument("--no-force", action="store_true",
                        help="Don't overwrite hand-edited index")

    # book-citation — emit 'How to cite NeqSim' from CITATION.cff
    p_bcite = subparsers.add_parser("book-citation",
                                     help="Generate citation block from CITATION.cff")
    p_bcite.add_argument("book_dir", help="Book directory")
    p_bcite.add_argument("--cff", default=None,
                          help="Path to CITATION.cff (default: repo root)")

    # book-revision-diff — track changes between two git refs
    p_brev = subparsers.add_parser("book-revision-diff",
                                    help="Diff book content between two git revisions")
    p_brev.add_argument("book_dir", help="Book directory")
    p_brev.add_argument("ref_old", help="Old git ref (branch, tag, commit)")
    p_brev.add_argument("ref_new", help="New git ref (branch, tag, commit)")
    p_brev.add_argument("--out", default=None, help="Output path")

    # book-render-xml — JATS / DocBook
    p_bxml = subparsers.add_parser("book-render-xml",
                                    help="Render JATS or DocBook XML")
    p_bxml.add_argument("book_dir", help="Book directory")
    p_bxml.add_argument("--fmt", choices=["jats", "docbook"], default="jats",
                         help="XML flavour (default: jats)")

    # book-prose-review — offline writing-style linter
    p_bprose = subparsers.add_parser("book-prose-review",
                                      help="Offline prose-review (passive voice, weasel words, ...)")
    p_bprose.add_argument("book_dir", help="Book directory")
    p_bprose.add_argument("--write", action="store_true",
                           help="Also write submission/prose_review.md")

    # paper-render-journal — IEEE / ACS / Elsevier / Springer / RSC
    p_pj = subparsers.add_parser("paper-render-journal",
                                  help="Render paper for a specific journal class")
    p_pj.add_argument("paper_dir", help="Paper directory")
    p_pj.add_argument("--journal", required=True,
                      choices=["ieee", "acs", "elsevier", "springer", "rsc"],
                      help="Target journal class")
    p_pj.add_argument("--out", default=None, help="Output .tex path")

    # book-check — run quality checks
    p_bcheck = subparsers.add_parser("book-check",
                                      help="Run quality checks on a book")
    p_bcheck.add_argument("book_dir", help="Book directory")
    p_bcheck.add_argument("--check", default="all",
                          help="Check to run (all, structure, completeness, etc.)")

    # book-status — overview of book project
    p_bstatus = subparsers.add_parser("book-status",
                                       help="Show book project status overview")
    p_bstatus.add_argument("book_dir", help="Book directory")

    # book-source-inventory — source provenance manifest for course/book material
    p_bsrc = subparsers.add_parser("book-source-inventory",
                                    help="Inventory source files for a course book")
    p_bsrc.add_argument("book_dir", help="Book directory")
    p_bsrc.add_argument("--source-root", required=True,
                        help="Root folder containing lectures, exercises, exams, graphics")
    p_bsrc.add_argument("--hash-limit-mb", type=float, default=20.0,
                        help="Hash files up to this size in MB (default: 20)")

    # book-figure-dossier — figure context and review dossier
    p_bfigdos = subparsers.add_parser("book-figure-dossier",
                                       help="Build a book-level figure dossier")
    p_bfigdos.add_argument("book_dir", help="Book directory")
    p_bfigdos.add_argument("--source-root", default=None,
                           help="Optional source root recorded in the dossier")
    p_bfigdos.add_argument("--vision", action="store_true",
                           help="Use configured LLM vision provider for generated context")
    p_bfigdos.add_argument("--provider", default="openai",
                           choices=["openai", "anthropic", "ollama"],
                           help="LLM provider when --vision is used")
    p_bfigdos.add_argument("--model", default="gpt-4o",
                           help="LLM model when --vision is used")

    # book-apply-figure-context — insert reviewed context from dossier
    p_bapply = subparsers.add_parser("book-apply-figure-context",
                                      help="Insert reviewed figure context into chapters")
    p_bapply.add_argument("book_dir", help="Book directory")
    p_bapply.add_argument("--reviewed-only", dest="reviewed_only", action="store_true",
                          default=True, help="Apply only approved dossier records (default)")
    p_bapply.add_argument("--include-draft", dest="reviewed_only", action="store_false",
                          help="Also apply draft/non-approved dossier records")
    p_bapply.add_argument("--dry-run", action="store_true",
                          help="Report insertions without editing chapters")

    # book-normalize-figures — ensure figures are numbered, referenced, and discussed
    p_bnormfig = subparsers.add_parser("book-normalize-figures",
                                        help="Normalize figure numbers and discussion references")
    p_bnormfig.add_argument("book_dir", help="Book directory")
    p_bnormfig.add_argument("--dry-run", action="store_true",
                            help="Report changes without editing chapters")

    # book-coverage-audit — source coverage vs coverage matrix
    p_bcov = subparsers.add_parser("book-coverage-audit",
                                    help="Audit source coverage against coverage_matrix.md")
    p_bcov.add_argument("book_dir", help="Book directory")
    p_bcov.add_argument("--source-root", required=True,
                        help="Root folder containing lectures, exercises, exams, graphics")

    # book-lecture-topic-coverage — slide-topic coverage from lecture decks
    p_blec = subparsers.add_parser("book-lecture-topic-coverage",
                                    help="Audit lecture-deck slide topics against mapped chapters")
    p_blec.add_argument("book_dir", help="Book directory")
    p_blec.add_argument("--apply", action="store_true",
                        help="Deprecated no-op; write audit files without inserting chapter slide checklists")
    p_blec.add_argument("--max-topics-per-deck", type=int, default=120,
                        help="Maximum slide topics to list per deck in chapter checkpoints")

    # book-lecture-figure-plan — candidate slide figures for chapter enrichment
    p_blecfig = subparsers.add_parser("book-lecture-figure-plan",
                                       help="Plan lecture slides to promote into chapter figures")
    p_blecfig.add_argument("book_dir", help="Book directory")
    p_blecfig.add_argument("--max-slides-per-deck", type=int, default=12,
                           help="Maximum candidate figure slides to report per deck")

    # book-evidence-check — release-gate evidence checks
    p_bev = subparsers.add_parser("book-evidence-check",
                                   help="Run evidence, caption, and figure-discussion checks")
    p_bev.add_argument("book_dir", help="Book directory")
    p_bev.add_argument("--strict", action="store_true",
                       help="Exit non-zero when errors or warnings are present")

    # book-conciseness-audit — repeated text/figure detection and restructure plan
    p_bconcise = subparsers.add_parser("book-conciseness-audit",
                                        help="Detect repeated text/figures and chapter merge candidates")
    p_bconcise.add_argument("book_dir", help="Book directory")
    p_bconcise.add_argument("--min-words", type=int, default=18,
                            help="Minimum paragraph token count to analyze (default: 18)")
    p_bconcise.add_argument("--paragraph-similarity", type=float, default=0.82,
                            help="Near-duplicate paragraph threshold (default: 0.82)")
    p_bconcise.add_argument("--chapter-similarity", type=float, default=0.44,
                            help="Adjacent chapter merge threshold (default: 0.44)")
    p_bconcise.add_argument("--max-pairs", type=int, default=80,
                            help="Maximum near-duplicate paragraph pairs to report (default: 80)")

    # book-apply-conciseness — compress generated lecture-topic appendices
    p_bapplyconcise = subparsers.add_parser("book-apply-conciseness",
                                             help="Compress generated lecture-topic appendices")
    p_bapplyconcise.add_argument("book_dir", help="Book directory")
    p_bapplyconcise.add_argument("--min-block-words", type=int, default=250,
                                 help="Only compress blocks with at least this many words")
    p_bapplyconcise.add_argument("--max-topics", type=int, default=10,
                                 help="Maximum source-topic headings to retain per chapter")
    p_bapplyconcise.add_argument("--dry-run", action="store_true",
                                 help="Report changes without editing chapters")

    # book-skill-stack-plan — systematic use of skills for book improvement
    p_bskill = subparsers.add_parser("book-skill-stack-plan",
                                      help="Build a systematic skill-stack plan for a book")
    p_bskill.add_argument("book_dir", help="Book directory")

    # book-standards-map — chapter-to-standards map
    p_bstd = subparsers.add_parser("book-standards-map",
                                    help="Build a standards-to-chapter map")
    p_bstd.add_argument("book_dir", help="Book directory")

    # book-exam-alignment — exam and exercise coverage map
    p_bexam = subparsers.add_parser("book-exam-alignment",
                                     help="Build an exam and exercise alignment report")
    p_bexam.add_argument("book_dir", help="Book directory")
    p_bexam.add_argument("--source-root", default=None,
                         help="Optional source root when source_manifest.json is unavailable")

    # book-source-pdf-html-plan — source PDF conversion plan
    p_bpdfhtml = subparsers.add_parser("book-source-pdf-html-plan",
                                        help="Plan source PDF-to-HTML conversion")
    p_bpdfhtml.add_argument("book_dir", help="Book directory")
    p_bpdfhtml.add_argument("--source-root", default=None,
                            help="Optional source root when source_manifest.json is unavailable")

    # book-knowledge-graph — graph chapters, skills, figures, standards, sources
    p_bgraph = subparsers.add_parser("book-knowledge-graph",
                                      help="Build a book knowledge graph")
    p_bgraph.add_argument("book_dir", help="Book directory")

    # book-toc — preview table of contents
    p_btoc = subparsers.add_parser("book-toc",
                                    help="Preview table of contents")
    p_btoc.add_argument("book_dir", help="Book directory")

    # book-draft — generate chapter drafts from outlines
    p_bdraft = subparsers.add_parser("book-draft",
                                      help="Generate draft chapters from chapter_outlines.yaml")
    p_bdraft.add_argument("book_dir", help="Book directory")
    p_bdraft.add_argument("--chapter", help="Draft a single chapter (dir name)")
    p_bdraft.add_argument("--force", action="store_true",
                          help="Overwrite existing chapter content")

    # book-expand-outline — LLM expands book.yaml into fine-grained sections
    p_bexp = subparsers.add_parser(
        "book-expand-outline",
        help="LLM-expand book.yaml chapter titles into a fine-grained "
             "chapter_outlines.yaml (one section per ~800 words).",
    )
    p_bexp.add_argument("book_dir", help="Book directory")
    p_bexp.add_argument("--chapter", action="append", default=None,
                        help="Restrict to chapter dir(s); repeatable")
    p_bexp.add_argument("--provider", default="litellm",
                        choices=["litellm", "openai", "anthropic",
                                 "github", "copilot-bridge"],
                        help="LLM provider (default: litellm). "
                             "`github` uses `gh auth token` (no API key); "
                             "`copilot-bridge` delegates to a running "
                             "VS Code Copilot Chat agent via files.")
    p_bexp.add_argument("--model", default="gpt-4o",
                        help="LLM model (default: gpt-4o)")
    p_bexp.add_argument("--force", action="store_true",
                        help="Overwrite existing per-chapter sections")
    p_bexp.add_argument("--target-pages", type=int, default=25,
                        help="Default target_pages when not in book.yaml "
                             "(default: 25)")

    # book-expand-local — non-LLM expansion (no API keys required)
    p_blocal = subparsers.add_parser(
        "book-expand-local",
        help="Augment every chapter.md with deterministic content blocks "
             "(worked examples linked to notebooks, self-test questions, "
             "key-terms glossary, chapter summary, further reading) — "
             "without any LLM call. Idempotent.",
    )
    p_blocal.add_argument("book_dir", help="Book directory")
    p_blocal.add_argument("--chapter", action="append", default=None,
                          help="Restrict to chapter dir(s); repeatable")
    p_blocal.add_argument("--strip", action="store_true",
                          help="Remove previously injected blocks instead "
                               "of adding new ones")

    # book-write — long-running drafting orchestrator (1000-page-capable)
    p_bw = subparsers.add_parser(
        "book-write",
        help="Draft every section of the book with one LLM call per section. "
             "Long-running, checkpointed, resumable. Hours of runtime is "
             "expected for full-length books.",
    )
    p_bw.add_argument("book_dir", help="Book directory")
    p_bw.add_argument("--chapter", action="append", default=None,
                      help="Restrict to chapter dir(s); repeatable")
    p_bw.add_argument("--section", action="append", default=None,
                      help="Restrict to section id(s) like 4.3; repeatable")
    p_bw.add_argument("--provider", default="litellm",
                      choices=["litellm", "openai", "anthropic",
                               "github", "copilot-bridge"])
    p_bw.add_argument("--model", default="gpt-4o")
    p_bw.add_argument("--no-resume", dest="resume", action="store_false",
                      default=True,
                      help="Re-draft sections already marked done")
    p_bw.add_argument("--stop-on-error", action="store_true",
                      help="Halt on first failed section")
    p_bw.add_argument("--skip-stitch", action="store_true",
                      help="Only draft section files; do not assemble chapter.md")
    p_bw.add_argument("--no-auto-expand", action="store_true",
                      help="Do not auto-run book-expand-outline if "
                           "chapter_outlines.yaml is missing")
    p_bw.add_argument("--target-pages", type=int, default=25,
                      help="Default target_pages for auto-expand (default: 25)")
    p_bw.add_argument("--max-tokens", type=int, default=4000,
                      help="Output budget per section (default: 4000)")
    p_bw.add_argument("--sleep", type=float, default=0.0,
                      help="Seconds to sleep between LLM calls (rate-limit dodge)")
    p_bw.add_argument("--dry-run", action="store_true",
                      help="Print plan and cost estimate; make no LLM calls")
    p_bw.add_argument("--confirm", action="store_true", default=True,
                      help="Ask for confirmation before > 50 calls (default on)")
    p_bw.add_argument("--no-confirm", dest="confirm", action="store_false",
                      help="Skip the confirmation prompt")

    # book-plan-notebooks — LLM-generate notebook stubs from outline figures
    p_bpn = subparsers.add_parser(
        "book-plan-notebooks",
        help="LLM-generate one Jupyter notebook per section/figure-group "
             "declared in chapter_outlines.yaml. Each notebook saves its "
             "PNG figures to ../figures/ so book-run-notebooks then "
             "executes them and book-build picks them up.",
    )
    p_bpn.add_argument("book_dir", help="Book directory")
    p_bpn.add_argument("--chapter", action="append", default=None,
                       help="Restrict to chapter dir(s); repeatable")
    p_bpn.add_argument("--provider", default="litellm",
                       choices=["litellm", "openai", "anthropic",
                                "github", "copilot-bridge"])
    p_bpn.add_argument("--model", default="gpt-4o")
    p_bpn.add_argument("--force", action="store_true",
                       help="Overwrite existing notebook files")
    p_bpn.add_argument("--no-llm", action="store_true",
                       help="Skip LLM calls; emit deterministic fallback "
                            "notebooks (offline / CI mode)")
    p_bpn.add_argument("--max-tokens", type=int, default=4000,
                       help="Output budget per notebook (default: 4000)")
    p_bpn.add_argument("--dry-run", action="store_true",
                       help="Print the plan only; make no LLM calls and "
                            "write no files")

    # book-run-notebooks — execute book notebooks via devtools
    p_bnb = subparsers.add_parser("book-run-notebooks",
                                   help="Run book notebooks via devtools (no JAR packaging)")
    p_bnb.add_argument("book_dir", help="Book directory")
    p_bnb.add_argument("--chapter", help="Run only this chapter's notebooks")
    p_bnb.add_argument("--no-compile", dest="compile", action="store_false",
                        default=True, help="Skip Maven compilation")
    p_bnb.add_argument("--timeout", type=int, default=600,
                        help="Per-notebook timeout in seconds (default: 600)")
    p_bnb.add_argument("--stop-on-error", action="store_true",
                        help="Stop on first notebook error")
    p_bnb.add_argument("--update-chapters", action="store_true",
                        help="Inject generated figures into chapter.md files")

    # book-build — full build pipeline (compile -> notebooks -> render)
    p_bbuild = subparsers.add_parser("book-build",
                                      help="Full build: compile, run notebooks, check, render")
    p_bbuild.add_argument("book_dir", help="Book directory")
    p_bbuild.add_argument("--format", dest="out_format", default="html",
                           choices=["html", "docx", "pdf", "odf", "all"],
                           help="Output format (default: html)")
    p_bbuild.add_argument("--no-compile", dest="compile", action="store_false",
                           default=True, help="Skip Maven compilation")
    p_bbuild.add_argument("--skip-notebooks", action="store_true",
                           help="Skip notebook execution (use existing outputs)")
    p_bbuild.add_argument("--timeout", type=int, default=600,
                           help="Per-notebook timeout in seconds (default: 600)")
    p_bbuild.add_argument("--stop-on-error", action="store_true",
                           help="Stop on first notebook error")

    # book-inject-citations — convert author-year prose refs to \cite{key}
    p_binject = subparsers.add_parser("book-inject-citations",
                                       help="Convert author-year prose references to \\cite{key}")
    p_binject.add_argument("book_dir", help="Book directory")
    p_binject.add_argument("--chapter", help="Inject in a single chapter (dir name)")
    p_binject.add_argument("--dry-run", action="store_true",
                            help="Show what would be changed without modifying files")

    args = parser.parse_args()

    if args.command == "new":
        cmd_new(args)
    elif args.command == "list":
        cmd_list(args)
    elif args.command == "benchmark":
        cmd_benchmark(args)
    elif args.command == "figures":
        cmd_figures(args)
    elif args.command == "validate-figures":
        cmd_validate_figures(args)
    elif args.command == "draft":
        cmd_draft(args)
    elif args.command == "iterate":
        cmd_iterate(args)
    elif args.command == "revise":
        cmd_revise(args)
    elif args.command == "format":
        cmd_format(args)
    elif args.command == "audit":
        cmd_audit(args)
    elif args.command == "status":
        cmd_status(args)
    elif args.command == "validate-bib":
        cmd_validate_bib(args)
    elif args.command == "render":
        cmd_render(args)
    elif args.command == "check-prose":
        cmd_check_prose(args)
    elif args.command == "suggest-refs":
        cmd_suggest_refs(args)
    elif args.command == "diff":
        cmd_diff(args)
    elif args.command == "scan":
        cmd_scan(args)
    elif args.command == "stats":
        cmd_stats(args)
    elif args.command == "check-plagiarism":
        cmd_check_plagiarism(args)
    elif args.command == "manifest":
        cmd_manifest(args)
    elif args.command == "verify-manifest":
        cmd_verify_manifest(args)
    elif args.command == "graphical-abstract":
        cmd_graphical_abstract(args)
    elif args.command == "credit":
        cmd_credit(args)
    elif args.command == "nomenclature":
        cmd_nomenclature(args)
    elif args.command == "related-work":
        cmd_related_work(args)
    elif args.command == "latex":
        cmd_latex(args)
    elif args.command == "verify-dois":
        cmd_verify_dois(args)
    elif args.command == "evaluate-figures":
        cmd_evaluate_figures(args)
    elif args.command == "evaluate-results":
        cmd_evaluate_results(args)
    elif args.command == "generate-context":
        cmd_generate_context(args)
    elif args.command == "book-new":
        cmd_book_new(args)
    elif args.command == "book-add-chapter":
        cmd_book_add_chapter(args)
    elif args.command == "book-render":
        cmd_book_render(args)
    elif args.command == "book-preview":
        cmd_book_preview(args)
    elif args.command == "book-enrich-bib":
        cmd_book_enrich_bib(args)
    elif args.command == "book-index":
        cmd_book_index(args)
    elif args.command == "book-citation":
        cmd_book_citation(args)
    elif args.command == "book-revision-diff":
        cmd_book_revision_diff(args)
    elif args.command == "book-render-xml":
        cmd_book_render_xml(args)
    elif args.command == "book-prose-review":
        cmd_book_prose_review(args)
    elif args.command == "paper-render-journal":
        cmd_paper_render_journal(args)
    elif args.command == "book-check":
        cmd_book_check(args)
    elif args.command == "book-status":
        cmd_book_status(args)
    elif args.command == "book-source-inventory":
        cmd_book_source_inventory(args)
    elif args.command == "book-figure-dossier":
        cmd_book_figure_dossier(args)
    elif args.command == "book-apply-figure-context":
        cmd_book_apply_figure_context(args)
    elif args.command == "book-normalize-figures":
        cmd_book_normalize_figures(args)
    elif args.command == "book-coverage-audit":
        cmd_book_coverage_audit(args)
    elif args.command == "book-lecture-topic-coverage":
        cmd_book_lecture_topic_coverage(args)
    elif args.command == "book-lecture-figure-plan":
        cmd_book_lecture_figure_plan(args)
    elif args.command == "book-evidence-check":
        cmd_book_evidence_check(args)
    elif args.command == "book-conciseness-audit":
        cmd_book_conciseness_audit(args)
    elif args.command == "book-apply-conciseness":
        cmd_book_apply_conciseness(args)
    elif args.command == "book-skill-stack-plan":
        cmd_book_skill_stack_plan(args)
    elif args.command == "book-standards-map":
        cmd_book_standards_map(args)
    elif args.command == "book-exam-alignment":
        cmd_book_exam_alignment(args)
    elif args.command == "book-source-pdf-html-plan":
        cmd_book_source_pdf_html_plan(args)
    elif args.command == "book-knowledge-graph":
        cmd_book_knowledge_graph(args)
    elif args.command == "book-toc":
        cmd_book_toc(args)
    elif args.command == "book-draft":
        cmd_book_draft(args)
    elif args.command == "book-expand-outline":
        cmd_book_expand_outline(args)
    elif args.command == "book-expand-local":
        cmd_book_expand_local(args)
    elif args.command == "book-write":
        cmd_book_write(args)
    elif args.command == "book-plan-notebooks":
        cmd_book_plan_notebooks(args)
    elif args.command == "book-run-notebooks":
        cmd_book_run_notebooks(args)
    elif args.command == "book-build":
        cmd_book_build(args)
    elif args.command == "book-inject-citations":
        cmd_book_inject_citations(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
