---
title: Verified S8 component-addition plan
description: A non-mutating target-scoped plan derived from a verified S8 ledger transition
---

# Verified S8 component-addition plan

`AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan` is the last non-mutating evidence object before a separately qualified executor may change a thermodynamic state. It re-verifies a complete prior/candidate ledger transition and binds the verified increment to a target-state identifier, an application idempotency key, and the expected prior `S8` amount in that target.

NeqSim's `SystemInterface.addComponent(String, double)` accepts the component amount in moles. The plan therefore carries the already qualified ledger delta in mol and kmol and constructs the expected post-application amount as

```text
n_candidate = n_prior + delta_n_S8
delta_n_reconstructed = n_candidate - n_prior
r_n = delta_n_S8 - delta_n_reconstructed
```

All amounts and the closure residual must be finite. Prior and candidate amounts must be non-negative. A positive increment that disappears at the scale of the prior target amount fails closed. An unchanged ledger transition preserves the prior amount exactly and reports that no mutation is required.

## Usage

```java
AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.Result plan =
    AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan.create(
        priorLedger,
        candidateLedger,
        transitionReceipt,
        "pipeline-state-before-segment-42",
        "apply-ledger-delta-42",
        priorS8AmountMol);
```

The returned plan retains the ledger and product-basis identifiers, both canonical ledger checkpoints, the transition digest, the target and application identifiers, the prior amount, the verified increment, the candidate amount, and the numerical closure evidence.

## Capability boundary

The plan does not authenticate the target, inspect its actual pre-state, reserve or persist the idempotency key, provide locking or compare-and-set semantics, or guarantee exactly-once execution. It does not call `SystemInterface.addComponent`, run a flash, create a solid phase, model deposition or corrosion, or mutate a process, stream, pipeline, or thermodynamic system.

A future executor must independently verify the target pre-state and target-state identifier, atomically consume the application key, apply the molar increment once, then reflash and validate material closure. Those responsibilities require their own capability contract and validation.

The upstream allocation of oxidized sulfur to `S8` remains an explicit caller scenario, not a measured sulfur yield. This capability introduces no product selectivity, H2S/O2 stoichiometry, oxygen consumption, reaction heat, pH or high-pressure equilibrium correction, precipitation kinetics, deposition model, or corrosion prediction.

The boundary remains coordinated with #3144, #2937, #2911, pipeline work, and #3153: this class supplies immutable chemistry evidence only and does not duplicate or take ownership of their process, transport, deposition, or solid-flash behavior.
