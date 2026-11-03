---
name: professional-process-overview
version: "1.0.0"
description: "Generates a professional one-sheet A3 process flow diagram (colour + monochrome, ISO 5457 / 7200 / 10628-2 conventions, embedded fonts, PDF + editable SVG) and a coordinated 16:9 presentation version from a solved NeqSim model or process_data.json, with detail sheets, a structured process graph and automatic validation. USE WHEN: a report, review or presentation needs the complete process on one overview sheet, a section-level process picture with traceable grouping, or a slide version of a PFD."
last_verified: "2026-11-01"
requires:
  python_packages: [pymupdf, matplotlib, fonttools, pyyaml]
---

# Professional process overview (A3 PFD + 16:9 slide)

One command turns the case-aware data set of a solved NeqSim model into a **one-sheet A3 landscape
process flow diagram** that communicates the whole process (feeds, sections, separation, compression,
pumping, heating/cooling, treatment, recycles, products) and a **16:9 presentation adaptation** of the same
drawing. Everything is deterministic vector output with editable text and embedded fonts; nothing in the
simulation model is modified.

Toolkit (flat `devtools/` modules, shared with the process-study report): `ppo_graph` (structured graph
and reductions), `ppo_layout` (hierarchical layout, routing, annotations, `neqsim.pfd_layout.v1` spec),
`ppo_render` (styles, symbol library, sheet furniture, SVG/PDF/PNG exporters), `ppo_validate`
(validation record), `process_overview` (orchestrator + CLI), `ppo_examples` (synthetic test plant).
Layout engine, symbol geometry, label placement and detail sheets are reused from `psr_diagrams`;
the layout gate is `pfd_layout_check` (skill `neqsim-pfd-layout-review`).

## Quick start

```powershell
# from the data extracted by the process-study report (preferred: same numbers as the report)
neqsim process-overview <task>/deliverables/process_report/data/process_data.json --case A `
    --out <task>/deliverables/process_overview --title-block <task>/inputs/overview_title_block.yaml

# directly from a model source (solved through process_report: model.py with build(case), process.json, plant.neqsim)
neqsim process-overview model.py --cases cases.yaml --case A --out out/overview

