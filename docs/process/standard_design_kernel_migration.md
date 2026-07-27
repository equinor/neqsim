---
title: Migrate process design to typed standard kernels
description: Replace global standard editions, metadata-only factories, mutable calculators, and envelope-only case execution with the typed process-design workflow.
---

# Migrate process design to typed standard kernels

The typed workflow is additive. Existing mechanical-design classes continue to work, but new or
migrated code should make editions, applicability, readiness, calculation status, cases, and
qualification evidence explicit.

## Backward compatibility

The migration preserves the existing public source entry points. Legacy standard factories,
single-argument canonical-unit methods, mechanical-design execution methods, and unconfigured
`DesignOptimizer` fluent calls remain available. The strict selection and typed-kernel APIs are
additive and can be adopted incrementally.

Backward compatibility does not mean identical behavior. Correctness fixes deliberately change
several observable results:

- `DesignOptimizer.optimize()` without explicit search bounds reports `VALIDATED` or
  `AUTO_SIZED` with `isConverged() == false`; it no longer claims that an optimization ran.
- invalid safety factors, null objectives, incompatible shared design updates, unsupported strict
  selections, and incomplete typed-kernel inputs fail earlier;
- system mechanical-design execution retains caller-configured design objects instead of replacing
  them with defaults;
- strict selections distinguish current, superseded, historical, and publisher-unverified editions;
- the API 610 catalog default is the publisher-listed 12th edition, while the existing calculator's
  13th-edition label is retained only as a legacy compatibility basis; and API 521/API 526
  applicability remains restricted to relief-system equipment;
- returned equipment-size collections are defensive snapshots and no longer expose mutable internal
  state.

Existing integrations should therefore continue to compile, but tests or control logic that relied
on the previous permissive outcomes must migrate to explicit result statuses. This compatibility
contract covers public source APIs and normal runtime use; deserializing Java object graphs written
by older NeqSim versions is outside its scope.

## 1. Replace global edition overrides

Process-global overrides are deprecated because concurrent projects can observe each other's mutable
state. Carry the edition and amendments with the design instead:

```java
// Legacy compatibility only; this does not establish a current-edition kernel.
StandardRegistry.setVersionOverride(StandardType.API_610, "13th Ed");

// Fail-closed current selection; blocked until a 12th-edition kernel exists.
StandardSelection current = StandardSelection.strict(StandardType.API_610);

// Exact compatibility basis implemented by the legacy adapter.
StandardEdition legacyEdition = StandardEdition.of(StandardType.API_610, "13th Ed");
StandardSelection historical = StandardSelection.historical(legacyEdition);
```

`StandardSelection.legacy(...)` is a temporary compatibility bridge. It preserves permissive factory
behavior and amendment metadata, and must not be interpreted as calculation support. Executable
strict and historical selections reject amendments unless the selected method explicitly implements
them.

Use `StandardSelection.strictRegistry(...)` only while migrating a current standard whose legacy
category factory has screened calculation support but no exact typed kernel. It verifies catalog
lifecycle, maturity, registry connection, and equipment applicability. It does not make the factory
edition-specific.

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

## 3. Map cross-equipment requirements separately

A standard such as NORSOK P-002 or IEC 61511 is not one equipment formula. Require its immutable
capability map separately from category factories and typed equipment kernels:

```java
StandardSelection requirementsSelection =
    StandardSelection.strictRequirements(StandardType.NORSOK_P_002);
StandardRequirementPack requirements =
    StandardRegistry.requireRequirementPack(requirementsSelection);
```

The pack identifies existing calculation screens and review workflows together with their declared
boundaries. It is not an executable conformity specification. Projects must retain a licensed
requirements register, map every applicable clause, record exclusions and deviations, and obtain
independent approval.

## 4. Snapshot mutable calculator inputs

API 610 and API 617 adapters accept configured legacy calculators for compatibility, but their input
constructors snapshot the scalar configuration. Configure first, construct the typed input, and do
not reuse a calculated legacy object as uncontrolled evidence. API 521 similarly copies the protected
item basis. Returned assessments and collections are immutable snapshots.

Every caller must branch on `EngineeringCalculationResult.Status`. A
`CALCULATED_REVIEW_REQUIRED` result contains findings; it is not certification. `BLOCKED` retains
structured readiness findings, and `FAILED` identifies an execution failure.

## 5. Migrate design cases and envelopes

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

`EngineeringDesignLoopOptions` now carries the complete `EngineeringCaseRunOptions`; the loop no
longer rebuilds a reduced option set. By default an incomplete case report cannot converge. Enable
`requireAcceptedCaseEnvelope(true)` when convergence must additionally require configured metric limits
with no violations.

## 6. Run regression and qualification gates

`StandardDesignKernelVerificationSuite.evaluateRegression()` executes the five registered exact
kernel editions, numeric API baselines, API 526 SI/customary equivalence, and API 12J
metre/micrometre equivalence. API 526, API 610, API 617, and API 12J are evaluated as explicit
historical edition labels; the suite does not reclassify them as current.
Require `areAllBenchmarksPassed()` in regression CI. Do not use that flag as qualification:
`isPassed()` requires independently reviewed, non-regression evidence for every exact
`method@version`.

## Recommended deployment order

1. Inventory legacy standards and record explicit editions and amendments.
2. Verify publisher lifecycle and resolve catalog-only, edition, and applicability failures.
3. Map cross-equipment requirements into a controlled project requirements register.
4. Move calculation calls behind typed kernels without deleting the legacy configuration path.
5. Replace envelope-only execution and require complete, and where needed accepted, case reports.
6. Add project limits, independent benchmark datasets, applicability envelopes, and approvals.
7. Remove use of global overrides only after integrations have migrated and the removal is scheduled for a major release.

The complete runnable API 526 path is in
`examples/neqsim/process/design/StandardDesignKernelMigrationExample.java`.
