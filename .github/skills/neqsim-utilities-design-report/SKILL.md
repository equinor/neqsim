---
name: neqsim-utilities-design-report
version: "1.0.0"
description: "Develops a traceable process utilities design report for onshore plants and offshore facilities by extending process-study-report. USE WHEN: a utilities design report, utility balance, consumer register, utility flow diagrams, generation/distribution capacity assessment or utility-loss study is requested. Covers heating/steam, cooling/refrigeration, air/nitrogen, fuel gas, water, drains and safety/electrical interfaces; calculation methods remain in existing utility specialists."
last_verified: "2026-10-25"
---

# Process utilities design report

Reuse [process-study-report](../process-study-report/SKILL.md) for solved-model evidence,
stream tables, balances, BFD/PFD sheets, document control and validation. Add utility consumer
schedules, case-dependent balances, generation/distribution assessment and utility drawings.
Use [neqsim-professional-reporting](../neqsim-professional-reporting/SKILL.md) for the task narrative
and appendices. This skill is an orchestration and engineering contract, not a new report CLI,
automatic utility extractor or detailed-design certification engine.

Read [methods](references/methods.md) before calculating, [report contract](references/report_contract.md)
before writing and [standards](references/standards.md) before selecting requirements. The
[synthetic example](assets/utility_study_example.yaml) and
[consumer template](assets/utility_consumers_template.csv) are task-local authoring aids;
no existing CLI automatically reads their utility-specific fields.

## 1. Establish scope and evidence

Read the task, main-process report/model, basis, cases and validation. Preserve model revision,
case IDs, equipment tags and source references. Resolve plant type, jurisdiction, project phase,
battery limits and utility supply/return conditions from sources. Distinguish operating, design
and mechanical limits. Unknowns stay null/unavailable with their effect, owner and closure action;
a missing consumer is never zero. Include non-process/package users (HVAC, buildings, workshops,
accommodation, marine systems) when inside the boundary. Assess offshore space/weight, motion,
seawater intake, corrosion and logistics when applicable.

Give each utility a scope status: included, external interface, excluded or unavailable.

| System | Report coverage | Existing specialist |
|---|---|---|
| Steam, condensate, feedwater, heating medium | Levels, recovery, make-up, blowdown, temperature grade, standby supply | `@utility-design`, `neqsim-heat-integration`, `neqsim-power-generation` |
| Closed/seawater cooling, air cooling, refrigeration | Seasonal ambient/intake envelope, approach, rejection heat, fouling, pumping | `neqsim-utility-design`, `neqsim-heat-integration` |
| Instrument/plant air, nitrogen | Quality/pressure, continuous/intermittent loads, receivers, storage, start-up/purges | `@utility-design`, `neqsim-utilities-specification` |
| Fuel gas/alternative fuels | Composition envelope, pressure, heating-value/Wobbe basis, dew points, start-up supply | `neqsim-power-generation`, `neqsim-process-modeling` |
| Fresh/service/potable/demineralized water | Quality/treatment, consumption, storage, make-up, discharge, autonomy | Applicable water specialist/vendor evidence |
| Open/closed drains, vents, flare | Segregation, collection/recovery, boundary loads, coincident scenarios | `neqsim-relief-flare-network`, `@safety-depressuring` |
| Firewater/foam | Approved scenarios, simultaneous demand, duration, hydraulics, coverage, availability | `neqsim-firewater-deluge-design`, fire-protection specialist |
| Electrical/HVAC/package interfaces | Shaft/electrical loads, losses, essential/emergency users, heat rejection | `@network-energy-systems`, `neqsim-energy-systems-and-electrification`, `@mechanical-design` |

Existing sizer defaults are screening assumptions, not requirements. Verify every numerical limit
and standard designation against its applicable source. Never automatically connect nitrogen to
instrument air or assign purge/firewater rates and autonomy.

## 2. Build consumers and simultaneous cases

Assign each physical demand a unique consumer ID and utility/header ID. Record process tag, area,
case, demand/unit/basis, supply/return conditions, minimum consumer pressure, quality,
duration/profile, criticality, source locator and input classification. For calculated loads,
record the extraction path/method and model revision. Include infrastructure demands absent from
the process model. Map heater/cooler duties to identified utility media, not temperature alone.
Exclude internal heat recovery from external demand. Keep net process duty, network losses and
generation efficiency separate; include utility-plant self-consumption and coupling where material.

