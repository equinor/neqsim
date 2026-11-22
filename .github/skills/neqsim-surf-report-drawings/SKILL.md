---
name: neqsim-surf-report-drawings
version: "1.0.0"
description: "SURF drawing set, datasheets, registers and 20-chapter report contract: 15 deterministic A3 sheets (field plan, network schematic, functional process sheets, route profiles, riser, template/PLEM, jumper, umbilical, thermal cross-sections, installation, results, comparison, quantities), canonical dataset tokens, fail-closed validation and visual inspection. USE WHEN: SURF drawings or the SURF design report are requested, reviewed or extended."
last_verified: "2026-10-25"
requires:
  python_packages: [python-docx, matplotlib, pymupdf, pyyaml]
---

# SURF drawings and report

Library: `devtools/surf_draw.py` (plan map, symbols, tables), `surf_sheets_a.py` / `surf_sheets_b.py` (sheets), `surf_diagrams.py` (package),
`surf_report.py` (DOCX), `surf_validate.py` (gates), `surf_dataset.py` (canonical dataset and token resolver). It reuses the shared
`fa_draw.Sheet`/`Axes`, `psr_diagrams.kit` and `psr_report.ReportBuilder`; do not fork or edit those generic generators.

## Sheets (A3 landscape, title block, SVG + PNG + vector PDF)

DWG-01 field plan (overall + detail), 02 network schematic, 03/04 functional process sheets with operating overlay and omitted systems, 05/06 route
profiles (elevation, p, T, hydrate, erosion on one chainage), 07 riser configuration (**CONCEPT SCHEMATIC - GEOMETRY NOT ANALYZED**), 08 template/manifold/PLEM/PLET,
09 jumper details, 10 umbilical architecture + conceptual section, 11 thermal cross-sections (to scale, U on the bore area), 12 installation sequence,
13 design results, 14 concept comparison, 15 quantities/interfaces/drawing register.

Rules: every number is bound to the dataset or a registered input; every sheet states scale or NOT TO SCALE; checker/approver stay
`not assigned`; schematic envelopes are never vendor GAs; absent geometry is labelled synthetic or schematic, never invented (no surveyed accuracy, clearances, ROV access, bends, tolerances).

## Visual gate (mandatory)

Flow/network schematics and arrangement sheets also pass `neqsim pfd-check` (skill `neqsim-pfd-layout-review`, agent `pfd-layout-review`).

Render and look at **every** PNG at intended size: clipped text, overlaps, illegible labels, crossing lines, wrong arrows, blank views, misleading geometry. Automated QA
(`layout_spec.json`: bounds, overlaps, minimum text 2.2 mm) is necessary, not sufficient. Fix the sheet code or the input and re-render; never hide a finding.

## Report (DOCX + PDF where Word is available)

20 chapters (executive summary ... appendices) with a coverage table, discipline/requirement matrix, numbered captions, lists of figures and tables, landscape drawing appendix.
Tokens: `[[ds:path|fmt|unit=..]]` and `[[fact:key]]`. Unresolved tokens, unit mismatches and invalid-value markers fail validation.
Every conclusion states its evidence and its remaining dependency; discuss each principal figure and table; justify non-applicable disciplines instead of padding.

## Validation gates (`validation_report.md`)

Inputs, convergence and fallback, hand checks, mesh sensitivity, cooldown RK4, dimensions, reconciliation of register/design basis/cost, feasibility-before-cost,
structural status, standards (no conformity claim), drawing layout/assets/title block/files, document tokens/sections/captions/dataset hash/PDF. FAIL -> `NOT_READY`; open evidence
-> `SCREENING`; the label `REVIEWED` is never assigned by software.

## Pitfalls

- Plan panels with an extreme aspect ratio need a strip plus a detail panel at a stated scale.
- Keep labels at least 2.2 mm high; shorten with `surf_draw.fit` instead of shrinking below the minimum.
- The Word PDF export needs Word; without it the report is DOCX only and the check warns (use `--no-word`).
- Keep the words "NaN" and "None" out of validation-table wording; the document check scans the text for them.
