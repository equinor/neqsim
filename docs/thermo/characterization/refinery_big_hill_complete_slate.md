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

## 650 degF+ vacuum-screening feed

`DoeBigHillSweetAssay.createVacuumScreeningFeed(...)` returns a three-cut heavy feed assembled
only from the 650-850 degF, 850-1050 degF, and one-sided 1050 degF+ source rows. Those rows account
for 42.84 mass% of whole crude. The factory preserves their relative masses and normalizes them to
the screening-feed basis:

$$w_i^{screen}=\frac{w_i^{whole\ crude}}{42.84\%}$$

| Source cut | Whole-crude mass % | Screening-feed mass % |
| --- | ---: | ---: |
| 650-850 degF | 18.44 | 43.0438842204 |
| 850-1050 degF | 12.84 | 29.9719887955 |
| 1050 degF+ | 11.56 | 26.9841269841 |

The source-weight accessor returns a defensive copy, while the factory preserves the source
specific gravities, sulfur, nitrogen, finite cut boundaries, and the residue's one-sided lower
boundary and Watson factor. The normalized feed reconstructs 0.815211951447 sulfur mass% and
0.249229691877 nitrogen mass%.

This feed is a transparent characterization and numerical-screening basis. It is not a measured
atmospheric-column bottoms stream: DOE does not report tower entrainment, cut-point overlap, light-tail
carryover, operating pressure, or a matching atmospheric-column material balance. The factory
therefore does not infer any of those quantities and does not perform pressure correction or ASTM
D1160 conversion.

## Evidence and validity boundary

This class is a source-specific reproducible reference composition. It does not mix the older 1998
five-cut table with the 2021 nine-cut workbook. It also does not validate the normalized gas allocation,
generated critical properties, acentric factors, vapor-liquid equilibrium, atmospheric product yields,
or conversion-unit performance. DOE publishes the workbook for information purposes with no warranty
of accuracy or completeness; users remain responsible for its application.

The complete slate has separately passed atmospheric-column integration and the public Sarir case
now provides the campaign's operating-case fractionation evidence. The normalized 650 degF+ slice is
the characterization handoff for a later low-pressure column benchmark; it does not itself validate
vacuum fractionation or product yields.

## Vacuum-column case handoff

`DoeBigHillVacuumFractionationCase.create(...)` turns the normalized screening feed into a
composable Java/JPype process handoff. It applies the three public heavy cuts to an SRK system,
sets the requested mass flow and feed state, and configures a partial-condenser,
reboiler-equipped `DistillationColumn` with the MESH-residual solver.

Every operating value is explicit because the DOE assay does not report a matching refinery
vacuum-column case:

```java
OperatingInputs inputs = new OperatingInputs(
    12,    // simple trays
    4,     // bottom-up feed-tray index
    640.0, // feed temperature, K
    0.12,  // feed pressure, bara
    0.08,  // top pressure, bara
    0.16,  // bottom pressure, bara
    700.0, // reboiler outlet temperature, K
    0.5);  // condenser reflux ratio

DoeBigHillVacuumFractionationCase model =
    DoeBigHillVacuumFractionationCase.create("Big Hill vacuum screen", 1000.0, inputs);
DistillationColumn column = model.getColumn();
```

The factory requires the pressure topology

$$0<P_{top}\leq P_{feed}\leq P_{bottom}<1.01325\;\mathrm{bara}$$

with a strictly positive pressure rise from top to bottom, a valid internal feed tray, and a
reboiler temperature above the feed temperature. Invalid, non-finite, atmospheric, or
non-positive inputs fail before a case is created.

Factory creation runs only the feed flash and deliberately returns an unsolved column. The example
values above are transparent engineering assumptions for API and low-pressure handoff qualification;
they are not DOE measurements or recommended design conditions. A caller must run the column and
independently qualify convergence, mass and energy closure, product ordering, operating sensitivity,
and suitability of the chosen thermodynamic model.

This handoff does not identify the screening feed as measured atmospheric bottoms, perform ASTM D1160
or pressure correction, or claim vacuum-gas-oil/residue yields, product quality, equipment design, or
plant agreement. Those remain separate solved-case and public-benchmark gates.


