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

## Product specifications

Table 2 reports ASTM D86 temperatures at 95 liquid volume percent as product specifications:

| Product | Test basis | Published specification |
| --- | --- | ---: |
| Light Naphtha | ASTM D86 at 95 volume% | 90 degC |
| Heavy Naphtha | ASTM D86 at 95 volume% | 160 degC |
| Kerosene | ASTM D86 at 95 volume% | 221 degC |
| Diesel | ASTM D86 at 95 volume% | 327 degC |
| Residual | ASTM D86 at 95 volume% | `<550+` |

The Java API keeps these five specification rows separate from the Table 5 laboratory/HYSYS
results. In particular, the 327 degC diesel specification is not replaced by the 346 degC
laboratory result or the 339 degC HYSYS result. The residual value is retained as the source's
nonnumeric, open-ended boundary; requesting a numeric specification temperature for that row
fails closed. These rows are source design/reference criteria, not independently reproduced
measurements and not evidence that NeqSim meets the specifications.

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


### Strict NeqSim T95 comparison

A calculated product standard can be compared with the three published Sarir T95 references
through the strict 95 liquid-volume-percent Riazi-Daubert path:

```java
Standard_ASTM_D86 d86 = new Standard_ASTM_D86(productFluid);
d86.calculate();

SarirD86ProductComparison.Result comparison =
    SarirD86ProductComparison.compareT95(d86, "Diesel");
double neqsimT95C = comparison.getNeqsimT95Celsius();
double laboratoryErrorPercent =
    comparison.getNeqsimAbsoluteRelativeErrorPercent();
double specificationMarginC = comparison.getSpecificationMarginCelsius();
```

The workflow delegates the NeqSim value to
`Standard_ASTM_D86.getQualifiedD86Temperature(95.0)`. It retains the laboratory, HYSYS, and
numeric specification values as read-only comparison evidence. Neither the source HYSYS value nor
the specification is a tuning target, and no laboratory-agreement threshold is imposed.

Sarir T5 values are not converted because 5 volume% is outside the qualified Riazi-Daubert
recovery set. The residual `550+` specification also remains nonnumeric and fails closed. A
specification margin is only arithmetic against the published reference; it is not an ASTM
procedure or compliance determination.

## Independent product-rate evidence

| Product | Plant (metric t/day) | HYSYS (metric t/day) | Absolute error (%) |
| --- | ---: | ---: | ---: |
| Total naphtha | 208.95 | 208.2 | 0.359 |
| Kerosene | 22.85 | 20.0 | 12.473 |
| Diesel | 425.018 | 393.0 | 7.533 |
| Residual | 646.5 | 706.1 | 9.219 |

Unlike the Al-Diwiniya reference, the Sarir paper does not say these HYSYS rates were
imposed. It describes calculated production rates compared with actual refinery data, so the
rows are retained as independent validation targets. The API recomputes absolute relative
errors from the raw published values instead of copying rounded percentages.

The conversion to kg/h uses exactly 1000 kg per metric tonne and 24 hours per day. Each Table 5
plant row is also bound to its matching Table 3 ADU hydrocarbon outlet:

| Product | Table 3 outlet | Plant (kg/h) | Table 3 (kg/h) |
| --- | --- | ---: | ---: |
| Total naphtha | Naphtha | 8706.25 | 8706.00 |
| Kerosene | Kerosene product | 952.083333 | 952.20 |
| Diesel | Diesel product | 17709.083333 | 17709.24 |
| Residual | Residual | 26937.50 | 26937.99 |
| **Total** | | **54304.916667** | **54305.43** |

The absolute difference between those independently published totals is 0.513333 kg/h, less
than (10^{-5}) of the plant total. This is a transcription and unit-basis reconciliation
between source tables, not proof of plant mass conservation or model agreement. The gas-to-flare,
water, and steam rows are therefore excluded from this hydrocarbon-product comparison.

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

SarirAtmosphericReference.ProductSpecificationReference dieselSpecification =
    SarirAtmosphericReference.getProductSpecification("Diesel");
double dieselSpecificationCelsius =
    dieselSpecification.getSpecificationTemperatureCelsius();

SarirAtmosphericReference.ProductSpecificationReference residualSpecification =
    SarirAtmosphericReference.getProductSpecification("Residual");
