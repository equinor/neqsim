# devtools — Python Development Tools for NeqSim

This directory is the Python project for NeqSim development scripts, CLI tools,
and Jupyter helpers. Use `uv` to resolve its dependencies and run its commands
from the repository root.

## Set up with uv

Python 3.11–3.14 is required. Upgrade Python 3.10 environments before syncing or
running the installer scripts. Pillow 13.0.0 or newer is required to fix
[CVE-2026-96639 / GHSA-v9pv-5gc8-w2m6](https://github.com/python-pillow/Pillow/security/advisories/GHSA-v9pv-5gc8-w2m6);
Pillow 13 no longer supports Python 3.10. The minimum applies to both the locked
uv project and editable pip installs.

Install `uv`, then sync the devtools environment from the repository root:

```bash
uv sync --project devtools
uv run --project devtools --locked neqsim --help
```

Use the test dependency group when running devtools tests:

```bash
uv sync --project devtools --group test
uv run --project devtools --locked --group test python -m pytest devtools/
```

The `install.sh` and `install.ps1` scripts remain available for users who need
to install the CLI into an existing Python environment. For repository
development and CI, use the uv project and committed lockfile.

## Recommended no-admin runbook for the user

On a locked-down / corporate PC (e.g. no administrator rights), everything
below installs into your **user profile** and needs **no elevation**.
Prerequisites — Git, Python 3.11–3.14, a JDK, and VS Code — must already be
provisioned per-user (via your software portal or `winget --scope user`).

```powershell
# 0. One-time Git setting (user-scope, no admin) — avoids >260-char path errors
git config --global core.longpaths true

# 1. Clone into your user profile (not C:\Program Files, not a short drive root)
cd $HOME\Documents\GitHub
git clone https://github.com/equinor/neqsim.git
cd neqsim

# 2. Python devtools in a venv ('neqsim' works in terminals where it is activated)
py -3 -m venv .venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned   # per-process, no admin
.\.venv\Scripts\Activate.ps1
.\install.ps1
neqsim doctor --skip-jar  # checks the CLI setup before building the Java JAR

# 3. Java build — needs a JDK. No admin? Let the installer fetch a PORTABLE JDK:
.\install.ps1 -InstallJdk       # downloads Temurin into ~/.neqsim\jdk, sets user env vars
# (or install a JDK manually and set JAVA_HOME yourself), then in a NEW terminal:
.\mvnw.cmd install -DskipTests
neqsim doctor                # full check, including the built JAR

# 4. Install AI agents/skills into ~/.copilot for VS Code Copilot (no admin)
neqsim agent install --all --vscode
neqsim skill install --all
#    Start over from a blank slate (only what NeqSim installed is removed —
#    core, community and enterprise agents/skills plus their ~/.copilot exports;
#    anything else in ~/.copilot is left alone). Preview first with --dry-run:
neqsim agent remove --all --with-skills --dry-run
neqsim agent remove --all --with-skills --yes
#    Or per catalog / per kind:
neqsim agent remove --all --source private --yes    # enterprise agents only
neqsim skill remove --all --source community --yes  # community skills only

# 5. Choose where solved tasks are saved (optional; defaults to <repo>\task_solve)
neqsim --set-task-root "D:\Engineering Tasks"   # or: cwd, to follow the terminal folder
neqsim --show-task-root

# 6. Choose the Word template reports are built from (optional)
neqsim --set-report-template "C:\Users\you\Documents\company report template.docx"
neqsim --show-report-template

# 7. Choose the folder agents read source documents from (optional)
neqsim --set-document-root "C:\Users\you\Documents\Engineering Documents"
neqsim --show-document-root
neqsim documents "C-12345"      # search the root and every subfolder
```

The task root is the parent folder `neqsim new-task` and the AI agents create each
dated task folder in. It is stored in `~/.neqsim/task_defaults.json`, so it is shared
by every NeqSim clone and keeps studies outside the repository. Precedence:
`--task-root PATH` > `NEQSIM_TASK_ROOT` > the saved default > `<repo>/task_solve`.
`neqsim --reset-task-root` removes the setting without moving existing tasks.
Add `--vscode` to any `--set-task-root` / `--set-document-root` command to also
add the folder to the VS Code workspace, or `--explorer` to open it in the file
explorer right away (both combine freely).

The report template is a `.docx`/`.dotx` file whose styles, fonts, headers, and
footers every generated Word report inherits — set it once and all later tasks
follow it. It is stored in the same settings file. Precedence:
`generate_report.py --template PATH` > `NEQSIM_REPORT_TEMPLATE` > the saved
default > built-in styling. The template's own body text is dropped (pass
`--keep-template-content` to keep it), `--no-template` ignores the setting for one
run, and `neqsim --reset-report-template` removes it. The scientific paper keeps
journal formatting and ignores the template.

