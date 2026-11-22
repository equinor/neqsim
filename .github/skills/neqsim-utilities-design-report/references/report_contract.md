# Utility design report contract

Keep main-process document control, structure, conventions, sources and readiness. Extend its
evidence rather than replacing balances or the drawing engine.

## Canonical task data

The asset YAML is an authoring convention, not a supported NeqSim CLI/extractor schema. Study
scripts must read/validate it explicitly if used. JSON/CSV equivalents are acceptable with the
same semantics; retain existing supported process-report input formats.

| Record | Minimum contents |
|---|---|
| Study/basis | Objective, plant type, jurisdiction, phase, boundary, model revision, pressure/gas reference conditions |
| Sources | ID, document/model revision, locator, inspected status, classification and allowed use |
| Systems/headers | Medium/level, supply/return, operating/design limits, quality, source/consumer boundaries |
| Consumers | ID, utility, process tag, area, demand/case/unit/basis, conditions/quality, duration/profile, criticality, source |
| Cases/events | Process-case/variant ID, active users, ambient/intake data, outages, shedding, sequence, duration |
| Supply/storage/distribution | Installed/available ratings, corrections, sparing/dependencies, storage states, network evidence |
| Calculations | Method/version, inputs/outputs, demand, losses, allowances, recovery, capacity, residuals, governing cases |
| Requirements/qualification | Source/locator, assessment status/evidence; method qualification/limits |
| Findings/actions | Assumptions, gaps, shortages, effect, mitigation, owner and closure evidence |

Source every numerical input, including synthetic/estimated inputs. Distinguish specified,
measured, calculated, estimated, synthetic and unavailable. Zero requires demonstrated inactivity
and rationale; unknowns remain null and cannot silently disappear from totals.

## Report chapters

1. Executive decision summary: governing demand/capacity, limiting users, loss findings,
   readiness, unresolved actions and supported decisions.
2. Scope, facility, battery limits and interface matrix to the main-process report.
3. Basis: environmental/design cases, reliability, conventions, input/source quality,
   project requirements and standards applicability/assessment matrix.
4. Main-process duty reconciliation and consumer register, including package/non-process loads.
5. Generation, distribution and storage philosophy per medium/level.
6. Case balances/capacity: raw/simultaneous/design loads, losses/recovery/allowances,
   available supply, governing conditions, outage margins and annual usage.
7. Operability: turndown, controls, start-up/shutdown, maintenance, loss/restoration,
   shedding and essential/emergency interfaces.
8. Safety, environment and reliability; discipline hand-offs and qualification limits.
9. Alternatives, heat integration, energy/emissions/cost with factors and uncertainty.
10. Conclusions, recommendations and action register with closure evidence.
11. Appendices: utility drawings, process package, all-case schedules, equipment/datasheets,
    calculations, benchmark evidence, validation, requirements and reproduction instructions.

Adjust depth to scope; retain explicit status for excluded/unassessed chapters. Every
conclusion-critical requirement needs evidence or an open action. Nameplate totals, solver
convergence and document rendering do not substitute for design acceptance.

## Drawings and schedules

Generate BFD/UFD and header distribution drawings from canonical connections. Retain editable
source, printable vector/PDF and PNG previews. Show sources, generation, storage, distribution,
users, return/disposal, package/isolation boundaries, tags, direction, levels, units, source/case
references, legend and controlled title block. Do not invent dimensions, instruments, valve
states or protection functions. Separate conceptual items from verified connections and list
omitted systems. A schematic UFD is not a compliant P&ID or construction drawing.

Include consumer schedules, demand/capacity by header/case, equipment/standby register, storage/
autonomy calculations, interface/line schedules where data exist, and a utility-loss dependency
matrix. Datasheets distinguish rated/design/operating values and vendor-data status. Plotted
contributors and demand/capacity envelopes must reconcile with schedules. Keep static fallbacks.

Inspect all pages/drawings at intended print size for topology, labels, symbols, off-page
references, tables and legends. Fix overlap/clipping and rerender; record inspected revisions.

## Integration and validation record

Use existing generic-report `tables`, `figures`, `key_results`, `evidence_matrix`, assumptions/
gaps, `limitations` and `reproducibility` fields as documented for the task schema. Author text
in `step3_report/report_sections.json`; link unchanged process-report artifacts and validation.
Custom utility working files are additional evidence, not new supported CLI inputs.

Store utility checks in a task-local validation artifact with check ID, scope/case, criterion/
source, observed value/unit, method, evidence and status. Cover register completeness,
aggregation, capacity/quality, storage, hydraulics, safety interfaces, provenance, standards,
traceability and visual inspection. A failed/unassessed conclusion-critical check blocks design
adequacy. Preserve exceptions with size, effect and disposition. Deliver actual artifact paths,
versions and executed rerun commands; do not claim process-report automatically generated or
validated utility annexes.
