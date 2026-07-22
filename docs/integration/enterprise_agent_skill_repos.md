---
title: Enterprise Agent and Skill Repositories
description: How to set up company-private NeqSim enterprise agent and skill repositories, publish catalogs, and configure Engineering Harness discovery and installation.
---

# Enterprise Agent and Skill Repositories

This guide explains how a company can set up private NeqSim enterprise repositories and make their agents and skills discoverable to runtimes such as the Engineering Harness.

Use this pattern when the content is useful to a company or project but should not be published in the community repositories because it contains company policy, internal integration wiring, governed workflow requirements, or private validation expectations.

## Onboarding: recommended order

There are two distinct roles. The **company/org admin** creates the private repositories once; **each engineer** then installs NeqSim and connects to them. Follow the steps in this order.

### A. One-time company setup (admin, once per organization)

1. **Create the two private repositories** from the NeqSim templates:
   `<company>-neqsim-enterprise-agents` (catalog `enterprise-agents.yaml`) and
   `<company>-neqsim-enterprise-skills` (catalog `enterprise-skills.yaml`). See
   [Step 1](#step-1-create-the-enterprise-skills-repository) and
   [Step 2](#step-2-create-the-enterprise-agents-repository) below.
2. **Grant engineers read access** through your normal GitHub org / SSO groups.
3. **Share the two repo names** (and any internal Git URLs) with engineers.

The public **community** catalogs (`neqsim-community-agents`,
`neqsim-community-skills`) are bundled with NeqSim and need no setup — do not
recreate them. Only the private enterprise repos are created here.

### B. Per-engineer onboarding (each user, in this order)

**Prerequisites** (install once, per-user — via your software portal / `winget`, no admin needed):
Python 3.8+, Git, and — only if you will use `--login` for GitHub SSO — the
[GitHub CLI](https://cli.github.com/) (`gh`). A JDK is only needed for Java builds,
not for the agents/skills CLI. **Behind a corporate proxy or internal PyPI mirror?**
Set `HTTPS_PROXY`/`HTTP_PROXY` (or configure your PyPI index) for the session first —
see [devtools/README.md](../../devtools/README.md#recommended-no-admin-runbook-for-the-user).

1. **Get the NeqSim code and open a terminal in it:**
   ```powershell
   git clone https://github.com/equinor/neqsim.git
   cd neqsim
   ```
2. **Install NeqSim.** On a locked-down/no-admin Windows PC use the GPO-proof
   batch installer (no PowerShell, no admin):
   ```powershell
   .\install.cmd            # or: py -m pip install -e devtools/
   ```
   macOS/Linux: `./install.sh`. This also puts the `neqsim` command on PATH
   (open a new terminal afterwards; or use `py -m neqsim_cli`).
   > **In VS Code:** if running `neqsim` shows *"The term 'neqsim' is not
   > recognized"*, a new integrated terminal is **not** enough — VS Code captures
   > PATH at launch, so fully quit and reopen VS Code. (Installing into an
   > activated virtualenv avoids this entirely.)
3. **Install community agents & skills — no auth needed.** These are the default
   catalog and work immediately:
   ```powershell
   neqsim agent install --all --vscode    # every agent + each agent's required skills
   neqsim skill install --all --vscode    # every catalog skill (incl. standalone ones)
   ```
   Each command prints a per-item progress list (`[1/N] <name>`) and an install
   summary. `--vscode` also exports them to VS Code (reload/restart VS Code to
   pick them up). To install just one: `neqsim skill install <name> --vscode`.
4. **Connect the enterprise repos AND sign in, in one step.** `private-init --login`
   registers the private repo in your per-user catalog **and** launches browser
   SSO (`gh auth login --web`) — so SSO and repo registration happen together:
   ```powershell
   neqsim agent private-init --repo <company>/<company>-neqsim-enterprise-agents --login
   neqsim skill private-init --repo <company>/<company>-neqsim-enterprise-skills --login
   # add more private repos later with the same options:
   neqsim agent add-repo --url https://git.internal.company.com/neqsim/enterprise-agents.git
   ```
   If your org already signs you in through Git Credential Manager, you can omit
   `--login`. Each command prints the catalog file it wrote
   (`~/.neqsim/private-agents.yaml` / `~/.neqsim/private-skills.yaml`).

   > **SAML SSO organizations (e.g. GitHub Enterprise / an org that enforces SSO):**
   > after `gh auth login --web` you must **authorize the token for the organization**
   > that owns the private repo, or reads fail with 403/404. The browser prompts you
   > during login; if you logged in earlier, run `gh auth refresh -h github.com` and
   > approve the org, or open the token's *Configure SSO* menu on GitHub. Verify access
   > with `gh repo view <org>/<repo>` before running `neqsim ... list --private`.
5. **Install the enterprise agents & skills.** Now that the private repos are
   registered, `--all` spans community **and** enterprise together — re-run it to
   pick up the enterprise content that step 3 could not see yet:
   ```powershell
   neqsim agent list --private                 # verify discovery
   neqsim agent install --all --vscode --force # every community + enterprise agent + required skills
   neqsim skill install --all --vscode --force # every community + enterprise catalog skill
   # ...or install a single item by name:
   neqsim skill install <name> --vscode
   neqsim agent install <name> --vscode
   ```
   Both `--vscode` and `--target vscode` export to your personal `~/.copilot/agents`
   and `~/.copilot/skills` folders — the locations VS Code and the GitHub Copilot CLI
   scan in every workspace. Reload/restart VS Code afterwards to pick them up.

> **Why this order?** Community content needs no authentication, so it is installed
> first and always works. SSO is only required to *read the private repos*, and
> `private-init --login` performs the sign-in as part of registering them — so you
> never do a separate "register SSO" step. Creating the repos (Part A) is a
> one-time admin task, not something each engineer repeats.

Runtime integrators using the **Engineering Harness** instead of the `neqsim`
CLI follow [Step 3](#step-3-configure-installation-and-discovery) onward, which
uses `plugins/sources.yaml` for the same repositories.

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

In the Engineering Harness workspace, create or update `plugins/sources.yaml` with the company repositories. Use `local_path` for local clones during development. For private GitHub repositories without `local_path`, prefer an authenticated GitHub CLI or Git Credential Manager session created through browser SSO; environment tokens are a CI/non-interactive fallback.

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
# Optional fallback for CI/non-interactive private GitHub reads:
# $env:GITHUB_TOKEN = "<token-with-read-access>"
engineering-harness plugins list
engineering-harness plugins sync
engineering-harness list skills
engineering-harness list agents
```

For local development with `local_path`, no GitHub token is required. For private GitHub repositories, run the normal browser SSO flow first, for example `gh auth login --web`, or use Git Credential Manager through your standard `git clone` flow. `GITHUB_TOKEN` or `GH_TOKEN` may be used only where interactive SSO is unavailable, such as CI or service contexts. Tokens are read at request time and must not be committed to `.env`, docs, examples, or test fixtures.

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