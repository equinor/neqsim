"""
book_notebook_runner — Execute book notebooks via devtools (no JAR packaging).

Uses ``neqsim_dev_setup`` to start the JVM from compiled classes + shaded JAR,
so notebooks can run immediately after ``mvnw compile`` without a full
``mvnw package`` step.

The runner:
1. Discovers all .ipynb files in chapter notebooks/ directories
2. Executes each notebook in a subprocess with the devtools classpath
3. Captures outputs and errors
4. Optionally injects generated figures into chapter.md
5. Reports pass/fail status for each notebook

Usage from CLI::

    python tools/book_notebook_runner.py <book_dir>
    python tools/book_notebook_runner.py <book_dir> --chapter ch04_association_theory
    python tools/book_notebook_runner.py <book_dir> --fix  # auto-retry failed cells
    python tools/book_notebook_runner.py <book_dir> --update-chapters  # inject figures

Usage from Python::

    from book_notebook_runner import run_book_notebooks, NotebookResult
    results = run_book_notebooks("path/to/book", compile_first=True)
"""

import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

# ---------------------------------------------------------------------------
# Project root detection
# ---------------------------------------------------------------------------

_THIS_DIR = Path(__file__).resolve().parent
_PAPERLAB_DIR = _THIS_DIR.parent
_DEFAULT_PROJECT_ROOT = _PAPERLAB_DIR.parent

DEVTOOLS_DIR = _DEFAULT_PROJECT_ROOT / "devtools"


def _find_project_root():
    """Auto-detect neqsim project root."""
    env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
    if env_root:
        p = Path(env_root)
        if (p / "pom.xml").exists():
            return p
    for candidate in [_DEFAULT_PROJECT_ROOT, Path.cwd()]:
        if (candidate / "pom.xml").exists():
            return candidate
    return _DEFAULT_PROJECT_ROOT


PROJECT_ROOT = _find_project_root()


# ---------------------------------------------------------------------------
# Maven compile helper
# ---------------------------------------------------------------------------

def compile_neqsim(project_root=None, quiet=True):
    """Run ``mvnw compile`` so classes are up to date.

    Returns True on success, raises RuntimeError on failure.
    """
    root = Path(project_root or PROJECT_ROOT)
    mvnw = root / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    cmd = [str(mvnw), "compile"]
    if quiet:
        cmd.append("-q")

    print(f"Compiling NeqSim ({root})... ", end="", flush=True)
    result = subprocess.run(
        cmd, cwd=str(root), capture_output=True, text=True, timeout=180,
    )
    if result.returncode == 0:
        print("OK")
        return True
    print("FAILED")
    print(result.stdout[-2000:] if len(result.stdout) > 2000 else result.stdout)
    print(result.stderr[-2000:] if len(result.stderr) > 2000 else result.stderr)
    raise RuntimeError("Maven compile failed")


# ---------------------------------------------------------------------------
# Notebook execution via nbconvert subprocess
# ---------------------------------------------------------------------------

