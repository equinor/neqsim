# Skill: PaperLab Source PDF to HTML

## Purpose

Convert or plan conversion of source PDFs into clean, searchable HTML for
PaperLab books. Use this when lecture PDFs, public reports, exam PDFs, or
standards excerpts need to be searchable by agents and linked to chapter
figures, captions, and source manifests.

## Inputs

- Book directory with `source_manifest.json`, or a `--source-root` folder.
- PDF files from lectures, exercises, exams, public reports, or approved
  reference documents.
- Optional OCR output for scanned/image-only PDFs.

## Workflow

1. Inventory source files with `book-source-inventory`.
2. Run `python paperflow.py book-source-pdf-html-plan <book_dir>` to create a
   conversion manifest.
3. Convert PDFs using the selected local converter. A robust converter should:
   - preserve the source PDF,
   - extract text and images,
   - fall back to OCR or PyMuPDF when table-heavy PDFs fail,
   - write HTML under an `_HTML/` folder next to the source category.
4. Feed converted HTML and extracted images into `technical_figure_understanding`
   and `figure_dossier.json`.

## Output

- `source_pdf_html_plan.json`
- `source_pdf_html_plan.md`
- Converted HTML files and extracted resources when the conversion script is run.

## Quality Checks

- Count converted PDFs against the plan.
- Flag PDFs with very low extracted text length for OCR.
- Do not silently drop pages or figures.
- Record source path, output path, page count, text length, image count, and
  extraction method when the converter supports it.

## Safety Rules

- Never delete or overwrite source PDFs.
- Do not publish converted HTML for non-public or licensed material unless that
  is explicitly allowed.
- For standards, extract only metadata or allowed excerpts unless the standard's
  license permits broader reproduction.
