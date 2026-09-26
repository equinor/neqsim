---
title: S8 reconciliation-checkpoint manifests
description: Bind named reconciliation checkpoints into one ordered integrity manifest
---

# S8 reconciliation-checkpoint manifests

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest`
binds caller-defined reconciliation identifiers and qualified reconciliation checkpoints into one
ordered integrity manifest.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Entry entry =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .entry("reconciliation-2026-09-26-A", reconciliation);

AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest.Result manifest =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifest
        .create("manifest-2026-09-26", Collections.singletonList(entry));
```

## Ordered identity and integrity contract

Each entry is created directly from qualified reconciliation evidence. The API delegates validation
and canonical checkpoint creation to
`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpoint`, then binds the
result to a non-blank caller-defined reconciliation identifier. A manifest rejects null or empty
input, null entries, duplicate reconciliation identifiers, duplicate checkpoint digests, and
integer count overflow.

The manifest uses SHA-256 over a versioned canonical binary encoding. It covers the manifest
identifier, ordered reconciliation identifiers, checkpoint schema and algorithm identifiers, raw
checkpoint digests, state counts, and checkpoint summary values. Strings use length-prefixed UTF-8
and floating-point values use `Double.doubleToLongBits`. Reordering entries, renaming an entry,
changing the manifest identity, or changing any checkpoint therefore changes the 64-character
lowercase hexadecimal fingerprint. Verification rebuilds the canonical representation and uses
constant-time digest comparison.

The result returns a fresh unmodifiable entry list and defensive digest-byte copies. It reports
reconciliation, represented-entry, strict-append, and unchanged counts. It deliberately does not
sum sulfur masses or claim that separately named reconciliations are chemically disjoint.

## Operational boundary

This manifest is not a durable ledger, database, digital signature, authentication mechanism,
compare-and-swap transaction, idempotency store, or exactly-once guarantee. A trusted caller must
persist and authorize the manifest identifier, reconciliation identifiers, and digest. The API
does not retain or replay the underlying stream-application evidence.

The manifest does not mutate a fluid or stream, call `addComponent`, run a flash, or attach
equipment to a process or pipeline. It does not qualify elemental sulfur as the H2S oxidation
product and adds no product yield, O2 demand, kinetic coefficient, thermodynamic model,
phase-stability rule, pH, precipitation, deposition, corrosion, hydraulic, wall/filter, transport,
or injection calculation. It neither runs `TPSolidflash` nor invokes
`SulfurDepositionAnalyser` or `SulfurFilter`.

Electrolyte/speciation ownership remains with #3144, generic phase stability with #2937, transient
state with #2911, pipeline hydraulics with the owning pipeline roadmap, and generic MCP exposure
with #3153.