Define credible simultaneous normal, high/low throughput, turndown, hot/cold ambient/intake,
start-up/shutdown, purge/regeneration, maintenance/outage, utility loss, blackout and restart
cases as applicable. State overlap, active users and shedding. Keep governing case per header
and quantity; unrelated individual maxima do not form a simultaneous case. Short-duration peak
profiles cannot be replaced by hourly averages.

## 3. Calculate and qualify

Delegate deterministic calculations to existing specialists. Discover current MCP schemas or
verify Java signatures before use. Python and Java must call the same NeqSim engine with
equivalent inputs, units, EOS and settings; source tasks use the repository development setup.
Keep `ProcessSystem` areas composable within `ProcessModel` and expose reusable streams/boundaries.

Existing utility sizers cover screening boiler/deaerator/steam-network, refrigeration and nitrogen
calculations. Sizer output alone does not establish header hydraulics, vendor capacity, air
quality, transient storage, firewater adequacy or electrical protection. Use qualified external
calculations when necessary and preserve provenance, qualification and unresolved interfaces.
Follow [methods](references/methods.md) for calculations and independent checks.

Compare simultaneous demand with available generation, storage and distribution at required
consumer conditions. Assess project-defined outages, site/vendor derating, duty/standby
arrangements, transfer delay, shared dependencies and common-cause failures. Separate operating
spare, design allowance, future expansion and uncertainty; apply each once with a source. Report
shortages/limiting consumers even when installed nameplate capacity exceeds total demand.

## 4. Produce the coordinated report

Run the documented `neqsim process-report` workflow on verified process/utility models using its
supported basis/case formats. Topology must remain identical within one run; use separate
documented model variants for topology-changing outage/start-up configurations. Preserve and
link the main-process package, case IDs and validation.

The base generator does not automatically turn duty stubs into consumer schedules, utility
balances or utility flow diagrams. Generate these task-local artifacts explicitly from the same
utility dataset using verified plotting/CAD/report tools. Attach their tables, figures and
conclusions through existing task-report `results.json` and
`step3_report/report_sections.json` contracts; use `neqsim report <task>` for the narrative and
the process-report package as the controlled technical appendix. Do not invent `utilities-report`
commands, new token kinds, flags or extractor fields.

Follow the [report contract](references/report_contract.md). Draw actual model/register
connections, supply/return headers, pressure levels, package boundaries and off-page references.
Label unmodelled connections and conceptual controls. P&ID drafts require explicit instrument/
valve data and separate verification; a UFD is not an issued P&ID. Keep report numbers, tables,
drawings and narrative traceable to the canonical dataset and source revision.

## 5. Validate and release honestly

**Flow-diagram gate:** utility flow diagrams and block diagrams pass `neqsim pfd-check` (skill `neqsim-pfd-layout-review`,
agent `pfd-layout-review`) before release.

Apply process-report validation without weakening checks. Additionally check:

- Unique IDs; every consumer covered in each applicable case; unknown distinct from zero.
- Units/gas-reference conditions; quality, pressure and temperature at the consumer.
- Independent demand/mass/energy reconciliation; recovery/efficiency/loss accounting.
- Simultaneity, sparing, derating, storage duration and unavailable-source scenarios.
- No duplicated duties, spare drivers, recovery, purge campaigns or allowances.
- Method range, sensitivity, independent checks and vendor/measurement comparisons.
- Standards applicability/evidence, safety interfaces, assumptions and gaps.
- Every table, figure and drawing rendered/viewed for topology, revision and legibility.

Record PASS, FAIL, WARN, NOT ASSESSED or NOT APPLICABLE with evidence. Unknown capacity or
unqualified safety analysis cannot pass. Keep readiness no higher than the weakest
conclusion-critical input/method: synthetic inputs and screening sizers support SCREENING only.
Design release requires project-approved basis, qualified methods, closed interfaces and
accountable review. Hand over exact paths/rerun steps, validation/readiness, governing demands
and margins, open actions and drawing inspection results.
