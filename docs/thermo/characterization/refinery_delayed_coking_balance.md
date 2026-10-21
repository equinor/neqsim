---
title: Delayed-coking yield and utility screening balances
description: Algebraic delayed-coking product-yield, heater-fuel, and steam receipts
---

# Delayed-coking yield and utility screening balances

`RefineryDelayedCokingYieldBalance` and `RefineryDelayedCokingUtilityBalance` provide a
transparent screening bridge from a residue-feed scenario to gas, naphtha, light coker gas oil
(LCGO), heavy coker gas oil (HCGO), petroleum coke, unconverted residue, process duty, fuel, and
steam. Every conversion, yield, and utility intensity is caller supplied.

## Capability boundary

The yield receipt applies a caller-owned conversion to fresh residue feed and allocates converted
feed among five product lumps. The utility receipt applies caller-owned specific process duty,
furnace efficiency, fuel lower heating value, and steam intensity on the same fresh-feed basis.

These classes do not predict conversion, yields, kinetics, cracking severity, coke quality, heater
coking, drum cycle, pressure or temperature, fractionator performance, recycle, product properties,
fuel composition, emissions, blowdown, decoking, or safe operability. This is not a furnace design
or fired-equipment rating. Qualify all scenario inputs independently for the feed,
equipment, and operating envelope.

## Equations

For fresh-feed mass flow \(F\), conversion \(X\), and converted-feed fractions \(y_i\):

\[
F_{\mathrm{converted}}=FX, \qquad F_i=FXy_i, \qquad
F_{\mathrm{unconverted}}=F(1-X), \qquad \sum_i y_i=1.
\]

For specific process duty \(q\), furnace efficiency \(\eta\), fuel lower heating value \(LHV\),
and steam intensity \(s\):

\[
Q_{\mathrm{process}}=Fq, \qquad
\dot m_{\mathrm{fuel}}=\frac{Fq}{\eta LHV}, \qquad
\dot m_{\mathrm{steam}}=\frac{Fs}{1000}.
\]

## Java example

```java
RefineryDelayedCokingYieldBalance yield = RefineryDelayedCokingYieldBalance.calculate(
    1000.0, 0.70, 0.08, 0.18, 0.32, 0.22, 0.20);
RefineryDelayedCokingUtilityBalance utility = RefineryDelayedCokingUtilityBalance.calculate(
    yield, 2.0, 0.85, 45.0, 50.0);
```

This synthetic acceptance case gives 700 kg/h converted feed and 300 kg/h unconverted residue.
The converted products are 56 kg/h gas, 126 kg/h naphtha, 224 kg/h LCGO, 154 kg/h HCGO, and
140 kg/h coke. The utility receipt gives 0.555555556 MW useful process duty,
52.287581699 kg/h fuel, and 50 kg/h steam, with mass and useful-energy residuals zero to numerical
precision.

## Public provenance

- U.S. EPA AP-42 section 5.1 describes coking as thermal cracking of residual oil to petroleum
  coke, gas oils, and lighter stocks, and describes the delayed-coker heater, drums, steam injection,
  and fractionator: https://gaftp.epa.gov/ap42/ch05/s01/final/c05s01_jan1995.pdf
- U.S. Department of Energy, *Factors Affecting U.S. Petroleum Refining*, describes public refinery
  conversion pathways and delayed-coking product families:
  https://www.energy.gov/sites/default/files/2022-11/1973-Factors_Affecting_US_Petroleum_Refining-Impact_of_New_Tech.pdf

No proprietary assay, commercial-simulator data, fitted correlation, or copyrighted table is
embedded. The numerical example is transparent synthetic scenario data and is not a literature
yield benchmark.

## Validation and use

Require converted-feed fractions to sum to one. Verify converted-product and overall mass
residuals and the utility useful-energy residual. Test nearby conversion, efficiency, and intensity
assumptions only after their physical range has been independently justified. This evidence-ladder
level is analytical conservation and dimensional consistency. It is not evidence that a delayed coker operating point is feasible or safe.
