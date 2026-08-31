---
title: "DOE Big Hill assay sulfur qualification"
description: "Public-data qualification of linear refinery-assay total-sulfur bookkeeping."
---

# DOE Big Hill assay sulfur qualification

This benchmark qualifies assay-carried total-sulfur inputs and bulk sulfur reconstruction against the public U.S. Department of Energy Strategic Petroleum Reserve Big Hill Sweet assay.

## Source and provenance

The source is the DOE/SPR **Big Hill Sweet** comprehensive assay workbook:

- workbook: <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- DOE assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in the workbook: **24 September 2021**

DOE/SPR publicly distributes the workbook with an informational-use disclaimer. No separate license statement was found. The regression freezes only attributed mass yields, total-sulfur values and the reported whole-crude result; it does not redistribute the workbook or reproduce a proprietary standard.

## Calculation

For resolved assay mass fractions $w_i$ and cut total-sulfur mass fractions $S_i$, NeqSim uses the explicit linear mass-basis rule:

$$S_{bulk}=\sum_i w_iS_i$$

Mass- and liquid-volume-basis assays share the existing authoritative basis resolution. Volume yields are converted with cut specific gravities before sulfur is aggregated. Every positive-yield cut must carry sulfur data; otherwise the calculation fails closed.

## Frozen DOE matrix

| Non-overlapping cut | Mass yield, % | Total sulfur, mass % |
| --- | ---: | ---: |
| C2-C4 gas | 1.70 | 0.0 assumed |
| C5-175 degF | 5.22 | 0.0008 |
| 175-250 degF | 8.32 | 0.0026 |
| 250-375 degF | 12.55 | 0.019 |
| 375-530 degF | 16.19 | 0.096 |
| 530-650 degF | 13.18 | 0.313 |
| 650-850 degF | 18.44 | 0.534 |
| 850-1050 degF | 12.84 | 0.752 |
| 1050 degF+ | 11.56 | 1.334 |

The source does not report total sulfur for the C2-C4 gas cut. The benchmark assigns zero to that 1.70 mass% cut as an explicit screening assumption; it is not presented as a measured DOE value. With that assumption, the reconstructed result is **0.40867518 mass%**, compared with DOE's whole-crude **0.409 mass%**. The absolute difference is **0.00032482 mass%**, below the predeclared **0.001 mass%** source-precision gate.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillSulfurTest` requires:

- exact mass-yield closure and analytical mass-weighted sulfur arithmetic;
- agreement with DOE whole-crude sulfur within 0.001 mass%;
- fraction and mass-percent inputs to agree to numerical precision;
- input-order independence;
- a volume-basis analytical control using density-resolved mass fractions;
- missing sulfur for a positive-yield cut to fail closed;
- valid sulfur inputs to remain between zero and one; and
- property queries not to add or modify thermodynamic components.

The capability is **qualified for linear assay sulfur bookkeeping** over this frozen public matrix. The calculation is O(cuts), non-iterative and does not alter thermodynamic or process calculations.

## Stop boundary

This evidence does not create elemental thermodynamic components, propagate sulfur into pseudo-components, predict sulfur species, perform hydrotreating chemistry, estimate emissions, apply nonlinear blending, certify a product specification or qualify a conversion unit. Those require separate #3305 contracts and validation.
