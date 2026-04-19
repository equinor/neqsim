# devtools — Jupyter Development Helpers for NeqSim

This directory contains a small pip-installable Python package that makes it
easy to use **Jupyter notebooks** for NeqSim Java development — edit Java code,
compile, and immediately test from Python without rebuilding a JAR.

## One-time setup

```bash
# From the project root (with your venv active):
pip install -e devtools/
```

This installs `neqsim_dev_setup` as an editable package and registers the **`neqsim` CLI**
so every notebook in the venv can import it — no `sys.path` hacks needed.

### The `neqsim` CLI

After installation you get a single `neqsim` command:

```bash
neqsim try               # interactive playground — explore NeqSim in 30 seconds
neqsim onboard           # interactive setup wizard
neqsim doctor            # check your environment is healthy
neqsim contribute        # guided wizard for your first contribution
neqsim new-task TITLE    # create a task-solving workspace
neqsim new-skill NAME    # scaffold a new AI skill
neqsim skill CMD         # manage community/private skills (list/search/install/remove/publish)
```

Run `neqsim --help` for the full list.

### Troubleshooting: `neqsim` not found

If `neqsim` is not recognized after `pip install -e devtools/`, the Python
Scripts directory is not on your PATH.

**Using a virtual environment (easiest — always works):**
```bash
python -m venv .venv
# Windows:  .venv\Scripts\Activate.ps1
# macOS/Linux:  source .venv/bin/activate
pip install -e devtools/
neqsim --help   # works immediately — venv puts scripts on PATH
```

**Windows (PowerShell) — without venv:**
```powershell
# Check where pip installed it:
python -c "import sysconfig; print(sysconfig.get_path('scripts'))"

# Add it permanently (typical path for Python 3.12):
$scripts = [System.IO.Path]::Combine($env:APPDATA, 'Python', 'Python312', 'Scripts')
[Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";$scripts", "User")
# Restart your terminal for the change to take effect.
```

**Linux / macOS — without venv:**
```bash
# The scripts directory is usually ~/.local/bin
export PATH="$HOME/.local/bin:$PATH"
# Add the line above to ~/.bashrc or ~/.zshrc to make it permanent.

# macOS with Homebrew Python: scripts may be at
#   /opt/homebrew/bin/ (Apple Silicon) or /usr/local/bin/ (Intel)
# Check with: python3 -c "import sysconfig; print(sysconfig.get_path('scripts'))"
```

**GitHub Codespaces:**

The devcontainer automatically installs the CLI and configures PATH.
If `neqsim` is not found, run:
```bash
export PATH="$HOME/.local/bin:$PATH"
```

**Alternative — run without PATH (works everywhere):**
```bash
python -m neqsim_cli          # runs the CLI module directly
python devtools/neqsim_cli.py  # runs the script file directly
```

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
| `neqsim_cli.py` | **Unified CLI entry point** — dispatches `neqsim try`, `neqsim onboard`, etc. |
| `neqsim_try.py` | Interactive playground — drops into a Python REPL with a sample fluid ready |
| `neqsim_contribute.py` | Guided contribution wizard — walks new contributors through their first PR |
| `neqsim_dev_setup.py` | JVM bootstrap, class imports, compile + kernel restart |
| `pyproject.toml` | Makes it pip-installable (`pip install -e devtools/`) |
| `new_task.py` | Create task-solving folders for the 4-step AI workflow |
| `new_skill.py` | Scaffold a new skill for the agentic system (`neqsim new-skill "name"`) |
| `install_skill.py` | Community/private skill manager — `neqsim skill list/search/install/remove/publish` |
| `neqsim_doctor.py` | Diagnostic tool — checks Java, Maven, JAR, Python, agents, skills, cross-tool configs |
| `onboard.py` | Interactive onboarding wizard — walks new contributors through full environment setup |
| `consistency_checker.py` | **Pre-report quality gate.** Extracts numerical values from notebooks and results.json, detects inconsistencies (numerical mismatches, scope mismatches, contradictory claims). Run before `generate_report.py`. Produces `consistency_report.json`. |
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
neqsim new-task "JT cooling for rich gas" --type A
neqsim new-task --list    # list existing tasks
neqsim new-task --setup   # create task_solve/ without a task
```