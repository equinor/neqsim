# NeqSim PaperLab

**PaperLab drives NeqSim development through scientific publication.** Every paper
produced here directly improves NeqSim's codebase — new algorithms are implemented,
existing models are validated and refined, test coverage is expanded, and
documentation is hardened. The publication process is the mechanism that turns
research ideas into production-quality code.

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  Identify gap   │ --> │  Implement in   │ --> │  Validate via    │
│  in NeqSim      │     │  NeqSim Java    │     │  benchmarks &    │
│  (scan command) │     │  codebase       │     │  paper writing   │
└─────────────────┘     └─────────────────┘     └──────────────────┘
       ^                                               │
       │         Paper accepted = code merged           │
       └───────────────────────────────────────────────┘
```

### Why This Matters

| Traditional approach | PaperLab approach |
|---------------------|-------------------|
| Write code, then write paper describing it | Paper requirements **drive** what code gets written |
| Code quality varies | Peer review forces rigorous validation |
| Tests are afterthoughts | Benchmarks **are** the paper's results |
| Documentation lags behind | Paper **is** the documentation |

Every benchmark run in PaperLab exercises the NeqSim Java code. Every validation
against literature data catches bugs. Every reviewer comment improves both the
paper and the underlying implementation. The `scan` command finds where NeqSim
has novel capabilities that deserve publication — and where the codebase needs
improvement to become publishable.

## Setup

```bash
cd neqsim-paperlab
pip install -r requirements.txt
```

## The Core Loop: Code → Paper → Better Code

PaperLab is designed for **iterative refinement** where editing the paper and
improving the NeqSim codebase happen together. The `iterate` command is the
heartbeat:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│ Edit paper   │ --> │  iterate     │ --> │ Fix issues   │
│ AND improve  │     │  (quality    │     │ in paper AND │
│  NeqSim code │     │   checks)    │     │  NeqSim code │
└─────────────┘     └──────────────┘     └─────────────┘
       ^                                        │
       └────────────────────────────────────────┘
                   Repeat until 100%
```

After editing Java code, recompile with `mvnw.cmd compile` and the next
Python/benchmark run picks up changes automatically — no reinstall needed.

```bash
# Check manuscript quality — run after every editing session
python paperflow.py iterate papers/my_paper/ --check all

# Available check categories:
#   all         — everything below
#   structure   — section presence (Abstract, Intro, Conclusions, etc.)
#   completeness — TODO placeholders, highlights count/length
#   evidence    — results traceability, figure references, claim linking
#   writing     — abstract word count, software-centric language
```

**See [WALKTHROUGH.md](WALKTHROUGH.md) for a complete end-to-end example.**

## Architecture

```
                          ┌──────────────────────────────────────────────────────┐
                          │              NeqSim Java Codebase                    │
                          │  (thermo, process, PVT, standards, mech. design)    │
                          └──────────┬───────────────────────┬──────────────────┘
                                     │ scan                  ↑ code improvements
                                     ↓                       │
Scout  →  Planner  →  Literature  →  Algorithm  →  Benchmark  →  Validation  →  Writer  →  Formatter
  ↑          ↑                          ↑             ↑                             ↑
scan      paper_type               NeqSim Java     cross-val                   claims OR
command   routing                  (via tools)     (EOS/ref)                   results.json
```

**Key principles:**
- Paper-writing agents never invent results. Every quantitative claim traces to
  a NeqSim tool output, a stored benchmark result, or a cited reference.
- Every paper improves NeqSim. The Algorithm Engineer agent proposes Java code
  changes; benchmarks exercise the codebase; validation catches bugs; the
  finished paper becomes documentation for the improved code.

### How Papers Drive NeqSim Development

| Paper Stage | NeqSim Improvement |
|-------------|-------------------|
| **Scan** (discover opportunities) | Identifies untested code, missing validations, novel but undocumented features |
| **Plan** (design study) | Defines what NeqSim needs to compute — gaps become implementation tickets |
| **Algorithm** (propose improvements) | New Java classes, solver improvements, bug fixes written into `src/main/java/` |
| **Benchmark** (run experiments) | Exercises NeqSim at scale — catches edge cases and performance issues |
| **Validation** (verify claims) | Compares NeqSim output to literature/experiments — calibrates model accuracy |
| **Paper accepted** | Validated code is merged; the paper serves as permanent documentation |

