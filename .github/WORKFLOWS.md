# GitHub Actions Workflow Catalog

Workflow YAML files must remain directly in [workflows/](workflows/): GitHub Actions
does not discover workflows in category subdirectories. Lowercase, kebab-case
filenames use the prefixes below to group workflows by responsibility.

The filename categories do not change workflow display names, job IDs, required
check names, permissions, triggers, or artifact names. AI includes agent, MCP,
task, and research tooling; it does not imply that a workflow invokes an LLM.

## Core Java and Quality

| Workflow | Actions Display Name | Events | Responsibility |
|---|---|---|---|
| [core-java-tests.yml](workflows/core-java-tests.yml) | Run build, test and javadoc | Push, PR, manual | Java 8/21 tests on Ubuntu/Windows, Javadoc, numerical contracts, slow-test shards, and coverage. Also retains agent benchmark and agent-skill checks. |
| [core-codeql.yml](workflows/core-codeql.yml) | CodeQL Security Analysis | Push, PR, schedule | Java security and quality analysis. |
| [core-precommit.yml](workflows/core-precommit.yml) | Pre-commit checks | PR, manual | Check-only contributor quality gate; never formats or pushes changes. |
| [core-spotless-patch.yml](workflows/core-spotless-patch.yml) | Spotless format patch | PR | Produces a formatting patch artifact without committing changes. |
| [core-safety-source-term-contract.yml](workflows/core-safety-source-term-contract.yml) | Safety source-term contract | PR, manual | Java and Python release-source-term contract validation. |
| [core-engineering-diagram-performance.yml](workflows/core-engineering-diagram-performance.yml) | Engineering diagram performance | PR, manual | Java diagram benchmark and Python performance-budget validation. |

## Publishing and Distribution

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [publish-github-release.yml](workflows/publish-github-release.yml) | Create release after merge | Push, manual | Builds core/MCP release assets, creates or updates GitHub releases, dispatches downstream publishers, and can notify the plugin marketplace. |
| [publish-maven-central.yml](workflows/publish-maven-central.yml) | Publish package to the Maven Central Repository | Published release, manual | Signs and publishes Maven packages using release credentials. |
| [publish-javadoc.yml](workflows/publish-javadoc.yml) | Deploy Javadoc | Published release, manual | Deploys generated Javadoc to the Javadoc branch. |
| [publish-neqsim-image.yml](workflows/publish-neqsim-image.yml) | Publish NeqSim Image | Published release | Publishes the self-contained NeqSim runtime container. |
| [publish-devcontainer.yml](workflows/publish-devcontainer.yml) | Prebuild Dev Container | Push, schedule, manual | Publishes the developer toolchain container, distinct from the runtime image. |
| [publish-mcp-server.yml](workflows/publish-mcp-server.yml) | Build MCP Server Release | Push, published release, manual | Builds the MCP distribution; release/manual runs can publish its container. |

Release dispatches use workflow filenames at the selected release tag. New tags
must contain the renamed workflows. Historical tags retain their original workflow
files; do not rewrite tags or use a publishing dispatch as a migration smoke test.

## AI Tooling, Tasks, and Research

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [ai-devtools-tests.yml](workflows/ai-devtools-tests.yml) | Devtools tests | PR, push | Python toolkit and agent installation/discovery tests. |
| [ai-skills-agents-lint.yml](workflows/ai-skills-agents-lint.yml) | Skills and agents lint | PR, push | Agent/skill structure, API references, and mapping consistency. |
| [ai-engineering-coverage.yml](workflows/ai-engineering-coverage.yml) | Engineering capability coverage | PR, push | Engineering capability inventory and routing evidence checks. |
| [ai-mcp-protocol-tests.yml](workflows/ai-mcp-protocol-tests.yml) | MCP protocol qualification | PR, push | Packaged MCP protocol and capability contracts. |
| [ai-install-smoke-test.yml](workflows/ai-install-smoke-test.yml) | Install smoke test | PR, push, schedule, manual | Community agent installer smoke tests. |
| [ai-task-quality.yml](workflows/ai-task-quality.yml) | Task quality gate | PR | Changed task result schemas and consistency checks. |
| [ai-task-improvement-issues.yml](workflows/ai-task-improvement-issues.yml) | Auto-open NIP issues | Push | Opens GitHub issues for new task improvement proposals. |
| [ai-paperlab-replication.yml](workflows/ai-paperlab-replication.yml) | PaperLab Replication Gate | PR, manual | Research reproducibility and numerical result comparison. |
| [ai-research-scan.yml](workflows/ai-research-scan.yml) | Daily Research Scan | PR, schedule, manual | Tests research scanning on PRs; scheduled/manual scans can open issues. |

