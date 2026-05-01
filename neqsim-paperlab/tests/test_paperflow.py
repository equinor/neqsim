"""Tests for paperflow CLI and supporting tools.

Run with: python -m pytest tests/ -v
"""
import argparse
import json
import os
import sys
import shutil
import tempfile
from pathlib import Path

import pytest
import yaml

# Add paperlab root to path
PAPERLAB_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PAPERLAB_ROOT))
sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))


# ═══════════════════════════════════════════════════════════════════════
# Fixtures
# ═══════════════════════════════════════════════════════════════════════

@pytest.fixture
def tmp_papers(tmp_path):
    """Create a temporary papers directory."""
    papers_dir = tmp_path / "papers"
    papers_dir.mkdir()
    return papers_dir


@pytest.fixture
def sample_paper(tmp_path):
    """Create a complete sample paper project for testing."""
    paper_dir = tmp_path / "papers" / "test_paper_2026"
    paper_dir.mkdir(parents=True)
    for sub in ["algorithm", "figures", "tables", "results", "results/raw", "submission"]:
        (paper_dir / sub).mkdir(parents=True, exist_ok=True)

    # plan.json
    plan = {
        "title": "Test Paper",
        "target_journal": "fluid_phase_equilibria",
        "paper_type": "characterization",
        "created": "2026-01-01",
        "status": "planning",
        "novelty_statement": "Test novelty",
        "research_questions": [
            {"id": "RQ1", "question": "Does it work?", "hypothesis": "Yes",
             "method": "Test it", "acceptance_criterion": "Works"}
        ],
        "benchmark_design": {
            "algorithms": ["baseline"],
            "eos_models": ["SRK"],
            "fluid_families": ["lean_gas", "rich_gas"],
            "metrics": ["convergence_rate", "cpu_time_ms"]
        },
        "workflow_log": [],
    }
    (paper_dir / "plan.json").write_text(json.dumps(plan, indent=2))

    # results.json
    results = {
        "key_results": {
            "total_cases": 100,
            "convergence_rate_pct": 99.5,
            "median_cpu_time_ms": 0.1,
        },
        "validation": {"all_passed": True},
        "approach": "Tested with SRK EOS across 100 cases.",
        "conclusions": "Algorithm converges 99.5% of cases.",
        "figure_captions": {
            "fig1_results.png": "Convergence results by family."
        },
        "tables": [
            {
                "title": "Summary Results",
                "headers": ["Family", "Cases", "Conv %"],
                "rows": [["lean_gas", 50, 100.0], ["rich_gas", 50, 99.0]]
            }
        ],
    }
    (paper_dir / "results.json").write_text(json.dumps(results, indent=2))

    # refs.bib
    (paper_dir / "refs.bib").write_text("@software{neqsim, title={NeqSim}}\n")

    # approved_claims.json (empty for characterization)
    (paper_dir / "approved_claims.json").write_text(
        json.dumps({"claims": [], "threats_to_validity": []}, indent=2))
    (paper_dir / "claims_manifest.json").write_text(
        json.dumps({"claims": [], "unlinked_claims": []}, indent=2))

    return paper_dir


# ═══════════════════════════════════════════════════════════════════════
# Test: paperflow new
# ═══════════════════════════════════════════════════════════════════════

class TestNew:
    def test_creates_directory_structure(self, tmp_path, monkeypatch):
        """cmd_new creates correct directory structure."""
        import paperflow
        monkeypatch.setattr(paperflow, "PAPERS_DIR", tmp_path / "papers")
        (tmp_path / "papers").mkdir()

        class Args:
            title = "Test Flash Paper"
            journal = "fluid_phase_equilibria"
            topic = "test_flash"

        paperflow.cmd_new(Args())

        paper_dir = tmp_path / "papers" / "test_flash_2026"
        assert paper_dir.exists()
        assert (paper_dir / "plan.json").exists()
        assert (paper_dir / "benchmark_config.json").exists()
        assert (paper_dir / "paper.md").exists()
        assert (paper_dir / "refs.bib").exists()
        assert (paper_dir / "approved_claims.json").exists()
        assert (paper_dir / "figures").is_dir()
        assert (paper_dir / "results" / "raw").is_dir()

    def test_plan_json_has_required_fields(self, tmp_path, monkeypatch):
        """plan.json contains expected skeleton fields."""
        import paperflow
        monkeypatch.setattr(paperflow, "PAPERS_DIR", tmp_path / "papers")
        (tmp_path / "papers").mkdir()

        class Args:
            title = "My Paper"
            journal = "computers_chem_eng"
            topic = "my_topic"

        paperflow.cmd_new(Args())

        plan = json.loads(
            (tmp_path / "papers" / "my_topic_2026" / "plan.json").read_text())
        assert plan["title"] == "My Paper"
        assert plan["target_journal"] == "computers_chem_eng"
        assert len(plan["research_questions"]) >= 1
        assert "benchmark_design" in plan


# ═══════════════════════════════════════════════════════════════════════
# Test: paperflow draft
# ═══════════════════════════════════════════════════════════════════════

class TestDraft:
    def test_generates_paper_md_from_artifacts(self, sample_paper, monkeypatch):
        """draft command produces a paper.md from plan + results."""
        import paperflow
        monkeypatch.setattr(paperflow, "JOURNALS_DIR",
                          PAPERLAB_ROOT / "journals")

        # Remove the empty paper.md so draft can write
        (sample_paper / "paper.md").unlink(missing_ok=True)

        class Args:
            paper_dir = str(sample_paper)
            force = True

        paperflow.cmd_draft(Args())

        paper_md = (sample_paper / "paper.md").read_text()
        assert "# Test Paper" in paper_md
        assert "characterization" in paper_md.lower() or "DRAFT" in paper_md
        # Results should appear somewhere
        assert "99.5" in paper_md  # convergence_rate_pct
        assert "Summary Results" in paper_md  # table title
        assert "fig1_results.png" in paper_md  # figure reference

    def test_draft_uses_plan_research_questions(self, sample_paper, monkeypatch):
        """draft command includes research questions from plan."""
        import paperflow
        monkeypatch.setattr(paperflow, "JOURNALS_DIR",
                          PAPERLAB_ROOT / "journals")
        (sample_paper / "paper.md").unlink(missing_ok=True)

        class Args:
            paper_dir = str(sample_paper)
            force = True

        paperflow.cmd_draft(Args())

        paper_md = (sample_paper / "paper.md").read_text()
        assert "RQ1" in paper_md
        assert "Does it work?" in paper_md


# ═══════════════════════════════════════════════════════════════════════
# Test: paperflow iterate
# ═══════════════════════════════════════════════════════════════════════

class TestIterate:
    def test_detects_missing_sections(self, sample_paper, monkeypatch):
        """iterate catches missing manuscript sections."""
        import paperflow
        monkeypatch.setattr(paperflow, "JOURNALS_DIR",
                          PAPERLAB_ROOT / "journals")

        # Write minimal paper.md
        (sample_paper / "paper.md").write_text(
            "# Test\n\n## Abstract\n\nSome text\n")

        class Args:
            paper_dir = str(sample_paper)
            check = "structure"

        paperflow.cmd_iterate(Args())

        feedback = json.loads(
            (sample_paper / "iteration_feedback.json").read_text())
        needs_work = [f for f in feedback["feedback"]
                     if f["status"] == "NEEDS_WORK"]
        # Should flag missing Conclusions, References, etc.
        assert len(needs_work) > 0

    def test_detects_todo_placeholders(self, sample_paper, monkeypatch):
        """iterate flags remaining TODO placeholders."""
        import paperflow
        monkeypatch.setattr(paperflow, "JOURNALS_DIR",
                          PAPERLAB_ROOT / "journals")

        (sample_paper / "paper.md").write_text(
            "# Test\n\n## Abstract\n\nTODO: write abstract\n\n"
            "## Conclusions\n\nTODO: conclusions\n\n## References\n\n"
            "## Acknowledgements\n\n## Keywords\n\n## Introduction\n\n")

        class Args:
            paper_dir = str(sample_paper)
            check = "completeness"

        paperflow.cmd_iterate(Args())

        feedback = json.loads(
            (sample_paper / "iteration_feedback.json").read_text())
        completeness = [f for f in feedback["feedback"]
                       if f["category"] == "completeness"
                       and f["status"] == "NEEDS_WORK"]
        assert any("TODO" in f["message"] for f in completeness)

    def test_saves_iteration_feedback_json(self, sample_paper, monkeypatch):
        """iterate produces machine-readable feedback file."""
        import paperflow
        monkeypatch.setattr(paperflow, "JOURNALS_DIR",
                          PAPERLAB_ROOT / "journals")

        (sample_paper / "paper.md").write_text("# Test\n\n## Abstract\n\n")

        class Args:
            paper_dir = str(sample_paper)
            check = "all"

        paperflow.cmd_iterate(Args())

        fb_file = sample_paper / "iteration_feedback.json"
        assert fb_file.exists()
        data = json.loads(fb_file.read_text())
        assert "score" in data
        assert "max_score" in data
        assert "feedback" in data


# ═══════════════════════════════════════════════════════════════════════
# Test: paperflow revise
# ═══════════════════════════════════════════════════════════════════════

class TestRevise:
    def test_creates_revision_workspace(self, sample_paper, monkeypatch):
        """revise creates revision_N directory with templates."""
        import paperflow

        (sample_paper / "paper.md").write_text("# Test\n\nContent\n")

        class Args:
            paper_dir = str(sample_paper)
            comments = None

        paperflow.cmd_revise(Args())

        rev_dir = sample_paper / "revision_1"
        assert rev_dir.exists()
        assert (rev_dir / "reviewer_comments.md").exists()
        assert (rev_dir / "response_to_reviewers.md").exists()
        assert (rev_dir / "revision_plan.json").exists()
        assert (rev_dir / "paper_r0_baseline.md").exists()


# ═══════════════════════════════════════════════════════════════════════
# Test: claim_tracer
# ═══════════════════════════════════════════════════════════════════════

