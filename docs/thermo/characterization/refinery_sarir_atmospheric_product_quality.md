---
title: "Sarir atmospheric product-quality screen"
description: "Strict ASTM D86 T95 evidence for calculated Sarir kerosene and diesel side draws."
---

# Sarir atmospheric product-quality screen

`SarirAtmosphericProductQualityScreen` connects the qualified Sarir atmospheric
fractionation result to the existing strict ASTM D86 T95 comparison. It evaluates
only the calculated kerosene and diesel liquid side draws after the column has
passed the MESH-residual, non-fallback, mass-balance, energy-balance, product
closure, and material-product gates.

## Source and license

The public evidence is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil
Refinery Using Aspen HYSYS*, Journal of Engineering Research (Libya), issue 33,
pages 51-64, published 31 March 2022, DOI
[10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The article is
licensed CC BY 4.0.

The screen preserves these source rows as read-only evidence:

| Product | Laboratory T95 (degC) | HYSYS T95 (degC) | Numeric specification T95 (degC) |
| --- | ---: | ---: | ---: |
| Kerosene | 221 | 214 | 221 |
| Diesel | 346 | 339 | 327 |

The calculated value comes only from the live NeqSim side-draw fluid through
`Standard_ASTM_D86.calculate()` and the qualified
`getQualifiedD86Temperature(95.0, "C")` path used by
`SarirD86ProductComparison`. Published values are not solver controls,
calibration targets, or acceptance thresholds.

## Java and Python/JPype access

Run a `SarirAtmosphericFractionationCase` first, then evaluate the same solved
case only after qualifying material side draws and their boiling order:

```java
SarirAtmosphericProductQualityScreen.Result quality =
    SarirAtmosphericProductQualityScreen.evaluate(model);

SarirD86ProductComparison.Result kerosene =
    quality.getComparison("Kerosene");
double calculatedKeroseneT95C = kerosene.getNeqsimT95Celsius();
double laboratoryErrorPercent =
    kerosene.getNeqsimAbsoluteRelativeErrorPercent();
double specificationMarginC = kerosene.getSpecificationMarginCelsius();

SarirD86ProductComparison.Result diesel =
    quality.getComparison("Diesel");
```

The result exposes comparisons in deterministic kerosene, diesel order and
returns a defensive array copy. Exact, case-sensitive labels are required, so
unsupported or unresolved products fail closed. Static methods and getters are
available to Python callers through JPype.

## Engineering boundaries

The current synthetic side-stripper contact fixture (700 K reboiler, liquid
draws at internal trays 3 and 2) closes its individual equilibrium contacts but
has an inverted kerosene/diesel mean boiling-point order. This screen rejects
that case before calculating T95. The separate 24/15-tray fixture has dry side
draws and is also rejected. Solver convergence alone is insufficient.

The regression suite verifies these rejection paths and retains the positive
standalone `SarirD86ProductComparison` tests. It does not yet establish a
qualified end-to-end Sarir column T95 benchmark; the example above requires a
caller-supplied case that satisfies all upstream gates and the ASTM correlation
range. No product-order or ASTM range checks are relaxed to obtain a result.

This is a product-quality screening calculation, not an ASTM laboratory
procedure or compliance determination. A non-negative specification margin is
only arithmetic against the published reference.

The atmospheric case has one total-naphtha outlet and does not resolve the
source's light/heavy naphtha split. The screen therefore does not synthesize
naphtha product fluids or assign either published naphtha row. Residual is
excluded because its published `550+` specification is nonnumeric and
open-ended.

The workflow does not reproduce or calibrate the Sarir plant. It does not claim
product certification, plant agreement, tray-location validity, side-stripper
performance, pump-around behavior, steam effects, tray efficiency, controller
behavior, or suitability for design or operations. Side-draw tray locations,
draw fractions, reflux, reboiler temperature, and pseudo-component property
profiles remain explicit caller-supplied engineering assumptions.
