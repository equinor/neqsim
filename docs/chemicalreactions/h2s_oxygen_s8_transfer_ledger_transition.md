---
title: S8 transfer ledger transition receipts
description: Checkpoint-linked audit receipts for exact-prefix H2S oxidation S8 ledger transitions.
---

# S8 transfer ledger transition receipts

This page combines [exact-prefix reconciliation](h2s_oxygen_s8_transfer_ledger_delta.md) with
[canonical integrity checkpoints](h2s_oxygen_s8_transfer_ledger_checkpoint.md). The result links
one validated prior ledger to an unchanged or strict-append successor without applying S8 to a
process model.

## Checkpoint-linked transition contract

`AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(...)` first proves that the
candidate preserves the complete ordered prior prefix. It then checkpoints both states and emits:

- the prior and candidate SHA-256 state fingerprints;
- unchanged or strict-append classification;
- added batch and transfer counts;
- source sulfur-equivalent, transferred S8, and unallocated sulfur-equivalent mass deltas; and
- the cumulative closure-residual delta.

A versioned canonical transition encoding covers both state fingerprints, every identity and
classification field, both counts, and every bitwise floating-point delta. Its own SHA-256
fingerprint therefore identifies one exact state transition.

```java
AqueousHydrogenSulfideOxidationS8TransferLedger.Result prior = ...;
AqueousHydrogenSulfideOxidationS8TransferLedger.Result candidate = ...;
AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.Result transition =
    AqueousHydrogenSulfideOxidationS8TransferLedgerTransition.create(prior, candidate);

boolean strictAppend = transition.isStrictAppend();
String priorFingerprint = transition.getPriorCheckpointHex();
String candidateFingerprint = transition.getCandidateCheckpointHex();
String transitionFingerprint = transition.getTransitionDigestHex();
```

`verify(...)` deterministically recomputes the exact-prefix delta, both checkpoints, all metadata,
and the transition fingerprint. The final digest comparison is constant time. Truncation,
reordering, replacement, ledger/product mismatch, null input, or invalid arithmetic fails closed.

## Audit and scientific boundary

The receipt is deterministic audit evidence, not a database transaction, digital signature,
authentication mechanism, durable store, lock, optimistic-concurrency primitive,
compare-and-swap operation, or exactly-once delivery guarantee. A caller must persist and apply it
inside its own transactional boundary.

No new chemistry is introduced. The receipt does not calculate S8 moles, yield, selectivity,
oxygen demand, reaction heat, phase behavior, saturation, nucleation, deposition, filtering,
corrosion, or wall inventory. It does not add `S8` to a system, run `TPSolidflash`,
`SulfurDepositionAnalyser`, or `SulfurFilter`, or mutate a reactor, stream, process, transient model,
or pipeline. Coordination boundaries remain #3144, #2937, #2911, pipeline work, and #3153.
