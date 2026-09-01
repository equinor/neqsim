---
title: DNV-RP-F114 pipe-soil interaction screening
description: Edition-aware, fail-closed screening of caller-controlled pipe-soil demand and resistance envelopes.
---

# DNV-RP-F114 pipe-soil interaction screening

`DnvRpF114PipeSoilInteractionScreeningKernel` provides a narrow deterministic screen for the
current `DNV-RP-F114 2021-05` basis. It compares externally established vertical, axial, and
lateral design-action magnitudes with caller-controlled pipe-soil resistance magnitudes at named
route locations and design situations. Every result has `SCREENING` maturity and requires
engineering review. It does not reproduce licensed DNV models or criteria and is not a conformity
decision.

DNV describes F114 as recommendations for evaluating pipe-soil interaction in design situations
and assessments for exposed and buried submarine pipelines. The
[DNV-RP-F114 publisher page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f114-pipe-soil-interaction-for-submarine-pipelines/)
lists edition `2021-05`. Use the purchased document and project-controlled geotechnical basis for
applicability, site investigation, soil interpretation, load-displacement models, characteristic
values, safety format, and acceptance criteria.

## Calculation boundary

For each case $i$ and interaction direction $j \in \{vertical, axial, lateral\}$, the caller
supplies a non-negative demand magnitude $S_{i,j}$ and positive resistance magnitude $R_{i,j}$ on
the same N/m basis. NeqSim reports:

$$
m_{i,j}=R_{i,j}-S_{i,j}, \qquad U_{i,j}=\frac{S_{i,j}}{R_{i,j}}.
$$

The assessment also reports the minimum margin and maximum utilization for each direction. A
negative margin remains a visible calculated finding. It is not converted into missing-data state,
and it does not by itself establish a DNV limit-state verdict.

The kernel deliberately does not derive resistance from a friction coefficient, undrained shear
strength, submerged weight, or burial depth. F114 pipe-soil response depends on project soil,
geometry, installation history, displacement, drainage, rate, consolidation, cyclic loading,
remoulding, and uncertainty bases that cannot be represented by a universal default.

## Runnable Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_F114);
DnvRpF114PipeSoilInteractionScreeningKernel.Input input =
    DnvRpF114PipeSoilInteractionScreeningKernel.Input
        .builder(edition, "Pipeline")
        .pipelineOuterDiameterM(0.3239)
        .submergedWeightNPerM(1200.0)
        .addInteractionCase(
            new DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase(
                "route section 1", 0.0, "installation",
                200.0, 500.0, 80.0, 160.0, 120.0, 240.0))
        .addInteractionCase(
            new DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase(
                "route section 2", 25000.0, "operation",
                300.0, 600.0, 100.0, 250.0, 220.0, 275.0))
        .applicabilityVerified(true)
        .siteInvestigationVerified(true)
        .soilModelVerified(true)
        .pipelineConfigurationVerified(true)
        .installationHistoryVerified(true)
        .cyclicDrainageRateEffectsVerified(true)
        .loadDisplacementAndResistanceVerified(true)
        .uncertaintyAndVariabilityVerified(true)
        .designActionsAndAcceptanceCriteriaVerified(true)
        .interfacesAndLifecycleReviewed(true)
        .build();

EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result =
    new DnvRpF114PipeSoilInteractionScreeningKernel().calculate(input, null);
Map<String, Object> report = result.getValue().toMap();
```

For these demonstration values, the minimum lateral margin is `55 N/m` and the maximum lateral
utilization is `0.8`. The numbers are deterministic regression data, not values taken from F114.

The
[executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f114_pipe_soil_interaction_kernel.ipynb)
uses the same API, plots direction-specific utilizations, preserves an exceeded lateral resistance
as a visible result, and demonstrates fail-closed behavior when site-investigation evidence is
absent.

## Fail-closed evidence boundary

Calculation is blocked unless all of the following are explicit and internally valid:

- exact unamended `2021-05` edition and catalogued pipeline applicability;
- positive pipeline outside diameter and submerged weight;
- at least one case with a unique label, non-negative route distance, and non-blank design
  situation;
- finite non-negative demand magnitudes and positive resistance magnitudes for vertical, axial,
  and lateral interaction; and
- external verification of applicability, site investigation, soil model, pipeline/interface
  configuration, installation history, cyclic/drainage/rate/consolidation effects,
  load-displacement and resistance models, uncertainty/spatial variability, design actions and
  acceptance criteria, plus adjacent-standard and lifecycle interfaces.

Verification flags are attestations. Controlled reports, calculations, data, uncertainties, and
accountable approvals remain external evidence.

## Requirement-pack routing

F114 interacts with several existing NeqSim capabilities. The `StandardRequirementPack` exposes
four bounded mappings:

| Capability | NeqSim implementation | Boundary |
| --- | --- | --- |
| Route segmentation | `TiebackRouteNetwork` | Route structure only; geotechnical units and spatial variability are external |
| Operating profile | `TwoFluidPipe` | Hydraulic/thermal profile only; structural actions and soil response are external |
| Burial thermal environment | `PipeSurroundingEnvironment` | Burial depth and soil thermal resistance only; not a geotechnical model |
| Mechanical design | `PipeMechanicalDesignCalculator` | Preliminary containment screen; F114 interaction and full ST-F101 checks are external |

Discover the immutable mapping independently of the typed kernel:

```java
StandardSelection selection = StandardSelection.strictRequirements(StandardType.DNV_RP_F114);
StandardRequirementPack requirements = StandardRegistry.requireRequirementPack(selection);
```

The pack is a capability map, not a clause register or statement of coverage.

## Not implemented

The kernel and pack do not calculate or approve:

- geotechnical site-investigation scope, laboratory or in-situ test interpretation, soil
  stratigraphy, geohazards, or data-quality acceptance;
- penetration, embedment, breakout, residual resistance, berm development, uplift, suction,
  trench-wall, or backfill response;
- vertical, axial, or lateral load-displacement curves; monotonic or cyclic soil constitutive
  models; drainage, rate, consolidation, or remoulding effects;
- characteristic soil values, partial factors, model uncertainty, spatial variability, or
  probabilistic calibration;
- structural design actions, combinations, finite-element springs, allowable displacement,
  installation analysis, intervention design, or integrity-management decisions;
- DNV-RP-F109 on-bottom stability, DNV-RP-F110 global buckling, DNV-RP-F105 free-span response, or
  DNV-ST-F101 structural design; or
- conformity assessment, certification, regulatory approval, or accountable engineering approval.

NeqSim burial depth and soil thermal resistance are heat-transfer inputs. They must not be treated
as F114 geotechnical resistance evidence.
