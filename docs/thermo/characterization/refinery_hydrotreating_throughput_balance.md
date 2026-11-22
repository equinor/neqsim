---
title: Hydrotreating throughput-rate balance
description: Convert qualified hydrotreating basis receipts to explicit process flow rates.
---

# Hydrotreating throughput-rate balance

`RefineryHydrotreatingThroughputBalance` converts a qualified
`RefineryHydrotreatingHydrogenRecycleBalance` basis to kg/h and kmol/h. It preserves the upstream
sulfur, hydrogen-supply, component-recovery, and purge assumptions exactly and does not mutate or
re-solve any upstream receipt.

## Rate conversion

For feed flow `m_feed` and upstream feed basis `M_basis`, the scale is:

```text
s = m_feed / M_basis   [1/h]
```

Every basis mass is multiplied by `s` to obtain kg/h. Every basis amount in mol is multiplied by
`s / 1000` to obtain kmol/h. Internal recycle is reported separately and is excluded from the
external balance:

```text
feed + fresh makeup = liquid product + export gas
```

## Java example

```java
RefineryHydrotreatingSulfurBalance sulfur =
    RefineryHydrotreatingSulfurBalance.calculate(
        1000.0, 0.0040867518, 15.0e-6, 2.0);
RefineryHydrotreatingHydrogenSupplyBalance supply =
    RefineryHydrotreatingHydrogenSupplyBalance.calculate(
        sulfur, 1.5, 0.90, 0.0280134);
RefineryHydrotreatingHydrogenRecycleBalance recycle =
    RefineryHydrotreatingHydrogenRecycleBalance.calculate(
        supply, 0.90, 0.10, 0.50, 0.05);

RefineryHydrotreatingThroughputBalance rate =
    RefineryHydrotreatingThroughputBalance.calculate(recycle, 1000.0);

double freshMakeupKgPerHour = rate.getFreshMakeupGasMassFlowKgPerHour();
double recycleKgPerHour = rate.getRecycleGasMassFlowKgPerHour();
double exportKgPerHour = rate.getExportGasMassFlowKgPerHour();
double freshHydrogenKmolPerHour = rate.getFreshHydrogenMolarFlowKmolPerHour();
```

On the public 1000 kg/h DOE/OEDI Big Hill basis, the receipt removes 4.071809037 kg/h sulfur,
consumes 0.511975530 kg/h H2, produces 996.184178728 kg/h liquid, requires 1.396916654 kg/h fresh
makeup gas, circulates 1.440246511 kg/h internal recycle gas, and exports 5.212737927 kg/h gas. The
fresh-H2 rate is 0.272384148 kmol/h and the external mass-rate residual is numerical zero.

## Provenance and boundary

This receipt inherits the public DOE/OEDI Big Hill assay and EIA, AIChE, and NIST evidence from the
qualified sulfur, hydrogen-supply, and recycle guides. It introduces no fitted parameter or new
physical correlation.

The calculation is rate conversion and conservation bookkeeping. It does not predict reaction
kinetics, catalyst performance, phase equilibrium, separator recovery, compressor duty, pressure,
temperature, yield, heat duty, emissions, optimization, or compliance. Process design requires
independent qualification of those effects.
