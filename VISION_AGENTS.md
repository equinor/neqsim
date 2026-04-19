# NeqSim Agent & Skill Vision

> This document defines what belongs in NeqSim's agentic layer (agents, skills,
> instructions) and what doesn't. It's a guardrail for contributors and
> reviewers — not a law of physics. Strong user demand and strong technical
> rationale can change any of these rules.

## What This Is

NeqSim's agentic system lets AI coding agents (Copilot, Claude Code, Cursor,
Codex, Windsurf) solve thermodynamics and process engineering tasks using
NeqSim's Java API. The system has three layers:

1. **Global instructions** — `AGENTS.md`, `.github/copilot-instructions.md`,
   `CONTEXT.md`. Read automatically by all agents. Define constraints (Java 8),
   build commands, code patterns, and the task-solving workflow.

2. **Skills** — `.github/skills/*/SKILL.md`. Reusable domain knowledge loaded
   on demand. Each skill covers a specific engineering domain (flow assurance,
   PVT, distillation, etc.) with verified patterns, code templates, and
   troubleshooting guidance.

3. **Agents** — `.github/agents/*.agent.md`. Specialist agent definitions with
   YAML frontmatter (name, description, argument-hint) and a system prompt body.
   Each agent is an entry point for a class of engineering tasks.

## What Belongs in Core

### Global instructions (core — high bar)

- Java 8 compatibility rules
- Build and test commands
- API patterns that apply to ALL simulations (fluid creation, flash, equipment)
- Task-solving workflow structure
- Security and serialization constraints

Changes require maintainer review. These files are read by every agent on every
invocation — keep them lean.

### Skills (core vs. community)

**Add to core** (`.github/skills/`) when the skill:

- Covers a NeqSim subsystem (thermo, process, PVT, standards, mechanical design)
- References internal Java classes, method signatures, or package structure
- Is used by multiple agents
- Requires testing against NeqSim's actual API (not just general knowledge)

**Keep as community skill** (`community-skills.yaml` / user's own repo) when the skill:

- Covers a narrow workflow (e.g., "my company's TEG sizing procedure")
- Is specific to one user's environment (plant historian tags, company standards)
- Doesn't reference NeqSim internals — just orchestrates existing capabilities
- Is experimental or unverified

### Agents (core — medium bar)

**Add to core** when the agent:

- Maps to a distinct engineering discipline (thermodynamics, PVT, flow assurance)
- Uses NeqSim classes that require specific knowledge to use correctly
- Is referenced by the router agent for delegation

**Keep as community / user-local** when the agent:

- Is a wrapper that just chains existing agents
- Covers a narrow use case better served by a skill
- Duplicates an existing agent's scope

## What We Will Not Merge (For Now)

- **New core skills that can live as community skills** — if it doesn't
  reference NeqSim internals, it should be a community skill first
- **Agent-hierarchy frameworks** (manager-of-managers, nested planner trees) —
  the router agent is the single coordination point
- **Heavy orchestration layers** that duplicate existing agent + skill
  infrastructure
- **Commercial service integrations** in skills (API keys, paid services) —
  these belong in user workspace skills
- **Full translations** of all skill/agent files — we plan AI-generated
  translations later
- **Skills without verified code patterns** — every skill must include
  copy-paste NeqSim code that has been tested against the actual API

## Contribution Rules

### For skills

1. Use `neqsim new-skill "name"` to scaffold
2. Fill in all sections — especially code patterns and common mistakes
3. Test every code pattern against NeqSim's actual API
4. Register in `.github/copilot-instructions.md` and `.github/skills/README.md`
5. One skill per PR — don't bundle unrelated skills

### For agents

1. Check that no existing agent covers the same scope
2. Include YAML frontmatter: `name`, `description`, `argumentHint`
3. Reference which skills the agent should load
4. Add the agent to `copilot-instructions.md` agent list and `AGENTS.md`
5. One agent per PR

### For global instructions

1. Discuss changes in a GitHub issue first
2. Keep additions minimal — these files are loaded on every invocation
3. Verify against the actual codebase (method names, class paths, etc.)
4. Changes here affect ALL agents — test broadly

### PR guidelines (same as OpenClaw)

- One PR = one issue/topic. Don't bundle unrelated changes.
- AI-assisted PRs are welcome — mark as `[AI-Assisted]` in the title.
- PRs over ~3,000 changed lines need exceptional justification.
- Don't open large batches of tiny PRs at once.

## Future: Community Skill Registry

Currently all skills live in `.github/skills/`. The long-term plan:

1. **Phase 1 (now)**: Skills in-repo, contributed via PR
2. **Phase 2 (done)**: YAML catalog (`community-skills.yaml`) listing community
   skills hosted in external repos — machine-readable, searchable
3. **Phase 3 (done)**: CLI tool to install community skills to user workspace:
   ```bash
   neqsim skill list              # browse catalog
   neqsim skill search "topic"    # search by keyword
   neqsim skill install <name>    # download to ~/.neqsim/skills/
   neqsim skill installed          # show installed
   neqsim skill remove <name>     # uninstall
   ```
4. **Phase 4 (future)**: Web UI / GitHub Action to auto-test community skills
   against NeqSim's API before publishing to the catalog

The goal: make NeqSim skills as easy to publish and install as npm packages
or ClawHub skills, while keeping the core lean and verified.
