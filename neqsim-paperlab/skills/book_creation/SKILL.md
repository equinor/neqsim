# Skill: Book Creation in PaperLab

## When to Use

USE WHEN: creating a new scientific book project, writing chapter content,
running book notebooks, building/rendering a book, or troubleshooting the book
pipeline. This skill covers the complete lifecycle from `book-new` to final
rendered outputs in HTML/Word/PDF/ODF.

DO NOT USE for: paper writing workflows (use `write_methods_section`,
`journal_formatting`, etc. instead).

---

## 1. Book Project Structure

Every book lives under `neqsim-paperlab/books/<slug>_<year>/`. The critical
file is **`book.yaml`** — it defines metadata, chapter ordering, parts, and
rendering settings.

```
books/<book_slug>/
├── book.yaml                  # Master manifest — defines EVERYTHING
├── refs.bib                   # Master bibliography
├── nomenclature.yaml          # Symbol definitions
├── frontmatter/
│   ├── title_page.md
│   ├── copyright.md
│   ├── dedication.md
│   └── preface.md
├── chapters/
│   ├── ch01_introduction/
│   │   ├── chapter.md         # Chapter text (markdown with LaTeX math)
│   │   ├── figures/           # Generated figures (PNG)
│   │   └── notebooks/         # Jupyter notebooks that generate figures
│   ├── ch02_topic_name/
│   │   ├── chapter.md
│   │   ├── figures/
│   │   └── notebooks/
│   └── ...
├── backmatter/
│   ├── glossary.md
│   └── author_bio.md
└── submission/                # Rendered outputs
    ├── book.html
    ├── book.docx
    ├── book.pdf
    ├── book.odf
    └── figures_chNN/          # PDF renderer copies figures here for Typst
```

### book.yaml Structure

```yaml
title: "Book Title"
subtitle: "Subtitle"
authors:
  - name: "Author Name"
    affiliation: "Institution"
    email: "email@example.com"
edition: "1st"
year: 2026
publisher: "springer"        # springer | wiley | crc | self
language: "en"

settings:
  page_size: "b5"            # b5 | a4
  font_size: 10
  line_spacing: 1.2
  two_sided: true
  chapter_numbering: true
  equation_numbering: "chapter"  # chapter-scoped: Eq. 3.1, 3.2, ...

frontmatter:
  - title_page
  - copyright
  - dedication
  - preface

parts:
  - title: "Part I: Foundations"
    chapters:
      - dir: "ch01_introduction"           # MUST match exact dir name
        title: "Introduction and Context"
      - dir: "ch02_topic_name"
        title: "Topic Name"
  - title: "Part II: Advanced"
    chapters:
      - dir: "ch03_another_topic"
        title: "Another Topic"

backmatter:
  - glossary
  - author_bio

nomenclature:
  file: "nomenclature.yaml"
  position: "after_toc"

bibliography:
  style: "numeric"
  file: "refs.bib"
```

### Critical Rules for book.yaml

1. **`dir` values MUST match actual directory names** under `chapters/`.
   Mismatches cause the renderer to silently skip chapters.
2. **Chapter numbering is sequential across all parts** — the renderers
   count 1, 2, 3, ... regardless of part boundaries.
3. **Do not create alternate directory names** (e.g., `ch03_cubic_eos` vs
   `ch03_cubic_equations_of_state`). Only the `dir` listed in `book.yaml`
   is used. Remove unused dirs to avoid confusion.
4. **Publisher profiles** in `books/_publisher_profiles/` define page size,
   margins, and fonts. The `publisher` key selects which profile to load.

---

## 2. Creating a New Book

### Command

```bash
cd neqsim-paperlab
python paperflow.py book-new "Book Title" --publisher springer --chapters 12
```

This creates the scaffold with empty chapters (`ch01` through `ch12`).

### Post-Scaffold Steps (MANDATORY)

1. **Rename chapter directories** to descriptive names:
   ```
   ch01 → ch01_introduction
   ch02 → ch02_thermodynamic_foundations
   ```

2. **Update book.yaml** to match the new directory names and set real titles.

3. **Organize into parts** by editing the `parts:` section in book.yaml.

