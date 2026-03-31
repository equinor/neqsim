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

    print(f"Generating figures from {results_dir} -> {figures_dir}")
    print("(Use the analyze_convergence skill for detailed figure generation)")
    print()
    print("Available result files:")
    for f in sorted(results_dir.glob("*.json")):
        print(f"  {f.name}")
    for f in sorted((results_dir / "raw").glob("*.jsonl")):
        print(f"  raw/{f.name}")


def cmd_validate_figures(args):
    """Validate figure and table formats against journal requirements."""
    paper_dir = Path(args.paper_dir)
    journal = args.journal

    sys.path.insert(0, str(TOOLS_DIR))
    from paper_renderer import (
        load_journal_profile, validate_figures, validate_tables, print_compliance
    )

    profile = load_journal_profile(journal, str(JOURNALS_DIR))

    print("Validating figures...")
    fig_checks = validate_figures(str(paper_dir), profile)

    print("Validating tables...")
    tbl_checks = validate_tables(str(paper_dir), profile)

    all_checks = fig_checks + tbl_checks
    print_compliance(all_checks)

    if any(c["status"] == "FAIL" for c in all_checks):
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

    # draft — placeholder (uses writer agent)
    p_draft = subparsers.add_parser("draft", help="Draft manuscript (uses writer agent)")
    p_draft.add_argument("paper_dir", help="Paper directory")

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
        print(f"Drafting is handled by the scientific_writer agent.")
        print(f"Use VS Code Copilot Chat with @scientific-writer to draft {args.paper_dir}/paper.md")
        print(f"Or run the workflow: see workflows/new_paper.yaml Stage 8")
    elif args.command == "format":
        cmd_format(args)
    elif args.command == "audit":
        cmd_audit(args)
    elif args.command == "status":
        cmd_status(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
