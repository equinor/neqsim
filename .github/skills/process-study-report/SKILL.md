---
name: process-study-report
version: "1.0.0"
description: "Produces a complete, validated engineering process-study report (Word + PDF) from a solved NeqSim ProcessModel or ProcessSystem: engineering basis, all-stream/all-case tables, balances, BFD and A3 PFD sheets (SVG/PDF), standards register (ISO 10628, NORSOK P-002) and a fail-closed validation report. USE WHEN: a task asks for a process study report, design basis, heat and material balance, stream tables, PFD/BFD drawings or a reviewable model documentation package from NeqSim."
last_verified: "2026-10-20"
requires:
  python_packages: [python-docx, matplotlib, pymupdf, openpyxl, pyyaml, jpype1]
---

# Process-study report

Turns a **solved NeqSim model** into a reviewable report package: basis, complete stream and
composition tables for every operating case, material/component/energy balances, a block flow
diagram (BFD), A3 PFD sheets with stream numbers, a standards register, appendices and a
machine-readable validation report. The toolkit lives in `devtools/` (flat modules, no extra
environment): `psr_extract`, `psr_balances`, `psr_basis`, `psr_diagrams`, `psr_report`,
`psr_validate`, `process_study_report` (CLI and `run_pipeline`), `psr_demo` (synthetic fixture).

## When to use

- A task needs a **process study / design basis / HMB / stream-table report** of a NeqSim flowsheet
  (single `ProcessSystem` or multi-area `ProcessModel`), for one or several operating cases.
- A reviewer needs **PFD/BFD drawings that match the simulated topology** and stream tables that
  match the drawings.
- Not for a quick single-flash answer (use the `mcp_neqsim_*` tools) and not for the generic task
  report of `neqsim report <task>` (see *Relation to the existing report toolchain*).

## Pipeline (do not skip steps)

| Step | What | Code |
|---|---|---|
| 1 Basis | Fill `basis.yaml` BEFORE writing: objective, audience, maturity, boundaries, cases, acceptance criteria, sources inspected, model/EOS/characterisation, conventions, data classification (measured / specified / calculated / estimated / unavailable), assumptions, exclusions, data gaps with effect on conclusions. Unknown = explicit `unavailable` entry, never invented. | `templates/basis_template.yaml`, `psr_basis.check_basis` |
| 2 Extract | One solved model per operating case -> normalised data; stream numbers `S001...` assigned deterministically (area order, unit order, inlets before outlets); all units, streams, components, pseudo-component properties. | `psr_extract.extract_process`, `merge_cases`, `write_outputs` |
| 3 Balances | Per unit, per area and overall: mass, component, energy (`sum(H_out)-sum(H_in)=Q+W`); PASS/FAIL against stated tolerances. | `psr_balances.compute_balances` |
| 4 Diagrams | BFD + PFD sheets from the topology, off-page connectors with the other drawing number, equipment data lines, legend, title block, SVG + PDF + PNG + layout spec JSON. | `psr_diagrams.build_bfd/build_pfd` |
| 5 Report | Word document (cover, control page, contents, exec summary ... appendices); fields updated and PDF exported through Word when available. | `psr_report.ReportBuilder` |
| 6 Validate | Fail-closed checks written to `validation_report.json/.md` and appended to the report. | `psr_validate.validate_all` |
| 7 Look | Render the PDF/PNG pages and **view them**; fix overlaps, tiny text, spaghetti. | `psr_report.render_pdf_pages` |

```python
import psr_extract, process_study_report as psr, psr_basis
d_a = psr_extract.extract_process(solved_model_a, "A", "Base case", model_name="my model")
d_b = psr_extract.extract_process(solved_model_b, "B", "High rate", model_name="my model")
data = psr_extract.merge_cases([d_a, d_b])            # raises if topology differs between cases
basis = psr_basis.load_structured("basis.yaml")
standards = psr_basis.load_structured("standards_register.yaml")["standards"]
summary = psr.run_pipeline(data, basis, standards, "out", use_word=True)   # deliverables under out/
```

CLI: `<python> -m process_study_report init-basis DIR` (copy templates),
`... build --data process_data.json --basis basis.yaml --standards standards.yaml --out OUT [--no-word]`,
`... validate ...` (pre-build checks only), `... demo OUT` (synthetic end-to-end example, needs the JVM).
Run from `devtools/` or with it on `PYTHONPATH`; interpreter: the NeqSim venv python of the repo
instructions (never create environments).

Outputs: `OUT/data/` (`process_data.json`, `.xlsx`, `stream_properties.csv`, `stream_components.csv`
= component flows for every stream x component x case, `stream_phase_compositions.csv`, `equipment.csv`,
`components.csv`, `balances.json`), `OUT/diagrams/{bfd,pfd}/` (SVG sheets, PDF set, PNG previews,
`*_layout_spec.json` = editable source), `OUT/report/` (docx, PDF, `report_facts.json`,
`validation_report.*`), `OUT/figures/`.

## Conventions the toolkit enforces

- **Absolute pressure** in all tables (bara); barg only as `bara - 1.01325`. Standard conditions
  15 degC and 1.01325 bara by default, stated in basis and tables; both flash standard volumes
  (gas/oil/aqueous) and ideal-gas standard volume are given. Heat added to the fluid and shaft work
  added to the fluid are positive. Phase fractions are mole fractions (beta); mass fractions and
  actual-volume fractions are derived. Enthalpy is on the NeqSim reference (relative only).
- **Value classes in tables:** `0` exact zero; `<1e-6` trace; `n/a` not available from the model
  (None); streams whose solver fluid has no moles are flagged `zero_flow` and shown `n/a`, never 0.
