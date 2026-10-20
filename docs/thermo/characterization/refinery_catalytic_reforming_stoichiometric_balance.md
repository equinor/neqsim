---
title: Catalytic reforming stoichiometric balance
description: Build an auditable naphthene/paraffin-to-aromatic and hydrogen screening receipt.
---

# Catalytic reforming stoichiometric balance

`RefineryCatalyticReformingStoichiometricBalance` creates an immutable material-balance
receipt for the first, deliberately narrow catalytic-reforming screen in NeqSim. It represents
same-carbon-number naphthene dehydrogenation and paraffin dehydrocyclization to an aromatic
product and hydrogen.

This is stoichiometric accounting, not a reactor or reformate-yield model. It does not predict
kinetics, equilibrium, catalyst activity or deactivation, temperature, pressure, heat duty,
hydrocracking, isomerization, coke, octane number, product specification, or plant yield.

## Basis and equations

For carbon number `n >= 6`, the receipt uses these idealized reactions:

```text
naphthene dehydrogenation:  CnH2n     -> CnH(2n-6) + 3 H2
paraffin dehydrocyclization: CnH(2n+2) -> CnH(2n-6) + 4 H2
```

The caller supplies the naphthene and paraffin feed flows in kmol/h and an independently justified
conversion fraction for each family. The result reports reaction extents, unconverted feed,
aromatic and hydrogen product flows, kg/t-feed intensities, and total-mass, carbon-atom, and
hydrogen-atom residuals. Carbon and hydrogen atomic masses are explicit public constants in the
class. Invalid or non-closing inputs fail closed.

## Java example

The following analytical C6 case uses one kmol/h of cyclohexane-family feed and two kmol/h of
hexane-family feed. The 0.50 and 0.25 conversions are illustrative assumptions, not literature
or plant yield claims.

```java
RefineryCatalyticReformingStoichiometricBalance receipt =
    RefineryCatalyticReformingStoichiometricBalance.calculate(
        6, 1.0, 2.0, 0.50, 0.25);

double aromaticsKmolPerHour = receipt.getAromaticProductFlowKmolPerHour();
double hydrogenKmolPerHour = receipt.getHydrogenProductFlowKmolPerHour();
double hydrogenKgPerTonne = receipt.getHydrogenProductKgPerTonneFeed();
double massResidualKgPerHour = receipt.getMassBalanceResidualKgPerHour();
```

The case converts 0.5 kmol/h from each feed family, produces 1.0 kmol/h aromatic and
3.5 kmol/h hydrogen, and retains 0.5 kmol/h naphthene plus 1.5 kmol/h paraffin. Its feed and
total product flows both equal 256.5156 kg/h. The hydrogen result is 7.05558 kg/h, or
27.505461656 kg/t feed; total-mass and atom residuals are numerically zero.

## Provenance and engineering boundary

- The US Department of Energy Office of Science report
  [Basic Research Needs: Catalysis for Energy](https://science.osti.gov/-/media/bes/pdf/reports/files/cat_rpt.pdf)
  identifies alkane dehydrocyclization to aromatics among the principal naphtha-reforming
  reactions and describes catalytic reforming as a net hydrogen producer.
- The US EPA public
  [Petroleum Refining Industry sector notebook](https://archive.epa.gov/sectors/web/pdf/petrefsn.pdf)
  distinguishes naphthene dehydrogenation and paraffin dehydrocyclization from isomerization and
  hydrocracking and notes hydrogen release from the represented reactions.

The two conversions are scenario inputs and must be selected and documented by the engineer.
A design, yield, octane, energy, emissions, or product-quality claim requires separately validated
feed-family characterization, competing-reaction network, thermodynamics and kinetics, catalyst
and deactivation model, operating envelope, heat balance, separator/recycle system, and independent
plant or literature validation.
