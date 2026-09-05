---
title: "Sarir atmospheric validation reference"
description: "Public whole-crude properties, TBP, atmospheric-unit operating data, and independent plant-yield evidence."
---

# Sarir atmospheric validation reference

`SarirAtmosphericReference` exposes a public refinery case to Java and Python/JPype callers while preserving the distinction between measured evidence, simulated results, and missing source data.

## Source and license

The source is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using Aspen HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51-64, published 31 March 2022, DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The [open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY 4.0.

The paper reports a Sarir crude density of 841.5 kg/m3 at 15 degC, API gravity 36.5 at 60 degF, sulfur 0.120 mass%, average molar mass 0.2447 kg/mol, and a public TBP assay originally prepared by the Libyan Petroleum Institute.

## Whole-crude property evidence

Table 1 gives the following numeric bulk measurements:

| Property | Published value | Basis |
| --- | ---: | --- |
| Density | 841.5 kg/m3 | 15 degC |
| API gravity | 36.5 | 60 degF |
| Total sulfur | 0.120 mass% | Whole crude |
| Asphaltenes | 0.20 mass% | Whole crude |
| Mercaptan sulfur | 8 ppm by mass | Whole crude |
| Water and sediment | 0.05 volume% | Whole crude |
| Cloud point | 48.7-49.6 degC | Published interval |
| Pour point | +21 degC | Whole crude |
| Kinematic viscosity | 10.63 cSt | 100 degF; the source prose gives the rounded equivalent 37.7 degC |
| Average molar mass | 0.2447 kg/mol | Whole crude |

The Java API keeps the cloud point as separate lower and upper endpoints. It also exposes both
published viscosity reference-temperature forms: 100 degF from Table 1 and 37.7 degC from the
source prose. An exact unit conversion gives 37.777777... degC; the API does not silently replace
the reported rounded Celsius value.

These are whole-crude reference measurements. The source does not publish uncertainty bounds or
per-cut allocations for these properties, so they are not distributed across pseudo-components and
do not qualify a wax, asphaltene, viscosity, water, or sulfur prediction model.

## Preserved TBP evidence

| TBP temperature (degC) | 70 | 90 | 110 | 150 | 195 | 215 | 255 | 275 | 295 | 335 | 370 | 400 | 460 | 480 | 500 | 520 | 550 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Cumulative liquid volume (%) | 7.44 | 10.47 | 13.83 | 21.16 | 28.52 | 31.54 | 38.03 | 41.76 | 44.68 | 51.97 | 59.19 | 63.50 | 72.52 | 75.61 | 78.66 | 81.05 | 83.70 |

The source then reports a `550 degC+` terminal residue reaching 100 volume%. NeqSim retains 550 degC as a one-sided lower boundary and computes the implied 16.30 volume% residue; it does not invent a finite 100% endpoint or an upper boiling limit. The source marks light-end hydrocarbons as not determined, so no light-end composition is synthesized.

## Atmospheric operating case

The published HYSYS case uses 34 valve trays and feeds crude to tray 31 counted from the top.
Table 3 publishes the complete ADU inlet/outlet stream set:

| Source label | Direction | Temperature (degC) | Pressure (kPa) | Mass flow (kg/h) |
| --- | --- | ---: | ---: | ---: |
| Crude oil tower | Inlet | 350 | 233 | 54,420 |
| Steam | Inlet | 150 | 476 | 340.2 |
| Kerosene steam | Inlet | 150 | 476 | 68.04 |
| Diesel steam | Inlet | 150 | 476 | 226.8 |
| Gas To Flare | Outlet | 49 | 140 | 6.985e-6 |
| Naphtha | Outlet | 49 | 140 | 8,706 |
| Kerosene product | Outlet | 126.3 | 210 | 952.2 |
| Diesel product | Outlet | 214.8 | 219.1 | 17,709.24 |
| Residual | Outlet | 341.9 | 230 | 26,937.99 |
| Water draw | Outlet | 49 | 140 | 745.5 |

The four published inlet flows sum to 55,055.04 kg/h and the six outlet flows sum to
55,050.930006985 kg/h. Their 4.109993015 kg/h difference is less than `1e-4` of the inlet flow.
This is a transcription and source-rounding check, not an independent plant conservation claim.

The three steam rows total 635.04 kg/h. Table 3 explicitly reports 150 degC and 476 kPa for each
steam service, correcting the earlier incomplete guide statement. The source does not report steam
quality, enthalpy, or injection tray locations, so temperature and pressure alone are not treated as
a complete thermodynamic state. The API exposes the rows as evidence and does not create executable
water streams or side-stripper topology.

Table 4 gives the complete numeric pump-around rows:

| Source label | Draw tray | Return tray | Flow (kg/h) | Draw temperature (degC) | Return temperature (degC) | Derived drop (K) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Top pump around (TPA) | 3 | 1 | 29,777.64 | 143.9 | 80.99 | 62.91 |
| Bottom pump around (BPA) | 22 | 19 | 60,423.66 | 232.4 | 173.99 | 58.41 |

The temperature drops are direct differences of the published draw and return temperatures. Table 4 does not explicitly state whether its tray labels are counted from the top or bottom. The Java API therefore exposes them as raw source tray numbers and reports the numbering basis as unresolved. They are not automatically converted to NeqSim's bottom-up indices or used to configure a column.

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

double cloudPointLowerCelsius = SarirAtmosphericReference.getCrudeCloudPointLowerCelsius();
double cloudPointUpperCelsius = SarirAtmosphericReference.getCrudeCloudPointUpperCelsius();
double viscosityCst = SarirAtmosphericReference.getCrudeKinematicViscosityAt100FCst();

SarirAtmosphericReference.AduStreamReference crudeFeed =
    SarirAtmosphericReference.getAduStream("Crude oil tower");
double feedRateKgPerHour = crudeFeed.getMassFlowRateKgPerHour();
double tableClosure =
    SarirAtmosphericReference.calculatePublishedAduMassBalanceErrorFraction();

SarirAtmosphericReference.ProductYieldReference diesel =
    SarirAtmosphericReference.getProductYield("Diesel");
double plantRate = diesel.getPlantMetricTonPerDay();
double errorPercent = diesel.getAbsoluteRelativeErrorPercent();

SarirAtmosphericReference.PumparoundReference topPumparound =
    SarirAtmosphericReference.getPumparound("Top pump around (TPA)");
double sourceFlowKgPerHour = topPumparound.getMassFlowRateKgPerHour();
int rawSourceDrawTray = topPumparound.getSourceDrawTrayNumber();
boolean trayBasisIsExplicit =
    SarirAtmosphericReference.hasExplicitPumparoundTrayNumberingBasis();

SarirAtmosphericReference.SteamInjectionReference keroseneSteam =
    SarirAtmosphericReference.getSteamInjection("Kerosene side stripper");
double steamRateKgPerHour = keroseneSteam.getMassFlowRateKgPerHour();
double totalSteamRateKgPerHour = SarirAtmosphericReference.getTotalSteamRateKgPerHour();
boolean steamTemperatureAndPressureAreExplicit =
    SarirAtmosphericReference.hasExplicitSteamTemperatureAndPressure();
boolean steamQualityIsExplicit = SarirAtmosphericReference.hasExplicitSteamQuality();
boolean steamStateIsExplicit =
    SarirAtmosphericReference.hasExplicitSteamThermodynamicState();
```

Static methods are directly accessible through JPype. Arrays are defensive copies, and unknown product labels or invalid error inputs fail closed.

## Constrained pseudo-component input

`SarirAtmosphericAssay` converts the published cumulative TBP coordinates into 18 liquid-volume
cuts: a 7.44 vol% `70 degC-` cut, 16 bounded intervals, and the 16.30 vol% `550 degC+`
residue. The first and last cuts remain one-sided; the factory does not invent a numeric
initial boiling point, residue endpoint, light-end composition, or per-cut property.

The article reports only whole-crude density and average molar mass. Callers must therefore
supply 18 cut specific gravities and 18 cut molar masses. Before changing the attached assay,
the factory requires the volume-weighted density profile to reproduce 841.5 kg/m3 within
1.0 kg/m3 and the mass-weighted number-average molar mass to reproduce 0.2447 kg/mol within
0.001 kg/mol. These tolerances are deterministic model-consistency gates, not estimates of
experimental uncertainty.

```java
double[] cutSpecificGravity = engineeringDensityProfile;
double[] cutMolarMassKgPerMol = engineeringMolarMassProfile;

OilAssayCharacterisation assay = SarirAtmosphericAssay.create(
    system, 1000.0, cutSpecificGravity, cutMolarMassKgPerMol);
assay.apply();
```

`calculateBulkSpecificGravity(...)`, `calculateBulkMolarMassKgPerMol(...)`, and
`getCutVolumePercent()` are available to Java and Python/JPype callers for preflight and
traceability. Profile validation completes before existing assay data or thermodynamic
components are modified.

## Scientific boundary

The source-derived volumes and boiling boundaries are reproducible, while every supplied cut
density and molar mass remains an explicit engineering input. The factory does not resolve the
missing light-end composition, distribute the published whole-crude sulfur among cuts, or claim
that NeqSim reproduces the plant yields. A follow-on 34-tray comparison must preserve the plant
rates as untouched acceptance targets and avoid tuning draw rates to the published products.

## 34-tray property-profile sensitivity gate

`SarirAtmosphericFractionationSensitivityTest` carries the constrained assay through 34 NeqSim
simple trays, matching the published valve-tray count. The source feed location, tray 31 counted
from the top, maps to NeqSim internal index 4 because NeqSim numbers simple trays from the bottom
and reserves index 0 for the reboiler. The test also uses the published feed rate, temperature, and
pressure.

Two separate cut-density and molar-mass profiles are exercised. Both profiles are engineering
inputs because the article does not publish cut properties, and both independently reproduce the
published 841.5 kg/m3 bulk density and 0.2447 kg/mol average molar mass before the assay is applied.
The bounded perturbation demonstrates that the unreported profile changes the calculated split or
product boiling metrics rather than being numerically inert.

The top pressure, condenser/reboiler settings, reflux ratio, side-draw trays, and side-draw
fractions are fixed engineering test controls. They are not values reported by the article. The
qualification uses the repository's MESH-residual refinery-audit solver with a 0.20 K
temperature tolerance and requires rigorous convergence without fallback, external and
per-component conservation, energy and per-tray material closure, finite non-negative
candidate-stream rates, at least two material products, and increasing mean normal boiling point
across material products in column order. A zero-flow candidate is permitted and excluded from composition and boiling-order
evaluation because the source reports light ends as not determined; the test does not synthesize
inventory merely to force every screen stream positive.

The published plant rates remain untouched read-only evidence. They are not used as product-flow
specifications, tuning targets, or numerical acceptance thresholds. This sensitivity gate does not
qualify plant-yield or D86 reproduction and does not model the published steam, pump-around,
side-stripper, tray-hydraulic, or efficiency details. The separate pump-around reference rows are
source evidence only; unresolved tray-numbering direction prevents direct model configuration.
Likewise, the steam rows retain the published service allocation, rates, temperatures, and
pressures but do not infer quality or enthalpy or configure water, injection locations, side-stripper
topology, or heat duties.
