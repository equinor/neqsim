---
title: Flow Assurance in NeqSim
description: "Guide to NeqSim flow-assurance screening for hydrates, wax, asphaltenes, scale, corrosion, erosion, emulsions, and pipeline cooldown."
---

Flow assurance keeps hydrocarbon fluids transportable during production, shutdown,
and restart. NeqSim combines thermodynamic models with screening calculators for
identifying risks and comparing mitigation options. Treat screening results as input
to an engineering assessment, not as design certification.

## Choose a workflow

| Topic | Start here |
| --- | --- |
| Integrated overview | [Flow-assurance overview](../flow_assurance_overview.md) |
| Hydrates | [Hydrate models](../../thermo/hydrate_models.md) |
| Wax | [Wax characterization](../../thermo/characterization/wax_characterization.md) |
| Asphaltenes | [Asphaltene modeling](asphaltene_modeling.md) |
| Pipeline cooldown, corrosion, scale, and wax curves | [Screening tools](flow_assurance_screening_tools.md) |
| Sand erosion | [Erosion prediction](erosion_prediction.md) |
| Emulsions | [Emulsion viscosity](emulsion_viscosity_calculator.md) |
| High-salinity scale and chemical treatment | [Mineral-scale and chemical-treatment validation](../mineral_scale_chemical_treatment_validation.md) |

## Screening classes

The following classes are in `neqsim.pvtsimulation.flowassurance`.

| Class | Purpose |
| --- | --- |
| `PipelineCooldownCalculator` | Lumped-parameter pipeline shutdown cooldown |
| `DeWaardMilliamsCorrosion` | Screening-level CO2 corrosion rate |
| `ScalePredictionCalculator` | Saturation indices for common mineral scales |
| `PitzerScaleActivityModel` | Activity coefficients for high-salinity brines |
| `MultiMineralScaleEquilibrium` | Coupled, shared-ion mineral precipitation |
| `WaxCurveCalculator` | Wax fraction curves with monotonicity enforcement |
| `ErosionPredictionCalculator` | API RP 14E velocity and DNV RP O501 erosion screening |
| `EmulsionViscosityCalculator` | Effective viscosity and phase-inversion screening |

For the fuller NORSOK M-506 and M-001 workflows in
`neqsim.process.corrosion`, see the
[corrosion analysis module](../../process/corrosion/index.md).

## Quick start: De Boer asphaltene screening

The De Boer method is a fast empirical screen based on reservoir
undersaturation and in-situ oil density. Pressures in this example are absolute
bar and density is in kg/m3.

```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;

DeBoerAsphalteneScreening screening =
    new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);

DeBoerRisk risk = screening.evaluateRisk();
double riskIndex = screening.calculateRiskIndex();

System.out.println("Risk category: " + risk);
System.out.println("Risk index: " + riskIndex);
```

For these inputs, the current correlation returns `MODERATE_PROBLEM` and a risk
index of `1.6`. This classification is a screening result; confirm a flagged fluid
with laboratory data and a fitted thermodynamic model.

## Select an asphaltene method

| Need | Class or guide | Notes |
| --- | --- | --- |
| Fast empirical screen | `DeBoerAsphalteneScreening` | Requires reservoir pressure, saturation pressure, and in-situ density |
| CPA stability analysis | `AsphalteneStabilityAnalyzer` | See [CPA calculations](asphaltene_cpa_calculations.md) |
| Compare available methods | `AsphalteneMethodComparison` or `AsphalteneMultiMethodBenchmark` | See [method comparison](asphaltene_method_comparison.md) |
| Regular-solution model | `FloryHugginsAsphalteneModel` | Configure and calibrate for the fluid being studied |
| Refractive-index screen | `RefractiveIndexAsphalteneScreening` | Requires measured or estimated refractive-index inputs |
| Cubic-EOS characterization | `PedersenAsphalteneCharacterization` | Adds characterized pseudo-components and supports binary-interaction tuning |
| CPA parameter fitting | `AsphalteneOnsetFitting` | See [parameter fitting](asphaltene_parameter_fitting.md) |
| Pressure-onset flash | `neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetPressureFlash` | Run the flash object and read `getOnsetPressure()` |
| Temperature-onset flash | `neqsim.thermodynamicoperations.flashops.saturationops.AsphalteneOnsetTemperatureFlash` | Run the flash object and read its onset result |

The `PhaseType` enum includes `ASPHALTENE` for a solid asphaltene-rich phase and
`LIQUID_ASPHALTENE` for the liquid-liquid Pedersen approach. Phase appearance and
type depend on the selected model, enabled phase checks, composition, and flash
conditions; do not assume that adding an asphaltene component alone guarantees a
precipitated phase.

NeqSim includes an `asphaltene` database component for CPA workflows. Its default
parameters are generic starting values. For cubic-EOS workflows,
`PedersenAsphalteneCharacterization` creates case-specific pseudo-components.
Tune either approach to measured onset or precipitation data before using it for
design decisions.

## Asphaltene documentation

- [De Boer screening](asphaltene_deboer_screening.md)
- [CPA calculations](asphaltene_cpa_calculations.md)
- [Parameter fitting](asphaltene_parameter_fitting.md)
- [Method comparison](asphaltene_method_comparison.md)
- [Validation cases](asphaltene_validation.md)

## Related documentation

- [PVT simulation](../README.md)
- [Thermodynamic models](../../thermo/README.md)
- [Process simulation](../../process/README.md)

