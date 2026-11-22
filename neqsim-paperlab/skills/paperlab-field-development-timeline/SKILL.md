# Skill: PaperLab Field Development Timeline

## Purpose

Create compact teaching timelines for field-development books. Use this when
case studies, NCS regulatory steps, PDO decisions, project gates, or CCS value
chain milestones need to be placed in chronological context.

## Inputs

- Case-study chapter text.
- Project milestone lists or tables.
- Regulatory dates, licence awards, PDO submission/approval dates, startup dates,
  and major modification dates.
- Optional source-document metadata from `source_manifest.json`.

## Workflow

1. Extract events as `title`, `date`, `case`, `phase`, and `source`.
2. Normalize dates to ISO `YYYY-MM-DD`; use year-only dates only when the source
   is genuinely coarse.
3. Group events by lifecycle phase: discovery, appraisal, concept select, FEED,
   PDO, execution, startup, operations, late life, and decommissioning.
4. Generate an offline HTML timeline or a Markdown table for the chapter.
5. Link timeline events back to case-study sections and source documents.

## Output

- `field_development_timeline.json`
- Optional `field_development_timeline.html`
- Optional chapter-ready Markdown event table.

## Safety Rules

- Do not invent exact dates from approximate source text.
- Keep public timelines to public or approved case-study information.
- Use timelines as teaching aids, not as authoritative project records unless
  the source is cited and verified.
