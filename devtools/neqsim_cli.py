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
    neqsim --set-document-root P     Read source documents from P and its subfolders
    neqsim --show-document-root      Print the configured document root
    neqsim --reset-document-root     Remove the saved document root
    neqsim documents [PATTERN]       List documents under the document root
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
    print("Source documents:")
    print("  --set-document-root P    Read documents from folder P and all its subfolders")
    print("  --show-document-root     Print the folder agents read documents from")
    print("  --reset-document-root    Remove the saved document root")
    print("  documents [PATTERN]      List documents under the document root")
    print()
    print("Run `neqsim <command> --help` for per-command options.")
    print("Docs: https://equinor.github.io/neqsim/")


TASK_ROOT_FLAGS = ("--set-task-root", "--show-task-root", "--reset-task-root", "task-root")
REPORT_TEMPLATE_FLAGS = ("--set-report-template", "--show-report-template",
                         "--reset-report-template", "report-template")
DOCUMENT_ROOT_FLAGS = ("--set-document-root", "--show-document-root",
                       "--reset-document-root", "document-root")


def _setting_flag(flag, known_flags):
    """Accept a setting written with or without the leading dashes."""
    candidate = "--" + flag.lstrip("-")
    return candidate if candidate in known_flags else flag


def _setting_value(argv):
    """Return the setting value, rejoining a path typed without quotes.

    Windows users routinely type ``neqsim --set-document-root C:\\Engineering
    Documents`` without quotes, which the shell splits into several arguments.
    Joining them back is what makes that command save the whole path instead of
    silently keeping the first word.

    Parameters
    ----------
    argv : list of str
        Arguments after the setting flag.

    Returns
    -------
    str or None
        The single value, or None when the flag was used without one.
    """
    value = " ".join(part for part in argv if part.strip()).strip()
    return value or None


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

    flag = _setting_flag(argv[0], REPORT_TEMPLATE_FLAGS)
    value = _setting_value(argv[1:])
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


def _handle_document_root(argv):
    """Set, show, or reset the folder agents read source documents from."""
    import new_task

    flag = _setting_flag(argv[0], DOCUMENT_ROOT_FLAGS)
    value = _setting_value(argv[1:])
    if flag == "document-root":
        flag = "--set-document-root" if value else "--show-document-root"

    if flag == "--set-document-root":
        if not value:
            print("Usage: neqsim --set-document-root \"C:\\path\\Engineering Documents\"")
            sys.exit(2)
        try:
            stored = new_task.save_default_document_root(value)
        except (OSError, ValueError) as error:
            print("Could not save document root: {}".format(error))
            sys.exit(2)
        print("Document root: {}".format(stored))
        print("Agents read source documents from this folder and all its subfolders.")
    elif flag == "--reset-document-root":
        try:
            new_task.clear_default_document_root()
        except (OSError, ValueError) as error:
            print("Could not remove document root: {}".format(error))
            sys.exit(2)
        print("Saved document root removed.")
    else:
        try:
            root = new_task.resolve_document_root()
        except (OSError, ValueError) as error:
            print("Document root is not usable: {}".format(error))
            sys.exit(2)
        print(root or "(none — set one with: neqsim --set-document-root \"PATH\")")


def _handle_documents(argv):
    """List documents under the document root, including every subfolder."""
    import new_task

    pattern = " ".join(argv).strip()
    try:
        matches = new_task.find_documents(pattern)
        root = new_task.resolve_document_root()
    except (OSError, ValueError) as error:
        print("ERROR: {}".format(error))
        return 2
    if not matches:
        print("No documents{} under {}".format(
            " matching {!r}".format(pattern) if pattern else "", root))
        return 1
    print("{} document(s) under {}:".format(len(matches), root))
    for path in matches:
        print("  {}".format(os.path.relpath(path, root)))
    return 0


def _handle_task_root(argv):
    """Set, show, or reset the parent folder new task folders are created in."""
    import new_task

    flag = _setting_flag(argv[0], TASK_ROOT_FLAGS)
    value = _setting_value(argv[1:])
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

    if _setting_flag(cmd, TASK_ROOT_FLAGS) in TASK_ROOT_FLAGS:
        _handle_task_root(sys.argv[1:])
        sys.exit(0)

    if _setting_flag(cmd, REPORT_TEMPLATE_FLAGS) in REPORT_TEMPLATE_FLAGS:
        _handle_report_template(sys.argv[1:])
        sys.exit(0)

    if _setting_flag(cmd, DOCUMENT_ROOT_FLAGS) in DOCUMENT_ROOT_FLAGS:
        _handle_document_root(sys.argv[1:])
        sys.exit(0)

    if cmd == "documents":
        sys.exit(_handle_documents(sys.argv[2:]))

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
