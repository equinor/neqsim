---
title: Coupled sulfur/nitrogen hydrotreating hydrogen supply
description: Close H2, H2S, NH3, and non-H2 makeup and outlet-gas bookkeeping.
---

# Coupled sulfur/nitrogen hydrotreating hydrogen supply

`RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance` composes a qualified
`RefineryHydrotreatingSulfurNitrogenBalance` with explicit makeup-hydrogen
purity, hydrogen supply factor, and average non-hydrogen molar mass. It reports
one auditable makeup/outlet-gas receipt containing H2, H2S, NH3, and an
aggregate non-H2 fraction.

This is material bookkeeping, not a reactor, separator, or recycle model. It
does not predict phase equilibrium, ammonia or hydrogen-sulfide separation,
kinetics, catalyst activity, pressure or temperature effects, hydrogen
solubility, purge behavior, compression, heat duty, product yield, emissions,
or compliance.

## Basis and equations

The upstream coupled receipt supplies consumed H2 mass and produced H2S and NH3
masses. The caller additionally supplies:

- hydrogen supply factor `fH2`, mol H2 supplied per mol H2 consumed, with
  `fH2 >= 1`;
- makeup hydrogen mole fraction `yH2`, with `0 < yH2 <= 1`; and
- average molar mass `MnonH2`, kg/mol, for the non-hydrogen makeup fraction.

For consumed hydrogen moles `nH2,cons`, the ideal bookkeeping equations are:

```text
nH2,in = fH2 * nH2,cons
nmakeup = nH2,in / yH2
nnonH2 = nmakeup - nH2,in
nH2,out = nH2,in - nH2,cons
nout = nH2,out + nH2S + nNH3 + nnonH2
```

The receipt calculates makeup and outlet masses from explicit component molar
masses. Its overall residual closes liquid feed plus makeup gas against liquid
product plus outlet gas. If the upstream receipt removes neither sulfur nor
nitrogen, required makeup and outlet gas are both zero.

## Java example

The example reuses the public DOE SPR/OEDI Big Hill sulfur/nitrogen case. The
90 mol% H2 makeup composition, 1.5 supply factor, and nitrogen-molar-mass
non-H2 proxy are illustrative caller assumptions, not plant data or
recommended design values.

```java
RefineryHydrotreatingSulfurNitrogenBalance material =
    RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129,
        15.0e-6, 10.0e-6, 2.0, 4.0);

RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance gas =
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(
        material, 1.5, 0.90, 0.0280134);

double makeupMassKg = gas.getMakeupGasMassKg();
double outletMassKg = gas.getOutletGasMassKg();
double outletAmmonia = gas.getOutletAmmoniaMoleFraction();
double residualKg = gas.getOverallMassBalanceResidualKg();
```

On this 1000 kg basis, the receipt consumes 563.873760327 mol H2 and supplies
845.810640491 mol H2 in 939.789600545 mol makeup gas. It reports
4.337722954 kg makeup gas and 580.377251140 mol or 8.848274975 kg outlet gas.
Outlet mole fractions are 0.4857821005 H2, 0.2187989647 H2S, 0.1334915679 NH3,
and 0.1619273668 aggregate non-H2. Overall mass closure is numerical zero.

## Provenance and engineering boundary

- The public [DOE SPR/OEDI Big Hill crude-oil assay](refinery_assay) supplies
  the upstream sulfur and total-nitrogen basis.
- The US Energy Information Administration defines
  [catalytic hydrotreating](https://www.eia.gov/tools/glossary/index.php?id=Catalytic+hydrotreating)
  as contacting petroleum fractions with hydrogen in the presence of a
  catalyst.
- The AIChE public
  [hydrotreating overview](https://www.aiche.org/sites/default/files/cep/20211029.pdf)
  describes excess hydrogen, sulfur conversion to hydrogen sulfide, and
  nitrogen conversion to ammonia.
- NIST Chemistry WebBook entries provide the identities and molecular weights
  used upstream for
  [hydrogen sulfide](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H2S/h1H2),
  [ammonia](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H3N/h1H3), and the
  illustrative [nitrogen](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/N2/c1-2)
  non-H2 proxy.

The engineer must supply makeup-gas composition and excess hydrogen appropriate
to the intended screen. A process-design claim requires separately validated
reaction/catalyst, operating-envelope, gas-liquid equilibrium, separation,
recycle/purge, compressor, product-yield, and plant hydrogen-system models.
