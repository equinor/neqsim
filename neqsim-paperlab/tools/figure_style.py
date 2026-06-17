"""
Publication-quality figure styling for NeqSim paperlab.

Provides journal-preset matplotlib configurations using SciencePlots.
Import this module at the top of any generate_figures.py script.

Usage::

    from tools.figure_style import apply_style, PALETTE, FIG_SINGLE, FIG_DOUBLE, save_fig

    apply_style("elsevier")           # or "ieee", "nature", "acs", "default"
    fig, ax = plt.subplots(figsize=FIG_SINGLE)
    ax.plot(x, y, color=PALETTE[0])
    save_fig(fig, "fig01_convergence.png")
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

try:
    import scienceplots  # noqa: F401 — registers styles on import
    _HAS_SCIENCEPLOTS = True
except ImportError:
    _HAS_SCIENCEPLOTS = False

from pathlib import Path


# ── Color Palettes ────────────────────────────────────────────────────────
PALETTE = ["#2171b5", "#e6550d", "#31a354", "#756bb1", "#e7298a", "#66a61e",
           "#a6761d", "#666666"]
BLUE, ORANGE, GREEN, PURPLE, PINK, LIME = PALETTE[:6]
GREY = "#636363"

# ── Figure Sizes (inches) ────────────────────────────────────────────────
# Elsevier / ACS / Wiley single-column ~3.5 in, double ~7.0 in
FIG_SINGLE = (3.5, 2.8)
FIG_SINGLE_TALL = (3.5, 3.5)
FIG_DOUBLE = (7.0, 3.5)
FIG_DOUBLE_TALL = (7.0, 5.5)
FIG_SQUARE = (3.5, 3.5)


# ── Journal Style Presets ─────────────────────────────────────────────────
_JOURNAL_STYLES = {
    "elsevier": ["science", "no-latex"],           # FPE, CACE, CES
    "ieee": ["science", "ieee", "no-latex"],
    "nature": ["science", "nature", "no-latex"],
    "acs": ["science", "no-latex"],                # IECR, ACS journals
    "default": ["science", "no-latex"],
}

# Fallback rcParams when SciencePlots is not installed
_FALLBACK_RC = {
    "font.family": "serif",
    "font.serif": ["Times New Roman", "DejaVu Serif", "serif"],
    "mathtext.fontset": "dejavuserif",
    "font.size": 9,
    "axes.labelsize": 10,
    "axes.titlesize": 10,
    "axes.titleweight": "bold",
    "axes.linewidth": 0.6,
    "legend.fontsize": 8,
    "legend.framealpha": 0.9,
    "legend.edgecolor": "0.7",
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "xtick.direction": "in",
    "ytick.direction": "in",
    "xtick.major.size": 3,
    "ytick.major.size": 3,
    "xtick.minor.size": 1.5,
    "ytick.minor.size": 1.5,
    "xtick.minor.visible": True,
    "ytick.minor.visible": True,
    "grid.linewidth": 0.3,
    "grid.alpha": 0.4,
    "lines.linewidth": 1.0,
    "lines.markersize": 4,
    "figure.dpi": 150,
    "savefig.dpi": 300,
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.05,
}


def apply_style(journal="elsevier"):
    """Apply a journal-appropriate matplotlib style.

    Uses SciencePlots styles when available, falls back to manually
    tuned rcParams otherwise.

    Parameters
    ----------
    journal : str
        One of "elsevier", "ieee", "nature", "acs", "default".
    """
    if _HAS_SCIENCEPLOTS:
        styles = _JOURNAL_STYLES.get(journal, _JOURNAL_STYLES["default"])
        plt.style.use(styles)
        # Override DPI for print quality
        plt.rcParams.update({"savefig.dpi": 300, "figure.dpi": 150})
    else:
        plt.rcParams.update(_FALLBACK_RC)


def save_fig(fig, filename, figures_dir=None, dpi=300, formats=None):
    """Save a figure in publication quality.

    Parameters
    ----------
    fig : matplotlib.figure.Figure
        The figure to save.
    filename : str
        Output filename (e.g. "fig01_convergence.png").
    figures_dir : str or Path, optional
        Output directory. Defaults to cwd/figures/.
    dpi : int
        Resolution (default 300, Elsevier minimum).
    formats : list of str, optional
        Additional formats to save (e.g. ["pdf", "tif"]).
    """
    if figures_dir is None:
        figures_dir = Path.cwd() / "figures"
    figures_dir = Path(figures_dir)
    figures_dir.mkdir(exist_ok=True)

    primary = figures_dir / filename
    fig.savefig(str(primary), dpi=dpi, bbox_inches="tight", pad_inches=0.05)

    if formats:
        stem = primary.stem
        for fmt in formats:
            alt = figures_dir / f"{stem}.{fmt}"
            fig.savefig(str(alt), dpi=dpi, bbox_inches="tight", pad_inches=0.05)

    plt.close(fig)
