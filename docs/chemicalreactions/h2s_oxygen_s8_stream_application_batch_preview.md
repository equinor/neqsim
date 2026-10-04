---
title: Ordered S8 stream-application batch preview
description: Preview verified S8 additions across detached stream snapshots
---

# Ordered S8 stream-application batch preview

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview` groups an ordered set of
independently verified S8 stream additions into one fail-closed preview. Each request captures an
independent stream and fluid snapshot before batch evaluation. The caller-owned streams and fluids
are never mutated.

Every entry delegates to `AqueousHydrogenSulfideOxidationS8StreamApplicationPreview`. Source order
is preserved, target-state identifiers must be unique, and application idempotency keys must be
unique. A null, invalid, duplicate, or non-cloneable entry fails the complete batch without
exposing a partial result.

```java
List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request> requests =
    Arrays.asList(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request.create(
            firstPlan, firstTargetIdentifier, firstApplicationKey, firstPriorStream),
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Request.create(
            secondPlan, secondTargetIdentifier, secondApplicationKey, secondPriorStream));

AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.Result preview =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchPreview.applyToClones(requests);
```

The result reports strict-append and unchanged counts, ordered per-stream application evidence,
the sum of planned S8 increments, the sum observed in the detached candidate fluids, and their
closure residual. Aggregate closure is checked against an ULP-scaled tolerance. Every per-entry
candidate getter continues to return a fresh stream and fluid clone.

## Capability boundary

This is a detached multi-stream snapshot, not an atomic transaction, control-volume integrator, or
pipeline solver. It does not run or flash streams, attach candidates to a `ProcessSystem`, replace
equipment connections, mutate a pipeline or injection model, or derive residence time, water
holdup, or phase appearance. It does not persist, reserve, or consume application keys and does not
claim exactly-once execution.

All chemistry remains inherited from the already qualified Millero-derived ledger and explicit
caller-owned S8 allocation. The batch adds no product yield, O2 consumption, reaction heat,
thermodynamic-stability claim, precipitation, deposition, corrosion, hydraulic, wall/filter, or
transport model. Ownership boundaries with #3144, #2937, #2911, pipeline work, and #3153 remain
unchanged.
