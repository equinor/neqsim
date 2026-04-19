---
title: "Skills Guide: Creating, Using, and Managing Skills in NeqSim"
description: "Comprehensive guide to the three types of skills in NeqSim's agentic engineering system: core program skills, user-contributed community skills, and local private skills. Covers skill anatomy, creation workflow, installation, and real-world examples including STID document retrieval and plant data integration."
---

# Skills Guide: Creating, Using, and Managing Skills in NeqSim

> **TL;DR — Quick Start by Skill Type**
>
> | I want to… | Do this |
> |------------|---------|
> | **Use an existing core skill** | Nothing — agents load them automatically from `.github/skills/` |
> | **Create a new core skill** | `neqsim new-skill "name"` → edit SKILL.md → register in README + copilot-instructions → PR |
> | **Install a community skill** | `neqsim skill install neqsim-topic` → copy to VS Code prompts → use with `#neqsim-topic` |
> | **Publish my skill to the community** | Host SKILL.md in a GitHub repo → `neqsim skill publish user/repo` → PR gets reviewed |
> | **Create a private/local skill** | Write a `.md` file in `%APPDATA%\Code\User\prompts\` → use with `#filename` in Chat |
> | **Share private skills with my team** | `neqsim skill private-init` → edit `~/.neqsim/private-skills.yaml` → distribute to team |
>
> Read on for the full details of each workflow.

Skills are the knowledge layer of NeqSim's agentic engineering system. They are structured markdown documents that encode domain-specific expertise — API patterns, code templates, decision rules, reference data, common error patterns, and recovery strategies — in a format that AI agents can read, interpret, and apply to engineering tasks.

This guide covers the three types of skills, how to create each type, and how they work together.

## The Three Types of Skills

NeqSim's skill system is organised into three tiers, each with a different scope, audience, and lifecycle:

