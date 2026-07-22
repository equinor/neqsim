---
name: neqsim-pdf-ocr
version: "1.0.0"
description: "OCR-based text extraction from PDFs (scanned documents, P&IDs, vendor datasheets, engineering drawings) using OCRmyPDF, Tesseract, and pytesseract. USE WHEN: a PDF has no embedded text layer (scanned), pymupdf returns empty/low text, or the user explicitly asks to extract text/tags from a P&ID, mechanical drawing, or paper-original datasheet. Pairs with neqsim-technical-document-reading (which handles text-based PDFs and visual analysis via view_image)."
last_verified: "2026-04-26"
requires:
  python_packages:
    - pymupdf       # text-layer detection (already in devtools/pyproject.toml [pdf])
    - ocrmypdf      # add searchable text layer to scanned PDFs
    - pytesseract   # per-page OCR with custom PSM (P&ID tag extraction)
    - pdf2image     # rasterise PDF pages for pytesseract
  system_binaries:
    - tesseract     # Windows: choco install tesseract / macOS: brew install tesseract / Linux: apt install tesseract-ocr
    - poppler       # required by pdf2image on macOS/Linux (brew install poppler / apt install poppler-utils)
---

# NeqSim PDF OCR Skill

Extract machine-readable text from PDFs that don't have an embedded text layer —
scanned documents, P&IDs, mechanical arrangement drawings, vendor datasheets
exported as raster, inspection reports, and old paper-original technical
requirements. Complements [`neqsim-technical-document-reading`](../neqsim-technical-document-reading/SKILL.md):

| Use case                                  | Skill                                |
| ----------------------------------------- | ------------------------------------ |
| Text-layer PDFs, Word, Excel              | `neqsim-technical-document-reading`  |
| Visual analysis of figures/drawings       | `neqsim-technical-document-reading` (uses `view_image`) |
| **No text layer / scanned / P&ID tags**   | **`neqsim-pdf-ocr`** (this skill)    |

## When to Use This Skill

Load this skill when **any** of these are true:

- The user mentions OCR, scanned PDFs, or P&ID text/tag extraction
- A PDF returns empty or near-empty text from `pymupdf` / `pdfplumber`
- A document is a **P&ID** and you need equipment/instrument tag numbers as strings
- A vendor datasheet is a scan of a paper original (common pre-2010)
- An engineering drawing has labels, line numbers, or revisions that must be searchable

Do **not** load this skill for born-digital PDFs that already have a text layer —
those are faster and more accurate via `pdfplumber` / `pymupdf` (covered in
`neqsim-technical-document-reading`).

## Detection: Is OCR Actually Needed?

Always try fast text extraction **first**. Only fall back to OCR when the yield
is too low. The utility implements this automatically; the rule of thumb is:

```
average_chars_per_page < 50  →  treat as scanned, run OCR
```

```python
import fitz
doc = fitz.open("document.pdf")
yield_chars = sum(len(page.get_text("text")) for page in doc) / len(doc)
if yield_chars < 50:
    # scanned — needs OCR
    ...
```

## Tool Selection Matrix

