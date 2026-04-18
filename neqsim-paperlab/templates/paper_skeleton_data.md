# {{TITLE}}

<!-- Target Journal: Journal of Chemical & Engineering Data (or similar data journal) -->
<!-- Paper Type: Data / Property Correlation Paper -->
<!-- Generated: {{DATE}} -->
<!-- Status: DRAFT -->

## Highlights

- TODO: First highlight — property studied and system scope (max 85 chars)
- TODO: Second highlight — state model accuracy (AAD%)
- TODO: Third highlight — number of data points or conditions covered

## Abstract

TODO: Structured abstract. Follow this flow:
- **System**: What property, what systems (pure or mixture), what conditions.
- **Approach**: Experimental, computational, or both. Which model/EOS.
- **Data scope**: Temperature and pressure ranges, number of data points.
- **Accuracy**: AAD%, maximum deviation, comparison with existing correlations.
- **Significance**: Why this data matters (design, safety, standards).

## Keywords

TODO: property name; equation of state; system names; temperature range; correlation

---

## 1. Introduction

### 1.1 Importance of [property] data

TODO: Why is this property important for process design, safety, or operations?
What industrial applications depend on accurate values?

### 1.2 Available data and gaps

TODO: Survey existing experimental data and correlations.

| Reference | System | $T$ range (K) | $P$ range (MPa) | Points | Method |
|:---|:---|---:|---:|---:|:---|
| Author1 [1] | ... | ... | ... | ... | Experimental |
| Author2 [2] | ... | ... | ... | ... | MD simulation |
| **This work** | ... | ... | ... | ... | EOS (SRK/CPA) |

### 1.3 Objective

TODO: State what this paper adds — new conditions, new systems, new model,
or systematic comparison across wider ranges.

---

## 2. Theory / Modeling Approach

### 2.1 Equation of state

TODO: Describe the EOS or model used (SRK, PR, CPA, GERG-2008).
State the mathematical form concisely.

$$
P = \frac{RT}{V_m - b} - \frac{a(T)}{V_m(V_m + b) + b(V_m - b)}
$$

### 2.2 Mixing rules

TODO: State the mixing rules and binary interaction parameters used.

### 2.3 Property calculation method

TODO: How is the target property derived from the EOS?
(e.g., density from volume root, viscosity from corresponding-states,
thermal conductivity from residual contribution, etc.)

### 2.4 Pure-component parameters

| Component | $T_c$ (K) | $P_c$ (MPa) | $\omega$ | Source |
|:---|---:|---:|---:|:---|
| Component 1 | | | | DIPPR |
| Component 2 | | | | NIST |

---

## 3. Computational Details

### 3.1 Software and implementation

TODO: NeqSim version, Java version, relevant class names (in appendix
or supplementary), flash/property calculation methodology.

### 3.2 Calculation protocol

TODO: Step-by-step procedure:
1. Create thermodynamic system at specified $T$, $P$, and composition.
2. Set mixing rule and BIPs.
3. Run flash calculation (TP-flash for single phase, stability + split for VLE).
4. Call `initProperties()` for transport property initialization.
5. Extract target property.

### 3.3 Reference data sources

TODO: Which experimental databases are used for comparison?
(NIST TDE, DIPPR, Dechema, published literature)

### 3.4 Uncertainty estimation

TODO: For computational studies, report:
- **Model uncertainty**: sensitivity to BIP, EOS parameters
- **Numerical uncertainty**: convergence tolerance, resolution
- **Reference data uncertainty**: reported experimental uncertainties

---

## 4. Results and Discussion

### 4.1 Pure-component validation

TODO: Validate model against well-known pure-component data first.

| $T$ (K) | $P$ (MPa) | $\rho_{\text{exp}}$ (kg/m³) | $\rho_{\text{calc}}$ (kg/m³) | Dev. (%) |
|---:|---:|---:|---:|---:|
| | | | | |

### 4.2 Binary mixture results

TODO: Tabulate computed properties alongside reference values.

### 4.3 Deviation analysis

TODO: Report AAD%, bias, and maximum deviation. Include deviation plot.

| System | $N_{\text{pts}}$ | AAD (%) | Bias (%) | Max Dev. (%) |
|:---|---:|---:|---:|---:|
| System 1 | | | | |
| System 2 | | | | |
| **Overall** | | | | |

### 4.4 Comparison with other models

TODO: Compare the current model against competing correlations or EOS.

### 4.5 Temperature and pressure sensitivity

TODO: Show how deviations vary with $T$ and $P$. Identify regions where
the model is strong vs weak.

### 4.6 Correlation development (if applicable)

TODO: If proposing a new correlation, state the functional form, fitted
parameters, and fitting statistics.

$$
\text{Property}(T, P, x) = \ldots
$$

| Parameter | Value | Unit | 95% CI |
|:---|---:|:---|---:|
| $a_1$ | | | |
| $a_2$ | | | |

---

## 5. Conclusions

TODO:
1. Property values computed for [N] state points across [T, P, x ranges].
2. Model achieves AAD of X% against [reference database].
3. [Model strengths and weaknesses by region].
4. Data are provided in Supporting Information for use by other researchers.

### 5.1 Limitations

TODO:
- Valid range: $T$ = [min]–[max] K, $P$ = [min]–[max] MPa.
- Not validated near [critical region / phase boundary / etc.].
- BIPs tuned to [source data] — may not transfer to other systems.

### 5.2 Recommendations

TODO: Recommendations for practitioners using these property values.

---

## Nomenclature

TODO: List symbols, Greek letters, subscripts.

## Acknowledgements

This work uses NeqSim, an open-source thermodynamic and process simulation
library developed at NTNU and Equinor.

## CRediT Author Contributions

TODO: Per CRediT taxonomy.

## Declaration of Competing Interest

The authors declare no competing financial interests.

## Data Availability

Full tabulated property data are provided in the Supporting Information.
The NeqSim source code is available at https://github.com/equinor/neqsim.

## References

<!-- See refs.bib -->

---

## Supporting Information

Supporting Information is available and includes:
- Table S1: Complete tabulated property data (all state points)
- Table S2: Pure-component parameters used
- Table S3: Binary interaction parameters
- Figure S1: Deviation plots by temperature
- Figure S2: Deviation plots by pressure
- Listing S1: NeqSim calculation script (Python)