### Paper Types

The framework supports four paper types, each with adapted stage routing:

| Type | Description | Example | Skipped Stages |
|------|-------------|---------|----------------|
| **comparative** | A-vs-B algorithm comparison | Flash: baseline vs improved NR | None |
| **characterization** | Systematic evaluation of existing algorithm | Flash convergence map across 1600+ cases | Implementation, candidate benchmark |
| **method** | New formulation or mathematical framework | RT-corrected Gibbs Jacobian derivation | Candidate benchmark |
| **application** | Engineering case study | TEG dehydration design optimization | Algorithm analysis, implementation |

## Quick Start

```bash
# 0. Discover paper opportunities — find what's worth publishing
python paperflow.py scan                    # scan NeqSim for opportunities
python paperflow.py scan --literature -v    # with Semantic Scholar + details

# 1. Create a new paper project (specify paper_type!)
python paperflow.py new "Reactive Gibbs Convergence" \
    --journal fluid_phase_equilibria \
    --topic gibbs_reactor

# 2. Edit plan.json — fill in research questions, benchmark design, paper_type

# 3. Run the benchmark suite
python paperflow.py benchmark papers/gibbs_reactor_2026/

# 4. Generate figures (auto-runs generate_figures.py if present)
python paperflow.py figures papers/gibbs_reactor_2026/

# 5. Generate a manuscript draft from plan + results
python paperflow.py draft papers/gibbs_reactor_2026/

# 6. Iterative refinement — run after every edit
python paperflow.py iterate papers/gibbs_reactor_2026/

# 7. Audit reproducibility and claim tracing
python paperflow.py audit papers/gibbs_reactor_2026/

# 8. Format for target journal
python paperflow.py format papers/gibbs_reactor_2026/ --journal fluid_phase_equilibria

# 9. Handle reviewer comments (after submission)
python paperflow.py revise papers/gibbs_reactor_2026/ --comments reviewer_comments.md

# 10. Validate figures and bibliography
python paperflow.py validate-figures papers/gibbs_reactor_2026/ --journal fluid_phase_equilibria
python paperflow.py validate-bib papers/gibbs_reactor_2026/

# 11. Check prose quality (readability, passive voice, hedging)
python paperflow.py check-prose papers/gibbs_reactor_2026/

# 12. Discover missing references via Semantic Scholar
python paperflow.py suggest-refs papers/gibbs_reactor_2026/

# 13. Render all submission formats at once
python paperflow.py render papers/gibbs_reactor_2026/

# 14. After revision: generate visual diff report
python paperflow.py diff papers/gibbs_reactor_2026/

# 15. Check project status at any time
python paperflow.py status papers/gibbs_reactor_2026/
```

## Book Production

PaperLab also supports **multi-chapter scientific books** (textbooks, monographs,
edited volumes). Books follow publisher-specific formatting via YAML profiles
and reuse the same quality infrastructure as papers.

```bash
# Create a new book project (Springer, Wiley, CRC, or self-published)
python paperflow.py book-new "Thermodynamic Modeling with NeqSim" --publisher springer --chapters 12

# Add a chapter to an existing book
python paperflow.py book-add-chapter books/thermodynamic_modeling_2026/ --title "Cubic EOS" --part 2

# Check project status (word counts, TODOs, page estimates)
python paperflow.py book-status books/thermodynamic_modeling_2026/

# Preview table of contents
python paperflow.py book-toc books/thermodynamic_modeling_2026/

# Run quality checks (structure, completeness, consistency, cross-refs, bibliography)
python paperflow.py book-check books/thermodynamic_modeling_2026/

# Render to HTML (single-page with sidebar navigation)
python paperflow.py book-render books/thermodynamic_modeling_2026/ --format html

# Render to Word (.docx with TOC, section breaks, part separators)
python paperflow.py book-render books/thermodynamic_modeling_2026/ --format docx

# Render to PDF (via Typst, uses publisher page size/fonts)
python paperflow.py book-render books/thermodynamic_modeling_2026/ --format pdf

# Render a single chapter
python paperflow.py book-render books/thermodynamic_modeling_2026/ --format html --chapter ch03
```

