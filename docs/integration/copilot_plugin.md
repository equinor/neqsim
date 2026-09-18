---
title: "NeqSim Copilot Agent Plugin"
description: "Bundle NeqSim's agents, skills and MCP server for GitHub Copilot in VS Code; install, verify and update the plugin on Windows, Linux and macOS."
---

# NeqSim Copilot Agent Plugin

Install the repository's engineering agents, reusable skills and MCP calculation
server as one plugin. The bundle uses **Agent Plugins 1.0** and includes every
`.github/agents/*.agent.md` and `.github/skills/*/SKILL.md` from the source checkout,
plus supporting skill files. `bundle-inventory.json` records the actual counts,
source revision, source/output hashes and normalized names.

The builder reads the canonical definitions each time. There is no second set of
hand-maintained agent instructions. Community and private enterprise repositories
are separate installations; this bundle does not copy them or their credentials.
PaperLab's internal role documents remain in the PaperLab workspace and are
accessed through the bundled PaperLab gateway agent.

## Quick installation

1. Use a current VS Code with GitHub Copilot and agent plugin support enabled
   (`chat.plugins.enabled`). Organization policies may control availability.
2. Obtain an assembled bundle from the **Copilot plugin** workflow artifact, or
   build it from a NeqSim checkout as shown below. Extract the ZIP to a persistent
   folder, for example `C:/appl/neqsim-plugin/neqsim`.
3. Open **Preferences: Open User Settings (JSON)** in VS Code and merge these
   settings with your existing settings, using the actual extracted directory:

   ```json
   {
     "chat.plugins.enabled": true,
     "chat.pluginLocations": {
       "C:/appl/neqsim-plugin/neqsim": true
     }
   }
   ```

   On Linux/macOS use an absolute path such as `/home/you/plugins/neqsim`.
   Select the directory containing `plugin.json`, not its parent or the ZIP.
4. Reload VS Code if needed. Open **Chat: Open Customizations** and inspect the
   agents and skills. Select **neqsim-router** for general engineering requests,
   or a specialist such as **neqsim-thermo-fluid** or **neqsim-process-model**.
5. Run **MCP: List Servers** and confirm the plugin's `neqsim` server is available.
   Ask: “Use the NeqSim MCP tools to report the server capabilities and available
   flash calculations.” Verify that a tool call actually occurred.

The artifact from **Copilot plugin** uses Docker: Docker must be installed and
running, with access to `ghcr.io/equinor/neqsim-mcp-server`. The first start may
pull the image. The **Build MCP Server Release** workflow also produces a bundle
with the matching Java server embedded; that variant needs **Java 21 or newer**
on PATH and does not require Docker, Python or Maven to run MCP calculations.

This repository's root is not itself an installable plugin. Use the assembled
directory or extracted artifact, rather than **Install Plugin From Source** with
the repository URL. Copilot CLI users can install the assembled directory with
`copilot plugin install /absolute/path/to/neqsim`; VS Code also discovers plugins
installed by Copilot CLI.

## Build a bundle locally

The builder requires Python 3.9+ and Git, with no third-party Python packages.
Use the Python interpreter already selected for your NeqSim workspace. These
examples assume the repository root is the working directory.

Windows PowerShell, using the documented NeqSim interpreter location:

```powershell
& 'C:\appl\neqsim-venv\Scripts\python.exe' devtools/build_copilot_plugin.py --archive target/neqsim-copilot-plugin.zip
```

Linux/macOS, substituting the absolute path to your selected interpreter:

```bash
/absolute/path/to/python devtools/build_copilot_plugin.py --archive target/neqsim-copilot-plugin.zip
```

The default output is `target/copilot-plugin/neqsim`. The command prints the exact
VS Code settings to register that directory. Use `--output` to select a persistent
folder outside the checkout. Existing output folders and archives are never
overwritten: use a new versioned location for an update, verify it, then change
the registered path. Locally registered plugins do not fetch updates themselves.

For reproducible Docker deployments, pass `--image` with an approved immutable
image digest. The default `ghcr.io/equinor/neqsim-mcp-server:latest` follows the
existing repository MCP setup, but does not guarantee the same NeqSim revision
as the packaged skills. Use the embedded-jar workflow when those must match.

