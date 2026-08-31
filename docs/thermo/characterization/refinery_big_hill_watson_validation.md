---
title: "DOE Big Hill Watson-factor qualification"
description: "Public-data qualification of per-cut UOP/Watson characterization factors from a DOE Strategic Petroleum Reserve assay."
---

# DOE Big Hill Watson-factor qualification

This benchmark qualifies `AssayCut.getWatsonCharacterizationFactor()` against reported refinery-cut properties in the public U.S. Department of Energy Strategic Petroleum Reserve Big Hill Sweet assay.

## Source and provenance

The source is the DOE/SPR **Big Hill Sweet** comprehensive assay workbook:

- workbook: <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- DOE assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in the workbook: **24 September 2021**

The workbook is publicly distributed by DOE/SPR and carries an informational-use disclaimer. No separate license statement was found. The regression therefore freezes only four attributed numerical UOP K-factor facts and the directly associated cut boundaries and relative densities; it does not redistribute the workbook or reproduce a proprietary standard.

## Calculation

For representative normal boiling point $T_b$ in K and specific gravity $SG$, NeqSim uses the established UOP/Watson relation:

$$K_W=\frac{(1.8T_b)^{1/3}}{SG}$$

This is equivalent to using the boiling point in degrees Rankine. A finite cut interval uses its arithmetic midpoint. API-gravity inputs follow the same dimensionless SG60/60 relation as the rest of `OilAssayCharacterisation`.

## Frozen matrix and observed errors

| DOE cut (degF) | SG60/60 | DOE UOP K | NeqSim midpoint K | Absolute error |
| --- | ---: | ---: | ---: | ---: |
| 375-530 | 0.8297 | 11.7 | 11.688825 | 0.011175 |
| 530-650 | 0.8604 | 11.8 | 11.811830 | 0.011830 |
| 650-850 | 0.9039 | 11.8 | 11.787868 | 0.012132 |
| 850-1050 | 0.9336 | 12.0 | 12.010054 | 0.010054 |

The maximum/mean/RMSE absolute error is **0.012132 / 0.011298 / 0.011326**. No coefficient or datum is tuned.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillWatsonTest` requires:

- all four bounded cuts to agree with DOE within **0.05 K-factor units**, consistent with one-decimal source reporting;
- the observed maximum error to remain frozen at `0.012132370527373482` within numerical precision;
- Kelvin, Celsius, Fahrenheit and finite-range midpoint input paths to agree to `1e-12`;
- exact-SG and equivalent API-gravity inputs to use the same authoritative calculation;
- missing density or representative boiling point to fail closed;
- property queries not to add or modify thermodynamic components.

Within the frozen matrix, the method is **qualified for assay screening** over 375-1050 degF cut boundaries and SG 0.8297-0.9336. It is a constant-time scalar calculation with no iterative solver.

## Stop boundary

This evidence does not qualify whole-crude K-factor aggregation, ASTM D86/D1160-to-TBP conversion, density-temperature correction, blend contraction, molecular-weight estimation, pseudo-component critical properties, vapor-liquid equilibrium, columns, blending, optimization or conversion-unit models. Those remain separate #3305 gates.