- **Every narrative number is a token** resolved from data: `[[stream:S067:mass_kg_h:A]]`,
  `[[unit:K-100:power_kw:A|.1f]]`, `[[balance:overall:A:mass_rel|.2e]]`, `[[fact:power_total_A]]`
  (dotted keys ok, e.g. `std_flash_m3_h.oil`). Unresolved tokens fail validation; the executive
  summary is checked number by number against facts, data and basis.
- **Unit categories** (`psr_extract.classify`): valve, mixer, splitter, separator (incl. scrubbers),
  cooler, heater, exchanger, compressor, pump, expander, ejector, column, pipe, reactor, recycle
  (numerical convergence element, drawn as a dashed `R`), logic (not drawn), other.
- `known_balance_exceptions` in the basis documents unit/area balance failures that are understood
  (e.g. isothermal re-flash of scrubber outlets, component splitters): they turn FAIL into WARN
  only when an explanation exists; undocumented failures stay FAIL.

## Standards (verified 2026-10-20; re-verify before every issue)

See `references/standards_verification.md` and `templates/standards_register_template.yaml`.

| Reference | Edition / status | What can be claimed |
|---|---|---|
| ISO 10628-1 | 2014, current per reseller listing (iso.org page not retrievable) | Conventions applied in spirit; text paywalled -> no conformance claim |
| ISO 10628-2 | 2012, current per reseller listing; collective application of ISO 14617 | Schematic symbols only; no conformance claim |
| ISO 14617-1 | 2005 | Informative |
| NORSOK P-002 | 2023+AC:2024 (edition 2 with the 2024 corrigendum; supersedes P-002:2023); Standards Norway sector page and a licensed copy confirm; it does not cite ISO 10628 | Applicability only; no compliance claim without line list, design pressures, safety-system data |
| DEXPI | 2.0.1 (not an ISO/IEC standard) | Optional exchange |
| IEC 62424 | edition **not verified** | Do not cite an edition |

Mark each row `mandatory` (project/company/regulatory requirement), `recommended` or `informative`.
`text_available: false` forbids unqualified "complies" wording (validator `STD-OVERCLAIM`). Company
requirement sets (for example a PFD/P&ID technical requirement) go in `requirement_matrices`
(item, paraphrased requirement, status met / partially met / not met / not applicable / not assessed,
evidence) and are rendered in Appendix G.

## Diagrams

Evidence for building our own generator: the native NeqSim renderer
(`EngineeringDiagramDelivery` + `NativeEngineeringDiagramRenderer`) produces generic boxes labelled
EQUIPMENT in one row, no stream numbers, overlapping labels and no ISO-style symbols; it is kept for
DEXPI/document-set export but not used for the report drawings. `psr_diagrams` implements a layered
(Sugiyama-style) layout with orthogonal channel routing, lane-routed recycles, stream-number diamonds,
off-page connectors (other sheet **and** drawing number), per-equipment data lines
(`equipment_data`), utility stubs (generic, medium not named), legend, notes, title block and a
"not modelled" note. Parameters: `max_units_per_sheet`, `sheets=[{title, areas}]`,
`layout_overrides={tag: {x, y}}`, `title_block` (`case_note`, `utility_note`, `omitted_systems`,
`revision_history`, `readiness_note`). The SVG sheets are the editable source (Inkscape etc.);
`pfd_layout_spec.json` allows regeneration. Symbols follow ISO 10628-2 / ISO 14617 conventions in
spirit; **never** claim conformance. Always render and look (see Step 7); check text at A3 print size
with a 200 dpi crop.

## Validation checks (all must be PASS or explained WARN)

Basis complete; standards register complete and no overclaim; standard conditions match; every stream
and unit has results for every case; topology and unique labels; composition sums (overall and per
phase); raw phase-fraction normalisation (WARN); zero-flow/undefined compositions shown n/a (WARN);
overall / area / unit balances; every stream and tag on a PFD, nothing extra; no layout warnings; BFD
present; exports complete (component-flow rows = streams x components x cases); no placeholder text;
no nan/inf/None tokens; stream register, properties and composition tables complete per case;
equipment register complete; every pseudo-component documented; figure alt text and captions;
standards in report; readiness stated; executive-summary numbers traceable; references resolved.

## Relation to the existing report toolchain

`neqsim report <task>` (generate_report.py, driven by `results.json` and
`step3_report/report_sections.json`) writes the generic task report. This skill adds what that
generator is not built for: 100+ page all-stream tables, A3 landscape drawings, balances and a
validation gate. It reuses `report_kit` helpers (`numbers_in_text`, `_same_number`) for number
traceability, uses the same title/readiness conventions and writes a `process_study_report` block
into `results.json` (paths, validation status) so that the task report can cite it. Use both: the
generic report for the narrative of a task, this package as the controlled technical report/appendix.

## Limitations (state them in every report)

- Topology is what the solved model contains; plant systems that are not modelled must be listed in
  `boundaries.unmodelled_systems` (printed on every sheet).
- Streams consumed by several units are counted once per area in area balances (validator warns).
- Layout is heuristic: some line crossings and sparse sheets remain; always inspect the images.
- Utility media, drivers, sparing, vessel sizes and control loops appear only if the model has them.
- Word field update and PDF export need Microsoft Word (COM); without Word the contents/list fields
  carry a static list and no PDF is produced.

## Related

`neqsim-professional-reporting` (report hygiene and `results.json`), `neqsim-task-workflow` (phase
6 report), `neqsim-standards-lookup`, `neqsim-pid-process-operations`,
`neqsim-document-intelligence-extraction`, agents `solve-task`, `process-model`, `solve-process`.
