---
title: Coupled hydrotreating sulfur and nitrogen balance
description: Solve sulfur and nitrogen removal on one auditable liquid-product mass basis.
---

# Coupled hydrotreating sulfur and nitrogen balance

`RefineryHydrotreatingSulfurNitrogenBalance` creates one immutable material-balance receipt for
simultaneous hydrodesulfurization and hydrodenitrogenation screening. Unlike two independent
single-contaminant calculations, it solves both product targets against the same calculated liquid
product mass.

This is stoichiometric accounting, not a reactor or yield model. It does not identify organic
sulfur or nitrogen species or predict pathways, kinetics, catalyst performance, severity, phase
equilibrium, separation, heat duty, hydrocarbon cracking, product yield, or compliance.

## Coupled basis and equations

The caller supplies feed mass `F`; feed sulfur and nitrogen mass fractions `zS` and `zN`;
target product fractions `xS` and `xN`; and explicit hydrogen assumptions `nuS` and `nuN`
in mol H2 per mole of contaminant removed.

One mole of removed sulfur is booked as one mole of H2S, and one mole of removed nitrogen as one
mole of NH3. The minimum stoichiometric assumptions are `nuS = 1.0` and `nuN = 1.5`.
Additional hydrogen is retained in the liquid bookkeeping balance.

Define the liquid-mass changes per kilogram removed:

```text
aS = (nuS M_H2 - M_H2S) / M_S
aN = (nuN M_H2 - M_NH3) / M_N
```

The common liquid product mass `P` is then:

```text
P = F (1 + aS zS + aN zN) / (1 + aS xS + aN xN)
R_S = F zS - xS P
R_N = F zN - xN P
```

This closed form makes both quality targets and total material closure auditable without iteration.

## Java example

The public DOE SPR/OEDI Big Hill assay reconstructs `0.40867518 mass%` sulfur and
`0.1095129 mass%` nitrogen. The 15 ppm sulfur target, 10 ppm nitrogen target,
`2.0 mol H2/mol S`, and `4.0 mol H2/mol N` are illustrative caller assumptions.

```java
RefineryHydrotreatingSulfurNitrogenBalance receipt =
    RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129,
        15.0e-6, 10.0e-6, 2.0, 4.0);

double productMassKg = receipt.getProductMassKg();
double totalHydrogenKg = receipt.getTotalHydrogenConsumedMassKg();
double hydrogenSulfideKg = receipt.getHydrogenSulfideProducedMassKg();
double ammoniaKg = receipt.getAmmoniaProducedMassKg();
```

On that 1000 kg basis, the receipt returns `995.489447979 kg` liquid product, removes
`4.071819458 kg` sulfur and `1.085174106 kg` nitrogen, consumes `1.136701836 kg` H2,
produces `4.327807878 kg` H2S and `1.319445979 kg` NH3, and achieves 15 ppm sulfur and
10 ppm total nitrogen. Total-mass, sulfur, and nitrogen residuals are zero within
floating-point tolerance.

Use `calculateForAssay(...)` to read both bulk attributes without mutating the assay, its cuts,
or the thermodynamic system.

## Provenance and engineering boundary

- The public DOE SPR/OEDI Big Hill comprehensive assay supplies the qualified sulfur and
  total-nitrogen basis.
- The existing sulfur receipt documents the EIA and AIChE hydrotreating basis and the NIST H2S
  molecular weight.
- The existing nitrogen receipt documents the AIChE nitrogen-to-ammonia basis and NIST ammonia
  molecular weight.

The source workbooks and public references are cited, not redistributed. Engineers must select
targets and H2/S and H2/N assumptions for the intended screen. Process-design claims require
independently validated species, reaction, feed, reactor, separation, recycle, operating-condition,
yield, and product-quality models.