boolean residualSpecificationIsNumeric =
    residualSpecification.hasNumericSpecificationTemperature();

SarirAtmosphericReference.ProductYieldReference diesel =
    SarirAtmosphericReference.getProductYield("Diesel");
double plantRateKgPerHour = diesel.getPlantMassFlowRateKgPerHour();
double hysysRateKgPerHour = diesel.getSimulationMassFlowRateKgPerHour();
String tableThreeOutlet = diesel.getAduStreamName();
double calculatedErrorPercent =
    diesel.calculateAbsoluteRelativeErrorPercentForMassFlowKgPerHour(
        calculatedDieselRateKgPerHour);

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

Static methods are directly accessible through JPype. Arrays are defensive copies, and unknown
product labels, negative calculated rates, and non-finite error inputs fail closed. A zero
calculated rate remains a valid comparison value. Plant and HYSYS rates are read-only evidence:
the comparison API neither changes a stream nor imposes an accuracy acceptance threshold.

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

## Reusable 34-tray case factory

`SarirAtmosphericFractionationCase.create(...)` promotes the qualified sensitivity setup into a
reusable Java and Python/JPype entry point. It returns the configured crude feed and an unsolved
`DistillationColumn`, allowing callers to execute, inspect, or extend the case without copying the
source-to-model tray mapping.

The factory reads only the published 34-valve-tray count, tray-31-from-the-top feed location,
54,420 kg/h crude rate, 350 degC feed temperature, and 233 kPa absolute feed pressure from
`SarirAtmosphericReference`. The source tray maps to NeqSim simple-tray index 4 because the
library numbers simple trays from the bottom and reserves index 0 for the reboiler.

Every unreported property or operating choice remains explicit. Callers must supply 18 cut specific
gravities, 18 cut molar masses, top and bottom pressures, reboiler temperature, condenser reflux
ratio, and both liquid side-draw tray indices and fractions. The operating-input object rejects
non-finite pressures, temperatures, reflux, or fractions; an inverted pressure profile; fractions
outside [0, 1); the feed tray as a side draw; and a kerosene screen draw at or below the heavier
diesel screen draw. The existing `SarirAtmosphericAssay` bulk-density and average-molar-mass gates
remain authoritative for the property profiles.

The returned column uses the qualified MESH-residual numerical controls, but it is intentionally
unsolved. Steam services, side strippers, pump-arounds, tray efficiencies, and source product rates
are not configured. In particular, the factory does not tune either side-draw fraction to the
published plant or HYSYS yields, and creating or solving the case is not evidence of product-yield
or D86 agreement.

## Solved-case product and balance summary

After the caller runs the configured column, `SarirAtmosphericFractionationResult.evaluate(case)`
creates an immutable Java/JPype summary of the rigorous result. The four rows remain in the
established top-to-bottom comparison order: Total Naphtha, Kerosene, Diesel, and Residual. Each row
reports calculated mass flow in kg/h, mass fraction of feed, mole-weighted mean normal boiling point,
the independently published plant rate, and the absolute relative rate difference.

The evaluator requires a solved non-fallback MESH-residual column. It rejects non-finite or negative
flows, mass or energy errors above the qualified five-percent integration tolerance, feed/product
mass-closure error above that tolerance, fewer than two material products, and material products
whose mean normal boiling points do not increase from overhead to bottoms. Product arrays are
defensive copies, and exact-label lookup fails closed for unsupported labels.

The calculated mass fractions and mean normal boiling points depend on the caller-supplied cut
properties and unreported column controls. They are model outputs, not source measurements, ASTM
D86 curves, or evidence that NeqSim reproduces the plant. The plant rates remain read-only
comparators: no calculated-versus-plant error is used as a solver control, tuning target, or pass
threshold. Java callers use the same evaluator directly; Python callers access the factory,
`getColumn().run(...)`, and evaluator through the existing JPype bridge.

## Scientific boundary

The source-derived volumes, boiling boundaries, and product-specification rows are reproducible,
while every supplied cut density and molar mass remains an explicit engineering input. Product
specifications are not laboratory results, independent validation targets, or a claim of NeqSim
product compliance. The residual specification remains nonnumeric and open-ended. The factory does not resolve the
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
