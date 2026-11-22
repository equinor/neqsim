---
title: Hydrotreating nitrogen and ammonia balance
description: Build an auditable hydrodenitrogenation screening receipt from assay total nitrogen.
---

# Hydrotreating nitrogen and ammonia balance

`RefineryHydrotreatingNitrogenBalance` creates an immutable material-balance receipt for an
early hydrodenitrogenation screen. It connects the total nitrogen carried by an
`OilAssayCharacterisation` to explicit hydrogen use and a requested nitrogen mass fraction in the
calculated liquid product.

This is stoichiometric accounting, not a reactor or yield model. It does not identify organic
nitrogen species or predict pathways, kinetics, catalyst performance, severity, phase equilibrium,
separation, heat duty, hydrocarbon cracking, product yield, or regulatory compliance.

## Basis and equations

The caller supplies feed mass `F` in kg, feed nitrogen mass fraction `zN`, target product nitrogen
mass fraction `xN`, and hydrogen consumption `nu` in mol H2 per mol nitrogen removed. The minimum
`nu = 1.5` supplies the three hydrogen atoms in one mole of ammonia. Larger values are explicit
screening assumptions and their excess hydrogen is retained in the liquid bookkeeping balance.

NIST reports ammonia as H3N with molecular weight 17.0305 g/mol. The receipt derives nitrogen
molar mass consistently:

```text
M_N = M_NH3 - 1.5 M_H2
```

For nitrogen removal `R_N`, the liquid-mass change per kilogram of nitrogen removed is:

```text
a = (nu M_H2 - M_NH3) / M_N
```

Because the target is defined on calculated product mass, the closed-form removal is:

```text
R_N = (F zN - xN F) / (1 + xN a)
```

The receipt reports initial, removed and remaining nitrogen; hydrogen consumed; ammonia produced;
hydrogen retained in liquid; product mass; achieved product nitrogen; and total and nitrogen
closure residuals. Invalid or non-closing inputs fail closed.

## Java example

The qualified DOE/OEDI Big Hill assay reconstructs `0.1095129 mass%` bulk nitrogen. The 10 ppm
target and `4.0 mol H2/mol N` below are illustrative caller assumptions, not measured or fitted
design values.

```java
RefineryHydrotreatingNitrogenBalance receipt =
    RefineryHydrotreatingNitrogenBalance.calculate(
        1000.0, 0.001095129, 10.0e-6, 4.0);

double productMassKg = receipt.getProductMassKg();
double hydrogenConsumedKg = receipt.getHydrogenConsumedMassKg();
double ammoniaProducedKg = receipt.getAmmoniaProducedMassKg();
```

On that 1000 kg basis, the receipt removes `1.085135947 kg` nitrogen, consumes
`0.624703028 kg` hydrogen, produces `1.319399583 kg` ammonia, retains `0.390439393 kg`
hydrogen in the liquid bookkeeping balance, and returns `999.305303446 kg` liquid product at
10 ppm total nitrogen. Total-mass and nitrogen residuals are zero within floating-point tolerance.

Use `calculateForAssay(...)` to read `getBulkNitrogenMassFraction()` without mutating the assay,
its cuts, or the thermodynamic system.

## Provenance and engineering boundary

- The public DOE SPR/OEDI Big Hill comprehensive assay supplies the qualified total-nitrogen basis.
- AIChE's public hydrotreating overview states that nitrogen is treated with hydrogen and transformed
  into ammonia gas.
- The [NIST Chemistry WebBook ammonia entry](https://webbook.nist.gov/cgi/cbook.cgi?ID=C7664417&Mask=1)
  supplies formula H3N and molecular weight 17.0305 g/mol.

The engineer must select and document the H2/N ratio and target for the intended screen. A design,
yield, duty, catalyst, product-quality, or compliance claim requires independently validated
nitrogen-species, reaction, feed, reactor, separation, and operating-condition models.
