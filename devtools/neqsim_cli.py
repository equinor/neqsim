#!/usr/bin/env python3
"""
neqsim - Unified CLI for NeqSim development and agentic workflows.

Usage:
    neqsim try               Try NeqSim in an interactive playground
    neqsim onboard           Interactive setup wizard for new contributors
    neqsim doctor            Check your environment is healthy
    neqsim contribute        Guided wizard for your first contribution
    neqsim new-task TITLE    Create a task-solving workspace
    neqsim tasks CMD         Across solved tasks: index/relink/env/duplicates
    neqsim report [DIR]      Generate the report (files named after its title)
    neqsim work-record [DIR] Generate WORK_RECORD.md (method, data, file map)
    neqsim --set-task-root P Set the folder new tasks are created in (created if
                            missing; --vscode adds it to the workspace, --explorer
                            opens it now)
    neqsim --show-task-root  Print the folder new tasks are created in
    neqsim --reset-task-root Remove the saved task-root setting
    neqsim --set-report-template P   Build Word reports from template P
    neqsim --show-report-template    Print the configured report template
    neqsim --reset-report-template   Remove the saved report template
    neqsim --set-document-root P     Read source documents from P and its subfolders
                                     (created if missing; --vscode / --explorer as above)
    neqsim --show-document-root      Print the configured document root
    neqsim --reset-document-root     Remove the saved document root
    neqsim documents [PATTERN]       List documents under the document root
    neqsim documents --index [DIR]   Refresh a task's document_root_index.md
    neqsim documents --index --all   Backfill that index into every existing task
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
CONTINUOUS_COMMANDS = ("living", "cycle", "solve", "backtest", "schedule", "promote", "ledger",
                       "status", "report", "reference-case")

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
    "tasks": {
        "module": "task_corpus",
        "desc": "Work across solved tasks (index/relink/env/duplicates)",
    },
    "new-skill": {
        "module": "new_skill",
        "desc": "Scaffold a new AI skill",
    },
    "skill": {
        "module": "install_skill",
        "desc": "Manage skills (list/search/install/remove [--all]/private-init/add-repo)",
    },
    "agent": {
        "module": "install_agent",
        "desc": "Manage agents (list/search/install/remove [--all --with-skills]/validate/...)",
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
                                "Generate the report (files named after its title)"))
    print("  {:<18s} {}".format("work-record [DIR]",
                                "Generate WORK_RECORD.md (method, data, file map)"))
    print()
    print("Living tasks (continuous task solving):")
    print("  task-living TASK         Make a task living (continuous/, baseline, ledger, goal)")
    print("  task-cycle TASK          Run one monitor or solve cycle")
    print("  task-solve TASK          Solve until the goal is met or improvement is marginal")
    print("  task-backtest TASK       Replay archived data with a simulated clock")
    print("  task-schedule TASK       Schedule daily cycles (Windows Task Scheduler / cron)")
    print("  task-promote TASK CYCLE  Promote a reviewed cycle to the baseline")
    print("  task-ledger TASK         List or update the improvement ledger")
    print("  task-status [PATH]       Status of a living task, or all living tasks (default: task root)")
    print("  task-report TASK         Rebuild continuous/LIVING_REPORT.md (--formal: also Word/HTML)")
    print("  task-reference-case [DIR] Create the public reference task (default: task root)")
    print("  (TASK is a folder path, or a folder name inside the task root)")
    print()
    print("Task destination:")
    print("  --set-task-root P  Create new tasks in folder P ('cwd' follows the terminal)")
    print("  --show-task-root   Print the folder new tasks are created in")
    print("  --reset-task-root  Remove the saved setting (existing tasks are unchanged)")
    print("                     A missing folder is created; add --vscode to also add")
    print("                     it to the VS Code workspace, or --explorer to open it")
    print("                     in the file explorer now.")
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
    print("  documents --index [DIR]  Refresh a task's document_root_index.md")
    print("  documents --index --all  Backfill that index into every existing task")
    print("                           (add --dry-run first; --all takes an optional root)")
    print("                           Put standards, technical requirements, datasheets,")
    print("                           and drawings agents must always know about here.")
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


VSCODE_FLAGS = ("--vscode", "--add-to-workspace")
EXPLORER_FLAGS = ("--explorer", "--open-explorer", "--reveal")


def _take_option(argv, flags):
    """Pull an option flag out of the arguments so it is not read as the path."""
    remaining = [part for part in argv
                 if ("--" + part.strip().lstrip("-").lower()) not in flags]
    return len(remaining) != len(argv), remaining


def _take_flags(argv, *flag_sets):
    """Pull several option flags out of the arguments in one pass.

    Returns one bool per flag set (in order), plus the remaining arguments.
    """
    remaining = argv
    found = []
    for flags in flag_sets:
        hit, remaining = _take_option(remaining, flags)
        found.append(hit)
    return tuple(found) + (remaining,)


def _offer_post_set_actions(path, add_to_vscode, open_explorer):
    """Add a configured folder to VS Code and/or open it in the file manager.

    Both are opt-in (``--vscode`` / ``--explorer``): a setting command never
    launches an application the user did not ask for.
    """
    import new_task

    if not add_to_vscode:
        print("Add it to your editor with File > Add Folder to Workspace "
              "(or re-run with --vscode).")
    else:
        added, reason = new_task.add_folder_to_vscode_workspace(path)
        if added:
            print("Added to the VS Code workspace.")
        else:
            print("Not added to the VS Code workspace: {}".format(reason))
            print("Add it manually with File > Add Folder to Workspace.")

    if not open_explorer:
        print("Open it yourself, or re-run with --explorer to open it in the file "
              "explorer now.")
    else:
        opened, reason = new_task.open_folder_in_file_manager(path)
        if opened:
            print("Opened in the file explorer.")
        else:
            print("Could not open the file explorer: {}".format(reason))


GENERATOR_PATH = os.path.join(DEVTOOLS_DIR, "task_template", "step3_report",
                              "generate_report.py")


def _handle_report(argv):
    """Run the canonical report generator against a task folder.

    Task folders carry a launcher, not a copy, of generate_report.py, so a
    template or formatting fix reaches every task. This command is the same
    entry point without needing the launcher.

    Parameters
    ----------
    argv : list of str
        Arguments after the ``report`` command. An optional leading positional
        is the task folder (default: current directory); everything else is
        forwarded to the generator (--paper, --pdf, --no-pdf, --template PATH,
        --no-template, --title TEXT, --author NAME...).

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
        print("Usage: neqsim report [TASK_DIR] [--paper] [--pdf] [--no-pdf] "
              "[--template PATH] [--no-template] [--title TEXT] [--author NAME] "
              "[--language CODE]")
        return 2
    if not os.path.isfile(GENERATOR_PATH):
        print("ERROR: report generator not found at {}".format(GENERATOR_PATH))
        return 2

    cmd = [sys.executable, GENERATOR_PATH, "--task-dir", task_dir] + passthrough
    print("Generating report for {}".format(task_dir))
    return subprocess.call(cmd)


