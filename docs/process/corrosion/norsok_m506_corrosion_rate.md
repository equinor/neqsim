---
title: "NORSOK M-506 CO2-corrosion screening"
description: "Edition-aware NeqSim screening adapter for the existing NorsokM506CorrosionRate calculation."
---

# NORSOK M-506 CO2-corrosion screening

NeqSim exposes the existing `NorsokM506CorrosionRate` calculation through the common,
edition-aware engineering kernel API:

- `NorsokM506CorrosionDesignKernel` is the preferred entry point for auditable studies.
- `NorsokM506CorrosionAssessment` is an immutable, unit-explicit result snapshot.
- `NorsokM506CorrosionRate` remains available for legacy mutable workflows and parameter sweeps.
- `NorsokM506ElectrolyteBridge` remains the route for obtaining an electrolyte-model pH and optional
  FeCO3 saturation ratio from a brine.

The kernel implements only the unamended `NORSOK-M-506 2017` basis. Standards Norway identified
that edition as current and under systematic review in May 2026; the catalog record was checked on
2026-08-02 against the [publisher notice](https://standard.no/en/news/norsok-m-5062017-co2-corrosion-rate-calculation-model-is-on-systematic-review/).
Because the review may produce a revision, future editions must be implemented and verified
explicitly rather than silently relabeling this calculation.

## Engineering boundary

This is a `SCREENING` implementation, not a conformity assessment or a material-selection
decision. It adapts the simplified equations already present in NeqSim and has regression evidence,
but no independent controlled benchmark against the purchased standard. Project use still requires
verification of the purchased edition, free-water and wetting basis, water chemistry, localized
corrosion, sour-service requirements, inhibitor availability, material selection, and project
acceptance criteria.

The readiness gate blocks calculation when:

- the standard edition or equipment type is unsupported;
- a required value is missing, non-finite, or physically invalid;
- temperature, pressure, CO2 partial pressure, or effective pH is outside the adapter's documented
  model envelope; or
- a fraction, exposure period, or optional FeCO3 saturation ratio is invalid.

Raw builder values are never clamped. A blocked result contains findings and no assessment value.
The legacy internal CO2-water pH estimate is allowed with a warning; a rigorous in-situ pH should be
supplied for buffered or saline brines. The FeCO3 saturation-ratio film factor is reported as a
NeqSim extension whenever enabled.

## Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.NORSOK_M_506);
NorsokM506CorrosionDesignKernel.Input input = NorsokM506CorrosionDesignKernel.Input
    .builder(edition, "Pipeline").temperatureC(60.0).totalPressureBara(100.0).co2MoleFraction(0.02)
    .actualPH(4.2).flowVelocityMPerS(3.0).pipeInternalDiameterM(0.254)
    .liquidDensityKgPerM3(1000.0).liquidDynamicViscosityPaS(0.001).inhibitorEfficiencyFraction(0.8)
    .exposureYears(25.0).build();

EngineeringCalculationResult<NorsokM506CorrosionAssessment> result =
    new NorsokM506CorrosionDesignKernel().calculate(input, null);
NorsokM506CorrosionAssessment assessment = result.getValue();
Map<String, Object> report = assessment.toMap();
```

`NorsokM506CorrosionDesignKernelTest.documentedExampleIsRunnable` executes every API call in this
snippet.

The executed
[NORSOK M-506 kernel notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/norsok_m506_corrosion_design_kernel.ipynb)
shows the typed result, an inhibitor-assumption sensitivity, and a deliberately blocked input.

## Result interpretation

The assessment reports CO2 partial pressure and fugacity, effective pH, baseline and corrected
rates, correction factors, scaling temperature, wall shear stress, and projected uniform wall loss.
The projected loss is simply:

$$\Delta t = CR_{corrected} \times t_{exposure}$$

It is not a specified corrosion allowance, remaining-life verdict, or pass/fail criterion. All
result maps retain `engineeringApprovalRequired = true`.

## Choosing the calculation path

| Need | API |
| --- | --- |
| Strict edition, range, applicability, and provenance checks | `NorsokM506CorrosionDesignKernel` |
| Rigorous brine pH and FeCO3 saturation input | `NorsokM506ElectrolyteBridge`, then pass the derived values to the kernel |
| Legacy mutable calculation or temperature/pressure sweep | `NorsokM506CorrosionRate` |

See also [Pipeline Corrosion Integration](pipeline_corrosion_integration) and
[Mechanical Design Standards](../mechanical_design_standards).
