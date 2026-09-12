#!/usr/bin/env python3
"""
neqsim - Unified CLI for NeqSim development and agentic workflows.

Usage:
    neqsim try               Try NeqSim in an interactive playground
    neqsim onboard           Interactive setup wizard for new contributors
    neqsim doctor            Check your environment is healthy
    neqsim contribute        Guided wizard for your first contribution
    neqsim new-task TITLE    Create a task-solving workspace
    neqsim report [DIR]      Generate Report.docx/html for a task folder
    neqsim --set-task-root P Set the folder new tasks are created in
    neqsim --show-task-root  Print the folder new tasks are created in
    neqsim --reset-task-root Remove the saved task-root setting
    neqsim --set-report-template P   Build Word reports from template P
    neqsim --show-report-template    Print the configured report template
    neqsim --reset-report-template   Remove the saved report template
    neqsim new-skill NAME    Scaffold a new AI skill
    neqsim skill CMD         Manage skills (list/search/install/remove/private-init/add-repo)
    neqsim agent CMD         Manage agents (list/search/install/remove/validate/private-init/add-repo)
    neqsim paperlab CMD      Manage PaperLab VS Code agents and skills

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
        "desc": "Manage skills (list/search/install/remove/private-init/add-repo)",
    },
    "agent": {
        "module": "install_agent",
        "desc": "Manage agents (list/search/install/remove/validate/private-init/add-repo)",
    },
    "paperlab": {
        "module": "paperlab_install",
        "desc": "Manage PaperLab VS Code agents and skills",
    },
    "install-skill": {
        "module": "install_skill",
        "desc": "(alias for 'skill') Manage skills",
    },
    "install-agent": {
        "module": "install_agent",
        "desc": "(alias for 'agent') Manage agents",
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
    print("  {:<18s} {}".format("report [DIR]",
                                "Generate Report.docx/html for a task folder"))
    print()
    print("Task destination:")
    print("  --set-task-root P  Create new tasks in folder P ('cwd' follows the terminal)")
    print("  --show-task-root   Print the folder new tasks are created in")
    print("  --reset-task-root  Remove the saved setting (existing tasks are unchanged)")
    print()
    print("Report template:")
    print("  --set-report-template P  Build Word reports from the .docx/.dotx template P")
    print("  --show-report-template   Print the configured report template")
    print("  --reset-report-template  Remove the saved template (built-in styling)")
    print()
    print("Run `neqsim <command> --help` for per-command options.")
    print("Docs: https://equinor.github.io/neqsim/")


TASK_ROOT_FLAGS = ("--set-task-root", "--show-task-root", "--reset-task-root", "task-root")
REPORT_TEMPLATE_FLAGS = ("--set-report-template", "--show-report-template",
                         "--reset-report-template", "report-template")

GENERATOR_PATH = os.path.join(DEVTOOLS_DIR, "task_template", "step3_report",
                              "generate_report.py")


def _handle_report(argv):
    """Run the canonical report generator against a task folder.

    Task folders vendor their own copy of generate_report.py at creation time,
    so an old task keeps an old generator. This command always runs the current
    devtools copy, which is how a template or formatting fix reaches every task.

    Parameters
    ----------
    argv : list of str
        Arguments after the ``report`` command. An optional leading positional
        is the task folder (default: current directory); everything else is
        forwarded to the generator (--paper, --template PATH, --no-template...).

    Returns
    -------
    int
        Exit code from the generator.
    """
    import subprocess

    task_dir = os.getcwd()
    passthrough = list(argv)
    if passthrough and not passthrough[0].startswith("-"):
        task_dir = os.path.abspath(os.path.expanduser(passthrough.pop(0)))

    if not os.path.isdir(task_dir):
        print("ERROR: not a folder: {}".format(task_dir))
        return 2
    markers = (os.path.join(task_dir, "results.json"),
               os.path.join(task_dir, "step1_scope_and_research"),
               os.path.join(task_dir, "step3_report"))
    if not any(os.path.exists(m) for m in markers):
        print("ERROR: {} does not look like a task folder "
              "(no results.json, step1_scope_and_research/, or step3_report/)."
              .format(task_dir))
        print("Usage: neqsim report [TASK_DIR] [--paper] [--template PATH] [--no-template]")
        return 2
    if not os.path.isfile(GENERATOR_PATH):
        print("ERROR: report generator not found at {}".format(GENERATOR_PATH))
        return 2

    cmd = [sys.executable, GENERATOR_PATH, "--task-dir", task_dir] + passthrough
    print("Generating report for {}".format(task_dir))
    return subprocess.call(cmd)


def _handle_report_template(argv):
    """Set, show, or reset the Word template generated reports are built from."""
    import new_task

    flag = argv[0]
    value = argv[1] if len(argv) > 1 else None
    if flag == "report-template":
        flag = "--set-report-template" if value else "--show-report-template"

    if flag == "--set-report-template":
        if not value:
            print("Usage: neqsim --set-report-template \"C:\\path\\company template.docx\"")
            sys.exit(2)
        try:
            stored = new_task.save_default_report_template(value)
        except (OSError, ValueError) as error:
            print("Could not save report template: {}".format(error))
            sys.exit(2)
        print("Report template: {}".format(stored))
        print("Word reports inherit its styles, fonts, headers, and footers.")
    elif flag == "--reset-report-template":
        try:
            new_task.clear_default_report_template()
        except (OSError, ValueError) as error:
            print("Could not remove report template: {}".format(error))
            sys.exit(2)
        print("Saved report template removed. Reports use built-in styling.")
    else:
        try:
            template = new_task.resolve_report_template()
        except (OSError, ValueError) as error:
            print("Report template is not usable: {}".format(error))
            sys.exit(2)
        print(template or "(none — reports use built-in styling)")


def _handle_task_root(argv):
    """Set, show, or reset the parent folder new task folders are created in."""
    import new_task

    flag = argv[0]
    value = argv[1] if len(argv) > 1 else None
    if flag == "task-root":
        flag = "--set-task-root" if value else "--show-task-root"

    if flag == "--set-task-root":
        if not value:
            print("Usage: neqsim --set-task-root \"PATH\"")
            print("       neqsim --set-task-root cwd   (follow the terminal's folder)")
            sys.exit(2)
        try:
            stored = new_task.save_default_task_root(value)
        except (OSError, ValueError) as error:
            print("Could not save task root: {}".format(error))
            sys.exit(2)
        if stored == new_task.CWD_TASK_ROOT:
            print("Task root: the terminal's current folder (re-resolved per command).")
        else:
            print("Task root: {}".format(stored))
        print("New tasks are created here. Existing tasks are unchanged.")
    elif flag == "--reset-task-root":
        new_task.clear_default_task_root()
        print("Saved task root removed. Existing tasks are unchanged.")
    else:
        print(new_task.resolve_task_root())


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

    if cmd in TASK_ROOT_FLAGS:
        _handle_task_root(sys.argv[1:])
        sys.exit(0)

    if cmd in REPORT_TEMPLATE_FLAGS:
        _handle_report_template(sys.argv[1:])
        sys.exit(0)

    if cmd == "report":
        sys.exit(_handle_report(sys.argv[2:]))

    if cmd not in COMMANDS:
        print("Unknown command: {!r}".format(cmd))
        print("Run `neqsim --help` for available commands.")
        sys.exit(1)

    # Rewrite sys.argv so the sub-module sees its own args
    module_name = COMMANDS[cmd]["module"]
    sys.argv = ["neqsim {}".format(cmd)] + sys.argv[2:]

    if cmd != "new-task":
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
