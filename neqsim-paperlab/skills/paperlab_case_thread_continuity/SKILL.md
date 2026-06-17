---
name: paperlab_case_thread_continuity
description: |
  Maintain consistency for recurring field, facility, and design cases across
  PaperLab books. Use when a book uses a repeated scenario as a teaching thread
  and assumptions must not drift silently.
---

# PaperLab Case Thread Continuity

## When to Use

USE WHEN: chapters reuse the same field, facility, well, pipeline, process, or
economic case across theory, notebooks, exercises, and capstone studies.

Pair with:

- `paperlab_chapter_flow_editor` for narrative transitions,
- `paperlab_book_knowledge_graph` for case graph links,
- `paperlab_notebook_regression_baselines` for computed case outputs.

## Registry Fields

Track recurring case parameters with:

- case id and public display name,
- parameter name and unit,
- base value and uncertainty range,
- source or chapter of origin,
- scenario tag,
- intended teaching role,
- confidentiality status.

## Drift Classes

| Class | Meaning |
|-------|---------|
| intentional-scenario | value changes because scenario changes |
| rounding | harmless numerical rounding |
| stale-value | old value remains after later update |
| naming-drift | same object has multiple names |
| contradiction | incompatible values without explanation |

## Output Schema

```json
{
  "case_id": "ultima_thule",
  "parameter": "export_pressure_bara",
  "values": [
    {"chapter": "ch26", "value": 120.0, "unit": "bara"},
    {"chapter": "ch31", "value": 150.0, "unit": "bara"}
  ],
  "classification": "intentional-scenario",
  "required_note": "Explain that Chapter 31 evaluates the high-pressure export case."
}
```

## Safety Rules

- Protect private case identifiers and document names.
- Do not collapse uncertainty ranges to one number.
- Do not erase scenario variation that supports teaching.