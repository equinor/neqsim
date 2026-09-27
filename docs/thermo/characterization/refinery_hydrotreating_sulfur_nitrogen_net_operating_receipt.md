---
title: Net coupled sulfur/nitrogen hydrotreating operating receipt
description: Combine qualified hydrogen and post-recovery fired-heater energy, emissions and cost receipts.
---

# Net coupled sulfur/nitrogen hydrotreating operating receipt

`RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt` composes the qualified
hydrogen operating receipt and fired-heater heat-recovery credit on a common
liquid-feed basis. A caller-owned carbon price is applied to the combined
hydrogen-supply and net fired-heater fuel emissions.

```java
RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt net =
    RefineryHydrotreatingSulfurNitrogenNetOperatingReceipt.calculate(
        hydrogenOperatingReceipt, heatRecoveryCredit, callerCarbonPricePerTonneCo2e);

double externalEnergyMWhPerTonne = net.getTotalExternalEnergyMWhPerTonneFeed();
double emissionsKgCo2ePerTonne = net.getTotalEmissionsKgCo2EquivalentPerTonneFeed();
double operatingCostPerTonne = net.getTotalOperatingCostPerTonneFeed();
```

The receipt closes three auditable identities:

```text
total external energy = fresh-H2 energy + net fired-heater fuel energy
total emissions = hydrogen-supply emissions + net fired-heater fuel emissions
total cost = H2 purchase cost + net fuel cost + combined carbon cost
```

For the public 1000 kg/h DOE/OEDI Big Hill chain, the qualified 60% heat-recovery
scenario, 75% caller-owned recovered-heat utilization, and an illustrative
carbon price of 100 currency units/t CO2e give 1.018063442 MWh/t external energy,
223.315219137 kg CO2e/t emissions, and 54.138738997 currency units/t operating
cost. These values qualify composition and arithmetic only. The carbon price,
fuel price, hydrogen price, emissions factors, furnace efficiency, recovery
fraction, and utilization fraction remain caller-owned inputs or qualified
upstream scenario inputs; none is a default or recommendation.

The receipt does not establish a lifecycle boundary, allocate shared utilities,
size equipment, optimize heat integration, choose market or carbon prices,
predict stack conditions, qualify corrosion or operability, or make compliance
or design claims. Independent process-heat, emissions-boundary, and economic
review is required for project use.

