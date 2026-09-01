---
title: Safety change revalidation and independent benchmarks
description: Propagate governed model changes into lifecycle revalidation tasks and qualify safety methods against controlled external references.
---

# Safety change revalidation and independent benchmarks

Safety verification evidence becomes stale when its equipment, design basis, requirements, or
upstream calculations change. `SafetyStudyRevalidationPlanner` converts a controlled
`ModelChangeEvent` into traceable HAZOP, LOPA, SRS, SIF, disposal-system, and facility-response work
without building a second dependency engine. It delegates propagation to
`GeneralizedImpactAnalyzer` and adds safety-specific actions only after the impacted graph nodes are
known.

`SafetyVerificationBenchmarkSuite` separately compares three implemented methods with externally
supplied reference values: SIF PFDavg screening, LOPA residual frequency, and closed-loop dynamic
response time. It uses the existing production-readiness benchmark classes so regression output is
not mistaken for independent evidence.

These controls support verification planning in the
[IEC 61511 lifecycle](https://webstore.iec.ch/en/publication/24241) and its
[application guidance](https://webstore.iec.ch/en/publication/25510). They do not establish
standards compliance or replace management-of-change, functional-safety assessment, independent
review, or accountable engineering approval.

## Tag the canonical engineering graph

Set the `safetyStudyType` property on every safety lifecycle work-product node. The accepted values
and generated actions are:

| `safetyStudyType` | Revalidation actions |
| --- | --- |
| `HAZOP` | Review the scenario set; reapprove |
| `LOPA` | Recheck IPL eligibility; recalculate frequency; reapprove |
| `SRS` | Revise requirements; reapprove |
| `SIF_RELIABILITY` | Recalculate PFD/PFH uncertainty; recheck degraded modes; reapprove |
| `SIF_DYNAMIC_VERIFICATION` | Rerun closed-loop cases; review safe state and deadline; reapprove |
| `RELIEF_BLOWDOWN_FLARE` | Recalculate relief/transient disposal; review concurrency and capacity; reapprove |
| `FACILITY_RESPONSE` | Rebuild the integrated handoff; reapprove |

Use the normal `EngineeringEdge` relationships to describe dependencies. For example, a LOPA
calculation can `DEPENDS_ON` a HAZOP document, an SRS can be `GENERATED_FROM` the LOPA, a dynamic SIF
calculation can `DEPENDS_ON` the SRS, and an approval can `APPROVES` the integrated response.

```java
EngineeringNode lopa = new EngineeringNode(lopaId, EngineeringNode.Kind.CALCULATION,
    "LOPA-101", "Separator overpressure LOPA")
    .putProperty(SafetyStudyRevalidationPlanner.SAFETY_STUDY_TYPE_PROPERTY, "LOPA");
graph.addNode(lopa);

SafetyStudyRevalidationPlanner.Result plan =
    new SafetyStudyRevalidationPlanner().plan(graph, controlledModelChangeEvent);

plan.getImpactAnalysis(); // complete generalized propagation result
plan.getTasks();          // safety-specific work with path and reason edges
plan.toJson();            // MOC/revalidation handoff
```

Every task is emitted as incomplete, and the plan always reports `fitForConstruction: false` and
`engineeringApprovalRequired: true`. Complete the work and restore approvals in the project MOC and
document-control systems, not by editing the generated JSON.

## Build a controlled benchmark suite

The expected values, source classification, source reference, dataset revision, tolerances, and
independent-review record are caller inputs. A suite qualifies only when all three method cases pass,
the source is not `REGRESSION_BASELINE`, and an independent-review record is present.

```java
SafetyVerificationBenchmarkSuite benchmarks = new SafetyVerificationBenchmarkSuite(
    "SAFETY-BENCH-001", "A",
    SourceClass.INDEPENDENT_CALCULATION,
    "INDEPENDENT-CALC-PACKAGE-REV-A", "A", "REVIEW-PS-042")
    .addSifPfd("ONE-OUT-OF-ONE-PFD", design,
        independentExpectedPfd, 1.0e-8, 0.01)
    .addLopaFrequency("TWO-IPL-LOPA", lopaResult,
        independentExpectedFrequencyPerYear, 1.0e-10, 0.01)
    .addDynamicResponse("ESD-VALVE-STROKE", dynamicResult, "esdv-closed",
        independentExpectedResponseSeconds, 0.1, 0.01);

EngineeringBenchmarkSuite.Report report = benchmarks.evaluate();
```

The method keys are versioned:

- `neqsim.safety.sif-pfd-screen@1.0`
- `neqsim.safety.lopa-frequency@1.0`
- `neqsim.safety.dynamic-response@1.0`

`DynamicSafetyScenarioResult.CriterionResult.getFirstSatisfiedSeconds()` exposes the executed
criterion response time without parsing JSON. The benchmark retains the standard production
readiness rule: every case for a required method must qualify.

## Independence and review rules

- Do not use a previous NeqSim result as an independent expected value. Declare it
  `REGRESSION_BASELINE`; it is useful for non-regression but cannot qualify the method.
- Prefer a hand calculation, published worked example, separate implementation, vendor result, or
  independently controlled CAE result whose assumptions match the benchmark case.
- Document equation versions, units, tolerances, fluid/property basis, voting assumptions, proof
  interval, dynamic step size, and response-time definition.
- Independence is a governance fact, not something Java can prove. The API records the caller's
  declaration; reviewers must verify the source and review record.
- A passing benchmark qualifies the implemented method/version for the controlled case set. It does
  not approve a project design, infer SIL, or prove the scenario set is complete.

## Validation coverage

`SafetyStudyRevalidationPlannerTest` changes a separator design pressure and demonstrates propagation
through HAZOP, LOPA, SRS, closed-loop verification, facility response, and the approval node.
`SafetyVerificationBenchmarkSuiteTest` checks analytical 1oo1 PFDavg, eligible-layer LOPA arithmetic,
and a physically stroked ESD valve. It also proves that an otherwise passing regression baseline
cannot qualify as independent evidence.
