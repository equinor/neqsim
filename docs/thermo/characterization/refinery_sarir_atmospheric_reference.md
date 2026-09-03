---
title: "Sarir atmospheric validation reference"
description: "Public complete-range TBP, atmospheric-unit operating data, and independent plant-yield evidence."
---

# Sarir atmospheric validation reference

`SarirAtmosphericReference` exposes a public refinery case to Java and Python/JPype callers while preserving the distinction between measured evidence, simulated results, and missing source data.

## Source and license

The source is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using Aspen HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51-64, published 31 March 2022, DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The [open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY 4.0.

The paper reports a Sarir crude density of 841.5 kg/m3 at 15 degC, API gravity 36.5 at 60 degF, sulfur 0.120 mass%, average molar mass 0.2447 kg/mol, and a public TBP assay originally prepared by the Libyan Petroleum Institute.

## Preserved TBP evidence

| TBP temperature (degC) | 70 | 90 | 110 | 150 | 195 | 215 | 255 | 275 | 295 | 335 | 370 | 400 | 460 | 480 | 500 | 520 | 550 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Cumulative liquid volume (%) | 7.44 | 10.47 | 13.83 | 21.16 | 28.52 | 31.54 | 38.03 | 41.76 | 44.68 | 51.97 | 59.19 | 63.50 | 72.52 | 75.61 | 78.66 | 81.05 | 83.70 |

The source then reports a `550 degC+` terminal residue reaching 100 volume%. NeqSim retains 550 degC as a one-sided lower boundary and computes the implied 16.30 volume% residue; it does not invent a finite 100% endpoint or an upper boiling limit. The source marks light-end hydrocarbons as not determined, so no light-end composition is synthesized.

## Atmospheric operating case

The published HYSYS case uses 34 valve trays, feeds 54,420 kg/h of crude at 350 degC and 233 kPa to tray 31 counted from the top, and includes main-column, kerosene-stripper, and diesel-stripper steam rates of 340.2, 68.04, and 226.8 kg/h. Top and bottom pump-around rates are 29,777.64 and 60,423.66 kg/h.

Four products have numeric laboratory and simulated ASTM D86 T5/T95 evidence:

| Product | Lab T5 (degC) | Lab T95 (degC) | HYSYS T5 (degC) | HYSYS T95 (degC) |
| --- | ---: | ---: | ---: | ---: |
| Light naphtha | 42 | 90 | -9 | 97 |
| Heavy naphtha | 96 | 160 | 83 | 153 |
| Kerosene | 185 | 221 | 159 | 214 |
| Diesel | 262 | 346 | 235 | 339 |

The residual laboratory curve is explicitly excluded from the numeric API because the paper states that it was unavailable and presents a non-numeric `550+` limit.

## Independent product-rate evidence

| Product | Plant (metric t/day) | HYSYS (metric t/day) | Absolute error (%) |
| --- | ---: | ---: | ---: |
| Total naphtha | 208.95 | 208.2 | 0.359 |
| Kerosene | 22.85 | 20.0 | 12.473 |
| Diesel | 425.018 | 393.0 | 7.533 |
| Residual | 646.5 | 706.1 | 9.219 |

Unlike the Al-Diwiniya reference, the Sarir paper does not say these HYSYS rates were imposed. It describes calculated production rates compared with actual refinery data, so the rows are retained as independent validation targets. The API recomputes absolute relative errors from the raw published values instead of copying rounded percentages.

## Java and Python access

```java
double[] volumePercent = SarirAtmosphericReference.getTbpCumulativeVolumePercent();
double residuePercent = SarirAtmosphericReference.getTerminalResidueVolumePercent();

SarirAtmosphericReference.ProductYieldReference diesel =
    SarirAtmosphericReference.getProductYield("Diesel");
double plantRate = diesel.getPlantMetricTonPerDay();
double errorPercent = diesel.getAbsoluteRelativeErrorPercent();
```

Static methods are directly accessible through JPype. Arrays are defensive copies, and unknown product labels or invalid error inputs fail closed.

## Scientific boundary

This increment qualifies source transcription, units, open-ended TBP treatment, and evidence classification. It does not yet construct a Sarir pseudo-component slate or claim that NeqSim reproduces the plant yields. A follow-on process-model comparison must keep the plant rates as untouched acceptance targets, document the missing light-end allocation, and avoid tuning draw rates to the published products.
