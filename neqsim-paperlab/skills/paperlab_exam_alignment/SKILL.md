# Skill: PaperLab Exam Alignment

## Purpose

Check whether a PaperLab course book prepares students for the associated
exercises and exams. Use this when a book has source folders with exams,
exercise PDFs, review chapters, learning objectives, or end-of-chapter
questions.

## Inputs

- `source_manifest.json` or a source root containing `exams/` and `exercises/`.
- Chapter markdown files with learning objectives and exercise sections.
- Review/exam-preparation chapters.
- Optional extracted exam text from `paperlab-source-pdf-to-html` or OCR.

## Workflow

1. Run `book-source-inventory` for source traceability.
2. Run `python paperflow.py book-exam-alignment <book_dir>`.
3. Review `exam_alignment.md` for topics marked `needs-review`.
4. For each weak topic, add a worked example, a self-test prompt, or a Chapter 26
   review item.
5. If exam PDF text is available, rerun with extracted text and update the topic
   matrix with evidence from actual problem statements.

## Output

- `exam_alignment.json`
- `exam_alignment.md`

## Topic Matrix

At minimum, map:

- field-development framing,
- PVT and flow performance,
- processing and separation,
- subsea, wells, and SURF,
- economics and scheduling,
- optimization and uncertainty,
- regulation and standards,
- CCS and gas quality.

## Safety Rules

- Do not reproduce confidential or copyrighted exam text unless allowed.
- Use topic frequencies and paraphrased problem types for public reports.
- Mark results as `needs-review` when source PDFs have not been OCR/extracted.
- Keep Chapter 26 concise; use cross-references to detailed worked examples in
  earlier chapters.
