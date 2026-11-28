---
title: S8 transition-chain checkpoint transitions
description: Deterministic receipts between qualified S8 transition-chain checkpoints.
---

# S8 transition-chain checkpoint transitions

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition`
creates an immutable receipt between two independently qualified transition-chain checkpoints. The caller-owned
checkpoint identifier is treated as a checkpoint-series identity, so both checkpoints must use the same identifier
and the candidate checkpoint sequence must increase strictly.

## Transition contract

`create(priorChain, priorCheckpoint, candidateChain, candidateCheckpoint)` first verifies each checkpoint against its
own chain. Ledger, chain, manifest, and transition-chain identities must remain equal. Every prior ordered
transition-ledger receipt must then be an exact prefix of the candidate chain, including its identities, endpoints,
state flags, aggregate counts, and raw SHA-256 digest compared with `MessageDigest.isEqual`.

The candidate may represent either an unchanged chain at a later checkpoint sequence or a strict append. A strict
append must continue the prior final ledger, chain, and manifest endpoints. Truncation, reordering, replacement,
same-length digest drift, endpoint discontinuity, identity drift, and a non-increasing checkpoint sequence fail
closed.

The receipt reports the exact positive sequence delta and non-negative deltas for transition-ledger receipts, ledger
receipts, underlying transitions, strict-append and unchanged transitions, reconciliations, represented entries, and
strict-append and unchanged entries. All subtraction and count-closure checks use overflow-detecting exact integer
arithmetic. The receipt binds both checkpoint digests, both chain digests, checkpoint sequences, identities,
endpoints, state flags, and count deltas into a versioned canonical SHA-256 digest. Result objects are serializable,
and raw digest accessors return defensive copies.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition.Result
    receipt =
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
            .create(priorChain, priorCheckpoint, candidateChain, candidateCheckpoint);
boolean valid =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReconciliationCheckpointManifestTransitionChainTransitionLedgerTransitionChainCheckpointTransition
        .verify(priorChain, priorCheckpoint, candidateChain, candidateCheckpoint, receipt);
```

## Evidence boundary

This capability qualifies immutable evidence transitions only. It does not replay or requalify underlying ledgers,
manifests, reconciliations, or stream evidence. It allocates no checkpoint identifier or sequence and provides no
clock, persistence, database, digital signature, authentication, authorization, transaction coordination,
compare-and-swap, atomic commit, replay protection, or exactly-once delivery guarantee.

The capability introduces no sulfur yield, reaction selectivity, oxidation rate, oxygen demand, solubility,
precipitation, deposition, corrosion, phase, hydraulic, or transport assumption. It performs no flash calculation and
no stream, fluid, process, pipeline, or injection mutation. The scientific and numerical boundary remains inherited
unchanged from the already-qualified #3318 evidence chain.
