---
title: "Sarir crude heating train"
description: "Source-bounded preheater and fired-heater screening to the public atmospheric-column feed boundary."
---

# Sarir crude heating train

`SarirAtmosphericCrudeHeatingCase` carries the existing constrained Sarir assay through a sensible
preheater and a fired heater to the published atmospheric-column feed boundary. It is a Phase 4
process-integration screen, not a reconstruction of the refinery's undocumented heat-exchanger
network or furnace.

## Source and license

The source is Hamza E. Omran Almansouri, *Simulation of Sarir Crude Oil Refinery Using Aspen
HYSYS*, Journal of Engineering Research (Libya), issue 33, pages 51-64, published 31 March 2022,
DOI [10.66411/jer.v33i.46](https://doi.org/10.66411/jer.v33i.46). The
[open-access article](https://jer.ly/jer/index.php/jer/article/download/46/38/39) is licensed CC BY
4.0.

Only three heating-train boundary values come from the article: 54,420 kg/h crude, 350 degC at the
atmospheric-column inlet, and 233 kPa absolute at that inlet. The paper does not publish a crude
inlet temperature, preheat target, preheater or furnace pressure loss, fired-heater efficiency, fuel
lower heating value, emission factors, or stack temperature. The case therefore requires all of
those values from the caller and keeps them separate from source facts.

## Capability contract

The factory first delegates the 18-cut density and molar-mass profiles to
`SarirAtmosphericAssay`. Its whole-crude density and average-molar-mass consistency gates remain
authoritative. It then derives the crude inlet pressure by adding the caller's two nonnegative
pressure losses to the published column-feed pressure. A `Heater` reaches the caller's intermediate
preheat temperature; a `FiredHeater` reaches the published 350 degC and 233 kPa boundary.

One `run(UUID)` call executes the inlet and both heaters with the same calculation identifier. The
case fails closed unless:

- inlet, preheat, and column-feed temperatures are strictly increasing;
- efficiency is in (0, 1], fuel LHV is positive, and pressure losses and emission factors are
  nonnegative;
- both heaters conserve the published hydrocarbon mass flow and reach their configured temperature
  and pressure boundaries;
- both absorbed duties and fuel consumption are finite and positive;
- fired duty equals absorbed furnace duty divided by efficiency, stack loss is their difference,
  and fuel, CO2, and NOx rates reproduce the caller-supplied factors.

## Java and Python/JPype access

```java
SarirAtmosphericCrudeHeatingCase.HeatingInputs inputs =
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
        "Sarir heating screen", cutSpecificGravity, cutMolarMassKgPerMol, inputs);
heating.run(java.util.UUID.randomUUID());

double preheatDutyW = heating.getPreheater().getDuty("W");
double firedDutyW = heating.getFurnace().getFiredDuty("W");
double fuelKgPerHour = heating.getFurnace().getFuelConsumption("kg/hr");
double co2KgPerHour = heating.getFurnace().getCO2Emissions("kg/hr");
StreamInterface columnFeed = heating.getColumnFeedStream();
```

The same public classes and getters are available through JPype. Numerical values in the example
must be selected and documented by the caller; the repository does not provide a hidden "typical"
fuel or efficiency profile.

## Limits and next boundary

This screen does not model heat-exchanger topology, utility streams, fouling, radiant or convection
sections, combustion chemistry, air demand, flue-gas composition, steam injection, pump-arounds,
side strippers, tray efficiency, or product-yield tuning. `FiredHeater` applies arithmetic fuel and
emission factors; its outputs are not a stack test, regulatory inventory, or calibrated plant
prediction.

The outlet matches the published atmospheric-column inlet state but is not automatically attached
to `SarirAtmosphericFractionationCase`. A later integration increment must preserve the existing
column MESH, conservation, product-ordering, and independent plant-comparison gates before it can
claim a complete end-to-end workflow.
