---
title: Numerical health and engineering closure
description: Fail-closed convergence, mass/energy closure, equation residual, and sensitivity diagnostics for controlled process states.
---

# Numerical health and engineering closure

`EngineeringNumericalHealthAnalyzer` converts solver state and engineering closure evidence into a deterministic,
machine-readable report. It addresses a different question from whether a calculation executed: whether the numerical
state is sufficiently complete and well behaved to enter a controlled engineering review.

The report contains:

- the `ProcessSystem.solved()` decision and detailed convergence diagnostics on failure;
- sorted per-unit mass closure with absolute and relative errors;
- explicit whole-process or subsystem energy closure observations;
- named equation residuals normalized against declared tolerances; and
- numerical rank and coefficient-scale screening for a supplied sensitivity Jacobian.

Missing required evidence produces `INCOMPLETE`; a convergence, tolerance, or rank failure produces `FAILED`. Only a
`HEALTHY` report returns `isAcceptableForEngineering() == true`. The report is evidence for engineering review, not an
engineering approval.

## Basic process check

```java
EngineeringNumericalHealthReport report = EngineeringNumericalHealthAnalyzer.analyze(process);
if (!report.isAcceptableForEngineering()) {
  throw new IllegalStateException(report.getFindings().toString());
}
```

The default criteria require convergence and at least one applicable unit-operation mass balance. Boundary streams
with zero reference inlet flow and zero imbalance are recorded as `NOT_APPLICABLE`, not silently counted as passing.

## Controlled closure criteria

```java
EngineeringNumericalHealthCriteria criteria = EngineeringNumericalHealthCriteria.builder()
    .maximumMassBalanceErrorPercent(0.05)
    .maximumEnergyBalanceErrorPercent(0.1)
    .requireEnergyClosure(true)
    .requireEquationResiduals(true)
    .requireSensitivityEvidence(true)
    .build();

double[][] jacobian = sensitivityAnalyzer.jacobian(outputs, "kW", inputs, "bara");
EngineeringNumericalHealthReport report = new EngineeringNumericalHealthAnalyzer(process, criteria)
    .addEnergyClosure("compression-train", energyImbalanceKw, energyThroughputKw, "kW")
    .addEquationResidual("recycle-flow", recycleResidual, 1.0e-6, "kg/sec")
    .sensitivityJacobian(jacobian)
    .analyze();
```

Energy evidence is supplied explicitly because NeqSim equipment does not yet expose one universal energy-balance API.
The denominator is the positive energy-flow magnitude used to normalize the imbalance; the report rejects absent,
non-finite, or zero reference magnitudes. A future common equipment energy interface can feed the same report without
changing its schema.

The Jacobian `coefficientScaleRatio` is a screening diagnostic, not a matrix condition number. Rank deficiency or a
ratio above the controlled limit blocks acceptance; final ill-conditioning decisions still require a suitable
scaled/SVD analysis and engineering judgment.

## Design cases and production readiness

Enable a report for every isolated design case:

```java
EngineeringCaseRunOptions options = EngineeringCaseRunOptions.builder()
    .parallelism(2)
    .numericalHealthCriteria(criteria)
    .build();
EngineeringCaseRunReport cases = EngineeringCaseRunner.run(process, caseSet, options);
```

Each `DesignCaseResult` then serializes `numericalHealth`. Attach controlled reports to
`EngineeringProductionReadinessBasis.addNumericalHealthReport(...)` to activate the optional
`NUMERICAL_HEALTH_AND_ENGINEERING_CLOSURE` readiness gate. Once activated, every attached process state must be
`HEALTHY`; qualification-plan output identifies each state that needs closure work.

## Review checklist

1. Set tolerances from the project numerical-method basis; do not inherit example values without review.
2. Cover every governing steady-state, transient snapshot, and degraded case used by downstream calculations.
3. Supply energy closure at the boundary used by the engineering decision.
4. Include residuals from recycles, columns, controllers, and custom equations where relevant.
5. Scale sensitivity inputs and outputs consistently before interpreting the Jacobian screen.
6. Store the JSON report beside the exact model revision, case inputs, method versions, and approval record.
