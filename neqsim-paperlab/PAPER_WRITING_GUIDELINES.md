# Paper Writing Guidelines — Algorithm-First Scientific Papers

## Foundational Principle: Papers Improve NeqSim

**PaperLab papers are not just about algorithms — they are the mechanism through
which NeqSim's codebase is developed, validated, and improved.**

Every paper produced through PaperLab must leave the NeqSim repository measurably
better than before. The paper process drives:

| What the paper does | What NeqSim gains |
|--------------------|-------------------|
| Benchmarks an algorithm across 1000+ cases | New test suite permanently in `src/test/` |
| Validates against experimental data | Calibrated model with known accuracy bounds |
| Compares methods (A vs B) | Performance-optimized implementation |
| Applies to an engineering problem | Documented, tested workflow |
| Responds to reviewer critique | Bug fixes, edge-case handling, robustness |

Before starting a paper, answer: **"What will be committed to the NeqSim repo when
this paper is accepted?"** If the answer is "nothing," the paper is out of scope.

### NeqSim Improvement Checklist (per paper)

- [ ] At least one new or improved Java class in `src/main/java/neqsim/`
- [ ] New JUnit tests validating the paper's claims in `src/test/java/neqsim/`
- [ ] Benchmark data stored as test fixtures in `src/test/resources/` or `examples/`
- [ ] Updated JavaDoc on modified classes
- [ ] Entry in `docs/development/TASK_LOG.md`

---

## MANDATORY: Literature Review and Citation Collection First

**Before writing a single sentence of a paper or book chapter, you MUST complete a
deep literature review and build a comprehensive bibliography.** This is the
non-negotiable first step for every paper and every book project.

### Why Literature First?

A book or paper with sparse references signals shallow scholarship. Readers (and
reviewers) judge depth by the breadth and quality of the bibliography:

| Project type | Minimum references | Target references |
|---|---|---|
| Journal paper | 30 | 40–60 |
| Conference paper | 15 | 25–40 |
| Book (full) | 100 | 150–300+ |
| Book chapter | 15 per chapter | 20–40 per chapter |

### Literature Review Workflow (Papers)

**Step 0 — before plan.json, before any writing:**

1. **Survey the field** — Identify 5–10 key review papers and seminal works
   in the topic area. Read abstracts and introductions to map the landscape.
