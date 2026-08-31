---
title: "DOE Big Hill assay nitrogen qualification"
description: "Public-data qualification of linear refinery-assay total-nitrogen bookkeeping."
---

# DOE Big Hill assay nitrogen qualification

This benchmark qualifies assay-carried total-nitrogen inputs and bulk nitrogen reconstruction against the public U.S. Department of Energy Strategic Petroleum Reserve Big Hill Sweet assay.

## Source and provenance

The source is the DOE/SPR **Big Hill Sweet** comprehensive assay workbook:

- workbook: <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- DOE assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in the workbook: **24 September 2021**

DOE/SPR publicly distributes the workbook with an informational-use disclaimer. No separate license statement was found. The regression freezes only attributed mass yields, total-nitrogen values and the reported whole-crude result; it does not redistribute the workbook or reproduce a proprietary standard.

## Calculation

For resolved assay mass fractions $w_i$ and cut total-nitrogen mass fractions $N_i$, NeqSim uses the explicit linear mass-basis rule:

$$N_{bulk}=\sum_i w_iN_i$$

Mass- and liquid-volume-basis assays share the existing authoritative basis resolution. Volume yields are converted with cut specific gravities before nitrogen is aggregated. Every positive-yield cut must carry nitrogen data; otherwise the calculation fails closed.

## Frozen DOE matrix

| Non-overlapping cut | Mass yield, % | Total nitrogen, mass % |
| --- | ---: | ---: |
| C2-C4 gas | 1.70 | 0.0 assumed |
| C5-175 degF | 5.22 | 0.0 assumed |
| 175-250 degF | 8.32 | 0.0 assumed |
| 250-375 degF | 12.55 | 0.0 assumed |
| 375-530 degF | 16.19 | 0.0018 |
| 530-650 degF | 13.18 | 0.0186 |
| 650-850 degF | 18.44 | 0.102 |
| 850-1050 degF | 12.84 | 0.234 |
| 1050 degF+ | 11.56 | 0.501 |

The source does not report total nitrogen for the gas through 250-375 degF cuts. The benchmark assigns zero to those four light cuts as an explicit screening assumption; those values are not presented as measured DOE data. With that assumption, the reconstructed result is **0.1095129 mass%**, compared with DOE's whole-crude **0.11 mass%**. The absolute difference is **0.0004871 mass%**, below the predeclared **0.001 mass%** source-precision gate.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillNitrogenTest` requires:

- exact mass-yield closure and analytical mass-weighted nitrogen arithmetic;
- agreement with DOE whole-crude nitrogen within 0.001 mass%;
- fraction and mass-percent inputs to agree to numerical precision;
- input-order independence;
- a volume-basis analytical control using density-resolved mass fractions;
- missing nitrogen for a positive-yield cut to fail closed;
- valid nitrogen inputs to remain between zero and one; and
- property queries not to add or modify thermodynamic components.

The capability is **qualified for linear assay nitrogen bookkeeping** over this frozen public matrix. The calculation is O(cuts), non-iterative and does not alter thermodynamic or process calculations.

## Stop boundary

This evidence does not create elemental thermodynamic components, propagate nitrogen into pseudo-components, predict nitrogen species, perform hydrotreating chemistry, estimate emissions, apply nonlinear blending, certify a product specification or qualify a conversion unit. Those require separate #3305 contracts and validation.
