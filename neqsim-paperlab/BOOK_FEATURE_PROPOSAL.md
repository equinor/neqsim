# Proposal: Scientific Book Production in PaperLab

## Executive Summary

Add a `bookflow` subsystem to PaperLab that produces **publishing-quality
scientific books** (50–1000 pages) in PDF and Word, built on the same
NeqSim-driven approach as papers: every chapter is backed by Jupyter notebooks
that exercise the Java codebase, and every figure is a reproducible computation.

The proposal reuses existing renderers (typst/pandoc for PDF, python-docx for
Word, KaTeX/HTML for web preview) and extends them with book-specific features:
multi-chapter assembly, parts, table of contents, list of figures/tables,
nomenclature, index, and cross-referencing.

---

## 1. Why This Fits PaperLab

| Paper | Book |
|-------|------|
| 1 manuscript (paper.md) | N chapter files (ch01.md, ch02.md, …) |
| 1–3 notebooks | 10–50+ notebooks |
| 1 refs.bib | 1 master refs.bib (or per-chapter) |
| ~20 pages | 50–1000 pages |
| Single journal target | Publisher template (Springer, Wiley, CRC, self-pub) |
| One research question | Broad topic coverage |

The core loop is the same: **identify NeqSim capability → write computational
examples → produce validated figures → compose narrative**. A book is essentially
a coordinated set of "paper-like" chapters sharing a common theme, bibliography,
and notation.

---

## 2. Standard Scientific Book Structure

Based on Chicago Manual of Style, Springer Author Guidelines, and Wiley-VCH
standards:

```
book.yaml                    ← Book manifest (metadata + chapter ordering)
refs.bib                     ← Master bibliography
nomenclature.yaml            ← Symbols and abbreviations

frontmatter/
  title_page.md              ← Title, authors, publisher, edition
  copyright.md               ← Copyright notice, ISBN placeholder, edition info
  dedication.md              ← (optional)
  foreword.md                ← (optional) Written by someone else
  preface.md                 ← Author's preface — how/why the book was written
  acknowledgements.md        ← (optional)

chapters/
  part1_fundamentals/
    ch01_introduction/
      chapter.md             ← Chapter text
      notebooks/             ← Jupyter notebooks for this chapter
      figures/               ← Generated figures (PNG/SVG)
      tables/                ← Generated data tables
    ch02_thermodynamic_models/
      chapter.md
      notebooks/
      figures/
  part2_process_simulation/
    ch03_flash_calculations/
      ...
    ch04_separators/
      ...

backmatter/
  appendix_a.md              ← Derivations, data tables, etc.
  appendix_b.md
  glossary.md                ← Term definitions
  index.md                   ← (auto-generated from tagged terms)
  author_bio.md

submission/                  ← Final output
  book.pdf
  book.docx
  book.html                  ← Web preview
```

---

## 3. Book Manifest: `book.yaml`

The central configuration file that defines the book structure:

```yaml
title: "Thermodynamic Modeling with NeqSim"
subtitle: "From Equations of State to Process Simulation"
authors:
  - name: "Author Name"
    affiliation: "University / Company"
    email: "author@example.com"
edition: "1st"
year: 2026
publisher: "springer"          # springer | wiley | crc | elsevier | self
language: "en"
isbn: ""                       # Filled after acceptance

# Book-level settings
settings:
  page_size: "a4"              # a4 | letter | b5
  font_size: 11                # 10 | 11 | 12
  line_spacing: 1.5            # 1.0 | 1.5 | 2.0
  two_sided: true
  chapter_numbering: true
  equation_numbering: "chapter" # chapter (1.1, 1.2) | global (1, 2, 3)

# Ordered structure
frontmatter:
  - title_page
  - copyright
  - dedication
  - preface
  - acknowledgements

parts:
  - title: "Part I: Fundamentals"
    chapters:
      - dir: "ch01_introduction"
        title: "Introduction to Thermodynamic Modeling"
      - dir: "ch02_thermodynamic_models"
        title: "Equations of State"
      - dir: "ch03_mixing_rules"
        title: "Mixing Rules and Binary Parameters"

  - title: "Part II: Flash and Phase Equilibria"
    chapters:
      - dir: "ch04_flash_calculations"
        title: "Flash Calculation Algorithms"
      - dir: "ch05_multiphase"
        title: "Multiphase Equilibria"

  - title: "Part III: Process Simulation"
    chapters:
      - dir: "ch06_separators"
        title: "Separation Equipment"
      - dir: "ch07_compressors"
        title: "Compression Systems"
      - dir: "ch08_heat_exchangers"
        title: "Heat Exchange"

backmatter:
  - appendix_a
  - appendix_b
  - glossary
  # index is auto-generated
  - author_bio

# Cross-cutting concerns
nomenclature:
  file: "nomenclature.yaml"
  position: "after_toc"        # after_toc | backmatter

bibliography:
  style: "numeric"             # numeric | author-year
  file: "refs.bib"
```

