---
title: Non-mutating S8 stream-application preview
description: Apply a verified S8 addition to a detached stream and fluid clone
---

# Non-mutating S8 stream-application preview

`AqueousHydrogenSulfideOxidationS8StreamApplicationPreview` lifts the verified component-level
preview to a caller-owned `StreamInterface`. It deep-clones the stream, verifies that the cloned
fluid does not alias the original, and installs only a defensively cloned candidate fluid on the
detached stream. The original stream and its original fluid are never mutated.

The implementation delegates the component addition to
`AqueousHydrogenSulfideOxidationS8ComponentApplicationPreview`. A strict append adds only the
plan's verified `S8` amount in mol and performs `init(0)` for component bookkeeping. The stream
preview then re-verifies the installed candidate with
`AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt`, including target/application
identity, prior and candidate `S8` amounts, total-mole closure, and preservation of every non-`S8`
component.

```java
AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.Result preview =
    AqueousHydrogenSulfideOxidationS8StreamApplicationPreview.applyToClone(
        plan,
        "detached-stream-before-segment-42",
        "preview-stream-ledger-delta-42",
        priorStream);

StreamInterface candidateStream = preview.getCandidateStream();
AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result evidence =
    preview.getApplicationReceipt();
```

Every `getCandidateStream()` call returns a fresh stream clone with a fresh fluid clone. A caller
may therefore inspect or mutate the returned copy without altering the stored preview or the
caller-owned prior stream. An unchanged ledger transition still returns an independent stream and
fluid while preserving the inventory exactly.

## Capability boundary

The candidate is a detached snapshot, not a committed stream update. The preview does not run the
stream, call `runTPflash()`, add the stream to a `ProcessSystem`, replace an equipment inlet or
outlet, or mutate a process, transient, pipeline, or injection model. It does not authenticate a
target, reserve or atomically consume the application idempotency key, and it does not claim
exactly-once execution.

The preview performs no TP or solid flash and does not prove thermodynamic stability or process
feasibility. A process owner must explicitly select, flash, validate, and connect any returned
candidate before use.

The upstream `S8` allocation remains a caller-owned scenario rather than measured sulfur yield.
No product selectivity, O2 consumption, reaction heat, pH or high-pressure correction,
precipitation kinetics, deposition rate, plugging risk, corrosion prediction, hydraulics, or
equipment loading is introduced. The ownership boundaries with #3144, #2937, #2911, pipeline
work, and #3153 remain unchanged.