## Examples and Scientific Qualification

These workflows qualify executable examples and scientific contracts. They are
not categorized as AI merely because an agent can use their results.

| Workflow | Actions Display Name | Events | Responsibility |
|---|---|---|---|
| [examples-pid-synthesis.yml](workflows/examples-pid-synthesis.yml) | Execute complete P&ID synthesis notebook | PR, manual | P&ID synthesis notebook and supporting tests. |
| [examples-ncs-hazop-safety.yml](workflows/examples-ncs-hazop-safety.yml) | Execute NCS HAZOP and safety engineering notebook | PR, manual | Offshore HAZOP and safety notebook qualification. |
| [examples-offshore-engineering.yml](workflows/examples-offshore-engineering.yml) | Execute complete offshore engineering notebook | PR, manual | Offshore engineering notebook and comparison checks. |
| [examples-process-to-engineering.yml](workflows/examples-process-to-engineering.yml) | Execute process-to-engineering simulator notebook | PR, manual | Process-to-engineering simulator notebook and handover contracts. |
| [examples-energy-networks.yml](workflows/examples-energy-networks.yml) | Execute Energy Network Notebooks | PR, manual | Energy-network notebook execution. |
| [examples-production-optimization.yml](workflows/examples-production-optimization.yml) | Production optimization documentation | PR, manual | Production optimization examples and documentation validation. |
| [examples-combustion-physics.yml](workflows/examples-combustion-physics.yml) | Optional combustion physics | PR, manual | Combustion benchmark provenance and optional chemistry qualification. |

## Documentation

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [docs-notebook-markdown.yml](workflows/docs-notebook-markdown.yml) | Convert Notebooks to Markdown | Push, manual | Converts documentation notebooks and commits generated Markdown. |
| [docs-search-coverage.yml](workflows/docs-search-coverage.yml) | Documentation search coverage | PR, manual | Builds and verifies the documentation search index. |

## Maintenance

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [maintenance-security-metrics.yml](workflows/maintenance-security-metrics.yml) | Security Metrics Badge | Schedule, push, manual | Intentionally disabled by `if: false`; would update security badge data if explicitly re-enabled. |

## Contributor Rules

- Choose the prefix by primary responsibility, not language or runtime.
- Keep all executable YAML flat in `workflows/`, with a `.yml` extension.
- Update this catalog when adding, removing, or changing a workflow's responsibility.
- Update self-path filters, cross-workflow dispatches, badges, documentation links,
  pre-commit patterns, and tests that read workflow files whenever renaming one.
- Preserve check names unless branch protection and ruleset consumers are reviewed.
- Keep publishing credentials and write permissions out of read-only test jobs.
- Reserve `.github/actions/` for genuinely reusable composite actions; do not create
  category subfolders under `workflows/` or duplicate workflows as filename aliases.
- Keep `.orig` backups outside workflow discovery in `.github/workflow-archive/`.
  Existing ignored backups are retained locally, not added as executable or tracked YAML.

Branch and path filters remain authoritative in each linked YAML file. The event
columns are summaries, not a promise that every change triggers every workflow.
Workflow consolidation and extracting the agent jobs from the core Java build are
separate changes, not part of this naming migration.