---

## 4. Chapter Template: `chapter.md`

Each chapter follows a consistent template:

```markdown
# Introduction to Thermodynamic Modeling

<!-- Chapter metadata -->
<!-- Notebooks: 01_eos_overview.ipynb, 02_cubic_eos_comparison.ipynb -->
<!-- Estimated pages: 25-35 -->

## Learning Objectives

After reading this chapter, the reader will be able to:

1. Explain the physical basis of cubic equations of state
2. Select an appropriate EOS for a given fluid system
3. Use NeqSim to create fluids and run flash calculations

## 1.1 Background

The prediction of thermodynamic properties is fundamental to ...

## 1.2 Cubic Equations of State

The general cubic equation of state takes the form:

$$
P = \frac{RT}{v - b} - \frac{a(T)}{(v + \epsilon b)(v + \sigma b)}
$$

where parameters $\epsilon$ and $\sigma$ define the specific EOS variant
(see Table 1.1).

| EOS | $\epsilon$ | $\sigma$ | Year |
|-----|-----------|----------|------|
| van der Waals | 0 | 0 | 1873 |
| SRK | 0 | 1 | 1972 |
| PR | $1-\sqrt{2}$ | $1+\sqrt{2}$ | 1976 |

*Table 1.1: Parameters for common cubic equations of state.*

### 1.2.1 NeqSim Implementation

```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
```

![Phase envelope comparison](figures/phase_envelope_comparison.png)

*Figure 1.1: Phase envelope for a ternary natural gas computed with SRK
and PR equations of state using NeqSim.*

## 1.3 Summary

Key points from this chapter:
- Point 1
- Point 2

## Exercises

1. **Exercise 1.1:** Create a five-component natural gas and compute ...
2. **Exercise 1.2:** Compare SRK and PR predictions for ...

## References

<!-- Chapter-level references are merged into master refs.bib -->
```

---

## 5. CLI Commands

New `paperflow.py` subcommands for the book workflow:

```bash
# Create a new book project
python paperflow.py book-new "Thermodynamic Modeling with NeqSim" \
    --publisher springer \
    --chapters 12

# Add a chapter to an existing book
python paperflow.py book-add-chapter books/thermo_modeling_2026/ \
    --title "Flash Calculation Algorithms" \
    --part 2

# Build all notebooks for a chapter (or all chapters)
python paperflow.py book-build books/thermo_modeling_2026/ \
    --chapter ch04_flash_calculations     # or --all

# Assemble and render the full book
python paperflow.py book-render books/thermo_modeling_2026/ --format pdf
python paperflow.py book-render books/thermo_modeling_2026/ --format docx
python paperflow.py book-render books/thermo_modeling_2026/ --format html

# Render a single chapter (fast preview during writing)
python paperflow.py book-render books/thermo_modeling_2026/ \
    --chapter ch04_flash_calculations --format pdf

# Quality check (extends paper iterate logic to book scale)
python paperflow.py book-check books/thermo_modeling_2026/ --check all

# Book status overview
python paperflow.py book-status books/thermo_modeling_2026/

# Generate table of contents, list of figures, list of tables
python paperflow.py book-toc books/thermo_modeling_2026/

# Generate nomenclature from all chapters
python paperflow.py book-nomenclature books/thermo_modeling_2026/
```

---

## 6. Rendering Pipeline

### 6.1 PDF via Typst (Primary — Best Quality)

Typst is already used for paper PDF rendering. It natively supports:
- Book/report document class with chapters
- Automatic table of contents, list of figures
- Chapter-aware equation numbering (Eq. 3.1, 3.2, ...)
- Cross-references between chapters
- Page headers with chapter titles
- Professional typography (New Computer Modern font)

The pipeline:
1. Parse `book.yaml` for structure and metadata
2. For each chapter, preprocess `chapter.md` (strip HTML comments, fix math)
3. Generate a master typst document with `#include` for each chapter
4. Add frontmatter pages (title, copyright, preface, etc.)
5. Add backmatter (appendices, glossary, bibliography)
6. Compile with `typst compile` → single `book.pdf`

For publisher submission (Springer, Wiley), also generate LaTeX via the
existing `latex_pipeline.py` with book-class extensions.

### 6.2 Word via python-docx

Extend existing `word_renderer.py`:
1. Create a single Document with book-level styles
2. Add section breaks between chapters
3. Use Heading 1 for chapters, Heading 2/3 for sections
4. Chapter-prefixed figure/table/equation numbering
5. Auto-generated TOC field (updates on open in Word)
6. Page breaks for parts, chapters
7. Header/footer with chapter title and page number

### 6.3 HTML (Web Preview)

