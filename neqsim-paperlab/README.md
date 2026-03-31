# NeqSim PaperLab

A structured, agent-driven workflow for producing rigorous scientific papers
using NeqSim as the computational engine.

## Architecture

```
User idea  →  Planner  →  Literature  →  Algorithm  →  Benchmark  →  Validation  →  Writer  →  Formatter
                 ↑                           ↑              ↑                            ↑
            paper_type                  NeqSim Java     cross-val                  claims OR
            routing                     (via tools)     (EOS/ref)                  results.json
```

**Key principle:** Paper-writing agents never invent results. Every quantitative
claim must trace to a NeqSim tool output, a stored benchmark result, or a cited
reference.

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
# 1. Create a new paper project (specify paper_type!)
python paperflow.py new "Reactive Gibbs Convergence" \
    --journal fluid_phase_equilibria \
    --topic gibbs_reactor \
    --paper_type characterization

# 2. Run the benchmark suite
python paperflow.py benchmark papers/gibbs_reactor_2026/

# 3. Generate figures and tables
python paperflow.py figures papers/gibbs_reactor_2026/

# 4. Draft the manuscript
python paperflow.py draft papers/gibbs_reactor_2026/

# 5. Format for target journal
python paperflow.py format papers/gibbs_reactor_2026/

# 6. Audit reproducibility
python paperflow.py audit papers/gibbs_reactor_2026/
```

## Directory Structure

```
neqsim-paperlab/
├── README.md                     # This file
├── paperflow.py                  # CLI orchestrator
├── journals/                     # Journal profile configs
│   └── fluid_phase_equilibria.yaml
├── agents/                       # Agent definitions (VS Code Copilot)
│   ├── planner.agent.md
│   ├── literature_reviewer.agent.md
│   ├── algorithm_engineer.agent.md
│   ├── benchmark.agent.md
│   ├── validation.agent.md
│   ├── scientific_writer.agent.md
│   └── journal_formatter.agent.md
├── skills/                       # Reusable scientific procedures
│   ├── design_flash_benchmark/SKILL.md
│   ├── run_flash_experiments/SKILL.md
│   ├── analyze_convergence/SKILL.md
│   ├── write_methods_section/SKILL.md
│   └── journal_formatting/SKILL.md
├── tools/                        # Python wrappers for NeqSim
│   ├── __init__.py
│   ├── neqsim_scientific_tools.py
│   ├── flash_benchmark.py
│   ├── paper_renderer.py
│   └── claim_tracer.py
├── workflows/                    # Workflow definitions
│   ├── new_paper.yaml
│   └── revise_paper.yaml
├── templates/                    # Document templates
│   ├── paper_skeleton.md
│   ├── cover_letter.md
│   └── response_to_reviewers.md
└── papers/                       # Paper projects (one folder per paper)
    └── tpflash_algorithms_2026/
        ├── plan.json
        ├── paper.md
        ├── refs.bib
        ├── claims_manifest.json
        ├── figures/
        ├── tables/
        └── results/
```

## Agents

| Agent | Purpose | Input | Output |
|-------|---------|-------|--------|
| **Planner** | Turns idea → research plan | Topic, journal, angle | plan.json, outline |
| **Literature Reviewer** | Builds technical context | Topic, keywords | literature_map.md, gap_statement |
| **Algorithm Engineer** | Proposes code changes | NeqSim source, plan | Pseudocode, impl tickets |
| **Benchmark** | Runs experiment suites | Config, algorithm version | Results, metrics, failures |
| **Validation** | Decides if claims hold | Results from benchmark | approved_claims.json |
| **Scientific Writer** | Drafts manuscript | All artifacts | paper.md |
| **Journal Formatter** | Adapts to journal rules | paper.md, journal profile | paper.tex / paper.docx |

## Skills

| Skill | When to Use |
|-------|-------------|
| `design_flash_benchmark` | Creating test matrices for flash algorithm comparisons |
| `design_reactor_benchmark` | Creating test matrices for Gibbs reactor / chemical equilibrium benchmarks |
| `run_flash_experiments` | Executing NeqSim flash calculations in batch |
| `analyze_convergence` | Interpreting convergence metrics, stability analysis |
| `analyze_gibbs_convergence` | Analyzing Gibbs reactor convergence, Jacobian conditioning, element balance |
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
2. **Reproducibility**: Every figure/table must be regenerable from stored data
3. **Claim tracing**: `claims_manifest.json` maps each claim to its evidence
4. **Version control**: Algorithm changes on named branches
5. **Validation gates**: Claims must pass statistical validation before entering manuscript

## Integration with NeqSim

Tools communicate with NeqSim through:
- **Python wrappers** (`tools/neqsim_scientific_tools.py`) using neqsim-python
- **MCP server** (`neqsim-mcp-server/`) for agent tool calls
- **Direct Java** via jpype for performance-critical benchmarks

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
