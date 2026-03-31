# Paper Writing Guidelines — Algorithm-First Scientific Papers

## Core Principle

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

### Abstract (max 200 words)
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
(Word) in a single invocation.

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
pip install python-docx latex2mathml lxml pyyaml
```

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