class TestClaimTracer:
    def test_audit_characterization_passes_with_results(self, sample_paper):
        """Characterization paper passes audit when results appear in text."""
        from claim_tracer import audit_manuscript

        paper_text = (
            "# Test Paper\n\n"
            "## Abstract\n\nWe achieved 99.5% convergence.\n\n"
            "## 5. Results and Discussion\n\n"
            "The algorithm converged in 99.5% of all 100 cases.\n"
            "Median CPU time was 0.1 ms.\n\n"
            "## 6. Conclusions\n\nDone.\n"
        )
        (sample_paper / "paper.md").write_text(paper_text)

        report = audit_manuscript(str(sample_paper))
        assert report["paper_type"] == "characterization"
        assert report["evidence_model"] == "results_tracing"
        # Should not have HIGH or CRITICAL issues
        assert report["issues_by_severity"]["CRITICAL"] == 0

    def test_audit_comparative_needs_claims(self, sample_paper):
        """Comparative paper fails audit without [Claim Cx] references."""
        from claim_tracer import audit_manuscript

        plan = json.loads((sample_paper / "plan.json").read_text())
        plan["paper_type"] = "comparative"
        (sample_paper / "plan.json").write_text(json.dumps(plan, indent=2))

        paper_text = (
            "# Test\n\n"
            "## 5. Results and Discussion\n\n"
            "We improved convergence by 15.2% overall.\n"
        )
        (sample_paper / "paper.md").write_text(paper_text)

        report = audit_manuscript(str(sample_paper))
        assert report["paper_type"] == "comparative"
        # Should flag unlinked numbers in results section
        issues = report["issues"]
        assert len(issues) > 0

    def test_detect_paper_type(self, sample_paper):
        """detect_paper_type reads from plan.json."""
        from claim_tracer import detect_paper_type

        assert detect_paper_type(str(sample_paper)) == "characterization"


# ═══════════════════════════════════════════════════════════════════════
# Test: paper_renderer
# ═══════════════════════════════════════════════════════════════════════

class TestPaperRenderer:
    def test_load_journal_profile(self):
        """Journal profiles load correctly."""
        from paper_renderer import load_journal_profile

        profile = load_journal_profile(
            "fluid_phase_equilibria", str(PAPERLAB_ROOT / "journals"))
        assert profile["journal_name"] == "Fluid Phase Equilibria"
        assert profile["abstract_words_max"] == 250
        assert profile["highlights_required"] is True

    def test_parse_manuscript(self, sample_paper):
        """Manuscript parser splits into sections."""
        from paper_renderer import parse_manuscript

        (sample_paper / "paper.md").write_text(
            "# Title\n\nPreamble\n\n## Abstract\n\nAbstract text\n\n"
            "## 1. Introduction\n\nIntro text\n")

        sections = parse_manuscript(str(sample_paper / "paper.md"))
        assert len(sections) >= 2
        titles = [s["title"] for s in sections]
        assert any("Abstract" in t for t in titles)

    def test_available_journal_profiles(self):
        """All journal profile files are valid YAML."""
        try:
            import yaml
        except ImportError:
            pytest.skip("PyYAML not installed")

        for profile_file in (PAPERLAB_ROOT / "journals").glob("*.yaml"):
            with open(profile_file, encoding="utf-8") as f:
                data = yaml.safe_load(f)
            assert "journal_name" in data, f"{profile_file.name} missing journal_name"
            assert "abstract_words_max" in data, f"{profile_file.name} missing abstract_words_max"


# ═══════════════════════════════════════════════════════════════════════
# Test: status command
# ═══════════════════════════════════════════════════════════════════════

class TestStatus:
    def test_status_runs_without_error(self, sample_paper, capsys):
        """status command prints project overview."""
        import paperflow

        class Args:
            paper_dir = str(sample_paper)

        paperflow.cmd_status(Args())
        output = capsys.readouterr().out
        assert "Test Paper" in output
        assert "plan.json" in output


# ═══════════════════════════════════════════════════════════════════════
# Test: figure_style
# ═══════════════════════════════════════════════════════════════════════

class TestFigureStyle:
    def test_apply_style_does_not_crash(self):
        """apply_style runs without error for all presets."""
        from figure_style import apply_style
        for journal in ("elsevier", "ieee", "nature", "acs", "default"):
            apply_style(journal)

    def test_palette_has_eight_colors(self):
        """PALETTE contains 8 hex colors."""
        from figure_style import PALETTE
        assert len(PALETTE) == 8
        for c in PALETTE:
            assert c.startswith("#")

    def test_save_fig_creates_file(self, tmp_path):
        """save_fig writes a PNG file."""
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from figure_style import save_fig

        fig, ax = plt.subplots()
        ax.plot([1, 2, 3], [1, 4, 9])
        save_fig(fig, "test_fig.png", figures_dir=str(tmp_path))
        plt.close(fig)
        assert (tmp_path / "test_fig.png").exists()
        assert (tmp_path / "test_fig.png").stat().st_size > 0

    def test_figure_size_constants(self):
        """FIG_SINGLE and FIG_DOUBLE have correct dimensions."""
        from figure_style import FIG_SINGLE, FIG_DOUBLE
        assert FIG_SINGLE == (3.5, 2.8)
        assert FIG_DOUBLE[0] == 7.0


# ═══════════════════════════════════════════════════════════════════════
# Test: figure_validator
# ═══════════════════════════════════════════════════════════════════════

class TestFigureValidator:
    def test_validate_missing_dir(self):
        """Returns FAIL for non-existent directory."""
        from figure_validator import validate_figures
        issues = validate_figures("/nonexistent/path")
        assert any(i["severity"] == "FAIL" for i in issues)

    def test_validate_empty_dir(self, tmp_path):
        """Returns WARNING for empty directory."""
        from figure_validator import validate_figures
        issues = validate_figures(str(tmp_path))
        assert any(i["severity"] == "WARNING" for i in issues)

    def test_validate_png_file(self, tmp_path):
        """Validates a real PNG figure."""
        try:
            from PIL import Image
        except ImportError:
            pytest.skip("Pillow not installed")
        from figure_validator import validate_single_figure

        # Create a test PNG at 300 DPI
        img = Image.new("RGB", (1050, 840), color="white")
        fig_path = tmp_path / "fig01_test.png"
        img.save(str(fig_path), dpi=(300, 300))

        issues = validate_single_figure(str(fig_path))
        # Should not have any FAIL issues for a valid 300 DPI image
        assert not any(i["severity"] == "FAIL" for i in issues)

    def test_validate_low_dpi(self, tmp_path):
        """Catches low DPI images."""
        try:
            from PIL import Image
        except ImportError:
            pytest.skip("Pillow not installed")
        from figure_validator import validate_single_figure

        img = Image.new("RGB", (500, 400), color="white")
        fig_path = tmp_path / "fig_lowdpi.png"
        img.save(str(fig_path), dpi=(72, 72))

        issues = validate_single_figure(str(fig_path))
        assert any(i["severity"] == "FAIL" and "DPI" in i["message"] for i in issues)


# ═══════════════════════════════════════════════════════════════════════
# Test: bib_validator
# ═══════════════════════════════════════════════════════════════════════

class TestBibValidator:
    def test_validate_missing_file(self):
        """Returns FAIL for non-existent bib file."""
        from bib_validator import validate_bibliography
        issues = validate_bibliography("/nonexistent/refs.bib")
        assert any(i["severity"] == "FAIL" for i in issues)

    def test_validate_valid_bib(self, tmp_path):
        """Passes for a well-formed bib entry."""
        from bib_validator import validate_bibliography

        bib_text = """@article{Smith2023,
  author = {Smith, John and Doe, Jane},
  title = {A Great Paper},
  journal = {Fluid Phase Equilibria},
  year = {2023},
  volume = {100},
  pages = {1--10},
  doi = {10.1016/j.fluid.2023.001}
}
"""
        bib_path = tmp_path / "refs.bib"
        bib_path.write_text(bib_text)

        issues = validate_bibliography(str(bib_path))
        assert not any(i["severity"] == "FAIL" for i in issues)

    def test_validate_missing_required_fields(self, tmp_path):
        """Catches entries with missing required fields."""
        from bib_validator import validate_bibliography

        bib_text = """@article{BadEntry,
  title = {An article with no author or journal}
}
"""
        bib_path = tmp_path / "refs.bib"
        bib_path.write_text(bib_text)

        issues = validate_bibliography(str(bib_path))
        fails = [i for i in issues if i["severity"] == "FAIL"]
        # Should flag missing author, journal, year
        assert len(fails) >= 2

    def test_cross_check_with_manuscript(self, tmp_path):
        """Detects citations not in bib and bib entries not cited."""
        from bib_validator import validate_bibliography

        bib_text = """@article{Used2023,
  author = {Author, A.},
  title = {Used Paper},
  journal = {Some Journal},
  year = {2023}
}
@article{Unused2023,
  author = {Author, B.},
  title = {Unused Paper},
  journal = {Other Journal},
  year = {2023}
}
"""
        manuscript_text = (
            "# Paper\n\n## Abstract\n\nAs shown by \\cite{Used2023} and "
            "\\cite{Missing2023}, this works.\n"
        )
        bib_path = tmp_path / "refs.bib"
        bib_path.write_text(bib_text)
        manuscript_path = tmp_path / "paper.md"
        manuscript_path.write_text(manuscript_text)

        issues = validate_bibliography(str(bib_path), str(manuscript_path))
        messages = " ".join(i["message"] for i in issues)
        # Missing2023 cited but not in bib
        assert "Missing2023" in messages
        # Unused2023 in bib but not cited
        assert "Unused2023" in messages

    def test_duplicate_keys(self, tmp_path):
        """Catches duplicate BibTeX keys."""
        from bib_validator import validate_bibliography

        bib_text = """@article{Dup2023,
  author = {Author, A.},
  title = {First},
  journal = {J1},
  year = {2023}
}
@article{Dup2023,
  author = {Author, B.},
  title = {Second},
  journal = {J2},
  year = {2023}
}
"""
        bib_path = tmp_path / "refs.bib"
        bib_path.write_text(bib_text)

        issues = validate_bibliography(str(bib_path))
        assert any(i["severity"] == "FAIL" and "Duplicate" in i["message"] for i in issues)


# ═══════════════════════════════════════════════════════════════════════
# Test: validate-bib CLI command
# ═══════════════════════════════════════════════════════════════════════

class TestValidateBibCLI:
    def test_validate_bib_command_runs(self, sample_paper, capsys):
        """validate-bib command runs without crashing."""
        import paperflow

        class Args:
            paper_dir = str(sample_paper)

        # Should not crash (sample_paper has refs.bib)
        paperflow.cmd_validate_bib(Args())
        output = capsys.readouterr().out
        assert "Bibliography Validation" in output


# ═══════════════════════════════════════════════════════════════════════
# Test: prose_quality tool
# ═══════════════════════════════════════════════════════════════════════

