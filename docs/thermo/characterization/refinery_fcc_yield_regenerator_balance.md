---
title: FCC yield and coke-regenerator screening balance
description: Algebraic FCC product-yield and coke-regenerator carbon, air, and dry-flue-gas receipts
---

# FCC yield and coke-regenerator screening balance

`RefineryFccYieldBalance` and `RefineryFccCokeRegeneratorBalance` provide a transparent,
screening-level mass-balance bridge from an FCC feed assumption to five converted-product lumps,
unconverted oil, coke burn, air demand, and dry flue gas. They expose every caller-owned assumption
and return explicit closure residuals.

## Capability boundary

The yield receipt allocates converted feed among dry gas, liquefied petroleum gas (LPG), gasoline,
light cycle oil (LCO), and coke. Unconverted feed remains an oil lump. The regenerator receipt then
allocates coke carbon between carbon dioxide, carbon monoxide, and remaining carbon using supplied
burn and selectivity assumptions.

These classes do not predict conversion, product yields, catalyst activity, riser residence time,
fractionation, product properties, regenerator temperature, heat balance, coke hydrogen or sulfur,
water, NOx, SOx, catalyst fines, or emissions compliance. Treat all inputs as scenario data that
must be qualified independently for the feed, catalyst, equipment, and operating envelope.

## Equations

For feed mass flow \(F\), conversion \(X\), and converted-feed product fractions \(y_i\):

\[
F_{\mathrm{converted}} = F X, \qquad F_i = F_{\mathrm{converted}} y_i,
\qquad F_{\mathrm{unconverted}} = F(1-X), \qquad \sum_i y_i = 1.
\]

For coke flow \(F_{\mathrm{coke}}\), coke carbon fraction \(w_C\), carbon burn fraction \(b\),
and fraction \(s\) of burned carbon forming carbon dioxide:

\[
n_{C,\mathrm{burned}} = \frac{F_{\mathrm{coke}} w_C b}{M_C}, \qquad
n_{CO_2}=s n_{C,\mathrm{burned}}, \qquad
n_{CO}=(1-s)n_{C,\mathrm{burned}}.
\]

The stoichiometric oxygen demand is

\[
n_{O_2,\mathrm{demand}} = n_{CO_2} + \frac{1}{2}n_{CO}.
\]

Supplied oxygen is the demand multiplied by one plus the caller-supplied excess-oxygen fraction.
Dry-air flow follows from the supplied dry-air oxygen mole fraction; nitrogen is inert in this
screening receipt.

## Java example

```java
RefineryFccYieldBalance yield = RefineryFccYieldBalance.calculate(
    1000.0,  // feed, kg/h
    0.75,    // conversion
    0.05,    // dry gas / converted feed
    0.15,    // LPG / converted feed
    0.50,    // gasoline / converted feed
    0.20,    // LCO / converted feed
    0.10);   // coke / converted feed

RefineryFccCokeRegeneratorBalance regenerator =
    RefineryFccCokeRegeneratorBalance.calculate(
        yield,
        0.90,   // coke carbon mass fraction
        0.96,   // coke-carbon burn fraction
        0.90,   // burned carbon forming CO2
        0.2095, // oxygen mole fraction in dry air
        0.10);  // excess oxygen relative to demand
```

For this case the yield receipt returns 750 kg/h converted feed, 250 kg/h unconverted oil,
37.5 kg/h dry gas, 112.5 kg/h LPG, 375 kg/h gasoline, 150 kg/h LCO, and 75 kg/h coke. The
regenerator receipt returns 64.8 kg/h burned carbon, 5.125301807 kmol/h stoichiometric oxygen
demand, 26.910892541 kmol/h dry air, and 841.132994195 kg/h represented dry flue gas. The feed,
converted-product, overall mass, carbon, oxygen, and dry-gas molar residuals close to numerical
precision.

## Public method provenance

The five product-lump boundary follows the public FCC literature convention of representing gas
oil conversion as gas, LPG, gasoline, light cycle oil/heavy oil, and coke lumps. The regenerator
stoichiometry follows the public process description in the U.S. Department of Energy report
*Factors Affecting U.S. Petroleum Refining*, which describes coke deposition in the riser,
air regeneration, carbon monoxide in flue gas, and carbon oxidation to carbon monoxide and carbon
dioxide. No proprietary assay, commercial simulator data, correlation coefficients, or copyrighted
tables are embedded. All numerical values above are transparent synthetic acceptance inputs, not a
validated commercial FCC operating case.

## Engineering acceptance

Before using the result in design or operations, replace the illustrative inputs with traceable,
licensed plant or literature data. Reconcile the five-lump yields to the intended analytical basis,
confirm coke ultimate analysis and burn behavior, extend the balance for hydrogen, sulfur, water,
and heat release where material, and compare against independent unit data. A closed algebraic
receipt is necessary but is not evidence that an FCC operating point is feasible or safe.
