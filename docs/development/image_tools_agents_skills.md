---
title: Image Tools, Agents, and Skills in NeqSim
description: Overview of NeqSim image-related tooling, agents, and skills for reading P&IDs, PFDs, mechanical drawings, vendor datasheets, scanned PDFs, compressor maps, phase envelopes, and engineering screenshots.
---

## Purpose

NeqSim has an engineering-image workflow for turning visual technical material into structured data for simulation, design, and reporting. The workflow supports images and image-heavy documents such as P&IDs, PFDs, mechanical drawings, vendor datasheets, scanned PDFs, compressor and pump maps, phase envelopes, and screenshots.

The core flow is:

```text
PDF or image document
-> PDF-to-PNG conversion or direct image view
-> visual analysis and/or OCR
-> structured extraction
-> NeqSim JSON, route model, notebook, or report
```

## Core Tools

| Capability | Tool | Purpose |
|------------|------|---------|
| Direct image inspection | `view_image` | Reads PNG, JPG, JPEG, GIF, and WEBP images directly. |
| PDF page rasterization | [devtools/pdf_to_figures.py](../../devtools/pdf_to_figures.py) | Converts PDF pages to PNG images for visual analysis. |
| Scanned PDF OCR | [devtools/pdf_ocr.py](../../devtools/pdf_ocr.py) | Extracts text from scanned PDFs and engineering drawings. |
| P&ID tag OCR | [devtools/pdf_ocr.py --pid](../../devtools/pdf_ocr.py) | Uses high-DPI sparse OCR for equipment, instrument, and line tags. |
| STID document download | [devtools/stid_download.py](../../devtools/stid_download.py) | Downloads STID documents into a task folder and can convert PDFs to PNG. |
| Document extraction | [`neqsim-technical-document-reading`](../../.github/skills/neqsim-technical-document-reading/SKILL.md) | Provides structured extraction patterns for technical documents and images. |
| Report figure traceability | `results.json` figure sections | Links figures to captions, discussions, and key results. |

## Image-Aware Agents

| Agent | Best Use |
|-------|----------|
| [`technical.reader.agent.md`](../../.github/agents/technical.reader.agent.md) | Primary agent for reading technical documents and engineering images. Extracts structured data from P&IDs, drawings, datasheets, maps, and phase envelopes. |
| [`extract.process.agent.md`](../../.github/agents/extract.process.agent.md) | Converts extracted PFD, P&ID, and process data into NeqSim JSON or route models. |
| [`literature.scout.agent.md`](../../.github/agents/literature.scout.agent.md) | Retrieves papers, standards, STID documents, vendor documents, and image-heavy references for task workflows. |
| [`solve.task.agent.md`](../../.github/agents/solve.task.agent.md) | Runs the full task-solving workflow and consumes extracted image and document data in engineering studies. |
| [`review.agent.md`](../../.github/agents/review.agent.md) | Reviews task deliverables, including figure-to-discussion-to-result traceability. |
| [`notebook.example.agent.md`](../../.github/agents/notebook.example.agent.md) | Creates notebooks with executed cells, plots, saved figures, and results metadata. |
| [`documentation.agent.md`](../../.github/agents/documentation.agent.md) | Turns results, figures, and workflows into documentation. |

## Relevant Skills

| Skill | Use |
|-------|-----|
| [`neqsim-technical-document-reading`](../../.github/skills/neqsim-technical-document-reading/SKILL.md) | Main skill for extracting structured data from documents and engineering images. |
| [`neqsim-pdf-ocr`](../../.github/skills/neqsim-pdf-ocr/SKILL.md) | OCR for scanned PDFs, P&IDs, vendor datasheets, and engineering drawings. |
| [`neqsim-stid-retriever`](../../.github/skills/neqsim-stid-retriever/SKILL.md) | Retrieval of STID, vendor, P&ID, and datasheet documents into task folders. |
| [`neqsim-process-extraction`](../../.github/skills/neqsim-process-extraction/SKILL.md) | Conversion of extracted process data into NeqSim JSON or route models. |
| [`neqsim-professional-reporting`](../../.github/skills/neqsim-professional-reporting/SKILL.md) | Figure captions, figure discussions, linked results, and report traceability. |
| [`neqsim-notebook-patterns`](../../.github/skills/neqsim-notebook-patterns/SKILL.md) | Figure generation, notebook execution, and `results.json` integration. |
| [`neqsim-plant-data`](../../.github/skills/neqsim-plant-data/SKILL.md) | Plant historian data workflows when image or document extraction is combined with PI/Aspen data. |
| [`neqsim-unisim-reader`](../../.github/skills/neqsim-unisim-reader/SKILL.md) | UniSim model extraction through COM rather than image reading. Useful when the source is an actual simulator file instead of screenshots. |

