# devtools — Jupyter Development Helpers for NeqSim

This directory contains a small pip-installable Python package that makes it
easy to use **Jupyter notebooks** for NeqSim Java development — edit Java code,
compile, and immediately test from Python without rebuilding a JAR.

## One-time setup

```bash
# From the project root (with your venv active):
pip install -e devtools/
```

This installs `neqsim_dev_setup` as an editable package so every notebook in
the venv can import it — no `sys.path` hacks needed.

## Usage (any notebook, any directory)

```python
# Cell 1 — start JVM (compile if needed)
from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(recompile=True)

# Cell 2 — import Java classes
ns = neqsim_classes(ns)          # standard set
# or cherry-pick:
# ns.MyClass = ns.JClass("neqsim.my.package.MyClass")
```

Then use `ns.SystemSrkEos`, `ns.Stream`, `ns.Compressor`, etc.

## Re-running after Java edits

Just re-run Cell 1. If the JVM is already running it will:
1. Recompile (`mvnw compile`)
2. Restart the kernel automatically
3. On restart, Cell 1 runs again with a fresh JVM

## Example notebook

See [`examples/notebooks/neqsim_dev.ipynb`](../examples/notebooks/neqsim_dev.ipynb)
for a working demo.

## Detailed documentation

See [Jupyter Development Workflow](../docs/development/jupyter_development_workflow.md)
for a full explanation of the architecture and internals.

## Files

| File | Purpose |
|------|---------|
| `neqsim_dev_setup.py` | JVM bootstrap, class imports, compile + kernel restart |
| `pyproject.toml` | Makes it pip-installable (`pip install -e devtools/`) |
| `new_task.py` | Create task-solving folders for the 4-step AI workflow |
| `unisim_reader.py` | UniSim/HYSYS .usc COM reader → NeqSim Python/notebook/EOT/JSON. 45+ op types, port-specific forward refs, auto-recycle wiring. |
| `test_unisim_outputs.py` | 14 pytest tests for all UniSim converter output modes (no COM needed) |
| `explore_unisim_com.py` | Diagnostic: dump UniSim COM object model from any .usc file |
| `pdf_to_figures.py` | Convert PDF pages to PNG images for AI analysis. Use `pdf_to_pngs()` for single files, `pdf_folder_to_pngs()` for batch. Requires `pymupdf` (`pip install pymupdf`). |
| `neqsim_runner/` | Supervised simulation execution — isolated subprocesses with own JVM, auto-retry, checkpointing, rate limiting, and context-window-exhaustion resilience. See [`neqsim_runner/README.md`](neqsim_runner/README.md). |

## Solving Engineering Tasks

The `new_task.py` script creates structured task folders for the
[AI-Supported Task Solving](../docs/development/TASK_SOLVING_GUIDE.md) workflow.

**Recommended:** Use the `@solve.task` Copilot agent instead — it runs the
script automatically and handles all 4 steps:

```
@solve.task JT cooling for rich gas at 100 bara
```

**Manual alternative:**

```bash
python devtools/new_task.py "JT cooling for rich gas" --type A
python devtools/new_task.py --list    # list existing tasks
python devtools/new_task.py --setup   # create task_solve/ without a task
```