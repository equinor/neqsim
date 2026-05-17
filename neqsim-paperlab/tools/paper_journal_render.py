"""Journal-article render path — converts a paper.md into LaTeX for major
journal classes (IEEE, ACS, Elsevier/elsarticle, Springer Nature) via
Pandoc, with class-specific templates.

Each preset emits a small ``\\documentclass{...}`` preamble and bibliography
style, plus the right options for two-column / single-column layouts.

Usage::

    render_paper_journal(paper_dir, journal="ieee", out=None)

The user keeps writing in Markdown; only the final tex/PDF is journal-
specific.
"""
from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


PRESETS: dict[str, dict] = {
    "ieee": {
        "documentclass": r"\documentclass[journal,onecolumn]{IEEEtran}",
        "extra_preamble": r"\usepackage{cite}\usepackage{amsmath,amssymb,amsfonts}\usepackage{graphicx}",
        "bib_style": "IEEEtran",
        "natbib": False,
    },
    "acs": {
        "documentclass": r"\documentclass[journal=jacsat,manuscript=article]{achemso}",
        "extra_preamble": r"\usepackage{amsmath,amssymb}\usepackage{graphicx}",
        "bib_style": "achemso",
        "natbib": False,
    },
    "elsevier": {
        "documentclass": r"\documentclass[review,12pt,a4paper]{elsarticle}",
        "extra_preamble": r"\usepackage{amsmath,amssymb}\usepackage{graphicx}\usepackage{lineno}\linenumbers",
        "bib_style": "elsarticle-num",
        "natbib": True,
    },
    "springer": {
        "documentclass": r"\documentclass[sn-mathphys-num]{sn-jnl}",
        "extra_preamble": r"\usepackage{amsmath,amssymb}\usepackage{graphicx}",
        "bib_style": "sn-mathphys-num",
        "natbib": True,
    },
    "rsc": {
        "documentclass": r"\documentclass[journal,9pt,twoside,twocolumn]{RSC}",
        "extra_preamble": r"\usepackage{amsmath,amssymb}\usepackage{graphicx}",
        "bib_style": "rsc",
        "natbib": True,
    },
}


def _build_template(journal: str) -> str:
    p = PRESETS[journal]
    natbib_line = r"\usepackage[numbers]{natbib}" if p["natbib"] else ""
    return rf"""$documentclass$
{p['extra_preamble']}
{natbib_line}

\title{{$title$}}
$if(author)$\author{{$for(author)$$author$$sep$ \and $endfor$}}$endif$
$if(date)$\date{{$date$}}$endif$

\begin{{document}}
\maketitle

$if(abstract)$
\begin{{abstract}}
$abstract$
\end{{abstract}}
$endif$

$body$

$if(bibliography)$
\bibliographystyle{{{p['bib_style']}}}
\bibliography{{$for(bibliography)$$bibliography$$sep$,$endfor$}}
$endif$
\end{{document}}
"""


def render_paper_journal(paper_dir, journal: str = "ieee", out: Path | None = None) -> Path:
    journal = journal.lower()
    if journal not in PRESETS:
        raise ValueError(f"Unknown journal '{journal}'. Choose: {sorted(PRESETS)}")
    if shutil.which("pandoc") is None:
        raise RuntimeError("pandoc not found on PATH")

    paper_dir = Path(paper_dir)
    paper_md = paper_dir / "paper.md"
    if not paper_md.exists():
        raise FileNotFoundError(paper_md)

    template = _build_template(journal)
    template_path = paper_dir / f".pandoc-template-{journal}.tex"
    template_path.write_text(template, encoding="utf-8")

    out = out or paper_dir / f"paper.{journal}.tex"
    bib = paper_dir / "refs.bib"

    cmd = [
        "pandoc",
        str(paper_md),
        "-f",
        "markdown+tex_math_dollars+pipe_tables+raw_html+raw_tex",
        "-t",
        "latex",
        "-o",
        str(out),
        "--template",
        str(template_path),
        f"--variable=documentclass:{PRESETS[journal]['documentclass']}",
        "--standalone",
    ]
    if bib.exists():
        cmd += [f"--bibliography={bib}"]

    print(f"[journal] rendering {journal.upper()} → {out}")
    subprocess.run(cmd, check=True, cwd=paper_dir)
    print(f"[journal] wrote {out}")

    # Try to compile to PDF if latexmk or pdflatex is available.
    if shutil.which("latexmk"):
        try:
            subprocess.run(
                ["latexmk", "-pdf", "-interaction=nonstopmode", str(out.name)],
                cwd=paper_dir,
                check=False,
                timeout=120,
            )
            pdf = out.with_suffix(".pdf")
            if pdf.exists():
                print(f"[journal] compiled {pdf}")
        except Exception as e:
            print(f"[journal] latexmk failed (non-fatal): {e}")

    return out
