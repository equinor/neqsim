"""
book_notebook_planner — LLM-driven notebook generator for long-form books.

Why this exists
---------------
The ``book_writer`` orchestrator drafts prose section-by-section and embeds
``![caption](figures/<file>)`` references whenever the section outline lists
figures. But the figure files do not exist yet — they have to be produced by
real NeqSim simulations whose code lives in chapter notebooks.

Manually authoring 100+ notebooks for a 1000-page book is impractical. This
module closes the loop:

1. Read ``chapter_outlines.yaml`` (already populated with per-section
   ``figures: [{file, caption, notebook}]`` entries by ``book-expand-outline``).
2. Group figures by their target notebook filename.
3. For each unique notebook, ask the LLM to produce a self-contained Jupyter
   notebook that:

   * sets up NeqSim (``from neqsim import jneqsim``),
   * builds the simulation needed by the section,
   * generates each PNG figure into ``../figures/<file>`` (relative to
     ``chapters/<ch>/notebooks/`` so the figure ends up in
     ``chapters/<ch>/figures/`` where ``stitch_chapter`` and
     ``book-build`` discover it).

4. Write the notebook to ``chapters/<ch>/notebooks/<name>.ipynb`` and
   checkpoint it so the operation is resumable.

The downstream loop is then::

    book-expand-outline   # plan sections + figures
    book-plan-notebooks   # this module — generate notebook stubs
    book-write            # draft prose with inline figure refs
    book-run-notebooks    # execute notebooks → figures/*.png on disk
    book-build            # render HTML/PDF/EPUB

A re-run skips already-generated notebooks unless ``--force`` is set, so
notebook authoring can be incrementally improved by hand without losing
progress.
"""

from __future__ import annotations

import json
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import yaml

# tools/ is on sys.path when invoked via paperflow.py
from llm_client import chat, has_any_provider, LLMError, estimate_tokens
from book_writer import (
    ChapterSpec,
    SectionSpec,
    _load_chapter_specs,
    DEFAULT_PAGE_WORDS,
)
from figure_templates import render as render_figure_code


PROGRESS_FILENAME = ".book_notebook_plan_progress.json"


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class NotebookPlan:
    """A grouped set of figures the LLM must produce in one notebook."""

    chapter_dir: str
    chapter_number: int
    chapter_title: str
    notebook_filename: str            # e.g. "4_3_dehydration.ipynb"
    section_id: str                   # e.g. "4.3"
    section_heading: str
    section_key_points: List[str]
    figures: List[Dict[str, str]]     # [{file, caption, notebook}, ...]


# ---------------------------------------------------------------------------
# Progress / checkpoint
# ---------------------------------------------------------------------------

def _progress_path(book_dir: Path) -> Path:
    return book_dir / PROGRESS_FILENAME


def load_progress(book_dir: Path) -> Dict[str, Any]:
    p = _progress_path(book_dir)
    if not p.exists():
        return {"started": None, "notebooks": {}}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {"started": None, "notebooks": {}}


def save_progress(book_dir: Path, progress: Dict[str, Any]) -> None:
    _progress_path(book_dir).write_text(
        json.dumps(progress, indent=2), encoding="utf-8"
    )


# ---------------------------------------------------------------------------
# Plan derivation — outline → notebook tasks
# ---------------------------------------------------------------------------

def derive_notebook_plans(
    specs: List[ChapterSpec],
    *,
    chapter_filter: Optional[List[str]] = None,
) -> List[NotebookPlan]:
    """Group section figures by notebook filename → ``NotebookPlan``.

    Sections whose ``figures`` list is empty, or whose figure entries lack
    a ``notebook`` field, contribute nothing to the plan.
    """
    plans: List[NotebookPlan] = []

    for ch in specs:
        if chapter_filter and ch.dir not in chapter_filter:
            continue
        for sec in ch.sections:
            if not sec.figures:
                continue

            # Group this section's figures by their target notebook file.
            by_nb: Dict[str, List[Dict[str, str]]] = {}
            for fig in sec.figures:
                nb = (fig.get("notebook") or "").strip()
                if not nb:
                    continue
                if not nb.endswith(".ipynb"):
                    nb = nb + ".ipynb"
                by_nb.setdefault(nb, []).append(fig)

            for nb_name, figs in by_nb.items():
                plans.append(
                    NotebookPlan(
                        chapter_dir=ch.dir,
                        chapter_number=ch.number,
                        chapter_title=ch.title,
                        notebook_filename=nb_name,
                        section_id=sec.id,
                        section_heading=sec.heading,
                        section_key_points=list(sec.key_points),
                        figures=figs,
                    )
                )

    return plans


