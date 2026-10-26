---
title: "Process Utilities Design Report"
description: "Agent workflow for onshore and offshore utilities design reports based on the NeqSim process-report evidence package, with consumer registers, simultaneous loads, utility flow diagrams and standards assessment."
---

# Process utilities design report

Use `@utilities-design-report` to prepare a utilities report from an existing
process report, model and design basis. The agent coordinates existing utility
modelling and reporting capabilities. It adds the consumer, case, supply,
distribution and safety-interface evidence needed to judge utility capacity.

This is an agent workflow, not a new command-line report generator. It reuses
the [process-study report](PROCESS_STUDY_REPORT.md) for model extraction, tables,
balances and BFD/PFD sheets, and the existing task report for the utility
narrative, registers and specialist appendices. A passed process-report check
does not validate utility generation, distribution or availability.

## Start the agent

Select the agent and give it the process-model or task-folder path, existing
process-report revision, facility type, project phase, utilities in scope and
the engineering decision. For example:

> @utilities-design-report Develop a concept utilities design report for this
> offshore gas-processing model based on its process report. Cover heating,
> cooling, steam/condensate, instrument air, nitrogen, fuel gas and water, with
> firewater, flare, drains and electrical interfaces. Compare normal, turndown,
> startup, summer/winter and one-generator-out cases. Provide consumer schedules,
> utility flow diagrams, capacity margins, standards evidence and open actions.

Missing project requirements remain visible data gaps. The agent searches the
task inputs and authorized sources before requesting information. Calculated
values come from reproducible models or documented calculations, not prose.

## How the agents work together

| Responsibility | Existing capability |
|---|---|
| Scope, case/consumer reconciliation, report and acceptance review | `@utilities-design-report` and `neqsim-utilities-design-report` |
| Utility levels and consumer quality requirements | `neqsim-utilities-specification` |
| Screening supply-system sizing | `@utility-design` and `neqsim-utility-design` |
| Process and utility flowsheets, Python or Java | `@process-model`, `@network-energy-systems` |
| Heat recovery and generation/electrical interfaces | `neqsim-heat-integration`, `neqsim-power-generation`, `neqsim-energy-systems-and-electrification` |
| Model tables, balances, BFD/PFD evidence | `@process-study-report` and `process-study-report` |
| Firewater, relief/flare and mechanical verification | `neqsim-firewater-deluge-design`, `@safety-depressuring`, `@mechanical-design` |
| Standards and independent review | `neqsim-standards-lookup`, `@standards-review`, `@review` |

Use installed community specialists when useful; authorized enterprise overlays
may add company requirements. Preserve capability identity, provenance and
licensing boundaries. Public examples contain synthetic data only.

## Required evidence

Keep the process report's case and tag IDs and record its revision. Identify
each utility consumer, header, return, load source, delivery conditions and
applicable operating cases. Distinguish zero load from an unavailable load.
Account explicitly for internal heat recovery, generation auxiliaries, losses,
intermittent consumers, duty/standby status and future allowance.

Evaluate compatible simultaneous demands. The governing case for cooling may
differ from that for air, nitrogen, steam or firewater. Do not add mutually
exclusive consumer maxima or treat a production-maximum case as a startup or
emergency case. Where topology differs, run a separately identified process
model/report package rather than violating the process-report same-topology
case contract.

Check the complete supply-to-consumer path, including minimum terminal
pressure, temperature grade, quality, return/backpressure and usable storage.
Loss-of-utility scenarios must include dependencies: electrical loss can also
remove cooling, instrument air, nitrogen generation or fuel-gas conditioning.
Availability requires evidence of independent supplies and auxiliaries, not
only a count of installed machines.

The skill contains [report content and drawing guidance](../../.github/skills/neqsim-utilities-design-report/SKILL.md)
and reusable input templates. Utility flow diagrams should show supply and
return headers, consumer tags, battery limits, flow direction, reference case,
legend, drawing number and revision. Distinguish calculated, specified,
estimated and unavailable data. Render and inspect every sheet and report page.
Concept P&IDs remain explicitly marked drafts until their discipline review.

## Standards

Reuse the main process-report standards workflow. Add a project applicability
and requirement matrix, using the skill's
[standards guide](../../.github/skills/neqsim-utilities-design-report/references/standards.md).
Candidate references include NORSOK P-002, ISO 10628 and ISO 14617 for process
design/drawings; ISO 8573-1 and ISA-7.0.01 for air quality; adopted piping and
pressure-equipment codes; API relief/flare standards; adopted firewater and
electrical standards; and actual water/discharge requirements.

The guide records official catalogue evidence checked on 2026-10-25, including
NORSOK P-002:2023+AC:2024 and ISO 14617-1:2025. Recheck editions and amendments
before issue and retain the project-adopted edition separately. Catalogue
verification is not clause verification. No fixed air pressure, nitrogen purity,
receiver duration, firewater demand or spare margin is prescribed by this workflow.

## Acceptance and delivery

Deliver the controlled utility report, process evidence appendix, case/consumer/
capacity registers, calculation files, standards assessment, editable diagrams,
rendered sheets, validation record and open-action list in the task folder.
Each utility conclusion names the governing case, criterion, source and result.

Apply the skill's utility gates in addition to process-report validation.
Unresolved failed criteria block an adequacy claim; missing requirements remain
unassessed and restrict readiness. Screening boiler, nitrogen and Carnot-fraction
refrigeration models do not establish vendor performance, detailed mechanical
design, transient response or installation certification. A reviewer must be
able to reproduce the values and see every limitation affecting the decision.

## Related

- [Process-study report](PROCESS_STUDY_REPORT.md)
- [Process engineering utilities](../process/engineering_utilities_v2.md)
- [Agent and skill map](AGENT_SKILL_MAP.md)
- [Capability discovery](AGENT_CAPABILITY_DISCOVERY.md)
