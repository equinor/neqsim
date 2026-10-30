---
name: neqsim-report-design-and-visual-qa
description: "Use when improving technical report formatting, Word typography, table layout, captions, pagination, or professional presentation. Produces a separate template-preserving DOCX revision, PDF and all-page previews, with explicit visual QA. Complements neqsim-professional-reporting."
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
