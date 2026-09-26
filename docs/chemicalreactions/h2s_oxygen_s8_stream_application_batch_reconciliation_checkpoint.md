---
title: S8 stream-application batch-reconciliation checkpoints
description: Create deterministic integrity fingerprints for ordered reconciliation evidence
---

# S8 stream-application batch-reconciliation checkpoints

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint` creates a
deterministic integrity fingerprint for one complete ordered reconciliation between a detached S8
batch preview and verified external-application evidence.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliation.Result reconciliation = ...;
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint.Result checkpoint =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint
        .create(reconciliation);

boolean unchanged =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint
        .verify(restoredReconciliation, checkpoint);
```

## Canonical integrity contract

The checkpoint uses SHA-256 over a versioned canonical binary encoding. Strings are encoded as
length-prefixed UTF-8, integers retain source order, and every floating-point value is encoded with
`Double.doubleToLongBits`. The resulting 64-character lowercase hexadecimal fingerprint is
independent of Java object-serialization bytes. Verification rebuilds the canonical encoding and
uses constant-time digest comparison.

The encoding covers every ordered target-state identifier, application idempotency key, transition
digest, candidate S8 and total amounts, residual, tolerance, state count, aggregate amount, and
maximum entry residual. Entry reordering, identity or transition replacement, and bitwise
floating-point change therefore produce a different checkpoint.

Before hashing, the implementation rejects empty or null evidence, inconsistent append/unchanged
counts, blank identities, non-finite or negative amounts and tolerances, residuals outside their
tolerances, and stored maximum residuals that do not match the ordered entries.

## Operational boundary

This checkpoint is not a digital signature or authentication mechanism. A trusted caller must
still durably persist the digest and decide how identities are authorized. The API does not write a
database, implement compare-and-swap, consume an idempotency key, or provide exactly-once delivery.
It does not mutate a fluid or stream, call `addComponent`, run a flash, or attach equipment to a
process or pipeline.

The checkpoint does not qualify elemental sulfur as the H2S oxidation product and adds no product
yield, O2 demand, kinetic coefficient, thermodynamic model, phase-stability rule, pH, precipitation,
deposition, corrosion, hydraulic, wall/filter, transport, or injection calculation. It neither runs
`TPSolidflash` nor invokes `SulfurDepositionAnalyser` or `SulfurFilter`.

Electrolyte/speciation ownership remains with #3144, generic phase stability with #2937, transient
state with #2911, pipeline hydraulics with the owning pipeline roadmap, and generic MCP exposure
with #3153.
