#!/usr/bin/env python3
"""Corpus-level maintenance for solved task folders.

A single task is handled by `neqsim new-task` / `neqsim report`. This tool works
across every configured task root (see task_roots.py) and fixes the things that
stop a corpus from being reusable by someone else:

    python devtools/task_corpus.py index          # INDEX.md + tasks.json
    python devtools/task_corpus.py relink         # replace vendored report
                                                  #   generators with a launcher
    python devtools/task_corpus.py env TASK       # stamp the software
                                                  #   environment into results.json
    python devtools/task_corpus.py duplicates     # folders present in >1 root

`relink` and `env` are dry-run by default; pass --apply to write. `env` only
touches task folders named on the command line — reports stamp themselves as
they are generated, and backfilling would claim a version that never ran.
"""
import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from task_roots import (  # noqa: E402
    add_task_root_argument,
    describe,
    duplicate_task_folders,
    find_task_folders,
    resolve_task_roots,
)

DEVTOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = DEVTOOLS_DIR.parent
CANONICAL_GENERATOR = (DEVTOOLS_DIR / "task_template" / "step3_report"
                       / "generate_report.py")
LAUNCHER_MARKER = "GENERATOR_HINT"
ENVIRONMENT_SCHEMA = "1.0"
TRACKED_PACKAGES = ("neqsim", "jpype1", "numpy", "matplotlib", "pandas",
                    "scipy", "python-docx")


# ── environment capture ────────────────────────────────────────────────

def _pom_version(project_root):
    """Return the NeqSim version declared in pom.xml, or an empty string."""
    pom = Path(project_root) / "pom.xml"
    try:
        text = pom.read_text(encoding="utf-8")
    except OSError:
        return ""
    # The first <version> after </parent> or after <artifactId>neqsim</artifactId>
    marker = text.find("<artifactId>neqsim</artifactId>")
    if marker == -1:
        return ""
    start = text.find("<version>", marker)
    end = text.find("</version>", start)
    if start == -1 or end == -1:
        return ""
    version = text[start + len("<version>"):end].strip()
    return _resolve_pom_property(text, version)


def _resolve_pom_property(pom_text, value):
    """Resolve a ${property} version reference against the pom's properties."""
    match = re.fullmatch(r"\$\{([\w.\-]+)\}", value or "")
    if not match:
        return value
    declared = re.search(r"<{0}>([^<]+)</{0}>".format(re.escape(match.group(1))),
                         pom_text)
    return declared.group(1).strip() if declared else value


