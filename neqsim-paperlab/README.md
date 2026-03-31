# NeqSim PaperLab — Scientific Paper Production System

A structured, agent-driven workflow for producing rigorous scientific papers
using NeqSim as the computational engine.

## Architecture

```
User idea  →  Planner  →  Literature  →  Algorithm  →  Benchmark  →  Validation  →  Writer  →  Formatter
                                             ↑                                          ↑
                                        NeqSim Java                              Approved claims
                                        (via tools)                              (from validation)
```

**Key principle:** Paper-writing agents never invent results. Every quantitative
claim must trace to a NeqSim tool output, a stored benchmark result, or a cited
reference.

## Quick Start

```bash
# 1. Create a new paper project
python paperflow.py new "TPflash Algorithm Improvements" \
    --journal fluid_phase_equilibria \
    --topic tpflash_algorithms

# 2. Run the benchmark suite
python paperflow.py benchmark papers/tpflash_algorithms_2026/

# 3. Generate figures and tables
python paperflow.py figures papers/tpflash_algorithms_2026/

# 4. Draft the manuscript
python paperflow.py draft papers/tpflash_algorithms_2026/

# 5. Format for target journal
python paperflow.py format papers/tpflash_algorithms_2026/

# 6. Audit reproducibility
python paperflow.py audit papers/tpflash_algorithms_2026/
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
| `run_flash_experiments` | Executing NeqSim flash calculations in batch |
| `analyze_convergence` | Interpreting convergence metrics, stability analysis |
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
- Statistical analysis of results
- Identify regimes where method improves / fails
- Produce approved_claims.json

### Stage 5: Write
- Draft all sections from structured artifacts
- Every claim linked to result ID
- Figures generated from benchmark data

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