# options
#   --level overview|engineering|presentation   A3 information level (default overview)
#   --detail-sheets collapsed|all|none          A3 PFD sheets for sections drawn as blocks (default collapsed)
#   --collapse "<section name>"                 force a section to block representation (repeatable)
#   --config cfg.json  --style style.json       graph / style overrides (see below)
#   --no-mono  --no-presentation  --no-embed-font  --dpi 150  --quiet
#   --no-supporting-slides                     keep main slides; A3 detail sheets unchanged
```

Without `neqsim` on PATH: `<python> -m neqsim_cli process-overview ...` (plugin install) or
`<python> devtools/process_overview.py ...` (checkout). Python: `process_overview.build_overview(data, out_dir, ...)`.

## Outputs (`--out` folder)

| File | Content |
|---|---|
| `overview_A3_colour.svg/.pdf/.png`, `overview_A3_mono.*` | A3 landscape 420 x 297 mm, frame with zones, legend, KEY STREAMS table, ISO 7200-style title block |
| `overview_16x9.*`, `overview_16x9_mono.*`, `overview_16x9_detail_NN.*` | 13.33 x 7.5 in slide, same tags/numbers; one supporting slide per section drawn as a block |
| `detail_sheets/` | `psr_diagrams.build_pfd` drawing set for the sections referenced on the overview (`sheet PFD-nn`) |
| `process_graph.json` (+ `process_graph_presentation.json`, `process_graph_drawn.json` when sections were merged) | structured source data: equipment, ports, streams, sections, trains, values of the case, mapping of every reduction |
| `layout_A3.json`, `layout_16x9.json`, `pfd_layout_spec_A3.json`, `pfd_layout_spec_16x9.json` | positions, routes, bubbles, bundles, fit attempts; the `neqsim.pfd_layout.v1` specs for `neqsim pfd-check` |
| `validation.json`, `validation.md` | checks V01-V16 (see below), pfd_layout_check findings, limitations |
| `manifest.json` | files, page sizes, collapsed sections, refs, warnings, the exact regeneration command |

## Procedure

1. **Source and case.** Prefer the `process_data.json` written by `neqsim process-report` (then drawing and
   report share one extraction). State the case (`--case`); every displayed value is `results[case]` of that
   file - never mix cases, never show design ratings as operating values.
2. **Title block.** Write a YAML/JSON with `company`, `project`, `title`, `document_number`, `revision`,
   `issue_status`, `date`, `drawn_by`, `model_source`, `notes`, `readiness_note`. Leave `checked_by` /
   `approved_by` unset ("not assigned") unless a person did it - never imply review or approval.
3. **Run** the command; read `manifest.json` (`validation_status`, `collapsed_sections`, `fits`, `warnings`).
4. **Inspect every drawing** at full page (`view` the PNG) and close-ups (crop dense regions): reading
   direction, section titles, feed/product labels, stream bubbles vs. KEY STREAMS table, legend, title block,
   mono variant separately (service is encoded by dash pattern there), the slide at normal viewing size.
5. **Run the layout gate** `neqsim pfd-check <out>/pfd_layout_spec_A3.json <out>/pfd_layout_spec_16x9.json` and note the
   result in the task's `pfd_layout_review/`. `CROSSING` warnings are expected on dense overviews; errors must
   be fixed by regeneration (config/style), never by hand-editing the SVG.
6. **Improve, regenerate, re-inspect.** Typical levers (never shrink text below the readability target):
   - `--collapse "<section>"` for sections whose equipment belongs on a detail sheet;
   - `--config` `{"section_groups": [{"name": "Compression", "sections": ["stage 3 train A", "stage 3 train B"]}]}`
     for a deliberate section hierarchy (A3) and `presentation_groups` for the slide story;
   - `{"sections": {...}}`, `{"section_labels": {...}}`, `{"section_order": [...]}` to rename or order sections;
   - `{"annotate": ["S012", ...]}` to choose the key streams; `{"group_trains": "never"}` to show both trains;
   - style: `{"pt": {"tag": 10}, "section": {"gap_x": 14}, "annotation": {"style": "box"}}`.
7. **Deliver** the folder with `validation.md` and a short limitation list (from `validation.json`) in the report.

## What the drawing shows and how it is built

- **Sections** come from the model areas (`ProcessModel` systems). They are placed left to right in
  mass-flow order (weighted feedback-arc-set ordering), stacked vertically when parallel, and wrapped into
  rows when the sheet is narrower than the process; connections between rows run in corridors with
  shared tracks, returns and recycles are routed above/below or along the left margin.
- **Inside a section** the units are drawn with the Sugiyama engine of `psr_diagrams` (layering, crossing
  reduction, port ordering, orthogonal channels). Separators put gas out top, water out bottom.
- **Reductions (recorded in `process_graph.json/mapping`)**: numerical `Recycle` elements removed (stream
  drawn to the real consumer, dashed); in-line valves absorbed into their stream (overview /
  presentation level); >= 3 boundary feeds into one unit grouped (`GF-<unit>`, count and range shown);
  identical parallel trains drawn once (`tag xN`); streams between two blocks bundled (`xN` mark).
- **Fit strategy** (A3 then slide): symbol scale -> block representation of the section that helps most
  (detail on `sheet PFD-nn`) -> single-unit sections as blocks -> stream bubbles limited to boundary streams
  -> compact text step at the readability target (A3 7 pt, slide 9 pt) -> contiguous section groups.
  Every step is logged in `layout_*.json/attempts` and the validation record.
- **Values**: boundary streams carry flow, T, P under their label; key cross-section streams carry a
  stream-number bubble and appear in the KEY STREAMS table (t/h, C, bara; totals for shaft power and duties).
  Standard conditions are stated in note 1.
- **Small mass flows**: use three significant figures, preserve signed nonzero flows and distinguish exact
  zero from trace bounds below 0.001 kg/h. The table retains t/h, including scientific notation when needed;
  the source JSON keeps full precision. See `docs/development/PROCESS_OVERVIEW.md` for the formatting policy.
- **Colour** (project convention, Okabe-Ito palette, defined in the legend): gas vermilion, oil/condensate
  bluish green, produced water blue, multiphase black, utility grey dashed, recycle dashed. The mono variant
  encodes the service by dash pattern; the heavy line is the main path in both.

## Standards basis (document what is used, claim nothing unverified)

| Topic | Convention applied | Note |
|---|---|---|
| Sheet | ISO 5457: A3 420 x 297 mm, frame with 20 mm filing margin left / 10 mm elsewhere, zone grid, centring marks | edition not verified against standard text |
| Title block | ISO 7200 data fields: legal owner, identification number, title, revision, date, sheet, document type, status, creator/checker/approver, represented case | fields that have not happened read "not assigned" |
| Content | ISO 10628-1 PFD level: equipment, main flows, recycles, utility interfaces where modelled | instrumentation only when it explains operation |
| Symbols | ISO 10628-2 / ISO 14617 shapes shared with the process-study report | documented in `ppo_render.SYMBOL_LIBRARY` |

Record the editions actually consulted in the task's standards register; the drawing note states
"editions not verified, conformance not claimed".

## Validation (automatic, `validation.md`)

V01 unique traceable ids - V02 valid endpoints, no dangling legs - V03 every source area and boundary
stream represented - V04 explicit mapping for grouped/bundled/collapsed items - V05 displayed values
re-derived from the source case - V06 inside the frame - V07 label size >= target - V08 `pfd_layout_check`
(overlaps, line-through-box, crossings) - V09 PDF page size, embedded fonts, exports - V10 fits without
shrinking text - V11 stream data placed - V12-V14 the same for the 16:9 version - V15 every supporting
slide (fit, page bounds, process fonts, layout/arrows, source values, endpoints and annotations) - V16 main
slide fit. Failed supporting slides block export and write a validation record. Split their sections or use
`supporting_slides=False` / `--no-supporting-slides`, retaining A3 detail PFDs. Review each supporting slide's
`overview_16x9_detail_NN_layout.json` and `_validation.json`. Status FAIL blocks delivery.

## Limitations

- Routing density: facilities with many inter-section streams end up at block level on A3 (the detail is on
  the referenced sheets); crossings are counted, not eliminated.
- Utilities, drivers and non-modelled systems are not drawn; say so in the notes (`title_block.notes`).
- Section quality follows the model's area structure - fix odd areas with `section_groups` / `section_labels`.
- No instrumentation; use the P&ID workflows for that.

Related: `process-study-report` (data extraction, detail sheets, stream tables), `neqsim-pfd-layout-review`
(gate), `neqsim-report-design-and-visual-qa`, `neqsim-professional-reporting`.
