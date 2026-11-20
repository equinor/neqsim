---
name: neqsim-report-design-and-visual-qa
description: "USE WHEN: improving technical report formatting, Word typography, table layout, captions, pagination, or professional presentation. Produces a separate template-preserving DOCX revision, PDF and all-page previews, with explicit visual QA. Complements neqsim-professional-reporting."
---

# Report design and visual QA

Engineering quality and presentation are separate gates. Use
[neqsim-professional-reporting](../neqsim-professional-reporting/SKILL.md) for
traceability, evidence and analytical depth; use this skill for the reader's
experience. A well-formed DOCX is not proof of good pagination.

## Boundaries

- Flow diagrams (PFD/BFD/P&ID sketches) are first checked geometrically with `neqsim pfd-check`
  ([neqsim-pfd-layout-review](../neqsim-pfd-layout-review/SKILL.md)); this skill covers page-level presentation.

- Preserve the configured corporate template, fonts, heading colours, logos,
  classification, revision history and header/footer content.
- Work on a separately named derivative under the existing task's
  `step3_report/formatting_pilot/`. Never overwrite the original during a pilot.
- Do not rewrite numbers, conclusions, readiness labels or evidence to make the
  layout easier. Formatting approval is not engineering approval.
- Do not upload a company report to online converters or design services.
- Never open, edit or copy the report generator. Put reusable finishing logic in
  `devtools/report_design.py`; authored narrative belongs in report inputs.

## Figure design rules (illustrations)

Learned from reviewing a 17-figure safety study; apply to every generated figure, not only flow diagrams.

| Rule | Why / measured failure |
|---|---|
| Design figures at the report text width (about 7.2 in) with 7-9 pt text | 13-19 in wide figures printed at 3-4 pt and were unreadable |
| One house style module per study: colours, fonts, markers, legend position, north arrow, margins | panels in different figures used different arrows, legends and extents |
| Title = the finding (bold, at most two lines), grey sub-line = case and conditions | descriptive titles ("Plan footprints") made readers hunt for the message |
| Reserve title and legend margins explicitly; never let a legend or sub-line overlap panel titles or axis labels | overlaps appeared in every multi-panel figure on first render |
| Do not scale a multi-panel figure by adding panels: 2 x 2 at most per page-width figure | 8-panel figures turned narrow plumes into slivers |
| Sensitivity axes start at zero and are shared between panels | auto-zoomed tornado axes made differences of 0.1 % look dominant |
| Show the quantity the decision needs (peak on the flight path), not the model's near-source core value; cap extreme values as "> 100 %" | "peak 6210 % LFL" was meaningless |
| State exaggerated scales (e.g. vertical x3) and show a scale bar on schematic overlays | readers misjudge geometry otherwise |
| Never publish a picture that shows nothing (default view, ITEMS 0, empty panel); keep it as evidence, list the gap in a coverage table | six "not found" captures were embedded as figures |
| A table is not repeated as a figure; no duplicate images (auto-collected wind rose next to the composite) | duplicates inflated the figure list |
| Overlays on third-party screenshots (digital twin, drone, map) carry the disclaimer "schematic, not registered to the camera" and the source | no camera parameters means the position is not geo-referenced |
| Give every figure a discussion entry (observation, mechanism, implication, recommendation) | the generator places figures with a discussion entry in the Discussion chapter and the rest in Results: mixing the two split related figures |
| Numbers quoted in figure text come from the results file, computed not typed; re-check after every model change | a benchmark sentence claimed 0.1 % where the rerun gave 2.3 % |
| Render the PDF and look at the figure pages at print size before sign-off | the contact sheet at print size exposed all of the above |

## Procedure

1. Generate the normal report with `neqsim report TASK_DIR` when needed. For an
   existing approved example, use the existing DOCX without regenerating it.
