---
title: Integrated hydrotreating operating receipt
description: Consolidate qualified hydrotreating material, energy, emissions, and scenario-cost results.
---

# Integrated hydrotreating operating receipt

`RefineryHydrotreatingOperatingReceipt` consolidates the qualified hydrotreating throughput,
hydrogen utility, and hydrogen-supply emissions receipts into one immutable process-integration
result. It retains the exact nested receipt chain and does not recompute any sulfur, hydrogen,
recycle, energy, emissions-factor, or price assumption.

## Reported quantities

The receipt exposes:

- fresh liquid feed and external fresh H2 rates in kg/h;
- fresh-H2 lower-heating-value energy intensity in MWh per tonne of liquid feed;
- indirect hydrogen-supply emissions in kg CO2e/h and kg CO2e per tonne feed;
- caller-priced hydrogen purchase and carbon costs per hour and per tonne feed;
- combined scenario cost and its arithmetic closure residual.

The combined scenario cost is:

```text
C_total = C_H2 + C_CO2e
```

This addition combines caller-owned scenario costs only. It is not a refinery operating-cost model.

## Java example

```java
RefineryHydrotreatingOperatingReceipt receipt =
    RefineryHydrotreatingOperatingReceipt.calculate(emissions);

double energyIntensityMWhPerTonne =
    receipt.getFreshHydrogenEnergyMWhPerTonneFeed();
double scenarioCostPerTonne =
    receipt.getTotalScenarioCostPerTonneFeed();
```

On the qualified public DOE/OEDI Big Hill 1000 kg/h case, the established illustrative scenario of
120 MJ/kg H2 LHV, 3 currency units/kg H2, 10 kg CO2e/kg H2, and
100 currency units/tonne CO2e gives:

- 0.549093756 kg/h fresh H2;
- 0.018303125 MWh fresh-H2 LHV per tonne feed;
- 5.49093756 kg CO2e/h and per tonne feed;
- 1.647281268 currency units/h hydrogen purchase cost;
- 0.549093756 currency units/h carbon cost; and
- 2.196375024 currency units/h and per tonne feed combined scenario cost.

All LHV, price, emission-factor, and carbon-price values remain explicit illustrative caller inputs,
not defaults, recommendations, measurements, market observations, or fitted correlations.

## Provenance and boundary

The material basis inherits the DOE/OEDI Big Hill assay and the EIA, AIChE, and NIST evidence
documented by the qualified sulfur, hydrogen-supply, recycle, throughput, utility, and emissions
receipts. This increment introduces no new parameter, dataset, production pathway, or correlation.

The receipt is aggregation and conservation bookkeeping only. It does not predict yield, reaction
heat, reactor, furnace, compressor, electricity, or steam duty, direct emissions, lifecycle
boundaries, hydrogen production, carbon capture, kinetics, catalyst behavior, phase equilibrium,
optimization, regulatory compliance, or a verified product carbon footprint.
