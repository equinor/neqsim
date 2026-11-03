---
name: neqsim-pfd-layout-review
description: "Mandatory deterministic layout review of every process flow diagram (PFD, BFD, P&ID sketch, flow/network diagram) before use. USE WHEN: a flow diagram is generated, redrawn or inserted in a report - runs `neqsim pfd-check` (overlaps, label/line/box collisions, crossings, arrowheads, flow direction, tags, stream ids, ports, legend, whitespace), loops with the generator until PASS, then a visual sign-off."
---

# PFD layout review (mandatory gate)

**Rule: every flow diagram must pass this review before it is used.** A diagram that only "looks right" in
one render is not accepted: geometry is checked by a deterministic program, then a person (or the
`pfd-layout-review` agent) looks at the picture. The checker never approves the engineering content, only the
drawing.

## What is checked (codes)

| Group | Codes (severity) | Meaning |
|---|---|---|
| Boxes | `BOX_OVERLAP` (ERROR), `BOX_TOUCH` (WARN), `OUTSIDE_CANVAS` (ERROR) | boxes overlap, touch (< 1.2 % of the diagonal) or leave the canvas |
| Labels | `LABEL_LABEL`, `LABEL_BOX`, `LABEL_LINE` (ERROR overlap, WARN touching), `NODE_TEXT_OVERFLOW`, `TEXT_OUTSIDE_CANVAS`, `TEXT_TOO_SMALL` | text on a line/box/other text, text not inside its box, text clipped, effective size < 5 pt (ERROR < 3.5 pt) at the intended print width |
| Lines | `LINE_THROUGH_BOX` (ERROR), `CROSSING` (WARN, counted), `LINE_OVERLAP` (ERROR), `LINE_DIAGONAL`, `MERGE_WITHOUT_JUNCTION` (ERROR), `DANGLING_END` | line crosses a box, other lines, runs collinear on another line, merges without junction dot, ends in free space |
| Direction | `NO_ARROWHEAD`/`NO_DIRECTION` (ERROR), `FLOW_DIRECTION`, `FEED_POSITION`, `PRODUCT_POSITION`, `VESSEL_ORIENTATION`, `PHASE_SIDE` | missing arrowheads, flow not left to right (feed left, product right; mark intentional `wrap`/`recycle` lines), columns drawn horizontal, gas not out of the top / liquid not out of the bottom, column inlets on wrong side |
| Ports | `PORT_AMBIGUOUS`, `PORT_CROWDED`, `PORT_AT_CORNER`, `PORT_ONE_SIDE` | inlet and outlet share a side, ports closer than 2 arrowheads, lines at a box corner |
| Identity | `MISSING_TAG`, `MISSING_STREAM_ID`, `UNLABELLED_STREAM`, `UNCONNECTED_NODE` (ERROR) | equipment without tag, streams without number/label, orphan boxes |
| Style | `SYMBOL_INCONSISTENT`, `LINE_STYLE_INCONSISTENT`, `MISSING_LEGEND`, `MISSING_TITLE`, `MISSING_FOOTER` | one symbol per equipment kind, one line style per fluid, legend/title/status footer present |
| Space | `WHITESPACE_UNBALANCED`, `WHITESPACE_POCKET`, `SMALL_CONTENT` | off-centre content, large empty pockets inside the diagram extent, diagram tiny on its sheet |

Status: **FAIL** if any ERROR, **WARN** if any WARN, **PASS** otherwise. `score` (0-100) is a trend number, never a
pass criterion. Each finding carries a concrete fix-up (distance to move, port to use, junction position).
Waivers (`spec.waivers = [{"code", "reason"}]` or `PfdDiagram.waive(...)`) turn a code into INFO and are listed in the
review; use only for a physical reason (for example a long recycle that cannot avoid one crossing), never for taste.

## Procedure

1. **Run the checker on the diagram source** - preferably the geometry spec the generator writes
   (`*.layout.json`), else the SVG:
   `neqsim pfd-check fig.layout.json other.svg --out-dir <task>/step3_report/pfd_layout_review`
   (without `neqsim` on PATH: `<python> -m pfd_layout_check ...`; source checkout: `devtools/pfd_layout_check.py`).
   A PNG alone can only be capped at WARN (`RASTER_ONLY`): regenerate from source instead. An SVG with no readable
   boxes or streams (maps, plots, profile sheets) gives `NO_STRUCTURE` WARN: nothing was verified, so view it and review by eye.
   Exit code 0 PASS, 1 WARN, 2 FAIL (`--strict` makes WARN fail too).
