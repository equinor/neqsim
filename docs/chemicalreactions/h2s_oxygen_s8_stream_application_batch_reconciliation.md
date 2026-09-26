---
title: S8 stream-application batch reconciliation
description: Reconcile an ordered detached preview with verified external application evidence
---

# S8 stream-application batch reconciliation

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation` proves that an ordered
batch of externally applied S8 additions matches the previously qualified detached batch preview.
It compares immutable evidence only and never mutates, runs, or flashes a stream or fluid.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.reconcile(
        preview, applicationReceipt);
```

The preview and external-application batches must have the same non-zero size and source order.
Every entry must retain the ledger, product identity, molecular-weight basis, target-state
identifier, application idempotency key, prior and candidate checkpoints, transition digest,
component identity, and strict-append or unchanged state. Reordering fails closed even when both
input batches are independently valid.

Candidate S8 amounts and total amounts are compared entry by entry. The result reports a fresh
unmodifiable ordered entry list, previewed and applied aggregate increments, reconciliation
residuals and ULP-scaled tolerances, strict-append and unchanged counts, and the largest per-entry
candidate residuals.

## Numerical contract

Each preview-versus-application amount comparison uses the existing 8-ULP basis. Aggregate S8 and
total-amount tolerances add the qualified per-entry scales plus an 8-ULP-per-entry summation
allowance. All amounts, residuals, and tolerances must remain finite. Counts and qualified amounts
must remain non-negative.

## Capability boundary

This is evidence reconciliation, not an executor, atomic transaction, idempotency store,
control-volume integrator, or pipeline solver. It does not call `addComponent`, mutate or run a
stream, execute a TP or solid flash, authenticate a target, persist or consume a key, attach a
stream to a `ProcessSystem`, replace equipment connections, or claim exactly-once execution.

All chemistry remains inherited from the already qualified Millero-derived ledger and explicit
caller-owned S8 allocation. Reconciliation adds no kinetic or thermodynamic model, product yield,
O2 consumption, reaction heat, pH calculation, phase-stability claim, precipitation, deposition,
corrosion, hydraulic, wall/filter, or transport coupling. Ownership boundaries with #3144, #2937,
#2911, pipeline work, and #3153 remain unchanged.
