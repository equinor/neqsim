---
title: "PaperLab CLI and VS Code Chat Install"
description: "Reference for the neqsim paperlab CLI command group. Covers the no-subcommand help output, list command, dry run, and VS Code Chat install command."
---

# PaperLab CLI and VS Code Chat Install

The `neqsim paperlab` command group manages PaperLab agents and skills from the NeqSim CLI. Running `neqsim paperlab` by itself is only a help command; it confirms that the PaperLab command group exists, but it does not install agents or skills.

## Command Summary

| Command | What it does |
|---------|--------------|
| `neqsim paperlab` | Prints PaperLab CLI help and exits. No files are copied. |
| `neqsim paperlab list` | Lists the PaperLab gateway agent and skills available for export. |
| `neqsim paperlab install --vscode --dry-run` | Shows where files would be copied without installing them. |
| `neqsim paperlab install --vscode` | Installs the `@paperlab` gateway agent and declared skills for VS Code Chat. |
| `neqsim paperlab install --vscode --include-internal` | Also exports internal specialist PaperLab agents and skills for compatibility or direct use. |

## What `neqsim paperlab` Means

Run this command when you only want to check the available PaperLab subcommands:

```powershell
neqsim paperlab
```

It prints the PaperLab command help:

```text
usage: neqsim paperlab [-h] {install,list} ...

Install PaperLab agents and skills for VS Code Chat.

positional arguments:
	{install,list}
		install       Install PaperLab agents and skills
		list          List PaperLab agents and skills

options:
	-h, --help      show this help message and exit
```

This output is expected and is not an error. It means the PaperLab CLI entry point is available. To see what would be exported, run `neqsim paperlab list`; to install into VS Code Chat, run `neqsim paperlab install --vscode`.

## Check What Is Available

List the PaperLab gateway agent and skills that can be exported:

```powershell
neqsim paperlab list
```

The normal public export contains one gateway agent named `paperlab` and the PaperLab skills it declares. Use the gateway agent as `@paperlab` in VS Code Chat after installation.

## Preview the Install

Before copying files, preview the export destinations:

```powershell
neqsim paperlab install --vscode --dry-run
```

On Windows with the default user scope, the dry run should show destinations under:

```text
%APPDATA%\Code\User\prompts
%APPDATA%\Code\User\prompts\skills
```

For example:

```text
C:\Users\<user>\AppData\Roaming\Code\User\prompts\paperlab.agent.md
C:\Users\<user>\AppData\Roaming\Code\User\prompts\skills\paperlab_publication_opportunity_mining\SKILL.md
```

## Install for VS Code Chat

Run the actual install from the NeqSim repository:

```powershell
neqsim paperlab install --vscode
```

Then reload VS Code with `Developer: Reload Window` so Copilot Chat picks up the new agent and skills.

## Optional Internal Exports

The default install exports the single `@paperlab` gateway agent and its declared skills. This is enough for normal VS Code Chat usage.

Use internal exports only when you explicitly need legacy or direct access to specialist PaperLab agents:

```powershell
neqsim paperlab install --vscode --include-internal
```

## Troubleshooting

If `neqsim paperlab` prints this usage text, the command is available but no install has run yet:

```text
usage: neqsim paperlab [-h] {install,list} ...
```

Run `neqsim paperlab install --vscode` to install. If the installer cannot find a VS Code prompts folder, specify the destination explicitly:

```powershell
neqsim paperlab install --vscode --vscode-agents-dir "%APPDATA%\Code\User\prompts" --vscode-skills-dir "%APPDATA%\Code\User\prompts\skills"
```