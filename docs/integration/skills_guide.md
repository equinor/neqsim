---
title: "Skills and Agents Guide: Creating, Using, and Managing NeqSim Agentic Extensions"
description: "Comprehensive guide to skills and agents in NeqSim's agentic engineering system: core program skills, community and private skills, and installable agent workflow definitions. Covers skill anatomy, agent packaging, installation, and examples including STID document retrieval and plant data integration."
---

# NeqSim Skills and Agents

> **TL;DR — Quick Start by Extension Type**
>
> | I want to… | Do this |
> |------------|---------|
> | **Use an existing core skill** | Nothing — workspace agents load them automatically from the `.github/skills/` discovery layer |
> | **Create a new core skill** | `neqsim new-skill "name"` → edit SKILL.md → register in README + copilot-instructions → PR |
> | **Install a community skill** | `neqsim skill install neqsim-topic` → canonical install in `~/.neqsim/skills` → export with `--target vscode` or `--target generic` |
> | **Install a community agent** | `neqsim agent install agent-name` → canonical install in `~/.neqsim/agents` → export with `--target vscode` or `--target generic` |
> | **Check PaperLab commands** | `neqsim paperlab` prints help only; this confirms the command exists but does not install anything |
> | **Use PaperLab in VS Code** | `neqsim paperlab install --vscode` for the `@paperlab` gateway; add `--include-internal` only for direct specialist-agent compatibility |
> | **Publish my skill to the community** | Host SKILL.md in a GitHub repo → `neqsim skill publish user/repo` → PR gets reviewed |
> | **Set up company enterprise repos** | Create private enterprise skills + agents repos → publish `enterprise-skills.yaml` and `enterprise-agents.yaml` → configure `EH_SOURCES_FILE` for the Engineering Harness |
> | **Create a private/local skill** | Put `SKILL.md` under `~/.neqsim/skills/<name>/` or a private catalog → export with `--target vscode` or `--target generic` |
> | **Share private skills with my team** | `neqsim skill private-init` → edit `~/.neqsim/private-skills.yaml` → distribute to team |
>
> Read on for the full details of each workflow.

Skills are the knowledge layer of NeqSim's agentic engineering system. They are structured markdown documents that encode domain-specific expertise — API patterns, code templates, decision rules, reference data, common error patterns, and recovery strategies — in a format that AI agents can read, interpret, and apply to engineering tasks.

Agents are the workflow layer. An agent defines a role, objective, constraints,
handoff pattern, and the skills it expects to load. In professional agent tools
this separation is common: skills are reusable capabilities; agents are curated
workflows that combine those capabilities for a specific job.

This guide covers skills, installable agents, how to create each type, and how they work together. For company-private enterprise repositories and installable internal catalogs, see [Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md). For the current cross-catalog dependency overview, see [Agent to Skill Map](agent_skill_map.md).

## Canonical Installs and Tool Exports

NeqSim separates source-of-truth content from tool-specific discovery folders:

| Layer | Purpose | Typical path |
|-------|---------|--------------|
| **Core workspace discovery** | Skills and agents committed with NeqSim and visible to VS Code in this workspace | `.github/skills/`, `.github/agents/` |
| **User-level canonical install** | Community, private, and enterprise content installed for one user without modifying the repo | `~/.neqsim/skills/`, `~/.neqsim/agents/` |
| **VS Code export** | Generated compatibility copies for VS Code Copilot discovery | `%APPDATA%\Code\User\prompts\skills` for installed skills and `%APPDATA%\Code\User\prompts` for installed agents; `.github/skills` and `.github/agents` only for explicit core workspace exports |
| **Generic export** | Tool-neutral copies and manifest for Codex, Claude, harnesses, or other coding agents | `~/.neqsim/export/generic/` |
| **PaperLab canonical source** | PaperLab's full internal role and skill library | `neqsim-paperlab/agents/`, `neqsim-paperlab/skills/` |

Treat `.github/skills` and `.github/agents` as core workspace discovery surfaces, not the default destination for installed community/private content. Installed content should be managed through `~/.neqsim` and, for VS Code, exported to the user's private prompts folders unless a maintainer explicitly wants a workspace export. PaperLab follows the same principle: `neqsim-paperlab/` is canonical, while the VS Code default is the single `@paperlab` gateway plus its declared public skills.

For the exact PaperLab CLI commands, including the no-subcommand help output, `list`, `--dry-run`, and the actual install command, see [PaperLab CLI and VS Code Chat Install](paperlab_vscode_install.md).

## The Four Types of Skills

NeqSim's skill system is organised into four tiers, each with a different scope, audience, and lifecycle:

