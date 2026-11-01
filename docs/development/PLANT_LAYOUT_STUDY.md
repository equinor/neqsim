---
title: Plant Layout Study
description: "Checked concept layouts for onshore plants and offshore decks, with process import, explicit equipment envelopes, drawings, optional CAD and reports."
---

# Plant layout study

`neqsim layout-study` develops physical equipment arrangements for onshore plots and offshore decks from explicit
dimensions and project requirements. `@plant-layout-study` coordinates the workflow; `@plant-layout-review` independently
checks the output. The geometry is separate from the existing PFD/P&ID sheet layout.

## Supported first profile

The first profile uses rectangular plots/decks, orthogonal equipment boxes and separate maintenance/operation/lifting
envelopes. It generates up to three distinct alternatives, independently checks the saved placements, produces plot/deck
drawings and a study report, and uses optional CAD adapters for supported exchange formats.
It remains concept SCREENING. It does not establish complete plant-design qualification.

The process interface reuses [process-report data](PROCESS_STUDY_REPORT.md), stable equipment/stream identities and current
numeric mechanical-design summaries. Preliminary module dimensions remain estimates; vendor package geometry and
maintenance envelopes require their own evidence. Missing dimensions cannot be reconstructed from a PFD symbol.

## Entry points

Use the installed `neqsim` command, or the selected interpreter with `-m neqsim_cli` and the source `devtools` path.
`layout-study --init DIR` writes a task-local specification. `--demo onshore` and `--demo offshore` generate synthetic
examples; always select an output directory outside the source repository. Run a source YAML/JSON specification with
`--out DIR`. Command help lists supported stages, process-data import and strict validation.

The synthetic studies deliberately lack project hazard/structural qualification. Their expected outcome is INCOMPLETE
with passing modeled geometry where feasible and visible NOT_EVALUATED engineering checks. Strict validation must retain
those missing assessments rather than silently passing the examples.

## Model and provenance

The versioned contract is `plant_layout_study.v1`; its portable schema is
[`devtools/layout_templates/study.schema.json`](../../devtools/layout_templates/study.schema.json).
Runtime validation additionally checks references, finite numbers, unique identities and valid bounds.

| Model component | Evidence required |
| --- | --- |
| Project/frame | ID, revision, facility type, jurisdiction, metres/kg, origin, heading/north and elevation datum |
| Plot/decks | Extents/elevations and any supplied height or mass limits |
| Equipment | Tag, dimensions/provenance, allowed decks/orientations, optional fixed placement and separate envelopes |
| Connectivity | Source equipment/stream IDs and explicit import mapping; boundary and parallel streams retained |
| Physical inventory scope | Stable scope/revision, controlled source reference and required connection, equipment-volume and isolation-section IDs |
| Routed piping | Connection and line IDs, three-dimensional centreline points, internal diameter and revisioned physical-source status |
| Isolation inventory | Located isolation devices with explicit normal/fail states, contained equipment volumes and bounded isolation sections |
| Reservations | Corridor geometry, exits, obstacles and source of project assumptions |
| Rules | Stable ID, applicability, source/access, inspected edition/clause where applicable, units and method |
| Assessment | Saved coordinates, independent checks, solver settings/status, input fingerprint and artifact hashes |

Metres are the model length unit; CAD conversion is explicit. Dry/operating/test mass and CoG, construction/maintenance
loads and marine/structural inputs need separate qualified cases before design use. The initial mass rollup does not
prove structural adequacy or vessel stability.

### Physical piping and isolation evidence

The optional physical-inventory contract is additive to the arrangement model. `physical_inventory_scope` declares the
exact process connections, equipment tags and isolation sections that must be covered. `physical_routes` records actual
three-dimensional centreline points and internal diameters against existing connection IDs. `equipment_volumes` records
separately evidenced contained volumes. `isolation_devices` records route chainage, device type and explicit normal/fail
state; `isolation_sections` names the two or more boundary devices and the route/equipment-volume records they contain.

The toolkit calculates routed length as the sum of Euclidean centreline segments and pipe inventory as
$V=\pi D_i^2L/4$. Isolation-section inventory is the sum of referenced pipe and equipment volumes. A synthetic regression
uses a 5 m plus 12 m routed centreline with 2 m internal diameter: route volume is exactly $17\pi$ m³, and the section
total is $17\pi+3$ m³ after adding a separately supplied 3 m³ equipment volume.

Each physical record requires a source kind, document/model reference, revision and one of
`CONTROLLED_PROJECT_INPUT`, `REVIEW_REQUIRED` or `ASSUMPTION`. Required missing or uncontrolled evidence produces named
blockers and keeps `ready_for_dynamic_safeguarding_design` false. The study fingerprint includes every scope, route,
device, volume and section field, so a geometry or isolation revision invalidates retained calculations and requires a
rerun. This is traceable inventory evidence, not relief sizing, source-term prediction, SIL/IPL credit or design approval.
The toolkit never substitutes process connectivity or the centre-to-centre arrangement proxy for actual pipe length.

## Constraints and result interpretation

Check placement identity/completeness, allowed deck/orientation, boundaries and heights, physical 3D/obstacle collision,
maintenance obstruction, reserved escape corridors, supplied deck mass limits and source-qualified separation rules.
Corridor connectivity is a geometric screening check; it does not establish independent escape under common hazards,
escape capacity or evacuation time. Distances and connection-length proxies use the saved physical coordinates.

PASS means the supplied modeled check passed; FAIL means its requirement failed. NOT_EVALUATED means required evidence
or a supported method is absent, while NOT_APPLICABLE requires justified scope. Required failure makes an alternative
INFEASIBLE; missing required assessment makes it INCOMPLETE. FEASIBLE is limited to the supplied modeled criteria and
does not confer approval. Bounded placement search does not prove global optimality or full-geometry infeasibility.

