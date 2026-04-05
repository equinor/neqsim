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
JOURNALS_DIR = PAPERLAB_ROOT / "journals"
TOOLS_DIR = PAPERLAB_ROOT / "tools"


def cmd_new(args):
    """Create a new paper project."""
    title = args.title
    journal = args.journal
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

    # Create paper skeleton
    paper_template = PAPERLAB_ROOT / "templates" / "paper_skeleton.md"
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

    args = parser.parse_args()

    if args.command == "new":
        cmd_new(args)
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
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
