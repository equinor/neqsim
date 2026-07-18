---
title: SIF reliability uncertainty and degraded modes
description: Seeded PFD/PFH uncertainty and governed SIF bypass, maintenance, failure, and proof-test state assessment.
---

# SIF reliability uncertainty and degraded modes

`SafetyFunctionDesign` remains the analytical source for low-demand PFDavg and high/continuous-demand
PFH screening. Two complementary APIs add lifecycle evidence without changing or certifying that model:

- `SafetyFunctionReliabilityStudy` propagates declared input uncertainty with a reproducible seed and
  reports P10/P50/P90 PFDavg and PFH plus the sampled probability of meeting the supplied SIL target.
- `SafetyFunctionDegradedModeAssessment` evaluates the effective MooN vote and governance gaps for
  bypassed, failed, forced-trip, under-repair, and proof-test-overdue states.

Neither result infers a SIL target, authorizes continued operation, validates common-cause independence,
or replaces a project SIL verification. Every output sets `engineeringApprovalRequired`.

## Reliability uncertainty

Define triangular distributions only where the project has an evidence basis. Failure-rate, proof-test,
and repair-time distributions are factors on the controlled design values. Diagnostic coverage, beta,
and bypass probability distributions are absolute fractions.

```java
SafetyFunctionReliabilityStudy.SubsystemUncertainty sensors =
    new SafetyFunctionReliabilityStudy.SubsystemUncertainty("2oo3 pressure transmitters")
        .setFailureRateFactor(TriangularDistribution.of(0.8, 1.0, 1.3))
        .setDiagnosticCoverage(TriangularDistribution.of(0.55, 0.60, 0.65))
        .setProofTestIntervalFactor(TriangularDistribution.of(0.9, 1.0, 1.2))
        .setBetaFactor(TriangularDistribution.of(0.03, 0.05, 0.08));

SafetyFunctionReliabilityStudy.Result reliability =
    SafetyFunctionReliabilityStudy.run(
        safetyFunctionDesign, Collections.singletonList(sensors), 10000, 20260718L);
```

Store the seed, iteration count, distributions, data sources, and deterministic design result together.
P10 is the lower PFD/PFH percentile and P90 is the higher, more conservative percentile. Do not select
only a favorable percentile to claim compliance; the acceptance basis belongs in the approved SRS and
verification plan.

## Degraded and maintenance operation

An operating-mode snapshot uses one-based channel numbers and exact subsystem names from
`SafetyFunctionDesign`:

```java
SafetyFunctionOperatingMode mode = SafetyFunctionOperatingMode
    .builder("PT-C authorized bypass", ModeType.DEGRADED)
    .authorizationReference("OPS-BYPASS-2026-014")
    .compensatingMeasure("Continuous pressure watch and restricted throughput")
    .duration(2.0, 8.0)
    .channelState("2oo3 pressure transmitters", 3, ChannelState.BYPASSED)
    .hoursSinceProofTest("2oo3 pressure transmitters", 4000.0)
    .build();

SafetyFunctionDegradedModeAssessment.Result assessment =
    SafetyFunctionDegradedModeAssessment.assess(safetyFunctionDesign, mode);
```

For a 2oo3 group with one bypassed channel, the result reports effective `2oo2`: demand capability
remains, but redundancy and the original SIL claim are not preserved by this screen. Two unavailable
channels produce `2oo1` and `NOT_DEMAND_CAPABLE`. A forced-trip channel reduces the remaining vote
and exposes spurious-trip susceptibility.

Non-normal modes are flagged unless they include an authorization reference, compensating measure,
and maximum duration. Exceeding the duration is a finding. Proof-test age must be recorded for every
subsystem; overdue or missing evidence invalidates preservation of the design claim.

## Required review package

- approved SRS and LOPA references;
- failure-rate and coverage sources, including applicability limits;
- proof-test and partial-stroke procedures, intervals, coverage, and actual completion records;
- common-cause, independence, systematic-capability, and architectural-constraint assessment;
- bypass/maintenance authorization, duration, owner, compensating measures, and restoration test;
- closed-loop transient evidence for normal and credited degraded states;
- independent functional-safety assessment where required by the project lifecycle.

See the official [IEC 61511-1](https://webstore.iec.ch/en/publication/24241) lifecycle requirements and
[IEC 61511-2](https://webstore.iec.ch/en/publication/25510) application guidance for the governing
project context.
