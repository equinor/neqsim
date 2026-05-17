"""Render paper.md to a standalone HTML file with KaTeX math and embedded figures.

DEPRECATED: This module is a thin redirect to render_html_generic.py which
works with any paper directory.  The original version was hardcoded to the
tpflash_algorithms_2026 paper.

Usage:
    python render_html.py <paper_dir>
"""
import sys
from pathlib import Path

# Re-export main entry point from the generic renderer
from render_html_generic import render_paper_html as render_html  # noqa: F401


def main():
    """CLI entry: redirect to render_html_generic."""
    if len(sys.argv) < 2:
        print("Usage: python render_html.py <paper_dir>")
        sys.exit(1)
    paper_dir = Path(sys.argv[1])
    render_html(paper_dir)


if __name__ == "__main__":
    main()
