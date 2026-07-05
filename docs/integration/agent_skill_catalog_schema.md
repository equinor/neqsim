---
title: Agent and Skill Catalog Schema
description: Shared schema and compatibility policy for NeqSim agent and skill catalogs, agent.yaml metadata, AGENT.md front matter, and trust namespaces.
---

# Agent and Skill Catalog Schema

This document is the shared contract for NeqSim agent and skill metadata across the core, community, and enterprise repositories. It keeps discovery deterministic while allowing community and internal packages to evolve independently.

For a practical company setup and installation workflow, see [Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md). For a browsable dependency overview, see [Agent to Skill Map](agent_skill_map.md).

## Repository Trust Scopes

Every catalog must declare a top-level `trust` value.

| Scope | Intended repository type | Machine-readable IDs |
| --- | --- | --- |
| `core` | Core NeqSim skills shipped with the main repository | `neqsim-*` |
| `community` | Public community agents and skills | `neqsim-*` |
| `internal` | Enterprise agents and skills | `enterprise-*` plus approved `neqsim-*` dependencies |

Runtimes should display the namespace with the agent or skill name, for example `community/pvt-agent` or `internal/pvt-agent`, whenever entries from more than one trust scope are loaded.

## Catalog Format

Agent catalogs use this shape:

```yaml
catalog_version: "0.1"
last_updated: "2026-07-05"
trust: community
agents:
  - name: pvt-agent
    version: "0.1.0"
    description: Short purpose statement.
    repo: equinor/neqsim-community-agents
    path: agents/pvt-agent/AGENT.md
    required_skills:
      - neqsim-fluid-quality-check
    supported_domains:
      - pvt
```

Skill catalogs use the same top-level metadata and a `skills` list:

```yaml
catalog_version: "1.0"
last_updated: "2026-07-05"
trust: community
skills:
  - name: neqsim-fluid-quality-check
    version: "0.1.0"
    description: Trigger-rich description.
    repo: equinor/neqsim-community-skills
    path: skills/pvt/fluid-quality-check/SKILL.md
    tags: [pvt, validation]
```

Required catalog fields are `name`, `version`, `description`, `repo`, and `path`. Agent entries also require `required_skills` and `supported_domains`. Skill entries should include `tags` and `min_neqsim_version` when applicable. Catalog metadata is not an independent copy-editing surface: shared fields such as `name`, `version`, `description`, and `required_skills` must match the package metadata exactly so search indexes, installers, and generated documentation all describe the same package.

## Package Metadata

Every agent package has two synchronized metadata surfaces:

| File | Purpose | Required fields |
| --- | --- | --- |
| `AGENT.md` front matter | Human-readable specification with machine-readable header | `name`, `description`, `version`, `required_skills` |
| `agent.yaml` | Installer and harness metadata | `name`, `description`, `version`, `required_skills`, `supported_domains`, `inputs`, `outputs`, `human_review_required` |

The shared fields in `AGENT.md` front matter and `agent.yaml` must match exactly. The same shared fields in the root agent catalog and `agent.yaml` must also match exactly. Enterprise agents may add governance fields such as `agent_type` and `status`.

Agents should normally list at least one `required_skills` entry. A skill-less enterprise agent is allowed only when it is an orchestration package, for example a front-door coordinator or study-review agent, and must declare an explicit `agent_type` such as `enterprise-coordinator` or `enterprise-orchestrator`.

Every skill package has a `SKILL.md` front matter block. The `name` value must be the canonical catalog ID, and the catalog `path` must point to that `SKILL.md` file.

## Canonical IDs and Alias Policy

Machine-readable dependency fields must use canonical IDs only.

| Dependency type | Canonical form | Allowed in `required_skills` |
| --- | --- | --- |
| Core or community skill | `neqsim-*` | Yes |
| Enterprise skill | `enterprise-*` | Yes, enterprise agents only |
| Legacy shorthand | `hydrate-screening`, `fluid-quality-check` | No |

Legacy shorthand names may appear in prose, migration notes, examples, or alias tables for reader convenience. Runtimes may provide explicit alias maps for backward compatibility, but catalogs, `agent.yaml`, `AGENT.md` front matter, and generated package metadata must not rely on implicit alias expansion.

## Cross-Catalog Resolution

When a runtime loads multiple catalogs, it should validate these invariants:

- Every `required_skills` entry resolves to a loaded core, community, or enterprise skill.
- Community catalogs never depend on `enterprise-*` skills.
- Duplicate short agent names are allowed only when their namespaced IDs are unique.
- Unqualified duplicate agent names must not be exposed as the only runtime selector.
- Catalog `trust` values are explicit and come from `core`, `community`, or `internal`.
- Root catalog metadata matches package metadata for shared fields.
- Empty `required_skills` is intentional and explained by an orchestration `agent_type`.

These invariants are intentionally independent of any one runtime so they can be checked by repository tests, CI workflows, and the Engineering Harness.

## Company Enterprise Catalogs

A company that needs private NeqSim agents and skills should publish two internal catalogs:

- `enterprise-skills.yaml` in a private enterprise skills repository.
- `enterprise-agents.yaml` in a private enterprise agents repository.

Both catalogs must declare `trust: internal`. Enterprise skill names must use `enterprise-*`. Enterprise agents may list public NeqSim dependencies such as `neqsim-fluid-quality-check` and internal dependencies such as `enterprise-fluid-quality-check` in `required_skills`.

The Engineering Harness installs these catalogs by reading a configured `plugins/sources.yaml` file. The source entry names the private repository, catalog file, branch or tag, trust scope, and optional `local_path` for local clones. For GitHub-hosted private repositories, the harness reads `GITHUB_TOKEN` or `GH_TOKEN` at request time and does not store it.

Use metadata-only sync for discovery and validation. Use full-content sync only when the runtime needs copied `AGENT.md`, `SKILL.md`, examples, or Python scripts:

```powershell
$env:EH_SOURCES_FILE = "C:\path\to\plugins\sources.yaml"
$env:GITHUB_TOKEN = "<token-with-read-access>"
engineering-harness plugins sync
engineering-harness plugins sync --content
```

See [Enterprise Agent and Skill Repositories](enterprise_agent_skill_repos.md) for the complete setup procedure.