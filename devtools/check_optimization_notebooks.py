#!/usr/bin/env python3
"""Execute the published optimization notebooks, preserving results and HTML.

Compile the current workspace and prepare ``target/neqsim-dev-classpath.txt``
before running. Install ``neqsim``, ``numpy``, ``scipy``, ``pandas``,
``matplotlib``, ``nbformat``, ``nbclient``, ``nbconvert`` and ``ipykernel`` with
the selected Python interpreter. The notebook setup cells load target/classes
through neqsim_dev_setup without changing any notebook source.

Default execution uses one fresh nbclient kernel per notebook. For environments
that prohibit local ZeroMQ sockets, ``--executor ipython`` uses a fresh child
process and the same sequential IPython cell execution and rich display capture.
There is no automatic fallback: the summary records the selected executor.
The default timeouts are 900 seconds per nbclient cell and 1800 seconds per
notebook worker; both can be configured through the command-line options.

Example::

    python devtools/check_optimization_notebooks.py
    python devtools/check_optimization_notebooks.py ProductionOptimizer_Tutorial

Artifacts are written to target/optimization-notebooks by default. Checked-in
notebooks are never overwritten by this verification command.
"""

import argparse
import asyncio
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import traceback


PROJECT_ROOT = Path(__file__).resolve().parents[1]
NOTEBOOKS = (
    "ProductionOptimizer_Tutorial",
    "NeqSim_Python_Optimization",
    "processmodel_plant_optimization",
    "autosize_and_optimize_workflows",
    "oilgas_production_energy_optimization",
    "reservoir_to_market_optimization",
    "capacity_constraints_optimization_demo",
    "process_optimization_enhancements",
    "pipeline_network_optimization",
    "norwegian_ncs_oil_network_optimization",
    "norwegian_ncs_gas_network_optimization",
    "production_optimization_topside_coupling",
    "full_field_optimization_reservoirs_to_export",
    "host_tie_in_capacity_and_holdback",
)

NOTEBOOK_PATHS = {name: Path("docs/examples") / (name + ".ipynb") for name in NOTEBOOKS[:6]}
NOTEBOOK_PATHS.update({name: Path("examples/notebooks") / (name + ".ipynb") for name in NOTEBOOKS[6:9]})
NOTEBOOK_PATHS.update({name: Path("examples/notebooks/process") / (name + ".ipynb") for name in NOTEBOOKS[9:11]})
NOTEBOOK_PATHS.update({name: Path("examples/notebooks") / (name + ".ipynb") for name in NOTEBOOKS[11:]})


def execute_ipython(notebook):
    """Execute every actual code cell, capturing rich outputs without sockets."""
    import nbformat
    from IPython.core.interactiveshell import InteractiveShell
    from IPython.utils.capture import capture_output

    shell = InteractiveShell.instance()
    # The inline backend does not require a GUI event loop in a child process.
    shell.enable_gui = lambda gui=None: None
    shell.run_line_magic("matplotlib", "inline")
    count = 0
    for index, cell in enumerate(notebook.cells):
        if cell.cell_type != "code":
            continue
        count += 1
        print(f"Executing cell {index + 1}", flush=True)
        cell.execution_count = count
        started = time.monotonic()
        with capture_output(stdout=True, stderr=True, display=True) as captured:
            result = shell.run_cell(cell.source, store_history=True)
        for name in ("stdout", "stderr"):
            value = getattr(captured, name)
            if value:
                cell.outputs.append(nbformat.v4.new_output("stream", name=name, text=value))
        for output in captured.outputs:
            cell.outputs.append(nbformat.v4.new_output(
                "display_data", data=output.data, metadata=output.metadata
            ))
        cell.metadata["execution_seconds"] = round(time.monotonic() - started, 3)
        error = result.error_in_exec or result.error_before_exec
        if error:
            cell.outputs.append(nbformat.v4.new_output(
                "error", ename=type(error).__name__, evalue=str(error),
                traceback=[str(error)],
            ))
            raise RuntimeError(f"Notebook cell {index + 1} failed: {error}") from error


def execute_nbclient(notebook, directory, timeout):
    """Use an isolated kernel specification tied to this exact interpreter."""
    from jupyter_client import AsyncKernelManager
    from jupyter_client.kernelspec import KernelSpecManager
    from nbclient import NotebookClient

    kernels_dir = directory / "kernel_specs"
    spec_dir = kernels_dir / "optimization-python"
    spec_dir.mkdir(parents=True, exist_ok=True)
    (spec_dir / "kernel.json").write_text(json.dumps({
        "argv": [sys.executable, "-m", "ipykernel_launcher", "-f", "{connection_file}"],
        "display_name": "Optimization notebook verification",
        "language": "python",
    }), encoding="utf-8")
    async def execute():
        # Blocking clients prevent nbclient's concurrent timeout and liveness polling.
        manager = AsyncKernelManager(
            kernel_name="optimization-python",
            kernel_spec_manager=KernelSpecManager(kernel_dirs=[str(kernels_dir)]),
        )
        client = NotebookClient(
            notebook, km=manager, timeout=timeout, allow_errors=False,
            resources={"metadata": {"path": str(directory)}},
        )
        try:
            # Complete startup before async_execute registers its exit callbacks.
            await client.async_start_new_kernel()
            await client.async_start_new_kernel_client()
            await client.async_execute(cleanup_kc=True)
        finally:
            # Also release resources when startup fails before nbclient enters its context.
            try:
                if manager.has_kernel:
                    await manager.shutdown_kernel(now=True)
            finally:
                if client.kc is not None:
                    client.kc.stop_channels()
                await manager.cleanup_resources()

    asyncio.run(execute())


