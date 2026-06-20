#!/usr/bin/env python3
"""Cross-platform Spotless runner for pre-commit hooks.

Invokes the Maven wrapper (``mvnw.cmd`` on Windows, ``./mvnw`` elsewhere) to run
a Spotless goal. This indirection lets the same ``.pre-commit-config.yaml`` work
on Windows, macOS, and Linux: the previous ``./mvnw`` entry only worked on
Unix-like shells and failed on Windows with ``Executable `/bin/sh` not found``.

Usage:
    python devtools/run_spotless.py apply   # reformat Java (pre-commit stage)
    python devtools/run_spotless.py check   # verify formatting (pre-push stage)
"""
import os
import subprocess
import sys


def main():
    """Run the requested Spotless goal via the OS-appropriate Maven wrapper.

    Returns:
        int: the Maven process exit code (0 on success).
    """
    goal = sys.argv[1] if len(sys.argv) > 1 else "check"
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    wrapper = os.path.join(repo_root, "mvnw.cmd" if os.name == "nt" else "mvnw")
    cmd = [wrapper, "spotless:" + goal, "-q"]
    return subprocess.call(cmd, cwd=repo_root)


if __name__ == "__main__":
    raise SystemExit(main())