Report files are named after the report title — a study titled "Hydrate margin
for the export line" produces `Hydrate_margin_for_the_export_line.docx` and
`.html` (paper: `..._Paper.docx`). Report files written under an earlier title
are deleted on regeneration, so a renamed study leaves no superseded deliverable.

The report language is English unless the task says otherwise. Set
`report.language` in the task's `study_config.yaml` (`en` default, `nb`/`no` for
Norwegian, or any ISO code); precedence is `generate_report.py --language CODE` >
`NEQSIM_REPORT_LANGUAGE` > `report.language` > English. It translates the report
furniture the generator owns — section headings, cover labels, caption prefixes,
navigation — and sets the document language of the `.docx` and `.html` so Word
spell-checks in that language. Write the study content (`results.json`,
`task_spec.md`) in the same language. A code with no built-in wording table still
gets its document language set, the headings stay English, and the generator says
so. The scientific paper stays English for journal submission.

For a formatting-only pilot, use
`neqsim report-design "SOURCE.docx" --output "formatting_pilot\PILOT.docx" --render`.
This opt-in finishing pass preserves the source, template fonts, branding and
technical wording. It improves table readability, adds missing numbered table
captions and page numbering, and writes a PDF, all-page PNG previews and a QA
JSON next to the derivative. The command refuses source overwrites and requires
`--overwrite` to replace an existing derivative. Use `--table-label Tabell` for
new Norwegian captions. Rendering needs Microsoft Word with pywin32 on Windows
or an installed LibreOffice; it never uses an online converter. The QA record
stays `PENDING` until someone actually inspects the page images. See the
[report-design and visual-QA skill](../.github/skills/neqsim-report-design-and-visual-qa/SKILL.md).
Normal report generation and scientific-paper styles are unchanged.

The canonical generator accepts both `benchmark_validation.tests` lists and
named benchmark mappings. Word and HTML outputs retain the source, numerical
comparisons, and PASS/FAIL status. Before rendering, the generator checks for
contradictions between benchmarks, validation, risk, discussions, and conclusions.
Findings appear in the console and the technical report's **Report Consistency
Review** section; calculation findings are written to `fixes_needed.json`.
These checks request review and do not rewrite the study's conclusions.

The document root is the folder the AI agents read source documents from —
standards, datasheets, P&IDs, vendor documents, historian exports — and **every
subfolder below it is in scope**. The setting is optional: it is either set or
undefined, and when undefined agents simply work from the documents you supply.
It is stored in the same settings file, so one setting covers all tasks, and each
new task records the resolved value as `inputs.document_root` in its
`study_config.yaml`. Precedence: explicit path > `NEQSIM_DOCUMENT_ROOT` > the
saved default > none. `neqsim documents [PATTERN]` lists matches from the root and
all subfolders, `neqsim documents --index [TASK_DIR]` refreshes the per-task
listing at `step1_scope_and_research/references/document_root_index.md` (written
at task creation so agents see the library without running the CLI;
`--index --all [ROOT]` backfills existing tasks, `--dry-run` shows the scope
first), and
`neqsim --reset-document-root` removes the setting. A
configured folder that no longer exists is reported as an error rather than
silently ignored.