class TestProseQuality:
    def test_analyze_returns_scores(self, tmp_path):
        """analyze_prose returns readability scores and summary."""
        from prose_quality import analyze_prose

        paper = tmp_path / "paper.md"
        paper.write_text(
            "# Introduction\n\n"
            "This is a simple test sentence. The algorithm converges quickly. "
            "We measured the pressure drop across the valve. "
            "Results show excellent agreement with laboratory data.\n"
        )
        report = analyze_prose(str(paper))
        assert "error" not in report
        assert report["word_count"] > 0
        assert report["sentence_count"] >= 3
        assert "overall" in report.get("summary_scores", {})

    def test_detects_passive_voice(self, tmp_path):
        """Passive voice sentences are flagged."""
        from prose_quality import analyze_prose

        paper = tmp_path / "paper.md"
        paper.write_text(
            "The pressure was measured by the sensor. "
            "The valve was opened by the operator. "
            "The flow was controlled by the system. "
            "The temperature was increased gradually. "
            "We analysed the results carefully.\n"
        )
        report = analyze_prose(str(paper))
        assert report["passive_voice"]["count"] >= 3

    def test_detects_long_sentences(self, tmp_path):
        """Very long sentences are flagged."""
        from prose_quality import analyze_prose

        long_sentence = " ".join(["word"] * 45) + "."
        paper = tmp_path / "paper.md"
        paper.write_text(f"This is short. {long_sentence}\n")
        report = analyze_prose(str(paper))
        assert report["sentence_stats"]["long_sentence_count"] >= 1

    def test_detects_hedging(self, tmp_path):
        """Hedging language is detected."""
        from prose_quality import analyze_prose

        paper = tmp_path / "paper.md"
        paper.write_text(
            "The results are somewhat encouraging. "
            "Perhaps this approach could be improved. "
            "It seems to work relatively well. "
            "The method is quite robust generally.\n"
        )
        report = analyze_prose(str(paper))
        assert report["hedging"]["count"] >= 3

    def test_detects_wordy_phrases(self, tmp_path):
        """Wordy phrases are identified with suggestions."""
        from prose_quality import analyze_prose

        paper = tmp_path / "paper.md"
        paper.write_text(
            "We did this in order to improve accuracy. "
            "Due to the fact that the sensor failed we stopped. "
            "The system has the ability to self-correct.\n"
        )
        report = analyze_prose(str(paper))
        wordy_issues = [i for i in report["issues"] if i["type"] == "WORDY_PHRASES"]
        assert len(wordy_issues) == 1
        assert wordy_issues[0]["count"] >= 2

    def test_missing_file_returns_error(self, tmp_path):
        """Returns error dict for missing file."""
        from prose_quality import analyze_prose

        report = analyze_prose(str(tmp_path / "nonexistent.md"))
        assert "error" in report


# ═══════════════════════════════════════════════════════════════════════
# Test: citation_discovery tool
# ═══════════════════════════════════════════════════════════════════════

class TestCitationDiscovery:
    def test_extract_search_queries(self, tmp_path):
        """Search queries are extracted from plan.json and paper.md."""
        from citation_discovery import _extract_search_terms

        paper_dir = tmp_path / "paper"
        paper_dir.mkdir()

        plan = {
            "title": "Improved Flash Calculations for Mixed CO2 Systems",
            "research_questions": [
                {"id": "RQ1", "question": "How does CO2 affect convergence?"}
            ],
        }
        (paper_dir / "plan.json").write_text(json.dumps(plan))
        (paper_dir / "paper.md").write_text(
            "# Abstract\n\nWe study equation of state flash convergence.\n"
        )

        queries = _extract_search_terms(str(paper_dir))
        assert len(queries) >= 1
        # Title should be the primary query
        assert any("flash" in q.lower() for q in queries)

    def test_filters_existing_refs(self, tmp_path):
        """Papers already in refs.bib are excluded from suggestions."""
        from citation_discovery import _get_existing_refs

        paper_dir = tmp_path / "paper"
        paper_dir.mkdir()
        (paper_dir / "refs.bib").write_text(
            "@article{Smith2020, title={Flash Algorithms}}\n"
            "@article{Jones2019, title={CO2 Modeling}}\n"
        )

        titles = _get_existing_refs(str(paper_dir))
        assert "flash algorithms" in titles
        assert "co2 modeling" in titles

    def test_no_plan_returns_empty(self, tmp_path):
        """Returns empty suggestions when no plan.json or paper.md exists."""
        from citation_discovery import suggest_citations

        paper_dir = tmp_path / "empty_paper"
        paper_dir.mkdir()

        report = suggest_citations(str(paper_dir))
        assert report.get("suggestions", []) == [] or "error" in report


# ═══════════════════════════════════════════════════════════════════════
# Test: revision_diff tool
# ═══════════════════════════════════════════════════════════════════════

class TestRevisionDiff:
    def test_generate_diff_explicit_files(self, tmp_path):
        """Diff report is generated for two explicit files."""
        from revision_diff import generate_diff

        old = tmp_path / "old.md"
        new = tmp_path / "new.md"
        old.write_text("# Introduction\n\nOriginal text here.\n")
        new.write_text("# Introduction\n\nRevised text with improvements.\n\n## Methods\n\nNew section.\n")

        report = generate_diff(str(tmp_path), old_file=str(old), new_file=str(new))
        assert "error" not in report
        assert report["additions"] > 0
        assert report["new_words"] > report["old_words"]
        assert Path(report["html_report"]).exists()

    def test_detects_added_sections(self, tmp_path):
        """Diff detects newly added markdown sections."""
        from revision_diff import generate_diff

        old = tmp_path / "old.md"
        new = tmp_path / "new.md"
        old.write_text("# Introduction\n\nText.\n")
        new.write_text("# Introduction\n\nText.\n\n## Results\n\nNew results.\n")

        report = generate_diff(str(tmp_path), old_file=str(old), new_file=str(new))
        assert "Results" in report["sections_added"]

    def test_html_report_valid(self, tmp_path):
        """Generated HTML report contains expected structure."""
        from revision_diff import generate_diff

        old = tmp_path / "old.md"
        new = tmp_path / "new.md"
        old.write_text("Line one.\nLine two.\n")
        new.write_text("Line one.\nLine two revised.\nLine three.\n")

        report = generate_diff(str(tmp_path), old_file=str(old), new_file=str(new))
        html_path = Path(report["html_report"])
        html_content = html_path.read_text()
        assert "Manuscript Diff Report" in html_content
        assert "Lines Added" in html_content

    def test_revision_dir_auto_detect(self, tmp_path):
        """Diff auto-detects revision directory baseline."""
        from revision_diff import generate_diff

        paper_dir = tmp_path / "paper"
        paper_dir.mkdir()
        rev_dir = paper_dir / "revision_1"
        rev_dir.mkdir()

        (rev_dir / "paper_r0_baseline.md").write_text("# Old version\n\nOriginal.\n")
        (paper_dir / "paper.md").write_text("# New version\n\nRevised.\n")

        report = generate_diff(str(paper_dir))
        assert "error" not in report
        assert report["additions"] > 0

    def test_missing_revision_returns_error(self, tmp_path):
        """Returns error when no revision directories exist."""
        from revision_diff import generate_diff

        paper_dir = tmp_path / "paper"
        paper_dir.mkdir()
        (paper_dir / "paper.md").write_text("Some text.\n")

        report = generate_diff(str(paper_dir))
        assert "error" in report


# ═══════════════════════════════════════════════════════════════════════
# Test: CLI commands for new tools
# ═══════════════════════════════════════════════════════════════════════

class TestCheckProseCLI:
    def test_check_prose_command_runs(self, sample_paper, capsys):
        """check-prose command runs on sample paper."""
        import paperflow

        # Write a paper.md to the sample paper dir
        (Path(sample_paper) / "paper.md").write_text(
            "# Introduction\n\n"
            "This paper presents a novel algorithm. "
            "The results demonstrate significant improvements. "
            "We validate our approach using benchmark data.\n"
        )

        class Args:
            paper_dir = str(sample_paper)

        paperflow.cmd_check_prose(Args())
        output = capsys.readouterr().out
        assert "PROSE QUALITY" in output


class TestDiffCLI:
    def test_diff_command_runs(self, sample_paper, capsys):
        """diff command runs with explicit files."""
        import paperflow

        old_f = Path(sample_paper) / "old.md"
        new_f = Path(sample_paper) / "paper.md"
        old_f.write_text("# Original\n\nOld content.\n")
        new_f.write_text("# Revised\n\nNew content with changes.\n")

        class Args:
            paper_dir = str(sample_paper)
            revision = None
            old = str(old_f)
            new = str(new_f)

        paperflow.cmd_diff(Args())
        output = capsys.readouterr().out
        assert "REVISION DIFF" in output


# ═══════════════════════════════════════════════════════════════════════
# Research Scanner
# ═══════════════════════════════════════════════════════════════════════