def _handle_work_record(argv):
    """Build the method-and-data record for a task folder.

    The report says what the answer is; the work record says how it was produced
    and where every input, script, and artifact lives, so the study can be
    audited or repeated by someone who was not in the conversation.

    Parameters
    ----------
    argv : list of str
        Arguments after the ``work-record`` command. An optional leading
        positional is the task folder (default: current directory); the rest is
        forwarded to the generator (--check, --stdout).

    Returns
    -------
    int
        Exit code from the generator.
    """
    import generate_work_record

    passthrough = list(argv)
    if not passthrough or passthrough[0].startswith("-"):
        passthrough = [os.getcwd()] + passthrough
    return generate_work_record.main(passthrough)


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
    add_to_vscode, open_explorer, rest = _take_flags(argv[1:], VSCODE_FLAGS, EXPLORER_FLAGS)
    value = _setting_value(rest)
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
        print("Put standards, technical requirements, datasheets, and drawings here.")
        _offer_post_set_actions(stored, add_to_vscode, open_explorer)
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


def _index_all_tasks(new_task, explicit_root, dry_run):
    """Backfill the document-root index across every existing task folder."""
    import task_roots

    roots = task_roots.resolve_task_roots(explicit_root or None)
    folders = task_roots.find_task_folders(roots)
    if not folders:
        print("No task folders found in {}".format(task_roots.describe(roots)))
        return 1
    print("{} task folder(s) in {}".format(len(folders), task_roots.describe(roots)))
    if dry_run:
        for folder in folders:
            print("  would index {}".format(folder))
        print("Re-run without --dry-run to write the index files.")
        return 0
    written = 0
    for folder in folders:
        try:
            new_task.write_document_root_index(str(folder))
            written += 1
        except (OSError, ValueError) as error:
            print("  SKIPPED {}: {}".format(folder.name, error))
    print("Indexed {} task folder(s).".format(written))
    return 0


def _handle_documents(argv):
    """List documents under the document root, including every subfolder."""
    import new_task

    if argv and argv[0] == "--index":
        rest = argv[1:]
        dry_run = "--dry-run" in rest
        rest = [item for item in rest if item != "--dry-run"]
        if rest and rest[0] == "--all":
            return _index_all_tasks(new_task, " ".join(rest[1:]).strip(), dry_run)
        task_dir = os.path.abspath(" ".join(rest).strip() or ".")
        if not os.path.isdir(task_dir):
            print("ERROR: task folder not found: {}".format(task_dir))
            return 2
        if dry_run:
            print("Would index: {}".format(task_dir))
            return 0
        try:
            path = new_task.write_document_root_index(task_dir)
        except (OSError, ValueError) as error:
            print("ERROR: {}".format(error))
            return 2
        print("Wrote {}".format(path))
        return 0

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
    add_to_vscode, open_explorer, rest = _take_flags(argv[1:], VSCODE_FLAGS, EXPLORER_FLAGS)
    value = _setting_value(rest)
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
            print("New tasks are created here. Existing tasks are unchanged.")
        else:
            print("Task root: {}".format(stored))
            print("New tasks are created here. Existing tasks are unchanged.")
            _offer_post_set_actions(stored, add_to_vscode, open_explorer)
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

    if cmd in ("work-record", "workrecord"):
        sys.exit(_handle_work_record(sys.argv[2:]))

    if cmd.startswith("task-") and cmd[5:] in CONTINUOUS_COMMANDS:
        from neqsim_continuous import cli as continuous_cli
        sys.exit(continuous_cli.main([cmd[5:]] + sys.argv[2:]))

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
        # Propagate a failure code so a refusal is not reported as success.
        code = mod.main()
        if code:
            sys.exit(code)
    else:
        # Fallback: re-run as script (shouldn't normally be needed)
        exec(open(os.path.join(DEVTOOLS_DIR, module_name + ".py")).read())


if __name__ == "__main__":
    main()
