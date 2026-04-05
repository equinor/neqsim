# PaperLab End-to-End Walkthrough

This guide walks through a complete paper production cycle using the `tpflash_algorithms_2026`
paper as a worked example. Every step is reproducible from a clean checkout.

## Prerequisites

NeqSim is loaded from the **local build** via `devtools/neqsim_dev_setup.py`.
You do **not** install neqsim via pip.

```bash
# 1. Build the Java project once (from the repo root)
mvnw.cmd package -DskipTests "-Dmaven.javadoc.skip=true"

# 2. Install Python dependencies (no neqsim — the local JAR is used)
cd neqsim-paperlab
pip install -r requirements.txt
```

After editing Java code, recompile with `mvnw.cmd compile` and the next
Python run will pick up the changes automatically.

## Step 1: Create a Paper Project

```bash
cd neqsim-paperlab

python paperflow.py new "Hybrid SS-NR Flash Characterization" \
    --journal fluid_phase_equilibria \
    --topic tpflash_characterization
```

This creates:
```
papers/tpflash_characterization_2026/
├── plan.json              # Skeleton research plan
├── benchmark_config.json  # Experiment matrix
├── paper.md               # Manuscript skeleton
├── refs.bib               # Starter references
├── approved_claims.json   # Empty claims (for comparative papers)
├── claims_manifest.json   # Empty manifest
├── algorithm/
├── figures/
├── tables/
├── results/raw/
└── submission/
```

## Step 2: Edit the Research Plan

Open `plan.json` and fill in:

1. **paper_type**: `"characterization"` (since we are benchmarking an existing algorithm)
2. **novelty_statement**: What is new about this work
3. **research_questions**: Specific, falsifiable questions with acceptance criteria
4. **benchmark_design**: Fluid families, P/T ranges, metrics

```json
{
  "paper_type": "characterization",
  "novelty_statement": "First systematic open-source benchmark of hybrid SS-DEM-NR flash across 6 natural gas families",
  "research_questions": [
    {
      "id": "RQ1",
      "question": "What is the convergence rate across diverse natural gas conditions?",
      "hypothesis": "The hybrid algorithm achieves >99% convergence",
      "method": "Run 1500+ flash cases across 6 fluid families",
      "acceptance_criterion": "Convergence rate >95% for all families"
    }
  ]
}
```

## Step 3: Run Benchmarks

```bash
python paperflow.py benchmark papers/tpflash_characterization_2026/
```

This executes the full benchmark matrix and produces:
- `results/raw/baseline_results.jsonl` — Raw per-case data
- `results/summary_baseline.json` — Aggregate statistics

## Step 4: Generate Figures

Create `papers/tpflash_characterization_2026/generate_figures.py` using the
`generate_publication_figures` skill as a template. Then:

```bash
python paperflow.py figures papers/tpflash_characterization_2026/
```

## Step 5: Generate the Draft

```bash
python paperflow.py draft papers/tpflash_characterization_2026/
```

This reads `plan.json` and `results.json` and produces a structured `paper.md`
with actual results data, tables, and figure references filled in.

## Step 6: Iterate on the Manuscript

This is the core refinement loop — run it after every editing session:

```bash
python paperflow.py iterate papers/tpflash_characterization_2026/
```

Output:
```
============================================================
ITERATION FEEDBACK REPORT
============================================================
Paper: tpflash_characterization_2026
Type:  characterization
Score: 8/15 (53%)

  [STRUCTURE]
    [OK] Abstract section present
    [OK] Keywords section present
    [!!] Missing Conclusions section

  [COMPLETENESS]
    [!!] 12 TODO placeholders still in manuscript

  [EVIDENCE]
    [OK] 5/7 key results referenced in text

  [WRITING]
    [OK] Abstract: 180 words (limit 250)
    [!!] NeqSim mentioned 6 times in body - use algorithm-first language

SUGGESTED NEXT ACTIONS:
  1. Missing Conclusions section
  2. 12 TODO placeholders still in manuscript
  3. NeqSim mentioned 6 times in body - use algorithm-first language
```

**Repeat**: edit → iterate → edit → iterate until score reaches 100%.

## Step 7: Audit Claims

```bash
python paperflow.py audit papers/tpflash_characterization_2026/
```

For characterization papers, this checks that results.json values appear in
the manuscript. For comparative papers, it verifies all [Claim Cx] references
are backed by approved_claims.json.

## Step 8: Format for Submission

```bash
python paperflow.py format papers/tpflash_characterization_2026/ \
    --journal fluid_phase_equilibria
```

Produces:
- `submission/paper.tex` — Journal-formatted LaTeX
- `submission/paper.docx` — Word document with proper styling

## Step 9: Validate Figures

```bash
python paperflow.py validate-figures papers/tpflash_characterization_2026/ \
    --journal fluid_phase_equilibria
```

Checks DPI (≥300), format (TIFF/EPS/PDF preferred), dimensions, file size,
and color mode using Pillow against journal-specific requirements.

## Step 10: Validate Bibliography

```bash
python paperflow.py validate-bib papers/tpflash_characterization_2026/
```

Checks `refs.bib` for:
- Missing required BibTeX fields (author, title, journal, year)
- Duplicate citation keys
- Empty fields, invalid years
- Cross-references: keys cited in `paper.md` but not in bib, and vice versa
- Missing DOI/URL recommendations

## Step 11: Render All Formats

