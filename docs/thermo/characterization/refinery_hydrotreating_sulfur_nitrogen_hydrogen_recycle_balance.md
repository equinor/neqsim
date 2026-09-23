---
title: Coupled sulfur/nitrogen hydrotreating hydrogen recycle and purge
description: Close H2, H2S, NH3, and non-H2 fresh, recycle, and export-gas bookkeeping.
---

# Coupled sulfur/nitrogen hydrotreating hydrogen recycle and purge

`RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance` composes a qualified
`RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance` with explicit
component-recovery and common-purge assumptions. It reports steady-state fresh
makeup, reactor-outlet gas, recycle gas, and exported gas for H2, H2S, NH3, and
the aggregate non-H2 makeup fraction.

This is component bookkeeping, not a separator, phase-equilibrium, solubility,
compressor, reactor, or catalyst model. Recovery and purge fractions must come
from an independent engineering basis.

## Basis and equations

The upstream receipt supplies the fixed reactor-inlet H2 target, consumed H2,
once-through unreacted H2, generated H2S and NH3, makeup-H2 purity, and the
average non-H2 molar mass. The caller additionally supplies a recovery fraction
`r_i` for each gas component and one common recovered-gas purge fraction
`p`.

The effective recycle fraction for component `i` is:

```text
k_i = r_i * (1 - p)
```

Hydrogen recycle and fresh hydrogen are calculated from the fixed reactor-inlet
target:

```text
nH2,recycle = k_H2 * nH2,out
nH2,fresh = nH2,in - nH2,recycle
nmakeup,fresh = nH2,fresh / yH2
```

For non-H2 makeup, H2S, and NH3, the steady-state outlet inventory is the fresh
or generated source divided by the net rejection fraction:

```text
n_i,out = n_i,source / (1 - k_i)
n_i,recycle = k_i * n_i,out
n_i,export = n_i,out - n_i,recycle
```

A positive source with `k_i = 1` has no purge or rejection path and therefore
fails closed. Total mass closes fresh liquid feed plus fresh makeup gas against
liquid product plus exported gas; internal recycle cancels from the overall
balance.

## Java example

The example reuses the public DOE SPR/OEDI Big Hill sulfur/nitrogen case. The
90% H2 recovery, 10% H2S recovery, 20% NH3 recovery, 50% non-H2 recovery, and
5% purge are illustrative caller assumptions, not measured plant or separator
performance.

```java
RefineryHydrotreatingSulfurNitrogenBalance material =
    RefineryHydrotreatingSulfurNitrogenBalance.calculate(
        1000.0, 0.0040867518, 0.001095129,
        15.0e-6, 10.0e-6, 2.0, 4.0);

RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance supply =
    RefineryHydrotreatingSulfurNitrogenHydrogenSupplyBalance.calculate(
        material, 1.5, 0.90, 0.0280134);

RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance recycle =
    RefineryHydrotreatingSulfurNitrogenHydrogenRecycleBalance.calculate(
        supply, 0.90, 0.10, 0.20, 0.50, 0.05);

double freshMakeupKg = recycle.getFreshMakeupGasMassKg();
double recycleGasKg = recycle.getRecycleGasMassKg();
double exportGasKg = recycle.getExportGasMassKg();
double ammoniaExportFraction = recycle.getExportAmmoniaMoleFraction();
double massResidualKg = recycle.getOverallMassBalanceResidualKg();
```

On this 1000 kg basis, the effective H2, H2S, NH3, and non-H2 recycle fractions
are 0.855, 0.095, 0.190, and 0.475. The receipt recycles 241.056032540 mol H2,
requires 671.949564390 mol or 3.101471912 kg fresh makeup gas, recycles
333.354743513 mol total gas, and exports 312.537214984 mol or 7.612023933 kg
gas. Recycle replaces 28.5% of the once-through hydrogen supply. H2, H2S, NH3,
non-H2, and overall mass residuals are numerical zero.

A purge fraction of one reproduces the once-through fresh-makeup and outlet-gas
receipt exactly. Zero sulfur and nitrogen removal produces a zero-gas receipt.

## Provenance and engineering boundary

- The public [DOE SPR/OEDI Big Hill crude-oil assay](refinery_assay) supplies
  the qualified upstream sulfur and total-nitrogen basis.
- The US Energy Information Administration defines
  [catalytic hydrotreating](https://www.eia.gov/tools/glossary/index.php?id=Catalytic+hydrotreating)
  as contacting petroleum fractions with hydrogen in the presence of a
  catalyst.
- The AIChE public
  [hydrotreating overview](https://www.aiche.org/sites/default/files/cep/20211029.pdf)
  describes excess hydrogen, sulfur conversion to hydrogen sulfide, and
  nitrogen conversion to ammonia.
- NIST Chemistry WebBook entries provide the identities and molecular weights
  inherited from the upstream receipt for
  [hydrogen sulfide](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H2S/h1H2),
  [ammonia](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/H3N/h1H3), and the
  illustrative [nitrogen](https://webbook.nist.gov/cgi/inchi/InChI%3D1S/N2/c1-2)
  non-H2 proxy.

The conservation equations do not estimate recovery or purge behavior. A
process-design claim requires separately validated reaction/catalyst and
operating-envelope models, gas-liquid equilibrium, H2S/NH3 separation,
hydrogen solubility, compressor and recycle-loop pressure drops, purge routing,
control philosophy, product yield, and plant hydrogen-system constraints.