## Solved screening-point result

After a caller explicitly runs the configured column,
`DoeBigHillVacuumFractionationResult.evaluate(model)` provides a fail-closed Java/JPype summary:

```java
DoeBigHillVacuumFractionationCase model =
    DoeBigHillVacuumFractionationCase.create("Big Hill vacuum screen", 1000.0, inputs);
model.getColumn().run();

DoeBigHillVacuumFractionationResult result =
    DoeBigHillVacuumFractionationResult.evaluate(model);
double overheadMassFraction = result.getProduct("Overhead").getMassFractionOfFeed();
double bottomsMassFraction = result.getProduct("Bottoms").getMassFractionOfFeed();
```

Evaluation accepts only a converged MESH-residual solve without failed or fallback products. The
external mass closure, column mass balance, column energy balance, maximum tray material balance,
final MESH residual, and every component molar balance must each satisfy the configured acceptance
limit. Both overhead and bottoms must have positive material flow, and their mole-weighted mean
normal boiling points must increase from overhead to bottoms. The result also records iteration
count, solve time, and convergence diagnostics, and returns defensive product arrays.

The regression executes the explicit 12-tray point shown above twice from independently constructed
cases. It requires the three exact DOE pseudo-component identities, no more than 5% mass,
energy, tray, or component closure error, and product flow and mean-boiling-point repeatability
within 1%.

These gates qualify numerical conservation and separation direction for one transparent engineering
screening point. No public DOE measurement defines a matching vacuum-column product split, so the
reported streams are deliberately labeled `Overhead` and `Bottoms`, not validated VGO or vacuum
residue. The calculation does not establish pressure correction, ASTM D1160 behavior, equipment
design, a general SRK accuracy envelope, or plant agreement.

## Discrete product boiling-range diagnostics

Each calculated product also exposes the ascending normal-boiling-point support of its positive
pseudo-components and the corresponding normalized cumulative product mole fractions:

```java
ProductResult overhead = result.getProduct("Overhead");
double overheadT10Kelvin = overhead.getNormalBoilingPointQuantileKelvin(0.10);
double overheadT50Kelvin = overhead.getNormalBoilingPointQuantileKelvin(0.50);
double overheadT90Kelvin = overhead.getNormalBoilingPointQuantileKelvin(0.90);
double[] supportKelvin = overhead.getBoilingPointTemperaturesKelvin();
double[] cumulativeMoleFractions = overhead.getCumulativeMoleFractions();
```

For a requested cumulative mole fraction (q) in ((0,1]), the quantile is the first discrete
pseudo-component normal boiling point whose normalized cumulative product mole fraction reaches
(q). The support and cumulative arrays are defensive copies, are ordered by increasing normal
boiling point, and close at a cumulative fraction of one. Invalid or non-finite quantile requests
fail closed.

The focused regression requires T10 <= T50 <= T90 for both products, an overhead T50 below the
bottoms T50, and 1% repeatability of all three diagnostics across independently constructed and
solved cases. These temperatures make the modeled separation direction and broad boiling range
inspectable on the exact three-pseudo-component DOE basis.

The curve is deliberately discrete and molar-basis. It is not a continuous simulated-distillation
curve, a TBP curve, an ASTM D86 or ASTM D1160 result, or a pressure-corrected laboratory
measurement. With only three heavy pseudo-components it must not be used to infer unreported cut
tails, detailed product quality, or validated VGO/residue yields.

## Absolute-pressure sensitivity screening

`DoeBigHillVacuumPressureSensitivity.run(...)` independently rebuilds, solves, and evaluates
multiple Big Hill vacuum cases while scaling the feed, top, and bottom absolute pressures together.
Tray count, feed tray, feed and reboiler temperatures, reflux ratio, feed composition, and feed mass
flow remain fixed. Pressure factors must be finite, positive, unique, strictly increasing, and retain
the qualified sub-atmospheric pressure topology.

The documented three-point screen uses factors close to the qualified base point:

