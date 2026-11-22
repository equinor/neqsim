---
title: Coupled sulfur/nitrogen product-distribution receipt
description: Report external liquid-product and export-gas distributions from a qualified hydrotreating throughput balance.
---

# Coupled sulfur/nitrogen product-distribution receipt
`RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt` converts the
qualified coupled recycle-throughput result into auditable external liquid-product
and export-gas distributions. Internal recycle is excluded from every yield,
fraction, and closure result.

The receipt reports liquid product and export gas in kg/h and kg per tonne liquid
feed. It also converts the qualified export H2, H2S, NH3, and non-H2 molar rates to
kg/h and reports their mass fractions. The two explicit closures are:

```text
total external products = liquid product + export gas
export gas = H2 + H2S + NH3 + non-H2 makeup gas
```

```java
RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt distribution =
    RefineryHydrotreatingSulfurNitrogenProductDistributionReceipt.calculate(throughput);

double liquidKgPerTonne = distribution.getLiquidProductKgPerTonneFeed();
double exportGasKgPerTonne = distribution.getExportGasKgPerTonneFeed();
double hydrogenSulfideKgPerHour = distribution.getExportHydrogenSulfideMassFlowKgPerHour();
double ammoniaKgPerHour = distribution.getExportAmmoniaMassFlowKgPerHour();
```

On the public 1000 kg/h DOE/OEDI Big Hill basis with the qualified illustrative
supply, recovery, and purge assumptions, the receipt reports 995.489447979 kg/h
liquid product and 7.612023933 kg/h export gas. The export gas contains
0.082410883 kg/h H2, 4.327807878 kg/h H2S, 1.319445979 kg/h NH3, and
1.882359193 kg/h non-H2 makeup gas. Both closure residuals are numerical zero.

The material basis inherits the public DOE/OEDI Big Hill assay and the EIA, AIChE,
and NIST evidence qualified by the upstream coupled receipts. This receipt adds
unit conversion and conservation bookkeeping only; it introduces no fitted
parameter, product-yield correlation, or proprietary dataset.

This receipt does not predict hydrocarbon yield or composition, reaction pathway,
kinetics, catalyst performance, phase equilibrium, separator recovery, operating
conditions, heat duty, product specifications, emissions, optimization, or
regulatory compliance. Those effects require independent engineering qualification.
