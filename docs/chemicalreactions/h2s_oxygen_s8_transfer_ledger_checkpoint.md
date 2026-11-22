---
title: S8 transfer ledger integrity checkpoints
description: Deterministic integrity fingerprints for persisted H2S oxidation S8 accounting ledgers.
---

# S8 transfer ledger integrity checkpoints

This page extends the [S8 transfer-ledger prefix reconciliation](h2s_oxygen_s8_transfer_ledger_delta.md)
with compact integrity evidence for a persisted ledger state. It introduces no kinetic,
thermodynamic, or product-yield parameter and does not apply S8 to a process model.

## Canonical checkpoint contract

`AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.create(...)` first rebuilds the supplied
ledger through its existing validation path. It then writes a versioned canonical binary encoding
containing:

- length-prefixed UTF-8 ledger, component, product, batch, allocation, and idempotency identities;
- ordered batch and transfer counts;
- the selected Millero fit-path enum ordinal; and
- every mass, rate, duration, water-inventory, and closure value through
  `Double.doubleToLongBits`.

The SHA-256 result is exposed as a 64-character lowercase hexadecimal fingerprint. The encoding is
independent of Java object-serialization bytes, so a serialized and restored ledger produces the
same checkpoint.

```java
AqueousHydrogenSulfideOxidationS8TransferLedger.Result restoredLedger = ...;
AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.Result checkpoint =
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.create(restoredLedger);

String fingerprint = checkpoint.getDigestHex();
boolean unchanged =
    AqueousHydrogenSulfideOxidationS8TransferLedgerCheckpoint.verify(
        restoredLedger, checkpoint);
```

Verification uses constant-time digest comparison. An append, reorder, replacement, identifier
change, fit-path change, idempotency-key change, or bitwise floating-point change produces a
different checkpoint.

## Integrity boundary

The fingerprint is deterministic integrity evidence, not a digital signature or authentication
mechanism. A caller must durably persist the ledger and checkpoint inside its own transactional
boundary. This class does not write a database, acquire a lock, coordinate writers, provide
compare-and-swap, or guarantee exactly-once delivery.

The checkpoint does not qualify elemental sulfur as the oxidation product and does not calculate
S8 moles, product selectivity, oxygen consumption, reaction heat, phase behavior, saturation,
nucleation, deposition, filtering, corrosion, or wall inventory. It does not add `S8` to a system,
run `TPSolidflash`, `SulfurDepositionAnalyser`, or `SulfurFilter`, or mutate a reactor, stream,
process, transient model, or pipeline. Coordination boundaries remain issues #3144, #2937, #2911,
pipeline work, and #3153.
