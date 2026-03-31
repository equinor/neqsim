# {{TITLE}}

<!-- Target Journal: {{JOURNAL}} -->
<!-- Generated: {{DATE}} -->
<!-- Status: DRAFT -->

## Highlights

- TODO: First highlight (max 85 chars per line, max 5 highlights)
- TODO: Second highlight
- TODO: Third highlight

## Abstract

TODO: Write a structured abstract (max 200 words for FPE).
State the problem, approach, key results, and significance.

## Keywords

TODO; thermodynamic flash; successive substitution; Newton-Raphson; vapor-liquid equilibrium; natural gas

---

## 1. Introduction

### 1.1 Background

TODO: The isothermal flash problem — thermodynamic equilibrium at fixed T and P — is a
fundamental computation in process simulation, reservoir engineering, and pipeline flow
assurance. The Rachford-Rice equation [Claim C_RR] governs phase-split determination,
while Michelsen's framework [Claim C_Mich] established the two-stage approach of
stability analysis followed by phase split.

### 1.2 Prior work

TODO: Survey of existing approaches — successive substitution (SS), Newton-Raphson (NR),
direct substitution, and hybrid methods. Cite benchmark studies. Identify gap.

### 1.3 Contributions

TODO: State the specific contributions of this paper:
1. Contribution 1
2. Contribution 2
3. Contribution 3

---

## 2. Mathematical Framework

### 2.1 Phase equilibrium conditions

The condition for vapor–liquid equilibrium at temperature $T$ and pressure $P$
requires equal fugacities for each component $i$:

$$
f_i^L(T, P, \mathbf{x}) = f_i^V(T, P, \mathbf{y})
$$

where $\mathbf{x}$ and $\mathbf{y}$ are liquid and vapor mole fraction vectors.

### 2.2 Equation of state

Cubic equations of state express fugacity through:

$$
\ln \hat\phi_i = \frac{1}{RT} \int_V^\infty \left[ \left(\frac{\partial P}{\partial n_i}\right)_{T,V,n_{j\neq i}} - \frac{RT}{V} \right] dV - \ln Z
$$

### 2.3 Rachford-Rice equation

The phase fraction $\beta$ satisfies:

$$
g(\beta) = \sum_{i=1}^{N_c} \frac{z_i (K_i - 1)}{1 + \beta(K_i - 1)} = 0
$$

### 2.4 Successive substitution

K-factors are updated iteratively:

$$
K_i^{(n+1)} = \frac{\hat\phi_i^L(\mathbf{x}^{(n)})}{\hat\phi_i^V(\mathbf{y}^{(n)})}
$$

### 2.5 Newton-Raphson formulation

TODO: Describe the u-variable formulation used in NeqSim.

---

## 3. Algorithm Description

### 3.1 Baseline algorithm

TODO: Describe NeqSim's current TPflash implementation.

### 3.2 Proposed improvement

TODO: Detail the algorithmic modification.

### 3.3 Implementation

TODO: Pseudocode of modified algorithm.

---

## 4. Benchmark Design

### 4.1 Test matrix

TODO: Describe the benchmark configuration.

| Fluid family | Components | $T$ range (K) | $P$ range (bar) | Cases |
|:---|:---|---:|---:|---:|
| Lean gas | CH₄/C₂/C₃/N₂/CO₂ | 200–400 | 1–200 | 500 |
| Rich gas | CH₄–C₅/N₂/CO₂ | 220–450 | 5–300 | 500 |
| Gas condensate | CH₄–C₁₀/N₂/CO₂ | 250–500 | 10–500 | 500 |

### 4.2 Performance metrics

| Metric | Definition | Unit |
|:---|:---|:---|
| Convergence rate | fraction of cases reaching tol < 10⁻¹⁰ | % |
| Iteration count | SS + NR iterations to convergence | — |
| CPU time | wall-clock per flash | ms |
| Residual norm | $\|\ln\phi^L - \ln\phi^V\|_2$ | — |

### 4.3 Statistical testing

TODO: Describe the Wilcoxon signed-rank test for paired comparison.

---

## 5. Results and Discussion

### 5.1 Convergence rate comparison

TODO: Overall convergence rates by fluid family. [Claim C1]

### 5.2 Iteration count reduction

TODO: Distribution of iteration counts. [Claim C2]

### 5.3 Timing comparison

TODO: CPU time comparison. [Claim C3]

### 5.4 Near-critical behavior

TODO: Performance near critical points. [Claim C4]

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
