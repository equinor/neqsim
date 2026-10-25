---
name: neqsim-surf-design-study
version: "1.0.0"
description: "End-to-end SURF (subsea umbilicals, risers, flowlines) design study: request plus structured inputs become a generated, solved NeqSim field network, concept comparison with feasibility before cost, checks with explicit maturity, drawings, MTO/cost and a validated report via `neqsim surf-study`. USE WHEN: a subsea tieback, template cluster, daisy chain, injection distribution or SURF design report is requested."
last_verified: "2026-10-25"
requires:
  python_packages: [python-docx, matplotlib, pymupdf, pyyaml, jpype1]
---

# SURF design study

Orchestration skill for a complete, reviewable SURF study. It owns the **workflow, gates and maturity language**; the
calculations stay with their owners (`LoopedPipeNetwork`/`FieldNetworkTopology`, `WellFlow`, `SubseaProductionSystem`,
`SURFCostEstimator`, the DNV-ST-F101 kernel). Focused companions: `neqsim-surf-model-generation` (specification to
NeqSim objects) and `neqsim-surf-report-drawings` (drawings, datasheets, report). Existing specialists are reused by hand-off:
`neqsim-field-development`, `neqsim-flow-assurance`, `neqsim-subsea-and-wells`, `neqsim-pipeline-and-terminal-networks`,
`neqsim-integrated-production-and-lifecycle`, `neqsim-pipeline-survey-processing`, mechanical design, `neqsim-standards-lookup`,
`neqsim-professional-reporting`, `process-study-report` and `neqsim-flow-assurance-study-report`.

SURF = subsea umbilicals, risers and flowlines, coordinated with trees, jumpers, manifolds/templates, PLETs/PLEMs, controls, chemical
distribution and host interfaces. **A report assembled around disconnected equipment counts, decorative sketches or typed-in
simulation results is not this workflow**: NeqSim must generate and solve the network.

## Workflow

scope/evidence -> alternatives and design basis -> generated SURF model -> case execution -> design assessment ->
quantities/cost/interfaces -> drawings/report -> independent checks and issue readiness.

| Step | Output | Failure behaviour |
|------|--------|-------------------|
| 1 Scope and evidence | task folder, sources, standards register, data classes | missing evidence is a declared assumption, a requested input or a hand-off - never a silent value |
| 2 Alternatives and basis | `study.yaml` (`surf_study.v1`): fluids, nodes, edges, cases, concepts, criteria | >= 2 concepts; a missing/conflicting input stops with exit 2 |
| 3 Model generation | operation plans, builders, topology JSON, mapping manifest | disconnected ports, conflicting OD/wall/ID, unknown IDs are rejected before any solve |
| 4 Case execution | one solve per concept/case, cached on the plan | non-convergence or a Darcy fallback makes the case unusable (criteria FAIL), never PASS |
| 5 Assessment | criteria, hydrate, erosion, cooldown, umbilical delivery, F101 kernel, hand checks | unassessed stays `NOT ASSESSED`; concepts are ranked on cost only if feasible in every case |
| 6 Quantities and cost | line/equipment/service registers, MTO, `SURFCostEstimator` | counts and lengths must reconcile with the design basis |
| 7 Drawings and report | 15 A3 sheets (SVG/PNG/PDF), 20-chapter DOCX/PDF, validation report | strict mode exits 1 on a FAIL; the label is at most SCREENING |
| 8 Independent checks | `@review`, specialist hand-offs `handoff/HO-*.json` | the toolkit never assigns REVIEWED |

## Quick start

```powershell
neqsim surf-study --init study_inputs                              # template + standards register; nothing is calculated
neqsim surf-study study.yaml --out surf_report --strict            # model -> solve -> report (about 10 minutes for the demo)
neqsim surf-study --demo --out demo                                # synthetic four-well, two-template example
neqsim surf-study study.yaml --out surf_report --stage report      # re-render from the retained dataset (refuses a stale one)
```

Without the `neqsim` command: `<python> -m neqsim_cli surf-study ...`. Stages: `build-model`, `run`, `report`, `all`.
Exit codes: 0 ok, 1 strict validation failure, 2 input or usage error (nothing is written).

## Rules that keep the study honest

- **Maturity states are separate**: numerical screening, supplier confirmation, specialist analysis, independent verification and approval. A polished report or a passing test promotes none of them.
- **Reuse, do not rebuild**: no new pressure-flow solver, optimizer, mechanical kernel, cost estimator or report framework. Hydraulics = `LoopedPipeNetwork` with per-edge `PipeModelType`; wells = live `WellFlow` through `FieldWellNetworkCoupler`; cost = `SubseaProductionSystem.estimateSurfCosts`.
- **Wells**: producers are solved (productivity-index inflow, VLP not modelled); injectors use the explicit liquid mass-injectivity contract. Do not use the legacy gas-standard-volume injection law for liquid injectivity.
- **Geometry**: hydraulic ID = OD - 2 x nominal wall; corrosion allowance is reported separately; installed length = route length x (1 + stated allowance) used once; elevations in metres above MSL.
- **Pressure/flow conventions**: absolute pressure (bara); edge rate positive from the from-node to the to-node; kg/s mass rates; standard and actual volumes are never mixed.
- **Hydrate/FA**: equilibrium onset is not plugging time; aqueous inhibitor mass fraction is not an overall mole fraction; cooldown is time to a temperature.
- **Structural**: DNV-ST-F101, on-bottom stability, free span, global buckling and pipe-soil are distinct checks with their own evidence. A U-value is not a soil resistance model. Riser shapes are `CONCEPT SCHEMATIC - GEOMETRY NOT ANALYZED` unless an external solver result is imported.
- **Standards**: public metadata only; cite a clause only after reading licensed text; project/company rules precede public standards; company templates, licensed tables and project data live in the enterprise/task layer.
- **Qualification gaps stay visible**: severe slugging, pigging dynamics, dynamic riser response, fatigue, seabed structural response, manufacturer umbilical and flexible-pipe construction are hand-offs, never numbers.

## Interview (ask only what cannot be found)

Read the task folder, existing models (`@field-development`), P&IDs/STID evidence and prior studies first. Then ask once for: decision and maturity,
host constraints, well/fluid basis, route and bathymetry (CRS, datums), line sizes and thermal layers, umbilical services, criteria and their sources,
adopted standards and editions, cost basis, and the document number and author.

## Pitfalls

- Passing `od_mm` without `wall_mm` or giving an `id_mm` that disagrees with OD - 2 x wall (rejected).
- A concept that is cheaper but fails a criterion must not be recommended; show it as infeasible.
- Mesh sensitivity: Beggs and Brill temperatures can move about 2 K between 8 and 16 increments on a 10 km line; keep the gate.
- SRK pure water has about 14 % higher heat capacity than water; use CPA/IAPWS for qualified thermal injection studies.
- Do not edit generic report generators; extend `surf_*` modules or the report sections JSON.

## Hand-offs

`@flow-assurance` (threats), `@mechanical-design` (F101 and structural inputs), `@field-development` (host and economics), `@standards-review`,
community `olga-simulation-agent` (transients), `enterprise-stid-agent`/`enterprise-subsea-infrastructure-agent` (enterprise overlays).
Independent review: `@review`.
