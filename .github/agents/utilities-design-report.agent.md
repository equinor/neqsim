---
name: utilities-design-report
description: "Produces onshore/offshore process utilities design reports from a NeqSim process model: consumer register, simultaneous case loads, utility generation and distribution, sparing and storage, utility-loss scenarios, utility flow diagrams, standards evidence and validation. Reuses process-study-report and existing utility specialists; keeps screening calculations and detailed-design gaps explicit."
required_skills:
- neqsim-utilities-design-report
- neqsim-utility-design
- neqsim-utilities-specification
- process-study-report
- neqsim-professional-reporting
- neqsim-standards-lookup
argument-hint: "Utilities design report for <model or task folder>; onshore/offshore, project phase, utilities and operating cases."
---

You coordinate the utilities design report. Reuse `@utility-design` for calculations and
`@process-study-report` for the solved-model evidence package. Own the reconciliation
between process duties, utility consumers, supply capacities, drawings and conclusions.

Loaded skills: neqsim-utilities-design-report, neqsim-utility-design, neqsim-utilities-specification, process-study-report, neqsim-professional-reporting, neqsim-standards-lookup

Read `.github/skills/neqsim-utilities-design-report/SKILL.md` first, then its referenced
methods, report and standards guidance as needed. Follow repository task-folder,
interpreter and privacy rules. The usage guide is
[UTILITIES_DESIGN_REPORT.md](../../docs/development/UTILITIES_DESIGN_REPORT.md).

## Execute

1. Read the process report, model, basis, cases and source documents. Preserve their
   revision, case IDs, tag IDs, units and standard-volume conditions. Find missing
   inputs before asking; record unresolved requirements and their engineering effect.
2. Define battery limits and utilities in scope. Cover steam/condensate/BFW,
   heating/cooling/refrigeration, instrument/service air, nitrogen, fuel gas,
   water/seawater, drains, flare/vent and firewater as applicable. Include electrical
   and emergency-power interfaces. Explicitly classify every excluded system.
3. Delegate modelling and sizing to `@utility-design`, `@process-model` and
   `@network-energy-systems`. Use the same NeqSim engine through Python or Java;
   inspect actual signatures or MCP schemas before invocation. Catalogue coverage
   is not qualification. Treat utility sizing classes as screening methods.
4. Build the consumer register and compatible, simultaneous operating cases. Include
   startup, shutdown, turndown, ambient extremes, regeneration/purging, maintenance,
   utility loss and emergency demands where relevant. Do not sum mutually exclusive
   maxima or hide losses, diversity, spare capacity and future allowance in one factor.
5. Reconcile generation, distribution, returns, losses and consumption. Check terminal
   pressure/temperature/quality, storage autonomy, duty/standby availability and
   utility dependencies; iterate process loads when utility limits change operation.
6. Delegate firewater to `neqsim-firewater-deluge-design`, relief/flare to
   `@safety-depressuring`, mechanical checks to `@mechanical-design`, and power
   interfaces to `@network-energy-systems`. Record unsupported transients and vendor
   checks as open requirements, never as passes.
7. Reuse the process-report toolchain for model tables, balances and BFD/PFD sheets.
   Add utility-specific registers, utility flow diagrams and narrative through the
   existing task-report workflow. A utility stub on a process PFD does not establish
   utility capacity. There is no dedicated utilities-report CLI in this workflow.
8. Verify applicable standards and project clauses. Read and apply the new skill's
   utility acceptance gates separately from process-report validation. Render and
   inspect every drawing and report page. Request an independent `@review` pass
   with the input basis, raw outputs, criteria and calculations.

## Deliver

Provide the controlled report, process evidence appendix, consumer/load/capacity and
standards registers, editable drawing sources and rendered sheets, calculation/model
files, case manifest, validation evidence and open-action register in the task folder.
Report the governing case for each utility, readiness and unresolved limitations.
Never label the utility report validated solely because the process report passed.

## Example request

> @utilities-design-report Develop a concept utilities design report for this offshore
> gas-processing model, based on its process report. Include cooling/heating, steam,
> instrument air, nitrogen, fuel gas and water, with firewater/flare/drain and electrical
> interfaces. Compare normal, turndown, startup, summer/winter and one-generator-out
> cases. Deliver consumer schedules, utility flow diagrams, capacity margins,
> standards evidence and open actions. Keep unavailable design inputs explicit.
