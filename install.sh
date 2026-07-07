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
#     ./install.sh --install-jdk # also download a portable Temurin JDK (no admin)
#
# No need to activate a venv first, and no need to have `pip` on PATH.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVTOOLS="$REPO_ROOT/devtools"

# Parse args: --uv anywhere selects uv; the first non-flag arg is the extras name.
USE_UV=0
INSTALL_JDK=0
JDK_VERSION="21"
EXTRAS=""
for arg in "$@"; do
    case "$arg" in
        --uv) USE_UV=1 ;;
        --install-jdk) INSTALL_JDK=1 ;;
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
echo "Ensuring the 'neqsim' command is on your PATH..."
"$PYTHON" "$DEVTOOLS/ensure_on_path.py" || true

echo ""
echo "Done. Verify with:"
echo "  $PYTHON -m neqsim_cli --help"
echo "If 'neqsim' is not found in this shell, open a NEW terminal (or 'source'"
echo "your shell rc file), or use the line above."

# ── JDK advisory (non-fatal) ─────────────────────────────────────────────
# The Python devtools do not need Java, but building the NeqSim JAR
# (./mvnw install) and the local dev-notebook runtime DO. Check for a JDK so
# users on locked-down machines get the portable-JDK remedy up front.

install_portable_jdk() {
    # Download a portable Temurin (Eclipse Adoptium) JDK into the user's home
    # and write an env file to source. Requires no administrator rights. The
    # download is verified against the SHA-256 checksum from the Adoptium
    # assets API before it is extracted.
    version="${1:-21}"
    case "$(uname -s)" in
        Darwin) os="mac" ;;
        *) os="linux" ;;
    esac
    case "$(uname -m)" in
        arm64|aarch64) arch="aarch64" ;;
        *) arch="x64" ;;
    esac
    dest="$HOME/.neqsim/jdk"
    tarball="$(mktemp -t temurin-XXXX.tar.gz)"
    meta="$(mktemp)"
    assets_url="https://api.adoptium.net/v3/assets/latest/$version/hotspot?architecture=$arch&image_type=jdk&os=$os&vendor=eclipse"

    echo ""
    echo "Querying Adoptium for a portable Temurin JDK $version ($os/$arch)..."
    if command -v curl >/dev/null 2>&1; then
        curl -fSL "$assets_url" -o "$meta" || { echo "ERROR: Adoptium query failed (proxy? set HTTPS_PROXY)." >&2; rm -f "$meta"; return 1; }
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$meta" "$assets_url" || { echo "ERROR: Adoptium query failed (proxy? set HTTPS_PROXY)." >&2; rm -f "$meta"; return 1; }
    else
        echo "ERROR: neither curl nor wget is available to download the JDK." >&2
        rm -f "$meta"; return 1
    fi

    # Parse the download link + checksum with Python (already located above).
    info="$("$PYTHON" -c "import json; d=json.load(open('$meta')); b=[a['binary'] for a in d if a.get('binary',{}).get('image_type')=='jdk']; p=(b[0]['package'] if b else {}); print(p.get('link','')); print(p.get('checksum',''))" 2>/dev/null)"
    rm -f "$meta"
    link="$(printf '%s\n' "$info" | sed -n '1p')"
    checksum="$(printf '%s\n' "$info" | sed -n '2p')"
    if [ -z "$link" ]; then
        echo "ERROR: no matching JDK package found in the Adoptium response." >&2
        return 1
    fi

    echo "Downloading $link ..."
    if command -v curl >/dev/null 2>&1; then
        curl -fSL "$link" -o "$tarball" || { echo "ERROR: JDK download failed." >&2; return 1; }
    else
        wget -O "$tarball" "$link" || { echo "ERROR: JDK download failed." >&2; return 1; }
    fi

    if [ -n "$checksum" ]; then
        echo "Verifying SHA-256 checksum..."
        actual=""
        if command -v sha256sum >/dev/null 2>&1; then
            actual="$(sha256sum "$tarball" | awk '{print $1}')"
        elif command -v shasum >/dev/null 2>&1; then
            actual="$(shasum -a 256 "$tarball" | awk '{print $1}')"
        else
            echo "WARNING: no sha256 tool found; skipping verification." >&2
        fi
        if [ -n "$actual" ] && [ "$actual" != "$checksum" ]; then
            echo "ERROR: checksum mismatch - download may be corrupt or tampered." >&2
            echo "  expected: $checksum" >&2
            echo "  actual:   $actual" >&2
            rm -f "$tarball"; return 1
        fi
        [ -n "$actual" ] && echo "Checksum OK."
    else
        echo "WARNING: Adoptium returned no checksum; skipping verification." >&2
    fi

    rm -rf "$dest"; mkdir -p "$dest"
    echo "Extracting to $dest ..."
    tar -xzf "$tarball" -C "$dest"
    rm -f "$tarball"

    # Temurin extracts to a versioned subfolder (bin/java, or Contents/Home on macOS).
    jdk_home="$(find "$dest" -maxdepth 3 -type f -name java -path '*/bin/java' 2>/dev/null | head -n1 | sed 's|/bin/java$||')"
    if [ -z "$jdk_home" ]; then
        echo "ERROR: Could not find bin/java in the extracted JDK." >&2
        return 1
    fi

    env_file="$HOME/.neqsim/jdk/env.sh"
    {
        echo "export JAVA_HOME=\"$jdk_home\""
        echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    } > "$env_file"

    # Make it usable in this session too.
    export JAVA_HOME="$jdk_home"
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "Portable JDK installed (no admin):"
    echo "  JAVA_HOME = $jdk_home"
    echo "  Persist it by adding this line to your ~/.bashrc or ~/.zshrc:"
    echo "    source \"$env_file\""
    return 0
}

JDK_FOUND=0
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JDK_FOUND=1
    echo ""
    echo "JDK detected via JAVA_HOME: $JAVA_HOME"
elif command -v java >/dev/null 2>&1; then
    JDK_FOUND=1
    echo ""
    echo "JDK detected on PATH (java)."
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "Tip: set JAVA_HOME so all Java tooling agrees on one JDK."
    fi
fi

if [ "$JDK_FOUND" -eq 0 ]; then
    if [ "$INSTALL_JDK" -eq 1 ]; then
        install_portable_jdk "$JDK_VERSION" || true
    else
        echo ""
        echo "NOTE: No JDK found (needed for './mvnw install' and local dev notebooks)." >&2
        echo "Easiest fix (no admin): re-run with --install-jdk to auto-download a portable JDK:" >&2
        echo "    ./install.sh --install-jdk" >&2
        echo "Or install one manually:" >&2
        echo "  1. Download a Temurin JDK 21 .tar.gz:" >&2
        echo "       https://adoptium.net/temurin/releases/?package=jdk" >&2
        echo "  2. Extract into your home dir, e.g. \$HOME/jdk-21" >&2
        echo "  3. Add to your shell rc file:" >&2
        echo "       export JAVA_HOME=\$HOME/jdk-21" >&2
        echo "       export PATH=\$JAVA_HOME/bin:\$PATH" >&2
        echo "  (Pure Python/pip 'neqsim' use still needs a JVM present via jpype.)" >&2
        echo "Run '$PYTHON -m neqsim_cli doctor' to re-check your environment." >&2
    fi
elif [ "$INSTALL_JDK" -eq 1 ]; then
    echo ""
    echo "A JDK is already available; skipping --install-jdk."
fi