4. **Fill frontmatter** — at minimum: title_page.md (title, authors),
   preface.md (motivation, audience, acknowledgements).

5. **Deep Literature Review and refs.bib (DO THIS BEFORE WRITING CHAPTERS)**:

   This step is **non-negotiable** — a book with sparse references signals
   shallow scholarship. Complete the bibliography BEFORE writing chapter content.

   a. **Build master refs.bib** at the book root with 100+ entries minimum.
      Organize into clearly commented sections by topic:
      ```bibtex
      % ─── Foundational thermodynamics ───
      @book{Prausnitz1999, ... }

      % ─── Equations of state ───
      @article{Soave1972, ... }
      ```
   b. **Mine existing PaperLab papers** — Search `papers/*/refs.bib` for
      related citations. Reuse BibTeX entries for consistency:
      ```bash
      grep -rl "keyword" papers/*/refs.bib
      ```
   c. **Ensure coverage per chapter** — Each chapter should have 10–20+ unique
      references identified before writing begins.
   d. **Include all categories**: seminal/foundational works, recent advances
      (last 5 years), textbooks, experimental data sources, competing methods,
      review articles.

   See `PAPER_WRITING_GUIDELINES.md` "MANDATORY: Literature Review and Citation
   Collection First" for the complete workflow and quality rules.

6. **Import content from PaperLab papers (MANDATORY)** — Before writing each
   chapter, check `papers/` for papers that cover the same topic:
   ```bash
   grep -rl "chapter_keyword" papers/*/paper.md papers/*/plan.json
   ```
   For each matching paper:
   - **Copy figures** from `papers/<paper>/figures/*.png` to `chapters/chNN/figures/`
   - **Copy tables** (markdown) from `paper.md` into `chapter.md`
   - **Incorporate results** — numerical benchmarks, validation data, performance comparisons
   - **Adapt text** — expand paper explanations for the book's broader audience
   - **Reuse equations** — copy LaTeX from paper.md, ensure notation matches book nomenclature

   This step ensures the book benefits from the detailed research already done
   in PaperLab papers. See `PAPER_WRITING_GUIDELINES.md` for the full workflow.

7. **Create chapter content** — each `chapter.md` uses markdown with:
   - `$$...$$` for display equations (rendered as OMML in Word, KaTeX in HTML)
   - `$...$` for inline math
   - `\cite{key}` tags for every claim, equation origin, and data source
   - `![Caption](figures/filename.png)` for figures
   - Standard markdown tables (rendered as booktabs-style)

---

## 3. Writing Chapter Content

### Equations

Display equations use `$$...$$` delimiters. They are automatically numbered
per-chapter (Eq. 1.1, 1.2, ..., Eq. 2.1, 2.2, ...).

```markdown
The cubic equation of state is:

$$
P = \frac{RT}{V_m - b} - \frac{a(T)}{(V_m + \epsilon b)(V_m + \sigma b)}
$$

For the SRK model, the alpha function is:

$$
\alpha(T) = \left[1 + m\left(1 - \sqrt{T_r}\right)\right]^2
$$
```

Inline math uses `$...$`:

```markdown
The acentric factor $\omega$ affects the alpha function $\alpha(T_r, \omega)$.
```

### Equation Pipeline (per renderer)

| Renderer | Display Equations | Inline Math |
|----------|-------------------|-------------|
| **Word** (.docx) | LaTeX → MathML (latex2mathml) → OMML (MML2OMML.XSL) → native Word | Same OMML pipeline |
| **HTML** | KaTeX auto-render (`$$...$$` blocks) | KaTeX inline |
| **PDF** | Pandoc → Typst (native math support) | Native Typst |
| **ODF** | Unicode fallback (Greek, sub/superscript) | Unicode fallback |

**Word renderer dependency**: Requires `MML2OMML.XSL` from Microsoft Office.
Location: `C:\Program Files\Microsoft Office\root\Office16\MML2OMML.XSL`.
The `math_utils.py` module handles the conversion pipeline.

### Figures

Figures are generated by Jupyter notebooks in `chapters/chNN/notebooks/` and
saved to `chapters/chNN/figures/`. Reference them in chapter.md as:

```markdown
![Phase diagram showing vapor-liquid equilibrium](figures/vle_phase_diagram.png)
```

