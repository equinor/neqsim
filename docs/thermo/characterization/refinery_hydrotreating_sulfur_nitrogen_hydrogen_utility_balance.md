---
title: Coupled sulfur/nitrogen hydrogen utility balance
description: Convert qualified coupled external hydrogen rates to auditable energy and cost receipts.
---

# Coupled sulfur/nitrogen hydrogen utility balance

`RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance` converts a qualified
`RefineryHydrotreatingSulfurNitrogenHydrogenRecycleThroughputBalance` into external hydrogen
chemical-energy and cost rates. It preserves the upstream sulfur, nitrogen, hydrogen-supply,
recycle, purge, and throughput receipts exactly. Internal recycle is excluded from the external
utility boundary.

## Explicit scenario inputs

The caller supplies:

- hydrogen lower heating value in MJ/kg; and
- hydrogen price in caller-owned currency units per kg.

These values are intentionally not hardcoded. A study can therefore state its thermochemical basis,
price date, currency, and scenario assumptions without presenting them as NeqSim correlations or
market data.

For fresh, consumed, and exported hydrogen mass flow `m`, the LHV power is:

```text
P_LHV = m * LHV / 3600   [MW]
```

The external steady-state hydrogen identity is:

```text
fresh H2 = consumed H2 + exported H2
```

Fresh-hydrogen cost per tonne of fresh liquid feed is:

```text
C_t = C_H2 * m_fresh,H2 * 1000 / m_feed
```

## Java example

```java
RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance utility =
    RefineryHydrotreatingSulfurNitrogenHydrogenUtilityBalance.calculate(
        throughput, 120.0, 3.0);

double freshHydrogenPowerMW =
    utility.getFreshHydrogenChemicalPowerMegaWatt();
double hydrogenCostPerTonneFeed =
    utility.getFreshHydrogenCostPerTonneFeed();
```

The 120 MJ/kg and 3 currency-units/kg values are illustrative scenario inputs, not embedded defaults
or validated market observations.

On the qualified public DOE/OEDI Big Hill 1000 kg/h coupled throughput case, those illustrative
inputs give 1.219112719 kg/h fresh H2, 1.136701836 kg/h consumed H2, 0.082410883 kg/h exported H2,
0.040637091 MW fresh-H2 LHV power, and 3.657338157 currency units per tonne of feed. The external
hydrogen-energy residual is numerical zero.

## Provenance and boundary

The material basis inherits the DOE/OEDI Big Hill assay and the EIA, AIChE, and NIST evidence
documented by the qualified coupled material, supply, recycle, and throughput receipts. This
increment introduces no fitted parameter, commodity-price dataset, or physical correlation.

The receipt is utility bookkeeping only. It does not predict reaction enthalpy, reactor or furnace
duty, heat loss, temperature, pressure, kinetics, catalyst behavior, phase equilibrium, compressor
power, hydrogen-production emissions, optimization, or compliance. Those effects require
independent models, data, and qualification.