## Supported Image and Drawing Types

| Image Type | Extracted Information |
|------------|----------------------|
| P&ID | Equipment tags, valve tags, instrument tags, line numbers, piping classes, connection topology, control loops, interlocks. |
| PFD | Equipment sequence, stream arrows, temperatures, pressures, flow rates, utility duties, recycle loops. |
| Mechanical arrangement drawing | Dimensions, nozzle sizes and orientations, standpipes, drains, elevations, piping runs, access limitations. |
| Vendor API datasheet image | Operating conditions, design pressure and temperature, seal data, bearing data, materials, leakage rates, utilities. |
| Compressor or pump performance map | Rated point, speed curves, head, flow, efficiency, surge line, stonewall, operating window. |
| Phase envelope | Critical point, cricondentherm, cricondenbar, operating point location, two-phase region. |
| Scanned report or datasheet | OCR text, tables where possible, document metadata, equipment tags, requirements. |
| Screenshot | Visible text, bullet lists, diagrams, high-level structure, manually readable data. |

## Recommended Workflows

### Image File

```text
PNG/JPG image
-> view_image
-> structured extraction
-> process JSON, notes, notebook, or report
```

### Born-Digital PDF

```text
PDF
-> pdfplumber or pymupdf text extraction
-> pdf_to_figures.py for diagrams and charts
-> view_image for visual pages
-> structured extraction
```

### Scanned PDF or P&ID

```text
Scanned PDF
-> pdf_to_figures.py at 300-400 DPI
-> view_image for visual topology
-> pdf_ocr.py for searchable text and tags
-> regex post-filtering for tags
-> structured extraction
```

### STID or Vendor Document Task

```text
STID/vendor source
-> stid_download.py into task references folder
-> optional PDF-to-PNG conversion
-> technical.reader extraction
-> process extraction or engineering analysis
-> results.json and report figures
```

## Structured Outputs

Image extraction can produce structured outputs such as:

- `PID_EXTRACTION`
- `VENDOR_DATASHEET_EXTRACTION`
- `PERFORMANCE_MAP_EXTRACTION`
- `PHASE_ENVELOPE_EXTRACTION`
- `MECHANICAL_ARRANGEMENT_EXTRACTION`
- `route_segments` for `PipingRouteBuilder`
- `figure_captions` and `figure_discussion` entries for reports
- NeqSim JSON for `ProcessSystem.fromJson()` or `ProcessSystem.fromJsonAndRun()`

## Important Practices

1. Convert PDFs to PNG before analyzing drawings, charts, and visual tables.
2. Use OCR only when text extraction is empty or unreliable.
3. Use 400 DPI and sparse OCR mode for P&IDs.
4. Cross-check visual extraction against text and tables from the same document.
5. Preserve original values and units alongside normalized values.
6. Flag uncertain values rather than guessing.
7. For task workflows, keep source PDFs in `step1_scope_and_research/references/` and converted PNGs in `figures/`.
8. For report-quality work, every important figure should have a caption, discussion, linked result, and recommendation.

## Current Limitations

- The workflow reads exported images and PDFs; it is not a native parser for Echo 3D, CAD, or full 3D model databases.
- OCR quality depends strongly on scan resolution and drawing clarity.
- Performance-map digitization is semi-structured: axes and points can be extracted, but critical values should be checked against vendor tables when available.
- P&ID topology extraction should be validated when it affects simulation wiring or safety conclusions.