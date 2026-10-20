---
title: Catalytic reforming stoichiometric balance
description: Build an auditable naphthene/paraffin-to-aromatic and hydrogen screening receipt.
---

# Catalytic reforming stoichiometric balance

`RefineryCatalyticReformingStoichiometricBalance` creates an immutable material-balance
receipt for one carbon number. `RefineryCatalyticReformingFeedBalance` composes those receipts
across a carbon-number distribution while preserving every cut result. Both represent
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

## Multi-cut feed aggregation

The feed-level receipt accepts aligned arrays of unique carbon numbers, naphthene and paraffin
flows, and independently justified conversions. It retains one immutable cut receipt per row and
reports total feed, idealized reformate, aromatics, hydrogen, kg/t-feed intensities, and mass and
atom closure. Returned arrays are defensive copies. Empty, mismatched, duplicated, non-finite,
negative, or zero-total rows fail closed.

```java
RefineryCatalyticReformingFeedBalance feedReceipt =
    RefineryCatalyticReformingFeedBalance.calculate(
        new int[] {6, 8},
        new double[] {1.0, 2.0},
        new double[] {2.0, 3.0},
        new double[] {0.50, 1.00},
        new double[] {0.25, 1.00});

double reformateKgPerHour = feedReceipt.getReformateMassFlowKgPerHour();
double reformateYield = feedReceipt.getReformateMassYieldFraction();
double aromaticMassFraction = feedReceipt.getAromaticMassFractionOfReformate();
double hydrogenKgPerTonne = feedReceipt.getHydrogenProductKgPerTonneFeed();
```

This analytical two-cut case closes 823.63844 kg/h of feed and total products. It reports
780.29702 kg/h idealized reformate, 608.95064 kg/h aromatics, and 43.34142 kg/h hydrogen.
The reformate mass yield is 0.9473780995, its aromatic mass fraction is 0.7804087731, and
hydrogen production is 52.621900454 kg/t feed. These results are sums of the represented
reactions, not a calibrated naphtha-reformer yield slate.

## Hydrogen recovery allocation

`RefineryCatalyticReformingHydrogenRecoveryBalance` partitions the feed receipt's produced
hydrogen into recovered and unrecovered amounts with one explicit caller-owned recovery fraction.
It reports molar and mass flows, kg/t-feed intensities, hydrogen-allocation residuals, and the
overall feed-to-reformate-plus-hydrogen mass residual.

```java
RefineryCatalyticReformingHydrogenRecoveryBalance hydrogenRecovery =
    RefineryCatalyticReformingHydrogenRecoveryBalance.calculate(feedReceipt, 0.80);

double recoveredHydrogenKgPerHour =
    hydrogenRecovery.getRecoveredHydrogenMassFlowKgPerHour();
double recoveredHydrogenKgPerTonne =
    hydrogenRecovery.getRecoveredHydrogenKgPerTonneFeed();
```

For the analytical C6/C8 feed receipt, an assumed 80% recovery allocates the produced
43.34142 kg/h hydrogen into 34.673136 kg/h recovered and 8.668284 kg/h unrecovered hydrogen.
The recovered intensity is 42.097520363 kg/t feed, and hydrogen molar, hydrogen mass, and overall
mass residuals are numerically zero.

## Provenance and engineering boundary

- The US Department of Energy Office of Science report
  [Basic Research Needs: Catalysis for Energy](https://science.osti.gov/-/media/bes/pdf/reports/files/cat_rpt.pdf)
  identifies alkane dehydrocyclization to aromatics among the principal naphtha-reforming
  reactions and describes catalytic reforming as a net hydrogen producer.
- The US EPA public
  [Petroleum Refining Industry sector notebook](https://archive.epa.gov/sectors/web/pdf/petrefsn.pdf)
  distinguishes naphthene dehydrogenation and paraffin dehydrocyclization from isomerization and
  hydrocracking and notes hydrogen release from the represented reactions.

A single-cut or multi-cut conversion vector is a scenario input and must be selected and
documented by the engineer. Each multi-cut row must map to a defensible feed-family carbon-number
inventory; the aggregator does not infer that inventory from TBP data or pseudo-components.
A hydrogen recovery fraction is likewise a caller-owned allocation assumption. The recovery
receipt does not predict separator performance, product purity, compression, fuel use, hydrogen
network allocation, or losses other than the explicit unrecovered amount.
A design, yield, octane, energy, emissions, or product-quality claim requires separately validated
feed-family characterization, competing-reaction network, thermodynamics and kinetics, catalyst
and deactivation model, operating envelope, heat balance, separator/recycle system, and independent
plant or literature validation.
