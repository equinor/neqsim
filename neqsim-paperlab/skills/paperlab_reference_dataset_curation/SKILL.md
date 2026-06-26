---
name: paperlab_reference_dataset_curation
description: |
  Curate reference datasets for PaperLab validation, including source tracing,
  unit normalization, uncertainty notes, license status, and citation mapping.
---

# PaperLab Reference Dataset Curation

## When to Use

USE WHEN: extracting literature or benchmark data into files that support paper
claims, book examples, or regression baselines.

## Dataset Manifest Fields

```json
{
  "dataset_id": "co2_water_solubility_2024",
  "source_citation_key": "Author2024",
  "source_type": "paper_table",
  "extraction_method": "manual_table_transcription",
  "original_units": {"temperature": "C", "pressure": "MPa"},
  "normalized_units": {"temperature": "K", "pressure": "bara"},
  "uncertainty": "reported standard deviation where available",
  "license_or_use_note": "cite only; do not redistribute full source text",
  "files": ["reference_data/co2_water_solubility_2024.csv"]
}
```

## Workflow

1. Identify source and citation key.
2. Extract only the data needed for validation.
3. Preserve original units and normalize into a separate column or metadata field.
4. Add uncertainty and data-quality notes.
5. Link each dataset to hypotheses, figures, and claims.

## Pass Criteria

- Every numeric column has a unit.
- Every dataset has a citation and extraction method.
- Public outputs avoid restricted source text.

## Safety Rules

- Do not redistribute copyrighted tables wholesale when a minimal derived dataset is enough.
- Never silently round or smooth data used for validation.
- Mark transcription uncertainty when data was read from figures.