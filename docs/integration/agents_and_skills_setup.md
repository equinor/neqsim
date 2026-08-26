---
title: "Setting up NeqSim Agents and Skills"
description: "Single start-here guide for installing NeqSim agentic AI in VS Code — install the neqsim CLI, install community agents and skills into GitHub Copilot, and (for companies) link to setting up your own private enterprise agent and skill pages."
---

# Setting up NeqSim Agents and Skills

This is the canonical **start-here** page for using NeqSim agentic AI: install the
`neqsim` CLI, install the public **community agents and skills** into VS Code with
GitHub Copilot, and — for organizations — set up your own **private enterprise
agent and skill pages**.

Human review is always required for engineering conclusions. Agents help you
screen, organize, calculate, and draft — they do not replace engineering judgement.

---

## 1. How agents and skills fit together

```text
NeqSim core → core skills → community skills → (enterprise skills) → agents
```

- **Skills** are markdown files of reusable engineering methods (code patterns,
  design rules, correlations). They contain the engineering knowledge.
- **Agents** are role/workflow definitions that orchestrate skills. They declare
  the skills they need (`required_skills`) and are invoked from Copilot Chat.

| Layer | Repository | Access |
|-------|------------|--------|
| Library + CLI | [equinor/neqsim](https://github.com/equinor/neqsim) | Public |
| Community agents | [equinor/neqsim-community-agents](https://github.com/equinor/neqsim-community-agents) | Public |
| Community skills | [equinor/neqsim-community-skills](https://github.com/equinor/neqsim-community-skills) | Public |
| Enterprise agents/skills | your company's private repos | Private |

---

## 2. Prerequisites

- **GitHub account** and a **GitHub Copilot** subscription.
- **[Visual Studio Code](https://code.visualstudio.com/)** with the **GitHub Copilot**
  and **GitHub Copilot Chat** extensions (or use **GitHub Codespaces** in the browser).
- Local development also needs **[Git](https://git-scm.com/downloads)**,
  **[Python 3.8+](https://www.python.org/downloads/)** (add to PATH),
  and **[Java (JDK)](https://adoptium.net/)**. You do not need to install Maven
  separately because the repository includes the Maven Wrapper.

> **Tip:** activate a Python virtual environment before installing so the `neqsim`
> command lands on PATH.

---

## 3. Install the `neqsim` CLI

Windows (PowerShell):

```powershell
git clone https://github.com/equinor/neqsim.git
cd neqsim
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1   # activate FIRST so 'neqsim' lands on PATH
.\install.cmd                  # pure-batch installer; works on locked-down machines
```

macOS / Linux:

```bash
git clone https://github.com/equinor/neqsim.git && cd neqsim
python3 -m venv .venv && source .venv/bin/activate
./install.sh
```

Keep the virtual environment active and verify in the same terminal:

```powershell
neqsim --help
neqsim doctor
```

If `neqsim` is not found, use `python -m neqsim_cli --help` and see
[devtools/README.md](../../devtools/README.md#troubleshooting-neqsim-not-found).
If you installed outside a virtual environment, fully quit and reopen VS Code so
its captured PATH is refreshed.

---

## 4. Install the community agents into VS Code

The public community catalog requires no login. Select it explicitly so any
previously registered private catalogs cannot affect the public installation.

```powershell
neqsim agent install --all --source community --vscode --force
neqsim agent doctor --target vscode --source community
```

- `--all --source community` installs all public community agents; `--vscode`
  exports them into GitHub Copilot Chat; `--force` overwrites stale exports
  (safe to re-run after updates).
- Installing an agent **automatically installs the skills** it declares in
  `required_skills`.
- Installation is successful when both commands exit with code `0` and doctor
  reports `Result: PASS`. Do not verify against a fixed agent count because the
  catalog changes over time.

Browse or install individually:

```powershell
neqsim agent list                    # list community agents
neqsim skill list                    # list community skills
neqsim agent search hydrate          # search by name/tag/skill
neqsim agent install <name> --vscode # install one agent
neqsim skill install <name> --vscode # install one skill (standalone)
neqsim agent doctor --target vscode --source community # verify community exports
```

---

## 5. Use the agents

Open **Copilot Chat** in VS Code, type `@` to see installed agents, and describe
your task in plain language. Globally exported community examples include
`@pvt-agent`, `@process-engineer-agent`, `@flow-assurance-engineer-agent`, and
`@process-safety-agent`.

For agentic task-solving (task folders, notebooks, reports) see
[AGENTS.md](../../AGENTS.md) and
[docs/development/TASK_SOLVING_GUIDE.md](../development/TASK_SOLVING_GUIDE.md).
Workspace-local core agents such as `@solve.task` are available when this NeqSim
workspace is open; they are distinct from globally exported community agents.

---

## 6. Set up your own private enterprise agents and skills

Companies keep proprietary methods, plant data, private tag names, internal URLs,
and project-specific design bases out of the public repos by building their own
**private enterprise agent and skill pages** on top of the public community
content. Engineers then register those private catalogs with the `neqsim` CLI
(browser SSO) and install community + enterprise together:

```powershell
neqsim agent private-init --repo <company>/<company>-neqsim-enterprise-agents --catalog-path enterprise-agents.yaml --login
neqsim skill private-init --repo <company>/<company>-neqsim-enterprise-skills --catalog-path enterprise-skills.yaml
neqsim agent install --all --vscode --force   # community + enterprise
```

Full company setup, catalog format, discovery, and governance:
**[Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md)**.

---

## 7. Learn more

- [Skills Guide](skills_guide.md) — full skill authoring and install walkthrough
- [Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md) — private company setup
- [VISION_AGENTS.md](../../VISION_AGENTS.md) — what belongs in core vs. community
- [.github/skills/README.md](../../.github/skills/README.md) — quick contribution guide
- [community-agents.yaml](../../community-agents.yaml) / [community-skills.yaml](../../community-skills.yaml) — the catalogs
