---
name: neqsim-plant-layout-study
version: "1.0.0"
description: "Orchestrate onshore and offshore physical plant-layout screening from explicit equipment envelopes and NeqSim process data to checked alternatives, drawings, CAD and a report. USE WHEN: plot plans, topsides/deck arrangements, plant layouts or a layout study report are requested."
last_verified: "2026-10-25"
---

# Plant layout study

Use `neqsim layout-study` through `@plant-layout-study`. Companions are `neqsim-plant-layout-geometry` and
`neqsim-plant-layout-delivery`. Read [the implementation guide](../../../docs/development/PLANT_LAYOUT_STUDY.md).
The workflow owns physical layout; process simulation stays with existing process agents and the process-report dataset.

## Scope and inputs

Create or resume the task folder. Read the decision, jurisdiction, facility type, maturity, coordinate frame/datum,
plot/decks, equipment schedule, topology, existing geometry, vendor/sizing evidence, maintenance/materials handling,
escape reservations, standards register and acceptance criteria. Ask only for missing information that changes the result.
Keep unanswered items in `data_gaps`; assumptions are explicit and traceable.

The `plant_layout_study.v1` model uses metres and kilograms. Equipment requires dimensions and their source, allowed decks,
orientations, optional locked positions and separate envelopes. Process import preserves stream identities and connectivity;
it never invents package dimensions or interprets a schematic symbol as a scaled footprint.
Reuse `ProcessDiagramGraphAdapter` and `EngineeringGraph` for semantic identities where available, and numeric
`SystemMechanicalDesign` / `MechanicalDesign` summaries for explicitly preliminary dimensions. Check
`SystemMechanicalDesignResult` completeness and per-equipment outcomes before accepting calculated geometry.

## Execute

- Initialize a task-local specification with `neqsim layout-study --init DIR`.
- Generate and inspect synthetic examples using `--demo onshore` or `--demo offshore`, with explicit `--out DIR`.
- Run the specification with `neqsim layout-study study.yaml --out DIR`; use `--strict` when complete required evidence is
  an acceptance gate. Read command help for supported stages and process-data input.
- Preserve dataset fingerprints and source snapshots. A stale stage cache must be regenerated before use.
- Reconcile process tags, coordinates and connection metrics. A centre-to-centre length proxy is not installed piping MTO.
- Review checks, CAD adapter availability/losses, drawings and report before handoff to `@plant-layout-review`.

## Standards and maturity

Establish applicability and the adopted edition. Candidate sources include Havtil regulations where in scope; NORSOK
S-001, S-002, L-002, P-002 and relevant C/R standards; ISO 13702/17776 for applicable offshore installations;
IEC 60079-10-1 for its hazardous-area scope; API RP 752/753/756 for applicable onshore buildings/tents.
Add LNG, storage, flare, electrical, structural and marine/class sources where relevant.
Use `neqsim-standards-lookup` and inspected authorized text. Public metadata cannot establish a spacing requirement.
Mandatory law prevails over project decisions; no project deviation can waive statutory obligations.

Software checks return PASS, FAIL, NOT_EVALUATED or NOT_APPLICABLE with evidence. An alternative is INFEASIBLE on required
failure and INCOMPLETE on missing required evidence. FEASIBLE means feasible against the supplied modeled criteria only;
it is not design approval or a statement of full standard compliance. Demonstrations remain SCREENING and synthetic.

## Handoffs and failure behavior

Invalid geometry/units/tags stop input processing. Failed geometric alternatives stay visible. Missing required hazard,
structural, vendor or marine evidence remains unassessed; do not relabel it PASS to make strict mode succeed.
Use process/mechanical/safety/standards specialists and optional community/enterprise overlays through current catalogs.
Do not commit project reports, plant data or licensed standards to public repositories.
