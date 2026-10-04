---
title: S8 component-amount projection
description: Verified conversion of checkpoint-linked S8 ledger mass deltas to the NeqSim S8 component amount basis.
---

# S8 component-amount projection

`AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.project(...)` is the dimensional seam
between a verified checkpoint-linked S8 ledger transition and NeqSim's existing `S8` component
amount basis. It first re-verifies the complete prior/candidate transition receipt. A mismatched,
divergent, truncated, reordered, or replaced ledger state therefore fails closed before conversion.

## Molecular-weight basis and equations

The projection uses `0.25648 kg/mol`, matching the `S8` row in NeqSim's `COMP.csv` component
database and the existing sulfur-equipment implementation. For the transition's incremental mass,

$n_{S8}=m_{S8}/M_{S8}, \qquad n_{S8,\mathrm{kmol}}=n_{S8}/1000.$

The immutable result carries the ledger and product-basis identities, both state fingerprints, the
transition fingerprint, unchanged/strict-append classification, mass [kg], amount [mol and kmol],
the molecular-weight basis, reconstructed mass, and mass-closure residual. The round trip
`n_S8 M_S8` must close within a strict floating-point bound. An unchanged transition maps exact
zero mass to exact zero mol and kmol.

```java
AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.Result projection =
    AqueousHydrogenSulfideOxidationS8ComponentAmountProjection.project(
        priorLedger, candidateLedger, transitionReceipt);

double amountKmol = projection.getTransferredS8AmountKmol();
String transitionFingerprint = projection.getTransitionDigestHex();
```

## Scientific and integration boundary

This conversion applies only after a caller has explicitly selected the existing `S8`
representation in the upstream allocation and transfer receipts. The database molecular weight
does not qualify the product selection as a measured yield or establish H2S/O2 stoichiometry.

The projection does not infer a rate or residence time, consume oxygen, calculate heat, phase
equilibrium, saturation, nucleation, deposition, capture, corrosion, or equipment loading. It does
not call `SystemInterface.addComponent`, mutate a stream, run `TPSolidflash`,
`SulfurDepositionAnalyser`, or `SulfurFilter`, or update a wall, filter, reactor, process, transient,
or pipeline state. The transition fingerprint is audit evidence, not authentication, persistence,
locking, compare-and-swap, or an exactly-once guarantee. Coordination boundaries remain #3144,
#2937, #2911, pipeline work, and #3153.
