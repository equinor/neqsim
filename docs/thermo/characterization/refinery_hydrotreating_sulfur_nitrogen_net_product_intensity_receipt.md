---
title: Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt
description: Convert qualified net operating results from a liquid-feed basis to the external liquid-product basis.
---

# Net coupled sulfur/nitrogen hydrotreating liquid-product intensity receipt

`RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt` converts the
qualified net operating receipt to an external liquid-product reporting basis.
The upstream product-distribution receipt supplies the liquid-product rate; the
energy, emissions, and operating-cost totals are not recomputed.

```java
RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt intensity =
    RefineryHydrotreatingSulfurNitrogenNetProductIntensityReceipt.calculate(
        netOperatingReceipt);

double energyMWhPerTonneProduct =
    intensity.getTotalExternalEnergyMWhPerTonneLiquidProduct();
double emissionsKgCo2ePerTonneProduct =
    intensity.getTotalEmissionsKgCo2EquivalentPerTonneLiquidProduct();
double costPerTonneProduct =
    intensity.getTotalOperatingCostPerTonneLiquidProduct();
```

The receipt closes three basis-conversion identities:

```text
product-basis energy intensity * liquid-product rate = feed-basis energy intensity * feed rate
product-basis emissions intensity * liquid-product rate = total emissions rate
product-basis operating-cost intensity * liquid-product rate = total operating-cost rate
```

For the public 1000 kg/h DOE/OEDI Big Hill chain, the qualified external liquid
product is 995.489447979 kg/h. The established net operating scenario therefore
reports 1.022676277 MWh/t liquid product, 224.327058002 kg CO2e/t liquid product,
and 54.384041043 caller-currency units/t liquid product. All three basis closure
residuals are numerical zero.

These are gross process-input intensities divided by the qualified external
liquid-product rate. They do not allocate energy, emissions, or cost between
liquid product and export gas, and they do not represent a product carbon
footprint or lifecycle result. The receipt adds no hydrocarbon-yield correlation,
product-quality model, byproduct credit, product value, market assumption,
equipment design, optimization, compliance conclusion, or design recommendation.
