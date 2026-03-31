# {{TITLE}}

<!-- Target Journal: {{JOURNAL}} -->
<!-- Paper Type: {{PAPER_TYPE}} -->
<!-- Generated: {{DATE}} -->
<!-- Status: DRAFT -->

<!--
  Paper types and their section structure:
  - comparative:      Algorithm A vs B — standard sections below
  - characterization: Systematic evaluation of existing algorithm — skip §3.2 "Proposed improvement"
  - method:           New formulation — expand §2 "Mathematical Framework", skip §4.3 "Statistical testing"
  - application:      Case study — expand §5 "Results", minimal §2 and §3
-->

## Highlights

- TODO: First highlight (max 85 chars per line, max 5 highlights)
- TODO: Second highlight
- TODO: Third highlight

## Abstract

TODO: Write a structured abstract (max 200 words for FPE).
State the problem, approach, key results, and significance.

## Keywords

TODO; comma-separated; 4–6 keywords relevant to the study

---

## 1. Introduction

### 1.1 Background

TODO: Describe the problem domain and its importance.

<!-- comparative: Frame as "existing methods have limitation X" -->
<!-- characterization: Frame as "method X is widely used but lacks systematic evaluation" -->
<!-- method: Frame as "current formulations miss aspect Y" -->
<!-- application: Frame as "this engineering problem requires coupled simulation" -->

### 1.2 Prior work

TODO: Survey of existing approaches and key references. Identify the gap.

### 1.3 Contributions

TODO: State the specific contributions of this paper:
1. Contribution 1
2. Contribution 2
3. Contribution 3

---

## 2. Mathematical Framework

### 2.1 Governing equations

TODO: State the mathematical problem being solved.

<!-- Flash papers: Phase equilibrium, Rachford-Rice, fugacity -->
<!-- Reactor papers: Gibbs energy minimization, element balance, chemical potential -->
<!-- PVT papers: Property correlations, mixing rules, departure functions -->

### 2.2 Equation of state / thermodynamic model

TODO: Describe the EOS or model used.

### 2.3 Solution method

TODO: Describe the numerical method.

<!-- comparative: Describe both baseline and candidate -->
<!-- characterization: Describe the single method under study in detail -->
<!-- method: This is the core section — full derivation here -->

---

## 3. Algorithm Description

### 3.1 Current algorithm

TODO: Describe the algorithm as implemented.

<!-- characterization: This is the main content — pseudocode, decision logic, parameters -->
<!-- comparative: Brief baseline description -->

### 3.2 Proposed improvement

<!-- [SKIP for characterization and application papers] -->

TODO: Detail the algorithmic modification (comparative/method papers only).

### 3.3 Implementation

TODO: Pseudocode and implementation details.

---

## 4. Benchmark Design

### 4.1 Test matrix

TODO: Describe the benchmark configuration.

<!-- Use paper-type appropriate table: -->
<!-- Flash:   Fluid families × T × P ranges -->
<!-- Reactor: Reaction systems × T × P × feed compositions -->
<!-- PVT:     Fluid types × property ranges × measurement types -->

| Category | Description | Conditions | Cases |
|:---------|:------------|:-----------|------:|
| TODO     | TODO        | TODO       | TODO  |

### 4.2 Performance metrics

| Metric | Definition | Unit |
|:-------|:-----------|:-----|
| TODO   | TODO       | TODO |

### 4.3 Statistical testing

<!-- [SKIP for characterization — use coverage completeness instead] -->
<!-- [SKIP for application — use engineering validation instead] -->

TODO: Describe the statistical tests for paired comparison (comparative papers).

### 4.4 Cross-validation

TODO: Describe cross-validation approach (different EOS, reference data, etc.).

---

## 5. Results and Discussion

### 5.1 Overview

TODO: Summary of aggregate results.

<!-- characterization: Present behavior maps, convergence profiles, property accuracy -->
<!-- comparative: Present paired comparison results with statistical significance -->
<!-- method: Present mathematical properties (convergence order, stability proofs) -->
<!-- application: Present engineering results with uncertainty -->

### 5.2 [Topic-specific subsection]

TODO: Detailed results.

### 5.3 [Topic-specific subsection]

TODO: Detailed results.

### 5.4 Edge cases and failure modes

TODO: Document where the method struggles. This is mandatory.

### 5.5 Cross-validation results

TODO: Results from cross-validation (different EOS, reference comparison, etc.).

### 5.5 Robustness analysis

TODO: Failure mode analysis.

---

## 6. Conclusions

TODO: Summarize findings tied to claims C1–C4.

### 6.1 Limitations

TODO: State limitations explicitly. This is mandatory.

### 6.2 Future work

TODO: Outline extensions.

---

## Acknowledgements

This work uses NeqSim, an open-source thermodynamic and process simulation library
developed at NTNU and Equinor.

## Data availability

All benchmark configurations, raw results, and analysis scripts are available in the
supplementary material.

## References

<!-- See refs.bib — rendered by journal_formatter agent -->