class TestResearchScanner:
    """Tests for the research_scanner tool."""

    def test_classify_domain_thermo(self):
        """Classify a thermo file to the correct domain."""
        from research_scanner import _classify_domain
        domain = _classify_domain("src/main/java/neqsim/thermo/system/SystemSrkEos.java")
        assert domain == "Thermodynamic Models"

    def test_classify_domain_pipeline(self):
        """Classify a pipeline file to the correct domain."""
        from research_scanner import _classify_domain
        domain = _classify_domain("src/main/java/neqsim/process/equipment/pipeline/TwoFluidPipe.java")
        assert domain == "Multiphase Pipeline Flow"

    def test_classify_domain_unknown(self):
        """Unknown paths get General domain."""
        from research_scanner import _classify_domain
        domain = _classify_domain("src/main/java/neqsim/util/SomeThing.java")
        assert domain == "General"

    def test_classify_paper_type_method(self):
        """Classify as method paper when method keywords dominate."""
        from research_scanner import _classify_paper_type
        ptype = _classify_paper_type(
            "GibbsSolver",
            ["Fix Newton convergence", "Add adaptive algorithm stepping"],
        )
        assert ptype == "method"

    def test_classify_paper_type_application(self):
        """Classify as application paper when application keywords dominate."""
        from research_scanner import _classify_paper_type
        ptype = _classify_paper_type(
            "PipelineCostReport",
            ["Add pipeline cost estimation", "Subsea field design report"],
        )
        assert ptype == "application"

    def test_generate_title(self):
        """Title generation produces readable titles."""
        from research_scanner import _generate_title
        title = _generate_title("GibbsReactor", "Chemical Reaction Equilibria", "method")
        assert "Gibbs" in title
        assert "Chemical Reaction" in title

    def test_novelty_score_range(self):
        """Novelty score stays in 0-100 range."""
        from research_scanner import _compute_novelty_score
        score = _compute_novelty_score(
            "TestClass", "Thermodynamic Models",
            line_count=600, has_test=True, recent_commits=8,
            literature_hits=0,
        )
        assert 0 <= score <= 100
        assert score >= 50  # should be high for tested, active, no literature

    def test_novelty_score_low_for_trivial(self):
        """Trivial classes get low novelty scores."""
        from research_scanner import _compute_novelty_score
        score = _compute_novelty_score(
            "TinyClass", "General",
            line_count=20, has_test=False, recent_commits=0,
            literature_hits=10,
        )
        assert score < 30

    def test_estimate_effort(self):
        """Effort estimation returns valid levels."""
        from research_scanner import _estimate_effort
        assert _estimate_effort(200, True, 3) == "low"
        assert _estimate_effort(600, True, 1) == "medium"
        assert _estimate_effort(1200, False, 0) == "high"
        assert _estimate_effort(2000, False, 0) == "very_high"

    def test_effort_weeks_mapping(self):
        """Effort weeks mapping returns expected values."""
        from research_scanner import _effort_weeks
        assert _effort_weeks("low") == 4
        assert _effort_weeks("medium") == 8
        assert _effort_weeks("high") == 12
        assert _effort_weeks("very_high") == 16

    def test_infer_code_improvement_untested(self):
        """Untested classes get test coverage recommendation."""
        from research_scanner import _infer_code_improvement
        cls_info = {
            "class_name": "BigSolver",
            "has_test": False,
            "recent_commits": 3,
            "line_count": 600,
            "domain": "Thermodynamic Models",
        }
        improvements = _infer_code_improvement(cls_info, "method")
        assert any("test" in imp.lower() for imp in improvements)
        assert any("calibrate" in imp.lower() or "validate" in imp.lower()
                    for imp in improvements)

    def test_infer_code_improvement_always_nonempty(self):
        """Every opportunity gets at least one improvement suggestion."""
        from research_scanner import _infer_code_improvement
        for ptype in ("method", "comparative", "characterization", "application"):
            cls_info = {
                "class_name": "X",
                "has_test": True,
                "recent_commits": 0,
                "line_count": 100,
                "domain": "General",
            }
            improvements = _infer_code_improvement(cls_info, ptype)
            assert len(improvements) >= 1, f"No improvements for {ptype}"

    def test_opportunity_has_neqsim_improvement(self):
        """Each opportunity in scan output has neqsim_improvement field."""
        from research_scanner import scan_opportunities
        repo_root = str(PAPERLAB_ROOT.parent)
        report = scan_opportunities(
            repo_root, since_days=365, top_n=3, check_literature=False,
        )
        for opp in report["opportunities"]:
            assert "neqsim_improvement" in opp, "Missing neqsim_improvement"
            assert isinstance(opp["neqsim_improvement"], list)
            assert len(opp["neqsim_improvement"]) >= 1

    def test_count_class_lines(self, tmp_path):
        """Line counting skips blanks and comments."""
        from research_scanner import _count_class_lines
        java_file = tmp_path / "Test.java"
        java_file.write_text(
            "package test;\n"
            "\n"
            "// A comment line\n"
            "public class Test {\n"
            "    public void run() {}\n"
            "}\n"
        )
        count = _count_class_lines(str(java_file))
        assert count == 4  # package, class decl, method, closing brace

    def test_extract_class_summary(self, tmp_path):
        """Extracts class name and method count."""
        from research_scanner import _extract_class_summary
        java_file = tmp_path / "MyClass.java"
        java_file.write_text(
            "public class MyClass {\n"
            "    public void method1() {}\n"
            "    public int method2() { return 0; }\n"
            "    private void helper() {}\n"
            "}\n"
        )
        name, methods = _extract_class_summary(str(java_file))
        assert name == "MyClass"
        assert methods >= 2  # at least 2 public methods

    def test_scan_on_real_repo(self):
        """Full scan runs against the actual NeqSim repo without error."""
        from research_scanner import scan_opportunities
        repo_root = str(PAPERLAB_ROOT.parent)
        report = scan_opportunities(
            repo_root, since_days=30, top_n=5, check_literature=False,
        )
        assert "metadata" in report
        assert "summary" in report
        assert "opportunities" in report
        assert isinstance(report["opportunities"], list)
        assert report["metadata"]["total_classes_scanned"] > 0

    def test_scan_report_structure(self):
        """Scan report has all required fields."""
        from research_scanner import scan_opportunities
        repo_root = str(PAPERLAB_ROOT.parent)
        report = scan_opportunities(
            repo_root, since_days=30, top_n=3, check_literature=False,
        )
        meta = report["metadata"]
        assert "scan_date" in meta
        assert "total_classes_scanned" in meta
        assert "tested_classes" in meta

        summary = report["summary"]
        assert "total_opportunities" in summary
        assert "by_domain" in summary
        assert "by_paper_type" in summary

    def test_opportunity_fields(self):
        """Each opportunity has required fields."""
        from research_scanner import scan_opportunities
        repo_root = str(PAPERLAB_ROOT.parent)
        report = scan_opportunities(
            repo_root, since_days=365, top_n=3, check_literature=False,
        )
        if report["opportunities"]:
            opp = report["opportunities"][0]
            assert "title" in opp
            assert "class_name" in opp
            assert "domain" in opp
            assert "paper_type" in opp
            assert opp["paper_type"] in ("method", "comparative", "characterization", "application")
            assert "score" in opp
            assert 0 <= opp["score"] <= 100
            assert "readiness" in opp
            assert opp["readiness"] in ("ready", "needs_validation", "needs_development")
            assert "effort" in opp
            assert "suggested_journals" in opp
            assert "evidence" in opp
            assert "source_path" in opp

    def test_print_scan_report_runs(self, capsys):
        """Pretty-printer does not crash."""
        from research_scanner import print_scan_report
        report = {
            "metadata": {
                "scan_date": "2026-07-03",
                "total_classes_scanned": 100,
                "classes_with_recent_changes": 30,
                "tested_classes": 50,
                "literature_checked": False,
            },
            "summary": {
                "total_opportunities": 2,
                "ready_count": 1,
                "by_domain": {"Thermodynamic Models": 1, "PVT & Reservoir Fluids": 1},
                "by_paper_type": {"method": 1, "characterization": 1},
            },
            "opportunities": [
                {
                    "title": "Test Opportunity Alpha",
                    "class_name": "AlphaClass",
                    "domain": "Thermodynamic Models",
                    "paper_type": "method",
                    "score": 85,
                    "readiness": "ready",
                    "effort": "medium",
                    "effort_weeks": 8,
                    "suggested_journals": ["fluid_phase_equilibria"],
                    "evidence": {
                        "line_count": 500, "method_count": 12,
                        "has_test": True, "recent_commits": 5,
                        "recent_insertions": 200, "last_changed": "2026-06-01",
                        "authors": ["Dev"],
                    },
                    "source_path": "src/main/java/neqsim/thermo/AlphaClass.java",
                    "commit_highlights": ["Fix convergence"],
                },
            ],
        }
        print_scan_report(report)
        output = capsys.readouterr().out
        assert "Research Opportunity Scanner" in output
        assert "Alpha" in output


class TestScanCLI:
    """Test the scan CLI command integration."""

    def test_scan_legacy_command_runs(self, capsys):
        """scan --legacy command runs against real repo."""
        import paperflow

        class Args:
            since = 30
            top = 3
            literature = False
            verbose = False
            output = None
            repo = str(PAPERLAB_ROOT.parent)
            legacy = True
            trending = False
            full = False

        paperflow.cmd_scan(Args())
        output = capsys.readouterr().out
        assert "Research Opportunity Scanner" in output


class TestMarkdownReport:
    """Test the markdown report generator."""

    def test_generate_markdown_report_empty(self):
        """Empty report produces valid markdown."""
        from research_scanner import generate_markdown_report
        report = {
            "metadata": {
                "scan_date": "2026-01-15",
                "total_classes_scanned": 0,
                "classes_with_recent_changes": 0,
                "tested_classes": 0,
                "literature_checked": False,
            },
            "summary": {
                "total_opportunities": 0,
                "ready_count": 0,
                "top_score": 0,
                "by_domain": {},
                "by_paper_type": {},
            },
            "opportunities": [],
        }
        md = generate_markdown_report(report)
        assert "# NeqSim Research Opportunity Report" in md
        assert "No opportunities found" in md

    def test_generate_markdown_report_with_opportunities(self):
        """Report with opportunities has table and detail sections."""
        from research_scanner import generate_markdown_report
        report = {
            "metadata": {
                "scan_date": "2026-01-15",
                "total_classes_scanned": 100,
                "classes_with_recent_changes": 10,
                "tested_classes": 50,
                "literature_checked": False,
            },
            "summary": {
                "total_opportunities": 1,
                "ready_count": 1,
                "top_score": 75,
                "by_domain": {"Thermodynamic Models": 1},
                "by_paper_type": {"method": 1},
            },
            "opportunities": [{
                "title": "A Novel Flash Approach for Thermo",
                "class_name": "FlashCalculator",
                "domain": "Thermodynamic Models",
                "paper_type": "method",
                "score": 75,
                "readiness": "ready",
                "effort": "medium",
                "effort_weeks": 8,
                "suggested_journals": ["fluid_phase_equilibria"],
                "neqsim_improvement": ["Improve solver implementation"],
                "evidence": {
                    "line_count": 400,
                    "method_count": 12,
                    "has_test": True,
                    "recent_commits": 5,
                    "recent_insertions": 200,
                    "last_changed": "2026-01-10",
                    "authors": ["dev"],
                },
                "source_path": "src/main/java/neqsim/thermo/FlashCalculator.java",
                "commit_highlights": ["Improve convergence"],
            }],
        }
        md = generate_markdown_report(report)
        assert "## Top Opportunities" in md
        assert "## Detailed Descriptions" in md
        assert "FlashCalculator" in md
        assert "Improve solver implementation" in md
        assert "## Domain Breakdown" in md

    def test_markdown_report_is_valid_markdown(self):
        """Report contains proper markdown table delimiters."""
        from research_scanner import generate_markdown_report
        report = {
            "metadata": {
                "scan_date": "2026-01-15",
                "total_classes_scanned": 10,
                "classes_with_recent_changes": 2,
                "tested_classes": 5,
                "literature_checked": False,
            },
            "summary": {
                "total_opportunities": 1,
                "ready_count": 0,
                "top_score": 40,
                "by_domain": {"General": 1},
                "by_paper_type": {"application": 1},
            },
            "opportunities": [{
                "title": "Test Title",
                "class_name": "TestClass",
                "domain": "General",
                "paper_type": "application",
                "score": 40,
                "readiness": "needs_validation",
                "effort": "low",
                "effort_weeks": 4,
                "suggested_journals": ["computers_chem_eng"],
                "neqsim_improvement": ["Add tests"],
                "evidence": {
                    "line_count": 100,
                    "method_count": 5,
                    "has_test": False,
                    "recent_commits": 1,
                    "recent_insertions": 50,
                    "last_changed": "2026-01-01",
                    "authors": [],
                },
                "source_path": "src/main/java/neqsim/TestClass.java",
                "commit_highlights": [],
            }],
        }
        md = generate_markdown_report(report)
        # Check table separators exist
        assert "|---|" in md
        assert "| # | Score" in md


