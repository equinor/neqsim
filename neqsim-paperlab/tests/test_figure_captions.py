"""Tests for normalize_figure_captions() in tools/paper_renderer.py.

Regression guard for the "Figure N: Figure N" duplicate-caption bug that
appeared in production_allocation_2026 — see PR #2326.

The bug pattern in markdown:

    ![Figure N](path.png)

    *Figure N. Real descriptive caption.*

…makes every renderer (LaTeX \\caption, Word native caption, HTML <figcaption>)
take the placeholder alt-text as the caption, producing "Figure N: Figure N"
with a duplicate italic line below it. normalize_figure_captions() must
collapse the pair into a single ``![Real descriptive caption.](path.png)``.

Run: python -m pytest tests/test_figure_captions.py -v
"""
import sys
from pathlib import Path

import pytest

# Match the path setup that test_paperflow.py uses
PAPERLAB_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PAPERLAB_ROOT / "tools"))

from paper_renderer import normalize_figure_captions, markdown_to_latex


class TestNormalizeFigureCaptions:
    """Unit tests for the shared pre-processing helper."""

    def test_placeholder_alt_merged_with_italic_caption(self):
        src = (
            "Para.\n\n"
            "![Figure 1](figures/x.png)\n\n"
            "*Figure 1. Real caption here.*\n\n"
            "Next para.\n"
        )
        out = normalize_figure_captions(src)
        assert "Real caption here." in out
        assert "*Figure 1." not in out, "duplicate italic line must be dropped"
        assert "![Figure 1](" not in out, "placeholder alt must be replaced"

    def test_descriptive_alt_yields_italic_text(self):
        # When both alt and italic are descriptive, italic wins (it's the canonical caption)
        src = "![Real caption](p.png)\n\n*Figure 2. Real caption.*\n"
        out = normalize_figure_captions(src)
        assert "![Real caption.](p.png)" in out
        assert "*Figure 2." not in out

    def test_fig_n_colon_variant(self):
        src = "![Fig 3](q.png)\n\n*Fig. 3: Another caption.*\n"
        out = normalize_figure_captions(src)
        assert "Another caption." in out
        assert "*Fig. 3:" not in out

    def test_italic_body_text_after_image_untouched(self):
        # Italic line that does NOT start with "Figure N." must be left alone
        src = "![alt](p.png)\n\n*This is italic body text, not a caption.*\n"
        out = normalize_figure_captions(src)
        assert out == src, "non-caption italic must be preserved verbatim"

    def test_no_italic_line_unchanged(self):
        src = "![only-alt](p.png)\n\nbody text\n"
        out = normalize_figure_captions(src)
        assert out == src

    def test_label_only_italic_dropped_alt_preserved(self):
        # Italic line is just the label (no description) → drop it, keep alt
        src = "![alt-text](p.png)\n\n*Figure 4.*\n"
        out = normalize_figure_captions(src)
        assert "![alt-text](p.png)" in out
        assert "*Figure 4.*" not in out

    def test_multiple_figures_all_normalized(self):
        src = (
            "![Fig 1](a.png)\n\n*Figure 1. First.*\n\n"
            "Prose.\n\n"
            "![Fig 2](b.png)\n\n*Figure 2. Second.*\n"
        )
        out = normalize_figure_captions(src)
        assert "First." in out
        assert "Second." in out
        assert "*Figure 1." not in out
        assert "*Figure 2." not in out

    def test_case_insensitive_matching(self):
        src = "![alt](p.png)\n\n*FIGURE 1. uppercase label.*\n"
        out = normalize_figure_captions(src)
        assert "uppercase label." in out
        assert "*FIGURE 1." not in out


class TestMarkdownToLatexAppliesNormalization:
    """End-to-end: markdown_to_latex() must not emit duplicate captions."""

    SAMPLE_MD = (
        "## Section\n\n"
        "![Figure 1](figures/recovery.png)\n\n"
        "*Figure 1. Recovery factors versus depletion stage.*\n\n"
        "Body paragraph.\n\n"
        "![Figure 2](figures/method.png)\n\n"
        "*Figure 2. Method accuracy comparison.*\n"
    )

    def test_no_placeholder_caption_in_latex(self):
        tex = markdown_to_latex(self.SAMPLE_MD)
        assert "\\caption{Figure 1}" not in tex
        assert "\\caption{Figure 2}" not in tex

    def test_real_captions_present_in_latex(self):
        tex = markdown_to_latex(self.SAMPLE_MD)
        assert "Recovery factors versus depletion stage." in tex
        assert "Method accuracy comparison." in tex

    def test_no_duplicate_italic_body_line(self):
        tex = markdown_to_latex(self.SAMPLE_MD)
        assert "\\textit{Figure 1." not in tex
        assert "\\textit{Figure 2." not in tex


class TestAllRenderersShareTheHelper:
    """Defence-in-depth: every renderer entry point must re-export the same helper."""

    @pytest.mark.parametrize(
        "module_name",
        ["render_all", "render_html_generic", "word_renderer"],
    )
    def test_renderer_imports_shared_helper(self, module_name):
        # If a renderer drops or forks the helper, this test fails immediately.
        renderer = pytest.importorskip(module_name)
        assert hasattr(renderer, "normalize_figure_captions"), (
            module_name + " must expose normalize_figure_captions"
        )
        assert (
            renderer.normalize_figure_captions is normalize_figure_captions
        ), module_name + " must use the shared helper, not a local copy"
