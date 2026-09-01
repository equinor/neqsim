---
title: DNV-RP-C203 S-N and Miner fatigue screening
description: Edition-aware, fail-closed fatigue screening with caller-controlled S-N curves and stress spectra.
---

# DNV-RP-C203 S-N and Miner fatigue screening

`DnvRpC203FatigueDesignKernel` provides a reusable, immutable calculation path for an explicit
`DNV-RP-C203 2024-10+AMD:2025-10` basis. It applies a caller-supplied S-N curve to a verified
stress-range spectrum and performs Palmgren-Miner summation. The result is always a screening result
that requires engineering approval.

NeqSim does not reproduce or select DNV's licensed S-N tables. The curve identifier and numeric
parameters must come from the project's controlled copy of the standard and must match the detail
category, environment, fabrication, thickness, weld, and inspection basis. Setting
`curveDefinitionVerified(true)` records an attestation; it does not create that evidence.

The publisher page was checked on 2026-08-02 and identifies the current basis as edition 2024-10,
amended 2025-10: [DNV-RP-C203](https://www.dnv.com/energy/standards-guidelines/dnv-rp-c203-fatigue-design-of-offshore-steel-structures/).

## Calculation contract

For each spectrum bin, the supplied factors are applied to the nominal stress range:

$$\Delta\sigma_{eff,i}=\Delta\sigma_{nom,i}\,SCF\,f_{th}\,f_{other}$$

For each active S-N branch, the kernel evaluates:

$$\log_{10}N_i=\log_{10}A-m\log_{10}(\Delta\sigma_{eff,i})$$

It then reports raw and design damage, utilization, and a linear life extrapolation:

$$D_{raw}=\sum_i\frac{n_i}{N_i},\qquad
D_{design}=DFF\,D_{raw},\qquad
U=\frac{D_{design}}{D_{limit}},\qquad
L_{est}=\frac{t_{spectrum}}{U}$$

Single-slope and continuous bi-linear curve definitions are supported. A bi-linear definition is
blocked when its branches differ by more than one percent at the supplied transition cycle count.
Zero-cycle bins are retained in the result, but at least one positive cycle count is required.

## Java example

The numeric curve below is deliberately named as project-controlled demonstration data; it is not a
DNV curve-table transcription.

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_C203);
DnvRpC203FatigueDesignKernel.Input input = DnvRpC203FatigueDesignKernel.Input
    .builder(edition, "Pipeline")
    .snCurve(DnvRpC203FatigueDesignKernel.SnCurve
        .singleSlope("PROJECT-CONTROLLED-DEMO", 12.0, 3.0))
    .addStressBin("high range", 100.0, 1.0e5)
    .addStressBin("moderate range", 50.0, 2.0e5)
    .stressConcentrationFactor(1.0)
    .thicknessCorrectionFactor(1.0)
    .otherStressRangeFactor(1.0)
    .designFatigueFactor(3.0)
    .minerDamageLimit(1.0)
    .assessedExposureYears(20.0)
    .curveDefinitionVerified(true)
    .stressSpectrumVerified(true)
    .build();

EngineeringCalculationResult<DnvRpC203FatigueAssessment> result =
    new DnvRpC203FatigueDesignKernel().calculate(input, null);
DnvRpC203FatigueAssessment assessment = result.getValue();
Map<String, Object> report = assessment.toMap();
```

`DnvRpC203FatigueDesignKernelTest.documentedExampleIsRunnable` executes the public API used in this
snippet. The [executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_c203_fatigue_kernel.ipynb)
shows the bin contributions and a deliberately blocked unverified input.

## Fail-closed readiness

Calculation is blocked for:

- an edition other than `2024-10+AMD:2025-10`, or additional project amendments;
- equipment outside `Pipeline`, `AdiabaticPipe`, `Pipe`, `Riser`, or `OffshoreStructure`;
- a missing, invalid, or discontinuous S-N curve;
- missing curve verification or stress-spectrum verification;
- missing bins, invalid stress ranges or cycle counts, or no positive cycles;
- non-positive stress factors, a design fatigue factor below one, or a damage limit outside
  `(0, 1]`;
- an invalid assessed exposure or a non-finite numerical result.

## Engineering boundary

The kernel does not derive structural stress, select a fatigue detail or S-N curve, perform
rainflow counting, calculate stress concentration or thickness/environmental factors, combine
simultaneous loads, determine fabrication tolerances, plan inspection, or perform a conformity
assessment. Those inputs and decisions remain external controlled evidence.

The older `PipeMechanicalDesignCalculator` and `RiserMechanicalDesignCalculator` fatigue methods
remain for compatibility. They use embedded simplified parameters that differ between classes and
must not be presented as current-edition DNV-RP-C203 evidence. New explicit C203 studies should pass
their approved stress spectra and curve parameters through this kernel.