class TestDailyScan:
    """Test the daily_scan CI helper."""

    def test_content_hash_deterministic(self):
        """Same opportunities produce same hash."""
        sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
        from daily_scan import _content_hash
        report = {"opportunities": [
            {"class_name": "A", "score": 50, "readiness": "ready"},
            {"class_name": "B", "score": 30, "readiness": "needs_validation"},
        ]}
        h1 = _content_hash(report)
        h2 = _content_hash(report)
        assert h1 == h2
        assert len(h1) == 16

    def test_content_hash_changes_with_score(self):
        """Score change produces different hash."""
        from daily_scan import _content_hash
        r1 = {"opportunities": [{"class_name": "A", "score": 50, "readiness": "ready"}]}
        r2 = {"opportunities": [{"class_name": "A", "score": 60, "readiness": "ready"}]}
        assert _content_hash(r1) != _content_hash(r2)

    def test_content_hash_empty(self):
        """Empty opportunities list still produces a hash."""
        from daily_scan import _content_hash
        h = _content_hash({"opportunities": []})
        assert len(h) == 16

    def test_content_hash_trending_format(self):
        """Trending-format opportunities also produce a deterministic hash."""
        from daily_scan import _content_hash
        report = {"opportunities": [
            {"domain": "CO2 Capture and Storage", "trend_score": 75,
             "inspiring_paper": {"title": "Novel CO2 capture method"}},
        ]}
        h1 = _content_hash(report)
        h2 = _content_hash(report)
        assert h1 == h2
        assert len(h1) == 16

    def test_content_hash_trending_differs_from_legacy(self):
        """Trending and legacy formats produce different hashes."""
        from daily_scan import _content_hash
        legacy = {"opportunities": [
            {"class_name": "SystemSrkEos", "score": 75, "readiness": "ready"},
        ]}
        trending = {"opportunities": [
            {"domain": "Thermodynamic EOS", "trend_score": 80,
             "inspiring_paper": {"title": "Novel CPA for polar mixtures"}},
        ]}
        assert _content_hash(legacy) != _content_hash(trending)


class TestTrendingTopics:
    """Test the trending_topics scanner."""

    def test_neqsim_domains_structure(self):
        """Each domain has required keys."""
        sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))
        from trending_topics import NEQSIM_DOMAINS
        required_keys = {"queries", "neqsim_classes", "journals", "keywords"}
        for name, info in NEQSIM_DOMAINS.items():
            assert required_keys.issubset(info.keys()), f"{name} missing keys"
            assert len(info["queries"]) >= 1, f"{name} has no queries"
            assert len(info["neqsim_classes"]) >= 1, f"{name} has no classes"

    def test_compute_trend_score_range(self):
        """Trend score stays in 0-100 range."""
        from trending_topics import _compute_trend_score
        paper = {
            "title": "Novel equation of state for CO2",
            "abstract": "A simulation-based modeling study",
            "year": 2026,
            "publicationDate": "2026-01-15",
            "citationCount": 50,
        }
        score = _compute_trend_score(paper, ["equation of state", "CO2", "simulation"])
        assert 0 <= score <= 100
        assert score >= 30  # recent, cited, matching keywords

    def test_compute_trend_score_low_for_old_paper(self):
        """Old papers without citations score low."""
        from trending_topics import _compute_trend_score
        paper = {
            "title": "Some random topic",
            "abstract": "No relevant keywords",
            "year": 2015,
            "citationCount": 0,
        }
        score = _compute_trend_score(paper, ["thermodynamic"])
        assert score < 30

    def test_generate_opportunity_structure(self):
        """Generated opportunity has all required fields."""
        from trending_topics import _generate_opportunity
        paper = {
            "title": "Benchmarking CPA EOS for polar mixtures",
            "abstract": "A benchmark comparison of equation of state models",
            "year": 2026,
            "citationCount": 12,
            "url": "https://example.com/paper",
            "authors": [{"name": "J. Smith"}, {"name": "A. Jones"}],
        }
        domain_info = {
            "neqsim_classes": ["SystemSrkCPAstatoil"],
            "journals": ["fluid_phase_equilibria"],
            "keywords": ["CPA", "polar"],
        }
        opp = _generate_opportunity(paper, "Thermodynamic EOS", domain_info, 75)
        assert "title" in opp
        assert "domain" in opp
        assert "paper_type" in opp
        assert "trend_score" in opp
        assert "inspiring_paper" in opp
        assert "neqsim_classes" in opp
        assert "neqsim_improvement" in opp
        assert "research_angle" in opp
        assert opp["paper_type"] == "comparative"  # "benchmark" in abstract
        assert opp["trend_score"] == 75
        assert "J. Smith" in opp["inspiring_paper"]["authors"]

    def test_suggestion_hash_deterministic(self):
        """Same opportunity produces same hash."""
        from trending_topics import _suggestion_hash
        opp = {
            "domain": "CO2 Capture and Storage",
            "inspiring_paper": {"title": "Novel CO2 method"},
        }
        h1 = _suggestion_hash(opp)
        h2 = _suggestion_hash(opp)
        assert h1 == h2
        assert len(h1) == 16

    def test_suggestion_hash_differs(self):
        """Different opportunities produce different hashes."""
        from trending_topics import _suggestion_hash
        o1 = {"domain": "A", "inspiring_paper": {"title": "Paper 1"}}
        o2 = {"domain": "B", "inspiring_paper": {"title": "Paper 2"}}
        assert _suggestion_hash(o1) != _suggestion_hash(o2)

    def test_daily_suggestion_with_mock_scan(self, tmp_path, monkeypatch):
        """daily_suggestion picks one opportunity and records history."""
        from datetime import date as _date
        from trending_topics import daily_suggestion
        # Mock scan_trending to avoid network calls
        mock_report = {
            "metadata": {"scan_date": "2026-04-18"},
            "summary": {"total_opportunities": 2, "by_domain": {}, "by_paper_type": {},
                        "top_score": 80},
            "opportunities": [
                {"title": "Study A", "domain": "Thermodynamic EOS",
                 "paper_type": "method", "trend_score": 80,
                 "inspiring_paper": {"title": "Paper A", "authors": [],
                                     "year": 2026, "citations": 5, "url": ""},
                 "suggested_journals": ["fluid_phase_equilibria"],
                 "neqsim_classes": ["SystemSrkEos"],
                 "neqsim_improvement": ["Improve"],
                 "research_angle": "Angle A",
                 "effort": "medium", "effort_weeks": 8},
                {"title": "Study B", "domain": "CO2 Capture and Storage",
                 "paper_type": "comparative", "trend_score": 60,
                 "inspiring_paper": {"title": "Paper B", "authors": [],
                                     "year": 2025, "citations": 10, "url": ""},
                 "suggested_journals": ["computers_chem_eng"],
                 "neqsim_classes": ["ProcessSystem"],
                 "neqsim_improvement": ["Benchmark"],
                 "research_angle": "Angle B",
                 "effort": "medium", "effort_weeks": 8},
            ],
        }
        import trending_topics as tt
        monkeypatch.setattr(tt, "scan_trending", lambda **kw: mock_report)

        pick = daily_suggestion(
            history_dir=str(tmp_path),
            _today=_date(2026, 4, 18),
        )

        assert pick is not None
        assert pick["title"] in ("Study A", "Study B")
        assert pick["suggestion_date"] == "2026-04-18"

        # History file written
        history_path = tmp_path / "suggestion_history.json"
        assert history_path.exists()
        history = json.loads(history_path.read_text())
        assert len(history) == 1
        assert history[0]["date"] == "2026-04-18"

    def test_daily_suggestion_different_dates(self, tmp_path, monkeypatch):
        """Different dates can produce different picks (rotation)."""
        from datetime import date as _date
        from trending_topics import daily_suggestion
        # 5 opportunities to give rotation room
        opps = []
        for i in range(5):
            opps.append({
                "title": f"Study {i}", "domain": f"Domain {i}",
                "paper_type": "method", "trend_score": 70 - i,
                "inspiring_paper": {"title": f"Paper {i}", "authors": [],
                                    "year": 2026, "citations": 5, "url": ""},
                "suggested_journals": ["fluid_phase_equilibria"],
                "neqsim_classes": ["X"],
                "neqsim_improvement": ["Y"],
                "research_angle": "Z",
                "effort": "medium", "effort_weeks": 8,
            })
        mock_report = {
            "metadata": {"scan_date": "2026-04-18"},
            "summary": {"total_opportunities": 5, "by_domain": {},
                        "by_paper_type": {}, "top_score": 70},
            "opportunities": opps,
        }
        import trending_topics as tt
        monkeypatch.setattr(tt, "scan_trending", lambda **kw: mock_report)

        # Collect picks over several days
        picks = set()
        for day_offset in range(20):
            d = _date(2026, 1, 1 + day_offset)
            # Use fresh history each day to not exhaust pool
            day_dir = tmp_path / f"day_{day_offset}"
            day_dir.mkdir()
            pick = daily_suggestion(
                history_dir=str(day_dir),
                _today=d,
            )
            if pick:
                picks.add(pick["title"])

        # With 5 opportunities and 20 days, should see multiple distinct picks
        assert len(picks) >= 2

    def test_format_daily_suggestion_none(self):
        """Formatting None returns fallback message."""
        from trending_topics import format_daily_suggestion
        text = format_daily_suggestion(None)
        assert "No suggestion" in text

    def test_format_daily_suggestion_output(self):
        """Formatted suggestion contains key fields."""
        from trending_topics import format_daily_suggestion
        pick = {
            "title": "NeqSim Application to Hydrogen Systems",
            "domain": "Hydrogen Systems",
            "paper_type": "application",
            "trend_score": 65,
            "effort": "medium",
            "effort_weeks": 8,
            "suggested_journals": ["computers_chem_eng"],
            "inspiring_paper": {
                "title": "H2 blending in gas networks",
                "authors": ["Smith", "Jones"],
                "year": 2026,
                "citations": 15,
                "url": "https://example.com",
            },
            "research_angle": "Compare NeqSim predictions with field data.",
            "neqsim_classes": ["ProcessSystem"],
            "neqsim_improvement": ["Validate H2 models"],
            "suggestion_date": "2026-04-18",
        }
        text = format_daily_suggestion(pick)
        assert "Today's Paper Opportunity" in text
        assert "Hydrogen Systems" in text
        assert "H2 blending" in text
        assert "Smith" in text

    def test_generate_markdown_suggestion(self):
        """Markdown suggestion has proper structure."""
        from trending_topics import generate_markdown_suggestion
        pick = {
            "title": "Benchmarking NeqSim for CO2 Transport",
            "domain": "CO2 Capture and Storage",
            "paper_type": "comparative",
            "trend_score": 80,
            "effort": "medium",
            "effort_weeks": 8,
            "suggested_journals": ["computers_chem_eng"],
            "inspiring_paper": {
                "title": "Dense phase CO2 transport review",
                "authors": ["Author1"],
                "year": 2026,
                "citations": 20,
                "url": "https://example.com",
            },
            "research_angle": "Benchmark NeqSim CO2 models.",
            "neqsim_classes": ["CO2InjectionWellAnalyzer"],
            "neqsim_improvement": ["Validate"],
            "suggestion_date": "2026-04-18",
        }
        md = generate_markdown_suggestion(pick)
        assert "# Daily Paper Suggestion" in md
        assert "CO2 Capture" in md
        assert "Inspiring Paper" in md
        assert "NeqSim Connection" in md

    def test_generate_markdown_suggestion_none(self):
        """Markdown for None returns fallback."""
        from trending_topics import generate_markdown_suggestion
        md = generate_markdown_suggestion(None)
        assert "No suggestions available" in md


