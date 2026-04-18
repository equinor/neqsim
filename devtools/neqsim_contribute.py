#!/usr/bin/env python3
"""
neqsim_contribute.py - Guided contribution wizard for NeqSim.

Presents an interactive menu of contribution paths and walks the user
through the chosen one step-by-step.

Usage:
    neqsim contribute          # interactive menu
    neqsim contribute --skill  # jump straight to skill contribution
"""
import os
import subprocess
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)


def _ask(prompt, options):
    """Present numbered options and return the user's choice (1-based index)."""
    print()
    for i, (label, desc) in enumerate(options, 1):
        print("  {}) {} — {}".format(i, label, desc))
    print()
    while True:
        try:
            raw = input("  Choose [1-{}]: ".format(len(options)))
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)
        raw = raw.strip()
        if raw.isdigit() and 1 <= int(raw) <= len(options):
            return int(raw)
        print("  Please enter a number between 1 and {}.".format(len(options)))


def _run(cmd, cwd=None):
    """Run a command, showing output live."""
    print("  $ {}".format(cmd))
    result = subprocess.run(cmd, shell=True, cwd=cwd or PROJECT_ROOT)
    return result.returncode == 0


def _pause(msg="Press Enter to continue..."):
    try:
        input("  " + msg)
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)


def _contribute_skill():
    """Walk through creating and submitting a skill."""
    print()
    print("  -- Contribute a Skill --------------------------------------")
    print()
    print("  Skills are markdown files containing engineering knowledge")
    print("  (code patterns, design rules, troubleshooting tips) that AI")
    print("  agents load automatically when solving related tasks.")
    print()
    print("  No Java required — just domain expertise and a text editor.")
    print()

    try:
        name = input("  Skill name (e.g. neqsim-lng-operations): ").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        return

    if not name:
        print("  Cancelled.")
        return

    try:
        desc = input("  Short description (one line): ").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        return

    # Scaffold the skill
    cmd = '{} -c "import new_skill; new_skill.create_skill(\'{}\', \'{}\')"'.format(
        sys.executable, name.replace("'", ""), (desc or "").replace("'", "")
    )
    old_path = sys.path[:]
    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import new_skill
        new_skill.create_skill(name, desc if desc else None)
    finally:
        sys.path[:] = old_path

    skill_path = os.path.join(PROJECT_ROOT, ".github", "skills", name, "SKILL.md")
    if os.path.exists(skill_path):
        print()
        print("  Created: .github/skills/{}/SKILL.md".format(name))
        print()
        print("  Next steps:")
        print("    1. Edit the SKILL.md with your domain knowledge")
        print("    2. Test: ask an AI agent a question in your skill's domain")
        print("    3. Submit a PR with your changes")
        print()
        print("  Full guide: .github/skills/README.md")


def _contribute_notebook():
    """Walk through creating a Jupyter notebook example."""
    print()
    print("  -- Contribute a Notebook -----------------------------------")
    print()
    print("  Notebook examples go in examples/notebooks/ and show how to")
    print("  use NeqSim for a specific task (flash calculation, process")
    print("  simulation, PVT study, etc.).")
    print()

    try:
        topic = input("  What does your notebook demonstrate? ").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        return

    if not topic:
        print("  Cancelled.")
        return

    # Suggest a filename
    slug = topic.lower().replace(" ", "_").replace("-", "_")
    slug = "".join(c for c in slug if c.isalnum() or c == "_")[:50]
    filename = slug + ".ipynb"

    nb_dir = os.path.join(PROJECT_ROOT, "examples", "notebooks")
    print()
    print("  Suggested filename: {}".format(filename))
    print("  Location: examples/notebooks/{}".format(filename))
    print()
    print("  Quick checklist for a good notebook:")
    print("    - Use `from neqsim import jneqsim` for imports")
    print("    - Include markdown cells explaining each step")
    print("    - Add 2-3 plots with matplotlib (labels, units, title)")
    print("    - Run all cells to verify before submitting")
    print()
    print("  Tip: Copy an existing notebook as a starting point.")
    print("  See: examples/notebooks/ for 30+ examples.")
    print()
    print("  When ready, submit a PR with your notebook!")


def _contribute_benchmark():
    """Walk through adding a validation benchmark."""
    print()
    print("  -- Contribute a Benchmark ----------------------------------")
    print()
    print("  Benchmarks compare NeqSim calculations against reference")
    print("  data (NIST, published literature, other simulators).")
    print("  They live in docs/benchmarks/.")
    print()
    print("  Steps:")
    print("    1. Find reference data (NIST WebBook, published paper, etc.)")
    print("    2. Write a Jupyter notebook or Java test that:")
    print("       a. Computes the same property with NeqSim")
    print("       b. Compares to reference data")
    print("       c. Reports absolute and relative error")
    print("    3. Add to docs/benchmarks/ and submit a PR")
    print()
    print("  Existing benchmarks: docs/benchmarks/index.md")
    print("  NIST WebBook: https://webbook.nist.gov/")
    print()


def _contribute_bugfix():
    """Walk through the bug fix / feature contribution path."""
    print()
    print("  -- Contribute a Bug Fix or Feature -------------------------")
    print()
    print("  Steps:")
    print("    1. Check existing issues: github.com/equinor/neqsim/issues")
    print("    2. Create a branch:")
    print("         git checkout -b fix/your-description")
    print("    3. Make changes (all Java code must be Java 8 compatible)")
    print("    4. Run tests:")

    if os.name == "nt":
        print("         mvnw.cmd test")
        print("         mvnw.cmd checkstyle:check")
    else:
        print("         ./mvnw test")
        print("         ./mvnw checkstyle:check")

    print("    5. Submit a PR with a clear description")
    print()
    print("  AI-assisted PRs welcome! Mark with [AI-Assisted] in the title.")
    print("  See CONTRIBUTING.md for full guidelines.")
    print()


def _contribute_doc_fix():
    """Walk through fixing documentation."""
    print()
    print("  -- Fix Documentation --------------------------------------")
    print()
    print("  Documentation lives in docs/ (350+ markdown pages).")
    print()
    print("  Easy wins:")
    print("    - Fix broken links (search docs/**/*.md for dead links)")
    print("    - Fix typos or outdated API references")
    print("    - Add missing examples to existing guides")
    print("    - Improve unclear explanations")
    print()
    print("  Index of all docs: docs/REFERENCE_MANUAL_INDEX.md")
    print("  Submit a PR when ready — doc fixes are always appreciated!")
    print()


PATHS = [
    ("Contribute a skill", "easiest — write domain knowledge for AI agents, no Java"),
    ("Create a notebook example", "show how to use NeqSim for a specific task"),
    ("Add a validation benchmark", "compare NeqSim to NIST or published data"),
    ("Fix a bug or add a feature", "Java code contribution"),
    ("Fix documentation", "fix links, typos, or add examples to docs"),
]

HANDLERS = [
    _contribute_skill,
    _contribute_notebook,
    _contribute_benchmark,
    _contribute_bugfix,
    _contribute_doc_fix,
]


def main():
    args = sys.argv[1:]

    if "-h" in args or "--help" in args:
        print("Usage: neqsim contribute [--skill]")
        print()
        print("  Interactive guide for making your first contribution to NeqSim.")
        print()
        print("Options:")
        print("  --skill   Jump straight to skill contribution")
        sys.exit(0)

    if "--skill" in args:
        _contribute_skill()
        return

    print()
    print("  -- NeqSim Contribution Guide ------------------------------")
    print("  What would you like to contribute?")

    choice = _ask("", PATHS)
    HANDLERS[choice - 1]()


if __name__ == "__main__":
    main()
