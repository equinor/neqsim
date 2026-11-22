---
title: Hydrotreating hydrogen supply and outlet gas balance
description: Extend a sulfur-removal receipt into an auditable makeup-gas and outlet-gas screen.
---

# Hydrotreating hydrogen supply and outlet gas balance

`RefineryHydrotreatingHydrogenSupplyBalance` composes a qualified
`RefineryHydrotreatingSulfurBalance` with explicit makeup-hydrogen purity, hydrogen supply factor,
and average non-hydrogen molar mass. It reports the makeup-gas requirement and the ideal material
balance of hydrogen, hydrogen sulfide, and the non-hydrogen makeup fraction.

This is material bookkeeping, not a reactor, separator, or recycle model. It does not predict
kinetics, catalyst activity, pressure or temperature effects, hydrogen solubility, phase
equilibrium, purge behavior, compression, heat duty, product yield, emissions, or compliance.

## Basis and equations

The upstream sulfur receipt supplies consumed hydrogen mass and produced hydrogen-sulfide mass.
The caller additionally supplies:

- hydrogen supply factor `fH2`, mol H2 supplied per mol H2 consumed, with `fH2 >= 1`;
- makeup hydrogen mole fraction `yH2`, with `0 < yH2 <= 1`; and
- average molar mass `MnonH2`, kg/mol, for the non-hydrogen makeup fraction.

For consumed hydrogen moles `nH2,cons`, the makeup and outlet balances are:

```text
nH2,in = fH2 * nH2,cons
nmakeup = nH2,in / yH2
nnonH2 = nmakeup - nH2,in
nH2,out = nH2,in - nH2,cons
nout = nH2,out + nH2S + nnonH2
```

The receipt calculates makeup and outlet masses from the explicit component molar masses. Its
overall residual closes liquid feed plus makeup gas against liquid product plus outlet gas. If the
upstream sulfur receipt removes no sulfur, the required makeup and outlet gas are both zero.

## Java example

The example reuses the public DOE/OEDI Big Hill sulfur case qualified by the upstream receipt.
The 90 mol% H2 makeup composition, 1.5 supply factor, and nitrogen-molar-mass non-H2 proxy are
illustrative screening assumptions, not plant data or recommended design values.

```java
RefineryHydrotreatingSulfurBalance sulfur =
    RefineryHydrotreatingSulfurBalance.calculate(
        1000.0, 0.0040867518, 15.0e-6, 2.0);

RefineryHydrotreatingHydrogenSupplyBalance gas =
    RefineryHydrotreatingHydrogenSupplyBalance.calculate(
        sulfur, 1.5, 0.90, 0.0280134);

double makeupMassKg = gas.getMakeupGasMassKg();
double outletMassKg = gas.getOutletGasMassKg();
double outletHydrogen = gas.getOutletHydrogenMoleFraction();
double residualKg = gas.getOverallMassBalanceResidualKg();
```

On this 1000 kg basis, the receipt consumes 253.971233373 mol H2 and supplies
380.956850059 mol H2 in 423.285388954 mol makeup gas. It reports 1.953729586 kg makeup gas and
5.769550859 kg outlet gas. The outlet gas is 3/7 H2, 3/7 H2S, and 1/7 non-H2 on this ideal molar
basis. Overall mass closure is numerical zero.

## Provenance and engineering boundary

- The public [DOE/OEDI Big Hill crude-oil assay](refinery_assay) supplies the upstream sulfur case.
- The US Energy Information Administration defines
  [catalytic hydrotreating](https://www.eia.gov/tools/glossary/index.php?id=Catalytic+hydrotreating)
  as contacting petroleum fractions with hydrogen in the presence of a catalyst.
- The AIChE public
  [hydrotreating overview](https://www.aiche.org/sites/default/files/cep/20211029.pdf)
  describes excess hydrogen and sulfur conversion to hydrogen sulfide.
- The [NIST Chemistry WebBook hydrogen-sulfide entry](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H2S/h1H2)
  provides the H2S molecular weight used upstream.
- The [NIST Chemistry WebBook nitrogen entry](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/N2/c1-2)
  provides the illustrative nitrogen molar mass used as the non-H2 proxy.

The engineer must supply makeup-gas composition and excess hydrogen appropriate to the intended
screen. A process-design claim requires a separately validated reaction/catalyst model, feed
contaminant specification, operating envelope, gas-liquid equilibrium, recycle and purge model,
separator and compressor model, product-yield model, and plant hydrogen-system constraints.
