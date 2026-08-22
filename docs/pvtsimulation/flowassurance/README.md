---
title: Flow Assurance in NeqSim
description: "Guide to NeqSim flow-assurance screening for hydrates, wax, asphaltenes, scale, corrosion, erosion, emulsions, and pipeline cooldown."
---

Flow assurance keeps hydrocarbon fluids transportable during production, shutdown, and restart.
NeqSim combines thermodynamic models with screening calculators for identifying risks and
comparing mitigation options. Treat every screening result as input to an engineering assessment,
not as design certification.

## Choose a workflow

| Topic | Start here |
| --- | --- |
| Integrated study sequence | [Flow-assurance overview](../flow_assurance_overview.md) |
| Cooldown, simplified corrosion, mineral scale, and wax curves | [Screening-tools guide](flow_assurance_screening_tools.md) |
| Hydrates | [Hydrate models](../../thermo/hydrate_models.md) |
| Wax fluid characterization | [Wax characterization](../../thermo/characterization/wax_characterization.md) |
| Asphaltenes | [Asphaltene modeling](asphaltene_modeling.md) |
| Sand erosion | [Erosion prediction](erosion_prediction.md) |
| Emulsions | [Emulsion viscosity](emulsion_viscosity_calculator.md) |
| High-salinity scale and treatment evidence | [Mineral-scale and chemical-treatment validation](../mineral_scale_chemical_treatment_validation.md) |

## Screening classes

The following classes are in `neqsim.pvtsimulation.flowassurance`.

| Class | Purpose |
| --- | --- |
| `PipelineCooldownCalculator` | Lumped-parameter shutdown cooldown from explicit geometry, thermal, fluid, and boundary inputs |
| `SurfCooldownAnalyzer` | NeqSim-fluid wrapper that calculates properties and hydrate equilibrium before the lumped cooldown screen |
| `DeWaardMilliamsCorrosion` | Simplified CO2-corrosion screening with empirical correction factors |
| `ScalePredictionCalculator` | Saturation indices for common mineral scales |
| `PitzerScaleActivityModel` | Activity coefficients for high-salinity brines |
| `MultiMineralScaleEquilibrium` | Coupled shared-ion mineral precipitation equilibrium |
| `WaxCurveCalculator` | Wax-fraction curves, WAT results, monotonicity correction, and flash diagnostics |
| `ErosionPredictionCalculator` | API RP 14E velocity and DNV RP O501 erosion screening |
| `EmulsionViscosityCalculator` | Effective viscosity and phase-inversion screening |

For process-coupled corrosion and materials workflows in `neqsim.process.corrosion`, see the
[corrosion analysis module](../../process/corrosion/index.md).

## Canonical executable example

The [screening-tools guide](flow_assurance_screening_tools.md) contains the maintained Java 8
program for cooldown, simplified corrosion, and scale screening, plus the current wax-curve API
contract. `FlowAssuranceDocumentationTest` executes the documented calls and checks result bounds.
Keeping one canonical program prevents landing-page fragments from drifting independently.

The example states every unit and preserves JSON outputs that can be paired with stable case or
asset identities, input provenance, model/version information, and data-quality diagnostics in a
larger workflow. The calculators do not add that governance automatically.

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

For a quick empirical classification, use the maintained
[De Boer screening guide](asphaltene_deboer_screening.md). The implementation uses absolute
reservoir and saturation pressure in bar and in-situ oil density in kg/m3. A flagged case still
requires measured onset or precipitation data and a calibrated model.

The `PhaseType` enum includes `ASPHALTENE` for a solid asphaltene-rich phase and
`LIQUID_ASPHALTENE` for the liquid-liquid Pedersen approach. Phase appearance and type depend on
the selected model, enabled phase checks, composition, and flash conditions; adding an asphaltene
component alone does not guarantee a precipitated phase.

NeqSim includes an `asphaltene` database component for CPA workflows. Its default parameters are
generic starting values. For cubic-EOS workflows, `PedersenAsphalteneCharacterization` creates
case-specific pseudo-components. Tune either approach to measured onset or precipitation data
before using it for design decisions.

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