def run_one(name, output_root, executor, timeout):
    """Run one notebook in a fresh process and retain diagnostic artifacts."""
    import nbformat
    import nbconvert
    from nbconvert import HTMLExporter

    source_path = PROJECT_ROOT / NOTEBOOK_PATHS[name]
    source_bytes = source_path.read_bytes()
    notebook = nbformat.reads(source_bytes.decode("utf-8"), as_version=4)
    original_sources = [cell.source for cell in notebook.cells]
    directory = output_root / name
    directory.mkdir(parents=True, exist_ok=True)
    os.environ["NEQSIM_PROJECT_ROOT"] = str(PROJECT_ROOT)
    os.environ["PYTHONPATH"] = os.pathsep.join(filter(None, [
        str(PROJECT_ROOT / "devtools"), os.environ.get("PYTHONPATH", "")
    ]))
    os.environ["MPLBACKEND"] = "module://matplotlib_inline.backend_inline"
    os.chdir(directory)
    for cell in notebook.cells:
        if cell.cell_type == "code":
            cell.outputs = []
            cell.execution_count = None

    started = time.monotonic()
    error = None
    try:
        if executor == "nbclient":
            execute_nbclient(notebook, directory, timeout)
        else:
            execute_ipython(notebook)
        if original_sources != [cell.source for cell in notebook.cells]:
            raise AssertionError("Execution changed notebook source")
        if any(output.output_type == "error" for cell in notebook.cells
               for output in cell.get("outputs", [])):
            raise AssertionError("Execution retained a notebook error output")
        if any(cell.execution_count is None for cell in notebook.cells if cell.cell_type == "code"):
            raise AssertionError("Execution skipped a code cell")
    except Exception:
        error = traceback.format_exc()

    code_cells = [cell for cell in notebook.cells if cell.cell_type == "code"]
    report = {
        "notebook": str(source_path.relative_to(PROJECT_ROOT)),
        "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
        "executor": executor,
        "python_executable": sys.executable,
        "java_source": "workspace target/classes via notebook devtools setup",
        "status": "FAIL" if error else "PASS",
        "code_cells": len(code_cells),
        "executed_code_cells": sum(cell.execution_count is not None for cell in code_cells),
        "png_outputs": sum("image/png" in output.get("data", {})
                           for cell in code_cells for output in cell.outputs),
        "seconds": round(time.monotonic() - started, 3),
        "error": error,
    }
    notebook.metadata["optimization_verification"] = report
    nbformat.write(notebook, directory / (name + ".executed.ipynb"))
    try:
        template_root = Path(nbconvert.__file__).resolve().parent.parent / "share/jupyter/nbconvert/templates"
        exporter = HTMLExporter(
            extra_template_basedirs=[str(template_root)],
            extra_template_paths=[str(template_root)],
        ) if template_root.is_dir() else HTMLExporter()
        html, _ = exporter.from_notebook_node(notebook)
        (directory / (name + ".html")).write_text(html, encoding="utf-8")
    except Exception:
        report["status"] = "FAIL"
        report["html_error"] = traceback.format_exc()
    (directory / "execution.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report), flush=True)
    return 0 if report["status"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("notebooks", nargs="*", choices=NOTEBOOKS)
    parser.add_argument("--executor", choices=("nbclient", "ipython"), default="nbclient")
    parser.add_argument("--output-dir", type=Path, default=PROJECT_ROOT / "target/optimization-notebooks")
    parser.add_argument("--timeout", type=int, default=900, help="Per-cell nbclient timeout in seconds")
    parser.add_argument("--notebook-timeout", type=int, default=1800,
                        help="Whole-notebook child process timeout in seconds (default: 1800)")
    parser.add_argument("--worker", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.timeout <= 0 or args.notebook_timeout <= 0:
        parser.error("Timeouts must be positive")
    if not (PROJECT_ROOT / "target/classes/neqsim/thermo/system/SystemSrkEos.class").is_file():
        parser.error("Compile the current workspace before executing the documentation notebooks")
    output_root = args.output_dir.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    names = args.notebooks or NOTEBOOKS
    if args.worker:
        if len(names) != 1:
            parser.error("A worker must receive exactly one notebook")
        return run_one(names[0], output_root, args.executor, args.timeout)

    reports = []
    for name in names:
        directory = output_root / name
        directory.mkdir(parents=True, exist_ok=True)
        command = [sys.executable, str(Path(__file__).resolve()), name, "--worker",
                   "--executor", args.executor, "--output-dir", str(output_root),
                   "--timeout", str(args.timeout)]
        print(f"Checking {name} with {args.executor}", flush=True)
        report_path = directory / "execution.json"
        if report_path.exists():
            report_path.unlink()
        try:
            with (directory / "execution.log").open("w", encoding="utf-8") as log:
                completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT,
                                           timeout=args.notebook_timeout, check=False)
            report = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {
                "notebook": name, "status": "FAIL", "error": "Worker did not produce a report; inspect execution.log"
            }
            if completed.returncode != 0:
                report["status"] = "FAIL"
        except subprocess.TimeoutExpired:
            report = {"notebook": name, "status": "FAIL", "error": "Whole-notebook timeout"}
        reports.append(report)
        print(f"{name}: {report['status']}", flush=True)
    (output_root / "summary.json").write_text(json.dumps(reports, indent=2) + "\n", encoding="utf-8")
    return 0 if all(report["status"] == "PASS" for report in reports) else 1


if __name__ == "__main__":
    sys.exit(main())