**Publisher profiles** in `books/_publisher_profiles/` define page size, margins,
fonts, and page limits for Springer, Wiley, CRC Press, and self-publishing.

## Directory Structure

```
neqsim-paperlab/
├── README.md                     # This file
├── WALKTHROUGH.md                # End-to-end worked example (12 steps)
├── PAPER_WRITING_GUIDELINES.md   # Mandatory scientific writing rules
├── paperflow.py                  # CLI orchestrator (new, draft, iterate, revise, ...)
├── requirements.txt              # Python dependencies
├── journals/                     # Journal profile configs (YAML)
│   ├── fluid_phase_equilibria.yaml
│   ├── computers_chem_eng.yaml
│   ├── chem_eng_sci.yaml
│   ├── iecr.yaml
│   └── aiche.yaml
├── agents/                       # Agent definitions (VS Code Copilot)
│   ├── research_scout.agent.md
│   ├── planner.agent.md
│   ├── literature_reviewer.agent.md
│   ├── algorithm_engineer.agent.md
│   ├── benchmark.agent.md
│   ├── validation.agent.md
│   ├── scientific_writer.agent.md
│   ├── figure_generator.agent.md
│   ├── journal_formatter.agent.md
│   └── reviewer_response.agent.md
├── skills/                       # Reusable scientific procedures
│   ├── design_flash_benchmark/SKILL.md
│   ├── design_reactor_benchmark/SKILL.md
│   ├── run_flash_experiments/SKILL.md
│   ├── analyze_convergence/SKILL.md
│   ├── analyze_gibbs_convergence/SKILL.md
│   ├── generate_publication_figures/SKILL.md
│   ├── write_methods_section/SKILL.md
│   └── journal_formatting/SKILL.md
├── tools/                        # Python tooling
│   ├── __init__.py
│   ├── neqsim_bootstrap.py         # NeqSim JVM bootstrap (local build)
│   ├── neqsim_scientific_tools.py  # NeqSim/jpype wrappers
│   ├── flash_benchmark.py          # Flash benchmark runner
│   ├── benchmark_chunk_worker.py   # Parallel benchmark chunk processor
│   ├── figure_style.py             # SciencePlots journal presets & palettes
│   ├── figure_validator.py         # Figure DPI/format/size validation (Pillow)
│   ├── bib_validator.py            # Bibliography validation (bibtexparser)
│   ├── prose_quality.py            # Readability scoring, passive voice, hedging (textstat)
│   ├── citation_discovery.py       # Suggest missing refs via Semantic Scholar API
│   ├── revision_diff.py            # Visual HTML diff between manuscript revisions
│   ├── research_scanner.py         # Codebase paper opportunity scanner
│   ├── trending_topics.py          # Trending research topic scanner (Semantic Scholar)
│   ├── daily_scan.py               # CI script for automated daily scan + issue
│   ├── paper_renderer.py           # LaTeX rendering
│   ├── latex_pipeline.py           # LaTeX compilation pipeline
│   ├── word_renderer.py            # Word/OMML rendering
│   ├── claim_tracer.py             # Evidence audit (all paper types)
│   ├── render_html.py              # HTML render (legacy, tpflash-specific)
│   ├── render_html_generic.py      # HTML render (generic, any paper)
│   ├── render_pdf.py               # PDF render (Typst-based)
│   ├── render_all.py               # Multi-format render dispatcher
│   ├── statistical_tests.py        # Statistical tests for benchmark results
│   ├── nomenclature_extractor.py   # Extract nomenclature from manuscript
│   ├── related_work_table.py       # Generate related work comparison table
│   ├── credit_generator.py         # CRediT author contribution table
│   ├── graphical_abstract.py       # Graphical abstract generator
│   ├── reproducibility_manifest.py # Submission manifest with checksums
│   ├── self_plagiarism_checker.py  # Self-plagiarism check across papers
│   ├── book_builder.py             # Book scaffolding, assembly, TOC, status
│   ├── book_render_pdf.py          # Book PDF rendering (Typst)
│   ├── book_render_word.py         # Book Word rendering (python-docx)
│   ├── book_render_html.py         # Book HTML rendering (sidebar nav)
│   └── book_checker.py             # Book quality checks
├── tests/                        # Pytest test suite
│   └── test_paperflow.py
├── workflows/                    # Workflow definitions
│   ├── new_paper.yaml
│   └── revise_paper.yaml
├── templates/                    # Document templates
│   ├── paper_skeleton.md            # Default paper template
│   ├── paper_skeleton_comparative.md # Comparative study template
│   ├── paper_skeleton_data.md       # Data paper template
│   ├── paper_skeleton_spe.md        # SPE format template
│   ├── cover_letter.md
│   ├── response_to_reviewers.md
│   ├── supplementary_material.md    # Supplementary material template
│   ├── book_chapter.md             # Chapter template for books
│   ├── book_frontmatter/           # Title page, copyright, dedication, preface
│   └── book_backmatter/            # Glossary, author bio
├── books/                        # Book projects (one folder per book)
│   └── _publisher_profiles/        # Publisher YAML configs
│       ├── springer.yaml
│       ├── wiley.yaml
│       ├── crc.yaml
│       └── self.yaml
└── papers/                       # Paper projects (one folder per paper)
    └── tpflash_algorithms_2026/
        ├── plan.json               # Research plan + questions
        ├── paper.md                # Manuscript (markdown)
        ├── results.json            # Benchmark results + key findings
        ├── refs.bib                # BibTeX references
        ├── iteration_feedback.json # Latest quality assessment
        ├── figures/                # Generated plots (PNG)
        └── tables/                 # Generated data tables
```

