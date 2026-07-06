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
#
# No need to activate a venv first, and no need to have `pip` on PATH.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVTOOLS="$REPO_ROOT/devtools"
EXTRAS="${1:-}"

echo "NeqSim devtools installer"
echo "Locating a working Python interpreter..."

PYTHON=""
for cand in python3 python; do
    if command -v "$cand" >/dev/null 2>&1; then
        # Require the interpreter to actually run and report a version.
        if "$cand" -c "import sys; print(sys.version.split()[0])" >/dev/null 2>&1; then
            PYTHON="$cand"
            break
        fi
    fi
done

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
echo "Using Python $VERSION via '$PYTHON'"

# Ensure pip is available for this interpreter.
if ! "$PYTHON" -m pip --version >/dev/null 2>&1; then
    echo "pip not found for this interpreter - bootstrapping with ensurepip..."
    if ! "$PYTHON" -m ensurepip --upgrade >/dev/null 2>&1; then
        echo "ERROR: could not bootstrap pip." >&2
        echo "  Debian/Ubuntu: sudo apt install python3-pip" >&2
        exit 1
    fi
fi

# Build the install target (support optional extras like devtools[pdf]).
TARGET="$DEVTOOLS"
if [ -n "$EXTRAS" ]; then
    TARGET="$DEVTOOLS[$EXTRAS]"
fi

echo "Installing (editable): $TARGET"
"$PYTHON" -m pip install -e "$TARGET"

echo ""
echo "Done. Verify with:"
echo "  $PYTHON -m neqsim_cli --help"
echo "If the 'neqsim' command is not found, see devtools/README.md > Troubleshooting."
