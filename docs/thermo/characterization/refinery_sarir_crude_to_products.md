---
title: "Sarir crude heating to atmospheric products"
description: "A source-bounded connected workflow from crude preheat through atmospheric fractionation."
---

# Sarir crude heating to atmospheric products

The connected Sarir workflow carries one characterized crude state through
`SarirAtmosphericCrudeHeatingCase` and into `SarirAtmosphericFractionationCase`. The furnace
outlet is retained as the live upstream stream for the column feed. This closes the software boundary
between the separately qualified heating and fractionation cases without inventing an undocumented
refinery flowsheet.

## Source and license

The public reference is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using
Aspen HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51-64, published 31 March
2022, DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The
[open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY
4.0.

The connected boundary uses only the article's reported atmospheric-column feed values already
encoded by `SarirAtmosphericReference`: 54,420 kg/h, 350 degC, and 233 kPa absolute. The caller
must still supply the constrained 18-cut density and molar-mass profiles and every source-unreported
heating, fuel, emissions, endpoint, reflux, and side-draw input.

## Java and Python/JPype workflow

Run the heating case before asking the fractionation factory to connect it. Then use one calculation
identifier for the connected feed and column run.

```java
SarirAtmosphericCrudeHeatingCase.HeatingInputs heatingInputs =
    new SarirAtmosphericCrudeHeatingCase.HeatingInputs(
        crudeInletTemperatureK,
        preheatTemperatureK,
        preheaterPressureLossBara,
        furnacePressureLossBara,
        thermalEfficiency,
        fuelLowerHeatingValueJPerKg,
        fuelCO2FactorKgPerKg,
        noxFactorKgPerGJ,
        stackTemperatureK);

SarirAtmosphericCrudeHeatingCase heating =
    SarirAtmosphericCrudeHeatingCase.create(
        "Sarir heating", cutSpecificGravity, cutMolarMassKgPerMol, heatingInputs);

UUID calculationId = UUID.randomUUID();
heating.run(calculationId);

SarirAtmosphericFractionationCase.OperatingInputs columnInputs =
    new SarirAtmosphericFractionationCase.OperatingInputs(
        topPressureBara,
        bottomPressureBara,
        reboilerTemperatureK,
        condenserRefluxRatio,
        keroseneSideDrawTray,
        keroseneSideDrawFraction,
        dieselSideDrawTray,
        dieselSideDrawFraction);

SarirAtmosphericFractionationCase fractionation =
    SarirAtmosphericFractionationCase.createFromHeatingCase(
        "Sarir connected screen", heating, columnInputs);
fractionation.run(calculationId);

SarirAtmosphericFractionationResult products =
    SarirAtmosphericFractionationResult.evaluate(fractionation);
```

The same classes and methods are accessible through JPype. Numerical inputs in the example are
intentionally symbolic: the repository does not provide hidden typical furnace, fuel, reflux,
side-draw, or endpoint assumptions.

## Fail-closed boundary and engineering checks

`createFromHeatingCase` rejects an unsolved heating case. It also rejects a non-finite or mismatched
furnace-outlet mass flow, temperature, or pressure before constructing the column. The connected feed
is a NeqSim stream wrapper around the qualified furnace outlet, so the characterized composition is
not rebuilt at the handoff.

`fractionation.run(UUID)` refreshes that connected feed with the supplied identifier, rechecks the
published boundary, and runs the existing 34-simple-tray MESH-residual column. The established
fractionation result remains responsible for convergence, fallback rejection, mass and energy
closure, ordered material products, discrete pseudo-component boiling diagnostics, and separate
calculated-versus-published product evidence.

## Interpretation and limits

This is a deterministic integration screen. Passing the boundary checks demonstrates that the same
qualified thermodynamic state reached by the heating train is admitted to the existing column model
at the published feed conditions. It does not show that the unreported heating or column inputs are
plant values, and it does not calibrate the calculated product yields against the published product
rates.

The workflow does not model exchanger-network topology, utilities, fouling, radiant or convection
sections, combustion chemistry, steam injection, pump-arounds, side strippers, tray efficiencies,
hydraulic capacity, controls, equipment design, or product blending. It does not establish ASTM
D86, ASTM D1160, or TBP equivalence; product specifications; regulatory emissions; optimization;
uncertainty bounds; or plant agreement. The independent source comparison in
`SarirAtmosphericFractionationResult` remains evidence rather than an acceptance target.
