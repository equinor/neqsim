---
title: Persistent S8 transfer accounting ledger
description: Immutable cross-batch duplicate-consumption evidence for mass-based H2S oxidation S8 transfer receipts.
---

# Persistent S8 transfer accounting ledger

This page extends the [immutable S8 transfer batch](h2s_oxygen_s8_transfer_batch.md) with a
software-accounting boundary. It does not add a kinetic model, qualify an elemental-sulfur
product yield, or provide a distributed exactly-once service.

## Immutable append-only ledger

`AqueousHydrogenSulfideOxidationS8TransferLedger.create(...)` groups a non-empty ordered list of
existing transfer batches into one immutable result. The ledger:

- requires one caller-supplied ledger identifier;
- requires one shared caller-supplied S8 product-identity basis;
- rejects duplicate batch identifiers;
- rejects duplicate downstream idempotency keys across all batches;
- preserves batch order and exposes unmodifiable defensive collections;
- reports the total source sulfur-equivalent mass, transferred S8 mass, unallocated
  sulfur-equivalent mass, and cumulative closure residual in kg; and
- does not sum segment or batch mass rates because sequential rates are not additive inventories.

The cumulative source mass must equal transferred plus unallocated mass within eight
floating-point units in the last place at the aggregate scale. Unchanged-state segment
subdivision preserves all cumulative mass totals.

```java
List<AqueousHydrogenSulfideOxidationS8TransferBatch.Result> batches = ...;
AqueousHydrogenSulfideOxidationS8TransferLedger.Result ledger =
    AqueousHydrogenSulfideOxidationS8TransferLedger.create(
        batches, "transport-case-A-S8-ledger");
double transferredKg = ledger.getTotalTransferredS8MassKg();
boolean alreadyConsumed = ledger.containsDownstreamIdempotencyKey("segment-17");
```

The public Java API is directly accessible through JPype. The caller must provide a trimmed,
non-empty ledger identifier no longer than 256 characters.

## Restart continuity and concurrency boundary

The result is serializable. After a caller durably persists and restores it,
`AqueousHydrogenSulfideOxidationS8TransferLedger.append(...)` rebuilds and validates the complete
ordered ledger before appending a new batch. A downstream idempotency key already present before
the process restart is therefore rejected.

Persistence remains the caller's responsibility. The class does not write a database, acquire a
distributed lock, coordinate concurrent writers, or provide atomic compare-and-swap semantics.
Callers with multiple processes or distributed consumers must serialize updates or place the
immutable result behind their own transactional store. Within that boundary, each transferred S8
mass may be applied at most once, and its unallocated sulfur-equivalent remainder must be carried
separately. The original source budget must not also be applied as product.

## Scientific evidence and limits

Every batch retains its explicit lower-rate, nominal, or upper-rate path derived from Millero et
al. (1987), <https://doi.org/10.1021/es00159a003>, plus the caller-defined elemental-sulfur
allocation and product-identity basis. Ledger accounting introduces no chemical coefficient.

The result does not:

- calculate S8 moles or introduce an S8 molecular-weight constant;
- qualify elemental sulfur as the oxidation product;
- predict product selectivity, oxygen consumption, reaction heat, pH, or speciation;
- calculate water holdup, pressure effects, phase transfer, saturation, nucleation, or deposition;
- add `S8` to a thermodynamic system or stream;
- execute `TPSolidflash`, `SulfurDepositionAnalyser`, or `SulfurFilter`; or
- mutate a reactor, wall inventory, process, transient model, or pipeline.

Those actions require separately qualified product evidence and remain owned by the existing
thermodynamic, solid-flash, deposition, filter, corrosion, process, and pipeline implementations.
Coordination boundaries remain issues #3144, #2937, #2911, pipeline work, and #3153.
