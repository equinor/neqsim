---
title: "DOE/OEDI COA bulk density and API qualification"
description: "Public-data qualification of whole-assay specific gravity and API reconstruction for a complete Bureau of Mines crude cut table."
---

# DOE/OEDI COA bulk density and API qualification

This qualification advances refinery issue #3305 from cut-yield bookkeeping to a first read-only whole-assay property. It tests whether `OilAssayCharacterisation` can reconstruct a crude's bulk specific gravity and API gravity from a complete liquid-volume-basis cut table without first inventing pseudo-component boiling properties.

## Public source, license, and method

The source is the U.S. Department of Energy/National Renewable Energy Laboratory **Crude Oil Analysis (COA) Database**, distributed through the Open Energy Data Initiative (OEDI):

- dataset catalog and provenance: <https://catalog.data.gov/dataset/crude-oil-analysis-coa-database>
- OEDI record and archive: <https://data.openei.org/submissions/178>
- license: [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)

The public database compiles 9,076 crude-oil analyses made by the U.S. Bureau of Mines and later maintained by DOE. The supplied `COAMDATA_DESC.pdf` identifies the standardized refinery categories and reference method. This regression freezes one transparent row from `Summary of Analyses.xls`: sample 920, Turner Valley, Alberta.

| Reported category | Liquid volume % | Specific gravity at 60 degF |
| --- | ---: | ---: |
| Total gasoline and naphtha | 70.5 | 0.754 |
| Kerosene | 13.2 | 0.813 |
| Gas oil | 3.7 | 0.831 |
| Residuum | 12.6 | 0.889 |
| **Total** | **100.0** | — |

The same public row reports whole-crude specific gravity **0.779** and API gravity **50.1 degrees API**.

## Calculation and acceptance

For resolved mass fractions `w_i` and cut specific gravities `SG_i`, NeqSim applies ideal additive liquid volumes:

$$SG_{bulk}=\left(\sum_i\frac{w_i}{SG_i}\right)^{-1}$$

For this liquid-volume-basis table the equation reduces to the normalized volume-weighted value:

$$SG_{bulk}=0.705(0.754)+0.132(0.813)+0.037(0.831)+0.126(0.889)=0.781647$$

The corresponding NeqSim API value is approximately **49.528 degrees API**. The regression accepts:

- source cut-yield closure at 100.0 vol%;
- exact reproduction of the additive-volume arithmetic to `1e-12`;
- absolute whole-crude SG difference no greater than **0.004**;
- whole-crude API difference no greater than **0.7 degrees API**;
- identical bulk SG after reversing input order;
- no added or modified thermodynamic components.

The observed public-source differences are about **0.002647 SG** and **0.572 degrees API**. They are recorded rather than tuned away. The category densities and rounded yields do not encode excess-volume effects or the complete laboratory reconstruction procedure.

## Validity and stop boundary

This is an **experimental screening qualification**, not a blend-density standard or custody-transfer method. It assumes additive liquid volumes at the cut density reference condition. It does not model temperature correction, pressure effects, excess volume, blend contraction, sulfur/heteroatom effects, or uncertainty correlations between reported fields.

The regression intentionally does not create pseudo-components because the COA summary categories do not provide one finite representative boiling point and molar mass for every terminal refinery category. It therefore adds no terminal-cut extrapolation, TBP/ASTM conversion, column tuning, JSON/MCP schema, vacuum fractionation, blending optimizer, or conversion-unit model.

Python uses the same authoritative Java methods through the normal NeqSim JVM gateway; no separate Python property equation is maintained. Promotion from experimental to qualified requires additional independent assay families and an explicit reference-condition/contraction treatment.