## Standards basis

Record the legally/contractually adopted edition and amendments. Verify current publication metadata at study setup,
but do not automatically replace the adopted edition. Mandatory law prevails; project deviations cannot waive it.

| Candidate source | Applicability boundary |
| --- | --- |
| Havtil Facilities Regulations, including design and access/escape | Norwegian offshore facilities within regulatory scope |
| Havtil Technical and Operational Regulations | Applicable Norwegian onshore facilities; establish competent authority |
| NORSOK S-001:2020+AC:2021; S-002:2025+AC:2026 | Adopted technical-safety and working-environment requirements |
| NORSOK L-002:2016; P-002:2023+AC:2024 | Applicable piping layout and process/utility interfaces |
| ISO 13702:2024; ISO 17776:2016 | Applicable offshore fire/explosion and major-accident management |
| IEC 60079-10-1:2020 | Explosive gas/vapour hazardous-area classification within its scope |
| API RP 752/753/756 | Applicable onshore permanent buildings, portable buildings and tents, respectively |
| Relevant NORSOK C/R and service-specific standards | Architectural/lifting, LNG/storage, flare, electrical, structural and marine interfaces |

Publication metadata was checked on 25 October 2026. It does not establish detailed clause content or numerical
clearances. Numeric standard-derived rules require authorized inspected text, edition/clause and applicability.
Synthetic/project screening assumptions remain separately identified. Hazardous-area classification does not replace
major-accident fire/explosion/toxic dispersion or QRA analysis.

Primary metadata: [NORSOK safety](https://standard.no/en/sectors/petroleum/norsok-standards/s-safety-she/),
[piping/layout](https://standard.no/en/sectors/petroleum/norsok-standards/l-piping--layout/),
[process](https://standard.no/en/sectors/petroleum/norsok-standards/p-process/),
[ISO 13702](https://www.iso.org/standard/82438.html), [ISO 17776](https://www.iso.org/standard/63062.html),
[IEC 60079-10-1](https://webstore.iec.ch/en/publication/63327),
[API building/tent siting](https://www.api.org/news-policy-and-issues/news/2024/01/16/api-releases-new-updates-to-standards-for-hazard-management-in-process-plant-str),
[Havtil offshore design](https://www.havtil.no/en/regulations/all-acts/the-facilities-regulations3/II/5/) and
[access/escape](https://www.havtil.no/en/regulations/all-acts/the-facilities-regulations3/III/13/).

## Tools, delivery and verification

The Python tooling owns normalization, placement, independent checks, process import and orchestration. CAD adapters are
optional; their absence cannot be reported as successful export. Parametric source and canonical JSON remain authoritative.
Export metadata records supported objects, identifiers, dimensions/units and format losses. A GLB view is a tessellated
review representation, not a complete plant-design exchange contract.

Keep input snapshots, JSON alternatives/findings, physical-inventory JSON, route/isolation CSVs, coordinate/check CSVs,
vector drawings, report and supported CAD exports in the task output directory. Render every drawing/report page and
inspect tags, dimensions, scale, legends and title blocks. Reopen supported CAD output and compare identity/count/bounds
within declared tolerances.

HTML and machine-readable report evidence remain available without the PDF adapter. ReportLab supplies report PDFs;
CadQuery and ezdxf supply the optional CAD formats. Their versions are recorded in the dataset. Install optional tools
through the project's selected interpreter/environment policy. Missing requested artifacts yield a separate INCOMPLETE
delivery status and nonzero strict exit, even when all supplied geometric criteria pass. Explicit `--no-cad` records CAD
as not requested. DOCX is an optional editable companion; its absence does not substitute for the required PDF/HTML pair.

`devtools/test_layout_study.py` exercises geometric failures, source gating, schema/frame rules, deterministic alternatives,
stage-cache behavior and end-to-end examples where available. Test results establish the executed software scope only.

The initial 25 October 2026 validation ran 46 tests successfully, including reopened STEP/DXF/GLB checks. A dependency-free
run passed with one optional CAD test skipped. Tested adapters were CadQuery 2.7.0, ezdxf 1.4.4, ReportLab 4.4.9,
python-docx 1.2.0 and PyMuPDF 1.26.6. The synthetic reference package contains three alternatives each for an eight-package
onshore plant, a nine-package three-deck offshore module and a four-unit solved NeqSim process. The live process used SRK,
50 bara/318.15 K feed at 10,000 kg/h and 110 bara export pressure; relative mass residual was 3.42e-14. Physical package
dimensions remained explicitly synthetic. These checks do not qualify the missing hazard, vendor, structural or marine
assessments.

## Deferred engineering interfaces

The physical-inventory contract records supplied centreline geometry and volumes; it does not generate routes, size piping,
calculate pressure drop or perform flexibility/stress analysis. Hydraulic feedback, irregular terrain/structures, CFD/QRA
hazard contours, evacuation capacity/hazard independence, fire/blast loads, crane capacity/removal operations, full vendor
packages and floating-facility structural/marine qualification require explicit specialist adapters and evidence. IFC
federation and commercial CAD authoring profiles require separate exchange qualification. Track these gaps without
claiming them solved by box placement or inventory arithmetic.

Related workflows: [SURF design](SURF_DESIGN_STUDY.md), [process reports](PROCESS_STUDY_REPORT.md) and
[agent capability discovery](AGENT_CAPABILITY_DISCOVERY.md). This physical-layout increment supports the direction of
[issue #1864](https://github.com/equinor/neqsim/issues/1864) without claiming its complete exporter roadmap is closed.
