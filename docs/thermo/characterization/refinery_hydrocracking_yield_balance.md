---
title: Hydrocracking yield and hydrogen-supply balance
description: Screen caller-defined hydrocracking conversion, product lumps, hydrogen demand, and reformer-hydrogen offset.
---

# Hydrocracking yield and hydrogen-supply balance

`RefineryHydrocrackingYieldBalance` creates an immutable screening receipt from an explicit
hydrocarbon feed rate, conversion, hydrogen consumption, and three caller-owned product
selectivities. `RefineryHydrocrackingHydrogenSupplyBalance` then allocates qualified recovered
catalytic-reformer hydrogen against that demand and reports the imported-hydrogen requirement or
reformer-hydrogen surplus.

These classes are transparent scenario balances, not yield predictors. They do not solve reactor
kinetics, catalyst activity or deactivation, quench, temperature profiles, recycle gas,
phase equilibrium, product properties, sulfur/nitrogen removal, coke, or fractionation.

## Yield-balance basis

Let hydrocarbon feed mass flow be `F`, conversion be `X`, and hydrogen consumption be `H` in kg/h.
The represented unconverted-oil and converted-product masses are:

```text
unconverted oil = F (1 - X)
converted product = F X + H
```

The caller supplies light-gas, naphtha, and middle-distillate mass fractions that sum to one on the
converted-product basis. The balance reports each product in kg/h plus feed-conversion,
converted-product, and overall mass residuals. Hydrogen consumption is supplied in kg/t feed and
converted internally to kg/h.

```java
RefineryHydrocrackingYieldBalance hydrocracking =
    RefineryHydrocrackingYieldBalance.calculate(
        1000.0, // feed, kg/h
        0.80,   // feed conversion
        20.0,   // hydrogen consumption, kg/t feed
        0.10,   // light gas fraction of converted product
        0.30,   // naphtha fraction of converted product
        0.60);  // middle-distillate fraction of converted product
```

This analytical example converts 800 kg/h feed, retains 200 kg/h unconverted oil, and consumes
20 kg/h hydrogen. The 820 kg/h converted-product mass partitions into 82 kg/h light gas,
246 kg/h naphtha, and 492 kg/h middle distillate. Total represented product is 1020 kg/h, and all
three mass residuals are numerically zero.

## Reformer-hydrogen allocation

The supply receipt connects the new yield balance to a qualified
`RefineryCatalyticReformingHydrogenRecoveryBalance` without introducing a network optimizer:

```java
RefineryHydrocrackingHydrogenSupplyBalance hydrogenSupply =
    RefineryHydrocrackingHydrogenSupplyBalance.calculate(
        hydrocracking, reformingHydrogenRecovery);

double importedHydrogenKgPerHour =
    hydrogenSupply.getImportedHydrogenRequiredKgPerHour();
double demandCoverage = hydrogenSupply.getHydrogenDemandCoverageFraction();
```

For the documented C6/C8 reforming receipt at 80% hydrogen recovery, 34.673136 kg/h recovered
hydrogen is available. A 1000 kg/h hydrocracker consuming 50 kg/t feed allocates all 34.673136 kg/h
to hydrocracking and requires 15.326864 kg/h imported hydrogen. Recovered reformer hydrogen covers
69.346272% of demand; demand and recovered-supply residuals are numerically zero.

## Public provenance and boundary

- The public US Department of the Interior report
  [Factors Affecting U.S. Petroleum Refining](https://www.energy.gov/sites/default/files/2022-11/1973-Factors_Affecting_US_Petroleum_Refining-Impact_of_New_Tech.pdf)
  describes hydrocracking as mixing hydrogen with hydrocarbon feed, producing lower-molecular-weight
  products across LPG, gasoline, jet-fuel, and distillate ranges, and sourcing hydrogen from catalytic
  reforming or a separate purchased/manufactured supply.
- Song et al.,
  [A New Lumped Kinetic Model of an Industrial Hydrocracking Process](https://doi.org/10.3303/CET1761110),
  Chemical Engineering Transactions 61 (2017), documents lumped hydrocracking product-yield modeling.
  NeqSim does not copy its fitted parameters or claim its kinetic validation.

The example conversion, hydrogen consumption, and product fractions are analytical assumptions, not
values extracted from either source. An engineering case must supply independently justified yields,
hydrogen demand, feed basis, and operating envelope. All represented consumed hydrogen is assigned to
the three converted hydrocarbon lumps; heteroatom byproducts and water are outside this simplified
mass boundary. A design claim requires a validated feed characterization, reaction/kinetic model,
catalyst and deactivation basis, reactor heat balance, separator/recycle system, product fractionation,
and independent plant or literature validation.
