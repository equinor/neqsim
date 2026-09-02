---
title: "DOE Big Hill Sweet complete modeled assay slate"
description: "Reproducible full-crude reference composition assembled from qualified DOE comprehensive-assay and PIANO inputs."
---

# DOE Big Hill Sweet complete modeled assay slate

`DoeBigHillSweetAssay` assembles the refinery campaign's qualified public inputs into one reusable
Java/JPype assay definition. The factory configures an `OilAssayCharacterisation` on a caller-selected
mass basis and deliberately requires a separate `apply()` call before the thermodynamic system is
mutated.

## Primary public sources

- DOE SPR [Big Hill Sweet comprehensive assay](https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx),
  reported 24 September 2021;
- DOE SPR [Big Hill Sweet PIANO workbook](https://www.spr.doe.gov/reports/Assays/2021/BigHillSwPIANO.xlsx).

The comprehensive workbook reports the following non-overlapping mass-basis cuts. The mass yields
close to exactly 100.00% at the displayed precision.

| Cut | Mass % | SG60/60 | Sulfur mass % | Nitrogen mass % | Characterization |
| --- | ---: | ---: | ---: | ---: | --- |
| C2-C4 gas | 1.70 | — | 0 assumed | 0 assumed | DOE PIANO C2-C4 subset normalized over the whole gas slice |
| C5-175 degF | 5.22 | 0.6731 | 0.0008 | 0 assumed | PIANO-derived 0.0791538366563 kg/mol; upper boundary only |
| 175-250 degF | 8.32 | 0.7432 | 0.0026 | 0 assumed | finite DOE cut boundaries |
| 250-375 degF | 12.55 | 0.7817 | 0.019 | 0 assumed | finite DOE cut boundaries |
| 375-530 degF | 16.19 | 0.8297 | 0.096 | 0.0018 | finite DOE cut boundaries |
| 530-650 degF | 13.18 | 0.8604 | 0.313 | 0.0186 | finite DOE cut boundaries |
| 650-850 degF | 18.44 | 0.9039 | 0.534 | 0.102 | finite DOE cut boundaries |
| 850-1050 degF | 12.84 | 0.9336 | 0.752 | 0.234 | finite DOE cut boundaries |
| 1050 degF+ | 11.56 | 1.0089 | 1.334 | 0.501 | lower boundary only; DOE Watson factor 11.7 |

DOE leaves sulfur blank for the gas cut and nitrogen blank through the 250-375 degF cut. The zeros
above are the same explicit screening assumptions used by the independently qualified sulfur and
nitrogen bookkeeping tests; they are not reported measurements.

## Light-end allocation assumption

The PIANO debutanization table reports ethane 0.09, propane 10.38, i-butane 10.21, and n-butane
45.95 wt%, totaling the reported 66.63 wt% C2-C4 subset. The reference factory normalizes those four
weights and allocates the full 1.70 mass% gas cut between them. This preserves the published gas-cut
mass while supplying real NeqSim standard components, but it is not a measured complete gas analysis.

## Usage

```java
SystemInterface crude = new SystemSrkEos(298.15, 1.01325);
OilAssayCharacterisation assay = DoeBigHillSweetAssay.create(crude, 1.0); // kg

// Inspect mass fractions, sulfur/nitrogen, and boundaries before mutation.
double sulfurMassPercent = assay.getBulkSulfurMassPercent();
assay.apply();
```

The one-kilogram regression requires 12 positive components (four standard light molecules and eight
petroleum pseudo-components), reconstructed component mass closure within `1e-10 kg`, sulfur
0.40867518 mass%, and nitrogen 0.1095129 mass%. A second construction must produce the identical
resolved mass vector.

## Evidence and validity boundary

This class is a source-specific reproducible reference composition. It does not mix the older 1998
five-cut table with the 2021 nine-cut workbook. It also does not validate the normalized gas allocation,
generated critical properties, acentric factors, vapor-liquid equilibrium, atmospheric product yields,
or conversion-unit performance. DOE publishes the workbook for information purposes with no warranty
of accuracy or completeness; users remain responsible for its application.

The next validation gate is to run this complete slate through the atmospheric-column workflow and
compare product yields and boiling ranges against independent public evidence before advancing to
vacuum fractionation.