Extend existing `render_html_generic.py`:
1. Multi-page HTML with navigation sidebar (chapter list)
2. Or single-page HTML with internal anchor links
3. KaTeX for math, syntax-highlighted code blocks
4. Responsive design for reading on various screens
5. Search functionality (client-side)

---

## 7. Publisher Templates

Each publisher has specific formatting requirements:

### 7.1 Springer (Most Common for Technical Books)

```yaml
# books/_publisher_profiles/springer.yaml
name: "Springer"
page_size: "b5"              # 155 x 235 mm (Springer standard)
margins:
  top: 2.5cm
  bottom: 2.5cm
  inner: 2.0cm
  outer: 1.5cm
font: "Times New Roman"
font_size: 10
line_spacing: 1.15
chapter_style: "numbered"    # "Chapter 1" header
figure_style: "Fig. 1.1"
table_style: "Table 1.1"
equation_numbering: "chapter"
bibliography_style: "numeric"
index: true
latex_class: "svmono"        # Springer monograph class
```

### 7.2 Wiley

```yaml
name: "Wiley"
page_size: "letter"
font: "Palatino"
font_size: 11
chapter_style: "numbered"
latex_class: "WileySTM-v1"
```

### 7.3 CRC Press / Taylor & Francis

```yaml
name: "CRC Press"
page_size: "a4"
font: "Computer Modern"
font_size: 11
latex_class: "kraninger"
```

### 7.4 Self-Published / Open Access

```yaml
name: "Self-Published"
page_size: "a4"
font: "New Computer Modern"
font_size: 11
cover_page: true
isbn: ""                     # User provides
license: "CC-BY-4.0"        # Creative Commons
```

---

## 8. Quality Checks (Book-Scale)

Extend the existing `iterate` quality checks for book scope:

| Check | What it verifies |
|-------|------------------|
| `structure` | All chapters listed in book.yaml exist; required sections present |
| `completeness` | TODO count per chapter; total word count vs target; figure coverage |
| `consistency` | Notation consistent across chapters (nomenclature.yaml); consistent terminology |
| `cross-refs` | All `[Chapter X]`, `[Figure Y.Z]`, `[Eq. Z.W]` references resolve |
| `bibliography` | All `\cite{key}` resolve to refs.bib; no orphan references |
| `notebooks` | All referenced notebooks exist and have been executed |
| `figures` | All referenced figures exist; resolution adequate for print (300 dpi) |
| `nomenclature` | All symbols in equations appear in nomenclature.yaml |
| `index` | Index terms cover key concepts (minimum density) |
| `page_estimate` | Word count → page estimate per chapter; total vs target range |
| `duplicates` | Detect duplicate content across chapters |

---

## 9. Example Book Templates

### 9.1 Textbook Template (~300–500 pages)

```
"Applied Thermodynamics with NeqSim"

Part I: Foundations (Chapters 1-4, ~100 pages)
  Ch 1: Introduction to Thermodynamic Modeling
  Ch 2: Equations of State
  Ch 3: Mixing Rules and Component Properties
  Ch 4: Phase Equilibria Fundamentals

Part II: Flash Calculations (Chapters 5-8, ~120 pages)
  Ch 5: Two-Phase Flash Algorithms
  Ch 6: Multiphase Flash
  Ch 7: Stability Analysis
  Ch 8: Special Flash Types (PS, PH, UV)

Part III: Process Applications (Chapters 9-12, ~120 pages)
  Ch 9:  Separation Processes
  Ch 10: Compression and Expansion
  Ch 11: Heat Exchange
  Ch 12: Distillation

Part IV: Advanced Topics (Chapters 13-15, ~80 pages)
  Ch 13: CPA for Polar Systems
  Ch 14: Electrolyte Systems
  Ch 15: Dynamic Simulation

Appendices:
  A: NeqSim Component Database
  B: Mathematical Derivations
  C: Notation and Units
```

### 9.2 Monograph Template (~100–200 pages)

```
"Advanced Flash Algorithms for Thermodynamic Simulation"

Ch 1: Introduction and Literature Review (~20 pages)
Ch 2: Mathematical Framework (~25 pages)
Ch 3: Successive Substitution Methods (~25 pages)
Ch 4: Newton-Raphson Methods (~25 pages)
Ch 5: Hybrid Approaches (~25 pages)
Ch 6: Multiphase Extensions (~25 pages)
Ch 7: Benchmarks and Validation (~30 pages)
Ch 8: Conclusions and Future Work (~10 pages)

Appendices:
  A: Proof of Convergence Theorems
  B: Benchmark Data Sets
```

### 9.3 Handbook / Reference Template (~500–1000 pages)

