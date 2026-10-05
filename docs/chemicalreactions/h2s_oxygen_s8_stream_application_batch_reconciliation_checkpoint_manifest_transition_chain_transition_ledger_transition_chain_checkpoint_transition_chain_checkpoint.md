---
title: S8 checkpoint-transition-chain checkpoints
description: Deterministic immutable checkpoints of qualified S8 checkpoint-transition chains.
---

# S8 checkpoint-transition-chain checkpoints

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint`
freezes one already-qualified checkpoint-transition chain as an immutable checkpoint. The caller owns the
checkpoint-transition-chain checkpoint identifier and its non-negative sequence.

## Checkpoint contract

`create(identifier, sequence, chain)` requires the exact versioned upstream schema. It retains the upstream
checkpoint-transition-chain, checkpoint-series, transition-chain, ledger, chain, and manifest identities. It also
retains the first and final checkpoint sequences, their exact aggregate sequence delta, the first and final raw
checkpoint and transition-chain digests, the ledger, chain, and manifest endpoints, every aggregate count, and the
upstream chain digest.

The capability fails closed unless the transition count is positive, every count is non-negative, strict-append and
unchanged receipt counts close to the transition count, nested transition and represented-entry state counts close
exactly, and the final-minus-first checkpoint sequence equals the recorded positive sequence delta. Required digests
must each contain exactly 32 bytes.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint.Result
    checkpoint =
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint
            .create("checkpoint-transition-chain-checkpoint-A", 31L, chain);
boolean valid =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransitionChainCheckpoint
        .verify("checkpoint-transition-chain-checkpoint-A", 31L, chain, checkpoint);
```

The versioned canonical SHA-256 digest binds the caller-owned identifier and sequence, upstream schema and identities,
checkpoint-sequence closure, raw checkpoint and transition-chain digests, endpoint digests, all aggregate counts, and
the raw upstream chain digest. Verification reconstructs the checkpoint and compares raw digests in constant time.
Every exposed byte array is a defensive copy.

## Evidence boundary

This is immutable evidence packaging only. It does not replay or requalify checkpoint transitions, transition chains,
ledgers, manifests, reconciliations, or stream evidence. It allocates no identifier or sequence and provides no clock,
persistence, database, digital signature, authentication, authorization, transaction, compare-and-swap, atomic commit,
replay store, or exactly-once delivery guarantee.

The capability introduces no sulfur yield, reaction selectivity, oxidation rate, oxygen demand, solubility,
precipitation, deposition, corrosion, phase, hydraulic, or transport assumption. It performs no flash calculation and
no stream, fluid, process, pipeline, or injection mutation. The scientific and numerical boundary remains inherited
unchanged from the already-qualified #3318 evidence chain.
