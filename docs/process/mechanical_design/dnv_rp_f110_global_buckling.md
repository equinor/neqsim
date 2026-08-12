---
title: DNV-RP-F110 global-buckling response screening
description: Edition-aware, fail-closed screening of caller-controlled submarine-pipeline global-buckling response envelopes.
---

# DNV-RP-F110 global-buckling response screening

`DnvRpF110GlobalBucklingResponseScreeningKernel` provides a narrow deterministic screen for the
current `DNV-RP-F110 2019-09+AMD:2021-09` basis. It compares response quantities produced by an
external global structural analysis with caller-controlled allowable values for named route
locations and design situations. Every result has `SCREENING` maturity and requires engineering
review. It does not reproduce licensed DNV models or criteria and is not a conformity decision.

DNV describes F110 as a methodology and criteria for satisfying the global-buckling functional
requirements of DNV-ST-F101. Its public scope distinguishes controlled buckling for pipelines
exposed on the seabed from prevention of upheaval and lateral buckling for buried pipelines. The
[DNV-RP-F110 publisher page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f110-global-buckling-of-submarine-pipelines/)
lists edition `2019-09` amended `2021-09`. Use the purchased document and project-controlled design
basis for applicability, load cases, structural analysis, pipe-soil response, imperfection and
trigger design, allowable values, and acceptance.

## Calculation boundary

For each externally analysed case $i$, NeqSim reports four caller-controlled margins and
utilizations:

$$
m_{F,i}=F_{allow,i}-F_{eff,i}, \qquad U_{F,i}=\frac{F_{eff,i}}{F_{allow,i}},
$$

$$
m_{\varepsilon,i}=\varepsilon_{allow,i}-\varepsilon_{peak,i}, \qquad
U_{\varepsilon,i}=\frac{\varepsilon_{peak,i}}{\varepsilon_{allow,i}},
$$

$$
m_{y,i}=y_{allow,i}-y_{peak,i}, \qquad U_{y,i}=\frac{y_{peak,i}}{y_{allow,i}},
$$

$$
m_{L,i}=L_{available,i}-L_{required,i}, \qquad
U_{L,i}=\frac{L_{required,i}}{L_{available,i}}.
$$

These are response-envelope identities, not critical-buckling equations. In particular, the
force limit is an external response allowable and must not be interpreted as a NeqSim-derived
buckle-initiation or buckle-prevention criterion. A negative margin remains a visible calculated
finding; it is not converted to missing-data state and does not establish a DNV limit-state
verdict.

## Runnable Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_F110);
DnvRpF110GlobalBucklingResponseScreeningKernel.Input input =
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input
        .builder(edition, "Pipeline")
        .pipelineOuterDiameterM(0.3239)
        .steelWallThicknessM(0.0206)
        .addBucklingCase(
            new DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase(
                "controlled buckle 1", 0.0, "operation",
                DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration.EXPOSED,
                DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy.CONTROLLED_BUCKLING,
                8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0))
        .addBucklingCase(
            new DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase(
                "buried section 1", 25000.0, "shutdown",
                DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration.BURIED,
                DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy.BUCKLING_PREVENTION,
                6.0e6, 10.0e6, 0.004, 0.010, 3.0, 5.0, 40.0, 100.0))
        .applicabilityVerified(true)
        .operatingEnvelopeAndEffectiveForceVerified(true)
        .pipePropertiesAndAsLaidGeometryVerified(true)
        .pipeSoilInteractionVerified(true)
        .imperfectionTriggerAndStrategyVerified(true)
        .globalStructuralModelVerified(true)
        .designSituationsAndLoadCombinationsVerified(true)
        .localCapacityAndStrainCriteriaVerified(true)
        .uncertaintySensitivityAndBuckleSharingVerified(true)
        .installationInterventionMonitoringAndLifecycleReviewed(true)
        .build();

EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result =
    new DnvRpF110GlobalBucklingResponseScreeningKernel().calculate(input, null);
Map<String, Object> report = result.getValue().toMap();
```

For these demonstration values, the maximum force, strain, displacement, and feed-in utilizations
are `0.8`, `0.6`, `0.8`, and `0.75`. These are deterministic regression data, not values taken from
F110.

The
[executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f110_global_buckling_response_kernel.ipynb)
uses the same API, plots the four response utilizations, preserves an exceeded strain limit as a
visible result, and demonstrates fail-closed behavior when global-structural-model evidence is
absent.

## Fail-closed evidence boundary

Calculation is blocked unless all of the following are explicit and internally valid:

- exact unamended `2019-09+AMD:2021-09` edition and catalogued pipeline applicability;
- positive, geometrically valid outside diameter and structural steel wall thickness;
- at least one case with a unique label, non-negative route distance, non-blank design situation,
  pipeline configuration, and design strategy;
- finite non-negative external responses and required feed-in length, together with positive
  allowable values and available feed-in length; and
- external verification of applicability, operating envelope and effective force, pipe properties
  and as-laid geometry, pipe-soil response, imperfections/triggers/strategy, global structural
  model, design situations and load combinations, local capacity and strain criteria,
  uncertainty/sensitivity/buckle sharing, plus installation/intervention/monitoring/lifecycle.

Verification flags are attestations. Controlled models, reports, assumptions, uncertainties, and
accountable approvals remain external evidence.

## Requirement-pack routing

The `StandardRequirementPack` exposes four bounded mappings:

| Capability | NeqSim implementation | Boundary |
| --- | --- | --- |
| Operating profile | `TwoFluidPipe` | Hydraulic and thermal profiles only; effective force and structural response are external |
| Route segmentation | `TiebackRouteNetwork` | Route structure only; as-laid geometry, imperfections, and buckling response are external |
| Pipe-soil envelope | `DnvRpF114PipeSoilInteractionScreeningKernel` | Caller-controlled F114 margins only; soil springs and F110 response are external |
| Mechanical design | `PipeMechanicalDesignCalculator` | Preliminary containment screen only; local capacity and complete ST-F101 checks are external |

Discover the immutable mapping independently of the typed kernel:

```java
StandardSelection selection = StandardSelection.strictRequirements(StandardType.DNV_RP_F110);
StandardRequirementPack requirements = StandardRegistry.requireRequirementPack(selection);
```

The pack is a capability map, not a clause register or statement of coverage.

## Not implemented

The kernel and pack do not calculate or approve:

- effective axial force, critical buckling load, buckle initiation, propagation, feed-in mechanics,
  upheaval or lateral-buckling response, or Hobbs/finite-element solutions;
- soil resistance or stiffness, pipe-soil springs, embedment, breakout, cyclic response, or
  geotechnical uncertainty;
- imperfection sensitivity, sleeper or distributed-buoyancy trigger design, buckle spacing,
  sharing, interaction, or rogue-buckle treatment;
- local strain capacity, ratcheting, fracture, fatigue, free-span, on-bottom-stability,
  installation, intervention, monitoring, or integrity-management acceptance;
- DNV-RP-F114 geotechnical design, DNV-RP-F109 stability, DNV-RP-F105 free-span assessment, or
  DNV-ST-F101 structural design; or
- conformity assessment, certification, regulatory approval, or accountable engineering approval.

All DNV-ST-F101 pressure containment, collapse, propagation buckling, local buckling, load
interaction, fatigue, incidental/test pressure, de-rating, safety class, ovality, fabrication route,
and installation-strain checks remain separate and are not replaced.
