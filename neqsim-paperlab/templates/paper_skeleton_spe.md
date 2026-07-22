# SPE-XXXXXX-MS

# {{TITLE}}

<!-- UNIT CONVENTION: SI units throughout. Temperature in K, pressure in Pa/kPa/MPa,
     density in kg/m³, energy in J/kJ. See PAPER_WRITING_GUIDELINES.md "SI Units (MANDATORY)". -->

**Author1**, SPE, Affiliation1; **Author2**, SPE, Affiliation2

Copyright 20XX, Society of Petroleum Engineers

This paper was prepared for presentation at the {{CONFERENCE}} held in
{{LOCATION}}, {{DATES}}.

---

## Abstract

TODO: SPE allows up to 300 words. Structure as:
- Problem statement (1-2 sentences)
- Approach / method (2-3 sentences)
- Results (3-4 sentences with numbers)
- Novelty / significance (1-2 sentences)
- Application / recommendation (1 sentence)

---

## Introduction

TODO: Describe the field problem or engineering challenge. SPE papers
emphasize practical relevance — connect to real operations early.

State the objective clearly at the end of the Introduction:
"The objective of this paper is to [verb] [subject] for [application]."

---

## Theory and Background

### [Subsection title]

TODO: Relevant theory. Keep concise — SPE readers are practitioners.
Reference established methods rather than re-deriving.

### [Subsection title]

TODO: Additional background as needed.

---

## Methodology

### Model description

TODO: Describe the simulation or experimental approach.

For NeqSim-based work, describe:
- Equation of state and mixing rules
- Flash calculation approach
- Process equipment configuration
- Key assumptions and simplifications

### Input data

TODO: Fluid composition, operating conditions, equipment specifications.

| Parameter | Value | Unit |
|:---|---:|:---|
| Reservoir temperature | | °C (°F) |
| Wellhead pressure | | bara (psia) |
| Production rate | | Sm³/d (MMSCF/d) |
| Water depth | | m (ft) |
| Pipeline length | | km (miles) |

### Case matrix

TODO: Describe the cases studied.

| Case | Description | Key variable |
|:---|:---|:---|
| Base case | ... | ... |
| Case 2 | ... | ... |
| Case 3 | ... | ... |

---

## Results and Discussion

### [First result topic]

TODO: Present key results with figures and tables. SPE papers
typically present 3-5 key findings.

### [Second result topic]

TODO: Additional results.

### [Third result topic]

TODO: Additional results.

### Field implications

TODO: SPE reviewers expect practical implications. Address:
- How do results change operating practice?
- What are the design recommendations?
- Where are the safety margins?

---

## Conclusions

TODO: Numbered conclusions (SPE convention):

1. First conclusion with quantitative support.
2. Second conclusion.
3. Third conclusion.
4. Recommendation for field application.

---

## Nomenclature

<!-- SPE requires a nomenclature section for all technical papers -->

| Symbol | Description | Unit |
|:---|:---|:---|
| $P$ | Pressure | Pa |
| $T$ | Temperature | K |
| $\rho$ | Density | kg/m³ |
| $\mu$ | Dynamic viscosity | Pa·s |
| $x_i$ | Liquid mole fraction of component $i$ | — |
| $y_i$ | Vapor mole fraction of component $i$ | — |
| $z_i$ | Feed mole fraction of component $i$ | — |
| $K_i$ | Equilibrium ratio of component $i$ | — |
| $\beta$ | Vapor fraction | — |

**Subscripts**

| Subscript | Description |
|:---|:---|
| $c$ | Critical property |
| $r$ | Reduced property |
| $L$ | Liquid phase |
| $V$ | Vapor phase |

---

## Acknowledgements

The authors thank [company/institution] for permission to publish this
work. This study uses NeqSim, an open-source thermodynamic and process
simulation library (https://github.com/equinor/neqsim).

---

## References

<!-- SPE reference style: numbered, in order of appearance -->
<!-- Format: Author1, A.B. and Author2, C.D. Year. Title. Conference/Journal. DOI. -->

---

## SI Metric Conversion Factors

<!-- SPE REQUIRES this appendix for all non-SI units used -->

| From | Multiply by | To |
|:---|:---|:---|
| bbl | 1.589 873 E-01 | m³ |
| ft | 3.048* E-01 | m |
| °F | (°F - 32)/1.8 | °C |
| psi | 6.894 757 E+00 | kPa |
| lbm | 4.535 924 E-01 | kg |
| Btu | 1.055 056 E+00 | kJ |
| cp | 1.0* E-03 | Pa·s |
| MMSCF/d | 2.831 685 E+04 | Sm³/d |

*Conversion factor is exact.
