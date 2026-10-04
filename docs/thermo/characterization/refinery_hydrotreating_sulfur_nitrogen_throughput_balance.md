---
title: Coupled hydrotreating sulfur/nitrogen throughput balance
description: Convert a qualified coupled material basis to auditable kg/h rates.
---

# Coupled hydrotreating sulfur/nitrogen throughput balance

`RefineryHydrotreatingSulfurNitrogenThroughputBalance` converts one qualified
`RefineryHydrotreatingSulfurNitrogenBalance` basis to unit-explicit hourly rates. Every rate is a
linear scale of the immutable upstream receipt; the calculation introduces no new chemistry,
correlation, target, or operating assumption.

## Inputs and reported rates

The caller supplies a qualified coupled receipt and a positive fresh liquid feed rate in kg/h. The
throughput receipt reports:

- basis scale in 1/h and liquid product in kg/h;
- feed, removed, and product sulfur and nitrogen in kg/h;
- sulfur-route, nitrogen-route, and total H2 consumption in kg/h;
- H2S and NH3 production in kg/h;
- hydrogen retained in the liquid bookkeeping balance in kg/h; and
- independent total, sulfur, and nitrogen closure residuals in kg/h.

The external total-mass check is:

```text
feed + H2 consumed = liquid product + H2S + NH3
```

Internal recycle is absent from this boundary. The existing sulfur-only makeup/recycle contract is
not reused because it does not yet represent NH3.

## Java example

```java
RefineryHydrotreatingSulfurNitrogenBalance material =
    RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129,
        15.0e-6, 10.0e-6, 2.0, 4.0);

RefineryHydrotreatingSulfurNitrogenThroughputBalance rates =
    RefineryHydrotreatingSulfurNitrogenThroughputBalance.calculate(
        material, 1000.0);
```

On the public DOE SPR/OEDI Big Hill screen at 1000 kg/h, the receipt reports
`995.489447979 kg/h` liquid product, `4.071819458 kg/h` sulfur removed,
`1.085174106 kg/h` nitrogen removed, `1.136701836 kg/h` total H2 consumed,
`4.327807878 kg/h` H2S, and `1.319445979 kg/h` NH3. The upstream targets remain exactly
15 ppm sulfur and 10 ppm total nitrogen on the common liquid-product basis.

## Provenance and engineering boundary

The material basis inherits the qualified public DOE SPR/OEDI Big Hill assay and the EIA, AIChE,
and NIST references documented by the sulfur, nitrogen, and coupled receipts. Source workbooks and
references are cited, not redistributed. Product targets and H2/S and H2/N ratios remain
illustrative caller assumptions.

This receipt performs rate conversion and conservation bookkeeping only. It does not identify
organic sulfur or nitrogen species; predict pathways, kinetics, catalyst performance, severity,
conversion, hydrocarbon yield, cracking, phase equilibrium, separation, recycle, or heat duty; or
make emissions, economics, compliance, or plant-performance claims.
