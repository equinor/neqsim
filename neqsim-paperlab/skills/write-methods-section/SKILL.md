# Skill: Write Methods Section

## Purpose

Draft a publication-quality Methods section for a computational thermodynamics
paper. Includes mathematical notation, algorithm pseudocode, and benchmark
design description.

## When to Use

- Drafting the Methods section of a flash algorithm paper
- Documenting algorithmic changes for peer review
- Writing supplementary material with implementation details

## Methods Section Structure

A good Methods section for a flash algorithm paper has these subsections:

### 1. Thermodynamic Framework

State the phase equilibrium conditions:

```markdown
## 2. Methods

### 2.1 Phase Equilibrium Conditions

At thermodynamic equilibrium, the fugacity of each component $i$ must be equal
across all phases:

$$f_i^V(T, P, \mathbf{y}) = f_i^L(T, P, \mathbf{x}) \quad \forall i = 1, \ldots, N_c$$

where $f_i^V$ and $f_i^L$ are the fugacities of component $i$ in the vapor and
liquid phases, $\mathbf{y}$ and $\mathbf{x}$ are the vapor and liquid mole
fraction vectors, and $N_c$ is the number of components.

The equilibrium ratio (K-value) is defined as:

$$K_i = \frac{y_i}{x_i} = \frac{\hat{\phi}_i^L(T, P, \mathbf{x})}{\hat{\phi}_i^V(T, P, \mathbf{y})}$$

where $\hat{\phi}_i$ denotes the fugacity coefficient.
```

### 2. Equation of State

Describe the EOS used:

```markdown
### 2.2 Equation of State

Calculations in this work use the Soave-Redlich-Kwong (SRK) equation of state
\cite{Soave1972}:

$$P = \frac{RT}{V - b} - \frac{a(T)}{V(V + b)}$$

with classical van der Waals mixing rules:

$$a = \sum_i \sum_j x_i x_j (a_i a_j)^{1/2} (1 - k_{ij})$$

$$b = \sum_i x_i b_i$$

where $k_{ij}$ are binary interaction parameters.
```

### 3. Baseline Algorithm

Describe the algorithm being compared against:

```markdown
### 2.3 Baseline TP Flash Algorithm

The baseline implementation follows the classical two-stage approach
\cite{Michelsen1982a, Michelsen1982b}:

**Stage 1: Successive Substitution (SS)**

Starting from Wilson K-value estimates:

$$K_i^{(0)} = \frac{P_{c,i}}{P} \exp\left[5.373(1 + \omega_i)\left(1 - \frac{T_{c,i}}{T}\right)\right]$$

the phase fraction $\beta$ is obtained from the Rachford-Rice equation:

$$g(\beta) = \sum_{i=1}^{N_c} \frac{z_i(K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

K-values are updated by:

$$K_i^{(n+1)} = K_i^{(n)} \frac{\hat{\phi}_i^L(\mathbf{x}^{(n)})}{\hat{\phi}_i^V(\mathbf{y}^{(n)})}$$

This is repeated until convergence or a maximum of $N_{SS}$ iterations.

**Stage 2: Newton-Raphson (NR)**

If SS has not converged, the algorithm switches to Newton-Raphson using
Michelsen's u-variable formulation \cite{Michelsen1982b}:

$$u_i = \beta \cdot y_i$$

The residual vector is:

$$r_i = \ln \hat{\phi}_i^V(\mathbf{y}) - \ln \hat{\phi}_i^L(\mathbf{x})$$

and the Jacobian:

$$J_{ij} = \frac{\partial r_i}{\partial u_j}$$

The update is:

$$\mathbf{u}^{(n+1)} = \mathbf{u}^{(n)} - \alpha \mathbf{J}^{-1} \mathbf{r}$$

where $\alpha$ is a line search parameter (Armijo backtracking).
```

### 4. Proposed Modification

Describe YOUR change clearly:

```markdown
### 2.4 Proposed Modification: [Name]

We propose modifying the [specific aspect] of the baseline algorithm.

[Describe the mathematical basis]

[Show the modified equations]

[Explain why this should improve convergence]

**Pseudocode:**

\begin{algorithm}
\caption{Modified TP Flash with [Name]}
\begin{algorithmic}
\STATE Initialize K-values using Wilson correlation
\FOR{$n = 1$ to $N_{max}$}
    \STATE Solve Rachford-Rice for $\beta$
    \STATE Compute $\mathbf{x}, \mathbf{y}$ from K-values and $\beta$
    \STATE Evaluate fugacity coefficients
    \STATE Update K-values via SS
    \IF{[switching criterion met]}
        \STATE Switch to Newton-Raphson
    \ENDIF
    \IF{$\|\mathbf{r}\| < \epsilon$}
        \STATE \RETURN converged
    \ENDIF
\ENDFOR
\end{algorithmic}
\end{algorithm}
```

### 5. Stability Analysis

```markdown
### 2.5 Phase Stability Analysis

Phase stability is verified using the tangent plane distance (TPD) criterion
\cite{Michelsen1982a}:

$$\text{TPD}(\mathbf{w}) = \sum_{i=1}^{N_c} w_i \left[\ln w_i + \ln \hat{\phi}_i(\mathbf{w}) - \ln z_i - \ln \hat{\phi}_i(\mathbf{z})\right]$$

A phase is unstable (and will split) if $\text{TPD} < 0$ for any trial
composition $\mathbf{w}$.
```

### 6. Benchmark Design

```markdown
### 2.6 Benchmark Design

To evaluate the proposed modification, we construct a systematic benchmark
covering [N] test cases across [M] fluid families (Table 1).

**Table 1: Fluid families and their characteristics**

| Family | Components | N_c | Characteristics |
|--------|-----------|-----|-----------------|
| ... | ... | ... | ... |

Compositions are generated using Dirichlet sampling around base compositions
with concentration parameter $\alpha = 50$, ensuring realistic variation while
maintaining family character.

The pressure-temperature space is sampled using a logarithmic grid in pressure
([P_min]–[P_max] bara) and linear grid in temperature ([T_min]–[T_max] K),
with additional stress cases near phase boundaries and the critical region.
```

### 7. Software Implementation

```markdown
### 2.7 Implementation

All calculations are performed using NeqSim \cite{neqsim2024}, an open-source
Java library for thermodynamic and process simulation. The baseline and
modified algorithms are implemented in the `TPflash` class of NeqSim version
[version] (commit [hash]).

Timing measurements use `System.nanoTime()` with a warm-up phase of 10
flashes, followed by 3 timed repetitions per case. The median wall-clock time
is reported to minimize sensitivity to JIT compilation and garbage collection.

All experiments were run on [hardware description] with OpenJDK [version].
```

## Notation Convention

Use consistent notation throughout:

| Symbol | Meaning |
|--------|---------|
| $N_c$ | Number of components |
| $z_i$ | Feed composition (mole fraction) |
| $x_i$ | Liquid composition |
| $y_i$ | Vapor composition |
| $K_i$ | Equilibrium ratio |
| $\beta$ | Vapor phase fraction |
| $\hat{\phi}_i$ | Fugacity coefficient |
| $T$ | Temperature (K) |
| $P$ | Pressure (Pa or bara) |
| $R$ | Universal gas constant |
| $\omega_i$ | Acentric factor |

## LaTeX Math Tips

- Use `\mathbf{x}` for vectors
- Use `\hat{\phi}` for fugacity coefficients
- Use `\ln` not `log` for natural logarithm
- Number all equations that are referenced in text
- Use `\text{TPD}` not `TPD` in math mode

## Writing Quality Check

After drafting the Methods section, run the prose quality tool:

```bash
python paperflow.py check-prose papers/<paper_slug>/
```

The Methods section often has the highest Flesch-Kincaid grade due to technical
terminology, which is expected. Focus on:
- Splitting sentences over 35 words (common around equation descriptions)
- Reducing unnecessary passive voice ("was calculated" → "we calculated")
- Cutting hedging language — Methods should be precise and definitive