The document root is the folder the AI agents read source documents from —
standards, datasheets, P&IDs, vendor documents, historian exports — and **every
subfolder below it is in scope**. The setting is optional: it is either set or
undefined, and when undefined agents simply work from the documents you supply.
It is stored in the same settings file, so one setting covers all tasks, and each
new task records the resolved value as `inputs.document_root` in its
`study_config.yaml`. Precedence: explicit path > `NEQSIM_DOCUMENT_ROOT` > the
saved default > none. `neqsim documents [PATTERN]` lists matches from the root and
all subfolders, and `neqsim --reset-document-root` removes the setting. A
configured folder that no longer exists is reported as an error rather than
silently ignored.

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

## Set up private / enterprise agents & skills

Public community agents/skills install with `neqsim agent install` /
`neqsim skill install`. To also use **company-private** agents and skills, register
your internal repository with `private-init`. It can register the repo **and**
sign you in with browser SSO in a single command:

```powershell
# GitHub (registers the repo and runs `gh auth login --web`):
# --catalog-path points at the catalog file published in the repo
neqsim agent private-init --repo my-org/neqsim-enterprise-agents --catalog-path enterprise-agents.yaml --login
neqsim skill private-init --repo my-org/neqsim-enterprise-skills --catalog-path enterprise-skills.yaml

# Internal Git server instead of GitHub:
neqsim agent private-init --url https://git.internal.company.com/neqsim/enterprise-agents.git

# Add more private repos later (same options; auto-creates the catalog if needed):
neqsim agent add-repo --repo my-org/another-agents-repo --login
neqsim skill add-repo --repo my-org/another-skills-repo --login

# Then discover + install as usual:
neqsim agent list --private
neqsim agent install <name> --vscode
neqsim skill install <name> --target vscode
```

> `private-init` and `add-repo` accept the same repo options — use `private-init`
> for first-time setup and `add-repo` to register additional repos later. Both
> print the catalog file path when they finish.