The renderers handle figure numbering automatically (Figure 1.1, 1.2, ...).

### Tables

Use standard markdown tables. The Word renderer applies booktabs-style
formatting (horizontal rules only, no vertical lines):

```markdown
| Component | Tc (K) | Pc (kPa) | ω |
|-----------|--------|----------|------|
| Methane   | 190.56 | 4599     | 0.0115 |
| Ethane    | 305.32 | 4872     | 0.0995 |
```

### Code Blocks

Use fenced code blocks with language specifiers:

````markdown
```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
```
````

---

## 4. Jupyter Notebooks

Each chapter has a `notebooks/` directory containing `.ipynb` files that
generate the chapter's figures.

### Notebook Setup Cell (MANDATORY first cell)

```python
import importlib, subprocess, sys

try:
    from neqsim_dev_setup import neqsim_init, neqsim_classes
    ns = neqsim_init(recompile=False)
    ns = neqsim_classes(ns)
    NEQSIM_MODE = "devtools"
    print("NeqSim loaded via devtools (local dev mode)")
except Exception:
    try:
        import neqsim
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "neqsim"])
    from neqsim import jneqsim
    NEQSIM_MODE = "pip"
    print("NeqSim loaded via pip package")
```

### Saving Figures (MANDATORY pattern)

```python
import matplotlib.pyplot as plt
from pathlib import Path

# Resolve to chapter figures/ directory
NOTEBOOK_DIR = Path(globals().get(
    "__vsc_ipynb_file__", __file__
)).resolve().parent
FIGURES_DIR = NOTEBOOK_DIR.parent / "figures"
FIGURES_DIR.mkdir(exist_ok=True)

fig, ax = plt.subplots(figsize=(6, 4))
# ... plotting code ...
fig.savefig(FIGURES_DIR / "figure_name.png", dpi=150, bbox_inches="tight")
plt.show()
```

### Running Notebooks

```bash
# Run all book notebooks (compiles NeqSim first)
python paperflow.py book-run-notebooks books/<book_dir>

# Run specific chapter's notebooks
python paperflow.py book-run-notebooks books/<book_dir> --chapter ch04_association_theory

# Skip compilation if already up to date
python paperflow.py book-run-notebooks books/<book_dir> --no-compile

# Full build (compile + notebooks + check + render)
python paperflow.py book-build books/<book_dir> --format all
```

---

## 5. Building and Rendering

### Build Commands

```bash
cd neqsim-paperlab

# Full build: compile → notebooks → quality checks → render all formats
python paperflow.py book-build books/<book_dir> --format all

# Skip notebooks (use existing figure outputs)
python paperflow.py book-build books/<book_dir> --format all --skip-notebooks

# Skip compilation (notebooks still run using existing classes)
python paperflow.py book-build books/<book_dir> --format all --no-compile

# Skip both (render only — fastest for formatting iterations)
python paperflow.py book-build books/<book_dir> --format all --skip-notebooks --no-compile

# Single format
python paperflow.py book-render books/<book_dir> --format docx
python paperflow.py book-render books/<book_dir> --format html
python paperflow.py book-render books/<book_dir> --format pdf
python paperflow.py book-render books/<book_dir> --format odf
```

### Build Pipeline Steps

The `book-build` command runs these steps in order:

1. **Compile** — `mvnw compile` (ensures Java classes are current)
2. **Notebooks** — execute all `.ipynb` files, capturing errors
3. **Figure injection** — auto-insert new figures into chapter.md
4. **Quality checks** — structure, completeness, consistency
5. **Render** — produce output in requested format(s)

### Output Locations

| Format | Output Path | Size Guide |
|--------|-------------|------------|
| HTML | `submission/book.html` | Single-page with sidebar navigation, KaTeX |
| Word | `submission/book.docx` | Native OMML equations, TOC, page numbers |
| PDF | `submission/book.pdf` | Via Typst, publisher page size |
| ODF | `submission/book.odf` | Unicode equation fallback |

### PDF Figure Handling

The PDF renderer copies chapter figures to `submission/figures_chNN/` because
Typst needs them alongside the master `.typ` file. This is by design — the
copies are build artifacts in `submission/` and should not be committed.