| Type | Location | Scope | Who Creates | Version Controlled |
|------|----------|-------|-------------|-------------------|
| **Core Program Skills** | `.github/skills/` in the NeqSim repo | All users, all agents | NeqSim maintainers | Yes (Git, PR review) |
| **Community Skills** | External repos, listed in `community-skills.yaml` | Anyone who installs them | Domain experts, contributors | Yes (author's repo) |
| **Local Private Skills** | `~/.neqsim/skills/` on your machine | Only you | You | Optional (your choice) |

### Core Program Skills

These are the 28+ skills shipped with NeqSim. They cover NeqSim's own API (`neqsim-api-patterns`), Java 8 rules (`neqsim-java8-rules`), and domain expertise that references NeqSim internals (flow assurance, CCS, distillation, platform modeling, etc.). Agents load these automatically when relevant.

**Examples:** `neqsim-api-patterns`, `neqsim-flow-assurance`, `neqsim-troubleshooting`, `neqsim-platform-modeling`

### Community Skills

Skills contributed by the community but hosted outside the core repo. They are listed in the machine-readable `community-skills.yaml` catalog and installed via the CLI tool. Community skills cover workflows and domains that don't require deep NeqSim internal knowledge.

**Examples:** company-specific TEG sizing procedures, regional regulatory workflows, alternative EOS validation approaches

### Local Private Skills

Skills stored on your own machine at `~/.neqsim/skills/`. These encode private or company-specific knowledge that should never be committed to a public repository — plant historian tag mappings, internal STID retrieval procedures, company standards, proprietary workflows.

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

Community skills are contributed by domain experts and hosted in their own repositories. They are listed in the `community-skills.yaml` catalog for discoverability and can be installed by any user.

### When to Create a Community Skill (vs. Core)

Create a **community** skill when:

- The skill covers a narrow workflow (e.g., "my company's TEG sizing procedure")
- It doesn't reference NeqSim internal class structure
- It orchestrates existing NeqSim capabilities in a specific way
- It's experimental or unverified against NeqSim's latest API

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

# Check what's installed
neqsim skill installed

# Get details about a specific skill
neqsim skill info neqsim-my-topic

# Remove a skill
neqsim skill remove neqsim-my-topic
```

Installed community skills are stored at `~/.neqsim/skills/<name>/SKILL.md`.

### Step 5: Make Installed Skills Visible to Agents

Installed community skills at `~/.neqsim/skills/` are **not** auto-discovered by VS Code Copilot. To use them, apply one of the same methods described in the "Creating Local Private Skills" section below:

1. **Copy to VS Code user prompts folder** (recommended):
   ```powershell
   Copy-Item ~/.neqsim/skills/neqsim-my-topic/SKILL.md "$env:APPDATA\Code\User\prompts\neqsim-my-topic.md"
   ```
   Then reference with `#neqsim-my-topic` in Copilot Chat.

2. **Add to workspace `copilot-instructions.md`** for automatic loading.

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

# 5. Copy to VS Code prompts for agent use:
# (Windows)
Copy-Item ~/.neqsim/skills/neqsim-teg-dehydration/SKILL.md `
    "$env:APPDATA\Code\User\prompts\neqsim-teg-dehydration.md"
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

> **Important: Local skills are not auto-discovered.** Core skills in `.github/skills/` are automatically listed in `copilot-instructions.md` and agents know about them. Local skills require you to make them visible to agents using one of the methods described below. The recommended method is **VS Code User Prompt Files**.

### How Local Skills Become Visible to Agents

There are three ways to make local skills available. They are listed from most recommended to least:

| Method | Auto-loads | Works across workspaces | Setup effort |
|--------|-----------|------------------------|-------------|
| **VS Code User Prompt Files** (recommended) | Yes, when referenced with `#` | Yes | Low |
| **Workspace `.github/copilot-instructions.md`** | Yes, always in context | No, per-workspace only | Medium |
| **Manual prompt reference** | No, must ask each time | Yes | None |

---

### Method 1: VS Code User Prompt Files (Recommended)

VS Code Copilot has a **user-level prompts folder** that works across all workspaces. Any `.md` file placed there becomes a reusable prompt that you can invoke by name in Copilot Chat. This is the best mechanism for local skills because:

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

### Method 2: Workspace-Level copilot-instructions.md

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

### Method 3: Manual Prompt Reference

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

**Option B: VS Code user prompts folder** — the skill files *are* the prompt files (recommended for simplicity):

```
%APPDATA%\Code\User\prompts\
├── neqsim-equinor-stid.md           # skill = prompt file (no subfolder needed)
├── neqsim-equinor-plant-data.md
└── neqsim-equinor-standards.md
```

Option B is simpler because the files are both the skill content and the prompt reference — no separate registration step needed.

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

  # Private GitHub repo (requires GITHUB_TOKEN env var)
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
| `github` (private repo) | `GITHUB_TOKEN` env var | `$env:GITHUB_TOKEN = "ghp_..."` |
| `url` | `PRIVATE_SKILL_TOKEN` env var | `$env:PRIVATE_SKILL_TOKEN = "Bearer ..."` |

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

| Aspect | Core Skills | Community Skills | Private Catalog | Local Skills |
|--------|-------------|-----------------|-----------------|--------------|
| **Location** | `.github/skills/` | External repos | `~/.neqsim/private-skills.yaml` | `~/.neqsim/skills/` or VS Code prompts |
| **Visibility** | All users | Catalog browsers | Your team | Only you |
| **Creation** | `neqsim new-skill` + PR | Your repo + catalog entry | YAML entry | Manual `mkdir` + write |
| **Installation** | Automatic (in repo) | `neqsim skill install` | `neqsim skill install` | Manual |
| **Source types** | Git repo | Public GitHub | Local / network / private GitHub / URL | Local file |
| **Contains private data** | Never | Never | Yes (company-internal) | Yes (personal) |
| **Review process** | PR review by maintainers | Self-published | Team-managed | None |
| **Examples** | `neqsim-api-patterns` | Custom TEG sizing | Company STID, PI tags, TR standards | Personal shortcuts |

The skill system makes it possible for any engineer to package their domain expertise — from thermodynamic model selection rules to company-specific data retrieval procedures — in a format that AI agents can use to solve engineering tasks correctly. The three-tier architecture ensures that public knowledge is shared broadly while private knowledge stays private.

---

## Comparison with OpenClaw

NeqSim's skill system was partly inspired by [OpenClaw](https://github.com/openclaw/openclaw) (360k+ GitHub stars), the largest open-source AI assistant. OpenClaw has a mature skill ecosystem with [ClawHub](https://github.com/openclaw/clawhub) — a public registry hosting 18,000+ community-published skills with vector search, versioning, and security analysis.

Both systems share the same core format: a `SKILL.md` file with optional YAML frontmatter inside a named folder. The key differences and lessons learned are:

### What NeqSim adopted from OpenClaw

| Feature | OpenClaw | NeqSim adoption |
|---------|----------|------------------|
| **Semver versioning** | `version: "1.2.0"` in frontmatter | Added to all skills — major/minor/patch with CHANGELOG.md |
| **Dependency declarations** | `metadata.openclaw.requires` (env, bins, config) | Added `requires` block: python_packages, java_packages, env, network |
| **CLI workflow** | `clawhub install/publish/search/sync` | `neqsim skill` (install/list/search/remove/publish) |

### Where the systems differ

| Aspect | OpenClaw | NeqSim |
|--------|----------|--------|
| **Scale** | 18,000+ skills (open marketplace) | ~30 curated core + community catalog |
| **Content** | Mostly API wrappers (Todoist, Slack, Gmail, etc.) | Deep engineering domain knowledge |
| **Quality** | Unfiltered, variable quality | Expert-verified, PR-reviewed core skills |
| **Search** | Vector search (OpenAI embeddings) | Keyword search in YAML catalog |
| **Security** | Automated analysis of declared vs actual behavior | Manual PR review by maintainers |
| **Agent integration** | Skills installed into workspace, agent discovers them | Skills wired into `copilot-instructions.md` with `USE WHEN` triggers |
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
