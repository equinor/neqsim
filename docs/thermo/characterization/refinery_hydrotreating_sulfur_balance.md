---
title: Hydrotreating sulfur and hydrogen balance
description: Build an auditable stoichiometric screening receipt from refinery-assay sulfur data.
---

# Hydrotreating sulfur and hydrogen balance

`RefineryHydrotreatingSulfurBalance` creates an immutable material-balance receipt for an
early hydrotreating screen. It connects the sulfur carried by an
`OilAssayCharacterisation` to an explicit hydrogen-use assumption and a requested sulfur
mass fraction in the calculated liquid product.

This is accounting, not a reactor model. It does not predict kinetics, catalyst performance,
pressure, temperature, heat duty, recycle, hydrogen availability, liquid yield, or regulatory
compliance.

## Basis and equations

The caller supplies:

- feed mass (F), kg;
- feed sulfur mass fraction (z_S);
- target product sulfur mass fraction (x_S); and
- hydrogen consumption ratio (
u), mol H2 per mol sulfur removed, with (
u ge 1).

The class uses (M_{H2}=0.00201588) kg/mol and derives
(M_S=M_{H2S}-M_{H2}) from the public NIST hydrogen-sulfide molecular weight
(M_{H2S}=0.034081) kg/mol. For sulfur removal (R_S), the liquid-mass change per
kilogram of sulfur removed is

[
a = rac{
u M_{H2} - M_{H2S}}{M_S}.
]

Because the target is defined on the calculated product mass, the closed-form sulfur removal is

[
R_S = rac{F z_S - x_S F}{1 + x_S a}.
]

The receipt then reports hydrogen consumed, hydrogen sulfide produced, hydrogen retained in the
liquid, product mass, remaining sulfur, achieved product sulfur fraction, total-mass residual,
and sulfur residual. Invalid or non-closing inputs fail closed.

## Java example

The DOE/OEDI Big Hill assay reconstruction already qualified in NeqSim gives a bulk sulfur mass
fraction of `0.0040867518`. The 15 ppm target and (
u=2.0) mol H2/mol S below are
illustrative screening assumptions, not literature-derived design values.

```java
RefineryHydrotreatingSulfurBalance receipt =
    RefineryHydrotreatingSulfurBalance.calculate(
        1000.0, 0.0040867518, 15.0e-6, 2.0);

double productMassKg = receipt.getProductMassKg();
double hydrogenConsumedKg = receipt.getHydrogenConsumedMassKg();
double hydrogenSulfideKg = receipt.getHydrogenSulfideProducedMassKg();
double achievedSulfur = receipt.getAchievedProductSulfurMassFraction();
```

For this 1000 kg screening basis, the receipt removes 4.071809037 kg sulfur, consumes
0.511975530 kg hydrogen, produces 4.327796802 kg hydrogen sulfide, retains 0.255987765 kg
hydrogen in the liquid, and returns 996.184178728 kg product at 15 ppm sulfur. Both total-mass
and sulfur closures are numerically zero.

Use `calculateForAssay(...)` to read `getBulkSulfurMassFraction()` directly from an assay
without mutating its cuts or thermodynamic system.

## Provenance and engineering boundary

- The public [DOE/OEDI Big Hill crude-oil assay](refinery_assay) supplies the reconstructed feed
  sulfur used for qualification.
- The US Energy Information Administration defines
  [catalytic hydrotreating](https://www.eia.gov/tools/glossary/index.php?id=Catalytic+hydrotreating)
  as contacting petroleum fractions with hydrogen in the presence of a catalyst to remove
  sulfur and other contaminants.
- The AIChE public
  [hydrotreating overview](https://www.aiche.org/sites/default/files/cep/20211029.pdf)
  describes sulfur conversion to hydrogen sulfide in refinery hydrotreating.
- The
  [NIST Chemistry WebBook entry for hydrogen sulfide](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H2S/h1H2)
  supplies the formula and molecular weight used by the receipt.

The H2/S ratio must be selected and documented by the engineer for the intended screening case.
A design or compliance claim requires a separately validated reaction model, feed contaminant
specification, reactor and catalyst basis, operating envelope, product-yield model, and plant
hydrogen/recycle system.