2. **Build refs.bib** — Collect BibTeX entries for ALL potentially relevant works.
   Aim for 2× the minimum count (you'll prune later). Include:
   - Foundational/seminal papers (the "must-cite" classics)
   - Recent advances (last 5 years)
   - Competing methods and alternative approaches
   - Experimental data sources used for validation
   - Textbooks that provide background theory
3. **Check existing PaperLab papers** — Search `papers/*/refs.bib` for related
   citations. Reuse BibTeX entries from other PaperLab papers when relevant.
   This ensures consistency across the project and avoids duplicating effort:
   ```bash
   grep -rl "keyword" papers/*/refs.bib
   ```
4. **Produce literature_map.md** — Use the literature-reviewer agent or write
   manually. Organize by theme, not chronologically.
5. **Identify gaps** — Write gap_statement.md articulating what's missing.

Only AFTER steps 1–5 should you create `plan.json` and begin writing.

### Literature Review Workflow (Books)

**Step 0 — before writing any chapter:**

1. **Build a comprehensive master refs.bib** at the book root with ALL references
   across all chapters. Aim for 100+ entries minimum for a full book. Organize
   the bib file into clearly commented sections by topic area:
   ```bibtex
   % ─── Foundational thermodynamics ───
   @book{Prausnitz1999, ... }
   @book{SmithVanNess2005, ... }

   % ─── Equations of state ───
   @article{Soave1972, ... }
   @article{PengRobinson1976, ... }

   % ─── Association theory ───
   @article{Wertheim1984, ... }
   ```
2. **Mine existing PaperLab papers for citations** — Every paper in `papers/`
   has a `refs.bib`. Extract relevant entries for the book. This is a major
   source of high-quality, pre-verified references:
   ```bash
   # Find all refs.bib files
   find papers/ -name "refs.bib" -exec grep -l "CPA\|association\|equation.of.state" {} \;
   ```
3. **Ensure coverage per chapter** — Each chapter should cite at least 10–20
   unique references. Before writing a chapter, identify which refs.bib entries
   belong to that chapter's topic.
4. **Cite as you write** — Every factual claim, historical attribution, method
   description, and data source gets a `\cite{}` tag. Do not write "as shown in
   the literature" — cite the specific work.
5. **After all chapters are written**, run the bibliography validation:
   ```bash
   python paperflow.py validate-bib books/<book_dir>/
   ```
   Verify: no orphan bib entries (every entry is cited), no unresolved cite tags,
   references are numbered sequentially.

### Cross-Referencing PaperLab Papers

PaperLab papers share a common knowledge base. When writing a new paper or book:

1. **Search existing paper bibliographies** for relevant entries:
   - `papers/implicit_cpa_performance_2026/refs.bib` — CPA algorithm references
   - `papers/tpflash_algorithms_2026/refs.bib` — Flash algorithm references
   - `papers/gibbs_minimization_2026/refs.bib` — Chemical equilibrium references
   - `papers/electrolyte_cpa_advanced_2026/refs.bib` — Electrolyte references
   - `papers/thermal_conductivity_methods_2026/refs.bib` — Transport property references
   - (and all others in `papers/`)
2. **Use consistent BibTeX keys** across papers and books. If `Michelsen1982a`
   is used in one paper, use the same key everywhere.
3. **Cite PaperLab papers themselves** when they are published or under review:
   ```bibtex
   @article{OurTPflash2026,
     title = {Systematic Characterization of a Hybrid Flash Algorithm},
     ...
   }
   ```

### MANDATORY: Reuse Content from PaperLab Papers

PaperLab papers are deep-dive research artifacts. When writing a **book**, the
corresponding paper is often a richer, more detailed treatment of the same topic.
**You MUST check for relevant papers and import their content into book chapters.**

**What to import:**

| Asset | How to reuse |
|---|---|
| **Figures** (PNG/PDF in `papers/*/figures/`) | Copy to chapter `figures/` dir, reference in chapter.md |
| **Tables** (markdown in `papers/*/paper.md`) | Copy table markdown into chapter.md, adapt caption for book context |
| **Results & data** (numerical values, benchmarks) | Incorporate into chapter narrative with proper attribution |
| **Text & explanations** (paragraphs, derivations) | Adapt and expand for book audience (more background, less jargon) |
| **Equations** (LaTeX in paper.md) | Reuse exact LaTeX, ensure notation matches book nomenclature |
| **Code examples** (Python/Java in paper) | Include as code blocks or move to chapter notebooks |

**Workflow for each book chapter:**

1. **Identify matching papers** — Before writing chapter N, search `papers/` for
   papers that cover the same topic:
   ```bash
   # Search paper titles and content for chapter keywords
   grep -rl "keyword" papers/*/paper.md papers/*/plan.json
   ```
2. **Read the paper(s)** — Understand what figures, tables, and results exist.
3. **Copy figures** — Copy relevant PNGs to the chapter's `figures/` directory:
   ```bash
   cp papers/<paper>/figures/relevant_fig.png books/<book>/chapters/chNN/figures/
   ```
4. **Adapt tables** — Copy markdown tables from paper.md into chapter.md.
   Expand captions and add book-level context (the book reader needs more
   background than a journal reader).
5. **Weave results into narrative** — Don't just paste; integrate the paper's
   findings into the book's flow with additional explanation and context.
6. **Credit the paper** — If the paper is published or under review, cite it.
   If it's internal PaperLab work, note it as "from our detailed study" with
   a `\cite{}` to the paper's own refs where applicable.

**Paper-to-chapter mapping (update per book):**

When starting a book project, create a mapping table in the book's notes or
preface showing which papers feed into which chapters. Example:

| Chapter | Relevant PaperLab paper(s) | Assets to import |
|---|---|---|
| Ch 8: Numerical Implementation | `implicit_cpa_performance_2026` | 6 figs, speedup tables |
| Ch 8: Numerical Implementation | `accelerated_cpa_solvers_2026` | convergence tables, barchart |
| Ch 4: Association Theory | `site_symmetry_reduction_wertheim_2026` | symmetry tables |
| Ch 10: Gas Processing | `teg_cpa_solvers_2026_2026` | TEG benchmark tables |
| Ch 12: Advanced Topics | `electrolyte_cpa_advanced_2026` | parity plots, salt tables |

### Literature Quality Rules

- **ALWAYS cite the original source**, not a secondary reference or review
- **DO NOT fabricate references** — if unsure, mark as `[VERIFY]` and confirm later
- **Include both classic AND recent** (last 5 years) references
- **Every equation** should cite its origin (who derived it first)
- **Every dataset** should cite its source (NIST, DIPPR, original experiment)
- **Every method** should cite the paper that introduced it
- **Every historical claim** should cite a primary source ("van der Waals proposed..." → cite van der Waals 1873)

---

## Core Writing Principle

**Papers are about algorithms, methods, and scientific contributions — not about NeqSim as a software product.**

NeqSim is the implementation vehicle, not the subject. The reader should learn about
thermodynamic algorithms, numerical methods, and chemical engineering science. They
should be able reproduce the work in any language or framework.

---

## What Goes WHERE

### Main Body (Sections 1–6): Algorithm & Science ONLY

- **Title**: Name the algorithm or method, not the software.
  - GOOD: "A Consistent Jacobian Formulation for Constrained Gibbs Energy Minimization in Multiphase Chemical Equilibrium"
  - BAD: "Chemical Equilibrium Solver in NeqSim: Implementation and Benchmarking"

- **Abstract**: Describe the algorithmic contribution and results. Mention the
  implementation platform once at most, as "an open-source thermodynamic library."

- **Introduction**: Frame as a scientific/algorithmic problem. Prior work section
  cites algorithms and methods (Michelsen, Gordon-McBride, Smith-Missen, etc.),
  not software packages.

- **Mathematical Framework**: Pure math — equations, derivations, proofs.
  No software API references.

- **Algorithm Description**: Pseudocode, convergence analysis, complexity.
  Write as if the reader will implement it themselves.
  Say "the algorithm" or "the solver", not "NeqSim's solver."

- **Benchmark Design**: Describe the test matrix, metrics, and statistical methods.
  The computational environment can mention the implementation language (Java),
  hardware, and OS — but frame it as reproducibility information, not advertising.

- **Results & Discussion**: Present data and interpret it scientifically.
  Discuss convergence behavior, scaling, failure modes, and algorithmic implications.

### Acknowledgements: Credit NeqSim

- "The algorithm is implemented in the open-source NeqSim library (https://github.com/equinor/neqsim),
  developed at NTNU and Equinor."

### Data Availability: Link to NeqSim

- "Source code, benchmark configurations, raw results, and figure-generation scripts
  are available at https://github.com/equinor/neqsim under the MIT license."

---

## Language Patterns

| AVOID | USE INSTEAD |
|-------|-------------|
| "NeqSim's TPflash algorithm" | "The hybrid SS–NR flash algorithm" |
| "NeqSim implements..." | "The algorithm implements..." |
| "Using NeqSim, we computed..." | "Using the SRK equation of state, we computed..." |
| "NeqSim's component database" | "The thermodynamic property database" |
| "In NeqSim, K-factors are..." | "K-factors are initialized from..." |
| "NeqSim version X.Y.Z" | "The implementation (open-source, see Data Availability)" |
| "NeqSim uses EJML for..." | "The linear system is solved via LU decomposition" |
| "the NeqSim library" | "the implementation" or "the open-source solver" |

---

## Section-by-Section Checklist

### Title
- [ ] Names the algorithm/method, not the software
- [ ] Contains the scientific contribution (e.g., "Consistent Jacobian", "Adaptive Step Sizing")

### Abstract (check journal profile for word limit; FPE: 250, CACE: 250)
- [ ] States the problem in algorithm terms
- [ ] Describes the method contribution
- [ ] Reports quantitative results (convergence %, timing, accuracy)
- [ ] Mentions "open-source implementation" at most once
- [ ] Zero occurrences of "NeqSim" in abstract

### Introduction
- [ ] Frames as a scientific/algorithmic problem
- [ ] Prior work cites algorithms and researchers, not software products
- [ ] Contributions list describes algorithmic novelty
- [ ] May mention "open-source implementation" once in contributions list

### Mathematical Framework
- [ ] Pure mathematics — no software references
- [ ] Equations derivable from first principles
- [ ] Notation defined at first use

### Algorithm Description
- [ ] Written as pseudocode + prose, language-agnostic
- [ ] Uses "the algorithm" or "the solver", not brand names
- [ ] Implementation details (library names, matrix packages) in a short
      "Implementation notes" subsection, not woven into algorithm description
- [ ] Convergence analysis is mathematical, not empirical

### Benchmark/Results
- [ ] Computational environment described factually for reproducibility
- [ ] Results discussed in terms of algorithmic behavior, not software features
- [ ] Comparisons are between algorithms/methods, not software packages

### Conclusions
- [ ] Summarizes algorithmic findings
- [ ] Future work proposes scientific extensions
- [ ] No software roadmap items

### Acknowledgements
- [ ] Credits the implementation platform with URL
- [ ] Credits funding, institutions, contributors

### Data Availability
- [ ] Links to repository with code, data, and scripts
- [ ] States the license

---

## Figure and Table Format Requirements

Figures and tables are automatically validated by `paperflow.py validate-figures`
and during `paperflow.py format`. The checks use the target journal's profile
(in `journals/*.yaml`) for format-specific requirements.

### Figure Checklist

- [ ] **Format**: All figure files use journal-allowed formats (typically: `pdf`, `eps`, `tif`, `png`)
- [ ] **DPI**: Raster images (PNG, TIFF, JPG) meet the minimum DPI requirement (typically 300 DPI; line art 1000 DPI)
- [ ] **Readability**: Images can be opened without errors (not corrupted or truncated)
- [ ] **Dimensions**: Figures are large enough for print (at least 200×150 px; aim for ≥6 inches wide at target DPI)
- [ ] **Numbering**: Figures are numbered sequentially (fig1, fig2, ...) with no gaps
- [ ] **Captions**: Every figure has a caption in `paper.md` using `*Figure N:*` or `**Fig. N.**` pattern
- [ ] **In-text references**: Every figure file is referenced in the manuscript text (Figure N or Fig. N)
- [ ] **No orphan references**: Every `Fig. N` in text corresponds to an actual figure definition
- [ ] **Vector preferred**: Line plots and diagrams should be PDF or EPS (vector) for best quality
- [ ] **No AI-generated images**: Unless part of the research methodology (per journal policy)

### Table Checklist

- [ ] **Captions**: Every table has a bold caption (`**Table N:**` or `**Table N.**`)
- [ ] **Column consistency**: All rows in each table have the same number of columns
- [ ] **No vertical rules**: Use three-line style (header rule, separator, bottom rule); no `||` double pipes
- [ ] **Numbering**: Tables are numbered sequentially with no gaps
- [ ] **In-text references**: Every table is referenced in manuscript text (`Table N`)
- [ ] **Units in headers**: Physical quantities include units in column headers (e.g., `T (K)`, `P (bar)`)
- [ ] **Significant figures**: Results should use appropriate significant figures (not excessive precision)

### Highlights Checklist (if required by journal)

- [ ] **Count**: Between minimum and maximum (typically 3–5)
- [ ] **Character limit**: Each highlight within the journal's max (typically 85 characters including spaces)
- [ ] **Content**: Each captures a novel result or method — not generic statements

### Running the Validation

```bash
# Standalone figure/table validation
python paperflow.py validate-figures papers/my_paper/ --journal fluid_phase_equilibria

# Full compliance check (includes figure/table validation)
python paperflow.py format papers/my_paper/ --journal fluid_phase_equilibria
```

Validation results show `[OK]`, `[!!]` (fail), or `[??]` (warning) for each check.
Fix all `[!!]` items before submission. Review `[??]` warnings and address if possible.

---

## Prose Quality Checks

Before submission, analyze your manuscript's writing quality:

```bash
python paperflow.py check-prose papers/my_paper/
```

This scores the manuscript on four dimensions (0-100 each):

| Dimension | What it checks | Target |
|-----------|---------------|--------|
| **Readability** | Flesch-Kincaid grade level | ≤14 (journal-level) |
| **Sentence structure** | Sentences over 35 words | <10% long sentences |
| **Active voice** | Passive constructions detected | <25% passive |
| **Conciseness** | Hedging words, wordy phrases | Minimal hedging |

Common issues flagged:

- **Passive voice**: "was measured by" → "we measured" / "the sensor measured"
- **Hedging**: "somewhat", "perhaps", "seems to" — be assertive with evidence
- **Wordy phrases**: "in order to" → "to"; "due to the fact that" → "because"
- **Long sentences**: Split sentences over 35 words into two

---

## Bibliography Validation

Validate `refs.bib` completeness and consistency with the manuscript:

```bash
python paperflow.py validate-bib papers/my_paper/
```

Checks include:
- Required fields present for each entry type (title, author, year, journal)
- No duplicate BibTeX keys
- All `\cite{key}` references in `paper.md` have matching entries in `refs.bib`
- All `refs.bib` entries are cited in the manuscript (no orphan references)

---

## Citation Discovery

Find highly-cited papers you may have missed:

```bash
python paperflow.py suggest-refs papers/my_paper/ --max 10
```

This extracts search terms from your `plan.json` (title, research questions) and
`paper.md` (abstract, keywords), queries Semantic Scholar for papers with 10+
citations, and excludes papers already in `refs.bib`. Copy-paste BibTeX entries
are provided for each suggestion.

---

## Revision Diff Report

After revising, generate a visual comparison between manuscript versions:

```bash
python paperflow.py diff papers/my_paper/
```

Produces a color-coded HTML report showing line-level additions, deletions,
section changes, and word count statistics. Useful for:
- Reviewer response letters: showing exactly what changed
- Self-review: verifying revision completeness
- Co-author review: quick visual summary of edits

---

## Paper Types — Choose the Right Structure

Not every paper proposes a new algorithm. Choose the paper type that matches
your contribution, then follow the corresponding structure.

### Type 1: Algorithm Improvement Paper (Comparative)

**Contribution:** A new or improved method, compared against a baseline.

**Structure:** Baseline → Modification → Benchmark (A vs B) → Statistical validation

**Claims pipeline:** Use the full `approved_claims.json` workflow with Wilcoxon
signed-rank tests and effect sizes. Every improvement claim needs p < 0.05.

**Example:** "Adaptive SS-NR switching reduces flash iterations by 15%"

### Type 2: Characterization / Baseline Paper

**Contribution:** First systematic benchmark of an existing method across diverse conditions.

**Structure:** Algorithm description → Benchmark design → Results characterization → Discussion of behavior regimes

**Claims pipeline:** Lighter weight — claims are observational, not comparative.
Use `results.json` directly. No statistical comparison needed (there's nothing
to compare against). Focus on coverage, failure analysis, and scaling behavior.

**Example:** "Systematic characterization of a hybrid SS-DEM-NR flash algorithm
across 1664 natural gas flash cases" (the TPflash paper we completed).

**Key differences from Type 1:**
- No candidate algorithm — single algorithm, comprehensive evaluation
- Two-phase fraction, timing distributions, and scaling are the main results
- Cross-validation (e.g., SRK vs PR) strengthens the paper
- Failure analysis and regime identification are critical sections

### Type 3: Method Paper (New Formulation)

**Contribution:** A novel mathematical formulation or solver approach.

**Structure:** Mathematical derivation → Proof of properties → Implementation → Validation against reference solutions

**Claims pipeline:** Mathematical claims (convergence order, stability) need proofs.
Computational claims use the full benchmark pipeline.

**Example:** "A consistent Jacobian formulation for constrained Gibbs energy
minimization in multiphase chemical equilibrium"

### Type 4: Application / Case Study Paper

**Contribution:** Novel application of existing methods to a new domain or problem.

**Structure:** Problem description → Method selection/adaptation → Results → Engineering implications

**Claims pipeline:** Domain-specific validation against experimental or literature data.
Benchmark against published values, not internal baselines.

**Example:** "Sulfur deposition prediction in sour gas pipelines using Gibbs
energy minimization with solid-phase flash"

### Choosing Your Type

| If your main contribution is... | Use Type | Key validation |
|---|---|---|
| A faster/more robust algorithm | 1 (Comparative) | A-vs-B benchmark with stats |
| First comprehensive evaluation | 2 (Characterization) | Coverage, scaling, regimes |
| New math/formulation | 3 (Method) | Proofs + reference solutions |
| Engineering insight from simulation | 4 (Application) | Literature/experimental data |

---

## Lessons Learned — TPflash Paper (2026)

These lessons were captured from the first paper produced with the PaperLab
system. Apply them to all future papers.

### 1. Start with a benchmark script, not modular stages

The single `run_benchmark.py` script that generates cases, runs flashes,
computes statistics, and produces figures was far more effective than the
planned modular generate→run→analyze pipeline. Write ONE comprehensive script
per paper that produces `results.json` + all figures.

---

## Generating Submission Documents

### Command

```bash
python paperflow.py format papers/<slug>/ --journal <journal_name>
```

This produces **both** `submission/paper.tex` (LaTeX) and `submission/paper.docx`
(Word) in a single invocation. A typeset **PDF** is also generated when the
`typst` Python package and `pandoc` are available.

### PDF Document Quality

The PDF renderer (`tools/render_pdf.py`) produces publication-quality PDFs with
properly typeset math equations, numbered automatically by typst.  The pipeline:

```
paper.md  →  strip \tag{} + HTML comments
          →  pandoc --to typst
          →  add academic preamble (A4, New Computer Modern 11pt, equation numbering)
          →  fix table column widths (auto-sizing instead of pandoc's percentages)
          →  typst.compile()  →  submission/paper.pdf
```

Key features:
- **Math equations** rendered natively by typst (fractions, subscripts, Greek, etc.)
- **Auto-numbered equations** via `math.equation(numbering: "(1)")`
- **Tables** use auto column widths so math-heavy cells are not squeezed
- **ASCII-art figures** in code blocks are preserved with monospace styling
- **Title and author** extracted from `plan.json` or `paper.md` automatically

The PDF can also be generated standalone:

```bash
python tools/render_pdf.py papers/<slug>/
```

### Word Document Quality

The Word renderer (`tools/word_renderer.py`) produces publication-quality documents
with **native OMML equations** — the same editable format as typing in Word's
equation editor. The pipeline:

```
LaTeX → latex2mathml → MathML XML → XSLT (MML2OMML.XSL) → OMML → Word
```

Tables use booktabs style (three-line borders, blue header, no vertical rules).
Citations from `refs.bib` resolve automatically to `[N]` references.

If the OMML pipeline is unavailable (missing Office XSL or packages), equations
fall back to Unicode text rendering (Greek letters, operators).

### Required Python Packages

```
pip install python-docx latex2mathml lxml pyyaml typst
```

Also ensure **pandoc ≥ 3.0** is installed and on PATH (https://pandoc.org).
Pandoc is needed for both LaTeX and PDF rendering.

### 2. Keep the claims pipeline proportional to the paper type

For characterization papers (Type 2), the full `approved_claims.json` →
`claims_manifest.json` pipeline is overkill. Use `results.json` directly.
Reserve the formal claims pipeline for comparative papers (Type 1) where
statistical significance is the gating criterion.

### 3. Plan pivots are normal — update plan.json

The TPflash paper was planned as a comparative study (baseline vs candidate)
but evolved into a characterization paper. This is fine, but the plan.json
should be updated to reflect the actual paper direction. Add a `"pivots"`
field to plan.json documenting scope changes.

### 4. Cross-validation is a standard quality measure

Always include at least one cross-validation axis:
- **EOS cross-validation**: SRK vs PR (or CPA, GERG-2008)
- **Solver cross-validation**: Different internal parameters, tolerances
- **Literature cross-validation**: Compare against published benchmark values

### 5. Include failure analysis even when convergence is 100%

The TPflash paper had 100% convergence — but the Discussion still analyzed
near-critical behavior, timing outliers, and regime differences. Always
discuss WHERE the algorithm works well and WHERE it is stressed.

### 6. Figures drive the narrative

The 6 figures in the TPflash paper (convergence by family, TP maps, timing
distribution, beta distribution, scaling, near-critical) each corresponded
to a subsection of Results. Design figures FIRST, then write prose around them.

### 7. Computational environment section matters for reproducibility

Always include: hardware, OS, Java version, JVM vendor, Python version,
random seed, number of timing repeats, and the NeqSim git commit hash.

---

## Applying to Existing Papers

When revising a draft, do a find-replace audit:
1. Search for "NeqSim" — each occurrence must be justified or relocated
2. Search for class/method names (e.g., `SystemSrkEos`, `TPflash`) — replace with algorithm descriptions
3. Search for "implementation" — ensure it's used generically, not as a brand reference

---

## Topic-Specific Guidance

### Flash Algorithm Papers

Refer to the existing skills:
- `design_flash_benchmark/SKILL.md` — Fluid families, PT grids, stress cases
- `run_flash_experiments/SKILL.md` — Execution patterns, timing, JSONL output
- `analyze_convergence/SKILL.md` — Figure catalog, statistical analysis

### Chemical Equilibrium / Reactor Papers

Refer to:
- `design_reactor_benchmark/SKILL.md` — Reaction systems, validation against JANAF/NASA
- `analyze_gibbs_convergence/SKILL.md` — Jacobian conditioning, element balance verification

Key considerations for reactor papers:
- Validate against JANAF/NASA-CEA thermochemical data
- Report element balance closure (should be < 1e-10 relative)
- Analyze conditioning of the Jacobian matrix (log10 condition number)
- Include adiabatic vs isothermal comparison

---

## Lessons Learned — Implicit CPA Paper (2026)

These lessons were captured from the second paper produced with PaperLab
(fully implicit CPA EOS, targeting Fluid Phase Equilibria). Apply them to
all future papers.

### 8. Reference ordering: paper.md is the source of truth

When `paper.md` contains manually numbered references (`[1]`, `[2]`, ...),
those MUST be rendered in the Word document in that exact order. The
`word_renderer.py` now detects pre-numbered refs and uses them directly,
bypassing alphabetical `refs.bib` sorting. Always use numbered references
in `paper.md` for control over citation order.

**Rule:** If you cite `[10]` in the text, ensure reference `[10]` in the
References section is the correct one. Cross-check after every major edit.

### 9. Image regex must handle brackets in alt text

Alt text like `![Fig. 1 comparison [10]](figures/fig1.png)` contains a `]`
character. The word renderer's regex must use `.*?` (lazy match) not `[^\]]*`
(character class that breaks on `]`). This was a silent bug that dropped
figures from the Word output.

### 10. Figure captions: avoid double-numbering

If `paper.md` already uses `**Fig. 1.** Caption text`, the word renderer
must strip that prefix before adding its own `Figure 1.` prefix. The
renderer now handles this automatically.

**Rule in paper.md:** Use plain caption text after the image tag. Let the
renderer handle numbering. Or use `**Fig. N.**` consistently and trust the
deduplication.

### 11. BibTeX special characters need Unicode conversion

BibTeX files contain LaTeX markup (`{\o}`, `{\"u}`, `{CPA}`). The
`clean_bibtex_latex()` function converts these to Unicode for Word rendering.
Always verify author names with accented characters render correctly.

### 12. Figure quality: serif fonts, compact sizes, log scales

Publication figures must use:
- **Serif fonts** (Times New Roman) — not the matplotlib default sans-serif
- **Compact sizes**: 3.5×2.8 in (single column), 7.0×3.5 in (double column)
- **Log scale** when data range exceeds 10×
- **Short system IDs** (A1, B1, C1) instead of full system names as labels
- **Manual annotation offsets** for scatter plots with clustered points

See the `generate_publication_figures` skill for copy-paste templates.

### 13. Post-render validation catches silent failures

The word renderer now includes `validate_word_output()` that checks:
- Figure count (paper.md vs docx embedded images)
- Reference count consistency
- Duplicate figure captions
- Display equation presence

Always check the validation output after rendering.

### 14. Journal profile accuracy matters

The FPE journal profile had wrong limits (abstract 200 → 250/words,
keywords 3–6 → 1–7). Always validate journal profiles against the actual
"Guide for Authors" page before submission. Keep journal YAML profiles
updated when guidelines change.

### 15. Compliance checker section matching must be flexible

The required section check uses keyword matching (`method*`, `result*`).
Papers may use alternative headings like "Algorithm Description" instead
of "Methods". The checker should match a broader set of synonyms.

### 16. Design figures FIRST, write around them

From both papers: figures drove the Results narrative. Plan all figures
before writing prose. Each figure should map to a Results subsection.
The `02_generate_figures.py` script should be the first deliverable
after `01_run_benchmark.py`.

### 17. Word audit scripts are essential

Python scripts that inspect the final `.docx` (count images, check reference
ordering, verify OMML equations) caught bugs that visual inspection missed.
The `validate_word_output()` function automates this.
- Test with trace species (< 1e-8 mole fraction) to stress the solver
- Report Lagrange multiplier convergence alongside composition

### PVT / Property Papers

Key considerations:
- Validate against NIST WebBook or REFPROP reference data
- Report deviations as AAD% (average absolute deviation)
- Include temperature and pressure sensitivity analysis
- Test near phase boundaries and critical point
- Compare multiple EOS models

### Mechanical Design / Standards Papers

Key considerations:
- Reference specific standard clauses (e.g., ASME VIII Div.1 UG-27)
- Compare with analytical solutions or published design examples
- Include safety factor sensitivity analysis
- Report cost estimation uncertainties

---

## Extended Toolchain

PaperLab includes additional tools for publication quality and compliance.
All tools are accessible via `paperflow.py` subcommands.

### Statistical Tests on Benchmark Results

```bash
python paperflow.py stats papers/my_paper/
```

Runs bootstrap confidence intervals, Cohen's d effect sizes, Wilcoxon signed-rank
tests, and Mann-Whitney U tests on benchmark `summary_*.json` files. Produces
publication-ready LaTeX and Markdown tables.

### Self-Plagiarism Check

```bash
python paperflow.py check-plagiarism papers/my_paper/ --doc-threshold 0.35
```

Uses TF-IDF cosine similarity to compare the manuscript against all other
`paper.md` files in the `papers/` directory. Detects document-level and
paragraph-level overlap. Requires `scikit-learn`.

### Reproducibility Manifest

```bash
python paperflow.py manifest papers/my_paper/
python paperflow.py verify-manifest papers/my_paper/
```

Generates SHA-256 hashes of all paper artifacts (manuscript, bibliography,
results, figures, source code) and captures the computational environment
(Python, Java, NeqSim version, OS). Use `verify-manifest` before submission
to ensure no artifacts have been modified since the last build.

### Graphical Abstract

```bash
python paperflow.py graphical-abstract papers/my_paper/
```

Generates a composite image (1600x900, 300 DPI) with the best figure from
`figures/` on the left and title + key highlights on the right. Requires `Pillow`.

### CRediT Author Statement

```bash
python paperflow.py credit papers/my_paper/
```

Generates a CRediT (Contributor Roles Taxonomy) author contribution statement
per NISO Z39.104-2022 with all 14 standard roles. Reads contributor assignments
from `plan.json` under `credit_contributions`. Validates role names and reports
uncovered roles.

### Nomenclature Extraction

```bash
python paperflow.py nomenclature papers/my_paper/
```

Scans `paper.md` for LaTeX math symbols (`$...$` and `$$...$$`), matches them
against a built-in database of thermodynamic/process engineering symbols, and
produces a sorted nomenclature table (Roman letters, Greek letters, subscripted
symbols) in both Markdown and LaTeX.

### Related Work Comparison Table

```bash
python paperflow.py related-work papers/my_paper/
```

Builds a structured comparison table from `literature_map.md` and `plan.json`
entries. Configurable dimensions (Method, EOS, Systems, Key Result, Limitation).
Outputs Markdown and LaTeX tables, optionally including a "This work" row.

### LaTeX/PDF Compilation

```bash
python paperflow.py latex papers/my_paper/ --journal elsevier --output-format both
```

Compiles `paper.md` to LaTeX/PDF via Pandoc with journal-specific class templates
(elsarticle, svjour3, achemso, MDPI, generic). Requires `pandoc` on PATH and
optionally a LaTeX distribution (TeX Live or MiKTeX) for PDF output.

Supported templates: `elsevier`, `springer`, `mdpi`, `acs`, `generic`.

### DOI Verification

```bash
python paperflow.py verify-dois papers/my_paper/
```

Sends HTTP HEAD requests to `https://doi.org/{doi}` for every DOI in `refs.bib`
and reports broken, timeout, or unreachable DOIs. Catches typos in DOI strings
before submission.

### Enhanced Citation Discovery (Crossref)

```bash
python paperflow.py suggest-refs papers/my_paper/ --max 15
```

Now queries **both** Semantic Scholar and Crossref APIs, deduplicates results,
and merges citation counts from both sources for broader coverage. Crossref
provides DOI-linked metadata and "is-referenced-by" counts.

### Enhanced Prose Quality

```bash
python paperflow.py check-prose papers/my_paper/
```

Now includes **proselint** integration (style and clarity lint), **LanguageTool**
grammar checking (with false-positive filtering for technical terms), NeqSim
name-in-abstract enforcement, and body overuse warnings. Scores across 7
dimensions: readability, sentence structure, active voice, conciseness,
proselint style, grammar, and algorithm-first compliance.

### Complete Tool Summary

| Command | Purpose | Optional Deps |
|---------|---------|---------------|
| `stats` | Statistical tests on benchmarks | scipy |
| `check-plagiarism` | Self-plagiarism detection | scikit-learn |
| `manifest` | Reproducibility manifest | — |
| `verify-manifest` | Verify manifest integrity | — |
| `graphical-abstract` | Composite graphical abstract | Pillow |
| `credit` | CRediT author statement | — |
| `nomenclature` | Symbol nomenclature table | — |
| `related-work` | Literature comparison table | — |
| `latex` | LaTeX/PDF via Pandoc | pandoc, TeX distribution |
| `verify-dois` | DOI resolution check | requests |
| `suggest-refs` | Citation discovery (S2 + Crossref) | requests |
| `check-prose` | Prose quality (7 dimensions) | proselint, language-tool-python |