```java
OperatingInputs baseline =
    new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
double[] pressureFactors = {0.98, 1.00, 1.02};

DoeBigHillVacuumPressureSensitivity sensitivity =
    DoeBigHillVacuumPressureSensitivity.run(
        "Big Hill vacuum pressure screen", 1000.0, baseline, pressureFactors);

for (DoeBigHillVacuumPressureSensitivity.PointResult point : sensitivity.getPoints()) {
  double factor = point.getPressureScaleFactor();
  double topPressureBara = point.getOperatingInputs().getTopPressureBara();
  double overheadMassFraction = point.getOverheadMassFraction();
  double overheadT50Kelvin = point.getOverheadBoilingPointQuantileKelvin(0.50);
}
```

Every point must pass the already qualified MESH-residual, fallback, mass, component, energy,
material-product, and boiling-point-order gates. The summary returns a defensive point array, exact
applied operating inputs, immutable per-point fractionation results, the observed overhead-yield
bounds, and the worst external mass closure, component closure, column energy error, and final MESH
residual. A failed point aborts the complete sensitivity instead of returning a partial envelope.

The factors scale **absolute pressure**, not vacuum gauge or pressure drop. This keeps the relative
pressure profile fixed and isolates one numerical operating variable; it does not represent an
optimized or vendor-recommended pressure profile. The narrow 0.98/1.00/1.02 regression is a
convergence and conservation test around the documented screening point. It does not establish a
measured pressure response, require a monotonic yield trend, define an uncertainty distribution,
validate product quality, or demonstrate equipment turndown.

All DOE assay provenance and the three-cut 650 degF+ normalization remain unchanged. The calculation
does not add ASTM D1160 or TBP pressure correction, measured vacuum-column data, fitted parameters,
calibrated VGO/residue yields, equipment design, or plant-agreement evidence.

## Condenser-reflux sensitivity screening

`DoeBigHillVacuumRefluxSensitivity.run(...)` independently rebuilds, solves, and evaluates
multiple Big Hill vacuum cases while varying only the condenser reflux ratio. Tray count, feed tray,
feed and reboiler temperatures, all three absolute pressures, feed composition, and feed mass flow
remain fixed. Reflux ratios must be finite, non-negative, unique, and strictly increasing.

The documented three-point screen stays close to the qualified base point:

```java
OperatingInputs baseline =
    new OperatingInputs(12, 4, 640.0, 0.12, 0.08, 0.16, 700.0, 0.5);
double[] refluxRatios = {0.49, 0.50, 0.51};

DoeBigHillVacuumRefluxSensitivity sensitivity =
    DoeBigHillVacuumRefluxSensitivity.run(
        "Big Hill vacuum reflux screen", 1000.0, baseline, refluxRatios);

for (DoeBigHillVacuumRefluxSensitivity.PointResult point : sensitivity.getPoints()) {
  double refluxRatio = point.getCondenserRefluxRatio();
  double overheadMassFraction = point.getOverheadMassFraction();
  double overheadT50Kelvin = point.getOverheadBoilingPointQuantileKelvin(0.50);
}
```

Every point must pass the already qualified MESH-residual, fallback, mass, component, energy,
material-product, and boiling-point-order gates. The summary returns a defensive point array, exact
applied operating inputs, immutable per-point fractionation results, the observed overhead-yield
bounds, and the worst external mass closure, component closure, column energy error, and final MESH
residual. A failed point aborts the complete sensitivity instead of returning a partial envelope.

The condenser reflux ratio is a dimensionless `DistillationColumn` input. This screen isolates that
single numerical operating variable; it does not represent a measured, optimized, or
vendor-recommended reflux policy. The narrow 0.49/0.50/0.51 regression is a convergence and
conservation test around the documented screening point. It does not establish a measured reflux
response, require a monotonic yield trend, quantify condenser or reboiler duty, define utilities,
size equipment, validate product quality, or demonstrate turndown.

All DOE assay provenance and the three-cut 650 degF+ normalization remain unchanged. The calculation
does not add ASTM D1160 or TBP pressure correction, measured vacuum-column data, fitted parameters,
calibrated VGO/residue yields, optimization, product specifications, or plant-agreement evidence.
