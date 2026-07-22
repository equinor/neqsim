---
title: HAZOP and LOPA to draft SRS handoff
description: Trace user-supplied hazard and risk inputs through IPL eligibility, LOPA arithmetic, and an unapproved SRS draft.
---

# HAZOP and LOPA to draft SRS handoff

`HazopLopaSrsWorkflow` preserves identifiers and evidence from a selected HAZOP deviation through
LOPA and, when a risk gap remains, into a review-required draft safety requirements specification.
The workflow performs arithmetic and completeness checks; it does not conduct or approve a HAZOP,
select event frequencies, determine the tolerable-risk target, establish IPL independence, or
approve a SIL target.

## Credit rule

`ProtectionLayerDefinition` receives LOPA credit only when all of these declarations are present:

- independent from the initiating event;
- independent from every other credited layer;
- specific to the scenario and consequence;
- auditable by test or inspection;
- positive proof-test or inspection interval;
- controlled evidence reference.

The PFD is always a user-supplied project input. An ineligible layer remains visible in the
`layerAssessments` output but does not reduce the scenario frequency. Every failed gate is reported.

## Traceable scenario

```java
LopaScenarioDefinition scenario = LopaScenarioDefinition
    .builder("LOPA-HP-101", "HAZOP-NODE-07", "MORE-PRESSURE-03")
    .equipmentTag("V-101")
    .initiatingEvent("Inlet control valve fails open", 0.1)
    .consequence("HP separator rupture and hydrocarbon release")
    .targetFrequencyPerYear(1.0e-6)
    .frequencyBasisReference("QRA-BASIS-2026-R2")
    .addProtectionLayer(ProtectionLayerDefinition
        .builder("IPL-BPCS", "Independent pressure control", LayerType.BPCS, 0.1)
        .independentFromInitiatingEvent(true)
        .independentFromOtherLayers(true)
        .specific(true)
        .auditable(true)
        .proofTestIntervalHours(8760.0)
        .evidenceReference("BPCS-SRS-101-R4")
        .build())
    .build();
```

Use a stable HAZOP row/deviation identifier, not only a node title. The frequency basis reference
must point to the controlled QRA, company frequency database, or approved study that supplied both
the initiating-event and tolerable-frequency values.

## Draft SRS input and result

```java
HazopLopaSrsWorkflow.SrsDesignInputs srsInputs =
    HazopLopaSrsWorkflow.SrsDesignInputs
        .builder("SRS-SIF-HP-101", "SIF-HP-101", "LOPA-HP-101-R1")
        .trip("separator pressure", TripDirection.HIGH, 60.0, "bara")
        .safeState("ESDV-101 closed and HP separator isolated")
        .maximumResponseTimeSeconds(5.0)
        .votingArchitecture("2oo3")
        .proofTestIntervalHours(8760.0)
        .resetPolicy("Manual reset after process permissive and field verification")
        .bypassPolicy("Permit-controlled single-channel bypass with time limit")
        .build();

HazopLopaSrsWorkflow.Result handoff =
    HazopLopaSrsWorkflow.run(scenario, srsInputs);
SafetyRequirementSpecificationDraft draft = handoff.getSrsDraft();
```

The workflow credits eligible layers in order and calculates the remaining frequency. If it exceeds
the supplied target, the existing `LOPAResult` determines the additional RRF and screening SIL band,
and a draft SRS is populated with the HAZOP/LOPA identifiers, trip, safe state, response time, voting,
test interval, reset policy, and bypass policy.

The draft always has `REVIEW_REQUIRED`, `draft=true`, `fitForConstruction=false`, and
`engineeringApprovalRequired=true`. If existing credited layers meet the target, no additional SIF
draft is created.

## Review and downstream verification

1. HAZOP team confirms scenario, cause, consequence, safeguards, and recommendations.
2. LOPA team approves event frequency, consequence target, conditional modifiers, IPL eligibility,
   independence, and PFD evidence.
3. Accountable functional-safety engineers review and approve the SRS and SIL target.
4. Build `SafetyFunctionDesign` from approved reliability and architecture data.
5. Run seeded reliability uncertainty and degraded-mode assessments.
6. Run `ClosedLoopSafetyFunction` scenarios for normal demand and approved degraded cases.
7. Link proof-test, cause-and-effect, vendor, commissioning, and validation records.

See [IEC 61511-1](https://webstore.iec.ch/en/publication/24241) for lifecycle requirements and
[IEC 61511-3](https://webstore.iec.ch/en/publication/25480) for required-SIL determination guidance.
