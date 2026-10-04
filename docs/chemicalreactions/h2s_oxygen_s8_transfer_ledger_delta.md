---
title: S8 transfer ledger prefix reconciliation
description: Deterministic append-only reconciliation for persisted H2S oxidation S8 accounting ledgers.
---

# S8 transfer ledger prefix reconciliation

This page extends the [persistent S8 transfer accounting ledger](h2s_oxygen_s8_transfer_ledger.md)
with a read-only restart/recovery check. It introduces no kinetic, thermodynamic, or product-yield
parameter and does not apply an accounting result to a process model.

## Exact ordered-prefix contract

`AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(...)` accepts a prior ledger and a
candidate successor. It rebuilds both states through the existing ledger validation path, then
requires:

- identical ledger identifier, `S8` component identity, and caller-defined product-identity basis;
- a candidate batch count no smaller than the prior count; and
- bitwise equality of every field in every prior batch and transfer, in exact source order.

The final condition covers identifiers, selected Millero fit path, source-segment index, duration,
water inventory, allocation and product bases, downstream idempotency keys, rates, masses, and
closure residuals. A candidate that truncates, reorders, or replaces prior evidence fails closed.
The comparison remains deterministic after Java serialization and restoration.

```java
AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ...;
AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ...;
AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.Result delta =
    AqueousHydrogenSulfideOxidationS8TransferLedgerDelta.reconcile(prior, candidate);

boolean unchanged = delta.isUnchanged();
boolean strictAppend = delta.isStrictAppend();
double addedS8MassKg = delta.getTransferredS8MassDeltaKg();
```

## Delta evidence

For an unchanged state, all counts and mass deltas are zero. For a strict append, the immutable
receipt exposes the ordered, unmodifiable suffix of added batches, its transfer count, and exact
candidate-minus-prior differences for:

- source sulfur-equivalent mass;
- transferred S8 mass;
- unallocated sulfur-equivalent mass; and
- cumulative mass-closure residual.

The receipt is accounting evidence only. It does not append the candidate, choose a winner between
divergent writers, write a database, acquire a distributed lock, or provide compare-and-swap or
exactly-once delivery. A caller must still make persistence and concurrency decisions in its own
transactional boundary.

## Scientific and ownership boundary

Every compared transfer retains the explicit lower-rate, nominal, or upper-rate path derived from
Millero et al. (1987), <https://doi.org/10.1021/es00159a003>, and the caller-defined allocation and
product identities. Reconciliation introduces no chemical coefficient and does not qualify
elemental sulfur as the oxidation product.

The result does not calculate S8 moles, product selectivity, oxygen consumption, reaction heat,
phase behavior, solid saturation, nucleation, deposition, filtering, corrosion, or wall inventory.
It does not add `S8` to a system, execute `TPSolidflash`, `SulfurDepositionAnalyser`, or
`SulfurFilter`, or mutate a reactor, stream, process, transient model, or pipeline. Coordination
boundaries remain issues #3144, #2937, #2911, pipeline work, and #3153.
