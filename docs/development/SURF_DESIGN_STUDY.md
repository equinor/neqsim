---
title: "SURF design study workflow"
description: "Generate, solve, assess and document a subsea SURF system with `neqsim surf-study`: specification, generated NeqSim network, concept comparison, drawings and a validated report."
---

# SURF design study workflow

`neqsim surf-study` turns a structured study specification (`surf_study.v1`) into a generated and solved NeqSim field
network, a concept comparison, 15 A3 drawings, quantities and cost, and a 20-chapter report with a fail-closed validation
report. The toolkit reuses `FieldNetworkTopology` over `LoopedPipeNetwork`, `FieldWellNetworkCoupler`,
`FieldWellNetworkProcessUnit`, `SubseaProductionSystem` and `SURFCostEstimator` (see
[SURF network integration](../fielddevelopment/SURF_NETWORK_INTEGRATION.md)) and the shared report kit of the
[flow-assurance study report](FLOW_ASSURANCE_STUDY_REPORT.md). It adds no solver.

The result is a **screening-level** deliverable. The software never assigns the REVIEWED label and never approves a design.

## Commands

<!-- parse -->
```powershell
neqsim surf-study --init study_inputs
neqsim surf-study study.yaml --out surf_report --strict
neqsim surf-study --demo --out demo --no-word
neqsim surf-study study.yaml --out surf_report --stage report --task my_task
```

Stages: `build-model` (validate, generate plans, builders and the mapping manifest), `run` (solve every concept and case, cached on
the generated plan), `report` (dataset, drawings, document, validation) and `all`. Exit codes: 0 ok, 1 strict validation failure,
2 input or usage error.

## Specification

<!-- run -->
```python
import sys
sys.path.insert(0, "devtools")
import surf_spec
spec, findings, model = surf_spec.load_spec("devtools/surf_examples/four_well_tieback/study.yaml")
assert not [f for f in findings if f["level"] == "error"]
assert len(spec["concepts"]) >= 2
```

Units are in key suffixes (`_barg` is rejected; use absolute `_bara`). Missing, `unavailable` or non-finite values stop the run
before anything is solved. The specification must contain at least two concepts, three cases, flexible-line supplier U-values and
acceptance criteria with sources.

## Output layout

`study_inputs/`, `model/` (builders, topology JSON, `mapping_manifest.json`), `cache/`, `dataset/`, `drawings/`, `figures/`,
`handoff/` (HO-01 to HO-08), `layout_spec.json`, the DOCX/PDF report and `validation_report.md`.

## Maturity

Numerical screening, supplier confirmation, specialist analysis, independent verification and approval are separate states.
Severe slugging, pigging dynamics, dynamic riser response, fatigue, seabed structural response and manufacturer umbilical or
flexible-pipe construction are hand-offs, not results. The riser sheet is a concept schematic, geometry not analysed.

## Related

[Agent skill map](AGENT_SKILL_MAP.md), the `surf-design-study` agent and the skills `neqsim-surf-design-study`,
`neqsim-surf-model-generation` and `neqsim-surf-report-drawings`.