```
"NeqSim Handbook: Thermodynamics and Process Simulation"

Part I: Thermodynamic Models
  Ch 1-6: EOS, mixing rules, CPA, electrolytes, ...

Part II: Physical Properties
  Ch 7-10: Viscosity, thermal conductivity, surface tension, ...

Part III: Phase Equilibria
  Ch 11-14: VLE, LLE, VLLE, hydrates, wax, ...

Part IV: Process Equipment
  Ch 15-22: Separators, compressors, HX, columns, pipes, ...

Part V: Flow Assurance
  Ch 23-26: Hydrates, wax, corrosion, slugging, ...

Part VI: Field Development
  Ch 27-30: Economics, production profiles, subsea, ...

Appendices:
  A-F: Data tables, correlations, standards reference, ...
```

---

## 10. Implementation Plan

### Phase 1: Foundation (Estimated scope: ~500 lines)

Files to create:
- `books/` directory (parallel to `papers/`)
- `templates/book_chapter.md` — chapter template
- `templates/book_frontmatter/` — title, copyright, preface, etc.
- `tools/book_builder.py` — core assembly logic
  - Parse `book.yaml`
  - Assemble chapters into ordered document
  - Chapter-aware numbering for figures, tables, equations
  - Cross-reference resolution
  - TOC / LOF / LOT generation

CLI additions to `paperflow.py`:
- `book-new` — scaffold a book project
- `book-add-chapter` — add a chapter directory
- `book-status` — word count, completion, notebook status per chapter

### Phase 2: Renderers (~300 lines)

Extend existing renderers:
- `tools/book_render_pdf.py` — typst-based PDF (extends render_pdf.py)
- `tools/book_render_word.py` — python-docx Word (extends word_renderer.py)
- `tools/book_render_html.py` — HTML web preview (extends render_html_generic.py)

CLI additions:
- `book-render` — produce PDF, Word, or HTML output

### Phase 3: Quality & Polish (~200 lines)

- `tools/book_checker.py` — book-level quality checks
- Cross-chapter consistency validation
- Nomenclature extraction and validation
- Index term extraction
- Publisher profile system (`books/_publisher_profiles/`)

CLI additions:
- `book-check` — run quality checks
- `book-nomenclature` — extract/validate symbols
- `book-toc` — preview table of contents

### Phase 4: Workflow Integration

- `workflows/new_book.yaml` — agent workflow for book production
- Integration with trending_topics.py for chapter topic suggestions
- Integration with research_scanner.py for identifying book-worthy topics

---

## 11. Technical Dependencies

All already available in the PaperLab environment:

| Dependency | Purpose | Status |
|-----------|---------|--------|
| `typst` (Python package) | PDF rendering | Already used by render_pdf.py |
| `pandoc` (CLI) | Markdown → typst/LaTeX conversion | Already used |
| `python-docx` | Word document generation | Already used by word_renderer.py |
| `PyYAML` | book.yaml parsing | Already in requirements.txt |
| `latex2mathml` | Equation rendering in Word | Already used |
| `lxml` | OMML equation pipeline | Already used |
| `bibtexparser` | Bibliography management | Already used by bib_validator.py |

No new dependencies needed.

---

## 12. Comparison with Alternatives

| Approach | Pros | Cons |
|----------|------|------|
| **PaperLab book module** (this proposal) | Reuses existing renderers; integrated with NeqSim notebooks; consistent with paper workflow; no new deps | Must implement assembly logic |
| **Jupyter Book** | Mature, well-supported | Separate ecosystem; hard to get publisher-quality PDF; not integrated with PaperLab tools |
| **Quarto Book** | Modern, multi-format | Requires Quarto install; separate config system; not integrated with NeqSim workflow |
| **Pure LaTeX** | Maximum control | Steep learning curve; hard to maintain; Word output difficult |
| **Sphinx** | Good for API docs | Not designed for scientific books; weak math support in PDF |

The PaperLab approach wins because it **reuses everything already built** — the
same figure validators, prose quality checks, citation tools, BibTeX handling,
equation rendering, and NeqSim bootstrap — just scaled from one paper to many
chapters.

---

## 13. Conclusion

This is feasible and a natural extension of PaperLab. The key insight is that a
scientific book is structurally a **coordinated collection of paper-like chapters**,
and PaperLab already has all the machinery for individual chapters. What's needed
is:

1. A `book.yaml` manifest for multi-chapter coordination
2. An assembly layer that concatenates chapters with proper numbering
3. Extended renderers for book-length PDF/Word/HTML
4. Book-scale quality checks

The existing typst, python-docx, and HTML renderers handle 80% of the work.
The remaining 20% is assembly logic and cross-chapter coordination.

**Recommended first book:** "Applied Thermodynamics with NeqSim" — a textbook
covering the full NeqSim capability set, with each chapter backed by
computational examples. This would simultaneously produce a publishable book
and create a comprehensive test/example suite for the codebase.
