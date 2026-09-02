---
title: "DOE Big Hill C5-175 degF PIANO molar-mass qualification"
description: "Public-data qualification of a PIANO family and carbon-number aggregate molar mass for the DOE Big Hill Sweet light naphtha cut."
---

# DOE Big Hill C5-175 degF PIANO molar-mass qualification

## Scope

This qualification freezes public U.S. Department of Energy Strategic Petroleum Reserve data for
the Big Hill Sweet C5-175 degF fraction and exercises
`OilAssayCharacterisation.calculatePianoMolarMassKgPerMol(...)`. The helper converts a mass-basis
PIANO family/carbon-number table into the mixture number-average molar mass required by NeqSim's
existing explicit-molar-mass pseudo-component path.

This is a composition and mass-bookkeeping qualification. It does not identify individual
isomers, infer a missing lower boiling boundary, or validate critical-property, VLE, flash,
distillation-yield, or conversion-unit predictions.

## Public evidence and provenance

The numerical inputs are taken from the official DOE SPR Big Hill Sweet workbooks:

- [comprehensive assay](https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx): C5-175
  degF yield 5.22 mass%, SG60/60 0.6731, and upper boundary 175 degF;
- [PIANO analysis](https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx): the
  hydrocarbon-family/carbon-number mass table used below.

Both workbooks report 24 September 2021. Only cited numerical facts are reproduced in NeqSim; the
source workbooks are not redistributed. The calculation uses the conventional atomic weights
C = 12.011 and H = 1.008 g/mol, which are within the current standard atomic-weight intervals
published by the [CIAAW](https://www.ciaaw.org/atomic-weights.htm).

## Frozen PIANO inputs

| Family | Carbon number | Mass% |
| --- | ---: | ---: |
| Paraffin | 3 | 0.32 |
| Paraffin | 4 | 3.49 |
| Paraffin | 5 | 21.47 |
| Paraffin | 6 | 15.28 |
| Paraffin | 7 | 0.94 |
| Iso-paraffin | 4 | 0.59 |
| Iso-paraffin | 5 | 10.96 |
| Iso-paraffin | 6 | 17.99 |
| Iso-paraffin | 7 | 4.04 |
| Iso-paraffin | 8 | 0.05 |
| Aromatic | 6 | 3.34 |
| Aromatic | 7 | 0.12 |
| Naphthene | 5 | 3.39 |
| Naphthene | 6 | 14.38 |
| Naphthene | 7 | 3.60 |
| Naphthene | 8 | 0.05 |

The row sum is 100.01 mass% because the source reports two decimals. The implementation therefore
accepts a small source-rounding closure error and normalizes by the actual submitted total. Frozen
family sums are 41.50% paraffins, 33.63% iso-paraffins, 3.46% aromatics, and 21.42% naphthenes.

## Calculation

The family formulas are paraffin and iso-paraffin `CnH(2n+2)`, naphthene `CnH(2n)`, and aromatic
`CnH(2n-6)`. For mass fractions `w_i` and species-group molar masses `M_i`, the number-average
mixture molar mass is

```text
M_n = 1 / sum_i(w_i / M_i)
```

The frozen expected result is `0.0791538366563 kg/mol`. The API is intentionally O(n), independent
of input row order, and rejects null families, impossible family/carbon-number combinations,
non-finite or negative weights, and totals outside the documented rounding tolerance.

## Application and acceptance criteria

The regression applies the published 5.22 mass% C5-175 degF cut to a 1 kg assay using:

- the PIANO-derived explicit molar mass;
- the measured SG60/60 of 0.6731;
- only the published 175 degF upper boiling boundary.

Acceptance requires:

1. PIANO row closure within 0.02 percentage point and the expected molar mass within `1e-14 kg/mol`;
2. identical output after reversing source-row order;
3. exact 0.0522 kg pseudo-component mass closure within `1e-10 kg`;
4. retention of the one-sided 175 degF boundary without creating a lower boundary; and
5. fail-closed invalid-input behavior before the thermodynamic system is mutated.

No individual species distribution or mean boiling point is claimed. The resulting molar mass is a
reproducible family/carbon-number aggregate suitable for the explicit assay-cut bookkeeping path.