## Agents

| Agent | Purpose | Input | Output |
|-------|---------|-------|--------|
| **Planner** | Turns idea → research plan | Topic, journal, angle | plan.json, outline |
| **Research Scout** | Discovers paper opportunities in codebase | NeqSim repo, git history | opportunities.json, scout_report.md |
| **Literature Reviewer** | Builds technical context | Topic, keywords | literature_map.md, gap_statement |
| **Algorithm Engineer** | Proposes code changes | NeqSim source, plan | Pseudocode, impl tickets |
| **Benchmark** | Runs experiment suites | Config, algorithm version | Results, metrics, failures |
| **Validation** | Decides if claims hold | Results from benchmark | approved_claims.json |
| **Scientific Writer** | Drafts manuscript | All artifacts | paper.md |
| **Journal Formatter** | Adapts to journal rules | paper.md, journal profile | paper.tex / paper.docx |
| **Reviewer Response** | Handles peer review | Reviewer comments | Response letter, revision plan |

## CLI Commands

| Command | Description | Key Flags |
|---------|-------------|-----------|
| `scan` | Discover paper opportunities in NeqSim codebase | `--since`, `--top`, `--literature`, `--verbose` |
| `new` | Create paper project from template | `--journal`, `--topic` |
| `benchmark` | Run NeqSim benchmark suite | |
| `figures` | Generate figures (auto-runs generate_figures.py) | |
| `draft` | Generate paper.md from plan + results | `--force` (overwrite existing) |
| `iterate` | Interactive quality check with scoring | `--check` (category: structure, completeness, evidence, writing) |
| `audit` | Trace claims to evidence artifacts | |
| `format` | Apply journal formatting rules | `--journal` |
| `render` | Render to all formats (HTML, LaTeX, Word) | `--journal` (optional) |
| `validate-figures` | Check figure DPI, format, size, color | `--journal` |
| `validate-bib` | Check refs.bib completeness and cross-refs | |
| `check-prose` | Readability scoring, passive voice, hedging | |
| `suggest-refs` | Suggest missing references (Semantic Scholar) | `--max` (max suggestions) |
| `diff` | Visual diff between manuscript revisions | `--revision`, `--old`, `--new` |
| `revise` | Create revision workspace from reviewer comments | `--comments` (path to comments file) |
| `status` | Show project completion status | |
| `list` | List all papers with status and metadata | |
| `stats` | Statistical tests for benchmark results | |
| `latex` | Render to LaTeX | `--journal` |
| `nomenclature` | Extract nomenclature from manuscript | |
| `related-work` | Generate related work section from refs.bib | |
| `credit` | Generate CRediT author contribution table | |
| `graphical-abstract` | Generate graphical abstract | |
| `manifest` | Create submission manifest (files + checksums) | |
| `verify-manifest` | Verify submission manifest integrity | |
| `check-plagiarism` | Self-plagiarism check across papers | |
| `verify-dois` | Verify DOIs in refs.bib resolve correctly | |
| **Book Commands** | | |
| `book-new` | Create a new book project | `--publisher`, `--chapters` |
| `book-add-chapter` | Add chapter to existing book | `--title`, `--part` |
| `book-render` | Render book to PDF, Word, or HTML | `--format`, `--chapter` |
| `book-check` | Run book quality checks | `--check` (structure, completeness, etc.) |
| `book-status` | Show book project overview | |
| `book-toc` | Preview table of contents | |

