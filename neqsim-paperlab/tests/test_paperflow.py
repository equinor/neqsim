"""Tests for paperflow CLI and supporting tools.

Run with: python -m pytest tests/ -v
"""
import json
import os
import sys
import shutil
import tempfile
from pathlib import Path

import pytest

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

    def test_scan_command_runs(self, capsys):
        """scan command runs against real repo."""
        import paperflow

        class Args:
            since = 30
            top = 3
            literature = False
            verbose = False
            output = None
            repo = str(PAPERLAB_ROOT.parent)

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
