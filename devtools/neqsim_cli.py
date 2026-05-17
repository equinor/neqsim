#!/usr/bin/env python3
"""
neqsim - Unified CLI for NeqSim development and agentic workflows.

Usage:
    neqsim try               Try NeqSim in an interactive playground
    neqsim onboard           Interactive setup wizard for new contributors
    neqsim doctor            Check your environment is healthy
    neqsim contribute        Guided wizard for your first contribution
    neqsim new-task TITLE    Create a task-solving workspace
    neqsim new-skill NAME    Scaffold a new AI skill
    neqsim skill CMD         Manage skills (list/search/install/remove/private-init)

Run `neqsim <command> --help` for per-command options.
"""
import importlib
import os
import sys


DEVTOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(DEVTOOLS_DIR)

COMMANDS = {
    "try": {
        "module": "neqsim_try",
        "desc": "Interactive playground — explore NeqSim in 30 seconds",
    },
    "onboard": {
        "module": "onboard",
        "desc": "Interactive setup wizard for new contributors",
    },
    "doctor": {
        "module": "neqsim_doctor",
        "desc": "Check your development environment is healthy",
    },
    "contribute": {
        "module": "neqsim_contribute",
        "desc": "Guided wizard for making your first contribution",
    },
    "new-task": {
        "module": "new_task",
        "desc": "Create a task-solving workspace",
    },
    "new-skill": {
        "module": "new_skill",
        "desc": "Scaffold a new AI skill",
    },
    "skill": {
        "module": "install_skill",
        "desc": "Manage skills (list/search/install/remove/private-init)",
    },
    "install-skill": {
        "module": "install_skill",
        "desc": "(alias for 'skill') Manage skills",
    },
}

BANNER = r"""
 _   _            ____  _
| \ | | ___  __ _/ ___|(_)_ __ ___
|  \| |/ _ \/ _` \___ \| | '_ ` _ \
| |\  |  __/ (_| |___) | | | | | | |
|_| \_|\___|\__, |____/|_|_| |_| |_|
               |_|
"""


def _print_usage():
    print(BANNER.lstrip("\n"))
    print("Usage: neqsim <command> [options]\n")
    print("Commands:")
    for name, info in COMMANDS.items():
        print("  {:<18s} {}".format(name, info["desc"]))
    print()
    print("Run `neqsim <command> --help` for per-command options.")
    print("Docs: https://equinor.github.io/neqsim/")


def main():
    # Ensure devtools/ is importable
    if DEVTOOLS_DIR not in sys.path:
        sys.path.insert(0, DEVTOOLS_DIR)

    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help", "help"):
        _print_usage()
        sys.exit(0)

    cmd = sys.argv[1]

    if cmd == "--version":
        print("neqsim-dev-tools 0.1.0")
        sys.exit(0)

    if cmd not in COMMANDS:
        print("Unknown command: {!r}".format(cmd))
        print("Run `neqsim --help` for available commands.")
        sys.exit(1)

    # Rewrite sys.argv so the sub-module sees its own args
    module_name = COMMANDS[cmd]["module"]
    sys.argv = ["neqsim {}".format(cmd)] + sys.argv[2:]

    # Change to project root so relative paths work consistently
    os.chdir(PROJECT_ROOT)

    mod = importlib.import_module(module_name)

    # Each module uses `if __name__ == "__main__": main()` pattern.
    # We call main() directly.
    if hasattr(mod, "main"):
        mod.main()
    else:
        # Fallback: re-run as script (shouldn't normally be needed)
        exec(open(os.path.join(DEVTOOLS_DIR, module_name + ".py")).read())


if __name__ == "__main__":
    main()