def _build_notebook_runner_script():
    """Return a Python script string that executes a notebook in-process.

    The script does NOT start the JVM itself — that would conflict with the
    kernel subprocess that ``ExecutePreprocessor`` spawns.  Instead it relies
    on ``PYTHONPATH`` (set by ``run_notebook``) so the kernel can import
    ``neqsim_dev_setup`` when the notebook's dual-boot cell runs.

    Steps:
    1. Reads the notebook
    2. Executes it via nbconvert (kernel inherits PYTHONPATH with devtools/)
    3. Writes the executed notebook back to disk
    4. Reports errors as JSON to stdout
    """
    return r'''
import json, os, sys, traceback
from pathlib import Path

notebook_path = sys.argv[1]
timeout = int(sys.argv[2]) if len(sys.argv) > 2 else 600

# Execute the notebook — the kernel subprocess will inherit PYTHONPATH
# which includes devtools/, so neqsim_dev_setup is importable by the
# notebook's dual-boot setup cell.
import nbformat
from nbconvert.preprocessors import ExecutePreprocessor, CellExecutionError

nb_path = Path(notebook_path)
with open(nb_path, "r", encoding="utf-8") as f:
    nb = nbformat.read(f, as_version=4)

# allow_errors=True: continue executing remaining cells even when one fails.
# Errors are captured in cell outputs so later cells (figures, results) still run.
ep = ExecutePreprocessor(
    timeout=timeout,
    kernel_name="python3",
    allow_errors=True,
)

# Set working directory to the notebook's directory
resources = {"metadata": {"path": str(nb_path.parent)}}

errors = []
try:
    ep.preprocess(nb, resources)
except Exception as e:
    errors.append({
        "cell": -1,
        "ename": type(e).__name__,
        "evalue": str(e)[:500],
    })

# Scan for error outputs in executed cells
for i, cell in enumerate(nb.cells):
    if cell.cell_type != "code":
        continue
    for out in cell.get("outputs", []):
        if out.get("output_type") == "error":
            errors.append({
                "cell": i + 1,
                "ename": out.get("ename", "Unknown"),
                "evalue": out.get("evalue", "")[:300],
            })

# Write executed notebook back
with open(nb_path, "w", encoding="utf-8") as f:
    nbformat.write(nb, f)

# Report
result = {
    "notebook": str(nb_path),
    "errors": errors,
    "n_cells": sum(1 for c in nb.cells if c.cell_type == "code"),
    "n_executed": sum(
        1 for c in nb.cells
        if c.cell_type == "code" and c.get("execution_count") is not None
    ),
}

print("NOTEBOOK_RESULT:" + json.dumps(result))
'''


def run_notebook(notebook_path, project_root=None, timeout=600):
    """Execute a single notebook in a subprocess with devtools JVM.

    Sets PYTHONPATH to include devtools/ so the kernel subprocess can
    import ``neqsim_dev_setup``.  Sets NEQSIM_PROJECT_ROOT so the
    devtools bootstrap knows where to find compiled classes.

    Returns a dict with execution results.
    """
    root = Path(project_root or PROJECT_ROOT)
    nb_path = Path(notebook_path).resolve()

    if not nb_path.exists():
        return {"notebook": str(nb_path), "error": "File not found"}

    # Write the runner script to a temp file
    runner_script = _build_notebook_runner_script()
    runner_path = root / "target" / "_nb_runner.py"
    runner_path.parent.mkdir(parents=True, exist_ok=True)
    runner_path.write_text(runner_script, encoding="utf-8")

    cmd = [
        sys.executable, str(runner_path),
        str(nb_path),
        str(timeout),
    ]

    # Set PYTHONPATH so the Jupyter kernel subprocess can find neqsim_dev_setup
    env = os.environ.copy()
    devtools_str = str(DEVTOOLS_DIR)
    existing_pypath = env.get("PYTHONPATH", "")
    if devtools_str not in existing_pypath:
        env["PYTHONPATH"] = devtools_str + os.pathsep + existing_pypath if existing_pypath else devtools_str
    env["NEQSIM_PROJECT_ROOT"] = str(root)

    start = time.perf_counter()
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout + 60,  # extra margin for subprocess overhead
            cwd=str(nb_path.parent),
            env=env,
        )
    except subprocess.TimeoutExpired:
        return {
            "notebook": str(nb_path),
            "error": f"Timeout after {timeout}s",
            "elapsed_s": timeout,
        }

    elapsed = time.perf_counter() - start

    # Parse the JSON result from stdout
    result = None
    for line in proc.stdout.splitlines():
        if line.startswith("NOTEBOOK_RESULT:"):
            try:
                result = json.loads(line[len("NOTEBOOK_RESULT:"):])
            except json.JSONDecodeError:
                pass

    if result is None:
        return {
            "notebook": str(nb_path),
            "error": "No result from runner",
            "returncode": proc.returncode,
            "stdout": proc.stdout[-2000:],
            "stderr": proc.stderr[-2000:],
            "elapsed_s": elapsed,
        }

    result["elapsed_s"] = round(elapsed, 1)
    result["returncode"] = proc.returncode
    return result