---

## 6. Quality Checks

```bash
python paperflow.py book-check books/<book_dir>
```

Checks: structure (dirs exist), completeness (word counts), consistency
(cross-references), bibliography, figure references, nomenclature.

---

## 7. Common Issues and Solutions

### Issue: Chapter silently skipped in render

**Cause**: The `dir` value in book.yaml doesn't match the actual directory name.
**Fix**: Verify `ls chapters/` matches every `dir:` entry in book.yaml.

### Issue: Equations render as plain text in Word

**Cause**: `MML2OMML.XSL` not found (no Office installed) or `latex2mathml`
not installed.
**Fix**: Install Office or ensure `MML2OMML.XSL` is on the system. Install
dependencies: `pip install latex2mathml lxml`.

### Issue: Equations render as Unicode instead of proper math in ODF

**Expected**: ODF renderer uses Unicode Greek/subscript/superscript as fallback.
Full equation rendering in ODF requires a dedicated math library.

### Issue: Duplicate chapter directories

**Cause**: Chapters renamed but old directories not deleted.
**Fix**: Only the `dir` listed in book.yaml is used. Delete unused directories.

### Issue: Stale Python module imports

**Cause**: `__pycache__` holds old versions of modified tools.
**Fix**: Delete `tools/__pycache__/` and re-run.

### Issue: Figures not appearing in rendered output

**Cause**: Figure path in chapter.md doesn't match actual filename.
**Fix**: Use `![Caption](figures/exact_filename.png)` — paths are relative to
the chapter directory.

### Issue: PDF build fails

**Cause**: Missing `pandoc` or `typst` package.
**Fix**: Install pandoc (system package) and `pip install typst`.

---

## 8. Workflow for Iterative Chapter Writing

The fastest iteration loop for writing/editing chapters:

1. **Edit** `chapter.md` (add equations, text, figure references)
2. **Render single format** for preview:
   ```bash
   python paperflow.py book-build books/<book_dir> --format html --skip-notebooks --no-compile
   ```
3. **Open** `submission/book.html` in browser — instant feedback
4. **When satisfied**, do a full build:
   ```bash
   python paperflow.py book-build books/<book_dir> --format all --skip-notebooks
   ```

For notebook development:
1. Edit notebooks in VS Code / JupyterLab
2. Run individual notebooks manually to iterate on figures
3. When figures are ready, rebuild:
   ```bash
   python paperflow.py book-build books/<book_dir> --format all --skip-notebooks --no-compile
   ```

---

## 9. Tools Reference

| Tool | Purpose |
|------|---------|
| `tools/book_builder.py` | Config loading, chapter iteration, scaffolding |
| `tools/book_notebook_runner.py` | Notebook execution, figure injection, full build pipeline |
| `tools/book_render_html.py` | HTML renderer (KaTeX, sidebar nav) |
| `tools/book_render_word.py` | Word renderer (OMML equations, TOC, booktabs tables) |
| `tools/book_render_pdf.py` | PDF renderer (Pandoc → Typst pipeline) |
| `tools/book_render_odf.py` | ODF renderer (Unicode math fallback) |
| `tools/book_checker.py` | Quality checks (structure, completeness, consistency) |
| `tools/math_utils.py` | LaTeX → OMML/Unicode conversion |

---

## 10. Full End-to-End Example

```bash
# 1. Create project
cd neqsim-paperlab
python paperflow.py book-new "Thermodynamic Modeling" --publisher springer --chapters 8

# 2. Edit book.yaml — rename chapters, set titles, organize parts

# 3. Write chapter content in chapters/ch01_xxx/chapter.md

# 4. Create notebooks in chapters/ch01_xxx/notebooks/

# 5. Run notebooks to generate figures
python paperflow.py book-run-notebooks books/thermodynamic_modeling_2026/

# 6. Build all formats
python paperflow.py book-build books/thermodynamic_modeling_2026/ --format all

# 7. Check quality
python paperflow.py book-check books/thermodynamic_modeling_2026/

# 8. Iterate — edit, rebuild, review
python paperflow.py book-build books/thermodynamic_modeling_2026/ --format html --skip-notebooks --no-compile
```