> **`neqsim` not recognized?** Without elevated privileges the console script may
> not be on PATH. Run every command above as `python -m neqsim_cli ...` instead
> (same arguments), or see
> [Troubleshooting](#troubleshooting-neqsim-not-found).

**Which file gets edited, and where?** `private-init` writes a per-user catalog:

| Content | File | Windows path |
|---------|------|--------------|
| Private skills | `~/.neqsim/private-skills.yaml` | `%USERPROFILE%\.neqsim\private-skills.yaml` |
| Private agents | `~/.neqsim/private-agents.yaml` | `%USERPROFILE%\.neqsim\private-agents.yaml` |

The command **prints the full path when it finishes** so you can open the file to
add or adjust entries by hand. These files are per-user and must never be committed
to a public repo. Useful options: `--source`, `--auth`, `--branch`,
`--catalog-path`, `--name-prefix`, and `--skill-path-glob` / `--agent-path-glob`
(run `neqsim agent private-init --help`). Authentication uses the GitHub CLI or Git
Credential Manager via browser SSO — NeqSim never requests, inspects, or stores
credentials. Full guide: [Enterprise Agent & Skill Repositories](../docs/integration/enterprise_agent_skill_repos.md)
and the [Skills & Agents Guide](../docs/integration/skills_guide.md#private-skill-catalog).

## One-time setup

**Recommended:** sync the uv project from the repository root:

```bash
uv sync --project devtools
uv run --project devtools --locked neqsim --help
```

The platform installer scripts remain available for existing Python
environments. They find a working interpreter and install the package there:

```powershell
# Windows (PowerShell):
.\install.ps1
```

```bash
# macOS / Linux:
./install.sh
```

> **"install.ps1 is not digitally signed" / execution-policy error on Windows?**
> That is PowerShell blocking scripts, not a problem with the file. Use the
> pure-batch installer instead — it avoids PowerShell, so it works even on locked-down machines where the execution
> policy is enforced by **Group Policy** (where even
> `powershell -ExecutionPolicy Bypass` is ignored):
>
> ```bat
> install.cmd            :: install neqsim-dev-setup (editable)
> install.cmd pdf        :: also install the optional [pdf] extras
> install.cmd ocr        :: also install the optional [ocr] extras
> install.cmd uv         :: install with the fast 'uv' package manager
> ```
>
> Equivalent one-liner (no script at all): `uv sync --project devtools`.
> The `-InstallJdk` option is `install.ps1`-only; on a GPO-locked machine
> install a JDK separately. With uv, use `uv run --project devtools --locked neqsim`
> instead of relying on a shell PATH entry.

**Using [uv](https://docs.astral.sh/uv/)?** The installer scripts also support
uv when installing into an existing environment. If uv is missing, the selected
uv installer path bootstraps it before installing the package:

```powershell
.\install.ps1 -Uv        # Windows
./install.sh --uv        # macOS / Linux
```

**Manual alternative.** From the repository root, sync the uv project:

```bash
uv sync --project devtools
```

Run the CLI without activating the project environment with
`uv run --project devtools --locked neqsim --help`.

This installs `neqsim_dev_setup` and the **`neqsim` CLI** into the project
environment, so notebooks can import it without `sys.path` hacks.

### The `neqsim` CLI

After installation you get a single `neqsim` command:

```bash
neqsim try               # interactive playground — explore NeqSim in 30 seconds
neqsim onboard           # interactive setup wizard
neqsim doctor            # check your environment is healthy (including a built JAR)
neqsim doctor --skip-jar # initial CLI/agent setup; explicitly omit only the JAR check
neqsim contribute        # guided wizard for your first contribution
neqsim new-task TITLE    # create a task-solving workspace
neqsim process-report MODEL   # process-study report (stream tables, BFD/PFD, balances, validation) from a Python/JSON/.neqsim model; see docs/development/PROCESS_STUDY_REPORT.md
neqsim process-overview SRC --out DIR  # one-sheet A3 process overview (colour + mono) and 16:9 slide from process_data.json or a model; see docs/development/PROCESS_OVERVIEW.md
neqsim flow-assurance-report ROUTE --basis B --cases C   # flow-assurance report + A3 pipeline drawings (--init DIR scaffolds inputs, --demo runs the synthetic example); see docs/development/FLOW_ASSURANCE_STUDY_REPORT.md
neqsim surf-study STUDY.yaml --out DIR                    # SURF design study: generated and solved NeqSim network, concept comparison, 15 A3 sheets, 20-chapter report (--init DIR scaffolds, --demo runs the synthetic example, --stage build-model|run|report); see docs/development/SURF_DESIGN_STUDY.md
neqsim file-issue TASK   # offer to file a recorded NeqSim gap as an issue (and PR)
neqsim new-skill NAME    # scaffold a new AI skill
neqsim skill CMD         # manage community/private skills (list/search/install/export/doctor)
neqsim agent CMD         # manage community/private agents (list/search/install/export/doctor/validate/schema)
```

Run `neqsim --help` for the full list.

### Troubleshooting: `neqsim` not found

If `neqsim` is not recognized in your shell, run it through the project:
`uv run --project devtools --locked neqsim`. The project environment does not need to be
activated or added to PATH.

**Unblock yourself immediately (no setup, always works):**
```bash
uv run --project devtools --locked neqsim --help
```
This is the same entry point the `neqsim` command runs, so every command in this
README works with it — prefix commands with `uv run --project devtools --locked`.

**Find out why, and fix it:**
```bash
uv run --project devtools --locked neqsim doctor --skip-jar   # names the cause under "CLI command"
uv run --project devtools --locked devtools/ensure_on_path.py  # puts the Scripts dir on your user PATH
```
Use `ensure_on_path.py` rather than editing PATH by hand: it writes the user
PATH through the registry, which avoids the truncation and the
machine-PATH-duplicated-into-user-PATH damage that `setx %PATH%` and
`SetEnvironmentVariable("PATH", $env:PATH + ...)` cause. Then open a **new**
terminal — and in VS Code, fully quit and reopen the window, because VS Code
captures PATH at launch and a new integrated terminal is not enough.

**Using the project environment:**
```bash
uv sync --project devtools
uv run --project devtools --locked neqsim --help
```
`uv run` selects the project environment automatically in every terminal.

**Where did pip put it?**
```bash
python -c "import sysconfig; print(sysconfig.get_path('scripts'))"
```
Typical locations: `%APPDATA%\Python\Python312\Scripts` (Windows, no admin),
`~/.local/bin` (Linux), `/opt/homebrew/bin` or `/usr/local/bin` (macOS Homebrew).

**GitHub Codespaces:**

The devcontainer automatically installs the CLI and configures PATH.
If `neqsim` is not found, run:
```bash
export PATH="$HOME/.local/bin:$PATH"
```

**Alternative — run without PATH (works everywhere):**
```bash
python -m neqsim_cli          # runs the CLI module directly
uv run --project devtools --locked devtools/neqsim_cli.py  # runs the script file directly
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
| `pyproject.toml`, `uv.lock` | Define and lock the uv-managed devtools project |
| `new_task.py` | Create task-solving folders for the 4-step AI workflow |
| `new_skill.py` | Scaffold a new skill for the agentic system (`neqsim new-skill "name"`) |
| `install_skill.py` | Community/private skill manager — `neqsim skill list/search/info/install[/--all]/installed/export/remove/publish/sync-packages/ensure/doctor/private-init/add-repo` |
| `install_agent.py` | Community/private agent manager — `neqsim agent list/search/info/install[/--all]/installed/export/remove/validate/run/schema/doctor/private-init/add-repo` |
| `neqsim_doctor.py` | Diagnostic tool — checks Java, Maven, JAR, Python, agents, skills, cross-tool configs |
| `onboard.py` | Interactive onboarding wizard — walks new contributors through full environment setup |
| `consistency_checker.py` | **Pre-report quality gate.** Extracts numerical values from notebooks and results.json, detects inconsistencies (numerical mismatches, scope mismatches, contradictory claims). Run before `generate_report.py`. Produces `consistency_report.json`. |
| `unisim_reader.py` | UniSim/HYSYS .usc COM reader → NeqSim Python/notebook/EOT/JSON. Full-mode conversion with E300 full-fluid export, registry-driven operation mapping, port-specific forward refs, and auto-recycle wiring. |
| `test_unisim_outputs.py` | Pure-Python regression tests for UniSim converter output modes, E300 fluid export, operation handler strategies, and mapping summaries (no COM needed) |
| `explore_unisim_com.py` | Diagnostic: dump UniSim COM object model from any .usc file |
| `pdf_to_figures.py` | Convert PDF pages to PNG images for AI analysis. Use `pdf_to_pngs()` for single files, `pdf_folder_to_pngs()` for batch. Requires the `pdf` extra (`uv sync --project devtools --extra pdf`). |
| `neqsim_runner/` | Supervised simulation execution — isolated subprocesses with own JVM, auto-retry, checkpointing, rate limiting, and context-window-exhaustion resilience. See [`neqsim_runner/README.md`](neqsim_runner/README.md). |

## Solving Engineering Tasks

The `new_task.py` script creates structured task folders for the
[AI-Supported Task Solving](../docs/development/TASK_SOLVING_GUIDE.md) workflow.

**Recommended:** Use the `@solve-task` Copilot agent instead — it runs the
script automatically and handles all 4 steps:

```
@solve-task JT cooling for rich gas at 100 bara
```

**Manual alternative:**

```bash
neqsim new-task "JT cooling for rich gas" --type A
neqsim new-task --list    # list existing tasks
neqsim new-task --setup   # create task_solve/ without a task
```
