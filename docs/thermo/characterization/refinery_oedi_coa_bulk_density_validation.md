---
title: "DOE/OEDI COA bulk density and API qualification"
description: "Public-data matrix qualification of whole-assay specific gravity and API reconstruction across every complete four-category COA row."
---

# DOE/OEDI COA bulk density and API qualification

This qualification advances refinery issue #3305 from one selected crude to the complete usable matrix in the public DOE/OEDI summary workbook. It tests whether `OilAssayCharacterisation` reconstructs bulk specific gravity and API gravity from complete liquid-volume-basis category tables without inventing terminal boiling properties or mutating the thermodynamic system.

## Public source, license, and method

The source is the U.S. Department of Energy/National Renewable Energy Laboratory **Crude Oil Analysis (COA) Database**, distributed through the Open Energy Data Initiative (OEDI):

- dataset catalog and provenance: <https://catalog.data.gov/dataset/crude-oil-analysis-coa-database>
- frozen archive: <https://data.openei.org/files/178/coa.zip>
- license: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)

The archive contains `Summary of Analyses.xls` and `COAMDATA_DESC.pdf`. The database compiles 9,076 crude-oil analyses made by the U.S. Bureau of Mines and later maintained by DOE. The method description records standardized atmospheric and 40 mm Hg distillation, fraction-volume and gravity measurements, and refinery category aggregation.

The regression selects **all** workbook rows that satisfy these predeclared completeness rules:

1. gasoline+naphtha, kerosene, gas-oil and residuum yields sum to 100.0 vol%;
2. every positive-yield category has a reported specific gravity;
3. nonviscous, medium and viscous lubricating-distillate yields are zero;
4. the row reports whole-crude specific gravity and API gravity.

Exactly five rows qualify. No row is removed because its reconstructed error is unfavorable.

## Frozen qualification matrix

| Sample | Location/field | Gasoline+naphtha vol% / SG | Kerosene vol% / SG | Gas oil vol% / SG | Residuum vol% / SG | Published crude SG / API | Reconstructed SG / API | Absolute SG / API error |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 920 | Turner Valley, Alberta | 70.5 / 0.754 | 13.2 / 0.813 | 3.7 / 0.831 | 12.6 / 0.889 | 0.779 / 50.1 | 0.781647 / 49.528 | 0.002647 / 0.572 |
| 50146 | Ranch W, Texas | 17.8 / 0.794 | 0.0 / — | 78.7 / 0.856 | 3.5 / 0.907 | 0.847 / 35.6 | 0.846749 / 35.610 | 0.000251 / 0.010 |
| 56337 | Manderson, Wyoming | 88.7 / 0.767 | 8.7 / 0.794 | 0.0 / — | 2.6 / 0.815 | 0.771 / 52.0 | 0.770597 / 52.124 | 0.000403 / 0.124 |
| 60205 | South McCallum, Colorado | 75.0 / 0.741 | 19.8 / 0.804 | 4.2 / 0.842 | 1.0 / 0.873 | 0.765 / 53.5 | 0.759036 / 54.921 | 0.005964 / 1.421 |
| 68120 | Vermilion Block 14, Louisiana | 49.7 / 0.749 | 38.5 / 0.805 | 0.0 / — | 11.8 / 0.832 | 0.782 / 49.4 | 0.780354 / 49.828 | 0.001646 / 0.428 |

Published category and crude values are reproduced at their source precision. A zero-yield category is omitted from the Java assay rather than assigned a fictitious density.

## Calculation and acceptance

For resolved mass fractions `w_i` and cut specific gravities `SG_i`, NeqSim applies ideal additive liquid volumes:

$$SG_{bulk}=\left(\sum_i\frac{w_i}{SG_i}\right)^{-1}$$

For a normalized liquid-volume-basis table this reduces to:

$$SG_{bulk}=\sum_i v_iSG_i$$

The corresponding API gravity is:

$$API=\frac{141.5}{SG_{bulk}}-131.5$$

The regression requires:

- exact 100.0 vol% source-yield closure;
- exact reproduction of the frozen additive-volume arithmetic to `1e-12`;
- finite positive SG and finite API outputs;
- identical results after reversing category order;
- no thermodynamic components added or modified;
- per-row absolute error no greater than **0.006 SG** and **1.5 degrees API**.

Across all five qualifying rows, the observed SG absolute-error maximum/mean/RMSE is **0.005964 / 0.002182 / 0.003017**. The API absolute-error maximum/mean/RMSE is **1.420670 / 0.510845 / 0.713312 degrees API**. These errors are frozen as evidence and are not tuned away.

## Maturity, validity, and stop boundary

Within the frozen COA matrix, the ideal-additive-volume behavior is **qualified for assay screening** over published whole-crude SG 0.765–0.847. This is not a blend-density standard, custody-transfer calculation or design certification.

The calculation uses the density reference condition represented by the source table. It does not model temperature or pressure correction, excess volume, blend contraction, sulfur/heteroatom effects, or uncertainty correlations between reported fields. The largest error is retained and limits the stated accuracy.

The matrix intentionally does not create pseudo-components because the summary categories do not provide finite representative boiling points and molar masses for all terminal categories. This increment adds no production formula, coefficient tuning, terminal-cut extrapolation, TBP/ASTM conversion, column change, JSON/MCP schema, notebook, vacuum model, blending optimizer or conversion-unit model.

Python uses the same authoritative Java methods through the normal NeqSim JVM gateway; no separate Python property equation is maintained.

Physical density at 60 degF is available separately through `getBulkDensityKgPerCubicMetreAt60F()`, using 999.016 kg/m3 for water. API-gravity inputs remain dimensionless SG60/60 values and are not pre-multiplied by water density; this preserves exact API-to-SG round-tripping while keeping physical-density units explicit.