```bash
python paperflow.py render papers/tpflash_characterization_2026/
```

One command that renders HTML, LaTeX, and Word:
- `submission/paper.html` — Standalone HTML with KaTeX math
- `submission/paper.tex` — Elsarticle LaTeX (requires journal profile)
- `submission/paper.docx` — Word document with OMML equations

## Step 12: Check Prose Quality

Before submission, run the prose quality analyzer:

```bash
python paperflow.py check-prose papers/tpflash_characterization_2026/
```

This scores your manuscript on four dimensions (0-100 each):
- **Readability** — Flesch-Kincaid grade level (target: ~12, college level)
- **Sentence structure** — flags sentences over 35 words
- **Active voice** — detects passive constructions (aim for <25%)
- **Conciseness** — catches hedging ("somewhat", "perhaps") and wordy phrases ("in order to" → "to")

## Step 13: Discover Missing References

Query Semantic Scholar for highly-cited papers you may have missed:

```bash
python paperflow.py suggest-refs papers/tpflash_characterization_2026/ --max 10
```

This extracts search terms from your plan.json title, research questions, and
paper.md abstract, then finds papers with 10+ citations that aren't already in
your refs.bib. Copy-paste BibTeX entries are provided for each suggestion.

## Step 14: Handle Reviewer Comments

After submission, when reviewer comments arrive:

```bash
python paperflow.py revise papers/tpflash_characterization_2026/ \
    --comments reviewer_comments.md
```

This creates `revision_1/` with a structured workspace for drafting responses.

## Step 15: Compare Revisions with Visual Diff

After revising, generate an HTML diff report showing exactly what changed:

```bash
python paperflow.py diff papers/tpflash_characterization_2026/
```

This compares the revision baseline with the current paper.md and produces a
color-coded HTML report with line-level additions, deletions, section changes,
and word count statistics. You can also compare arbitrary files:

```bash
python paperflow.py diff papers/tpflash_characterization_2026/ \
    --old revision_1/paper_r0_baseline.md --new paper.md
```

---

## The Iterative Workflow (Key to Quality)

The most important feature is the **edit → iterate → fix loop**:

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Edit paper │ --> │  Iterate    │ --> │  Fix issues │
│  (human or  │     │  (CLI check)│     │  (human or  │
│   agent)    │     │             │     │   agent)    │
└─────────────┘     └─────────────┘     └─────────────┘
       ^                                       │
       └───────────────────────────────────────┘
                    Repeat until 100%
```

Each `iterate` run:
1. Checks manuscript structure against journal requirements
2. Verifies all TODO placeholders are resolved
3. Traces quantitative claims to evidence (results.json or approved_claims.json)
4. Checks writing quality (abstract length, software-centric language)
5. Saves machine-readable `iteration_feedback.json` for agent consumption
6. Logs progress in `plan.json` workflow log

## Using with VS Code Copilot Agents

For agent-assisted writing, the agents in `agents/` work with the CLI:

1. `@planner` — Creates plan.json from a topic description
2. `@literature-reviewer` — Builds refs.bib and literature_map.md
3. `@benchmark` — Designs and runs experiments
4. `@scientific-writer` — Drafts sections from artifacts
5. `@reviewer-response` — Processes reviewer comments

The CLI handles the mechanical parts (formatting, auditing, iteration checks).
The agents handle the creative parts (writing, analysis, literature synthesis).

## Full Command Reference

| Command | Purpose | Key output |
|---------|---------|------------|
| `new` | Create paper project | Directory structure + plan.json |
| `benchmark` | Run experiments | results/raw/*.jsonl, results/summary*.json |
| `figures` | Generate figures | figures/*.png |
| `draft` | Generate manuscript | paper.md (from plan + results) |
| `iterate` | Quality check | iteration_feedback.json + console report |
| `audit` | Claim verification | Pass/fail verdict |
| `format` | Journal formatting | submission/paper.tex, paper.docx |
| `render` | Render all formats | submission/paper.html, .tex, .docx |
| `validate-figures` | Figure compliance | Per-figure DPI/format/size checks |
| `validate-bib` | Bibliography check | Missing fields, duplicates, cross-refs |
| `check-prose` | Prose quality scoring | Readability, passive voice, hedging report |
| `suggest-refs` | Citation discovery | Suggested references from Semantic Scholar |
| `diff` | Revision diff | Color-coded HTML diff report |
| `revise` | Reviewer response | revision_N/ workspace |
| `status` | Project overview | Console summary |

## Publication-Quality Figures

For journal-quality figures, use the `figure_style` module. It wraps
SciencePlots with journal presets and provides standard palettes and sizes:

```python
from tools.figure_style import apply_style, save_fig, PALETTE, FIG_SINGLE

apply_style("elsevier")                    # or "ieee", "nature", "acs"
fig, ax = plt.subplots(figsize=FIG_SINGLE) # 3.5 × 2.8 inches (single-column)
ax.plot(x, y, color=PALETTE[0])
ax.set_xlabel("Temperature (K)")
ax.set_ylabel("Convergence rate (%)")
save_fig(fig, "fig01_convergence.png", dpi=300)
```

Available constants: `FIG_SINGLE`, `FIG_DOUBLE`, `FIG_SINGLE_TALL`,
`FIG_DOUBLE_TALL`, `FIG_SQUARE`, and named colors `BLUE`, `ORANGE`,
`GREEN`, `PURPLE`, `PINK`, `LIME`, `GREY`.