2. **Fix in the generator, not in the picture.** Apply each fix-up, regenerate, re-run. Typical moves:
   widen the gap between boxes (>= 8 units when a bubble + label sits on the line), put stream numbers on the line
   and condition labels beside it, use port sides (gas out of the top, liquid out of the bottom, lean solvent into
   the top of a contactor, feed gas into the bottom), lay out on a grid left to right with serpentine rows joined by
   `wrap` lines, add junction dots, align port coordinates so lines are straight.
3. **Visual inspection** of the PNG/PDF page at the size it will be printed (use the `view` tool; crop dense regions):
   overlaps the checker cannot see (symbol meaning, label next to the right line, reading order), legibility at
   print size, consistent symbols. Record what was looked at.
4. **Sign-off record** `step3_report/pfd_layout_review/<figure>.review.json` + `review.md` per figure: status,
   score, findings, waivers, the generator script, reviewer. The report may use a figure only when it is
   PASS (or WARN with every warning explained in the review and agreed by the user). Loop 1-3 until then.
5. **Tell the user** which figures passed, which were redrawn, what was waived, and keep the previous versions in
   `figures_prev/` (never overwrite a delivered figure without keeping the old one).

## Building diagrams that pass first time

`devtools/pfd_diagram.py` (`PfdDiagram`) is a deterministic builder for matplotlib generators: grid placement
(`d.grid(cols, rows)`), nodes by kind (`vessel column hx pump compressor tank valve block boundary junction`), edges
between named port sides with orthogonal routing (`via="hv"`, `"vh"` or waypoints, `offa`/`offb` port offsets,
`role="wrap"|"recycle"`), stream-number bubbles and collision-aware condition labels, automatic legend, wrapped footer
and the exact geometry export. `render(path)` writes PNG, SVG, `*.layout.json`, `*.review.json` and returns the
report. Coordinates: 100 units wide, y down, `(x, y)` is a node centre. Never draw a line from a box to a label.

Spec schema (`neqsim.pfd_layout.v1`): `canvas {width,height,unit,intended_width_mm}`, `nodes[] {id, kind, tag,
bbox[x,y,w,h], texts[], style}`, `edges[] {id, from, to, points[], arrow_end, fluid, stream, label{text,bbox}, role,
style}`, `labels[]`, `junctions[]`, `title`, `legend`, `footer`, `flow_direction`, `waivers`, `options`.
SVG written by `psr_diagrams` (`data-tag`/`data-block` node groups, `data-stream`/`data-edge` edge groups) is parsed
directly; other SVG is read as free shapes and text (limited checks).
External utility/driver text uses `data-layout-kind="outside"` on its SVG text element. This identifies
intentional outside-symbol placement; it does not waive label collisions, line-through-text, font-size
or arrowhead checks. Keep this metadata in generated SVG rather than suppressing overflow codes globally.

## Where the gate is mandatory

`process-study-report` (BFD/PFD sheets: `process_study_report.build_diagrams` writes
`diagrams/layout_review/`), `flow-assurance-study-report`, `surf-design-study` and SURF layout, `utilities-design-report`,
`plant-layout-study`, enterprise process-notebook reports, and any figure that shows flow topology (tie-in P&ID sketches,
subsea layout schematics, block diagrams in papers). Geographic maps and profile charts are not flow diagrams: review
them with `neqsim-report-design-and-visual-qa`.

## Limits

Text extents of SVG are estimated from font size (no font files); matplotlib builders measure real extents. The
checker does not judge process correctness, symbol conformance to ISO 10628 or whether a stream belongs on a
line. Raster images cannot be verified structurally.

See also: `neqsim-report-design-and-visual-qa` (page-level QA), `neqsim-plant-layout-geometry` (plot plans),
`neqsim-professional-reporting` (report hygiene), agent `pfd-layout-review`.
