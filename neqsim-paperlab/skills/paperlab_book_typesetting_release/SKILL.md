---
name: paperlab_book_typesetting_release
description: |
  Final production review for PaperLab books across HTML, PDF, DOCX, and ODF.
  Use for responsive layout, page breaks, equation rendering, table overflow,
  caption style, running headers, figure placement, and release-candidate polish.
---

# PaperLab Book Typesetting Release

## When to Use

USE WHEN: preparing a PaperLab book for professional review, course release, or
publisher submission. This skill checks rendered outputs, not only Markdown
source. Use after content, figure, citation, and notebook checks are mostly stable.

DO NOT USE for: scientific validation of claims. Pair with
`paperlab_scientific_traceability_audit` for that.

## Required Render Targets

For a release candidate, render at least:

```powershell
python paperflow.py book-build books/<book> --format html --skip-notebooks --no-compile
python paperflow.py book-render books/<book> --format docx
python paperflow.py book-render books/<book> --format pdf
python paperflow.py book-render books/<book> --format odf
```

Use `--format odf`, not `--format odt`.

## HTML Checks

- No horizontal page overflow at 500 px, 768 px, 1024 px, and desktop widths.
- Sidebar behavior is usable on desktop and hidden or collapsed on small screens.
- Cover, title, figures, equations, and tables are not clipped.
- Tables scroll horizontally only inside the table area, not the full page.
- KaTeX equations render without raw LaTeX showing in normal reading mode.
- Standalone HTML has embedded images when generated with the book-root
  `_embed_images.py` helper.

## PDF Checks

- Correct page size, margins, font size, and publisher profile.
- Chapter openings start cleanly, preferably recto for two-sided books.
- Running headers are absent from title pages and sensible on body pages.
- Tables use booktabs-style rules and do not overflow the text block.
- Figures stay close to their first reference and do not split from captions.
- Equations are numbered by chapter and do not collide with text.
- No orphan headings, widowed single lines, or large blank areas caused by floats.

## DOCX Checks

- Equations become native Word equations when the OMML conversion dependency is available.
- Figure captions use consistent style and numbering.
- Cross-references and citation text survive conversion.
- Tables have readable column widths and no vertical-rule clutter.
- Heading levels map correctly to Word styles and table of contents generation.

## ODF Checks

- Unicode math fallback is readable.
- Tables and figures remain within page bounds.
- Styles are not flattened into uneditable direct formatting where avoidable.

## Release Checklist

```markdown
## Typesetting Release Review

- HTML: pass | warn | fail
- PDF: pass | warn | fail
- DOCX: pass | warn | fail
- ODF: pass | warn | fail
- Blocking issues:
  - <issue>
- Non-blocking polish:
  - <issue>
- Recommended renderer fix:
  - <file/function or none>
```

## Safety Rules

- Fix renderer source files, not generated files in `submission/`.
- Do not hand-edit `submission/book.typ`, `book.html`, or `book.docx` as a source of truth.
- When a rendered issue is caused by Markdown source, fix the chapter source and rebuild.
- Record viewport, renderer command, and output path for every reported issue.
