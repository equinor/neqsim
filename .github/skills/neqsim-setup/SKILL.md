---
name: neqsim-setup
description: "Configure and verify the NeqSim task-solving environment: where new task folders are created (task root), which folder of standards/datasheets/drawings agents read (document root), which Word template reports use, and whether Java, the packaged NeqSim JAR, the MCP server and the neqsim CLI are healthy. USE WHEN: a user invokes /neqsim-setup, asks to set or show the task folder, document folder, work path or report template, asks 'is my NeqSim setup working', runs first-time setup after installing the NeqSim agent plugin, or when @solve-task cannot resolve a task root. Works identically in a source checkout and in a plugin-only install; settings live in ~/.neqsim/task_defaults.json and apply to both."
last_verified: "2026-09-18"
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
neither works the toolkit is not installed yet: run
`<python-executable> -m pip install "neqsim-dev-setup @ git+https://github.com/equinor/neqsim.git#subdirectory=devtools"`
(the plugin's SessionStart hook does this automatically on the next session).

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
