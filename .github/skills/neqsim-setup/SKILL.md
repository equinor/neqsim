---
name: neqsim-setup
description: "Configure and verify the NeqSim task environment: task root, document root, Word report template, and health of Java, the NeqSim JAR, the MCP server and the neqsim CLI. USE WHEN: a user runs /neqsim-setup, asks to set or show the task/document folder, work path or report template, asks 'is my NeqSim setup working', does first-time setup after installing the plugin, or @solve-task cannot resolve a task root. Settings live in ~/.neqsim/task_defaults.json."
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
plugin folder is
`%USERPROFILE%\.vscode\agent-plugins\github.com\equinor\neqsim-copilot-plugin\neqsim`
on Windows (`~/.vscode/agent-plugins/...` on Linux and macOS). A marketplace
install lands there, **not** under `agentPlugins` in the VS Code user-data
folder — that one only holds synced customization plugins, so do not conclude
from its absence that the plugin is not installed. A Copilot CLI install lands
in `~/.copilot/installed-plugins/neqsim-copilot-plugin/<plugin>`. Without a
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
new chat, then check the plugin version against these known hook failures —
all fixed by updating the plugin:

| Hook error in chat | Cause | Fixed in |
|---|---|---|
| *The argument '/scripts/install_skill_packages.ps1' ... does not exist* | VS Code does not expand `${PLUGIN_ROOT}` | 1.1.1 / 1.0.1 |
| PowerShell parse errors with variables replaced by nothing (`+  = ''; if (-not  ...`) | the hook's `windows` string had its `$` tokens stripped | a later 1.1.x / 1.0.x |
| *install_skill_packages.ps1 ... is not digitally signed. You cannot run this script on the current system* (often arriving as a `#< CLIXML` blob), once per installed plugin | a Group Policy execution policy on a managed PC overrides `-ExecutionPolicy Bypass`, so the hook's unsigned `.ps1` cannot run; nothing is pip-installed and the MCP entry is never repaired | neqsim 1.1.7 / community 1.0.5 / enterprise 1.0.8 |

On an older plugin, do the hook's work by hand (`pip` and a `.py` are not
covered by the execution policy):
`<python-executable> "<plugin folder>\scripts\install_skill_packages.py"` with
`PLUGIN_ROOT` set to that folder, for each installed plugin.

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
| MCP output shows *Could not find or load main class `${PLUGIN_ROOT}`.servers.NeqsimMcpLauncher.java*, exit code 1 | expected: VS Code does not expand `${PLUGIN_ROOT}` in an Agent Plugins `mcp.json` (microsoft/vscode#336882), so the plugin's own `neqsim` server entry always errors. Do **not** blame Java and do not edit the plugin's `mcp.json`. The working entry is an absolute one in the **VS Code user** `mcp.json` (`%APPDATA%\Code\User\mcp.json`), which the SessionStart hook writes; if it is missing the hook never ran — see §0. Manual equivalent: `"servers": { "neqsim": { "type": "stdio", "command": "java", "args": ["<plugin folder>/servers/NeqsimMcpLauncher.java"] } }`, then open a new chat |
| *Registered server java* FAIL, or no `neqsim_*` tools and `~/.neqsim/mcp-server/` missing | the `neqsim` entry in the VS Code user `mcp.json` runs a Java below 21 (typically Java 8 first on PATH shadowing a newer JDK): the launcher is source-launched and dies with *Could not find or load main class* before it can complain. Re-run the plugin install script (it pins the newest JDK 21+ into the entry) or set `NEQSIM_MCP_JAVA` and start a new chat (the session hook re-pins). Prove the fix without VS Code: `"<jdk21+>/bin/java" "<plugin>/servers/NeqsimMcpLauncher.java" --prefetch` (exit 0 = OK). MCP tools bind at chat-session start: after any fix open a **new chat** or Reload Window |
| no packaged JAR | `<python-executable> -m pip install neqsim` or set `NEQSIM_JAR` |
| `neqsim` on PATH comes from another interpreter | use `<python-executable> -m neqsim_cli` explicitly |
| task root not writable | choose another folder with `--set-task-root` |

## 3. Confirm

Finish with a three-line summary: task root, document root (or "not set - work
from user-supplied documents"), report template (or "built-in"), and the
`doctor` pass count. Optionally list the document root's contents with
`neqsim documents` so the user sees what agents will be able to find.