## Automated Daily Scan (CI/CD)

The research scanner runs automatically every day via GitHub Actions and opens
a GitHub Issue suggesting new paper opportunities when changes are detected.

**How it works:**

1. `.github/workflows/research-scan.yml` triggers daily at 06:00 UTC
2. `neqsim-paperlab/tools/daily_scan.py` runs the scanner on the full repo
3. If opportunities changed since the last scan, a GitHub Issue is created
   with the full `scout_report.md` as the issue body

**Manual trigger:**

```bash
# From GitHub: Actions → Daily Research Scan → Run workflow
# Or use the gh CLI:
gh workflow run research-scan.yml -f force=true -f since_days=90
```

**What the issue contains:**

- Ranked table of paper opportunities with scores and readiness
- Detailed cards per opportunity with NeqSim code improvements
- Domain and paper-type breakdowns

**Change detection:** A content hash tracks whether opportunities changed
between scans. Issues are only created when new or modified opportunities
appear (or when `force=true` is set).

## Skills

| Skill | When to Use |
|-------|-------------|
| `design_flash_benchmark` | Creating test matrices for flash algorithm comparisons |
| `design_reactor_benchmark` | Creating test matrices for Gibbs reactor / chemical equilibrium benchmarks |
| `run_flash_experiments` | Executing NeqSim flash calculations in batch |
| `analyze_convergence` | Interpreting convergence metrics, stability analysis |
| `analyze_gibbs_convergence` | Analyzing Gibbs reactor convergence, Jacobian conditioning, element balance |
| `generate_publication_figures` | Producing publication-ready matplotlib figures |
| `write_methods_section` | Drafting the Methods section with proper math notation |
| `journal_formatting` | Applying journal-specific formatting rules |

## Workflow Stages

### Stage 1: Plan
- Define research questions, hypothesis, novelty statement
- Design benchmark matrix
- Create manuscript outline

### Stage 2: Literature
- Map prior work
- Identify gap
- Build reference database

### Stage 3: Experiment
- Implement algorithm changes (on branch)
- Run benchmark suite
- Collect raw results

### Stage 4: Validate
- Statistical analysis of results (comparative papers)
- Coverage completeness analysis (characterization papers)
- Reference data comparison (all types)
- Identify regimes where method improves / fails
- Produce approved_claims.json or results.json

### Stage 5: Write
- Draft all sections from structured artifacts
- Every claim linked to result ID (comparative) or results.json key (characterization)
- Figures generated from benchmark data
- Cross-validation results integrated

### Stage 6: Format
- Apply journal template
- Check compliance
- Generate submission package

## Governance Rules

1. **No unsupported claims**: Every quantitative statement must link to a result artifact
   - *Comparative papers*: formal `[Claim Cx]` → `approved_claims.json` pipeline
   - *Other paper types*: results.json key tracing + figure reference validation
2. **Reproducibility**: Every figure/table must be regenerable from stored data
3. **Claim tracing**: `claim_tracer.py` audits all paper types with type-appropriate logic
4. **Version control**: Algorithm changes on named branches
5. **Validation gates**: Claims must pass validation before entering manuscript
6. **Iterative quality**: Use `paperflow.py iterate` after *every* editing session

