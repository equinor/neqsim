---
name: neqsim-setup
description: "Configure and verify the NeqSim task-solving environment: where new task folders are created (task root), which folder of standards/datasheets/drawings agents read (document root), which Word template reports use, and whether Java, the packaged NeqSim JAR, the MCP server and the neqsim CLI are healthy. USE WHEN: a user invokes /neqsim-setup, asks to set or show the task folder, document folder, work path or report template, asks 'is my NeqSim setup working', runs first-time setup after installing the NeqSim agent plugin, or when @solve-task cannot resolve a task root. Works identically in a source checkout and in a plugin-only install; settings live in ~/.neqsim/task_defaults.json and apply to both."
last_verified: "2026-09-20"
---

# NeqSim setup (`/neqsim-setup`)

Walk the user through the three user-level settings the task workflow depends
on, then run `neqsim doctor`. Everything here is stored in
`~/.neqsim/task_defaults.json`, so it is shared by the workspace checkout, the
pip-installed toolkit and the agent plugin.

## 0. Find the CLI

Run `neqsim --show-task-root`. If the shell says `neqsim` is not recognized,
use `<python-executable> -m neqsim_cli --show-task-root` where
`<python-executable>` is `NEQSIM_PYTHON` if set, else the first `python3` /
`py -3` / `python` on PATH. Use that same form for every command below. If
neither works the toolkit is not installed yet. In a plugin install the
SessionStart hook installs it in the background on the first prompt of a
session; check `~/.neqsim/plugin-install/neqsim/install.log` (last line
`== ... OK` or `== ... FAILED` with the pip error above it). If the log is
missing or failed, install by hand from the plugin's vendored copy:
`<python-executable> -m pip install "<plugin folder>/toolkit"` where the
plugin folder is `%APPDATA%\Code\agentPlugins\github.com\equinor\neqsim-copilot-plugin\neqsim`
on Windows (`~/.config/Code/agentPlugins/...` on Linux,
`~/Library/Application Support/Code/agentPlugins/...` on macOS), or without a
plugin:
`<python-executable> -m pip install "neqsim-dev-setup @ git+https://github.com/equinor/neqsim.git#subdirectory=devtools"`.
Installing the toolkit also pip-installs the `neqsim` PyPI package (the packaged
JAR + jpype bridge), and the hook's own `install.log` runs `IMPORT_OK=1
IMPORT_FAILED=0` against it afterwards, so a plugin-only install always ends up
with the full NeqSim Python API importable — not just the CLI.

The `neqsim-community` and `neqsim-enterprise` plugins install their skill
packages through the same hook, into the same interpreter:
`~/.neqsim/plugin-install/<plugin>/install.log`. A healthy log ends with
`live dependencies: all N installed`, `IMPORT_OK=<n> IMPORT_FAILED=0` and
`== ... OK`. Each `FAILED <package> (<error>)` line names a skill package that
does not import and why — most often a missing live dependency
(`ModuleNotFoundError: stidapi`, `eq_api_connector`, `msal_bearer`, `pepr_client`,
`tagreader`), which means the STID / SAP-Maintenance / PDM / PEPR / historian
agents cannot reach their systems. Fix by hand with
`<python-executable> -m pip install -e "<plugin folder>/neqsim-enterprise" --no-deps`
followed by
`<python-executable> -m pip install -r "<plugin folder>/neqsim-enterprise/requirements-live.txt"`
(add `--no-index --find-links "<plugin folder>/neqsim-enterprise/wheels"` for an
offline bundle). A missing log means the hook never ran for that plugin: start a
new chat (plugin versions before 1.1.1 / 1.0.1 failed with *The argument
'/scripts/install_skill_packages.ps1' ... does not exist* because VS Code does
not expand `${PLUGIN_ROOT}`; update the plugin).

## 1. Ask for the three paths (skip any the user already has)

Show the current values first:

```
neqsim --show-task-root
neqsim --show-document-root
neqsim --show-report-template
```

Then ask, one question each, with the current value as the default:

| Setting | Question | Command |
|---|---|---|
| Task root | "Where should new task folders be created? (a OneDrive/shared folder is common; `cwd` means the terminal's folder)" | `neqsim --set-task-root "PATH"` |
| Document root | "Which folder holds your standards, TRs, datasheets and drawings? Agents search it and all subfolders before declaring a document unavailable. Leave empty to skip." | `neqsim --set-document-root "PATH"` |
| Report template | "Which `.docx`/`.dotx` template should Word reports use? Leave empty for built-in styling." | `neqsim --set-report-template "PATH"` |

After setting the task root or the document root (skip for `cwd`, which has no
single folder to reveal), ask: "Open this folder in File Explorer now?" and, if
yes, re-run the same `--set-task-root` / `--set-document-root` command with
`--explorer` appended (add `--vscode` too if the user also wants it added to the
VS Code workspace — both flags combine freely, e.g.
`neqsim --set-document-root "PATH" --vscode --explorer`). Without `--explorer`
the command still prints how to open the folder by hand.

Quote paths with spaces. Do not guess or persist a path the user did not give;
`--reset-task-root` / `--reset-document-root` / `--reset-report-template`
remove a saved value. Per-session overrides are the environment variables
`NEQSIM_TASK_ROOT`, `NEQSIM_DOCUMENT_ROOT`, `NEQSIM_REPORT_TEMPLATE`.

## 2. Verify

Run `neqsim doctor` and relay the result. It detects its own mode:

- **workspace** (source checkout): Java, Maven, built JAR, Python `neqsim`,
  agents/skills files, task root, template, document root, git.
- **toolkit** (pip / plugin, no checkout): Java (>= 8 for NeqSim, **>= 21 for
  the MCP server**), the packaged NeqSim JAR starts and flashes, MCP server jar
  cache, `neqsim` CLI, task root, template, document root.

Report only the `[!!]` and `[??]` lines with their fix hints. Typical fixes:

| Symptom | Fix |
|---|---|
| Java < 21 | install a JDK 21+ first on PATH (`winget install EclipseAdoptium.Temurin.21.JDK`); NeqSim itself runs on 8+ but the MCP server needs 21 |
| no packaged JAR | `<python-executable> -m pip install neqsim` or set `NEQSIM_JAR` |
| `neqsim` on PATH comes from another interpreter | use `<python-executable> -m neqsim_cli` explicitly |
| task root not writable | choose another folder with `--set-task-root` |

## 3. Confirm

Finish with a three-line summary: task root, document root (or "not set - work
from user-supplied documents"), report template (or "built-in"), and the
`doctor` pass count. Optionally list the document root's contents with
`neqsim documents` so the user sees what agents will be able to find.