def _git_commit(project_root):
    """Return the short commit of the NeqSim checkout, or an empty string."""
    try:
        out = subprocess.run(["git", "-C", str(project_root), "rev-parse",
                              "--short", "HEAD"],
                             capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        return ""
    return out.stdout.strip() if out.returncode == 0 else ""


def _package_versions():
    """Return installed versions of the packages a task result depends on."""
    versions = {}
    try:
        from importlib import metadata
    except ImportError:
        return versions
    for name in TRACKED_PACKAGES:
        try:
            versions[name] = metadata.version(name)
        except Exception:
            continue
    return versions


def capture_environment(project_root=None):
    """Return the software environment a task result was produced with.

    Without this a third party cannot tell which NeqSim produced a number, and
    a rerun that disagrees cannot be distinguished from a regression.
    """
    project_root = str(project_root or os.environ.get("NEQSIM_PROJECT_ROOT")
                       or REPO_ROOT)
    import platform
    environment = {
        "schema": ENVIRONMENT_SCHEMA,
        "recorded": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "neqsim_version": _pom_version(project_root),
        "neqsim_commit": _git_commit(project_root),
        "neqsim_project_root": project_root,
        "python": platform.python_version(),
        "python_executable": sys.executable,
        "platform": platform.platform(),
        "packages": _package_versions(),
    }
    return {key: value for key, value in environment.items() if value not in ("", {})}


def stamp_environment(task_dir, project_root=None, force=False):
    """Write the environment block into a task's results.json.

    Returns 'written', 'kept', or a reason string when nothing was done.
    """
    results_file = Path(task_dir) / "results.json"
    if not results_file.is_file():
        return "no results.json"
    try:
        with open(results_file, "r", encoding="utf-8-sig") as handle:
            data = json.load(handle)
    except (OSError, ValueError) as error:
        return "unreadable results.json: {}".format(error)
    if not isinstance(data, dict):
        return "results.json is not an object"
    if data.get("environment") and not force:
        return "kept"
    data["environment"] = capture_environment(project_root)
    try:
        with open(results_file, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
    except OSError as error:
        return "could not write results.json: {}".format(error)
    return "written"


# ── report generator relinking ─────────────────────────────────────────

def _launcher_source(task_dir):
    """Return the launcher text for a task, pointing at this checkout."""
    sys.path.insert(0, str(DEVTOOLS_DIR))
    import new_task
    return new_task.GENERATE_REPORT.replace("__GENERATOR_HINT__",
                                            str(CANONICAL_GENERATOR))


def is_launcher(path):
    """Return True when a task's generate_report.py is already a launcher."""
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError:
        return False
    return LAUNCHER_MARKER in text and len(text) < 20000


# Constants a task may have hand-edited inside its vendored generator.
EXTRACTED_STRINGS = ("TITLE", "AUTHOR", "CLASSIFICATION", "DOC_NUMBER",
                     "REVISION", "PAPER_TITLE", "PAPER_JOURNAL",
                     "PAPER_ACKNOWLEDGMENTS")
EXTRACTED_COLLECTIONS = ("MANUAL_SECTIONS", "PAPER_SECTIONS",
                         "REVISION_HISTORY", "PAPER_AUTHORS", "PAPER_KEYWORDS")


def _static_eval(node):
    """Evaluate a literal expression without executing the module."""
    import ast
    if isinstance(node, ast.Constant):
        return node.value
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left, right = _static_eval(node.left), _static_eval(node.right)
        if isinstance(left, str) and isinstance(right, str):
            return left + right
        raise ValueError("unsupported addition")
    if isinstance(node, ast.Dict):
        return {_static_eval(k): _static_eval(v)
                for k, v in zip(node.keys, node.values)}
    if isinstance(node, (ast.List, ast.Tuple)):
        return [_static_eval(item) for item in node.elts]
    raise ValueError("unsupported expression {}".format(type(node).__name__))


def extract_report_sections(generator_path, canonical_defaults):
    """Return (sections, unknown_keys) for a task's vendored generator.

    Parsed statically, never executed. Values equal to the canonical defaults
    or still holding template placeholders are dropped, so relinking only
    preserves content a human actually wrote. ``unknown_keys`` lists section
    names the canonical generator does not render — a task that invented its
    own sections also edited its rendering code and must not be relinked.
    """
    import ast
    try:
        tree = ast.parse(Path(generator_path).read_text(encoding="utf-8"))
    except (OSError, SyntaxError, ValueError):
        return None, []
    found = {}
    for node in tree.body:
        if not isinstance(node, ast.Assign) or len(node.targets) != 1:
            continue
        target = node.targets[0]
        if not isinstance(target, ast.Name):
            continue
        if target.id not in EXTRACTED_STRINGS + EXTRACTED_COLLECTIONS:
            continue
        try:
            found[target.id] = _static_eval(node.value)
        except ValueError:
            continue

    sections = {}
    unknown = []
    for name in EXTRACTED_STRINGS:
        value = found.get(name)
        if isinstance(value, str) and value.strip() \
                and value != canonical_defaults.get(name) \
                and not value.strip().startswith("["):
            sections[name.lower()] = value
    for name in EXTRACTED_COLLECTIONS:
        value = found.get(name)
        default = canonical_defaults.get(name)
        if isinstance(value, dict):
            kept = {}
            for key, text in value.items():
                if not isinstance(text, str) or not text.strip() \
                        or text.strip().startswith("["):
                    continue
                if isinstance(default, dict) and key not in default:
                    unknown.append("{}.{}".format(name.lower(), key))
                    continue
                if isinstance(default, dict) and default.get(key) == text:
                    continue
                kept[key] = text
            if kept:
                sections[name.lower()] = kept
        elif isinstance(value, list) and value and value != default:
            sections[name.lower()] = value
    return sections, unknown


def canonical_defaults():
    """Return the placeholder values shipped by the canonical generator."""
    return _canonical_literals()


def _canonical_literals():
    """Return the canonical generator's own constant values for comparison."""
    import ast
    tree = ast.parse(CANONICAL_GENERATOR.read_text(encoding="utf-8"))
    values = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1 \
                and isinstance(node.targets[0], ast.Name):
            name = node.targets[0].id
            if name in EXTRACTED_STRINGS + EXTRACTED_COLLECTIONS:
                try:
                    values[name] = _static_eval(node.value)
                except ValueError:
                    continue
    return values


def relink_reports(folders, apply_changes=False, include_custom=False):
    """Replace vendored report-generator copies with the shared launcher.

    Hand-written report content found in a fork is written to
    step3_report/report_sections.json first. A fork that renders sections the
    canonical generator does not know about is left alone: its report would
    lose content, and no automatic migration can recover custom layout code.
    """
    defaults = _canonical_literals()
    forked, relinked, preserved, custom, already = [], [], [], [], 0
    for folder in folders:
        path = folder / "step3_report" / "generate_report.py"
        if not path.is_file():
            continue
        if is_launcher(path):
            already += 1
            continue
        sections, unknown = extract_report_sections(path, defaults)
        if unknown and not include_custom:
            custom.append((folder.name, unknown))
            continue
        forked.append((path, dict(sections or {})))
        if sections:
            preserved.append(folder.name)
        if not apply_changes:
            continue
        try:
            if sections:
                sections_file = folder / "step3_report" / "report_sections.json"
                if not sections_file.exists():
                    sections_file.write_text(
                        json.dumps(sections, indent=2, ensure_ascii=False) + "\n",
                        encoding="utf-8")
            path.write_text(_launcher_source(folder), encoding="utf-8")
            relinked.append(path)
        except OSError as error:
            print("  ERROR {}: {}".format(path, error))
    return forked, relinked, preserved, custom, already


# ── task index ─────────────────────────────────────────────────────────

def _read_json(path):
    """Return parsed JSON, or None when missing or malformed."""
    try:
        with open(path, "r", encoding="utf-8-sig") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return None


def _title(folder, results):
    """Return the study title from study_config.yaml, results, or the folder."""
    config = folder / "study_config.yaml"
    if config.is_file():
        try:
            for line in config.read_text(encoding="utf-8").splitlines():
                stripped = line.strip()
                if stripped.startswith("title:"):
                    value = stripped.split(":", 1)[1].strip().strip('"').strip("'")
                    if value and not value.startswith("["):
                        return value
        except OSError:
            pass
    if isinstance(results, dict):
        for key in ("title", "objective", "task_statement"):
            value = results.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip().split("\n")[0][:160]
    name = folder.name
    return name[11:].replace("_", " ").capitalize() if name[:2].isdigit() else name


def summarize_task(folder):
    """Return the index record for one task folder."""
    results = _read_json(folder / "results.json")
    key_results = (results or {}).get("key_results") or {}
    standards = [s.get("standard") if isinstance(s, dict) else s
                 for s in ((results or {}).get("standards_applied") or [])]
    report_dir = folder / "step3_report"
    deliverables = sorted(p.name for p in report_dir.glob("*.docx")) \
        if report_dir.is_dir() else []
    return {
        "folder": folder.name,
        "path": str(folder),
        "date": folder.name[:10] if folder.name[:4].isdigit() else "",
        "title": _title(folder, results),
        "has_results": results is not None,
        "key_result_count": len(key_results) if isinstance(key_results, dict) else 0,
        "headline": next(iter(key_results.items()), ("", ""))[0]
        if isinstance(key_results, dict) and key_results else "",
        "standards": [s for s in standards if s],
        "benchmark_validation": bool((results or {}).get("benchmark_validation")),
        "uncertainty": bool((results or {}).get("uncertainty")),
        "risk_evaluation": bool((results or {}).get("risk_evaluation")),
        "environment_recorded": bool((results or {}).get("environment")),
        "work_record": (report_dir / "WORK_RECORD.md").is_file(),
        "sources_md": (folder / "step1_scope_and_research" / "references"
                       / "SOURCES.md").is_file(),
        "deliverables": deliverables,
    }


def render_index(records, roots, out_dir=None):
    """Return the Markdown index of a task corpus."""
    lines = [
        "# Solved task index",
        "",
        "{} tasks. Generated by `devtools/task_corpus.py index` on {}.".format(
            len(records), datetime.now().strftime("%Y-%m-%d")),
        "",
        "Roots: {}".format(describe(roots)),
        "",
        "Legend — R: results.json, B: benchmark validation, U: uncertainty, "
        "K: risk register, W: work record, S: sources index.",
        "",
        "| Date | Task | Title | R | B | U | K | W | S |",
        "|---|---|---|---|---|---|---|---|---|",
    ]

    def mark(flag):
        return "x" if flag else ""

    def link(record):
        """Link to the task, relative when it sits under the index folder."""
        if out_dir is None:
            return record["folder"]
        try:
            return Path(record["path"]).relative_to(out_dir).as_posix()
        except ValueError:
            return Path(record["path"]).as_uri()

    for record in sorted(records, key=lambda r: r["folder"], reverse=True):
        lines.append("| {} | [{}]({}/) | {} | {} | {} | {} | {} | {} |".format(
            record["date"], record["folder"], link(record),
            record["title"].replace("|", "/"),
            mark(record["has_results"]), mark(record["benchmark_validation"]),
            mark(record["uncertainty"]), mark(record["risk_evaluation"]),
            mark(record["work_record"]), mark(record["sources_md"])))
    lines.append("")
    return "\n".join(lines)


# ── commands ───────────────────────────────────────────────────────────

def cmd_index(args):
    """Write INDEX.md and tasks.json describing the whole corpus."""
    roots = resolve_task_roots(args.task_root)
    folders = find_task_folders(roots)
    if not folders:
        print("No task folders found in {}".format(describe(roots)))
        return 1
    records = [summarize_task(folder) for folder in folders]
    out_dir = Path(args.out).resolve() if args.out else roots[0]
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "INDEX.md").write_text(render_index(records, roots, out_dir),
                                      encoding="utf-8")
    (out_dir / "tasks.json").write_text(
        json.dumps({"generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "roots": [str(r) for r in roots],
                    "tasks": records}, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8")
    print("Indexed {} tasks -> {}".format(len(records), out_dir / "INDEX.md"))
    print("                    {}".format(out_dir / "tasks.json"))
    missing = sum(1 for r in records if not r["has_results"])
    if missing:
        print("{} task(s) have no results.json and are not machine-readable."
              .format(missing))
    return 0


def cmd_relink(args):
    """Point vendored report generators at the single canonical generator."""
    roots = resolve_task_roots(args.task_root)
    folders = find_task_folders(roots)
    forked, relinked, preserved, custom, already = relink_reports(
        folders, args.apply, args.include_custom)
    print("Task folders: {}".format(len(folders)))
    print("Already using the launcher: {}".format(already))
    print("Forkable copies of generate_report.py: {}".format(len(forked)))
    print("  of which carry hand-written report content: {}".format(len(preserved)))
    if custom:
        print("Left alone — custom report sections a relink would drop: {}"
              .format(len(custom)))
        for name, keys in custom[:10]:
            print("  {}: {}".format(name, ", ".join(keys[:4])))
    if forked and not args.apply:
        for path, sections in forked[:10]:
            note = "  [{} hand-written key(s) -> report_sections.json]".format(
                len(sections)) if sections else ""
            print("  {}{}".format(path, note))
        if len(forked) > 10:
            print("  ... and {} more".format(len(forked) - 10))
        print("\nRe-run with --apply to replace them with the launcher.")
    elif relinked:
        print("Relinked {} task(s); preserved content for {}.".format(
            len(relinked), len(preserved)))
    return 0


def cmd_env(args):
    """Stamp the software environment into results.json for named tasks.

    Reports stamp themselves as they are generated, which is the only honest
    moment: backfilling today's checkout onto an older task would assert a
    NeqSim version that did not produce its numbers. This command therefore
    only touches task folders the caller names.
    """
    if not args.task:
        print("Name the task folder(s) to stamp, for example:")
        print("  neqsim tasks env --apply <task folder>")
        print("")
        print("Reports record the environment when they are generated, so a")
        print("regenerated report needs no stamping. Backfilling an old task")
        print("would claim this checkout produced numbers it never produced.")
        return 2
    folders = [Path(task).resolve() for task in args.task]
    missing = [f for f in folders if not f.is_dir()]
    if missing:
        for folder in missing:
            print("ERROR: not a folder: {}".format(folder))
        return 2
    if not args.apply:
        for folder in folders:
            state = "has an environment block" if (
                (_read_json(folder / "results.json") or {}).get("environment")
            ) else "would be stamped"
            print("  {}: {}".format(folder.name, state))
        print("\nRe-run with --apply to write the environment block.")
        return 0
    written = 0
    for folder in folders:
        outcome = stamp_environment(folder, force=args.force)
        if outcome == "written":
            written += 1
        print("  {}: {}".format(folder.name, outcome))
    print("Stamped {} task(s).".format(written))
    return 0


def cmd_duplicates(args):
    """List task folders that exist in more than one root."""
    roots = resolve_task_roots(args.task_root)
    duplicates = duplicate_task_folders(roots)
    print("Roots: {}".format(describe(roots)))
    if not duplicates:
        print("No duplicated task folders.")
        return 0
    print("{} task folder(s) exist in more than one root:".format(len(duplicates)))
    for name in sorted(duplicates):
        print("  {}".format(name))
        for path in duplicates[name]:
            print("      {}".format(path))
    return 0


def main(argv=None):
    """Dispatch the corpus maintenance subcommands."""
    parser = argparse.ArgumentParser(
        description="Corpus-level maintenance for solved task folders.")
    sub = parser.add_subparsers(dest="command")

    p_index = sub.add_parser("index", help="Write INDEX.md and tasks.json")
    p_index.add_argument("--out", help="Output folder (default: first task root)")
    add_task_root_argument(p_index)
    p_index.set_defaults(func=cmd_index)

    p_relink = sub.add_parser(
        "relink", help="Replace vendored report generators with the launcher")
    p_relink.add_argument("--apply", action="store_true", help="Write the changes")
    p_relink.add_argument("--include-custom", action="store_true",
                          help="Also relink tasks whose fork renders custom "
                               "sections (their extra sections will be lost)")
    add_task_root_argument(p_relink)
    p_relink.set_defaults(func=cmd_relink)

    p_env = sub.add_parser(
        "env", help="Stamp the software environment into a task's results.json")
    p_env.add_argument("task", nargs="*", help="Task folder(s) to stamp")
    p_env.add_argument("--apply", action="store_true", help="Write the changes")
    p_env.add_argument("--force", action="store_true",
                       help="Overwrite an existing environment block")
    p_env.set_defaults(func=cmd_env)

    p_dup = sub.add_parser("duplicates", help="List folders present in >1 root")
    add_task_root_argument(p_dup)
    p_dup.set_defaults(func=cmd_duplicates)

    args = parser.parse_args(argv)
    if not getattr(args, "func", None):
        parser.print_help()
        return 2
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
