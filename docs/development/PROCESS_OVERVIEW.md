---
title: "Process Overview: one A3 process flow diagram and a 16:9 slide from a NeqSim model"
description: "How neqsim process-overview turns the case-aware data of a solved NeqSim ProcessSystem or ProcessModel into a one-sheet A3 process flow diagram (colour and monochrome, embedded fonts, PDF and editable SVG), a coordinated 16:9 presentation version, subsystem detail sheets, a structured process graph and an automatic validation record."
---

# Process Overview (A3 PFD + 16:9 presentation)

`neqsim process-overview` draws the **complete process on one A3 landscape sheet**: where the feed enters, how
the sections connect, how gas, oil and water separate and move, where compression, pumping, heating, cooling,
treatment and export happen, which recycles and returns matter, and where products leave. The same process
graph is reflowed into a **16:9 presentation version** with larger labels and fewer numbers. Output is
deterministic vector drawing with editable text and embedded fonts; the simulation model is never modified.

The toolkit lives in `devtools/` next to the [process-study report](PROCESS_STUDY_REPORT.md) and reuses its
extraction (`psr_extract`), layout engine, symbol geometry and detail-sheet builder (`psr_diagrams`), and the
mandatory layout gate `pfd_layout_check`:

| Module | Responsibility |
|---|---|
| `ppo_graph` | structured graph `neqsim.process_overview.graph/1`: equipment, ports, streams, sections, trains, values of one case, reductions with explicit mapping (`merge_sections`, `subgraph` helpers) |
| `ppo_layout` | hierarchical layout: section ordering (mass-flow weighted feedback-arc set), rows of layers, section-internal Sugiyama layout, corridor/channel routing with interval-coloured tracks, bubbles and attached annotations, `neqsim.pfd_layout.v1` spec |
| `ppo_render` | styles (A3 and 16:9), fonts with real metrics, symbol library, ISO 5457-style frame, ISO 7200-style title block, SVG/PDF/PNG exporters |
| `ppo_validate` | checks V01-V16, including every supporting slide, and the Markdown/JSON validation record |
| `process_overview` | orchestrator, fit strategy, detail sheets, supporting slides, CLI |
| `ppo_examples` | synthetic two-train gas plant used by the tests |

## Quick start

```powershell
neqsim process-overview deliverables/process_report/data/process_data.json --case A --out deliverables/process_overview `
    --title-block inputs/overview_title_block.yaml
neqsim process-overview model.py --cases cases.yaml --case A --out out/overview      # solve a model source first
neqsim process-overview data.json --out out --level engineering --detail-sheets all  # all equipment, sheets for every section
neqsim process-overview data.json --out out --collapse "ejector gas" --config overview_config.json --style style.json
```

Python:

```python
import json
import process_overview

data = json.load(open("process_data.json", encoding="utf-8"))
manifest = process_overview.build_overview(data, "out/overview", case_id="A",
                                           title_block={"document_number": "XX-PFO-001", "revision": "A"})
