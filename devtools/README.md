# devtools — Jupyter Development Helpers for NeqSim

This directory contains a small pip-installable Python package that makes it
easy to use **Jupyter notebooks** for NeqSim Java development — edit Java code,
compile, and immediately test from Python without rebuilding a JAR.

## Setting up a Python environment (recommended)

A **virtual environment (venv)** is an isolated, per-project Python. It keeps
NeqSim's dependencies separate from other projects and — importantly — puts the
`neqsim` command on your PATH automatically, avoiding the most common install
problems. Use one whenever you can.

**1. Create the environment (once per clone).** Run from the project root:

```powershell
# Windows (PowerShell)
py -3 -m venv .venv        # or: python -m venv .venv
```

```bash
# macOS / Linux
python3 -m venv .venv
```

This creates a `.venv/` folder containing a private copy of Python + pip.

**2. Activate it (every new terminal session).** Activation points `python`,
`pip`, and `neqsim` at the venv for the current shell:

```powershell
# Windows PowerShell
.\.venv\Scripts\Activate.ps1
```

```bat
:: Windows Command Prompt (cmd.exe)
.\.venv\Scripts\activate.bat
```

```bash
# macOS / Linux (bash / zsh)
source .venv/bin/activate
```

Your prompt now shows `(.venv)`. Confirm the right Python is active:

```powershell
python -c "import sys; print(sys.executable)"   # should print a path inside .venv
```

> **PowerShell blocks the activation script?** If you see
> *"running scripts is disabled on this system"*, allow local scripts for the
> current session and try again:
> ```powershell
> Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
> ```

**3. Install NeqSim devtools into the active venv:**

```powershell
.\install.ps1            # Windows  (uses the active venv automatically)
./install.sh             # macOS / Linux
```

**4. Deactivate when you're done** (or just close the terminal):

```bash
deactivate
```

**Using [uv](https://docs.astral.sh/uv/)?** uv can create and manage the venv
for you (and is much faster):

```bash
uv venv                        # create .venv
# activate as in step 2 above, then:
.\install.ps1 -Uv              # Windows   (or: ./install.sh --uv)
# ...or install directly with uv into the active venv:
uv pip install -e devtools/
```

> **No venv?** The installer still works — it falls back to your system/user
> Python. But then the `neqsim` command may not be on PATH; see
> [Troubleshooting](#troubleshooting-neqsim-not-found) below, or use
> `python -m neqsim_cli` as a fallback.

## Recommended no-admin runbook for the user

On a locked-down / corporate PC (e.g. no administrator rights), everything
below installs into your **user profile** and needs **no elevation**.
Prerequisites — Git, Python 3.8+, a JDK, and VS Code — must already be
provisioned per-user (via your software portal or `winget --scope user`).

```powershell
# 0. One-time Git setting (user-scope, no admin) — avoids >260-char path errors
git config --global core.longpaths true

# 1. Clone into your user profile (not C:\Program Files, not a short drive root)
cd $HOME\Documents\GitHub
git clone https://github.com/equinor/neqsim.git
cd neqsim

# 2. Python devtools in a venv (keeps the 'neqsim' command on PATH)
py -3 -m venv .venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned   # per-process, no admin
.\.venv\Scripts\Activate.ps1
.\install.ps1
neqsim doctor          # verifies Python, Java/JDK, Maven wrapper, agents

# 3. Java build — needs a JDK. No admin? Let the installer fetch a PORTABLE JDK:
.\install.ps1 -InstallJdk       # downloads Temurin into ~/.neqsim\jdk, sets user env vars
# (or install a JDK manually and set JAVA_HOME yourself), then in a NEW terminal:
.\mvnw.cmd install -DskipTests

# 4. Install AI agents/skills into the VS Code USER prompts folder (no admin)
neqsim agent install --all --vscode
neqsim skill install --all
```

**Behind a corporate proxy?** Set it for the current session (user-scope, no
admin) so `git`, `pip`, and agent-catalog downloads work:

```powershell
$env:HTTPS_PROXY = "http://proxy.example.com:8080"
$env:HTTP_PROXY  = $env:HTTPS_PROXY
```

**VS Code Insiders?** The `--vscode` export auto-detects Insiders; force the
target with `NEQSIM_VSCODE_FLAVOR=insiders` or point `NEQSIM_VSCODE_AGENTS_DIR`
at the exact folder. macOS/Linux users run the same steps with `./install.sh`
and forward slashes.

## One-time setup

**Recommended (works even when `pip`/`python` are not on PATH).** Run the
bootstrap installer from the **project root** — it finds a working Python
interpreter for you (preferring an active venv) and runs `python -m pip` under
the hood:

```powershell
# Windows (PowerShell):
.\install.ps1
```

```bash
# macOS / Linux:
./install.sh
```

**Using [uv](https://docs.astral.sh/uv/)?** Pass the `uv` flag to install with
the fast `uv` package manager instead of pip:

```powershell
.\install.ps1 -Uv        # Windows
./install.sh --uv        # macOS / Linux
```

**Manual alternative.** If you prefer to install by hand, always use
`python -m pip` (not bare `pip`) so it targets the interpreter you are running:

```bash
# From the project root (with your venv active):
python -m pip install -e devtools/

# ...or with uv:
uv pip install -e devtools/
```

> Do **not** use bare `pip install -e devtools/`. If `pip` is not on PATH it
> fails with "pip not available"; `python -m pip` avoids that. On Windows, if
> `python` opens the Microsoft Store, use the `py` launcher (`py -m pip ...`)
> or run `.\install.ps1`, which handles this automatically.

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
neqsim skill CMD         # manage community/private skills (list/search/install/export/doctor)
neqsim agent CMD         # manage community/private agents (list/search/install/export/doctor/validate/schema)
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
| `install_agent.py` | Community/private agent manager — `neqsim agent list/search/install[/--all]/remove/validate/schema` |
| `neqsim_doctor.py` | Diagnostic tool — checks Java, Maven, JAR, Python, agents, skills, cross-tool configs |
| `onboard.py` | Interactive onboarding wizard — walks new contributors through full environment setup |
| `consistency_checker.py` | **Pre-report quality gate.** Extracts numerical values from notebooks and results.json, detects inconsistencies (numerical mismatches, scope mismatches, contradictory claims). Run before `generate_report.py`. Produces `consistency_report.json`. |
| `unisim_reader.py` | UniSim/HYSYS .usc COM reader → NeqSim Python/notebook/EOT/JSON. Full-mode conversion with E300 full-fluid export, registry-driven operation mapping, port-specific forward refs, and auto-recycle wiring. |
| `test_unisim_outputs.py` | Pure-Python regression tests for UniSim converter output modes, E300 fluid export, operation handler strategies, and mapping summaries (no COM needed) |
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