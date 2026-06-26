---
name: paperlab_publication_calendar_scout
description: |
  Find and track public calls for papers, special issues, conferences, and
  themed collections relevant to PaperLab and NeqSim research topics.
---

# PaperLab Publication Calendar Scout

## When to Use

USE WHEN: aligning PaperLab papers and books with special issues, conferences,
deadlines, or themed publication opportunities.

## Calendar Fields

```json
{
  "calls": [
    {
      "title": "Special issue on process systems engineering",
      "venue": "Journal name",
      "url": "https://example.org/call",
      "abstract_deadline": "2026-09-01",
      "submission_deadline": "2026-12-01",
      "matched_topics": ["optimization", "digital twin"],
      "readiness_action": "accelerate benchmark validation"
    }
  ]
}
```

## Workflow

1. Search public venue and publisher pages.
2. Record deadlines, scope, article type, guest editors when public, and source URL.
3. Match calls to active PaperLab opportunities and readiness levels.
4. Recommend acceleration or retargeting actions.

## Safety Rules

- Do not use or store credentials for publisher sites.
- Record the date each call was checked.
- Do not infer acceptance likelihood from topical match.