---
title: "NORSOK M-506 CO2-corrosion screening"
description: "Rev. 2 unmitigated CO2 corrosion correlation and the legacy engineering screening adapter."
---

# NORSOK M-506 CO2-corrosion screening

Choose the calculation that matches the basis of the study.

- `NorsokM506Rev2` evaluates the unmitigated Rev. 2 carbon-steel correlation.
- `NorsokM506CorrosionDesignKernel` records findings and inputs for legacy screening studies.
- `NorsokM506CorrosionAssessment` is an immutable, unit-explicit result snapshot.
- `NorsokM506CorrosionRate` remains available for legacy mutable workflows and parameter sweeps.
- `NorsokM506ElectrolyteBridge` remains the route for obtaining an electrolyte-model pH and optional
  FeCO3 saturation ratio from a brine. It finds the aqueous phase also when the flash labels a
  water-rich phase as oil. If the aqueous phase is suspect (water mole fraction below 0.7) or the
  electrolyte pH is outside 2.5-10, it falls back to the M-506 correlation pH and
  `isElectrolytePhaseSuspect()` returns true (also in `toMap()`/`toJson()`). The electrolyte CPA pH of
  CO2-saturated water is known to be high (about 4.8 at 25 C and 1 bar CO2, against about 3.9 from the
  literature and the "acidgas" method), so compare with the correlation pH before relying on it.

The kernel accepts only the unamended `NORSOK-M-506 2017` edition identifier. It evaluates the legacy de Waard-Milliams screening equations. Standards Norway identified
that edition as current and under systematic review in May 2026; the catalog record was checked on
2026-08-02 against the [publisher notice](https://standard.no/en/news/norsok-m-5062017-co2-corrosion-rate-calculation-model-is-on-systematic-review/).
Because the review may produce a revision, future editions must be implemented and verified
explicitly rather than silently relabeling this calculation.

## Unmitigated rate from the 2005 edition

For a water-wet carbon steel surface in sweet CO2 service, use
`NorsokM506Rev2.rateMmPerYear(temperatureC, co2FugacityBar, inSituPH, wallShearStressPa)`
for NORSOK M-506 Rev. 2 (June 2005), section 5.2, equations (1)–(3) and Tables 1–2.
Supply 5–150 °C, 0.1–10 bar CO2 fugacity, pH 3.5–6.5 and 1–150 Pa wall shear.
At 5 °C the rate is independent of shear. The temperature interpolation uses the
two bracketing **rates**, including the pH factor at each knot. At a shared pH
breakpoint the implementation uses the higher-pH branch.

If the available input is a gas mole fraction, the configured mutable calculator's
`calculateRev2UnmitigatedRate(inSituPH, wallShearStressPa)` computes fugacity
using Rev. 2 section 8.1 before evaluating the rate. At 60 °C, 40 bara and 1 mol%
CO2, equations (7)–(8) give a base-10 fugacity coefficient of 0.9034571. At pH 4
and 19 Pa the resulting rate is **6.30919 mm/year**. Supplying an independently
determined fugacity of 0.38275 bar gives **6.53795 mm/year** instead. The pressure
cap of 250 bara applies only to the coefficient; partial pressure uses total pressure.

This method evaluates the 2005 unmitigated uniform-corrosion correlation. The caller supplies
the water-wetting, sour-service applicability, in-situ pH and local shear basis.
Glycol, inhibitor, FeCO3 film and exposure-year projections belong to separate assessments.
The existing `calculate()` and edition-aware screening kernel continue to publish the legacy
screening rates under the `rateModel` identifier in their result maps.

## Legacy screening kernel boundary

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