2. Run `neqsim report-design SOURCE.docx --output PILOT.docx --render`.
   In a checkout, use the selected Python interpreter with
   `devtools/report_design.py` and the same arguments; without a checkout use
   `-m report_design`. Quote paths containing spaces.
3. Use `--table-label Tabell` for new Norwegian captions. Existing caption
   wording is preserved. `--overwrite` permits replacing only the derivative.
4. Inspect the contact sheet and **every page image**, not just the cover.
   The command writes `PILOT.pdf`, `PILOT.qa.json` and `PILOT_preview/`.
   Inspect dense tables, continued tables, figure/caption pairs and section
   transitions at full size.
5. Correct issues in the shared finishing helper or report inputs, run focused
   tests, then repeat rendering. Do not repair only the final Word file and
   leave the shared workflow broken.
6. Record the inspected pages, renderer, findings, remaining editorial issues
   and reviewer in the QA JSON. Change `visual_review` from `PENDING` only after
   actual inspection. Never equate empty geometry findings with visual approval.

The command is opt-in. Existing `neqsim report` output and scientific-paper
formatting are unchanged. Word rendering uses a separate application instance,
disables macros and link updates, and refreshes the derivative's contents and
fields. LibreOffice is used when Word automation is unavailable; its pagination
may differ from Word. Missing rendering software is an explicit blocker, not a
reason to claim visual QA passed.

## Acceptance criteria

| Area | Check |
|---|---|
| Body text | Template font; consistent named-style sizing and spacing; comfortable line length |
| Hierarchy | Clear heading levels; no orphan heading or short table lead-in |
| Tables | Content-aware widths; aligned numeric values; readable type; repeated headers |
| Pagination | No clipped content, logo collisions, blank spill pages or excessive whitespace |
| Long rows | Ordinary rows stay intact; page-sized evidence rows can continue without overflow |
| Captions | Every body data table has a live number; captions stay with their object |
| Figures | Sharp text and axes; adequate size; no figure detached from its caption |
| Navigation | Updated contents, page references and page numbers |
| Preservation | Original paragraphs, evidence cells, branding and source file unchanged |

Use restrained rules and alternate-row shading instead of adding decorative
boxes everywhere. The finishing pass uses 10.5-point table text and preserves
template fonts and existing column proportions, except that two-column evidence
tables allocate more width to the text-heavy column. Short tables stay together;
long tables can continue with repeated headers. It does not impose a new brand.

## Automated render checks (run on every finished document)

Check the rendered PDF, not the DOCX XML, because layout differs between engines:

| Check | Fail when |
|---|---|
| Small table text | any table text below the template minimum at print size (about 8 pt); many small cells hide an oversized table |
| Overprinting | text boxes overlap other text, drawing frames, stamps or logos; table or figure crosses the margin or the title block |
| First page | no verdict (status, readiness, headline numbers, decisions) after the cover |
| Contents | missing or not updated table of contents in a document of more than about ten pages |
| Reader wording | schema names, field keys, file names, `snake_case` identifiers or tool names appear in the body text |
| Depth | fewer chapters, tables or figures than the structural minimum of the document type |

Then view a first page, a table page and a figure page of every document. A PASS from a text extraction without
that view is not visual QA. See `neqsim-professional-reporting` (document-package structural rules).

## Editorial problems formatting must expose

Serialized dictionaries, machine-generated headings, empty workflow columns,
placeholder sections and excessive results lists need source-level editing.
Do not silently summarize or delete them. Mark decisive results as headlines
in the report inputs when authorized, leaving complete results in an appendix.
Keep that editorial change distinct from a formatting-only pilot.

## Validation and ownership

Focused regression suite: `devtools/test_report_design.py`. It covers source and
content preservation, template branding, merged cells, section widths, captions,
oversized rows, page previews, CLI routing and explicit rendering failures.
Synthetic fixtures only: private reports, logos and screenshots stay in the task.

These are independently authored engineering-report conventions, not copied
third-party document-skill materials.
