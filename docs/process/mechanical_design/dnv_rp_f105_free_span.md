---
title: DNV-RP-F105 first-mode free-span screening
description: Edition-aware, fail-closed first-mode and dimensionless screening for free-spanning submarine pipelines.
---

# DNV-RP-F105 first-mode free-span screening

`DnvRpF105FreeSpanScreeningKernel` provides a transparent preliminary screen for the current
`DNV-RP-F105 2025-12` basis. It calculates a simply supported Euler-Bernoulli first-mode frequency
and common current/wave dimensionless groups. It deliberately does not reproduce licensed response
or acceptance tables.

DNV identifies the current edition and public scope, including current, waves, combined loading,
VIV, direct wave loading, ULS, and FLS, on its
[DNV-RP-F105 publisher page](https://www.dnv.com/energy/standards-guidelines/dnv-rp-f105-free-spanning-pipelines/).
Use the purchased standard and project design basis to establish applicability, response models,
environmental cases, factors, and acceptance criteria.

## Implemented calculation

For a simply supported span of length $L$, steel second moment $I$, effective modal mass per length
$m_e$, Young's modulus $E$, and effective axial force $T$ (positive tension, negative compression),
the kernel evaluates

$$
I = \frac{\pi}{64}\left(D_o^4 - (D_o - 2t)^4\right)
$$

$$
f_n = \frac{1}{2\pi}\sqrt{\frac{EI(\pi/L)^4 + T(\pi/L)^2}{m_e}}.
$$

The structural solution fails closed when compression produces a non-positive first-mode
eigenvalue. The result also reports the simply supported Euler critical compression magnitude for
review; NeqSim does not turn that value into a code acceptance check.

With hydrodynamic diameter $D_h$, normal current velocity $U_c$, wave orbital-velocity amplitude
$U_w$, wave period $T_w$, and caller-controlled Strouhal number $St$, the result contains:

| Quantity | Expression |
| --- | --- |
| Current shedding frequency | $f_s = St U_c / D_h$ |
| Current frequency ratio | $f_s / f_n$ |
| Current reduced velocity | $U_c / (f_n D_h)$ |
| Wave frequency ratio | $(1/T_w) / f_n$ |
| Wave reduced velocity | $U_w / (f_n D_h)$ |
| Keulegan-Carpenter number | $U_w T_w / D_h$ |

The input keeps steel outside diameter separate from hydrodynamic diameter so coatings and marine
growth can be represented without corrupting structural stiffness. Effective mass is an external
project input and should include the accepted pipe, coating, contents, entrained-water, and added-
mass basis.

## Runnable Java example

```java
StandardEdition edition = StandardEdition.defaultEdition(StandardType.DNV_RP_F105);
DnvRpF105FreeSpanScreeningKernel.Input input = DnvRpF105FreeSpanScreeningKernel.Input
    .builder(edition, "Pipeline")
    .spanLengthM(30.0)
    .steelOuterDiameterM(0.3239)
    .steelWallThicknessM(0.0206)
    .hydrodynamicDiameterM(0.3239)
    .youngsModulusPa(207.0e9)
    .effectiveMassPerLengthKgPerM(250.0)
    .effectiveAxialForceN(500000.0)
    .currentVelocityMPerS(0.8)
    .waveOrbitalVelocityAmplitudeMPerS(1.2)
    .wavePeriodS(10.0)
    .strouhalNumber(0.2)
    .lockInFrequencyRatioLower(0.8)
    .lockInFrequencyRatioUpper(1.2)
    .maxCurrentReducedVelocityForScreening(4.0)
    .maxWaveReducedVelocityForScreening(3.0)
    .spanGeometryVerified(true)
    .structuralModelVerified(true)
    .environmentalBasisVerified(true)
    .projectScreeningLimitsVerified(true)
    .build();

EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result =
    new DnvRpF105FreeSpanScreeningKernel().calculate(input, null);
DnvRpF105FreeSpanAssessment screen = result.getValue();
```

For these demonstration inputs, the natural frequency is about `1.06182 Hz`, current reduced
velocity is `2.32609`, wave reduced velocity is `3.48914`, and the project-controlled wave trigger
requests detailed response assessment. These values are deterministic regression data, not an
independent DNV benchmark or a safe-span decision.

The
[executed notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dnv_rp_f105_free_span_screening_kernel.ipynb)
uses the same API, sweeps span length to expose first-mode/reduced-velocity sensitivity, and shows a
deliberately blocked unverified structural basis.

## Fail-closed evidence boundary

Calculation is blocked unless all of the following are explicit and internally valid:

- exact unamended `2025-12` edition and `Pipeline` or `AdiabaticPipe` applicability;
- surveyed free-span and pipe geometry, including a hydrodynamic diameter at least as large as the
  steel diameter;
- accepted Young's modulus, effective mass, effective axial force, and simply supported first-mode
  applicability;
- at least one positive normal current or wave velocity, with a positive period for wave input;
- caller-controlled Strouhal number, current frequency-ratio band, and current/wave reduced-
  velocity triggers; and
- external verification flags for geometry, structural model, environment, and project triggers.

The trigger values are recorded as caller evidence. Reaching or not reaching one does not mean that
a span complies with DNV-RP-F105.

## Not implemented

The kernel does not calculate or approve:

- soil, shoulder, boundary-condition, or interacting multi-span stiffness;
- mode shapes beyond the simply supported first mode;
- in-line or cross-flow VIV response amplitudes and stress ranges;
- direct wave-loading response or load combinations;
- ULS, FLS, fatigue damage, acceptance criteria, or safety factors;
- seabed proximity corrections, directionality, return-period selection, or environmental contour
  construction;
- sensor selection, monitoring interpretation, mitigation, buckle arrest, or intervention; or
- conformity, certification, or accountable engineering approval.

Use `DnvRpC203FatigueDesignKernel` only after a controlled workflow has derived applicable stress
ranges and selected verified project S-N data. The F105 screen does not generate those inputs.

## Legacy NeqSim span estimate

`PipeMechanicalDesignCalculator.calculateAllowableSpanLength(...)` remains available for backward
compatibility. It uses a fixed simplified VIV-avoidance assumption and arbitrary fallback/cap
behavior. It is not edition-aware and must not be reported as a DNV-RP-F105 calculation. New
explicit F105 studies should use the typed kernel and retain the detailed external assessment.
