---
title: Enterprise Agent and Skill Repositories
description: How to set up company-private NeqSim enterprise agent and skill repositories, publish catalogs, and configure Engineering Harness discovery and installation.
---

# Enterprise Agent and Skill Repositories

This guide explains how a company can set up private NeqSim enterprise repositories and make their agents and skills discoverable to runtimes such as the Engineering Harness.

Use this pattern when the content is useful to a company or project but should not be published in the community repositories because it contains company policy, internal integration wiring, governed workflow requirements, or private validation expectations.

## Repository Model

Create two private repositories per company or organization:

| Repository | Purpose | Catalog |
| --- | --- | --- |
| `<company>-neqsim-enterprise-skills` | Reusable company skill packages and policy overlays | `enterprise-skills.yaml` |
| `<company>-neqsim-enterprise-agents` | Agent orchestration definitions that depend on approved skills | `enterprise-agents.yaml` |

The dependency direction is one way:

```text
NeqSim core
  -> core/community skills (neqsim-*)
  -> enterprise skills (enterprise-*)
  -> enterprise agents
```

Skills contain engineering methods or policy overlays. Agents orchestrate skills and must not embed calculation methods directly.

## Step 1: Create the Enterprise Skills Repository

Start from the structure used by the NeqSim enterprise skills repository:

```text
<company>-neqsim-enterprise-skills/
|-- enterprise-skills.yaml
|-- pyproject.toml
|-- skills/
|   |-- process/
|   |   `-- enterprise-example-skill/
|   |       |-- SKILL.md
|   |       |-- README.md
|   |       |-- pyproject.toml
|   |       |-- src/
|   |       |-- examples/
|   |       `-- tests/
|-- templates/
`-- tests/
```

Use `enterprise-*` names for all internal skill IDs:

```yaml
catalog_version: "1.0"
last_updated: "2026-07-05"
trust: internal
skills:
  - name: enterprise-example-skill
    version: "0.1.0"
    description: Company-specific policy overlay for an approved NeqSim workflow.
    repo: company/<company>-neqsim-enterprise-skills
    path: skills/process/enterprise-example-skill/SKILL.md
    tags: [process, policy]
```

Each `SKILL.md` front matter `name` must match the catalog `name`. Keep examples synthetic and never commit credentials, private URLs, internal document extracts, production data, or proprietary vendor data.

## Step 2: Create the Enterprise Agents Repository

Start from the structure used by the NeqSim enterprise agents repository:

```text
<company>-neqsim-enterprise-agents/
|-- enterprise-agents.yaml
|-- agents/
|   `-- example-agent/
|       |-- AGENT.md
|       |-- agent.yaml
|       |-- prompts/
|       |-- workflows/
|       |-- examples/
|       |-- tests/
|       `-- README.md
|-- templates/
`-- tests/
```

Enterprise agents may depend on both public NeqSim skills and company enterprise skills:

```yaml
catalog_version: "0.1"
last_updated: "2026-07-05"
trust: internal
agents:
  - name: example-agent
    version: "0.1.0"
    description: Coordinates the public screening method and company policy overlay.
    repo: company/<company>-neqsim-enterprise-agents
    path: agents/example-agent/AGENT.md
    required_skills:
      - neqsim-fluid-quality-check
      - enterprise-example-skill
    supported_domains:
      - process
```

The shared fields in `AGENT.md` front matter and `agent.yaml` must match exactly. Put workflow policy, required inputs, output expectations, validation gates, and human-review requirements in the agent. Put executable methods in skills.

## Step 3: Configure Installation and Discovery

In the Engineering Harness workspace, create or update `plugins/sources.yaml` with the company repositories. Use `local_path` for local clones during development, or omit it to read from GitHub using a token.

```yaml
sources:
  - name: neqsim-community-skills
    kind: skills
    repo: equinor/neqsim-community-skills
    ref: main
    catalog: community-skills.yaml
    trust: community
    enabled: true

  - name: company-enterprise-skills
    kind: skills
    repo: company/<company>-neqsim-enterprise-skills
    ref: main
    catalog: enterprise-skills.yaml
    trust: internal
    enabled: true
    # local_path: C:\path\to\<company>-neqsim-enterprise-skills
    # install_content: true

  - name: company-enterprise-agents
    kind: agents
    repo: company/<company>-neqsim-enterprise-agents
    ref: main
    catalog: enterprise-agents.yaml
    trust: internal
    enabled: true
    # local_path: C:\path\to\<company>-neqsim-enterprise-agents
    # install_content: true
```

Then point the harness at that source file and synchronize:

```powershell
$env:EH_SOURCES_FILE = "C:\path\to\plugins\sources.yaml"
$env:GITHUB_TOKEN = "<token-with-read-access>"   # only needed for private GitHub reads
engineering-harness plugins list
engineering-harness plugins sync
engineering-harness list skills
engineering-harness list agents
```

For local development with `local_path`, no GitHub token is required. For private GitHub repositories, use `GITHUB_TOKEN` or `GH_TOKEN` with read access. The token is read at request time and must not be committed to `.env`, docs, examples, or test fixtures.

## Step 4: Choose Metadata-Only or Full-Content Install

By default, `engineering-harness plugins sync` imports catalog metadata into `plugins/agents/` and `plugins/skills/`. This is enough for discovery, validation, and workflow wiring.

Use full-content install when the runtime must read agent prompts or run installed skill scripts:

```powershell
$env:EH_PLUGIN_INSTALL_CONTENT = "true"
engineering-harness plugins sync --content
```

Full-content install copies safe text and Python files into `plugins/content/agents/<agent>/` and `plugins/content/skills/<skill>/`. Agentic runs can inject installed `AGENT.md` instructions when `EH_ENABLE_AGENT_PROMPTS=true`. Skill scripts are executable only when `EH_ENABLE_SCRIPT_TOOLS=true` and the workflow explicitly uses the `script` tool.

## Step 5: Validate the Company Catalogs

Run metadata tests in each enterprise repository and a harness sync check before advertising the repositories as installable:

```powershell
Push-Location C:\path\to\<company>-neqsim-enterprise-skills
python -m pytest
Pop-Location

Push-Location C:\path\to\<company>-neqsim-enterprise-agents
python -m unittest discover -s tests
Pop-Location

Push-Location C:\path\to\engineering-harness
$env:EH_SOURCES_FILE = "C:\path\to\plugins\sources.yaml"
engineering-harness plugins sync --content
engineering-harness list skills
engineering-harness list agents
Pop-Location
```

If the repositories are part of the NeqSim ecosystem, add or run a cross-catalog contract test that verifies every agent `required_skills` entry resolves to an imported `neqsim-*` or `enterprise-*` skill.

## Contribution Rules for Company Repositories

- Use `enterprise-*` for internal skills and `neqsim-*` for public/core dependencies.
- Declare `trust: internal` in enterprise catalogs.
- Keep examples synthetic and public-safe.
- Keep credentials, tokens, cookies, private URLs, internal document text, and production data out of Git.
- Require human review for safety, design, operational, production, and regulatory conclusions.
- Promote reusable public screening methods upstream to community skills instead of duplicating them privately.
- Promote validated NeqSim calculations into the core NeqSim repository when they are general and suitable for open publication.

See [Agent and Skill Catalog Schema](agent_skill_catalog_schema.md) for the shared metadata contract and [Skills and Agents Guide](skills_guide.md) for the broader core/community/private model.