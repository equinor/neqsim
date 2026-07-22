---
name: neqsim-document-intelligence-extraction
version: "0.1.0"
description: "Classify mixed engineering documents and images, route native text/table, OCR, and vision extraction, and produce source-traceable evidence packages with confidence and human-review gates. USE WHEN: a NeqSim task receives PDFs, scans, Word/Excel files, drawings, charts, photographs, or multiple conflicting engineering sources."
last_verified: "2026-07-11"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Document Intelligence Extraction

Use this skill as the intake layer for documents and images supplied to NeqSim tasks. It makes extraction automatic where tools are available without treating OCR or model output as verified engineering data.

## When to Use

- A task receives PDF, DOCX, XLSX, CSV, HTML, XML, presentation, image, scan, drawing, chart, or photograph inputs.
- A PDF may contain both embedded text and visual engineering content.
- Multiple sources must be reconciled without silently choosing one value.
- Extracted values will feed simulation, design, safety, operations, or reporting workflows.

## Inputs

- `source_path`: source file path; retain the original file unchanged.
- `embedded_text_chars`: optional PDF text-layer yield used to trigger OCR.
- `contains_visuals`: optional Office-document visual-content hint.
- Adapter-produced facts with original text, page or locator, extraction method, confidence, and optional normalized value/unit.

## Outputs

- `ExtractionPlan`: source kind plus ordered native-text, table, OCR, rendering, and vision operations.
- `ExtractionResult`: schema-versioned evidence package with facts, quality score, warnings, gaps, and review status.
- Conflict records for fields whose normalized values differ across sources.

## Engineering Method

The skill uses a staged, loss-minimizing method: preserve native structure, add OCR only when
the text layer is absent or weak, use vision when spatial meaning matters, and reconcile all
methods into evidence rather than free-form prose. Its Python package implements deterministic
classification, routing, provenance validation, review gates, quality scoring, and conflict
detection. Runtime adapters perform format parsing, OCR, and multimodal inference. This is an
engineering-data governance workflow, not a validated physical model or a substitute for
document control.

## Extraction Workflow

1. Inventory files recursively and preserve original names, hashes, and relative paths.
2. Classify by actual format where possible, not filename alone. Reject encrypted, corrupt, unexpectedly executable, or unsupported content for manual triage.
3. Prefer structured/native extraction first:
   - PDF text layer and table geometry;
   - DOCX paragraphs, tables, headers, footnotes, and embedded media;
   - XLSX sheet names, cells, formulas, merged ranges, hidden rows/columns, and units;
   - CSV/JSON/XML structure without flattening it to prose.
4. For low-yield/scanned PDFs, render pages and run OCR with word coordinates. Keep OCR text separate from native text.
5. Use multimodal vision for drawings, P&IDs, charts, photographs, symbols, topology, annotations, and spatial relationships. OCR alone cannot establish these semantics.
6. Reconcile native text, OCR, tables, and vision. Do not overwrite disagreements; emit a conflict requiring review.
7. Normalize values and units while preserving the exact original value, text, and unit.
8. Gate every safety-critical, ambiguous, or confidence-below-0.85 fact as `needs_review`.
9. Hand the evidence package to a document-type skill for engineering interpretation, then to the relevant NeqSim model.

## Python Usage Pattern

```python
from document_intelligence_extraction import DocumentIntelligenceExtractor, EvidenceFact

extractor = DocumentIntelligenceExtractor()
plan = extractor.plan("equipment_datasheet.pdf", embedded_text_chars=420)

fact = EvidenceFact(
    field="design_pressure",
    value=150.0,
    unit="bara",
    original_text="Design pressure: 150 bar(a)",
    page=2,
    locator="table:Design Conditions,row:Pressure",
    method="native_tables",
    confidence=0.98,
    safety_critical=True,
)
result = extractor.package(plan, "equipment_datasheet", [fact])
payload = result.to_dict()
```

The package does not embed one OCR or AI vendor. A harness executes each planned operation using available parsers, OCR engines, and multimodal tools, then creates `EvidenceFact` objects.

## Evidence Contract

Every fact must include:

- semantic field name and extracted value;
- original text and original unit;
- page number or stable locator such as sheet/cell, table/row, bounding box, or XML path;
- extraction method and confidence from 0 to 1;
- normalized value/unit when conversion is performed;
- ambiguity and safety-critical flags;
- review status assigned by the contract.

File-level metadata should additionally preserve content hash, document title/number, revision, date, language, page/sheet count, and extraction-tool versions.

## Validation Checklist

- [ ] Originals remain unchanged and a content hash is recorded by the harness.
- [ ] Native extraction was attempted before OCR.
- [ ] Every visual page was rendered and assessed when layout carries meaning.
- [ ] Tables retain row/column provenance and multi-page continuations are checked.
- [ ] Every fact has original text plus page or locator.
- [ ] Values and units are normalized without discarding originals.
- [ ] Conflicting sources are reported rather than resolved silently.
- [ ] Safety-critical and low-confidence facts require human review.
- [ ] Missing expected fields are listed as gaps, never fabricated.
- [ ] Downstream NeqSim input validation runs after extraction review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| A scanned PDF appears empty | Only the embedded text layer was read | Render pages, OCR them, and retain coordinates |
| P&ID tags are found but topology is wrong | OCR was used without visual reasoning | Use vision plus a specialized P&ID topology skill |
| Spreadsheet units disappear | The sheet was flattened to CSV too early | Read cells, headers, merged ranges, and formulas natively |
| Conflicting design values are silently replaced | Sources were merged by field name | Use `find_conflicts` and require review |
| A plausible number enters a simulation | Provenance/review gates were skipped | Reject facts without original text and page/locator |

## Limitations

- This skill routes and governs extraction; parser, OCR, and multimodal quality depend on the runtime adapters and source quality.
- Handwriting, faint scans, dense drawings, unusual fonts, password protection, and proprietary formats may require specialist tools or manual transcription.
- Confidence is a triage signal, not proof of correctness.
- It does not infer missing engineering values or replace document control and qualified review.

## Cooperation and Handoffs

- Chain to `neqsim-technical-document-reading` for document-type schemas, engineering normalization, and physical validation.
- Chain to `neqsim-pdf-ocr` when PDF text yield is low or pages are scanned.
- Chain to P&ID, standards, compressor-chart, fluid, process-extraction, or other domain skills only after the evidence package is built.
- Enterprise retrieval, identifiers, controlled-document status, and internal governance remain in enterprise skills; they should emit the same evidence contract. Enterprise **document/attachment retrieval** skills and task-solver agents (which download PEPR/STID/maintenance attachments and drawings) use this skill as their **intake/extraction layer** — every downloaded document, drawing, chart, or photograph is planned and packaged here (native text, OCR, and vision) before interpretation, so all information (including figures, photos, and P&IDs) reaches the model as governed evidence facts.
- Downstream calculations should use NeqSim input validation and retain links back to evidence facts.

## Related NeqSim Functionality

The skill supplies governed inputs to NeqSim workflows such as `ProcessSystem.fromJsonAndRun`, `SimulationValidator.validate`, process equipment sizing, PVT simulations, and MCP tools including `runProcess`, `runPVT`, `runPipeline`, and `validateInput`. It performs no thermodynamic calculation itself.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- W3C Web Content Accessibility Guidelines for text alternatives and document accessibility concepts
