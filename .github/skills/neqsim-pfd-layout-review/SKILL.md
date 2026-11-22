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
| Phase bands | `BAND_NODE` (ERROR), `BAND_EDGE`, `BAND_ORDER` (WARN) | only when the spec has `bands`: equipment drawn outside its declared band (gas / oil / water), a gas, oil or water stream whose horizontal run lies mostly outside its band (recycle, wrap and return lines exempt), bands not stacked gas above oil above water |
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
   A PNG alone can only be capped at WARN (`RASTER_ONLY`): regenerate from source instead. A PNG or SVG written by
   `PfdDiagram` (or any generator that writes `<stem>.layout.json` beside it) is reviewed through that sidecar spec when the
   spec is not older than the image by more than 120 s, so `neqsim pfd-check fig.png` is then a real check
   (`checked_from_sidecar_spec` in the metrics). An SVG with no readable
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

## Phase-band layout: gas on top, oil in the middle, water at the bottom

Oil and gas flow diagrams are organised in horizontal **phase bands**, not in an arbitrary grid. Basis (read from the standards
library, cite the clause, never paraphrase from memory):

| Source | Rule |
|---|---|
| Equinor **TR2329** v2.02 (Process flow diagrams and P&IDs), **SR-15794** | "Three separate horizontal bands should be used on a drawing. The top band ... interconnection piping for gas flow. The centre band ... equipment layout. The bottom band ... interconnecting piping for liquid flow." Process flow **left to right**; lines enter at the left border and leave at the right border; every line entering or leaving carries a source/destination label (**SR-15749**, with the drawing number when it continues on another PFD); at crossovers vertical lines are broken and horizontal lines drawn full, no bridges |
| TR2329 Addendum (2013, OM validity area) 2.2.3.2 | equipment in logical sequence left to right; a zone dedicated to gas / vapour flows above the equipment; the lower part contains oil / liquid lines; one system function per drawing |
| TR2329 **SR-15755 / 15756 / 15811 / 15752** | every line shown has a stream number **in a diamond** on the line; heat and material table for each stream at the bottom of the diagram or on a separate sheet; equipment data (duty, ID x TT, power, efficiency) on the equipment, preferably in its upper part; sparing shown (2 x 100 %); separate PFD or table per operating case |
| **ISO 10628:1997** 5.3 | main flow left to right **and top to bottom**; main flow lines 1 mm, symbols and subsidiary lines 0.5 mm, valves/instruments 0.25 mm (never < 0.25 mm); parallel lines at least 2 x the widest line and > 10 mm apart is desirable; letters 5 mm (major equipment tags) and 2.5 mm (other text); flow data in frames or a table with the frame placed above horizontal lines |

Consequence for oil/gas facilities (this repository's convention, built on the rules above):

1. **GAS band on top**: gas coolers, scrubbers, compressors, gas lift / injection / export / fuel gas. Several rows inside the band are
   fine (one compression stage per row, joined by `wrap` lines); gas leaves separators at the top.
2. **OIL / liquid hydrocarbon band in the middle**: inlet and production separators, manifolds, oil/condensate pumps, stabilisation,
   export oil. The mixed-phase feed enters at the left of this band.
3. **WATER band at the bottom**: produced water outlet, water treatment, injection water. Water leaves separators at the bottom.
4. Process flow left to right in every band; a stream that moves to another band leaves or enters at the edge of its band by a vertical
   run; returns (gas lift, condensate return) use `role="recycle"` / `"return"` and boundary stubs with a source/destination label
   instead of long lines across other bands.
5. Equipment data and utilisation go **on the equipment** (text lines, fill colour); the stream bubbles refer to the stream table of the
   process report (SR-15756). The drawing says what is not drawn.

`PfdDiagram.phase_bands(weights=(gas, oil, water))` creates the three lanes (tinted background and lane label) and returns
`{band: (y_top, y_bottom, y_centre)}`; give every equipment node `band="gas"|"oil"|"water"` and place it on the band centre line or on
a row inside the band. The spec then carries `bands`, and `neqsim pfd-check` reports `BAND_NODE`, `BAND_EDGE`, `BAND_ORDER`. A worked,
checked example (gas train, separator, oil export pump, produced water; PASS) is `devtools/pfd_band_example.py`; the full Oseberg Sor
version with utilisation fill was generated the same way. Tips that decided the layout:

- Count the width first: a gas stage (cooler 6.6 + scrubber 6.6 + compressor 11.5 units + three links of >= 4.4 units for number bubbles)
  needs about 40 of the 100 canvas units; two stages fit one row, three stages need two rows (serpentine with a `wrap` line, product on
  the right of the lower row). Drop number bubbles only on links shorter than a bubble; never shrink text below 7 pt at A3.
- Put the separator at the left of the oil band and run its gas riser straight up into the first gas cooler (align the riser x with the
  cooler x); scrubber liquids go into one collector channel between the gas rows and back to the separator top.
- Fuel gas and gas-lift take-offs go **up** from the gas row into boundary boxes above it; a take-off that must cross another gas line
  would break the "horizontal full, vertical broken" rule - redraw as a stub with a source/destination box.
- Everything that is not drawn (condensate pump, utilities, drivers) is said in the footer; do not hide equipment that carries a
  utilisation figure unless the footer says so.

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

## Reconciling the layout spec with the rendering (drawing sheets)

A drawing sheet exists as a source spec (`*.layout.json`), a vector file and a raster or PDF page. The verdicts can
disagree: the spec review says FAIL, while a check of the exported page says PASS or only `RASTER_ONLY`/`NO_STRUCTURE`
WARN. Rules:

1. The verdict of the spec of the **same revision** governs. A PASS from a raster or a stale file never overrides a FAIL
   of the spec. Compare file times or hashes: the spec must not be older than the rendering by more than the sidecar
   tolerance (120 s), otherwise regenerate before judging.
2. A spec FAIL is cleared only by regenerating from the generator and re-running `neqsim pfd-check` on the new spec.
   Do not edit the spec by hand to turn it green and do not waive a defect that has a layout fix.
3. A spec PASS with a visibly defective page is a checker gap: fix the page (generator), list the gap in the review
   and, when it is generic, add a rule or test to the checker.
4. Record the reconciliation in `<figure>.review.json`: spec path and hash, rendering path and hash, both verdicts,
   which governs, and what was changed. Sheets that are maps or charts (`NO_STRUCTURE`) are reviewed by eye and listed
   as such, never counted as PASS.

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
`neqsim-professional-reporting` (report hygiene), agent `pfd-layout-review`; reconcile a spec FAIL with a rendering PASS as
described above.
