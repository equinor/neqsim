"""Reproduce (and later verify the fix for) the DistillationColumn
re-solve-with-changed-feed accumulation, using the SAME local dev build that
column_study.py uses.

Question under test:
    column_study.py builds a FRESH column and calls col.run() exactly ONCE,
    so it never exercises a re-solve with a changed feed. The integrated ASGB
    model calls process.run_step() ~20x, and the column feed CHANGES every
    step (recycles load it). Does re-solving the SAME column with a CHANGED
    feed make the column products grow without bound?

Run:
    python examples/notebooks/column_resolve_repro.py
"""
from __future__ import annotations

import contextlib
import io
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

# Importing column_study starts the JVM on the LOCAL dev build and gives us
# the exact same build_base_fluid / make_stream / build_column / constants.
with contextlib.redirect_stdout(io.StringIO()):
    import column_study as cs  # noqa: E402

# All result rows are written here so the Java solver's stdout spam never
# hides them.
OUT_PATH = HERE / "column_resolve_repro_out.txt"
_OUT = open(OUT_PATH, "w", encoding="utf-8")


def emit(text=""):
    _OUT.write(text + "\n")
    _OUT.flush()


def products(col):
    g = float(col.getGasOutStream().getFlowRate("kg/hr"))
    l = float(col.getLiquidOutStream().getFlowRate("kg/hr"))
    mb = float(col.getMassBalance("kg/hr"))
    return g, l, mb


def run_quiet(col):
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        col.run()


def set_feed(stream, kg_hr):
    stream.setFlowRate(kg_hr, "kg/hr")
    stream.run()
    stream.run()
    stream.run()


def header(title):
    emit("\n" + "=" * 78)
    emit(" " + title)
    emit("=" * 78)
    emit(f"  {'iter':>4}{'feed main':>12}{'feed refl':>11}"
         f"{'gasOut':>12}{'liqOut':>13}{'gas+liq':>13}{'massBal':>12}")
    emit("  " + "-" * 73)


def line(i, fmain, frefl, g, l, mb, tag=""):
    emit(f"  {str(i):>4}{fmain:>12,.0f}{frefl:>11,.0f}"
         f"{g:>12,.0f}{l:>13,.0f}{g + l:>13,.0f}{mb:>12,.0f}  {tag}")


def build():
    with contextlib.redirect_stdout(io.StringIO()):
        base = cs.build_base_fluid(eos_name="SRK", tbp_model="PedersenSRK")
        feed = cs.make_stream(base, "main_feed", cs.MANUAL_FEED,
                              cs.MANUAL_FEED["mass_flow_kg_hr"], "kg/hr")
        topf = cs.make_stream(base, "top_reflux", cs.MANUAL_TOP_FEED,
                              cs.MANUAL_TOP_FEED["mass_flow_kg_hr"], "kg/hr")
        col, _mfns, _tfns = cs.build_column(feed, topf)
    return base, feed, topf, col


def fmain(feed):
    return float(feed.getFlowRate("kg/hr"))


def frefl(topf):
    return float(topf.getFlowRate("kg/hr"))


# ---------------------------------------------------------------------------
# TEST 1 — re-run with the SAME feed (what the user tried; expected idempotent)
# ---------------------------------------------------------------------------
base, feed, topf, col = build()
header("TEST 1: same fixed feed, col.run() repeated 6x  (expect: stable, massBal~0)")
for i in range(6):
    run_quiet(col)
    g, l, mb = products(col)
    line("build" if i == 0 else i, fmain(feed), frefl(topf), g, l, mb)
emit(f"\n  answer ref: feed ~107,040 -> gasOut ~23,745 + liqOut ~83,561 = ~107,306")

# ---------------------------------------------------------------------------
# TEST 2 — re-run the SAME column with a CHANGED main feed each iteration
#          (mimics the integrated recycle loop loading the column)
# ---------------------------------------------------------------------------
base, feed, topf, col = build()
header("TEST 2: SAME column, main feed CHANGES each run  (the integrated case)")
ramp = [20000, 60000, 99381, 99381, 99381, 99381, 99381, 99381]
for i, fl in enumerate(ramp):
    set_feed(feed, fl)
    run_quiet(col)
    g, l, mb = products(col)
    line(i, fmain(feed), frefl(topf), g, l, mb)

# ---------------------------------------------------------------------------
# TEST 3 — same as TEST 2 but force re-initialization before each run
#          (candidate Python-level workaround)
# ---------------------------------------------------------------------------
base, feed, topf, col = build()
header("TEST 3: SAME column, feed changes, setDoInitializion(True) before each run")
for i, fl in enumerate(ramp):
    set_feed(feed, fl)
    col.setDoInitializion(True)
    run_quiet(col)
    g, l, mb = products(col)
    line(i, fmain(feed), frefl(topf), g, l, mb)

emit("\nDONE.")
_OUT.close()
print(f"Results written to {OUT_PATH}")
