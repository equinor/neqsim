"""
NeqSim Bootstrap — Always loads NeqSim from the local devtools build.

This module provides a single entry point for all paperlab tools to get
the NeqSim Java gateway. It uses the devtools/neqsim_dev_setup.py to
start the JVM from the locally compiled JAR (target/classes + shaded JAR),
so any Java code changes are picked up immediately after ``mvnw compile``.

Usage::

    from tools.neqsim_bootstrap import get_jneqsim, get_ns

    # Option A: jneqsim gateway (drop-in replacement for ``from neqsim import jneqsim``)
    jneqsim = get_jneqsim()
    fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)

    # Option B: namespace with class shortcuts (ns.SystemSrkEos, ns.Stream, etc.)
    ns = get_ns()
    fluid = ns.SystemSrkEos(298.15, 50.0)

Environment variable override:
    NEQSIM_PROJECT_ROOT  — path to the neqsim repo root (auto-detected by default)
"""

import os
import sys
from pathlib import Path

import jpype

# ── Locate project root (two levels up from this file: tools/ -> neqsim-paperlab/ -> repo root)
_THIS_DIR = Path(__file__).resolve().parent
_PAPERLAB_DIR = _THIS_DIR.parent
_DEFAULT_PROJECT_ROOT = _PAPERLAB_DIR.parent

# Allow override via environment variable
PROJECT_ROOT = Path(os.environ.get("NEQSIM_PROJECT_ROOT", str(_DEFAULT_PROJECT_ROOT)))

# Add devtools/ to sys.path so we can import neqsim_dev_setup
_DEVTOOLS_DIR = PROJECT_ROOT / "devtools"
if str(_DEVTOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_DEVTOOLS_DIR))

# Module-level singletons (initialised lazily)
_jneqsim = None
_ns = None


def _ensure_jvm():
    """Start the JVM via neqsim_dev_setup if not already running."""
    global _ns
    if jpype.isJVMStarted():
        return

    from neqsim_dev_setup import neqsim_init, neqsim_classes

    ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)
    ns = neqsim_classes(ns)
    _ns = ns


def get_jneqsim():
    """Return a jpype JPackage gateway equivalent to ``from neqsim import jneqsim``.

    This is a drop-in replacement: ``jneqsim.thermo.system.SystemSrkEos(...)``
    works exactly the same way, but the classes come from the local build.
    """
    global _jneqsim
    if _jneqsim is not None:
        return _jneqsim

    _ensure_jvm()
    _jneqsim = jpype.JPackage("neqsim")
    return _jneqsim


def get_ns():
    """Return the devtools namespace with class shortcuts.

    Attributes like ``ns.SystemSrkEos``, ``ns.Stream``, ``ns.ProcessSystem``
    are available directly.
    """
    global _ns
    if _ns is not None:
        return _ns

    _ensure_jvm()
    return _ns


def recompile_and_reload():
    """Recompile the Java source and restart the JVM.

    Only works inside Jupyter notebooks (restarts the kernel).
    For standalone scripts, just recompile externally and re-run.
    """
    from neqsim_dev_setup import neqsim_compile
    neqsim_compile(project_root=PROJECT_ROOT, restart_kernel=True)
