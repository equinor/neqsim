---
title: Non-mutating S8 component-application preview
description: Apply a verified S8 addition to an independent thermodynamic-system clone
---

# Non-mutating S8 component-application preview

`AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview` applies a verified
`AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan` to an independent deep clone of a
caller-owned `SystemInterface`. The original target is never mutated. The result retains a private
candidate snapshot and returns a fresh defensive clone on every `getCandidateTarget()` call.

The preview first matches the observed target-state identifier and application idempotency key to
the plan. For a strict ledger append it adds only the plan's verified `S8` amount in mol to the
clone and runs `init(0)` for component bookkeeping. It then delegates to
`AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt`, which verifies the prior,
candidate, and incremental `S8` amounts, total-mole closure, and preservation of every non-`S8`
component identity and amount. An unchanged transition returns an independent clone without an
addition.

```java
AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.Result preview =
    AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview.applyToClone(
        plan,
        "pipeline-state-before-segment-42",
        "preview-ledger-delta-42",
        priorTarget);

SystemInterface candidate = preview.getCandidateTarget();
AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result evidence =
    preview.getApplicationReceipt();
```

The candidate is a sandboxed working copy. A caller may inspect it or pass its own copy to a
separately owned equilibrium or process workflow. Mutating a returned candidate cannot alter the
stored preview or the caller's prior target.

## Capability boundary

The preview is not a committed application. It does not mutate a stream, process, pipeline, or
injection model. It does not authenticate the target, reserve, persist, or atomically consume the
application idempotency key, and it does not claim exactly-once execution.

`init(0)` initializes composition bookkeeping only; the preview does not run a TP or solid flash,
select a stable phase, or prove that the candidate is thermodynamically stable. An external owner
must explicitly choose and validate any subsequent equilibrium calculation and must control how a
qualified candidate is committed.

The upstream allocation to `S8` remains an explicit caller scenario rather than a measured sulfur
yield. No product selectivity, O2 consumption, reaction heat, pH/high-pressure correction,
precipitation kinetics, deposition rate, plugging risk, or corrosion prediction is introduced.
The ownership boundaries with #3144, #2937, #2911, pipeline work, and #3153 remain unchanged.
