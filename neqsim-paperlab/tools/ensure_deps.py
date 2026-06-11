"""ensure_deps — auto-install PaperLab rendering dependencies on demand.

PaperLab's Word/PDF/HTML renderers depend on a small set of Python packages
(``latex2mathml``, ``lxml``, ``python-docx``, ...). When any of these is
missing the renderers silently degrade — for example, equations fall back to
plain text instead of native Word math. To make rendering robust, the render
commands call :func:`ensure_render_deps` first, which transparently installs
any missing package into the active interpreter.

The behaviour can be controlled with the ``PAPERLAB_AUTO_INSTALL`` environment
variable:

* unset / ``1`` / ``true``  → auto-install missing packages (default)
* ``0`` / ``false``         → do not install; only warn about what is missing
"""

import importlib
import os
import subprocess
import sys


# Mapping of import name -> pip requirement spec. The import name is what we
# probe with importlib; the spec is what we hand to pip when it is missing.
_RENDER_DEPS = [
    ("latex2mathml", "latex2mathml>=3.0"),
    ("lxml", "lxml>=4.9"),
    ("docx", "python-docx>=0.8.11"),
]


def _auto_install_enabled():
    """Return True when automatic installation of missing deps is allowed."""
    val = os.environ.get("PAPERLAB_AUTO_INSTALL", "1").strip().lower()
    return val not in ("0", "false", "no", "off")


def _is_installed(import_name):
    """Return True if *import_name* can be imported in this interpreter."""
    try:
        importlib.import_module(import_name)
        return True
    except Exception:
        return False


def _pip_install(specs):
    """Install the given pip requirement *specs* into the current interpreter.

    Parameters
    ----------
    specs : list of str
        pip requirement specifiers (e.g. ``["latex2mathml>=3.0"]``).

    Returns
    -------
    bool
        True if pip exited successfully, else False.
    """
    cmd = [sys.executable, "-m", "pip", "install", "--quiet"] + list(specs)
    print("[paperlab] Installing rendering dependencies: {}".format(
        ", ".join(specs)))
    try:
        subprocess.check_call(cmd)
        return True
    except Exception as exc:  # pragma: no cover - network/permission failures
        print("[paperlab] Automatic install failed: {}".format(exc))
        print("[paperlab] Install manually with: pip install {}".format(
            " ".join(specs)))
        return False


def ensure_render_deps(deps=None):
    """Ensure rendering dependencies are importable, installing if needed.

    Parameters
    ----------
    deps : list of tuple, optional
        Sequence of ``(import_name, pip_spec)`` pairs to check. Defaults to the
        standard PaperLab render dependency set.

    Returns
    -------
    bool
        True if every dependency is importable after this call, else False.
    """
    deps = deps if deps is not None else _RENDER_DEPS

    missing = [(name, spec) for name, spec in deps if not _is_installed(name)]
    if not missing:
        return True

    missing_specs = [spec for _name, spec in missing]

    if not _auto_install_enabled():
        print("[paperlab] Missing rendering dependencies: {}".format(
            ", ".join(missing_specs)))
        print("[paperlab] Auto-install disabled (PAPERLAB_AUTO_INSTALL=0). "
              "Install with: pip install {}".format(" ".join(missing_specs)))
        return False

    if not _pip_install(missing_specs):
        return False

    # Refresh importlib caches and re-check so freshly installed packages are
    # importable without restarting the process.
    importlib.invalidate_caches()
    still_missing = [name for name, _spec in missing if not _is_installed(name)]
    if still_missing:
        print("[paperlab] Still unable to import: {}".format(
            ", ".join(still_missing)))
        return False
    return True
