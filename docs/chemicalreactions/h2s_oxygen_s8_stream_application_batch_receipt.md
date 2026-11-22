---
title: Verified S8 stream-application batch receipt
description: Verify externally applied S8 additions across ordered stream snapshots
---

# Verified S8 stream-application batch receipt

`AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt` verifies an ordered set of
externally applied S8 additions from independently captured before and after stream snapshots. It
never mutates, runs, or flashes a caller-owned stream or fluid.

Each request binds an existing target-scoped component-addition plan to the observed target-state
identifier, application idempotency key, prior stream, and candidate stream. Positive applications
require distinct caller-provided before and after stream and fluid objects. Both states are then
captured as independent deep clones.

```java
List<AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request> requests =
    Arrays.asList(
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
            firstPlan, firstTargetIdentifier, firstApplicationKey, firstPriorStream,
            firstCandidateStream),
        AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Request.create(
            secondPlan, secondTargetIdentifier, secondApplicationKey, secondPriorStream,
            secondCandidateStream));

AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.Result receipt =
    AqueousHydrogenSulfideOxidationS8StreamApplicationBatchReceipt.verify(requests);
```

The batch fails closed on null or duplicate identities before returning evidence. Every entry is
verified by `AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt`, so the observed prior
and candidate S8 inventories must match the plan, non-S8 component identities and amounts must be
preserved, and the total-mole change must close on the S8 increment.

The result preserves source order and reports a fresh unmodifiable receipt list, strict-append and
unchanged counts, aggregate planned and observed S8 increments, aggregate observed total-mole
increment, both closure residuals and ULP-scaled tolerances, the preserved non-S8 component count,
and the largest non-S8 inventory residual.

## Numerical contract

Every entry retains the existing 8-ULP application and inventory gates. Aggregate S8 and total
amount tolerances add the qualified per-entry comparison scales and an 8-ULP-per-entry summation
allowance. Values must remain finite; additions and counts must remain non-negative; unchanged
entries retain an exact zero increment.

## Capability boundary

This is verification evidence, not an executor, atomic transaction, idempotency store,
control-volume integrator, or pipeline solver. It does not call `addComponent`, authenticate a
target, reserve or consume a key, attach a stream to a `ProcessSystem`, or replace equipment
connections, and it does not claim exactly-once execution.

All chemistry remains inherited from the already qualified Millero-derived ledger and explicit
caller-owned S8 allocation. The receipt adds no kinetic or thermodynamic model, product yield, O2
consumption, reaction heat, pH calculation, phase-stability claim, precipitation, deposition,
corrosion, hydraulic, wall/filter, or transport coupling. Ownership boundaries with #3144, #2937,
#2911, pipeline work, and #3153 remain unchanged.
