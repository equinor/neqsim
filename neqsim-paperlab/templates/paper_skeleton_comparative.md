# {{TITLE}}

<!-- Target Journal: {{JOURNAL}} -->
<!-- Paper Type: Type 1 — Algorithm Improvement (Comparative) -->
<!-- Generated: {{DATE}} -->
<!-- Status: DRAFT -->

## Highlights

- TODO: First highlight — state the improvement (max 85 chars)
- TODO: Second highlight — quantify the gain
- TODO: Third highlight — state the scope (how many cases/conditions tested)

## Abstract

TODO: Structured abstract (check journal word limit). Follow this flow:
- **Motivation**: 1-2 sentences on the problem importance.
- **Gap**: 1 sentence on what existing methods lack.
- **Approach**: 1-2 sentences describing the proposed improvement.
- **Results**: 2-3 sentences with key quantitative findings (include p-values or effect sizes).
- **Significance**: 1 sentence on broader impact.

## Keywords

TODO: keyword1; keyword2; keyword3; keyword4; keyword5

---

## 1. Introduction

### 1.1 Background

TODO: The [problem domain] is fundamental to [application field]. The standard
approach uses [baseline method], which [describe known limitations].

### 1.2 Prior work

TODO: Survey of existing approaches. Cite benchmark studies. Organize as:
- **Classical methods**: [references]
- **Modern improvements**: [references]
- **Open gaps**: What has not been addressed?

### 1.3 Contributions

This paper makes the following contributions:
1. TODO: Contribution 1 — the algorithmic modification
2. TODO: Contribution 2 — the benchmark design
3. TODO: Contribution 3 — the quantitative result

The remainder is organized as follows. Section 2 reviews the mathematical
framework. Section 3 describes the baseline and proposed algorithms. Section 4
details the benchmark design. Section 5 presents results, and Section 6
concludes.

---

## 2. Mathematical Framework

### 2.1 Governing equations

TODO: State the governing equations common to both baseline and candidate.

$$
\text{Equation 1}
$$

### 2.2 Baseline formulation

TODO: Describe the existing (baseline) mathematical formulation.

### 2.3 Proposed modification

TODO: Derive or motivate the proposed algorithmic change.

$$
\text{Modified equation}
$$

### 2.4 Convergence criterion

TODO: State the convergence criterion used for both methods.

---

## 3. Algorithm Description

### 3.1 Baseline algorithm (Method A)

TODO: Pseudocode or step-by-step description of the baseline.

```
Algorithm 1: Baseline Method
Input: ...
Output: ...
1. Initialize ...
2. While not converged:
   a. ...
   b. ...
3. Return solution
```

### 3.2 Proposed algorithm (Method B)

TODO: Pseudocode of the modified algorithm. Highlight differences from baseline.

```
Algorithm 2: Proposed Method
Input: ...
Output: ...
1. Initialize ...
2. While not converged:
   a. ... (MODIFIED STEP)
   b. ...
3. Return solution
```

### 3.3 Computational complexity

TODO: Compare theoretical complexity of both methods.

---

## 4. Benchmark Design

### 4.1 Test matrix

TODO: Describe the benchmark cases systematically.

| Family | Components | Parameter ranges | Cases |
|:---|:---|:---|---:|
| Family 1 | ... | ... | N₁ |
| Family 2 | ... | ... | N₂ |
| Family 3 | ... | ... | N₃ |
| **Total** | | | **N** |

### 4.2 Performance metrics

| Metric | Definition | Unit |
|:---|:---|:---|
| Convergence rate | Fraction reaching tolerance | % |
| Iteration count | Iterations to convergence | — |
| CPU time | Wall-clock per case | ms |
| Residual norm | Final error norm | — |

### 4.3 Statistical testing protocol

Paired comparison uses Wilcoxon signed-rank test (two-sided, $\alpha = 0.05$)
on matched case pairs. Effect size reported as Cohen's $d$ with bootstrap
95% confidence intervals ($B = 10{,}000$ resamples).

Claims require $p < 0.05$ and $|d| > 0.2$ (small effect) to be accepted.

### 4.4 Computational environment

TODO: Hardware, OS, Java version, NeqSim commit hash, timing methodology
(median of N repeats, JVM warm-up discarded).

---

## 5. Results and Discussion

### 5.1 Overall convergence comparison

TODO: Convergence rates for Method A vs Method B by family. [Claim C1]

| Family | Method A (%) | Method B (%) | $\Delta$ (pp) |
|:---|---:|---:|---:|
| Family 1 | | | |
| Family 2 | | | |
| **All** | | | |

### 5.2 Iteration count reduction

TODO: Distribution of iteration counts. Wilcoxon test result. [Claim C2]

### 5.3 Timing comparison

TODO: CPU time distributions. Median speedup. [Claim C3]

### 5.4 Regime analysis

TODO: Where does Method B help most? Where is it neutral? Near-critical? High-pressure?

### 5.5 Failure mode analysis

TODO: Cases where Method B does not improve or degrades. Root cause.

### 5.6 Cross-validation

TODO: Repeat key comparisons with a different EOS (e.g., SRK vs PR)
to confirm results are not EOS-specific.

---

## 6. Conclusions

TODO: Summarize findings tied to Claims C1–C3.

### 6.1 Summary

1. Claim C1 result
2. Claim C2 result
3. Claim C3 result

### 6.2 Limitations

TODO: State limitations explicitly:
- Scope limited to [system types tested]
- Not tested for [edge cases]
- [Other caveats]

### 6.3 Future work

TODO: Natural extensions of this work.

---

## Nomenclature

<!-- Generated by: python paperflow.py nomenclature papers/SLUG/ -->

TODO: Use the nomenclature extractor or list symbols manually.

## Acknowledgements

This work uses NeqSim, an open-source thermodynamic and process simulation
library developed at NTNU and Equinor.

## CRediT Author Contributions

<!-- Generated by: python paperflow.py credit papers/SLUG/ -->

TODO: Use the credit generator or write manually per CRediT taxonomy.

## Declaration of Competing Interest

The authors declare that they have no known competing financial interests
or personal relationships that could have appeared to influence the work
reported in this paper.

## Data Availability

All benchmark configurations, raw results, analysis scripts, and the
NeqSim source code are available at https://github.com/equinor/neqsim.

## References

<!-- See refs.bib — rendered by paperflow format command -->