print(manifest["validation_status"], manifest["collapsed_sections"])
```

Use `--no-supporting-slides` (Python: `supporting_slides=False`) to generate the A3 and main 16:9
overviews without supporting presentation slides. A3 detail PFDs still follow `--detail-sheets`;
use `--detail-sheets all` to keep a complete equipment drawing set.

## Outputs

| File | Content |
|---|---|
| `overview_A3_colour.svg/.pdf/.png`, `overview_A3_mono.*` | A3 420 x 297 mm: frame with zone grid, section frames, symbols, stream lines, boundary data, stream-number bubbles, legend, KEY STREAMS table, title block |
| `overview_16x9.*`, `overview_16x9_mono.*`, `overview_16x9_detail_NN.*` | 13.33 x 7.5 in slide and one supporting slide per section drawn as a block |
| `detail_sheets/` | A3 PFD drawing set (`psr_diagrams.build_pfd`) for the sections referenced as `sheet PFD-nn` |
| `process_graph.json` | the structured source data (plus `process_graph_presentation.json`; `process_graph_drawn.json` when sections were merged) |
| `layout_A3.json`, `layout_16x9.json`, `pfd_layout_spec_A3.json`, `pfd_layout_spec_16x9.json` | positions, ports, routes, bubbles, bundles, fit attempts; `neqsim.pfd_layout.v1` specs for `neqsim pfd-check` |
| `overview_16x9_detail_NN_layout.json`, `overview_16x9_detail_NN_validation.json` | supporting slide geometry and pre-export checks; a failed slide writes its validation record and blocks export |
| `validation.json`, `validation.md` | validation record |
| `manifest.json` | files with page sizes, refs, warnings and the regeneration command |

## Information levels and reductions

| Level | Shown | Absorbed / grouped |
|---|---|---|
| `overview` (A3 default) | all equipment except numerical recycle elements and in-line valves; boundary data; bubbles on main cross-section streams | recycle elements, in-line valves, >= 3 feeds into one unit, identical trains |
| `engineering` | all equipment incl. valves; more annotated streams | recycle elements |
| `presentation` (slide) | boundary data only | recycle elements, in-line valves, pipe segments |

Every reduction is recorded in `process_graph.json/mapping` (`collapsed_equipment`, `merged_streams`,
`grouped_feeds`, `grouped_trains`, `merged_sections`) and block-to-block bundles in `layout_*.json/bundles`.

## Fit strategy

Text is never reduced below the readability target (A3 7 pt, slide 9 pt). When the drawing does not fit the
sheet: smaller symbol scale -> the section whose block representation reduces the overflow most becomes a block
with a detail-sheet reference -> single-unit sections -> bubbles limited to boundary streams -> compact text step
at the target -> contiguous groups of sections in process order (`section_groups` / `presentation_groups` in the
config give the user control over this hierarchy). All attempts are logged in `layout_*.json`.

Supporting slides retain the full section instead of collapsing it again. If a supporting section
does not fit, has clipped text, undersized process labels, invalid values, missing arrows or collision
errors, generation raises `OverviewError` before exporting the overview images/PDFs. Split the section
into smaller coordinated sections, or disable supporting slides and retain the A3 detail sheets.
Page size and font embedding alone do not establish that slide content fits.

## Mass-flow precision

Boundary annotations and the KEY STREAMS table use three significant figures. Boundary annotations
use kg/h below an absolute flow of 1000 kg/h and t/h at or above it; the table keeps its t/h header.
Exact zero is `0`. Nonzero magnitudes below 0.001 kg/h use signed bounds (`<0.001 kg/h` or
`>-0.001 kg/h`; `<1e-06` or `>-1e-06` in the t/h column). A flow of 0.04 kg/h is `0.04 kg/h`, and
22 kg/h appears as `0.022` in the table. Missing or nonfinite values are unavailable, never zero.
The table reserves width for the formatted mass values. Source values in JSON retain full precision.

## Configuration

`--config` (JSON, keys of `ppo_graph.DEFAULT_CONFIG` plus `section_groups`, `presentation_groups`):
`collapse`, `group_feeds_min`, `group_trains` (`identical`|`never`), `train_pattern`, `main_fraction`, `annotate`
(`auto`|`all`|`none`|list), `annotate_max`, `sections`, `section_labels`, `section_order`.

`--style` (JSON, nested overrides of `ppo_render.STYLE_A3`; a `"16x9"` sub-object overrides the slide style):
`pt` (title, section, tag, desc, value, stream, note, legend, table ...), `line` (main, process, utility, frame,
symbol ...), `arrow`, `section` (pad_x, pad_y, title_h, gap_x, gap_y, corridor_gap, channel_pitch, lane_pitch),
`annotation` (`style` bubble|box, bubble size), `port_pitch`, `scales`, `compact_pt`, `font` (regular/bold .ttf),
`frame` (margins, zones, centring marks), `title_block`, `panels`.

## Standards basis

The sheet follows ISO 5457 (A3, filing margin, zones, centring marks), the title block the data fields of
ISO 7200, the content level ISO 10628-1 and the symbols ISO 10628-2 / ISO 14617 conventions as implemented in
`ppo_render.SYMBOL_LIBRARY`. These are documented conventions; editions were not verified against the standard
text and conformance is not claimed (the drawing note says so). Colour is a project convention (Okabe-Ito
palette) defined in the legend; the monochrome variant encodes the service by dash pattern.

## Validation record

| Id | Check |
|---|---|
| V01 | equipment and stream identifiers unique and traceable |
| V02 | every connection has valid endpoints; no dangling legs |
| V03 | every source area and boundary stream represented |
| V04 | grouped, bundled and collapsed elements have an explicit mapping |
| V05 | displayed values re-derived from the selected source case |
| V06 | symbols, lines and labels inside the frame |
| V07 | label text at or above the readability target |
| V08 | `pfd_layout_check`: overlaps, line-through-box, crossings |
| V09 | PDF page dimensions, embedded fonts, all exports present |
| V10 | fits without shrinking text |
| V11 | stream data placed without collisions |
| V12-V14 | the same for the 16:9 version |
| V15:slide:check | every supporting slide: fit, page bounds, minimum process font, layout/arrow review, source values, endpoints and annotation placement; missing review geometry blocks validation |
| V16 | main 16:9 layout fits without shrinking text |

Status `FAIL` blocks delivery; `WARN` (typically the crossing count) needs the reviewer's judgement.

## Tests

`devtools/test_process_overview.py` runs the complete pipeline on the synthetic plant (`ppo_examples`) without a
JVM: reductions and mapping, train grouping, layout determinism and orthogonality, symbol library, exports
(page size, embedded fonts, editable SVG), validation record and CLI.
`devtools/test_process_report_regressions.py` adds signed small/trace/unavailable flow cases, table
precision, external driver-label review, an oversized single-section slide and the main-overview-only option.

Related: [process-study report](PROCESS_STUDY_REPORT.md), skill `professional-process-overview`, skill
`neqsim-pfd-layout-review`.
