---
title: Verified S8 component-application receipt
description: Non-mutating verification of an externally applied S8 component-addition plan
---

# Verified S8 component-application receipt

`AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt` checks whether a separately owned executor produced the before/after inventory change prescribed by an `AqueousHydrogenSulfideOxidationS8ComponentAdditionPlan`. It reads two caller-supplied `SystemInterface` objects and returns immutable evidence; it does not mutate either object.

The caller supplies the observed target-state identifier and application idempotency key. Both must exactly match the verified plan. Component inventories are compared by normalized component identity rather than array position, so a pure component-order change is accepted while component substitution is not.

For the planned `S8` component, the verifier requires

```text
n_S8,prior(observed)     = n_S8,prior(plan)
n_S8,candidate(observed) = n_S8,candidate(plan)
delta_n_S8(observed)     = n_S8,candidate - n_S8,prior
r_plan                   = delta_n_S8(plan) - delta_n_S8(observed)
```

Every non-`S8` component identity and amount must remain unchanged within an 8-ULP comparison gate. The candidate-minus-prior total amount must close on the observed `S8` increment. Each system's component sum must also close on `SystemInterface.getTotalNumberOfMoles()` using compensated summation and a component-count-scaled ULP gate. NaN, infinity, negative inventory, a positive application on aliased before/after objects, prior-state mismatch, double application, contamination, and component removal all fail closed.

## Usage

```java
AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.Result receipt =
    AqueousHydrogenSulfideOxidationS8ComponentApplicationReceipt.verify(
        plan,
        "pipeline-state-before-segment-42",
        "apply-ledger-delta-42",
        priorTarget,
        candidateTarget);
```

The receipt records the ledger and product-basis identifiers, molecular-weight basis, checkpoint and transition digests, matched target and application identities, observed prior/candidate `S8` amounts, planned and observed increments, plan-application residual, total-amount closure, preserved non-`S8` component count, and maximum non-`S8` inventory residual.

## Capability boundary

This verifier does not authenticate the target, reserve, persist, atomically consume, or otherwise enforce the application idempotency key. It does not claim exactly-once execution. An external owner must capture trustworthy before/after states and enforce concurrency control.

The class does not call `SystemInterface.addComponent`, run a flash, choose a solid phase, or mutate a thermodynamic system, stream, process, pipeline, or injection model. It is retrospective inventory evidence, not an executor and not proof that a candidate state is physically stable.

The upstream allocation to `S8` remains an explicit caller scenario, not a measured sulfur yield. No product selectivity, O2 consumption, reaction heat, pH/high-pressure correction, precipitation kinetics, deposition rate, plugging risk, or corrosion prediction is added here.

The boundary remains coordinated with #3144, #2937, #2911, pipeline work, and #3153. Those owners retain process, transport, solid-flash, deposition, and application authority; this capability only verifies a bounded component-inventory delta.