To include an existing **NeqSim MCP server uber-jar**, pass `--mcp-jar` instead:

```powershell
& 'C:\appl\neqsim-venv\Scripts\python.exe' devtools/build_copilot_plugin.py --mcp-jar neqsim-mcp-server/target/neqsim-mcp-server-1.0.0-SNAPSHOT-runner.jar --output target/copilot-java/neqsim --archive target/neqsim-copilot-java.zip
```

Build that jar using the existing
[MCP server build instructions](https://github.com/equinor/neqsim/blob/master/neqsim-mcp-server/README.md).
The builder validates that it is a runnable NeqSim MCP archive and records its
SHA-256. It copies the jar into the plugin and launches it with
`-Dquarkus.profile=stdio`, allowing multiple VS Code windows without a fixed
HTTP-port collision. No build, download script or installation hook runs when
the plugin is enabled.

## What is available where?

| Capability | Runtime or workspace needed |
|---|---|
| Agents and skills in Copilot Chat | Installed plugin and supported Copilot client |
| MCP calculations | Docker variant: running Docker; embedded variant: Java 21+ |
| Python notebooks and report scripts | NeqSim source checkout, its configured Python environment and task dependencies |
| Java implementation and tests | NeqSim source checkout and repository build tools |
| PaperLab workflows | Existing `neqsim-paperlab` workspace and its dependencies |
| Enterprise data retrieval | Separately configured enterprise integrations and access |

The plugin installation is a resource directory, not a working copy of NeqSim.
Keep task outputs in the selected task directory. For workflows referring to
`devtools/`, `src/`, or `neqsim-paperlab/`, open your NeqSim checkout or set the
existing `NEQSIM_PROJECT_ROOT` to it; use its configured Python interpreter.
The plugin does not modify Python selection or create environments.

Installed names use hyphens: for example, `analyze_convergence` becomes
`analyze-convergence`, and `thermo.fluid.agent.md` becomes
`neqsim-thermo-fluid.agent.md`. Internal links and known agent/skill references
are relocated. Repository-only skill metadata is retained under `metadata`.
Links to source documentation are pinned to the source revision recorded in the
bundle. References to external skills remain external requirements.

Avoid enabling duplicate copies of the same NeqSim agents/skills and MCP server.
If your workspace already discovers `.github/agents` and `.github/skills`, use
that setup there and disable the plugin for that workspace, or use the plugin
in a separate study workspace. Do not delete workspace definitions to install it.

The plugin retains the server's existing deployment-profile behavior. Changing
profiles, configuring hosted authentication, or granting access to enterprise
systems is a separate server configuration task. Neither packaging nor the
transport smoke check certifies engineering calculations.

## Validate the installation

From the source checkout, run with your selected Python interpreter:

```powershell
& 'C:\appl\neqsim-venv\Scripts\python.exe' devtools/smoke_copilot_plugin.py C:/appl/neqsim-plugin/neqsim --timeout 90
```

The check launches the command from the installed `mcp.json`, initializes MCP,
checks that `runFlash`, `runProcess` and `getCapabilities` are listed, and calls
`getCapabilities`. It reports the server identity and discovered tool count.
The MCP protocol qualification and release builds run the same check against
their embedded jar before upload, including on pull requests affecting packaging.
This is a packaging/transport test; it does not automate the VS Code UI.

If agents are absent, check the registered directory and VS Code's plugin
setting. If tools are absent, inspect **MCP: List Servers** and the server log:
check Docker access for the Docker variant, or `java -version` and the embedded
`server/neqsim-mcp-server.jar` for the Java variant. Use the folder's own
`bundle-inventory.json` to identify the build when reporting a problem.

## Format references

- [VS Code agent plugins](https://code.visualstudio.com/docs/agent-customization/agent-plugins)
- [Agent Plugins manifest](https://agent-plugins.org/plugin-authors/plugin-manifest)
- [Portable MCP configuration](https://agent-plugins.org/plugin-authors/mcp-servers)
- [Copilot CLI plugin reference](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference)