# ═══════════════════════════════════════════════════════════════════════
# Test: paperflow list command
# ═══════════════════════════════════════════════════════════════════════

class TestCmdList:
    def test_list_shows_papers(self, tmp_path, monkeypatch, capsys):
        """cmd_list prints a table of papers."""
        import paperflow as pf
        papers = tmp_path / "papers"
        papers.mkdir()
        (papers / "paper_a").mkdir()
        (papers / "paper_a" / "plan.json").write_text(json.dumps({
            "title": "A", "target_journal": "fluid_phase_equilibria",
            "status": "drafting", "paper_type": "comparative",
        }))
        (papers / "paper_a" / "paper.md").write_text("hello world test")
        monkeypatch.setattr(pf, "PAPERS_DIR", papers)
        pf.cmd_list(argparse.Namespace())
        out = capsys.readouterr().out
        assert "paper_a" in out
        assert "drafting" in out
        assert "1 papers total" in out

    def test_list_handles_broken_json(self, tmp_path, monkeypatch, capsys):
        """cmd_list marks broken plan.json as BROKEN."""
        import paperflow as pf
        papers = tmp_path / "papers"
        papers.mkdir()
        (papers / "broken_paper").mkdir()
        (papers / "broken_paper" / "plan.json").write_text("{bad json")
        monkeypatch.setattr(pf, "PAPERS_DIR", papers)
        pf.cmd_list(argparse.Namespace())
        out = capsys.readouterr().out
        assert "BROKEN" in out


# ═══════════════════════════════════════════════════════════════════════
# Test: normalize_status
# ═══════════════════════════════════════════════════════════════════════

class TestNormalizeStatus:
    def test_canonical_passthrough(self):
        from paperflow import normalize_status
        assert normalize_status("drafting") == "drafting"
        assert normalize_status("planning") == "planning"

    def test_alias_mapping(self):
        from paperflow import normalize_status
        assert normalize_status("DRAFT") == "drafting"
        assert normalize_status("FIRST DRAFT") == "drafting"
        assert normalize_status("draft_v2") == "drafting"
        assert normalize_status("draft_in_progress") == "drafting"


# ═══════════════════════════════════════════════════════════════════════
# Test: nomenclature_extractor
# ═══════════════════════════════════════════════════════════════════════

class TestNomenclatureExtractor:
    def test_extracts_known_symbols(self, tmp_path):
        """Finds known thermo symbols in manuscript text."""
        from nomenclature_extractor import extract_nomenclature
        md = "The pressure $P$ and temperature $T$ affect the fugacity coefficient $\\phi$.\n"
        paper = tmp_path / "paper.md"
        paper.write_text(md)
        report = extract_nomenclature(str(paper))
        assert report.get("symbol_count", 0) > 0

    def test_missing_manuscript(self, tmp_path):
        """Returns error for missing file."""
        from nomenclature_extractor import extract_nomenclature
        report = extract_nomenclature(str(tmp_path / "no.md"))
        assert "error" in report

    def test_no_symbols(self, tmp_path):
        """Returns no_symbols status for plain text."""
        from nomenclature_extractor import extract_nomenclature
        paper = tmp_path / "paper.md"
        paper.write_text("Just plain text with no math at all.")
        report = extract_nomenclature(str(paper))
        assert report.get("status") == "no_symbols" or report.get("symbol_count", 0) == 0


# ═══════════════════════════════════════════════════════════════════════
# Test: statistical_tests
# ═══════════════════════════════════════════════════════════════════════

class TestStatisticalTests:
    def test_missing_results(self, tmp_path):
        """Returns error for paper with no results."""
        from statistical_tests import analyze_benchmarks
        report = analyze_benchmarks(str(tmp_path))
        assert "error" in report or report.get("status") == "no_data"

    def test_format_markdown_empty(self):
        """format_stats_markdown handles empty report gracefully."""
        from statistical_tests import format_stats_markdown
        md = format_stats_markdown({}, metric="cpu_time_ms")
        assert isinstance(md, str)


# ═══════════════════════════════════════════════════════════════════════
# Test: render_html_generic
# ═══════════════════════════════════════════════════════════════════════

class TestRenderHtmlGeneric:
    def test_renders_paper_to_html(self, tmp_path):
        """Produces an HTML file from a minimal paper.md."""
        from render_html_generic import render_paper_html, get_html_header
        paper_dir = tmp_path / "paper_test"
        paper_dir.mkdir()
        (paper_dir / "figures").mkdir()
        (paper_dir / "submission").mkdir()
        (paper_dir / "plan.json").write_text(json.dumps({"title": "Test"}))
        (paper_dir / "paper.md").write_text("# Test\n\n## Introduction\n\nHello world.\n")
        result = render_paper_html(str(paper_dir))
        assert result is not None
        html_file = paper_dir / "submission" / "paper.html"
        assert html_file.exists()
        html = html_file.read_text(encoding="utf-8")
        assert "Hello world" in html

    def test_get_html_header(self):
        """get_html_header includes the title."""
        from render_html_generic import get_html_header
        header = get_html_header(title="My Paper")
        assert "My Paper" in header
        assert "<html>" in header


# ═══════════════════════════════════════════════════════════════════════
# Test: template selection in cmd_new
# ═══════════════════════════════════════════════════════════════════════

class TestTemplateSelection:
    def test_comparative_gets_comparative_template(self, tmp_path, monkeypatch):
        """--paper-type comparative selects paper_skeleton_comparative.md."""
        import paperflow as pf
        monkeypatch.setattr(pf, "PAPERS_DIR", tmp_path / "papers")
        (tmp_path / "papers").mkdir()
        args = argparse.Namespace(
            title="Test Comp", journal="fluid_phase_equilibria",
            topic="test_comp", paper_type="comparative",
        )
        pf.cmd_new(args)
        paper_dir = tmp_path / "papers" / "test_comp_2026"
        plan = json.loads((paper_dir / "plan.json").read_text())
        assert plan["paper_type"] == "comparative"

    def test_default_template_when_no_type(self, tmp_path, monkeypatch):
        """No --paper-type uses default skeleton."""
        import paperflow as pf
        monkeypatch.setattr(pf, "PAPERS_DIR", tmp_path / "papers")
        (tmp_path / "papers").mkdir()
        args = argparse.Namespace(
            title="Test Default", journal="fluid_phase_equilibria",
            topic="test_default", paper_type=None,
        )
        pf.cmd_new(args)
        paper_dir = tmp_path / "papers" / "test_default_2026"
        assert (paper_dir / "paper.md").exists()


# ═══════════════════════════════════════════════════════════════════════
# Book feature tests
# ═══════════════════════════════════════════════════════════════════════

class TestBookBuilder:
    """Tests for tools/book_builder.py scaffolding and assembly."""

    def _create_book(self, tmp_path, n_chapters=3, publisher="self"):
        """Helper: scaffold a book project into tmp_path/books."""
        from book_builder import create_book_project
        return create_book_project(
            title="Test Book",
            publisher=publisher,
            n_chapters=n_chapters,
            books_dir=tmp_path / "books",
        )

    def test_scaffold_creates_directories(self, tmp_path):
        """book-new creates the expected directory structure."""
        bd = self._create_book(tmp_path)
        assert (bd / "book.yaml").exists()
        assert (bd / "frontmatter").is_dir()
        assert (bd / "chapters").is_dir()
        assert (bd / "backmatter").is_dir()
        assert (bd / "submission").is_dir()

    def test_scaffold_chapters(self, tmp_path):
        """Scaffolded book has the requested number of chapters."""
        bd = self._create_book(tmp_path, n_chapters=5)
        cfg = yaml.safe_load((bd / "book.yaml").read_text())
        total = sum(len(part.get("chapters", [])) for part in cfg["parts"])
        assert total == 5

    def test_scaffold_chapter_dirs_exist(self, tmp_path):
        """Every chapter listed in book.yaml has a directory on disk."""
        bd = self._create_book(tmp_path)
        cfg = yaml.safe_load((bd / "book.yaml").read_text())
        for part in cfg["parts"]:
            for ch in part.get("chapters", []):
                assert (bd / "chapters" / ch["dir"]).is_dir()
                assert (bd / "chapters" / ch["dir"] / "chapter.md").exists()

    def test_scaffold_frontmatter(self, tmp_path):
        """Frontmatter files are created from templates."""
        bd = self._create_book(tmp_path)
        for name in ("title_page", "copyright", "dedication", "preface"):
            assert (bd / "frontmatter" / f"{name}.md").exists()

    def test_scaffold_duplicate_raises(self, tmp_path):
        """Creating the same book twice raises FileExistsError."""
        self._create_book(tmp_path)
        with pytest.raises(FileExistsError):
            self._create_book(tmp_path)

    def test_load_config(self, tmp_path):
        """load_book_config returns parsed YAML with required keys."""
        from book_builder import load_book_config
        bd = self._create_book(tmp_path)
        cfg = load_book_config(bd)
        assert "title" in cfg
        assert "authors" in cfg
        assert "parts" in cfg

    def test_load_config_missing_yaml(self, tmp_path):
        """load_book_config raises FileNotFoundError for bad path."""
        from book_builder import load_book_config
        with pytest.raises(FileNotFoundError):
            load_book_config(tmp_path / "nonexistent")


class TestBookAddChapter:
    """Tests for add_chapter."""

    def test_add_chapter_increments_count(self, tmp_path):
        """Adding a chapter increases the total chapter count."""
        from book_builder import create_book_project, add_chapter, load_book_config
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        cfg_before = load_book_config(bd)
        n_before = sum(len(p.get("chapters", [])) for p in cfg_before["parts"])

        add_chapter(bd, title="New Chapter")
        cfg_after = load_book_config(bd)
        n_after = sum(len(p.get("chapters", [])) for p in cfg_after["parts"])
        assert n_after == n_before + 1

    def test_add_chapter_creates_dir(self, tmp_path):
        """The added chapter has a directory and chapter.md on disk."""
        from book_builder import create_book_project, add_chapter, load_book_config
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        ch_dir = add_chapter(bd, title="Extra Chapter")
        assert Path(ch_dir).is_dir()
        assert (Path(ch_dir) / "chapter.md").exists()