# ---------------------------------------------------------------------------
# LLM prompts
# ---------------------------------------------------------------------------

_NOTEBOOK_SYSTEM = """\
You are an expert NeqSim Python author generating a self-contained Jupyter
notebook that produces specific PNG figures for a chapter of an oil & gas
engineering textbook.

Hard rules:

1. NEQSIM ONLY — use the high-level Python wrapper:

       from neqsim import jneqsim

   Build fluids via ``jneqsim.thermo.system.SystemSrkEos`` (or PR / CPA /
   GERG-2008 if appropriate), call ``setMixingRule("classic")`` after adding
   components, and call ``fluid.initProperties()`` after any flash.

2. UNITS — SI: K, Pa, J, kg, m, mol. Bar acceptable for pressure. Always
   set/read with explicit unit strings where the API allows.

3. FIGURES — for every entry in the figure list, the notebook MUST contain
   a code cell that produces a matplotlib figure and saves it as

       fig.savefig("../figures/<file>", dpi=150, bbox_inches="tight")

   The path uses ``../figures/`` because notebooks live in
   ``chapters/<ch>/notebooks/`` and figures must end up in
   ``chapters/<ch>/figures/``. Use exactly the filenames given.

4. STRUCTURE — alternate one short markdown cell (≤4 lines) with one
   focused code cell. Order: (a) setup + imports, (b) one section per
   figure: prep → compute → plot → savefig, (c) a final markdown cell
   summarising what was produced.

5. SELF-CONTAINED — no external CSV, no relative imports, no network. All
   data either hard-coded or generated from NeqSim. Keep total runtime
   under 60 seconds; use coarse grids (≤50 points) and few components.

6. NO PLACEHOLDERS — every code cell must be runnable as-is. Never write
   ``# TODO`` or ``...`` in code.

7. OUTPUT — return ONLY a JSON object:

       {"cells": [
           {"type": "markdown", "source": "..."},
           {"type": "code",     "source": "..."},
           ...
       ]}

   No prefatory text, no closing remarks, no triple-backtick fence.
"""


_NOTEBOOK_USER_TEMPLATE = """\
Book: {book_title}
Chapter {ch_num}: {ch_title}
Section: {sec_id} — {sec_heading}
Notebook to produce: chapters/{ch_dir}/notebooks/{nb_name}

Section key points (use these as the technical context):
{key_points_block}

Figures this notebook must produce (EXACT filenames, one savefig per file):
{figures_block}

Remember: relative path is ``../figures/<file>``. Each figure must be a
distinct matplotlib figure, with axis labels (with units), a title, a
legend if multiple series, and grid lines.

Return the JSON cell list described in the system prompt.
"""


def _strip_json_fence(text: str) -> str:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return text


# ---------------------------------------------------------------------------
# Notebook construction
# ---------------------------------------------------------------------------

