---
title: DNV-RP-F101 isolated metal-loss pressure screening
description: Edition-aware, fail-closed pressure-resistance screening for an isolated longitudinal metal-loss defect.
---

# DNV-RP-F101 isolated metal-loss pressure screening

`DnvRpF101CorrodedPipelineScreeningKernel` provides a narrow deterministic calculation for the
current `DNV-RP-F101 2019-09+AMD:2025-09` basis: one isolated longitudinal metal-loss defect under
internal pressure. The result is always a `SCREENING` result requiring engineering review. It is
not a fitness-for-service decision and does not reproduce licensed safety-factor tables.

DNV identifies the current edition and a broader scope covering internal pressure, internal
pressure combined with longitudinal compressive stress, and multiple assessment approaches on its
[DNV-RP-F101 publisher page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f101-corroded-pipelines/).
Use the purchased standard and project assessment procedure to establish defect classification,
input values, factors, interactions, load cases, and acceptance.

## Implemented equation

For steel outside diameter $D$, externally established assessment wall thickness $t$, axial defect
length $l$, assessment defect depth $d$, and characteristic ultimate tensile strength $UTS$, the
kernel evaluates the public isolated-defect equation:

$$
Q=\sqrt{1+0.31\left(\frac{l}{\sqrt{Dt}}\right)^2}
$$

$$
P_f=\frac{2t\,UTS}{D-t}\left(\frac{1-d/t}{1-(d/t)/Q}\right).
$$

The assessment depth is measured maximum defect depth plus a caller-controlled depth allowance.
The assessed pressure is internal absolute pressure minus external absolute pressure. NeqSim also
reports a caller-controlled pressure limit $P_{limit}=f_pP_f$, utilization
$U=(P_i-P_e)/P_{limit}$, and margin $P_{limit}-(P_i-P_e)$.

The multiplier $f_p$ is deliberately an input. NeqSim does not infer DNV safety class, inspection
accuracy, partial factors, assessment approach, or project acceptance from open-source defaults.

## Runnable Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_F101);
DnvRpF101CorrodedPipelineScreeningKernel.Input input =
    DnvRpF101CorrodedPipelineScreeningKernel.Input
        .builder(edition, "Pipeline")
        .steelOuterDiameterM(0.508)
        .assessmentWallThicknessM(0.0127)
        .measuredDefectDepthM(0.004)
        .defectDepthAllowanceM(0.0005)
        .defectAxialLengthM(0.2)
        .characteristicUltimateTensileStrengthPa(535.0e6)
        .internalPressurePaAbsolute(10.1e6)
        .externalPressurePaAbsolute(0.1e6)
        .callerControlledPressureFactor(0.72)
        .geometryVerified(true)
        .inspectionSizingVerified(true)
        .materialStrengthVerified(true)
        .pressureBasisVerified(true)
        .projectFactorVerified(true)
        .isolatedLongitudinalMetalLossApplicabilityVerified(true)
        .build();

EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result =
    new DnvRpF101CorrodedPipelineScreeningKernel().calculate(input, null);
DnvRpF101CorrodedPipelineAssessment assessment = result.getValue();
Map<String, Object> report = assessment.toMap();
```

For these demonstration inputs, assessment depth is `4.5 mm`, calculated failure pressure is about
`22.3466 MPa`, the caller-controlled limit is about `16.0896 MPa`, and pressure utilization is about
`0.62152`. These values are deterministic regression data, not an independent DNV benchmark or an
acceptance recommendation.

The
[executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f101_corroded_pipeline_screening_kernel.ipynb)
uses the same API, shows defect-depth and length sensitivity, and demonstrates a blocked input when
inspection evidence is not verified.

## Fail-closed evidence boundary

Calculation is blocked unless all of the following are explicit and internally valid:

- exact unamended `2019-09+AMD:2025-09` edition and catalogued pipeline/riser applicability;
- positive outside diameter, assessment wall thickness, axial defect length, and characteristic
  ultimate tensile strength;
- positive measured defect depth, non-negative caller-controlled allowance, and positive remaining
  wall thickness;
- positive internal and external absolute pressures with internal pressure above external pressure;
- a caller-controlled pressure factor above zero and no greater than one; and
- external verification of geometry, inspection sizing and allowance, material strength, pressure
  basis, project factor, and isolated longitudinal metal-loss/internal-pressure applicability.

The verification flags are attestations. The inspection reports, uncertainty/growth basis,
material records, pressure cases, licensed factor source, and accountable approvals remain external
evidence.

## Not implemented

The kernel does not calculate or approve:

- multiple, interacting, or complex-profile defects;
- combined internal pressure and longitudinal compression or other combined loads;
- probabilistic assessment, inspection-accuracy models, or partial-factor selection;
- corrosion-growth prediction or conversion of a corrosion rate into defect dimensions;
- crack-like flaws, gouges, dents, blisters, weld flaws, laminations, or material degradation;
- repair selection, inspection interval, fitness-for-service acceptance, or certification; or
- detailed finite-element or fracture-mechanics assessment.

`NorsokM506CorrosionDesignKernel` predicts a simplified CO2 corrosion rate and projected uniform
wall loss. That rate/allowance workflow is not an RP-F101 defect assessment and must not be silently
converted into measured defect geometry.

## Separation from DNV-ST-F101

DNV-RP-F101 assessment of existing metal loss is separate from original pipeline-system design.
This kernel does not replace DNV-ST-F101 pressure containment, collapse, propagation buckling,
local buckling, load interaction, fatigue, incidental/test pressure, de-rating, safety class,
ovality, fabrication route, or installation-strain checks. Those design checks and their load-case
evidence remain independently required.