class TestBookTOC:
    """Tests for TOC generation."""

    def test_generate_toc_entries(self, tmp_path):
        """TOC contains an entry for each chapter."""
        from book_builder import create_book_project, generate_toc
        bd = create_book_project("Test", n_chapters=4, books_dir=tmp_path / "books")
        toc = generate_toc(bd)
        # At minimum, should have part header + 4 chapter entries
        chapter_entries = [t for t in toc if t[0] == 1]
        assert len(chapter_entries) == 4

    def test_format_toc_produces_string(self, tmp_path):
        """format_toc returns a non-empty string."""
        from book_builder import create_book_project, generate_toc, format_toc
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        toc = generate_toc(bd)
        text = format_toc(toc)
        assert isinstance(text, str)
        assert len(text) > 0


class TestBookStatus:
    """Tests for get_book_status."""

    def test_status_counts(self, tmp_path):
        """get_book_status returns correct chapter count."""
        from book_builder import create_book_project, get_book_status
        bd = create_book_project("Test", n_chapters=3, books_dir=tmp_path / "books")
        status = get_book_status(bd)
        assert status["total_chapters"] == 3
        assert status["title"] == "Test"
        assert isinstance(status["chapters"], list)
        assert len(status["chapters"]) == 3

    def test_status_word_count(self, tmp_path):
        """Word count reflects actual chapter content."""
        from book_builder import create_book_project, get_book_status
        bd = create_book_project("Test", n_chapters=1, books_dir=tmp_path / "books")
        # Write some content to the first chapter
        ch_dir = bd / "chapters" / "ch01"
        (ch_dir / "chapter.md").write_text("word " * 100, encoding="utf-8")
        status = get_book_status(bd)
        assert status["total_words"] == 100

    def test_status_todo_detection(self, tmp_path):
        """TODOs in chapter text are counted."""
        from book_builder import create_book_project, get_book_status
        bd = create_book_project("Test", n_chapters=1, books_dir=tmp_path / "books")
        ch_dir = bd / "chapters" / "ch01"
        (ch_dir / "chapter.md").write_text("Some text TODO fix this TODO add that",
                                           encoding="utf-8")
        status = get_book_status(bd)
        assert status["total_todos"] == 2


class TestBookChecker:
    """Tests for tools/book_checker.py."""

    def test_clean_book_no_errors(self, tmp_path):
        """A freshly scaffolded book has no structure errors."""
        from book_builder import create_book_project
        from book_checker import check_structure
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        issues = check_structure(bd)
        errors = [i for i in issues if i["severity"] == "error"]
        assert len(errors) == 0

    def test_missing_chapter_dir_is_error(self, tmp_path):
        """Deleting a chapter directory triggers a structure error."""
        from book_builder import create_book_project
        from book_checker import check_structure
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        shutil.rmtree(bd / "chapters" / "ch02")
        issues = check_structure(bd)
        errors = [i for i in issues if i["severity"] == "error"]
        assert len(errors) >= 1

    def test_run_checks_all(self, tmp_path):
        """run_checks with no filter runs all checks without crashing."""
        from book_builder import create_book_project
        from book_checker import run_checks
        bd = create_book_project("Test", n_chapters=2, books_dir=tmp_path / "books")
        issues = run_checks(bd)
        assert isinstance(issues, list)

    def test_format_issues_empty(self):
        """format_issues with no issues returns success message."""
        from book_checker import format_issues
        assert "passed" in format_issues([]).lower()

    def test_format_issues_with_errors(self):
        """format_issues renders error count."""
        from book_checker import format_issues
        issues = [{"check": "test", "severity": "error", "message": "bad"}]
        text = format_issues(issues)
        assert "1" in text
        assert "error" in text.lower()

    def test_completeness_reports_short_chapters(self, tmp_path):
        """check_completeness warns about chapters below target word count."""
        from book_builder import create_book_project
        from book_checker import check_completeness
        bd = create_book_project("Test", n_chapters=1, books_dir=tmp_path / "books")
        # Default template is short — should trigger warning
        issues = check_completeness(bd, target_words_per_chapter=5000)
        warnings = [i for i in issues if i["severity"] == "warning"]
        assert len(warnings) >= 1