# ---------------------------------------------------------------------------
# Book-level notebook discovery and execution
# ---------------------------------------------------------------------------

def discover_notebooks(book_dir, chapter_filter=None):
    """Find all .ipynb notebooks in a book's chapter directories.

    Returns list of (chapter_dir_name, notebook_path) tuples, ordered by
    chapter then by notebook filename.
    """
    book_dir = Path(book_dir)
    chapters_dir = book_dir / "chapters"
    if not chapters_dir.exists():
        return []

    notebooks = []
    for ch_dir in sorted(chapters_dir.iterdir()):
        if not ch_dir.is_dir():
            continue
        if chapter_filter and ch_dir.name != chapter_filter:
            continue
        nb_dir = ch_dir / "notebooks"
        if not nb_dir.exists():
            continue
        for nb in sorted(nb_dir.glob("*.ipynb")):
            if ".ipynb_checkpoints" in str(nb):
                continue
            notebooks.append((ch_dir.name, nb))

    return notebooks


def run_book_notebooks(book_dir, chapter_filter=None, compile_first=True,
                       timeout=600, project_root=None, stop_on_error=False):
    """Execute all notebooks in a book project.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    chapter_filter : str, optional
        Only run notebooks for this chapter (e.g., ``ch04_association_theory``).
    compile_first : bool
        Run ``mvnw compile`` before executing notebooks.
    timeout : int
        Timeout per notebook in seconds.
    project_root : str or Path, optional
        NeqSim project root (auto-detected if not specified).
    stop_on_error : bool
        Stop on first notebook error.

    Returns
    -------
    list of dict
        Execution results for each notebook.
    """
    root = Path(project_root or PROJECT_ROOT)

    if compile_first:
        compile_neqsim(root)

    notebooks = discover_notebooks(book_dir, chapter_filter)
    if not notebooks:
        print("No notebooks found.")
        return []

    print(f"\nRunning {len(notebooks)} notebook(s)...\n")
    results = []

    for ch_name, nb_path in notebooks:
        rel = nb_path.relative_to(Path(book_dir))
        print(f"  [{ch_name}] {rel.name} ... ", end="", flush=True)

        result = run_notebook(nb_path, project_root=root, timeout=timeout)
        result["chapter"] = ch_name

        errors = result.get("errors", [])
        error_msg = result.get("error", "")

        if errors or error_msg:
            n_exec = result.get("n_executed", "?")
            n_total = result.get("n_cells", "?")
            # Distinguish: WARN = cell errors but notebook ran to completion,
            # FAIL = notebook could not execute at all (e.g. kernel crash).
            if error_msg:
                tag = "FAIL"
            else:
                tag = "WARN"
            print(f"{tag} ({len(errors)} error(s), {n_exec}/{n_total} cells, "
                  f"{result.get('elapsed_s', 0):.0f}s)")
            for err in errors:
                print(f"    Cell {err.get('cell', '?')}: {err.get('ename', '?')}: "
                      f"{err.get('evalue', '')[:100]}")
            if error_msg:
                print(f"    {error_msg}")
            if stop_on_error:
                results.append(result)
                break
        else:
            n_exec = result.get("n_executed", "?")
            n_total = result.get("n_cells", "?")
            print(f"OK ({n_exec}/{n_total} cells, {result.get('elapsed_s', 0):.0f}s)")

        results.append(result)

    # Summary
    passed = sum(1 for r in results if not r.get("errors") and not r.get("error"))
    warned = sum(1 for r in results if r.get("errors") and not r.get("error"))
    failed = sum(1 for r in results if r.get("error"))
    print(f"\n{'=' * 60}")
    print(f"Results: {passed} passed, {warned} warned, {failed} failed, {len(results)} total")
    if warned > 0:
        print(f"\nNotebooks with cell errors (still ran to completion):")
        for r in results:
            if r.get("errors") and not r.get("error"):
                print(f"  - {r.get('chapter', '?')}/{Path(r['notebook']).name}")
    if failed > 0:
        print(f"\nFailed notebooks:")
        for r in results:
            if r.get("error"):
                print(f"  - {r.get('chapter', '?')}/{Path(r['notebook']).name}")

    return results


