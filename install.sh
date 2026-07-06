#!/usr/bin/env bash
# install.sh - Bootstrap the NeqSim devtools package on macOS/Linux.
#
# Fixes the common "pip not available / python not available" errors by
# locating a working Python interpreter (python3 or python) and running
# `python -m pip` for you.
#
# Usage (from the repo root):
#     ./install.sh              # install neqsim-dev-setup (editable)
#     ./install.sh pdf          # also install the optional [pdf] extras
#     ./install.sh ocr          # also install the optional [ocr] extras
#     ./install.sh --uv         # use the fast 'uv' package manager instead of pip
#     ./install.sh pdf --uv     # extras + uv
#
# No need to activate a venv first, and no need to have `pip` on PATH.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVTOOLS="$REPO_ROOT/devtools"

# Parse args: --uv anywhere selects uv; the first non-flag arg is the extras name.
USE_UV=0
EXTRAS=""
for arg in "$@"; do
    case "$arg" in
        --uv) USE_UV=1 ;;
        *) EXTRAS="$arg" ;;
    esac
done

echo "NeqSim devtools installer"
echo "Locating a working Python interpreter..."

PYTHON=""
# Prefer an active virtual environment so we install where the user expects.
if [ -n "${VIRTUAL_ENV:-}" ] && [ -x "$VIRTUAL_ENV/bin/python" ]; then
    PYTHON="$VIRTUAL_ENV/bin/python"
else
    for cand in python3 python; do
        if command -v "$cand" >/dev/null 2>&1; then
            # Require the interpreter to actually run and report a version.
            if "$cand" -c "import sys; print(sys.version.split()[0])" >/dev/null 2>&1; then
                PYTHON="$cand"
                break
            fi
        fi
    done
fi

if [ -z "$PYTHON" ]; then
    echo ""
    echo "ERROR: No working Python interpreter was found." >&2
    echo "" >&2
    echo "Fix: install Python 3.8+:" >&2
    echo "  * macOS:        brew install python   (or https://www.python.org/downloads/)" >&2
    echo "  * Debian/Ubuntu: sudo apt install python3 python3-pip python3-venv" >&2
    echo "  * Fedora/RHEL:   sudo dnf install python3 python3-pip" >&2
    exit 1
fi

VERSION="$("$PYTHON" -c "import sys; print(sys.version.split()[0])")"
PYEXE="$("$PYTHON" -c "import sys; print(sys.executable)")"
echo "Using Python $VERSION via '$PYTHON'"

# Build the install target (support optional extras like devtools[pdf]).
TARGET="$DEVTOOLS"
if [ -n "$EXTRAS" ]; then
    TARGET="$DEVTOOLS[$EXTRAS]"
fi

if [ "$USE_UV" -eq 1 ]; then
    # Fast path: install with uv into the detected interpreter's environment.
    if ! command -v uv >/dev/null 2>&1; then
        echo "" >&2
        echo "ERROR: --uv was requested but 'uv' is not installed." >&2
        echo "Install uv with one of:" >&2
        echo "  * curl -LsSf https://astral.sh/uv/install.sh | sh" >&2
        echo "  * brew install uv" >&2
        echo "  * $PYTHON -m pip install uv" >&2
        echo "Or re-run without --uv to use pip." >&2
        exit 1
    fi
    echo "Installing (editable, via uv): $TARGET"
    uv pip install --python "$PYEXE" -e "$TARGET"
else
    # Ensure pip is available for this interpreter.
    if ! "$PYTHON" -m pip --version >/dev/null 2>&1; then
        echo "pip not found for this interpreter - bootstrapping with ensurepip..."
        if ! "$PYTHON" -m ensurepip --upgrade >/dev/null 2>&1; then
            echo "ERROR: could not bootstrap pip." >&2
            echo "  Debian/Ubuntu: sudo apt install python3-pip" >&2
            exit 1
        fi
    fi

    echo "Installing (editable): $TARGET"
    "$PYTHON" -m pip install -e "$TARGET"
fi

echo ""
echo "Done. Verify with:"
echo "  $PYTHON -m neqsim_cli --help"
echo "If the 'neqsim' command is not found, see devtools/README.md > Troubleshooting."
