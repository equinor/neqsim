# Skill: NeqSim Standard Requirement Extraction

## Purpose

Extract and map engineering requirements from standards or approved standards
excerpts into a structured format that can support NeqSim book chapters,
engineering reports, and design checks. Use this for NORSOK, DNV, API, ISO,
IEC, ASME, and similar standards when clause-level traceability is needed.

## Inputs

- A standards PDF or approved excerpt, when extraction is legally permitted.
- Existing chapter text or `standards_map.json` to identify the target chapter
  and equipment context.
- Optional OCR text for scanned standards.

## Workflow

1. Identify the standard code, revision, publisher, and license constraints.
2. Extract only requirement-level statements using keywords such as `shall`,
   `should`, `may`, `required`, and standard-specific forms.
3. Preserve section, clause, page, context-before, and context-after metadata.
4. Classify each item as mandatory, recommendation, permission, information, or
   ambiguous.
5. Link each item to chapters, equipment, or skills without copying large
   licensed text into public outputs.
6. Use `book-standards-map` to summarize the chapter-level standard relevance.

## Output Schema

```json
{
  "standard": "NORSOK D-010",
  "revision": "latest-confirmed",
  "source": "approved local excerpt",
  "requirements": [
    {
      "id": "REQ-001",
      "level": "mandatory",
      "clause": "5.2",
      "topic": "well barriers",
      "text_excerpt": "short allowed excerpt or paraphrase",
      "context": "why it matters for the chapter",
      "mapped_chapters": ["ch14_drilling_and_wells"],
      "status": "candidate"
    }
  ]
}
```

## Safety Rules

- Respect standards copyright and license restrictions.
- Prefer clause references and paraphrased summaries for public artifacts.
- Do not claim compliance from keyword extraction alone; human review is needed.
- Mark OCR-derived requirements as `needs-human-check` until reviewed.

## Book Uses

- Chapter 7: separator and vessel design.
- Chapter 11: facility architecture, power systems, and design basis.
- Chapter 13: subsea and SURF systems.
- Chapter 14: wells and barriers.
- Chapter 21: NCS regulation and standards governance.
- Chapter 23: CO2 transport and injection integrity.