## Integration with NeqSim

NeqSim is **always** loaded from the local build via `devtools/neqsim_dev_setup.py`.
This means any Java code change is picked up immediately after `mvnw compile` —
no pip install needed.

All tools go through `tools/neqsim_bootstrap.py`:
```python
from tools.neqsim_bootstrap import get_jneqsim
jneqsim = get_jneqsim()
fluid = jneqsim.thermo.system.SystemSrkEos(298.15, 50.0)
```

Communication paths:
- **Python wrappers** (`tools/neqsim_scientific_tools.py`) via bootstrap → jpype
- **MCP server** (`neqsim-mcp-server/`) for agent tool calls
- **Direct Java** via jpype for performance-critical benchmarks

## Running Tests

```bash
cd neqsim-paperlab
pip install -r requirements.txt
python -m pytest tests/ -v
```

## Adding a New Journal

Create a YAML profile in `journals/`:

```yaml
journal_name: "Your Journal Name"
publisher: "Publisher"
abstract_words_max: 200
section_order: [Abstract, Keywords, Introduction, Methods, Results, Discussion, Conclusions]
reference_style: "elsevier-numbered"
figure_format: "tif-or-pdf"
highlights_required: true
```

Then use: `python paperflow.py format papers/your_paper/ --journal your_journal`

---

## Lessons Learned (TPflash Paper, 2026)

These lessons from the first completed paper (`tpflash_algorithms_2026`) informed
the v2.0 updates to agents, skills, workflow, and templates.

### 1. Paper type matters more than you think

The TPflash paper was planned as a **comparative** study (baseline vs improved algorithm)
but evolved into a **characterization** study (systematic evaluation of the existing
hybrid SS-DEM-NR algorithm). The formal claims pipeline (`approved_claims.json`,
`claims_manifest.json`) was never used because it requires A-vs-B framing.

**Fix:** The framework now supports four paper types with adapted stage routing.
Choose the right type at planning time. If the paper pivots, update `plan.json`
with a documented pivot reason.

### 2. Single benchmark script beats modular pipeline

The planned generate → run → analyze pipeline was replaced by a single
`run_benchmark.py` that does everything: generates cases, runs NeqSim,
collects metrics, and saves results to JSONL + JSON.

**Fix:** The benchmark agent now recommends a unified script for characterization
papers, and the modular pipeline for comparative papers where baseline/candidate
separation matters.

### 3. Cross-validation is essential — plan for it

SRK vs Peng-Robinson cross-validation was added organically and significantly
strengthened the paper. It should be a default quality measure.

**Fix:** The workflow now includes an explicit `cross_validation` stage between
benchmark and validation. Recommended for all paper types.

### 4. Literature intermediates are optional for characterization

`literature_map.md`, `gap_statement.md`, and `related_work_table.csv` were never
produced for the TPflash paper — only `refs.bib` was needed. A characterization
paper cites prior work inline rather than building a formal gap argument.

**Fix:** These intermediates are now marked as optional for characterization and
application papers. `refs.bib` is always required.

### 5. Figures-first writing works better

The most effective sections were written around figures — convergence maps,
iteration distributions, property accuracy plots. Having the figure catalog
upfront made writing faster and more focused.

**Fix:** The scientific writer agent now follows a "Figures-First Strategy" with
paper-type-specific figure catalogs.

### 6. Don't over-engineer the claims pipeline

The original governance model (claim → statistical test → approval → manifest →
manuscript link) works for comparative papers where statistical significance
matters. For characterization papers, claims come directly from results.json
and are validated by completeness and consistency rather than p-values.

**Fix:** Two validation paths now coexist: formal claims pipeline (comparative)
and direct results validation (characterization/method/application).

### 7. PAPER_WRITING_GUIDELINES work well — reinforce them

Algorithm-first naming (not "NeqSim's flash"), proper mathematical notation,
and reproducibility requirements from the guidelines were consistently followed.
These should be the first thing any writing agent reads.

**Fix:** All agent updates now reference PAPER_WRITING_GUIDELINES.md as
mandatory reading, and the guidelines include topic-specific sections.