# ---------------------------------------------------------------------------
# Figure injection — update chapter.md with notebook-generated figures
# ---------------------------------------------------------------------------

def collect_generated_figures(book_dir, chapter_filter=None):
    """Scan chapter figures/ directories for generated PNGs.

    Returns dict mapping chapter_dir_name -> list of figure filenames.
    """
    book_dir = Path(book_dir)
    figures = {}
    chapters_dir = book_dir / "chapters"

    for ch_dir in sorted(chapters_dir.iterdir()):
        if not ch_dir.is_dir():
            continue
        if chapter_filter and ch_dir.name != chapter_filter:
            continue
        fig_dir = ch_dir / "figures"
        if not fig_dir.exists():
            continue
        figs = sorted(f.name for f in fig_dir.glob("*.png"))
        if figs:
            figures[ch_dir.name] = figs

    return figures


def inject_figures_into_chapter(book_dir, ch_dir_name, figure_files=None):
    """Add figure references to chapter.md for any figures not already referenced.

    Only adds figures that are not already mentioned in the chapter text.
    Appends them to a '## Figures' section at the end.

    Returns the number of figures injected.
    """
    book_dir = Path(book_dir)
    ch_dir = book_dir / "chapters" / ch_dir_name
    ch_md = ch_dir / "chapter.md"

    if not ch_md.exists():
        return 0

    text = ch_md.read_text(encoding="utf-8")

    if figure_files is None:
        fig_dir = ch_dir / "figures"
        if not fig_dir.exists():
            return 0
        figure_files = sorted(f.name for f in fig_dir.glob("*.png"))

    if not figure_files:
        return 0

    # Find figures not already referenced
    unreferenced = []
    for fig in figure_files:
        if fig not in text:
            unreferenced.append(fig)

    if not unreferenced:
        return 0

    # Build caption from filename
    lines = ["\n\n## Figures\n"]
    ch_num = re.match(r"ch(\d+)", ch_dir_name)
    ch_n = int(ch_num.group(1)) if ch_num else 0

    for i, fig in enumerate(unreferenced, 1):
        # Derive caption from filename: fig_ch04_ex01_xa_density.png -> X_A density
        caption = fig.replace(".png", "").replace("fig_", "")
        # Remove chapter prefix
        caption = re.sub(r"ch\d+_", "", caption)
        # Convert underscores to spaces and title-case
        caption = caption.replace("_", " ").strip()
        caption = f"Figure {ch_n}.{i}: {caption.title()}" if ch_n else caption.title()

        lines.append(f"![{caption}](figures/{fig})\n")
        lines.append(f"*{caption}*\n")

    text += "\n".join(lines)
    ch_md.write_text(text, encoding="utf-8")
    return len(unreferenced)


def update_all_chapters_with_figures(book_dir, chapter_filter=None):
    """Inject figure references into all chapters that have generated figures.

    Returns dict mapping chapter_dir_name -> number of figures injected.
    """
    figures = collect_generated_figures(book_dir, chapter_filter)
    injected = {}

    for ch_name, figs in figures.items():
        n = inject_figures_into_chapter(book_dir, ch_name, figs)
        if n > 0:
            injected[ch_name] = n
            print(f"  {ch_name}: injected {n} figure(s)")

    return injected


# ---------------------------------------------------------------------------
# Full build pipeline
# ---------------------------------------------------------------------------

