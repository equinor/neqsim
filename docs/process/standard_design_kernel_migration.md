---
title: Migrate process design to typed standard kernels
description: Replace global standard editions, metadata-only factories, mutable calculators, and envelope-only case execution with the typed process-design workflow.
---

# Migrate process design to typed standard kernels

The typed workflow is additive. Existing mechanical-design classes continue to work, but new or
migrated code should make editions, applicability, readiness, calculation status, cases, and
qualification evidence explicit.

## 1. Replace global edition overrides

Process-global overrides are deprecated because concurrent projects can observe each other's mutable
state. Carry the edition and amendments with the design instead:

```java
// Legacy compatibility only
StandardRegistry.setVersionOverride(StandardType.API_610, "13th Ed");

// Reproducible project selection
StandardEdition edition = StandardEdition.of(StandardType.API_610, "13th Ed",
    Collections.singletonList("Project amendment A"));
StandardSelection selection = StandardSelection.strict(edition);
```

`StandardSelection.legacy(...)` is a temporary compatibility bridge. It preserves permissive factory
behavior and must not be interpreted as calculation support.

## 2. Separate metadata factories from executable calculations

`StandardRegistry.createStandard(...)` creates the legacy category-specific metadata/calculation
object. It does not prove that the selected edition has an executable implementation. Require the
typed kernel when a calculation is needed:

```java
EquipmentDesignKernel<?, ?> kernel = StandardRegistry.requireDesignKernel(selection);
```

The call fails with `KERNEL_NOT_IMPLEMENTED` or `EDITION_NOT_IMPLEMENTED` instead of returning a
metadata object that appears usable. The concrete kernel then checks equipment applicability and
input readiness before calculation.

## 3. Snapshot mutable calculator inputs

API 610 and API 617 adapters accept configured legacy calculators for compatibility, but their input
constructors snapshot the scalar configuration. Configure first, construct the typed input, and do
not reuse a calculated legacy object as uncontrolled evidence. API 521 similarly copies the protected
item basis. Returned assessments and collections are immutable snapshots.

Every caller must branch on `EngineeringCalculationResult.Status`. A
`CALCULATED_REVIEW_REQUIRED` result contains findings; it is not certification. `BLOCKED` retains
structured readiness findings, and `FAILED` identifies an execution failure.

## 4. Migrate design cases and envelopes

The envelope-only `DesignCaseEngine.run(...)` entry point is deprecated. Build one deterministic
`EngineeringCaseSet` and use `EngineeringCaseRunner`:

```java
EngineeringCaseRunReport report = EngineeringCaseRunner.run(process, caseSet,
    EngineeringCaseRunOptions.builder()
        .failurePolicy(EngineeringCaseFailurePolicy.THROW_WITH_PARTIAL_RESULT)
        .build());
```

Use `report.isComplete()` before treating the envelope as a closed basis. `isAccepted()` additionally
requires acceptance limits for every governing metric and no violations. An exception thrown under
`THROW_WITH_PARTIAL_RESULT` retains the report for diagnosis and audit.

For an `EngineeringProject`, use `EngineeringSimulationRunner.buildCaseSet(project)` so the detailed
runner, closed design loop, and deliverable compiler use the same case identity and ordering.

## 5. Run regression and qualification gates

`StandardDesignKernelVerificationSuite.evaluateRegression()` executes the five registered kernels,
numeric API baselines, API 526 SI/customary equivalence, and API 12J metre/micrometre equivalence.
Require `areAllBenchmarksPassed()` in regression CI. Do not use that flag as qualification:
`isPassed()` requires independently reviewed, non-regression evidence for every exact
`method@version`.

## Recommended deployment order

1. Inventory legacy standards and record explicit editions and amendments.
2. Introduce strict selections and resolve catalog-only, edition, and applicability failures.
3. Move calculation calls behind typed kernels without deleting the legacy configuration path.
4. Replace envelope-only execution and require complete case reports.
5. Add project limits, independent benchmark datasets, applicability envelopes, and approvals.
6. Remove use of global overrides only after serialized projects and integrations have migrated.

The complete runnable API 526 path is in
`examples/neqsim/process/design/StandardDesignKernelMigrationExample.java`.
