---
title: De Boer Asphaltene Screening
description: Empirical asphaltene-risk screening with explicit inputs, executable Java, and engineering-use boundaries.
---

## Purpose

`DeBoerAsphalteneScreening` places an oil on NeqSim's implementation of the De Boer
undersaturation-pressure versus live-oil-density chart. Use it for early triage: it can identify
cases that merit laboratory work or a calibrated thermodynamic study.

The result is an empirical risk category. It is not an asphaltene-onset pressure, a deposition
model, or a probability of failure.

## Inputs and units

Provide all three values for the same reservoir fluid and condition basis.

| Input | NeqSim unit | Interpretation |
|---|---:|---|
| Reservoir pressure | bara | Current or scenario reservoir pressure |
| Saturation pressure | bara | Bubble-point pressure at reservoir temperature |
| In-situ oil density | kg/m³ | Live-oil density at reservoir pressure and temperature |

NeqSim calculates the undersaturation pressure as

$$
\Delta P = P_{\mathrm{reservoir}} - P_{\mathrm{saturation}}.
$$

Because both pressures are absolute, their difference is in bar. Do not pass gauge pressure for
one input and absolute pressure for the other.

## Executable screening example

The example uses explicit measured or previously calculated properties, so it isolates the
screening correlation from fluid-model uncertainty. Java assertions document the expected result
and are enabled by the documentation test.

<!-- deboer-screening-example:start -->
```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DeBoerScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(DeBoerScreeningExample.class);

  private DeBoerScreeningExample() {}

  public static void main(String[] args) {
    double reservoirPressureBara = 350.0;
    double saturationPressureBara = 150.0;
    double liveOilDensityKgM3 = 750.0;

    DeBoerAsphalteneScreening screening =
        new DeBoerAsphalteneScreening(
            reservoirPressureBara, saturationPressureBara, liveOilDensityKgM3);

    DeBoerRisk risk = screening.evaluateRisk();
    double riskIndex = screening.calculateRiskIndex();

    assert risk == DeBoerRisk.MODERATE_PROBLEM;
    assert Double.isFinite(riskIndex);
    assert Math.abs(riskIndex - 200.0 / 120.0) < 1.0e-9;

    logger.info(
        "De Boer screen: risk={}, index={}, undersaturation={} bar",
        risk,
        riskIndex,
        screening.getUndersaturationPressure());
  }
}
```
<!-- deboer-screening-example:end -->

For these inputs, $\Delta P = 200$ bar. The current implementation classifies the case as
`MODERATE_PROBLEM` and returns a risk index of 200/120 = 1.667 (the no-problem boundary at 750 kg/m3 is
capped at the 120-bar slight-problem boundary, see below). The assertions, rather than a copied console
transcript, are the maintained result contract.

## Interpreting the API

### Risk category

`evaluateRisk()` returns one of four `DeBoerRisk` values:

| Enum value | Screening meaning | Engineering response |
|---|---|---|
| `NO_PROBLEM` | Below the lowest implemented boundary | Record assumptions; monitor if conditions or fluid properties change |
| `SLIGHT_PROBLEM` | Above the no-problem boundary | Review data quality and consider laboratory screening |
| `MODERATE_PROBLEM` | Above the slight-problem boundary | Escalate to measured onset/deposition work and mitigation evaluation |
| `SEVERE_PROBLEM` | Above the severe-problem boundary | Treat as a strong screening flag; obtain specialist validation |

These labels report position relative to the implemented boundary curves. They do not by
themselves establish operating limits.

### Boundary ordering

The three boundaries are quadratic fits in density. The fitted no-problem curve lies above the
slight-problem curve below about 800 kg/m3 (98 bar against 90 bar at 700 kg/m3). Before 2026-11 a
point between the two curves skipped `SLIGHT_PROBLEM` and was reported as `MODERATE_PROBLEM` even
though it was below the no-problem curve. `DeBoerAsphalteneScreening.boundaries(density)` now returns
the boundaries ordered as no-problem <= slight <= severe: the no-problem boundary is capped at the
slight boundary, so where the fitted curves cross, the slight-problem band has zero width.
`evaluateRisk()`, `calculateRiskIndex()`, `generatePlotData(...)` and
`AsphalteneStabilityAnalyzer.deBoerScreening(...)` (which now delegates to this class) all use the
ordered boundaries.

### Risk index

`calculateRiskIndex()` divides the actual undersaturation by the implementation's lower
problem boundary for the supplied density. It is an implementation-specific screening ratio, not
a probability or a universal severity scale. If `setAsphalteneContent(...)` is used, NeqSim
applies an additional heuristic multiplier, 1 + (w - 0.02)/0.03 with a floor of 0.5, which leaves
the index unchanged at 2 wt% and doubles it at 5 wt% (earlier versions divided by 0.05, giving 1.6
at 5 wt% although the comment said "doubles"); document the supplied weight fraction and sensitivity
when using that option.

### Thermodynamic-system constructor

The constructor that accepts a `SystemInterface` can calculate saturation pressure and density
from a fluid model. In that workflow:

- characterize and tune the fluid before screening;
- set reservoir temperature in kelvin and pressure in bara;
- check that bubble-point and live-oil-density calculations are finite and physically credible;
- preserve the measured-property screen as a comparison when suitable data exist.

A fluid model adds property-model uncertainty; it does not convert the empirical chart into a
thermodynamic precipitation model.

## Applicability and limitations

Use the screen as one line of evidence. It does not predict deposition rate, plugging, or inhibitor
dose. It also does not represent changes in composition caused by gas injection, blending,
biodegradation, or production history unless those changes are reflected in defensible input
properties.

Before an operating or design decision:

1. check pressure basis, temperature basis, density units, and representative sampling;
2. compare nearby depletion or injection scenarios, especially near a category boundary;
3. validate against measured SARA data and onset/deposition observations;
4. use a calibrated model when composition effects or a pressure-temperature envelope matter; and
5. retain uncertainty margins appropriate to the consequence of plugging or deposition.

For deeper analysis, see [Asphaltene modeling overview](asphaltene_modeling),
[CPA calculations](asphaltene_cpa_calculations), and
[method comparison](asphaltene_method_comparison).

## Verification contract

`DeBoerScreeningDocumentationTest` extracts the exact marked Java block, requires it to be the
only Java fence on this page, compiles it for Java 8, enables assertions, and runs `main`. The
test also protects the input units and the limitations above from drifting into unsupported
claims.
