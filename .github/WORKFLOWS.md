# GitHub Actions Workflow Catalog

Workflow YAML files must remain directly in [workflows/](workflows/): GitHub Actions
does not discover workflows in category subdirectories. Use lowercase, kebab-case
filenames with the category prefix below, followed by a readable slug of the
workflow's Actions display name. For example, `core-codeql-security-analysis.yml`
maps to the display name `CodeQL Security Analysis`.

Mirror the category prefix and filename description in the Actions display name.
Keep job IDs and job-level required check names stable when changing display
names. AI includes agent, MCP, task, and research tooling; it does not imply
that a workflow invokes an LLM.

## Core Java and Quality

| Workflow | Actions Display Name | Events | Responsibility |
|---|---|---|---|
| [core-run-build-test-and-javadoc.yml](workflows/core-run-build-test-and-javadoc.yml) | [Core] Build, Test, and Javadoc | Push, PR, manual | Java 8/21 tests on Ubuntu/Windows, Javadoc, numerical contracts, eight slow-test shards, coverage, and retained Surefire reports. The Java 21 fast-test matrix has a 120-minute job budget for coverage overhead and hosted-runner variation. Preserves four legacy slow-test check contexts; fast matrix failures do not cancel other platforms. Also retains agent benchmark and agent-skill checks. |
| [core-codeql-security-analysis.yml](workflows/core-codeql-security-analysis.yml) | [Core] CodeQL Security Analysis | Push, PR, schedule | Java security and quality analysis. |
| [core-pre-commit-checks.yml](workflows/core-pre-commit-checks.yml) | [Core] Pre-commit Checks | PR, manual | Check-only contributor quality gate; never formats or pushes changes. |
| [core-spotless-format-patch.yml](workflows/core-spotless-format-patch.yml) | [Core] Spotless Format Patch | PR | Produces a formatting patch artifact without committing changes. |
| [core-safety-source-term-contract.yml](workflows/core-safety-source-term-contract.yml) | [Core] Safety Source-Term Contract | PR, manual | Java and Python release-source-term contract validation. |
| [core-engineering-diagram-performance.yml](workflows/core-engineering-diagram-performance.yml) | [Core] Engineering Diagram Performance | PR, manual | Java diagram benchmark and Python performance-budget validation. |

### Windows JVM crash workaround

