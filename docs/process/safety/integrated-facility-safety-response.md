---
title: Integrated facility safety response
description: Join executed ESD, compressor-trip, relief, blowdown, flare, and process-limit evidence without replacing the underlying NeqSim calculations.
---

# Integrated facility safety response

`FacilitySafetyResponseStudy` creates one review package from safety calculations that have already
been executed by their owning NeqSim engines. It is intended for scenarios where isolation,
compressor protection, depressuring, relief, and flare response interact and a reviewer needs to
see both the system-level verdict and the original structured evidence.

This workflow supports verification handoff under the [IEC 61511 lifecycle](https://webstore.iec.ch/en/publication/24241).
It does not establish IEC compliance, select a SIL, approve a scenario, or replace accountable
process-safety, rotating-equipment, flare, piping, and mechanical engineering review.

## Evidence sources

| Response | Owning NeqSim result | Facility check |
| --- | --- | --- |
| Sensor, vote, ESD/HIPPS final element | `DynamicSafetyScenarioResult` | Every required dynamic criterion passed |
| Repeated compressor surge | `AntiSurge` plus `CompressorTripResponse` | Required trip was observed within its controlled deadline |
| PSV and concurrent steady loads | `CoupledReliefBlowdownFlareResult` | Relief sizing and accumulated-pressure checks are acceptable |
| Blowdown and flare transient | Same coupled result | Header Mach and configured flare-capacity limits are acceptable |
| MDMT, hydrate margin, pressure, temperature, or other limits | `ProcessSafetyConstraint` | Every required minimum or maximum is met |

The facility package embeds `DynamicSafetyScenarioResult.toMap()` and
`CoupledReliefBlowdownFlareResult.toMap()` unchanged. It does not copy their physics or recalculate
their verdicts.

## Example

```java
AntiSurge antiSurge = compressor.getAntiSurge();
CompressorTripResponse trip = CompressorTripResponse.capture(
    "K-101", antiSurge, tripObserved, 1.8, 3.0, "C&E-K-101-REV-4");

FacilitySafetyResponseStudy study = FacilitySafetyResponseStudy.builder("FACILITY-ESD-001")
    .scenarioSelectionReviewed(true)
    .addEvidenceReference("HAZOP-TRAIN-A-REV-6")
    .addEvidenceReference("SRS-ESD-001-REV-3")
    .addProtectionResult("2oo3 inlet ESD", dynamicEsdResult)
    .disposalResult(coupledReliefBlowdownFlareResult)
    .addCompressorTripResponse(trip)
    .addConstraint(ProcessSafetyConstraint.minimum(
        "MDMT-V-101", "Vessel minimum metal temperature", "degC",
        minimumCalculatedTemperatureC, -29.0, "V-101-DATASHEET-REV-2"))
    .addConstraint(ProcessSafetyConstraint.minimum(
        "HYDRATE-MARGIN", "Minimum hydrate margin", "degC",
        minimumHydrateMarginC, 3.0, "FLOW-ASSURANCE-BASIS-REV-5"))
    .build();

boolean technical = study.isTechnicallyAcceptable();
boolean complete = study.isEvidenceComplete();
boolean readyForReview = study.isReadyForEngineeringReview();
String reviewPackage = study.toJson();
```

`readyForReview` is deliberately not an approval flag. The JSON always carries
`engineeringApprovalRequired: true` and `silTargetInferred: false`.

## Compressor-trip evidence

`CompressorTripResponse.capture(...)` snapshots the surge-cycle count, configured trip-cycle
threshold, anti-surge valve position, trip demand, observed trip, response time, deadline, and
controlled evidence reference. `AntiSurge.getMaxSurgeCyclesBeforeTrip()` exposes the configured
threshold, and `setMaxSurgeCyclesBeforeTrip(...)` rejects zero or negative values so a model cannot
silently demand a permanent trip.

A trip-demand verdict is acceptable only when the trip was observed and its response time is no
greater than the controlled deadline. When no repeated-surge trip is demanded, the response does
not create a false failure, but its evidence reference remains part of the completeness gate.

## Review sequence

1. Approve the initiating events, credibility, concurrency, and controlled limit sources outside
   the model.
2. Run each closed-loop protection scenario on an isolated process copy.
3. Run the coupled PSV, blowdown, header-hydraulics, and flare-capacity calculation.
4. Capture anti-surge trip demand and the independently observed compressor state/response time.
5. Add MDMT, hydrate-margin, pressure, temperature, and other project constraints with controlled
   references.
6. Review `getFindings()` and the embedded engine results; do not approve from the three aggregate
   booleans alone.
7. Record accountable discipline approvals in the project document-control system.

## Required controls and limitations

- A passed process model is evidence for the modeled case only; it is not proof that the HAZOP
  scenario set is complete.
- The compressor trip observation must come from the scenario equipment state, test system, or
  another controlled source. `AntiSurge.shouldTrip()` states the demand, not the final-element proof.
- Constraint limits must cite an approved datasheet, SRS, design basis, or other controlled source.
- Relief and flare acceptance remains subject to the assumptions and limitations reported by the
  coupled calculation.
- SIL selection and reliability verification remain in the HAZOP/LOPA/SRS and
  `SafetyFunctionReliabilityStudy` workflows.

The implementation is covered by `FacilitySafetyResponseStudyTest`, which executes a 2oo3 ESD
valve stroke, a repeated-surge trip demand, a PSV calculation, a transient blowdown/flare case, and
MDMT/hydrate constraints before building the facility handoff.
