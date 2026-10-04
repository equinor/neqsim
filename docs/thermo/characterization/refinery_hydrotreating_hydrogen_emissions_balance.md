---
title: Hydrotreating hydrogen-supply emissions balance
description: Apply explicit emissions and carbon-price scenarios to qualified fresh hydrogen.
---

# Hydrotreating hydrogen-supply emissions balance

`RefineryHydrotreatingHydrogenEmissionsBalance` converts a qualified
`RefineryHydrotreatingHydrogenUtilityBalance` into indirect hydrogen-supply emissions and a
caller-priced carbon-cost receipt. The upstream sulfur, hydrogen-supply, recycle, throughput, and
utility receipts remain immutable.

## Explicit scenario inputs

The caller supplies:

- hydrogen-supply emission factor in kg CO2e per kg fresh H2; and
- carbon price in caller-owned currency units per tonne CO2e.

These values are not NeqSim defaults. A study must state the hydrogen-production pathway, lifecycle
boundary, data source, geography, time basis, and price scenario externally.

For external fresh hydrogen mass flow `m_H2`, the scenario emissions are:

```text
E_H2 = m_H2 * EF_H2   [kg CO2e/h]
```

The carbon-price receipt is:

```text
C_CO2e = E_H2 * p_CO2e / 1000   [caller-owned currency units/h]
```

Emissions and cost per tonne of fresh liquid feed use the qualified feed mass rate from the upstream
throughput receipt.

## Java example

```java
RefineryHydrotreatingHydrogenEmissionsBalance emissions =
    RefineryHydrotreatingHydrogenEmissionsBalance.calculate(
        utility, 10.0, 100.0);

double emissionsKgCo2ePerHour =
    emissions.getHydrogenSupplyEmissionsKgCo2EquivalentPerHour();
double carbonCostPerTonneFeed =
    emissions.getCarbonCostPerTonneFeed();
```

The 10 kg CO2e/kg H2 emission factor and 100 currency-units/tonne CO2e carbon price are illustrative
scenario inputs. They are not validated observations, recommended factors, market prices, or
embedded correlations.

On the qualified public DOE/OEDI Big Hill 1000 kg/h case, these illustrative inputs give
0.549093756 kg/h fresh H2, 5.49093756 kg CO2e/h, 5.49093756 kg CO2e per tonne of feed,
0.549093756 currency units/h, and 0.549093756 currency units per tonne of feed.

## Provenance and boundary

The material basis inherits the DOE/OEDI Big Hill assay and the EIA, AIChE, and NIST evidence
documented by the qualified sulfur, hydrogen-supply, recycle, throughput, and utility receipts. This
increment introduces no fitted parameter, emissions database, commodity-price dataset, or physical
correlation.

The receipt is indirect scenario bookkeeping only. It does not select a hydrogen-production
pathway, establish a lifecycle boundary, predict direct process, furnace, compressor, electricity,
or steam emissions, model carbon capture, calculate reaction heat or equipment duty, predict
kinetics, catalyst behavior or phase equilibrium, perform optimization, demonstrate regulatory
compliance, or establish a verified product carbon footprint.