The Windows fast-test step sets `JAVA_TOOL_OPTIONS` to exclude two methods from
JIT compilation: `org.ejml.dense.row.linsol.lu.LinearSolverLu_DDRM::solve` and
`neqsim.thermo.phase.PhaseSrkCPA::initCPAMatrix`.
PR [#4484](https://github.com/equinor/neqsim/pull/4484) exposed a fatal
`EXCEPTION_ACCESS_VIOLATION` in the LU solver's C2-compiled code on Temurin
21.0.12.1 while running `PTPhaseEnvelopeReferenceEosTest`. PR
[#4499](https://github.com/equinor/neqsim/pull/4499) retained a second fatal
access violation in C2-compiled `PhaseSrkCPA.initCPAMatrix` during
`SystemHybridEosGeFlashTest` on the same JVM build; see
[the failing Windows job](https://github.com/equinor/neqsim/actions/runs/39612791624/job/119266533232)
and its `jvm-crash-logs-Windows` artifact. Both methods still run through the
interpreter; EJML remains the numerical backend and every test stays
enabled. The setting reaches the forked Surefire JVM and is limited to the
Windows step. Ubuntu coverage and production JVM options are unchanged.

Both fast-test platforms retain HotSpot `hs_err_pid*.log` and `replay_pid*.log`
files in a separate `jvm-crash-logs-<platform>` artifact when present. Existing
Surefire report artifact names and layouts are preserved. Inspect the fatal
error and problematic frame before changing numerical code or test tolerances.
Remove an exclusion only after the Windows full suite passes on a replacement
JDK without that exclusion; a passing Linux run alone is insufficient evidence.

## Publishing and Distribution

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [publish-create-release-after-merge.yml](workflows/publish-create-release-after-merge.yml) | [Publish] Create Release After Merge | Push, manual | Builds core/MCP release assets, creates or updates GitHub releases, dispatches downstream publishers, and can notify the plugin marketplace. |
| [publish-package-to-maven-central.yml](workflows/publish-package-to-maven-central.yml) | [Publish] Publish Package to Maven Central | Published release, manual | Signs and publishes Maven packages using release credentials. |
| [publish-deploy-javadoc.yml](workflows/publish-deploy-javadoc.yml) | [Publish] Deploy Javadoc | Published release, manual | Deploys generated Javadoc to the Javadoc branch. |
| [publish-neqsim-image.yml](workflows/publish-neqsim-image.yml) | [Publish] Publish NeqSim Image | Published release | Publishes the self-contained NeqSim runtime container. |
| [publish-prebuild-dev-container.yml](workflows/publish-prebuild-dev-container.yml) | [Publish] Prebuild Dev Container | Push, schedule, manual | Publishes the developer toolchain container, distinct from the runtime image. |
| [publish-build-mcp-server-release.yml](workflows/publish-build-mcp-server-release.yml) | [Publish] Build MCP Server Release | Push, published release, manual | Builds the MCP distribution; release/manual runs can publish its container. |

Release dispatches use workflow filenames at the selected release tag. New tags
must contain the renamed workflows. Historical tags retain their original workflow
files; do not rewrite tags or use a publishing dispatch as a migration smoke test.

## AI Tooling, Tasks, and Research

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [ai-devtools-python-tests.yml](workflows/ai-devtools-python-tests.yml) | [AI] Devtools Python Tests | PR, push | Python toolkit, hermetic agent installation/discovery, production guard, and ledger tests. |
| [ai-skills-and-agents-lint.yml](workflows/ai-skills-and-agents-lint.yml) | [AI] Skills and Agents Lint | PR, push | Agent/skill structure, API references, and mapping consistency. |
| [ai-engineering-capability-coverage.yml](workflows/ai-engineering-capability-coverage.yml) | [AI] Engineering Capability Coverage | PR, push | Rebuilds and validates the engineering capability inventory. A conditional step in the coverage job commits changed inventory to same-repository PR branches or master; fork PRs remain validation-only. |
| [ai-mcp-protocol-qualification.yml](workflows/ai-mcp-protocol-qualification.yml) | [AI] MCP Protocol Qualification | PR, push | Packaged MCP protocol and capability contracts. |
| [ai-agent-install-smoke-test.yml](workflows/ai-agent-install-smoke-test.yml) | [AI] Agent Install Smoke Test | PR, push, schedule, manual | Community agent installer smoke tests. |
| [ai-task-quality-gate.yml](workflows/ai-task-quality-gate.yml) | [AI] Task Quality Gate | PR | Changed task result schemas and consistency checks. |
| [ai-auto-open-nip-issues.yml](workflows/ai-auto-open-nip-issues.yml) | [AI] Auto-open NIP Issues | Push | Opens GitHub issues for new task improvement proposals. |
| [ai-paperlab-replication-gate.yml](workflows/ai-paperlab-replication-gate.yml) | [AI] PaperLab Replication Gate | PR, manual | Research reproducibility and numerical result comparison. |
| [ai-daily-research-scan.yml](workflows/ai-daily-research-scan.yml) | [AI] Daily Research Scan | PR, schedule, manual | Tests research scanning on PRs; scheduled/manual scans can open issues. |

## Examples and Scientific Qualification

These workflows qualify executable examples and scientific contracts. They are
not categorized as AI merely because an agent can use their results.

| Workflow | Actions Display Name | Events | Responsibility |
|---|---|---|---|
| [examples-execute-complete-pid-synthesis-notebook.yml](workflows/examples-execute-complete-pid-synthesis-notebook.yml) | [Examples] Execute Complete P&ID Synthesis Notebook | PR, manual | P&ID synthesis notebook and supporting tests. |
| [examples-execute-ncs-hazop-and-safety-engineering-notebook.yml](workflows/examples-execute-ncs-hazop-and-safety-engineering-notebook.yml) | [Examples] Execute NCS HAZOP and Safety Engineering Notebook | PR, manual | Offshore HAZOP and safety notebook qualification. |
| [examples-execute-complete-offshore-engineering-notebook.yml](workflows/examples-execute-complete-offshore-engineering-notebook.yml) | [Examples] Execute Complete Offshore Engineering Notebook | PR, manual | Offshore engineering notebook and comparison checks. |
| [examples-execute-process-to-engineering-simulator-notebook.yml](workflows/examples-execute-process-to-engineering-simulator-notebook.yml) | [Examples] Execute Process-to-Engineering Simulator Notebook | PR, manual | Process-to-engineering simulator notebook and handover contracts. |
| [examples-execute-energy-network-notebooks.yml](workflows/examples-execute-energy-network-notebooks.yml) | [Examples] Execute Energy Network Notebooks | PR, manual | Energy-network notebook execution. |
| [examples-production-optimization-documentation.yml](workflows/examples-production-optimization-documentation.yml) | [Examples] Production Optimization Documentation | PR, manual | Production optimization examples and documentation validation. |
| [examples-optional-combustion-physics.yml](workflows/examples-optional-combustion-physics.yml) | [Examples] Optional Combustion Physics | PR, manual | Combustion benchmark provenance and optional chemistry qualification. |

## Documentation

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [docs-convert-notebooks-to-markdown.yml](workflows/docs-convert-notebooks-to-markdown.yml) | [Docs] Convert Notebooks to Markdown | Push, manual | Converts documentation notebooks and commits generated Markdown. |
| [docs-documentation-search-coverage.yml](workflows/docs-documentation-search-coverage.yml) | [Docs] Documentation Search Coverage | PR, manual | Builds and verifies the documentation search index. |

## Maintenance

| Workflow | Actions Display Name | Events | Responsibility and Side Effects |
|---|---|---|---|
| [maintenance-security-metrics-badge.yml](workflows/maintenance-security-metrics-badge.yml) | [Maintenance] Security Metrics Badge | Schedule, push, manual | Intentionally disabled by `if: false`; would update security badge data if explicitly re-enabled. |

## Contributor Rules

- Choose the prefix by primary responsibility, not language or runtime.
- Keep the filename suffix aligned with the workflow display name and its category
  label. Do not rename jobs or alter required check names to rename a workflow.
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

## Validation of stacked pull requests

The core build/test/Javadoc, pre-commit, CodeQL and documentation-search workflows
run for pull requests targeting any branch, including feature branches used for
stacked changes. Push-triggered publication and master coverage remain restricted
to their existing targets. Required check names and permissions are unchanged.
A green auxiliary workflow does not substitute for the complete applicable matrix
on the current head.
