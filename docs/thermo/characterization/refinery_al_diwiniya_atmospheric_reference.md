---
title: "Al-Diwiniya atmospheric operating reference"
description: "Public partial-TBP and atmospheric crude-unit operating evidence, with explicit validation limits."
---

# Al-Diwiniya atmospheric operating reference

`AlDiwiniyaAtmosphericReference` makes a public atmospheric crude-unit case available to Java and Python/JPype callers without turning missing source data into model inputs.

## Source and license

The source is Ahmed Qasim, Hyfaa Yousif, and Nazar Qasim, *Simulation of Atmospheric Distillation Unit for AL-Diwiniya Crude Oil Refinery by Using Aspen Hysys*, Journal of Petroleum Research and Studies 15(3), 85-97, published 21 September 2025, DOI [10.52716/jprs.v15i3.965](https://doi.org/10.52716/jprs.v15i3.965). The [open-access article](https://jprs.gov.iq/index.php/jprs/article/download/965/614/5888) is licensed CC BY 4.0.

The case describes a 10,000 barrel/day Al-Diwiniya refinery atmospheric unit processing 66 m3/h of 2021 Basra medium crude. The reported crude has API gravity 29.8 at 15 degC, density 876 kg/m3 at an unstated reference temperature, sulfur 3 mass%, salt 159 ppm, water and bottom sediment 0.15 vol%, and kinematic viscosity 12.7 cSt at 20 degC.

## Preserved evidence

The reference preserves all 17 published crude TBP coordinates:

| Cumulative liquid volume (%) | 2 | 3.5 | 5 | 7.5 | 10 | 12.5 | 15 | 17.5 | 20 | 25 | 30 | 35 | 40 | 45 | 50 | 55 | 60 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TBP temperature (degC) | 40 | 52 | 62 | 77 | 95 | 112 | 128 | 143 | 159 | 189 | 218 | 249 | 279 | 310 | 342 | 373 | 405 |

This is explicitly a **partial curve**. NeqSim does not create synthetic 0 or 100 vol% endpoints, interpolate the unreported 60-100 vol% residue, or assign one bulk density to all TBP intervals.

The operating reference also records:

- crude preheat from 25 to 150 degC and furnace heating to a 300 degC column feed;
- 29 stages, with the feed flash zone between trays 3 and 4;
- 1.5 barg feed pressure, 0.75 barg top pressure, and 1.2 barg bottom pressure;
- heavy-naphtha draws at trays 24 and 22, kerosene at tray 15, and gasoil at tray 9;
- a 3 m3/h gasoil pump-around returned at 60 degC;
- four-stage kerosene and gasoil side strippers using 75 and 125 kg/h steam;
- 300 kg/h bottom steam at 220 degC and 5 barg.

## Product operating rows

| Product | Plant rate (m3/h) | HYSYS rate (m3/h) | Plant draw (degC) | HYSYS draw (degC) |
| --- | ---: | ---: | ---: | ---: |
| Light naphtha | 8 | 8 | 110 | 109 |
| Total heavy naphtha | 2 | 2 | 135 | 140 |
| Kerosene | 4 | 4 | 180 | 190 |
| Gasoil | 10 | 10 | 240 | 235 |
| Atmospheric residue | 41 | 41.25 | 295 | 295 |
| Off gas | 1 | 0.75 | 60 | 65 |

The source uses signed relative error `100 * (plant - HYSYS) / plant`. `ProductReference` exposes the raw values and recomputes that convention. The five measured hydrocarbon-liquid product rates total 65 m3/h, or 98.4848% of the 66 m3/h crude feed. Off-gas and the separately reported 0.4 m3/h water stream are retained but excluded from this liquid-volume closure because the source does not state compatible reference conditions.

## Java and Python access

```java
double[] volumePercent = AlDiwiniyaAtmosphericReference.getTbpCumulativeVolumePercent();
double[] temperatureCelsius = AlDiwiniyaAtmosphericReference.getTbpTemperatureCelsius();

AlDiwiniyaAtmosphericReference.ProductReference kerosene =
    AlDiwiniyaAtmosphericReference.getProduct("Kerosene");
double plantDrawCelsius = kerosene.getPlantDrawTemperatureCelsius();
double signedErrorPercent = kerosene.getDrawTemperatureRelativeErrorPercent();
```

The static methods are directly accessible through JPype. Returned arrays are defensive copies; changing a Python or Java copy cannot mutate the frozen source data.

## Scientific boundary

The paper states that all HYSYS product flow rates were fixed. Equality of light-naphtha, heavy-naphtha, kerosene, and gasoil rates is therefore **not independent product-yield validation**. Product draw temperatures were calculated and can support a future operating-envelope comparison, but the source does not publish the cut-density curve, molecular-weight curve, complete crude TBP tail, numeric product ASTM D86 tables, reflux rate, or all heat duties needed for an independently reproducible 29-stage crude-column model.

This increment qualifies public data retention, units, error conventions, and evidence classification. It does not claim to validate NeqSim product yields, pseudo-component properties, or the Al-Diwiniya column. A future process-model comparison must add sufficient public assay and product-quality data without tuning to imposed rates.
