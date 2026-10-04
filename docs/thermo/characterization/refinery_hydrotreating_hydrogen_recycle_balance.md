---
title: Hydrotreating hydrogen recycle and purge balance
description: Close fresh-makeup, recycle, and export gas around a hydrotreating screening receipt.
---

# Hydrotreating hydrogen recycle and purge balance

`RefineryHydrotreatingHydrogenRecycleBalance` composes a qualified
`RefineryHydrotreatingHydrogenSupplyBalance` with explicit recovery and purge assumptions. It
reports steady-state fresh makeup, reactor-outlet gas, recycle gas, and exported gas for hydrogen,
hydrogen sulfide, and the non-hydrogen makeup fraction.

This is component bookkeeping. It is not a separator, phase-equilibrium, solubility, compressor,
reactor, or catalyst model. Recovery fractions must come from an independent engineering basis.

## Basis and equations

The upstream receipt supplies reactor-inlet hydrogen, consumed hydrogen, once-through unreacted
hydrogen, generated hydrogen sulfide, makeup-hydrogen purity, and the average non-hydrogen molar
mass. The caller additionally supplies component recovery fractions `r_i` and one common recovered
gas purge fraction `p`.

The effective recycle fraction for component `i` is:

```text
k_i = r_i * (1 - p)
```

Hydrogen recycle and fresh hydrogen are calculated directly from the fixed reactor-inlet target:

```text
nH2,recycle = k_H2 * nH2,out
nH2,fresh = nH2,in - nH2,recycle
nmakeup,fresh = nH2,fresh / yH2
```

For non-hydrogen makeup and hydrogen sulfide, the steady-state outlet inventory is the fresh or
generated source divided by the net rejection fraction:

```text
n_i,out = n_i,source / (1 - k_i)
n_i,recycle = k_i * n_i,out
n_i,export = n_i,out - n_i,recycle
```

A positive source with `k_i = 1` has no purge or rejection path and therefore fails closed. Total
mass closes fresh liquid feed plus fresh makeup gas against liquid product plus exported gas;
internal recycle cancels from the overall balance.

## Java example

The example reuses the public DOE/OEDI Big Hill sulfur case. The 90% H2 recovery, 10% H2S
recovery, 50% non-H2 recovery, and 5% purge are illustrative assumptions rather than measured
plant or separator performance.

```java
RefineryHydrotreatingSulfurBalance sulfur =
    RefineryHydrotreatingSulfurBalance.calculate(
        1000.0, 0.0040867518, 15.0e-6, 2.0);

RefineryHydrotreatingHydrogenSupplyBalance supply =
    RefineryHydrotreatingHydrogenSupplyBalance.calculate(
        sulfur, 1.5, 0.90, 0.0280134);

RefineryHydrotreatingHydrogenRecycleBalance recycle =
    RefineryHydrotreatingHydrogenRecycleBalance.calculate(
        supply, 0.90, 0.10, 0.50, 0.05);

double freshMakeupKg = recycle.getFreshMakeupGasMassKg();
double recycleGasKg = recycle.getRecycleGasMassKg();
double exportGasKg = recycle.getExportGasMassKg();
double massResidualKg = recycle.getOverallMassBalanceResidualKg();
```

On this 1000 kg basis, the effective H2 recycle fraction is 0.855. The receipt recycles
108.572702267 mol H2, requires 302.649053102 mol and 1.396916654 kg fresh makeup gas, recycles
149.285217505 mol total gas, and exports 175.663436416 mol and 5.212737927 kg gas. Recycle replaces
28.5% of the once-through hydrogen supply. H2, H2S, non-H2, and overall mass residuals are numerical
zero.

A purge fraction of one reproduces the once-through fresh-makeup and outlet-gas receipt exactly.
Zero sulfur removal produces a zero-gas receipt.

## Provenance and engineering boundary

- The public [DOE/OEDI Big Hill crude-oil assay](refinery_assay) supplies the qualified sulfur
  basis inherited from the upstream receipts.
- The US Energy Information Administration defines
  [catalytic hydrotreating](https://www.eia.gov/tools/glossary/index.php?id=Catalytic+hydrotreating)
  as contacting petroleum fractions with hydrogen in the presence of a catalyst.
- The AIChE public
  [hydrotreating overview](https://www.aiche.org/sites/default/files/cep/20211029.pdf)
  describes excess hydrogen and sulfur conversion to hydrogen sulfide.
- The [NIST Chemistry WebBook hydrogen-sulfide entry](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H2S/h1H2)
  supplies the H2S molecular weight inherited from the sulfur receipt.
- The [NIST Chemistry WebBook nitrogen entry](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/N2/c1-2)
  supplies the illustrative non-H2 molar mass used in this public case.

The conservation equations do not estimate recovery. A design claim requires a validated
gas-liquid equilibrium and separator model, reactor pressure and temperature, hydrogen solubility,
compressor and recycle-loop pressure drops, catalyst and reaction models, contaminant behavior,
purge routing, control philosophy, and plant hydrogen-system constraints.