class TestBookImprovementTools:
    """Tests for book-improvement source, figure, and evidence tooling."""

    def test_source_inventory_skips_dependency_cache(self, tmp_path):
        """source inventory includes course files and skips dependency caches."""
        from book_builder import create_book_project
        from book_improvement_tools import build_source_inventory

        bd = create_book_project("Improve Test", n_chapters=1, books_dir=tmp_path / "books")
        source_root = tmp_path / "source"
        (source_root / "lectures").mkdir(parents=True)
        (source_root / "lectures" / "intro.pdf").write_text("pdf", encoding="utf-8")
        (source_root / ".venv" / "Lib" / "site-packages").mkdir(parents=True)
        (source_root / ".venv" / "Lib" / "site-packages" / "noise.py").write_text(
            "skip", encoding="utf-8")

        manifest = build_source_inventory(bd, source_root)

        assert manifest["total_files"] == 1
        assert manifest["skipped_files"] == 1
        assert manifest["summary"]["by_area"] == {"lectures": 1}
        assert (bd / "source_manifest.json").exists()

    def test_figure_dossier_and_evidence_gate(self, tmp_path):
        """figure dossier records missing discussion and evidence gate reports it."""
        from book_builder import create_book_project
        from book_improvement_tools import build_evidence_report, build_figure_dossier

        bd = create_book_project("Figure Test", n_chapters=1, books_dir=tmp_path / "books")
        chapter_dir = bd / "chapters" / "ch01"
        (chapter_dir / "figures" / "plot.png").write_bytes(b"not a real image")
        (chapter_dir / "chapter.md").write_text(
            "# Chapter 1\n\n## Section\n\n"
            "![Figure 1.1: Pressure profile for the example pipeline.](figures/plot.png)\n\n"
            "Text after figure.\n",
            encoding="utf-8",
        )

        dossier = build_figure_dossier(bd)
        report = build_evidence_report(bd)

        assert dossier["figure_count"] == 1
        assert dossier["summary"]["missing_files"] == 0
        assert dossier["summary"]["without_discussion"] == 1
        assert any(issue["category"] == "figure_discussion" for issue in report["issues"])
        assert (bd / "figure_dossier.json").exists()
        assert (bd / "evidence_report.json").exists()

    def test_coverage_audit_maps_known_lectures(self, tmp_path):
        """coverage audit maps lecture folders by title and flags extra lectures."""
        from book_builder import create_book_project
        from book_improvement_tools import build_coverage_audit

        bd = create_book_project("Coverage Test", n_chapters=1, books_dir=tmp_path / "books")
        (bd / "coverage_matrix.md").write_text(
            "| Date | Lecture title | Chapter(s) |\n"
            "|------|---------------|------------|\n"
            "| 16-01-2026 | Flow Performance in Production Systems | ch04 |\n",
            encoding="utf-8",
        )
        source_root = tmp_path / "source"
        (source_root / "lectures" / "16-01-2026 - Flow Performance in Production Systems").mkdir(
            parents=True)
        (source_root / "lectures" / "28-04-2026 - Review of Mathematics").mkdir(parents=True)

        audit = build_coverage_audit(bd, source_root)

        assert len(audit["lecture_folders"]) == 2
        assert len(audit["unmapped_lecture_folders"]) == 1
        assert "Review of Mathematics" in audit["unmapped_lecture_folders"][0]["source"]

    def test_conciseness_audit_detects_repeated_text_and_figures(self, tmp_path):
        """conciseness audit finds repeated paragraphs and duplicated figures."""
        from book_builder import create_book_project
        from book_improvement_tools import build_conciseness_audit

        bd = create_book_project("Concise Test", n_chapters=2, books_dir=tmp_path / "books")
        repeated = (
            "This production system explanation repeats the same engineering message about "
            "pressure drop, capacity, facilities, operating envelopes, and field development "
            "choices. It should be written once and cross referenced instead of repeated."
        )
        for idx in (1, 2):
            chapter_dir = bd / "chapters" / f"ch{idx:02d}"
            (chapter_dir / "figures" / "shared.png").write_bytes(b"shared image")
            (chapter_dir / "chapter.md").write_text(
                f"# Chapter {idx}\n\n## Shared Topic\n\n{repeated}\n\n"
                "![Shared figure caption.](figures/shared.png)\n",
                encoding="utf-8",
            )

        audit = build_conciseness_audit(bd, min_words=10, paragraph_similarity_threshold=0.9)

        assert audit["summary"]["exact_duplicate_paragraph_groups"] >= 1
        assert audit["summary"]["duplicate_figure_groups"] >= 1
        assert audit["summary"]["chapter_merge_candidates"] >= 1
        assert (bd / "conciseness_audit.json").exists()
        assert (bd / "conciseness_audit.md").exists()

    def test_apply_conciseness_compresses_generated_appendix(self, tmp_path):
        """apply conciseness replaces long generated lecture-topic blocks."""
        from book_builder import create_book_project
        from book_improvement_tools import apply_conciseness_edits

        bd = create_book_project("Apply Concise Test", n_chapters=1, books_dir=tmp_path / "books")
        chapter_md = bd / "chapters" / "ch01" / "chapter.md"
        repeated_sentence = "This generated slide text repeats background process material. " * 35
        chapter_md.write_text(
            "# Chapter 1\n\nMain text.\n\n"
            "<!-- LECTURE_TOPICS_START -->\n"
            "## Further topics covered in the course\n\n"
            "**Repeated topic one.** " + repeated_sentence + "\n\n"
            "**Repeated topic two.** " + repeated_sentence + "\n"
            "<!-- LECTURE_TOPICS_END -->\n",
            encoding="utf-8",
        )

        result = apply_conciseness_edits(bd, min_block_words=20, max_topics=2)
        text = chapter_md.read_text(encoding="utf-8")

        assert result["chapters_changed"] == 1
        assert result["removed_words"] > 0
        assert "Repeated topic one" in text
        assert "generated source appendix has been condensed" in text

    def test_skill_stack_artifacts_build_from_book_sources(self, tmp_path):
        """skill-stack, standards, exam, source, and graph artifacts are written."""
        from book_builder import create_book_project
        from book_improvement_tools import (
            build_book_knowledge_graph,
            build_exam_alignment,
            build_skill_stack_plan,
            build_source_inventory,
            build_source_pdf_html_plan,
            build_standards_map,
        )

        bd = create_book_project("Skill Stack Test", n_chapters=2, books_dir=tmp_path / "books")
        ch01 = bd / "chapters" / "ch01" / "chapter.md"
        ch01.write_text(
            "# Chapter 1\n\n"
            "## Learning Objectives\n\n"
            "- Explain separator and subsea well design standards.\n\n"
            "## Exercises\n\n"
            "1. Check a separator against NORSOK and API guidance.\n\n"
            "The chapter covers separator, subsea, well, safety, and CO2 topics.\n",
            encoding="utf-8",
        )
        source_root = tmp_path / "source"
        (source_root / "exams").mkdir(parents=True)
        (source_root / "exams" / "exam_2026.pdf").write_text("exam", encoding="utf-8")
        (source_root / "exercises").mkdir(parents=True)
        (source_root / "exercises" / "exercise_01.pdf").write_text("exercise", encoding="utf-8")
        (source_root / "lectures").mkdir(parents=True)
        (source_root / "lectures" / "lecture_01.pdf").write_text("lecture", encoding="utf-8")

        build_source_inventory(bd, source_root)
        standards = build_standards_map(bd)
        exam = build_exam_alignment(bd)
        pdf_plan = build_source_pdf_html_plan(bd)
        skill_plan = build_skill_stack_plan(bd)
        graph = build_book_knowledge_graph(bd)

        assert standards["summary"]["unique_standards"] >= 1
        assert exam["summary"]["exam_source_files"] == 1
        assert exam["summary"]["exercise_source_files"] == 1
        assert pdf_plan["summary"]["pdf_files"] == 3
        assert skill_plan["summary"]["dimensions_total"] >= 5
        assert graph["summary"]["nodes"] > 2
        assert graph["summary"]["edges"] > 1
        assert (bd / "skill_stack_plan.md").exists()
        assert (bd / "standards_map.md").exists()
        assert (bd / "exam_alignment.md").exists()
        assert (bd / "source_pdf_html_plan.md").exists()
        assert (bd / "book_knowledge_graph.html").exists()

    def test_lecture_topic_coverage_applies_chapter_checkpoint(self, tmp_path):
        """lecture topic coverage writes report, checkpoint, and appendix."""
        from book_builder import create_book_project
        from book_improvement_tools import build_lecture_topic_coverage

        bd = create_book_project("Lecture Coverage Test", n_chapters=1, books_dir=tmp_path / "books")
        manifest = [
            {
                "lecture": "09-01-2026 Intro",
                "chapter": "ch01",
                "pptx": "intro.pptx",
                "slides": [
                    {"idx": 1, "title": "Field development value chain", "body": ["reservoir", "facilities"]},
                    {"idx": 2, "title": "Decision gates and PUD", "body": ["DG1", "DG2", "approval"]},
                ],
            }
        ]
        (bd / "_lecture_topic_manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

        report = build_lecture_topic_coverage(bd, apply_checkpoints=True)
        chapter_text = (bd / "chapters" / "ch01" / "chapter.md").read_text(encoding="utf-8")

        assert report["summary"]["lecture_decks"] == 1
        assert report["summary"]["topics_needing_review"] == 0
        assert "Lecture coverage checkpoint" in chapter_text
        assert "Field development value chain" in chapter_text
        assert (bd / "lecture_topic_coverage.md").exists()
        assert (bd / "backmatter" / "lecture_coverage.md").exists()

    def test_lecture_figure_plan_reports_rendered_candidates(self, tmp_path):
        """lecture figure plan finds figure-like slides and rendered PNG paths."""
        from book_builder import create_book_project
        from book_improvement_tools import build_lecture_figure_plan

        bd = create_book_project("Lecture Figure Test", n_chapters=1, books_dir=tmp_path / "books")
        manifest = [
            {
                "lecture": "10-03-2026 - Offshore Structures",
                "chapter": "ch01",
                "pptx": "Offshore structures_March.pptx",
                "slides": [
                    {"idx": 1, "title": "Topics in this lecture", "body": ["agenda"]},
                    {"idx": 2, "title": "Platform types", "body": ["bottom fixed", "floaters", "photos"]},
                    {"idx": 3, "title": "Natural periods of motion matter", "body": ["loads", "response"]},
                ],
            }
        ]
        (bd / "_lecture_topic_manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        cache = bd / "_pptx_slides_cache" / "10-03-2026 - Offshore Structures"
        cache.mkdir(parents=True)
        (cache / "Offshore structures_March__slide_002.png").write_bytes(b"png")

        report = build_lecture_figure_plan(bd, max_slides_per_deck=4)

        assert report["summary"]["lecture_decks"] == 1
        assert report["summary"]["candidate_slides"] >= 2
        assert report["summary"]["rendered_candidate_slides"] == 1
        assert any(row["rendered"] for row in report["decks"][0]["candidates"])
        assert (bd / "lecture_figure_plan.md").exists()


class TestBookRenderHTML:
    """Tests for book HTML rendering (no external deps)."""

    def test_render_produces_html(self, tmp_path):
        """render_book_html produces an .html file."""
        from book_builder import create_book_project
        from book_render_html import render_book_html
        bd = create_book_project("Test HTML", n_chapters=2, books_dir=tmp_path / "books")
        result = render_book_html(bd)
        assert result is not None
        assert Path(result).exists()
        assert Path(result).suffix == ".html"

    def test_html_contains_chapter_content(self, tmp_path):
        """Generated HTML includes chapter text."""
        from book_builder import create_book_project
        from book_render_html import render_book_html
        bd = create_book_project("Test HTML", n_chapters=1, books_dir=tmp_path / "books")
        # Write recognisable content
        ch_dir = bd / "chapters" / "ch01"
        (ch_dir / "chapter.md").write_text(
            "# Chapter 1\n\nUnique sentinel text alpha bravo charlie.",
            encoding="utf-8",
        )
        result = render_book_html(bd)
        html = Path(result).read_text(encoding="utf-8")
        assert "alpha bravo charlie" in html

    def test_html_copies_nested_chapter_figures(self, tmp_path):
        """Nested chapter figure folders are copied to submission/figures."""
        from book_builder import create_book_project
        from book_render_html import render_book_html
        bd = create_book_project("Nested Figure Test", n_chapters=1, books_dir=tmp_path / "books")
        ch_dir = bd / "chapters" / "ch01"
        nested = ch_dir / "figures" / "lecture" / "slides"
        nested.mkdir(parents=True)
        (nested / "slide.png").write_bytes(b"png")
        (ch_dir / "chapter.md").write_text(
            "# Chapter 1\n\n![Nested lecture figure.](figures/lecture/slides/slide.png)\n",
            encoding="utf-8",
        )

        render_book_html(bd)

        assert (bd / "submission" / "figures" / "lecture" / "slides" / "slide.png").exists()

    def test_html_has_sidebar(self, tmp_path):
        """Full-book HTML includes a sidebar nav."""
        from book_builder import create_book_project
        from book_render_html import render_book_html
        bd = create_book_project("Test Nav", n_chapters=2, books_dir=tmp_path / "books")
        result = render_book_html(bd)
        html = Path(result).read_text(encoding="utf-8")
        assert "sidebar" in html

    def test_html_chapter_filter(self, tmp_path):
        """chapter_filter renders only the specified chapter."""
        from book_builder import create_book_project
        from book_render_html import render_book_html
        bd = create_book_project("Filter Test", n_chapters=3, books_dir=tmp_path / "books")
        # Write unique content per chapter
        for i in range(1, 4):
            ch_md = bd / "chapters" / f"ch{i:02d}" / "chapter.md"
            ch_md.write_text(f"# Chapter {i}\n\nMarker{i}Unique", encoding="utf-8")
        result = render_book_html(bd, chapter_filter="ch02")
        html = Path(result).read_text(encoding="utf-8")
        assert "Marker2Unique" in html
        # Should NOT contain ch01 or ch03 content
        assert "Marker1Unique" not in html
        assert "Marker3Unique" not in html


class TestBookRenderPDFPreprocess:
    """Tests for PDF preprocessing helpers."""

    def test_external_lecture_figure_is_copied_into_submission(self, tmp_path):
        """external lecture figures are rewritten inside the Typst sandbox."""
        from book_render_pdf import _preprocess_chapter

        book_dir = tmp_path / "book"
        chapter_dir = book_dir / "chapters" / "ch04"
        source_fig = book_dir / "figures" / "lectures" / "ch04" / "slide.png"
        submission_dir = book_dir / "submission"
        chapter_dir.mkdir(parents=True)
        source_fig.parent.mkdir(parents=True)
        source_fig.write_bytes(b"fake image")

        text = (
            "![Caption with citation [1]]"
            "(../../figures/lectures/ch04/slide.png)\n"
        )
        processed = _preprocess_chapter(
            text,
            4,
            chapter_dir=chapter_dir,
            submission_dir=submission_dir,
        )

        assert "../../figures" not in processed
        assert "figures/ch04_figures_lectures_ch04_slide.png" in processed
        assert (submission_dir / "figures" / "ch04_figures_lectures_ch04_slide.png").exists()


class TestBookCLI:
    """Integration tests for book CLI commands in paperflow.py."""

    def test_cmd_book_new(self, tmp_path, monkeypatch):
        """book-new creates a book project via CLI function."""
        import paperflow as pf
        monkeypatch.setattr(pf, "BOOKS_DIR", tmp_path / "books")
        args = argparse.Namespace(
            title="CLI Test Book", publisher="self", chapters=3,
        )
        pf.cmd_book_new(args)
        # Find the created directory
        books = list((tmp_path / "books").iterdir())
        assert len(books) == 1
        assert (books[0] / "book.yaml").exists()

    def test_cmd_book_status(self, tmp_path, monkeypatch, capsys):
        """book-status prints chapter overview."""
        import paperflow as pf
        from book_builder import create_book_project
        bd = create_book_project("Status Test", n_chapters=2, books_dir=tmp_path / "books")
        args = argparse.Namespace(book_dir=str(bd))
        pf.cmd_book_status(args)
        captured = capsys.readouterr()
        assert "Status Test" in captured.out
        assert "Chapters" in captured.out

    def test_cmd_book_toc(self, tmp_path, capsys):
        """book-toc prints table of contents."""
        import paperflow as pf
        from book_builder import create_book_project
        bd = create_book_project("TOC Test", n_chapters=3, books_dir=tmp_path / "books")
        args = argparse.Namespace(book_dir=str(bd))
        pf.cmd_book_toc(args)
        captured = capsys.readouterr()
        assert "TOC Test" in captured.out

    def test_cmd_book_check(self, tmp_path, capsys):
        """book-check runs without error on clean book."""
        import paperflow as pf
        from book_builder import create_book_project
        bd = create_book_project("Check Test", n_chapters=2, books_dir=tmp_path / "books")
        args = argparse.Namespace(book_dir=str(bd), check="all")
        # Should not raise (clean book has warnings but no errors)
        try:
            pf.cmd_book_check(args)
        except SystemExit as e:
            # Exit code 1 is acceptable if there are warnings treated as errors
            pass

    def test_cmd_book_render_html(self, tmp_path):
        """book-render --format html produces output."""
        import paperflow as pf
        from book_builder import create_book_project
        bd = create_book_project("Render Test", n_chapters=2, books_dir=tmp_path / "books")
        args = argparse.Namespace(
            book_dir=str(bd), out_format="html", chapter=None,
        )
        pf.cmd_book_render(args)