| Tool          | Best for                                    | Why                                                                                  |
| ------------- | ------------------------------------------- | ------------------------------------------------------------------------------------ |
| **OCRmyPDF** | Add a searchable text layer to a scanned PDF | Idempotent (`--skip-text` skips already-OCR'd pages), preserves original, deskew/rotate built-in, parallel by default |
| **pytesseract + pdf2image** | Per-page OCR with custom PSM, P&ID tag extraction, programmatic control | Lets you set page-segmentation mode 11 (sparse text) for tag-heavy drawings, returns text directly |
| **pdfplumber / pymupdf** | Text-layer extraction (run before OCR)        | Fast, no external binaries — use whenever the text layer exists                      |
| **opencv** (preprocessing) | Deskew / denoise rotated P&ID scans before OCR | Improves Tesseract accuracy on poor scans                                            |

## P&ID-Specific Patterns

P&IDs are the hardest case for OCR. Use these settings:

- **DPI ≥ 400** (300 is too low — tag digits get lost)
- **Tesseract PSM 11** (`Sparse text. Find as much text as possible in no particular order`)
- **`--rotate-pages`** in OCRmyPDF (drawings often have rotated title-block text)
- **Multi-language**: `eng+nor` for Norwegian operator drawings
- **Post-filter with regex** to recover tags from noisy OCR output:

```python
import re
TAG = re.compile(r"\b[A-Z]{1,4}-\d{3,5}[A-Z]?\b")  # V-100, PT-2301A, FIC-12345
LINE = re.compile(r"\b\d{1,3}\"?-[A-Z]{1,4}-\d{3,6}-[A-Z0-9]+\b")  # 6"-PG-1001-A1A
```

## Code Patterns

### Pattern 1: Auto-OCR text extraction (recommended default)

```python
from devtools.pdf_ocr import extract_text

# Tries pymupdf first, falls back to OCR automatically if scanned
pages = extract_text("step1_scope_and_research/references/datasheet.pdf")
for page_num, text in pages.items():
    print(f"--- Page {page_num} ---\n{text}")
```

### Pattern 2: P&ID tag extraction

```python
from devtools.pdf_ocr import extract_tags

# pid_mode=True → 400 DPI, PSM 11 (sparse), force OCR
tags = extract_tags("references/PID-001.pdf", pid_mode=True)
# → ['FIC-2301', 'PT-2305', 'V-100', 'V-101', ...]
```

### Pattern 3: Add a searchable text layer (preserves drawing for archival)

```python
from devtools.pdf_ocr import ocr_pdf

# Produces references/scanned_ocr.pdf with embedded text layer
out = ocr_pdf(
    "references/scanned_datasheet.pdf",
    language="eng+nor",
    dpi=400,
    rotate=True,
    deskew=True,
)
# Now usable by pdfplumber / pymupdf / search tools
```

### Pattern 4: Combine with pdf_to_figures.py for full P&ID analysis

For a P&ID, do **both** rasterise + OCR. They're complementary:

```python
from devtools.pdf_to_figures import pdf_to_pngs
from devtools.pdf_ocr import extract_tags, extract_text

pdf = "references/PID-001.pdf"

# 1. Visual analysis — render to PNG, then use view_image (multimodal)
pngs = pdf_to_pngs(pdf, outdir="figures/", dpi=400)
# → use view_image(pngs[0]) to read symbols, topology, valve types

# 2. Textual extraction — OCR for tag numbers, line specs, notes
tags = extract_tags(pdf, pid_mode=True)
text_by_page = extract_text(pdf, force_ocr=True, dpi=400, psm=11)
```

### Pattern 5: CLI usage in task workflows

```powershell
# Extract text to JSON
python devtools/pdf_ocr.py step1_scope_and_research/references/datasheet.pdf `
    --json step1_scope_and_research/datasheet_text.json

# P&ID preset (400 DPI, sparse PSM, force OCR, print tags)
python devtools/pdf_ocr.py references/PID-001.pdf --pid

# Add searchable layer for archival
python devtools/pdf_ocr.py scanned.pdf --add-text-layer --output scanned_ocr.pdf
```

## Common Mistakes

| Mistake                                                         | Fix                                                                              |
| --------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Running OCR on a born-digital PDF                               | Try `pymupdf` first; only fall back when yield < 50 chars/page                   |
| 200 DPI for a P&ID                                              | Use ≥ 400 DPI — tag digits become unreadable below this                          |
| Default PSM (3) on a P&ID                                       | Use PSM 11 (`--psm 11`) — sparse-text mode finds isolated tag labels             |
| Forgetting `--rotate-pages` on OCRmyPDF                         | Drawings have rotated title blocks; without this, ~30% of text is lost           |
| `tesseract` not on PATH                                         | Install the binary system-wide; pip install alone is not enough                  |
| Using OCR'd text as ground truth                                | Always sanity-check tag patterns with a regex post-filter; OCR makes 1↔l, 0↔O errors |
| Running OCRmyPDF on already-searchable PDFs                     | Use `--skip-text` (default in our wrapper) instead of `--force-ocr`              |
| Ignoring Norwegian operator drawings                            | Use `language="eng+nor"` for NCS documents                                       |
| OCR-ing a scan, then expecting tables to be reconstructed       | Tesseract returns flowing text; for tables, OCR + pdfplumber on the OCR'd PDF    |

## Validation Checklist

When you've extracted text/tags from a scanned PDF:

- [ ] Average chars/page > 200 (low yield = OCR likely failed; raise DPI or check language)
- [ ] Tag count matches expectation (typical P&ID: 30–150 tags)
- [ ] No common confusions in tags: digit `0` vs letter `O`, `1` vs `l`, `5` vs `S`
- [ ] If pid_mode used, verify a sample of tags against the visual (`view_image` on rendered PNG)
- [ ] For task workflows, save extracted text as JSON in `step1_scope_and_research/`
- [ ] Source PDF stays in `step1_scope_and_research/references/` (per AGENTS.md task-folder rule)

## Installation

The OCR stack is an **optional extra** of `devtools/`:

```bash
pip install ocrmypdf pytesseract pdf2image pymupdf
```

Plus the system Tesseract binary:

| OS      | Command                                                              |
| ------- | -------------------------------------------------------------------- |
| Windows | `choco install tesseract` (or download UB Mannheim build)            |
| macOS   | `brew install tesseract poppler`                                     |
| Linux   | `apt install tesseract-ocr poppler-utils`                            |

Norwegian language pack (for NCS documents):

| OS      | Command                                                              |
| ------- | -------------------------------------------------------------------- |
| Windows | Bundled with UB Mannheim installer (select Norwegian during install) |
| macOS   | `brew install tesseract-lang`                                        |
| Linux   | `apt install tesseract-ocr-nor`                                      |

The `pdf_ocr.py` utility checks for these dependencies and prints a clear
remediation message if they're missing — never silently produces empty results.

## Related Skills

- [`neqsim-technical-document-reading`](../neqsim-technical-document-reading/SKILL.md) — text-based PDFs, Word, Excel, image analysis with `view_image`
- [`neqsim-stid-retriever`](../neqsim-stid-retriever/SKILL.md) — fetch the documents in the first place
- [`neqsim-process-extraction`](../neqsim-process-extraction/SKILL.md) — convert extracted text into NeqSim JSON