def build_book(book_dir, output_format="html", compile_first=True,
               run_notebooks=True, update_chapters=True, check_quality=True,
               project_root=None, timeout=600, stop_on_error=False):
    """Full book build pipeline: compile -> run notebooks -> update chapters -> render.

    Parameters
    ----------
    book_dir : str or Path
        Path to the book project directory.
    output_format : str
        Render format: 'html', 'docx', 'pdf', or 'all'.
    compile_first : bool
        Run ``mvnw compile`` before notebooks.
    run_notebooks : bool
        Execute all notebooks.
    update_chapters : bool
        Inject generated figures into chapter.md files.
    check_quality : bool
        Run book quality checks before rendering.
    project_root : str or Path, optional
        NeqSim project root.
    timeout : int
        Per-notebook timeout in seconds.
    stop_on_error : bool
        Halt pipeline on first notebook error.

    Returns
    -------
    dict
        Build summary with notebook results, checks, and render paths.
    """
    book_dir = Path(book_dir)
    root = Path(project_root or PROJECT_ROOT)
    summary = {"book_dir": str(book_dir), "steps": {}}

    print(f"{'=' * 60}")
    print(f"Building book: {book_dir.name}")
    print(f"{'=' * 60}\n")

    # Step 1: Compile
    if compile_first:
        print("Step 1: Compile NeqSim")
        try:
            compile_neqsim(root)
            summary["steps"]["compile"] = "OK"
        except RuntimeError as e:
            summary["steps"]["compile"] = f"FAILED: {e}"
            print(f"  Compile failed — aborting.\n")
            return summary
    else:
        summary["steps"]["compile"] = "skipped"

    # Step 2: Run notebooks
    nb_results = []
    if run_notebooks:
        print("\nStep 2: Run notebooks")
        nb_results = run_book_notebooks(
            book_dir, compile_first=False,  # already compiled
            timeout=timeout, project_root=root, stop_on_error=stop_on_error,
        )
        passed = sum(1 for r in nb_results if not r.get("errors") and not r.get("error"))
        warned = sum(1 for r in nb_results if r.get("errors") and not r.get("error"))
        failed = sum(1 for r in nb_results if r.get("error"))
        summary["steps"]["notebooks"] = {
            "total": len(nb_results),
            "passed": passed,
            "warned": warned,
            "failed": failed,
        }

        if stop_on_error and failed > 0:
            print("  Notebook errors — stopping build.\n")
            summary["notebook_results"] = nb_results
            return summary
    else:
        summary["steps"]["notebooks"] = "skipped"

    # Step 3: Update chapters with generated figures
    if update_chapters:
        print("\nStep 3: Update chapters with generated figures")
        injected = update_all_chapters_with_figures(book_dir)
        summary["steps"]["figure_injection"] = injected or "no new figures"
    else:
        summary["steps"]["figure_injection"] = "skipped"

    # Step 4: Quality checks
    if check_quality:
        print("\nStep 4: Quality checks")
        try:
            sys.path.insert(0, str(_THIS_DIR))
            from book_checker import run_checks, format_issues
            issues = run_checks(str(book_dir))
            errors = sum(1 for i in issues if i["severity"] == "error")
            warnings = sum(1 for i in issues if i["severity"] == "warning")
            print(f"  {errors} error(s), {warnings} warning(s)")
            summary["steps"]["quality"] = {
                "errors": errors, "warnings": warnings,
            }
        except Exception as e:
            print(f"  Quality check failed: {e}")
            summary["steps"]["quality"] = f"FAILED: {e}"
    else:
        summary["steps"]["quality"] = "skipped"

    # Step 5: Render
    formats = [output_format] if output_format != "all" else ["html", "docx", "pdf", "odf"]
    print(f"\nStep 5: Render ({', '.join(formats)})")
    render_results = {}

    for fmt in formats:
        try:
            sys.path.insert(0, str(_THIS_DIR))
            if fmt == "html":
                from book_render_html import render_book_html
                out = render_book_html(str(book_dir))
                render_results["html"] = str(out) if out else "FAILED"
            elif fmt == "docx":
                from book_render_word import render_book_word
                out = render_book_word(str(book_dir))
                render_results["docx"] = str(out) if out else "FAILED"
            elif fmt == "pdf":
                from book_render_pdf import render_book_pdf
                out = render_book_pdf(str(book_dir))
                render_results["pdf"] = str(out) if out else "FAILED"
            elif fmt == "odf":
                from book_render_odf import render_book_odf
                out = render_book_odf(str(book_dir))
                render_results["odf"] = str(out) if out else "FAILED"
            print(f"  {fmt}: OK")
        except Exception as e:
            render_results[fmt] = f"FAILED: {e}"
            print(f"  {fmt}: FAILED ({e})")

    summary["steps"]["render"] = render_results
    summary["notebook_results"] = nb_results

    # Final summary
    print(f"\n{'=' * 60}")
    print("Build complete!")
    nb_info = summary["steps"].get("notebooks", {})
    if isinstance(nb_info, dict):
        parts = [f"{nb_info.get('passed', 0)} passed"]
        if nb_info.get('warned', 0):
            parts.append(f"{nb_info['warned']} warned")
        if nb_info.get('failed', 0):
            parts.append(f"{nb_info['failed']} failed")
        print(f"  Notebooks: {', '.join(parts)} / {nb_info.get('total', 0)} total")
    for fmt, path in render_results.items():
        print(f"  {fmt}: {path}")
    print(f"{'=' * 60}\n")

    return summary


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    """CLI entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Run book notebooks via devtools (no JAR packaging needed)",
    )
    parser.add_argument("book_dir", help="Book project directory")
    parser.add_argument("--chapter", help="Run only this chapter's notebooks")
    parser.add_argument("--compile", action="store_true", default=True,
                        help="Compile NeqSim first (default: True)")
    parser.add_argument("--no-compile", dest="compile", action="store_false",
                        help="Skip compilation")
    parser.add_argument("--timeout", type=int, default=600,
                        help="Per-notebook timeout in seconds (default: 600)")
    parser.add_argument("--stop-on-error", action="store_true",
                        help="Stop on first notebook error")
    parser.add_argument("--update-chapters", action="store_true",
                        help="Inject generated figures into chapter.md files")
    parser.add_argument("--build", action="store_true",
                        help="Full build pipeline: compile, run, inject, check, render")
    parser.add_argument("--format", dest="out_format", default="html",
                        choices=["html", "docx", "pdf", "odf", "all"],
                        help="Render format for --build (default: html)")
    parser.add_argument("--json", action="store_true",
                        help="Output results as JSON")

    args = parser.parse_args()

    if args.build:
        summary = build_book(
            args.book_dir,
            output_format=args.out_format,
            compile_first=args.compile,
            project_root=PROJECT_ROOT,
            timeout=args.timeout,
            stop_on_error=args.stop_on_error,
        )
        if args.json:
            print(json.dumps(summary, indent=2, default=str))
    else:
        if args.update_chapters:
            if args.compile:
                compile_neqsim(PROJECT_ROOT)
            results = run_book_notebooks(
                args.book_dir,
                chapter_filter=args.chapter,
                compile_first=False,
                timeout=args.timeout,
                project_root=PROJECT_ROOT,
                stop_on_error=args.stop_on_error,
            )
            update_all_chapters_with_figures(args.book_dir, args.chapter)
        else:
            results = run_book_notebooks(
                args.book_dir,
                chapter_filter=args.chapter,
                compile_first=args.compile,
                timeout=args.timeout,
                project_root=PROJECT_ROOT,
                stop_on_error=args.stop_on_error,
            )

        if args.json:
            print(json.dumps(results, indent=2, default=str))

        # Exit with error code only if a notebook completely failed (not for cell warnings)
        failed = sum(1 for r in results if r.get("error"))
        sys.exit(1 if failed > 0 else 0)


if __name__ == "__main__":
    main()