| Type | Location | Scope | Who Creates | Version Controlled |
|------|----------|-------|-------------|-------------------|
| **Core Program Skills** | `.github/skills/` discovery layer in the NeqSim repo | All users, all agents | NeqSim maintainers | Yes (Git, PR review) |
| **Community Skills** | External repos, listed in `community-skills.yaml` | Anyone who installs them | Domain experts, contributors | Yes (author's repo) |
| **Enterprise Skills** | Private company repos, listed in `enterprise-skills.yaml` | Company/project runtimes | Company domain owners | Yes (private Git, governed review) |
| **Local Private Skills** | `~/.neqsim/skills/` on your machine, exported per tool when needed | Only you | You | Optional (your choice) |

### Core Program Skills

These are the workspace-visible skills shipped with NeqSim. They cover NeqSim's own API (`neqsim-api-patterns`), Java 8 rules (`neqsim-java8-rules`), and domain expertise that references NeqSim internals (flow assurance, CCS, distillation, platform modeling, etc.). Agents load these automatically when relevant.

**Examples:** `neqsim-api-patterns`, `neqsim-flow-assurance`, `neqsim-troubleshooting`, `neqsim-platform-modeling`

### Community Skills

Skills contributed by the community but hosted outside the core repo. They are listed in the machine-readable `community-skills.yaml` catalog and installed via the CLI tool. Community skills cover public workflows and domains that do not require deep NeqSim internal knowledge or company-private policy.

**Examples:** educational screening checks, public regional regulatory workflows, alternative EOS validation approaches

### Enterprise Skills and Agents

Enterprise skills and agents live in private company repositories. They are listed in `enterprise-skills.yaml` and `enterprise-agents.yaml`, declare `trust: internal`, and are installed by configuring the runtime's plugin sources. Enterprise skills use `enterprise-*` IDs and may extend approved public `neqsim-*` skills. Enterprise agents orchestrate both layers through `required_skills`.

**Examples:** company hydrate-margin policy overlays, governed STID-to-NeqSim study preparation, internal document-reading workflows, plant-data integration wrappers

See [Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md) for the repository template, catalog examples, `plugins/sources.yaml` configuration, token handling, and validation commands.

### Local Private Skills

Skills stored on your own machine at `~/.neqsim/skills/`. These encode private or company-specific knowledge that should never be committed to a public repository — plant historian tag mappings, internal STID retrieval procedures, company standards, proprietary workflows. Export them to VS Code or generic agent folders only when a tool needs a discovery copy.

**Examples:** Equinor STID document retrieval, plant data reading from specific PI servers, company TR document requirements

---

## How Skills Work

When an agent receives a task, the skill loading process works as follows:

1. **Request analysis** — The agent examines the user's request and identifies the engineering domain(s) involved.
2. **Skill matching** — The agent compares the identified domains against skill descriptions. Each skill's description and `USE WHEN` clause provides matching criteria.
3. **Selective loading** — Only matched skills are loaded into the agent's context using `read_file`. A hydrate calculation loads `neqsim-flow-assurance` and `neqsim-api-patterns`. A distillation design loads `neqsim-distillation-design` and `neqsim-api-patterns`.
4. **Application** — The agent combines its general reasoning capabilities with the skill's specific domain knowledge to produce correct, working code and analysis.

This dynamic loading keeps the agent's finite context window focused on task-relevant knowledge rather than loading all 28+ skills at once.

---

## Anatomy of a Skill File

Every skill follows the same structure regardless of type. Here is the anatomy of a `SKILL.md` file:

```markdown
---
name: neqsim-my-topic
version: "1.0.0"
description: "Short description. USE WHEN: trigger conditions for when agents should load this skill."
last_verified: "2026-04-19"
requires:
  python_packages: [pandas]    # pip packages needed to run patterns
  # java_packages: []           # Maven artifacts beyond NeqSim core
  # env: [MY_API_KEY]           # environment variables needed at runtime
  # network: [equinor-vpn]      # network access requirements
---

# Skill Title

One-paragraph summary of what this skill covers and when agents should load it.

## When to Use This Skill

- When the user asks about [topic]
- When a task involves [specific operation]

## Key Concepts

### Concept 1
Explanation of domain concepts the agent needs to understand.

## NeqSim Code Patterns

### Pattern: Basic Setup
```java
// Java 8 compatible — no var, List.of(), etc.
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.setMixingRule("classic");
```

### Pattern: Python (Jupyter)
```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
```

## Common Mistakes

| Symptom | Cause | Fix |
|---------|-------|-----|
| Zero viscosity after flash | Missing `initProperties()` | Add `fluid.initProperties()` after flash |

## References

- Applicable standards (API, ISO, NORSOK, etc.)
```

The key sections and their purposes:

| Section | Purpose |
|---------|---------|
| **YAML frontmatter** | Machine-readable metadata: name, version, description with `USE WHEN` triggers, verification date, dependency declarations |
| **When to Use** | Explicit trigger conditions so agents know when to load the skill |
| **Key Concepts** | Domain knowledge the agent needs to reason correctly |
| **Code Patterns** | Copy-paste templates with inline comments marking mandatory steps |
| **Common Mistakes** | Error catalog: symptom, cause, fix — prevents agents from repeating known failures |
| **References** | Standards, papers, and documentation links for traceability |

### Frontmatter Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique identifier, must start with `neqsim-` |
| `version` | Yes | Semver version (e.g., `"1.0.0"`). Bump major for breaking changes, minor for new patterns, patch for fixes |
| `description` | Yes | Short description including `USE WHEN:` trigger clause |
| `last_verified` | Yes | ISO date when code patterns were last tested against NeqSim |
| `requires` | No | Dependency declarations (see below) |
| `requires.python_packages` | No | Python packages needed (e.g., `[tagreader, pandas]`) |
| `requires.java_packages` | No | Maven artifacts beyond NeqSim core |
| `requires.env` | No | Environment variables needed at runtime (e.g., `[PI_WEB_API_URL]`) |
| `requires.network` | No | Network access requirements (e.g., `[equinor-vpn]`) |

The `requires` block is inspired by [OpenClaw's runtime metadata](https://github.com/openclaw/clawhub). It lets agents check prerequisites before attempting patterns that need external access or packages. Agents should warn the user if a declared requirement is not met.

---

## Creating Core Program Skills

Core skills are shipped with NeqSim and reference internal Java classes, method signatures, or package structure. They require PR review by maintainers.

### Step 1: Scaffold the Skill

```bash
neqsim new-skill "neqsim-my-topic"
neqsim new-skill "neqsim-my-topic" --description "Short description of what it covers"
```

This creates `.github/skills/neqsim-my-topic/SKILL.md` with a structured template.

### Step 2: Fill in the Template

Edit the generated `SKILL.md`. Focus on three critical sections:

1. **When to Use** — be specific about trigger conditions
2. **Code Patterns** — every pattern must work against NeqSim's actual API
3. **Common Mistakes** — catalogue real failures you've encountered

### Step 3: Verify Code Patterns

Every code example must be tested:

```bash
# For Java patterns — create a JUnit test
./mvnw test -Dtest=MySkillPatternsTest

# For Python patterns — run in a notebook
from neqsim import jneqsim
# ... test each pattern ...

# Verify method names exist in the source
# Use grep_search or file_search to confirm
```

### Step 4: Register the Skill

Two registrations are required:

**1. Add to the skills README** (`.github/skills/README.md`):

Add a row to the Skill Index table:

```markdown
| `neqsim-my-topic` | Description of what the skill covers | agent1, agent2 |
```

**2. Add to copilot-instructions.md** (`.github/copilot-instructions.md`):

Add a `<skill>` entry under the `<skills>` section:

```xml
<skill>
<name>neqsim-my-topic</name>
<description>Description. USE WHEN: trigger conditions.</description>
<file>.github/skills/neqsim-my-topic/SKILL.md</file>
</skill>
```

### Step 5: Submit a PR

One skill per PR. Include the skill file, the two registrations, and any supporting test files.

### What Makes a Good Core Skill

A core skill should be:

- **Specific** — "Use SRK for dry gas above -30°C; switch to CPA when water exceeds 100 ppm" is better than "Use the appropriate EOS"
- **Operational** — Code templates that agents can copy-paste, not theoretical explanations
- **Error-aware** — Catalogue of known failure modes with recovery strategies
- **Verified** — Every code pattern tested against NeqSim's actual API
- **Referencing NeqSim internals** — If it doesn't reference NeqSim classes or methods, it's a community skill

---

## Creating Community Skills

Community skills are contributed by domain experts and hosted outside the core
NeqSim source tree. They are listed in the `community-skills.yaml` catalog for
discoverability and can be installed by any user. The recommended shared public
collection is
[equinor/neqsim-community-skills](https://github.com/equinor/neqsim-community-skills),
which is intended for reusable, public, non-confidential skills.

### When to Create a Community Skill (vs. Core)

Create a **community** skill when:

- The skill covers a public, reusable workflow that does not need to live in core
- It uses synthetic, public, or open reference data only
- It gives educational screening guidance, validation helpers, checklists, examples, or agent workflow patterns
- It orchestrates existing NeqSim capabilities without depending on internal Java package details
- It is documented with clear limitations and tested examples when runnable code is included

Do **not** put proprietary methods, plant data, private tag names, internal URLs,
company standards, customer/project identifiers, or project-specific design bases
in the public community repository. Put that material in a private skill catalog
instead.

### Step 1: Create the Skill in Your Repository

Create a GitHub repository for your skill (or add it to an existing repo). The skill needs a `SKILL.md` file following the standard format.

**Single-skill repo (simplest):**
```
neqsim-my-topic/
├── SKILL.md          # required — the skill content
├── CHANGELOG.md      # recommended — version history
└── README.md         # optional — repo landing page
```

**Multi-skill repo (one repo, several skills):**
```
your-repo/
└── skills/
    ├── neqsim-skill-a/
    │   └── SKILL.md
    └── neqsim-skill-b/
        └── SKILL.md
```

The `path` field in the catalog entry tells the installer where to find `SKILL.md`.
For a single-skill repo it defaults to `SKILL.md` (the repo root). For a multi-skill
repo, set it explicitly (e.g., `path: "skills/neqsim-skill-a/SKILL.md"`).

For a public multi-skill GitHub repo, the NeqSim catalog can also list the repo once
under `repositories:`. The CLI searches the online repository, first reading its
remote `community-skills.yaml` and then falling back to scanning for matching
`SKILL.md` files:

```yaml
repositories:
  - repo: "equinor/neqsim-community-skills"
    catalog_path: "community-skills.yaml"
    skill_path_glob: "skills/**/SKILL.md"
```

This keeps the NeqSim catalog small while allowing new skills in the external repo
to appear in `neqsim skill list` after the external repo updates its own catalog.

The skill name must start with `neqsim-`. Use the anatomy template shown above, including the `version` and `requires` fields in the YAML frontmatter.

#### SKILL.md Requirements Checklist

Before publishing, verify your `SKILL.md` meets these requirements:

| Requirement | Details |
|-------------|---------|
| **File name** | Must be `SKILL.md` (exact case) |
| **Skill name** | Must start with `neqsim-` (e.g., `neqsim-teg-dehydration`) |
| **YAML frontmatter** | Must include `name`, `version`, `description`, `last_verified` |
| **`USE WHEN` clause** | The `description` field must contain a `USE WHEN:` trigger so agents know when to load it |
| **Version** | Semver format: `"1.0.0"` |
| **When to Use section** | Bullet list of explicit trigger conditions |
| **Code Patterns section** | At least one working code example (Java 8 compatible if Java) |
| **Common Mistakes section** | Table with Symptom / Cause / Fix columns |
| **Java 8 compatible** | No `var`, `List.of()`, `String.repeat()`, text blocks, etc. in Java code |
| **Tested** | All code patterns verified against NeqSim's actual API |
| **No duplication** | Does not duplicate an existing core skill |
| **`requires` block** (if needed) | Declare Python packages, env vars, or network access your patterns need |

**Tip:** You can use the scaffolder to create the initial template locally, then copy it to your repo:

```bash
neqsim new-skill "neqsim-my-topic" --description "My skill description"
# Creates .github/skills/neqsim-my-topic/SKILL.md (local template)
# Copy the generated SKILL.md to your external repo
```

### Step 2: Publish to the Community Catalog

There are two ways to add your skill to the catalog:

**Option A: Automated (recommended)** — Use `neqsim skill publish`:

```bash
neqsim skill publish your-username/your-repo
# The tool will:
#   1. Validate that SKILL.md exists in your repo on GitHub
#   2. Extract description and auto-detect tags from content
#   3. Generate the catalog entry and show it for confirmation
#   4. Append the entry to community-skills.yaml
#   5. Create a Git branch and draft PR via GitHub CLI (if `gh` is installed)
```

If GitHub CLI (`gh`) is not installed, the tool saves the catalog entry and prints manual PR instructions:

```bash
git checkout -b community-skill/neqsim-my-topic
git add community-skills.yaml
git commit -m "Add community skill: neqsim-my-topic"
git push -u origin community-skill/neqsim-my-topic
gh pr create --draft --title "Add community skill: neqsim-my-topic"
```

**Option B: Manual** — Edit `community-skills.yaml` directly and submit a PR:

```yaml
skills:
  - name: neqsim-my-topic
    version: "1.0.0"
    description: "What it does and when to use it"
    author: "your-github-username"
    repo: "your-username/your-repo"
    path: "skills/neqsim-my-topic/SKILL.md"
    tags: [keyword1, keyword2]
    min_neqsim_version: "3.7.0"  # optional
```

### Step 3: PR Review and Merge

A NeqSim maintainer will review the PR. They check:

- The skill's `SKILL.md` is reachable at the declared `repo` + `path`
- The description is accurate and includes `USE WHEN` triggers
- Code patterns are Java 8 compatible (if any)
- The skill doesn't duplicate an existing core skill
- Tags are relevant and help discoverability

Once merged, the skill appears in the catalog and anyone can install it.

### Step 4: Users Install with the CLI

Once in the catalog, anyone can install your skill:

```bash
# Browse available community skills
neqsim skill list

# Search by keyword
neqsim skill search "dehydration"

# Install a skill (downloads to ~/.neqsim/skills/)
neqsim skill install neqsim-my-topic

# Install and export for VS Code user prompts discovery
neqsim skill install neqsim-my-topic --target vscode

# Install and export for tool-neutral discovery
neqsim skill install neqsim-my-topic --target generic

# Check that exported agents can see their required skills
neqsim agent doctor --target generic

# Check what's installed
neqsim skill installed

# Get details about a specific skill
neqsim skill info neqsim-my-topic

# Remove a skill
neqsim skill remove neqsim-my-topic
```

Installed community skills are stored at `~/.neqsim/skills/<name>/SKILL.md`.

### Step 5: Make Installed Skills Visible to Agents

Installed community skills at `~/.neqsim/skills/` are the local source of truth. Agent tools discover different folders, so use export targets to create generated copies for a specific tool:

```bash
# Export an already-installed skill to VS Code's user prompts skills folder
neqsim skill export neqsim-my-topic --target vscode

# Export an already-installed skill to ~/.neqsim/export/generic/skills/
neqsim skill export neqsim-my-topic --target generic
```

The generic export writes `~/.neqsim/export/generic/skills/<name>/SKILL.md` plus `~/.neqsim/export/generic/manifest.json`. The same manifest also records generic agent exports when both agents and skills are exported. Use this target for coding agents that can be configured with arbitrary local instruction folders. Use the VS Code target when you want Copilot/VS Code to discover the skill from the user's private prompts folder. Maintainers can choose workspace export explicitly with `--vscode-scope workspace` when a generated `.github/skills` copy is intentional.

Prefer the export commands over manual copying. If a tool cannot read the VS Code
or generic export layouts, point that tool at the installed source folder
`~/.neqsim/skills/<name>/` or copy from there into the tool-specific location it
documents.

## Installing Community and Private Agents

Skills and agents are intentionally separate concepts. A skill is a reusable
capability or knowledge package. An agent is a role/workflow definition that may
depend on several skills. NeqSim installs agents into `~/.neqsim/agents/` so they
do not pollute the core repository, and installation never executes agent code.

Agent packages can use either of these shapes:

```text
single-file-agent/
└── process.agent.md

folder-agent/
├── AGENT.md
├── agent.yaml
├── prompts/
├── workflows/
├── examples/
└── tests/
```

The folder package is preferred for larger workflows because `agent.yaml` can
declare dependencies and compatibility metadata in a tool-neutral way. The
current NeqSim agent manifest schema is `1.0`:

```yaml
agent_manifest_schema_version: "1.0"
name: tie-in-screening-agent
description: Early-stage tie-in screening using NeqSim and community skills
version: "0.1.0"
required_skills:
  - neqsim-flow-assurance
  - neqsim-standards-lookup
supported_domains:
  - flow-assurance
  - process
inputs:
  - feed_composition
  - operating_conditions
outputs:
  - results_json
  - report
requires_mcp_tools:
  - runProcess
human_review_required: true
trust_level: community  # core | community | private | experimental
```

### Agent CLI Commands

```bash
# Browse available community and private agents
neqsim agent list

# Search by name, tag, description, or required skill
neqsim agent search "tie-in"

# Inspect catalog metadata
neqsim agent info neqsim-example-agent

# Install an agent definition to ~/.neqsim/agents/
neqsim agent install neqsim-example-agent

# Install and export to VS Code's user prompts folder
neqsim agent install neqsim-example-agent --target vscode

# Install and export to ~/.neqsim/export/generic/agents/
neqsim agent install neqsim-example-agent --target generic

# Agent install also installs and exports required skills when they exist in skill catalogs
neqsim agent doctor --target generic

# Export an already-installed agent later
neqsim agent export neqsim-example-agent --target generic

# Install every agent in the catalog at once
neqsim agent install --all

# Optionally install missing required skills that are present in skill catalogs
neqsim agent install neqsim-example-agent --install-missing-skills

# Validate an installed agent or local folder
neqsim agent validate neqsim-example-agent
neqsim agent validate ./agents/tie-in-screening-agent

# Show the supported agent.yaml schema
neqsim agent schema

# Remove an installed agent
neqsim agent remove neqsim-example-agent
```

The public catalog lives in `community-agents.yaml`. Private/internal agents use
the same pattern as private skills:

```bash
neqsim agent private-init
# Edit ~/.neqsim/private-agents.yaml
neqsim agent list --private
neqsim agent install company-tie-in-screening-agent
```

Agent catalog entries support local folders, private GitHub repositories,
network shares, and direct internal URLs. For private GitHub repositories,
prefer browser SSO through `gh auth login --web` or Git Credential Manager;
`GITHUB_TOKEN` remains a non-interactive fallback for CI or service contexts.
Direct internal URLs can use `PRIVATE_AGENT_TOKEN` when the URL endpoint cannot
use the normal corporate browser or git credential flow.

### Making Installed Agents Visible to Tools

Installed agents are not automatically run by NeqSim. Point your AI tool at the
installed folder, or export a generated copy to the tool-specific layout. For VS
Code Copilot custom agents, the default NeqSim export target is the user's
private prompts folder, not the Git-tracked workspace:

```bash
neqsim agent export neqsim-example-agent --target vscode
neqsim agent doctor --target vscode
```

For tool-neutral agents, use the generic target and point the tool at the
generated manifest:

```bash
neqsim agent export neqsim-example-agent --target generic
neqsim agent doctor --target generic
```

This keeps the trust boundary explicit:

```text
skill install = install reusable capability
agent install = install workflow/role definition
agent run = explicit action in the AI tool that owns execution
```

### Complete Example: Contributing a TEG Dehydration Skill

Here is a complete walkthrough of contributing a community skill:

```bash
# 1. Create a repo on GitHub: your-username/neqsim-teg-dehydration
#    Add your SKILL.md with TEG-specific patterns

# 2. Publish to the NeqSim community catalog
neqsim skill publish your-username/neqsim-teg-dehydration

#    Output:
#    Validating your-username/neqsim-teg-dehydration / SKILL.md ...
#    [OK] Found SKILL.md (2847 bytes)
#
#    Generated catalog entry:
#      - name: neqsim-teg-dehydration
#        description: "TEG dehydration sizing and design per GPA 8100..."
#        author: "your-username"
#        repo: "your-username/neqsim-teg-dehydration"
#        path: "SKILL.md"
#        tags: [flow-assurance, process]
#
#    Add to catalog and create PR? [y/N] y
#    [OK] Added entry to community-skills.yaml
#    [OK] Draft PR created: https://github.com/equinor/neqsim/pull/1234

# 3. Wait for maintainer review and merge

# 4. Other users install it:
neqsim skill install neqsim-teg-dehydration

# 5. Export for your AI tool:
neqsim skill export neqsim-teg-dehydration --target vscode
neqsim skill export neqsim-teg-dehydration --target generic
```

### Updating a Published Community Skill

When you update your skill in your repo (e.g., bump `version` to `1.1.0`):

1. Update the `SKILL.md` in your repository (add patterns, fix mistakes, bump version)
2. Users re-install with `--force` to get the latest:
   ```bash
   neqsim skill install neqsim-my-topic --force
   ```

---

## Creating Local Private Skills

Local skills encode knowledge that is specific to your organisation and should **never** be committed to a public repository. This is the most important skill type for production engineering workflows because it connects the generic NeqSim capabilities to your specific plant, your specific data systems, and your specific corporate standards.

> **Important: local skills are not auto-discovered by every tool.** Install shared local, community, and private skills into `~/.neqsim/skills/`, then export generated copies with `neqsim skill export <name> --target vscode` or `neqsim skill export <name> --target generic` for the tool you want to use. VS Code user prompt files remain useful for one-off personal prompts, but they are no longer the preferred NeqSim skill install location.

### How Local Skills Become Visible to Agents

There are several ways to make local skills available. They are listed from most recommended to least:

| Method | Auto-loads | Works across workspaces | Setup effort |
|--------|-----------|------------------------|-------------|
| **NeqSim install + export targets** (recommended) | Yes, when the target tool reads the export | Yes for generic and default VS Code user exports; workspace-scoped only with explicit `--vscode-scope workspace` | Low |
| **VS Code User Prompt Files** | Yes, when referenced with `#` | Yes | Low |
| **Workspace `.github/copilot-instructions.md`** | Yes, always in context | No, per-workspace only | Medium |
| **Manual prompt reference** | No, must ask each time | Yes | None |

---

### Method 1: NeqSim Install and Export Targets (Recommended)

Use this method for reusable skills that should behave like installed community or enterprise skills. The canonical copy stays under `~/.neqsim/skills`; exports are generated compatibility copies.

```powershell
# Install from a public, private, local, GitHub, git, or URL catalog entry
neqsim skill install neqsim-company-stid

# Make the installed skill visible to VS Code from the user prompts skills folder
neqsim skill export neqsim-company-stid --target vscode

# Make it visible to tool-neutral coding agents
neqsim skill export neqsim-company-stid --target generic
```

For agents, the equivalent commands install into `~/.neqsim/agents` and export to VS Code user prompts or the generic agent layout:

```powershell
neqsim agent install company-study-review-agent --install-missing-skills
neqsim agent export company-study-review-agent --target vscode
neqsim agent export company-study-review-agent --target generic
neqsim agent doctor --target generic
```

The generic target writes `~/.neqsim/export/generic/manifest.json`, which records exported agents and skills for coding agents that can consume arbitrary local instruction folders.

---

### Method 2: VS Code User Prompt Files

VS Code Copilot has a **user-level prompts folder** that works across all workspaces. Any `.md` file placed there becomes a reusable prompt that you can invoke by name in Copilot Chat. This is useful for personal, VS Code-only prompts because:

- Files survive across all workspaces and conversations
- You invoke them by name with `#` — the agent reads the full file
- They are never committed to any repository
- They work with Copilot Chat, Copilot Edits, and agent mode

#### Step 1: Create the user prompts folder

The folder location depends on your OS:

| OS | Path |
|----|------|
| **Windows** | `%APPDATA%\Code\User\prompts\` |
| **macOS** | `~/Library/Application Support/Code/User/prompts/` |
| **Linux** | `~/.config/Code/User/prompts/` |

On Windows, the full path is typically:

```
C:\Users\<YourName>\AppData\Roaming\Code\User\prompts\
```

Create it if it doesn't exist:

```powershell
# Windows PowerShell
New-Item -ItemType Directory -Path "$env:APPDATA\Code\User\prompts" -Force
```

```bash
# macOS / Linux
mkdir -p ~/.config/Code/User/prompts  # Linux
mkdir -p ~/Library/Application\ Support/Code/User/prompts  # macOS
```

#### Step 2: Enable prompt files in VS Code settings

Open VS Code settings (Ctrl+, or Cmd+,) and ensure this setting is enabled:

```json
{
  "chat.promptFiles": true
}
```

Or search for "prompt files" in the Settings UI and check the box.

#### Step 3: Create your skill as a prompt file

Write a `.md` file in the prompts folder. Use the same SKILL.md format:

```powershell
# Windows — create a local STID skill
New-Item -ItemType File -Path "$env:APPDATA\Code\User\prompts\neqsim-equinor-stid.md"
```

Then edit the file with your skill content (see the full examples below).

#### Step 4: Use the skill in Copilot Chat

In any VS Code workspace, type `#` in Copilot Chat to see available prompt files, then select your skill:

```
#neqsim-equinor-stid

Download compressor curves for 35-KA001A on Grane platform
```

Copilot reads the full file content and uses it as context for the task. The agent gets all your patterns, tag conventions, and common mistakes — just like a core skill.

You can also reference multiple skills in one prompt:

```
#neqsim-equinor-stid #neqsim-equinor-plant-data

Compare the rated compressor capacity from STID datasheets
against actual PI historian data for the last 30 days
```

#### Example: User prompts folder structure

```
C:\Users\ESOL\AppData\Roaming\Code\User\prompts\
├── neqsim-equinor-stid.md          # STID document retrieval
├── neqsim-equinor-plant-data.md    # PI historian access
├── neqsim-equinor-standards.md     # Company TR requirements
└── neqsim-my-platform-tags.md      # Tag mappings for a specific platform
```

---

### Method 3: Workspace-Level copilot-instructions.md

If you want a local skill to be loaded automatically for every conversation in a specific workspace (without needing to type `#`), add it to the workspace's `.github/copilot-instructions.md`. This is useful for project-specific skills that every team member needs.

> **Warning:** `.github/copilot-instructions.md` is typically committed to Git. If you add private skill references here, either add the skill file path to `.gitignore` or use a separate non-committed instructions file.

Add a `<skill>` entry pointing to your local file:

```xml
<skill>
<name>neqsim-my-local-skill</name>
<description>My local skill for company-specific workflows. USE WHEN: task involves our plant data.</description>
<file>C:\Users\ESOL\.neqsim\skills\neqsim-my-local-skill\SKILL.md</file>
</skill>
```

The agent will see this skill in its available skills list and load it when the description matches the task.

---

### Method 4: Manual Prompt Reference

The simplest (but least convenient) approach — just tell the agent to read your skill file in the chat:

```
Read the file at C:\Users\ESOL\.neqsim\skills\neqsim-equinor-stid\SKILL.md
and use it to download compressor curves for 35-KA001A
```

This works but requires you to remember and type the path every time.

---

### Where to Store Skill Files

You can store the actual skill files anywhere on your machine. Two common locations:

**Option A: `~/.neqsim/skills/`** — co-located with community skills installed via `neqsim skill install`:

```
~/.neqsim/skills/
├── installed.json                    # manifest of installed community skills
├── neqsim-my-stid-retrieval/
│   └── SKILL.md                     # your private STID skill
├── neqsim-my-plant-data/
│   └── SKILL.md                     # your private plant data skill
└── neqsim-my-company-standards/
    └── SKILL.md                     # your company's engineering standards
```

**Option B: VS Code user prompts folder** — useful for ad hoc personal prompts that you invoke manually with `#`:

```
%APPDATA%\Code\User\prompts\
├── neqsim-equinor-stid.md           # skill = prompt file (no subfolder needed)
├── neqsim-equinor-plant-data.md
└── neqsim-equinor-standards.md
```

Option B is simple because the files are both the skill content and the prompt reference, but it is VS Code-specific. Prefer Option A plus export targets for reusable NeqSim skills and agents that should work across tools.

---

## Private Skill Catalog

For teams that need to share private skills across an organisation — without publishing them to the public community catalog — the CLI supports a **private skill catalog** stored at `~/.neqsim/private-skills.yaml`.

This works exactly like `community-skills.yaml` but:

- Lives on each user's machine (never committed to public repos)
- Supports **local file paths**, **network shares**, **private GitHub repos**, and **direct URLs** (internal Git, Azure DevOps)
- Installed skills appear alongside community skills in the CLI

### Setup

```bash
# 1. Create the catalog template
neqsim skill private-init

# 2. Edit ~/.neqsim/private-skills.yaml — uncomment and fill in entries
# 3. Verify
neqsim skill list --private

# 4. Install a private skill (same command as community skills)
neqsim skill install neqsim-company-stid
```

### Catalog Format

The private catalog supports four source types:

```yaml
catalog_version: "1.0"
organisation: "equinor"

skills:
  # Local file or network share
  - name: neqsim-equinor-stid
    description: "STID document retrieval for Equinor installations"
    author: "process-engineering"
    source: local
    path: "C:/Users/me/.neqsim/skills/neqsim-equinor-stid/SKILL.md"
    tags: [stid, documents, internal]

  # Network share (UNC path)
  - name: neqsim-company-standards
    description: "Company TR requirements for equipment design"
    author: "engineering-team"
    source: local
    path: "//fileserver/shared/neqsim-skills/company-standards/SKILL.md"
    tags: [standards, tr, internal]

  # Private GitHub repo (prefer gh auth login --web or Git Credential Manager;
  # GITHUB_TOKEN is a CI/non-interactive fallback)
  - name: neqsim-plant-data-mapping
    description: "PI tag mappings for our platforms"
    author: "data-team"
    source: github
    repo: "equinor/neqsim-internal-skills"
    path: "skills/neqsim-plant-data-mapping/SKILL.md"
    tags: [pi, historian, plant-data]

  # Direct URL (Azure DevOps, internal Git, etc.)
  - name: neqsim-internal-workflow
    description: "Internal simulation workflow for project X"
    author: "project-team"
    source: url
    url: "https://dev.azure.com/org/project/_git/repo?path=/SKILL.md"
    tags: [workflow, internal]
```

### Authentication for Private Sources

| Source | Auth mechanism | How to set |
|--------|---------------|-----------|
| `local` | File system permissions | No token needed |
| `github` (private repo) | Preferred: browser SSO via GitHub CLI or Git Credential Manager. Fallback: `GITHUB_TOKEN` for CI/non-interactive runs | `gh auth login --web`, or configure Git Credential Manager; fallback `$env:GITHUB_TOKEN = "..."` |
| `git` | Git Credential Manager or another configured git SSO broker | Run the normal enterprise git login once; NeqSim reuses git credentials |
| `url` | Preferred: normal corporate browser/session or network access. Fallback: `PRIVATE_SKILL_TOKEN`/`PRIVATE_AGENT_TOKEN` | Use the internal URL directly when accessible; fallback `$env:PRIVATE_SKILL_TOKEN = "Bearer ..."` |

### Distributing the Catalog to Your Team

The catalog file itself can be shared via:

1. **Network share** — put `private-skills.yaml` on a shared drive and have each team member copy it to `~/.neqsim/`
2. **Internal Git repo** — store the catalog in a private repo and clone/sync periodically
3. **Onboarding script** — include in your team's setup script:

```powershell
# Example onboarding snippet
Copy-Item "\\fileserver\neqsim-config\private-skills.yaml" `
          "$env:USERPROFILE\.neqsim\private-skills.yaml"
neqsim skill list --private
```

### How It Relates to the Three Skill Types

```
┌─────────────────────────────────────────────────────────┐
│  Core Skills (.github/skills/)                          │
│  └─ Auto-loaded by agents, version-controlled           │
├─────────────────────────────────────────────────────────┤
│  Community Catalog (community-skills.yaml)              │
│  └─ Public, anyone can contribute via PR                │
├─────────────────────────────────────────────────────────┤
│  Private Catalog (~/.neqsim/private-skills.yaml)   NEW  │
│  └─ Company-internal, shared via network/private repo   │
├─────────────────────────────────────────────────────────┤
│  Local Skills (VS Code user prompts / manual files)     │
│  └─ Personal, only on your machine                      │
└─────────────────────────────────────────────────────────┘
```

All installed skills (community and private) go to `~/.neqsim/skills/<name>/SKILL.md` and are used the same way.

---

## Example: Local Skill for STID Document Retrieval

This example shows a local private skill for retrieving engineering documents from Equinor's STID system. This skill is private because it contains company-specific server URLs, authentication patterns, and tag naming conventions.

### `~/.neqsim/skills/neqsim-equinor-stid/SKILL.md`

```markdown
---
name: neqsim-equinor-stid
version: "1.0.0"
description: "Retrieves engineering documents from Equinor's STID system for
  use in NeqSim tasks. USE WHEN: a task requires vendor datasheets, compressor
  curves, mechanical drawings, or P&IDs for Equinor-operated installations."
last_verified: "2026-04-19"
requires:
  python_packages: [pymupdf]
  network: [equinor-vpn]
  env: [STID_BASE_URL]
---

# Equinor STID Document Retrieval

Retrieve engineering documents (compressor curves, mechanical drawings, data
sheets) from Equinor's STID (Statoil Technical Information and Documentation)
system for use in NeqSim engineering tasks.

## When to Use This Skill

- When a task references specific equipment tags (e.g., 35-KA001A)
- When vendor performance data is needed (compressor maps, pump curves)
- When mechanical drawings are needed for design verification
- When P&IDs are needed for process topology analysis

## Prerequisites

- VPN access to Equinor's internal network
- `devtools/doc_retrieval_config.yaml` configured (gitignored):

```yaml
backend: stidapi
stidapi:
  base_url: "https://stid.equinor.com/api/v2"
  auth: kerberos
  timeout: 60
  default_installation: "MYINST"
```

## Retrieval Patterns

### Download by Equipment Tag

```bash
# Download documents for specific equipment tags
python devtools/stid_download.py \
    --task-dir task_solve/2026-04-19_my_task \
    --inst GRANE --tags 35-KA001A 35-KA001B

# Download + convert to PNG for AI image analysis
python devtools/stid_download.py \
    --task-dir task_solve/2026-04-19_my_task \
    --inst GRANE --tags 35-KA001A --convert-png
```

### Download by Document Number

```bash
python devtools/stid_download.py \
    --task-dir task_solve/2026-04-19_my_task \
    --inst GRANE --docs E001-AS-P-XB-00001-01
```

### Programmatic Retrieval

```python
from devtools.doc_retriever import retrieve_documents

docs = retrieve_documents(
    tags=['35-KA001A'],
    doc_types=['CE', 'DS', 'AA', 'MD'],
    output_dir='step1_scope_and_research/references/'
)
```

## Document Type Codes

| Code | Type | When Relevant |
|------|------|---------------|
| CE | Performance Curves | Compressor, pump, turbine analysis |
| DS | Data Sheet | Any equipment analysis |
| AA | General Arrangement | Physical layout, sizing |
| MD | Mechanical Drawing | Detailed dimensions, nozzles |
| PI | P&ID | Process topology |
| IN | Instrument Data Sheet | Control system design |

## Tag Naming Convention (Equinor)

Equipment tags follow the pattern: `XX-YYNNNZ`

- `XX` = system number (e.g., 35 = gas compression)
- `YY` = equipment type code (e.g., KA = compressor)
- `NNN` = sequence number
- `Z` = train identifier (A, B, C...)

## Common Mistakes

| Symptom | Cause | Fix |
|---------|-------|-----|
| No documents returned | Wrong installation code | Check `--inst` matches the platform (GRANE, TROLL, etc.) |
| Timeout errors | VPN not connected | Connect to Equinor VPN first |
| Documents saved outside task | Wrong output path | Always use `--task-dir` flag |
| Irrelevant documents cluttering analysis | No type filtering | Use `--doc-types CE,DS` to filter |
```

---

## Example: Local Skill for Plant Data from PI Historian

This example shows a local private skill for reading real-time and historical data from Equinor's OSIsoft PI historian. This is private because it contains server names, tag naming conventions, and authentication details.

### `~/.neqsim/skills/neqsim-equinor-plant-data/SKILL.md`

```markdown
---
name: neqsim-equinor-plant-data
version: "1.0.0"
description: "Reads plant process data from Equinor's PI historian for
  use in NeqSim digital twin and model validation tasks. USE WHEN: a task
  needs real operating data — temperatures, pressures, flow rates,
  compositions — from an Equinor-operated facility."
last_verified: "2026-04-19"
requires:
  python_packages: [tagreader, pandas]
  network: [equinor-vpn]
---

# Equinor Plant Data via PI Historian

Read process data from Equinor's OSIsoft PI historian using tagreader-python.

## When to Use This Skill

- When validating a NeqSim model against real plant data
- When building a digital twin loop
- When extracting operating conditions for design basis
- When comparing simulated vs measured values

## Prerequisites

- On Equinor network (or VPN)
- `pip install tagreader`
- Kerberos authentication (default on Equinor machines)

## Connection Pattern

```python
import tagreader

# List available PI sources
sources = tagreader.list_sources("piwebapi")
print(sources)  # e.g., ['PINO', 'PIMNT', 'PIK', ...]

# Connect to a specific PI server
c = tagreader.IMSClient("PINO", "piwebapi")
c.connect()
```

## Tag Mapping Pattern

Map NeqSim simulation variables to PI historian tags:

```python
TAG_MAP = {
    # NeqSim variable name  :  PI tag name
    "hp_sep.pressure"       : "35-PT-0101.PV",
    "hp_sep.temperature"    : "35-TT-0101.PV",
    "hp_sep.gas_flow"       : "35-FT-0101.PV",
    "hp_sep.oil_level"      : "35-LT-0101.PV",
    "compressor.suction_p"  : "35-PT-0201.PV",
    "compressor.discharge_p": "35-PT-0202.PV",
    "compressor.speed"      : "35-ST-0201.PV",
    "export.gas_flow"       : "36-FI-0001.PV",
}
```

## Reading Data

```python
tags = list(TAG_MAP.values())
start = "01.06.2025 06:00:00"
end   = "01.06.2025 18:00:00"

# Interpolated data every 5 minutes
df = c.read(tags, start, end, 300)

# MANDATORY: Save to CSV in the task folder
import pathlib
REFS_DIR = TASK_DIR / "step1_scope_and_research" / "references"
REFS_DIR.mkdir(parents=True, exist_ok=True)
df.to_csv(str(REFS_DIR / "plant_data_raw.csv"))
```

## Digital Twin Comparison Pattern

```python
import pandas as pd

# Run NeqSim simulation
process.run()

# Compare with plant data
sim_results = {
    "hp_sep.pressure": sep.getPressure("bara"),
    "hp_sep.temperature": sep.getTemperature("C"),
    "compressor.discharge_p": comp.getOutletPressure("bara"),
}

comparison = []
for var_name, sim_value in sim_results.items():
    tag = TAG_MAP[var_name]
    plant_value = df[tag].mean()
    deviation_pct = abs(sim_value - plant_value) / plant_value * 100
    comparison.append({
        "variable": var_name,
        "simulated": round(sim_value, 2),
        "measured": round(plant_value, 2),
        "deviation_%": round(deviation_pct, 1),
    })

df_comp = pd.DataFrame(comparison)
print(df_comp.to_string(index=False))
```

## Common Mistakes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `ConnectionError` | Not on Equinor network | Connect VPN or use `read_type=SNAPSHOT` for quick test |
| NaN values in DataFrame | Tag doesn't exist or no data in range | Verify tag with `c.search("TAG*")` |
| Timezone mismatch | PI returns UTC, analysis assumes Oslo | Set `tz="Europe/Oslo"` in client constructor |
| Stale data in CSV | Forgot to re-read after time range change | Delete old CSV, re-run read |
| Plant data not in task folder | Saved to workspace root | Always save to `REFS_DIR` inside the task |
```

---

## Skill Lifecycle and Maintenance

### When to Update a Skill

Update a skill when:

- NeqSim's API changes (new methods, renamed classes, changed signatures)
- New failure modes are discovered
- Better patterns are found through operational experience
- Industry standards are updated

### Version Tracking

Skills use [semver](https://semver.org/) versioning plus a verification date:

```yaml
---
name: neqsim-my-skill
version: "1.2.0"
last_verified: "2026-04-19"
---
```

| Version bump | When |
|-------------|------|
| **Major** (2.0.0) | Breaking changes: removed patterns, changed prerequisites, incompatible with previous NeqSim versions |
| **Minor** (1.3.0) | New patterns, new sections, expanded coverage |
| **Patch** (1.2.1) | Fix typos, correct code errors, update verification date |

Update `version` for every change and `last_verified` whenever you re-verify patterns against NeqSim's API.

The `neqsim new-skill` scaffolder also creates a `CHANGELOG.md` alongside `SKILL.md` to track version history:

```markdown
# neqsim-my-skill Changelog

## 1.2.0 (2026-04-19)

- Added Python pattern for batch processing
- New common mistake: missing initProperties() after PSflash

## 1.1.0 (2026-03-15)

- Added valve sizing pattern per IEC 60534

## 1.0.0 (2026-02-01)

- Initial release
```

### From Local to Community to Core

Skills can be promoted through the tiers:

1. **Start local** — Write a private skill for your workflow
2. **Share as community** — Once proven, publish to your repo and add to `community-skills.yaml`
3. **Promote to core** — If the skill references NeqSim internals and is used by multiple agents, propose merging into `.github/skills/`

---

## Summary

| Aspect | Core Skills | Community Skills | Private Skills | Community/Private Agents |
|--------|-------------|-----------------|----------------|--------------------------|
| **Location** | `.github/skills/` | External repos | `~/.neqsim/private-skills.yaml`, `~/.neqsim/skills/` | External repos or `~/.neqsim/private-agents.yaml`, installed to `~/.neqsim/agents/` |
| **Visibility** | All users | Catalog browsers | You or your team | Catalog browsers, you, or your team |
| **Creation** | `neqsim new-skill` + PR | Your repo + catalog entry | YAML entry or local file | Agent markdown/folder + `community-agents.yaml` or private catalog |
| **Installation** | Automatic (in repo) | `neqsim skill install` to `~/.neqsim/skills` | `neqsim skill install` to `~/.neqsim/skills` | `neqsim agent install` to `~/.neqsim/agents` |
| **Tool export** | Already workspace-visible; some folders may be generated compatibility exports | `neqsim skill export <name> --target vscode/generic` | `neqsim skill export <name> --target vscode/generic` | `neqsim agent export <name> --target vscode/generic` |
| **Source types** | Git repo | Public GitHub | Local / network / private GitHub / URL | Local / network / GitHub / URL |
| **Contains private data** | Never | Never | Yes (company-internal) | Yes only in private catalogs |
| **Review process** | PR review by maintainers | Self-published | Team-managed | PR review for public catalog; team-managed for private |
| **Examples** | `neqsim-api-patterns` | Custom TEG sizing | Company STID, PI tags, TR standards | Tie-in screening agent, internal review agent |

The skill and agent system makes it possible for any engineer to package domain expertise and repeatable workflows — from thermodynamic model selection rules to company-specific review processes — in a format AI tools can use safely. Public knowledge is shared broadly while private knowledge stays private.

---

## Comparison with OpenClaw

NeqSim's skill system was partly inspired by [OpenClaw](https://github.com/openclaw/openclaw) (360k+ GitHub stars), the largest open-source AI assistant. OpenClaw has a mature skill ecosystem with [ClawHub](https://github.com/openclaw/clawhub) — a public registry hosting 18,000+ community-published skills with vector search, versioning, and security analysis.

Both systems share the same core idea: install structured, versioned knowledge or workflow packages into layered locations. For NeqSim, `SKILL.md` packages knowledge and `AGENT.md` or `.agent.md` packages workflow definitions. The key differences and lessons learned are:

### What NeqSim adopted from OpenClaw

| Feature | OpenClaw | NeqSim adoption |
|---------|----------|------------------|
| **Semver versioning** | `version: "1.2.0"` in frontmatter | Added to all skills — major/minor/patch with CHANGELOG.md |
| **Dependency declarations** | `metadata.openclaw.requires` (env, bins, config) | Added `requires` block: python_packages, java_packages, env, network |
| **CLI workflow** | `clawhub install/publish/search/sync` | `neqsim skill` (install/list/search/remove/publish) |
| **Agent packages** | Role/workflow definitions can depend on skills | `neqsim agent` (install/list/search/remove/validate) with `required_skills` checks |

### Where the systems differ

| Aspect | OpenClaw | NeqSim |
|--------|----------|--------|
| **Scale** | 18,000+ skills (open marketplace) | ~30 curated core + community catalog |
| **Content** | Mostly API wrappers (Todoist, Slack, Gmail, etc.) | Deep engineering domain knowledge |
| **Quality** | Unfiltered, variable quality | Expert-verified, PR-reviewed core skills |
| **Search** | Vector search (OpenAI embeddings) | Keyword search in YAML catalog |
| **Security** | Automated analysis of declared vs actual behavior | Manual PR review by maintainers |
| **Agent integration** | Skills exported into a tool-visible layout and described by a manifest | Core skills are workspace-visible; installed skills and agents live under `~/.neqsim/` and are exported to VS Code or generic folders for explicit tool use |
| **Tiers** | Single public tier + local workspace | Three tiers (core/community/local) with different trust levels |
| **License** | MIT-0 (no attribution) | Per-skill, follows NeqSim's Apache 2.0 for core |

### What NeqSim does better

1. **Domain curation** — 30 expert-verified skills beat 18,000 unfiltered ones for engineering reliability
2. **Three-tier architecture** — Clear separation between core/community/local with different trust levels
3. **Trigger-based loading** — `USE WHEN` clauses enable automatic skill selection based on task analysis, not manual installation
4. **Knowledge-first** — Skills encode physics, standards, and engineering expertise rather than wrapping third-party APIs

### Potential future improvements (inspired by OpenClaw)

- **Vector search** for the community catalog (find skills by meaning, not just keywords)
- **Automated security analysis** of contributed skills (check that declared `requires` matches actual behavior)
- ~~**`publish` command**~~ — already implemented via `neqsim skill publish`
- **`sync` command** to auto-detect changed skills and publish updates
- **Workflow composition** (OpenClaw has [lobster](https://github.com/openclaw/lobster) for chaining skills into pipelines)

## Related Documentation

- [AI Agents Reference](ai_agents_reference.md) — Complete catalog of all agents and skills
- [AI Agentic Programming Introduction](ai_agentic_programming_intro.md) — Overview of the agentic system
- [AI Workflow Examples](ai_workflow_examples.md) — End-to-end worked examples
