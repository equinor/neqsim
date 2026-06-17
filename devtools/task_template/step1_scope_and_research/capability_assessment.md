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