def _build_ipynb(cells: List[Dict[str, str]]) -> Dict[str, Any]:
    """Wrap a list of ``{type, source}`` dicts as an nbformat-4 notebook."""
    nb_cells: List[Dict[str, Any]] = []
    for c in cells:
        ctype = c.get("type", "code")
        src = c.get("source", "")
        if isinstance(src, list):
            src = "".join(src)
        # Split into list-with-trailing-newlines (nbformat convention).
        lines = src.splitlines(keepends=True) or [""]
        if ctype == "markdown":
            nb_cells.append({
                "cell_type": "markdown",
                "metadata": {},
                "source": lines,
            })
        else:
            nb_cells.append({
                "cell_type": "code",
                "metadata": {},
                "source": lines,
                "outputs": [],
                "execution_count": None,
            })

    return {
        "cells": nb_cells,
        "metadata": {
            "kernelspec": {
                "display_name": "Python 3",
                "language": "python",
                "name": "python3",
            },
            "language_info": {
                "name": "python",
                "version": "3.11",
            },
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }


_FALLBACK_HEADER = """\
# {ch_title} — {sec_heading}

This notebook is auto-generated by ``book-plan-notebooks``. It produces the
figures referenced by section {sec_id}. Replace any placeholder logic with
problem-specific NeqSim code as needed.
"""


_FALLBACK_FIGURE_CODE = """\
import os
import matplotlib.pyplot as plt
from neqsim import jneqsim

os.makedirs("../figures", exist_ok=True)

fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

P_range = [10.0, 30.0, 50.0, 70.0, 100.0]
density = []
for p in P_range:
    fluid.setPressure(p)
    ops.TPflash()
    fluid.initProperties()
    density.append(float(fluid.getDensity("kg/m3")))

fig, ax = plt.subplots(figsize=(6.5, 4.0))
ax.plot(P_range, density, marker="o", lw=2)
ax.set_xlabel("Pressure (bara)")
ax.set_ylabel("Density (kg/m³)")
ax.set_title("{caption}")
ax.grid(True, alpha=0.3)
fig.tight_layout()
fig.savefig("../figures/{file}", dpi=150, bbox_inches="tight")
plt.close(fig)
print("wrote ../figures/{file}")
"""


def _build_fallback_cells(plan: NotebookPlan) -> List[Dict[str, str]]:
    """Build a generic, runnable notebook when no LLM provider is available."""
    cells: List[Dict[str, str]] = [
        {
            "type": "markdown",
            "source": _FALLBACK_HEADER.format(
                ch_title=plan.chapter_title,
                sec_id=plan.section_id,
                sec_heading=plan.section_heading,
            ),
        }
    ]
    for fig in plan.figures:
        cells.append({
            "type": "markdown",
            "source": f"## Figure — {fig.get('caption','')}\n\nProduces ``{fig.get('file','')}``.\n",
        })
        cells.append({
            "type": "code",
            "source": render_figure_code(
                caption=fig.get("caption", "") or "",
                file=fig.get("file", "figure.png"),
            ),
        })
    cells.append({
        "type": "markdown",
        "source": "All figures written to ``../figures/``.\n",
    })
    return cells


# ---------------------------------------------------------------------------
# Per-notebook generation
# ---------------------------------------------------------------------------

def plan_one_notebook(
    book_dir: Path,
    plan: NotebookPlan,
    *,
    book_title: str,
    provider: str,
    model: str,
    max_tokens: int = 4000,
    use_llm: bool = True,
) -> Path:
    """Generate one ``.ipynb`` file from a ``NotebookPlan``.

    When ``use_llm`` is False (or no provider is configured) a deterministic
    fallback notebook is produced with a generic NeqSim plot per figure.
    """
    out_dir = book_dir / "chapters" / plan.chapter_dir / "notebooks"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / plan.notebook_filename

    cells: Optional[List[Dict[str, str]]] = None

    if use_llm and has_any_provider():
        kp = "\n".join(f"  - {p}" for p in plan.section_key_points) or "  - (none)"
        figs = "\n".join(
            f"  - file: {f.get('file','')}  caption: {f.get('caption','')}"
            for f in plan.figures
        )
        user = _NOTEBOOK_USER_TEMPLATE.format(
            book_title=book_title,
            ch_num=plan.chapter_number,
            ch_title=plan.chapter_title,
            ch_dir=plan.chapter_dir,
            nb_name=plan.notebook_filename,
            sec_id=plan.section_id,
            sec_heading=plan.section_heading,
            key_points_block=kp,
            figures_block=figs,
        )
        try:
            raw = chat(
                [
                    {"role": "system", "content": _NOTEBOOK_SYSTEM},
                    {"role": "user", "content": user},
                ],
                provider=provider,
                model=model,
                max_tokens=max_tokens,
                temperature=0.2,
            )
            parsed = json.loads(_strip_json_fence(raw))
            cell_list = parsed.get("cells")
            if isinstance(cell_list, list) and cell_list:
                cells = cell_list
        except (LLMError, json.JSONDecodeError, ValueError) as exc:
            print(f"    [warn] LLM notebook generation failed for "
                  f"{plan.notebook_filename}: {exc} — using fallback")

    if cells is None:
        cells = _build_fallback_cells(plan)

    nb = _build_ipynb(cells)
    out_path.write_text(json.dumps(nb, indent=1), encoding="utf-8")
    return out_path


# ---------------------------------------------------------------------------
# Public driver
# ---------------------------------------------------------------------------

def estimate_plan_cost(plans: List[NotebookPlan]) -> Dict[str, Any]:
    """Rough cost estimate for the LLM notebook-generation step."""
    n = len(plans)
    tokens_in = n * 1500
    tokens_out = n * 2000
    usd = tokens_in * 2.5e-6 + tokens_out * 10e-6
    return {
        "n_notebooks": n,
        "tokens_in": tokens_in,
        "tokens_out": tokens_out,
        "usd_estimate": round(usd, 2),
    }


def plan_book_notebooks(
    book_dir: Path,
    *,
    provider: str = "litellm",
    model: str = "gpt-4o",
    chapter_filter: Optional[List[str]] = None,
    force: bool = False,
    use_llm: bool = True,
    max_tokens: int = 4000,
    dry_run: bool = False,
) -> List[Path]:
    """Generate one notebook per ``(section, notebook_filename)`` group.

    Parameters
    ----------
    book_dir : Path
        Path to the book project.
    chapter_filter : list of str, optional
        Restrict to these chapter directory names.
    force : bool
        Overwrite existing ``.ipynb`` files (otherwise skipped).
    use_llm : bool
        If False, always use the deterministic fallback. Useful for offline
        runs and CI.
    dry_run : bool
        Plan only — do not call the LLM and do not write any files.

    Returns
    -------
    list of Path
        Notebook files that were written (or would have been written, in
        dry-run mode).
    """
    book_dir = Path(book_dir)
    from book_builder import load_book_config  # local import: avoid cycle

    cfg = load_book_config(book_dir)
    book_title = cfg.get("title", "")

    specs = _load_chapter_specs(book_dir)
    plans = derive_notebook_plans(specs, chapter_filter=chapter_filter)

    cost = estimate_plan_cost(plans)
    print("Notebook planning:")
    print(f"  notebooks        : {cost['n_notebooks']}")
    print(f"  est. tokens in   : {cost['tokens_in']}")
    print(f"  est. tokens out  : {cost['tokens_out']}")
    print(f"  est. USD (~mid)  : ${cost['usd_estimate']}")

    if dry_run:
        print("Dry run — no notebooks written.")
        for p in plans:
            print(f"  would write chapters/{p.chapter_dir}/notebooks/{p.notebook_filename}"
                  f"  ({len(p.figures)} figures, section {p.section_id})")
        return []

    progress = load_progress(book_dir)
    progress.setdefault("started", time.strftime("%Y-%m-%dT%H:%M:%S"))
    progress.setdefault("notebooks", {})

    written: List[Path] = []
    for plan in plans:
        key = f"{plan.chapter_dir}/{plan.notebook_filename}"
        out_path = book_dir / "chapters" / plan.chapter_dir / "notebooks" / plan.notebook_filename
        if out_path.exists() and not force:
            print(f"  skip {key} (exists)")
            progress["notebooks"][key] = {"status": "skipped", "path": str(out_path)}
            continue

        print(f"  plan {key} — section {plan.section_id} — {len(plan.figures)} figures")
        try:
            written_path = plan_one_notebook(
                book_dir,
                plan,
                book_title=book_title,
                provider=provider,
                model=model,
                max_tokens=max_tokens,
                use_llm=use_llm,
            )
            progress["notebooks"][key] = {
                "status": "ok",
                "path": str(written_path),
                "figures": [f.get("file") for f in plan.figures],
            }
            written.append(written_path)
        except Exception as exc:  # pragma: no cover — defensive
            print(f"    [error] {key}: {exc}")
            progress["notebooks"][key] = {"status": "error", "error": str(exc)}
        save_progress(book_dir, progress)

    print(f"Wrote {len(written)} notebook(s).")
    return written
