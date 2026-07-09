# Capability Assessment & Implementation Plan

> **Mandatory artifact for Standard and Comprehensive tasks.**
> Quick tasks may skip this and write a one-paragraph summary in `notes.md` instead.
>
> Produced by `@capability.scout` (or by the solver agent invoking the scout
> step) BEFORE writing any notebook code. The point is to discover gaps while
> they are still cheap to fix.

## 1. Task Decomposition

State the engineering disciplines, physical phenomena, equipment types, and
output deliverables that the task implies.

| Aspect | Items |
|---|---|
| Disciplines | thermodynamics / process / mechanical / chemical / economics / safety / control / environmental |
| Phenomena | phase equilibrium, transport, hydrate, wax, corrosion, … |
| Equipment | separators / compressors / heat exchangers / pipelines / wells / columns / reactors / valves / … |
| Standards | NORSOK / DNV / API / ASME / ISO / EN / company TR |
| Economic models | NPV / DCF / CAPEX / OPEX / fiscal regime |
| Output types | properties / profiles / sizing / cost / risk |

## 2. Capability Requirements Matrix

| # | Capability needed | Discipline | Priority | Notes |
|---|---|---|---|---|
| 1 |  | Thermo | Critical |  |
| 2 |  | Process | Critical |  |
| 3 |  | Mechanical | Important |  |
| 4 |  | Economics | Important |  |
| 5 |  | Standards | Important |  |

**Priority levels:** Critical (cannot solve without) · Important (affects
quality/completeness) · Nice-to-have (adds value).

## 3. NeqSim Coverage Check

For each row in section 2, list the NeqSim class/method that already covers it,
or mark it as a gap.

| # | Capability | NeqSim class/method | Status |
|---|---|---|---|
| 1 |  | `neqsim.thermo.system.SystemSrkEos` | ✅ Covered |
| 2 |  | `neqsim.process.equipment.separator.Separator` | ✅ Covered |
| 3 |  | — | ❌ Gap |
| 4 |  | `neqsim.process.util.fielddevelopment.DCFCalculator` | ✅ Covered |

**Search method used:** `grep_search` on `src/main/java/neqsim/...`,
`@capability.scout` agent, `neqsim-capability-map` skill.

## 4. Skills to Load

List the skills the solver should load for this task (run
`python devtools/skill_search.py "<task title>"` to get top-k recommendations,
then prune).

- `neqsim-...`
- `neqsim-...`

## 4b. Agents to Delegate To

Run `python devtools/agent_search.py "<task title>" --top 8` to rank the best
specialist agents across all repos (neqsim + community + enterprise), then
prune. Record the chosen agents and *why*. The skills each agent loads are shown
in the search output — prefer delegating to an agent over re-loading its skills
manually, so authentication/governance and its internal workflow are handled.

| Rank | Agent | Repo | Loads skills | Why selected / role in this task |
|---|---|---|---|---|
| 1 |  |  |  |  |
| 2 |  |  |  |  |
| 3 |  |  |  |  |

> Store the raw ranking: `python devtools/agent_search.py "<task>" --json --out
> step1_scope_and_research/agent_plan.json` — this file is part of the audit
> trail and feeds `results.json` `agent_workflow_plan`.

## 4c. Workflow Plan

Decide how the selected agents/skills are composed. Pick ONE:

- **Single agent** — one specialist covers the whole task.
- **Composition pattern** — a known 2–3 agent sequence from `router.agent.md`
  (e.g. `@process.model` → `@mechanical.design`). List the stages.
- **Declarative workflow** — for ≥3 disciplines or repeatable programs, use MCP
  `composeWorkflow` / `composeMultiServerWorkflow`, or an `engineering-harness`
  study. Note the workflow name/id.

| Stage | Agent / tool | Input (from) | Output (to) | Notes |
|---|---|---|---|---|
| 1 |  | task inputs |  |  |
| 2 |  | stage 1 output |  |  |

**Rationale for the composition choice:** _(1–2 sentences)_

## 5. External References

Public literature, standards documents, and internal database queries needed.

| Source | Type | Status | Path |
|---|---|---|---|
| | paper / standard / internal doc | retrieved / pending | `references/...` |

For internal documents, use `@stid.retriever` or the configured retrieval
backend (`devtools/doc_retrieval_config.yaml`).

## 6. Gap Implementation Plan

For each ❌ Gap in section 3, decide one of:

- **Workaround in notebook** — implement in Python, document the limitation.
- **NeqSim Improvement Proposal (NIP)** — specify the new Java class and tests
  in `neqsim_improvements.md`. Triggered automatically into a GitHub issue
  when the PR merges (see `.github/workflows/task_nip_issues.yml`).

| # | Gap | Decision | Owner | Effort |
|---|---|---|---|---|
| | | workaround / NIP | agent / human | hours |

## 7. Acceptance Criteria

How do we know the capability assessment is correct? At least one of:

- Notebook runs end-to-end on the planned NeqSim API surface.
- Benchmark notebook reproduces an independent reference (NIST, textbook,
  published case).
- All standards listed in section 1 have an entry in `results.json`
  `standards_applied`.

## 8. Decision Log

| Date | Decision | Rationale |
|---|---|---|
| | | |
